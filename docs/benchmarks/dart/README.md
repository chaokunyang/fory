# Fory Dart Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory and Protocol Buffers in Dart.

## Hardware and Runtime Info

| Key                   | Value                                                             |
| --------------------- | ----------------------------------------------------------------- |
| Timestamp             | 2026-05-07T09:12:52.301869Z                                       |
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

| Datatype         | Operation   |   Fory TPS | Protobuf TPS | Fastest       |
| ---------------- | ----------- | ---------: | -----------: | ------------- |
| Struct           | Serialize   |  9,631,226 |    2,134,738 | fory (4.51x)  |
| Struct           | Deserialize | 10,449,715 |    4,968,658 | fory (2.10x)  |
| Sample           | Serialize   |  2,468,767 |      536,410 | fory (4.60x)  |
| Sample           | Deserialize |  2,393,903 |      901,767 | fory (2.65x)  |
| MediaContent     | Serialize   |  1,162,191 |      431,591 | fory (2.69x)  |
| MediaContent     | Deserialize |  2,005,785 |      767,396 | fory (2.61x)  |
| StructList       | Serialize   |  2,851,755 |      374,020 | fory (7.62x)  |
| StructList       | Deserialize |  3,768,194 |      740,750 | fory (5.09x)  |
| SampleList       | Serialize   |    568,405 |       48,603 | fory (11.69x) |
| SampleList       | Deserialize |    546,914 |      111,151 | fory (4.92x)  |
| MediaContentList | Serialize   |    270,092 |       83,028 | fory (3.25x)  |
| MediaContentList | Deserialize |    454,291 |      149,294 | fory (3.04x)  |

## Serialized Size (bytes)

| Datatype         | Fory | Protobuf |
| ---------------- | ---: | -------: |
| Struct           |   57 |       61 |
| Sample           |  445 |      377 |
| MediaContent     |  362 |      307 |
| StructList       |  182 |      315 |
| SampleList       | 1978 |     1900 |
| MediaContentList | 1531 |     1550 |

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
