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

"""Run alternating Kotlin JSON JMH trials and retain the raw results."""

from __future__ import annotations

import argparse
import copy
import json
import statistics
import subprocess
from pathlib import Path
from typing import Any

LIBRARIES = ("fory", "kotlinx", "moshi", "jackson")
BENCHMARK_CLASS = "org.apache.fory.benchmark.json.MediaContentBenchmark"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--warmup-iterations", type=int, default=3)
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--duration", default="2s")
    parser.add_argument("--output-dir", default="reports/json")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    for name in ("rounds", "warmup_iterations", "iterations"):
        if getattr(args, name) <= 0:
            parser.error(f"--{name.replace('_', '-')} must be positive")
    return args


def run(command: list[str], cwd: Path, log: Path) -> None:
    log.parent.mkdir(parents=True, exist_ok=True)
    print(f"Running in {cwd}: {' '.join(command)}", flush=True)
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(
            command,
            cwd=cwd,
            stdout=output,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}); see {log}")


def alternating_order(round_index: int) -> tuple[str, ...]:
    offset = round_index % len(LIBRARIES)
    return LIBRARIES[offset:] + LIBRARIES[:offset]


def load_results(path: Path) -> list[dict[str, Any]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list):
        raise TypeError(f"Expected a JMH result list in {path}")
    return value


def aggregate(trials: list[list[dict[str, Any]]], rounds: int) -> list[dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for trial in trials:
        for result in trial:
            grouped.setdefault(result["benchmark"], []).append(result)
    if len(grouped) != 16:
        raise ValueError(f"Expected 16 Kotlin JSON benchmarks, found {len(grouped)}")

    output = []
    for name in sorted(grouped):
        samples = grouped[name]
        if len(samples) != rounds:
            raise ValueError(f"Expected {rounds} samples for {name}")
        result = copy.deepcopy(samples[0])
        scores = [float(sample["primaryMetric"]["score"]) for sample in samples]
        median = statistics.median(scores)
        result["primaryMetric"]["score"] = median
        result["primaryMetric"]["scoreError"] = max(
            abs(score - median) for score in scores
        )
        for field in ("rawData", "scoreConfidence", "scorePercentiles"):
            result["primaryMetric"].pop(field, None)
        result["alternatingRounds"] = rounds
        result["aggregation"] = "median"
        output.append(result)
    return output


def jmh_jar(benchmark_dir: Path) -> Path:
    jars = sorted((benchmark_dir / "build" / "libs").glob("*-jmh.jar"))
    if len(jars) != 1:
        raise RuntimeError(f"Expected one JMH JAR, found {len(jars)}")
    return jars[0]


def main() -> None:
    args = parse_args()
    benchmark_dir = Path(__file__).resolve().parent
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = benchmark_dir / output_dir
    raw_dir = output_dir / "alternating-runs"
    raw_dir.mkdir(parents=True, exist_ok=True)

    if not args.skip_build:
        run(
            [
                "gradle",
                "--no-daemon",
                "clean",
                "test",
                "verifyGeneratedJsonArtifacts",
                "jmhJar",
            ],
            benchmark_dir,
            raw_dir / "build.log",
        )
    jar = jmh_jar(benchmark_dir)
    if args.prepare_only:
        print(f"Kotlin JSON benchmark JAR: {jar}")
        return

    trials: list[list[dict[str, Any]]] = []
    for round_index in range(args.rounds):
        for library in alternating_order(round_index):
            result = raw_dir / f"round-{round_index + 1}-{library}.json"
            selector = f"{BENCHMARK_CLASS}.{library}.*"
            run(
                [
                    "java",
                    "-jar",
                    str(jar),
                    selector,
                    "-f",
                    "1",
                    "-wi",
                    str(args.warmup_iterations),
                    "-i",
                    str(args.iterations),
                    "-t",
                    "1",
                    "-w",
                    args.duration,
                    "-r",
                    args.duration,
                    "-bm",
                    "thrpt",
                    "-tu",
                    "s",
                    "-rf",
                    "json",
                    "-rff",
                    str(result),
                ],
                benchmark_dir,
                raw_dir / f"round-{round_index + 1}-{library}.log",
            )
            trials.append(load_results(result))

    results = aggregate(trials, args.rounds)
    destination = output_dir / "benchmark_results.json"
    destination.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(f"Kotlin JSON benchmark results: {destination}")


if __name__ == "__main__":
    main()
