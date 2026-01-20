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


@dataclass(frozen=True)
class SourceLocation:
    """Track original source location for error messages."""

    file: str
    line: int
    column: int
    source_format: str


class PrimitiveKind(PyEnum):
    """Primitive type kinds."""

    BOOL = "bool"
    INT8 = "int8"
    INT16 = "int16"
    INT32 = "int32"
    INT64 = "int64"
    VARINT32 = "varint32"
    VARINT64 = "varint64"
    TAGGED_INT64 = "tagged_int64"
    UINT8 = "uint8"
    UINT16 = "uint16"
    UINT32 = "uint32"
    UINT64 = "uint64"
    VAR_UINT32 = "var_uint32"
    VAR_UINT64 = "var_uint64"
    TAGGED_UINT64 = "tagged_uint64"
    FLOAT16 = "float16"
    FLOAT32 = "float32"
    FLOAT64 = "float64"
    STRING = "string"
    BYTES = "bytes"
    DATE = "date"
    TIMESTAMP = "timestamp"
    DURATION = "duration"
    DECIMAL = "decimal"


# Type aliases for primitive type names
PRIMITIVE_TYPES = {
    "bool": PrimitiveKind.BOOL,
    "int8": PrimitiveKind.INT8,
    "int16": PrimitiveKind.INT16,
    "int32": PrimitiveKind.VARINT32,
    "int64": PrimitiveKind.VARINT64,
    "fixed_int32": PrimitiveKind.INT32,
    "fixed_int64": PrimitiveKind.INT64,
    "varint32": PrimitiveKind.VARINT32,
    "varint64": PrimitiveKind.VARINT64,
    "tagged_int64": PrimitiveKind.TAGGED_INT64,
    "uint8": PrimitiveKind.UINT8,
    "uint16": PrimitiveKind.UINT16,
    "uint32": PrimitiveKind.VAR_UINT32,
    "uint64": PrimitiveKind.VAR_UINT64,
    "fixed_uint32": PrimitiveKind.UINT32,
    "fixed_uint64": PrimitiveKind.UINT64,
    "var_uint32": PrimitiveKind.VAR_UINT32,
    "var_uint64": PrimitiveKind.VAR_UINT64,
    "tagged_uint64": PrimitiveKind.TAGGED_UINT64,
    "float16": PrimitiveKind.FLOAT16,
    "float32": PrimitiveKind.FLOAT32,
    "float64": PrimitiveKind.FLOAT64,
    "string": PrimitiveKind.STRING,
    "bytes": PrimitiveKind.BYTES,
    "date": PrimitiveKind.DATE,
    "timestamp": PrimitiveKind.TIMESTAMP,
    "duration": PrimitiveKind.DURATION,
    "decimal": PrimitiveKind.DECIMAL,
}


@dataclass
class PrimitiveType:
    """A primitive type like int32, string, etc."""

    kind: PrimitiveKind
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        return f"PrimitiveType({self.kind.value})"


@dataclass
class NamedType:
    """A reference to a user-defined type (message or enum)."""

    name: str
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        return f"NamedType({self.name})"


@dataclass
class ListType:
    """A list/repeated type."""

    element_type: "FieldType"
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        return f"ListType({self.element_type})"


@dataclass
class MapType:
    """A map type with key and value types."""

    key_type: "FieldType"
    value_type: "FieldType"
    location: Optional[SourceLocation] = None

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
    element_optional: bool = False
    element_ref: bool = False
    options: dict = field(default_factory=dict)
    line: int = 0
    column: int = 0
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        modifiers = []
        if self.optional:
            modifiers.append("optional")
        if self.ref:
            modifiers.append("ref")
        if self.element_optional:
            modifiers.append("element_optional")
        if self.element_ref:
            modifiers.append("element_ref")
        mod_str = " ".join(modifiers) + " " if modifiers else ""
        opts_str = f" [{self.options}]" if self.options else ""
        return (
            f"Field({mod_str}{self.field_type} {self.name} = {self.number}{opts_str})"
        )


@dataclass
class Import:
    """An import statement."""

    path: str
    line: int = 0
    column: int = 0
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        return f'Import("{self.path}")'


