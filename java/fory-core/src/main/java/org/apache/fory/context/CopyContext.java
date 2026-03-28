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

package org.apache.fory.context;

import java.util.Arrays;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.type.Types;
import org.apache.fory.collection.IdentityMap;

@SuppressWarnings("unchecked")
public final class CopyContext {
  private final TypeResolver typeResolver;
  private final boolean copyRefTracking;
  private final IdentityMap<Object, Object> originToCopyMap;
  private int depth;

  public CopyContext(TypeResolver typeResolver, boolean copyRefTracking) {
    this.typeResolver = typeResolver;
    this.copyRefTracking = copyRefTracking;
    originToCopyMap = new IdentityMap<>(2);
  }

  public void reset() {
    if (copyRefTracking) {
      originToCopyMap.clear();
    }
    depth = 0;
  }

  public int getDepth() {
    return depth;
  }

  public TypeResolver getTypeResolver() {
    return typeResolver;
  }

  public void increaseDepth(int diff) {
    depth += diff;
  }

  public boolean copyTrackingRef() {
    return copyRefTracking;
  }

  public <T> void reference(T origin, T copied) {
    if (copyRefTracking && origin != null) {
      originToCopyMap.put(origin, copied);
    }
  }

  public <T> T getCopyObject(T origin) {
    return (T) originToCopyMap.get(origin);
  }

  public <T> T copyObject(T obj) {
    if (obj == null) {
      return null;
    }
    TypeInfo typeInfo = typeResolver.getTypeInfo(obj.getClass(), true);
    int typeId = typeInfo.getTypeId();
    switch (typeId) {
      case Types.BOOL:
      case Types.INT8:
      case ClassResolver.CHAR_ID:
      case Types.INT16:
      case Types.INT32:
      case Types.FLOAT32:
      case Types.INT64:
      case Types.FLOAT64:
        return obj;
      case Types.STRING:
        if (typeInfo.getCls() == String.class) {
          return obj;
        }
        return copyObject(obj, typeInfo.getSerializer());
      case ClassResolver.PRIMITIVE_BOOLEAN_ARRAY_ID:
        return (T) Arrays.copyOf((boolean[]) obj, ((boolean[]) obj).length);
      case ClassResolver.PRIMITIVE_BYTE_ARRAY_ID:
        return (T) Arrays.copyOf((byte[]) obj, ((byte[]) obj).length);
      case ClassResolver.PRIMITIVE_CHAR_ARRAY_ID:
        return (T) Arrays.copyOf((char[]) obj, ((char[]) obj).length);
      case ClassResolver.PRIMITIVE_SHORT_ARRAY_ID:
        return (T) Arrays.copyOf((short[]) obj, ((short[]) obj).length);
      case ClassResolver.PRIMITIVE_INT_ARRAY_ID:
        return (T) Arrays.copyOf((int[]) obj, ((int[]) obj).length);
      case ClassResolver.PRIMITIVE_FLOAT_ARRAY_ID:
        return (T) Arrays.copyOf((float[]) obj, ((float[]) obj).length);
      case ClassResolver.PRIMITIVE_LONG_ARRAY_ID:
        return (T) Arrays.copyOf((long[]) obj, ((long[]) obj).length);
      case ClassResolver.PRIMITIVE_DOUBLE_ARRAY_ID:
        return (T) Arrays.copyOf((double[]) obj, ((double[]) obj).length);
      case ClassResolver.STRING_ARRAY_ID:
        return (T) Arrays.copyOf((String[]) obj, ((String[]) obj).length);
      default:
        return copyObject(obj, typeInfo.getSerializer());
    }
  }

  public <T> T copyObject(T obj, int classId) {
    if (obj == null) {
      return null;
    }
    switch (classId) {
      case ClassResolver.PRIMITIVE_BOOL_ID:
      case ClassResolver.PRIMITIVE_INT8_ID:
      case ClassResolver.PRIMITIVE_CHAR_ID:
      case ClassResolver.PRIMITIVE_INT16_ID:
      case ClassResolver.PRIMITIVE_INT32_ID:
      case ClassResolver.PRIMITIVE_FLOAT32_ID:
      case ClassResolver.PRIMITIVE_INT64_ID:
      case ClassResolver.PRIMITIVE_FLOAT64_ID:
      case Types.BOOL:
      case Types.INT8:
      case ClassResolver.CHAR_ID:
      case Types.INT16:
      case Types.INT32:
      case Types.FLOAT32:
      case Types.INT64:
      case Types.FLOAT64:
        return obj;
      case Types.STRING:
        if (obj.getClass() == String.class) {
          return obj;
        }
        return copyObject(obj, typeResolver.getTypeInfo(obj.getClass(), true).getSerializer());
      default:
        return copyObject(obj, typeResolver.getTypeInfo(obj.getClass(), true).getSerializer());
    }
  }

  public <T> T copyObject(T obj, Serializer<T> serializer) {
    depth++;
    try {
      if (serializer.needToCopyRef()) {
        T existing = getCopyObject(obj);
        if (existing != null) {
          return existing;
        }
        T copied = serializer.copy(this, obj);
        originToCopyMap.put(obj, copied);
        return copied;
      }
      return serializer.copy(this, obj);
    } finally {
      depth--;
    }
  }
}
