---
title: Out-of-Band Serialization
sidebar_position: 11
id: out-of-band
license: |
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
---

Fory can separate supported binary storage from the main serialized bytes through an out-of-band
buffer callback. Python native mode supports this flow for NumPy ndarrays and
`pickle.PickleBuffer` values.

## Overview

Out-of-band serialization separates the Fory root bytes from selected buffers:

- `BufferObject.getbuffer()` exposes a `memoryview`; contiguous NumPy storage can be exposed without
  an additional copy.
- The application transports the root bytes and out-of-band buffers together and in order.
- `BufferObject.write_to()` writes a selected buffer to a writable stream.

`numpy.ndarray` and `pickle.PickleBuffer` are built-in native types. If an application wrapper or
an object-dtype ndarray can contain custom values, register every application and Python-native
carrier type before the first root attempt. That first attempt permanently freezes registration,
including when it fails; `strict=False` does not permit late discovery.

## Basic Out-of-Band Serialization

```python
import numpy as np
import pyfory

fory = pyfory.Fory(xlang=False, ref=False)

# Large numpy array
array = np.arange(10000, dtype=np.float64)

# Serialize with out-of-band buffers
buffer_objects = []
serialized_data = fory.serialize(array, buffer_callback=buffer_objects.append)

# Convert collected buffer objects to memoryviews for transport.
buffers = [obj.getbuffer() for obj in buffer_objects]

# Deserialize with out-of-band buffers (accepts memoryview, bytes, or Buffer)
deserialized_array = fory.deserialize(serialized_data, buffers=buffers)

assert np.array_equal(array, deserialized_array)
```

## Selective Out-of-Band Serialization

Control which buffers go out-of-band by providing a callback that returns `True` to keep data in-band or `False` to send it out-of-band:

```python
import numpy as np
import pyfory

fory = pyfory.Fory(xlang=False, ref=True)

arr1 = np.arange(1000, dtype=np.float64)
arr2 = np.arange(2000, dtype=np.float64)
data = [arr1, arr2]

buffer_objects = []

def selective_callback(buffer_object):
    # Send buffers of at least 12,000 bytes out-of-band.
    if buffer_object.total_bytes() >= 12_000:
        buffer_objects.append(buffer_object)
        return False
    return True

serialized = fory.serialize(data, buffer_callback=selective_callback)
buffers = [obj.getbuffer() for obj in buffer_objects]
deserialized = fory.deserialize(serialized, buffers=buffers)

assert np.array_equal(arr1, deserialized[0])
assert np.array_equal(arr2, deserialized[1])
```

## `pickle.PickleBuffer` Values

Python native mode accepts `pickle.PickleBuffer` as a built-in value. The outer bytes remain Fory
native bytes; they are not Pickle wire data.

```python
import pickle

import pyfory

fory = pyfory.Fory(xlang=False, ref=False)

# PickleBuffer objects are automatically supported
data = b"Large binary data"
pickle_buffer = pickle.PickleBuffer(data)

# Serialize with buffer callback for out-of-band handling
buffer_objects = []
serialized = fory.serialize(pickle_buffer, buffer_callback=buffer_objects.append)
buffers = [obj.getbuffer() for obj in buffer_objects]

# Deserialize with buffers
deserialized = fory.deserialize(serialized, buffers=buffers)
assert bytes(deserialized.raw()) == data
```

## Writing A Buffer To A Stream

The `BufferObject.write_to()` method accepts a writable stream object:

```python
import io

import numpy as np
import pyfory

fory = pyfory.Fory(xlang=False, ref=False)

array = np.arange(1000, dtype=np.float64)

# Collect out-of-band buffers
buffer_objects = []
serialized = fory.serialize(array, buffer_callback=buffer_objects.append)

# Write to an in-memory stream and obtain a memoryview.
for buffer_obj in buffer_objects:
    bytes_stream = io.BytesIO()
    buffer_obj.write_to(bytes_stream)
    assert bytes_stream.getvalue() == array.tobytes()
    mv = buffer_obj.getbuffer()
    assert isinstance(mv, memoryview)
```

For a contiguous NumPy ndarray, `getbuffer()` can expose the existing storage. A non-contiguous
array may be copied to produce a contiguous transport buffer.

## Related Topics

- [NumPy Integration](numpy-integration.md) - NumPy array serialization
- [Basic Serialization](basic-serialization.md) - Standard serialization
- [Configuration](configuration.md) - Fory parameters
