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

"""Generate a Java xlang throughput plot from JMH JSON output."""

from __future__ import annotations

import argparse
import json
import os
import re
from collections import defaultdict
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

SERIALIZER_ORDER = ["fory", "protobuf", "flatbuffer"]
COLORS = {
    "fory": "#FF6f01",
    "protobuf": "#55BCC2",
    "flatbuffer": (0.55, 0.40, 0.45),
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
BENCHMARK_PATTERN = re.compile(
    r"(?:^|[.])BM_(?P<serializer>Fory|Protobuf|Flatbuffer)_"
    r"(?P<datatype>Struct|Sample|MediaContent|StructList|SampleList|MediaContentList)_"
    r"(?P<operation>Serialize|Deserialize)$"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate throughput.png for Java xlang benchmark results"
    )
    parser.add_argument(
        "--json-file",
        default="results/benchmark_results.json",
        help="JMH JSON output file",
    )
    parser.add_argument(
        "--output-dir",
        default="results",
        help="Directory for throughput.png",
    )
    return parser.parse_args()


def load_json(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def datatype_key(name: str) -> str:
    return name.lower()


def datatype_title(datatype: str) -> str:
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


def score_to_ops_per_sec(score: float, unit: str) -> float:
    if unit == "ops/s":
        return score
    if unit == "ops/ms":
        return score * 1_000
    if unit == "ops/us":
        return score * 1_000_000
    if unit == "ops/ns":
        return score * 1_000_000_000
    return score


def collect_results(payload: Any) -> dict:
    results: dict = defaultdict(lambda: defaultdict(dict))
    benchmarks = payload if isinstance(payload, list) else payload.get("benchmarks", [])
    for bench in benchmarks:
        benchmark_name = bench.get("benchmark") or bench.get("name", "")
        match = BENCHMARK_PATTERN.search(benchmark_name)
        if match is None:
            continue
        metric = bench.get("primaryMetric", {})
        score = float(metric.get("score", bench.get("opsPerSec", 0.0)))
        unit = metric.get("scoreUnit", "ops/s")
        serializer = match.group("serializer").lower()
        datatype = datatype_key(match.group("datatype"))
        operation = match.group("operation").lower()
        results[datatype][operation][serializer] = score_to_ops_per_sec(score, unit)
    return results


def format_tps(value: float, _position) -> str:
    if value >= 1e9:
        return f"{value / 1e9:.2f}G"
    if value >= 1e6:
        return f"{value / 1e6:.2f}M"
    if value >= 1e3:
        return f"{value / 1e3:.2f}K"
    return f"{value:.0f}"


def plot_group(ax, results: dict, datatype: str) -> None:
    if datatype not in results:
        ax.set_title(f"{datatype_title(datatype)}\nNo Data")
        ax.axis("off")
        return

    serializers = [
        serializer
        for serializer in SERIALIZER_ORDER
        if any(
            results.get(datatype, {}).get(operation, {}).get(serializer, 0.0) > 0
            for operation in OPERATIONS
        )
    ]
    if not serializers:
        ax.set_title(f"{datatype_title(datatype)}\nNo Data")
        ax.axis("off")
        return

    x = np.arange(len(OPERATIONS))
    width = 0.8 / len(serializers)
    for index, serializer in enumerate(serializers):
        values = [
            results.get(datatype, {}).get(operation, {}).get(serializer, 0.0)
            for operation in OPERATIONS
        ]
        offset = (index - (len(serializers) - 1) / 2) * width
        ax.bar(
            x + offset,
            values,
            width=width,
            label=serializer,
            color=COLORS.get(serializer, "#888888"),
        )

    ax.set_title(datatype_title(datatype), fontweight="normal")
    ax.set_xticks(x)
    ax.set_xticklabels(["Serialize", "Deserialize"])
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.yaxis.set_major_formatter(FuncFormatter(format_tps))
    ax.legend(loc="upper right", fontsize=8, framealpha=0.9)


def render_plot(results: dict, output_dir: str) -> str:
    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    fig.suptitle(
        "Java Xlang Serialization Throughput", fontsize=14, fontweight="normal"
    )

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


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    results = collect_results(load_json(args.json_file))
    output_path = render_plot(results, str(output_dir))
    print(f"Generated {output_path}")


if __name__ == "__main__":
    main()
