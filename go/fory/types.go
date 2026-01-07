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

import "reflect"

type TypeId = int16

const (
	// UNKNOWN Unknown/polymorphic type marker
	UNKNOWN = 0
	// BOOL Boolean as 1 bit LSB bit-packed ordering
	BOOL = 1
	// INT8 Signed 8-bit little-endian integer
	INT8 = 2
	// INT16 Signed 16-bit little-endian integer
	INT16 = 3
	// INT32 Signed 32-bit little-endian integer
	INT32 = 4
	// VARINT32 a 32-bit signed integer which uses fory var_int32 encoding
	VARINT32 = 5
	// INT64 Signed 64-bit little-endian integer
	INT64 = 6
	// VARINT64 a 64-bit signed integer which uses fory PVL encoding
	VARINT64 = 7
	// TAGGED_INT64 a 64-bit signed integer which uses fory hybrid encoding
	TAGGED_INT64 = 8
	// UINT8 Unsigned 8-bit little-endian integer
	UINT8 = 9
	// UINT16 Unsigned 16-bit little-endian integer
	UINT16 = 10
	// UINT32 Unsigned 32-bit little-endian integer
	UINT32 = 11
	// VAR_UINT32 a 32-bit unsigned integer which uses fory var_uint32 encoding
	VAR_UINT32 = 12
	// UINT64 Unsigned 64-bit little-endian integer
	UINT64 = 13
	// VAR_UINT64 a 64-bit unsigned integer which uses fory var_uint64 encoding
	VAR_UINT64 = 14
	// TAGGED_UINT64 a 64-bit unsigned integer which uses fory hybrid encoding
	TAGGED_UINT64 = 15
	// FLOAT16 2-byte floating point value
	FLOAT16 = 16
	// FLOAT32 4-byte floating point value
	FLOAT32 = 17
	// FLOAT64 8-byte floating point value
	FLOAT64 = 18
	// STRING UTF8 variable-length string as List<Char>
	STRING = 19
	// LIST A list of some logical data type
	LIST = 20
	// SET an unordered set of unique elements
	SET = 21
	// MAP Map a repeated struct logical type
	MAP = 22
	// ENUM a data type consisting of a set of named values
	ENUM = 23
	// NAMED_ENUM an enum whose value will be serialized as the registered name
	NAMED_ENUM = 24
	// STRUCT a morphic(final) type serialized by Fory Struct serializer
	STRUCT = 25
	// COMPATIBLE_STRUCT a morphic(final) type serialized by Fory compatible Struct serializer
	COMPATIBLE_STRUCT = 26
	// NAMED_STRUCT a struct whose type mapping will be encoded as a name
	NAMED_STRUCT = 27
	// NAMED_COMPATIBLE_STRUCT a compatible_struct whose type mapping will be encoded as a name
	NAMED_COMPATIBLE_STRUCT = 28
	// EXT a type which will be serialized by a customized serializer
	EXT = 29
	// NAMED_EXT an ext type whose type mapping will be encoded as a name
	NAMED_EXT = 30
	// UNION an union type that can hold different types of values
	UNION = 31
	// NONE a null value with no data
	NONE = 32
	// DURATION Measure of elapsed time in either seconds milliseconds microseconds
	DURATION = 33
	// TIMESTAMP Exact timestamp encoded with int64 since UNIX epoch
	TIMESTAMP = 34
	// LOCAL_DATE a naive date without timezone
	LOCAL_DATE = 35
	// DECIMAL Precision- and scale-based decimal type
	DECIMAL = 36
	// BINARY Variable-length bytes (no guarantee of UTF8-ness)
	BINARY = 37
	// ARRAY a multidimensional array which every sub-array can have different sizes but all have the same type
	ARRAY = 38
	// BOOL_ARRAY one dimensional bool array
	BOOL_ARRAY = 39
	// INT8_ARRAY one dimensional int8 array
	INT8_ARRAY = 40
	// INT16_ARRAY one dimensional int16 array
	INT16_ARRAY = 41
	// INT32_ARRAY one dimensional int32 array
	INT32_ARRAY = 42
	// INT64_ARRAY one dimensional int64 array
	INT64_ARRAY = 43
	// UINT8_ARRAY one dimensional uint8 array
	UINT8_ARRAY = 44
	// UINT16_ARRAY one dimensional uint16 array
	UINT16_ARRAY = 45
	// UINT32_ARRAY one dimensional uint32 array
	UINT32_ARRAY = 46
	// UINT64_ARRAY one dimensional uint64 array
	UINT64_ARRAY = 47
	// FLOAT16_ARRAY one dimensional float16 array
	FLOAT16_ARRAY = 48
	// FLOAT32_ARRAY one dimensional float32 array
	FLOAT32_ARRAY = 49
	// FLOAT64_ARRAY one dimensional float64 array
	FLOAT64_ARRAY = 50
)

// IsNamespacedType checks whether the given type ID is a namespace type
func IsNamespacedType(typeID TypeId) bool {
	switch typeID & 0xFF {
	case NAMED_EXT, NAMED_ENUM, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT:
		return true
	default:
		return false
	}
}

