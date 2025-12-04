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
)

const (
	CollectionDefaultFlag       = 0b0000
	CollectionTrackingRef       = 0b0001
	CollectionHasNull           = 0b0010
	CollectionIsDeclElementType = 0b0100
	CollectionIsSameType        = 0b1000
	CollectionDeclSameType      = CollectionIsSameType | CollectionIsDeclElementType
)

// sliceSerializer provides the dynamic slice implementation(e.g. []interface{}) that inspects
// element values at runtime
type sliceSerializer struct {
	elemInfo     TypeInfo
	declaredType reflect.Type
}

func (s sliceSerializer) TypeId() TypeId {
	return LIST
}

func (s sliceSerializer) NeedToWriteRef() bool {
	return true
}

// AnySerializer interface methods
func (s sliceSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	_ = reflect.ValueOf(value) // Used for validation
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(LIST)
	}
	return s.WriteDataAny(ctx, value)
}

func (s sliceSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	// For now, delegate to the reflection-based implementation
	// TODO: This will be replaced with direct generic implementation
	rv := reflect.ValueOf(value)
	length := rv.Len()
	if length == 0 {
		ctx.buffer.WriteVarUint32(0)
		return nil
	}
	// Use simplified path for now - write as polymorphic collection
	ctx.buffer.WriteVarUint32(uint32(length))
	ctx.buffer.WriteInt8(CollectionDefaultFlag) // No special flags for now
	for i := 0; i < length; i++ {
		elem := rv.Index(i).Interface()
		if elem == nil {
			ctx.buffer.WriteInt8(NullFlag)
		} else {
			ctx.buffer.WriteInt8(NotNullValueFlag)
			// TODO: Need proper element serialization
		}
	}
	return nil
}

func (s sliceSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataAny(ctx)
}

func (s sliceSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	length := int(ctx.buffer.ReadVarUint32())
	if length == 0 {
		return []any{}, nil
	}
	_ = ctx.buffer.ReadInt8() // Read collection flags
	result := make([]any, length)
	for i := 0; i < length; i++ {
		flag := ctx.buffer.ReadInt8()
		if flag == NullFlag {
			result[i] = nil
		}
		// TODO: Need proper element deserialization
	}
	return result, nil
}

// WriteReflect serializes a slice value into the buffer
func (s sliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	// Get slice length and handle empty slice case
	length := value.Len()
	if length == 0 {
		buf.WriteVarUint32(0) // Write 0 for empty slice
		return nil
	}

	// Write collection header and get type information
	collectFlag, elemTypeInfo := s.writeHeader(ctx, buf, value)

	// Choose serialization path based on type consistency
	if (collectFlag & CollectionIsSameType) != 0 {
		return s.writeSameType(ctx, buf, value, elemTypeInfo, collectFlag) // Optimized path for same-type elements
	}
	return s.writeDifferentTypes(ctx, buf, value) // Fallback path for mixed-type elements
}

// writeHeader prepares and writes collection metadata including:
// - Collection size
// - Type consistency flags
// - Element type information (if homogeneous)
func (s sliceSerializer) writeHeader(ctx *WriteContext, buf *ByteBuffer, value reflect.Value) (byte, TypeInfo) {
	collectFlag := CollectionDefaultFlag
	var elemTypeInfo TypeInfo
	hasNull := false
	hasSameType := true

	// Seed elemTypeInfo from the first element so writeSameType can reuse it.
	// Empty slices leave elemTypeInfo zero-value, which is also fine because
	// writeSameType won't do anything in that case.
	if value.Len() > 0 {
		elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(value.Index(0), true)
	}

	if s.declaredType != nil {
		collectFlag |= CollectionIsDeclElementType | CollectionIsSameType
	} else {
		// Iterate through elements to check for nulls and type consistency
		var firstType reflect.Type
		for i := 0; i < value.Len(); i++ {
			elem := value.Index(i)
			if elem.Kind() == reflect.Interface || elem.Kind() == reflect.Ptr {
				elem = elem.Elem()
			}
			if isNull(elem) {
				hasNull = true
				continue
			}

			// Compare each element's type with the first element's type
			if firstType == nil {
				firstType = elem.Type()
			} else {
				if firstType != elem.Type() {
					hasSameType = false
				}
			}
		}
	}

	// Set collection flags based on findings
	if hasNull {
		collectFlag |= CollectionHasNull // Mark if collection contains null values
	}
	if hasSameType {
		collectFlag |= CollectionIsSameType // Mark if elements have same types
	}

	// Enable reference tracking if configured and element type supports it
	if ctx.RefTracking() && (elemTypeInfo.Serializer == nil || elemTypeInfo.Serializer.NeedToWriteRef()) {
		collectFlag |= CollectionTrackingRef
	}

	// Write metadata to buffer
	buf.WriteVarUint32(uint32(value.Len())) // Collection size
	buf.WriteInt8(int8(collectFlag))        // Collection flags

	// Write element type ID if all elements have same type and not using declared type
	if hasSameType && (collectFlag&CollectionIsDeclElementType == 0) {
		buf.WriteVarInt32(elemTypeInfo.TypeID)
	}

	return byte(collectFlag), elemTypeInfo
}

