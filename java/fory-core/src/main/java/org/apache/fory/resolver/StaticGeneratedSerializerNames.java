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

package org.apache.fory.resolver;

import org.apache.fory.annotation.Internal;

/** Deterministic generated serializer names used by runtime lookup. */
@Internal
public final class StaticGeneratedSerializerNames {
  private static final String XLANG_SUFFIX = "_ForySerializer";
  private static final String NATIVE_SUFFIX = "_ForyNativeSerializer";

  private StaticGeneratedSerializerNames() {}

  public static String generatedSerializerBinaryName(
      Class<?> targetType, StaticGeneratedSerializerRegistry.Mode mode) {
    return generatedSerializerBinaryName(targetType.getName(), mode);
  }

  public static String generatedSerializerBinaryName(
      String targetBinaryName, StaticGeneratedSerializerRegistry.Mode mode) {
    int packageEnd = targetBinaryName.lastIndexOf('.');
    if (packageEnd < 0) {
      return generatedSerializerSimpleName(targetBinaryName, mode);
    }
    return targetBinaryName.substring(0, packageEnd)
        + "."
        + generatedSerializerSimpleName(targetBinaryName.substring(packageEnd + 1), mode);
  }

  public static String generatedSerializerSimpleName(
      String targetBinarySimpleName, StaticGeneratedSerializerRegistry.Mode mode) {
    return escapeBinarySimpleName(targetBinarySimpleName) + suffix(mode);
  }

  private static String suffix(StaticGeneratedSerializerRegistry.Mode mode) {
    return mode == StaticGeneratedSerializerRegistry.Mode.XLANG ? XLANG_SUFFIX : NATIVE_SUFFIX;
  }

  private static String escapeBinarySimpleName(String binarySimpleName) {
    StringBuilder builder = new StringBuilder(binarySimpleName.length() + 32);
    for (int i = 0; i < binarySimpleName.length(); ) {
      int codePoint = binarySimpleName.codePointAt(i);
      if (codePoint == '$') {
        builder.append('_');
      } else if (codePoint == '_') {
        builder.append("_u_");
      } else if (isJavaIdentifierPart(codePoint)) {
        builder.appendCodePoint(codePoint);
      } else {
        builder.append("_x").append(Integer.toHexString(codePoint)).append('_');
      }
      i += Character.charCount(codePoint);
    }
    return builder.toString();
  }

  private static boolean isJavaIdentifierPart(int codePoint) {
    return Character.isJavaIdentifierPart(codePoint);
  }
}
