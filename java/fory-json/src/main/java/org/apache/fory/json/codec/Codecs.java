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

package org.apache.fory.json.codec;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import org.apache.fory.json.meta.JsonFieldKind;
import org.apache.fory.json.resolver.JsonTypeResolver;

public final class Codecs {
  private Codecs() {}

  public static JsonCodec forResolvedType(
      Class<?> rawType, Type declaredType, JsonTypeResolver resolver, CodecRegistry registry) {
    JsonCodec customCodec = registry.get(rawType);
    if (customCodec != null) {
      return customCodec;
    }
    if (rawType == Object.class) {
      return NaturalCodec.INSTANCE;
    } else if (rawType == String.class) {
      return StringCodec.INSTANCE;
    } else if (rawType == boolean.class || rawType == Boolean.class) {
      return BooleanCodec.INSTANCE;
    } else if (rawType == int.class || rawType == Integer.class) {
      return IntCodec.INSTANCE;
    } else if (rawType == long.class || rawType == Long.class) {
      return LongCodec.INSTANCE;
    } else if (rawType == short.class || rawType == Short.class) {
      return ShortCodec.INSTANCE;
    } else if (rawType == byte.class || rawType == Byte.class) {
      return ByteCodec.INSTANCE;
    } else if (rawType == char.class || rawType == Character.class) {
      return CharCodec.INSTANCE;
    } else if (rawType == float.class || rawType == Float.class) {
      return FloatCodec.INSTANCE;
    } else if (rawType == double.class || rawType == Double.class) {
      return DoubleCodec.INSTANCE;
    } else if (rawType.isEnum()) {
      return new EnumCodec(rawType);
    } else if (rawType.isArray()) {
      return new ArrayCodec(rawType.getComponentType(), resolver);
    } else if (Collection.class.isAssignableFrom(rawType)) {
      return new CollectionCodec(rawType, CodecUtils.elementType(declaredType), resolver);
    } else if (Map.class.isAssignableFrom(rawType)) {
      return new MapCodec(rawType, CodecUtils.mapValueType(declaredType), resolver);
    }
    return null;
  }

  public static JsonFieldKind kind(Class<?> type) {
    if (type == boolean.class || type == Boolean.class) {
      return JsonFieldKind.BOOLEAN;
    } else if (type == byte.class || type == Byte.class) {
      return JsonFieldKind.BYTE;
    } else if (type == short.class || type == Short.class) {
      return JsonFieldKind.SHORT;
    } else if (type == int.class || type == Integer.class) {
      return JsonFieldKind.INT;
    } else if (type == long.class || type == Long.class) {
      return JsonFieldKind.LONG;
    } else if (type == float.class || type == Float.class) {
      return JsonFieldKind.FLOAT;
    } else if (type == double.class || type == Double.class) {
      return JsonFieldKind.DOUBLE;
    } else if (type == char.class || type == Character.class) {
      return JsonFieldKind.CHAR;
    } else if (type == String.class) {
      return JsonFieldKind.STRING;
    } else if (type.isEnum()) {
      return JsonFieldKind.ENUM;
    } else if (type.isArray()) {
      return JsonFieldKind.ARRAY;
    } else if (Collection.class.isAssignableFrom(type)) {
      return JsonFieldKind.COLLECTION;
    } else if (Map.class.isAssignableFrom(type)) {
      return JsonFieldKind.MAP;
    }
    return JsonFieldKind.OBJECT;
  }
}
