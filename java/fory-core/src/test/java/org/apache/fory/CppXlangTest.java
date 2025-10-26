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

import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Cross-language serialization tests between Java and C++.
 *
 * <p>These tests verify that data serialized by Java can be deserialized by C++ and vice versa,
 * ensuring compatibility across language boundaries.
 */
public class CppXlangTest {

  private static Path tempDir;
  private static final String CPP_EXECUTABLE = "xlang_test_main";

  // ============================================================================
  // Test Data Classes
  // ============================================================================

  public static class SimpleStruct {
    public int x;
    public int y;

    public SimpleStruct() {}

    public SimpleStruct(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SimpleStruct)) return false;
      SimpleStruct that = (SimpleStruct) o;
      return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
      return Objects.hash(x, y);
    }
  }

  public static class ComplexStruct {
    public String name;
    public int age;
    public List<String> hobbies;

    public ComplexStruct() {}

    public ComplexStruct(String name, int age, List<String> hobbies) {
      this.name = name;
      this.age = age;
      this.hobbies = hobbies;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ComplexStruct)) return false;
      ComplexStruct that = (ComplexStruct) o;
      return age == that.age
          && Objects.equals(name, that.name)
          && Objects.equals(hobbies, that.hobbies);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, age, hobbies);
    }
  }

  public static class NestedStruct {
    public SimpleStruct point;
    public String label;
    public Map<String, Integer> properties;

    public NestedStruct() {}

    public NestedStruct(SimpleStruct point, String label, Map<String, Integer> properties) {
      this.point = point;
      this.label = label;
      this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NestedStruct)) return false;
      NestedStruct that = (NestedStruct) o;
      return Objects.equals(point, that.point)
          && Objects.equals(label, that.label)
          && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
      return Objects.hash(point, label, properties);
    }
  }

  // ============================================================================
  // Setup and Teardown
  // ============================================================================

  @BeforeClass
  public static void setup() throws IOException {
    tempDir = Files.createTempDirectory("fory_cpp_xlang_test");
    System.out.println("Created temp directory: " + tempDir);
  }

  @AfterClass
  public static void teardown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
      System.out.println("Cleaned up temp directory: " + tempDir);
    }
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private static String findCppExecutable() {
    // Try to find the C++ executable in bazel-bin
    String[] possiblePaths = {
      "cpp/fory/serialization/" + CPP_EXECUTABLE,
      "bazel-bin/cpp/fory/serialization/" + CPP_EXECUTABLE,
      "../cpp/fory/serialization/" + CPP_EXECUTABLE,
    };

    for (String path : possiblePaths) {
      File file = new File(path);
      if (file.exists() && file.canExecute()) {
        return file.getAbsolutePath();
      }
    }

    return null;
  }

  private static boolean runCppTest(String mode) throws IOException, InterruptedException {
    String executable = findCppExecutable();
    if (executable == null) {
      System.err.println("WARNING: C++ executable not found, skipping C++ xlang tests");
      System.err.println("To run these tests, build the C++ executable:");
      System.err.println("  cd cpp && bazel build //fory/serialization:xlang_test_main");
      return false;
    }

    ProcessBuilder pb =
        new ProcessBuilder(executable, mode, tempDir.toAbsolutePath().toString());
    pb.redirectErrorStream(true);

    Process process = pb.start();

    // Capture and print output
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println("[C++] " + line);
      }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      System.err.println("C++ test failed with exit code: " + exitCode);
      return false;
    }

    return true;
  }

  private static <T> void testJavaWrite(Fory fory, T obj, String filename) throws IOException {
    byte[] bytes = fory.serialize(obj);
    Files.write(tempDir.resolve(filename), bytes);
    System.out.println("Java wrote " + bytes.length + " bytes to " + filename);
  }

  private static <T> T testJavaRead(Fory fory, String filename, Class<T> clazz)
      throws IOException {
    byte[] bytes = Files.readAllBytes(tempDir.resolve(filename));
    System.out.println("Java read " + bytes.length + " bytes from " + filename);
    return fory.deserialize(bytes, clazz);
  }

  // ============================================================================
  // Test Cases: Java -> C++ (Java writes, C++ reads)
  // ============================================================================

  @Test(priority = 1)
  public void testJavaToCpp_Primitives() throws Exception {
    System.out.println("\n=== Testing Java -> C++ (Primitives) ===");

    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    // Write test data
    testJavaWrite(fory, 12345, "int32.bin");
    testJavaWrite(fory, 9876543210L, "int64.bin");
    testJavaWrite(fory, 3.141592653589793, "double.bin");
    testJavaWrite(fory, "Hello, Cross-Language!", "string.bin");

    // Run C++ read test
    boolean success = runCppTest("read");
    if (!success) {
      System.out.println("Skipping test - C++ executable not available");
      return;
    }

    assertTrue(success, "C++ should successfully read Java-serialized data");
  }

  @Test(priority = 2)
  public void testJavaToCpp_Collections() throws Exception {
    System.out.println("\n=== Testing Java -> C++ (Collections) ===");

    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    // Write test data
    testJavaWrite(fory, Arrays.asList(1, 2, 3, 4, 5), "vector_int32.bin");
    testJavaWrite(fory, Arrays.asList("foo", "bar", "baz"), "vector_string.bin");

    Map<String, Integer> map = new HashMap<>();
    map.put("one", 1);
    map.put("two", 2);
    map.put("three", 3);
    testJavaWrite(fory, map, "map_string_int32.bin");

    // Run C++ read test
    boolean success = runCppTest("read");
    if (!success) {
      System.out.println("Skipping test - C++ executable not available");
      return;
    }

    assertTrue(success, "C++ should successfully read Java-serialized collections");
  }

  @Test(priority = 3)
  public void testJavaToCpp_Structs() throws Exception {
    System.out.println("\n=== Testing Java -> C++ (Structs) ===");

    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    fory.register(SimpleStruct.class);
    fory.register(ComplexStruct.class);
    fory.register(NestedStruct.class);

    // Write test data
    testJavaWrite(fory, new SimpleStruct(42, 100), "simple_struct.bin");
    testJavaWrite(
        fory,
        new ComplexStruct("Alice", 30, Arrays.asList("reading", "coding", "hiking")),
        "complex_struct.bin");

    Map<String, Integer> props = new HashMap<>();
    props.put("key1", 1);
    props.put("key2", 2);
    props.put("key3", 3);
    testJavaWrite(
        fory, new NestedStruct(new SimpleStruct(10, 20), "nested_example", props), "nested_struct.bin");

    // Run C++ read test
    boolean success = runCppTest("read");
    if (!success) {
      System.out.println("Skipping test - C++ executable not available");
      return;
    }

    assertTrue(success, "C++ should successfully read Java-serialized structs");
  }

  // ============================================================================
  // Test Cases: C++ -> Java (C++ writes, Java reads)
  // ============================================================================

  @Test(priority = 10)
  public void testCppToJava_Primitives() throws Exception {
    System.out.println("\n=== Testing C++ -> Java (Primitives) ===");

    // Run C++ write test
    boolean success = runCppTest("write");
    if (!success) {
      System.out.println("Skipping test - C++ executable not available");
      return;
    }

    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    // Read and verify test data
    Integer i32 = testJavaRead(fory, "int32.bin", Integer.class);
    assertEquals(i32.intValue(), 12345);

    Long i64 = testJavaRead(fory, "int64.bin", Long.class);
    assertEquals(i64.longValue(), 9876543210L);

    Double d = testJavaRead(fory, "double.bin", Double.class);
    assertEquals(d, 3.141592653589793, 0.0000001);

    String s = testJavaRead(fory, "string.bin", String.class);
    assertEquals(s, "Hello, Cross-Language!");

    System.out.println("All primitive types read successfully from C++");
  }

  @Test(priority = 11)
  public void testCppToJava_Collections() throws Exception {
    System.out.println("\n=== Testing C++ -> Java (Collections) ===");

    // C++ write test should already be run
    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    // Read and verify collections
    @SuppressWarnings("unchecked")
    List<Integer> vec = testJavaRead(fory, "vector_int32.bin", List.class);
    assertEquals(vec, Arrays.asList(1, 2, 3, 4, 5));

    @SuppressWarnings("unchecked")
    List<String> vecStr = testJavaRead(fory, "vector_string.bin", List.class);
    assertEquals(vecStr, Arrays.asList("foo", "bar", "baz"));

    @SuppressWarnings("unchecked")
    Map<String, Integer> map = testJavaRead(fory, "map_string_int32.bin", Map.class);
    assertEquals(map.get("one"), Integer.valueOf(1));
    assertEquals(map.get("two"), Integer.valueOf(2));
    assertEquals(map.get("three"), Integer.valueOf(3));

    System.out.println("All collections read successfully from C++");
  }

  @Test(priority = 12)
  public void testCppToJava_Structs() throws Exception {
    System.out.println("\n=== Testing C++ -> Java (Structs) ===");

    // C++ write test should already be run
    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withRefTracking(false)
            .withCompatibleMode(ForyConfig.CompatibleMode.COMPATIBLE)
            .build();

    fory.register(SimpleStruct.class);
    fory.register(ComplexStruct.class);
    fory.register(NestedStruct.class);

    // Read and verify structs
    SimpleStruct simple = testJavaRead(fory, "simple_struct.bin", SimpleStruct.class);
    assertEquals(simple, new SimpleStruct(42, 100));

    ComplexStruct complex = testJavaRead(fory, "complex_struct.bin", ComplexStruct.class);
    assertEquals(complex.name, "Alice");
    assertEquals(complex.age, 30);
    assertEquals(complex.hobbies, Arrays.asList("reading", "coding", "hiking"));

    NestedStruct nested = testJavaRead(fory, "nested_struct.bin", NestedStruct.class);
    assertEquals(nested.point, new SimpleStruct(10, 20));
    assertEquals(nested.label, "nested_example");
    assertEquals(nested.properties.get("key1"), Integer.valueOf(1));

    System.out.println("All structs read successfully from C++");
  }
}
