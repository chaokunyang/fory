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
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.annotation.Internal;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.collection.ConcurrentIdentityMap;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.MetaString;
import org.apache.fory.meta.MetaStringEncoder;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.serializer.ResolvedFieldInfo;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorBuilder;
import org.apache.fory.type.DescriptorGrouper;

/** Shared caches reused by multiple equivalent {@link org.apache.fory.Fory} instances. */
@Internal
public final class SharedRegistry {
  final ConcurrentIdentityMap<Class<?>, TypeDef> typeDefMap = new ConcurrentIdentityMap<>();
  final ConcurrentIdentityMap<Class<?>, TypeDef> currentLayerTypeDef =
      new ConcurrentIdentityMap<>();
  final ConcurrentHashMap<Long, TypeDef> typeDefById = new ConcurrentHashMap<>();
  final ConcurrentHashMap<Tuple2<Class<?>, Boolean>, SortedMap<Member, Descriptor>>
      descriptorsCache = new ConcurrentHashMap<>();
  final ConcurrentHashMap<FieldDescriptorsKey, List<Descriptor>> fieldDescriptorsCache =
      new ConcurrentHashMap<>();
  final ConcurrentHashMap<Field, Descriptor> fieldDescriptorCache = new ConcurrentHashMap<>();
  final ConcurrentHashMap<FieldReadMethodKey, Descriptor> fieldReadMethodDescriptorCache =
      new ConcurrentHashMap<>();
  final ConcurrentIdentityMap<Descriptor, Boolean> sharedFieldDescriptors =
      new ConcurrentIdentityMap<>();
  final ConcurrentIdentityMap<Collection<Descriptor>, FieldDescriptorsKey>
      fieldDescriptorCollectionKeys = new ConcurrentIdentityMap<>();
  final ConcurrentIdentityMap<Descriptor, ResolvedFieldInfo> resolvedFieldInfoCache =
      new ConcurrentIdentityMap<>();
  final ConcurrentHashMap<DescriptorGrouperKey, DescriptorGrouper> descriptorGrouperCache =
      new ConcurrentHashMap<>();
  final ConcurrentHashMap<List<ClassLoader>, CodeGenerator> codeGeneratorMap =
      new ConcurrentHashMap<>();
  final ConcurrentHashMap<MetaStringKey, EncodedMetaString> metaStringMap =
      new ConcurrentHashMap<>();
  volatile IdentityHashMap<Class<?>, Integer> registeredClassIdMap;
  volatile BiMap<String, Class<?>> registeredClasses;

  synchronized void setRegistrationIfAbsent(
      IdentityHashMap<Class<?>, Integer> candidateRegisteredClassIdMap,
      BiMap<String, Class<?>> candidateRegisteredClasses) {
    Objects.requireNonNull(candidateRegisteredClassIdMap);
    Objects.requireNonNull(candidateRegisteredClasses);
    if (registeredClassIdMap == null) {
      registeredClassIdMap = candidateRegisteredClassIdMap;
      registeredClasses = candidateRegisteredClasses;
    }
  }

