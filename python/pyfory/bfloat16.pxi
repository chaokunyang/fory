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
Cython-only public carrier types for xlang bfloat16 values and one-dimensional arrays.

The compiled ``pyfory.serialization`` extension provides these names directly. There is no
pure-Python fallback module for the bfloat16 API surface.
"""

import array as _py_array
from libc.string cimport memcpy


cdef inline uint16_t _bfloat16_float_to_bits(float value):
    cdef uint32_t bits32
    cdef uint32_t lsb
    memcpy(&bits32, &value, sizeof(float))
    if (bits32 & 0x7F800000) == 0x7F800000 and (bits32 & 0x007FFFFF) != 0:
        return <uint16_t>0x7FC0
    lsb = (bits32 >> 16) & 1
    return <uint16_t>(((bits32 + 0x7FFF + lsb) >> 16) & 0xFFFF)


cdef inline float _bfloat16_bits_to_float(uint16_t bits):
    cdef uint32_t bits32 = (<uint32_t>bits) << 16
    cdef float value
    memcpy(&value, &bits32, sizeof(float))
    return value


cdef inline bint _bfloat16_try_coerce_float(object value, float* out_value):
    try:
        out_value[0] = float(value)
        return True
    except (TypeError, ValueError, OverflowError):
        return False


@cython.final
cdef class bfloat16:
    """Exact IEEE 754 bfloat16 value carrier with reduced-precision arithmetic operators."""

    cdef uint16_t bits

    def __cinit__(self, value=0.0):
        if isinstance(value, bfloat16):
            self.bits = (<bfloat16>value).bits
        else:
            self.bits = _bfloat16_float_to_bits(<float>value)

    @staticmethod
    def from_bits(bits):
        cdef bfloat16 value = bfloat16.__new__(bfloat16)
        value.bits = <uint16_t>bits
        return value

    @staticmethod
    def from_float(value):
        return bfloat16(value)

    def to_bits(self):
        return self.bits

    def to_float(self):
        return _bfloat16_bits_to_float(self.bits)

    def __float__(self):
        return self.to_float()

    def __int__(self):
        return int(self.to_float())

    def __repr__(self):
        return f"bfloat16(bits=0x{self.bits:04x}, value={self.to_float()!r})"

    def __hash__(self):
        return hash(self.bits)

    def __eq__(self, other):
        if isinstance(other, bfloat16):
            return self.bits == (<bfloat16>other).bits
        return self.to_float() == float(other)

    def __lt__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _bfloat16_bits_to_float(self.bits) < rhs

    def __le__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _bfloat16_bits_to_float(self.bits) <= rhs

    def __gt__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _bfloat16_bits_to_float(self.bits) > rhs

    def __ge__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _bfloat16_bits_to_float(self.bits) >= rhs

    def __add__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return bfloat16(_bfloat16_bits_to_float(self.bits) + rhs)

    def __radd__(self, other):
        return self.__add__(other)

    def __sub__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return bfloat16(_bfloat16_bits_to_float(self.bits) - rhs)

    def __rsub__(self, other):
        cdef float lhs
        if not _bfloat16_try_coerce_float(other, &lhs):
            return NotImplemented
        return bfloat16(lhs - _bfloat16_bits_to_float(self.bits))

    def __mul__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return bfloat16(_bfloat16_bits_to_float(self.bits) * rhs)

    def __rmul__(self, other):
        return self.__mul__(other)

    def __truediv__(self, other):
        cdef float rhs
        if not _bfloat16_try_coerce_float(other, &rhs):
            return NotImplemented
        return bfloat16(_bfloat16_bits_to_float(self.bits) / rhs)

    def __rtruediv__(self, other):
        cdef float lhs
        if not _bfloat16_try_coerce_float(other, &lhs):
            return NotImplemented
        return bfloat16(lhs / _bfloat16_bits_to_float(self.bits))

    def __neg__(self):
        return bfloat16.from_bits(self.bits ^ 0x8000)

    def __pos__(self):
        return bfloat16.from_bits(self.bits)

    def __abs__(self):
        return bfloat16.from_bits(self.bits & 0x7FFF)


cdef inline uint16_t _coerce_bfloat16_bits(value):
    if isinstance(value, bfloat16):
        return (<bfloat16>value).bits
    return _bfloat16_float_to_bits(<float>value)


@cython.final
cdef class bfloat16array:
    """Packed one-dimensional carrier for xlang ``bfloat16_array`` payloads."""

    cdef object _data

    def __cinit__(self, values=()):
        self._data = _py_array.array("H")
        self.extend(values)

    @staticmethod
    def from_bits(bits_iterable):
        cdef bfloat16array value = bfloat16array.__new__(bfloat16array)
        value._data = _py_array.array("H", ((int(bits) & 0xFFFF) for bits in bits_iterable))
        return value

    def append(self, value):
        self._data.append(_coerce_bfloat16_bits(value))

    def extend(self, values):
        for value in values:
            self.append(value)

    def to_bits(self):
        return _py_array.array("H", self._data)

    def tolist(self):
        return [bfloat16.from_bits(bits) for bits in self._data]

    def __len__(self):
        return len(self._data)

    def __iter__(self):
        for bits in self._data:
            yield bfloat16.from_bits(bits)

    def __getitem__(self, index):
        if isinstance(index, slice):
            return bfloat16array.from_bits(self._data[index])
        return bfloat16.from_bits(self._data[index])

    def __repr__(self):
        return f"bfloat16array({self.tolist()!r})"

    def __eq__(self, other):
        if isinstance(other, bfloat16array):
            return self._data == (<bfloat16array>other)._data
        try:
            return self.tolist() == list(other)
        except TypeError:
            return False


@cython.final
cdef class BFloat16Serializer(Serializer):
    """Serializer for xlang ``bfloat16`` scalar values."""

    cpdef inline write(self, WriteContext write_context, value):
        write_context.write_uint16(_coerce_bfloat16_bits(value))

    cpdef inline read(self, ReadContext read_context):
        return bfloat16.from_bits(read_context.read_uint16())


@cython.final
cdef class BFloat16ArraySerializer(Serializer):
    """Serializer for xlang ``bfloat16_array`` payloads."""

    cpdef write(self, WriteContext write_context, value):
        cdef bfloat16array safe
        cdef object bits
        if value is None:
            safe = bfloat16array()
        else:
            safe = value
        write_context.write_var_uint32(len(safe) * 2)
        for bits in safe._data:
            write_context.write_uint16(bits)

    cpdef read(self, ReadContext read_context):
        cdef uint32_t payload_size = read_context.read_var_uint32()
        cdef uint32_t i
        cdef bfloat16array values
        if payload_size & 1:
            raise ValueError("bfloat16 array payload size mismatch")
        values = bfloat16array()
        for i in range(payload_size // 2):
            values._data.append(read_context.read_uint16())
        return values
