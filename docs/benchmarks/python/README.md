# Python Benchmark Performance Report

_Generated on 2026-05-07 18:53:14_

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

| Datatype          | Operation   | fory (ns) | pickle (ns) | protobuf (ns) | Fastest  |
| ----------------- | ----------- | --------- | ----------- | ------------- | -------- |
| NumericStruct     | Serialize   | 386.3     | 903.8       | 581.4         | fory     |
| NumericStruct     | Deserialize | 445.1     | 1003.0      | 809.6         | fory     |
| Sample            | Serialize   | 4386.0    | 9962.0      | 3143.2        | protobuf |
| Sample            | Deserialize | 3683.4    | 6690.4      | 6446.7        | fory     |
| MediaContent      | Serialize   | 944.9     | 4294.2      | 3165.3        | fory     |
| MediaContent      | Deserialize | 1483.1    | 4151.0      | 4521.5        | fory     |
| NumericStructList | Serialize   | 860.5     | 2872.0      | 4232.2        | fory     |
| NumericStructList | Deserialize | 1587.3    | 3253.8      | 4280.9        | fory     |
| SampleList        | Serialize   | 20217.2   | 32796.7     | 18257.8       | protobuf |
| SampleList        | Deserialize | 17579.5   | 23958.4     | 35273.3       | fory     |
| MediaContentList  | Serialize   | 3069.7    | 11015.2     | 18220.7       | fory     |
| MediaContentList  | Deserialize | 6403.9    | 10215.3     | 21166.5       | fory     |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS  | pickle TPS | protobuf TPS | Fastest  |
| ----------------- | ----------- | --------- | ---------- | ------------ | -------- |
| NumericStruct     | Serialize   | 2,588,986 | 1,106,456  | 1,719,845    | fory     |
| NumericStruct     | Deserialize | 2,246,442 | 997,010    | 1,235,152    | fory     |
| Sample            | Serialize   | 227,997   | 100,382    | 318,144      | protobuf |
| Sample            | Deserialize | 271,487   | 149,467    | 155,119      | fory     |
| MediaContent      | Serialize   | 1,058,347 | 232,873    | 315,927      | fory     |
| MediaContent      | Deserialize | 674,274   | 240,906    | 221,165      | fory     |
| NumericStructList | Serialize   | 1,162,076 | 348,184    | 236,284      | fory     |
| NumericStructList | Deserialize | 629,999   | 307,334    | 233,594      | fory     |
| SampleList        | Serialize   | 49,463    | 30,491     | 54,771       | protobuf |
| SampleList        | Deserialize | 56,885    | 41,739     | 28,350       | fory     |
| MediaContentList  | Serialize   | 325,768   | 90,784     | 54,883       | fory     |
| MediaContentList  | Deserialize | 156,156   | 97,892     | 47,244       | fory     |

### Serialized Data Sizes (bytes)

| Datatype          | fory | pickle | protobuf |
| ----------------- | ---- | ------ | -------- |
| NumericStruct     | 57   | 126    | 61       |
| Sample            | 445  | 1177   | 375      |
| MediaContent      | 366  | 624    | 301      |
| NumericStructList | 154  | 420    | 315      |
| SampleList        | 1914 | 3547   | 1890     |
| MediaContentList  | 1614 | 1415   | 1520     |
