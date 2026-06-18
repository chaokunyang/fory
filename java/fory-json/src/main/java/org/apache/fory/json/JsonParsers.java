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

package org.apache.fory.json;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonParsers {
  private JsonParsers() {}

  static <T> T fromString(String json, Class<T> type, JsonClassCache classCache) {
    JsonReader reader = new StringJsonReader(json);
    Object value = readValue(reader, type, type, classCache);
    reader.finish();
    return castValue(value, type);
  }

  static <T> T fromBytes(byte[] bytes, Class<T> type, JsonClassCache classCache) {
    JsonReader reader = new Utf8JsonReader(bytes);
    Object value = readValue(reader, type, type, classCache);
    reader.finish();
    return castValue(value, type);
  }

  @SuppressWarnings("unchecked")
  private static <T> T castValue(Object value, Class<T> type) {
    return type.isPrimitive() ? (T) value : type.cast(value);
  }

  private static Object readValue(
      JsonReader reader, Type type, Class<?> fallback, JsonClassCache classCache) {
    if (reader.peekNull()) {
      reader.readNull();
      if (fallback != null && fallback.isPrimitive()) {
        throw new ForyJsonException("Cannot read null into primitive " + fallback);
      }
      return null;
    }
    Class<?> rawType = JsonSerializers.rawType(type, fallback);
    if (rawType == Object.class) {
      return readNatural(reader, classCache);
    } else if (rawType == String.class) {
      return reader.readString();
    } else if (rawType == boolean.class || rawType == Boolean.class) {
      return Boolean.valueOf(reader.readBoolean());
    } else if (rawType == int.class || rawType == Integer.class) {
      return Integer.valueOf(Integer.parseInt(reader.readNumber()));
    } else if (rawType == long.class || rawType == Long.class) {
      return Long.valueOf(Long.parseLong(reader.readNumber()));
    } else if (rawType == short.class || rawType == Short.class) {
      return Short.valueOf(Short.parseShort(reader.readNumber()));
    } else if (rawType == byte.class || rawType == Byte.class) {
      return Byte.valueOf(Byte.parseByte(reader.readNumber()));
    } else if (rawType == float.class || rawType == Float.class) {
      return Float.valueOf(Float.parseFloat(reader.readNumber()));
    } else if (rawType == double.class || rawType == Double.class) {
      return Double.valueOf(Double.parseDouble(reader.readNumber()));
    } else if (rawType == char.class || rawType == Character.class) {
      String value = reader.readString();
      if (value.length() != 1) {
        throw new ForyJsonException("Expected one-character JSON string for char");
      }
      return Character.valueOf(value.charAt(0));
    } else if (rawType.isEnum()) {
      return enumValue(rawType, reader.readString());
    } else if (rawType.isArray()) {
      return readArray(reader, rawType.getComponentType(), classCache);
    } else if (Collection.class.isAssignableFrom(rawType)) {
      return readCollection(reader, rawType, JsonSerializers.elementType(type), classCache);
    } else if (Map.class.isAssignableFrom(rawType)) {
      return readMap(reader, rawType, JsonSerializers.mapValueType(type), classCache);
    }
    return readObject(reader, rawType, classCache);
  }

  private static Object readNatural(JsonReader reader, JsonClassCache classCache) {
    char token = reader.peekToken();
    if (token == '"') {
      return reader.readString();
    } else if (token == '{') {
      return readMap(reader, LinkedHashMap.class, Object.class, classCache);
    } else if (token == '[') {
      return readCollection(reader, ArrayList.class, Object.class, classCache);
    } else if (token == 't' || token == 'f') {
      return Boolean.valueOf(reader.readBoolean());
    } else if (token == 'n') {
      reader.readNull();
      return null;
    }
    String number = reader.readNumber();
    if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
      return Double.valueOf(Double.parseDouble(number));
    }
    return Long.valueOf(Long.parseLong(number));
  }

  private static Object readObject(JsonReader reader, Class<?> rawType, JsonClassCache classCache) {
    JsonClassInfo classInfo = classCache.get(rawType);
    Object object = classInfo.newInstance();
    reader.expect('{');
    if (reader.consume('}')) {
      return object;
    }
    do {
      String name = reader.readString();
      reader.expect(':');
      JsonPropertyInfo property = classInfo.readTable().get(name);
      if (property == null) {
        reader.skipValue();
      } else {
        readProperty(reader, object, property, classCache);
      }
    } while (reader.consume(','));
    reader.expect('}');
    return object;
  }

  private static void readProperty(
      JsonReader reader, Object object, JsonPropertyInfo property, JsonClassCache classCache) {
    JsonMemberAccessor accessor = property.readAccessor();
    JsonPropertyKind kind = property.readKind();
    if (reader.peekNull()) {
      reader.readNull();
      if (property.readRawType().isPrimitive()) {
        throw new ForyJsonException("Cannot read null into primitive property " + property.name());
      }
      accessor.putObject(object, null);
      return;
    }
    switch (kind) {
      case BOOLEAN:
        boolean booleanValue = reader.readBoolean();
        if (property.readRawType().isPrimitive()) {
          accessor.putBoolean(object, booleanValue);
        } else {
          accessor.putObject(object, Boolean.valueOf(booleanValue));
        }
        return;
      case BYTE:
        byte byteValue = Byte.parseByte(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putByte(object, byteValue);
        } else {
          accessor.putObject(object, Byte.valueOf(byteValue));
        }
        return;
      case SHORT:
        short shortValue = Short.parseShort(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putShort(object, shortValue);
        } else {
          accessor.putObject(object, Short.valueOf(shortValue));
        }
        return;
      case INT:
        int intValue = Integer.parseInt(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putInt(object, intValue);
        } else {
          accessor.putObject(object, Integer.valueOf(intValue));
        }
        return;
      case LONG:
        long longValue = Long.parseLong(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putLong(object, longValue);
        } else {
          accessor.putObject(object, Long.valueOf(longValue));
        }
        return;
      case FLOAT:
        float floatValue = Float.parseFloat(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putFloat(object, floatValue);
        } else {
          accessor.putObject(object, Float.valueOf(floatValue));
        }
        return;
      case DOUBLE:
        double doubleValue = Double.parseDouble(reader.readNumber());
        if (property.readRawType().isPrimitive()) {
          accessor.putDouble(object, doubleValue);
        } else {
          accessor.putObject(object, Double.valueOf(doubleValue));
        }
        return;
      case CHAR:
        String value = reader.readString();
        if (value.length() != 1) {
          throw new ForyJsonException("Expected one-character JSON string for char");
        }
        char charValue = value.charAt(0);
        if (property.readRawType().isPrimitive()) {
          accessor.putChar(object, charValue);
        } else {
          accessor.putObject(object, Character.valueOf(charValue));
        }
        return;
      default:
        accessor.putObject(
            object, readValue(reader, property.readType(), property.readRawType(), classCache));
    }
  }

  private static Object readArray(
      JsonReader reader, Class<?> componentType, JsonClassCache classCache) {
    List<Object> values = new ArrayList<>();
    reader.expect('[');
    if (!reader.consume(']')) {
      do {
        values.add(readValue(reader, componentType, componentType, classCache));
      } while (reader.consume(','));
      reader.expect(']');
    }
    Object array = Array.newInstance(componentType, values.size());
    for (int i = 0; i < values.size(); i++) {
      Array.set(array, i, values.get(i));
    }
    return array;
  }

  private static Collection<Object> readCollection(
      JsonReader reader, Class<?> rawType, Type elementType, JsonClassCache classCache) {
    Collection<Object> collection = newCollection(rawType);
    reader.expect('[');
    if (!reader.consume(']')) {
      Class<?> elementRawType = JsonSerializers.rawType(elementType, Object.class);
      do {
        collection.add(readValue(reader, elementType, elementRawType, classCache));
      } while (reader.consume(','));
      reader.expect(']');
    }
    return collection;
  }

  private static Map<String, Object> readMap(
      JsonReader reader, Class<?> rawType, Type valueType, JsonClassCache classCache) {
    Map<String, Object> map = newMap(rawType);
    reader.expect('{');
    if (!reader.consume('}')) {
      Class<?> valueRawType = JsonSerializers.rawType(valueType, Object.class);
      do {
        String name = reader.readString();
        reader.expect(':');
        map.put(name, readValue(reader, valueType, valueRawType, classCache));
      } while (reader.consume(','));
      reader.expect('}');
    }
    return map;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object enumValue(Class<?> rawType, String name) {
    return Enum.valueOf((Class<? extends Enum>) rawType, name);
  }

  @SuppressWarnings("unchecked")
  private static Collection<Object> newCollection(Class<?> rawType) {
    if (rawType.isInterface()) {
      if (Set.class.isAssignableFrom(rawType)) {
        return new LinkedHashSet<>();
      }
      return new ArrayList<>();
    }
    try {
      return (Collection<Object>) rawType.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new ForyJsonException("Cannot create collection " + rawType, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> newMap(Class<?> rawType) {
    if (rawType.isInterface()) {
      return new LinkedHashMap<>();
    }
    try {
      return (Map<String, Object>) rawType.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new ForyJsonException("Cannot create map " + rawType, e);
    }
  }
}
