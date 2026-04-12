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
from collections import defaultdict

import matplotlib.pyplot as plt


COLORS = {
    "fory": "#FF6f01",
    "protobuf": "#55BCC2",
}


def format_label(value: float) -> str:
    if value >= 1e6:
        return f"{value / 1e6:.2f}M"
    if value >= 1e3:
        return f"{value / 1e3:.2f}K"
    return f"{value:.2f}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Dart benchmark report")
    parser.add_argument("--json-file", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)

    with open(args.json_file, "r", encoding="utf-8") as handle:
        payload = json.load(handle)

    results = defaultdict(lambda: defaultdict(dict))
    for record in payload["results"]:
        results[record["data_type"]][record["operation"]][record["serializer"]] = (
            record["median_ops_per_sec"]
        )

    plot_paths = []
    for data_type, operations in sorted(results.items()):
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
                    xy=(bar.get_x() + bar.get_width() / 2, bar.get_height()),
                    xytext=(0, 3),
                    textcoords="offset points",
                    ha="center",
                    va="bottom",
                    fontsize=9,
                )
        figure.suptitle(data_type)
        figure.tight_layout(rect=[0, 0, 1, 0.95])
        path = os.path.join(args.output_dir, f"{data_type}.png")
        figure.savefig(path, dpi=150)
        plt.close(figure)
        plot_paths.append((data_type, path))

    report_path = os.path.join(args.output_dir, "README.md")
    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("# Dart benchmark results\n\n")
        handle.write("## Throughput\n\n")
        handle.write(
            "| Data Type | Operation | Fory (ops/s) | Protobuf (ops/s) | Fory vs PB |\n"
        )
        handle.write("| --- | --- | ---: | ---: | ---: |\n")
        for data_type, operations in sorted(results.items()):
            for operation in ["serialize", "deserialize"]:
                fory_value = operations.get(operation, {}).get("fory", 0.0)
                protobuf_value = operations.get(operation, {}).get("protobuf", 0.0)
                ratio = (fory_value / protobuf_value) if protobuf_value else 0.0
                handle.write(
                    f"| {data_type} | {operation} | {fory_value:,.2f} | "
                    f"{protobuf_value:,.2f} | {ratio:.2f}x |\n"
                )
        handle.write("\n## Serialized sizes\n\n")
        handle.write("| Data Type | Fory | Protobuf |\n")
        handle.write("| --- | ---: | ---: |\n")
        for data_type, values in sorted(payload["sizes"].items()):
            handle.write(f"| {data_type} | {values['fory']} | {values['protobuf']} |\n")
        if plot_paths:
            handle.write("\n## Plots\n\n")
            for data_type, path in plot_paths:
                handle.write(
                    f"### {data_type}\n\n![{data_type}]({os.path.basename(path)})\n\n"
                )


if __name__ == "__main__":
    main()
