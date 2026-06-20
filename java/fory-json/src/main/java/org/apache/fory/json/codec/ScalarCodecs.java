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

import java.util.HashMap;
import java.util.Map;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.meta.JsonMemberAccessor;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

final class ScalarCodecs {
  private ScalarCodecs() {}

  static final class NaturalCodec extends AbstractJsonCodec {
    static final NaturalCodec INSTANCE = new NaturalCodec();

    private NaturalCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        resolver.getObjectCodec(Object.class).write(writer, value, resolver);
      } else {
        resolver.writeValue(writer, value, valueClass);
      }
    }

    @Override
    void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        resolver.getObjectCodec(Object.class).writeString(writer, value, resolver);
      } else {
        resolver.writeStringValue(writer, value, valueClass);
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        resolver.getObjectCodec(Object.class).writeUtf8(writer, value, resolver);
      } else {
        resolver.writeUtf8Value(writer, value, valueClass);
      }
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      char token = reader.peekToken();
      if (token == '"') {
        return reader.readString();
      } else if (token == '{') {
        return MapCodec.readUntyped(reader, resolver);
      } else if (token == '[') {
        return CollectionCodec.readUntyped(reader, resolver);
      } else if (token == 't' || token == 'f') {
        return reader.readBoolean();
      } else if (token == 'n') {
        reader.readNull();
        return null;
      }
      String number = reader.readNumber();
      if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
        return Double.parseDouble(number);
      }
      return Long.parseLong(number);
    }
  }

  static final class StringCodec extends AbstractJsonCodec {
    static final StringCodec INSTANCE = new StringCodec();

    private StringCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readString();
    }
  }

  static final class BooleanCodec extends AbstractJsonCodec {
    static final BooleanCodec INSTANCE = new BooleanCodec();

    private BooleanCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean((Boolean) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean((Boolean) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readBoolean();
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putBoolean(object, reader.readBoolean());
      } else {
        accessor.putObject(object, reader.readBoolean());
      }
    }
  }

  static final class IntCodec extends AbstractJsonCodec {
    static final IntCodec INSTANCE = new IntCodec();

    private IntCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt((Integer) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt((Integer) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Integer.parseInt(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putInt(object, Integer.parseInt(reader.readNumber()));
      } else {
        accessor.putObject(object, Integer.parseInt(reader.readNumber()));
      }
    }
  }

  static final class LongCodec extends AbstractJsonCodec {
    static final LongCodec INSTANCE = new LongCodec();

    private LongCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong((Long) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong((Long) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Long.parseLong(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putLong(object, Long.parseLong(reader.readNumber()));
      } else {
        accessor.putObject(object, Long.parseLong(reader.readNumber()));
      }
    }
  }

  static final class ShortCodec extends AbstractJsonCodec {
    static final ShortCodec INSTANCE = new ShortCodec();

    private ShortCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Short.parseShort(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putShort(object, Short.parseShort(reader.readNumber()));
      } else {
        accessor.putObject(object, Short.parseShort(reader.readNumber()));
      }
    }
  }

  static final class ByteCodec extends AbstractJsonCodec {
    static final ByteCodec INSTANCE = new ByteCodec();

    private ByteCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Byte.parseByte(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putByte(object, Byte.parseByte(reader.readNumber()));
      } else {
        accessor.putObject(object, Byte.parseByte(reader.readNumber()));
      }
    }
  }

  static final class FloatCodec extends AbstractJsonCodec {
    static final FloatCodec INSTANCE = new FloatCodec();

    private FloatCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat((Float) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat((Float) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Float.parseFloat(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putFloat(object, Float.parseFloat(reader.readNumber()));
      } else {
        accessor.putObject(object, Float.parseFloat(reader.readNumber()));
      }
    }
  }

  static final class DoubleCodec extends AbstractJsonCodec {
    static final DoubleCodec INSTANCE = new DoubleCodec();

    private DoubleCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble((Double) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble((Double) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Double.parseDouble(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putDouble(object, Double.parseDouble(reader.readNumber()));
      } else {
        accessor.putObject(object, Double.parseDouble(reader.readNumber()));
      }
    }
  }

  static final class CharCodec extends AbstractJsonCodec {
    static final CharCodec INSTANCE = new CharCodec();

    private CharCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar((Character) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar((Character) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      String value = reader.readString();
      if (value.length() != 1) {
        throw new ForyJsonException("Expected one-character JSON string for char");
      }
      return value.charAt(0);
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonMemberAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else {
        char value = (Character) readNonNull(reader, typeInfo, resolver);
        if (typeInfo.primitive()) {
          accessor.putChar(object, value);
        } else {
          accessor.putObject(object, value);
        }
      }
    }
  }

  static final class EnumCodec extends AbstractJsonCodec {
    private final Class<?> type;
    private final Map<String, Enum<?>> values;

    EnumCodec(Class<?> type) {
      this.type = type;
      Enum<?>[] constants = (Enum<?>[]) type.getEnumConstants();
      values = new HashMap<>(constants.length * 2);
      for (Enum<?> constant : constants) {
        values.put(constant.name(), constant);
      }
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(((Enum<?>) value).name());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(((Enum<?>) value).name());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      String name = reader.readString();
      Enum<?> value = values.get(name);
      if (value == null) {
        throw new ForyJsonException("Unknown enum value " + name + " for " + type);
      }
      return value;
    }
  }
}
