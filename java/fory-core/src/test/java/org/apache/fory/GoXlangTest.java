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

package org.apache.fory;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** Executes cross-language tests against the Go implementation. */
@Test
public class GoXlangTest extends XlangTestBase {
  private static final String GO_BINARY = "xlang_test_main";

  @Override
  protected void ensurePeerReady() {
    String enabled = System.getenv("FORY_GO_JAVA_CI");
    if (!"1".equals(enabled)) {
      throw new SkipException("Skipping GoXlangTest: FORY_GO_JAVA_CI not set to 1");
    }
    boolean goInstalled = true;
    try {
      Process process = new ProcessBuilder("go", "version").start();
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        goInstalled = false;
      }
    } catch (IOException | InterruptedException e) {
      goInstalled = false;
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    if (!goInstalled) {
      throw new SkipException("Skipping GoXlangTest: go not installed");
    }
    // Check if binary exists
    File binaryFile = new File("../../go/fory/tests/" + GO_BINARY);
    if (!binaryFile.exists()) {
      throw new SkipException("Skipping GoXlangTest: " + GO_BINARY + " not found. Please build it with 'cd go/fory/tests/xlang && go build -o ../xlang_test_main xlang_test_main.go'");
    }
  }

  @Override
  protected CommandContext buildCommandContext(String caseName, Path dataFile) {
    List<String> command = new ArrayList<>();
    command.add("./" + GO_BINARY);
    command.add("--case");
    command.add(caseName);
    ImmutableMap<String, String> env = envBuilder(dataFile).build();
    return new CommandContext(command, env, new File("../../go/fory/tests"));
  }
}
