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

from pyfory.context import EncodedMetaString, EMPTY_ENCODED_META_STRING
from pyfory.resolver import NULL_FLAG, REF_FLAG, NOT_NULL_VALUE_FLAG, REF_VALUE_FLAG


INT64_TYPE_ID = TypeId.VARINT64
FLOAT64_TYPE_ID = TypeId.FLOAT64
BOOL_TYPE_ID = TypeId.BOOL
STRING_TYPE_ID = TypeId.STRING


cdef inline uint64_t _mix64(uint64_t x):
    x ^= x >> 33
    x *= <uint64_t>0xff51afd7ed558ccd
    x ^= x >> 33
    x *= <uint64_t>0xc4ceb9fe1a85ec53
    x ^= x >> 33
    return x


cdef inline int64_t _hash_small_metastring(
    int64_t v1,
    int64_t v2,
    int32_t length,
    uint8_t encoding,
):
    cdef uint64_t k = <uint64_t>0x9e3779b97f4a7c15
    cdef uint64_t x = (<uint64_t>v1) ^ ((<uint64_t>v2) * k)
    x ^= (<uint64_t>length) << 56
    cdef uint64_t h = _mix64(x)
    h = (h & <uint64_t>0xffffffffffffff00) | encoding
    return <int64_t>h


cdef class WriteContext
cdef class ReadContext


@cython.final
cdef class RefWriter:
    cdef readonly bint track_ref
    cdef dict written_objects

    def __init__(self, bint track_ref):
        self.track_ref = track_ref
        self.written_objects = {}

    cpdef bint write_ref_or_null(self, WriteContext write_context, obj):
        if obj is None:
            write_context.write_int8(NULL_FLAG)
            return True
        if not self.track_ref:
            write_context.write_int8(NOT_NULL_VALUE_FLAG)
            return False
        object_id = id(obj)
        written_id = self.written_objects.get(object_id)
        if written_id is not None:
            write_context.write_int8(REF_FLAG)
            write_context.write_var_uint32(written_id[0])
            return True
        written_id = len(self.written_objects)
        self.written_objects[object_id] = (written_id, obj)
        write_context.write_int8(REF_VALUE_FLAG)
        return False

    cpdef bint write_ref_value_flag(self, WriteContext write_context, obj):
        assert obj is not None
        if not self.track_ref:
            write_context.write_int8(NOT_NULL_VALUE_FLAG)
            return True
        object_id = id(obj)
        written_id = self.written_objects.get(object_id)
        if written_id is not None:
            write_context.write_int8(REF_FLAG)
            write_context.write_var_uint32(written_id[0])
            return False
        written_id = len(self.written_objects)
        self.written_objects[object_id] = (written_id, obj)
        write_context.write_int8(REF_VALUE_FLAG)
        return True

    cpdef bint write_null_flag(self, WriteContext write_context, obj):
        if obj is None:
            write_context.write_int8(NULL_FLAG)
            return True
        return False

    cpdef reset(self):
        if self.track_ref:
            self.written_objects.clear()


