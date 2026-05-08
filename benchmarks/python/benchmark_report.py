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

"""Generate plots and Markdown report from Python benchmark JSON results."""

from __future__ import annotations

import argparse
import json
import os
import platform
import shutil
import subprocess
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Dict

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter, MaxNLocator

try:
    import psutil

    HAS_PSUTIL = True
except ImportError:
    HAS_PSUTIL = False


COLORS = {
    "fory": "#FF6F01",
    "protobuf": "#55BCC2",
    "pickle": (0.55, 0.40, 0.45),
}
PLOT_RC_PARAMS = {
    "figure.facecolor": "white",
    "axes.facecolor": "white",
    "axes.titleweight": "normal",
    "axes.labelcolor": "#222222",
    "xtick.color": "#222222",
    "ytick.color": "#222222",
    "font.size": 10,
    "axes.titlesize": 11,
    "axes.labelsize": 10,
    "xtick.labelsize": 9,
    "ytick.labelsize": 9,
    "legend.fontsize": 8,
}
GRID_COLOR = "#D9DEE7"
SPINE_COLOR = "#8A939E"
BAR_EDGE_COLOR = "white"
SERIALIZER_ORDER = ["fory", "protobuf", "pickle"]
SERIALIZER_LABELS = {
    "fory": "fory",
    "protobuf": "protobuf",
    "pickle": "pickle",
}
DATATYPE_ORDER = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate markdown report and plots for Python benchmark suite"
    )
    parser.add_argument(
        "--json-file",
        default="results/benchmark_results.json",
        help="Benchmark JSON file produced by benchmark.py",
    )
    parser.add_argument(
        "--output-dir",
        default="results/report",
        help="Output directory for report and plots",
    )
    parser.add_argument(
        "--plot-prefix",
        default="",
        help="Optional image path prefix used in markdown",
    )
    return parser.parse_args()


def load_json(path: Path) -> Dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def get_system_info() -> Dict[str, str]:
    info = {
        "OS": f"{platform.system()} {platform.release()}",
        "Machine": platform.machine(),
        "Processor": platform.processor() or "Unknown",
        "Python": platform.python_version(),
    }
    if HAS_PSUTIL:
        info["CPU Cores (Physical)"] = str(psutil.cpu_count(logical=False))
        info["CPU Cores (Logical)"] = str(psutil.cpu_count(logical=True))
        info["Total RAM (GB)"] = str(
            round(psutil.virtual_memory().total / (1024**3), 2)
        )
    return info


def format_datatype_label(datatype: str) -> str:
    mapping = {
        "struct": "NumericStruct",
        "sample": "Sample",
        "mediacontent": "MediaContent",
        "structlist": "NumericStruct\nList",
        "samplelist": "Sample\nList",
        "mediacontentlist": "MediaContent\nList",
    }
    return mapping.get(datatype, datatype)


def format_datatype_table_label(datatype: str) -> str:
    mapping = {
        "struct": "NumericStruct",
        "sample": "Sample",
        "mediacontent": "MediaContent",
        "structlist": "NumericStructList",
        "samplelist": "SampleList",
        "mediacontentlist": "MediaContentList",
    }
    return mapping.get(datatype, datatype)


def format_tps_label(tps: float) -> str:
    if tps >= 1e9:
        return f"{tps / 1e9:.2f}G"
    if tps >= 1e6:
        return f"{tps / 1e6:.2f}M"
    if tps >= 1e3:
        return f"{tps / 1e3:.2f}K"
    return f"{tps:.0f}"


def format_tps_tick(tps: float, _position) -> str:
    return format_tps_label(tps)


def style_throughput_axis(ax):
    ax.set_axisbelow(True)
    ax.grid(True, axis="y", color=GRID_COLOR, linestyle="-", linewidth=0.7)
    ax.grid(False, axis="x")
    ax.yaxis.set_major_locator(MaxNLocator(nbins=5, min_n_ticks=3))
    ax.tick_params(axis="both", width=0.8, length=3)
    for spine in ax.spines.values():
        spine.set_color(SPINE_COLOR)
        spine.set_linewidth(0.8)


