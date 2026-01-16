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

import org.apache.fory.Fory;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.ClassResolver;

public class DispatchId {
  public static final int UNKNOWN = 0;
  public static final int PRIMITIVE_BOOL = 1;
  public static final int PRIMITIVE_INT8 = 2;
  public static final int PRIMITIVE_INT16 = 3;
  public static final int PRIMITIVE_CHAR = 4;
  public static final int PRIMITIVE_INT32 = 5;
  public static final int PRIMITIVE_VARINT32 = 6;
  public static final int PRIMITIVE_INT64 = 7;
  public static final int PRIMITIVE_VARINT64 = 8;
  public static final int PRIMITIVE_TAGGED_INT64 = 9;
  public static final int PRIMITIVE_FLOAT32 = 10;
  public static final int PRIMITIVE_FLOAT64 = 11;
  public static final int PRIMITIVE_UINT8 = 12;
  public static final int PRIMITIVE_UINT16 = 13;
  public static final int PRIMITIVE_UINT32 = 14;
  public static final int PRIMITIVE_VAR_UINT32 = 15;
  public static final int PRIMITIVE_UINT64 = 16;
  public static final int PRIMITIVE_VAR_UINT64 = 17;
  public static final int PRIMITIVE_TAGGED_UINT64 = 18;

  // Non-nullable boxed types: read value directly (no null flag), box it, use putObject
  // Used when remote TypeMeta has nullable=false but local field is boxed (e.g., Integer)
  public static final int NOTNULL_BOXED_BOOL = 19;
  public static final int NOTNULL_BOXED_INT8 = 20;
  public static final int NOTNULL_BOXED_INT16 = 21;
  public static final int NOTNULL_BOXED_CHAR = 22;
  public static final int NOTNULL_BOXED_INT32 = 23;
  public static final int NOTNULL_BOXED_VARINT32 = 24;
  public static final int NOTNULL_BOXED_INT64 = 25;
  public static final int NOTNULL_BOXED_VARINT64 = 26;
  public static final int NOTNULL_BOXED_TAGGED_INT64 = 27;
  public static final int NOTNULL_BOXED_FLOAT32 = 28;
  public static final int NOTNULL_BOXED_FLOAT64 = 29;
  public static final int NOTNULL_BOXED_UINT8 = 30;
  public static final int NOTNULL_BOXED_UINT16 = 31;
  public static final int NOTNULL_BOXED_UINT32 = 32;
  public static final int NOTNULL_BOXED_VAR_UINT32 = 33;
  public static final int NOTNULL_BOXED_UINT64 = 34;
  public static final int NOTNULL_BOXED_VAR_UINT64 = 35;
  public static final int NOTNULL_BOXED_TAGGED_UINT64 = 36;

  // Nullable boxed types: read null flag first, then box if not null
  public static final int BOOL = 37;
  public static final int INT8 = 38;
  public static final int CHAR = 39;
  public static final int INT16 = 40;
  public static final int INT32 = 41;
  public static final int VARINT32 = 42;
  public static final int INT64 = 43;
  public static final int VARINT64 = 44;
  public static final int TAGGED_INT64 = 45;
  public static final int FLOAT32 = 46;
  public static final int FLOAT64 = 47;
  public static final int UINT8 = 48;
  public static final int UINT16 = 49;
  public static final int UINT32 = 50;
  public static final int VAR_UINT32 = 51;
  public static final int UINT64 = 52;
  public static final int VAR_UINT64 = 53;
  public static final int TAGGED_UINT64 = 54;
  public static final int STRING = 55;

  // Dispatch mode for determining how to read/write a field
  private static final int MODE_PRIMITIVE = 0; // Local field is primitive, use Platform.putInt
  private static final int MODE_NOTNULL_BOXED =
      1; // Local is boxed, remote nullable=false, box and putObject
  private static final int MODE_NULLABLE_BOXED =
      2; // Local is boxed, remote nullable=true, read null flag

