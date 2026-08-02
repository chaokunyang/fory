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
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
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
  private static Map<Executable, MethodHandle> nativeInvokers = new HashMap<>();
  private static Map<Executable, MethodHandle> nativeStringInvokers = new HashMap<>();
  private static Map<Executable, Constructor<?>> nativeConstructors = new HashMap<>();
  private static boolean nativeCreatorsFrozen;

  private final Class<?> ownerType;
  private final Executable executable;
  private final JsonCreatorFieldInfo[] fields;
  private final Object[] defaults;
  private final long[] hashes;
  private final MethodHandle invoker;
  private final Constructor<?> nativeConstructor;
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
    nativeConstructor = generatedCodec == null ? nativeConstructor(executable) : null;
    invoker =
        generatedCodec == null && nativeConstructor == null && !GraalvmSupport.isGraalBuildTime()
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
      Object value;
      if (nativeConstructor != null) {
        value = nativeConstructor.newInstance(arguments);
      } else if (executable instanceof Constructor) {
        value = ((Constructor<?>) executable).newInstance(arguments);
      } else {
        value = ((Method) executable).invoke(null, arguments);
      }
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
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new ForyJsonException("JSON creator failed for " + ownerType.getName(), cause);
    }
    return requireResult(value);
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

  /** Returns the cached one-String-argument creator used by a JsonValue representation. */
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

  /** Returns the prepared Native Image constructor, or {@code null} outside native runtime. */
  @Internal
  public static Constructor<?> nativeConstructor(Executable executable) {
    if (!GraalvmSupport.isGraalRuntime() || !(executable instanceof Constructor)) {
      return null;
    }
    Constructor<?> constructor = nativeConstructors.get(executable);
    if (constructor == null) {
      throw missingNativeCreator(executable);
    }
    return constructor;
  }

  private static MethodHandle creatorTarget(Class<?> ownerType, Executable executable) {
    try {
      // A target-class trusted lookup has full member access without requiring the application
      // module to export or open its model package. Native Image retains final factory handles;
      // constructor creators use the separately registered Constructor cache.
      return executable instanceof Constructor
          ? _JDKAccess._trustedLookup(ownerType).unreflectConstructor((Constructor<?>) executable)
          : _JDKAccess._trustedLookup(executable.getDeclaringClass())
              .unreflect((Method) executable);
    } catch (IllegalAccessException e) {
      throw new ForyJsonException("Cannot access JSON creator for " + ownerType.getName(), e);
    }
  }

  private static ForyJsonException missingNativeCreator(Executable executable) {
    return new ForyJsonException(
        "Missing Native Image Fory JSON creator metadata for " + executable);
  }

  /** Prepares the Native Image runtime access for one object creator. */
  @Internal
  public static synchronized void prepareNativeCreator(Class<?> ownerType, Executable executable) {
    if (!GraalvmSupport.isGraalBuildTime() || nativeCreatorsFrozen) {
      throw new IllegalStateException("Fory JSON native creator cache is not writable");
    }
    if (executable instanceof Constructor) {
      Constructor<?> constructor = (Constructor<?>) executable;
      makeAccessible(constructor);
      nativeConstructors.putIfAbsent(executable, constructor);
      return;
    }
    MethodHandle target = creatorTarget(ownerType, executable);
    nativeInvokers.putIfAbsent(
        executable,
        target
            .asSpreader(Object[].class, executable.getParameterCount())
            .asType(MethodType.methodType(Object.class, Object[].class)));
    if (executable.getParameterCount() == 1 && executable.getParameterTypes()[0] == String.class) {
      nativeStringInvokers.putIfAbsent(
          executable, target.asType(MethodType.methodType(Object.class, String.class)));
    }
  }

  private static void makeAccessible(AccessibleObject member) {
    try {
      // setAccessible0 is the JDK's access-check-free operation. Invoking it through the trusted
      // lookup preserves access to closed application modules without an exports/opens contract.
      _JDKAccess._trustedLookup(AccessibleObject.class)
          .findVirtual(
              AccessibleObject.class,
              "setAccessible0",
              MethodType.methodType(boolean.class, boolean.class))
          .invoke(member, true);
    } catch (Throwable e) {
      throw new ForyJsonException("Cannot prepare Native Image JSON creator " + member, e);
    }
  }

  /** Freezes all Native Image object creator access after hosted analysis. */
  @Internal
  public static synchronized void freezeNativeCreators() {
    if (nativeCreatorsFrozen) {
      return;
    }
    nativeInvokers =
        nativeInvokers.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(nativeInvokers));
    nativeStringInvokers =
        nativeStringInvokers.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(nativeStringInvokers));
    nativeConstructors =
        nativeConstructors.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(nativeConstructors));
    nativeCreatorsFrozen = true;
  }
}