def build_benchmark_matrix(benchmarks):
    data = defaultdict(lambda: defaultdict(dict))
    for bench in benchmarks:
        datatype = bench["datatype"]
        operation = bench["operation"]
        serializer = bench["serializer"]
        data[datatype][operation][serializer] = bench["mean_ns"]
    return data


def plot_datatype(ax, data, datatype: str, operation: str):
    if datatype not in data or operation not in data[datatype]:
        ax.set_title(f"{format_datatype_table_label(datatype)} {operation}: no data")
        ax.axis("off")
        return

    libs = [
        lib
        for lib in SERIALIZER_ORDER
        if data[datatype][operation].get(lib, 0) and data[datatype][operation][lib] > 0
    ]
    if not libs:
        ax.set_title(f"{format_datatype_table_label(datatype)} {operation}: no data")
        ax.axis("off")
        return

    times = [data[datatype][operation][lib] for lib in libs]
    throughput = [1e9 / t if t > 0 else 0 for t in times]

    x = np.arange(len(libs))
    bars = ax.bar(
        x,
        throughput,
        color=[COLORS.get(lib, "#999999") for lib in libs],
        edgecolor=BAR_EDGE_COLOR,
        linewidth=0.8,
        width=0.46,
    )

    ax.set_xticks(x)
    ax.set_xticklabels([SERIALIZER_LABELS.get(lib, lib) for lib in libs])
    ax.set_ylabel("Throughput (ops/sec)")
    ax.set_title(f"{operation.capitalize()} Throughput (higher is better)", pad=8)
    style_throughput_axis(ax)
    ax.ticklabel_format(style="scientific", axis="y", scilimits=(0, 0))

    for bar, val in zip(bars, throughput):
        ax.annotate(
            format_tps_label(val),
            xy=(bar.get_x() + bar.get_width() / 2, bar.get_height()),
            xytext=(0, 3),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=9,
        )


def plot_throughput_grid_subplot(ax, data, datatype: str):
    if datatype not in data:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    available_libs = [
        lib
        for lib in SERIALIZER_ORDER
        if any(
            data.get(datatype, {}).get(operation, {}).get(lib, 0) > 0
            for operation in ["serialize", "deserialize"]
        )
    ]
    if not available_libs:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    operations = ["serialize", "deserialize"]
    x = np.arange(len(operations))
    bar_width = 0.135
    offset_step = 0.17
    for idx, lib in enumerate(available_libs):
        times = [
            data.get(datatype, {}).get(operation, {}).get(lib, 0)
            for operation in operations
        ]
        tps = [1e9 / val if val > 0 else 0 for val in times]
        offset = (idx - (len(available_libs) - 1) / 2) * offset_step
        ax.bar(
            x + offset,
            tps,
            bar_width,
            label=SERIALIZER_LABELS.get(lib, lib),
            color=COLORS.get(lib, "#999999"),
            edgecolor=BAR_EDGE_COLOR,
            linewidth=0.8,
        )

    max_tps = max(
        1e9 / data[datatype][operation][lib]
        for operation in operations
        for lib in available_libs
        if data.get(datatype, {}).get(operation, {}).get(lib, 0) > 0
    )
    ax.set_ylim(0, max_tps * 1.12)
    ax.set_title(format_datatype_table_label(datatype), pad=8)
    ax.set_xticks(x)
    ax.set_xticklabels(["Serialize", "Deserialize"])
    ax.set_xlim(-0.48, 1.48)
    style_throughput_axis(ax)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tps_tick))
    ax.legend(
        loc="upper right",
        frameon=True,
        framealpha=0.95,
        edgecolor="#D6DAE0",
        borderpad=0.3,
        labelspacing=0.3,
        handlelength=1.4,
        handletextpad=0.45,
    )


