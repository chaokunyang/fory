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

import pickle
import pytest
import pyfory
from pyfory import Fory


try:
    import numpy as np
except ImportError:
    np = None

try:
    import pandas as pd
except ImportError:
    pd = None


def test_pickle_buffer_serialization():
    fory = Fory(xlang=False, ref=False, strict=False)

    data = b"Hello, PickleBuffer!"
    pickle_buffer = pickle.PickleBuffer(data)

    serialized = fory.serialize(pickle_buffer)
    deserialized = fory.deserialize(serialized)

    assert isinstance(deserialized, pickle.PickleBuffer)
    assert bytes(deserialized.raw()) == data


@pytest.mark.skipif(np is None, reason="Requires numpy")
def test_numpy_out_of_band_serialization():
    fory = Fory(xlang=False, ref=False, strict=False)

    arr = np.arange(10000, dtype=np.float64)

    buffer_objects = []
    serialized = fory.serialize(arr, buffer_callback=buffer_objects.append)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    np.testing.assert_array_equal(arr, deserialized)


@pytest.mark.skipif(pd is None, reason="Requires pandas")
def test_pandas_out_of_band_serialization():
    fory = Fory(xlang=False, ref=False, strict=False)

    df = pd.DataFrame({
        'a': np.arange(1000, dtype=np.float64),
        'b': np.arange(1000, dtype=np.int64),
        'c': ['text'] * 1000
    })

    buffer_objects = []
    serialized = fory.serialize(df, buffer_callback=buffer_objects.append)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    pd.testing.assert_frame_equal(df, deserialized)


@pytest.mark.skipif(np is None, reason="Requires numpy")
def test_numpy_multiple_arrays_out_of_band():
    fory = Fory(xlang=False, ref=True, strict=False)

    arr1 = np.arange(5000, dtype=np.float32)
    arr2 = np.arange(3000, dtype=np.int32)
    arr3 = np.arange(2000, dtype=np.float64)

    data = [arr1, arr2, arr3]

    buffer_objects = []
    serialized = fory.serialize(data, buffer_callback=buffer_objects.append)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    assert len(deserialized) == 3
    np.testing.assert_array_equal(arr1, deserialized[0])
    np.testing.assert_array_equal(arr2, deserialized[1])
    np.testing.assert_array_equal(arr3, deserialized[2])


@pytest.mark.skipif(np is None, reason="Requires numpy")
def test_numpy_with_mixed_types():
    fory = Fory(xlang=False, ref=True, strict=False)

    arr = np.arange(1000, dtype=np.float64)
    text = "some text"
    number = 42

    data = {"array": arr, "text": text, "number": number}

    buffer_objects = []
    serialized = fory.serialize(data, buffer_callback=buffer_objects.append)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    assert deserialized["text"] == text
    assert deserialized["number"] == number
    np.testing.assert_array_equal(arr, deserialized["array"])


@pytest.mark.skipif(pd is None or np is None, reason="Requires numpy and pandas")
def test_mixed_numpy_pandas_out_of_band():
    fory = Fory(xlang=False, ref=True, strict=False)

    arr = np.arange(500, dtype=np.float64)
    df = pd.DataFrame({
        'x': np.arange(500, dtype=np.int64),
        'y': np.arange(500, dtype=np.float32)
    })

    data = {"array": arr, "dataframe": df}

    buffer_objects = []
    serialized = fory.serialize(data, buffer_callback=buffer_objects.append)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    np.testing.assert_array_equal(arr, deserialized["array"])
    pd.testing.assert_frame_equal(df, deserialized["dataframe"])


@pytest.mark.skipif(np is None, reason="Requires numpy")
def test_selective_out_of_band_serialization():
    fory = Fory(xlang=False, ref=True, strict=False)

    arr1 = np.arange(1000, dtype=np.float64)
    arr2 = np.arange(1000, dtype=np.float64)

    data = [arr1, arr2]

    buffer_objects = []
    counter = 0

    def selective_buffer_callback(buffer_object):
        nonlocal counter
        counter += 1
        if counter % 2 == 0:
            buffer_objects.append(buffer_object)
            return False
        else:
            return True

    serialized = fory.serialize(data, buffer_callback=selective_buffer_callback)

    buffers = [o.to_buffer() for o in buffer_objects]

    deserialized = fory.deserialize(serialized, buffers=buffers)

    assert len(deserialized) == 2
    np.testing.assert_array_equal(arr1, deserialized[0])
    np.testing.assert_array_equal(arr2, deserialized[1])
