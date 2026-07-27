#!/usr/bin/env python3
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

import argparse
import base64
import binascii
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

LANES = (
    "class-root",
    "struct-root",
    "holder-field",
    "list-field",
    "list-root",
    "map-field",
    "map-root",
)
OPERATIONS = ("serialize", "deserialize")
IMPLEMENTATIONS = ("ordinary", "external")
TEXT_METADATA = (
    "RuntimeVersion",
    "OsDescription",
    "OsArchitecture",
    "ProcessArchitecture",
)
METADATA_FIELDS = TEXT_METADATA + (
    "ProcessorCount",
    "WarmupSeconds",
    "DurationSeconds",
    "AllocationIterations",
)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Merge isolated C# external-equivalence benchmark results."
    )
    parser.add_argument(
        "--lane",
        action="append",
        choices=LANES,
        help="Expected lane; repeat as needed. Defaults to all lanes.",
    )
    parser.add_argument(
        "--input",
        action="append",
        required=True,
        help="Worker JSON path; repeat for every ordinary/external result.",
    )
    parser.add_argument("--output", required=True, help="Combined JSON output path.")
    return parser.parse_args(argv)


def require_object(value, owner: str) -> dict:
    if not isinstance(value, dict):
        raise TypeError(f"{owner} must be a JSON object")
    return value


def require_string(value, owner: str) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{owner} must be a non-empty string")
    return value


