# Fory Rust Benchmark

This benchmark compares Rust serialization and deserialization throughput for Apache Fory and Protocol Buffers using the shared benchmark dataset defined in `benchmarks/proto/bench.proto`.

## Prerequisites

The benchmark build generates Rust code from the shared schema and requires a
`protoc` executable on `PATH`, or the `PROTOC` environment variable set to an
existing executable.

## Quick Start

Run the complete Rust benchmark pipeline:

```bash
cd benchmarks/rust
./run.sh
```

## Run Options

```bash
./run.sh --help

Options:
  --data <struct|sample|mediacontent|structlist|samplelist|mediacontentlist>
                               Filter benchmark by data type
  --serializer <fory|protobuf>
                               Filter benchmark by serializer
  --filter <regex>             Custom criterion filter
  --no-report                  Skip Python report generation
```

Examples:

```bash
# Run only NumericStruct benchmarks
./run.sh --data struct

# Run only Protobuf benchmarks
./run.sh --serializer protobuf

# Run only Sample and MediaContent benchmarks for Protobuf
./run.sh --data sample,mediacontent --serializer protobuf
```

## Schema Mismatch Mode

Set `FORY_BENCH_SCHEMA_MISMATCH=1` to run the Fory-only compatible-read
schema-mismatch mode. This mode is off by default. When enabled, run with
`--serializer fory`; protobuf and MessagePack benchmark modes fail with a
configuration error. Fory serialization uses the normal v1 benchmark structs,
and Fory deserialization uses v2 structs registered with the same Fory type IDs
where one int32 field is widened to int64.

## Benchmark Cases

| Benchmark case      | Description                                                            |
| ------------------- | ---------------------------------------------------------------------- |
| `NumericStruct`     | Numeric struct with 12 int32 fields                                    |
| `Sample`            | Mixed primitive and array payload matching the shared benchmark schema |
| `MediaContent`      | Media and image payload matching the Java/C++ benchmark data           |
| `NumericStructList` | List of shared `NumericStruct` payloads                                |
| `SampleList`        | List of shared `Sample` payloads                                       |
| `MediaContentList`  | List of shared `MediaContent` payloads                                 |

The separate `external_type_bench` Criterion target contains branch-local Rust
external-type serialization comparisons. Keeping it separate preserves the
ordinary `serialization_bench` binary and measurement shape. Each comparison has `self_serialize`,
`selected_serialize`, `self_deserialize`, and `selected_deserialize` lanes.
The self lane uses an equivalent self-provided Rust target. The selected lane
uses the external structural serializer, manual serializer, carrier serializer,
or registered external target named by the case. Setup verifies byte equality
before measuring the pair.

The matrix covers:

- direct roots, direct fields, skipped fields, and recursive list/map/tuple
  field selection;
- external structural serializers, manual leaf serializers, and exact
  whole-container manual serializers;
- Option, Box, Rc, Arc, Fory weak references, RefCell, Mutex, lists, sets,
  heaps, fixed arrays, maps, and tuples;
- map key-only, value-only, key-and-value, and nested carrier selection;
- tuple arities 1 and 22 plus representative nested tuple composition;
- `Vec<i32>`, `Vec<u8>`, and nested primitive Vec composition;
- native struct-style enums in compatible and non-compatible modes;
- Box, Rc, and Arc `dyn Any` plus arbitrary registered application traits.

Run one comparison case by its Criterion group name:

```bash
cargo bench --bench external_type_bench -- carrier_map_nested
cargo bench --bench external_type_bench -- external_command_compatible
cargo bench --bench external_type_bench -- dynamic_trait_arc
```

For regression gates, run baseline and current cases sequentially. Existing
ordinary cases compare directly with `apache/main`. APIs added for
external-type serialization compare branch-locally with their equivalent self
lane; do not add compatibility shims to the baseline checkout.

## Shared Proto Schema

The Rust benchmark uses the shared protobuf definition at `benchmarks/proto/bench.proto`, the same benchmark schema used by the C++ benchmark suite.

## Manual Commands

Run Criterion benchmarks:

```bash
cd benchmarks/rust
cargo bench --bench serialization_bench
cargo bench --bench external_type_bench
```

Print serialized sizes:

```bash
cd benchmarks/rust
cargo run --release --bin fory_profiler -- --print-all-serialized-sizes
```

Generate the markdown report manually:

```bash
cd benchmarks/rust
cargo bench --bench serialization_bench 2>&1 | tee results/cargo_bench.log
cargo bench --bench external_type_bench 2>&1 | tee -a results/cargo_bench.log
cargo run --release --bin fory_profiler -- --print-all-serialized-sizes | tee results/serialized_sizes.txt
python benchmark_report.py --log-file results/cargo_bench.log --size-file results/serialized_sizes.txt --output-dir results
```

When external-type comparison cases are present in the Criterion log, the
report adds a table with self-provided and selected timings and their
percentage delta. The existing cross-library plot and tables remain unchanged.

## Report Output

The report generator writes:

- `results/README.md`
- `results/throughput.png`
