# Apache Fory JSON Kotlin

`fory-json-kotlin` adds Kotlin/JVM model support to Apache Fory JSON. It preserves Kotlin
nullability, constructor defaults, generic arguments, unsigned types, value classes, and other
supported Kotlin semantic types while using the standard Fory JSON APIs.

## Installation

Add the Kotlin JSON runtime:

```kotlin
plugins {
  kotlin("jvm") version "2.3.20"
}

dependencies {
  implementation("org.apache.fory:fory-json-kotlin:1.7.0-SNAPSHOT")
}
```

The runtime reads Kotlin/JVM metadata directly and does not require `kotlin-reflect` or KSP.

For an Android build that enables R8 or ProGuard, also apply KSP 2.3.8 and use the matching
processor version:

```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.3.8"
}

dependencies {
  ksp("org.apache.fory:fory-json-kotlin-ksp:1.7.0-SNAPSHOT")
}
```

Annotate application models with `@JsonType`, or define source-owned `@JsonMixin` declarations, so
KSP can package their exact retention rules. KSP owns a Mixin request when either its source or its
exact target is Kotlin. The processor does not replace runtime metadata mapping.

## Usage

Create a `ForyJson` instance with the Kotlin module installed and retain complete type tokens for
declared Kotlin roots:

```kotlin
import org.apache.fory.json.kotlin.ForyJsonKotlin
import org.apache.fory.json.kotlin.jsonTypeRef

data class Account(
  val id: ULong,
  val name: String,
  val nickname: String? = null,
)

val json = ForyJsonKotlin.builder().build()
val accountType = jsonTypeRef<Account>()

val text = json.toJson(Account(7u, "Alice"), accountType)
val account = json.fromJson(text, accountType)
```

Construct each `jsonTypeRef<T>()` once and reuse it. It preserves distinctions that a Java `Class`
cannot express, including occurrence nullability, unsigned semantics, value-class identity, and
nested generic arguments such as `List<Account?>`.

`ForyJsonKotlin.builder()` is equivalent to installing the module explicitly:

```kotlin
import org.apache.fory.json.ForyJson
import org.apache.fory.json.kotlin.ForyJsonKotlin

val json = ForyJson.builder().withModule(ForyJsonKotlin).build()
```

Compiler defaults apply only when a JSON member is absent. Explicit JSON `null` remains distinct
and is accepted only for a nullable declaration. Raw generic types, star projections, and
contravariant projections are not complete schemas and are rejected.

## Platforms

The module supports Kotlin/JVM on HotSpot, GraalVM Native Image, and Android API 26 or later.
Standard JVM and Android builds read the model's Kotlin metadata. Android builds that enable R8 or
ProGuard must package the exact KSP-generated retention rules instead of broad keep rules. GraalVM
builds use the Fory JSON provider workflow and must not add reflection configuration for application
models.

Kotlin/Native, Kotlin/JS, and Kotlin/Wasm are not supported by this JVM module.

For constructor rules, annotations, supported types, collections, sealed hierarchies, security,
and complete platform setup, see the
[Fory JSON Kotlin guide](../../docs/json/kotlin.md).
