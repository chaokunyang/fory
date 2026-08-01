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

package org.apache.fory.json;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.resolver.JsonSharedRegistry.GeneratedClasses;
import org.apache.fory.reflect.TypeRef;

/** Frozen Native Image mapping from JSON configuration semantics to generated classes. */
@Internal
public final class JsonGeneratedClassRegistry {
  private static Map<JsonCodegenKey, MutableConfiguration> pending = new HashMap<>();
  private static Map<JsonCodegenKey, Configuration> configurations = Collections.emptyMap();
  private static boolean frozen;

  private JsonGeneratedClassRegistry() {}

  static synchronized Set<Class<?>> register(
      JsonCodegenKey key, GeneratedClasses generatedClasses) {
    if (frozen) {
      throw new IllegalStateException("Fory JSON generated class registry is frozen");
    }
    MutableConfiguration configuration = pending.get(key);
    if (configuration == null) {
      configuration = new MutableConfiguration();
      pending.put(key, configuration);
    }
    LinkedHashSet<Class<?>> added = new LinkedHashSet<>();
    configuration.merge(generatedClasses, added);
    configurations = snapshot();
    return added;
  }

  static synchronized void freeze() {
    if (frozen) {
      return;
    }
    pending = null;
    frozen = true;
  }

  private static Map<JsonCodegenKey, Configuration> snapshot() {
    Map<JsonCodegenKey, Configuration> snapshot = new HashMap<>(pending.size());
    for (Map.Entry<JsonCodegenKey, MutableConfiguration> entry : pending.entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue().freeze());
    }
    return Collections.unmodifiableMap(snapshot);
  }

  /** Returns the immutable generated classes for {@code key}, or {@code null}. */
  @Internal
  public static Configuration configuration(JsonCodegenKey key) {
    return configurations.get(key);
  }

  /** Immutable generated classes for one configuration. */
  @Internal
  public static final class Configuration {
    private final Map<Class<?>, Class<?>> stringWriters;
    private final Map<Class<?>, Class<?>> utf8Writers;
    private final Map<Class<?>, Class<?>> latin1Readers;
    private final Map<Class<?>, Class<?>> utf16Readers;
    private final Map<Class<?>, Class<?>> utf8Readers;
    private final Map<String, Class<?>> utf8CollectionWriters;
    private final Map<String, Class<?>> utf8CollectionReaders;

    private Configuration(MutableConfiguration source) {
      stringWriters = immutable(source.stringWriters);
      utf8Writers = immutable(source.utf8Writers);
      latin1Readers = immutable(source.latin1Readers);
      utf16Readers = immutable(source.utf16Readers);
      utf8Readers = immutable(source.utf8Readers);
      utf8CollectionWriters = immutable(source.utf8CollectionWriters);
      utf8CollectionReaders = immutable(source.utf8CollectionReaders);
    }

    public Class<?> stringWriter(Class<?> type) {
      return stringWriters.get(type);
    }

    public Class<?> utf8Writer(Class<?> type) {
      return utf8Writers.get(type);
    }

    public Class<?> latin1Reader(Class<?> type) {
      return latin1Readers.get(type);
    }

    public Class<?> utf16Reader(Class<?> type) {
      return utf16Readers.get(type);
    }

    public Class<?> utf8Reader(Class<?> type) {
      return utf8Readers.get(type);
    }

    public Class<?> utf8CollectionWriter(Type type) {
      return utf8CollectionWriters.get(typeKey(type));
    }

    public Class<?> utf8CollectionReader(Type type) {
      return utf8CollectionReaders.get(typeKey(type));
    }

    private static <K> Map<K, Class<?>> immutable(Map<K, Class<?>> classes) {
      return classes.isEmpty()
          ? Collections.emptyMap()
          : Collections.unmodifiableMap(new HashMap<>(classes));
    }
  }

  private static final class MutableConfiguration {
    private final Map<Class<?>, Class<?>> stringWriters = new HashMap<>();
    private final Map<Class<?>, Class<?>> utf8Writers = new HashMap<>();
    private final Map<Class<?>, Class<?>> latin1Readers = new HashMap<>();
    private final Map<Class<?>, Class<?>> utf16Readers = new HashMap<>();
    private final Map<Class<?>, Class<?>> utf8Readers = new HashMap<>();
    private final Map<String, Class<?>> utf8CollectionWriters = new HashMap<>();
    private final Map<String, Class<?>> utf8CollectionReaders = new HashMap<>();

    private void merge(GeneratedClasses source, Set<Class<?>> added) {
      merge(source.stringWriters(), stringWriters, added);
      merge(source.utf8Writers(), utf8Writers, added);
      merge(source.latin1Readers(), latin1Readers, added);
      merge(source.utf16Readers(), utf16Readers, added);
      merge(source.utf8Readers(), utf8Readers, added);
      mergeTypes(source.utf8CollectionWriters(), utf8CollectionWriters, added);
      mergeTypes(source.utf8CollectionReaders(), utf8CollectionReaders, added);
    }

    private Configuration freeze() {
      return new Configuration(this);
    }

    private static <K> void merge(
        Map<K, Class<?>> source, Map<K, Class<?>> target, Set<Class<?>> added) {
      for (Map.Entry<K, Class<?>> entry : source.entrySet()) {
        merge(entry.getKey(), entry.getValue(), target, added);
      }
    }

    private static void mergeTypes(
        Map<Type, Class<?>> source, Map<String, Class<?>> target, Set<Class<?>> added) {
      for (Map.Entry<Type, Class<?>> entry : source.entrySet()) {
        merge(typeKey(entry.getKey()), entry.getValue(), target, added);
      }
    }

    private static <K> void merge(
        K key, Class<?> generatedClass, Map<K, Class<?>> target, Set<Class<?>> added) {
      Class<?> previous = target.putIfAbsent(key, generatedClass);
      if (previous == null) {
        added.add(generatedClass);
      } else if (previous != generatedClass) {
        throw new IllegalStateException("Conflicting generated Fory JSON classes for " + key);
      }
    }
  }

  private static String typeKey(Type type) {
    return TypeRef.of(type).getTypeKey();
  }
}
