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

import org.apache.fory.Fory;
import org.apache.fory.builder.JITContext;
import org.apache.fory.config.Config;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.SerializationContext;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeInfoHolder;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.type.Generics;

/**
 * Internal adapter that exposes the old runtime helper surface without retaining a {@code Fory}
 * instance inside serializers.
 */
@SuppressWarnings("unchecked")
public final class SerializerRuntime {
  private final Config config;
  private final TypeResolver fallbackTypeResolver;

  SerializerRuntime(Config config) {
    this(config, null);
  }

  SerializerRuntime(TypeResolver typeResolver) {
    this(typeResolver.getConfig(), typeResolver);
  }

  private SerializerRuntime(Config config, TypeResolver fallbackTypeResolver) {
    this.config = config;
    this.fallbackTypeResolver = fallbackTypeResolver;
  }

  public Config getConfig() {
    return config;
  }

  public TypeResolver getTypeResolver() {
    return currentTypeResolver();
  }

  public Fory getFory() {
    return currentFory();
  }

  public RefResolver getRefResolver() {
    return currentRefResolver();
  }

  public Generics getGenerics() {
    return currentGenerics();
  }

  public StringSerializer getStringSerializer() {
    return currentStringSerializer();
  }

  public boolean trackingRef() {
    return config.trackingRef();
  }

  public boolean copyTrackingRef() {
    return config.copyRef();
  }

  public boolean isCrossLanguage() {
    return config.isXlang();
  }

  public boolean compressInt() {
    return config.compressInt();
  }

  public boolean isCompatible() {
    return currentFory().isCompatible();
  }

  public boolean isShareMeta() {
    return config.isMetaShareEnabled();
  }

  public boolean checkClassVersion() {
    return config.checkClassVersion();
  }

  public ClassLoader getClassLoader() {
    return currentFory().getClassLoader();
  }

  public SerializationContext getSerializationContext() {
    return currentFory().getSerializationContext();
  }

  public JITContext getJITContext() {
    return currentFory().getJITContext();
  }

  public Class<? extends Serializer> getDefaultJDKStreamSerializerType() {
    return config.getDefaultJDKStreamSerializerType();
  }

  public BufferCallback getBufferCallback() {
    return WriteContext.current().getBufferCallback();
  }

  public boolean isPeerOutOfBandEnabled() {
    return ReadContext.current().isPeerOutOfBandEnabled();
  }

  public void writeRef(MemoryBuffer buffer, Object obj) {
    WriteContext.current().writeRef(obj);
  }

  public void writeRef(MemoryBuffer buffer, Object obj, TypeInfoHolder typeInfoHolder) {
    WriteContext.current().writeRef(obj, typeInfoHolder);
  }

  public void writeRef(MemoryBuffer buffer, Object obj, TypeInfo typeInfo) {
    WriteContext.current().writeRef(obj, typeInfo);
  }

  public <T> void writeRef(MemoryBuffer buffer, T obj, Serializer<T> serializer) {
    WriteContext.current().writeRef(obj, serializer);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj) {
    WriteContext.current().writeNonRef(obj);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj, TypeInfoHolder holder) {
    WriteContext.current().writeNonRef(obj, holder);
  }

  public void writeNonRef(MemoryBuffer buffer, Object obj, Serializer serializer) {
    WriteContext.current().writeNonRef(obj, serializer);
  }

  public Object readRef(MemoryBuffer buffer) {
    return ReadContext.current().readRef();
  }

  public Object readRef(MemoryBuffer buffer, TypeInfo typeInfo) {
    return ReadContext.current().readRef(typeInfo);
  }

  public Object readRef(MemoryBuffer buffer, TypeInfoHolder typeInfoHolder) {
    return ReadContext.current().readRef(typeInfoHolder);
  }

  public <T> T readRef(MemoryBuffer buffer, Serializer<T> serializer) {
    return ReadContext.current().readRef(serializer);
  }

