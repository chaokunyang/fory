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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
JAVA_TEST_PATTERN="${IDL_JAVA_TEST_PATTERN:-}"

python "${SCRIPT_DIR}/generate_idl.py" --lang java

cd "${ROOT_DIR}/java"
MAVEN_ARGS=(-T16 --no-transfer-progress)
if [[ -n "${JAVA_TEST_PATTERN}" ]]; then
  MAVEN_ARGS+=("-Dtest=${JAVA_TEST_PATTERN}")
fi
MAVEN_ARGS+=(test -f "${ROOT_DIR}/integration_tests/idl_tests/java/pom.xml")
ENABLE_FORY_DEBUG_OUTPUT=1 mvn "${MAVEN_ARGS[@]}"
