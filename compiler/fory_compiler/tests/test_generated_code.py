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

"""Tests for generated code consistency across frontends."""

from pathlib import Path
from textwrap import dedent
from typing import Dict, Tuple, Type

from fory_compiler.frontend.fbs import FBSFrontend
from fory_compiler.frontend.fdl.lexer import Lexer
from fory_compiler.frontend.fdl.parser import Parser
from fory_compiler.frontend.proto import ProtoFrontend
from fory_compiler.cli import resolve_imports
from fory_compiler.generators.base import BaseGenerator, GeneratorOptions
from fory_compiler.generators.cpp import CppGenerator
from fory_compiler.generators.go import GoGenerator
from fory_compiler.generators.java import JavaGenerator
from fory_compiler.generators.python import PythonGenerator
from fory_compiler.generators.rust import RustGenerator
from fory_compiler.generators.csharp import CSharpGenerator
from fory_compiler.generators.javascript import JavaScriptGenerator
from fory_compiler.generators.swift import SwiftGenerator
from fory_compiler.generators.dart import DartGenerator
from fory_compiler.ir.ast import Schema


GENERATOR_CLASSES: Tuple[Type[BaseGenerator], ...] = (
    JavaGenerator,
    PythonGenerator,
    CppGenerator,
    RustGenerator,
    GoGenerator,
    CSharpGenerator,
    JavaScriptGenerator,
    SwiftGenerator,
    DartGenerator,
)


def parse_fdl(source: str) -> Schema:
    return Parser(Lexer(source).tokenize()).parse()


def parse_proto(source: str) -> Schema:
    return ProtoFrontend().parse(source)


def parse_fbs(source: str) -> Schema:
    return FBSFrontend().parse(source)


def generate_files(
    schema: Schema, generator_cls: Type[BaseGenerator]
) -> Dict[str, str]:
    options = GeneratorOptions(output_dir=Path("/tmp"))
    generator = generator_cls(schema, options)
    return {item.path: item.content for item in generator.generate()}


def render_files(files: Dict[str, str]) -> str:
    return "\n".join(content for _, content in sorted(files.items()))


def assert_language_outputs_equal(
    schemas: Dict[str, Schema], generator_cls: Type[BaseGenerator]
) -> None:
    baseline_label = None
    baseline_files: Dict[str, str] = {}
    for label, schema in schemas.items():
        files = generate_files(schema, generator_cls)
        if baseline_label is None:
            baseline_label = label
            baseline_files = files
            continue
        assert files == baseline_files, (
            f"{generator_cls.language_name} output mismatch for {label} vs {baseline_label}"
        )


def assert_all_languages_equal(schemas: Dict[str, Schema]) -> None:
    for generator_cls in GENERATOR_CLASSES:
        assert_language_outputs_equal(schemas, generator_cls)


def test_generated_code_scalar_types_equivalent():
    fdl = dedent(
        """
        package gen;

        message ScalarTypes {
            bool active = 1;
            int32 i32 = 2;
            int64 i64 = 3;
            uint32 u32 = 4;
            uint64 u64 = 5;
            float32 f32 = 6;
            float64 f64 = 7;
            string name = 8;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message ScalarTypes {
            bool active = 1;
            sint32 i32 = 2;
            sint64 i64 = 3;
            uint32 u32 = 4;
            uint64 u64 = 5;
            float f32 = 6;
            double f64 = 7;
            string name = 8;
        }
        """
    )
    fbs = dedent(
        """
        namespace gen;

        table ScalarTypes {
            active:bool;
            i32:int;
            i64:long;
            u32:uint;
            u64:ulong;
            f32:float;
            f64:double;
            name:string;
        }
        """
    )
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
        "fbs": parse_fbs(fbs),
    }
    assert_all_languages_equal(schemas)


