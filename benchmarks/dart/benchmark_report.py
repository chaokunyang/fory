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
import json
import math
import os
import socket
import subprocess
from collections import defaultdict

import matplotlib.pyplot as plt


COLORS = {
    "fory": "#FF6F01",
    "protobuf": "#55BCC2",
}

DATA_TYPES = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]

DISPLAY_NAMES = {
    "struct": "Struct",
    "sample": "Sample",
    "mediacontent": "MediaContent",
    "structlist": "StructList",
    "samplelist": "SampleList",
    "mediacontentlist": "MediaContentList",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate Dart benchmark report")
    parser.add_argument("--json-file", required=True)
    parser.add_argument("--output-dir", required=True)
    return parser.parse_args()


def load_payload(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def collect_results(payload: dict) -> dict:
    results = defaultdict(lambda: defaultdict(dict))
    for record in payload["results"]:
        results[record["data_type"]][record["operation"]][record["serializer"]] = (
            record["median_ops_per_sec"]
        )
    return results


def format_label(value: float) -> str:
    if value >= 1e6:
        return f"{value / 1e6:.2f}M"
    if value >= 1e3:
        return f"{value / 1e3:.2f}K"
    return f"{value:.2f}"


def format_int(value: float) -> str:
    return f"{int(round(value)):,}"


def fastest_entry(fory_value: float, protobuf_value: float) -> str:
    if protobuf_value <= 0 and fory_value <= 0:
        return "n/a"
    if protobuf_value <= 0:
        return "fory"
    if fory_value <= 0:
        return "protobuf"
    if math.isclose(fory_value, protobuf_value):
        return "tie (1.00x)"
    if fory_value > protobuf_value:
        return f"fory ({fory_value / protobuf_value:.2f}x)"
    return f"protobuf ({protobuf_value / fory_value:.2f}x)"


def detect_memory_gb() -> str:
    try:
        output = subprocess.check_output(
            ["sysctl", "-n", "hw.memsize"],
            text=True,
        ).strip()
        return f"{int(output) / (1024 ** 3):.2f}"
    except Exception:
        return "Unknown"


def system_info(metadata: dict) -> list[tuple[str, str]]:
    return [
        ("Timestamp", metadata.get("generated_at", "Unknown")),
        ("OS", metadata.get("os_version", metadata.get("os", "Unknown"))),
        ("Host", socket.gethostname() or "Unknown"),
        ("CPU Cores (Logical)", str(metadata.get("cpus", "Unknown"))),
        ("Memory (GB)", detect_memory_gb()),
        ("Dart", metadata.get("dart_version", "Unknown")),
        ("Samples per case", str(metadata.get("samples", "Unknown"))),
        ("Warmup per case (s)", str(metadata.get("warmup_seconds", "Unknown"))),
        ("Duration per case (s)", str(metadata.get("duration_seconds", "Unknown"))),
    ]


def save_summary_plot(results: dict, output_dir: str) -> str:
    figure, axes = plt.subplots(2, 1, figsize=(14, 10), sharex=True)
    x_positions = range(len(DATA_TYPES))
    bar_width = 0.36

    for axis, operation in zip(axes, ["serialize", "deserialize"]):
        fory_values = [
            results.get(data_type, {}).get(operation, {}).get("fory", 0.0)
            for data_type in DATA_TYPES
        ]
        protobuf_values = [
            results.get(data_type, {}).get(operation, {}).get("protobuf", 0.0)
            for data_type in DATA_TYPES
        ]
        fory_positions = [x - bar_width / 2 for x in x_positions]
        protobuf_positions = [x + bar_width / 2 for x in x_positions]
        fory_bars = axis.bar(
            fory_positions,
            fory_values,
            width=bar_width,
            color=COLORS["fory"],
            label="Fory",
        )
        protobuf_bars = axis.bar(
            protobuf_positions,
            protobuf_values,
            width=bar_width,
            color=COLORS["protobuf"],
            label="Protobuf",
        )
        axis.set_title(f"{operation.capitalize()} throughput")
        axis.set_ylabel("ops/s")
        axis.grid(True, axis="y", linestyle="--", alpha=0.35)
        axis.legend(loc="upper right")
        for bars in (fory_bars, protobuf_bars):
            for bar in bars:
                value = bar.get_height()
                axis.annotate(
                    format_label(value),
                    xy=(bar.get_x() + bar.get_width() / 2, value),
                    xytext=(0, 3),
                    textcoords="offset points",
                    ha="center",
                    va="bottom",
                    fontsize=8,
                    rotation=90,
                )

    axes[-1].set_xticks(list(x_positions))
    axes[-1].set_xticklabels([DISPLAY_NAMES[data_type] for data_type in DATA_TYPES])
    figure.suptitle("Apache Fory Dart Benchmark")
    figure.tight_layout(rect=[0, 0, 1, 0.97])

    path = os.path.join(output_dir, "throughput.png")
    figure.savefig(path, dpi=150)
    plt.close(figure)
    return path


def save_per_type_plots(results: dict, output_dir: str) -> list[tuple[str, str]]:
    plot_paths = []
    for data_type in DATA_TYPES:
        operations = results.get(data_type, {})
        if not operations:
            continue
        figure, axes = plt.subplots(1, 2, figsize=(12, 5))
        for index, operation in enumerate(["serialize", "deserialize"]):
            serializers = ["fory", "protobuf"]
            values = [
                operations.get(operation, {}).get(serializer, 0.0)
                for serializer in serializers
            ]
            bars = axes[index].bar(
                serializers,
                values,
                color=[COLORS[serializer] for serializer in serializers],
            )
            axes[index].set_title(f"{operation.capitalize()} throughput")
            axes[index].set_ylabel("ops/s")
            axes[index].grid(True, axis="y", linestyle="--", alpha=0.4)
            for bar, value in zip(bars, values):
                axes[index].annotate(
                    format_label(value),
                    xy=(bar.get_x() + bar.get_width() / 2, value),
                    xytext=(0, 3),
                    textcoords="offset points",
                    ha="center",
                    va="bottom",
                    fontsize=9,
                )
        figure.suptitle(DISPLAY_NAMES[data_type])
        figure.tight_layout(rect=[0, 0, 1, 0.95])
        path = os.path.join(output_dir, f"{data_type}.png")
        figure.savefig(path, dpi=150)
        plt.close(figure)
        plot_paths.append((DISPLAY_NAMES[data_type], path))
    return plot_paths


def write_report(payload: dict, results: dict, output_dir: str, plot_paths: list[tuple[str, str]]):
    metadata = payload["metadata"]
    report_path = os.path.join(output_dir, "README.md")
    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("# Fory Dart Benchmark\n\n")
        handle.write(
            "This benchmark compares serialization and deserialization throughput for "
            "Apache Fory and Protocol Buffers in Dart.\n\n"
        )
        handle.write("## Hardware and Runtime Info\n\n")
        handle.write("| Key | Value |\n")
        handle.write("| --- | --- |\n")
        for key, value in system_info(metadata):
            handle.write(f"| {key} | {value} |\n")

        handle.write("\n## Throughput Results\n\n")
        handle.write("![Throughput](throughput.png)\n\n")
        handle.write(
            "| Datatype | Operation | Fory TPS | Protobuf TPS | Fastest |\n"
        )
        handle.write("| --- | --- | ---: | ---: | --- |\n")
        for data_type in DATA_TYPES:
            operations = results.get(data_type, {})
            if not operations:
                continue
            for operation in ["serialize", "deserialize"]:
                fory_value = operations.get(operation, {}).get("fory", 0.0)
                protobuf_value = operations.get(operation, {}).get("protobuf", 0.0)
                handle.write(
                    f"| {DISPLAY_NAMES[data_type]} | {operation.capitalize()} | "
                    f"{format_int(fory_value)} | {format_int(protobuf_value)} | "
                    f"{fastest_entry(fory_value, protobuf_value)} |\n"
                )

        handle.write("\n## Serialized Size (bytes)\n\n")
        handle.write("| Datatype | Fory | Protobuf |\n")
        handle.write("| --- | ---: | ---: |\n")
        for data_type in DATA_TYPES:
            sizes = payload["sizes"].get(data_type)
            if sizes is None:
                continue
            handle.write(
                f"| {DISPLAY_NAMES[data_type]} | {sizes['fory']} | {sizes['protobuf']} |\n"
            )

        if plot_paths:
            handle.write("\n## Per-workload Plots\n\n")
            for display_name, path in plot_paths:
                handle.write(f"### {display_name}\n\n")
                handle.write(f"![{display_name}]({os.path.basename(path)})\n\n")


def main() -> None:
    args = parse_args()
    os.makedirs(args.output_dir, exist_ok=True)
    payload = load_payload(args.json_file)
    results = collect_results(payload)
    save_summary_plot(results, args.output_dir)
    plot_paths = save_per_type_plots(results, args.output_dir)
    write_report(payload, results, args.output_dir, plot_paths)


if __name__ == "__main__":
    main()
