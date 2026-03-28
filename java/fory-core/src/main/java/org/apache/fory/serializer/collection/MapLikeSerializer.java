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

package org.apache.fory.serializer.collection;

import static org.apache.fory.serializer.collection.MapFlags.KEY_DECL_TYPE;
import static org.apache.fory.serializer.collection.MapFlags.KEY_HAS_NULL;
import static org.apache.fory.serializer.collection.MapFlags.KV_NULL;
import static org.apache.fory.serializer.collection.MapFlags.NULL_KEY_VALUE_DECL_TYPE;
import static org.apache.fory.serializer.collection.MapFlags.NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF;
import static org.apache.fory.serializer.collection.MapFlags.NULL_VALUE_KEY_DECL_TYPE;
import static org.apache.fory.serializer.collection.MapFlags.NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF;
import static org.apache.fory.serializer.collection.MapFlags.TRACKING_KEY_REF;
import static org.apache.fory.serializer.collection.MapFlags.TRACKING_VALUE_REF;
import static org.apache.fory.serializer.collection.MapFlags.VALUE_DECL_TYPE;
import static org.apache.fory.serializer.collection.MapFlags.VALUE_HAS_NULL;
import static org.apache.fory.type.TypeUtils.MAP_TYPE;

import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;

import com.google.common.collect.ImmutableMap.Builder;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.fory.Fory;
import org.apache.fory.annotation.CodegenInvoke;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.config.Config;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeInfoHolder;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.type.GenericType;
import org.apache.fory.type.Generics;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.Preconditions;

