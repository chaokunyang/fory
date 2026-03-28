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

package org.apache.fory.context;

import java.util.Iterator;
import java.util.function.Supplier;
import org.apache.fory.Fory;
import org.apache.fory.config.Config;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.MetaStringResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.SerializationContext;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeInfoHolder;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.PrimitiveSerializers.LongSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.StringSerializer;
import org.apache.fory.type.Generics;
import org.apache.fory.type.Types;
import org.apache.fory.util.Preconditions;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class ReadContext {
  private static final ThreadLocal<ReadContext> CURRENT = new ThreadLocal<>();
  private final Config config;
  private final Generics generics;
  private final TypeResolver typeResolver;
  private final RefResolver refResolver;
  private final MetaStringResolver metaStringResolver;
  private final SerializationContext serializationContext;
  private final StringSerializer stringSerializer;
  private final boolean crossLanguage;
  private final boolean compressInt;
  private final LongEncoding longEncoding;
  private final int maxDepth;
  private MemoryBuffer buffer;
  private Iterator<MemoryBuffer> outOfBandBuffers;
  private boolean peerOutOfBandEnabled;
  private int depth;
  private boolean active;

  public ReadContext(
      Config config,
      Generics generics,
      TypeResolver typeResolver,
      RefResolver refResolver,
      MetaStringResolver metaStringResolver,
      SerializationContext serializationContext,
      StringSerializer stringSerializer) {
    this.config = config;
    this.generics = generics;
    this.typeResolver = typeResolver;
    this.refResolver = refResolver;
    this.metaStringResolver = metaStringResolver;
    this.serializationContext = serializationContext;
    this.stringSerializer = stringSerializer;
    crossLanguage = config.isXlang();
    compressInt = config.compressInt();
    longEncoding = config.longEncoding();
    maxDepth = config.maxDepth();
  }

  public void prepare(
      MemoryBuffer buffer, Iterable<MemoryBuffer> outOfBandBuffers, boolean peerOutOfBandEnabled) {
    this.buffer = buffer;
    this.peerOutOfBandEnabled = peerOutOfBandEnabled;
    this.outOfBandBuffers = outOfBandBuffers == null ? null : outOfBandBuffers.iterator();
    active = true;
  }

  public <T> T run(
      MemoryBuffer buffer,
      Iterable<MemoryBuffer> outOfBandBuffers,
      boolean peerOutOfBandEnabled,
      Supplier<T> action) {
    prepare(buffer, outOfBandBuffers, peerOutOfBandEnabled);
    ReadContext previous = enter(this);
    try {
      return action.get();
    } finally {
      restore(previous);
      reset();
    }
  }

  public static ReadContext current() {
    ReadContext context = CURRENT.get();
    if (context == null) {
      throw new IllegalStateException("ReadContext is only available during deserialization");
    }
    return context;
  }

  public static ReadContext enter(ReadContext context) {
    ReadContext previous = CURRENT.get();
    CURRENT.set(context);
    return previous;
  }

  public static void restore(ReadContext previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }

  public boolean isActive() {
    return active;
  }

  public MemoryBuffer getBuffer() {
    return buffer;
  }

  public void reset() {
    refResolver.resetRead();
    typeResolver.resetRead();
    metaStringResolver.resetRead();
    serializationContext.resetRead();
    buffer = null;
    outOfBandBuffers = null;
    peerOutOfBandEnabled = false;
    depth = 0;
    active = false;
  }

  public Config getConfig() {
    return config;
  }

  public Generics getGenerics() {
    return generics;
  }

  public TypeResolver getTypeResolver() {
    return typeResolver;
  }

  public RefResolver getRefResolver() {
    return refResolver;
  }

  public StringSerializer getStringSerializer() {
    return stringSerializer;
  }

  public boolean isPeerOutOfBandEnabled() {
    return peerOutOfBandEnabled;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public void incDepth(int diff) {
    depth += diff;
  }

  public void decDepth() {
    depth -= 1;
  }

  public void incReadDepth() {
    if ((depth += 1) > maxDepth) {
      throw new InsecureException(
          String.format(
              "Read depth exceed max depth %s, the deserialization data may be malicious. If "
                  + "it's not malicious, please increase max read depth by "
                  + "ForyBuilder#withMaxDepth(largerDepth)",
              maxDepth));
    }
  }

  public MemoryBuffer readBufferObject(MemoryBuffer buffer) {
    boolean inBand = buffer.readBoolean();
    if (inBand) {
      int size;
      if (!crossLanguage) {
        size = buffer.readAlignedVarUint32();
      } else {
        size = buffer.readVarUint32();
      }
      if (buffer.readerIndex() + size > buffer.size() && buffer.getStreamReader() != null) {
        buffer.getStreamReader().fillBuffer(buffer.readerIndex() + size - buffer.size());
      }
      MemoryBuffer slice = buffer.slice(buffer.readerIndex(), size);
      buffer.readerIndex(buffer.readerIndex() + size);
      return slice;
    }
    Preconditions.checkArgument(outOfBandBuffers.hasNext());
    return outOfBandBuffers.next();
  }

  public String readString(MemoryBuffer buffer) {
    return stringSerializer.readString(buffer);
  }

  public String readStringRef(MemoryBuffer buffer) {
    if (stringSerializer.needToWriteRef()) {
      int nextReadRefId = refResolver.tryPreserveRefId(buffer);
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        String obj = stringSerializer.read(this);
        refResolver.setReadObject(nextReadRefId, obj);
        return obj;
      }
      return (String) refResolver.getReadObject();
    }
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return stringSerializer.read(this);
  }

  public long readInt64(MemoryBuffer buffer) {
    return LongSerializer.readInt64(buffer, longEncoding);
  }

  /** Deserialize nullable referencable object from the current buffer. */
  public Object readRef() {
    MemoryBuffer buffer = this.buffer;
    int nextReadRefId = refResolver.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      TypeInfo typeInfo = typeResolver.readTypeInfo(buffer);
      Object o = readNonRef(typeInfo);
      refResolver.setReadObject(nextReadRefId, o);
      return o;
    }
    return refResolver.getReadObject();
  }

  public Object readRef(TypeInfo typeInfo) {
    int nextReadRefId = refResolver.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      Object o = readNonRef(typeInfo);
      refResolver.setReadObject(nextReadRefId, o);
      return o;
    }
    return refResolver.getReadObject();
  }

  public Object readRef(TypeInfoHolder classInfoHolder) {
    int nextReadRefId = refResolver.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      TypeInfo typeInfo = typeResolver.readTypeInfo(buffer, classInfoHolder);
      Object o = readNonRef(typeInfo);
      refResolver.setReadObject(nextReadRefId, o);
      return o;
    }
    return refResolver.getReadObject();
  }

  public <T> T readRef(Serializer<T> serializer) {
    if (serializer.needToWriteRef()) {
      int nextReadRefId = refResolver.tryPreserveRefId(buffer);
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        Object o = readNonRef(serializer);
        refResolver.setReadObject(nextReadRefId, o);
        return (T) o;
      }
      return (T) refResolver.getReadObject();
    }
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return (T) readNonRef(serializer);
  }

  /** Deserialize not-null and non-reference object from the current buffer. */
  public Object readNonRef() {
    TypeInfo typeInfo = typeResolver.readTypeInfo(buffer);
    return readNonRef(typeInfo);
  }

  public Object readNonRef(TypeInfoHolder classInfoHolder) {
    TypeInfo typeInfo = typeResolver.readTypeInfo(buffer, classInfoHolder);
    return readNonRef(typeInfo);
  }

  public Object readNonRef(TypeInfo typeInfo) {
    return readDataInternal(typeInfo);
  }

  public Object readNonRef(Serializer<?> serializer) {
    incReadDepth();
    Object o = serializer.read(this);
    depth--;
    return o;
  }

  /** Read object class and data without tracking ref. */
  public Object readNullable() {
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return readNonRef();
  }

  public Object readNullable(Serializer serializer) {
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return serializer.read(this);
  }

  public Object readNullable(TypeInfoHolder classInfoHolder) {
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return readNonRef(classInfoHolder);
  }

  /** Class should be read already. */
  public Object readData(TypeInfo typeInfo) {
    incReadDepth();
    Serializer<?> serializer = typeInfo.getSerializer();
    Object read = serializer.read(this);
    depth--;
    return read;
  }

  private Object readDataInternal(TypeInfo typeInfo) {
    MemoryBuffer buffer = this.buffer;
    int typeId = typeInfo.getTypeId();
    switch (typeId) {
      case Types.BOOL:
        return buffer.readBoolean();
      case Types.INT8:
        return buffer.readByte();
      case ClassResolver.CHAR_ID:
        return buffer.readChar();
      case Types.INT16:
        return buffer.readInt16();
      case Types.INT32:
        if (compressInt) {
          return buffer.readVarInt32();
        }
        return buffer.readInt32();
      case Types.VARINT32:
        return buffer.readVarInt32();
      case Types.FLOAT32:
        return buffer.readFloat32();
      case Types.INT64:
        return LongSerializer.readInt64(buffer, longEncoding);
      case Types.VARINT64:
        return buffer.readVarInt64();
      case Types.TAGGED_INT64:
        return buffer.readTaggedInt64();
      case Types.FLOAT64:
        return buffer.readFloat64();
      case Types.STRING:
        if (typeInfo.getCls() == String.class) {
          return stringSerializer.readString(buffer);
        }
        incReadDepth();
        Object stringLike = typeInfo.getSerializer().read(this);
        depth--;
        return stringLike;
      default:
        incReadDepth();
        Object read = typeInfo.getSerializer().read(this);
        depth--;
        return read;
    }
  }
}
