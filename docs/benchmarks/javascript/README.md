# JavaScript Benchmark Performance Report

_Generated on 2026-05-07 22:31:46_

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
| Benchmark Date             | 2026-05-07T14:23:17.497Z |
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

| Datatype          | Operation   | fory (ns) | protobuf (ns) | json (ns) | Fastest  |
| ----------------- | ----------- | --------- | ------------- | --------- | -------- |
| NumericStruct     | Serialize   | 73.7      | 327.1         | 86.0      | fory     |
| NumericStruct     | Deserialize | 94.5      | 58.6          | 255.4     | protobuf |
| Sample            | Serialize   | 272.1     | 1722.4        | 600.8     | fory     |
| Sample            | Deserialize | 605.4     | 837.5         | 1343.6    | fory     |
| MediaContent      | Serialize   | 445.7     | 1235.9        | 543.8     | fory     |
| MediaContent      | Deserialize | 679.3     | 656.6         | 1159.6    | protobuf |
| NumericStructList | Serialize   | 163.6     | 1594.7        | 462.8     | fory     |
| NumericStructList | Deserialize | 355.1     | 374.9         | 1064.0    | fory     |
| SampleList        | Serialize   | 1176.2    | 8626.2        | 2816.5    | fory     |
| SampleList        | Deserialize | 2843.3    | 3813.9        | 5929.6    | fory     |
| MediaContentList  | Serialize   | 2045.3    | 6149.6        | 2428.6    | fory     |
| MediaContentList  | Deserialize | 3139.5    | 3567.7        | 5936.9    | fory     |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | json TPS   | Fastest  |
| ----------------- | ----------- | ---------- | ------------ | ---------- | -------- |
| NumericStruct     | Serialize   | 13,571,526 | 3,057,294    | 11,633,709 | fory     |
| NumericStruct     | Deserialize | 10,580,990 | 17,077,053   | 3,914,862  | protobuf |
| Sample            | Serialize   | 3,675,367  | 580,583      | 1,664,330  | fory     |
| Sample            | Deserialize | 1,651,863  | 1,193,999    | 744,267    | fory     |
| MediaContent      | Serialize   | 2,243,627  | 809,114      | 1,838,857  | fory     |
| MediaContent      | Deserialize | 1,472,084  | 1,523,024    | 862,357    | protobuf |
| NumericStructList | Serialize   | 6,110,806  | 627,088      | 2,160,736  | fory     |
| NumericStructList | Deserialize | 2,816,456  | 2,667,095    | 939,814    | fory     |
| SampleList        | Serialize   | 850,225    | 115,926      | 355,050    | fory     |
| SampleList        | Deserialize | 351,702    | 262,198      | 168,646    | fory     |
| MediaContentList  | Serialize   | 488,929    | 162,612      | 411,755    | fory     |
| MediaContentList  | Deserialize | 318,520    | 280,292      | 168,438    | fory     |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 57   | 61       | 103  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 182  | 315      | 537  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
