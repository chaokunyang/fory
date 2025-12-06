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
	"errors"
	"fmt"
	"reflect"
	"unsafe"
)

// ErrTypeMismatch indicates a type ID mismatch during deserialization
var ErrTypeMismatch = errors.New("fory: type ID mismatch")

// ============================================================================
// WriteContext - Holds all state needed during serialization
// ============================================================================

// WriteContext holds all state needed during serialization.
// It replaces passing multiple parameters to every method.
type WriteContext struct {
	buffer         *ByteBuffer
	refWriter      *RefWriter
	trackRef       bool // Cached flag to avoid indirection
	compatible     bool // Schema evolution compatibility mode
	depth          int
	maxDepth       int
	typeResolver   *typeResolver           // For complex type serialization
	refResolver    *RefResolver            // For reference tracking (legacy)
	bufferCallback func(BufferObject) bool // Callback for out-of-band buffers
	outOfBand      bool                    // Whether out-of-band serialization is enabled
}

// NewWriteContext creates a new write context
func NewWriteContext(trackRef bool, maxDepth int) *WriteContext {
	return &WriteContext{
		buffer:    NewByteBuffer(nil),
		refWriter: NewRefWriter(trackRef),
		trackRef:  trackRef,
		maxDepth:  maxDepth,
	}
}

// Reset clears state for reuse (called before each Serialize)
func (c *WriteContext) Reset() {
	c.buffer.Reset()
	c.refWriter.Reset()
	c.depth = 0
	if c.refResolver != nil {
		c.refResolver.resetWrite()
	}
	if c.typeResolver != nil {
		c.typeResolver.resetWrite()
	}
}

// ResetState clears internal state but NOT the buffer.
// Use this when streaming multiple values to an external buffer.
func (c *WriteContext) ResetState() {
	c.refWriter.Reset()
	c.depth = 0
	c.bufferCallback = nil
	c.outOfBand = false
	if c.refResolver != nil {
		c.refResolver.resetWrite()
	}
	if c.typeResolver != nil {
		c.typeResolver.resetWrite()
	}
}

// Buffer returns the underlying buffer
func (c *WriteContext) Buffer() *ByteBuffer {
	return c.buffer
}

// TrackRef returns whether reference tracking is enabled
func (c *WriteContext) TrackRef() bool {
	return c.trackRef
}

// Compatible returns whether schema evolution compatibility mode is enabled
func (c *WriteContext) Compatible() bool {
	return c.compatible
}

// TypeResolver returns the type resolver
func (c *WriteContext) TypeResolver() *typeResolver {
	return c.typeResolver
}

// RefResolver returns the reference resolver (legacy)
func (c *WriteContext) RefResolver() *RefResolver {
	return c.refResolver
}

// Inline primitive writes (compiler will inline these)
func (c *WriteContext) WriteBool(v bool)        { c.buffer.WriteBool(v) }
func (c *WriteContext) WriteInt8(v int8)        { c.buffer.WriteByte_(byte(v)) }
func (c *WriteContext) WriteInt16(v int16)      { c.buffer.WriteInt16(v) }
func (c *WriteContext) WriteInt32(v int32)      { c.buffer.WriteInt32(v) }
func (c *WriteContext) WriteInt64(v int64)      { c.buffer.WriteInt64(v) }
func (c *WriteContext) WriteFloat32(v float32)  { c.buffer.WriteFloat32(v) }
func (c *WriteContext) WriteFloat64(v float64)  { c.buffer.WriteFloat64(v) }
func (c *WriteContext) WriteVarInt32(v int32)   { c.buffer.WriteVarint32(v) }
func (c *WriteContext) WriteVarInt64(v int64)   { c.buffer.WriteVarint64(v) }
func (c *WriteContext) WriteVarUint32(v uint32) { c.buffer.WriteVarUint32(v) }
func (c *WriteContext) WriteByte(v byte)        { c.buffer.WriteByte_(v) }
func (c *WriteContext) WriteBytes(v []byte)     { c.buffer.WriteBinary(v) }

