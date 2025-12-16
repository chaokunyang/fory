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

// GenericSet type.
// TODO use golang generics; support more concrete key types
type GenericSet map[interface{}]bool

func (s GenericSet) Add(values ...interface{}) {
	for _, v := range values {
		s[v] = true
	}
}

type setSerializer struct {
}

func (s setSerializer) TypeId() TypeId {
	return SET
}

func (s setSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	// Get all map keys (set elements)
	keys := value.MapKeys()
	length := len(keys)

	// Handle empty set case
	if length == 0 {
		buf.WriteVaruint32(0) // WriteData 0 length for empty set
		return nil
	}

	// WriteData collection header and get type information
	collectFlag, elemTypeInfo := s.writeHeader(ctx, buf, keys)

	// Check if all elements are of same type
	if (collectFlag & CollectionIsSameType) != 0 {
		// Optimized path for same-type elements
		return s.writeSameType(ctx, buf, keys, elemTypeInfo, collectFlag)
	}
	// Fallback path for mixed-type elements
	return s.writeDifferentTypes(ctx, buf, keys, collectFlag)
}

func (s setSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	if refMode != RefModeNone {
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
		// For polymorphic set elements, need to write full type info
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
func (s setSerializer) writeHeader(ctx *WriteContext, buf *ByteBuffer, keys []reflect.Value) (byte, *TypeInfo) {
	// Initialize collection flags and type tracking variables
	collectFlag := CollectionDefaultFlag
	var elemTypeInfo *TypeInfo
	hasNull := false
	hasSameType := true

	// Check elements to detect types
	// Initialize element type information from first non-null element
	if len(keys) > 0 {
		firstElem := UnwrapReflectValue(keys[0])
		if isNull(firstElem) {
			hasNull = true
		} else {
			// Get type info for first element to use as reference
			elemTypeInfo, _ = ctx.TypeResolver().getTypeInfo(firstElem, true)
		}
	}

	// Iterate through elements to check for nulls and type consistency
	for _, key := range keys {
		key = UnwrapReflectValue(key)
		if isNull(key) {
			hasNull = true
			continue
		}

		// Compare each element's type with the reference type
		currentTypeInfo, _ := ctx.TypeResolver().getTypeInfo(key, true)
		var elemTypeID, currentTypeID uint32
		if elemTypeInfo != nil {
			elemTypeID = elemTypeInfo.TypeID
		}
		if currentTypeInfo != nil {
			currentTypeID = currentTypeInfo.TypeID
		}
		if currentTypeID != elemTypeID {
			hasSameType = false
		}
	}

	// Set collection flags based on findings
	if hasNull {
		collectFlag |= CollectionHasNull // Mark if collection contains null values
	}
	if hasSameType {
		collectFlag |= CollectionIsSameType // Mark if elements have different types
	}

	// Enable reference tracking if configured
	if ctx.TrackRef() {
		collectFlag |= CollectionTrackingRef
	}

	// WriteData metadata to buffer
	buf.WriteVaruint32(uint32(len(keys))) // Collection size
	buf.WriteInt8(int8(collectFlag))      // Collection flags

	// WriteData element type ID if all elements have same type
	if hasSameType && elemTypeInfo != nil {
		buf.WriteVaruint32Small7(uint32(elemTypeInfo.TypeID))
	}

	return byte(collectFlag), elemTypeInfo
}

// writeSameType efficiently serializes a collection where all elements share the same type
func (s setSerializer) writeSameType(ctx *WriteContext, buf *ByteBuffer, keys []reflect.Value, typeInfo *TypeInfo, flag byte) error {
	if typeInfo == nil {
		return nil
	}
	serializer := typeInfo.Serializer
	trackRefs := (flag & CollectionTrackingRef) != 0 // Check if reference tracking is enabled

	for _, key := range keys {
		key = UnwrapReflectValue(key)
		if isNull(key) {
			buf.WriteInt8(NullFlag) // WriteData null marker
			continue
		}

		if trackRefs {
			// Handle reference tracking if enabled
			refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, key)
			if err != nil {
				return err
			}
			if !refWritten {
				// WriteData actual value if not a reference
				if err := serializer.WriteData(ctx, key); err != nil {
					return err
				}
			}
		} else {
			// Directly write value without reference tracking
			if err := serializer.WriteData(ctx, key); err != nil {
				return err
			}
		}
	}
	return nil
}

// writeDifferentTypes handles serialization of collections with mixed element types
func (s setSerializer) writeDifferentTypes(ctx *WriteContext, buf *ByteBuffer, keys []reflect.Value, flag byte) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0

	for _, key := range keys {
		key = UnwrapReflectValue(key)
		if isNull(key) {
			buf.WriteInt8(NullFlag) // WriteData null marker
			continue
		}

		// Get type info for each element (since types vary)
		typeInfo, _ := ctx.TypeResolver().getTypeInfo(key, true)

		if trackRefs {
			// Write ref flag, type ID, and data
			refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, key)
			if err != nil {
				return err
			}
			buf.WriteVaruint32Small7(uint32(typeInfo.TypeID))
			if !refWritten {
				if err := typeInfo.Serializer.WriteData(ctx, key); err != nil {
					return err
				}
			}
		} else if hasNull {
			// No ref tracking but may have nulls - write NotNullValueFlag before type + data
			buf.WriteInt8(NotNullValueFlag)
			buf.WriteVaruint32Small7(uint32(typeInfo.TypeID))
			if err := typeInfo.Serializer.WriteData(ctx, key); err != nil {
				return err
			}
		} else {
			// No ref tracking and no nulls - write type + data directly
			buf.WriteVaruint32Small7(uint32(typeInfo.TypeID))
			if err := typeInfo.Serializer.WriteData(ctx, key); err != nil {
				return err
			}
		}
	}
	return nil
}

