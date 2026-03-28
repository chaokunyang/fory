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

import java.util.function.Supplier;
import org.apache.fory.Fory;
import org.apache.fory.config.Config;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.MetaStringResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.SerializationContext;
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
  private static final ThreadLocal<WriteContext> CURRENT = new ThreadLocal<>();
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
  private final boolean forVirtualThread;
  private MemoryBuffer defaultBuffer;
  private MemoryBuffer buffer;
  private BufferCallback bufferCallback;
  private int depth;
  private boolean active;

  public WriteContext(
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
    forVirtualThread = config.forVirtualThread();
  }

  public void prepare(MemoryBuffer buffer, BufferCallback callback) {
    this.buffer = buffer;
    bufferCallback = callback;
    active = true;
  }

  public <T> T run(MemoryBuffer buffer, BufferCallback callback, Supplier<T> action) {
    prepare(buffer, callback);
    WriteContext previous = enter(this);
    try {
      return action.get();
    } finally {
      restore(previous);
      reset();
    }
  }

  public static WriteContext current() {
    WriteContext context = CURRENT.get();
    if (context == null) {
      throw new IllegalStateException("WriteContext is only available during serialization");
    }
    return context;
  }

  public static WriteContext enter(WriteContext context) {
    WriteContext previous = CURRENT.get();
    CURRENT.set(context);
    return previous;
  }

  public static void restore(WriteContext previous) {
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
    if (active) {
      return buffer;
    }
    MemoryBuffer buf = defaultBuffer;
    if (buf == null) {
      buf = defaultBuffer = MemoryBuffer.newHeapBuffer(64);
    }
    return buf;
  }

  public void resetBuffer() {
    MemoryBuffer buf = defaultBuffer;
    if (buf != null && buf.size() > config.bufferSizeLimitBytes()) {
      defaultBuffer = MemoryBuffer.newHeapBuffer(config.bufferSizeLimitBytes());
    }
  }

  public void reset() {
    refResolver.resetWrite();
    typeResolver.resetWrite();
    metaStringResolver.resetWrite();
    serializationContext.resetWrite();
    buffer = null;
    bufferCallback = null;
    depth = 0;
    active = false;
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

  public RefResolver getRefResolver() {
    return refResolver;
  }

  public StringSerializer getStringSerializer() {
    return stringSerializer;
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

  public void incDepth(int diff) {
    depth += diff;
  }

  public void incDepth() {
    depth += 1;
  }

  public void decDepth() {
    depth -= 1;
  }

  public BufferCallback getBufferCallback() {
    return bufferCallback;
  }

  /** Serialize a nullable referencable object to the current buffer. */
  public void writeRef(Object obj) {
    MemoryBuffer buffer = this.buffer;
    if (!refResolver.writeRefOrNull(buffer, obj)) {
      TypeResolver resolver = typeResolver;
      TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass());
      if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
        return;
      }
      resolver.writeTypeInfo(buffer, typeInfo);
      writeData(typeInfo, obj);
    }
  }

  public void writeRef(Object obj, TypeInfoHolder classInfoHolder) {
    MemoryBuffer buffer = this.buffer;
    if (!refResolver.writeRefOrNull(buffer, obj)) {
      TypeResolver resolver = typeResolver;
      TypeInfo typeInfo = resolver.getTypeInfo(obj.getClass(), classInfoHolder);
      if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
        return;
      }
      resolver.writeTypeInfo(buffer, typeInfo);
      writeData(typeInfo, obj);
    }
  }

  public void writeRef(Object obj, TypeInfo typeInfo) {
    MemoryBuffer buffer = this.buffer;
    if (crossLanguage && typeInfo.getCls() == UnknownStruct.class) {
      if (!refResolver.writeRefOrNull(buffer, obj)) {
        depth++;
        typeInfo.getSerializer().write(this, obj);
        depth--;
      }
      return;
    }
    TypeResolver resolver = typeResolver;
    Serializer<Object> serializer = typeInfo.getSerializer();
    if (serializer.needToWriteRef()) {
      if (!refResolver.writeRefOrNull(buffer, obj)) {
        resolver.writeTypeInfo(buffer, typeInfo);
        depth++;
        serializer.write(this, obj);
        depth--;
      }
    } else if (obj == null) {
      buffer.writeByte(Fory.NULL_FLAG);
    } else {
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      resolver.writeTypeInfo(buffer, typeInfo);
      depth++;
      serializer.write(this, obj);
      depth--;
    }
  }

  public <T> void writeRef(T obj, Serializer<T> serializer) {
    MemoryBuffer buffer = this.buffer;
    if (serializer.needToWriteRef()) {
      if (!refResolver.writeRefOrNull(buffer, obj)) {
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
    resolver.writeTypeInfo(buffer, typeInfo);
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
    resolver.writeTypeInfo(buffer, typeInfo);
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
    typeResolver.writeTypeInfo(buffer, typeInfo);
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
      if (!refResolver.writeRefOrNull(buffer, str)) {
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