def generate_plots(data, output_dir: Path):
    with plt.rc_context(PLOT_RC_PARAMS):
        plot_images = []
        operations = ["serialize", "deserialize"]

        datatypes = [dt for dt in DATATYPE_ORDER if dt in data]
        for datatype in datatypes:
            fig, axes = plt.subplots(1, 2, figsize=(11.5, 4.6))
            for idx, operation in enumerate(operations):
                plot_datatype(axes[idx], data, datatype, operation)
            fig.suptitle(
                f"{format_datatype_table_label(datatype)} Throughput",
                fontsize=13,
                fontweight="normal",
            )
            fig.tight_layout(rect=[0, 0, 1, 0.93], w_pad=1.3)

            path = output_dir / f"{datatype}.png"
            plt.savefig(path, dpi=170, bbox_inches="tight", pad_inches=0.12)
            plt.close()
            plot_images.append((datatype, path))

        fig, axes = plt.subplots(2, 3, figsize=(16.5, 9.0))
        for index, (ax, datatype) in enumerate(zip(axes.flat, DATATYPE_ORDER)):
            plot_throughput_grid_subplot(ax, data, datatype)
            if index % 3 == 0:
                ax.set_ylabel("Throughput (ops/sec)", labelpad=10)
            else:
                ax.tick_params(axis="y", labelleft=False)
                ax.yaxis.get_offset_text().set_visible(False)

        fig.suptitle(
            "Python Serialization Throughput",
            fontsize=15,
            fontweight="normal",
            y=0.975,
        )
        fig.tight_layout(rect=[0.02, 0.02, 0.995, 0.945], w_pad=1.0, h_pad=1.25)
        throughput_path = output_dir / "throughput.png"
        plt.savefig(throughput_path, dpi=170, bbox_inches="tight", pad_inches=0.12)
        plt.close()
        plot_images.append(("throughput", throughput_path))

    return plot_images


