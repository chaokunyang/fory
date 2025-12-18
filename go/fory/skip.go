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

// SkipFieldValue skips a field value in compatible mode when the field doesn't exist
// or is incompatible with the local type.
func SkipFieldValue(ctx *ReadContext, fieldDef FieldDef, readRefFlag bool) error {
	return SkipFieldValueWithTypeFlag(ctx, fieldDef, readRefFlag, false)
}

// SkipFieldValueWithTypeFlag skips a field value with explicit control over type info reading.
// readTypeInfo should be true if type info was written for this field (struct fields in compatible mode).
func SkipFieldValueWithTypeFlag(ctx *ReadContext, fieldDef FieldDef, readRefFlag bool, readTypeInfo bool) error {
	if readTypeInfo {
		// Type info was written for this field (struct fields in compatible mode)
		// Read ref flag first if needed
		if readRefFlag {
			refFlag := ctx.buffer.ReadInt8()
			if refFlag == NullFlag {
				return nil
			}
			if refFlag == RefFlag {
				// Reference to already-seen object, skip the reference index
				_ = ctx.buffer.ReadVaruint32()
				return nil
			}
			// RefValueFlag (0) or NotNullValueFlag (-1) means we need to read the actual object
		}

		// Read type info (typeID + meta_index)
		wroteTypeID := ctx.buffer.ReadVaruint32Small7()
		internalID := wroteTypeID & 0xff

		// Check if it's an EXT type first - EXT types don't have meta info like structs
		if internalID == uint32(EXT) {
			// EXT types with numeric ID - try to find the registered serializer
			serializer := ctx.TypeResolver().getSerializerByTypeID(wroteTypeID)
			if serializer != nil {
				// Use the serializer to read and discard the value
				var dummy interface{}
				dummyVal := reflect.ValueOf(&dummy).Elem()
				return serializer.Read(ctx, RefModeNone, false, dummyVal)
			}
			// If no serializer is registered, we can't skip this type
			return fmt.Errorf("cannot skip EXT type %d: no serializer registered", wroteTypeID)
		}

		// Check if it's a NAMED_EXT type - need to read type info to find serializer
		if internalID == uint32(NAMED_EXT) {
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, wroteTypeID)
			if err != nil {
				return err
			}
			if typeInfo.Serializer != nil {
				// Use the serializer to read and discard the value
				var dummy interface{}
				dummyVal := reflect.ValueOf(&dummy).Elem()
				return typeInfo.Serializer.Read(ctx, RefModeNone, false, dummyVal)
			}
			return fmt.Errorf("cannot skip NAMED_EXT type: no serializer found")
		}

		// Check if it's a struct type - need to read type info and skip struct data
		if internalID == uint32(COMPATIBLE_STRUCT) || internalID == uint32(STRUCT) ||
			internalID == uint32(NAMED_STRUCT) || internalID == uint32(NAMED_COMPATIBLE_STRUCT) {
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, wroteTypeID)
			if err != nil {
				return err
			}
			// Now skip the struct data using the typeInfo from the written type
			return skipStruct(ctx, &typeInfo)
		}

		if IsNamespacedType(TypeId(wroteTypeID)) {
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, wroteTypeID)
			if err != nil {
				return err
			}
			// Now skip the struct data using the typeInfo from the written type
			return skipStruct(ctx, &typeInfo)
		}
	}

	return skipValue(ctx, fieldDef, readRefFlag, true, nil)
}

// isStructTypeId checks if a type ID represents a struct type
func isStructTypeId(id TypeId) bool {
	return id == STRUCT || id == NAMED_STRUCT ||
		id == COMPATIBLE_STRUCT || id == NAMED_COMPATIBLE_STRUCT
}

