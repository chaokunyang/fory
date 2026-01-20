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

from typing import List, Optional, Set, Tuple

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


class GoGenerator(BaseGenerator):
    """Generates Go structs with fory tags."""

    language_name = "go"
    file_extension = ".go"
    indent_str = "\t"  # Go uses tabs

    def get_go_package_info(self) -> Tuple[Optional[str], str]:
        """Parse go_package option and return (import_path, package_name).

        Supports format: "github.com/mycorp/apis/gen/payment/v1;paymentv1"
        - Part before ';' is the import path
        - Part after ';' is the package name
        - If no ';', the last element of the import path is used as package name
        - If no go_package option, falls back to FDL package

        Returns:
            Tuple of (import_path, package_name). import_path may be None.
        """
        go_package = self.schema.get_option("go_package")
        if go_package:
            if ";" in go_package:
                import_path, package_name = go_package.split(";", 1)
                return (import_path, package_name)
            else:
                # Use last element of path as package name
                parts = go_package.rstrip("/").split("/")
                return (go_package, parts[-1])

        # Fall back to FDL package
        if self.schema.package:
            parts = self.schema.package.split(".")
            return (None, parts[-1])

        return (None, "generated")

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
        _, package_name = self.get_go_package_info()
        return package_name

    def get_file_name(self) -> str:
        """Get the Go file name."""
        if self.package:
            return self.package.replace(".", "_")
        return "generated"

    def generate_file(self) -> GeneratedFile:
        """Generate a Go file with all types."""
        lines = []
        imports: Set[str] = set()

        # Collect imports (including from nested types)
        imports.add('fory "github.com/apache/fory/go/fory"')

        for message in self.schema.messages:
            self.collect_message_imports(message, imports)

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
                lines.append(f"\t{imp}")
            lines.append(")")
            lines.append("")

        # Generate enums (top-level)
        for enum in self.schema.enums:
            lines.extend(self.generate_enum(enum, ""))
            lines.append("")

        # Generate messages (including nested as flat types with qualified names)
        for message in self.schema.messages:
            lines.extend(self.generate_message_with_nested(message))

        # Generate registration function
        lines.extend(self.generate_registration())
        lines.append("")

        return GeneratedFile(
            path=f"{self.get_file_name()}.go",
            content="\n".join(lines),
        )

    def collect_message_imports(self, message: Message, imports: Set[str]):
        """Collect imports for a message and its nested types recursively."""
        for field in message.fields:
            self.collect_imports(field.field_type, imports)
        for nested_msg in message.nested_messages:
            self.collect_message_imports(nested_msg, imports)

    def generate_enum(self, enum: Enum, parent_name: str = "") -> List[str]:
        """Generate a Go enum (using type alias and constants)."""
        lines = []

        # For nested enums, use Parent_Child naming
        type_name = f"{parent_name}_{enum.name}" if parent_name else enum.name

        # Type definition
        lines.append(f"type {type_name} int32")
        lines.append("")

        # Constants (strip prefix first, then add enum name back for Go's unscoped style)
        lines.append("const (")
        for value in enum.values:
            # Strip the proto-style prefix (e.g., DEVICE_TIER_UNKNOWN -> UNKNOWN)
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            # Add enum name prefix for Go (e.g., DeviceTierUnknown)
            const_name = f"{type_name}{self.to_pascal_case(stripped_name)}"
            lines.append(f"\t{const_name} {type_name} = {value.value}")
        lines.append(")")

        return lines

    def generate_message(
        self,
        message: Message,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a Go struct."""
        lines = []

        # For nested messages, use Parent_Child naming
        prefix = "_".join(parent.name for parent in parent_stack or [])
        type_name = f"{prefix}_{message.name}" if prefix else message.name
        lineage = (parent_stack or []) + [message]

        lines.append(f"type {type_name} struct {{")

        # Fields
        for field in message.fields:
            field_lines = self.generate_field(field, lineage)
            for line in field_lines:
                lines.append(f"\t{line}")

        lines.append("}")

        return lines

    def generate_message_with_nested(
        self,
        message: Message,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a Go struct and all its nested types (flattened)."""
        lines = []

        # Current message's type name
        prefix = "_".join(parent.name for parent in parent_stack or [])
        type_name = f"{prefix}_{message.name}" if prefix else message.name
        lineage = (parent_stack or []) + [message]

        # First, generate all nested enums
        for nested_enum in message.nested_enums:
            lines.extend(self.generate_enum(nested_enum, type_name))
            lines.append("")

        # Then, generate all nested messages (recursively)
        for nested_msg in message.nested_messages:
            lines.extend(self.generate_message_with_nested(nested_msg, lineage))

        # Finally, generate this message
        lines.extend(self.generate_message(message, parent_stack))
        lines.append("")

        return lines

    def generate_field(
        self,
        field: Field,
        parent_stack: Optional[List[Message]] = None,
    ) -> List[str]:
        """Generate a struct field."""
        lines = []

        go_type = self.generate_type(
            field.field_type,
            field.optional,
            field.ref,
            field.element_optional,
            field.element_ref,
            parent_stack,
        )
        field_name = self.to_pascal_case(
            field.name
        )  # Go uses PascalCase for exported fields

        # Build fory tag
        tags = []
        is_list = isinstance(field.field_type, ListType)
        nullable_tag: Optional[bool] = None
        ref_tag: Optional[bool] = None

        if field.optional:
            nullable_tag = True
        elif is_list and (field.ref or field.element_optional or field.element_ref):
            nullable_tag = False

        if field.ref:
            ref_tag = True
        elif is_list and field.element_ref:
            ref_tag = False

        if nullable_tag is True:
            tags.append("nullable")
        elif nullable_tag is False:
            tags.append("nullable=false")

        if ref_tag is True:
            tags.append("ref")
        elif ref_tag is False:
            tags.append("ref=false")

        if tags:
            tag_str = ",".join(tags)
            lines.append(f'{field_name} {go_type} `fory:"{tag_str}"`')
        else:
            lines.append(f"{field_name} {go_type}")

        return lines

    def generate_type(
        self,
        field_type: FieldType,
        nullable: bool = False,
        ref: bool = False,
        element_optional: bool = False,
        element_ref: bool = False,
        parent_stack: Optional[List[Message]] = None,
    ) -> str:
        """Generate Go type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable and base_type not in ("[]byte",):
                return f"*{base_type}"
            return base_type

        elif isinstance(field_type, NamedType):
            type_name = self.resolve_nested_type_name(field_type.name, parent_stack)
            if nullable or ref:
                return f"*{type_name}"
            return type_name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(
                field_type.element_type,
                element_optional,
                element_ref,
                False,
                False,
                parent_stack,
            )
            return f"[]{element_type}"

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(
                field_type.key_type,
                False,
                False,
                False,
                False,
                parent_stack,
            )
            value_type = self.generate_type(
                field_type.value_type,
                False,
                False,
                False,
                False,
                parent_stack,
            )
            return f"map[{key_type}]{value_type}"

        return "interface{}"

    def resolve_nested_type_name(
        self,
        type_name: str,
        parent_stack: Optional[List[Message]] = None,
    ) -> str:
        """Resolve nested type names to flattened Go identifiers."""
        if "." in type_name:
            return type_name.replace(".", "_")
        if not parent_stack:
            return type_name

        for i in range(len(parent_stack) - 1, -1, -1):
            message = parent_stack[i]
            if message.get_nested_type(type_name) is not None:
                prefix = "_".join(parent.name for parent in parent_stack[: i + 1])
                return f"{prefix}_{type_name}"

        return type_name

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

        # Register enums (top-level)
        for enum in self.schema.enums:
            self.generate_enum_registration(lines, enum, "")

        # Register messages (including nested types)
        for message in self.schema.messages:
            self.generate_message_registration(lines, message, "")

        lines.append("\treturn nil")
        lines.append("}")

        return lines

    def generate_enum_registration(
        self, lines: List[str], enum: Enum, parent_name: str
    ):
        """Generate registration code for an enum."""
        type_name = f"{parent_name}_{enum.name}" if parent_name else enum.name

        if enum.type_id is not None:
            lines.append(
                f"\tif err := f.RegisterEnum({type_name}(0), {enum.type_id}); err != nil {{"
            )
            lines.append("\t\treturn err")
            lines.append("\t}")
        else:
            # Use FDL package for namespace (consistent across languages)
            ns = self.schema.package or "default"
            lines.append(
                f'\tif err := f.RegisterNamedEnum({type_name}(0), "{ns}.{type_name}"); err != nil {{'
            )
            lines.append("\t\treturn err")
            lines.append("\t}")

    def generate_message_registration(
        self, lines: List[str], message: Message, parent_name: str
    ):
        """Generate registration code for a message and its nested types."""
        type_name = f"{parent_name}_{message.name}" if parent_name else message.name

        # Register nested enums first
        for nested_enum in message.nested_enums:
            self.generate_enum_registration(lines, nested_enum, type_name)

        # Register nested messages recursively
        for nested_msg in message.nested_messages:
            self.generate_message_registration(lines, nested_msg, type_name)

        # Register this message
        if message.type_id is not None:
            lines.append(
                f"\tif err := f.RegisterStruct({type_name}{{}}, {message.type_id}); err != nil {{"
            )
            lines.append("\t\treturn err")
            lines.append("\t}")
        else:
            # Use FDL package for namespace (consistent across languages)
            ns = self.schema.package or "default"
            lines.append(
                f'\tif err := f.RegisterNamedStruct({type_name}{{}}, "{ns}.{type_name}"); err != nil {{'
            )
            lines.append("\t\treturn err")
            lines.append("\t}")
