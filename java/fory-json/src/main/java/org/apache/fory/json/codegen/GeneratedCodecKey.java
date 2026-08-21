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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.apache.fory.annotation.Internal;

/** Exact, source-independent identity of one generated JSON capability class. */
@Internal
public final class GeneratedCodecKey {
  private static final int CLASS_VERSION = 1;

  /** Generated capability roles whose classes have independent source shapes. */
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

  /**
   * Stable JVM identity for one reflected member without retaining a reflection-object identity.
   */
  public static final class MemberDescriptor {
    private final Class<?> declaringClass;
    private final byte kind;
    private final String name;
    private final String descriptor;
    private final int hash;

    private MemberDescriptor(Class<?> declaringClass, byte kind, String name, String descriptor) {
      this.declaringClass = declaringClass;
      this.kind = kind;
      this.name = name;
      this.descriptor = descriptor;
      hash =
          (((System.identityHashCode(declaringClass) * 31 + kind) * 31 + name.hashCode()) * 31)
              + descriptor.hashCode();
    }

    public static MemberDescriptor of(Member member) {
      if (member == null) {
        return null;
      }
      if (member instanceof Field) {
        Field field = (Field) member;
        return new MemberDescriptor(
            field.getDeclaringClass(), (byte) 1, field.getName(), descriptor(field.getType()));
      }
      Executable executable = (Executable) member;
      StringBuilder descriptor = new StringBuilder("(");
      for (Class<?> parameter : executable.getParameterTypes()) {
        descriptor.append(descriptor(parameter));
      }
      descriptor.append(')');
      byte kind;
      String name;
      if (executable instanceof Constructor) {
        kind = 2;
        name = "<init>";
        descriptor.append('V');
      } else {
        kind = 3;
        name = executable.getName();
        descriptor.append(descriptor(((Method) executable).getReturnType()));
      }
      return new MemberDescriptor(
          executable.getDeclaringClass(), kind, name, descriptor.toString());
    }

