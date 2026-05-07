#!/usr/bin/env bash
#
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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_FILTER=""
SERIALIZER_FILTER=""
DURATION_SECONDS="3"
OUTPUT_DIR="${SCRIPT_DIR}/results"
SKIP_BUILD="false"
GENERATE_REPORT="true"

usage() {
  cat <<'EOF'
Usage: ./run.sh [OPTIONS]

Options:
  --data <struct|sample|mediacontent|structlist|samplelist|mediacontentlist>
                               Filter benchmark by data type
  --serializer <fory|protobuf|flatbuffer>
                               Filter benchmark by serializer
  --duration <seconds>         JMH warmup and measurement duration per iteration
  --output-dir <dir>           Directory for benchmark_results.json and throughput.png
  --skip-build                 Reuse an existing target/benchmarks.jar
  --no-report                  Do not generate throughput.png
  --help                       Show this help
EOF
}

lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

serializer_regex() {
  case "$(lower "$1")" in
    "")
      printf '%s' "Fory|Protobuf|Flatbuffer"
      ;;
    fory)
      printf '%s' "Fory"
      ;;
    protobuf)
      printf '%s' "Protobuf"
      ;;
    flatbuffer)
      printf '%s' "Flatbuffer"
      ;;
    *)
      echo "Unknown serializer: $1" >&2
      echo "Expected one of: fory, protobuf, flatbuffer" >&2
      exit 1
      ;;
  esac
}

data_regex() {
  case "$(lower "$1")" in
    "")
      printf '%s' "Struct|Sample|MediaContent|StructList|SampleList|MediaContentList"
      ;;
    struct)
      printf '%s' "Struct"
      ;;
    sample)
      printf '%s' "Sample"
      ;;
    mediacontent)
      printf '%s' "MediaContent"
      ;;
    structlist)
      printf '%s' "StructList"
      ;;
    samplelist)
      printf '%s' "SampleList"
      ;;
    mediacontentlist)
      printf '%s' "MediaContentList"
      ;;
    *)
      echo "Unknown data type: $1" >&2
      echo "Expected one of: struct, sample, mediacontent, structlist, samplelist, mediacontentlist" >&2
      exit 1
      ;;
  esac
}

jmh_time() {
  case "$1" in
    *[!0-9.]*)
      printf '%s' "$1"
      ;;
    *.*)
      python3 - "$1" <<'PY'
import sys

millis = max(1, round(float(sys.argv[1]) * 1000))
print(f"{millis}ms")
PY
      ;;
    *)
      printf '%ss' "$1"
      ;;
  esac
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --data)
      DATA_FILTER="${2:-}"
      shift 2
      ;;
    --serializer)
      SERIALIZER_FILTER="${2:-}"
      shift 2
      ;;
    --duration)
      DURATION_SECONDS="${2:-}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift
      ;;
    --no-report)
      GENERATE_REPORT="false"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

SERIALIZERS="$(serializer_regex "${SERIALIZER_FILTER}")"
DATA_TYPES="$(data_regex "${DATA_FILTER}")"
JMH_DURATION="$(jmh_time "${DURATION_SECONDS}")"
RESULT_JSON="${OUTPUT_DIR}/benchmark_results.json"
BENCHMARK_REGEX="org.apache.fory.benchmark.XlangBenchmark.BM_(${SERIALIZERS})_(${DATA_TYPES})_(Serialize|Deserialize)"

mkdir -p "${OUTPUT_DIR}"

cd "${SCRIPT_DIR}"

if [[ "${SKIP_BUILD}" != "true" ]]; then
  mvn -Pjmh -DskipTests package
fi

ENABLE_FORY_DEBUG_OUTPUT=0 \
  java -jar target/benchmarks.jar "${BENCHMARK_REGEX}" \
  -f 1 \
  -wi 3 \
  -i 3 \
  -t 1 \
  -w "${JMH_DURATION}" \
  -r "${JMH_DURATION}" \
  -bm thrpt \
  -tu s \
  -rf json \
  -rff "${RESULT_JSON}"

if [[ "${GENERATE_REPORT}" == "true" ]]; then
  python3 benchmark_report.py --json-file "${RESULT_JSON}" --output-dir "${OUTPUT_DIR}"
fi
