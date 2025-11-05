---
title: Java Serialization Guide
sidebar_position: 0
id: java_serialization
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

Apache Fory™ provides blazingly-fast serialization for Java through JIT compilation and zero-copy techniques, delivering up to 170x performance improvement over traditional frameworks. This guide covers Java-specific serialization (`Language.JAVA`), optimized for Java-to-Java communication. For cross-language serialization with Python, Rust, Go, or other languages, see [Cross-Language Serialization](#cross-language-serialization) or the [Xlang Serialization Guide](xlang_serialization_guide.md).

## Quick Start

**Important**: Fory instances are expensive to create. **Reuse them** across serializations by storing as static fields or singleton instances.

### Single-Threaded Usage

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

public class Example {
  public static void main(String[] args) {
    // Create Fory instance for Java-only serialization
    // Note: Fory instances are expensive to create - reuse them!
    Fory fory = Fory.builder()
      .withLanguage(Language.JAVA)  // Use JAVA mode for best performance
      .requireClassRegistration(true)  // Security: only registered types allowed
      .build();
    
    // Register your custom types (order matters if no explicit ID provided)
    // Built-in types (String, Integer, List, etc.) are automatically registered
    fory.register(SomeClass.class);
    
    // Serialize object to bytes
    SomeClass object = new SomeClass();
    byte[] bytes = fory.serialize(object);
    
    // Deserialize bytes back to object
    SomeClass result = (SomeClass) fory.deserialize(bytes);
  }
}
```

### Multi-Threaded Usage

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

public class Example {
  // IMPORTANT: Store Fory as static field for reuse across threads
  // ThreadLocalFory maintains one Fory instance per thread
  private static final ThreadSafeFory fory = new ThreadLocalFory(classLoader -> {
    // This factory is called once per thread to create its Fory instance
    Fory f = Fory.builder()
      .withLanguage(Language.JAVA)
      .withClassLoader(classLoader)  // Use thread's classloader
      .build();
    
    // Register types for this thread's Fory instance
    f.register(SomeClass.class);
    return f;
  });

  public static void main(String[] args) {
    SomeClass object = new SomeClass();
    
    // Thread-safe: automatically uses current thread's Fory instance
    byte[] bytes = fory.serialize(object);
    SomeClass result = (SomeClass) fory.deserialize(bytes);
  }
}
```

**Note for Virtual Threads**: `ThreadLocalFory` creates a Fory instance per thread. For environments using virtual threads (Project Loom), use `buildThreadSafeForyPool()` instead to avoid excessive instance creation.

### Instance Reuse Pattern

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

public class Example {
  // Best Practice: Store Fory as static field for maximum reuse
  // This single instance serves all threads in the application
  private static final ThreadSafeFory fory = new ThreadLocalFory(classLoader -> {
    Fory f = Fory.builder()
      .withLanguage(Language.JAVA)
      .withClassLoader(classLoader)
      .build();
    
    // Register all types once during initialization
    f.register(SomeClass.class);
    return f;
  });

  public static void main(String[] args) {
    SomeClass object = new SomeClass();
    
    // The same 'fory' instance is used for all serialization operations
    byte[] bytes = fory.serialize(object);
    SomeClass result = (SomeClass) fory.deserialize(bytes);
  }
}
```

## Class Registration

Class registration is essential for both **security** and **performance** in Fory serialization. By default, Fory requires explicit type registration to prevent arbitrary code execution and reduce serialization overhead.

### Why Register Classes?

1. **Security**: Prevents deserialization of untrusted types (prevents arbitrary code execution)
2. **Performance**: Eliminates need to serialize full class names (uses compact numeric IDs instead)
3. **Type Safety**: Ensures only expected types can be serialized/deserialized

### Registration Methods

Fory provides three ways to register classes:

#### 1. Auto-Generated ID (Simplest)

```java
Fory fory = Fory.builder().build();

// Fory automatically assigns sequential IDs based on registration order
// WARNING: Order matters! All Fory instances must register in the same order
fory.register(User.class);      // Auto-assigned ID 1 (first registration)
fory.register(Product.class);   // Auto-assigned ID 2 (second registration)
fory.register(Order.class);     // Auto-assigned ID 3 (third registration)

// If another Fory instance registers in different order, deserialization fails:
// fory2.register(Product.class);  // Gets ID 1 (conflict!)
// fory2.register(User.class);      // Gets ID 2 (conflict!)
```

**Pros**:
- Simple and convenient
- No ID management needed

**Cons**:
- **Registration order must be identical** across all Fory instances
- Fragile: adding a class in the middle breaks compatibility
- Not recommended for distributed systems

**Use When**: Single application, no cross-process communication, tight control over registration order.

#### 2. Explicit Numeric ID (Recommended for Production)

```java
Fory fory = Fory.builder().build();

// Explicit IDs allow registration in any order - most robust approach
// IDs are variable-length encoded: smaller IDs = smaller serialized size
fory.register(User.class, 100);     // ID 100: encodes to ~1 byte
fory.register(Product.class, 101);   // Registration order doesn't matter
fory.register(Order.class, 102);     // Can add new types with new IDs safely

// Another Fory instance can register in different order - no problem:
// fory2.register(Order.class, 102);    // Same ID = same type (correct!)
// fory2.register(User.class, 100);     // Order independence ensures compatibility
```

**Pros**:
- Registration order doesn't matter
- Compact serialization (IDs are variable-length encoded)
- Best performance
- Easy to manage with constants or enums

**Cons**:
- Need to coordinate IDs across teams/services
- Risk of ID conflicts

**Best Practice**: Use constants or enums to manage IDs:

```java
// Centralize ID management to prevent conflicts and ensure consistency
public class TypeIds {
    // Start at 100 to avoid conflicts with Fory's internal IDs (< 100)
    public static final int USER = 100;
    public static final int PRODUCT = 101;
    public static final int ORDER = 102;
    // Reserve ranges for different modules: 100-199 user module, 200-299 product module, etc.
}

// All services/processes use the same TypeIds class to ensure consistency
fory.register(User.class, TypeIds.USER);
fory.register(Product.class, TypeIds.PRODUCT);
fory.register(Order.class, TypeIds.ORDER);
```

**Use When**: Production systems, distributed applications, cross-process communication.

#### 3. String-Based Type Name (Flexible but Verbose)

```java
Fory fory = Fory.builder().build();

// String-based registration uses type names instead of numeric IDs
// Namespace prevents collisions between types with same name in different packages
fory.register(User.class, "com.example.domain", "User");
// Empty namespace "" means global scope (use only if name is globally unique)
fory.register(Product.class, "", "UniqueProductType");

// Type name is encoded in serialized data (larger size than numeric ID)
// But offers flexibility: no ID coordination needed between services
```

**Pros**:
- No ID coordination needed
- Human-readable
- Flexible for dynamic scenarios

**Cons**:
- **Significantly larger payload** (full strings vs. compact IDs)
- Slower serialization/deserialization
- Not suitable for high-performance scenarios

**Use When**: Prototyping, debugging, or when ID coordination is impractical.

### Registration Order Considerations

When using auto-generated IDs (`fory.register(Class)`), order matters:

**Correct** - Same order on both sides:
```java
// Service A (Serializer)
// Auto-generated IDs require identical registration order across all instances
fory.register(User.class);      // ID: 1 (first registration)
fory.register(Product.class);   // ID: 2 (second registration)
fory.register(Order.class);     // ID: 3 (third registration)

// Service B (Deserializer) - SAME ORDER ensures compatibility
fory.register(User.class);      // ID: 1 matches Service A
fory.register(Product.class);   // ID: 2 matches Service A
fory.register(Order.class);     // ID: 3 matches Service A
// Deserialization succeeds because IDs align with types
```

**Incorrect** - Different order causes failures:
```java
// Service A
fory.register(User.class);      // ID: 1
fory.register(Product.class);   // ID: 2
fory.register(Order.class);     // ID: 3

// Service B - WRONG ORDER causes type mismatch!
fory.register(Order.class);     // ID: 1 (but serialized data has User at ID 1)
fory.register(User.class);      // ID: 2 (but serialized data has Product at ID 2)
fory.register(Product.class);   // ID: 3 (but serialized data has Order at ID 3)
// Deserialization will fail with ClassCastException or produce corrupted objects
```

### Built-in Type Registration

Fory automatically handles common types without explicit registration:
- Primitives: `int`, `long`, `double`, `boolean`, etc.
- Wrappers: `Integer`, `Long`, `Double`, `Boolean`, etc.
- Standard types: `String`, `Date`, `Timestamp`, etc.
- Collections: `List`, `ArrayList`, `HashMap`, `HashSet`, etc.
- Arrays: `int[]`, `String[]`, `Object[]`, etc.

Only register **your custom types**.

### Nested Type Registration

When registering types with nested objects, register **all custom types** in the object graph:

```java
public class Order {
    private User user;           // Custom type - must register
    private List<Product> items; // Product is custom type - must register
    private String status;       // String is built-in - no registration needed
    private int totalAmount;     // Primitive - no registration needed
}

// Register all custom types in the object graph
// Fory traverses object references during serialization and needs serializers for all types
fory.register(User.class, 100);     // Register referenced types first
fory.register(Product.class, 101);  // Order: doesn't matter with explicit IDs
fory.register(Order.class, 102);    // Register container type last (or any order)
```

### Polymorphic Type Registration

For polymorphic scenarios, register the base class and all implementations:

```java
interface Animal { }
class Dog implements Animal { }
class Cat implements Animal { }

// Register interface/base class first, then all implementations
// This enables Fory to serialize the concrete type and deserialize to correct subclass
fory.register(Animal.class, 200);  // Base interface
fory.register(Dog.class, 201);     // Implementation 1
fory.register(Cat.class, 202);     // Implementation 2

// Fory serializes concrete type (Dog/Cat) even when declared as Animal
List<Animal> animals = Arrays.asList(new Dog(), new Cat());
### Disabling Class Registration (Not Recommended)

For trusted environments only:

```java
Fory fory = Fory.builder()
    // ⚠️ SECURITY RISK: Allows deserialization of ANY class on the classpath
    // This bypasses Fory's security protection against arbitrary code execution
    .requireClassRegistration(false)  // Disables security whitelist
    .build();

// No registration needed - Fory serializes full class names instead of IDs
// Any class can now be deserialized, including potentially malicious types
byte[] bytes = fory.serialize(anyObject);  // Works for unregistered types
Object result = fory.deserialize(bytes);    // Can instantiate ANY class!
``` .build();

// No registration needed, but any class can be deserialized
byte[] bytes = fory.serialize(anyObject);
```

**⚠️ Warning**: Disabling class registration allows arbitrary code execution during deserialization. Only use in completely trusted environments. See [Security](#security) for safer alternatives.

### Cross-Language Registration

For cross-language scenarios, types must be registered with **identical IDs or names** in all languages:

```java
// Java
fory.register(Person.class, 300);  // or fory.register(Person.class, "example.Person");

// Python (must use same ID or name)
fory.register_type(Person, type_id=300)  # or typename="example.Person"
```

See [Cross-Language Serialization](#cross-language-serialization) for details.

## Configuration

### ForyBuilder Options

Configure Fory using `ForyBuilder` to optimize for your use case:

| Option                              | Description                                                                                                                                                                                           | Default                                          |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `timeRefIgnored`                    | Ignore reference tracking for time types when ref tracking enabled. Enable per-type: `fory.registerSerializer(Date.class, new DateSerializer(fory, true))` before codegen.                          | `true`                                           |
| `compressInt`                       | Variable-length int compression (1-5 bytes).                                                                                                                                                          | `true`                                           |
| `compressLong`                      | Variable-length long compression (SLI or PVL encoding).                                                                                                                                               | `true`                                           |
| `compressIntArray`                  | SIMD-accelerated `int[]` compression. Requires Java 16+ and `fory-simd`.                                                                                                                             | `true`                                           |
| `compressLongArray`                 | SIMD-accelerated `long[]` compression. Requires Java 16+ and `fory-simd`.                                                                                                                            | `true`                                           |
| `compressString`                    | String compression.                                                                                                                                                                                   | `false`                                          |
| `classLoader`                       | ClassLoader for type resolution. Use `LoaderBinding`/`ThreadSafeFory` for dynamic classloaders.                                                                                                      | `Thread.currentThread().getContextClassLoader()` |
| `compatibleMode`                    | `SCHEMA_CONSISTENT`: schemas must match. `COMPATIBLE`: supports field addition/deletion. See [Schema Evolution](#schema-evolution).                                                                  | `SCHEMA_CONSISTENT`                              |
| `checkClassVersion`                 | Validate schema consistency via `classVersionHash`. Auto-disabled in `COMPATIBLE` mode.                                                                                                              | `false`                                          |
| `checkJdkClassSerializable`         | Require `Serializable` for `java.*` classes.                                                                                                                                                         | `true`                                           |
| `registerGuavaTypes`                | Pre-register Guava types (`RegularImmutableMap`, `RegularImmutableList`).                                                                                                                            | `true`                                           |
| `requireClassRegistration`          | Require explicit class registration. **Disabling is a security risk!**                                                                                                                               | `true`                                           |
| `maxDepth`                          | Maximum object graph depth (prevents stack overflow).                                                                                                                                                | `50`                                             |
| `suppressClassRegistrationWarnings` | Suppress unregistered class warnings.                                                                                                                                                                | `true`                                           |
| `metaShareEnabled`                  | Share metadata across serializations. Auto-enabled in `COMPATIBLE` mode.                                                                                                                             | `false` (`true` in `COMPATIBLE`)                 |
| `scopedMetaShareEnabled`            | Per-serialization metadata sharing.                                                                                                                                                                  | `false` (`true` in `COMPATIBLE`)                 |
| `metaCompressor`                    | Thread-safe metadata compressor (`DeflaterMetaCompressor` default, consider `zstd`).                                                                                                                 | `DeflaterMetaCompressor`                         |
| `deserializeNonexistentClass`       | Deserialize non-existent classes into lazy `Map`. Auto-enabled in `COMPATIBLE` mode.                                                                                                                 | `false` (`true` in `COMPATIBLE`)                 |
| `codeGenEnabled`                    | Enable JIT code generation.                                                                                                                                                                           | `true`                                           |
| `asyncCompilationEnabled`           | Use interpreter initially while JIT compiles asynchronously.                                                                                                                                          | `false`                                          |
| `scalaOptimizationEnabled`          | Enable Scala-specific optimizations.                                                                                                                                                                 | `false`                                          |
| `copyRef`                           | Track references during deep copy. Disabling improves performance but breaks shared/circular references.                                                                                             | `true`                                           |
| `serializeEnumByName`               | Serialize enums by name (better compatibility when order changes).                                                                                                                                   | `false`                                          |

## Advanced Features

### Custom Fory Configuration

#### Single-Threaded Configuration

```java
// Configure Fory with custom options for single-threaded use
Fory fory = Fory.builder()
  .withLanguage(Language.JAVA)  // Java-only protocol (most efficient)
  .withRefTracking(false)  // Disable if no shared/circular references (20-30% faster)
  .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)  // Or .COMPATIBLE for schema evolution support
  .withAsyncCompilation(true)  // Use interpreter first, compile in background (reduces latency spikes)
  .build();

