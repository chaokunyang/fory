#!/usr/bin/env python3
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

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


LICENSE_HEADER = """// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
"""


def snake_to_pascal(value: str) -> str:
    return "".join(part.capitalize() for part in value.split("_") if part)


def snake_to_camel(value: str) -> str:
    parts = [part for part in value.split("_") if part]
    if not parts:
        return value
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


@dataclass(frozen=True)
class FieldSpec:
    name: str
    type_expr: str
    field_id: int

    @property
    def java_property(self) -> str:
        return snake_to_camel(self.name)


SCALAR_FIELDS = [
    FieldSpec("bool_value", "bool", 1),
    FieldSpec("int8_value", "int8", 2),
    FieldSpec("int16_value", "int16", 3),
    FieldSpec("fixed_int32_value", "fixed_int32", 4),
    FieldSpec("varint32_value", "int32", 5),
    FieldSpec("fixed_int64_value", "fixed_int64", 6),
    FieldSpec("varint64_value", "int64", 7),
    FieldSpec("tagged_int64_value", "tagged_int64", 8),
    FieldSpec("uint8_value", "uint8", 9),
    FieldSpec("uint16_value", "uint16", 10),
    FieldSpec("fixed_uint32_value", "fixed_uint32", 11),
    FieldSpec("var_uint32_value", "uint32", 12),
    FieldSpec("fixed_uint64_value", "fixed_uint64", 13),
    FieldSpec("var_uint64_value", "uint64", 14),
    FieldSpec("tagged_uint64_value", "tagged_uint64", 15),
    FieldSpec("float16_value", "float16", 16),
    FieldSpec("bfloat16_value", "bfloat16", 17),
    FieldSpec("float32_value", "float32", 18),
    FieldSpec("float64_value", "float64", 19),
    FieldSpec("string_value", "string", 20),
    FieldSpec("bytes_value", "bytes", 21),
    FieldSpec("date_value", "date", 22),
    FieldSpec("timestamp_value", "timestamp", 23),
    FieldSpec("duration_value", "duration", 24),
    FieldSpec("decimal_value", "decimal", 25),
    FieldSpec("enum_value", "ExampleState", 26),
    FieldSpec("message_value", "ExampleLeaf", 27),
    FieldSpec("union_value", "ExampleLeafUnion", 28),
]

LIST_FIELDS = [
    FieldSpec("bool_list", "list<bool>", 101),
    FieldSpec("int8_list", "list<int8>", 102),
    FieldSpec("int16_list", "list<int16>", 103),
    FieldSpec("fixed_int32_list", "list<fixed_int32>", 104),
    FieldSpec("varint32_list", "list<int32>", 105),
    FieldSpec("fixed_int64_list", "list<fixed_int64>", 106),
    FieldSpec("varint64_list", "list<int64>", 107),
    FieldSpec("tagged_int64_list", "list<tagged_int64>", 108),
    FieldSpec("uint8_list", "list<uint8>", 109),
    FieldSpec("uint16_list", "list<uint16>", 110),
    FieldSpec("fixed_uint32_list", "list<fixed_uint32>", 111),
    FieldSpec("var_uint32_list", "list<uint32>", 112),
    FieldSpec("fixed_uint64_list", "list<fixed_uint64>", 113),
    FieldSpec("var_uint64_list", "list<uint64>", 114),
    FieldSpec("tagged_uint64_list", "list<tagged_uint64>", 115),
    FieldSpec("float16_list", "list<float16>", 116),
    FieldSpec("bfloat16_list", "list<bfloat16>", 117),
    FieldSpec("maybe_float16_list", "list<optional float16>", 118),
    FieldSpec("maybe_bfloat16_list", "list<optional bfloat16>", 119),
    FieldSpec("float32_list", "list<float32>", 120),
    FieldSpec("float64_list", "list<float64>", 121),
    FieldSpec("string_list", "list<string>", 122),
    FieldSpec("bytes_list", "list<bytes>", 123),
    FieldSpec("date_list", "list<date>", 124),
    FieldSpec("timestamp_list", "list<timestamp>", 125),
    FieldSpec("duration_list", "list<duration>", 126),
    FieldSpec("decimal_list", "list<decimal>", 127),
    FieldSpec("enum_list", "list<ExampleState>", 128),
    FieldSpec("message_list", "list<ExampleLeaf>", 129),
    FieldSpec("union_list", "list<ExampleLeafUnion>", 130),
]