func (c *WriteContext) WriteString(v string) {
	c.buffer.WriteVarUint32(uint32(len(v)))
	if len(v) > 0 {
		c.buffer.WriteBinary(unsafe.Slice(unsafe.StringData(v), len(v)))
	}
}

func (c *WriteContext) WriteBinary(v []byte) {
	c.buffer.WriteVarUint32(uint32(len(v)))
	c.buffer.WriteBinary(v)
}

func (c *WriteContext) WriteTypeId(id TypeId) {
	c.buffer.WriteInt16(id)
}

// WriteLength writes a length value as varint
func (c *WriteContext) WriteLength(length int) error {
	if length > MaxInt32 || length < MinInt32 {
		return fmt.Errorf("length %d exceeds int32 range", length)
	}
	c.buffer.WriteVarInt32(int32(length))
	return nil
}

// ============================================================================
// Typed Write Methods - Write primitives with optional ref/type info
// ============================================================================

// WriteBoolValue writes a bool with optional ref/type info
func (c *WriteContext) WriteBoolValue(value bool, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(BOOL)
	}
	c.buffer.WriteBool(value)
	return nil
}

// WriteInt8Value writes an int8 with optional ref/type info
func (c *WriteContext) WriteInt8Value(value int8, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT8)
	}
	c.buffer.WriteInt8(value)
	return nil
}

// WriteInt16Value writes an int16 with optional ref/type info
func (c *WriteContext) WriteInt16Value(value int16, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT16)
	}
	c.buffer.WriteInt16(value)
	return nil
}

// WriteInt32Value writes an int32 with optional ref/type info
func (c *WriteContext) WriteInt32Value(value int32, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT32)
	}
	c.buffer.WriteVarint32(value)
	return nil
}

// WriteInt64Value writes an int64 with optional ref/type info
func (c *WriteContext) WriteInt64Value(value int64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT64)
	}
	c.buffer.WriteVarint64(value)
	return nil
}

// WriteFloat32Value writes a float32 with optional ref/type info
func (c *WriteContext) WriteFloat32Value(value float32, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(FLOAT)
	}
	c.buffer.WriteFloat32(value)
	return nil
}

// WriteFloat64Value writes a float64 with optional ref/type info
func (c *WriteContext) WriteFloat64Value(value float64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(DOUBLE)
	}
	c.buffer.WriteFloat64(value)
	return nil
}

// WriteStringValue writes a string with optional ref/type info
func (c *WriteContext) WriteStringValue(value string, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(STRING)
	}
	c.buffer.WriteVarUint32(uint32(len(value)))
	if len(value) > 0 {
		c.buffer.WriteBinary(unsafe.Slice(unsafe.StringData(value), len(value)))
	}
	return nil
}

// WriteBoolSlice writes []bool with ref/type info
func (c *WriteContext) WriteBoolSlice(value []bool, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(BOOL_ARRAY)
	}
	c.buffer.WriteLength(len(value))
	for _, elem := range value {
		c.buffer.WriteBool(elem)
	}
	return nil
}

// WriteInt32Slice writes []int32 with ref/type info
func (c *WriteContext) WriteInt32Slice(value []int32, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT32_ARRAY)
	}
	size := len(value) * 4
	c.buffer.WriteLength(size)
	for _, elem := range value {
		c.buffer.WriteInt32(elem)
	}
	return nil
}

// WriteInt64Slice writes []int64 with ref/type info
func (c *WriteContext) WriteInt64Slice(value []int64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(INT64_ARRAY)
	}
	size := len(value) * 8
	c.buffer.WriteLength(size)
	for _, elem := range value {
		c.buffer.WriteInt64(elem)
	}
	return nil
}

