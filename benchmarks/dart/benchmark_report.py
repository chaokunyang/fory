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
import shutil
import socket
import subprocess
from collections import defaultdict

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter


COLORS = {
    "fory": "#FF6F01",
    "protobuf": "#55BCC2",
    "json": (0.55, 0.40, 0.45),
}
SERIALIZERS = ["fory", "protobuf", "json"]

DATA_TYPES = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]

DISPLAY_NAMES = {
    "struct": "NumericStruct",
    "sample": "Sample",
    "mediacontent": "MediaContent",
    "structlist": "NumericStructList",
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


def format_tick(value: float, _position) -> str:
    return format_label(value)


def format_int(value: float) -> str:
    return f"{int(round(value)):,}"


def datatype_plot_label(data_type: str) -> str:
    if data_type == "struct":
        return "NumericStruct"
    if data_type == "structlist":
        return "NumericStruct\nList"
    if data_type == "mediacontent":
        return "MediaContent"
    if data_type == "mediacontentlist":
        return "MediaContent\nList"
    if data_type.endswith("list"):
        return f"{data_type[:-4].capitalize()}\nList"
    return data_type.capitalize()


def fastest_entry(values: dict[str, float]) -> str:
    positive = {serializer: value for serializer, value in values.items() if value > 0}
    if not positive:
        return "n/a"
    ranked = sorted(positive.items(), key=lambda entry: entry[1], reverse=True)
    best_serializer, best_value = ranked[0]
    if len(ranked) == 1:
        return best_serializer
    second_value = ranked[1][1]
    if math.isclose(best_value, second_value):
        return "tie (1.00x)"
    return f"{best_serializer} ({best_value / second_value:.2f}x)"


def detect_memory_gb() -> str:
    try:
        output = subprocess.check_output(
            ["sysctl", "-n", "hw.memsize"],
            text=True,
        ).strip()
        return f"{int(output) / (1024**3):.2f}"
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


def plot_summary_group(ax, results: dict, data_type: str) -> None:
    if data_type not in results:
        ax.set_title(f"{DISPLAY_NAMES[data_type]}\nNo Data")
        ax.axis("off")
        return

    serializers = [
        serializer
        for serializer in SERIALIZERS
        if any(
            results.get(data_type, {}).get(operation, {}).get(serializer, 0.0) > 0
            for operation in ["serialize", "deserialize"]
        )
    ]
    if not serializers:
        ax.set_title(f"{DISPLAY_NAMES[data_type]}\nNo Data")
        ax.axis("off")
        return

    operations = ["serialize", "deserialize"]
    x_positions = np.arange(len(operations))
    bar_width = 0.8 / len(serializers)
    for index, serializer in enumerate(serializers):
        values = [
            results.get(data_type, {}).get(operation, {}).get(serializer, 0.0)
            for operation in operations
        ]
        offset = (index - (len(serializers) - 1) / 2) * bar_width
        ax.bar(
            x_positions + offset,
            values,
            width=bar_width,
            label=serializer,
            color=COLORS[serializer],
        )

    ax.set_title(DISPLAY_NAMES[data_type])
    ax.set_xticks(x_positions)
    ax.set_xticklabels(["Serialize", "Deserialize"])
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tick))
    ax.legend(loc="upper right", fontsize=8, framealpha=0.9)


def save_summary_plot(results: dict, output_dir: str) -> str:
    figure, axes = plt.subplots(2, 3, figsize=(18, 10))
    figure.suptitle("Dart Serialization Throughput", fontsize=14)

    for index, (ax, data_type) in enumerate(zip(axes.flat, DATA_TYPES)):
        plot_summary_group(ax, results, data_type)
        if index % 3 == 0:
            ax.set_ylabel("Throughput (ops/sec)")
        else:
            ax.tick_params(axis="y", labelleft=False)
            ax.yaxis.get_offset_text().set_visible(False)

    figure.tight_layout()

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
            serializers = SERIALIZERS
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


def write_report(
    payload: dict, results: dict, output_dir: str, plot_paths: list[tuple[str, str]]
):
    metadata = payload["metadata"]
    report_path = os.path.join(output_dir, "README.md")
    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("# Fory Dart Benchmark\n\n")
        handle.write(
            "This benchmark compares serialization and deserialization throughput for "
            "Apache Fory, Protocol Buffers, and JSON in Dart.\n\n"
        )
        handle.write("## Hardware and Runtime Info\n\n")
        handle.write("| Key | Value |\n")
        handle.write("| --- | --- |\n")
        for key, value in system_info(metadata):
            handle.write(f"| {key} | {value} |\n")

        handle.write("\n## Throughput Results\n\n")
        handle.write("![Throughput](throughput.png)\n\n")
        handle.write(
            "| Datatype | Operation | Fory TPS | Protobuf TPS | JSON TPS | Fastest |\n"
        )
        handle.write("| --- | --- | ---: | ---: | ---: | --- |\n")
        for data_type in DATA_TYPES:
            operations = results.get(data_type, {})
            if not operations:
                continue
            for operation in ["serialize", "deserialize"]:
                values = {
                    serializer: operations.get(operation, {}).get(serializer, 0.0)
                    for serializer in SERIALIZERS
                }
                handle.write(
                    f"| {DISPLAY_NAMES[data_type]} | {operation.capitalize()} | "
                    + " | ".join(
                        format_int(values[serializer]) for serializer in SERIALIZERS
                    )
                    + f" | {fastest_entry(values)} |\n"
                )

        handle.write("\n## Serialized Size (bytes)\n\n")
        handle.write("| Datatype | Fory | Protobuf | JSON |\n")
        handle.write("| --- | ---: | ---: | ---: |\n")
        for data_type in DATA_TYPES:
            sizes = payload["sizes"].get(data_type)
            if sizes is None:
                continue
            handle.write(
                f"| {DISPLAY_NAMES[data_type]} | {sizes['fory']} | {sizes['protobuf']} | {sizes['json']} |\n"
            )

        if plot_paths:
            handle.write("\n## Per-workload Plots\n\n")
            for display_name, path in plot_paths:
                handle.write(f"### {display_name}\n\n")
                handle.write(f"![{display_name}]({os.path.basename(path)})\n\n")

    prettier = shutil.which("prettier")
    if prettier is not None:
        subprocess.run([prettier, "--write", report_path], check=True)


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
