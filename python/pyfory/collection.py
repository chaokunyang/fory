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

"""
Collection serializers using the Python container interfaces.

ABCs and native subclasses reuse these codecs in both runtime modes. Exact
built-ins select the specialized Cython codecs at the import boundary below.
"""

import struct
import types
from collections import abc

from pyfory.serialization import ENABLE_FORY_CYTHON_SERIALIZATION

if ENABLE_FORY_CYTHON_SERIALIZATION:
    from pyfory.serialization import Serializer, StringSerializer
else:
    from pyfory._serializer import Serializer, StringSerializer
from pyfory.policy import DEFAULT_POLICY
from pyfory.resolver import NOT_NULL_VALUE_FLAG, NULL_FLAG
from pyfory.types import TypeId

COLL_DEFAULT_FLAG = 0b0
COLL_TRACKING_REF = 0b1
COLL_HAS_NULL = 0b10
COLL_IS_DECL_ELEMENT_TYPE = 0b100
COLL_IS_SAME_TYPE = 0b1000
_REFERENCE_BYTES = struct.calcsize("P")
# Lower-bound shallow owner costs for retained Python collection objects. Element, key, and value
# slots are charged separately by count below; these are not Fory wire header sizes.
_LIST_OWNER_BYTES = 4 * _REFERENCE_BYTES
_TUPLE_OWNER_BYTES = 3 * _REFERENCE_BYTES
_SET_OWNER_BYTES = 6 * _REFERENCE_BYTES
_DICT_OWNER_BYTES = 8 * _REFERENCE_BYTES
_UNBACKED_CONTAINER_CHECK_INTERVAL = 1024


def _raise_invalid_map_chunk_size(chunk_size, remaining):
    raise ValueError(f"Invalid map chunk size {chunk_size}, remaining entries {remaining}")


def _ensure_container_allocation(read_context, count):
    required = count - read_context.remaining_unbacked_container_items
    if required > 0:
        read_context.check_readable_bytes(required)


def _settle_unbacked_container_items(read_context, completed, start_index):
    consumed = read_context.get_reader_index() - start_index
    if completed > consumed:
        read_context.reserve_unbacked_container_items(completed - consumed)


def _needs_element_type_info(type_id):
    return type_id in {
        TypeId.STRUCT,
        TypeId.COMPATIBLE_STRUCT,
        TypeId.NAMED_STRUCT,
        TypeId.NAMED_COMPATIBLE_STRUCT,
        TypeId.EXT,
        TypeId.NAMED_EXT,
    }


