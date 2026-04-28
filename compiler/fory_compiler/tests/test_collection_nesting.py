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

"""Tests for collection nesting validation."""

from fory_compiler.frontend.fdl.lexer import Lexer
from fory_compiler.frontend.fdl.parser import Parser
from fory_compiler.ir.validator import SchemaValidator


def parse_schema(source: str):
    return Parser(Lexer(source).tokenize()).parse()


def assert_nested_collections_rejected(source: str) -> None:
    schema = parse_schema(source)
    validator = SchemaValidator(schema)
    assert not validator.validate()
    assert any(
        "nested list/map types are not allowed" in err.message
        for err in validator.errors
    )


def assert_validation_error(source: str, expected_message: str) -> None:
    schema = parse_schema(source)
    validator = SchemaValidator(schema)
    assert not validator.validate()
    assert any(expected_message in err.message for err in validator.errors)


def test_nested_list_rejected():
    source = """
    message Foo {
        list<list<int32>> values = 1;
    }
    """
    assert_nested_collections_rejected(source)


def test_list_of_map_rejected():
    source = """
    message Foo {
        list<map<string, int32>> values = 1;
    }
    """
    assert_nested_collections_rejected(source)


def test_map_with_list_value_rejected():
    source = """
    message Foo {
        map<string, list<int32>> values = 1;
    }
    """
    assert_nested_collections_rejected(source)


def test_map_with_map_value_rejected():
    source = """
    message Foo {
        map<string, map<string, int32>> values = 1;
    }
    """
    assert_nested_collections_rejected(source)


def test_map_with_list_key_rejected():
    source = """
    message Foo {
        map<list<int32>, int32> values = 1;
    }
    """
    assert_nested_collections_rejected(source)


def test_map_with_float16_key_rejected():
    source = """
    message Foo {
        map<float16, string> values = 1;
    }
    """
    assert_validation_error(source, "map keys cannot use floating-point types")


def test_map_with_bytes_key_rejected():
    source = """
    message Foo {
        map<bytes, string> values = 1;
    }
    """
    assert_validation_error(source, "map keys cannot use bytes/binary types")


def test_map_with_message_key_rejected():
    source = """
    message Key {
        string name = 1;
    }

    message Foo {
        map<Key, string> values = 1;
    }
    """
    assert_validation_error(source, "map keys cannot use message or union types")


def test_map_with_union_key_rejected():
    source = """
    union Key {
        string value = 1;
    }

    message Foo {
        map<Key, string> values = 1;
    }
    """
    assert_validation_error(source, "map keys cannot use message or union types")


def test_map_with_nested_enum_key_rejected():
    source = """
    message Foo {
        enum Status {
            UNKNOWN = 0;
            READY = 1;
        }

        map<Status, string> values = 1;
    }
    """
    assert_validation_error(source, "map keys cannot use nested types")


def test_map_with_top_level_enum_and_timestamp_keys_allowed():
    source = """
    enum Status {
        UNKNOWN = 0;
        READY = 1;
    }

    message Foo {
        map<Status, string> by_status = 1;
        map<timestamp, string> by_timestamp = 2;
    }
    """
    schema = parse_schema(source)
    validator = SchemaValidator(schema)
    assert validator.validate(), validator.errors
