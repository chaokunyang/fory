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

package org.apache.fory.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.builder.Generated;
import org.apache.fory.exception.ForyException;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.ArraySerializers;
import org.apache.fory.serializer.BufferSerializers;
import org.apache.fory.serializer.CodegenSerializer;
import org.apache.fory.serializer.EnumSerializer;
import org.apache.fory.serializer.ExternalizableSerializer;
import org.apache.fory.serializer.JavaSerializer;
import org.apache.fory.serializer.JdkProxySerializer;
import org.apache.fory.serializer.LambdaSerializer;
import org.apache.fory.serializer.ObjectSerializer;
import org.apache.fory.serializer.ObjectStreamSerializer;
import org.apache.fory.serializer.ReplaceResolveSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.Serializers;
import org.apache.fory.serializer.TimeSerializers;
import org.apache.fory.serializer.UnknownClassSerializers;
import org.apache.fory.serializer.collection.ChildContainerSerializers;
import org.apache.fory.serializer.collection.CollectionSerializer;
import org.apache.fory.serializer.collection.CollectionSerializers;
import org.apache.fory.serializer.collection.MapSerializer;
import org.apache.fory.serializer.collection.MapSerializers;
import org.apache.fory.serializer.scala.SingletonCollectionSerializer;
import org.apache.fory.serializer.scala.SingletonMapSerializer;
import org.apache.fory.serializer.scala.SingletonObjectSerializer;
import org.apache.fory.util.record.RecordUtils;

/** A helper for Graalvm native image support. */
public class GraalvmSupport {
  // https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageInfo.java
  public static final boolean IN_GRAALVM_NATIVE_IMAGE;
  // We can't cache code key or isBuildTime as static constant, since this class will be initialized
  // at build time,
  // and will be still build time value when it's graalvm runtime actually.
  private static final String GRAAL_IMAGE_CODE_KEY = "org.graalvm.nativeimage.imagecode";
  private static final String GRAAL_IMAGE_BUILDTIME = "buildtime";
  private static final String GRAAL_IMAGE_RUNTIME = "runtime";

  private static final Map<Integer, GraalvmClassRegistry> GRAALVM_REGISTRY =
      new ConcurrentHashMap<>();
  private static final Set<Class<? extends Serializer>> DEFAULT_SERIALIZER_CLASSES =
      new LinkedHashSet<>();

  static {
    String imageCode = System.getProperty(GRAAL_IMAGE_CODE_KEY);
    IN_GRAALVM_NATIVE_IMAGE = imageCode != null;
    registerDefaultSerializerClass(ArraySerializers.ObjectArraySerializer.class);
    registerDefaultSerializerClass(ArraySerializers.UnknownArraySerializer.class);
    registerDefaultSerializerClass(EnumSerializer.class);
    registerDefaultSerializerClass(UnknownClassSerializers.UnknownEnumSerializer.class);
    registerDefaultSerializerClass(CollectionSerializers.EnumSetSerializer.class);
    registerDefaultSerializerClass(Serializers.CharsetSerializer.class);
    registerDefaultSerializerClass(ReplaceResolveSerializer.class);
    registerDefaultSerializerClass(JdkProxySerializer.class);
    registerDefaultSerializerClass(LambdaSerializer.class);
    registerDefaultSerializerClass(TimeSerializers.CalendarSerializer.class);
    registerDefaultSerializerClass(TimeSerializers.ZoneIdSerializer.class);
    registerDefaultSerializerClass(TimeSerializers.TimeZoneSerializer.class);
    registerDefaultSerializerClass(BufferSerializers.ByteBufferSerializer.class);
    registerDefaultSerializerClass(MapSerializers.StringKeyMapSerializer.class);
    registerDefaultSerializerClass(ChildContainerSerializers.ChildArrayListSerializer.class);
    registerDefaultSerializerClass(ChildContainerSerializers.ChildCollectionSerializer.class);
    registerDefaultSerializerClass(ChildContainerSerializers.ChildMapSerializer.class);
    registerDefaultSerializerClass(SingletonCollectionSerializer.class);
    registerDefaultSerializerClass(SingletonMapSerializer.class);
    registerDefaultSerializerClass(SingletonObjectSerializer.class);
    registerDefaultSerializerClass(CollectionSerializers.JDKCompatibleCollectionSerializer.class);
    registerDefaultSerializerClass(CollectionSerializers.DefaultJavaCollectionSerializer.class);
    registerDefaultSerializerClass(CollectionSerializer.class);
    registerDefaultSerializerClass(MapSerializers.JDKCompatibleMapSerializer.class);
    registerDefaultSerializerClass(MapSerializers.DefaultJavaMapSerializer.class);
    registerDefaultSerializerClass(MapSerializer.class);
    registerDefaultSerializerClass(CodegenSerializer.LazyInitBeanSerializer.class);
    registerDefaultSerializerClass(ExternalizableSerializer.class);
    registerDefaultSerializerClass(ObjectStreamSerializer.class);
    registerDefaultSerializerClass(JavaSerializer.class);
    registerDefaultSerializerClass(ObjectSerializer.class);
  }