class CollectionSerializer(Serializer):
    owner_bytes = _LIST_OWNER_BYTES
    _iterate = staticmethod(iter)
    _length = staticmethod(len)

    __slots__ = (
        "elem_serializer",
        "elem_tracking_ref",
        "elem_type",
        "elem_type_info",
    )

    def __init__(self, type_resolver, type_, elem_serializer=None, elem_tracking_ref=None):
        super().__init__(type_resolver, type_)
        self.read_data_always_advances = True
        self.elem_serializer = elem_serializer
        if elem_tracking_ref is not None:
            self.elem_tracking_ref = 1 if elem_tracking_ref else 0
        else:
            self.elem_tracking_ref = -1
        if elem_serializer is None:
            self.elem_type = None
            self.elem_type_info = self.type_resolver.get_type_info(None)
        else:
            self.elem_type = elem_serializer.type_
            self.elem_type_info = self.type_resolver.get_type_info(self.elem_type)
            if elem_tracking_ref is None:
                self.elem_tracking_ref = int(elem_serializer.need_to_write_ref)

    def write_header(self, write_context, value):
        collect_flag = COLL_DEFAULT_FLAG
        elem_type = self.elem_type
        elem_type_info = self.elem_type_info
        has_null = False
        has_same_type = True
        if elem_type is None:
            for item in self._iterate(value):
                if item is None:
                    has_null = True
                    continue
                if elem_type is None:
                    elem_type = type(item)
                elif has_same_type and type(item) is not elem_type:
                    has_same_type = False
            if has_same_type:
                collect_flag |= COLL_IS_SAME_TYPE
                if elem_type is not None:
                    elem_type_info = self.type_resolver.get_type_info(elem_type)
        else:
            collect_flag |= COLL_IS_SAME_TYPE
            if not _needs_element_type_info(elem_type_info.type_id):
                collect_flag |= COLL_IS_DECL_ELEMENT_TYPE
            for item in self._iterate(value):
                if item is None:
                    has_null = True
                    break

        if has_null:
            collect_flag |= COLL_HAS_NULL
        if write_context.track_ref:
            if self.elem_tracking_ref == 1:
                collect_flag |= COLL_TRACKING_REF
            elif self.elem_tracking_ref == -1:
                if not has_same_type or elem_type_info.serializer.need_to_write_ref:
                    collect_flag |= COLL_TRACKING_REF
        write_context.write_var_uint32(self._length(value))
        write_context.write_int8(collect_flag)
        if has_same_type and (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
            self.type_resolver.write_type_info(write_context, elem_type_info)
        return collect_flag, elem_type_info

    def write(self, write_context, value):
        if self._length(value) == 0:
            write_context.write_var_uint32(0)
            return
        collect_flag, typeinfo = self.write_header(write_context, value)
        serializer = (
            self.elem_serializer if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) != 0 and self.elem_serializer is not None else typeinfo.serializer
        )
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_TRACKING_REF) != 0:
                self._write_same_type_ref(write_context, value, serializer)
            elif (collect_flag & COLL_HAS_NULL) == 0:
                self._write_same_type_no_ref(write_context, value, serializer)
            else:
                self._write_same_type_has_null(write_context, value, serializer)
        else:
            self._write_different_types(write_context, value, collect_flag)

    def _write_same_type_no_ref(self, write_context, value, serializer):
        for item in self._iterate(value):
            serializer.write(write_context, item)

    def _write_same_type_has_null(self, write_context, value, serializer):
        for item in self._iterate(value):
            if item is None:
                write_context.write_int8(NULL_FLAG)
            else:
                write_context.write_int8(NOT_NULL_VALUE_FLAG)
                serializer.write(write_context, item)

    def _write_same_type_ref(self, write_context, value, serializer):
        for item in self._iterate(value):
            if not write_context.write_ref_or_null(item):
                serializer.write(write_context, item)

    def _write_different_types(self, write_context, value, collect_flag=0):
        tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
        has_null = (collect_flag & COLL_HAS_NULL) != 0
        if tracking_ref:
            for item in self._iterate(value):
                if not write_context.write_ref_or_null(item):
                    typeinfo = self.type_resolver.get_type_info(type(item))
                    self.type_resolver.write_type_info(write_context, typeinfo)
                    typeinfo.serializer.write(write_context, item)
            return
        if not has_null:
            for item in self._iterate(value):
                typeinfo = self.type_resolver.get_type_info(type(item))
                self.type_resolver.write_type_info(write_context, typeinfo)
                typeinfo.serializer.write(write_context, item)
            return
        for item in self._iterate(value):
            if item is None:
                write_context.write_int8(NULL_FLAG)
            else:
                write_context.write_int8(NOT_NULL_VALUE_FLAG)
                typeinfo = self.type_resolver.get_type_info(type(item))
                self.type_resolver.write_type_info(write_context, typeinfo)
                typeinfo.serializer.write(write_context, item)

    def read(self, read_context):
        length = read_context.read_var_uint32()
        read_context.reserve_graph_memory(self.owner_bytes + length * _REFERENCE_BYTES)
        if length == 0:
            return self.new_instance(read_context, self.type_)
        collect_flag = read_context.read_int8()
        # IMPORTANT: collection readers must obey the ref/null bits written on
        # the wire, not the local Python element annotation or runtime type
        # that may imply a different ref policy. Shared xlang tests
        # intentionally deserialize one ref policy and then serialize another
        # local payload. DO NOT REMOVE this comment.
        serializer = None
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
                typeinfo = self.type_resolver.read_type_info(
                    read_context,
                    self.elem_type_info if self.elem_serializer is not None else None,
                )
                serializer = typeinfo.serializer
            else:
                serializer = self.elem_serializer
        element_read_always_advances = (
            (collect_flag & (COLL_TRACKING_REF | COLL_HAS_NULL)) != 0
            or (collect_flag & COLL_IS_SAME_TYPE) == 0
            or serializer.read_data_always_advances
        )
        if element_read_always_advances:
            read_context.check_readable_bytes(length)
        else:
            _ensure_container_allocation(read_context, length)
        collection_ = self.new_instance(read_context, self.type_)
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_TRACKING_REF) != 0:
                self._read_same_type_ref(read_context, length, collection_, serializer)
            elif (collect_flag & COLL_HAS_NULL) == 0:
                if element_read_always_advances:
                    self._read_same_type_no_ref(read_context, length, collection_, serializer)
                else:
                    self._read_same_type_no_ref_guarded(read_context, length, collection_, serializer)
            else:
                self._read_same_type_has_null(read_context, length, collection_, serializer)
        else:
            self._read_different_types(read_context, length, collection_, collect_flag)
        return collection_

    def new_instance(self, read_context, type_):
        raise NotImplementedError

    def _add_element(self, collection_, element):
        raise NotImplementedError

    def _read_same_type_no_ref(self, read_context, length, collection_, serializer):
        read_context.increase_depth()
        for _ in range(length):
            self._add_element(collection_, read_context.read_no_ref(serializer=serializer))
        read_context.decrease_depth()

    def _read_same_type_no_ref_guarded(self, read_context, length, collection_, serializer):
        read_context.increase_depth()
        window_start = read_context.get_reader_index()
        window_items = 0
        for _ in range(length):
            self._add_element(collection_, read_context.read_no_ref(serializer=serializer))
            window_items += 1
            if window_items == _UNBACKED_CONTAINER_CHECK_INTERVAL:
                _settle_unbacked_container_items(read_context, window_items, window_start)
                window_start = read_context.get_reader_index()
                window_items = 0
        if window_items:
            _settle_unbacked_container_items(read_context, window_items, window_start)
        read_context.decrease_depth()

    def _read_same_type_has_null(self, read_context, length, collection_, serializer):
        read_context.increase_depth()
        for _ in range(length):
            if read_context.read_int8() == NULL_FLAG:
                self._add_element(collection_, None)
            else:
                self._add_element(collection_, read_context.read_no_ref(serializer=serializer))
        read_context.decrease_depth()

    def _read_same_type_ref(self, read_context, length, collection_, serializer):
        read_context.increase_depth()
        for _ in range(length):
            ref_id = read_context.try_preserve_ref_id()
            if ref_id < NOT_NULL_VALUE_FLAG:
                obj = read_context.get_read_ref()
            else:
                obj = serializer.read(read_context)
                read_context.set_read_ref(ref_id, obj)
            self._add_element(collection_, obj)
        read_context.decrease_depth()

    def _read_different_types(self, read_context, length, collection_, collect_flag):
        read_context.increase_depth()
        tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
        has_null = (collect_flag & COLL_HAS_NULL) != 0
        if tracking_ref:
            for _ in range(length):
                self._add_element(collection_, get_next_element(read_context))
            read_context.decrease_depth()
            return
        if not has_null:
            for _ in range(length):
                typeinfo = self.type_resolver.read_type_info(read_context)
                elem = None if typeinfo is None else read_context.read_no_ref(serializer=typeinfo.serializer)
                self._add_element(collection_, elem)
            read_context.decrease_depth()
            return
        for _ in range(length):
            head_flag = read_context.read_int8()
            if head_flag == NULL_FLAG:
                elem = None
            else:
                typeinfo = self.type_resolver.read_type_info(read_context)
                elem = None if typeinfo is None else read_context.read_no_ref(serializer=typeinfo.serializer)
            self._add_element(collection_, elem)
        read_context.decrease_depth()


