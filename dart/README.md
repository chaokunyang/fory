# Apache Fory™ Dart

Apache Fory™ Dart is the Dart xlang runtime for Apache Fory™. It reads and
writes Fory's cross-language wire format and works in both Dart and Flutter
applications. Because Flutter prohibits `dart:mirrors`, the runtime uses static
code generation for type handling.

The publishable package lives at `packages/fory/`. See its
[README](packages/fory/README.md) for the full user-facing documentation
including getting started, API reference, and code examples.

## Project Structure

| Directory                        | Description                             |
| -------------------------------- | --------------------------------------- |
| `packages/fory/lib/`             | Core runtime and public API             |
| `packages/fory/lib/src/codegen/` | Build-runner code generator             |
| `packages/fory/example/`         | Annotated example with generated output |
| `packages/fory/test/`            | Unit and integration tests              |
| `test/`                          | Cross-language integration tests        |

## Type Mapping

| Fory xlang type | Dart type                |
| --------------- | ------------------------ |
| bool            | `bool`                   |
| int8            | `fory.Int8` (wrapper)    |
| int16           | `fory.Int16` (wrapper)   |
| int32           | `fory.Int32` (wrapper)   |
| int64           | `int`                    |
| float16         | `fory.Float16` (wrapper) |
| float32         | `fory.Float32` (wrapper) |
| float64         | `double`                 |
| string          | `String`                 |
| binary          | `Uint8List`              |
| local_date      | `LocalDate`              |
| timestamp       | `Timestamp`              |
| list            | `List`                   |
| set             | `Set`                    |
| map             | `Map`                    |
| enum            | `enum`                   |
| named_struct    | `class`                  |
| bool_array      | `List<bool>`             |
| int8_array      | `Int8List`               |
| int16_array     | `Int16List`              |
| int32_array     | `Int32List`              |
| int64_array     | `Int64List`              |
| float32_array   | `Float32List`            |
| float64_array   | `Float64List`            |

## Quick Start

Annotate your model and run the code generator:

```dart
import 'package:fory/fory.dart';

part 'person.fory.dart';

@ForyStruct()
class Person {
  Person();

  String name = '';
  Int32 age = Int32(0);
}
```

```bash
dart run build_runner build --delete-conflicting-outputs
```

Serialize and deserialize:

```dart
final fory = Fory();
PersonFory.register(fory, Person, namespace: 'example', typeName: 'Person');

final bytes = fory.serialize(Person()..name = 'Ada'..age = Int32(36));
final roundTrip = fory.deserialize<Person>(bytes);
```

## Development

Run tests from the workspace root:

```bash
cd packages/fory
dart test
```

Run the code generator on the example:

```bash
cd packages/fory
dart run build_runner build --delete-conflicting-outputs
```
