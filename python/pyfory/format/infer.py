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

import datetime
import typing

from typing import Optional
from pyfory.type import TypeVisitor, infer_field
from pyfory.format._format import (
    Schema,
    DataType,
    TypeId,
    boolean,
    int64,
    float64,
    utf8,
    binary,
    date32,
    timestamp,
    list_,
    map_,
    struct,
    field,
    schema,
)

__type_map__ = {}
__schemas__ = {}  # ensure `id(schema)` doesn't get duplicate.


def get_cls_by_schema(schema):
    id_ = id(schema)
    if id_ not in __type_map__:
        # For Fory Schema, we don't have metadata support yet
        # Try to get class name from schema if available
        cls_name = ""
        if hasattr(schema, "metadata") and schema.metadata:
            cls_name = schema.metadata.get(b"cls", b"").decode()
        if cls_name:
            import importlib

            module_name, class_name = cls_name.rsplit(".", 1)
            mod = importlib.import_module(module_name)
            cls_ = getattr(mod, class_name)
        else:
            from pyfory.type import record_class_factory

            cls_ = record_class_factory("Record" + str(id(schema)), [schema.field(i).name for i in range(schema.num_fields)])
        __type_map__[id_] = cls_
        __schemas__[id_] = schema
    return __type_map__[id_]


def remove_schema(schema):
    __schemas__.pop(id(schema))


def reset():
    __type_map__.clear()
    __schemas__.clear()


_supported_types = {
    bool,
    int,
    float,
    str,
    bytes,
    typing.List,
    typing.Dict,
}
_supported_types_str = [f"{t.__module__}.{getattr(t, '__name__', t)}" for t in _supported_types]
_supported_types_mapping = {
    bool: boolean,
    int: int64,
    float: float64,
    str: utf8,
    bytes: binary,
    list: list_,
    dict: map_,
    typing.List: list_,
    typing.Dict: map_,
    datetime.date: date32,
    datetime.datetime: timestamp,
}


def infer_schema(clz, types_path=None) -> Schema:
    types_path = list(types_path or [])
    type_hints = typing.get_type_hints(clz)
    keys = sorted(type_hints.keys())
    fields = [
        infer_field(
            field_name,
            type_hints[field_name],
            ForyTypeVisitor(),
            types_path=types_path,
        )
        for field_name in keys
    ]
    # TODO: Add metadata support to Fory Schema
    return schema(fields)


class ForyTypeVisitor(TypeVisitor):
    def visit_list(self, field_name, elem_type, types_path=None):
        # Infer type recursively for type such as List[Dict[str, str]]
        elem_field = infer_field("item", elem_type, self, types_path=types_path)
        return field(field_name, list_(elem_field.type))

    def visit_set(self, field_name, elem_type, types_path=None):
        # Infer type recursively for type such as Set[Dict[str, str]]
        elem_field = infer_field("item", elem_type, self, types_path=types_path)
        return field(field_name, list_(elem_field.type))

    def visit_dict(self, field_name, key_type, value_type, types_path=None):
        # Infer type recursively for type such as Dict[str, Dict[str, str]]
        key_field = infer_field("key", key_type, self, types_path=types_path)
        value_field = infer_field("value", value_type, self, types_path=types_path)
        return field(field_name, map_(key_field.type, value_field.type))

    def visit_customized(self, field_name, type_, types_path=None):
        # type_ is a pojo
        pojo_schema = infer_schema(type_)
        fields = [pojo_schema.field(i) for i in range(pojo_schema.num_fields)]
        # TODO: Add metadata support
        return field(field_name, struct(fields))

    def visit_other(self, field_name, type_, types_path=None):
        if type_ not in _supported_types_mapping:
            raise TypeError(
                f"Type {type_} not supported, currently only compositions of {_supported_types_str} are supported. types_path is {types_path}"
            )
        fory_type_func = _supported_types_mapping.get(type_)
        return field(field_name, fory_type_func())


def infer_data_type(clz) -> Optional[DataType]:
    try:
        return infer_field("", clz, ForyTypeVisitor()).type
    except TypeError:
        return None


def get_type_id(clz) -> Optional[int]:
    type_ = infer_data_type(clz)
    if type_:
        return type_.id
    else:
        return None


def compute_schema_hash(schema: Schema):
    hash_ = 17
    for i in range(schema.num_fields):
        hash_ = _compute_hash(hash_, schema.field(i).type)
    return hash_


def _compute_hash(hash_: int, type_: DataType):
    while True:
        h = hash_ * 31 + int(type_.id)
        if h > 2**63 - 1:
            hash_ = hash_ >> 2
        else:
            hash_ = h
            break
    types = []
    type_id = type_.id
    if type_id == TypeId.LIST:
        list_type = type_
        types.append(list_type.value_type)
    elif type_id == TypeId.MAP:
        map_type = type_
        types.append(map_type.key_type)
        types.append(map_type.item_type)
    elif type_id == TypeId.STRUCT:
        for i in range(type_.num_fields):
            types.append(type_.field(i).type)
    else:
        assert type_.num_fields == 0, f"field type should not be nested, but got type {type_}."

    for t in types:
        hash_ = _compute_hash(hash_, t)
    return hash_


def to_arrow_schema(fory_schema: Schema):
    """Convert a Fory Schema to an Arrow Schema.

    This is for compatibility with ArrowWriter which requires Arrow schemas.

    Args:
        fory_schema: A Fory Schema object.

    Returns:
        An Arrow Schema object with the same structure.

    Raises:
        ImportError: If pyarrow is not available.
    """
    try:
        import pyarrow as pa
    except ImportError:
        raise ImportError("pyarrow is required for Arrow schema conversion")

    def convert_type(fory_type: DataType):
        type_id = fory_type.id
        if type_id == TypeId.BOOL:
            return pa.bool_()
        elif type_id == TypeId.INT8:
            return pa.int8()
        elif type_id == TypeId.INT16:
            return pa.int16()
        elif type_id == TypeId.INT32:
            return pa.int32()
        elif type_id == TypeId.INT64:
            return pa.int64()
        elif type_id == TypeId.FLOAT32:
            return pa.float32()
        elif type_id == TypeId.FLOAT64:
            return pa.float64()
        elif type_id == TypeId.STRING:
            return pa.string()
        elif type_id == TypeId.BINARY:
            return pa.binary()
        elif type_id == TypeId.LOCAL_DATE:
            return pa.date32()
        elif type_id == TypeId.TIMESTAMP:
            return pa.timestamp("us")
        elif type_id == TypeId.LIST:
            return pa.list_(convert_type(fory_type.value_type))
        elif type_id == TypeId.MAP:
            return pa.map_(convert_type(fory_type.key_type), convert_type(fory_type.item_type))
        elif type_id == TypeId.STRUCT:
            fields = []
            for i in range(fory_type.num_fields):
                f = fory_type.field(i)
                fields.append(pa.field(f.name, convert_type(f.type), nullable=f.nullable))
            return pa.struct(fields)
        else:
            raise TypeError(f"Unsupported Fory type for Arrow conversion: {fory_type}")

    arrow_fields = []
    for i in range(fory_schema.num_fields):
        f = fory_schema.field(i)
        arrow_fields.append(pa.field(f.name, convert_type(f.type), nullable=f.nullable))
    return pa.schema(arrow_fields)