  synchronized IdentityHashMap<Class<?>, Integer> getRegisteredClassIdMap() {
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

  public Descriptor getOrCreateDescriptor(
      Field field, Method readMethod, boolean trackingRef, boolean nullable) {
    Objects.requireNonNull(field, "field");
    Descriptor descriptor;
    if (readMethod == null) {
      descriptor =
          fieldDescriptorCache.computeIfAbsent(
              field,
              ignored -> {
                Descriptor base =
                    new Descriptor(field, TypeRef.of(field.getAnnotatedType()), null, null);
                if (trackingRef == base.isTrackingRef() && nullable == base.isNullable()) {
                  return base;
                }
                return new DescriptorBuilder(base)
                    .trackingRef(trackingRef)
                    .nullable(nullable)
                    .build();
              });
    } else {
      FieldReadMethodKey key = new FieldReadMethodKey(field, readMethod);
      descriptor =
          fieldReadMethodDescriptorCache.computeIfAbsent(
              key,
              ignored -> {
                Descriptor base =
                    new Descriptor(field, TypeRef.of(field.getAnnotatedType()), readMethod, null);
                if (trackingRef == base.isTrackingRef() && nullable == base.isNullable()) {
                  return base;
                }
                return new DescriptorBuilder(base)
                    .trackingRef(trackingRef)
                    .nullable(nullable)
                    .build();
              });
    }
    sharedFieldDescriptors.putIfAbsent(descriptor, Boolean.TRUE);
    return descriptor;
  }

  List<Descriptor> getOrCreateFieldDescriptors(
      Class<?> type,
      boolean searchParent,
      java.util.function.Supplier<List<Descriptor>> factory) {
    FieldDescriptorsKey key = new FieldDescriptorsKey(type, searchParent);
    List<Descriptor> descriptors =
        fieldDescriptorsCache.computeIfAbsent(
            key, ignored -> Collections.unmodifiableList(new ArrayList<>(factory.get())));
    fieldDescriptorCollectionKeys.putIfAbsent(descriptors, key);
    for (Descriptor descriptor : descriptors) {
      Field field = descriptor.getField();
      if (field != null) {
        Method readMethod = descriptor.getReadMethod();
        if (readMethod == null) {
          fieldDescriptorCache.putIfAbsent(field, descriptor);
        } else {
          fieldReadMethodDescriptorCache.putIfAbsent(
              new FieldReadMethodKey(field, readMethod), descriptor);
        }
      }
      sharedFieldDescriptors.putIfAbsent(descriptor, Boolean.TRUE);
    }
    return descriptors;
  }

  public ResolvedFieldInfo getOrCreateResolvedFieldInfo(
      Descriptor descriptor,
      java.util.function.Supplier<ResolvedFieldInfo> factory) {
    if (sharedFieldDescriptors.get(descriptor) == null) {
      return factory.get();
    }
    return resolvedFieldInfoCache.computeIfAbsent(descriptor, ignored -> factory.get());
  }

  DescriptorGrouper getOrCreateDescriptorGrouper(
      Collection<Descriptor> descriptors,
      boolean descriptorsGroupedOrdered,
      java.util.function.Function<Descriptor, Descriptor> descriptorUpdator,
      java.util.function.Supplier<DescriptorGrouper> factory) {
    FieldDescriptorsKey fieldDescriptorsKey = fieldDescriptorCollectionKeys.get(descriptors);
    if (fieldDescriptorsKey == null) {
      return factory.get();
    }
    DescriptorGrouperKey key =
        new DescriptorGrouperKey(fieldDescriptorsKey, descriptorsGroupedOrdered, descriptorUpdator);
    return descriptorGrouperCache.computeIfAbsent(key, ignored -> factory.get());
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
    fieldDescriptorsCache.entrySet()
        .removeIf(entry -> entry.getKey().type.getClassLoader() == loader);
    fieldDescriptorCache.entrySet()
        .removeIf(entry -> entry.getKey().getDeclaringClass().getClassLoader() == loader);
    fieldReadMethodDescriptorCache.entrySet()
        .removeIf(entry -> hasClassLoader(entry.getKey(), loader));
    sharedFieldDescriptors.removeIf(
        (descriptor, ignored) -> hasClassLoader(descriptor, loader));
    fieldDescriptorCollectionKeys.removeIf(
        (descriptors, key) -> key.type.getClassLoader() == loader);
    resolvedFieldInfoCache.removeIf((descriptor, ignored) -> hasClassLoader(descriptor, loader));
    descriptorGrouperCache.entrySet()
        .removeIf(entry -> entry.getKey().fieldDescriptorsKey.type.getClassLoader() == loader);
    codeGeneratorMap.entrySet().removeIf(entry -> entry.getKey().contains(loader));
  }

  private synchronized void clearSharedRegistrationIfClassLoader(ClassLoader loader) {
    IdentityHashMap<Class<?>, Integer> sharedRegisteredClassIdMap = registeredClassIdMap;
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
      IdentityHashMap<Class<?>, ?> classMap, ClassLoader loader) {
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

  private static boolean hasClassLoader(Descriptor descriptor, ClassLoader loader) {
    java.lang.reflect.Field field = descriptor.getField();
    return (field != null && field.getDeclaringClass().getClassLoader() == loader)
        || descriptor.getRawType().getClassLoader() == loader;
  }

  private static boolean hasClassLoader(FieldReadMethodKey key, ClassLoader loader) {
    return key.field.getDeclaringClass().getClassLoader() == loader
        || key.readMethod.getDeclaringClass().getClassLoader() == loader;
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

  private static final class FieldDescriptorsKey {
    private final Class<?> type;
    private final boolean searchParent;

    private FieldDescriptorsKey(Class<?> type, boolean searchParent) {
      this.type = Objects.requireNonNull(type);
      this.searchParent = searchParent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FieldDescriptorsKey)) {
        return false;
      }
      FieldDescriptorsKey that = (FieldDescriptorsKey) o;
      return type == that.type && searchParent == that.searchParent;
    }

    @Override
    public int hashCode() {
      return Objects.hash(System.identityHashCode(type), searchParent);
    }
  }

  private static final class FieldReadMethodKey {
    private final Field field;
    private final Method readMethod;
    private final int hash;

    private FieldReadMethodKey(Field field, Method readMethod) {
      this.field = Objects.requireNonNull(field);
      this.readMethod = Objects.requireNonNull(readMethod);
      this.hash = 31 * field.hashCode() + readMethod.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FieldReadMethodKey)) {
        return false;
      }
      FieldReadMethodKey that = (FieldReadMethodKey) o;
      return field.equals(that.field) && readMethod.equals(that.readMethod);
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }

  private static final class DescriptorGrouperKey {
    private final FieldDescriptorsKey fieldDescriptorsKey;
    private final boolean descriptorsGroupedOrdered;
    private final java.util.function.Function<Descriptor, Descriptor> descriptorUpdator;

    private DescriptorGrouperKey(
        FieldDescriptorsKey fieldDescriptorsKey,
        boolean descriptorsGroupedOrdered,
        java.util.function.Function<Descriptor, Descriptor> descriptorUpdator) {
      this.fieldDescriptorsKey = Objects.requireNonNull(fieldDescriptorsKey);
      this.descriptorsGroupedOrdered = descriptorsGroupedOrdered;
      this.descriptorUpdator = descriptorUpdator;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof DescriptorGrouperKey)) {
        return false;
      }
      DescriptorGrouperKey that = (DescriptorGrouperKey) o;
      return descriptorsGroupedOrdered == that.descriptorsGroupedOrdered
          && fieldDescriptorsKey.equals(that.fieldDescriptorsKey)
          && descriptorUpdator == that.descriptorUpdator;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          fieldDescriptorsKey,
          descriptorsGroupedOrdered,
          System.identityHashCode(descriptorUpdator));
    }
  }
}
