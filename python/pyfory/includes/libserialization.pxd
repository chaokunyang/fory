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

from libc.stdint cimport int32_t
from libcpp cimport bool as c_bool
from pyfory.includes.libutil cimport CBuffer

cdef extern from "fory/type/type.h" namespace "fory" nogil:

    # Declare the C++ TypeId enum
    cdef enum class TypeId(int32_t):
        UNKNOWN = 0
        BOOL = 1
        INT8 = 2
        INT16 = 3
        INT32 = 4
        VAR32 = 5
        INT64 = 6
        VAR64 = 7
        H64 = 8
        UINT8 = 9
        UINT16 = 10
        UINT32 = 11
        VARU32 = 12
        UINT64 = 13
        VARU64 = 14
        HU64 = 15
        FLOAT16 = 16
        FLOAT32 = 17
        FLOAT64 = 18
        STRING = 19
        ENUM = 20
        NAMED_ENUM = 21
        STRUCT = 22
        COMPATIBLE_STRUCT = 23
        NAMED_STRUCT = 24
        NAMED_COMPATIBLE_STRUCT = 25
        EXT = 26
        NAMED_EXT = 27
        LIST = 28
        SET = 29
        MAP = 30
        DURATION = 31
        TIMESTAMP = 32
        LOCAL_DATE = 33
        DECIMAL = 34
        BINARY = 35
        ARRAY = 36
        BOOL_ARRAY = 37
        INT8_ARRAY = 38
        INT16_ARRAY = 39
        INT32_ARRAY = 40
        INT64_ARRAY = 41
        UINT8_ARRAY = 42
        UINT16_ARRAY = 43
        UINT32_ARRAY = 44
        UINT64_ARRAY = 45
        FLOAT16_ARRAY = 46
        FLOAT32_ARRAY = 47
        FLOAT64_ARRAY = 48
        UNION = 49
        NONE = 50
        BOUND = 64

    cdef c_bool IsNamespacedType(int32_t type_id)
    cdef c_bool IsTypeShareMeta(int32_t type_id)

cdef extern from "fory/python/pyfory.h" namespace "fory":
    int Fory_PyBooleanSequenceWriteToBuffer(object collection, CBuffer *buffer, Py_ssize_t start_index)
    int Fory_PyFloatSequenceWriteToBuffer(object collection, CBuffer *buffer, Py_ssize_t start_index)
