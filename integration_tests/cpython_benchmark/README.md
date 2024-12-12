# Fury CPython Benchmark

Microbenchmark for Fury serialization in cpython

## Benchmark

Step 1: Install Fury into Python

Step 2: Install the dependencies required for the benchmark script

```bash
pip install -r requirements.txt
```

Step 3: Execute the benchmark script

```bash
python fury_benchmark.py
```

### fury options

`--xlang` specify using cross-language mode, otherwise choose python mode

`--no-ref` specify ref tracking is true

`--disable-cython` disable cython serialization

### pyperf options

`--affinity CPU_LIST` specify CPU affinity for worker processes

`-o FILENAME, --output FILENAME` write results encoded to JSON into FILENAME

`--profile PROFILE` collect profile data using cProfile and output to the given file

`--help` to get more `pyperf` options
