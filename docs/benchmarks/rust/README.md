# Rust Benchmark Performance Report

_Generated on 2026-05-07 18:47:40_

## How to Generate This Report

```bash
cd benchmarks/rust
cargo bench --bench serialization_bench 2>&1 | tee results/cargo_bench.log
cargo run --release --bin fory_profiler -- --print-all-serialized-sizes | tee results/serialized_sizes.txt
python benchmark_report.py --log-file results/cargo_bench.log --size-file results/serialized_sizes.txt --output-dir results
```

## Hardware & OS Info

| Key                  | Value               |
| -------------------- | ------------------- |
| OS                   | Darwin 24.6.0       |
| Machine              | arm64               |
| Processor            | arm                 |
| CPU Cores (Physical) | 12                  |
| CPU Cores (Logical)  | 12                  |
| Total RAM (GB)       | 48.0                |
| Benchmark Date       | 2026-05-07T18:47:37 |

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

| Datatype          | Operation   | fory (ns) | protobuf (ns) | msgpack (ns) | Fastest |
| ----------------- | ----------- | --------- | ------------- | ------------ | ------- |
| NumericStruct     | Serialize   | 34.6      | 68.2          | 168.1        | fory    |
| NumericStruct     | Deserialize | 23.8      | 41.5          | 68.6         | fory    |
| Sample            | Serialize   | 96.1      | 560.6         | 588.2        | fory    |
| Sample            | Deserialize | 349.7     | 890.4         | 783.5        | fory    |
| MediaContent      | Serialize   | 121.5     | 554.9         | 466.5        | fory    |
| MediaContent      | Deserialize | 544.8     | 702.1         | 896.5        | fory    |
| NumericStructList | Serialize   | 83.4      | 351.1         | 484.2        | fory    |
| NumericStructList | Deserialize | 96.9      | 296.6         | 406.0        | fory    |
| SampleList        | Serialize   | 270.1     | 2956.6        | 2042.2       | fory    |
| SampleList        | Deserialize | 1669.2    | 4340.0        | 4174.0       | fory    |
| MediaContentList  | Serialize   | 374.1     | 2836.7        | 1448.4       | fory    |
| MediaContentList  | Deserialize | 2786.7    | 3682.6        | 4799.6       | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | msgpack TPS | Fastest |
| ----------------- | ----------- | ---------- | ------------ | ----------- | ------- |
| NumericStruct     | Serialize   | 28,873,361 | 14,665,337   | 5,949,902   | fory    |
| NumericStruct     | Deserialize | 41,958,629 | 24,113,817   | 14,575,347  | fory    |
| Sample            | Serialize   | 10,407,343 | 1,783,899    | 1,700,073   | fory    |
| Sample            | Deserialize | 2,859,676  | 1,123,040    | 1,276,324   | fory    |
| MediaContent      | Serialize   | 8,232,485  | 1,802,191    | 2,143,807   | fory    |
| MediaContent      | Deserialize | 1,835,603  | 1,424,197    | 1,115,399   | fory    |
| NumericStructList | Serialize   | 11,984,947 | 2,848,516    | 2,065,262   | fory    |
| NumericStructList | Deserialize | 10,319,811 | 3,370,976    | 2,462,933   | fory    |
| SampleList        | Serialize   | 3,702,470  | 338,226      | 489,668     | fory    |
| SampleList        | Deserialize | 599,089    | 230,415      | 239,578     | fory    |
| MediaContentList  | Serialize   | 2,673,368  | 352,522      | 690,417     | fory    |
| MediaContentList  | Deserialize | 358,847    | 271,547      | 208,351     | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | msgpack |
| ----------------- | ---- | -------- | ------- |
| NumericStruct     | 57   | 61       | 55      |
| Sample            | 445  | 375      | 590     |
| MediaContent      | 362  | 301      | 500     |
| NumericStructList | 182  | 315      | 289     |
| SampleList        | 1978 | 1890     | 2964    |
| MediaContentList  | 1531 | 1520     | 2521    |
