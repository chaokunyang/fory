/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fory.type;

import static org.apache.fory.collection.Collections.ofHashMap;

import java.util.Map;
import org.apache.fory.util.Preconditions;

public class Types {

  /** Unknown/polymorphic type marker. */
  public static final int UNKNOWN = 0;

  /** bool: a boolean value (true or false). */
  public static final int BOOL = 1;

  /** int8: an 8-bit signed integer. */
  public static final int INT8 = 2;

  /** int16: a 16-bit signed integer. */
  public static final int INT16 = 3;

  /** int32: a 32-bit signed integer. */
  public static final int INT32 = 4;

  /** var32: a 32-bit signed integer which uses fory var_int32 encoding. */
  public static final int VAR32 = 5;

  /** int64: a 64-bit signed integer. */
  public static final int INT64 = 6;

  /** var64: a 64-bit signed integer which uses fory PVL encoding. */
  public static final int VAR64 = 7;

  /** h64: a 64-bit signed integer which uses fory hybrid encoding. */
  public static final int H64 = 8;

  /** uint8: an 8-bit unsigned integer. */
  public static final int UINT8 = 9;

  /** uint16: a 16-bit unsigned integer. */
  public static final int UINT16 = 10;

  /** uint32: a 32-bit unsigned integer. */
  public static final int UINT32 = 11;

  /** varu32: a 32-bit unsigned integer which uses fory var_uint32 encoding. */
  public static final int VARU32 = 12;

  /** uint64: a 64-bit unsigned integer. */
  public static final int UINT64 = 13;

  /** varu64: a 64-bit unsigned integer which uses fory var_uint64 encoding. */
  public static final int VARU64 = 14;

  /** hu64: a 64-bit unsigned integer which uses fory hybrid encoding. */
  public static final int HU64 = 15;

  /** float16: a 16-bit floating point number. */
  public static final int FLOAT16 = 16;

  /** float32: a 32-bit floating point number. */
  public static final int FLOAT32 = 17;

  /** float64: a 64-bit floating point number including NaN and Infinity. */
  public static final int FLOAT64 = 18;

  /** string: a text string encoded using Latin1/UTF16/UTF-8 encoding. */
  public static final int STRING = 19;

  /** enum: a data type consisting of a set of named values. */
  public static final int ENUM = 20;

  /** named_enum: an enum whose value will be serialized as the registered name. */
  public static final int NAMED_ENUM = 21;

  /**
   * A morphic(final) type serialized by Fory Struct serializer. i.e. it doesn't have subclasses.
   * Suppose we're deserializing {@code List<SomeClass>}, we can save dynamic serializer dispatch
   * since `SomeClass` is morphic(final).
   */
  public static final int STRUCT = 22;

  /** A morphic(final) type serialized by Fory compatible Struct serializer. */
  public static final int COMPATIBLE_STRUCT = 23;

  /** A `struct` whose type mapping will be encoded as a name. */
  public static final int NAMED_STRUCT = 24;

  /** A `compatible_struct` whose type mapping will be encoded as a name. */
  public static final int NAMED_COMPATIBLE_STRUCT = 25;

  /** A type which will be serialized by a customized serializer. */
  public static final int EXT = 26;

  /** An `ext` type whose type mapping will be encoded as a name. */
  public static final int NAMED_EXT = 27;

  /** A sequence of objects. */
  public static final int LIST = 28;

  /** An unordered set of unique elements. */
  public static final int SET = 29;

  /**
   * A map of key-value pairs. Mutable types such as `list/map/set/array/tensor/arrow` are not
   * allowed as key of map.
   */
  public static final int MAP = 30;

  /**
   * An absolute length of time, independent of any calendar/timezone, as a count of nanoseconds.
   */
  public static final int DURATION = 31;

  /**
   * A point in time, independent of any calendar/timezone, as a count of nanoseconds. The count is
   * relative to an epoch at UTC midnight on January 1, 1970.
   */
  public static final int TIMESTAMP = 32;

  /**
   * A naive date without timezone. The count is days relative to an epoch at UTC midnight on Jan 1,
   * 1970.
   */
  public static final int LOCAL_DATE = 33;

  /** Exact decimal value represented as an integer value in two's complement. */
  public static final int DECIMAL = 34;

  /** A variable-length array of bytes. */
  public static final int BINARY = 35;

  /**
   * A multidimensional array where every sub-array can have different sizes but all have the same
   * type. Only numeric components allowed. Other arrays will be taken as List. The implementation
   * should support interoperability between array and list.
   */
  public static final int ARRAY = 36;

  /** One dimensional bool array. */
  public static final int BOOL_ARRAY = 37;

  /** One dimensional int8 array. */
  public static final int INT8_ARRAY = 38;

  /** One dimensional int16 array. */
  public static final int INT16_ARRAY = 39;

