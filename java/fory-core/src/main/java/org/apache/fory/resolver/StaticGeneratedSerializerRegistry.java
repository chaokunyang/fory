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

package org.apache.fory.resolver;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.annotation.Internal;
import org.apache.fory.exception.ForyException;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.serializer.StaticGeneratedStructSerializer;
import org.apache.fory.type.Descriptor;

/** Shared registry of build-time generated static serializer mappings. */
@Internal
public final class StaticGeneratedSerializerRegistry {
  public enum Mode {
    XLANG,
    NATIVE
  }

  public interface RuntimeFactory {
    StaticGeneratedStructSerializer<?> create(
        TypeResolver resolver, Class<?> type, TypeDef typeDef);
  }

  public interface DescriptorFactory {
    StaticGeneratedStructSerializer<?> create();
  }

  private static final class Entry {
    private final Class<? extends StaticGeneratedStructSerializer> serializerClass;
    private final RuntimeFactory runtimeFactory;
    private final DescriptorFactory descriptorFactory;

    private Entry(
        Class<? extends StaticGeneratedStructSerializer> serializerClass,
        RuntimeFactory runtimeFactory,
        DescriptorFactory descriptorFactory) {
      this.serializerClass = serializerClass;
      this.runtimeFactory = runtimeFactory;
      this.descriptorFactory = descriptorFactory;
    }

    Class<? extends StaticGeneratedStructSerializer> getSerializerClass() {
      return serializerClass;
    }

    StaticGeneratedStructSerializer<?> newSerializer(
        TypeResolver resolver, Class<?> type, TypeDef typeDef) {
      return runtimeFactory.create(resolver, type, typeDef);
    }

    List<Descriptor> getGeneratedDescriptors() {
      return descriptorFactory.create().getGeneratedDescriptors();
    }
  }

  private final ConcurrentHashMap<Class<?>, Entry> xlangSerializers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Class<?>, Entry> nativeSerializers = new ConcurrentHashMap<>();
  private final Set<ClassLoader> loadedClassLoaders =
      Collections.newSetFromMap(new IdentityHashMap<>());

  public void register(
      Class<?> targetType,
      Mode mode,
      Class<? extends StaticGeneratedStructSerializer> serializerClass,
      RuntimeFactory runtimeFactory,
      DescriptorFactory descriptorFactory) {
    Entry entry = new Entry(serializerClass, runtimeFactory, descriptorFactory);
    ConcurrentHashMap<Class<?>, Entry> entries =
        mode == Mode.XLANG ? xlangSerializers : nativeSerializers;
    Entry existing = entries.putIfAbsent(targetType, entry);
    if (existing != null && existing.getSerializerClass() != serializerClass) {
      throw new ForyException(
          "Conflicting static generated serializer SPI mapping for "
              + targetType.getName()
              + " in "
              + mode
              + " mode. Existing="
              + existing.getSerializerClass().getName()
              + ", new="
              + serializerClass.getName());
    }
  }

  Class<? extends StaticGeneratedStructSerializer> getSerializerClass(
      Class<?> targetType, boolean xlang, ClassLoader resolverClassLoader) {
    Entry entry = getEntry(targetType, xlang, resolverClassLoader);
    return entry == null ? null : entry.getSerializerClass();
  }

  StaticGeneratedStructSerializer<?> newSerializer(
      TypeResolver resolver,
      Class<?> targetType,
      Class<? extends StaticGeneratedStructSerializer> serializerClass,
      TypeDef typeDef) {
    Entry entry = getEntry(targetType, resolver.isCrossLanguage(), resolver.getClassLoader());
    if (entry == null || entry.getSerializerClass() != serializerClass) {
      return null;
    }
    return entry.newSerializer(resolver, targetType, typeDef);
  }

  List<Descriptor> getGeneratedDescriptors(
      Class<?> targetType, boolean xlang, ClassLoader resolverClassLoader) {
    Entry entry = getEntry(targetType, xlang, resolverClassLoader);
    return entry == null ? null : entry.getGeneratedDescriptors();
  }

  private Entry getEntry(Class<?> targetType, boolean xlang, ClassLoader resolverClassLoader) {
    loadFrom(resolverClassLoader);
    ClassLoader targetClassLoader = targetType.getClassLoader();
    if (targetClassLoader != resolverClassLoader) {
      loadFrom(targetClassLoader);
    }
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null
        && contextClassLoader != resolverClassLoader
        && contextClassLoader != targetClassLoader) {
      loadFrom(contextClassLoader);
    }
    return (xlang ? xlangSerializers : nativeSerializers).get(targetType);
  }

  private void loadFrom(ClassLoader classLoader) {
    synchronized (loadedClassLoaders) {
      if (!loadedClassLoaders.add(classLoader)) {
        return;
      }
      loadFrom(classLoader, StaticGeneratedSerializerProvider.class);
      loadFrom(classLoader, StaticGeneratedSerializerProvider.JavaAnnotationProcessor.class);
      loadFrom(classLoader, StaticGeneratedSerializerProvider.KotlinSymbolProcessor.class);
    }
  }

  private <T extends StaticGeneratedSerializerProvider> void loadFrom(
      ClassLoader classLoader, Class<T> providerType) {
    ServiceLoader<T> providers =
        classLoader == null
            ? ServiceLoader.load(providerType)
            : ServiceLoader.load(providerType, classLoader);
    for (T provider : providers) {
      provider.register(this);
    }
  }
}
