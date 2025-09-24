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
Fury nanobind-based high-performance serialization module.

This module provides a C++ implementation of Fury's serialization system
using nanobind for Python bindings, targeting 100%+ performance improvement
over the Cython implementation.

All classes and functions are implemented in C++ and imported from the
_nanobind_serialization.so compiled module.
"""

try:
    from . import _nanobind_serialization
    ENABLE_FORY_NANOBIND_SERIALIZATION = getattr(_nanobind_serialization, 'ENABLE_FORY_NANOBIND_SERIALIZATION', True)
except ImportError:
    _nanobind_serialization = None
    ENABLE_FORY_NANOBIND_SERIALIZATION = False

# Import all classes directly from the C++ compiled module
if ENABLE_FORY_NANOBIND_SERIALIZATION and _nanobind_serialization:
    # Core serialization classes - all implemented in C++
    Fory = _nanobind_serialization.Fory
    Buffer = _nanobind_serialization.Buffer

    # Reference and type resolution - all implemented in C++
    MapRefResolver = _nanobind_serialization.MapRefResolver
    TypeResolver = _nanobind_serialization.TypeResolver
    MetaStringResolver = _nanobind_serialization.MetaStringResolver
    TypeInfo = _nanobind_serialization.TypeInfo

    # Language constants - from C++
    Language = _nanobind_serialization.Language

    # Export constants from C++ module
    NULL_FLAG = _nanobind_serialization.NULL_FLAG
    REF_FLAG = _nanobind_serialization.REF_FLAG
    NOT_NULL_VALUE_FLAG = _nanobind_serialization.NOT_NULL_VALUE_FLAG
    REF_VALUE_FLAG = _nanobind_serialization.REF_VALUE_FLAG
    SMALL_STRING_THRESHOLD = _nanobind_serialization.SMALL_STRING_THRESHOLD

    # Legacy aliases
    PyBuffer = Buffer  # For backward compatibility

else:
    # Provide error stubs when nanobind is not available
    def _create_error_stub(name):
        def __init__(self, *args, **kwargs):
            raise ImportError(f"nanobind serialization not available - {name} module failed to load")
        return type(name, (), {"__init__": __init__})

    Fory = _create_error_stub("Fory")
    Buffer = _create_error_stub("Buffer")
    PyBuffer = _create_error_stub("PyBuffer")
    MapRefResolver = _create_error_stub("MapRefResolver")
    TypeResolver = _create_error_stub("TypeResolver")
    MetaStringResolver = _create_error_stub("MetaStringResolver")
    TypeInfo = _create_error_stub("TypeInfo")
    Language = _create_error_stub("Language")

    # Default constants
    NULL_FLAG = -3
    REF_FLAG = -2
    NOT_NULL_VALUE_FLAG = -1
    REF_VALUE_FLAG = 0
    SMALL_STRING_THRESHOLD = 32

__all__ = [
    'ENABLE_FORY_NANOBIND_SERIALIZATION',
    'Fory',
    'Buffer',
    'PyBuffer',
    'MapRefResolver',
    'TypeResolver',
    'MetaStringResolver',
    'TypeInfo',
    'Language',
    'NULL_FLAG',
    'REF_FLAG',
    'NOT_NULL_VALUE_FLAG',
    'REF_VALUE_FLAG',
    'SMALL_STRING_THRESHOLD',
]