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

// writeSliceRefAndType handles reference and type writing for slice serializers.
// Returns true if the value was already written (nil or ref), false if data should be written.
func writeSliceRefAndType(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value, typeId TypeId) (bool, error) {
	switch refMode {
	case RefModeTracking:
		if value.Kind() == reflect.Slice && value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return true, nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return false, err
		}
		if refWritten {
			return true, nil
		}
	case RefModeNullOnly:
		if value.Kind() == reflect.Slice && value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return true, nil
		}
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(typeId))
	}
	return false, nil
}

// readSliceRefAndType handles reference and type reading for slice serializers.
// Returns true if a reference was resolved (value already set), false if data should be read.
func readSliceRefAndType(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) (bool, error) {
	buf := ctx.Buffer()
	switch refMode {
	case RefModeTracking:
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
	case RefModeNullOnly:
		flag := buf.ReadInt8()
		if flag == NullFlag {
			return true, nil
		}
	}
	if readType {
		buf.ReadVaruint32Small7()
	}
	return false, nil
}

// Helper function to check if a value is null/nil
func isNull(v reflect.Value) bool {
	// Zero value (Invalid kind) is considered null
	if !v.IsValid() {
		return true
	}
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
	typeId         TypeId // TypeId to use: LIST for xlang, -LIST for internal
}

// newSliceConcreteValueSerializer creates a sliceConcreteValueSerializer for slices with concrete element types.
// It returns an error if the element type is an interface or pointer to interface.
// Uses -LIST typeId for internal Go serialization.
func newSliceConcreteValueSerializer(type_ reflect.Type, elemSerializer Serializer) (*sliceConcreteValueSerializer, error) {
	return newSliceConcreteValueSerializerWithTypeId(type_, elemSerializer, -LIST)
}

// newSliceConcreteValueSerializerForXlang creates a sliceConcreteValueSerializer for xlang struct fields.
// Uses LIST typeId for cross-language compatibility.
func newSliceConcreteValueSerializerForXlang(type_ reflect.Type, elemSerializer Serializer) (*sliceConcreteValueSerializer, error) {
	return newSliceConcreteValueSerializerWithTypeId(type_, elemSerializer, LIST)
}

// newSliceConcreteValueSerializerWithTypeId creates a sliceConcreteValueSerializer with a specific TypeId.
func newSliceConcreteValueSerializerWithTypeId(type_ reflect.Type, elemSerializer Serializer, typeId TypeId) (*sliceConcreteValueSerializer, error) {
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
		typeId:         typeId,
	}, nil
}

func (s *sliceConcreteValueSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	length := value.Len()
	buf := ctx.Buffer()

	// WriteData length
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return nil
	}

	// Determine collection flags
	// For xlang (positive typeId): Set CollectionIsDeclElementType since Java knows the type from struct definition
	// For internal Go serialization (negative typeId): Don't set CollectionIsDeclElementType, write element type info
	collectFlag := CollectionIsSameType
	isXlang := s.typeId > 0
	if isXlang {
		collectFlag |= CollectionIsDeclElementType
	}
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

	// Write element type info for internal Go serialization (not xlang)
	// so the reader knows what type to deserialize
	if !isXlang {
		elemTypeInfo, _ := ctx.TypeResolver().getTypeInfo(reflect.New(elemType).Elem(), false)
		if err := ctx.TypeResolver().WriteTypeInfo(buf, elemTypeInfo); err != nil {
			return err
		}
	}

	// WriteData elements
	trackRefs := (collectFlag & CollectionTrackingRef) != 0
	elemRefMode := RefModeNone
	if trackRefs {
		elemRefMode = RefModeTracking
	}

	for i := 0; i < length; i++ {
		elem := value.Index(i)

		// Handle null values (only for pointer element types)
		if hasNull && elem.IsNil() {
			if trackRefs {
				// When tracking refs, the element serializer will write the null flag
				if err := s.elemSerializer.Write(ctx, elemRefMode, false, elem); err != nil {
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
			if err := s.elemSerializer.Write(ctx, elemRefMode, false, elem); err != nil {
				return err
			}
		} else if hasNull {
			// When hasNull is set but trackRefs is not, write NotNullValueFlag before data
			buf.WriteInt8(NotNullValueFlag)
			if err := s.elemSerializer.WriteData(ctx, elem); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls: directly write data
			if err := s.elemSerializer.WriteData(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *sliceConcreteValueSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, s.typeId)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s *sliceConcreteValueSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *sliceConcreteValueSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, refMode, false, value)
}

func (s *sliceConcreteValueSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVaruint32())
	isArrayType := type_.Kind() == reflect.Array

	if length == 0 {
		if !isArrayType {
			value.Set(reflect.MakeSlice(value.Type(), 0, 0))
		}
		return nil
	}

	// ReadData collection flags
	collectFlag := buf.ReadInt8()

	// ReadData element type info if present in buffer
	// We must consume these bytes for protocol compliance
	if (collectFlag & CollectionIsSameType) != 0 {
		if (collectFlag & CollectionIsDeclElementType) == 0 {
			typeID := buf.ReadVaruint32Small7()
			// ReadData additional metadata for namespaced types
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}

	// Handle slice vs array allocation
	if isArrayType {
		// For arrays, verify the length matches (arrays have fixed size)
		if value.Len() < length {
			return fmt.Errorf("array length %d is smaller than serialized length %d", value.Len(), length)
		}
	} else {
		// For slices, allocate or resize as needed
		if value.Cap() < length {
			value.Set(reflect.MakeSlice(value.Type(), length, length))
		} else if value.Len() < length {
			value.Set(value.Slice(0, length))
		}
	}
	ctx.RefResolver().Reference(value)

	trackRefs := (collectFlag & CollectionTrackingRef) != 0
	hasNull := (collectFlag & CollectionHasNull) != 0
	elemRefMode := RefModeNone
	if trackRefs {
		elemRefMode = RefModeTracking
	}

	elemType := s.type_.Elem()
	for i := 0; i < length; i++ {
		elem := value.Index(i)

		if trackRefs {
			// When trackRefs is true, elemSerializer will read the ref flag via TryPreserveRefId
			// For pointer types, elemSerializer will handle allocation and reference tracking
			if err := s.elemSerializer.Read(ctx, elemRefMode, false, elem); err != nil {
				return err
			}
		} else if hasNull {
			// When hasNull is set, read a flag byte for each element:
			// - NullFlag (-3) for null elements
			// - NotNullValueFlag (-1) + data for non-null elements
			refFlag := buf.ReadInt8()
			if refFlag == NullFlag {
				// Element is null, leave slice element as nil (zero value)
				continue
			}
			// refFlag should be NotNullValueFlag, now read the actual data
			if err := s.elemSerializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls: directly read data
			if err := s.elemSerializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
		}
	}
	return nil
}
