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
	"github.com/apache/fory/go/fory/meta"
	"github.com/spaolacci/murmur3"
	"reflect"
)

const (
	META_SIZE_MASK       = 0xFFF
	COMPRESS_META_FLAG   = 0b1 << 13
	HAS_FIELDS_META_FLAG = 0b1 << 12
	NUM_HASH_BITS        = 50
)

/*
TypeDef represents a transportable value object containing type information and field definitions.
typeDef are layout as following:
  - first 8 bytes: global header (50 bits hash + 1 bit compress flag + write fields meta + 12 bits meta size)
  - next 1 byte: meta header (2 bits reserved + 1 bit register by name flag + 5 bits num fields)
  - next variable bytes: type id (varint) or ns name + type name
  - next variable bytes: field definitions (see below)
*/
type TypeDef struct {
	typeId         uint32 // Full composite type ID (userId << 8 | internalTypeId)
	nsName         *MetaStringBytes
	typeName       *MetaStringBytes
	compressed     bool
	registerByName bool
	fieldDefs      []FieldDef
	encoded        []byte
	type_          reflect.Type
}

func NewTypeDef(typeId uint32, nsName, typeName *MetaStringBytes, registerByName, compressed bool, fieldDefs []FieldDef) *TypeDef {
	return &TypeDef{
		typeId:         typeId,
		nsName:         nsName,
		typeName:       typeName,
		compressed:     compressed,
		registerByName: registerByName,
		fieldDefs:      fieldDefs,
		encoded:        nil,
	}
}

func (td *TypeDef) writeTypeDef(buffer *ByteBuffer) {
	buffer.WriteBinary(td.encoded)
}

// buildTypeInfo constructs a TypeInfo from the TypeDef
func (td *TypeDef) buildTypeInfo() (TypeInfo, error) {
	type_ := td.type_

	var serializer Serializer
	if type_ == nil {
		// Unknown struct type - use skipStructSerializer to skip data
		serializer = &skipStructSerializer{
			fieldDefs: td.fieldDefs,
		}
	} else {
		// Known struct type - use structSerializer with fieldDefs
		serializer = &structSerializer{
			type_:     type_,
			fieldDefs: td.fieldDefs,
		}
	}

	info := TypeInfo{
		Type:         type_,
		TypeID:       int32(td.typeId), // TypeInfo uses int32, safe conversion from uint32
		Serializer:   serializer,
		PkgPathBytes: td.nsName,
		NameBytes:    td.typeName,
		IsDynamic:    type_ == nil, // Mark as dynamic if type is unknown
	}
	return info, nil
}

func readTypeDef(fory *Fory, buffer *ByteBuffer, header int64) (*TypeDef, error) {
	return decodeTypeDef(fory, buffer, header)
}

func skipTypeDef(buffer *ByteBuffer, header int64) {
	sz := int(header & META_SIZE_MASK)
	if sz == META_SIZE_MASK {
		sz += int(buffer.ReadVarUint32())
	}
	buffer.IncreaseReaderIndex(sz)
}

const BIG_NAME_THRESHOLD = 0b111111 // 6 bits for size when using 2 bits for encoding

// readPkgName reads package name from TypeDef (not the meta string format with dynamic IDs)
// Java format: 6 bits size | 2 bits encoding flags
// Package encodings: UTF_8=0, ALL_TO_LOWER_SPECIAL=1, LOWER_UPPER_DIGIT_SPECIAL=2
func readPkgName(buffer *ByteBuffer, namespaceDecoder *meta.Decoder) (string, error) {
	header := int(buffer.ReadInt8()) & 0xff
	encodingFlags := header & 0b11 // 2 bits for encoding
	size := header >> 2            // 6 bits for size
	if size == BIG_NAME_THRESHOLD {
		size = int(buffer.ReadVarUint32Small7()) + BIG_NAME_THRESHOLD
	}

	var encoding meta.Encoding
	switch encodingFlags {
	case 0:
		encoding = meta.UTF_8
	case 1:
		encoding = meta.ALL_TO_LOWER_SPECIAL
	case 2:
		encoding = meta.LOWER_UPPER_DIGIT_SPECIAL
	default:
		return "", fmt.Errorf("invalid package encoding flags: %d", encodingFlags)
	}

	data := make([]byte, size)
	if _, err := buffer.Read(data); err != nil {
		return "", err
	}

	return namespaceDecoder.Decode(data, encoding)
}

// readTypeName reads type name from TypeDef (not the meta string format with dynamic IDs)
// Java format: 6 bits size | 2 bits encoding flags
// TypeName encodings: UTF_8=0, LOWER_UPPER_DIGIT_SPECIAL=1, FIRST_TO_LOWER_SPECIAL=2, ALL_TO_LOWER_SPECIAL=3
func readTypeName(buffer *ByteBuffer, typeNameDecoder *meta.Decoder) (string, error) {
	header := int(buffer.ReadInt8()) & 0xff
	encodingFlags := header & 0b11 // 2 bits for encoding
	size := header >> 2            // 6 bits for size
	if size == BIG_NAME_THRESHOLD {
		size = int(buffer.ReadVarUint32Small7()) + BIG_NAME_THRESHOLD
	}

	var encoding meta.Encoding
	switch encodingFlags {
	case 0:
		encoding = meta.UTF_8
	case 1:
		encoding = meta.LOWER_UPPER_DIGIT_SPECIAL
	case 2:
		encoding = meta.FIRST_TO_LOWER_SPECIAL
	case 3:
		encoding = meta.ALL_TO_LOWER_SPECIAL
	default:
		return "", fmt.Errorf("invalid typename encoding flags: %d", encodingFlags)
	}

	data := make([]byte, size)
	if _, err := buffer.Read(data); err != nil {
		return "", err
	}

	return typeNameDecoder.Decode(data, encoding)
}

