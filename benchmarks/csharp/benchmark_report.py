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
import json
import os
import platform
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_style import (  # noqa: E402
    BAR_EDGE_COLOR,
    GROUP_BAR_WIDTH,
    GROUP_X,
    apply_benchmark_style,
    add_compact_legend,
    format_markdown_with_prettier,
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
    "fory": "#FF6f01",
    "protobuf": "#55BCC2",
    "msgpack": (0.55, 0.40, 0.45),
}
SERIALIZER_ORDER = ["fory", "protobuf", "msgpack"]
SERIALIZER_LABELS = {
    "fory": "fory",
    "protobuf": "protobuf",
    "msgpack": "msgpack",
}
PREFERRED_DATATYPE_ORDER = [
    "struct",
    "sample",
    "mediacontent",
    "structlist",
    "samplelist",
    "mediacontentlist",
]
PREFERRED_OPERATION_ORDER = ["serialize", "deserialize"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Plot C# benchmark stats and generate Markdown report"
    )
    parser.add_argument(
        "--json-file",
        default="benchmark_results.json",
        help="Benchmark JSON output file",
    )
    parser.add_argument(
        "--output-dir",
        default="",
        help="Output directory for plots and report",
    )
    parser.add_argument(
        "--plot-prefix",
        default="",
        help="Image path prefix in Markdown report",
    )
    return parser.parse_args()


def load_results(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def get_system_info(benchmark_data: dict) -> dict:
    info = {
        "OS": benchmark_data.get(
            "OsDescription", f"{platform.system()} {platform.release()}"
        ),
        "OS Architecture": benchmark_data.get("OsArchitecture", "Unknown"),
        "Machine": benchmark_data.get("ProcessArchitecture", platform.machine()),
        "Runtime Version": benchmark_data.get("RuntimeVersion", "Unknown"),
        "Benchmark Date (UTC)": benchmark_data.get("GeneratedAtUtc", "Unknown"),
        "Warmup Seconds": benchmark_data.get("WarmupSeconds", "Unknown"),
        "Duration Seconds": benchmark_data.get("DurationSeconds", "Unknown"),
    }
    processor_count = benchmark_data.get("ProcessorCount")
    if processor_count is not None:
        info["CPU Logical Cores (from benchmark)"] = processor_count

    if HAS_PSUTIL:
        info["CPU Cores (Physical)"] = psutil.cpu_count(logical=False)
        info["CPU Cores (Logical)"] = psutil.cpu_count(logical=True)
        info["Total RAM (GB)"] = round(psutil.virtual_memory().total / (1024**3), 2)
    return info


def format_datatype_label(datatype: str) -> str:
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


def format_datatype_table_label(datatype: str) -> str:
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


def format_tps_tick(tps: float, _position) -> str:
    return format_throughput_tick(tps, _position)


def preferred_ordered_values(values, preferred):
    return [item for item in preferred if item in values] + sorted(
        item for item in values if item not in preferred
    )


def process_benchmark_rows(rows):
    raw_timings = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    raw_throughputs = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    raw_sizes = defaultdict(lambda: defaultdict(list))

    for row in rows:
        serializer = str(row.get("Serializer", "")).lower()
        data_type = str(row.get("DataType", "")).lower()
        operation = str(row.get("Operation", "")).lower()
        if not serializer or not data_type or not operation:
            continue

        avg_ns_raw = row.get("AverageNanoseconds")
        ops_raw = row.get("OperationsPerSecond")
        avg_ns = float(avg_ns_raw) if avg_ns_raw is not None else 0.0
        ops = float(ops_raw) if ops_raw is not None else 0.0

        if avg_ns <= 0.0 and ops > 0.0:
            avg_ns = 1e9 / ops
        if ops <= 0.0 and avg_ns > 0.0:
            ops = 1e9 / avg_ns

        if avg_ns > 0.0:
            raw_timings[data_type][operation][serializer].append(avg_ns)
        if ops > 0.0:
            raw_throughputs[data_type][operation][serializer].append(ops)

        serialized_size = row.get("SerializedSize")
        if serialized_size is not None:
            raw_sizes[data_type][serializer].append(int(round(serialized_size)))

    timings = defaultdict(lambda: defaultdict(dict))
    throughputs = defaultdict(lambda: defaultdict(dict))
    sizes = defaultdict(dict)

    for data_type, op_values in raw_timings.items():
        for operation, serializer_values in op_values.items():
            for serializer, values in serializer_values.items():
                timings[data_type][operation][serializer] = sum(values) / len(values)

    for data_type, op_values in raw_throughputs.items():
        for operation, serializer_values in op_values.items():
            for serializer, values in serializer_values.items():
                throughputs[data_type][operation][serializer] = sum(values) / len(
                    values
                )

    for data_type, serializer_values in raw_sizes.items():
        for serializer, values in serializer_values.items():
            sizes[data_type][serializer] = sum(values) / len(values)

    return timings, throughputs, sizes


def build_coverage(rows):
    cases = set()
    serializers = set()
    datatypes = set()
    operations = set()
    for row in rows:
        serializer = str(row.get("Serializer", "")).lower()
        data_type = str(row.get("DataType", "")).lower()
        operation = str(row.get("Operation", "")).lower()
        if not serializer or not data_type or not operation:
            continue
        cases.add((serializer, data_type, operation))
        serializers.add(serializer)
        datatypes.add(data_type)
        operations.add(operation)

    expected_cases = (
        len(SERIALIZER_ORDER)
        * len(PREFERRED_DATATYPE_ORDER)
        * len(PREFERRED_OPERATION_ORDER)
    )
    return {
        "case_count": len(cases),
        "expected_case_count": expected_cases,
        "serializers": sorted(serializers),
        "datatypes": preferred_ordered_values(
            list(datatypes), PREFERRED_DATATYPE_ORDER
        ),
        "operations": preferred_ordered_values(
            list(operations), PREFERRED_OPERATION_ORDER
        ),
        "is_partial": len(cases) < expected_cases,
    }


def plot_throughput_grid_subplot(ax, throughputs, datatype):
    if datatype not in throughputs:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    available_libs = [
        lib
        for lib in SERIALIZER_ORDER
        if any(
            throughputs[datatype][operation].get(lib, 0) > 0
            for operation in PREFERRED_OPERATION_ORDER
        )
    ]
    if not available_libs:
        ax.set_title(f"{format_datatype_table_label(datatype)}\nNo Data")
        ax.axis("off")
        return

    x = GROUP_X
    for idx, lib in enumerate(available_libs):
        tps = [
            throughputs[datatype][operation].get(lib, 0)
            for operation in PREFERRED_OPERATION_ORDER
        ]
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
        throughputs[datatype][operation][lib]
        for operation in PREFERRED_OPERATION_ORDER
        for lib in available_libs
        if throughputs[datatype][operation].get(lib, 0) > 0
    )
    ax.set_ylim(0, max_tps * 1.12)
    ax.set_title(format_datatype_table_label(datatype), pad=8)
    set_grouped_operation_axis(ax)
    style_throughput_axis(ax)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tps_tick))
    add_compact_legend(ax)


