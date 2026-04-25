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
	"strings"
)

type TypeSpec struct {
	typeId     TypeId
	userTypeId uint32
	nullable   bool
	ref        bool
	element    *TypeSpec
	key        *TypeSpec
	value      *TypeSpec
}

func NewTypeSpec(typeId TypeId) *TypeSpec {
	return &TypeSpec{
		typeId:     typeId,
		userTypeId: invalidUserTypeID,
	}
}

func (s *TypeSpec) Clone() *TypeSpec {
	if s == nil {
		return nil
	}
	clone := *s
	clone.element = s.element.Clone()
	clone.key = s.key.Clone()
	clone.value = s.value.Clone()
	return &clone
}

func (s *TypeSpec) TypeId() TypeId {
	if s == nil {
		return UNKNOWN
	}
	return s.typeId
}

func (s *TypeSpec) UserTypeId() uint32 {
	if s == nil {
		return invalidUserTypeID
	}
	return s.userTypeId
}

func (s *TypeSpec) Nullable() bool {
	return s != nil && s.nullable
}

func (s *TypeSpec) Ref() bool {
	return s != nil && s.ref
}

func (s *TypeSpec) Element() *TypeSpec {
	if s == nil {
		return nil
	}
	return s.element
}

func (s *TypeSpec) Key() *TypeSpec {
	if s == nil {
		return nil
	}
	return s.key
}

func (s *TypeSpec) Value() *TypeSpec {
	if s == nil {
		return nil
	}
	return s.value
}

func (s *TypeSpec) Equal(other *TypeSpec) bool {
	if s == nil || other == nil {
		return s == other
	}
	return s.typeId == other.typeId &&
		s.userTypeId == other.userTypeId &&
		s.nullable == other.nullable &&
		s.ref == other.ref &&
		s.element.Equal(other.element) &&
		s.key.Equal(other.key) &&
		s.value.Equal(other.value)
}

func (s *TypeSpec) String() string {
	if s == nil {
		return "nil"
	}
	var b strings.Builder
	s.appendString(&b)
	return b.String()
}

func (s *TypeSpec) appendString(b *strings.Builder) {
	b.WriteString(typeSpecNameForTypeId(s.typeId))
	var attrs []string
	if s.nullable {
		attrs = append(attrs, "nullable=true")
	}
	if s.ref {
		attrs = append(attrs, "ref=true")
	}
	if len(attrs) > 0 {
		b.WriteByte('(')
		b.WriteString(strings.Join(attrs, ","))
		b.WriteByte(')')
	}
	switch s.typeId {
	case LIST, SET:
		b.WriteString("[")
		if s.element != nil {
			s.element.appendString(b)
		}
		b.WriteString("]")
	case MAP:
		b.WriteString("[")
		if s.key != nil {
			s.key.appendString(b)
		}
		b.WriteString("=>")
		if s.value != nil {
			s.value.appendString(b)
		}
		b.WriteString("]")
	}
}

func (s *TypeSpec) write(buffer *ByteBuffer) {
	buffer.WriteUint8(uint8(s.typeId))
	switch s.typeId {
	case LIST, SET:
		if s.element == nil {
			NewTypeSpec(UNKNOWN).writeWithFlags(buffer, true, false)
			return
		}
		s.element.writeWithFlags(buffer, s.element.nullable, s.element.ref)
	case MAP:
		key := s.key
		if key == nil {
			key = NewTypeSpec(UNKNOWN)
		}
		value := s.value
		if value == nil {
			value = NewTypeSpec(UNKNOWN)
		}
		key.writeWithFlags(buffer, key.nullable, key.ref)
		value.writeWithFlags(buffer, value.nullable, value.ref)
	}
}