// buildTypeDef constructs a TypeDef from a value
func buildTypeDef(fory *Fory, value reflect.Value) (*TypeDef, error) {
	fieldDefs, err := buildFieldDefs(fory, value)
	if err != nil {
		return nil, fmt.Errorf("failed to extract field infos: %w", err)
	}

	info, err := fory.typeResolver.getTypeInfo(value, true)
	if err != nil {
		return nil, fmt.Errorf("failed to get type info for value %v: %w", value, err)
	}
	typeId := uint32(info.TypeID) // Use full uint32 type ID
	registerByName := IsNamespacedType(TypeId(typeId & 0xFF))
	typeDef := NewTypeDef(typeId, info.PkgPathBytes, info.NameBytes, registerByName, false, fieldDefs)

	// encoding the typeDef, and save the encoded bytes
	encoded, err := encodingTypeDef(fory.typeResolver, typeDef)
	if err != nil {
		return nil, fmt.Errorf("failed to encode class definition: %w", err)
	}

	typeDef.encoded = encoded
	return typeDef, nil
}

/*
FieldDef contains definition of a single field in a struct
field def layout as following:
  - first 1 byte: header (2 bits field name encoding + 4 bits size + nullability flag + ref tracking flag)
  - next variable bytes: FieldType info
  - next variable bytes: field name or tag id
*/
type FieldDef struct {
	name         string
	nameEncoding meta.Encoding
	nullable     bool
	trackingRef  bool
	fieldType    FieldType
}

// buildFieldDefs extracts field definitions from a struct value
func buildFieldDefs(fory *Fory, value reflect.Value) ([]FieldDef, error) {
	var fieldDefs []FieldDef

	type_ := value.Type()
	for i := 0; i < type_.NumField(); i++ {
		field := type_.Field(i)
		fieldValue := value.Field(i)

		var fieldInfo FieldDef
		fieldName := SnakeCase(field.Name)

		nameEncoding := fory.typeResolver.typeNameEncoder.ComputeEncodingWith(fieldName, fieldNameEncodings)

		ft, err := buildFieldType(fory, fieldValue)
		if err != nil {
			return nil, fmt.Errorf("failed to build field type for field %s: %w", fieldName, err)
		}
		fieldInfo = FieldDef{
			name:         fieldName,
			nameEncoding: nameEncoding,
			nullable:     nullable(field.Type),
			trackingRef:  fory.config.TrackRef,
			fieldType:    ft,
		}
		fieldDefs = append(fieldDefs, fieldInfo)
	}

	// Sort field definitions
	if len(fieldDefs) > 1 {
		// Extract serializers and names for sorting
		serializers := make([]Serializer, len(fieldDefs))
		fieldNames := make([]string, len(fieldDefs))
		for i, fieldDef := range fieldDefs {
			serializer, err := getFieldTypeSerializer(fory, fieldDef.fieldType)
			if err != nil {
				// If we can't get serializer, use nil (will be handled by sortFields)
				serializers[i] = nil
			} else {
				serializers[i] = serializer
			}
			fieldNames[i] = fieldDef.name
		}

		// Use existing sortFields function to get optimal order
		_, sortedNames := sortFields(fory.typeResolver, fieldNames, serializers)

		// Rebuild fieldInfos in the sorted order
		nameToFieldInfo := make(map[string]FieldDef)
		for _, fieldInfo := range fieldDefs {
			nameToFieldInfo[fieldInfo.name] = fieldInfo
		}

		sortedFieldInfos := make([]FieldDef, len(fieldDefs))
		for i, name := range sortedNames {
			sortedFieldInfos[i] = nameToFieldInfo[name]
		}

		fieldDefs = sortedFieldInfos
	}

	return fieldDefs, nil
}

// FieldType interface represents different field types, including object, collection, and map types
type FieldType interface {
	TypeId() TypeId
	write(*ByteBuffer)
	getTypeInfo(*Fory) (TypeInfo, error)                     // some serializer need typeinfo as well
	getTypeInfoWithResolver(*TypeResolver) (TypeInfo, error) // version that uses typeResolver directly
}

// BaseFieldType provides common functionality for field types
type BaseFieldType struct {
	typeId TypeId
}

func (b *BaseFieldType) TypeId() TypeId { return b.typeId }
func (b *BaseFieldType) write(buffer *ByteBuffer) {
	buffer.WriteVarUint32Small7(uint32(b.typeId))
}

func getFieldTypeSerializer(fory *Fory, ft FieldType) (Serializer, error) {
	typeInfo, err := ft.getTypeInfo(fory)
	if err != nil {
		return nil, err
	}
	return typeInfo.Serializer, nil
}

func getFieldTypeSerializerWithResolver(resolver *TypeResolver, ft FieldType) (Serializer, error) {
	typeInfo, err := ft.getTypeInfoWithResolver(resolver)
	if err != nil {
		return nil, err
	}
	return typeInfo.Serializer, nil
}

