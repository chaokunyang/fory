/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fory.platform;

/** JDK version facts which are safe to load without initializing Unsafe-backed code. */
public final class JdkVersion {
  public static final int MAJOR_VERSION = parseMajorVersion();

  private JdkVersion() {}

  private static int parseMajorVersion() {
    String version = System.getProperty("java.specification.version");
    if (version == null || version.isEmpty()) {
      version = System.getProperty("java.version", "");
    }
    return parseMajorVersion(version);
  }

  static int parseMajorVersion(String version) {
    if (version == null || version.isEmpty()) {
      return 0;
    }
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    int end = 0;
    while (end < version.length()) {
      char c = version.charAt(end);
      if (c < '0' || c > '9') {
        break;
      }
      end++;
    }
    if (end == 0) {
      return 0;
    }
    return Integer.parseInt(version.substring(0, end));
  }
}