// Reuse this fory instance for all serialization operations in the thread
byte[] bytes = fory.serialize(object);
Object result = fory.deserialize(bytes);
```

#### Thread-Safe Configuration

```java
// ThreadSafeFory wraps per-thread Fory instances for safe concurrent use
ThreadSafeFory fory = Fory.builder()
  .withLanguage(Language.JAVA)
  .withRefTracking(false)  // Disable if your data has no shared/circular references
  .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)  // Schema must match exactly
  .withAsyncCompilation(true)  // Async JIT reduces first-call latency
  .buildThreadSafeFory();  // Creates ThreadLocalFory under the hood

// Same fory instance used from multiple threads - thread-safe
byte[] bytes = fory.serialize(object);  // Uses thread-local Fory instance
Object result = fory.deserialize(bytes);
```

**Virtual Thread Consideration**: `buildThreadSafeFory()` creates `ThreadLocalFory`, which creates one Fory instance per thread. For virtual thread environments, use `buildThreadSafeForyPool()` to avoid excessive instance creation.

### Schema Evolution

Schema evolution allows serialization and deserialization with different class versions. Enable `COMPATIBLE` mode when class definitions may change over time.

**Default Behavior** (`SCHEMA_CONSISTENT`):
- Assumes identical schemas on both ends
- Minimal overhead, maximum performance
- Deserialization fails if schemas differ

**Compatible Mode** (`COMPATIBLE`):
- Supports field additions and deletions
- Forward and backward compatibility
- Additional metadata overhead for flexibility
succeed even when the serialization and deserialization processes have different class schemas.

**Enable Compatible Mode**:

```java
// COMPATIBLE mode serializes class schema (field names/types) with data
// This enables forward/backward compatibility at the cost of larger payload
Fory fory = Fory.builder()
  .withLanguage(Language.JAVA)
  .withCompatibleMode(CompatibleMode.COMPATIBLE)  // Enable schema evolution
  .build();