def generate_markdown_report(
    raw, data, sizes, plot_images, output_dir: Path, plot_prefix: str
):
    context = raw.get("context", {})
    system_info = get_system_info()

    if context.get("python_implementation"):
        system_info["Python Implementation"] = context["python_implementation"]
    if context.get("platform"):
        system_info["Benchmark Platform"] = context["platform"]

    datatypes = [dt for dt in DATATYPE_ORDER if dt in data]
    operations = ["serialize", "deserialize"]

    md = [
        "# Python Benchmark Performance Report\n\n",
        f"_Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n",
        "## How to Generate This Report\n\n",
        "```bash\n",
        "cd benchmarks/python\n",
        "./run.sh\n",
        "```\n\n",
        "## Hardware & OS Info\n\n",
        "| Key | Value |\n",
        "|-----|-------|\n",
    ]

    for key, value in system_info.items():
        md.append(f"| {key} | {value} |\n")

    md.append("\n## Benchmark Configuration\n\n")
    md.append("| Key | Value |\n")
    md.append("|-----|-------|\n")
    for key in ["warmup", "iterations", "repeat", "number", "list_size"]:
        if key in context:
            md.append(f"| {key} | {context[key]} |\n")

    md.append("\n## Benchmark Plots\n")
    md.append("\nAll plots show throughput (ops/sec); higher is better.\n")

    plot_images_sorted = sorted(
        plot_images, key=lambda item: (0 if item[0] == "throughput" else 1, item[0])
    )
    for datatype, image_path in plot_images_sorted:
        image_name = os.path.basename(image_path)
        image_ref = f"{plot_prefix}{image_name}"
        plot_title = (
            "Throughput"
            if datatype == "throughput"
            else format_datatype_table_label(datatype)
        )
        md.append(f"\n### {plot_title}\n\n")
        md.append(f"![{plot_title}]({image_ref})\n")

    md.append("\n## Benchmark Results\n\n")
    md.append("### Timing Results (nanoseconds)\n\n")
    timing_headers = [
        f"{SERIALIZER_LABELS.get(lib, lib)} (ns)" for lib in SERIALIZER_ORDER
    ]
    md.append(
        "| Datatype | Operation | " + " | ".join(timing_headers) + " | Fastest |\n"
    )
    md.append(
        "|----------|-----------|"
        + "|".join("-" * (len(header) + 2) for header in timing_headers)
        + "|---------|\n"
    )

    for datatype in datatypes:
        for operation in operations:
            times = {
                lib: data.get(datatype, {}).get(operation, {}).get(lib, 0)
                for lib in SERIALIZER_ORDER
            }
            valid = {lib: val for lib, val in times.items() if val > 0}
            fastest = min(valid, key=valid.get) if valid else "N/A"
            md.append(
                "| "
                + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
                + " | ".join(
                    f"{times[lib]:.1f}" if times[lib] > 0 else "N/A"
                    for lib in SERIALIZER_ORDER
                )
                + f" | {SERIALIZER_LABELS.get(fastest, fastest)} |\n"
            )

    md.append("\n### Throughput Results (ops/sec)\n\n")
    throughput_headers = [
        f"{SERIALIZER_LABELS.get(lib, lib)} TPS" for lib in SERIALIZER_ORDER
    ]
    md.append(
        "| Datatype | Operation | " + " | ".join(throughput_headers) + " | Fastest |\n"
    )
    md.append(
        "|----------|-----------|"
        + "|".join("-" * (len(header) + 2) for header in throughput_headers)
        + "|---------|\n"
    )

    for datatype in datatypes:
        for operation in operations:
            times = {
                lib: data.get(datatype, {}).get(operation, {}).get(lib, 0)
                for lib in SERIALIZER_ORDER
            }
            tps = {lib: (1e9 / val if val > 0 else 0) for lib, val in times.items()}
            valid_tps = {lib: val for lib, val in tps.items() if val > 0}
            fastest = max(valid_tps, key=valid_tps.get) if valid_tps else "N/A"
            md.append(
                "| "
                + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
                + " | ".join(
                    f"{tps[lib]:,.0f}" if tps[lib] > 0 else "N/A"
                    for lib in SERIALIZER_ORDER
                )
                + f" | {SERIALIZER_LABELS.get(fastest, fastest)} |\n"
            )

    if sizes:
        md.append("\n### Serialized Data Sizes (bytes)\n\n")
        size_headers = [SERIALIZER_LABELS.get(lib, lib) for lib in SERIALIZER_ORDER]
        md.append("| Datatype | " + " | ".join(size_headers) + " |\n")
        md.append(
            "|----------|"
            + "|".join("-" * (len(header) + 2) for header in size_headers)
            + "|\n"
        )

        for datatype in datatypes:
            datatype_sizes = sizes.get(datatype, {})
            row = []
            for lib in SERIALIZER_ORDER:
                value = datatype_sizes.get(lib, -1)
                row.append(str(value) if value is not None and value >= 0 else "N/A")
            md.append(
                f"| {format_datatype_table_label(datatype)} | "
                + " | ".join(row)
                + " |\n"
            )

    report_path = output_dir / "README.md"
    report_path.write_text("".join(md), encoding="utf-8")

    prettier = shutil.which("prettier")
    if prettier is not None:
        subprocess.run([prettier, "--write", str(report_path)], check=True)

    return report_path


def main() -> int:
    args = parse_args()

    json_file = Path(args.json_file)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    raw = load_json(json_file)
    benchmarks = raw.get("benchmarks", [])
    sizes = raw.get("sizes", {})

    data = build_benchmark_matrix(benchmarks)
    plot_images = generate_plots(data, output_dir)

    report_path = generate_markdown_report(
        raw,
        data,
        sizes,
        plot_images,
        output_dir,
        args.plot_prefix,
    )

    print(f"Plots saved in: {output_dir}")
    print(f"Markdown report generated at: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
