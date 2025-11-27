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

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Fory C++ Benchmark ===${NC}"
echo ""

# Number of parallel jobs for build
JOBS=16

# Step 1: Build
echo -e "${YELLOW}[1/3] Building benchmark...${NC}"
mkdir -p build
cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
cmake --build . -j"$JOBS"
echo -e "${GREEN}Build complete!${NC}"
echo ""

# Step 2: Run benchmark
echo -e "${YELLOW}[2/3] Running benchmark...${NC}"
./fory_benchmark --benchmark_format=json --benchmark_out=benchmark_results.json
echo -e "${GREEN}Benchmark complete!${NC}"
echo ""

# Step 3: Generate report
echo -e "${YELLOW}[3/3] Generating report...${NC}"
cd "$SCRIPT_DIR"

# Check for Python dependencies
if ! python3 -c "import matplotlib" 2>/dev/null; then
    echo -e "${YELLOW}Installing required Python packages...${NC}"
    pip3 install matplotlib numpy psutil
fi

python3 benchmark_report.py --json-file build/benchmark_results.json --output-dir report
echo ""

echo -e "${GREEN}=== All done! ===${NC}"
echo -e "Report generated at: ${SCRIPT_DIR}/report/REPORT.md"
echo -e "Plots saved in: ${SCRIPT_DIR}/report/"
