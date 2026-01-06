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

import org.apache.fory.annotation.Int32Type;
import org.apache.fory.annotation.Int64Type;
import org.apache.fory.annotation.Uint16Type;
import org.apache.fory.annotation.Uint32Type;
import org.apache.fory.annotation.Uint64Type;
import org.apache.fory.annotation.Uint8Type;

import java.lang.annotation.Annotation;

public class TypeAnnotationUtils {
  public static int getTypeId(Annotation typeAnnotation) {
    if (typeAnnotation == null) return Types.UNKNOWN;
    if (typeAnnotation instanceof Uint8Type) {
      return Types.UINT8;
    } else if (typeAnnotation instanceof Uint16Type) {
      return Types.UINT16;
    } else if (typeAnnotation instanceof Uint32Type) {
      Uint32Type  uint32Type = (Uint32Type) typeAnnotation;
      return uint32Type.compress() ? Types.VAR_UINT32 : Types.UINT32;
    } else if (typeAnnotation instanceof Uint64Type) {
      Uint64Type  uint64Type = (Uint64Type) typeAnnotation;
      switch (uint64Type.encoding()) {
        case VARINT64:
          return Types.VAR_UINT64;
        case FIXED_INT64:
          return Types.UINT64;
        case TAGGED_INT64:
          return Types.TAGGED_UINT64;
        default:
          throw new IllegalArgumentException("Unsupported encoding: " + uint64Type.encoding());
      }
    } else if (typeAnnotation instanceof Int32Type) {
      Int32Type int32Type = (Int32Type) typeAnnotation;
      return int32Type.compress() ? Types.VARINT32 : Types.INT32;
    } else if (typeAnnotation instanceof Int64Type) {
      Int64Type int64Type = (Int64Type) typeAnnotation;
      switch (int64Type.encoding()) {
        case VARINT64:
          return Types.VARINT64;
        case FIXED_INT64:
          return Types.INT64;
        case TAGGED_INT64:
          return Types.TAGGED_INT64;
        default:
          throw new IllegalArgumentException("Unsupported encoding: " + int64Type.encoding());
      }
    }
    throw new IllegalArgumentException("Unsupported type: " + typeAnnotation.getClass());
  }
}
