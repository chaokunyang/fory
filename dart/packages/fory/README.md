# Apache Fory Dart

This package provides the Dart runtime for Apache Fory xlang serialization.

For normal application code, use annotated objects plus the generated
library-level namespace such as `ExampleFory.registerAll(fory)` or
`ExampleFory.registerType(fory, Person, id: 1)`. Those generated helpers keep
serializer metadata private to the source library and route into
`Fory.register(...)` internally, while manual `Serializer` implementations
remain the advanced escape hatch for external types, custom wire behavior, or
manual union implementations through `Fory.registerSerializer(...)`.

The runtime is built around a small public surface:

- `Fory`
- `Config`
- `Buffer`
- `WriteContext`
- `ReadContext`
- `Serializer`
- `ForyStruct`
- `ForyField`

Generated structs and enums register through the generated library namespace,
which binds generated metadata and then calls `Fory.register(...)`.

## Public API

- `Fory`: root facade for xlang serialization, deserialization, generated type registration, and advanced manual serializer registration.
- `Config`: immutable runtime options for compatible mode and safety limits.
- `Buffer`: reusable byte buffer with explicit reader and writer indices.
- `WriteContext` and `ReadContext`: advanced context APIs used by generated and manual serializers.
- `Serializer`: low-level extension point for manual serializers and generated code.
- `ForyStruct` and `ForyField`: annotations for struct code generation.
- Numeric wrapper and time types such as `Int32`, `UInt32`, `Float16`, `LocalDate`, and `Timestamp`: xlang value types when Dart primitives are not precise enough to describe the wire type.

Refer to the Dart doc comments on each exported symbol for the precise contract of each type and method.

## Example

The primary example uses generated serializers:

1. Generate the example companion file:
   `dart run build_runner build --delete-conflicting-outputs`
2. Run the example:
   `dart run example/example.dart`

The example library exposes `ExampleFory.registerType(...)` and
`ExampleFory.registerAll(...)` for generated registration.

The advanced manual serializer example lives at `example/manual_serializer.dart`.
