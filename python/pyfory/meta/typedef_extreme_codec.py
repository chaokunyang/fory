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
Prototype "direct TypeDef" compressor.

This codec does not encode the existing TypeDef binary bytes. Instead, it encodes
the TypeDef semantic model directly:

- field flags are columnar bitsets (no per-field byte header)
- field type trees are deduplicated as a DAG dictionary
- field names are delta-coded with LCP + 6-bit alphabet packing

The implementation is designed for tiny payloads where one-shot compression ratio
matters more than encoder speed.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Sequence, Tuple

from pyfory._fory import NO_USER_TYPE_ID
from pyfory.meta.typedef import TypeDef
from pyfory.types import TypeId

_CODEC_VERSION = 0b01
_ALPHA_5_ALPHABET = "abcdefghijklmnopqrstuvwxyz_.$|"
_ALPHA_5_TO_INDEX = {c: i for i, c in enumerate(_ALPHA_5_ALPHABET)}
_ALPHA_6_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_."
_ALPHA_6_TO_INDEX = {c: i for i, c in enumerate(_ALPHA_6_ALPHABET)}
_NAME_MODE_ALPHA5 = 0b00
_NAME_MODE_ALPHA6 = 0b01
_NAME_MODE_UTF8 = 0b10
_NAME_MODE_TOKEN = 0b11

_SEPARATORS = "._$|"
_SEP_TO_ID = {c: i for i, c in enumerate(_SEPARATORS)}

_TOKEN_CASE_LOWER = 0b00
_TOKEN_CASE_CAPITALIZED = 0b01
_TOKEN_CASE_UPPER = 0b10
_TOKEN_CASE_RAW = 0b11

_TOKEN_STYLE_GENERIC = 0b00
_TOKEN_STYLE_SNAKE = 0b01
_TOKEN_STYLE_UPPER_CAMEL = 0b10
_TOKEN_STYLE_LOWER_CAMEL = 0b11

_COMMON_TOKENS: Tuple[str, ...] = ()
_TOKEN_TO_INDEX: Dict[str, int] = {}
_TOKEN_INDEX_WIDTH = 0

# Fixed prefix codebook tuned for common struct metadata type IDs.
_TYPE_ID_CODES = {
    TypeId.INT32: "00",
    TypeId.STRING: "01",
    TypeId.INT64: "100",
    TypeId.BOOL: "1010",
    TypeId.FLOAT64: "1011",
    TypeId.LIST: "1100",
    TypeId.MAP: "1101",
    TypeId.SET: "11100",
    TypeId.STRUCT: "111010",
    TypeId.COMPATIBLE_STRUCT: "111011",
    TypeId.BINARY: "111100",
    TypeId.UNKNOWN: "111101",
}
_TYPE_ID_ESCAPE_CODE = "111111"
_TYPE_ID_DECODE = {bits: type_id for type_id, bits in _TYPE_ID_CODES.items()}
_TYPE_ID_DECODE[_TYPE_ID_ESCAPE_CODE] = None


@dataclass(frozen=True)
class TypeEdge:
    nullable: bool
    tracking_ref: bool
    node: "TypeNode"


@dataclass(frozen=True)
class TypeNode:
    type_id: int
    children: Tuple[TypeEdge, ...] = ()


@dataclass(frozen=True)
class CanonicalField:
    name: str
    tag_id: int
    nullable: bool
    tracking_ref: bool
    type_node: TypeNode


@dataclass(frozen=True)
class CanonicalTypeDef:
    register_by_name: bool
    namespace: str
    typename: str
    type_id: int
    user_type_id: int
    fields: Tuple[CanonicalField, ...]


class _BitWriter:
    __slots__ = ("_data", "_current", "_bits_filled")

    def __init__(self):
        self._data = bytearray()
        self._current = 0
        self._bits_filled = 0

    def write_bit(self, bit: int) -> None:
        self._current = (self._current << 1) | (bit & 1)
        self._bits_filled += 1
        if self._bits_filled == 8:
            self._data.append(self._current)
            self._current = 0
            self._bits_filled = 0

    def write_bits(self, value: int, width: int) -> None:
        for shift in range(width - 1, -1, -1):
            self.write_bit((value >> shift) & 1)

    def write_ue(self, value: int) -> None:
        if value < 0:
            raise ValueError(f"UE code only supports non-negative values, got {value}")
        code_num = value + 1
        bit_length = code_num.bit_length()
        for _ in range(bit_length - 1):
            self.write_bit(0)
        self.write_bits(code_num, bit_length)

    def write_se(self, value: int) -> None:
        mapped = (-value) * 2 if value <= 0 else value * 2 - 1
        self.write_ue(mapped)

    def to_bytes(self) -> bytes:
        if self._bits_filled:
            self._data.append(self._current << (8 - self._bits_filled))
            self._current = 0
            self._bits_filled = 0
        return bytes(self._data)


