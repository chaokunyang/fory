---
title: Reference Tracking
sidebar_position: 45
id: xlang_reference_tracking
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

This page explains how Fory handles reference tracking for shared and circular references in cross-language serialization.

## Overview

Reference tracking enables:

- **Shared references**: Same object referenced multiple times is serialized once
- **Circular references**: Objects that reference themselves or form cycles
- **Memory efficiency**: No duplicate data for repeated objects

## Enabling Reference Tracking

### Java

```java
Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)
    .withRefTracking(true)
    .build();
```

### Python

```python
fory = pyfory.Fory(xlang=True, ref_tracking=True)
```

### Go

```go
fory := forygo.NewFory(true)  // true enables ref tracking
```

### C++

```cpp
auto fory = fory::Fory::create(fory::Config{
    .ref_tracking = true
});
```

### Rust

```rust
let fory = Fory::builder()
    .with_ref_tracking(true)
    .build();
```

## Wire Format

When reference tracking is enabled, nullable fields write a **ref flag byte** before the value:

```
[ref_flag] [value data if not null/ref]
```

Where `ref_flag` is:

| Value                      | Meaning                                               |
| -------------------------- | ----------------------------------------------------- |
| `-1` (NULL_FLAG)           | Value is null                                         |
| `-2` (NOT_NULL_VALUE_FLAG) | Value is present, first occurrence                    |
| `≥0`                       | Reference ID pointing to previously serialized object |

## Reference Tracking vs Nullability

These are **independent** concepts:

| Concept                | Purpose                                    | Controlled By                            |
| ---------------------- | ------------------------------------------ | ---------------------------------------- |
| **Nullability**        | Whether a field can hold null values       | Field type (`Optional<T>`) or annotation |
| **Reference Tracking** | Whether duplicate objects are deduplicated | Global `refTracking` option              |

Key behavior:

- Ref flag bytes are **only written for nullable fields**
- Non-nullable fields skip ref flags entirely, even with `refTracking=true`
- Reference deduplication only applies to objects that appear multiple times

```java
// Reference tracking enabled, but non-nullable fields still skip ref flags
Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)
    .withRefTracking(true)
    .build();
```

## Example: Shared References

```java
public class Container {
    List<String> data;
    List<String> sameData;  // Points to same list
}

Container obj = new Container();
obj.data = Arrays.asList("a", "b", "c");
obj.sameData = obj.data;  // Shared reference

// With refTracking=true: data serialized once, sameData stores reference ID
// With refTracking=false: data serialized twice (duplicate)
```

## Example: Circular References

```java
public class Node {
    String value;
    Node next;
}

Node a = new Node("A");
Node b = new Node("B");
a.next = b;
b.next = a;  // Circular reference

// With refTracking=true: works correctly
// With refTracking=false: infinite recursion error
```

## Language Support

| Language   | Shared Refs | Circular Refs        |
| ---------- | ----------- | -------------------- |
| Java       | Yes         | Yes                  |
| Python     | Yes         | Yes                  |
| Go         | Yes         | Yes                  |
| C++        | Yes         | Yes                  |
| JavaScript | Yes         | Yes                  |
| Rust       | Yes         | No (ownership rules) |

## Performance Considerations

- **Overhead**: Reference tracking adds a hash map lookup per object
- **When to enable**: Use when data has shared/circular references
- **When to disable**: Use for simple data structures without sharing

## See Also

- [Field Nullability](field-nullability.md) - How nullability affects serialization
- [Serialization](serialization.md) - Basic cross-language serialization examples
- [Xlang Specification](https://fory.apache.org/docs/specification/fory_xlang_serialization_spec) - Binary protocol details
