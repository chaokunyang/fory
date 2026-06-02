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

package org.apache.fory.benchmark;

import org.apache.fory.memory.MemoryBuffer;

/** Runtime smoke check that JDK25 benchmark runs load the multi-release Fory classes. */
public final class Jdk25MrJarCheck {
  private Jdk25MrJarCheck() {}

  public static void main(String[] args) {
    verifyVersionedClass(MemoryBuffer.class);
    verifyMissing("org.apache.fory.platform.UnsafeOps");
    Class<?> jdkAccess = verifyRootClass("org.apache.fory.platform.internal._JDKAccess");
    verifyVersionedClass("org.apache.fory.reflect.FieldAccessorStrategy");
    verifyVersionedClass("org.apache.fory.serializer.PlatformStringUtils");
    if (hasUnsafeField(jdkAccess)) {
      throw new IllegalStateException("JDK25 benchmark jar loaded Unsafe-owning _JDKAccess");
    }
  }

  private static void verifyMissing(String className) {
    try {
      Class.forName(className);
      throw new IllegalStateException("JDK25 benchmark jar must not contain " + className);
    } catch (ClassNotFoundException expected) {
      // expected
    }
  }

  private static Class<?> verifyClass(String className) {
    try {
      Class<?> cls = Class.forName(className);
      return cls;
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("JDK25 benchmark jar is missing " + className, e);
    }
  }

  private static Class<?> verifyVersionedClass(String className) {
    Class<?> cls = verifyClass(className);
    verifyVersionedClass(cls);
    return cls;
  }

  private static void verifyVersionedClass(Class<?> cls) {
    String resourceName = cls.getSimpleName() + ".class";
    String resource = String.valueOf(cls.getResource(resourceName));
    if (!resource.contains("benchmarks.jar!") || !resource.contains("!/META-INF/versions/25/")) {
      throw new IllegalStateException("JDK25 benchmark jar loaded root class for " + cls);
    }
  }

  private static Class<?> verifyRootClass(String className) {
    Class<?> cls = verifyClass(className);
    String resourceName = cls.getSimpleName() + ".class";
    String resource = String.valueOf(cls.getResource(resourceName));
    if (!resource.contains("benchmarks.jar!") || resource.contains("!/META-INF/versions/25/")) {
      throw new IllegalStateException("JDK25 benchmark jar loaded versioned class for " + cls);
    }
    return cls;
  }

  private static boolean hasUnsafeField(Class<?> jdkAccess) {
    for (java.lang.reflect.Field field : jdkAccess.getDeclaredFields()) {
      if (field.getName().equals("UNSAFE") || field.getType().getName().equals("sun.misc.Unsafe")) {
        return true;
      }
    }
    return false;
  }
}
