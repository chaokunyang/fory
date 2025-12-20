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
	TypeId       TypeId // Fory type ID for the serializer
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
	// ENUM (numeric ID), NAMED_ENUM (namespace/typename), NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, NAMED_EXT
	// all require special serialization and should not use the primitive fast path
	// Note: ENUM uses unsigned Varuint32Small7 for ordinals, not signed zigzag varint
	switch field.TypeId {
	case ENUM, NAMED_ENUM, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, NAMED_EXT:
		return true
	default:
		return false
	}
}

// isEnumField checks if a field is an enum type based on its TypeId
func isEnumField(field *FieldInfo) bool {
	if field.Serializer == nil {
		return false
	}
	internalTypeId := field.TypeId & 0xFF
	return internalTypeId == ENUM || internalTypeId == NAMED_ENUM
}

// writeEnumField writes an enum field with null flag + ordinal.
// Java always writes null flag for enum fields in struct (both compatible and non-compatible mode).
// Java writes enum ordinals as unsigned Varuint32Small7, not signed zigzag.
func writeEnumField(ctx *WriteContext, field *FieldInfo, fieldValue reflect.Value) {
	buf := ctx.Buffer()
	// Handle pointer enum fields
	if fieldValue.Kind() == reflect.Ptr {
		if fieldValue.IsNil() {
			buf.WriteInt8(NullFlag)
			return
		}
		buf.WriteInt8(NotNullValueFlag)
		// For pointer enum fields, the serializer is ptrToValueSerializer wrapping enumSerializer.
		// We need to call the inner enumSerializer directly with the dereferenced value.
		if ptrSer, ok := field.Serializer.(*ptrToValueSerializer); ok {
			ptrSer.valueSerializer.WriteData(ctx, fieldValue.Elem())
			return
		}
		field.Serializer.WriteData(ctx, fieldValue.Elem())
		return
	}
	// Non-pointer enum: always write null flag then value
	buf.WriteInt8(NotNullValueFlag)
	field.Serializer.WriteData(ctx, fieldValue)
}