// Read deserializes a set from the buffer into the provided reflect.Value
func (s setSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	// ReadData collection length from buffer
	length := int(buf.ReadVaruint32())
	if length == 0 {
		// Initialize empty set if length is 0
		value.Set(reflect.MakeMap(type_))
		return nil
	}

	// ReadData collection flags that indicate special characteristics
	collectFlag := buf.ReadInt8()
	var elemTypeInfo TypeInfo

	// If all elements are same type, read the shared type info
	if (collectFlag & CollectionIsSameType) != 0 {
		elemTypeInfo, _ = ctx.TypeResolver().readTypeInfo(buf, reflect.New(type_.Key()).Elem())
	}

	// Initialize set if nil
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	// Register reference for tracking (handles circular references)
	ctx.RefResolver().Reference(value)

	// Choose appropriate deserialization path based on type consistency
	if (collectFlag & CollectionIsSameType) != 0 {
		return s.readSameType(ctx, buf, value, elemTypeInfo, collectFlag, length)
	}
	return s.readDifferentTypes(ctx, buf, value, length, collectFlag)
}

// readSameType handles deserialization of sets where all elements share the same type
func (s setSerializer) readSameType(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, typeInfo TypeInfo, flag int8, length int) error {
	// Determine if reference tracking is enabled
	trackRefs := (flag & CollectionTrackingRef) != 0
	serializer := typeInfo.Serializer

	for i := 0; i < length; i++ {
		var refID int32
		if trackRefs {
			// Handle reference tracking if enabled
			refID, _ = ctx.RefResolver().TryPreserveRefId(buf)
			if int8(refID) < NotNullValueFlag {
				// Use existing reference if available
				elem := ctx.RefResolver().GetReadObject(refID)
				value.SetMapIndex(reflect.ValueOf(elem), reflect.ValueOf(true))
				continue
			}
		}

		// Create new element and deserialize from buffer
		elem := reflect.New(typeInfo.Type).Elem()
		if err := serializer.ReadData(ctx, elem.Type(), elem); err != nil {
			return err
		} // Register new reference if tracking
		if trackRefs {
			ctx.RefResolver().SetReadObject(refID, elem)
		}
		// Add element to set
		value.SetMapIndex(elem, reflect.ValueOf(true))
	}
	return nil
}

// readDifferentTypes handles deserialization of sets with mixed element types
func (s setSerializer) readDifferentTypes(ctx *ReadContext, buf *ByteBuffer, value reflect.Value, length int, flag int8) error {
	trackRefs := (flag & CollectionTrackingRef) != 0
	hasNull := (flag & CollectionHasNull) != 0
	keyType := value.Type().Key()

	for i := 0; i < length; i++ {
		if trackRefs {
			// Read with ref tracking: refFlag + typeId + data
			refID, err := ctx.RefResolver().TryPreserveRefId(buf)
			if err != nil {
				return err
			}
			if int8(refID) == NullFlag {
				// Null element - skip for sets
				continue
			}
			if int8(refID) < NotNullValueFlag {
				// Use existing reference if available
				elem := ctx.RefResolver().GetReadObject(refID)
				value.SetMapIndex(elem, reflect.ValueOf(true))
				continue
			}
			// Read type info (handles namespaced types, meta sharing, etc.)
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, reflect.New(keyType).Elem())
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			// Create new element and deserialize from buffer
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, elem.Type(), elem); err != nil {
				return err
			}
			ctx.RefResolver().SetReadObject(refID, elem)
			setMapKey(value, elem, keyType)
		} else if hasNull {
			// No ref tracking but may have nulls: headFlag + typeId + data (or just NullFlag)
			headFlag := buf.ReadInt8()
			if headFlag == NullFlag {
				// Null element - skip for sets
				continue
			}
			// headFlag should be NotNullValueFlag, read type info
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, reflect.New(keyType).Elem())
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, elem.Type(), elem); err != nil {
				return err
			}
			setMapKey(value, elem, keyType)
		} else {
			// No ref tracking and no nulls: typeId + data directly
			typeInfo, err := ctx.TypeResolver().readTypeInfo(buf, reflect.New(keyType).Elem())
			if err != nil {
				return fmt.Errorf("failed to read type info: %w", err)
			}
			elem := reflect.New(typeInfo.Type).Elem()
			if err := typeInfo.Serializer.ReadData(ctx, elem.Type(), elem); err != nil {
				return err
			}
			setMapKey(value, elem, keyType)
		}
	}
	return nil
}

// setMapKey sets a key into a map (set), handling interface types where
// the concrete type may need to be wrapped in a pointer to implement the interface.
func setMapKey(mapValue, key reflect.Value, keyType reflect.Type) {
	if keyType.Kind() == reflect.Interface {
		// Check if key is directly assignable to the interface
		if key.Type().AssignableTo(keyType) {
			mapValue.SetMapIndex(key, reflect.ValueOf(true))
		} else {
			// Try pointer - common case where interface has pointer receivers
			ptr := reflect.New(key.Type())
			ptr.Elem().Set(key)
			mapValue.SetMapIndex(ptr, reflect.ValueOf(true))
		}
	} else {
		mapValue.SetMapIndex(key, reflect.ValueOf(true))
	}
}

func (s setSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if refMode != RefModeNone {
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
		// ReadData and discard type info for sets
		typeID := uint32(buf.ReadVaruint32Small7())
		if IsNamespacedType(TypeId(typeID)) {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s setSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
