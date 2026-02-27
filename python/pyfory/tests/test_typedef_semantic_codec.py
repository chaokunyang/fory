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

from dataclasses import dataclass, make_dataclass
from typing import Dict, List, Optional

from pyfory import Fory, field as fory_field
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.meta.typedef_semantic_codec import (
    build_semantic_bundle,
    pack_semantic_bundle,
    pack_typedef,
    pack_typedef_with_bundle,
    semantic_fingerprint,
    unpack_semantic_bundle,
    unpack_typedef,
    unpack_typedef_with_bundle,
)


@dataclass
class ProfileRecord:
    user_id: int
    user_name: str
    display_name: Optional[str]
    active: bool


@dataclass
class AggregateWindow:
    window_start_ms: int
    window_end_ms: int
    metric_names: List[str]
    counters: Dict[str, int]
    nested_counters: Dict[str, List[int]]


@dataclass
class TaggedAggregateWindow:
    window_start_ms: int = fory_field(id=1, default=0)
    window_end_ms: int = fory_field(id=2, default=0)
    metric_names: List[str] = fory_field(id=3, default_factory=list)
    counters: Dict[str, int] = fory_field(id=4, default_factory=dict)
    owner_name: str = fory_field(id=7, default="")


SAMPLE_CLASSES = [ProfileRecord, AggregateWindow, TaggedAggregateWindow]


def _collect_codec_stats():
    fory = Fory(xlang=True)
    for cls in SAMPLE_CLASSES:
        fory.register(cls, namespace="semantic", typename=cls.__name__)

    resolver = fory.type_resolver
    rows = []
    for cls in SAMPLE_CLASSES:
        legacy_typedef = encode_typedef(resolver, cls)
        packed = pack_typedef(legacy_typedef)
        restored = unpack_typedef(packed)
        rows.append(
            {
                "type": cls.__name__,
                "legacy_size": len(legacy_typedef.encoded),
                "packed_size": len(packed),
                "legacy_typedef": legacy_typedef,
                "restored_typedef": restored,
            }
        )
    return rows


def _build_bulk_classes(num_classes: int = 96):
    classes = []
    for idx in range(num_classes):
        cls = make_dataclass(
            f"BulkRecord{idx}",
            [
                ("user_id", int),
                ("user_name", str),
                ("display_name", Optional[str]),
                ("active", bool),
                ("metric_names", List[str]),
                ("counters", Dict[str, int]),
            ],
        )
        classes.append(cls)
    return classes


def _collect_bundle_stats(num_classes: int = 96):
    classes = _build_bulk_classes(num_classes)
    fory = Fory(xlang=True)
    for cls in classes:
        fory.register(cls, namespace="semantic_bulk", typename=cls.__name__)
    resolver = fory.type_resolver

    legacy_typedefs = [encode_typedef(resolver, cls) for cls in classes]
    bundle = build_semantic_bundle(legacy_typedefs)
    bundle_bytes = pack_semantic_bundle(bundle)
    recovered_bundle = unpack_semantic_bundle(bundle_bytes)

    packed = [pack_typedef_with_bundle(typedef, bundle) for typedef in legacy_typedefs]
    restored = [unpack_typedef_with_bundle(item, recovered_bundle) for item in packed]
    return {
        "legacy_typedefs": legacy_typedefs,
        "packed": packed,
        "restored": restored,
        "bundle_bytes": bundle_bytes,
    }


def test_typedef_semantic_codec_roundtrip():
    rows = _collect_codec_stats()
    for row in rows:
        assert semantic_fingerprint(row["legacy_typedef"]) == semantic_fingerprint(row["restored_typedef"])


def test_typedef_semantic_codec_size_improvement():
    rows = _collect_codec_stats()
    total_legacy = sum(row["legacy_size"] for row in rows)
    total_packed = sum(row["packed_size"] for row in rows)
    improved_count = sum(1 for row in rows if row["packed_size"] < row["legacy_size"])

    assert improved_count >= 2
    assert total_packed < total_legacy


def test_typedef_semantic_bundle_roundtrip():
    stats = _collect_bundle_stats(num_classes=24)
    for legacy_typedef, restored_typedef in zip(stats["legacy_typedefs"], stats["restored"]):
        assert semantic_fingerprint(legacy_typedef) == semantic_fingerprint(restored_typedef)


def test_typedef_semantic_bundle_compression_over_50_percent():
    stats = _collect_bundle_stats(num_classes=96)
    legacy_total = sum(len(typedef.encoded) for typedef in stats["legacy_typedefs"])
    packed_total = sum(len(item) for item in stats["packed"])
    bundle_total = len(stats["bundle_bytes"])
    total_with_bundle = packed_total + bundle_total

    assert total_with_bundle <= legacy_total * 0.5
