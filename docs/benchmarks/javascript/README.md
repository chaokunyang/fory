# JavaScript Benchmark Performance Report

_Generated on 2026-04-14 15:32:27_

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
| Benchmark Date             | 2026-04-14T07:30:36.278Z |
| CPU Cores (from benchmark) | 12                       |
| Node.js                    | v22.20.0                 |
| V8                         | 12.4.254.21-node.33      |

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
| Struct           | Serialize   | 106.8     | 535.1         | 332.4     | fory     |
| Struct           | Deserialize | 124.8     | 130.5         | 266.8     | fory     |
| Sample           | Serialize   | 628.6     | 2394.6        | 1378.8    | fory     |
| Sample           | Deserialize | 679.7     | 1232.9        | 1418.1    | fory     |
| MediaContent     | Serialize   | 848.8     | 1416.7        | 786.4     | json     |
| MediaContent     | Deserialize | 1074.5    | 819.8         | 1118.3    | protobuf |
| StructList       | Serialize   | 317.1     | 2072.9        | 1140.1    | fory     |
| StructList       | Deserialize | 413.2     | 728.5         | 1080.2    | fory     |
| SampleList       | Serialize   | 2983.5    | 9541.6        | 6226.3    | fory     |
| SampleList       | Deserialize | 3128.0    | 6236.3        | 6321.1    | fory     |
| MediaContentList | Serialize   | 3828.5    | 7050.9        | 3688.2    | json     |
| MediaContentList | Deserialize | 4992.5    | 4360.3        | 5396.4    | protobuf |

### Throughput Results (ops/sec)

| Datatype         | Operation   | fory TPS  | protobuf TPS | json TPS  | Fastest  |
| ---------------- | ----------- | --------- | ------------ | --------- | -------- |
| Struct           | Serialize   | 9,366,643 | 1,868,870    | 3,008,589 | fory     |
| Struct           | Deserialize | 8,012,212 | 7,661,344    | 3,748,390 | fory     |
| Sample           | Serialize   | 1,590,714 | 417,612      | 725,249   | fory     |
| Sample           | Deserialize | 1,471,175 | 811,093      | 705,147   | fory     |
| MediaContent     | Serialize   | 1,178,111 | 705,890      | 1,271,668 | json     |
| MediaContent     | Deserialize | 930,701   | 1,219,807    | 894,205   | protobuf |
| StructList       | Serialize   | 3,153,309 | 482,413      | 877,109   | fory     |
| StructList       | Deserialize | 2,420,060 | 1,372,723    | 925,751   | fory     |
| SampleList       | Serialize   | 335,172   | 104,804      | 160,610   | fory     |
| SampleList       | Deserialize | 319,696   | 160,351      | 158,199   | fory     |
| MediaContentList | Serialize   | 261,198   | 141,826      | 271,133   | json     |
| MediaContentList | Deserialize | 200,301   | 229,342      | 185,308   | protobuf |

### Serialized Data Sizes (bytes)

| Datatype         | fory | protobuf | json |
| ---------------- | ---- | -------- | ---- |
| Struct           | 58   | 61       | 103  |
| Sample           | 446  | 377      | 724  |
| MediaContent     | 365  | 307      | 596  |
| StructList       | 184  | 315      | 537  |
| SampleList       | 1980 | 1900     | 3642 |
| MediaContentList | 1535 | 1550     | 3009 |
