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
type sliceDynSerializer struct {
	elemInfo     TypeInfo
	declaredType reflect.Type
}

// newSliceDynSerializer creates a new sliceDynSerializer.
// This serializer is ONLY for slices with interface or pointer to interface element types.
// For other slice types, use sliceConcreteValueSerializer instead.
func newSliceDynSerializer(elemType reflect.Type) (sliceDynSerializer, error) {
	// Nil element type is allowed for fully dynamic slices (e.g., []interface{})
	if elemType == nil {
		return sliceDynSerializer{}, nil
	}
	// Validate element type is interface or pointer to interface
	if elemType.Kind() != reflect.Interface &&
		!(elemType.Kind() == reflect.Ptr && elemType.Elem().Kind() == reflect.Interface) {
		return sliceDynSerializer{}, fmt.Errorf(
			"sliceDynSerializer only supports interface or pointer to interface element types, got %v; use sliceConcreteValueSerializer for other types", elemType)
	}
	return sliceDynSerializer{}, nil
}

// newSliceDynSerializerWithTypeInfo creates a sliceDynSerializer with declared type information.
// This is used when the element type is known at registration time (e.g., struct fields).
func newSliceDynSerializerWithTypeInfo(elemInfo TypeInfo, declaredType reflect.Type) *sliceDynSerializer {
	return &sliceDynSerializer{
		elemInfo:     elemInfo,
		declaredType: declaredType,
	}
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

func (s sliceDynSerializer) TypeId() TypeId {
	return LIST
}

func (s sliceDynSerializer) NeedToWriteRef() bool {
	return true
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

func (s sliceDynSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, LIST)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
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

	if s.declaredType != nil {
		collectFlag |= CollectionIsDeclElementType | CollectionIsSameType
		// Get elemTypeInfo from declared type for writeSameType
		if value.Len() > 0 {
			elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(reflect.New(s.declaredType).Elem(), true)
		}
	} else {
		// Iterate through elements to check for nulls and type consistency
		var firstType reflect.Type
		var firstElem reflect.Value
		for i := 0; i < value.Len(); i++ {
			elem := value.Index(i)
			if elem.Kind() == reflect.Interface || elem.Kind() == reflect.Ptr {
				elem = elem.Elem()
			}
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
	}

	// Set collection flags based on findings
	if hasNull {
		collectFlag |= CollectionHasNull // Mark if collection contains null values
	}
	if hasSameType {
		collectFlag |= CollectionIsSameType // Mark if elements have same types
	}

	// Enable reference tracking if configured and element type supports it
	if ctx.TrackRef() && (elemTypeInfo == nil || elemTypeInfo.Serializer == nil || elemTypeInfo.Serializer.NeedToWriteRef()) {
		collectFlag |= CollectionTrackingRef
	}

	// WriteData metadata to buffer
	buf.WriteVaruint32(uint32(value.Len())) // Collection size
	buf.WriteInt8(int8(collectFlag))        // Collection flags

	// WriteData element type info if all elements have same type and not using declared type
	if hasSameType && (collectFlag&CollectionIsDeclElementType == 0) && elemTypeInfo != nil {
		// For struct types and namespaced types, write full type info including meta share
		if NeedsTypeMetaWrite(TypeId(elemTypeInfo.TypeID)) {
			if err := ctx.TypeResolver().writeTypeInfo(buf, elemTypeInfo); err != nil {
				return 0, nil, err
			}
		} else {
			buf.WriteVaruint32Small7(elemTypeInfo.TypeID)
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
			if err := serializer.Write(ctx, RefModeTracking, false, elem); err != nil {
				return err
			}
		} else if hasNull {
			// When hasNull is set but trackRefs is not, write NotNullValueFlag before data
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

		// Get serializer for this element
		typeInfo, err := ctx.TypeResolver().getTypeInfo(elem, true)
		if err != nil {
			return err
		}

		if trackRefs {
			// Write ref flag, and if not a reference, write type info and data
			refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, elem)
			if err != nil {
				return err
			}
			if !refWritten {
				// Write full type info (handles namespaced types with meta string encoding)
				if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
					return err
				}
				if err := typeInfo.Serializer.WriteData(ctx, elem); err != nil {
					return err
				}
			}
		} else if hasNull {
			// No ref tracking but may have nulls - write NotNullValueFlag before type + data
			buf.WriteInt8(NotNullValueFlag)
			// Write full type info (handles namespaced types with meta string encoding)
			if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
				return err
			}
			if err := typeInfo.Serializer.WriteData(ctx, elem); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls - write type + data directly
			// Write full type info (handles namespaced types with meta string encoding)
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

func (s sliceDynSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	// ReadData slice length from buffer
	length := int(buf.ReadVaruint32())
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
			// Read type info (handles namespaced types, meta sharing, etc.)
			elemTypeInfo, _ = ctx.TypeResolver().readTypeInfo(buf, reflect.New(type_.Elem()).Elem())
		}
	}

	// Priority 1: Use declared type and its serializer if available
	if s.declaredType != nil {
		elemType = s.declaredType
		elemSerializer = s.elemInfo.Serializer
	} else if type_.Elem().Kind() != reflect.Interface {
		// Priority 2: Use the slice's element type to get serializer
		elemType = type_.Elem()
		typeInfoPtr, err := ctx.TypeResolver().getTypeInfo(reflect.New(elemType).Elem(), true)
		if err == nil && typeInfoPtr != nil {
			elemTypeInfo = *typeInfoPtr
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
				panic(fmt.Sprintf("sliceDynSerializer.ReadValue: unexpected type %v (kind=%v)", type_, type_.Kind()))
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
		err = s.readDifferentTypes(ctx, buf, readValue, collectFlag)
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

func (s sliceDynSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s sliceDynSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, refMode, false, value)
}

// readSameType handles deserialization of slices where all elements share the same type
func (s sliceDynSerializer) readSameType(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, elemType reflect.Type, serializer Serializer, flag int8) error {
	// Determine if reference tracking is enabled
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0
	if serializer == nil {
		return fmt.Errorf("no serializer available for element type %v", elemType)
	}

	// Check if the slice element type is a pointer type or interface
	// This affects how we handle null values
	sliceElemType := value.Type().Elem()
	isNullableElem := sliceElemType.Kind() == reflect.Ptr || sliceElemType.Kind() == reflect.Interface

	for i := 0; i < value.Len(); i++ {
		if trackRefs {
			// When trackRefs is enabled, the serializer.Read will handle null detection
			// via TryPreserveRefId which reads the ref flag
			if isNullableElem {
				// For pointer/interface elements, peek at ref flag to check for null
				refID, err := ctx.RefResolver().TryPreserveRefId(buf)
				if err != nil {
					return err
				}
				if int8(refID) == NullFlag {
					// Element is null, leave slice element as nil (zero value for pointer/interface)
					continue
				}
				// Not null, read the element
				if sliceElemType.Kind() == reflect.Ptr {
					ptr := reflect.New(elemType)
					if err := serializer.ReadData(ctx, elemType, ptr.Elem()); err != nil {
						return err
					}
					ctx.RefResolver().Reference(ptr)
					value.Index(i).Set(ptr)
				} else {
					// Interface element
					elem := reflect.New(elemType).Elem()
					if err := serializer.ReadData(ctx, elemType, elem); err != nil {
						return err
					}
					ctx.RefResolver().Reference(elem)
					value.Index(i).Set(elem)
				}
			} else {
				// Non-nullable elements: read with ref tracking
				elem := reflect.New(elemType).Elem()
				if err := serializer.Read(ctx, RefModeTracking, false, elem); err != nil {
					return err
				}
				value.Index(i).Set(elem)
			}
		} else if hasNull {
			// No ref tracking, but collection may have nulls
			// When hasNull is set, writer writes a flag byte for each element:
			// - NullFlag (-3) for null elements
			// - NotNullValueFlag (-1) + data for non-null elements
			refFlag := buf.ReadInt8()
			if refFlag == NullFlag {
				// Element is null, leave slice element as nil (zero value)
				continue
			}
			// refFlag should be NotNullValueFlag, now read the actual data
			elem := reflect.New(elemType).Elem()
			if err := serializer.ReadData(ctx, elemType, elem); err != nil {
				return err
			}
			if isNullableElem && sliceElemType.Kind() == reflect.Ptr {
				ptr := reflect.New(elemType)
				ptr.Elem().Set(elem)
				value.Index(i).Set(ptr)
			} else {
				value.Index(i).Set(elem)
			}
		} else {
			// No ref tracking and no nulls: directly read data
			if isNullableElem && sliceElemType.Kind() == reflect.Ptr {
				ptr := reflect.New(elemType)
				if err := serializer.ReadData(ctx, elemType, ptr.Elem()); err != nil {
					return err
				}
				value.Index(i).Set(ptr)
			} else {
				elem := reflect.New(elemType).Elem()
				if err := serializer.ReadData(ctx, elemType, elem); err != nil {
					return err
				}
				value.Index(i).Set(elem)
			}
		}
	}
	return nil
}

// readDifferentTypes handles deserialization of slices with mixed element types
func (s sliceDynSerializer) readDifferentTypes(
	ctx *ReadContext, buf *ByteBuffer, value reflect.Value, flag int8) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0
	sliceElemType := value.Type().Elem()

	for i := 0; i < value.Len(); i++ {
		if trackRefs {
			// Read with ref tracking: refFlag + typeId + data
			refID, err := ctx.RefResolver().TryPreserveRefId(buf)
			if err != nil {
				return err
			}
			if int8(refID) == NullFlag {
				// Null element
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
			// Read type info (handles namespaced types, meta sharing, etc.)
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, value.Index(i))
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			// Create new element of actual type and read data
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, typeInfo.Type, elem); err != nil {
				return err
			}
			ctx.RefResolver().SetReadObject(refID, elem)
			// Set element, handling interface/pointer types
			setSliceElement(value.Index(i), elem, sliceElemType)
		} else if hasNull {
			// No ref tracking but may have nulls: headFlag + typeId + data (or just NullFlag)
			headFlag := buf.ReadInt8()
			if headFlag == NullFlag {
				// Null element
				continue
			}
			// headFlag should be NotNullValueFlag, read type info
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, value.Index(i))
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, typeInfo.Type, elem); err != nil {
				return err
			}
			// Set element
			setSliceElement(value.Index(i), elem, sliceElemType)
		} else {
			// No ref tracking and no nulls: typeId + data directly
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, value.Index(i))
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, typeInfo.Type, elem); err != nil {
				return err
			}
			// Set element
			setSliceElement(value.Index(i), elem, sliceElemType)
		}
	}
	return nil
}

// setSliceElement sets an element into a slice, handling interface types where
// the concrete type may need to be wrapped in a pointer to implement the interface.
func setSliceElement(target, elem reflect.Value, targetType reflect.Type) {
	if targetType.Kind() == reflect.Interface {
		// Check if elem is directly assignable to the interface
		if elem.Type().AssignableTo(targetType) {
			target.Set(elem)
		} else {
			// Try pointer - common case where interface has pointer receivers
			ptr := reflect.New(elem.Type())
			ptr.Elem().Set(elem)
			target.Set(ptr)
		}
	} else if targetType.Kind() == reflect.Ptr && elem.Type().Kind() != reflect.Ptr {
		ptr := reflect.New(elem.Type())
		ptr.Elem().Set(elem)
		target.Set(ptr)
	} else {
		target.Set(elem)
	}
}
