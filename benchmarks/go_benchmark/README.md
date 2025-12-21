# Go Serialization Benchmark

This directory contains benchmarks comparing [Apache Fory](https://github.com/apache/fory) with Protocol Buffers and MessagePack for Go serialization.

## Serializers Compared

- **Fory**: Apache Fory Go implementation - high-performance cross-language serialization
- **Protocol Buffers**: Google's protobuf via `google.golang.org/protobuf`
- **MessagePack**: Efficient binary serialization via `github.com/vmihailenco/msgpack/v5`

## Data Types Benchmarked

| Data Type     | Description                                      |
| ------------- | ------------------------------------------------ |
| NumericStruct | Simple struct with 8 int32 fields                |
| Sample        | Complex struct with primitives and 7 array types |
| MediaContent  | Nested objects with strings, enums, and lists    |

The benchmark data matches the C++ benchmark for cross-language comparison.

## Quick Start

### Prerequisites

- Go 1.21+
- Protocol Buffers compiler (`protoc`)

Install protoc:

```bash
# macOS
brew install protobuf

# Ubuntu/Debian
apt-get install protobuf-compiler

# Or download from https://github.com/protocolbuffers/protobuf/releases
```

### Run Benchmarks

```bash
# Run all benchmarks
./run.sh

# Run specific data type
./run.sh --data struct
./run.sh --data sample
./run.sh --data mediacontent

# Run specific serializer
./run.sh --serializer fory
./run.sh --serializer protobuf
./run.sh --serializer msgpack

# Combine filters
./run.sh --data struct --serializer fory

# Customize benchmark parameters
./run.sh --count 10 --benchtime 2s

# Skip report generation
./run.sh --no-report
```

### Manual Run

```bash
# Generate protobuf code
mkdir -p proto
protoc --proto_path=../proto \
    --go_out=proto \
    --go_opt=paths=source_relative \
    ../proto/bench.proto

# Download dependencies
go mod tidy

# Run benchmarks
go test -bench=. -benchmem

# Run specific benchmark
go test -bench=BenchmarkFory_Struct -benchmem
```

## Results

Example results on Apple M1 Pro:

| Data Type    | Operation   | Fory (ops/s) | Protobuf (ops/s) | Msgpack (ops/s) | Fory vs PB | Fory vs MP |
| ------------ | ----------- | ------------ | ---------------- | --------------- | ---------- | ---------- |
| Struct       | Serialize   | 8.40M        | 4.11M            | 2.08M           | 2.04x      | 4.04x      |
| Struct       | Deserialize | 4.35M        | 4.74M            | 1.29M           | 0.92x      | 3.37x      |
| Sample       | Serialize   | 1.78M        | 1.33M            | 288K            | 1.34x      | 6.18x      |
| Sample       | Deserialize | 452K         | 1.00M            | 150K            | 0.45x      | 3.02x      |
| Mediacontent | Serialize   | 1.42M        | 952K             | 479K            | 1.49x      | 2.96x      |
| Mediacontent | Deserialize | 761K         | 736K             | 307K            | 1.03x      | 2.48x      |

_Note: Results vary by hardware. Run benchmarks on your own system for accurate comparisons._

## Benchmark Methodology

### Fair Comparison

The benchmarks follow the same methodology as the C++ benchmark:

1. **Conversion Cost Included**: For protobuf, the benchmark includes converting plain Go structs to/from protobuf messages, matching real-world usage patterns.

2. **Buffer Reuse**: Each serializer uses its optimal pattern for buffer management.

3. **Same Test Data**: Test data matches the C++ benchmark exactly for cross-language comparisons.

### What's Measured

- **Serialize**: Time to convert a Go struct to bytes
- **Deserialize**: Time to convert bytes back to a Go struct
- **Memory**: Allocations per operation (with `-benchmem`)

## Output Files

After running `./run.sh`:

- `benchmark_results.txt` - Human-readable benchmark output
- `benchmark_results.json` - JSON format for programmatic analysis
- `benchmark_report.md` - Generated markdown report
- `benchmark_*.png` - Performance comparison charts (requires matplotlib)

## Directory Structure

```
go_benchmark/
├── README.md              # This file
├── go.mod                 # Go module definition
├── models.go              # Data structures (NumericStruct, Sample, MediaContent)
├── proto_convert.go       # Protobuf conversion utilities
├── benchmark_test.go      # Benchmark tests
├── run.sh                 # Build and run script
├── benchmark_report.py    # Report generation script
└── proto/                 # Generated protobuf code (after running)
    └── bench.pb.go
```

## Contributing

When adding new benchmarks:

1. Add data structures to `models.go`
2. Add protobuf conversions to `proto_convert.go`
3. Add benchmark functions to `benchmark_test.go`
4. Update this README with new data types

## License

Licensed under the Apache License, Version 2.0.
