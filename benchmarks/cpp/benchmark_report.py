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

import json
import os
import platform
import argparse
import sys
from pathlib import Path
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter
from collections import defaultdict
from datetime import datetime

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_style import (  # noqa: E402
    BAR_EDGE_COLOR,
    GROUP_BAR_WIDTH,
    GROUP_X,
    apply_benchmark_style,
    add_compact_legend,
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

# === Colors and serializer order ===
COLORS = {
    "fory": "#FF6f01",  # Orange
    "protobuf": "#55BCC2",  # Teal
    "msgpack": (0.55, 0.40, 0.45),
}
SERIALIZER_ORDER = ["fory", "protobuf", "msgpack"]
SERIALIZER_LABELS = {
    "fory": "fory",
    "protobuf": "protobuf",
    "msgpack": "msgpack",
}
DATATYPE_ORDER = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]
DATATYPE_ORDER_INDEX = {
    datatype: index for index, datatype in enumerate(DATATYPE_ORDER)
}

# === Parse arguments ===
parser = argparse.ArgumentParser(
    description="Plot Google Benchmark stats and generate Markdown report for C++ benchmarks"
)
parser.add_argument(
    "--json-file", default="benchmark_results.json", help="Benchmark JSON output file"
)
parser.add_argument(
    "--output-dir", default="", help="Output directory for plots and report"
)
parser.add_argument(
    "--plot-prefix", default="", help="Image path prefix in Markdown report"
)
args = parser.parse_args()

# === Determine output directory ===
if args.output_dir.strip():
    output_dir = args.output_dir
else:
    output_dir = datetime.now().strftime("%Y_%m_%d_%H_%M_%S")

os.makedirs(output_dir, exist_ok=True)


# === Get system info ===
def get_system_info():
    try:
        info = {
            "OS": f"{platform.system()} {platform.release()}",
            "Machine": platform.machine(),
            "Processor": platform.processor() or "Unknown",
        }
        if HAS_PSUTIL:
            info["CPU Cores (Physical)"] = psutil.cpu_count(logical=False)
            info["CPU Cores (Logical)"] = psutil.cpu_count(logical=True)
            info["Total RAM (GB)"] = round(psutil.virtual_memory().total / (1024**3), 2)
    except Exception as e:
        info = {"Error gathering system info": str(e)}
    return info


# === Parse benchmark name ===
def parse_benchmark_name(name):
    """
    Parse benchmark names like:
    - BM_Fory_NumericStruct_Serialize
    - BM_Protobuf_Sample_Deserialize
    - BM_Msgpack_MediaContent_Deserialize
    Returns: (library, datatype, operation)
    """
    # Remove BM_ prefix
    if name.startswith("BM_"):
        name = name[3:]

    parts = name.split("_")
    if len(parts) >= 3:
        library = parts[0].lower()
        datatype = parts[1].lower()
        if datatype == "numericstruct":
            datatype = "struct"
        elif datatype == "numericstructlist":
            datatype = "structlist"
        operation = parts[2].lower()
        return library, datatype, operation
    return None, None, None


def format_datatype_label(datatype):
    if not datatype:
        return ""
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
    if not datatype:
        return ""
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


def ordered_datatypes(datatypes):
    return sorted(
        datatypes,
        key=lambda datatype: (
            DATATYPE_ORDER_INDEX.get(datatype, len(DATATYPE_ORDER)),
            datatype,
        ),
    )


