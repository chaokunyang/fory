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

package org.apache.fory.serializer.scala;

import static org.apache.fory.serializer.scala.ToFactorySerializers.IterableToFactoryClass;
import static org.apache.fory.serializer.scala.ToFactorySerializers.MapToFactoryClass;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.annotation.Internal;
import org.apache.fory.config.Config;
import org.apache.fory.exception.ForyException;
import org.apache.fory.resolver.TypeResolver;
import scala.collection.immutable.NumericRange;
import scala.collection.immutable.Range;

public class ScalaSerializers {
  private static final Map<Fory, Boolean> INSTALLED_FORY =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static void registerSerializers(ThreadSafeFory fory) {
    fory.register(ScalaSerializers::registerSerializers);
  }

  public static void registerSerializers(Fory fory) {
    TypeResolver resolver = fory.getTypeResolver();
    checkRegistrationOpen(resolver);
    synchronized (INSTALLED_FORY) {
      if (INSTALLED_FORY.containsKey(fory)) {
        return;
      }
      INSTALLED_FORY.put(fory, Boolean.TRUE);
    }
    try {
      fory.registerSerializerFactory(new ScalaSerializerFactory());
      if (resolver.isCrossLanguage()) {
        return;
      }
      Config config = resolver.getConfig();

      resolver.registerSerializer(
          IterableToFactoryClass, new ToFactorySerializers.IterableToFactorySerializer(config));
      resolver.registerSerializer(
          MapToFactoryClass, new ToFactorySerializers.MapToFactorySerializer(config));

    // Seq
    resolver.register(scala.collection.immutable.Seq.class);
    resolver.register(scala.collection.immutable.Nil$.class);
    resolver.register(scala.collection.immutable.List$.class);
    resolver.register(scala.collection.immutable.$colon$colon.class);
    // StrictOptimizedSeqFactory -> ... extends -> IterableFactory
    resolver.register(scala.collection.immutable.Vector$.class);
    resolver.register("scala.collection.immutable.VectorImpl");
    resolver.register("scala.collection.immutable.Vector0");
    resolver.register("scala.collection.immutable.Vector1");
    resolver.register("scala.collection.immutable.Vector2");
    resolver.register("scala.collection.immutable.Vector3");
    resolver.register("scala.collection.immutable.Vector4");
    resolver.register("scala.collection.immutable.Vector5");
    resolver.register("scala.collection.immutable.Vector6");
    resolver.register(scala.collection.immutable.Queue.class);
    resolver.register(scala.collection.immutable.Queue$.class);
    resolver.register(scala.collection.immutable.LazyList.class);
    resolver.register(scala.collection.immutable.LazyList$.class);
    resolver.register(scala.collection.immutable.ArraySeq.class);
    resolver.register(scala.collection.immutable.ArraySeq$.class);

    // Set
    resolver.register(scala.collection.immutable.Set.class);
    // IterableFactory
    resolver.register(scala.collection.immutable.Set$.class);
    resolver.register(scala.collection.immutable.Set.Set1.class);
    resolver.register(scala.collection.immutable.Set.Set2.class);
    resolver.register(scala.collection.immutable.Set.Set3.class);
    resolver.register(scala.collection.immutable.Set.Set4.class);
    resolver.register(scala.collection.immutable.HashSet.class);
    resolver.register(scala.collection.immutable.TreeSet.class);
    // SortedIterableFactory
    resolver.register(scala.collection.immutable.TreeSet$.class);
    // IterableFactory
    resolver.register(scala.collection.immutable.HashSet$.class);
    resolver.register(scala.collection.immutable.ListSet.class);
    resolver.register(scala.collection.immutable.ListSet$.class);
    resolver.register("scala.collection.immutable.Set$EmptySet$");
    resolver.register("scala.collection.immutable.SetBuilderImpl");
    resolver.register("scala.collection.immutable.SortedMapOps$ImmutableKeySortedSet");

    // Map
    resolver.register(scala.collection.immutable.Map.class);
    resolver.register(scala.collection.immutable.Map$.class);
    resolver.register(scala.collection.immutable.Map.Map1.class);
    resolver.register(scala.collection.immutable.Map.Map2.class);
    resolver.register(scala.collection.immutable.Map.Map3.class);
    resolver.register(scala.collection.immutable.Map.Map4.class);
    resolver.register(scala.collection.immutable.Map.WithDefault.class);
    resolver.register("scala.collection.immutable.MapBuilderImpl");
    resolver.register("scala.collection.immutable.Map$EmptyMap$");
    resolver.register("scala.collection.immutable.SeqMap$EmptySeqMap$");
    resolver.register(scala.collection.immutable.HashMap.class);
    resolver.register(scala.collection.immutable.HashMap$.class);
    resolver.register(scala.collection.immutable.TreeMap.class);
    resolver.register(scala.collection.immutable.TreeMap$.class);
    resolver.register(scala.collection.immutable.SortedMap$.class);
    resolver.register(scala.collection.immutable.TreeSeqMap.class);
    resolver.register(scala.collection.immutable.TreeSeqMap$.class);
    resolver.register(scala.collection.immutable.ListMap.class);
    resolver.register(scala.collection.immutable.ListMap$.class);
    resolver.register(scala.collection.immutable.IntMap.class);
    resolver.register(scala.collection.immutable.IntMap$.class);
    resolver.register(scala.collection.immutable.LongMap.class);
    resolver.register(scala.collection.immutable.LongMap$.class);

    // Range
    resolver.register("scala.math.Numeric$IntIsIntegral$");
    resolver.register("scala.math.Numeric$LongIsIntegral$");
    resolver.registerSerializerAndType(
        Range.Inclusive.class, new RangeSerializer(resolver, Range.Inclusive.class));
    resolver.registerSerializerAndType(
        Range.Exclusive.class, new RangeSerializer(resolver, Range.Exclusive.class));
    resolver.registerSerializerAndType(
        NumericRange.class, new NumericRangeSerializer<>(resolver, NumericRange.class));
    resolver.registerSerializerAndType(
        NumericRange.Exclusive.class,
        new NumericRangeSerializer<>(resolver, NumericRange.Exclusive.class));
    resolver.registerSerializerAndType(
        NumericRange.Inclusive.class,
        new NumericRangeSerializer<>(resolver, NumericRange.Inclusive.class));

    resolver.register(scala.collection.generic.SerializeEnd$.class);
    resolver.register(scala.collection.generic.DefaultSerializationProxy.class);
    resolver.register(scala.runtime.ModuleSerializationProxy.class);

    // mutable collection types
    resolver.register(scala.collection.mutable.StringBuilder.class);
    resolver.register(scala.collection.mutable.ArrayBuffer.class);
    resolver.register(scala.collection.mutable.ArrayBuffer$.class);
    resolver.register(scala.collection.mutable.ArraySeq.class);
    resolver.register(scala.collection.mutable.ArraySeq$.class);
    resolver.register(scala.collection.mutable.ListBuffer.class);
    resolver.register(scala.collection.mutable.ListBuffer$.class);
    resolver.register(scala.collection.mutable.Buffer$.class);
    resolver.register(scala.collection.mutable.ArrayDeque.class);
    resolver.register(scala.collection.mutable.ArrayDeque$.class);

    resolver.register(scala.collection.mutable.HashSet.class);
    resolver.register(scala.collection.mutable.HashSet$.class);
    resolver.register(scala.collection.mutable.TreeSet.class);
    resolver.register(scala.collection.mutable.TreeSet$.class);

    resolver.register(scala.collection.mutable.HashMap.class);
    resolver.register(scala.collection.mutable.HashMap$.class);
    resolver.register(scala.collection.mutable.TreeMap.class);
    resolver.register(scala.collection.mutable.TreeMap$.class);
    resolver.register(scala.collection.mutable.LinkedHashMap.class);
    resolver.register(scala.collection.mutable.LinkedHashMap$.class);
    resolver.register(scala.collection.mutable.LinkedHashSet.class);
    resolver.register(scala.collection.mutable.LinkedHashSet$.class);
    resolver.register(scala.collection.mutable.LongMap.class);
    resolver.register(scala.collection.mutable.LongMap$.class);

    resolver.register(scala.collection.mutable.Queue.class);
    resolver.register(scala.collection.mutable.Queue$.class);
    resolver.register(scala.collection.mutable.Stack.class);
    resolver.register(scala.collection.mutable.Stack$.class);
      resolver.register(scala.collection.mutable.BitSet.class);
      resolver.register(scala.collection.mutable.BitSet$.class);
    } catch (RuntimeException | Error e) {
      synchronized (INSTALLED_FORY) {
        INSTALLED_FORY.remove(fory);
      }
      throw e;
    }
  }