func (s *TypeSpec) writeWithFlags(buffer *ByteBuffer, nullable bool, ref bool) {
	value := uint32(s.typeId) << 2
	if nullable {
		value |= 0b10
	}
	if ref {
		value |= 0b01
	}
	buffer.WriteVarUint32Small7(value)
	switch s.typeId {
	case LIST, SET:
		if s.element == nil {
			NewTypeSpec(UNKNOWN).writeWithFlags(buffer, true, false)
			return
		}
		s.element.writeWithFlags(buffer, s.element.nullable, s.element.ref)
	case MAP:
		key := s.key
		if key == nil {
			key = NewTypeSpec(UNKNOWN)
		}
		valueSpec := s.value
		if valueSpec == nil {
			valueSpec = NewTypeSpec(UNKNOWN)
		}
		key.writeWithFlags(buffer, key.nullable, key.ref)
		valueSpec.writeWithFlags(buffer, valueSpec.nullable, valueSpec.ref)
	}
}

func readTypeSpec(buffer *ByteBuffer, err *Error) (*TypeSpec, error) {
	typeId := TypeId(buffer.ReadUint8(err))
	spec := NewTypeSpec(typeId)
	switch typeId {
	case LIST, SET:
		element, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		spec.element = element
	case MAP:
		key, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		value, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		spec.key = key
		spec.value = value
	}
	return spec, nil
}

func readTypeSpecWithFlags(buffer *ByteBuffer, err *Error) (*TypeSpec, error) {
	rawValue := buffer.ReadVarUint32Small7(err)
	typeId := TypeId(rawValue >> 2)
	spec := NewTypeSpec(typeId)
	spec.nullable = (rawValue & 0b10) != 0
	spec.ref = (rawValue & 0b1) != 0
	switch typeId {
	case LIST, SET:
		element, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		spec.element = element
	case MAP:
		key, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		value, readErr := readTypeSpecWithFlags(buffer, err)
		if readErr != nil {
			return nil, readErr
		}
		spec.key = key
		spec.value = value
	}
	return spec, nil
}

func (s *TypeSpec) getTypeInfo(fory *Fory) (TypeInfo, error) {
	return s.getTypeInfoWithResolver(fory.typeResolver)
}

func (s *TypeSpec) getTypeInfoWithResolver(resolver *TypeResolver) (TypeInfo, error) {
	if s == nil {
		return TypeInfo{}, nil
	}
	if isUserDefinedType(s.typeId) {
		if info, err := resolver.getTypeInfoById(uint32(s.typeId)); err == nil && info != nil {
			return *info, nil
		}
		return TypeInfo{Type: interfaceType}, nil
	}
	switch s.typeId {
	case LIST:
		elemInfo, err := s.element.getTypeInfoWithResolver(resolver)
		if err != nil {
			return TypeInfo{}, err
		}
		elemType := elemInfo.Type
		if elemType == nil {
			elemType = interfaceType
		}
		listType := reflect.SliceOf(elemType)
		if elemType == interfaceType {
			return TypeInfo{Type: listType, Serializer: sliceDynSerializer{}}, nil
		}
		serializer, err := buildSerializerForTypeSpec(resolver, listType, s)
		if err != nil {
			return TypeInfo{}, err
		}
		return TypeInfo{Type: listType, Serializer: serializer}, nil
	case SET:
		elemInfo, err := s.element.getTypeInfoWithResolver(resolver)
		if err != nil {
			return TypeInfo{}, err
		}
		elemType := elemInfo.Type
		if elemType == nil {
			elemType = interfaceType
		}
		setType := reflect.MapOf(elemType, emptyStructVal.Type())
		serializer, err := buildSerializerForTypeSpec(resolver, setType, s)
		if err != nil {
			return TypeInfo{}, err
		}
		return TypeInfo{Type: setType, Serializer: serializer}, nil
	case MAP:
		keyInfo, err := s.key.getTypeInfoWithResolver(resolver)
		if err != nil {
			return TypeInfo{}, err
		}
		valueInfo, err := s.value.getTypeInfoWithResolver(resolver)
		if err != nil {
			return TypeInfo{}, err
		}
		keyType := keyInfo.Type
		if keyType == nil {
			keyType = interfaceType
		}
		valueType := valueInfo.Type
		if valueType == nil {
			valueType = interfaceType
		}
		mapType := reflect.MapOf(keyType, valueType)
		serializer, err := buildSerializerForTypeSpec(resolver, mapType, s)
		if err != nil {
			return TypeInfo{}, err
		}
		return TypeInfo{Type: mapType, Serializer: serializer}, nil
	default:
		info, err := resolver.getTypeInfoById(uint32(s.typeId))
		if err == nil && info != nil {
			return *info, nil
		}
		goType := typeFromTypeId(s.typeId)
		if goType == nil {
			return TypeInfo{Type: interfaceType}, nil
		}
		serializer, serErr := buildSerializerForTypeSpec(resolver, goType, s)
		if serErr != nil {
			return TypeInfo{}, serErr
		}
		return TypeInfo{Type: goType, Serializer: serializer}, nil
	}
}