class SequenceSerializer(CollectionSerializer):
    def new_instance(self, read_context, type_):
        instance = []
        read_context.reference(instance)
        return instance

    def _add_element(self, collection_, element):
        collection_.append(element)


class TupleSerializer(CollectionSerializer):
    owner_bytes = _TUPLE_OWNER_BYTES

    def new_instance(self, read_context, type_):
        return []

    def _add_element(self, collection_, element):
        collection_.append(element)

    def read(self, read_context):
        return tuple(super().read(read_context))


class StringArraySerializer(SequenceSerializer):
    def __init__(self, type_resolver, type_):
        super().__init__(type_resolver, type_, StringSerializer(type_resolver, str))


class SetCollectionSerializer(CollectionSerializer):
    owner_bytes = _SET_OWNER_BYTES

    def new_instance(self, read_context, type_):
        instance = set()
        read_context.reference(instance)
        return instance

    def _add_element(self, collection_, element):
        collection_.add(element)


def get_next_element(read_context):
    ref_id = read_context.try_preserve_ref_id()
    if ref_id < NOT_NULL_VALUE_FLAG:
        return read_context.get_read_ref()
    typeinfo = read_context.type_resolver.read_type_info(read_context)
    obj = typeinfo.serializer.read(read_context)
    read_context.set_read_ref(ref_id, obj)
    return obj


