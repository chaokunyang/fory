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
from fory_compiler.generators.dart import DartGenerator
from fory_compiler.ir.validator import SchemaValidator


def parse_schema(source: str):
    schema = Parser(Lexer(source).tokenize()).parse()
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
    return schema


def generate_dart(source: str):
    schema = parse_schema(source)
    generator = DartGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
    files = generator.generate()
    assert len(files) == 1
    return files[0]


def test_dart_generator_emits_annotated_structs_and_generated_part_registration():
    file = generate_dart(
        """
        package demo;

        message Scalar [id=100] {
            fixed_int32 fixed_value = 1;
            int32 varint_value = 2;
            tagged_uint64 tagged_value = 3;
        }
        """
    )

    assert "part 'demo.fory.dart';" in file.content
    assert "@ForyStruct()" in file.content
    assert "final class Scalar {" in file.content
    assert "@Int32Type(encoding: Encoding.fixed)" in file.content
    assert "@Uint64Type(encoding: Encoding.tagged)" in file.content
    assert "@ForyField(id: 1)" in file.content
    assert (
        "DemoFory.register(fory, Scalar, id: registrationMode.id, namespace: registrationMode.namespace, typeName: registrationMode.typeName);"
        in file.content
    )
    assert "GeneratedStructRegistration<Scalar>" not in file.content
    assert "_ScalarForySerializer" not in file.content


def test_dart_generator_keeps_enum_helpers_in_source_and_uses_generated_enum_registration():
    file = generate_dart(
        """
        package demo;

        enum Status [id=101] {
            STATUS_UNKNOWN = 0;
            STATUS_OK = 7;
        }
        """
    )

    assert "enum Status {" in file.content
    assert "int get rawValue => switch (this) {" in file.content
    assert "Status.ok => 7," in file.content
    assert "static Status fromRawValue(int value) => switch (value) {" in file.content
    assert "_StatusForySerializer" not in file.content
    assert (
        "DemoFory.register(fory, Status, id: registrationMode.id, namespace: registrationMode.namespace, typeName: registrationMode.typeName);"
        in file.content
    )


def test_dart_generator_keeps_union_serializers_direct_and_marks_union_types():
    file = generate_dart(
        """
        package demo;

        message Node [id=100] {
            string id = 1;
        }

        union Animal [id=101] {
            Node node = 3;
            string note = 7;
        }
        """
    )

    assert "@ForyUnion()" in file.content
    assert "final class Animal {" in file.content
    assert "factory Animal.node(Node value)" in file.content
    assert (
        "final class _AnimalForySerializer extends UnionSerializer<Animal>"
        in file.content
    )
    assert "int caseId(Animal value) => value.caseId;" in file.content
    assert "Object? caseValue(Animal value) => value.value;" in file.content
    assert "Animal buildValue(int caseId, Object? value) {" in file.content
    assert "context.writeVarUint32(value.caseId);" not in file.content
    assert "Animal read(ReadContext context) {" not in file.content
    assert (
        "fory.registerSerializer(Animal, const _AnimalForySerializer(), id: registrationMode.id, namespace: registrationMode.namespace, typeName: registrationMode.typeName);"
        in file.content
    )


def test_dart_generator_uses_plain_int_for_small_unsigned_scalars():
    file = generate_dart(
        """
        package demo;

        message UnsignedHolder {
            uint8 u8 = 1;
            uint16 u16 = 2;
            fixed_uint32 fixed_u32 = 3;
            uint32 var_u32 = 4;
            map<uint8, string> names_by_u8 = 5;
        }

        union UnsignedValue {
            uint16 u16 = 1;
            fixed_uint32 fixed_u32 = 2;
        }
        """
    )

    assert "@Uint8Type()" in file.content
    assert "int u8 = 0;" in file.content
    assert "@Uint16Type()" in file.content
    assert "int u16 = 0;" in file.content
    assert "@Uint32Type(encoding: Encoding.fixed)" in file.content
    assert "int fixedU32 = 0;" in file.content
    assert "int varU32 = 0;" in file.content
    assert "Map<int, String> namesByU8 = <int, String>{};" in file.content
    assert "factory UnsignedValue.u16(int value)" in file.content
    assert "factory UnsignedValue.fixedU32(int value)" in file.content