// WriteFloat64Slice writes []float64 with ref/type info
func (c *WriteContext) WriteFloat64Slice(value []float64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(FLOAT64_ARRAY)
	}
	size := len(value) * 8
	c.buffer.WriteLength(size)
	for _, elem := range value {
		c.buffer.WriteFloat64(elem)
	}
	return nil
}

// WriteByteSlice writes []byte with ref/type info
func (c *WriteContext) WriteByteSlice(value []byte, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		c.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		c.WriteTypeId(BINARY)
	}
	c.buffer.WriteBool(true) // in-band
	c.buffer.WriteLength(len(value))
	c.buffer.WriteBinary(value)
	return nil
}

// WriteBufferObject writes a buffer object
// If a buffer callback is set and returns false, the buffer is written out-of-band
func (c *WriteContext) WriteBufferObject(bufferObject BufferObject) error {
	// Check if we should write this buffer out-of-band
	inBand := true
	if c.bufferCallback != nil {
		inBand = c.bufferCallback(bufferObject)
	}

	c.buffer.WriteBool(inBand)
	if inBand {
		// Write the buffer data in-band
		size := bufferObject.TotalBytes()
		c.buffer.WriteLength(size)
		writerIndex := c.buffer.writerIndex
		c.buffer.grow(size)
		bufferObject.WriteTo(c.buffer.Slice(writerIndex, size))
		c.buffer.writerIndex += size
		if size > MaxInt32 {
			return fmt.Errorf("length %d exceeds max int32", size)
		}
	}
	// If out-of-band, we just write false (already done above) and the data is handled externally
	return nil
}

// WriteValue writes a polymorphic value with reference tracking and type info.
// This is used when the concrete type is not known at compile time.
func (c *WriteContext) WriteValue(value reflect.Value) error {
	return c.writeReferencable(value)
}

// writeReferencable writes a value with reference tracking
func (c *WriteContext) writeReferencable(value reflect.Value) error {
	return c.writeReferencableBySerializer(value, nil)
}

// writeReferencableBySerializer writes a value with reference tracking using a specific serializer
func (c *WriteContext) writeReferencableBySerializer(value reflect.Value, serializer Serializer) error {
	if refWritten, err := c.refResolver.WriteRefOrNull(c.buffer, value); err == nil && !refWritten {
		// check ptr
		if value.Kind() == reflect.Ptr {
			switch value.Elem().Kind() {
			case reflect.Ptr, reflect.Map, reflect.Slice, reflect.Interface:
				return fmt.Errorf("pointer to reference type %s is not supported", value.Type())
			}
		}
		return c.writeValue(value, serializer)
	} else {
		return err
	}
}

// writeValue writes a value using the type resolver
func (c *WriteContext) writeValue(value reflect.Value, serializer Serializer) error {
	// Handle interface values by getting their concrete element
	if value.Kind() == reflect.Interface {
		value = value.Elem()
	}

	// For array types, pre-convert the value
	if value.Kind() == reflect.Array {
		length := value.Len()
		sliceType := reflect.SliceOf(value.Type().Elem())
		slice := reflect.MakeSlice(sliceType, length, length)
		reflect.Copy(slice, value)
		value = slice
	}

	if serializer != nil {
		return serializer.WriteReflect(c, value)
	}

	// Get type information for the value
	typeInfo, err := c.typeResolver.getTypeInfo(value, true)
	if err != nil {
		return fmt.Errorf("cannot get typeinfo for value %v: %v", value, err)
	}
	err = c.typeResolver.writeTypeInfo(c.buffer, typeInfo)
	if err != nil {
		return fmt.Errorf("cannot write typeinfo for value %v: %v", value, err)
	}
	serializer = typeInfo.Serializer
	return serializer.WriteReflect(c, value)
}

// ============================================================================
// ReadContext - Holds all state needed during deserialization
// ============================================================================

