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

"""Test collection size guardrails for payload-driven container allocation."""

from dataclasses import dataclass
from typing import List

import pytest

import pyfory
from pyfory import Fory
from pyfory.types import TypeId


class ObjectPayload:
    pass


def roundtrip(data, limit, xlang=False, ref=False):
    """Serialize and deserialize with given collection size limit."""
    writer = Fory(xlang=xlang, ref=ref, compatible=xlang)
    reader = Fory(xlang=xlang, ref=ref, max_collection_size=limit, compatible=xlang)
    return reader.deserialize(writer.serialize(data))


class TestCollectionSizeLimit:
    """Collections (list/set/dict) preallocate memory, so need size limits."""

    @pytest.mark.parametrize("xlang", [False, True])
    @pytest.mark.parametrize(
        "data,limit",
        [
            ([1, 2, 3], 10),  # list within limit
            ({1, 2, 3}, 10),  # set within limit
            ({"a": 1}, 10),  # dict within limit
            ([], 0),  # empty list ok
            (set(), 0),  # empty set ok
            ({}, 0),  # empty dict ok
        ],
    )
    def test_within_limit_succeeds(self, xlang, data, limit):
        assert roundtrip(data, limit, xlang=xlang) == data

    @pytest.mark.parametrize("xlang", [False, True])
    @pytest.mark.parametrize(
        "data,limit",
        [
            (list(range(10)), 5),  # list exceeds
            (set(range(10)), 5),  # set exceeds
            ({str(i): i for i in range(10)}, 5),  # dict exceeds
            ([[1], list(range(10))], 5),  # nested inner exceeds
        ],
    )
    def test_exceeds_limit_fails(self, xlang, data, limit):
        with pytest.raises(ValueError, match="exceeds the configured limit"):
            roundtrip(data, limit, xlang=xlang)

    @pytest.mark.parametrize("ref", [False, True])
    @pytest.mark.parametrize(
        "data,limit,should_fail",
        [
            ((1, 2, 3), 10, False),
            (tuple(range(10)), 5, True),
        ],
    )
    def test_tuple_limit(self, ref, data, limit, should_fail):
        """Tuple only works in xlang=False mode."""
        if should_fail:
            with pytest.raises(ValueError, match="exceeds the configured limit"):
                roundtrip(data, limit, xlang=False, ref=ref)
        else:
            assert roundtrip(data, limit, xlang=False, ref=ref) == data

    def test_default_limit_is_one_million(self):
        assert (
            Fory(
                xlang=False,
                compatible=False,
            ).max_collection_size
            == 1_000_000
        )

    def test_dataclass_list_field_exceeds_limit(self):
        @dataclass
        class Container:
            items: List[pyfory.Int32]

        writer = Fory(xlang=True, compatible=False)
        writer.register(Container)
        reader = Fory(xlang=True, compatible=False, max_collection_size=5)
        reader.register(Container)

        with pytest.raises(ValueError, match="exceeds the configured limit"):
            reader.deserialize(writer.serialize(Container(items=list(range(10)))))

    def test_object_field_count_exceeds_limit(self):
        obj = ObjectPayload()
        obj.value = 1
        writer = Fory(xlang=False, ref=True, strict=False, compatible=False)
        reader = Fory(xlang=False, ref=True, strict=False, max_collection_size=0, compatible=False)
        writer.register(ObjectPayload)
        reader.register(ObjectPayload)

        with pytest.raises(ValueError, match="object field size 1 exceeds"):
            reader.deserialize(writer.serialize(obj))

    def test_local_class_base_count_exceeds_limit(self):
        def make_local_class():
            class LocalPayload:
                pass

            return LocalPayload

        writer = Fory(xlang=False, ref=True, strict=False, compatible=False)
        reader = Fory(xlang=False, ref=True, strict=False, max_collection_size=0, compatible=False)

        with pytest.raises(ValueError, match="local class base size 1 exceeds"):
            reader.deserialize(writer.serialize(make_local_class()))

    def test_local_function_defaults_exceed_limit(self):
        def local_function(value=1):
            return value

        writer = Fory(xlang=False, ref=True, strict=False, compatible=False)
        reader = Fory(xlang=False, ref=True, strict=False, max_collection_size=0, compatible=False)

        with pytest.raises(ValueError, match="function default size 1 exceeds"):
            reader.deserialize(writer.serialize(local_function))

    def test_object_ndarray_length_exceeds_limit(self):
        np = pytest.importorskip("numpy")
        arr = np.array([object(), object()], dtype=object)
        writer = Fory(xlang=False, ref=True, strict=False, compatible=False)
        reader = Fory(xlang=False, ref=True, strict=False, max_collection_size=1, compatible=False)

        with pytest.raises(ValueError, match="ndarray object size 2 exceeds"):
            reader.deserialize(writer.serialize(arr))


class TestMalformedMetadata:
    def test_malformed_metastring_ref_raises_value_error(self):
        payload = bytes([1, 255, TypeId.NAMED_STRUCT, 3])
        with pytest.raises(ValueError, match="Invalid dynamic metastring id"):
            Fory(xlang=True, compatible=False, strict=False).deserialize(payload)
