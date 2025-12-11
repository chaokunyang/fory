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

// Array serializers

type arraySerializer struct{}

func (s arraySerializer) TypeId() TypeId       { return -LIST }
func (s arraySerializer) NeedToWriteRef() bool { return true }

func (s arraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteVarUint32(uint32(length))
	for i := 0; i < length; i++ {
		elem := value.Index(i)
		buf.WriteInt8(NotNullValueFlag)
		_ = elem
	}
	return nil
}

func (s arraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// For polymorphic array elements, need to write full type info
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

func (s arraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVarUint32())
	for i := 0; i < length; i++ {
		_ = buf.ReadInt8()
	}
	return nil
}

func (s arraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	}
	if readType {
		typeID := buf.ReadVaruint32()
		if IsNamespacedType(TypeId(typeID)) {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s arraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// arrayConcreteValueSerializer serialize an array/*array
type arrayConcreteValueSerializer struct {
	type_          reflect.Type
	elemSerializer Serializer
	referencable   bool
}

func (s *arrayConcreteValueSerializer) TypeId() TypeId      { return -LIST }
func (s arrayConcreteValueSerializer) NeedToWriteRef() bool { return true }

func (s *arrayConcreteValueSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	buf := ctx.Buffer()

	// Write length
	buf.WriteVarUint32(uint32(length))
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
	var elemTypeInfo TypeInfo
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
	internalTypeID := elemTypeInfo.TypeID
	if IsNamespacedType(TypeId(internalTypeID)) {
		if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
			return err
		}
	} else {
		buf.WriteVarUint32Small7(uint32(elemTypeInfo.TypeID))
	}

	// Write elements
	trackRefs := (collectFlag & CollectionTrackingRef) != 0

	for i := 0; i < length; i++ {
		elem := value.Index(i)

		// Handle null values (only for pointer element types)
		if hasNull && elem.IsNil() {
			if trackRefs {
				if err := s.elemSerializer.Write(ctx, true, false, elem); err != nil {
					return err
				}
			} else {
				buf.WriteInt8(NullFlag)
			}
			continue
		}

		// Write element
		if trackRefs {
			if err := s.elemSerializer.Write(ctx, true, false, elem); err != nil {
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

func (s *arrayConcreteValueSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// Generic array type, need to write full type info
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.Buffer(), typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

func (s *arrayConcreteValueSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVarUint32())

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
			if err := s.elemSerializer.Read(ctx, true, false, elem); err != nil {
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

func (s *arrayConcreteValueSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	}
	if readType {
		typeID := buf.ReadVaruint32()
		if IsNamespacedType(TypeId(typeID)) {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	if err := s.ReadData(ctx, value.Type(), value); err != nil {
		return err
	}
	if readRef {
		ctx.RefResolver().Reference(value)
	}
	return nil
}

func (s *arrayConcreteValueSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type byteArraySerializer struct{}

func (s byteArraySerializer) TypeId() TypeId       { return -BINARY }
func (s byteArraySerializer) NeedToWriteRef() bool { return false }

func (s byteArraySerializer) Write(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	ctx.buffer.WriteVarUint32(uint32(length))
	if value.CanAddr() {
		ctx.buffer.WriteBinary(value.Slice(0, length).Bytes())
	} else {
		data := make([]byte, length)
		for i := 0; i < length; i++ {
			data[i] = byte(value.Index(i).Uint())
		}
		ctx.buffer.WriteBinary(data)
	}
	return nil
}

func (s byteArraySerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	length := int(ctx.buffer.ReadVarUint32())
	data := make([]byte, length)
	ctx.buffer.Read(data)
	if value.CanSet() {
		for i := 0; i < length && i < value.Len(); i++ {
			value.Index(i).SetUint(uint64(data[i]))
		}
	}
	return nil
}

func (s byteArraySerializer) ReadFull(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	if readRef {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
	}
	if readType {
		_ = ctx.buffer.ReadVaruint32()
	}
	return s.Read(ctx, value.Type(), value)
}

func (s byteArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.ReadFull(ctx, readRef, false, value)
}