// ReadContext holds all state needed during deserialization.
type ReadContext struct {
	buffer           *ByteBuffer
	refReader        *RefReader
	trackRef         bool          // Cached flag to avoid indirection
	compatible       bool          // Schema evolution compatibility mode
	typeResolver     *typeResolver // For complex type deserialization
	refResolver      *RefResolver  // For reference tracking (legacy)
	outOfBandBuffers []*ByteBuffer // Out-of-band buffers for deserialization
	outOfBandIndex   int           // Current index into out-of-band buffers
}

// NewReadContext creates a new read context
func NewReadContext(trackRef bool) *ReadContext {
	return &ReadContext{
		buffer:    NewByteBuffer(nil),
		refReader: NewRefReader(trackRef),
		trackRef:  trackRef,
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
func (c *ReadContext) TypeResolver() *typeResolver {
	return c.typeResolver
}

// RefResolver returns the reference resolver (legacy)
func (c *ReadContext) RefResolver() *RefResolver {
	return c.refResolver
}

// Inline primitive reads
func (c *ReadContext) ReadBool() bool        { return c.buffer.ReadBool() }
func (c *ReadContext) ReadInt8() int8        { return int8(c.buffer.ReadByte_()) }
func (c *ReadContext) ReadInt16() int16      { return c.buffer.ReadInt16() }
func (c *ReadContext) ReadInt32() int32      { return c.buffer.ReadInt32() }
func (c *ReadContext) ReadInt64() int64      { return c.buffer.ReadInt64() }
func (c *ReadContext) ReadFloat32() float32  { return c.buffer.ReadFloat32() }
func (c *ReadContext) ReadFloat64() float64  { return c.buffer.ReadFloat64() }
func (c *ReadContext) ReadVarInt32() int32   { return c.buffer.ReadVarint32() }
func (c *ReadContext) ReadVarInt64() int64   { return c.buffer.ReadVarint64() }
func (c *ReadContext) ReadVarUint32() uint32 { return c.buffer.ReadVarUint32() }
func (c *ReadContext) ReadByte() byte        { return c.buffer.ReadByte_() }

func (c *ReadContext) ReadString() string {
	length := c.buffer.ReadVarUint32()
	if length == 0 {
		return ""
	}
	data := c.buffer.ReadBinary(int(length))
	return string(data)
}

func (c *ReadContext) ReadBinary() []byte {
	length := c.buffer.ReadVarUint32()
	return c.buffer.ReadBinary(int(length))
}

func (c *ReadContext) ReadTypeId() TypeId {
	return c.buffer.ReadInt16()
}

// ReadAndValidateTypeId reads type ID and validates it matches expected
func (c *ReadContext) ReadAndValidateTypeId(expected TypeId) error {
	actual := c.ReadTypeId()
	if actual != expected {
		return ErrTypeMismatch
	}
	return nil
}

// ReadLength reads a length value as varint
func (c *ReadContext) ReadLength() int {
	return int(c.buffer.ReadVarInt32())
}

// ============================================================================
// Typed Read Methods - Read primitives with optional ref/type info
// ============================================================================

// ReadBoolValue reads a bool with optional ref/type info
func (c *ReadContext) ReadBoolValue(readRefInfo, readTypeInfo bool) (bool, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadBool(), nil
}

// ReadIntoBoolValue reads a bool into target with optional ref/type info
func (c *ReadContext) ReadIntoBoolValue(target *bool, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadBool()
	return nil
}

// ReadInt8Value reads an int8 with optional ref/type info
func (c *ReadContext) ReadInt8Value(readRefInfo, readTypeInfo bool) (int8, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadInt8(), nil
}

// ReadIntoInt8Value reads an int8 into target with optional ref/type info
func (c *ReadContext) ReadIntoInt8Value(target *int8, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadInt8()
	return nil
}

// ReadInt16Value reads an int16 with optional ref/type info
func (c *ReadContext) ReadInt16Value(readRefInfo, readTypeInfo bool) (int16, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadInt16(), nil
}

// ReadIntoInt16Value reads an int16 into target with optional ref/type info
func (c *ReadContext) ReadIntoInt16Value(target *int16, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadInt16()
	return nil
}

// ReadInt32Value reads an int32 with optional ref/type info
func (c *ReadContext) ReadInt32Value(readRefInfo, readTypeInfo bool) (int32, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadVarint32(), nil
}

// ReadIntoInt32Value reads an int32 into target with optional ref/type info
func (c *ReadContext) ReadIntoInt32Value(target *int32, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadVarint32()
	return nil
}

// ReadInt64Value reads an int64 with optional ref/type info
func (c *ReadContext) ReadInt64Value(readRefInfo, readTypeInfo bool) (int64, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadVarint64(), nil
}

// ReadIntoInt64Value reads an int64 into target with optional ref/type info
func (c *ReadContext) ReadIntoInt64Value(target *int64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadVarint64()
	return nil
}

// ReadFloat32Value reads a float32 with optional ref/type info
func (c *ReadContext) ReadFloat32Value(readRefInfo, readTypeInfo bool) (float32, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadFloat32(), nil
}

// ReadIntoFloat32Value reads a float32 into target with optional ref/type info
func (c *ReadContext) ReadIntoFloat32Value(target *float32, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadFloat32()
	return nil
}

// ReadFloat64Value reads a float64 with optional ref/type info
func (c *ReadContext) ReadFloat64Value(readRefInfo, readTypeInfo bool) (float64, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	return c.buffer.ReadFloat64(), nil
}

// ReadIntoFloat64Value reads a float64 into target with optional ref/type info
func (c *ReadContext) ReadIntoFloat64Value(target *float64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	*target = c.buffer.ReadFloat64()
	return nil
}

// ReadStringValue reads a string with optional ref/type info
func (c *ReadContext) ReadStringValue(readRefInfo, readTypeInfo bool) (string, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	length := c.buffer.ReadVarUint32()
	if length == 0 {
		return "", nil
	}
	data := c.buffer.ReadBinary(int(length))
	return string(data), nil
}

// ReadIntoStringValue reads a string into target with optional ref/type info
func (c *ReadContext) ReadIntoStringValue(target *string, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	length := c.buffer.ReadVarUint32()
	if length == 0 {
		*target = ""
		return nil
	}
	data := c.buffer.ReadBinary(int(length))
	*target = string(data)
	return nil
}

// ReadBoolSlice reads []bool with optional ref/type info
func (c *ReadContext) ReadBoolSlice(readRefInfo, readTypeInfo bool) ([]bool, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	length := c.buffer.ReadLength()
	result := make([]bool, length)
	for i := 0; i < length; i++ {
		result[i] = c.buffer.ReadBool()
	}
	return result, nil
}

// ReadIntoBoolSlice reads []bool into target, reusing capacity when possible
func (c *ReadContext) ReadIntoBoolSlice(target *[]bool, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	length := c.buffer.ReadLength()
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]bool, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = c.buffer.ReadBool()
	}
	return nil
}

