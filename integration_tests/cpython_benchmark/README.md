# Apache Fory™ CPython Benchmark

Microbenchmark for Apache Fory™ serialization in cpython

## Benchmark

Step 1: Install Apache Fory™ into Python

Step 2: Install the dependencies required for the benchmark script

```bash
pip install -r requirements.txt
```

Step 3: Execute the benchmark script

```bash
python fory_benchmark.py
```

## Usage

### Basic Usage

```bash
# Run all benchmarks with default settings (ref tracking enabled)
python fory_benchmark.py

# Run all benchmarks without reference tracking
python fory_benchmark.py --no-ref

# Run specific benchmarks
python fory_benchmark.py --benchmarks dict,large_dict,complex

# Disable cython serialization (use pure Python mode)
python fory_benchmark.py --disable-cython
```

### Fory Options

#### `--benchmarks BENCHMARK_LIST`

Comma-separated list of benchmarks to run. Default: `all`

Available benchmarks:

- `dict` - Small dictionary serialization
- `large_dict` - Large dictionary (2^10 + 1 entries)
- `dict_group` - Group of dictionaries
- `tuple` - Small tuple serialization
- `large_tuple` - Large tuple (2^20 + 1 integers)
- `large_float_tuple` - Large tuple of floats
- `large_boolean_tuple` - Large tuple of booleans
- `list` - Nested lists
- `large_list` - Large list (2^20 + 1 integers)
- `complex` - Complex dataclass objects

Examples:

```bash
# Run only dictionary benchmarks
python fory_benchmark.py --benchmarks dict,large_dict,dict_group

# Run only large data benchmarks
python fory_benchmark.py --benchmarks large_dict,large_tuple,large_list

# Run only the complex object benchmark
python fory_benchmark.py --benchmarks complex
```

#### `--no-ref`

Disable reference tracking. By default, Fory tracks references to handle shared and circular references.

```bash
# Run without reference tracking
python fory_benchmark.py --no-ref
```

#### `--disable-cython`

Disable Cython serialization and use pure Python mode. Useful for debugging protocol issues.

```bash
# Use pure Python serialization
python fory_benchmark.py --disable-cython
```

### Pyperf Options

The benchmark script uses `pyperf` for accurate measurements. Common pyperf options:

#### `--affinity CPU_LIST`

Specify CPU affinity for worker processes to reduce variance.

```bash
python fory_benchmark.py --affinity 0,1
```

#### `-o FILENAME, --output FILENAME`

Write results encoded to JSON into the specified file.

```bash
python fory_benchmark.py -o results.json
```

#### `--profile PROFILE`

Collect profile data using cProfile and output to the given file.

```bash
python fory_benchmark.py --profile profile.prof
```

#### `--help`

Display all available pyperf options.

```bash
python fory_benchmark.py --help
```

## Examples

```bash
# Run all benchmarks and save results
python fory_benchmark.py -o results.json

# Run specific benchmarks without ref tracking
python fory_benchmark.py --benchmarks dict,tuple,complex --no-ref

# Profile complex object benchmark
python fory_benchmark.py --benchmarks complex --profile complex.prof

# Run with CPU affinity for consistent results
python fory_benchmark.py --affinity 0,1 -o stable_results.json

# Debug protocol with pure Python mode
python fory_benchmark.py --disable-cython --benchmarks dict
```
