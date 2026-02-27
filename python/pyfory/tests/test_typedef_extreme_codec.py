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
from typing import Dict, List, Optional

from pyfory import Fory
from pyfory.buffer import Buffer
from pyfory.field import field as fory_field
from pyfory.meta.typedef import META_SIZE_MASKS
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.meta.typedef_extreme_codec import (
    canonicalize_typedef,
    decode_extreme_typedef,
    encode_canonical_typedef,
    encode_extreme_typedef,
)


@dataclass
class UserProfile:
    user_id: int
    user_name: str
    user_email: str
    user_age: int
    is_active: bool
    created_at: int


@dataclass
class SessionState:
    session_id: str
    user_id: int
    started_at: int
    expires_at: int
    attributes: Dict[str, str]


@dataclass
class ProductCatalog:
    catalog_id: int
    catalog_name: str
    item_ids: List[int]
    item_names: List[str]
    item_prices: List[float]


@dataclass
class GeoEnvelope:
    min_latitude: float
    min_longitude: float
    max_latitude: float
    max_longitude: float
    region_name: Optional[str]


@dataclass
class TaggedMetric:
    request_total: int = fory_field(id=1)
    success_total: int = fory_field(id=2)
    error_total: int = fory_field(id=3)
    latency_p99: int = fory_field(id=4)
    service_name: str = fory_field(id=5)


@dataclass
class AuditEvent:
    event_id: str
    event_type: str
    actor_id: str
    object_id: str
    tags: List[str]
    metadata: Dict[str, str]


_TEST_TYPES = [
    UserProfile,
    SessionState,
    ProductCatalog,
    GeoEnvelope,
    TaggedMetric,
    AuditEvent,
]


def _typedef_payload_size(encoded_typedef: bytes) -> int:
    buffer = Buffer(encoded_typedef)
    header = buffer.read_int64()
    payload_size = header & META_SIZE_MASKS
    if payload_size == META_SIZE_MASKS:
        payload_size += buffer.read_var_uint32()
    _ = buffer.read_bytes(payload_size)
    return payload_size


def _build_resolver():
    fory = Fory(xlang=True)
    for cls in _TEST_TYPES:
        fory.register(cls, namespace="bench", typename=cls.__name__)
    return fory.type_resolver


def test_typedef_extreme_codec_roundtrip():
    resolver = _build_resolver()
    for cls in _TEST_TYPES:
        type_def = encode_typedef(resolver, cls)
        canonical = canonicalize_typedef(type_def)
        payload = encode_canonical_typedef(canonical)
        decoded = decode_extreme_typedef(payload)
        assert decoded == canonical


def test_typedef_extreme_codec_smaller_than_current_payload_on_corpus():
    resolver = _build_resolver()
    baseline_payload_total = 0
    extreme_payload_total = 0

    for cls in _TEST_TYPES:
        type_def = encode_typedef(resolver, cls)
        baseline_payload_total += _typedef_payload_size(type_def.encoded)
        extreme_payload_total += len(encode_extreme_typedef(type_def))

    assert extreme_payload_total < baseline_payload_total
    saving_ratio = (baseline_payload_total - extreme_payload_total) / baseline_payload_total
    assert saving_ratio >= 0.50
