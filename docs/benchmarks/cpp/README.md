# C++ Benchmark Performance Report

_Generated on 2026-05-07 18:53:14_

## How to Generate This Report

```bash
cd benchmarks/cpp/build
./fory_benchmark --benchmark_format=json --benchmark_out=benchmark_results.json
cd ..
python benchmark_report.py --json-file build/benchmark_results.json --output-dir report
```

## Hardware & OS Info

| Key                        | Value                     |
| -------------------------- | ------------------------- |
| OS                         | Darwin 24.6.0             |
| Machine                    | arm64                     |
| Processor                  | arm                       |
| CPU Cores (Physical)       | 12                        |
| CPU Cores (Logical)        | 12                        |
| Total RAM (GB)             | 48.0                      |
| Benchmark Date             | 2026-05-07T18:05:45+08:00 |
| CPU Cores (from benchmark) | 12                        |

## Benchmark Plots

All class-level plots below show throughput (ops/sec).

### Throughput

![Throughput](throughput.png)

### NumericStruct

![NumericStruct](struct.png)

### Sample

![Sample](sample.png)

### MediaContent

![MediaContent](mediacontent.png)

### NumericStructList

![NumericStructList](structlist.png)

### SampleList

![SampleList](samplelist.png)

### MediaContentList

![MediaContentList](mediacontentlist.png)

## Benchmark Results

### Timing Results (nanoseconds)

| Datatype          | Operation   | fory (ns) | protobuf (ns) | msgpack (ns) | Fastest |
| ----------------- | ----------- | --------- | ------------- | ------------ | ------- |
| NumericStruct     | Serialize   | 25.7      | 33.5          | 55.6         | fory    |
| NumericStruct     | Deserialize | 22.4      | 24.2          | 766.9        | fory    |
| Sample            | Serialize   | 67.7      | 95.1          | 304.8        | fory    |
| Sample            | Deserialize | 334.4     | 659.1         | 2700.8       | fory    |
| MediaContent      | Serialize   | 118.7     | 896.9         | 292.1        | fory    |
| MediaContent      | Deserialize | 400.6     | 1229.1        | 2835.3       | fory    |
| NumericStructList | Serialize   | 78.7      | 428.6         | 291.9        | fory    |
| NumericStructList | Deserialize | 121.1     | 357.5         | 3562.5       | fory    |
| SampleList        | Serialize   | 285.5     | 4956.0        | 1518.7       | fory    |
| SampleList        | Deserialize | 1777.6    | 4941.3        | 13495.3      | fory    |
| MediaContentList  | Serialize   | 474.9     | 4822.4        | 1453.5       | fory    |
| MediaContentList  | Deserialize | 2021.5    | 6748.5        | 13689.0      | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | msgpack TPS | Fastest |
| ----------------- | ----------- | ---------- | ------------ | ----------- | ------- |
| NumericStruct     | Serialize   | 38,957,337 | 29,876,488   | 18,000,975  | fory    |
| NumericStruct     | Deserialize | 44,669,303 | 41,288,443   | 1,303,964   | fory    |
| Sample            | Serialize   | 14,767,238 | 10,514,278   | 3,281,289   | fory    |
| Sample            | Deserialize | 2,990,424  | 1,517,129    | 370,258     | fory    |
| MediaContent      | Serialize   | 8,424,986  | 1,114,891    | 3,423,100   | fory    |
| MediaContent      | Deserialize | 2,496,565  | 813,607      | 352,702     | fory    |
| NumericStructList | Serialize   | 12,710,487 | 2,333,175    | 3,425,907   | fory    |
| NumericStructList | Deserialize | 8,255,958  | 2,797,074    | 280,698     | fory    |
| SampleList        | Serialize   | 3,502,186  | 201,774      | 658,471     | fory    |
| SampleList        | Deserialize | 562,564    | 202,374      | 74,100      | fory    |
| MediaContentList  | Serialize   | 2,105,549  | 207,364      | 687,997     | fory    |
| MediaContentList  | Deserialize | 494,684    | 148,180      | 73,051      | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | msgpack |
| ----------------- | ---- | -------- | ------- |
| NumericStruct     | 57   | 61       | 55      |
| Sample            | 445  | 375      | 530     |
| MediaContent      | 362  | 301      | 480     |
| NumericStructList | 182  | 315      | 289     |
| SampleList        | 1978 | 1890     | 2664    |
| MediaContentList  | 1531 | 1520     | 2421    |
