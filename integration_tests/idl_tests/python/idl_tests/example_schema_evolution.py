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

from typing import Dict, List, Optional

import datetime
import decimal
import example_common
import pyfory
from pyfory import Ref


EXAMPLE_MESSAGE_TYPE_ID = 1500


@pyfory.dataclass
class ExampleMessageEmpty:
    pass


@pyfory.dataclass
class ExampleFieldBoolValue:
    bool_value: bool = pyfory.field(id=1, default=False)


@pyfory.dataclass
class ExampleFieldInt8Value:
    int8_value: pyfory.int8 = pyfory.field(id=2, default=0)


@pyfory.dataclass
class ExampleFieldInt16Value:
    int16_value: pyfory.int16 = pyfory.field(id=3, default=0)


@pyfory.dataclass
class ExampleFieldFixedInt32Value:
    fixed_int32_value: pyfory.fixed_int32 = pyfory.field(id=4, default=0)


@pyfory.dataclass
class ExampleFieldVarint32Value:
    varint32_value: pyfory.int32 = pyfory.field(id=5, default=0)


@pyfory.dataclass
class ExampleFieldFixedInt64Value:
    fixed_int64_value: pyfory.fixed_int64 = pyfory.field(id=6, default=0)


@pyfory.dataclass
class ExampleFieldVarint64Value:
    varint64_value: pyfory.int64 = pyfory.field(id=7, default=0)


@pyfory.dataclass
class ExampleFieldTaggedInt64Value:
    tagged_int64_value: pyfory.tagged_int64 = pyfory.field(id=8, default=0)


@pyfory.dataclass
class ExampleFieldUint8Value:
    uint8_value: pyfory.uint8 = pyfory.field(id=9, default=0)


@pyfory.dataclass
class ExampleFieldUint16Value:
    uint16_value: pyfory.uint16 = pyfory.field(id=10, default=0)


@pyfory.dataclass
class ExampleFieldFixedUint32Value:
    fixed_uint32_value: pyfory.fixed_uint32 = pyfory.field(id=11, default=0)


@pyfory.dataclass
class ExampleFieldVarUint32Value:
    var_uint32_value: pyfory.uint32 = pyfory.field(id=12, default=0)


@pyfory.dataclass
class ExampleFieldFixedUint64Value:
    fixed_uint64_value: pyfory.fixed_uint64 = pyfory.field(id=13, default=0)


@pyfory.dataclass
class ExampleFieldVarUint64Value:
    var_uint64_value: pyfory.uint64 = pyfory.field(id=14, default=0)


@pyfory.dataclass
class ExampleFieldTaggedUint64Value:
    tagged_uint64_value: pyfory.tagged_uint64 = pyfory.field(id=15, default=0)


@pyfory.dataclass
class ExampleFieldFloat16Value:
    float16_value: pyfory.float16 = pyfory.field(
        id=16, default=pyfory.float16(0.0)
    )


@pyfory.dataclass
class ExampleFieldBfloat16Value:
    bfloat16_value: pyfory.bfloat16 = pyfory.field(
        id=17, default=pyfory.bfloat16(0.0)
    )


@pyfory.dataclass
class ExampleFieldFloat32Value:
    float32_value: pyfory.float32 = pyfory.field(id=18, default=0.0)


@pyfory.dataclass
class ExampleFieldFloat64Value:
    float64_value: pyfory.float64 = pyfory.field(id=19, default=0.0)


@pyfory.dataclass
class ExampleFieldStringValue:
    string_value: str = pyfory.field(id=20, default="")


@pyfory.dataclass
class ExampleFieldBytesValue:
    bytes_value: bytes = pyfory.field(id=21, default=b"")


@pyfory.dataclass
class ExampleFieldDateValue:
    date_value: datetime.date = pyfory.field(id=22, default=None)


@pyfory.dataclass
class ExampleFieldTimestampValue:
    timestamp_value: datetime.datetime = pyfory.field(id=23, default=None)


@pyfory.dataclass
class ExampleFieldDurationValue:
    duration_value: datetime.timedelta = pyfory.field(
        id=24, default=datetime.timedelta()
    )


