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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
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
      return new ClassValueCache<>(new ClassValueStore(false));
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
      return new ClassValueCache<>(new ClassValueStore(true));
    }
  }

  private interface Store {
    Object getIfPresent(Class<?> key);

    Object get(Class<?> key, Callable<Object> loader) throws Exception;

    void put(Class<?> key, Object value);
  }

  private static final class MapStore implements Store {
    private final ConcurrentMap<Class<?>, Object> cache;

    private MapStore(int concurrencyLevel) {
      cache = new ConcurrentHashMap<>(concurrencyLevel);
    }

    @Override
    public Object getIfPresent(Class<?> key) {
      return cache.get(key);
    }

    @Override
    public Object get(Class<?> key, Callable<Object> loader) {
      try {
        Object value = cache.get(key);
        if (value != null) {
          return value;
        }
        value = loader.call();
        Object racedValue = cache.putIfAbsent(key, value);
        return racedValue == null ? value : racedValue;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void put(Class<?> key, Object value) {
      cache.put(key, value);
    }
  }

  private static final class ClassValueStore implements Store {
    private final boolean softValues;
    // ClassValue has no explicit put API, so each class maps to a mutable cell while class
    // unloading still follows ClassValue reachability.
    private final ClassValue<AtomicReference<Object>> classValue =
        new ClassValue<AtomicReference<Object>>() {
          @Override
          protected AtomicReference<Object> computeValue(Class<?> type) {
            return new AtomicReference<>();
          }
        };

    private ClassValueStore(boolean softValues) {
      this.softValues = softValues;
    }

    @Override
    public Object getIfPresent(Class<?> key) {
      return unwrap(classValue.get(key).get());
    }

    @Override
    public Object get(Class<?> key, Callable<Object> loader) throws Exception {
      AtomicReference<Object> ref = classValue.get(key);
      Object current = unwrap(ref.get());
      if (current != null) {
        return current;
      }
      synchronized (ref) {
        current = unwrap(ref.get());
        if (current != null) {
          return current;
        }
        current = loader.call();
        ref.set(wrap(current));
        return current;
      }
    }

    @Override
    public void put(Class<?> key, Object value) {
      classValue.get(key).set(wrap(value));
    }

    private Object wrap(Object value) {
      if (softValues) {
        return new SoftReference<>(value);
      }
      return value;
    }

    private Object unwrap(Object value) {
      if (softValues && value instanceof SoftReference) {
        return ((SoftReference<?>) value).get();
      }
      return value;
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
