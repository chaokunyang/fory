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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CompileUnit;

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
 * <p>This implementation uses Janino to generate unique empty marker classes at runtime. Each
 * generated class is unique and serves as a key in identity-based maps. The cache uses ClassValue
 * for efficient per-class caching, and the classes generated at GraalVM build time will be
 * available at runtime.
 */
public class LayerMarkerClassGenerator {

  private static final String LAYER_MARKER_SUFFIX = "_ForyLayer_";

  /**
   * ClassValue-based cache for layer marker classes. For each target class, we store a map from
   * layer index to the generated marker class. This approach is efficient and works well with
   * GraalVM's class initialization.
   */
  private static final ClassValue<Map<Integer, Class<?>>> layerClassCache =
      new ClassValue<Map<Integer, Class<?>>>() {
        @Override
        protected Map<Integer, Class<?>> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  /**
   * Get or create a unique marker class for the given target class and layer index.
   *
   * <p>This method returns a unique Class object for each (targetClass, layerIndex) pair. The
   * returned class is generated using Janino at runtime (or at GraalVM build time), ensuring
   * uniqueness without requiring array instantiation.
   *
   * <p>The generated classes are cached using ClassValue, so classes generated during GraalVM build
   * time will be available at runtime.
   *
   * @param fory the Fory instance
   * @param targetClass the target class this marker represents
   * @param layerIndex the layer index in the class hierarchy (0 = topmost parent)
   * @return a unique class representing this layer marker
   */
  public static Class<?> getOrCreate(Fory fory, Class<?> targetClass, int layerIndex) {
    Map<Integer, Class<?>> layerMap = layerClassCache.get(targetClass);
    return layerMap.computeIfAbsent(layerIndex, idx -> createMarkerClass(fory, targetClass, idx));
  }

  /**
   * Generate a marker class name for the given target class and layer index.
   *
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return the marker class simple name
   */
  public static String getMarkerClassName(Class<?> targetClass, int layerIndex) {
    // Replace $ with _ to handle inner class names properly
    String simpleName = targetClass.getSimpleName().replace('$', '_');
    return simpleName + LAYER_MARKER_SUFFIX + layerIndex;
  }

  /**
   * Create a unique marker class for the given target class and layer index.
   *
   * <p>This method generates a simple empty class using the shared CodeGenerator. The class is
   * unique for each (targetClass, layerIndex) pair, making it suitable as an identity-based map
   * key.
   *
   * @param fory the Fory instance
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return a unique class for this layer marker
   */
  private static Class<?> createMarkerClass(Fory fory, Class<?> targetClass, int layerIndex) {
    String pkg = CodeGenerator.getPackage(targetClass);
    String className = getMarkerClassName(targetClass, layerIndex);
    String qualifiedClassName = pkg.isEmpty() ? className : pkg + "." + className;

    // Generate a simple empty marker class using the shared CodeGenerator
    StringBuilder codeBuilder = new StringBuilder();
    if (!pkg.isEmpty()) {
      codeBuilder.append("package ").append(pkg).append(";\n\n");
    }
    codeBuilder.append("public final class ").append(className).append(" {}");
    CompileUnit compileUnit = new CompileUnit(pkg, className, codeBuilder.toString());

    CodeGenerator codeGenerator =
        CodeGenerator.getSharedCodeGenerator(fory.getClass().getClassLoader());
    ClassLoader classLoader = codeGenerator.compile(compileUnit);
    try {
      return classLoader.loadClass(qualifiedClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Failed to load generated marker class: " + qualifiedClassName, e);
    }
  }
}
