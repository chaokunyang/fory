# Python Benchmark Performance Report

_Generated on 2026-05-07 19:06:37_

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

| Datatype          | Operation   | fory (ns) | protobuf (ns) | pickle (ns) | Fastest  |
| ----------------- | ----------- | --------- | ------------- | ----------- | -------- |
| NumericStruct     | Serialize   | 386.3     | 581.4         | 903.8       | fory     |
| NumericStruct     | Deserialize | 445.1     | 809.6         | 1003.0      | fory     |
| Sample            | Serialize   | 4386.0    | 3143.2        | 9962.0      | protobuf |
| Sample            | Deserialize | 3683.4    | 6446.7        | 6690.4      | fory     |
| MediaContent      | Serialize   | 944.9     | 3165.3        | 4294.2      | fory     |
| MediaContent      | Deserialize | 1483.1    | 4521.5        | 4151.0      | fory     |
| NumericStructList | Serialize   | 860.5     | 4232.2        | 2872.0      | fory     |
| NumericStructList | Deserialize | 1587.3    | 4280.9        | 3253.8      | fory     |
| SampleList        | Serialize   | 20217.2   | 18257.8       | 32796.7     | protobuf |
| SampleList        | Deserialize | 17579.5   | 35273.3       | 23958.4     | fory     |
| MediaContentList  | Serialize   | 3069.7    | 18220.7       | 11015.2     | fory     |
| MediaContentList  | Deserialize | 6403.9    | 21166.5       | 10215.3     | fory     |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS  | protobuf TPS | pickle TPS | Fastest  |
| ----------------- | ----------- | --------- | ------------ | ---------- | -------- |
| NumericStruct     | Serialize   | 2,588,986 | 1,719,845    | 1,106,456  | fory     |
| NumericStruct     | Deserialize | 2,246,442 | 1,235,152    | 997,010    | fory     |
| Sample            | Serialize   | 227,997   | 318,144      | 100,382    | protobuf |
| Sample            | Deserialize | 271,487   | 155,119      | 149,467    | fory     |
| MediaContent      | Serialize   | 1,058,347 | 315,927      | 232,873    | fory     |
| MediaContent      | Deserialize | 674,274   | 221,165      | 240,906    | fory     |
| NumericStructList | Serialize   | 1,162,076 | 236,284      | 348,184    | fory     |
| NumericStructList | Deserialize | 629,999   | 233,594      | 307,334    | fory     |
| SampleList        | Serialize   | 49,463    | 54,771       | 30,491     | protobuf |
| SampleList        | Deserialize | 56,885    | 28,350       | 41,739     | fory     |
| MediaContentList  | Serialize   | 325,768   | 54,883       | 90,784     | fory     |
| MediaContentList  | Deserialize | 156,156   | 47,244       | 97,892     | fory     |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | pickle |
| ----------------- | ---- | -------- | ------ |
| NumericStruct     | 57   | 61       | 126    |
| Sample            | 445  | 375      | 1177   |
| MediaContent      | 366  | 301      | 624    |
| NumericStructList | 154  | 315      | 420    |
| SampleList        | 1914 | 1890     | 3547   |
| MediaContentList  | 1614 | 1520     | 1415   |
