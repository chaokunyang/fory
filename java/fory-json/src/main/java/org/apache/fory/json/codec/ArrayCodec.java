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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class ArrayCodec extends AbstractJsonCodec {
  private final Class<?> componentType;
  private final ArrayAccess access;

  public ArrayCodec(Class<?> componentType, JsonTypeResolver resolver) {
    this.componentType = componentType;
    access = access(componentType, resolver);
  }

  @Override
  void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    access.write(writer, value, resolver);
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    access.writeString(writer, value, resolver);
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    access.writeUtf8(writer, value, resolver);
  }

  @Override
  Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
    return access.read(reader, componentType, resolver);
  }

  private static ArrayAccess access(Class<?> componentType, JsonTypeResolver resolver) {
    if (componentType == int.class) {
      return IntArrayAccess.INSTANCE;
    } else if (componentType == long.class) {
      return LongArrayAccess.INSTANCE;
    } else if (componentType == boolean.class) {
      return BooleanArrayAccess.INSTANCE;
    } else if (componentType == short.class) {
      return ShortArrayAccess.INSTANCE;
    } else if (componentType == byte.class) {
      return ByteArrayAccess.INSTANCE;
    } else if (componentType == char.class) {
      return CharArrayAccess.INSTANCE;
    } else if (componentType == float.class) {
      return FloatArrayAccess.INSTANCE;
    } else if (componentType == double.class) {
      return DoubleArrayAccess.INSTANCE;
    }
    return new ObjectArrayAccess(resolver.getTypeInfo(componentType, componentType));
  }

  private interface ArrayAccess {
    void write(JsonWriter writer, Object value, JsonTypeResolver resolver);

    void writeString(StringJsonWriter writer, Object value, JsonTypeResolver resolver);

    void writeUtf8(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver);

    Object read(JsonReader reader, Class<?> componentType, JsonTypeResolver resolver);
  }

  private abstract static class PrimitiveArrayAccess implements ArrayAccess {
    @Override
    public final void writeString(
        StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      write(writer, value, resolver);
    }

    @Override
    public final void writeUtf8(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      write(writer, value, resolver);
    }

    @Override
    public final Object read(JsonReader reader, Class<?> componentType, JsonTypeResolver resolver) {
      return readArray(reader, componentType, resolver);
    }
  }

  private static final class IntArrayAccess extends PrimitiveArrayAccess {
    private static final IntArrayAccess INSTANCE = new IntArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      int[] array = (int[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class LongArrayAccess extends PrimitiveArrayAccess {
    private static final LongArrayAccess INSTANCE = new LongArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      long[] array = (long[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeLong(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class BooleanArrayAccess extends PrimitiveArrayAccess {
    private static final BooleanArrayAccess INSTANCE = new BooleanArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      boolean[] array = (boolean[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeBoolean(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class ShortArrayAccess extends PrimitiveArrayAccess {
    private static final ShortArrayAccess INSTANCE = new ShortArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      short[] array = (short[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class ByteArrayAccess extends PrimitiveArrayAccess {
    private static final ByteArrayAccess INSTANCE = new ByteArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      byte[] array = (byte[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class CharArrayAccess extends PrimitiveArrayAccess {
    private static final CharArrayAccess INSTANCE = new CharArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      char[] array = (char[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeChar(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class FloatArrayAccess extends PrimitiveArrayAccess {
    private static final FloatArrayAccess INSTANCE = new FloatArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      float[] array = (float[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeFloat(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class DoubleArrayAccess extends PrimitiveArrayAccess {
    private static final DoubleArrayAccess INSTANCE = new DoubleArrayAccess();

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      double[] array = (double[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeDouble(array[i]);
      }
      writer.writeArrayEnd();
    }
  }

  private static final class ObjectArrayAccess implements ArrayAccess {
    private final JsonTypeInfo elementTypeInfo;
    private final JsonCodec elementCodec;

    private ObjectArrayAccess(JsonTypeInfo elementTypeInfo) {
      this.elementTypeInfo = elementTypeInfo;
      elementCodec = elementTypeInfo.codec();
    }

    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Object[] array = (Object[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        elementCodec.write(writer, array[i], resolver);
      }
      writer.writeArrayEnd();
    }

    @Override
    public void writeString(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      Object[] array = (Object[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        elementCodec.writeString(writer, array[i], resolver);
      }
      writer.writeArrayEnd();
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Object[] array = (Object[]) value;
      writer.writeArrayStart();
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        elementCodec.writeUtf8(writer, array[i], resolver);
      }
      writer.writeArrayEnd();
    }

    @Override
    public Object read(JsonReader reader, Class<?> componentType, JsonTypeResolver resolver) {
      List<Object> values = new ArrayList<>();
      reader.expect('[');
      if (!reader.consume(']')) {
        do {
          values.add(elementCodec.read(reader, elementTypeInfo, resolver));
        } while (reader.consume(','));
        reader.expect(']');
      }
      Object array = Array.newInstance(componentType, values.size());
      for (int i = 0; i < values.size(); i++) {
        Array.set(array, i, values.get(i));
      }
      return array;
    }
  }

  private static Object readArray(
      JsonReader reader, Class<?> componentType, JsonTypeResolver resolver) {
    JsonTypeInfo elementTypeInfo = resolver.getTypeInfo(componentType, componentType);
    JsonCodec elementCodec = elementTypeInfo.codec();
    List<Object> values = new ArrayList<>();
    reader.expect('[');
    if (!reader.consume(']')) {
      do {
        values.add(elementCodec.read(reader, elementTypeInfo, resolver));
      } while (reader.consume(','));
      reader.expect(']');
    }
    Object array = Array.newInstance(componentType, values.size());
    for (int i = 0; i < values.size(); i++) {
      Array.set(array, i, values.get(i));
    }
    return array;
  }
}
