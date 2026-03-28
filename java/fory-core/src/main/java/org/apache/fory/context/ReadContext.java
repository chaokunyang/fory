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

import java.util.IdentityHashMap;
import java.util.Iterator;
import org.apache.fory.Fory;
import org.apache.fory.config.Config;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.ClassResolver;
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
  private final Config config;
  private final Generics generics;
  private final TypeResolver typeResolver;
  private final RefReader refReader;
  private final MetaStringReader metaStringReader;
  private final StringSerializer stringSerializer;
  private final boolean crossLanguage;
  private final boolean compressInt;
  private final LongEncoding longEncoding;
  private final int maxDepth;
  private final boolean scopedMetaShareEnabled;
  private final boolean forVirtualThread;
  private final IdentityHashMap<Object, Object> contextObjects = new IdentityHashMap<>();
  private MemoryBuffer buffer;
  private Iterator<MemoryBuffer> outOfBandBuffers;
  private MetaContext metaContext;
  private boolean peerOutOfBandEnabled;
  private int depth;

  public ReadContext(
      Config config,
      Generics generics,
      TypeResolver typeResolver,
      RefReader refReader,
      MetaStringReader metaStringReader) {
    this.config = config;
    this.generics = generics;
    this.typeResolver = typeResolver;
    this.refReader = refReader;
    this.metaStringReader = metaStringReader;
    stringSerializer = new StringSerializer(config);
    crossLanguage = config.isXlang();
    compressInt = config.compressInt();
    longEncoding = config.longEncoding();
    maxDepth = config.maxDepth();
    forVirtualThread = config.forVirtualThread();
    scopedMetaShareEnabled = config.isScopedMetaShareEnabled();
    if (scopedMetaShareEnabled) {
      metaContext = new MetaContext();
    }
  }

  public void prepare(
      MemoryBuffer buffer, Iterable<MemoryBuffer> outOfBandBuffers, boolean peerOutOfBandEnabled) {
    this.buffer = buffer;
    this.peerOutOfBandEnabled = peerOutOfBandEnabled;
    this.outOfBandBuffers = outOfBandBuffers == null ? null : outOfBandBuffers.iterator();
  }

  public MemoryBuffer getBuffer() {
    return buffer;
  }

  public void reset() {
    refReader.reset();
    metaStringReader.reset();
    if (!contextObjects.isEmpty()) {
      contextObjects.clear();
    }
    if (scopedMetaShareEnabled) {
      metaContext.readTypeInfos.size = 0;
    } else {
      metaContext = null;
    }
    if (forVirtualThread) {
      stringSerializer.clearBuffer(config.bufferSizeLimitBytes());
    }
    buffer = null;
    outOfBandBuffers = null;
    peerOutOfBandEnabled = false;
    depth = 0;
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

  public RefReader getRefReader() {
    return refReader;
  }

  public byte readRefOrNull() {
    return refReader.readRefOrNull(buffer);
  }

  public int preserveRefId() {
    return refReader.preserveRefId();
  }

  public int preserveRefId(int refId) {
    return refReader.preserveRefId(refId);
  }

  public int tryPreserveRefId() {
    return refReader.tryPreserveRefId(buffer);
  }

  public int lastPreservedRefId() {
    return refReader.lastPreservedRefId();
  }

  public boolean hasPreservedRefId() {
    return refReader.hasPreservedRefId();
  }

  public void reference(Object object) {
    refReader.reference(object);
  }

  public Object getReadObject(int id) {
    return refReader.getReadObject(id);
  }

  public Object getReadObject() {
    return refReader.getReadObject();
  }

  public void setReadObject(int id, Object object) {
    refReader.setReadObject(id, object);
  }

  public MetaStringReader getMetaStringReader() {
    return metaStringReader;
  }

  public StringSerializer getStringSerializer() {
    return stringSerializer;
  }

  public Object putContextObject(Object key, Object value) {
    return contextObjects.put(key, value);
  }

  public boolean hasContextObject(Object key) {
    return contextObjects.containsKey(key);
  }

  public Object getContextObject(Object key) {
    return contextObjects.get(key);
  }

  public MetaContext getMetaContext() {
    return metaContext;
  }

  public void setMetaContext(MetaContext metaContext) {
    Preconditions.checkArgument(!scopedMetaShareEnabled);
    this.metaContext = metaContext;
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

  public void increaseDepth() {
    if ((depth += 1) > maxDepth) {
      throw new InsecureException(
          String.format(
              "Read depth exceed max depth %s, the deserialization data may be malicious. If "
                  + "it's not malicious, please increase max read depth by "
                  + "ForyBuilder#withMaxDepth(largerDepth)",
              maxDepth));
    }
  }

  public void increaseDepth(int diff) {
    depth += diff;
  }

  public void decreaseDepth() {
    depth -= 1;
  }

  public MemoryBuffer readBufferObject() {
    MemoryBuffer buffer = this.buffer;
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

  public String readString() {
    MemoryBuffer buffer = this.buffer;
    return stringSerializer.readString(buffer);
  }

  public String readStringRef() {
    MemoryBuffer buffer = this.buffer;
    if (stringSerializer.needToWriteRef()) {
      int nextReadRefId = refReader.tryPreserveRefId(buffer);
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        String obj = stringSerializer.read(this);
        refReader.setReadObject(nextReadRefId, obj);
        return obj;
      }
      return (String) refReader.getReadObject();
    }
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return stringSerializer.read(this);
  }

  public long readInt64() {
    MemoryBuffer buffer = this.buffer;
    return LongSerializer.readInt64(buffer, longEncoding);
  }

  /** Deserialize nullable referencable object from the current buffer. */
  public Object readRef() {
    MemoryBuffer buffer = this.buffer;
    int nextReadRefId = refReader.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      TypeInfo typeInfo = typeResolver.readTypeInfo(this);
      Object o = readNonRef(typeInfo);
      refReader.setReadObject(nextReadRefId, o);
      return o;
    }
    return refReader.getReadObject();
  }

  public Object readRef(TypeInfo typeInfo) {
    int nextReadRefId = refReader.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      Object o = readNonRef(typeInfo);
      refReader.setReadObject(nextReadRefId, o);
      return o;
    }
    return refReader.getReadObject();
  }

  public Object readRef(TypeInfoHolder classInfoHolder) {
    int nextReadRefId = refReader.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      TypeInfo typeInfo = typeResolver.readTypeInfo(this, classInfoHolder);
      Object o = readNonRef(typeInfo);
      refReader.setReadObject(nextReadRefId, o);
      return o;
    }
    return refReader.getReadObject();
  }

  public <T> T readRef(Serializer<T> serializer) {
    if (serializer.needToWriteRef()) {
      int nextReadRefId = refReader.tryPreserveRefId(buffer);
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        Object o = readNonRef(serializer);
        refReader.setReadObject(nextReadRefId, o);
        return (T) o;
      }
      return (T) refReader.getReadObject();
    }
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.NULL_FLAG) {
      return null;
    }
    return (T) readNonRef(serializer);
  }

  /** Deserialize not-null and non-reference object from the current buffer. */
  public Object readNonRef() {
    TypeInfo typeInfo = typeResolver.readTypeInfo(this);
    return readNonRef(typeInfo);
  }

  public Object readNonRef(TypeInfoHolder classInfoHolder) {
    TypeInfo typeInfo = typeResolver.readTypeInfo(this, classInfoHolder);
    return readNonRef(typeInfo);
  }

  public Object readNonRef(TypeInfo typeInfo) {
    return readDataInternal(typeInfo);
  }

  public Object readNonRef(Serializer<?> serializer) {
    increaseDepth();
    Object o = serializer.read(this);
    decreaseDepth();
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
    increaseDepth();
    Serializer<?> serializer = typeInfo.getSerializer();
    Object read = serializer.read(this);
    decreaseDepth();
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
        increaseDepth();
        Object stringLike = typeInfo.getSerializer().read(this);
        decreaseDepth();
        return stringLike;
      default:
        increaseDepth();
        Object read = typeInfo.getSerializer().read(this);
        decreaseDepth();
        return read;
    }
  }
}