    public Class<?> declaringClass() {
      return declaringClass;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof MemberDescriptor)) {
        return false;
      }
      MemberDescriptor that = (MemberDescriptor) other;
      return declaringClass == that.declaringClass
          && kind == that.kind
          && name.equals(that.name)
          && descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    private static String descriptor(Class<?> type) {
      if (type.isPrimitive()) {
        if (type == void.class) {
          return "V";
        }
        if (type == boolean.class) {
          return "Z";
        }
        if (type == byte.class) {
          return "B";
        }
        if (type == char.class) {
          return "C";
        }
        if (type == short.class) {
          return "S";
        }
        if (type == int.class) {
          return "I";
        }
        if (type == long.class) {
          return "J";
        }
        if (type == float.class) {
          return "F";
        }
        return "D";
      }
      if (type.isArray()) {
        return type.getName().replace('.', '/');
      }
      return "L" + type.getName().replace('.', '/') + ";";
    }
  }

  private final Class<?> targetClass;
  private final Role role;
  private final Object[] keyParts;
  private final Class<?>[] referencedClasses;
  private final Class<?> anchorClass;
  private final int hash;

  private GeneratedCodecKey(
      Class<?> targetClass,
      Role role,
      Object[] keyParts,
      Class<?>[] referencedClasses,
      Class<?> preferredAnchor) {
    this.targetClass = Objects.requireNonNull(targetClass);
    this.role = Objects.requireNonNull(role);
    this.keyParts = keyParts.clone();
    this.referencedClasses = uniqueClasses(targetClass, referencedClasses, keyParts);
    anchorClass = anchor(preferredAnchor, this.referencedClasses);
    hash =
        ((System.identityHashCode(targetClass) * 31 + role.hashCode()) * 31 + CLASS_VERSION) * 31
            + valuesHash(this.keyParts);
  }

  public static GeneratedCodecKey object(
      Class<?> targetClass, Role role, Object[] keyParts, Class<?>[] referencedClasses) {
    if (role == Role.UTF8_COLLECTION_WRITER || role == Role.UTF8_COLLECTION_READER) {
      throw new IllegalArgumentException("Collection role requires a collection key");
    }
    return new GeneratedCodecKey(targetClass, role, keyParts, referencedClasses, targetClass);
  }

  public static GeneratedCodecKey collection(
      Class<?> collectionClass, Class<?> elementClass, Role role, boolean stringElements) {
    if (role != Role.UTF8_COLLECTION_WRITER && role != Role.UTF8_COLLECTION_READER) {
      throw new IllegalArgumentException("Object role requires an object key");
    }
    return new GeneratedCodecKey(
        collectionClass,
        role,
        new Object[] {collectionClass, elementClass, stringElements},
        new Class<?>[] {elementClass, collectionClass},
        elementClass);
  }

  public Class<?> targetClass() {
    return targetClass;
  }

  public Role role() {
    return role;
  }

  /** Returns the first application-owned class whose lifecycle may retain this key. */
  public Class<?> anchorClass() {
    return anchorClass;
  }

  /** Returns the identity-deduplicated classes required by compilation in canonical order. */
  public Class<?>[] referencedClasses() {
    return referencedClasses.clone();
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
        && valuesEqual(keyParts, that.keyParts);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static Class<?>[] uniqueClasses(Class<?> target, Class<?>[] explicit, Object[] keyParts) {
    ArrayList<Class<?>> classes = new ArrayList<>();
    IdentityHashMap<Class<?>, Boolean> seen = new IdentityHashMap<>();
    addClass(target, classes, seen);
    for (Class<?> type : explicit) {
      addClass(type, classes, seen);
    }
    collectClasses(keyParts, classes, seen);
    return classes.toArray(new Class<?>[0]);
  }

  private static void collectClasses(
      Object value, ArrayList<Class<?>> classes, IdentityHashMap<Class<?>, Boolean> seen) {
    if (value instanceof Class<?>) {
      addClass((Class<?>) value, classes, seen);
    } else if (value instanceof MemberDescriptor) {
      addClass(((MemberDescriptor) value).declaringClass, classes, seen);
    } else if (value instanceof Object[]) {
      for (Object item : (Object[]) value) {
        collectClasses(item, classes, seen);
      }
    }
  }

  private static void addClass(
      Class<?> type, ArrayList<Class<?>> classes, IdentityHashMap<Class<?>, Boolean> seen) {
    if (type != null && seen.put(type, Boolean.TRUE) == null) {
      classes.add(type);
    }
  }

  private static Class<?> anchor(Class<?> preferred, Class<?>[] classes) {
    if (preferred.getClassLoader() != null) {
      return preferred;
    }
    for (Class<?> type : classes) {
      if (type.getClassLoader() != null) {
        return type;
      }
    }
    return preferred;
  }

  private static boolean valuesEqual(Object[] left, Object[] right) {
    if (left.length != right.length) {
      return false;
    }
    for (int i = 0; i < left.length; i++) {
      Object a = left[i];
      Object b = right[i];
      if (a instanceof Class<?> || b instanceof Class<?>) {
        if (a != b) {
          return false;
        }
      } else if (a instanceof Object[] && b instanceof Object[]) {
        if (!valuesEqual((Object[]) a, (Object[]) b)) {
          return false;
        }
      } else if (a instanceof byte[] && b instanceof byte[]) {
        if (!Arrays.equals((byte[]) a, (byte[]) b)) {
          return false;
        }
      } else if (!Objects.equals(a, b)) {
        return false;
      }
    }
    return true;
  }

  private static int valuesHash(Object[] values) {
    int hash = 1;
    for (Object value : values) {
      int valueHash;
      if (value instanceof Class<?>) {
        valueHash = System.identityHashCode(value);
      } else if (value instanceof Object[]) {
        valueHash = valuesHash((Object[]) value);
      } else if (value instanceof byte[]) {
        valueHash = Arrays.hashCode((byte[]) value);
      } else {
        valueHash = Objects.hashCode(value);
      }
      hash = hash * 31 + valueHash;
    }
    return hash;
  }
}
