# Apache Fory™ Rust

[![Crates.io](https://img.shields.io/crates/v/fory.svg)](https://crates.io/crates/fory)
[![Documentation](https://docs.rs/fory/badge.svg)](https://docs.rs/fory)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Apache Fory™ is a blazingly fast multi-language serialization framework powered by
codegen and zero-copy techniques. It provides high-performance
serialization and deserialization for Rust applications with cross-language
compatibility.

## 🚀 Key Features

- **High Performance**: Optimized for speed with zero-copy deserialization
- **Cross-Language**: Designed for multi-language environments and microservices
- **Two Serialization Modes**: Object serialization with highly-optimized protocol and row-based lazy on-demand serialization
- **Type Safety**: Compile-time type checking with derive macros
- **Schema Evolution**: Support for compatible mode with field additions/deletions
- **Zero Dependencies**: Minimal runtime dependencies for maximum performance

## 📦 Crates

This repository contains three main crates:

- **[`fory`](fory/)**: Main crate with high-level API and derive macros
- **[`fory-core`](fory-core/)**: Core serialization engine and low-level APIs
- **[`fory-derive`](fory-derive/)**: Procedural macros for code generation

## 🏃‍♂️ Quick Start

Add Apache Fory™ to your `Cargo.toml`:

```toml
[dependencies]
fory = "0.1"
fory-derive = "0.1"
chrono = "0.4"
```

### Object Serialization

For complex data structures with full object graph serialization:

```rust
use fory::{Fory, Error};
use fory_derive::Fory;
use std::collections::HashMap;

#[derive(Fory, Debug, PartialEq, Default)]
struct Person {
    name: String,
    age: i32,
    address: Address,
    hobbies: Vec<String>,
    metadata: HashMap<String, String>,
}

#[derive(Fory, Debug, PartialEq, Default)]
struct Address {
    street: String,
    city: String,
    country: String,
}

// Create a Fory instance and register types
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
        ("department".to_string(), "engineering".to_string()),
        ("level".to_string(), "senior".to_string()),
    ]),
};

// Serialize the object
let serialized = fory.serialize(&person);

// Deserialize back to the original type
let deserialized: Person = fory.deserialize(&serialized)?;

assert_eq!(person, deserialized);
```

### Trait Object Serialization

Apache Fory™ supports serializing trait objects with polymorphism, enabling dynamic dispatch and type flexibility.

#### Box-Based Trait Objects

Serialize trait objects using `Box<dyn Trait>`:

```rust
use fory_core::{Fory, register_trait_type};
use fory_core::serializer::Serializer;
use fory_derive::Fory;
use fory_core::types::Mode;

trait Animal: Serializer {
    fn speak(&self) -> String;
    fn name(&self) -> &str;
}

#[derive(Fory)]
struct Dog { name: String, breed: String }

impl Animal for Dog {
    fn speak(&self) -> String { "Woof!".to_string() }
    fn name(&self) -> &str { &self.name }
}

#[derive(Fory)]
struct Cat { name: String, color: String }

impl Animal for Cat {
    fn speak(&self) -> String { "Meow!".to_string() }
    fn name(&self) -> &str { &self.name }
}

register_trait_type!(Animal, Dog, Cat);

#[derive(Fory)]
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

let serialized = fory.serialize(&zoo);
let deserialized: Zoo = fory.deserialize(&serialized)?;

assert_eq!(deserialized.star_animal.name(), "Buddy");
assert_eq!(deserialized.star_animal.speak(), "Woof!");
```

#### Rc/Arc-Based Trait Objects

For fields with `Rc<dyn Trait>` or `Arc<dyn Trait>`, use them directly in struct definitions:

```rust
use std::sync::Arc;
use std::rc::Rc;
use std::collections::HashMap;

#[derive(Fory)]
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

let serialized = fory.serialize(&shelter);
let deserialized: AnimalShelter = fory.deserialize(&serialized)?;

assert_eq!(deserialized.animals_rc[0].name(), "Rex");
assert_eq!(deserialized.animals_arc[0].speak(), "Woof!");
```

**Wrapper Types for Standalone Usage**

Due to Rust's orphan rule, `Rc<dyn Trait>` and `Arc<dyn Trait>` cannot implement `Serializer` directly. For standalone serialization (not inside struct fields), use the fory auto-generated wrapper types:

```rust
// register_trait_type! generates: AnimalRc and AnimalArc

// Wrap Rc/Arc trait objects
let dog_rc: Rc<dyn Animal> = Rc::new(Dog {
    name: "Rex".to_string(),
    breed: "Golden".to_string()
});
let wrapper = AnimalRc::from(dog_rc);

// Serialize the wrapper
let serialized = fory.serialize(&wrapper);
let deserialized: AnimalRc = fory.deserialize(&serialized)?;

// Unwrap back to Rc<dyn Animal>
let unwrapped: Rc<dyn Animal> = deserialized.unwrap();
assert_eq!(unwrapped.name(), "Rex");
```

For struct fields with `Rc<dyn Trait>` and `Arc<dyn Trait>` type, fory will generate code automatically to convert such fields to wrappers for serialization and deserialization.

### Row-Based Serialization

For high-performance, zero-copy scenarios:

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

// Deserialize with zero-copy access
let row = from_row::<UserProfile>(&row_data);

// Access fields directly from the row data
assert_eq!(row.id(), 12345);
assert_eq!(row.username(), "alice");
assert_eq!(row.email(), "alice@example.com");
assert_eq!(row.is_active(), true);

// Access collections efficiently
let scores = row.scores();
assert_eq!(scores.size(), 4);
assert_eq!(scores.get(0), 95);
assert_eq!(scores.get(1), 87);

let prefs = row.preferences();
assert_eq!(prefs.keys().size(), 2);
assert_eq!(prefs.keys().get(0), "language");
assert_eq!(prefs.values().get(0), "en");
```

## 📚 Documentation

- **[API Documentation](https://docs.rs/fory)** - Complete API reference
- **[Performance Guide](docs/performance.md)** - Optimization tips and benchmarks

## 🎯 Use Cases

### Object Serialization

- **Complex data structures** with nested objects and references
- **Cross-language communication** in microservices architectures
- **General-purpose serialization** with full type safety
- **Schema evolution** with compatible mode

### Row-Based Serialization

- **High-throughput data processing** with zero-copy access
- **Analytics workloads** requiring fast field access
- **Memory-constrained environments** with minimal allocations
- **Real-time data streaming** applications

## 🔧 Supported Types

### Primitive Types

- `bool`, `i8`, `i16`, `i32`, `i64`, `f32`, `f64`
- `String`, `&str` (in row format)
- `Vec<u8>` for binary data

### Collections

- `Vec<T>` for arrays/lists
- `HashMap<K, V>` and `BTreeMap<K, V>` for maps
- `Option<T>` for nullable values

### Date and Time

- `chrono::NaiveDate` for dates (requires chrono dependency)
- `chrono::NaiveDateTime` for timestamps (requires chrono dependency)

### Custom Types

- Structs with `#[derive(Fory)]` or `#[derive(ForyRow)]`
- Enums with `#[derive(Fory)]`
- Trait objects

## ⚡ Performance

Apache Fory™ is designed for maximum performance:

- **Zero-copy deserialization** in row mode
- **Buffer pre-allocation** to minimize allocations
- **Variable-length encoding** for compact representation
- **Little-endian byte order** for cross-platform compatibility
- **Optimized code generation** with derive macros

## 🌍 Cross-Language Compatibility

Apache Fory™ is designed to work across multiple programming languages, making it ideal for:

- **Microservices architectures** with polyglot services
- **Distributed systems** with heterogeneous components
- **Data pipelines** spanning multiple language ecosystems
- **API communication** between different technology stacks

## Benchmark

```bash
cargo bench --package fory-benchmarks
```

## 🛠️ Development Status

Apache Fory™ Rust implementation roadmap:

- [x] Static codegen based on rust macro
- [x] Row format serialization
- [x] Cross-language object graph serialization
- [x] Static codegen based on fory-derive macro processor
- [ ] Advanced schema evolution features
- [ ] Performance optimizations and benchmarks

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for details.

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](../CONTRIBUTING.md) for details.

## 📞 Support

- **Documentation**: [docs.rs/fory](https://docs.rs/fory)
- **Issues**: [GitHub Issues](https://github.com/apache/fory/issues)
- **Discussions**: [GitHub Discussions](https://github.com/apache/fory/discussions)

---

**Apache Fory™** - Blazingly fast serialization.