/** Serializer for all map-like objects. */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class MapLikeSerializer<T> extends Serializer<T> {
  public static final int MAX_CHUNK_SIZE = 255;

  static final class MapTypeCache {
    final TypeInfoHolder keyTypeInfoWriteCache;
    final TypeInfoHolder keyTypeInfoReadCache;
    final TypeInfoHolder valueTypeInfoWriteCache;
    final TypeInfoHolder valueTypeInfoReadCache;
    GenericType partialGenericKVTypeKey0;
    GenericType partialGenericKVTypeValue0;
    GenericType partialGenericKVTypeKey1;
    GenericType partialGenericKVTypeValue1;

    private MapTypeCache(TypeResolver typeResolver) {
      keyTypeInfoWriteCache = typeResolver.nilTypeInfoHolder();
      keyTypeInfoReadCache = typeResolver.nilTypeInfoHolder();
      valueTypeInfoWriteCache = typeResolver.nilTypeInfoHolder();
      valueTypeInfoReadCache = typeResolver.nilTypeInfoHolder();
    }
  }

  protected MethodHandle constructor;
  protected final Config config;
  protected final boolean supportCodegenHook;
  private final GenericType objType;
  // For subclass whose kv type are instantiated already, such as
  // `Subclass implements Map<String, Long>`. If declared `Map` doesn't specify
  // instantiated kv type, then the serialization will need to write those kv
  // types. Although we can extract this generics when creating the serializer,
  // we can't do it when jit `Serializer` for some class which contains one of such map
  // field. So we will write those extra kv classes to keep protocol consistency between
  // interpreter and jit mode although it seems unnecessary.
  // With kv header in future, we can write this kv classes only once, the cost won't be too much.
  private int numElements;
  protected final TypeResolver typeResolver;
  private final boolean trackRef;
  private MapTypeCache mapTypeCache;

  public MapLikeSerializer(TypeResolver typeResolver, Class<T> cls) {
    this(typeResolver, cls, !ReflectionUtils.isDynamicGeneratedCLass(cls));
  }

  public MapLikeSerializer(TypeResolver typeResolver, Class<T> cls, boolean supportCodegenHook) {
    this(typeResolver, cls, supportCodegenHook, false);
  }

  public MapLikeSerializer(
      TypeResolver typeResolver, Class<T> cls, boolean supportCodegenHook, boolean immutable) {
    super(typeResolver, cls, immutable);
    this.config = typeResolver.getConfig();
    this.typeResolver = typeResolver;
    trackRef = typeResolver.getConfig().trackingRef();
    this.supportCodegenHook = supportCodegenHook;
    objType = typeResolver.buildGenericType(Object.class);
  }

  final MapTypeCache mapTypeCache() {
    MapTypeCache state = mapTypeCache;
    if (state == null) {
      state = new MapTypeCache(typeResolver);
      mapTypeCache = state;
    }
    return state;
  }

  @Override
  public void write(WriteContext writeContext, T value) {
    MemoryBuffer buffer = writeContext.getBuffer();
    Map map = onMapWrite(buffer, value);
    if (map.isEmpty()) {
      return;
    }
    TypeResolver classResolver = typeResolver;
    Iterator<Entry<Object, Object>> iterator = map.entrySet().iterator();
    Entry<Object, Object> entry = iterator.next();
    Generics generics = typeResolver.getGenerics();
    while (entry != null) {
      GenericType genericType = generics.nextGenericType();
      if (genericType == null) {
        entry = writeJavaNullChunk(buffer, entry, iterator, null, null);
        if (entry != null) {
          entry = writeJavaChunk(classResolver, buffer, entry, iterator, null, null);
        }
      } else {
        if (genericType.getTypeParametersCount() < 2) {
          genericType = getKVGenericType(genericType);
        }
        GenericType keyGenericType = genericType.getTypeParameter0();
        GenericType valueGenericType = genericType.getTypeParameter1();
        entry =
            writeJavaNullChunkGeneric(buffer, entry, iterator, keyGenericType, valueGenericType);
        if (entry != null) {
          entry =
              writeJavaChunkGeneric(classResolver, generics, genericType, buffer, entry, iterator);
        }
      }
    }
    onMapWriteFinish(map);
  }

  public final Entry writeJavaNullChunk(
      MemoryBuffer buffer,
      Entry entry,
      Iterator<Entry<Object, Object>> iterator,
      Serializer keySerializer,
      Serializer valueSerializer) {
    while (true) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key != null) {
        if (value != null) {
          return entry;
        }
        writeNullValueChunk(buffer, keySerializer, key);
      } else {
        writeNullKeyChunk(buffer, valueSerializer, value);
      }
      if (iterator.hasNext()) {
        entry = iterator.next();
      } else {
        return null;
      }
    }
  }

  private void writeNullValueChunk(MemoryBuffer buffer, Serializer keySerializer, Object key) {
    // noinspection Duplicates
      if (keySerializer != null) {
        if (keySerializer.needToWriteRef()) {
          buffer.writeByte(NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF);
          keySerializer.write(typeResolver.getWriteContext(), RefMode.TRACKING, key);
        } else {
          buffer.writeByte(NULL_VALUE_KEY_DECL_TYPE);
          keySerializer.write(typeResolver.getWriteContext(), key);
        }
    } else {
      buffer.writeByte(VALUE_HAS_NULL | TRACKING_KEY_REF);
      typeResolver.getWriteContext().writeRef(key, mapTypeCache().keyTypeInfoWriteCache);
    }
  }

  /**
   * Write chunk of size 1, the key is null. Since we can have at most one key whose value is null,
   * this method is not in critical path, make it as a separate method to let caller eligible for
   * jit inline.
   */
  private void writeNullKeyChunk(MemoryBuffer buffer, Serializer valueSerializer, Object value) {
    if (value != null) {
      // noinspection Duplicates
      if (valueSerializer != null) {
        if (valueSerializer.needToWriteRef()) {
          buffer.writeByte(NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF);
          valueSerializer.write(typeResolver.getWriteContext(), RefMode.TRACKING, value);
        } else {
          buffer.writeByte(NULL_KEY_VALUE_DECL_TYPE);
          valueSerializer.write(typeResolver.getWriteContext(), value);
        }
      } else {
        buffer.writeByte(KEY_HAS_NULL | TRACKING_VALUE_REF);
        typeResolver.getWriteContext().writeRef(value, mapTypeCache().valueTypeInfoWriteCache);
      }
    } else {
      buffer.writeByte(KV_NULL);
    }
  }

  @CodegenInvoke
  public final Entry writeNullChunkKVFinalNoRef(
      MemoryBuffer buffer,
      Entry entry,
      Iterator<Entry<Object, Object>> iterator,
      Serializer keySerializer,
      Serializer valueSerializer) {
    while (true) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key != null) {
        if (value != null) {
          return entry;
        }
        buffer.writeByte(NULL_VALUE_KEY_DECL_TYPE);
        keySerializer.write(typeResolver.getWriteContext(), key);
      } else {
        writeNullKeyChunk(buffer, valueSerializer, value);
      }
      if (iterator.hasNext()) {
        entry = iterator.next();
      } else {
        return null;
      }
    }
  }

  public final Entry writeJavaNullChunkGeneric(
      MemoryBuffer buffer,
      Entry entry,
      Iterator<Entry<Object, Object>> iterator,
      GenericType keyType,
      GenericType valueType) {
    while (true) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key != null) {
        if (value != null) {
          return entry;
        }
        writeKeyForNullValueChunkGeneric(buffer, key, keyType);
      } else {
        writeValueForNullKeyChunkGeneric(buffer, value, valueType);
      }
      if (iterator.hasNext()) {
        entry = iterator.next();
      } else {
        return null;
      }
    }
  }

  private void writeKeyForNullValueChunkGeneric(
      MemoryBuffer buffer, Object key, GenericType keyType) {
    boolean trackingRef = keyType.trackingRef(typeResolver);
    if (!keyType.isMonomorphic()) {
      if (trackingRef) {
        buffer.writeByte(VALUE_HAS_NULL | TRACKING_KEY_REF);
        typeResolver.getWriteContext().writeRef(key, mapTypeCache().keyTypeInfoWriteCache);
      } else {
        buffer.writeByte(VALUE_HAS_NULL);
        typeResolver.getWriteContext().writeNonRef(key);
      }
      return;
    }
    Serializer serializer = keyType.getSerializer(typeResolver);
    Generics generics = typeResolver.getGenerics();
    WriteContext writeContext = typeResolver.getWriteContext();
    if (keyType.hasGenericParameters()) {
      generics.pushGenericType(keyType);
      writeContext.incDepth();
    }
    if (trackingRef && serializer.needToWriteRef()) {
      buffer.writeByte(NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF);
      serializer.write(writeContext, RefMode.TRACKING, key);
    } else {
      buffer.writeByte(NULL_VALUE_KEY_DECL_TYPE);
      serializer.write(writeContext, key);
    }
    if (keyType.hasGenericParameters()) {
      writeContext.decDepth();
      generics.popGenericType();
    }
  }

  private void writeValueForNullKeyChunkGeneric(
      MemoryBuffer buffer, Object value, GenericType valueType) {
    boolean trackingRef = valueType.trackingRef(typeResolver);
    if (!valueType.isMonomorphic()) {
      if (trackingRef) {
        buffer.writeByte(KEY_HAS_NULL | TRACKING_VALUE_REF);
        typeResolver.getWriteContext().writeRef(value, mapTypeCache().valueTypeInfoWriteCache);
      } else {
        buffer.writeByte(KEY_HAS_NULL);
        typeResolver.getWriteContext().writeNonRef(value);
      }
      return;
    }
    Serializer serializer = valueType.getSerializer(typeResolver);
    Generics generics = typeResolver.getGenerics();
    WriteContext writeContext = typeResolver.getWriteContext();
    if (valueType.hasGenericParameters()) {
      generics.pushGenericType(valueType);
      writeContext.incDepth();
    }
    if (trackingRef && serializer.needToWriteRef()) {
      buffer.writeByte(NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF);
      serializer.write(writeContext, RefMode.TRACKING, value);
    } else {
      buffer.writeByte(NULL_KEY_VALUE_DECL_TYPE);
      serializer.write(writeContext, value);
    }
    if (valueType.hasGenericParameters()) {
      writeContext.decDepth();
      generics.popGenericType();
    }
  }

  // Make byte code of this method smaller than 325 for better jit inline
  private Entry writeJavaChunk(
      TypeResolver classResolver,
      MemoryBuffer buffer,
      Entry<Object, Object> entry,
      Iterator<Entry<Object, Object>> iterator,
      Serializer keySerializer,
      Serializer valueSerializer) {
    WriteContext writeContext = typeResolver.getWriteContext();
    Object key = entry.getKey();
    Object value = entry.getValue();
    Class keyType = key.getClass();
    Class valueType = value.getClass();
    // place holder for chunk header and size.
    buffer.writeInt16((short) -1);
    int chunkSizeOffset = buffer.writerIndex() - 1;
    int chunkHeader = 0;
    if (keySerializer != null) {
      chunkHeader |= KEY_DECL_TYPE;
    } else {
      keySerializer = writeKeyTypeInfo(classResolver, keyType, buffer);
    }
    if (valueSerializer != null) {
      chunkHeader |= VALUE_DECL_TYPE;
    } else {
      valueSerializer = writeValueTypeInfo(classResolver, valueType, buffer);
    }
    // noinspection Duplicates
    boolean keyWriteRef = keySerializer.needToWriteRef();
    boolean valueWriteRef = valueSerializer.needToWriteRef();
    if (keyWriteRef) {
      chunkHeader |= TRACKING_KEY_REF;
    }
    if (valueWriteRef) {
      chunkHeader |= TRACKING_VALUE_REF;
    }
    buffer.putByte(chunkSizeOffset - 1, (byte) chunkHeader);
    // Use int to make chunk size representable for 0~255 instead of 0~127.
    int chunkSize = 0;
    while (true) {
      if (key == null
          || value == null
          || (key.getClass() != keyType)
          || (value.getClass() != valueType)) {
        break;
      }
      if (!keyWriteRef || !writeContext.writeRefOrNull(key)) {
        keySerializer.write(writeContext, key);
      }
      if (!valueWriteRef || !writeContext.writeRefOrNull(value)) {
        valueSerializer.write(writeContext, value);
      }
      // noinspection Duplicates
      ++chunkSize;
      if (iterator.hasNext()) {
        entry = iterator.next();
        key = entry.getKey();
        value = entry.getValue();
      } else {
        entry = null;
        break;
      }
      if (chunkSize == MAX_CHUNK_SIZE) {
        break;
      }
    }
    buffer.putByte(chunkSizeOffset, (byte) chunkSize);
    return entry;
  }

  private Serializer writeKeyTypeInfo(
      TypeResolver classResolver, Class keyType, MemoryBuffer buffer) {
    TypeInfo typeInfo = classResolver.getTypeInfo(keyType, mapTypeCache().keyTypeInfoWriteCache);
    classResolver.writeTypeInfo(buffer, typeInfo);
    return typeInfo.getSerializer();
  }

  private Serializer writeValueTypeInfo(
      TypeResolver classResolver, Class valueType, MemoryBuffer buffer) {
    TypeInfo typeInfo =
        classResolver.getTypeInfo(valueType, mapTypeCache().valueTypeInfoWriteCache);
    classResolver.writeTypeInfo(buffer, typeInfo);
    return typeInfo.getSerializer();
  }

  @CodegenInvoke
  public Entry writeJavaChunkGeneric(
      TypeResolver classResolver,
      Generics generics,
      GenericType genericType,
      MemoryBuffer buffer,
      Entry<Object, Object> entry,
      Iterator<Entry<Object, Object>> iterator) {
    WriteContext writeContext = typeResolver.getWriteContext();
    // type parameters count for `Map field` will be 0;
    // type parameters count for `SubMap<V> field` which SubMap is
    // `SubMap<V> implements Map<String, V>` will be 1;
    if (genericType.getTypeParametersCount() < 2) {
      genericType = getKVGenericType(genericType);
    }
    GenericType keyGenericType = genericType.getTypeParameter0();
    GenericType valueGenericType = genericType.getTypeParameter1();
    if (keyGenericType == objType && valueGenericType == objType) {
      return writeJavaChunk(classResolver, buffer, entry, iterator, null, null);
    }
    // Can't avoid push generics repeatedly in loop by stack depth, because push two
    // generic type changed generics stack top, which is depth index, update stack top
    // and depth will have some cost too.
    // Stack depth to avoid push generics repeatedly in loop.
    // Note push two generic type changed generics stack top, which is depth index,
    // stack top should be updated when using for serialization k/v.
    boolean keyGenericTypeFinal = keyGenericType.isMonomorphic();
    boolean valueGenericTypeFinal = valueGenericType.isMonomorphic();
    Object key = entry.getKey();
    Object value = entry.getValue();
    Class keyType = key.getClass();
    Class valueType = value.getClass();
    Serializer keySerializer, valueSerializer;
    // place holder for chunk header and size.
    buffer.writeInt16((short) -1);
    int chunkSizeOffset = buffer.writerIndex() - 1;
    int chunkHeader = 0;
    // noinspection Duplicates
    if (keyGenericTypeFinal) {
      chunkHeader |= KEY_DECL_TYPE;
      keySerializer = keyGenericType.getSerializer(classResolver);
    } else {
      keySerializer = writeKeyTypeInfo(classResolver, keyType, buffer);
    }
    if (valueGenericTypeFinal) {
      chunkHeader |= VALUE_DECL_TYPE;
      valueSerializer = valueGenericType.getSerializer(classResolver);
    } else {
      valueSerializer = writeValueTypeInfo(classResolver, valueType, buffer);
    }
    boolean trackingKeyRef = keyGenericType.trackingRef(typeResolver);
    boolean trackingValueRef = valueGenericType.trackingRef(typeResolver);
    boolean keyWriteRef = trackingKeyRef && keySerializer.needToWriteRef();
    if (keyWriteRef) {
      chunkHeader |= TRACKING_KEY_REF;
    }
    boolean valueWriteRef = trackingValueRef && valueSerializer.needToWriteRef();
    if (valueWriteRef) {
      chunkHeader |= TRACKING_VALUE_REF;
    }
    buffer.putByte(chunkSizeOffset - 1, (byte) chunkHeader);
    // Use int to make chunk size representable for 0~255 instead of 0~127.
    int chunkSize = 0;
    while (true) {
      if (key == null
          || value == null
          || (key.getClass() != keyType)
          || (value.getClass() != valueType)) {
        break;
      }
      generics.pushGenericType(keyGenericType);
      if (!keyWriteRef || !writeContext.writeRefOrNull(key)) {
        writeContext.incDepth();
        keySerializer.write(writeContext, key);
        writeContext.decDepth();
      }
      generics.popGenericType();
      generics.pushGenericType(valueGenericType);
      if (!valueWriteRef || !writeContext.writeRefOrNull(value)) {
        writeContext.incDepth();
        valueSerializer.write(writeContext, value);
        writeContext.decDepth();
      }
      generics.popGenericType();
      ++chunkSize;
      // noinspection Duplicates
      if (iterator.hasNext()) {
        entry = iterator.next();
        key = entry.getKey();
        value = entry.getValue();
      } else {
        entry = null;
        break;
      }
      if (chunkSize == MAX_CHUNK_SIZE) {
        break;
      }
    }
    buffer.putByte(chunkSizeOffset, (byte) chunkSize);
    return entry;
  }

  private GenericType getKVGenericType(GenericType genericType) {
    GenericType mapGenericType = getCachedMapGenericType(genericType);
    if (mapGenericType == null) {
      TypeRef<?> typeRef = genericType.getTypeRef();
      if (!MAP_TYPE.isSupertypeOf(typeRef)) {
        mapGenericType = GenericType.build(TypeUtils.mapOf(Object.class, Object.class));
      } else {
        Tuple2<TypeRef<?>, TypeRef<?>> mapKeyValueType = TypeUtils.getMapKeyValueType(typeRef);
        mapGenericType = GenericType.build(TypeUtils.mapOf(mapKeyValueType.f0, mapKeyValueType.f1));
      }
      cacheMapGenericType(genericType, mapGenericType);
    }
    return mapGenericType;
  }

  private GenericType getCachedMapGenericType(GenericType genericType) {
    MapTypeCache state = mapTypeCache;
    if (state == null) {
      return null;
    }
    if (genericType == state.partialGenericKVTypeKey0) {
      return state.partialGenericKVTypeValue0;
    }
    if (genericType == state.partialGenericKVTypeKey1) {
      return state.partialGenericKVTypeValue1;
    }
    return null;
  }

  private void cacheMapGenericType(GenericType genericType, GenericType mapGenericType) {
    MapTypeCache state = mapTypeCache();
    state.partialGenericKVTypeKey1 = state.partialGenericKVTypeKey0;
    state.partialGenericKVTypeValue1 = state.partialGenericKVTypeValue0;
    state.partialGenericKVTypeKey0 = genericType;
    state.partialGenericKVTypeValue0 = mapGenericType;
  }

  protected <K, V> void copyEntry(
      CopyContext copyContext, Map<K, V> originMap, Map<K, V> newMap) {
    TypeResolver classResolver = typeResolver;
    MapTypeCache state = mapTypeCache();
    for (Map.Entry<K, V> entry : originMap.entrySet()) {
      K key = entry.getKey();
      if (key != null) {
        TypeInfo typeInfo = classResolver.getTypeInfo(key.getClass(), state.keyTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          key = copyContext.copyObject(key, typeInfo.getTypeId());
        }
      }
      V value = entry.getValue();
      if (value != null) {
        TypeInfo typeInfo =
            classResolver.getTypeInfo(value.getClass(), state.valueTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          value = copyContext.copyObject(value, typeInfo.getTypeId());
        }
      }
      newMap.put(key, value);
    }
  }

  protected <K, V> void copyEntry(
      CopyContext copyContext, Map<K, V> originMap, Builder<K, V> builder) {
    TypeResolver classResolver = typeResolver;
    MapTypeCache state = mapTypeCache();
    for (Entry<K, V> entry : originMap.entrySet()) {
      K key = entry.getKey();
      if (key != null) {
        TypeInfo typeInfo = classResolver.getTypeInfo(key.getClass(), state.keyTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          key = copyContext.copyObject(key, typeInfo.getTypeId());
        }
      }
      V value = entry.getValue();
      if (value != null) {
        TypeInfo typeInfo =
            classResolver.getTypeInfo(value.getClass(), state.valueTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          value = copyContext.copyObject(value, typeInfo.getTypeId());
        }
      }
      builder.put(key, value);
    }
  }

  protected <K, V> void copyEntry(
      CopyContext copyContext, Map<K, V> originMap, Object[] elements) {
    TypeResolver classResolver = typeResolver;
    MapTypeCache state = mapTypeCache();
    int index = 0;
    for (Entry<K, V> entry : originMap.entrySet()) {
      K key = entry.getKey();
      if (key != null) {
        TypeInfo typeInfo = classResolver.getTypeInfo(key.getClass(), state.keyTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          key = copyContext.copyObject(key, typeInfo.getTypeId());
        }
      }
      V value = entry.getValue();
      if (value != null) {
        TypeInfo typeInfo =
            classResolver.getTypeInfo(value.getClass(), state.valueTypeInfoWriteCache);
        if (!typeInfo.getSerializer().isImmutable()) {
          value = copyContext.copyObject(value, typeInfo.getTypeId());
        }
      }
      elements[index++] = key;
      elements[index++] = value;
    }
  }

  @Override
  public T read(ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
    Map map = newMap(buffer);
    int size = getAndClearNumElements();
    readElements(buffer, size, map);
    return onMapRead(map);
  }

  public void readElements(MemoryBuffer buffer, int size, Map map) {
    int chunkHeader = 0;
    if (size != 0) {
      chunkHeader = buffer.readUnsignedByte();
    }
    Generics generics = typeResolver.getGenerics();
    while (size > 0) {
      long sizeAndHeader = readJavaNullChunk(buffer, map, chunkHeader, size, null, null);
      chunkHeader = (int) (sizeAndHeader & 0xff);
      size = (int) (sizeAndHeader >>> 8);
      if (size == 0) {
        break;
      }
      GenericType genericType = generics.nextGenericType();
      if (genericType == null) {
        sizeAndHeader = readJavaChunk(buffer, map, size, chunkHeader, null, null);
      } else {
        sizeAndHeader = readJavaChunkGeneric(generics, genericType, buffer, map, size, chunkHeader);
      }
      chunkHeader = (int) (sizeAndHeader & 0xff);
      size = (int) (sizeAndHeader >>> 8);
    }
  }

  public long readJavaNullChunk(
      MemoryBuffer buffer,
      Map map,
      int chunkHeader,
      long size,
      Serializer keySerializer,
      Serializer valueSerializer) {
    ReadContext readContext = typeResolver.getReadContext();
    MapTypeCache state = mapTypeCache();
    while (true) {
      boolean keyHasNull = (chunkHeader & KEY_HAS_NULL) != 0;
      boolean valueHasNull = (chunkHeader & VALUE_HAS_NULL) != 0;
      if (!keyHasNull) {
        if (!valueHasNull) {
          return (size << 8) | chunkHeader;
        } else {
          boolean trackKeyRef = (chunkHeader & TRACKING_KEY_REF) != 0;
          Object key;
          if ((chunkHeader & KEY_DECL_TYPE) != 0) {
              if (keySerializer == null) {
                key = readNonEmptyValueFromNullChunk(buffer, trackKeyRef, true);
              } else {
              readContext.incReadDepth();
              if (trackKeyRef) {
                key = keySerializer.read(readContext, RefMode.TRACKING);
              } else {
                key = keySerializer.read(readContext, RefMode.NONE);
              }
              readContext.decDepth();
            }
          } else {
            if (trackKeyRef) {
              key = readContext.readRef(state.keyTypeInfoReadCache);
            } else {
              key = readContext.readNonRef(state.keyTypeInfoReadCache);
            }
          }
          map.put(key, null);
        }
      } else {
        readNullKeyChunk(buffer, map, chunkHeader, valueSerializer, valueHasNull);
      }
      if (--size == 0) {
        return 0;
      } else {
        chunkHeader = buffer.readUnsignedByte();
      }
    }
  }

  /**
   * Read chunk of size 1, the key is null. Since we can have at most one key whose value is null,
   * this method is not in critical path, make it as a separate method to let caller eligible for
   * jit inline.
   */
  private void readNullKeyChunk(
      MemoryBuffer buffer,
      Map map,
      int chunkHeader,
      Serializer valueSerializer,
      boolean valueHasNull) {
    ReadContext readContext = typeResolver.getReadContext();
    MapTypeCache state = mapTypeCache();
    if (!valueHasNull) {
      Object value;
      boolean trackValueRef = (chunkHeader & TRACKING_VALUE_REF) != 0;
      if ((chunkHeader & VALUE_DECL_TYPE) != 0) {
        if (valueSerializer == null) {
          value = readNonEmptyValueFromNullChunk(buffer, trackValueRef, false);
        } else {
          readContext.incReadDepth();
          if (trackValueRef) {
            value = valueSerializer.read(readContext, RefMode.TRACKING);
          } else {
            value = valueSerializer.read(readContext, RefMode.NONE);
          }
          readContext.decDepth();
        }
      } else {
        if (trackValueRef) {
          value = readContext.readRef(state.valueTypeInfoReadCache);
        } else {
          value = readContext.readNonRef(state.valueTypeInfoReadCache);
        }
      }
      map.put(null, value);
    } else {
      map.put(null, null);
    }
  }

  private Object readNonEmptyValueFromNullChunk(
      MemoryBuffer buffer, boolean trackRef, boolean isKey) {
    ReadContext readContext = typeResolver.getReadContext();
    Generics generics = typeResolver.getGenerics();
    GenericType genericType = generics.nextGenericType();
    if (genericType.getTypeParametersCount() < 2) {
      genericType = getKVGenericType(genericType);
    }
    GenericType type = isKey ? genericType.getTypeParameter0() : genericType.getTypeParameter1();
    generics.pushGenericType(type);
    Serializer<?> serializer = type.getSerializer(typeResolver);
    Object v;
    readContext.incReadDepth();
    if (trackRef) {
      v = serializer.read(readContext, RefMode.TRACKING);
    } else {
      v = serializer.read(readContext, RefMode.NONE);
    }
    readContext.decDepth();
    generics.popGenericType();
    return v;
  }

  @CodegenInvoke
  public long readNullChunkKVFinalNoRef(
      MemoryBuffer buffer,
      Map map,
      int chunkHeader,
      long size,
      Serializer keySerializer,
      Serializer valueSerializer) {
    ReadContext readContext = typeResolver.getReadContext();
    while (true) {
      boolean keyHasNull = (chunkHeader & KEY_HAS_NULL) != 0;
      boolean valueHasNull = (chunkHeader & VALUE_HAS_NULL) != 0;
      if (!keyHasNull) {
        if (!valueHasNull) {
          return (size << 8) | chunkHeader;
        } else {
          readContext.incReadDepth();
          Object key = keySerializer.read(readContext, RefMode.NONE);
          map.put(key, null);
          readContext.decDepth();
        }
      } else {
        readNullKeyChunk(buffer, map, chunkHeader, valueSerializer, valueHasNull);
      }
      if (--size == 0) {
        return 0;
      } else {
        chunkHeader = buffer.readUnsignedByte();
      }
    }
  }

  @CodegenInvoke
  public long readJavaChunk(
      MemoryBuffer buffer,
      Map map,
      long size,
      int chunkHeader,
      Serializer keySerializer,
      Serializer valueSerializer) {
    ReadContext readContext = typeResolver.getReadContext();
    MapTypeCache state = mapTypeCache();
    // noinspection Duplicates
    boolean trackKeyRef = (chunkHeader & TRACKING_KEY_REF) != 0;
    boolean trackValueRef = (chunkHeader & TRACKING_VALUE_REF) != 0;
    if (trackKeyRef || trackValueRef) {
      Preconditions.checkState(config.trackingRef(), "Ref tracking is not enabled");
    }
    boolean keyIsDeclaredType = (chunkHeader & KEY_DECL_TYPE) != 0;
    boolean valueIsDeclaredType = (chunkHeader & VALUE_DECL_TYPE) != 0;
    int chunkSize = buffer.readUnsignedByte();
    if (!keyIsDeclaredType) {
      keySerializer = typeResolver.readTypeInfo(buffer, state.keyTypeInfoReadCache).getSerializer();
    }
    if (!valueIsDeclaredType) {
      valueSerializer =
          typeResolver.readTypeInfo(buffer, state.valueTypeInfoReadCache).getSerializer();
    }
    readContext.incReadDepth();
    for (int i = 0; i < chunkSize; i++) {
      Object key =
          trackKeyRef
              ? keySerializer.read(readContext, RefMode.TRACKING)
              : keySerializer.read(readContext, RefMode.NONE);
      Object value =
          trackValueRef
              ? valueSerializer.read(readContext, RefMode.TRACKING)
              : valueSerializer.read(readContext, RefMode.NONE);
      map.put(key, value);
      size--;
    }
    readContext.decDepth();
    return size > 0 ? (size << 8) | buffer.readUnsignedByte() : 0;
  }

  private long readJavaChunkGeneric(
      Generics generics,
      GenericType genericType,
      MemoryBuffer buffer,
      Map map,
      long size,
      int chunkHeader) {
    ReadContext readContext = typeResolver.getReadContext();
    MapTypeCache state = mapTypeCache();
    // type parameters count for `Map field` will be 0;
    // type parameters count for `SubMap<V> field` which SubMap is
    // `SubMap<V> implements Map<String, V>` will be 1;
    if (genericType.getTypeParametersCount() < 2) {
      genericType = getKVGenericType(genericType);
    }
    GenericType keyGenericType = genericType.getTypeParameter0();
    GenericType valueGenericType = genericType.getTypeParameter1();
    // noinspection Duplicates
    boolean trackKeyRef = (chunkHeader & TRACKING_KEY_REF) != 0;
    boolean trackValueRef = (chunkHeader & TRACKING_VALUE_REF) != 0;
    if (trackKeyRef || trackValueRef) {
      Preconditions.checkState(config.trackingRef(), "Ref tracking is not enabled");
    }
    boolean keyIsDeclaredType = (chunkHeader & KEY_DECL_TYPE) != 0;
    boolean valueIsDeclaredType = (chunkHeader & VALUE_DECL_TYPE) != 0;
    int chunkSize = buffer.readUnsignedByte();
    Serializer keySerializer, valueSerializer;
    if (!keyIsDeclaredType) {
      keySerializer = typeResolver.readTypeInfo(buffer, state.keyTypeInfoReadCache).getSerializer();
    } else {
      keySerializer = keyGenericType.getSerializer(typeResolver);
    }
    if (!valueIsDeclaredType) {
      valueSerializer =
          typeResolver.readTypeInfo(buffer, state.valueTypeInfoReadCache).getSerializer();
    } else {
      valueSerializer = valueGenericType.getSerializer(typeResolver);
    }
    for (int i = 0; i < chunkSize; i++) {
      generics.pushGenericType(keyGenericType);
      readContext.incReadDepth();
      Object key =
          trackKeyRef
              ? keySerializer.read(readContext, RefMode.TRACKING)
              : keySerializer.read(readContext, RefMode.NONE);
      readContext.decDepth();
      generics.popGenericType();
      generics.pushGenericType(valueGenericType);
      readContext.incReadDepth();
      Object value =
          trackValueRef
              ? valueSerializer.read(readContext, RefMode.TRACKING)
              : valueSerializer.read(readContext, RefMode.NONE);
      readContext.decDepth();
      generics.popGenericType();
      map.put(key, value);
      size--;
    }
    return size > 0 ? (size << 8) | buffer.readUnsignedByte() : 0;
  }

  /**
   * Hook for java serialization codegen, read/write key/value by entrySet.
   *
   * <p>For key/value type which is final, using codegen may get a big performance gain
   *
   * @return true if read/write key/value support calling entrySet method
   */
  public final boolean supportCodegenHook() {
    return supportCodegenHook;
  }

  /**
   * Write data except size and elements.
   *
   * <ol>
   *   In codegen, follows is call order:
   *   <li>write map class if not final
   *   <li>write map size
   *   <li>onCollectionWrite
   *   <li>write keys/values
   * </ol>
   */
  public abstract Map onMapWrite(MemoryBuffer buffer, T value);

  public void onMapWriteFinish(Map map) {}

  /**
   * Read data except size and elements, return empty map to be filled.
   *
   * <ol>
   *   In codegen, follows is call order:
   *   <li>read map class if not final
   *   <li>newMap: read and set map size, read map header and create map.
   *   <li>read keys/values
   * </ol>
   *
   * <p>Map must have default constructor to be invoked by fory, otherwise created object can't be
   * used to adding elements. For example:
   *
   * <pre>{@code new ArrayList<Integer> {add(1);}}</pre>
   *
   * <p>without default constructor, created list will have elementData as null, adding elements
   * will raise NPE.
   */
  public Map newMap(MemoryBuffer buffer) {
    numElements = buffer.readVarUint32Small7();
    if (constructor == null) {
      constructor = ReflectionUtils.getCtrHandle(type, true);
    }
    try {
      Map instance = (Map) constructor.invoke();
      typeResolver.getReadContext().reference(instance);
      return instance;
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          "Please provide public no arguments constructor for class " + type, e);
    }
  }

  public Map newMap(CopyContext copyContext, Map map) {
    return newMap(map);
  }

  /** Create a new empty map for copy. */
  public Map newMap(Map map) {
    numElements = map.size();
    if (constructor == null) {
      constructor = ReflectionUtils.getCtrHandle(type, true);
    }
    try {
      return (Map) constructor.invoke();
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          "Please provide public no arguments constructor for class " + type, e);
    }
  }

  /**
   * Get and reset numElements of deserializing collection. Should be called after {@link
   * #newMap(MemoryBuffer buffer)}. Nested read may overwrite this element, reset is necessary to
   * avoid use wrong value by mistake.
   */
  public int getAndClearNumElements() {
    int size = numElements;
    numElements = -1; // nested read may overwrite this element.
    return size;
  }

  public void setNumElements(int numElements) {
    this.numElements = numElements;
  }

  public abstract T onMapCopy(Map map);

  public abstract T onMapRead(Map map);
}
