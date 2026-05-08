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
import os
import platform
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_style import (  # noqa: E402
    BAR_EDGE_COLOR,
    GROUP_BAR_WIDTH,
    GROUP_X,
    add_compact_legend,
    apply_benchmark_style,
    format_markdown_with_prettier,
    format_throughput_label,
    format_throughput_tick,
    save_benchmark_figure,
    serializer_offset,
    set_grouped_operation_axis,
    style_throughput_axis,
)

apply_benchmark_style(plt)

try:
    import psutil

    HAS_PSUTIL = True
except ImportError:
    HAS_PSUTIL = False

COLORS = {
    "fory": "#FF6F01",
    "protobuf": "#55BCC2",
    "json": (0.55, 0.40, 0.45),
}
SERIALIZER_ORDER = ["fory", "protobuf", "json"]
SERIALIZER_LABELS = {
    "fory": "fory",
    "protobuf": "protobuf",
    "json": "json",
}
DATATYPE_ORDER = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]

parser = argparse.ArgumentParser(
    description="Generate plots and Markdown report for JavaScript benchmark results"
)
parser.add_argument(
    "--json-file", default="benchmark_results.json", help="Benchmark JSON output file"
)
parser.add_argument(
    "--output-dir",
    default="",
    help="Output directory for plots and report",
)
parser.add_argument(
    "--plot-prefix", default="", help="Image path prefix in Markdown report"
)
args = parser.parse_args()

output_dir = args.output_dir.strip() or datetime.now().strftime("%Y_%m_%d_%H_%M_%S")
os.makedirs(output_dir, exist_ok=True)


def get_system_info():
    info = {
        "OS": f"{platform.system()} {platform.release()}",
        "Machine": platform.machine(),
        "Processor": platform.processor() or "Unknown",
    }
    if HAS_PSUTIL:
        info["CPU Cores (Physical)"] = psutil.cpu_count(logical=False)
        info["CPU Cores (Logical)"] = psutil.cpu_count(logical=True)
        info["Total RAM (GB)"] = round(psutil.virtual_memory().total / (1024**3), 2)
    return info


def parse_benchmark_name(name):
    if name.startswith("BM_"):
        name = name[3:]
    parts = name.split("_")
    if len(parts) >= 3:
        datatype = parts[1].lower()
        if datatype == "numericstruct":
            datatype = "struct"
        elif datatype == "numericstructlist":
            datatype = "structlist"
        return parts[0].lower(), datatype, parts[2].lower()
    return None, None, None


def format_datatype_label(datatype):
    if datatype == "struct":
        return "NumericStruct"
    if datatype == "structlist":
        return "NumericStruct\nList"
    if datatype.endswith("list"):
        base = datatype[: -len("list")]
        if base == "mediacontent":
            return "MediaContent\nList"
        return f"{base.capitalize()}\nList"
    if datatype == "mediacontent":
        return "MediaContent"
    return datatype.capitalize()


def format_datatype_table_label(datatype):
    if datatype == "struct":
        return "NumericStruct"
    if datatype == "structlist":
        return "NumericStructList"
    if datatype.endswith("list"):
        base = datatype[: -len("list")]
        if base == "mediacontent":
            return "MediaContentList"
        return f"{base.capitalize()}List"
    if datatype == "mediacontent":
        return "MediaContent"
    return datatype.capitalize()


with open(args.json_file, "r", encoding="utf-8") as handle:
    benchmark_data = json.load(handle)

data = defaultdict(lambda: defaultdict(dict))
sizes = {}

for bench in benchmark_data.get("benchmarks", []):
    name = bench.get("name", "")
    if "PrintSerializedSizes" in name:
        for key, value in bench.items():
            if key.endswith("_size"):
                sizes[key] = int(value)
        continue
    serializer, datatype, operation = parse_benchmark_name(name)
    if serializer and datatype and operation:
        time_ns = bench.get("real_time", bench.get("cpu_time", 0))
        data[datatype][operation][serializer] = time_ns

system_info = get_system_info()
context = benchmark_data.get("context", {})
if context.get("date"):
    system_info["Benchmark Date"] = context["date"]
if context.get("num_cpus"):
    system_info["CPU Cores (from benchmark)"] = context["num_cpus"]
if context.get("node_version"):
    system_info["Node.js"] = context["node_version"]
if context.get("v8_version"):
    system_info["V8"] = context["v8_version"]


def format_tps_label(tps):
    return format_throughput_label(tps)


