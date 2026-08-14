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
    from . import release
else:
    import release


class ReleaseDocVersionTest(unittest.TestCase):
    def test_updates_release_doc_dependencies(self):
        lines = [
            "python -m pip install pyfory==1.5.0\n",
            'python -m pip install "pyfory[format]==1.5.0"\n',
            "go get github.com/apache/fory/go/fory@v1.5.0\n",
            "npm install @apache-fory/core@1.5.0 @apache-fory/hps@1.5.0\n",
            "python -m pip install pyfory==1.5.0 grpcio==1.73.0 # Fory 1.4.0\n",
            "go get github.com/apache/fory/go/fory@v1.5.0 # Fory 1.4.0\n",
            "npm install @apache-fory/core@1.5.0 grpc@1.73.0 @apache-fory/hps@1.5.0\n",
            'fory = "1.5.0"\n',
            "  fory: 1.5.0\n",
            '    .package(url: "https://github.com/apache/fory.git", exact: "1.5.0")\n',
            'implementation("org.apache.fory:fory-core:1.5.0")\n',
            'libraryDependencies += "org.apache.fory" %% "fory-scala" % "1.5.0"\n',
            'libraryDependencies += "org.apache.fory" %% "fory-json-scala" % "1.5.0"\n',
            'implementation("io.grpc:grpc-api:1.73.0")\n',
            "The wire format was introduced in version 1.5.0.\n",
        ]
        expected = [
            "python -m pip install pyfory==1.6.0\n",
            'python -m pip install "pyfory[format]==1.6.0"\n',
            "go get github.com/apache/fory/go/fory@v1.6.0\n",
            "npm install @apache-fory/core@1.6.0 @apache-fory/hps@1.6.0\n",
            "python -m pip install pyfory==1.6.0 grpcio==1.73.0 # Fory 1.4.0\n",
            "go get github.com/apache/fory/go/fory@v1.6.0 # Fory 1.4.0\n",
            "npm install @apache-fory/core@1.6.0 grpc@1.73.0 @apache-fory/hps@1.6.0\n",
            'fory = "1.6.0"\n',
            "  fory: 1.6.0\n",
            '    .package(url: "https://github.com/apache/fory.git", exact: "1.6.0")\n',
            'implementation("org.apache.fory:fory-core:1.6.0")\n',
            'libraryDependencies += "org.apache.fory" %% "fory-scala" % "1.6.0"\n',
            'libraryDependencies += "org.apache.fory" %% "fory-json-scala" % "1.6.0"\n',
            'implementation("io.grpc:grpc-api:1.73.0")\n',
            "The wire format was introduced in version 1.5.0.\n",
        ]

        updated = release._update_release_doc_lines(lines, "1.6.0")

        self.assertEqual(expected, updated)
        self.assertEqual(expected, release._update_release_doc_lines(updated, "1.6.0"))


class ScalaReleaseTest(unittest.TestCase):
    @mock.patch.object(release, "_run_release_cmd")
    def test_publishes_each_module(self, run_release_cmd):
        release._publish_scala()

        self.assertEqual(
            [
                mock.call("sbt clean", "scala"),
                mock.call("sbt 'project fory-scala' +publishSigned", "scala"),
                mock.call("sbt 'project fory-json-scala' +publishSigned", "scala"),
                mock.call("sbt sonatypePrepare", "scala"),
                mock.call("sbt sonatypeBundleUpload", "scala"),
            ],
            run_release_cmd.call_args_list,
        )

    @mock.patch.object(release, "_run_release_cmd")
    def test_snapshot_has_no_signing_or_staging(self, run_release_cmd):
        release._publish_java("snapshot")
        release._publish_kotlin("snapshot")
        release._publish_scala("snapshot")

        commands = [call.args[0] for call in run_release_cmd.call_args_list]
        self.assertIn("-Dgpg.skip=true", commands[0])
        self.assertIn("-Dgpg.skip=true", commands[1])
        self.assertIn("-Psnapshot-publication", commands[0])
        self.assertIn("-Psnapshot-publication", commands[1])
        self.assertFalse(any("publishSigned" in command for command in commands))
        self.assertFalse(any("sonatypePrepare" in command for command in commands))
        self.assertFalse(any("sonatypeBundleUpload" in command for command in commands))