// Serialized data includes class metadata for compatibility
byte[] bytes = fory.serialize(object);

// Can deserialize even if class definition changed (fields added/removed)
Object result = fory.deserialize(bytes);
```

Compatible mode serializes class metadata (field names, types) with the data. Despite aggressive compression, this adds overhead. Fory supports [metadata sharing](https://fory.apache.org/docs/specification/fory_java_serialization_spec#meta-share) to send metadata once per connection/session, reducing costs.

### Numeric Compression

Fory provides variable-length encoding for integers and longs, enabled by default. Disable for performance-critical numeric workloads.

**Int Compression** (1-5 bytes):
- Uses variable-length encoding with continuation bits
- First bit indicates if more bytes follow

**Long Compression** (SLI encoding - default):
- Values in `[-1073741824, 1073741823]`: 4 bytes
- Other values: 9 bytes (1 flag byte + 8 data bytes)
- Alternative: PVL encoding with zigzag conversion for negative numbers

**When to Disable**:
- Data consists primarily of large numbers
- Using uncompressed formats previously (e.g., Flatbuffers)
- Compression causes >50% performance degradation for your workload

```java
// Disable compression when working with large numbers or when raw speed is critical
Fory fory = Fory.builder()
  .withIntCompressed(false)   // Ints always use 4 bytes (faster, larger)
  .withLongCompressed(false)  // Longs always use 8 bytes (faster, larger)
  .build();