  public static void registerEnum(Fory fory, Class<?> cls, long typeId) {
    TypeResolver resolver = fory.getTypeResolver();
    checkRegistrationOpen(resolver);
    ScalaEnumSerializer serializer = new ScalaEnumSerializer(resolver, cls);
    Object[] values = serializer.getEnumConstants();
    resolver.registerEnum(cls, typeId, serializer);
    registerEnumRuntimeAliases(fory, cls, values);
  }

  private static String[] splitName(String name) {
    Objects.requireNonNull(name, "name");
    int idx = name.lastIndexOf('.');
    String namespace = "";
    String typeName = name;
    if (idx >= 0) {
      namespace = name.substring(0, idx);
      typeName = name.substring(idx + 1);
    }
    if (typeName.isEmpty()) {
      throw new IllegalArgumentException("Name must include a non-empty type name");
    }
    return new String[] {namespace, typeName};
  }

  private static void checkTypeName(String typeName) {
    if (typeName == null || typeName.isEmpty() || typeName.contains(".")) {
      throw new IllegalArgumentException(
          "typeName must be non-empty and must not contain `.` when namespace is provided");
    }
  }

  public static void registerEnum(Fory fory, Class<?> cls, String name) {
    TypeResolver resolver = fory.getTypeResolver();
    String[] parts = splitName(name);
    checkRegistrationOpen(resolver);
    ScalaEnumSerializer serializer = new ScalaEnumSerializer(resolver, cls);
    Object[] values = serializer.getEnumConstants();
    resolver.registerEnum(cls, parts[0], parts[1], serializer);
    registerEnumRuntimeAliases(fory, cls, values);
  }

