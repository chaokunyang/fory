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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.fory.annotation.Internal;
import org.apache.fory.builder.JITContext;
import org.apache.fory.collection.IdentityMap;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.Config;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.CopyException;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.exception.ForyException;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.exception.SerializationException;
import org.apache.fory.io.ForyInputStream;
import org.apache.fory.io.ForyReadableChannel;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.meta.MetaCompressor;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.MapRefResolver;
import org.apache.fory.resolver.MetaStringResolver;
import org.apache.fory.resolver.NoRefResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.SerializationContext;
import org.apache.fory.resolver.SharedRegistry;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeInfoHolder;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.resolver.XtypeResolver;
import org.apache.fory.serializer.ArraySerializers;
import org.apache.fory.serializer.BufferCallback;
import org.apache.fory.serializer.BufferObject;
import org.apache.fory.serializer.PrimitiveSerializers.LongSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.SerializerFactory;
import org.apache.fory.serializer.StringSerializer;
import org.apache.fory.serializer.UnknownClass.UnknownStruct;
import org.apache.fory.serializer.collection.CollectionSerializers.ArrayListSerializer;
import org.apache.fory.serializer.collection.MapSerializers.HashMapSerializer;
import org.apache.fory.type.Generics;
import org.apache.fory.type.Types;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.StringUtils;

/**
 * Cross-language header layout: 1-byte bitmap.
 *
 * <p>Bit 0: null flag, Bit 1: xlang flag, Bit 2: out-of-band flag, Bits 3-7 reserved.
 *
 * <p>serialize/deserialize is user API for root object serialization, write/read api is for inner
 * serialization.
 */
@NotThreadSafe
public final class Fory implements BaseFory {
  private static final Logger LOG = LoggerFactory.getLogger(Fory.class);

  public static final byte NULL_FLAG = -3;
  // This flag indicates that object is a not-null value.
  // We don't use another byte to indicate REF, so that we can save one byte.
  public static final byte REF_FLAG = -2;
  // this flag indicates that the object is a non-null value.
  public static final byte NOT_NULL_VALUE_FLAG = -1;
  // this flag indicates that the object is a referencable and first write.
  public static final byte REF_VALUE_FLAG = 0;
  public static final byte NOT_SUPPORT_XLANG = 0;
  private static final byte isNilFlag = 1;
  private static final byte isCrossLanguageFlag = 1 << 1;
  private static final byte isOutOfBandFlag = 1 << 2;

  private final Config config;
  private final boolean refTracking;
  private final boolean shareMeta;
  private final RefResolver refResolver;
  private final TypeResolver typeResolver;
  private final MetaStringResolver metaStringResolver;
  private final SerializationContext serializationContext;
  private final SharedRegistry sharedRegistry;
  private final ClassLoader classLoader;
  private final JITContext jitContext;
  private final StringSerializer stringSerializer;
  private final ArrayListSerializer arrayListSerializer;
  private final HashMapSerializer hashMapSerializer;
  private final boolean crossLanguage;
  private final boolean compressInt;
  private final LongEncoding longEncoding;
  private final Generics generics;
  private final WriteContext writeContext;
  private final ReadContext readContext;
  private final CopyContext copyContext;
  private boolean registrationFinished;
  private final boolean copyRefTracking;

  public Fory(ForyBuilder builder, ClassLoader classLoader) {
    this(builder, classLoader, new SharedRegistry());
  }

