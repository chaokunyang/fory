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

// Helper function to check if a value is null/nil
func isNull(v reflect.Value) bool {
	switch v.Kind() {
	case reflect.Ptr, reflect.Interface, reflect.Slice, reflect.Map, reflect.Func:
		return v.IsNil() // Check if reference types are nil
	default:
		return false // Value types are never null
	}
}

// sliceConcreteValueSerializer serialize a slice whose elem is not an interface or pointer to interface.
// Use newSliceConcreteValueSerializer to create instances with proper type validation.
type sliceConcreteValueSerializer struct {
	type_          reflect.Type
	elemSerializer Serializer
	referencable   bool
}

// newSliceConcreteValueSerializer creates a sliceConcreteValueSerializer for slices with concrete element types.
// It returns an error if the element type is an interface or pointer to interface.
func newSliceConcreteValueSerializer(type_ reflect.Type, elemSerializer Serializer) (*sliceConcreteValueSerializer, error) {
	elem := type_.Elem()
	if elem.Kind() == reflect.Interface {
		return nil, fmt.Errorf("sliceConcreteValueSerializer does not support interface element type: %v", type_)
	}
	if elem.Kind() == reflect.Ptr && elem.Elem().Kind() == reflect.Interface {
		return nil, fmt.Errorf("sliceConcreteValueSerializer does not support pointer to interface element type: %v", type_)
	}
	return &sliceConcreteValueSerializer{
		type_:          type_,
		elemSerializer: elemSerializer,
		referencable:   nullable(elem),
	}, nil
}

func (s *sliceConcreteValueSerializer) TypeId() TypeId {
	return -LIST
}

func (s *sliceConcreteValueSerializer) NeedToWriteRef() bool {
	return true
}

func (s *sliceConcreteValueSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	buf := ctx.Buffer()

	// WriteData length
	buf.WriteVarUint32(uint32(length))
	if length == 0 {
		return nil
	}

	// Determine collection flags - don't set CollectionIsDeclElementType
	// so that the element type ID is written and can be read by generic deserializer
	collectFlag := CollectionIsSameType
	hasNull := false
	elemType := s.type_.Elem()
	isPointerElem := elemType.Kind() == reflect.Ptr

	// Check for null values first (only applicable for pointer element types)
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

	// WriteData element type info since CollectionIsDeclElementType is not set
	var elemTypeInfo TypeInfo
	if length > 0 {
		// Get type info for the first non-nil element to get proper typeID
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
	// WriteData element type info (handles namespaced types properly)
	internalTypeID := elemTypeInfo.TypeID
	if IsNamespacedType(TypeId(internalTypeID)) {
		if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
			return err
		}
	} else {
		buf.WriteVarUint32Small7(uint32(elemTypeInfo.TypeID))
	}

	// WriteData elements
	trackRefs := (collectFlag & CollectionTrackingRef) != 0

	for i := 0; i < length; i++ {
		elem := value.Index(i)

		// Handle null values (only for pointer element types)
		if hasNull && elem.IsNil() {
			if trackRefs {
				// When tracking refs, the element serializer will write the null flag
				if err := s.elemSerializer.Write(ctx, true, false, elem); err != nil {
					return err
				}
			} else {
				buf.WriteInt8(NullFlag)
			}
			continue
		}

		if trackRefs {
			// Use Write with ref tracking enabled
			// The element serializer will handle writing ref flags
			if err := s.elemSerializer.Write(ctx, true, false, elem); err != nil {
				return err
			}
		} else {
			// WriteData the element using its serializer
			if err := s.elemSerializer.WriteData(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *sliceConcreteValueSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
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

func (s *sliceConcreteValueSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
		typeID := int32(buf.ReadVarUint32Small7())
		if IsNamespacedType(TypeId(typeID)) {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *sliceConcreteValueSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

func (s *sliceConcreteValueSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVarUint32())
	if length == 0 {
		value.Set(reflect.MakeSlice(value.Type(), 0, 0))
		return nil
	}

	// ReadData collection flags
	collectFlag := buf.ReadInt8()

	// ReadData element type info if present in buffer
	// We must consume these bytes for protocol compliance
	if (collectFlag & CollectionIsSameType) != 0 {
		if (collectFlag & CollectionIsDeclElementType) == 0 {
			typeID := int32(buf.ReadVarUint32Small7())
			// ReadData additional metadata for namespaced types
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}

	if value.Cap() < length {
		value.Set(reflect.MakeSlice(value.Type(), length, length))
	} else if value.Len() < length {
		value.Set(value.Slice(0, length))
	}
	ctx.RefResolver().Reference(value)

	trackRefs := (collectFlag & CollectionTrackingRef) != 0

	for i := 0; i < length; i++ {
		elem := value.Index(i)

		// Call element serializer's Read method
		// When trackRefs is true, elemSerializer will read the ref flag via TryPreserveRefId
		// For pointer types, elemSerializer will handle allocation and reference tracking
		if err := s.elemSerializer.Read(ctx, trackRefs, false, elem); err != nil {
			return err
		}
	}
	return nil
}