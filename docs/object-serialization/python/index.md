---
title: Python Object Serialization
sidebar_position: 0
id: index
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

**Apache Fory™** is a multi-language serialization framework with Python-native and
cross-language object serialization modes.

`pyfory` provides the Python implementation of Apache Fory™, offering xlang mode for cross-language payloads and native mode for Python-only object serialization.

## Key Features

### Flexible Serialization Modes

- **Xlang mode**: Default cross-language wire format with compatible schema evolution
- **Python native mode**: Same-language mode for a configured Python type surface

### Versatile Serialization Features

- **Reference tracking** for shared xlang schema objects and Python native-mode circular graphs
- **Polymorphism support** for registered customized types
- **Schema evolution** support for backward/forward compatibility when using dataclasses in xlang mode
- **Out-of-band buffer support** for NumPy ndarrays and `pickle.PickleBuffer` values

### Python Runtime Support

- **Runtime code generation** for registered data models
- **Cython-accelerated** core implementation

### Compact Data Size

- **Compact object graph protocol**
- **Meta packing and sharing** to minimize type forward/backward compatibility space overhead

### Security & Safety

- **Strict mode** requires application type registration.
- **Reference tracking** for handling circular references safely

## Installation

### Basic Installation

```bash
pip install pyfory
```

For the optional Apache Arrow dependency and Row APIs, use the
[Python Row Format guide](../../row-format/python.md).

For source development, clone the repository and install the development
extras:

```bash
git clone https://github.com/apache/fory.git
cd fory/python
pip install -e ".[dev]"
```

### Requirements

- **Python**: 3.8 or higher
- **OS**: Linux, macOS, Windows

## Thread Safety

`pyfory` provides `ThreadSafeFory` for thread-safe serialization using a pooled wrapper:

```python
import threading
from dataclasses import dataclass

import pyfory

@dataclass
class Person:
    name: str
    age: int

# Create a thread-safe xlang Fory instance
fory = pyfory.ThreadSafeFory(xlang=True, ref=True)
fory.register(Person)

# Use in multiple threads safely
def serialize_in_thread(thread_id):
    person = Person(name=f"User{thread_id}", age=25 + thread_id)
    data = fory.serialize(person)
    result = fory.deserialize(data)
    print(f"Thread {thread_id}: {result}")

threads = [threading.Thread(target=serialize_in_thread, args=(i,)) for i in range(10)]
for t in threads: t.start()
for t in threads: t.join()
```

**Key Features:**

- **Instance Pool**: Maintains a pool of `Fory` instances protected by a lock for thread safety
- **Shared Configuration**: All registrations must be done upfront and are applied to all instances
- **Matching Operations**: Exposes the corresponding root and registration methods
- **Registration Safety**: The first root attempt permanently freezes registration, even if the
  operation fails

**When to Use:**

- **Multi-threaded Applications**: Web servers, concurrent workers, parallel processing
- **Shared Fory Instances**: When multiple threads need to serialize/deserialize data
- **Thread Pools**: Applications using thread pools or concurrent.futures

## Quick Start

```python
from dataclasses import dataclass

import pyfory

@dataclass
class Person:
    name: str
    age: int

# Create an xlang Fory instance
fory = pyfory.Fory(xlang=True, ref=True)
fory.register(Person)

person = Person("Alice", 30)
data = fory.serialize(person)
result = fory.deserialize(data)
print(result)  # Person(name='Alice', age=30)
```

## Registration Lifecycle

Register every application type before the first root serialization or deserialization attempt. In
native mode, also register callable, class, method, state, and reduction carrier types that can
appear in the object graph. The first root attempt permanently freezes the instance's registry,
including when that attempt fails. Setting `strict=False` may authorize module-global resolution
during a trusted native read, but it does not permit type or serializer registration after that
point.

If the first operation exposes an incomplete or invalid registration, create a new instance and
register the complete type surface before retrying. A fully configured instance can process a later
root after a failure while reading input data or serializing a value. See
[Type Registration](type-registration.md) for the complete lifecycle.

## Xlang Mode And Native Mode

Use xlang mode for cross-language payloads and dataclass schemas shared with other Fory implementations. Xlang mode is the default Python wire mode, and Python examples that use it set `xlang=True` explicitly so the mode choice is visible.

Use native mode for Python-only traffic. Native mode is selected with `xlang=False` and supports a
configured surface that may include functions, lambdas, classes, methods, `__reduce__`,
`__getstate__`, NumPy ndarrays, and out-of-band buffers. Register application types and Python-native
carrier types before the first root attempt. Compatible mode is enabled by default. Set
`compatible=False` only when every reader and writer uses the same Python class schema.

See [Native Serialization](native.md) for Python-only serialization details and [Cross-Language Interoperability](basic-serialization.md#cross-language-interoperability) for Python xlang registration and interoperability rules.

## Next Steps

- [Basic Serialization](basic-serialization.md) - Default xlang APIs and interoperability
- [Native Serialization](native.md) - Python-only serialization
- [Configuration](configuration.md) - Fory parameters, modes, and security
- [Type Registration](type-registration.md) - User-defined type registration
- [Custom Serializers](custom-serializers.md) - Extend serialization behavior
- [Row Format](../../row-format/python.md) - Row-format APIs
- [gRPC Support](../../grpc/python.md) - Fory payloads over grpcio

## Links

- **Documentation**: https://fory.apache.org/docs/object-serialization/python/
- **GitHub**: https://github.com/apache/fory
- **PyPI**: https://pypi.org/project/pyfory/
- **Issues**: https://github.com/apache/fory/issues
- **Slack**: https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw

Before decoding bytes from outside the application trust boundary, read
[Python Security](security.md).