// SkipAnyValue skips any value by reading its type info first, then skipping the data.
// This is used for polymorphic types where the actual type is unknown at compile time.
func SkipAnyValue(ctx *ReadContext, readRefFlag bool) error {
	// Handle ref flag first if needed
	if readRefFlag {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
		if refFlag == RefFlag {
			// Reference to already-seen object, skip the reference index
			_ = ctx.buffer.ReadVaruint32()
			return nil
		}
		// RefValueFlag (0) or NotNullValueFlag (-1) means we need to read the actual object
	}

	// ReadData type_id first
	typeID := ctx.buffer.ReadVaruint32Small7()
	internalID := typeID & 0xff

	// For struct-like types, also read meta_index to get type_info
	var fieldDef FieldDef
	var typeInfo *TypeInfo

	switch TypeId(internalID) {
	case LIST, SET:
		fieldDef = FieldDef{
			fieldType: NewCollectionFieldType(TypeId(typeID), NewSimpleFieldType(UNKNOWN)),
			nullable:  true,
		}
	case MAP:
		fieldDef = FieldDef{
			fieldType: NewMapFieldType(TypeId(typeID), NewSimpleFieldType(UNKNOWN), NewSimpleFieldType(UNKNOWN)),
			nullable:  true,
		}
	case COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT, STRUCT, NAMED_STRUCT:
		// For struct types, read meta_index to get type_info
		if ctx.TypeResolver().metaShareEnabled() {
			metaIndex := ctx.buffer.ReadVaruint32()
			context := ctx.TypeResolver().fory.MetaContext()
			if context == nil || int(metaIndex) >= len(context.readTypeInfos) {
				return fmt.Errorf("invalid meta index %d", metaIndex)
			}
			info := context.readTypeInfos[metaIndex]
			typeInfo = &info
		} else {
			// Without share_meta, read namespace and type_name
			nsBytes, err := ctx.TypeResolver().metaStringResolver.ReadMetaStringBytes(ctx.buffer)
			if err != nil {
				return err
			}
			typeNameBytes, err := ctx.TypeResolver().metaStringResolver.ReadMetaStringBytes(ctx.buffer)
			if err != nil {
				return err
			}
			// We don't have the actual type registered, so we'll have to skip fields blindly
			_ = nsBytes
			_ = typeNameBytes
		}
		fieldDef = FieldDef{
			fieldType: NewSimpleFieldType(TypeId(typeID)),
			nullable:  true,
		}
	default:
		fieldDef = FieldDef{
			fieldType: NewSimpleFieldType(TypeId(typeID)),
			nullable:  true,
		}
	}

	// Don't read ref flag again since we already handled it
	return skipValue(ctx, fieldDef, false, false, typeInfo)
}

// readTypeInfoForSkip reads type info from buffer for struct types during skip.
// For DynamicFieldType fields, the buffer contains: typeID + meta info (meta index or namespace/typename).
func readTypeInfoForSkip(ctx *ReadContext, fieldTypeId TypeId) (TypeInfo, error) {
	// Read the actual typeID from buffer (Java writes typeID for struct fields)
	typeID := ctx.buffer.ReadVaruint32Small7()
	// Use readTypeInfoWithTypeID which handles both namespaced and non-namespaced types correctly
	return ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
}

// skipCollection skips a collection (list/set) value
func skipCollection(ctx *ReadContext, fieldDef FieldDef) error {
	length := ctx.buffer.ReadVaruint32()
	if length == 0 {
		return nil
	}

	header := ctx.buffer.ReadByte_()

	hasNull := (header & CollectionHasNull) != 0
	isSameType := (header & CollectionIsSameType) != 0
	trackingRef := (header & CollectionTrackingRef) != 0
	isDeclared := (header & CollectionIsDeclElementType) != 0

	var elemDef FieldDef
	var elemTypeInfo *TypeInfo
	if isSameType && !isDeclared {
		// ReadData element type info - first read the typeID from buffer
		typeID := ctx.buffer.ReadVaruint32Small7()
		typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
		if err != nil {
			return err
		}
		elemDef = FieldDef{
			fieldType: NewSimpleFieldType(TypeId(typeInfo.TypeID)),
			nullable:  hasNull,
		}
		// Keep the typeInfo for struct types so skipValue can use it
		elemTypeInfo = &typeInfo
	} else if isDeclared {
		// Use declared element type from the collection's field type
		if collType, ok := fieldDef.fieldType.(*CollectionFieldType); ok && collType.elementType != nil {
			elemDef = FieldDef{
				fieldType: collType.elementType,
				nullable:  hasNull,
			}
		} else {
			// Fallback: use unknown type
			elemDef = FieldDef{
				fieldType: NewSimpleFieldType(UNKNOWN),
				nullable:  true,
			}
		}
	} else {
		// Not same type - each element has its own type info, use unknown
		elemDef = FieldDef{
			fieldType: NewSimpleFieldType(UNKNOWN),
			nullable:  true,
		}
	}

	if err := ctx.incDepth(); err != nil {
		return err
	}
	defer ctx.decDepth()

	for i := uint32(0); i < length; i++ {
		// Read ref flag if collection has ref tracking enabled
		if err := skipValue(ctx, elemDef, trackingRef, false, elemTypeInfo); err != nil {
			return err
		}
	}

	return nil
}