func isStructTypeSpec(spec *TypeSpec) bool {
	if spec == nil {
		return false
	}
	switch spec.typeId {
	case STRUCT, COMPATIBLE_STRUCT, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, EXT, NAMED_EXT:
		return true
	default:
		return IsNamespacedType(spec.typeId)
	}
}

func getTypeSpecSerializer(fory *Fory, goType reflect.Type, spec *TypeSpec) (Serializer, error) {
	return getTypeSpecSerializerWithResolver(fory.typeResolver, goType, spec, nil)
}

func getTypeSpecSerializerWithResolver(resolver *TypeResolver, goType reflect.Type, spec *TypeSpec, legacy FieldType) (Serializer, error) {
	if spec != nil {
		if goType != nil {
			return buildSerializerForTypeSpec(resolver, goType, spec)
		}
		info, err := spec.getTypeInfoWithResolver(resolver)
		if err != nil {
			return nil, err
		}
		return info.Serializer, nil
	}
	if legacy == nil {
		return nil, nil
	}
	return getFieldTypeSerializerWithResolver(resolver, legacy)
}

func getTypeInfoForFieldDef(resolver *TypeResolver, goType reflect.Type, def FieldDef) (TypeInfo, error) {
	if def.typeSpec != nil {
		if goType != nil {
			serializer, err := buildSerializerForTypeSpec(resolver, goType, def.typeSpec)
			if err != nil {
				return TypeInfo{}, err
			}
			return TypeInfo{Type: goType, Serializer: serializer, TypeID: uint32(def.typeSpec.TypeId())}, nil
		}
		return def.typeSpec.getTypeInfoWithResolver(resolver)
	}
	if def.fieldType == nil {
		return TypeInfo{}, nil
	}
	return def.fieldType.getTypeInfoWithResolver(resolver)
}

