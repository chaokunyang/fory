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

import unittest
from unittest import mock

if __package__:
    from .tasks import kotlin
else:
    from tasks import kotlin


class KotlinTaskTest(unittest.TestCase):
    @mock.patch.object(kotlin.common, "exec_cmd")
    @mock.patch.object(kotlin.common, "cd_project_subdir")
    @mock.patch.object(kotlin, "java_major_version", return_value=25)
    def test_jdk25_compiles_verifier(
        self, _java_major_version, _cd_project_subdir, exec_cmd
    ):
        kotlin.install_java_json()

        command = exec_cmd.call_args.args[0]
        self.assertIn("-pl fory-json,fory-annotation-processor", command)
        self.assertIn("-DskipTests", command)
        self.assertNotIn("-Dmaven.test.skip=true", command)
        self.assertNotIn("-Dmaven.source.skip=true", command)

    @mock.patch.object(kotlin.common, "exec_cmd")
    @mock.patch.object(kotlin.common, "cd_project_subdir")
    @mock.patch.object(kotlin, "java_major_version", return_value=17)
    def test_pre_jdk25_skips_test_compile(
        self, _java_major_version, _cd_project_subdir, exec_cmd
    ):
        kotlin.install_java_json()

        command = exec_cmd.call_args.args[0]
        self.assertIn("-Dmaven.test.skip=true", command)
        self.assertIn("-Dmaven.source.skip=true", command)


if __name__ == "__main__":
    unittest.main()