MAP_FIELDS = [
    FieldSpec("string_values_by_bool", "map<bool, string>", 201),
    FieldSpec("string_values_by_int8", "map<int8, string>", 202),
    FieldSpec("string_values_by_int16", "map<int16, string>", 203),
    FieldSpec("string_values_by_fixed_int32", "map<fixed_int32, string>", 204),
    FieldSpec("string_values_by_varint32", "map<int32, string>", 205),
    FieldSpec("string_values_by_fixed_int64", "map<fixed_int64, string>", 206),
    FieldSpec("string_values_by_varint64", "map<int64, string>", 207),
    FieldSpec("string_values_by_tagged_int64", "map<tagged_int64, string>", 208),
    FieldSpec("string_values_by_uint8", "map<uint8, string>", 209),
    FieldSpec("string_values_by_uint16", "map<uint16, string>", 210),
    FieldSpec("string_values_by_fixed_uint32", "map<fixed_uint32, string>", 211),
    FieldSpec("string_values_by_var_uint32", "map<uint32, string>", 212),
    FieldSpec("string_values_by_fixed_uint64", "map<fixed_uint64, string>", 213),
    FieldSpec("string_values_by_var_uint64", "map<uint64, string>", 214),
    FieldSpec("string_values_by_tagged_uint64", "map<tagged_uint64, string>", 215),
    FieldSpec("string_values_by_string", "map<string, string>", 218),
    FieldSpec("string_values_by_timestamp", "map<timestamp, string>", 219),
    FieldSpec("string_values_by_duration", "map<duration, string>", 220),
    FieldSpec("string_values_by_enum", "map<ExampleState, string>", 221),
    FieldSpec("float16_values_by_name", "map<string, float16>", 222),
    FieldSpec("maybe_float16_values_by_name", "map<string, optional float16>", 223),
    FieldSpec("bfloat16_values_by_name", "map<string, bfloat16>", 224),
    FieldSpec("maybe_bfloat16_values_by_name", "map<string, optional bfloat16>", 225),
    FieldSpec("bytes_values_by_name", "map<string, bytes>", 226),
    FieldSpec("date_values_by_name", "map<string, date>", 227),
    FieldSpec("decimal_values_by_name", "map<string, decimal>", 228),
    FieldSpec("message_values_by_name", "map<string, ExampleLeaf>", 229),
    FieldSpec("union_values_by_name", "map<string, ExampleLeafUnion>", 230),
]

ALL_FIELDS = [*SCALAR_FIELDS, *LIST_FIELDS, *MAP_FIELDS]


def render_fields(fields: list[FieldSpec]) -> str:
    return "\n".join(f"    {field.type_expr} {field.name} = {field.field_id};" for field in fields)


def render_common_schema() -> str:
    return f"""{LICENSE_HEADER}

package example_common;

option go_package = "github.com/apache/fory/integration_tests/idl_tests/go/example_common/generated;example_common";

enum ExampleState [id=1504] {{
    UNKNOWN = 0;
    READY = 1;
    FAILED = 2;
}}

message ExampleLeaf [id=1502] {{
    string label = 1;
    int32 count = 2;
}}

union ExampleLeafUnion [id=1503] {{
    string note = 1;
    int32 code = 2;
    ExampleLeaf leaf = 3;
}}
"""


def render_example_schema() -> str:
    fields = render_fields(ALL_FIELDS)
    return f"""{LICENSE_HEADER}

package example;
import "example_common.fdl";

option go_package = "github.com/apache/fory/integration_tests/idl_tests/go/example/generated;example";
option evolving = false;

message ExampleMessage [id=1500, evolving=true] {{
{fields}
}}

union ExampleMessageUnion [id=1501] {{
{fields}
}}
"""


def write_if_changed(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text() == content:
        return
    path.write_text(content)
