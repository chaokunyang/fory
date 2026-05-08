# Fory Swift Benchmark

This benchmark compares serialization and deserialization throughput for Apache Fory, Protocol Buffers, and MessagePack in Swift.

## Hardware and Runtime Info

| Key                   | Value                         |
| --------------------- | ----------------------------- |
| Timestamp             | 2026-05-08T08:51:09Z          |
| OS                    | Version 15.7.2 (Build 24G325) |
| Host                  | macbook-pro.local             |
| CPU Cores (Logical)   | 12                            |
| Memory (GB)           | 48.00                         |
| Duration per case (s) | 3                             |

## Throughput Results

![Throughput](throughput.png)

| Datatype          | Operation   |   Fory TPS | Protobuf TPS | Msgpack TPS | Fastest      |
| ----------------- | ----------- | ---------: | -----------: | ----------: | ------------ |
| NumericStruct     | Serialize   |  9,028,377 |    6,105,101 |     100,362 | fory (1.48x) |
| NumericStruct     | Deserialize | 10,870,818 |    6,761,243 |      66,335 | fory (1.61x) |
| Sample            | Serialize   |  3,669,350 |    1,210,002 |      16,593 | fory (3.03x) |
| Sample            | Deserialize |    926,032 |      735,925 |      12,075 | fory (1.26x) |
| MediaContent      | Serialize   |  1,569,696 |      650,443 |      27,821 | fory (2.41x) |
| MediaContent      | Deserialize |    597,655 |      457,845 |      11,662 | fory (1.31x) |
| NumericStructList | Serialize   |  2,917,607 |      920,508 |      18,368 | fory (3.17x) |
| NumericStructList | Deserialize |  2,459,439 |      694,040 |       6,150 | fory (3.54x) |
| SampleList        | Serialize   |    771,032 |      196,725 |       3,301 | fory (3.92x) |
| SampleList        | Deserialize |    185,035 |      129,993 |       1,458 | fory (1.42x) |
| MediaContentList  | Serialize   |    344,079 |       98,899 |       5,347 | fory (3.48x) |
| MediaContentList  | Deserialize |    109,422 |       81,397 |       1,420 | fory (1.34x) |

## Serialized Size (bytes)

| Datatype          | Fory | Protobuf | Msgpack |
| ----------------- | ---: | -------: | ------: |
| NumericStruct     |   78 |       93 |     100 |
| Sample            |  445 |      375 |     737 |
| MediaContent      |  362 |      301 |     524 |
| NumericStructList |  255 |      475 |     513 |
| SampleList        | 1978 |     1890 |    3698 |
| MediaContentList  | 1531 |     1520 |    2639 |