// ReadInt32Slice reads []int32 with optional ref/type info
func (c *ReadContext) ReadInt32Slice(readRefInfo, readTypeInfo bool) ([]int32, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 4
	result := make([]int32, length)
	for i := 0; i < length; i++ {
		result[i] = c.buffer.ReadInt32()
	}
	return result, nil
}

// ReadIntoInt32Slice reads []int32 into target, reusing capacity when possible
func (c *ReadContext) ReadIntoInt32Slice(target *[]int32, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 4
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int32, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = c.buffer.ReadInt32()
	}
	return nil
}

// ReadInt64Slice reads []int64 with optional ref/type info
func (c *ReadContext) ReadInt64Slice(readRefInfo, readTypeInfo bool) ([]int64, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 8
	result := make([]int64, length)
	for i := 0; i < length; i++ {
		result[i] = c.buffer.ReadInt64()
	}
	return result, nil
}

// ReadIntoInt64Slice reads []int64 into target, reusing capacity when possible
func (c *ReadContext) ReadIntoInt64Slice(target *[]int64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 8
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int64, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = c.buffer.ReadInt64()
	}
	return nil
}

// ReadFloat64Slice reads []float64 with optional ref/type info
func (c *ReadContext) ReadFloat64Slice(readRefInfo, readTypeInfo bool) ([]float64, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 8
	result := make([]float64, length)
	for i := 0; i < length; i++ {
		result[i] = c.buffer.ReadFloat64()
	}
	return result, nil
}

