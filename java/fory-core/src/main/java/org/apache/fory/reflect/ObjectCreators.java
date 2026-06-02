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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.util.record.RecordUtils;

/**
 * Factory class for creating {@link ObjectCreator} instances.
 *
 * <p>This class provides a centralized way to obtain optimized object creators for different types.
 * It automatically selects the most appropriate creation strategy based on the target type and
 * runtime environment:
 *
 * <ul>
 *   <li><strong>Record types:</strong> Uses {@link RecordObjectCreator} with MethodHandle for
 *       parameterized constructor invocation
 *   <li><strong>Classes with no-arg constructors:</strong> Uses {@link
 *       DeclaredNoArgCtrObjectCreator} with MethodHandle for fast invocation
 *   <li><strong>Classes without accessible constructors:</strong> Uses a private
 *       constructor-bypassing creator on runtimes where that is still supported
 *   <li><strong>Android compatibility:</strong> Uses reflection for records and no-arg
 *       constructors, and throws when no supported reflective construction path exists
 * </ul>
 *
 * <p>The static {@link #getObjectCreator(Class)} method keeps the legacy process-global cache.
 * Runtime-owned paths should use {@link
 * org.apache.fory.resolver.TypeResolver#getObjectCreator(Class)} so ObjectStream-compatible
 * creators stay scoped to the Fory runtime.
 *
 * <p><strong>Thread Safety:</strong> This class and all returned ObjectCreator instances are
 * thread-safe and can be safely used across multiple threads concurrently.
 */
@SuppressWarnings("unchecked")
public class ObjectCreators {
  private static final ClassValueCache<ObjectCreator<?>> cache =
      ClassValueCache.newClassKeySoftCache(8);

  /**
   * Returns an optimized ObjectCreator for the given type.
   *
   * <p>This method automatically selects the most appropriate creation strategy based on the type
   * characteristics and caches the result for future use. The selection logic prioritizes
   * performance and platform compatibility.
   *
   * @param <T> the type for which to create an ObjectCreator
   * @param type the Class object representing the target type
   * @return a cached ObjectCreator instance optimized for the given type
   * @throws ForyException if the type cannot be instantiated (e.g., missing no-arg constructor in
   *     GraalVM native image)
   */
  public static <T> ObjectCreator<T> getObjectCreator(Class<T> type) {
    return (ObjectCreator<T>) cache.get(type, () -> createObjectCreator(type));
  }

  /** Creates an uncached object creator for runtime-scoped registries. */
  @Internal
  public static <T> ObjectCreator<T> createObjectCreator(Class<T> type) {
    if (RecordUtils.isRecord(type)) {
      return new RecordObjectCreator<>(type);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
    if (AndroidSupport.IS_ANDROID) {
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrObjectCreator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectCreator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      if (noArgConstructor != null) {
        return new DeclaredNoArgCtrObjectCreator<>(type);
      } else {
        return new ConstructorBypassObjectCreator<>(type);
      }
    }
    if (noArgConstructor == null) {
      return new ConstructorBypassObjectCreator<>(type);
    }
    return new DeclaredNoArgCtrObjectCreator<>(type);
  }

  /** Creates an uncached empty-instance creator for Java ObjectStream-compatible serializers. */
  @Internal
  public static <T> ObjectCreator<T> createObjectStreamCreator(Class<T> type) {
    if (AndroidSupport.IS_ANDROID) {
      Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrObjectCreator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectCreator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return new ConstructorBypassObjectCreator<>(type);
    }
    return new ParentNoArgCtrObjectCreator<>(type);
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

  private static final class ReflectiveNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private final Constructor<T> constructor;

    private ReflectiveNoArgCtrObjectCreator(Class<T> type, Constructor<T> constructor) {
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

  private static final class UnsupportedObjectCreator<T> extends ObjectCreator<T> {
    private final String message;

    private UnsupportedObjectCreator(Class<T> type, String message) {
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

  private static final class ConstructorBypassObjectCreator<T> extends ObjectCreator<T> {
    private final ConstructorBypassAllocator<T> allocator;

    public ConstructorBypassObjectCreator(Class<T> type) {
      super(type);
      allocator = new ConstructorBypassAllocator<>(type);
    }

    @Override
    public T newInstance() {
      return allocator.allocate();
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class DeclaredNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public DeclaredNoArgCtrObjectCreator(Class<T> type) {
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

  public static final class RecordObjectCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;
    private final Constructor<?> constructor;

    public RecordObjectCreator(Class<T> type) {
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

  public static final class ParentNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private static volatile Object reflectionFactory;
    private static volatile Method newConstructorForSerializationMethod;

    private final Constructor<T> constructor;
    private final ConstructorBypassAllocator<T> allocator;

    public ParentNoArgCtrObjectCreator(Class<T> type) {
      super(type);
      if (JdkVersion.MAJOR_VERSION >= 25) {
        constructor = null;
        allocator = new ConstructorBypassAllocator<>(type);
        return;
      }
      this.constructor = createSerializationConstructor(type);
      allocator = null;
    }

    private static <T> Constructor<T> createSerializationConstructor(Class<T> type) {
      try {
        if (reflectionFactory == null) {
          Class<?> reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory");
          Method getReflectionFactory = reflectionFactoryClass.getMethod("getReflectionFactory");
          reflectionFactory = getReflectionFactory.invoke(null);
          newConstructorForSerializationMethod =
              reflectionFactoryClass.getMethod(
                  "newConstructorForSerialization", Class.class, Constructor.class);
        }
        Constructor<?> parentConstructor = findSerializationConstructor(type);
        return (Constructor<T>)
            newConstructorForSerializationMethod.invoke(reflectionFactory, type, parentConstructor);
      } catch (Throwable e) {
        throw new ForyException(
            "Failed to create instance, please provide a no-arg constructor for " + type, e);
      }
    }

    private static Constructor<?> findSerializationConstructor(Class<?> type)
        throws NoSuchMethodException {
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
      ConstructorBypassAllocator<T> constructorBypassAllocator = allocator;
      if (constructorBypassAllocator != null) {
        return constructorBypassAllocator.allocate();
      }
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
}