def build_markdown(
    args,
    system_info,
    coverage,
    timings,
    throughputs,
    sizes,
    datatypes,
    operations,
):
    report_lines = [
        "# C# Benchmark Performance Report\n\n",
        f"_Generated on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n",
        "## How to Generate This Report\n\n",
        "```bash\n",
        "cd benchmarks/csharp\n",
        "dotnet run -c Release --project ./Fory.CSharpBenchmark.csproj -- --output build/benchmark_results.json\n",
        "python3 benchmark_report.py --json-file build/benchmark_results.json --output-dir report\n",
        "```\n\n",
        "## Benchmark Plot\n\n",
        "The plot shows throughput (ops/sec); higher is better.\n\n",
        f"![Throughput]({args.plot_prefix}throughput.png)\n\n",
        "## Hardware & OS Info\n\n",
        "| Key | Value |\n",
        "|-----|-------|\n",
    ]

    for key, value in system_info.items():
        report_lines.append(f"| {key} | {value} |\n")

    report_lines.append("\n## Benchmark Coverage\n\n")
    report_lines.append("| Key | Value |\n")
    report_lines.append("|-----|-------|\n")
    report_lines.append(
        f"| Cases in input JSON | {coverage['case_count']} / {coverage['expected_case_count']} |\n"
    )
    report_lines.append(
        f"| Serializers | {', '.join(coverage['serializers']) or 'N/A'} |\n"
    )
    report_lines.append(
        f"| Datatypes | {', '.join(coverage['datatypes']) or 'N/A'} |\n"
    )
    report_lines.append(
        f"| Operations | {', '.join(coverage['operations']) or 'N/A'} |\n"
    )
    if coverage["is_partial"]:
        report_lines.append(
            "\n> Warning: benchmark input is partial; plots/tables only show available cases.\n"
        )

    report_lines.append("\n## Benchmark Results\n\n")
    report_lines.append("### Timing Results (nanoseconds)\n\n")
    report_lines.append(
        "| Datatype | Operation | fory (ns) | protobuf (ns) | msgpack (ns) | Fastest |\n"
    )
    report_lines.append(
        "|----------|-----------|-----------|---------------|--------------|---------|\n"
    )

    for datatype in datatypes:
        for operation in operations:
            times = {
                lib: timings[datatype][operation].get(lib, 0)
                for lib in SERIALIZER_ORDER
            }
            positive_times = {lib: t for lib, t in times.items() if t > 0}
            fastest = "N/A"
            if positive_times:
                fastest_lib = min(positive_times, key=positive_times.get)
                fastest = SERIALIZER_LABELS.get(fastest_lib, fastest_lib)
            report_lines.append(
                "| "
                + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
                + " | ".join(
                    f"{times[lib]:.1f}" if times[lib] > 0 else "N/A"
                    for lib in SERIALIZER_ORDER
                )
                + f" | {fastest} |\n"
            )

    report_lines.append("\n### Throughput Results (ops/sec)\n\n")
    report_lines.append(
        "| Datatype | Operation | fory TPS | protobuf TPS | msgpack TPS | Fastest |\n"
    )
    report_lines.append(
        "|----------|-----------|----------|--------------|-------------|---------|\n"
    )

    for datatype in datatypes:
        for operation in operations:
            tps_values = {
                lib: throughputs[datatype][operation].get(lib, 0)
                for lib in SERIALIZER_ORDER
            }
            positive_tps = {lib: v for lib, v in tps_values.items() if v > 0}
            fastest = "N/A"
            if positive_tps:
                fastest_lib = max(positive_tps, key=positive_tps.get)
                fastest = SERIALIZER_LABELS.get(fastest_lib, fastest_lib)
            report_lines.append(
                "| "
                + f"{format_datatype_table_label(datatype)} | {operation.capitalize()} | "
                + " | ".join(
                    f"{tps_values[lib]:,.0f}" if tps_values[lib] > 0 else "N/A"
                    for lib in SERIALIZER_ORDER
                )
                + f" | {fastest} |\n"
            )

    if sizes:
        report_lines.append("\n### Serialized Data Sizes (bytes)\n\n")
        report_lines.append("| Datatype | fory | protobuf | msgpack |\n")
        report_lines.append("|----------|------|----------|---------|\n")
        for datatype in datatypes:
            row_values = []
            has_value = False
            for serializer in SERIALIZER_ORDER:
                size = sizes[datatype].get(serializer)
                if size is None:
                    row_values.append("N/A")
                else:
                    row_values.append(str(int(round(size))))
                    has_value = True
            if has_value:
                report_lines.append(
                    f"| {format_datatype_table_label(datatype)} | "
                    + " | ".join(row_values)
                    + " |\n"
                )

    return "".join(report_lines)


