# Apache Fory™ Python

[![Build Status](https://img.shields.io/github/actions/workflow/status/apache/fory/ci.yml?branch=main&style=for-the-badge&label=GITHUB%20ACTIONS&logo=github)](https://github.com/apache/fory/actions/workflows/ci.yml)
[![PyPI](https://img.shields.io/pypi/v/pyfory.svg?logo=PyPI&style=for-the-badge)](https://pypi.org/project/pyfory/)
[![Python Versions](https://img.shields.io/pypi/pyversions/pyfory.svg?logo=python&style=for-the-badge)](https://pypi.org/project/pyfory/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)
[![Slack Channel](https://img.shields.io/badge/slack-join-3f0e40?logo=slack&style=for-the-badge)](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw)
[![X](https://img.shields.io/badge/@ApacheFory-follow-blue?logo=x&style=for-the-badge)](https://x.com/ApacheFory)

`pyfory` is the Python implementation of Apache Fory™. It provides Python-native and cross-language
object serialization together with row-format APIs for analytical data.

## Key Features

### **Flexible Serialization Modes**

- **Xlang mode**: Default cross-language wire format with compatible schema evolution
- **Python native mode**: Same-language mode for configured Python type surfaces
- **Row Format**: Random and partial access to analytical row data

### Versatile Serialization Features

- **Shared/circular reference support** for complex object graphs in both Python native and xlang modes
- **Polymorphism support** for customized types with automatic type dispatching
- **Schema evolution** support for backward/forward compatibility when using dataclasses in xlang mode
- **Out-of-band buffer support** for NumPy arrays and `pickle.PickleBuffer` values
- **Reduced-precision xlang types** use reserved `pyfory.Float16` and `pyfory.BFloat16` annotations and native Python `float` values; dense array payloads use public wrappers such as `Float16Array` and `BFloat16Array`

### Python Runtime Implementation

- **Runtime code generation** for supported Python classes
- **Cython-accelerated** core implementation

### Compact Data Size

- **Compact object graph protocol** for Python-native and cross-language payloads
- **Meta packing and sharing** to minimize type forward/backward compatibility space overhead

### **Security & Safety**

- **Strict mode** limits application class loading to the configured registration surface.
- **Reference tracking** for handling circular references safely

## Installation

### Basic Installation

Install pyfory using pip:

```bash
pip install pyfory
```

### Optional Dependencies

```bash
# Install with row format support (requires Apache Arrow)
pip install pyfory[format]

# Install from source for development
git clone https://github.com/apache/fory.git
cd fory/python
pip install -e ".[dev,format]"
```

### Requirements

- **Python**: 3.8 or higher
- **OS**: Linux, macOS, Windows

## Python Native Serialization

`pyfory` provides a Python native mode for configured Python-only payloads, with support for
functions, methods, dataclasses, stateful types, and reduction hooks.

Register every application type and native carrier before the first root operation. The first
serialization or deserialization attempt permanently freezes that `Fory` instance's registry,
including when the attempt fails.

To use Python native mode, create `Fory` with `xlang=False`. Use this mode when replacing pickle or
cloudpickle for pure Python applications:

```python
import pyfory
fory = pyfory.Fory(xlang=False, ref=False, strict=True)
```

## Xlang Object Serialization

### Basic Object Serialization

Serialize and deserialize Python objects with a simple API. This example shows serializing a dictionary with mixed types:

```python
import pyfory

# Create an xlang Fory instance.
fory = pyfory.Fory(xlang=True)

# Serialize xlang-compatible values
data = fory.dumps({"name": "Alice", "age": 30, "scores": [95, 87, 92]})

# Deserialize back to Python object
obj = fory.loads(data)
print(obj)  # {'name': 'Alice', 'age': 30, 'scores': [95, 87, 92]}
```

**Note**: `dumps()`/`loads()` are aliases for `serialize()`/`deserialize()`. Both APIs are identical, use whichever feels more intuitive.

### Custom Class Serialization

Fory automatically handles dataclasses and custom types. Register your class once, then serialize instances seamlessly:

```python
import pyfory
from dataclasses import dataclass
from typing import List, Dict

@dataclass
class Person:
    name: str
    age: pyfory.Int32
    scores: List[pyfory.Int32]
    metadata: Dict[str, str]

fory = pyfory.Fory(xlang=True, ref=True)
fory.register(Person, name="example.Person")
person = Person("Bob", 25, [88, 92, 85], {"team": "engineering"})
data = fory.serialize(person)
result = fory.deserialize(data)
print(result)  # Person(name='Bob', age=25, ...)
```

## Pickle-Style Python-Native Serialization

`pyfory` can serialize a configured Python type surface with the following options:

- **For circular references**: Set `ref=True` to enable reference tracking
- **For Python-native carriers**: Set `strict=False` only for trusted payloads

Register every application type and native carrier type before the first root serialization or
deserialization call. The first root operation permanently freezes that `Fory` instance's registry,
even when the operation fails. `strict=False` may authorize module-global resolution while reading
a trusted native payload, but it does not permit late type or serializer registration. If the first
operation exposes incomplete or invalid registration, configure a new instance before retrying.

**Security Warning**: Configured native carriers can import modules and construct Python objects
when `strict=False`. Use this mode only with trusted payloads, and provide a
`DeserializationPolicy` through `policy=` when the accepted surface must be restricted.

### Common Usage

Built-in containers require no registration. Register custom classes before the first root:

```python
import pyfory

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

from dataclasses import dataclass

@dataclass
class Person:
    name: str
    age: int

fory.register_type(Person)

# serialize common Python objects
data = fory.dumps({"name": "Alice", "age": 30, "scores": [95, 87, 92]})
print(fory.loads(data))

# serialize custom objects
person = Person("Bob", 25)
data = fory.dumps(person)
print(fory.loads(data))  # Person(name='Bob', age=25)
```

### Serialize Global Functions

Capture and get functions defined at module level. Fory deserialize and return same function object:

```python
import pyfory
import types

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize global functions
def my_global_function(x):
    return 10 * x

fory.register_type(types.FunctionType)
data = fory.dumps(my_global_function)
print(fory.loads(data)(10))  # 100
```

#### Serialize Local Functions/Lambdas

Serialize functions with closures and lambda expressions. Fory captures the closure variables automatically:

```python
import pyfory
import types

# Create Fory instance
fory = pyfory.Fory(xlang=False, ref=True, strict=False)

# serialize local functions with closures
def my_function():
    local_var = 10
    def local_func(x):
        return x * local_var
    return local_func

fory.register_type(types.FunctionType)
data = fory.dumps(my_function())
print(fory.loads(data)(10))  # 100

# serialize lambdas
data = fory.dumps(lambda x: 10 * x)
print(fory.loads(data)(10))  # 100
```

#### Serialize Methods

Register method carriers and receiver classes before serializing bound instance methods. A static
method is serialized as its underlying function:

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

print(fory.loads(fory.dumps(Calculator().scale))(10))  # 30
print(fory.loads(fory.dumps(Calculator.double))(10))  # 20
```

### Out-of-Band Buffer Serialization

Python native mode can separate supported NumPy ndarray and `pickle.PickleBuffer` storage from the
root bytes through `buffer_callback`. Transport the collected buffers with the root bytes and pass
them to `deserialize` in the same order. For contiguous storage, `BufferObject.getbuffer()` can
expose a `memoryview` without an additional source-side copy; non-contiguous storage may be copied.
This does not promise copy-free transport or decoding.

See [Out-of-Band Buffers](https://fory.apache.org/docs/object-serialization/python/out-of-band) for
the callback, transport, and stream APIs.

## Cross-Language Object Graph Serialization

`pyfory` supports cross-language object graph serialization, allowing you to serialize data in Python and deserialize it in Java, Go, Rust, or other supported languages.

The binary protocol and API are similar to `pyfory`'s Python native mode. Python-specific callable,
stateful, and reduction carriers are available only in native mode and must be registered before
the first root operation.

Xlang mode is the default. Set `xlang=True` explicitly in cross-language examples so the mode choice is visible:

```python
import pyfory
fory = pyfory.Fory(xlang=True, ref=False, strict=True)
```

### Cross-Language Serialization

Serialize data in Python and deserialize it in Java, Go, Rust, or other supported languages. Both sides must register the same type with matching names:

**Python (Serializer)**

```python
from dataclasses import dataclass
import pyfory

# Xlang mode for interoperability
f = pyfory.Fory(xlang=True, ref=True)

# Register type for cross-language compatibility
@dataclass
class Person:
    name: str
    age: pyfory.Int32

f.register(Person, name="example.Person")

person = Person("Charlie", 35)
binary_data = f.serialize(person)
# binary_data can now be sent to Java, Go, etc.
```

Nested collection annotations are declared schema and are honored in both pure
Python and Cython modes.

**Java (Deserializer)**

```java
import org.apache.fory.*;

public class Person {
    public String name;
    public int age;
}

Fory fory = Fory.builder()
    .withXlang(true)
    .withRefTracking(true)
    .build();

fory.register(Person.class, "example.Person");
Person person = (Person) fory.deserialize(binaryData);
```

## Row Format

Row Format provides random and partial access to trusted analytical data without reconstructing the
complete object graph. See the
[Python Row Format guide](https://fory.apache.org/docs/row-format/python) for supported types, schema
requirements, and APIs.

### Basic Row Format Usage

Encode objects to row format for random access without full deserialization. Ideal for large datasets:

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

# Access selected fields without full deserialization.
foo_row = pyfory.RowData(encoder.schema, binary)
print(foo_row.f2[100000])              # Access 100,000th element directly
print(foo_row.f4[100000].f1)           # Access nested field directly
print(foo_row.f4[200000].f2[5])        # Access deeply nested field directly
```

### Cross-Language Compatibility

Row format works across languages. Here's the same data structure accessed in Java:

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

// Random access without full deserialization
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

And in C++ with compile-time type information:

```cpp
#include "fory/encoder/row_encoder.h"
#include "fory/row/writer.h"

struct Bar {
  std::string f1;
  std::vector<int64_t> f2;
  FORY_STRUCT(Bar, f1, f2);
};

struct Foo {
  int32_t f1;
  std::vector<int32_t> f2;
  std::map<std::string, int32_t> f3;
  std::vector<Bar> f4;
  FORY_STRUCT(Foo, f1, f2, f3, f4);
};

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
fory::row::encoder::RowEncoder<Foo> encoder;
encoder.encode(foo);
auto row = encoder.get_writer().to_row();

// Random access without full deserialization
auto f2_array = row->get_array(1);                   // Access f2 list
auto f4_array = row->get_array(3);                   // Access f4 list
auto bar10 = f4_array->get_struct(10);               // Access 11th Bar
int64_t value = bar10->get_array(1)->get_int64(5);   // Access 6th element of bar.f2
std::string str = bar10->get_string(0);              // Access bar.f1
```

### Key Benefits

- **Random access**: Read nested fields without deserializing the entire object
- **Cross-language layout**: Share Standard Row Format data between supported runtimes
- **Partial deserialization**: Deserialize only the elements the application needs

## Core API Reference

### Fory Class

The main serialization interface:

```python
class Fory:
    def __init__(
        self,
        xlang: bool = True,
        ref: bool = False,
        strict: bool = True,
        compatible: bool | None = None,
        max_depth: int = 50
    )
```

### ThreadSafeFory Class

Thread-safe serialization interface for sharing one configured facade across threads:

```python
class ThreadSafeFory:
    def __init__(self, fory_factory=None, **kwargs)
```

Without `fory_factory`, keyword arguments are forwarded to each pooled `Fory`. Use
`fory_factory` when each pooled instance needs custom instance-level configuration.

Register all types before the first serialization or deserialization attempt. That first attempt
permanently freezes registration, even when it fails. Every later registration attempt raises an
error.

**Thread Safety Example:**

```python
import pyfory
import threading
from dataclasses import dataclass

@dataclass
class Person:
    name: str
    age: int

# Create thread-safe Fory instance
fory = pyfory.ThreadSafeFory(xlang=False, ref=True)
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

- **Thread-safe use**: One configured facade can be shared across threads
- **Shared Configuration**: Complete all registrations before the first root attempt
- **Same root API**: Provides the same serialization and deserialization methods as `Fory`
- **Registration Safety**: The first root attempt permanently freezes registration, even if it fails

**When to Use:**

- **Multi-threaded Applications**: Web servers, concurrent workers, parallel processing
- **Shared Fory Instances**: When multiple threads need to serialize/deserialize data
- **Thread Pools**: Applications using thread pools or concurrent.futures

**Parameters:**

- **`xlang`** (`bool`, default=`True`): Use xlang mode. Set `False` for Python native mode supporting Python-specific objects.
- **`ref`** (`bool`, default=`False`): Enable reference tracking for shared/circular references. Disable for better performance if your data has no shared references.
- **`strict`** (`bool`, default=`True`): Require type registration for security. **Highly recommended** for production. Only disable in trusted environments.
- **`compatible`** (`bool | None`, default `None`): Enable schema evolution. `None` enables compatible mode in both xlang and native mode. Set `False` only when every reader and writer always uses the same Python class schema and you want faster serialization and smaller size.
- **`max_depth`** (`int`, default=`50`): Maximum deserialization depth for security, preventing stack overflow attacks.

**Key Methods:**

```python
# Complete registration before the first root API call.
fory.register(MyClass, type_id=123)
# Alternatively, register by name or provide a custom serializer.
# fory.register(MyClass, name="my.package.MyClass")
# fory.register(MyClass, type_id=123, serializer=MySerializer)

# serialize/deserialize are identical to dumps/loads.
data: bytes = fory.serialize(obj)
obj = fory.deserialize(data)
data = fory.dumps(obj)
obj = fory.loads(data)
```

### Xlang And Native Mode Comparison

| Feature             | Native mode (`xlang=False`)               | Xlang mode (default)                  |
| ------------------- | ----------------------------------------- | ------------------------------------- |
| Use case            | Pure Python applications                  | Multi-language systems                |
| Compatibility       | Python only                               | Java, Go, Rust, C++, JavaScript, etc. |
| Supported types     | Configured Python type surface            | Cross-language compatible types       |
| Functions/lambdas   | Registered and policy-authorized carriers | Not allowed                           |
| Instance methods    | Registered and policy-authorized carriers | Not allowed                           |
| Stateful/reduce     | Registered and policy-authorized types    | Not allowed                           |
| Schema mode default | Compatible                                | Compatible                            |

#### Native Mode (`xlang=False`)

Python native mode supports Python-specific objects such as functions and closures. Configure the
complete type surface before the first root operation:

```python
import pyfory
import types

# Python native mode
fory = pyfory.Fory(xlang=False, ref=True, strict=False)
fory.register_type(types.FunctionType)

# Every carrier is registered before this first root operation.
data = fory.dumps({
    'function': lambda x: x * 2,
    'values': [1, 2, 3],
})
result = fory.loads(data)
assert result['function'](4) == 8
```

#### Xlang Mode

Xlang mode restricts types to those compatible across all Fory implementations. Use it for multi-language systems:

```python
import pyfory

f = pyfory.Fory(xlang=True, ref=True)

# Only supports cross-language compatible types
f.register(MyDataClass, name="com.example.MyDataClass")

# Data can be read by Java, Go, Rust, etc.
data = f.serialize(MyDataClass(field1="value", field2=42))
```

## Advanced Features

### Reference Tracking & Circular References

Handle shared references and circular dependencies safely. Set `ref=True` to deduplicate objects:

```python
from dataclasses import dataclass
from typing import Optional

import pyfory

f = pyfory.Fory(xlang=False, ref=True)  # Enable reference tracking

@dataclass
class Node:
    value: str
    next: Optional["Node"] = pyfory.field(ref=True, nullable=True, default=None)

f.register_type(Node)

root = Node("root")
child = Node("child")
root.next = child
child.next = root  # Circular reference

# Serializes without infinite recursion
data = f.serialize(root)
result = f.deserialize(data)
assert result.next.next is result  # Reference preserved
```

### Type Registration

Register the complete application type surface before the first root operation. See
[Type Registration](https://fory.apache.org/docs/object-serialization/python/type-registration) for
registration identity, strict-mode behavior, and the frozen registry lifecycle. See
[Python Security](https://fory.apache.org/docs/object-serialization/python/security) before
accepting untrusted input.

### Custom Serializers

Custom serializers implement the serializer-owned `write` and `read` operations and are registered
before the first root operation. See
[Custom Serializers](https://fory.apache.org/docs/object-serialization/python/custom-serializers) for
the supported constructor and context APIs.

### NumPy & Scientific Computing

Python native mode supports NumPy ndarrays, including multidimensional and object-dtype arrays. See
[NumPy Integration](https://fory.apache.org/docs/object-serialization/python/numpy-integration) for
supported behavior and out-of-band transport.

## Best Practices

### Production Configuration

Use these recommended settings to balance security, performance, and functionality in production:

```python
import pyfory

# Recommended settings for production
fory = pyfory.Fory(
    xlang=False,        # Native mode for Python-only traffic
    ref=False,           # Enable if you have shared/circular references
    strict=True,        # CRITICAL: Always True in production
    max_depth=20       # Adjust based on your data structure depth
)

# Register all types upfront
fory.register(UserModel, type_id=100)
fory.register(OrderModel, type_id=101)
fory.register(ProductModel, type_id=102)
```

### Performance Tips

Use these configuration rules before measuring an application workload:

1. **Disable `ref=True` if not needed**: Reference tracking has overhead
2. **Reuse configured Fory instances**: Create once, use many times; use `ThreadSafeFory` when an
   instance must be shared across threads
3. **Use `compatible=False` only for same-schema data**: Every reader and writer must use the same
   Python class schema
4. **Use Row Format for partial reads**: Choose it when applications need random access to trusted
   analytical row data instead of object reconstruction; see the
   [Python Row Format guide](https://fory.apache.org/docs/row-format/python)

```python
# Good: Reuse instance
fory = pyfory.Fory(xlang=False)
for obj in objects:
    data = fory.dumps(obj)

# Bad: Create new instance each time
for obj in objects:
    fory = pyfory.Fory(xlang=False)  # Wasteful!
    data = fory.dumps(obj)
```

### Type Registration Patterns

Use stable names for shared xlang schemas and numeric IDs for Python-native type identity. See
[Type Registration](https://fory.apache.org/docs/object-serialization/python/type-registration) for
the supported patterns, including custom serializers and batch registration.

### Error Handling

A failed root never reopens the registry. Create and fully configure a new instance after a missing
or invalid registration failure. A fully configured instance can process another root after a
failure while reading input data or serializing a value. See
[Error Handling](https://fory.apache.org/docs/object-serialization/python/troubleshooting#error-handling)
for a complete example.

## Security Best Practices

### Production Configuration

Never disable `strict=True` in production unless your environment is completely trusted:

```python
import pyfory

# Recommended production settings
f = pyfory.Fory(
    ref=True,      # Handle circular references
    strict=True,   # IMPORTANT: Prevent malicious data
    max_depth=100  # Prevent deep recursion attacks
)

# Explicitly register allowed types
f.register(UserModel, type_id=100)
f.register(OrderModel, type_id=101)
# Never set strict=False in production with untrusted data!
```

### Development vs Production

Use environment variables to switch between development and production configurations:

```python
import pyfory
import os

# Development configuration
if os.getenv('ENV') == 'development':
    fory = pyfory.Fory(
        xlang=False,
        ref=True,
        strict=False,    # Use only with trusted development payloads
        max_depth=1000   # Higher limit for development
    )
    for model_class in [UserModel, ProductModel, OrderModel]:
        fory.register_type(model_class)
else:
    # Production configuration (security hardened)
    fory = pyfory.Fory(
        ref=True,
        strict=True,     # CRITICAL: Require registration
        max_depth=100    # Reasonable limit
    )
    # Register only known safe types
    for idx, model_class in enumerate([UserModel, ProductModel, OrderModel]):
        fory.register(model_class, type_id=100 + idx)
```

### DeserializationPolicy

When `strict=False` is necessary for trusted native-mode payloads, configure a
`DeserializationPolicy` before the first root operation to restrict accepted types and object hooks.
See
[Python Security](https://fory.apache.org/docs/object-serialization/python/security#deserializationpolicy)
for the supported policy hooks and configuration example.

## Troubleshooting

### Common Issues

**Q: ImportError with format features**

```python
# A: Install Row format support
pip install pyfory[format]

# Or install from source with format support
pip install -e ".[format]"
```

**Q: Slow serialization performance**

```python
# A: Check if Cython acceleration is enabled
import pyfory
print(pyfory.ENABLE_FORY_CYTHON_SERIALIZATION)  # Should be True

# If False, Cython extension may not be compiled correctly
# Reinstall with: pip install --force-reinstall --no-cache-dir pyfory

# For debugging, you can disable the Cython implementation before importing
import os
os.environ['ENABLE_FORY_CYTHON_SERIALIZATION'] = '0'
import pyfory  # Now uses the pure Python implementation
```

**Q: Cross-language compatibility issues**

```python
# A: Use explicit type registration with consistent naming
f = pyfory.Fory(xlang=True)
f.register(MyClass, name="com.package.MyClass")  # Use same name in all languages
```

**Q: Circular reference errors or duplicate data**

Registered xlang schema objects and Python native objects both require reference tracking when
object identity or cycles matter:

```python
# A: Enable reference tracking for registered schema objects
f = pyfory.Fory(ref=True)
```

For configured Python object graphs with circular references, use native mode, register every
application type before the first root, and declare reference-tracked recursive fields as shown in
[Reference Tracking & Circular References](#reference-tracking--circular-references).

### Debug Mode

```python
# Set environment variable BEFORE importing pyfory to disable Cython for debugging
import os
os.environ['ENABLE_FORY_CYTHON_SERIALIZATION'] = '0'
import pyfory  # Now uses pure Python implementation

# This is useful for:
# 1. Debugging protocol issues
# 2. Understanding serialization behavior
# 3. Development without recompiling Cython
```

**Q: Schema evolution not working**

Xlang mode defaults to compatible schema evolution. Configure writer and reader schemas on separate
instances because each instance's registry freezes on its first root operation. See
[Schema Evolution](https://fory.apache.org/docs/object-serialization/python/schema-evolution) for a
complete example.

**Q: Type registration errors in strict mode**

```python
# A: Register all custom types before serialization
f = pyfory.Fory(strict=True)

# Must register before use
f.register(MyClass, type_id=100)
f.register(AnotherClass, type_id=101)

# Native carriers still require pre-registration when strict mode is disabled.
f = pyfory.Fory(xlang=False, strict=False)  # Use only with trusted payloads
f.register_type(MyClass)
```

## Contributing

Apache Fory™ is an open-source project under the Apache Software Foundation. We welcome all forms of contributions:

### How to Contribute

1. **Report Issues**: Found a bug? [Open an issue](https://github.com/apache/fory/issues)
2. **Suggest Features**: Have an idea? Start a discussion
3. **Improve Docs**: Documentation improvements are always welcome
4. **Submit Code**: See our [Contributing Guide](https://github.com/apache/fory/blob/main/CONTRIBUTING.md)

> **For Contributors**: See [CONTRIBUTING.md](CONTRIBUTING.md) for comprehensive development setup instructions

## License

Apache License 2.0. See [LICENSE](https://github.com/apache/fory/blob/main/LICENSE) for details.

## Links

- **Documentation**: https://fory.apache.org/docs/object-serialization/python/
- **GitHub**: https://github.com/apache/fory
- **PyPI**: https://pypi.org/project/pyfory/
- **Issues**: https://github.com/apache/fory/issues
- **Slack**: https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw
- **Benchmarks**: https://fory.apache.org/docs/benchmarks/

## Community

We welcome contributions! Whether it's bug reports, feature requests, documentation improvements, or code contributions, we appreciate your help.

- Star the project on [GitHub](https://github.com/apache/fory)
- Join our [Slack community](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw)
- Follow us on [X/Twitter](https://x.com/ApacheFory)
