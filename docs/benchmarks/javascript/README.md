# JavaScript Benchmark Performance Report

_Generated on 2026-05-07 22:58:28_

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
| Benchmark Date             | 2026-05-07T14:56:37.733Z |
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
| NumericStruct     | Serialize   | 70.0      | 306.3         | 81.8      | fory     |
| NumericStruct     | Deserialize | 73.0      | 55.9          | 248.2     | protobuf |
| Sample            | Serialize   | 265.8     | 1647.4        | 566.0     | fory     |
| Sample            | Deserialize | 566.9     | 795.5         | 1355.8    | fory     |
| MediaContent      | Serialize   | 471.2     | 1245.3        | 584.5     | fory     |
| MediaContent      | Deserialize | 717.6     | 665.4         | 1223.0    | protobuf |
| NumericStructList | Serialize   | 170.7     | 1619.3        | 420.3     | fory     |
| NumericStructList | Deserialize | 330.0     | 369.6         | 1063.2    | fory     |
| SampleList        | Serialize   | 1168.5    | 8752.9        | 2794.7    | fory     |
| SampleList        | Deserialize | 2828.1    | 3773.0        | 5798.7    | fory     |
| MediaContentList  | Serialize   | 2089.5    | 6434.2        | 2398.6    | fory     |
| MediaContentList  | Deserialize | 3142.3    | 3320.1        | 5858.2    | fory     |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | json TPS   | Fastest  |
| ----------------- | ----------- | ---------- | ------------ | ---------- | -------- |
| NumericStruct     | Serialize   | 14,286,846 | 3,265,189    | 12,228,194 | fory     |
| NumericStruct     | Deserialize | 13,702,322 | 17,902,789   | 4,029,403  | protobuf |
| Sample            | Serialize   | 3,762,780  | 607,023      | 1,766,877  | fory     |
| Sample            | Deserialize | 1,763,993  | 1,257,096    | 737,581    | fory     |
| MediaContent      | Serialize   | 2,122,255  | 803,037      | 1,711,007  | fory     |
| MediaContent      | Deserialize | 1,393,616  | 1,502,911    | 817,681    | protobuf |
| NumericStructList | Serialize   | 5,857,723  | 617,560      | 2,379,095  | fory     |
| NumericStructList | Deserialize | 3,030,568  | 2,705,374    | 940,531    | fory     |
| SampleList        | Serialize   | 855,789    | 114,248      | 357,818    | fory     |
| SampleList        | Deserialize | 353,597    | 265,041      | 172,451    | fory     |
| MediaContentList  | Serialize   | 478,585    | 155,420      | 416,910    | fory     |
| MediaContentList  | Deserialize | 318,239    | 301,197      | 170,701    | fory     |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 57   | 61       | 103  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 182  | 315      | 537  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
