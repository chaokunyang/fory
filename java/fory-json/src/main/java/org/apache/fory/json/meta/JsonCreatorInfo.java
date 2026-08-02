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

package org.apache.fory.json.meta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.GeneratedJsonCodec;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._JDKAccess;

/**
 * Immutable ordered construction metadata for one JSON object codec.
 *
 * <p>Record canonical constructors, property-based {@code JsonCreator} constructors, and
 * property-based {@code JsonCreator} factories share this owner. The separate complete-string
 * {@code JsonValue} representation owns its value creator in its value codec. The interpreted path
 * allocates exactly one fixed-size argument array per object. Generated JIT readers consume the
 * same field metadata and executable but invoke it directly with typed locals.
 */
@Internal
public final class JsonCreatorInfo {
  private static final MethodHandle CONSTRUCTOR_REFLECTION_INVOKER =
      prepareConstructorReflectionInvoker();
  private static Map<Executable, MethodHandle> nativeInvokers = new HashMap<>();
  private static Map<Executable, MethodHandle> nativeStringInvokers = new HashMap<>();
  private static boolean nativeCreatorsFrozen;

  private final Class<?> ownerType;
  private final Executable executable;
  private final JsonCreatorFieldInfo[] fields;
  private final Object[] defaults;
  private final long[] hashes;
  private final MethodHandle invoker;
  private final GeneratedJsonCodec<?> generatedCodec;

  public JsonCreatorInfo(
      Class<?> ownerType,
      Executable executable,
      JsonCreatorFieldInfo[] fields,
      Object[] defaults,
      GeneratedJsonCodec<?> generatedCodec) {
    this.ownerType = ownerType;
    this.executable = executable;
    this.fields = fields;
    this.defaults = defaults;
    this.generatedCodec = generatedCodec;
    invoker =
        generatedCodec == null
            ? buildInvoker(ownerType, executable, executable.getParameterCount())
            : null;
    hashes = new long[fields.length];
    for (int i = 0; i < fields.length; i++) {
      hashes[i] = fields[i].nameHash();
    }
  }

  public Executable executable() {
    return executable;
  }

  public JsonCreatorFieldInfo[] fields() {
    return fields;
  }

  public Object[] newArguments() {
    return Arrays.copyOf(defaults, defaults.length);
  }

  public int index(long hash) {
    // Creator arity is deliberately finite and normally small. A linear exact-hash table avoids a
    // second object graph and is allocation-free.
    for (int i = 0; i < hashes.length; i++) {
      if (hashes[i] == hash) {
        return i;
      }
    }
    return -1;
  }

  public void resolveTypes(JsonTypeResolver resolver) {
    for (JsonCreatorFieldInfo field : fields) {
      field.resolveType(resolver);
    }
  }

