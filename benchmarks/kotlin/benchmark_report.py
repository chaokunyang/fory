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

"""Aggregate paired Kotlin JSON JMH samples and generate the published report."""

from __future__ import annotations

import argparse
import csv
import math
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from plot_style import (  # noqa: E402
    BAR_EDGE_COLOR,
    apply_benchmark_style,
    format_markdown_with_prettier,
    format_throughput_label,
    format_throughput_tick,
    save_benchmark_figure,
    style_throughput_axis,
)

apply_benchmark_style(plt)

LIBRARIES = ("fory", "kotlinx", "moshi", "jackson")
COMPARATORS = ("kotlinx", "moshi", "jackson")
OPERATIONS = (
    "string_serialization",
    "utf8_bytes_serialization",
    "string_deserialization",
    "utf8_bytes_deserialization",
)
LABELS = {
    "fory": "fory-json-kotlin",
    "kotlinx": "kotlinx.serialization",
    "moshi": "Moshi",
    "jackson": "Jackson Kotlin",
}
COLORS = {
    "fory": "#FF6F01",
    "kotlinx": "#4C78A8",
    "moshi": "#55BCC2",
    "jackson": "#8C6BB1",
}
CHART_NAMES = {operation: f"{operation}_throughput.png" for operation in OPERATIONS}
SETTINGS_FIELDS = (
    "jdk_version",
    "jmh_version",
    "kotlin_version",
    "forks",
    "threads",
    "warmup_iterations",
    "warmup_time",
    "measurement_iterations",
    "measurement_time",
    "gradle_version",
    "fory_version",
    "kotlinx_version",
    "moshi_version",
    "jackson_version",
    "ksp_version",
    "jmh_plugin_version",
)


@dataclass(frozen=True)
class Aggregate:
    median: float
    mad: float
    count: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--samples", required=True)
    parser.add_argument("--output-dir", required=True)
    return parser.parse_args()