  public static void registerEnum(Fory fory, Class<?> cls, String namespace, String typeName) {
    checkTypeName(typeName);
    TypeResolver resolver = fory.getTypeResolver();
    checkRegistrationOpen(resolver);
    ScalaEnumSerializer serializer = new ScalaEnumSerializer(resolver, cls);
    Object[] values = serializer.getEnumConstants();
    resolver.registerEnum(cls, namespace, typeName, serializer);
    registerEnumRuntimeAliases(fory, cls, values);
  }

  @Internal
  public static void registerRuntimeTypeAlias(
      Fory fory, Class<?> runtimeClass, Class<?> canonicalClass) {
    fory.getTypeResolver().registerRuntimeTypeAlias(runtimeClass, canonicalClass);
  }

  private static void registerEnumRuntimeAliases(Fory fory, Class<?> cls, Object[] values) {
    for (Object value : values) {
      Class<?> runtimeClass = value.getClass();
      if (runtimeClass != cls) {
        registerRuntimeTypeAlias(fory, runtimeClass, cls);
      }
    }
  }

  private static void checkRegistrationOpen(TypeResolver resolver) {
    if (resolver.isRegistrationFinished()) {
      throw new ForyException(
          "Cannot register class/serializer after registration has been frozen. Please register "
              + "all classes before invoking top-level `serialize/deserialize/copy` methods of "
              + "Fory.");
    }
  }
}