  public Object create(Object[] arguments) {
    if (generatedCodec != null) {
      try {
        return requireResult(generatedCodec.newInstance(arguments));
      } catch (Throwable cause) {
        if (cause instanceof Error) {
          throw (Error) cause;
        }
        throw new ForyJsonException("JSON creator failed for " + ownerType.getName(), cause);
      }
    }
    if (invoker != null) {
      return invoke(arguments);
    }
    try {
      Object value =
          executable instanceof Constructor
              ? ((Constructor<?>) executable).newInstance(arguments)
              : ((Method) executable).invoke(null, arguments);
      return requireResult(value);
    } catch (InstantiationException | IllegalAccessException e) {
      throw new ForyJsonException("Failed to invoke JSON creator for " + ownerType.getName(), e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new ForyJsonException("JSON creator failed for " + ownerType.getName(), cause);
    }
  }

  private Object invoke(Object[] arguments) {
    Object value;
    try {
      value = (Object) invoker.invokeExact(arguments);
    } catch (Throwable cause) {
      cause = creatorCause(cause);
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new ForyJsonException("JSON creator failed for " + ownerType.getName(), cause);
    }
    return requireResult(value);
  }

  private Throwable creatorCause(Throwable cause) {
    return GraalvmSupport.isGraalRuntime()
            && JdkVersion.MAJOR_VERSION >= 25
            && executable instanceof Constructor
            && cause instanceof InvocationTargetException
        ? cause.getCause()
        : cause;
  }

  private Object requireResult(Object value) {
    if (value == null || value.getClass() != ownerType) {
      throw new ForyJsonException(
          "JSON creator must return an exact non-null " + ownerType.getName());
    }
    return value;
  }

  private static MethodHandle buildInvoker(
      Class<?> ownerType, Executable executable, int parameterCount) {
    if (AndroidSupport.IS_ANDROID) {
      // Android has no supported trusted MethodHandle lookup. Creator shape validation guarantees
      // a public executable; accessibility is needed only when its declaring class is non-public.
      executable.setAccessible(true);
      return null;
    }
    if (GraalvmSupport.isGraalRuntime()) {
      MethodHandle invoker = nativeInvokers.get(executable);
      if (invoker == null) {
        throw missingNativeCreator(executable);
      }
      return invoker;
    }
    MethodHandle target = creatorTarget(ownerType, executable);
    // The interpreted reader already owns one trusted fixed-size argument array. Spread that
    // exact array into the creator without a second carrier or per-call reflective access check.
    return target
        .asSpreader(Object[].class, parameterCount)
        .asType(MethodType.methodType(Object.class, Object[].class));
  }

  /** Returns the one-String-argument creator used by a JsonValue representation. */
  @Internal
  public static MethodHandle stringCreatorHandle(Class<?> ownerType, Executable executable) {
    if (GraalvmSupport.isGraalRuntime()) {
      MethodHandle invoker = nativeStringInvokers.get(executable);
      if (invoker == null) {
        throw missingNativeCreator(executable);
      }
      return invoker;
    }
    return creatorTarget(ownerType, executable)
        .asType(MethodType.methodType(Object.class, String.class));
  }

  /** Prepares the exact Native Image runtime handle for one registered creator. */
  @Internal
  public static synchronized void prepareNativeCreator(Class<?> ownerType, Executable executable) {
    if (!GraalvmSupport.isGraalBuildTime() || nativeCreatorsFrozen) {
      throw new IllegalStateException("Fory JSON native creator cache is not writable");
    }
    MethodHandle target;
    MethodHandle invoker;
    if (executable instanceof Constructor && JdkVersion.MAJOR_VERSION >= 25) {
      target = constructorReflectionTarget(ownerType, (Constructor<?>) executable);
      invoker = target;
    } else {
      target = creatorTarget(ownerType, executable);
      invoker =
          target
              .asSpreader(Object[].class, executable.getParameterCount())
              .asType(MethodType.methodType(Object.class, Object[].class));
    }
    nativeInvokers.putIfAbsent(executable, invoker);
    if (executable.getParameterCount() == 1 && executable.getParameterTypes()[0] == String.class) {
      if (!(executable instanceof Constructor) || JdkVersion.MAJOR_VERSION < 25) {
        nativeStringInvokers.putIfAbsent(
            executable, target.asType(MethodType.methodType(Object.class, String.class)));
      }
    }
  }

  /** Freezes all Native Image creator handles after hosted analysis. */
  @Internal
  public static synchronized void freezeNativeCreators() {
    if (nativeCreatorsFrozen) {
      return;
    }
    nativeInvokers = immutable(nativeInvokers);
    nativeStringInvokers = immutable(nativeStringInvokers);
    nativeCreatorsFrozen = true;
  }

  private static Map<Executable, MethodHandle> immutable(Map<Executable, MethodHandle> invokers) {
    return invokers.isEmpty()
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new HashMap<>(invokers));
  }

  private static ForyJsonException missingNativeCreator(Executable executable) {
    return new ForyJsonException(
        "Missing Native Image Fory JSON creator metadata for " + executable);
  }

  /** Returns the prepared array-argument creator used by GraalVM 25 constructors. */
  @Internal
  public static MethodHandle arrayCreatorHandle(Class<?> ownerType, Executable executable) {
    return buildInvoker(ownerType, executable, executable.getParameterCount());
  }

  private static MethodHandle constructorReflectionTarget(
      Class<?> ownerType, Constructor<?> constructor) {
    return MethodHandles.insertArguments(
        CONSTRUCTOR_REFLECTION_INVOKER.bindTo(constructor), 1, true, ownerType);
  }

  private static MethodHandle prepareConstructorReflectionInvoker() {
    if (!GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE || JdkVersion.MAJOR_VERSION < 25) {
      return null;
    }
    try {
      // GraalVM 25 implements direct constructor MethodHandles through a reflection bridge whose
      // synthetic caller cannot access concealed model packages. Preserve the executable's normal
      // public access contract by supplying its declaring class as the caller. The resulting
      // per-executable handle is prepared at image build time and performs no runtime lookup.
      return _JDKAccess._trustedLookup(Constructor.class)
          .findVirtual(
              Constructor.class,
              "newInstanceWithCaller",
              MethodType.methodType(Object.class, Object[].class, boolean.class, Class.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ForyJsonException("Cannot prepare GraalVM 25 JSON constructor invocation", e);
    }
  }

  private static MethodHandle creatorTarget(Class<?> ownerType, Executable executable) {
    try {
      // A target-class trusted lookup has full member access without requiring the application
      // module to export or open its model package.
      if (executable instanceof Constructor) {
        return _JDKAccess._trustedLookup(ownerType)
            .findConstructor(
                ownerType, MethodType.methodType(void.class, executable.getParameterTypes()));
      }
      Method factory = (Method) executable;
      return _JDKAccess._trustedLookup(factory.getDeclaringClass())
          .findStatic(
              factory.getDeclaringClass(),
              factory.getName(),
              MethodType.methodType(factory.getReturnType(), factory.getParameterTypes()));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ForyJsonException("Cannot access JSON creator for " + ownerType.getName(), e);
    }
  }
}
