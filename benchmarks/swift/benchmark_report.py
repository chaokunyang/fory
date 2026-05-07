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

"""Generate plots and a markdown report from Swift benchmark JSON output."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

SERIALIZER_ORDER = ["fory", "protobuf", "msgpack"]
COLORS = {
    "fory": "#FF6f01",
    "protobuf": "#55BCC2",
    "msgpack": "#8C6F6D",
}
DATATYPE_ORDER = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]
OPERATIONS = ["serialize", "deserialize"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate report for Swift benchmark results"
    )
    parser.add_argument(
        "--json-file",
        default="results/benchmark_results.json",
        help="Benchmark JSON output file",
    )
    parser.add_argument(
        "--output-dir",
        default="results",
        help="Directory for report output",
    )
    parser.add_argument(
        "--plot-prefix",
        default="",
        help="Prefix for image paths in markdown report",
    )
    return parser.parse_args()


def load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def normalize_datatype(datatype: str) -> str:
    key = datatype.lower()
    if key == "numericstruct":
        return "struct"
    if key == "numericstructlist":
        return "structlist"
    return key


def datatype_title(datatype: str) -> str:
    datatype = normalize_datatype(datatype)
    if datatype == "struct":
        return "NumericStruct"
    if datatype == "structlist":
        return "NumericStructList"
    if datatype == "mediacontent":
        return "MediaContent"
    if datatype == "mediacontentlist":
        return "MediaContentList"
    if datatype.endswith("list"):
        return f"{datatype[:-4].capitalize()}List"
    return datatype.capitalize()


def datatype_plot_label(datatype: str) -> str:
    datatype = normalize_datatype(datatype)
    if datatype == "struct":
        return "NumericStruct"
    if datatype == "structlist":
        return "NumericStruct\nList"
    if datatype == "mediacontent":
        return "MediaContent"
    if datatype == "mediacontentlist":
        return "MediaContent\nList"
    if datatype.endswith("list"):
        return f"{datatype[:-4].capitalize()}\nList"
    return datatype.capitalize()


def format_tps(value: float) -> str:
    return f"{value:,.0f}"


def format_tps_label(value: float) -> str:
    if value >= 1e9:
        return f"{value / 1e9:.2f}G"
    if value >= 1e6:
        return f"{value / 1e6:.2f}M"
    if value >= 1e3:
        return f"{value / 1e3:.2f}K"
    return f"{value:.0f}"


def format_tps_tick(value: float, _position) -> str:
    return format_tps_label(value)


def collect_results(payload: dict) -> dict:
    results: dict = defaultdict(lambda: defaultdict(dict))
    for bench in payload.get("benchmarks", []):
        serializer = bench.get("serializer", "")
        datatype = bench.get("dataType", "")
        operation = bench.get("operation", "")
        ops = float(bench.get("opsPerSec", 0.0))
        if serializer and datatype and operation:
            results[datatype][operation][serializer] = ops
    return results


def plot_group(ax, results: dict, datatype: str) -> None:
    if datatype not in results:
        ax.set_title(f"{datatype_title(datatype)}\nNo Data")
        ax.axis("off")
        return

    available_serializers = [
        serializer
        for serializer in SERIALIZER_ORDER
        if any(
            results.get(datatype, {}).get(operation, {}).get(serializer, 0.0) > 0
            for operation in OPERATIONS
        )
    ]
    if not available_serializers:
        ax.set_title(f"{datatype_title(datatype)}\nNo Data")
        ax.axis("off")
        return

    x = np.arange(len(OPERATIONS))
    width = 0.8 / len(available_serializers)
    for index, serializer in enumerate(available_serializers):
        values = [
            results.get(datatype, {}).get(operation, {}).get(serializer, 0.0)
            for operation in OPERATIONS
        ]
        offset = (index - (len(available_serializers) - 1) / 2) * width
        ax.bar(
            x + offset,
            values,
            width=width,
            label=serializer,
            color=COLORS.get(serializer, "#888888"),
        )

    ax.set_title(datatype_title(datatype))
    ax.set_xticks(x)
    ax.set_xticklabels(["Serialize", "Deserialize"])
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tps_tick))
    ax.legend(loc="upper right", fontsize=8, framealpha=0.9)


def render_plot(results: dict, output_dir: str) -> str:
    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    fig.suptitle("Swift Serialization Throughput", fontsize=14)

    for index, (ax, datatype) in enumerate(zip(axes.flat, DATATYPE_ORDER)):
        plot_group(ax, results, datatype)
        if index % 3 == 0:
            ax.set_ylabel("Throughput (ops/sec)")
        else:
            ax.tick_params(axis="y", labelleft=False)
            ax.yaxis.get_offset_text().set_visible(False)

    fig.tight_layout()
    output_path = os.path.join(output_dir, "throughput.png")
    plt.savefig(output_path, dpi=150)
    plt.close(fig)
    return output_path


def winner_cell(throughputs: dict) -> str:
    rows = [
        (serializer, value) for serializer, value in throughputs.items() if value > 0
    ]
    if not rows:
        return "-"
    rows.sort(key=lambda pair: pair[1], reverse=True)
    best_serializer, best_value = rows[0]
    if len(rows) == 1:
        return f"{best_serializer}"
    ratio = best_value / rows[1][1] if rows[1][1] > 0 else 0
    return f"{best_serializer} ({ratio:.2f}x)"


def write_report(
    payload: dict,
    results: dict,
    throughput_plot: str,
    output_dir: str,
    plot_prefix: str,
) -> str:
    context = payload.get("context", {})
    sizes = payload.get("serializedSizes", [])

    lines: list[str] = []
    lines.append("# Fory Swift Benchmark")
    lines.append("")
    lines.append(
        "This benchmark compares serialization and deserialization throughput for "
        "Apache Fory, Protocol Buffers, and MessagePack in Swift."
    )
    lines.append("")
    lines.append("## Hardware and Runtime Info")
    lines.append("")
    lines.append("| Key | Value |")
    lines.append("| --- | --- |")
    lines.append(f"| Timestamp | {context.get('timestamp', '-')} |")
    lines.append(f"| OS | {context.get('os', '-')} |")
    lines.append(f"| Host | {context.get('host', '-')} |")
    lines.append(f"| CPU Cores (Logical) | {context.get('cpuCoresLogical', '-')} |")
    memory = context.get("memoryGB")
    memory_str = f"{memory:.2f}" if isinstance(memory, (int, float)) else "-"
    lines.append(f"| Memory (GB) | {memory_str} |")
    lines.append(f"| Duration per case (s) | {context.get('durationSeconds', '-')} |")
    lines.append("")
    lines.append("## Throughput Results")
    lines.append("")
    plot_name = os.path.basename(throughput_plot)
    if plot_prefix:
        image_path = f"{plot_prefix.rstrip('/')}/{plot_name}"
    else:
        image_path = plot_name
    lines.append(f"![Throughput]({image_path})")
    lines.append("")
    lines.append(
        "| Datatype | Operation | Fory TPS | Protobuf TPS | Msgpack TPS | Fastest |"
    )
    lines.append("| --- | --- | ---: | ---: | ---: | --- |")

    for datatype in DATATYPE_ORDER:
        if datatype not in results:
            continue
        for operation in OPERATIONS:
            throughputs = results.get(datatype, {}).get(operation, {})
            fory = throughputs.get("fory", 0.0)
            protobuf = throughputs.get("protobuf", 0.0)
            msgpack = throughputs.get("msgpack", 0.0)
            lines.append(
                "| "
                + f"{datatype_title(datatype)} | {operation.capitalize()} | "
                + f"{format_tps(fory)} | {format_tps(protobuf)} | {format_tps(msgpack)} | "
                + f"{winner_cell(throughputs)} |"
            )

    lines.append("")
    lines.append("## Serialized Size (bytes)")
    lines.append("")
    lines.append("| Datatype | Fory | Protobuf | Msgpack |")
    lines.append("| --- | ---: | ---: | ---: |")
    sizes_by_datatype = {
        normalize_datatype(str(entry.get("dataType", ""))): entry for entry in sizes
    }
    for datatype in DATATYPE_ORDER:
        entry = sizes_by_datatype.get(datatype)
        if entry is None:
            continue
        datatype_label = datatype_title(datatype)
        lines.append(
            "| "
            + f"{datatype_label} | "
            + f"{entry.get('fory', '-')} | "
            + f"{entry.get('protobuf', '-')} | "
            + f"{entry.get('msgpack', '-')} |"
        )

    report_path = os.path.join(output_dir, "REPORT.md")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    prettier = shutil.which("prettier")
    if prettier is not None:
        subprocess.run([prettier, "--write", report_path], check=True)

    return report_path


def main() -> int:
    args = parse_args()
    Path(args.output_dir).mkdir(parents=True, exist_ok=True)

    payload = load_json(args.json_file)
    results = collect_results(payload)
    throughput_plot = render_plot(results, args.output_dir)
    report = write_report(
        payload, results, throughput_plot, args.output_dir, args.plot_prefix
    )

    print(f"Generated report: {report}")
    print(f"Generated plot: {throughput_plot}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
