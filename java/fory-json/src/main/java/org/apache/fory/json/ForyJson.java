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

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.fory.json.reader.JsonParsers;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

/** Thread-safe public facade for Fory JSON serialization and parsing. */
public final class ForyJson {
  private static final int PREFERRED_SLOT_RETRIES = 2;
  private static final int INITIAL_BUFFER_SIZE = 8192;
  private static final int MAX_CACHED_BUFFER_SIZE = 1024 * 1024;
  private static final int DEFAULT_POOL_SIZE =
      Math.max(1, Runtime.getRuntime().availableProcessors() * 4);

  private final JsonTypeResolver typeResolver;
  private final int poolSize;
  private final AtomicReferenceArray<PooledState> slots;
  private final Semaphore waiterSignal = new Semaphore(0);
  private final AtomicInteger waitingBorrowers = new AtomicInteger();

  ForyJson(boolean writeNullFields, boolean codegenEnabled) {
    typeResolver = new JsonTypeResolver(codegenEnabled, writeNullFields);
    poolSize = DEFAULT_POOL_SIZE;
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
    StringJsonWriter writer = state.borrowStringWriter();
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        state.rootTypeInfo(typeResolver, value.getClass()).write(writer, value, typeResolver);
      }
      return writer.toJson();
    } finally {
      state.releaseStringBuffer(writer.buffer());
      release(entry);
    }
  }

  public byte[] toJsonBytes(Object value) {
    PooledState entry = acquire();
    JsonState state = entry.state;
    Utf8JsonWriter writer = state.borrowUtf8Writer();
    try {
      if (value == null) {
        writer.writeNull();
      } else {
        state.rootTypeInfo(typeResolver, value.getClass()).writeUtf8(writer, value, typeResolver);
      }
      return writer.toJsonBytes();
    } finally {
      state.releaseUtf8Buffer(writer.buffer());
      release(entry);
    }
  }

  public <T> T fromJson(String json, Class<T> type) {
    return JsonParsers.fromString(json, type, typeResolver);
  }

  public <T> T fromJson(byte[] bytes, Class<T> type) {
    return JsonParsers.fromBytes(bytes, type, typeResolver);
  }

  boolean hasGeneratedWriter(Class<?> type) {
    return typeResolver.getClassInfo(type).hasObjectWriter();
  }

  private PooledState acquire() {
    int slotIndex = slotIndexForCurrentThread();
    PooledState entry = tryBorrowPreferredSlots(slotIndex);
    if (entry != null) {
      return entry;
    }
    waitingBorrowers.incrementAndGet();
    try {
      while (true) {
        entry = tryBorrowPreferredSlots(slotIndex);
        if (entry != null) {
          return entry;
        }
        try {
          waiterSignal.acquire();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new ForyJsonException("Interrupted while borrowing a pooled JSON state.", e);
        }
      }
    } finally {
      waitingBorrowers.decrementAndGet();
    }
  }

  private void release(PooledState entry) {
    slots.lazySet(entry.homeIndex, entry);
    if (waitingBorrowers.get() > 0) {
      waiterSignal.release();
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
    private byte[] utf8Buffer;
    private byte[] stringBuffer;
    private Class<?> lastRootType;
    private JsonTypeInfo lastRootInfo;

    private JsonState(boolean writeNullFields) {
      utf8Buffer = new byte[INITIAL_BUFFER_SIZE];
      stringBuffer = new byte[INITIAL_BUFFER_SIZE];
      utf8Writer = new Utf8JsonWriter(writeNullFields, utf8Buffer);
      stringWriter = new StringJsonWriter(writeNullFields, stringBuffer);
    }

    private StringJsonWriter borrowStringWriter() {
      byte[] buffer = stringBuffer;
      stringBuffer = null;
      stringWriter.reset(buffer == null ? new byte[INITIAL_BUFFER_SIZE] : buffer);
      return stringWriter;
    }

    private Utf8JsonWriter borrowUtf8Writer() {
      byte[] buffer = utf8Buffer;
      utf8Buffer = null;
      utf8Writer.reset(buffer == null ? new byte[INITIAL_BUFFER_SIZE] : buffer);
      return utf8Writer;
    }

    private void releaseStringBuffer(byte[] buffer) {
      if (buffer.length <= MAX_CACHED_BUFFER_SIZE) {
        stringBuffer = buffer;
      }
    }

    private void releaseUtf8Buffer(byte[] buffer) {
      if (buffer.length <= MAX_CACHED_BUFFER_SIZE) {
        utf8Buffer = buffer;
      }
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
