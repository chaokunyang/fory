# Python Benchmark Performance Report

_Generated on 2026-05-08 15:32:09_

## How to Generate This Report

```bash
cd benchmarks/python
./run.sh
```

## Hardware & OS Info

| Key                   | Value                        |
| --------------------- | ---------------------------- |
| OS                    | Darwin 24.6.0                |
| Machine               | arm64                        |
| Processor             | arm                          |
| Python                | 3.10.8                       |
| CPU Cores (Physical)  | 12                           |
| CPU Cores (Logical)   | 12                           |
| Total RAM (GB)        | 48.0                         |
| Python Implementation | CPython                      |
| Benchmark Platform    | macOS-15.7.2-arm64-arm-64bit |

## Benchmark Configuration

| Key        | Value |
| ---------- | ----- |
| warmup     | 3     |
| iterations | 15    |
| repeat     | 5     |
| number     | 1000  |
| list_size  | 5     |

## Benchmark Plots

All plots show throughput (ops/sec); higher is better.

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

| Datatype          | Operation   | fory (ns) | protobuf (ns) | pickle (ns) | Fastest |
| ----------------- | ----------- | --------- | ------------- | ----------- | ------- |
| NumericStruct     | Serialize   | 441.2     | 746.9         | 990.2       | fory    |
| NumericStruct     | Deserialize | 514.6     | 1110.8        | 1230.8      | fory    |
| Sample            | Serialize   | 1566.2    | 3714.1        | 10015.0     | fory    |
| Sample            | Deserialize | 2596.3    | 6540.3        | 7260.8      | fory    |
| MediaContent      | Serialize   | 1006.9    | 3122.9        | 4243.1      | fory    |
| MediaContent      | Deserialize | 1550.1    | 4305.9        | 4143.9      | fory    |
| NumericStructList | Serialize   | 1074.7    | 4336.7        | 2944.4      | fory    |
| NumericStructList | Deserialize | 1604.1    | 5485.4        | 3979.9      | fory    |
| SampleList        | Serialize   | 3460.1    | 18305.4       | 32050.7     | fory    |
| SampleList        | Deserialize | 13109.6   | 34646.1       | 23058.1     | fory    |
| MediaContentList  | Serialize   | 3096.9    | 17360.3       | 11011.1     | fory    |
| MediaContentList  | Deserialize | 6332.0    | 21551.9       | 10236.8     | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS  | protobuf TPS | pickle TPS | Fastest |
| ----------------- | ----------- | --------- | ------------ | ---------- | ------- |
| NumericStruct     | Serialize   | 2,266,423 | 1,338,931    | 1,009,908  | fory    |
| NumericStruct     | Deserialize | 1,943,331 | 900,229      | 812,486    | fory    |
| Sample            | Serialize   | 638,474   | 269,247      | 99,851     | fory    |
| Sample            | Deserialize | 385,161   | 152,898      | 137,725    | fory    |
| MediaContent      | Serialize   | 993,157   | 320,220      | 235,674    | fory    |
| MediaContent      | Deserialize | 645,118   | 232,237      | 241,321    | fory    |
| NumericStructList | Serialize   | 930,467   | 230,589      | 339,626    | fory    |
| NumericStructList | Deserialize | 623,388   | 182,303      | 251,265    | fory    |
| SampleList        | Serialize   | 289,010   | 54,629       | 31,201     | fory    |
| SampleList        | Deserialize | 76,280    | 28,863       | 43,369     | fory    |
| MediaContentList  | Serialize   | 322,909   | 57,603       | 90,817     | fory    |
| MediaContentList  | Deserialize | 157,928   | 46,400       | 97,687     | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | pickle |
| ----------------- | ---- | -------- | ------ |
| NumericStruct     | 78   | 93       | 169    |
| Sample            | 445  | 375      | 1176   |
| MediaContent      | 366  | 301      | 624    |
| NumericStructList | 219  | 475      | 582    |
| SampleList        | 1914 | 1890     | 3546   |
| MediaContentList  | 1614 | 1520     | 1415   |