func (b *BaseFieldType) getTypeInfo(fory *Fory) (TypeInfo, error) {
	info, err := fory.typeResolver.getTypeInfoById(b.typeId)
	if err != nil {
		return TypeInfo{}, err
	}
	return info, nil
}

func (b *BaseFieldType) getTypeInfoWithResolver(resolver *TypeResolver) (TypeInfo, error) {
	info, err := resolver.getTypeInfoById(b.typeId)
	if err != nil {
		return TypeInfo{}, err
	}
	return info, nil
}

// readFieldType reads field type info from the buffer according to the TypeId
// This is called for top-level field types where flags are NOT embedded in the type ID
func readFieldType(buffer *ByteBuffer) (FieldType, error) {
	typeId := buffer.ReadVarUint32Small7()

	switch typeId {
	case LIST, SET:
		// For nested types, flags ARE embedded in the type ID
		elementType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read element type: %w", err)
		}
		return NewCollectionFieldType(TypeId(typeId), elementType), nil
	case MAP:
		// For nested types, flags ARE embedded in the type ID
		keyType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read key type: %w", err)
		}
		valueType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read value type: %w", err)
		}
		return NewMapFieldType(TypeId(typeId), keyType, valueType), nil
	case UNKNOWN, EXT, STRUCT, NAMED_STRUCT, COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT:
		return NewDynamicFieldType(TypeId(typeId)), nil
	}
	return NewSimpleFieldType(TypeId(typeId)), nil
}

// readFieldTypeWithFlags reads field type info where flags are embedded in the type ID
// Format: (typeId << 2) | (nullable ? 0b10 : 0) | (trackingRef ? 0b1 : 0)
func readFieldTypeWithFlags(buffer *ByteBuffer) (FieldType, error) {
	rawValue := buffer.ReadVarUint32Small7()
	// Extract flags (lower 2 bits)
	// trackingRef := (rawValue & 0b1) != 0  // Not used currently
	// nullable := (rawValue & 0b10) != 0    // Not used currently
	typeId := rawValue >> 2

	switch typeId {
	case LIST, SET:
		elementType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read element type: %w", err)
		}
		return NewCollectionFieldType(TypeId(typeId), elementType), nil
	case MAP:
		keyType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read key type: %w", err)
		}
		valueType, err := readFieldTypeWithFlags(buffer)
		if err != nil {
			return nil, fmt.Errorf("failed to read value type: %w", err)
		}
		return NewMapFieldType(TypeId(typeId), keyType, valueType), nil
	case UNKNOWN, EXT, STRUCT, NAMED_STRUCT, COMPATIBLE_STRUCT, NAMED_COMPATIBLE_STRUCT:
		return NewDynamicFieldType(TypeId(typeId)), nil
	}
	return NewSimpleFieldType(TypeId(typeId)), nil
}

// CollectionFieldType represents collection types like List, Slice
type CollectionFieldType struct {
	BaseFieldType
	elementType FieldType
}

func NewCollectionFieldType(typeId TypeId, elementType FieldType) *CollectionFieldType {
	return &CollectionFieldType{
		BaseFieldType: BaseFieldType{typeId: typeId},
		elementType:   elementType,
	}
}

func (c *CollectionFieldType) write(buffer *ByteBuffer) {
	c.BaseFieldType.write(buffer)
	c.elementType.write(buffer)
}

func (c *CollectionFieldType) getTypeInfo(f *Fory) (TypeInfo, error) {
	elemInfo, err := c.elementType.getTypeInfo(f)
	if err != nil {
		return TypeInfo{}, err
	}
	collectionType := reflect.SliceOf(elemInfo.Type)
	sliceSerializer := &sliceSerializer{elemInfo: elemInfo, declaredType: elemInfo.Type}
	return TypeInfo{Type: collectionType, Serializer: sliceSerializer}, nil
}

func (c *CollectionFieldType) getTypeInfoWithResolver(resolver *TypeResolver) (TypeInfo, error) {
	elemInfo, err := c.elementType.getTypeInfoWithResolver(resolver)
	if err != nil {
		return TypeInfo{}, err
	}
	collectionType := reflect.SliceOf(elemInfo.Type)
	sliceSerializer := &sliceSerializer{elemInfo: elemInfo, declaredType: elemInfo.Type}
	return TypeInfo{Type: collectionType, Serializer: sliceSerializer}, nil
}

// MapFieldType represents map types
type MapFieldType struct {
	BaseFieldType
	keyType   FieldType
	valueType FieldType
}

func NewMapFieldType(typeId TypeId, keyType, valueType FieldType) *MapFieldType {
	return &MapFieldType{
		BaseFieldType: BaseFieldType{typeId: typeId},
		keyType:       keyType,
		valueType:     valueType,
	}
}

func (m *MapFieldType) write(buffer *ByteBuffer) {
	m.BaseFieldType.write(buffer)
	m.keyType.write(buffer)
	m.valueType.write(buffer)
}

