# C# Benchmark Performance Report

_Generated on 2026-05-07 18:53:14_

## How to Generate This Report

```bash
cd benchmarks/csharp
dotnet run -c Release --project ./Fory.CSharpBenchmark.csproj -- --output build/benchmark_results.json
python3 benchmark_report.py --json-file build/benchmark_results.json --output-dir report
```

## Hardware & OS Info

| Key                                | Value                                                                                                                        |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| OS                                 | Darwin 24.6.0 Darwin Kernel Version 24.6.0: Wed Oct 15 21:12:15 PDT 2025; root:xnu-11417.140.69.703.14~1/RELEASE_ARM64_T6041 |
| OS Architecture                    | Arm64                                                                                                                        |
| Machine                            | Arm64                                                                                                                        |
| Runtime Version                    | 8.0.24                                                                                                                       |
| Benchmark Date (UTC)               | 2026-05-07T10:09:28.9522110Z                                                                                                 |
| Warmup Seconds                     | 1                                                                                                                            |
| Duration Seconds                   | 3                                                                                                                            |
| CPU Logical Cores (from benchmark) | 12                                                                                                                           |
| CPU Cores (Physical)               | 12                                                                                                                           |
| CPU Cores (Logical)                | 12                                                                                                                           |
| Total RAM (GB)                     | 48.0                                                                                                                         |

## Benchmark Coverage

| Key                 | Value                                                                  |
| ------------------- | ---------------------------------------------------------------------- |
| Cases in input JSON | 36 / 36                                                                |
| Serializers         | fory, msgpack, protobuf                                                |
| Datatypes           | struct, sample, mediacontent, structlist, samplelist, mediacontentlist |
| Operations          | serialize, deserialize                                                 |

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
| NumericStruct     | Serialize   | 45.4      | 127.0         | 82.1         | fory    |
| NumericStruct     | Deserialize | 54.5      | 212.0         | 106.9        | fory    |
| Sample            | Serialize   | 288.2     | 571.5         | 358.6        | fory    |
| Sample            | Deserialize | 174.7     | 1179.5        | 566.6        | fory    |
| MediaContent      | Serialize   | 324.4     | 445.6         | 362.7        | fory    |
| MediaContent      | Deserialize | 390.5     | 812.8         | 687.0        | fory    |
| NumericStructList | Serialize   | 143.3     | 487.8         | 290.7        | fory    |
| NumericStructList | Deserialize | 264.8     | 710.3         | 503.4        | fory    |
| SampleList        | Serialize   | 1287.1    | 2947.3        | 1695.0       | fory    |
| SampleList        | Deserialize | 826.4     | 5465.5        | 2784.3       | fory    |
| MediaContentList  | Serialize   | 1437.9    | 2237.2        | 1783.5       | fory    |
| MediaContentList  | Deserialize | 1777.1    | 3511.9        | 3445.5       | fory    |

### Throughput Results (ops/sec)

| Datatype          | Operation   | fory TPS   | protobuf TPS | msgpack TPS | Fastest |
| ----------------- | ----------- | ---------- | ------------ | ----------- | ------- |
| NumericStruct     | Serialize   | 22,039,301 | 7,875,520    | 12,183,282  | fory    |
| NumericStruct     | Deserialize | 18,340,131 | 4,717,359    | 9,350,694   | fory    |
| Sample            | Serialize   | 3,470,317  | 1,749,692    | 2,788,639   | fory    |
| Sample            | Deserialize | 5,722,958  | 847,796      | 1,764,890   | fory    |
| MediaContent      | Serialize   | 3,082,350  | 2,244,389    | 2,757,259   | fory    |
| MediaContent      | Deserialize | 2,560,667  | 1,230,267    | 1,455,501   | fory    |
| NumericStructList | Serialize   | 6,977,318  | 2,050,217    | 3,440,177   | fory    |
| NumericStructList | Deserialize | 3,776,106  | 1,407,826    | 1,986,624   | fory    |
| SampleList        | Serialize   | 776,911    | 339,296      | 589,988     | fory    |
| SampleList        | Deserialize | 1,210,004  | 182,967      | 359,153     | fory    |
| MediaContentList  | Serialize   | 695,466    | 446,982      | 560,700     | fory    |
| MediaContentList  | Deserialize | 562,725    | 284,748      | 290,237     | fory    |

### Serialized Data Sizes (bytes)

| Datatype          | fory | protobuf | msgpack |
| ----------------- | ---- | -------- | ------- |
| NumericStruct     | 57   | 61       | 55      |
| Sample            | 445  | 460      | 562     |
| MediaContent      | 362  | 307      | 479     |
| NumericStructList | 182  | 315      | 284     |
| SampleList        | 1978 | 2315     | 2819    |
| MediaContentList  | 1531 | 1550     | 2404    |
