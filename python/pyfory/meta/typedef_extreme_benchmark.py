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
Benchmark helper for the direct TypeDef extreme codec prototype.

Usage:
    cd python
    python -m pyfory.meta.typedef_extreme_benchmark
"""

from dataclasses import make_dataclass
from typing import Dict, List, Optional

from pyfory import Fory
from pyfory.buffer import Buffer
from pyfory.field import field as fory_field
from pyfory.meta.typedef import META_SIZE_MASKS
from pyfory.meta.typedef_encoder import encode_typedef
from pyfory.meta.typedef_extreme_codec import encode_extreme_typedef


def _typedef_payload_size(encoded_typedef: bytes) -> int:
    buffer = Buffer(encoded_typedef)
    header = buffer.read_int64()
    payload_size = header & META_SIZE_MASKS
    if payload_size == META_SIZE_MASKS:
        payload_size += buffer.read_var_uint32()
    _ = buffer.read_bytes(payload_size)
    return payload_size


def _build_corpus():
    specs = [
        (
            "UserProfile",
            [
                ("user_id", int),
                ("user_name", str),
                ("user_email", str),
                ("user_age", int),
                ("created_at", int),
                ("is_active", bool),
            ],
        ),
        (
            "SessionState",
            [
                ("session_id", str),
                ("user_id", int),
                ("started_at", int),
                ("expires_at", int),
                ("attributes", Dict[str, str]),
            ],
        ),
        (
            "OrderLine",
            [
                ("order_id", int),
                ("product_id", int),
                ("product_name", str),
                ("quantity", int),
                ("unit_price", float),
            ],
        ),
        (
            "CartSnapshot",
            [
                ("cart_id", str),
                ("user_id", int),
                ("item_ids", List[int]),
                ("item_counts", List[int]),
                ("item_tags", Dict[str, str]),
            ],
        ),
        (
            "InventoryState",
            [
                ("warehouse_id", int),
                ("sku_code", str),
                ("available_count", int),
                ("reserved_count", int),
                ("updated_at", int),
            ],
        ),
        (
            "AuditEvent",
            [
                ("event_id", str),
                ("event_type", str),
                ("actor_id", str),
                ("target_id", str),
                ("metadata", Dict[str, str]),
            ],
        ),
        (
            "GeoEnvelope",
            [
                ("min_latitude", float),
                ("min_longitude", float),
                ("max_latitude", float),
                ("max_longitude", float),
                ("region_name", Optional[str]),
            ],
        ),
        (
            "FeatureSwitch",
            [
                ("feature_name", str),
                ("is_enabled", bool),
                ("rollout_ratio", float),
                ("owner_team", str),
                ("updated_at", int),
            ],
        ),
        (
            "ModelServingConfig",
            [
                ("model_name", str),
                ("model_version", str),
                ("batch_size", int),
                ("timeout_ms", int),
                ("labels", Dict[str, str]),
            ],
        ),
        (
            "TaggedMetric",
            [
                ("request_total", int, fory_field(id=1)),
                ("success_total", int, fory_field(id=2)),
                ("error_total", int, fory_field(id=3)),
                ("latency_p99", int, fory_field(id=4)),
                ("service_name", str, fory_field(id=5)),
            ],
        ),
        (
            "TaggedSpan",
            [
                ("trace_id", str, fory_field(id=1)),
                ("span_id", str, fory_field(id=2)),
                ("parent_span_id", str, fory_field(id=3)),
                ("duration_ns", int, fory_field(id=4)),
                ("status_code", int, fory_field(id=5)),
            ],
        ),
        (
            "TaggedCounter",
            [
                ("counter_name", str, fory_field(id=1)),
                ("window_start", int, fory_field(id=2)),
                ("window_end", int, fory_field(id=3)),
                ("value", int, fory_field(id=4)),
                ("source_id", int, fory_field(id=5)),
            ],
        ),
    ]
    return [make_dataclass(name, fields) for name, fields in specs]


def run_benchmark() -> str:
    classes = _build_corpus()
    fory = Fory(xlang=True)
    for cls in classes:
        fory.register(cls, namespace="bench", typename=cls.__name__)
    resolver = fory.type_resolver

    rows = []
    total_wire = 0
    total_payload = 0
    total_extreme = 0
    for cls in classes:
        type_def = encode_typedef(resolver, cls)
        wire_size = len(type_def.encoded)
        payload_size = _typedef_payload_size(type_def.encoded)
        extreme_size = len(encode_extreme_typedef(type_def))
        total_wire += wire_size
        total_payload += payload_size
        total_extreme += extreme_size
        rows.append((cls.__name__, wire_size, payload_size, extreme_size))

    lines = []
    lines.append("| Type | Current Wire B | Current Payload B | Extreme B | Payload Delta | Payload Saving |")
    lines.append("| --- | ---: | ---: | ---: | ---: | ---: |")
    for name, wire_size, payload_size, extreme_size in rows:
        delta = extreme_size - payload_size
        saving = 0.0 if payload_size == 0 else (payload_size - extreme_size) * 100.0 / payload_size
        lines.append(f"| {name} | {wire_size} | {payload_size} | {extreme_size} | {delta:+d} | {saving:.2f}% |")

    total_delta = total_extreme - total_payload
    total_saving = 0.0 if total_payload == 0 else (total_payload - total_extreme) * 100.0 / total_payload
    lines.append("")
    lines.append(f"Total current wire bytes: {total_wire}")
    lines.append(f"Total current payload bytes: {total_payload}")
    lines.append(f"Total extreme bytes: {total_extreme}")
    lines.append(f"Total payload delta: {total_delta:+d}")
    lines.append(f"Total payload saving: {total_saving:.2f}%")
    return "\n".join(lines)


if __name__ == "__main__":
    print(run_benchmark())
