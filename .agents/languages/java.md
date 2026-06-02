# Java

Load this file when changing anything under `java/` or when Java drives a cross-language validation flow.

## Rules

- Run all Maven commands from within `java/`.
- Changes under `java/` must pass code style checks and tests.
- Fory Java requires JDK `17+`.
- Run Java `spotless` with JDK `21+`. If the current runtime is lower than 21, export `JAVA_HOME` to a JDK 21 installation before running `mvn spotless:check` or `mvn spotless:apply`.
- `fory-core` targets Java 8 bytecode and `fory-format` targets Java 11 bytecode. Do not use newer APIs in those modules.
- Do not use wildcard imports.
- Import config and annotation types instead of fully qualifying enum constants or annotation
  values; use qualified names only when a real name conflict requires it.
- If you run temporary tests with `java -cp`, run `mvn -T16 install -DskipTests` first so local Fory jars are current.
- `WriteContext`, `ReadContext`, and `CopyContext` must stay explicit. Do not reintroduce `ThreadLocal` or ambient runtime-context patterns.
- Generated serializers must not retain runtime context fields. `Fory` should stay a root-operation facade rather than accumulating serializer or convenience state.
- When the serializer class and constructor shape are known at the call site, prefer direct constructor lambdas or direct instantiation over reflective `Serializers.newSerializer(...)`.
- For GraalVM, use `fory codegen` to generate serializers when building native images. Do not add reflection configuration except for JDK `proxy`.
- In Java native mode (`xlang=false`), only `Types.BOOL` through `Types.STRING` share type IDs with xlang mode. Other native-mode type IDs differ.
- Choose one serializer ownership location per logical Java type family. Add native/xlang serializer variants only when the wire format or constructor contract truly differs.
- Do not add normal-JVM process-global caches keyed by user classes, generated classes, serializer classes, classloaders, or class-bound method handles. Prefer per-runtime state, immutable shared metadata, or build-time-only template data.
- Concrete serializers may opt into sharing only after auditing retained fields. Treat serializers retaining `TypeResolver`, `RefResolver`, mutable scratch buffers, runtime state, or classloader-sensitive state as non-shareable unless that state is externalized.
- Resolver and serializer hot paths should keep the fast-path/null-slow-path shape obvious. Hoist repeated buffer or cache-state access into locals for multi-step operations and keep rebuild/restoration logic cold.
- Hot-path feature gates that are runtime constants must be `static final` fields read directly in
  the branch. Do not hide them behind helper methods such as `jdkInternalFieldAccess()`, because
  that obscures branch folding and can leave avoidable call/inlining work in hot serializers.
- In Java codec hot paths, avoid `Preconditions.checkArgument` for attacker-controlled primitive
  validation. Use direct primitive branches and throw on the cold error path to preserve inlining and
  avoid varargs/helper overhead.
- Do not introduce codegen or generated-serializer changes that may cause behavior or performance
  regressions. When editing `java/fory-core/src/main/java/org/apache/fory/builder/**` or APIs used
  by generated serializers, do extra self-review: inspect the generated output impact, preserve
  unsafe/codegen optimizations unless intentionally changing them, and run validation appropriate to
  the regression risk.
- Android and JVM serializers must use a unified wire protocol: each side must be able to
  deserialize data written by the other side. If implementation paths diverge, the writer must emit
  enough metadata for either reader to identify and parse that path correctly; add both
  Android-read-JVM and JVM-read-Android regression coverage.
- In `MemoryBuffer`, Android branches are intentional method-boundary exits. Each
  `if (AndroidSupport.IS_ANDROID)` branch must contain exactly one `MemoryOps` call and no local
  Android heap logic. Keep heap index math, direct field updates, typed array loops, and
  reader/writer index changes in `MemoryOps`; do not add Android-named helpers, heap wrapper
  helpers, or Android-specific `MemoryBuffer` subclasses.
- In `MemoryBuffer` and `MemoryOps` hot paths, duplicate small straight-line copy/read/write logic
  when that keeps control flow direct. Do not add private helper indirection to hot paths just to
  reduce local code duplication; keep helpers for slow, cold, or error paths.
- In `MemoryBuffer` small-varint read/write hot paths, once Android has exited through the single
  `MemoryOps` call, keep JVM bulk loads/stores local with raw Unsafe operations instead of routing
  through branchful `_unsafeGet*` or `_unsafePut*` helpers. Add or preserve source comments that
  explain this inlining invariant when editing these methods.
- Unsafe object-offset arithmetic must stay `long` before calls such as `getLong(Object, long)` or
  `putLong(Object, long)`. An all-`int` expression can compile under JDK8 to a bytecode descriptor
  that fails with `NoSuchMethodError` on JDK9+.