// writeSameType efficiently serializes a slice where all elements share the same type
func (s sliceSerializer) writeSameType(ctx *WriteContext, buf *ByteBuffer, value reflect.Value, typeInfo TypeInfo, flag byte) error {
	serializer := typeInfo.Serializer
	trackRefs := (flag & CollectionTrackingRef) != 0 // Check if reference tracking is enabled

	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i)
		if elem.Kind() == reflect.Interface || elem.Kind() == reflect.Ptr {
			elem = elem.Elem()
		}
		if isNull(elem) {
			buf.WriteInt8(NullFlag) // Write null marker
			continue
		}

		if trackRefs {
			// Handle reference tracking if enabled
			refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, elem)
			if err != nil {
				return err
			}
			if !refWritten {
				// Write actual value if not a reference
				if err := serializer.WriteValue(ctx, elem); err != nil {
					return err
				}
			}
		} else {
			// Directly write value without reference tracking
			if err := serializer.WriteValue(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

// writeDifferentTypes handles serialization of slices with mixed element types
func (s sliceSerializer) writeDifferentTypes(ctx *WriteContext, buf *ByteBuffer, value reflect.Value) error {
	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i)
		if elem.Kind() == reflect.Interface || elem.Kind() == reflect.Ptr {
			elem = elem.Elem()
		}
		if isNull(elem) {
			buf.WriteInt8(NullFlag) // Write null marker
			continue
		}
		// The following write logic doesn't cover the fast path for strings, so add it here
		if elem.Kind() == reflect.String {
			buf.WriteInt8(NotNullValueFlag)
			buf.WriteVarInt32(STRING)
			err := writeString(buf, elem.Interface().(string))
			if err != nil {
				return err
			}
			continue
		}
		// Handle reference tracking
		refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, elem)
		if err != nil {
			return err
		}
		if refWritten {
			continue // Skip if element was written as reference
		}

		// Get and write type info for each element (since types vary)
		typeInfo, _ := ctx.TypeResolver().getTypeInfo(elem, true)
		ctx.TypeResolver().writeTypeInfo(buf, typeInfo)
		// When writing the actual value, detect if elem is an array and convert it
		// to the corresponding slice type so the existing slice serializer can be reused
		if elem.Kind() == reflect.Array {
			sliceType := reflect.SliceOf(elem.Type().Elem())
			slice := reflect.MakeSlice(sliceType, elem.Len(), elem.Len())
			reflect.Copy(slice, elem)
			elem = slice
		}
		if err := typeInfo.Serializer.WriteValue(ctx, elem); err != nil {
			return err
		}
	}
	return nil
}