def main() -> None:
    args = parse_args()
    benchmark_data = load_results(args.json_file)

    if args.output_dir.strip():
        output_dir = args.output_dir
    else:
        output_dir = datetime.now().strftime("%Y_%m_%d_%H_%M_%S")
    os.makedirs(output_dir, exist_ok=True)

    rows = benchmark_data.get("Results", [])
    timings, throughputs, sizes = process_benchmark_rows(rows)
    coverage = build_coverage(rows)

    datatype_candidates = (
        set(timings.keys()) | set(throughputs.keys()) | set(sizes.keys())
    )
    datatypes = preferred_ordered_values(
        list(datatype_candidates), PREFERRED_DATATYPE_ORDER
    )
    operations_present = set()
    for datatype in datatypes:
        operations_present.update(timings[datatype].keys())
        operations_present.update(throughputs[datatype].keys())
    operations = preferred_ordered_values(
        list(operations_present), PREFERRED_OPERATION_ORDER
    )

    fig, axes = plt.subplots(2, 3, figsize=(16.5, 9.0))
    for index, (ax, datatype) in enumerate(zip(axes.flat, PREFERRED_DATATYPE_ORDER)):
        plot_throughput_grid_subplot(ax, throughputs, datatype)
        if index % 3 == 0:
            ax.set_ylabel("Throughput (ops/sec)", labelpad=10)
    fig.suptitle(
        "C# Serialization Throughput", fontsize=15, fontweight="normal", y=0.955
    )
    fig.tight_layout(rect=[0.02, 0.02, 0.995, 0.965], w_pad=1.2, h_pad=1.25)
    throughput_path = os.path.join(output_dir, "throughput.png")
    save_benchmark_figure(fig, throughput_path)
    plt.close()

    report = build_markdown(
        args=args,
        system_info=get_system_info(benchmark_data),
        coverage=coverage,
        timings=timings,
        throughputs=throughputs,
        sizes=sizes,
        datatypes=datatypes,
        operations=operations,
    )
    legacy_report_path = os.path.join(output_dir, "REPORT.md")
    if os.path.exists(legacy_report_path):
        os.remove(legacy_report_path)
    report_path = os.path.join(output_dir, "README.md")
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report)

    format_markdown_with_prettier(report_path)

    if coverage["is_partial"]:
        print(
            "Warning: partial benchmark input detected "
            f"({coverage['case_count']}/{coverage['expected_case_count']} cases)."
        )
    print(f"Plots saved in: {output_dir}")
    print(f"Markdown report generated at: {report_path}")


if __name__ == "__main__":
    main()
