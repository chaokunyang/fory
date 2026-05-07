# Fory Swift Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory, Protocol Buffers, and MessagePack in Swift.

## Hardware and Runtime Info

| Key                   | Value                         |
| --------------------- | ----------------------------- |
| Timestamp             | 2026-05-07T10:50:24Z          |
| OS                    | Version 15.7.2 (Build 24G325) |
| Host                  | macbook-pro.local             |
| CPU Cores (Logical)   | 12                            |
| Memory (GB)           | 48.00                         |
| Duration per case (s) | 3                             |

## Throughput Results

![Throughput](throughput.png)

| Datatype          | Operation   |   Fory TPS | Protobuf TPS | Msgpack TPS | Fastest      |
| ----------------- | ----------- | ---------: | -----------: | ----------: | ------------ |
| NumericStruct     | Serialize   |  9,541,057 |    6,781,103 |     141,072 | fory (1.41x) |
| NumericStruct     | Deserialize | 11,374,958 |    9,588,318 |      97,167 | fory (1.19x) |
| Sample            | Serialize   |  3,675,875 |    1,320,469 |      16,848 | fory (2.78x) |
| Sample            | Deserialize |  1,015,246 |      756,711 |      12,260 | fory (1.34x) |
| MediaContent      | Serialize   |  1,583,205 |      679,730 |      28,409 | fory (2.33x) |
| MediaContent      | Deserialize |    606,616 |      471,716 |      12,071 | fory (1.29x) |
| NumericStructList | Serialize   |  3,151,469 |    1,021,936 |      24,904 | fory (3.08x) |
| NumericStructList | Deserialize |  2,646,486 |      829,685 |       8,731 | fory (3.19x) |
| SampleList        | Serialize   |    778,581 |      204,687 |       3,314 | fory (3.80x) |
| SampleList        | Deserialize |    187,868 |      135,953 |       1,459 | fory (1.38x) |
| MediaContentList  | Serialize   |    347,965 |      101,570 |       5,435 | fory (3.43x) |
| MediaContentList  | Deserialize |    114,695 |       85,530 |       1,455 | fory (1.34x) |

## Serialized Size (bytes)

| Datatype          | Fory | Protobuf | Msgpack |
| ----------------- | ---: | -------: | ------: |
| MediaContent      |  362 |      301 |     524 |
| MediaContentList  | 1531 |     1520 |    2639 |
| Sample            |  445 |      375 |     737 |
| SampleList        | 1978 |     1890 |    3698 |
| NumericStruct     |   57 |       61 |      65 |
| NumericStructList |  182 |      315 |     338 |