class JvmPublicationTest(unittest.TestCase):
    def test_language_order(self):
        self.assertEqual(
            ["java", "scala"], release._jvm_release_langs("scala,java,scala")
        )

    @mock.patch.dict(release.os.environ, {}, clear=True)
    def test_missing_credentials(self):
        with self.assertRaisesRegex(RuntimeError, "NEXUS_USERNAME, NEXUS_PASSWORD"):
            release._require_publication_authority("snapshot")

    @mock.patch.object(release, "_has_gpg_secret_key", return_value=False)
    @mock.patch.dict(
        release.os.environ,
        {"NEXUS_USERNAME": "user", "NEXUS_PASSWORD": "password"},
        clear=True,
    )
    def test_release_signing_authority(self, _has_gpg_secret_key):
        with self.assertRaisesRegex(RuntimeError, "GPG secret key"):
            release._require_publication_authority("release")

    @mock.patch.dict(
        release.os.environ,
        {
            "NEXUS_USERNAME": "nexus-user",
            "NEXUS_PASSWORD": "nexus-password",
            "SONATYPE_USERNAME": "stale-user",
            "SONATYPE_PASSWORD": "stale-password",
        },
        clear=True,
    )
    def test_snapshot_owns_scala_credentials(self):
        release._require_publication_authority("snapshot")

        self.assertEqual("nexus-user", release.os.environ["SONATYPE_USERNAME"])
        self.assertEqual("nexus-password", release.os.environ["SONATYPE_PASSWORD"])

    @mock.patch.object(release, "verify_kotlin_artifacts")
    @mock.patch.object(release, "_verify_fory_core_mr_jar")
    @mock.patch.object(release, "_publish_scala")
    @mock.patch.object(release, "_publish_kotlin")
    @mock.patch.object(release, "_publish_java")
    @mock.patch.object(release, "_ensure_openjdk25")
    @mock.patch.object(release, "_require_publication_authority")
    def test_snapshot_reactor_order(
        self,
        require_authority,
        ensure_openjdk25,
        publish_java,
        publish_kotlin,
        publish_scala,
        verify_core,
        verify_kotlin,
    ):
        release.publish_jvm(mode="snapshot")

        require_authority.assert_called_once_with("snapshot")
        ensure_openjdk25.assert_called_once_with()
        publish_java.assert_called_once_with("snapshot")
        publish_kotlin.assert_called_once_with("snapshot")
        publish_scala.assert_called_once_with("snapshot")
        verify_kotlin.assert_called_once_with()
        verify_core.assert_called_once_with()


class KotlinVersionTest(unittest.TestCase):
    def test_android_dependencies(self):
        lines = [
            "implementation 'org.apache.fory:fory-json-kotlin:1.7.0-SNAPSHOT'\n",
            "ksp 'org.apache.fory:fory-json-kotlin-ksp:1.7.0-SNAPSHOT'\n",
            "implementation 'org.apache.fory:kotlin-json-corpus:1.7.0-SNAPSHOT'\n",
            "`org.apache.fory:fory-json-kotlin:1.7.0-SNAPSHOT`\n",
            "`org.apache.fory:fory-json-kotlin-ksp:1.7.0-SNAPSHOT`\n",
        ]

        self.assertEqual(
            [
                "implementation 'org.apache.fory:fory-json-kotlin:1.8.0-SNAPSHOT'\n",
                "ksp 'org.apache.fory:fory-json-kotlin-ksp:1.8.0-SNAPSHOT'\n",
                "implementation 'org.apache.fory:kotlin-json-corpus:1.8.0-SNAPSHOT'\n",
                "`org.apache.fory:fory-json-kotlin:1.8.0-SNAPSHOT`\n",
                "`org.apache.fory:fory-json-kotlin-ksp:1.8.0-SNAPSHOT`\n",
            ],
            release._update_android_kotlin_version(lines, "1.8.0-SNAPSHOT"),
        )

    def test_benchmark_version(self):
        lines = ["kotlinVersion=2.3.20\n", "foryVersion=1.7.0-SNAPSHOT\n"]

        self.assertEqual(
            ["kotlinVersion=2.3.20\n", "foryVersion=1.8.0-SNAPSHOT\n"],
            release._update_kotlin_benchmark_version(lines, "1.8.0-SNAPSHOT"),
        )

    @mock.patch.object(release, "_bump_version")
    def test_kotlin_version_paths(self, bump_version):
        release.bump_kotlin_version("1.8.0-SNAPSHOT")

        paths = {(call.args[0], call.args[1]) for call in bump_version.call_args_list}
        expected_paths = {
            ("kotlin", "pom.xml"),
            ("kotlin/fory-kotlin", "pom.xml"),
            ("kotlin/fory-kotlin-ksp", "pom.xml"),
            ("kotlin/fory-json-kotlin", "pom.xml"),
            ("kotlin/fory-json-kotlin-ksp", "pom.xml"),
            ("kotlin/fory-kotlin-tests", "pom.xml"),
            ("integration_tests/kotlin_json_corpus", "pom.xml"),
            ("integration_tests/graalvm_kotlin_tests", "pom.xml"),
            ("integration_tests/grpc_tests/kotlin", "pom.xml"),
            ("integration_tests/idl_tests/kotlin", "pom.xml"),
            ("integration_tests/android_tests", "build.gradle"),
            ("integration_tests/android_tests", "README.md"),
            ("benchmarks/kotlin", "gradle.properties"),
            ("kotlin/fory-json-kotlin", "README.md"),
            ("kotlin/fory-json-kotlin-ksp", "README.md"),
            ("docs/json", "kotlin.md"),
        }
        self.assertEqual(expected_paths, paths)
        self.assertEqual(len(expected_paths), len(bump_version.call_args_list))
        self.assertIn("kotlin/README.md", release.RELEASE_DOC_ROOTS)


if __name__ == "__main__":
    unittest.main()