func (m *MapFieldType) getTypeInfo(f *Fory) (TypeInfo, error) {
	keyInfo, err := m.keyType.getTypeInfo(f)
	if err != nil {
		return TypeInfo{}, err
	}
	valueInfo, err := m.valueType.getTypeInfo(f)
	if err != nil {
		return TypeInfo{}, err
	}
	var mapType reflect.Type
	if keyInfo.Type != nil && valueInfo.Type != nil {
		mapType = reflect.MapOf(keyInfo.Type, valueInfo.Type)
	}
	mapSerializer := &mapSerializer{
		keySerializer:   keyInfo.Serializer,
		valueSerializer: valueInfo.Serializer,
	}
	return TypeInfo{Type: mapType, Serializer: mapSerializer}, nil
}

func (m *MapFieldType) getTypeInfoWithResolver(resolver *TypeResolver) (TypeInfo, error) {
	keyInfo, err := m.keyType.getTypeInfoWithResolver(resolver)
	if err != nil {
		return TypeInfo{}, err
	}
	valueInfo, err := m.valueType.getTypeInfoWithResolver(resolver)
	if err != nil {
		return TypeInfo{}, err
	}
	var mapType reflect.Type
	if keyInfo.Type != nil && valueInfo.Type != nil {
		mapType = reflect.MapOf(keyInfo.Type, valueInfo.Type)
	}
	mapSerializer := &mapSerializer{
		keySerializer:   keyInfo.Serializer,
		valueSerializer: valueInfo.Serializer,
	}
	return TypeInfo{Type: mapType, Serializer: mapSerializer}, nil
}

// SimpleFieldType represents object field types that aren't collection/map types
type SimpleFieldType struct {
	BaseFieldType
}

func NewSimpleFieldType(typeId TypeId) *SimpleFieldType {
	return &SimpleFieldType{
		BaseFieldType: BaseFieldType{
			typeId: typeId,
		},
	}
}

// DynamicFieldType represents a field type that is determined at runtime, like EXT or STRUCT
type DynamicFieldType struct {
	BaseFieldType
}

func NewDynamicFieldType(typeId TypeId) *DynamicFieldType {
	return &DynamicFieldType{
		BaseFieldType: BaseFieldType{
			typeId: typeId,
		},
	}
}

func (d *DynamicFieldType) getTypeInfo(fory *Fory) (TypeInfo, error) {
	// leave empty for runtime resolution, we not know the actual type here
	return TypeInfo{Type: reflect.TypeOf((*interface{})(nil)).Elem(), Serializer: nil}, nil
}

func (d *DynamicFieldType) getTypeInfoWithResolver(resolver *TypeResolver) (TypeInfo, error) {
	// Try to resolve the actual type from the resolver
	// The typeId might be a composite ID (customId << 8 + baseType)
	typeId := d.typeId

	// First try direct lookup
	info, err := resolver.getTypeInfoById(typeId)
	if err == nil {
		return info, nil
	}

	// If direct lookup fails and it's a composite ID, extract the custom ID
	baseType := typeId & 0xFF
	if baseType == NAMED_STRUCT || baseType == NAMED_COMPATIBLE_STRUCT || baseType == COMPATIBLE_STRUCT {
		customId := typeId >> 8
		if customId > 0 {
			// Try looking up by the custom ID
			info, err = resolver.getTypeInfoById(TypeId(customId))
			if err == nil {
				return info, nil
			}
		}
	}

	// Fallback to interface{} for unknown types
	return TypeInfo{Type: reflect.TypeOf((*interface{})(nil)).Elem(), Serializer: nil}, nil
}

// buildFieldType builds field type from reflect.Type, handling collection, map recursively
func buildFieldType(fory *Fory, fieldValue reflect.Value) (FieldType, error) {
	fieldType := fieldValue.Type()
	// Handle Interface type, we can't determine the actual type here, so leave it as dynamic type
	if fieldType.Kind() == reflect.Interface {
		return NewDynamicFieldType(UNKNOWN), nil
	}

	// Handle slice and array types BEFORE getTypeInfo to avoid anonymous type errors
	// For fixed-size arrays with primitive elements, use primitive array type IDs (INT16_ARRAY, etc.)
	// For slices and arrays with non-primitive elements, use collection format
	if fieldType.Kind() == reflect.Slice || fieldType.Kind() == reflect.Array {
		elemType := fieldType.Elem()

		// Check if element is a primitive type that maps to a primitive array type ID
		// Only fixed-size arrays use primitive array format; slices always use LIST
		if fieldType.Kind() == reflect.Array {
			switch elemType.Kind() {
			case reflect.Int8:
				return NewSimpleFieldType(INT8_ARRAY), nil
			case reflect.Int16:
				return NewSimpleFieldType(INT16_ARRAY), nil
			case reflect.Int32:
				return NewSimpleFieldType(INT32_ARRAY), nil
			case reflect.Int64:
				return NewSimpleFieldType(INT64_ARRAY), nil
			case reflect.Float32:
				return NewSimpleFieldType(FLOAT32_ARRAY), nil
			case reflect.Float64:
				return NewSimpleFieldType(FLOAT64_ARRAY), nil
			}
		}

		// For slices and non-primitive arrays, use collection format
		elemValue := reflect.Zero(elemType)
		elementFieldType, err := buildFieldType(fory, elemValue)
		if err != nil {
			return nil, fmt.Errorf("failed to build element field type: %w", err)
		}

		return NewCollectionFieldType(LIST, elementFieldType), nil
	}

	// Handle map types BEFORE getTypeInfo to avoid anonymous type errors
	if fieldType.Kind() == reflect.Map {
		// Create zero values for key and value types
		keyType := fieldType.Key()
		valueType := fieldType.Elem()
		keyValue := reflect.Zero(keyType)
		valueValue := reflect.Zero(valueType)

		keyFieldType, err := buildFieldType(fory, keyValue)
		if err != nil {
			return nil, fmt.Errorf("failed to build key field type: %w", err)
		}

		valueFieldType, err := buildFieldType(fory, valueValue)
		if err != nil {
			return nil, fmt.Errorf("failed to build value field type: %w", err)
		}

		return NewMapFieldType(MAP, keyFieldType, valueFieldType), nil
	}

	// Now get type info for other types (primitives, structs, etc.)
	var typeId TypeId
	typeInfo, err := fory.typeResolver.getTypeInfo(fieldValue, true)
	if err != nil {
		return nil, err
	}
	typeId = TypeId(typeInfo.TypeID)

	if isUserDefinedType(typeId) {
		return NewDynamicFieldType(typeId), nil
	}

	return NewSimpleFieldType(typeId), nil
}

