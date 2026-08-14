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

import logging
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from . import common


PRODUCTION_MODULES = "fory-kotlin,fory-kotlin-ksp,fory-json-kotlin,fory-json-kotlin-ksp"
LOW_JDK_MODULES = "fory-kotlin,fory-kotlin-ksp,fory-json-kotlin"
CORPUS_MODULE_NAME = "org.apache.fory.integration.kotlin.json.corpus"
CORPUS_PACKAGE = "org.apache.fory.integration.kotlin.json.corpus"
CORPUS_GENERATED_MODELS = (
    "PlatformAccount",
    "PlatformAnnotated",
    "PlatformBox",
    "PlatformBuiltins",
    "PlatformCase",
    "PlatformCaseManifest",
    "PlatformCircle",
    "PlatformEnvelope",
    "PlatformGenericKey",
    "PlatformKotlinProfile",
    "PlatformMarker",
    "PlatformNode",
    "PlatformNullableText",
    "PlatformNullOnly",
    "PlatformNulls",
    "PlatformOrdinary",
    "PlatformPositiveId",
    "PlatformPropertyNumber",
    "PlatformRoot",
    "PlatformShapeMarker",
    "PlatformUnitHolder",
    "PlatformUnlistedShape",
    "PlatformValueHolder",
    "PlatformWrappedData",
    "PlatformWrappedMarker",
    "PlatformWrappedNumber",
)
CORPUS_RULE_MODELS = CORPUS_GENERATED_MODELS + (
    "PlatformInvalidPropertyShape",
    "PlatformPropertyShape",
    "PlatformWrappedShape",
)
CORPUS_MIXIN_COMPANIONS = (
    "PlatformJavaProfileMixin_ForyJsonMixin_"
    "org_x2e_apache_x2e_fory_x2e_integration_x2e_kotlin_x2e_json_x2e_corpus_x2e_"
    "PlatformJavaProfile_ForyJsonCodec",
    "PlatformKotlinProfileMixin_ForyJsonMixin_"
    "org_x2e_apache_x2e_fory_x2e_integration_x2e_kotlin_x2e_json_x2e_corpus_x2e_"
    "PlatformKotlinProfile_ForyJsonCodec",
)
CORPUS_MIXIN_MODELS = (
    "PlatformJavaProfileMixin",
    "PlatformKotlinProfileMixin",
)


def java_major_version():
    """Return the active Java runtime's major version."""
    version_output = subprocess.check_output(
        "java -version 2>&1", shell=True, universal_newlines=True
    )
    match = re.search(r'version "([^"]+)"', version_output)
    if not match:
        raise RuntimeError(f"Unable to parse Java version from:\n{version_output}")
    version = match.group(1)
    if version.startswith("1."):
        return int(version.split(".")[1])
    return int(version.split(".")[0])


def install_java_json(include_jpms=False):
    """Install the Java artifacts consumed by Kotlin JSON modules."""
    modules = "fory-json,fory-annotation-processor"
    test_option = "-Dmaven.test.skip=true"
    if include_jpms:
        modules = (
            "fory-json,fory-format,fory-test-core,fory-testsuite,"
            "fory-annotation-processor"
        )
        # The JDK25 multi-release verifier is test-owned and must still compile.
        test_option = "-DskipTests"
    common.cd_project_subdir("java")
    common.exec_cmd(
        "mvn -T16 --batch-mode --no-transfer-progress "
        f"-pl {modules} -am install {test_option} "
        "-Dmaven.javadoc.skip=true -Dmaven.source.skip=true"
    )


def install_artifacts(include_corpus=True, modules=PRODUCTION_MODULES):
    """Install Kotlin production artifacts and the shared JSON corpus."""
    common.cd_project_subdir("kotlin")
    common.exec_cmd(
        "mvn -T16 --batch-mode --no-transfer-progress "
        f"-pl {modules} -am clean install -DskipTests "
        "-Ddokka.skip=true -Dmaven.source.skip=true"
    )
    if include_corpus:
        common.cd_project_subdir("integration_tests/kotlin_json_corpus")
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress clean install "
            "-DskipTests -Ddokka.skip=true -Dmaven.source.skip=true"
        )
        verify_corpus_artifact()