@pyfory.dataclass
class ExampleFieldDecimalValue:
    decimal_value: decimal.Decimal = pyfory.field(
        id=25, default=decimal.Decimal("0")
    )


@pyfory.dataclass
class ExampleFieldEnumValue:
    enum_value: example_common.ExampleState = pyfory.field(id=26, default=None)


@pyfory.dataclass
class ExampleFieldMessageValue:
    message_value: Optional[example_common.ExampleLeaf] = pyfory.field(
        id=27, nullable=True, default=None
    )


@pyfory.dataclass
class ExampleFieldUnionValue:
    union_value: example_common.ExampleLeafUnion = pyfory.field(id=28, default=None)


@pyfory.dataclass
class ExampleFieldBoolList:
    bool_list: pyfory.bool_ndarray = pyfory.field(id=101, default=None)


@pyfory.dataclass
class ExampleFieldInt8List:
    int8_list: pyfory.int8_ndarray = pyfory.field(id=102, default=None)


@pyfory.dataclass
class ExampleFieldInt16List:
    int16_list: pyfory.int16_ndarray = pyfory.field(id=103, default=None)


@pyfory.dataclass
class ExampleFieldFixedInt32List:
    fixed_int32_list: pyfory.int32_ndarray = pyfory.field(id=104, default=None)


@pyfory.dataclass
class ExampleFieldVarint32List:
    varint32_list: pyfory.int32_ndarray = pyfory.field(id=105, default=None)


@pyfory.dataclass
class ExampleFieldFixedInt64List:
    fixed_int64_list: pyfory.int64_ndarray = pyfory.field(id=106, default=None)


@pyfory.dataclass
class ExampleFieldVarint64List:
    varint64_list: pyfory.int64_ndarray = pyfory.field(id=107, default=None)


@pyfory.dataclass
class ExampleFieldTaggedInt64List:
    tagged_int64_list: pyfory.int64_ndarray = pyfory.field(id=108, default=None)


@pyfory.dataclass
class ExampleFieldUint8List:
    uint8_list: pyfory.uint8_ndarray = pyfory.field(id=109, default=None)


@pyfory.dataclass
class ExampleFieldUint16List:
    uint16_list: pyfory.uint16_ndarray = pyfory.field(id=110, default=None)


@pyfory.dataclass
class ExampleFieldFixedUint32List:
    fixed_uint32_list: pyfory.uint32_ndarray = pyfory.field(id=111, default=None)


@pyfory.dataclass
class ExampleFieldVarUint32List:
    var_uint32_list: pyfory.uint32_ndarray = pyfory.field(id=112, default=None)


@pyfory.dataclass
class ExampleFieldFixedUint64List:
    fixed_uint64_list: pyfory.uint64_ndarray = pyfory.field(id=113, default=None)


@pyfory.dataclass
class ExampleFieldVarUint64List:
    var_uint64_list: pyfory.uint64_ndarray = pyfory.field(id=114, default=None)


@pyfory.dataclass
class ExampleFieldTaggedUint64List:
    tagged_uint64_list: pyfory.uint64_ndarray = pyfory.field(id=115, default=None)


@pyfory.dataclass
class ExampleFieldFloat16List:
    float16_list: pyfory.float16array = pyfory.field(
        id=116, default=pyfory.float16array()
    )


@pyfory.dataclass
class ExampleFieldBfloat16List:
    bfloat16_list: pyfory.bfloat16array = pyfory.field(
        id=117, default=pyfory.bfloat16array()
    )


@pyfory.dataclass
class ExampleFieldMaybeFloat16List:
    maybe_float16_list: List[Optional[pyfory.float16]] = pyfory.field(
        id=118, default=pyfory.float16array()
    )


@pyfory.dataclass
class ExampleFieldMaybeBfloat16List:
    maybe_bfloat16_list: List[Optional[pyfory.bfloat16]] = pyfory.field(
        id=119, default=pyfory.bfloat16array()
    )


