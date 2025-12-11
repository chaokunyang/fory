// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package fory

import (
	"encoding/binary"
	"errors"
	"fmt"
	"reflect"
	"strconv"
	"unsafe"
)

// ============================================================================
// Errors
// ============================================================================

// ErrMagicNumber indicates an invalid magic number in the data stream
var ErrMagicNumber = errors.New("fory: invalid magic number")

// ErrNoSerializer indicates no serializer is registered for a type
var ErrNoSerializer = errors.New("fory: no serializer registered for type")

// ============================================================================
// Constants
// ============================================================================

// Protocol constants
const (
	MAGIC_NUMBER int16 = 0x62D4
)

// Language constants for protocol header
const (
	LangXLANG uint8 = iota
	LangJAVA
	LangPYTHON
	LangCPP
	LangGO
)

// Bitmap flags for protocol header
const (
	IsNilFlag        = 1
	LittleEndianFlag = 2
	XLangFlag        = 4
	OutOfBandFlag    = 8
)

// Reference flags
const (
	NullFlag         int8 = -3
	RefFlag          int8 = -2
	NotNullValueFlag int8 = -1
	RefValueFlag     int8 = 0
)

// ============================================================================
// Config
// ============================================================================

// Config holds configuration options for Fory instances
type Config struct {
	TrackRef   bool
	MaxDepth   int
	IsXlang    bool
	Compatible bool // Schema evolution compatibility mode
}

// defaultConfig returns the default configuration
func defaultConfig() Config {
	return Config{
		TrackRef: false, // Match Java's default: reference tracking disabled
		MaxDepth: 100,
		IsXlang:  true,
	}
}

// Option is a function that configures a Fory instance
type Option func(*Fory)

// WithTrackRef sets reference tracking mode
func WithTrackRef(enabled bool) Option {
	return func(f *Fory) {
		f.config.TrackRef = enabled
	}
}

// WithRefTracking is deprecated, use WithTrackRef instead
func WithRefTracking(enabled bool) Option {
	return WithTrackRef(enabled)
}

// WithMaxDepth sets the maximum serialization depth
func WithMaxDepth(depth int) Option {
	return func(f *Fory) {
		f.config.MaxDepth = depth
	}
}

// WithXlang sets cross-language serialization mode
func WithXlang(enabled bool) Option {
	return func(f *Fory) {
		f.config.IsXlang = enabled
	}
}

// WithCompatible sets schema evolution compatibility mode
func WithCompatible(enabled bool) Option {
	return func(f *Fory) {
		f.config.Compatible = enabled
	}
}

// ============================================================================
// Fory - Main serialization instance
// ============================================================================

// Fory is the main serialization instance.
// Note: Fory is NOT thread-safe. Use ThreadSafeFory for concurrent use.
type Fory struct {
	config      Config
	metaContext *MetaContext

	// Reusable contexts - avoid allocation on each Serialize/Deserialize call
	writeCtx *WriteContext
	readCtx  *ReadContext

	// Resolvers shared between contexts
	typeResolver *TypeResolver
	refResolver  *RefResolver
}

// MetaContext returns the meta context for schema evolution
func (f *Fory) MetaContext() *MetaContext {
	return f.metaContext
}

// Register registers a struct type with a numeric ID for cross-language serialization.
// This is compatible with Java's fory.register(Class, int) method.
// type_ can be either a reflect.Type or an instance of the type
// typeID should be the user type ID in the range 0-8192 (the internal type ID will be added automatically)
// Note: For enum types, use RegisterEnum instead.
func (f *Fory) Register(type_ interface{}, typeID uint32) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}

	// Only struct types are supported via Register
	// For enums, use RegisterEnum
	if t.Kind() != reflect.Struct {
		return fmt.Errorf("Register only supports struct types; for enum types use RegisterEnum. Got: %v", t.Kind())
	}

	// Determine the internal type ID based on config
	var internalTypeID TypeId
	// Use COMPATIBLE_STRUCT when compatible mode is enabled (matches Java behavior)
	if f.config.Compatible {
		internalTypeID = COMPATIBLE_STRUCT
	} else {
		internalTypeID = STRUCT
	}

	// Calculate full type ID: (userID << 8) | internalTypeID
	fullTypeID := (typeID << 8) | uint32(internalTypeID)

	return f.typeResolver.RegisterByID(t, fullTypeID)
}

