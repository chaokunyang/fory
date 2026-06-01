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
- In primitive-array swap-endian readers, do not loop through `MemoryBuffer._unsafeGet*` helpers.
  Copy the payload through the typed payload API, then swap destination values locally so the path
  stays stream-safe and avoids Android-dispatch helper drift.
- Keep GraalVM feature code as a thin metadata/registration layer. Build time should publish metadata needed for runtime reconstruction, not retain concrete generated or user serializer instances in the image heap.
- If changes touch GraalVM bootstrap, serializer retention, native-image metadata, or `ObjectStreamSerializer` GraalVM behavior, verify the native-image build and run the produced binary; a plain Java compile is insufficient.
- Put latest-JDK or virtual-thread tests in the latest-JDK test modules with the matching compiler/profile floor, and centralize runtime-version probing in existing compatibility utilities.
- For JDK25+ zero-Unsafe work, preserve serializer-family selection by type and configuration. Do not switch a type from `ObjectStreamSerializer` or another Fory serializer family to `JavaSerializer`, a JDK stream fallback, or any broad `java.* Serializable` fallback by JDK version or no-arg-constructor shape.
- Keep JDK25+ unsafe-removal implementation invariants in agent/design docs and tests, not user guides. User guides should document user actions such as `--sun-misc-unsafe-memory-access=deny`, `java.base/java.lang.invoke` opens, JDK26+ `--enable-final-field-mutation`, package opens for user named modules, and public APIs such as `@ForyConstructor` and `BaseFory.registerConstructor(...)`; do not expose internal serializer names, owner-model rationale, or avoided fallback strategies there.
- For JDK25+ zero-Unsafe final-field behavior, distinguish JDK25 from JDK26+: JDK25 has no final-field mutation flag requirement, while JDK26+ requires `--enable-final-field-mutation` for post-construction final-field writes.
- For JDK25+ object creation, do not use `sun.reflect.ReflectionFactory` or `jdk.internal.reflect.ReflectionFactory`. Serializable classes without a no-arg constructor may use `ObjectStreamClass.newInstance` through the trusted lookup owner; non-Serializable classes without a no-arg constructor require `@ForyConstructor`, `BaseFory.registerConstructor(...)`, record construction, or a custom serializer.
- Constructor-bound serializers must cache constructor field metadata during serializer initialization. Do not call defensive-copy metadata getters such as `getConstructorFieldTypes()` from per-object read paths.
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
- In IntelliJ IDEA, use a JDK 11+ project SDK and disable `--release` if it blocks `sun.misc.Unsafe` access.