@pyfory.dataclass
class ExampleFieldFloat32List:
    float32_list: pyfory.float32_ndarray = pyfory.field(id=120, default=None)


@pyfory.dataclass
class ExampleFieldFloat64List:
    float64_list: pyfory.float64_ndarray = pyfory.field(id=121, default=None)


@pyfory.dataclass
class ExampleFieldStringList:
    string_list: List[str] = pyfory.field(id=122, default_factory=list)


@pyfory.dataclass
class ExampleFieldBytesList:
    bytes_list: List[bytes] = pyfory.field(id=123, default_factory=list)


@pyfory.dataclass
class ExampleFieldDateList:
    date_list: List[datetime.date] = pyfory.field(id=124, default_factory=list)


@pyfory.dataclass
class ExampleFieldTimestampList:
    timestamp_list: List[datetime.datetime] = pyfory.field(
        id=125, default_factory=list
    )


@pyfory.dataclass
class ExampleFieldDurationList:
    duration_list: List[datetime.timedelta] = pyfory.field(
        id=126, default_factory=list
    )


@pyfory.dataclass
class ExampleFieldDecimalList:
    decimal_list: List[decimal.Decimal] = pyfory.field(id=127, default_factory=list)


@pyfory.dataclass
class ExampleFieldEnumList:
    enum_list: List[example_common.ExampleState] = pyfory.field(
        id=128, default_factory=list
    )


@pyfory.dataclass
class ExampleFieldMessageList:
    message_list: List[Ref[example_common.ExampleLeaf, False]] = pyfory.field(
        id=129, default_factory=list
    )


@pyfory.dataclass
class ExampleFieldUnionList:
    union_list: List[Ref[example_common.ExampleLeafUnion, False]] = pyfory.field(
        id=130, default_factory=list
    )