func buildSerializerForTypeSpec(resolver *TypeResolver, goType reflect.Type, spec *TypeSpec) (Serializer, error) {
	if spec == nil {
		return resolver.getSerializerByType(goType, false)
	}
	if info, ok := getOptionalInfo(goType); ok {
		valueSer, err := buildSerializerForTypeSpec(resolver, info.valueType, spec)
		if err != nil {
			return nil, err
		}
		return newOptionalSerializer(goType, info, valueSer), nil
	}
	if goType.Kind() == reflect.Ptr {
		if goType.Elem().Kind() == reflect.Interface {
			return &ptrToInterfaceSerializer{}, nil
		}
		childSpec := spec.Clone()
		childSpec.nullable = false
		childSpec.ref = false
		valueSer, err := buildSerializerForTypeSpec(resolver, goType.Elem(), childSpec)
		if err != nil {
			return nil, err
		}
		return &ptrToValueSerializer{valueSerializer: valueSer}, nil
	}

	switch spec.typeId {
	case LIST:
		if goType.Kind() != reflect.Slice && goType.Kind() != reflect.Array {
			return nil, fmt.Errorf("type spec LIST requires slice or array carrier, got %v", goType)
		}
		elemSer, err := buildSerializerForTypeSpec(resolver, goType.Elem(), spec.element)
		if err != nil {
			return nil, err
		}
		return &sliceSerializer{
			type_:          goType,
			elemSerializer: elemSer,
			referencable:   typeSpecChildTrackRef(goType.Elem(), spec.element, resolver.isXlang),
		}, nil
	case MAP:
		if goType.Kind() != reflect.Map {
			return nil, fmt.Errorf("type spec MAP requires map carrier, got %v", goType)
		}
		keySer, err := buildSerializerForTypeSpec(resolver, goType.Key(), spec.key)
		if err != nil {
			return nil, err
		}
		valueSer, err := buildSerializerForTypeSpec(resolver, goType.Elem(), spec.value)
		if err != nil {
			return nil, err
		}
		return &mapSerializer{
			type_:             goType,
			keySerializer:     keySer,
			valueSerializer:   valueSer,
			keyReferencable:   typeSpecChildTrackRef(goType.Key(), spec.key, resolver.isXlang),
			valueReferencable: typeSpecChildTrackRef(goType.Elem(), spec.value, resolver.isXlang),
			hasGenerics:       true,
		}, nil
	case SET:
		if !isSetReflectType(goType) {
			return nil, fmt.Errorf("type spec SET requires set carrier, got %v", goType)
		}
		elemSer, err := buildSerializerForTypeSpec(resolver, goType.Key(), spec.element)
		if err != nil {
			return nil, err
		}
		return setSerializer{
			elementSerializer: elemSer,
			referencable:      typeSpecChildTrackRef(goType.Key(), spec.element, resolver.isXlang),
		}, nil
	case BINARY, BOOL_ARRAY, INT8_ARRAY, INT16_ARRAY, INT32_ARRAY, INT64_ARRAY, UINT8_ARRAY, UINT16_ARRAY, UINT32_ARRAY, UINT64_ARRAY, FLOAT16_ARRAY, BFLOAT16_ARRAY, FLOAT32_ARRAY, FLOAT64_ARRAY:
		if goType.Kind() == reflect.Array {
			return resolver.GetArraySerializer(goType)
		}
		return resolver.GetSliceSerializer(goType)
	case INT32:
		return fixedInt32ValueSerializer{}, nil
	case UINT32:
		return fixedUint32ValueSerializer{}, nil
	case INT64:
		return fixedInt64ValueSerializer{}, nil
	case UINT64:
		return fixedUint64ValueSerializer{}, nil
	case TAGGED_INT64:
		return taggedInt64ValueSerializer{}, nil
	case TAGGED_UINT64:
		return taggedUint64ValueSerializer{}, nil
	case VARINT32, VARINT64, VAR_UINT32, VAR_UINT64:
		return resolver.getSerializerByType(goType, false)
	default:
		return resolver.getSerializerByType(goType, false)
	}
}

func typeSpecChildTrackRef(goType reflect.Type, spec *TypeSpec, xlang bool) bool {
	if spec == nil {
		return isRefType(goType, xlang)
	}
	if !NeedWriteRef(spec.typeId) {
		return false
	}
	return spec.ref
}

func typeFromTypeId(typeId TypeId) reflect.Type {
	switch typeId {
	case BOOL:
		return reflect.TypeOf(false)
	case INT8:
		return reflect.TypeOf(int8(0))
	case INT16:
		return reflect.TypeOf(int16(0))
	case INT32, VARINT32:
		return reflect.TypeOf(int32(0))
	case INT64, VARINT64, TAGGED_INT64:
		return reflect.TypeOf(int64(0))
	case UINT8:
		return reflect.TypeOf(uint8(0))
	case UINT16:
		return reflect.TypeOf(uint16(0))
	case UINT32, VAR_UINT32:
		return reflect.TypeOf(uint32(0))
	case UINT64, VAR_UINT64, TAGGED_UINT64:
		return reflect.TypeOf(uint64(0))
	case FLOAT32:
		return reflect.TypeOf(float32(0))
	case FLOAT64:
		return reflect.TypeOf(float64(0))
	case STRING:
		return reflect.TypeOf("")
	case BINARY:
		return reflect.TypeOf([]byte{})
	case BOOL_ARRAY:
		return reflect.TypeOf([]bool{})
	case INT8_ARRAY:
		return reflect.TypeOf([]int8{})
	case INT16_ARRAY:
		return reflect.TypeOf([]int16{})
	case INT32_ARRAY:
		return reflect.TypeOf([]int32{})
	case INT64_ARRAY:
		return reflect.TypeOf([]int64{})
	case UINT8_ARRAY:
		return reflect.TypeOf([]uint8{})
	case UINT16_ARRAY:
		return reflect.TypeOf([]uint16{})
	case UINT32_ARRAY:
		return reflect.TypeOf([]uint32{})
	case UINT64_ARRAY:
		return reflect.TypeOf([]uint64{})
	case FLOAT32_ARRAY:
		return reflect.TypeOf([]float32{})
	case FLOAT64_ARRAY:
		return reflect.TypeOf([]float64{})
	default:
		return nil
	}
}

