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
Compare TypeDef compression on benchmarks/proto/bench.proto messages.

Usage:
    cd python
    ENABLE_FORY_CYTHON_SERIALIZATION=0 python -m pyfory.meta.typedef_extreme_bench_proto_compare
"""

from dataclasses import dataclass
import enum
from typing import Dict, List, Optional

from pyfory import Fory
from pyfory.buffer import Buffer
from pyfory.field import field as fory_field
from pyfory.meta.typedef import META_SIZE_MASKS
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.meta.typedef_extreme_codec import (
    build_shared_named_type_table,
    build_shared_token_dictionary,
    canonicalize_typedef,
    encode_canonical_typedef,
    encode_extreme_typedef,
    measure_token_dictionary_wire_size,
)


@dataclass
class Foo:
    f1: Optional[str] = fory_field(id=1)
    f2: Dict[str, int] = fory_field(id=2)


@dataclass
class Bar:
    f1: Optional[Foo] = fory_field(id=1)
    f2: Optional[str] = fory_field(id=2)
    f3: List[Foo] = fory_field(id=3)
    f4: Dict[int, Foo] = fory_field(id=4)
    f5: Optional[int] = fory_field(id=5)
    f6: Optional[int] = fory_field(id=6)
    f7: Optional[float] = fory_field(id=7)
    f8: Optional[float] = fory_field(id=8)
    f9: List[int] = fory_field(id=9)
    f10: List[int] = fory_field(id=10)


@dataclass
class Sample:
    int_value: int = fory_field(id=1)
    long_value: int = fory_field(id=2)
    float_value: float = fory_field(id=3)
    double_value: float = fory_field(id=4)
    short_value: int = fory_field(id=5)
    char_value: int = fory_field(id=6)
    boolean_value: bool = fory_field(id=7)
    int_value_boxed: int = fory_field(id=8)
    long_value_boxed: int = fory_field(id=9)
    float_value_boxed: float = fory_field(id=10)
    double_value_boxed: float = fory_field(id=11)
    short_value_boxed: int = fory_field(id=12)
    char_value_boxed: int = fory_field(id=13)
    boolean_value_boxed: bool = fory_field(id=14)
    int_array: List[int] = fory_field(id=15)
    long_array: List[int] = fory_field(id=16)
    float_array: List[float] = fory_field(id=17)
    double_array: List[float] = fory_field(id=18)
    short_array: List[int] = fory_field(id=19)
    char_array: List[int] = fory_field(id=20)
    boolean_array: List[bool] = fory_field(id=21)
    string: str = fory_field(id=22)


@dataclass
class SampleList:
    sample_list: List[Sample] = fory_field(id=1)


class Player(enum.Enum):
    JAVA = 0
    FLASH = 1


class Size(enum.Enum):
    SMALL = 0
    LARGE = 1


@dataclass
class Media:
    uri: str = fory_field(id=1)
    title: Optional[str] = fory_field(id=2)
    width: int = fory_field(id=3)
    height: int = fory_field(id=4)
    format: str = fory_field(id=5)
    duration: int = fory_field(id=6)
    size: int = fory_field(id=7)
    bitrate: int = fory_field(id=8)
    has_bitrate: bool = fory_field(id=9)
    persons: List[str] = fory_field(id=10)
    player: Player = fory_field(id=11)
    copyright: str = fory_field(id=12)


@dataclass
class Image:
    uri: str = fory_field(id=1)
    title: Optional[str] = fory_field(id=2)
    width: int = fory_field(id=3)
    height: int = fory_field(id=4)
    size: Size = fory_field(id=5)
    media: Optional[Media] = fory_field(id=6)


@dataclass
class MediaContent:
    media: Media = fory_field(id=1)
    images: List[Image] = fory_field(id=2)


@dataclass
class MediaContentList:
    media_content_list: List[MediaContent] = fory_field(id=1)


@dataclass
class Struct:
    f1: int = fory_field(id=1)
    f2: int = fory_field(id=2)
    f3: int = fory_field(id=3)
    f4: int = fory_field(id=4)
    f5: int = fory_field(id=5)
    f6: int = fory_field(id=6)
    f7: int = fory_field(id=7)
    f8: int = fory_field(id=8)


@dataclass
class StructList:
    struct_list: List[Struct] = fory_field(id=1)


@dataclass
class FooNoId:
    f1: Optional[str]
    f2: Dict[str, int]


@dataclass
class BarNoId:
    f1: Optional[FooNoId]
    f2: Optional[str]
    f3: List[FooNoId]
    f4: Dict[int, FooNoId]
    f5: Optional[int]
    f6: Optional[int]
    f7: Optional[float]
    f8: Optional[float]
    f9: List[int]
    f10: List[int]


@dataclass
class SampleNoId:
    int_value: int
    long_value: int
    float_value: float
    double_value: float
    short_value: int
    char_value: int
    boolean_value: bool
    int_value_boxed: int
    long_value_boxed: int
    float_value_boxed: float
    double_value_boxed: float
    short_value_boxed: int
    char_value_boxed: int
    boolean_value_boxed: bool
    int_array: List[int]
    long_array: List[int]
    float_array: List[float]
    double_array: List[float]
    short_array: List[int]
    char_array: List[int]
    boolean_array: List[bool]
    string: str


@dataclass
class SampleListNoId:
    sample_list: List[SampleNoId]


@dataclass
class MediaNoId:
    uri: str
    title: Optional[str]
    width: int
    height: int
    format: str
    duration: int
    size: int
    bitrate: int
    has_bitrate: bool
    persons: List[str]
    player: Player
    copyright: str


@dataclass
class ImageNoId:
    uri: str
    title: Optional[str]
    width: int
    height: int
    size: Size
    media: Optional[MediaNoId]


@dataclass
class MediaContentNoId:
    media: MediaNoId
    images: List[ImageNoId]


@dataclass
class MediaContentListNoId:
    media_content_list: List[MediaContentNoId]


@dataclass
class StructNoId:
    f1: int
    f2: int
    f3: int
    f4: int
    f5: int
    f6: int
    f7: int
    f8: int


@dataclass
class StructListNoId:
    struct_list: List[StructNoId]


_MESSAGE_CLASSES = [
    Foo,
    Bar,
    Sample,
    SampleList,
    Media,
    Image,
    MediaContent,
    MediaContentList,
    Struct,
    StructList,
]

_AUX_CLASSES = [Player, Size]

_MESSAGE_CLASSES_NO_FIELD_ID = [
    FooNoId,
    BarNoId,
    SampleNoId,
    SampleListNoId,
    MediaNoId,
    ImageNoId,
    MediaContentNoId,
    MediaContentListNoId,
    StructNoId,
    StructListNoId,
]

_PROTO_TYPENAME = {
    Foo: "Foo",
    Bar: "Bar",
    Sample: "Sample",
    SampleList: "SampleList",
    Media: "Media",
    Image: "Image",
    MediaContent: "MediaContent",
    MediaContentList: "MediaContentList",
    Struct: "Struct",
    StructList: "StructList",
    FooNoId: "Foo",
    BarNoId: "Bar",
    SampleNoId: "Sample",
    SampleListNoId: "SampleList",
    MediaNoId: "Media",
    ImageNoId: "Image",
    MediaContentNoId: "MediaContent",
    MediaContentListNoId: "MediaContentList",
    StructNoId: "Struct",
    StructListNoId: "StructList",
}


def _typedef_payload_size(encoded_typedef: bytes) -> int:
    buffer = Buffer(encoded_typedef)
    header = buffer.read_int64()
    payload_size = header & META_SIZE_MASKS
    if payload_size == META_SIZE_MASKS:
        payload_size += buffer.read_var_uint32()
    _ = buffer.read_bytes(payload_size)
    return payload_size


def _run_compare_for_classes(label: str, message_classes: List[type]) -> str:
    fory = Fory(xlang=True)

    for aux in _AUX_CLASSES:
        fory.register(aux, namespace="protobuf", typename=aux.__name__)
    for cls in message_classes:
        fory.register(cls, namespace="protobuf", typename=_PROTO_TYPENAME[cls])

    resolver = fory.type_resolver
    rows = []
    total_wire = 0
    total_payload = 0
    total_extreme_standalone = 0
    total_extreme_shared_body = 0
    canonicals = []

    for cls in message_classes:
        type_def = encode_typedef(resolver, cls)
        canonical = canonicalize_typedef(type_def)
        wire_size = len(type_def.encoded)
        payload_size = _typedef_payload_size(type_def.encoded)
        extreme_standalone = len(encode_extreme_typedef(type_def))
        total_wire += wire_size
        total_payload += payload_size
        total_extreme_standalone += extreme_standalone
        canonicals.append(canonical)
        rows.append(
            {
                "name": _PROTO_TYPENAME[cls],
                "wire": wire_size,
                "payload": payload_size,
                "extreme_standalone": extreme_standalone,
            }
        )

    shared_tokens = build_shared_token_dictionary(canonicals)
    shared_dict_size = measure_token_dictionary_wire_size(shared_tokens)
    shared_named_type_mapping, shared_named_type_list = build_shared_named_type_table(canonicals)

    for row, canonical in zip(rows, canonicals):
        shared_body = len(
            encode_canonical_typedef(
                canonical,
                token_dictionary=shared_tokens,
                write_token_dictionary=False,
                shared_named_type_table=shared_named_type_mapping,
            )
        )
        row["extreme_shared_body"] = shared_body
        total_extreme_shared_body += shared_body

    lines = []
    lines.append(f"# {label}")
    lines.append("")
    lines.append("## Standalone TypeDef Compression")
    lines.append("")
    lines.append("| Message | Current Wire B | Current Payload B | Extreme B | Payload Delta | Payload Saving |")
    lines.append("| --- | ---: | ---: | ---: | ---: | ---: |")
    for row in rows:
        name = row["name"]
        wire_size = row["wire"]
        payload_size = row["payload"]
        extreme_size = row["extreme_standalone"]
        delta = extreme_size - payload_size
        saving = 0.0 if payload_size == 0 else (payload_size - extreme_size) * 100.0 / payload_size
        lines.append(f"| {name} | {wire_size} | {payload_size} | {extreme_size} | {delta:+d} | {saving:.2f}% |")

    total_delta = total_extreme_standalone - total_payload
    total_saving = 0.0 if total_payload == 0 else (total_payload - total_extreme_standalone) * 100.0 / total_payload
    lines.append("")
    lines.append(f"Total current wire bytes: {total_wire}")
    lines.append(f"Total current payload bytes: {total_payload}")
    lines.append(f"Total extreme bytes: {total_extreme_standalone}")
    lines.append(f"Total payload delta: {total_delta:+d}")
    lines.append(f"Total payload saving: {total_saving:.2f}%")

    lines.append("")
    lines.append("## Shared Dictionary Session Compression")
    lines.append("")
    lines.append("| Message | Current Payload B | Extreme Body B | Body Delta | Body Saving |")
    lines.append("| --- | ---: | ---: | ---: | ---: |")
    for row in rows:
        payload_size = row["payload"]
        shared_body = row["extreme_shared_body"]
        delta = shared_body - payload_size
        saving = 0.0 if payload_size == 0 else (payload_size - shared_body) * 100.0 / payload_size
        lines.append(f"| {row['name']} | {payload_size} | {shared_body} | {delta:+d} | {saving:.2f}% |")

    total_shared_extreme = shared_dict_size + total_extreme_shared_body
    total_shared_delta = total_shared_extreme - total_payload
    total_shared_saving = 0.0 if total_payload == 0 else (total_payload - total_shared_extreme) * 100.0 / total_payload
    lines.append("")
    lines.append(f"Shared token dictionary size (one-time): {shared_dict_size}")
    lines.append(f"Shared named type table entries (out-of-band): {len(shared_named_type_list)}")
    lines.append(f"Total shared extreme body bytes: {total_extreme_shared_body}")
    lines.append(f"Total shared extreme bytes (dict + bodies): {total_shared_extreme}")
    lines.append(f"Total shared payload delta: {total_shared_delta:+d}")
    lines.append(f"Total shared payload saving: {total_shared_saving:.2f}%")
    return "\n".join(lines)


def run_compare() -> str:
    with_field_id = _run_compare_for_classes("With Field ID", _MESSAGE_CLASSES)
    without_field_id = _run_compare_for_classes("Without Field ID", _MESSAGE_CLASSES_NO_FIELD_ID)
    return f"{with_field_id}\n\n{without_field_id}"


if __name__ == "__main__":
    print(run_compare())
