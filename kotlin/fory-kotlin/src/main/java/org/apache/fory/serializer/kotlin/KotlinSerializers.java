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

package org.apache.fory.serializer.kotlin;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import kotlin.*;
import kotlin.UByteArray;
import kotlin.UIntArray;
import kotlin.ULongArray;
import kotlin.UShortArray;
import kotlin.text.*;
import kotlin.time.Duration;
import kotlin.time.DurationUnit;
import kotlin.time.TimedValue;
import kotlin.uuid.Uuid;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.builder.JITContext;
import org.apache.fory.codegen.GeneratedClassNames;
import org.apache.fory.config.Config;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.EnumSerializer;
import org.apache.fory.serializer.ReplaceResolveSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.Serializers;
import org.apache.fory.serializer.collection.CollectionSerializers;
import org.apache.fory.serializer.collection.MapSerializers;
import org.apache.fory.util.DefaultValueUtils;

/** KotlinSerializers provide default serializers for kotlin. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class KotlinSerializers {
  private static final String XLANG_GENERATED_SERIALIZER_SUFFIX = "_ForySerializer";
  private static final Map<Fory, Boolean> INSTALLED_FORY =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static void registerSerializers(ThreadSafeFory fory) {
    fory.register(KotlinSerializers::registerSerializers);
  }

  public static void registerSerializers(Fory fory) {
    synchronized (INSTALLED_FORY) {
      if (INSTALLED_FORY.containsKey(fory)) {
        return;
      }
      INSTALLED_FORY.put(fory, Boolean.TRUE);
    }
    try {
      DefaultValueUtils.setKotlinDefaultValueSupport(new KotlinDefaultValueSupport());
      TypeResolver resolver = fory.getTypeResolver();
      if (resolver.isCrossLanguage()) {
        return;
      }
      Config config = resolver.getConfig();

      // UByte
      Class ubyteClass = KotlinToJavaClass.INSTANCE.getUByteClass();
      registerIfAbsent(resolver, ubyteClass);
      resolver.registerSerializer(ubyteClass, new UByteSerializer(config));

      // UShort
      Class ushortClass = KotlinToJavaClass.INSTANCE.getUShortClass();
      registerIfAbsent(resolver, ushortClass);
      resolver.registerSerializer(ushortClass, new UShortSerializer(config));

      // UInt
      Class uintClass = KotlinToJavaClass.INSTANCE.getUIntClass();
      registerIfAbsent(resolver, uintClass);
      resolver.registerSerializer(uintClass, new UIntSerializer(config));

      // ULong
      Class ulongClass = KotlinToJavaClass.INSTANCE.getULongClass();
      registerIfAbsent(resolver, ulongClass);
      resolver.registerSerializer(ulongClass, new ULongSerializer(config));

      // EmptyList
      Class emptyListClass = KotlinToJavaClass.INSTANCE.getEmptyListClass();
      registerIfAbsent(resolver, emptyListClass);
      resolver.registerSerializer(
          emptyListClass, new CollectionSerializers.EmptyListSerializer(resolver, emptyListClass));

      // EmptySet
      Class emptySetClass = KotlinToJavaClass.INSTANCE.getEmptySetClass();
      registerIfAbsent(resolver, emptySetClass);
      resolver.registerSerializer(
          emptySetClass, new CollectionSerializers.EmptySetSerializer(resolver, emptySetClass));

      // EmptyMap
      Class emptyMapClass = KotlinToJavaClass.INSTANCE.getEmptyMapClass();
      registerIfAbsent(resolver, emptyMapClass);
      resolver.registerSerializer(
          emptyMapClass, new MapSerializers.EmptyMapSerializer(resolver, emptyMapClass));

      // Non-Java collection implementation in kotlin stdlib.
      Class arrayDequeClass = KotlinToJavaClass.INSTANCE.getArrayDequeClass();
      registerIfAbsent(resolver, arrayDequeClass);
      resolver.registerSerializer(
          arrayDequeClass, new KotlinArrayDequeSerializer(resolver, arrayDequeClass));

      // Unsigned array classes: UByteArray, UShortArray, UIntArray, ULongArray.
      registerIfAbsent(resolver, UByteArray.class);
      resolver.registerSerializer(UByteArray.class, new UByteArraySerializer(resolver));
      registerIfAbsent(resolver, UShortArray.class);
      resolver.registerSerializer(UShortArray.class, new UShortArraySerializer(resolver));
      registerIfAbsent(resolver, UIntArray.class);
      resolver.registerSerializer(UIntArray.class, new UIntArraySerializer(resolver));
      registerIfAbsent(resolver, ULongArray.class);
      resolver.registerSerializer(ULongArray.class, new ULongArraySerializer(resolver));

      // Ranges and Progressions.
      registerIfAbsent(resolver, kotlin.ranges.CharRange.class);
      registerIfAbsent(resolver, kotlin.ranges.CharProgression.class);
      registerIfAbsent(resolver, kotlin.ranges.IntRange.class);
      registerIfAbsent(resolver, kotlin.ranges.IntProgression.class);
      registerIfAbsent(resolver, kotlin.ranges.LongRange.class);
      registerIfAbsent(resolver, kotlin.ranges.LongProgression.class);
      registerIfAbsent(resolver, kotlin.ranges.UIntRange.class);
      registerIfAbsent(resolver, kotlin.ranges.UIntProgression.class);
      registerIfAbsent(resolver, kotlin.ranges.ULongRange.class);
      registerIfAbsent(resolver, kotlin.ranges.ULongProgression.class);

      // Built-in classes.
      registerIfAbsent(resolver, kotlin.Pair.class);
      registerIfAbsent(resolver, kotlin.Triple.class);
      registerIfAbsent(resolver, kotlin.Result.class);
      registerIfAbsent(resolver, Result.Failure.class);

      // kotlin.random
      registerIfAbsent(resolver, KotlinToJavaClass.INSTANCE.getRandomDefaultClass());
      registerIfAbsent(resolver, KotlinToJavaClass.INSTANCE.getRandomInternalClass());
      registerIfAbsent(resolver, KotlinToJavaClass.INSTANCE.getRandomSerializedClass());

      // kotlin.text
      Class regexSerializedClass = KotlinToJavaClass.INSTANCE.getRegexSerializedClass();
      // Register both types before serializer construction can resolve the carrier's data codec.
      registerIfAbsent(resolver, Regex.class);
      registerIfAbsent(resolver, regexSerializedClass);
      resolver.registerSerializer(
          regexSerializedClass, new KotlinRegexSerializer(resolver, regexSerializedClass));
      registerIfAbsent(resolver, RegexOption.class);
      registerIfAbsent(resolver, CharCategory.class);
      registerIfAbsent(resolver, CharDirectionality.class);
      registerIfAbsent(resolver, HexFormat.class);
      registerIfAbsent(resolver, MatchGroup.class);

      // kotlin.time
      registerIfAbsent(resolver, DurationUnit.class);
      registerIfAbsent(resolver, Duration.class);
      resolver.registerSerializer(Duration.class, new DurationSerializer(config));
      registerIfAbsent(resolver, TimedValue.class);

      // kotlin.uuid
      registerIfAbsent(resolver, Uuid.class);
      resolver.registerSerializer(Uuid.class, new UuidSerializer(config));
    } catch (RuntimeException | Error e) {
      synchronized (INSTALLED_FORY) {
        INSTALLED_FORY.remove(fory);
      }
      throw e;
    }
  }

  private static void registerIfAbsent(TypeResolver resolver, Class<?> cls) {
    if (!resolver.isRegistered(cls)) {
      resolver.register(cls);
    }
  }

  private static final class KotlinRegexSerializer extends ReplaceResolveSerializer {
    private KotlinRegexSerializer(TypeResolver resolver, Class<?> type) {
      super(resolver, type);
      // The registered carrier owns this guard. Replacement stubs share its existing cache before
      // invoking readResolve, so changing the outer wire type cannot select an unguarded data
      // codec.
      jdkMethodInfoWriteCache.setObjectSerializer(new KotlinRegexCarrierSerializer(resolver, type));
    }
  }

  private static final class KotlinRegexCarrierSerializer extends Serializer<Object> {
    private final ClassResolver resolver;
    private final FieldAccessor flagsAccessor;
    private volatile Serializer<Object> delegate;

    private KotlinRegexCarrierSerializer(TypeResolver resolver, Class<?> type) {
      super(resolver.getConfig(), (Class<Object>) type);
      this.resolver = (ClassResolver) resolver;
      try {
        flagsAccessor = FieldAccessor.createAccessor(type.getDeclaredField("flags"));
      } catch (NoSuchFieldException e) {
        throw new ForyException("Failed to access Kotlin Regex serialized flags", e);
      }
    }

    @Override
    public void write(WriteContext writeContext, Object value) {
      delegate().write(writeContext, value);
    }

    @Override
    public Object read(ReadContext readContext) {
      Object value = delegate().read(readContext);
      int flags = flagsAccessor.getInt(value);
      if ((flags & Pattern.CANON_EQ) != 0) {
        throwUnsupportedRegexFlags(flags);
      }
      return value;
    }

    @Override
    public Object copy(CopyContext copyContext, Object value) {
      return delegate().copy(copyContext, value);
    }

    private Serializer<Object> delegate() {
      Serializer<Object> serializer = delegate;
      return serializer == null ? initializeDelegate() : serializer;
    }

    private synchronized Serializer<Object> initializeDelegate() {
      Serializer<Object> serializer = delegate;
      if (serializer == null) {
        Class<? extends Serializer> serializerClass =
            resolver.getObjectSerializerClass(
                type,
                new JITContext.SerializerJITCallback<Class<? extends Serializer>>() {
                  @Override
                  public void onSuccess(Class<? extends Serializer> result) {
                    // Keep the guard in MethodInfoCache; compilation may replace only its delegate.
                    setDelegate(result);
                  }

                  @Override
                  public Object id() {
                    return KotlinRegexCarrierSerializer.this;
                  }
                });
        serializer = newDelegate(serializerClass);
        if (delegate == null) {
          delegate = serializer;
        } else {
          serializer = delegate;
        }
      }
      return serializer;
    }

    private synchronized void setDelegate(Class<? extends Serializer> serializerClass) {
      delegate = newDelegate(serializerClass);
    }

    private Serializer<Object> newDelegate(Class<? extends Serializer> serializerClass) {
      Serializer<Object> previous = resolver.getSerializer(type, false);
      try {
        return Serializers.newSerializer(resolver, type, serializerClass);
      } finally {
        resolver.resetSerializer(type, previous);
      }
    }

    private static void throwUnsupportedRegexFlags(int flags) {
      throw new InsecureException(
          "Kotlin Regex flags must not include CANON_EQ during deserialization: " + flags);
    }
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

  public static void registerType(Fory fory, Class<?> cls, long typeId) {
    fory.getTypeResolver().register(cls, typeId);
  }

  public static void registerType(Fory fory, Class<?> cls, String name) {
    fory.register(cls, name);
  }

  public static void registerType(Fory fory, Class<?> cls, String namespace, String typeName) {
    checkTypeName(typeName);
    fory.getTypeResolver().register(cls, namespace, typeName);
  }

  public static void register(Fory fory, Class<?> cls) {
    fory.register(cls);
    registerSerializer(fory, cls);
  }

  public static void register(Fory fory, Class<?> cls, long typeId) {
    registerType(fory, cls, typeId);
    registerSerializer(fory, cls);
  }

  public static void register(Fory fory, Class<?> cls, String name) {
    registerType(fory, cls, name);
    registerSerializer(fory, cls);
  }

  public static void register(Fory fory, Class<?> cls, String namespace, String typeName) {
    registerType(fory, cls, namespace, typeName);
    registerSerializer(fory, cls);
  }

  public static void registerSerializer(Fory fory, Class<?> cls) {
    TypeResolver resolver = fory.getTypeResolver();
    Serializer serializer = newGeneratedSerializer(resolver, cls);
    if (resolver.isRegistered(cls)) {
      resolver.setSerializer(cls, serializer);
    } else {
      resolver.registerSerializer(cls, serializer);
    }
  }

  public static void registerEnum(Fory fory, Class<?> cls, long typeId) {
    TypeResolver resolver = fory.getTypeResolver();
    resolver.registerEnum(cls, typeId, new EnumSerializer(resolver.getConfig(), enumClass(cls)));
  }

  public static void registerEnum(Fory fory, Class<?> cls, String namespace, String typeName) {
    checkTypeName(typeName);
    TypeResolver resolver = fory.getTypeResolver();
    resolver.registerEnum(
        cls, namespace, typeName, new EnumSerializer(resolver.getConfig(), enumClass(cls)));
  }

  public static void registerEnum(Fory fory, Class<?> cls, String name) {
    TypeResolver resolver = fory.getTypeResolver();
    String[] parts = splitName(name);
    resolver.registerEnum(
        cls, parts[0], parts[1], new EnumSerializer(resolver.getConfig(), enumClass(cls)));
  }

  public static void registerUnion(Fory fory, Class<?> cls, long typeId) {
    TypeResolver resolver = fory.getTypeResolver();
    resolver.registerUnion(cls, typeId, newGeneratedSerializer(resolver, cls));
    registerCaseAliases(fory, cls);
  }

  public static void registerUnion(Fory fory, Class<?> cls, String namespace, String typeName) {
    checkTypeName(typeName);
    TypeResolver resolver = fory.getTypeResolver();
    resolver.registerUnion(cls, namespace, typeName, newGeneratedSerializer(resolver, cls));
    registerCaseAliases(fory, cls);
  }

  public static void registerUnion(Fory fory, Class<?> cls, String name) {
    TypeResolver resolver = fory.getTypeResolver();
    String[] parts = splitName(name);
    resolver.registerUnion(cls, parts[0], parts[1], newGeneratedSerializer(resolver, cls));
    registerCaseAliases(fory, cls);
  }

  private static Serializer<?> newGeneratedSerializer(TypeResolver resolver, Class<?> cls) {
    Class<? extends Serializer> serializerClass =
        generatedSerializerClass(cls).asSubclass(Serializer.class);
    try {
      return serializerClass
          .getConstructor(TypeResolver.class, Class.class)
          .newInstance(resolver, cls);
    } catch (ReflectiveOperationException e) {
      throw new ForyException(
          "Failed to create generated Kotlin serializer "
              + serializerClass.getName()
              + " for "
              + cls.getName(),
          e);
    }
  }

  private static Class<?> generatedSerializerClass(Class<?> cls) {
    String generatedName = generatedSerializerBinaryName(cls);
    try {
      return Class.forName(generatedName, false, cls.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new ForyException(
          "Missing generated Kotlin xlang serializer "
              + generatedName
              + " for "
              + cls.getName()
              + ". Enable fory-kotlin-ksp for generated Kotlin IDL sources.",
          e);
    }
  }

  private static String generatedSerializerBinaryName(Class<?> cls) {
    return GeneratedClassNames.withSuffix(cls.getName(), XLANG_GENERATED_SERIALIZER_SUFFIX);
  }

  @SuppressWarnings("unchecked")
  private static Class<Enum> enumClass(Class<?> cls) {
    if (!Enum.class.isAssignableFrom(cls)) {
      throw new IllegalArgumentException("Kotlin enum registration target must be an enum: " + cls);
    }
    return (Class<Enum>) cls;
  }

  private static void registerCaseAliases(Fory fory, Class<?> canonicalClass) {
    for (Class<?> nestedClass : canonicalClass.getDeclaredClasses()) {
      if (canonicalClass.isAssignableFrom(nestedClass)) {
        fory.getTypeResolver().registerRuntimeTypeAlias(nestedClass, canonicalClass);
      }
    }
  }
}
