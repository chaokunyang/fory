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

package org.apache.fory.serializer;

import java.util.function.Supplier;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;

@SuppressWarnings({"rawtypes", "unchecked"})
public class LazySerializer extends Serializer {
  private final Supplier<Serializer> serializerSupplier;
  private Serializer serializer;

  public LazySerializer(TypeResolver typeResolver, Class type, Supplier<Serializer> serializerSupplier) {
    super(typeResolver, type);
    this.serializerSupplier = serializerSupplier;
  }

  @Override
  public void write(org.apache.fory.context.WriteContext writeContext, Object value) {
    MemoryBuffer buffer = writeContext.getBuffer();
    if (serializer == null) {
      serializer = serializerSupplier.get();
      fory.getTypeResolver().setSerializer(value.getClass(), serializer);
      if (!isJava) {
        fory.getTypeResolver().getTypeInfo(value.getClass()).setSerializer(serializer);
      }
    }
    serializer.write(org.apache.fory.context.WriteContext.current(), value);
  }

  @Override
  public Object read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
    boolean unInit = serializer == null;
    if (unInit) {
      serializer = serializerSupplier.get();
    }
    Object value = serializer.read(org.apache.fory.context.ReadContext.current());
    if (unInit) {
      fory.getTypeResolver().setSerializer(value.getClass(), serializer);
      if (!isJava) {
        fory.getTypeResolver().getTypeInfo(value.getClass()).setSerializer(serializer);
      }
    }
    return value;
  }

  @Override
  public Object copy(Object value) {
    if (serializer == null) {
      serializer = serializerSupplier.get();
      fory.getTypeResolver().setSerializer(value.getClass(), serializer);
    }
    return serializer.copy(value);
  }

  public static class LazyObjectSerializer extends LazySerializer {
    public LazyObjectSerializer(
        TypeResolver typeResolver, Class type, Supplier<Serializer> serializerSupplier) {
      super(typeResolver, type, serializerSupplier);
    }
  }
}