// ReadReflect deserializes a slice from the buffer into the provided reflect.Value
func (s sliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	// Read slice length from buffer
	length := int(buf.ReadVarUint32())
	if length == 0 {
		// Initialize empty slice if length is 0
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return nil
	}

	// Read collection flags that indicate special characteristics
	collectFlag := buf.ReadInt8()
	var elemTypeInfo TypeInfo
	// Read element type information if all elements are same type
	if (collectFlag & CollectionIsSameType) != 0 {
		if (collectFlag & CollectionIsDeclElementType) == 0 {
			typeID := buf.ReadVarInt32()
			var err error
			elemTypeInfo, err = ctx.TypeResolver().getTypeInfoById(int16(typeID))
			if err != nil {
				elemTypeInfo = s.elemInfo
			}
		} else {
			elemTypeInfo = s.elemInfo
		}
	}
	// Initialize slice with proper capacity

	if value.IsZero() || value.Cap() < length {
		if type_.Kind() != reflect.Slice {
			if type_.Kind() == reflect.Interface {
				type_ = reflect.TypeOf([]interface{}{})
			}
		}
		value.Set(reflect.MakeSlice(type_, length, length))
		if value.Kind() == reflect.Interface || value.Kind() == reflect.Ptr {
			value = value.Elem()
		}
	} else {
		value.Set(value.Slice(0, length))
	}
	// Register reference for tracking
	ctx.RefResolver().Reference(value)

	// Choose appropriate deserialization path based on type consistency
	if (collectFlag & CollectionIsSameType) != 0 {
		return s.readSameType(ctx, buf, value, elemTypeInfo, collectFlag)
	}
	return s.readDifferentTypes(ctx, buf, value)
}

// readSameType handles deserialization of slices where all elements share the same type
func (s sliceSerializer) readSameType(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, typeInfo TypeInfo, flag int8) error {
	// Determine if reference tracking is enabled
	trackRefs := (flag & CollectionTrackingRef) != 0
	serializer := typeInfo.Serializer
	var refID int32

	for i := 0; i < value.Len(); i++ {
		if trackRefs {
			// Handle reference tracking if enabled
			refID, _ = ctx.RefResolver().TryPreserveRefId(buf)
			if int8(refID) < NotNullValueFlag {
				// Use existing reference if available
				value.Index(i).Set(ctx.RefResolver().GetCurrentReadObject())
				continue
			}
		}

		// Create new element of the correct type and deserialize from buffer
		elem := reflect.New(typeInfo.Type).Elem()
		if err := serializer.ReadValue(ctx, elem.Type(), elem); err != nil {
			return err
		}
		// Set element in slice and register reference
		value.Index(i).Set(elem)
		ctx.RefResolver().SetReadObject(refID, elem)
	}
	return nil
}

// readDifferentTypes handles deserialization of slices with mixed element types
func (s sliceSerializer) readDifferentTypes(ctx *ReadContext, buf *ByteBuffer, value reflect.Value) error {
	for i := 0; i < value.Len(); i++ {
		// Handle reference tracking for each element
		refID, _ := ctx.RefResolver().TryPreserveRefId(buf)
		if int8(refID) < NotNullValueFlag {
			// Use existing reference if available
			value.Index(i).Set(ctx.RefResolver().GetCurrentReadObject())
			continue
		}

		typeInfo, _ := ctx.TypeResolver().readTypeInfo(buf, value)

		// Create new element and deserialize from buffer
		elem := reflect.New(typeInfo.Type).Elem()
		if err := typeInfo.Serializer.ReadValue(ctx, typeInfo.Type, elem); err != nil {
			return err
		}
		// Set element in slice and register reference
		ctx.RefResolver().SetReadObject(refID, elem)
		value.Index(i).Set(elem)
	}
	return nil
}

// Helper function to check if a value is null/nil
func isNull(v reflect.Value) bool {
	switch v.Kind() {
	case reflect.Ptr, reflect.Interface, reflect.Slice, reflect.Map, reflect.Func:
		return v.IsNil() // Check if reference types are nil
	default:
		return false // Value types are never null
	}
}

// sliceConcreteValueSerializer serialize a slice whose elem is not an interface or pointer to interface
type sliceConcreteValueSerializer struct {
	type_          reflect.Type
	elemSerializer Serializer
	referencable   bool
}

func (s *sliceConcreteValueSerializer) TypeId() TypeId {
	return -LIST
}

func (s *sliceConcreteValueSerializer) NeedToWriteRef() bool {
	return true
}