@pyfory.dataclass
class ExampleFieldStringValuesByBool:
    string_values_by_bool: Dict[bool, str] = pyfory.field(
        id=201, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByInt8:
    string_values_by_int8: Dict[pyfory.int8, str] = pyfory.field(
        id=202, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByInt16:
    string_values_by_int16: Dict[pyfory.int16, str] = pyfory.field(
        id=203, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByFixedInt32:
    string_values_by_fixed_int32: Dict[pyfory.fixed_int32, str] = pyfory.field(
        id=204, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByVarint32:
    string_values_by_varint32: Dict[pyfory.int32, str] = pyfory.field(
        id=205, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByFixedInt64:
    string_values_by_fixed_int64: Dict[pyfory.fixed_int64, str] = pyfory.field(
        id=206, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByVarint64:
    string_values_by_varint64: Dict[pyfory.int64, str] = pyfory.field(
        id=207, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByTaggedInt64:
    string_values_by_tagged_int64: Dict[pyfory.tagged_int64, str] = pyfory.field(
        id=208, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByUint8:
    string_values_by_uint8: Dict[pyfory.uint8, str] = pyfory.field(
        id=209, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByUint16:
    string_values_by_uint16: Dict[pyfory.uint16, str] = pyfory.field(
        id=210, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByFixedUint32:
    string_values_by_fixed_uint32: Dict[pyfory.fixed_uint32, str] = pyfory.field(
        id=211, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByVarUint32:
    string_values_by_var_uint32: Dict[pyfory.uint32, str] = pyfory.field(
        id=212, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByFixedUint64:
    string_values_by_fixed_uint64: Dict[pyfory.fixed_uint64, str] = pyfory.field(
        id=213, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByVarUint64:
    string_values_by_var_uint64: Dict[pyfory.uint64, str] = pyfory.field(
        id=214, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByTaggedUint64:
    string_values_by_tagged_uint64: Dict[pyfory.tagged_uint64, str] = pyfory.field(
        id=215, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByString:
    string_values_by_string: Dict[str, str] = pyfory.field(
        id=218, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByTimestamp:
    string_values_by_timestamp: Dict[datetime.datetime, str] = pyfory.field(
        id=219, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByDuration:
    string_values_by_duration: Dict[datetime.timedelta, str] = pyfory.field(
        id=220, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldStringValuesByEnum:
    string_values_by_enum: Dict[example_common.ExampleState, str] = pyfory.field(
        id=221, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldFloat16ValuesByName:
    float16_values_by_name: Dict[str, pyfory.float16] = pyfory.field(
        id=222, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldMaybeFloat16ValuesByName:
    maybe_float16_values_by_name: Dict[str, Optional[pyfory.float16]] = pyfory.field(
        id=223, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldBfloat16ValuesByName:
    bfloat16_values_by_name: Dict[str, pyfory.bfloat16] = pyfory.field(
        id=224, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldMaybeBfloat16ValuesByName:
    maybe_bfloat16_values_by_name: Dict[str, Optional[pyfory.bfloat16]] = (
        pyfory.field(id=225, default_factory=dict)
    )


@pyfory.dataclass
class ExampleFieldBytesValuesByName:
    bytes_values_by_name: Dict[str, bytes] = pyfory.field(
        id=226, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldDateValuesByName:
    date_values_by_name: Dict[str, datetime.date] = pyfory.field(
        id=227, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldDecimalValuesByName:
    decimal_values_by_name: Dict[str, decimal.Decimal] = pyfory.field(
        id=228, default_factory=dict
    )


@pyfory.dataclass
class ExampleFieldMessageValuesByName:
    message_values_by_name: Dict[str, Ref[example_common.ExampleLeaf, False]] = (
        pyfory.field(id=229, default_factory=dict)
    )


@pyfory.dataclass
class ExampleFieldUnionValuesByName:
    union_values_by_name: Dict[str, Ref[example_common.ExampleLeafUnion, False]] = (
        pyfory.field(id=230, default_factory=dict)
    )


VARIANT_SPECS = [
    (1, "bool_value", ExampleFieldBoolValue),
    (2, "int8_value", ExampleFieldInt8Value),
    (3, "int16_value", ExampleFieldInt16Value),
    (4, "fixed_int32_value", ExampleFieldFixedInt32Value),
    (5, "varint32_value", ExampleFieldVarint32Value),
    (6, "fixed_int64_value", ExampleFieldFixedInt64Value),
    (7, "varint64_value", ExampleFieldVarint64Value),
    (8, "tagged_int64_value", ExampleFieldTaggedInt64Value),
    (9, "uint8_value", ExampleFieldUint8Value),
    (10, "uint16_value", ExampleFieldUint16Value),
    (11, "fixed_uint32_value", ExampleFieldFixedUint32Value),
    (12, "var_uint32_value", ExampleFieldVarUint32Value),
    (13, "fixed_uint64_value", ExampleFieldFixedUint64Value),
    (14, "var_uint64_value", ExampleFieldVarUint64Value),
    (15, "tagged_uint64_value", ExampleFieldTaggedUint64Value),
    (16, "float16_value", ExampleFieldFloat16Value),
    (17, "bfloat16_value", ExampleFieldBfloat16Value),
    (18, "float32_value", ExampleFieldFloat32Value),
    (19, "float64_value", ExampleFieldFloat64Value),
    (20, "string_value", ExampleFieldStringValue),
    (21, "bytes_value", ExampleFieldBytesValue),
    (22, "date_value", ExampleFieldDateValue),
    (23, "timestamp_value", ExampleFieldTimestampValue),
    (24, "duration_value", ExampleFieldDurationValue),
    (25, "decimal_value", ExampleFieldDecimalValue),
    (26, "enum_value", ExampleFieldEnumValue),
    (27, "message_value", ExampleFieldMessageValue),
    (28, "union_value", ExampleFieldUnionValue),
    (101, "bool_list", ExampleFieldBoolList),
    (102, "int8_list", ExampleFieldInt8List),
    (103, "int16_list", ExampleFieldInt16List),
    (104, "fixed_int32_list", ExampleFieldFixedInt32List),
    (105, "varint32_list", ExampleFieldVarint32List),
    (106, "fixed_int64_list", ExampleFieldFixedInt64List),
    (107, "varint64_list", ExampleFieldVarint64List),
    (108, "tagged_int64_list", ExampleFieldTaggedInt64List),
    (109, "uint8_list", ExampleFieldUint8List),
    (110, "uint16_list", ExampleFieldUint16List),
    (111, "fixed_uint32_list", ExampleFieldFixedUint32List),
    (112, "var_uint32_list", ExampleFieldVarUint32List),
    (113, "fixed_uint64_list", ExampleFieldFixedUint64List),
    (114, "var_uint64_list", ExampleFieldVarUint64List),
    (115, "tagged_uint64_list", ExampleFieldTaggedUint64List),
    (116, "float16_list", ExampleFieldFloat16List),
    (117, "bfloat16_list", ExampleFieldBfloat16List),
    (118, "maybe_float16_list", ExampleFieldMaybeFloat16List),
    (119, "maybe_bfloat16_list", ExampleFieldMaybeBfloat16List),
    (120, "float32_list", ExampleFieldFloat32List),
    (121, "float64_list", ExampleFieldFloat64List),
    (122, "string_list", ExampleFieldStringList),
    (123, "bytes_list", ExampleFieldBytesList),
    (124, "date_list", ExampleFieldDateList),
    (125, "timestamp_list", ExampleFieldTimestampList),
    (126, "duration_list", ExampleFieldDurationList),
    (127, "decimal_list", ExampleFieldDecimalList),
    (128, "enum_list", ExampleFieldEnumList),
    (129, "message_list", ExampleFieldMessageList),
    (130, "union_list", ExampleFieldUnionList),
    (201, "string_values_by_bool", ExampleFieldStringValuesByBool),
    (202, "string_values_by_int8", ExampleFieldStringValuesByInt8),
    (203, "string_values_by_int16", ExampleFieldStringValuesByInt16),
    (204, "string_values_by_fixed_int32", ExampleFieldStringValuesByFixedInt32),
    (205, "string_values_by_varint32", ExampleFieldStringValuesByVarint32),
    (206, "string_values_by_fixed_int64", ExampleFieldStringValuesByFixedInt64),
    (207, "string_values_by_varint64", ExampleFieldStringValuesByVarint64),
    (208, "string_values_by_tagged_int64", ExampleFieldStringValuesByTaggedInt64),
    (209, "string_values_by_uint8", ExampleFieldStringValuesByUint8),
    (210, "string_values_by_uint16", ExampleFieldStringValuesByUint16),
    (211, "string_values_by_fixed_uint32", ExampleFieldStringValuesByFixedUint32),
    (212, "string_values_by_var_uint32", ExampleFieldStringValuesByVarUint32),
    (213, "string_values_by_fixed_uint64", ExampleFieldStringValuesByFixedUint64),
    (214, "string_values_by_var_uint64", ExampleFieldStringValuesByVarUint64),
    (215, "string_values_by_tagged_uint64", ExampleFieldStringValuesByTaggedUint64),
    (218, "string_values_by_string", ExampleFieldStringValuesByString),
    (219, "string_values_by_timestamp", ExampleFieldStringValuesByTimestamp),
    (220, "string_values_by_duration", ExampleFieldStringValuesByDuration),
    (221, "string_values_by_enum", ExampleFieldStringValuesByEnum),
    (222, "float16_values_by_name", ExampleFieldFloat16ValuesByName),
    (223, "maybe_float16_values_by_name", ExampleFieldMaybeFloat16ValuesByName),
    (224, "bfloat16_values_by_name", ExampleFieldBfloat16ValuesByName),
    (225, "maybe_bfloat16_values_by_name", ExampleFieldMaybeBfloat16ValuesByName),
    (226, "bytes_values_by_name", ExampleFieldBytesValuesByName),
    (227, "date_values_by_name", ExampleFieldDateValuesByName),
    (228, "decimal_values_by_name", ExampleFieldDecimalValuesByName),
    (229, "message_values_by_name", ExampleFieldMessageValuesByName),
    (230, "union_values_by_name", ExampleFieldUnionValuesByName),
]