  /** Returns true if current process is running in graalvm native image build stage. */
  public static boolean isGraalBuildtime() {
    return IN_GRAALVM_NATIVE_IMAGE
        && GRAAL_IMAGE_BUILDTIME.equals(System.getProperty(GRAAL_IMAGE_CODE_KEY));
  }

  /** Returns true if current process is running in graalvm native image runtime stage. */
  public static boolean isGraalRuntime() {
    return IN_GRAALVM_NATIVE_IMAGE
        && GRAAL_IMAGE_RUNTIME.equals(System.getProperty(GRAAL_IMAGE_CODE_KEY));
  }

  /** Returns all classes registered for GraalVM native image compilation. */
  public static Set<Class<?>> getRegisteredClasses() {
    Set<Class<?>> allClasses = ConcurrentHashMap.newKeySet();
    for (GraalvmClassRegistry registry : GRAALVM_REGISTRY.values()) {
      allClasses.addAll(registry.registeredClasses);
    }
    return Collections.unmodifiableSet(allClasses);
  }

  /** Returns all proxy interfaces registered for GraalVM native image compilation. */
  public static Set<Class<?>> getProxyInterfaces() {
    Set<Class<?>> allInterfaces = ConcurrentHashMap.newKeySet();
    for (GraalvmClassRegistry registry : GRAALVM_REGISTRY.values()) {
      for (List<Class<?>> proxyInterfaceList : registry.proxyInterfaceLists) {
        allInterfaces.addAll(proxyInterfaceList);
      }
    }
    return Collections.unmodifiableSet(allInterfaces);
  }

  /** Returns all proxy interface lists registered for GraalVM native image compilation. */
  public static Set<List<Class<?>>> getProxyInterfaceLists() {
    Set<List<Class<?>>> allInterfaceLists = ConcurrentHashMap.newKeySet();
    for (GraalvmClassRegistry registry : GRAALVM_REGISTRY.values()) {
      allInterfaceLists.addAll(registry.proxyInterfaceLists);
    }
    return Collections.unmodifiableSet(allInterfaceLists);
  }

  /** Returns all serializer classes registered for GraalVM native image compilation. */
  public static Set<Class<? extends Serializer>> getRegisteredSerializerClasses() {
    Set<Class<? extends Serializer>> serializerClasses = ConcurrentHashMap.newKeySet();
    serializerClasses.addAll(DEFAULT_SERIALIZER_CLASSES);
    for (GraalvmClassRegistry registry : GRAALVM_REGISTRY.values()) {
      serializerClasses.addAll(registry.serializerClasses);
      serializerClasses.addAll(registry.serializerClassMap.values());
      serializerClasses.addAll(registry.deserializerClassMap.values());
    }
    return Collections.unmodifiableSet(serializerClasses);
  }

  /**
   * Returns serializer classes that are safe to initialize at GraalVM build time.
   *
   * <p>Generated codec classes must remain runtime-initialized.
   */
  public static Set<Class<? extends Serializer>> getBuildTimeInitializedSerializerClasses() {
    Set<Class<? extends Serializer>> serializerClasses = ConcurrentHashMap.newKeySet();
    for (Class<? extends Serializer> serializerClass : getRegisteredSerializerClasses()) {
      if (!Generated.class.isAssignableFrom(serializerClass)) {
        serializerClasses.add(serializerClass);
      }
    }
    return Collections.unmodifiableSet(serializerClasses);
  }

  /** Clears all GraalVM native image registrations. Primarily for testing purposes. */
  public static void clearRegistrations() {
    for (GraalvmClassRegistry registry : GRAALVM_REGISTRY.values()) {
      registry.registeredClasses.clear();
      registry.proxyInterfaceLists.clear();
      registry.serializerClasses.clear();
      registry.serializerClassMap.clear();
      registry.deserializerClassMap.clear();
      registry.layerSerializerClassMap.clear();
      registry.resolvers.clear();
    }
  }

