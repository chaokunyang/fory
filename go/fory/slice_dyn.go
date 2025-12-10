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

func (s sliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	// Get slice length and handle empty slice case
	length := value.Len()
	if length == 0 {
		buf.WriteVarUint32(0) // WriteData 0 for empty slice
		return nil
	}

	// WriteData collection header and get type information
	collectFlag, elemTypeInfo, err := s.writeHeader(ctx, buf, value)
	if err != nil {
		return err
	}

	// Choose serialization path based on type consistency
	if (collectFlag & CollectionIsSameType) != 0 {
		return s.writeSameType(ctx, buf, value, elemTypeInfo, collectFlag) // Optimized path for same-type elements
	}
	return s.writeDifferentTypes(ctx, buf, value) // Fallback path for mixed-type elements
}

func (s sliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
			ctx.buffer.WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.buffer, value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		// For polymorphic slice elements, need to write full type info
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.buffer, typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

// writeHeader prepares and writes collection metadata including:
// - Collection size
// - Type consistency flags
// - Element type information (if homogeneous)
func (s sliceSerializer) writeHeader(ctx *WriteContext, buf *ByteBuffer, value reflect.Value) (byte, TypeInfo, error) {
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
	if ctx.TrackRef() && (elemTypeInfo.Serializer == nil || elemTypeInfo.Serializer.NeedToWriteRef()) {
		collectFlag |= CollectionTrackingRef
	}

	// WriteData metadata to buffer
	buf.WriteVarUint32(uint32(value.Len())) // Collection size
	buf.WriteInt8(int8(collectFlag))        // Collection flags

	// WriteData element type info if all elements have same type and not using declared type
	if hasSameType && (collectFlag&CollectionIsDeclElementType == 0) {
		// For namespaced types, write full type info
		internalTypeID := elemTypeInfo.TypeID
		if IsNamespacedType(TypeId(internalTypeID)) {
			if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
				return 0, TypeInfo{}, err
			}
		} else {
			buf.WriteVarUint32Small7(uint32(elemTypeInfo.TypeID))
		}
	}

	return byte(collectFlag), elemTypeInfo, nil
}

// writeSameType efficiently serializes a slice where all elements share the same type
func (s sliceSerializer) writeSameType(
	ctx *WriteContext, buf *ByteBuffer, value reflect.Value, typeInfo TypeInfo, flag byte) error {
	serializer := typeInfo.Serializer
	trackRefs := (flag & CollectionTrackingRef) != 0 // Check if reference tracking is enabled

	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i)
		if elem.Kind() == reflect.Interface || elem.Kind() == reflect.Ptr {
			elem = elem.Elem()
		}
		if isNull(elem) {
			buf.WriteInt8(NullFlag) // WriteData null marker
			continue
		}

		if trackRefs {
			// Use Write with ref tracking enabled
			// serializer.Write will handle writing ref flags
			if err := serializer.Write(ctx, true, false, elem); err != nil {
				return err
			}
		} else {
			// Directly write value without reference tracking
			if err := serializer.WriteData(ctx, elem); err != nil {
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
			buf.WriteInt8(NullFlag) // WriteData null marker
			continue
		}
		// When writing the actual value, detect if elem is an array and convert it
		// to the corresponding slice type so the existing slice serializer can be reused
		if elem.Kind() == reflect.Array {
			sliceType := reflect.SliceOf(elem.Type().Elem())
			slice := reflect.MakeSlice(sliceType, elem.Len(), elem.Len())
			reflect.Copy(slice, elem)
			elem = slice
		}
		// Use Write with ref and type tracking enabled
		if err := ctx.WriteValue(elem); err != nil {
			return err
		}
	}
	return nil
}

