# The release-minified instrumented test calls target-APK methods from a
# separate androidTest APK. Keep the target-side scenario entry points so R8
# sees the same Fory API reachability that a production app would have from its
# own code.
-keep class org.apache.fory.android.AndroidForyRuntimeScenarios { *; }
-keep class org.apache.fory.android.AndroidForyRuntimeScenarios$* { *; }
