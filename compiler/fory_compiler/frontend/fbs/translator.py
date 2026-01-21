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

"""Translate FlatBuffers AST into Fory IR."""

from typing import Dict, List

from fory_compiler.frontend.fbs.ast import (
    FbsEnum,
    FbsSchema,
    FbsStruct,
    FbsTable,
    FbsTypeName,
    FbsTypeRef,
    FbsVectorType,
)
from fory_compiler.ir.ast import (
    Enum,
    EnumValue,
    Field,
    Import,
    ListType,
    Message,
    NamedType,
    PrimitiveType,
    Schema,
    SourceLocation,
)
from fory_compiler.ir.types import PrimitiveKind


class FbsTranslator:
    """Translate FlatBuffers AST to Fory IR."""

    TYPE_MAPPING: Dict[str, PrimitiveKind] = {
        "byte": PrimitiveKind.INT8,
        "ubyte": PrimitiveKind.UINT8,
        "short": PrimitiveKind.INT16,
        "ushort": PrimitiveKind.UINT16,
        "int": PrimitiveKind.VARINT32,
        "uint": PrimitiveKind.VAR_UINT32,
        "long": PrimitiveKind.VARINT64,
        "ulong": PrimitiveKind.VAR_UINT64,
        "float": PrimitiveKind.FLOAT32,
        "double": PrimitiveKind.FLOAT64,
        "bool": PrimitiveKind.BOOL,
        "string": PrimitiveKind.STRING,
    }

    def __init__(self, schema: FbsSchema):
        self.schema = schema

    def _location(self, line: int, column: int) -> SourceLocation:
        return SourceLocation(
            file=self.schema.source_file or "<input>",
            line=line,
            column=column,
            source_format="fbs",
        )

    def translate(self) -> Schema:
        return Schema(
            package=self.schema.namespace,
            imports=[Import(path=inc) for inc in self.schema.includes],
            enums=[self._translate_enum(e) for e in self.schema.enums],
            messages=self._translate_messages(),
            options={},
            source_file=self.schema.source_file,
            source_format="fbs",
        )

    def _translate_enum(self, fbs_enum: FbsEnum) -> Enum:
        values = [
            EnumValue(
                name=v.name,
                value=v.value,
                line=v.line,
                column=v.column,
                location=self._location(v.line, v.column),
            )
            for v in fbs_enum.values
        ]
        return Enum(
            name=fbs_enum.name,
            type_id=None,
            values=values,
            options=dict(fbs_enum.attributes),
            line=fbs_enum.line,
            column=fbs_enum.column,
            location=self._location(fbs_enum.line, fbs_enum.column),
        )

    def _translate_messages(self) -> List[Message]:
        messages: List[Message] = []
        ordered = []
        for struct in self.schema.structs:
            ordered.append(("struct", struct.line, struct.column, struct))
        for table in self.schema.tables:
            ordered.append(("table", table.line, table.column, table))
        for kind, _line, _column, item in sorted(ordered, key=lambda v: (v[1], v[2])):
            if kind == "struct":
                messages.append(self._translate_struct(item))
            else:
                messages.append(self._translate_table(item))
        return messages

    def _translate_struct(self, fbs_struct: FbsStruct) -> Message:
        options = dict(fbs_struct.attributes)
        options["evolving"] = False
        fields = self._translate_fields(fbs_struct.fields)
        return Message(
            name=fbs_struct.name,
            type_id=None,
            fields=fields,
            options=options,
            line=fbs_struct.line,
            column=fbs_struct.column,
            location=self._location(fbs_struct.line, fbs_struct.column),
        )

    def _translate_table(self, fbs_table: FbsTable) -> Message:
        options = dict(fbs_table.attributes)
        options["evolving"] = True
        fields = self._translate_fields(fbs_table.fields)
        return Message(
            name=fbs_table.name,
            type_id=None,
            fields=fields,
            options=options,
            line=fbs_table.line,
            column=fbs_table.column,
            location=self._location(fbs_table.line, fbs_table.column),
        )

    def _translate_fields(self, fields) -> List[Field]:
        translated: List[Field] = []
        for index, field in enumerate(fields):
            field_type = self._translate_type(field.field_type)
            translated.append(
                Field(
                    name=field.name,
                    field_type=field_type,
                    number=index,
                    tag_id=index,
                    options=dict(field.attributes),
                    line=field.line,
                    column=field.column,
                    location=self._location(field.line, field.column),
                )
            )
        return translated

    def _translate_type(self, fbs_type: FbsTypeRef):
        if isinstance(fbs_type, FbsVectorType):
            element_type = self._translate_type(fbs_type.element_type)
            return ListType(
                element_type, location=self._location(fbs_type.line, fbs_type.column)
            )
        if isinstance(fbs_type, FbsTypeName):
            primitive = self.TYPE_MAPPING.get(fbs_type.name)
            if primitive is not None:
                return PrimitiveType(
                    primitive, location=self._location(fbs_type.line, fbs_type.column)
                )
            return NamedType(
                fbs_type.name, location=self._location(fbs_type.line, fbs_type.column)
            )
        raise ValueError("Unknown FlatBuffers type")