@cython.final
cdef class RefReader:
    cdef readonly bint track_ref
    cdef list read_objects
    cdef list read_ref_ids
    cdef PyObject * read_object

    def __init__(self, bint track_ref):
        self.track_ref = track_ref
        self.read_objects = []
        self.read_ref_ids = []
        self.read_object = NULL

    cpdef int32_t read_ref_or_null(self, ReadContext read_context):
        cdef int32_t head_flag = read_context.read_int8()
        cdef int32_t ref_id
        if not self.track_ref:
            return head_flag
        if head_flag == REF_FLAG:
            ref_id = read_context.read_var_uint32()
            obj = self.get_read_ref(ref_id)
            Py_INCREF(obj)
            Py_XDECREF(self.read_object)
            self.read_object = <PyObject *>obj
        else:
            Py_XDECREF(self.read_object)
            self.read_object = NULL
        return head_flag

    cpdef int32_t preserve_ref_id(self, int32_t ref_id=-2147483648):
        if not self.track_ref:
            return -1
        if ref_id == -2147483648:
            ref_id = len(self.read_objects)
            self.read_objects.append(None)
        self.read_ref_ids.append(ref_id)
        return ref_id

    cpdef int32_t try_preserve_ref_id(self, ReadContext read_context):
        cdef int32_t head_flag = read_context.read_int8()
        cdef int32_t ref_id
        if not self.track_ref:
            return head_flag
        if head_flag == REF_FLAG:
            ref_id = read_context.read_var_uint32()
            obj = self.get_read_ref(ref_id)
            Py_INCREF(obj)
            Py_XDECREF(self.read_object)
            self.read_object = <PyObject *>obj
            return head_flag
        Py_XDECREF(self.read_object)
        self.read_object = NULL
        if head_flag == REF_VALUE_FLAG:
            return self.preserve_ref_id()
        self.read_ref_ids.append(-1)
        return head_flag

    cpdef int32_t last_preserved_ref_id(self):
        if not self.track_ref:
            return -1
        return self.read_ref_ids[-1]

    cpdef bint has_preserved_ref_id(self):
        if not self.track_ref:
            return False
        return bool(self.read_ref_ids)

    cpdef reference(self, obj):
        if not self.track_ref:
            return
        cdef int32_t ref_id = self.read_ref_ids.pop()
        if ref_id < 0:
            return
        self.set_read_ref(ref_id, obj)

    cpdef get_read_ref(self, id_=None):
        cdef int32_t ref_id
        if not self.track_ref:
            return None
        if id_ is None:
            if self.read_object == NULL:
                return None
            return <object>self.read_object
        ref_id = id_
        if ref_id < 0 or ref_id >= len(self.read_objects):
            raise ValueError(f"Invalid ref id {ref_id}, current size {len(self.read_objects)}")
        obj = self.read_objects[ref_id]
        if obj is None:
            raise ValueError(f"Invalid ref id {ref_id}, current size {len(self.read_objects)}")
        return obj

    cpdef set_read_ref(self, int32_t id_, obj):
        if not self.track_ref or id_ < 0:
            return
        if id_ < 0:
            return
        if id_ >= len(self.read_objects):
            raise RuntimeError(f"Ref id {id_} invalid")
        self.read_objects[id_] = obj

    cpdef reset(self):
        if self.track_ref:
            self.read_objects.clear()
            self.read_ref_ids.clear()
        Py_XDECREF(self.read_object)
        self.read_object = NULL


@cython.final
cdef class MetaStringWriter:
    cdef dict _written_meta_strings

    def __init__(self):
        self._written_meta_strings = {}

    cpdef write_encoded_meta_string(self, Buffer buffer, encoded_meta_string):
        entry = self._written_meta_strings.get(id(encoded_meta_string))
        cdef int32_t length = encoded_meta_string.length
        if entry is None:
            dynamic_id = len(self._written_meta_strings)
            self._written_meta_strings[id(encoded_meta_string)] = (dynamic_id, encoded_meta_string)
            buffer.write_var_uint32(length << 1)
            if length <= 16:
                if length != 0:
                    buffer.write_int8(encoded_meta_string.encoding)
            else:
                buffer.write_int64(encoded_meta_string.hashcode)
            buffer.write_bytes(encoded_meta_string.data)
            return
        buffer.write_var_uint32(((entry[0] + 1) << 1) | 1)

    cpdef reset(self):
        self._written_meta_strings.clear()


