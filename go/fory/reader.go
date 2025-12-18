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
	"fmt"
	"reflect"
	"strconv"
	"unsafe"
)

// ============================================================================
// ReadContext - Holds all state needed during deserialization
// ============================================================================

// ReadContext holds all state needed during deserialization.
type ReadContext struct {
	buffer           *ByteBuffer
	refReader        *RefReader
	trackRef         bool          // Cached flag to avoid indirection
	compatible       bool          // Schema evolution compatibility mode
	typeResolver     *TypeResolver // For complex type deserialization
	refResolver      *RefResolver  // For reference tracking (legacy)
	outOfBandBuffers []*ByteBuffer // Out-of-band buffers for deserialization
	outOfBandIndex   int           // Current index into out-of-band buffers
	depth            int           // Current nesting depth for cycle detection
	maxDepth         int           // Maximum allowed nesting depth
}

// NewReadContext creates a new read context
func NewReadContext(trackRef bool) *ReadContext {
	return &ReadContext{
		buffer:    NewByteBuffer(nil),
		refReader: NewRefReader(trackRef),
		trackRef:  trackRef,
		maxDepth:  128, // Default maximum nesting depth
	}
}

// Reset clears state for reuse (called before each Deserialize)
func (c *ReadContext) Reset() {
	c.refReader.Reset()
	c.outOfBandBuffers = nil
	c.outOfBandIndex = 0
	if c.refResolver != nil {
		c.refResolver.resetRead()
	}
	if c.typeResolver != nil {
		c.typeResolver.resetRead()
	}
}

// SetData sets new input data (for buffer reuse)
func (c *ReadContext) SetData(data []byte) {
	c.buffer = NewByteBuffer(data)
}

// Buffer returns the underlying buffer
func (c *ReadContext) Buffer() *ByteBuffer {
	return c.buffer
}

// TrackRef returns whether reference tracking is enabled
func (c *ReadContext) TrackRef() bool {
	return c.trackRef
}

// Compatible returns whether schema evolution compatibility mode is enabled
func (c *ReadContext) Compatible() bool {
	return c.compatible
}

// TypeResolver returns the type resolver
func (c *ReadContext) TypeResolver() *TypeResolver {
	return c.typeResolver
}

// RefResolver returns the reference resolver (legacy)
func (c *ReadContext) RefResolver() *RefResolver {
	return c.refResolver
}

// Inline primitive reads
func (c *ReadContext) RawBool() bool         { return c.buffer.ReadBool() }
func (c *ReadContext) RawInt8() int8         { return int8(c.buffer.ReadByte_()) }
func (c *ReadContext) RawInt16() int16       { return c.buffer.ReadInt16() }
func (c *ReadContext) RawInt32() int32       { return c.buffer.ReadInt32() }
func (c *ReadContext) RawInt64() int64       { return c.buffer.ReadInt64() }
func (c *ReadContext) RawFloat32() float32   { return c.buffer.ReadFloat32() }
func (c *ReadContext) RawFloat64() float64   { return c.buffer.ReadFloat64() }
func (c *ReadContext) ReadVarint32() int32   { return c.buffer.ReadVarint32() }
func (c *ReadContext) ReadVarint64() int64   { return c.buffer.ReadVarint64() }
func (c *ReadContext) ReadVaruint32() uint32 { return c.buffer.ReadVaruint32() }
func (c *ReadContext) ReadByte() byte        { return c.buffer.ReadByte_() }

func (c *ReadContext) RawString() string {
	length := c.buffer.ReadVaruint32()
	if length == 0 {
		return ""
	}
	data := c.buffer.ReadBinary(int(length))
	return string(data)
}

func (c *ReadContext) ReadBinary() []byte {
	length := c.buffer.ReadVaruint32()
	return c.buffer.ReadBinary(int(length))
}

func (c *ReadContext) ReadTypeId() TypeId {
	// Use Varuint32Small7 encoding to match Java's xlang serialization
	return TypeId(c.buffer.ReadVaruint32Small7())
}

// readFast reads a value using fast path based on StaticTypeId
func (c *ReadContext) readFast(ptr unsafe.Pointer, ct StaticTypeId) {
	switch ct {
	case ConcreteTypeBool:
		*(*bool)(ptr) = c.buffer.ReadBool()
	case ConcreteTypeInt8:
		*(*int8)(ptr) = int8(c.buffer.ReadByte_())
	case ConcreteTypeInt16:
		*(*int16)(ptr) = c.buffer.ReadInt16()
	case ConcreteTypeInt32:
		*(*int32)(ptr) = c.buffer.ReadVarint32()
	case ConcreteTypeInt:
		if strconv.IntSize == 64 {
			*(*int)(ptr) = int(c.buffer.ReadVarint64())
		} else {
			*(*int)(ptr) = int(c.buffer.ReadVarint32())
		}
	case ConcreteTypeInt64:
		*(*int64)(ptr) = c.buffer.ReadVarint64()
	case ConcreteTypeFloat32:
		*(*float32)(ptr) = c.buffer.ReadFloat32()
	case ConcreteTypeFloat64:
		*(*float64)(ptr) = c.buffer.ReadFloat64()
	case ConcreteTypeString:
		*(*string)(ptr) = readString(c.buffer)
	}
}

