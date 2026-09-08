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
- Kotlin Regex deserialization rejects `CANON_EQ` at its registered serialized carrier before
  `readResolve` compiles the pattern. Generic replacement stubs must reuse that carrier's checked
  method cache; guarding only ordinary Regex dispatch leaves a wire-selected alternate outer type
  unchecked. Async compilation may replace the carrier's data delegate, never its read guard.
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
