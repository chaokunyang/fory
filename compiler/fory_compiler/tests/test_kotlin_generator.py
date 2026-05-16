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

from pathlib import Path

import pytest

from fory_compiler.cli import main as foryc_main, resolve_imports
from fory_compiler.frontend.fdl.lexer import Lexer
from fory_compiler.frontend.fdl.parser import Parser
from fory_compiler.generators.base import GeneratorOptions
from fory_compiler.generators.kotlin import KotlinGenerator
from fory_compiler.ir.validator import SchemaValidator


def generate_kotlin(source: str, package_override=None):
    schema = Parser(Lexer(source).tokenize()).parse()
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    generator = KotlinGenerator(
        schema,
        GeneratorOptions(output_dir=Path("/tmp"), package_override=package_override),
    )
    return {item.path: item.content for item in generator.generate()}


def test_emits_models():
    files = generate_kotlin(
        """
        package demo;
        option kotlin_package = "org.example.demo";

        enum Status [id=101] {
            STATUS_UNKNOWN = 0;
            STATUS_OK = 7;
        }

        message User [id=102] {
            string name = 1;
            optional int32 age = 2;
            fixed int64 fixed_id = 3;
            array<int8> bytes = 4;
        }

        union SearchTarget [id=103] {
            User user = 1;
            string note = 2;
            array<int8> bytes = 3;
        }
        """
    )

    user = files["org/example/demo/User.kt"]
    assert "package org.example.demo" in user
    assert "public data class User(" in user
    assert "public val name: String" in user
    assert "public val age: Int?" in user
    assert "public val fixedId: @Fixed Long" in user
    assert "@VarInt" not in user
    assert "@ArrayType" in user
    assert "import org.apache.fory.collection.Int8List" not in user
    assert "public val bytes: @ArrayType ByteArray" in user

    status = files["org/example/demo/Status.kt"]
    assert "public enum class Status" in status
    assert "@ForyEnumId(0)" in status
    assert "Unknown," in status
    assert "@ForyEnumId(7)" in status
    assert "Ok;" in status

    union = files["org/example/demo/SearchTarget.kt"]
    assert "@ForyUnion" in union
    assert "public sealed class SearchTarget" in union
    assert "public data class UnknownCase(" in union
    assert "@ForyCase(id = 1)" in union
    assert "public data class UserCase(public val value: User)" in union
    assert "import org.apache.fory.annotation.ArrayType" in union
    assert (
        "public data class BytesCase(public val value: @ArrayType ByteArray)" in union
    )

    registration = files["org/example/demo/DemoForyRegistration.kt"]
    assert (
        "KotlinSerializers.registerType(fory, User::class.java, 102L)" in registration
    )
    assert (
        "KotlinSerializers.registerSerializer(fory, User::class.java)" in registration
    )
    assert (
        "KotlinSerializers.registerEnum(fory, Status::class.java, 101L)" in registration
    )
    assert (
        "KotlinSerializers.registerUnion(fory, SearchTarget::class.java, 103L)"
        in registration
    )


def test_fdl_package():
    files = generate_kotlin(
        """
        package demo.models;

        message User {
            string name = 1;
        }
        """
    )

    assert "demo/models/User.kt" in files
    assert "package demo.models" in files["demo/models/User.kt"]


def test_override_rejects_import_mismatch(tmp_path):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package shared;

        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )

    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="same effective Kotlin package"):
        generator = KotlinGenerator(
            schema,
            GeneratorOptions(output_dir=tmp_path, package_override="org.override"),
        )
        generator.generate()


def test_override_reports_cli_error(tmp_path, capsys):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package shared;

        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )

    out = tmp_path / "out"
    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(out),
            "--package",
            "org.override",
            str(main),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "same effective Kotlin package" in captured.err
    assert not out.exists()


