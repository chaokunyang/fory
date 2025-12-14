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
	"sort"
	"strings"
	"unicode"
	"unicode/utf8"
	"unsafe"

	"github.com/spaolacci/murmur3"
)

// FieldInfo stores field metadata computed at init time
// Uses offset for unsafe direct memory access at runtime
type FieldInfo struct {
	Name         string
	Offset       uintptr
	Type         reflect.Type
	StaticId     StaticTypeId
	Serializer   Serializer
	Referencable bool
	FieldIndex   int      // -1 if field doesn't exist in current struct (for compatible mode)
	FieldDef     FieldDef // original FieldDef from remote TypeDef (for compatible mode skip)
}

// isFixedSizePrimitive returns true for non-nullable fixed-size primitives
func isFixedSizePrimitive(staticId StaticTypeId, referencable bool) bool {
	if referencable {
		return false
	}
	switch staticId {
	case ConcreteTypeBool, ConcreteTypeInt8, ConcreteTypeInt16,
		ConcreteTypeFloat32, ConcreteTypeFloat64:
		return true
	default:
		return false
	}
}

// isVarintPrimitive returns true for non-nullable varint primitives
func isVarintPrimitive(staticId StaticTypeId, referencable bool) bool {
	if referencable {
		return false
	}
	switch staticId {
	case ConcreteTypeInt32, ConcreteTypeInt64, ConcreteTypeInt:
		return true
	default:
		return false
	}
}

// isPrimitiveStaticId returns true if the staticId represents a primitive type
func isPrimitiveStaticId(staticId StaticTypeId) bool {
	switch staticId {
	case ConcreteTypeBool, ConcreteTypeInt8, ConcreteTypeInt16, ConcreteTypeInt32,
		ConcreteTypeInt64, ConcreteTypeInt, ConcreteTypeFloat32, ConcreteTypeFloat64:
		return true
	default:
		return false
	}
}

// fieldHasNonPrimitiveSerializer returns true if the field has a serializer with a non-primitive type ID.
// This is used to skip the fast path for fields like enums where StaticId is int32 but the serializer
// writes a different format (e.g., unsigned varint for enum ordinals vs signed zigzag for int32).
func fieldHasNonPrimitiveSerializer(field *FieldInfo) bool {
	if field.Serializer == nil {
		return false
	}
	typeId := field.Serializer.TypeId()
	// ENUM (numeric ID), NAMED_ENUM (namespace/typename), NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, NAMED_EXT
	// all require special serialization and should not use the primitive fast path
	// Note: ENUM uses unsigned Varuint32Small7 for ordinals, not signed zigzag varint
	switch typeId {
	case ENUM, NAMED_ENUM, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, NAMED_EXT:
		return true
	default:
		return false
	}
}

type structSerializer struct {
	typeTag         string
	type_           reflect.Type
	fields          []*FieldInfo          // all fields in sorted order
	fixedFields     []*FieldInfo          // fixed-size primitives (bool, int8, int16, float32, float64)
	varintFields    []*FieldInfo          // varint primitives (int32, int64, int)
	remainingFields []*FieldInfo          // all other fields (string, slice, map, struct, etc.)
	fieldMap        map[string]*FieldInfo // for compatible reading
	structHash      int32
	fieldDefs       []FieldDef // for type_def compatibility
}

var UNKNOWN_TYPE_ID = int16(63)

func (s *structSerializer) TypeId() TypeId {
	return NAMED_STRUCT
}

func (s *structSerializer) NeedToWriteRef() bool {
	return true
}

