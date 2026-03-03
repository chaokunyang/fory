# Python Benchmark Performance Report

_Generated on 2026-03-03 12:49:52_

## How to Generate This Report

```bash
cd benchmarks/python
./run.sh
```

## Hardware & OS Info

| Key | Value |
|-----|-------|
| OS | Darwin 24.6.0 |
| Machine | arm64 |
| Processor | arm |
| Python | 3.10.8 |
| CPU Cores (Physical) | 12 |
| CPU Cores (Logical) | 12 |
| Total RAM (GB) | 48.0 |
| Python Implementation | CPython |
| Benchmark Platform | macOS-15.7.2-arm64-arm-64bit |

## Benchmark Configuration

| Key | Value |
|-----|-------|
| warmup | 3 |
| iterations | 15 |
| repeat | 5 |
| number | 1000 |
| list_size | 5 |

## Benchmark Plots

All plots show throughput (ops/sec); higher is better.

### Throughput

<p align="center">
<img src="throughput.png" width="90%" />
</p>

### Mediacontent

<p align="center">
<img src="mediacontent.png" width="90%" />
</p>

### Mediacontentlist

<p align="center">
<img src="mediacontentlist.png" width="90%" />
</p>

### Sample

<p align="center">
<img src="sample.png" width="90%" />
</p>

### Samplelist

<p align="center">
<img src="samplelist.png" width="90%" />
</p>

### Struct

<p align="center">
<img src="struct.png" width="90%" />
</p>

### Structlist

<p align="center">
<img src="structlist.png" width="90%" />
</p>

## Benchmark Results

### Timing Results (nanoseconds)

| Datatype | Operation | fory (ns) | pickle (ns) | protobuf (ns) | msgpack (ns) | Fastest |
|----------|-----------|-----------|-------------|---------------|--------------|---------|
| Struct | Serialize | 433.4 | 859.7 | 575.5 | 6634.4 | fory |
| Struct | Deserialize | 532.8 | 952.7 | 1115.9 | 28330.5 | fory |
| Sample | Serialize | 836.1 | 1724.1 | 2557.2 | 52027.3 | fory |
| Sample | Deserialize | 1630.5 | 2709.5 | 3957.1 | 118989.3 | fory |
| MediaContent | Serialize | 1156.0 | 2854.2 | 2884.0 | 40650.8 | fory |
| MediaContent | Deserialize | 1734.2 | 2887.0 | 3364.3 | 116956.4 | fory |
| StructList | Serialize | 1071.1 | 2688.6 | 3469.5 | 53316.9 | fory |
| StructList | Deserialize | 1622.1 | 2710.0 | 3699.4 | 156416.3 | fory |
| SampleList | Serialize | 2880.7 | 5559.7 | 15140.4 | 412005.3 | fory |
| SampleList | Deserialize | 5302.9 | 8296.2 | 20080.9 | 602338.4 | fory |
| MediaContentList | Serialize | 3388.2 | 9311.5 | 16349.9 | 277963.2 | fory |
| MediaContentList | Deserialize | 6193.9 | 8421.9 | 16842.6 | 590322.7 | fory |

### Throughput Results (ops/sec)

| Datatype | Operation | fory TPS | pickle TPS | protobuf TPS | msgpack TPS | Fastest |
|----------|-----------|----------|------------|--------------|-------------|---------|
| Struct | Serialize | 2,307,104 | 1,163,141 | 1,737,673 | 150,728 | fory |
| Struct | Deserialize | 1,876,865 | 1,049,604 | 896,156 | 35,298 | fory |
| Sample | Serialize | 1,195,980 | 580,007 | 391,046 | 19,221 | fory |
| Sample | Deserialize | 613,306 | 369,076 | 252,710 | 8,404 | fory |
| MediaContent | Serialize | 865,047 | 350,360 | 346,740 | 24,600 | fory |
| MediaContent | Deserialize | 576,622 | 346,380 | 297,237 | 8,550 | fory |
| StructList | Serialize | 933,577 | 371,947 | 288,226 | 18,756 | fory |
| StructList | Deserialize | 616,498 | 369,005 | 270,317 | 6,393 | fory |
| SampleList | Serialize | 347,133 | 179,864 | 66,048 | 2,427 | fory |
| SampleList | Deserialize | 188,576 | 120,537 | 49,799 | 1,660 | fory |
| MediaContentList | Serialize | 295,139 | 107,395 | 61,162 | 3,598 | fory |
| MediaContentList | Deserialize | 161,450 | 118,738 | 59,373 | 1,694 | fory |

### Serialized Data Sizes (bytes)

| Datatype | fory | pickle | protobuf | msgpack |
|----------|------|--------|----------|---------|
| Struct | 72 | 126 | 61 | 55 |
| Sample | 517 | 793 | 375 | 634 |
| MediaContent | 470 | 586 | 301 | 480 |
| StructList | 205 | 420 | 315 | 289 |
| SampleList | 1810 | 2539 | 1890 | 3184 |
| MediaContentList | 1756 | 1377 | 1520 | 2421 |
