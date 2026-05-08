# Python Benchmark Performance Report

_Generated on 2026-05-08 15:12:24_

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
| NumericStruct     | Serialize   | 444.0     | 778.0         | 969.4       | fory    |
| NumericStruct     | Deserialize | 493.5     | 1184.3        | 1251.6      | fory    |
| Sample            | Serialize   | 887.9     | 3000.9        | 9723.8      | fory    |
| Sample            | Deserialize | 2639.2    | 6481.6        | 6379.8      | fory    |
| MediaContent      | Serialize   | 962.1     | 3016.8        | 4194.9      | fory    |
| MediaContent      | Deserialize | 1465.4    | 4094.2        | 4252.0      | fory    |
| NumericStructList | Serialize   | 1066.1    | 4603.8        | 2924.1      | fory    |
| NumericStructList | Deserialize | 1605.7    | 5494.7        | 3889.5      | fory    |
| SampleList        | Serialize   | 3346.6    | 17854.4       | 32762.0     | fory    |
| SampleList        | Deserialize | 13163.9   | 33866.9       | 23014.8     | fory    |
| MediaContentList  | Serialize   | 3004.8    | 17152.6       | 10984.7     | fory    |
| MediaContentList  | Deserialize | 6177.8    | 20747.9       | 9816.4      | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS  | protobuf TPS | pickle TPS | Fastest |
| ----------------- | ----------- | --------- | ------------ | ---------- | ------- |
| NumericStruct     | Serialize   | 2,252,241 | 1,285,372    | 1,031,523  | fory    |
| NumericStruct     | Deserialize | 2,026,466 | 844,379      | 798,972    | fory    |
| Sample            | Serialize   | 1,126,263 | 333,228      | 102,840    | fory    |
| Sample            | Deserialize | 378,906   | 154,283      | 156,745    | fory    |
| MediaContent      | Serialize   | 1,039,438 | 331,475      | 238,386    | fory    |
| MediaContent      | Deserialize | 682,410   | 244,249      | 235,185    | fory    |
| NumericStructList | Serialize   | 938,014   | 217,212      | 341,981    | fory    |
| NumericStructList | Deserialize | 622,799   | 181,994      | 257,102    | fory    |
| SampleList        | Serialize   | 298,815   | 56,008       | 30,523     | fory    |
| SampleList        | Deserialize | 75,965    | 29,527       | 43,450     | fory    |
| MediaContentList  | Serialize   | 332,800   | 58,300       | 91,036     | fory    |
| MediaContentList  | Deserialize | 161,870   | 48,198       | 101,871    | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | pickle |
| ----------------- | ---- | -------- | ------ |
| NumericStruct     | 78   | 93       | 169    |
| Sample            | 445  | 375      | 1176   |
| MediaContent      | 366  | 301      | 624    |
| NumericStructList | 219  | 475      | 582    |
| SampleList        | 1914 | 1890     | 3546   |
| MediaContentList  | 1614 | 1520     | 1415   |