// RegisterEnum registers an enum type with a numeric ID for cross-language serialization.
// In Go, enums are typically defined as int-based types (e.g., type Color int32).
// This method creates an enum serializer that writes/reads the enum value as Varuint32Small7.
// type_ can be either a reflect.Type or an instance of the enum type
// typeID should be the user type ID in the range 0-8192 (the internal type ID will be added automatically)
func (f *Fory) RegisterEnum(type_ interface{}, typeID uint32) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}

	// Verify it's a numeric type (Go enums are int-based)
	switch t.Kind() {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
		reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		// OK
	default:
		return fmt.Errorf("RegisterEnum only supports numeric types (Go enums); got: %v", t.Kind())
	}

	// Calculate full type ID: (userID << 8) | ENUM
	fullTypeID := (typeID << 8) | uint32(ENUM)

	return f.typeResolver.RegisterEnumByID(t, fullTypeID)
}

// RegisterEnumByName registers an enum type with a name for cross-language serialization.
// In Go, enums are typically defined as int-based types (e.g., type Color int32).
// type_ can be either a reflect.Type or an instance of the enum type
// typeName is the name to use for cross-language serialization (e.g., "demo.color" or "color")
func (f *Fory) RegisterEnumByName(type_ interface{}, namespace, typeName string) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}

	// Verify it's a numeric type (Go enums are int-based)
	switch t.Kind() {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
		reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		// OK
	default:
		return fmt.Errorf("RegisterEnumByName only supports numeric types (Go enums); got: %v", t.Kind())
	}

	return f.typeResolver.RegisterEnumByName(t, namespace, typeName)
}

// RegisterNamedType registers a named struct type for cross-language serialization
// type_ can be either a reflect.Type or an instance of the type
// Note: For enum types, use RegisterEnumByName instead.
func (f *Fory) RegisterNamedType(type_ interface{}, typeName string) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}
	if t.Kind() != reflect.Struct {
		return fmt.Errorf("RegisterNamedType only supports struct types; for enum types use RegisterEnumByName. Got: %v", t.Kind())
	}
	return f.typeResolver.RegisterNamedType(t, 0, "", typeName)
}

// NewForyWithOptions is an alias for New for backward compatibility
func NewForyWithOptions(opts ...Option) *Fory {
	return New(opts...)
}

// NewFory is an alias for New for backward compatibility
func NewFory(opts ...Option) *Fory {
	return New(opts...)
}

// Marshal serializes a value to bytes (instance method for backward compatibility)
func (f *Fory) Marshal(v interface{}) ([]byte, error) {
	return f.SerializeAny(v)
}

// Unmarshal deserializes bytes into the provided value (instance method for backward compatibility)
func (f *Fory) Unmarshal(data []byte, v interface{}) error {
	if v == nil {
		return nil
	}
	rv := reflect.ValueOf(v)
	if rv.Kind() != reflect.Ptr || rv.IsNil() {
		return fmt.Errorf("v must be a non-nil pointer")
	}
	defer func() {
		// Reset context and set data
		f.readCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
		f.readCtx.SetData(data)
	}()
	f.readCtx.SetData(data)

	// ReadData and validate header, get meta offset if present
	metaOffset, err := readHeader(f.readCtx)
	if err != nil {
		return err
	}

	// Check if the serialized object is null
	if metaOffset == NullObjectMetaOffset {
		// Set target to zero value (nil for pointers/interfaces)
		rv.Elem().Set(reflect.Zero(rv.Elem().Type()))
		return nil
	}

	// In compatible mode, load type definitions if meta offset is present
	var finalPos int
	if f.config.Compatible && metaOffset > 0 {
		// Save current position (after reading metaOffset, before object data)
		dataStartPos := f.readCtx.buffer.ReaderIndex()

		// Jump to meta section and read type definitions
		metaPos := dataStartPos + int(metaOffset)
		f.readCtx.buffer.SetReaderIndex(metaPos)

		if err := f.typeResolver.readTypeDefs(f.readCtx.buffer); err != nil {
			return fmt.Errorf("failed to read type definitions: %w", err)
		}

		// Save final position (after reading TypeDefs)
		finalPos = f.readCtx.buffer.ReaderIndex()

		// Return to data start position to deserialize object
		f.readCtx.buffer.SetReaderIndex(dataStartPos)
	}

	// Deserialize directly into the target
	if err := f.readCtx.ReadValue(rv.Elem()); err != nil {
		return err
	}

	// Restore final position if we loaded type definitions
	if finalPos > 0 {
		f.readCtx.buffer.SetReaderIndex(finalPos)
	}

	return nil
}

