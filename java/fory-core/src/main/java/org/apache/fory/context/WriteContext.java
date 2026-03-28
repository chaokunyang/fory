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
import org.apache.fory.Fory;
import org.apache.fory.config.Config;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeInfoHolder;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.ArraySerializers;
import org.apache.fory.serializer.BufferCallback;
import org.apache.fory.serializer.BufferObject;
import org.apache.fory.serializer.PrimitiveSerializers.LongSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.StringSerializer;
import org.apache.fory.serializer.UnknownClass.UnknownStruct;
import org.apache.fory.type.Generics;
import org.apache.fory.type.Types;
import org.apache.fory.util.Preconditions;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class WriteContext {
  private final Config config;
  private final Generics generics;
  private final TypeResolver typeResolver;
  private final RefWriter refWriter;
  private final MetaStringWriter metaStringWriter;
  private final StringSerializer stringSerializer;
  private final boolean crossLanguage;
  private final boolean compressInt;
  private final LongEncoding longEncoding;
  private final boolean forVirtualThread;
  private final boolean scopedMetaShareEnabled;
  private final IdentityHashMap<Object, Object> contextObjects = new IdentityHashMap<>();
  private MemoryBuffer buffer;
  private BufferCallback bufferCallback;
  private MetaContext metaContext;
  private int depth;

  public WriteContext(
      Config config,
      Generics generics,
      TypeResolver typeResolver,
      RefWriter refWriter,
      MetaStringWriter metaStringWriter) {
    this.config = config;
    this.generics = generics;
    this.typeResolver = typeResolver;
    this.refWriter = refWriter;
    this.metaStringWriter = metaStringWriter;
    stringSerializer = new StringSerializer(config);
    crossLanguage = config.isXlang();
    compressInt = config.compressInt();
    longEncoding = config.longEncoding();
    forVirtualThread = config.forVirtualThread();
    scopedMetaShareEnabled = config.isScopedMetaShareEnabled();
    if (scopedMetaShareEnabled) {
      metaContext = new MetaContext();
    }
  }

  public void prepare(MemoryBuffer buffer, BufferCallback callback) {
    this.buffer = buffer;
    bufferCallback = callback;
  }

  public MemoryBuffer getBuffer() {
    return Preconditions.checkNotNull(buffer);
  }

  public MemoryBuffer getBufferOrNull() {
    return buffer;
  }

  public void reset() {
    refWriter.reset();
    metaStringWriter.reset();
    if (!contextObjects.isEmpty()) {
      contextObjects.clear();
    }
    if (scopedMetaShareEnabled) {
      metaContext.classMap.clear();
    } else {
      metaContext = null;
    }
    buffer = null;
    bufferCallback = null;
    depth = 0;
    if (forVirtualThread) {
      stringSerializer.clearBuffer(config.bufferSizeLimitBytes());
    }
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

  public RefWriter getRefWriter() {
    return refWriter;
  }

  public boolean writeRefOrNull(Object obj) {
    return refWriter.writeRefOrNull(buffer, obj);
  }

  public boolean writeRefValueFlag(Object obj) {
    return refWriter.writeRefValueFlag(buffer, obj);
  }

  public boolean writeNullFlag(Object obj) {
    return refWriter.writeNullFlag(buffer, obj);
  }

  public void replaceRef(Object original, Object newObject) {
    refWriter.replaceRef(original, newObject);
  }

  public MetaStringWriter getMetaStringWriter() {
    return metaStringWriter;
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

  public boolean isCrossLanguage() {
    return crossLanguage;
  }

  public boolean compressInt() {
    return compressInt;
  }

  public LongEncoding longEncoding() {
    return longEncoding;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public void increaseDepth(int diff) {
    depth += diff;
  }

  public void increaseDepth() {
    depth += 1;
  }

  public void decreaseDepth() {
    depth -= 1;
  }

  public BufferCallback getBufferCallback() {
    return bufferCallback;
  }

  /** Serialize a nullable referencable object to the current buffer. */
  public void writeRef(Object obj) {
    MemoryBuffer buffer = this.buffer;
    if (!refWriter.writeRefOrNull(buffer, obj)) {
      TypeResolver resolver = typeResolver;
      TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass());
      if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
        return;
      }
      resolver.writeTypeInfo(this, typeInfo);
      writeData(typeInfo, obj);
    }
  }

  public void writeRef(Object obj, TypeInfoHolder classInfoHolder) {
    MemoryBuffer buffer = this.buffer;
    if (!refWriter.writeRefOrNull(buffer, obj)) {
      TypeResolver resolver = typeResolver;
      TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass(), classInfoHolder);
      if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
        return;
      }
      resolver.writeTypeInfo(this, typeInfo);
      writeData(typeInfo, obj);
    }
  }

  public void writeRef(Object obj, TypeInfo typeInfo) {
    MemoryBuffer buffer = this.buffer;
    if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
      if (!refWriter.writeRefOrNull(buffer, obj)) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
      }
      return;
    }
    TypeResolver resolver = typeResolver;
    Serializer<Object> serializer = typeInfo.getSerializer();
    if (serializer.needToWriteRef()) {
      if (!refWriter.writeRefOrNull(buffer, obj)) {
        resolver.writeTypeInfo(this, typeInfo);
        depth++;
        serializer.write(this, obj);
        depth--;
      }
    } else if (obj == null) {
      buffer.writeByte(Fory.NULL_FLAG);
    } else {
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      resolver.writeTypeInfo(this, typeInfo);
      depth++;
      serializer.write(this, obj);
      depth--;
    }
  }

  public <T> void writeRef(T obj, Serializer<T> serializer) {
    MemoryBuffer buffer = this.buffer;
    if (serializer.needToWriteRef()) {
      if (!refWriter.writeRefOrNull(buffer, obj)) {
        depth++;
        serializer.write(this, obj);
        depth--;
      }
    } else if (obj == null) {
      buffer.writeByte(Fory.NULL_FLAG);
    } else {
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      depth++;
      serializer.write(this, obj);
      depth--;
    }
  }

  /**
   * Serialize a not-null and non-reference object to the current buffer.
   *
   * <p>If reference is enabled, this method should be called only when the object is first seen in
   * the object graph.
   */
  public void writeNonRef(Object obj) {
    MemoryBuffer buffer = this.buffer;
    TypeResolver resolver = typeResolver;
    TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass());
    if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
      depth++;
      typeInfo.getSerializer().write(this, obj);
      depth--;
      return;
    }
    resolver.writeTypeInfo(this, typeInfo);
    writeData(typeInfo, obj);
  }

  public void writeNonRef(Object obj, Serializer serializer) {
    depth++;
    serializer.write(this, obj);
    depth--;
  }

  public void writeNonRef(Object obj, TypeInfoHolder holder) {
    MemoryBuffer buffer = this.buffer;
    TypeResolver resolver = typeResolver;
    TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass(), holder);
    if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
      depth++;
      typeInfo.getSerializer().write(this, obj);
      depth--;
      return;
    }
    resolver.writeTypeInfo(this, typeInfo);
    writeData(typeInfo, obj);
  }

  public void writeNonRef(Object obj, TypeInfo typeInfo) {
    MemoryBuffer buffer = this.buffer;
    if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
      depth++;
      typeInfo.getSerializer().write(this, obj);
      depth--;
      return;
    }
    typeResolver.writeTypeInfo(this, typeInfo);
    writeData(typeInfo, obj);
  }

  /** Class/type info should be written already. */
  public void writeData(TypeInfo typeInfo, Object obj) {
    MemoryBuffer buffer = this.buffer;
    int typeId = typeInfo.getTypeId();
    switch (typeId) {
      case Types.BOOL:
        buffer.writeBoolean((Boolean) obj);
        break;
      case Types.INT8:
        buffer.writeByte((Byte) obj);
        break;
      case Types.INT16:
        buffer.writeInt16((Short) obj);
        break;
      case ClassResolver.CHAR_ID:
        buffer.writeChar((Character) obj);
        break;
      case Types.INT32:
      case Types.VARINT32:
        if (compressInt) {
          buffer.writeVarInt32((Integer) obj);
        } else {
          buffer.writeInt32((Integer) obj);
        }
        break;
      case Types.INT64:
        LongSerializer.writeInt64(buffer, (Long) obj, longEncoding);
        break;
      case Types.VARINT64:
        buffer.writeVarInt64((Long) obj);
        break;
      case Types.TAGGED_INT64:
        buffer.writeTaggedInt64((Long) obj);
        break;
      case Types.FLOAT32:
        buffer.writeFloat32((Float) obj);
        break;
      case Types.FLOAT64:
        buffer.writeFloat64((Double) obj);
        break;
      case Types.STRING:
        if (typeInfo.getCls() == String.class) {
          stringSerializer.writeString(buffer, (String) obj);
          break;
        }
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
        break;
      default:
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
    }
  }

  public void writeBufferObject(BufferObject bufferObject) {
    MemoryBuffer buffer = this.buffer;
    if (bufferCallback == null || bufferCallback.apply(bufferObject)) {
      buffer.writeBoolean(true);
      int totalBytes = bufferObject.totalBytes();
      if (!crossLanguage) {
        buffer.writeVarUint32Aligned(totalBytes);
      } else {
        buffer.writeVarUint32(totalBytes);
      }
      int writerIndex = buffer.writerIndex();
      buffer.ensure(writerIndex + bufferObject.totalBytes());
      bufferObject.writeTo(buffer);
      int size = buffer.writerIndex() - writerIndex;
      Preconditions.checkArgument(size == totalBytes);
    } else {
      buffer.writeBoolean(false);
    }
  }

  public void writeBufferObject(ArraySerializers.PrimitiveArrayBufferObject bufferObject) {
    MemoryBuffer buffer = this.buffer;
    if (bufferCallback == null || bufferCallback.apply(bufferObject)) {
      buffer.writeBoolean(true);
      int totalBytes = bufferObject.totalBytes();
      if (!crossLanguage) {
        buffer.writeVarUint32Aligned(totalBytes);
      } else {
        buffer.writeVarUint32(totalBytes);
      }
      bufferObject.writeTo(buffer);
    } else {
      buffer.writeBoolean(false);
    }
  }

  public void writeString(String str) {
    stringSerializer.writeString(buffer, str);
  }

  public void writeStringRef(String str) {
    MemoryBuffer buffer = this.buffer;
    if (stringSerializer.needToWriteRef()) {
      if (!refWriter.writeRefOrNull(buffer, str)) {
        stringSerializer.writeString(buffer, str);
      }
    } else if (str == null) {
      buffer.writeByte(Fory.NULL_FLAG);
    } else {
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      stringSerializer.write(this, str);
    }
  }

  public void writeInt64(long value) {
    LongSerializer.writeInt64(buffer, value, longEncoding);
  }
}
