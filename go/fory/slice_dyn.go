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

// sliceDynSerializer provides the dynamic slice implementation that inspects
// element values at runtime.
// This serializer is designed for slices with any interface element type
// (e.g., []interface{}, []io.Reader, []fmt.Stringer, or pointers to interfaces).
// For slices with concrete element types, use sliceConcreteValueSerializer instead.
type sliceDynSerializer struct {
	elemType        reflect.Type
	isInterfaceElem bool
	isPointerElem   bool
}

// newSliceDynSerializer creates a new sliceDynSerializer.
// This serializer is ONLY for slices with interface or pointer to interface element types.
// For other slice types, use sliceConcreteValueSerializer instead.
func newSliceDynSerializer(elemType reflect.Type) (sliceDynSerializer, error) {
	// Nil element type is allowed for fully dynamic slices (e.g., []interface{})
	if elemType == nil {
		return sliceDynSerializer{
			isInterfaceElem: true,
		}, nil
	}
	// Validate element type is interface or pointer to interface
	isInterface := elemType.Kind() == reflect.Interface
	isPointerToInterface := elemType.Kind() == reflect.Ptr && elemType.Elem().Kind() == reflect.Interface
	if !isInterface && !isPointerToInterface {
		return sliceDynSerializer{}, fmt.Errorf(
			"sliceDynSerializer only supports interface or pointer to interface element types, got %v; use sliceConcreteValueSerializer for other types", elemType)
	}
	return sliceDynSerializer{
		elemType:        elemType,
		isInterfaceElem: isInterface,
		isPointerElem:   isPointerToInterface,
	}, nil
}

// mustNewSliceDynSerializer is like newSliceDynSerializer but panics on error.
// Used for initialization code where the element type is known to be valid.
func mustNewSliceDynSerializer(elemType reflect.Type) sliceDynSerializer {
	s, err := newSliceDynSerializer(elemType)
	if err != nil {
		panic(err)
	}
	return s
}

func (s sliceDynSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, LIST)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s sliceDynSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	// Get slice length and handle empty slice case
	length := value.Len()
	if length == 0 {
		buf.WriteVaruint32(0) // WriteData 0 for empty slice
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
	return s.writeDifferentTypes(ctx, buf, value, collectFlag) // Fallback path for mixed-type elements
}

// writeHeader prepares and writes collection metadata including:
// - Collection size
// - Type consistency flags
// - Element type information (if homogeneous)
// Returns pointer to TypeInfo to avoid copy overhead.
func (s sliceDynSerializer) writeHeader(ctx *WriteContext, buf *ByteBuffer, value reflect.Value) (byte, *TypeInfo, error) {
	collectFlag := CollectionDefaultFlag
	var elemTypeInfo *TypeInfo
	hasNull := false
	hasSameType := true

	// Iterate through elements to check for nulls and type consistency
	var firstType reflect.Type
	var firstElem reflect.Value
	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i).Elem()
		if isNull(elem) {
			hasNull = true
			continue
		}

		// Track first non-null element type
		if firstType == nil {
			firstType = elem.Type()
			firstElem = elem
		} else {
			// Compare each element's type with the first element's type
			if firstType != elem.Type() {
				hasSameType = false
			}
		}
	}
	// Only get elemTypeInfo if all elements have same type
	if hasSameType && firstElem.IsValid() {
		elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(firstElem, true)
	}

	// Set collection flags based on findings
	if hasNull {
		collectFlag |= CollectionHasNull // Mark if collection contains null values
	}
	if hasSameType {
		collectFlag |= CollectionIsSameType // Mark if elements have same types
	}

	// Enable reference tracking if configured and element type supports it
	if ctx.TrackRef() && (elemTypeInfo == nil || elemTypeInfo.NeedWriteRef) {
		collectFlag |= CollectionTrackingRef
	}

	// WriteData metadata to buffer
	buf.WriteVaruint32(uint32(value.Len())) // Collection size
	buf.WriteInt8(int8(collectFlag))        // Collection flags

	// WriteData element type info if all elements have same type and not using declared type
	if hasSameType && (collectFlag&CollectionIsDeclElementType == 0) && elemTypeInfo != nil {
		if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
			return 0, nil, err
		}
	}

	return byte(collectFlag), elemTypeInfo, nil
}

