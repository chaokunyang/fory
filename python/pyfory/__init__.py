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

from pyfory import lib  # noqa: F401 # pylint: disable=unused-import
from pyfory._fory import (  # noqa: F401 # pylint: disable=unused-import
    Fory,
    Language,
)

PYTHON = Language.PYTHON
XLANG = Language.XLANG

try:
    from pyfory._serialization import ENABLE_FORY_CYTHON_SERIALIZATION
except ImportError:
    ENABLE_FORY_CYTHON_SERIALIZATION = False

# Try to import nanobind serialization
try:
    from pyfory.nanobind import ENABLE_FORY_NANOBIND_SERIALIZATION
except ImportError:
    ENABLE_FORY_NANOBIND_SERIALIZATION = False

# Environment variable to enable nanobind serialization
import os
if os.environ.get('ENABLE_FORY_NANOBIND_SERIALIZATION', '0').strip() in ('1', 'true', 'True'):
    ENABLE_FORY_NANOBIND_SERIALIZATION = True

from pyfory._registry import TypeInfo

# Prioritize nanobind over cython if both are available
if ENABLE_FORY_NANOBIND_SERIALIZATION:
    try:
        from pyfory.nanobind import (
            Fory,  # Import C++ backed Fory class
            Buffer,  # Import C++ backed Buffer class
            Language,  # Import Language constants
            MapRefResolver,
            TypeResolver,
            MetaStringResolver,
            TypeInfo,  # Override with nanobind version
        )
        print("Using nanobind-based serialization (experimental)")
    except ImportError as e:
        print(f"Failed to import nanobind serialization: {e}")
        ENABLE_FORY_NANOBIND_SERIALIZATION = False

elif ENABLE_FORY_CYTHON_SERIALIZATION:
    from pyfory._serialization import Fory, TypeInfo  # noqa: F401,F811
    print("Using Cython-based serialization")

from pyfory.serializer import *  # noqa: F401,F403 # pylint: disable=unused-import
from pyfory.type import (  # noqa: F401 # pylint: disable=unused-import
    record_class_factory,
    get_qualified_classname,
    TypeId,
    Int8Type,
    Int16Type,
    Int32Type,
    Int64Type,
    Float32Type,
    Float64Type,
    # Int8ArrayType,
    Int16ArrayType,
    Int32ArrayType,
    Int64ArrayType,
    Float32ArrayType,
    Float64ArrayType,
    dataslots,
)
# Import Buffer - use nanobind version if available, else fallback to _util
if not ENABLE_FORY_NANOBIND_SERIALIZATION:
    from pyfory._util import Buffer  # noqa: F401 # pylint: disable=unused-import

import warnings

# Temporarily commented out to avoid conflicts during nanobind development
# try:
#     with warnings.catch_warnings():
#         warnings.filterwarnings("ignore", category=RuntimeWarning)
#         from pyfory.format import *  # noqa: F401,F403 # pylint: disable=unused-import
# except (AttributeError, ImportError):
#     pass

__version__ = "0.13.0.dev"
