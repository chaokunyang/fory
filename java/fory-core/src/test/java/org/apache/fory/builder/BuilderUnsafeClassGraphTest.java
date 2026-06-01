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

public class BuilderUnsafeClassGraphTest {
  private static final Path ROOT_BUILDER = Paths.get("src/main/java/org/apache/fory/builder");
  private static final Path JAVA25_BUILDER = Paths.get("src/main/java25/org/apache/fory/builder");
  private static final Pattern ROOT_UNSAFE_REFERENCE =
      Pattern.compile(
          "import\\s+sun\\.misc\\.Unsafe|"
              + "sun\\.misc\\.Unsafe|"
              + "\\bUnsafe\\.class\\b|"
              + "_JDKAccess\\.UNSAFE|"
              + "TypeRef\\.of\\(Unsafe");
  private static final Pattern JAVA25_UNSAFE_REFERENCE =
      Pattern.compile(
          "import\\s+sun\\.misc\\.Unsafe|"
              + "sun\\.misc\\.Unsafe|"
              + "\\bUnsafe\\.class\\b|"
              + "_JDKAccess\\.UNSAFE|"
              + "TypeRef\\.of\\(Unsafe|"
              + "Class\\.forName\\(\"sun\\.misc\\.Unsafe\"\\)");

  @Test
  public void testUnsafeOwner() throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(ROOT_BUILDER)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                  if (!ROOT_UNSAFE_REFERENCE.matcher(source).find()) {
                    return;
                  }
                  Path relative = ROOT_BUILDER.relativize(path);
                  Path replacement = JAVA25_BUILDER.resolve(relative);
                  if (!Files.exists(replacement)) {
                    violations.add(relative.toString().replace('\\', '/'));
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    assertTrue(
        violations.isEmpty(),
        "Root builder classes that mention Unsafe must have Java 25 replacements: " + violations);
  }

  @Test
  public void testJava25OwnerIsClean() throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(JAVA25_BUILDER)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                  if (JAVA25_UNSAFE_REFERENCE.matcher(source).find()) {
                    violations.add(JAVA25_BUILDER.relativize(path).toString().replace('\\', '/'));
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    assertTrue(
        violations.isEmpty(),
        "Java 25 builder replacements must not reference sun.misc.Unsafe: " + violations);
  }
}
