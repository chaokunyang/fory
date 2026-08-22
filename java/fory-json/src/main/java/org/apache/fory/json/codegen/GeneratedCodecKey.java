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

package org.apache.fory.json.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.apache.fory.annotation.Internal;

/** Exact, source-independent identity of one generated JSON capability class. */
@Internal
public final class GeneratedCodecKey {
  /** Generated capabilities with independent implementations. */
  public enum Role {
    STRING_WRITER("StringWriter"),
    UTF8_WRITER("Utf8Writer"),
    LATIN1_READER("Latin1Reader"),
    UTF16_READER("Utf16Reader"),
    UTF8_READER("Utf8Reader"),
    UTF8_COLLECTION_WRITER("Utf8CollectionWriter"),
    UTF8_COLLECTION_READER("Utf8CollectionReader");

    private final String classSuffix;

    Role(String classSuffix) {
      this.classSuffix = classSuffix;
    }

    public String classSuffix() {
      return classSuffix;
    }
  }

  private final Class<?> targetClass;
  private final Role role;
  private final Object[] keyParts;
  private final int hash;

  private GeneratedCodecKey(Class<?> targetClass, Role role, Object[] keyParts) {
    this.targetClass = Objects.requireNonNull(targetClass);
    this.role = Objects.requireNonNull(role);
    this.keyParts = keyParts.clone();
    // Hosted keys are reconstructed at Native runtime. Class names keep hashes stable across that
    // boundary; equals still uses Class identity so same-named loader classes remain distinct.
    hash =
        (targetClass.getName().hashCode() * 31 + role.ordinal()) * 31 + valuesHash(this.keyParts);
  }

  public static GeneratedCodecKey object(Class<?> targetClass, Role role, Object[] keyParts) {
    if (collectionRole(role)) {
      throw new IllegalArgumentException("Collection role requires a collection key");
    }
    return new GeneratedCodecKey(targetClass, role, keyParts);
  }

  public static GeneratedCodecKey collection(
      Class<?> collectionClass, Class<?> elementClass, Role role, boolean stringElements) {
    if (!collectionRole(role)) {
      throw new IllegalArgumentException("Object role requires an object key");
    }
    return new GeneratedCodecKey(
        collectionClass, role, new Object[] {elementClass, stringElements});
  }

  public Class<?> targetClass() {
    return targetClass;
  }

  public Role role() {
    return role;
  }

  /** Returns the element class which owns collection-codec generation. */
  public Class<?> collectionElementClass() {
    requireCollectionRole();
    return (Class<?>) keyParts[0];
  }

  /** Returns whether the collection generator uses its String-specialized body. */
  public boolean stringCollectionElements() {
    requireCollectionRole();
    return (Boolean) keyParts[1];
  }

  /** Returns the first application-owned class whose lifecycle may retain this key. */
  public Class<?> anchorClass() {
    Class<?> preferred = collectionRole(role) ? collectionElementClass() : targetClass;
    if (preferred.getClassLoader() != null) {
      return preferred;
    }
    if (targetClass.getClassLoader() != null) {
      return targetClass;
    }
    for (Object keyPart : keyParts) {
      if (keyPart instanceof Class<?> && ((Class<?>) keyPart).getClassLoader() != null) {
        return (Class<?>) keyPart;
      }
    }
    return preferred;
  }

  /** Returns the identity-deduplicated classes required by compilation in canonical order. */
  public Class<?>[] referencedClasses() {
    ArrayList<Class<?>> classes = new ArrayList<>();
    IdentityHashMap<Class<?>, Boolean> seen = new IdentityHashMap<>();
    addClass(targetClass, classes, seen);
    for (Object keyPart : keyParts) {
      if (keyPart instanceof Class<?>) {
        addClass((Class<?>) keyPart, classes, seen);
      }
    }
    return classes.toArray(new Class<?>[0]);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof GeneratedCodecKey)) {
      return false;
    }
    GeneratedCodecKey that = (GeneratedCodecKey) other;
    return targetClass == that.targetClass
        && role == that.role
        && Arrays.equals(keyParts, that.keyParts);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static void addClass(
      Class<?> type, ArrayList<Class<?>> classes, IdentityHashMap<Class<?>, Boolean> seen) {
    if (seen.put(type, Boolean.TRUE) == null) {
      classes.add(type);
    }
  }

  private void requireCollectionRole() {
    if (!collectionRole(role)) {
      throw new IllegalStateException("Object key has no collection inputs");
    }
  }

  private static boolean collectionRole(Role role) {
    return role == Role.UTF8_COLLECTION_WRITER || role == Role.UTF8_COLLECTION_READER;
  }

  private static int valuesHash(Object[] values) {
    int hash = 1;
    for (Object value : values) {
      int valueHash;
      if (value instanceof Class<?>) {
        valueHash = ((Class<?>) value).getName().hashCode();
      } else if (value instanceof Enum<?>) {
        Enum<?> enumValue = (Enum<?>) value;
        valueHash = enumValue.getDeclaringClass().getName().hashCode() * 31 + enumValue.ordinal();
      } else if (value instanceof Boolean || value instanceof Integer || value instanceof String) {
        valueHash = value.hashCode();
      } else {
        throw new IllegalArgumentException(
            "Unsupported generated codec key part " + value.getClass().getName());
      }
      hash = hash * 31 + valueHash;
    }
    return hash;
  }
}
