# Apache Fory™ Rust

[![Crates.io](https://img.shields.io/crates/v/fory.svg)](https://crates.io/crates/fory)
[![Documentation](https://docs.rs/fory/badge.svg)](https://docs.rs/fory)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Apache Fory™ is a blazingly-fast multi-language serialization framework powered by JIT compilation and zero-copy techniques, delivering **up to 170x** performance improvement over traditional serialization methods. The Rust implementation provides high-performance serialization with automatic memory management and compile-time type safety.

## 🚀 Why Apache Fory™ Rust?

- **🔥 Blazingly Fast**: Zero-copy deserialization and optimized binary protocols
- **🌍 Cross-Language**: Seamlessly serialize/deserialize data across Java, Python, C++, Go, JavaScript, and Rust
- **🎯 Type-Safe**: Compile-time type checking with derive macros
- **🔄 Circular References**: Automatic tracking of shared and circular references with `Rc`/`Arc` and weak pointers
- **🧬 Polymorphic**: Serialize trait objects with `Box<dyn Trait>`, `Rc<dyn Trait>`, and `Arc<dyn Trait>`
- **📦 Schema Evolution**: Compatible mode for independent schema changes
- **⚡ Two Modes**: Object graph serialization and zero-copy row-based format

## 📦 Crates

| Crate | Description | Version |
|-------|-------------|---------|
| [`fory`](fory/) | High-level API with derive macros | [![crates.io](https://img.shields.io/crates/v/fory.svg)](https://crates.io/crates/fory) |
| [`fory-core`](fory-core/) | Core serialization engine | [![crates.io](https://img.shields.io/crates/v/fory-core.svg)](https://crates.io/crates/fory-core) |
| [`fory-derive`](fory-derive/) | Procedural macros | [![crates.io](https://img.shields.io/crates/v/fory-derive.svg)](https://crates.io/crates/fory-derive) |

## 🏃 Quick Start

Add Apache Fory™ to your `Cargo.toml`:

```toml
[dependencies]
fory = "0.13"
fory-derive = "0.13"
```

### Basic Example

```rust
use fory::{Fory, Error};
use fory_derive::ForyObject;

#[derive(ForyObject, Debug, PartialEq)]
struct User {
    name: String,
    age: i32,
    email: String,
}

fn main() -> Result<(), Error> {
    let mut fory = Fory::default();
    fory.register::<User>(1);

    let user = User {
        name: "Alice".to_string(),
        age: 30,
        email: "alice@example.com".to_string(),
    };

    // Serialize
    let bytes = fory.serialize(&user);

    // Deserialize
    let decoded: User = fory.deserialize(&bytes)?;
    assert_eq!(user, decoded);

    Ok(())
}
```

## 📚 Core Features

### 1. Object Graph Serialization

Serialize complex object graphs with full reference tracking:

```rust
use fory::{Fory, Error};
use fory_derive::ForyObject;
use std::collections::HashMap;

#[derive(ForyObject, Debug, PartialEq, Default)]
struct Person {
    name: String,
    age: i32,
    address: Address,
    hobbies: Vec<String>,
    metadata: HashMap<String, String>,
}

#[derive(ForyObject, Debug, PartialEq, Default)]
struct Address {
    street: String,
    city: String,
    country: String,
}

let mut fory = Fory::default();
fory.register::<Address>(100);
fory.register::<Person>(200);

let person = Person {
    name: "John Doe".to_string(),
    age: 30,
    address: Address {
        street: "123 Main St".to_string(),
        city: "New York".to_string(),
        country: "USA".to_string(),
    },
    hobbies: vec!["reading".to_string(), "coding".to_string()],
    metadata: HashMap::from([
        ("role".to_string(), "developer".to_string()),
    ]),
};

let bytes = fory.serialize(&person);
let decoded: Person = fory.deserialize(&bytes)?;
assert_eq!(person, decoded);
```

### 2. Row-Based Serialization

Zero-copy deserialization for maximum performance:

```rust
use fory::{to_row, from_row};
use fory_derive::ForyRow;
use std::collections::BTreeMap;

#[derive(ForyRow)]
struct UserProfile {
    id: i64,
    username: String,
    email: String,
    scores: Vec<i32>,
    preferences: BTreeMap<String, String>,
    is_active: bool,
}

let profile = UserProfile {
    id: 12345,
    username: "alice".to_string(),
    email: "alice@example.com".to_string(),
    scores: vec![95, 87, 92, 88],
    preferences: BTreeMap::from([
        ("theme".to_string(), "dark".to_string()),
        ("language".to_string(), "en".to_string()),
    ]),
    is_active: true,
};

// Serialize to row format
let row_data = to_row(&profile);

// Zero-copy deserialization
let row = from_row::<UserProfile>(&row_data);
assert_eq!(row.id(), 12345);
assert_eq!(row.username(), "alice");
assert_eq!(row.is_active(), true);
```

### 3. Shared and Circular References

Automatically track reference identity with `Rc<T>`, `Arc<T>`, and weak pointers:

#### Shared References

```rust
use fory::Fory;
use std::rc::Rc;

let fory = Fory::default();

// Create a shared value
let shared = Rc::new(String::from("shared_value"));

// Reference it multiple times
let data = vec![shared.clone(), shared.clone(), shared.clone()];

// Only serialized once, reference identity preserved
let bytes = fory.serialize(&data);
let decoded: Vec<Rc<String>> = fory.deserialize(&bytes)?;

// Verify reference identity is preserved
assert!(Rc::ptr_eq(&decoded[0], &decoded[1]));
assert!(Rc::ptr_eq(&decoded[1], &decoded[2]));
```

#### Circular References with Weak Pointers

```rust
use fory::{Fory, Error};
use fory_derive::ForyObject;
use fory_core::serializer::weak::RcWeak;
use std::rc::Rc;
use std::cell::RefCell;

#[derive(ForyObject, Debug)]
struct Node {
    value: i32,
    parent: RcWeak<RefCell<Node>>,
    children: Vec<Rc<RefCell<Node>>>,
}

let mut fory = Fory::default();
fory.register::<Node>(1);

// Build a parent-child tree
let parent = Rc::new(RefCell::new(Node {
    value: 1,
    parent: RcWeak::new(),
    children: vec![],
}));

let child1 = Rc::new(RefCell::new(Node {
    value: 2,
    parent: RcWeak::from(&parent),
    children: vec![],
}));

let child2 = Rc::new(RefCell::new(Node {
    value: 3,
    parent: RcWeak::from(&parent),
    children: vec![],
}));

parent.borrow_mut().children.push(child1.clone());
parent.borrow_mut().children.push(child2.clone());

// Serialize and deserialize the circular structure
let bytes = fory.serialize(&parent);
let decoded: Rc<RefCell<Node>> = fory.deserialize(&bytes)?;

// Verify the circular relationship
assert_eq!(decoded.borrow().children.len(), 2);
for child in &decoded.borrow().children {
    let upgraded_parent = child.borrow().parent.upgrade().unwrap();
    assert!(Rc::ptr_eq(&decoded, &upgraded_parent));
}
```

### 4. Trait Object Serialization

Serialize polymorphic types with trait objects:

#### Box-Based Trait Objects

```rust
use fory_core::{Fory, register_trait_type};
use fory_core::serializer::Serializer;
use fory_derive::ForyObject;
use fory_core::types::Mode;

trait Animal: Serializer {
    fn speak(&self) -> String;
    fn name(&self) -> &str;
}

#[derive(ForyObject)]
struct Dog { name: String, breed: String }

impl Animal for Dog {
    fn speak(&self) -> String { "Woof!".to_string() }
    fn name(&self) -> &str { &self.name }
}

#[derive(ForyObject)]
struct Cat { name: String, color: String }

impl Animal for Cat {
    fn speak(&self) -> String { "Meow!".to_string() }
    fn name(&self) -> &str { &self.name }
}

// Register trait implementations
register_trait_type!(Animal, Dog, Cat);

#[derive(ForyObject)]
struct Zoo {
    star_animal: Box<dyn Animal>,
}

let mut fory = Fory::default().mode(Mode::Compatible);
fory.register::<Dog>(100);
fory.register::<Cat>(101);
fory.register::<Zoo>(102);

let zoo = Zoo {
    star_animal: Box::new(Dog {
        name: "Buddy".to_string(),
        breed: "Labrador".to_string(),
    }),
};

let bytes = fory.serialize(&zoo);
let decoded: Zoo = fory.deserialize(&bytes)?;

assert_eq!(decoded.star_animal.name(), "Buddy");
assert_eq!(decoded.star_animal.speak(), "Woof!");
```

#### Rc/Arc-Based Trait Objects

For fields with `Rc<dyn Trait>` or `Arc<dyn Trait>`, use them directly:

```rust
use std::sync::Arc;
use std::rc::Rc;
use std::collections::HashMap;

#[derive(ForyObject)]
struct AnimalShelter {
    animals_rc: Vec<Rc<dyn Animal>>,
    animals_arc: Vec<Arc<dyn Animal>>,
    registry: HashMap<String, Arc<dyn Animal>>,
}

let mut fory = Fory::default().mode(Mode::Compatible);
fory.register::<Dog>(100);
fory.register::<Cat>(101);
fory.register::<AnimalShelter>(102);

let shelter = AnimalShelter {
    animals_rc: vec![
        Rc::new(Dog { name: "Rex".to_string(), breed: "Golden".to_string() }),
        Rc::new(Cat { name: "Mittens".to_string(), color: "Gray".to_string() }),
    ],
    animals_arc: vec![
        Arc::new(Dog { name: "Buddy".to_string(), breed: "Labrador".to_string() }),
    ],
    registry: HashMap::from([
        ("pet1".to_string(), Arc::new(Dog {
            name: "Max".to_string(),
            breed: "Shepherd".to_string()
        }) as Arc<dyn Animal>),
    ]),
};

let bytes = fory.serialize(&shelter);
let decoded: AnimalShelter = fory.deserialize(&bytes)?;

assert_eq!(decoded.animals_rc[0].name(), "Rex");
assert_eq!(decoded.animals_arc[0].speak(), "Woof!");
```

### 5. Schema Evolution

Support independent schema changes between serialization peers:

```rust
use fory::Fory;
use fory_core::types::Mode;
use fory_derive::ForyObject;
use std::collections::HashMap;

#[derive(ForyObject, Debug)]
struct PersonV1 {
    name: String,
    age: i32,
    address: String,
}

#[derive(ForyObject, Debug)]
struct PersonV2 {
    name: String,
    age: i32,
    // address removed
    // phone added
    phone: Option<String>,
    metadata: HashMap<String, String>,
}

let mut fory1 = Fory::default().mode(Mode::Compatible);
fory1.register::<PersonV1>(1);

let mut fory2 = Fory::default().mode(Mode::Compatible);
fory2.register::<PersonV2>(1);

let person_v1 = PersonV1 {
    name: "Alice".to_string(),
    age: 30,
    address: "123 Main St".to_string(),
};

// Serialize with V1
let bytes = fory1.serialize(&person_v1);

// Deserialize with V2 - missing fields get default values
let person_v2: PersonV2 = fory2.deserialize(&bytes)?;
assert_eq!(person_v2.name, "Alice");
assert_eq!(person_v2.age, 30);
assert_eq!(person_v2.phone, None);
```

### 6. Enum Support

Serialize enums with or without payloads:

```rust
use fory_derive::ForyObject;

#[derive(ForyObject, Debug, PartialEq, Default)]
enum Status {
    #[default]
    Pending,
    Active,
    Inactive,
    Deleted,
}

let mut fory = Fory::default();
fory.register::<Status>(1);

let status = Status::Active;
let bytes = fory.serialize(&status);
let decoded: Status = fory.deserialize(&bytes)?;
assert_eq!(status, decoded);
```

### 7. Custom Serializers

Implement custom serialization logic:

```rust
use fory_core::fory::{Fory, read_data, write_data};
use fory_core::resolver::context::{ReadContext, WriteContext};
use fory_core::serializer::{Serializer, ForyDefault};
use fory_core::error::Error;
use std::any::Any;

#[derive(Debug, PartialEq, Default)]
struct CustomType {
    value: i32,
}

impl Serializer for CustomType {
    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        write_data(&self.value, context, is_field);
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error> {
        Ok(Self {
            value: read_data(context, is_field)?,
        })
    }

    fn fory_type_id_dyn(&self, fory: &Fory) -> u32 {
        Self::fory_get_type_id(fory)
    }

    fn as_any(&self) -> &dyn Any {
        self
    }
}

impl ForyDefault for CustomType {
    fn fory_default() -> Self {
        Self::default()
    }
}

let mut fory = Fory::default();
fory.register_serializer::<CustomType>(100);

let custom = CustomType { value: 42 };
let bytes = fory.serialize(&custom);
let decoded: CustomType = fory.deserialize(&bytes)?;
assert_eq!(custom, decoded);
```

## 🔧 Supported Types

### Primitive Types

| Rust Type | Description |
|-----------|-------------|
| `bool` | Boolean |
| `i8`, `i16`, `i32`, `i64` | Signed integers |
| `f32`, `f64` | Floating point |
| `String` | UTF-8 string |

### Collections

| Rust Type | Description |
|-----------|-------------|
| `Vec<T>` | Dynamic array |
| `HashMap<K, V>` | Hash map |
| `BTreeMap<K, V>` | Ordered map |
| `HashSet<T>` | Hash set |
| `Option<T>` | Optional value |

### Smart Pointers

| Rust Type | Description |
|-----------|-------------|
| `Box<T>` | Heap allocation |
| `Rc<T>` | Reference counting (shared refs tracked) |
| `Arc<T>` | Thread-safe reference counting (shared refs tracked) |
| `RcWeak<T>` | Weak reference to `Rc<T>` (breaks circular refs) |
| `ArcWeak<T>` | Weak reference to `Arc<T>` (breaks circular refs) |
| `RefCell<T>` | Interior mutability (runtime borrow checking) |
| `Mutex<T>` | Thread-safe interior mutability |

### Date and Time

| Rust Type | Description |
|-----------|-------------|
| `chrono::NaiveDate` | Date without timezone |
| `chrono::NaiveDateTime` | Timestamp without timezone |

### Custom Types

| Macro | Description |
|-------|-------------|
| `#[derive(ForyObject)]` | Object graph serialization |
| `#[derive(ForyRow)]` | Row-based serialization |

## 🌍 Cross-Language Serialization

Apache Fory™ supports seamless data exchange across multiple languages:

```rust
use fory::Fory;
use fory_core::types::Mode;

// Enable cross-language mode
let mut fory = Fory::default()
    .mode(Mode::Compatible)
    .xlang(true);

// Register types with consistent IDs across languages
fory.register::<MyStruct>(100);

// Or use namespace-based registration
fory.register_by_namespace::<MyStruct>("com.example", "MyStruct");
```

See [xlang_type_mapping.md](../docs/guide/xlang_type_mapping.md) for type mapping across languages.

## ⚡ Performance

Apache Fory™ Rust is designed for maximum performance:

- **Zero-Copy Deserialization**: Row format enables direct memory access without copying
- **Buffer Pre-allocation**: Minimizes memory allocations during serialization
- **Compact Encoding**: Variable-length encoding for space efficiency
- **Little-Endian**: Optimized for modern CPU architectures
- **Reference Deduplication**: Shared objects serialized only once

Run benchmarks:

```bash
cd rust
cargo bench --package fory-benchmarks
```

## 📖 Documentation

- **[API Documentation](https://docs.rs/fory)** - Complete API reference
- **[Protocol Specification](../docs/specification/xlang_serialization_spec.md)** - Serialization protocol details
- **[Row Format Specification](../docs/specification/row_format_spec.md)** - Row format details
- **[Type Mapping](../docs/guide/xlang_type_mapping.md)** - Cross-language type mappings

## 🎯 Use Cases

### Object Serialization

- Complex data structures with nested objects and references
- Cross-language communication in microservices
- General-purpose serialization with full type safety
- Schema evolution with compatible mode
- Graph-like data structures with circular references

### Row-Based Serialization

- High-throughput data processing
- Analytics workloads requiring fast field access
- Memory-constrained environments
- Real-time data streaming applications
- Zero-copy scenarios

## 🏗️ Architecture

The Rust implementation consists of three main crates:

```
fory/                   # High-level API
├── src/lib.rs         # Public API exports

fory-core/             # Core serialization engine
├── src/
│   ├── fory.rs       # Main serialization entry point
│   ├── buffer.rs     # Binary buffer management
│   ├── serializer/   # Type-specific serializers
│   ├── resolver/     # Type resolution and metadata
│   ├── meta/         # Meta string compression
│   ├── row/          # Row format implementation
│   └── types.rs      # Type definitions

fory-derive/           # Procedural macros
├── src/
│   ├── object/       # ForyObject macro
│   └── fory_row.rs  # ForyRow macro
```

## 🔄 Serialization Modes

Apache Fory™ supports two serialization modes:

### SchemaConsistent Mode (Default)

Type declarations must match exactly between peers:

```rust
let fory = Fory::default(); // SchemaConsistent by default
```

### Compatible Mode

Allows independent schema evolution:

```rust
use fory_core::types::Mode;

let fory = Fory::default().mode(Mode::Compatible);
```

## 🛠️ Development

### Building

```bash
cd rust
cargo build
```

### Testing

```bash
# Run all tests
cargo test --features tests

# Run specific test
cargo test -p fory-tests --test test_complex_struct
```

### Code Quality

```bash
# Format code
cargo fmt

# Check formatting
cargo fmt --check

# Run linter
cargo clippy --all-targets --all-features -- -D warnings
```

## 🗺️ Roadmap

- [x] Static codegen based on rust macro
- [x] Row format serialization
- [x] Cross-language object graph serialization
- [x] Shared and circular reference tracking
- [x] Weak pointer support
- [x] Trait object serialization with polymorphism
- [x] Schema evolution in compatible mode
- [ ] SIMD optimizations for primitive arrays
- [ ] Async serialization support
- [ ] More comprehensive benchmarks

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for details.

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](../CONTRIBUTING.md) for details.

## 📞 Support

- **Documentation**: [docs.rs/fory](https://docs.rs/fory)
- **Issues**: [GitHub Issues](https://github.com/apache/fory/issues)
- **Discussions**: [GitHub Discussions](https://github.com/apache/fory/discussions)
- **Slack**: [Apache Fory Slack](https://join.slack.com/t/fory-project/shared_invite/zt-1u8soj4qc-ieYEu7ciHOqA2mo47llS8A)

---

**Apache Fory™** - Blazingly fast multi-language serialization framework.