- In primitive-array swap-endian readers, do not loop through `MemoryBuffer._unsafeGet*` helpers.
  Copy the payload through the typed payload API, then swap destination values locally so the path
  stays stream-safe and avoids Android-dispatch helper drift.
- Keep GraalVM feature code as a thin metadata/registration layer. Build time should publish metadata needed for runtime reconstruction, not retain concrete generated or user serializer instances in the image heap.
- If changes touch GraalVM bootstrap, serializer retention, native-image metadata, or `ObjectStreamSerializer` GraalVM behavior, verify the native-image build and run the produced binary; a plain Java compile is insufficient.
- Put latest-JDK or virtual-thread tests in the latest-JDK test modules with the matching compiler/profile floor, and centralize runtime-version probing in existing compatibility utilities.
- For JDK25+ zero-Unsafe work, preserve serializer-family selection by type and configuration. Do not switch a type from `ObjectStreamSerializer` or another Fory serializer family to `JavaSerializer`, a JDK stream fallback, or any broad `java.* Serializable` fallback by JDK version or no-arg-constructor shape.
- JDK25+ zero-Unsafe runtime support is a JPMS named-module design. Fory core must run with `--add-opens=java.base/java.lang.invoke=org.apache.fory.core` so it can obtain the trusted lookup; missing this open is an invalid access configuration, not a reason to open per-package JDK internals or switch serializer/object-creation families. Do not use `ALL-UNNAMED` as the zero-Unsafe verification target.
- Keep JDK25+ unsafe-removal implementation invariants in agent/design docs and tests, not user guides. User guides should document user actions such as `--sun-misc-unsafe-memory-access=deny`, `java.base/java.lang.invoke` opens, JDK26+ `--enable-final-field-mutation`, package opens for user named modules, and public APIs such as `@ForyConstructor` and `BaseFory.registerConstructor(...)`; do not expose internal serializer names, owner-model rationale, or avoided fallback strategies there.
- For JDK25+ zero-Unsafe final-field behavior, distinguish JDK25 from JDK26+: JDK25 has no final-field mutation flag requirement, while JDK26+ requires `--enable-final-field-mutation` for post-construction final-field writes.
- For JDK25+ object creation, do not use `sun.reflect.ReflectionFactory` or `jdk.internal.reflect.ReflectionFactory`. The shared `ObjectCreators` facade should route ObjectStream-compatible construction through `ParentNoArgCtrObjectCreator`; the replaceable constructor-bypass allocator owns the JDK8-24 Unsafe allocation path and the JDK25+ `ObjectStreamClass.newInstance` path. Serializable classes without a no-arg constructor may use that trusted-lookup owner; non-Serializable classes without a no-arg constructor require `@ForyConstructor`, `BaseFory.registerConstructor(...)`, record construction, or a custom serializer.
- In JDK25+ constructor-bypass allocation, cache `ObjectStreamClass.lookupAny(...)` per class and let `ObjectStreamClass.newInstance` validate unsupported classes. Do not add redundant `Serializable` prechecks or per-call descriptor lookups, and keep exception/message construction in cold helper methods.
- Keep the Java25 `_Lookup` and `DefineClass` overlays unless a future refactor can merge them without exposing Unsafe to the JDK25 class graph or replacing direct hidden-class APIs with reflective wrappers. Root `_Lookup` uses Unsafe for the JDK8-24 trusted-lookup fast path, while Java25 `_Lookup` uses the required `java.lang.invoke` open. Root `DefineClass` targets Java 8 bytecode and cannot directly reference `Lookup#defineHiddenClass` or `Lookup.ClassOption.NESTMATE`; Java25 `DefineClass` owns that direct API use.
- Treat `ByteArrayOutputStream` and `ByteArrayInputStream` as ordinary streams on every JDK. Do
  not restore private-buffer wrapping for JDK8-24 performance, because that reintroduces
  `java.base/java.io` private-field ownership and module-open requirements.
- `ObjectStreamSerializer` must use its stream-specific object creator path and must not use
  `TypeResolver.getObjectCreator`. `@ForyConstructor` and registered constructor mappings require
  field values up front, while ObjectStream reconstruction creates the object before stream fields
  are read. This path must not invoke Serializable class constructors, including no-arg
  constructors; the JDK8-24 ReflectionFactory template constructor must be the first
  non-Serializable superclass no-arg constructor, and private no-arg constructors must be rejected
  instead of made accessible. Package-private no-arg constructors are valid only for same-package
  Serializable subclasses.
- Root codegen and builder classes that still need Unsafe on JDK8-24 must route symbolic Unsafe
  access through a helper with a Java 25 replacement. Do not leave `_JDKAccess.unsafe()` or
  `sun.misc.Unsafe` references in JDK25-visible classes outside matching `java25` replacements.