def test_generated_code_integer_encoding_variants_equivalent():
    fdl = dedent(
        """
        package gen;

        message EncodingTypes {
            fixed_int32 fi32 = 1;
            fixed_int64 fi64 = 2;
            fixed_uint32 fu32 = 3;
            fixed_uint64 fu64 = 4;
            tagged_int64 ti64 = 5;
            tagged_uint64 tu64 = 6;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message EncodingTypes {
            sfixed32 fi32 = 1;
            sfixed64 fi64 = 2;
            fixed32 fu32 = 3;
            fixed64 fu64 = 4;
            int64 ti64 = 5 [(fory).type = "tagged_int64"];
            uint64 tu64 = 6 [(fory).type = "tagged_uint64"];
        }
        """
    )
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
    }
    assert_all_languages_equal(schemas)

    python_output = render_files(generate_files(schemas["fdl"], PythonGenerator))
    assert "pyfory.tagged_int64" in python_output
    assert "pyfory.tagged_uint64" in python_output


def test_generated_code_list_modifier_aliases_equivalent():
    repeated = dedent(
        """
        package gen;

        message Item {
            string name = 1;
        }

        message Container {
            repeated string tags = 1;
            optional repeated string labels = 2;
            repeated optional string aliases = 3;
            ref repeated Item items = 4;
            repeated ref Item children = 5;
        }
        """
    )
    list_syntax = dedent(
        """
        package gen;

        message Item {
            string name = 1;
        }

        message Container {
            list<string> tags = 1;
            optional list<string> labels = 2;
            list<optional string> aliases = 3;
            ref list<Item> items = 4;
            list<ref Item> children = 5;
        }
        """
    )
    schemas = {
        "repeated": parse_fdl(repeated),
        "list": parse_fdl(list_syntax),
    }
    assert_all_languages_equal(schemas)


def test_generated_code_primitive_arrays_equivalent():
    fdl = dedent(
        """
        package gen;

        message ArrayTypes {
            repeated bool flags = 1;
            repeated int8 i8s = 2;
            repeated int16 i16s = 3;
            repeated int32 i32s = 4;
            repeated int64 i64s = 5;
            repeated uint8 u8s = 6;
            repeated uint16 u16s = 7;
            repeated uint32 u32s = 8;
            repeated uint64 u64s = 9;
            repeated float32 f32s = 10;
            repeated float64 f64s = 11;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message ArrayTypes {
            repeated bool flags = 1;
            repeated int8 i8s = 2;
            repeated int16 i16s = 3;
            repeated sint32 i32s = 4;
            repeated sint64 i64s = 5;
            repeated uint8 u8s = 6;
            repeated uint16 u16s = 7;
            repeated uint32 u32s = 8;
            repeated uint64 u64s = 9;
            repeated float f32s = 10;
            repeated double f64s = 11;
        }
        """
    )
    fbs = dedent(
        """
        namespace gen;

        table ArrayTypes {
            flags:[bool];
            i8s:[byte];
            i16s:[short];
            i32s:[int];
            i64s:[long];
            u8s:[ubyte];
            u16s:[ushort];
            u32s:[uint];
            u64s:[ulong];
            f32s:[float];
            f64s:[double];
        }
        """
    )
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
        "fbs": parse_fbs(fbs),
    }
    assert_all_languages_equal(schemas)


def test_generated_code_list_types_equivalent():
    fdl = dedent(
        """
        package gen;

        message ListItem {
            string value = 1;
        }

        message ListTypes {
            repeated string names = 1;
            repeated bool flags = 2;
            repeated ListItem items = 3;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message ListItem {
            string value = 1;
        }

        message ListTypes {
            repeated string names = 1;
            repeated bool flags = 2;
            repeated ListItem items = 3;
        }
        """
    )
    fbs = dedent(
        """
        namespace gen;

        table ListItem {
            value:string;
        }

        table ListTypes {
            names:[string];
            flags:[bool];
            items:[ListItem];
        }
        """
    )
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
        "fbs": parse_fbs(fbs),
    }
    assert_all_languages_equal(schemas)


