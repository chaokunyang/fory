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

import array as _py_array
from libc.string cimport memcpy


cdef inline uint16_t _float16_float_to_bits(float value):
    cdef uint32_t bits32
    cdef uint32_t sign
    cdef uint32_t exp
    cdef uint32_t mant
    cdef int32_t new_exp
    cdef uint32_t out_exp
    cdef uint32_t out_mant
    cdef uint32_t full_mant
    cdef int32_t shift
    cdef int32_t net_shift
    cdef uint32_t round_bit
    cdef uint32_t sticky
    memcpy(&bits32, &value, sizeof(float))
    sign = (bits32 >> 16) & 0x8000
    exp = (bits32 >> 23) & 0xFF
    mant = bits32 & 0x7FFFFF

    if exp == 0xFF:
        out_exp = 0x1F
        if mant != 0:
            out_mant = 0x200 | ((mant >> 13) & 0x1FF)
            if out_mant == 0x200:
                out_mant = 0x201
        else:
            out_mant = 0
    elif exp == 0:
        out_exp = 0
        out_mant = 0
    else:
        new_exp = <int32_t>exp - 127 + 15
        if new_exp >= 31:
            out_exp = 0x1F
            out_mant = 0
        elif new_exp <= 0:
            full_mant = mant | 0x800000
            shift = 1 - new_exp
            net_shift = 13 + shift
            if net_shift >= 24:
                out_exp = 0
                out_mant = 0
            else:
                out_exp = 0
                round_bit = (full_mant >> (net_shift - 1)) & 1
                sticky = full_mant & ((1 << (net_shift - 1)) - 1)
                out_mant = full_mant >> net_shift
                if round_bit == 1 and (sticky != 0 or (out_mant & 1) == 1):
                    out_mant += 1
        else:
            out_exp = <uint32_t>new_exp
            out_mant = mant >> 13
            round_bit = (mant >> 12) & 1
            sticky = mant & 0xFFF
            if round_bit == 1 and (sticky != 0 or (out_mant & 1) == 1):
                out_mant += 1
                if out_mant > 0x3FF:
                    out_mant = 0
                    out_exp += 1
                    if out_exp >= 31:
                        out_exp = 0x1F

    return <uint16_t>(sign | (out_exp << 10) | out_mant)


cdef inline float _float16_bits_to_float(uint16_t bits):
    cdef uint32_t sign = (<uint32_t>((bits >> 15) & 0x1)) << 31
    cdef uint32_t exp = (bits >> 10) & 0x1F
    cdef uint32_t mant = bits & 0x3FF
    cdef uint32_t out_bits = sign
    cdef int32_t shift = 0
    cdef float value

    if exp == 0x1F:
        out_bits |= 0xFF << 23
        if mant != 0:
            out_bits |= mant << 13
    elif exp == 0:
        if mant != 0:
            while (mant & 0x400) == 0:
                mant <<= 1
                shift += 1
            mant &= 0x3FF
            out_bits |= <uint32_t>(1 - 15 - shift + 127) << 23
            out_bits |= mant << 13
    else:
        out_bits |= <uint32_t>(exp - 15 + 127) << 23
        out_bits |= mant << 13

    memcpy(&value, &out_bits, sizeof(float))
    return value


cdef inline bint _float16_try_coerce_float(object value, float* out_value):
    try:
        out_value[0] = float(value)
        return True
    except (TypeError, ValueError, OverflowError):
        return False


