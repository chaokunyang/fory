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

"""Java code generator."""

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


class JavaGenerator(BaseGenerator):
    """Generates Java POJOs with Fory annotations."""

    language_name = "java"
    file_extension = ".java"

    # Mapping from FDL primitive types to Java types
    PRIMITIVE_MAP = {
        PrimitiveKind.BOOL: "boolean",
        PrimitiveKind.INT8: "byte",
        PrimitiveKind.INT16: "short",
        PrimitiveKind.INT32: "int",
        PrimitiveKind.INT64: "long",
        PrimitiveKind.FLOAT32: "float",
        PrimitiveKind.FLOAT64: "double",
        PrimitiveKind.STRING: "String",
        PrimitiveKind.BYTES: "byte[]",
        PrimitiveKind.DATE: "java.time.LocalDate",
        PrimitiveKind.TIMESTAMP: "java.time.Instant",
    }

    # Boxed versions for nullable primitives
    BOXED_MAP = {
        PrimitiveKind.BOOL: "Boolean",
        PrimitiveKind.INT8: "Byte",
        PrimitiveKind.INT16: "Short",
        PrimitiveKind.INT32: "Integer",
        PrimitiveKind.INT64: "Long",
        PrimitiveKind.FLOAT32: "Float",
        PrimitiveKind.FLOAT64: "Double",
    }

    def generate(self) -> List[GeneratedFile]:
        """Generate Java files for the schema."""
        files = []

        # Generate enum files
        for enum in self.schema.enums:
            files.append(self.generate_enum_file(enum))

        # Generate message files
        for message in self.schema.messages:
            files.append(self.generate_message_file(message))

        # Generate registration helper
        files.append(self.generate_registration_file())

        return files

    def get_java_package_path(self) -> str:
        """Get the Java package as a path."""
        if self.package:
            return self.package.replace(".", "/")
        return ""

    def generate_enum_file(self, enum: Enum) -> GeneratedFile:
        """Generate a Java enum file."""
        lines = []

        # License header
        lines.append(self.get_license_header())
        lines.append("")

        # Package
        if self.package:
            lines.append(f"package {self.package};")
            lines.append("")

        # Enum declaration
        lines.append(f"public enum {enum.name} {{")

        # Enum values
        for i, value in enumerate(enum.values):
            comma = "," if i < len(enum.values) - 1 else ";"
            lines.append(f"    {value.name}{comma}")

        lines.append("}")
        lines.append("")

        # Build file path
        path = self.get_java_package_path()
        if path:
            path = f"{path}/{enum.name}.java"
        else:
            path = f"{enum.name}.java"

        return GeneratedFile(path=path, content="\n".join(lines))

    def generate_message_file(self, message: Message) -> GeneratedFile:
        """Generate a Java class file for a message."""
        lines = []
        imports: Set[str] = set()

        # Collect imports
        for field in message.fields:
            self.collect_imports(field.field_type, imports)
            if field.optional or field.ref:
                imports.add("org.apache.fory.annotation.ForyField")

        # Add imports for equals/hashCode
        imports.add("java.util.Objects")
        if self.has_array_field(message):
            imports.add("java.util.Arrays")

        # License header
        lines.append(self.get_license_header())
        lines.append("")

        # Package
        if self.package:
            lines.append(f"package {self.package};")
            lines.append("")

        # Imports
        if imports:
            for imp in sorted(imports):
                lines.append(f"import {imp};")
            lines.append("")

        # Class declaration
        lines.append(f"public class {message.name} {{")

        # Fields
        for field in message.fields:
            field_lines = self.generate_field(field)
            for line in field_lines:
                lines.append(f"    {line}")

        lines.append("")

        # Default constructor
        lines.append(f"    public {message.name}() {{")
        lines.append("    }")
        lines.append("")

        # Getters and setters
        for field in message.fields:
            getter_setter = self.generate_getter_setter(field)
            for line in getter_setter:
                lines.append(f"    {line}")

        # equals method
        for line in self.generate_equals_method(message):
            lines.append(f"    {line}")

        # hashCode method
        for line in self.generate_hashcode_method(message):
            lines.append(f"    {line}")

        lines.append("}")
        lines.append("")

        # Build file path
        path = self.get_java_package_path()
        if path:
            path = f"{path}/{message.name}.java"
        else:
            path = f"{message.name}.java"

        return GeneratedFile(path=path, content="\n".join(lines))

    def generate_field(self, field: Field) -> List[str]:
        """Generate field declaration with annotations."""
        lines = []

        # Generate @ForyField annotation if needed
        annotations = []
        if field.optional:
            annotations.append("nullable = true")
        if field.ref:
            annotations.append("trackingRef = true")

        if annotations:
            lines.append(f"@ForyField({', '.join(annotations)})")

        # Field type
        java_type = self.generate_type(field.field_type, field.optional)

        lines.append(f"private {java_type} {self.to_camel_case(field.name)};")
        lines.append("")

        return lines

    def generate_getter_setter(self, field: Field) -> List[str]:
        """Generate getter and setter for a field."""
        lines = []
        java_type = self.generate_type(field.field_type, field.optional)
        field_name = self.to_camel_case(field.name)
        pascal_name = self.to_pascal_case(field.name)

        # Getter
        lines.append(f"public {java_type} get{pascal_name}() {{")
        lines.append(f"    return {field_name};")
        lines.append("}")
        lines.append("")

        # Setter
        lines.append(f"public void set{pascal_name}({java_type} {field_name}) {{")
        lines.append(f"    this.{field_name} = {field_name};")
        lines.append("}")
        lines.append("")

        return lines

    def generate_type(self, field_type: FieldType, nullable: bool = False) -> str:
        """Generate Java type string."""
        if isinstance(field_type, PrimitiveType):
            if nullable and field_type.kind in self.BOXED_MAP:
                return self.BOXED_MAP[field_type.kind]
            return self.PRIMITIVE_MAP[field_type.kind]

        elif isinstance(field_type, NamedType):
            return field_type.name

        elif isinstance(field_type, ListType):
            element_type = self.generate_type(field_type.element_type, True)
            return f"List<{element_type}>"

        elif isinstance(field_type, MapType):
            key_type = self.generate_type(field_type.key_type, True)
            value_type = self.generate_type(field_type.value_type, True)
            return f"Map<{key_type}, {value_type}>"

        return "Object"

    def collect_imports(self, field_type: FieldType, imports: Set[str]):
        """Collect required imports for a field type."""
        if isinstance(field_type, PrimitiveType):
            if field_type.kind == PrimitiveKind.DATE:
                imports.add("java.time.LocalDate")
            elif field_type.kind == PrimitiveKind.TIMESTAMP:
                imports.add("java.time.Instant")

        elif isinstance(field_type, ListType):
            imports.add("java.util.List")
            self.collect_imports(field_type.element_type, imports)

        elif isinstance(field_type, MapType):
            imports.add("java.util.Map")
            self.collect_imports(field_type.key_type, imports)
            self.collect_imports(field_type.value_type, imports)

    def has_array_field(self, message: Message) -> bool:
        """Check if message has any byte[] fields."""
        for field in message.fields:
            if isinstance(field.field_type, PrimitiveType):
                if field.field_type.kind == PrimitiveKind.BYTES:
                    return True
        return False

    def generate_equals_method(self, message: Message) -> List[str]:
        """Generate equals() method for a message."""
        lines = []
        lines.append("@Override")
        lines.append("public boolean equals(Object o) {")
        lines.append("    if (this == o) return true;")
        lines.append(f"    if (o == null || getClass() != o.getClass()) return false;")
        lines.append(f"    {message.name} that = ({message.name}) o;")

        if not message.fields:
            lines.append("    return true;")
        else:
            comparisons = []
            for field in message.fields:
                field_name = self.to_camel_case(field.name)
                if isinstance(field.field_type, PrimitiveType):
                    kind = field.field_type.kind
                    if kind == PrimitiveKind.BYTES:
                        comparisons.append(f"Arrays.equals({field_name}, that.{field_name})")
                    elif kind in (PrimitiveKind.FLOAT32,):
                        comparisons.append(f"Float.compare({field_name}, that.{field_name}) == 0")
                    elif kind in (PrimitiveKind.FLOAT64,):
                        comparisons.append(f"Double.compare({field_name}, that.{field_name}) == 0")
                    elif kind in (PrimitiveKind.BOOL, PrimitiveKind.INT8, PrimitiveKind.INT16,
                                  PrimitiveKind.INT32, PrimitiveKind.INT64) and not field.optional:
                        comparisons.append(f"{field_name} == that.{field_name}")
                    else:
                        comparisons.append(f"Objects.equals({field_name}, that.{field_name})")
                else:
                    comparisons.append(f"Objects.equals({field_name}, that.{field_name})")

            if len(comparisons) == 1:
                lines.append(f"    return {comparisons[0]};")
            else:
                lines.append(f"    return {comparisons[0]}")
                for i, comp in enumerate(comparisons[1:], 1):
                    if i == len(comparisons) - 1:
                        lines.append(f"        && {comp};")
                    else:
                        lines.append(f"        && {comp}")

        lines.append("}")
        lines.append("")
        return lines

    def generate_hashcode_method(self, message: Message) -> List[str]:
        """Generate hashCode() method for a message."""
        lines = []
        lines.append("@Override")
        lines.append("public int hashCode() {")

        if not message.fields:
            lines.append("    return 0;")
        else:
            hash_args = []
            array_fields = []
            for field in message.fields:
                field_name = self.to_camel_case(field.name)
                if isinstance(field.field_type, PrimitiveType):
                    if field.field_type.kind == PrimitiveKind.BYTES:
                        array_fields.append(field_name)
                    else:
                        hash_args.append(field_name)
                else:
                    hash_args.append(field_name)

            if array_fields and hash_args:
                lines.append(f"    int result = Objects.hash({', '.join(hash_args)});")
                for arr in array_fields:
                    lines.append(f"    result = 31 * result + Arrays.hashCode({arr});")
                lines.append("    return result;")
            elif array_fields:
                if len(array_fields) == 1:
                    lines.append(f"    return Arrays.hashCode({array_fields[0]});")
                else:
                    lines.append(f"    int result = Arrays.hashCode({array_fields[0]});")
                    for arr in array_fields[1:]:
                        lines.append(f"    result = 31 * result + Arrays.hashCode({arr});")
                    lines.append("    return result;")
            else:
                lines.append(f"    return Objects.hash({', '.join(hash_args)});")

        lines.append("}")
        lines.append("")
        return lines

    def generate_registration_file(self) -> GeneratedFile:
        """Generate the Fory registration helper class."""
        lines = []

        # Determine class name
        if self.package:
            parts = self.package.split(".")
            class_name = self.to_pascal_case(parts[-1]) + "ForyRegistration"
        else:
            class_name = "ForyRegistration"

        # License header
        lines.append(self.get_license_header())
        lines.append("")

        # Package
        if self.package:
            lines.append(f"package {self.package};")
            lines.append("")

        # Imports
        lines.append("import org.apache.fory.Fory;")
        lines.append("")

        # Class
        lines.append(f"public class {class_name} {{")
        lines.append("")
        lines.append("    public static void register(Fory fory) {")

        # Register enums
        for enum in self.schema.enums:
            if enum.type_id is not None:
                lines.append(f"        fory.register({enum.name}.class, {enum.type_id});")
            else:
                ns = self.package or "default"
                lines.append(f'        fory.register({enum.name}.class, "{ns}", "{enum.name}");')

        # Register messages
        for message in self.schema.messages:
            if message.type_id is not None:
                lines.append(f"        fory.register({message.name}.class, {message.type_id});")
            else:
                ns = self.package or "default"
                lines.append(f'        fory.register({message.name}.class, "{ns}", "{message.name}");')

        lines.append("    }")
        lines.append("}")
        lines.append("")

        # Build file path
        path = self.get_java_package_path()
        if path:
            path = f"{path}/{class_name}.java"
        else:
            path = f"{class_name}.java"

        return GeneratedFile(path=path, content="\n".join(lines))
