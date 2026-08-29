---
title: Functions, Classes, and Methods
sidebar_position: 9
id: functions-classes-methods
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

Python native mode serializes Python-specific callable and type values that are outside the xlang
type system. Use `strict=False` only for trusted payloads. Configure a deserialization policy that
authorizes the callable and class references accepted by the application.

Register every callable carrier and application type whose serializer must be installed before the
first root operation. The first serialization or deserialization attempt permanently freezes the
registry, even when it fails. With `strict=False`, the configured policy may still resolve a
module-global function or class while reading a trusted native payload; that resolution does not
install a type or serializer.

## Serialize Global Functions

Functions imported from a module deserialize to the same function object. Register the function
carrier and authorize the expected module-level function before the first root operation:

```python
import statistics
import types

import pyfory
from pyfory import DeserializationPolicy


class TrustedFunctionPolicy(DeserializationPolicy):
    def validate_module(self, module_name, is_local, **kwargs):
        if module_name != "statistics":
            raise ValueError(f"Blocked module: {module_name}")

    def validate_function(self, func, is_local, **kwargs):
        if func is not statistics.mean or is_local:
            raise ValueError(f"Blocked function: {func!r}")


fory = pyfory.Fory(
    xlang=False,
    ref=True,
    strict=False,
    policy=TrustedFunctionPolicy(),
)
fory.register_type(types.FunctionType)
restored = fory.loads(fory.dumps(statistics.mean))
assert restored is statistics.mean
assert restored([10, 20, 30]) == 20
```

## Serialize Local Functions/Lambdas

Serialize functions with closures and lambda expressions. Fory captures the closure variables
automatically:

```python
import types

import pyfory
from pyfory import DeserializationPolicy


class TrustedLocalFunctionPolicy(DeserializationPolicy):
    def authorize_instantiation(self, cls, **kwargs):
        if cls is not types.FunctionType:
            raise ValueError(f"Blocked materialization: {cls!r}")

    def validate_function(self, func, is_local, **kwargs):
        if not is_local or func.__name__ not in {"multiply", "<lambda>"}:
            raise ValueError(f"Blocked function: {func!r}")


fory = pyfory.Fory(
    xlang=False,
    ref=True,
    strict=False,
    policy=TrustedLocalFunctionPolicy(),
)
fory.register_type(types.FunctionType)

def make_multiplier(factor):
    def multiply(value):
        return factor * value

    return multiply


restored = fory.loads(fory.dumps(make_multiplier(10)))
assert restored(10) == 100

restored_lambda = fory.loads(fory.dumps(lambda x: 10 * x))
assert restored_lambda(10) == 100
```

## Serialize Class Objects

Register the `type` carrier before serializing a class object, and authorize class resolution in the
deserialization policy. Register the concrete application class separately when its instances also
appear in the payload:

```python
from collections import Counter

import pyfory
from pyfory import DeserializationPolicy


class TrustedClassPolicy(DeserializationPolicy):
    def validate_module(self, module_name, is_local, **kwargs):
        if module_name != "collections":
            raise ValueError(f"Blocked module: {module_name}")

    def validate_class(self, cls, is_local, **kwargs):
        if cls is not Counter or is_local:
            raise ValueError(f"Blocked class: {cls!r}")


fory = pyfory.Fory(
    xlang=False,
    ref=True,
    strict=False,
    policy=TrustedClassPolicy(),
)
fory.register_type(type)

restored = fory.loads(fory.dumps(Counter))
assert restored is Counter
```

## Serialize Local Classes And Class Methods

Local classes are reconstructed from their definition, so use `ref=True` and a policy that
authorizes construction of the class, its functions, and its bound class methods. Register all
carriers before the first root operation:

```python
import types

import pyfory
from pyfory import DeserializationPolicy


class TrustedLocalTypePolicy(DeserializationPolicy):
    allowed_materialization = {type, types.FunctionType, types.MethodType}

    def authorize_instantiation(self, cls, **kwargs):
        if cls not in self.allowed_materialization:
            raise ValueError(f"Blocked materialization: {cls!r}")

    def validate_class(self, cls, is_local, **kwargs):
        if cls is object and not is_local:
            return
        if not is_local or cls.__name__ != "LocalMessage":
            raise ValueError(f"Blocked class: {cls!r}")

    def validate_function(self, func, is_local, **kwargs):
        if not is_local or func.__name__ != "label":
            raise ValueError(f"Blocked function: {func!r}")

    def validate_method(self, method, is_local, **kwargs):
        if not is_local or method.__name__ != "label":
            raise ValueError(f"Blocked method: {method!r}")


def make_local_class():
    class LocalMessage:
        kind = "local"

        @classmethod
        def label(cls, value):
            return f"{cls.kind}: {value}"

    return LocalMessage


fory = pyfory.Fory(
    xlang=False,
    ref=True,
    strict=False,
    policy=TrustedLocalTypePolicy(),
)
for carrier in (type, types.FunctionType, types.MethodType, staticmethod, classmethod):
    fory.register_type(carrier)

restored = fory.loads(fory.dumps(make_local_class()))
assert restored.label("hello") == "local: hello"
```

## Serialize Methods

Register the method carriers and receiver class before serializing bound instance methods. A
static method is serialized as its underlying function:

```python
import types

import pyfory
from pyfory import DeserializationPolicy


class Calculator:
    def scale(self, x):
        return 3 * x

    @staticmethod
    def double(x):
        return 2 * x


class TrustedMethodPolicy(DeserializationPolicy):
    def authorize_instantiation(self, cls, **kwargs):
        if cls not in (Calculator, types.FunctionType, types.MethodType):
            raise ValueError(f"Blocked materialization: {cls!r}")

    def validate_function(self, func, is_local, **kwargs):
        if func is Calculator.double and not is_local:
            return
        if is_local and func.__qualname__ == "Calculator.double":
            return
        raise ValueError(f"Blocked function: {func!r}")

    def validate_method(self, method, is_local, **kwargs):
        if type(method.__self__) is not Calculator or method.__name__ != "scale":
            raise ValueError(f"Blocked method: {method!r}")


fory = pyfory.Fory(
    xlang=False,
    ref=True,
    strict=False,
    policy=TrustedMethodPolicy(),
)
for carrier in (types.FunctionType, types.MethodType, Calculator):
    fory.register_type(carrier)

assert fory.loads(fory.dumps(Calculator().scale))(10) == 30
assert fory.loads(fory.dumps(Calculator.double))(10) == 20
```