@cython.final
cdef class float16:
    """Exact IEEE 754 binary16 value carrier with reduced-precision arithmetic operators."""

    cdef uint16_t bits

    def __cinit__(self, value=0.0):
        if isinstance(value, float16):
            self.bits = (<float16>value).bits
        else:
            self.bits = _float16_float_to_bits(<float>value)

    @staticmethod
    def from_bits(bits):
        cdef float16 value = float16.__new__(float16)
        value.bits = <uint16_t>bits
        return value

    @staticmethod
    def from_float(value):
        return float16(value)

    def to_bits(self):
        return self.bits

    def to_float(self):
        return _float16_bits_to_float(self.bits)

    def __float__(self):
        return self.to_float()

    def __int__(self):
        return int(self.to_float())

    def __repr__(self):
        return f"float16(bits=0x{self.bits:04x}, value={self.to_float()!r})"

    def __hash__(self):
        return hash(self.bits)

    def __eq__(self, other):
        if isinstance(other, float16):
            return self.bits == (<float16>other).bits
        return self.to_float() == float(other)

    def __lt__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _float16_bits_to_float(self.bits) < rhs

    def __le__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _float16_bits_to_float(self.bits) <= rhs

    def __gt__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _float16_bits_to_float(self.bits) > rhs

    def __ge__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return _float16_bits_to_float(self.bits) >= rhs

    def __add__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return float16(_float16_bits_to_float(self.bits) + rhs)

    def __radd__(self, other):
        return self.__add__(other)

    def __sub__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return float16(_float16_bits_to_float(self.bits) - rhs)

    def __rsub__(self, other):
        cdef float lhs
        if not _float16_try_coerce_float(other, &lhs):
            return NotImplemented
        return float16(lhs - _float16_bits_to_float(self.bits))

    def __mul__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return float16(_float16_bits_to_float(self.bits) * rhs)

    def __rmul__(self, other):
        return self.__mul__(other)

    def __truediv__(self, other):
        cdef float rhs
        if not _float16_try_coerce_float(other, &rhs):
            return NotImplemented
        return float16(_float16_bits_to_float(self.bits) / rhs)

    def __rtruediv__(self, other):
        cdef float lhs
        if not _float16_try_coerce_float(other, &lhs):
            return NotImplemented
        return float16(lhs / _float16_bits_to_float(self.bits))

    def __neg__(self):
        return float16.from_bits(self.bits ^ 0x8000)

    def __pos__(self):
        return float16.from_bits(self.bits)

    def __abs__(self):
        return float16.from_bits(self.bits & 0x7FFF)


cdef inline uint16_t _coerce_float16_bits(value):
    if isinstance(value, float16):
        return (<float16>value).bits
    return _float16_float_to_bits(<float>value)


@cython.final
cdef class float16array:
    cdef object _data

    def __cinit__(self, values=()):
        self._data = _py_array.array("H")
        self.extend(values)

    @staticmethod
    def from_bits(bits_iterable):
        cdef float16array value = float16array.__new__(float16array)
        value._data = _py_array.array("H", ((int(bits) & 0xFFFF) for bits in bits_iterable))
        return value

    def append(self, value):
        self._data.append(_coerce_float16_bits(value))

    def extend(self, values):
        for value in values:
            self.append(value)

    def to_bits(self):
        return _py_array.array("H", self._data)

    def tolist(self):
        return [float16.from_bits(bits) for bits in self._data]

    def __len__(self):
        return len(self._data)

    def __iter__(self):
        for bits in self._data:
            yield float16.from_bits(bits)

    def __getitem__(self, index):
        if isinstance(index, slice):
            return float16array.from_bits(self._data[index])
        return float16.from_bits(self._data[index])

    def __repr__(self):
        return f"float16array({self.tolist()!r})"

    def __eq__(self, other):
        if isinstance(other, float16array):
            return self._data == (<float16array>other)._data
        try:
            return self.tolist() == list(other)
        except TypeError:
            return False


@cython.final
cdef class Float16Serializer(Serializer):
    cpdef inline write(self, WriteContext write_context, value):
        write_context.write_uint16(_coerce_float16_bits(value))

    cpdef inline read(self, ReadContext read_context):
        return float16.from_bits(read_context.read_uint16())


@cython.final
cdef class Float16ArraySerializer(Serializer):
    cpdef write(self, WriteContext write_context, value):
        cdef float16array safe
        cdef object bits
        if value is None:
            safe = float16array()
        else:
            safe = value
        write_context.write_var_uint32(len(safe) * 2)
        for bits in safe._data:
            write_context.write_uint16(bits)

    cpdef read(self, ReadContext read_context):
        cdef uint32_t payload_size = read_context.read_var_uint32()
        cdef uint32_t i
        cdef float16array values
        if payload_size & 1:
            raise ValueError("float16 array payload size mismatch")
        values = float16array()
        for i in range(payload_size // 2):
            values._data.append(read_context.read_uint16())
        return values
