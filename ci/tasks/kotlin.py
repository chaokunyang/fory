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
CORPUS_RULE_MODELS = (
    "PlatformAccount",
    "PlatformAnnotated",
    "PlatformBox",
    "PlatformBuiltins",
    "PlatformCase",
    "PlatformCaseManifest",
    "PlatformCircle",
    "PlatformCodecSlots",
    "PlatformEnvelope",
    "PlatformGenericKey",
    "PlatformKotlinProfile",
    "PlatformMarker",
    "PlatformNode",
    "PlatformNullableText",
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
    "PlatformInvalidPropertyShape",
    "PlatformPropertyShape",
    "PlatformWrappedShape",
)
CORPUS_MIXIN_TARGETS = {
    "PlatformCodecSlotsMixin": "PlatformCodecSlots",
    "PlatformJavaProfileMixin": "PlatformJavaProfile",
    "PlatformKotlinProfileMixin": "PlatformKotlinProfile",
}
CORPUS_CODEC_TYPES = (
    "PlatformContentStringCodec",
    "PlatformElementStringCodec",
    "PlatformIntKeyCodec",
    "PlatformMapValueStringCodec",
    "PlatformWholeStringCodec",
)
PLATFORM_BUILTINS_PARAMETERS = (
    "kotlin.Pair,kotlin.Triple,byte,short,int,long,byte[],short[],int[],long[],"
    "java.util.Map,java.util.Map,java.util.Map,java.util.Map,long,long,long,long,long,"
    "kotlin.time.Instant,kotlin.time.Instant,kotlin.time.Instant,kotlin.time.Instant,"
    "kotlin.uuid.Uuid,kotlin.Unit,kotlin.Unit,java.lang.Void,kotlin.ranges.IntRange,"
    "kotlin.ranges.UIntRange,kotlin.ranges.IntProgression,kotlin.ranges.ULongProgression,"
    "kotlin.time.TimedValue"
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
    """Verify exact KSP consumer-rule packaging in the shared platform corpus JAR."""
    module_dir = Path(
        common.PROJECT_ROOT_DIR, "integration_tests", "kotlin_json_corpus"
    )
    version = _kotlin_version()
    jar_path = module_dir / "target" / f"kotlin-json-corpus-{version}.jar"
    if not jar_path.is_file():
        raise FileNotFoundError(f"Missing Kotlin JSON corpus artifact: {jar_path}")

    expected_rules = {
        f"META-INF/proguard/fory-json-{CORPUS_PACKAGE}.{model}.pro"
        for model in CORPUS_RULE_MODELS
    }
    expected_rules.update(
        f"META-INF/proguard/fory-json-mixin-{CORPUS_PACKAGE}.{model}.pro"
        for model in CORPUS_MIXIN_TARGETS
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
        missing = sorted(expected_rules - names)
        if missing:
            raise RuntimeError(f"{jar_path} is missing consumer rules: {missing}")
        actual_rules = {
            name
            for name in names
            if name.startswith("META-INF/proguard/fory-json-") and name.endswith(".pro")
        }
        if actual_rules != expected_rules:
            raise RuntimeError(
                f"{jar_path} has unexpected consumer rules: "
                f"{sorted(actual_rules ^ expected_rules)}"
            )
        for model in CORPUS_RULE_MODELS:
            name = f"META-INF/proguard/fory-json-{CORPUS_PACKAGE}.{model}.pro"
            _verify_rule(jar, name, model)
        _verify_builtin_creator_rule(jar)
        for mixin, target in CORPUS_MIXIN_TARGETS.items():
            name = f"META-INF/proguard/fory-json-mixin-{CORPUS_PACKAGE}.{mixin}.pro"
            _verify_rule(jar, name, mixin)
            _verify_rule(jar, name, target)
        for name in (
            f"META-INF/proguard/fory-json-{CORPUS_PACKAGE}.PlatformCodecSlots.pro",
            f"META-INF/proguard/fory-json-mixin-{CORPUS_PACKAGE}.PlatformCodecSlotsMixin.pro",
        ):
            for codec in CORPUS_CODEC_TYPES:
                _verify_codec_rule(jar, name, codec)


def _verify_rule(jar, name, model):
    lines = jar.read(name).decode("utf-8").splitlines()
    exact_keep = f"-keep,allowoptimization class {CORPUS_PACKAGE}.{model}"
    if exact_keep not in lines:
        raise RuntimeError(
            f"{jar.filename}!/{name} does not retain exact model {model}"
        )


def _verify_codec_rule(jar, name, codec):
    text = jar.read(name).decode("utf-8")
    lines = text.splitlines()
    codec_type = f"{CORPUS_PACKAGE}.{codec}"
    class_rule = f"-keep,allowoptimization,allowobfuscation class {codec_type}"
    member_rule = f"-keepclassmembers class {codec_type} {{\n  public <init>();\n}}"
    if class_rule not in lines or member_rule not in text:
        raise RuntimeError(
            f"{jar.filename}!/{name} does not retain the public constructor of {codec}"
        )


def _verify_builtin_creator_rule(jar):
    name = f"META-INF/proguard/fory-json-{CORPUS_PACKAGE}.PlatformBuiltins.pro"
    text = jar.read(name).decode("utf-8")
    owner = f"{CORPUS_PACKAGE}.PlatformBuiltins"
    header = f"-keepclassmembers class {owner} {{\n"
    if text.count(header) != 1:
        raise RuntimeError(f"{jar.filename}!/{name} must have one exact member block")
    start = text.index(header) + len(header)
    end = text.index("}\n", start)
    block = text[start:end]
    constructors = (
        f"  <init>({PLATFORM_BUILTINS_PARAMETERS});",
        "  <init>("
        f"{PLATFORM_BUILTINS_PARAMETERS},kotlin.jvm.internal.DefaultConstructorMarker);",
    )
    if any(block.count(constructor) != 1 for constructor in constructors):
        raise RuntimeError(
            f"{jar.filename}!/{name} does not retain both exact Kotlin constructors"
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
        "Inline JSON subtype requires the default object representation",
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