// Serialize serializes a value to buffer (for streaming/cross-language use).
// The third parameter is an optional callback for buffer objects (can be nil).
// If callback is provided, it will be called for each BufferObject during serialization.
// Return true from callback to write in-band, false for out-of-band.
func (f *Fory) Serialize(buffer *ByteBuffer, v interface{}, callback func(BufferObject) bool) error {
	buf := f.writeCtx.buffer
	defer func() {
		// Reset internal state but NOT the buffer - caller manages buffer state
		// This allows streaming multiple values to the same buffer
		f.writeCtx.ResetState()
		f.writeCtx.buffer = buf
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
		// Set up buffer callback for out-of-band serialization
		if callback != nil {
			f.writeCtx.bufferCallback = nil
			f.writeCtx.outOfBand = false
		}
	}()
	f.writeCtx.buffer = buffer
	if f.metaContext != nil {
		f.metaContext.Reset()
	}
	// Set up buffer callback for out-of-band serialization
	if callback != nil {
		f.writeCtx.bufferCallback = callback
		f.writeCtx.outOfBand = true
	}

	// WriteData protocol header
	writeHeader(f.writeCtx, f.config)

	// In compatible mode, reserve space for meta offset (matches C++/Java)
	var metaStartOffset int
	if f.config.Compatible {
		metaStartOffset = buffer.writerIndex
		buffer.WriteInt32(-1) // Placeholder for meta offset
	}

	// Serialize the value
	if err := f.writeCtx.WriteValue(reflect.ValueOf(v)); err != nil {
		return err
	}

	// WriteData collected TypeMetas at the end in compatible mode (matches C++/Java)
	if f.config.Compatible && f.metaContext != nil && len(f.metaContext.writingTypeDefs) > 0 {
		// Calculate offset from the position after meta offset field to meta section start
		currentPos := buffer.writerIndex
		offset := currentPos - metaStartOffset - 4
		// Update the meta offset field
		buffer.PutInt32(metaStartOffset, int32(offset))
		// WriteData type definitions
		f.typeResolver.writeTypeDefs(buffer)
	}

	return nil
}

// Deserialize deserializes from buffer into the provided value (for streaming/cross-language use).
// The third parameter is optional external buffers for out-of-band data (can be nil).
func (f *Fory) Deserialize(buffer *ByteBuffer, v interface{}, buffers []*ByteBuffer) error {
	// Reset context and use the provided buffer
	f.readCtx.Reset()
	f.readCtx.buffer = buffer

	// Set up out-of-band buffers if provided
	if buffers != nil {
		f.readCtx.outOfBandBuffers = buffers
	}

	// ReadData and validate header, get meta offset if present
	metaOffset, err := readHeader(f.readCtx)
	if err != nil {
		return err
	}

	// Check if the serialized object is null
	if metaOffset == NullObjectMetaOffset {
		// v must be a pointer so we can set it to nil
		rv := reflect.ValueOf(v)
		if rv.Kind() == reflect.Ptr && !rv.IsNil() {
			rv.Elem().Set(reflect.Zero(rv.Elem().Type()))
		}
		return nil
	}

	// In compatible mode, load type definitions if meta offset is present
	// This matches C++ deserialize_impl: read type defs BEFORE deserializing object
	var finalPos int
	if f.config.Compatible && metaOffset > 0 {
		// Save current position (right after meta offset field, before object data)
		dataStartPos := buffer.ReaderIndex()

		// Jump to meta section and read type definitions
		metaPos := dataStartPos + int(metaOffset)
		buffer.SetReaderIndex(metaPos)

		if err := f.typeResolver.readTypeDefs(buffer); err != nil {
			return fmt.Errorf("failed to read type definitions: %w", err)
		}

		// Save final position (after reading TypeDefs)
		finalPos = buffer.ReaderIndex()

		// Return to data start position to deserialize the object
		buffer.SetReaderIndex(dataStartPos)
	}

	// v must be a pointer so we can deserialize into it
	if v == nil {
		return fmt.Errorf("v cannot be nil")
	}
	rv := reflect.ValueOf(v)
	if rv.Kind() != reflect.Ptr {
		return fmt.Errorf("v must be a pointer, got %v", rv.Kind())
	}
	if rv.IsNil() {
		return fmt.Errorf("v must be a non-nil pointer")
	}
	// Deserialize directly into v
	if err := f.readCtx.ReadValue(rv.Elem()); err != nil {
		return err
	}
	// Restore final position if we loaded type definitions
	if finalPos > 0 {
		buffer.SetReaderIndex(finalPos)
	}
	return nil
}