def read_samples(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise ValueError(f"No benchmark samples in {path}")
    return rows


def ops_per_second(value: str | float, unit: str) -> float:
    score = float(value)
    multiplier = {
        "ops/s": 1.0,
        "ops/ms": 1_000.0,
        "ops/us": 1_000_000.0,
        "ops/ns": 1_000_000_000.0,
    }.get(unit)
    if multiplier is None:
        raise ValueError(f"Unsupported JMH throughput unit: {unit}")
    throughput = score * multiplier
    if not math.isfinite(throughput) or throughput <= 0:
        raise ValueError(f"Invalid JMH throughput: {value} {unit}")
    return throughput


def included_samples(rows: Iterable[Mapping[str, str]]) -> list[Mapping[str, str]]:
    included = []
    for row in rows:
        if row.get("included", "").lower() != "true":
            if not row.get("exclusion_reason"):
                raise ValueError(
                    "Every excluded sample must record an exclusion reason"
                )
            continue
        if row.get("return_code") != "0":
            raise ValueError("A failed benchmark process cannot be included")
        if row.get("library") not in LIBRARIES:
            raise ValueError(f"Unknown library: {row.get('library')}")
        if row.get("operation") not in OPERATIONS:
            raise ValueError(f"Unknown operation: {row.get('operation')}")
        if not row.get("score"):
            raise ValueError("Included samples must have a score")
        included.append(row)
    if not included:
        raise ValueError("No included benchmark samples")
    return included


def validate_settings(rows: Iterable[Mapping[str, str]]) -> dict[str, str]:
    values = list(rows)
    metadata: dict[str, str] = {}
    for field in SETTINGS_FIELDS:
        distinct = {row.get(field, "") for row in values}
        if "" in distinct or len(distinct) != 1:
            raise ValueError(f"Missing or mixed benchmark setting: {field}")
        metadata[field] = next(iter(distinct))
    for field in (
        "source_commit",
        "fory_commit",
        "fory_artifact_sha256",
        "dependency_set_sha256",
        "benchmark_jar_sha256",
        "benchmark_date",
        "platform",
        "hardware",
    ):
        distinct = {row.get(field, "") for row in values}
        if "" in distinct or len(distinct) != 1:
            raise ValueError(f"Missing or mixed benchmark identity: {field}")
        metadata[field] = next(iter(distinct))
    return metadata


def median_mad(values: Iterable[float]) -> Aggregate:
    samples = list(values)
    if not samples:
        raise ValueError("Cannot aggregate an empty sample set")
    median = statistics.median(samples)
    mad = statistics.median(abs(value - median) for value in samples)
    return Aggregate(median, mad, len(samples))


def aggregate_absolute(
    rows: Iterable[Mapping[str, str]],
) -> dict[tuple[str, str], Aggregate]:
    grouped: dict[tuple[str, str], list[float]] = defaultdict(list)
    for row in rows:
        grouped[(row["operation"], row["library"])].append(
            ops_per_second(row["score"], row["score_unit"])
        )
    missing = [
        f"{library}/{operation}"
        for operation in OPERATIONS
        for library in LIBRARIES
        if (operation, library) not in grouped
    ]
    if missing:
        raise ValueError("Missing included benchmark cases: " + ", ".join(missing))
    return {key: median_mad(values) for key, values in grouped.items()}


def aggregate_ratios(
    rows: Iterable[Mapping[str, str]],
) -> dict[tuple[str, str], Aggregate]:
    by_round: dict[tuple[str, str, str, str], float] = {}
    for row in rows:
        order = row.get("order", "")
        if order == "unpaired":
            continue
        if order not in ("AB", "BA"):
            raise ValueError(f"Unknown benchmark pair order: {order}")
        comparator = row.get("adjacent_comparator", "")
        if comparator not in COMPARATORS:
            raise ValueError(f"Unknown adjacent comparator: {comparator}")
        library = row["library"]
        if library not in ("fory", comparator):
            raise ValueError(
                f"Paired launch {library} does not match comparator {comparator}"
            )
        key = (row["round_id"], row["operation"], comparator, library)
        if key in by_round:
            raise ValueError(f"Duplicate included launch for {'/'.join(key)}")
        by_round[key] = ops_per_second(row["score"], row["score_unit"])

    ratios: dict[tuple[str, str], list[float]] = defaultdict(list)
    rounds = sorted({key[0] for key in by_round})
    for round_id in rounds:
        for operation in OPERATIONS:
            for comparator in COMPARATORS:
                fory = by_round.get((round_id, operation, comparator, "fory"))
                other = by_round.get((round_id, operation, comparator, comparator))
                if fory is not None and other is not None:
                    ratios[(operation, comparator)].append(fory / other)
    missing = [
        f"fory/{comparator}/{operation}"
        for operation in OPERATIONS
        for comparator in COMPARATORS
        if not ratios[(operation, comparator)]
    ]
    if missing:
        raise ValueError("Missing within-round pairs: " + ", ".join(missing))
    return {key: median_mad(values) for key, values in ratios.items()}


def write_summary(
    absolute: Mapping[tuple[str, str], Aggregate],
    ratios: Mapping[tuple[str, str], Aggregate],
    output: Path,
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    fields = (
        "kind",
        "operation",
        "library",
        "comparator",
        "median",
        "mad",
        "unit",
        "sample_count",
    )
    with output.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        for operation in OPERATIONS:
            for library in LIBRARIES:
                result = absolute[(operation, library)]
                writer.writerow(
                    {
                        "kind": "absolute",
                        "operation": operation,
                        "library": library,
                        "comparator": "",
                        "median": f"{result.median:.12g}",
                        "mad": f"{result.mad:.12g}",
                        "unit": "ops/s",
                        "sample_count": result.count,
                    }
                )
            for comparator in COMPARATORS:
                result = ratios[(operation, comparator)]
                writer.writerow(
                    {
                        "kind": "paired_ratio",
                        "operation": operation,
                        "library": "fory",
                        "comparator": comparator,
                        "median": f"{result.median:.12g}",
                        "mad": f"{result.mad:.12g}",
                        "unit": "ratio",
                        "sample_count": result.count,
                    }
                )


def render_chart(
    operation: str,
    absolute: Mapping[tuple[str, str], Aggregate],
    output: Path,
) -> None:
    figure, axis = plt.subplots(figsize=(8.2, 5.2))
    x = np.arange(len(LIBRARIES), dtype=float)
    values = [absolute[(operation, library)].median for library in LIBRARIES]
    errors = [absolute[(operation, library)].mad for library in LIBRARIES]
    bars = axis.bar(
        x,
        values,
        width=0.62,
        yerr=errors,
        capsize=2.5,
        color=[COLORS[library] for library in LIBRARIES],
        edgecolor=BAR_EDGE_COLOR,
        linewidth=0.8,
    )
    axis.bar_label(
        bars,
        labels=[format_throughput_label(value) for value in values],
        padding=3,
        fontsize=8,
    )
    upper = max(value + error for value, error in zip(values, errors))
    axis.set_ylim(0, upper * 1.18 if upper else 1)
    axis.set_xticks(x)
    axis.set_xticklabels([LABELS[library] for library in LIBRARIES])
    axis.set_ylabel("Throughput (ops/sec)")
    axis.set_title(operation.replace("_", " ").title())
    axis.yaxis.set_major_formatter(FuncFormatter(format_throughput_tick))
    style_throughput_axis(axis)
    figure.tight_layout()
    save_benchmark_figure(figure, output)
    plt.close(figure)


def percent(ratio: float) -> str:
    return f"{(ratio - 1.0) * 100:.1f}%"


def render_report(
    metadata: Mapping[str, str],
    absolute: Mapping[tuple[str, str], Aggregate],
    ratios: Mapping[tuple[str, str], Aggregate],
    excluded_count: int,
    output: Path,
) -> None:
    lines = [
        "# Kotlin JSON Benchmark Report\n\n",
        "This report compares Fory JSON Kotlin, kotlinx.serialization, Moshi, and Jackson Kotlin "
        "on the same immutable `MediaContent` model and Eishay JSON fixture. Every value below "
        "comes from an isolated one-library, one-operation JMH process.\n\n",
        f"- Benchmark date: `{metadata['benchmark_date']}`\n",
        f"- Source commit: `{metadata['source_commit']}`\n",
        f"- Fory artifact commit: `{metadata['fory_commit']}`\n",
        f"- Fory artifact SHA-256: `{metadata['fory_artifact_sha256']}`\n",
        f"- Dependency-set SHA-256: `{metadata['dependency_set_sha256']}`\n",
        f"- Executed JMH JAR SHA-256: `{metadata['benchmark_jar_sha256']}`\n",
        f"- Platform: {metadata['platform']}\n",
        f"- Hardware: {metadata['hardware']}\n",
        f"- JDK: `{metadata['jdk_version']}`; Kotlin: `{metadata['kotlin_version']}`; JMH: `{metadata['jmh_version']}`\n",
        f"- Gradle: `{metadata['gradle_version']}`; JMH Gradle plugin: `{metadata['jmh_plugin_version']}`\n",
        f"- Moshi codegen KSP Gradle plugin: `{metadata['ksp_version']}`\n",
        f"- Dependencies: Fory `{metadata['fory_version']}`, kotlinx.serialization `{metadata['kotlinx_version']}`, Moshi `{metadata['moshi_version']}`, Jackson Kotlin `{metadata['jackson_version']}`\n",
        f"- Forks: {metadata['forks']}; threads: {metadata['threads']}\n",
        f"- Warmup: {metadata['warmup_iterations']} iterations × `{metadata['warmup_time']}`\n",
        f"- Measurement: {metadata['measurement_iterations']} iterations × `{metadata['measurement_time']}`\n",
        f"- Excluded launches: {excluded_count}; exclusions remain in the raw sample file with reasons\n",
        "- Mode: throughput; higher is better; dispersion is median absolute deviation\n\n",
        "The fixture SHA-256 is "
        "`8faba2f57ab397f319aced5cf1e8411a76785557d4c7d1703ec9d540354310a1`. "
        "All model properties are immutable. Setup verifies fixture decoding, each library's "
        "String and byte round trips, and structural equality of all eight encoded outputs before "
        "measurement. Fory uses a retained `jsonTypeRef<MediaContent>()` and synchronous code "
        "generation.\n\n",
        "String methods exclude UTF-8 conversion. For byte serialization, kotlinx.serialization "
        "materializes a fresh `ByteArrayOutputStream`, Moshi materializes a fresh Okio `Buffer`, "
        "and Jackson uses its direct byte API. Their final byte materialization cost is included. "
        "Byte deserialization likewise includes each required in-memory stream or buffer.\n\n",
    ]
    for operation in OPERATIONS:
        title = operation.replace("_", " ").title()
        lines.extend(
            [
                f"## {title}\n\n",
                f"![Kotlin JSON {title} throughput]({CHART_NAMES[operation]})\n\n",
                "| Library | Median ops/sec | MAD | Samples |\n",
                "| --- | ---: | ---: | ---: |\n",
            ]
        )
        for library in LIBRARIES:
            result = absolute[(operation, library)]
            lines.append(
                f"| {LABELS[library]} | {result.median:,.0f} | "
                f"{result.mad:,.0f} | {result.count} |\n"
            )
        lines.extend(
            [
                "\nPaired Fory ratios use only adjacent AB/BA launches and are calculated inside "
                "each round before taking the median.\n\n",
                "| Comparator | Median Fory/comparator ratio | MAD | Relative Fory difference | Paired rounds |\n",
                "| --- | ---: | ---: | ---: | ---: |\n",
            ]
        )
        for comparator in COMPARATORS:
            result = ratios[(operation, comparator)]
            lines.append(
                f"| {LABELS[comparator]} | {result.median:.3f}× | "
                f"{result.mad:.3f} | {percent(result.median)} | {result.count} |\n"
            )
        lines.append("\n")
    lines.extend(
        [
            "## Raw data\n\n",
            "- [Per-launch JMH samples](data/jmh_samples.csv)\n",
            "- [Absolute and paired aggregates](data/summary.csv)\n\n",
            "The local run directory retains the JMH JSON and process logs referenced by the raw "
            "rows. Checked-in results are evidence for the recorded environment, not a guarantee "
            "for another workload or machine.\n",
        ]
    )
    output.write_text("".join(lines), encoding="utf-8")
    format_markdown_with_prettier(output)


def generate(samples: Path, output_dir: Path) -> None:
    rows = read_samples(samples)
    included = included_samples(rows)
    metadata = validate_settings(included)
    absolute = aggregate_absolute(included)
    ratios = aggregate_ratios(included)
    output_dir.mkdir(parents=True, exist_ok=True)
    data_dir = output_dir / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    published_samples = data_dir / "jmh_samples.csv"
    published_samples.write_bytes(samples.read_bytes())
    write_summary(absolute, ratios, data_dir / "summary.csv")
    for operation in OPERATIONS:
        render_chart(operation, absolute, output_dir / CHART_NAMES[operation])
    render_report(
        metadata,
        absolute,
        ratios,
        len(rows) - len(included),
        output_dir / "README.md",
    )


def main() -> None:
    args = parse_args()
    generate(Path(args.samples), Path(args.output_dir))


if __name__ == "__main__":
    main()
