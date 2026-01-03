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

"""Go code generator."""

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


class GoGenerator(BaseGenerator):
    """Generates Go structs with fory tags."""

    language_name = "go"
    file_extension = ".go"
    indent_str = "\t"  # Go uses tabs

    # Mapping from FDL primitive types to Go types
    PRIMITIVE_MAP = {
        PrimitiveKind.BOOL: "bool",
        PrimitiveKind.INT8: "int8",
        PrimitiveKind.INT16: "int16",
        PrimitiveKind.INT32: "int32",
        PrimitiveKind.INT64: "int64",
        PrimitiveKind.FLOAT32: "float32",
        PrimitiveKind.FLOAT64: "float64",
        PrimitiveKind.STRING: "string",
        PrimitiveKind.BYTES: "[]byte",
        PrimitiveKind.DATE: "time.Time",
        PrimitiveKind.TIMESTAMP: "time.Time",
    }

    def generate(self) -> List[GeneratedFile]:
        """Generate Go files for the schema."""
        files = []

        # Generate a single Go file with all types
        files.append(self.generate_file())

        return files

    def get_package_name(self) -> str:
        """Get the Go package name."""
        if self.package:
            # Use last part of package as Go package name
            parts = self.package.split(".")
            return parts[-1]
        return "generated"

    def get_file_name(self) -> str:
        """Get the Go file name."""
        if self.package:
            return self.package.replace(".", "_")
        return "generated"

    def generate_file(self) -> GeneratedFile:
        """Generate a Go file with all types."""
        lines = []
        imports: Set[str] = set()

        # Collect imports
        imports.add('fory "github.com/apache/fory/go/fory"')

        for message in self.schema.messages:
            for field in message.fields:
                self.collect_imports(field.field_type, imports)

        # License header
        lines.append(self.get_license_header("//"))
        lines.append("")

        # Package declaration
        lines.append(f"package {self.get_package_name()}")
        lines.append("")

        # Imports
        if imports:
            lines.append("import (")
            for imp in sorted(imports):
                lines.append(f'\t{imp}')
            lines.append(")")
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
            path=f"{self.get_file_name()}.go",
            content="\n".join(lines),
        )

    def generate_enum(self, enum: Enum) -> List[str]:
        """Generate a Go enum (using type alias and constants)."""
        lines = []

        # Type definition
        lines.append(f"type {enum.name} int32")
        lines.append("")

        # Constants (strip prefix first, then add enum name back for Go's unscoped style)
        lines.append("const (")
        for value in enum.values:
            # Strip the proto-style prefix (e.g., DEVICE_TIER_UNKNOWN -> UNKNOWN)
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            # Add enum name prefix for Go (e.g., DeviceTierUnknown)
            const_name = f"{enum.name}{self.to_pascal_case(stripped_name)}"
            lines.append(f"\t{const_name} {enum.name} = {value.value}")
        lines.append(")")

        return lines

    def generate_message(self, message: Message) -> List[str]:
        """Generate a Go struct."""
        lines = []

        lines.append(f"type {message.name} struct {{")

        # Fields
        for field in message.fields:
            field_lines = self.generate_field(field)
            for line in field_lines:
                lines.append(f"\t{line}")

        lines.append("}")

        return lines

    def generate_field(self, field: Field) -> List[str]:
        """Generate a struct field."""
        lines = []

        go_type = self.generate_type(field.field_type, field.optional, field.ref)
        field_name = self.to_pascal_case(field.name)  # Go uses PascalCase for exported fields

        # Build fory tag
        tags = []
        if field.optional:
            tags.append("nullable")
        if field.ref:
            tags.append("trackRef")

        if tags:
            tag_str = ",".join(tags)
            lines.append(f'{field_name} {go_type} `fory:"{tag_str}"`')
        else:
            lines.append(f"{field_name} {go_type}")

        return lines

    def generate_type(self, field_type: FieldType, nullable: bool = False, ref: bool = False) -> str:
        """Generate Go type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable and base_type not in ("[]byte",):
                return f"*{base_type}"
            return base_type

        elif isinstance(field_type, NamedType):
            if nullable or ref:
                return f"*{field_type.name}"
            return field_type.name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(field_type.element_type, False, False)
            return f"[]{element_type}"

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(field_type.key_type, False, False)
            value_type = self.generate_type(field_type.value_type, False, False)
            return f"map[{key_type}]{value_type}"

        return "interface{}"

    def collect_imports(self, field_type: FieldType, imports: Set[str]):
        """Collect required imports for a field type."""
        if isinstance(field_type, PrimitiveType):
            if field_type.kind in (PrimitiveKind.DATE, PrimitiveKind.TIMESTAMP):
                imports.add('"time"')

        elif isinstance(field_type, ListType):
            self.collect_imports(field_type.element_type, imports)

        elif isinstance(field_type, MapType):
            self.collect_imports(field_type.key_type, imports)
            self.collect_imports(field_type.value_type, imports)

    def generate_registration(self) -> List[str]:
        """Generate the Fory registration function."""
        lines = []

        lines.append("func RegisterTypes(f *fory.Fory) error {")

        # Register enums
        for enum in self.schema.enums:
            if enum.type_id is not None:
                lines.append(f"\tif err := f.RegisterEnum({enum.name}(0), {enum.type_id}); err != nil {{")
                lines.append("\t\treturn err")
                lines.append("\t}")
            else:
                ns = self.package or "default"
                lines.append(f'\tif err := f.RegisterTagType("{ns}.{enum.name}", {enum.name}(0)); err != nil {{')
                lines.append("\t\treturn err")
                lines.append("\t}")

        # Register messages
        for message in self.schema.messages:
            if message.type_id is not None:
                lines.append(f"\tif err := f.Register({message.name}{{}}, {message.type_id}); err != nil {{")
                lines.append("\t\treturn err")
                lines.append("\t}")
            else:
                ns = self.package or "default"
                lines.append(f'\tif err := f.RegisterTagType("{ns}.{message.name}", {message.name}{{}}); err != nil {{')
                lines.append("\t\treturn err")
                lines.append("\t}")

        lines.append("\treturn nil")
        lines.append("}")

        return lines
