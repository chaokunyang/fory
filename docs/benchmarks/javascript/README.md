# JavaScript Benchmark Performance Report

_Generated on 2026-05-08 08:00:44_

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
| Benchmark Date             | 2026-05-07T23:58:53.337Z |
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
| NumericStruct     | Serialize   | 75.5      | 464.6         | 120.0     | fory    |
| NumericStruct     | Deserialize | 45.8      | 81.5          | 313.5     | fory    |
| Sample            | Serialize   | 265.4     | 1653.9        | 579.0     | fory    |
| Sample            | Deserialize | 518.1     | 790.2         | 1301.5    | fory    |
| MediaContent      | Serialize   | 441.9     | 1211.0        | 558.3     | fory    |
| MediaContent      | Deserialize | 541.7     | 651.1         | 1164.4    | fory    |
| NumericStructList | Serialize   | 188.6     | 2277.5        | 598.0     | fory    |
| NumericStructList | Deserialize | 135.2     | 531.1         | 1420.1    | fory    |
| SampleList        | Serialize   | 1121.3    | 8273.6        | 2701.4    | fory    |
| SampleList        | Deserialize | 2410.3    | 3890.5        | 5681.4    | fory    |
| MediaContentList  | Serialize   | 1987.6    | 6059.5        | 2343.3    | fory    |
| MediaContentList  | Deserialize | 2400.1    | 3186.7        | 5749.5    | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | json TPS  | Fastest |
| ----------------- | ----------- | ---------- | ------------ | --------- | ------- |
| NumericStruct     | Serialize   | 13,244,428 | 2,152,374    | 8,335,638 | fory    |
| NumericStruct     | Deserialize | 21,830,676 | 12,265,018   | 3,190,140 | fory    |
| Sample            | Serialize   | 3,767,258  | 604,626      | 1,727,154 | fory    |
| Sample            | Deserialize | 1,929,951  | 1,265,552    | 768,351   | fory    |
| MediaContent      | Serialize   | 2,262,969  | 825,783      | 1,791,143 | fory    |
| MediaContent      | Deserialize | 1,846,067  | 1,535,878    | 858,830   | fory    |
| NumericStructList | Serialize   | 5,303,077  | 439,081      | 1,672,225 | fory    |
| NumericStructList | Deserialize | 7,395,539  | 1,883,018    | 704,167   | fory    |
| SampleList        | Serialize   | 891,824    | 120,867      | 370,177   | fory    |
| SampleList        | Deserialize | 414,883    | 257,036      | 176,014   | fory    |
| MediaContentList  | Serialize   | 503,111    | 165,030      | 426,747   | fory    |
| MediaContentList  | Deserialize | 416,646    | 313,807      | 173,928   | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 78   | 93       | 159  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 255  | 475      | 817  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
