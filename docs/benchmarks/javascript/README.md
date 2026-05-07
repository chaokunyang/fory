# JavaScript Benchmark Performance Report

_Generated on 2026-05-07 18:34:06_

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
| Benchmark Date             | 2026-05-07T10:23:30.159Z |
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
| NumericStruct     | Serialize   | 115.1     | 439.0         | 84.3      | json    |
| NumericStruct     | Deserialize | 91.8      | 112.1         | 266.1     | fory    |
| Sample            | Serialize   | 660.2     | 2214.8        | 638.4     | json    |
| Sample            | Deserialize | 642.9     | 1207.6        | 1349.3    | fory    |
| MediaContent      | Serialize   | 840.8     | 1376.1        | 589.8     | json    |
| MediaContent      | Deserialize | 699.6     | 918.6         | 1493.4    | fory    |
| NumericStructList | Serialize   | 287.1     | 1663.6        | 421.2     | fory    |
| NumericStructList | Deserialize | 303.7     | 676.4         | 1038.6    | fory    |
| SampleList        | Serialize   | 2979.8    | 8434.0        | 2763.9    | json    |
| SampleList        | Deserialize | 2724.5    | 5644.4        | 5717.5    | fory    |
| MediaContentList  | Serialize   | 3628.4    | 6789.4        | 2423.3    | json    |
| MediaContentList  | Deserialize | 3157.4    | 4651.7        | 5799.1    | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | json TPS   | Fastest |
| ----------------- | ----------- | ---------- | ------------ | ---------- | ------- |
| NumericStruct     | Serialize   | 8,685,464  | 2,278,069    | 11,860,799 | json    |
| NumericStruct     | Deserialize | 10,887,335 | 8,923,795    | 3,758,132  | fory    |
| Sample            | Serialize   | 1,514,711  | 451,514      | 1,566,522  | json    |
| Sample            | Deserialize | 1,555,441  | 828,119      | 741,103    | fory    |
| MediaContent      | Serialize   | 1,189,301  | 726,696      | 1,695,393  | json    |
| MediaContent      | Deserialize | 1,429,446  | 1,088,670    | 669,630    | fory    |
| NumericStructList | Serialize   | 3,482,505  | 601,097      | 2,374,226  | fory    |
| NumericStructList | Deserialize | 3,292,752  | 1,478,475    | 962,799    | fory    |
| SampleList        | Serialize   | 335,592    | 118,568      | 361,813    | json    |
| SampleList        | Deserialize | 367,038    | 177,166      | 174,903    | fory    |
| MediaContentList  | Serialize   | 275,606    | 147,288      | 412,662    | json    |
| MediaContentList  | Deserialize | 316,712    | 214,976      | 172,439    | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | json |
| ----------------- | ---- | -------- | ---- |
| NumericStruct     | 57   | 61       | 103  |
| Sample            | 445  | 377      | 724  |
| MediaContent      | 388  | 307      | 596  |
| NumericStructList | 182  | 315      | 537  |
| SampleList        | 1978 | 1900     | 3642 |
| MediaContentList  | 1661 | 1550     | 3009 |
