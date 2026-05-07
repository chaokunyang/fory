# Python Benchmark Performance Report

_Generated on 2026-05-08 03:03:54_

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
| NumericStruct     | Serialize   | 415.8     | 773.2         | 959.6       | fory    |
| NumericStruct     | Deserialize | 488.6     | 1095.8        | 1231.1      | fory    |
| Sample            | Serialize   | 945.5     | 3025.3        | 9585.3      | fory    |
| Sample            | Deserialize | 2753.3    | 6540.7        | 6542.1      | fory    |
| MediaContent      | Serialize   | 966.7     | 3125.1        | 4233.2      | fory    |
| MediaContent      | Deserialize | 1511.2    | 4254.7        | 4288.4      | fory    |
| NumericStructList | Serialize   | 1032.0    | 4484.1        | 2975.1      | fory    |
| NumericStructList | Deserialize | 1548.8    | 5422.3        | 3882.8      | fory    |
| SampleList        | Serialize   | 3318.9    | 18078.6       | 33086.1     | fory    |
| SampleList        | Deserialize | 13430.2   | 35726.7       | 23281.2     | fory    |
| MediaContentList  | Serialize   | 2973.8    | 17492.4       | 10953.7     | fory    |
| MediaContentList  | Deserialize | 6199.2    | 21206.6       | 10353.3     | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS  | protobuf TPS | pickle TPS | Fastest |
| ----------------- | ----------- | --------- | ------------ | ---------- | ------- |
| NumericStruct     | Serialize   | 2,404,945 | 1,293,335    | 1,042,138  | fory    |
| NumericStruct     | Deserialize | 2,046,797 | 912,599      | 812,314    | fory    |
| Sample            | Serialize   | 1,057,671 | 330,542      | 104,326    | fory    |
| Sample            | Deserialize | 363,201   | 152,888      | 152,856    | fory    |
| MediaContent      | Serialize   | 1,034,403 | 319,986      | 236,228    | fory    |
| MediaContent      | Deserialize | 661,733   | 235,036      | 233,188    | fory    |
| NumericStructList | Serialize   | 968,982   | 223,012      | 336,118    | fory    |
| NumericStructList | Deserialize | 645,657   | 184,425      | 257,547    | fory    |
| SampleList        | Serialize   | 301,302   | 55,314       | 30,224     | fory    |
| SampleList        | Deserialize | 74,459    | 27,990       | 42,953     | fory    |
| MediaContentList  | Serialize   | 336,273   | 57,168       | 91,294     | fory    |
| MediaContentList  | Deserialize | 161,310   | 47,155       | 96,588     | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | pickle |
| ----------------- | ---- | -------- | ------ |
| NumericStruct     | 78   | 93       | 169    |
| Sample            | 445  | 375      | 1177   |
| MediaContent      | 366  | 301      | 624    |
| NumericStructList | 219  | 475      | 582    |
| SampleList        | 1914 | 1890     | 3547   |
| MediaContentList  | 1614 | 1520     | 1415   |
