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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.JSONObject;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.Latin1StringJsonReader;
import org.apache.fory.json.reader.Utf16StringJsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.TypeRef;

public abstract class MapCodec extends AbstractJsonCodec {
  private static final Class<?> UNTYPED_MAP = LinkedHashMap.class;

  private final TypeRef<?> typeRef;
  private final MapFactory factory;

  MapCodec(TypeRef<?> typeRef, MapFactory factory) {
    this.typeRef = typeRef;
    this.factory = factory;
  }

  public static MapCodec create(Class<?> rawType, TypeRef<?> typeRef, JsonTypeResolver resolver) {
    Tuple2<TypeRef<?>, TypeRef<?>> keyValueTypeRefs = CodecUtils.mapKeyValueTypeRefs(typeRef);
    Type keyType = keyValueTypeRefs.f0.getType();
    Class<?> keyRawType = CodecUtils.rawType(keyType, String.class);
    Type valueType = keyValueTypeRefs.f1.getType();
    Class<?> valueRawType = CodecUtils.rawType(valueType, Object.class);
    MapFactory factory = mapFactory(rawType, keyRawType);
    if (keyRawType == String.class || keyRawType == Object.class) {
      if (valueRawType == Boolean.class || valueRawType == boolean.class) {
        return new StringBooleanMapCodec(typeRef, factory);
      }
      if (valueRawType == Integer.class || valueRawType == int.class) {
        return new StringIntMapCodec(typeRef, factory);
      }
      if (valueRawType == Long.class || valueRawType == long.class) {
        return new StringLongMapCodec(typeRef, factory);
      }
      if (valueRawType == Short.class || valueRawType == short.class) {
        return new StringShortMapCodec(typeRef, factory);
      }
      if (valueRawType == Byte.class || valueRawType == byte.class) {
        return new StringByteMapCodec(typeRef, factory);
      }
      if (valueRawType == Float.class || valueRawType == float.class) {
        return new StringFloatMapCodec(typeRef, factory);
      }
      if (valueRawType == Double.class || valueRawType == double.class) {
        return new StringDoubleMapCodec(typeRef, factory);
      }
      if (valueRawType == BigInteger.class) {
        return new StringBigIntegerMapCodec(typeRef, factory);
      }
      if (valueRawType == BigDecimal.class) {
        return new StringBigDecimalMapCodec(typeRef, factory);
      }
    }
    if (valueRawType == String.class && isNumericKey(keyRawType)) {
      return new NumberStringMapCodec(typeRef, factory, MapKeyCodec.of(keyRawType));
    }
    return new GenericMapCodec(
        typeRef, factory, MapKeyCodec.of(keyRawType), valueType, valueRawType, resolver);
  }

  final TypeRef<?> typeRef() {
    return typeRef;
  }

  static Map<Object, Object> readUntyped(JsonReader reader, JsonTypeResolver resolver) {
    JsonTypeInfo valueInfo = resolver.getTypeInfo(Object.class, Object.class);
    Map<Object, Object> map = (Map<Object, Object>) (Map<?, ?>) new JSONObject();
    readGeneric(reader, map, MapKeyCodec.STRING, valueInfo, valueInfo.codec(), resolver);
    return map;
  }

  final Map<Object, Object> newMap() {
    return factory.newMap();
  }

  private static void writeKey(JsonWriter writer, Object key, MapKeyCodec keyCodec) {
    writer.writeFieldName(keyCodec.toName(key));
  }

  private static void readGeneric(
      JsonReader reader,
      Map<Object, Object> map,
      MapKeyCodec keyCodec,
      JsonTypeInfo valueInfo,
      JsonCodec valueCodec,
      JsonTypeResolver resolver) {
    reader.expect('{');
    if (!reader.consume('}')) {
      do {
        Object key = keyCodec.fromName(reader.readString());
        reader.expect(':');
        map.put(key, valueCodec.read(reader, valueInfo, resolver));
      } while (reader.consume(','));
      reader.expect('}');
    }
  }

