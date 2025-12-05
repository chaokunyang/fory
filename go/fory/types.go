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
	// VAR_INT32 a 32-bit signed integer which uses fory var_int32 encoding
	VAR_INT32 = 5
	// INT64 Signed 64-bit little-endian integer
	INT64 = 6
	// VAR_INT64 a 64-bit signed integer which uses fory PVL encoding
	VAR_INT64 = 7
	// SLI_INT64 a 64-bit signed integer which uses fory SLI encoding
	SLI_INT64 = 8
	// HALF_FLOAT 2-byte floating point value
	HALF_FLOAT = 9
	// FLOAT 4-byte floating point value
	FLOAT = 10
	// DOUBLE 8-byte floating point value
	DOUBLE = 11
	// STRING UTF8 variable-length string as List<Char>
	STRING = 12
	// ENUM a data type consisting of a set of named values
	ENUM = 13
	// NAMED_ENUM an enum whose value will be serialized as the registered name
	NAMED_ENUM = 14
	// STRUCT a morphic(final) type serialized by Fory Struct serializer
	STRUCT = 15
	// COMPATIBLE_STRUCT a morphic(final) type serialized by Fory compatible Struct serializer
	COMPATIBLE_STRUCT = 16
	// NAMED_STRUCT a struct whose type mapping will be encoded as a name
	NAMED_STRUCT = 17
	// NAMED_COMPATIBLE_STRUCT a compatible_struct whose type mapping will be encoded as a name
	NAMED_COMPATIBLE_STRUCT = 18
	// EXT a type which will be serialized by a customized serializer
	EXT = 19
	// NAMED_EXT an ext type whose type mapping will be encoded as a name
	NAMED_EXT = 20
	// LIST A list of some logical data type
	LIST = 21
	// SET an unordered set of unique elements
	SET = 22
	// MAP Map a repeated struct logical type
	MAP = 23
	// DURATION Measure of elapsed time in either seconds milliseconds microseconds
	DURATION = 24
	// TIMESTAMP Exact timestamp encoded with int64 since UNIX epoch
	TIMESTAMP = 25
	// LOCAL_DATE a naive date without timezone
	LOCAL_DATE = 26
	// DECIMAL128 Precision- and scale-based decimal type with 128 bits.
	DECIMAL128 = 27
	// BINARY Variable-length bytes (no guarantee of UTF8-ness)
	BINARY = 28
	// ARRAY a multidimensional array which every sub-array can have different sizes but all have the same type
	ARRAY = 29
	// BOOL_ARRAY one dimensional bool array
	BOOL_ARRAY = 30
	// INT8_ARRAY one dimensional int8 array
	INT8_ARRAY = 31
	// INT16_ARRAY one dimensional int16 array
	INT16_ARRAY = 32
	// INT32_ARRAY one dimensional int32 array
	INT32_ARRAY = 33
	// INT64_ARRAY one dimensional int64 array
	INT64_ARRAY = 34
	// FLOAT16_ARRAY one dimensional half_float_16 array
	FLOAT16_ARRAY = 35
	// FLOAT32_ARRAY one dimensional float32 array
	FLOAT32_ARRAY = 36
	// FLOAT64_ARRAY one dimensional float64 array
	FLOAT64_ARRAY = 37

	// UINT8 Unsigned 8-bit little-endian integer
	UINT8 = 100 // Not in mapping table, assign a higher value
	// UINT16 Unsigned 16-bit little-endian integer
	UINT16 = 101
	// UINT32 Unsigned 32-bit little-endian integer
	UINT32 = 102
	// UINT64 Unsigned 64-bit little-endian integer
	UINT64 = 103
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