func (s *sliceConcreteValueSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	if err := ctx.WriteLength(length); err != nil {
		return err
	}

	var prevType reflect.Type
	for i := 0; i < length; i++ {
		elem := value.Index(i)
		elemType := elem.Type()

		var elemSerializer Serializer
		if i == 0 || elemType != prevType {
			elemSerializer = nil
		} else {
			elemSerializer = s.elemSerializer
		}

		if err := writeBySerializer(ctx, elem, elemSerializer, s.referencable); err != nil {
			return err
		}

		prevType = elemType
	}
	return nil
}

func (s *sliceConcreteValueSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	length := ctx.ReadLength()
	if value.Cap() < length {
		value.Set(reflect.MakeSlice(value.Type(), length, length))
	} else if value.Len() < length {
		value.Set(value.Slice(0, length))
	}
	ctx.RefResolver().Reference(value)
	var prevType reflect.Type
	for i := 0; i < length; i++ {

		elem := value.Index(i)
		elemType := elem.Type()

		var elemSerializer Serializer
		if i == 0 || elemType != prevType {
			elemSerializer = nil
		} else {
			elemSerializer = s.elemSerializer
		}
		if err := readBySerializer(ctx, value.Index(i), elemSerializer, s.referencable); err != nil {
			return err
		}

		prevType = elemType
	}
	return nil
}

type byteSliceSerializer struct {
}

func (s byteSliceSerializer) TypeId() TypeId {
	return BINARY
}

func (s byteSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s byteSliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	if err := ctx.WriteBufferObject(&ByteSliceBufferObject{value.Interface().([]byte)}); err != nil {
		return err
	}
	return nil
}

func (s byteSliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	object, err := ctx.ReadBufferObject()
	if err != nil {
		return err
	}
	raw := object.GetData()
	switch type_.Kind() {
	case reflect.Slice:
		value.Set(reflect.ValueOf(raw))
		return nil
	case reflect.Array:
		if len(raw) != type_.Len() {
			return fmt.Errorf("byte array len %d ≠ %d", len(raw), type_.Len())
		}
		dst := reflect.New(type_).Elem()
		reflect.Copy(dst, reflect.ValueOf(raw))
		value.Set(dst)
		return nil

	default:
		return fmt.Errorf("unsupported kind %v; want slice or array", type_.Kind())
	}
}

type ByteSliceBufferObject struct {
	data []byte
}

func (o *ByteSliceBufferObject) TotalBytes() int {
	return len(o.data)
}

func (o *ByteSliceBufferObject) WriteTo(buf *ByteBuffer) {
	buf.WriteBinary(o.data)
}

func (o *ByteSliceBufferObject) ToBuffer() *ByteBuffer {
	return NewByteBuffer(o.data)
}

type boolArraySerializer struct {
}

func (s boolArraySerializer) TypeId() TypeId {
	return BOOL_ARRAY
}

func (s boolArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s boolArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]bool)
	size := len(v)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteBool(elem)
	}
	return nil
}

func (s boolArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool())
	}
	value.Set(r)
	return nil
}

type int8ArraySerializer struct {
}

func (s int8ArraySerializer) TypeId() TypeId {
	return INT8_ARRAY
}

func (s int8ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int8ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int8)
	size := len(v)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteByte_(byte(elem))
	}
	return nil
}

func (s int8ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(int8(buf.ReadByte_())))
	}
	value.Set(r)
	return nil
}

type int16ArraySerializer struct {
}

func (s int16ArraySerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int16ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	size := length * 2
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", length)
	}
	buf.WriteLength(size)
	for i := 0; i < length; i++ {
		buf.WriteInt16(int16(value.Index(i).Int()))
	}
	return nil
}

func (s int16ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	value.Set(r)
	return nil
}

type int32ArraySerializer struct {
}

func (s int32ArraySerializer) TypeId() TypeId {
	return INT32_ARRAY
}

func (s int32ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int32ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt32(elem)
	}
	return nil
}

func (s int32ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	value.Set(r)
	return nil
}

type int64ArraySerializer struct {
}

func (s int64ArraySerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int64ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt64(elem)
	}
	return nil
}

