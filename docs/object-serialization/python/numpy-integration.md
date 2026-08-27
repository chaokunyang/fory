---
title: NumPy
sidebar_position: 12
id: numpy-integration
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

Python native mode supports NumPy ndarrays as built-in values.

## NumPy Array Serialization

Serialize and deserialize an ndarray directly:

```python
import numpy as np
import pyfory

fory = pyfory.Fory(xlang=False)

# Numpy arrays are supported natively
arrays = {
    "matrix": np.arange(12, dtype=np.float64).reshape(3, 4),
    "vector": np.arange(10, dtype=np.int64),
    "bool_mask": np.array([True, False, True]),
}

data = fory.serialize(arrays)
result = fory.deserialize(data)

assert np.array_equal(arrays["matrix"], result["matrix"])
```

The ndarray carrier itself is available when the instance is created. Register application types
that contain ndarrays, plus every custom type that can appear inside an object-dtype ndarray, before
the first root attempt. The first root attempt permanently freezes registration, including when it
fails. `strict=False` does not permit late type or serializer registration.

## Out-of-Band Buffers

Use a buffer callback to transport ndarray storage separately from the root bytes:

```python
import numpy as np
import pyfory

fory = pyfory.Fory(xlang=False, ref=False)

array = np.arange(10000, dtype=np.float64).reshape(100, 100)

buffer_objects = []
data = fory.serialize(array, buffer_callback=buffer_objects.append)
buffers = [obj.getbuffer() for obj in buffer_objects]

result = fory.deserialize(data, buffers=buffers)
assert np.array_equal(array, result)
```

For a contiguous ndarray, `getbuffer()` can expose the existing storage as a `memoryview`. A
non-contiguous ndarray may be copied to create a contiguous transport buffer. The application must
send all collected buffers with the root bytes and provide them to `deserialize` in the same order.

## Related Topics

- [Out-of-Band Serialization](out-of-band.md) - Buffer callback APIs
- [Basic Serialization](basic-serialization.md) - Standard usage
