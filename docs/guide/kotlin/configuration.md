---
title: Configuration
sidebar_position: 1
id: configuration
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

This page covers Kotlin-specific runtime configuration and Fory instance creation.

## Xlang Setup

Fory Kotlin follows the Java builder default: xlang mode with compatible schema
evolution. Use this path for cross-language Kotlin payloads, schema IDL
generated Kotlin models, and KSP-generated xlang serializers.

```kotlin
import org.apache.fory.kotlin.ForyKotlin

val fory = ForyKotlin.builder()
    .withXlang(true)
    .requireClassRegistration(true)
    .build()
```

## Native Mode Setup

For same-language Kotlin/JVM payloads that need native JVM object behavior, use
native mode explicitly:

```kotlin
import org.apache.fory.kotlin.ForyKotlin

val fory = ForyKotlin.builder().withXlang(false)
    .requireClassRegistration(true)
    .build()
```

## Thread Safety

Fory instance creation is not cheap. Instances should be shared between multiple serializations.

### Single-Thread Usage

```kotlin
import org.apache.fory.Fory
import org.apache.fory.kotlin.ForyKotlin

object ForyHolder {
    val fory: Fory = ForyKotlin.builder()
        .withXlang(true)
        .requireClassRegistration(true)
        .build()
}
```

### Multi-Thread Usage

For multi-threaded applications, use `ThreadSafeFory`:

```kotlin
import org.apache.fory.ThreadSafeFory
import org.apache.fory.kotlin.ForyKotlin

object ForyHolder {
    val fory: ThreadSafeFory = ForyKotlin.builder()
        .withXlang(true)
        .requireClassRegistration(true)
        .buildThreadSafeFory()
}
```

### Using Builder Methods

```kotlin
// Thread-safe Fory
val fory: ThreadSafeFory = ForyKotlin.builder()
    .withXlang(true)
    .requireClassRegistration(true)
    .buildThreadSafeFory()
```

## Configuration

All configuration options from Fory Java are available. See [Java Configuration](../java/configuration.md) for the complete list.

## JDK25+ Zero-Unsafe Mode

On JDK25+ with Unsafe memory access denied, Kotlin classes with final constructor properties need
an explicit constructor mapping when Fory must call the primary constructor. Annotate the
constructor with `@ForyConstructor`, register the constructor with `registerConstructor(...)`, use a
generated serializer that carries explicit constructor metadata, or provide a custom serializer.
Register constructors during setup before requesting serializers or starting serialization,
deserialization, or copy operations.

```kotlin
import org.apache.fory.annotation.ForyConstructor

class User @ForyConstructor("name", "age") constructor(
    val name: String,
    val age: Int,
)
```

KSP-generated `@ForyStruct` serializers that call a primary constructor require the same explicit
`@ForyConstructor` mapping. Mutable no-argument `@ForyStruct` classes can instead expose serialized
`var` properties with `@ForyField`.

The JVM also needs the module opens and final-field mutation option listed in
[Java Troubleshooting](../java/troubleshooting.md#jdk25-zero-unsafe-mode-and-module-opens).

Common options for Kotlin native-mode payloads:

```kotlin
import org.apache.fory.kotlin.ForyKotlin

val fory = ForyKotlin.builder().withXlang(false)
    // Enable reference tracking for circular references
    .withRefTracking(true)
    // Enable schema evolution support for native-mode payloads
    .withCompatible(true)
    // Enable async compilation for better startup performance
    .withAsyncCompilation(true)
    // Compression options
    .withIntCompressed(true)
    .withLongCompressed(true)
    .build()
```

## Security

Kotlin uses the Java runtime configuration surface. Keep class registration enabled for production
and any untrusted payload source:

```kotlin
val fory = ForyKotlin.builder()
    .requireClassRegistration(true)
    .withMaxDepth(50)
    .build()
```

Security-related configuration:

- Keep `requireClassRegistration(true)` and register application classes or generated modules.
- Use `withMaxDepth(...)` to reject unexpectedly deep object graphs.
- Follow [Java Configuration](../java/configuration.md#security) for allow-listing and unknown-class
  controls.