// skipMap skips a map value
func skipMap(ctx *ReadContext, fieldDef FieldDef) error {
	length := ctx.buffer.ReadVaruint32()
	if length == 0 {
		return nil
	}

	// Extract key/value types from MapFieldType if available
	// When KEY_DECL_TYPE/VALUE_DECL_TYPE flags are set, the type info is NOT written
	// to the buffer, so we must use the declared types from the FieldDef
	var declaredKeyDef, declaredValueDef FieldDef
	if mapFieldType, ok := fieldDef.fieldType.(*MapFieldType); ok {
		declaredKeyDef = FieldDef{
			fieldType: mapFieldType.keyType,
			nullable:  true,
		}
		declaredValueDef = FieldDef{
			fieldType: mapFieldType.valueType,
			nullable:  true,
		}
	} else {
		// Fallback to unknown types if MapFieldType is not available
		declaredKeyDef = FieldDef{
			fieldType: NewSimpleFieldType(UNKNOWN),
			nullable:  true,
		}
		declaredValueDef = FieldDef{
			fieldType: NewSimpleFieldType(UNKNOWN),
			nullable:  true,
		}
	}

	var lenCounter uint32
	for lenCounter < length {
		header := ctx.buffer.ReadByte_()

		// Both null
		if (header&KEY_HAS_NULL) != 0 && (header&VALUE_HAS_NULL) != 0 {
			lenCounter++
			continue
		}

		// Only key is null
		if (header & KEY_HAS_NULL) != 0 {
			valueDeclared := (header & VALUE_DECL_TYPE) != 0
			var valueDef FieldDef
			var valueTypeInfo *TypeInfo
			if !valueDeclared {
				typeID := ctx.buffer.ReadVaruint32Small7()
				typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
				if err != nil {
					return err
				}
				valueDef = FieldDef{
					fieldType: NewSimpleFieldType(TypeId(typeInfo.TypeID)),
					nullable:  true,
				}
				valueTypeInfo = &typeInfo
			} else {
				valueDef = declaredValueDef
			}
			if err := ctx.incDepth(); err != nil {
				return err
			}
			if err := skipValue(ctx, valueDef, false, false, valueTypeInfo); err != nil {
				ctx.decDepth()
				return err
			}
			ctx.decDepth()
			lenCounter++
			continue
		}

		// Only value is null
		if (header & VALUE_HAS_NULL) != 0 {
			keyDeclared := (header & KEY_DECL_TYPE) != 0
			var keyDef FieldDef
			var keyTypeInfo *TypeInfo
			if !keyDeclared {
				typeID := ctx.buffer.ReadVaruint32Small7()
				typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
				if err != nil {
					return err
				}
				keyDef = FieldDef{
					fieldType: NewSimpleFieldType(TypeId(typeInfo.TypeID)),
					nullable:  true,
				}
				keyTypeInfo = &typeInfo
			} else {
				keyDef = declaredKeyDef
			}
			if err := ctx.incDepth(); err != nil {
				return err
			}
			if err := skipValue(ctx, keyDef, false, false, keyTypeInfo); err != nil {
				ctx.decDepth()
				return err
			}
			ctx.decDepth()
			lenCounter++
			continue
		}

		// Both key and value are non-null
		chunkSize := ctx.buffer.ReadByte_()

		keyDeclared := (header & KEY_DECL_TYPE) != 0
		valueDeclared := (header & VALUE_DECL_TYPE) != 0

		var keyDef, valueDef FieldDef
		var keyTypeInfo, valueTypeInfo *TypeInfo
		if !keyDeclared {
			typeID := ctx.buffer.ReadVaruint32Small7()
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
			if err != nil {
				return err
			}
			keyDef = FieldDef{
				fieldType: NewSimpleFieldType(TypeId(typeInfo.TypeID)),
				nullable:  true,
			}
			keyTypeInfo = &typeInfo
		} else {
			keyDef = declaredKeyDef
		}

		if !valueDeclared {
			typeID := ctx.buffer.ReadVaruint32Small7()
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeID)
			if err != nil {
				return err
			}
			valueDef = FieldDef{
				fieldType: NewSimpleFieldType(TypeId(typeInfo.TypeID)),
				nullable:  true,
			}
			valueTypeInfo = &typeInfo
		} else {
			valueDef = declaredValueDef
		}

		// Check if ref tracking is enabled for keys and values
		keyTrackingRef := (header & TRACKING_KEY_REF) != 0
		valueTrackingRef := (header & TRACKING_VALUE_REF) != 0

		if err := ctx.incDepth(); err != nil {
			return err
		}
		for i := byte(0); i < chunkSize; i++ {
			if err := skipValue(ctx, keyDef, keyTrackingRef, false, keyTypeInfo); err != nil {
				ctx.decDepth()
				return err
			}
			if err := skipValue(ctx, valueDef, valueTrackingRef, false, valueTypeInfo); err != nil {
				ctx.decDepth()
				return err
			}
		}
		ctx.decDepth()
		lenCounter += uint32(chunkSize)
	}

	return nil
}

