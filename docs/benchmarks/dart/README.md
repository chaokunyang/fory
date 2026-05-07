# Fory Dart Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory, Protocol Buffers, and JSON in Dart.

## Hardware and Runtime Info

| Key                   | Value                                                             |
| --------------------- | ----------------------------------------------------------------- |
| Timestamp             | 2026-05-07T10:39:41.006553Z                                       |
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

| Datatype          | Operation   |   Fory TPS | Protobuf TPS |  JSON TPS | Fastest       |
| ----------------- | ----------- | ---------: | -----------: | --------: | ------------- |
| NumericStruct     | Serialize   |  9,980,488 |    2,259,988 | 1,207,837 | fory (4.42x)  |
| NumericStruct     | Deserialize | 10,553,728 |    4,877,759 | 1,925,134 | fory (2.16x)  |
| Sample            | Serialize   |  2,553,310 |      553,571 |   135,703 | fory (4.61x)  |
| Sample            | Deserialize |  2,476,268 |      916,265 |   240,179 | fory (2.70x)  |
| MediaContent      | Serialize   |  1,131,670 |      433,874 |   226,355 | fory (2.61x)  |
| MediaContent      | Deserialize |  1,979,716 |      770,425 |   253,405 | fory (2.57x)  |
| NumericStructList | Serialize   |  2,822,285 |      382,506 |   218,463 | fory (7.38x)  |
| NumericStructList | Deserialize |  3,769,510 |      753,492 |   362,597 | fory (5.00x)  |
| SampleList        | Serialize   |    558,603 |       50,513 |    25,508 | fory (11.06x) |
| SampleList        | Deserialize |    544,041 |      110,942 |    48,131 | fory (4.90x)  |
| MediaContentList  | Serialize   |    274,211 |       81,808 |    42,800 | fory (3.35x)  |
| MediaContentList  | Deserialize |    457,961 |      151,931 |    51,106 | fory (3.01x)  |

## Serialized Size (bytes)

| Datatype          | Fory | Protobuf | JSON |
| ----------------- | ---: | -------: | ---: |
| NumericStruct     |   57 |       61 |  103 |
| Sample            |  445 |      377 |  791 |
| MediaContent      |  362 |      307 |  619 |
| NumericStructList |  182 |      315 |  536 |
| SampleList        | 1978 |     1900 | 3976 |
| MediaContentList  | 1531 |     1550 | 3122 |

## Per-workload Plots

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
