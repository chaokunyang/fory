---
title: Dart Serialization Guide
sidebar_position: 0
id: dart_serialization_index
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

Apache Fory™ Dart is the xlang runtime for Dart and Flutter-style environments. It reads and writes the same cross-language wire format defined by the [xlang serialization specification](../../specification/xlang_serialization_spec.md) and is designed around generated serializers plus an advanced manual serializer escape hatch.

## Why Fory Dart?

- Cross-language serialization with the Fory xlang format
- Generated serializers for annotated Dart models
- Compatible-mode schema evolution for evolving structs
- Explicit reference tracking for shared and circular object graphs
- Manual serializer support for external types, special wire behavior, and unions
- A small public API centered on `Fory`, `Config`, annotations, and xlang value wrappers

## Runtime Model

The Dart runtime only supports xlang payloads. There is no separate native-mode builder. Root operations happen through `Fory`, while nested payload work stays in explicit `WriteContext` and `ReadContext` instances. This mirrors the ownership model described in the [xlang implementation guide](../../specification/xlang_implementation_guide.md).

## Quick Start

### Requirements

- Dart SDK 3.6 or later
- `build_runner` for generated serializers

### Install

Add the package from the `dart/packages/fory` workspace or publish source once available for your environment.

```yaml
dependencies:
  fory:
    path: packages/fory

dev_dependencies:
  build_runner: ^2.4.0
```

### Basic Example

```dart
import 'package:fory/fory.dart';

part 'person.fory.dart';

enum Color {
  red,
  blue,
}

@ForyStruct()
class Person {
  Person();

  String name = '';
  Int32 age = Int32(0);
  Color favoriteColor = Color.red;
  List<String> tags = <String>[];
}

void main() {
  final fory = Fory();
  PersonFory.register(
    fory,
    Color,
    namespace: 'example',
    typeName: 'Color',
  );
  PersonFory.register(
    fory,
    Person,
    namespace: 'example',
    typeName: 'Person',
  );

  final person = Person()
    ..name = 'Ada'
    ..age = Int32(36)
    ..favoriteColor = Color.blue
    ..tags = <String>['engineer', 'mathematician'];

  final bytes = fory.serialize(person);
  final roundTrip = fory.deserialize<Person>(bytes);
  print(roundTrip.name);
}
```

Generate the companion file before running the program:

```bash
cd dart/packages/fory
dart run build_runner build --delete-conflicting-outputs
```

## Core API Surface

- `Fory({Config config = const Config()})`
- `serialize(Object? value, {bool trackRef = false})`
- `deserialize<T>(Uint8List bytes)`
- `register(Type type, {int? id, String? namespace, String? typeName})`
- `registerSerializer(Type type, Serializer serializer, ...)`
- `@ForyStruct()` and `@ForyField(...)` for generated serializers
- xlang value wrappers such as `Int8`, `Int16`, `Int32`, `UInt8`, `UInt16`, `UInt32`, `Float16`, `Float32`, `LocalDate`, and `Timestamp`

## Documentation

| Topic                                         | Description                                                     |
| --------------------------------------------- | --------------------------------------------------------------- |
| [Configuration](configuration.md)             | Runtime options, compatible mode, and safety limits             |
| [Basic Serialization](basic-serialization.md) | `serialize`, `deserialize`, generated registration, root graphs |
| [Code Generation](code-generation.md)         | `@ForyStruct`, build runner, and generated namespaces           |
| [Type Registration](type-registration.md)     | ID-based vs name-based registration and registration rules      |
| [Custom Serializers](custom-serializers.md)   | Manual `Serializer<T>` implementations and unions               |
| [Field Configuration](field-configuration.md) | `@ForyField`, field IDs, nullability, references, polymorphism  |
| [Supported Types](supported-types.md)         | Built-in xlang values, wrappers, collections, and structs       |
| [Schema Evolution](schema-evolution.md)       | Compatible structs and evolving schemas                         |
| [Cross-Language](cross-language.md)           | Interoperability rules and field alignment                      |
| [Troubleshooting](troubleshooting.md)         | Common errors, diagnostics, and validation steps                |

## Related Resources

- [Xlang serialization specification](../../specification/xlang_serialization_spec.md)
- [Xlang implementation guide](../../specification/xlang_implementation_guide.md)
- [Cross-language guide](../xlang/index.md)
- [Dart runtime source directory](https://github.com/apache/fory/tree/main/dart)
