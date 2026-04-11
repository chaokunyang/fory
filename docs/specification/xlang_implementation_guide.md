---
title: Xlang Implementation Guide
sidebar_position: 10
id: xlang_implementation_guide
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

## Overview

This guide describes the current xlang runtime shape used by the Dart rewrite and
the ownership model other runtimes should mirror when they implement the same
protocol behavior.

When this guide conflicts with the wire-format specification, follow
`docs/specification/xlang_serialization_spec.md`. When it conflicts with a
runtime-specific implementation detail, follow the current runtime code for that
language.

## Source Of Truth

Use these sources in this order:

1. `docs/specification/xlang_serialization_spec.md`
2. The current runtime implementation for the language
3. Cross-language tests under `integration_tests/`

For Dart, the runtime shape is centered on:

- `Fory`
- `WriteContext`
- `ReadContext`
- `RefWriter`
- `RefReader`
- `TypeResolver`

## Runtime Ownership

`Fory` is the root operation facade. It owns exactly four runtime members:

- `Buffer`
- `WriteContext`
- `ReadContext`
- `TypeResolver`

`Fory` is responsible for:

- preparing the shared buffer for root operations
- writing and reading the root xlang header
- delegating value encoding to `WriteContext`
- delegating value decoding to `ReadContext`
- owning type registration through `TypeResolver`

`Fory` must not accumulate serializer-specific mutable state beyond those four
runtime members.

## Public Dart API

The Dart package exports only the user-facing API:

- `Fory`
- `Config`
- `Buffer`
- `WriteContext`
- `ReadContext`
- `Serializer`
- `ForyStruct`
- `ForyField`
- built-in wrapper/value types such as `Int32`, `Float32`, `LocalDate`, and `Timestamp`

Important public API rules:

- `Config` contains only `compatible`, `checkStructVersion`, `maxDepth`,
  `maxCollectionSize`, and `maxBinarySize`.
- `compatible` is the only public schema-mode switch.
- field-level reference tracking is controlled by `@ForyField(ref: true)`.
- root graph reference tracking is controlled only by
  `serialize(..., trackRef: true)` and `serializeTo(..., trackRef: true)`.
- `ForyField.dynamic` is `bool?`, where `null` means auto, `false` means use
  the declared type, and `true` means write runtime type metadata.

## Context Responsibilities

### WriteContext

`WriteContext` owns all write-side per-operation state:

- current `Buffer`
- `RefWriter`
- shared TypeDef write state
- meta-string write cache
- root `trackRef` mode
- recursion depth and limits

It exposes one-shot primitive helpers such as:

- `writeBool`
- `writeInt32`
- `writeVarUint32`

These helpers are convenience methods. Serializers that perform repeated
primitive IO should cache `final buffer = context.buffer;` and call buffer
methods directly.

### ReadContext

`ReadContext` owns all read-side per-operation state:

- current `Buffer`
- `RefReader`
- shared TypeDef read state
- meta-string read cache
- compatible-struct field mapping state
- recursion depth and limits

It exposes the matching one-shot primitive helpers such as:

- `readBool`
- `readInt32`
- `readVarUint32`

Generated struct serializers call `context.reference(value)` immediately after
constructing the target instance so later ref slots can resolve to that object.

## RefWriter And RefReader

`RefWriter` and `RefReader` are internal collaborators owned by the contexts.
They implement xlang ref flags:

- `NULL (-3)`
- `REF (-2)`
- `NOT_NULL (-1)`
- `REF_VALUE (0)`

Key behavior:

- basic values never use ref tracking
- field metadata controls ref behavior inside generated structs
- root `trackRef` is only for top-level graphs and container roots with no field
  metadata
- generated struct reads and writes must preserve the current object so annotated
  self-references can resolve correctly

## TypeResolver

`TypeResolver` is the internal registry and type-dispatch owner. It is
responsible for:

- built-in type resolution
- registration by numeric id or by `namespace + typeName`
- serializer lookup
- struct metadata lookup
- wire type decisions for struct, compatible struct, enum, ext, and union forms

Generator-only metadata should stay off the public `Serializer` API. The Dart
runtime keeps those details in internal generated serializer base classes.

## Root Wire Flow

Root operations follow this framing:

1. write or read the root header bitmap
2. write or read root ref metadata
3. write or read type metadata
4. write or read the value payload

For non-null xlang values the root header must set the xlang bit. Null roots use
the null bit and stop there.

## Compatible Structs

When `Config.compatible` is enabled and the struct is marked evolving:

- the wire type uses the compatible struct form
- the runtime writes shared TypeDef metadata
- reads map incoming fields by identifier and skip unknown fields

When `compatible` is disabled and `checkStructVersion` is enabled:

- the runtime writes the schema hash for struct payloads
- the read side checks that hash before reading fields

## Code Generation

The normal Dart integration path is:

1. annotate structs with `@ForyStruct`
2. annotate field overrides with `@ForyField`
3. run `build_runner`
4. call the generated registration helper for that library

Generated code should emit:

- private serializer classes
- private metadata constants
- one generated registration helper per annotated library

Generated code should not create a public global registry or a second public API
family.

## Directory Layout

Under each Dart package `lib/` tree, only one nested source layer is allowed.

Allowed:

- `lib/fory.dart`
- `lib/src/<file>.dart`
- `lib/src/<area>/<file>.dart`

Not allowed:

- `lib/src/<area>/<subarea>/<file>.dart`

## Validation

For Dart runtime changes, run at minimum:

```bash
cd dart
dart run build_runner build --delete-conflicting-outputs
dart analyze
dart test
```

For generated consumer coverage, also run:

```bash
cd dart/packages/fory-test
dart run build_runner build --delete-conflicting-outputs
dart test
```
