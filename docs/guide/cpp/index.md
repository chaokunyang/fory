---
title: C++ Serialization Guide
sidebar_position: 0
id: cpp_serialization_index
license: |
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
---

**Apache Fory™** is a blazing fast multi-language serialization framework powered by **JIT compilation** and **zero-copy** techniques, providing up to **ultra-fast performance** while maintaining ease of use and safety.

The C++ implementation provides high-performance serialization with compile-time type safety using modern C++17 features and template metaprogramming.

## Why Apache Fory™ C++?

- **🔥 Blazingly Fast**: Zero-copy deserialization and optimized binary protocols
- **🌍 Cross-Language**: Seamlessly serialize/deserialize data across Java, Python, C++, Go, JavaScript, and Rust
- **🎯 Type-Safe**: Compile-time type checking with macro-based struct registration
- **🔄 Reference Tracking**: Automatic tracking of shared and circular references
- **📦 Schema Evolution**: Compatible mode for independent schema changes
- **⚡ Two Modes**: Object graph serialization and zero-copy row-based format
- **🧵 Thread Safety**: Both single-threaded (fastest) and thread-safe variants

## Build System

The C++ implementation supports both Bazel and CMake build systems:

```bash
# Build with Bazel
bazel build //cpp/...

# Run tests
bazel test $(bazel query //cpp/...)
```

## Quick Start

### Basic Example

```cpp
#include "fory/serialization/fory.h"

using namespace fory::serialization;

// Define a struct
struct Person {
  std::string name;
  int32_t age;
  std::vector<std::string> hobbies;

  bool operator==(const Person &other) const {
    return name == other.name && age == other.age && hobbies == other.hobbies;
  }
};

// Register the struct with Fory (must be in the same namespace)
FORY_STRUCT(Person, name, age, hobbies);

int main() {
  // Create a Fory instance
  auto fory = Fory::builder()
      .xlang(true)          // Enable cross-language mode
      .track_ref(false)     // Disable reference tracking for simple types
      .build();

  // Register the type with a unique ID
  fory.register_struct<Person>(1);

  // Create an object
  Person person{"Alice", 30, {"reading", "coding"}};

  // Serialize
  auto result = fory.serialize(person);
  if (!result.ok()) {
    // Handle error
    return 1;
  }
  std::vector<uint8_t> bytes = std::move(result).value();

  // Deserialize
  auto deser_result = fory.deserialize<Person>(bytes);
  if (!deser_result.ok()) {
    // Handle error
    return 1;
  }
  Person decoded = std::move(deser_result).value();

  assert(person == decoded);
  return 0;
}
```

## Thread Safety

Apache Fory™ C++ provides two variants for different threading needs:

### Single-Threaded (Fastest)

```cpp
// Single-threaded Fory - fastest, NOT thread-safe
auto fory = Fory::builder()
    .xlang(true)
    .build();
```

### Thread-Safe

```cpp
// Thread-safe Fory - uses context pools
auto fory = Fory::builder()
    .xlang(true)
    .build_thread_safe();

// Can be used from multiple threads safely
std::thread t1([&]() {
  auto result = fory.serialize(obj1);
});
std::thread t2([&]() {
  auto result = fory.serialize(obj2);
});
```

**Tip:** Perform type registrations before spawning threads so every worker sees the same metadata.

## Use Cases

### Object Serialization

- Complex data structures with nested objects and references
- Cross-language communication in microservices
- General-purpose serialization with full type safety
- Schema evolution with compatible mode

### Row-Based Serialization

- High-throughput data processing
- Analytics workloads requiring fast field access
- Memory-constrained environments
- Zero-copy scenarios

## Next Steps

- [Configuration](configuration.md) - Builder options and modes
- [Basic Serialization](basic-serialization.md) - Object graph serialization
- [Schema Evolution](schema-evolution.md) - Compatible mode and schema changes
- [Type Registration](type-registration.md) - Registering types
- [Supported Types](supported-types.md) - All supported types
- [Cross-Language](cross-language.md) - XLANG mode
- [Row Format](row-format.md) - Zero-copy row-based format