MAX_CHUNK_SIZE = 255
TRACKING_KEY_REF = 0b1
KEY_HAS_NULL = 0b10
KEY_DECL_TYPE = 0b100
TRACKING_VALUE_REF = 0b1000
VALUE_HAS_NULL = 0b10000
VALUE_DECL_TYPE = 0b100000
KV_NULL = KEY_HAS_NULL | VALUE_HAS_NULL
NULL_KEY_VALUE_DECL_TYPE = KEY_HAS_NULL | VALUE_DECL_TYPE
NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF = KEY_HAS_NULL | VALUE_DECL_TYPE | TRACKING_VALUE_REF
NULL_VALUE_KEY_DECL_TYPE = VALUE_HAS_NULL | KEY_DECL_TYPE
NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF = VALUE_HAS_NULL | KEY_DECL_TYPE | TRACKING_KEY_REF


class MappingSerializer(Serializer):
    _length = staticmethod(len)

    @staticmethod
    def _items(value):
        return value.items()

    def __init__(
        self,
        type_resolver,
        type_,
        key_serializer=None,
        value_serializer=None,
        key_tracking_ref=None,
        value_tracking_ref=None,
        key_write_type_info=False,
        value_write_type_info=False,
    ):
        super().__init__(type_resolver, type_)
        self.read_data_always_advances = True
        self.key_serializer = key_serializer
        self.value_serializer = value_serializer
        # Compatible evolving child schemas need dynamic write framing, while this
        # reader must still accept declared chunks from an exact peer.
        self.key_write_serializer = None if key_write_type_info else key_serializer
        self.value_write_serializer = None if value_write_type_info else value_serializer
        self.key_tracking_ref = False
        self.value_tracking_ref = False
        if key_serializer is not None:
            self.key_tracking_ref = bool(key_serializer.need_to_write_ref)
            if key_tracking_ref is not None:
                self.key_tracking_ref = bool(key_tracking_ref) and type_resolver.track_ref
        if value_serializer is not None:
            self.value_tracking_ref = bool(value_serializer.need_to_write_ref)
            if value_tracking_ref is not None:
                self.value_tracking_ref = bool(value_tracking_ref) and type_resolver.track_ref

    def write(self, write_context, obj):
        length = self._length(obj)
        write_context.write_var_uint32(length)
        if length == 0:
            return
        type_resolver = self.type_resolver
        key_serializer = self.key_write_serializer
        value_serializer = self.value_write_serializer

        items_iter = iter(self._items(obj))
        key, value = next(items_iter)
        has_next = True
        while has_next:
            while True:
                if key is not None:
                    if value is not None:
                        break
                    if key_serializer is not None:
                        key_write_ref = self.key_tracking_ref
                        if key_write_ref:
                            write_context.write_int8(NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF)
                            if not write_context.write_ref_or_null(key):
                                self._write_obj(key_serializer, write_context, key)
                        else:
                            write_context.write_int8(NULL_VALUE_KEY_DECL_TYPE)
                            self._write_obj(key_serializer, write_context, key)
                    else:
                        write_context.write_int8(VALUE_HAS_NULL | TRACKING_KEY_REF)
                        write_context.write_ref(key)
                else:
                    if value is not None:
                        if value_serializer is not None:
                            value_write_ref = self.value_tracking_ref
                            if value_write_ref:
                                write_context.write_int8(NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF)
                                if not write_context.write_ref_or_null(value):
                                    value_serializer.write(write_context, value)
                            else:
                                write_context.write_int8(NULL_KEY_VALUE_DECL_TYPE)
                                value_serializer.write(write_context, value)
                        else:
                            write_context.write_int8(KEY_HAS_NULL | TRACKING_VALUE_REF)
                            write_context.write_ref(value)
                    else:
                        write_context.write_int8(KV_NULL)
                try:
                    key, value = next(items_iter)
                except StopIteration:
                    has_next = False
                    break

            if not has_next:
                break

            key_cls = type(key)
            value_cls = type(value)
            write_context.enter_flush_barrier()
            write_context.write_int16(-1)
            chunk_size_offset = write_context.get_writer_index() - 1
            chunk_header = 0

            if key_serializer is not None:
                chunk_header |= KEY_DECL_TYPE
            else:
                key_type_info = type_resolver.get_type_info(key_cls)
                type_resolver.write_type_info(write_context, key_type_info)
                key_serializer = key_type_info.serializer

            if value_serializer is not None:
                chunk_header |= VALUE_DECL_TYPE
            else:
                value_type_info = type_resolver.get_type_info(value_cls)
                type_resolver.write_type_info(write_context, value_type_info)
                value_serializer = value_type_info.serializer

            key_write_ref = self.key_tracking_ref if self.key_serializer is not None else bool(key_serializer.need_to_write_ref)
            value_write_ref = self.value_tracking_ref if self.value_serializer is not None else bool(value_serializer.need_to_write_ref)
            if key_write_ref:
                chunk_header |= TRACKING_KEY_REF
            if value_write_ref:
                chunk_header |= TRACKING_VALUE_REF

            write_context.put_uint8(chunk_size_offset - 1, chunk_header)
            chunk_size = 0

            while chunk_size < MAX_CHUNK_SIZE:
                if key is None or value is None or type(key) is not key_cls or type(value) is not value_cls:
                    break
                if not key_write_ref or not write_context.write_ref_or_null(key):
                    self._write_obj(key_serializer, write_context, key)
                if not value_write_ref or not write_context.write_ref_or_null(value):
                    self._write_obj(value_serializer, write_context, value)
                chunk_size += 1
                try:
                    key, value = next(items_iter)
                except StopIteration:
                    has_next = False
                    break

            key_serializer = self.key_write_serializer
            value_serializer = self.value_write_serializer
            write_context.put_uint8(chunk_size_offset, chunk_size)
            write_context.exit_flush_barrier()
            write_context.try_flush()

    def read(self, read_context):
        size = read_context.read_var_uint32()
        read_context.reserve_graph_memory(_DICT_OWNER_BYTES + size * 2 * _REFERENCE_BYTES)
        if size:
            if (
                self.key_write_serializer is not None
                and self.key_write_serializer.read_data_always_advances
                or self.value_write_serializer is not None
                and self.value_write_serializer.read_data_always_advances
            ):
                read_context.check_readable_bytes(size)
            else:
                _ensure_container_allocation(read_context, size)
        map_ = self.new_instance(read_context, self.type_)
        chunk_header = read_context.read_uint8() if size != 0 else 0
        key_serializer = self.key_serializer
        value_serializer = self.value_serializer
        read_context.increase_depth()
        while size > 0:
            while True:
                key_has_null = (chunk_header & KEY_HAS_NULL) != 0
                value_has_null = (chunk_header & VALUE_HAS_NULL) != 0
                if not key_has_null and not value_has_null:
                    break
                if not key_has_null:
                    track_key_ref = (chunk_header & TRACKING_KEY_REF) != 0
                    if (chunk_header & KEY_DECL_TYPE) != 0:
                        if track_key_ref:
                            ref_id = read_context.try_preserve_ref_id()
                            if ref_id < NOT_NULL_VALUE_FLAG:
                                key = read_context.get_read_ref()
                            else:
                                key = self._read_obj(key_serializer, read_context)
                                read_context.set_read_ref(ref_id, key)
                        else:
                            key = self._read_obj_no_ref(key_serializer, read_context)
                    else:
                        key = read_context.read_ref()
                    dict.__setitem__(map_, key, None)
                elif not value_has_null:
                    track_value_ref = (chunk_header & TRACKING_VALUE_REF) != 0
                    if (chunk_header & VALUE_DECL_TYPE) != 0:
                        if track_value_ref:
                            ref_id = read_context.try_preserve_ref_id()
                            if ref_id < NOT_NULL_VALUE_FLAG:
                                value = read_context.get_read_ref()
                            else:
                                value = self._read_obj(value_serializer, read_context)
                                read_context.set_read_ref(ref_id, value)
                        else:
                            value = self._read_obj_no_ref(value_serializer, read_context)
                    else:
                        value = read_context.read_ref()
                    dict.__setitem__(map_, None, value)
                else:
                    dict.__setitem__(map_, None, None)
                size -= 1
                if size == 0:
                    read_context.decrease_depth()
                    return map_
                chunk_header = read_context.read_uint8()

            # IMPORTANT: map readers must obey the sender-written key/value ref
            # bits in the wire header. Local Python serializer choices must not
            # override that decision while reading. Shared xlang tests
            # intentionally deserialize one ref policy and then serialize
            # another local payload. DO NOT REMOVE this comment.
            track_key_ref = (chunk_header & TRACKING_KEY_REF) != 0
            track_value_ref = (chunk_header & TRACKING_VALUE_REF) != 0
            key_is_declared_type = (chunk_header & KEY_DECL_TYPE) != 0
            value_is_declared_type = (chunk_header & VALUE_DECL_TYPE) != 0
            chunk_size = read_context.read_uint8()
            if chunk_size == 0 or chunk_size > size:
                _raise_invalid_map_chunk_size(chunk_size, size)
            if not key_is_declared_type:
                key_serializer = self.type_resolver.read_type_info(read_context).serializer
            if not value_is_declared_type:
                value_serializer = self.type_resolver.read_type_info(read_context).serializer
            entry_read_always_advances = (
                track_key_ref or track_value_ref or key_serializer.read_data_always_advances or value_serializer.read_data_always_advances
            )
            if not entry_read_always_advances:
                chunk_start = read_context.get_reader_index()
            for _ in range(chunk_size):
                if track_key_ref:
                    ref_id = read_context.try_preserve_ref_id()
                    if ref_id < NOT_NULL_VALUE_FLAG:
                        key = read_context.get_read_ref()
                    else:
                        key = self._read_obj(key_serializer, read_context)
                        read_context.set_read_ref(ref_id, key)
                else:
                    key = self._read_obj_no_ref(key_serializer, read_context)
                if track_value_ref:
                    ref_id = read_context.try_preserve_ref_id()
                    if ref_id < NOT_NULL_VALUE_FLAG:
                        value = read_context.get_read_ref()
                    else:
                        value = self._read_obj(value_serializer, read_context)
                        read_context.set_read_ref(ref_id, value)
                else:
                    value = self._read_obj_no_ref(value_serializer, read_context)
                dict.__setitem__(map_, key, value)
                size -= 1
            if not entry_read_always_advances:
                _settle_unbacked_container_items(read_context, chunk_size, chunk_start)
            if size != 0:
                chunk_header = read_context.read_uint8()
        read_context.decrease_depth()
        return map_

    def new_instance(self, read_context, type_):
        instance = {}
        read_context.reference(instance)
        return instance

    def _write_obj(self, serializer, write_context, obj):
        serializer.write(write_context, obj)

    def _read_obj(self, serializer, read_context):
        return serializer.read(read_context)

    def _read_obj_no_ref(self, serializer, read_context):
        return read_context.read_no_ref(serializer=serializer)


