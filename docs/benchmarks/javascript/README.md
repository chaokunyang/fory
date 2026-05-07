# JavaScript Benchmark Performance Report

_Generated on 2026-05-08 03:05:56_

## How to Generate This Report

```bash
cd benchmarks/javascript
./run.sh
```

## Benchmark Semantics

The timed serializer loops use serializer-native typed values. Fory receives the pre-normalized Fory value used by its schema, protobuf receives the prebuilt protobuf-shaped value, and JSON receives the benchmark JavaScript object. Protobuf timings do not include `toProto`, `fromProto`, `protobufjs.create`, or `toObject` conversion work.

## Hardware & OS Info

| Key                        | Value                    |
| -------------------------- | ------------------------ |
| OS                         | Darwin 24.6.0            |
| Machine                    | arm64                    |
| Processor                  | arm                      |
| CPU Cores (Physical)       | 12                       |
| CPU Cores (Logical)        | 12                       |
| Total RAM (GB)             | 48.0                     |
| Benchmark Date             | 2026-05-07T19:04:05.491Z |
| CPU Cores (from benchmark) | 12                       |
| Node.js                    | v25.8.1                  |
| V8                         | 14.1.146.11-node.21      |

## Benchmark Plots

All class-level plots below show throughput (ops/sec).

### Throughput

![Throughput](throughput.png)

### MediaContent

![MediaContent](mediacontent.png)

### MediaContentList

![MediaContentList](mediacontentlist.png)

### Sample

![Sample](sample.png)

### SampleList

![SampleList](samplelist.png)

### NumericStruct

![NumericStruct](struct.png)

### NumericStructList

![NumericStructList](structlist.png)

## Benchmark Results

### Timing Results (nanoseconds)

| Datatype          | Operation   | fory (ns) | protobuf (ns) | json (ns) | Fastest |
| ----------------- | ----------- | --------- | ------------- | --------- | ------- |
| NumericStruct     | Serialize   | 78.0      | 486.2         | 121.0     | fory    |
| NumericStruct     | Deserialize | 79.1      | 82.3          | 315.3     | fory    |
| Sample            | Serialize   | 274.9     | 1692.8        | 591.9     | fory    |
| Sample            | Deserialize | 542.4     | 804.7         | 1440.5    | fory    |
| MediaContent      | Serialize   | 487.2     | 1254.2        | 562.9     | fory    |
| MediaContent      | Deserialize | 660.1     | 665.8         | 1229.5    | fory    |
| NumericStructList | Serialize   | 192.7     | 2417.2        | 607.0     | fory    |
| NumericStructList | Deserialize | 394.1     | 547.0         | 1435.4    | fory    |
| SampleList        | Serialize   | 1176.2    | 8605.4        | 2891.0    | fory    |
| SampleList        | Deserialize | 2605.9    | 3894.1        | 5805.0    | fory    |
| MediaContentList  | Serialize   | 2186.8    | 6374.6        | 2486.6    | fory    |
| MediaContentList  | Deserialize | 2905.0    | 3324.4        | 5803.2    | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | json TPS  | Fastest |
| ----------------- | ----------- | ---------- | ------------ | --------- | ------- |
| NumericStruct     | Serialize   | 12,815,639 | 2,056,852    | 8,265,329 | fory    |
| NumericStruct     | Deserialize | 12,649,188 | 12,149,761   | 3,171,738 | fory    |
| Sample            | Serialize   | 3,637,528  | 590,738      | 1,689,451 | fory    |
| Sample            | Deserialize | 1,843,614  | 1,242,687    | 694,214   | fory    |
| MediaContent      | Serialize   | 2,052,699  | 797,333      | 1,776,667 | fory    |
| MediaContent      | Deserialize | 1,514,900  | 1,502,041    | 813,354   | fory    |
| NumericStructList | Serialize   | 5,188,593  | 413,700      | 1,647,331 | fory    |
| NumericStructList | Deserialize | 2,537,355  | 1,828,152    | 696,692   | fory    |
| SampleList        | Serialize   | 850,231    | 116,207      | 345,896   | fory    |
| SampleList        | Deserialize | 383,740    | 256,798      | 172,267   | fory    |
| MediaContentList  | Serialize   | 457,291    | 156,872      | 402,152   | fory    |
| MediaContentList  | Deserialize | 344,233    | 300,811      | 172,320   | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 78   | 93       | 159  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 255  | 475      | 817  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