@cython.final
cdef class MetaStringReader:
    cdef object shared_registry
    cdef list _dynamic_id_to_encoded_meta_strings
    cdef dict _hash_to_encoded_meta_strings
    cdef dict _small_encoded_meta_strings

    def __init__(self, shared_registry):
        self.shared_registry = shared_registry
        self._dynamic_id_to_encoded_meta_strings = []
        self._hash_to_encoded_meta_strings = {}
        self._small_encoded_meta_strings = {}

    cpdef read_encoded_meta_string(self, Buffer buffer):
        cdef int32_t header = buffer.read_var_uint32()
        cdef int32_t length = header >> 1
        cdef object encoded_meta_string
        if header & 0b1 != 0:
            if length <= 0:
                raise ValueError("Invalid dynamic metastring id 0")
            return self._dynamic_id_to_encoded_meta_strings[length - 1]
        if length <= 16:
            encoded_meta_string = self._read_small_encoded_meta_string(buffer, length)
        else:
            encoded_meta_string = self._read_big_encoded_meta_string(buffer, length)
        self._dynamic_id_to_encoded_meta_strings.append(encoded_meta_string)
        return encoded_meta_string

    cdef object _read_small_encoded_meta_string(self, Buffer buffer, int32_t length):
        if length == 0:
            return EMPTY_ENCODED_META_STRING
        cdef int8_t encoding = buffer.read_int8()
        cdef int64_t v1 = 0
        cdef int64_t v2 = 0
        if length <= 8:
            v1 = buffer.read_bytes_as_int64(length)
        else:
            v1 = buffer.read_int64()
            v2 = buffer.read_bytes_as_int64(length - 8)
        key = (v1, v2, encoding, length)
        encoded_meta_string = self._small_encoded_meta_strings.get(key)
        if encoded_meta_string is not None:
            return encoded_meta_string
        hashcode = _hash_small_metastring(v1, v2, length, <uint8_t>encoding)
        reader_index = buffer.get_reader_index()
        data = buffer.get_bytes(reader_index - length, length)
        encoded_meta_string = self.shared_registry.get_or_create_encoded_meta_string(data, hashcode)
        self._small_encoded_meta_strings[key] = encoded_meta_string
        return encoded_meta_string

    cdef object _read_big_encoded_meta_string(self, Buffer buffer, int32_t length):
        cdef int64_t hashcode = buffer.read_int64()
        cdef int32_t reader_index = buffer.get_reader_index()
        buffer.check_bound(reader_index, length)
        data = buffer.get_bytes(reader_index, length)
        buffer.set_reader_index(reader_index + length)
        key = (hashcode, data)
        encoded_meta_string = self._hash_to_encoded_meta_strings.get(key)
        if encoded_meta_string is None:
            encoded_meta_string = self.shared_registry.get_or_create_encoded_meta_string(data, hashcode)
            self._hash_to_encoded_meta_strings[key] = encoded_meta_string
        return encoded_meta_string

    cpdef reset(self):
        self._dynamic_id_to_encoded_meta_strings.clear()


@cython.final
cdef class MetaShareWriteContext:
    cdef flat_hash_map[uint64_t, int32_t] class_map

    def __init__(self):
        pass

    cpdef reset(self):
        self.class_map.clear()


@cython.final
cdef class MetaShareReadContext:
    cdef public list read_type_infos

    def __init__(self):
        self.read_type_infos = []

    cpdef reset(self):
        self.read_type_infos.clear()