- Java 25 multi-release classes never run on Android. Do not keep `AndroidSupport.IS_ANDROID`
  branches or imports under `src/main/java25`; Android compatibility belongs to the root sources.
- String zero-copy construction is serializer-owned behavior. Keep private `String` constructor
  lambda factories in `StringSerializer`, keep `PlatformStringUtils`
  focused on field and array access, keep serialization hook discovery in serializer-owned code,
  and keep `_JDKAccess` limited to JDK lookup, module, function factory, and access-flag
  primitives.
- JDK25+ serialization hook access must use the required trusted lookup from
  `java.base/java.lang.invoke=org.apache.fory.core`. Keep `sun.reflect.ReflectionFactory` as a
  JDK8-24 optimization only, and do not add per-type reflective escapes for hook invocation.
- JDK25+ `PlatformStringUtils` getter methods sit behind `StringSerializer` static-final access
  gates. Do not add per-call access checks in those getters; missing module opens should fail at
  trusted-lookup initialization or cold setup, not inside string hot paths.
- JDK25+ collection serializers must fail unsupported `Collections.newSetFromMap` backing maps
  before writing or copying. Do not rewrite them to `HashMap`, because that changes equality
  semantics and can drop entries.
- Do not enable Java or Kotlin parameter-name metadata (`-parameters`, `maven.compiler.parameters`, or Kotlin `javaParameters`) to make constructor binding work. Constructor binding must be driven by explicit field mappings from `@ForyConstructor`, `BaseFory.registerConstructor(...)`, or generated metadata.
- Constructor-bound serializers must cache constructor field metadata during serializer initialization. Do not call defensive-copy metadata getters such as `getConstructorFieldTypes()` from per-object read paths.
- Explicit constructor binding is for user and third-party classes, not Java platform types. Do not use
  `java.*` internals such as `String` fields as constructor-binding test data; JDK built-in classes
  are owned by their serializers.
- Runtime serializers and generated serializers must use the same constructor-copy lifecycle:
  install a pending marker before copying constructor-bound fields, run field serializers normally,
  reject copied constructor arguments that still retain the marker, then construct and reference the
  real copy. Do not implement a separate raw-field pre-scan or leave a generated path without the
  marker guard.
- Generated JVM copy code may direct-copy immutable scalar values, but Java `Collection`/`Map`
  subclasses must be copied through `CopyContext.copyObject(...)` so collection/map serializers own
  concrete type, comparator, wrapper, and reference behavior.
- When a `Throwable` is created without running `Throwable` constructors, restore the private
  `cause` and `suppressedExceptions` sentinels directly before exposing the object. Do not call
  `initCause` or `addSuppressed` on a constructor-bypassed `Throwable` whose sentinels are still
  absent.
- For JDK25+ CI, do not run core runtime tests from raw Maven reactor `target/classes`. Those classes bypass `META-INF/versions/25` and exercise the JDK8-24 root implementation. Build/install the multi-release artifact first, then verify the zero-Unsafe path through the JPMS module-path suite where `org.apache.fory.core` is the real access target.
- Do not make GraalVM native-image JDK25+ pass by opening `java.lang.invoke` to `ALL-UNNAMED`. Keep zero-Unsafe verification on JPMS JVM tests unless the native-image path itself runs Fory as a named module and the produced binary passes.

## Key Modules

- `fory-core`: core object-graph serialization runtime
- `fory-format`: row-format encoding and decoding
- `fory-extensions`: protobuf serializers and other extensions
- `fory-simd`: SIMD-accelerated serializers and utilities
- `fory-test-core`: shared Java test utilities
- `testsuite`: issue-driven and complex regression coverage
- `benchmark`: JMH benchmark suite

## Commands

```bash
# Clean the build
mvn -T16 clean

# Build
mvn -T16 package

# Install
mvn -T16 install -DskipTests

# Format check
mvn -T16 spotless:check

# Apply formatting
mvn -T16 spotless:apply

# Spotless with JDK 21 when the current runtime is older
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -T16 spotless:check

# Style check
mvn -T16 checkstyle:check

# Run tests
mvn -T16 test

# Run a specific test
mvn -T16 test -Dtest=org.apache.fory.TestClass#testMethod
```

## Debugging And IDE Notes

- Set `FORY_CODE_DIR` to dump generated code.
- Set `ENABLE_FORY_GENERATED_CLASS_UNIQUE_ID=false` when you need stable generated class names.
- When debugging Java tests or runtime behavior, set `FORY_LOG_LEVEL=INFO` unless a narrower
  level is required.
- In IntelliJ IDEA, use the same JDK and module flags as the Maven profile you are debugging.