func typeSpecNameForTypeId(typeId TypeId) string {
	switch typeId {
	case BOOL:
		return "bool"
	case INT8:
		return "int8"
	case INT16:
		return "int16"
	case INT32:
		return "int32"
	case VARINT32:
		return "int32(varint)"
	case INT64:
		return "int64"
	case VARINT64:
		return "int64(varint)"
	case TAGGED_INT64:
		return "int64(tagged)"
	case UINT8:
		return "uint8"
	case UINT16:
		return "uint16"
	case UINT32:
		return "uint32"
	case VAR_UINT32:
		return "uint32(varint)"
	case UINT64:
		return "uint64"
	case VAR_UINT64:
		return "uint64(varint)"
	case TAGGED_UINT64:
		return "uint64(tagged)"
	case FLOAT32:
		return "float32"
	case FLOAT64:
		return "float64"
	case STRING:
		return "string"
	case LIST:
		return "list"
	case SET:
		return "set"
	case MAP:
		return "map"
	case STRUCT, COMPATIBLE_STRUCT, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT:
		return "struct"
	case BINARY:
		return "binary"
	case BOOL_ARRAY:
		return "bool_array"
	case INT8_ARRAY:
		return "int8_array"
	case INT16_ARRAY:
		return "int16_array"
	case INT32_ARRAY:
		return "int32_array"
	case INT64_ARRAY:
		return "int64_array"
	case UINT8_ARRAY:
		return "uint8_array"
	case UINT16_ARRAY:
		return "uint16_array"
	case UINT32_ARRAY:
		return "uint32_array"
	case UINT64_ARRAY:
		return "uint64_array"
	case FLOAT32_ARRAY:
		return "float32_array"
	case FLOAT64_ARRAY:
		return "float64_array"
	default:
		return fmt.Sprintf("type_%d", typeId)
	}
}

func buildTypeSpecForField(resolver *TypeResolver, fieldType reflect.Type, tag ForyTag) (*TypeSpec, error) {
	spec, err := inferTypeSpec(resolver, fieldType, true)
	if err != nil {
		return nil, err
	}
	if tag.EncodingSet {
		typeId, mapErr := applyNumericEncoding(fieldType, tag.Encoding)
		if mapErr != nil {
			return nil, mapErr
		}
		spec.typeId = typeId
	}
	if tag.TypeOverride != nil {
		if err := applyTypeOverride(fieldType, spec, tag.TypeOverride, true); err != nil {
			return nil, err
		}
	}
	spec = normalizeTypeSpec(fieldType, spec)
	if tag.NullableSet {
		spec.nullable = tag.Nullable
	}
	if tag.RefSet {
		spec.ref = tag.Ref && NeedWriteRef(spec.typeId)
	}
	return spec, nil
}

