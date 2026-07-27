#!/bin/bash
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
export ENABLE_FORY_DEBUG_OUTPUT=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
DOCS_DIR="$SCRIPT_DIR/../../docs/benchmarks/csharp"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

DATA=""
SERIALIZER=""
DURATION="3"
WARMUP="1"
OUTPUT_DIR=""
COPY_DOCS=true
EXTERNAL_EQUIVALENCE=false
EXTERNAL_FIRST=false
ALLOCATION_ITERATIONS=""

usage() {
    cat <<USAGE
Usage: $0 [OPTIONS]

Build and run C# benchmarks.

Options:
  --data <struct|sample|mediacontent|structlist|samplelist|mediacontentlist>
                               Filter benchmark by data type
  --serializer <fory|protobuf|msgpack>
                               Filter benchmark by serializer
  --duration <seconds>         Measure duration per benchmark (default: 3)
  --warmup <seconds>           Warmup duration per benchmark (default: 1)
  --output-dir <dir>           Base directory for benchmark outputs
  --no-copy-docs               Skip copying report/plots into docs/benchmarks/csharp
  --external-equivalence       Run ordinary/external Fory equivalence cases
  --external-first             Run each external worker before its ordinary peer
  --allocation-iterations <n>  Measure allocations for each equivalence case
  --help                       Show this help
USAGE
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --data)
            DATA="$2"
            shift 2
            ;;
        --serializer)
            SERIALIZER="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --warmup)
            WARMUP="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --no-copy-docs)
            COPY_DOCS=false
            shift
            ;;
        --external-equivalence)
            EXTERNAL_EQUIVALENCE=true
            shift
            ;;
        --external-first)
            EXTERNAL_FIRST=true
            shift
            ;;
        --allocation-iterations)
            ALLOCATION_ITERATIONS="$2"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

if [[ "$EXTERNAL_EQUIVALENCE" == true && -n "$SERIALIZER" ]]; then
    echo -e "${RED}--external-equivalence does not accept --serializer.${NC}"
    exit 1
fi

if [[ "$EXTERNAL_EQUIVALENCE" == false && -n "$ALLOCATION_ITERATIONS" ]]; then
    echo -e "${RED}--allocation-iterations requires --external-equivalence.${NC}"
    exit 1
fi

if [[ "$EXTERNAL_EQUIVALENCE" == false && "$EXTERNAL_FIRST" == true ]]; then
    echo -e "${RED}--external-first requires --external-equivalence.${NC}"
    exit 1
fi

if [[ "$EXTERNAL_EQUIVALENCE" == true && "${FORY_BENCH_SCHEMA_MISMATCH:-0}" == "1" ]]; then
    echo -e "${RED}--external-equivalence does not support schema-mismatch mode.${NC}"
    exit 1
fi

if [[ "$EXTERNAL_EQUIVALENCE" == true && -n "$DATA" ]]; then
    case "$DATA" in
        class-root|struct-root|holder-field|list-field|list-root|map-field|map-root)
            ;;
        *)
            echo -e "${RED}Unknown external-equivalence data lane: $DATA${NC}"
            exit 1
            ;;
    esac
fi

if [[ "$EXTERNAL_EQUIVALENCE" == false && "${FORY_BENCH_SCHEMA_MISMATCH:-0}" == "1" && "$SERIALIZER" != "fory" ]]; then
    echo -e "${RED}FORY_BENCH_SCHEMA_MISMATCH=1 supports only Fory benchmarks; rerun with --serializer fory.${NC}"
    exit 1
fi

if [[ -n "$OUTPUT_DIR" ]]; then
    BUILD_DIR="$OUTPUT_DIR/build"
    REPORT_DIR="$OUTPUT_DIR/report"
else
    BUILD_DIR="build"
    REPORT_DIR="report"
fi

echo -e "${GREEN}=== Fory C# Benchmark ===${NC}"
echo ""