def format_tps_tick(tps, _position):
    return format_throughput_tick(tps, _position)


def plot_datatype(ax, datatype, operation):
    if datatype not in data or operation not in data[datatype]:
        ax.set_title(f"{datatype} {operation} - No Data")
        ax.axis("off")
        return

    libs = [lib for lib in SERIALIZER_ORDER if lib in data[datatype][operation]]
    if not libs:
        ax.set_title(f"{datatype} {operation} - No Data")
        ax.axis("off")
        return

    times = [data[datatype][operation].get(lib, 0) for lib in libs]
    throughput = [1e9 / value if value > 0 else 0 for value in times]
    colors = [COLORS[lib] for lib in libs]

    x = np.arange(len(libs))
    bars = ax.bar(
        x,
        throughput,
        color=colors,
        edgecolor=BAR_EDGE_COLOR,
        linewidth=0.8,
        width=0.46,
    )
    ax.set_title(f"{operation.capitalize()} Throughput (higher is better)", pad=8)
    ax.set_xticks(x)
    ax.set_xticklabels([SERIALIZER_LABELS[lib] for lib in libs])
    ax.set_ylabel("Throughput (ops/sec)")
    style_throughput_axis(ax)
    ax.ticklabel_format(style="scientific", axis="y", scilimits=(0, 0))

    for bar, tps in zip(bars, throughput):
        ax.annotate(
            format_tps_label(tps),
            xy=(bar.get_x() + bar.get_width() / 2, bar.get_height()),
            xytext=(0, 3),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=9,
        )


plot_images = []
datatypes = [datatype for datatype in DATATYPE_ORDER if datatype in data]
operations = ["serialize", "deserialize"]

for datatype in datatypes:
    fig, axes = plt.subplots(1, 2, figsize=(11.5, 4.6))
    for i, operation in enumerate(operations):
        plot_datatype(axes[i], datatype, operation)
    fig.suptitle(
        f"{format_datatype_table_label(datatype)} Throughput",
        fontsize=13,
        fontweight="normal",
    )
    fig.tight_layout(rect=[0, 0, 1, 0.93], w_pad=1.3)
    plot_path = os.path.join(output_dir, f"{datatype}.png")
    save_benchmark_figure(fig, plot_path)
    plot_images.append((datatype, plot_path))
    plt.close()


def plot_throughput_grid_subplot(ax, datatype):
    if datatype not in data:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    available_libs = [
        lib
        for lib in SERIALIZER_ORDER
        if any(
            data[datatype][operation].get(lib, 0) > 0
            for operation in ["serialize", "deserialize"]
        )
    ]
    if not available_libs:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    operations = ["serialize", "deserialize"]
    x = GROUP_X
    for idx, lib in enumerate(available_libs):
        times = [data[datatype][operation].get(lib, 0) for operation in operations]
        throughput = [1e9 / value if value > 0 else 0 for value in times]
        offset = serializer_offset(idx, len(available_libs))
        ax.bar(
            x + offset,
            throughput,
            GROUP_BAR_WIDTH,
            label=SERIALIZER_LABELS[lib],
            color=COLORS[lib],
            edgecolor=BAR_EDGE_COLOR,
            linewidth=0.8,
        )

    max_tps = max(
        1e9 / data[datatype][operation][lib]
        for operation in operations
        for lib in available_libs
        if data[datatype][operation].get(lib, 0) > 0
    )
    ax.set_ylim(0, max_tps * 1.12)
    ax.set_title(format_datatype_table_label(datatype), pad=8)
    set_grouped_operation_axis(ax)
    style_throughput_axis(ax)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tps_tick))
    add_compact_legend(ax)


fig, axes = plt.subplots(2, 3, figsize=(16.5, 9.0))
for index, (ax, datatype) in enumerate(zip(axes.flat, DATATYPE_ORDER)):
    plot_throughput_grid_subplot(ax, datatype)
    if index % 3 == 0:
        ax.set_ylabel("Throughput (ops/sec)", labelpad=10)
fig.suptitle(
    "JavaScript Serialization Throughput", fontsize=15, fontweight="normal", y=0.955
)
fig.tight_layout(rect=[0.02, 0.02, 0.995, 0.965], w_pad=1.2, h_pad=1.25)
combined_plot_path = os.path.join(output_dir, "throughput.png")
save_benchmark_figure(fig, combined_plot_path)
plot_images.append(("throughput", combined_plot_path))
plt.close()

