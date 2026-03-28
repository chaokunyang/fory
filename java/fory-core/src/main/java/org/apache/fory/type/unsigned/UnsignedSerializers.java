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

package org.apache.fory.type.unsigned;

import org.apache.fory.config.Config;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializers;

/** Utility class for registering Uint wrapper type serializers. */
public class UnsignedSerializers {

  /** Register all Uint wrapper type serializers. */
  public static void registerSerializers(TypeResolver resolver) {
    Config config = resolver.getConfig();
    resolver.registerSerializer(Uint8.class, new Uint8Serializer(config));
    resolver.registerSerializer(Uint16.class, new Uint16Serializer(config));
    resolver.registerSerializer(Uint32.class, new Uint32Serializer(config));
    resolver.registerSerializer(Uint64.class, new Uint64Serializer(config));
  }

  /** Serializer for Uint8 wrapper type. */
  public static class Uint8Serializer extends Serializers.CrossLanguageCompatibleSerializer<Uint8> {
    public Uint8Serializer(Config config) {
      super(config, Uint8.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, Uint8 value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      buffer.writeByte(value.byteValue());
    }

    @Override
    public Uint8 read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      return Uint8.valueOf(buffer.readByte());
    }
  }

  /** Serializer for Uint16 wrapper type. */
  public static class Uint16Serializer
      extends Serializers.CrossLanguageCompatibleSerializer<Uint16> {
    public Uint16Serializer(Config config) {
      super(config, Uint16.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, Uint16 value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      buffer.writeInt16((short) value.toInt());
    }

    @Override
    public Uint16 read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      return Uint16.valueOf(buffer.readInt16());
    }
  }

  /** Serializer for Uint32 wrapper type. */
  public static class Uint32Serializer
      extends Serializers.CrossLanguageCompatibleSerializer<Uint32> {
    public Uint32Serializer(Config config) {
      super(config, Uint32.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, Uint32 value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      buffer.writeInt32((int) value.toLong());
    }

    @Override
    public Uint32 read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      return Uint32.valueOf(buffer.readInt32());
    }
  }

  /** Serializer for Uint64 wrapper type. */
  public static class Uint64Serializer
      extends Serializers.CrossLanguageCompatibleSerializer<Uint64> {
    public Uint64Serializer(Config config) {
      super(config, Uint64.class);
    }

    @Override
    public void write(org.apache.fory.context.WriteContext writeContext, Uint64 value) {
    MemoryBuffer buffer = writeContext.getBuffer();
      buffer.writeInt64(value.toLong());
    }

    @Override
    public Uint64 read(org.apache.fory.context.ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
      return Uint64.valueOf(buffer.readInt64());
    }
  }
}
