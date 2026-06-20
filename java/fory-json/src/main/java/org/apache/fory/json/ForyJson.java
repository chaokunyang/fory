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

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.fory.json.codec.CodecRegistry;
import org.apache.fory.json.codec.GeneratedObjectCodec;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.StringJsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.TypeRef;

/** Thread-safe public facade for Fory JSON serialization and parsing. */
public final class ForyJson {
  private static final int PREFERRED_SLOT_RETRIES = 2;
  private static final int INITIAL_BUFFER_SIZE = 8192;
  private static final int MAX_CACHED_BUFFER_SIZE = 1024 * 1024;
  private static final int PRIMARY_SLOT = -1;
  private static final int TEMPORARY_SLOT = -2;
  private static final int DEFAULT_POOL_SIZE =
      Math.max(1, Runtime.getRuntime().availableProcessors() * 4);

  private final JsonTypeResolver typeResolver;
  private final boolean writeNullFields;
  private final int poolSize;
  private final AtomicReference<PooledState> primarySlot;
  private final AtomicReferenceArray<PooledState> slots;

  ForyJson(boolean writeNullFields, boolean codegenEnabled, CodecRegistry codecRegistry) {
    this.writeNullFields = writeNullFields;
    typeResolver = new JsonTypeResolver(codegenEnabled, writeNullFields, codecRegistry);
    poolSize = DEFAULT_POOL_SIZE;
    primarySlot =
        new AtomicReference<>(new PooledState(new JsonState(writeNullFields), PRIMARY_SLOT));
    slots = new AtomicReferenceArray<>(poolSize);
    for (int i = 0; i < poolSize; i++) {
      slots.set(i, new PooledState(new JsonState(writeNullFields), i));
    }
  }

  public static ForyJsonBuilder builder() {
    return new ForyJsonBuilder();
  }

