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

#include "fory/serialization/fory.h"
#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <vector>

using namespace fory::serialization;

// ============================================================================
// Test Struct Definitions
// ============================================================================

struct SimpleStruct {
  int32_t x;
  int32_t y;

  bool operator==(const SimpleStruct &other) const {
    return x == other.x && y == other.y;
  }
};

FORY_STRUCT(SimpleStruct, x, y);

struct ComplexStruct {
  std::string name;
  int32_t age;
  std::vector<std::string> hobbies;

  bool operator==(const ComplexStruct &other) const {
    return name == other.name && age == other.age && hobbies == other.hobbies;
  }
};

FORY_STRUCT(ComplexStruct, name, age, hobbies);

struct NestedStruct {
  SimpleStruct point;
  std::string label;
  std::map<std::string, int32_t> properties;

  bool operator==(const NestedStruct &other) const {
    return point == other.point && label == other.label &&
           properties == other.properties;
  }
};

FORY_STRUCT(NestedStruct, point, label, properties);

// ============================================================================
// Helper Functions
// ============================================================================

template <typename T>
bool test_serialize_write(const T &obj, const std::string &filename) {
  auto fory = Fory::builder().xlang(true).track_ref(false).build();

  // Serialize
  auto result = fory.serialize(obj);
  if (!result.ok()) {
    std::cerr << "Serialization failed: " << result.error().to_string()
              << std::endl;
    return false;
  }

  std::vector<uint8_t> bytes = std::move(result).value();

  // Write to file
  std::ofstream out(filename, std::ios::binary);
  if (!out.is_open()) {
    std::cerr << "Failed to open file: " << filename << std::endl;
    return false;
  }
  out.write(reinterpret_cast<const char *>(bytes.data()), bytes.size());
  out.close();

  std::cout << "Wrote " << bytes.size() << " bytes to " << filename
            << std::endl;
  return true;
}

template <typename T>
bool test_deserialize_read(const std::string &filename, T &obj) {
  // Read from file
  std::ifstream in(filename, std::ios::binary | std::ios::ate);
  if (!in.is_open()) {
    std::cerr << "Failed to open file: " << filename << std::endl;
    return false;
  }

  size_t size = in.tellg();
  in.seekg(0, std::ios::beg);

  std::vector<uint8_t> bytes(size);
  in.read(reinterpret_cast<char *>(bytes.data()), size);
  in.close();

  std::cout << "Read " << size << " bytes from " << filename << std::endl;

  // Deserialize
  auto fory = Fory::builder().xlang(true).track_ref(false).build();
  auto result = fory.deserialize<T>(bytes.data(), bytes.size());
  if (!result.ok()) {
    std::cerr << "Deserialization failed: " << result.error().to_string()
              << std::endl;
    return false;
  }

  obj = std::move(result).value();
  return true;
}

// ============================================================================
// Main Function
// ============================================================================

