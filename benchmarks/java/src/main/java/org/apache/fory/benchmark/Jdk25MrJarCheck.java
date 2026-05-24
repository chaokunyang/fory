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
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.serializer.StringSerializer;

/** Runtime smoke check that JDK25 benchmark runs load the multi-release Fory classes. */
public final class Jdk25MrJarCheck {
  private Jdk25MrJarCheck() {}

  public static void main(String[] args) {
    verifyClass(MemoryBuffer.class);
    verifyClass(UnsafeOps.class);
    verifyClass(_JDKAccess.class);
    verifyClass(FieldAccessor.class);
    verifyClass(StringSerializer.class);
    if (_JDKAccess.UNSAFE != null) {
      throw new IllegalStateException("JDK25 benchmark jar loaded Unsafe-backed _JDKAccess");
    }
  }

  private static void verifyClass(Class<?> cls) {
    String resourceName = cls.getSimpleName() + ".class";
    String resource = String.valueOf(cls.getResource(resourceName));
    if (!resource.contains("benchmarks.jar!") || !resource.contains("!/META-INF/versions/25/")) {
      throw new IllegalStateException("JDK25 benchmark jar loaded root class for " + cls);
    }
  }
}
