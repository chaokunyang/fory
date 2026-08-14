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

from __future__ import annotations

import csv
import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import benchmark_report
import run_json_benchmark


class KotlinJsonBenchmarkTest(unittest.TestCase):
    def sample_rows(self) -> list[dict[str, str]]:
        rows = []
        scores = {"fory": 400.0, "kotlinx": 200.0, "moshi": 100.0, "jackson": 50.0}
        for round_index in range(3):
            for launch in run_json_benchmark.schedule(round_index):
                rows.append(
                    {
                        "source_commit": "a" * 40,
                        "fory_commit": "b" * 40,
                        "comparison_commit": "not-applicable",
                        "fory_artifact_sha256": "c" * 64,
                        "comparison_artifact_sha256": "not-applicable",
                        "dependency_set_sha256": "d" * 64,
                        "comparison_dependency_set_sha256": "not-applicable",
                        "benchmark_jar_sha256": "e" * 64,
                        "benchmark_date": "2026-08-14",
                        "platform": "test-platform",
                        "hardware": "test-hardware",
                        "gradle_version": "9.3.0",
                        "fory_version": "1.7.0-SNAPSHOT",
                        "kotlinx_version": "1.11.0",
                        "moshi_version": "1.15.2",
                        "jackson_version": "2.22.1",
                        "ksp_version": "2.3.8",
                        "jmh_plugin_version": "0.7.3",
                        "run_id": f"run-r{round_index}-{launch.position}",
                        "variant": "current",
                        "round_id": f"round-{round_index}",
                        "pair_id": f"pair-{round_index}-{launch.operation}",
                        "adjacent_comparator": launch.adjacent_comparator,
                        "library": launch.library,
                        "operation": launch.operation,
                        "position": str(launch.position),
                        "order": launch.order,
                        "jdk_version": "26.0.1",
                        "jmh_version": "1.37",
                        "kotlin_version": "2.3.20",
                        "forks": "1",
                        "threads": "1",
                        "warmup_iterations": "3",
                        "warmup_time": "2s",
                        "measurement_iterations": "5",
                        "measurement_time": "2s",
                        "score": str(scores[launch.library] + round_index),
                        "score_unit": "ops/ms",
                        "score_error": "1.0",
                        "score_confidence_low": "0.0",
                        "score_confidence_high": "2.0",
                        "raw_data_json": "[[1.0,2.0]]",
                        "raw_log_path": f"raw/{launch.position}.log",
                        "result_json_path": f"raw/{launch.position}.json",
                        "return_code": "0",
                        "included": "true",
                        "exclusion_reason": "",
                    }
                )
        return rows

    def write_rows(self, path: Path, rows: list[dict[str, str]]) -> None:
        with path.open("w", newline="", encoding="utf-8") as target:
            writer = csv.DictWriter(target, fieldnames=run_json_benchmark.SAMPLE_FIELDS)
            writer.writeheader()
            writer.writerows(rows)

    def test_round_has_sixteen_isolated_cases(self) -> None:
        paired_cases = []
        for round_index in range(6):
            launches = run_json_benchmark.schedule(round_index)
            self.assertEqual(len(launches), 16)
            self.assertEqual(
                {(launch.library, launch.operation) for launch in launches},
                {
                    (library, operation)
                    for library in benchmark_report.LIBRARIES
                    for operation in benchmark_report.OPERATIONS
                },
            )
            for operation in benchmark_report.OPERATIONS:
                cases = [launch for launch in launches if launch.operation == operation]
                fory_index = next(
                    i for i, launch in enumerate(cases) if launch.library == "fory"
                )
                paired_index = next(
                    i
                    for i, launch in enumerate(cases)
                    if launch.library == launch.adjacent_comparator
                )
                self.assertEqual(abs(fory_index - paired_index), 1)
                fory = cases[fory_index]
                paired_cases.append(
                    (fory.operation, fory.adjacent_comparator, fory.order)
                )
        self.assertEqual(len(paired_cases), 24)
        self.assertEqual(
            set(paired_cases),
            {
                (operation, comparator, order)
                for operation in benchmark_report.OPERATIONS
                for comparator in benchmark_report.COMPARATORS
                for order in ("AB", "BA")
            },
        )

    def test_round_count_balances_pairs(self) -> None:
        with (
            mock.patch.object(
                sys,
                "argv",
                ["run_json_benchmark.py", "--rounds", "5"],
            ),
            mock.patch("sys.stderr", new=io.StringIO()),
            self.assertRaises(SystemExit),
        ):
            run_json_benchmark.parse_args()
        with mock.patch.object(
            sys,
            "argv",
            ["run_json_benchmark.py", "--rounds", "3"],
        ):
            self.assertEqual(run_json_benchmark.parse_args().rounds, 3)

    def test_revision_round_order(self) -> None:
        first = run_json_benchmark.revision_schedule(0)
        second = run_json_benchmark.revision_schedule(1)
        self.assertEqual(len(first), 8)
        self.assertEqual(len(second), 8)
        for operation in benchmark_report.OPERATIONS:
            first_pair = [
                variant for launch, variant in first if launch.operation == operation
            ]
            second_pair = [
                variant for launch, variant in second if launch.operation == operation
            ]
            self.assertEqual(first_pair, list(reversed(second_pair)))

    def test_launch_runs_one_exact_method(self) -> None:
        launch = run_json_benchmark.schedule(0)[0]
        args = SimpleNamespace(
            forks=1,
            warmup_iterations=1,
            measurement_iterations=1,
            threads=1,
            warmup_time="100ms",
            measurement_time="100ms",
        )

        def run(command: list[str], **_: object) -> SimpleNamespace:
            result_path = Path(command[command.index("-rff") + 1])
            result_path.write_text(
                '[{"benchmark":"'
                + run_json_benchmark.BENCHMARK_CLASS
                + "."
                + launch.method
                + '","jdkVersion":"26","jmhVersion":"1.37",'
                '"primaryMetric":{"score":1.0,"scoreUnit":"ops/s",'
                '"scoreError":0.1,"scoreConfidence":[0.9,1.1],'
                '"rawData":[[1.0]]}}]\n',
                encoding="utf-8",
            )
            return SimpleNamespace(returncode=0)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with mock.patch.object(
                run_json_benchmark.subprocess, "run", side_effect=run
            ) as call:
                row = run_json_benchmark.launch_row(
                    launch,
                    "session",
                    root / "benchmarks.jar",
                    root,
                    {"jdk_version": "26"},
                    args,
                    {},
                )
            self.assertEqual(call.call_count, 1)
            command = call.call_args.args[0]
            self.assertEqual(
                command[3],
                f"{run_json_benchmark.BENCHMARK_CLASS}\\.{launch.method}$",
            )
            self.assertEqual(row["raw_data_json"], "[[1.0]]")

    def test_launch_preserves_raw_files(self) -> None:
        launch = run_json_benchmark.schedule(0)[0]
        args = SimpleNamespace(
            forks=1,
            warmup_iterations=1,
            measurement_iterations=1,
            threads=1,
            warmup_time="100ms",
            measurement_time="100ms",
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run_id = "session-r01-p01-current-fory-string_serialization"
            (root / f"{run_id}.log").write_text("retained", encoding="utf-8")
            with (
                mock.patch.object(run_json_benchmark.subprocess, "run") as run,
                self.assertRaisesRegex(ValueError, "retained raw launch"),
            ):
                run_json_benchmark.launch_row(
                    launch,
                    "session",
                    root / "benchmarks.jar",
                    root,
                    {},
                    args,
                    {},
                )
            run.assert_not_called()

    def test_macos_cpu_model(self) -> None:
        result = SimpleNamespace(returncode=0, stdout="Apple M4 Pro\n")
        with (
            mock.patch.object(
                run_json_benchmark.platform, "system", return_value="Darwin"
            ),
            mock.patch.object(
                run_json_benchmark.platform, "machine", return_value="arm64"
            ),
            mock.patch.object(
                run_json_benchmark.platform, "processor", return_value="arm"
            ),
            mock.patch.object(run_json_benchmark.os, "cpu_count", return_value=14),
            mock.patch.object(
                run_json_benchmark.subprocess, "run", return_value=result
            ),
        ):
            identity = run_json_benchmark.hardware_identity()
        self.assertEqual(
            identity,
            "architecture=arm64; processor=Apple M4 Pro; logical_cpus=14",
        )

    def test_java_version(self) -> None:
        result = SimpleNamespace(
            returncode=0,
            stdout="",
            stderr='openjdk version "26.0.1" 2026-04-21\n',
        )
        with mock.patch.object(
            run_json_benchmark.subprocess,
            "run",
            return_value=result,
        ):
            self.assertEqual(run_json_benchmark.java_version(), "26.0.1")

    def test_revision_summary_ratios(self) -> None:
        rows: list[dict[str, object]] = []
        for round_index in range(3):
            for launch, variant in run_json_benchmark.revision_schedule(round_index):
                rows.append(
                    {
                        "round_id": f"round-{round_index}",
                        "operation": launch.operation,
                        "variant": variant,
                        "included": "true",
                        "score": 200 + round_index if variant == "current" else 100,
                        "score_unit": "ops/s",
                    }
                )
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "summary.csv"
            run_json_benchmark.write_revision_summary(rows, output)
            with output.open(encoding="utf-8") as source:
                summary = list(csv.DictReader(source))
            self.assertEqual(len(summary), 4)
            self.assertEqual(summary[0]["median_current_comparison_ratio"], "2.01")

    def test_unit_and_ratio_aggregation(self) -> None:
        included = benchmark_report.included_samples(self.sample_rows())
        absolute = benchmark_report.aggregate_absolute(included)
        ratios = benchmark_report.aggregate_ratios(included)
        self.assertEqual(absolute[("string_serialization", "fory")].median, 401_000)
        self.assertAlmostEqual(
            ratios[("string_serialization", "kotlinx")].median,
            2.0,
        )
        self.assertEqual(ratios[("string_serialization", "kotlinx")].count, 1)
        with self.assertRaisesRegex(ValueError, "Invalid JMH throughput"):
            benchmark_report.ops_per_second("nan", "ops/s")

    def test_exclusion_reasons(self) -> None:
        rows = self.sample_rows()
        rows[0]["included"] = "false"
        rows[0]["exclusion_reason"] = "background load"
        included = benchmark_report.included_samples(rows)
        self.assertEqual(len(included), len(rows) - 1)
        rows[1]["included"] = "false"
        with self.assertRaisesRegex(ValueError, "exclusion reason"):
            benchmark_report.included_samples(rows)

    def test_rejects_mixed_and_missing_settings(self) -> None:
        rows = self.sample_rows()
        included = benchmark_report.included_samples(rows)
        rows[0]["threads"] = "2"
        with self.assertRaisesRegex(ValueError, "threads"):
            benchmark_report.validate_settings(included)
        rows = [row for row in self.sample_rows() if row["library"] != "moshi"]
        included = benchmark_report.included_samples(rows)
        with self.assertRaisesRegex(ValueError, "moshi"):
            benchmark_report.aggregate_absolute(included)

    def test_artifact_isolation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            current_repo = root / "current-repo"
            baseline_repo = root / "baseline-repo"
            current_repo.mkdir()
            baseline_repo.mkdir()
            current_artifact = current_repo / "fory-json-kotlin-1.7.0-SNAPSHOT.jar"
            baseline_artifact = baseline_repo / "fory-json-kotlin-1.7.0-SNAPSHOT.jar"
            current_artifact.write_bytes(b"current")
            baseline_artifact.write_bytes(b"baseline")
            current = root / "current.txt"
            baseline = root / "baseline.txt"
            current.write_text(str(current_artifact) + "\n", encoding="utf-8")
            baseline.write_text(str(baseline_artifact) + "\n", encoding="utf-8")
            hashes = run_json_benchmark.validate_isolated_artifacts(current, baseline)
            self.assertNotEqual(hashes[0], hashes[2])
            self.assertNotEqual(hashes[1], hashes[3])
            with self.assertRaisesRegex(ValueError, "separate"):
                run_json_benchmark.validate_isolated_artifacts(current, current)

    def test_revision_surface_must_be_identical(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            current = root / "current.jar"
            comparison = root / "comparison.jar"
            for jar, changed in ((current, False), (comparison, False)):
                with zipfile.ZipFile(jar, "w") as target:
                    for entry in run_json_benchmark.REVISION_SURFACE_ENTRIES:
                        target.writestr(
                            entry, b"changed" if changed else entry.encode()
                        )
            run_json_benchmark.validate_revision_surface(current, comparison)
            with zipfile.ZipFile(comparison, "w") as target:
                for entry in run_json_benchmark.REVISION_SURFACE_ENTRIES:
                    target.writestr(
                        entry,
                        b"changed"
                        if entry.endswith("MediaContent.class")
                        else entry.encode(),
                    )
            with self.assertRaisesRegex(ValueError, "benchmark surface"):
                run_json_benchmark.validate_revision_surface(current, comparison)

    def test_report_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            samples = root / "samples.csv"
            report = root / "report"
            self.write_rows(samples, self.sample_rows())
            benchmark_report.generate(samples, report)
            for chart in benchmark_report.CHART_NAMES.values():
                self.assertTrue((report / chart).is_file())
            readme = (report / "README.md").read_text(encoding="utf-8")
            self.assertIn("Per-launch JMH samples", readme)
            self.assertIn("Median Fory/comparator ratio", readme)
            self.assertTrue((report / "data" / "jmh_samples.csv").is_file())
            self.assertTrue((report / "data" / "summary.csv").is_file())


if __name__ == "__main__":
    unittest.main()