const (
	SmallNumFieldsThreshold = 31
	REGISTER_BY_NAME_FLAG   = 0b1 << 5
	FieldNameSizeThreshold  = 15
)

// Encoding `UTF8/ALL_TO_LOWER_SPECIAL/LOWER_UPPER_DIGIT_SPECIAL/TAG_ID` for fieldName
var fieldNameEncodings = []meta.Encoding{
	meta.UTF_8,
	meta.ALL_TO_LOWER_SPECIAL,
	meta.LOWER_UPPER_DIGIT_SPECIAL,
	// todo: add support for TAG_ID encoding
}

func getFieldNameEncodingIndex(encoding meta.Encoding) int {
	for i, enc := range fieldNameEncodings {
		if enc == encoding {
			return i
		}
	}
	return 0 // Default to UTF_8 if not found
}

/*
encodingTypeDef encodes a TypeDef into binary format according to the specification
typeDef are layout as following:
- first 8 bytes: global header (50 bits hash + 1 bit compress flag + write fields meta + 12 bits meta size)
- next 1 byte: meta header (2 bits reserved + 1 bit register by name flag + 5 bits num fields)
- next variable bytes: type id (varint) or ns name + type name
- next variable bytes: field defs (see below)
*/
// writeSimpleName writes namespace using simple format (for TypeDef)
// Format: 1 byte header (6 bits size | 2 bits encoding flags) + data bytes
// This matches Java's format in ClassDefEncoder.writeName()
func writeSimpleName(buffer *ByteBuffer, metaBytes *MetaStringBytes, encoder *meta.Encoder) error {
	if metaBytes == nil || len(metaBytes.Data) == 0 {
		// WriteData header for empty namespace
		buffer.WriteByte(0)
		return nil
	}

	data := metaBytes.Data
	encoding := metaBytes.Encoding

	// Get encoding flags (0-2) - Java uses 2 bits for package encoding:
	// 0=UTF8, 1=ALL_TO_LOWER_SPECIAL, 2=LOWER_UPPER_DIGIT_SPECIAL
	var encodingFlags byte
	switch encoding {
	case meta.UTF_8:
		encodingFlags = 0
	case meta.ALL_TO_LOWER_SPECIAL:
		encodingFlags = 1
	case meta.LOWER_UPPER_DIGIT_SPECIAL:
		encodingFlags = 2
	default:
		return fmt.Errorf("unsupported namespace encoding: %v", encoding)
	}

	size := len(data)
	if size >= BIG_NAME_THRESHOLD {
		// Size doesn't fit in 6 bits, write BIG_NAME_THRESHOLD and then varuint
		header := byte((BIG_NAME_THRESHOLD << 2) | int(encodingFlags))
		buffer.WriteByte(header)
		buffer.WriteVarUint32Small7(uint32(size - BIG_NAME_THRESHOLD))
	} else {
		// Size fits in 6 bits (6 bits for size, 2 bits for encoding)
		header := byte((size << 2) | int(encodingFlags))
		buffer.WriteByte(header)
	}

	buffer.Write(data)
	return nil
}

// writeSimpleTypeName writes typename using simple format (for TypeDef)
// Format: 1 byte header (6 bits size | 2 bits encoding flags) + data bytes
// This matches Java's format in ClassDefEncoder.writeName()
func writeSimpleTypeName(buffer *ByteBuffer, metaBytes *MetaStringBytes, encoder *meta.Encoder) error {
	if metaBytes == nil || len(metaBytes.Data) == 0 {
		// WriteData header for empty typename (shouldn't happen)
		buffer.WriteByte(0)
		return nil
	}

	data := metaBytes.Data
	encoding := metaBytes.Encoding

	// Get encoding flags (0-3) - Java uses 2 bits for typename encoding:
	// 0=UTF8, 1=LOWER_UPPER_DIGIT_SPECIAL, 2=FIRST_TO_LOWER_SPECIAL, 3=ALL_TO_LOWER_SPECIAL
	var encodingFlags byte
	switch encoding {
	case meta.UTF_8:
		encodingFlags = 0
	case meta.LOWER_UPPER_DIGIT_SPECIAL:
		encodingFlags = 1
	case meta.FIRST_TO_LOWER_SPECIAL:
		encodingFlags = 2
	case meta.ALL_TO_LOWER_SPECIAL:
		encodingFlags = 3
	default:
		return fmt.Errorf("unsupported typename encoding: %v", encoding)
	}

	size := len(data)
	if size >= BIG_NAME_THRESHOLD {
		// Size doesn't fit in 6 bits, write BIG_NAME_THRESHOLD and then varuint
		header := byte((BIG_NAME_THRESHOLD << 2) | int(encodingFlags))
		buffer.WriteByte(header)
		buffer.WriteVarUint32Small7(uint32(size - BIG_NAME_THRESHOLD))
	} else {
		// Size fits in 6 bits (6 bits for size, 2 bits for encoding)
		header := byte((size << 2) | int(encodingFlags))
		buffer.WriteByte(header)
	}

	buffer.Write(data)
	return nil
}