  public String toJson(Object value) {
    PooledState entry = acquire();
    JsonState state = entry.state;
    StringJsonWriter writer = state.stringWriter();
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        JsonTypeInfo typeInfo = state.rootTypeInfo(typeResolver, value.getClass());
        typeInfo.codec().writeString(writer, value, typeResolver);
      }
      return writer.toJson();
    } finally {
      release(entry);
    }
  }

  public byte[] toJsonBytes(Object value) {
    PooledState entry = acquire();
    JsonState state = entry.state;
    Utf8JsonWriter writer = state.utf8Writer();
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        JsonTypeInfo typeInfo = state.rootTypeInfo(typeResolver, value.getClass());
        typeInfo.codec().writeUtf8(writer, value, typeResolver);
      }
      return writer.toJsonBytes();
    } finally {
      release(entry);
    }
  }

  public <T> T fromJson(String json, Class<T> type) {
    return castValue(readValue(new StringJsonReader(json), type, type), type);
  }

  /** Parses JSON using a generic type captured by {@link TypeRef}. */
  public <T> T fromJson(String json, TypeRef<T> typeRef) {
    Object value = readValue(new StringJsonReader(json), typeRef.getType(), typeRef.getRawType());
    return castValue(value, typeRef);
  }

  public <T> T fromJson(byte[] bytes, Class<T> type) {
    return castValue(readValue(new Utf8JsonReader(bytes), type, type), type);
  }

  /** Parses UTF-8 JSON bytes using a generic type captured by {@link TypeRef}. */
  public <T> T fromJson(byte[] bytes, TypeRef<T> typeRef) {
    Object value = readValue(new Utf8JsonReader(bytes), typeRef.getType(), typeRef.getRawType());
    return castValue(value, typeRef);
  }

  boolean hasGeneratedWriter(Class<?> type) {
    return typeResolver.getObjectCodec(type) instanceof GeneratedObjectCodec;
  }

  private PooledState acquire() {
    PooledState entry = primarySlot.get();
    if (entry != null && primarySlot.compareAndSet(entry, null)) {
      return entry;
    }
    int slotIndex = slotIndexForCurrentThread();
    entry = tryBorrowPreferredSlots(slotIndex);
    if (entry != null) {
      return entry;
    }
    return new PooledState(new JsonState(writeNullFields), TEMPORARY_SLOT);
  }

  private void release(PooledState entry) {
    if (entry.homeIndex == PRIMARY_SLOT) {
      primarySlot.lazySet(entry);
    } else if (entry.homeIndex >= 0) {
      slots.lazySet(entry.homeIndex, entry);
    }
  }

  private PooledState tryBorrowPreferredSlots(int slotIndex) {
    PooledState entry = tryBorrowSlot(slotIndex);
    if (entry != null) {
      return entry;
    }
    for (int i = 1; i < PREFERRED_SLOT_RETRIES; i++) {
      entry = tryBorrowSlot(slotIndex);
      if (entry != null) {
        return entry;
      }
    }
    int index = slotIndex + 1;
    if (index == poolSize) {
      index = 0;
    }
    for (int i = 1; i < poolSize; i++) {
      entry = tryBorrowSlot(index);
      if (entry != null) {
        return entry;
      }
      index++;
      if (index == poolSize) {
        index = 0;
      }
    }
    return null;
  }

  private PooledState tryBorrowSlot(int index) {
    return slots.getAndSet(index, null);
  }

  private int slotIndexForCurrentThread() {
    return Math.floorMod(spread(System.identityHashCode(Thread.currentThread())), poolSize);
  }

  private static int spread(int hash) {
    return hash ^ (hash >>> 16);
  }

  private Object readValue(JsonReader reader, Type type, Class<?> fallback) {
    JsonTypeInfo typeInfo = typeResolver.getTypeInfo(type, fallback);
    Object value = typeInfo.codec().read(reader, typeInfo, typeResolver);
    reader.finish();
    return value;
  }

  @SuppressWarnings("unchecked")
  private static <T> T castValue(Object value, Class<T> type) {
    return type.isPrimitive() ? (T) value : type.cast(value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T castValue(Object value, TypeRef<T> typeRef) {
    Class<?> rawType = typeRef.getRawType();
    return rawType.isPrimitive() ? (T) value : (T) rawType.cast(value);
  }

  private static final class PooledState {
    private final JsonState state;
    private final int homeIndex;

    private PooledState(JsonState state, int homeIndex) {
      this.state = state;
      this.homeIndex = homeIndex;
    }
  }

  private static final class JsonState {
    private final Utf8JsonWriter utf8Writer;
    private final StringJsonWriter stringWriter;
    private Class<?> lastRootType;
    private JsonTypeInfo lastRootInfo;

    private JsonState(boolean writeNullFields) {
      utf8Writer = new Utf8JsonWriter(writeNullFields, new byte[INITIAL_BUFFER_SIZE]);
      stringWriter = new StringJsonWriter(writeNullFields, new byte[INITIAL_BUFFER_SIZE]);
    }

    private StringJsonWriter stringWriter() {
      stringWriter.reset(resetBuffer(stringWriter.buffer()));
      return stringWriter;
    }

    private Utf8JsonWriter utf8Writer() {
      utf8Writer.reset(resetBuffer(utf8Writer.buffer()));
      return utf8Writer;
    }

    private byte[] resetBuffer(byte[] buffer) {
      return buffer.length <= MAX_CACHED_BUFFER_SIZE ? buffer : new byte[INITIAL_BUFFER_SIZE];
    }

    private JsonTypeInfo rootTypeInfo(JsonTypeResolver resolver, Class<?> type) {
      JsonTypeInfo typeInfo = lastRootInfo;
      if (lastRootType == type && typeInfo != null) {
        return typeInfo;
      }
      typeInfo = resolver.getTypeInfo(type, type);
      lastRootType = type;
      lastRootInfo = typeInfo;
      return typeInfo;
    }
  }
}
