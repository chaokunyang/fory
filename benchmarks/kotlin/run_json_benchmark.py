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

"""Run isolated paired Kotlin JSON JMH rounds and retain every launch."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import uuid
import zipfile
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Iterable, Mapping

import benchmark_report

BENCHMARK_CLASS = "org.apache.fory.benchmark.json.MediaContentBenchmark"


def load_versions() -> dict[str, str]:
    path = Path(__file__).resolve().parent / "gradle.properties"
    versions = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        versions[key] = value
    required = (
        "foryVersion",
        "kotlinVersion",
        "kspVersion",
        "kspApiVersion",
        "kotlinxSerializationVersion",
        "moshiVersion",
        "jacksonVersion",
        "jmhVersion",
        "jmhPluginVersion",
        "gradleVersion",
    )
    missing = [key for key in required if key not in versions]
    if missing:
        raise ValueError("Missing benchmark versions: " + ", ".join(missing))
    return versions


VERSIONS = load_versions()
KOTLIN_VERSION = VERSIONS["kotlinVersion"]
JMH_VERSION = VERSIONS["jmhVersion"]
REVISION_SURFACE_ENTRIES = (
    "org/apache/fory/benchmark/json/MediaContent.class",
    "org/apache/fory/benchmark/json/Media.class",
    "org/apache/fory/benchmark/json/Image.class",
    "org/apache/fory/benchmark/json/Player.class",
    "org/apache/fory/benchmark/json/ImageSize.class",
    "org/apache/fory/benchmark/json/BenchmarkCodecs.class",
    "org/apache/fory/benchmark/json/MediaContentFixture.class",
    "org/apache/fory/benchmark/json/MediaContentBenchmark.class",
    "org/apache/fory/benchmark/json/BenchmarkState.class",
    "META-INF/BenchmarkList",
    "data/eishay.json",
)
SAMPLE_FIELDS = (
    "source_commit",
    "fory_commit",
    "comparison_commit",
    "fory_artifact_sha256",
    "comparison_artifact_sha256",
    "dependency_set_sha256",
    "comparison_dependency_set_sha256",
    "benchmark_jar_sha256",
    "benchmark_date",
    "platform",
    "hardware",
    "gradle_version",
    "fory_version",
    "kotlinx_version",
    "moshi_version",
    "jackson_version",
    "ksp_version",
    "ksp_api_version",
    "jmh_plugin_version",
    "run_id",
    "variant",
    "round_id",
    "pair_id",
    "adjacent_comparator",
    "library",
    "operation",
    "position",
    "order",
    "jdk_version",
    "jmh_version",
    "kotlin_version",
    "forks",
    "threads",
    "warmup_iterations",
    "warmup_time",
    "measurement_iterations",
    "measurement_time",
    "score",
    "score_unit",
    "score_error",
    "score_confidence_low",
    "score_confidence_high",
    "raw_data_json",
    "raw_log_path",
    "result_json_path",
    "return_code",
    "included",
    "exclusion_reason",
)


@dataclass(frozen=True)
class Launch:
    round_index: int
    position: int
    library: str
    operation: str
    adjacent_comparator: str
    order: str

    @property
    def method(self) -> str:
        operation = "".join(word.title() for word in self.operation.split("_"))
        return f"{self.library}{operation[0].upper()}{operation[1:]}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=6)
    parser.add_argument("--warmup-iterations", type=int, default=3)
    parser.add_argument("--measurement-iterations", type=int, default=5)
    parser.add_argument("--warmup-time", default="2s")
    parser.add_argument("--measurement-time", default="2s")
    parser.add_argument("--forks", type=int, default=1)
    parser.add_argument("--threads", type=int, default=1)
    parser.add_argument("--output-dir", default="reports/json")
    parser.add_argument("--jmh-jar")
    parser.add_argument("--classpath-file")
    parser.add_argument("--comparison-classpath-file")
    parser.add_argument("--comparison-jmh-jar")
    parser.add_argument("--fory-commit")
    parser.add_argument("--comparison-commit", default="not-applicable")
    parser.add_argument("--exclusions")
    parser.add_argument("--session-id")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    for field in (
        "rounds",
        "warmup_iterations",
        "measurement_iterations",
        "forks",
        "threads",
    ):
        if getattr(args, field) <= 0:
            parser.error(f"--{field.replace('_', '-')} must be positive")
    if args.rounds % len(benchmark_report.COMPARATORS) != 0:
        parser.error("--rounds must be a multiple of 3 to balance comparator pairs")
    comparison_values = (
        bool(args.comparison_classpath_file),
        bool(args.comparison_jmh_jar),
        args.comparison_commit != "not-applicable",
    )
    if len(set(comparison_values)) != 1:
        parser.error(
            "--comparison-classpath-file, --comparison-jmh-jar, and "
            "--comparison-commit must be supplied together"
        )
    if args.session_id and re.fullmatch(r"[A-Za-z0-9_.-]+", args.session_id) is None:
        parser.error(
            "--session-id may contain only letters, digits, dot, underscore, and hyphen"
        )
    return args


def schedule(round_index: int) -> tuple[Launch, ...]:
    launches: list[Launch] = []
    position = 1
    for operation_index, operation in enumerate(benchmark_report.OPERATIONS):
        comparator_index = (round_index + operation_index) % len(
            benchmark_report.COMPARATORS
        )
        adjacent = benchmark_report.COMPARATORS[comparator_index]
        remaining = [
            library for library in benchmark_report.COMPARATORS if library != adjacent
        ]
        is_ab = (round_index + operation_index) % 2 == 0
        if is_ab:
            order = ["fory", adjacent, *remaining]
            order_name = "AB"
        else:
            order = [*reversed(remaining), adjacent, "fory"]
            order_name = "BA"
        for library in order:
            launches.append(
                Launch(
                    round_index=round_index,
                    position=position,
                    library=library,
                    operation=operation,
                    adjacent_comparator=adjacent,
                    order=order_name if library in ("fory", adjacent) else "unpaired",
                )
            )
            position += 1
    if len(launches) != 16:
        raise AssertionError("Each round must have exactly 16 isolated launches")
    return tuple(launches)


def revision_schedule(round_index: int) -> tuple[tuple[Launch, str], ...]:
    launches = []
    position = 1
    for operation_index, operation in enumerate(benchmark_report.OPERATIONS):
        variants = (
            ("current", "comparison")
            if (round_index + operation_index) % 2 == 0
            else ("comparison", "current")
        )
        order = "AB" if variants[0] == "current" else "BA"
        for variant in variants:
            launches.append(
                (
                    Launch(
                        round_index=round_index,
                        position=position,
                        library="fory",
                        operation=operation,
                        adjacent_comparator="fory-revision",
                        order=order,
                    ),
                    variant,
                )
            )
            position += 1
    if len(launches) != 8:
        raise AssertionError("Each Fory revision round must have eight launches")
    return tuple(launches)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def classpath_identity(path: Path) -> tuple[str, str]:
    files = [Path(line) for line in path.read_text(encoding="utf-8").splitlines()]
    missing = [str(file) for file in files if not file.is_file()]
    if missing:
        raise ValueError("Missing classpath artifacts: " + ", ".join(missing))
    artifact_hashes = [(file.name, sha256(file)) for file in files]
    fory = [
        value
        for name, value in artifact_hashes
        if name.startswith("fory-json-kotlin-") and "-ksp-" not in name
    ]
    if len(fory) != 1:
        raise ValueError(
            "Expected one fory-json-kotlin runtime artifact, found " + str(len(fory))
        )
    digest = hashlib.sha256()
    for name, value in sorted(artifact_hashes):
        digest.update(name.encode())
        digest.update(b"\0")
        digest.update(value.encode())
        digest.update(b"\n")
    return fory[0], digest.hexdigest()


def validate_isolated_artifacts(
    current: Path, comparison: Path
) -> tuple[str, str, str, str]:
    if current.resolve() == comparison.resolve():
        raise ValueError(
            "Fory revision comparison requires separate classpath manifests"
        )
    current_files = {
        Path(line).resolve()
        for line in current.read_text(encoding="utf-8").splitlines()
    }
    comparison_files = {
        Path(line).resolve()
        for line in comparison.read_text(encoding="utf-8").splitlines()
    }
    current_fory = {
        file
        for file in current_files
        if file.name.startswith("fory-json-kotlin-") and "-ksp-" not in file.name
    }
    comparison_fory = {
        file
        for file in comparison_files
        if file.name.startswith("fory-json-kotlin-") and "-ksp-" not in file.name
    }
    if len(current_fory) != 1 or len(comparison_fory) != 1:
        raise ValueError(
            "Each comparison classpath must contain one Fory Kotlin JSON artifact"
        )
    if current_fory == comparison_fory:
        raise ValueError("Fory revisions resolved to the same artifact path")
    current_fory_hash, current_dependency_hash = classpath_identity(current)
    comparison_fory_hash, comparison_dependency_hash = classpath_identity(comparison)
    return (
        current_fory_hash,
        current_dependency_hash,
        comparison_fory_hash,
        comparison_dependency_hash,
    )


def validate_revision_surface(current: Path, comparison: Path) -> None:
    with (
        zipfile.ZipFile(current) as current_jar,
        zipfile.ZipFile(comparison) as comparison_jar,
    ):
        for entry in REVISION_SURFACE_ENTRIES:
            try:
                current_bytes = current_jar.read(entry)
                comparison_bytes = comparison_jar.read(entry)
            except KeyError as error:
                raise ValueError(
                    f"Missing revision benchmark surface entry: {entry}"
                ) from error
            if current_bytes != comparison_bytes:
                raise ValueError(
                    "Fory revisions do not share the exact benchmark surface: " + entry
                )


def git_commit(root: Path) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        text=True,
        capture_output=True,
        check=True,
    )
    return result.stdout.strip()


def hardware_identity() -> str:
    processor = platform.processor().strip()
    if platform.system() == "Darwin":
        result = subprocess.run(
            ["sysctl", "-n", "machdep.cpu.brand_string"],
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip():
            processor = result.stdout.strip()
    elif platform.system() == "Linux":
        cpu_info = Path("/proc/cpuinfo")
        if cpu_info.is_file():
            for line in cpu_info.read_text(
                encoding="utf-8", errors="replace"
            ).splitlines():
                name, separator, value = line.partition(":")
                if separator and name.strip() in ("model name", "Hardware"):
                    processor = value.strip()
                    break
    return (
        f"architecture={platform.machine()}; "
        f"processor={processor or 'unknown'}; "
        f"logical_cpus={os.cpu_count() or 'unknown'}"
    )


def prepare(benchmark_dir: Path, output_dir: Path) -> tuple[Path, Path]:
    log = output_dir / "prepare.log"
    command = [
        "gradle",
        "--no-daemon",
        "test",
        "verifyGeneratedJsonArtifacts",
        "jmhJar",
        "writeBenchmarkClasspath",
    ]
    with log.open("w", encoding="utf-8") as target:
        result = subprocess.run(
            command,
            cwd=benchmark_dir,
            stdout=target,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    if result.returncode != 0:
        raise RuntimeError(f"Benchmark preparation failed; see {log}")
    jars = sorted((benchmark_dir / "build" / "libs").glob("*-jmh.jar"))
    if len(jars) != 1:
        raise ValueError(f"Expected one JMH jar, found {len(jars)}")
    classpath = benchmark_dir / "build" / "benchmark-runtime-classpath.txt"
    if not classpath.is_file():
        raise ValueError(f"Missing generated classpath manifest: {classpath}")
    return jars[0], classpath


def load_exclusions(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}
    with path.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    exclusions = {}
    for row in rows:
        run_id = row.get("run_id", "")
        reason = row.get("reason", "")
        if not run_id or not reason:
            raise ValueError("Exclusions require non-empty run_id and reason columns")
        if run_id in exclusions:
            raise ValueError(f"Duplicate exclusion for {run_id}")
        exclusions[run_id] = reason
    return exclusions


def read_jmh_result(path: Path, expected_method: str) -> Mapping[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list) or len(value) != 1:
        raise ValueError(f"Expected one JMH result in {path}")
    benchmark = value[0]
    if not str(benchmark.get("benchmark", "")).endswith("." + expected_method):
        raise ValueError(
            f"Unexpected benchmark in {path}: {benchmark.get('benchmark')}"
        )
    return benchmark


def write_samples(path: Path, rows: Iterable[Mapping[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=SAMPLE_FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def java_version() -> str:
    result = subprocess.run(
        ["java", "-version"],
        text=True,
        capture_output=True,
        check=False,
    )
    output = result.stderr or result.stdout
    first_line = output.splitlines()[0] if output else ""
    match = re.search(r'version "([^"]+)"', first_line)
    if result.returncode != 0 or match is None:
        raise ValueError("Unable to determine the benchmark JDK from java -version")
    return match.group(1)


def jdk_version(result: Mapping[str, Any], fallback: object) -> str:
    return str(result.get("jdkVersion") or fallback)


def launch_row(
    launch: Launch,
    session_id: str,
    jar: Path,
    raw_dir: Path,
    common: Mapping[str, object],
    args: argparse.Namespace,
    exclusions: Mapping[str, str],
    variant: str = "current",
) -> dict[str, object]:
    run_id = (
        f"{session_id}-r{launch.round_index + 1:02d}-"
        f"p{launch.position:02d}-{variant}-{launch.library}-{launch.operation}"
    )
    result_path = raw_dir / f"{run_id}.json"
    log_path = raw_dir / f"{run_id}.log"
    if result_path.exists() or log_path.exists():
        raise ValueError(f"Refusing to overwrite retained raw launch {run_id}")
    selector = f"{BENCHMARK_CLASS}\\.{launch.method}$"
    command = [
        "java",
        "-jar",
        str(jar),
        selector,
        "-f",
        str(args.forks),
        "-wi",
        str(args.warmup_iterations),
        "-i",
        str(args.measurement_iterations),
        "-t",
        str(args.threads),
        "-w",
        args.warmup_time,
        "-r",
        args.measurement_time,
        "-bm",
        "thrpt",
        "-tu",
        "s",
        "-rf",
        "json",
        "-rff",
        str(result_path),
    ]
    with log_path.open("w", encoding="utf-8") as target:
        process = subprocess.run(
            command,
            stdout=target,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )

    reason = exclusions.get(run_id, "")
    result: Mapping[str, Any] = {}
    score = ""
    score_unit = ""
    score_error = ""
    confidence_low = ""
    confidence_high = ""
    raw_data = ""
    if process.returncode == 0:
        try:
            result = read_jmh_result(result_path, launch.method)
            metric = result["primaryMetric"]
            score = metric["score"]
            score_unit = metric["scoreUnit"]
            score_error = metric.get("scoreError", "")
            confidence = metric.get("scoreConfidence", [])
            if isinstance(confidence, list) and len(confidence) == 2:
                confidence_low, confidence_high = confidence
            raw_data = json.dumps(metric.get("rawData", []), separators=(",", ":"))
        except (OSError, ValueError, KeyError, TypeError) as error:
            detail = f"invalid JMH result: {error}"
            reason = f"{reason}; {detail}" if reason else detail
    else:
        reason = reason or f"process exited with {process.returncode}"

    return {
        **common,
        "run_id": run_id,
        "variant": variant,
        "round_id": f"round-{launch.round_index + 1:02d}",
        "pair_id": (
            f"round-{launch.round_index + 1:02d}:{launch.operation}:"
            f"{launch.adjacent_comparator}"
        ),
        "adjacent_comparator": launch.adjacent_comparator,
        "library": launch.library,
        "operation": launch.operation,
        "position": launch.position,
        "order": launch.order,
        "jdk_version": jdk_version(result, common["jdk_version"]),
        "jmh_version": result.get("jmhVersion", JMH_VERSION),
        "kotlin_version": KOTLIN_VERSION,
        "forks": args.forks,
        "threads": args.threads,
        "warmup_iterations": args.warmup_iterations,
        "warmup_time": args.warmup_time,
        "measurement_iterations": args.measurement_iterations,
        "measurement_time": args.measurement_time,
        "score": score,
        "score_unit": score_unit,
        "score_error": score_error,
        "score_confidence_low": confidence_low,
        "score_confidence_high": confidence_high,
        "raw_data_json": raw_data,
        "raw_log_path": str(Path("raw") / log_path.name),
        "result_json_path": str(Path("raw") / result_path.name),
        "return_code": process.returncode,
        "included": str(process.returncode == 0 and not reason).lower(),
        "exclusion_reason": reason,
    }


def write_revision_summary(rows: Iterable[Mapping[str, object]], output: Path) -> None:
    by_round: dict[tuple[str, str, str], float] = {}
    for row in rows:
        if row["included"] != "true":
            continue
        key = (str(row["round_id"]), str(row["operation"]), str(row["variant"]))
        if key in by_round:
            raise ValueError(f"Duplicate Fory revision launch for {'/'.join(key)}")
        by_round[key] = benchmark_report.ops_per_second(
            str(row["score"]), str(row["score_unit"])
        )
    fields = (
        "operation",
        "median_current_comparison_ratio",
        "mad",
        "paired_rounds",
    )
    with output.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        rounds = sorted({key[0] for key in by_round})
        for operation in benchmark_report.OPERATIONS:
            ratios = []
            for round_id in rounds:
                current = by_round.get((round_id, operation, "current"))
                comparison = by_round.get((round_id, operation, "comparison"))
                if current is not None and comparison is not None:
                    ratios.append(current / comparison)
            if not ratios:
                raise ValueError(f"Missing Fory revision pairs for {operation}")
            result = benchmark_report.median_mad(ratios)
            writer.writerow(
                {
                    "operation": operation,
                    "median_current_comparison_ratio": f"{result.median:.12g}",
                    "mad": f"{result.mad:.12g}",
                    "paired_rounds": result.count,
                }
            )


def main() -> None:
    args = parse_args()
    benchmark_dir = Path(__file__).resolve().parent
    root = benchmark_dir.parents[1]
    output_dir = Path(args.output_dir)
    if not output_dir.is_absolute():
        output_dir = benchmark_dir / output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    sample_path = output_dir / "jmh_samples.csv"
    if not args.prepare_only and sample_path.exists():
        raise ValueError(
            f"Refusing to overwrite retained benchmark samples: {sample_path}"
        )
    revision_path = output_dir / "revision_samples.csv"
    if not args.prepare_only and args.comparison_jmh_jar and revision_path.exists():
        raise ValueError(
            f"Refusing to overwrite retained revision samples: {revision_path}"
        )

    if args.skip_build:
        if not args.jmh_jar or not args.classpath_file:
            raise ValueError("--skip-build requires --jmh-jar and --classpath-file")
        jar = Path(args.jmh_jar)
        classpath = Path(args.classpath_file)
    else:
        jar, classpath = prepare(benchmark_dir, output_dir)

    if args.prepare_only:
        print(f"Prepared {jar}")
        return

    comparison_hash = "not-applicable"
    comparison_dependency_hash = "not-applicable"
    if args.comparison_classpath_file:
        (
            fory_hash,
            dependency_hash,
            comparison_hash,
            comparison_dependency_hash,
        ) = validate_isolated_artifacts(classpath, Path(args.comparison_classpath_file))
    else:
        fory_hash, dependency_hash = classpath_identity(classpath)

    source_commit = git_commit(root)
    common: dict[str, object] = {
        "source_commit": source_commit,
        "fory_commit": args.fory_commit or source_commit,
        "comparison_commit": args.comparison_commit,
        "fory_artifact_sha256": fory_hash,
        "comparison_artifact_sha256": comparison_hash,
        "dependency_set_sha256": dependency_hash,
        "comparison_dependency_set_sha256": comparison_dependency_hash,
        "benchmark_date": date.today().isoformat(),
        "platform": platform.platform(),
        "hardware": hardware_identity(),
        "jdk_version": java_version(),
        "gradle_version": VERSIONS["gradleVersion"],
        "fory_version": VERSIONS["foryVersion"],
        "kotlinx_version": VERSIONS["kotlinxSerializationVersion"],
        "moshi_version": VERSIONS["moshiVersion"],
        "jackson_version": VERSIONS["jacksonVersion"],
        "ksp_version": VERSIONS["kspVersion"],
        "ksp_api_version": VERSIONS["kspApiVersion"],
        "jmh_plugin_version": VERSIONS["jmhPluginVersion"],
    }
    exclusions = load_exclusions(Path(args.exclusions) if args.exclusions else None)
    raw_dir = output_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    retained_jar = output_dir / "artifacts" / jar.name
    retained_jar.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, retained_jar)
    current_common = {**common, "benchmark_jar_sha256": sha256(retained_jar)}
    retained_comparison_jar = None
    comparison_common = None
    if args.comparison_jmh_jar:
        comparison_jar = Path(args.comparison_jmh_jar)
        if comparison_jar.resolve() == jar.resolve():
            raise ValueError("Fory revision comparison requires separate JMH jars")
        validate_revision_surface(jar, comparison_jar)
        retained_comparison_jar = (
            output_dir / "artifacts" / ("comparison-" + comparison_jar.name)
        )
        shutil.copy2(comparison_jar, retained_comparison_jar)
        comparison_common = {
            **common,
            "benchmark_jar_sha256": sha256(retained_comparison_jar),
        }

    rows: list[dict[str, object]] = []
    session_id = args.session_id or uuid.uuid4().hex[:12]
    for round_index in range(args.rounds):
        for launch in schedule(round_index):
            print(
                f"Round {round_index + 1}/{args.rounds}, "
                f"case {launch.position}/16: {launch.library} {launch.operation}",
                flush=True,
            )
            rows.append(
                launch_row(
                    launch,
                    session_id,
                    retained_jar,
                    raw_dir,
                    current_common,
                    args,
                    exclusions,
                )
            )
            write_samples(sample_path, rows)

    failures = [row for row in rows if row["return_code"] != 0 or not row["score"]]
    if failures:
        raise RuntimeError(
            f"{len(failures)} benchmark processes failed; retained samples in {sample_path}"
        )
    if retained_comparison_jar is not None:
        assert comparison_common is not None
        revision_rows: list[dict[str, object]] = []
        revision_session = "revision-" + session_id
        for round_index in range(args.rounds):
            for launch, variant in revision_schedule(round_index):
                revision_rows.append(
                    launch_row(
                        launch,
                        revision_session,
                        retained_jar
                        if variant == "current"
                        else retained_comparison_jar,
                        raw_dir,
                        current_common if variant == "current" else comparison_common,
                        args,
                        exclusions,
                        variant,
                    )
                )
                write_samples(revision_path, revision_rows)
        revision_failures = [
            row for row in revision_rows if row["return_code"] != 0 or not row["score"]
        ]
        if revision_failures:
            raise RuntimeError(
                f"{len(revision_failures)} Fory revision processes failed; "
                f"retained samples in {revision_path}"
            )
        write_revision_summary(revision_rows, output_dir / "revision_summary.csv")
        rows.extend(revision_rows)
    unknown_exclusions = set(exclusions) - {str(row["run_id"]) for row in rows}
    if unknown_exclusions:
        raise ValueError(
            "Exclusions did not match a launch: "
            + ", ".join(sorted(unknown_exclusions))
        )
    benchmark_report.generate(sample_path, output_dir / "report")
    print(f"Kotlin JSON benchmark report: {output_dir / 'report' / 'README.md'}")


if __name__ == "__main__":
    main()