// writeSameType efficiently serializes a slice where all elements share the same type
func (s sliceDynSerializer) writeSameType(
	ctx *WriteContext, buf *ByteBuffer, value reflect.Value, typeInfo *TypeInfo, flag byte) error {
	if typeInfo == nil {
		return nil
	}
	serializer := typeInfo.Serializer
	trackRefs := (flag & CollectionTrackingRef) != 0 // Check if reference tracking is enabled
	hasNull := (flag & CollectionHasNull) != 0

	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i).Elem()
		if trackRefs {
			// serializer.Write handles null via ref tracking
			if err := serializer.Write(ctx, RefModeTracking, false, elem); err != nil {
				return err
			}
		} else if hasNull {
			// Check null only when hasNull flag is set
			if isNull(elem) {
				buf.WriteInt8(NullFlag)
				continue
			}
			buf.WriteInt8(NotNullValueFlag)
			if err := serializer.WriteData(ctx, elem); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls: directly write data
			if err := serializer.WriteData(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

// writeDifferentTypes handles serialization of slices with mixed element types
func (s sliceDynSerializer) writeDifferentTypes(ctx *WriteContext, buf *ByteBuffer, value reflect.Value, flag byte) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0

	for i := 0; i < value.Len(); i++ {
		elem := value.Index(i).Elem()
		if trackRefs {
			// WriteRefOrNull handles null via ref tracking
			refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, elem)
			if err != nil {
				return err
			}
			if !refWritten {
				typeInfo, err := ctx.TypeResolver().getTypeInfo(elem, true)
				if err != nil {
					return err
				}
				if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
					return err
				}
				if err := typeInfo.Serializer.WriteData(ctx, elem); err != nil {
					return err
				}
			}
		} else if hasNull {
			// Check null only when hasNull flag is set
			if isNull(elem) {
				buf.WriteInt8(NullFlag)
				continue
			}
			buf.WriteInt8(NotNullValueFlag)
			typeInfo, err := ctx.TypeResolver().getTypeInfo(elem, true)
			if err != nil {
				return err
			}
			if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
				return err
			}
			if err := typeInfo.Serializer.WriteData(ctx, elem); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls - write type + data directly
			typeInfo, err := ctx.TypeResolver().getTypeInfo(elem, true)
			if err != nil {
				return err
			}
			if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
				return err
			}
			if err := typeInfo.Serializer.WriteData(ctx, elem); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s sliceDynSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s sliceDynSerializer) ReadData(ctx *ReadContext, _ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := int(buf.ReadVaruint32())
	sliceType := value.Type()
	value.Set(reflect.MakeSlice(sliceType, length, length))
	if length == 0 {
		return nil
	}

	collectFlag := buf.ReadInt8()
	ctx.RefResolver().Reference(value)

	var elemTypeInfo TypeInfo
	var elemType reflect.Type
	var elemSerializer Serializer
	if (collectFlag & CollectionIsSameType) != 0 {
		if (collectFlag & CollectionIsDeclElementType) == 0 {
			elemTypeInfo, _ = ctx.TypeResolver().readTypeInfo(buf, reflect.New(sliceType.Elem()).Elem())
		}
		if elemTypeInfo.Serializer != nil {
			elemType = elemTypeInfo.Type
			elemSerializer = elemTypeInfo.Serializer
		}
		return s.readSameType(ctx, buf, value, elemType, elemSerializer, collectFlag)
	}
	return s.readDifferentTypes(ctx, buf, value, collectFlag)
}

func (s sliceDynSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, refMode, false, value)
}

// readSameType handles deserialization of slices where all elements share the same type
func (s sliceDynSerializer) readSameType(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, elemType reflect.Type, serializer Serializer, flag int8) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0
	if serializer == nil {
		return fmt.Errorf("no serializer available for element type %v", elemType)
	}

	// Wrap serializer to produce pointers if needed for interface implementation
	elemType, serializer = s.wrapSerializerIfNeeded(elemType, serializer)

	for i := 0; i < value.Len(); i++ {
		if trackRefs {
			refID, err := ctx.RefResolver().TryPreserveRefId(buf)
			if err != nil {
				return err
			}
			if int8(refID) == NullFlag {
				continue
			}
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			ctx.RefResolver().Reference(elem)
			value.Index(i).Set(elem)
		} else if hasNull {
			refFlag := buf.ReadInt8()
			if refFlag == NullFlag {
				continue
			}
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			value.Index(i).Set(elem)
		} else {
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			value.Index(i).Set(elem)
		}
	}
	return nil
}

// readDifferentTypes handles deserialization of slices with mixed element types
func (s sliceDynSerializer) readDifferentTypes(
	ctx *ReadContext, buf *ByteBuffer, value reflect.Value, flag int8) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0

	for i := 0; i < value.Len(); i++ {
		if trackRefs {
			refID, err := ctx.RefResolver().TryPreserveRefId(buf)
			if err != nil {
				return err
			}
			if int8(refID) == NullFlag {
				continue
			}
			if int8(refID) < NotNullValueFlag {
				// Reference to existing object
				obj := ctx.RefResolver().GetReadObject(refID)
				if obj.IsValid() {
					value.Index(i).Set(obj)
				}
				continue
			}
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, value.Index(i))
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elemType, serializer := s.wrapSerializerIfNeeded(typeInfo.Type, typeInfo.Serializer)
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			ctx.RefResolver().SetReadObject(refID, elem)
			value.Index(i).Set(elem)
		} else {
			if hasNull {
				headFlag := buf.ReadInt8()
				if headFlag == NullFlag {
					continue
				}
			}
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, value.Index(i))
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elemType, serializer := s.wrapSerializerIfNeeded(typeInfo.Type, typeInfo.Serializer)
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			value.Index(i).Set(elem)
		}
	}
	return nil
}

// wrapSerializerIfNeeded wraps the serializer with ptrToValueSerializer if:
//  1. Slice element type is pointer-to-interface and the deserialized type is not a pointer, OR
//  2. Slice element type is interface and the deserialized type doesn't directly implement it
//     but the pointer type does (common case where interface has pointer receivers)
func (s sliceDynSerializer) wrapSerializerIfNeeded(elemType reflect.Type, serializer Serializer) (reflect.Type, Serializer) {
	if elemType.Kind() == reflect.Ptr {
		return elemType, serializer
	}
	// Check if we need pointer wrapper for isPointerElem or interface implementation
	needsPointer := s.isPointerElem ||
		(s.isInterfaceElem && s.elemType != nil && !elemType.AssignableTo(s.elemType))
	if needsPointer {
		return reflect.PtrTo(elemType), &ptrToValueSerializer{valueSerializer: serializer}
	}
	return elemType, serializer
}
