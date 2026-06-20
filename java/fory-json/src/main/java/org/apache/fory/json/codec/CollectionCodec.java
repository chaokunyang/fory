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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class CollectionCodec extends AbstractJsonCodec {
  private static final Class<?> UNTYPED_COLLECTION = ArrayList.class;
  private final Class<?> rawType;
  private final JsonTypeInfo elementTypeInfo;
  private final JsonCodec elementCodec;

  public CollectionCodec(Class<?> rawType, Type elementType, JsonTypeResolver resolver) {
    this.rawType = rawType;
    Class<?> elementRawType = CodecUtils.rawType(elementType, Object.class);
    elementTypeInfo = resolver.getTypeInfo(elementType, elementRawType);
    elementCodec = elementTypeInfo.codec();
  }

  @Override
  void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : (Collection<?>) value) {
      writer.writeComma(index++);
      elementCodec.write(writer, element, resolver);
    }
    writer.writeArrayEnd();
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : (Collection<?>) value) {
      writer.writeComma(index++);
      elementCodec.writeString(writer, element, resolver);
    }
    writer.writeArrayEnd();
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeArrayStart();
    int index = 0;
    for (Object element : (Collection<?>) value) {
      writer.writeComma(index++);
      elementCodec.writeUtf8(writer, element, resolver);
    }
    writer.writeArrayEnd();
  }

  @Override
  Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
    Collection<Object> collection = newCollection(rawType);
    readInto(reader, collection, elementTypeInfo, elementCodec, resolver);
    return collection;
  }

  static Collection<Object> readUntyped(JsonReader reader, JsonTypeResolver resolver) {
    JsonTypeInfo elementInfo = resolver.getTypeInfo(Object.class, Object.class);
    Collection<Object> collection = new ArrayList<>();
    readInto(reader, collection, elementInfo, elementInfo.codec(), resolver);
    return collection;
  }

  private static void readInto(
      JsonReader reader,
      Collection<Object> collection,
      JsonTypeInfo elementInfo,
      JsonCodec elementCodec,
      JsonTypeResolver resolver) {
    reader.expect('[');
    if (!reader.consume(']')) {
      do {
        collection.add(elementCodec.read(reader, elementInfo, resolver));
      } while (reader.consume(','));
      reader.expect(']');
    }
  }

  @SuppressWarnings("unchecked")
  private static Collection<Object> newCollection(Class<?> rawType) {
    if (rawType == UNTYPED_COLLECTION || rawType.isInterface()) {
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
}