  /**
   * Register a class in the GraalVM registry for native image compilation.
   *
   * @param cls the class to register
   * @param configHash the configuration hash for the Fory instance
   */
  public static void registerClass(Class<?> cls, int configHash) {
    if (!IN_GRAALVM_NATIVE_IMAGE) {
      return;
    }
    GraalvmClassRegistry registry =
        GRAALVM_REGISTRY.computeIfAbsent(configHash, k -> new GraalvmClassRegistry());
    registry.registeredClasses.add(cls);
  }

  /**
   * Register a serializer class in the GraalVM registry for native image compilation.
   *
   * @param serializerClass the serializer class to register
   * @param configHash the configuration hash for the Fory instance
   */
  public static void registerSerializerClass(
      Class<? extends Serializer> serializerClass, int configHash) {
    if (!IN_GRAALVM_NATIVE_IMAGE) {
      return;
    }
    GraalvmClassRegistry registry =
        GRAALVM_REGISTRY.computeIfAbsent(configHash, k -> new GraalvmClassRegistry());
    registry.serializerClasses.add(serializerClass);
  }

  /**
   * Register a generated object-stream layer serializer class in the GraalVM registry.
   *
   * @param typeDefId the layer TypeDef id
   * @param serializerClass the generated serializer class
   * @param configHash the configuration hash for the Fory instance
   */
  public static void registerLayerSerializerClass(
      long typeDefId, Class<? extends Serializer> serializerClass, int configHash) {
    if (!IN_GRAALVM_NATIVE_IMAGE) {
      return;
    }
    GraalvmClassRegistry registry =
        GRAALVM_REGISTRY.computeIfAbsent(configHash, k -> new GraalvmClassRegistry());
    registry.layerSerializerClassMap.put(typeDefId, serializerClass);
    registry.serializerClasses.add(serializerClass);
  }

  /** Returns the generated object-stream layer serializer class for the given TypeDef id. */
  public static Class<? extends Serializer> getLayerSerializerClass(long typeDefId, int configHash) {
    return getClassRegistry(configHash).layerSerializerClassMap.get(typeDefId);
  }

  /**
   * Register a proxy interface in the GraalVM registry for native image compilation.
   *
   * @param proxyInterface the proxy interface to register
   * @param configHash the configuration hash for the Fory instance
   */
  public static void registerProxyInterface(Class<?> proxyInterface, int configHash) {
    registerProxyInterfaces(configHash, proxyInterface);
  }

  private static void registerProxyInterfaces(
      int configHash, Class<?> proxyInterface, Class<?>... otherProxyInterfaces) {
    if (!IN_GRAALVM_NATIVE_IMAGE) {
      return;
    }
    ArrayList<Class<?>> proxyInterfaceList = new ArrayList<>(otherProxyInterfaces.length + 1);
    LinkedHashSet<Class<?>> deduplicatedInterfaces =
        new LinkedHashSet<>(otherProxyInterfaces.length + 1);
    addProxyInterface(proxyInterfaceList, deduplicatedInterfaces, proxyInterface);
    for (Class<?> otherProxyInterface : otherProxyInterfaces) {
      addProxyInterface(proxyInterfaceList, deduplicatedInterfaces, otherProxyInterface);
    }
    GraalvmClassRegistry registry =
        GRAALVM_REGISTRY.computeIfAbsent(configHash, k -> new GraalvmClassRegistry());
    registry.proxyInterfaceLists.add(Collections.unmodifiableList(proxyInterfaceList));
  }

  /**
   * Register proxy support for GraalVM native image compilation.
   *
   * @param proxyInterface the proxy interface to register
   */
  public static void registerProxySupport(Class<?> proxyInterface) {
    registerProxyInterface(proxyInterface, 0);
  }

  /**
   * Register proxy support for a GraalVM native image proxy that implements multiple interfaces.
   *
   * <p>The interface order must match the order used in {@code Proxy.newProxyInstance(...)}.
   *
   * @param proxyInterface the first proxy interface to register
   * @param otherProxyInterfaces additional proxy interfaces to register in proxy definition order
   */
  public static void registerProxySupport(
      Class<?> proxyInterface, Class<?>... otherProxyInterfaces) {
    registerProxyInterfaces(0, proxyInterface, otherProxyInterfaces);
  }

