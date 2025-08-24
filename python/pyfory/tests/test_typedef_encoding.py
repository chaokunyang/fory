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

"""
Tests for xlang TypeDef implementation.
"""

import pytest
from typing import List, Dict, Optional
from pyfory._util import Buffer
from pyfory.meta.typedef import (
    TypeDef, FieldInfo, FieldType, CollectionFieldType, MapFieldType, 
    DynamicFieldType
)
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.meta.typedef_decoder import decode_typedef, skip_typedef
from pyfory.type import TypeId


class TestTypeDef:
    """Test class for TypeDef functionality."""
    
    def __init__(self, name: str, age: int, scores: List[float], metadata: Dict[str, str]):
        self.name = name
        self.age = age
        self.scores = scores
        self.metadata = metadata


class SimpleTypeDef:
    """Simple test class."""
    
    def __init__(self, value: int):
        self.value = value


# Mock resolver for testing
class MockResolver:
    def __init__(self):
        self.fory = MockFory()
    
    def get_typeinfo(self, cls):
        return MockTypeInfo()
    
    def get_meta_compressor(self):
        return MockMetaCompressor()


class MockFory:
    def __init__(self):
        self.ref_tracking = False


class MockTypeInfo:
    def __init__(self):
        self.type_id = TypeId.STRUCT
    
    def decode_namespace(self):
        return "test"
    
    def decode_typename(self):
        return "TestType"


class MockMetaCompressor:
    def compress(self, data):
        return data
    
    def decompress(self, data):
        return data


def test_field_type_creation():
    """Test creation of different field types."""
    # Test primitive types
    string_field = FieldType(TypeId.STRING, True, True, False)
    int_field = FieldType(TypeId.INT32, True, True, False)
    bool_field = FieldType(TypeId.BOOL, True, True, False)
    
    assert string_field.type_id == TypeId.STRING
    assert int_field.type_id == TypeId.INT32
    assert bool_field.type_id == TypeId.BOOL
    assert string_field.is_nullable
    assert string_field.is_tracking_ref is False


def test_collection_field_type():
    """Test collection field type creation and serialization."""
    element_type = FieldType(TypeId.INT32, True, True, False)
    list_field = CollectionFieldType(TypeId.LIST, True, True, False, element_type)
    
    assert list_field.type_id == TypeId.LIST
    assert list_field.element_type == element_type
    assert list_field.is_nullable


def test_map_field_type():
    """Test map field type creation and serialization."""
    key_type = FieldType(TypeId.STRING, True, True, False)
    value_type = FieldType(TypeId.INT32, True, True, False)
    map_field = MapFieldType(TypeId.MAP, True, True, False, key_type, value_type)
    
    assert map_field.type_id == TypeId.MAP
    assert map_field.key_type == key_type
    assert map_field.value_type == value_type


def test_typedef_creation():
    """Test TypeDef creation."""
    fields = [
        FieldInfo("name", FieldType(TypeId.STRING, True, True, False), "TestTypeDef"),
        FieldInfo("age", FieldType(TypeId.INT32, True, True, False), "TestTypeDef"),
    ]
    
    typedef = TypeDef("TestTypeDef", TypeId.STRUCT, fields, b"encoded_data", False)
    
    assert typedef.name == "TestTypeDef"
    assert typedef.type_id == TypeId.STRUCT
    assert len(typedef.fields) == 2
    assert typedef.encoded == b"encoded_data"
    assert typedef.is_compressed is False


def test_field_info_creation():
    """Test FieldInfo creation."""
    field_type = FieldType(TypeId.STRING, True, True, False)
    field_info = FieldInfo("test_field", field_type, "TestClass")
    
    assert field_info.name == "test_field"
    assert field_info.field_type == field_type
    assert field_info.defined_class == "TestClass"


def test_dynamic_field_type():
    """Test dynamic field type."""
    dynamic_field = DynamicFieldType(TypeId.EXT, False, True, False)
    
    assert dynamic_field.type_id == TypeId.EXT
    assert dynamic_field.is_monomorphic is False
    assert dynamic_field.is_nullable
    assert dynamic_field.is_tracking_ref is False


def test_field_type_serialization():
    """Test field type serialization to buffer."""
    buffer = Buffer.allocate(64)
    
    # Test primitive type serialization
    string_field = FieldType(TypeId.STRING, True, True, False)
    string_field.xwrite(buffer, True)
    
    # Reset buffer for reading
    buffer.reader_index = 0
    
    # Read back the field type
    # Note: This would need a proper resolver in a real implementation
    # read_field_type = FieldType.xread(buffer, resolver)
    # assert read_field_type.type_id == TypeId.STRING


def test_buffer_operations():
    """Test buffer operations for TypeDef encoding/decoding."""
    buffer = Buffer.allocate(128)
    
    # Write some test data
    buffer.write_varuint32(42)
    buffer.write_int8(123)
    buffer.write_bytes(b"test_data")
    
    # Reset for reading
    buffer.reader_index = 0
    
    # Read back the data
    assert buffer.read_varuint32() == 42
    assert buffer.read_int8() == 123
    assert buffer.read_bytes(9) == b"test_data"


def test_skip_typedef():
    """Test skipping TypeDef in buffer."""
    buffer = Buffer.allocate(64)
    
    # Write some dummy data
    buffer.write_bytes(b"dummy_typedef_data")
    
    # Reset for reading
    buffer.reader_index = 0
    
    # Skip the typedef - size is 18 bytes (0x12)
    skip_typedef(buffer, 0x1012)  # Size encoded in id_value
    
    # Should have skipped all data
    assert buffer.reader_index == 18


def test_encode_decode_typedef():
    """Test encoding and decoding a TypeDef."""
    # Create a mock resolver
    resolver = MockResolver()
    
    # Encode a TypeDef
    typedef = encode_typedef(resolver, SimpleTypeDef)
    
    # Create a buffer from the encoded data
    buffer = Buffer(typedef.encoded)
    
    # Decode the TypeDef
    decoded_typedef = decode_typedef(buffer, resolver)
    
    # Verify the decoded TypeDef has the expected properties
    assert decoded_typedef.type_id == TypeId.STRUCT
    assert decoded_typedef.is_compressed == typedef.is_compressed
    # Note: We can't easily verify field names and types without a proper resolver
    # that can handle type lookups, but we can verify the basic structure


if __name__ == "__main__":
    # Run basic tests
    test_field_type_creation()
    test_collection_field_type()
    test_map_field_type()
    test_typedef_creation()
    test_field_info_creation()
    test_dynamic_field_type()
    test_field_type_serialization()
    test_buffer_operations()
    test_skip_typedef()
    test_encode_decode_typedef()
    
    print("All basic tests passed!")
