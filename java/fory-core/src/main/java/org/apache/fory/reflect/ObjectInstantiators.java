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

package org.apache.fory.reflect;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.util.record.RecordUtils;

/**
 * Factory class for creating {@link ObjectInstantiator} instances.
 *
 * <p>This class provides a centralized way to obtain optimized object instantiators for different
 * types. It automatically selects the most appropriate creation strategy based on the target type
 * and runtime environment:
 *
 * <ul>
 *   <li><strong>Record types:</strong> Uses {@link RecordObjectInstantiator} with MethodHandle for
 *       parameterized constructor invocation
 *   <li><strong>Classes with no-arg constructors:</strong> Uses {@link
 *       DeclaredNoArgCtrInstantiator} with MethodHandle for fast invocation
 *   <li><strong>Classes without accessible constructors:</strong> Uses JDK8-24 Unsafe allocation or
 *       serialization constructor creation through the runtime ReflectionFactory owner
 *   <li><strong>Android compatibility:</strong> Uses reflection for records and no-arg
 *       constructors, and throws when no supported reflective construction path exists
 * </ul>
 *
 * <p>The static {@link #getObjectInstantiator(Class)} method keeps the process-global construction
 * cache. Runtime-owned paths should use {@link
 * org.apache.fory.resolver.TypeResolver#getObjectInstantiator(Class)} so ObjectStream-compatible
 * instantiators stay scoped to the Fory runtime.
 *
 * <p><strong>Thread Safety:</strong> This class and all returned ObjectInstantiator instances are
 * thread-safe and can be safely used across multiple threads concurrently.
 */
@SuppressWarnings("unchecked")
public class ObjectInstantiators {
  private static final ClassValueCache<ObjectInstantiator<?>> cache =
      ClassValueCache.newClassKeySoftCache(8);

  /**
   * Returns an optimized ObjectInstantiator for the given type.
   *
   * <p>This method automatically selects the most appropriate creation strategy based on the type
   * characteristics and caches the result for future use. The selection logic prioritizes
   * performance and platform compatibility.
   *
   * @param <T> the type for which to create an ObjectInstantiator
   * @param type the Class object representing the target type
   * @return a cached ObjectInstantiator instance optimized for the given type
   * @throws ForyException if the type cannot be instantiated (e.g., missing no-arg constructor in
   *     GraalVM native image)
   */
  public static <T> ObjectInstantiator<T> getObjectInstantiator(Class<T> type) {
    return (ObjectInstantiator<T>) cache.get(type, () -> createObjectInstantiator(type));
  }

