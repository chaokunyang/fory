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
High-performance Cython collection serializers.

These stay separate from the pure-Python collection serializers because list/tuple
bulk IO, primitive collection fast paths, and chunked map encoding all benefit
from direct Cython access to the active WriteContext / ReadContext.
"""

cdef int8_t COLL_DEFAULT_FLAG = 0b0
cdef int8_t COLL_TRACKING_REF = 0b1
cdef int8_t COLL_HAS_NULL = 0b10
cdef int8_t COLL_IS_DECL_ELEMENT_TYPE = 0b100
cdef int8_t COLL_IS_SAME_TYPE = 0b1000

cdef int MAX_CHUNK_SIZE = 255
cdef int8_t TRACKING_KEY_REF = 0b1
cdef int8_t KEY_HAS_NULL = 0b10
cdef int8_t KEY_DECL_TYPE = 0b100
cdef int8_t TRACKING_VALUE_REF = 0b1000
cdef int8_t VALUE_HAS_NULL = 0b10000
cdef int8_t VALUE_DECL_TYPE = 0b100000
cdef int8_t KV_NULL = KEY_HAS_NULL | VALUE_HAS_NULL
cdef int8_t NULL_KEY_VALUE_DECL_TYPE = KEY_HAS_NULL | VALUE_DECL_TYPE
cdef int8_t NULL_KEY_VALUE_DECL_TYPE_TRACKING_REF = KEY_HAS_NULL | VALUE_DECL_TYPE | TRACKING_VALUE_REF
cdef int8_t NULL_VALUE_KEY_DECL_TYPE = VALUE_HAS_NULL | KEY_DECL_TYPE
cdef int8_t NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF = VALUE_HAS_NULL | KEY_DECL_TYPE | TRACKING_KEY_REF


cdef class CollectionSerializer(Serializer):
    # Element serializers may be Cython or pure-Python implementations.
    cdef Serializer elem_serializer
    cdef int8_t elem_tracking_ref
    cdef object elem_type
    cdef TypeInfo elem_type_info

    def __init__(self, type_resolver, type_, elem_serializer=None, elem_tracking_ref=None):
        super().__init__(type_resolver, type_)
        self.elem_serializer = elem_serializer
        if elem_serializer is None:
            self.elem_type = None
            self.elem_type_info = self.type_resolver.get_type_info(None)
            self.elem_tracking_ref = -1
        else:
            self.elem_type = elem_serializer.type_
            self.elem_type_info = self.type_resolver.get_type_info(self.elem_type)
            self.elem_tracking_ref = <int8_t>elem_serializer.need_to_write_ref
            if elem_tracking_ref is not None:
                self.elem_tracking_ref = <int8_t>(1 if elem_tracking_ref else 0)

    cdef inline TypeInfo write_header(self, WriteContext write_context, value, int8_t *collect_flag_ptr):
        cdef int8_t collect_flag = COLL_DEFAULT_FLAG
        cdef object elem_type = self.elem_type
        cdef TypeInfo elem_type_info = self.elem_type_info
        cdef bint has_null = False
        cdef bint has_same_type = True
        cdef PyObject **items = fory_sequence_get_items(value)
        cdef PyObject *item
        cdef PyTypeObject *first_type = NULL
        cdef Py_ssize_t i
        cdef Py_ssize_t size = Py_SIZE(value)

        if elem_type is None:
            if items != NULL:
                for i in range(size):
                    item = items[i]
                    if item == <PyObject *>None:
                        has_null = True
                        continue
                    if first_type == NULL:
                        first_type = item.ob_type
                    elif has_same_type and item.ob_type != first_type:
                        has_same_type = False
                if first_type != NULL:
                    elem_type = <object>first_type
            else:
                for item_obj in value:
                    if item_obj is None:
                        has_null = True
                        continue
                    if elem_type is None:
                        elem_type = type(item_obj)
                    elif has_same_type and type(item_obj) is not elem_type:
                        has_same_type = False
            if has_same_type:
                collect_flag |= COLL_IS_SAME_TYPE
                if elem_type is not None:
                    elem_type_info = self.type_resolver.get_type_info(elem_type)
        else:
            collect_flag |= COLL_IS_DECL_ELEMENT_TYPE | COLL_IS_SAME_TYPE
            if items != NULL:
                for i in range(size):
                    if items[i] == <PyObject *>None:
                        has_null = True
                        break
            else:
                for item_obj in value:
                    if item_obj is None:
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

        write_context.write_var_uint32(size)
        write_context.write_int8(collect_flag)
        if has_same_type and (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
            self.type_resolver.write_type_info(write_context, elem_type_info)
        collect_flag_ptr[0] = collect_flag
        return elem_type_info

    cpdef write(self, WriteContext write_context, value):
        cdef int8_t collect_flag
        cdef TypeInfo elem_type_info
        cdef Serializer elem_serializer

        if len(value) == 0:
            write_context.write_var_uint32(0)
            return

        elem_type_info = self.write_header(write_context, value, &collect_flag)
        elem_serializer = self.elem_serializer
        if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0 or elem_serializer is None:
            elem_serializer = elem_type_info.serializer

        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_HAS_NULL) == 0:
                if Fory_CanUsePrimitiveCollectionFastpath(elem_type_info.type_id):
                    self._write_primitive_fastpath(write_context, value, elem_type_info.type_id)
                elif (collect_flag & COLL_TRACKING_REF) == 0:
                    self._write_same_type_no_ref(write_context, value, elem_serializer)
                else:
                    self._write_same_type_ref(write_context, value, elem_serializer)
            elif (collect_flag & COLL_TRACKING_REF) != 0:
                self._write_same_type_ref(write_context, value, elem_serializer)
            else:
                self._write_same_type_has_null(write_context, value, elem_serializer)
            return

        self._write_different_types(write_context, value, collect_flag)

    cdef inline void _write_primitive_fastpath(self, WriteContext write_context, value, uint8_t type_id):
        Fory_PyPrimitiveCollectionWriteToBuffer(value, write_context.c_buffer, type_id)

    cdef inline void _read_primitive_fastpath(self, ReadContext read_context, int64_t len_, object collection_, uint8_t type_id):
        Fory_PyPrimitiveCollectionReadFromBuffer(collection_, read_context.c_buffer, len_, type_id)

    cpdef _write_same_type_no_ref(self, WriteContext write_context, value, Serializer serializer):
        cdef PyObject **items = fory_sequence_get_items(value)
        cdef Py_ssize_t i
        cdef Py_ssize_t size
        if items != NULL:
            size = Py_SIZE(value)
            for i in range(size):
                serializer.write(write_context, <object>items[i])
            return
        for item in value:
            serializer.write(write_context, item)

    cpdef _read_same_type_no_ref(self, ReadContext read_context, int64_t len_, object collection_, Serializer serializer):
        cdef PyObject **items = fory_sequence_get_items(collection_)
        cdef bint is_list = type(collection_) is list
        cdef int64_t i
        cdef object obj
        read_context.increase_depth()
        try:
            if items != NULL:
                for i in range(len_):
                    obj = read_context.read_non_ref(serializer)
                    Py_INCREF(obj)
                    if is_list:
                        PyList_SET_ITEM(collection_, i, obj)
                    else:
                        PyTuple_SET_ITEM(collection_, i, obj)
                return
            for i in range(len_):
                self._add_element(collection_, i, read_context.read_non_ref(serializer))
        finally:
            read_context.decrease_depth()

    cpdef _write_same_type_has_null(self, WriteContext write_context, value, Serializer serializer):
        cdef PyObject **items = fory_sequence_get_items(value)
        cdef PyObject *item
        cdef Py_ssize_t i
        cdef Py_ssize_t size
        if items != NULL:
            size = Py_SIZE(value)
            for i in range(size):
                item = items[i]
                if item == <PyObject *>None:
                    write_context.write_int8(NULL_FLAG)
                else:
                    write_context.write_int8(NOT_NULL_VALUE_FLAG)
                    serializer.write(write_context, <object>item)
            return
        for item_obj in value:
            if item_obj is None:
                write_context.write_int8(NULL_FLAG)
            else:
                write_context.write_int8(NOT_NULL_VALUE_FLAG)
                serializer.write(write_context, item_obj)

    cpdef _read_same_type_has_null(self, ReadContext read_context, int64_t len_, object collection_, Serializer serializer):
        cdef int8_t flag
        cdef PyObject **items = fory_sequence_get_items(collection_)
        cdef bint is_list = type(collection_) is list
        cdef int64_t i
        cdef object obj
        read_context.increase_depth()
        try:
            if items != NULL:
                for i in range(len_):
                    flag = read_context.read_int8()
                    if flag == NULL_FLAG:
                        obj = None
                    else:
                        obj = read_context.read_non_ref(serializer)
                    Py_INCREF(obj)
                    if is_list:
                        PyList_SET_ITEM(collection_, i, obj)
                    else:
                        PyTuple_SET_ITEM(collection_, i, obj)
                return
            for i in range(len_):
                flag = read_context.read_int8()
                if flag == NULL_FLAG:
                    self._add_element(collection_, i, None)
                else:
                    self._add_element(collection_, i, read_context.read_non_ref(serializer))
        finally:
            read_context.decrease_depth()

    cpdef _write_same_type_ref(self, WriteContext write_context, value, Serializer serializer):
        cdef RefWriter ref_writer = write_context.ref_writer
        cdef PyObject **items = fory_sequence_get_items(value)
        cdef Py_ssize_t i
        cdef Py_ssize_t size
        if items != NULL:
            size = Py_SIZE(value)
            for i in range(size):
                item_obj = <object>items[i]
                if not ref_writer.write_ref_or_null(write_context, item_obj):
                    serializer.write(write_context, item_obj)
            return
        for item_obj in value:
            if not ref_writer.write_ref_or_null(write_context, item_obj):
                serializer.write(write_context, item_obj)

    cpdef _read_same_type_ref(self, ReadContext read_context, int64_t len_, object collection_, Serializer serializer):
        cdef RefReader ref_reader = read_context.ref_reader
        cdef PyObject **items = fory_sequence_get_items(collection_)
        cdef bint is_list = type(collection_) is list
        cdef int64_t i
        read_context.increase_depth()
        try:
            if items != NULL:
                for i in range(len_):
                    ref_id = ref_reader.try_preserve_ref_id(read_context)
                    if ref_id < NOT_NULL_VALUE_FLAG:
                        obj = ref_reader.get_read_ref()
                    else:
                        obj = serializer.read(read_context)
                        ref_reader.set_read_ref(ref_id, obj)
                    Py_INCREF(obj)
                    if is_list:
                        PyList_SET_ITEM(collection_, i, obj)
                    else:
                        PyTuple_SET_ITEM(collection_, i, obj)
                return
            for i in range(len_):
                ref_id = ref_reader.try_preserve_ref_id(read_context)
                if ref_id < NOT_NULL_VALUE_FLAG:
                    obj = ref_reader.get_read_ref()
                else:
                    obj = serializer.read(read_context)
                    ref_reader.set_read_ref(ref_id, obj)
                self._add_element(collection_, i, obj)
        finally:
            read_context.decrease_depth()

    cpdef _write_different_types(self, WriteContext write_context, value, int8_t collect_flag):
        cdef bint tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
        cdef bint has_null = (collect_flag & COLL_HAS_NULL) != 0
        cdef RefWriter ref_writer = write_context.ref_writer
        cdef TypeInfo typeinfo

        if tracking_ref:
            for item_obj in value:
                if not ref_writer.write_ref_or_null(write_context, item_obj):
                    typeinfo = self.type_resolver.get_type_info(type(item_obj))
                    self.type_resolver.write_type_info(write_context, typeinfo)
                    typeinfo.serializer.write(write_context, item_obj)
            return
        if not has_null:
            for item_obj in value:
                typeinfo = self.type_resolver.get_type_info(type(item_obj))
                self.type_resolver.write_type_info(write_context, typeinfo)
                typeinfo.serializer.write(write_context, item_obj)
            return
        for item_obj in value:
            if item_obj is None:
                write_context.write_int8(NULL_FLAG)
            else:
                write_context.write_int8(NOT_NULL_VALUE_FLAG)
                typeinfo = self.type_resolver.get_type_info(type(item_obj))
                self.type_resolver.write_type_info(write_context, typeinfo)
                typeinfo.serializer.write(write_context, item_obj)

    cpdef _add_element(self, object collection_, int64_t index, object element):
        raise NotImplementedError


cdef inline object get_next_element(ReadContext read_context):
    cdef int32_t ref_id = read_context.try_preserve_ref_id()
    cdef TypeInfo typeinfo
    cdef object obj
    if ref_id < NOT_NULL_VALUE_FLAG:
        return read_context.get_read_ref()
    typeinfo = read_context.type_resolver.read_type_info(read_context)
    obj = typeinfo.serializer.read(read_context)
    read_context.set_read_ref(ref_id, obj)
    return obj


cdef class ListSerializer(CollectionSerializer):
    cpdef read(self, ReadContext read_context):
        cdef int32_t len_ = read_context.read_var_uint32()
        cdef list list_
        cdef int8_t collect_flag
        cdef TypeInfo typeinfo
        cdef Serializer elem_serializer = self.elem_serializer
        cdef uint8_t type_id = 0
        cdef bint tracking_ref
        cdef bint has_null
        cdef int8_t head_flag
        cdef int64_t i

        if len_ > read_context.max_collection_size:
            raise ValueError(
                f"List size {len_} exceeds the configured limit of {read_context.max_collection_size}"
            )
        list_ = PyList_New(len_)
        if len_ == 0:
            return list_

        collect_flag = read_context.read_int8()
        read_context.reference(list_)
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
                typeinfo = self.type_resolver.read_type_info(read_context)
                elem_serializer = typeinfo.serializer
            else:
                typeinfo = self.elem_type_info
            if (collect_flag & COLL_HAS_NULL) == 0:
                type_id = typeinfo.type_id
                if Fory_CanUsePrimitiveCollectionFastpath(type_id):
                    self._read_primitive_fastpath(read_context, len_, list_, type_id)
                    return list_
                if (collect_flag & COLL_TRACKING_REF) == 0:
                    self._read_same_type_no_ref(read_context, len_, list_, elem_serializer)
                else:
                    self._read_same_type_ref(read_context, len_, list_, elem_serializer)
            elif (collect_flag & COLL_TRACKING_REF) != 0:
                self._read_same_type_ref(read_context, len_, list_, elem_serializer)
            else:
                self._read_same_type_has_null(read_context, len_, list_, elem_serializer)
            return list_

        read_context.increase_depth()
        try:
            tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
            has_null = (collect_flag & COLL_HAS_NULL) != 0
            if tracking_ref:
                for i in range(len_):
                    elem = get_next_element(read_context)
                    Py_INCREF(elem)
                    PyList_SET_ITEM(list_, i, elem)
                return list_
            if not has_null:
                for i in range(len_):
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    elem = read_context.read_non_ref(typeinfo.serializer)
                    Py_INCREF(elem)
                    PyList_SET_ITEM(list_, i, elem)
                return list_
            for i in range(len_):
                head_flag = read_context.read_int8()
                if head_flag == NULL_FLAG:
                    elem = None
                else:
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    elem = read_context.read_non_ref(typeinfo.serializer)
                Py_INCREF(elem)
                PyList_SET_ITEM(list_, i, elem)
            return list_
        finally:
            read_context.decrease_depth()

    cpdef _add_element(self, object collection_, int64_t index, object element):
        Py_INCREF(element)
        PyList_SET_ITEM(collection_, index, element)


@cython.final
cdef class TupleSerializer(CollectionSerializer):
    cpdef read(self, ReadContext read_context):
        cdef int32_t len_ = read_context.read_var_uint32()
        cdef tuple tuple_
        cdef int8_t collect_flag
        cdef TypeInfo typeinfo
        cdef Serializer elem_serializer = self.elem_serializer
        cdef uint8_t type_id = 0
        cdef bint tracking_ref
        cdef bint has_null
        cdef int8_t head_flag
        cdef int64_t i

        if len_ > read_context.max_collection_size:
            raise ValueError(
                f"Tuple size {len_} exceeds the configured limit of {read_context.max_collection_size}"
            )
        tuple_ = PyTuple_New(len_)
        if len_ == 0:
            return tuple_

        collect_flag = read_context.read_int8()
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
                typeinfo = self.type_resolver.read_type_info(read_context)
                elem_serializer = typeinfo.serializer
            else:
                typeinfo = self.elem_type_info
            if (collect_flag & COLL_HAS_NULL) == 0:
                type_id = typeinfo.type_id
                if Fory_CanUsePrimitiveCollectionFastpath(type_id):
                    self._read_primitive_fastpath(read_context, len_, tuple_, type_id)
                    return tuple_
                if (collect_flag & COLL_TRACKING_REF) == 0:
                    self._read_same_type_no_ref(read_context, len_, tuple_, elem_serializer)
                else:
                    self._read_same_type_ref(read_context, len_, tuple_, elem_serializer)
            elif (collect_flag & COLL_TRACKING_REF) != 0:
                self._read_same_type_ref(read_context, len_, tuple_, elem_serializer)
            else:
                self._read_same_type_has_null(read_context, len_, tuple_, elem_serializer)
            return tuple_

        read_context.increase_depth()
        try:
            tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
            has_null = (collect_flag & COLL_HAS_NULL) != 0
            if tracking_ref:
                for i in range(len_):
                    elem = get_next_element(read_context)
                    Py_INCREF(elem)
                    PyTuple_SET_ITEM(tuple_, i, elem)
                return tuple_
            if not has_null:
                for i in range(len_):
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    elem = read_context.read_non_ref(typeinfo.serializer)
                    Py_INCREF(elem)
                    PyTuple_SET_ITEM(tuple_, i, elem)
                return tuple_
            for i in range(len_):
                head_flag = read_context.read_int8()
                if head_flag == NULL_FLAG:
                    elem = None
                else:
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    elem = read_context.read_non_ref(typeinfo.serializer)
                Py_INCREF(elem)
                PyTuple_SET_ITEM(tuple_, i, elem)
            return tuple_
        finally:
            read_context.decrease_depth()

    cpdef _add_element(self, object collection_, int64_t index, object element):
        Py_INCREF(element)
        PyTuple_SET_ITEM(collection_, index, element)


@cython.final
cdef class StringArraySerializer(ListSerializer):
    def __init__(self, type_resolver, type_):
        super().__init__(type_resolver, type_, StringSerializer(type_resolver, str))


@cython.final
cdef class SetSerializer(CollectionSerializer):
    cpdef read(self, ReadContext read_context):
        cdef set instance = set()
        cdef int32_t len_
        cdef int8_t collect_flag
        cdef TypeInfo typeinfo
        cdef Serializer elem_serializer = self.elem_serializer
        cdef uint8_t type_id = 0
        cdef bint tracking_ref
        cdef bint has_null
        cdef int8_t head_flag
        cdef int64_t i

        read_context.reference(instance)
        len_ = read_context.read_var_uint32()
        if len_ > read_context.max_collection_size:
            raise ValueError(
                f"Set size {len_} exceeds the configured limit of {read_context.max_collection_size}"
            )
        if len_ == 0:
            return instance

        collect_flag = read_context.read_int8()
        if (collect_flag & COLL_IS_SAME_TYPE) != 0:
            if (collect_flag & COLL_IS_DECL_ELEMENT_TYPE) == 0:
                typeinfo = self.type_resolver.read_type_info(read_context)
                elem_serializer = typeinfo.serializer
            else:
                typeinfo = self.elem_type_info
            if (collect_flag & COLL_HAS_NULL) == 0:
                type_id = typeinfo.type_id
                if Fory_CanUsePrimitiveCollectionFastpath(type_id):
                    self._read_primitive_fastpath(read_context, len_, instance, type_id)
                    return instance
                if (collect_flag & COLL_TRACKING_REF) == 0:
                    self._read_same_type_no_ref(read_context, len_, instance, elem_serializer)
                else:
                    self._read_same_type_ref(read_context, len_, instance, elem_serializer)
            elif (collect_flag & COLL_TRACKING_REF) != 0:
                self._read_same_type_ref(read_context, len_, instance, elem_serializer)
            else:
                self._read_same_type_has_null(read_context, len_, instance, elem_serializer)
            return instance

        read_context.increase_depth()
        try:
            tracking_ref = (collect_flag & COLL_TRACKING_REF) != 0
            has_null = (collect_flag & COLL_HAS_NULL) != 0
            if tracking_ref:
                for _ in range(len_):
                    instance.add(get_next_element(read_context))
                return instance
            if not has_null:
                for _ in range(len_):
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    instance.add(read_context.read_non_ref(typeinfo.serializer))
                return instance
            for _ in range(len_):
                head_flag = read_context.read_int8()
                if head_flag == NULL_FLAG:
                    instance.add(None)
                else:
                    typeinfo = self.type_resolver.read_type_info(read_context)
                    instance.add(read_context.read_non_ref(typeinfo.serializer))
            return instance
        finally:
            read_context.decrease_depth()

    cpdef _add_element(self, object collection_, int64_t index, object element):
        collection_.add(element)


@cython.final
cdef class MapSerializer(Serializer):
    # Map serializers can point at either Cython or Python serializer instances.
    cdef Serializer key_serializer
    cdef Serializer value_serializer
    cdef bint key_tracking_ref
    cdef bint value_tracking_ref

    def __init__(
        self,
        type_resolver,
        type_,
        key_serializer=None,
        value_serializer=None,
        key_tracking_ref=None,
        value_tracking_ref=None,
    ):
        super().__init__(type_resolver, type_)
        self.key_serializer = key_serializer
        self.value_serializer = value_serializer
        self.key_tracking_ref = False
        self.value_tracking_ref = False
        if key_serializer is not None:
            self.key_tracking_ref = key_serializer.need_to_write_ref
            if key_tracking_ref is not None:
                self.key_tracking_ref = bool(key_tracking_ref) and type_resolver.track_ref
        if value_serializer is not None:
            self.value_tracking_ref = value_serializer.need_to_write_ref
            if value_tracking_ref is not None:
                self.value_tracking_ref = bool(value_tracking_ref) and type_resolver.track_ref

    cpdef write(self, WriteContext write_context, obj):
        cdef int32_t length = len(obj)
        cdef RefWriter ref_writer = write_context.ref_writer
        cdef Serializer key_serializer = self.key_serializer
        cdef Serializer value_serializer = self.value_serializer
        cdef object items_iter
        cdef object key
        cdef object value
        cdef bint has_next = True
        cdef int32_t chunk_size_offset
        cdef int8_t chunk_header
        cdef int32_t chunk_size
        cdef TypeInfo key_type_info
        cdef TypeInfo value_type_info
        cdef bint key_write_ref
        cdef bint value_write_ref
        cdef object key_cls
        cdef object value_cls

        write_context.write_var_uint32(length)
        if length == 0:
            return

        items_iter = iter(obj.items())
        key, value = next(items_iter)
        while has_next:
            while True:
                if key is not None:
                    if value is not None:
                        break
                    if key_serializer is not None:
                        key_write_ref = self.key_tracking_ref
                        if key_write_ref:
                            write_context.write_int8(NULL_VALUE_KEY_DECL_TYPE_TRACKING_REF)
                            if not ref_writer.write_ref_or_null(write_context, key):
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
                                if not ref_writer.write_ref_or_null(write_context, value):
                                    self._write_obj(value_serializer, write_context, value)
                            else:
                                write_context.write_int8(NULL_KEY_VALUE_DECL_TYPE)
                                self._write_obj(value_serializer, write_context, value)
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
            chunk_size_offset = write_context.buffer.get_writer_index() - 1
            chunk_header = 0

            if key_serializer is not None:
                chunk_header |= KEY_DECL_TYPE
            else:
                key_type_info = self.type_resolver.get_type_info(key_cls)
                self.type_resolver.write_type_info(write_context, key_type_info)
                key_serializer = key_type_info.serializer

            if value_serializer is not None:
                chunk_header |= VALUE_DECL_TYPE
            else:
                value_type_info = self.type_resolver.get_type_info(value_cls)
                self.type_resolver.write_type_info(write_context, value_type_info)
                value_serializer = value_type_info.serializer

            key_write_ref = self.key_tracking_ref if self.key_serializer is not None else key_serializer.need_to_write_ref
            value_write_ref = self.value_tracking_ref if self.value_serializer is not None else value_serializer.need_to_write_ref
            if key_write_ref:
                chunk_header |= TRACKING_KEY_REF
            if value_write_ref:
                chunk_header |= TRACKING_VALUE_REF

            write_context.buffer.put_uint8(chunk_size_offset - 1, chunk_header)
            chunk_size = 0
            while chunk_size < MAX_CHUNK_SIZE:
                if key is None or value is None or type(key) is not key_cls or type(value) is not value_cls:
                    break
                if not key_write_ref or not ref_writer.write_ref_or_null(write_context, key):
                    self._write_obj(key_serializer, write_context, key)
                if not value_write_ref or not ref_writer.write_ref_or_null(write_context, value):
                    self._write_obj(value_serializer, write_context, value)
                chunk_size += 1
                try:
                    key, value = next(items_iter)
                except StopIteration:
                    has_next = False
                    break

            key_serializer = self.key_serializer
            value_serializer = self.value_serializer
            write_context.buffer.put_uint8(chunk_size_offset, chunk_size)
            write_context.exit_flush_barrier()
            write_context.try_flush()

    cpdef read(self, ReadContext read_context):
        cdef int32_t size = read_context.read_var_uint32()
        cdef dict map_ = {}
        cdef int8_t chunk_header = read_context.read_uint8() if size != 0 else 0
        cdef RefReader ref_reader = read_context.ref_reader
        cdef Serializer key_serializer = self.key_serializer
        cdef Serializer value_serializer = self.value_serializer
        cdef bint key_has_null
        cdef bint value_has_null
        cdef bint track_key_ref
        cdef bint track_value_ref
        cdef bint key_is_declared_type
        cdef bint value_is_declared_type
        cdef int32_t chunk_size
        cdef int32_t ref_id

        if size > read_context.max_collection_size:
            raise ValueError(f"Map size {size} exceeds the configured limit of {read_context.max_collection_size}")
        read_context.reference(map_)
        read_context.increase_depth()
        try:
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
                                ref_id = ref_reader.try_preserve_ref_id(read_context)
                                if ref_id < NOT_NULL_VALUE_FLAG:
                                    key = ref_reader.get_read_ref()
                                else:
                                    key = self._read_obj(key_serializer, read_context)
                                    ref_reader.set_read_ref(ref_id, key)
                            else:
                                key = self._read_obj_no_ref(key_serializer, read_context)
                        else:
                            key = read_context.read_ref()
                        map_[key] = None
                    elif not value_has_null:
                        track_value_ref = (chunk_header & TRACKING_VALUE_REF) != 0
                        if (chunk_header & VALUE_DECL_TYPE) != 0:
                            if track_value_ref:
                                ref_id = ref_reader.try_preserve_ref_id(read_context)
                                if ref_id < NOT_NULL_VALUE_FLAG:
                                    value = ref_reader.get_read_ref()
                                else:
                                    value = self._read_obj(value_serializer, read_context)
                                    ref_reader.set_read_ref(ref_id, value)
                            else:
                                value = self._read_obj_no_ref(value_serializer, read_context)
                        else:
                            value = read_context.read_ref()
                        map_[None] = value
                    else:
                        map_[None] = None
                    size -= 1
                    if size == 0:
                        return map_
                    chunk_header = read_context.read_uint8()

                track_key_ref = (chunk_header & TRACKING_KEY_REF) != 0
                track_value_ref = (chunk_header & TRACKING_VALUE_REF) != 0
                key_is_declared_type = (chunk_header & KEY_DECL_TYPE) != 0
                value_is_declared_type = (chunk_header & VALUE_DECL_TYPE) != 0
                chunk_size = read_context.read_uint8()
                if not key_is_declared_type:
                    key_serializer = self.type_resolver.read_type_info(read_context).serializer
                if not value_is_declared_type:
                    value_serializer = self.type_resolver.read_type_info(read_context).serializer
                for _ in range(chunk_size):
                    if track_key_ref:
                        ref_id = ref_reader.try_preserve_ref_id(read_context)
                        if ref_id < NOT_NULL_VALUE_FLAG:
                            key = ref_reader.get_read_ref()
                        else:
                            key = self._read_obj(key_serializer, read_context)
                            ref_reader.set_read_ref(ref_id, key)
                    else:
                        key = self._read_obj_no_ref(key_serializer, read_context)
                    if track_value_ref:
                        ref_id = ref_reader.try_preserve_ref_id(read_context)
                        if ref_id < NOT_NULL_VALUE_FLAG:
                            value = ref_reader.get_read_ref()
                        else:
                            value = self._read_obj(value_serializer, read_context)
                            ref_reader.set_read_ref(ref_id, value)
                    else:
                        value = self._read_obj_no_ref(value_serializer, read_context)
                    map_[key] = value
                    size -= 1
                if size != 0:
                    chunk_header = read_context.read_uint8()
            return map_
        finally:
            read_context.decrease_depth()

    cdef inline void _write_obj(self, Serializer serializer, WriteContext write_context, object obj):
        serializer.write(write_context, obj)

    cdef inline object _read_obj(self, Serializer serializer, ReadContext read_context):
        return serializer.read(read_context)

    cdef inline object _read_obj_no_ref(self, Serializer serializer, ReadContext read_context):
        return read_context.read_non_ref(serializer)


SubMapSerializer = MapSerializer