// ReadAndValidateTypeId reads type ID and validates it matches expected
func (c *ReadContext) ReadAndValidateTypeId(expected TypeId) error {
	actual := c.ReadTypeId()
	if actual != expected {
		return ErrTypeMismatch
	}
	return nil
}

// ReadLength reads a length value as varint (non-negative values)
func (c *ReadContext) ReadLength() int {
	return int(c.buffer.ReadVaruint32())
}

// ============================================================================
// Typed Read Methods - Fastpath for codegen
// For primitive numeric types, use ctx.Buffer().ReadXXX()
// For strings, use ctx.ReadString()
// For slices/maps, use these methods which handle ref tracking
// ============================================================================

// ReadString reads a string value (caller handles nullable/type meta)
func (c *ReadContext) ReadString() string {
	return readString(c.buffer)
}

// ReadBoolSlice reads []bool with ref/type info
func (c *ReadContext) ReadBoolSlice(refMode RefMode, readType bool) ([]bool, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readBoolSlice(c.buffer)
}

// ReadInt8Slice reads []int8 with optional ref/type info
func (c *ReadContext) ReadInt8Slice(refMode RefMode, readType bool) ([]int8, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readInt8Slice(c.buffer)
}

// ReadInt16Slice reads []int16 with optional ref/type info
func (c *ReadContext) ReadInt16Slice(refMode RefMode, readType bool) ([]int16, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readInt16Slice(c.buffer)
}

// ReadInt32Slice reads []int32 with optional ref/type info
func (c *ReadContext) ReadInt32Slice(refMode RefMode, readType bool) ([]int32, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readInt32Slice(c.buffer)
}

// ReadInt64Slice reads []int64 with optional ref/type info
func (c *ReadContext) ReadInt64Slice(refMode RefMode, readType bool) ([]int64, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readInt64Slice(c.buffer)
}

// ReadIntSlice reads []int with optional ref/type info
func (c *ReadContext) ReadIntSlice(refMode RefMode, readType bool) ([]int, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readIntSlice(c.buffer)
}

// ReadFloat32Slice reads []float32 with optional ref/type info
func (c *ReadContext) ReadFloat32Slice(refMode RefMode, readType bool) ([]float32, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readFloat32Slice(c.buffer)
}

// ReadFloat64Slice reads []float64 with optional ref/type info
func (c *ReadContext) ReadFloat64Slice(refMode RefMode, readType bool) ([]float64, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readFloat64Slice(c.buffer)
}

// ReadByteSlice reads []byte with optional ref/type info
func (c *ReadContext) ReadByteSlice(refMode RefMode, readType bool) ([]byte, error) {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	isInBand := c.buffer.ReadBool()
	if !isInBand {
		return nil, fmt.Errorf("out-of-band byte slice not supported in fast path")
	}
	size := c.buffer.ReadLength()
	return c.buffer.ReadBinary(size), nil
}

// ReadStringStringMap reads map[string]string with optional ref/type info
func (c *ReadContext) ReadStringStringMap(refMode RefMode, readType bool) map[string]string {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapStringString(c.buffer)
}

// ReadStringInt64Map reads map[string]int64 with optional ref/type info
func (c *ReadContext) ReadStringInt64Map(refMode RefMode, readType bool) map[string]int64 {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapStringInt64(c.buffer)
}

// ReadStringIntMap reads map[string]int with optional ref/type info
func (c *ReadContext) ReadStringIntMap(refMode RefMode, readType bool) map[string]int {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapStringInt(c.buffer)
}

// ReadStringFloat64Map reads map[string]float64 with optional ref/type info
func (c *ReadContext) ReadStringFloat64Map(refMode RefMode, readType bool) map[string]float64 {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapStringFloat64(c.buffer)
}

// ReadStringBoolMap reads map[string]bool with optional ref/type info
func (c *ReadContext) ReadStringBoolMap(refMode RefMode, readType bool) map[string]bool {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapStringBool(c.buffer)
}

// ReadInt32Int32Map reads map[int32]int32 with optional ref/type info
func (c *ReadContext) ReadInt32Int32Map(refMode RefMode, readType bool) map[int32]int32 {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapInt32Int32(c.buffer)
}

// ReadInt64Int64Map reads map[int64]int64 with optional ref/type info
func (c *ReadContext) ReadInt64Int64Map(refMode RefMode, readType bool) map[int64]int64 {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapInt64Int64(c.buffer)
}

// ReadIntIntMap reads map[int]int with optional ref/type info
func (c *ReadContext) ReadIntIntMap(refMode RefMode, readType bool) map[int]int {
	if refMode != RefModeNone {
		_ = c.buffer.ReadInt8()
	}
	if readType {
		_ = c.buffer.ReadVaruint32Small7()
	}
	return readMapIntInt(c.buffer)
}