  public static int getDispatchId(Fory fory, Descriptor d) {
    int typeId = Types.getDescriptorTypeId(fory, d);
    TypeRef<?> typeRef = d.getTypeRef();
    Class<?> rawType = typeRef.getRawType();
    TypeExtMeta typeExtMeta = typeRef.getTypeExtMeta();

    // Determine the dispatch mode based on local field type and remote nullable flag
    int mode;
    boolean localIsPrimitive = rawType.isPrimitive();

    // Determine remote nullable: if typeExtMeta exists, use remote info; otherwise use local type
    // For consistent schema (no typeExtMeta), primitive fields never have null flag
    boolean remoteNullable;
    if (typeExtMeta != null) {
      remoteNullable = typeExtMeta.nullable();
    } else {
      // No remote info (consistent schema) - use local field type to determine nullable
      // Primitive types are never nullable, boxed/reference types are nullable
      remoteNullable = !localIsPrimitive;
    }

    if (!remoteNullable) {
      // Remote wrote without null flag (or no remote info and local is primitive)
      if (localIsPrimitive) {
        mode = MODE_PRIMITIVE;
      } else if (Types.isPrimitiveType(typeId)) {
        mode = MODE_NOTNULL_BOXED;
      } else {
        mode = MODE_NULLABLE_BOXED;
      }
    } else {
      // Remote wrote with null flag - MUST read null flag regardless of local field type
      mode = MODE_NULLABLE_BOXED;
    }

    if (fory.isCrossLanguage()) {
      return xlangTypeIdToDispatchId(typeId, mode);
    } else {
      return nativeIdToDispatchId(typeId, d, mode);
    }
  }

  private static int xlangTypeIdToDispatchId(int typeId, int mode) {
    switch (typeId) {
      case Types.BOOL:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_BOOL
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_BOOL : BOOL);
      case Types.INT8:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_INT8
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_INT8 : INT8);
      case Types.INT16:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_INT16
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_INT16 : INT16);
      case Types.INT32:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_INT32
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_INT32 : INT32);
      case Types.VARINT32:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_VARINT32
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_VARINT32 : VARINT32);
      case Types.INT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_INT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_INT64 : INT64);
      case Types.VARINT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_VARINT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_VARINT64 : VARINT64);
      case Types.TAGGED_INT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_TAGGED_INT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_TAGGED_INT64 : TAGGED_INT64);
      case Types.UINT8:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_UINT8
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_UINT8 : UINT8);
      case Types.UINT16:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_UINT16
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_UINT16 : UINT16);
      case Types.UINT32:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_UINT32
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_UINT32 : UINT32);
      case Types.VAR_UINT32:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_VAR_UINT32
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_VAR_UINT32 : VAR_UINT32);
      case Types.UINT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_UINT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_UINT64 : UINT64);
      case Types.VAR_UINT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_VAR_UINT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_VAR_UINT64 : VAR_UINT64);
      case Types.TAGGED_UINT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_TAGGED_UINT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_TAGGED_UINT64 : TAGGED_UINT64);
      case Types.FLOAT32:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_FLOAT32
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_FLOAT32 : FLOAT32);
      case Types.FLOAT64:
        return mode == MODE_PRIMITIVE
            ? PRIMITIVE_FLOAT64
            : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_FLOAT64 : FLOAT64);
      case Types.STRING:
        return STRING;
      default:
        return UNKNOWN;
    }
  }

  private static int nativeIdToDispatchId(int nativeId, Descriptor descriptor, int mode) {
    if (nativeId >= Types.BOOL && nativeId <= ClassResolver.NATIVE_START_ID) {
      return xlangTypeIdToDispatchId(nativeId, mode);
    }
    if (nativeId == ClassResolver.CHAR_ID) {
      return mode == MODE_PRIMITIVE
          ? PRIMITIVE_CHAR
          : (mode == MODE_NOTNULL_BOXED ? NOTNULL_BOXED_CHAR : CHAR);
    }
    if (nativeId == ClassResolver.PRIMITIVE_CHAR_ID) {
      return PRIMITIVE_CHAR;
    }
    if (nativeId >= ClassResolver.PRIMITIVE_VOID_ID
        && nativeId <= ClassResolver.PRIMITIVE_FLOAT64_ID) {
      throw new IllegalArgumentException(
          String.format(
              "%s should use `Types.BOOL~Types.FLOAT64` with nullable meta instead, but got %s",
              descriptor.getField(), nativeId));
    }
    return xlangTypeIdToDispatchId(nativeId, mode);
  }

  public static boolean isPrimitive(int dispatchId) {
    return dispatchId >= PRIMITIVE_BOOL && dispatchId <= PRIMITIVE_TAGGED_UINT64;
  }

  public static boolean isNotnullBoxed(int dispatchId) {
    return dispatchId >= NOTNULL_BOXED_BOOL && dispatchId <= NOTNULL_BOXED_TAGGED_UINT64;
  }

  public static boolean isNullableBoxed(int dispatchId) {
    return dispatchId >= BOOL && dispatchId <= TAGGED_UINT64;
  }
}
