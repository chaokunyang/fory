# Apache Fory Dart

This package provides the Dart runtime for Apache Fory xlang serialization.

The runtime is built around a small public surface:

- `Fory`
- `Config`
- `Buffer`
- `WriteContext`
- `ReadContext`
- `Serializer`
- `ForyObject`
- `ForyField`

The generated registration helper for annotated libraries is the normal integration path for
structs and enums.