def test_dart_generator_uses_typed_lists_for_non_nullable_primitive_lists():
    file = generate_dart(
        """
        package demo;

        message Holder {
            list<int32> ints = 1;
            list<optional int32> nullable_ints = 2;
            optional list<int32> maybe_ints = 3;
        }

        union ValueUnion {
            list<uint32> values = 1;
        }
        """
    )

    assert "Int32List ints = Int32List(0);" in file.content
    assert "List<int?> nullableInts = <int?>[];" in file.content
    assert "Int32List? maybeInts = null;" in file.content
    assert "factory ValueUnion.values(Uint32List value)" in file.content


def test_dart_generator_emits_uint8_array_wire_override_for_uint8_lists():
    file = generate_dart(
        """
        package demo;

        message Holder {
            list<uint8> uint8_values = 1;
            bytes bytes_value = 2;
        }
        """
    )

    assert "@ForyField(id: 1, type: Uint8ArrayType())" in file.content
    assert "Uint8List uint8Values = Uint8List(0);" in file.content
    assert "@ForyField(id: 2)" in file.content
    assert "Uint8List bytesValue = Uint8List(0);" in file.content


def test_dart_generator_supports_decimal_fields_and_unions():
    file = generate_dart(
        """
        package demo;

        message Money [id=100] {
            decimal amount = 1;
        }

        union ValueUnion [id=101] {
            decimal amount = 1;
            Money money = 2;
        }
        """
    )

    assert "Decimal amount = Decimal.zero();" in file.content
    assert "factory ValueUnion.amount(Decimal value)" in file.content


def test_dart_generator_supports_bfloat16_duration_and_nullable_map_values():
    file = generate_dart(
        """
        package demo;

        message Holder {
            bfloat16 bf16 = 1;
            duration span = 2;
            list<float16> f16s = 3;
            list<bfloat16> bf16s = 4;
            map<string, optional bfloat16> maybe_by_name = 5;
        }

        union ValueUnion {
            duration span = 1;
            bfloat16 bf16 = 2;
        }
        """
    )

    assert "Bfloat16 bf16 = const Bfloat16.fromBits(0);" in file.content
    assert "Duration span = Duration.zero;" in file.content
    assert "Float16List f16s = Float16List(0);" in file.content
    assert "Bfloat16List bf16s = Bfloat16List(0);" in file.content
    assert "Map<String, Bfloat16?> maybeByName = <String, Bfloat16?>{};" in file.content
    assert "factory ValueUnion.span(Duration value)" in file.content
    assert "factory ValueUnion.bf16(Bfloat16 value)" in file.content


def test_dart_generator_uses_current_timestamp_default_value():
    file = generate_dart(
        """
        package demo;

        message Holder {
            timestamp created_at = 1;
        }
        """
    )

    assert "Timestamp createdAt = Timestamp(Int64(0), 0);" in file.content


def test_dart_generator_emits_container_ref_annotations_for_builder_metadata():
    file = generate_dart(
        """
        package demo;

        message Node {
            list<ref Node> children = 1;
            map<string, ref Node> by_name = 2;
            ref Node parent = 3;
        }
        """
    )

    assert "@ForyField(id: 1)" in file.content
    assert "@ListField(element: DeclaredType(ref: true))" in file.content
    assert "@ForyField(id: 2)" in file.content
    assert "@MapField(value: DeclaredType(ref: true))" in file.content
    assert "@ForyField(id: 3, ref: true)" in file.content


def test_dart_generator_marks_map_value_ref_messages_as_ref_capable():
    file = generate_dart(
        """
        package demo;

        message Node {
            map<string, ref Node> by_name = 1;
        }
        """
    )

    assert "@ForyField(id: 1)" in file.content
    assert "@MapField(value: DeclaredType(ref: true))" in file.content
    assert "Map<String, Node> byName = <String, Node>{};" in file.content


