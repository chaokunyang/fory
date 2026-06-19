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

package org.apache.fory.json.resolver;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.fory.json.codegen.JsonCodegen;
import org.apache.fory.json.meta.JsonClassInfo;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.serializer.JsonSerializers;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class JsonTypeResolver {
  private final ConcurrentMap<Class<?>, JsonClassInfo> classes = new ConcurrentHashMap<>();
  private final ConcurrentMap<Object, JsonTypeInfo> typeInfos = new ConcurrentHashMap<>();
  private final JsonCodegen codegen;

  public JsonTypeResolver(boolean codegenEnabled, boolean writeNullFields) {
    codegen = codegenEnabled ? new JsonCodegen(writeNullFields) : null;
  }

  public JsonClassInfo getClassInfo(Class<?> type) {
    JsonClassInfo classInfo = classes.get(type);
    if (classInfo != null) {
      return classInfo;
    }
    return buildAndPublish(type);
  }

  public JsonTypeInfo getTypeInfo(Type declaredType, Class<?> fallback) {
    Class<?> rawType = JsonSerializers.rawType(declaredType, fallback);
    Object key = typeInfoKey(declaredType, rawType);
    JsonTypeInfo typeInfo = typeInfos.get(key);
    if (typeInfo != null) {
      return typeInfo;
    }
    return buildAndPublishTypeInfo(key, rawType, declaredType);
  }

  public void writeValue(JsonWriter writer, Object value, Type declaredType) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    getTypeInfo(declaredType, value.getClass()).write(writer, value, this);
  }

  public void writeStringValue(StringJsonWriter writer, Object value, Type declaredType) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    getTypeInfo(declaredType, value.getClass()).writeString(writer, value, this);
  }

  public void writeUtf8Value(Utf8JsonWriter writer, Object value, Type declaredType) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    getTypeInfo(declaredType, value.getClass()).writeUtf8(writer, value, this);
  }

  public Object readValue(JsonReader reader, Type declaredType, Class<?> fallback) {
    return getTypeInfo(declaredType, fallback).read(reader, this);
  }

  private synchronized JsonClassInfo buildAndPublish(Class<?> type) {
    JsonClassInfo cached = classes.get(type);
    if (cached != null) {
      return cached;
    }
    JsonClassInfo classInfo = JsonClassInfo.build(type);
    // Codegen may ask for nested class metadata that points back to this type.
    // Publishing metadata before compiling the writer keeps that recursion cache-owned.
    classes.put(type, classInfo);
    try {
      classInfo.resolveTypes(this);
      if (codegen != null) {
        classInfo.setObjectWriters(codegen.compile(classInfo, this));
      }
    } catch (RuntimeException | Error e) {
      classes.remove(type, classInfo);
      throw e;
    }
    return classInfo;
  }

  private synchronized JsonTypeInfo buildAndPublishTypeInfo(
      Object key, Class<?> rawType, Type declaredType) {
    JsonTypeInfo cached = typeInfos.get(key);
    if (cached != null) {
      return cached;
    }
    JsonTypeInfo typeInfo = buildTypeInfo(rawType, declaredType);
    typeInfos.put(key, typeInfo);
    return typeInfo;
  }

  private JsonTypeInfo buildTypeInfo(Class<?> rawType, Type declaredType) {
    if (rawType == Object.class) {
      return new JsonTypeInfo.NaturalInfo();
    } else if (rawType == String.class) {
      return new JsonTypeInfo.StringInfo(rawType);
    } else if (rawType == boolean.class || rawType == Boolean.class) {
      return new JsonTypeInfo.BooleanInfo(rawType);
    } else if (rawType == int.class || rawType == Integer.class) {
      return new JsonTypeInfo.IntInfo(rawType);
    } else if (rawType == long.class || rawType == Long.class) {
      return new JsonTypeInfo.LongInfo(rawType);
    } else if (rawType == short.class || rawType == Short.class) {
      return new JsonTypeInfo.ShortInfo(rawType);
    } else if (rawType == byte.class || rawType == Byte.class) {
      return new JsonTypeInfo.ByteInfo(rawType);
    } else if (rawType == char.class || rawType == Character.class) {
      return new JsonTypeInfo.CharInfo(rawType);
    } else if (rawType == float.class || rawType == Float.class) {
      return new JsonTypeInfo.FloatInfo(rawType);
    } else if (rawType == double.class || rawType == Double.class) {
      return new JsonTypeInfo.DoubleInfo(rawType);
    } else if (rawType.isEnum()) {
      return new JsonTypeInfo.EnumInfo(rawType);
    } else if (rawType.isArray()) {
      return new JsonTypeInfo.ArrayInfo(rawType);
    } else if (Collection.class.isAssignableFrom(rawType)) {
      return new JsonTypeInfo.CollectionInfo(rawType, declaredType);
    } else if (Map.class.isAssignableFrom(rawType)) {
      return new JsonTypeInfo.MapInfo(rawType, declaredType);
    }
    return JsonTypeInfo.objectInfo(rawType, this);
  }

  private static Object typeInfoKey(Type declaredType, Class<?> rawType) {
    if (declaredType instanceof Class) {
      return rawType;
    }
    return declaredType;
  }
}
