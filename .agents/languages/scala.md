# Scala

Load this file when changing `scala/`.

## Rules

- Run Scala commands from within `scala/`.
- Scala serializers build on the Java implementation. If Java changed and the updated Java artifacts are not installed yet, run `cd ../java && mvn -T16 install -DskipTests` first.
- Scala supports the JVM and GraalVM Native Image, not Android. Do not add Android-specific Scala
  sources, tests, resources, R8 metadata, compiler plugins, macros, dependencies, or compatibility
  design.
- Scala registration extensions target `BaseFory` so direct and thread-safe facades share the same
  pre-root registration API, including module installation. Complete thread-safe facade
  registration before concurrent serialization, deserialization, copy, or execution begins.
- Explicit type, serializer, enum, and union registration checks the receiving `BaseFory` facade or
  natural registry owner's one frozen flag before mutation. Keep generated serializer construction
  on the existing direct resolver path; do not add a parallel registration path or lifecycle state.
- Combined generated structural registration attaches the serializer with `setSerializer` after
  registering the canonical STRUCT `TypeInfo`; `registerSerializer` would incorrectly reclassify
  that wire identity as EXT. Generated unions use `registerUnion`.

## Commands

```bash
# Compile
sbt compile

# Run tests
sbt test

# Repo-owned formatter pass for changed files
cd .. && ci/format.sh
```

The Scala module does not currently wire a `scalafmt` sbt command.