// ReadIntoFloat64Slice reads []float64 into target, reusing capacity when possible
func (c *ReadContext) ReadIntoFloat64Slice(target *[]float64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	size := c.buffer.ReadLength()
	length := size / 8
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]float64, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = c.buffer.ReadFloat64()
	}
	return nil
}

// ReadByteSlice reads []byte with optional ref/type info
func (c *ReadContext) ReadByteSlice(readRefInfo, readTypeInfo bool) ([]byte, error) {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	isInBand := c.buffer.ReadBool()
	if !isInBand {
		return nil, fmt.Errorf("out-of-band byte slice not supported in fast path")
	}
	size := c.buffer.ReadLength()
	return c.buffer.ReadBinary(size), nil
}

// ReadIntoByteSlice reads []byte into target, reusing capacity when possible
func (c *ReadContext) ReadIntoByteSlice(target *[]byte, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = c.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = c.buffer.ReadInt16()
	}
	isInBand := c.buffer.ReadBool()
	if !isInBand {
		return fmt.Errorf("out-of-band byte slice not supported in fast path")
	}
	size := c.buffer.ReadLength()
	data := c.buffer.ReadBinary(size)
	if cap(*target) >= size {
		*target = (*target)[:size]
		copy(*target, data)
	} else {
		*target = data
	}
	return nil
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

// ReadValue reads a polymorphic value with reference tracking
func (c *ReadContext) ReadValue(value reflect.Value) error {
	return c.readReferencable(value)
}

// readReferencable reads a value with reference tracking
func (c *ReadContext) readReferencable(value reflect.Value) error {
	return c.readReferencableBySerializer(value, nil)
}

// readReferencableBySerializer reads a value with reference tracking using a specific serializer
func (c *ReadContext) readReferencableBySerializer(value reflect.Value, serializer Serializer) error {
	// dynamic-with-refroute or unknown serializer
	if serializer == nil || serializer.NeedToWriteRef() {
		refId, err := c.refResolver.TryPreserveRefId(c.buffer)
		if err != nil {
			return fmt.Errorf("failed to preserve refID: %w", err)
		}
		// first read
		if refId >= int32(NotNullValueFlag) {
			// deserialize non-ref (may read typeinfo or use provided serializer)
			if err := c.readData(value, serializer); err != nil {
				return fmt.Errorf("failed to read data: %w", err)
			}
			// record in resolver
			c.refResolver.SetReadObject(refId, value)
			return nil
		}
		// back-reference or null
		if refId == int32(NullFlag) {
			value.Set(reflect.Zero(value.Type()))
			return nil
		}
		prev := c.refResolver.GetReadObject(refId)
		value.Set(prev)
		return nil
	}

	// static path: no references
	headFlag := c.buffer.ReadInt8()
	if headFlag == NullFlag {
		value.Set(reflect.Zero(value.Type()))
		return nil
	}
	// directly read without altering serializer
	return serializer.ReadReflect(c, value.Type(), value)
}

