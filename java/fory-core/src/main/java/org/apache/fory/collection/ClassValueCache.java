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

package org.apache.fory.collection;

import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.apache.fory.annotation.Internal;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;

@Internal
public class ClassValueCache<T> {

  private static final Object NULL_VALUE = new Object();

  private final Store store;

  private ClassValueCache(Store store) {
    this.store = store;
  }

  public T getIfPresent(Class<?> k) {
    return unmaskNull(store.getIfPresent(k));
  }

  public T get(Class<?> k, Callable<? extends T> loader) {
    try {
      return unmaskNull(store.get(k, () -> maskNull(loader.call())));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void put(Class<?> k, T v) {
    store.put(k, maskNull(v));
  }

  /**
   * Create a cache with weak keys.
   *
   * <p>when in graalvm or Android, the cache is a concurrent hash map. when in jvm, the cache is
   * backed by {@link ClassValue}.
   *
   * <p>The normal JVM path must use {@link ClassValue}: several cached values contain fields,
   * method handles, or generated classes that point back to the key class. A regular weak-key map
   * would keep those values strongly reachable and prevent class unloading.
   *
   * @param concurrencyLevel the concurrency level
   * @return the cache
   */
  public static <T> ClassValueCache<T> newClassKeyCache(int concurrencyLevel) {
    if (AndroidSupport.IS_ANDROID || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return new ClassValueCache<>(new MapStore(concurrencyLevel));
    } else {
      return new ClassValueCache<>(new JvmClassValueStore(false));
    }
  }

  /**
   * Create a cache with weak keys and soft values.
   *
   * <p>when in graalvm or Android, the cache is a concurrent hash map. when in jvm, the cache is
   * backed by {@link ClassValue} with soft values.
   *
   * @param concurrencyLevel the concurrency level
   * @return the cache
   */
  public static <T> ClassValueCache<T> newClassKeySoftCache(int concurrencyLevel) {
    if (AndroidSupport.IS_ANDROID || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return new ClassValueCache<>(new MapStore(concurrencyLevel));
    } else {
      return new ClassValueCache<>(new JvmClassValueStore(true));
    }
  }

  private interface Store {
    Object getIfPresent(Class<?> key);

    Object get(Class<?> key, Callable<Object> loader) throws Exception;

    void put(Class<?> key, Object value);
  }

  private static final class MapStore implements Store {
    private final Cache<Class<?>, Object> cache;

    private MapStore(int concurrencyLevel) {
      cache = CacheBuilder.newBuilder().concurrencyLevel(concurrencyLevel).build();
    }

    @Override
    public Object getIfPresent(Class<?> key) {
      return cache.getIfPresent(key);
    }

    @Override
    public Object get(Class<?> key, Callable<Object> loader) {
      try {
        return cache.get(key, loader);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void put(Class<?> key, Object value) {
      cache.put(key, value);
    }
  }

  private static final class JvmClassValueStore implements Store {
    private final boolean softValues;
    private final ClassValue<Holder> classValue =
        new ClassValue<Holder>() {
          @Override
          protected Holder computeValue(Class<?> type) {
            return new Holder(softValues);
          }
        };

    private JvmClassValueStore(boolean softValues) {
      this.softValues = softValues;
    }

    @Override
    public Object getIfPresent(Class<?> key) {
      return classValue.get(key).get();
    }

    @Override
    public Object get(Class<?> key, Callable<Object> loader) throws Exception {
      return classValue.get(key).get(loader);
    }

    @Override
    public void put(Class<?> key, Object value) {
      classValue.get(key).put(value);
    }
  }

  private static final class Holder {
    private final boolean softValue;
    private volatile Object value;

    private Holder(boolean softValue) {
      this.softValue = softValue;
    }

    private Object get() {
      Object current = value;
      if (softValue && current instanceof SoftReference) {
        return ((SoftReference<?>) current).get();
      }
      return current;
    }

    private synchronized Object get(Callable<Object> loader) throws Exception {
      Object current = get();
      if (current != null) {
        return current;
      }
      current = loader.call();
      put(current);
      return current;
    }

    private void put(Object value) {
      this.value = softValue ? new SoftReference<>(value) : value;
    }
  }

  private static Object maskNull(Object value) {
    return value == null ? NULL_VALUE : value;
  }

  @SuppressWarnings("unchecked")
  private static <T> T unmaskNull(Object value) {
    return value == NULL_VALUE ? null : (T) value;
  }
}
