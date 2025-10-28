<div align="center">
  <img width="65%" alt="Apache Fory logo" src="docs/images/logo/fory-horizontal.png"><br>
</div>

[![Build Status](https://img.shields.io/github/actions/workflow/status/apache/fory/ci.yml?branch=main&style=for-the-badge&label=GITHUB%20ACTIONS&logo=github)](https://github.com/apache/fory/actions/workflows/ci.yml)
[![Slack Channel](https://img.shields.io/badge/slack-join-3f0e40?logo=slack&style=for-the-badge)](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw)
[![X](https://img.shields.io/badge/@ApacheFory-follow-blue?logo=x&style=for-the-badge)](https://x.com/ApacheFory)
[![Maven Version](https://img.shields.io/maven-central/v/org.apache.fory/fory-core?style=for-the-badge)](https://search.maven.org/#search|gav|1|g:"org.apache.fory"%20AND%20a:"fory-core")
[![Crates.io](https://img.shields.io/crates/v/fory.svg?style=for-the-badge)](https://crates.io/crates/fory)
[![PyPI](https://img.shields.io/pypi/v/pyfory.svg?logo=PyPI&style=for-the-badge)](https://pypi.org/project/pyfory/)

**Apache Fory™** is a blazingly-fast multi-language serialization framework powered by **JIT compilation**, **zero-copy** techniques, and **advanced code generation**, achieving up to **170x performance improvement** while maintaining simplicity and ease of use.

<https://fory.apache.org>

> [!IMPORTANT]
> **Apache Fory™ was previously named as Apache Fury. For versions before 0.11, please use "fury" instead of "fory" in package names, imports, and dependencies, see [Fury Docs](https://fory.apache.org/docs/0.10/docs/introduction/) for how to use Fury in older versions**.

## Why Choose Apache Fory™?

**Compared to Static Serialization Frameworks**:

- No IDL definitions or schema management required
- Automatic handling of complex object graphs with references
- Native support for polymorphic types
- Significantly faster for most workloads

**Compared to Dynamic Serialization Frameworks**:

- Up to 170x better performance through JIT compilation
- Smaller serialized data size with compact binary encoding
- Cross-language support
- Better security with class registration and deserialization policy controls

## Key Features

Apache Fory™ combines extreme performance with rich functionality for modern serialization needs:

- **🚀 Extreme Performance**: JIT-based code generation, zero-copy operations, and optimized memory access deliver industry-leading speed
- **🌍 Multi-Language Support**: Native implementations for Java, Python, C++, Go, JavaScript, Rust, Scala, Kotlin, Dart, and TypeScript with full cross-language interoperability
- **🔄 Automatic Serialization**: No IDL definitions, schema compilation, or manual protocol conversion required
- **📦 Multiple Protocols**: Object graph serialization, row format, and language-specific optimizations for different use cases
- **🧬 Advanced Features**: Shared references, circular references, polymorphism, and schema evolution
- **🔒 Production-Ready**: Java 8-24 support, GraalVM native image, and comprehensive security controls

### 🚀 High-Performance Serialization

Apache Fory™ achieves exceptional performance through multiple optimization techniques:

- **JIT Compilation**: Runtime code generation eliminates virtual method calls and inlines hot paths
- **Zero-Copy**: Direct memory access without intermediate buffer copies; row format supports random access and partial serialization
- **Variable-Length Encoding**: Intelligent compression of integers and strings
- **SIMD Acceleration**: Java Vector API support for array operations (Java 16+)
- **Meta Sharing**: Class metadata packing reduces redundant type information

### 🌍 Cross-Language Serialization

The **[xlang serialization format](docs/specification/xlang_serialization_spec.md)** enables seamless data exchange between different programming languages:

- **Automatic Object Serialization**: No IDL or schema compilation needed
- **Type Mapping**: Intelligent conversion between language-specific types ([see type mapping](docs/specification/xlang_type_mapping.md))
- **Reference Preservation**: Shared and circular references work across languages
- **Polymorphism Support**: Serialize and deserialize objects with their actual runtime types
- **Schema Evolution**: Optional forward/backward compatibility for schema changes

### 📊 Row Format

The **[row format](docs/specification/row_format_spec.md)** provides a cache-friendly binary random access format optimized for analytics:

- **Zero-Copy Random Access**: Read individual fields without deserializing entire objects
- **Partial Serialization**: Skip unnecessary fields during serialization
- **Apache Arrow Integration**: Automatic conversion to columnar format for analytics
- **Language Support**: Available in Java, Python, and C++

### 🔒 Security Features

- **Class Registration**: Whitelist-based deserialization control (enabled by default)
- **Depth Limiting**: Protection against recursive object graph attacks
- **Configurable Policies**: Custom class checkers and deserialization policies
- **Safe Mode**: Strict type checking prevents arbitrary code execution

## Protocols

Apache Fory™ implements multiple binary protocols optimized for different scenarios:

| Protocol                                                                  | Use Case                       | Key Features                                           |
| ------------------------------------------------------------------------- | ------------------------------ | ------------------------------------------------------ |
| **[Xlang Serialization](docs/specification/xlang_serialization_spec.md)** | Cross-language object exchange | Automatic serialization, references, polymorphism      |
| **[Java Serialization](docs/specification/java_serialization_spec.md)**   | High-performance Java-only     | Drop-in JDK serialization replacement, 100x faster     |
| **[Row Format](docs/specification/row_format_spec.md)**                   | Analytics and data processing  | Zero-copy random access, Arrow compatibility           |
| **Python Native**                                                         | Python-specific serialization  | Pickle/cloudpickle replacement with better performance |

All protocols share the same optimized codebase, allowing improvements in one protocol to benefit others.

## Benchmarks

> **Note**: Different serialization frameworks excel in different scenarios. Benchmark results are for reference only.
> For your specific use case, conduct benchmarks with appropriate configurations and workloads.

Apache Fory™ demonstrates exceptional performance across various scenarios. Dynamic serialization frameworks typically support polymorphism and references but incur higher overhead compared to static frameworks—unless they employ JIT techniques like Fory.

**Important**: Due to Fory's runtime code generation, ensure adequate **warm-up** before collecting benchmark data for accurate statistics.

### Java Serialization Performance

The following benchmarks compare Fory against popular Java serialization frameworks. Charts labeled **"compatible"** show schema evolution mode with forward/backward compatibility enabled, while others show schema consistent mode where class schemas must match.

**Test Classes**:

- `Struct`: Class with [100 primitive fields](docs/benchmarks#Struct)
- `MediaContent`: Class from [jvm-serializers](https://github.com/eishay/jvm-serializers/blob/master/tpc/src/data/media/MediaContent.java)
- `Sample`: Class from [Kryo benchmark](https://github.com/EsotericSoftware/kryo/blob/master/benchmarks/src/main/java/com/esotericsoftware/kryo/benchmarks/data/Sample.java)

**Serialization Throughput**:

<p align="center">
<img width="24%" alt="Struct Serialization Compatible" src="docs/benchmarks/compatible/bench_serialize_compatible_STRUCT_to_directBuffer_tps.png">
<img width="24%" alt="MediaContent Serialization Compatible" src="docs/benchmarks/compatible/bench_serialize_compatible_MEDIA_CONTENT_to_array_tps.png">
<img width="24%" alt="MediaContent Serialization" src="docs/benchmarks/serialization/bench_serialize_MEDIA_CONTENT_to_array_tps.png">
<img width="24%" alt="Sample Serialization" src="docs/benchmarks/serialization/bench_serialize_SAMPLE_to_array_tps.png">
</p>

**Deserialization Throughput**:

<p align="center">
<img width="24%" alt="Struct Deserialization Compatible" src="docs/benchmarks/compatible/bench_deserialize_compatible_STRUCT_from_directBuffer_tps.png">
<img width="24%" alt="MediaContent Deserialization Compatible" src="docs/benchmarks/compatible/bench_deserialize_compatible_MEDIA_CONTENT_from_array_tps.png">
<img width="24%" alt="MediaContent Deserialization" src="docs/benchmarks/deserialization/bench_deserialize_MEDIA_CONTENT_from_array_tps.png">
<img width="24%" alt="Sample Deserialization" src="docs/benchmarks/deserialization/bench_deserialize_SAMPLE_from_array_tps.png">
</p>

For additional benchmarks covering type forward/backward compatibility, off-heap support, and zero-copy serialization, see [Java Benchmarks](docs/benchmarks).

### Rust Serialization Performance

Fory Rust demonstrates competitive performance compared to other Rust serialization frameworks.

<p align="center">
<img src="docs/benchmarks/rust/ecommerce_data.png" width="70%">
</p>

<p align="center">
<img src="docs/benchmarks/rust/system_data.png" width="70%">
</p>

For more detailed benchmarks and methodology, see [Rust Benchmarks](rust/benches).

## Installation

**Java**:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-core</artifactId>
  <version>0.13.0</version>
</dependency>
<!-- Optional row format support -->
<!--
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-format</artifactId>
  <version>0.13.0</version>
</dependency>
-->
<!-- SIMD acceleration for array compression (Java 16+) -->
<!--
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-simd</artifactId>
  <version>0.13.0</version>
</dependency>
-->
```

Snapshots are available from `https://repository.apache.org/snapshots/` (version `0.14.0-SNAPSHOT`).

**Scala**:

```sbt
// Scala 2.13
libraryDependencies += "org.apache.fory" % "fory-scala_2.13" % "0.13.0"

// Scala 3
libraryDependencies += "org.apache.fory" % "fory-scala_3" % "0.13.0"
```

**Kotlin**:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-kotlin</artifactId>
  <version>0.13.0</version>
</dependency>
```

**Python**:

```bash
pip install pyfory

# With row format support
pip install pyfory[format]
```

**Rust**:

```toml
[dependencies]
fory = "0.13"
```

**Golang**:

```bash
go get github.com/apache/fory/go/fory
```

## Quick Start

This section provides quick examples for getting started with Apache Fory™. For comprehensive guides, see the [Documentation](#documentation).

### Native Serialization

#### Java Serialization

When you don't need cross-language support, use Java mode for optimal performance.

```java
import org.apache.fory.*;
import org.apache.fory.config.*;

public class Example {
  public static void main(String[] args) {
    // Create Fory instance - should be reused across serializations
    BaseFory fory = Fory.builder()
      .withLanguage(Language.JAVA)
      .requireClassRegistration(true)
      // replace `build` with `buildThreadSafeFory` for Thread-Safe Usage
      .build();
    // Register your classes (required when class registration is enabled)
    fory.register(SomeClass.class);
    // Serialize
    SomeClass object = new SomeClass();
    byte[] bytes = fory.serialize(object);
    // Deserialize
    SomeClass result = (SomeClass) fory.deserialize(bytes);
  }
}
```

For detailed Java usage including compatibility modes, compression, and advanced features, see [Java Serialization Guide](docs/guide/java_serialization_guide.md) and [java/README.md](java/README.md).

#### Python Serialization

#### Scala Serialization

#### Kotlin Serialization

### Cross-Language Serialization

For data exchange between different programming languages, use xlang mode. The following examples demonstrate serializing an object with circular references across Java, Python, and Go.

**Java**

```java
import org.apache.fory.*;
import org.apache.fory.config.*;
import java.util.*;

public class ReferenceExample {
  public static class SomeClass {
    SomeClass f1;
    Map<String, String> f2;
    Map<String, String> f3;
  }

  public static Object createObject() {
    SomeClass obj = new SomeClass();
    obj.f1 = obj;  // Circular reference
    obj.f2 = new HashMap<String, String>() {{
      put("k1", "v1");
      put("k2", "v2");
    }};
    obj.f3 = obj.f2;  // Shared reference
    return obj;
  }

  public static void main(String[] args) {
    Fory fory = Fory.builder()
      .withLanguage(Language.XLANG)
      .withRefTracking(true)
      .build();

    // Register with cross-language type name
    fory.register(SomeClass.class, "example.SomeClass");

    byte[] bytes = fory.serialize(createObject());
    // bytes can be deserialized by Python, Go, or other languages
    System.out.println(fory.deserialize(bytes));
  }
}
```

**Python**

```python
from typing import Dict
import pyfory

class SomeClass:
    f1: "SomeClass"
    f2: Dict[str, str]
    f3: Dict[str, str]

fory = pyfory.Fory(ref_tracking=True)
fory.register_type(SomeClass, typename="example.SomeClass")

obj = SomeClass()
obj.f2 = {"k1": "v1", "k2": "v2"}
obj.f1, obj.f3 = obj, obj.f2  # Circular and shared references

data = fory.serialize(obj)
# data can be deserialized by Java, Go, or other languages
print(fory.deserialize(data))
```

For more cross-language examples and type mapping details, see [Cross-Language Serialization Guide](docs/guide/xlang_serialization_guide.md).

### Row Format Encoding

Row format provides zero-copy random access to serialized data, making it ideal for analytics workloads and data processing pipelines.

#### Java

```java
import org.apache.fory.format.*;
import java.util.*;
import java.util.stream.*;

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
Foo foo = new Foo();
foo.f1 = 10;
foo.f2 = IntStream.range(0, 1000000).boxed().collect(Collectors.toList());
foo.f3 = IntStream.range(0, 1000000).boxed().collect(Collectors.toMap(i -> "k"+i, i -> i));

List<Bar> bars = new ArrayList<>(1000000);
for (int i = 0; i < 1000000; i++) {
  Bar bar = new Bar();
  bar.f1 = "s" + i;
  bar.f2 = LongStream.range(0, 10).boxed().collect(Collectors.toList());
  bars.add(bar);
}
foo.f4 = bars;

// Serialize to row format (can be zero-copy read by Python)
BinaryRow binaryRow = encoder.toRow(foo);

// Deserialize entire object
Foo newFoo = encoder.fromRow(binaryRow);

// Zero-copy access to nested fields without full deserialization
BinaryArray binaryArray2 = binaryRow.getArray(1);  // Access f2 field
BinaryArray binaryArray4 = binaryRow.getArray(3);  // Access f4 field
BinaryRow barStruct = binaryArray4.getStruct(10);   // Access 11th Bar element
long value = barStruct.getArray(1).getInt64(5);     // Access nested value

// Partial deserialization
RowEncoder<Bar> barEncoder = Encoders.bean(Bar.class);
Bar newBar = barEncoder.fromRow(barStruct);
Bar newBar2 = barEncoder.fromRow(binaryArray4.getStruct(20));
```

#### Python

```python
from dataclasses import dataclass
from typing import List, Dict
import pyarrow as pa
import pyfory

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

encoder = pyfory.encoder(Foo)
foo = Foo(
    f1=10,
    f2=list(range(1000_000)),
    f3={f"k{i}": i for i in range(1000_000)},
    f4=[Bar(f1=f"s{i}", f2=list(range(10))) for i in range(1000_000)]
)

# Serialize to row format
binary: bytes = encoder.to_row(foo).to_bytes()

# Zero-copy random access without full deserialization
foo_row = pyfory.RowData(encoder.schema, binary)
print(foo_row.f2[100000])           # Access element directly
print(foo_row.f4[100000].f1)        # Access nested field
print(foo_row.f4[200000].f2[5])     # Access deeply nested field
```

For more details on row format including Apache Arrow integration, see [Row Format Guide](docs/guide/row_format_guide.md).

## Documentation

### User Guides

- **[Java Serialization Guide](docs/guide/java_serialization_guide.md)**: Comprehensive guide for Java-specific serialization
- **[Cross-Language Serialization Guide](docs/guide/xlang_serialization_guide.md)**: Multi-language object exchange
- **[Row Format Guide](docs/guide/row_format_guide.md)**: Zero-copy random access format
- **[Python Guide](docs/guide/python_guide.md)**: Python-specific features and usage
- **[Rust Guide](docs/guide/rust_guide.md)**: Rust implementation and patterns
- **[Scala Guide](docs/guide/scala_guide.md)**: Scala integration and best practices
- **[GraalVM Guide](docs/guide/graalvm_guide.md)**: Native image support and AOT compilation
- **[Development Guide](docs/guide/DEVELOPMENT.md)**: Building and contributing to Fory

### Protocol Specifications

- **[Xlang Serialization Specification](docs/specification/xlang_serialization_spec.md)**: Cross-language binary protocol
- **[Java Serialization Specification](docs/specification/java_serialization_spec.md)**: Java-optimized protocol
- **[Row Format Specification](docs/specification/row_format_spec.md)**: Row-based binary format
- **[Type Mapping](docs/specification/xlang_type_mapping.md)**: Cross-language type conversion rules

## Compatibility

### Schema Compatibility

Apache Fory™ supports class schema forward/backward compatibility across **Java, Python, Rust, and Golang**, enabling seamless schema evolution in production systems without requiring coordinated upgrades across all services. Fory provides two schema compatibility modes:

1. **Schema Consistent Mode (Default)**: Assumes identical class schemas between serialization and deserialization peers. This mode offers minimal serialization overhead, smallest data size, and fastest performance: ideal for stable schemas or controlled environments.

2. **Compatible Mode**: Supports independent schema evolution with forward and backward compatibility. This mode enables field addition/deletion, limited type evolution, and graceful handling of schema mismatches. Enable using `withCompatibleMode(CompatibleMode.COMPATIBLE)` in Java, `compatible=True` in Python, `compatible_mode(true)` in Rust, or `NewFory(true)` in Go.

### Binary Compatibility

**Current Status**: Binary compatibility is **not guaranteed** between Fory major releases as the protocol continues to evolve. However, compatibility **is guaranteed** between minor versions (e.g., 0.13.x).

**Recommendations**:

- Version your serialized data by Fory major version
- Plan migration strategies when upgrading major versions
- See [upgrade guide](docs/guide/java_serialization_guide.md#upgrade-fory) for details

**Future**: Binary compatibility will be guaranteed starting from Fory 1.0 release.

## Security

### Overview

Serialization security varies by mode:

- **Static Serialization** (Row Format): Relatively secure with predefined schemas
- **Dynamic Serialization** (Java/Python native): More flexible but requires careful security configuration

### Security Risks

Dynamic serialization can deserialize arbitrary types, which introduces risks:

- Malicious code execution during deserialization
- Constructor/method invocation (`__init__`, `equals`, `hashCode`)
- Object graph manipulation attacks

### Security Controls

#### Class Registration (Recommended)

Fory enables class registration **by default** for dynamic protocols, allowing only trusted registered types:

```java
Fory fory = Fory.builder()
  .withLanguage(Language.JAVA)
  .requireClassRegistration(true)  // Default: true
  .build();

// Only registered classes can be deserialized
fory.register(TrustedClass.class);
```

**⚠️ Warning**: Only disable class registration in trusted environments:

```java
// Only for controlled environments!
Fory fory = Fory.builder()
  .requireClassRegistration(false)
  .build();

// Configure custom class checker
fory.getClassResolver().setClassChecker((classResolver, className) -> {
  // Implement your security policy
  return className.startsWith("com.mycompany.trusted.");
});
```

#### Python Security

```python
import pyfory

# Strict mode (recommended)
fory = pyfory.Fory(xlang=False, ref=True, strict=True)
fory.register(TrustedClass)

# Relaxed mode - only for trusted environments
fory = pyfory.Fory(xlang=False, strict=False, policy=my_custom_policy)
```

### Best Practices

1. **Enable Class Registration**: Always use class registration in production
2. **Validate Input**: Sanitize serialized data from untrusted sources
3. **Limit Depth**: Configure maximum deserialization depth to prevent DoS attacks
4. **Custom Policies**: Implement custom `ClassChecker` or `DeserializationPolicy` for fine-grained control
5. **Security Audits**: Monitor class registration warnings (enabled by default)

### Reporting Vulnerabilities

To report security vulnerabilities in Apache Fory™, please follow the [ASF vulnerability reporting process](https://apache.org/security/#reporting-a-vulnerability).

## Community and Support

### Getting Help

- **Slack**: Join our [Slack workspace](https://join.slack.com/t/fory-project/shared_invite/zt-36g0qouzm-kcQSvV_dtfbtBKHRwT5gsw) for community discussions
- **Twitter/X**: Follow [@ApacheFory](https://x.com/ApacheFory) for updates and announcements
- **GitHub Issues**: Report bugs and request features at [apache/fory](https://github.com/apache/fory/issues)
- **Mailing Lists**: Subscribe to Apache Fory mailing lists for development discussions

### Contributing

We welcome contributions! Please read our [Contributing Guide](CONTRIBUTING.md) to get started.

**Ways to Contribute**:

- 🐛 Report bugs and issues
- 💡 Propose new features
- 📝 Improve documentation
- 🔧 Submit pull requests
- 🧪 Add test cases
- 📊 Share benchmarks

See [Development Guide](docs/guide/DEVELOPMENT.md) for build instructions and development workflow.

## License

Apache Fory™ is licensed under the [Apache License 2.0](LICENSE).

## Acknowledgments

Apache Fory™ is an [Apache Software Foundation](https://apache.org/) project, developed and maintained by contributors worldwide. We thank all contributors for their valuable contributions to the project.