class _ContainerSubclassSerializer:
    """Restore native container storage and instance state into one reference owner."""

    def __init__(self, type_resolver, type_):
        super().__init__(type_resolver, type_)
        self._getstate = getattr(type_, "__getstate__", None)
        self._setstate = getattr(type_, "__setstate__", None)
        self._state_hook = self._setstate is not None or self._getstate is not getattr(self._base_type, "__getstate__", None)
        self._slots = tuple(
            (name, descriptor)
            for cls in reversed(type_.__mro__)
            for name, descriptor in vars(cls).items()
            if isinstance(descriptor, types.MemberDescriptorType)
        )

    def new_instance(self, read_context, type_):
        read_context.policy.authorize_instantiation(type_)
        read_context.reserve_graph_memory(max(0, type_.__basicsize__ - self._base_type.__basicsize__))
        instance = self._base_type.__new__(type_)
        # Publish the final subclass before reading its contents or state. A
        # temporary built-in followed by conversion would break cycles.
        read_context.reference(instance)
        return instance

    def write(self, write_context, value):
        super().write(write_context, value)
        if self._state_hook:
            if self._getstate is not None:
                state = self._getstate(value)
            else:
                # Python before 3.11 has no object.__getstate__ implementation.
                state = getattr(value, "__dict__", None)
                slots = {}
                for name, descriptor in self._slots:
                    try:
                        slots[name] = descriptor.__get__(value)
                    except AttributeError:
                        pass
                if slots:
                    state = (state, slots)
            write_context.write_ref(state)
            return
        write_context.write_ref(getattr(value, "__dict__", None))
        for _name, descriptor in self._slots:
            try:
                field_value = descriptor.__get__(value)
            except AttributeError:
                write_context.write_bool(False)
            else:
                write_context.write_bool(True)
                write_context.write_ref(field_value)

    def read(self, read_context):
        instance = super().read(read_context)
        state = read_context.read_ref()
        if self._state_hook:
            if state is not None:
                read_context.policy.intercept_setstate(instance, state)
                if self._setstate is not None:
                    self._setstate(instance, state)
                else:
                    slots = None
                    if isinstance(state, tuple):
                        state, slots = state
                    if state is not None:
                        instance.__dict__ = state
                    if slots is not None:
                        for name, value in slots.items():
                            setattr(instance, name, value)
            return instance
        if state is not None:
            read_context.policy.intercept_setstate(instance, state)
            instance.__dict__ = state
        for name, descriptor in self._slots:
            if read_context.read_bool():
                field_value = read_context.read_ref()
                if read_context.policy is not DEFAULT_POLICY:
                    state = {name: field_value}
                    read_context.policy.intercept_setstate(instance, state)
                    field_value = state[name]
                descriptor.__set__(instance, field_value)
        return instance


