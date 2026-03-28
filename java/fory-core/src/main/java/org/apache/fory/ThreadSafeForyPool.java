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

package org.apache.fory;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.io.ForyInputStream;
import org.apache.fory.io.ForyReadableChannel;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.resolver.SharedRegistry;
import org.apache.fory.serializer.BufferCallback;

/**
 * A lightweight thread-safe {@link Fory} pool that reuses equivalent {@link Fory} instances built
 * from a single configuration and classloader.
 */
public class ThreadSafeForyPool extends AbstractThreadSafeFory {
  private static final int DEFAULT_POOL_SIZE = 1024;

  private final Function<ForyBuilder, Fory> foryFactory;
  private final SharedRegistry sharedRegistry;
  private final ConcurrentLinkedQueue<Fory> pool = new ConcurrentLinkedQueue<>();
  private final Object callbackLock = new Object();
  private final int maxPoolSize;
  private final Semaphore idleCapacity;
  private Consumer<Fory> factoryCallback = f -> {};
  private volatile boolean used;

  public ThreadSafeForyPool(Function<ForyBuilder, Fory> foryFactory) {
    this(foryFactory, new SharedRegistry(), DEFAULT_POOL_SIZE);
  }

  public ThreadSafeForyPool(Function<ForyBuilder, Fory> foryFactory, SharedRegistry sharedRegistry) {
    this(foryFactory, sharedRegistry, DEFAULT_POOL_SIZE);
  }

  public ThreadSafeForyPool(
      Function<ForyBuilder, Fory> foryFactory, SharedRegistry sharedRegistry, int maxPoolSize) {
    this.foryFactory = Objects.requireNonNull(foryFactory);
    this.sharedRegistry = Objects.requireNonNull(sharedRegistry);
    if (maxPoolSize < 0) {
      throw new IllegalArgumentException(
          String.format("ThreadSafeForyPool maxPoolSize must be >= 0, got %s", maxPoolSize));
    }
    this.maxPoolSize = maxPoolSize;
    idleCapacity = new Semaphore(maxPoolSize);
  }

  @Override
  public <R> R execute(Function<Fory, R> action) {
    used = true;
    Fory fory = acquire();
    try {
      return action.apply(fory);
    } finally {
      release(fory);
    }
  }

  @Override
  public void registerCallback(Consumer<Fory> callback) {
    synchronized (callbackLock) {
      if (!used) {
        factoryCallback = factoryCallback.andThen(callback);
        return;
      }
    }
    execute(
        fory -> {
          callback.accept(fory);
          return null;
        });
    synchronized (callbackLock) {
      factoryCallback = factoryCallback.andThen(callback);
    }
  }

  @Override
  public byte[] serialize(Object obj) {
    return execute(fory -> fory.serialize(obj));
  }

  @Override
  public byte[] serialize(Object obj, BufferCallback callback) {
    return execute(fory -> fory.serialize(obj, callback));
  }

  @Override
  public MemoryBuffer serialize(Object obj, long address, int size) {
    return execute(fory -> fory.serialize(obj, address, size));
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj) {
    return execute(fory -> fory.serialize(buffer, obj));
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj, BufferCallback callback) {
    return execute(fory -> fory.serialize(buffer, obj, callback));
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj) {
    execute(
        fory -> {
          fory.serialize(outputStream, obj);
          return null;
        });
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj, BufferCallback callback) {
    execute(
        fory -> {
          fory.serialize(outputStream, obj, callback);
          return null;
        });
  }

  @Override
  public Object deserialize(ByteBuffer byteBuffer) {
    return execute(fory -> fory.deserialize(MemoryUtils.wrap(byteBuffer)));
  }

  @Override
  public Object deserialize(byte[] bytes) {
    return execute(fory -> fory.deserialize(bytes));
  }

  @Override
  public <T> T deserialize(byte[] bytes, Class<T> type) {
    return execute(fory -> fory.deserialize(bytes, type));
  }

  @Override
  public <T> T deserialize(MemoryBuffer buffer, Class<T> type) {
    return execute(fory -> fory.deserialize(buffer, type));
  }

  @Override
  public <T> T deserialize(ForyInputStream inputStream, Class<T> type) {
    return execute(fory -> fory.deserialize(inputStream, type));
  }

  @Override
  public <T> T deserialize(ForyReadableChannel channel, Class<T> type) {
    return execute(fory -> fory.deserialize(channel, type));
  }

  @Override
  public Object deserialize(byte[] bytes, Iterable<MemoryBuffer> outOfBandBuffers) {
    return execute(fory -> fory.deserialize(bytes, outOfBandBuffers));
  }

  @Override
  public Object deserialize(long address, int size) {
    return execute(fory -> fory.deserialize(address, size));
  }

  @Override
  public Object deserialize(MemoryBuffer buffer) {
    return execute(fory -> fory.deserialize(buffer));
  }

  @Override
  public Object deserialize(MemoryBuffer buffer, Iterable<MemoryBuffer> outOfBandBuffers) {
    return execute(fory -> fory.deserialize(buffer, outOfBandBuffers));
  }

  @Override
  public Object deserialize(ForyInputStream inputStream) {
    return execute(fory -> fory.deserialize(inputStream));
  }

  @Override
  public Object deserialize(ForyInputStream inputStream, Iterable<MemoryBuffer> outOfBandBuffers) {
    return execute(fory -> fory.deserialize(inputStream, outOfBandBuffers));
  }

  @Override
  public Object deserialize(ForyReadableChannel channel) {
    return execute(fory -> fory.deserialize(channel));
  }

  @Override
  public Object deserialize(ForyReadableChannel channel, Iterable<MemoryBuffer> outOfBandBuffers) {
    return execute(fory -> fory.deserialize(channel, outOfBandBuffers));
  }

  @Override
  public <T> T copy(T obj) {
    return execute(fory -> fory.copy(obj));
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  private Fory acquire() {
    Fory fory = pool.poll();
    if (fory != null) {
      if (maxPoolSize > 0) {
        idleCapacity.release();
      }
      return fory;
    }
    ForyBuilder builder = new ForyBuilder().withSharedRegistry(sharedRegistry);
    fory = foryFactory.apply(builder);
    Consumer<Fory> callback = factoryCallback;
    if (callback != null) {
      callback.accept(fory);
    }
    return fory;
  }

  private void release(Fory fory) {
    if (fory == null || maxPoolSize == 0) {
      return;
    }
    if (idleCapacity.tryAcquire()) {
      pool.offer(fory);
    }
  }
}
