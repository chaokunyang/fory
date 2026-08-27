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

import marshal
import types

import pytest

import pyfory
from pyfory.policy import DEFAULT_POLICY
from pyfory.serialization import Buffer
from pyfory.serializer import FunctionSerializer


def test_function_globals_carrier():
    def local_func():
        return None

    writer = pyfory.Fory(xlang=False)
    buffer = Buffer.allocate(256)
    try:
        writer.write_context.prepare(buffer)
        buffer.write_int8(2)
        buffer.write_string(local_func.__module__)
        buffer.write_string(local_func.__qualname__)
        buffer.write_bytes_and_size(marshal.dumps(local_func.__code__))
        buffer.write_bool(False)
        buffer.write_bool(False)
        buffer.write_var_uint32(0)
        buffer.write_var_uint32(0)
        writer.write_context.write_ref([])
        writer.write_context.write_ref({})
        data = buffer.to_bytes(0, buffer.get_writer_index())
    finally:
        writer.reset_write()

    reader = pyfory.Fory(xlang=False, strict=False)
    serializer = FunctionSerializer(reader.type_resolver, types.FunctionType)
    try:
        reader.read_context.prepare(Buffer(data))
        with pytest.raises(Exception):
            reader.read_context.read_non_ref(serializer)
    finally:
        reader.reset_read()

    class DictSubclass(dict):
        def __len__(self):
            raise AssertionError("dict subclass operations must not run")

    class FunctionReadContext:
        policy = DEFAULT_POLICY

        def __init__(self):
            self._strings = iter((local_func.__module__, local_func.__qualname__))

        def read_int8(self):
            return 2

        def read_string(self):
            return next(self._strings)

        def read_bytes_and_size(self):
            return marshal.dumps(local_func.__code__)

        def reserve_graph_memory(self, _size):
            pass

        def read_bool(self):
            return False

        def read_var_uint32(self):
            return 0

        def read_ref(self):
            return DictSubclass()

    with pytest.raises(Exception) as failure:
        serializer._deserialize_function(FunctionReadContext())
    assert not isinstance(failure.value, AssertionError)


def test_lambda_functions_serialization():
    """Tests serialization of lambda functions."""
    fory = pyfory.Fory(
        xlang=False,
        strict=False,
    )
    test_input = 5

    # Register the necessary types
    fory.register_type(tuple)
    fory.register_type(list)
    # dict is already registered by default with MapSerializer

    # Simple lambda
    simple_lambda = lambda x: x * 2  # noqa: E731
    fory.register_type(type(simple_lambda))
    serialized = fory.serialize(simple_lambda)
    deserialized = fory.deserialize(serialized)
    assert simple_lambda(test_input) == deserialized(test_input)

    # Complex lambda with closure
    multiplier = 3
    closure_lambda = lambda x: x * multiplier  # noqa: E731
    serialized = fory.serialize(closure_lambda)
    deserialized = fory.deserialize(serialized)
    assert closure_lambda(test_input) == deserialized(test_input)


def test_regular_function_roundtrip():
    """Tests serialization of regular functions."""
    fory = pyfory.Fory(
        xlang=False,
        strict=False,
        compatible=False,
    )
    test_input = 5

    def add_one(x):
        return x + 1

    def complex_function(a, b, c=10):
        """A more complex function with default arguments."""
        return a * b + c

    # Test regular function
    fory.register_type(type(add_one))
    # Registry contents are finalized by the first root operation.
    fory.register_type(tuple)
    fory.register_type(list)
    serialized = fory.serialize(add_one)
    deserialized = fory.deserialize(serialized)
    assert add_one(test_input) == deserialized(test_input)

    # dict is already registered by default with MapSerializer

    # Test complex function
    serialized = fory.serialize(complex_function)
    deserialized = fory.deserialize(serialized)
    assert complex_function(2, 3) == deserialized(2, 3)


def test_nested_functions_serialization():
    """Tests serialization of nested functions."""
    fory = pyfory.Fory(
        xlang=False,
        strict=False,
        compatible=False,
    )

    # Register the necessary types
    fory.register_type(tuple)
    fory.register_type(list)
    # dict is already registered by default with MapSerializer

    def outer_function(x):
        def inner_function(y):
            return x + y

        return inner_function

    # Create a nested function
    nested_func = outer_function(10)
    fory.register_type(type(nested_func))

    serialized = fory.serialize(nested_func)
    deserialized = fory.deserialize(serialized)

    assert nested_func(5) == deserialized(5)


def test_local_class_serialization():
    """Tests serialization of local classes."""
    fory = pyfory.Fory(
        xlang=False,
        strict=False,
        compatible=False,
    )

    # Register the necessary types
    fory.register_type(tuple)
    fory.register_type(list)
    # dict is already registered by default with MapSerializer

    def create_local_class():
        from dataclasses import dataclass

        @dataclass
        class LocalClass:
            value: int
            name: str

        return LocalClass(42, "test")

    local_obj = create_local_class()
    fory.register_type(type(local_obj))

    serialized = fory.serialize(local_obj)
    deserialized = fory.deserialize(serialized)

    assert local_obj == deserialized
