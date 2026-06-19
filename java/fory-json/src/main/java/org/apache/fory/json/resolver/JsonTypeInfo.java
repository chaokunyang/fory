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
import java.util.HashMap;
import java.util.Map;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.meta.JsonClassInfo;
import org.apache.fory.json.meta.JsonMemberAccessor;
import org.apache.fory.json.reader.JsonParsers;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.serializer.JsonSerializers;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

/** Resolved JSON read/write behavior for one declared Java type shape. */
public abstract class JsonTypeInfo {
  private final Class<?> rawType;
  private final boolean primitive;

  JsonTypeInfo(Class<?> rawType) {
    this.rawType = rawType;
    primitive = rawType.isPrimitive();
  }

  public final Class<?> rawType() {
    return rawType;
  }

  public final boolean primitive() {
    return primitive;
  }

  public final void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    if (value == null) {
      writer.writeNull();
    } else {
      writeNonNull(writer, value, resolver);
    }
  }

  public final void writeUtf8(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    if (value == null) {
      writer.writeNull();
    } else {
      writeUtf8NonNull(writer, value, resolver);
    }
  }

  public final Object read(JsonReader reader, JsonTypeResolver resolver) {
    if (reader.peekNull()) {
      reader.readNull();
      if (primitive) {
        throw new ForyJsonException("Cannot read null into primitive " + rawType);
      }
      return null;
    }
    return readNonNull(reader, resolver);
  }

  public void readField(
      JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
    accessor.putObject(object, read(reader, resolver));
  }

  abstract void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver);

  abstract void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver);

  abstract Object readNonNull(JsonReader reader, JsonTypeResolver resolver);

  static final class NaturalInfo extends JsonTypeInfo {
    NaturalInfo() {
      super(Object.class);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        resolver.getClassInfo(Object.class).write(writer, value, resolver);
      } else {
        resolver.writeValue(writer, value, valueClass);
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == Object.class) {
        resolver.getClassInfo(Object.class).writeUtf8(writer, value, resolver);
      } else {
        resolver.writeUtf8Value(writer, value, valueClass);
      }
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return JsonParsers.readNatural(reader, resolver);
    }
  }

  static final class StringInfo extends JsonTypeInfo {
    StringInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return reader.readString();
    }
  }

  static final class BooleanInfo extends JsonTypeInfo {
    BooleanInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean(((Boolean) value).booleanValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean(((Boolean) value).booleanValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Boolean.valueOf(reader.readBoolean());
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putBoolean(object, reader.readBoolean());
      } else {
        accessor.putObject(object, Boolean.valueOf(reader.readBoolean()));
      }
    }
  }

  static final class IntInfo extends JsonTypeInfo {
    IntInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Integer) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Integer) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Integer.valueOf(Integer.parseInt(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putInt(object, Integer.parseInt(reader.readNumber()));
      } else {
        accessor.putObject(object, Integer.valueOf(Integer.parseInt(reader.readNumber())));
      }
    }
  }

  static final class LongInfo extends JsonTypeInfo {
    LongInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Long) value).longValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Long) value).longValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Long.valueOf(Long.parseLong(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putLong(object, Long.parseLong(reader.readNumber()));
      } else {
        accessor.putObject(object, Long.valueOf(Long.parseLong(reader.readNumber())));
      }
    }
  }

  static final class ShortInfo extends JsonTypeInfo {
    ShortInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Short.valueOf(Short.parseShort(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putShort(object, Short.parseShort(reader.readNumber()));
      } else {
        accessor.putObject(object, Short.valueOf(Short.parseShort(reader.readNumber())));
      }
    }
  }

  static final class ByteInfo extends JsonTypeInfo {
    ByteInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Byte.valueOf(Byte.parseByte(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putByte(object, Byte.parseByte(reader.readNumber()));
      } else {
        accessor.putObject(object, Byte.valueOf(Byte.parseByte(reader.readNumber())));
      }
    }
  }

  static final class FloatInfo extends JsonTypeInfo {
    FloatInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((Float) value).floatValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((Float) value).floatValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Float.valueOf(Float.parseFloat(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putFloat(object, Float.parseFloat(reader.readNumber()));
      } else {
        accessor.putObject(object, Float.valueOf(Float.parseFloat(reader.readNumber())));
      }
    }
  }

  static final class DoubleInfo extends JsonTypeInfo {
    DoubleInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble(((Double) value).doubleValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble(((Double) value).doubleValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return Double.valueOf(Double.parseDouble(reader.readNumber()));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else if (primitive()) {
        accessor.putDouble(object, Double.parseDouble(reader.readNumber()));
      } else {
        accessor.putObject(object, Double.valueOf(Double.parseDouble(reader.readNumber())));
      }
    }
  }

  static final class CharInfo extends JsonTypeInfo {
    CharInfo(Class<?> rawType) {
      super(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar(((Character) value).charValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar(((Character) value).charValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      String value = reader.readString();
      if (value.length() != 1) {
        throw new ForyJsonException("Expected one-character JSON string for char");
      }
      return Character.valueOf(value.charAt(0));
    }

    @Override
    public void readField(
        JsonReader reader, Object object, JsonMemberAccessor accessor, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        super.readField(reader, object, accessor, resolver);
      } else {
        char value = ((Character) readNonNull(reader, resolver)).charValue();
        if (primitive()) {
          accessor.putChar(object, value);
        } else {
          accessor.putObject(object, Character.valueOf(value));
        }
      }
    }
  }

  static final class EnumInfo extends JsonTypeInfo {
    private final Map<String, Enum<?>> values;

    EnumInfo(Class<?> rawType) {
      super(rawType);
      Enum<?>[] constants = (Enum<?>[]) rawType.getEnumConstants();
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
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      String name = reader.readString();
      Enum<?> value = values.get(name);
      if (value == null) {
        throw new ForyJsonException("Unknown enum value " + name + " for " + rawType());
      }
      return value;
    }
  }

  static final class ArrayInfo extends JsonTypeInfo {
    private final Class<?> componentType;

    ArrayInfo(Class<?> rawType) {
      super(rawType);
      componentType = rawType.getComponentType();
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeArray(writer, value, componentType, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeUtf8Array(writer, value, componentType, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return JsonParsers.readArray(reader, componentType, resolver);
    }
  }

  static final class CollectionInfo extends JsonTypeInfo {
    private final Type elementType;

    CollectionInfo(Class<?> rawType, Type declaredType) {
      super(rawType);
      elementType = JsonSerializers.elementType(declaredType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeCollection(writer, (Collection<?>) value, elementType, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeUtf8Collection(writer, (Collection<?>) value, elementType, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return JsonParsers.readCollection(reader, rawType(), elementType, resolver);
    }
  }

  static final class MapInfo extends JsonTypeInfo {
    private final Type valueType;

    MapInfo(Class<?> rawType, Type declaredType) {
      super(rawType);
      valueType = JsonSerializers.mapValueType(declaredType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeMap(writer, (Map<?, ?>) value, valueType, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonSerializers.writeUtf8Map(writer, (Map<?, ?>) value, valueType, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return JsonParsers.readMap(reader, rawType(), valueType, resolver);
    }
  }

  static final class ObjectInfo extends JsonTypeInfo {
    private final JsonClassInfo classInfo;

    ObjectInfo(Class<?> rawType, JsonTypeResolver resolver) {
      super(rawType);
      classInfo = resolver.getClassInfo(rawType);
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == rawType()) {
        classInfo.write(writer, value, resolver);
      } else {
        resolver.writeValue(writer, value, valueClass);
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Class<?> valueClass = value.getClass();
      if (valueClass == rawType()) {
        classInfo.writeUtf8(writer, value, resolver);
      } else {
        resolver.writeUtf8Value(writer, value, valueClass);
      }
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeResolver resolver) {
      return JsonParsers.readObject(reader, rawType(), resolver);
    }
  }
}