def verify_corpus_artifact():
    """Verify KSP class/resource packaging in the shared platform corpus JAR."""
    module_dir = Path(
        common.PROJECT_ROOT_DIR, "integration_tests", "kotlin_json_corpus"
    )
    version = _kotlin_version()
    jar_path = module_dir / "target" / f"kotlin-json-corpus-{version}.jar"
    if not jar_path.is_file():
        raise FileNotFoundError(f"Missing Kotlin JSON corpus artifact: {jar_path}")

    expected_companions = {
        f"{CORPUS_PACKAGE.replace('.', '/')}/{model}_ForyJsonCodec.class"
        for model in CORPUS_GENERATED_MODELS
    }
    expected_companions.update(
        f"{CORPUS_PACKAGE.replace('.', '/')}/{name}.class"
        for name in CORPUS_MIXIN_COMPANIONS
    )
    expected_operations = {
        name[:-6] + "_Operations.class" for name in expected_companions
    }
    expected_sources = {
        f"{CORPUS_PACKAGE.replace('.', '/')}/{model}_ForyJsonCodec.java"
        for model in CORPUS_GENERATED_MODELS
    }
    expected_sources.update(
        f"{CORPUS_PACKAGE.replace('.', '/')}/{name}.java"
        for name in CORPUS_MIXIN_COMPANIONS
    )
    expected_rules = {
        f"META-INF/proguard/fory-json-{CORPUS_PACKAGE}.{model}.pro"
        for model in CORPUS_RULE_MODELS
    }
    expected_rules.update(
        f"META-INF/proguard/fory-json-mixin-{CORPUS_PACKAGE}.{model}.pro"
        for model in CORPUS_MIXIN_MODELS
    )
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
        expected_module = f"Automatic-Module-Name: {CORPUS_MODULE_NAME}"
        if expected_module not in manifest:
            raise RuntimeError(f"{jar_path} is missing {expected_module}")
        case_manifest = f"{CORPUS_PACKAGE.replace('.', '/')}/cases.json"
        if case_manifest not in names:
            raise RuntimeError(f"{jar_path} is missing {case_manifest}")
        for label, expected in (
            ("generated companions", expected_companions),
            ("generated operations", expected_operations),
            ("consumer rules", expected_rules),
        ):
            missing = sorted(expected - names)
            if missing:
                raise RuntimeError(f"{jar_path} is missing {label}: {missing}")
        actual_companions = {
            name
            for name in names
            if name.startswith(CORPUS_PACKAGE.replace(".", "/") + "/")
            and name.endswith("_ForyJsonCodec.class")
        }
        actual_rules = {
            name
            for name in names
            if name.startswith("META-INF/proguard/fory-json-") and name.endswith(".pro")
        }
        actual_operations = {
            name
            for name in names
            if name.startswith(CORPUS_PACKAGE.replace(".", "/") + "/")
            and name.endswith("_ForyJsonCodec_Operations.class")
        }
        if actual_companions != expected_companions:
            raise RuntimeError(
                f"{jar_path} has unexpected generated companions: "
                f"{sorted(actual_companions ^ expected_companions)}"
            )
        if actual_rules != expected_rules:
            raise RuntimeError(
                f"{jar_path} has unexpected consumer rules: "
                f"{sorted(actual_rules ^ expected_rules)}"
            )
        if actual_operations != expected_operations:
            raise RuntimeError(
                f"{jar_path} has unexpected generated operations: "
                f"{sorted(actual_operations ^ expected_operations)}"
            )
        for name in expected_companions | expected_operations:
            class_bytes = jar.read(name)
            if class_bytes[:4] != b"\xca\xfe\xba\xbe":
                raise RuntimeError(f"{jar_path}!/{name} is not a JVM class")
            major = int.from_bytes(class_bytes[6:8], "big")
            if major != 52:
                raise RuntimeError(
                    f"{jar_path}!/{name} is JVM class version {major}, expected 52"
                )
    generated_source_dir = module_dir / "target" / "generated-sources" / "ksp"
    actual_sources = {
        path.relative_to(generated_source_dir).as_posix()
        for path in generated_source_dir.rglob("*_ForyJsonCodec.java")
    }
    if actual_sources != expected_sources:
        raise RuntimeError(
            f"{generated_source_dir} has unexpected generated companions: "
            f"{sorted(actual_sources ^ expected_sources)}"
        )