  /** Creates an uncached object instantiator for runtime-scoped registries. */
  @Internal
  public static <T> ObjectInstantiator<T> createObjectInstantiator(Class<T> type) {
    if (RecordUtils.isRecord(type)) {
      return new RecordObjectInstantiator<>(type);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
    if (AndroidSupport.IS_ANDROID) {
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrInstantiator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectInstantiator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      if (noArgConstructor != null) {
        return new DeclaredNoArgCtrInstantiator<>(type);
      } else if (JdkVersion.MAJOR_VERSION >= 25) {
        return new ParentNoArgCtrInstantiator<>(type);
      } else {
        return new UnsafeObjectInstantiator<>(type);
      }
    }
    if (noArgConstructor == null) {
      if (JdkVersion.MAJOR_VERSION >= 25) {
        return new ParentNoArgCtrInstantiator<>(type);
      }
      return new UnsafeObjectInstantiator<>(type);
    }
    return new DeclaredNoArgCtrInstantiator<>(type);
  }

  /**
   * Creates an uncached empty-instance instantiator for Java ObjectStream-compatible serializers.
   */
  @Internal
  public static <T> ObjectInstantiator<T> createObjectStreamInstantiator(Class<T> type) {
    if (AndroidSupport.IS_ANDROID) {
      Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrInstantiator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectInstantiator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION < 25) {
      return new UnsafeObjectInstantiator<>(type);
    }
    return new ParentNoArgCtrInstantiator<>(type);
  }

  private static RuntimeException makeException(Class<?> type, Throwable cause) {
    Throwable target = unwrapConstructorFailure(cause);
    // Keep constructor invocation failures outside ForyException so top-level deserialization can
    // attach the read-object trail before surfacing the error to users.
    return new RuntimeException("Failed to create instance for " + type, target);
  }

  private static Throwable unwrapConstructorFailure(Throwable cause) {
    if (cause instanceof InvocationTargetException) {
      Throwable target = ((InvocationTargetException) cause).getTargetException();
      if (target != null) {
        return target;
      }
    }
    return cause;
  }

  private static final class ReflectiveNoArgCtrInstantiator<T> extends ObjectInstantiator<T> {
    private final Constructor<T> constructor;

    private ReflectiveNoArgCtrInstantiator(Class<T> type, Constructor<T> constructor) {
      super(type);
      this.constructor = constructor;
      try {
        constructor.setAccessible(true);
      } catch (RuntimeException e) {
        throw new ForyException("Failed to make no-arg constructor accessible for " + type, e);
      }
    }

    @Override
    public T newInstance() {
      try {
        return constructor.newInstance();
      } catch (Exception e) {
        throw makeException(type, e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class UnsupportedObjectInstantiator<T> extends ObjectInstantiator<T> {
    private final String message;

    private UnsupportedObjectInstantiator(Class<T> type, String message) {
      super(type);
      this.message = message;
    }

    @Override
    public T newInstance() {
      throw new ForyException(message);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new ForyException(message);
    }
  }

  public static final class DeclaredNoArgCtrInstantiator<T> extends ObjectInstantiator<T> {
    private final MethodHandle handle;

    public DeclaredNoArgCtrInstantiator(Class<T> type) {
      super(type);
      handle = ReflectionUtils.getCtrHandle(type, true);
    }

    @Override
    public T newInstance() {
      try {
        return (T) handle.invoke();
      } catch (Throwable e) {
        throw makeException(type, e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class RecordObjectInstantiator<T> extends ObjectInstantiator<T> {
    private final MethodHandle handle;
    private final Constructor<?> constructor;

    public RecordObjectInstantiator(Class<T> type) {
      super(type);
      Tuple2<Constructor, MethodHandle> tuple2 = RecordUtils.getRecordConstructor(type);
      constructor = tuple2.f0;
      handle =
          tuple2.f1 == null
              ? null
              : tuple2.f1.asSpreader(Object[].class, constructor.getParameterCount());
      if (AndroidSupport.IS_ANDROID
          || (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION >= 25)) {
        try {
          constructor.setAccessible(true);
        } catch (Throwable t) {
          throw new ForyException(
              "Failed to create instance, please provide a public constructor for " + type, t);
        }
      }
    }

    @Override
    public T newInstance() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      try {
        // compile-time constant is eligible for dead code elimination.
        if (AndroidSupport.IS_ANDROID
            || handle == null
            || (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION >= 25)) {
          return (T) constructor.newInstance(arguments);
        } else {
          // Regular path: use method handle
          return (T) handle.invoke(arguments);
        }
      } catch (Throwable e) {
        throw makeException(type, e);
      }
    }
  }

  public static final class ParentNoArgCtrInstantiator<T> extends ObjectInstantiator<T> {
    private final Constructor<T> constructor;

    public ParentNoArgCtrInstantiator(Class<T> type) {
      super(type);
      this.constructor = createSerializationConstructor(type);
    }

    private static <T> Constructor<T> createSerializationConstructor(Class<T> type) {
      try {
        Constructor<?> parentConstructor = findSerializationConstructor(type);
        return (Constructor<T>)
            ReflectionFactoryAccess.newConstructorForSerialization(type, parentConstructor);
      } catch (Throwable e) {
        throw new ForyException(
            "Failed to create instance, please provide a no-arg constructor for " + type, e);
      }
    }

    private static Constructor<?> findSerializationConstructor(Class<?> type)
        throws NoSuchMethodException {
      if (!Serializable.class.isAssignableFrom(type)) {
        // ReflectionFactory can synthesize serialization constructors for ordinary classes too.
        // Use Object as the template so normal Fory object creation keeps empty-instance
        // semantics and never depends on the target class hierarchy exposing a no-arg constructor.
        return Object.class.getDeclaredConstructor();
      }
      Class<?> current = type.getSuperclass();
      // Java ObjectStream reconstruction skips every Serializable class constructor and invokes
      // only the first non-Serializable superclass no-arg constructor.
      while (current != null && Serializable.class.isAssignableFrom(current)) {
        current = current.getSuperclass();
      }
      if (current == null) {
        current = Object.class;
      }
      Constructor<?> constructor = current.getDeclaredConstructor();
      if (!validSerializationConstructor(type, current, constructor)) {
        throw new ForyException(
            "First non-Serializable superclass "
                + current.getName()
                + " does not expose a valid no-arg constructor for "
                + type);
      }
      return constructor;
    }

    private static boolean validSerializationConstructor(
        Class<?> type, Class<?> constructorClass, Constructor<?> constructor) {
      int modifiers = constructor.getModifiers();
      if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
        return true;
      }
      if (Modifier.isPrivate(modifiers)) {
        return false;
      }
      return ReflectionUtils.getPackage(type).equals(ReflectionUtils.getPackage(constructorClass));
    }

    @Override
    public T newInstance() {
      try {
        return constructor.newInstance();
      } catch (Exception e) {
        throw makeException(type, e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }

    private static final class ReflectionFactoryAccess {
      private static final Object REFLECTION_FACTORY;
      private static final MethodHandle NEW_CONSTRUCTOR_FOR_SERIALIZATION;

      static {
        try {
          Class<?> reflectionFactoryClass = reflectionFactoryClass();
          MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(reflectionFactoryClass);
          MethodHandle getReflectionFactory =
              lookup.findStatic(
                  reflectionFactoryClass,
                  "getReflectionFactory",
                  MethodType.methodType(reflectionFactoryClass));
          REFLECTION_FACTORY = getReflectionFactory.invoke();
          NEW_CONSTRUCTOR_FOR_SERIALIZATION =
              lookup.findVirtual(
                  reflectionFactoryClass,
                  "newConstructorForSerialization",
                  MethodType.methodType(Constructor.class, Class.class, Constructor.class));
        } catch (Throwable e) {
          throw new ExceptionInInitializerError(e);
        }
      }

      private static Class<?> reflectionFactoryClass() throws ClassNotFoundException {
        if (JdkVersion.MAJOR_VERSION >= 25) {
          return Class.forName("jdk.internal.reflect.ReflectionFactory");
        }
        return Class.forName("sun.reflect.ReflectionFactory");
      }

      private static Constructor<?> newConstructorForSerialization(
          Class<?> type, Constructor<?> parentConstructor) throws Throwable {
        return (Constructor<?>)
            NEW_CONSTRUCTOR_FOR_SERIALIZATION.invoke(REFLECTION_FACTORY, type, parentConstructor);
      }
    }
  }
}
