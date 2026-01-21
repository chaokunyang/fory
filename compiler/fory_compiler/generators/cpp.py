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

"""C++ code generator."""

from typing import Dict, List, Optional, Set

from fory_compiler.generators.base import BaseGenerator, GeneratedFile
from fory_compiler.ir.ast import (
    Message,
    Enum,
    Field,
    FieldType,
    PrimitiveType,
    NamedType,
    ListType,
    MapType,
)
from fory_compiler.ir.types import PrimitiveKind


class CppGenerator(BaseGenerator):
    """Generates C++ classes with FORY_STRUCT macros."""

    language_name = "cpp"
    file_extension = ".h"

    # Mapping from FDL primitive types to C++ types
    PRIMITIVE_MAP = {
        PrimitiveKind.BOOL: "bool",
        PrimitiveKind.INT8: "int8_t",
        PrimitiveKind.INT16: "int16_t",
        PrimitiveKind.INT32: "int32_t",
        PrimitiveKind.VARINT32: "int32_t",
        PrimitiveKind.INT64: "int64_t",
        PrimitiveKind.VARINT64: "int64_t",
        PrimitiveKind.TAGGED_INT64: "int64_t",
        PrimitiveKind.UINT8: "uint8_t",
        PrimitiveKind.UINT16: "uint16_t",
        PrimitiveKind.UINT32: "uint32_t",
        PrimitiveKind.VAR_UINT32: "uint32_t",
        PrimitiveKind.UINT64: "uint64_t",
        PrimitiveKind.VAR_UINT64: "uint64_t",
        PrimitiveKind.TAGGED_UINT64: "uint64_t",
        PrimitiveKind.FLOAT16: "float",
        PrimitiveKind.FLOAT32: "float",
        PrimitiveKind.FLOAT64: "double",
        PrimitiveKind.STRING: "std::string",
        PrimitiveKind.BYTES: "std::vector<uint8_t>",
        PrimitiveKind.DATE: "fory::serialization::LocalDate",
        PrimitiveKind.TIMESTAMP: "fory::serialization::Timestamp",
    }

    def generate(self) -> List[GeneratedFile]:
        """Generate C++ files for the schema."""
        files = []

        # Generate a single header file with all types
        files.append(self.generate_header())

        return files

    def get_header_name(self) -> str:
        """Get the header file name."""
        if self.package:
            return self.package.replace(".", "_")
        return "generated"

    def get_namespace(self) -> str:
        """Get the C++ namespace."""
        if self.package:
            return self.package.replace(".", "::")
        return ""

    def get_namespaced_type_name(
        self,
        type_name: str,
        parent_stack: List[Message],
    ) -> str:
        """Get a C++ type name including namespace for global macros."""
        qualified_name = self.get_qualified_type_name(type_name, parent_stack)
        namespace = self.get_namespace()
        if namespace:
            return f"{namespace}::{qualified_name}"
        return qualified_name

    def get_field_config_type_name(
        self,
        type_name: str,
        parent_stack: List[Message],
        type_aliases: Dict[str, str],
    ) -> str:
        """Get a macro-safe type name for FORY_FIELD_CONFIG and record alias."""
        qualified_name = self.get_namespaced_type_name(type_name, parent_stack)
        if "::" in qualified_name:
            alias = f"ForyType_{qualified_name.replace('::', '_')}"
            type_aliases.setdefault(alias, qualified_name)
            return alias
        return qualified_name

    def generate_header(self) -> GeneratedFile:
        """Generate a C++ header file with all types."""
        lines = []
        includes: Set[str] = set()
        enum_macros: List[str] = []
        struct_macros: List[str] = []
        field_config_macros: List[str] = []
        type_aliases: Dict[str, str] = {}

        # Collect includes (including from nested types)
        includes.add("<cstdint>")
        includes.add("<string>")
        includes.add('"fory/serialization/fory.h"')

        for message in self.schema.messages:
            self.collect_message_includes(message, includes)

        # License header
        lines.append("/*")
        for line in self.get_license_header(" *").split("\n"):
            lines.append(line)
        lines.append(" */")
        lines.append("")

        # Header guard
        guard_name = f"{self.get_header_name().upper()}_H_"
        lines.append(f"#ifndef {guard_name}")
        lines.append(f"#define {guard_name}")
        lines.append("")

        # Includes
        for inc in sorted(includes):
            lines.append(f"#include {inc}")
        lines.append("")

        # Namespace
        namespace = self.get_namespace()
        if namespace:
            lines.append(f"namespace {namespace} {{")
            lines.append("")

        # Forward declarations (top-level messages only)
        self.generate_forward_declarations(lines)
        if self.schema.messages:
            lines.append("")

        # Generate enums (top-level)
        for enum in self.schema.enums:
            lines.extend(self.generate_enum_definition(enum))
            enum_macros.append(self.generate_enum_macro(enum, []))
            lines.append("")

        # Generate messages (with nested enums/messages defined inside classes)
        for message in self.schema.messages:
            lines.extend(
                self.generate_message_definition(
                    message,
                    [],
                    struct_macros,
                    enum_macros,
                    field_config_macros,
                    type_aliases,
                    "",
                )
            )
            lines.append("")

        if struct_macros:
            lines.extend(struct_macros)
            lines.append("")

        if namespace:
            lines.append(f"}} // namespace {namespace}")
            lines.append("")

        if type_aliases:
            for alias, target in sorted(type_aliases.items()):
                lines.append(f"using {alias} = {target};")
            lines.append("")

        if field_config_macros:
            lines.extend(field_config_macros)
            lines.append("")

        if enum_macros:
            lines.extend(enum_macros)
            lines.append("")

        if namespace:
            lines.append(f"namespace {namespace} {{")
            lines.append("")

        # Generate registration function (after FORY_STRUCT/FORY_ENUM)
        lines.extend(self.generate_registration())
        lines.append("")

        if namespace:
            lines.append(f"}} // namespace {namespace}")
            lines.append("")

        # End header guard
        lines.append(f"#endif // {guard_name}")
        lines.append("")

        return GeneratedFile(
            path=f"{self.get_header_name()}.h",
            content="\n".join(lines),
        )

    def collect_message_includes(self, message: Message, includes: Set[str]):
        """Collect includes for a message and its nested types recursively."""
        for field in message.fields:
            self.collect_includes(
                field.field_type,
                field.optional,
                field.ref,
                includes,
                field.element_optional,
                field.element_ref,
            )
        for nested_msg in message.nested_messages:
            self.collect_message_includes(nested_msg, includes)

    def generate_forward_declarations(self, lines: List[str]):
        """Generate forward declarations for top-level messages."""
        for message in self.schema.messages:
            lines.append(f"class {message.name};")

    def get_enum_value_names(self, enum: Enum) -> List[str]:
        """Get enum value names without the enum prefix."""
        stripped_names = []
        for value in enum.values:
            stripped_names.append(self.strip_enum_prefix(enum.name, value.name))
        return stripped_names

    def generate_enum_definition(self, enum: Enum, indent: str = "") -> List[str]:
        """Generate a C++ enum class definition."""
        lines = []
        lines.append(f"{indent}enum class {enum.name} : int32_t {{")
        for value in enum.values:
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            lines.append(f"{indent}    {stripped_name} = {value.value},")
        lines.append(f"{indent}}};")
        return lines

    def generate_enum_macro(
        self,
        enum: Enum,
        parent_stack: List[Message],
    ) -> str:
        """Generate a FORY_ENUM macro line for an enum."""
        value_names = ", ".join(self.get_enum_value_names(enum))
        qualified_name = self.get_namespaced_type_name(enum.name, parent_stack)
        return f"FORY_ENUM({qualified_name}, {value_names});"

    def generate_message_definition(
        self,
        message: Message,
        parent_stack: List[Message],
        struct_macros: List[str],
        enum_macros: List[str],
        field_config_macros: List[str],
        type_aliases: Dict[str, str],
        indent: str,
    ) -> List[str]:
        """Generate a C++ class definition with nested types."""
        lines = []
        class_name = message.name
        lineage = parent_stack + [message]
        body_indent = f"{indent}  "
        field_indent = f"{indent}    "

        lines.append(f"{indent}class {class_name} final {{")
        lines.append(f"{body_indent}public:")

        for nested_enum in message.nested_enums:
            lines.extend(self.generate_enum_definition(nested_enum, body_indent))
            lines.append("")
            enum_macros.append(self.generate_enum_macro(nested_enum, lineage))

        for nested_msg in message.nested_messages:
            lines.extend(
                self.generate_message_definition(
                    nested_msg,
                    lineage,
                    struct_macros,
                    enum_macros,
                    field_config_macros,
                    type_aliases,
                    body_indent,
                )
            )
            lines.append("")

        for field in message.fields:
            cpp_type = self.generate_type(
                field.field_type,
                field.optional,
                field.ref,
                field.element_optional,
                field.element_ref,
                lineage,
            )
            field_name = self.to_snake_case(field.name)
            lines.append(f"{field_indent}{cpp_type} {field_name};")

        lines.append("")

        lines.append(
            f"{body_indent}bool operator==(const {class_name}& other) const {{"
        )
        if message.fields:
            conditions = []
            for field in message.fields:
                field_name = self.to_snake_case(field.name)
                conditions.append(f"{field_name} == other.{field_name}")
            lines.append(f"{body_indent}  return {' && '.join(conditions)};")
        else:
            lines.append(f"{body_indent}  return true;")
        lines.append(f"{body_indent}}}")

        lines.append(f"{indent}}};")

        struct_type_name = self.get_qualified_type_name(message.name, parent_stack)
        if message.fields:
            field_names = ", ".join(self.to_snake_case(f.name) for f in message.fields)
            struct_macros.append(f"FORY_STRUCT({struct_type_name}, {field_names});")
            field_config_type_name = self.get_field_config_type_name(
                message.name, parent_stack, type_aliases
            )
            field_config_macros.append(
                self.generate_field_config_macro(message, field_config_type_name)
            )
        else:
            struct_macros.append(f"FORY_STRUCT({struct_type_name});")

        return lines

    def generate_field_config_macro(
        self,
        message: Message,
        qualified_name: str,
    ) -> str:
        """Generate FORY_FIELD_CONFIG macro for a message."""
        entries = []
        for field in message.fields:
            field_name = self.to_snake_case(field.name)
            meta = self.get_field_meta(field)
            entries.append(f"({field_name}, {meta})")
        joined = ", ".join(entries)
        return f"FORY_FIELD_CONFIG({qualified_name}, {joined});"

    def get_field_meta(self, field: Field) -> str:
        """Build FieldMeta expression for a field."""
        meta = "fory::FieldMeta{}"
        if field.tag_id is not None:
            meta += f".id({field.tag_id})"
        if field.optional:
            meta += ".nullable()"
        if field.ref or field.element_ref or field.options.get("tracking_ref") is True:
            meta += ".ref()"
        encoding = self.get_encoding_config(field.field_type)
        if encoding:
            meta += encoding
        return meta

    def get_encoding_config(self, field_type: FieldType) -> str:
        """Return encoding config for primitive types."""
        kind = None
        if isinstance(field_type, PrimitiveType):
            kind = field_type.kind
        elif isinstance(field_type, ListType) and isinstance(
            field_type.element_type, PrimitiveType
        ):
            kind = field_type.element_type.kind
        if kind is None:
            return ""
        if kind in (
            PrimitiveKind.INT32,
            PrimitiveKind.INT64,
            PrimitiveKind.UINT32,
            PrimitiveKind.UINT64,
        ):
            return ".fixed()"
        if kind in (PrimitiveKind.VAR_UINT32, PrimitiveKind.VAR_UINT64):
            return ".varint()"
        if kind in (PrimitiveKind.TAGGED_INT64, PrimitiveKind.TAGGED_UINT64):
            return ".tagged()"
        return ""

    def generate_type(
        self,
        field_type: FieldType,
        nullable: bool = False,
        ref: bool = False,
        element_optional: bool = False,
        element_ref: bool = False,
        parent_stack: Optional[List[Message]] = None,
    ) -> str:
        """Generate C++ type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable:
                return f"std::optional<{base_type}>"
            return base_type

        elif isinstance(field_type, NamedType):
            type_name = self.resolve_nested_type_name(field_type.name, parent_stack)
            if ref:
                type_name = f"std::shared_ptr<{type_name}>"
            if nullable:
                type_name = f"std::optional<{type_name}>"
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
            list_type = f"std::vector<{element_type}>"
            if ref:
                list_type = f"std::shared_ptr<{list_type}>"
            if nullable:
                list_type = f"std::optional<{list_type}>"
            return list_type

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(
                field_type.key_type, False, False, False, False, parent_stack
            )
            value_type = self.generate_type(
                field_type.value_type, False, False, False, False, parent_stack
            )
            map_type = f"std::map<{key_type}, {value_type}>"
            if ref:
                map_type = f"std::shared_ptr<{map_type}>"
            if nullable:
                map_type = f"std::optional<{map_type}>"
            return map_type

        return "void*"

    def get_qualified_type_name(
        self,
        type_name: str,
        parent_stack: List[Message],
    ) -> str:
        """Get the C++ qualified type name for nested types."""
        if not parent_stack:
            return type_name
        prefix = "::".join(parent.name for parent in parent_stack)
        return f"{prefix}::{type_name}"

    def get_registration_type_name(
        self,
        type_name: str,
        parent_stack: List[Message],
    ) -> str:
        """Get the dot-qualified type name used in registration."""
        if not parent_stack:
            return type_name
        prefix = ".".join(parent.name for parent in parent_stack)
        return f"{prefix}.{type_name}"

    def resolve_nested_type_name(
        self,
        type_name: str,
        parent_stack: Optional[List[Message]] = None,
    ) -> str:
        """Resolve nested type names to qualified C++ identifiers."""
        if "." in type_name:
            return type_name.replace(".", "::")
        if not parent_stack:
            return type_name

        for i in range(len(parent_stack) - 1, -1, -1):
            message = parent_stack[i]
            if message.get_nested_type(type_name) is not None:
                prefix = "::".join(parent.name for parent in parent_stack[: i + 1])
                return f"{prefix}::{type_name}"

        return type_name

    def collect_includes(
        self,
        field_type: FieldType,
        nullable: bool,
        ref: bool,
        includes: Set[str],
        element_optional: bool = False,
        element_ref: bool = False,
    ):
        """Collect required includes for a field type."""
        if nullable:
            includes.add("<optional>")
        if ref:
            includes.add("<memory>")

        if isinstance(field_type, PrimitiveType):
            if field_type.kind == PrimitiveKind.STRING:
                includes.add("<string>")
            elif field_type.kind == PrimitiveKind.BYTES:
                includes.add("<vector>")
            elif field_type.kind in (PrimitiveKind.DATE, PrimitiveKind.TIMESTAMP):
                includes.add('"fory/serialization/temporal_serializers.h"')

        elif isinstance(field_type, ListType):
            includes.add("<vector>")
            self.collect_includes(
                field_type.element_type,
                element_optional,
                element_ref,
                includes,
            )

        elif isinstance(field_type, MapType):
            includes.add("<map>")
            self.collect_includes(field_type.key_type, False, False, includes)
            self.collect_includes(field_type.value_type, False, False, includes)

    def generate_registration(self) -> List[str]:
        """Generate the Fory registration function."""
        lines = []

        lines.append("inline void RegisterTypes(fory::serialization::Fory& fory) {")

        # Register enums (top-level)
        for enum in self.schema.enums:
            self.generate_enum_registration(lines, enum, [])

        # Register messages (including nested types)
        for message in self.schema.messages:
            self.generate_message_registration(lines, message, [])

        lines.append("}")

        return lines

    def generate_enum_registration(
        self, lines: List[str], enum: Enum, parent_stack: List[Message]
    ):
        """Generate registration code for an enum."""
        code_name = self.get_qualified_type_name(enum.name, parent_stack)
        type_name = self.get_registration_type_name(enum.name, parent_stack)

        if enum.type_id is not None:
            lines.append(f"    fory.register_enum<{code_name}>({enum.type_id});")
        else:
            ns = self.package or "default"
            lines.append(f'    fory.register_enum<{code_name}>("{ns}", "{type_name}");')

    def generate_message_registration(
        self, lines: List[str], message: Message, parent_stack: List[Message]
    ):
        """Generate registration code for a message and its nested types."""
        code_name = self.get_qualified_type_name(message.name, parent_stack)
        type_name = self.get_registration_type_name(message.name, parent_stack)

        # Register nested enums first
        for nested_enum in message.nested_enums:
            self.generate_enum_registration(
                lines, nested_enum, parent_stack + [message]
            )

        # Register nested messages recursively
        for nested_msg in message.nested_messages:
            self.generate_message_registration(
                lines, nested_msg, parent_stack + [message]
            )

        # Register this message
        if message.type_id is not None:
            lines.append(f"    fory.register_struct<{code_name}>({message.type_id});")
        else:
            ns = self.package or "default"
            lines.append(
                f'    fory.register_struct<{code_name}>("{ns}", "{type_name}");'
            )
