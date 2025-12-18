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
	"reflect"
)

// writeArrayRefAndType handles reference and type writing for array serializers.
// Arrays are value types, so ref handling is simpler than slices.
// Returns true if the value was already written, false if data should be written.
func writeArrayRefAndType(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value, typeId TypeId) (bool, error) {
	if refMode != RefModeNone {
		// Arrays are value types, just write NotNullValueFlag
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(typeId))
	}
	return false, nil
}

// readArrayRefAndType handles reference and type reading for array serializers.
// Returns true if a reference was resolved (value already set), false if data should be read.
func readArrayRefAndType(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) (bool, error) {
	buf := ctx.Buffer()
	if refMode != RefModeNone {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return false, err
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return true, nil
		}
	}
	if readType {
		typeID := buf.ReadVaruint32Small7()
		if IsNamespacedType(TypeId(typeID)) {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return false, nil
}

// Array serializers

type arraySerializer struct{}

func (s arraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteVaruint32(uint32(length))
	for i := 0; i < length; i++ {
		elem := value.Index(i)
		buf.WriteInt8(NotNullValueFlag)
		_ = elem
	}
	return nil
}

func (s arraySerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, refMode, writeType, value, -LIST)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s arraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVaruint32())
	for i := 0; i < length; i++ {
		_ = buf.ReadInt8()
	}
	return nil
}

func (s arraySerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s arraySerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

// arrayConcreteValueSerializer serialize an array/*array
type arrayConcreteValueSerializer struct {
	type_          reflect.Type
	elemSerializer Serializer
	referencable   bool
}

func (s *arrayConcreteValueSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	buf := ctx.Buffer()

	// Write length
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return nil
	}

	// Determine collection flags - same logic as slices
	collectFlag := CollectionIsSameType
	hasNull := false
	elemType := s.type_.Elem()
	isPointerElem := elemType.Kind() == reflect.Ptr

	// Check for null values (only for pointer element types)
	if isPointerElem {
		for i := 0; i < length; i++ {
			elem := value.Index(i)
			if elem.IsNil() {
				hasNull = true
				break
			}
		}
	}

	if hasNull {
		collectFlag |= CollectionHasNull
	}
	if ctx.TrackRef() && s.referencable {
		collectFlag |= CollectionTrackingRef
	}
	buf.WriteInt8(int8(collectFlag))

	// Write element type info
	var elemTypeInfo *TypeInfo
	if length > 0 {
		// Get type info for the first non-nil element
		for i := 0; i < length; i++ {
			elem := value.Index(i)
			if isPointerElem {
				if !elem.IsNil() {
					elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(elem.Elem(), true)
					break
				}
			} else {
				elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(elem, true)
				break
			}
		}
	}

	// Write element type info (handles namespaced types)
	var internalTypeID uint32
	if elemTypeInfo != nil {
		internalTypeID = elemTypeInfo.TypeID
	}
	if IsNamespacedType(TypeId(internalTypeID)) {
		if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
			return err
		}
	} else {
		buf.WriteVaruint32Small7(uint32(internalTypeID))
	}

	// Write elements
	trackRefs := (collectFlag & CollectionTrackingRef) != 0

	for i := 0; i < length; i++ {
		elem := value.Index(i)

		// Handle null values (only for pointer element types)
		if hasNull && elem.IsNil() {
			if trackRefs {
				if err := s.elemSerializer.Write(ctx, RefModeTracking, false, elem); err != nil {
					return err
				}
			} else {
				buf.WriteInt8(NullFlag)
			}
			continue
		}

		// Write element
		if trackRefs {
			if err := s.elemSerializer.Write(ctx, RefModeTracking, false, elem); err != nil {
				return err
			}
		} else {
			if err := s.elemSerializer.WriteData(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *arrayConcreteValueSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, refMode, writeType, value, -LIST)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s *arrayConcreteValueSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVaruint32())

	var trackRefs bool
	if length > 0 {
		// Read collection flags (same format as slices)
		collectFlag := buf.ReadInt8()

		// Read element type info if present
		if (collectFlag & CollectionIsSameType) != 0 {
			if (collectFlag & CollectionIsDeclElementType) == 0 {
				typeID := buf.ReadVaruint32()
				// Read additional metadata for namespaced types
				_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			}
		}

		trackRefs = (collectFlag & CollectionTrackingRef) != 0
	}

	for i := 0; i < length && i < value.Len(); i++ {
		elem := value.Index(i)

		// When tracking refs, the element serializer handles ref flags
		if trackRefs {
			if err := s.elemSerializer.Read(ctx, RefModeTracking, false, elem); err != nil {
				return err
			}
		} else {
			if err := s.elemSerializer.ReadData(ctx, elem.Type(), elem); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *arrayConcreteValueSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	if err := s.ReadData(ctx, value.Type(), value); err != nil {
		return err
	}
	if refMode != RefModeNone {
		ctx.RefResolver().Reference(value)
	}
	return nil
}

func (s *arrayConcreteValueSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

// arrayDynSerializer wraps sliceDynSerializer for arrays with interface element types.
// It converts arrays to slices and delegates to sliceDynSerializer.
type arrayDynSerializer struct {
	sliceSerializer sliceDynSerializer
}

func newArrayDynSerializer(elemType reflect.Type) (arrayDynSerializer, error) {
	sliceSer, err := newSliceDynSerializer(elemType)
	if err != nil {
		return arrayDynSerializer{}, err
	}
	return arrayDynSerializer{sliceSerializer: sliceSer}, nil
}

func (s arrayDynSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Convert array to slice and forward to sliceDynSerializer
	slice := value.Slice(0, value.Len())
	return s.sliceSerializer.WriteData(ctx, slice)
}

func (s arrayDynSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, refMode, writeType, value, LIST)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s arrayDynSerializer) ReadData(ctx *ReadContext, _ reflect.Type, value reflect.Value) error {
	// Create a temp slice to read into, then copy back to array
	sliceType := reflect.SliceOf(value.Type().Elem())
	tempSlice := reflect.MakeSlice(sliceType, value.Len(), value.Len())
	if err := s.sliceSerializer.ReadData(ctx, sliceType, tempSlice); err != nil {
		return err
	}
	// Copy elements from temp slice to array
	copyLen := tempSlice.Len()
	if copyLen > value.Len() {
		copyLen = value.Len()
	}
	for i := 0; i < copyLen; i++ {
		value.Index(i).Set(tempSlice.Index(i))
	}
	return nil
}

func (s arrayDynSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s arrayDynSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type byteArraySerializer struct{}

func (s byteArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length)
	if value.CanAddr() {
		buf.WriteBinary(value.Slice(0, length).Bytes())
	} else {
		data := make([]byte, length)
		for i := 0; i < length; i++ {
			data[i] = byte(value.Index(i).Uint())
		}
		buf.WriteBinary(data)
	}
	return nil
}

func (s byteArraySerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, refMode, writeType, value, BINARY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s byteArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	data := make([]byte, length)
	buf.Read(data)
	if value.CanSet() {
		for i := 0; i < length && i < value.Len(); i++ {
			value.Index(i).SetUint(uint64(data[i]))
		}
	}
	return nil
}

func (s byteArraySerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s byteArraySerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
