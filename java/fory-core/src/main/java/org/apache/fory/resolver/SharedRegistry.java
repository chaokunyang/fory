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

import com.google.common.collect.BiMap;
import java.lang.reflect.Member;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.annotation.Internal;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.collection.ConcurrentIdentityMap;
import org.apache.fory.collection.IdentityMap;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.MetaString;
import org.apache.fory.meta.MetaStringEncoder;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.type.Descriptor;

/** Shared caches reused by multiple equivalent {@link org.apache.fory.Fory} instances. */
@Internal
public final class SharedRegistry {
  final ConcurrentIdentityMap<Class<?>, TypeDef> typeDefMap = new ConcurrentIdentityMap<>();
  final ConcurrentIdentityMap<Class<?>, TypeDef> currentLayerTypeDef =
      new ConcurrentIdentityMap<>();
  final ConcurrentHashMap<Long, TypeDef> typeDefById = new ConcurrentHashMap<>();
  final ConcurrentHashMap<Tuple2<Class<?>, Boolean>, SortedMap<Member, Descriptor>>
      descriptorsCache = new ConcurrentHashMap<>();
  final ConcurrentHashMap<List<ClassLoader>, CodeGenerator> codeGeneratorMap =
      new ConcurrentHashMap<>();
  final ConcurrentHashMap<MetaStringKey, EncodedMetaString> metaStringMap =
      new ConcurrentHashMap<>();
  volatile IdentityMap<Class<?>, Integer> registeredClassIdMap;
  volatile BiMap<String, Class<?>> registeredClasses;

  synchronized void setRegistrationIfAbsent(
      IdentityMap<Class<?>, Integer> candidateRegisteredClassIdMap,
      BiMap<String, Class<?>> candidateRegisteredClasses) {
    Objects.requireNonNull(candidateRegisteredClassIdMap);
    Objects.requireNonNull(candidateRegisteredClasses);
    if (registeredClassIdMap == null) {
      registeredClassIdMap = candidateRegisteredClassIdMap;
      registeredClasses = candidateRegisteredClasses;
    }
  }

  synchronized IdentityMap<Class<?>, Integer> getRegisteredClassIdMap() {
    return Objects.requireNonNull(registeredClassIdMap);
  }

  synchronized BiMap<String, Class<?>> getRegisteredClasses() {
    return Objects.requireNonNull(registeredClasses);
  }

  EncodedMetaString getOrCreateEncodedMetaString(
      String string,
      MetaStringEncoder encoder,
      MetaString.Encoding encoding,
      String encoderTypeKey) {
    if (string.isEmpty()) {
      return EncodedMetaString.EMPTY;
    }
    MetaStringKey key = new MetaStringKey(string, encoderTypeKey, encoding);
    return metaStringMap.computeIfAbsent(key, ignored -> encoder.encodeBinary(string, encoding));
  }

  TypeDef getOrCreateTypeDef(TypeDef typeDef) {
    TypeDef existingTypeDef = typeDefById.putIfAbsent(typeDef.getId(), typeDef);
    return existingTypeDef == null ? typeDef : existingTypeDef;
  }

  public void clearClassLoader(ClassLoader loader) {
    if (loader == null) {
      return;
    }
    clearSharedRegistrationIfClassLoader(loader);
    typeDefMap.removeIf((cls, typeDef) -> cls.getClassLoader() == loader);
    currentLayerTypeDef.removeIf((cls, typeDef) -> cls.getClassLoader() == loader);
    typeDefById.entrySet()
        .removeIf(
            entry -> {
              Class<?> cls = entry.getValue().getClassSpec().type;
              return cls != null && cls.getClassLoader() == loader;
        });
    descriptorsCache.entrySet().removeIf(entry -> entry.getKey().f0.getClassLoader() == loader);
    codeGeneratorMap.entrySet().removeIf(entry -> entry.getKey().contains(loader));
  }

  private synchronized void clearSharedRegistrationIfClassLoader(ClassLoader loader) {
    IdentityMap<Class<?>, Integer> sharedRegisteredClassIdMap = registeredClassIdMap;
    BiMap<String, Class<?>> sharedRegisteredClasses = registeredClasses;
    if (sharedRegisteredClassIdMap == null || sharedRegisteredClasses == null) {
      return;
    }
    if (containsClassLoader(sharedRegisteredClassIdMap, loader)
        || containsClassLoader(sharedRegisteredClasses.values(), loader)) {
      registeredClassIdMap = null;
      registeredClasses = null;
    }
  }

  private static boolean containsClassLoader(
      Iterable<Class<?>> classes, ClassLoader loader) {
    for (Class<?> cls : classes) {
      if (cls.getClassLoader() == loader) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsClassLoader(
      IdentityMap<Class<?>, ?> classMap, ClassLoader loader) {
    final boolean[] found = new boolean[1];
    classMap.forEach(
        (cls, value) -> {
          if (cls.getClassLoader() == loader) {
            found[0] = true;
          }
        });
    if (found[0]) {
      return true;
    }
    return false;
  }

  private static final class MetaStringKey {
    private final String string;
    private final String encoderTypeKey;
    private final MetaString.Encoding encoding;

    private MetaStringKey(
        String string, String encoderTypeKey, MetaString.Encoding encoding) {
      this.string = Objects.requireNonNull(string);
      this.encoderTypeKey = Objects.requireNonNull(encoderTypeKey);
      this.encoding = Objects.requireNonNull(encoding);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MetaStringKey)) {
        return false;
      }
      MetaStringKey that = (MetaStringKey) o;
      return string.equals(that.string)
          && encoderTypeKey.equals(that.encoderTypeKey)
          && encoding == that.encoding;
    }

    @Override
    public int hashCode() {
      return Objects.hash(string, encoderTypeKey, encoding);
    }
  }
}
