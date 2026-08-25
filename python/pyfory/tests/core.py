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

import types

import pytest

try:
    import pyarrow as pa
except ImportError:
    pa = None


def require_pyarrow(func):
    arrow_not_installed = pa is None or not hasattr(pa, "get_library_dirs")
    mark_decorator = pytest.mark.skipif(arrow_not_installed, reason="pyarrow not installed")(func)
    return mark_decorator


def prepare_pandas_types(fory, frame, pandas):
    classes = {
        pandas.DataFrame,
        type(frame._mgr),
        type,
        type(pandas._libs.internals._unpickle_block),
        type(frame.columns),
        types.FunctionType,
        type(frame.index),
    }
    values = [block.values for block in frame._mgr.blocks]
    values.append(pandas.array([""], dtype="string"))
    classes.update(type(block) for block in frame._mgr.blocks)
    for value in values:
        classes.add(type(value))
        dtype = getattr(value, "dtype", None)
        if dtype is not None:
            classes.add(type(dtype))
        arrow_array = getattr(value, "_pa_array", None)
        if arrow_array is None:
            continue
        classes.add(type(arrow_array))
        classes.add(type(arrow_array.type))
        for chunk in arrow_array.chunks:
            classes.add(type(chunk))
            classes.add(type(chunk.type))
            classes.update(type(buffer) for buffer in chunk.buffers() if buffer is not None)
    for cls in classes:
        fory.type_resolver.get_type_info(cls)
