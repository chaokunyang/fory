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

package org.apache.fory.config;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.fory.AbstractThreadSafeFory;
import org.apache.fory.Fory;
import org.apache.fory.ThreadLocalFory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.io.ForyInputStream;
import org.apache.fory.io.ForyReadableChannel;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.pool.FastForyPool;
import org.apache.fory.serializer.BufferCallback;
import org.apache.fory.util.LoaderBinding;

@ThreadSafe
final class AdaptiveThreadSafeFory extends AbstractThreadSafeFory {
  private final ThreadLocalFory threadLocalFory;
  private final FastForyPool fastForyPool;

  AdaptiveThreadSafeFory(ThreadLocalFory threadLocalFory, FastForyPool fastForyPool) {
    this.threadLocalFory = Objects.requireNonNull(threadLocalFory);
    this.fastForyPool = Objects.requireNonNull(fastForyPool);
  }

  private ThreadSafeFory current() {
    return Platform.isCurrentThreadVirtual() ? fastForyPool : threadLocalFory;
  }

  @Override
  public <R> R execute(Function<Fory, R> action) {
    return current().execute(action);
  }

  @Override
  public byte[] serialize(Object obj) {
    return current().serialize(obj);
  }

  @Override
  public byte[] serialize(Object obj, BufferCallback callback) {
    return current().serialize(obj, callback);
  }

  @Override
  public MemoryBuffer serialize(Object obj, long address, int size) {
    return current().serialize(obj, address, size);
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj) {
    return current().serialize(buffer, obj);
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj, BufferCallback callback) {
    return current().serialize(buffer, obj, callback);
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj) {
    current().serialize(outputStream, obj);
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj, BufferCallback callback) {
    current().serialize(outputStream, obj, callback);
  }

  @Override
  public Object deserialize(ByteBuffer byteBuffer) {
    return current().deserialize(byteBuffer);
  }

  @Override
  public Object deserialize(byte[] bytes) {
    return current().deserialize(bytes);
  }

  @Override
  public <T> T deserialize(byte[] bytes, Class<T> type) {
    return current().deserialize(bytes, type);
  }

  @Override
  public <T> T deserialize(MemoryBuffer buffer, Class<T> type) {
    return current().deserialize(buffer, type);
  }

  @Override
  public <T> T deserialize(ForyInputStream inputStream, Class<T> type) {
    return current().deserialize(inputStream, type);
  }

  @Override
  public <T> T deserialize(ForyReadableChannel channel, Class<T> type) {
    return current().deserialize(channel, type);
  }

  @Override
  public Object deserialize(byte[] bytes, Iterable<MemoryBuffer> outOfBandBuffers) {
    return current().deserialize(bytes, outOfBandBuffers);
  }

  @Override
  public Object deserialize(long address, int size) {
    return current().deserialize(address, size);
  }

  @Override
  public Object deserialize(MemoryBuffer buffer) {
    return current().deserialize(buffer);
  }

  @Override
  public Object deserialize(MemoryBuffer buffer, Iterable<MemoryBuffer> outOfBandBuffers) {
    return current().deserialize(buffer, outOfBandBuffers);
  }

  @Override
  public Object deserialize(ForyInputStream inputStream) {
    return current().deserialize(inputStream);
  }

  @Override
  public Object deserialize(ForyInputStream inputStream, Iterable<MemoryBuffer> outOfBandBuffers) {
    return current().deserialize(inputStream, outOfBandBuffers);
  }

  @Override
  public Object deserialize(ForyReadableChannel channel) {
    return current().deserialize(channel);
  }

  @Override
  public Object deserialize(ForyReadableChannel channel, Iterable<MemoryBuffer> outOfBandBuffers) {
    return current().deserialize(channel, outOfBandBuffers);
  }

  @Override
  public <T> T copy(T obj) {
    return current().copy(obj);
  }

  @Override
  public void setClassLoader(ClassLoader classLoader) {
    current().setClassLoader(classLoader);
  }

  @Override
  public void setClassLoader(ClassLoader classLoader, LoaderBinding.StagingType stagingType) {
    current().setClassLoader(classLoader, stagingType);
  }

  @Override
  public ClassLoader getClassLoader() {
    return current().getClassLoader();
  }

  @Override
  public void clearClassLoader(ClassLoader loader) {
    current().clearClassLoader(loader);
  }

  @Override
  public void registerCallback(Consumer<Fory> callback) {
    threadLocalFory.registerCallback(callback);
    fastForyPool.registerCallback(callback);
  }
}
