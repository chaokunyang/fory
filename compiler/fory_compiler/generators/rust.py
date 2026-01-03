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

from typing import List, Set

from fory_compiler.generators.base import BaseGenerator, GeneratedFile
from fory_compiler.parser.ast import (
    Schema,
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

        # Collect uses
        uses.add("use fory::{Fory, ForyObject}")

        for message in self.schema.messages:
            for field in message.fields:
                self.collect_uses_for_field(field, uses)

        # License header
        lines.append(self.get_license_header("//"))
        lines.append("")

        # Uses
        for use in sorted(uses):
            lines.append(f"{use};")
        lines.append("")

        # Generate enums
        for enum in self.schema.enums:
            lines.extend(self.generate_enum(enum))
            lines.append("")

        # Generate messages
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

    def generate_enum(self, enum: Enum) -> List[str]:
        """Generate a Rust enum."""
        lines = []

        # Derive macros
        lines.append("#[derive(ForyObject, Debug, Clone, PartialEq, Default)]")
        lines.append("#[repr(i32)]")

        # Tag for name-based registration
        if enum.type_id is None and self.package:
            lines.append(f'#[tag("{self.package}.{enum.name}")]')

        lines.append(f"pub enum {enum.name} {{")

        # Enum values (strip prefix for scoped enums)
        for i, value in enumerate(enum.values):
            if i == 0:
                lines.append("    #[default]")
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            lines.append(f"    {self.to_pascal_case(stripped_name)} = {value.value},")

        lines.append("}")

        return lines

    def generate_message(self, message: Message) -> List[str]:
        """Generate a Rust struct."""
        lines = []

        # Derive macros
        lines.append("#[derive(ForyObject, Debug, Clone, PartialEq, Default)]")

        # Tag for name-based registration
        if message.type_id is None and self.package:
            lines.append(f'#[tag("{self.package}.{message.name}")]')

        lines.append(f"pub struct {message.name} {{")

        # Fields
        for field in message.fields:
            field_lines = self.generate_field(field)
            for line in field_lines:
                lines.append(f"    {line}")

        lines.append("}")

        return lines

    def generate_field(self, field: Field) -> List[str]:
        """Generate a struct field."""
        lines = []

        # Field attributes
        if field.optional:
            lines.append("#[fory(nullable = true)]")

        rust_type = self.generate_type(field.field_type, field.optional, field.ref)
        field_name = self.to_snake_case(field.name)

        lines.append(f"pub {field_name}: {rust_type},")

        return lines

    def generate_type(self, field_type: FieldType, nullable: bool = False, ref: bool = False) -> str:
        """Generate Rust type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable:
                return f"Option<{base_type}>"
            return base_type

        elif isinstance(field_type, NamedType):
            if ref:
                return f"Rc<{field_type.name}>"
            if nullable:
                return f"Option<{field_type.name}>"
            return field_type.name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(field_type.element_type, False, False)
            return f"Vec<{element_type}>"

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(field_type.key_type, False, False)
            value_type = self.generate_type(field_type.value_type, False, False)
            return f"HashMap<{key_type}, {value_type}>"

        return "()"

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
        if field.ref:
            uses.add("use std::rc::Rc")
        self.collect_uses(field.field_type, uses)

    def generate_registration(self) -> List[str]:
        """Generate the Fory registration function."""
        lines = []

        lines.append("pub fn register_types(fory: &mut Fory) -> Result<(), fory::Error> {")

        # Register enums
        for enum in self.schema.enums:
            if enum.type_id is not None:
                lines.append(f"    fory.register::<{enum.name}>({enum.type_id})?;")
            else:
                ns = self.package or "default"
                lines.append(f'    fory.register_by_namespace::<{enum.name}>("{ns}", "{enum.name}")?;')

        # Register messages
        for message in self.schema.messages:
            if message.type_id is not None:
                lines.append(f"    fory.register::<{message.name}>({message.type_id})?;")
            else:
                ns = self.package or "default"
                lines.append(f'    fory.register_by_namespace::<{message.name}>("{ns}", "{message.name}")?;')

        lines.append("    Ok(())")
        lines.append("}")

        return lines
