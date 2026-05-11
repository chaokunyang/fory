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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.apache.fory.annotation.Internal;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;

@Internal
public class ClassValueCache<T> {

  private static final Object NULL_VALUE = new Object();

  private final Cache<Class<?>, Object> cache;

  private ClassValueCache(Cache<Class<?>, Object> cache) {
    this.cache = cache;
  }

  public T getIfPresent(Class<?> k) {
    return unmaskNull(cache.getIfPresent(k));
  }

  public T get(Class<?> k, Callable<? extends T> loader) {
    try {
      return unmaskNull(cache.get(k, () -> maskNull(loader.call())));
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  public void put(Class<?> k, T v) {
    cache.put(k, maskNull(v));
  }

  /**
   * Create a cache with weak keys.
   *
   * <p>when in graalvm or Android, the cache is a concurrent hash map. when in jvm, the cache is a
   * weak-key hash map.
   *
   * <p>Android intentionally uses strong keys and values because Android does not support {@link
   * ClassValue}; this cache is the Android-safe replacement for direct class-value caches.
   *
   * @param concurrencyLevel the concurrency level
   * @return the cache
   */
  public static <T> ClassValueCache<T> newClassKeyCache(int concurrencyLevel) {
    if (AndroidSupport.IS_ANDROID || GraalvmSupport.isGraalBuildTime()) {
      return new ClassValueCache<>(
          CacheBuilder.newBuilder().concurrencyLevel(concurrencyLevel).build());
    } else {
      return new ClassValueCache<>(
          CacheBuilder.newBuilder().weakKeys().concurrencyLevel(concurrencyLevel).build());
    }
  }

  /**
   * Create a cache with weak keys and soft values.
   *
   * <p>when in graalvm or Android, the cache is a concurrent hash map. when in jvm, the cache is a
   * weak-key, soft-value hash map.
   *
   * @param concurrencyLevel the concurrency level
   * @return the cache
   */
  public static <T> ClassValueCache<T> newClassKeySoftCache(int concurrencyLevel) {
    if (AndroidSupport.IS_ANDROID || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return new ClassValueCache<>(
          CacheBuilder.newBuilder().concurrencyLevel(concurrencyLevel).build());
    } else {
      return new ClassValueCache<>(
          CacheBuilder.newBuilder()
              .weakKeys()
              .softValues()
              .concurrencyLevel(concurrencyLevel)
              .build());
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
