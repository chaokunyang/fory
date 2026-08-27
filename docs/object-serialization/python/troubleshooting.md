---
title: Troubleshooting
sidebar_position: 90
id: troubleshooting
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

This page covers common issues and their solutions.

## Common Issues

### ImportError with Format Features

```python
# Solution: Install Row format support
pip install pyfory[format]

# Or install from source with format support
pip install -e ".[format]"
```

### Slow Serialization Performance

```python
# Check if Cython acceleration is enabled
import pyfory
print(pyfory.ENABLE_FORY_CYTHON_SERIALIZATION)  # Should be True

# If False, Cython extension may not be compiled correctly
# Reinstall with: pip install --force-reinstall --no-cache-dir pyfory
```

### Cross-Language Compatibility Issues

```python
# Use explicit type registration with consistent naming
f = pyfory.Fory(xlang=True)
f.register(MyClass, name="com.package.MyClass")  # Use same name in all languages
```

### Circular Reference Errors or Duplicate Data

Registered xlang schema objects and Python native objects both require reference tracking when
object identity or cycles matter:

```python
# Enable reference tracking for registered schema objects.
f = pyfory.Fory(ref=True)
```

For configured Python-native object graphs with circular references, use Python native mode:

```python
from dataclasses import dataclass
from typing import Optional

import pyfory

f = pyfory.Fory(xlang=False, ref=True, strict=False)

# Example with circular reference
@dataclass
class Node:
    value: int
    next: Optional["Node"] = pyfory.field(ref=True, nullable=True, default=None)

f.register_type(Node)

node1 = Node(1)
node2 = Node(2)
node1.next = node2
node2.next = node1  # Circular reference

data = f.dumps(node1)
result = f.loads(data)
assert result.next.next is result  # Circular reference preserved
```

### Schema Evolution Not Working

```python
from dataclasses import dataclass

import pyfory

# Version 1: Original class
@dataclass
class UserV1:
    name: str
    age: pyfory.Int32

writer = pyfory.Fory(xlang=True)
writer.register(UserV1, name="example.User")
data = writer.dumps(UserV1("Alice", 30))

# Version 2: Add new field (backward compatible)
@dataclass
class UserV2:
    name: str
    age: pyfory.Int32
    email: str = "unknown@example.com"  # New field with default

reader = pyfory.Fory(xlang=True)
reader.register(UserV2, name="example.User")
user = reader.loads(data)
print(user.email)  # "unknown@example.com"
```

### Type Registration Errors in Strict Mode

```python
# Register all custom types before serialization
f = pyfory.Fory(strict=True)

# Must register before use
f.register(MyClass, type_id=100)
f.register(AnotherClass, type_id=101)

# Native mode may use strict=False only for trusted data, but application
# and Python-native carrier types still must be registered before use.
native_fory = pyfory.Fory(xlang=False, strict=False)
native_fory.register_type(MyClass)
```

The first root attempt permanently freezes registration, even when it fails. Do not register a
missing type and retry on that same instance. Create a new instance, register the complete type
surface, and retry with the new instance.

## Debug Mode

Set environment variable BEFORE importing pyfory to disable Cython for debugging:

```python
import os
os.environ['ENABLE_FORY_CYTHON_SERIALIZATION'] = '0'
import pyfory  # Now uses pure Python implementation

# This is useful for:
# 1. Debugging protocol issues
# 2. Understanding serialization behavior
# 3. Development without recompiling Cython
```

## Error Handling

Handle common serialization errors gracefully:

```python
from dataclasses import dataclass

import pyfory
from pyfory.error import TypeUnregisteredError

@dataclass
class Message:
    text: str

message = Message("hello")
unconfigured = pyfory.Fory(xlang=False, strict=True, compatible=False)
try:
    unconfigured.dumps(message)
except TypeUnregisteredError as e:
    print(f"Type not registered: {e}")
    # The failed instance is already frozen. Configure a new one.
    fory = pyfory.Fory(xlang=False, strict=True, compatible=False)
    fory.register_type(Message, type_id=100)
    data = fory.dumps(message)

try:
    fory.loads(b"")
except Exception:
    pass

# Root cleanup makes the configured instance reusable after the failed read.
assert fory.loads(data) == message
```

## Development Setup

```bash
git clone https://github.com/apache/fory.git
cd fory/python

# Install dependencies
pip install -e ".[dev,format]"

# Run tests
pytest -v -s .

# Run specific test
pytest -v -s pyfory/tests/test_serializer.py

# Format code
ruff format .
ruff check --fix .
```

## Related Topics

- [Configuration](configuration.md) - Fory parameters
- [Type Registration](type-registration.md) - Registration best practices
- [Configuration](configuration.md#security) - Security configuration