// RegisterByName registers a type with namespace for cross-language serialization
func (f *Fory) RegisterByName(type_ interface{}, namespace, typeName string) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}
	return f.typeResolver.RegisterNamedType(t, 0, namespace, typeName)
}

// RegisterExtensionType registers a type as an extension type (NAMED_EXT) for cross-language serialization.
// Extension types use a custom serializer provided by the user.
// This is used for types with custom serializers in cross-language serialization.
//
// Example:
//
//	type MyExtSerializer struct{}
//
//	func (s *MyExtSerializer) Write(buf *ByteBuffer, value interface{}) error {
//	    myExt := value.(MyExt)
//	    buf.WriteVarint32(myExt.Id)
//	    return nil
//	}
//
//	func (s *MyExtSerializer) Read(buf *ByteBuffer) (interface{}, error) {
//	    id := buf.ReadVarint32()
//	    return MyExt{Id: id}, nil
//	}
//
//	// Register with custom serializer
//	f.RegisterExtensionType(MyExt{}, "my_ext", &MyExtSerializer{})
func (f *Fory) RegisterExtensionType(type_ interface{}, typeName string, serializer ExtensionSerializer) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}
	return f.typeResolver.RegisterExtensionType(t, "", typeName, serializer)
}

// RegisterExtensionTypeByID registers a type as an extension type with a numeric ID.
// Extension types use a custom serializer provided by the user.
// typeID should be the user type ID in the range 0-8192.
func (f *Fory) RegisterExtensionTypeByID(type_ interface{}, typeID uint32, serializer ExtensionSerializer) error {
	var t reflect.Type
	if rt, ok := type_.(reflect.Type); ok {
		t = rt
	} else {
		t = reflect.TypeOf(type_)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}
	}
	return f.typeResolver.RegisterExtensionTypeByID(t, typeID, serializer)
}

// New creates a new Fory instance with the given options
func New(opts ...Option) *Fory {
	f := &Fory{
		config: defaultConfig(),
	}

	// Apply options
	for _, opt := range opts {
		opt(f)
	}

	// Initialize meta context if compatible mode is enabled
	if f.config.Compatible {
		f.metaContext = &MetaContext{
			typeMap:               make(map[reflect.Type]uint32),
			writingTypeDefs:       make([]*TypeDef, 0),
			readTypeInfos:         make([]TypeInfo, 0),
			scopedMetaShareEnable: true,
		}
	}

	// Initialize resolvers
	f.typeResolver = newTypeResolver(f)
	f.refResolver = newRefResolver(f.config.TrackRef)

	// Initialize reusable contexts with resolvers
	f.writeCtx = NewWriteContext(f.config.TrackRef, f.config.MaxDepth)
	f.writeCtx.typeResolver = f.typeResolver
	f.writeCtx.refResolver = f.refResolver
	f.writeCtx.compatible = f.config.Compatible

	f.readCtx = NewReadContext(f.config.TrackRef)
	f.readCtx.typeResolver = f.typeResolver
	f.readCtx.refResolver = f.refResolver
	f.readCtx.compatible = f.config.Compatible

	return f
}

// Reset clears internal state for reuse
func (f *Fory) Reset() {
	f.writeCtx.Reset()
	f.readCtx.Reset()
}

// ============================================================================
// Generic Serialization API
// ============================================================================

