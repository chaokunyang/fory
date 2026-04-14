# JavaScript Benchmark Performance Report

_Generated on 2026-04-14 14:56:11_

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
| Benchmark Date             | 2026-04-14T06:50:56.546Z |
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
| Struct           | Serialize   | 113.4     | 434.5         | 88.4      | json     |
| Struct           | Deserialize | 220.6     | 113.7         | 261.4     | protobuf |
| Sample           | Serialize   | 692.7     | 2154.4        | 596.4     | json     |
| Sample           | Deserialize | 1139.3    | 1218.0        | 1392.9    | fory     |
| MediaContent     | Serialize   | 875.3     | 1340.1        | 563.8     | json     |
| MediaContent     | Deserialize | 1385.8    | 900.9         | 1188.0    | protobuf |
| StructList       | Serialize   | 359.1     | 1581.4        | 426.1     | fory     |
| StructList       | Deserialize | 488.9     | 639.2         | 1042.4    | fory     |
| SampleList       | Serialize   | 3340.3    | 8725.7        | 2834.2    | json     |
| SampleList       | Deserialize | 3816.0    | 5796.2        | 5848.5    | fory     |
| MediaContentList | Serialize   | 4142.2    | 6626.7        | 2442.4    | json     |
| MediaContentList | Deserialize | 5524.2    | 4557.2        | 6305.6    | protobuf |

### Throughput Results (ops/sec)

| Datatype         | Operation   | fory TPS  | protobuf TPS | json TPS   | Fastest  |
| ---------------- | ----------- | --------- | ------------ | ---------- | -------- |
| Struct           | Serialize   | 8,815,859 | 2,301,627    | 11,309,688 | json     |
| Struct           | Deserialize | 4,532,327 | 8,793,358    | 3,825,292  | protobuf |
| Sample           | Serialize   | 1,443,696 | 464,162      | 1,676,750  | json     |
| Sample           | Deserialize | 877,718   | 821,027      | 717,930    | fory     |
| MediaContent     | Serialize   | 1,142,472 | 746,194      | 1,773,606  | json     |
| MediaContent     | Deserialize | 721,615   | 1,109,944    | 841,732    | protobuf |
| StructList       | Serialize   | 2,785,094 | 632,345      | 2,346,856  | fory     |
| StructList       | Deserialize | 2,045,390 | 1,564,335    | 959,342    | fory     |
| SampleList       | Serialize   | 299,376   | 114,603      | 352,832    | json     |
| SampleList       | Deserialize | 262,051   | 172,528      | 170,985    | fory     |
| MediaContentList | Serialize   | 241,415   | 150,904      | 409,430    | json     |
| MediaContentList | Deserialize | 181,020   | 219,434      | 158,590    | protobuf |

### Serialized Data Sizes (bytes)

| Datatype         | fory | protobuf | json |
| ---------------- | ---- | -------- | ---- |
| Struct           | 58   | 61       | 103  |
| Sample           | 446  | 377      | 724  |
| MediaContent     | 365  | 307      | 596  |
| StructList       | 184  | 315      | 537  |
| SampleList       | 1980 | 1900     | 3642 |
| MediaContentList | 1535 | 1550     | 3009 |