// Trade-off: ~10-20% faster serialization but 20-40% larger payload for typical data
```

### Array Compression

SIMD-accelerated compression for `int[]` and `long[]` when values fit in smaller types. Requires Java 16+ and `fory-simd` module.

**Compression Ratios**:
- `int[]` → `byte[]`: 75% size reduction (values in [-128, 127])
**Configuration**:

```java
// SIMD array compression requires Java 16+ Vector API
Fory fory = Fory.builder()
  .withLanguage(Language.JAVA)
  .withIntArrayCompressed(true)   // Enable int[] compression
  .withLongArrayCompressed(true)  // Enable long[] compression
  .build();

// Must explicitly register compressed serializers (not registered by default)
CompressedArraySerializers.registerSerializers(fory);

// Now int[] and long[] use SIMD-accelerated compression
// Example: int[] with values [1, 2, 3] compressed to byte[] (75% smaller)
```
// Must register compressed serializers
CompressedArraySerializers.registerSerializers(fory);
```

**Maven Dependency**:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-simd</artifactId>
  <version>0.13.0</version>
</dependency>
```

### Deep Copy

Fory provides efficient deep copying with optional reference tracking:

**With Reference Tracking** (preserves shared/circular references):

```java
// Enable reference tracking to preserve object identity during copy
Fory fory = Fory.builder().withRefCopy(true).build();
SomeClass copied = fory.copy(original);

// Shared references preserved: if original.child == original.parent.child
// then copied.child == copied.parent.child (same instance in copied graph)
```

**Without Reference Tracking** (faster, but duplicates shared objects):

```java
// Disable reference tracking for faster copy when object graph is a tree
Fory fory = Fory.builder().withRefCopy(false).build();
SomeClass copied = fory.copy(original);

// Shared references NOT preserved: if original.child == original.parent.child
// then copied.child != copied.parent.child (duplicated instances)
// Trade-off: ~30% faster but breaks object identity
```

## Cross-Language Serialization

Fory supports cross-language serialization via `Language.XLANG` mode, enabling data exchange between Java, Python, Rust, Go, JavaScript, and other languages. For comprehensive cross-language documentation, see the [Xlang Serialization Guide](xlang_serialization_guide.md).

### Quick Start

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

// XLANG mode enables cross-language serialization (Java, Python, Rust, Go, JS, etc.)
// Uses language-agnostic protocol with explicit type encoding
Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)  // Cross-language protocol (slower than Language.JAVA)
    .withRefTracking(true)  // Enable for graphs with shared/circular references
    .build();

// Register types with consistent IDs or names across ALL languages
// See Type Registration section for details
```

**1. Register by ID (Recommended for Performance)**

```java
public record Person(String name, int age) {}

// Register with numeric ID (same ID must be used in Python/Rust/Go/etc.)
// IDs are compact (1-2 bytes) - best for performance
fory.register(Person.class, 300);  // All languages use ID 300 for Person

byte[] bytes = fory.serialize(new Person("Alice", 30));
// Serialized bytes contain ID 300 instead of class name "Person"
// Other languages deserialize using their type registered with ID 300
```y fory = Fory.builder()
    .withLanguage(Language.XLANG)
    .withRefTracking(true)
    .build();
```

**2. Register by Name (Recommended for Flexibility)**

```java
// Register using string typename (same name used in all languages)
// More flexible: no ID coordination needed, but larger payload (full type name)
fory.register(Person.class, "example.Person");  // All languages use "example.Person"

byte[] bytes = fory.serialize(new Person("Bob", 25));
// Serialized bytes contain type name "example.Person" (compressed via meta string)
// Trade-off: +5-20 bytes per unique type, but no ID conflicts possible
```

### Java ↔ Python Example

**Java**:

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

### Java ↔ Python Example

**Java (Serialization)**:

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

// Define Java record for person data
public record Person(String name, int age) {}

// Create XLANG Fory instance for cross-language serialization
Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)  // Must use XLANG mode for cross-language
    .build();

// Register type with string name (must match Python registration)
fory.register(Person.class, "example.Person");

// Serialize Java object to bytes
byte[] bytes = fory.serialize(new Person("Alice", 30));
// Send bytes to Python via network, file, or other transport...
```

**Python (Deserialization)**:

```python
import pyfory
### Shared and Circular References

Enable reference tracking for object graphs with shared or circular references:

```java
public class Node {
    public String value;
    public Node next;    // Forward reference
    public Node parent;  // Back reference (creates cycle)
}

