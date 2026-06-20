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
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class MapCodec extends AbstractJsonCodec {
  private static final Class<?> UNTYPED_MAP = LinkedHashMap.class;
  private final Class<?> rawType;
  private final JsonTypeInfo valueTypeInfo;
  private final JsonCodec valueCodec;

  public MapCodec(Class<?> rawType, Type valueType, JsonTypeResolver resolver) {
    this.rawType = rawType;
    Class<?> valueRawType = CodecUtils.rawType(valueType, Object.class);
    valueTypeInfo = resolver.getTypeInfo(valueType, valueRawType);
    valueCodec = valueTypeInfo.codec();
  }

  @Override
  void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writeMap(writer, (Map<?, ?>) value, resolver);
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    writeMap(writer, (Map<?, ?>) value, resolver);
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writeMap(writer, (Map<?, ?>) value, resolver);
  }

  @Override
  Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
    Map<String, Object> map = newMap(rawType);
    readInto(reader, map, valueTypeInfo, valueCodec, resolver);
    return map;
  }

  static Map<String, Object> readUntyped(JsonReader reader, JsonTypeResolver resolver) {
    JsonTypeInfo valueInfo = resolver.getTypeInfo(Object.class, Object.class);
    Map<String, Object> map = new LinkedHashMap<>();
    readInto(reader, map, valueInfo, valueInfo.codec(), resolver);
    return map;
  }

  private void writeMap(JsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      valueCodec.write(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private void writeMap(StringJsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      valueCodec.writeString(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private void writeMap(Utf8JsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      valueCodec.writeUtf8(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private static void readInto(
      JsonReader reader,
      Map<String, Object> map,
      JsonTypeInfo valueInfo,
      JsonCodec valueCodec,
      JsonTypeResolver resolver) {
    reader.expect('{');
    if (!reader.consume('}')) {
      do {
        String name = reader.readString();
        reader.expect(':');
        map.put(name, valueCodec.read(reader, valueInfo, resolver));
      } while (reader.consume(','));
      reader.expect('}');
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> newMap(Class<?> rawType) {
    if (rawType == UNTYPED_MAP || rawType.isInterface()) {
      return new LinkedHashMap<>();
    }
    try {
      return (Map<String, Object>) rawType.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new ForyJsonException("Cannot create map " + rawType, e);
    }
  }
}