// Serialize - type T inferred, serializer auto-resolved.
// The serializer handles its own ref/type info writing internally.
// Falls back to reflection-based serialization for unregistered types.
// Note: Fory instance is NOT thread-safe. Use ThreadSafeFory for concurrent use.
func Serialize[T any](f *Fory, value *T) ([]byte, error) {
	defer func() {
		f.writeCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
	}()
	// WriteData protocol header
	writeHeader(f.writeCtx, f.config)

	// Fast path: type switch for common types (Go compiler can optimize this)
	// Using *T avoids interface heap allocation and struct copy
	// Store boxed value once and reuse in default case
	v := any(value)
	var err error
	switch val := v.(type) {
	case *bool:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(BOOL)
		f.writeCtx.buffer.WriteBool(*val)
	case *int8:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT8)
		f.writeCtx.buffer.WriteInt8(*val)
	case *int16:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT16)
		f.writeCtx.buffer.WriteInt16(*val)
	case *int32:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT32)
		f.writeCtx.buffer.WriteVarint32(*val)
	case *int64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT64)
		f.writeCtx.buffer.WriteVarint64(*val)
	case *int:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		if strconv.IntSize == 64 {
			f.writeCtx.WriteTypeId(INT64)
			f.writeCtx.buffer.WriteVarint64(int64(*val))
		} else {
			f.writeCtx.WriteTypeId(INT32)
			f.writeCtx.buffer.WriteVarint32(int32(*val))
		}
	case *float32:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(FLOAT)
		f.writeCtx.buffer.WriteFloat32(*val)
	case *float64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(DOUBLE)
		f.writeCtx.buffer.WriteFloat64(*val)
	case *string:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(STRING)
		f.writeCtx.buffer.WriteVaruint32(uint32(len(*val)))
		if len(*val) > 0 {
			f.writeCtx.buffer.WriteBinary(unsafe.Slice(unsafe.StringData(*val), len(*val)))
		}
	case *[]byte:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(BINARY)
		f.writeCtx.buffer.WriteBool(true) // in-band
		f.writeCtx.buffer.WriteLength(len(*val))
		f.writeCtx.buffer.WriteBinary(*val)
	case *[]int8:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT8_ARRAY)
		err = writeInt8Slice(f.writeCtx.buffer, *val)
	case *[]int16:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT16_ARRAY)
		err = writeInt16Slice(f.writeCtx.buffer, *val)
	case *[]int32:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT32_ARRAY)
		err = writeInt32Slice(f.writeCtx.buffer, *val)
	case *[]int64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(INT64_ARRAY)
		err = writeInt64Slice(f.writeCtx.buffer, *val)
	case *[]int:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		if strconv.IntSize == 64 {
			f.writeCtx.WriteTypeId(INT64_ARRAY)
		} else {
			f.writeCtx.WriteTypeId(INT32_ARRAY)
		}
		err = writeIntSlice(f.writeCtx.buffer, *val)
	case *[]float32:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(FLOAT32_ARRAY)
		err = writeFloat32Slice(f.writeCtx.buffer, *val)
	case *[]float64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(FLOAT64_ARRAY)
		err = writeFloat64Slice(f.writeCtx.buffer, *val)
	case *[]bool:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(BOOL_ARRAY)
		err = writeBoolSlice(f.writeCtx.buffer, *val)
	case *map[string]string:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapStringString(f.writeCtx.buffer, *val)
	case *map[string]int64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapStringInt64(f.writeCtx.buffer, *val)
	case *map[string]int:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapStringInt(f.writeCtx.buffer, *val)
	case *map[string]float64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapStringFloat64(f.writeCtx.buffer, *val)
	case *map[string]bool:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapStringBool(f.writeCtx.buffer, *val)
	case *map[int32]int32:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapInt32Int32(f.writeCtx.buffer, *val)
	case *map[int64]int64:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapInt64Int64(f.writeCtx.buffer, *val)
	case *map[int]int:
		f.writeCtx.buffer.WriteInt8(NotNullValueFlag)
		f.writeCtx.WriteTypeId(MAP)
		writeMapIntInt(f.writeCtx.buffer, *val)
	default:
		// Fall back to reflection-based serialization
		// Reuse v (already boxed) and .Elem() to get underlying value without copy
		return f.serializeReflectValue(reflect.ValueOf(v).Elem())
	}

	if err != nil {
		return nil, err
	}

	// Return copy of buffer data
	return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
}

