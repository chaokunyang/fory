# Kotlin

Load this file when changing `kotlin/` or compiler code that generates Kotlin source.

## Rules

- Run Kotlin Maven commands from within `kotlin/`.
- Kotlin serializers build on the Java implementation. If Java changed and the updated Java artifacts are not installed yet, run `cd ../java && mvn -T16 install -DskipTests` first.
- KSP `@ForyStruct` serializers that use a primary constructor map constructor parameters to
  same-named source properties at generation time and call the constructor directly. Do not restore
  `@ForyConstructor`, runtime constructor registration, or Kotlin `javaParameters` dependencies;
  mutable no-argument structs should use `var` properties with `@ForyField`.
- Preserve serializer-family selection for Kotlin standard-library types already registered by
  Fory. Do not auto-install a new serializer for an existing type-registered Kotlin class unless the
  wire format matches the previous serializer family and old-payload/new-runtime compatibility is
  tested.
- Public registration helpers must check the registry freeze before constructing a serializer,
  enum serializer, or union serializer. Generated serializer construction must enter the existing
  `TypeResolver` construction graph so its candidate remains unpublished until the authoritative
  lifecycle recheck and normal resolver commit.
- Combined generated-struct registration must publish the canonical type before constructing its
  serializer because generated construction resolves the canonical `TypeInfo`. The serializer-only
  helper must reject a missing canonical type rather than auto-register it. Do not move construction
  before type registration or add direct replacement, rollback, staging, or a parallel registration
  path.
- `Fory.register(ForyModule)` is the only owner of bootstrap identity, cycle breaking, and
  idempotence. Kotlin bootstrap code must not add a marker, monitor, or separate reentry policy.
  Keep the install body replay-safe until its final non-repeatable publication; publish the single
  global Kotlin default-value support owner only after all per-runtime registrations succeed, and
  never replace its class-value cache for each runtime.
- Install modules for thread-safe facades through `ForyBuilder.withModule` before building them.
  Runtime registration extensions target concrete `Fory` instances and must not recreate a
  thread-safe module-registration wrapper.
- When adding Kotlin gRPC service companions, emit Kotlin source only. Reuse the generated schema
  module's `ThreadSafeFory` and KSP-generated schema serializers, and keep grpc-java/grpc-kotlin
  dependencies application-owned instead of adding them as hard `fory-kotlin` dependencies.

## Commands

```bash
# Build
mvn clean package

# Run tests
mvn test
```
