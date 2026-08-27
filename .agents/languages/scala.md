# Scala

Load this file when changing `scala/`.

## Rules

- Run Scala commands from within `scala/`.
- Scala serializers build on the Java implementation. If Java changed and the updated Java artifacts are not installed yet, run `cd ../java && mvn -T16 install -DskipTests` first.
- Scala supports the JVM and GraalVM Native Image, not Android. Do not add Android-specific Scala
  sources, tests, resources, R8 metadata, compiler plugins, macros, dependencies, or compatibility
  design.
- Public registration helpers must check the registry freeze before invoking generated serializer
  construction or enum discovery. Registered-type replacement must check again after
  `ForySerializer` callbacks and before mutation; Scala enum registration must likewise recheck
  after companion-driven value discovery and reuse the values already owned by the serializer.
- Combined generated-struct registration must publish the canonical type before constructing its
  serializer because generated construction resolves the canonical `TypeInfo`. Do not move that
  construction before type registration or add rollback, staging, or a parallel registration path.
  Union construction is the exception because it does not require canonical registration: finish
  its serializer-owned callbacks and recheck the freeze before publishing the union type.

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