// readData reads value data using the type resolver
func (c *ReadContext) readData(value reflect.Value, serializer Serializer) error {
	if serializer == nil {
		typeInfo, err := c.typeResolver.readTypeInfo(c.buffer, value)
		if err != nil {
			return fmt.Errorf("read typeinfo failed: %w", err)
		}
		serializer = typeInfo.Serializer

		var concrete reflect.Value
		var type_ reflect.Type
		switch {
		case value.Kind() == reflect.Interface,
			!value.CanSet():
			concrete = reflect.New(typeInfo.Type).Elem()
			type_ = typeInfo.Type
		default:
			concrete = value
			type_ = concrete.Type()
			// For slice types with concrete element types, prefer type-specific serializer
			if type_.Kind() == reflect.Slice && !isDynamicType(type_.Elem()) {
				if typeSpecific, err := c.typeResolver.getSerializerByType(type_, false); err == nil && typeSpecific != nil {
					serializer = typeSpecific
				}
			}
		}
		if err := serializer.ReadReflect(c, type_, concrete); err != nil {
			return err
		}
		value.Set(concrete)
		return nil
	}
	return serializer.ReadReflect(c, value.Type(), value)
}

// ============================================================================
// RefWriter - Handles reference tracking during serialization
// ============================================================================

// RefWriter handles reference tracking during serialization
type RefWriter struct {
	enabled bool
	refs    map[uintptr]int32
	nextId  int32
}

// NewRefWriter creates a new reference writer
func NewRefWriter(enabled bool) *RefWriter {
	return &RefWriter{
		enabled: enabled,
		refs:    make(map[uintptr]int32),
		nextId:  0,
	}
}

// Reset clears state for reuse
func (w *RefWriter) Reset() {
	clear(w.refs)
	w.nextId = 0
}

// TryWriteRef attempts to write a reference. Returns true if the value was already seen.
func (w *RefWriter) TryWriteRef(ctx *WriteContext, ptr uintptr) bool {
	if !w.enabled {
		return false
	}
	if refId, exists := w.refs[ptr]; exists {
		ctx.buffer.WriteInt8(RefFlag)
		ctx.buffer.WriteVarint32(refId)
		return true
	}
	// First time seeing this reference
	w.refs[ptr] = w.nextId
	w.nextId++
	ctx.buffer.WriteInt8(RefValueFlag)
	return false
}

// WriteRefValue writes ref flag for a new value and registers it
func (w *RefWriter) WriteRefValue(ctx *WriteContext, ptr uintptr) {
	if w.enabled {
		w.refs[ptr] = w.nextId
		w.nextId++
		ctx.buffer.WriteInt8(RefValueFlag)
	} else {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
}

// ============================================================================
// RefReader - Handles reference tracking during deserialization
// ============================================================================

// RefReader handles reference tracking during deserialization
type RefReader struct {
	enabled bool
	refs    []any
}

// NewRefReader creates a new reference reader
func NewRefReader(enabled bool) *RefReader {
	return &RefReader{
		enabled: enabled,
		refs:    make([]any, 0, 16),
	}
}

// Reset clears state for reuse
func (r *RefReader) Reset() {
	r.refs = r.refs[:0]
}

// ReadRefFlag reads the reference flag and returns:
// - flag: the flag value
// - refId: the reference ID if flag is RefFlag
// - needRead: true if we need to read the actual data
func (r *RefReader) ReadRefFlag(ctx *ReadContext) (flag int8, refId int32, needRead bool) {
	flag = ctx.ReadInt8()
	switch flag {
	case NullFlag:
		return flag, 0, false
	case RefFlag:
		refId = ctx.ReadVarInt32()
		return flag, refId, false
	default: // RefValueFlag or NotNullValueFlag
		return flag, 0, true
	}
}

// Reference stores a reference for later retrieval
func (r *RefReader) Reference(value any) {
	if r.enabled {
		r.refs = append(r.refs, value)
	}
}

// GetRef retrieves a reference by ID
func (r *RefReader) GetRef(refId int32) any {
	if int(refId) < len(r.refs) {
		return r.refs[refId]
	}
	return nil
}