func inferTypeSpec(resolver *TypeResolver, type_ reflect.Type, topLevel bool) (*TypeSpec, error) {
	if info, ok := getOptionalInfo(type_); ok {
		spec, err := inferTypeSpec(resolver, info.valueType, topLevel)
		if err != nil {
			return nil, err
		}
		spec.nullable = true
		spec.ref = NeedWriteRef(spec.typeId) && isRefType(type_, resolver.isXlang)
		return spec, nil
	}
	if type_.Kind() == reflect.Ptr {
		spec, err := inferTypeSpec(resolver, type_.Elem(), topLevel)
		if err != nil {
			return nil, err
		}
		spec.nullable = true
		spec.ref = NeedWriteRef(spec.typeId) && isRefType(type_, resolver.isXlang)
		return spec, nil
	}
	if isUnionType(type_) {
		spec := NewTypeSpec(UNION)
		spec.ref = NeedWriteRef(spec.typeId) && isRefType(type_, resolver.isXlang)
		return spec, nil
	}
	if type_.Kind() == reflect.Interface {
		spec := NewTypeSpec(UNKNOWN)
		spec.ref = true
		return spec, nil
	}

	switch type_.Kind() {
	case reflect.Slice, reflect.Array:
		spec := NewTypeSpec(LIST)
		elemSpec, err := inferElementTypeSpec(resolver, type_.Elem())
		if err != nil {
			return nil, err
		}
		spec.element = elemSpec
		spec.ref = isRefType(type_, resolver.isXlang)
		return spec, nil
	case reflect.Map:
		if isSetReflectType(type_) {
			spec := NewTypeSpec(SET)
			elemSpec, err := inferTypeSpec(resolver, type_.Key(), false)
			if err != nil {
				return nil, err
			}
			spec.element = elemSpec
			spec.ref = isRefType(type_, resolver.isXlang)
			return spec, nil
		}
		spec := NewTypeSpec(MAP)
		keySpec, err := inferTypeSpec(resolver, type_.Key(), false)
		if err != nil {
			return nil, err
		}
		valueSpec, err := inferTypeSpec(resolver, type_.Elem(), false)
		if err != nil {
			return nil, err
		}
		spec.key = keySpec
		spec.value = valueSpec
		spec.ref = isRefType(type_, resolver.isXlang)
		return spec, nil
	default:
		typeId, err := inferLeafTypeId(resolver, type_)
		if err != nil {
			return nil, err
		}
		spec := NewTypeSpec(typeId)
		spec.ref = NeedWriteRef(typeId) && isRefType(type_, resolver.isXlang)
		return spec, nil
	}
}

func inferElementTypeSpec(resolver *TypeResolver, elemType reflect.Type) (*TypeSpec, error) {
	if info, ok := getOptionalInfo(elemType); ok {
		spec, err := inferElementTypeSpec(resolver, info.valueType)
		if err != nil {
			return nil, err
		}
		spec.nullable = true
		spec.ref = NeedWriteRef(spec.typeId) && isRefType(elemType, resolver.isXlang)
		return spec, nil
	}
	if elemType.Kind() == reflect.Ptr {
		spec, err := inferTypeSpec(resolver, elemType.Elem(), false)
		if err != nil {
			return nil, err
		}
		spec.nullable = true
		spec.ref = NeedWriteRef(spec.typeId) && isRefType(elemType, resolver.isXlang)
		return spec, nil
	}
	switch elemType.Kind() {
	case reflect.Int32:
		spec := NewTypeSpec(INT32)
		return spec, nil
	case reflect.Uint32:
		spec := NewTypeSpec(UINT32)
		return spec, nil
	case reflect.Int64, reflect.Int:
		spec := NewTypeSpec(INT64)
		return spec, nil
	case reflect.Uint64, reflect.Uint:
		spec := NewTypeSpec(UINT64)
		return spec, nil
	case reflect.Int16:
		return NewTypeSpec(INT16), nil
	case reflect.Uint16:
		return NewTypeSpec(UINT16), nil
	case reflect.Int8:
		return NewTypeSpec(INT8), nil
	case reflect.Uint8:
		return NewTypeSpec(UINT8), nil
	case reflect.Bool:
		return NewTypeSpec(BOOL), nil
	case reflect.Float32:
		return NewTypeSpec(FLOAT32), nil
	case reflect.Float64:
		return NewTypeSpec(FLOAT64), nil
	default:
		return inferTypeSpec(resolver, elemType, false)
	}
}

func inferLeafTypeId(resolver *TypeResolver, type_ reflect.Type) (TypeId, error) {
	typeId := typeIdFromKind(type_)
	if typeId == NAMED_ENUM {
		return ENUM, nil
	}
	if typeId == NAMED_UNION || typeId == TYPED_UNION {
		return UNION, nil
	}
	if typeId != UNKNOWN && typeId != NAMED_STRUCT {
		return typeId, nil
	}
	typeInfo, err := resolver.getTypeInfo(reflect.New(type_).Elem(), true)
	if err != nil {
		return UNKNOWN, err
	}
	inferred := TypeId(typeInfo.TypeID)
	if inferred == NAMED_ENUM {
		inferred = ENUM
	}
	if inferred == NAMED_UNION || inferred == TYPED_UNION {
		inferred = UNION
	}
	return inferred, nil
}

