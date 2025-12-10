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

package org.apache.fory.builder;

import java.lang.reflect.Array;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.collection.Tuple2;

/**
 * Creates unique marker classes for each layer in a class hierarchy. These marker classes serve as
 * unique keys in {@code metaContext.classMap} to distinguish different layers during serialization.
 *
 * <p>For a class hierarchy {@code C extends B extends A}, this generator creates unique marker
 * classes for:
 *
 * <ul>
 *   <li>A's layer with index 0 - marker for A's layer fields
 *   <li>B's layer with index 1 - marker for B's layer fields
 *   <li>C's layer with index 2 - marker for C's layer fields
 * </ul>
 *
 * <p>This implementation uses array types to create unique Class objects without runtime code
 * generation. For each (targetClass, layerIndex) pair, we create an array type with dimensions
 * equal to (layerIndex + 1). For example:
 *
 * <ul>
 *   <li>Layer 0: Foo[].class
 *   <li>Layer 1: Foo[][].class
 *   <li>Layer 2: Foo[][][].class
 * </ul>
 *
 * <p>This approach is compatible with GraalVM native images which don't support runtime class
 * generation via Janino.
 */
public class LayerMarkerClassGenerator {

  private static final ConcurrentHashMap<Tuple2<Class<?>, Integer>, Class<?>> cache =
      new ConcurrentHashMap<>();

  private static final String LAYER_MARKER_SUFFIX = "$ForyLayer$";

  /**
   * Get or create a marker class for the given target class and layer index.
   *
   * <p>This method returns a unique Class object for each (targetClass, layerIndex) pair. The
   * returned class is an array type of the target class, with dimensions equal to (layerIndex + 1).
   * This ensures uniqueness without requiring runtime code generation.
   *
   * @param fory the Fory instance (not used but kept for API compatibility)
   * @param targetClass the target class this marker represents
   * @param layerIndex the layer index in the class hierarchy (0 = topmost parent)
   * @return a unique class representing this layer marker
   */
  public static Class<?> getOrCreate(Fory fory, Class<?> targetClass, int layerIndex) {
    Tuple2<Class<?>, Integer> key = Tuple2.of(targetClass, layerIndex);
    return cache.computeIfAbsent(key, k -> createMarkerClass(targetClass, layerIndex));
  }

  /**
   * Generate a marker class name for the given target class and layer index.
   *
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return the marker class simple name
   */
  public static String getMarkerClassName(Class<?> targetClass, int layerIndex) {
    return targetClass.getSimpleName() + LAYER_MARKER_SUFFIX + layerIndex;
  }

  /**
   * Create a marker class for the given target class and layer index.
   *
   * <p>This method creates a unique Class object by creating an array type. The array dimension is
   * (layerIndex + 1), ensuring uniqueness for each layer. Array types are intrinsic to the JVM and
   * don't require runtime code generation, making this approach compatible with GraalVM native
   * images.
   *
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return a unique class representing this layer marker
   */
  private static Class<?> createMarkerClass(Class<?> targetClass, int layerIndex) {
    // Create an array type with (layerIndex + 1) dimensions
    // This gives us a unique Class object for each (targetClass, layerIndex) pair
    // without requiring runtime code generation
    int dimensions = layerIndex + 1;
    Class<?> markerClass = targetClass;
    for (int i = 0; i < dimensions; i++) {
      // Create array of this type to get the array class
      markerClass = Array.newInstance(markerClass, 0).getClass();
    }
    return markerClass;
  }

  /** Clear the cache. Used for testing. */
  public static void clearCache() {
    cache.clear();
  }
}
