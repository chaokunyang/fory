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

from typing import List, Optional

from fory_compiler.ir.ast import (
    Schema,
    Message,
    Enum,
    FieldType,
    NamedType,
    ListType,
    MapType,
)


def validate_schema(schema: Schema) -> List[str]:
    """Validate a schema and return a list of errors."""
    errors: List[str] = []

    # Check for duplicate type names at top level
    names = set()
    for enum in schema.enums:
        if enum.name in names:
            errors.append(f"Duplicate type name: {enum.name}")
        names.add(enum.name)
    for message in schema.messages:
        if message.name in names:
            errors.append(f"Duplicate type name: {message.name}")
        names.add(message.name)

    # Check for duplicate type IDs (including nested types)
    type_ids = {}
    all_types = schema.get_all_types()
    for t in all_types:
        if t.type_id is not None:
            if t.type_id in type_ids:
                errors.append(
                    f"Duplicate type ID @{t.type_id}: "
                    f"{t.name} and {type_ids[t.type_id]}"
                )
            type_ids[t.type_id] = t.name

    # Validate messages recursively (including nested)
    def validate_message(message: Message, parent_path: str = ""):
        full_name = f"{parent_path}.{message.name}" if parent_path else message.name

        # Check for duplicate nested type names
        nested_names = set()
        for nested_enum in message.nested_enums:
            if nested_enum.name in nested_names:
                errors.append(
                    f"Duplicate nested type name in {full_name}: {nested_enum.name}"
                )
            nested_names.add(nested_enum.name)
        for nested_msg in message.nested_messages:
            if nested_msg.name in nested_names:
                errors.append(
                    f"Duplicate nested type name in {full_name}: {nested_msg.name}"
                )
            nested_names.add(nested_msg.name)

        # Check for duplicate field numbers and names
        field_numbers = {}
        field_names = set()
        for f in message.fields:
            if f.number in field_numbers:
                errors.append(
                    f"Duplicate field number {f.number} in {full_name}: "
                    f"{f.name} and {field_numbers[f.number]}"
                )
            field_numbers[f.number] = f.name
            if f.name in field_names:
                errors.append(f"Duplicate field name in {full_name}: {f.name}")
            field_names.add(f.name)

        # Validate nested enums
        for nested_enum in message.nested_enums:
            validate_enum(nested_enum, full_name)

        # Recursively validate nested messages
        for nested_msg in message.nested_messages:
            validate_message(nested_msg, full_name)

    def validate_enum(enum: Enum, parent_path: str = ""):
        full_name = f"{parent_path}.{enum.name}" if parent_path else enum.name
        value_numbers = {}
        value_names = set()
        for v in enum.values:
            if v.value in value_numbers:
                errors.append(
                    f"Duplicate enum value {v.value} in {full_name}: "
                    f"{v.name} and {value_numbers[v.value]}"
                )
            value_numbers[v.value] = v.name
            if v.name in value_names:
                errors.append(f"Duplicate enum value name in {full_name}: {v.name}")
            value_names.add(v.name)

    # Validate all top-level enums
    for enum in schema.enums:
        validate_enum(enum)

    # Validate all top-level messages (and their nested types)
    for message in schema.messages:
        validate_message(message)

    # Check that referenced types exist (supports qualified names and nested type lookup)
    def check_type_ref(
        field_type: FieldType,
        context: str,
        enclosing_messages: Optional[List[Message]] = None,
    ):
        if isinstance(field_type, NamedType):
            type_name = field_type.name
            found = False

            # First, try to find as a nested type in any enclosing message
            if enclosing_messages and "." not in type_name:
                for message in reversed(enclosing_messages):
                    if message.get_nested_type(type_name) is not None:
                        found = True
                        break

            # Then, try to find as a top-level or qualified type
            if not found and schema.get_type(type_name) is not None:
                found = True

            if not found:
                errors.append(f"Unknown type '{type_name}' in {context}")
        elif isinstance(field_type, ListType):
            check_type_ref(field_type.element_type, context, enclosing_messages)
        elif isinstance(field_type, MapType):
            check_type_ref(field_type.key_type, context, enclosing_messages)
            check_type_ref(field_type.value_type, context, enclosing_messages)

    def check_message_refs(
        message: Message,
        parent_path: str = "",
        enclosing_messages: Optional[List[Message]] = None,
    ):
        full_name = f"{parent_path}.{message.name}" if parent_path else message.name
        lineage = (enclosing_messages or []) + [message]
        for f in message.fields:
            check_type_ref(f.field_type, f"{full_name}.{f.name}", lineage)
        for nested_msg in message.nested_messages:
            check_message_refs(nested_msg, full_name, lineage)

    for message in schema.messages:
        check_message_refs(message)

    return errors