@cython.final
cdef class WriteContext:
    """
    Per-operation serialization state for the active Cython runtime.

    The write context owns mutable write-only state such as reference tracking,
    meta-string tracking, meta-share state, the active output buffer, temporary
    callback hooks, and context-local objects used during one top-level write.
    """

    cdef readonly TypeResolver type_resolver
    cdef readonly bint xlang
    cdef readonly bint track_ref
    cdef readonly bint strict
    cdef readonly bint compatible
    cdef readonly bint field_nullable
    cdef readonly object policy
    cdef readonly int32_t max_collection_size
    cdef readonly int32_t max_binary_size
    cdef readonly RefWriter ref_writer
    cdef readonly MetaStringWriter meta_string_writer
    cdef readonly MetaShareWriteContext meta_share_context
    cdef public Buffer buffer
    cdef CBuffer* c_buffer
    cdef public object buffer_callback
    cdef public object unsupported_callback
    cdef dict context_objects
    cdef public int32_t depth

    def __init__(self, Config config, TypeResolver type_resolver):
        """
        Create a reusable write context bound to one runtime config and resolver.

        Args:
            config: Immutable runtime configuration for this Fory instance.
            type_resolver: Active Cython resolver cache used during writes.
        """
        self.type_resolver = type_resolver
        self.xlang = config.xlang
        self.track_ref = config.track_ref
        self.strict = config.strict
        self.compatible = config.compatible
        self.field_nullable = config.field_nullable
        self.policy = config.policy
        self.max_collection_size = config.max_collection_size
        self.max_binary_size = config.max_binary_size
        self.ref_writer = RefWriter(self.track_ref)
        self.meta_string_writer = MetaStringWriter()
        self.meta_share_context = MetaShareWriteContext() if config.scoped_meta_share_enabled else None
        self.buffer = None
        self.c_buffer = NULL
        self.buffer_callback = None
        self.unsupported_callback = None
        self.context_objects = {}
        self.depth = 0

    cpdef prepare(self, Buffer buffer, buffer_callback=None, unsupported_callback=None):
        self.buffer = buffer
        self.c_buffer = buffer.c_buffer
        self.buffer_callback = buffer_callback
        self.unsupported_callback = unsupported_callback
        self.depth = 0

    cpdef Buffer serialize_root(self, obj, Buffer buffer, buffer_callback=None, unsupported_callback=None):
        cdef uint8_t header = 0b10
        self.prepare(buffer, buffer_callback=buffer_callback, unsupported_callback=unsupported_callback)
        if obj is None:
            header |= 0b1
        if buffer_callback is not None:
            header |= 0b100
        self.c_buffer.write_uint8(header)
        if not self.track_ref:
            if obj is None:
                self.write_int8(NULL_FLAG)
            else:
                self.write_int8(NOT_NULL_VALUE_FLAG)
                self.write_non_ref(obj)
            return buffer
        self.write_ref(obj)
        return buffer

    cpdef reset(self):
        self.ref_writer.reset()
        self.meta_string_writer.reset()
        if self.meta_share_context is not None:
            self.meta_share_context.reset()
        if self.context_objects:
            self.context_objects.clear()
        self.buffer = None
        self.c_buffer = NULL
        self.buffer_callback = None
        self.unsupported_callback = None
        self.depth = 0

    cpdef add_context_object(self, key, obj):
        self.context_objects[id(key)] = obj

    cpdef bint has_context_object(self, key):
        return id(key) in self.context_objects

    cpdef get_context_object(self, key, default=None):
        return self.context_objects.get(id(key), default)

    cpdef increase_depth(self, int32_t diff=1):
        self.depth += diff

    cpdef decrease_depth(self, int32_t diff=1):
        self.depth -= diff

    cpdef bint write_ref_or_null(self, obj):
        return self.ref_writer.write_ref_or_null(self, obj)

    cpdef bint write_ref_value_flag(self, obj):
        return self.ref_writer.write_ref_value_flag(self, obj)

    cpdef bint write_null_flag(self, obj):
        return self.ref_writer.write_null_flag(self, obj)

    cpdef write_ref(self, obj, TypeInfo typeinfo=None, Serializer serializer=None):
        if serializer is None and typeinfo is not None:
            serializer = typeinfo.serializer
        if serializer is None or serializer.need_to_write_ref:
            if self.ref_writer.write_ref_or_null(self, obj):
                return
            self.write_non_ref(obj, serializer=serializer, typeinfo=typeinfo)
            return
        if obj is None:
            self.write_int8(NULL_FLAG)
            return
        self.write_int8(NOT_NULL_VALUE_FLAG)
        self.write_non_ref(obj, serializer=serializer, typeinfo=typeinfo)

    cpdef write_non_ref(self, obj, Serializer serializer=None, TypeInfo typeinfo=None):
        cdef object cls
        cdef TypeInfo c_typeinfo
        if serializer is not None:
            serializer.write(self, obj)
            return
        cls = type(obj)
        if cls is str:
            self.write_uint8(STRING_TYPE_ID)
            self.write_string(obj)
            return
        if cls is int:
            self.write_uint8(INT64_TYPE_ID)
            self.write_varint64(obj)
            return
        if cls is bool:
            self.write_uint8(BOOL_TYPE_ID)
            self.write_bool(obj)
            return
        if cls is float:
            self.write_uint8(FLOAT64_TYPE_ID)
            self.write_double(obj)
            return
        if typeinfo is None:
            typeinfo = self.type_resolver.get_type_info(cls)
        c_typeinfo = <TypeInfo>typeinfo
        self.type_resolver.write_type_info(self, c_typeinfo)
        c_typeinfo.serializer.write(self, obj)

    cpdef write_no_ref(self, obj, Serializer serializer=None, TypeInfo typeinfo=None):
        self.write_non_ref(obj, serializer=serializer, typeinfo=typeinfo)

    cpdef write_buffer_object(self, buffer_object):
        cdef int32_t size
        cdef int32_t writer_index
        cdef Buffer buf
        if self.buffer_callback is None:
            size = buffer_object.total_bytes()
            self.write_var_uint32(size)
            writer_index = self.buffer.get_writer_index()
            self.buffer.ensure(writer_index + size)
            buf = self.buffer.slice(writer_index, size)
            buffer_object.write_to(buf)
            self.buffer.set_writer_index(writer_index + size)
            return
        if self.buffer_callback(buffer_object):
            self.write_bool(True)
            size = buffer_object.total_bytes()
            self.write_var_uint32(size)
            writer_index = self.buffer.get_writer_index()
            self.buffer.ensure(writer_index + size)
            buf = self.buffer.slice(writer_index, size)
            buffer_object.write_to(buf)
            self.buffer.set_writer_index(writer_index + size)
            return
        self.write_bool(False)

    cpdef handle_unsupported_write(self, obj):
        if self.unsupported_callback is None or self.unsupported_callback(obj):
            raise NotImplementedError(f"{type(obj)} is not supported for write")

    cpdef enter_flush_barrier(self):
        output_stream = None if self.buffer is None else self.buffer.get_output_stream()
        if output_stream is not None:
            output_stream.enter_flush_barrier()

    cpdef exit_flush_barrier(self):
        output_stream = None if self.buffer is None else self.buffer.get_output_stream()
        if output_stream is not None:
            output_stream.exit_flush_barrier()

    cpdef try_flush(self):
        if self.buffer is None or self.buffer.get_writer_index() <= 4096:
            return
        output_stream = self.buffer.get_output_stream()
        if output_stream is not None:
            output_stream.try_flush()

    cpdef force_flush(self):
        if self.buffer is None:
            return
        output_stream = self.buffer.get_output_stream()
        if output_stream is not None:
            output_stream.force_flush()

    cpdef inline void write_bool(self, bint value):
        self.c_buffer.write_uint8(<uint8_t>value)

    cpdef inline void write_int8(self, int8_t value):
        self.c_buffer.write_int8(value)

    cpdef inline void write_uint8(self, uint8_t value):
        self.c_buffer.write_uint8(value)

    cpdef inline void write_int16(self, int16_t value):
        self.c_buffer.write_int16(value)

    cpdef inline void write_uint16(self, uint16_t value):
        self.c_buffer.write_uint16(value)

    cpdef inline void write_int32(self, int32_t value):
        self.c_buffer.write_int32(value)

    cpdef inline void write_uint32(self, uint32_t value):
        self.c_buffer.write_uint32(value)

    cpdef inline void write_int64(self, int64_t value):
        self.c_buffer.write_int64(value)

    cpdef inline void write_uint64(self, uint64_t value):
        self.c_buffer.write_int64(<int64_t>value)

    cpdef inline void write_varint32(self, int32_t value):
        self.c_buffer.write_var_int32(value)

    cpdef inline void write_var_uint32(self, uint32_t value):
        self.c_buffer.write_var_uint32(value)

    cpdef inline void write_varint64(self, int64_t value):
        self.c_buffer.write_var_int64(value)

    cpdef inline void write_var_uint64(self, uint64_t value):
        self.c_buffer.write_var_uint64(value)

    cpdef inline void write_tagged_int64(self, int64_t value):
        self.c_buffer.write_tagged_int64(value)

    cpdef inline void write_tagged_uint64(self, uint64_t value):
        self.c_buffer.write_tagged_uint64(value)

    cpdef inline void write_float(self, float value):
        self.c_buffer.write_float(value)

    cpdef inline void write_float32(self, float value):
        self.c_buffer.write_float(value)

    cpdef inline void write_double(self, double value):
        self.c_buffer.write_double(value)

    cpdef inline void write_float64(self, double value):
        self.c_buffer.write_double(value)

    cpdef write_string(self, str value):
        self.buffer.write_string(value)

    cpdef write_bytes(self, bytes value):
        self.buffer.write_bytes(value)

    cpdef inline void write_bytes_and_size(self, bytes value):
        self.buffer.write_bytes_and_size(value)

    cpdef write_buffer(self, value, src_index=0, length_=None):
        self.buffer.write_buffer(value, src_index=src_index, length_=length_)

    cpdef inline int32_t get_writer_index(self):
        return self.buffer.get_writer_index()

    cpdef inline put_uint8(self, uint32_t offset, uint8_t value):
        self.buffer.put_uint8(offset, value)


