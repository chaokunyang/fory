# Android Integration Tests

This project runs Android API 26+ instrumented tests for Java `fory-core`.

The tests consume `org.apache.fory:fory-core:0.18.0-SNAPSHOT` from the local Maven repository, so
install the Java artifact before running Gradle:

```bash
cd ../../java
mvn -T16 --no-transfer-progress -pl fory-core -am install -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true
cd ../integration_tests/android_tests
gradle --no-daemon connectedCheck
```

`java/fory-format` is intentionally not covered here because it is not part of the Android support
surface.