def test_override_rejects_empty_import_mismatch(tmp_path, capsys):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package shared;
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            string name = 1;
        }
        """
    )
    out = tmp_path / "out"
    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="same effective Kotlin package"):
        KotlinGenerator(
            schema,
            GeneratorOptions(output_dir=tmp_path, package_override="org.override"),
        )

    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(out),
            "--package",
            "org.override",
            str(main),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "same effective Kotlin package" in captured.err
    assert not out.exists()


def test_override_rejects_empty_import_from_include_path(tmp_path):
    include_dir = tmp_path / "include"
    include_dir.mkdir()
    common = include_dir / "common.fdl"
    common.write_text(
        """
        package shared;
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            string name = 1;
        }
        """
    )

    schema = resolve_imports(main, [include_dir])
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="same effective Kotlin package"):
        KotlinGenerator(
            schema,
            GeneratorOptions(output_dir=tmp_path, package_override="org.override"),
        )


def test_override_rejects_transitive_include_import(tmp_path):
    include_one = tmp_path / "include1"
    include_two = tmp_path / "include2"
    include_one.mkdir()
    include_two.mkdir()
    (include_one / "mid.fdl").write_text(
        """
        package app;
        import "empty.fdl";

        message Mid [id=200] {
            string name = 1;
        }
        """
    )
    (include_two / "empty.fdl").write_text(
        """
        package shared;
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "mid.fdl";

        message Holder [id=201] {
            Mid mid = 1;
        }
        """
    )

    schema = resolve_imports(main, [include_one, include_two])
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="same effective Kotlin package"):
        KotlinGenerator(
            schema,
            GeneratorOptions(output_dir=tmp_path, package_override="org.override"),
        )


def test_override_applies_to_imports(tmp_path):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package app;

        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )
    out = tmp_path / "out"

    assert (
        foryc_main(
            [
                "--lang",
                "kotlin",
                "--kotlin_out",
                str(out),
                "--package",
                "org.override",
                str(main),
            ]
        )
        == 0
    )

    holder = (out / "org/override/Holder.kt").read_text()
    common_content = (out / "org/override/Common.kt").read_text()
    registration = (out / "org/override/MainForyRegistration.kt").read_text()

    assert "package org.override" in holder
    assert "package org.override" in common_content
    assert "org.override.CommonForyRegistration.register(fory)" in registration
    assert "org.override.MainForyRegistration.register(fory)" not in registration


def test_registration_path_collision_rejected(tmp_path, capsys):
    first_dir = tmp_path / "first"
    second_dir = tmp_path / "second"
    first_dir.mkdir()
    second_dir.mkdir()
    (first_dir / "common.fdl").write_text(
        """
        package app;

        message First [id=200] {
            string name = 1;
        }
        """
    )
    (second_dir / "common.fdl").write_text(
        """
        package app;

        message Second [id=201] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "first/common.fdl";
        import "second/common.fdl";

        message Holder [id=202] {
            First first = 1;
            Second second = 2;
        }
        """
    )
    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="generated file path collision"):
        KotlinGenerator(schema, GeneratorOptions(output_dir=tmp_path))

    out = tmp_path / "out"
    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(out),
            str(main),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "generated file path collision" in captured.err
    assert not out.exists()


def test_registration_type_path_collision_rejected(tmp_path, capsys):
    schema_file = tmp_path / "demo.fdl"
    schema_file.write_text(
        """
        package app;

        message DemoForyRegistration [id=200] {
            string name = 1;
        }
        """
    )
    schema = resolve_imports(schema_file)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="generated file path collision"):
        KotlinGenerator(schema, GeneratorOptions(output_dir=tmp_path))

    out = tmp_path / "out"
    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(out),
            str(schema_file),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "generated file path collision" in captured.err
    assert not out.exists()


def test_registration_name_sanitizes_source_stem(tmp_path):
    schema = tmp_path / "123-my-schema.fdl"
    schema.write_text(
        """
        package app;

        message Entry [id=200] {
            string name = 1;
        }
        """
    )
    out = tmp_path / "out"

    assert (
        foryc_main(
            [
                "--lang",
                "kotlin",
                "--kotlin_out",
                str(out),
                str(schema),
            ]
        )
        == 0
    )

    registration = out / "app/Schema123MySchemaForyRegistration.kt"
    assert registration.is_file()
    assert "public object Schema123MySchemaForyRegistration" in registration.read_text()


def test_override_rejects_input_mismatch(tmp_path, capsys):
    app = tmp_path / "app.fdl"
    app.write_text(
        """
        package app;

        message App [id=200] {
            string name = 1;
        }
        """
    )
    shared = tmp_path / "shared.fdl"
    shared.write_text(
        """
        package shared;

        message Shared [id=201] {
            string name = 1;
        }
        """
    )

    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(tmp_path / "out"),
            "--package",
            "org.override",
            str(app),
            str(shared),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "all input schema files" in captured.err


def test_override_rejects_mixed_lang_imports(tmp_path, capsys):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package app;

        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )

    result = foryc_main(
        [
            "--lang",
            "java,kotlin",
            "--output",
            str(tmp_path / "out"),
            "--package",
            "org.override",
            str(main),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert (
        "Kotlin --package with imports requires Kotlin-only generation" in captured.err
    )


def test_rejects_mixed_default_and_named_imports(tmp_path, capsys):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )

    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    with pytest.raises(ValueError, match="default-package"):
        KotlinGenerator(schema, GeneratorOptions(output_dir=tmp_path))

    out = tmp_path / "out"
    result = foryc_main(
        [
            "--lang",
            "kotlin",
            "--kotlin_out",
            str(out),
            str(main),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert "default-package" in captured.err
    assert not out.exists()


def test_default_package_import_registers_dependency(tmp_path):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        message Common [id=200] {
            string name = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        import "common.fdl";

        message Holder [id=201] {
            Common common = 1;
        }
        """
    )

    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    generator = KotlinGenerator(schema, GeneratorOptions(output_dir=tmp_path))
    files = {item.path: item.content for item in generator.generate()}

    registration = files["MainForyRegistration.kt"]
    assert "CommonForyRegistration.register(fory)" in registration
    assert ".CommonForyRegistration.register(fory)" not in registration


