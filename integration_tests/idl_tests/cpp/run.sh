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
BUILD_DIR="${SCRIPT_DIR}/build"
VENV_DIR="${BUILD_DIR}/cmake-venv"

resolve_cmake() {
  if command -v cmake >/dev/null 2>&1; then
    echo "cmake"
    return
  fi

  if python -m cmake --version >/dev/null 2>&1; then
    echo "python -m cmake"
    return
  fi

  if [[ ! -x "${VENV_DIR}/bin/python" ]]; then
    python -m venv "${VENV_DIR}"
    "${VENV_DIR}/bin/python" -m pip install --quiet cmake
  fi

  echo "${VENV_DIR}/bin/python -m cmake"
}

if [[ "${FORY_CPP_IDL_SKIP_BUILD:-}" == "1" && -x "${BUILD_DIR}/idl_roundtrip" ]]; then
  "${BUILD_DIR}/idl_roundtrip"
  exit 0
fi

cmake_cmd="$(resolve_cmake)"

eval "${cmake_cmd}" -S "${SCRIPT_DIR}" -B "${BUILD_DIR}" -DCMAKE_BUILD_TYPE=Release
eval "${cmake_cmd}" --build "${BUILD_DIR}" --parallel

"${BUILD_DIR}/idl_roundtrip"