// ReadBufferObject reads a buffer object
func (c *ReadContext) ReadBufferObject() (*ByteBuffer, error) {
	isInBand := c.buffer.ReadBool()
	if isInBand {
		size := c.buffer.ReadLength()
		buf := c.buffer.Slice(c.buffer.readerIndex, size)
		c.buffer.readerIndex += size
		return buf, nil
	}
	// Out-of-band: get the next buffer from the out-of-band buffers list
	if c.outOfBandBuffers == nil || c.outOfBandIndex >= len(c.outOfBandBuffers) {
		return nil, fmt.Errorf("out-of-band buffer expected but not available at index %d", c.outOfBandIndex)
	}
	buf := c.outOfBandBuffers[c.outOfBandIndex]
	c.outOfBandIndex++
	return buf, nil
}

// incDepth increments the nesting depth and checks for overflow
func (c *ReadContext) incDepth() error {
	c.depth++
	if c.depth > c.maxDepth {
		return fmt.Errorf("maximum nesting depth exceeded: %d", c.maxDepth)
	}
	return nil
}

// decDepth decrements the nesting depth
func (c *ReadContext) decDepth() {
	c.depth--
}

// ReadValue reads a polymorphic value - queries serializer by type and deserializes
func (c *ReadContext) ReadValue(value reflect.Value) error {
	if !value.IsValid() {
		return fmt.Errorf("invalid reflect.Value")
	}

	// For interface{} types, we need to read the actual type from the buffer first
	if value.Type().Kind() == reflect.Interface {
		// Read ref flag
		refID, err := c.RefResolver().TryPreserveRefId(c.buffer)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := c.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}

		// Read type info to determine the actual type
		typeInfo, err := c.typeResolver.readTypeInfo(c.buffer, value)
		if err != nil {
			return fmt.Errorf("failed to read type info for interface: %w", err)
		}

		// Create a new instance of the actual type
		actualType := typeInfo.Type
		if actualType == nil {
			// Unknown type - skip the data using the serializer (skipStructSerializer)
			if typeInfo.Serializer != nil {
				if err := typeInfo.Serializer.ReadData(c, nil, reflect.Value{}); err != nil {
					return fmt.Errorf("failed to skip unknown type data: %w", err)
				}
			}
			// Leave interface value as nil for unknown types
			return nil
		}

		// Create a new instance
		var newValue reflect.Value
		var valueToSet reflect.Value
		internalTypeID := TypeId(typeInfo.TypeID & 0xFF)

		// For named struct types, create a pointer type to support circular references.
		// In Java/xlang serialization, objects are always by reference, so when deserializing
		// into interface{}, we need to use pointers to maintain reference semantics.
		isNamedStruct := actualType.Kind() == reflect.Struct &&
			(internalTypeID == NAMED_STRUCT || internalTypeID == NAMED_COMPATIBLE_STRUCT ||
				internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT)

		if actualType.Kind() == reflect.Ptr {
			// For pointer types, create a pointer directly
			// The serializer's ReadData will handle allocating and reading the element
			newValue = reflect.New(actualType).Elem()
			valueToSet = newValue
		} else if isNamedStruct {
			// For named struct types, create a pointer to support circular references
			// Create *A instead of A
			newValue = reflect.New(actualType)
			valueToSet = newValue
		} else {
			newValue = reflect.New(actualType).Elem()
			valueToSet = newValue
		}

		// For named structs, register the pointer BEFORE reading data
		// This is critical for circular references to work correctly
		if isNamedStruct && int8(refID) >= NotNullValueFlag {
			c.RefResolver().SetReadObject(refID, newValue)
		}

		// For named structs, read into the pointer's element
		var readTarget reflect.Value
		if isNamedStruct {
			readTarget = newValue.Elem()
		} else {
			readTarget = newValue
		}

		if err := typeInfo.Serializer.ReadData(c, actualType, readTarget); err != nil {
			return err
		}

		// Register reference after reading data for non-struct types
		if !isNamedStruct && int8(refID) >= NotNullValueFlag {
			c.RefResolver().SetReadObject(refID, newValue)
		}

		// Set the interface value
		value.Set(valueToSet)
		return nil
	}

	// Get serializer for the value's type
	serializer, err := c.typeResolver.getSerializerByType(value.Type(), false)
	if err != nil {
		return fmt.Errorf("failed to get serializer for type %v: %w", value.Type(), err)
	}

	// Read handles ref tracking and type info internally
	return serializer.Read(c, RefModeTracking, true, value)
}

// ReadInto reads a value using a specific serializer with optional ref/type info
func (c *ReadContext) ReadInto(value reflect.Value, serializer Serializer, refMode RefMode, readTypeInfo bool) error {
	if !value.IsValid() {
		return fmt.Errorf("invalid reflect.Value")
	}
	if serializer == nil {
		return fmt.Errorf("serializer cannot be nil")
	}

	return serializer.Read(c, refMode, readTypeInfo, value)
}
