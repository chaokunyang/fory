---
title: Native Serialization
sidebar_position: 3
id: native
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

Native serialization uses a runtime-specific wire format and the owning language's native type
system. Choose it when every writer and reader uses the same runtime and the payload should preserve
language-specific types or behavior. Native payloads from different runtime families are not
interchangeable.

Use [xlang serialization](xlang.md) whenever another Fory runtime must read the bytes. Xlang uses a
shared type system and wire format; native mode deliberately stays closer to one language's object
model.

## When To Use Native Mode

Native mode is the right choice when:

- every producer and consumer uses the same language runtime;
- the object graph contains language-specific types or behavior outside the portable xlang type
  mapping;
- an application is moving from an existing same-language serializer and should keep its current
  object model instead of introducing a cross-language schema; or
- stored or transported data is an internal runtime boundary rather than a contract shared with
  other languages.

Use xlang instead when the bytes cross a language boundary, when multiple runtime implementations
share the contract, or when portability is more important than the full native object surface.

| Scenario                                                      | Recommended mode |
| ------------------------------------------------------------- | ---------------- |
| Same-runtime object graph with language-specific types        | Native           |
| Replacement for an existing same-language object serializer   | Native           |
| Data exchanged by two or more Fory language implementations   | Xlang            |
| A long-lived contract intended to remain language-independent | Xlang            |

## Java

Choose [Java native serialization](java/native.md) for Java/JVM-only payloads that need a broader
Java object surface than the portable xlang mapping. This includes ordinary Java objects, records,
enums, primitive and object arrays, common JDK collections and wrappers, interfaces, inheritance,
shared references, and circular object graphs.

Java native mode also supports classes that use JDK serialization hooks:

- `writeObject` and `readObject`;
- `writeReplace` and `readResolve`;
- `readObjectNoData`; and
- `Externalizable`.

Fory honors these hooks while writing Fory native bytes; it does not emit Java
`ObjectOutputStream` bytes. See [JDK Custom Serialization](java/jdk-serialization.md) for the exact
semantics.

Use Java native mode when replacing Java-only use of Kryo, FST, Hessian, or JDK serialization and
the existing Java object model should remain the serialization model.

## Python

Choose [Python native serialization](python/native.md) for Python-only payloads that need Python
objects beyond the portable xlang surface. Native mode supports classes, global and local
functions, lambdas, closures, instance methods, class methods, static methods, shared references,
and circular object graphs.

It also supports Python object construction and state hooks, including:

- `__getstate__` and `__setstate__`;
- `__getnewargs__` and `__getnewargs_ex__`; and
- `__reduce__` and `__reduce_ex__`.

Use it when replacing Pickle or cloudpickle for a Python-only object graph. It is also an option for
replacing a Python-only MessagePack boundary when both endpoints move to Fory and the application
wants to serialize Python objects directly. If MessagePack is currently used as a language-neutral
exchange format, use xlang instead.

See [Functions, Classes, and Methods](python/functions-classes-methods.md) and
[Serialization Hooks](python/serialization-hooks.md) for the supported Python object shapes and
reconstruction behavior.

## Rust

Choose [Rust native serialization](rust/native.md) for Rust-only payloads that use a broader Rust
type and ownership model than the portable xlang mapping. Examples include Rust-specific integer
types such as `u128`, `i128`, `usize`, and `isize`; typed `Rc<T>` and `Arc<T>` values; weak
references; `RefCell<T>`; `Mutex<T>`; and native Rust targets carried through traits or `dyn Any`.

Rust native mode also supports data-carrying, struct-style enums whose variants contain multiple
fields. A `#[derive(ForyUnion)]` enum can mix unit variants, tuple variants with one or more fields,
and named variants with one or more fields:

```rust
use fory::ForyUnion;

#[derive(ForyUnion)]
enum Command {
    #[fory(default)]
    Idle,
    Move(i32, i32),
    Create { id: u128, label: String },
}
```

An xlang UNION alternative carries at most one declared payload value. Multiple logical fields must
be wrapped in an explicitly declared struct for xlang, while Rust native mode can encode the tuple
or named fields directly. See [Rust Enum Support](rust/schema-evolution.md#enum-support) and
[External-Type Serialization](rust/external-types.md#native-struct-style-enums) for local and
third-party enum shapes.

## C++

Choose [C++ native serialization](cpp/native.md) when every endpoint is C++ and the data model
should preserve a broader C++ type surface without portable xlang mapping constraints. This
includes:

- structs and classes described with `FORY_STRUCT`;
- standard containers, `std::optional`, `std::variant`, and tuple-like values;
- `std::shared_ptr` and `std::unique_ptr` object graphs;
- native character types such as `char`, `char16_t`, and `char32_t`; and
- unsigned integer shapes with C++ native-mode type IDs.

Use [Supported Types](cpp/supported-types.md) for the complete C++ type matrix and the corresponding
xlang mappings.

## Migrating From Another Serializer

Native mode is a replacement serialization path, not a decoder for another library's wire format.
Kryo, FST, Hessian, JDK serialization, Pickle, cloudpickle, and MessagePack bytes do not become Fory
native bytes automatically.

Move writers and readers to the matching Fory runtime together. If existing stored data must remain
readable during migration, keep the previous decoder at that boundary and reserialize values with
Fory as they are migrated. Do not use native mode for a boundary that still has readers in another
language.

## Enable Native Mode

| Runtime | Native-mode configuration                 |
| ------- | ----------------------------------------- |
| Java    | `Fory.builder().withXlang(false).build()` |
| Python  | `pyfory.Fory(xlang=False)`                |
| C++     | `Fory::builder().xlang(false).build()`    |
| Rust    | `Fory::builder().xlang(false).build()`    |

## Runtime Guides

- [Java](java/native.md)
- [Python](python/native.md)
- [C++](cpp/native.md)
- [Go](go/native.md)
- [Rust](rust/native.md)
- [Scala](scala/native.md)
- [Kotlin](kotlin/native.md)

Each runtime guide owns its exact supported types, configuration, schema behavior, extension APIs,
and diagnostics.