if [[ "$EXTERNAL_EQUIVALENCE" == true ]]; then
    mkdir -p "$BUILD_DIR"
    RESULT_JSON="$BUILD_DIR/external_equivalence_results.json"
    SIDE_DIR="$BUILD_DIR/external_equivalence_sides"
    mkdir -p "$SIDE_DIR"

    if [[ -n "$DATA" ]]; then
        EXTERNAL_LANES=("$DATA")
    else
        EXTERNAL_LANES=(
            class-root
            struct-root
            holder-field
            list-field
            list-root
            map-field
            map-root
        )
    fi

    echo -e "${YELLOW}[1/4] Restoring dependencies...${NC}"
    dotnet restore ./Fory.CSharpBenchmark.csproj >/dev/null

    echo -e "${YELLOW}[2/4] Building benchmark once...${NC}"
    dotnet build -c Release --no-restore ./Fory.CSharpBenchmark.csproj

    echo -e "${YELLOW}[3/4] Running isolated adjacent pairs...${NC}"
    MERGE_ARGS=(--output "$RESULT_JSON")
    PAIR_ORDER=(ordinary external)
    if [[ "$EXTERNAL_FIRST" == true ]]; then
        PAIR_ORDER=(external ordinary)
    fi
    for LANE in "${EXTERNAL_LANES[@]}"; do
        ORDINARY_JSON="$SIDE_DIR/${LANE}-ordinary.json"
        EXTERNAL_JSON="$SIDE_DIR/${LANE}-external.json"
        WORKER_ARGS=(
            --external-equivalence
            --data "$LANE"
            --duration "$DURATION"
            --warmup "$WARMUP"
        )
        if [[ -n "$ALLOCATION_ITERATIONS" ]]; then
            WORKER_ARGS+=(--allocation-iterations "$ALLOCATION_ITERATIONS")
        fi

        for IMPLEMENTATION in "${PAIR_ORDER[@]}"; do
            if [[ "$IMPLEMENTATION" == ordinary ]]; then
                SIDE_JSON="$ORDINARY_JSON"
            else
                SIDE_JSON="$EXTERNAL_JSON"
            fi
            echo "Running $LANE $IMPLEMENTATION worker..."
            dotnet run -c Release --no-build \
                --project ./Fory.CSharpBenchmark.csproj -- \
                "${WORKER_ARGS[@]}" \
                --external-implementation "$IMPLEMENTATION" \
                --output "$SIDE_JSON"
        done

        MERGE_ARGS+=(
            --lane "$LANE"
            --input "$ORDINARY_JSON"
            --input "$EXTERNAL_JSON"
        )
    done

    echo -e "${YELLOW}[4/4] Validating and merging results...${NC}"
    python3 external_equivalence_report.py "${MERGE_ARGS[@]}"
    echo ""
    echo -e "${GREEN}=== All done! ===${NC}"
    echo "Results written to: $RESULT_JSON"
    exit 0
fi

mkdir -p "$BUILD_DIR" "$REPORT_DIR"
RESULT_JSON="$BUILD_DIR/benchmark_results.json"
RUN_ARGS=(
    --output "$RESULT_JSON"
    --duration "$DURATION"
    --warmup "$WARMUP"
)

if [[ -n "$DATA" ]]; then
    RUN_ARGS+=(--data "$DATA")
fi

if [[ -n "$SERIALIZER" ]]; then
    RUN_ARGS+=(--serializer "$SERIALIZER")
fi

echo -e "${YELLOW}[1/3] Restoring dependencies...${NC}"
dotnet restore ./Fory.CSharpBenchmark.csproj >/dev/null

echo -e "${YELLOW}[2/3] Running benchmark...${NC}"
dotnet run -c Release --project ./Fory.CSharpBenchmark.csproj -- "${RUN_ARGS[@]}"

echo -e "${YELLOW}[3/3] Generating report...${NC}"
# Check for Python dependencies needed for plotting.
if ! python3 -c "import matplotlib" 2>/dev/null; then
    echo -e "${YELLOW}Installing required Python packages...${NC}"
    pip3 install matplotlib numpy psutil
fi

python3 benchmark_report.py --json-file "$RESULT_JSON" --output-dir "$REPORT_DIR"
if [[ "$COPY_DOCS" == true ]]; then
    mkdir -p "$DOCS_DIR"
    cp "$REPORT_DIR/README.md" "$DOCS_DIR/README.md"
    cp "$REPORT_DIR/throughput.png" "$DOCS_DIR/throughput.png"
    echo -e "${GREEN}Copied report and throughput plot to: ${DOCS_DIR}${NC}"
fi

echo ""
echo -e "${GREEN}=== All done! ===${NC}"
if [[ "$REPORT_DIR" = /* ]]; then
    REPORT_PATH="$REPORT_DIR/README.md"
    REPORT_PLOTS_DIR="$REPORT_DIR"
else
    REPORT_PATH="$SCRIPT_DIR/$REPORT_DIR/README.md"
    REPORT_PLOTS_DIR="$SCRIPT_DIR/$REPORT_DIR"
fi
echo "Report generated at: $REPORT_PATH"
echo "Plots saved in: $REPORT_PLOTS_DIR/"
if [[ "$COPY_DOCS" == true ]]; then
    echo "Docs sync: $DOCS_DIR"
fi