// Reference tracking prevents infinite recursion and duplicate data
Fory fory = Fory.builder()
    .withLanguage(Language.XLANG)
    .withRefTracking(true)  // REQUIRED for circular/shared references
    .build();

fory.register(Node.class, "example.Node");

// Create circular reference: A -> B -> A
Node node1 = new Node();
node1.value = "A";
Node node2 = new Node();

node2.value = "B";
node1.next = node2;
node2.parent = node1;  // Circular reference: A.next=B, B.parent=A

byte[] bytes = fory.serialize(node1);
// With ref tracking: each object serialized once, references encoded as IDs
// Without ref tracking: would cause infinite recursion (stack overflow)
// Other languages (Python, Rust, etc.) correctly deserialize circular references
```

### Type Compatibility

Use portable types for maximum cross-language compatibility:

**Compatible**:
- Primitives: `int`, `long`, `double`, `String`, `boolean`
- Collections: `List`, `Map`, `Set`
- Arrays: `int[]`, `byte[]`, `String[]`

**Avoid**:
- Java-specific: `Optional`, `BigDecimal`, `EnumSet`
- JDK implementation details

### Performance Considerations

- **Use ID registration** for smaller payloads
- **Disable ref tracking** if no circular references (`withRefTracking(false)`)
- **Use Java mode** (`Language.JAVA`) when cross-language support isn't needed

### Learn More

For comprehensive cross-language serialization documentation, including:
- Type mapping between languages
- Schema evolution across services
- Advanced polymorphism support
- Zero-copy serialization
- Language-specific examples

See the **[Xlang Serialization Guide](xlang_serialization_guide.md)**.

## Custom Serializers

### Implementing Custom Serializers

Implement custom serializers for types with special requirements or to bypass inefficient JDK serialization (`writeObject`/`readObject`):

**Example**: Custom serializer bypassing JDK `writeObject`:

```java
// Original class with expensive JDK serialization
class Foo {
  public long f1;
  
  // Slow: JDK writeObject calls expensive System.out.println on every serialization
  private void writeObject(ObjectOutputStream s) throws IOException {
    System.out.println(f1);  // Expensive I/O operation
    s.defaultWriteObject();
  }
}

// Custom Fory serializer bypasses writeObject for 10-100x speedup
class FooSerializer extends Serializer<Foo> {
  public FooSerializer(Fory fory) {
    super(fory, Foo.class);
  }

  // Direct field serialization (fast path, no writeObject called)
  @Override
  public void write(MemoryBuffer buffer, Foo value) {
    buffer.writeInt64(value.f1);  // Direct write, ~10ns
  }

  @Override
  public Foo read(MemoryBuffer buffer) {
    Foo foo = new Foo();
    foo.f1 = buffer.readInt64();  // Direct read, ~10ns
    return foo;
  }
}

// Register custom serializer to override default behavior
fory.registerSerializer(Foo.class, new FooSerializer(fory));
```

### Collection Serializers

When implementing collection serializers, extend `CollectionSerializer` or `CollectionLikeSerializer`. The `supportCodegenHook` parameter controls JIT optimization:

- `true`: Enables JIT compilation for better performance (recommended for standard collections)
- `false`: Uses dynamic dispatch (required for complex custom collections)

**With JIT Support**:

```java
// Custom serializer for collection types supporting JIT optimization
public class CustomCollectionSerializer<T extends Collection> extends CollectionSerializer<T> {
    public CustomCollectionSerializer(Fory fory, Class<T> cls) {
        // Pass true to enable JIT: Fory generates bytecode for element serialization
        super(fory, cls, true);  // JIT compiles element writes for 2-5x speedup
    }

    @Override
    public Collection onCollectionWrite(MemoryBuffer buffer, T value) {
        // Write collection header (size)
        buffer.writeVarUint32Small7(value.size());
        // Return collection: Fory's JIT-compiled code handles element serialization
        return value;  // JIT writes elements using generated bytecode
    }

    @Override
    public Collection newCollection(MemoryBuffer buffer) {
        // Read collection header and prepare container
        int numElements = buffer.readVarUint32Small7();
        setNumElements(numElements);  // Tell Fory how many elements to read
        // Fory's JIT-compiled code populates this collection with deserialized elements
        return new ArrayList(numElements);
    }
}
```

**Without JIT** (for primitive arrays or special cases):

```java
// Custom serializer for primitive collection (no JIT needed)
class IntListSerializer extends CollectionLikeSerializer<IntList> {
    public IntListSerializer(Fory fory) {
        // Pass false: JIT doesn't benefit primitive types (manual loop is faster)
        super(fory, IntList.class, false);  // Disable JIT overhead
    }

    @Override
    public void write(MemoryBuffer buffer, IntList value) {
        buffer.writeVarUint32Small7(value.size());
        // Manual loop for primitives: faster than JIT for simple types
        for (int i = 0; i < value.size(); i++) {
            buffer.writeVarInt32(value.get(i));  // Direct primitive write (~5ns/element)
        }
    }

    @Override
    public IntList read(MemoryBuffer buffer) {
        int size = buffer.readVarUint32Small7();
        int[] elements = new int[size];
        // Manual loop avoids boxing/unboxing overhead
        for (int i = 0; i < size; i++) {
            elements[i] = buffer.readVarInt32();  // Direct primitive read
        }
        return new IntList(elements, size);
    }