class ListSubclassSerializer(_ContainerSubclassSerializer, SequenceSerializer):
    # Native reconstruction restores base storage without reapplying overridden
    # iteration or mutation methods; those can depend on not-yet-restored state.
    _base_type = list
    _iterate = staticmethod(list.__iter__)
    _length = staticmethod(list.__len__)

    def _add_element(self, collection_, element):
        list.append(collection_, element)


class SetSubclassSerializer(_ContainerSubclassSerializer, SetCollectionSerializer):
    _base_type = set
    _iterate = staticmethod(set.__iter__)
    _length = staticmethod(set.__len__)

    def _add_element(self, collection_, element):
        set.add(collection_, element)


class DictSubclassSerializer(_ContainerSubclassSerializer, MappingSerializer):
    _base_type = dict
    _length = staticmethod(dict.__len__)
    _items = staticmethod(dict.items)


def _create_collection_serializer(type_resolver, cls):
    """Select a data-only xlang codec on the resolver's type-cache miss path."""
    if not isinstance(cls, type):
        return None
    if issubclass(cls, abc.Mapping):
        return MappingSerializer(type_resolver, dict)
    if issubclass(cls, abc.Set):
        return SetCollectionSerializer(type_resolver, set)
    if issubclass(cls, abc.Sequence) and not issubclass(cls, (str, bytes, bytearray)):
        return SequenceSerializer(type_resolver, list)
    return None


