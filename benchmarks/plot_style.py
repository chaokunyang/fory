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

"""Shared plotting style for language benchmark reports."""

from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import numpy as np
from matplotlib.ticker import MaxNLocator

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
GROUP_X = np.array([0.0, 0.68])
GROUP_BAR_WIDTH = 0.15
GROUP_OFFSET_STEP = 0.165
GROUP_X_LIMITS = (-0.39, 1.07)


def apply_benchmark_style(plt) -> None:
    plt.rcParams.update(PLOT_RC_PARAMS)


def format_throughput_label(value: float) -> str:
    def format_scaled(scaled: float, suffix: str) -> str:
        return f"{scaled:.2f}".rstrip("0").rstrip(".") + suffix

    if value >= 1e9:
        return format_scaled(value / 1e9, "G")
    if value >= 1e6:
        return format_scaled(value / 1e6, "M")
    if value >= 1e3:
        return format_scaled(value / 1e3, "K")
    return f"{value:.0f}"


def format_throughput_tick(value: float, _position) -> str:
    return format_throughput_label(value)


def style_throughput_axis(ax) -> None:
    ax.set_axisbelow(True)
    ax.grid(True, axis="y", color=GRID_COLOR, linestyle="-", linewidth=0.7)
    ax.grid(False, axis="x")
    ax.yaxis.set_major_locator(MaxNLocator(nbins=5, min_n_ticks=3))
    ax.tick_params(axis="both", width=0.8, length=3)
    for spine in ax.spines.values():
        spine.set_color(SPINE_COLOR)
        spine.set_linewidth(0.8)


def serializer_offset(index: int, count: int) -> float:
    return (index - (count - 1) / 2) * GROUP_OFFSET_STEP


def set_grouped_operation_axis(ax, labels=("Serialize", "Deserialize")) -> None:
    ax.set_xticks(GROUP_X)
    ax.set_xticklabels(list(labels))
    ax.set_xlim(*GROUP_X_LIMITS)


def add_compact_legend(ax) -> None:
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


def save_benchmark_figure(fig, path: str | Path) -> None:
    fig.savefig(path, dpi=170, bbox_inches="tight", pad_inches=0.12)


def format_markdown_with_prettier(*paths: str | Path) -> None:
    prettier = shutil.which("prettier")
    if prettier is None or not paths:
        return
    subprocess.run(
        [
            prettier,
            "--write",
            "--ignore-path",
            os.devnull,
            *(str(path) for path in paths),
        ],
        check=True,
    )
