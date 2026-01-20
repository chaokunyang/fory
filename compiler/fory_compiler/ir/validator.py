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

"""Schema validation for Fory IDL."""

from dataclasses import dataclass
from typing import List, Optional

from fory_compiler.ir.ast import (
    Schema,
    Message,
    Enum,
    Field,
    FieldType,
    NamedType,
    ListType,
    MapType,
    SourceLocation,
)


@dataclass
class ValidationIssue:
    """Validation issue with optional source location."""

    message: str
    location: Optional[SourceLocation]
    severity: str

    def __str__(self) -> str:
        if not self.location:
            return self.message
        return f"{self.location.file}:{self.location.line}:{self.location.column}: {self.message}"


class SchemaValidator:
    """Validates a Fory IR schema."""

    def __init__(self, schema: Schema):
        self.schema = schema
        self.errors: List[ValidationIssue] = []
        self.warnings: List[ValidationIssue] = []

    def validate(self) -> bool:
        self._check_duplicate_type_names()
        self._check_duplicate_type_ids()
        self._check_messages()
        self._check_type_references()
        return not self.errors

    def _error(self, message: str, location: Optional[SourceLocation]) -> None:
        self.errors.append(ValidationIssue(message, location, "error"))

    def _check_duplicate_type_names(self) -> None:
        names = {}
        for enum in self.schema.enums:
            if enum.name in names:
                self._error(
                    f"Duplicate type name: {enum.name}", enum.location or names[enum.name]
                )
            names.setdefault(enum.name, enum.location)
        for message in self.schema.messages:
            if message.name in names:
                self._error(
                    f"Duplicate type name: {message.name}",
                    message.location or names[message.name],
                )
            names.setdefault(message.name, message.location)

    def _check_duplicate_type_ids(self) -> None:
        type_ids = {}
        for t in self.schema.get_all_types():
            if t.type_id is None:
                continue
            if t.type_id in type_ids:
                self._error(
                    f"Duplicate type ID @{t.type_id}: {t.name} and {type_ids[t.type_id].name}",
                    t.location,
                )
            type_ids.setdefault(t.type_id, t)

    def _check_messages(self) -> None:
        def validate_message(message: Message, parent_path: str = ""):
            full_name = f"{parent_path}.{message.name}" if parent_path else message.name

            nested_names = {}
            for nested_enum in message.nested_enums:
                if nested_enum.name in nested_names:
                    self._error(
                        f"Duplicate nested type name in {full_name}: {nested_enum.name}",
                        nested_enum.location,
                    )
                nested_names.setdefault(nested_enum.name, nested_enum.location)
            for nested_msg in message.nested_messages:
                if nested_msg.name in nested_names:
                    self._error(
                        f"Duplicate nested type name in {full_name}: {nested_msg.name}",
                        nested_msg.location,
                    )
                nested_names.setdefault(nested_msg.name, nested_msg.location)

            field_numbers = {}
            field_names = {}
            for f in message.fields:
                if f.number in field_numbers:
                    self._error(
                        f"Duplicate field number {f.number} in {full_name}: {f.name} and {field_numbers[f.number].name}",
                        f.location,
                    )
                field_numbers.setdefault(f.number, f)
                if f.name in field_names:
                    self._error(
                        f"Duplicate field name in {full_name}: {f.name}", f.location
                    )
                field_names.setdefault(f.name, f)

            for nested_enum in message.nested_enums:
                validate_enum(nested_enum, full_name)

            for nested_msg in message.nested_messages:
                validate_message(nested_msg, full_name)

        def validate_enum(enum: Enum, parent_path: str = ""):
            full_name = f"{parent_path}.{enum.name}" if parent_path else enum.name
            value_numbers = {}
            value_names = {}
            for v in enum.values:
                if v.value in value_numbers:
                    self._error(
                        f"Duplicate enum value {v.value} in {full_name}: {v.name} and {value_numbers[v.value].name}",
                        v.location,
                    )
                value_numbers.setdefault(v.value, v)
                if v.name in value_names:
                    self._error(
                        f"Duplicate enum value name in {full_name}: {v.name}",
                        v.location,
                    )
                value_names.setdefault(v.name, v)

        for enum in self.schema.enums:
            validate_enum(enum)

        for message in self.schema.messages:
            validate_message(message)

    def _check_type_references(self) -> None:
        def check_type_ref(
            field_type: FieldType,
            field: Field,
            enclosing_messages: Optional[List[Message]] = None,
        ):
            if isinstance(field_type, NamedType):
                type_name = field_type.name
                found = False

                if enclosing_messages and "." not in type_name:
                    for message in reversed(enclosing_messages):
                        if message.get_nested_type(type_name) is not None:
                            found = True
                            break

                if not found and self.schema.get_type(type_name) is not None:
                    found = True

                if not found:
                    self._error(f"Unknown type '{type_name}'", field.location)
            elif isinstance(field_type, ListType):
                check_type_ref(field_type.element_type, field, enclosing_messages)
            elif isinstance(field_type, MapType):
                check_type_ref(field_type.key_type, field, enclosing_messages)
                check_type_ref(field_type.value_type, field, enclosing_messages)

        def check_message_refs(
            message: Message,
            enclosing_messages: Optional[List[Message]] = None,
        ):
            lineage = (enclosing_messages or []) + [message]
            for f in message.fields:
                check_type_ref(f.field_type, f, lineage)
            for nested_msg in message.nested_messages:
                check_message_refs(nested_msg, lineage)

        for message in self.schema.messages:
            check_message_refs(message)


def validate_schema(schema: Schema) -> List[str]:
    """Validate a schema and return a list of error messages."""
    validator = SchemaValidator(schema)
    validator.validate()
    return [str(err) for err in validator.errors]
