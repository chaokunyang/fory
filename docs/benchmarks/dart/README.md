# Fory Dart Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory and Protocol Buffers in Dart.

## Hardware and Runtime Info

| Key                   | Value                                                             |
| --------------------- | ----------------------------------------------------------------- |
| Timestamp             | 2026-04-23T10:50:07.751368Z                                       |
| OS                    | Version 15.7.2 (Build 24G325)                                     |
| Host                  | MacBook-Pro.local                                                 |
| CPU Cores (Logical)   | 12                                                                |
| Memory (GB)           | 48.00                                                             |
| Dart                  | 3.10.7 (stable) (Tue Dec 23 00:01:57 2025 -0800) on "macos_arm64" |
| Samples per case      | 5                                                                 |
| Warmup per case (s)   | 1.0                                                               |
| Duration per case (s) | 1.5                                                               |

## Throughput Results

![Throughput](throughput.png)

| Datatype         | Operation   |  Fory TPS | Protobuf TPS | Fastest      |
| ---------------- | ----------- | --------: | -----------: | ------------ |
| Struct           | Serialize   | 5,041,693 |    2,073,839 | fory (2.43x) |
| Struct           | Deserialize | 6,395,290 |    4,991,881 | fory (1.28x) |
| Sample           | Serialize   | 1,783,688 |      552,140 | fory (3.23x) |
| Sample           | Deserialize | 2,124,197 |      934,794 | fory (2.27x) |
| MediaContent     | Serialize   |   952,498 |      438,419 | fory (2.17x) |
| MediaContent     | Deserialize | 1,649,039 |      737,340 | fory (2.24x) |
| StructList       | Serialize   | 1,945,119 |      399,007 | fory (4.87x) |
| StructList       | Deserialize | 2,119,403 |      764,832 | fory (2.77x) |
| SampleList       | Serialize   |   475,413 |       52,512 | fory (9.05x) |
| SampleList       | Deserialize |   508,939 |      116,236 | fory (4.38x) |
| MediaContentList | Serialize   |   224,925 |       84,860 | fory (2.65x) |
| MediaContentList | Deserialize |   387,070 |      154,392 | fory (2.51x) |

## Serialized Size (bytes)

| Datatype         | Fory | Protobuf |
| ---------------- | ---: | -------: |
| Struct           |   58 |       61 |
| Sample           |  446 |      377 |
| MediaContent     |  365 |      307 |
| StructList       |  184 |      315 |
| SampleList       | 1980 |     1900 |
| MediaContentList | 1535 |     1550 |

## Per-workload Plots

### Struct

![Struct](struct.png)

### Sample

![Sample](sample.png)

### MediaContent

![MediaContent](mediacontent.png)

### StructList

![StructList](structlist.png)

### SampleList

![SampleList](samplelist.png)

### MediaContentList

![MediaContentList](mediacontentlist.png)