func (s sliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	// ReadData slice length from buffer
	length := int(buf.ReadVarUint32())
	if length == 0 {
		// Initialize empty slice if length is 0
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return nil
	}

	// ReadData collection flags that indicate special characteristics
	collectFlag := buf.ReadInt8()
	var elemTypeInfo TypeInfo
	var elemSerializer Serializer
	var elemType reflect.Type

	// ReadData element type information from buffer if present
	// We must consume these bytes even if we use declared type serializer
	if (collectFlag & CollectionIsSameType) != 0 {
		if (collectFlag & CollectionIsDeclElementType) == 0 {
			// ReadData type ID from buffer
			typeID := int32(buf.ReadVarUint32Small7())

			// ReadData additional metadata for namespaced types
			if IsNamespacedType(TypeId(typeID)) {
				elemTypeInfo, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			} else {
				elemTypeInfo, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			}
		}
	}

	// Priority 1: Use declared type and its serializer if available
	if s.declaredType != nil {
		elemType = s.declaredType
		elemSerializer = s.elemInfo.Serializer
	} else if type_.Elem().Kind() != reflect.Interface {
		// Priority 2: Use the slice's element type to get serializer
		elemType = type_.Elem()
		var err error
		elemTypeInfo, err = ctx.TypeResolver().getTypeInfo(reflect.New(elemType).Elem(), true)
		if err == nil {
			elemSerializer = elemTypeInfo.Serializer
		}
	} else if elemTypeInfo.Serializer != nil {
		// Priority 3: Use type info read from buffer (for interface{} slices)
		elemType = elemTypeInfo.Type
		elemSerializer = elemTypeInfo.Serializer
	}
	// Initialize slice with proper capacity
	isArrayType := type_.Kind() == reflect.Array
	var arrayValue reflect.Value
	if isArrayType {
		arrayValue = value // Save the original array value
		// For arrays, we'll work with a slice and copy back later
		type_ = reflect.SliceOf(type_.Elem())
	}

	var readValue reflect.Value
	if value.IsZero() || value.Cap() < length {
		if type_.Kind() != reflect.Slice {
			if type_.Kind() == reflect.Interface {
				type_ = reflect.TypeOf([]interface{}{})
			} else {
				panic(fmt.Sprintf("sliceSerializer.ReadValue: unexpected type %v (kind=%v)", type_, type_.Kind()))
			}
		}
		// For arrays, create a temp slice to read into
		if isArrayType {
			readValue = reflect.MakeSlice(type_, length, length)
		} else {
			value.Set(reflect.MakeSlice(type_, length, length))
			readValue = value
			if readValue.Kind() == reflect.Interface || readValue.Kind() == reflect.Ptr {
				readValue = readValue.Elem()
			}
		}
	} else {
		if !isArrayType {
			value.Set(value.Slice(0, length))
		}
		readValue = value
	}
	// Register reference for tracking (handles circular references)
	ctx.RefResolver().Reference(readValue)

	// Choose appropriate deserialization path based on type consistency
	var err error
	if (collectFlag & CollectionIsSameType) != 0 {
		err = s.readSameType(ctx, buf, readValue, elemType, elemSerializer, collectFlag)
	} else {
		err = s.readDifferentTypes(ctx, buf, readValue)
	}
	if err != nil {
		return err
	}

	// For arrays, copy from the temp slice back to the array
	if isArrayType && arrayValue.IsValid() {
		arrayLen := arrayValue.Len()
		copyLen := length
		if copyLen > arrayLen {
			copyLen = arrayLen
		}
		for i := 0; i < copyLen; i++ {
			arrayValue.Index(i).Set(readValue.Index(i))
		}
	}

	return nil
}

func (s sliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	}
	if readType {
		// ReadData and discard type info for slices (we already know it's a list)
		typeID := int32(buf.ReadVarUint32Small7())
		if IsNamespacedType(TypeId(typeID)) {
			// For namespaced types, need to read additional metadata
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s sliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

// readSameType handles deserialization of slices where all elements share the same type
func (s sliceSerializer) readSameType(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, elemType reflect.Type, serializer Serializer, flag int8) error {
	// Determine if reference tracking is enabled
	trackRefs := (flag & CollectionTrackingRef) != 0
	if serializer == nil {
		return fmt.Errorf("no serializer available for element type %v", elemType)
	}

	// Check if the slice element type is a pointer type
	// This affects how we handle reference tracking
	sliceElemType := value.Type().Elem()
	isConcretePointerElem := sliceElemType.Kind() == reflect.Ptr

	for i := 0; i < value.Len(); i++ {
		// For concrete pointer elements ([]*T), we always need to create a pointer.
		if isConcretePointerElem {
			// Target is []*T, create pointer and read
			ptr := reflect.New(elemType)
			if err := serializer.Read(ctx, trackRefs, false, ptr.Elem()); err != nil {
				return err
			}
			value.Index(i).Set(ptr)
		} else {
			// Non-pointer elements: read directly into a value
			elem := reflect.New(elemType).Elem()
			if err := serializer.Read(ctx, trackRefs, false, elem); err != nil {
				return err
			}
			value.Index(i).Set(elem)
		}
	}
	return nil
}

// readDifferentTypes handles deserialization of slices with mixed element types
func (s sliceSerializer) readDifferentTypes(
	ctx *ReadContext, buf *ByteBuffer, value reflect.Value) error {
	for i := 0; i < value.Len(); i++ {
		// Create new element and deserialize from buffer
		elem := reflect.New(value.Type().Elem()).Elem()
		if err := ctx.ReadValue(elem); err != nil {
			return err
		}
		value.Index(i).Set(elem)
	}
	return nil
}