  public Fory(ForyBuilder builder, ClassLoader classLoader, SharedRegistry sharedRegistry) {
    // Avoid set classLoader in `ForyBuilder`, which won't be clear when
    // `org.apache.fory.ThreadSafeFory.clearClassLoader` is called.
    if (sharedRegistry == null) {
      sharedRegistry = new SharedRegistry();
    }
    if (classLoader == null) {
      classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader == null) {
        classLoader = Fory.class.getClassLoader();
      }
    }
    this.sharedRegistry = sharedRegistry;
    this.classLoader = classLoader;
    config = new Config(builder);
    crossLanguage = config.isXlang();
    this.refTracking = config.trackingRef();
    this.copyRefTracking = config.copyRef();
    this.shareMeta = config.isMetaShareEnabled();
    compressInt = config.compressInt();
    longEncoding = config.longEncoding();
    if (refTracking) {
      this.refResolver = new MapRefResolver(config.mapRefLoadFactor());
    } else {
      this.refResolver = new NoRefResolver();
    }
    jitContext = new JITContext(this);
    generics = new Generics(this);
    // init stringSerializer first, so other places can share same StringSerializer.
    stringSerializer = new StringSerializer(config);
    metaStringResolver = new MetaStringResolver(sharedRegistry);
    typeResolver = crossLanguage ? new XtypeResolver(this) : new ClassResolver(this);
    typeResolver.initialize();
    serializationContext = new SerializationContext(config);
    writeContext =
        new WriteContext(
            config,
            generics,
            typeResolver,
            refResolver,
            metaStringResolver,
            serializationContext,
            stringSerializer);
    readContext =
        new ReadContext(
            config,
            generics,
            typeResolver,
            refResolver,
            metaStringResolver,
            serializationContext,
            stringSerializer);
    copyContext = new CopyContext(typeResolver, copyRefTracking);
    arrayListSerializer = new ArrayListSerializer(typeResolver);
    hashMapSerializer = new HashMapSerializer(typeResolver);
    LOG.info("Created new fory {}", this);
  }

  @Override
  public void register(Class<?> cls) {
    getTypeResolver().register(cls);
  }

  @Override
  public void register(Class<?> cls, int id) {
    getTypeResolver().register(cls, Integer.toUnsignedLong(id));
  }

  /**
   * Register class with given type name, this method will have bigger serialization time/space cost
   * compared to register by id.
   */
  @Override
  public void register(Class<?> cls, String typeName) {
    int idx = typeName.lastIndexOf('.');
    String namespace = "";
    if (idx > 0) {
      namespace = typeName.substring(0, idx);
      typeName = typeName.substring(idx + 1);
    }
    register(cls, namespace, typeName);
  }

  public void register(Class<?> cls, String namespace, String typeName) {
    getTypeResolver().register(cls, namespace, typeName);
  }

  @Override
  public void register(String className) {
    getTypeResolver().register(className);
  }

  @Override
  public void register(String className, int classId) {
    getTypeResolver().register(className, Integer.toUnsignedLong(classId));
  }

  @Override
  public void register(String className, String namespace, String typeName) {
    getTypeResolver().register(className, namespace, typeName);
  }

  @Override
  public void registerUnion(Class<?> cls, int id, Serializer<?> serializer) {
    getTypeResolver().registerUnion(cls, Integer.toUnsignedLong(id), serializer);
  }

  @Override
  public void registerUnion(
      Class<?> cls, String namespace, String typeName, Serializer<?> serializer) {
    getTypeResolver().registerUnion(cls, namespace, typeName, serializer);
  }

  @Override
  public <T> void registerSerializer(Class<T> type, Class<? extends Serializer> serializerClass) {
    getTypeResolver().registerSerializer(type, serializerClass);
  }

  @Override
  public void registerSerializer(Class<?> type, Serializer<?> serializer) {
    getTypeResolver().registerSerializer(type, serializer);
  }

  @Override
  public void registerSerializer(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    getTypeResolver().registerSerializer(type, serializerCreator.apply(typeResolver));
  }

  @Override
  public <T> void registerSerializerAndType(
      Class<T> type, Class<? extends Serializer> serializerClass) {
    getTypeResolver().registerSerializerAndType(type, serializerClass);
  }

  @Override
  public void registerSerializerAndType(Class<?> type, Serializer<?> serializer) {
    getTypeResolver().registerSerializerAndType(type, serializer);
  }

  @Override
  public void registerSerializerAndType(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    getTypeResolver().registerSerializerAndType(type, serializerCreator.apply(typeResolver));
  }

  @Override
  public void setSerializerFactory(SerializerFactory serializerFactory) {
    typeResolver.setSerializerFactory(serializerFactory);
  }

  public SerializerFactory getSerializerFactory() {
    return typeResolver.getSerializerFactory();
  }

  public <T> Serializer<T> getSerializer(Class<T> cls) {
    Preconditions.checkNotNull(cls);
    return typeResolver.getSerializer(cls);
  }

  private void ensureRegistrationFinished() {
    if (!registrationFinished) {
      typeResolver.finishRegistration();
      registrationFinished = true;
    }
  }

  @Override
  public MemoryBuffer serialize(Object obj, long address, int size) {
    MemoryBuffer buffer = MemoryUtils.buffer(address, size);
    serialize(buffer, obj, null);
    return buffer;
  }

  @Override
  public byte[] serialize(Object obj) {
    MemoryBuffer buf = getBuffer();
    buf.writerIndex(0);
    serialize(buf, obj, null);
    byte[] bytes = buf.getBytes(0, buf.writerIndex());
    resetBuffer();
    return bytes;
  }

  @Override
  public byte[] serialize(Object obj, BufferCallback callback) {
    MemoryBuffer buf = getBuffer();
    buf.writerIndex(0);
    serialize(buf, obj, callback);
    byte[] bytes = buf.getBytes(0, buf.writerIndex());
    resetBuffer();
    return bytes;
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj) {
    return serialize(buffer, obj, null);
  }

  @Override
  public MemoryBuffer serialize(MemoryBuffer buffer, Object obj, BufferCallback callback) {
    ensureRegistrationFinished();
    return writeContext.run(
        buffer,
        callback,
        () -> {
          byte bitmap = 0;
          if (crossLanguage) {
            bitmap |= isCrossLanguageFlag;
          }
          if (obj == null) {
            bitmap |= isNilFlag;
            buffer.writeByte(bitmap);
            return buffer;
          }
          if (callback != null) {
            bitmap |= isOutOfBandFlag;
          }
          buffer.writeByte(bitmap);
          try {
            jitContext.lock();
            if (writeContext.getDepth() > 0) {
              throwDepthSerializationException();
            }
            writeContext.writeRef(obj);
            return buffer;
          } catch (Throwable t) {
            throw processSerializationError(t);
          } finally {
            jitContext.unlock();
          }
        });
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj) {
    serializeToStream(outputStream, buf -> serialize(buf, obj, null));
  }

  @Override
  public void serialize(OutputStream outputStream, Object obj, BufferCallback callback) {
    serializeToStream(outputStream, buf -> serialize(buf, obj, callback));
  }

  private ForyException processSerializationError(Throwable e) {
    if (!refTracking) {
      String msg =
          "Object may contain circular references, please enable ref tracking "
              + "by `ForyBuilder#withRefTracking(true)`";
      String rawMessage = e.getMessage();
      if (StringUtils.isNotBlank(rawMessage)) {
        msg += ": " + rawMessage;
      }
      if (e instanceof StackOverflowError) {
        e = ExceptionUtils.trySetStackOverflowErrorMessage((StackOverflowError) e, msg);
      }
    }
    if (!(e instanceof ForyException)) {
      e = new SerializationException(e);
    }
    throw (ForyException) e;
  }

  private ForyException processCopyError(Throwable e) {
    if (!copyRefTracking) {
      String msg =
          "Object may contain circular references, please enable ref tracking "
              + "by `ForyBuilder#withRefCopy(true)`";
      e = ExceptionUtils.trySetStackOverflowErrorMessage((StackOverflowError) e, msg);
    }
    if (!(e instanceof ForyException)) {
      throw new CopyException(e);
    }
    throw (ForyException) e;
  }

  public MemoryBuffer getBuffer() {
    return writeContext.getBuffer();
  }

  public void resetBuffer() {
    writeContext.resetBuffer();
  }

  /** Serialize a nullable referencable object to <code>buffer</code>. */
  public void writeRef(MemoryBuffer buffer, Object obj) {
    writeContext.writeRef(obj);
  }

  public void writeRef(MemoryBuffer buffer, Object obj, TypeInfoHolder classInfoHolder) {
    writeContext.writeRef(obj, classInfoHolder);
  }

  public void writeRef(MemoryBuffer buffer, Object obj, TypeInfo typeInfo) {
    writeContext.writeRef(obj, typeInfo);
  }

  public <T> void writeRef(MemoryBuffer buffer, T obj, Serializer<T> serializer) {
    writeContext.writeRef(obj, serializer);
  }

  /**
   * Serialize a not-null and non-reference object to <code>buffer</code>.
   *
   * <p>If reference is enabled, this method should be called only the object is first seen in the
   * object graph.
   */
  public void writeNonRef(MemoryBuffer buffer, Object obj) {
    writeContext.writeNonRef(obj);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj, Serializer serializer) {
    writeContext.writeNonRef(obj, serializer);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj, TypeInfoHolder holder) {
    writeContext.writeNonRef(obj, holder);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj, TypeInfo typeInfo) {
    writeContext.writeNonRef(obj, typeInfo);
  }

  /** Class/type info should be written already. */
  public void writeData(MemoryBuffer buffer, TypeInfo typeInfo, Object obj) {
    writeContext.writeData(typeInfo, obj);
  }

  public void writeBufferObject(MemoryBuffer buffer, BufferObject bufferObject) {
    writeContext.writeBufferObject(bufferObject);
  }

  // duplicate for speed.
  public void writeBufferObject(
      MemoryBuffer buffer, ArraySerializers.PrimitiveArrayBufferObject bufferObject) {
    writeContext.writeBufferObject(bufferObject);
  }

  public MemoryBuffer readBufferObject(MemoryBuffer buffer) {
    return readContext.readBufferObject(buffer);
  }

  public void writeString(MemoryBuffer buffer, String str) {
    writeContext.writeString(str);
  }

  public String readString(MemoryBuffer buffer) {
    return readContext.readString(buffer);
  }

  public void writeStringRef(MemoryBuffer buffer, String str) {
    writeContext.writeStringRef(str);
  }

  public String readStringRef(MemoryBuffer buffer) {
    return readContext.readStringRef(buffer);
  }

  public void writeInt64(MemoryBuffer buffer, long value) {
    LongSerializer.writeInt64(buffer, value, longEncoding);
  }

  public long readInt64(MemoryBuffer buffer) {
    return readContext.readInt64(buffer);
  }

  @Override
  public Object deserialize(byte[] bytes) {
    return deserialize(MemoryUtils.wrap(bytes), (Iterable<MemoryBuffer>) null);
  }

  @Override
  public <T> T deserialize(byte[] bytes, Class<T> type) {
    return deserialize(MemoryUtils.wrap(bytes), type);
  }

  @Override
  public <T> T deserialize(MemoryBuffer buffer, Class<T> type) {
    ensureRegistrationFinished();
    byte bitmap = buffer.readByte();
    if ((bitmap & isNilFlag) == isNilFlag) {
      return null;
    }
    boolean peerOutOfBandEnabled = (bitmap & isOutOfBandFlag) == isOutOfBandFlag;
    assert !peerOutOfBandEnabled : "Out of band buffers not passed in when deserializing";
    checkXlangBitmap(bitmap);
    try {
      return readContext.run(
          buffer,
          null,
          false,
          () -> {
            try {
              jitContext.lock();
              if (readContext.getDepth() > 0) {
                throwDepthDeserializationException();
              }
              return deserializeByType(buffer, type);
            } finally {
              jitContext.unlock();
            }
          });
    } catch (Throwable t) {
      throw ExceptionUtils.handleReadFailed(this, t);
    }
  }

  @Override
  public <T> T deserialize(ForyInputStream inputStream, Class<T> type) {
    try {
      return deserialize(inputStream.getBuffer(), type);
    } finally {
      inputStream.shrinkBuffer();
    }
  }

  @Override
  public <T> T deserialize(ForyReadableChannel channel, Class<T> type) {
    return deserialize(channel.getBuffer(), type);
  }

  @Override
  public Object deserialize(byte[] bytes, Iterable<MemoryBuffer> outOfBandBuffers) {
    return deserialize(MemoryUtils.wrap(bytes), outOfBandBuffers);
  }

  @Override
  public Object deserialize(long address, int size) {
    return deserialize(MemoryUtils.buffer(address, size), (Iterable<MemoryBuffer>) null);
  }

  @Override
  public Object deserialize(MemoryBuffer buffer) {
    return deserialize(buffer, (Iterable<MemoryBuffer>) null);
  }

  /**
   * Deserialize <code>obj</code> from a <code>buffer</code> and <code>outOfBandBuffers</code>.
   *
   * @param buffer serialized data. If the provided buffer start address is aligned with 4 bytes,
   *     the bulk read will be more efficient.
   * @param outOfBandBuffers If <code>buffers</code> is not None, it should be an iterable of
   *     buffer-enabled objects that is consumed each time the pickle stream references an
   *     out-of-band {@link BufferObject}. Such buffers have been given in order to the
   *     `bufferCallback` of a Fory object. If <code>outOfBandBuffers</code> is null (the default),
   *     then the buffers are taken from the serialized stream, assuming they are serialized there.
   *     It is an error for <code>outOfBandBuffers</code> to be null if the serialized stream was
   *     produced with a non-null `bufferCallback`.
   */
  @Override
  public Object deserialize(MemoryBuffer buffer, Iterable<MemoryBuffer> outOfBandBuffers) {
    ensureRegistrationFinished();
    byte bitmap = buffer.readByte();
    if ((bitmap & isNilFlag) == isNilFlag) {
      return null;
    }
    checkXlangBitmap(bitmap);
    boolean peerOutOfBandEnabled = (bitmap & isOutOfBandFlag) == isOutOfBandFlag;
    if (peerOutOfBandEnabled) {
      Preconditions.checkNotNull(
          outOfBandBuffers,
          "outOfBandBuffers shouldn't be null when the serialized stream is "
              + "produced with bufferCallback not null.");
    } else {
      Preconditions.checkArgument(
          outOfBandBuffers == null,
          "outOfBandBuffers should be null when the serialized stream is "
              + "produced with bufferCallback null.");
    }
    try {
      return readContext.run(
          buffer,
          peerOutOfBandEnabled ? outOfBandBuffers : null,
          peerOutOfBandEnabled,
          () -> {
            try {
              jitContext.lock();
              if (readContext.getDepth() > 0) {
                throwDepthDeserializationException();
              }
              return readContext.readRef();
            } finally {
              jitContext.unlock();
            }
          });
    } catch (Throwable t) {
      throw ExceptionUtils.handleReadFailed(this, t);
    }
  }

  @Override
  public Object deserialize(ForyInputStream inputStream) {
    return deserialize(inputStream, (Iterable<MemoryBuffer>) null);
  }

  @Override
  public Object deserialize(ForyInputStream inputStream, Iterable<MemoryBuffer> outOfBandBuffers) {
    try {
      MemoryBuffer buf = inputStream.getBuffer();
      return deserialize(buf, outOfBandBuffers);
    } finally {
      inputStream.shrinkBuffer();
    }
  }

  @Override
  public Object deserialize(ForyReadableChannel channel) {
    return deserialize(channel, (Iterable<MemoryBuffer>) null);
  }

  @Override
  public Object deserialize(ForyReadableChannel channel, Iterable<MemoryBuffer> outOfBandBuffers) {
    MemoryBuffer buf = channel.getBuffer();
    return deserialize(buf, outOfBandBuffers);
  }

  @SuppressWarnings("unchecked")
  private <T> T deserializeByType(MemoryBuffer buffer, Class<T> type) {
    generics.pushGenericType(typeResolver.buildGenericType(type));
    try {
      RefResolver refResolver = this.refResolver;
      int nextReadRefId = refResolver.tryPreserveRefId(buffer);
      if (nextReadRefId < NOT_NULL_VALUE_FLAG) {
        return (T) refResolver.getReadObject();
      }
      TypeInfo typeInfo = typeResolver.readTypeInfo(buffer, type);
      Object value = readNonRef(buffer, typeInfo);
      refResolver.setReadObject(nextReadRefId, value);
      return (T) value;
    } finally {
      generics.popGenericType();
    }
  }

  /** Deserialize nullable referencable object from <code>buffer</code>. */
  public Object readRef(MemoryBuffer buffer) {
    return readContext.readRef();
  }

  public Object readRef(MemoryBuffer buffer, TypeInfo typeInfo) {
    return readContext.readRef(typeInfo);
  }

  public Object readRef(MemoryBuffer buffer, TypeInfoHolder classInfoHolder) {
    return readContext.readRef(classInfoHolder);
  }

  @SuppressWarnings("unchecked")
  public <T> T readRef(MemoryBuffer buffer, Serializer<T> serializer) {
    return readContext.readRef(serializer);
  }

  /** Deserialize not-null and non-reference object from <code>buffer</code>. */
  public Object readNonRef(MemoryBuffer buffer) {
    return readContext.readNonRef();
  }

  public Object readNonRef(MemoryBuffer buffer, TypeInfoHolder classInfoHolder) {
    return readContext.readNonRef(classInfoHolder);
  }

  public Object readNonRef(MemoryBuffer buffer, TypeInfo typeInfo) {
    return readContext.readNonRef(typeInfo);
  }

  public Object readNonRef(MemoryBuffer buffer, Serializer<?> serializer) {
    return readContext.readNonRef(serializer);
  }

  /** Read object class and data without tracking ref. */
  public Object readNullable(MemoryBuffer buffer) {
    return readContext.readNullable();
  }

  public Object readNullable(MemoryBuffer buffer, Serializer serializer) {
    return readContext.readNullable(serializer);
  }

  public Object readNullable(MemoryBuffer buffer, TypeInfoHolder classInfoHolder) {
    return readContext.readNullable(classInfoHolder);
  }

  /** Class should be read already. */
  public Object readData(MemoryBuffer buffer, TypeInfo typeInfo) {
    return readContext.readData(typeInfo);
  }

  private Object readDataInternal(MemoryBuffer buffer, TypeInfo typeInfo) {
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
        } else {
          return buffer.readInt32();
        }
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
        Object stringLike = typeInfo.getSerializer().read(readContext);
        readContext.decDepth();
        return stringLike;
      default:
        incReadDepth();
        Object read = typeInfo.getSerializer().read(readContext);
        readContext.decDepth();
        return read;
    }
  }

  private void checkXlangBitmap(byte bitmap) {
    boolean payloadCrossLanguage = (bitmap & isCrossLanguageFlag) == isCrossLanguageFlag;
    Preconditions.checkArgument(
        payloadCrossLanguage == crossLanguage,
        "Serialized payload xlang flag %s does not match this Fory mode %s",
        payloadCrossLanguage,
        crossLanguage);
  }

  @Override
  public <T> T copy(T obj) {
    ensureRegistrationFinished();
    try {
      return copyContext.run(() -> copyContext.copyObject(obj));
    } catch (Throwable e) {
      throw processCopyError(e);
    } finally {
      resetCopy();
    }
  }

  /**
   * Copy object. This method provides a fast copy of common types.
   *
   * @param obj object to copy
   * @return copied object
   */
  public <T> T copyObject(T obj) {
    return copyContext.copyObject(obj);
  }

  public <T> T copyObject(T obj, int classId) {
    return copyContext.copyObject(obj, classId);
  }

  public <T> T copyObject(T obj, Serializer<T> serializer) {
    return copyContext.copyObject(obj, serializer);
  }

  /**
   * Track ref for copy.
   *
   * <p>Call this method immediately after composited object such as object
   * array/map/collection/bean is created so that circular reference can be copy correctly.
   *
   * @param o1 object before copying
   * @param o2 the copied object
   */
  public <T> void reference(T o1, T o2) {
    copyContext.reference(o1, o2);
  }

  @SuppressWarnings("unchecked")
  public <T> T getCopyObject(T originObj) {
    return copyContext.getCopyObject(originObj);
  }

  private void serializeToStream(OutputStream outputStream, Consumer<MemoryBuffer> function) {
    MemoryBuffer buf = getBuffer();
    if (outputStream.getClass() == ByteArrayOutputStream.class) {
      byte[] oldBytes = buf.getHeapMemory(); // Note: This should not be null.
      assert oldBytes != null;
      MemoryUtils.wrap((ByteArrayOutputStream) outputStream, buf);
      function.accept(buf);
      MemoryUtils.wrap(buf, (ByteArrayOutputStream) outputStream);
      buf.pointTo(oldBytes, 0, oldBytes.length);
    } else {
      buf.writerIndex(0);
      function.accept(buf);
      try {
        byte[] bytes = buf.getHeapMemory();
        if (bytes != null) {
          outputStream.write(bytes, 0, buf.writerIndex());
        } else {
          outputStream.write(buf.getBytes(0, buf.writerIndex()));
        }
        outputStream.flush();
      } catch (IOException e) {
        throw new SerializationException(e);
      } finally {
        resetBuffer();
      }
    }
  }

  public void reset() {
    resetWrite();
    resetRead();
    resetCopy();
  }

  public void resetWrite() {
    writeContext.reset();
  }

  public void resetRead() {
    readContext.reset();
  }

  public void resetCopy() {
    copyContext.reset();
  }

  private void throwDepthSerializationException() {
    String method = "Fory#writeXXX";
    throw new SerializationException(
        String.format(
            "Nested call Fory.serializeXXX is not allowed when serializing, Please use %s instead",
            method));
  }

  private void throwDepthDeserializationException() {
    String method = "Fory#readXXX";
    throw new DeserializationException(
        String.format(
            "Nested call Fory.deserializeXXX is not allowed when deserializing, Please use %s instead",
            method));
  }

  @Override
  public void ensureSerializersCompiled() {
    getTypeResolver().ensureSerializersCompiled();
  }

  public JITContext getJITContext() {
    return jitContext;
  }

  public BufferCallback getBufferCallback() {
    return writeContext.getBufferCallback();
  }

  public boolean isPeerOutOfBandEnabled() {
    return readContext.isPeerOutOfBandEnabled();
  }

  public RefResolver getRefResolver() {
    return refResolver;
  }

  /**
   * Don't use this API for type resolving and dispatch, methods on returned resolver has
   * polymorphic invoke cost.
   */
  @Internal
  // CHECKSTYLE.OFF:MethodName
  public TypeResolver getTypeResolver() {
    // CHECKSTYLE.ON:MethodName
    return typeResolver;
  }

  public MetaStringResolver getMetaStringResolver() {
    return metaStringResolver;
  }

  public SerializationContext getSerializationContext() {
    return serializationContext;
  }

  public Generics getGenerics() {
    return generics;
  }

  public int getDepth() {
    try {
      return ReadContext.current().getDepth();
    } catch (IllegalStateException ignored) {
      try {
        return WriteContext.current().getDepth();
      } catch (IllegalStateException ignoredWrite) {
        return CopyContext.current().getDepth();
      }
    }
  }

  public void setDepth(int depth) {
    try {
      ReadContext.current().setDepth(depth);
    } catch (IllegalStateException ignored) {
      WriteContext.current().setDepth(depth);
    }
  }

  public void incDepth(int diff) {
    try {
      ReadContext.current().incDepth(diff);
    } catch (IllegalStateException ignored) {
      WriteContext.current().incDepth(diff);
    }
  }

  public void incDepth() {
    WriteContext.current().incDepth();
  }

  public void decDepth() {
    try {
      ReadContext.current().decDepth();
    } catch (IllegalStateException ignored) {
      WriteContext.current().decDepth();
    }
  }

  public void incReadDepth() {
    ReadContext.current().incReadDepth();
  }

  public void incCopyDepth(int diff) {
    copyContext.incDepth(diff);
  }

  // Invoked by jit
  public StringSerializer getStringSerializer() {
    return stringSerializer;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  @Internal
  public SharedRegistry getSharedRegistry() {
    return sharedRegistry;
  }

  @Internal
  public boolean isRegistrationFinished() {
    return registrationFinished;
  }

  @Internal
  public void setRegistrationFinished() {
    registrationFinished = true;
  }

  public boolean isCrossLanguage() {
    return crossLanguage;
  }

  public boolean isCompatible() {
    return config.getCompatibleMode() == CompatibleMode.COMPATIBLE;
  }

  public boolean isShareMeta() {
    return shareMeta;
  }

  public boolean trackingRef() {
    return refTracking;
  }

  public boolean copyTrackingRef() {
    return copyRefTracking;
  }

  public boolean isStringRefIgnored() {
    return config.isStringRefIgnored();
  }

  public boolean checkClassVersion() {
    return config.checkClassVersion();
  }

  public CompatibleMode getCompatibleMode() {
    return config.getCompatibleMode();
  }

  public Config getConfig() {
    return config;
  }

  public Class<? extends Serializer> getDefaultJDKStreamSerializerType() {
    return config.getDefaultJDKStreamSerializerType();
  }

  public boolean compressString() {
    return config.compressString();
  }

  public boolean compressInt() {
    return compressInt;
  }

  public LongEncoding longEncoding() {
    return longEncoding;
  }

  public boolean compressLong() {
    return config.compressLong();
  }

  public MetaCompressor getMetaCompressor() {
    return config.getMetaCompressor();
  }

  public static ForyBuilder builder() {
    return new ForyBuilder();
  }
}
