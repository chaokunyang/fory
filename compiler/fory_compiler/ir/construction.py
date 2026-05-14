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
from typing import Dict, Iterable, List, Set

from fory_compiler.ir.ast import (
    ArrayType,
    FieldType,
    ListType,
    MapType,
    Message,
    NamedType,
    PrimitiveType,
    Schema,
)


@dataclass(frozen=True)
class MessageConstructionShape:
    """Generated message construction shape."""

    cycle_owned: bool


def analyze_message_construction_shapes(
    schema: Schema,
) -> Dict[str, MessageConstructionShape]:
    """Return construction shapes for all messages in ``schema``.

    A message becomes cycle-owned only when message dependencies form a real
    construction cycle. A top-level ``ref`` marker, nested ``ref`` marker, or
    ``any`` field does not force this shape by itself.
    """

    messages = {message.name: message for message in schema.messages}
    graph = {name: set(_message_dependencies(message, messages)) for name, message in messages.items()}
    cycle_owned = _cycle_nodes(graph)
    return {
        name: MessageConstructionShape(cycle_owned=name in cycle_owned)
        for name in messages
    }


def _message_dependencies(
    message: Message, messages: Dict[str, Message]
) -> Iterable[str]:
    for field in message.fields:
        yield from _field_type_dependencies(field.field_type, messages)


def _field_type_dependencies(
    field_type: FieldType, messages: Dict[str, Message]
) -> Iterable[str]:
    if isinstance(field_type, PrimitiveType):
        return
    if isinstance(field_type, NamedType):
        root_name = field_type.name.split(".", 1)[0]
        if root_name in messages:
            yield root_name
        return
    if isinstance(field_type, ListType):
        yield from _field_type_dependencies(field_type.element_type, messages)
        return
    if isinstance(field_type, ArrayType):
        yield from _field_type_dependencies(field_type.element_type, messages)
        return
    if isinstance(field_type, MapType):
        yield from _field_type_dependencies(field_type.key_type, messages)
        yield from _field_type_dependencies(field_type.value_type, messages)


def _cycle_nodes(graph: Dict[str, Set[str]]) -> Set[str]:
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
                result.update(component)
            elif component[0] in graph[component[0]]:
                result.add(component[0])

    for node in graph:
        if node not in indexes:
            strong_connect(node)
    return result