def _kotlin_version():
    pom = Path(common.PROJECT_ROOT_DIR, "kotlin", "pom.xml")
    root = ET.parse(pom).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = root.findtext("m:version", namespaces=namespace)
    if not version:
        raise ValueError(f"Cannot find Kotlin parent version in {pom}")
    return version


def run_tests():
    """Run the Kotlin JVM matrix for the active JDK."""
    logging.info("Executing fory kotlin tests")
    os.environ.setdefault("ENABLE_FORY_DEBUG_OUTPUT", "1")
    major = java_major_version()
    install_java_json(include_jpms=major == 25)
    modules = PRODUCTION_MODULES if major >= 17 else LOW_JDK_MODULES
    install_artifacts(include_corpus=major >= 17, modules=modules)
    common.cd_project_subdir("kotlin")
    if major >= 17:
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress test -DfailIfNoTests=false"
        )
        common.exec_cmd("mvn -T16 --batch-mode --no-transfer-progress spotless:check")
        common.cd_project_subdir("integration_tests/kotlin_json_corpus")
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress clean test "
            "-DfailIfNoTests=false"
        )
    else:
        logging.info(
            "Skipping KSP generation tests on JDK < 17 because ksp-maven-plugin requires Java 17+"
        )
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress "
            f"-pl {LOW_JDK_MODULES} -am test -DfailIfNoTests=false"
        )
    if major == 25:
        common.cd_project_subdir("kotlin")
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress "
            f"-pl {PRODUCTION_MODULES} -am package -DskipTests "
            "-Dgpg.skip=true -Papache-release"
        )
        common.cd_project_subdir("")
        common.exec_cmd("python ci/release.py verify_kotlin_artifacts")
        common.cd_project_subdir("integration_tests/jpms_tests")
        common.exec_cmd("mvn -T10 --batch-mode --no-transfer-progress clean test")

    logging.info("Executing fory kotlin tests succeeds")


def run_native_json():
    """Build and execute the dedicated Kotlin JSON Native Image fixture."""
    os.environ.setdefault("ENABLE_FORY_DEBUG_OUTPUT", "1")
    install_java_json()
    install_artifacts(include_corpus=True)
    common.cd_project_subdir("integration_tests/graalvm_kotlin_tests")
    common.exec_cmd(
        "mvn --batch-mode --no-transfer-progress -DskipTests=true -Pnative clean package"
    )
    common.exec_cmd("./target/main")
    expect_native_failure(
        "org.apache.fory.graalvm.kotlin.CodegenDisabledMain",
        "codegen-disabled ForyJson",
    )
    expect_native_failure(
        "org.apache.fory.graalvm.kotlin.InvalidPropertyMain",
        "PROPERTY",
    )


def expect_native_failure(main_class, expected_text):
    """Require one invalid provider image to fail during hosted analysis."""
    project_dir = Path(
        common.PROJECT_ROOT_DIR, "integration_tests", "graalvm_kotlin_tests"
    )
    command = [
        "mvn",
        "--batch-mode",
        "--no-transfer-progress",
        "-DskipTests=true",
        "-Pnative",
        f"-DmainClass={main_class}",
        "clean",
        "package",
    ]
    logging.info("Expecting Native Image analysis failure for %s", main_class)
    result = subprocess.run(
        command,
        cwd=project_dir,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode == 0:
        raise RuntimeError(f"Native Image unexpectedly accepted {main_class}")
    if expected_text not in result.stdout:
        raise RuntimeError(
            f"Native Image failed for the wrong reason ({main_class}):\n"
            + result.stdout[-12000:]
        )


def run(task="tests"):
    """Run the selected Kotlin CI task."""
    if task == "tests":
        run_tests()
    elif task == "install":
        install_java_json()
        install_artifacts(include_corpus=True)
    elif task == "install-kotlin":
        install_artifacts(include_corpus=True)
    elif task == "native-json":
        run_native_json()
    else:
        raise ValueError(f"Unsupported Kotlin CI task: {task}")