def test_generated_code_map_types_equivalent():
    fdl = dedent(
        """
        package gen;

        message MapValue {
            string id = 1;
        }

        message MapTypes {
            map<string, int32> counts = 1;
            optional map<string, MapValue> entries = 2;
            map<string, ref(weak=true, thread_safe=false) MapValue> weak_entries = 3;
            optional int32 version = 4;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message MapValue {
            string id = 1;
        }

        message MapTypes {
            map<string, sint32> counts = 1;
            map<string, MapValue> entries = 2 [(fory).nullable = true];
            map<string, MapValue> weak_entries = 3 [
                (fory).ref = true,
                (fory).weak_ref = true,
                (fory).thread_safe_pointer = false
            ];
            optional sint32 version = 4;
        }
        """
    )
    # FlatBuffers does not support maps, compare FDL vs proto only.
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
    }
    assert_all_languages_equal(schemas)

    rust_output = render_files(generate_files(schemas["fdl"], RustGenerator))
    assert "RcWeak<MapValue>" in rust_output
    assert "Option<i32>" in rust_output

    cpp_output = render_files(generate_files(schemas["fdl"], CppGenerator))
    assert "SharedWeak<MapValue>" in cpp_output


def test_generated_code_nested_messages_equivalent():
    fdl = dedent(
        """
        package gen;

        message Outer {
            string id = 1;

            message Inner {
                string value = 1;
            }

            Inner inner = 2;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package gen;

        message Outer {
            string id = 1;

            message Inner {
                string value = 1;
            }

            Inner inner = 2;
        }
        """
    )
    # FlatBuffers does not support nested message declarations.
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
    }
    assert_all_languages_equal(schemas)


def test_generated_code_tree_ref_options_equivalent():
    fdl = dedent(
        """
        package tree;

        message TreeNode {
            string id = 1;
            string name = 2;

            repeated ref TreeNode children = 3;
            ref(weak=true) TreeNode parent = 4;
        }
        """
    )
    proto = dedent(
        """
        syntax = "proto3";
        package tree;

        message TreeNode {
            string id = 1;
            string name = 2;

            repeated TreeNode children = 3 [(fory).ref = true];
            TreeNode parent = 4 [(fory).weak_ref = true];
        }
        """
    )
    fbs = dedent(
        """
        namespace tree;

        table TreeNode {
            id: string;
            name: string;
            children: [TreeNode] (fory_ref: true);
            parent: TreeNode (fory_weak_ref: true);
        }
        """
    )
    # Tree ref options should produce identical outputs across frontends.
    schemas = {
        "fdl": parse_fdl(fdl),
        "proto": parse_proto(proto),
        "fbs": parse_fbs(fbs),
    }
    assert_all_languages_equal(schemas)

    rust_output = render_files(generate_files(schemas["fdl"], RustGenerator))
    assert "ArcWeak<TreeNode>" in rust_output

    cpp_output = render_files(generate_files(schemas["fdl"], CppGenerator))
    assert "SharedWeak<TreeNode>" in cpp_output


def test_java_float16_equals_hash_contract_generation():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            message Float16Contract {
                float16 f16 = 1;
                optional float16 opt_f16 = 2;
            }
            """
        )
    )
    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "Objects.equals(f16, that.f16)" in java_output
    assert "Objects.equals(optF16, that.optF16)" in java_output
    assert "return Objects.hash(f16, optF16);" in java_output


def test_java_repeated_float16_generation_uses_float16_list():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            message RepeatedFloat16 {
                list<float16> vals = 1;
            }
            """
        )
    )
    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "import org.apache.fory.collection.Float16List;" in java_output
    assert "private Float16List vals;" in java_output