func applyTypeOverride(goType reflect.Type, spec *TypeSpec, node *typeOverrideNode, root bool) error {
	if spec == nil || node == nil {
		return nil
	}
	if info, ok := getOptionalInfo(goType); ok {
		return applyTypeOverride(info.valueType, spec, node, root)
	}
	if goType.Kind() == reflect.Ptr {
		return applyTypeOverride(goType.Elem(), spec, node, root)
	}

	if node.explicitName {
		typeId, err := overrideTypeIdForNode(goType, spec, node, root)
		if err != nil {
			return err
		}
		spec.typeId = typeId
	}
	if node.encodingSet {
		typeId, err := applyNumericEncoding(goType, node.encoding)
		if err != nil {
			return err
		}
		spec.typeId = typeId
	}
	if node.nullable != nil {
		spec.nullable = *node.nullable
	}
	if node.ref != nil {
		spec.ref = *node.ref && NeedWriteRef(spec.typeId)
	}
	switch spec.typeId {
	case LIST, SET:
		if node.key != nil || node.value != nil {
			return fmt.Errorf("%s override cannot define key/value children", spec.String())
		}
		if node.element != nil {
			if spec.element == nil {
				spec.element = NewTypeSpec(UNKNOWN)
			}
			if err := applyTypeOverride(goType.Elem(), spec.element, node.element, false); err != nil {
				return err
			}
		}
	case MAP:
		if node.element != nil {
			return fmt.Errorf("map override cannot define element child")
		}
		if node.key != nil {
			if err := applyTypeOverride(goType.Key(), spec.key, node.key, false); err != nil {
				return err
			}
		}
		if node.value != nil {
			if err := applyTypeOverride(goType.Elem(), spec.value, node.value, false); err != nil {
				return err
			}
		}
	default:
		if node.element != nil || node.key != nil || node.value != nil {
			return fmt.Errorf("scalar override %q cannot define nested children", node.name)
		}
	}
	return nil
}

func overrideTypeIdForNode(goType reflect.Type, current *TypeSpec, node *typeOverrideNode, root bool) (TypeId, error) {
	switch node.name {
	case "list":
		if goType.Kind() != reflect.Slice && goType.Kind() != reflect.Array {
			return UNKNOWN, fmt.Errorf("type override list requires slice/array carrier, got %v", goType)
		}
		return LIST, nil
	case "set":
		if !isSetReflectType(goType) {
			return UNKNOWN, fmt.Errorf("type override set requires Set/map[T]struct{} carrier, got %v", goType)
		}
		return SET, nil
	case "map":
		if goType.Kind() != reflect.Map || isSetReflectType(goType) {
			return UNKNOWN, fmt.Errorf("type override map requires map carrier, got %v", goType)
		}
		return MAP, nil
	case "struct":
		if current == nil {
			return UNKNOWN, fmt.Errorf("cannot apply struct override without inferred type")
		}
		return current.typeId, nil
	default:
		typeId, ok := typeIdForOverrideName(node.name)
		if !ok {
			return UNKNOWN, fmt.Errorf("unsupported type override %q", node.name)
		}
		return typeId, nil
	}
}

func typeIdForOverrideName(name string) (TypeId, bool) {
	switch name {
	case "bool":
		return BOOL, true
	case "int8":
		return INT8, true
	case "int16":
		return INT16, true
	case "int32":
		return VARINT32, true
	case "int64":
		return VARINT64, true
	case "uint8":
		return UINT8, true
	case "uint16":
		return UINT16, true
	case "uint32":
		return VAR_UINT32, true
	case "uint64":
		return VAR_UINT64, true
	case "float32":
		return FLOAT32, true
	case "float64":
		return FLOAT64, true
	case "string":
		return STRING, true
	case "binary":
		return BINARY, true
	default:
		return UNKNOWN, false
	}
}