  @SuppressWarnings("unchecked")
  private static MapFactory mapFactory(Class<?> rawType, Class<?> keyRawType) {
    if (rawType == JSONObject.class) {
      return () -> (Map<Object, Object>) (Map<?, ?>) new JSONObject();
    }
    if (rawType == EnumMap.class) {
      if (!keyRawType.isEnum()) {
        throw new ForyJsonException("EnumMap requires an enum key type");
      }
      return () -> new EnumMap(keyRawType);
    }
    if (rawType == UNTYPED_MAP || rawType.isInterface()) {
      if (ConcurrentMap.class.isAssignableFrom(rawType)) {
        if (NavigableMap.class.isAssignableFrom(rawType)
            || SortedMap.class.isAssignableFrom(rawType)) {
          return ConcurrentSkipListMap::new;
        }
        return ConcurrentHashMap::new;
      }
      if (NavigableMap.class.isAssignableFrom(rawType)
          || SortedMap.class.isAssignableFrom(rawType)) {
        return TreeMap::new;
      }
      return LinkedHashMap::new;
    }
    return () -> {
      try {
        return (Map<Object, Object>) rawType.newInstance();
      } catch (ReflectiveOperationException e) {
        throw new ForyJsonException("Cannot create map " + rawType, e);
      }
    };
  }

  private static boolean isNumericKey(Class<?> type) {
    return type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class
        || type == byte.class
        || type == Byte.class;
  }

  private interface MapFactory {
    Map<Object, Object> newMap();
  }

  private static final class GenericMapCodec extends MapCodec {
    private final MapKeyCodec keyCodec;
    private final JsonTypeInfo valueTypeInfo;
    private final JsonCodec valueCodec;

    private GenericMapCodec(
        TypeRef<?> typeRef,
        MapFactory factory,
        MapKeyCodec keyCodec,
        Type valueType,
        Class<?> valueRawType,
        JsonTypeResolver resolver) {
      super(typeRef, factory);
      this.keyCodec = keyCodec;
      valueTypeInfo = resolver.getTypeInfo(valueType, valueRawType);
      valueCodec = valueTypeInfo.codec();
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeObjectStart();
      int index = 0;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        writer.writeComma(index++);
        writeKey(writer, entry.getKey(), keyCodec);
        valueCodec.write(writer, entry.getValue(), resolver);
      }
      writer.writeObjectEnd();
    }

