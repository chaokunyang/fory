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

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.Platform;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.record.RecordUtils;

/**
 * Factory class for creating and caching {@link ObjectCreator} instances.
 *
 * <p>This class provides a centralized way to obtain optimized object creators for different types.
 * It automatically selects the most appropriate creation strategy based on the target type and
 * runtime environment:
 *
 * <ul>
 *   <li><strong>Record types:</strong> Uses {@link RecordCtrCreator} with MethodHandle for
 *       parameterized constructor invocation
 *   <li><strong>Classes with no-arg constructors:</strong> Uses {@link DeclaredNoArgCtrCreator}
 *       with MethodHandle for fast invocation
 *   <li><strong>Classes without accessible constructors:</strong> Uses {@link UnsafeObjectCreator}
 *       with platform-specific unsafe allocation
 *   <li><strong>GraalVM native image compatibility:</strong> Uses {@link
 *       NoArgSerializableObjectCreator} for reflection-based creation when needed
 * </ul>
 *
 * <p>All created ObjectCreator instances are cached using a soft reference cache to improve
 * performance on repeated requests for the same type.
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
    return (ObjectCreator<T>) cache.get(type, () -> creategetObjectCreator(type));
  }

  static <T> ObjectCreator<T> creategetObjectCreator(Class<T> type) {
    if (RecordUtils.isRecord(type)) {
      return new RecordCtrCreator<>(type);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type, true);
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && Platform.JAVA_VERSION >= 25) {
      if (noArgConstructor == null) {
        throw GraalvmSupport.throwNoArgCtrException(type);
      }
      if (noArgConstructor.getDeclaringClass() == type) {
        return new DeclaredNoArgCtrCreator<>(type);
      } else {
        return new NoArgSerializableObjectCreator<>(type);
      }
    }
    if (noArgConstructor == null || noArgConstructor.getDeclaringClass() != type) {
      return new UnsafeObjectCreator<>(type);
    }
    return new DeclaredNoArgCtrCreator<>(type);
  }

  public static final class UnsafeObjectCreator<T> extends ObjectCreator<T> {

    public UnsafeObjectCreator(Class<T> type) {
      super(type);
    }

    @Override
    public T newInstance() {
      return Platform.newInstance(type);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class DeclaredNoArgCtrCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public DeclaredNoArgCtrCreator(Class<T> type) {
      super(type);
      handle = ReflectionUtils.getCtrHandle(type, true);
    }

    @Override
    public T newInstance() {
      try {
        return (T) handle.invoke();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class NoArgSerializableObjectCreator<T> extends ObjectCreator<T> {
    private final Constructor<T> ctr;

    public NoArgSerializableObjectCreator(Class<T> type) {
      this(type, ReflectionUtils.getNoArgConstructor(type, true));
    }

    public NoArgSerializableObjectCreator(Class<T> type, Constructor<T> ctr) {
      super(type);
      this.ctr = Preconditions.checkNotNull(ctr);
      try {
        ctr.setAccessible(true);
        // ensure it does work;
        newInstance();
      } catch (Throwable e) {
        throw new ForyException("Please provide a no-arg constructor for " + type, e);
      }
    }

    @Override
    public T newInstance() {
      try {
        return ctr.newInstance();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class RecordCtrCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public RecordCtrCreator(Class<T> type) {
      super(type);
      handle = RecordUtils.getRecordCtrHandle(type);
    }

    @Override
    public T newInstance() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      try {
        return (T) handle.invokeWithArguments(arguments);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}