func applyNumericEncoding(type_ reflect.Type, encoding string) (TypeId, error) {
	if info, ok := getOptionalInfo(type_); ok {
		type_ = info.valueType
	}
	if type_.Kind() == reflect.Ptr {
		type_ = type_.Elem()
	}
	switch type_.Kind() {
	case reflect.Int32:
		switch encoding {
		case "fixed":
			return INT32, nil
		case "varint":
			return VARINT32, nil
		}
	case reflect.Uint32:
		switch encoding {
		case "fixed":
			return UINT32, nil
		case "varint":
			return VAR_UINT32, nil
		}
	case reflect.Int64, reflect.Int:
		switch encoding {
		case "fixed":
			return INT64, nil
		case "varint":
			return VARINT64, nil
		case "tagged":
			return TAGGED_INT64, nil
		}
	case reflect.Uint64, reflect.Uint:
		switch encoding {
		case "fixed":
			return UINT64, nil
		case "varint":
			return VAR_UINT64, nil
		case "tagged":
			return TAGGED_UINT64, nil
		}
	}
	return UNKNOWN, fmt.Errorf("encoding=%q is invalid for %v", encoding, type_)
}

func normalizeTypeSpec(goType reflect.Type, spec *TypeSpec) *TypeSpec {
	if spec == nil {
		return nil
	}
	normalized := spec.Clone()
	if info, ok := getOptionalInfo(goType); ok {
		return normalizeTypeSpec(info.valueType, normalized)
	}
	if goType.Kind() == reflect.Ptr {
		return normalizeTypeSpec(goType.Elem(), normalized)
	}
	switch normalized.typeId {
	case LIST:
		if goType.Kind() == reflect.Slice || goType.Kind() == reflect.Array {
			normalized.element = normalizeTypeSpec(goType.Elem(), normalized.element)
			if packedTypeId, ok := packedArrayTypeIdFor(goType, normalized.element); ok {
				normalized.typeId = packedTypeId
				normalized.element = nil
			}
		}
	case SET:
		if isSetReflectType(goType) {
			normalized.element = normalizeTypeSpec(goType.Key(), normalized.element)
		}
	case MAP:
		if goType.Kind() == reflect.Map {
			normalized.key = normalizeTypeSpec(goType.Key(), normalized.key)
			normalized.value = normalizeTypeSpec(goType.Elem(), normalized.value)
		}
	}
	return normalized
}

func packedArrayTypeIdFor(goType reflect.Type, element *TypeSpec) (TypeId, bool) {
	if element == nil || element.nullable || element.ref {
		return UNKNOWN, false
	}
	if goType.Kind() != reflect.Slice && goType.Kind() != reflect.Array {
		return UNKNOWN, false
	}
	switch goType.Elem().Kind() {
	case reflect.Bool:
		if element.typeId == BOOL {
			return BOOL_ARRAY, true
		}
	case reflect.Int8:
		if element.typeId == INT8 {
			return INT8_ARRAY, true
		}
	case reflect.Uint8:
		if element.typeId == UINT8 {
			return BINARY, true
		}
	case reflect.Int16:
		if element.typeId == INT16 {
			return INT16_ARRAY, true
		}
	case reflect.Uint16:
		if element.typeId == UINT16 {
			if goType.Elem() == float16Type {
				return FLOAT16_ARRAY, true
			}
			if goType.Elem() == bfloat16Type {
				return BFLOAT16_ARRAY, true
			}
			return UINT16_ARRAY, true
		}
	case reflect.Int32:
		if element.typeId == INT32 {
			return INT32_ARRAY, true
		}
	case reflect.Uint32:
		if element.typeId == UINT32 {
			return UINT32_ARRAY, true
		}
	case reflect.Int64, reflect.Int:
		if element.typeId == INT64 {
			return INT64_ARRAY, true
		}
	case reflect.Uint64, reflect.Uint:
		if element.typeId == UINT64 {
			return UINT64_ARRAY, true
		}
	case reflect.Float32:
		if element.typeId == FLOAT32 {
			return FLOAT32_ARRAY, true
		}
	case reflect.Float64:
		if element.typeId == FLOAT64 {
			return FLOAT64_ARRAY, true
		}
	}
	return UNKNOWN, false
}
