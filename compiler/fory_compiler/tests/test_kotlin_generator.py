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

from fory_compiler.cli import resolve_imports
from fory_compiler.frontend.fdl.lexer import Lexer
from fory_compiler.frontend.fdl.parser import Parser
from fory_compiler.generators.base import GeneratorOptions
from fory_compiler.generators.kotlin import KotlinGenerator
from fory_compiler.ir.validator import SchemaValidator


def generate_kotlin(source: str):
    schema = Parser(Lexer(source).tokenize()).parse()
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    generator = KotlinGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
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
    assert "@field:ArrayType" in user
    assert "import org.apache.fory.collection.Int8List" in user
    assert "public val bytes: Int8List" in user

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
        "public data class BytesCase(@field:ArrayType public val value: Int8List)"
        in union
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


def test_package_override_imports(tmp_path):
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
    generator = KotlinGenerator(
        schema,
        GeneratorOptions(output_dir=tmp_path, package_override="org.override"),
    )
    files = {item.path: item.content for item in generator.generate()}

    holder = files["org/override/app/Holder.kt"]
    assert "public val common: org.override.shared.Common" in holder
    registration = files["org/override/app/AppForyRegistration.kt"]
    assert "org.override.shared.SharedForyRegistration.register(fory)" in registration


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
    assert "import org.apache.fory.annotation.Nullable" in node
    assert "@ForyField(id = 2)\n    public var parent: @Nullable Node? = null" in node
    assert "@ForyField(id = 3)\n    public lateinit var status: Status" in node
    assert "public var ttl: Duration = Duration.ZERO" in node


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
    assert "public var payload: Int8List = Int8List(0)" in node
    assert "public var metadata: @Nullable Any? = null" in node
    assert "public lateinit var metadata" not in node
    assert "public var payload: Int8List = null" not in node
