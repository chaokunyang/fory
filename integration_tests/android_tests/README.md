# Android Integration Tests

This project runs Android API 26+ instrumented tests for Java `fory-core`, Java
`fory-json`, and Kotlin `fory-json-kotlin`. API 26 runs both debug coverage and
the release-minified suite. API 36 runs the release-minified suite. Release
coverage verifies static serializers, processor-generated Fory JSON execution
for mutable Java classes, `JsonCreator` classes, object-mapped and `JsonValue`
desugared Records, Kotlin immutable/default/value/object/sealed models, generated
retention rules, generated validator invocation and failure propagation, and
generated operations for exact target-Mixin pairs. It also verifies that a
Kotlin model without its required generated companion fails and that the next
root operation succeeds. Mixin coverage registers the source at runtime after
R8 minification so broad application keep rules cannot hide missing processor
output.

The tests consume `org.apache.fory:fory-core:1.7.0-SNAPSHOT`,
`org.apache.fory:fory-json:1.7.0-SNAPSHOT`,
`org.apache.fory:fory-annotation-processor:1.7.0-SNAPSHOT`,
`org.apache.fory:fory-json-kotlin:1.7.0-SNAPSHOT`,
`org.apache.fory:fory-json-kotlin-ksp:1.7.0-SNAPSHOT`, and the shared Kotlin JSON
corpus from the local Maven repository. From the repository root, install the
Java, Kotlin, KSP, and corpus artifacts through the single Kotlin CI owner before
running Gradle:

```bash
python ./ci/run_ci.py kotlin --task install
cd integration_tests/android_tests
gradle --no-daemon -PforyTestBuildType=debug connectedCheck
gradle --no-daemon -PforyTestBuildType=release connectedCheck
```

The `foryTestBuildType` property is test-only. It selects the target build type
used by the instrumentation suite; production build configuration is unchanged.

`java/fory-format` is intentionally not covered here because it is not part of
the Android support surface.
