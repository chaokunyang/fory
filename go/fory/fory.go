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
	"sync"
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

// Language represents the programming language
type Language = uint8

const (
	XLANG Language = iota
	JAVA
	PYTHON
	CPP
	GO
	JAVASCRIPT
	RUST
	DART
)

// Protocol constants
const (
	MAGIC_NUMBER int16 = 0x62D4
)

// Bitmap flags for protocol header
const (
	NilFlag          = 0
	LittleEndianFlag = 2
	XLangFlag        = 4
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
	RefTracking bool
	MaxDepth    int
	Language    Language
	Compatible  bool // Schema evolution compatibility mode
}

// defaultConfig returns the default configuration
func defaultConfig() Config {
	return Config{
		RefTracking: true,
		MaxDepth:    100,
		Language:    XLANG,
	}
}

// Option is a function that configures a Fory instance
type Option func(*Fory)

// WithRefTracking sets reference tracking mode
func WithRefTracking(enabled bool) Option {
	return func(f *Fory) {
		f.config.RefTracking = enabled
	}
}

// WithMaxDepth sets the maximum serialization depth
func WithMaxDepth(depth int) Option {
	return func(f *Fory) {
		f.config.MaxDepth = depth
	}
}

// WithLanguage sets the language mode
func WithLanguage(lang Language) Option {
	return func(f *Fory) {
		f.config.Language = lang
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

// MetaContext holds metadata for schema evolution and type sharing
type MetaContext struct {
	typeMap               map[reflect.Type]uint32
	writingTypeDefs       []*TypeDef
	readTypeInfos         []TypeInfo
	scopedMetaShareEnable bool
}

// IsScopedMetaShareEnabled returns whether scoped meta share is enabled
func (m *MetaContext) IsScopedMetaShareEnabled() bool {
	return m.scopedMetaShareEnable
}

// Fory is the main serialization instance.
// Note: Fory is NOT thread-safe. Use ThreadSafeFory for concurrent use.
type Fory struct {
	config      Config
	registry    *GenericRegistry
	metaContext *MetaContext

	// Reusable contexts - avoid allocation on each Serialize/Deserialize call
	writeCtx *WriteContext
	readCtx  *ReadContext

	// Resolvers shared between contexts
	typeResolver *typeResolver
	refResolver  *RefResolver
}

// MetaContext returns the meta context for schema evolution
func (f *Fory) MetaContext() *MetaContext {
	return f.metaContext
}

// RegisterNamedType registers a named type for cross-language serialization
// type_ can be either a reflect.Type or an instance of the type
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
	return SerializeAny(f, v)
}

// Unmarshal deserializes bytes into the provided value (instance method for backward compatibility)
func (f *Fory) Unmarshal(data []byte, v interface{}) error {
	result, err := DeserializeAny(f, data)
	if err != nil {
		return err
	}
	if v == nil {
		return nil
	}
	// Set the result into the provided value
	rv := reflect.ValueOf(v)
	if rv.Kind() != reflect.Ptr || rv.IsNil() {
		return fmt.Errorf("v must be a non-nil pointer")
	}
	if result != nil {
		resultVal := reflect.ValueOf(result)
		if resultVal.Type().AssignableTo(rv.Elem().Type()) {
			rv.Elem().Set(resultVal)
		} else if resultVal.Type().ConvertibleTo(rv.Elem().Type()) {
			rv.Elem().Set(resultVal.Convert(rv.Elem().Type()))
		}
	}
	return nil
}

// Serialize is an alias for Marshal (instance method for backward compatibility)
func (f *Fory) Serialize(v interface{}) ([]byte, error) {
	return f.Marshal(v)
}

// Deserialize deserializes bytes into the provided value (instance method for backward compatibility)
func (f *Fory) Deserialize(data []byte, v interface{}) error {
	return f.Unmarshal(data, v)
}

// RegisterByNamespace registers a type with namespace for cross-language serialization
func (f *Fory) RegisterByNamespace(type_ interface{}, namespace, typeName string) error {
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

// New creates a new Fory instance with the given options
func New(opts ...Option) *Fory {
	f := &Fory{
		config:   defaultConfig(),
		registry: GetGlobalRegistry(),
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
	f.refResolver = newRefResolver(f.config.RefTracking)

	// Initialize reusable contexts with resolvers
	f.writeCtx = NewWriteContext(f.registry, f.config.RefTracking, f.config.MaxDepth)
	f.writeCtx.typeResolver = f.typeResolver
	f.writeCtx.refResolver = f.refResolver
	f.writeCtx.compatible = f.config.Compatible

	f.readCtx = NewReadContext(f.registry, f.config.RefTracking)
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
// Note: Fory instance is NOT thread-safe. Use ThreadSafeFory for concurrent use.
func Serialize[T any](f *Fory, value T) ([]byte, error) {
	serializer := getSerializer[T](f.registry)

	// Reuse context, just reset state
	f.writeCtx.Reset()

	// Write protocol header
	writeHeader(f.writeCtx, f.config)

	// Always pass true from top level - each serializer decides internally:
	// - Value types: write NotNullValueFlag, no ref tracking
	// - Reference types: handle null check, ref tracking if enabled
	// - Polymorphic types: write runtime type info
	if err := serializer.Write(f.writeCtx, value, true, true); err != nil {
		return nil, err
	}

	// Return copy of buffer data
	return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
}

// Deserialize - serializer auto-resolved from type T.
// The serializer handles its own ref/type info reading internally.
// Note: Fory instance is NOT thread-safe. Use ThreadSafeFory for concurrent use.
func Deserialize[T any](f *Fory, data []byte) (T, error) {
	serializer := getSerializer[T](f.registry)

	var zero T

	// Reuse context, reset and set new data
	f.readCtx.Reset()
	f.readCtx.SetData(data)

	// Read and validate header
	if err := readHeader(f.readCtx); err != nil {
		return zero, err
	}

	// Always pass true from top level - each serializer decides internally
	// how to handle ref flag and type info based on its type characteristics
	return serializer.Read(f.readCtx, true, true)
}

// SerializeAny serializes polymorphic values where concrete type is unknown.
// Uses runtime type dispatch to find the appropriate serializer.
func SerializeAny(f *Fory, value any) ([]byte, error) {
	if value == nil {
		f.writeCtx.Reset()
		writeHeader(f.writeCtx, f.config)
		f.writeCtx.buffer.WriteInt8(NullFlag)
		return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
	}

	f.writeCtx.Reset()
	writeHeader(f.writeCtx, f.config)

	// Use WriteValue to serialize the value through the typeResolver
	if err := f.writeCtx.WriteValue(reflect.ValueOf(value)); err != nil {
		return nil, err
	}

	return f.writeCtx.buffer.GetByteSlice(0, f.writeCtx.buffer.writerIndex), nil
}

// DeserializeAny deserializes polymorphic values.
// Returns the concrete type as `any`.
func DeserializeAny(f *Fory, data []byte) (any, error) {
	f.readCtx.Reset()
	f.readCtx.SetData(data)

	if err := readHeader(f.readCtx); err != nil {
		return nil, err
	}

	// Use ReadValue to deserialize through the typeResolver
	var result interface{}
	if err := f.readCtx.ReadValue(reflect.ValueOf(&result).Elem()); err != nil {
		return nil, err
	}

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
	if config.Language == XLANG {
		bitmap |= XLangFlag
	}
	ctx.buffer.WriteByte_(bitmap)
	ctx.buffer.WriteByte_(GO)
}

// readHeader reads and validates the Fory protocol header
func readHeader(ctx *ReadContext) error {
	magicNumber := ctx.buffer.ReadInt16()
	if magicNumber != MAGIC_NUMBER {
		return ErrMagicNumber
	}
	_ = ctx.buffer.ReadByte_() // bitmap
	_ = ctx.buffer.ReadByte_() // language
	return nil
}

// ============================================================================
// Registry Lookup
// ============================================================================

// getSerializer retrieves serializer with zero allocation (compile-time typed).
// Panics if no serializer is registered for type T.
func getSerializer[T any](r *GenericRegistry) TypedSerializer[T] {
	t := reflect.TypeFor[T]()

	r.mu.RLock()
	s, ok := r.serializers[t]
	r.mu.RUnlock()

	if !ok {
		panic("fory: no serializer for type " + t.String() +
			". Use fory.Register[T]() to register a serializer")
	}
	return s.(TypedSerializer[T])
}

// TryGetSerializer retrieves serializer, returning error if not found
func TryGetSerializer[T any](r *GenericRegistry) (TypedSerializer[T], error) {
	t := reflect.TypeFor[T]()

	r.mu.RLock()
	s, ok := r.serializers[t]
	r.mu.RUnlock()

	if !ok {
		return nil, ErrNoSerializer
	}

	typed, ok := s.(TypedSerializer[T])
	if !ok {
		return nil, ErrNoSerializer
	}
	return typed, nil
}

// MustGetSerializer retrieves serializer from global registry, panics if not found
func MustGetSerializer[T any]() TypedSerializer[T] {
	return getSerializer[T](globalGenericRegistry)
}

// ============================================================================
// ThreadSafeFory - Thread-safe wrapper using sync.Pool
// ============================================================================

// ThreadSafeFory is a thread-safe wrapper around Fory using sync.Pool.
// It provides the same API as Fory but is safe for concurrent use.
type ThreadSafeFory struct {
	pool   sync.Pool
	config Config
}

// NewThreadSafe creates a new thread-safe Fory instance
func NewThreadSafe(opts ...Option) *ThreadSafeFory {
	// Create a temporary Fory to extract config
	tmpFory := &Fory{config: defaultConfig(), registry: GetGlobalRegistry()}
	for _, opt := range opts {
		opt(tmpFory)
	}

	tsf := &ThreadSafeFory{
		config: tmpFory.config,
	}
	tsf.pool = sync.Pool{
		New: func() any {
			return New(opts...)
		},
	}
	return tsf
}

func (tsf *ThreadSafeFory) acquire() *Fory {
	return tsf.pool.Get().(*Fory)
}

func (tsf *ThreadSafeFory) release(f *Fory) {
	f.Reset()
	tsf.pool.Put(f)
}

// Serialize serializes a value using a pooled Fory instance
func (tsf *ThreadSafeFory) Serialize(v interface{}) ([]byte, error) {
	f := tsf.acquire()
	defer tsf.release(f)
	return f.Marshal(v)
}

// Deserialize deserializes data into the provided value using a pooled Fory instance
func (tsf *ThreadSafeFory) Deserialize(data []byte, v interface{}) error {
	f := tsf.acquire()
	defer tsf.release(f)
	return f.Unmarshal(data, v)
}

// SerializeTS - type T inferred, serializer auto-resolved, thread-safe.
func SerializeTS[T any](tsf *ThreadSafeFory, value T) ([]byte, error) {
	f := tsf.acquire()
	defer tsf.release(f)
	return Serialize(f, value)
}

// DeserializeTS - serializer auto-resolved from type T, thread-safe.
func DeserializeTS[T any](tsf *ThreadSafeFory, data []byte) (T, error) {
	f := tsf.acquire()
	defer tsf.release(f)
	return Deserialize[T](f, data)
}

// SerializeAnyTS serializes polymorphic values, thread-safe.
func SerializeAnyTS(tsf *ThreadSafeFory, value any) ([]byte, error) {
	f := tsf.acquire()
	defer tsf.release(f)
	return SerializeAny(f, value)
}

// DeserializeAnyTS deserializes polymorphic values, thread-safe.
func DeserializeAnyTS(tsf *ThreadSafeFory, data []byte) (any, error) {
	f := tsf.acquire()
	defer tsf.release(f)
	return DeserializeAny(f, data)
}

// ============================================================================
// Convenience Functions
// ============================================================================

// Global thread-safe Fory instance for convenience
var globalFory = NewThreadSafe()

// Marshal serializes a value using the global thread-safe instance
func Marshal[T any](value T) ([]byte, error) {
	return SerializeTS(globalFory, value)
}

// Unmarshal deserializes data using the global thread-safe instance (generic version)
func Unmarshal[T any](data []byte) (T, error) {
	return DeserializeTS[T](globalFory, data)
}

// UnmarshalTo deserializes data into the provided pointer using the global thread-safe instance
func UnmarshalTo(data []byte, v interface{}) error {
	f := globalFory.acquire()
	defer globalFory.release(f)
	return f.Unmarshal(data, v)
}
