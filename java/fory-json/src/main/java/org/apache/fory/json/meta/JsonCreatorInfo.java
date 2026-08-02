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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.GeneratedJsonCodec;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.util.ExceptionUtils;

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
  private static final MethodHandle NATIVE_CONSTRUCTOR_INVOKER =
      prepareNativeInvoker(
          Constructor.class,
          "newInstanceWithCaller",
          MethodType.methodType(Object.class, Object[].class, boolean.class, Class.class));
  private static final MethodHandle NATIVE_FACTORY_INVOKER =
      prepareNativeInvoker(
          Method.class,
          "invoke",
          MethodType.methodType(Object.class, Object.class, Object[].class, Class.class));

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
        generatedCodec == null && !GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE
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
      if (executable instanceof Constructor) {
        value = invokeConstructor((Constructor<?>) executable, arguments);
      } else {
        value = invokeFactory((Method) executable, arguments);
      }
      return requireResult(value);
    } catch (Throwable cause) {
      if (cause instanceof InvocationTargetException) {
        cause = cause.getCause();
      }
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
    return creatorTarget(ownerType, executable)
        .asType(MethodType.methodType(Object.class, String.class));
  }

  /** Invokes a creator constructor using the prepared Native Image access path when required. */
  @Internal
  public static Object invokeConstructor(Constructor<?> constructor, Object[] arguments) {
    if (!GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      try {
        return constructor.newInstance(arguments);
      } catch (Throwable e) {
        throw ExceptionUtils.throwException(e);
      }
    }
    try {
      // Creator validation already requires a public executable. Checking access as the declaring
      // class preserves that contract without requiring its package to be exported or open.
      Class<?> caller = constructor.getDeclaringClass();
      return (Object) NATIVE_CONSTRUCTOR_INVOKER.invokeExact(constructor, arguments, true, caller);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  /** Invokes a static creator method using the prepared Native Image access path when required. */
  @Internal
  public static Object invokeFactory(Method factory, Object[] arguments) {
    if (!GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      try {
        return factory.invoke(null, arguments);
      } catch (Throwable e) {
        throw ExceptionUtils.throwException(e);
      }
    }
    try {
      // Method.invoke is caller-sensitive; use the declaring class for the same module-access
      // contract as constructor invocation above.
      Class<?> caller = factory.getDeclaringClass();
      return (Object) NATIVE_FACTORY_INVOKER.invokeExact(factory, (Object) null, arguments, caller);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static MethodHandle prepareNativeInvoker(
      Class<?> ownerType, String name, MethodType methodType) {
    if (!GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return null;
    }
    try {
      return _JDKAccess._trustedLookup(ownerType).findVirtual(ownerType, name, methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ForyJsonException("Cannot prepare Native Image JSON creator invocation", e);
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