func encodingTypeDef(typeResolver *TypeResolver, typeDef *TypeDef) ([]byte, error) {
	buffer := NewByteBuffer(nil)

	if err := writeMetaHeader(buffer, typeDef); err != nil {
		return nil, fmt.Errorf("failed to write meta header: %w", err)
	}

	if typeDef.registerByName {
		// WriteData namespace and typename using simple format (NOT meta string format)
		// Simple format: 1 byte header (6 bits size | 2 bits encoding) + data bytes
		if err := writeSimpleName(buffer, typeDef.nsName, typeResolver.namespaceEncoder); err != nil {
			return nil, fmt.Errorf("failed to write namespace: %w", err)
		}
		if err := writeSimpleTypeName(buffer, typeDef.typeName, typeResolver.typeNameEncoder); err != nil {
			return nil, fmt.Errorf("failed to write typename: %w", err)
		}
	} else {
		// Java uses writeVarUint32 for type ID (unsigned varint)
		// typeDef.typeId is already int32, no need for conversion
		buffer.WriteVarUint32(uint32(typeDef.typeId))
	}

	if err := writeFieldDefs(typeResolver, buffer, typeDef.fieldDefs); err != nil {
		return nil, fmt.Errorf("failed to write fields def: %w", err)
	}

	result, err := prependGlobalHeader(buffer, false, len(typeDef.fieldDefs) > 0)
	if err != nil {
		return nil, fmt.Errorf("failed to write global binary header: %w", err)
	}

	return result.GetByteSlice(0, result.WriterIndex()), nil
}

// prependGlobalHeader writes the 8-byte global header
func prependGlobalHeader(buffer *ByteBuffer, isCompressed bool, hasFieldsMeta bool) (*ByteBuffer, error) {
	var header uint64
	metaSize := buffer.WriterIndex()

	hashValue := murmur3.Sum64WithSeed(buffer.GetByteSlice(0, metaSize), 47)
	header |= hashValue << (64 - NUM_HASH_BITS)

	if hasFieldsMeta {
		header |= HAS_FIELDS_META_FLAG
	}

	if isCompressed {
		header |= COMPRESS_META_FLAG
	}

	if metaSize < META_SIZE_MASK {
		header |= uint64(metaSize) & 0xFFF
	} else {
		header |= 0xFFF // Set to max value, actual size will follow
	}

	result := NewByteBuffer(make([]byte, metaSize+8))
	result.WriteInt64(int64(header))

	if metaSize >= META_SIZE_MASK {
		result.WriteVarUint32(uint32(metaSize - META_SIZE_MASK))
	}
	result.WriteBinary(buffer.GetByteSlice(0, metaSize))

	return result, nil
}

// writeMetaHeader writes the 1-byte meta header
func writeMetaHeader(buffer *ByteBuffer, typeDef *TypeDef) error {
	// 2 bits reserved + 1 bit register by name flag + 5 bits num fields
	offset := buffer.writerIndex
	if err := buffer.WriteByte(0xFF); err != nil {
		return err
	}
	fieldInfos := typeDef.fieldDefs
	header := len(fieldInfos)
	if header > SmallNumFieldsThreshold {
		header = SmallNumFieldsThreshold
		buffer.WriteVarUint32(uint32(len(fieldInfos) - SmallNumFieldsThreshold))
	}
	if typeDef.registerByName {
		header |= REGISTER_BY_NAME_FLAG
	}

	buffer.PutUint8(offset, uint8(header))
	return nil
}

// writeFieldDefs writes field definitions according to the specification
// field def layout as following:
//   - first 1 byte: header (2 bits field name encoding + 4 bits size + nullability flag + ref tracking flag)
//   - next variable bytes: FieldType info
//   - next variable bytes: field name or tag id
func writeFieldDefs(typeResolver *TypeResolver, buffer *ByteBuffer, fieldDefs []FieldDef) error {
	for _, field := range fieldDefs {
		if err := writeFieldDef(typeResolver, buffer, field); err != nil {
			return fmt.Errorf("failed to write field def for field %s: %w", field.name, err)
		}
	}
	return nil
}

