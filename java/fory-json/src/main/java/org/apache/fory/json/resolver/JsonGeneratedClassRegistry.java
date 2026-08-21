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

package org.apache.fory.json.resolver;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.codec.GeneratedJsonCodec;
import org.apache.fory.json.codegen.GeneratedCodecKey;
import org.apache.fory.json.resolver.JsonSharedRegistry.GeneratedClasses;
import org.apache.fory.reflect.TypeRef;

/** Frozen exact-key registry of generated JSON classes retained in a Native Image. */
@Internal
public final class JsonGeneratedClassRegistry {
  private static Map<GeneratedCodecKey, Class<?>> pendingClasses = new HashMap<>();
  private static Map<CompanionKey, GeneratedJsonCodec<?>> pendingCompanions = new HashMap<>();
  private static GeneratedEntry[] generatedEntries = new GeneratedEntry[0];
  private static CompanionEntry[] companionEntries = new CompanionEntry[0];
  private static boolean frozen;

  private JsonGeneratedClassRegistry() {}

  /** Publishes one hosted configuration's generated classes during Native Image analysis. */
  public static synchronized Set<Class<?>> register(JsonSharedRegistry hostedRegistry) {
    if (frozen) {
      throw new IllegalStateException("Fory JSON generated class registry is frozen");
    }
    GeneratedClasses generated = hostedRegistry.generatedClasses();
    LinkedHashSet<Class<?>> added = new LinkedHashSet<>();
    mergeClasses(generated.classes(), added);
    mergeCompanions(generated.sourceCodecs(), added);
    snapshot();
    return added;
  }

  /** Finalizes Native runtime lookup and releases hosted mutable state. */
  public static synchronized void freeze() {
    if (frozen) {
      return;
    }
    snapshot();
    pendingClasses = null;
    pendingCompanions = null;
    frozen = true;
  }

  static Class<?> generatedClass(GeneratedCodecKey key) {
    Map<GeneratedCodecKey, Class<?>> pending = pendingClasses;
    if (pending != null) {
      return pending.get(key);
    }
    for (GeneratedEntry entry : generatedEntries) {
      if (entry.key.equals(key)) {
        return entry.generatedClass;
      }
    }
    return null;
  }

  static GeneratedJsonCodec<?> sourceCodec(CompanionKey key) {
    Map<CompanionKey, GeneratedJsonCodec<?>> pending = pendingCompanions;
    if (pending != null) {
      return pending.get(key);
    }
    for (CompanionEntry entry : companionEntries) {
      if (entry.key.equals(key)) {
        return entry.codec;
      }
    }
    return null;
  }

  private static void mergeClasses(Map<GeneratedCodecKey, Class<?>> source, Set<Class<?>> added) {
    for (Map.Entry<GeneratedCodecKey, Class<?>> entry : source.entrySet()) {
      GeneratedCodecKey key = entry.getKey();
      Class<?> generatedClass = entry.getValue();
      Class<?> previous = pendingClasses.putIfAbsent(key, generatedClass);
      if (previous == null) {
        added.add(generatedClass);
      } else if (previous != generatedClass) {
        throw new IllegalStateException(
            "Conflicting generated Fory JSON classes for " + key.targetClass().getName());
      }
    }
  }

  private static void mergeCompanions(
      Map<CompanionKey, GeneratedJsonCodec<?>> source, Set<Class<?>> added) {
    mergeSourceCodecs(source, pendingCompanions, added);
  }

  static void mergeSourceCodecs(
      Map<CompanionKey, ? extends GeneratedJsonCodec<?>> source,
      Map<CompanionKey, GeneratedJsonCodec<?>> target,
      Set<Class<?>> added) {
    for (Map.Entry<CompanionKey, ? extends GeneratedJsonCodec<?>> entry : source.entrySet()) {
      GeneratedJsonCodec<?> codec = entry.getValue();
      GeneratedJsonCodec<?> previous = target.putIfAbsent(entry.getKey(), codec);
      if (previous == null) {
        added.add(codec.getClass());
      } else if (previous.getClass() != codec.getClass()) {
        throw new IllegalStateException(
            "Conflicting source-generated Fory JSON companions for " + entry.getKey().type);
      }
    }
  }

  private static void snapshot() {
    generatedEntries = new GeneratedEntry[pendingClasses.size()];
    int index = 0;
    for (Map.Entry<GeneratedCodecKey, Class<?>> entry : pendingClasses.entrySet()) {
      generatedEntries[index++] = new GeneratedEntry(entry.getKey(), entry.getValue());
    }
    companionEntries = new CompanionEntry[pendingCompanions.size()];
    index = 0;
    for (Map.Entry<CompanionKey, GeneratedJsonCodec<?>> entry : pendingCompanions.entrySet()) {
      companionEntries[index++] = new CompanionEntry(entry.getKey(), entry.getValue());
    }
  }

  static final class CompanionKey {
    private final TypeRef<?> type;
    private final Class<?> mixinType;
    private final int hash;

    CompanionKey(TypeRef<?> type, Class<?> mixinType) {
      this.type = type;
      this.mixinType = mixinType;
      hash = type.hashCode() * 31 + System.identityHashCode(mixinType);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof CompanionKey)) {
        return false;
      }
      CompanionKey that = (CompanionKey) other;
      return type.equals(that.type) && mixinType == that.mixinType;
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  private static final class GeneratedEntry {
    private final GeneratedCodecKey key;
    private final Class<?> generatedClass;

    private GeneratedEntry(GeneratedCodecKey key, Class<?> generatedClass) {
      this.key = key;
      this.generatedClass = generatedClass;
    }
  }

  private static final class CompanionEntry {
    private final CompanionKey key;
    private final GeneratedJsonCodec<?> codec;

    private CompanionEntry(CompanionKey key, GeneratedJsonCodec<?> codec) {
      this.key = key;
      this.codec = codec;
    }
  }
}