@cython.final
cdef class ReadContext:
    """
    Per-operation deserialization state for the active Cython runtime.

    The read context owns mutable read-only state such as reference resolution,
    meta-string caches, meta-share state, input buffer iterators, unsupported
    object iterators, and the active read depth for one top-level read.
    """

    cdef readonly TypeResolver type_resolver
    cdef readonly bint xlang
    cdef readonly bint track_ref
    cdef readonly bint strict
    cdef readonly bint compatible
    cdef readonly bint field_nullable
    cdef readonly object policy
    cdef readonly int32_t max_depth
    cdef readonly int32_t max_collection_size
    cdef readonly int32_t max_binary_size
    cdef readonly RefReader ref_reader
    cdef readonly MetaStringReader meta_string_reader
    cdef readonly MetaShareReadContext meta_share_context
    cdef public Buffer buffer
    cdef CBuffer* c_buffer
    cdef public object buffers
    cdef public object unsupported_objects
    cdef public bint peer_out_of_band_enabled
    cdef dict context_objects
    cdef public int32_t depth

    def __init__(self, Config config, TypeResolver type_resolver):
        """
        Create a reusable read context bound to one runtime config and resolver.

        Args:
            config: Immutable runtime configuration for this Fory instance.
            type_resolver: Active Cython resolver cache used during reads.
        """
        self.type_resolver = type_resolver
        self.xlang = config.xlang
        self.track_ref = config.track_ref
        self.strict = config.strict
        self.compatible = config.compatible
        self.field_nullable = config.field_nullable
        self.policy = config.policy
        self.max_depth = config.max_depth
        self.max_collection_size = config.max_collection_size
        self.max_binary_size = config.max_binary_size
        self.ref_reader = RefReader(self.track_ref)
        self.meta_string_reader = MetaStringReader(self.type_resolver.shared_registry)
        self.meta_share_context = MetaShareReadContext() if config.scoped_meta_share_enabled else None
        self.buffer = None
        self.c_buffer = NULL
        self.buffers = None
        self.unsupported_objects = None
        self.peer_out_of_band_enabled = False
        self.context_objects = {}
        self.depth = 0

    cpdef prepare(
        self,
        Buffer buffer,
        buffers=None,
        unsupported_objects=None,
        bint peer_out_of_band_enabled=False,
    ):
        self.buffer = buffer
        self.c_buffer = buffer.c_buffer
        self.buffers = iter(buffers) if buffers is not None else None
        self.unsupported_objects = iter(unsupported_objects) if unsupported_objects is not None else None
        self.peer_out_of_band_enabled = peer_out_of_band_enabled
        self.depth = 0

    cpdef deserialize_root(self, Buffer buffer, buffers=None, unsupported_objects=None):
        cdef uint8_t header = buffer.read_uint8()
        cdef bint peer_out_of_band_enabled
        if header & 0b1:
            return None
        peer_out_of_band_enabled = (header & 0b100) != 0
        if peer_out_of_band_enabled:
            assert buffers is not None, (
                "buffers shouldn't be null when the serialized stream is produced with buffer_callback not null."
            )
        else:
            assert buffers is None, (
                "buffers should be null when the serialized stream is produced with buffer_callback null."
            )
        self.prepare(
            buffer,
            buffers=buffers,
            unsupported_objects=unsupported_objects,
            peer_out_of_band_enabled=peer_out_of_band_enabled,
        )
        if not self.track_ref:
            if self.read_int8() == NULL_FLAG:
                return None
            return self.read_non_ref()
        return self.read_ref()

    cpdef reset(self):
        self.ref_reader.reset()
        self.meta_string_reader.reset()
        if self.meta_share_context is not None:
            self.meta_share_context.reset()
        if self.context_objects:
            self.context_objects.clear()
        self.buffer = None
        self.c_buffer = NULL
        self.buffers = None
        self.unsupported_objects = None
        self.peer_out_of_band_enabled = False
        self.depth = 0

    cpdef add_context_object(self, key, obj):
        self.context_objects[id(key)] = obj

    cpdef bint has_context_object(self, key):
        return id(key) in self.context_objects

    cpdef get_context_object(self, key, default=None):
        return self.context_objects.get(id(key), default)

    cpdef increase_depth(self, int32_t diff=1):
        # Depth accounting is paired on the successful path only.
        # If a nested read raises, the top-level deserialize/reset path clears
        # `depth`, so nested readers must not add local try/finally wrappers
        # around increase/decrease pairs.
        self.depth += diff
        if self.depth > self.max_depth:
            raise Exception(
                f"Read depth exceed max depth: {self.depth}, the deserialization data may be malicious. "
                "If it's not malicious, please increase max read depth by Fory(..., max_depth=...)"
            )

    cpdef decrease_depth(self, int32_t diff=1):
        # Only call this after the matching nested read completed successfully.
        self.depth -= diff

    cpdef int32_t read_ref_or_null(self):
        return self.ref_reader.read_ref_or_null(self)

    cpdef int32_t preserve_ref_id(self, ref_id=None):
        if ref_id is None:
            return self.ref_reader.preserve_ref_id()
        return self.ref_reader.preserve_ref_id(ref_id)

    cpdef int32_t try_preserve_ref_id(self):
        return self.ref_reader.try_preserve_ref_id(self)

    cpdef int32_t last_preserved_ref_id(self):
        return self.ref_reader.last_preserved_ref_id()

    cpdef bint has_preserved_ref_id(self):
        return self.ref_reader.has_preserved_ref_id()

    cpdef reference(self, obj):
        self.ref_reader.reference(obj)

    cpdef get_read_ref(self, ref_id=None):
        return self.ref_reader.get_read_ref(ref_id)

    cpdef set_read_ref(self, int32_t ref_id, obj):
        self.ref_reader.set_read_ref(ref_id, obj)

    cpdef read_ref(self, Serializer serializer=None):
        cdef int32_t ref_id
        cdef object obj
        if serializer is None or serializer.need_to_write_ref:
            ref_id = self.ref_reader.try_preserve_ref_id(self)
            if ref_id >= NOT_NULL_VALUE_FLAG:
                obj = self._read_non_ref_internal(serializer)
                self.ref_reader.set_read_ref(ref_id, obj)
                return obj
            return self.ref_reader.get_read_ref()
        if self.read_int8() == NULL_FLAG:
            return None
        return self._read_non_ref_internal(serializer)

    cpdef read_non_ref(self, Serializer serializer=None):
        if self.track_ref and (serializer is None or serializer.need_to_write_ref):
            self.ref_reader.preserve_ref_id(-1)
        return self._read_non_ref_internal(serializer)

    cpdef read_no_ref(self, Serializer serializer=None):
        return self.read_non_ref(serializer=serializer)

    cpdef read_nullable(self, Serializer serializer=None):
        if self.read_int8() == NULL_FLAG:
            return None
        return self._read_non_ref_internal(serializer)

    cdef object _read_non_ref_internal(self, Serializer serializer=None):
        cdef TypeInfo typeinfo
        cdef uint8_t type_id
        cdef object obj
        if serializer is None:
            typeinfo = self.type_resolver.read_type_info(self)
            type_id = typeinfo.type_id
            if type_id == STRING_TYPE_ID:
                return self.read_string()
            if type_id == INT64_TYPE_ID:
                return self.read_varint64()
            if type_id == BOOL_TYPE_ID:
                return self.read_bool()
            if type_id == FLOAT64_TYPE_ID:
                return self.read_double()
            serializer = typeinfo.serializer
        self.increase_depth()
        obj = serializer.read(self)
        self.decrease_depth()
        return obj

    cpdef read_buffer_object(self):
        cdef int32_t size
        cdef int32_t reader_index
        cdef Buffer buf
        if not self.peer_out_of_band_enabled:
            size = self.read_var_uint32()
            if self.buffer.has_input_stream():
                return self.buffer.read_bytes(size)
            reader_index = self.buffer.get_reader_index()
            buf = self.buffer.slice(reader_index, size)
            self.buffer.set_reader_index(reader_index + size)
            return buf
        if not self.read_bool():
            assert self.buffers is not None
            return next(self.buffers)
        size = self.read_var_uint32()
        if self.buffer.has_input_stream():
            return self.buffer.read_bytes(size)
        reader_index = self.buffer.get_reader_index()
        buf = self.buffer.slice(reader_index, size)
        self.buffer.set_reader_index(reader_index + size)
        return buf

    cpdef handle_unsupported_read(self):
        assert self.unsupported_objects is not None
        return next(self.unsupported_objects)

    cpdef inline bint read_bool(self):
        cdef uint8_t value = self.c_buffer.read_uint8(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value != 0

    cpdef inline uint8_t read_uint8(self):
        cdef uint8_t value = self.c_buffer.read_uint8(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int8_t read_int8(self):
        cdef int8_t value = self.c_buffer.read_int8(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int16_t read_int16(self):
        cdef int16_t value = self.c_buffer.read_int16(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline uint16_t read_uint16(self):
        cdef uint16_t value = self.c_buffer.read_uint16(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int32_t read_int32(self):
        cdef int32_t value = self.c_buffer.read_int32(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline uint32_t read_uint32(self):
        cdef uint32_t value = self.c_buffer.read_uint32(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int64_t read_int64(self):
        cdef int64_t value = self.c_buffer.read_int64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline uint64_t read_uint64(self):
        cdef uint64_t value = self.c_buffer.read_uint64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int32_t read_varint32(self):
        cdef int32_t value = self.c_buffer.read_var_int32(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline uint32_t read_var_uint32(self):
        cdef uint32_t value = self.c_buffer.read_var_uint32(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int64_t read_varint64(self):
        cdef int64_t value = self.c_buffer.read_var_int64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline int64_t read_var_uint64(self):
        cdef uint64_t value = self.c_buffer.read_var_uint64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return <int64_t>value

    cpdef inline int64_t read_tagged_int64(self):
        cdef int64_t value = self.c_buffer.read_tagged_int64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline uint64_t read_tagged_uint64(self):
        cdef uint64_t value = self.c_buffer.read_tagged_uint64(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline float read_float(self):
        cdef float value = self.c_buffer.read_float(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline float read_float32(self):
        cdef float value = self.c_buffer.read_float(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline double read_double(self):
        cdef double value = self.c_buffer.read_double(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef inline double read_float64(self):
        cdef double value = self.c_buffer.read_double(self.buffer._error)
        if not self.buffer._error.ok():
            self.buffer._raise_if_error()
        return value

    cpdef read_string(self):
        return self.buffer.read_string()

    cpdef inline bytes read_bytes_and_size(self):
        return self.buffer.read_bytes_and_size()

    cpdef inline int32_t get_reader_index(self):
        return self.buffer.get_reader_index()

    cpdef inline void set_reader_index(self, int32_t value):
        self.buffer.set_reader_index(value)

    cpdef inline void shrink_input_buffer(self):
        self.buffer.shrink_input_buffer()