// Deserialize deserializes data directly into the provided target.
// Takes pointer to avoid interface heap allocation and enable direct writes.
// For primitive types, this writes directly to the target pointer.
// For slices, it reuses existing capacity when possible.
// For structs, it reads directly into the struct fields.
// Note: Fory instance is NOT thread-safe. Use ThreadSafeFory for concurrent use.
func Deserialize[T any](f *Fory, data []byte, target *T) error {
	// Reuse context, reset and set new data
	f.readCtx.Reset()
	f.readCtx.SetData(data)

	// ReadData and validate header
	metaOffset, err := readHeader(f.readCtx)
	if err != nil {
		return err
	}

	// Check if the serialized object is null
	if metaOffset == NullObjectMetaOffset {
		var zero T
		*target = zero
		return nil
	}

	// Fast path: type switch for common types (Go compiler can optimize this)
	switch t := any(target).(type) {
	case *bool:
		return f.readCtx.ReadBoolInto(t, true, true)
	case *int8:
		return f.readCtx.ReadInt8Into(t, true, true)
	case *int16:
		return f.readCtx.ReadInt16Into(t, true, true)
	case *int32:
		return f.readCtx.ReadInt32Into(t, true, true)
	case *int64:
		return f.readCtx.ReadInt64Into(t, true, true)
	case *int:
		return f.readCtx.ReadIntInto(t, true, true)
	case *float32:
		return f.readCtx.ReadFloat32Into(t, true, true)
	case *float64:
		return f.readCtx.ReadFloat64Into(t, true, true)
	case *string:
		return f.readCtx.ReadStringInto(t, true, true)
	case *[]byte:
		return f.readCtx.ReadByteSliceInto(t, true, true)
	case *[]int8:
		return f.readCtx.ReadInt8SliceInto(t, true, true)
	case *[]int16:
		return f.readCtx.ReadInt16SliceInto(t, true, true)
	case *[]int32:
		return f.readCtx.ReadInt32SliceInto(t, true, true)
	case *[]int64:
		return f.readCtx.ReadInt64SliceInto(t, true, true)
	case *[]int:
		return f.readCtx.ReadIntSliceInto(t, true, true)
	case *[]float32:
		return f.readCtx.ReadFloat32SliceInto(t, true, true)
	case *[]float64:
		return f.readCtx.ReadFloat64SliceInto(t, true, true)
	case *[]bool:
		return f.readCtx.ReadBoolSliceInto(t, true, true)
	case *map[string]string:
		return f.readCtx.ReadStringStringMapInto(t, true, true)
	case *map[string]int64:
		return f.readCtx.ReadStringInt64MapInto(t, true, true)
	case *map[string]int:
		return f.readCtx.ReadStringIntMapInto(t, true, true)
	case *map[string]float64:
		return f.readCtx.ReadStringFloat64MapInto(t, true, true)
	case *map[string]bool:
		return f.readCtx.ReadStringBoolMapInto(t, true, true)
	case *map[int32]int32:
		return f.readCtx.ReadInt32Int32MapInto(t, true, true)
	case *map[int64]int64:
		return f.readCtx.ReadInt64Int64MapInto(t, true, true)
	case *map[int]int:
		return f.readCtx.ReadIntIntMapInto(t, true, true)
	}

	// Slow path: use serializer-based deserialization
	targetVal := reflect.ValueOf(target).Elem()
	targetType := targetVal.Type()

	// Get serializer for the target type
	serializer, err := f.typeResolver.getSerializerByType(targetType, false)
	if err != nil {
		return fmt.Errorf("failed to get serializer for type %v: %w", targetType, err)
	}

	// Use Read to deserialize directly into target
	return serializer.Read(f.readCtx, true, true, targetVal)
}

// SerializeAny serializes polymorphic values where concrete type is unknown.
// Uses runtime type dispatch to find the appropriate serializer.
func (f *Fory) SerializeAny(value any) ([]byte, error) {
	defer func() {
		f.writeCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
	}()
	// Check if value is nil interface OR a nil pointer/slice/map/etc.
	// In Go, `*int32(nil)` wrapped in `any` is NOT equal to `nil`, but we need to serialize it as null.
	if isNilValue(value) {
		// Use Java-compatible null format: 3 bytes (magic + bitmap with isNilFlag)
		writeNullHeader(f.writeCtx)
		return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
	}
	// WriteData protocol header
	writeHeader(f.writeCtx, f.config)

	// In compatible mode, reserve space for meta offset (matches C++/Java)
	var metaStartOffset int
	if f.config.Compatible {
		metaStartOffset = f.writeCtx.buffer.writerIndex
		f.writeCtx.buffer.WriteInt32(-1) // Placeholder for meta offset
	}

	// Serialize the value
	if err := f.writeCtx.WriteValue(reflect.ValueOf(value)); err != nil {
		return nil, err
	}

	// WriteData collected TypeMetas at the end in compatible mode (matches C++/Java)
	if f.config.Compatible && f.metaContext != nil && len(f.metaContext.writingTypeDefs) > 0 {
		// Calculate offset from the position after meta offset field to meta section start
		currentPos := f.writeCtx.buffer.writerIndex
		offset := currentPos - metaStartOffset - 4
		// Update the meta offset field
		f.writeCtx.buffer.PutInt32(metaStartOffset, int32(offset))
		// WriteData type definitions
		f.typeResolver.writeTypeDefs(f.writeCtx.buffer)
	}

	return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
}