def test_dart_generator_emits_container_wire_type_annotations_for_fixed_map_types():
    file = generate_dart(
        """
        package demo;

        message Holder {
            map<fixed_int32, string> names_by_fixed_i32 = 1;
            map<fixed_uint32, fixed_int32> fixed_values = 2;
            map<tagged_int64, fixed_uint64> names_by_tagged_i64 = 3;
            map<uint64, string> names_by_var_u64 = 4;
        }
        """
    )

    assert "@MapField(key: Int32Type(encoding: Encoding.fixed))" in file.content
    assert (
        "@MapField(key: Uint32Type(encoding: Encoding.fixed), value: Int32Type(encoding: Encoding.fixed))"
        in file.content
    )
    assert (
        "@MapField(key: Int64Type(encoding: Encoding.tagged), value: Uint64Type(encoding: Encoding.fixed))"
        in file.content
    )
    assert "@MapField(key: Uint64Type())" in file.content


def test_dart_generator_preserves_multi_word_enum_value_suffixes():
    file = generate_dart(
        """
        package demo;

        enum Status [id=101] {
            STATUS_SOME_MULTI_WORD = 0;
            STATUS_OK = 1;
        }
        """
    )

    assert "someMultiWord," in file.content
    assert "ok;" in file.content
    assert " word," not in file.content
    assert " word;" not in file.content


def test_dart_generator_flattens_nested_type_references_and_keeps_classes_final():
    file = generate_dart(
        """
        package demo;

        message Envelope {
            enum Status {
                STATUS_UNKNOWN = 0;
                STATUS_OK = 1;
            }

            message Payload {
                Status status = 1;
            }

            union Detail {
                Payload payload = 1;
                string note = 2;
            }

            Payload payload = 1;
            Detail detail = 2;
        }
        """
    )

    assert "enum Envelope_Status {" in file.content
    assert "final class Envelope_Payload {" in file.content
    assert "Envelope_Status status = Envelope_Status.unknown;" in file.content
    assert "@ForyUnion()" in file.content
    assert "final class Envelope_Detail {" in file.content
    assert "final class Envelope {" in file.content


def test_dart_generator_uses_name_registration_when_auto_id_disabled():
    file = generate_dart(
        """
        option enable_auto_type_id = false;
        package demo;

        message Envelope {
            message Payload {
                string note = 1;
            }
        }
        """
    )

    assert "defaultNamespace: 'demo'," in file.content
    assert "defaultTypeName: 'Envelope'," in file.content
    assert "defaultTypeName: 'Envelope.Payload'," in file.content
    assert (
        "DemoFory.register(fory, Envelope_Payload, id: registrationMode.id, namespace: registrationMode.namespace, typeName: registrationMode.typeName);"
        in file.content
    )


def test_dart_generator_output_path_uses_package_segments_and_package_leaf():
    file = generate_dart(
        """
        package demo.foo;

        message User [id=1] {
            string name = 1;
        }
        """
    )
    assert file.path == "demo/foo/demo_foo.dart"

    schema = parse_schema(
        """
        package any_example_pb;

        message AnyInner [id=300] {
            string name = 1;
        }
        """
    )
    schema.source_file = "/tmp/any_example.proto"
    generator = DartGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
    file = generator.generate()[0]
    assert file.path == "any_example_pb/any_example_pb.dart"
    assert "part 'any_example_pb.fory.dart';" in file.content


def test_dart_generator_supports_imported_registration_calls_without_fallthrough_throw():
    repo_root = Path(__file__).resolve().parents[3]
    idl_dir = repo_root / "integration_tests" / "idl_tests" / "idl"
    schema = resolve_imports(idl_dir / "root.idl", [idl_dir])
    generator = DartGenerator(schema, GeneratorOptions(output_dir=Path("/tmp")))
    file = generator.generate()[0]
    assert "import '../addressbook/addressbook.dart' as addressbook;" in file.content
    assert "try {" in file.content
    assert (
        "addressbook.ForyRegistration.register(fory, type, id: id, namespace: namespace, typeName: typeName);"
        in file.content
    )
    assert "return;" in file.content
    assert "} on ArgumentError {" in file.content
    assert "addressbook.AddressBook" in file.content