md_report = [
    "# JavaScript Benchmark Performance Report\n\n",
    f"_Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n",
    "## How to Generate This Report\n\n",
    "```bash\n",
    "cd benchmarks/javascript\n",
    "./run.sh\n",
    "```\n\n",
    "## Benchmark Semantics\n\n",
    "The timed serializer loops use serializer-native typed values. Fory receives "
    "the pre-normalized Fory value used by its schema, protobuf receives the "
    "prebuilt protobuf-shaped value, and JSON receives the benchmark JavaScript "
    "object. Protobuf timings do not include `toProto`, `fromProto`, "
    "`protobufjs.create`, or `toObject` conversion work.\n\n",
    "## Hardware & OS Info\n\n",
    "| Key | Value |\n",
    "|-----|-------|\n",
]

for key, value in system_info.items():
    md_report.append(f"| {key} | {value} |\n")

md_report.append("\n## Benchmark Plots\n")
md_report.append("\nAll class-level plots below show throughput (ops/sec).\n")
for datatype, image in sorted(
    plot_images, key=lambda item: (0 if item[0] == "throughput" else 1, item[0])
):
    image_name = os.path.basename(image)
    image_path = args.plot_prefix + image_name
    title = (
        "Throughput"
        if datatype == "throughput"
        else format_datatype_table_label(datatype)
    )
    md_report.append(f"\n### {title}\n\n")
    md_report.append(f"![{title}]({image_path})\n")

md_report.append("\n## Benchmark Results\n\n")
md_report.append("### Timing Results (nanoseconds)\n\n")
md_report.append(
    "| Datatype | Operation | fory (ns) | protobuf (ns) | json (ns) | Fastest |\n"
)
md_report.append(
    "|----------|-----------|-----------|---------------|-----------|---------|\n"
)

for datatype in datatypes:
    for operation in operations:
        times = {lib: data[datatype][operation].get(lib, 0) for lib in SERIALIZER_ORDER}
        valid = {lib: value for lib, value in times.items() if value > 0}
        fastest = min(valid, key=valid.get) if valid else None
        md_report.append(
            "| "
            + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
            + " | ".join(
                f"{times[lib]:.1f}" if times[lib] > 0 else "N/A"
                for lib in SERIALIZER_ORDER
            )
            + f" | {SERIALIZER_LABELS[fastest] if fastest else 'N/A'} |\n"
        )

md_report.append("\n### Throughput Results (ops/sec)\n\n")
md_report.append(
    "| Datatype | Operation | fory TPS | protobuf TPS | json TPS | Fastest |\n"
)
md_report.append(
    "|----------|-----------|----------|--------------|----------|---------|\n"
)

for datatype in datatypes:
    for operation in operations:
        times = {lib: data[datatype][operation].get(lib, 0) for lib in SERIALIZER_ORDER}
        tps = {lib: (1e9 / value if value > 0 else 0) for lib, value in times.items()}
        valid = {lib: value for lib, value in tps.items() if value > 0}
        fastest = max(valid, key=valid.get) if valid else None
        md_report.append(
            "| "
            + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
            + " | ".join(
                f"{tps[lib]:,.0f}" if tps[lib] > 0 else "N/A"
                for lib in SERIALIZER_ORDER
            )
            + f" | {SERIALIZER_LABELS[fastest] if fastest else 'N/A'} |\n"
        )

if sizes:
    md_report.append("\n### Serialized Data Sizes (bytes)\n\n")
    md_report.append("| Datatype | fory | protobuf | json |\n")
    md_report.append("|----------|------|----------|------|\n")
    size_datatypes = [
        ("struct", "NumericStruct"),
        ("sample", "Sample"),
        ("media", "MediaContent"),
        ("struct_list", "NumericStructList"),
        ("sample_list", "SampleList"),
        ("media_list", "MediaContentList"),
    ]
    for datatype_key, datatype_label in size_datatypes:
        row = []
        has_value = False
        for serializer in SERIALIZER_ORDER:
            value = sizes.get(f"{serializer}_{datatype_key}_size")
            if value is None:
                row.append("N/A")
            else:
                row.append(str(value))
                has_value = True
        if has_value:
            md_report.append(f"| {datatype_label} | " + " | ".join(row) + " |\n")

report_path = os.path.join(output_dir, "README.md")
with open(report_path, "w", encoding="utf-8") as handle:
    handle.writelines(md_report)

format_markdown_with_prettier(report_path)

print(f"Plots saved in: {output_dir}")
print(f"Markdown report generated at: {report_path}")
