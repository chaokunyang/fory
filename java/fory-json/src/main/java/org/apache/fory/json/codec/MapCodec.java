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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.JSONObject;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class MapCodec extends AbstractJsonCodec {
  private static final Class<?> UNTYPED_MAP = LinkedHashMap.class;
  private final Class<?> rawType;
  private final MapKeyCodec keyCodec;
  private final Class<?> keyRawType;
  private final JsonTypeInfo valueTypeInfo;
  private final JsonCodec valueCodec;

  public MapCodec(Class<?> rawType, Type mapType, JsonTypeResolver resolver) {
    this.rawType = rawType;
    Type keyType = CodecUtils.mapKeyType(mapType);
    keyRawType = CodecUtils.rawType(keyType, String.class);
    keyCodec = MapKeyCodec.of(keyRawType);
    Type valueType = CodecUtils.mapValueType(mapType);
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
    Map<Object, Object> map = newMap(rawType, keyRawType);
    readInto(reader, map, keyCodec, valueTypeInfo, valueCodec, resolver);
    return map;
  }

  static Map<Object, Object> readUntyped(JsonReader reader, JsonTypeResolver resolver) {
    JsonTypeInfo valueInfo = resolver.getTypeInfo(Object.class, Object.class);
    Map<Object, Object> map = (Map<Object, Object>) (Map<?, ?>) new JSONObject();
    readInto(reader, map, MapKeyCodec.STRING, valueInfo, valueInfo.codec(), resolver);
    return map;
  }

  private void writeMap(JsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      writer.writeComma(index++);
      writer.writeFieldName(keyCodec.toName(entry.getKey()));
      valueCodec.write(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private void writeMap(StringJsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      writer.writeComma(index++);
      writer.writeFieldName(keyCodec.toName(entry.getKey()));
      valueCodec.writeString(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private void writeMap(Utf8JsonWriter writer, Map<?, ?> map, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      writer.writeComma(index++);
      writer.writeFieldName(keyCodec.toName(entry.getKey()));
      valueCodec.writeUtf8(writer, entry.getValue(), resolver);
    }
    writer.writeObjectEnd();
  }

  private static void readInto(
      JsonReader reader,
      Map<Object, Object> map,
      JsonTypeInfo valueInfo,
      JsonCodec valueCodec,
      JsonTypeResolver resolver) {
    readInto(reader, map, MapKeyCodec.STRING, valueInfo, valueCodec, resolver);
  }

  private static void readInto(
      JsonReader reader,
      Map<Object, Object> map,
      MapKeyCodec keyCodec,
      JsonTypeInfo valueInfo,
      JsonCodec valueCodec,
      JsonTypeResolver resolver) {
    reader.expect('{');
    if (!reader.consume('}')) {
      do {
        String name = reader.readString();
        reader.expect(':');
        map.put(keyCodec.fromName(name), valueCodec.read(reader, valueInfo, resolver));
      } while (reader.consume(','));
      reader.expect('}');
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> newMap(Class<?> rawType, Class<?> keyRawType) {
    if (rawType == JSONObject.class) {
      return (Map<Object, Object>) (Map<?, ?>) new JSONObject();
    }
    if (rawType == EnumMap.class) {
      if (!keyRawType.isEnum()) {
        throw new ForyJsonException("EnumMap requires an enum key type");
      }
      return new EnumMap(keyRawType);
    }
    if (rawType == UNTYPED_MAP || rawType.isInterface()) {
      if (ConcurrentMap.class.isAssignableFrom(rawType)) {
        if (NavigableMap.class.isAssignableFrom(rawType)
            || SortedMap.class.isAssignableFrom(rawType)) {
          return new ConcurrentSkipListMap<>();
        }
        return new ConcurrentHashMap<>();
      }
      if (NavigableMap.class.isAssignableFrom(rawType)
          || SortedMap.class.isAssignableFrom(rawType)) {
        return new TreeMap<>();
      }
      return new LinkedHashMap<>();
    }
    try {
      return (Map<Object, Object>) rawType.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new ForyJsonException("Cannot create map " + rawType, e);
    }
  }

  private interface MapKeyCodec {
    MapKeyCodec STRING =
        new MapKeyCodec() {
          @Override
          public String toName(Object key) {
            if (!(key instanceof String)) {
              throw new ForyJsonException("Expected String map key, got " + key);
            }
            return (String) key;
          }

          @Override
          public Object fromName(String name) {
            return name;
          }
        };

    String toName(Object key);

    Object fromName(String name);

    static MapKeyCodec of(Class<?> rawType) {
      if (rawType == String.class || rawType == Object.class) {
        return STRING;
      }
      if (rawType.isEnum()) {
        return new EnumKeyCodec(rawType);
      }
      if (rawType == int.class || rawType == Integer.class) {
        return IntKeyCodec.INSTANCE;
      }
      if (rawType == long.class || rawType == Long.class) {
        return LongKeyCodec.INSTANCE;
      }
      throw new ForyJsonException("Unsupported JSON map key type " + rawType);
    }
  }

  private static final class EnumKeyCodec implements MapKeyCodec {
    private final Class<?> type;
    private final Map<String, Enum<?>> values;

    @SuppressWarnings("unchecked")
    private EnumKeyCodec(Class<?> type) {
      this.type = type;
      Enum<?>[] constants = (Enum<?>[]) type.getEnumConstants();
      values = new LinkedHashMap<>(constants.length * 2);
      for (Enum<?> constant : constants) {
        values.put(constant.name(), constant);
      }
    }

    @Override
    public String toName(Object key) {
      if (!type.isInstance(key)) {
        throw new ForyJsonException("Expected " + type + " map key, got " + key);
      }
      return ((Enum<?>) key).name();
    }

    @Override
    public Object fromName(String name) {
      Enum<?> value = values.get(name);
      if (value == null) {
        throw new ForyJsonException("Unknown enum map key " + name + " for " + type);
      }
      return value;
    }
  }

  private static final class IntKeyCodec implements MapKeyCodec {
    private static final IntKeyCodec INSTANCE = new IntKeyCodec();

    @Override
    public String toName(Object key) {
      return String.valueOf((Integer) key);
    }

    @Override
    public Object fromName(String name) {
      return Integer.valueOf(name);
    }
  }

  private static final class LongKeyCodec implements MapKeyCodec {
    private static final LongKeyCodec INSTANCE = new LongKeyCodec();

    @Override
    public String toName(Object key) {
      return String.valueOf((Long) key);
    }

    @Override
    public Object fromName(String name) {
      return Long.valueOf(name);
    }
  }
}
