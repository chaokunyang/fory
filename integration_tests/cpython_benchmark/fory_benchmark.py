# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""Apache Fory™ CPython Benchmark Suite

Microbenchmark for Apache Fory™ serialization performance in CPython.

Usage:
    python fory_benchmark.py [OPTIONS]

Fory Options:
    --benchmarks BENCHMARK_LIST
        Comma-separated list of benchmarks to run. Default: all
        Available: dict, large_dict, dict_group, tuple, large_tuple,
                   large_float_tuple, large_boolean_tuple, list, large_list, complex

    --no-ref
        Disable reference tracking (enabled by default)

    --disable-cython
        Use pure Python mode instead of Cython serialization

Common Pyperf Options:
    --affinity CPU_LIST       Specify CPU affinity for worker processes
    -o FILENAME              Write results to JSON file
    --profile PROFILE        Collect cProfile data
    --help                   Show all available options

Examples:
    # Run all benchmarks
    python fory_benchmark.py

    # Run specific benchmarks
    python fory_benchmark.py --benchmarks dict,large_dict,complex

    # Run without reference tracking
    python fory_benchmark.py --no-ref

    # Profile and save results
    python fory_benchmark.py --benchmarks complex --profile complex.prof -o results.json

    # Debug with pure Python mode
    python fory_benchmark.py --disable-cython --benchmarks dict
