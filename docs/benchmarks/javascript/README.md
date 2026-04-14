# JavaScript Benchmark Performance Report

_Generated on 2026-04-14 14:05:17_

## How to Generate This Report

```bash
cd benchmarks/javascript
./run.sh
```

## Hardware & OS Info

| Key                        | Value                    |
| -------------------------- | ------------------------ |
| OS                         | Darwin 24.6.0            |
| Machine                    | arm64                    |
| Processor                  | arm                      |
| CPU Cores (Physical)       | 12                       |
| CPU Cores (Logical)        | 12                       |
| Total RAM (GB)             | 48.0                     |
| Benchmark Date             | 2026-04-14T06:03:26.265Z |
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

### Struct

![Struct](struct.png)

### StructList

![StructList](structlist.png)

## Benchmark Results

### Timing Results (nanoseconds)

| Datatype         | Operation   | fory (ns) | protobuf (ns) | json (ns) | Fastest  |
| ---------------- | ----------- | --------- | ------------- | --------- | -------- |
| MediaContent     | Serialize   | 876.9     | 1415.2        | 560.8     | json     |
| MediaContent     | Deserialize | 1361.0    | 923.2         | 1191.3    | protobuf |
| MediaContentList | Serialize   | 4158.8    | 6570.6        | 2610.4    | json     |
| MediaContentList | Deserialize | 5274.6    | 4502.4        | 5914.4    | protobuf |
| Sample           | Serialize   | 646.1     | 2174.9        | 591.3     | json     |
| Sample           | Deserialize | 1053.5    | 1194.7        | 1317.1    | fory     |
| SampleList       | Serialize   | 2976.6    | 8553.4        | 2810.3    | json     |
| SampleList       | Deserialize | 3708.4    | 5799.9        | 5748.1    | fory     |
| Struct           | Serialize   | 121.6     | 436.6         | 83.0      | json     |
| Struct           | Deserialize | 223.8     | 110.6         | 254.3     | protobuf |
| StructList       | Serialize   | 323.6     | 1593.6        | 420.3     | fory     |
| StructList       | Deserialize | 481.0     | 638.3         | 1033.1    | fory     |

### Throughput Results (ops/sec)

| Datatype         | Operation   | fory TPS  | protobuf TPS | json TPS   | Fastest  |
| ---------------- | ----------- | --------- | ------------ | ---------- | -------- |
| MediaContent     | Serialize   | 1,140,402 | 706,626      | 1,783,311  | json     |
| MediaContent     | Deserialize | 734,733   | 1,083,190    | 839,405    | protobuf |
| MediaContentList | Serialize   | 240,455   | 152,193      | 383,077    | json     |
| MediaContentList | Deserialize | 189,587   | 222,105      | 169,080    | protobuf |
| Sample           | Serialize   | 1,547,651 | 459,792      | 1,691,264  | json     |
| Sample           | Deserialize | 949,243   | 837,018      | 759,217    | fory     |
| SampleList       | Serialize   | 335,951   | 116,913      | 355,831    | json     |
| SampleList       | Deserialize | 269,660   | 172,417      | 173,971    | fory     |
| Struct           | Serialize   | 8,223,001 | 2,290,315    | 12,047,596 | json     |
| Struct           | Deserialize | 4,468,033 | 9,045,242    | 3,931,739  | protobuf |
| StructList       | Serialize   | 3,090,101 | 627,514      | 2,379,216  | fory     |
| StructList       | Deserialize | 2,078,801 | 1,566,673    | 967,981    | fory     |

### Serialized Data Sizes (bytes)

| Datatype         | fory | protobuf | json |
| ---------------- | ---- | -------- | ---- |
| Struct           | 58   | 61       | 103  |
| Sample           | 446  | 377      | 724  |
| MediaContent     | 365  | 307      | 596  |
| StructList       | 184  | 315      | 537  |
| SampleList       | 1980 | 1900     | 3642 |
| MediaContentList | 1535 | 1550     | 3009 |
