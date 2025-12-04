#!/usr/bin/env python

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

"""
Host-side wrapper: workflow provides only --arch.
Images are defined as regular Python lists (no env vars).

Environment:
  - GITHUB_WORKSPACE (optional; defaults to cwd)
"""

from __future__ import annotations
import argparse
import os
import shlex
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List

# Maximum number of parallel container builds
MAX_PARALLEL_WORKERS = 2

# Define Python version sets directly in the Python script
RELEASE_PYTHON_VERSIONS = (
    "cp38-cp38 cp39-cp39 cp310-cp310 cp311-cp311 cp312-cp312 cp313-cp313"
)
DEFAULT_PYTHON_VERSIONS = "cp38-cp38 cp313-cp313"

# Path to the container build script
CONTAINER_SCRIPT_PATH = "ci/tasks/python_container_build_script.sh"

DEFAULT_X86_IMAGES = [
    "quay.io/pypa/manylinux2014_x86_64:latest",
    # "quay.io/pypa/manylinux_2_28_x86_64:latest",
    # bazel binaries do not work with musl
    # "quay.io/pypa/musllinux_1_2_x86_64:latest",
]

DEFAULT_AARCH64_IMAGES = [
    "quay.io/pypa/manylinux2014_aarch64:latest",
    # "quay.io/pypa/manylinux_2_28_aarch64:latest",
    # bazel binaries do not work with musl
    # "quay.io/pypa/musllinux_1_2_aarch64:latest",
]

ARCH_ALIASES = {
    "X86": "x86",
    "X64": "x86",
    "X86_64": "x86",
    "AMD64": "x86",
    "ARM": "arm64",
    "ARM64": "arm64",
    "AARCH64": "arm64",
}


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument(
        "--arch", required=True, help="Architecture (e.g. X86, X64, AARCH64)"
    )
    p.add_argument(
        "--release", action="store_true", help="Run full test suite for release"
    )
    p.add_argument(
        "--dry-run", action="store_true", help="Print docker commands without running"
    )
    return p.parse_args()


def normalize_arch(raw: str) -> str:
    key = raw.strip().upper()
    return ARCH_ALIASES.get(key, raw.strip().lower())


def collect_images_for_arch(arch_normalized: str) -> List[str]:
    if arch_normalized == "x86":
        imgs = DEFAULT_X86_IMAGES  # dedupe preserving order
    elif arch_normalized == "arm64":
        imgs = DEFAULT_AARCH64_IMAGES
    else:
        raise SystemExit(f"Unsupported arch: {arch_normalized!r}")
    return imgs


def build_docker_cmd(
    workspace: str, image: str, python_version: str, release: bool = False
) -> List[str]:
    workspace = os.path.abspath(workspace)

    # Get GitHub reference name from environment
    github_ref_name = os.environ.get("GITHUB_REF_NAME", "")

    cmd = [
        "docker",
        "run",
        "-i",
        "--rm",
        "-v",
        f"{workspace}:/work",  # (v)olume
        "-w",
        "/work",  # (w)orking directory
        "-e",
        f"PYTHON_VERSIONS={python_version}",  # Single Python version
        "-e",
        f"RELEASE_BUILD={'1' if release else '0'}",
    ]

    # Pass GitHub reference name if available
    if github_ref_name:
        cmd.extend(["-e", f"GITHUB_REF_NAME={github_ref_name}"])

    cmd.extend([image, "bash", CONTAINER_SCRIPT_PATH])
    return cmd


def run_single_build(
    image: str, python_version: str, workspace: str, release: bool, dry_run: bool
) -> tuple[str, int]:
    """Run a single container build for one Python version.

    Returns:
        Tuple of (python_version, return_code)
    """
    docker_cmd = build_docker_cmd(workspace, image, python_version, release=release)
    printable = " ".join(shlex.quote(c) for c in docker_cmd)
    print(f"[{python_version}] + {printable}")

    if dry_run:
        return (python_version, 0)

    try:
        completed = subprocess.run(
            docker_cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        output = completed.stdout.decode("utf-8", errors="replace")

        if completed.returncode != 0:
            print(f"[{python_version}] Container exited with {completed.returncode}")
            print(f"[{python_version}] Output:\n{output}")
            return (python_version, completed.returncode)
        else:
            print(f"[{python_version}] Build completed successfully.")
            return (python_version, 0)
    except FileNotFoundError as e:
        print(f"[{python_version}] Error running docker: {e}")
        return (python_version, 2)


def run_parallel_builds(
    images: List[str],
    python_versions: List[str],
    workspace: str,
    dry_run: bool,
    release: bool = False,
) -> int:
    """Run container builds in parallel with ThreadPoolExecutor.

    Args:
        images: List of Docker images to use
        python_versions: List of Python versions to build
        workspace: Path to the workspace directory
        dry_run: If True, print commands without running
        release: If True, run in release mode

    Returns:
        Overall return code (0 if all succeeded, first non-zero otherwise)
    """
    rc_overall = 0
    failed_versions = []

    # For now, we only use the first image (manylinux2014)
    image = images[0]

    print(f"Building {len(python_versions)} Python versions with max "
          f"{MAX_PARALLEL_WORKERS} parallel workers: {python_versions}")

    with ThreadPoolExecutor(max_workers=MAX_PARALLEL_WORKERS) as executor:
        futures = {
            executor.submit(
                run_single_build, image, pv, workspace, release, dry_run
            ): pv
            for pv in python_versions
        }

        try:
            for future in as_completed(futures):
                python_version, rc = future.result()
                if rc != 0:
                    if rc_overall == 0:
                        rc_overall = rc
                    failed_versions.append(python_version)
        except KeyboardInterrupt:
            print("\nInterrupted by user, cancelling pending builds...", file=sys.stderr)
            executor.shutdown(wait=False, cancel_futures=True)
            return 130

    if failed_versions:
        print(f"\nFailed versions: {failed_versions}", file=sys.stderr)
    else:
        print(f"\nAll {len(python_versions)} versions built successfully.")

    return rc_overall


def main() -> int:
    args = parse_args()
    arch = normalize_arch(args.arch)
    images = collect_images_for_arch(arch)
    if not images:
        print(f"No images configured for arch {arch}", file=sys.stderr)
        return 2
    workspace = os.environ.get("GITHUB_WORKSPACE", os.getcwd())

    # Check if the container script exists
    script_path = os.path.join(workspace, CONTAINER_SCRIPT_PATH)
    if not os.path.exists(script_path):
        print(f"Container script not found at {script_path}", file=sys.stderr)
        return 2

    # Get Python versions based on release mode
    versions_str = RELEASE_PYTHON_VERSIONS if args.release else DEFAULT_PYTHON_VERSIONS
    python_versions = versions_str.split()

    print(f"Selected images for arch {args.arch}: {images}")
    return run_parallel_builds(
        images, python_versions, workspace, args.dry_run, release=args.release
    )


if __name__ == "__main__":
    sys.exit(main())