  public Object readNonRef(MemoryBuffer buffer) {
    return ReadContext.current().readNonRef();
  }

  public Object readNonRef(MemoryBuffer buffer, TypeInfoHolder typeInfoHolder) {
    return ReadContext.current().readNonRef(typeInfoHolder);
  }

  public Object readNonRef(MemoryBuffer buffer, TypeInfo typeInfo) {
    return ReadContext.current().readNonRef(typeInfo);
  }

  public Object readData(MemoryBuffer buffer, TypeInfo typeInfo) {
    return ReadContext.current().readData(typeInfo);
  }

  public Object readNullable(MemoryBuffer buffer) {
    return ReadContext.current().readNullable();
  }

  public Object readNullable(MemoryBuffer buffer, Serializer serializer) {
    return ReadContext.current().readNullable(serializer);
  }

  public void writeString(MemoryBuffer buffer, String value) {
    currentStringSerializer().writeString(buffer, value);
  }

  public String readString(MemoryBuffer buffer) {
    return currentStringSerializer().readString(buffer);
  }

  public void writeStringRef(MemoryBuffer buffer, String value) {
    WriteContext.current().writeStringRef(value);
  }

  public String readStringRef(MemoryBuffer buffer) {
    return ReadContext.current().readStringRef(buffer);
  }

  public void writeBufferObject(MemoryBuffer buffer, BufferObject bufferObject) {
    WriteContext.current().writeBufferObject(bufferObject);
  }

  public void writeBufferObject(
      MemoryBuffer buffer, ArraySerializers.PrimitiveArrayBufferObject bufferObject) {
    WriteContext.current().writeBufferObject(bufferObject);
  }

  public MemoryBuffer readBufferObject(MemoryBuffer buffer) {
    return ReadContext.current().readBufferObject(buffer);
  }

  public void reference(Object origin, Object copied) {
    CopyContext.current().reference(origin, copied);
  }

  public <T> T copyObject(T value) {
    return CopyContext.current().copyObject(value);
  }

  public <T> T copyObject(T value, int classId) {
    return CopyContext.current().copyObject(value, classId);
  }

  public <T> T copyObject(T value, Serializer<T> serializer) {
    return CopyContext.current().copyObject(value, serializer);
  }

  public int getDepth() {
    try {
      return ReadContext.current().getDepth();
    } catch (IllegalStateException ignored) {
      return WriteContext.current().getDepth();
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

  private TypeResolver currentTypeResolver() {
    try {
      return WriteContext.current().getTypeResolver();
    } catch (IllegalStateException ignored) {
      try {
        return ReadContext.current().getTypeResolver();
      } catch (IllegalStateException ignored2) {
        try {
          return CopyContext.current().getTypeResolver();
        } catch (IllegalStateException ignored3) {
          if (fallbackTypeResolver != null) {
            return fallbackTypeResolver;
          }
          throw ignored3;
        }
      }
    }
  }

  private RefResolver currentRefResolver() {
    try {
      return WriteContext.current().getRefResolver();
    } catch (IllegalStateException ignored) {
      try {
        return ReadContext.current().getRefResolver();
      } catch (IllegalStateException ignored2) {
        return currentTypeResolver().getRefResolver();
      }
    }
  }

  private Generics currentGenerics() {
    try {
      return WriteContext.current().getGenerics();
    } catch (IllegalStateException ignored) {
      try {
        return ReadContext.current().getGenerics();
      } catch (IllegalStateException ignored2) {
        return currentTypeResolver().getGenerics();
      }
    }
  }

  private StringSerializer currentStringSerializer() {
    try {
      return WriteContext.current().getStringSerializer();
    } catch (IllegalStateException ignored) {
      try {
        return ReadContext.current().getStringSerializer();
      } catch (IllegalStateException ignored2) {
        return currentTypeResolver().getStringSerializer();
      }
    }
  }

  private Fory currentFory() {
    return currentTypeResolver().getFory();
  }
}
