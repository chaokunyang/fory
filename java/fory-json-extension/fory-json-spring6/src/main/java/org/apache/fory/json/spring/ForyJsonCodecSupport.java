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

package org.apache.fory.json.spring;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonObject;
import org.apache.fory.reflect.TypeRef;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ProblemDetail;
import org.springframework.util.MimeType;

final class ForyJsonCodecSupport {
  private static final TypeRef<Map<String, Object>> PROBLEM_DETAIL_MAP_TYPE =
      new TypeRef<Map<String, Object>>() {};

  private ForyJsonCodecSupport() {}

  static boolean supportsType(ResolvableType type) {
    Class<?> rawType = type.resolve();
    return rawType != null && isFullyBound(type.getType()) && supportsClass(rawType);
  }

  static boolean supportsClass(Class<?> type) {
    return !CharSequence.class.isAssignableFrom(type)
        && type != byte[].class
        && !Resource.class.isAssignableFrom(type)
        && !ResourceRegion.class.isAssignableFrom(type);
  }

  static boolean supportsMimeType(MimeType mimeType) {
    return mimeType == null
        || mimeType.getCharset() == null
        || StandardCharsets.UTF_8.equals(mimeType.getCharset());
  }

  private static boolean isFullyBound(Type type) {
    if (type instanceof TypeVariable<?> || type instanceof WildcardType) {
      return false;
    }
    if (type instanceof GenericArrayType genericArrayType) {
      return isFullyBound(genericArrayType.getGenericComponentType());
    }
    if (type instanceof ParameterizedType parameterizedType) {
      Type owner = parameterizedType.getOwnerType();
      if (owner != null && !isFullyBound(owner)) {
        return false;
      }
      for (Type argument : parameterizedType.getActualTypeArguments()) {
        if (!isFullyBound(argument)) {
          return false;
        }
      }
    }
    return true;
  }

  static byte[] write(ForyJson foryJson, Object value, ResolvableType declaredType) {
    if (value instanceof ProblemDetail problemDetail) {
      return foryJson.toJsonBytes(problemDetailValues(problemDetail), PROBLEM_DETAIL_MAP_TYPE);
    }
    if (declaredType == ResolvableType.NONE || declaredType.resolve() == null) {
      return foryJson.toJsonBytes(value);
    }
    if (declaredType.getType() instanceof Class<?>) {
      return writeClass(foryJson, value, declaredType.toClass());
    }
    return writeType(foryJson, value, TypeRef.of(declaredType.getType()));
  }

  static Object read(ForyJson foryJson, byte[] bytes, ResolvableType declaredType) {
    Class<?> rawType = declaredType.resolve();
    if (rawType == ProblemDetail.class) {
      return readProblemDetail(foryJson, bytes);
    }
    if (declaredType.getType() instanceof Class<?>) {
      return foryJson.fromJson(bytes, declaredType.toClass());
    }
    return foryJson.fromJson(bytes, TypeRef.of(declaredType.getType()));
  }

  static ProblemDetail readProblemDetail(ForyJson foryJson, byte[] bytes) {
    return readProblemDetail(foryJson.fromJson(bytes, Object.class));
  }

  static ProblemDetail readProblemDetail(Object value) {
    if (!(value instanceof JsonObject values)) {
      throw new IllegalArgumentException("Expected a JSON object for ProblemDetail");
    }
    Object statusValue = values.remove("status");
    if (!(statusValue instanceof Number status)) {
      throw new IllegalArgumentException("ProblemDetail status must be a number");
    }
    ProblemDetail problemDetail = ProblemDetail.forStatus(status.intValue());
    setUri(values.remove("type"), problemDetail::setType, "type");
    setString(values.remove("title"), problemDetail::setTitle, "title");
    setString(values.remove("detail"), problemDetail::setDetail, "detail");
    setUri(values.remove("instance"), problemDetail::setInstance, "instance");
    values.forEach(problemDetail::setProperty);
    return problemDetail;
  }

  private static Map<String, Object> problemDetailValues(ProblemDetail value) {
    // Spring exposes ProblemDetail extension properties as top-level JSON members rather than
    // under a nested properties field, so use a map to preserve that HTTP contract.
    Map<String, Object> values = new LinkedHashMap<>();
    putUri(values, "type", value.getType());
    putString(values, "title", value.getTitle());
    if (value.getStatus() != 0) {
      values.put("status", value.getStatus());
    }
    putString(values, "detail", value.getDetail());
    putUri(values, "instance", value.getInstance());
    if (value.getProperties() != null) {
      values.putAll(value.getProperties());
    }
    return values;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static byte[] writeClass(ForyJson foryJson, Object value, Class<?> declaredType) {
    return foryJson.toJsonBytes(value, (Class) declaredType);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static byte[] writeType(ForyJson foryJson, Object value, TypeRef<?> declaredType) {
    return foryJson.toJsonBytes(value, (TypeRef) declaredType);
  }

  private static void putUri(Map<String, Object> values, String name, URI value) {
    if (value != null) {
      values.put(name, value.toString());
    }
  }

  private static void putString(Map<String, Object> values, String name, String value) {
    if (value != null && !value.isEmpty()) {
      values.put(name, value);
    }
  }

  private static void setUri(
      Object value, java.util.function.Consumer<URI> setter, String propertyName) {
    if (value != null) {
      if (!(value instanceof String text)) {
        throw new IllegalArgumentException("ProblemDetail " + propertyName + " must be a string");
      }
      setter.accept(URI.create(text));
    }
  }

  private static void setString(
      Object value, java.util.function.Consumer<String> setter, String propertyName) {
    if (value != null) {
      if (!(value instanceof String text)) {
        throw new IllegalArgumentException("ProblemDetail " + propertyName + " must be a string");
      }
      setter.accept(text);
    }
  }
}
