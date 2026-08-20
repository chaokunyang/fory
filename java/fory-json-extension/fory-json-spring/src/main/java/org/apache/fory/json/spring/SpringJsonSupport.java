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
import java.util.function.Consumer;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonObject;
import org.apache.fory.reflect.TypeRef;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.MimeType;

final class SpringJsonSupport {
  static final MediaType APPLICATION_JSON_SUFFIX = new MediaType("application", "*+json");
  private static final TypeRef<Map<String, Object>> PROBLEM_DETAIL_MAP_TYPE =
      new TypeRef<Map<String, Object>>() {};

  private SpringJsonSupport() {}

  static boolean supportsMimeType(MimeType mimeType, boolean includeNdjson) {
    if (mimeType == null) {
      return true;
    }
    if (mimeType.getCharset() != null && !StandardCharsets.UTF_8.equals(mimeType.getCharset())) {
      return false;
    }
    return MediaType.APPLICATION_JSON.isCompatibleWith(mimeType)
        || APPLICATION_JSON_SUFFIX.isCompatibleWith(mimeType)
        || includeNdjson && MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType);
  }

  static boolean supportsType(Type type) {
    if (!isFullyBound(type)) {
      return false;
    }
    return supportsClass(TypeRef.of(type).getRawType());
  }

  static boolean supportsClass(Class<?> type) {
    return !CharSequence.class.isAssignableFrom(type)
        && type != byte[].class
        && !Resource.class.isAssignableFrom(type)
        && !ResourceRegion.class.isAssignableFrom(type);
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  static Object read(ForyJson foryJson, byte[] bytes, Type type) {
    if (ProblemDetail.class.equals(TypeRef.of(type).getRawType())) {
      return readProblemDetail(foryJson.fromJson(bytes, Object.class));
    }
    if (type instanceof Class<?> rawType) {
      return foryJson.fromJson(bytes, rawType);
    }
    return foryJson.fromJson(bytes, TypeRef.of(type));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static byte[] write(ForyJson foryJson, Object value, Type type) {
    if (value instanceof ProblemDetail problemDetail) {
      return foryJson.toJsonBytes(problemDetailValues(problemDetail), PROBLEM_DETAIL_MAP_TYPE);
    }
    if (type == null) {
      return foryJson.toJsonBytes(value);
    }
    if (type instanceof Class<?> rawType) {
      return foryJson.toJsonBytes(value, (Class) rawType);
    }
    return foryJson.toJsonBytes(value, TypeRef.of(type));
  }

  static ProblemDetail readProblemDetail(Object value) {
    if (!(value instanceof JsonObject values)) {
      throw new IllegalArgumentException("Expected a JSON object for ProblemDetail");
    }
    Object statusValue = values.remove("status");
    if (statusValue != null && !(statusValue instanceof Number)) {
      throw new IllegalArgumentException("ProblemDetail status must be a number");
    }
    int status = statusValue == null ? 0 : ((Number) statusValue).intValue();
    ProblemDetail problemDetail = ProblemDetail.forStatus(status);
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

  private static void setUri(Object value, Consumer<URI> setter, String propertyName) {
    if (value == null) {
      return;
    }
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException("ProblemDetail " + propertyName + " must be a string");
    }
    setter.accept(URI.create(text));
  }

  private static void setString(Object value, Consumer<String> setter, String propertyName) {
    if (value == null) {
      return;
    }
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException("ProblemDetail " + propertyName + " must be a string");
    }
    setter.accept(text);
  }
}
