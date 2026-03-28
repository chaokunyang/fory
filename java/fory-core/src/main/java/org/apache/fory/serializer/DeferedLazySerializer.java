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
import org.apache.fory.collection.Tuple2;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;

@SuppressWarnings({"rawtypes", "unchecked"})
public class DeferedLazySerializer extends Serializer {
  private final Supplier<Tuple2<Boolean, Serializer>> serializerSupplier;
  private Serializer serializer;

  public DeferedLazySerializer(
      TypeResolver typeResolver, Class type, Supplier<Tuple2<Boolean, Serializer>> serializerSupplier) {
    super(typeResolver, type);
    this.serializerSupplier = serializerSupplier;
  }

  @Override
  public void write(org.apache.fory.context.WriteContext writeContext, Object value) {
    MemoryBuffer buffer = writeContext.getBuffer();
    getSerializer().write(writeContext, value);
  }

  @Override
  public Object read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
    return getSerializer().read(readContext);
  }

  private Serializer getSerializer() {
    if (serializer == null) {
      Tuple2<Boolean, Serializer> tuple2 = serializerSupplier.get();
      if (tuple2.f0) {
        serializer = tuple2.f1;
        fory.getTypeResolver().setSerializer(type, serializer);
      } else {
        return tuple2.f1;
      }
    }
    return serializer;
  }

  /**
   * Force resolution of the deferred serializer without writing data. Used during GraalVM build
   * time to ensure the actual serializer is compiled.
   *
   * @return the resolved serializer
   */
  public Serializer resolveSerializer() {
    return getSerializer();
  }

  @Override
  public Object copy(Object value) {
    return getSerializer().copy(value);
  }

  public static class DeferredLazyObjectSerializer extends DeferedLazySerializer {
    public DeferredLazyObjectSerializer(
        TypeResolver typeResolver,
        Class type,
        Supplier<Tuple2<Boolean, Serializer>> serializerSupplier) {
      super(typeResolver, type, serializerSupplier);
    }
  }
}
