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

from dataclasses import dataclass

import pyfory
from pyfory import Fory
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.struct import DataClassSerializer, compute_struct_fingerprint
from pyfory.types import TypeId


@dataclass
class UnsignedArrays:
    u8: pyfory.uint8_array = pyfory.field(0)
    u16: pyfory.uint16_array = pyfory.field(1)
    u32: pyfory.uint32_array = pyfory.field(2)
    u64: pyfory.uint64_array = pyfory.field(3)


def test_unsigned_array_typedef_type_ids():
    fory = Fory(xlang=True)
    fory.register_type(UnsignedArrays, namespace="test", typename="UnsignedArrays")

    typedef = encode_typedef(fory.type_resolver, UnsignedArrays)
    field_type_ids = {field.name: field.field_type.type_id for field in typedef.fields}

    assert field_type_ids == {
        "u8": TypeId.UINT8_ARRAY,
        "u16": TypeId.UINT16_ARRAY,
        "u32": TypeId.UINT32_ARRAY,
        "u64": TypeId.UINT64_ARRAY,
    }


def test_unsigned_array_fingerprint_type_ids():
    fory = Fory(xlang=True)
    serializer = DataClassSerializer(fory, UnsignedArrays, xlang=True)

    fingerprint = compute_struct_fingerprint(
        fory.type_resolver,
        serializer._field_names,
        serializer._serializers,
        serializer._nullable_fields,
        serializer._field_infos,
    )

    expected = (
        f"0,{TypeId.UINT8_ARRAY},0,0;"
        f"1,{TypeId.UINT16_ARRAY},0,0;"
        f"2,{TypeId.UINT32_ARRAY},0,0;"
        f"3,{TypeId.UINT64_ARRAY},0,0;"
    )
    assert fingerprint == expected