    @Override
    void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Map<Object, Object> map = newMap();
      readGeneric(reader, map, keyCodec, valueTypeInfo, valueCodec, resolver);
      return map;
    }
  }

  private abstract static class StringKeyMapCodec extends MapCodec {
    StringKeyMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    public final Object readLatin1(
        Latin1StringJsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          String key = reader.readString();
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : readLatin1Value(reader));
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }

    @Override
    public final Object readUtf16(
        Utf16StringJsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          String key = reader.readString();
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : readUtf16Value(reader));
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }

    @Override
    public final Object readUtf8(
        Utf8JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          String key = reader.readString();
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : readUtf8Value(reader));
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }

    abstract Object readLatin1Value(Latin1StringJsonReader reader);

    abstract Object readUtf16Value(Utf16StringJsonReader reader);

    abstract Object readUtf8Value(Utf8JsonReader reader);
  }

  private static final class StringBooleanMapCodec extends StringKeyMapCodec {
    private StringBooleanMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeObjectStart();
      int index = 0;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        writer.writeComma(index++);
        writer.writeFieldName((String) entry.getKey());
        Object element = entry.getValue();
        if (element == null) {
          writer.writeNull();
        } else {
          writer.writeBoolean((Boolean) element);
        }
      }
      writer.writeObjectEnd();
    }

    @Override
    void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Map<Object, Object> map = newMap();
      reader.expect('{');
      if (!reader.consume('}')) {
        do {
          String key = reader.readString();
          reader.expect(':');
          map.put(key, reader.tryReadNull() ? null : Boolean.valueOf(reader.readBoolean()));
        } while (reader.consume(','));
        reader.expect('}');
      }
      return map;
    }

    @Override
    Object readLatin1Value(Latin1StringJsonReader reader) {
      return Boolean.valueOf(reader.readBooleanValue());
    }

    @Override
    Object readUtf16Value(Utf16StringJsonReader reader) {
      return Boolean.valueOf(reader.readBooleanValue());
    }

    @Override
    Object readUtf8Value(Utf8JsonReader reader) {
      return Boolean.valueOf(reader.readBooleanValue());
    }
  }

  private abstract static class StringNumberMapCodec extends StringKeyMapCodec {
    StringNumberMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    final void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeObjectStart();
      int index = 0;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        writer.writeComma(index++);
        writer.writeFieldName((String) entry.getKey());
        Object element = entry.getValue();
        if (element == null) {
          writer.writeNull();
        } else {
          writeNumber(writer, element);
        }
      }
      writer.writeObjectEnd();
    }

    @Override
    final void writeStringNonNull(
        StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    final void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    final Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Map<Object, Object> map = newMap();
      reader.expect('{');
      if (!reader.consume('}')) {
        do {
          String key = reader.readString();
          reader.expect(':');
          map.put(key, reader.tryReadNull() ? null : readNumber(reader));
        } while (reader.consume(','));
        reader.expect('}');
      }
      return map;
    }

    abstract void writeNumber(JsonWriter writer, Object value);

    abstract Object readNumber(JsonReader reader);

    Object readLatin1Number(Latin1StringJsonReader reader) {
      return readNumber(reader);
    }

    Object readUtf16Number(Utf16StringJsonReader reader) {
      return readNumber(reader);
    }

    Object readUtf8Number(Utf8JsonReader reader) {
      return readNumber(reader);
    }

    @Override
    final Object readLatin1Value(Latin1StringJsonReader reader) {
      return readLatin1Number(reader);
    }

    @Override
    final Object readUtf16Value(Utf16StringJsonReader reader) {
      return readUtf16Number(reader);
    }

    @Override
    final Object readUtf8Value(Utf8JsonReader reader) {
      return readUtf8Number(reader);
    }
  }

  private static final class StringIntMapCodec extends StringNumberMapCodec {
    private StringIntMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeInt((Integer) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Integer.valueOf(reader.readInt());
    }

    @Override
    Object readLatin1Number(Latin1StringJsonReader reader) {
      return Integer.valueOf(reader.readIntValue());
    }

    @Override
    Object readUtf16Number(Utf16StringJsonReader reader) {
      return Integer.valueOf(reader.readIntValue());
    }

    @Override
    Object readUtf8Number(Utf8JsonReader reader) {
      return Integer.valueOf(reader.readIntValue());
    }
  }

  private static final class StringLongMapCodec extends StringNumberMapCodec {
    private StringLongMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeLong((Long) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Long.valueOf(reader.readLong());
    }

    @Override
    Object readLatin1Number(Latin1StringJsonReader reader) {
      return Long.valueOf(reader.readLongValue());
    }

    @Override
    Object readUtf16Number(Utf16StringJsonReader reader) {
      return Long.valueOf(reader.readLongValue());
    }

    @Override
    Object readUtf8Number(Utf8JsonReader reader) {
      return Long.valueOf(reader.readLongValue());
    }
  }

  private static final class StringShortMapCodec extends StringNumberMapCodec {
    private StringShortMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeInt((Short) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Short.valueOf(readShort(reader.readInt()));
    }

    @Override
    Object readLatin1Number(Latin1StringJsonReader reader) {
      return Short.valueOf(readShort(reader.readIntValue()));
    }

    @Override
    Object readUtf16Number(Utf16StringJsonReader reader) {
      return Short.valueOf(readShort(reader.readIntValue()));
    }

    @Override
    Object readUtf8Number(Utf8JsonReader reader) {
      return Short.valueOf(readShort(reader.readIntValue()));
    }
  }

  private static final class StringByteMapCodec extends StringNumberMapCodec {
    private StringByteMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeInt((Byte) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Byte.valueOf(readByte(reader.readInt()));
    }

    @Override
    Object readLatin1Number(Latin1StringJsonReader reader) {
      return Byte.valueOf(readByte(reader.readIntValue()));
    }

    @Override
    Object readUtf16Number(Utf16StringJsonReader reader) {
      return Byte.valueOf(readByte(reader.readIntValue()));
    }

    @Override
    Object readUtf8Number(Utf8JsonReader reader) {
      return Byte.valueOf(readByte(reader.readIntValue()));
    }
  }

  private static final class StringFloatMapCodec extends StringNumberMapCodec {
    private StringFloatMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeFloat((Float) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Float.valueOf(Float.parseFloat(reader.readNumber()));
    }
  }

  private static final class StringDoubleMapCodec extends StringNumberMapCodec {
    private StringDoubleMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeDouble((Double) value);
    }

    @Override
    Object readNumber(JsonReader reader) {
      return Double.valueOf(Double.parseDouble(reader.readNumber()));
    }
  }

  private static final class StringBigIntegerMapCodec extends StringNumberMapCodec {
    private StringBigIntegerMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeNumber(value.toString());
    }

    @Override
    Object readNumber(JsonReader reader) {
      return new BigInteger(reader.readNumber());
    }
  }

  private static final class StringBigDecimalMapCodec extends StringNumberMapCodec {
    private StringBigDecimalMapCodec(TypeRef<?> typeRef, MapFactory factory) {
      super(typeRef, factory);
    }

    @Override
    void writeNumber(JsonWriter writer, Object value) {
      writer.writeNumber(value.toString());
    }

    @Override
    Object readNumber(JsonReader reader) {
      return new BigDecimal(reader.readNumber());
    }
  }

  private static final class NumberStringMapCodec extends MapCodec {
    private final MapKeyCodec keyCodec;

    private NumberStringMapCodec(TypeRef<?> typeRef, MapFactory factory, MapKeyCodec keyCodec) {
      super(typeRef, factory);
      this.keyCodec = keyCodec;
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeObjectStart();
      int index = 0;
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        writer.writeComma(index++);
        writer.writeFieldName(keyCodec.toName(entry.getKey()));
        Object element = entry.getValue();
        if (element == null) {
          writer.writeNull();
        } else {
          writer.writeString((String) element);
        }
      }
      writer.writeObjectEnd();
    }

    @Override
    void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Map<Object, Object> map = newMap();
      reader.expect('{');
      if (!reader.consume('}')) {
        do {
          Object key = keyCodec.fromName(reader.readString());
          reader.expect(':');
          map.put(key, reader.tryReadNull() ? null : reader.readString());
        } while (reader.consume(','));
        reader.expect('}');
      }
      return map;
    }

    @Override
    public Object readLatin1(
        Latin1StringJsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          Object key = keyCodec.fromName(reader.readString());
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : reader.readString());
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }

    @Override
    public Object readUtf16(
        Utf16StringJsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          Object key = keyCodec.fromName(reader.readString());
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : reader.readString());
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }

    @Override
    public Object readUtf8(
        Utf8JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      Map<Object, Object> map = newMap();
      reader.expectToken('{');
      if (!reader.consumeToken('}')) {
        do {
          Object key = keyCodec.fromName(reader.readString());
          reader.expectToken(':');
          map.put(key, reader.tryReadNullToken() ? null : reader.readString());
        } while (reader.consumeToken(','));
        reader.expectToken('}');
      }
      return map;
    }
  }

  private interface MapKeyCodec {
    MapKeyCodec STRING =
        new MapKeyCodec() {
          @Override
          public String toName(Object key) {
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
      if (rawType == short.class || rawType == Short.class) {
        return ShortKeyCodec.INSTANCE;
      }
      if (rawType == byte.class || rawType == Byte.class) {
        return ByteKeyCodec.INSTANCE;
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

  private static final class ShortKeyCodec implements MapKeyCodec {
    private static final ShortKeyCodec INSTANCE = new ShortKeyCodec();

    @Override
    public String toName(Object key) {
      return String.valueOf((Short) key);
    }

    @Override
    public Object fromName(String name) {
      return Short.valueOf(readShort(Integer.parseInt(name)));
    }
  }

  private static final class ByteKeyCodec implements MapKeyCodec {
    private static final ByteKeyCodec INSTANCE = new ByteKeyCodec();

    @Override
    public String toName(Object key) {
      return String.valueOf((Byte) key);
    }

    @Override
    public Object fromName(String name) {
      return Byte.valueOf(readByte(Integer.parseInt(name)));
    }
  }

  private static short readShort(int value) {
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
      throw new ForyJsonException("Short overflow");
    }
    return (short) value;
  }

  private static byte readByte(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
      throw new ForyJsonException("Byte overflow");
    }
    return (byte) value;
  }
}
