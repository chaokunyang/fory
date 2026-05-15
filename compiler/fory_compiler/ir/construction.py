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

"""Construction shape analysis shared by JVM-family generators."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Set, Tuple

from fory_compiler.ir.ast import (
    ArrayType,
    Enum,
    Field,
    FieldType,
    ListType,
    MapType,
    Message,
    NamedType,
    PrimitiveType,
    Schema,
    Union,
)


@dataclass(frozen=True)
class MessageConstructionShape:
    """Generated message construction shape."""

    cycle_owned: bool


@dataclass(frozen=True)
class _Dependency:
    """Message/union dependency used by construction-shape analysis."""

    name: str
    constructor_owned: bool


def analyze_message_construction_shapes(
    schema: Schema,
) -> Dict[str, MessageConstructionShape]:
    """Return construction shapes for all messages in ``schema``.

    A message becomes cycle-owned only when message dependencies form a real
    construction cycle. A top-level ``ref`` marker, nested ``ref`` marker, or
    ``any`` field does not force this shape by itself.
    """

    message_entries, union_entries, types = _collect_types(
        schema.messages, schema.unions, schema.enums
    )
    messages = {name: message for name, message, _ in message_entries}
    graph: Dict[str, Set[str]] = {}
    constructor_graph: Dict[str, Set[str]] = {}
    for name, message, parent_paths in message_entries:
        dependencies = list(
            _field_dependencies(
                message.fields, types, (*parent_paths, message.name), False
            )
        )
        graph[name] = {dependency.name for dependency in dependencies}
        constructor_graph[name] = {
            dependency.name
            for dependency in dependencies
            if dependency.constructor_owned
        }
    for name, union, parent_paths in union_entries:
        dependencies = list(
            _field_dependencies(union.fields, types, (*parent_paths, union.name), False)
        )
        graph[name] = {dependency.name for dependency in dependencies}
        constructor_graph[name] = {
            dependency.name
            for dependency in dependencies
            if dependency.constructor_owned
        }
    cycle_owned = _cycle_nodes(graph, constructor_graph)
    return {
        name: MessageConstructionShape(cycle_owned=name in cycle_owned)
        for name in messages
    }


def _collect_types(
    messages: Iterable[Message],
    unions: Iterable[Union],
    enums: Iterable[Enum],
    parent_paths: Optional[Tuple[str, ...]] = None,
) -> Tuple[
    List[Tuple[str, Message, Tuple[str, ...]]],
    List[Tuple[str, Union, Tuple[str, ...]]],
    Dict[str, object],
]:
    parent_paths = parent_paths or ()
    message_entries: List[Tuple[str, Message, Tuple[str, ...]]] = []
    union_entries: List[Tuple[str, Union, Tuple[str, ...]]] = []
    types: Dict[str, object] = {}
    for union in unions:
        name = ".".join((*parent_paths, union.name))
        union_entries.append((name, union, parent_paths))
        types[name] = union
    for enum in enums:
        name = ".".join((*parent_paths, enum.name))
        types[name] = enum
    for message in messages:
        name = ".".join((*parent_paths, message.name))
        message_entries.append((name, message, parent_paths))
        types[name] = message
        nested_messages, nested_unions, nested_types = _collect_types(
            message.nested_messages,
            message.nested_unions,
            message.nested_enums,
            (*parent_paths, message.name),
        )
        message_entries.extend(nested_messages)
        union_entries.extend(nested_unions)
        types.update(nested_types)
    return message_entries, union_entries, types


def _field_dependencies(
    fields: Iterable[Field],
    types: Dict[str, object],
    current_path: Tuple[str, ...],
    nested: bool,
) -> Iterable[_Dependency]:
    for field in fields:
        yield from _field_type_dependencies(
            field.field_type, types, current_path, nested
        )


def _field_type_dependencies(
    field_type: FieldType,
    types: Dict[str, object],
    current_path: Tuple[str, ...],
    nested: bool,
) -> Iterable[_Dependency]:
    if isinstance(field_type, PrimitiveType):
        return
    if isinstance(field_type, NamedType):
        resolved = _resolve_type_name(field_type.name, types, current_path)
        if resolved is not None and isinstance(resolved[1], (Message, Union)):
            yield _Dependency(resolved[0], constructor_owned=not nested)
        return
    if isinstance(field_type, ListType):
        yield from _field_type_dependencies(
            field_type.element_type, types, current_path, True
        )
        return
    if isinstance(field_type, ArrayType):
        yield from _field_type_dependencies(
            field_type.element_type, types, current_path, True
        )
        return
    if isinstance(field_type, MapType):
        yield from _field_type_dependencies(
            field_type.key_type, types, current_path, True
        )
        yield from _field_type_dependencies(
            field_type.value_type, types, current_path, True
        )
        return


def _resolve_type_name(
    name: str, types: Dict[str, object], parent_paths: Tuple[str, ...]
) -> Optional[Tuple[str, object]]:
    if "." in name:
        resolved = types.get(name)
        return (name, resolved) if resolved is not None else None
    for index in range(len(parent_paths), 0, -1):
        candidate = ".".join((*parent_paths[:index], name))
        resolved = types.get(candidate)
        if resolved is not None:
            return candidate, resolved
    resolved = types.get(name)
    if resolved is not None:
        return name, resolved
    return None


def _cycle_nodes(
    graph: Dict[str, Set[str]], constructor_graph: Dict[str, Set[str]]
) -> Set[str]:
    index = 0
    stack: List[str] = []
    on_stack: Set[str] = set()
    indexes: Dict[str, int] = {}
    lowlinks: Dict[str, int] = {}
    result: Set[str] = set()

    def strong_connect(node: str) -> None:
        nonlocal index
        indexes[node] = index
        lowlinks[node] = index
        index += 1
        stack.append(node)
        on_stack.add(node)

        for target in graph[node]:
            if target not in graph:
                continue
            if target not in indexes:
                strong_connect(target)
                lowlinks[node] = min(lowlinks[node], lowlinks[target])
            elif target in on_stack:
                lowlinks[node] = min(lowlinks[node], indexes[target])

        if lowlinks[node] == indexes[node]:
            component = []
            while True:
                current = stack.pop()
                on_stack.remove(current)
                component.append(current)
                if current == node:
                    break
            if len(component) > 1:
                component_set = set(component)
                if any(
                    target in component_set
                    for current in component
                    for target in constructor_graph[current]
                ):
                    result.update(component)
            elif component[0] in constructor_graph[component[0]]:
                result.add(component[0])

    for node in graph:
        if node not in indexes:
            strong_connect(node)
    return result
