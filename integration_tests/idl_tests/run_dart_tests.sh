#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

python "${SCRIPT_DIR}/generate_idl.py" --lang dart

cd "${SCRIPT_DIR}/dart"
dart pub get
dart run build_runner build --delete-conflicting-outputs
dart test

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fory-dart-idl-XXXXXX")"
cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

export DATA_FILE="${TMP_DIR}/addressbook.bin"
export DATA_FILE_AUTO_ID="${TMP_DIR}/auto_id.bin"
export DATA_FILE_TREE="${TMP_DIR}/tree.bin"
export DATA_FILE_GRAPH="${TMP_DIR}/graph.bin"
export DATA_FILE_EXAMPLE_MESSAGE="${TMP_DIR}/example_message.bin"
export DATA_FILE_EXAMPLE_UNION="${TMP_DIR}/example_union.bin"
IDL_COMPATIBLE=true dart test test/idl_roundtrip_test.dart --plain-name "interop file roundtrip hooks when env vars are set"
IDL_COMPATIBLE=false dart test test/idl_roundtrip_test.dart --plain-name "interop file roundtrip hooks when env vars are set"