// writeFieldDef writes a single field's definition
func writeFieldDef(typeResolver *TypeResolver, buffer *ByteBuffer, field FieldDef) error {
	// WriteData field header
	// 2 bits field name encoding + 4 bits size + nullability flag + ref tracking flag
	offset := buffer.writerIndex
	if err := buffer.WriteByte(0xFF); err != nil {
		return err
	}
	var header uint8
	if field.trackingRef {
		header |= 0b1
	}
	if field.nullable {
		header |= 0b10
	}
	// store index of encoding in the 2 highest bits
	encodingFlag := byte(getFieldNameEncodingIndex(field.nameEncoding))
	header |= encodingFlag << 6
	metaString, err := typeResolver.typeNameEncoder.EncodeWithEncoding(field.name, field.nameEncoding)
	if err != nil {
		return err
	}
	nameLen := len(metaString.GetEncodedBytes())
	if nameLen < FieldNameSizeThreshold {
		header |= uint8((nameLen-1)&0x0F) << 2 // 1-based encoding
	} else {
		header |= 0x0F << 2 // Max value, actual length will follow
		buffer.WriteVarUint32(uint32(nameLen - FieldNameSizeThreshold))
	}
	buffer.PutUint8(offset, header)

	// WriteData field type
	field.fieldType.write(buffer)

	// todo: support tag id
	// write field name
	if _, err := buffer.Write(metaString.GetEncodedBytes()); err != nil {
		return err
	}
	return nil
}

/*
decodeTypeDef decodes a TypeDef from the buffer
typeDef are layout as following:
  - first 8 bytes: global header (50 bits hash + 1 bit compress flag + write fields meta + 12 bits meta size)
  - next 1 byte: meta header (2 bits reserved + 1 bit register by name flag + 5 bits num fields)
  - next variable bytes: type id (varint) or ns name + type name
  - next variable bytes: field definitions (see below)
*/
func decodeTypeDef(fory *Fory, buffer *ByteBuffer, header int64) (*TypeDef, error) {
	// ReadData 8-byte global header
	globalHeader := header
	hasFieldsMeta := (globalHeader & HAS_FIELDS_META_FLAG) != 0
	isCompressed := (globalHeader & COMPRESS_META_FLAG) != 0
	metaSize := int(globalHeader & META_SIZE_MASK)
	if metaSize == META_SIZE_MASK {
		metaSize += int(buffer.ReadVarUint32())
	}

	// Store the encoded bytes for the TypeDef (including meta header and metadata)
	// todo: handle compression if is_compressed is true
	if isCompressed {
	}
	encoded := buffer.ReadBinary(metaSize)
	metaBuffer := NewByteBuffer(encoded)

	// ReadData 1-byte meta header
	metaHeaderByte, err := metaBuffer.ReadByte()
	if err != nil {
		return nil, err
	}
	// Extract field count from lower 5 bits
	fieldCount := int(metaHeaderByte & SmallNumFieldsThreshold)
	if fieldCount == SmallNumFieldsThreshold {
		fieldCount += int(metaBuffer.ReadVarUint32())
	}
	registeredByName := (metaHeaderByte & REGISTER_BY_NAME_FLAG) != 0

	// ReadData name or type ID according to the registerByName flag
	var typeId uint32
	var nsBytes, nameBytes *MetaStringBytes
	var type_ reflect.Type
	if registeredByName {
		// ReadData namespace and type name for namespaced types
		// NOTE: TypeDefs use simple name format, not meta string format with dynamic IDs
		// Format: 1 byte header (6 bits size | 2 bits encoding flags) + data bytes
		// ReadData namespace
		nsHeader := int(metaBuffer.ReadInt8()) & 0xff
		nsEncodingFlags := nsHeader & 0b11 // 2 bits for encoding
		nsSize := nsHeader >> 2            // 6 bits for size
		if nsSize == BIG_NAME_THRESHOLD {
			nsSize = int(metaBuffer.ReadVarUint32Small7()) + BIG_NAME_THRESHOLD
		}

		// Java pkg encoding: 0=UTF8, 1=ALL_TO_LOWER_SPECIAL, 2=LOWER_UPPER_DIGIT_SPECIAL
		var nsEncoding meta.Encoding
		switch nsEncodingFlags {
		case 0:
			nsEncoding = meta.UTF_8
		case 1:
			nsEncoding = meta.ALL_TO_LOWER_SPECIAL
		case 2:
			nsEncoding = meta.LOWER_UPPER_DIGIT_SPECIAL
		default:
			return nil, fmt.Errorf("invalid package encoding flags: %d", nsEncodingFlags)
		}
		nsData := make([]byte, nsSize)
		if _, err := metaBuffer.Read(nsData); err != nil {
			return nil, fmt.Errorf("failed to read namespace data: %w", err)
		}

		// ReadData typename
		// Format: 1 byte header (6 bits size | 2 bits encoding flags) + data bytes
		typeHeader := int(metaBuffer.ReadInt8()) & 0xff
		typeEncodingFlags := typeHeader & 0b11 // 2 bits for encoding
		typeSize := typeHeader >> 2            // 6 bits for size
		if typeSize == BIG_NAME_THRESHOLD {
			typeSize = int(metaBuffer.ReadVarUint32Small7()) + BIG_NAME_THRESHOLD
		}

		// Java typename encoding: 0=UTF8, 1=LOWER_UPPER_DIGIT_SPECIAL, 2=FIRST_TO_LOWER_SPECIAL, 3=ALL_TO_LOWER_SPECIAL
		var typeEncoding meta.Encoding
		switch typeEncodingFlags {
		case 0:
			typeEncoding = meta.UTF_8
		case 1:
			typeEncoding = meta.LOWER_UPPER_DIGIT_SPECIAL
		case 2:
			typeEncoding = meta.FIRST_TO_LOWER_SPECIAL
		case 3:
			typeEncoding = meta.ALL_TO_LOWER_SPECIAL
		default:
			return nil, fmt.Errorf("invalid typename encoding flags: %d", typeEncodingFlags)
		}
		typeData := make([]byte, typeSize)
		if _, err := metaBuffer.Read(typeData); err != nil {
			return nil, fmt.Errorf("failed to read typename data: %w", err)
		}

		// Create MetaStringBytes directly from the read data
		// Compute hash for namespace
		nsHash := ComputeMetaStringHash(nsData, nsEncoding)
		nsBytes = &MetaStringBytes{
			Data:     nsData,
			Encoding: nsEncoding,
			Hashcode: nsHash,
		}

		// Compute hash for typename
		typeHash := ComputeMetaStringHash(typeData, typeEncoding)
		nameBytes = &MetaStringBytes{
			Data:     typeData,
			Encoding: typeEncoding,
			Hashcode: typeHash,
		}

		info, exists := fory.typeResolver.nsTypeToTypeInfo[nsTypeKey{nsBytes.Hashcode, nameBytes.Hashcode}]
		if !exists {
			// Try fallback: decode strings and look up by name
			ns, _ := fory.typeResolver.namespaceDecoder.Decode(nsBytes.Data, nsBytes.Encoding)
			typeName, _ := fory.typeResolver.typeNameDecoder.Decode(nameBytes.Data, nameBytes.Encoding)
			nameKey := [2]string{ns, typeName}

			if fallbackInfo, fallbackExists := fory.typeResolver.namedTypeToTypeInfo[nameKey]; fallbackExists {
				info = fallbackInfo
				exists = true
				fory.typeResolver.nsTypeToTypeInfo[nsTypeKey{nsBytes.Hashcode, nameBytes.Hashcode}] = info
			}
		}
		if exists {
			// TypeDef is always for value types, but nsTypeToTypeInfo may have pointer type
			// if pointer type was registered after value type. Normalize to value type.
			type_ = info.Type
			if type_.Kind() == reflect.Ptr {
				type_ = type_.Elem()
			}
			typeId = uint32(info.TypeID)
		} else {
			// Type not registered - use NAMED_STRUCT as default typeId
			// The type_ will remain nil and will be set from field definitions later
			typeId = uint32(NAMED_STRUCT)
			type_ = nil
		}
	} else {
		// Java uses writeVarUint32 for type ID in TypeDef
		// The type ID is a composite: (userID << 8) | internalTypeID
		typeId = metaBuffer.ReadVarUint32()
		// Try to get the type from registry using the full type ID
		if info, exists := fory.typeResolver.typeIDToTypeInfo[int32(typeId)]; exists {
			type_ = info.Type
		} else {
			//Type not registered - will be built from field definitions
			type_ = nil
		}
	}

	// ReadData fields information
	fieldInfos := make([]FieldDef, fieldCount)
	if hasFieldsMeta {
		for i := 0; i < fieldCount; i++ {
			fieldInfo, err := readFieldDef(fory.typeResolver, metaBuffer)
			if err != nil {
				return nil, fmt.Errorf("failed to read field def %d: %w", i, err)
			}
			fieldInfos[i] = fieldInfo
		}
	}

	// Create TypeDef
	typeDef := NewTypeDef(typeId, nsBytes, nameBytes, registeredByName, isCompressed, fieldInfos)
	typeDef.encoded = encoded
	typeDef.type_ = type_

	return typeDef, nil
}