def require_int(value, owner: str, minimum: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ValueError(
            f"{owner} must be an integer greater than or equal to {minimum}"
        )
    return value


def require_number(value, owner: str, positive: bool):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError(f"{owner} must be a number")
    number = float(value)
    if not math.isfinite(number) or (number <= 0 if positive else number < 0):
        qualifier = "positive" if positive else "non-negative"
        raise ValueError(f"{owner} must be a finite {qualifier} number")
    return value


def validate_metadata(document: dict, path: Path) -> dict:
    owner = str(path)
    require_string(document.get("GeneratedAtUtc"), f"{owner}.GeneratedAtUtc")
    for field in TEXT_METADATA:
        require_string(document.get(field), f"{owner}.{field}")
    require_int(document.get("ProcessorCount"), f"{owner}.ProcessorCount", 1)
    require_number(document.get("WarmupSeconds"), f"{owner}.WarmupSeconds", True)
    require_number(document.get("DurationSeconds"), f"{owner}.DurationSeconds", True)
    require_int(
        document.get("AllocationIterations"),
        f"{owner}.AllocationIterations",
        0,
    )
    return {field: document[field] for field in METADATA_FIELDS}


def validate_measurement(value, owner: str) -> dict:
    measurement = require_object(value, owner)
    for field in ("OperationsPerSecond", "AverageNanoseconds", "ElapsedSeconds"):
        require_number(measurement.get(field), f"{owner}.{field}", True)
    require_int(measurement.get("Iterations"), f"{owner}.Iterations", 1)
    return measurement


def decode_frame(value, owner: str, serialized_size: int) -> bytes:
    encoded = require_string(value, owner)
    try:
        frame = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as error:
        raise ValueError(f"{owner} is not valid Base64: {error}") from error
    if base64.b64encode(frame).decode("ascii") != encoded:
        raise ValueError(f"{owner} is not canonical Base64")
    if len(frame) != serialized_size:
        raise ValueError(
            f"{owner} decodes to {len(frame)} bytes, expected {serialized_size}"
        )
    return frame


def load_inputs(paths, expected_lanes):
    metadata = None
    results = {}
    expected_lane_set = set(expected_lanes)

    for input_path in paths:
        path = Path(input_path)
        with path.open("r", encoding="utf-8") as source:
            document = require_object(json.load(source), str(path))

        current_metadata = validate_metadata(document, path)
        if metadata is None:
            metadata = current_metadata
        elif current_metadata != metadata:
            differences = [
                field
                for field in METADATA_FIELDS
                if current_metadata[field] != metadata[field]
            ]
            raise ValueError(
                f"{path} has incompatible metadata fields: {', '.join(differences)}"
            )

        implementation = require_string(
            document.get("Implementation"), f"{path}.Implementation"
        )
        if implementation not in IMPLEMENTATIONS:
            raise ValueError(f"{path}.Implementation must be ordinary or external")

        raw_results = document.get("Results")
        if not isinstance(raw_results, list) or not raw_results:
            raise ValueError(f"{path}.Results must be a non-empty array")

        for index, raw_result in enumerate(raw_results):
            owner = f"{path}.Results[{index}]"
            result = require_object(raw_result, owner)
            lane = result.get("DataType")
            if lane not in expected_lane_set:
                raise ValueError(f"{owner}.DataType is not an expected lane: {lane!r}")
            operation = result.get("Operation")
            if operation not in OPERATIONS:
                raise ValueError(f"{owner}.Operation must be serialize or deserialize")
            key = (implementation, lane, operation)
            if key in results:
                raise ValueError(f"duplicate result for {'/'.join(key)}")

            serialized_size = require_int(
                result.get("SerializedSize"), f"{owner}.SerializedSize", 0
            )
            frame = decode_frame(
                result.get("SerializedFrameBase64"),
                f"{owner}.SerializedFrameBase64",
                serialized_size,
            )
            measurement = validate_measurement(
                result.get("Measurement"), f"{owner}.Measurement"
            )
            allocated = result.get("AllocatedBytesPerOperation")
            if current_metadata["AllocationIterations"] == 0:
                if allocated is not None:
                    raise ValueError(
                        f"{owner}.AllocatedBytesPerOperation must be null when "
                        "allocation measurement is disabled"
                    )
            elif allocated is None:
                raise ValueError(
                    f"{owner}.AllocatedBytesPerOperation is required when "
                    "allocation measurement is enabled"
                )
            else:
                require_number(
                    allocated,
                    f"{owner}.AllocatedBytesPerOperation",
                    False,
                )

            results[key] = {
                "SerializedSize": serialized_size,
                "Frame": frame,
                "Measurement": measurement,
                "AllocatedBytesPerOperation": allocated,
            }

    return metadata, results


def merge_results(metadata: dict, results: dict, expected_lanes) -> dict:
    expected_keys = {
        (implementation, lane, operation)
        for implementation in IMPLEMENTATIONS
        for lane in expected_lanes
        for operation in OPERATIONS
    }
    missing = expected_keys - set(results)
    if missing:
        raise ValueError(
            f"missing results: {', '.join('/'.join(key) for key in sorted(missing))}"
        )

    combined_results = []
    for lane in expected_lanes:
        for implementation in IMPLEMENTATIONS:
            serialized = results[(implementation, lane, "serialize")]
            deserialized = results[(implementation, lane, "deserialize")]
            if (
                serialized["SerializedSize"] != deserialized["SerializedSize"]
                or serialized["Frame"] != deserialized["Frame"]
            ):
                raise ValueError(
                    f"serialized frame differs between operations for "
                    f"{implementation}/{lane}"
                )

        for operation in OPERATIONS:
            ordinary = results[("ordinary", lane, operation)]
            external = results[("external", lane, operation)]
            if ordinary["SerializedSize"] != external["SerializedSize"]:
                raise ValueError(
                    f"serialized-size mismatch for {lane}/{operation}: "
                    f"ordinary={ordinary['SerializedSize']}, "
                    f"external={external['SerializedSize']}"
                )
            if ordinary["Frame"] != external["Frame"]:
                raise ValueError(f"serialized-byte mismatch for {lane}/{operation}")
            if (
                ordinary["AllocatedBytesPerOperation"]
                != external["AllocatedBytesPerOperation"]
            ):
                raise ValueError(
                    f"allocation mismatch for {lane}/{operation}: "
                    f"ordinary={ordinary['AllocatedBytesPerOperation']}, "
                    f"external={external['AllocatedBytesPerOperation']}"
                )

            ordinary_measurement = ordinary["Measurement"]
            external_measurement = external["Measurement"]
            slowdown = (
                external_measurement["AverageNanoseconds"]
                / ordinary_measurement["AverageNanoseconds"]
                - 1.0
            ) * 100.0
            allocation_enabled = metadata["AllocationIterations"] > 0
            combined_results.append(
                {
                    "DataType": lane,
                    "Operation": operation,
                    "SerializedSize": ordinary["SerializedSize"],
                    "Ordinary": ordinary_measurement,
                    "External": external_measurement,
                    "SlowdownPercent": slowdown,
                    "OrdinaryAllocatedBytesPerOperation": ordinary[
                        "AllocatedBytesPerOperation"
                    ],
                    "ExternalAllocatedBytesPerOperation": external[
                        "AllocatedBytesPerOperation"
                    ],
                    "AllocationEqual": True if allocation_enabled else None,
                }
            )

    return {
        "GeneratedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        **metadata,
        "Results": combined_results,
    }


def print_summary(output: dict) -> None:
    print("=== External-Type Equivalence Summary ===")
    for result in output["Results"]:
        allocation = ""
        if result["AllocationEqual"] is not None:
            allocation = (
                f", allocated={result['OrdinaryAllocatedBytesPerOperation']:.2f} B/op"
            )
        print(
            f"{result['DataType']}/{result['Operation']}: "
            f"ordinary={result['Ordinary']['AverageNanoseconds']:.1f} ns/op, "
            f"external={result['External']['AverageNanoseconds']:.1f} ns/op, "
            f"slowdown={result['SlowdownPercent']:+.2f}%{allocation}"
        )


def main(argv=None) -> int:
    args = parse_args(argv)
    lanes = args.lane if args.lane is not None else list(LANES)
    if len(lanes) != len(set(lanes)):
        raise ValueError("--lane may specify each lane only once")

    metadata, results = load_inputs(args.input, lanes)
    output = merge_results(metadata, results, lanes)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as destination:
        json.dump(output, destination, indent=2)
        destination.write("\n")
    print_summary(output)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, TypeError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