// skipStruct skips a struct value using TypeInfo
func skipStruct(ctx *ReadContext, info *TypeInfo) error {
	// Read struct hash (4 bytes)
	_ = ctx.buffer.ReadInt32()

	// Get fieldDefs from the serializer
	var fieldDefs []FieldDef
	if info.Serializer != nil {
		if ss, ok := info.Serializer.(*structSerializer); ok && ss.fieldDefs != nil {
			fieldDefs = ss.fieldDefs
		} else if sss, ok := info.Serializer.(*skipStructSerializer); ok && sss.fieldDefs != nil {
			fieldDefs = sss.fieldDefs
		}
	}

	// If we couldn't get fieldDefs from serializer, try getTypeDef as fallback
	if fieldDefs == nil {
		typeDef, err := ctx.TypeResolver().getTypeDef(info.Type, false)
		if err != nil {
			return fmt.Errorf("cannot skip struct without field definitions: %w", err)
		}
		fieldDefs = typeDef.fieldDefs
	}

	if err := ctx.incDepth(); err != nil {
		return err
	}
	defer ctx.decDepth()

	for _, fieldDef := range fieldDefs {
		// Use FieldDef's trackingRef and nullable to determine if ref flag was written by Java
		// Java writes ref flag based on its FieldDef, not based on type rules
		readRefFlag := fieldDef.trackingRef || fieldDef.nullable
		// For struct-like fields (struct, ext), type info is written in the buffer
		readTypeInfo := isStructFieldType(fieldDef.fieldType)
		if err := SkipFieldValueWithTypeFlag(ctx, fieldDef, readRefFlag, readTypeInfo); err != nil {
			return err
		}
	}

	return nil
}

