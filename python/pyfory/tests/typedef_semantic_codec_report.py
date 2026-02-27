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

from pyfory.buffer import Buffer
from pyfory.meta.typedef import META_SIZE_MASKS
from pyfory.tests.test_typedef_semantic_codec import _collect_bundle_stats, _collect_codec_stats


def _legacy_payload_size(encoded: bytes) -> int:
    buffer = Buffer(encoded)
    header = buffer.read_int64()
    size = header & META_SIZE_MASKS
    if size == META_SIZE_MASKS:
        size += buffer.read_var_uint32()
    return size


def main():
    rows = _collect_codec_stats()
    print("TypeDef semantic codec report")
    print("=" * 96)
    print(f"{'Type':30} {'LegacyTotal':>12} {'LegacyPayload':>14} {'Packed':>10} {'Ratio':>10}")
    print("-" * 96)

    total_legacy = 0
    total_payload = 0
    total_packed = 0
    for row in rows:
        legacy_total = row["legacy_size"]
        legacy_payload = _legacy_payload_size(row["legacy_typedef"].encoded)
        packed_size = row["packed_size"]
        ratio = packed_size / legacy_total if legacy_total > 0 else 1.0
        print(f"{row['type']:30} {legacy_total:12d} {legacy_payload:14d} {packed_size:10d} {ratio:10.3f}")
        total_legacy += legacy_total
        total_payload += legacy_payload
        total_packed += packed_size

    total_ratio = total_packed / total_legacy if total_legacy > 0 else 1.0
    payload_ratio = total_packed / total_payload if total_payload > 0 else 1.0
    print("-" * 96)
    print(f"{'TOTAL':30} {total_legacy:12d} {total_payload:14d} {total_packed:10d} {total_ratio:10.3f}")
    print(f"Packed vs legacy total ratio: {total_ratio:.3f}")
    print(f"Packed vs legacy payload ratio: {payload_ratio:.3f}")

    print()
    print("TypeDef semantic bundle report")
    print("=" * 96)
    bundle_stats = _collect_bundle_stats(num_classes=96)
    legacy_total = sum(len(typedef.encoded) for typedef in bundle_stats["legacy_typedefs"])
    packed_total = sum(len(item) for item in bundle_stats["packed"])
    bundle_size = len(bundle_stats["bundle_bytes"])
    combined_total = packed_total + bundle_size
    combined_ratio = combined_total / legacy_total if legacy_total > 0 else 1.0

    print(f"{'Bulk classes':30} {len(bundle_stats['legacy_typedefs']):12d}")
    print(f"{'Legacy total bytes':30} {legacy_total:12d}")
    print(f"{'Bundle bytes':30} {bundle_size:12d}")
    print(f"{'Packed typedef bytes':30} {packed_total:12d}")
    print(f"{'Combined bytes':30} {combined_total:12d}")
    print(f"{'Combined ratio':30} {combined_ratio:12.3f}")
    print(f"{'Reduction':30} {(1.0 - combined_ratio) * 100:11.2f}%")


if __name__ == "__main__":
    main()
