# JavaScript Benchmark Performance Report

_Generated on 2026-06-20 18:31:24_

## How to Generate This Report

```bash
cd benchmarks/javascript
./run.sh
```

## Benchmark Semantics

The timed serializer loops use serializer-native typed values. Fory receives the pre-normalized Fory value used by its schema, protobuf receives the prebuilt protobuf-shaped value, and JSON receives the benchmark JavaScript object. Protobuf timings do not include `toProto`, `fromProto`, `protobufjs.create`, or `toObject` conversion work.

## Benchmark Plot

The plot shows throughput (ops/sec); higher is better.

![Throughput](throughput.png)

## Hardware & OS Info

| Key                        | Value                    |
| -------------------------- | ------------------------ |
| OS                         | Darwin 24.6.0            |
| Machine                    | arm64                    |
| Processor                  | arm                      |
| CPU Cores (Physical)       | 12                       |
| CPU Cores (Logical)        | 12                       |
| Total RAM (GB)             | 48.0                     |
| Benchmark Date             | 2026-06-20T10:31:03.709Z |
| CPU Cores (from benchmark) | 12                       |
| Node.js                    | v25.8.1                  |
| V8                         | 14.1.146.11-node.21      |

## Benchmark Results

### Timing Results (nanoseconds)

| Datatype     | Operation   | fory (ns) | protobuf (ns) | json (ns) | Fastest |
| ------------ | ----------- | --------- | ------------- | --------- | ------- |
| MediaContent | Serialize   | 473.0     | N/A           | N/A       | fory    |
| MediaContent | Deserialize | 542.9     | N/A           | N/A       | fory    |

### Throughput Results (ops/sec)

| Datatype     | Operation   | fory TPS  | protobuf TPS | json TPS | Fastest |
| ------------ | ----------- | --------- | ------------ | -------- | ------- |
| MediaContent | Serialize   | 2,114,356 | N/A          | N/A      | fory    |
| MediaContent | Deserialize | 1,842,026 | N/A          | N/A      | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 78   | 93       | 159  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 255  | 475      | 817  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
