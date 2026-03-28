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

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.apache.fory.config.Config;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;

/**
 * Serializers for {@link Optional}, {@link OptionalInt}, {@link OptionalLong} and {@link
 * OptionalDouble}.
 */
public final class OptionalSerializers {
  public static final class OptionalSerializer extends Serializer<Optional> {

    public OptionalSerializer(Config config) {
      super(config, Optional.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, Optional value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      Object nullable = value.isPresent() ? value.get() : null;
      fory.writeRef(buffer, nullable);
    }

    @Override
    public Optional copy(Optional originOptional) {
      if (originOptional.isPresent()) {
        return Optional.ofNullable(fory.copyObject(originOptional.get()));
      }
      return originOptional;
    }

    @Override
    public Optional read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      return Optional.ofNullable(fory.readRef(buffer));
    }
  }

  public static final class OptionalIntSerializer extends ImmutableSerializer<OptionalInt> {
    public OptionalIntSerializer(Config config) {
      super(config, OptionalInt.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, OptionalInt value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      boolean present = value.isPresent();
      buffer.writeBoolean(present);
      if (present) {
        buffer.writeInt32(value.getAsInt());
      }
    }

    @Override
    public OptionalInt read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      if (buffer.readBoolean()) {
        return OptionalInt.of(buffer.readInt32());
      } else {
        return OptionalInt.empty();
      }
    }
  }

  public static final class OptionalLongSerializer extends ImmutableSerializer<OptionalLong> {
    public OptionalLongSerializer(Config config) {
      super(config, OptionalLong.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, OptionalLong value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      boolean present = value.isPresent();
      buffer.writeBoolean(present);
      if (present) {
        buffer.writeInt64(value.getAsLong());
      }
    }

    @Override
    public OptionalLong read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      if (buffer.readBoolean()) {
        return OptionalLong.of(buffer.readInt64());
      } else {
        return OptionalLong.empty();
      }
    }
  }

  public static final class OptionalDoubleSerializer extends ImmutableSerializer<OptionalDouble> {
    public OptionalDoubleSerializer(Config config) {
      super(config, OptionalDouble.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, OptionalDouble value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      boolean present = value.isPresent();
      buffer.writeBoolean(present);
      if (present) {
        buffer.writeFloat64(value.getAsDouble());
      }
    }

    @Override
    public OptionalDouble read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      if (buffer.readBoolean()) {
        return OptionalDouble.of(buffer.readFloat64());
      } else {
        return OptionalDouble.empty();
      }
    }
  }

  public static void registerDefaultSerializers(TypeResolver resolver) {
    Config config = resolver.getConfig();
    resolver.registerInternalSerializer(Optional.class, new OptionalSerializer(config));
    resolver.registerInternalSerializer(OptionalInt.class, new OptionalIntSerializer(config));
    resolver.registerInternalSerializer(OptionalLong.class, new OptionalLongSerializer(config));
    resolver.registerInternalSerializer(OptionalDouble.class, new OptionalDoubleSerializer(config));
  }
}
