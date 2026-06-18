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

/** Public facade for Fory JSON serialization and parsing. Instances are not thread-safe. */
public final class ForyJson {
  private static final int INITIAL_BUFFER_SIZE = 8192;
  private static final int MAX_CACHED_BUFFER_SIZE = 1024 * 1024;

  private final JsonClassCache classCache;
  private final Utf8JsonWriter utf8Writer;
  private final StringJsonWriter stringWriter;
  private byte[] utf8Buffer;
  private char[] stringBuffer;
  private final boolean writeNullFields;
  private Class<?> lastWriteClass;
  private JsonClassInfo lastWriteClassInfo;

  ForyJson(boolean writeNullFields, boolean codegenEnabled) {
    this.writeNullFields = writeNullFields;
    classCache = new JsonClassCache(codegenEnabled, writeNullFields);
    utf8Buffer = new byte[INITIAL_BUFFER_SIZE];
    stringBuffer = new char[INITIAL_BUFFER_SIZE];
    utf8Writer = new Utf8JsonWriter(writeNullFields, utf8Buffer);
    stringWriter = new StringJsonWriter(writeNullFields, stringBuffer);
  }

  public static ForyJsonBuilder builder() {
    return new ForyJsonBuilder();
  }

  public String toJson(Object value) {
    char[] buffer = borrowStringBuffer();
    StringJsonWriter writer = stringWriter;
    writer.reset(buffer);
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        Class<?> type = value.getClass();
        if (type.isArray()
            || type.isPrimitive()
            || type.isEnum()
            || value instanceof String
            || value instanceof java.util.Collection
            || value instanceof java.util.Map) {
          JsonSerializers.writeValue(writer, value, type, classCache);
        } else {
          JsonClassInfo classInfo = getWriteClassInfo(type);
          classInfo.write(writer, value, classCache);
        }
      }
      return writer.toJson();
    } finally {
      releaseStringBuffer(writer.buffer());
    }
  }

  public byte[] toJsonBytes(Object value) {
    byte[] buffer = borrowUtf8Buffer();
    Utf8JsonWriter writer = utf8Writer;
    writer.reset(buffer);
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        Class<?> type = value.getClass();
        if (type.isArray()
            || type.isPrimitive()
            || type.isEnum()
            || value instanceof String
            || value instanceof java.util.Collection
            || value instanceof java.util.Map) {
          JsonSerializers.writeUtf8Value(writer, value, type, classCache);
        } else {
          JsonClassInfo classInfo = getWriteClassInfo(type);
          classInfo.writeUtf8(writer, value, classCache);
        }
      }
      return writer.toJsonBytes();
    } finally {
      releaseUtf8Buffer(writer.buffer());
    }
  }

  public <T> T fromJson(String json, Class<T> type) {
    return JsonParsers.fromString(json, type, classCache);
  }

  public <T> T fromJson(byte[] bytes, Class<T> type) {
    return JsonParsers.fromBytes(bytes, type, classCache);
  }

  boolean hasGeneratedWriter(Class<?> type) {
    return classCache.get(type).hasObjectWriter();
  }

  private byte[] borrowUtf8Buffer() {
    byte[] buffer = utf8Buffer;
    utf8Buffer = null;
    return buffer == null ? new byte[INITIAL_BUFFER_SIZE] : buffer;
  }

  private void releaseUtf8Buffer(byte[] buffer) {
    if (buffer.length <= MAX_CACHED_BUFFER_SIZE) {
      utf8Buffer = buffer;
    }
  }

  private char[] borrowStringBuffer() {
    char[] buffer = stringBuffer;
    stringBuffer = null;
    return buffer == null ? new char[INITIAL_BUFFER_SIZE] : buffer;
  }

  private void releaseStringBuffer(char[] buffer) {
    if (buffer.length <= MAX_CACHED_BUFFER_SIZE) {
      stringBuffer = buffer;
    }
  }

  private JsonClassInfo getWriteClassInfo(Class<?> type) {
    JsonClassInfo classInfo = lastWriteClassInfo;
    if (lastWriteClass == type && classInfo != null) {
      return classInfo;
    }
    classInfo = classCache.get(type);
    lastWriteClass = type;
    lastWriteClassInfo = classInfo;
    return classInfo;
  }
}