def test_java_imported_ref_types_use_simple_names_in_generic_annotations(tmp_path: Path):
    common_path = tmp_path / "common.fdl"
    common_path.write_text(
        dedent(
            """
            package common;

            message Node {
                string name = 1;
            }

            union Choice {
                Node node = 1;
            }
            """
        )
    )
    root_path = tmp_path / "root.fdl"
    root_path.write_text(
        dedent(
            """
            package root;
            import "common.fdl";

            message Holder {
                list<Node> nodes = 1;
                map<string, Choice> choices = 2;
            }
            """
        )
    )

    java_output = render_files(generate_files(resolve_imports(root_path), JavaGenerator))
    assert "import common.Node;" in java_output
    assert "import common.Choice;" in java_output
    assert "private List<@Ref(enable=false) Node> nodes;" in java_output
    assert "private Map<String, @Ref(enable=false) Choice> choices;" in java_output
    assert "@Ref(enable=false) common.Node" not in java_output
    assert "@Ref(enable=false) common.Choice" not in java_output


def test_java_union_float16_list_wrapping_uses_packed_short_arrays():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            union Example {
                list<float16> f16s = 1;
                list<bfloat16> bf16s = 2;
                list<optional float16> maybe_f16s = 3;
                list<optional bfloat16> maybe_bf16s = 4;
            }
            """
        )
    )

    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "if (value instanceof short[]) {" in java_output
    assert "value = new Float16List((short[]) value);" in java_output
    assert "value = new BFloat16List((short[]) value);" in java_output
    assert "new Float16List((Float16[]) value)" not in java_output
    assert "new BFloat16List((BFloat16[]) value)" not in java_output


def test_java_numeric_container_annotations_are_emitted():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            message ContainerKinds {
                list<i32> fixed_int32s = 1;
                list<vi32> varint32s = 2;
                map<i32, string> names_by_fixed_int32 = 3;
                map<vu64, string> names_by_var_uint64 = 4;
                map<u8, string> names_by_uint8 = 5;
            }
            """
        )
    )

    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "import org.apache.fory.annotation.Int32Type;" in java_output
    assert "import org.apache.fory.annotation.Uint8Type;" in java_output
    assert "import org.apache.fory.annotation.Uint64Type;" in java_output
    assert "private Int32List fixedInt32s;" in java_output
    assert "private Int32List varint32s;" in java_output
    assert (
        "private Map<@Int32Type(compress = false) Integer, String> namesByFixedInt32;"
        in java_output
    )
    assert (
        "private Map<@Uint64Type(encoding = LongEncoding.VARINT) Long, String>"
        " namesByVarUint64;" in java_output
    )
    assert "private Map<@Uint8Type Integer, String> namesByUint8;" in java_output


def test_cpp_generator_supports_decimal_fields_and_unions():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            message Money {
                decimal amount = 1;
            }

            union Value {
                decimal amount = 1;
                Money money = 2;
            }
            """
        )
    )

    cpp_output = render_files(generate_files(schema, CppGenerator))
    assert '#include "fory/serialization/decimal_serializers.h"' in cpp_output
    assert "const fory::serialization::Decimal& amount() const" in cpp_output
    assert "std::variant<fory::serialization::Decimal, Money> value_" in cpp_output
    assert "(fory::serialization::Decimal, amount, fory::F(1))" in cpp_output


def test_cpp_union_generation_uses_variant_indices_for_duplicate_carriers():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            union EncodedNumber {
                fixed_int32 fixed_i32 = 1;
                int32 var_i32 = 2;
                fixed_int64 fixed_i64 = 3;
                int64 var_i64 = 4;
            }
            """
        )
    )

    cpp_output = render_files(generate_files(schema, CppGenerator))
    assert "std::variant<int32_t, int32_t, int64_t, int64_t> value_" in cpp_output
    assert "return EncodedNumber(std::in_place_index<0>, std::move(v));" in cpp_output
    assert "return EncodedNumber(std::in_place_index<1>, std::move(v));" in cpp_output
    assert "return EncodedNumber(std::in_place_index<2>, std::move(v));" in cpp_output
    assert "return EncodedNumber(std::in_place_index<3>, std::move(v));" in cpp_output
    assert "return value_.index() == 0;" in cpp_output
    assert "return value_.index() == 1;" in cpp_output
    assert "return std::get_if<0>(&value_);" in cpp_output
    assert "return std::get_if<1>(&value_);" in cpp_output
    assert "return std::get<2>(value_);" in cpp_output
    assert "return std::get<3>(value_);" in cpp_output
    assert "std::holds_alternative<int32_t>" not in cpp_output
    assert "std::get_if<int32_t>" not in cpp_output
    assert "std::get<int32_t>" not in cpp_output
    assert "std::in_place_type<int32_t>" not in cpp_output