    // These methods unused when JIT disabled (supportCodegenHook=false)
    @Override
    public Collection onCollectionWrite(MemoryBuffer buffer, IntList value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection newCollection(MemoryBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IntList onCollectionRead(Collection collection) {
        throw new UnsupportedOperationException();
    }
}
```

### Map Serializers

Map serializers follow similar patterns to collection serializers:

**With JIT Support**:

```java
// Custom map serializer with JIT optimization for key/value serialization
public class CustomMapSerializer<T extends Map> extends MapSerializer<T> {
    public CustomMapSerializer(Fory fory, Class<T> cls) {
        // Enable JIT: Fory generates bytecode for key/value serialization
        super(fory, cls, true);  // JIT compiles key/value writes for 2-5x speedup
    }

    @Override
    public Map onMapWrite(MemoryBuffer buffer, T value) {
        // Write map header (number of entries)
        buffer.writeVarUint32Small7(value.size());
        // Return map: Fory's JIT code handles key/value serialization
        return value;  // JIT writes entries using generated bytecode
    }

    @Override
    public Map newMap(MemoryBuffer buffer) {
        // Read map header and prepare container
        int numElements = buffer.readVarUint32Small7();
        setNumElements(numElements);  // Tell Fory how many entries to read
        T map = (T) new HashMap(numElements);
        // Register map for reference tracking (important for circular refs)
        fory.getRefResolver().reference(map);
        return map;  // Fory's JIT code populates with deserialized entries
    }
}
```

For complete collection and map serializer examples (including collection-like and map-like types), see the original documentation sections that follow.

### Alternative: Externalizable

For classes that need custom serialization, implementing `java.io.Externalizable` is another option. Fory's `ExternalizableSerializer` handles such types automatically.

## Memory Management

### Custom Memory Allocation

Customize memory buffer allocation for pooling, debugging, or off-heap memory:

```java
// Custom allocator for memory pooling or special allocation strategies
MemoryAllocator customAllocator = new MemoryAllocator() {
    @Override
    public MemoryBuffer allocate(int initialCapacity) {
        // Add extra capacity to reduce reallocation frequency
        // Or use custom allocation strategy (pooled buffers, off-heap memory, etc.)
        return MemoryBuffer.fromByteArray(new byte[initialCapacity + 100]);
    }

    @Override
    public MemoryBuffer grow(MemoryBuffer buffer, int newCapacity) {
        if (newCapacity <= buffer.size()) {
            return buffer;  // No growth needed
        }
        // Custom growth strategy: 2x growth (default is 1.5x-2x)
        // Aggressive growth reduces reallocations at cost of memory
        int newSize = newCapacity * 2;
        byte[] data = new byte[newSize];
        // Copy existing data to new buffer
        buffer.copyToUnsafe(0, data, Platform.BYTE_ARRAY_OFFSET, buffer.size());
        buffer.initHeapBuffer(data, 0, data.length);
        return buffer;
    }
};

// Set allocator globally for all Fory instances in this JVM
MemoryBuffer.setGlobalAllocator(customAllocator);
```

**Use Cases**:
- Memory pooling to reduce GC pressure
- Custom growth strategies
- Off-heap memory integration
- Memory usage tracking/debugging

## Security

### Class Registration

**Always require class registration in production** to prevent arbitrary code execution during deserialization.

```java
// Default configuration: secure by default
Fory fory = Fory.builder()
    .requireClassRegistration(true)  // Enabled by default (security whitelist)
    .build();

// Explicitly register allowed types (whitelist approach)
fory.register(SafeClass1.class);          // Allow SafeClass1
fory.register(SafeClass2.class, 100);     // Allow SafeClass2 with ID 100
// Any other type will be rejected during deserialization
```

**Disabling class registration is dangerous** and should only be done in trusted environments. If you must disable it, use a `ClassChecker`:

```java
// ⚠️ SECURITY RISK: Disabling registration requires ClassChecker for safety
Fory fory = Fory.builder()
    .requireClassRegistration(false)  // Allow unregistered types (dangerous!)
    .build();

// Whitelist specific packages to limit attack surface
// Only classes matching "com.example.*" can be deserialized
**AllowListChecker** for more sophisticated control:

```java
// AllowListChecker provides fine-grained control over allowed classes
AllowListChecker checker = new AllowListChecker(AllowListChecker.CheckLevel.STRICT);

// Create ThreadLocalFory with AllowListChecker for thread-safe security
ThreadSafeFory fory = new ThreadLocalFory(classLoader -> {
    Fory f = Fory.builder()
        .requireClassRegistration(false)  // Disable registration requirement
        .withClassLoader(classLoader)
        .build();
    // Install checker to validate all deserialized classes
    f.getClassResolver().setClassChecker(checker);
    checker.addListener(f.getClassResolver());  // Sync checker state
    return f;
});

// Allow specific package patterns (supports wildcards)
checker.allowClass("com.example.*");  // Allow all classes in com.example package
### Depth Limiting

Protect against deeply nested object graph attacks:

```java
// Limit object graph depth to prevent stack overflow attacks
Fory fory = Fory.builder()
    .withMaxDepth(50)  // Default is 50 levels
    .build();

// Example attack: A -> B -> C -> ... (1000 levels deep)
// Fory throws ForyException when depth > 50, preventing stack overflow
// Adjust limit based on legitimate use case requirements
```
```java
Fory fory = Fory.builder()
    .withMaxDepth(50)  // Default is 50
    .build();
```

Fory throws `ForyException` when max depth is exceeded.
### String-Based Registration (Detailed)

When numeric ID coordination is impractical (e.g., many independent teams, dynamic class loading), use string-based registration:

```java
// With namespace (recommended): prevents naming conflicts between modules
// Namespace acts as a package/module identifier
fory.register(Foo.class, "com.example.models", "Foo");
fory.register(Bar.class, "com.example.models", "Bar");
// Another team can safely use "com.other.models::Foo" without conflict

// Without namespace (risky): only if names are globally unique across all teams
fory.register(UniqueClass.class, "", "GloballyUniqueClassName");
// Risk: If another team uses same name, deserialization will fail
```
// Without namespace (only if names are globally unique)
fory.register(UniqueClass.class, "", "UniqueClass");
```

**Trade-offs**:
- ✅ No ID coordination needed across teams
- ✅ Descriptive and human-readable
- ✅ Safe from ID conflicts
- ❌ **3-10x larger payload** (strings vs. varint IDs)
- ❌ Slower serialization (string hashing and comparison)
- ❌ Higher CPU usage

**When to Use**:
- Prototyping and debugging
- Systems with hundreds of types from different teams
- Dynamic class loading scenarios
- When serialization performance is not critical

**Production Recommendation**: Use explicit numeric IDs (see [Class Registration](#class-registration)) for better performance and smaller payloads.

### Zero-Copy Serialization

Separate large buffers from main payload for zero-copy transfers:

```java
import org.apache.fory.serializer.BufferObject;
import java.util.*;
import java.util.stream.Collectors;

Fory fory = Fory.builder().withLanguage(Language.JAVA).build();

// Create list with large arrays that we want to transfer without copying
List<Object> list = Arrays.asList("str", new byte[1000], new int[100]);

// Serialize: extract large buffers for zero-copy transfer
Collection<BufferObject> bufferObjects = new ArrayList<>();
// Filter function extracts buffers: returns false to extract, true to inline
byte[] bytes = fory.serialize(list, e -> !bufferObjects.add(e));

// bufferObjects now contains large arrays (byte[1000], int[100])
// These can be transferred via zero-copy mechanisms (e.g., direct I/O, shared memory)
List<MemoryBuffer> buffers = bufferObjects.stream()
    .map(BufferObject::toBuffer)  // Convert to MemoryBuffer for deserialization
    .collect(Collectors.toList());

// Deserialize: provide extracted buffers separately
Object result = fory.deserialize(bytes, buffers);
// Result is identical to original list, but large arrays weren't copied in serialization
```

### Meta Sharing

Share class metadata across serializations in a session/connection:

```java
// Single-threaded usage: reuse MetaContext across multiple serializations
MetaContext context = new MetaContext();
fory.getSerializationContext().setMetaContext(context);
// First serialization sends full metadata (field names, types)
byte[] bytes1 = fory.serialize(obj1);
// Subsequent serializations reference previously sent metadata (smaller payload)
byte[] bytes2 = fory.serialize(obj2);

// Thread-safe usage: execute lambda with per-call MetaContext
MetaContext context = new MetaContext();
byte[] bytes = fory.execute(f -> {
    // Set context for this serialization only
    f.getSerializationContext().setMetaContext(context);
    return f.serialize(obj);
});
### Deserialize Non-Existent Classes

Enable deserialization when classes don't exist on the deserializing side:

```java
// COMPATIBLE mode allows deserializing types not present on classpath
Fory fory = Fory.builder()
    .withCompatibleMode(CompatibleMode.COMPATIBLE)  // Enables schema evolution
    .deserializeNonexistentClass(true)  // Auto-enabled in COMPATIBLE mode
    .build();

// If class "com.example.Foo" doesn't exist on this JVM:
// Fory deserializes into lazy Map implementation instead of failing
// Map keys are field names, values are field values
// Allows forward compatibility: old services can read new types as generic data
```
    .build();
### Type Mapping Between Objects

Map objects from one type to another (deep copy with type conversion):

```java
static class Struct1 {
    int f1;
    String f2;
}

static class Struct2 {
    int f1;
    String f2;
    double f3;  // Extra field (will get default value)
}

// Create separate Fory instances for each type
static Fory fory1 = Fory.builder()
    .withCompatibleMode(CompatibleMode.COMPATIBLE)  // Required for type mapping
    .build();
static Fory fory2 = Fory.builder()
    .withCompatibleMode(CompatibleMode.COMPATIBLE)
    .build();

static {
    // CRITICAL: Both types must use SAME ID for type mapping to work
    fory1.register(Struct1.class, 100);  // Struct1 uses ID 100
    fory2.register(Struct2.class, 100);  // Struct2 also uses ID 100
}

// Serialize Struct1, deserialize as Struct2
Struct1 struct1 = new Struct1(10, "abc");
byte[] bytes = fory1.serialize(struct1);
Struct2 struct2 = (Struct2) fory2.deserialize(bytes);
// struct2.f1 == 10, struct2.f2 == "abc", struct2.f3 == 0.0 (default for missing field)
```Map Struct1 → Struct2
Struct1 struct1 = new Struct1(10, "abc");
Struct2 struct2 = (Struct2) fory2.deserialize(fory1.serialize(struct1));
// struct2.f1 == 10, struct2.f2 == "abc", struct2.f3 == 0.0 (default)
```

**Requirements**:
- Both types registered with same ID
### Deserialize Into Different Type

Deserialize POJO into a different class with schema differences:

```java
static class Struct1 {
    int f1;
    String f2;
}

static class Struct2 {
    int f1;
    String f2;
    double f3;  // Additional field not in Struct1
}

// Single Fory instance handles type conversion
Fory fory = Fory.builder()
    .withCompatibleMode(CompatibleMode.COMPATIBLE)  // Required for schema evolution
    .build();

// Serialize Struct1 with full class metadata
Struct1 struct1 = new Struct1(10, "abc");
byte[] data = fory.serializeJavaObject(struct1);  // Includes class schema

// Deserialize into Struct2: Fory maps common fields, defaults missing fields
Struct2 struct2 = fory.deserializeJavaObject(data, Struct2.class);
// struct2.f1 == 10, struct2.f2 == "abc", struct2.f3 == 0.0 (default)
```uct1 struct1 = new Struct1(10, "abc");
byte[] data = fory.serializeJavaObject(struct1);
### From JDK Serialization

Gradually migrate from JDK serialization in production:

```java
byte[] bytes = ...; // From network/storage (may be JDK or Fory serialized)

// Detect serialization format by checking magic bytes
if (JavaSerializer.serializedByJDK(bytes)) {
    // Old path: deserialize using JDK serialization
    // Checks for JDK magic bytes: 0xaced
    ObjectInputStream ois = ...;
    return ois.readObject();
} else {
    // New path: deserialize using Fory
    // All new serializations use Fory format
    return fory.deserialize(bytes);
}

// Strategy: New writes use Fory, reads support both formats
// Gradually eliminates JDK-serialized data from system
``` return ois.readObject();
} else {
    // New: Fory serialization
    return fory.deserialize(bytes);
}
```

Deploy this logic, then gradually migrate writers to Fory.

### Between Fory Versions

Binary compatibility is guaranteed within minor versions (e.g., 0.13.0 → 0.13.1) but not across major versions (e.g., 0.13.x → 0.14.x).

**For major version upgrades**, version the data:

```java
// Serialization: embed Fory version in data stream
MemoryBuffer buffer = ...;
buffer.writeVarInt32(13);  // Fory version number (e.g., 13 for v0.13.x)
fory.serialize(buffer, obj);  // Serialize object with current Fory version

// Deserialization: read version and use appropriate Fory instance
MemoryBuffer buffer = ...;
int foryVersion = buffer.readVarInt32();  // Extract Fory version
Fory fory = getForyInstance(foryVersion);  // Load Fory 0.13.x or 0.14.x instance
return fory.deserialize(buffer);  // Deserialize with matching version

// Use shading/relocation to run multiple Fory versions simultaneously in same JVM
```

## Troubleshooting

### Class Schema Inconsistency

**Symptom**: Unexpected serialization errors despite same class name.

**Diagnosis**: Enable class version checking:

```java
// Enable automatic schema consistency validation
Fory fory = Fory.builder()
    .withClassVersionCheck(true)  // Validates classVersionHash during deserialization
    .build();

// If schemas differ: throws ClassNotCompatibleException with details
// Exception shows which fields changed between serialization and deserialization
**Solution**: Enable compatible mode:

```java
// COMPATIBLE mode handles schema differences (field additions/deletions)
Fory fory = Fory.builder()
    .withCompatibleMode(CompatibleMode.COMPATIBLE)  // Enables schema evolution
    .build();

// Now deserialization succeeds even if serialized class had different fields
// Trade-off: +10-30% overhead for metadata, but enables forward/backward compatibility
```

**Note**: Only enable compatible mode if needed—it has performance and space overhead.

### Wrong Deserialization API

Use the correct deserialization method matching your serialization call:

| Serialization Method             | Deserialization Method            |
| -------------------------------- | --------------------------------- |
| `fory.serialize(obj)`            | `fory.deserialize(bytes)`         |
| `fory.serializeJavaObject(obj)`  | `fory.deserializeJavaObject(bytes)` or `fory.deserializeJavaObject(bytes, Class)` |
| `fory.serializeJavaObjectAndClass(obj)` | `fory.deserializeJavaObjectAndClass(bytes)` |

### Performance Issues

1. **Ensure Fory reuse**: Create once, use many times
2. **Warm up JIT**: Run serialization multiple times before measuring
3. **Disable compression** for numeric-heavy workloads
4. **Enable async compilation**: `withAsyncCompilation(true)`
5. **Profile your specific workload**: Different data patterns have different optimal configurations

### Registration Order Mismatch

When using `fory.register(Class)` without explicit IDs, registration order must be **identical** across all Fory instances. This is a common source of errors in distributed systems.

**Symptoms**:
- `ClassCastException` during deserialization
- Deserialized objects have wrong types
**Example of the Problem**:

```java
// Service A (Serializer) - registrations in this order
fory.register(User.class);      // Auto-assigned ID: 1
fory.register(Product.class);   // Auto-assigned ID: 2
fory.register(Order.class);     // Auto-assigned ID: 3

// Service B (Deserializer) - DIFFERENT ORDER causes type mismatch!
fory.register(Product.class);   // Auto-assigned ID: 1 (but serialized ID 1 is User!)
fory.register(Order.class);     // Auto-assigned ID: 2 (but serialized ID 2 is Product!)
fory.register(User.class);      // Auto-assigned ID: 3 (but serialized ID 3 is Order!)

// Result: Type confusion during deserialization
// - Serialized User (ID 1) deserialized as Product (ID 1 in Service B)
// - ClassCastException, data corruption, or runtime errors
```
// Result: Wrong types deserialized, ClassCastException, or data corruption
**Solution**: Use explicit IDs to make registration order irrelevant:

```java
// Service A - register with explicit numeric IDs
fory.register(User.class, 100);     // User always ID 100
fory.register(Product.class, 101);  // Product always ID 101
fory.register(Order.class, 102);    // Order always ID 102

// Service B - Registration order doesn't matter with explicit IDs!
fory.register(Order.class, 102);    // ✅ ID 102 = Order (correct!)
fory.register(Product.class, 101);  // ✅ ID 101 = Product (correct!)
fory.register(User.class, 100);     // ✅ ID 100 = User (correct!)

// Deserialization succeeds: IDs uniquely identify types regardless of registration order
```y.register(Product.class, 101);  // ✅ Correct ID
fory.register(User.class, 100);     // ✅ Correct ID
```

**See Also**: [Class Registration](#class-registration) for detailed registration strategies and best practices.

## Further Reading

- **[Xlang Serialization Guide](xlang_serialization_guide.md)**: Cross-language serialization with Python, Rust, Go, JavaScript
- **[Java Serialization Spec](https://fory.apache.org/docs/specification/fory_java_serialization_spec)**: Binary protocol specification
- **[Row Format Guide](https://fory.apache.org/docs/specification/row_format_spec)**: Zero-copy row-based format for analytics
- **[GraalVM Guide](graalvm_guide.md)**: Native image compilation with Fory
- **[Benchmarks](https://fory.apache.org/docs/benchmarks)**: Performance comparisons and methodology

---

**For questions or issues**:
- GitHub: [apache/fory](https://github.com/apache/fory)
- Slack: [Join the community](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw)
- Twitter: [@ApacheFory](https://x.com/ApacheFory)
