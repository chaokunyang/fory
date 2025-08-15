#!/usr/bin/env bash

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


set -x

# Cause the script to exit if a single command fails.
set -e

# configure ~/.pypirc before run this script
#if [ ! -f ~/.pypirc ]; then
#  echo  "Please configure .pypirc before run this script"
#  exit 1
#fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

bump_version() {
  python "$ROOT/ci/release.py" bump_version -l all -version "$1"
}

bump_java_version() {
  python "$ROOT/ci/release.py" bump_version -l java -version "$1"
}

bump_py_version() {
  local version="$1"
  if [ -z "$version" ]; then
    # Get the latest tag from the current Git repository
    version=$(git describe --tags --abbrev=0)
    # Check if the tag starts with 'v' and strip it
    if [[ $version == v* ]]; then
      version="${version:1}"
    fi
  fi
  python "$ROOT/ci/release.py" bump_version -l python -version "$version"
}

bump_javascript_version() {
  python "$ROOT/ci/release.py" bump_version -l javascript -version "$1"
}

deploy_jars() {
  cd "$ROOT/java"
  mvn -T10 clean deploy --no-transfer-progress -DskipTests -Prelease
}

build_pyfory() {
  echo "Python version $(python -V), path $(which python)"
  install_pyarrow
  pip install Cython wheel pytest auditwheel
  pushd "$ROOT/python"
  pip list
  echo "Install pyfory"
  # Fix strange installed deps not found
  pip install setuptools -U

  if [[ "$OSTYPE" == "darwin"* ]]; then
    MACOS_VERSION=$(sw_vers -productVersion | cut -d. -f1-2)
    echo "MACOS_VERSION: $MACOS_VERSION"
    echo "MACOSX_DEPLOYMENT_TARGET: $MACOSX_DEPLOYMENT_TARGET"
    if [[ "$MACOS_VERSION" == "14"* ]]; then
      export MACOSX_DEPLOYMENT_TARGET=14.0
    fi
    if [[ "$MACOS_VERSION" == "13"* ]]; then
      export MACOSX_DEPLOYMENT_TARGET=13.0
    fi
  fi

  python setup.py bdist_wheel --dist-dir=../dist
  ls -l ../dist

  if [ -n "$PLAT" ]; then
    # In manylinux container, repair the wheel to embed shared libraries
    # and rename the wheel with the manylinux tag.
    PYARROW_LIB_DIR=$(python -c 'import pyarrow; print(":".join(pyarrow.get_library_dirs()))')
    export LD_LIBRARY_PATH="$PYARROW_LIB_DIR:$LD_LIBRARY_PATH"
    auditwheel repair ../dist/pyfory-*-linux_*.whl --plat "$PLAT" --exclude '*arrow*' --exclude '*parquet*' --exclude '*numpy*' -w ../dist/
    rm ../dist/pyfory-*-linux_*.whl
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    # Check macOS version
    MACOS_VERSION=$(sw_vers -productVersion | cut -d. -f1-2)
    if [[ "$MACOS_VERSION" == "13"* ]]; then
      # Check if wheel ends with x86_64.whl
      for wheel in ../dist/pyfory-*-macosx*.whl; do
        if [[ "$wheel" == *"x86_64.whl" ]]; then
          echo "Fixing wheel tags for x86_64 wheel: $wheel"
          wheel tags --platform-tag macosx_12_0_x86_64 "$wheel"
        else
          echo "Skipping wheel tags for non-x86_64 wheel: $wheel"
        fi
      done
    else
      # Other macOS versions: skip wheel repair
      echo "Skipping wheel repair for macOS $MACOS_VERSION"
    fi
  elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    echo "Skip windows wheel repair"
  fi
  ls -l ../dist
  popd
}

install_pyarrow() {
  pyversion=$(python -V | cut -d' ' -f2)
  if [[ $pyversion  ==  3.13* ]]; then
    pip install pyarrow==18.0.0
    pip install numpy
  else
    pip install pyarrow==15.0.0
    # Automatically install numpy
  fi
}

deploy_scala() {
  echo "Start to build jars"
  sbt +publishSigned
  echo "Start to prepare upload"
  sbt sonatypePrepare
  echo "Start to upload jars"
  sbt sonatypeBundleUpload
  echo "Deploy scala jars succeed!"
}

case "$1" in
java) # Deploy jars to maven repository.
  deploy_jars
  ;;
*)
  echo "Execute command $*"
  "$@"
  ;;
esac
