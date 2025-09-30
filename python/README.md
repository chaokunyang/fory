# Apache Fory™ Python

[![Build Status](https://img.shields.io/github/actions/workflow/status/apache/fory/ci.yml?branch=main&style=for-the-badge&label=GITHUB%20ACTIONS&logo=github)](https://github.com/apache/fory/actions/workflows/ci.yml)
[![PyPI](https://img.shields.io/pypi/v/pyfory.svg?logo=PyPI)](https://pypi.org/project/pyfory/)
[![Python Versions](https://img.shields.io/pypi/pyversions/pyfory.svg?logo=python)](https://pypi.org/project/pyfory/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Slack Channel](https://img.shields.io/badge/slack-join-3f0e40?logo=slack&style=for-the-badge)](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw)
[![X](https://img.shields.io/badge/@ApacheFory-follow-blue?logo=x&style=for-the-badge)](https://x.com/ApacheFory)

**Apache Fory™** is a blazing fast multi-language serialization framework powered by **JIT compilation** and **zero-copy** techniques, providing up to **ultra-fast performance** while maintaining ease of use and safety.

`pyfory` provides the Python implementation of Apache Fory™, offering both high-performance object serialization and advanced row-format capabilities for data processing tasks.

## 🚀 Key Features

### 🔧 **Flexible Serialization Modes**

- **Python native Mode**: Full Python compatibility, drop-in replacement for pickle/cloudpickle
- **Cross-Language Mode**: Optimized for multi-language data exchange
- **Row Format**: Zero-copy row format for analytics workloads

### Visalite Serialization Features

- **Shared/circular reference support** for object graph in python-native and cross-Language mode
- **Polymorphicsim support** for customized types.
- **Schema evolution** support for backward/forward compatibility

### ⚡ **Blazing Fast Performance**

- **Extremely fast performance** than other serialization frameworks
- **Runtime code generation** and **Cython-accelerated** core implementation for optimal performance

### Compact Data Size

- **Compatact object graph protocol** with minimal space, can have up to 3X reduction in size compared to pickle/cloudpickle.
- **Meta packing and share** to minimize type forward/backward comaptibility space overhead

### 🛡️ **Security & Safety**

- **Strict mode** prevents deserialization of untrusted types by type registration and checks.
- **Reference tracking** for handling circular references safely

## 📦 Installation

Install pyfory using pip:

```bash
pip install pyfory
```

For development with row format features:

```bash
pip install pyfory[format]
```

## 🏃‍♂️ Quick Start

### Basic Object Serialization

```python
import pyfory

# Create Fory instance
fory = pyfory.Fory()

# Serialize any Python object
data = fory.dumps({"name": "Alice", "age": 30, "scores": [95, 87, 92]})

# Deserialize back to Python object
obj = fory.loads(data)
print(obj)  # {'name': 'Alice', 'age': 30, 'scores': [95, 87, 92]}
```

Note that `serialize/deserialize` API can also be used for serializing and deserializing Python objects. `dumps/loads` are just aliases for `serialize/deserialize`

### Custom Class Serialization

```python
import pyfory
from dataclasses import dataclass
from typing import List, Dict

@dataclass
class Person:
    name: str
    age: int
    scores: List[int]
    metadata: Dict[str, str]

# Python mode - supports all Python types including dataclasses
fory = pyfory.Fory(xlang=False, ref=True)
fory.register(Person)
person = Person("Bob", 25, [88, 92, 85], {"team": "engineering"})
data = fory.serialize(person)
result = fory.deserialize(data)
print(result)  # Person(name='Bob', age=25, ...)
```

### Cross-Language Serialization

Serialize in Python and deserialize in other languages:

**Python (Serializer)**

```python
import pyfory

# Cross-language mode for interoperability
f = pyfory.Fory(xlang=True, ref=True)

# Register type for cross-language compatibility
@dataclass
class Person:
    name: str
    age: int

f.register(Person, typename="example.Person")

person = Person("Charlie", 35)
binary_data = f.serialize(person)
# binary_data can now be sent to Java, Go, etc.
```

**Java (Deserializer)**

```java
import org.apache.fory.*;

public class Person {
    public String name;
    public int age;
}

Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)
    .withRefTracking(true)
    .build();

fory.register(Person.class, "example.Person");
Person person = (Person) fory.deserialize(binaryData);
```

### Drop-in Replacement for Pickle/Cloudpickle

pyfory provides a drop-in replacement for pickle/cloudpickle, offering the same functionality but with **enhanced performance, better compression and security features**.

#### Common Usage

```python
import pyfory

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize common Python objects
data = fory.dumps({"name": "Alice", "age": 30, "scores": [95, 87, 92]})
print(fory.loads(data))

# serialize custom objects
@dataclass
class Person:
    name: str
    age: int

person = Person("Bob", 25)
data = fory.dumps(person)
print(fory.loads(data))
```

#### Serialize Global Functions

```python
import pyfory

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize global functions
def my_global_function(x):
    return 10 * x

data = fory.dumps(my_global_function)
print(fory.loads(data)(10))
```

#### Serialize Local Functions/Lambdas

```python
import pyfory

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize local functions
def my_function():
    local_var = 10
    def loca_func(x):
        return x * local_var
    return loca_func

data = fory.dumps(my_function())
print(fory.loads(data)(10))

# serialize lambdas
data = fory.dumps(lambda x: 10 * x)
print(fory.loads(data)(10))
```

#### Serialize Global Classes/Methods

```python
from dataclasses import dataclass
import pyfory
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize global class
@dataclass
class Person:
    name: str
    age: int

    def f(self, x):
        return self.age * x
    
    @classmethod
    def g(cls, x):
        return 10 * x
    
    @staticmethod
    def h(x):
        return 10 * x

print(fory.loads(fory.dumps(Person))("Bob", 25))
# serialize global class instance method
print(fory.loads(fory.dumps(Person("Bob", 20).f))(10))
# serialize global class class method
print(fory.loads(fory.dumps(Person.g))(10))
# serialize global class static method
print(fory.loads(fory.dumps(Person.h))(10))
```

#### Serialize Local Classes/Methods

```python
from dataclasses import dataclass
import pyfory
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

def create_local_class():
    class LocalClass:
        def f(self, x):
            return 10 * x
        
        @classmethod
        def g(cls, x):
            return 10 * x

        @staticmethod
        def h(x):
            return 10 * x
    return LocalClass

# serialize local class
data = fory.dumps(create_local_class())
print(fory.loads(data)().f(10))

# serialize local class instance method
data = fory.dumps(create_local_class()().f)
print(fory.loads(data)(10))

# serialize local class method
data = fory.dumps(create_local_class().g)
print(fory.loads(data)(10))

# serialize local class static method
data = fory.dumps(create_local_class().h)
print(fory.loads(data)(10))
```

## 🏗️ Core API Reference

### Fory Class

The main serialization interface:

```python
class Fory:
    def __init__(
        self,
        xlang: bool = False,
        ref: bool = False,
        strict: bool = True,
        compatible: bool = False,
        max_depth: int = 50
    )
```

**Parameters:**

- **`xlang`**: Enable cross-language serialization
- **`ref`**: Enable reference tracking for shared/circular references
- **`strict`**: Require type registration for security (recommended: True)
- **`compatible`**: Enable cross-language schema evolution
- **`max_depth`**: Maximum recursion depth for security

**Key Methods:**

```python
# Serialization
data: bytes = fory.serialize(obj)
obj = fory.deserialize(data)

# Type registration by id
fory.register(MyClass, type_id=123)
fory.register(MyClass, type_id=123, serializer=custom_serializer)

# Type registration by name
fory.register(MyClass, typename="my.package.MyClass")
fory.register(MyClass, typename="my.package.MyClass", serializer=custom_serializer)
```

### Language Modes Explained

#### Python Mode (`xlang=False`)

```python
import pyfory

# Full Python compatibility mode
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# Supports ALL Python objects:
data = fory.dumps({
    'function': lambda x: x * 2,        # Functions
    'class': type('Dynamic', (), {}),    # Dynamic classes
    'local_var': locals(),              # Local variables
    'nested': {'circular_ref': None}    # Circular references
})

# Drop-in replacement for pickle/cloudpickle
import pickle
obj = [1, 2, {"nested": [3, 4]}]
assert fory.loads(fory.dumps(obj)) == pickle.loads(pickle.dumps(obj))
```

#### Cross-Language Mode (`xlang=True`)

```python
import pyfory

# Cross-language compatibility mode
f = pyfory.Fory(xlang=True, ref=True)

# Only supports cross-language compatible types
f.register(MyDataClass, typename="com.example.MyDataClass")

# Data can be read by Java, Go, Rust, etc.
data = f.serialize(MyDataClass(field1="value", field2=42))
```

## 📊 Row Format - Zero-Copy Processing

Apache Fury™ provides a random-access row format that enables reading nested fields from binary data without full deserialization. This drastically reduces overhead when working with large objects where only partial data access is needed. The format also supports memory-mapped files for ultra-low memory footprint.

### Basic Row Format Usage

**Python**

```python
import pyfory
import pyarrow as pa
from dataclasses import dataclass
from typing import List, Dict

@dataclass
class Bar:
    f1: str
    f2: List[pa.int64]

@dataclass
class Foo:
    f1: pa.int32
    f2: List[pa.int32]
    f3: Dict[str, pa.int32]
    f4: List[Bar]

# Create encoder for row format
encoder = pyfory.encoder(Foo)

# Create large dataset
foo = Foo(
    f1=10,
    f2=list(range(1_000_000)),
    f3={f"k{i}": i for i in range(1_000_000)},
    f4=[Bar(f1=f"s{i}", f2=list(range(10))) for i in range(1_000_000)]
)

# Encode to row format
binary: bytes = encoder.to_row(foo).to_bytes()

# Zero-copy access - no full deserialization needed!
foo_row = pyfory.RowData(encoder.schema, binary)
print(foo_row.f2[100000])              # Access 100,000th element directly
print(foo_row.f4[100000].f1)           # Access nested field directly
print(foo_row.f4[200000].f2[5])        # Access deeply nested field directly
```

### Cross-Language Compatibility

**Java**

```java
public class Bar {
  String f1;
  List<Long> f2;
}

public class Foo {
  int f1;
  List<Integer> f2;
  Map<String, Integer> f3;
  List<Bar> f4;
}

RowEncoder<Foo> encoder = Encoders.bean(Foo.class);

// Create large dataset
Foo foo = new Foo();
foo.f1 = 10;
foo.f2 = IntStream.range(0, 1_000_000).boxed().collect(Collectors.toList());
foo.f3 = IntStream.range(0, 1_000_000).boxed().collect(Collectors.toMap(i -> "k" + i, i -> i));
List<Bar> bars = new ArrayList<>(1_000_000);
for (int i = 0; i < 1_000_000; i++) {
  Bar bar = new Bar();
  bar.f1 = "s" + i;
  bar.f2 = LongStream.range(0, 10).boxed().collect(Collectors.toList());
  bars.add(bar);
}
foo.f4 = bars;

// Encode to row format (cross-language compatible with Python)
BinaryRow binaryRow = encoder.toRow(foo);

// Zero-copy random access without full deserialization
BinaryArray f2Array = binaryRow.getArray(1);              // Access f2 list
BinaryArray f4Array = binaryRow.getArray(3);              // Access f4 list
BinaryRow bar10 = f4Array.getStruct(10);                  // Access 11th Bar
long value = bar10.getArray(1).getInt64(5);               // Access 6th element of bar.f2

// Partial deserialization - only deserialize what you need
RowEncoder<Bar> barEncoder = Encoders.bean(Bar.class);
Bar bar1 = barEncoder.fromRow(f4Array.getStruct(10));     // Deserialize 11th Bar only
Bar bar2 = barEncoder.fromRow(f4Array.getStruct(20));     // Deserialize 21st Bar only

// Full deserialization when needed
Foo newFoo = encoder.fromRow(binaryRow);
```

**C++**

```cpp
#include "fory/encoder/row_encoder.h"
#include "fory/row/writer.h"

struct Bar {
  std::string f1;
  std::vector<int64_t> f2;
};

FORY_FIELD_INFO(Bar, f1, f2);

struct Foo {
  int32_t f1;
  std::vector<int32_t> f2;
  std::map<std::string, int32_t> f3;
  std::vector<Bar> f4;
};

FORY_FIELD_INFO(Foo, f1, f2, f3, f4);

// Create large dataset
Foo foo;
foo.f1 = 10;
for (int i = 0; i < 1000000; i++) {
  foo.f2.push_back(i);
  foo.f3["k" + std::to_string(i)] = i;
}
for (int i = 0; i < 1000000; i++) {
  Bar bar;
  bar.f1 = "s" + std::to_string(i);
  for (int j = 0; j < 10; j++) {
    bar.f2.push_back(j);
  }
  foo.f4.push_back(bar);
}

// Encode to row format (cross-language compatible with Python/Java)
fory::encoder::RowEncoder<Foo> encoder;
encoder.Encode(foo);
auto row = encoder.GetWriter().ToRow();

// Zero-copy random access without full deserialization
auto f2_array = row->GetArray(1);                    // Access f2 list
auto f4_array = row->GetArray(3);                    // Access f4 list
auto bar10 = f4_array->GetStruct(10);                // Access 11th Bar
int64_t value = bar10->GetArray(1)->GetInt64(5);    // Access 6th element of bar.f2
std::string str = bar10->GetString(0);               // Access bar.f1
```

### Key Benefits

- **Zero-Copy Access**: Read nested fields without deserializing the entire object
- **Memory Efficiency**: Memory-map large datasets directly from disk
- **Cross-Language**: Binary format is compatible between Python, Java, and other Fury implementations
- **Partial Deserialization**: Deserialize only the specific elements you need
- **High Performance**: Skip unnecessary data parsing for analytics and big data workloads

## 🔧 Advanced Features

### Reference Tracking & Circular References

```python
import pyfory

f = pyfory.Fory(ref=True)  # Enable reference tracking

# Handle circular references safely
class Node:
    def __init__(self, value):
        self.value = value
        self.children = []
        self.parent = None

root = Node("root")
child = Node("child")
child.parent = root  # Circular reference
root.children.append(child)

# Serializes without infinite recursion
data = f.serialize(root)
result = f.deserialize(data)
assert result.children[0].parent is result  # Reference preserved
```

### Type Registration & Security

```python
import pyfory

# Strict mode (recommended for production)
f = pyfory.Fory(strict=True)

class SafeClass:
    def __init__(self, data):
        self.data = data

# Must register types in strict mode
f.register(SafeClass, typename="com.example.SafeClass")

# Now serialization works
obj = SafeClass("safe data")
data = f.serialize(obj)
result = f.deserialize(data)

# Unregistered types will raise an exception
class UnsafeClass:
    pass

# This will fail in strict mode
try:
    f.serialize(UnsafeClass())
except Exception as e:
    print("Security protection activated!")
```

### Custom Serializers

```python
import pyfory
from pyfory.serializer import Serializer

class FooSerializer(Serializer):
    def write(self, buffer, obj: Foo):
        buffer.write_varint32(foo.f1)
        buffer.write_string(foo.f2)

    def read(self, buffer):
        return Foo(buffer.read_varint32(), buffer.read_string())

f = pyfory.Fory()
f.register(datetime.datetime, type_id=10, serializer=TimestampSerializer(f, datetime.datetime))
```

### Numpy & Scientific Computing

```python
import pyfory
import numpy as np

f = pyfory.Fory()

# Numpy arrays are supported natively
arrays = {
    'matrix': np.random.rand(1000, 1000),
    'vector': np.arange(10000),
    'bool_mask': np.random.choice([True, False], size=5000)
}

data = f.serialize(arrays)
result = f.deserialize(data)

# Zero-copy for compatible array types
assert np.array_equal(arrays['matrix'], result['matrix'])
```

## 🛠️ Migration Guide

### From Pickle

```python
# Before (pickle)
import pickle
data = pickle.dumps(obj)
result = pickle.loads(data)

# After (Fory - drop-in replacement)
import pyfory
f = pyfory.Fory(xlang=False, ref=True, strict=False)
data = f.dumps(obj)
result = f.loads(data)
```

### From JSON

```python
# Before (JSON - limited types)
import json
data = json.dumps({"name": "Alice", "age": 30})
result = json.loads(data)

# After (Fory - all Python types)
import pyfory
f = pyfory.Fory()
data = f.dumps({"name": "Alice", "age": 30, "func": lambda x: x})
result = f.loads(data)
```

## 🚨 Security Best Practices

### Production Configuration

```python
import pyfory

# Recommended production settings
f = pyfory.Fory(
    xlang=False,   # or True for cross-language
    ref=True,      # Handle circular references
    strict=True,   # IMPORTANT: Prevent milicious data
    max_depth=100  # Prevent deep recursion attacks
)

# Explicitly register allowed types
f.register(UserModel, type_id=100)
f.register(OrderModel, type_id=101)
# Never set strict=False in production with untrusted data!
```

### Development vs Production

```python
# Development (more permissive)
dev_fory = pyfory.Fory(strict=False, ref=True)

# Production (security hardened)
prod_fory = pyfory.Fory(strict=True, ref=True, max_depth=10)

# Register only known safe types in production
for idx, model_class in enumerate([UserModel, ProductModel, OrderModel]):
    prod_fory.register(model_class, type_id=100 + idx)
```

## 🐛 Troubleshooting

### Common Issues

**Q: ImportError with format features**

```python
# A: Install Row format support
pip install pyfory[format]
```

**Q: Slow serialization performance**

```python
# A: Check if Cython acceleration is enabled
import pyfory
print(pyfory.ENABLE_FORY_CYTHON_SERIALIZATION)  # Should be True

# Force enable (if available)
import os
os.environ['ENABLE_FORY_CYTHON_SERIALIZATION'] = '1'
```

**Q: Cross-language compatibility issues**

```python
# A: Use explicit type registration
f = pyfory.Fory(xlang=True)
f.register_type(MyClass, typename="com.package.MyClass")  # Use same name in all languages
```

**Q: Circular reference errors**

```python
# A: Enable reference tracking
f = pyfory.Fory(ref=True)  # Required for circular references
```

### Debug Mode

```python
# Set environment variable before import pyfory to disable Cython for debugging
import os
os.environ['ENABLE_FORY_CYTHON_SERIALIZATION'] = '0'
```

## 🤝 Contributing

Apache Fory™ is an open-source project. Contributions are welcome!

- **GitHub**: https://github.com/apache/fory
- **Issues**: https://github.com/apache/fory/issues
- **Documentation**: https://fory.apache.org/docs/latest/python_guide/
- **Slack**: https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw

### Development Setup

```bash
git clone https://github.com/apache/fory.git
cd fory/python
pip install -e ".[dev,format]"
pytest  # Run tests
```

## 📄 License

Apache License 2.0. See [LICENSE](https://github.com/apache/fory/blob/main/LICENSE) for details.

---

**Apache Fory™** - Blazing fast, secure, and versatile serialization for modern applications.
