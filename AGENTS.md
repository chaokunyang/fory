# AGENT.md

This file provides guidance to AI coding agent when working with code in this repository.

While working on Fory, please remember:

- Always use English in code and comments.
- Only add meaningful comments when the code's behavior is difficult to understand.
- Only add meaningful tests when they actually verify internal behaviors; otherwise, don't create them unless requested.

## Build and Development Commands

### Java Development

- All maven commands must be executed within the `java` directory.
- All changes to `java` must pass the code style check and tests.
- Fory java needs JDK `17+` installed.

```bash
# Clean the build
mvn -T16 clean

# Build
mvn -T16 package

# Install
mvn -T16 install -DskipTests

# Code format check
mvn -T16 spotless:check

# Code format
mvn -T16 spotless:apply

# Code style check
mvn -T16 checkstyle:check

# Run tests
mvn -T16 test

# Run specific tests
mvn -T16 test -Dtest=org.apache.fory.TestClass#testMethod
```

### C++ Development

- All commands must be executed within the `cpp` directory.

```bash
# Prepare for build
pip install pyarrow==15.0.0

# Run tests
bazel test $(bazel query //...)
```

### Python Development

- All commands must be executed within the `python` directory.
- All changes to `python` must pass the code style check and tests.
- When running tests, you can use the `ENABLE_FORY_CYTHON_SERIALIZATION` environment variable to enable or disable cython serialization.
- When debugging protocol related issues, you should use `ENABLE_FORY_CYTHON_SERIALIZATION=0` first to verify the behavior.
- Fory python needs cpython `3.8+` installed although some modules such as `fory-core` use `java8`.

```bash
# clean build
rm -rf build dist .pytest_cache
bazel clean --expunge

# Code format
ruff format .
ruff check --fix .

# Install
pip install -v -e .

# Build native extension
bazel build //:cp_fory_so --config=x86_64 # For x86_64
bazel build //:cp_fory_so --copt=-fsigned-char # For arm64 and arch64

# Run tests without cython
ENABLE_FORY_CYTHON_SERIALIZATION=0 pytest -v -s .
# Run tests with cython
ENABLE_FORY_CYTHON_SERIALIZATION=1 pytest -v -s .
```

### Golang Development

- All commands must be executed within the `go/fory` directory.
- All changes to `go` must pass the format check and tests.

```bash
# Run Tests
go test -v
```

### Rust Development

- All cargo commands must be executed within the `rust` directory.
- All changes to `rust` must pass the clippy check and tests.

```bash
# Check code
cargo check

# Build
cargo build

# Run linter for all services.
cargo clippy --all-targets --all-features -- -D warnings

# Run tests (requires test features)
cargo test --features tests

# Format code
cargo fmt

# Check formatting
cargo fmt --check

# Build documentation
cargo doc --lib --no-deps --all-features

# Run benchmarks
cargo bench
```

### Documentation

- When you update any markdown documentation, please use the `prettier --write $file` to format.
- When update any important public API, please update the documentation under `docs/`
- **`docs/specification/**` are the specification for the Fory protocol\*\*, please read those documents detailly.

## Architecture Overview

Apache Fory is a blazingly-fast multi-language serialization framework that revolutionizes data exchange between systems and languages. By leveraging JIT compilation, code generation and zero-copy techniques, Fory delivers up to 170x faster performance compared to other serialization frameworkds while being extremely easy to use.

### Binary protocol

Fory uses binary protocols for efficient serialization and deserialization. Fory designed and implemented multiple binary protocols for different scenarios:

- **[xlang serialization format](docs/specification/xlang_serialization_spec.md)**:
  - Cross-language serialize any object automatically, no need for IDL definition, schema compilation and object to/from
    protocol conversion.
  - Support optional shared reference and circular reference, no duplicate data or recursion error.
  - Support object polymorphism.
- **[Row format format](docs/specification/row_format_spec.md)**: A cache-friendly binary random access format, supports skipping serialization and
  partial serialization, and can convert to column-format automatically.
- **[Java serialization format](docs/specification/java_serialization_spec.md)**: Highly-optimized and drop-in replacement for Java serialization.
- **Python serialization format**: Highly-optimized and drop-in replacement for Python pickle, which is an extention built upon **[xlang serialization format](docs/specification/xlang_serialization_spec.md)**.

**`docs/specification/**` are the specification for the Fory protocol\*\*, please read those documents detailly and think hard and make sure you understand them before making changes to code and documentation.

### Core Structure

Fory serialization for every language is implemented independently to minimize the object memory layout interoperability, object allocation, memory access cost, thus maxmammaximize the performance. There is no code reuse between languages except for `fory python`, which reused code from `fory c++`.

#### Java

- **fory-core**: Java library implementing the core object graph serialization
  - `java/fory-core/src/main/java/org/apache/fory/Fory.java`: main serialization entry point
  - `java/fory-core/src/main/java/org/apache/fory/resolver/TypeResolver.java`: type resolution and serializer dispatch
  - `java/fory-core/src/main/java/org/apache/fory/resolver/RefResolver.java`: class for resolving shared/circular references when ref tracking is enabled
  - `java/fory-core/src/main/java/org/apache/fory/serializer`: serializers for each supported type
  - `java/fory-core/src/main/java/org/apache/fory/codegen`: code generators, provide expression abtraction and compile expression tree to java code and byte code
  - `java/fory-core/src/main/java/org/apache/fory/builder`: build expression tree for serialization to generate serialization code
  - `java/fory-core/src/main/java/org/apache/fory/reflect`: reflection utilities
  - `java/fory-core/src/main/java/org/apache/fory/type`: java generics and type inference utilities
  - `java/fory-core/src/main/java/org/apache/fory/util`: utility classes