  /** One dimensional int32 array. */
  public static final int INT32_ARRAY = 40;

  /** One dimensional int64 array. */
  public static final int INT64_ARRAY = 41;

  /** One dimensional uint8 array. */
  public static final int UINT8_ARRAY = 42;

  /** One dimensional uint16 array. */
  public static final int UINT16_ARRAY = 43;

  /** One dimensional uint32 array. */
  public static final int UINT32_ARRAY = 44;

  /** One dimensional uint64 array. */
  public static final int UINT64_ARRAY = 45;

  /** One dimensional float16 array. */
  public static final int FLOAT16_ARRAY = 46;

  /** One dimensional float32 array. */
  public static final int FLOAT32_ARRAY = 47;

  /** One dimensional float64 array. */
  public static final int FLOAT64_ARRAY = 48;

  /** A tagged union type that can hold one of several alternative types. */
  public static final int UNION = 49;

  /** Represents an empty/unit value with no data (e.g., for empty union alternatives). */
  public static final int NONE = 50;

  /** Bound value for range checks (types with id >= BOUND are not internal types). */
  public static final int BOUND = 64;

  // Deprecated aliases for backward compatibility
  /** @deprecated Use {@link #VAR32} instead */
  @Deprecated public static final int VAR_INT32 = VAR32;

  /** @deprecated Use {@link #VAR64} instead */
  @Deprecated public static final int VAR_INT64 = VAR64;

  /** @deprecated Use {@link #H64} instead */
  @Deprecated public static final int SLI_INT64 = H64;

  // Helper methods
  public static boolean isNamedType(int value) {
    assert value < 0xff;
    switch (value) {
      case NAMED_STRUCT:
      case NAMED_COMPATIBLE_STRUCT:
      case NAMED_ENUM:
      case NAMED_EXT:
        return true;
      default:
        return false;
    }
  }

  public static boolean isStructType(int value) {
    assert value < 0xff;
    return value == STRUCT
        || value == COMPATIBLE_STRUCT
        || value == NAMED_STRUCT
        || value == NAMED_COMPATIBLE_STRUCT;
  }

  public static boolean isExtType(int value) {
    assert value < 0xff;
    return value == EXT || value == NAMED_EXT;
  }

  public static boolean isEnumType(int value) {
    assert value < 0xff;
    return value == ENUM || value == NAMED_ENUM;
  }

  public static boolean isUserDefinedType(byte typeId) {
    return isStructType(typeId) || isExtType(typeId) || isEnumType(typeId);
  }

  private static final Map<Class, Integer> PRIMITIVE_TYPE_ID_MAP =
      ofHashMap(
          boolean.class, BOOL,
          byte.class, INT8,
          short.class, INT16,
          int.class, INT32,
          long.class, INT64,
          float.class, FLOAT32,
          double.class, FLOAT64);

  public static int getPrimitiveTypeId(Class<?> cls) {
    Preconditions.checkArgument(cls.isPrimitive(), "Class %s is not primitive", cls);
    return PRIMITIVE_TYPE_ID_MAP.getOrDefault(cls, -1);
  }

  public static boolean isPrimitiveType(int typeId) {
    // noinspection Duplicates
    switch (typeId) {
      case BOOL:
      case INT8:
      case INT16:
      case INT32:
      case INT64:
      case FLOAT32:
      case FLOAT64:
        return true;
      default:
        return false;
    }
  }

  public static boolean isPrimitiveArray(int typeId) {
    // noinspection Duplicates
    switch (typeId) {
      case BOOL_ARRAY:
      case INT8_ARRAY:
      case INT16_ARRAY:
      case INT32_ARRAY:
      case INT64_ARRAY:
      case UINT8_ARRAY:
      case UINT16_ARRAY:
      case UINT32_ARRAY:
      case UINT64_ARRAY:
      case FLOAT16_ARRAY:
      case FLOAT32_ARRAY:
      case FLOAT64_ARRAY:
        return true;
      default:
        return false;
    }
  }

  public static int getPrimitiveArrayTypeId(int typeId) {
    switch (typeId) {
      case BOOL:
        return BOOL_ARRAY;
      case INT8:
        return INT8_ARRAY;
      case INT16:
        return INT16_ARRAY;
      case INT32:
        return INT32_ARRAY;
      case INT64:
        return INT64_ARRAY;
      case UINT8:
        return UINT8_ARRAY;
      case UINT16:
        return UINT16_ARRAY;
      case UINT32:
        return UINT32_ARRAY;
      case UINT64:
        return UINT64_ARRAY;
      case FLOAT16:
        return FLOAT16_ARRAY;
      case FLOAT32:
        return FLOAT32_ARRAY;
      case FLOAT64:
        return FLOAT64_ARRAY;
      default:
        throw new IllegalArgumentException(
            String.format("Type id %d is not a primitive id", typeId));
    }
  }
}