@dataclass
class EnumValue:
    """A value in an enum."""

    name: str
    value: int
    line: int = 0
    column: int = 0
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        return f"EnumValue({self.name} = {self.value})"


@dataclass
class Message:
    """A message definition."""

    name: str
    type_id: Optional[int]
    fields: List[Field] = field(default_factory=list)
    nested_messages: List["Message"] = field(default_factory=list)
    nested_enums: List["Enum"] = field(default_factory=list)
    options: dict = field(default_factory=dict)
    line: int = 0
    column: int = 0
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        id_str = f" [id={self.type_id}]" if self.type_id is not None else ""
        nested_str = ""
        if self.nested_messages or self.nested_enums:
            nested_str = (
                f", nested={len(self.nested_messages)}msg+{len(self.nested_enums)}enum"
            )
        opts_str = f", options={len(self.options)}" if self.options else ""
        return (
            f"Message({self.name}{id_str}, fields={self.fields}{nested_str}{opts_str})"
        )

    def get_nested_type(self, name: str) -> Optional[Union["Message", "Enum"]]:
        """Look up a nested type by name."""
        for msg in self.nested_messages:
            if msg.name == name:
                return msg
        for enum in self.nested_enums:
            if enum.name == name:
                return enum
        return None


@dataclass
class Enum:
    """An enum definition."""

    name: str
    type_id: Optional[int]
    values: List[EnumValue] = field(default_factory=list)
    options: dict = field(default_factory=dict)
    line: int = 0
    column: int = 0
    location: Optional[SourceLocation] = None

    def __repr__(self) -> str:
        id_str = f" [id={self.type_id}]" if self.type_id is not None else ""
        opts_str = f", options={len(self.options)}" if self.options else ""
        return f"Enum({self.name}{id_str}, values={self.values}{opts_str})"


@dataclass
class Schema:
    """The root AST node representing a complete FDL file."""

    package: Optional[str]
    imports: List[Import] = field(default_factory=list)
    enums: List[Enum] = field(default_factory=list)
    messages: List[Message] = field(default_factory=list)
    options: dict = field(
        default_factory=dict
    )  # File-level options (java_package, go_package, etc.)
    source_file: Optional[str] = None
    source_format: Optional[str] = None

    def __repr__(self) -> str:
        opts = f", options={len(self.options)}" if self.options else ""
        return f"Schema(package={self.package}, imports={len(self.imports)}, enums={len(self.enums)}, messages={len(self.messages)}{opts})"

    def get_option(self, name: str, default: Optional[str] = None) -> Optional[str]:
        """Get a file-level option value."""
        return self.options.get(name, default)

    def get_type(self, name: str) -> Optional[Union[Message, Enum]]:
        """Look up a type by name, supporting qualified names like Parent.Child."""
        # Handle qualified names (e.g., SearchResponse.Result)
        if "." in name:
            parts = name.split(".")
            # Find the top-level type
            current = self._get_top_level_type(parts[0])
            if current is None:
                return None
            # Navigate through nested types
            for part in parts[1:]:
                if isinstance(current, Message):
                    current = current.get_nested_type(part)
                    if current is None:
                        return None
                else:
                    # Enums don't have nested types
                    return None
            return current
        else:
            return self._get_top_level_type(name)

    def _get_top_level_type(self, name: str) -> Optional[Union[Message, Enum]]:
        """Look up a top-level type by simple name."""
        for enum in self.enums:
            if enum.name == name:
                return enum
        for message in self.messages:
            if message.name == name:
                return message
        return None

    def get_all_types(self) -> List[Union[Message, Enum]]:
        """Get all types including nested types (flattened)."""
        result: List[Union[Message, Enum]] = []
        result.extend(self.enums)
        for message in self.messages:
            self._collect_types(message, result)
        return result

    def _collect_types(self, message: Message, result: List[Union[Message, Enum]]):
        """Recursively collect all types from a message and its nested types."""
        result.append(message)
        for nested_enum in message.nested_enums:
            result.append(nested_enum)
        for nested_msg in message.nested_messages:
            self._collect_types(nested_msg, result)

    def validate(self) -> List[str]:
        """Validate the schema and return a list of errors."""
        from fory_compiler.ir.validator import validate_schema

        return validate_schema(self)
