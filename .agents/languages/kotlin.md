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
  enum serializer, or union serializer. A registered-type serializer replacement must check again
  after generated serializer construction and before the explicit replacement because
  `TypeResolver.setSerializer` remains available for lazy internal resolution.
- Combined generated-struct registration must publish the canonical type before constructing its
  serializer because generated construction resolves the canonical `TypeInfo`. Do not move that
  construction before type registration or add rollback, staging, or a parallel registration path.
- Bootstrap installation markers must be published only after every nested registration completes
  and an authoritative lifecycle recheck succeeds. A failed or root-reentered installation leaves
  the marker absent naturally; do not publish early and add rollback or staging state. Use the
  concrete `Fory` as the per-runtime monitor for the complete bootstrap, and reject same-runtime
  same-thread reentry instead of recursively starting a second installation.
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
