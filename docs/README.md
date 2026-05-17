# User Guide

- For xlang serialization, see the [xlang guide](guide/xlang/index.md).
- For Java serialization, see the [Java guide](guide/java/index.md).
- For row format, see the [row format spec](specification/row_format_spec.md).
- For Scala serialization, see the [Scala guide](guide/scala/index.md).
- For using Apache Fory™ with GraalVM native image, see [graalvm support](guide/java/graalvm-support.md) doc.

## Fory IDL Schema

Define cross-language data structures with Fory IDL and generate native code for multiple languages.

- [Fory IDL Overview](compiler/index.md) - Introduction and quick start
- [Fory Schema IDL](compiler/schema-idl.md) - Complete language syntax
- [Type System](compiler/schema-idl.md#type-system) - Primitive types, collections, and mappings
- [Compiler Guide](compiler/compiler-guide.md) - CLI usage and build integration
- [Generated Code](compiler/generated-code.md) - Output format for each language
- [Protocol Buffers vs Fory IDL](compiler/protobuf-idl.md) - Feature comparison and porting

## Serialization Format

- For Cross Language Serialization Format, see [xlang serialization spec](specification/xlang_serialization_spec.md) doc.
- For Java Object Graph Format, see [java serialization spec](specification/java_serialization_spec.md) doc.
- For Row Format, see [row format spec](specification/row_format_spec.md) doc.

## Benchmarks

- Benchmark source code:
  - Java: [Java Benchmarks Source](../benchmarks/java)
  - Python: [Python Benchmarks Source](../benchmarks/python)
  - Rust: [Rust Benchmarks Source](../benchmarks/rust/)
- Benchmark result:
  - Java: [Java Benchmarks](benchmarks/java/)
  - Rust: [Rust Benchmarks](benchmarks/rust).

## Development

- For cpp debug, see [cpp debug](cpp_debug.md) doc.
- For development, see [CONTRIBUTING](../CONTRIBUTING.md) doc.
