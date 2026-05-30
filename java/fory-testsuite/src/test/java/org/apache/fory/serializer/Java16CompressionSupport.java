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

package org.apache.fory.serializer;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.SkipException;

final class Java16CompressionSupport {
  private static volatile ClassLoader loader;

  private Java16CompressionSupport() {}

  static Class<?> loadClass(String name) {
    if (javaMajorVersion() < 16) {
      throw new SkipException("Compressed array serializers are Java 16+ multi-release classes.");
    }
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException ignored) {
      // Reactor test classpaths use fory-core/target/classes, which does not expose versioned
      // entries as MR-JAR classes. Load the compiled Java 16 output directly instead.
    }
    try {
      return Class.forName(name, true, getLoader());
    } catch (ClassNotFoundException e) {
      throw new AssertionError("Missing Java 16 compression class: " + name, e);
    }
  }

  private static ClassLoader getLoader() {
    ClassLoader current = loader;
    if (current != null) {
      return current;
    }
    Path java16Classes =
        Paths.get(System.getProperty("user.dir"))
            .getParent()
            .resolve("fory-core")
            .resolve("target")
            .resolve("jpms-classes")
            .resolve("java16");
    if (!Files.isDirectory(java16Classes)) {
      throw new AssertionError(
          "Missing " + java16Classes + "; run fory-testsuite with -am or build fory-core first.");
    }
    try {
      current =
          new URLClassLoader(
              new URL[] {java16Classes.toUri().toURL()},
              Thread.currentThread().getContextClassLoader());
    } catch (MalformedURLException e) {
      throw new AssertionError("Invalid Java 16 class output path: " + java16Classes, e);
    }
    loader = current;
    return current;
  }

  private static int javaMajorVersion() {
    String version = System.getProperty("java.specification.version");
    if (version.startsWith("1.")) {
      return Integer.parseInt(version.substring(2));
    }
    int dot = version.indexOf('.');
    return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
  }
}