"""

import argparse
from dataclasses import dataclass
import datetime
import random
import sys
from typing import Any, Dict, List
import pyfory
import pyperf


# The benchmark case is rewritten from pyperformance bm_pickle
# https://github.com/python/pyperformance/blob/main/pyperformance/data-files/benchmarks/bm_pickle/run_benchmark.py
DICT = {
    "ads_flags": 0,
    "age": 18,
    "birthday": datetime.date(1980, 5, 7),
    "bulletin_count": 0,
    "comment_count": 0,
    "country": "BR",
    "encrypted_id": "G9urXXAJwjE",
    "favorite_count": 9,
    "first_name": "",
    "flags": 412317970704,
    "friend_count": 0,
    "gender": "m",
    "gender_for_display": "Male",
    "id": 302935349,
    "is_custom_profile_icon": 0,
    "last_name": "",
    "locale_preference": "pt_BR",
    "member": 0,
    "tags": ["a", "b", "c", "d", "e", "f", "g"],
    "profile_foo_id": 827119638,
    "secure_encrypted_id": "Z_xxx2dYx3t4YAdnmfgyKw",
    "session_number": 2,
    "signup_id": "201-19225-223",
    "status": "A",
    "theme": 1,
    "time_created": 1225237014,
    "time_updated": 1233134493,
    "unread_message_count": 0,
    "user_group": "0",
    "username": "collinwinter",
    "play_count": 9,
    "view_count": 7,
    "zip": "",
}
LARGE_DICT = {str(i): i for i in range(2**10 + 1)}

TUPLE = (
    [
        265867233,
        265868503,
        265252341,
        265243910,
        265879514,
        266219766,
        266021701,
        265843726,
        265592821,
        265246784,
        265853180,
        45526486,
        265463699,
        265848143,
        265863062,
        265392591,
        265877490,
        265823665,
        265828884,
        265753032,
    ],
    60,
)
LARGE_TUPLE = tuple(range(2**20 + 1))
LARGE_FLOAT_TUPLE = tuple([random.random() * 10000 for _ in range(2**20 + 1)])
LARGE_BOOLEAN_TUPLE = tuple([bool(random.random() > 0.5) for _ in range(2**20 + 1)])


LIST = [[list(range(10)), list(range(10))] for _ in range(10)]
LARGE_LIST = [i for i in range(2**20 + 1)]


def mutate_dict(orig_dict, random_source):
    new_dict = dict(orig_dict)
    for key, value in new_dict.items():
        rand_val = random_source.random() * sys.maxsize
        if isinstance(key, (int, bytes, str)):
            new_dict[key] = type(key)(rand_val)
    return new_dict


random_source = random.Random(5)
DICT_GROUP = [mutate_dict(DICT, random_source) for _ in range(3)]


@dataclass
class ComplexObject1:
    f1: Any = None
    f2: str = None
    f3: List[str] = None
    f4: Dict[pyfory.int8, pyfory.int32] = None
    f5: pyfory.int8 = None
    f6: pyfory.int16 = None
    f7: pyfory.int32 = None
    f8: pyfory.int64 = None
    f9: pyfory.float32 = None
    f10: pyfory.float64 = None
    f11: pyfory.int16_array = None
    f12: List[pyfory.int16] = None


@dataclass
class ComplexObject2:
    f1: Any
    f2: Dict[pyfory.int8, pyfory.int32]


COMPLEX_OBJECT = ComplexObject1(
    f1=ComplexObject2(f1=True, f2={-1: 2}),
    f2="abc",
    f3=["abc", "abc"],
    f4={1: 2},
    f5=2**7 - 1,
    f6=2**15 - 1,
    f7=2**31 - 1,
    f8=2**63 - 1,
    f9=1.0 / 2,
    f10=1 / 3.0,
    f11=[-1, 4],
)

# Global fory instances
fory_with_ref = pyfory.Fory(ref=True)
fory_without_ref = pyfory.Fory(ref=False)

# Register all custom types on both instances
for fory_instance in (fory_with_ref, fory_without_ref):
    fory_instance.register_type(ComplexObject1)
    fory_instance.register_type(ComplexObject2)


def fory_object(ref, obj):
    fory = fory_with_ref if ref else fory_without_ref
    binary = fory.serialize(obj)
    fory.deserialize(binary)


def fory_data_class(ref, obj):
    fory = fory_with_ref if ref else fory_without_ref
    binary = fory.serialize(obj)
    fory.deserialize(binary)


def benchmark_args():
    parser = argparse.ArgumentParser(description="Fory Benchmark")
    parser.add_argument("--no-ref", action="store_true", default=False)
    parser.add_argument("--disable-cython", action="store_true", default=False)
    parser.add_argument(
        "--benchmarks",
        type=str,
        default="all",
        help="Comma-separated list of benchmarks to run. Available: dict, large_dict, "
        "dict_group, tuple, large_tuple, large_float_tuple, large_boolean_tuple, "
        "list, large_list, complex. Default: all",
    )

    if "--help" in sys.argv:
        parser.print_help()
        return None
    args, unknown_args = parser.parse_known_args()
    sys.argv = sys.argv[:1] + unknown_args
    return args


def micro_benchmark():
    args = benchmark_args()
    runner = pyperf.Runner()
    runner.parse_args()
    ref = not args.no_ref

    # Define all available benchmarks
    benchmarks = {
        "dict": ("fory_dict", fory_object, ref, DICT),
        "large_dict": ("fory_large_dict", fory_object, ref, LARGE_DICT),
        "dict_group": ("fory_dict_group", fory_object, ref, DICT_GROUP),
        "tuple": ("fory_tuple", fory_object, ref, TUPLE),
        "large_tuple": ("fory_large_tuple", fory_object, ref, LARGE_TUPLE),
        "large_float_tuple": (
            "fory_large_float_tuple",
            fory_object,
            ref,
            LARGE_FLOAT_TUPLE,
        ),
        "large_boolean_tuple": (
            "fory_large_boolean_tuple",
            fory_object,
            ref,
            LARGE_BOOLEAN_TUPLE,
        ),
        "list": ("fory_list", fory_object, ref, LIST),
        "large_list": ("fory_large_list", fory_object, ref, LARGE_LIST),
        "complex": ("fory_complex", fory_data_class, ref, COMPLEX_OBJECT),
    }

    # Determine which benchmarks to run
    if args.benchmarks == "all":
        selected_benchmarks = benchmarks.keys()
    else:
        selected_benchmarks = [b.strip() for b in args.benchmarks.split(",")]
        # Validate benchmark names
        invalid = [b for b in selected_benchmarks if b not in benchmarks]
        if invalid:
            print(f"Error: Invalid benchmark names: {', '.join(invalid)}")
            print(f"Available benchmarks: {', '.join(benchmarks.keys())}")
            sys.exit(1)

    # Run selected benchmarks
    for benchmark_name in selected_benchmarks:
        runner.bench_func(*benchmarks[benchmark_name])


if __name__ == "__main__":
    micro_benchmark()
