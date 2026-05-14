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
import re
import subprocess
from . import common


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


def run():
    """Run Kotlin CI tasks."""
    logging.info("Executing fory kotlin tests")
    common.cd_project_subdir("kotlin")

    # The KSP Maven plugin discovers processors from JAR artifacts. Build and install the
    # processor first so the generated-test module does not see the reactor classes directory as
    # its processor artifact.
    common.exec_cmd(
        "mvn -T16 --batch-mode --no-transfer-progress "
        "-pl fory-kotlin,fory-kotlin-ksp -am -DskipTests install"
    )
    if java_major_version() >= 17:
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress test -DfailIfNoTests=false"
        )
    else:
        logging.info(
            "Skipping fory-kotlin-tests on JDK < 17 because ksp-maven-plugin requires Java 17+"
        )
        common.exec_cmd(
            "mvn -T16 --batch-mode --no-transfer-progress "
            "-pl fory-kotlin,fory-kotlin-ksp -am test -DfailIfNoTests=false"
        )

    logging.info("Executing fory kotlin tests succeeds")
