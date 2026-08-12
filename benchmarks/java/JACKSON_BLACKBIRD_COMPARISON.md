# Jackson and Jackson Blackbird Benchmark Comparison

This document records two sequential Java JSON benchmark runs on August 12, 2026. The first run
explicitly selected Jackson Blackbird, and the second run selected ordinary Jackson. Both runs used
the same benchmark JAR, workload, host, and JMH settings.

The ordinary configuration uses Jackson `2.22.1`. The Blackbird configuration uses Jackson
`3.2.1` with `jackson-module-blackbird` `3.2.1`. Because both the Jackson major version and the
module configuration differ, the measured delta must not be attributed to Blackbird alone.

## Environment and methodology

- Platform: Apple M4 Pro, 12 cores, 48 GB RAM, macOS 15.7.2 arm64
- JDK: `26.0.1`
- JMH: `1.37`
- Benchmark JAR SHA-256:
  `4ac05586cbcec971f52ce804a03c3cc00d46c0bc89668ba1c22470c2845d9c75`
- Workload: the same `MediaContent` JSON fixture for every implementation
- Warmup: 3 iterations × 2 seconds
- Measurement: 5 iterations × 2 seconds
- Forks: 1
- Threads: 1
- Mode: throughput in operations per second; higher is better

## First run: Jackson Blackbird

Command:

```bash
cd benchmarks/java
./run_json.sh --skip-build --jackson blackbird \
  --reports-dir ../../tasks/task-java-json-blackbird/published-blackbird
```

| Representation | Operation   | fory-json ops/sec | Jackson Blackbird ops/sec | Gson ops/sec | Fastest   |
| -------------- | ----------- | ----------------: | ------------------------: | -----------: | --------- |
| String         | Serialize   |         7,033,317 |                 2,144,424 |    1,018,621 | fory-json |
| String         | Deserialize |         3,176,133 |                 1,072,902 |      795,836 | fory-json |
| UTF-8 bytes    | Serialize   |         9,866,897 |                 1,971,515 |    1,001,983 | fory-json |
| UTF-8 bytes    | Deserialize |         3,123,599 |                 1,314,077 |      884,427 | fory-json |

Raw JMH result SHA-256:
`337cfc849e96da1727943d2993901d91f4f11bf71fe6510869a8b560658bd9bf`.

## Second run: ordinary Jackson

Command:

```bash
cd benchmarks/java
./run_json.sh --skip-build --jackson standard \
  --reports-dir ../../tasks/task-java-json-blackbird/published-standard
```

| Representation | Operation   | fory-json ops/sec | Jackson ops/sec | Gson ops/sec | Fastest   |
| -------------- | ----------- | ----------------: | --------------: | -----------: | --------- |
| String         | Serialize   |         7,042,926 |       1,956,691 |      999,161 | fory-json |
| String         | Deserialize |         2,884,851 |       1,045,016 |      865,033 | fory-json |
| UTF-8 bytes    | Serialize   |         9,838,225 |       1,703,997 |      966,242 | fory-json |
| UTF-8 bytes    | Deserialize |         3,066,469 |       1,212,879 |      891,051 | fory-json |

Raw JMH result SHA-256:
`cf8d12715b33df34ef663fc1bdbee6bcb07576933117ee18f31278137762c436`.

## Ordinary Jackson versus Jackson Blackbird

The improvement is calculated as `(Blackbird throughput / Jackson throughput - 1) × 100%`.

| Representation | Operation   | Jackson ops/sec | Jackson Blackbird ops/sec | Difference ops/sec | Blackbird improvement |
| -------------- | ----------- | --------------: | ------------------------: | -----------------: | --------------------: |
| String         | Serialize   |       1,956,691 |                 2,144,424 |           +187,733 |                +9.59% |
| String         | Deserialize |       1,045,016 |                 1,072,902 |            +27,887 |                +2.67% |
| UTF-8 bytes    | Serialize   |       1,703,997 |                 1,971,515 |           +267,519 |               +15.70% |
| UTF-8 bytes    | Deserialize |       1,212,879 |                 1,314,077 |           +101,198 |                +8.34% |

The geometric-mean improvement across the four throughput ratios is `8.98%`. The String
deserialization difference is small relative to the JMH-reported score-error ranges, so additional
paired runs would be needed to establish that individual `2.67%` delta with high confidence.
