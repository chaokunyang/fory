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


class CppGenerator(BaseGenerator):
    """Generates C++ structs with FORY_STRUCT macros."""

    language_name = "cpp"
    file_extension = ".h"

    # Mapping from FDL primitive types to C++ types
    PRIMITIVE_MAP = {
        PrimitiveKind.BOOL: "bool",
        PrimitiveKind.INT8: "int8_t",
        PrimitiveKind.INT16: "int16_t",
        PrimitiveKind.INT32: "int32_t",
        PrimitiveKind.INT64: "int64_t",
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

    def generate_header(self) -> GeneratedFile:
        """Generate a C++ header file with all types."""
        lines = []
        includes: Set[str] = set()

        # Collect includes
        includes.add("<cstdint>")
        includes.add("<string>")
        includes.add('"fory/serialization/fory.h"')

        for message in self.schema.messages:
            for field in message.fields:
                self.collect_includes(field.field_type, field.optional, field.ref, includes)

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

        # Forward declarations
        for message in self.schema.messages:
            lines.append(f"struct {message.name};")
        if self.schema.messages:
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

        # Close namespace
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

    def generate_enum(self, enum: Enum) -> List[str]:
        """Generate a C++ enum class."""
        lines = []

        lines.append(f"enum class {enum.name} : int32_t {{")
        # Enum values (strip prefix for scoped enums)
        stripped_names = []
        for value in enum.values:
            stripped_name = self.strip_enum_prefix(enum.name, value.name)
            stripped_names.append(stripped_name)
            lines.append(f"    {stripped_name} = {value.value},")
        lines.append("};")

        # FORY_ENUM macro
        value_names = ", ".join(stripped_names)
        lines.append(f"FORY_ENUM({enum.name}, {value_names});")

        return lines

    def generate_message(self, message: Message) -> List[str]:
        """Generate a C++ struct."""
        lines = []

        lines.append(f"struct {message.name} {{")

        # Fields
        for field in message.fields:
            cpp_type = self.generate_type(field.field_type, field.optional, field.ref)
            field_name = self.to_snake_case(field.name)
            lines.append(f"    {cpp_type} {field_name};")

        lines.append("")

        # Equality operator
        lines.append(f"    bool operator==(const {message.name}& other) const {{")
        if message.fields:
            conditions = []
            for field in message.fields:
                field_name = self.to_snake_case(field.name)
                conditions.append(f"{field_name} == other.{field_name}")
            lines.append(f"        return {' && '.join(conditions)};")
        else:
            lines.append("        return true;")
        lines.append("    }")

        lines.append("};")

        # FORY_STRUCT macro
        field_names = ", ".join(self.to_snake_case(f.name) for f in message.fields)
        lines.append(f"FORY_STRUCT({message.name}, {field_names});")

        return lines

    def generate_type(self, field_type: FieldType, nullable: bool = False, ref: bool = False) -> str:
        """Generate C++ type string."""
        if isinstance(field_type, PrimitiveType):
            base_type = self.PRIMITIVE_MAP[field_type.kind]
            if nullable:
                return f"std::optional<{base_type}>"
            return base_type

        elif isinstance(field_type, NamedType):
            if ref:
                return f"std::shared_ptr<{field_type.name}>"
            if nullable:
                return f"std::optional<{field_type.name}>"
            return field_type.name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(field_type.element_type, False, False)
            return f"std::vector<{element_type}>"

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(field_type.key_type, False, False)
            value_type = self.generate_type(field_type.value_type, False, False)
            return f"std::map<{key_type}, {value_type}>"

        return "void*"

    def collect_includes(self, field_type: FieldType, nullable: bool, ref: bool, includes: Set[str]):
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
            self.collect_includes(field_type.element_type, False, False, includes)

        elif isinstance(field_type, MapType):
            includes.add("<map>")
            self.collect_includes(field_type.key_type, False, False, includes)
            self.collect_includes(field_type.value_type, False, False, includes)

    def generate_registration(self) -> List[str]:
        """Generate the Fory registration function."""
        lines = []

        lines.append("inline void RegisterTypes(fory::serialization::Fory& fory) {")

        # Register enums
        for enum in self.schema.enums:
            if enum.type_id is not None:
                lines.append(f"    fory.register_enum<{enum.name}>({enum.type_id});")
            else:
                ns = self.package or "default"
                lines.append(f'    fory.register_enum<{enum.name}>("{ns}", "{enum.name}");')

        # Register messages
        for message in self.schema.messages:
            if message.type_id is not None:
                lines.append(f"    fory.register_struct<{message.name}>({message.type_id});")
            else:
                ns = self.package or "default"
                lines.append(f'    fory.register_struct<{message.name}>("{ns}", "{message.name}");')

        lines.append("}")

        return lines
