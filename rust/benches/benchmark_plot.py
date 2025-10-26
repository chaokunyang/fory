import os
import re
import matplotlib.pyplot as plt
import numpy as np
from collections import defaultdict
from datetime import datetime

# === Colors you specified ===
FORY_COLOR = "#FF6f01"  # Orange for Fory
JSON_COLOR = "#55BCC2"  # Teal for JSON
PROTOBUF_COLOR = (1, 0.84, 0.66)  # Light gold for Protobuf

log_file = "cargo_bench.log"

bench_re = re.compile(
    r"Benchmarking\s+([\w_]+)/([\w]+)_(serialize|deserialize)/(\w+).*?time:\s+\[([\d\.]+)\s*([µ\w]+)",
    re.DOTALL,
)

UNIT_TO_SEC = {"ns": 1e-9, "µs": 1e-6, "us": 1e-6, "ms": 1e-3, "s": 1.0}

# data[datatype][size][operation][library]
data = defaultdict(lambda: defaultdict(lambda: defaultdict(dict)))

# Read benchmark log
with open(log_file, "r", encoding="utf-8") as f:
    content = f.read()

# Parse log
for match in bench_re.finditer(content):
    struct, lib, op, size, time_val, unit = match.groups()
    try:
        time_val = float(time_val)
    except ValueError:
        continue
    unit = unit.replace("μ", "µ")
    if unit not in UNIT_TO_SEC:
        continue
    sec = time_val * UNIT_TO_SEC[unit]
    tps = 1.0 / sec
    data[struct][size][op][lib] = tps

sizes = ["small", "medium", "large"]
ops = ["serialize", "deserialize"]

output_dir = datetime.now().strftime("%Y_%m_%d_%H_%M_%S")
os.makedirs(output_dir, exist_ok=True)

# Create one figure PER datatype with 3 subplots (small, medium, large)
for struct in sorted(data.keys()):
    fig, axes = plt.subplots(1, 3, figsize=(18, 5), sharey=False)
    fig.suptitle(f"{struct}", fontsize=16)

    for idx, size in enumerate(sizes):
        ax = axes[idx]
        if size not in data[struct]:
            ax.set_title(f"{size} - No Data")
            ax.axis("off")
            continue

        ops_present = [op for op in ops if op in data[struct][size]]
        libs = sorted(
            {lib for op in ops_present for lib in data[struct][size][op].keys()}
        )

        # Force consistent display order
        lib_order = [lib for lib in ["fory", "json", "protobuf"] if lib in libs] + [
            lib for lib in libs if lib not in ["fory", "json", "protobuf"]
        ]

        x = np.arange(len(ops_present))
        width = 0.8 / len(lib_order)

        for i, lib in enumerate(lib_order):
            if lib == "fory":
                color = FORY_COLOR
            elif lib == "json":
                color = JSON_COLOR
            elif lib == "protobuf":
                color = PROTOBUF_COLOR
            else:
                color = None
            tps_vals = [data[struct][size][op].get(lib, 0) for op in ops_present]
            ax.bar(x + i * width, tps_vals, width, label=lib, color=color)

        ax.set_title(f"{size}")
        ax.set_xticks(x + width * (len(lib_order) - 1) / 2)
        ax.set_xticklabels(ops_present)
        ax.grid(True, axis="y", linestyle="--", alpha=0.5)
        if idx == 0:
            ax.set_ylabel("TPS (ops/sec)")

    # Add legend to first subplot only
    axes[0].legend()
    axes[1].legend()
    axes[2].legend()

    plt.tight_layout(rect=[0, 0, 1, 0.95])  # leave space for suptitle
    plt.savefig(os.path.join(output_dir, f"{struct}.png"))
    plt.close()

print(f"✅ Combined datatype plots saved in: {output_dir}")
