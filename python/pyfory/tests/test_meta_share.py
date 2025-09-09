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

import pytest
import dataclasses
from pyfory import Fory, Language
from pyfory.buffer import Buffer
from pyfory.type import TypeId


@dataclasses.dataclass
class SimpleDataClass:
    name: str
    age: int
    active: bool


@dataclasses.dataclass
class SimpleNestedDataClass:
    value: int
    name: str


class TestMetaShareMode:
    
    def setup_method(self):
        """Setup method to register dataclasses for each test."""
        pass
    
    def test_meta_share_enabled(self):
        """Test that meta share mode can be enabled."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        assert fory.serialization_context.scoped_meta_share_enabled
        assert fory.serialization_context.meta_context is not None

    def test_meta_share_disabled(self):
        """Test that meta share mode can be disabled."""
        fory = Fory(language=Language.XLANG, meta_share=False)
        assert not fory.serialization_context.scoped_meta_share_enabled
        assert fory.serialization_context.meta_context is None

    def test_simple_dataclass_serialization(self):
        """Test serialization of simple dataclass with meta share."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        
        # Register the dataclass
        fory.register_type(SimpleDataClass)
        
        obj = SimpleDataClass(name="test", age=25, active=True)
        buffer = fory.serialize(obj)
        
        # Deserialize
        deserialized = fory.deserialize(buffer)
        assert deserialized.name == obj.name
        assert deserialized.age == obj.age
        assert deserialized.active == obj.active

    def test_multiple_objects_same_type(self):
        """Test that multiple objects of same type reuse type definition."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        
        # Register the dataclass
        fory.register_type(SimpleDataClass)
        
        obj1 = SimpleDataClass(name="test1", age=25, active=True)
        obj2 = SimpleDataClass(name="test2", age=30, active=False)
        
        # Serialize both objects
        buffer1 = fory.serialize(obj1)
        buffer2 = fory.serialize(obj2)
        
        # Create a new fory instance with the same meta context for deserialization
        fory2 = Fory(language=Language.XLANG, meta_share=True)
        fory2.register_type(SimpleDataClass)
        # Copy the meta context from the first fory instance
        fory2.serialization_context.meta_context = fory.serialization_context.meta_context
        
        # Deserialize both
        deserialized1 = fory2.deserialize(buffer1)
        deserialized2 = fory2.deserialize(buffer2)
        
        assert deserialized1.name == obj1.name
        assert deserialized2.name == obj2.name
        assert deserialized1.age == obj1.age
        assert deserialized2.age == obj2.age

    def test_simple_nested_dataclass_serialization(self):
        """Test serialization of simple nested dataclass with meta share."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        
        # Register the dataclass
        fory.register_type(SimpleNestedDataClass)
        
        obj = SimpleNestedDataClass(value=42, name="test")
        
        buffer = fory.serialize(obj)
        deserialized = fory.deserialize(buffer)
        
        assert deserialized.value == obj.value
        assert deserialized.name == obj.name

    def test_meta_context_type_mapping(self):
        """Test that meta context properly maps types to IDs."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        meta_context = fory.serialization_context.meta_context
        
        # Register the dataclass
        fory.register_type(SimpleDataClass)
        
        obj = SimpleDataClass(name="test", age=25, active=True)
        buffer = fory.serialize(obj)
        
        # Check that type was added to meta context
        type_id = meta_context.get_type_id(SimpleDataClass)
        assert type_id is not None
        assert type_id >= 0

    def test_serialization_without_meta_share(self):
        """Test that serialization works without meta share mode."""
        fory = Fory(language=Language.XLANG, meta_share=False)
        
        # Register the dataclass
        fory.register_type(SimpleDataClass)
        
        obj = SimpleDataClass(name="test", age=25, active=True)
        buffer = fory.serialize(obj)
        deserialized = fory.deserialize(buffer)
        
        assert deserialized.name == obj.name
        assert deserialized.age == obj.age
        assert deserialized.active == obj.active

    def test_meta_context_reset(self):
        """Test that meta context is properly reset."""
        fory = Fory(language=Language.XLANG, meta_share=True)
        meta_context = fory.serialization_context.meta_context
        
        # Register the dataclass
        fory.register_type(SimpleDataClass)
        
        obj = SimpleDataClass(name="test", age=25, active=True)
        fory.serialize(obj)
        
        # Check that type was added
        type_id = meta_context.get_type_id(SimpleDataClass)
        assert type_id is not None
        
        # Reset and check that type mapping is preserved (meta share behavior)
        fory.reset_write()
        type_id_after_reset = meta_context.get_type_id(SimpleDataClass)
        assert type_id_after_reset is not None  # Should be preserved in meta share mode