/*
readFieldDef reads a single field's definition from the buffer
field def layout as following:
  - first 1 byte: header (2 bits field name encoding + 4 bits size + nullability flag + ref tracking flag)
  - next variable bytes: FieldType info
  - next variable bytes: field name or tag id
*/
func readFieldDef(typeResolver *TypeResolver, buffer *ByteBuffer) (FieldDef, error) {
	// ReadData field header
	headerByte, err := buffer.ReadByte()
	if err != nil {
		return FieldDef{}, fmt.Errorf("failed to read field header: %w", err)
	}

	// Resolve the header
	nameEncodingFlag := (headerByte >> 6) & 0b11
	nameEncoding := fieldNameEncodings[nameEncodingFlag]
	nameLen := int((headerByte >> 2) & 0x0F)
	refTracking := (headerByte & 0b1) != 0
	isNullable := (headerByte & 0b10) != 0
	if nameLen == 0x0F {
		nameLen = FieldNameSizeThreshold + int(buffer.ReadVarUint32())
	} else {
		nameLen++ // Adjust for 1-based encoding
	}

	// reading field type
	ft, err := readFieldType(buffer)
	if err != nil {
		return FieldDef{}, err
	}

	// Reading field name based on encoding
	nameBytes := buffer.ReadBinary(nameLen)
	fieldName, err := typeResolver.typeNameDecoder.Decode(nameBytes, nameEncoding)
	if err != nil {
		return FieldDef{}, fmt.Errorf("failed to decode field name: %w", err)
	}

	return FieldDef{
		name:         fieldName,
		nameEncoding: nameEncoding,
		fieldType:    ft,
		nullable:     isNullable,
		trackingRef:  refTracking,
	}, nil
}