// skipValue is the main dispatcher for skipping values based on their type
func skipValue(ctx *ReadContext, fieldDef FieldDef, readRefFlag bool, isField bool, typeInfo *TypeInfo) error {
	if readRefFlag {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
		if refFlag == RefFlag {
			// Reference to already-seen object, skip the reference index
			_ = ctx.buffer.ReadVaruint32()
			return nil
		}
		// RefValueFlag (0) or NotNullValueFlag (-1) means we need to read the actual object
	}

	typeIDNum := uint32(fieldDef.fieldType.TypeId())

	// Check if it's a user-defined type (high bits set, meaning type_id > 255)
	if typeIDNum > 255 {
		internalID := typeIDNum & 0xff
		// Handle struct-like types
		if internalID == uint32(COMPATIBLE_STRUCT) || internalID == uint32(STRUCT) ||
			internalID == uint32(NAMED_STRUCT) || internalID == uint32(NAMED_COMPATIBLE_STRUCT) ||
			internalID == uint32(UNKNOWN) {
			// If type_info is provided (from SkipAnyValue), use skipStruct directly
			if typeInfo != nil {
				return skipStruct(ctx, typeInfo)
			}
			// Otherwise we need to read type info
			ti, err := ctx.TypeResolver().readTypeInfoWithTypeID(ctx.buffer, typeIDNum)
			if err != nil {
				return err
			}
			return skipStruct(ctx, &ti)
		} else if internalID == uint32(ENUM) || internalID == uint32(NAMED_ENUM) {
			_ = ctx.buffer.ReadVaruint32()
			return nil
		} else if internalID == uint32(EXT) || internalID == uint32(NAMED_EXT) {
			// EXT types use custom serializers - try to find the registered serializer
			serializer := ctx.TypeResolver().getSerializerByTypeID(typeIDNum)
			if serializer != nil {
				// Use the serializer to read and discard the value
				// Create a dummy value to read into
				var dummy interface{}
				dummyVal := reflect.ValueOf(&dummy).Elem()
				err := serializer.Read(ctx, RefModeNone, false, dummyVal)
				return err
			}
			// If no serializer is registered, we can't skip this type
			return fmt.Errorf("cannot skip EXT type %d: no serializer registered", typeIDNum)
		} else {
			return fmt.Errorf("unknown type id: %d (internal_id: %d)", typeIDNum, internalID)
		}
	}

	// Match on built-in types
	switch TypeId(typeIDNum) {
	// Boolean type
	case BOOL:
		_ = ctx.buffer.ReadByte_()
		return nil

	// Integer types
	case INT8:
		_ = ctx.buffer.ReadInt8()
		return nil
	case INT16:
		_ = ctx.buffer.ReadInt16()
		return nil
	case INT32:
		_ = ctx.buffer.ReadVaruint32Small7()
		return nil
	case VAR_INT32:
		_ = ctx.buffer.ReadVaruint32Small7()
		return nil
	case INT64, VAR_INT64, SLI_INT64:
		_ = ctx.buffer.ReadVarint64()
		return nil

	// Floating point types
	case FLOAT:
		_ = ctx.buffer.ReadFloat32()
		return nil
	case DOUBLE:
		_ = ctx.buffer.ReadFloat64()
		return nil

	// String types
	case STRING:
		// String format: varuint64 header (size << 2 | encoding) + data bytes
		header := ctx.buffer.ReadVaruint64()
		size := header >> 2
		encoding := header & 0b11
		switch encoding {
		case 0: // Latin1 - 1 byte per char
			_ = ctx.buffer.ReadBinary(int(size))
		case 1: // UTF-16LE - 2 bytes per char
			_ = ctx.buffer.ReadBinary(int(size * 2))
		case 2: // UTF-8 - variable, but size is byte count
			_ = ctx.buffer.ReadBinary(int(size))
		}
		return nil
	case BINARY:
		length := ctx.buffer.ReadVaruint32()
		_ = ctx.buffer.ReadBinary(int(length))
		return nil

	// Date/Time types
	case LOCAL_DATE:
		_ = ctx.buffer.ReadVaruint32Small7()
		return nil
	case TIMESTAMP:
		_ = ctx.buffer.ReadVarint64()
		return nil

	// Container types
	case LIST, SET:
		return skipCollection(ctx, fieldDef)
	case MAP:
		return skipMap(ctx, fieldDef)

	// Struct types
	case COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT, STRUCT, NAMED_STRUCT:
		if typeInfo != nil {
			return skipStruct(ctx, typeInfo)
		}
		// For DynamicFieldType fields, type info is written in the buffer - read it first
		ti, err := readTypeInfoForSkip(ctx, TypeId(typeIDNum))
		if err != nil {
			return err
		}
		return skipStruct(ctx, &ti)

	// Enum types
	case ENUM, NAMED_ENUM:
		_ = ctx.buffer.ReadVaruint32()
		return nil

	// Unsigned integer types
	case UINT8:
		_ = ctx.buffer.ReadByte_()
		return nil
	case UINT16:
		_ = ctx.buffer.ReadInt16() // No ReadUint16, but same binary representation
		return nil
	case UINT32:
		_ = ctx.buffer.ReadVaruint32()
		return nil
	case UINT64:
		_ = ctx.buffer.ReadVaruint64()
		return nil

	// Unknown (polymorphic) type - read type info and skip dynamically
	case UNKNOWN:
		// UNKNOWN (0) is used for polymorphic types in cross-language serialization
		// We need to read the actual type info to know how to skip
		return SkipAnyValue(ctx, false)

	// Named extension types - not yet supported
	case NAMED_EXT:
		return fmt.Errorf("unsupported type for skip: NAMED_EXT (%d)", typeIDNum)

	// Unsupported types
	default:
		return fmt.Errorf("unsupported type for skip: %d", typeIDNum)
	}
}

// fieldNeedWriteRef determines if a field needs a ref flag based on its type and nullability
func fieldNeedWriteRef(typeId TypeId, nullable bool) bool {
	if nullable {
		return true
	}
	// Non-nullable container types still need ref tracking
	switch typeId {
	case LIST, SET, MAP:
		return true
	case STRING, BINARY:
		return true
	default:
		return false
	}
}
