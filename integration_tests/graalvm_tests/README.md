# GraalVM Native Image Tests

Examples and tests for Fory serialization in GraalVM Native Image. The Fory JSON entry points are
compiled with annotation processing disabled. They cover direct `JsonType` models, exact
`JsonMixin` target/source mappings, provider-selected hosted codec generation, configuration
fallback to interpreted codecs, and a separate image with no reachable `ForyJsonProvider`.

## Test

```bash
mvn -DmainClass=org.apache.fory.graalvm.ForyJsonExample clean -DskipTests=true -Dexec.skip=true -Pnative package
./target/main
mvn -DmainClass=org.apache.fory.graalvm.ForyJsonExample clean -DskipTests=true -Pnative-module package
./target/main-module

mvn -DmainClass=org.apache.fory.graalvm.ForyJsonNoProviderExample clean -DskipTests=true -Dexec.skip=true -Pnative package
./target/main
mvn -DmainClass=org.apache.fory.graalvm.ForyJsonNoProviderExample clean -DskipTests=true -Pnative-module package
./target/main-module
```

## Benchmark

```bash
BENCHMARK_REPEAT=400000 mvn -DskipTests=true -Pnative package
```
