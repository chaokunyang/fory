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

package org.apache.fury.serializer;

import java.util.function.Supplier;
import org.apache.fury.Fury;
import org.apache.fury.memory.MemoryBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class LazySerializer extends Serializer {
  private final Supplier<Serializer> serializerSupplier;
  private Serializer serializer;

  public LazySerializer(Fury fury, Class type, Supplier<Serializer> serializerSupplier) {
    super(fury, type);
    this.serializerSupplier = serializerSupplier;
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    if (serializer == null) {
      serializer = serializerSupplier.get();
      fury.getClassResolver().setSerializer(value.getClass(), serializer);
    }
    serializer.write(buffer, value);
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    boolean unInit = serializer == null;
    if (unInit) {
      serializer = serializerSupplier.get();
    }
    Object value = serializer.read(buffer);
    if (unInit) {
      fury.getClassResolver().setSerializer(value.getClass(), serializer);
    }
    return value;
  }

  @Override
  public void xwrite(MemoryBuffer buffer, Object value) {
    if (serializer == null) {
      serializer = serializerSupplier.get();
      fury.getClassResolver().setSerializer(value.getClass(), serializer);
      fury.getXtypeResolver().getClassInfo(value.getClass()).setSerializer(serializer);
    }
    serializer.xwrite(buffer, value);
  }

  @Override
  public Object xread(MemoryBuffer buffer) {
    boolean unInit = serializer == null;
    if (unInit) {
      serializer = serializerSupplier.get();
    }
    Object value = serializer.xread(buffer);
    if (unInit) {
      fury.getClassResolver().setSerializer(value.getClass(), serializer);
      fury.getXtypeResolver().getClassInfo(value.getClass()).setSerializer(serializer);
    }
    return value;
  }

  @Override
  public Object copy(Object value) {
    if (serializer == null) {
      serializer = serializerSupplier.get();
      fury.getClassResolver().setSerializer(value.getClass(), serializer);
    }
    return serializer.copy(value);
  }

  public static class LazyObjectSerializer extends LazySerializer {
    public LazyObjectSerializer(Fury fury, Class type, Supplier<Serializer> serializerSupplier) {
      super(fury, type, serializerSupplier);
    }
  }
}
