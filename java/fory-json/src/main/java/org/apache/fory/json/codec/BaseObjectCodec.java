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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.apache.fory.annotation.Expose;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.annotation.JsonIgnore;
import org.apache.fory.json.meta.JsonFieldAccessor;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldTable;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.ObjectInstantiator;
import org.apache.fory.reflect.ObjectInstantiators;

public abstract class BaseObjectCodec extends AbstractJsonCodec {
  protected final Class<?> type;
  protected final JsonFieldInfo[] writeFields;
  protected final JsonFieldInfo[] readFields;
  protected final JsonFieldTable readTable;
  protected final ObjectInstantiator<?> instantiator;

  protected BaseObjectCodec(
      Class<?> type,
      JsonFieldInfo[] writeFields,
      JsonFieldInfo[] readFields,
      ObjectInstantiator<?> instantiator) {
    this.type = type;
    this.writeFields = writeFields;
    this.readFields = readFields;
    readTable = new JsonFieldTable(readFields);
    this.instantiator = instantiator;
  }

  public static ObjectCodec build(Class<?> type) {
    if (type.isInterface()
        || Modifier.isAbstract(type.getModifiers())
        || type.isPrimitive()
        || type.isArray()
        || type.isEnum()) {
      throw new ForyJsonException("Unsupported JSON object type " + type);
    }
    boolean writeExpose = hasWriteExpose(type);
    boolean readExpose = hasReadExpose(type);
    TreeMap<String, FieldBuilder> builders = new TreeMap<>();
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (!isEligibleField(field)) {
          continue;
        }
        boolean write = includeWrite(field, writeExpose);
        boolean read = !Modifier.isFinal(modifiers) && includeRead(field, readExpose);
        if (!write && !read) {
          continue;
        }
        FieldBuilder builder = new FieldBuilder(field.getName());
        if (write) {
          builder.setWriteField(field);
        }
        if (read) {
          builder.setReadField(field);
        }
        if (builders.put(field.getName(), builder) != null) {
          throw new ForyJsonException("Duplicate JSON field " + field.getName());
        }
      }
    }
    List<JsonFieldInfo> writes = new ArrayList<>();
    List<JsonFieldInfo> reads = new ArrayList<>();
    for (FieldBuilder builder : builders.values()) {
      JsonFieldInfo field = builder.build();
      if (builder.writeAccessor != null) {
        writes.add(field);
      }
      if (builder.readAccessor != null) {
        reads.add(field);
      }
    }
    JsonFieldInfo[] writeArray = writes.toArray(new JsonFieldInfo[0]);
    JsonFieldInfo[] readArray = reads.toArray(new JsonFieldInfo[0]);
    return new ObjectCodec(
        type, writeArray, readArray, ObjectInstantiators.createObjectInstantiator(type));
  }

  public final Class<?> type() {
    return type;
  }

  public final JsonFieldInfo[] writeFields() {
    return writeFields;
  }

  public final JsonFieldInfo[] readFields() {
    return readFields;
  }

  public final JsonFieldTable readTable() {
    return readTable;
  }

  public final void resolveTypes(JsonTypeResolver typeResolver) {
    for (JsonFieldInfo field : writeFields) {
      field.resolveTypes(typeResolver);
    }
    for (JsonFieldInfo field : readFields) {
      field.resolveTypes(typeResolver);
    }
  }

  public final Object newInstance() {
    return instantiator.newInstance();
  }

  @Override
  final void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      writeObject(writer, value, resolver);
    } else {
      JsonTypeInfo typeInfo = resolver.getTypeInfo(valueClass, valueClass);
      typeInfo.codec().write(writer, value, resolver);
    }
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      writeStringObject(writer, value, resolver);
    } else {
      JsonTypeInfo typeInfo = resolver.getTypeInfo(valueClass, valueClass);
      typeInfo.codec().writeString(writer, value, resolver);
    }
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      writeUtf8Object(writer, value, resolver);
    } else {
      JsonTypeInfo typeInfo = resolver.getTypeInfo(valueClass, valueClass);
      typeInfo.codec().writeUtf8(writer, value, resolver);
    }
  }

  @Override
  Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
    Object object = newInstance();
    reader.expect('{');
    if (reader.consume('}')) {
      return object;
    }
    do {
      JsonFieldInfo field = reader.readField(readTable);
      reader.expect(':');
      if (field == null) {
        reader.skipValue();
      } else {
        field.read(reader, object, resolver);
      }
    } while (reader.consume(','));
    reader.expect('}');
    return object;
  }

  protected final void writeObject(JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int written = 0;
    JsonFieldInfo[] fields = writeFields;
    int length = fields.length;
    int i = 0;
    while (i + 4 <= length) {
      if (fields[i++].write(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].write(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].write(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].write(writer, value, resolver, written)) {
        written++;
      }
    }
    while (i < length) {
      if (fields[i++].write(writer, value, resolver, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  protected final void writeStringObject(
      StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int written = 0;
    JsonFieldInfo[] fields = writeFields;
    int length = fields.length;
    int i = 0;
    while (i + 4 <= length) {
      if (fields[i++].writeString(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeString(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeString(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeString(writer, value, resolver, written)) {
        written++;
      }
    }
    while (i < length) {
      if (fields[i++].writeString(writer, value, resolver, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  protected final void writeUtf8Object(
      Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    writer.writeObjectStart();
    int written = 0;
    JsonFieldInfo[] fields = writeFields;
    int length = fields.length;
    int i = 0;
    while (i + 4 <= length) {
      if (fields[i++].writeUtf8(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeUtf8(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeUtf8(writer, value, resolver, written)) {
        written++;
      }
      if (fields[i++].writeUtf8(writer, value, resolver, written)) {
        written++;
      }
    }
    while (i < length) {
      if (fields[i++].writeUtf8(writer, value, resolver, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  private static boolean hasWriteExpose(Class<?> type) {
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (isEligibleField(field) && field.isAnnotationPresent(Expose.class)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasReadExpose(Class<?> type) {
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (isEligibleField(field)
            && !Modifier.isFinal(field.getModifiers())
            && field.isAnnotationPresent(Expose.class)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isEligibleField(Field field) {
    int modifiers = field.getModifiers();
    return !Modifier.isStatic(modifiers)
        && !Modifier.isTransient(modifiers)
        && !field.isSynthetic();
  }

  private static boolean includeWrite(Field field, boolean exposeMode) {
    return include(field, exposeMode, true);
  }

  private static boolean includeRead(Field field, boolean exposeMode) {
    return include(field, exposeMode, false);
  }

  private static boolean include(Field field, boolean exposeMode, boolean write) {
    JsonIgnore ignore = field.getAnnotation(JsonIgnore.class);
    boolean ignored = ignore != null && (write ? ignore.ignoreWrite() : ignore.ignoreRead());
    boolean exposed = field.isAnnotationPresent(Expose.class);
    if (ignored && exposed) {
      throw new ForyJsonException("JSON field cannot be both exposed and ignored: " + field);
    }
    if (ignored) {
      return false;
    }
    return !exposeMode || exposed;
  }

  private static final class FieldBuilder {
    private final String name;
    private Field writeField;
    private Field readField;
    private JsonFieldAccessor writeAccessor;
    private JsonFieldAccessor readAccessor;

    private FieldBuilder(String name) {
      this.name = name;
    }

    private void setWriteField(Field field) {
      if (writeField != null) {
        throw new ForyJsonException("Duplicate public JSON field " + name);
      }
      writeField = field;
    }

    private void setReadField(Field field) {
      if (readField != null) {
        throw new ForyJsonException("Duplicate public JSON field " + name);
      }
      readField = field;
    }

    private JsonFieldInfo build() {
      writeAccessor = writeField == null ? null : JsonFieldAccessor.forField(writeField);
      readAccessor = readField == null ? null : JsonFieldAccessor.forField(readField);
      return new JsonFieldInfo(name, writeField, readField, writeAccessor, readAccessor);
    }
  }
}