def test_cpp_large_union_case_macros_wrap_template_types():
    fields = "\n".join(f"                int32 value_{i} = {i};" for i in range(1, 17))
    schema = parse_fdl(
        dedent(
            f"""
            package gen;

            union BigValue {{
{fields}
                map<string, optional float16> by_name = 17;
            }}
            """
        )
    )

    cpp_output = render_files(generate_files(schema, CppGenerator))
    assert "FORY_UNION_IDS(gen::BigValue, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);" in cpp_output
    assert (
        "FORY_UNION_CASE(gen::BigValue, 17, "
        "FORY_UNION_TYPE(std::map<std::string, std::optional<fory::float16_t>>), "
        "gen::BigValue::by_name, fory::F(17));"
    ) in cpp_output


def test_java_enum_generation_uses_fory_enum_ids():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            enum Status {
                UNKNOWN = 4096;
                OK = 8192;
            }
            """
        )
    )
    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "import org.apache.fory.annotation.ForyEnumId;" in java_output
    assert "public enum Status {" in java_output
    assert "UNKNOWN(4096)," in java_output
    assert "OK(8192);" in java_output
    assert "private final int id;" in java_output
    assert "Status(int id) {" in java_output
    assert "this.id = id;" in java_output
    assert "@ForyEnumId" in java_output
    assert "public int getId() {" in java_output
    assert "return id;" in java_output


def test_go_bfloat16_generation():
    idl = dedent(
        """
        package bfloat16_test;

        message BFloat16Message {
            bfloat16 val = 1;
            optional bfloat16 opt_val = 2;
            list<bfloat16> list_val = 3;
        }
        """
    )
    schema = parse_fdl(idl)
    files = generate_files(schema, GoGenerator)

    assert len(files) == 1
    content = list(files.values())[0]

    # Check imports
    assert 'bfloat16 "github.com/apache/fory/go/fory/bfloat16"' in content

    # Check fields
    assert '\tVal bfloat16.BFloat16 `fory:"id=1"`' in content
    assert (
        '\tOptVal optional.Optional[bfloat16.BFloat16] `fory:"id=2,nullable"`'
        in content
    )
    assert "\tListVal []bfloat16.BFloat16" in content


def test_generators_support_extended_xlang_scalar_and_array_types():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            message Item {
                string name = 1;
            }

            message AllTypes {
                bfloat16 bf16 = 1;
                duration span = 2;
                date day = 3;
                timestamp instant = 4;
                decimal amount = 5;
                list<float16> f16s = 6;
                list<bfloat16> bf16s = 7;
                list<optional float16> maybe_f16s = 8;
                list<optional bfloat16> maybe_bf16s = 9;
                map<string, float16> by_name = 10;
                map<string, optional bfloat16> maybe_by_name = 11;
                map<string, optional ref Item> maybe_items = 12;
            }

            union AllUnion {
                bfloat16 bf16 = 1;
                duration span = 2;
                date day = 3;
                timestamp instant = 4;
                decimal amount = 5;
                list<float16> f16s = 6;
                list<bfloat16> bf16s = 7;
                Item item = 8;
            }
            """
        )
    )

    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "import org.apache.fory.type.BFloat16;" in java_output
    assert "import org.apache.fory.collection.BFloat16List;" in java_output
    assert "Types.BFLOAT16" in java_output
    assert "Types.BFLOAT16_ARRAY" in java_output
    assert "Types.DURATION" in java_output
    assert "Types.DECIMAL" in java_output

    python_output = render_files(generate_files(schema, PythonGenerator))
    assert "pyfory.bfloat16" in python_output
    assert "datetime.timedelta" in python_output
    assert "pyfory.float16array" in python_output
    assert "pyfory.bfloat16array" in python_output
    assert "Dict[str, Optional[pyfory.bfloat16]]" in python_output

    cpp_output = render_files(generate_files(schema, CppGenerator))
    assert '#include "fory/util/bfloat16.h"' in cpp_output
    assert '#include "fory/serialization/temporal_serializers.h"' in cpp_output
    assert "fory::bfloat16_t bf16" in cpp_output
    assert "fory::serialization::Duration span" in cpp_output
    assert "std::map<std::string, std::optional<fory::bfloat16_t>> maybe_by_name" in cpp_output

    rust_output = render_files(generate_files(schema, RustGenerator))
    assert "pub bf16: fory::BFloat16" in rust_output
    assert "pub span: chrono::Duration" in rust_output
    assert "pub f16s: Vec<fory::Float16>" in rust_output
    assert "pub bf16s: Vec<fory::BFloat16>" in rust_output
    assert "pub maybe_by_name: HashMap<String, Option<fory::BFloat16>>" in rust_output

    go_output = render_files(generate_files(schema, GoGenerator))
    assert 'bfloat16 "github.com/apache/fory/go/fory/bfloat16"' in go_output
    assert '"time"' in go_output
    assert "\tSpan time.Duration `fory:\"id=2\"`" in go_output
    assert "\tBf16s []bfloat16.BFloat16" in go_output
    assert "\tMaybeByName map[string]*bfloat16.BFloat16" in go_output
    assert "TypeID: fory.BFLOAT16" in go_output
    assert "TypeID: fory.DURATION" in go_output
    assert "TypeID: fory.FLOAT16_ARRAY" in go_output
    assert "TypeID: fory.BFLOAT16_ARRAY" in go_output

    csharp_output = render_files(generate_files(schema, CSharpGenerator))
    assert "public BFloat16 Bf16 { get; set; }" in csharp_output
    assert "public TimeSpan Span { get; set; }" in csharp_output
    assert "public ForyDecimal Amount { get; set; }" in csharp_output
    assert "Dictionary<string, BFloat16?> MaybeByName" in csharp_output

    javascript_output = render_files(generate_files(schema, JavaScriptGenerator))
    assert "import { Decimal } from '@apache-fory/core';" in javascript_output
    assert "import { BFloat16 } from '@apache-fory/core';" in javascript_output
    assert "bf16: BFloat16 | number;" in javascript_output
    assert "maybeByName: Map<string, BFloat16 | number | null>;" in javascript_output
    assert "Type.bfloat16()" in javascript_output
    assert "Type.duration()" in javascript_output

    swift_output = render_files(generate_files(schema, SwiftGenerator))
    assert "public var span: Duration = Duration.foryDefault()" in swift_output
    assert "public var bf16: BFloat16 = BFloat16.foryDefault()" in swift_output
    assert "case span(Duration)" in swift_output

    dart_output = render_files(generate_files(schema, DartGenerator))
    assert "Bfloat16 bf16 = const Bfloat16.fromBits(0);" in dart_output
    assert "Duration span = Duration.zero;" in dart_output
    assert "Float16List f16s = Float16List(0);" in dart_output
    assert "Bfloat16List bf16s = Bfloat16List(0);" in dart_output
    assert "Map<String, Bfloat16?> maybeByName = <String, Bfloat16?>{};" in dart_output


def test_java_union_optional_half_float_lists_preserve_list_type():
    schema = parse_fdl(
        dedent(
            """
            package gen;

            union Value {
                list<optional float16> maybe_f16s = 1;
                list<optional bfloat16> maybe_bf16s = 2;
            }
            """
        )
    )

    java_output = render_files(generate_files(schema, JavaGenerator))
    assert "public static Value ofMaybeF16s(List<Float16> v)" in java_output
    assert "public static Value ofMaybeBf16s(List<BFloat16> v)" in java_output
    assert "return Types.LIST;" in java_output
    assert "Types.FLOAT16_ARRAY" not in java_output
    assert "Types.BFLOAT16_ARRAY" not in java_output