// readEnumField reads an enum field with null flag + ordinal.
// Java always writes null flag for enum fields in struct.
// Uses context error state for deferred error checking.
func readEnumField(ctx *ReadContext, field *FieldInfo, fieldValue reflect.Value) {
	buf := ctx.Buffer()
	nullFlag := buf.ReadInt8(ctx.Err())
	if ctx.HasError() {
		return
	}
	if nullFlag == NullFlag {
		// For pointer enum fields, leave as nil; for non-pointer, set to zero
		if fieldValue.Kind() != reflect.Ptr {
			fieldValue.SetInt(0)
		}
		return
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
		ptrSer.valueSerializer.ReadData(ctx, field.Type.Elem(), targetValue)
	} else {
		field.Serializer.ReadData(ctx, field.Type, targetValue)
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

// newStructSerializer creates a new structSerializer with the given parameters.
// typeTag can be empty and will be derived from type_.Name() if not provided.
// fieldDefs can be nil for local structs without remote schema.
func newStructSerializer(type_ reflect.Type, typeTag string, fieldDefs []FieldDef) *structSerializer {
	if typeTag == "" && type_ != nil {
		typeTag = type_.Name()
	}
	return &structSerializer{
		type_:     type_,
		typeTag:   typeTag,
		fieldDefs: fieldDefs,
	}
}

func (s *structSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	switch refMode {
	case RefModeTracking:
		if value.Kind() == reflect.Ptr && value.IsNil() {
			ctx.buffer.WriteInt8(NullFlag)
			return
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.buffer, value)
		if err != nil {
			ctx.SetError(FromError(err))
			return
		}
		if refWritten {
			return
		}
	case RefModeNullOnly:
		if value.Kind() == reflect.Ptr && value.IsNil() {
			ctx.buffer.WriteInt8(NullFlag)
			return
		}
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// Structs have dynamic type IDs, need to look up from TypeResolver
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			ctx.SetError(FromError(err))
			return
		}
		if err := ctx.TypeResolver().WriteTypeInfo(ctx.buffer, typeInfo); err != nil {
			ctx.SetError(FromError(err))
			return
		}
	}
	s.WriteData(ctx, value)
}

func (s *structSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	// Dereference pointer if needed
	if value.Kind() == reflect.Ptr {
		if value.IsNil() {
			ctx.SetError(SerializationError("cannot write nil pointer"))
			return
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
				ctx.SetError(FromError(err))
				return
			}
		} else {
			if err := s.initFieldsFromContext(ctx); err != nil {
				ctx.SetError(FromError(err))
				return
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

	// In compatible mode, always write fields in sorted order to match TypeDef order
	// This ensures consistency with the read path which uses readFieldsInOrder
	if ctx.Compatible() {
		s.writeFieldsInOrder(ctx, value)
		return
	}

	// Non-compatible mode with fieldDefs still uses writeFieldsInOrder
	if s.fieldDefs != nil {
		s.writeFieldsInOrder(ctx, value)
		return
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

		if isEnumField(field) {
			writeEnumField(ctx, field, fieldValue)
			if ctx.HasError() {
				return
			}
			continue
		}

		if field.Serializer != nil {
			// For nested struct fields in compatible mode, write type info
			writeType := ctx.Compatible() && isStructField(field.Type)
			// Determine RefMode based on ctx.TrackRef() and field.Referencable
			// This must match the read path logic which uses FieldDef.trackingRef/nullable
			refMode := RefModeNone
			if ctx.TrackRef() && field.Referencable {
				refMode = RefModeTracking
			} else if field.Referencable {
				refMode = RefModeNullOnly
			}
			field.Serializer.Write(ctx, refMode, writeType, fieldValue)
			if ctx.HasError() {
				return
			}
		} else {
			ctx.WriteValue(fieldValue)
			if ctx.HasError() {
				return
			}
		}
	}
}

// writeFieldsInOrder writes fields in the order they appear in s.fields (TypeDef order)
// This is used in compatible mode where Java expects fields in TypeDef order
func (s *structSerializer) writeFieldsInOrder(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	canUseUnsafe := value.CanAddr()
	var ptr unsafe.Pointer
	if canUseUnsafe {
		ptr = unsafe.Pointer(value.UnsafeAddr())
	}

	for _, field := range s.fields {
		if field.FieldIndex < 0 {
			s.writeZeroField(ctx, field)
			if ctx.HasError() {
				return
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

		if isEnumField(field) {
			writeEnumField(ctx, field, fieldValue)
			if ctx.HasError() {
				return
			}
			continue
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
				ctx.SetError(SerializationErrorf("unhandled primitive type: %v", field.StaticId))
				return
			}
			continue
		}

		// Slow path for non-primitives (all need ref flag per xlang spec)
		if field.Serializer != nil {
			// Per xlang spec:
			// - Nullable primitives (*int32, *float64, etc.): ref flag + data (NO type info)
			// - Other types (struct, collections, etc.): ref flag + type info + data
			writeType := !isInternalTypeWithoutTypeMeta(field.Type)
			// Determine RefMode based on FieldDef's trackingRef and nullable flags
			// In compatible mode with TypeDef, we must use the flags from the TypeDef
			refMode := RefModeNone
			if field.FieldDef.trackingRef {
				refMode = RefModeTracking
			} else if field.FieldDef.nullable || field.Referencable {
				refMode = RefModeNullOnly
			}
			field.Serializer.Write(ctx, refMode, writeType, fieldValue)
			if ctx.HasError() {
				return
			}
		} else {
			ctx.WriteValue(fieldValue)
			if ctx.HasError() {
				return
			}
		}
	}
}

// writeZeroField writes a zero value for a field that doesn't exist in the current struct
func (s *structSerializer) writeZeroField(ctx *WriteContext, field *FieldInfo) {
	zeroValue := reflect.Zero(field.Type)
	if field.Serializer != nil {
		refMode := RefModeNone
		if field.Referencable {
			refMode = RefModeTracking
		}
		field.Serializer.Write(ctx, refMode, false, zeroValue)
		return
	}
	ctx.WriteValue(zeroValue)
}

func (s *structSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	switch refMode {
	case RefModeTracking:
		refID, refErr := ctx.RefResolver().TryPreserveRefId(buf)
		if refErr != nil {
			ctx.SetError(FromError(refErr))
			return
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8(ctxErr)
		if flag == NullFlag {
			return
		}
	}
	if ctx.HasError() {
		return
	}
	if readType {
		// Read type info - in compatible mode this returns the serializer with remote fieldDefs
		typeID := buf.ReadVaruint32Small7(ctxErr)
		if ctx.HasError() {
			return
		}
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			// For struct types in compatible mode, use the serializer from TypeInfo
			typeInfo, tiErr := ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			if tiErr != nil {
				ctx.SetError(FromError(tiErr))
				return
			}
			// Use the serializer from TypeInfo which has the remote field definitions
			if structSer, ok := typeInfo.Serializer.(*structSerializer); ok && len(structSer.fieldDefs) > 0 {
				structSer.ReadData(ctx, value.Type(), value)
				return
			}
		}
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s *structSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
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
				ctx.SetError(FromError(err))
				return
			}
		} else {
			if err := s.initFieldsFromContext(ctx); err != nil {
				ctx.SetError(FromError(err))
				return
			}
		}
	}
	if s.structHash == 0 {
		s.structHash = s.computeHash()
	}

	// In compatible mode with meta share, struct hash is not written
	// because type meta is written separately
	if !ctx.Compatible() {
		err := ctx.Err()
		structHash := buf.ReadInt32(err)
		if ctx.HasError() {
			return
		}
		if structHash != s.structHash {
			ctx.SetError(HashMismatchError(structHash, s.structHash, s.type_.String()))
			return
		}
	}

	// In compatible mode with fieldDefs, read fields in TypeDef order (not grouped)
	if s.fieldDefs != nil {
		s.readFieldsInOrder(ctx, value)
		return
	}

	// Get base pointer for unsafe access
	ptr := unsafe.Pointer(value.UnsafeAddr())
	err := ctx.Err()

	// Phase 1: Read fixed-size primitive fields (no ref flag)
	// Use deferred error checking - read all fields, check once at end
	for _, field := range s.fixedFields {
		if field.FieldIndex < 0 {
			s.skipField(ctx, field)
			if ctx.HasError() {
				return
			}
			continue
		}
		fieldPtr := unsafe.Add(ptr, field.Offset)
		switch field.StaticId {
		case ConcreteTypeBool:
			*(*bool)(fieldPtr) = buf.ReadBool(err)
		case ConcreteTypeInt8:
			*(*int8)(fieldPtr) = buf.ReadInt8(err)
		case ConcreteTypeInt16:
			*(*int16)(fieldPtr) = buf.ReadInt16(err)
		case ConcreteTypeFloat32:
			*(*float32)(fieldPtr) = buf.ReadFloat32(err)
		case ConcreteTypeFloat64:
			*(*float64)(fieldPtr) = buf.ReadFloat64(err)
		}
	}

	// Deferred error check after fixed-size primitives
	if ctx.HasError() {
		return
	}

	// Phase 2: Read varint primitive fields (no ref flag)
	for _, field := range s.varintFields {
		if field.FieldIndex < 0 {
			s.skipField(ctx, field)
			if ctx.HasError() {
				return
			}
			continue
		}
		fieldPtr := unsafe.Add(ptr, field.Offset)
		switch field.StaticId {
		case ConcreteTypeInt32:
			*(*int32)(fieldPtr) = buf.ReadVarint32(err)
		case ConcreteTypeInt64:
			*(*int64)(fieldPtr) = buf.ReadVarint64(err)
		case ConcreteTypeInt:
			*(*int)(fieldPtr) = int(buf.ReadVarint64(err))
		}
	}

	// Deferred error check after varint primitives
	if ctx.HasError() {
		return
	}

	// Phase 3: Read remaining fields (all non-primitives have ref flag per xlang spec)
	for _, field := range s.remainingFields {
		if field.FieldIndex < 0 {
			s.skipField(ctx, field)
			if ctx.HasError() {
				return
			}
			continue
		}
		fieldValue := value.Field(field.FieldIndex)

		if isEnumField(field) {
			readEnumField(ctx, field, fieldValue)
			if ctx.HasError() {
				return
			}
			continue
		}

		if field.Serializer != nil {
			// For nested struct fields in compatible mode, read type info
			readType := ctx.Compatible() && isStructField(field.Type)
			// Per xlang spec, all non-primitive fields have ref flag
			field.Serializer.Read(ctx, RefModeTracking, readType, fieldValue)
			if ctx.HasError() {
				return
			}
		} else {
			ctx.ReadValue(fieldValue)
			if ctx.HasError() {
				return
			}
		}
	}
}

// readFieldsInOrder reads fields in the order they appear in s.fields (TypeDef order)
// This is used in compatible mode where Java writes fields in TypeDef order
func (s *structSerializer) readFieldsInOrder(ctx *ReadContext, value reflect.Value) {
	buf := ctx.Buffer()
	ptr := unsafe.Pointer(value.UnsafeAddr())
	err := ctx.Err()

	for _, field := range s.fields {
		if field.FieldIndex < 0 {
			s.skipField(ctx, field)
			if ctx.HasError() {
				return
			}
			continue
		}

		fieldPtr := unsafe.Add(ptr, field.Offset)

		// Fast path for fixed-size primitive types (no ref flag)
		// Use error-aware methods with deferred checking
		if isFixedSizePrimitive(field.StaticId, field.Referencable) {
			switch field.StaticId {
			case ConcreteTypeBool:
				*(*bool)(fieldPtr) = buf.ReadBool(err)
			case ConcreteTypeInt8:
				*(*int8)(fieldPtr) = buf.ReadInt8(err)
			case ConcreteTypeInt16:
				*(*int16)(fieldPtr) = buf.ReadInt16(err)
			case ConcreteTypeFloat32:
				*(*float32)(fieldPtr) = buf.ReadFloat32(err)
			case ConcreteTypeFloat64:
				*(*float64)(fieldPtr) = buf.ReadFloat64(err)
			}
			continue
		}

		// Fast path for varint primitive types (no ref flag)
		// Skip fast path if field has a serializer with a non-primitive type (e.g., NAMED_ENUM)
		if isVarintPrimitive(field.StaticId, field.Referencable) && !fieldHasNonPrimitiveSerializer(field) {
			switch field.StaticId {
			case ConcreteTypeInt32:
				*(*int32)(fieldPtr) = buf.ReadVarint32(err)
			case ConcreteTypeInt64:
				*(*int64)(fieldPtr) = buf.ReadVarint64(err)
			case ConcreteTypeInt:
				*(*int)(fieldPtr) = int(buf.ReadVarint64(err))
			}
			continue
		}

		// Check for accumulated errors before slow path (complex types)
		if ctx.HasError() {
			return
		}

		// Get field value for slow paths
		fieldValue := value.Field(field.FieldIndex)

		if isEnumField(field) {
			readEnumField(ctx, field, fieldValue)
			if ctx.HasError() {
				return
			}
			continue
		}

		// Slow path for non-primitives (all need ref flag per xlang spec)
		if field.Serializer != nil {
			// Per xlang spec:
			// - Nullable primitives (*int32, *float64, etc.): ref flag + data (NO type info)
			// - Other types (struct, collections, etc.): ref flag + type info + data
			readType := !isInternalTypeWithoutTypeMeta(field.Type)
			// Determine RefMode based on FieldDef's trackingRef and nullable flags
			// In compatible mode with TypeDef, we must use the flags from the remote TypeDef
			refMode := RefModeNone
			if field.FieldDef.trackingRef {
				refMode = RefModeTracking
			} else if field.FieldDef.nullable || field.Referencable {
				refMode = RefModeNullOnly
			}
			field.Serializer.Read(ctx, refMode, readType, fieldValue)
			if ctx.HasError() {
				return
			}
		} else {
			ctx.ReadValue(fieldValue)
			if ctx.HasError() {
				return
			}
		}
	}
}

// skipField skips a field that doesn't exist or is incompatible
// Uses context error state for deferred error checking.
func (s *structSerializer) skipField(ctx *ReadContext, field *FieldInfo) {
	if field.FieldDef.name != "" {
		fieldDefIsStructType := isStructFieldType(field.FieldDef.fieldType)
		// Use FieldDef's trackingRef and nullable to determine if ref flag was written by Java
		// Java writes ref flag based on its FieldDef, not Go's field type
		readRefFlag := field.FieldDef.trackingRef || field.FieldDef.nullable
		SkipFieldValueWithTypeFlag(ctx, field.FieldDef, readRefFlag, ctx.Compatible() && fieldDefIsStructType)
		return
	}
	// No FieldDef available, read into temp value
	tempValue := reflect.New(field.Type).Elem()
	if field.Serializer != nil {
		readType := ctx.Compatible() && isStructField(field.Type)
		refMode := RefModeNone
		if field.Referencable {
			refMode = RefModeTracking
		}
		field.Serializer.Read(ctx, refMode, readType, tempValue)
	} else {
		ctx.ReadValue(tempValue)
	}
}

func (s *structSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	// typeInfo is already read, don't read it again
	s.Read(ctx, refMode, false, value)
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
	var typeIds []TypeId
	var nullables []bool

	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		firstRune, _ := utf8.DecodeRuneInString(field.Name)
		if unicode.IsLower(firstRune) {
			continue // skip unexported fields
		}

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
			case reflect.Bool:
				fieldSerializer = boolArraySerializer{arrayType: fieldType}
			case reflect.Int8:
				fieldSerializer = int8ArraySerializer{arrayType: fieldType}
			case reflect.Int16:
				fieldSerializer = int16ArraySerializer{arrayType: fieldType}
			case reflect.Int32:
				fieldSerializer = int32ArraySerializer{arrayType: fieldType}
			case reflect.Int64:
				fieldSerializer = int64ArraySerializer{arrayType: fieldType}
			case reflect.Uint8:
				fieldSerializer = uint8ArraySerializer{arrayType: fieldType}
			case reflect.Float32:
				fieldSerializer = float32ArraySerializer{arrayType: fieldType}
			case reflect.Float64:
				fieldSerializer = float64ArraySerializer{arrayType: fieldType}
			case reflect.Int:
				// Platform-dependent int type
				if reflect.TypeOf(int(0)).Size() == 8 {
					fieldSerializer = int64ArraySerializer{arrayType: fieldType}
				} else {
					fieldSerializer = int32ArraySerializer{arrayType: fieldType}
				}
			default:
				// For non-primitive arrays, use sliceConcreteValueSerializer with xlang TypeId
				elemTypeInfo := typeResolver.typesInfo[elemType]
				fieldSerializer, _ = newSliceConcreteValueSerializerForXlang(fieldType, elemTypeInfo.Serializer)
			}
		} else if fieldType.Kind() == reflect.Slice && fieldType.Elem().Kind() != reflect.Interface {
			// For struct fields with concrete element types, use sliceConcreteValueSerializer with xlang TypeId
			elemTypeInfo := typeResolver.typesInfo[fieldType.Elem()]
			fieldSerializer, _ = newSliceConcreteValueSerializerForXlang(fieldType, elemTypeInfo.Serializer)
		} else if fieldType.Kind() == reflect.Slice && fieldType.Elem().Kind() == reflect.Interface {
			// For struct fields with interface element types, use sliceDynSerializer
			fieldSerializer = mustNewSliceDynSerializer(fieldType.Elem())
		}

		// Get TypeId for the serializer, fallback to deriving from kind
		fieldTypeId := typeResolver.getTypeIdByType(fieldType)
		if fieldTypeId == 0 {
			fieldTypeId = typeIdFromKind(fieldType)
		}
		// Calculate nullable flag to match buildFieldDefs logic in type_def.go
		// This ensures writer and reader use the same field ordering and ref mode
		nullableFlag := nullable(fieldType)
		internalId := TypeId(fieldTypeId & 0xFF)
		if isUserDefinedType(int16(internalId)) || internalId == ENUM || internalId == NAMED_ENUM {
			nullableFlag = true
		}
		fieldInfo := &FieldInfo{
			Name:         SnakeCase(field.Name),
			Offset:       field.Offset,
			Type:         fieldType,
			StaticId:     GetStaticTypeId(fieldType),
			TypeId:       fieldTypeId,
			Serializer:   fieldSerializer,
			Referencable: nullableFlag, // Use same logic as TypeDef's nullable flag for consistent ref handling
			FieldIndex:   i,
		}
		fields = append(fields, fieldInfo)
		fieldNames = append(fieldNames, fieldInfo.Name)
		serializers = append(serializers, fieldSerializer)
		typeIds = append(typeIds, fieldTypeId)
		nullables = append(nullables, nullableFlag)
	}

	// Sort fields according to specification using nullable info for consistent ordering
	serializers, fieldNames = sortFieldsWithNullable(typeResolver, fieldNames, serializers, typeIds, nullables)
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
			// Get TypeId from FieldType's TypeId method
			fieldTypeId := def.fieldType.TypeId()
			fieldInfo := &FieldInfo{
				Name:         def.name,
				Offset:       0,
				Type:         remoteType,
				StaticId:     GetStaticTypeId(remoteType),
				TypeId:       fieldTypeId,
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
			} else if typeLookupFailed && (defTypeId == LIST || defTypeId == SET) {
				// For collection fields with failed type lookup (e.g., List<Animal> with interface element type),
				// check if local type is a slice with interface element type (e.g., []Animal)
				// The type lookup fails because sliceConcreteValueSerializer doesn't support interface elements
				if localType.Kind() == reflect.Slice && localType.Elem().Kind() == reflect.Interface {
					shouldRead = true
					fieldType = localType
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
				// For collection fields with interface element types, use sliceDynSerializer
				if typeLookupFailed && (defTypeId == LIST || defTypeId == SET) && fieldSerializer == nil {
					if localType.Kind() == reflect.Slice && localType.Elem().Kind() == reflect.Interface {
						fieldSerializer = mustNewSliceDynSerializer(localType.Elem())
					}
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
				// For array fields, use array serializers (not slice serializers) even if typeID maps to slice serializer
				// The typeID (INT16_ARRAY, etc.) is shared between arrays and slices, but we need the correct
				// serializer based on the actual Go type
				if localType.Kind() == reflect.Array {
					elemType := localType.Elem()
					switch elemType.Kind() {
					case reflect.Bool:
						fieldSerializer = boolArraySerializer{arrayType: localType}
					case reflect.Int8:
						fieldSerializer = int8ArraySerializer{arrayType: localType}
					case reflect.Int16:
						fieldSerializer = int16ArraySerializer{arrayType: localType}
					case reflect.Int32:
						fieldSerializer = int32ArraySerializer{arrayType: localType}
					case reflect.Int64:
						fieldSerializer = int64ArraySerializer{arrayType: localType}
					case reflect.Uint8:
						fieldSerializer = uint8ArraySerializer{arrayType: localType}
					case reflect.Float32:
						fieldSerializer = float32ArraySerializer{arrayType: localType}
					case reflect.Float64:
						fieldSerializer = float64ArraySerializer{arrayType: localType}
					case reflect.Int:
						if reflect.TypeOf(int(0)).Size() == 8 {
							fieldSerializer = int64ArraySerializer{arrayType: localType}
						} else {
							fieldSerializer = int32ArraySerializer{arrayType: localType}
						}
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

		// Get TypeId from FieldType's TypeId method
		fieldTypeId := def.fieldType.TypeId()
		fieldInfo := &FieldInfo{
			Name:         def.name,
			Offset:       offset,
			Type:         fieldType,
			StaticId:     GetStaticTypeId(fieldType),
			TypeId:       fieldTypeId,
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
		sb.WriteString(SnakeCase(field.Name))
		sb.WriteString(",")

		var typeId TypeId
		isEnumField := false
		if field.Serializer == nil {
			typeId = UNKNOWN
		} else {
			typeId = field.TypeId
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

// ptrToCodegenSerializer wraps a generated serializer for pointer types
type ptrToCodegenSerializer struct {
	type_             reflect.Type
	codegenSerializer Serializer
}

func (s *ptrToCodegenSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	// Dereference pointer and delegate to the generated serializer
	s.codegenSerializer.WriteData(ctx, value.Elem())
}

func (s *ptrToCodegenSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	switch refMode {
	case RefModeTracking:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			ctx.SetError(FromError(err))
			return
		}
		if refWritten {
			return
		}
	case RefModeNullOnly:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return
		}
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// Codegen structs have dynamic type IDs
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			ctx.SetError(FromError(err))
			return
		}
		if err := ctx.TypeResolver().WriteTypeInfo(ctx.Buffer(), typeInfo); err != nil {
			ctx.SetError(FromError(err))
			return
		}
	}
	s.WriteData(ctx, value)
}

func (s *ptrToCodegenSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	// Allocate new value if needed
	newValue := reflect.New(type_.Elem())
	value.Set(newValue)
	elem := newValue.Elem()
	ctx.RefResolver().Reference(newValue)
	s.codegenSerializer.ReadData(ctx, type_.Elem(), elem)
}

func (s *ptrToCodegenSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	switch refMode {
	case RefModeTracking:
		refID, refErr := ctx.RefResolver().TryPreserveRefId(buf)
		if refErr != nil {
			ctx.SetError(FromError(refErr))
			return
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8(ctxErr)
		if flag == NullFlag {
			return
		}
	}
	if ctx.HasError() {
		return
	}
	if readType {
		typeID := buf.ReadVaruint32Small7(ctxErr)
		if ctx.HasError() {
			return
		}
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			_, _ = ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
		}
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s *ptrToCodegenSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
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
	typeIds []TypeId,
) ([]Serializer, []string) {
	// Default nullable to false for all fields (backwards compatible)
	nullables := make([]bool, len(fieldNames))
	return sortFieldsWithNullable(typeResolver, fieldNames, serializers, typeIds, nullables)
}

// sortFieldsWithNullable sorts fields with nullable information to match Java's field ordering.
// Java separates primitive types (int, long) from boxed types (Integer, Long).
// In Go, this corresponds to non-pointer primitives vs pointer-to-primitive.
func sortFieldsWithNullable(
	typeResolver *TypeResolver,
	fieldNames []string,
	serializers []Serializer,
	typeIds []TypeId,
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
			others = append(others, triple{UNKNOWN, nil, name, nullables[i]})
			continue
		}
		typeTriples = append(typeTriples, triple{typeIds[i], ser, name, nullables[i]})
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
		case t.typeID == UNKNOWN:
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
			return SnakeCase(ai.name) < SnakeCase(aj.name)
		})
	}
	sortPrimitiveSlice(primitives)
	sortPrimitiveSlice(boxed)
	sortByTypeIDThenName := func(s []triple) {
		sort.Slice(s, func(i, j int) bool {
			if s[i].typeID != s[j].typeID {
				return s[i].typeID < s[j].typeID
			}
			return SnakeCase(s[i].name) < SnakeCase(s[j].name)
		})
	}
	sortTuple := func(s []triple) {
		sort.Slice(s, func(i, j int) bool {
			return SnakeCase(s[i].name) < SnakeCase(s[j].name)
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

// typeIdFromKind derives a TypeId from a reflect.Type's kind
// This is used when the type is not registered in typesInfo
func typeIdFromKind(type_ reflect.Type) TypeId {
	switch type_.Kind() {
	case reflect.Bool:
		return BOOL
	case reflect.Int8:
		return INT8
	case reflect.Int16:
		return INT16
	case reflect.Int32:
		return INT32
	case reflect.Int64, reflect.Int:
		return INT64
	case reflect.Uint8:
		return UINT8
	case reflect.Uint16:
		return UINT16
	case reflect.Uint32:
		return UINT32
	case reflect.Uint64, reflect.Uint:
		return UINT64
	case reflect.Float32:
		return FLOAT
	case reflect.Float64:
		return DOUBLE
	case reflect.String:
		return STRING
	case reflect.Slice:
		return LIST
	case reflect.Array:
		// For arrays, return the appropriate primitive array type ID based on element type
		elemKind := type_.Elem().Kind()
		switch elemKind {
		case reflect.Bool:
			return BOOL_ARRAY
		case reflect.Int8:
			return INT8_ARRAY
		case reflect.Int16:
			return INT16_ARRAY
		case reflect.Int32:
			return INT32_ARRAY
		case reflect.Int64, reflect.Int:
			return INT64_ARRAY
		case reflect.Float32:
			return FLOAT32_ARRAY
		case reflect.Float64:
			return FLOAT64_ARRAY
		default:
			// Non-primitive arrays use LIST
			return LIST
		}
	case reflect.Map:
		return MAP
	case reflect.Struct:
		return NAMED_STRUCT
	case reflect.Ptr:
		// For pointer types, get the type ID of the element type
		return typeIdFromKind(type_.Elem())
	default:
		return UNKNOWN
	}
}

// skipStructSerializer is a serializer that skips unknown struct data
// It reads and discards field data based on fieldDefs from remote TypeDef
type skipStructSerializer struct {
	fieldDefs []FieldDef
}

func (s *skipStructSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	ctx.SetError(SerializationError("skipStructSerializer does not support WriteData - unknown struct type"))
}

func (s *skipStructSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	ctx.SetError(SerializationError("skipStructSerializer does not support Write - unknown struct type"))
}

func (s *skipStructSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	// Skip all fields based on fieldDefs from remote TypeDef
	for _, fieldDef := range s.fieldDefs {
		isStructType := isStructFieldType(fieldDef.fieldType)
		// Use trackingRef from FieldDef for ref flag decision
		SkipFieldValueWithTypeFlag(ctx, fieldDef, fieldDef.trackingRef, ctx.Compatible() && isStructType)
		if ctx.HasError() {
			return
		}
	}
}

func (s *skipStructSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	switch refMode {
	case RefModeTracking:
		refID, refErr := ctx.RefResolver().TryPreserveRefId(buf)
		if refErr != nil {
			ctx.SetError(FromError(refErr))
			return
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found, nothing to skip
			return
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8(ctxErr)
		if flag == NullFlag {
			return
		}
	}
	if ctx.HasError() {
		return
	}
	s.ReadData(ctx, nil, value)
}

func (s *skipStructSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	// typeInfo is already read, don't read it again - just skip data
	s.Read(ctx, refMode, false, value)
}