// NeedsTypeMetaWrite checks whether a type needs additional type meta written after type ID
// This includes namespaced types and struct types that need meta share in compatible mode
func NeedsTypeMetaWrite(typeID TypeId) bool {
	internalID := typeID & 0xFF
	switch TypeId(internalID) {
	case NAMED_EXT, NAMED_ENUM, NAMED_STRUCT, NAMED_COMPATIBLE_STRUCT, COMPATIBLE_STRUCT, STRUCT:
		return true
	default:
		return false
	}
}

func isPrimitiveType(typeID int16) bool {
	switch typeID {
	case BOOL,
		INT8,
		INT16,
		INT32,
		VARINT32,
		INT64,
		VARINT64,
		TAGGED_INT64,
		UINT8,
		UINT16,
		UINT32,
		VAR_UINT32,
		UINT64,
		VAR_UINT64,
		TAGGED_UINT64,
		FLOAT16,
		FLOAT32,
		FLOAT64:
		return true
	default:
		return false
	}
}

// NeedWriteRef returns whether a type with the given type ID needs reference tracking.
// Primitive types, strings, and time types don't need reference tracking.
// Collections, structs, and other complex types need reference tracking.
func NeedWriteRef(typeID TypeId) bool {
	switch typeID {
	case BOOL, INT8, INT16, INT32, INT64, VARINT32, VARINT64, TAGGED_INT64,
		FLOAT32, FLOAT64, FLOAT16,
		STRING, TIMESTAMP, LOCAL_DATE, DURATION:
		return false
	default:
		return true
	}
}

func isListType(typeID int16) bool {
	return typeID == LIST
}

func isSetType(typeID int16) bool {
	return typeID == SET
}

func isMapType(typeID int16) bool {
	return typeID == MAP
}

func isCollectionType(typeID int16) bool {
	return typeID == LIST || typeID == SET || typeID == MAP
}

func isPrimitiveArrayType(typeID int16) bool {
	switch typeID {
	case BOOL_ARRAY,
		INT8_ARRAY,
		INT16_ARRAY,
		INT32_ARRAY,
		INT64_ARRAY,
		FLOAT32_ARRAY,
		FLOAT64_ARRAY:
		return true
	default:
		return false
	}
}

var primitiveTypeSizes = map[int16]int{
	BOOL:          1,
	INT8:          1,
	UINT8:         1,
	INT16:         2,
	UINT16:        2,
	FLOAT16:       2,
	INT32:         4,
	VARINT32:      4,
	UINT32:        4,
	VAR_UINT32:    4,
	FLOAT32:       4,
	INT64:         8,
	VARINT64:      8,
	TAGGED_INT64:  8,
	UINT64:        8,
	VAR_UINT64:    8,
	TAGGED_UINT64: 8,
	FLOAT64:       8,
}

func getPrimitiveTypeSize(typeID int16) int {
	if sz, ok := primitiveTypeSizes[typeID]; ok {
		return sz
	}
	return -1
}

func isUserDefinedType(typeID int16) bool {
	id := int(typeID & 0xff)
	return id == STRUCT ||
		id == COMPATIBLE_STRUCT ||
		id == NAMED_STRUCT ||
		id == NAMED_COMPATIBLE_STRUCT ||
		id == EXT ||
		id == NAMED_EXT ||
		id == ENUM ||
		id == NAMED_ENUM
}

// ============================================================================
// DispatchId for switch-based fast path (avoids interface virtual method cost)
// ============================================================================

// DispatchId identifies concrete Go types for optimized serialization dispatch
type DispatchId uint8

const (
	UnknowDispatchId DispatchId = iota
	BoolDispatchId
	Int8DispatchId
	Int16DispatchId
	Int32DispatchId
	Int64DispatchId
	IntDispatchId
	Uint8DispatchId
	Uint16DispatchId
	Uint32DispatchId
	Uint64DispatchId
	UintDispatchId
	Float32DispatchId
	Float64DispatchId
	StringDispatchId
	ByteSliceDispatchId
	Int8SliceDispatchId
	Int16SliceDispatchId
	Int32SliceDispatchId
	Int64SliceDispatchId
	IntSliceDispatchId
	UintSliceDispatchId
	Float32SliceDispatchId
	Float64SliceDispatchId
	BoolSliceDispatchId
	StringSliceDispatchId
	StringStringMapDispatchId
	StringInt32MapDispatchId
	StringInt64MapDispatchId
	StringIntMapDispatchId
	StringFloat64MapDispatchId
	StringBoolMapDispatchId
	Int32Int32MapDispatchId
	Int64Int64MapDispatchId
	IntIntMapDispatchId
	EnumDispatchId // Enum types (both ENUM and NAMED_ENUM)
)