func (s int64ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64())
	}
	value.Set(r)
	return nil
}

type float32ArraySerializer struct {
}

func (s float32ArraySerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s float32ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat32(elem)
	}
	return nil
}

func (s float32ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	value.Set(r)
	return nil
}

type float64ArraySerializer struct {
}

func (s float64ArraySerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s float64ArraySerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat64(elem)
	}
	return nil
}

func (s float64ArraySerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64())
	}
	value.Set(r)
	return nil
}

// Legacy slice serializers - kept for backward compatibility but not used for xlang
type boolSliceSerializer struct {
}

func (s boolSliceSerializer) TypeId() TypeId {
	return BOOL_ARRAY // Use legacy type ID to avoid conflicts
}

func (s boolSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s boolSliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]bool)
	size := len(v)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteBool(elem)
	}
	return nil
}

func (s boolSliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool())
	}
	value.Set(r)
	return nil
}

type int16SliceSerializer struct {
}

func (s int16SliceSerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s int16SliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int16)
	size := len(v) * 2
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt16(elem)
	}
	return nil
}

func (s int16SliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	value.Set(r)
	return nil
}

type int32SliceSerializer struct {
}

func (s int32SliceSerializer) TypeId() TypeId {
	return INT32_ARRAY
}

func (s int32SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s int32SliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt32(elem)
	}
	return nil
}

func (s int32SliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	value.Set(r)
	return nil
}

type int64SliceSerializer struct {
}

func (s int64SliceSerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s int64SliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt64(elem)
	}
	return nil
}

func (s int64SliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64())
	}
	value.Set(r)
	return nil
}

type float32SliceSerializer struct {
}

func (s float32SliceSerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s float32SliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat32(elem)
	}
	return nil
}

func (s float32SliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	value.Set(r)
	return nil
}

type float64SliceSerializer struct {
}

func (s float64SliceSerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s float64SliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat64(elem)
	}
	return nil
}

func (s float64SliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64())
	}
	value.Set(r)
	return nil
}

type stringSliceSerializer struct {
	strSerializer stringSerializer
}

func (s stringSliceSerializer) TypeId() TypeId {
	return FORY_STRING_ARRAY
}

func (s stringSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s stringSliceSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]string)
	err := ctx.WriteLength(len(v))
	if err != nil {
		return err
	}
	for _, str := range v {
		if refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, reflect.ValueOf(str)); err == nil {
			if !refWritten {
				if err := writeString(buf, str); err != nil {
					return err
				}
			}
		} else {
			return err
		}
	}
	return nil
}

func (s stringSliceSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := ctx.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}

	elemTyp := type_.Elem()
	set := func(i int, s string) {
		if elemTyp.Kind() == reflect.String {
			r.Index(i).SetString(s)
		} else {
			r.Index(i).Set(reflect.ValueOf(s).Convert(elemTyp))
		}
	}

	for i := 0; i < length; i++ {
		refFlag := ctx.RefResolver().ReadRefOrNull(buf)
		if refFlag == RefValueFlag || refFlag == NotNullValueFlag {
			var nextReadRefId int32
			if refFlag == RefValueFlag {
				var err error
				nextReadRefId, err = ctx.RefResolver().PreserveRefId()
				if err != nil {
					return err
				}
			}
			elem := readString(buf)
			if ctx.RefTracking() && refFlag == RefValueFlag {
				// If value is not nil(reflect), then value is a pointer to some variable, we can update the `value`,
				// then record `value` in the reference resolver.
				ctx.RefResolver().SetReadObject(nextReadRefId, reflect.ValueOf(elem))
			}
			set(i, elem)
		} else if refFlag == NullFlag {
			set(i, "")
		} else { // RefNoneFlag
			set(i, ctx.RefResolver().GetCurrentReadObject().Interface().(string))
		}
	}
	value.Set(r)
	return nil
}

// those types will be serialized by `sliceConcreteValueSerializer`, which correspond to List types in java/python

type Int8Slice []int8
type Int16Slice []int16
type Int32Slice []int32
type Int64Slice []int64
type Float32Slice []float64
type Float64Slice []float64