def test_package_override_wins_over_kotlin_package():
    files = generate_kotlin(
        """
        package demo;
        option kotlin_package = "org.example.demo";

        message User {
            string name = 1;
        }
        """,
        package_override="org.override",
    )

    assert "org/override/User.kt" in files
    assert "package org.override" in files["org/override/User.kt"]
    assert "org/example/demo/User.kt" not in files


def test_imported_nested_int8_array_does_not_block_local_generation(tmp_path):
    common = tmp_path / "common.fdl"
    common.write_text(
        """
        package shared;

        message Imported {
            list<array<int8>> chunks = 1;
        }
        """
    )
    main = tmp_path / "main.fdl"
    main.write_text(
        """
        package app;
        import "common.fdl";

        message Holder {
            string name = 1;
        }
        """
    )

    schema = resolve_imports(main)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    generator = KotlinGenerator(schema, GeneratorOptions(output_dir=tmp_path))
    files = {item.path: item.content for item in generator.generate()}

    assert "app/Holder.kt" in files


def test_container_refs():
    files = generate_kotlin(
        """
        package graph;

        message Node [id=125] {
            string id = 1;
            list<ref Node> children = 2;
        }
        """
    )

    node = files["graph/Node.kt"]
    assert "public data class Node(" in node
    assert "public val children: List<@Ref Node>" in node
    assert "public class Node()" not in node


def test_cycle_class_fields():
    files = generate_kotlin(
        """
        package graph;

        enum Status [id=127] {
            UNKNOWN = 0;
            READY = 1;
        }

        message Node [id=126] {
            string id = 1;
            Node parent = 2;
            Status status = 3;
            duration ttl = 4;
        }
        """
    )

    node = files["graph/Node.kt"]
    assert "public class Node()" in node
    assert 'public var id: String = ""' in node
    assert "import org.apache.fory.annotation.Nullable" not in node
    assert "@ForyField(id = 2)\n    public var parent: Node? = null" in node
    assert "@ForyField(id = 3)\n    public lateinit var status: Status" in node
    assert "public var ttl: Duration = Duration.ZERO" in node
    registration = files["graph/GraphForyRegistration.kt"]
    assert (
        "KotlinSerializers.registerSerializer(fory, Node::class.java)" in registration
    )


def test_cycle_defaults():
    files = generate_kotlin(
        """
        package graph;

        message Node [id=126] {
            string id = 1;
            ref Node parent = 2;
            array<int8> payload = 3;
            any metadata = 4;
        }
        """
    )

    node = files["graph/Node.kt"]
    assert "public var payload: @ArrayType ByteArray = ByteArray(0)" in node
    assert "import org.apache.fory.annotation.Nullable" not in node
    assert "public var metadata: Any? = null" in node
    assert "public lateinit var metadata" not in node
    assert "public var payload: ByteArray = null" not in node


def test_nested_int8_array_uses_type_annotation():
    files = generate_kotlin(
        """
        package demo;

        message Holder {
            list<array<int8>> chunks = 1;
            map<string, array<int8>> chunks_by_name = 2;
        }
        """
    )

    holder = files["demo/Holder.kt"]
    assert "import org.apache.fory.annotation.ArrayType" in holder
    assert "public val chunks: List<@ArrayType ByteArray>" in holder
    assert "public val chunksByName: Map<String, @ArrayType ByteArray>" in holder
