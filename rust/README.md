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
- **Shared/Circular References**: Automatic tracking and preservation of reference identity with `Rc<T>`, `Arc<T>`, and weak pointer support
- **Type Safety**: Compile-time type checking with derive macros
- **Trait Object Serialization**: Polymorphic serialization with `Box<dyn Trait>`, `Rc<dyn Trait>`, and `Arc<dyn Trait>`
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

let serialized = fory.serialize(&shelter);
let deserialized: AnimalShelter = fory.deserialize(&serialized)?;

assert_eq!(deserialized.animals_rc[0].name(), "Rex");
assert_eq!(deserialized.animals_arc[0].speak(), "Woof!");
```

**Wrapper Types for Standalone Usage**

Due to Rust's orphan rule, `Rc<dyn Trait>` and `Arc<dyn Trait>` cannot implement `Serializer` directly. For standalone serialization (not inside struct fields), use the `Rc<dyn Any>/Arc<dyn Any>` or fory auto-generated wrapper types instead:

Example for `Rc<dyn T>`:

```rust
let dog_rc: Rc<dyn Animal> = Rc::new(Dog {
    name: "Rex".to_string(),
    breed: "Golden".to_string()
});
let dog = doc_rc.as_any();

// Serialize the wrapper
let serialized = fory.serialize(&wrapper);
let deserialized: Rc<dyn Any> = fory.deserialize(&serialized)?;

// Unwrap back to Rc<dyn Animal>
let unwrapped: Rc<dyn Animal> = deserialized.downcast_ref::<Dog>();
assert_eq!(unwrapped.name(), "Rex");
```

Example for `Arc<dyn Any>`:

```rust
// register_trait_type! generates: AnimalRc and AnimalArc

// Wrap Rc/Arc trait objects
let dog_rc: Arc<dyn Animal> = Arc::new(Dog {
    name: "Rex".to_string(),
    breed: "Golden".to_string()
});
let wrapper = AnimalArc::from(dog_rc);

// Serialize the wrapper
let serialized = fory.serialize(&wrapper);
let deserialized: AnimalArc = fory.deserialize(&serialized)?;

// Unwrap back to Arc<dyn Animal>
let unwrapped: Arc<dyn Animal> = deserialized.unwrap();
assert_eq!(unwrapped.name(), "Rex");
```

For struct fields with `Rc<dyn Trait>` and `Arc<dyn Trait>` type, fory will generate code automatically to convert such fields to wrappers for serialization and deserialization.

### Shared/Circular References Serialization

Apache Fory™ provides comprehensive support for serializing complex object graphs with shared references and circular dependencies. This feature is essential for real-world data structures like trees with parent pointers, doubly-linked lists, and general graphs.

#### Shared References with `Rc<T>` and `Arc<T>`

Fory automatically tracks reference identity for `Rc<T>` (single-threaded) and `Arc<T>` (thread-safe) smart pointers. When the same object is referenced multiple times in an object graph, Fory serializes it only once and uses reference IDs for subsequent occurrences, ensuring:

- **Space efficiency**: No data duplication in serialized output
- **Reference identity preservation**: Deserialized objects maintain the same sharing relationships

```rust
use fory::Fory;
use std::rc::Rc;

let fory = Fory::default();

// Create a shared value
let shared = Rc::new(String::from("shared_value"));

// Reference it multiple times in a vector
let data = vec![shared.clone(), shared.clone(), shared.clone()];

// The shared value is serialized only once
let serialized = fory.serialize(&data);
let deserialized: Vec<Rc<String>> = fory.deserialize(&serialized)?;

// Verify reference identity is preserved
assert_eq!(deserialized.len(), 3);
assert_eq!(*deserialized[0], "shared_value");

// All three Rc pointers point to the same object
assert!(Rc::ptr_eq(&deserialized[0], &deserialized[1]));
assert!(Rc::ptr_eq(&deserialized[1], &deserialized[2]));
```

For thread-safe shared references, use `Arc<T>`:

```rust
use fory::Fory;
use std::sync::Arc;

let fory = Fory::default();

let shared1 = Arc::new(42i32);
let shared2 = Arc::new(100i32);

// Multiple references to different shared values
let data = vec![
    shared1.clone(),
    shared2.clone(),
    shared1.clone(),
    shared2.clone(),
];

let serialized = fory.serialize(&data);
let deserialized: Vec<Arc<i32>> = fory.deserialize(&serialized)?;

