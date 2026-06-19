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

package org.apache.fory.json.reader;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.meta.JsonClassInfo;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.serializer.JsonSerializers;

public final class JsonParsers {
  private JsonParsers() {}

  public static <T> T fromString(String json, Class<T> type, JsonTypeResolver typeResolver) {
    JsonReader reader = new StringJsonReader(json);
    Object value = typeResolver.readValue(reader, type, type);
    reader.finish();
    return castValue(value, type);
  }

  public static <T> T fromBytes(byte[] bytes, Class<T> type, JsonTypeResolver typeResolver) {
    JsonReader reader = new Utf8JsonReader(bytes);
    Object value = typeResolver.readValue(reader, type, type);
    reader.finish();
    return castValue(value, type);
  }

  @SuppressWarnings("unchecked")
  private static <T> T castValue(Object value, Class<T> type) {
    return type.isPrimitive() ? (T) value : type.cast(value);
  }

  public static Object readNatural(JsonReader reader, JsonTypeResolver typeResolver) {
    char token = reader.peekToken();
    if (token == '"') {
      return reader.readString();
    } else if (token == '{') {
      return readMap(reader, LinkedHashMap.class, Object.class, typeResolver);
    } else if (token == '[') {
      return readCollection(reader, ArrayList.class, Object.class, typeResolver);
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

  public static Object readObject(
      JsonReader reader, Class<?> rawType, JsonTypeResolver typeResolver) {
    JsonClassInfo classInfo = typeResolver.getClassInfo(rawType);
    Object object = classInfo.newInstance();
    reader.expect('{');
    if (reader.consume('}')) {
      return object;
    }
    do {
      String name = reader.readString();
      reader.expect(':');
      JsonFieldInfo property = classInfo.readTable().get(name);
      if (property == null) {
        reader.skipValue();
      } else {
        property.read(reader, object, typeResolver);
      }
    } while (reader.consume(','));
    reader.expect('}');
    return object;
  }

  public static Object readArray(
      JsonReader reader, Class<?> componentType, JsonTypeResolver typeResolver) {
    List<Object> values = new ArrayList<>();
    reader.expect('[');
    if (!reader.consume(']')) {
      do {
        values.add(typeResolver.readValue(reader, componentType, componentType));
      } while (reader.consume(','));
      reader.expect(']');
    }
    Object array = Array.newInstance(componentType, values.size());
    for (int i = 0; i < values.size(); i++) {
      Array.set(array, i, values.get(i));
    }
    return array;
  }

  public static Collection<Object> readCollection(
      JsonReader reader, Class<?> rawType, Type elementType, JsonTypeResolver typeResolver) {
    Collection<Object> collection = newCollection(rawType);
    reader.expect('[');
    if (!reader.consume(']')) {
      Class<?> elementRawType = JsonSerializers.rawType(elementType, Object.class);
      do {
        collection.add(typeResolver.readValue(reader, elementType, elementRawType));
      } while (reader.consume(','));
      reader.expect(']');
    }
    return collection;
  }

  public static Map<String, Object> readMap(
      JsonReader reader, Class<?> rawType, Type valueType, JsonTypeResolver typeResolver) {
    Map<String, Object> map = newMap(rawType);
    reader.expect('{');
    if (!reader.consume('}')) {
      Class<?> valueRawType = JsonSerializers.rawType(valueType, Object.class);
      do {
        String name = reader.readString();
        reader.expect(':');
        map.put(name, typeResolver.readValue(reader, valueType, valueRawType));
      } while (reader.consume(','));
      reader.expect('}');
    }
    return map;
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