// serializeReflectValue serializes a reflect.Value directly, avoiding boxing overhead.
// This is used by Serialize[T] fallback path to avoid struct copy.
func (f *Fory) serializeReflectValue(value reflect.Value) ([]byte, error) {
	// WriteData protocol header
	writeHeader(f.writeCtx, f.config)
	// In compatible mode, reserve space for meta offset (matches C++/Java)
	var metaStartOffset int
	if f.config.Compatible {
		metaStartOffset = f.writeCtx.buffer.writerIndex
		f.writeCtx.buffer.WriteInt32(-1) // Placeholder for meta offset
	}

	// Serialize the value
	if err := f.writeCtx.WriteValue(value); err != nil {
		return nil, err
	}

	// WriteData collected TypeMetas at the end in compatible mode (matches C++/Java)
	if f.config.Compatible && f.metaContext != nil && len(f.metaContext.writingTypeDefs) > 0 {
		// Calculate offset from the position after meta offset field to meta section start
		currentPos := f.writeCtx.buffer.writerIndex
		offset := currentPos - metaStartOffset - 4

		// Update the meta offset field
		f.writeCtx.buffer.PutInt32(metaStartOffset, int32(offset))

		// WriteData type definitions
		f.typeResolver.writeTypeDefs(f.writeCtx.buffer)
	}

	return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
}

// SerializeAnyTo serializes a value and appends the bytes to the provided buffer.
// This is useful when you need to write multiple serialized values to the same buffer.
// Returns error if serialization fails.
func (f *Fory) SerializeAnyTo(buf *ByteBuffer, value interface{}) error {
	// Handle nil values
	if isNilValue(value) {
		// Use Java-compatible null format: 3 bytes (magic + bitmap with isNilFlag)
		buf.WriteInt16(MAGIC_NUMBER)
		buf.WriteByte_(IsNilFlag)
		return nil
	}

	defer func() {
		f.writeCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
	}()

	// Temporarily swap buffer
	origBuffer := f.writeCtx.buffer
	f.writeCtx.buffer = buf

	// Write protocol header
	writeHeader(f.writeCtx, f.config)

	// In compatible mode, reserve space for meta offset
	var metaStartOffset int
	if f.config.Compatible {
		metaStartOffset = buf.writerIndex
		buf.WriteInt32(-1) // Placeholder for meta offset
	}

	// Serialize the value
	if err := f.writeCtx.WriteValue(reflect.ValueOf(value)); err != nil {
		f.writeCtx.buffer = origBuffer
		return err
	}

	// Write collected TypeMetas at the end in compatible mode
	if f.config.Compatible && f.metaContext != nil && len(f.metaContext.writingTypeDefs) > 0 {
		// Calculate offset from the position after meta offset field to meta section start
		currentPos := buf.writerIndex
		offset := currentPos - metaStartOffset - 4

		// Update the meta offset field
		buf.PutInt32(metaStartOffset, int32(offset))

		// Write type definitions
		f.typeResolver.writeTypeDefs(buf)
	}

	// Restore original buffer
	f.writeCtx.buffer = origBuffer
	return nil
}

// DeserializeAny deserializes polymorphic values.
// Returns the concrete type as `any`.
func (f *Fory) DeserializeAny(data []byte) (any, error) {
	defer func() {
		f.readCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
	}()
	f.readCtx.SetData(data)

	metaOffset, err := readHeader(f.readCtx)
	if err != nil {
		return nil, err
	}

	// Check if the serialized object is null
	if metaOffset == NullObjectMetaOffset {
		return nil, nil
	}

	// In compatible mode, load type definitions if meta offset is present
	var finalPos int
	if f.config.Compatible && metaOffset > 0 {
		// Save current position (right after meta offset field, before object data)
		dataStartPos := f.readCtx.buffer.ReaderIndex()

		// Jump to meta section and read type definitions
		metaPos := dataStartPos + int(metaOffset)
		f.readCtx.buffer.SetReaderIndex(metaPos)

		if err := f.typeResolver.readTypeDefs(f.readCtx.buffer); err != nil {
			return nil, fmt.Errorf("failed to read type definitions: %w", err)
		}

		// Save final position (after reading TypeDefs)
		finalPos = f.readCtx.buffer.ReaderIndex()

		// Return to data start position to deserialize the object
		f.readCtx.buffer.SetReaderIndex(dataStartPos)
	}

	// Use ReadValue to deserialize through the typeResolver
	var result interface{}
	if err := f.readCtx.ReadValue(reflect.ValueOf(&result).Elem()); err != nil {
		return nil, err
	}

	// Restore final position if we loaded type definitions
	if finalPos > 0 {
		f.readCtx.buffer.SetReaderIndex(finalPos)
	}

	return result, nil
}

