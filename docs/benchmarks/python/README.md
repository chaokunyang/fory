# Python Benchmark Performance Report

_Generated on 2026-03-03 13:02:52_

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
| Struct | Serialize | 436.2 | 895.5 | 575.3 | 462.8 | fory |
| Struct | Deserialize | 536.8 | 953.0 | 761.7 | 417.8 | msgpack |
| Sample | Serialize | 863.0 | 1791.5 | 2514.6 | 1534.2 | fory |
| Sample | Deserialize | 1305.4 | 2320.1 | 3947.5 | 1606.9 | fory |
| MediaContent | Serialize | 1163.5 | 2937.2 | 2973.8 | 1221.2 | fory |
| MediaContent | Deserialize | 1798.4 | 2909.2 | 3481.2 | 1620.5 | msgpack |
| StructList | Serialize | 1125.4 | 2852.6 | 3578.3 | 1448.7 | fory |
| StructList | Deserialize | 1469.5 | 2667.7 | 3790.0 | 1791.1 | fory |
| SampleList | Serialize | 2992.8 | 5633.1 | 15303.2 | 6482.0 | fory |
| SampleList | Deserialize | 6015.3 | 8346.1 | 20112.7 | 8273.3 | fory |
| MediaContentList | Serialize | 3490.1 | 9829.4 | 17044.7 | 5056.6 | fory |
| MediaContentList | Deserialize | 6297.6 | 8481.2 | 17182.2 | 7649.3 | fory |

### Throughput Results (ops/sec)

| Datatype | Operation | fory TPS | pickle TPS | protobuf TPS | msgpack TPS | Fastest |
|----------|-----------|----------|------------|--------------|-------------|---------|
| Struct | Serialize | 2,292,488 | 1,116,754 | 1,738,244 | 2,160,781 | fory |
| Struct | Deserialize | 1,862,990 | 1,049,336 | 1,312,890 | 2,393,315 | msgpack |
| Sample | Serialize | 1,158,735 | 558,207 | 397,674 | 651,801 | fory |
| Sample | Deserialize | 766,038 | 431,015 | 253,323 | 622,315 | fory |
| MediaContent | Serialize | 859,477 | 340,462 | 336,268 | 818,835 | fory |
| MediaContent | Deserialize | 556,050 | 343,741 | 287,254 | 617,109 | msgpack |
| StructList | Serialize | 888,557 | 350,555 | 279,466 | 690,283 | fory |
| StructList | Deserialize | 680,506 | 374,859 | 263,850 | 558,323 | fory |
| SampleList | Serialize | 334,133 | 177,523 | 65,346 | 154,273 | fory |
| SampleList | Deserialize | 166,244 | 119,817 | 49,720 | 120,871 | fory |
| MediaContentList | Serialize | 286,529 | 101,735 | 58,669 | 197,760 | fory |
| MediaContentList | Deserialize | 158,790 | 117,908 | 58,200 | 130,731 | fory |

### Serialized Data Sizes (bytes)

| Datatype | fory | pickle | protobuf | msgpack |
|----------|------|--------|----------|---------|
| Struct | 72 | 126 | 61 | 55 |
| Sample | 517 | 793 | 375 | 634 |
| MediaContent | 470 | 586 | 301 | 480 |
| StructList | 205 | 420 | 315 | 289 |
| SampleList | 1810 | 2539 | 1890 | 3184 |
| MediaContentList | 1756 | 1377 | 1520 | 2421 |
