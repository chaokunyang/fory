# Fory JSON Kotlin KSP

`fory-json-kotlin-ksp` generates the exact JVM operations required by
`fory-json-kotlin` for Kotlin classes annotated with `@JsonType` and for source-owned
`@JsonMixin` requests involving Kotlin declarations.

The processor emits a deterministic `GeneratedJsonCodec`, direct JVM bridge bytecode, and
model-specific R8 consumer rules. JSON parsing, naming, annotations, child-codec resolution,
security accounting, and recursion remain owned by `fory-json`.

Use this artifact as a KSP processor and depend on `fory-json-kotlin` at runtime. Generated class
output must be included in the application JAR or Android artifact.
