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
import java.util.Arrays;
import java.util.List;
import org.apache.fory.test.TestUtils;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** Executes cross-language tests against the Python implementation. */
@Test
public class PythonXlangTest extends XlangTestBase {
  private static final String PYTHON_EXECUTABLE = "python";
  private static final String PYTHON_MODULE = "pyfory.tests.xlang_test_main";

  private static final List<String> PYTHON_BASE_COMMAND =
      Arrays.asList(PYTHON_EXECUTABLE, "-m", PYTHON_MODULE, "<PYTHON_TESTCASE>");

  private static final int PYTHON_TESTCASE_INDEX = 3;

  @Override
  protected void ensurePeerReady() {
    String enabled = System.getenv("FORY_PYTHON_JAVA_CI");
    if (!"1".equals(enabled)) {
      throw new SkipException("Skipping PythonXlangTest: FORY_PYTHON_JAVA_CI not set to 1");
    }
    TestUtils.verifyPyforyInstalled();
  }

  @Override
  protected CommandContext buildCommandContext(String caseName, Path dataFile) {
    List<String> command = new ArrayList<>(PYTHON_BASE_COMMAND);
    command.set(PYTHON_TESTCASE_INDEX, caseName);
    ImmutableMap<String, String> env =
        envBuilder(dataFile).put("ENABLE_CROSS_LANGUAGE_TESTS", "true").build();
    return new CommandContext(command, env, new File("../../python"));
  }

  // ============================================================================
  // Skip tests that are similar to CrossLanguageTest.java
  // These tests are already covered in CrossLanguageTest which tests Java-Python
  // ============================================================================

  @Override
  @Test
  public void testBuffer() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testMurmurHash3() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testCrossLanguageSerializer() throws Exception {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testList() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testMap() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testItem() throws IOException {
    throw new SkipException("Skipping: simple struct tests covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testColor() throws IOException {
    throw new SkipException("Skipping: enum tests covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testStructWithList() throws IOException {
    throw new SkipException("Skipping: struct with list covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testStructWithMap() throws IOException {
    throw new SkipException("Skipping: struct with map covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testBufferVar() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  @Override
  @Test
  public void testInteger() throws IOException {
    throw new SkipException("Skipping: similar test already covered in CrossLanguageTest");
  }

  // All other tests from XlangTestBase are inherited and will run:
  // - testStringSerializer
  // - testSimpleStruct
  // - testSimpleNamedStruct
  // - testSkipIdCustom
  // - testSkipNameCustom
  // - testConsistentNamed
  // - testStructVersionCheck
  // - testPolymorphicList
  // - testPolymorphicMap
}
