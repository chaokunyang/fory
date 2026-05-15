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
from fory_compiler.generators.scala import ScalaGenerator
from fory_compiler.ir.validator import SchemaValidator


def generate_scala(source: str):
    schema = Parser(Lexer(source).tokenize()).parse()
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    generator = ScalaGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
    return {item.path: item.content for item in generator.generate()}


def test_scala_generator_emits_case_classes_options_enums_and_unions():
    files = generate_scala(
        """
        package demo;

        enum Status [id=101] {
            STATUS_UNKNOWN = 0;
            STATUS_OK = 7;
        }

        message User [id=102] {
            string name = 1;
            optional int32 age = 2;
            list<string> tags = 3;
        }

        union SearchTarget [id=103] {
            User user = 1;
            string note = 2;
        }
        """
    )

    user = files["demo/User.scala"]
    assert "final case class User(" in user
    assert "@ForyField(id = 1) name: String" in user
    assert "@ForyField(id = 2) age: Option[Int]" in user
    assert "@ForyField(id = 3) tags: List[String]" in user
    assert "derives ForySerializer" in user

    status = files["demo/Status.scala"]
    assert "enum Status(val foryId: Int)" in status
    assert "case Unknown extends Status(0)" in status
    assert "case Ok extends Status(7)" in status
    assert "@ForyEnumId" in status

    union = files["demo/SearchTarget.scala"]
    assert "@ForyUnion" in union
    assert "enum SearchTarget derives ForySerializer" in union
    assert "@ForyCase(id = 0)" in union
    assert "case UnknownCase(caseId: Int, value: Any)" in union
    assert "@ForyCase(id = 1)" in union
    assert "case UserCase(value: User)" in union
    assert "@ForyCase(id = 2)" in union
    assert "case NoteCase(value: String)" in union


def test_scala_generator_uses_mutable_normal_class_for_construction_cycles():
    files = generate_scala(
        """
        package graph;

        message Node [id=110] {
            string id = 1;
            ref Node parent = 2;
        }
        """
    )

    node = files["graph/Node.scala"]
    assert "final class Node() derives ForySerializer" in node
    assert 'var id: String = ""' in node
    assert "var parent: Option[Node @Ref] = None" in node


def test_scala_generator_keeps_imported_types_in_owner_package():
    repo_root = Path(__file__).resolve().parents[3]
    idl_dir = repo_root / "integration_tests" / "idl_tests" / "idl"
    schema = resolve_imports(idl_dir / "root.idl", [idl_dir])
    generator = ScalaGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
    files = {item.path: item.content for item in generator.generate()}

    assert "root/MultiHolder.scala" in files
    assert "root/PrimitiveTypes.scala" not in files
    assert "addressbook.AddressBook" in files["root/MultiHolder.scala"]
    assert "tree.TreeNode" in files["root/MultiHolder.scala"]

    registration = files["root/RootForyRegistration.scala"]
    assert "addressbook.AddressbookForyRegistration.register(fory)" in registration
    assert "tree.TreeForyRegistration.register(fory)" in registration
    assert "classOf[PrimitiveTypes]" not in registration