int main(int argc, char **argv) {
  if (argc != 3) {
    std::cerr << "Usage: " << argv[0] << " <mode> <dir>" << std::endl;
    std::cerr << "  mode: write | read" << std::endl;
    std::cerr << "  dir:  directory for test files" << std::endl;
    return 1;
  }

  std::string mode = argv[1];
  std::string dir = argv[2];

  if (mode == "write") {
    std::cout << "Writing C++ serialized data for cross-language tests..."
              << std::endl;

    // Test 1: SimpleStruct
    SimpleStruct simple{42, 100};
    if (!test_serialize_write(simple, dir + "/simple_struct.bin")) {
      return 1;
    }

    // Test 2: ComplexStruct
    ComplexStruct complex{"Alice", 30, {"reading", "coding", "hiking"}};
    if (!test_serialize_write(complex, dir + "/complex_struct.bin")) {
      return 1;
    }

    // Test 3: NestedStruct
    NestedStruct nested{SimpleStruct{10, 20},
                        "nested_example",
                        {{"key1", 1}, {"key2", 2}, {"key3", 3}}};
    if (!test_serialize_write(nested, dir + "/nested_struct.bin")) {
      return 1;
    }

    // Test 4: Primitives
    if (!test_serialize_write<int32_t>(12345, dir + "/int32.bin")) {
      return 1;
    }
    if (!test_serialize_write<int64_t>(9876543210LL, dir + "/int64.bin")) {
      return 1;
    }
    if (!test_serialize_write<double>(3.141592653589793,
                                       dir + "/double.bin")) {
      return 1;
    }
    if (!test_serialize_write<std::string>("Hello, Cross-Language!",
                                            dir + "/string.bin")) {
      return 1;
    }

    // Test 5: Collections
    std::vector<int32_t> vec{1, 2, 3, 4, 5};
    if (!test_serialize_write(vec, dir + "/vector_int32.bin")) {
      return 1;
    }

    std::vector<std::string> vec_str{"foo", "bar", "baz"};
    if (!test_serialize_write(vec_str, dir + "/vector_string.bin")) {
      return 1;
    }

    std::map<std::string, int32_t> map{
        {"one", 1}, {"two", 2}, {"three", 3}};
    if (!test_serialize_write(map, dir + "/map_string_int32.bin")) {
      return 1;
    }

    std::cout << "All write tests passed!" << std::endl;
    return 0;

  } else if (mode == "read") {
    std::cout << "Reading C++ serialized data for cross-language tests..."
              << std::endl;

    // Test 1: SimpleStruct
    SimpleStruct simple;
    if (!test_deserialize_read(dir + "/simple_struct.bin", simple)) {
      return 1;
    }
    std::cout << "SimpleStruct: x=" << simple.x << ", y=" << simple.y
              << std::endl;

    // Test 2: ComplexStruct
    ComplexStruct complex;
    if (!test_deserialize_read(dir + "/complex_struct.bin", complex)) {
      return 1;
    }
    std::cout << "ComplexStruct: name=" << complex.name
              << ", age=" << complex.age << ", hobbies=[";
    for (size_t i = 0; i < complex.hobbies.size(); ++i) {
      if (i > 0)
        std::cout << ", ";
      std::cout << complex.hobbies[i];
    }
    std::cout << "]" << std::endl;

    // Test 3: NestedStruct
    NestedStruct nested;
    if (!test_deserialize_read(dir + "/nested_struct.bin", nested)) {
      return 1;
    }
    std::cout << "NestedStruct: point=(" << nested.point.x << ","
              << nested.point.y << "), label=" << nested.label << std::endl;

    // Test 4: Primitives
    int32_t i32;
    if (!test_deserialize_read(dir + "/int32.bin", i32)) {
      return 1;
    }
    std::cout << "int32: " << i32 << std::endl;

    int64_t i64;
    if (!test_deserialize_read(dir + "/int64.bin", i64)) {
      return 1;
    }
    std::cout << "int64: " << i64 << std::endl;

    double d;
    if (!test_deserialize_read(dir + "/double.bin", d)) {
      return 1;
    }
    std::cout << "double: " << d << std::endl;

    std::string s;
    if (!test_deserialize_read(dir + "/string.bin", s)) {
      return 1;
    }
    std::cout << "string: " << s << std::endl;

    // Test 5: Collections
    std::vector<int32_t> vec;
    if (!test_deserialize_read(dir + "/vector_int32.bin", vec)) {
      return 1;
    }
    std::cout << "vector<int32>: [";
    for (size_t i = 0; i < vec.size(); ++i) {
      if (i > 0)
        std::cout << ", ";
      std::cout << vec[i];
    }
    std::cout << "]" << std::endl;

    std::vector<std::string> vec_str;
    if (!test_deserialize_read(dir + "/vector_string.bin", vec_str)) {
      return 1;
    }
    std::cout << "vector<string>: [";
    for (size_t i = 0; i < vec_str.size(); ++i) {
      if (i > 0)
        std::cout << ", ";
      std::cout << vec_str[i];
    }
    std::cout << "]" << std::endl;

    std::map<std::string, int32_t> map;
    if (!test_deserialize_read(dir + "/map_string_int32.bin", map)) {
      return 1;
    }
    std::cout << "map<string,int32>: {";
    bool first = true;
    for (const auto &[k, v] : map) {
      if (!first)
        std::cout << ", ";
      std::cout << k << ":" << v;
      first = false;
    }
    std::cout << "}" << std::endl;

    std::cout << "All read tests passed!" << std::endl;
    return 0;

  } else {
    std::cerr << "Unknown mode: " << mode << std::endl;
    return 1;
  }
}
