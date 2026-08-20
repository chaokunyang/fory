/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.net.URLClassLoader
import java.security.MessageDigest

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("com.google.devtools.ksp")
  id("me.champeau.jmh")
}

group = "org.apache.fory.benchmark"
version = "1.0-SNAPSHOT"

val foryVersion: String by project
val kotlinxSerializationVersion: String by project
val moshiVersion: String by project
val jacksonVersion: String by project
val benchmarkJmhVersion = providers.gradleProperty("jmhVersion").get()

dependencies {
  implementation("org.apache.fory:fory-json-kotlin:$foryVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
  implementation("com.squareup.moshi:moshi:$moshiVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

  ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")

  testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.1")
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
  useJUnitPlatform()
}

jmh {
  jmhVersion = benchmarkJmhVersion
  benchmarkMode = listOf("thrpt")
  timeUnit = "s"
  warmupIterations = 3
  iterations = 5
  fork = 1
  threads = 1
  timeOnIteration = "2s"
  warmup = "2s"
  resultFormat = "JSON"
}

val verifyGeneratedJsonArtifacts = tasks.register("verifyGeneratedJsonArtifacts") {
  dependsOn(tasks.named("classes"))
  doLast {
    val runtimeFiles = sourceSets.main.get().runtimeClasspath.files
    URLClassLoader(runtimeFiles.map { it.toURI().toURL() }.toTypedArray(), null).use { loader ->
      val modelNames =
        listOf(
          "org.apache.fory.benchmark.json.MediaContent",
          "org.apache.fory.benchmark.json.Media",
          "org.apache.fory.benchmark.json.Image",
        )
      for (modelName in modelNames) {
        loader.loadClass(modelName + "JsonAdapter")
      }
    }
  }
}

tasks.named("check") {
  dependsOn(verifyGeneratedJsonArtifacts)
}

tasks.matching { it.name == "compileJmhKotlin" || it.name == "jmhClasses" }.configureEach {
  dependsOn(verifyGeneratedJsonArtifacts)
}

fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

val benchmarkRuntimeFiles = {
  (sourceSets.main.get().runtimeClasspath.files +
      tasks.named<Jar>("jar").get().archiveFile.get().asFile)
    .filter { it.isFile }
    .distinctBy { it.canonicalPath }
    .sortedBy { it.canonicalPath }
}

val benchmarkProvenanceFile =
  layout.buildDirectory.file("generated/benchmark-provenance/benchmark-runtime.properties")

val writeBenchmarkProvenance = tasks.register("writeBenchmarkProvenance") {
  dependsOn(tasks.named("jar"))
  inputs.files(sourceSets.main.get().runtimeClasspath)
  inputs.file(tasks.named<Jar>("jar").flatMap { it.archiveFile })
  outputs.file(benchmarkProvenanceFile)
  doLast {
    val identities = benchmarkRuntimeFiles().map { it.name to sha256(it) }
    val foryArtifacts =
      identities.filter { (name, _) ->
        name.startsWith("fory-json-kotlin-") && !name.contains("-ksp-")
      }
    check(foryArtifacts.size == 1) {
      "Expected one fory-json-kotlin runtime artifact, found ${foryArtifacts.size}"
    }
    val dependencyDigest = MessageDigest.getInstance("SHA-256")
    identities.sortedWith(compareBy({ it.first }, { it.second })).forEach { (name, hash) ->
      dependencyDigest.update(name.toByteArray(Charsets.UTF_8))
      dependencyDigest.update(0.toByte())
      dependencyDigest.update(hash.toByteArray(Charsets.US_ASCII))
      dependencyDigest.update('\n'.code.toByte())
    }
    val output = benchmarkProvenanceFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(
      "formatVersion=1\n" +
        "foryArtifactSha256=${foryArtifacts.single().second}\n" +
        "dependencySetSha256=${dependencyDigest.digest().joinToString("") { "%02x".format(it) }}\n",
      Charsets.UTF_8,
    )
  }
}

tasks.named<Jar>("jmhJar") {
  dependsOn(writeBenchmarkProvenance)
  from(benchmarkProvenanceFile) {
    into("META-INF")
    rename { "fory-kotlin-json-benchmark.properties" }
  }
}

tasks.register("writeBenchmarkClasspath") {
  dependsOn(tasks.named("jar"))
  val output = layout.buildDirectory.file("benchmark-runtime-classpath.txt")
  outputs.file(output)
  doLast {
    output.get().asFile.writeText(
      benchmarkRuntimeFiles().joinToString("\n", postfix = "\n") { it.canonicalPath }
    )
  }
}