def _create_container_subclass_serializer(type_resolver, cls):
    """Select native built-in storage only when no custom reconstruction is required."""
    if not isinstance(cls, type):
        return None
    for base, serializer in ((list, ListSubclassSerializer), (set, SetSubclassSerializer), (dict, DictSubclassSerializer)):
        if cls is base or not issubclass(cls, base):
            continue
        for name in ("__reduce__", "__reduce_ex__"):
            if getattr(cls, name, None) is not getattr(base, name, None):
                return None
        if cls.__new__ is not base.__new__ or hasattr(cls, "__getnewargs__") or hasattr(cls, "__getnewargs_ex__"):
            raise TypeError(f"{cls} requires an explicit serializer or reduce hook for its custom construction")
        return serializer(type_resolver, cls)
    return None


if ENABLE_FORY_CYTHON_SERIALIZATION:
    from pyfory.serialization import (
        CollectionSerializer as CythonCollectionSerializer,
        ListSerializer as CythonListSerializer,
        TupleSerializer as CythonTupleSerializer,
        StringArraySerializer as CythonStringArraySerializer,
        SetSerializer as CythonSetSerializer,
        MapSerializer as CythonMapSerializer,
    )

    CollectionSerializer = CythonCollectionSerializer
    ListSerializer = CythonListSerializer
    TupleSerializer = CythonTupleSerializer
    StringArraySerializer = CythonStringArraySerializer
    SetSerializer = CythonSetSerializer
    MapSerializer = CythonMapSerializer
    SubMapSerializer = CythonMapSerializer
else:
    ListSerializer = SequenceSerializer
    SetSerializer = SetCollectionSerializer
    MapSerializer = MappingSerializer
    SubMapSerializer = MappingSerializer
