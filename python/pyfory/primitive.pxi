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

@cython.final
cdef class BooleanSerializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_bool(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_bool()


@cython.final
cdef class ByteSerializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_int8(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_int8()


@cython.final
cdef class Int16Serializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_int16(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_int16()


@cython.final
cdef class Int32Serializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_varint32(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_varint32()


@cython.final
cdef class Int64Serializer(XlangCompatibleSerializer):
    cpdef inline xwrite(self, Buffer buffer, value):
        buffer.write_varint64(value)

    cpdef inline xread(self, Buffer buffer):
        return buffer.read_varint64()

    cpdef inline write(self, Buffer buffer, value):
        buffer.write_varint64(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_varint64()


@cython.final
cdef class Float32Serializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_float(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_float()


@cython.final
cdef class Float64Serializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        buffer.write_double(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_double()


@cython.final
cdef class StringSerializer(XlangCompatibleSerializer):
    def __init__(self, fory, type_, track_ref=False):
        super().__init__(fory, type_)
        self.need_to_write_ref = track_ref

    cpdef inline write(self, Buffer buffer, value):
        buffer.write_string(value)

    cpdef inline read(self, Buffer buffer):
        return buffer.read_string()


cdef _base_date = datetime.date(1970, 1, 1)
cdef int _base_date_ordinal = _base_date.toordinal()  # Precompute for faster date deserialization


@cython.final
cdef class DateSerializer(XlangCompatibleSerializer):
    cpdef inline write(self, Buffer buffer, value):
        if type(value) is not datetime.date:
            raise TypeError(
                "{} should be {} instead of {}".format(
                    value, datetime.date, type(value)
                )
            )
        days = (value - _base_date).days
        buffer.write_int32(days)

    cpdef inline read(self, Buffer buffer):
        days = buffer.read_int32()
        return datetime.date.fromordinal(_base_date_ordinal + days)


@cython.final
cdef class TimestampSerializer(XlangCompatibleSerializer):
    cdef bint win_platform

    def __init__(self, fory, type_: Union[type, TypeVar]):
        super().__init__(fory, type_)
        self.win_platform = platform.system() == "Windows"

    cdef inline _get_timestamp(self, value):
        seconds_offset = 0
        if self.win_platform and value.tzinfo is None:
            is_dst = time.daylight and time.localtime().tm_isdst > 0
            seconds_offset = time.altzone if is_dst else time.timezone
            value = value.replace(tzinfo=datetime.timezone.utc)
        return int((value.timestamp() + seconds_offset) * 1000000)

    cpdef inline write(self, Buffer buffer, value):
        if type(value) is not datetime.datetime:
            raise TypeError(
                "{} should be {} instead of {}".format(value, datetime, type(value))
            )
        # TimestampType represent micro seconds
        buffer.write_int64(self._get_timestamp(value))

    cpdef inline read(self, Buffer buffer):
        ts = buffer.read_int64() / 1000000
        # TODO support timezone
        return datetime.datetime.fromtimestamp(ts)
