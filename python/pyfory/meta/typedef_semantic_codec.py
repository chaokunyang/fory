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
Experimental semantic TypeDef codec.

This module encodes TypeDef semantics directly instead of re-encoding the current
TypeDef wire bytes. It is designed for maximum compactness on small schema
payloads and intentionally prioritizes compression ratio over encode speed.
"""

from __future__ import annotations

from dataclasses import dataclass
from collections import Counter
from typing import Dict, Iterable, List, Tuple

from pyfory._fory import NO_USER_TYPE_ID
from pyfory.meta.typedef import CollectionFieldType, DynamicFieldType, FieldInfo, FieldType, MapFieldType, TypeDef
from pyfory.types import TypeId, is_polymorphic_type

_FORMAT_VERSION = 0b001
_FORMAT_VERSION_BUNDLE_TYPEDEF = 0b010
_FORMAT_VERSION_BUNDLE = 0b011

_LOWER_SPECIAL_ALPHABET = "abcdefghijklmnopqrstuvwxyz._$|"
_LOWER_SPECIAL_TO_CODE = {ch: idx for idx, ch in enumerate(_LOWER_SPECIAL_ALPHABET)}
_LOWER_SPECIAL_FROM_CODE = {idx: ch for idx, ch in enumerate(_LOWER_SPECIAL_ALPHABET)}

_LUDS_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._"
_LUDS_TO_CODE = {ch: idx for idx, ch in enumerate(_LUDS_ALPHABET)}
_LUDS_FROM_CODE = {idx: ch for idx, ch in enumerate(_LUDS_ALPHABET)}

_DYNAMIC_TYPE_IDS = {
    TypeId.UNKNOWN,
    TypeId.EXT,
    TypeId.STRUCT,
    TypeId.NAMED_STRUCT,
    TypeId.COMPATIBLE_STRUCT,
    TypeId.NAMED_COMPATIBLE_STRUCT,
}

_ROOT_TYPE_ID_SHORTLIST = (
    TypeId.NAMED_COMPATIBLE_STRUCT,
    TypeId.COMPATIBLE_STRUCT,
    TypeId.NAMED_STRUCT,
    TypeId.STRUCT,
)
_ROOT_TYPE_ID_TO_CODE = {type_id: idx for idx, type_id in enumerate(_ROOT_TYPE_ID_SHORTLIST)}
_ROOT_TYPE_ID_BITS = (len(_ROOT_TYPE_ID_SHORTLIST) - 1).bit_length()

_EXPR_TYPE_ID_SHORTLIST = (
    TypeId.UNKNOWN,
    TypeId.BOOL,
    TypeId.INT32,
    TypeId.INT64,
    TypeId.STRING,
    TypeId.LIST,
    TypeId.SET,
    TypeId.MAP,
)
_EXPR_TYPE_ID_TO_CODE = {type_id: idx for idx, type_id in enumerate(_EXPR_TYPE_ID_SHORTLIST)}
_EXPR_TYPE_ID_BITS = (len(_EXPR_TYPE_ID_SHORTLIST) - 1).bit_length()


class _BitWriter:
    __slots__ = ("_bytes", "_current", "_used")

    def __init__(self):
        self._bytes = bytearray()
        self._current = 0
        self._used = 0

    def write_bit(self, bit: int) -> None:
        self._current = (self._current << 1) | (1 if bit else 0)
        self._used += 1
        if self._used == 8:
            self._bytes.append(self._current)
            self._current = 0
            self._used = 0

    def write_bits(self, value: int, num_bits: int) -> None:
        if num_bits < 0:
            raise ValueError(f"num_bits must be >= 0, got {num_bits}")
        if num_bits == 0:
            return
        for shift in range(num_bits - 1, -1, -1):
            self.write_bit((value >> shift) & 1)

    def write_ue(self, value: int) -> None:
        if value < 0:
            raise ValueError(f"UE value must be >= 0, got {value}")
        code_num = value + 1
        bit_len = code_num.bit_length()
        leading_zeros = bit_len - 1
        for _ in range(leading_zeros):
            self.write_bit(0)
        self.write_bits(code_num, bit_len)

    def to_bytes(self) -> bytes:
        if self._used:
            self._bytes.append(self._current << (8 - self._used))
            self._current = 0
            self._used = 0
        return bytes(self._bytes)


class _BitReader:
    __slots__ = ("_data", "_bit_pos", "_total_bits")

    def __init__(self, data: bytes):
        self._data = data
        self._bit_pos = 0
        self._total_bits = len(data) * 8

    def read_bit(self) -> int:
        if self._bit_pos >= self._total_bits:
            raise EOFError("Unexpected end of semantic typedef bitstream")
        byte_idx = self._bit_pos >> 3
        intra = 7 - (self._bit_pos & 7)
        self._bit_pos += 1
        return (self._data[byte_idx] >> intra) & 1

    def read_bits(self, num_bits: int) -> int:
        if num_bits < 0:
            raise ValueError(f"num_bits must be >= 0, got {num_bits}")
        value = 0
        for _ in range(num_bits):
            value = (value << 1) | self.read_bit()
        return value

    def read_ue(self) -> int:
        leading_zeros = 0
        while self.read_bit() == 0:
            leading_zeros += 1
        suffix = self.read_bits(leading_zeros) if leading_zeros else 0
        return ((1 << leading_zeros) | suffix) - 1


ExprKey = Tuple


@dataclass
class SemanticBundle:
    strings: Tuple[str, ...]
    string_to_id: Dict[str, int]
    expr_keys: Tuple[ExprKey, ...]
    expr_to_id: Dict[ExprKey, int]
    expr_entries: Tuple[Tuple, ...]
    field_sigs: Tuple[Tuple[int, int, int, int], ...]
    field_sig_to_id: Dict[Tuple[int, int, int, int], int]
    type_sigs: Tuple[Tuple[int, ...], ...]
    type_sig_to_id: Dict[Tuple[int, ...], int]


def pack_typedef(typedef: TypeDef) -> bytes:
    """
    Encode a TypeDef into a compact semantic bitstream.

    The output is self-contained and decodable without a registry.
    """
    writer = _BitWriter()
    writer.write_bits(_FORMAT_VERSION, 3)

    canonical_fields = _canonicalize_fields(typedef.fields)
    strings = {typedef.namespace, typedef.typename}
    for field in canonical_fields:
        if not field.uses_tag_id():
            strings.add(field.name)
    ordered_strings = sorted(strings)
    string_to_id = {value: idx for idx, value in enumerate(ordered_strings)}

    _write_string_table(writer, ordered_strings)
    writer.write_ue(string_to_id[typedef.namespace])
    writer.write_ue(string_to_id[typedef.typename])
    _write_root_type_id(writer, int(typedef.type_id))

    has_user_type_id = typedef.user_type_id not in (None, NO_USER_TYPE_ID)
    writer.write_bit(1 if has_user_type_id else 0)
    if has_user_type_id:
        writer.write_ue(int(typedef.user_type_id))

    field_expr_keys = [_freeze_field_type(field.field_type) for field in canonical_fields]
    expr_counts = _count_exprs(field_expr_keys)
    expr_order = sorted(expr_counts, key=lambda key: (-expr_counts[key], _expr_sort_key(key)))
    expr_to_id = {expr_key: idx for idx, expr_key in enumerate(expr_order)}

    writer.write_ue(len(expr_order))
    for expr_key in expr_order:
        _write_expr_entry(writer, expr_key, expr_to_id)

    writer.write_ue(len(canonical_fields))
    for field, expr_key in zip(canonical_fields, field_expr_keys):
        has_tag_id = field.uses_tag_id()
        writer.write_bit(1 if has_tag_id else 0)
        if has_tag_id:
            writer.write_ue(int(field.tag_id))
        else:
            writer.write_ue(string_to_id[field.name])
        writer.write_ue(expr_to_id[expr_key])

    return writer.to_bytes()


def build_semantic_bundle(typedefs: Iterable[TypeDef]) -> SemanticBundle:
    """
    Train a reusable bundle on a TypeDef corpus.

    Bundle-trained encoding is significantly smaller than per-TypeDef standalone
    encoding when many TypeDefs share field signatures and type expressions.
    """
    canonical_rows = []
    string_counter: Counter = Counter()
    expr_counter: Counter = Counter()

    for typedef in typedefs:
        canonical_fields = _canonicalize_fields(typedef.fields)
        expr_keys = [_freeze_field_type(field.field_type) for field in canonical_fields]
        canonical_rows.append((typedef, canonical_fields, expr_keys))

        string_counter[typedef.namespace] += 1
        string_counter[typedef.typename] += 1
        for field in canonical_fields:
            if not field.uses_tag_id():
                string_counter[field.name] += 1
        expr_counter.update(_count_exprs(expr_keys))

    ordered_strings = tuple(sorted(string_counter, key=lambda value: (-string_counter[value], len(value), value)))
    string_to_id = {value: idx for idx, value in enumerate(ordered_strings)}

    expr_keys = tuple(sorted(expr_counter, key=lambda key: (-expr_counter[key], _expr_sort_key(key))))
    expr_to_id = {expr_key: idx for idx, expr_key in enumerate(expr_keys)}
    expr_entries = tuple(_expr_entry_from_key(expr_key, expr_to_id) for expr_key in expr_keys)

    field_sig_counter: Counter = Counter()
    row_field_sig_keys = []
    for _, canonical_fields, row_expr_keys in canonical_rows:
        field_sig_keys = []
        for field, expr_key in zip(canonical_fields, row_expr_keys):
            expr_id = expr_to_id[expr_key]
            if field.uses_tag_id():
                field_sig = (1, int(field.tag_id), -1, expr_id)
            else:
                field_sig = (0, -1, string_to_id[field.name], expr_id)
            field_sig_keys.append(field_sig)
            field_sig_counter[field_sig] += 1
        row_field_sig_keys.append(tuple(field_sig_keys))

    field_sigs = tuple(sorted(field_sig_counter, key=lambda key: (-field_sig_counter[key], key)))
    field_sig_to_id = {field_sig: idx for idx, field_sig in enumerate(field_sigs)}

    type_sig_counter: Counter = Counter()
    for field_sig_keys in row_field_sig_keys:
        type_sig_counter[tuple(field_sig_to_id[field_sig] for field_sig in field_sig_keys)] += 1

    type_sigs = tuple(sorted(type_sig_counter, key=lambda key: (-type_sig_counter[key], key)))
    type_sig_to_id = {type_sig: idx for idx, type_sig in enumerate(type_sigs)}

    return SemanticBundle(
        strings=ordered_strings,
        string_to_id=string_to_id,
        expr_keys=expr_keys,
        expr_to_id=expr_to_id,
        expr_entries=expr_entries,
        field_sigs=field_sigs,
        field_sig_to_id=field_sig_to_id,
        type_sigs=type_sigs,
        type_sig_to_id=type_sig_to_id,
    )


def pack_typedef_with_bundle(typedef: TypeDef, bundle: SemanticBundle) -> bytes:
    """
    Encode a TypeDef with a pre-trained semantic bundle.

    This is the high-compression path intended for cached/schema-registry
    environments where the bundle is shared once and reused.
    """
    writer = _BitWriter()
    writer.write_bits(_FORMAT_VERSION_BUNDLE_TYPEDEF, 3)

    writer.write_ue(_bundle_string_id(bundle, typedef.namespace, "namespace"))
    writer.write_ue(_bundle_string_id(bundle, typedef.typename, "typename"))
    _write_root_type_id(writer, int(typedef.type_id))

    has_user_type_id = typedef.user_type_id not in (None, NO_USER_TYPE_ID)
    writer.write_bit(1 if has_user_type_id else 0)
    if has_user_type_id:
        writer.write_ue(int(typedef.user_type_id))

    canonical_fields = _canonicalize_fields(typedef.fields)
    field_sig_ids = []
    for field in canonical_fields:
        expr_key = _freeze_field_type(field.field_type)
        expr_id = bundle.expr_to_id.get(expr_key)
        if expr_id is None:
            raise ValueError(f"Expression key is not in bundle: {expr_key}")

        if field.uses_tag_id():
            field_sig = (1, int(field.tag_id), -1, expr_id)
        else:
            name_id = _bundle_string_id(bundle, field.name, "field name")
            field_sig = (0, -1, name_id, expr_id)

        field_sig_id = bundle.field_sig_to_id.get(field_sig)
        if field_sig_id is None:
            raise ValueError(f"Field signature is not in bundle: {field_sig}")
        field_sig_ids.append(field_sig_id)

    type_sig = tuple(field_sig_ids)
    type_sig_id = bundle.type_sig_to_id.get(type_sig)
    if type_sig_id is None:
        raise ValueError(f"Type signature is not in bundle: {type_sig}")

    writer.write_ue(type_sig_id)
    return writer.to_bytes()


def unpack_typedef_with_bundle(encoded: bytes, bundle: SemanticBundle) -> TypeDef:
    """
    Decode bundle-based TypeDef bytes.
    """
    reader = _BitReader(encoded)
    version = reader.read_bits(3)
    if version != _FORMAT_VERSION_BUNDLE_TYPEDEF:
        raise ValueError(f"Unsupported bundle typedef version {version}, expected {_FORMAT_VERSION_BUNDLE_TYPEDEF}")

    namespace = _string_ref(list(bundle.strings), reader.read_ue(), "namespace")
    typename = _string_ref(list(bundle.strings), reader.read_ue(), "typename")
    root_type_id = _read_root_type_id(reader)

    has_user_type_id = bool(reader.read_bit())
    user_type_id = reader.read_ue() if has_user_type_id else NO_USER_TYPE_ID

    type_sig_id = reader.read_ue()
    if type_sig_id < 0 or type_sig_id >= len(bundle.type_sigs):
        raise ValueError(f"Invalid type signature id {type_sig_id}, max is {len(bundle.type_sigs) - 1}")
    field_sig_ids = bundle.type_sigs[type_sig_id]

    class_name = f"{namespace}.{typename}" if namespace else typename
    expr_entries = list(bundle.expr_entries)
    field_types_cache: Dict[int, FieldType] = {}
    fields: List[FieldInfo] = []
    for field_sig_id in field_sig_ids:
        if field_sig_id < 0 or field_sig_id >= len(bundle.field_sigs):
            raise ValueError(f"Invalid field signature id {field_sig_id}, max is {len(bundle.field_sigs) - 1}")
        has_tag, tag_id, name_id, expr_id = bundle.field_sigs[field_sig_id]

        if has_tag == 1:
            field_name = f"__tag_{tag_id}__"
            resolved_tag_id = int(tag_id)
        else:
            field_name = _string_ref(list(bundle.strings), int(name_id), "field name")
            resolved_tag_id = -1
        field_type = _build_field_type(int(expr_id), expr_entries, field_types_cache, set())
        fields.append(FieldInfo(field_name, field_type, class_name, resolved_tag_id))

    return TypeDef(
        namespace=namespace,
        typename=typename,
        cls=None,
        type_id=root_type_id,
        fields=fields,
        encoded=encoded,
        is_compressed=False,
        user_type_id=user_type_id,
    )


def pack_semantic_bundle(bundle: SemanticBundle) -> bytes:
    """
    Serialize a semantic bundle to bytes.
    """
    writer = _BitWriter()
    writer.write_bits(_FORMAT_VERSION_BUNDLE, 3)

    writer.write_ue(len(bundle.strings))
    for value in bundle.strings:
        _write_string(writer, value)

    writer.write_ue(len(bundle.expr_entries))
    for entry in bundle.expr_entries:
        _write_expr_entry_from_entry(writer, entry)

    writer.write_ue(len(bundle.field_sigs))
    for has_tag, tag_id, name_id, expr_id in bundle.field_sigs:
        writer.write_bit(1 if has_tag else 0)
        if has_tag:
            writer.write_ue(int(tag_id))
        else:
            writer.write_ue(int(name_id))
        writer.write_ue(int(expr_id))

    writer.write_ue(len(bundle.type_sigs))
    for type_sig in bundle.type_sigs:
        writer.write_ue(len(type_sig))
        for field_sig_id in type_sig:
            writer.write_ue(int(field_sig_id))

    return writer.to_bytes()


def unpack_semantic_bundle(encoded: bytes) -> SemanticBundle:
    """
    Deserialize a semantic bundle from bytes.
    """
    reader = _BitReader(encoded)
    version = reader.read_bits(3)
    if version != _FORMAT_VERSION_BUNDLE:
        raise ValueError(f"Unsupported bundle version {version}, expected {_FORMAT_VERSION_BUNDLE}")

    string_count = reader.read_ue()
    strings = tuple(_read_string(reader) for _ in range(string_count))
    string_to_id = {value: idx for idx, value in enumerate(strings)}

    expr_count = reader.read_ue()
    expr_entries = []
    for _ in range(expr_count):
        type_id = _read_expr_type_id(reader)
        is_nullable = bool(reader.read_bit())
        is_tracking_ref = bool(reader.read_bit())
        if type_id in (TypeId.LIST, TypeId.SET):
            child_id = reader.read_ue()
            expr_entries.append((type_id, is_nullable, is_tracking_ref, child_id))
        elif type_id == TypeId.MAP:
            key_id = reader.read_ue()
            value_id = reader.read_ue()
            expr_entries.append((type_id, is_nullable, is_tracking_ref, key_id, value_id))
        else:
            expr_entries.append((type_id, is_nullable, is_tracking_ref))
    expr_entries = tuple(expr_entries)

    expr_key_cache: Dict[int, ExprKey] = {}
    expr_keys = tuple(_expr_key_from_entries(idx, expr_entries, expr_key_cache) for idx in range(len(expr_entries)))
    expr_to_id = {expr_key: idx for idx, expr_key in enumerate(expr_keys)}

    field_sig_count = reader.read_ue()
    field_sigs = []
    for _ in range(field_sig_count):
        has_tag = reader.read_bit()
        if has_tag:
            tag_id = reader.read_ue()
            name_id = -1
        else:
            tag_id = -1
            name_id = reader.read_ue()
        expr_id = reader.read_ue()
        field_sigs.append((int(has_tag), int(tag_id), int(name_id), int(expr_id)))
    field_sigs = tuple(field_sigs)
    field_sig_to_id = {field_sig: idx for idx, field_sig in enumerate(field_sigs)}

    type_sig_count = reader.read_ue()
    type_sigs = []
    for _ in range(type_sig_count):
        field_count = reader.read_ue()
        field_sig_ids = tuple(reader.read_ue() for _ in range(field_count))
        type_sigs.append(field_sig_ids)
    type_sigs = tuple(type_sigs)
    type_sig_to_id = {type_sig: idx for idx, type_sig in enumerate(type_sigs)}

    return SemanticBundle(
        strings=strings,
        string_to_id=string_to_id,
        expr_keys=expr_keys,
        expr_to_id=expr_to_id,
        expr_entries=expr_entries,
        field_sigs=field_sigs,
        field_sig_to_id=field_sig_to_id,
        type_sigs=type_sigs,
        type_sig_to_id=type_sig_to_id,
    )


def unpack_typedef(encoded: bytes) -> TypeDef:
    """
    Decode a semantic TypeDef bitstream back into a TypeDef object.
    """
    reader = _BitReader(encoded)
    version = reader.read_bits(3)
    if version != _FORMAT_VERSION:
        raise ValueError(f"Unsupported semantic typedef version {version}, expected {_FORMAT_VERSION}")

    strings = _read_string_table(reader)
    namespace = _string_ref(strings, reader.read_ue(), "namespace")
    typename = _string_ref(strings, reader.read_ue(), "typename")
    root_type_id = _read_root_type_id(reader)

    has_user_type_id = bool(reader.read_bit())
    user_type_id = reader.read_ue() if has_user_type_id else NO_USER_TYPE_ID

    expr_entries = _read_expr_entries(reader)
    field_count = reader.read_ue()

    class_name = f"{namespace}.{typename}" if namespace else typename
    field_types_cache: Dict[int, FieldType] = {}
    fields: List[FieldInfo] = []
    for _ in range(field_count):
        has_tag_id = bool(reader.read_bit())
        if has_tag_id:
            tag_id = reader.read_ue()
            field_name = f"__tag_{tag_id}__"
        else:
            tag_id = -1
            field_name = _string_ref(strings, reader.read_ue(), "field name")
        expr_id = reader.read_ue()
        field_type = _build_field_type(expr_id, expr_entries, field_types_cache, set())
        fields.append(FieldInfo(field_name, field_type, class_name, tag_id))

    return TypeDef(
        namespace=namespace,
        typename=typename,
        cls=None,
        type_id=root_type_id,
        fields=fields,
        encoded=encoded,
        is_compressed=False,
        user_type_id=user_type_id,
    )


def semantic_fingerprint(typedef: TypeDef) -> Tuple:
    """
    Build a deterministic semantic fingerprint suitable for equality checks.
    """
    canonical_fields = _canonicalize_fields(typedef.fields)
    normalized_user_type_id = typedef.user_type_id
    if normalized_user_type_id in (None, NO_USER_TYPE_ID):
        normalized_user_type_id = NO_USER_TYPE_ID
    field_part = tuple(
        (
            field.name if not field.uses_tag_id() else f"__tag_{field.tag_id}__",
            int(field.tag_id),
            _freeze_field_type(field.field_type),
        )
        for field in canonical_fields
    )
    return (
        typedef.namespace,
        typedef.typename,
        int(typedef.type_id),
        int(normalized_user_type_id),
        field_part,
    )


def _canonicalize_fields(fields: Iterable[FieldInfo]) -> List[FieldInfo]:
    def sort_key(field: FieldInfo):
        if field.uses_tag_id():
            return (0, int(field.tag_id), field.name)
        return (1, field.name)

    return sorted(list(fields), key=sort_key)


def _freeze_field_type(field_type: FieldType) -> ExprKey:
    type_id = int(field_type.type_id)
    base = (type_id, bool(field_type.is_nullable), bool(field_type.is_tracking_ref))
    if type_id in (TypeId.LIST, TypeId.SET):
        return base + (_freeze_field_type(field_type.element_type),)
    if type_id == TypeId.MAP:
        return base + (
            _freeze_field_type(field_type.key_type),
            _freeze_field_type(field_type.value_type),
        )
    return base


def _expr_children(expr_key: ExprKey) -> Tuple[ExprKey, ...]:
    if len(expr_key) <= 3:
        return ()
    return tuple(expr_key[3:])


def _count_exprs(expr_keys: Iterable[ExprKey]) -> Counter:
    counter: Counter = Counter()

    def visit(expr_key: ExprKey) -> None:
        counter[expr_key] += 1
        for child in _expr_children(expr_key):
            visit(child)

    for expr_key in expr_keys:
        visit(expr_key)
    return counter


def _expr_sort_key(expr_key: ExprKey) -> Tuple:
    prefix = (int(expr_key[0]), int(bool(expr_key[1])), int(bool(expr_key[2])), len(expr_key))
    if len(expr_key) <= 3:
        return prefix
    return prefix + tuple(_expr_sort_key(child) for child in _expr_children(expr_key))


def _write_expr_entry(writer: _BitWriter, expr_key: ExprKey, expr_to_id: Dict[ExprKey, int]) -> None:
    type_id = int(expr_key[0])
    _write_expr_type_id(writer, type_id)
    writer.write_bit(1 if expr_key[1] else 0)
    writer.write_bit(1 if expr_key[2] else 0)
    if type_id in (TypeId.LIST, TypeId.SET):
        writer.write_ue(expr_to_id[expr_key[3]])
    elif type_id == TypeId.MAP:
        writer.write_ue(expr_to_id[expr_key[3]])
        writer.write_ue(expr_to_id[expr_key[4]])


def _expr_entry_from_key(expr_key: ExprKey, expr_to_id: Dict[ExprKey, int]) -> Tuple:
    type_id = int(expr_key[0])
    is_nullable = bool(expr_key[1])
    is_tracking_ref = bool(expr_key[2])
    if type_id in (TypeId.LIST, TypeId.SET):
        return (type_id, is_nullable, is_tracking_ref, expr_to_id[expr_key[3]])
    if type_id == TypeId.MAP:
        return (
            type_id,
            is_nullable,
            is_tracking_ref,
            expr_to_id[expr_key[3]],
            expr_to_id[expr_key[4]],
        )
    return (type_id, is_nullable, is_tracking_ref)


def _write_expr_entry_from_entry(writer: _BitWriter, entry: Tuple) -> None:
    type_id = int(entry[0])
    _write_expr_type_id(writer, type_id)
    writer.write_bit(1 if entry[1] else 0)
    writer.write_bit(1 if entry[2] else 0)
    if type_id in (TypeId.LIST, TypeId.SET):
        writer.write_ue(int(entry[3]))
    elif type_id == TypeId.MAP:
        writer.write_ue(int(entry[3]))
        writer.write_ue(int(entry[4]))


def _expr_key_from_entries(expr_id: int, expr_entries: Tuple[Tuple, ...], cache: Dict[int, ExprKey]) -> ExprKey:
    cached = cache.get(expr_id)
    if cached is not None:
        return cached
    if expr_id < 0 or expr_id >= len(expr_entries):
        raise ValueError(f"Invalid expression id {expr_id}, max is {len(expr_entries) - 1}")
    entry = expr_entries[expr_id]
    type_id = int(entry[0])
    head = (type_id, bool(entry[1]), bool(entry[2]))
    if type_id in (TypeId.LIST, TypeId.SET):
        expr_key = head + (_expr_key_from_entries(int(entry[3]), expr_entries, cache),)
    elif type_id == TypeId.MAP:
        expr_key = head + (
            _expr_key_from_entries(int(entry[3]), expr_entries, cache),
            _expr_key_from_entries(int(entry[4]), expr_entries, cache),
        )
    else:
        expr_key = head
    cache[expr_id] = expr_key
    return expr_key


def _read_expr_entries(reader: _BitReader) -> List[Tuple]:
    entry_count = reader.read_ue()
    entries: List[Tuple] = []
    for _ in range(entry_count):
        type_id = _read_expr_type_id(reader)
        is_nullable = bool(reader.read_bit())
        is_tracking_ref = bool(reader.read_bit())
        if type_id in (TypeId.LIST, TypeId.SET):
            child_id = reader.read_ue()
            entries.append((type_id, is_nullable, is_tracking_ref, child_id))
        elif type_id == TypeId.MAP:
            key_id = reader.read_ue()
            value_id = reader.read_ue()
            entries.append((type_id, is_nullable, is_tracking_ref, key_id, value_id))
        else:
            entries.append((type_id, is_nullable, is_tracking_ref))
    return entries


def _build_field_type(
    expr_id: int,
    expr_entries: List[Tuple],
    cache: Dict[int, FieldType],
    visiting: set,
) -> FieldType:
    if expr_id in cache:
        return cache[expr_id]
    if expr_id < 0 or expr_id >= len(expr_entries):
        raise ValueError(f"Invalid expression id {expr_id}, max is {len(expr_entries) - 1}")
    if expr_id in visiting:
        raise ValueError(f"Cyclic expression graph detected for expr id {expr_id}")

    visiting.add(expr_id)
    entry = expr_entries[expr_id]
    type_id = int(entry[0])
    is_nullable = bool(entry[1])
    is_tracking_ref = bool(entry[2])

    if type_id in (TypeId.LIST, TypeId.SET):
        child = _build_field_type(int(entry[3]), expr_entries, cache, visiting)
        result = CollectionFieldType(
            type_id=type_id,
            is_monomorphic=not is_polymorphic_type(type_id),
            is_nullable=is_nullable,
            is_tracking_ref=is_tracking_ref,
            element_type=child,
        )
    elif type_id == TypeId.MAP:
        key_type = _build_field_type(int(entry[3]), expr_entries, cache, visiting)
        value_type = _build_field_type(int(entry[4]), expr_entries, cache, visiting)
        result = MapFieldType(
            type_id=type_id,
            is_monomorphic=not is_polymorphic_type(type_id),
            is_nullable=is_nullable,
            is_tracking_ref=is_tracking_ref,
            key_type=key_type,
            value_type=value_type,
        )
    elif type_id in _DYNAMIC_TYPE_IDS:
        result = DynamicFieldType(
            type_id=type_id,
            is_monomorphic=False,
            is_nullable=is_nullable,
            is_tracking_ref=is_tracking_ref,
            user_type_id=NO_USER_TYPE_ID,
        )
    else:
        result = FieldType(
            type_id=type_id,
            is_monomorphic=not is_polymorphic_type(type_id),
            is_nullable=is_nullable,
            is_tracking_ref=is_tracking_ref,
            user_type_id=NO_USER_TYPE_ID,
        )

    visiting.remove(expr_id)
    cache[expr_id] = result
    return result


def _string_ref(strings: List[str], idx: int, label: str) -> str:
    if idx < 0 or idx >= len(strings):
        raise ValueError(f"Invalid {label} string id {idx}, max is {len(strings) - 1}")
    return strings[idx]


def _bundle_string_id(bundle: SemanticBundle, value: str, label: str) -> int:
    idx = bundle.string_to_id.get(value)
    if idx is None:
        raise ValueError(f"{label} '{value}' is not in bundle string table")
    return idx


def _write_root_type_id(writer: _BitWriter, value: int) -> None:
    code = _ROOT_TYPE_ID_TO_CODE.get(value)
    if code is not None:
        writer.write_bit(1)
        writer.write_bits(code, _ROOT_TYPE_ID_BITS)
        return
    writer.write_bit(0)
    writer.write_ue(value)


def _read_root_type_id(reader: _BitReader) -> int:
    is_short = bool(reader.read_bit())
    if is_short:
        code = reader.read_bits(_ROOT_TYPE_ID_BITS)
        if code >= len(_ROOT_TYPE_ID_SHORTLIST):
            raise ValueError(f"Invalid root type shortcut code {code}")
        return int(_ROOT_TYPE_ID_SHORTLIST[code])
    return reader.read_ue()


def _write_expr_type_id(writer: _BitWriter, value: int) -> None:
    code = _EXPR_TYPE_ID_TO_CODE.get(value)
    if code is not None:
        writer.write_bit(1)
        writer.write_bits(code, _EXPR_TYPE_ID_BITS)
        return
    writer.write_bit(0)
    writer.write_ue(value)


def _read_expr_type_id(reader: _BitReader) -> int:
    is_short = bool(reader.read_bit())
    if is_short:
        code = reader.read_bits(_EXPR_TYPE_ID_BITS)
        if code >= len(_EXPR_TYPE_ID_SHORTLIST):
            raise ValueError(f"Invalid expr type shortcut code {code}")
        return int(_EXPR_TYPE_ID_SHORTLIST[code])
    return reader.read_ue()


def _ue_bits(value: int) -> int:
    if value < 0:
        raise ValueError(f"value must be >= 0, got {value}")
    return 2 * (value + 1).bit_length() - 1


def _write_string_table(writer: _BitWriter, strings: List[str]) -> None:
    writer.write_ue(len(strings))
    if not strings:
        return
    previous = ""
    for idx, value in enumerate(strings):
        if idx == 0:
            _write_string(writer, value)
        else:
            lcp = _common_prefix_len(previous, value)
            writer.write_ue(lcp)
            _write_string(writer, value[lcp:])
        previous = value


def _read_string_table(reader: _BitReader) -> List[str]:
    count = reader.read_ue()
    values: List[str] = []
    previous = ""
    for idx in range(count):
        if idx == 0:
            value = _read_string(reader)
        else:
            lcp = reader.read_ue()
            if lcp > len(previous):
                raise ValueError(f"Invalid LCP {lcp}, previous length is {len(previous)}")
            suffix = _read_string(reader)
            value = previous[:lcp] + suffix
        values.append(value)
        previous = value
    return values


def _common_prefix_len(left: str, right: str) -> int:
    limit = min(len(left), len(right))
    idx = 0
    while idx < limit and left[idx] == right[idx]:
        idx += 1
    return idx


def _write_string(writer: _BitWriter, value: str) -> None:
    mode = _choose_string_mode(value)
    writer.write_bits(mode, 2)
    if mode == 0:
        writer.write_ue(len(value))
        for char in value:
            writer.write_bits(_LOWER_SPECIAL_TO_CODE[char], 5)
        return
    if mode == 1:
        writer.write_ue(len(value))
        for char in value:
            writer.write_bits(_LUDS_TO_CODE[char], 6)
        return
    if mode == 2:
        writer.write_ue(len(value))
        for char in value:
            writer.write_bits(ord(char), 7)
        return

    encoded = value.encode("utf-8")
    writer.write_ue(len(encoded))
    for byte in encoded:
        writer.write_bits(byte, 8)


def _read_string(reader: _BitReader) -> str:
    mode = reader.read_bits(2)
    if mode == 0:
        length = reader.read_ue()
        chars = [_LOWER_SPECIAL_FROM_CODE[reader.read_bits(5)] for _ in range(length)]
        return "".join(chars)
    if mode == 1:
        length = reader.read_ue()
        chars = [_LUDS_FROM_CODE[reader.read_bits(6)] for _ in range(length)]
        return "".join(chars)
    if mode == 2:
        length = reader.read_ue()
        chars = [chr(reader.read_bits(7)) for _ in range(length)]
        return "".join(chars)
    if mode == 3:
        length = reader.read_ue()
        payload = bytes(reader.read_bits(8) for _ in range(length))
        return payload.decode("utf-8")
    raise ValueError(f"Unsupported string mode {mode}")


def _choose_string_mode(value: str) -> int:
    if value == "":
        return 0

    candidates: List[Tuple[int, int]] = []

    if all(char in _LOWER_SPECIAL_TO_CODE for char in value):
        bits = 2 + _ue_bits(len(value)) + 5 * len(value)
        candidates.append((0, bits))
    if all(char in _LUDS_TO_CODE for char in value):
        bits = 2 + _ue_bits(len(value)) + 6 * len(value)
        candidates.append((1, bits))
    if all(ord(char) <= 0x7F for char in value):
        bits = 2 + _ue_bits(len(value)) + 7 * len(value)
        candidates.append((2, bits))

    utf8 = value.encode("utf-8")
    candidates.append((3, 2 + _ue_bits(len(utf8)) + 8 * len(utf8)))

    candidates.sort(key=lambda item: (item[1], item[0]))
    return candidates[0][0]