// DeserializeAnyFrom deserializes a polymorphic value from an existing buffer.
// The buffer's reader index is advanced as data is read.
// This is useful when reading multiple serialized values from the same buffer.
func (f *Fory) DeserializeAnyFrom(buf *ByteBuffer) (any, error) {
	// Reset contexts for each independent serialized object
	defer func() {
		f.writeCtx.Reset()
		if f.metaContext != nil {
			f.metaContext.Reset()
		}
	}()

	// Temporarily swap buffer
	origBuffer := f.readCtx.buffer
	f.readCtx.buffer = buf

	metaOffset, err := readHeader(f.readCtx)
	if err != nil {
		f.readCtx.buffer = origBuffer
		return nil, err
	}

	// Check if the serialized object is null
	if metaOffset == NullObjectMetaOffset {
		f.readCtx.buffer = origBuffer
		return nil, nil
	}

	// In compatible mode, load type definitions if meta offset is present
	var finalPos int
	if f.config.Compatible && metaOffset > 0 {
		// Save current position (right after meta offset field, before object data)
		dataStartPos := buf.ReaderIndex()

		// Jump to meta section and read type definitions
		metaPos := dataStartPos + int(metaOffset)
		buf.SetReaderIndex(metaPos)

		if err := f.typeResolver.readTypeDefs(buf); err != nil {
			f.readCtx.buffer = origBuffer
			return nil, fmt.Errorf("failed to read type definitions: %w", err)
		}

		// Save final position (after reading TypeDefs)
		finalPos = buf.ReaderIndex()

		// Return to data start position to deserialize the object
		buf.SetReaderIndex(dataStartPos)
	}

	// Use ReadValue to deserialize through the typeResolver
	var result interface{}
	if err := f.readCtx.ReadValue(reflect.ValueOf(&result).Elem()); err != nil {
		f.readCtx.buffer = origBuffer
		return nil, err
	}

	// Restore final position if we loaded type definitions
	if finalPos > 0 {
		buf.SetReaderIndex(finalPos)
	}

	// Restore original buffer
	f.readCtx.buffer = origBuffer

	return result, nil
}

// ============================================================================
// Protocol Header
// ============================================================================

// writeHeader writes the Fory protocol header
func writeHeader(ctx *WriteContext, config Config) {
	ctx.buffer.WriteInt16(MAGIC_NUMBER)

	var bitmap byte = 0
	if nativeEndian == binary.LittleEndian {
		bitmap |= LittleEndianFlag
	}
	if config.IsXlang {
		bitmap |= XLangFlag
	}
	if ctx.outOfBand {
		bitmap |= OutOfBandFlag
	}
	ctx.buffer.WriteByte_(bitmap)
	ctx.buffer.WriteByte_(LangGO)
}

// isNilValue checks if a value is nil, including nil pointers wrapped in interface{}
// In Go, `*int32(nil)` wrapped in `any` is NOT equal to `nil`, but we need to treat it as null.
func isNilValue(value any) bool {
	if value == nil {
		return true
	}
	rv := reflect.ValueOf(value)
	switch rv.Kind() {
	case reflect.Ptr, reflect.Slice, reflect.Map, reflect.Chan, reflect.Func, reflect.Interface:
		return rv.IsNil()
	}
	return false
}

// writeNullHeader writes a null object header (3 bytes: magic + bitmap with isNilFlag)
// This is compatible with Java's null serialization format
func writeNullHeader(ctx *WriteContext) {
	ctx.buffer.WriteInt16(MAGIC_NUMBER)
	ctx.buffer.WriteByte_(IsNilFlag) // bitmap with only isNilFlag set
}

// Special return value indicating null object in readHeader
// Using math.MinInt32 to avoid conflict with -1 which is used for "no meta offset"
const NullObjectMetaOffset int32 = -0x7FFFFFFF

// readHeader reads and validates the Fory protocol header
// Returns the meta start offset if present (0 if not present)
// Returns NullObjectMetaOffset if the serialized object is null
func readHeader(ctx *ReadContext) (int32, error) {
	magicNumber := ctx.buffer.ReadInt16()
	if magicNumber != MAGIC_NUMBER {
		return 0, ErrMagicNumber
	}
	bitmap := ctx.buffer.ReadByte_()

	// Check if this is a null object - only magic number + bitmap with isNilFlag was written
	if (bitmap & IsNilFlag) != 0 {
		return NullObjectMetaOffset, nil
	}

	_ = ctx.buffer.ReadByte_() // language

	// In compatible mode with meta share, Java writes a 4-byte meta offset
	// We need to read it but we'll handle type defs later
	if ctx.compatible {
		metaOffset := ctx.buffer.ReadInt32()
		return metaOffset, nil
	}

	return 0, nil
}
