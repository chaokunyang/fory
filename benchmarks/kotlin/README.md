# Kotlin JSON Benchmarks

This standalone Gradle/JMH project compares Fory JSON Kotlin, kotlinx.serialization, Moshi, and
Jackson Kotlin on one immutable Eishay `MediaContent` model. It is a repository benchmark project,
not a published Fory artifact.

The model uses only `val` properties and has no public zero-argument constructor. All four
libraries consume the same model and fixture; no library-specific data transfer object or measured
model conversion is used. The fixture SHA-256 is
`8faba2f57ab397f319aced5cf1e8411a76785557d4c7d1703ec9d540354310a1`.

## Compared operations

The suite contains exactly 16 methods: four libraries multiplied by String serialization, UTF-8
byte serialization, String deserialization, and UTF-8 byte deserialization.

Each library uses a retained declared-type or generated serializer API:

- Fory retains `jsonTypeRef<MediaContent>()`, disables asynchronous compilation for deterministic
  setup, enables null emission, and warms all four generated paths before measurement.
- kotlinx.serialization retains `MediaContent.serializer()` and uses its String and stream APIs.
- Moshi retains its KSP-generated adapter and uses its String and Okio buffer APIs.
- Jackson Kotlin retains one `ObjectReader` and one `ObjectWriter` and uses its direct String and
  byte APIs.

The final byte materialization required by a library remains inside its measured byte-serialization
method. Deserialization likewise includes any fresh in-memory stream or buffer required by that
library. No byte method routes through a prebuilt String.

## Correctness gates

Before timing, setup verifies that every library:

- decodes the exact fixture from String and UTF-8 bytes to the independent expected object;
- emits structurally equivalent JSON from String and byte APIs; and
- round-trips its own String and byte output.

The Gradle build also fails unless the Moshi KSP adapter is present for every object model. Fory
uses its normal HotSpot metadata path in this benchmark; Android retention rules do not participate
in the measured runtime.

## Build and run

Install the current `fory-json-kotlin` artifact in Maven local first. Use Gradle 9.3.0 and a JDK 17
or later toolchain. Install the pinned Python report dependencies before running the report tests
or producing charts:

```bash
python -m pip install -r requirements.txt
```

Build the correctness and JMH artifacts:

```bash
gradle --no-daemon test verifyGeneratedJsonArtifacts jmhJar writeBenchmarkClasspath
```

Run the paired process-isolated scheduler:

```bash
python run_json_benchmark.py --rounds 6 --output-dir reports/json
```

Each round launches 16 separate JVM processes and runs one exact method per process. For every
operation, Fory and one comparator are adjacent; the selected comparator and AB/BA direction rotate
across rounds. Only those adjacent AB/BA launches contribute to Fory/comparator ratios. The report
computes each ratio inside its round before calculating the median and median absolute deviation.
The round count must be a multiple of three; the six-round default gives every comparator one AB
and one BA adjacency for each operation.

Use `--prepare-only` for CI correctness and Moshi adapter-generation checks without performance
timing.
When comparing two Fory revisions at the same Maven coordinate, resolve each revision from a
separate Maven repository and build one JMH JAR from each isolated classpath. Supply the second JMH
JAR and generated classpath manifest with `--comparison-jmh-jar` and
`--comparison-classpath-file`, plus its commit with `--comparison-commit`. The runner rejects a
shared Fory artifact path and requires each JMH JAR's embedded build provenance to match the exact
runtime classpath manifest supplied with it. It also verifies that the immutable model, fixture,
benchmark methods, and JMH case list are identical, records both artifact and dependency-set hashes
plus the executed JMH JAR hash for every launch, and alternates current/comparison Fory launches in
adjacent AB/BA pairs. The generated report publishes the per-launch revision samples and the four
current/comparison operation ratios. If the comparison revision lacks this exact module, API, or
benchmark surface, do not report a revision ratio.

Excluded runs are retained. Use `--session-id` when predeclaring deterministic run IDs, then supply
a CSV with `run_id,reason` columns through `--exclusions`; the raw sample remains present with
`included=false` and the reason. A failed process is also retained and fails the overall run. A
completed raw CSV can instead be reviewed, marked with exclusions, and passed directly to
`benchmark_report.py` without deleting any launch.

See the [published Kotlin JSON benchmark report](../../docs/benchmarks/json/kotlin/README.md). The
published page explicitly remains pending until a complete measured run is available; the tooling
does not synthesize results.
