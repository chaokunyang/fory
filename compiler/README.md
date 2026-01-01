# Fory Definition Language (FDL) Compiler

The FDL compiler generates cross-language serialization code from schema definitions. It enables type-safe cross-language data exchange by generating native data structures with Fory serialization support for multiple programming languages.

## Features

- **Multi-language code generation**: Java, Python, Go, Rust, C++
- **Rich type system**: Primitives, enums, messages, lists, maps
- **Cross-language serialization**: Generated code works seamlessly with Apache Fory
- **Type ID and namespace support**: Both numeric IDs and name-based type registration
- **Field modifiers**: Optional fields, reference tracking, repeated fields

## Documentation

For comprehensive documentation, see the [FDL Schema Guide](../docs/schema/index.md):

- [FDL Syntax Reference](../docs/schema/fdl-syntax.md) - Complete language syntax and grammar
- [Type System](../docs/schema/type-system.md) - Primitive types, collections, and language mappings
- [Compiler Guide](../docs/schema/compiler-guide.md) - CLI options and build integration
- [Generated Code](../docs/schema/generated-code.md) - Output format for each target language
- [Protocol Buffers vs FDL](../docs/schema/proto-vs-fdl.md) - Feature comparison and migration guide

## Installation

```bash
cd compiler
pip install -e .
```

## Quick Start

### 1. Define Your Schema

Create a `.fdl` file:

```fdl
package demo;

enum Color @101 {
    GREEN = 0;
    RED = 1;
    BLUE = 2;
}

message Dog @102 {
    optional string name = 1;
    int32 age = 2;
}

message Cat @103 {
    ref Dog friend = 1;
    optional string name = 2;
    repeated string tags = 3;
    map<string, int32> scores = 4;
    int32 lives = 5;
}
```

### 2. Compile

```bash
# Generate for all languages
fory compile schema.fdl --output ./generated

# Generate for specific languages
fory compile schema.fdl --lang java,python --output ./generated

# Override package name
fory compile schema.fdl --package myapp.models --output ./generated
```

### 3. Use Generated Code

**Java:**

```java
import demo.*;
import org.apache.fory.Fory;

Fory fory = Fory.builder().build();
DemoForyRegistration.register(fory);

Cat cat = new Cat();
cat.setName("Whiskers");
cat.setLives(9);
byte[] bytes = fory.serialize(cat);
```

**Python:**

```python
import pyfory
from demo import Cat, register_demo_types

fory = pyfory.Fory()
register_demo_types(fory)

cat = Cat(name="Whiskers", lives=9)
data = fory.serialize(cat)
```

## FDL Syntax

### Package Declaration

```fdl
package com.example.models;
```

### Enum Definition

```fdl
enum Status @100 {
    PENDING = 0;
    ACTIVE = 1;
    INACTIVE = 2;
}
```

### Message Definition

```fdl
message User @101 {
    string name = 1;
    int32 age = 2;
    optional string email = 3;
}
```

### Type ID vs Name-based Registration

Types with `@<id>` use numeric type IDs for efficient serialization:

```fdl
message User @101 { ... }  // Registered with type ID 101
```

Types without `@<id>` use namespace-based registration:

```fdl
message Config { ... }  // Registered as "package.Config"
```

### Primitive Types

| FDL Type    | Java        | Python               | Go          | Rust                    | C++                    |
| ----------- | ----------- | -------------------- | ----------- | ----------------------- | ---------------------- |
| `bool`      | `boolean`   | `bool`               | `bool`      | `bool`                  | `bool`                 |
| `int8`      | `byte`      | `pyfory.Int8Type`    | `int8`      | `i8`                    | `int8_t`               |
| `int16`     | `short`     | `pyfory.Int16Type`   | `int16`     | `i16`                   | `int16_t`              |
| `int32`     | `int`       | `pyfory.Int32Type`   | `int32`     | `i32`                   | `int32_t`              |
| `int64`     | `long`      | `int`                | `int64`     | `i64`                   | `int64_t`              |
| `float32`   | `float`     | `pyfory.Float32Type` | `float32`   | `f32`                   | `float`                |
| `float64`   | `double`    | `float`              | `float64`   | `f64`                   | `double`               |
| `string`    | `String`    | `str`                | `string`    | `String`                | `std::string`          |
| `bytes`     | `byte[]`    | `bytes`              | `[]byte`    | `Vec<u8>`               | `std::vector<uint8_t>` |
| `date`      | `LocalDate` | `datetime.date`      | `time.Time` | `chrono::NaiveDate`     | `fory::LocalDate`      |
| `timestamp` | `Instant`   | `datetime.datetime`  | `time.Time` | `chrono::NaiveDateTime` | `fory::Timestamp`      |