  private static void addProxyInterface(
      List<Class<?>> proxyInterfaceList,
      Set<Class<?>> deduplicatedInterfaces,
      Class<?> proxyInterface) {
    if (proxyInterface == null) {
      throw new NullPointerException("Proxy interface must not be null");
    }
    if (!proxyInterface.isInterface()) {
      throw new IllegalArgumentException(
          "Proxy type must be an interface: " + proxyInterface.getName());
    }
    if (!deduplicatedInterfaces.add(proxyInterface)) {
      throw new IllegalArgumentException(
          "Duplicate proxy interface: " + proxyInterface.getName());
    }
    proxyInterfaceList.add(proxyInterface);
  }

  private static void registerDefaultSerializerClass(
      Class<? extends Serializer> serializerClass) {
    DEFAULT_SERIALIZER_CLASSES.add(serializerClass);
  }

  public static ForyException throwNoArgCtrException(Class<?> type) {
    throw new ForyException("Please provide a no-arg constructor for " + type);
  }

  public static boolean isRecordConstructorPublicAccessible(Class<?> type) {
    if (!RecordUtils.isRecord(type)) {
      return false;
    }

    try {
      Constructor<?>[] constructors = type.getDeclaredConstructors();
      for (Constructor<?> constructor : constructors) {
        if (Modifier.isPublic(constructor.getModifiers())) {
          Class<?>[] paramTypes = constructor.getParameterTypes();
          boolean allParamsPublic = true;
          for (Class<?> paramType : paramTypes) {
            if (!Modifier.isPublic(paramType.getModifiers())) {
              allParamsPublic = false;
              break;
            }
          }
          if (allParamsPublic) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      return false;
    }
    return false;
  }

  /**
   * Checks whether a class requires reflective instantiation handling in GraalVM.
   *
   * <p>Returns true only when Fory will instantiate the class through its declared no-arg
   * constructor and that constructor is not publicly accessible. Classes without a no-arg
   * constructor use the unsafe allocation path instead, so registering them for reflective
   * instantiation is both unnecessary and invalid for types such as JDK immutable collections.
   *
   * @param type the class to check
   * @return true if reflective instantiation handling is required, false otherwise
   */
  public static boolean needReflectionRegisterForCreation(Class<?> type) {
    if (type.isInterface()
        || Modifier.isAbstract(type.getModifiers())
        || type.isArray()
        || type.isEnum()
        || type.isAnonymousClass()
        || type.isLocalClass()) {
      return false;
    }
    Constructor<?>[] constructors = type.getDeclaredConstructors();
    if (constructors.length == 0) {
      return !Modifier.isPublic(type.getModifiers());
    }
    for (Constructor<?> constructor : constructors) {
      if (constructor.getParameterCount() == 0) {
        return !Modifier.isPublic(type.getModifiers())
            || !Modifier.isPublic(constructor.getModifiers());
      }
    }
    if (RecordUtils.isRecord(type)) {
      return !isRecordConstructorPublicAccessible(type);
    }
    return false;
  }

  /**
   * Get the GraalVM class registry for a specific configuration hash. Package-private method for
   * use by TypeResolver and ClassResolver.
   */
  public static GraalvmClassRegistry getClassRegistry(int configHash) {
    if (!IN_GRAALVM_NATIVE_IMAGE) {
      return new GraalvmClassRegistry();
    }
    return GRAALVM_REGISTRY.computeIfAbsent(configHash, k -> new GraalvmClassRegistry());
  }

  /** GraalVM class registry. */
  public static class GraalvmClassRegistry {
    public final List<TypeResolver> resolvers;
    public final Set<Class<? extends Serializer>> serializerClasses;
    public final Map<Class<?>, Class<? extends Serializer>> serializerClassMap;
    public final Map<Long, Class<? extends Serializer>> deserializerClassMap;
    public final Map<Long, Class<? extends Serializer>> layerSerializerClassMap;
    public final Set<Class<?>> registeredClasses;
    public final Set<List<Class<?>>> proxyInterfaceLists;

    private GraalvmClassRegistry() {
      resolvers = Collections.synchronizedList(new ArrayList<>());
      serializerClasses = ConcurrentHashMap.newKeySet();
      serializerClassMap = new ConcurrentHashMap<>();
      deserializerClassMap = new ConcurrentHashMap<>();
      layerSerializerClassMap = new ConcurrentHashMap<>();
      registeredClasses = ConcurrentHashMap.newKeySet();
      proxyInterfaceLists = ConcurrentHashMap.newKeySet();
    }
  }
}
