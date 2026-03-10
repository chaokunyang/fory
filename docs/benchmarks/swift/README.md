# Fory Swift Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory, Protocol Buffers, and MessagePack in Swift.

## Hardware and Runtime Info

| Key                   | Value                         |
| --------------------- | ----------------------------- |
| Timestamp             | 2026-03-09T14:33:22Z          |
| OS                    | Version 15.7.2 (Build 24G325) |
| Host                  | macbook-pro.local             |
| CPU Cores (Logical)   | 12                            |
| Memory (GB)           | 48.00                         |
| Duration per case (s) | 3                             |

## Throughput Results

<p align="center">
<img src="throughput.png" width="95%" />
</p>

| Datatype         | Operation   |   Fory TPS | Protobuf TPS | Msgpack TPS | Fastest      |
| ---------------- | ----------- | ---------: | -----------: | ----------: | ------------ |
| Struct           | Serialize   |  9,431,923 |    6,820,649 |     144,341 | fory (1.38x) |
| Struct           | Deserialize | 10,932,004 |    9,800,284 |      98,553 | fory (1.12x) |
| Sample           | Serialize   |  3,465,820 |    1,296,513 |      17,166 | fory (2.67x) |
| Sample           | Deserialize |  1,021,655 |      779,113 |      12,737 | fory (1.31x) |
| MediaContent     | Serialize   |  1,558,600 |      530,492 |      26,006 | fory (2.94x) |
| MediaContent     | Deserialize |    540,511 |      195,579 |      12,581 | fory (2.76x) |
| StructList       | Serialize   |  2,984,505 |    1,035,284 |      25,607 | fory (2.88x) |
| StructList       | Deserialize |  1,983,886 |      846,597 |       8,915 | fory (2.34x) |
| SampleList       | Serialize   |    700,391 |      198,065 |       3,384 | fory (3.54x) |
| SampleList       | Deserialize |    193,487 |      136,761 |       1,452 | fory (1.41x) |
| MediaContentList | Serialize   |    342,939 |      100,652 |       5,461 | fory (3.41x) |
| MediaContentList | Deserialize |    105,262 |       85,138 |       1,505 | fory (1.24x) |

## Serialized Size (bytes)

| Datatype         | Fory | Protobuf | Msgpack |
| ---------------- | ---: | -------: | ------: |
| MediaContent     |  365 |      301 |     524 |
| MediaContentList | 1535 |     1520 |    2639 |
| Sample           |  446 |      375 |     737 |
| SampleList       | 1980 |     1890 |    3698 |
| Struct           |   58 |       61 |      65 |
| StructList       |  184 |      315 |     338 |
