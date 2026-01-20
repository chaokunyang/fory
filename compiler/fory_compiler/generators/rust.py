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

"""Rust code generator."""

from typing import List, Optional, Set

from fory_compiler.generators.base import BaseGenerator, GeneratedFile
from fory_compiler.parser.ast import (
    Message,
    Enum,
    Field,
    FieldType,
    PrimitiveType,
    PrimitiveKind,
    NamedType,
    ListType,
    MapType,
)


class RustGenerator(BaseGenerator):
    """Generates Rust structs with ForyObject derive macro."""

    language_name = "rust"
    file_extension = ".rs"

    # Mapping from FDL primitive types to Rust types
    PRIMITIVE_MAP = {
        PrimitiveKind.BOOL: "bool",
        PrimitiveKind.INT8: "i8",
        PrimitiveKind.INT16: "i16",
        PrimitiveKind.INT32: "i32",
        PrimitiveKind.INT64: "i64",
        PrimitiveKind.FLOAT32: "f32",
        PrimitiveKind.FLOAT64: "f64",
        PrimitiveKind.STRING: "String",
        PrimitiveKind.BYTES: "Vec<u8>",
        PrimitiveKind.DATE: "chrono::NaiveDate",
        PrimitiveKind.TIMESTAMP: "chrono::NaiveDateTime",
    }

    def generate(self) -> List[GeneratedFile]:
        """Generate Rust files for the schema."""
        files = []

        # Generate a single module file with all types
        files.append(self.generate_module())

        return files

    def get_module_name(self) -> str:
        """Get the Rust module name."""
        if self.package:
            return self.package.replace(".", "_")
        return "generated"

    def generate_module(self) -> GeneratedFile:
        """Generate a Rust module with all types."""
        lines = []
        uses: Set[str] = set()

        # Collect uses (including from nested types)
        uses.add("use fory::{Fory, ForyObject}")

        for message in self.schema.messages:
            self.collect_message_uses(message, uses)

        # License header
        lines.append(self.get_license_header("//"))
        lines.append("")

        # Uses
        for use in sorted(uses):
            lines.append(f"{use};")
        lines.append("")

        # Generate enums (top-level)
        for enum in self.schema.enums:
            lines.extend(self.generate_enum(enum))
            lines.append("")

        # Generate modules for nested types
        for message in self.schema.messages:
            module_lines = self.generate_nested_module(message)
            if module_lines:
                lines.extend(module_lines)
                lines.append("")

        # Generate aliases for nested types
        alias_lines: List[str] = []
        for message in self.schema.messages:
            alias_lines.extend(self.collect_nested_aliases(message))
        if alias_lines:
            lines.extend(alias_lines)
            lines.append("")

        # Generate messages (top-level only)
        for message in self.schema.messages:
            lines.extend(self.generate_message(message))
            lines.append("")

        # Generate registration function
        lines.extend(self.generate_registration())
        lines.append("")

        return GeneratedFile(
            path=f"{self.get_module_name()}.rs",
            content="\n".join(lines),
        )

    def collect_message_uses(self, message: Message, uses: Set[str]):
        """Collect uses for a message and its nested types recursively."""
        for field in message.fields:
            self.collect_uses_for_field(field, uses)
        for nested_msg in message.nested_messages:
            self.collect_message_uses(nested_msg, uses)

    def get_flattened_type_name(
        self, name: str, parent_stack: Optional[List[Message]] = None
    ) -> str:
        """Build underscore-flattened type name for nested types."""
        parts = [parent.name for parent in parent_stack or []] + [name]
        if len(parts) == 1:
            return parts[0]
        return "_".join(parts)

    def get_registration_type_name(
        self, name: str, parent_stack: Optional[List[Message]] = None
    ) -> str:
        """Build dot-qualified type name for registration."""
        parts = [parent.name for parent in parent_stack or []] + [name]
        if len(parts) == 1:
            return parts[0]
        return ".".join(parts)

    def get_module_path(self, parent_stack: Optional[List[Message]]) -> str:
        """Build module path from parent message names."""
        if not parent_stack:
            return ""
        return "::".join(self.to_snake_case(parent.name) for parent in parent_stack)

    def indent_lines(self, lines: List[str], level: int) -> List[str]:
        """Indent a list of lines by the given level."""
        prefix = self.indent_str * level
        return [f"{prefix}{line}" if line else line for line in lines]

    def generate_enum(
        self,
        enum: Enum,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a Rust enum."""
        lines = []

        type_name = enum.name
        flat_name = self.get_flattened_type_name(enum.name, parent_stack)
        reg_name = self.get_registration_type_name(enum.name, parent_stack)

        # Derive macros
        lines.append("#[derive(ForyObject, Debug, Clone, PartialEq, Default)]")
        lines.append("#[repr(i32)]")

        # Tag for name-based registration
        if enum.type_id is None and self.package:
            lines.append(f'#[tag("{self.package}.{reg_name}")]')

        lines.append(f"pub enum {type_name} {{")

        # Enum values (strip prefix for scoped enums)
        for i, value in enumerate(enum.values):
            if i == 0:
                lines.append("    #[default]")
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            lines.append(f"    {self.to_pascal_case(stripped_name)} = {value.value},")

        lines.append("}")

        return lines

    def generate_message(
        self,
        message: Message,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a Rust struct."""
        lines = []

        type_name = message.name
        flat_name = self.get_flattened_type_name(message.name, parent_stack)
        reg_name = self.get_registration_type_name(message.name, parent_stack)

        # Derive macros
        lines.append("#[derive(ForyObject, Debug, Clone, PartialEq, Default)]")

        # Tag for name-based registration
        if message.type_id is None and self.package:
            lines.append(f'#[tag("{self.package}.{reg_name}")]')

        lines.append(f"pub struct {type_name} {{")

        # Fields
        lineage = (parent_stack or []) + [message]
        for field in message.fields:
            field_lines = self.generate_field(field, lineage)
            for line in field_lines:
                lines.append(f"    {line}")

        lines.append("}")

        return lines

    def generate_nested_module(
        self,
        message: Message,
        parent_stack: Optional[List[Message]] = None,
        indent: int = 0,
    ) -> List[str]:
        """Generate a nested Rust module containing message nested types."""
        if not message.nested_enums and not message.nested_messages:
            return []

        lines: List[str] = []
        ind = self.indent_str * indent
        module_name = self.to_snake_case(message.name)
        lines.append(f"{ind}pub mod {module_name} {{")
        lines.append(f"{ind}{self.indent_str}use super::*;")
        lines.append("")

        lineage = (parent_stack or []) + [message]

        # Nested enums
        for nested_enum in message.nested_enums:
            enum_lines = self.generate_enum(nested_enum, lineage)
            lines.extend(self.indent_lines(enum_lines, indent + 1))
            lines.append("")

        # Nested messages and their modules
        for nested_msg in message.nested_messages:
            nested_module_lines = self.generate_nested_module(
                nested_msg, lineage, indent + 1
            )
            if nested_module_lines:
                lines.extend(nested_module_lines)
                lines.append("")

            msg_lines = self.generate_message(nested_msg, lineage)
            lines.extend(self.indent_lines(msg_lines, indent + 1))
            lines.append("")

        if lines[-1] == "":
            lines.pop()
        lines.append(f"{ind}}}")
        return lines

    def collect_nested_aliases(
        self,
        message: Message,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Collect pub use aliases for nested types."""
        lines: List[str] = []
        lineage = (parent_stack or []) + [message]
        module_path = self.get_module_path(lineage)

        for nested_enum in message.nested_enums:
            alias = self.get_flattened_type_name(nested_enum.name, lineage)
            lines.append(f"pub use self::{module_path}::{nested_enum.name} as {alias};")

        for nested_msg in message.nested_messages:
            alias = self.get_flattened_type_name(nested_msg.name, lineage)
            lines.append(f"pub use self::{module_path}::{nested_msg.name} as {alias};")
            lines.extend(self.collect_nested_aliases(nested_msg, lineage))

        return lines

    def generate_field(
        self,
        field: Field,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a struct field."""
        lines = []

        # Field attributes
        if field.optional:
            lines.append("#[fory(nullable = true)]")

        pointer_type = self.get_pointer_type(field)
        rust_type = self.generate_type(
            field.field_type,
            nullable=field.optional,
            ref=field.ref,
            element_optional=field.element_optional,
            element_ref=field.element_ref,
            parent_stack=parent_stack,
            pointer_type=pointer_type,
        )
        field_name = self.to_snake_case(field.name)

        lines.append(f"pub {field_name}: {rust_type},")

        return lines

    def generate_type(
        self,
        field_type: FieldType,
        nullable: bool = False,
        ref: bool = False,
        element_optional: bool = False,
        element_ref: bool = False,
        parent_stack: Optional[List[Message]] = None,
        pointer_type: str = "Arc",
    ) -> str:
        """Generate Rust type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable:
                return f"Option<{base_type}>"
            return base_type

        elif isinstance(field_type, NamedType):
            type_name = self.resolve_nested_type_name(field_type.name, parent_stack)
            if ref:
                type_name = f"{pointer_type}<{type_name}>"
            if nullable:
                type_name = f"Option<{type_name}>"
            return type_name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(
                field_type.element_type,
                nullable=element_optional,
                ref=element_ref,
                parent_stack=parent_stack,
                pointer_type=pointer_type,
            )
            list_type = f"Vec<{element_type}>"
            if ref:
                list_type = f"{pointer_type}<{list_type}>"
            if nullable:
                list_type = f"Option<{list_type}>"
            return list_type

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(
                field_type.key_type,
                nullable=False,
                ref=False,
                parent_stack=parent_stack,
                pointer_type=pointer_type,
            )
            value_type = self.generate_type(
                field_type.value_type,
                nullable=False,
                ref=False,
                parent_stack=parent_stack,
                pointer_type=pointer_type,
            )
            map_type = f"HashMap<{key_type}, {value_type}>"
            if ref:
                map_type = f"{pointer_type}<{map_type}>"
            if nullable:
                map_type = f"Option<{map_type}>"
            return map_type

        return "()"

    def resolve_nested_type_name(
        self,
        type_name: str,
        parent_stack: Optional[List[Message]] = None,
    ) -> str:
        """Resolve nested type names to flattened Rust identifiers."""
        if "." in type_name:
            return type_name.replace(".", "_")
        if not parent_stack:
            return type_name

        for i in range(len(parent_stack) - 1, -1, -1):
            message = parent_stack[i]
            if message.get_nested_type(type_name) is not None:
                return self.get_flattened_type_name(
                    type_name, parent_stack[: i + 1]
                )

        return type_name

    def collect_uses(self, field_type: FieldType, uses: Set[str]):
        """Collect required use statements for a field type."""
        if isinstance(field_type, PrimitiveType):
            if field_type.kind in (PrimitiveKind.DATE, PrimitiveKind.TIMESTAMP):
                uses.add("use chrono")

        elif isinstance(field_type, NamedType):
            pass  # No additional uses needed

        elif isinstance(field_type, ListType):
            self.collect_uses(field_type.element_type, uses)

        elif isinstance(field_type, MapType):
            uses.add("use std::collections::HashMap")
            self.collect_uses(field_type.key_type, uses)
            self.collect_uses(field_type.value_type, uses)

    def collect_uses_for_field(self, field: Field, uses: Set[str]):
        """Collect uses for a field, including ref tracking."""
        pointer_type = self.get_pointer_type(field)
        if field.ref or field.element_ref:
            if pointer_type == "Rc":
                uses.add("use std::rc::Rc")
            else:
                uses.add("use std::sync::Arc")
        self.collect_uses(field.field_type, uses)

    def get_pointer_type(self, field: Field) -> str:
        """Determine pointer type for ref tracking based on field options."""
        thread_safe = field.options.get("fory.thread_safe_pointer")
        if thread_safe is False:
            return "Rc"
        return "Arc"

    def generate_registration(self) -> List[str]:
        """Generate the Fory registration function."""
        lines = []

        lines.append(
            "pub fn register_types(fory: &mut Fory) -> Result<(), fory::Error> {"
        )

        # Register enums (top-level)
        for enum in self.schema.enums:
            self.generate_enum_registration(lines, enum, None)

        # Register messages (including nested types)
        for message in self.schema.messages:
            self.generate_message_registration(lines, message, None)

        lines.append("    Ok(())")
        lines.append("}")

        return lines

    def generate_enum_registration(
        self,
        lines: List[str],
        enum: Enum,
        parent_stack: Optional[List[Message]],
    ):
        """Generate registration code for an enum."""
        type_name = self.get_flattened_type_name(enum.name, parent_stack)
        reg_name = self.get_registration_type_name(enum.name, parent_stack)

        if enum.type_id is not None:
            lines.append(f"    fory.register::<{type_name}>({enum.type_id})?;")
        else:
            ns = self.package or "default"
            lines.append(
                f'    fory.register_by_namespace::<{type_name}>("{ns}", "{reg_name}")?;'
            )

    def generate_message_registration(
        self,
        lines: List[str],
        message: Message,
        parent_stack: Optional[List[Message]],
    ):
        """Generate registration code for a message and its nested types."""
        type_name = self.get_flattened_type_name(message.name, parent_stack)
        reg_name = self.get_registration_type_name(message.name, parent_stack)

        # Register nested enums first
        for nested_enum in message.nested_enums:
            self.generate_enum_registration(
                lines, nested_enum, (parent_stack or []) + [message]
            )

        # Register nested messages recursively
        for nested_msg in message.nested_messages:
            self.generate_message_registration(
                lines, nested_msg, (parent_stack or []) + [message]
            )

        # Register this message
        if message.type_id is not None:
            lines.append(f"    fory.register::<{type_name}>({message.type_id})?;")
        else:
            ns = self.package or "default"
            lines.append(
                f'    fory.register_by_namespace::<{type_name}>("{ns}", "{reg_name}")?;'
            )
