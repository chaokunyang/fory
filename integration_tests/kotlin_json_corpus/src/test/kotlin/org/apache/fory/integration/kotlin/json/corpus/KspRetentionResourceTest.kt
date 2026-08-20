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

package org.apache.fory.integration.kotlin.json.corpus

import java.nio.file.Files
import java.nio.file.Path
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

public class KspRetentionResourceTest {
  @Test
  public fun emitsOnlyExactResources(): Unit {
    val generatedResources = Path.of("target/generated-resources/ksp")
    val outputs = regularFiles(generatedResources)
    assertTrue(outputs.isNotEmpty(), "KSP did not emit retention resources")
    assertTrue(
      outputs.all { generatedResources.relativize(it).toString().endsWith(".pro") },
      outputs.toString(),
    )
    assertTrue(regularFiles(Path.of("target/generated-sources/ksp")).isEmpty())
    assertTrue(regularFiles(Path.of("target/ksp-classes")).isEmpty())

    val named = rules("PlatformNamedSubtypeBase")
    assertTrue(named.contains("-keep,allowoptimization class $PACKAGE.PlatformNamedSubtype"), named)
    assertFalse(named.contains("java.lang.Void"), named)

    val base64 = rules("PlatformBase64Owner")
    assertConstructor(base64, "org.apache.fory.json.codec.Base64ByteArrayCodec")

    val endpoints = rules("PlatformEndpointOwner")
    assertConstructor(endpoints, "$PACKAGE.PlatformDirectEndpointCodec")
    assertConstructor(endpoints, "$PACKAGE.PlatformInheritedEndpointCodec")
    assertConstructor(endpoints, "$PACKAGE.PlatformEndpointList")
    assertConstructor(endpoints, "$PACKAGE.PlatformEndpointMap")
    assertTrue(
      endpoints.contains(
        "-keep,allowoptimization,allowobfuscation class $PACKAGE.PlatformEndpointContract"
      ),
      endpoints,
    )

    val methodEndpoint = rules("PlatformMethodEndpointOwner")
    assertConstructor(methodEndpoint, "$PACKAGE.PlatformDirectEndpointCodec")

    val mixin = mixinRules("PlatformMixinRetention")
    assertConstructor(mixin, "$PACKAGE.PlatformReplacementTypeCodec")
    assertFalse(mixin.contains("PlatformOldTypeCodec"), mixin)
    assertFalse(mixin.contains("PlatformRemovedSubtype"), mixin)

    val factory = rules("PlatformFactoryModel")
    assertTrue(
      factory.contains("$PACKAGE.PlatformFactoryModel create($PACKAGE.PlatformDirectEndpoint);"),
      factory,
    )
    assertTrue(factory.contains("class $PACKAGE.PlatformFactoryModel\$Companion"), factory)
    assertConstructor(factory, "$PACKAGE.PlatformDirectEndpointCodec")

    outputs.forEach { output ->
      val text = Files.readString(output)
      assertFalse(text.contains('*'), output.toString())
      assertFalse(text.contains("JsonCodec\$NoJsonValueCodec"), output.toString())
      assertFalse(text.contains("JsonCodec\$NoMapKeyCodec"), output.toString())
    }
  }

  private fun rules(model: String): String =
    resourceText("META-INF/proguard/fory-json-$PACKAGE.$model.pro")

  private fun mixinRules(model: String): String =
    resourceText("META-INF/proguard/fory-json-mixin-$PACKAGE.$model.pro")

  private fun resourceText(path: String): String =
    checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing KSP resource $path" }
      .bufferedReader()
      .use { it.readText() }

  private fun assertConstructor(rules: String, type: String) {
    assertTrue(
      rules.contains("-keep,allowoptimization,allowobfuscation class $type"),
      rules,
    )
    assertTrue(rules.contains("-keepclassmembers class $type {\n  public <init>();\n}"), rules)
  }

  private fun regularFiles(root: Path): List<Path> {
    if (!Files.exists(root)) return emptyList()
    return Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).toList() }
  }

  private companion object {
    const val PACKAGE = "org.apache.fory.integration.kotlin.json.corpus"
  }
}
