# Scala

Load this file when changing `scala/`.

## Rules

- Run Scala commands from within `scala/`.
- Scala serializers build on the Java implementation. If Java changed and the updated Java artifacts are not installed yet, run `cd ../java && mvn -T16 install -DskipTests` first.
- Missing Scala JSON case-class constructor parameters use their type defaults: zero for numbers,
  false for booleans, empty collections and arrays, None for Option, and null for other references.
  Explicit constructor defaults take precedence. Mutable defaults must be fresh for each object.
  Explicit JSON null keeps its existing decoding semantics. Preserve these rules in interpreted
  and generated readers.
- Scala supports the JVM and GraalVM Native Image, not Android. Do not add Android-specific Scala
  sources, tests, resources, R8 metadata, compiler plugins, macros, dependencies, or compatibility
  design.

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
