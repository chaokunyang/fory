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

# Get the repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

echo "=== Fory C++ Hello Row Example (Bazel) ==="
echo ""

cd "${REPO_ROOT}"

# Build
echo "Building with Bazel..."
bazel build //examples/cpp/hello_row:hello_row

echo ""
echo "=== Build successful! ==="
echo ""

# Run
echo "=== Running hello_row ==="
echo ""
bazel run //examples/cpp/hello_row:hello_row

echo ""
echo "=== Example completed! ==="
