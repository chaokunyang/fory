# Fory C++ Benchmark

This benchmark compares serialization/deserialization performance between Apache Fory and Protocol Buffers in C++.

## Prerequisites

- CMake 3.16+
- C++17 compatible compiler (GCC 8+, Clang 7+, MSVC 2019+)
- Git (for fetching dependencies)

Note: Protobuf is fetched automatically via CMake FetchContent, so no manual installation is required.

## Building

```bash
cd benchmarks/cpp_benchmark
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
cmake --build . -j$(nproc)
```

## Running Benchmarks

```bash
./fory_benchmark
```

### Filter specific benchmarks

```bash
# Run only Struct benchmarks
./fory_benchmark --benchmark_filter="Struct"

# Run only Fory benchmarks
./fory_benchmark --benchmark_filter="Fory"

# Run only serialization benchmarks
./fory_benchmark --benchmark_filter="Serialize"
```

### Output formats

```bash
# JSON output
./fory_benchmark --benchmark_format=json --benchmark_out=results.json

# CSV output
./fory_benchmark --benchmark_format=csv --benchmark_out=results.csv
```

## Benchmark Cases

| Benchmark                        | Description                                                         |
| -------------------------------- | ------------------------------------------------------------------- |
| `BM_Fory_Struct_Serialize`       | Serialize a simple struct with 8 int32 fields using Fory            |
| `BM_Protobuf_Struct_Serialize`   | Serialize the same struct using Protobuf                            |
| `BM_Fory_Struct_Deserialize`     | Deserialize a simple struct using Fory                              |
| `BM_Protobuf_Struct_Deserialize` | Deserialize the same struct using Protobuf                          |
| `BM_Fory_Sample_Serialize`       | Serialize a complex object with various types and arrays using Fory |
| `BM_Protobuf_Sample_Serialize`   | Serialize the same object using Protobuf                            |
| `BM_Fory_Sample_Deserialize`     | Deserialize a complex object using Fory                             |
| `BM_Protobuf_Sample_Deserialize` | Deserialize the same object using Protobuf                          |

## Data Structures

### Struct (Simple)

A simple structure with 8 int32 fields, useful for measuring baseline serialization overhead.

### Sample (Complex)

A complex structure containing:

- Primitive types (int32, int64, float, double, bool)
- Multiple arrays (int, long, float, double, short, char, bool)
- String field

## Proto Definition

The benchmark uses `benchmarks/proto/bench.proto` which is shared with the Java benchmark for consistency.