class _BitReader:
    __slots__ = ("_data", "_byte_index", "_bit_index")

    def __init__(self, data: bytes):
        self._data = data
        self._byte_index = 0
        self._bit_index = 0

    def read_bit(self) -> int:
        if self._byte_index >= len(self._data):
            raise ValueError("Unexpected end of stream")
        byte = self._data[self._byte_index]
        bit = (byte >> (7 - self._bit_index)) & 1
        self._bit_index += 1
        if self._bit_index == 8:
            self._bit_index = 0
            self._byte_index += 1
        return bit

    def read_bits(self, width: int) -> int:
        value = 0
        for _ in range(width):
            value = (value << 1) | self.read_bit()
        return value

    def read_ue(self) -> int:
        leading_zeros = 0
        while self.read_bit() == 0:
            leading_zeros += 1
        if leading_zeros == 0:
            return 0
        payload = self.read_bits(leading_zeros)
        return ((1 << leading_zeros) | payload) - 1

    def read_se(self) -> int:
        mapped = self.read_ue()
        if mapped % 2 == 0:
            return -(mapped // 2)
        return (mapped + 1) // 2


def canonicalize_typedef(type_def: TypeDef) -> CanonicalTypeDef:
    register_by_name = TypeId.is_namespaced_type(type_def.type_id)
    namespace = type_def.namespace if register_by_name else ""
    typename = type_def.typename if register_by_name else ""
    user_type_id = type_def.user_type_id
    if user_type_id is None:
        user_type_id = NO_USER_TYPE_ID

    fields = []
    for field in type_def.fields:
        tag_id = field.tag_id
        field_name = field.name if tag_id < 0 else f"__tag_{tag_id}__"
        field_type = field.field_type
        fields.append(
            CanonicalField(
                name=field_name,
                tag_id=tag_id,
                nullable=field_type.is_nullable,
                tracking_ref=field_type.is_tracking_ref,
                type_node=_to_type_node(field_type),
            )
        )
    return CanonicalTypeDef(
        register_by_name=register_by_name,
        namespace=namespace,
        typename=typename,
        type_id=type_def.type_id,
        user_type_id=user_type_id,
        fields=tuple(fields),
    )


def encode_extreme_typedef(
    type_def: TypeDef,
    token_dictionary: Sequence[str] | None = None,
    write_token_dictionary: bool = True,
    shared_named_type_table: Dict[Tuple[str, str], int] | None = None,
) -> bytes:
    return encode_canonical_typedef(
        canonicalize_typedef(type_def),
        token_dictionary=token_dictionary,
        write_token_dictionary=write_token_dictionary,
        shared_named_type_table=shared_named_type_table,
    )


def encode_canonical_typedef(
    canonical: CanonicalTypeDef,
    token_dictionary: Sequence[str] | None = None,
    write_token_dictionary: bool = True,
    shared_named_type_table: Dict[Tuple[str, str], int] | None = None,
) -> bytes:
    writer = _BitWriter()
    writer.write_bits(_CODEC_VERSION, 2)
    writer.write_bit(1 if canonical.register_by_name else 0)
    writer.write_ue(len(canonical.fields))
    if token_dictionary is None:
        if write_token_dictionary:
            _write_token_dictionary(writer, canonical)
        else:
            _set_token_dictionary(_build_token_dictionary(canonical))
    else:
        _set_token_dictionary(tuple(token_dictionary))
        if write_token_dictionary:
            writer.write_ue(len(_COMMON_TOKENS))
            for token in _COMMON_TOKENS:
                _write_raw_name(writer, token)

    _write_type_id(writer, canonical.type_id)
    if canonical.register_by_name:
        if shared_named_type_table is None:
            _write_name(writer, canonical.namespace)
            _write_name(writer, canonical.typename)
        else:
            key = (canonical.namespace, canonical.typename)
            if key not in shared_named_type_table:
                raise ValueError(f"Missing shared named type entry for {key}")
            writer.write_ue(shared_named_type_table[key])
    else:
        writer.write_ue(canonical.user_type_id + 1)

    tag_mask = [field.tag_id >= 0 for field in canonical.fields]
    nullable_mask = [field.nullable for field in canonical.fields]
    tracking_mask = [field.tracking_ref for field in canonical.fields]
    _write_mask(writer, tag_mask)
    _write_mask(writer, nullable_mask)
    _write_mask(writer, tracking_mask)

    _write_tag_ids(writer, canonical.fields, tag_mask)
    _write_type_nodes(writer, canonical.fields)
    _write_field_names(writer, canonical.fields, tag_mask)
    _reset_token_dictionary()
    return writer.to_bytes()


def decode_extreme_typedef(
    data: bytes,
    token_dictionary: Sequence[str] | None = None,
    read_token_dictionary: bool = True,
    shared_named_type_table: Sequence[Tuple[str, str]] | None = None,
) -> CanonicalTypeDef:
    reader = _BitReader(data)
    version = reader.read_bits(2)
    if version != _CODEC_VERSION:
        raise ValueError(f"Unsupported typedef extreme codec version {version}")

    register_by_name = bool(reader.read_bit())
    field_count = reader.read_ue()
    if token_dictionary is None:
        if read_token_dictionary:
            _read_token_dictionary(reader)
        else:
            _set_token_dictionary(())
    else:
        _set_token_dictionary(tuple(token_dictionary))
        if read_token_dictionary:
            # Keep stream compatibility if caller still wants inline dictionary.
            _read_token_dictionary(reader)

    type_id = _read_type_id(reader)
    if register_by_name:
        if shared_named_type_table is None:
            namespace = _read_name(reader)
            typename = _read_name(reader)
        else:
            named_type_index = reader.read_ue()
            if named_type_index >= len(shared_named_type_table):
                raise ValueError(f"Invalid shared named type index {named_type_index}")
            namespace, typename = shared_named_type_table[named_type_index]
        user_type_id = NO_USER_TYPE_ID
    else:
        namespace = ""
        typename = ""
        user_type_id = reader.read_ue() - 1

    tag_mask = _read_mask(reader, field_count)
    nullable_mask = _read_mask(reader, field_count)
    tracking_mask = _read_mask(reader, field_count)
    tag_values = _read_tag_ids(reader, tag_mask)
    field_nodes = _read_type_nodes(reader, field_count)
    field_names = _read_field_names(reader, tag_mask)

    fields: List[CanonicalField] = []
    for i in range(field_count):
        tag_id = tag_values.get(i, -1)
        name = field_names[i]
        if tag_id >= 0:
            name = f"__tag_{tag_id}__"
        fields.append(
            CanonicalField(
                name=name,
                tag_id=tag_id,
                nullable=nullable_mask[i],
                tracking_ref=tracking_mask[i],
                type_node=field_nodes[i],
            )
        )
    result = CanonicalTypeDef(
        register_by_name=register_by_name,
        namespace=namespace,
        typename=typename,
        type_id=type_id,
        user_type_id=user_type_id,
        fields=tuple(fields),
    )
    _reset_token_dictionary()
    return result


def _write_token_dictionary(writer: _BitWriter, canonical: CanonicalTypeDef) -> None:
    tokens = _build_token_dictionary(canonical)
    writer.write_ue(len(tokens))
    for token in tokens:
        _write_raw_name(writer, token)
    _set_token_dictionary(tokens)


def _read_token_dictionary(reader: _BitReader) -> None:
    token_count = reader.read_ue()
    tokens = [_read_raw_name(reader) for _ in range(token_count)]
    _set_token_dictionary(tokens)


def _set_token_dictionary(tokens: Sequence[str]) -> None:
    global _COMMON_TOKENS, _TOKEN_TO_INDEX, _TOKEN_INDEX_WIDTH
    _COMMON_TOKENS = tuple(tokens)
    _TOKEN_TO_INDEX = {token: i for i, token in enumerate(_COMMON_TOKENS)}
    _TOKEN_INDEX_WIDTH = _bit_width(len(_COMMON_TOKENS) - 1) if _COMMON_TOKENS else 0


def _reset_token_dictionary() -> None:
    _set_token_dictionary(())


def _build_token_dictionary(canonical: CanonicalTypeDef) -> Tuple[str, ...]:
    return _build_token_dictionary_from_names(_names_for_dictionary(canonical))


def build_shared_token_dictionary(canonicals: Sequence[CanonicalTypeDef]) -> Tuple[str, ...]:
    names: List[str] = []
    for canonical in canonicals:
        names.extend(_names_for_dictionary(canonical))
    return _build_token_dictionary_from_names(names)


def build_shared_named_type_table(canonicals: Sequence[CanonicalTypeDef]) -> Tuple[Dict[Tuple[str, str], int], Tuple[Tuple[str, str], ...]]:
    ordered: List[Tuple[str, str]] = []
    seen = set()
    for canonical in canonicals:
        if not canonical.register_by_name:
            continue
        key = (canonical.namespace, canonical.typename)
        if key in seen:
            continue
        seen.add(key)
        ordered.append(key)
    mapping = {key: i for i, key in enumerate(ordered)}
    return mapping, tuple(ordered)


def measure_token_dictionary_wire_size(tokens: Sequence[str]) -> int:
    writer = _BitWriter()
    writer.write_ue(len(tokens))
    for token in tokens:
        _write_raw_name(writer, token)
    return len(writer.to_bytes())


def _build_token_dictionary_from_names(names: Sequence[str]) -> Tuple[str, ...]:
    frequencies: Dict[str, int] = {}
    for name in names:
        for token in _candidate_tokens_from_name(name):
            frequencies[token] = frequencies.get(token, 0) + 1
    if not frequencies:
        return ()

    candidates = sorted(
        frequencies.keys(),
        key=lambda token: (frequencies[token] * _raw_name_bits(token), frequencies[token], len(token), token),
        reverse=True,
    )
    max_tokens = min(127, len(candidates))
    best_net = 0
    best_tokens: Tuple[str, ...] = ()
    for size in range(1, max_tokens + 1):
        selected = candidates[:size]
        width = _bit_width(size - 1) if size > 0 else 0
        dict_bits = _ue_bit_length(size) + sum(_raw_name_bits(token) for token in selected)
        gain_bits = 0
        for token in selected:
            raw_bits = _raw_name_bits(token)
            use_dict_bits = 1 + width
            gain_bits += frequencies[token] * (raw_bits - use_dict_bits)
        net = gain_bits - dict_bits
        if net > best_net:
            best_net = net
            best_tokens = tuple(selected)
    return best_tokens


def _names_for_dictionary(canonical: CanonicalTypeDef) -> List[str]:
    names: List[str] = []
    if canonical.register_by_name:
        names.append(canonical.namespace)
        names.append(canonical.typename)
    for field in canonical.fields:
        if field.tag_id < 0:
            names.append(field.name)
    return names


def _candidate_tokens_from_name(name: str) -> List[str]:
    tokens = []
    for piece_type, piece_value in _tokenize_name(name):
        if piece_type != "tok":
            continue
        normalized = piece_value.lower()
        if not normalized:
            continue
        if normalized[0].isdigit() and len(normalized) == 1:
            continue
        tokens.append(normalized)
    return tokens


def _to_type_node(field_type) -> TypeNode:
    type_id = field_type.type_id
    if type_id in (TypeId.LIST, TypeId.SET):
        element = field_type.element_type
        child = TypeEdge(
            nullable=element.is_nullable,
            tracking_ref=element.is_tracking_ref,
            node=_to_type_node(element),
        )
        return TypeNode(type_id=type_id, children=(child,))

    if type_id == TypeId.MAP:
        key = field_type.key_type
        value = field_type.value_type
        key_edge = TypeEdge(
            nullable=key.is_nullable,
            tracking_ref=key.is_tracking_ref,
            node=_to_type_node(key),
        )
        value_edge = TypeEdge(
            nullable=value.is_nullable,
            tracking_ref=value.is_tracking_ref,
            node=_to_type_node(value),
        )
        return TypeNode(type_id=type_id, children=(key_edge, value_edge))
    return TypeNode(type_id=type_id, children=())


def _write_code_bits(writer: _BitWriter, code: str) -> None:
    for bit in code:
        writer.write_bit(1 if bit == "1" else 0)


def _write_type_id(writer: _BitWriter, type_id: int) -> None:
    bits = _TYPE_ID_CODES.get(type_id)
    if bits is not None:
        _write_code_bits(writer, bits)
        return
    _write_code_bits(writer, _TYPE_ID_ESCAPE_CODE)
    writer.write_ue(type_id)


def _read_type_id(reader: _BitReader) -> int:
    prefix = ""
    for _ in range(6):
        prefix += "1" if reader.read_bit() == 1 else "0"
        if prefix in _TYPE_ID_DECODE:
            type_id = _TYPE_ID_DECODE[prefix]
            if type_id is None:
                return reader.read_ue()
            return type_id
    raise ValueError(f"Invalid type id prefix {prefix}")


def _write_mask(writer: _BitWriter, bits: Sequence[bool]) -> None:
    count = len(bits)
    if count == 0:
        writer.write_bits(0b00, 2)
        return
    if all(not bit for bit in bits):
        writer.write_bits(0b00, 2)
        return
    if all(bits):
        writer.write_bits(0b01, 2)
        return
    writer.write_bits(0b10, 2)
    for bit in bits:
        writer.write_bit(1 if bit else 0)


def _read_mask(reader: _BitReader, count: int) -> List[bool]:
    mode = reader.read_bits(2)
    if mode == 0b00:
        return [False] * count
    if mode == 0b01:
        return [True] * count
    if mode == 0b10:
        return [reader.read_bit() == 1 for _ in range(count)]
    raise ValueError(f"Unsupported mask mode {mode:02b}")


def _write_tag_ids(writer: _BitWriter, fields: Sequence[CanonicalField], tag_mask: Sequence[bool]) -> None:
    tag_values = [fields[i].tag_id for i in range(len(fields)) if tag_mask[i]]
    if not tag_values:
        return
    if _tag_ids_arithmetic_bits(tag_values) < _tag_ids_delta_bits(tag_values):
        writer.write_bit(1)
        _write_tag_ids_arithmetic(writer, tag_values)
        return
    writer.write_bit(0)
    _write_tag_ids_delta(writer, tag_values)


def _read_tag_ids(reader: _BitReader, tag_mask: Sequence[bool]) -> Dict[int, int]:
    positions = [i for i, is_tag in enumerate(tag_mask) if is_tag]
    tag_values: Dict[int, int] = {}
    if not positions:
        return tag_values
    arithmetic_mode = reader.read_bit() == 1
    if arithmetic_mode:
        values = _read_tag_ids_arithmetic(reader, len(positions))
    else:
        values = _read_tag_ids_delta(reader, len(positions))

    for pos, value in zip(positions, values):
        if value < 0:
            raise ValueError(f"Decoded negative tag ID at field index {pos}")
        tag_values[pos] = value
    return tag_values


def _tag_ids_delta_bits(values: Sequence[int]) -> int:
    bits = 1 + _ue_bit_length(values[0])
    prev = values[0]
    for value in values[1:]:
        bits += _ue_bit_length(_se_to_ue(value - prev))
        prev = value
    return bits


def _tag_ids_arithmetic_bits(values: Sequence[int]) -> int:
    bits = 1 + _ue_bit_length(values[0])
    if len(values) >= 2:
        bits += _ue_bit_length(_se_to_ue(values[1] - values[0]))
    return bits


def _write_tag_ids_delta(writer: _BitWriter, values: Sequence[int]) -> None:
    writer.write_ue(values[0])
    prev = values[0]
    for value in values[1:]:
        writer.write_se(value - prev)
        prev = value


def _read_tag_ids_delta(reader: _BitReader, count: int) -> List[int]:
    values = [reader.read_ue()]
    prev = values[0]
    for _ in range(1, count):
        prev += reader.read_se()
        values.append(prev)
    return values


def _write_tag_ids_arithmetic(writer: _BitWriter, values: Sequence[int]) -> None:
    writer.write_ue(values[0])
    if len(values) >= 2:
        writer.write_se(values[1] - values[0])


def _read_tag_ids_arithmetic(reader: _BitReader, count: int) -> List[int]:
    first = reader.read_ue()
    if count == 1:
        return [first]
    step = reader.read_se()
    return [first + i * step for i in range(count)]


def _se_to_ue(value: int) -> int:
    return (-value) * 2 if value <= 0 else value * 2 - 1


def _write_type_nodes(writer: _BitWriter, fields: Sequence[CanonicalField]) -> None:
    use_dictionary = _should_use_type_dictionary(fields)
    writer.write_bit(1 if use_dictionary else 0)
    if use_dictionary:
        _write_type_nodes_dictionary(writer, fields)
        return
    _write_type_nodes_inline(writer, fields)


def _read_type_nodes(reader: _BitReader, field_count: int) -> List[TypeNode]:
    use_dictionary = reader.read_bit() == 1
    if use_dictionary:
        return _read_type_nodes_dictionary(reader, field_count)
    return _read_type_nodes_inline(reader, field_count)


def _write_type_nodes_inline(writer: _BitWriter, fields: Sequence[CanonicalField]) -> None:
    for field in fields:
        _write_type_tree(writer, field.type_node)


def _read_type_nodes_inline(reader: _BitReader, field_count: int) -> List[TypeNode]:
    return [_read_type_tree(reader) for _ in range(field_count)]


def _write_type_nodes_dictionary(writer: _BitWriter, fields: Sequence[CanonicalField]) -> None:
    top_nodes = [field.type_node for field in fields]
    node_order = _collect_node_order(top_nodes)
    writer.write_ue(len(node_order))
    if not node_order:
        return

    node_to_id = {node: i for i, node in enumerate(node_order)}
    width = _bit_width(len(node_order) - 1)
    for node in node_order:
        _write_type_id(writer, node.type_id)
        for edge in node.children:
            writer.write_bit(1 if edge.nullable else 0)
            writer.write_bit(1 if edge.tracking_ref else 0)
            if width > 0:
                writer.write_bits(node_to_id[edge.node], width)

    for node in top_nodes:
        if width > 0:
            writer.write_bits(node_to_id[node], width)


def _read_type_nodes_dictionary(reader: _BitReader, field_count: int) -> List[TypeNode]:
    node_count = reader.read_ue()
    if field_count > 0 and node_count == 0:
        raise ValueError("Non-empty fields require at least one type node")
    if node_count == 0:
        return []

    width = _bit_width(node_count - 1)
    nodes: List[TypeNode] = []
    for _ in range(node_count):
        type_id = _read_type_id(reader)
        edges: List[TypeEdge] = []
        for _ in range(_type_arity(type_id)):
            nullable = reader.read_bit() == 1
            tracking_ref = reader.read_bit() == 1
            child_id = reader.read_bits(width) if width > 0 else 0
            if child_id >= len(nodes):
                raise ValueError(f"Invalid child type node id {child_id}")
            edges.append(TypeEdge(nullable=nullable, tracking_ref=tracking_ref, node=nodes[child_id]))
        nodes.append(TypeNode(type_id=type_id, children=tuple(edges)))

    if width == 0:
        return [nodes[0] for _ in range(field_count)]
    return [nodes[reader.read_bits(width)] for _ in range(field_count)]


def _write_type_tree(writer: _BitWriter, node: TypeNode) -> None:
    _write_type_id(writer, node.type_id)
    for edge in node.children:
        writer.write_bit(1 if edge.nullable else 0)
        writer.write_bit(1 if edge.tracking_ref else 0)
        _write_type_tree(writer, edge.node)


def _read_type_tree(reader: _BitReader) -> TypeNode:
    type_id = _read_type_id(reader)
    edges = []
    for _ in range(_type_arity(type_id)):
        nullable = reader.read_bit() == 1
        tracking_ref = reader.read_bit() == 1
        edges.append(
            TypeEdge(
                nullable=nullable,
                tracking_ref=tracking_ref,
                node=_read_type_tree(reader),
            )
        )
    return TypeNode(type_id=type_id, children=tuple(edges))


def _should_use_type_dictionary(fields: Sequence[CanonicalField]) -> bool:
    if not fields:
        return False
    return _type_dictionary_bits(fields) <= _type_inline_bits(fields)


def _type_inline_bits(fields: Sequence[CanonicalField]) -> int:
    bits = 1  # type encoding mode bit
    for field in fields:
        bits += _type_tree_bits(field.type_node)
    return bits


def _type_dictionary_bits(fields: Sequence[CanonicalField]) -> int:
    top_nodes = [field.type_node for field in fields]
    node_order = _collect_node_order(top_nodes)
    bits = 1  # type encoding mode bit
    bits += _ue_bit_length(len(node_order))
    if not node_order:
        return bits
    width = _bit_width(len(node_order) - 1)
    for node in node_order:
        bits += _type_id_bit_length(node.type_id)
        bits += len(node.children) * (2 + width)
    bits += len(fields) * width
    return bits


def _type_tree_bits(node: TypeNode) -> int:
    bits = _type_id_bit_length(node.type_id)
    for edge in node.children:
        bits += 2
        bits += _type_tree_bits(edge.node)
    return bits


def _collect_node_order(top_nodes: Sequence[TypeNode]) -> List[TypeNode]:
    visited = set()
    order: List[TypeNode] = []

    def visit(node: TypeNode) -> None:
        if node in visited:
            return
        for edge in node.children:
            visit(edge.node)
        visited.add(node)
        order.append(node)

    for node in top_nodes:
        visit(node)
    return order


def _type_arity(type_id: int) -> int:
    if type_id in (TypeId.LIST, TypeId.SET):
        return 1
    if type_id == TypeId.MAP:
        return 2
    return 0


def _bit_width(max_value: int) -> int:
    if max_value <= 0:
        return 0
    return max_value.bit_length()


def _ue_bit_length(value: int) -> int:
    if value < 0:
        raise ValueError(f"UE code only supports non-negative values, got {value}")
    return ((value + 1).bit_length() << 1) - 1


def _type_id_bit_length(type_id: int) -> int:
    code = _TYPE_ID_CODES.get(type_id)
    if code is not None:
        return len(code)
    return len(_TYPE_ID_ESCAPE_CODE) + _ue_bit_length(type_id)


def _raw_name_mode(value: str) -> int:
    can_alpha5 = True
    can_alpha6 = True
    for char in value:
        if can_alpha5 and char not in _ALPHA_5_TO_INDEX:
            can_alpha5 = False
        if can_alpha6 and char not in _ALPHA_6_TO_INDEX:
            can_alpha6 = False
        if not can_alpha5 and not can_alpha6:
            return _NAME_MODE_UTF8
    if can_alpha5:
        return _NAME_MODE_ALPHA5
    if can_alpha6:
        return _NAME_MODE_ALPHA6
    return _NAME_MODE_UTF8


def _name_mode(value: str) -> int:
    raw_mode = _raw_name_mode(value)
    raw_bits = _raw_name_bits(value)
    token_bits = 2 + _token_name_body_bits(value)
    if token_bits < raw_bits:
        return _NAME_MODE_TOKEN
    return raw_mode


def _common_prefix_len(left: str, right: str) -> int:
    max_len = min(len(left), len(right))
    i = 0
    while i < max_len and left[i] == right[i]:
        i += 1
    return i


def _write_name(writer: _BitWriter, value: str) -> None:
    mode = _name_mode(value)
    writer.write_bits(mode, 2)
    _write_name_body(writer, value, mode)


def _single_name_bits(value: str) -> int:
    mode = _name_mode(value)
    return 2 + _name_body_bits(value, mode)


def _read_name(reader: _BitReader) -> str:
    mode = reader.read_bits(2)
    return _read_name_body(reader, mode)


def _write_field_names(writer: _BitWriter, fields: Sequence[CanonicalField], tag_mask: Sequence[bool]) -> None:
    names = [fields[i].name for i in range(len(fields)) if not tag_mask[i]]
    if not names:
        return
    plain_bits = _field_name_plain_bits(names)
    lcp_bits = _field_name_lcp_bits(names)
    snake_bits = _field_name_snake_bits(names)
    if snake_bits <= plain_bits and snake_bits <= lcp_bits:
        writer.write_bits(0b10, 2)
        _write_field_names_snake(writer, names)
        return
    if lcp_bits <= plain_bits:
        writer.write_bits(0b01, 2)
        _write_field_names_lcp(writer, names)
        return
    writer.write_bits(0b00, 2)
    for name in names:
        _write_name(writer, name)


def _read_field_names(reader: _BitReader, tag_mask: Sequence[bool]) -> List[str]:
    non_tag_count = sum(1 for is_tag in tag_mask if not is_tag)
    if non_tag_count == 0:
        return ["" for _ in tag_mask]
    mode = reader.read_bits(2)
    if mode == 0b00:
        decoded_names = [_read_name(reader) for _ in range(non_tag_count)]
    elif mode == 0b01:
        decoded_names = _read_field_names_lcp(reader, non_tag_count)
    elif mode == 0b10:
        decoded_names = _read_field_names_snake(reader, non_tag_count)
    else:
        raise ValueError(f"Unsupported field-name stream mode {mode:02b}")

    names: List[str] = []
    non_tag_index = 0
    for is_tag in tag_mask:
        if is_tag:
            names.append("")
        else:
            names.append(decoded_names[non_tag_index])
            non_tag_index += 1
    return names


def _write_field_names_lcp(writer: _BitWriter, names: Sequence[str]) -> None:
    previous_name = ""
    for name in names:
        suffix = name[_common_prefix_len(previous_name, name) :]
        mode = _name_mode(suffix)
        writer.write_bits(mode, 2)
        lcp = _common_prefix_len(previous_name, name)
        writer.write_ue(lcp)
        _write_name_body(writer, suffix, mode)
        previous_name = name


def _read_field_names_lcp(reader: _BitReader, count: int) -> List[str]:
    names: List[str] = []
    previous_name = ""
    for _ in range(count):
        mode = reader.read_bits(2)
        lcp = reader.read_ue()
        if lcp > len(previous_name):
            raise ValueError(f"Invalid field name LCP {lcp}, previous length {len(previous_name)}")
        suffix = _read_name_body(reader, mode)
        name = previous_name[:lcp] + suffix
        names.append(name)
        previous_name = name
    return names


def _field_name_plain_bits(names: Sequence[str]) -> int:
    return sum(_single_name_bits(name) for name in names)


def _field_name_lcp_bits(names: Sequence[str]) -> int:
    bits = 0
    previous_name = ""
    for name in names:
        lcp = _common_prefix_len(previous_name, name)
        suffix = name[lcp:]
        mode = _name_mode(suffix)
        bits += 2  # mode
        bits += _ue_bit_length(lcp)
        bits += _name_body_bits(suffix, mode)
        previous_name = name
    return bits


def _field_name_snake_bits(names: Sequence[str]) -> int:
    bits = 0
    for name in names:
        tokens = _split_snake_tokens(name)
        if tokens is None:
            return 1 << 60
        bits += _ue_bit_length(len(tokens))
        for token in tokens:
            bits += _token_base_bits(token)
    return bits


def _write_field_names_snake(writer: _BitWriter, names: Sequence[str]) -> None:
    for name in names:
        tokens = _split_snake_tokens(name)
        if tokens is None:
            raise ValueError(f"Snake field-name mode selected for non-snake name {name}")
        writer.write_ue(len(tokens))
        for token in tokens:
            _write_token_base(writer, token)


def _read_field_names_snake(reader: _BitReader, count: int) -> List[str]:
    names = []
    for _ in range(count):
        token_count = reader.read_ue()
        tokens = [_read_token_base(reader) for _ in range(token_count)]
        names.append("_".join(tokens))
    return names


def _name_payload_bits(value: str, mode: int) -> int:
    if mode == _NAME_MODE_ALPHA5:
        return _ue_bit_length(len(value)) + len(value) * 5
    if mode == _NAME_MODE_ALPHA6:
        return _ue_bit_length(len(value)) + len(value) * 6
    if mode == _NAME_MODE_UTF8:
        payload = value.encode("utf-8")
        return _ue_bit_length(len(payload)) + len(payload) * 8
    raise ValueError(f"Unsupported name mode {mode}")


def _write_name_payload(writer: _BitWriter, value: str, mode: int) -> None:
    if mode == _NAME_MODE_ALPHA5:
        writer.write_ue(len(value))
        for char in value:
            writer.write_bits(_ALPHA_5_TO_INDEX[char], 5)
        return
    if mode == _NAME_MODE_ALPHA6:
        writer.write_ue(len(value))
        for char in value:
            writer.write_bits(_ALPHA_6_TO_INDEX[char], 6)
        return
    if mode == _NAME_MODE_UTF8:
        payload = value.encode("utf-8")
        writer.write_ue(len(payload))
        for byte in payload:
            writer.write_bits(byte, 8)
        return
    raise ValueError(f"Unsupported name mode {mode}")


def _read_name_payload(reader: _BitReader, mode: int) -> str:
    length = reader.read_ue()
    if mode == _NAME_MODE_ALPHA5:
        return "".join(_ALPHA_5_ALPHABET[reader.read_bits(5)] for _ in range(length))
    if mode == _NAME_MODE_ALPHA6:
        return "".join(_ALPHA_6_ALPHABET[reader.read_bits(6)] for _ in range(length))
    if mode == _NAME_MODE_UTF8:
        payload = bytearray(reader.read_bits(8) for _ in range(length))
        return payload.decode("utf-8")
    raise ValueError(f"Unsupported name mode {mode}")


def _raw_name_bits(value: str) -> int:
    mode = _raw_name_mode(value)
    return 2 + _name_payload_bits(value, mode)


def _write_raw_name(writer: _BitWriter, value: str) -> None:
    mode = _raw_name_mode(value)
    writer.write_bits(mode, 2)
    _write_name_payload(writer, value, mode)


def _read_raw_name(reader: _BitReader) -> str:
    mode = reader.read_bits(2)
    return _read_name_payload(reader, mode)


def _name_body_bits(value: str, mode: int) -> int:
    if mode == _NAME_MODE_TOKEN:
        return _token_name_body_bits(value)
    return _name_payload_bits(value, mode)


def _write_name_body(writer: _BitWriter, value: str, mode: int) -> None:
    if mode == _NAME_MODE_TOKEN:
        _write_tokenized_name(writer, value)
        return
    _write_name_payload(writer, value, mode)


def _read_name_body(reader: _BitReader, mode: int) -> str:
    if mode == _NAME_MODE_TOKEN:
        return _read_tokenized_name(reader)
    return _read_name_payload(reader, mode)


def _tokenize_name(value: str) -> List[Tuple[str, str]]:
    pieces: List[Tuple[str, str]] = []
    i = 0
    n = len(value)
    while i < n:
        char = value[i]
        if char in _SEP_TO_ID:
            pieces.append(("sep", char))
            i += 1
            continue

        j = i + 1
        while j < n:
            current = value[j]
            prev = value[j - 1]
            if current in _SEP_TO_ID:
                break
            if prev.islower() and current.isupper():
                break
            if prev.isupper() and current.isupper() and j + 1 < n and value[j + 1].islower():
                break
            j += 1
        pieces.append(("tok", value[i:j]))
        i = j
    return pieces


def _token_case(token: str) -> int:
    if token.isdigit() or token.islower():
        return _TOKEN_CASE_LOWER
    if token.isupper():
        return _TOKEN_CASE_UPPER
    if len(token) >= 2 and token[0].isupper() and token[1:].islower():
        return _TOKEN_CASE_CAPITALIZED
    return _TOKEN_CASE_RAW


def _apply_token_case(base: str, case_mode: int) -> str:
    if case_mode == _TOKEN_CASE_LOWER:
        return base
    if case_mode == _TOKEN_CASE_UPPER:
        return base.upper()
    if case_mode == _TOKEN_CASE_CAPITALIZED:
        if not base:
            return base
        return base[0].upper() + base[1:]
    raise ValueError(f"Invalid token case mode {case_mode}")


def _token_name_body_bits(value: str) -> int:
    generic_bits = 2 + _token_name_generic_bits(value)
    best_bits = generic_bits

    snake_tokens = _split_snake_tokens(value)
    if snake_tokens is not None:
        best_bits = min(best_bits, 2 + _token_name_snake_bits(snake_tokens))

    upper_camel_tokens, lower_camel_tokens = _split_camel_tokens(value)
    if upper_camel_tokens is not None:
        best_bits = min(best_bits, 2 + _token_name_camel_bits(upper_camel_tokens))
    if lower_camel_tokens is not None:
        best_bits = min(best_bits, 2 + _token_name_camel_bits(lower_camel_tokens))
    return best_bits


def _write_tokenized_name(writer: _BitWriter, value: str) -> None:
    best_style = _TOKEN_STYLE_GENERIC
    best_bits = _token_name_generic_bits(value)

    snake_tokens = _split_snake_tokens(value)
    if snake_tokens is not None:
        snake_bits = _token_name_snake_bits(snake_tokens)
        if snake_bits < best_bits:
            best_style = _TOKEN_STYLE_SNAKE
            best_bits = snake_bits

    upper_camel_tokens, lower_camel_tokens = _split_camel_tokens(value)
    if upper_camel_tokens is not None:
        upper_bits = _token_name_camel_bits(upper_camel_tokens)
        if upper_bits < best_bits:
            best_style = _TOKEN_STYLE_UPPER_CAMEL
            best_bits = upper_bits
    if lower_camel_tokens is not None:
        lower_bits = _token_name_camel_bits(lower_camel_tokens)
        if lower_bits < best_bits:
            best_style = _TOKEN_STYLE_LOWER_CAMEL
            best_bits = lower_bits

    writer.write_bits(best_style, 2)
    if best_style == _TOKEN_STYLE_GENERIC:
        _write_tokenized_name_generic(writer, value)
        return
    if best_style == _TOKEN_STYLE_SNAKE:
        _write_tokenized_name_snake(writer, snake_tokens)
        return
    if best_style == _TOKEN_STYLE_UPPER_CAMEL:
        _write_tokenized_name_camel(writer, upper_camel_tokens)
        return
    if best_style == _TOKEN_STYLE_LOWER_CAMEL:
        _write_tokenized_name_camel(writer, lower_camel_tokens)
        return
    raise ValueError(f"Unsupported token style {best_style}")


def _read_tokenized_name(reader: _BitReader) -> str:
    style = reader.read_bits(2)
    if style == _TOKEN_STYLE_GENERIC:
        return _read_tokenized_name_generic(reader)
    if style == _TOKEN_STYLE_SNAKE:
        return "_".join(_read_tokenized_name_camel(reader))
    if style == _TOKEN_STYLE_UPPER_CAMEL:
        tokens = _read_tokenized_name_camel(reader)
        return "".join(_apply_token_case(token, _TOKEN_CASE_CAPITALIZED) for token in tokens)
    if style == _TOKEN_STYLE_LOWER_CAMEL:
        tokens = _read_tokenized_name_camel(reader)
        if not tokens:
            return ""
        head = _apply_token_case(tokens[0], _TOKEN_CASE_LOWER)
        tail = "".join(_apply_token_case(token, _TOKEN_CASE_CAPITALIZED) for token in tokens[1:])
        return head + tail
    raise ValueError(f"Unsupported token style {style}")


def _token_name_generic_bits(value: str) -> int:
    pieces = _tokenize_name(value)
    bits = _ue_bit_length(len(pieces))
    for piece_type, piece_value in pieces:
        bits += 1
        if piece_type == "sep":
            bits += 2
            continue

        case_mode = _token_case(piece_value)
        bits += 2
        if case_mode == _TOKEN_CASE_RAW:
            bits += _raw_name_bits(piece_value)
            continue

        bits += _token_base_bits(piece_value.lower())
    return bits


def _write_tokenized_name_generic(writer: _BitWriter, value: str) -> None:
    pieces = _tokenize_name(value)
    writer.write_ue(len(pieces))
    for piece_type, piece_value in pieces:
        if piece_type == "sep":
            writer.write_bit(1)
            writer.write_bits(_SEP_TO_ID[piece_value], 2)
            continue

        writer.write_bit(0)
        case_mode = _token_case(piece_value)
        writer.write_bits(case_mode, 2)
        if case_mode == _TOKEN_CASE_RAW:
            _write_raw_name(writer, piece_value)
            continue
        _write_token_base(writer, piece_value.lower())


def _read_tokenized_name_generic(reader: _BitReader) -> str:
    piece_count = reader.read_ue()
    pieces: List[str] = []
    for _ in range(piece_count):
        is_sep = reader.read_bit() == 1
        if is_sep:
            sep_id = reader.read_bits(2)
            if sep_id >= len(_SEPARATORS):
                raise ValueError(f"Invalid separator id {sep_id}")
            pieces.append(_SEPARATORS[sep_id])
            continue

        case_mode = reader.read_bits(2)
        if case_mode == _TOKEN_CASE_RAW:
            pieces.append(_read_raw_name(reader))
            continue
        pieces.append(_apply_token_case(_read_token_base(reader), case_mode))
    return "".join(pieces)


def _split_snake_tokens(value: str):
    if "_" not in value:
        return None
    if value.startswith("_") or value.endswith("_"):
        return None
    parts = value.split("_")
    if any(len(part) == 0 for part in parts):
        return None
    for part in parts:
        if any(ch in _SEP_TO_ID for ch in part):
            return None
        if any(ch.isupper() for ch in part):
            return None
    return parts


def _split_camel_tokens(value: str):
    pieces = _tokenize_name(value)
    if any(piece_type == "sep" for piece_type, _ in pieces):
        return None, None
    tokens = [piece_value for _, piece_value in pieces]
    if not tokens:
        return [], []

    cases = [_token_case(token) for token in tokens]
    upper_camel = None
    lower_camel = None
    if cases[0] == _TOKEN_CASE_CAPITALIZED and all(case == _TOKEN_CASE_CAPITALIZED for case in cases[1:]):
        upper_camel = [token.lower() for token in tokens]
    if cases[0] == _TOKEN_CASE_LOWER and all(case == _TOKEN_CASE_CAPITALIZED for case in cases[1:]):
        lower_camel = [token.lower() for token in tokens]
    return upper_camel, lower_camel


def _token_name_snake_bits(tokens) -> int:
    bits = _ue_bit_length(len(tokens))
    for token in tokens:
        bits += _token_base_bits(token)
    return bits


def _write_tokenized_name_snake(writer: _BitWriter, tokens) -> None:
    writer.write_ue(len(tokens))
    for token in tokens:
        _write_token_base(writer, token)


def _token_name_camel_bits(tokens) -> int:
    bits = _ue_bit_length(len(tokens))
    for token in tokens:
        bits += _token_base_bits(token)
    return bits


def _write_tokenized_name_camel(writer: _BitWriter, tokens) -> None:
    writer.write_ue(len(tokens))
    for token in tokens:
        _write_token_base(writer, token)


def _read_tokenized_name_camel(reader: _BitReader) -> List[str]:
    token_count = reader.read_ue()
    return [_read_token_base(reader) for _ in range(token_count)]


def _token_base_bits(base: str) -> int:
    token_index = _TOKEN_TO_INDEX.get(base)
    if token_index is not None:
        return 1 + _token_index_bits(token_index)
    return 1 + _raw_name_bits(base)


def _write_token_base(writer: _BitWriter, base: str) -> None:
    token_index = _TOKEN_TO_INDEX.get(base)
    if token_index is not None:
        writer.write_bit(1)
        _write_token_index(writer, token_index)
        return
    writer.write_bit(0)
    _write_raw_name(writer, base)


def _read_token_base(reader: _BitReader) -> str:
    use_dictionary = reader.read_bit() == 1
    if use_dictionary:
        token_index = _read_token_index(reader)
        if token_index >= len(_COMMON_TOKENS):
            raise ValueError(f"Invalid token index {token_index}")
        return _COMMON_TOKENS[token_index]
    return _read_raw_name(reader)


def _token_index_bits(index: int) -> int:
    _ = index
    return _TOKEN_INDEX_WIDTH


def _write_token_index(writer: _BitWriter, index: int) -> None:
    if _TOKEN_INDEX_WIDTH > 0:
        writer.write_bits(index, _TOKEN_INDEX_WIDTH)


def _read_token_index(reader: _BitReader) -> int:
    if _TOKEN_INDEX_WIDTH == 0:
        return 0
    return reader.read_bits(_TOKEN_INDEX_WIDTH)
