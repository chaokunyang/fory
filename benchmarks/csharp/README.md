# Fory C# Benchmark

This benchmark compares Apache Fory C#, protobuf-net, and MessagePack-CSharp.

Serializer setup used in this benchmark:

- `fory`: `Fory.Builder().Compatible(true).Build()`
- `protobuf`: `protobuf-net` runtime serializer
- `msgpack`: `MessagePackSerializer.Typeless` with `TypelessContractlessStandardResolver`

## Prerequisites

- .NET SDK 8.0+
- Python 3.8+

## Quick Start

```bash
cd benchmarks/csharp
./run.sh
```

This runs all benchmark cases and generates:

- `build/benchmark_results.json`
- `report/README.md`
- `report/throughput.png`

## Run Options

```bash
./run.sh --help

Options:
  --data <struct|sample|mediacontent|structlist|samplelist|mediacontentlist>
  --serializer <fory|protobuf|msgpack>
  --duration <seconds>
  --warmup <seconds>
  --external-equivalence
  --allocation-iterations <count>
```

Examples:

```bash
# Run only struct benchmarks
./run.sh --data struct

# Run only Fory benchmarks
./run.sh --serializer fory

# Use longer runs for stable numbers
./run.sh --duration 10 --warmup 2
```

## External-Type Equivalence

The opt-in external-type mode compares generated serializers for ordinary
benchmark models with external structural serializers for equivalent models
from a Fory-free project:

```bash
./run.sh --external-equivalence --duration 10 --warmup 3
```

It covers direct class and struct roots, a direct holder field, list and map
fields, and list and map roots. Each ordinary/external pair uses the same Fory
configuration, numeric registration IDs, field schemas, values, and native
carrier serializers.

The runner builds the benchmark once. For each selected lane, it then launches
an ordinary worker in a fresh process immediately followed by an external
worker in another fresh process. Each worker constructs, registers, validates,
warms, times, and optionally measures allocations for only its selected
implementation. Keeping the implementations in separate processes prevents
shared reference-generic Dynamic PGO profiles from biasing the second
implementation while retaining normal .NET tiering.

Use `--data` to select one of these lanes:

- `class-root`
- `struct-root`
- `holder-field`
- `list-field`
- `list-root`
- `map-field`
- `map-root`

Add a fixed allocation pass with:

```bash
./run.sh --external-equivalence --allocation-iterations 100000
```

Registration, serializer resolution, payload creation, delegates, metadata
caches, and timed warmup for the selected implementation are completed before
allocation counters start. Allocation results use
`GC.GetAllocatedBytesForCurrentThread()` and report bytes per operation.

Results are written to
`build/external_equivalence_results.json`. Per-process results, including a
Base64 proof frame, are written under
`build/external_equivalence_sides/`. After every worker finishes, the merger
rejects missing or duplicate lanes, incompatible runtime metadata, serialized
size or byte differences, and allocation differences. This opt-in mode does
not invoke the default Python report generator or update the published 36-case
report.

The benchmark executable requires exactly one implementation for direct worker
use:

```bash
dotnet run -c Release --no-build \
  --project ./Fory.CSharpBenchmark.csproj -- \
  --external-equivalence \
  --external-implementation ordinary \
  --data class-root \
  --duration 10 \
  --warmup 3 \
  --output build/external_equivalence_sides/class-root-ordinary.json
```

Use `ordinary` or `external`. `run.sh` owns the paired process ordering and
normally supplies this selector.

For the performance gate, run at least nine immediately adjacent
ordinary/external pairs, discard only the first pair, and compare the retained
median for every serialize and deserialize lane. Each external median must be
within 1% of its ordinary equivalent.

## Schema Mismatch Mode

Set `FORY_BENCH_SCHEMA_MISMATCH=1` to run the Fory-only compatible-read
schema-mismatch mode. This mode is off by default. When enabled, run with
`--serializer fory`; protobuf-net and MessagePack benchmark modes fail with a
configuration error. Fory serialization uses the normal v1 benchmark models,
and Fory deserialization uses v2 models registered with the same Fory type IDs
where one int32 field is widened to int64.

## Benchmark Cases

- `struct`: 8-field integer object
- `sample`: mixed primitive fields and arrays
- `mediacontent`: nested object with list fields
- `structlist`: list of struct payloads
- `samplelist`: list of sample payloads
- `mediacontentlist`: list of media content payloads

Each case benchmarks:

- serialize throughput
- deserialize throughput

## Results

Latest run (`Darwin arm64`, `.NET 8.0.24`, `--duration 2 --warmup 0.5`):

| Serializer | Mean ops/sec across all cases |
| ---------- | ----------------------------: |
| fory       |                     2,032,057 |
| protobuf   |                     1,940,328 |
| msgpack    |                     1,901,489 |

Per-case winners vary by payload and operation. The full breakdown is generated at:

- `benchmarks/csharp/build/benchmark_results.json`
- `benchmarks/csharp/report/README.md`
- `benchmarks/csharp/report/throughput.png`
