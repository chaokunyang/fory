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

"""Translate Proto AST into Fory IR."""

from typing import Dict, List, Optional, Tuple

from fory_compiler.frontend.proto.ast import (
    ProtoSchema,
    ProtoMessage,
    ProtoEnum,
    ProtoField,
    ProtoType,
)
from fory_compiler.ir.ast import (
    Schema,
    Message,
    Enum,
    EnumValue,
    Field,
    Import,
    PrimitiveType,
    NamedType,
    ListType,
    MapType,
    PrimitiveKind,
)


class ProtoTranslator:
    """Translate Proto AST to Fory IR."""

    TYPE_MAPPING: Dict[str, PrimitiveKind] = {
        "bool": PrimitiveKind.BOOL,
        "int32": PrimitiveKind.VAR_UINT32,
        "int64": PrimitiveKind.VAR_UINT64,
        "sint32": PrimitiveKind.VARINT32,
        "sint64": PrimitiveKind.VARINT64,
        "uint32": PrimitiveKind.VAR_UINT32,
        "uint64": PrimitiveKind.VAR_UINT64,
        "fixed32": PrimitiveKind.UINT32,
        "fixed64": PrimitiveKind.UINT64,
        "sfixed32": PrimitiveKind.INT32,
        "sfixed64": PrimitiveKind.INT64,
        "float": PrimitiveKind.FLOAT32,
        "double": PrimitiveKind.FLOAT64,
        "string": PrimitiveKind.STRING,
        "bytes": PrimitiveKind.BYTES,
    }

    WELL_KNOWN_TYPES: Dict[str, PrimitiveKind] = {
        "google.protobuf.Timestamp": PrimitiveKind.TIMESTAMP,
        "google.protobuf.Duration": PrimitiveKind.DURATION,
    }

    def __init__(self, proto_schema: ProtoSchema):
        self.proto_schema = proto_schema
        self.warnings: List[str] = []

    def translate(self) -> Schema:
        return Schema(
            package=self.proto_schema.package,
            imports=self._translate_imports(),
            enums=[self._translate_enum(e) for e in self.proto_schema.enums],
            messages=[self._translate_message(m) for m in self.proto_schema.messages],
            options=self._translate_file_options(self.proto_schema.options),
            source_format="proto",
        )

    def _translate_imports(self) -> List[Import]:
        return [Import(path=imp) for imp in self.proto_schema.imports]

    def _translate_file_options(self, options: Dict[str, object]) -> Dict[str, object]:
        translated = {}
        for name, value in options.items():
            if name.startswith("fory."):
                translated[name.removeprefix("fory.")] = value
            else:
                translated[name] = value
        return translated

    def _translate_enum(self, proto_enum: ProtoEnum) -> Enum:
        type_id, options = self._translate_type_options(proto_enum.options)
        values = [
            EnumValue(
                name=v.name,
                value=v.value,
                line=v.line,
                column=v.column,
            )
            for v in proto_enum.values
        ]
        return Enum(
            name=proto_enum.name,
            type_id=type_id,
            values=values,
            options=options,
            line=proto_enum.line,
            column=proto_enum.column,
        )

    def _translate_message(self, proto_msg: ProtoMessage) -> Message:
        if proto_msg.oneofs:
            raise ValueError(f"oneof is not supported yet in '{proto_msg.name}'")

        type_id, options = self._translate_type_options(proto_msg.options)
        fields = [self._translate_field(f) for f in proto_msg.fields]
        nested_messages = [self._translate_message(m) for m in proto_msg.nested_messages]
        nested_enums = [self._translate_enum(e) for e in proto_msg.nested_enums]
        return Message(
            name=proto_msg.name,
            type_id=type_id,
            fields=fields,
            nested_messages=nested_messages,
            nested_enums=nested_enums,
            options=options,
            line=proto_msg.line,
            column=proto_msg.column,
        )

    def _translate_field(self, proto_field: ProtoField) -> Field:
        field_type = self._translate_field_type(proto_field.field_type)
        if proto_field.label == "repeated":
            field_type = ListType(field_type)

        ref, nullable, options = self._translate_field_options(proto_field.options)
        optional = proto_field.label == "optional" or nullable

        return Field(
            name=proto_field.name,
            field_type=field_type,
            number=proto_field.number,
            optional=optional,
            ref=ref,
            options=options,
            line=proto_field.line,
            column=proto_field.column,
        )

    def _translate_field_type(self, proto_type: ProtoType):
        if proto_type.is_map:
            key_type = self._translate_type_name(proto_type.map_key_type or "")
            value_type = self._translate_type_name(proto_type.map_value_type or "")
            return MapType(key_type, value_type)
        return self._translate_type_name(proto_type.name)

    def _translate_type_name(self, type_name: str):
        cleaned = type_name.lstrip(".")
        if cleaned in self.WELL_KNOWN_TYPES:
            return PrimitiveType(self.WELL_KNOWN_TYPES[cleaned])
        if cleaned in self.TYPE_MAPPING:
            return PrimitiveType(self.TYPE_MAPPING[cleaned])
        return NamedType(cleaned)

    def _translate_type_options(self, options: Dict[str, object]) -> Tuple[Optional[int], Dict[str, object]]:
        type_id = None
        translated: Dict[str, object] = {}
        for name, value in options.items():
            if name == "fory.id":
                type_id = value
            elif name.startswith("fory."):
                translated[name.removeprefix("fory.")] = value
        return type_id, translated

    def _translate_field_options(
        self, options: Dict[str, object]
    ) -> Tuple[bool, bool, Dict[str, object]]:
        ref = False
        nullable = False
        translated: Dict[str, object] = {}
        for name, value in options.items():
            if name == "fory.ref" and value:
                ref = True
            elif name == "fory.tracking_ref" and value:
                ref = True
                translated["tracking_ref"] = True
            elif name == "fory.nullable" and value:
                nullable = True
            elif name.startswith("fory."):
                translated[name.removeprefix("fory.")] = value
        return ref, nullable, translated
