---
title: Static Generated Serializers
sidebar_position: 4
id: static_generated_serializers
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

`fory-kotlin-ksp` generates Kotlin source serializers for Fory xlang/schema
mode. The generated serializers use the existing Java runtime
`WriteContext`, `ReadContext`, and `MemoryBuffer`; they do not define a
Kotlin-only protocol or buffer abstraction.

## Scope

KSP generation is only for xlang/schema mode. It does not generate Fory Java
native object serializers and does not preserve concrete JVM runtime identity
for object graphs. Collection declarations are schema carriers: `List<T>` is
encoded as `list<T>`, `Map<K, V>` as `map<K, V>`, and deserialization only
guarantees a value assignable to the declared Kotlin field type.

Mutable collection interface fields are supported by reconstructing a mutable
implementation assignable to the declared type. Sorted collection declarations
without an explicit comparator, such as `TreeSet` and `ConcurrentSkipListSet`,
are accepted only for non-null scalar or string elements. Concurrent map
declarations are accepted only with non-null values because the JVM concurrent
map implementations reject null entries.

Kotlin/JVM and Android are supported. Kotlin/Native and Kotlin/JS are not
supported by this module.

Phase 1 KSP generation emits serializers for public concrete, non-generic
`@ForyStruct` classes with primary-constructor fields and a public or internal
primary constructor. Kotlin `private` and `internal` struct classes are rejected
because generated serializers and SPI providers are public runtime entry
points. Kotlin `object` declarations, annotated interfaces, abstract classes,
sealed declarations, and generic struct targets are rejected during KSP
processing because they are not single constructor-field serializer targets for
the phase 1 generator. Dense primitive and unsigned array types, such as
`IntArray` and `UIntArray`, are supported as top-level fields. Nested dense
arrays, such as `List<UIntArray>` or `Map<String, IntArray>`, are rejected in
phase 1. Fory Java `@ArrayType` is also supported on top-level `List<T>` fields
when `T` is a non-null bool or numeric dense-array element domain. The generated
serializer writes the field as dense `array<T>` schema and converts decoded JVM
list elements back to the declared Kotlin carrier, such as `Int`, `UInt`, or
`Double`.

## Annotations

Reuse Java annotations for Fory concepts:

```kotlin
import org.apache.fory.annotation.ForyField
import org.apache.fory.annotation.ForyStruct
import org.apache.fory.kotlin.Fixed
import org.apache.fory.kotlin.VarInt

@ForyStruct
data class User(
  @ForyField(id = 1)
  val id: @Fixed UInt,

  @ForyField(id = 2)
  val score: @VarInt Long,
)
```

Use `@ForyField(id = 1)`. `@field:ForyField(id = 1)` is also accepted for
field-backed properties. `@get:ForyField` and `@set:ForyField` are rejected
because accessors are not schema fields.

Kotlin nullability is expressed with `?`, including nested positions such as
`List<String?>?`. Do not use Fory `@Nullable` in Kotlin source.

`@ForyField(ref = true)` is rejected by the KSP xlang processor. Phase 1
generated serializers construct Kotlin values through primary constructors and
therefore cannot publish partially constructed objects for cyclic
back-references. Use non-cyclic xlang schemas for Kotlin KSP serializers.

Kotlin default constructor arguments are supported for compatible reads by
generating constructor calls that omit missing defaulted arguments. Because
Kotlin source generation must emit these calls statically, the first KSP
implementation supports at most 12 defaulted constructor fields per struct.

## Encoding

Kotlin type-use encoding annotations map to Fory xlang integer encodings:

| Annotation | Valid Kotlin types             |
| ---------- | ------------------------------ |
| `@Fixed`   | `Int`, `Long`, `UInt`, `ULong` |
| `@VarInt`  | `Int`, `Long`, `UInt`, `ULong` |
| `@Tagged`  | `Long`, `ULong`                |

Without an annotation, xlang `Int`, `Long`, `UInt`, and `ULong` use varint
encoding. This is required by xlang mode and is not controlled by Java native
mode numeric compression options.

## Registration

Users register Kotlin struct classes with the normal Fory Java registration
APIs. Do not reference generated serializer class names.

```kotlin
val fory = Fory.builder()
  .withXlang(true)
  .requireClassRegistration(true)
  .build()

KotlinSerializers.registerSerializers(fory)
fory.register(User::class.java, "example", "User")
```

The KSP processor emits a service provider that maps target classes to their
generated serializers. On Android and for Kotlin classes, the Java runtime
requires this provider mapping for xlang structs. Missing KSP/SPI metadata is a
configuration error, not a reflective fallback path. This avoids generated-name
`Class.forName` lookups and works as an R8-compatible discovery path when the
generated service resource and serializer classes are kept by the application
build.

Android builds that run R8 must preserve the generated SPI resource and the
generated provider/serializer classes. Application shrinker rules should keep:

```text
-keep class * implements org.apache.fory.resolver.StaticGeneratedSerializerProvider { *; }
-keep class * implements org.apache.fory.resolver.StaticGeneratedSerializerProvider$KotlinSymbolProcessor { *; }
-keep class * implements org.apache.fory.resolver.StaticGeneratedSerializerProvider$JavaAnnotationProcessor { *; }
-keep class **.*__ForySerializer__ { *; }
-keep class org.apache.fory.resolver.StaticGeneratedSerializerProvider { *; }
-keep class org.apache.fory.resolver.StaticGeneratedSerializerProvider$* { *; }
```

The Kotlin KSP generated service resource
`META-INF/services/org.apache.fory.resolver.StaticGeneratedSerializerProvider$KotlinSymbolProcessor`
must also be packaged. Java annotation-processor generated serializers use the
separate
`META-INF/services/org.apache.fory.resolver.StaticGeneratedSerializerProvider$JavaAnnotationProcessor`
resource so mixed Java/Kotlin artifacts do not overwrite one another's provider
lists. If a build plugin strips service resources, configure the application
packaging step to retain these files.

The KSP processor emits one aggregate provider for the compilation module in
`org.apache.fory.kotlin.generated`. The provider class name includes a stable
hash of the generated target mappings, so multiple Kotlin artifacts can publish
providers on the same classpath without binary-name collisions. That provider
contains mappings for all annotated Kotlin structs in the module, across source
packages.

Applications must package the generated service resource. There is no manual
provider registration API; users still register only target classes and their
IDs or xlang names through the normal Fory Java registration APIs.
