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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.fory.json.codec.BaseObjectCodec;
import org.apache.fory.json.codec.CodecRegistry;
import org.apache.fory.json.codec.CodecUtils;
import org.apache.fory.json.codec.Codecs;
import org.apache.fory.json.codec.JsonCodec;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.codec.ObjectWriters;
import org.apache.fory.json.codegen.JsonCodegen;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class JsonTypeResolver {
  private final ConcurrentMap<Class<?>, BaseObjectCodec> objectCodecs = new ConcurrentHashMap<>();
  private final ConcurrentMap<Object, JsonTypeInfo> typeInfos = new ConcurrentHashMap<>();
  private final CodecRegistry registry;
  private final JsonCodegen codegen;

  public JsonTypeResolver(boolean codegenEnabled, boolean writeNullFields, CodecRegistry registry) {
    this.registry = registry.copy();
    codegen = codegenEnabled ? new JsonCodegen(writeNullFields) : null;
  }

  public BaseObjectCodec getObjectCodec(Class<?> type) {
    BaseObjectCodec codec = objectCodecs.get(type);
    if (codec != null) {
      return codec;
    }
    return buildAndPublish(type);
  }

  public JsonTypeInfo getTypeInfo(Type declaredType, Class<?> fallback) {
    Class<?> rawType = CodecUtils.rawType(declaredType, fallback);
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
    JsonTypeInfo typeInfo = getTypeInfo(declaredType, value.getClass());
    typeInfo.codec().write(writer, value, this);
  }

  public void writeStringValue(StringJsonWriter writer, Object value, Type declaredType) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    JsonTypeInfo typeInfo = getTypeInfo(declaredType, value.getClass());
    typeInfo.codec().writeString(writer, value, this);
  }

  public void writeUtf8Value(Utf8JsonWriter writer, Object value, Type declaredType) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    JsonTypeInfo typeInfo = getTypeInfo(declaredType, value.getClass());
    typeInfo.codec().writeUtf8(writer, value, this);
  }

  public Object readValue(
      org.apache.fory.json.reader.JsonReader reader, Type declaredType, Class<?> fallback) {
    JsonTypeInfo typeInfo = getTypeInfo(declaredType, fallback);
    return typeInfo.codec().read(reader, typeInfo, this);
  }

  private synchronized BaseObjectCodec buildAndPublish(Class<?> type) {
    BaseObjectCodec cached = objectCodecs.get(type);
    if (cached != null) {
      return cached;
    }
    ObjectCodec codec = BaseObjectCodec.build(type);
    // Codegen may ask for nested object metadata that points back to this type.
    // Publishing before compiling keeps recursive ownership in this resolver cache.
    objectCodecs.put(type, codec);
    try {
      codec.resolveTypes(this);
      if (codegen != null) {
        ObjectWriters writers = codegen.compile(codec, this);
        if (writers != null) {
          BaseObjectCodec generated = codec.withWriters(writers);
          objectCodecs.put(type, generated);
          return generated;
        }
      }
      return codec;
    } catch (RuntimeException | Error e) {
      objectCodecs.remove(type, codec);
      throw e;
    }
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
    JsonCodec codec = Codecs.forResolvedType(rawType, declaredType, this, registry);
    if (codec == null) {
      codec = getObjectCodec(rawType);
    }
    return new JsonTypeInfo(declaredType, rawType, Codecs.kind(rawType), codec);
  }

  private static Object typeInfoKey(Type declaredType, Class<?> rawType) {
    if (declaredType instanceof Class) {
      return rawType;
    }
    return declaredType;
  }
}
