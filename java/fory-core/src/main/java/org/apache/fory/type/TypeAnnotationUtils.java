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

import java.lang.annotation.Annotation;
import org.apache.fory.annotation.Int32Type;
import org.apache.fory.annotation.Int64Type;
import org.apache.fory.annotation.Int8ArrayType;
import org.apache.fory.annotation.Uint16ArrayType;
import org.apache.fory.annotation.Uint16Type;
import org.apache.fory.annotation.Uint32ArrayType;
import org.apache.fory.annotation.Uint32Type;
import org.apache.fory.annotation.Uint64ArrayType;
import org.apache.fory.annotation.Uint64Type;
import org.apache.fory.annotation.Uint8ArrayType;
import org.apache.fory.annotation.Uint8Type;

public class TypeAnnotationUtils {

  /**
   * Get the type id for the given type annotation and validate it against the field type.
   *
   * @param typeAnnotation the type annotation
   * @param fieldType the field type class
   * @return the type id
   * @throws IllegalArgumentException if the annotation is not compatible with the field type
   */
  public static int getTypeId(Annotation typeAnnotation, Class<?> fieldType) {
    if (typeAnnotation == null) {
      return Types.UNKNOWN;
    }
    if (typeAnnotation instanceof Uint8Type) {
      checkFieldType(fieldType, "@Uint8Type", byte.class, Byte.class);
      return Types.UINT8;
    } else if (typeAnnotation instanceof Uint16Type) {
      checkFieldType(fieldType, "@Uint16Type", short.class, Short.class);
      return Types.UINT16;
    } else if (typeAnnotation instanceof Uint32Type) {
      checkFieldType(fieldType, "@Uint32Type", int.class, Integer.class);
      Uint32Type uint32Type = (Uint32Type) typeAnnotation;
      return uint32Type.compress() ? Types.VAR_UINT32 : Types.UINT32;
    } else if (typeAnnotation instanceof Uint64Type) {
      checkFieldType(fieldType, "@Uint64Type", long.class, Long.class);
      Uint64Type uint64Type = (Uint64Type) typeAnnotation;
      switch (uint64Type.encoding()) {
        case VARINT:
          return Types.VAR_UINT64;
        case FIXED:
          return Types.UINT64;
        case TAGGED:
          return Types.TAGGED_UINT64;
        default:
          throw new IllegalArgumentException("Unsupported encoding: " + uint64Type.encoding());
      }
    } else if (typeAnnotation instanceof Int32Type) {
      checkFieldType(fieldType, "@Int32Type", int.class, Integer.class);
      Int32Type int32Type = (Int32Type) typeAnnotation;
      return int32Type.compress() ? Types.VARINT32 : Types.INT32;
    } else if (typeAnnotation instanceof Int64Type) {
      checkFieldType(fieldType, "@Int64Type", long.class, Long.class);
      Int64Type int64Type = (Int64Type) typeAnnotation;
      switch (int64Type.encoding()) {
        case VARINT:
          return Types.VARINT64;
        case FIXED:
          return Types.INT64;
        case TAGGED:
          return Types.TAGGED_INT64;
        default:
          throw new IllegalArgumentException("Unsupported encoding: " + int64Type.encoding());
      }
    } else if (typeAnnotation instanceof Int8ArrayType) {
      checkFieldType(fieldType, "@Int8ArrayType", byte[].class);
      return Types.INT8_ARRAY;
    } else if (typeAnnotation instanceof Uint8ArrayType) {
      checkFieldType(fieldType, "@Uint8ArrayType", byte[].class);
      return Types.UINT8_ARRAY;
    } else if (typeAnnotation instanceof Uint16ArrayType) {
      checkFieldType(fieldType, "@Uint16ArrayType", short[].class);
      return Types.UINT16_ARRAY;
    } else if (typeAnnotation instanceof Uint32ArrayType) {
      checkFieldType(fieldType, "@Uint32ArrayType", int[].class);
      return Types.UINT32_ARRAY;
    } else if (typeAnnotation instanceof Uint64ArrayType) {
      checkFieldType(fieldType, "@Uint64ArrayType", long[].class);
      return Types.UINT64_ARRAY;
    }
    throw new IllegalArgumentException("Unsupported type annotation: " + typeAnnotation.getClass());
  }

  private static void checkFieldType(
      Class<?> fieldType, String annotationName, Class<?>... allowedTypes) {
    for (Class<?> allowedType : allowedTypes) {
      if (fieldType == allowedType) {
        return;
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < allowedTypes.length; i++) {
      if (i > 0) {
        sb.append(" or ");
      }
      sb.append(allowedTypes[i].getSimpleName());
    }
    throw new IllegalArgumentException(
        annotationName + " can only be applied to " + sb + " fields, but got " + fieldType);
  }
}
