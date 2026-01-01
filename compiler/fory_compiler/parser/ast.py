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

"""AST node definitions for FDL."""

from dataclasses import dataclass, field
from typing import List, Optional, Union
from enum import Enum as PyEnum


class PrimitiveKind(PyEnum):
    """Primitive type kinds."""

    BOOL = "bool"
    INT8 = "int8"
    INT16 = "int16"
    INT32 = "int32"
    INT64 = "int64"
    FLOAT32 = "float32"
    FLOAT64 = "float64"
    STRING = "string"
    BYTES = "bytes"
    DATE = "date"
    TIMESTAMP = "timestamp"


# Type aliases for primitive type names
PRIMITIVE_TYPES = {
    "bool": PrimitiveKind.BOOL,
    "int8": PrimitiveKind.INT8,
    "int16": PrimitiveKind.INT16,
    "int32": PrimitiveKind.INT32,
    "int64": PrimitiveKind.INT64,
    "float32": PrimitiveKind.FLOAT32,
    "float64": PrimitiveKind.FLOAT64,
    "string": PrimitiveKind.STRING,
    "bytes": PrimitiveKind.BYTES,
    "date": PrimitiveKind.DATE,
    "timestamp": PrimitiveKind.TIMESTAMP,
}


@dataclass
class PrimitiveType:
    """A primitive type like int32, string, etc."""

    kind: PrimitiveKind

    def __repr__(self) -> str:
        return f"PrimitiveType({self.kind.value})"


@dataclass
class NamedType:
    """A reference to a user-defined type (message or enum)."""

    name: str

    def __repr__(self) -> str:
        return f"NamedType({self.name})"


@dataclass
class ListType:
    """A list/repeated type."""

    element_type: "FieldType"

    def __repr__(self) -> str:
        return f"ListType({self.element_type})"


@dataclass
class MapType:
    """A map type with key and value types."""

    key_type: "FieldType"
    value_type: "FieldType"

    def __repr__(self) -> str:
        return f"MapType({self.key_type}, {self.value_type})"


# Union of all field types
FieldType = Union[PrimitiveType, NamedType, ListType, MapType]


@dataclass
class Field:
    """A field in a message."""

    name: str
    field_type: FieldType
    number: int
    optional: bool = False
    ref: bool = False
    line: int = 0
    column: int = 0

    def __repr__(self) -> str:
        modifiers = []
        if self.optional:
            modifiers.append("optional")
        if self.ref:
            modifiers.append("ref")
        mod_str = " ".join(modifiers) + " " if modifiers else ""
        return f"Field({mod_str}{self.field_type} {self.name} = {self.number})"


@dataclass
class EnumValue:
    """A value in an enum."""

    name: str
    value: int
    line: int = 0
    column: int = 0

    def __repr__(self) -> str:
        return f"EnumValue({self.name} = {self.value})"


@dataclass
class Message:
    """A message definition."""

    name: str
    type_id: Optional[int]
    fields: List[Field] = field(default_factory=list)
    line: int = 0
    column: int = 0

    def __repr__(self) -> str:
        id_str = f" @{self.type_id}" if self.type_id is not None else ""
        return f"Message({self.name}{id_str}, fields={self.fields})"


@dataclass
class Enum:
    """An enum definition."""

    name: str
    type_id: Optional[int]
    values: List[EnumValue] = field(default_factory=list)
    line: int = 0
    column: int = 0

    def __repr__(self) -> str:
        id_str = f" @{self.type_id}" if self.type_id is not None else ""
        return f"Enum({self.name}{id_str}, values={self.values})"


@dataclass
class Schema:
    """The root AST node representing a complete FDL file."""

    package: Optional[str]
    enums: List[Enum] = field(default_factory=list)
    messages: List[Message] = field(default_factory=list)

    def __repr__(self) -> str:
        return f"Schema(package={self.package}, enums={len(self.enums)}, messages={len(self.messages)})"

    def get_type(self, name: str) -> Optional[Union[Message, Enum]]:
        """Look up a type by name."""
        for enum in self.enums:
            if enum.name == name:
                return enum
        for message in self.messages:
            if message.name == name:
                return message
        return None

    def validate(self) -> List[str]:
        """Validate the schema and return a list of errors."""
        errors = []

        # Check for duplicate type names
        names = set()
        for enum in self.enums:
            if enum.name in names:
                errors.append(f"Duplicate type name: {enum.name}")
            names.add(enum.name)
        for message in self.messages:
            if message.name in names:
                errors.append(f"Duplicate type name: {message.name}")
            names.add(message.name)

        # Check for duplicate type IDs
        type_ids = {}
        for enum in self.enums:
            if enum.type_id is not None:
                if enum.type_id in type_ids:
                    errors.append(
                        f"Duplicate type ID @{enum.type_id}: "
                        f"{enum.name} and {type_ids[enum.type_id]}"
                    )
                type_ids[enum.type_id] = enum.name
        for message in self.messages:
            if message.type_id is not None:
                if message.type_id in type_ids:
                    errors.append(
                        f"Duplicate type ID @{message.type_id}: "
                        f"{message.name} and {type_ids[message.type_id]}"
                    )
                type_ids[message.type_id] = message.name

        # Check for duplicate field numbers within messages
        for message in self.messages:
            field_numbers = {}
            field_names = set()
            for f in message.fields:
                if f.number in field_numbers:
                    errors.append(
                        f"Duplicate field number {f.number} in {message.name}: "
                        f"{f.name} and {field_numbers[f.number]}"
                    )
                field_numbers[f.number] = f.name
                if f.name in field_names:
                    errors.append(
                        f"Duplicate field name in {message.name}: {f.name}"
                    )
                field_names.add(f.name)

        # Check for duplicate enum values
        for enum in self.enums:
            value_numbers = {}
            value_names = set()
            for v in enum.values:
                if v.value in value_numbers:
                    errors.append(
                        f"Duplicate enum value {v.value} in {enum.name}: "
                        f"{v.name} and {value_numbers[v.value]}"
                    )
                value_numbers[v.value] = v.name
                if v.name in value_names:
                    errors.append(
                        f"Duplicate enum value name in {enum.name}: {v.name}"
                    )
                value_names.add(v.name)

        # Check that referenced types exist
        def check_type_ref(field_type: FieldType, context: str):
            if isinstance(field_type, NamedType):
                if self.get_type(field_type.name) is None:
                    errors.append(f"Unknown type '{field_type.name}' in {context}")
            elif isinstance(field_type, ListType):
                check_type_ref(field_type.element_type, context)
            elif isinstance(field_type, MapType):
                check_type_ref(field_type.key_type, context)
                check_type_ref(field_type.value_type, context)

        for message in self.messages:
            for f in message.fields:
                check_type_ref(f.field_type, f"{message.name}.{f.name}")

        return errors
