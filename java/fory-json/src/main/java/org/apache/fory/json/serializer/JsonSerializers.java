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

package org.apache.fory.json.serializer;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class JsonSerializers {
  private JsonSerializers() {}

  public static void writeArray(
      JsonWriter writer, Object value, Class<?> componentType, JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    if (componentType == int.class) {
      int[] array = (int[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == long.class) {
      long[] array = (long[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeLong(array[i]);
      }
    } else if (componentType == boolean.class) {
      boolean[] array = (boolean[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeBoolean(array[i]);
      }
    } else if (componentType == short.class) {
      short[] array = (short[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == byte.class) {
      byte[] array = (byte[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == char.class) {
      char[] array = (char[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeChar(array[i]);
      }
    } else if (componentType == float.class) {
      float[] array = (float[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeFloat(array[i]);
      }
    } else if (componentType == double.class) {
      double[] array = (double[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeDouble(array[i]);
      }
    } else {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        writer.writeComma(i);
        Object element = Array.get(value, i);
        typeResolver.writeValue(writer, element, componentType);
      }
    }
    writer.writeArrayEnd();
  }

  public static void writeCollection(
      JsonWriter writer,
      Collection<?> collection,
      Type elementType,
      JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : collection) {
      writer.writeComma(index++);
      typeResolver.writeValue(writer, element, elementType);
    }
    writer.writeArrayEnd();
  }

  public static void writeMap(
      JsonWriter writer, Map<?, ?> map, Type valueType, JsonTypeResolver typeResolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      typeResolver.writeValue(writer, entry.getValue(), valueType);
    }
    writer.writeObjectEnd();
  }

  public static void writeArray(
      StringJsonWriter writer,
      Object value,
      Class<?> componentType,
      JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    if (componentType == int.class) {
      int[] array = (int[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == long.class) {
      long[] array = (long[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeLong(array[i]);
      }
    } else if (componentType == boolean.class) {
      boolean[] array = (boolean[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeBoolean(array[i]);
      }
    } else if (componentType == short.class) {
      short[] array = (short[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == byte.class) {
      byte[] array = (byte[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == char.class) {
      char[] array = (char[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeChar(array[i]);
      }
    } else if (componentType == float.class) {
      float[] array = (float[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeFloat(array[i]);
      }
    } else if (componentType == double.class) {
      double[] array = (double[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeDouble(array[i]);
      }
    } else {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        writer.writeComma(i);
        Object element = Array.get(value, i);
        typeResolver.writeStringValue(writer, element, componentType);
      }
    }
    writer.writeArrayEnd();
  }

  public static void writeCollection(
      StringJsonWriter writer,
      Collection<?> collection,
      Type elementType,
      JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : collection) {
      writer.writeComma(index++);
      typeResolver.writeStringValue(writer, element, elementType);
    }
    writer.writeArrayEnd();
  }

  public static void writeMap(
      StringJsonWriter writer, Map<?, ?> map, Type valueType, JsonTypeResolver typeResolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      typeResolver.writeStringValue(writer, entry.getValue(), valueType);
    }
    writer.writeObjectEnd();
  }

  public static void writeUtf8Array(
      Utf8JsonWriter writer, Object value, Class<?> componentType, JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    if (componentType == int.class) {
      int[] array = (int[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == long.class) {
      long[] array = (long[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeLong(array[i]);
      }
    } else if (componentType == boolean.class) {
      boolean[] array = (boolean[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeBoolean(array[i]);
      }
    } else if (componentType == short.class) {
      short[] array = (short[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == byte.class) {
      byte[] array = (byte[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeInt(array[i]);
      }
    } else if (componentType == char.class) {
      char[] array = (char[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeChar(array[i]);
      }
    } else if (componentType == float.class) {
      float[] array = (float[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeFloat(array[i]);
      }
    } else if (componentType == double.class) {
      double[] array = (double[]) value;
      for (int i = 0; i < array.length; i++) {
        writer.writeComma(i);
        writer.writeDouble(array[i]);
      }
    } else {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        writer.writeComma(i);
        Object element = Array.get(value, i);
        typeResolver.writeUtf8Value(writer, element, componentType);
      }
    }
    writer.writeArrayEnd();
  }

  public static void writeUtf8Collection(
      Utf8JsonWriter writer,
      Collection<?> collection,
      Type elementType,
      JsonTypeResolver typeResolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : collection) {
      writer.writeComma(index++);
      typeResolver.writeUtf8Value(writer, element, elementType);
    }
    writer.writeArrayEnd();
  }

  public static void writeUtf8Map(
      Utf8JsonWriter writer, Map<?, ?> map, Type valueType, JsonTypeResolver typeResolver) {
    writer.writeObjectStart();
    int index = 0;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw new ForyJsonException("Only String map keys are supported for JSON, got " + key);
      }
      writer.writeComma(index++);
      writer.writeFieldName((String) key);
      typeResolver.writeUtf8Value(writer, entry.getValue(), valueType);
    }
    writer.writeObjectEnd();
  }

  public static Class<?> rawType(Type type, Class<?> fallback) {
    if (type instanceof Class) {
      Class<?> rawType = (Class<?>) type;
      if (rawType == Object.class && fallback != null) {
        return fallback;
      }
      return rawType;
    }
    if (type instanceof ParameterizedType) {
      Type rawType = ((ParameterizedType) type).getRawType();
      if (rawType instanceof Class) {
        return (Class<?>) rawType;
      }
    }
    return fallback == null ? Object.class : fallback;
  }

  public static Type elementType(Type type) {
    if (type instanceof ParameterizedType) {
      Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
      if (arguments.length == 1) {
        return arguments[0];
      }
    }
    return Object.class;
  }

  public static Type mapValueType(Type type) {
    if (type instanceof ParameterizedType) {
      Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
      if (arguments.length == 2) {
        return arguments[1];
      }
    }
    return Object.class;
  }
}
