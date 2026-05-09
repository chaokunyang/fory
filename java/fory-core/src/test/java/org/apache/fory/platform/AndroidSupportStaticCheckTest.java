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

package org.apache.fory.platform;

import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.testng.annotations.Test;

public class AndroidSupportStaticCheckTest {
  private static final Pattern DIRECT_CLASS_VALUE =
      Pattern.compile("\\bClassValue\\s*<|new\\s+ClassValue\\s*<");

  @Test
  public void testNoDirectClassValueUsageInCoreSources() throws IOException {
    Path sourceRoot = Paths.get("src/main/java/org/apache/fory");
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                  if (DIRECT_CLASS_VALUE.matcher(source).find()) {
                    violations.add(sourceRoot.relativize(path).toString());
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    assertTrue(
        violations.isEmpty(),
        "Direct ClassValue usage is not Android-safe; use ClassValueCache instead: " + violations);
  }
}