// Verify reference identity preservation
assert!(Arc::ptr_eq(&deserialized[0], &deserialized[2]));
assert!(Arc::ptr_eq(&deserialized[1], &deserialized[3]));
```

#### Circular References with Weak Pointers

To serialize circular references like parent-child relationships, use `RcWeak<T>` or `ArcWeak<T>` to break the cycle. These weak pointers are serialized as references to their strong counterparts, preserving the graph structure without causing memory leaks or infinite recursion.

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
fory.register::<Node>(2000);

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
let serialized = fory.serialize(&parent);
let deserialized: Rc<RefCell<Node>> = fory.deserialize(&serialized)?;

// Verify the circular relationship
assert_eq!(deserialized.borrow().children.len(), 2);
for child in &deserialized.borrow().children {
    let upgraded_parent = child.borrow().parent.upgrade().unwrap();
    assert!(Rc::ptr_eq(&deserialized, &upgraded_parent));
}
```

**Key Features of Weak Reference Serialization:**

- **Reference tracking**: Weak pointers serialize as references to their strong counterparts
- **Null handling**: If the strong pointer has been dropped, weak serializes as `Null`
- **Forward reference resolution**: Weak pointers that appear before their targets are resolved via callbacks after deserialization
- **Shared cell semantics**: All clones of an `RcWeak`/`ArcWeak` share the same internal cell, so deserialization updates propagate to all copies

#### Thread-Safe Circular Graphs with `Arc`

For multi-threaded scenarios with circular references, use `ArcWeak<T>` with `Arc<Mutex<T>>`:

```rust
use fory::{Fory, Error};
use fory_derive::ForyObject;
use fory_core::serializer::weak::ArcWeak;
use std::sync::{Arc, Mutex};

#[derive(ForyObject)]
struct Node {
    val: i32,
    parent: ArcWeak<Mutex<Node>>,
    children: Vec<Arc<Mutex<Node>>>,
}

let mut fory = Fory::default();
fory.register::<Node>(6000);

// Build a parent-child tree with Arc/Mutex
let parent = Arc::new(Mutex::new(Node {
    val: 10,
    parent: ArcWeak::new(),
    children: vec![],
}));

let child1 = Arc::new(Mutex::new(Node {
    val: 20,
    parent: ArcWeak::from(&parent),
    children: vec![],
}));

let child2 = Arc::new(Mutex::new(Node {
    val: 30,
    parent: ArcWeak::from(&parent),
    children: vec![],
}));

parent.lock().unwrap().children.push(child1.clone());
parent.lock().unwrap().children.push(child2.clone());

// Serialize and deserialize the circular structure
let serialized = fory.serialize(&parent);
let deserialized: Arc<Mutex<Node>> = fory.deserialize(&serialized)?;

// Verify the circular relationship
assert_eq!(deserialized.lock().unwrap().children.len(), 2);
for child in &deserialized.lock().unwrap().children {
    let upgraded_parent = child.lock().unwrap().parent.upgrade().unwrap();
    assert!(Arc::ptr_eq(&deserialized, &upgraded_parent));
}
```

#### Implementation Details

The reference tracking system uses four reference flags:

- **`NotNullValue`**: First occurrence of an object that won't be referenced again
- **`RefValue`**: First occurrence of an object that will be referenced later
- **`Ref`**: Subsequent reference to an already-serialized object (followed by reference ID)
- **`Null`**: Null reference

During serialization:

1. When encountering an `Rc<T>`/`Arc<T>`, check if it was seen before
2. If first time: write `RefValue` flag, assign reference ID, serialize data
3. If seen before: write `Ref` flag + reference ID

During deserialization:

1. Read reference flag
2. If `RefValue`: deserialize object, store with reference ID
3. If `Ref`: lookup object by reference ID
4. For weak pointers appearing before targets: register callback for later resolution

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

### Smart Pointers

- `Box<T>` for heap-allocated values
- `Rc<T>` for single-threaded reference counting with shared reference tracking
- `Arc<T>` for thread-safe reference counting with shared reference tracking
- `RcWeak<T>` for weak references to `Rc<T>` (breaks circular references)
- `ArcWeak<T>` for weak references to `Arc<T>` (breaks circular references)
- `RefCell<T>` and `Mutex<T>` for interior mutability

### Date and Time

- `chrono::NaiveDate` for dates (requires chrono dependency)
- `chrono::NaiveDateTime` for timestamps (requires chrono dependency)

### Custom Types

- Structs with `#[derive(ForyObject)]` or `#[derive(ForyRow)]`
- Enums with `#[derive(ForyObject)]`
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
- [x] Shared and circular reference tracking with `Rc<T>` and `Arc<T>`
- [x] Weak pointer support with `RcWeak<T>` and `ArcWeak<T>`
- [x] Trait object serialization with polymorphism
- [x] Advanced schema evolution features
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
