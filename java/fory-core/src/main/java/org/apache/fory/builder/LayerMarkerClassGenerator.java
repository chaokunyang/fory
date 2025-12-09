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

import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CompileUnit;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.serializer.CurrentLayerMarker;
import org.apache.fory.util.StringUtils;

/**
 * Generates marker classes for each layer in a class hierarchy. These marker classes implement
 * {@link CurrentLayerMarker} and serve as unique keys in {@code metaContext.classMap} to
 * distinguish different layers during serialization.
 *
 * <p>For a class hierarchy {@code C extends B extends A}, this generator creates:
 *
 * <ul>
 *   <li>{@code A$ForyLayer$0} - marker for A's layer fields
 *   <li>{@code B$ForyLayer$1} - marker for B's layer fields
 *   <li>{@code C$ForyLayer$2} - marker for C's layer fields
 * </ul>
 */
public class LayerMarkerClassGenerator {

  private static final ConcurrentHashMap<Tuple2<Class<?>, Integer>, Class<?>> cache =
      new ConcurrentHashMap<>();

  private static final String LAYER_MARKER_SUFFIX = "$ForyLayer$";

  /**
   * Get or create a marker class for the given target class and layer index.
   *
   * @param fory the Fory instance for code generation
   * @param targetClass the target class this marker represents
   * @param layerIndex the layer index in the class hierarchy (0 = topmost parent)
   * @return the generated marker class
   */
  public static Class<?> getOrCreate(Fory fory, Class<?> targetClass, int layerIndex) {
    Tuple2<Class<?>, Integer> key = Tuple2.of(targetClass, layerIndex);
    Class<?> markerClass = cache.get(key);
    if (markerClass != null) {
      return markerClass;
    }
    return cache.computeIfAbsent(key, k -> generateMarkerClass(fory, targetClass, layerIndex));
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
   * Generate a marker class for the given target class and layer index.
   *
   * @param fory the Fory instance
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return the generated marker class
   */
  private static Class<?> generateMarkerClass(Fory fory, Class<?> targetClass, int layerIndex) {
    String pkg = CodeGenerator.getPackage(targetClass);
    String className = getMarkerClassName(targetClass, layerIndex);
    String code = generateMarkerClassCode(pkg, className, targetClass, layerIndex);

    CompileUnit compileUnit = new CompileUnit(pkg, className, code);
    ClassLoader baseClassLoader = targetClass.getClassLoader();
    if (baseClassLoader == null) {
      baseClassLoader = fory.getClassLoader();
    }
    if (baseClassLoader == null) {
      baseClassLoader = Thread.currentThread().getContextClassLoader();
    }
    CodeGenerator codeGenerator = CodeGenerator.getSharedCodeGenerator(baseClassLoader);
    ClassLoader classLoader = codeGenerator.compile(compileUnit);

    try {
      String qualifiedName = StringUtils.isNotBlank(pkg) ? pkg + "." + className : className;
      return classLoader.loadClass(qualifiedName);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Failed to load generated marker class: " + className, e);
    }
  }

  /**
   * Generate the source code for a marker class.
   *
   * @param pkg the package name
   * @param className the class name
   * @param targetClass the target class
   * @param layerIndex the layer index
   * @return the generated source code
   */
  private static String generateMarkerClassCode(
      String pkg, String className, Class<?> targetClass, int layerIndex) {
    StringBuilder sb = new StringBuilder();

    // Package declaration
    if (StringUtils.isNotBlank(pkg)) {
      sb.append("package ").append(pkg).append(";\n\n");
    }

    // Import
    sb.append("import ").append(CurrentLayerMarker.class.getName()).append(";\n\n");

    // Class declaration
    sb.append("public final class ").append(className).append(" implements CurrentLayerMarker {\n");

    // Singleton instance
    sb.append("  public static final ").append(className).append(" INSTANCE = new ");
    sb.append(className).append("();\n\n");

    // Private constructor
    sb.append("  private ").append(className).append("() {}\n\n");

    // getTargetClass method
    sb.append("  @Override\n");
    sb.append("  public Class<?> getTargetClass() {\n");
    sb.append("    return ").append(targetClass.getName()).append(".class;\n");
    sb.append("  }\n\n");

    // getLayerIndex method
    sb.append("  @Override\n");
    sb.append("  public int getLayerIndex() {\n");
    sb.append("    return ").append(layerIndex).append(";\n");
    sb.append("  }\n");

    sb.append("}\n");

    return sb.toString();
  }

  /** Clear the cache. Used for testing. */
  public static void clearCache() {
    cache.clear();
  }
}
