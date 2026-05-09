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

import java.nio.ByteBuffer;
import java.util.function.Function;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.resolver.TypeChecker;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.SerializerFactory;

public abstract class AbstractThreadSafeFory implements ThreadSafeFory {
  @Override
  public void register(Class<?> clz) {
    registerCallback(fory -> fory.register(clz));
  }

  @Override
  public void register(Class<?> cls, int id) {
    registerCallback(fory -> fory.register(cls, id));
  }

  @Override
  public void register(Class<?> cls, String typeName) {
    registerCallback(fory -> fory.register(cls, typeName));
  }

  @Override
  public void register(Class<?> cls, String namespace, String typeName) {
    registerCallback(fory -> fory.register(cls, namespace, typeName));
  }

  @Override
  public void register(String className) {
    registerCallback(fory -> fory.register(className));
  }

  @Override
  public void register(String className, int id) {
    registerCallback(fory -> fory.register(className, id));
  }

  @Override
  public void register(String className, String namespace, String typeName) {
    registerCallback(fory -> fory.register(className, namespace, typeName));
  }

  public void registerUnion(
      Class<?> cls, int id, org.apache.fory.serializer.Serializer<?> serializer) {
    registerCallback(fory -> fory.registerUnion(cls, id, serializer));
  }

  public void registerUnion(
      Class<?> cls,
      String namespace,
      String typeName,
      org.apache.fory.serializer.Serializer<?> serializer) {
    registerCallback(fory -> fory.registerUnion(cls, namespace, typeName, serializer));
  }

  @Override
  public <T> void registerSerializer(Class<T> type, Class<? extends Serializer> serializerClass) {
    registerCallback(fory -> fory.registerSerializer(type, serializerClass));
  }

  @Override
  public void registerSerializer(Class<?> type, Serializer<?> serializer) {
    registerCallback(fory -> fory.registerSerializer(type, serializer));
  }

  @Override
  public void registerSerializer(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    registerCallback(fory -> fory.registerSerializer(type, serializerCreator));
  }

  @Override
  public <T> void registerSerializerAndType(
      Class<T> type, Class<? extends Serializer> serializerClass) {
    registerCallback(fory -> fory.registerSerializerAndType(type, serializerClass));
  }

  @Override
  public void registerSerializerAndType(Class<?> type, Serializer<?> serializer) {
    registerCallback(fory -> fory.registerSerializerAndType(type, serializer));
  }

  @Override
  public void registerSerializerAndType(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    registerCallback(fory -> fory.registerSerializerAndType(type, serializerCreator));
  }

  @Override
  public void setSerializerFactory(SerializerFactory serializerFactory) {
    registerCallback(fory -> fory.setSerializerFactory(serializerFactory));
  }

  @Override
  public TypeResolver getTypeResolver() {
    return execute(Fory::getTypeResolver);
  }

  @Override
  public void setTypeChecker(TypeChecker typeChecker) {
    registerCallback(fory -> fory.getTypeResolver().setTypeChecker(typeChecker));
  }

  @Override
  public void ensureSerializersCompiled() {
    execute(
        fory -> {
          fory.ensureSerializersCompiled();
          return null;
        });
  }

  protected static Object deserializeByteBuffer(Fory fory, ByteBuffer byteBuffer) {
    if (!AndroidSupport.IS_ANDROID && !byteBuffer.isReadOnly()) {
      return fory.deserialize(MemoryUtils.wrap(byteBuffer));
    }
    int size = byteBuffer.remaining();
    MemoryBuffer buffer = fory.getBuffer();
    byte[] heapMemory = buffer.getHeapMemory();
    if (heapMemory == null || heapMemory.length < size) {
      heapMemory = new byte[size];
    }
    int restoreSize = heapMemory.length;
    ByteBuffer source = byteBuffer.duplicate();
    if (source.hasArray()) {
      System.arraycopy(
          source.array(), source.arrayOffset() + source.position(), heapMemory, 0, size);
    } else {
      source.get(heapMemory, 0, size);
    }
    buffer.initHeapBuffer(heapMemory, 0, size);
    buffer.readerIndex(0);
    buffer.writerIndex(size);
    try {
      return fory.deserialize(buffer);
    } finally {
      buffer.initHeapBuffer(heapMemory, 0, restoreSize);
      buffer.readerIndex(0);
      buffer.writerIndex(0);
    }
  }
}