// GetDispatchId returns the DispatchId for a reflect.Type
func GetDispatchId(t reflect.Type) DispatchId {
	switch t.Kind() {
	case reflect.Bool:
		return BoolDispatchId
	case reflect.Int8:
		return Int8DispatchId
	case reflect.Int16:
		return Int16DispatchId
	case reflect.Int32:
		return Int32DispatchId
	case reflect.Int64:
		return Int64DispatchId
	case reflect.Int:
		return IntDispatchId
	case reflect.Uint8:
		return Uint8DispatchId
	case reflect.Uint16:
		return Uint16DispatchId
	case reflect.Uint32:
		return Uint32DispatchId
	case reflect.Uint64:
		return Uint64DispatchId
	case reflect.Uint:
		return UintDispatchId
	case reflect.Float32:
		return Float32DispatchId
	case reflect.Float64:
		return Float64DispatchId
	case reflect.String:
		return StringDispatchId
	case reflect.Slice:
		// Check for specific slice types
		switch t.Elem().Kind() {
		case reflect.Uint8:
			return ByteSliceDispatchId
		case reflect.Int8:
			return Int8SliceDispatchId
		case reflect.Int16:
			return Int16SliceDispatchId
		case reflect.Int32:
			return Int32SliceDispatchId
		case reflect.Int64:
			return Int64SliceDispatchId
		case reflect.Int:
			return IntSliceDispatchId
		case reflect.Uint:
			return UintSliceDispatchId
		case reflect.Float32:
			return Float32SliceDispatchId
		case reflect.Float64:
			return Float64SliceDispatchId
		case reflect.Bool:
			return BoolSliceDispatchId
		case reflect.String:
			return StringSliceDispatchId
		}
		return UnknowDispatchId
	case reflect.Map:
		// Check for specific common map types
		if t.Key().Kind() == reflect.String {
			switch t.Elem().Kind() {
			case reflect.String:
				return StringStringMapDispatchId
			case reflect.Int64:
				return StringInt64MapDispatchId
			case reflect.Int:
				return StringIntMapDispatchId
			case reflect.Float64:
				return StringFloat64MapDispatchId
			case reflect.Bool:
				return StringBoolMapDispatchId
			}
		} else if t.Key().Kind() == reflect.Int32 && t.Elem().Kind() == reflect.Int32 {
			return Int32Int32MapDispatchId
		} else if t.Key().Kind() == reflect.Int64 && t.Elem().Kind() == reflect.Int64 {
			return Int64Int64MapDispatchId
		} else if t.Key().Kind() == reflect.Int && t.Elem().Kind() == reflect.Int {
			return IntIntMapDispatchId
		}
		return UnknowDispatchId
	default:
		return UnknowDispatchId
	}
}

// IsPrimitiveTypeId checks if a type ID is a primitive type
func IsPrimitiveTypeId(typeId TypeId) bool {
	switch typeId {
	case BOOL, INT8, INT16, INT32, VARINT32, INT64, VARINT64, TAGGED_INT64,
		UINT8, UINT16, UINT32, VAR_UINT32, UINT64, VAR_UINT64, TAGGED_UINT64,
		FLOAT16, FLOAT32, FLOAT64, STRING:
		return true
	default:
		return false
	}
}

// isFixedSizePrimitive returns true for non-nullable fixed-size primitives
func isFixedSizePrimitive(staticId DispatchId, referencable bool) bool {
	if referencable {
		return false
	}
	switch staticId {
	case BoolDispatchId, Int8DispatchId, Uint8DispatchId, Int16DispatchId, Uint16DispatchId,
		Float32DispatchId, Float64DispatchId:
		return true
	default:
		return false
	}
}

// isVarintPrimitive returns true for non-nullable varint primitives
func isVarintPrimitive(staticId DispatchId, referencable bool) bool {
	if referencable {
		return false
	}
	switch staticId {
	case Int32DispatchId, Int64DispatchId, IntDispatchId,
		Uint32DispatchId, Uint64DispatchId, UintDispatchId:
		return true
	default:
		return false
	}
}

// isPrimitiveStaticId returns true if the staticId represents a primitive type
func isPrimitiveStaticId(staticId DispatchId) bool {
	switch staticId {
	case BoolDispatchId, Int8DispatchId, Int16DispatchId, Int32DispatchId,
		Int64DispatchId, IntDispatchId, Uint8DispatchId, Uint16DispatchId,
		Uint32DispatchId, Uint64DispatchId, UintDispatchId,
		Float32DispatchId, Float64DispatchId:
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

// getFixedSizeByDispatchId returns byte size for fixed primitives (0 if not fixed)
func getFixedSizeByDispatchId(staticId DispatchId) int {
	switch staticId {
	case BoolDispatchId, Int8DispatchId, Uint8DispatchId:
		return 1
	case Int16DispatchId, Uint16DispatchId:
		return 2
	case Float32DispatchId:
		return 4
	case Float64DispatchId:
		return 8
	default:
		return 0
	}
}

// getVarintMaxSizeByDispatchId returns max byte size for varint primitives (0 if not varint)
func getVarintMaxSizeByDispatchId(staticId DispatchId) int {
	switch staticId {
	case Int32DispatchId, Uint32DispatchId:
		return 5
	case Int64DispatchId, IntDispatchId, Uint64DispatchId, UintDispatchId:
		return 10
	default:
		return 0
	}
}