func (s *structSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	// Dereference pointer if needed
	if value.Kind() == reflect.Ptr {
		if value.IsNil() {
			return fmt.Errorf("cannot write nil pointer")
		}
		value = value.Elem()
	}

	if s.fields == nil {
		if s.type_ == nil {
			s.type_ = value.Type()
		}
		// Ensure s.type_ is the struct type, not a pointer type
		for s.type_.Kind() == reflect.Ptr {
			s.type_ = s.type_.Elem()
		}
		// If we have fieldDefs from TypeDef (compatible mode), use them
		// Otherwise initialize from local type structure
		if s.fieldDefs != nil {
			if err := s.initFieldsFromDefsWithResolver(ctx.TypeResolver()); err != nil {
				return err
			}
		} else {
			if err := s.initFieldsFromContext(ctx); err != nil {
				return err
			}
		}
	}
	if s.structHash == 0 {
		s.structHash = s.computeHash()
	}

	// In compatible mode with meta share, struct hash is not written
	// because type meta is written separately
	if !ctx.Compatible() {
		buf.WriteInt32(s.structHash)
	}

	// In compatible mode, we need to write fields in TypeDef order (declaration order)
	// because the TypeMeta written to the buffer uses TypeDef order
	if ctx.Compatible() {
		// If we have fieldDefs from reading TypeMeta, use them
		if s.fieldDefs != nil {
			return s.writeFieldsInOrder(ctx, value)
		}
		// Otherwise, get TypeDef and use its field order
		typeDef, err := ctx.TypeResolver().getTypeDef(s.type_, true)
		if err == nil && typeDef != nil && len(typeDef.fieldDefs) > 0 {
			return s.writeFieldsInTypeDefOrder(ctx, value, typeDef.fieldDefs)
		}
	}

	// Check if value is addressable for unsafe access optimization
	canUseUnsafe := value.CanAddr()

	// Phase 1: Write fixed-size primitive fields (no ref flag)
	if canUseUnsafe {
		ptr := unsafe.Pointer(value.UnsafeAddr())
		for _, field := range s.fixedFields {
			if field.FieldIndex < 0 {
				s.writeZeroField(ctx, field)
				continue
			}
			fieldPtr := unsafe.Add(ptr, field.Offset)
			switch field.StaticId {
			case ConcreteTypeBool:
				buf.WriteBool(*(*bool)(fieldPtr))
			case ConcreteTypeInt8:
				buf.WriteByte_(*(*byte)(fieldPtr))
			case ConcreteTypeInt16:
				buf.WriteInt16(*(*int16)(fieldPtr))
			case ConcreteTypeFloat32:
				buf.WriteFloat32(*(*float32)(fieldPtr))
			case ConcreteTypeFloat64:
				buf.WriteFloat64(*(*float64)(fieldPtr))
			}
		}
	} else {
		// Fallback to reflect-based access for unaddressable values
		for _, field := range s.fixedFields {
			if field.FieldIndex < 0 {
				s.writeZeroField(ctx, field)
				continue
			}
			fieldValue := value.Field(field.FieldIndex)
			switch field.StaticId {
			case ConcreteTypeBool:
				buf.WriteBool(fieldValue.Bool())
			case ConcreteTypeInt8:
				buf.WriteByte_(byte(fieldValue.Int()))
			case ConcreteTypeInt16:
				buf.WriteInt16(int16(fieldValue.Int()))
			case ConcreteTypeFloat32:
				buf.WriteFloat32(float32(fieldValue.Float()))
			case ConcreteTypeFloat64:
				buf.WriteFloat64(fieldValue.Float())
			}
		}
	}

	// Phase 2: Write varint primitive fields (no ref flag)
	if canUseUnsafe {
		ptr := unsafe.Pointer(value.UnsafeAddr())
		for _, field := range s.varintFields {
			if field.FieldIndex < 0 {
				s.writeZeroField(ctx, field)
				continue
			}
			fieldPtr := unsafe.Add(ptr, field.Offset)
			switch field.StaticId {
			case ConcreteTypeInt32:
				buf.WriteVarint32(*(*int32)(fieldPtr))
			case ConcreteTypeInt64:
				buf.WriteVarint64(*(*int64)(fieldPtr))
			case ConcreteTypeInt:
				buf.WriteVarint64(int64(*(*int)(fieldPtr)))
			}
		}
	} else {
		// Fallback to reflect-based access for unaddressable values
		for _, field := range s.varintFields {
			if field.FieldIndex < 0 {
				s.writeZeroField(ctx, field)
				continue
			}
			fieldValue := value.Field(field.FieldIndex)
			switch field.StaticId {
			case ConcreteTypeInt32:
				buf.WriteVarint32(int32(fieldValue.Int()))
			case ConcreteTypeInt64:
				buf.WriteVarint64(fieldValue.Int())
			case ConcreteTypeInt:
				buf.WriteVarint64(fieldValue.Int())
			}
		}
	}

	// Phase 3: Write remaining fields (all non-primitives need ref flag per xlang spec)
	for _, field := range s.remainingFields {
		if field.FieldIndex < 0 {
			s.writeZeroField(ctx, field)
			continue
		}
		fieldValue := value.Field(field.FieldIndex)

		// Special handling for enum fields: always emit NotNullValueFlag then ordinal
		// to align with Java xlang encoding (unsigned Varuint32Small7 for ordinal).
		if field.Serializer != nil {
			serTypeId := field.Serializer.TypeId()
			if serTypeId == ENUM || serTypeId == NAMED_ENUM {
				buf.WriteInt8(NotNullValueFlag)
				if err := field.Serializer.WriteData(ctx, fieldValue); err != nil {
					return err
				}
				continue
			}
		}

		if field.Serializer != nil {
			// For nested struct fields in compatible mode, write type info
			writeType := ctx.Compatible() && isStructField(field.Type)
			// Per xlang spec, all non-primitive fields need ref flag
			if err := field.Serializer.Write(ctx, true, writeType, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.WriteValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

// writeFieldsInOrder writes fields in the order they appear in s.fields (TypeDef order)
// This is used in compatible mode where Java expects fields in TypeDef order
func (s *structSerializer) writeFieldsInOrder(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	canUseUnsafe := value.CanAddr()
	var ptr unsafe.Pointer
	if canUseUnsafe {
		ptr = unsafe.Pointer(value.UnsafeAddr())
	}

	for _, field := range s.fields {
		if field.FieldIndex < 0 {
			if err := s.writeZeroField(ctx, field); err != nil {
				return err
			}
			continue
		}

		// Fast path for fixed-size primitive types (no ref flag)
		if canUseUnsafe && isFixedSizePrimitive(field.StaticId, field.Referencable) {
			fieldPtr := unsafe.Add(ptr, field.Offset)
			switch field.StaticId {
			case ConcreteTypeBool:
				buf.WriteBool(*(*bool)(fieldPtr))
			case ConcreteTypeInt8:
				buf.WriteByte_(*(*byte)(fieldPtr))
			case ConcreteTypeInt16:
				buf.WriteInt16(*(*int16)(fieldPtr))
			case ConcreteTypeFloat32:
				buf.WriteFloat32(*(*float32)(fieldPtr))
			case ConcreteTypeFloat64:
				buf.WriteFloat64(*(*float64)(fieldPtr))
			}
			continue
		}

		// Fast path for varint primitive types (no ref flag)
		// Skip fast path if field has a serializer with a non-primitive type (e.g., NAMED_ENUM)
		if canUseUnsafe && isVarintPrimitive(field.StaticId, field.Referencable) && !fieldHasNonPrimitiveSerializer(field) {
			fieldPtr := unsafe.Add(ptr, field.Offset)
			switch field.StaticId {
			case ConcreteTypeInt32:
				buf.WriteVarint32(*(*int32)(fieldPtr))
			case ConcreteTypeInt64:
				buf.WriteVarint64(*(*int64)(fieldPtr))
			case ConcreteTypeInt:
				buf.WriteVarint64(int64(*(*int)(fieldPtr)))
			}
			continue
		}

		// Get field value for slow paths
		fieldValue := value.Field(field.FieldIndex)

		// Special handling for enum fields:
		// Java always writes null flag + ordinal for enum fields (both compatible and non-compatible mode)
		// Java writes enum ordinals as unsigned Varuint32Small7, not signed zigzag
		if field.Serializer != nil {
			serTypeId := field.Serializer.TypeId()
			if serTypeId == ENUM || serTypeId == NAMED_ENUM {
				// Handle pointer enum fields
				if fieldValue.Kind() == reflect.Ptr {
					if fieldValue.IsNil() {
						buf.WriteInt8(NullFlag)
						continue
					}
					buf.WriteInt8(NotNullValueFlag)
					// For pointer enum fields, the serializer is ptrToValueSerializer wrapping enumSerializer.
					// We need to call the inner enumSerializer directly with the dereferenced value.
					if ptrSer, ok := field.Serializer.(*ptrToValueSerializer); ok {
						if err := ptrSer.valueSerializer.WriteData(ctx, fieldValue.Elem()); err != nil {
							return err
						}
					} else {
						if err := field.Serializer.WriteData(ctx, fieldValue.Elem()); err != nil {
							return err
						}
					}
				} else {
					// Java always writes null flag for enum fields in struct
					buf.WriteInt8(NotNullValueFlag)
					if err := field.Serializer.WriteData(ctx, fieldValue); err != nil {
						return err
					}
				}
				continue
			}
		}

		// Slow path for primitives when canUseUnsafe is false
		// These don't need ref flag according to xlang spec

		// Handle non-nullable primitives without ref flag (slow path version)
		if !field.Referencable && isPrimitiveStaticId(field.StaticId) {
			switch field.StaticId {
			case ConcreteTypeBool:
				buf.WriteBool(fieldValue.Bool())
			case ConcreteTypeInt8:
				buf.WriteByte_(byte(fieldValue.Int()))
			case ConcreteTypeInt16:
				buf.WriteInt16(int16(fieldValue.Int()))
			case ConcreteTypeInt32:
				buf.WriteVarint32(int32(fieldValue.Int()))
			case ConcreteTypeInt64, ConcreteTypeInt:
				buf.WriteVarint64(fieldValue.Int())
			case ConcreteTypeFloat32:
				buf.WriteFloat32(float32(fieldValue.Float()))
			case ConcreteTypeFloat64:
				buf.WriteFloat64(fieldValue.Float())
			default:
				return fmt.Errorf("unhandled primitive type: %v", field.StaticId)
			}
			continue
		}

		// Slow path for non-primitives (all need ref flag per xlang spec)
		if field.Serializer != nil {
			// Per xlang spec:
			// - Nullable primitives (*int32, *float64, etc.): ref flag + data (NO type info)
			// - Other types (struct, collections, etc.): ref flag + type info + data
			writeType := !isInternalTypeWithoutTypeMeta(field.Type)
			// Per xlang spec, all non-primitive fields have ref flag
			if err := field.Serializer.Write(ctx, true, writeType, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.WriteValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

// writeZeroField writes a zero value for a field that doesn't exist in the current struct
func (s *structSerializer) writeZeroField(ctx *WriteContext, field *FieldInfo) error {
	zeroValue := reflect.Zero(field.Type)
	if field.Serializer != nil {
		return field.Serializer.Write(ctx, field.Referencable, false, zeroValue)
	}
	return ctx.WriteValue(zeroValue)
}

// writeFieldsInTypeDefOrder writes fields in the order specified by fieldDefs from TypeDef
// This is used in compatible mode when we have TypeDef but s.fields might be in different order
func (s *structSerializer) writeFieldsInTypeDefOrder(ctx *WriteContext, value reflect.Value, fieldDefs []FieldDef) error {
	buf := ctx.Buffer()
	canUseUnsafe := value.CanAddr()
	var ptr unsafe.Pointer
	if canUseUnsafe {
		ptr = unsafe.Pointer(value.UnsafeAddr())
	}

	// Build field map for quick lookup
	type_ := value.Type()
	fieldMap := make(map[string]struct {
		index  int
		offset uintptr
		typ    reflect.Type
	})
	for i := 0; i < type_.NumField(); i++ {
		f := type_.Field(i)
		name := SnakeCase(f.Name)
		fieldMap[name] = struct {
			index  int
			offset uintptr
			typ    reflect.Type
		}{i, f.Offset, f.Type}
	}

	for _, def := range fieldDefs {
		fieldInfo, exists := fieldMap[def.name]
		if !exists {
			// Field doesn't exist in Go struct, write zero value
			fieldType, _ := def.fieldType.getTypeInfoWithResolver(ctx.TypeResolver())
			if fieldType.Type == nil {
				fieldType.Type = reflect.TypeOf((*interface{})(nil)).Elem()
			}
			zeroValue := reflect.Zero(fieldType.Type)
			ser, _ := ctx.TypeResolver().getSerializerByType(fieldType.Type, true)
			nullable := fieldNeedWriteRef(def.fieldType.TypeId(), def.nullable)
			writeType := !isInternalTypeWithoutTypeMeta(fieldType.Type)
			if ser != nil {
				if err := ser.Write(ctx, nullable, writeType, zeroValue); err != nil {
					return err
				}
			} else {
				if err := ctx.WriteValue(zeroValue); err != nil {
					return err
				}
			}
			continue
		}

		// Get serializer for this field
		fieldType := fieldInfo.typ
		ser, _ := ctx.TypeResolver().getSerializerByType(fieldType, true)
		staticId := GetStaticTypeId(fieldType)
		referencable := isReferencable(fieldType)

		// Check if field has a non-primitive serializer (like ENUM or NAMED_ENUM)
		// ENUM uses unsigned Varuint32Small7 for ordinals, not signed zigzag varint
		hasNonPrimitiveSer := ser != nil && (ser.TypeId() == ENUM || ser.TypeId() == NAMED_ENUM ||
			ser.TypeId() == NAMED_STRUCT || ser.TypeId() == NAMED_COMPATIBLE_STRUCT || ser.TypeId() == NAMED_EXT)

		// Fast path for fixed-size primitive types (no ref flag)
		if canUseUnsafe && isFixedSizePrimitive(staticId, referencable) && !hasNonPrimitiveSer {
			fieldPtr := unsafe.Add(ptr, fieldInfo.offset)
			switch staticId {
			case ConcreteTypeBool:
				buf.WriteBool(*(*bool)(fieldPtr))
			case ConcreteTypeInt8:
				buf.WriteByte_(*(*byte)(fieldPtr))
			case ConcreteTypeInt16:
				buf.WriteInt16(*(*int16)(fieldPtr))
			case ConcreteTypeFloat32:
				buf.WriteFloat32(*(*float32)(fieldPtr))
			case ConcreteTypeFloat64:
				buf.WriteFloat64(*(*float64)(fieldPtr))
			}
			continue
		}

		// Fast path for varint primitive types (no ref flag)
		if canUseUnsafe && isVarintPrimitive(staticId, referencable) && !hasNonPrimitiveSer {
			fieldPtr := unsafe.Add(ptr, fieldInfo.offset)
			switch staticId {
			case ConcreteTypeInt32:
				buf.WriteVarint32(*(*int32)(fieldPtr))
			case ConcreteTypeInt64:
				buf.WriteVarint64(*(*int64)(fieldPtr))
			case ConcreteTypeInt:
				buf.WriteVarint64(int64(*(*int)(fieldPtr)))
			}
			continue
		}

		// Get field value for slow paths
		fieldValue := value.Field(fieldInfo.index)

		// Special handling for enum fields:
		// Java always writes null flag + ordinal for enum fields (both compatible and non-compatible mode)
		// Java writes enum ordinals as unsigned Varuint32Small7, not signed zigzag
		if ser != nil {
			serTypeId := ser.TypeId()
			if serTypeId == ENUM || serTypeId == NAMED_ENUM {
				// Handle pointer enum fields
				if fieldValue.Kind() == reflect.Ptr {
					if fieldValue.IsNil() {
						buf.WriteInt8(NullFlag)
						continue
					}
					buf.WriteInt8(NotNullValueFlag)
					// For pointer enum fields, the serializer is ptrToValueSerializer wrapping enumSerializer.
					// We need to call the inner enumSerializer directly with the dereferenced value.
					if ptrSer, ok := ser.(*ptrToValueSerializer); ok {
						if err := ptrSer.valueSerializer.WriteData(ctx, fieldValue.Elem()); err != nil {
							return err
						}
					} else {
						if err := ser.WriteData(ctx, fieldValue.Elem()); err != nil {
							return err
						}
					}
				} else {
					// Java always writes null flag for enum fields in struct
					buf.WriteInt8(NotNullValueFlag)
					if err := ser.WriteData(ctx, fieldValue); err != nil {
						return err
					}
				}
				continue
			}
		}

		// Slow path: use serializer
		if ser != nil {
			// Per xlang spec:
			// - Non-nullable primitives (int32, float64, etc.): NO ref flag, NO type info
			// - Nullable primitives (*int32, *float64, etc.): ref flag + data (NO type info)
			// - Other types (struct, collections, etc.): ref flag + type info + data
			isPrimitive := (isFixedSizePrimitive(staticId, false) || isVarintPrimitive(staticId, false)) && !hasNonPrimitiveSer
			writeRef := !isPrimitive || referencable                               // referencable means it's a pointer type
			writeType := !isPrimitive && !isInternalTypeWithoutTypeMeta(fieldType) // primitives never need type info
			if err := ser.Write(ctx, writeRef, writeType, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.WriteValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

func (s *structSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.Kind() == reflect.Ptr && value.IsNil() {
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
		// Structs have dynamic type IDs, need to look up from TypeResolver
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

func (s *structSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	if value.Kind() == reflect.Ptr {
		if value.IsNil() {
			value.Set(reflect.New(type_.Elem()))
		}
		value = value.Elem()
		type_ = type_.Elem()
	}

	if s.fields == nil {
		if s.type_ == nil {
			s.type_ = type_
		}
		// Ensure s.type_ is the struct type, not a pointer type
		for s.type_.Kind() == reflect.Ptr {
			s.type_ = s.type_.Elem()
		}
		// If we have fieldDefs from TypeDef (compatible mode), use them
		// Otherwise initialize from local type structure
		if s.fieldDefs != nil {
			if err := s.initFieldsFromDefsWithResolver(ctx.TypeResolver()); err != nil {
				return err
			}
		} else {
			if err := s.initFieldsFromContext(ctx); err != nil {
				return err
			}
		}
	}
	if s.structHash == 0 {
		s.structHash = s.computeHash()
	}

	// In compatible mode with meta share, struct hash is not written
	// because type meta is written separately
	if !ctx.Compatible() {
		structHash := buf.ReadInt32()
		if structHash != s.structHash {
			return fmt.Errorf("hash %d is not consistent with %d for type %s",
				structHash, s.structHash, s.type_)
		}
	}

	// In compatible mode with fieldDefs, read fields in order (not grouped)
	// because Java writes fields in TypeDef order, not grouped by type
	if s.fieldDefs != nil && ctx.Compatible() {
		return s.readFieldsInOrder(ctx, value)
	}

	// Get base pointer for unsafe access
	ptr := unsafe.Pointer(value.UnsafeAddr())

	// Phase 1: Read fixed-size primitive fields (no ref flag)
	for _, field := range s.fixedFields {
		if field.FieldIndex < 0 {
			if err := s.skipField(ctx, field); err != nil {
				return err
			}
			continue
		}
		fieldPtr := unsafe.Add(ptr, field.Offset)
		switch field.StaticId {
		case ConcreteTypeBool:
			*(*bool)(fieldPtr) = buf.ReadBool()
		case ConcreteTypeInt8:
			*(*int8)(fieldPtr) = int8(buf.ReadByte_())
		case ConcreteTypeInt16:
			*(*int16)(fieldPtr) = buf.ReadInt16()
		case ConcreteTypeFloat32:
			*(*float32)(fieldPtr) = buf.ReadFloat32()
		case ConcreteTypeFloat64:
			*(*float64)(fieldPtr) = buf.ReadFloat64()
		}
	}

	// Phase 2: Read varint primitive fields (no ref flag)
	for _, field := range s.varintFields {
		if field.FieldIndex < 0 {
			if err := s.skipField(ctx, field); err != nil {
				return err
			}
			continue
		}
		fieldPtr := unsafe.Add(ptr, field.Offset)
		switch field.StaticId {
		case ConcreteTypeInt32:
			*(*int32)(fieldPtr) = buf.ReadVarint32()
		case ConcreteTypeInt64:
			*(*int64)(fieldPtr) = buf.ReadVarint64()
		case ConcreteTypeInt:
			*(*int)(fieldPtr) = int(buf.ReadVarint64())
		}
	}

	// Phase 3: Read remaining fields (all non-primitives have ref flag per xlang spec)
	for _, field := range s.remainingFields {
		if field.FieldIndex < 0 {
			if err := s.skipField(ctx, field); err != nil {
				return err
			}
			continue
		}
		fieldValue := value.Field(field.FieldIndex)

		// Special handling for enum fields:
		// Java always writes null flag + ordinal for enum fields (both compatible and non-compatible mode)
		// Java writes enum ordinals as unsigned Varuint32Small7, not signed zigzag
		if field.Serializer != nil {
			serTypeId := field.Serializer.TypeId()
			if serTypeId == ENUM || serTypeId == NAMED_ENUM {
				// Java always writes null flag for enum fields in struct
				nullFlag := buf.ReadInt8()
				if nullFlag == NullFlag {
					// For pointer enum fields, leave as nil; for non-pointer, set to zero
					if fieldValue.Kind() != reflect.Ptr {
						fieldValue.SetInt(0)
					}
					continue
				}
				// For pointer enum fields, allocate a new value
				targetValue := fieldValue
				if fieldValue.Kind() == reflect.Ptr {
					newVal := reflect.New(field.Type.Elem())
					fieldValue.Set(newVal)
					targetValue = newVal.Elem()
				}
				// For pointer enum fields, the serializer is ptrToValueSerializer wrapping enumSerializer.
				// We need to call the inner enumSerializer directly with the dereferenced value.
				if ptrSer, ok := field.Serializer.(*ptrToValueSerializer); ok {
					if err := ptrSer.valueSerializer.ReadData(ctx, field.Type.Elem(), targetValue); err != nil {
						return err
					}
				} else {
					if err := field.Serializer.ReadData(ctx, field.Type, targetValue); err != nil {
						return err
					}
				}
				continue
			}
		}

		if field.Serializer != nil {
			// For nested struct fields in compatible mode, read type info
			readType := ctx.Compatible() && isStructField(field.Type)
			// Per xlang spec, all non-primitive fields have ref flag
			if err := field.Serializer.Read(ctx, true, readType, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.ReadValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

// readFieldsInOrder reads fields in the order they appear in s.fields (TypeDef order)
// This is used in compatible mode where Java writes fields in TypeDef order
func (s *structSerializer) readFieldsInOrder(ctx *ReadContext, value reflect.Value) error {
	buf := ctx.Buffer()
	ptr := unsafe.Pointer(value.UnsafeAddr())

	for _, field := range s.fields {
		if field.FieldIndex < 0 {
			if err := s.skipField(ctx, field); err != nil {
				return err
			}
			continue
		}

		fieldPtr := unsafe.Add(ptr, field.Offset)

		// Fast path for fixed-size primitive types (no ref flag)
		if isFixedSizePrimitive(field.StaticId, field.Referencable) {
			switch field.StaticId {
			case ConcreteTypeBool:
				*(*bool)(fieldPtr) = buf.ReadBool()
			case ConcreteTypeInt8:
				*(*int8)(fieldPtr) = int8(buf.ReadByte_())
			case ConcreteTypeInt16:
				*(*int16)(fieldPtr) = buf.ReadInt16()
			case ConcreteTypeFloat32:
				*(*float32)(fieldPtr) = buf.ReadFloat32()
			case ConcreteTypeFloat64:
				*(*float64)(fieldPtr) = buf.ReadFloat64()
			}
			continue
		}

		// Fast path for varint primitive types (no ref flag)
		// Skip fast path if field has a serializer with a non-primitive type (e.g., NAMED_ENUM)
		if isVarintPrimitive(field.StaticId, field.Referencable) && !fieldHasNonPrimitiveSerializer(field) {
			switch field.StaticId {
			case ConcreteTypeInt32:
				*(*int32)(fieldPtr) = buf.ReadVarint32()
			case ConcreteTypeInt64:
				*(*int64)(fieldPtr) = buf.ReadVarint64()
			case ConcreteTypeInt:
				*(*int)(fieldPtr) = int(buf.ReadVarint64())
			}
			continue
		}

		// Get field value for slow paths
		fieldValue := value.Field(field.FieldIndex)

		// Special handling for enum fields:
		// Java always writes null flag + ordinal for enum fields (both compatible and non-compatible mode)
		// Java writes enum ordinals as unsigned Varuint32Small7, not signed zigzag
		if field.Serializer != nil {
			serTypeId := field.Serializer.TypeId()
			if serTypeId == ENUM || serTypeId == NAMED_ENUM {
				// Java always writes null flag for enum fields in struct
				nullFlag := buf.ReadInt8()
				if nullFlag == NullFlag {
					// For pointer enum fields, leave as nil; for non-pointer, set to zero
					if fieldValue.Kind() != reflect.Ptr {
						fieldValue.SetInt(0)
					}
					continue
				}
				// For pointer enum fields, allocate a new value
				targetValue := fieldValue
				if fieldValue.Kind() == reflect.Ptr {
					newVal := reflect.New(field.Type.Elem())
					fieldValue.Set(newVal)
					targetValue = newVal.Elem()
				}
				// For pointer enum fields, the serializer is ptrToValueSerializer wrapping enumSerializer.
				// We need to call the inner enumSerializer directly with the dereferenced value.
				if ptrSer, ok := field.Serializer.(*ptrToValueSerializer); ok {
					if err := ptrSer.valueSerializer.ReadData(ctx, field.Type.Elem(), targetValue); err != nil {
						return err
					}
				} else {
					if err := field.Serializer.ReadData(ctx, field.Type, targetValue); err != nil {
						return err
					}
				}
				continue
			}
		}

		// Slow path for non-primitives (all need ref flag per xlang spec)
		if field.Serializer != nil {
			// Per xlang spec:
			// - Nullable primitives (*int32, *float64, etc.): ref flag + data (NO type info)
			// - Other types (struct, collections, etc.): ref flag + type info + data
			readType := !isInternalTypeWithoutTypeMeta(field.Type)
			// Per xlang spec, all non-primitive fields have ref flag
			if err := field.Serializer.Read(ctx, true, readType, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.ReadValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

// skipField skips a field that doesn't exist or is incompatible
func (s *structSerializer) skipField(ctx *ReadContext, field *FieldInfo) error {
	if field.FieldDef.name != "" {
		fieldDefIsStructType := isStructFieldType(field.FieldDef.fieldType)
		return SkipFieldValueWithTypeFlag(ctx, field.FieldDef, field.Referencable, ctx.Compatible() && fieldDefIsStructType)
	}
	// No FieldDef available, read into temp value
	tempValue := reflect.New(field.Type).Elem()
	if field.Serializer != nil {
		readType := ctx.Compatible() && isStructField(field.Type)
		return field.Serializer.Read(ctx, field.Referencable, readType, tempValue)
	}
	return ctx.ReadValue(tempValue)
}

func (s *structSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
		// Read type info - in compatible mode this returns the serializer with remote fieldDefs
		typeID := buf.ReadVaruint32Small7()
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			// For struct types in compatible mode, use the serializer from TypeInfo
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			if err != nil {
				return err
			}
			// Use the serializer from TypeInfo which has the remote field definitions
			if structSer, ok := typeInfo.Serializer.(*structSerializer); ok && len(structSer.fieldDefs) > 0 {
				return structSer.ReadData(ctx, value.Type(), value)
			}
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *structSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

// ReadCompatible reads struct data with schema evolution support
// It reads fields based on remote schema and maps to local fields by name
func (s *structSerializer) ReadCompatible(ctx *ReadContext, type_ reflect.Type, value reflect.Value, remoteFields []*FieldInfo) error {
	buf := ctx.Buffer()
	if value.Kind() == reflect.Ptr {
		if value.IsNil() {
			value.Set(reflect.New(type_.Elem()))
		}
		value = value.Elem()
	}

	if s.fieldMap == nil {
		s.fieldMap = make(map[string]*FieldInfo)
		for _, field := range s.fields {
			s.fieldMap[field.Name] = field
		}
	}

	ptr := unsafe.Pointer(value.UnsafeAddr())

	for _, remoteField := range remoteFields {
		localField, exists := s.fieldMap[remoteField.Name]

		if !exists {
			// Field doesn't exist locally, discard
			tempValue := reflect.New(remoteField.Type).Elem()
			if remoteField.Serializer != nil {
				if err := remoteField.Serializer.Read(ctx, remoteField.Referencable, false, tempValue); err != nil {
					return err
				}
			} else {
				if err := ctx.ReadValue(tempValue); err != nil {
					return err
				}
			}
			continue
		}

		fieldPtr := unsafe.Add(ptr, localField.Offset)

		// Fast path for fixed-size primitive types (no ref flag)
		if isFixedSizePrimitive(localField.StaticId, localField.Referencable) {
			switch localField.StaticId {
			case ConcreteTypeBool:
				*(*bool)(fieldPtr) = buf.ReadBool()
			case ConcreteTypeInt8:
				*(*int8)(fieldPtr) = int8(buf.ReadByte_())
			case ConcreteTypeInt16:
				*(*int16)(fieldPtr) = buf.ReadInt16()
			case ConcreteTypeFloat32:
				*(*float32)(fieldPtr) = buf.ReadFloat32()
			case ConcreteTypeFloat64:
				*(*float64)(fieldPtr) = buf.ReadFloat64()
			}
			continue
		}

		// Fast path for varint primitive types (no ref flag)
		if isVarintPrimitive(localField.StaticId, localField.Referencable) {
			switch localField.StaticId {
			case ConcreteTypeInt32:
				*(*int32)(fieldPtr) = buf.ReadVarint32()
			case ConcreteTypeInt64:
				*(*int64)(fieldPtr) = buf.ReadVarint64()
			case ConcreteTypeInt:
				*(*int)(fieldPtr) = int(buf.ReadVarint64())
			}
			continue
		}

		// Slow path for non-primitives (all need ref flag per xlang spec)
		fieldValue := value.Field(localField.FieldIndex)
		if localField.Serializer != nil {
			// Per xlang spec, all non-primitive fields have ref flag
			if err := localField.Serializer.Read(ctx, true, false, fieldValue); err != nil {
				return err
			}
		} else {
			if err := ctx.ReadValue(fieldValue); err != nil {
				return err
			}
		}
	}
	return nil
}

// initFieldsFromContext initializes fields using context's type resolver (for WriteContext)
func (s *structSerializer) initFieldsFromContext(ctx interface{ TypeResolver() *TypeResolver }) error {
	typeResolver := ctx.TypeResolver()

	// If we have fieldDefs from type_def (remote meta), use them
	if len(s.fieldDefs) > 0 {
		return s.initFieldsFromDefsWithResolver(typeResolver)
	}

	// Otherwise initialize from local struct type
	type_ := s.type_
	var fields []*FieldInfo
	var fieldNames []string
	var serializers []Serializer

	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		firstRune, _ := utf8.DecodeRuneInString(field.Name)
		if unicode.IsLower(firstRune) {
			continue // skip unexported fields
		}

		originalFieldType := field.Type
		fieldType := field.Type

		var fieldSerializer Serializer
		// For interface{} fields, don't get a serializer - use WriteValue/ReadValue instead
		// which will handle polymorphic types dynamically
		if fieldType.Kind() != reflect.Interface {
			// Get serializer for all non-interface field types
			fieldSerializer, _ = typeResolver.getSerializerByType(fieldType, true)
		}

		if fieldType.Kind() == reflect.Array && fieldType.Elem().Kind() != reflect.Interface {
			// For fixed-size arrays with primitive elements, use primitive array serializers
			// to match cross-language format (Python int8_array, int16_array, etc.)
			elemType := fieldType.Elem()
			switch elemType.Kind() {
			case reflect.Int8:
				fieldSerializer = int8ArraySerializer{}
			case reflect.Int16:
				fieldSerializer = int16ArraySerializer{}
			case reflect.Int32:
				fieldSerializer = int32ArraySerializer{}
			case reflect.Int64:
				fieldSerializer = int64ArraySerializer{}
			case reflect.Float32:
				fieldSerializer = float32ArraySerializer{}
			case reflect.Float64:
				fieldSerializer = float64ArraySerializer{}
			default:
				// For non-primitive arrays, use sliceDynSerializer
				fieldSerializer = sliceDynSerializer{
					elemInfo:     typeResolver.typesInfo[elemType],
					declaredType: elemType,
				}
			}
		} else if fieldType.Kind() == reflect.Slice && fieldType.Elem().Kind() != reflect.Interface {
			// For struct fields, always use the generic sliceDynSerializer for cross-language compatibility
			// The generic sliceDynSerializer uses collection flags and element type ID format
			// which matches the codegen format
			fieldSerializer = sliceDynSerializer{
				elemInfo:     typeResolver.typesInfo[fieldType.Elem()],
				declaredType: fieldType.Elem(),
			}
		}

		fieldInfo := &FieldInfo{
			Name:         SnakeCase(field.Name),
			Offset:       field.Offset,
			Type:         fieldType,
			StaticId:     GetStaticTypeId(fieldType),
			Serializer:   fieldSerializer,
			Referencable: isReferencable(originalFieldType), // Use isReferencable instead of nullable
			FieldIndex:   i,
		}
		fields = append(fields, fieldInfo)
		fieldNames = append(fieldNames, fieldInfo.Name)
		serializers = append(serializers, fieldSerializer)
	}

	// Sort fields according to specification
	serializers, fieldNames = sortFields(typeResolver, fieldNames, serializers)
	order := make(map[string]int, len(fieldNames))
	for idx, name := range fieldNames {
		order[name] = idx
	}

	sort.SliceStable(fields, func(i, j int) bool {
		oi, okI := order[fields[i].Name]
		oj, okJ := order[fields[j].Name]
		switch {
		case okI && okJ:
			return oi < oj
		case okI:
			return true
		case okJ:
			return false
		default:
			return false
		}
	})

	s.fields = fields
	s.groupFields()
	return nil
}

// groupFields categorizes fields into fixedFields, varintFields, and remainingFields
func (s *structSerializer) groupFields() {
	s.fixedFields = nil
	s.varintFields = nil
	s.remainingFields = nil

	for _, field := range s.fields {
		// Fields with non-primitive serializers (NAMED_ENUM, NAMED_STRUCT, etc.)
		// must go to remainingFields to use their serializer's type info writing
		if fieldHasNonPrimitiveSerializer(field) {
			s.remainingFields = append(s.remainingFields, field)
		} else if isFixedSizePrimitive(field.StaticId, field.Referencable) {
			s.fixedFields = append(s.fixedFields, field)
		} else if isVarintPrimitive(field.StaticId, field.Referencable) {
			s.varintFields = append(s.varintFields, field)
		} else {
			s.remainingFields = append(s.remainingFields, field)
		}
	}
}

// initFieldsFromDefsWithResolver initializes fields from remote fieldDefs using typeResolver
func (s *structSerializer) initFieldsFromDefsWithResolver(typeResolver *TypeResolver) error {
	type_ := s.type_
	if type_ == nil {
		// Type is not known - we'll create an interface{} placeholder
		// This happens when deserializing unknown types in compatible mode
		// For now, we'll create fields that discard all data
		var fields []*FieldInfo
		for _, def := range s.fieldDefs {
			fieldSerializer, _ := getFieldTypeSerializerWithResolver(typeResolver, def.fieldType)
			remoteTypeInfo, _ := def.fieldType.getTypeInfoWithResolver(typeResolver)
			remoteType := remoteTypeInfo.Type
			if remoteType == nil {
				remoteType = reflect.TypeOf((*interface{})(nil)).Elem()
			}
			fieldInfo := &FieldInfo{
				Name:         def.name,
				Offset:       0,
				Type:         remoteType,
				StaticId:     GetStaticTypeId(remoteType),
				Serializer:   fieldSerializer,
				Referencable: fieldNeedWriteRef(def.fieldType.TypeId(), def.nullable), // Use remote nullable flag
				FieldIndex:   -1,                                                      // Mark as non-existent field to discard data
				FieldDef:     def,                                                     // Save original FieldDef for skipping
			}
			fields = append(fields, fieldInfo)
		}
		s.fields = fields
		s.groupFields()
		return nil
	}

	// Build map from field names to struct field indices
	fieldNameToIndex := make(map[string]int)
	fieldNameToOffset := make(map[string]uintptr)
	fieldNameToType := make(map[string]reflect.Type)
	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		name := SnakeCase(field.Name)
		fieldNameToIndex[name] = i
		fieldNameToOffset[name] = field.Offset
		fieldNameToType[name] = field.Type
	}

	var fields []*FieldInfo

	for _, def := range s.fieldDefs {
		fieldSerializer, err := getFieldTypeSerializerWithResolver(typeResolver, def.fieldType)
		if err != nil || fieldSerializer == nil {
			// If we can't get serializer from typeID, try to get it from the Go type
			// This can happen when the type isn't registered in typeIDToTypeInfo
			remoteTypeInfo, _ := def.fieldType.getTypeInfoWithResolver(typeResolver)
			if remoteTypeInfo.Type != nil {
				fieldSerializer, _ = typeResolver.getSerializerByType(remoteTypeInfo.Type, true)
			}
		}

		// Get the remote type from fieldDef
		remoteTypeInfo, _ := def.fieldType.getTypeInfoWithResolver(typeResolver)
		remoteType := remoteTypeInfo.Type
		// Track if type lookup failed - we'll need to skip such fields
		// Note: DynamicFieldType.getTypeInfoWithResolver returns interface{} (not nil) when lookup fails
		emptyInterfaceType := reflect.TypeOf((*interface{})(nil)).Elem()
		typeLookupFailed := remoteType == nil || remoteType == emptyInterfaceType
		if remoteType == nil {
			remoteType = emptyInterfaceType
		}

		// For struct-like fields, even if TypeDef lookup fails, we can try to read
		// the field because type resolution happens at read time from the buffer.
		// The type name might map to a different local type.
		isStructLikeField := isStructFieldType(def.fieldType)

		// Try to find corresponding local field
		fieldIndex := -1
		var offset uintptr
		var fieldType reflect.Type

		if idx, exists := fieldNameToIndex[def.name]; exists {
			localType := fieldNameToType[def.name]
			// Check if types are compatible
			// For primitive types: skip if types don't match
			// For struct-like types: allow read even if TypeDef lookup failed,
			// because runtime type resolution by name might work
			shouldRead := false
			isPolymorphicField := def.fieldType.TypeId() == UNKNOWN
			defTypeId := def.fieldType.TypeId()
			// Check if field is an enum - either by type ID or by serializer type
			// The type ID may be a composite value with namespace bits, so check the low 8 bits
			internalDefTypeId := defTypeId & 0xFF
			isEnumField := internalDefTypeId == NAMED_ENUM || internalDefTypeId == ENUM
			if !isEnumField && fieldSerializer != nil {
				_, isEnumField = fieldSerializer.(*enumSerializer)
			}
			if isPolymorphicField && localType.Kind() == reflect.Interface {
				// For polymorphic (UNKNOWN) fields with interface{} local type,
				// allow reading - the actual type will be determined at runtime
				shouldRead = true
				fieldType = localType
			} else if typeLookupFailed && isEnumField {
				// For enum fields with failed TypeDef lookup (NAMED_ENUM stores by namespace/typename, not typeId),
				// check if local field is a numeric type (Go enums are int-based)
				// Also handle pointer enum fields (*EnumType)
				localKind := localType.Kind()
				elemKind := localKind
				if localKind == reflect.Ptr {
					elemKind = localType.Elem().Kind()
				}
				if isNumericKind(elemKind) {
					shouldRead = true
					fieldType = localType
					// Get the serializer for the base type (the enum type, not the pointer)
					baseType := localType
					if localKind == reflect.Ptr {
						baseType = localType.Elem()
					}
					fieldSerializer, _ = typeResolver.getSerializerByType(baseType, true)
				}
			} else if typeLookupFailed && isStructLikeField {
				// For struct fields with failed TypeDef lookup, check if local field can hold a struct
				localKind := localType.Kind()
				if localKind == reflect.Ptr {
					localKind = localType.Elem().Kind()
				}
				if localKind == reflect.Struct || localKind == reflect.Interface {
					shouldRead = true
					fieldType = localType // Use local type for struct fields
				}
			} else if !typeLookupFailed && typesCompatible(localType, remoteType) {
				shouldRead = true
				fieldType = localType
			}

			if shouldRead {
				fieldIndex = idx
				offset = fieldNameToOffset[def.name]
				// For struct-like fields with failed type lookup, get the serializer for the local type
				if typeLookupFailed && isStructLikeField && fieldSerializer == nil {
					fieldSerializer, _ = typeResolver.getSerializerByType(localType, true)
				}
				// If local type is *T and remote type is T, we need the serializer for *T
				// This handles Java's Integer/Long (nullable boxed types) mapping to Go's *int32/*int64
				if localType.Kind() == reflect.Ptr && localType.Elem() == remoteType {
					fieldSerializer, _ = typeResolver.getSerializerByType(localType, true)
				}
				// For pointer enum fields (*EnumType), get the serializer for the base enum type
				// The struct read/write code will handle pointer dereferencing
				if isEnumField && localType.Kind() == reflect.Ptr {
					baseType := localType.Elem()
					fieldSerializer, _ = typeResolver.getSerializerByType(baseType, true)
					if DebugOutputEnabled() {
						fmt.Printf("[fory-debug] pointer enum field %s: localType=%v baseType=%v serializer=%T\n",
							def.name, localType, baseType, fieldSerializer)
					}
				}
			} else {
				// Types are incompatible or unknown - use remote type but mark field as not settable
				fieldType = remoteType
				fieldIndex = -1
				offset = 0 // Don't set offset for incompatible fields
			}
		} else {
			// Field doesn't exist locally, use type from fieldDef
			fieldType = remoteType
		}

		fieldInfo := &FieldInfo{
			Name:         def.name,
			Offset:       offset,
			Type:         fieldType,
			StaticId:     GetStaticTypeId(fieldType),
			Serializer:   fieldSerializer,
			Referencable: fieldNeedWriteRef(def.fieldType.TypeId(), def.nullable), // Use remote nullable flag
			FieldIndex:   fieldIndex,
			FieldDef:     def, // Save original FieldDef for skipping
		}
		fields = append(fields, fieldInfo)
	}

	s.fields = fields
	s.groupFields()
	return nil
}

// toSnakeCase converts CamelCase to snake_case
func toSnakeCase(s string) string {
	var result []rune
	for i, r := range s {
		if i > 0 && unicode.IsUpper(r) {
			result = append(result, '_')
		}
		result = append(result, unicode.ToLower(r))
	}
	return string(result)
}

// isNonNullablePrimitiveKind returns true for Go kinds that map to Java primitive types
// These are the types that cannot be null in Java and should have nullable=0 in hash computation
func isNonNullablePrimitiveKind(kind reflect.Kind) bool {
	switch kind {
	case reflect.Bool, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
		reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64,
		reflect.Float32, reflect.Float64, reflect.Int, reflect.Uint:
		return true
	default:
		return false
	}
}

// isNumericKind returns true for numeric types (Go enums are typically int-based)
func isNumericKind(kind reflect.Kind) bool {
	switch kind {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
		reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		return true
	default:
		return false
	}
}

// isReferencable determines if a type needs reference tracking based on Go type semantics
func isReferencable(t reflect.Type) bool {
	// Pointers, maps, slices, and interfaces need reference tracking
	kind := t.Kind()
	switch kind {
	case reflect.Ptr, reflect.Map, reflect.Slice, reflect.Interface:
		return true
	default:
		return false
	}
}

// isInternalTypeWithoutTypeMeta checks if a type is serialized without type meta per xlang spec.
// Per the spec (struct field serialization), these types use format: | ref/null flag | value data | (NO type meta)
// - Nullable primitives (*int32, *float64, etc.): | null flag | field value |
// - Strings (string): | null flag | value data |
// - Binary ([]byte): | null flag | value data |
// - List/Slice: | ref meta | value data |
// - Set: | ref meta | value data |
// - Map: | ref meta | value data |
// Only struct/enum/ext types need type meta: | ref flag | type meta | value data |
func isInternalTypeWithoutTypeMeta(t reflect.Type) bool {
	kind := t.Kind()
	// String type - no type meta needed
	if kind == reflect.String {
		return true
	}
	// Slice (list or byte slice) - no type meta needed
	if kind == reflect.Slice {
		return true
	}
	// Map type - no type meta needed
	if kind == reflect.Map {
		return true
	}
	// Pointer to primitive - no type meta needed
	if kind == reflect.Ptr {
		elemKind := t.Elem().Kind()
		switch elemKind {
		case reflect.Bool, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64,
			reflect.Int, reflect.Float32, reflect.Float64, reflect.String:
			return true
		}
	}
	return false
}

// isStructField checks if a type is a struct type (directly or via pointer)
func isStructField(t reflect.Type) bool {
	if t.Kind() == reflect.Struct {
		return true
	}
	if t.Kind() == reflect.Ptr && t.Elem().Kind() == reflect.Struct {
		return true
	}
	return false
}

// isStructFieldType checks if a FieldType represents a type that needs type info written
// This is used to determine if type info was written for the field in compatible mode
// In compatible mode, Java writes type info for struct and ext types, but NOT for enum types
// Enum fields only have null flag + ordinal, no type ID
func isStructFieldType(ft FieldType) bool {
	if ft == nil {
		return false
	}
	typeId := ft.TypeId()
	// Check base type IDs that need type info (struct and ext, NOT enum)
	switch typeId {
	case STRUCT, NAMED_STRUCT, COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT,
		EXT, NAMED_EXT:
		return true
	}
	// Check for composite type IDs (customId << 8 | baseType)
	if typeId > 255 {
		baseType := typeId & 0xff
		switch TypeId(baseType) {
		case STRUCT, NAMED_STRUCT, COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT,
			EXT, NAMED_EXT:
			return true
		}
	}
	return false
}

func (s *structSerializer) computeHash() int32 {
	var sb strings.Builder

	for _, field := range s.fields {
		sb.WriteString(toSnakeCase(field.Name))
		sb.WriteString(",")

		var typeId TypeId
		isEnumField := false
		if field.Serializer == nil {
			typeId = UNKNOWN
		} else {
			typeId = field.Serializer.TypeId()
			// Check if this is an enum serializer (directly or wrapped in ptrToValueSerializer)
			if _, ok := field.Serializer.(*enumSerializer); ok {
				isEnumField = true
				// Java uses UNKNOWN (0) for enum types in fingerprint computation
				typeId = UNKNOWN
			} else if ptrSer, ok := field.Serializer.(*ptrToValueSerializer); ok {
				if _, ok := ptrSer.valueSerializer.(*enumSerializer); ok {
					isEnumField = true
					// Java uses UNKNOWN (0) for enum types in fingerprint computation
					typeId = UNKNOWN
				}
			}
			// For fixed-size arrays with primitive elements, use primitive array type IDs
			// This matches Python's int8_array, int16_array, etc. types
			if field.Type.Kind() == reflect.Array {
				elemKind := field.Type.Elem().Kind()
				switch elemKind {
				case reflect.Int8:
					typeId = INT8_ARRAY
				case reflect.Int16:
					typeId = INT16_ARRAY
				case reflect.Int32:
					typeId = INT32_ARRAY
				case reflect.Int64:
					typeId = INT64_ARRAY
				case reflect.Float32:
					typeId = FLOAT32_ARRAY
				case reflect.Float64:
					typeId = FLOAT64_ARRAY
				default:
					typeId = LIST
				}
			} else if field.Type.Kind() == reflect.Slice {
				// Slices use LIST type ID (maps to Python List[T])
				typeId = LIST
			}
		}
		sb.WriteString(fmt.Sprintf("%d", typeId))
		sb.WriteString(",")

		// For cross-language hash compatibility, nullable=0 only for primitive non-pointer types
		// This matches Java's behavior where isPrimitive() returns true only for int, long, boolean, etc.
		// Go strings and other non-primitive types should have nullable=1
		// Enum types are always nullable (like Java enums which are objects)
		nullableFlag := "1"
		if isNonNullablePrimitiveKind(field.Type.Kind()) && !field.Referencable && !isEnumField {
			nullableFlag = "0"
		}
		sb.WriteString(nullableFlag)
		sb.WriteString(";")
	}

	hashString := sb.String()
	data := []byte(hashString)
	h1, _ := murmur3.Sum128WithSeed(data, 47)
	hash := int32(h1 & 0xFFFFFFFF)

	if DebugOutputEnabled() {
		fmt.Printf("[Go][fory-debug] struct %v version fingerprint=\"%s\" version hash=%d\n", s.type_, hashString, hash)
	}

	if hash == 0 {
		panic(fmt.Errorf("hash for type %v is 0", s.type_))
	}
	return hash
}

// ptrToStructSerializer serializes a *struct
type ptrToStructSerializer struct {
	type_            reflect.Type
	structSerializer *structSerializer
}

func (s *ptrToStructSerializer) TypeId() TypeId {
	return NAMED_STRUCT
}

func (s *ptrToStructSerializer) NeedToWriteRef() bool {
	return true
}

func (s *ptrToStructSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	elemValue := value.Elem()
	return s.structSerializer.WriteData(ctx, elemValue)
}

func (s *ptrToStructSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Check if value is already a pointer type or needs to be made into one
	if value.Kind() == reflect.Ptr {
		// Value is already a pointer (e.g., reading into an interface{})
		if value.IsNil() {
			newValue := reflect.New(type_)
			value.Set(newValue)
		}
		elem := value.Elem()
		ctx.RefResolver().Reference(value)
		return s.structSerializer.ReadData(ctx, type_, elem)
	} else {
		// Value is not a pointer - this happens when slice reader dereferences
		// Just read directly into the struct value
		return s.structSerializer.ReadData(ctx, type_, value)
	}
}

func (s *ptrToStructSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
		// Read type info - in compatible mode this returns the serializer with remote fieldDefs
		typeID := buf.ReadVaruint32Small7()
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			// For struct types in compatible mode, use the serializer from TypeInfo
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			if err != nil {
				return err
			}
			// Use the serializer from TypeInfo which has the remote field definitions
			if structSer, ok := typeInfo.Serializer.(*structSerializer); ok && len(structSer.fieldDefs) > 0 {
				return structSer.ReadData(ctx, value.Type(), value)
			}
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *ptrToStructSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

func (s *ptrToStructSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
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
		// Structs have dynamic type IDs, need to look up from TypeResolver
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

// ptrToCodegenSerializer wraps a generated serializer for pointer types
type ptrToCodegenSerializer struct {
	type_             reflect.Type
	codegenSerializer Serializer
}

func (s *ptrToCodegenSerializer) TypeId() TypeId {
	return NAMED_STRUCT
}

func (s *ptrToCodegenSerializer) NeedToWriteRef() bool {
	return true
}

func (s *ptrToCodegenSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Dereference pointer and delegate to the generated serializer
	return s.codegenSerializer.WriteData(ctx, value.Elem())
}

func (s *ptrToCodegenSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
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
		// Codegen structs have dynamic type IDs
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

func (s *ptrToCodegenSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Allocate new value if needed
	newValue := reflect.New(type_.Elem())
	value.Set(newValue)
	elem := newValue.Elem()
	ctx.RefResolver().Reference(newValue)
	return s.codegenSerializer.ReadData(ctx, type_.Elem(), elem)
}

func (s *ptrToCodegenSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
		typeID := buf.ReadVaruint32Small7()
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *ptrToCodegenSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// Field sorting helpers

type triple struct {
	typeID     int16
	serializer Serializer
	name       string
	nullable   bool
}

func sortFields(
	typeResolver *TypeResolver,
	fieldNames []string,
	serializers []Serializer,
) ([]Serializer, []string) {
	// Default nullable to false for all fields (backwards compatible)
	nullables := make([]bool, len(fieldNames))
	return sortFieldsWithNullable(typeResolver, fieldNames, serializers, nullables)
}

// sortFieldsWithNullable sorts fields with nullable information to match Java's field ordering.
// Java separates primitive types (int, long) from boxed types (Integer, Long).
// In Go, this corresponds to non-pointer primitives vs pointer-to-primitive.
func sortFieldsWithNullable(
	typeResolver *TypeResolver,
	fieldNames []string,
	serializers []Serializer,
	nullables []bool,
) ([]Serializer, []string) {
	var (
		typeTriples []triple
		others      []triple
		userDefined []triple
	)

	for i, name := range fieldNames {
		ser := serializers[i]
		if ser == nil {
			others = append(others, triple{UNKNOWN_TYPE_ID, nil, name, nullables[i]})
			continue
		}
		typeTriples = append(typeTriples, triple{ser.TypeId(), ser, name, nullables[i]})
	}
	// Java orders: primitives, boxed, finals, others, collections, maps
	// primitives = non-nullable primitive types (int, long, etc.)
	// boxed = nullable boxed types (Integer, Long, etc. which are pointers in Go)
	var primitives, boxed, collection, setFields, maps, otherInternalTypeFields []triple

	for _, t := range typeTriples {
		switch {
		case isPrimitiveType(t.typeID):
			// Separate non-nullable primitives from nullable (boxed) primitives
			if t.nullable {
				boxed = append(boxed, t)
			} else {
				primitives = append(primitives, t)
			}
		case isListType(t.typeID), isPrimitiveArrayType(t.typeID):
			collection = append(collection, t)
		case isSetType(t.typeID):
			setFields = append(setFields, t)
		case isMapType(t.typeID):
			maps = append(maps, t)
		case isUserDefinedType(t.typeID):
			userDefined = append(userDefined, t)
		case t.typeID == UNKNOWN_TYPE_ID:
			others = append(others, t)
		default:
			otherInternalTypeFields = append(otherInternalTypeFields, t)
		}
	}
	// Sort primitives (non-nullable) - same logic as boxed
	sortPrimitiveSlice := func(s []triple) {
		sort.Slice(s, func(i, j int) bool {
			ai, aj := s[i], s[j]
			compressI := ai.typeID == INT32 || ai.typeID == INT64 ||
				ai.typeID == VAR_INT32 || ai.typeID == VAR_INT64
			compressJ := aj.typeID == INT32 || aj.typeID == INT64 ||
				aj.typeID == VAR_INT32 || aj.typeID == VAR_INT64
			if compressI != compressJ {
				return !compressI && compressJ
			}
			szI, szJ := getPrimitiveTypeSize(ai.typeID), getPrimitiveTypeSize(aj.typeID)
			if szI != szJ {
				return szI > szJ
			}
			return toSnakeCase(ai.name) < toSnakeCase(aj.name)
		})
	}
	sortPrimitiveSlice(primitives)
	sortPrimitiveSlice(boxed)
	sortByTypeIDThenName := func(s []triple) {
		sort.Slice(s, func(i, j int) bool {
			if s[i].typeID != s[j].typeID {
				return s[i].typeID < s[j].typeID
			}
			return toSnakeCase(s[i].name) < toSnakeCase(s[j].name)
		})
	}
	sortTuple := func(s []triple) {
		sort.Slice(s, func(i, j int) bool {
			return toSnakeCase(s[i].name) < toSnakeCase(s[j].name)
		})
	}
	sortByTypeIDThenName(otherInternalTypeFields)
	sortTuple(others)
	sortTuple(collection)
	sortTuple(maps)
	sortTuple(userDefined)

	// Java order: primitives, boxed, finals, collections, maps, others
	// finals = String and other monomorphic types (otherInternalTypeFields)
	// others = userDefined types (structs, enums) and unknown types
	all := make([]triple, 0, len(fieldNames))
	all = append(all, primitives...)
	all = append(all, boxed...)
	all = append(all, otherInternalTypeFields...) // finals (String, etc.)
	all = append(all, collection...)
	all = append(all, setFields...)
	all = append(all, maps...)
	all = append(all, userDefined...) // others (structs, enums)
	all = append(all, others...)      // unknown types

	outSer := make([]Serializer, len(all))
	outNam := make([]string, len(all))
	for i, t := range all {
		outSer[i] = t.serializer
		outNam[i] = t.name
	}
	return outSer, outNam
}

// Legacy support types for type_def

type fieldInfo struct {
	name         string
	field        reflect.StructField
	fieldIndex   int
	type_        reflect.Type
	referencable bool
	serializer   Serializer
}

type structFieldsInfo []*fieldInfo

func (x structFieldsInfo) Len() int { return len(x) }
func (x structFieldsInfo) Less(i, j int) bool {
	return x[i].name < x[j].name
}
func (x structFieldsInfo) Swap(i, j int) { x[i], x[j] = x[j], x[i] }

// createStructFieldInfos creates legacy fieldInfo slice for compatibility
func createStructFieldInfos(f *Fory, type_ reflect.Type) (structFieldsInfo, error) {
	var fields structFieldsInfo
	serializers := make([]Serializer, 0)
	fieldnames := make([]string, 0)
	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		firstRune, _ := utf8.DecodeRuneInString(field.Name)
		if unicode.IsLower(firstRune) {
			continue
		}
		originalFieldType := field.Type
		if field.Type.Kind() == reflect.Interface {
			field.Type = reflect.ValueOf(field.Type).Elem().Type()
		}
		var fieldSerializer Serializer
		if field.Type.Kind() != reflect.Struct {
			var _ error
			fieldSerializer, _ = f.typeResolver.getSerializerByType(field.Type, true)
			if field.Type.Kind() == reflect.Array {
				elemType := field.Type.Elem()
				sliceType := reflect.SliceOf(elemType)
				fieldSerializer = f.typeResolver.typeToSerializers[sliceType]
			} else if field.Type.Kind() == reflect.Slice {
				if field.Type.Elem().Kind() != reflect.Interface {
					fieldSerializer = sliceDynSerializer{
						elemInfo: f.typeResolver.typesInfo[field.Type.Elem()],
					}
				}
			}
		}
		f := fieldInfo{
			name:         SnakeCase(field.Name),
			field:        field,
			fieldIndex:   i,
			type_:        field.Type,
			referencable: nullable(originalFieldType),
			serializer:   fieldSerializer,
		}
		fields = append(fields, &f)
		serializers = append(serializers, fieldSerializer)
		fieldnames = append(fieldnames, f.name)
	}
	sort.Sort(fields)
	fieldPairs := make([]fieldPair, len(fieldnames))
	for i := range fieldPairs {
		fieldPairs[i] = fieldPair{name: fieldnames[i], ser: serializers[i]}
	}

	sort.Slice(fieldPairs, func(i, j int) bool {
		return fieldPairs[i].name < fieldPairs[j].name
	})

	for i, p := range fieldPairs {
		fieldnames[i] = p.name
		serializers[i] = p.ser
	}
	serializers, fieldnames = sortFields(f.typeResolver, fieldnames, serializers)
	order := make(map[string]int, len(fieldnames))
	for idx, name := range fieldnames {
		order[name] = idx
	}
	sort.SliceStable(fields, func(i, j int) bool {
		oi, okI := order[fields[i].name]
		oj, okJ := order[fields[j].name]
		switch {
		case okI && okJ:
			return oi < oj
		case okI:
			return true
		case okJ:
			return false
		default:
			return false
		}
	})
	return fields, nil
}

type fieldPair struct {
	name string
	ser  Serializer
}

// createStructFieldInfosFromFieldDefs creates structFieldsInfo from fieldDefs
func createStructFieldInfosFromFieldDefs(f *Fory, fieldDefs []FieldDef, type_ reflect.Type) (structFieldsInfo, error) {
	fieldNameToIndex := make(map[string]int)

	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		fieldName := SnakeCase(field.Name)
		fieldNameToIndex[fieldName] = i
	}

	var fields structFieldsInfo

	for _, def := range fieldDefs {
		fieldTypeFromDef, err := resolveFieldDefType(f, def)
		if err != nil {
			return nil, err
		}

		fieldIndex := -1
		var fieldType reflect.Type
		var structField reflect.StructField

		if structFieldIndex, exists := fieldNameToIndex[def.name]; exists {
			structField = type_.Field(structFieldIndex)
			fieldType = fieldTypeFromDef
			if typesCompatible(structField.Type, fieldTypeFromDef) {
				fieldIndex = structFieldIndex
				fieldType = structField.Type
			} else {
				fieldType = fieldTypeFromDef
			}
		} else {
			fieldType = fieldTypeFromDef
		}

		fieldSerializer, err := getFieldTypeSerializer(f, def.fieldType)
		if err != nil {
			return nil, fmt.Errorf("failed to get serializer for field %s: %w", def.name, err)
		}

		fieldInfo := &fieldInfo{
			name:         def.name,
			field:        structField,
			fieldIndex:   fieldIndex,
			type_:        fieldType,
			referencable: def.nullable,
			serializer:   fieldSerializer,
		}

		fields = append(fields, fieldInfo)
	}

	return fields, nil
}

func resolveFieldDefType(f *Fory, def FieldDef) (reflect.Type, error) {
	typeInfo, err := def.fieldType.getTypeInfo(f)
	if err != nil {
		return nil, fmt.Errorf("unknown type for field %s with typeId %d: %w", def.name, def.fieldType.TypeId(), err)
	}
	if typeInfo.Type == nil {
		return nil, fmt.Errorf("type information missing for field %s with typeId %d", def.name, def.fieldType.TypeId())
	}
	return typeInfo.Type, nil
}

func typesCompatible(actual, expected reflect.Type) bool {
	if actual == nil || expected == nil {
		return false
	}
	if actual == expected {
		return true
	}
	// interface{} can accept any value
	if actual.Kind() == reflect.Interface && actual.NumMethod() == 0 {
		return true
	}
	if actual.AssignableTo(expected) || expected.AssignableTo(actual) {
		return true
	}
	if actual.Kind() == reflect.Ptr && actual.Elem() == expected {
		return true
	}
	if expected.Kind() == reflect.Ptr && expected.Elem() == actual {
		return true
	}
	if actual.Kind() == expected.Kind() {
		switch actual.Kind() {
		case reflect.Slice, reflect.Array:
			return elementTypesCompatible(actual.Elem(), expected.Elem())
		case reflect.Map:
			return elementTypesCompatible(actual.Key(), expected.Key()) && elementTypesCompatible(actual.Elem(), expected.Elem())
		}
	}
	if (actual.Kind() == reflect.Array && expected.Kind() == reflect.Slice) ||
		(actual.Kind() == reflect.Slice && expected.Kind() == reflect.Array) {
		return true
	}
	return false
}

func elementTypesCompatible(actual, expected reflect.Type) bool {
	if actual == nil || expected == nil {
		return false
	}
	if actual == expected || actual.AssignableTo(expected) || expected.AssignableTo(actual) {
		return true
	}
	if actual.Kind() == reflect.Ptr {
		return elementTypesCompatible(actual, expected.Elem())
	}
	return false
}

func computeStructHash(fieldsInfo structFieldsInfo, typeResolver *TypeResolver) (int32, error) {
	var sb strings.Builder

	for _, fieldInfo := range fieldsInfo {
		snakeCaseName := SnakeCase(fieldInfo.name)
		sb.WriteString(snakeCaseName)
		sb.WriteString(",")

		var typeId TypeId
		serializer := fieldInfo.serializer
		if serializer == nil {
			typeId = UNKNOWN
		} else {
			typeId = serializer.TypeId()
			// For fixed-size arrays with primitive elements, use primitive array type IDs
			// This matches Python's int8_array, int16_array, etc. types
			if fieldInfo.type_.Kind() == reflect.Array {
				elemKind := fieldInfo.type_.Elem().Kind()
				switch elemKind {
				case reflect.Int8:
					typeId = INT8_ARRAY
				case reflect.Int16:
					typeId = INT16_ARRAY
				case reflect.Int32:
					typeId = INT32_ARRAY
				case reflect.Int64:
					typeId = INT64_ARRAY
				case reflect.Float32:
					typeId = FLOAT32_ARRAY
				case reflect.Float64:
					typeId = FLOAT64_ARRAY
				default:
					typeId = LIST
				}
			} else if fieldInfo.type_.Kind() == reflect.Slice {
				// Slices use LIST type ID (maps to Python List[T])
				typeId = LIST
			}
		}
		sb.WriteString(fmt.Sprintf("%d", typeId))
		sb.WriteString(",")

		nullableFlag := "0"
		if fieldInfo.referencable {
			nullableFlag = "1"
		}
		sb.WriteString(nullableFlag)
		sb.WriteString(";")
	}

	hashString := sb.String()
	data := []byte(hashString)
	h1, _ := murmur3.Sum128WithSeed(data, 47)
	hash := int32(h1 & 0xFFFFFFFF)

	if hash == 0 {
		panic(fmt.Errorf("hash for type %v is 0", fieldsInfo))
	}
	return hash, nil
}

// skipStructSerializer is a serializer that skips unknown struct data
// It reads and discards field data based on fieldDefs from remote TypeDef
type skipStructSerializer struct {
	fieldDefs []FieldDef
}

func (s *skipStructSerializer) TypeId() TypeId {
	return NAMED_STRUCT
}

func (s *skipStructSerializer) NeedToWriteRef() bool {
	return true
}

func (s *skipStructSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	return fmt.Errorf("skipStructSerializer does not support WriteData - unknown struct type")
}

func (s *skipStructSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	return fmt.Errorf("skipStructSerializer does not support Write - unknown struct type")
}

func (s *skipStructSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Skip all fields based on fieldDefs from remote TypeDef
	for _, fieldDef := range s.fieldDefs {
		isStructType := isStructFieldType(fieldDef.fieldType)
		// Use trackingRef from FieldDef for ref flag decision
		if err := SkipFieldValueWithTypeFlag(ctx, fieldDef, fieldDef.trackingRef, ctx.Compatible() && isStructType); err != nil {
			return fmt.Errorf("failed to skip field %s: %w", fieldDef.name, err)
		}
	}
	return nil
}

func (s *skipStructSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found, nothing to skip
			return nil
		}
	}
	return s.ReadData(ctx, nil, value)
}

func (s *skipStructSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again - just skip data
	return s.Read(ctx, readRef, false, value)
}