- **fory-format**: Java library implementing the core row format encoding and decoding
  - `java/fory-format/src/main/java/org/apache/fory/format/row`: row format data structures
  - `java/fory-format/src/main/java/org/apache/fory/format/encoder`: generate row format encoder and decoder to encode/decode objects to/from row format
  - `java/fory-format/src/main/java/org/apache/fory/format/type`: type inference for row format
  - `java/fory-format/src/main/java/org/apache/fory/format/vectorized`: interoperation with apache arrow columar format

- **`fory-extension`**: extension libraries for java, including:
  - Protobuf serializers for fory java native object graph protocol.
  - Meta compression based on zstd

- **fory-simd**: SIMD-accelerated serialization and deserialization based on java vector API
  - `java/fory-simd/src/main/java/org/apache/fory/util`: SIMD utilities
  - `java/fory-simd/src/main/java/org/apache/fory/serializer`: SIMD accelerated serializers

- **fory-test-core**: Core test utilities and data generators

- **fory-test-suite**: Complex test suite for issues reported by users and hard to reproduce using simple test cases

- **benchmark**: Benchmark suite based on jmh

#### C++

- `cpp/fory/row`: Row format data structures
- `cpp/fory/meta`: Compile-time reflection utilities for extractr struct fields information.
- `cpp/fory/encoder`: Row format encoder and decoder
- `cpp/fory/columnar`: Interoperation between fory row format and apache arrow columnar format
- `cpp/fory/util`: Common utilities
  - `cpp/fory/util/buffer.h`: Buffer for reading and writing data
  - `cpp/fory/util/bit_util.h`: utilities for bit manipulation
  - `cpp/fory/util/string_util.h`: String utilities
  - `cpp/fory/util/status.h`: Status code for error handling

#### Python

Fory python has two implementations for the protocol:

- **Python mode**: Pure python implementation based on `xlang serialization format`, used for debugging and testing only. This mode can be enabled by setting `ENABLE_FORY_CYTHON_SERIALIZATION=1` environment variable.
- **Cython mode**: Cython based implementation based on `xlang serialization format`, which is used by default and has better performance than pure python. This mode can be enabled by setting `ENABLE_FORY_CYTHON_SERIALIZATION=0` environment variable.
- **Python mode** and **Cython mode** reused some code from each other to reduce code duplication.

Code structure:

- `python/pyfory/_serialization.pyx`: Core serialization logic and entry point for cython mode based on `xlang serialization format`
- `python/pyfory/_fory.py`: Serialization entry point for pure python mode based on `xlang serialization format`
- `python/pyfory/_registry_.py`: Type registry, resolution and serializer dispatch for pure python mode, which is alse used by cython mode. Cython mode use a cache to reduce invocations to this module.
- `python/pyfory/serializer.py`: Serializers for non-internal types
- `python/pyfory/includes`: Cython headers for `c++` functions and classes.
- `python/pyfory/resolver.py`: resolving shared/circular references when ref tracking is enabled in pure python mode
- `python/pyfory/format`: Fory row format encoding and decoding, arrow columnar format interoperation
- `python/pyfory/_util.pyx`: Buffer for reading/writing data, string utilities. Used by `_serialization.pyx` and `python/pyfory/format` at the same time.

#### Go

Fory go provides reflection-based and codegen-based serialization and deserialization.

- `go/fory/fory.go`: serialization entry point
- `go/fory/resolver.go`: resolving shared/circular references when ref tracking is enabled
- `go/fory/type.go`: type system and type resolution, serializer dispatch
- `go/fory/slice.go`: serializers for `slice` type
- `go/fory/map.go`: serializers for `map` type
- `go/fory/set.go`: serializers for `set` type
- `go/fory/struct.go`: serializers for `struct` type
- `go/fory/string.go`: serializers for `string` type
- `go/fory/buffer.go`: Buffer for reading/writing data
- `go/fory/codegen`: code generators, provide code generator to be invoked by `go:generate` to generate serialization code to speed up the serialization.
- `go/fory/meta`: Meta string compression

#### Rust

Fory rust provides macro-based serialization and deserialization. Fory rust consists of:

- **fory**: Main library entry point
  - `rust/fory/src/lib.rs`: main library entry point to export API to users
- **fory-core**: Core library for serialization and deserialization
  - `rust/fory-core/src/fory.rs`: main serialization entry point
  - `rust/fory-core/src/resolver/type_resolver.rs`: type resolution and registation
  - `rust/fory-core/src/resolver/metastring_resolver.rs`: resolver for meta string
  - `rust/fory-core/src/resolver/context.rs`: context for reading/writing
  - `rust/fory-core/src/buffer.rs`: buffer for reading/writing data
  - `rust/fory-core/src/meta`: meta string compression, type meta encoding
  - `rust/fory-core/src/serializer`: serializers for each supported type
  - `rust/fory-core/src/row`: row format encoding and decoding
- **fory-derive**: Rust macro-based codegen for serialization and deserialization
  - `rust/fory-derive/src/object`: macro for serializing/deserializing structs
  - `rust/fory-derive/src/fory_row`: macro for encoding/decoding row format

## Key Development Tips

- Performance is always the most important goal, do not introduce code that reduces performance
- Public APIs should be well-documented and easy to understand
- Code should be well-documented and easy to understand

## Commit Message Format

Use conventional commits:

```
feat(java): add codegen support for xlang serialization
fix(rust): fix collection header when collection is empty
docs(python): add docs for xlang serialization
refactor(java): unify serialization exceptions heriarchy
```
