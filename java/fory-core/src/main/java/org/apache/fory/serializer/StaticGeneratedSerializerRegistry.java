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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.apache.fory.annotation.Internal;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.type.Descriptor;

/** Runtime-local registry of build-time generated static serializers. */
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

  public static final class Entry {
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

    public Class<? extends StaticGeneratedStructSerializer> getSerializerClass() {
      return serializerClass;
    }

    public StaticGeneratedStructSerializer<?> newSerializer(
        TypeResolver resolver, Class<?> type, TypeDef typeDef) {
      return runtimeFactory.create(resolver, type, typeDef);
    }

    public List<Descriptor> getGeneratedDescriptors() {
      return descriptorFactory.create().getGeneratedDescriptors();
    }
  }

  private final Map<Class<?>, Entry> xlangSerializers = new HashMap<>();
  private final Map<Class<?>, Entry> nativeSerializers = new HashMap<>();

  public void register(
      Class<?> targetType,
      Mode mode,
      Class<? extends StaticGeneratedStructSerializer> serializerClass,
      RuntimeFactory runtimeFactory,
      DescriptorFactory descriptorFactory) {
    Entry entry = new Entry(serializerClass, runtimeFactory, descriptorFactory);
    if (mode == Mode.XLANG) {
      xlangSerializers.put(targetType, entry);
    } else {
      nativeSerializers.put(targetType, entry);
    }
  }

  public void registerProvider(StaticGeneratedSerializerProvider provider) {
    Objects.requireNonNull(provider, "provider").register(this);
  }

  public Entry get(Class<?> targetType, boolean xlang) {
    return (xlang ? xlangSerializers : nativeSerializers).get(targetType);
  }

  public static StaticGeneratedSerializerRegistry load(ClassLoader classLoader) {
    StaticGeneratedSerializerRegistry registry = new StaticGeneratedSerializerRegistry();
    registry.loadFrom(classLoader);
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null && contextClassLoader != classLoader) {
      registry.loadFrom(contextClassLoader);
    }
    return registry;
  }

  public void loadFrom(ClassLoader classLoader) {
    loadFrom(classLoader, StaticGeneratedSerializerProvider.class);
    loadFrom(classLoader, StaticGeneratedSerializerProvider.JavaAnnotationProcessor.class);
    loadFrom(classLoader, StaticGeneratedSerializerProvider.KotlinSymbolProcessor.class);
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
