---
title: Troubleshooting
sidebar_position: 17
id: troubleshooting
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

This page covers common issues and their solutions.

## Class Inconsistency and Class Version Check

If you create Fory without enabling compatible mode and you get a strange serialization error, it may be caused by class inconsistency between the serialization peer and deserialization peer.

In such cases, you can invoke `ForyBuilder#withClassVersionCheck` to create Fory to validate it. If deserialization throws `org.apache.fory.exception.ClassNotCompatibleException`, it shows classes are inconsistent, and you should create Fory with `ForyBuilder#withCompatible(true)`.

```java
// Enable class version check to diagnose issues
Fory fory = Fory.builder()
  .withClassVersionCheck(true)
  .build();

// If ClassNotCompatibleException is thrown, add compatible mode to the
// same builder configuration on every peer.
ForyBuilder builder = Fory.builder()
  .withCompatible(true);
```

**Note**: compatible mode has more performance and space cost. For xlang mode it is the default and recommended setting. Use schema-consistent mode only if your classes are always consistent between serialization and deserialization, or if all services deploy schema changes at the same time.

## Using Wrong API for Deserialization

Use `serialize` with one of the `deserialize` overloads:

| Serialization API | Deserialization API |
| ----------------- | ------------------- |
| `Fory#serialize`  | `Fory#deserialize`  |

**Wrong usage example:**

```java
// Wrong: deserialize with an incompatible target class
byte[] bytes = fory.serialize(struct1);
Struct2 result = fory.deserialize(bytes, Struct2.class);  // May throw ClassCastException
```

**Correct usage:**

```java
byte[] bytes = fory.serialize(object);
Object result = fory.deserialize(bytes);

byte[] typedBytes = fory.serialize(object);
MyClass typedResult = fory.deserialize(typedBytes, MyClass.class);
```

## Deserialize POJO into Another Type

If you want to serialize one POJO and deserialize it into a different POJO type, enable compatible mode:

```java
public class DeserializeIntoType {
  static class Struct1 {
    int f1;
    String f2;

    public Struct1(int f1, String f2) {
      this.f1 = f1;
      this.f2 = f2;
    }
  }

  static class Struct2 {
    int f1;
    String f2;
    double f3;
  }

  static ThreadSafeFory fory = Fory.builder()
    .withCompatible(true).buildThreadSafeFory();

  public static void main(String[] args) {
    Struct1 struct1 = new Struct1(10, "abc");
    byte[] data = fory.serialize(struct1);
    Struct2 struct2 = fory.deserialize(data, Struct2.class);
  }
}
```

## Common Error Messages

### "Class not registered"

**Cause**: Class registration is required but the class wasn't registered.

**Solution**: Register the class before serialization:

```java
fory.register(MyClass.class);
// or with explicit ID
fory.register(MyClass.class, 100);
```

### "ClassNotCompatibleException"

**Cause**: Class schema differs between serialization and deserialization.

**Solution**: Use compatible mode:

```java
Fory fory = Fory.builder()
  .withCompatible(true)
  .build();
```

### "Max depth exceeded"

**Cause**: Object graph is too deep, possibly indicating a circular reference attack.

**Solution**: Increase max depth if legitimate, or check for malicious data:

```java
Fory fory = Fory.builder()
  .withMaxDepth(100)  // Increase from default 50
  .build();
```

### "Serializer not found"

**Cause**: No serializer registered for the type.

**Solution**: Register a custom serializer:

```java
fory.registerSerializer(MyClass.class, new MyClassSerializer(fory.getTypeResolver()));
```

### JDK25+ zero-Unsafe mode and module opens

When running on JDK25+ with Unsafe memory access denied, or on a later JDK where denied Unsafe
memory access becomes the default, start the JVM with:

```bash
--sun-misc-unsafe-memory-access=deny
```

If Fory needs private fields in your named module, open the target package to both Java modules.
When any Fory artifact is on the classpath instead of the module path, also include `ALL-UNNAMED`:

```bash
--add-opens=<user.module>/<user.package>=ALL-UNNAMED,org.apache.fory.core,org.apache.fory.format
```

Some optimized serializers also need JDK-private packages. For each package in the table, open the
owning JDK module/package to `org.apache.fory.core` and `org.apache.fory.format`; include
`ALL-UNNAMED` too when any Fory artifact is on the classpath. Add only the opens needed by the paths
used in your process:

| Path                                                                             | Required opens                                                            |
| -------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| String fast paths and throwable fields                                           | `java.base/java.lang`                                                     |
| Serialized lambdas                                                               | `java.base/java.lang.invoke`                                              |
| Reflection-based object construction                                             | `java.base/java.lang.reflect`, `java.base/jdk.internal.reflect`           |
| Collection wrappers, sublists, `EnumMap`, and `StringTokenizer`                  | `java.base/java.util`                                                     |
| Blocking queue capacity serializers                                              | `java.base/java.util.concurrent`, `java.base/java.util.concurrent.atomic` |
| `ByteArrayInputStream`, `ByteArrayOutputStream`, and Java object-stream metadata | `java.base/java.io`                                                       |
| URL and networking serializers                                                   | `java.base/java.net`                                                      |
| Proxy serializers                                                                | `java.base/java.lang.reflect`                                             |
| Big number internals                                                             | `java.base/java.math`                                                     |

Normal classes with final instance fields require final-field mutation to be enabled for the module
that contains Fory's mutating code when Unsafe allocation is denied. Use the Fory module name on the
module path:

```bash
--enable-final-field-mutation=org.apache.fory.core
```

Use `ALL-UNNAMED` when running Fory on the classpath:

```bash
--enable-final-field-mutation=ALL-UNNAMED
```

Fory restores those final fields through method-handle based access. Non-final fields can still be
restored through generated direct field assignment where available.

The vectorized Arrow APIs in `fory-format` depend on Apache Arrow's memory layer. With the current
Arrow dependency, those APIs are unavailable when `--sun-misc-unsafe-memory-access=deny` is set
because Arrow initializes its own `sun.misc.Unsafe` memory access internally.

## Performance Issues

### Slow Initial Serialization

**Cause**: JIT compilation happening on first serialization.

**Solution**: Enable async compilation:

```java
Fory fory = Fory.builder()
  .withAsyncCompilation(true)
  .build();
```

### High Memory Usage

**Cause**: Large object graphs or reference tracking overhead.

**Solutions**:

- Disable reference tracking if not needed: `.withRefTracking(false)`
- Use custom memory allocator for pooling
- Consider row format for large datasets

### Large Serialized Size

**Cause**: Metadata overhead or uncompressed data.

**Solutions**:

- Enable compression: `.withIntCompressed(true)`, `.withLongCompressed(true)`
- Use compatible mode only when needed
- Register classes to avoid class name serialization

## Debugging Tips

1. **Enable class version check** to diagnose schema issues
2. **Check API pairing** - ensure serialize/deserialize APIs match
3. **Verify registration order** - must be consistent across peers
4. **Enable logging** to see internal operations:

```bash
FORY_LOG_LEVEL=INFO mvn test -Dtest=org.apache.fory.TestClass#testMethod
```

## Related Topics

- [Configuration](configuration.md) - All ForyBuilder options
- [Schema Evolution](schema-evolution.md) - Compatible mode details
- [Type Registration](type-registration.md) - Registration best practices
- [Native Serialization](native-serialization.md) - Java-only serialization features
