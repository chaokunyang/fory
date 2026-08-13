# Scala JSON Benchmark Report

The benchmark compares fory-json-scala, jsoniter-scala, and Jackson Scala on the same immutable Scala MediaContent model and Eishay JSON document. The String group excludes UTF-8 conversion; every library in the UTF-8 group uses its direct byte-array API.

- Benchmark date: `2026-08-14`
- Source commit: `05670052978e76f1ae5b6df74049dc9bad790019`
- Platform: macOS-15.7.2-arm64-arm-64bit (arm64)
- JDK: `26.0.1`
- VM: `OpenJDK 64-Bit Server VM`
- JMH: `1.37`
- Warmup: 1 iterations × `500 ms`
- Measurement: 2 iterations × `500 ms`
- Forks: 1; threads: 1
- Aggregation: median of 3 alternating short runs; error bars show the maximum cross-run deviation
- Mode: throughput; higher is better

## String

![Scala JSON String benchmark throughput](string_throughput.png)

## UTF-8 Bytes

![Scala JSON UTF-8 bytes benchmark throughput](utf8_bytes_throughput.png)

## Results

| Representation | Operation   | fory-json-scala ops/sec | jsoniter-scala ops/sec | Jackson Scala ops/sec | Fastest         |
| -------------- | ----------- | ----------------------: | ---------------------: | --------------------: | --------------- |
| String         | Serialize   |               6,633,363 |              2,709,254 |             2,016,233 | fory-json-scala |
| String         | Deserialize |               2,716,257 |              2,171,056 |               932,881 | fory-json-scala |
| UTF-8 bytes    | Serialize   |               9,116,849 |              2,797,010 |             1,710,957 | fory-json-scala |
| UTF-8 bytes    | Deserialize |               2,354,314 |              2,225,435 |             1,053,413 | fory-json-scala |

## Fory performance advantage

| Representation | Operation   | vs jsoniter-scala | vs Jackson Scala |
| -------------- | ----------- | ----------------: | ---------------: |
| String         | Serialize   |            144.8% |           229.0% |
| String         | Deserialize |             25.1% |           191.2% |
| UTF-8 bytes    | Serialize   |            225.9% |           432.9% |
| UTF-8 bytes    | Deserialize |              5.8% |           123.5% |
