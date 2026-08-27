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
type system. Use `strict=False` only for trusted payloads and apply a deserialization policy when
the accepted dynamic surface must be restricted.

Register every callable carrier and application type whose serializer must be installed before the
first root operation. The first serialization or deserialization attempt permanently freezes the
registry, even when it fails. With `strict=False`, the configured policy may still resolve a
module-global function or class while reading a trusted native payload; that resolution does not
install a type or serializer.

## Serialize Global Functions

Capture and serialize functions defined at module level. Fory deserializes and returns the same
function object:

```python
import pyfory
import types

fory = pyfory.Fory(xlang=False, ref=True, strict=False)

def my_global_function(x):
    return 10 * x

fory.register_type(types.FunctionType)
data = fory.dumps(my_global_function)
print(fory.loads(data)(10))  # 100
```

## Serialize Local Functions/Lambdas

Serialize functions with closures and lambda expressions. Fory captures the closure variables
automatically:

```python
import pyfory
import types

fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# Local functions with closures
def my_function():
    local_var = 10
    def local_func(x):
        return x * local_var
    return local_func

fory.register_type(types.FunctionType)
data = fory.dumps(my_function())
print(fory.loads(data)(10))  # 100

# Lambdas
data = fory.dumps(lambda x: 10 * x)
print(fory.loads(data)(10))  # 100
```

## Serialize Methods

Register the method carriers and receiver class before serializing bound instance methods. A
static method is serialized as its underlying function:

```python
import pyfory
import types

fory = pyfory.Fory(xlang=False, ref=True, strict=False)

class Calculator:
    def scale(self, x):
        return 3 * x

    @staticmethod
    def double(x):
        return 2 * x

for carrier in (types.FunctionType, types.MethodType, Calculator):
    fory.register_type(carrier)

# Serialize instance method
print(fory.loads(fory.dumps(Calculator().scale))(10))  # 30

# Serialize static method
print(fory.loads(fory.dumps(Calculator.double))(10))  # 20
```