### Collection Types

```fdl
repeated string tags = 1;           // List<String>
map<string, int32> scores = 2;      // Map<String, Integer>
```

### Field Modifiers

- **`optional`**: Field can be null/None
- **`ref`**: Enable reference tracking for shared/circular references
- **`repeated`**: Field is a list/array

```fdl
message Example {
    optional string nullable_field = 1;
    ref OtherMessage shared_ref = 2;
    repeated int32 numbers = 3;
}
```

## Architecture

```
fory_compiler/
├── __init__.py           # Package exports
├── __main__.py           # Module entry point
├── cli.py                # Command-line interface
├── parser/
│   ├── ast.py            # AST node definitions
│   ├── lexer.py          # Hand-written tokenizer
│   └── parser.py         # Recursive descent parser
└── generators/
    ├── base.py           # Base generator class
    ├── java.py           # Java POJO generator
    ├── python.py         # Python dataclass generator
    ├── go.py             # Go struct generator
    ├── rust.py           # Rust struct generator
    └── cpp.py            # C++ struct generator
```

### Parser

The parser is a hand-written recursive descent parser that produces an AST:

- **Lexer** (`lexer.py`): Tokenizes FDL source into tokens (keywords, identifiers, punctuation)
- **AST** (`ast.py`): Defines node types - `Schema`, `Message`, `Enum`, `Field`, `FieldType`
- **Parser** (`parser.py`): Builds AST from token stream with validation

### Generators

Each generator extends `BaseGenerator` and implements:

- `generate()`: Returns list of `GeneratedFile` objects
- `generate_type()`: Converts FDL types to target language types
- Language-specific registration helpers

## Generated Output

### Java

Generates POJOs with:

- Private fields with getters/setters
- `@ForyField` annotations for nullable/ref fields
- Registration helper class

```java
public class Cat {
    @ForyField(trackingRef = true)
    private Dog friend;

    @ForyField(nullable = true)
    private String name;

    private List<String> tags;
    // ...
}
```

### Python

Generates dataclasses with:

- Type hints
- Default values
- Registration function

```python
@dataclass
class Cat:
    friend: Optional[Dog] = None
    name: Optional[str] = None
    tags: List[str] = None
```

### Go

Generates structs with:

- Fory struct tags
- Pointer types for nullable fields
- Registration function with error handling

```go
type Cat struct {
    Friend *Dog              `fory:"trackRef"`
    Name   *string           `fory:"nullable"`
    Tags   []string
}
```

### Rust

Generates structs with:

- `#[derive(ForyObject)]` macro
- `#[fory(...)]` field attributes
- `#[tag(...)]` for namespace registration

```rust
#[derive(ForyObject, Debug, Clone, PartialEq, Default)]
pub struct Cat {
    pub friend: Rc<Dog>,
    #[fory(nullable = true)]
    pub name: Option<String>,
    pub tags: Vec<String>,
}
```

### C++

Generates structs with:

- `FORY_STRUCT` macro for serialization
- `std::optional` for nullable fields
- `std::shared_ptr` for ref fields

```cpp
struct Cat {
    std::shared_ptr<Dog> friend;
    std::optional<std::string> name;
    std::vector<std::string> tags;
};
FORY_STRUCT(Cat, friend, name, tags, scores, lives);
```

## CLI Reference

```
fory compile [OPTIONS] FILES...

Arguments:
  FILES                 FDL files to compile

Options:
  --lang TEXT          Target languages (java,python,cpp,rust,go or "all")
                       Default: all
  --output, -o PATH    Output directory
                       Default: ./generated
  --package TEXT       Override package name from FDL file
  --help               Show help message
```

## Examples

See the `examples/` directory for sample FDL files and generated output.

```bash
# Compile the demo schema
fory compile examples/demo.fdl --output examples/generated
```

## Development

```bash
# Install in development mode
pip install -e .

# Run the compiler
python -m fory_compiler compile examples/demo.fdl

# Or use the installed command
fory compile examples/demo.fdl
```

## License

Apache License 2.0