# === Read and parse benchmark JSON ===
def load_benchmark_data(json_file):
    with open(json_file, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data


# === Data storage ===
# Structure: data[datatype][operation][library] = time_ns
data = defaultdict(lambda: defaultdict(dict))
sizes = {}  # Store serialized sizes

# === Load and process data ===
benchmark_data = load_benchmark_data(args.json_file)

# Extract context info
context = benchmark_data.get("context", {})

# Process benchmarks
for bench in benchmark_data.get("benchmarks", []):
    name = bench.get("name", "")
    # Skip aggregate results and size benchmarks
    if "/iterations:" in name or "PrintSerializedSizes" in name:
        # Extract sizes from PrintSerializedSizes
        if "PrintSerializedSizes" in name:
            for key, value in bench.items():
                if key.endswith("_size"):
                    sizes[key] = int(value)
        continue

    library, datatype, operation = parse_benchmark_name(name)
    if library and datatype and operation:
        # Get time in nanoseconds
        time_ns = bench.get("real_time", bench.get("cpu_time", 0))
        time_unit = bench.get("time_unit", "ns")

        # Convert to nanoseconds if needed
        if time_unit == "us":
            time_ns *= 1000
        elif time_unit == "ms":
            time_ns *= 1000000
        elif time_unit == "s":
            time_ns *= 1000000000

        data[datatype][operation][library] = time_ns

# === System info ===
system_info = get_system_info()

# Add context info from benchmark
if context:
    if "date" in context:
        system_info["Benchmark Date"] = context["date"]
    if "num_cpus" in context:
        system_info["CPU Cores (from benchmark)"] = context["num_cpus"]


# === Plotting ===
def format_tps_label(tps):
    return format_throughput_label(tps)


def format_tps_tick(tps, _position):
    return format_throughput_tick(tps, _position)


def plot_datatype(ax, datatype, operation):
    """Plot a single datatype/operation throughput comparison."""
    if datatype not in data or operation not in data[datatype]:
        ax.set_title(f"{datatype} {operation} - No Data")
        ax.axis("off")
        return

    libs = set(data[datatype][operation].keys())
    lib_order = [lib for lib in SERIALIZER_ORDER if lib in libs]

    times = [data[datatype][operation].get(lib, 0) for lib in lib_order]
    throughput = [1e9 / t if t > 0 else 0 for t in times]
    colors = [COLORS.get(lib, "#888888") for lib in lib_order]

    x = np.arange(len(lib_order))
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
    ax.set_xticklabels([SERIALIZER_LABELS.get(lib, lib) for lib in lib_order])
    ax.set_ylabel("Throughput (ops/sec)")
    style_throughput_axis(ax)
    ax.ticklabel_format(style="scientific", axis="y", scilimits=(0, 0))

    # Add value labels on bars
    for bar, tps_val in zip(bars, throughput):
        height = bar.get_height()
        ax.annotate(
            format_tps_label(tps_val),
            xy=(bar.get_x() + bar.get_width() / 2, height),
            xytext=(0, 3),
            textcoords="offset points",
            ha="center",
            va="bottom",
            fontsize=9,
        )


# === Create plots ===
plot_images = []
datatypes = ordered_datatypes(data.keys())
operations = ["serialize", "deserialize"]

for datatype in datatypes:
    fig, axes = plt.subplots(1, 2, figsize=(11.5, 4.6))
    for i, op in enumerate(operations):
        plot_datatype(axes[i], datatype, op)
    fig.suptitle(f"{format_datatype_table_label(datatype)} Throughput", fontsize=13)
    fig.tight_layout(rect=[0, 0, 1, 0.93], w_pad=1.3)
    plot_path = os.path.join(output_dir, f"{datatype}.png")
    save_benchmark_figure(fig, plot_path)
    plot_images.append((datatype, plot_path))
    plt.close()

# === Create combined TPS comparison plot ===
non_list_datatypes = [dt for dt in datatypes if not dt.endswith("list")]
list_datatypes = [dt for dt in datatypes if dt.endswith("list")]


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
        tps = [1e9 / t if t > 0 else 0 for t in times]
        offset = serializer_offset(idx, len(available_libs))
        ax.bar(
            x + offset,
            tps,
            GROUP_BAR_WIDTH,
            label=SERIALIZER_LABELS.get(lib, lib),
            color=COLORS.get(lib, "#888888"),
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
fig.suptitle("C++ Serialization Throughput", fontsize=15, fontweight="normal", y=0.955)
fig.tight_layout(rect=[0.02, 0.02, 0.995, 0.965], w_pad=1.2, h_pad=1.25)
combined_plot_path = os.path.join(output_dir, "throughput.png")
save_benchmark_figure(fig, combined_plot_path)
plot_images.append(("throughput", combined_plot_path))
plt.close()

# === Markdown report ===
md_report = [
    "# C++ Benchmark Performance Report\n\n",
    f"_Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n",
    "## How to Generate This Report\n\n",
    "```bash\n",
    "cd benchmarks/cpp/build\n",
    "./fory_benchmark --benchmark_format=json --benchmark_out=benchmark_results.json\n",
    "cd ..\n",
    "python benchmark_report.py --json-file build/benchmark_results.json --output-dir report\n",
    "```\n\n",
    "## Hardware & OS Info\n\n",
    "| Key | Value |\n",
    "|-----|-------|\n",
]
for k, v in system_info.items():
    md_report.append(f"| {k} | {v} |\n")

# Plots section
md_report.append("\n## Benchmark Plots\n")
md_report.append("\nAll class-level plots below show throughput (ops/sec).\n")
plot_images_sorted = sorted(
    plot_images,
    key=lambda item: (
        0 if item[0] == "throughput" else 1,
        DATATYPE_ORDER_INDEX.get(item[0], len(DATATYPE_ORDER)),
        item[0],
    ),
)
for datatype, img in plot_images_sorted:
    img_filename = os.path.basename(img)
    img_path_report = args.plot_prefix + img_filename
    plot_title = (
        "Throughput"
        if datatype == "throughput"
        else format_datatype_table_label(datatype)
    )
    md_report.append(f"\n### {plot_title}\n\n")
    md_report.append(f"![{plot_title}]({img_path_report})\n")

# Results table
md_report.append("\n## Benchmark Results\n\n")
md_report.append("### Timing Results (nanoseconds)\n\n")
md_report.append(
    "| Datatype | Operation | fory (ns) | protobuf (ns) | msgpack (ns) | Fastest |\n"
)
md_report.append(
    "|----------|-----------|-----------|---------------|--------------|---------|\n"
)

for datatype in datatypes:
    for op in operations:
        times = {lib: data[datatype][op].get(lib, 0) for lib in SERIALIZER_ORDER}
        positive_times = {lib: t for lib, t in times.items() if t > 0}
        fastest_str = "N/A"
        if positive_times:
            fastest_lib = min(positive_times, key=positive_times.get)
            fastest_str = SERIALIZER_LABELS.get(fastest_lib, fastest_lib)
        md_report.append(
            "| "
            + f"{format_datatype_table_label(datatype)} | {op.capitalize()} | "
            + " | ".join(
                f"{times[lib]:.1f}" if times[lib] > 0 else "N/A"
                for lib in SERIALIZER_ORDER
            )
            + f" | {fastest_str} |\n"
        )

# Throughput table
md_report.append("\n### Throughput Results (ops/sec)\n\n")
md_report.append(
    "| Datatype | Operation | fory TPS | protobuf TPS | msgpack TPS | Fastest |\n"
)
md_report.append(
    "|----------|-----------|----------|--------------|-------------|---------|\n"
)

for datatype in datatypes:
    for op in operations:
        times = {lib: data[datatype][op].get(lib, 0) for lib in SERIALIZER_ORDER}
        tps = {lib: (1e9 / t if t > 0 else 0) for lib, t in times.items()}
        positive_tps = {lib: v for lib, v in tps.items() if v > 0}
        fastest_str = "N/A"
        if positive_tps:
            fastest_lib = max(positive_tps, key=positive_tps.get)
            fastest_str = SERIALIZER_LABELS.get(fastest_lib, fastest_lib)
        md_report.append(
            "| "
            + f"{format_datatype_table_label(datatype)} | {op.capitalize()} | "
            + " | ".join(
                f"{tps[lib]:,.0f}" if tps[lib] > 0 else "N/A"
                for lib in SERIALIZER_ORDER
            )
            + f" | {fastest_str} |\n"
        )

# Serialized sizes
if sizes:
    md_report.append("\n### Serialized Data Sizes (bytes)\n\n")
    md_report.append("| Datatype | fory | protobuf | msgpack |\n")
    md_report.append("|----------|------|----------|---------|\n")
    size_prefix = {
        "fory": "fory",
        "protobuf": "protobuf",
        "msgpack": "msgpack",
    }
    size_datatypes = [
        ("struct", "NumericStruct"),
        ("sample", "Sample"),
        ("media", "MediaContent"),
        ("struct_list", "NumericStructList"),
        ("sample_list", "SampleList"),
        ("media_list", "MediaContentList"),
    ]
    for datatype_key, datatype_label in size_datatypes:
        row_values = []
        has_value = False
        for lib in SERIALIZER_ORDER:
            key = f"{size_prefix[lib]}_{datatype_key}_size"
            value = sizes.get(key)
            if value is None and lib == "protobuf":
                value = sizes.get(f"proto_{datatype_key}_size")
            if value is None:
                row_values.append("N/A")
            else:
                row_values.append(str(value))
                has_value = True
        if has_value:
            md_report.append(f"| {datatype_label} | " + " | ".join(row_values) + " |\n")

# Save Markdown
report_path = os.path.join(output_dir, "README.md")
with open(report_path, "w", encoding="utf-8") as f:
    f.writelines(md_report)

format_markdown_with_prettier(report_path)

print(f"✅ Plots saved in: {output_dir}")
print(f"📄 Markdown report generated at: {report_path}")
