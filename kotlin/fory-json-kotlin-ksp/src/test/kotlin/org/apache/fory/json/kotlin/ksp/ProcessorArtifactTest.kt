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

package org.apache.fory.json.kotlin.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSFile
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProcessorArtifactTest {
  @Test
  fun discoversProvider() {
    val providers =
      ServiceLoader.load(SymbolProcessorProvider::class.java)
        .filterIsInstance<ForyJsonKotlinSymbolProcessorProvider>()

    assertEquals(1, providers.size)
  }

  @Test
  fun writesOwnedArtifacts() {
    val generator = RecordingCodeGenerator()
    val environment =
      SymbolProcessorEnvironment(
        emptyMap(),
        KotlinVersion.CURRENT,
        generator,
        SilentLogger,
      )
    val processor = ForyJsonKotlinSymbolProcessorProvider().create(environment)
    val write = processor.javaClass.getDeclaredMethod("write", JsonModel::class.java)
    write.isAccessible = true
    val concreteSource = sourceFile("Artifact.kt")
    val concrete = model(generateCompanion = true, originatingFiles = listOf(concreteSource))

    write.invoke(processor, concrete)

    assertEquals(
      setOf(
        "example/Artifact_ForyJsonCodec.java",
        "example/Artifact_ForyJsonCodec_Operations.class",
        "META-INF/proguard/fory-json-example.Artifact.pro",
      ),
      generator.outputs.keys,
    )
    assertContains(
      generator.text("example/Artifact_ForyJsonCodec.java"),
      "public final class Artifact_ForyJsonCodec",
    )
    val operations = generator.bytes("example/Artifact_ForyJsonCodec_Operations.class")
    assertEquals(52, (operations[6].toInt() and 0xff shl 8) or (operations[7].toInt() and 0xff))
    assertFalse(
      generator.text("META-INF/proguard/fory-json-example.Artifact.pro").contains('*'),
    )
    assertTrue(generator.dependencies.all { !it.aggregating && !it.isAllSources })
    assertTrue(
      generator.dependencies.all {
        it.originatingFiles.size == 1 && it.originatingFiles.single() === concreteSource
      }
    )

    val sealedGenerator = RecordingCodeGenerator()
    val sealedProcessor =
      ForyJsonKotlinSymbolProcessorProvider()
        .create(
          SymbolProcessorEnvironment(
            emptyMap(),
            KotlinVersion.CURRENT,
            sealedGenerator,
            SilentLogger,
          )
        )
    val sealedWrite = sealedProcessor.javaClass.getDeclaredMethod("write", JsonModel::class.java)
    sealedWrite.isAccessible = true
    val sealedSource = sourceFile("Shape.kt")
    sealedWrite.invoke(
      sealedProcessor,
      model(
        target = "example.Shape",
        generateCompanion = false,
        originatingFiles = listOf(sealedSource),
      ),
    )

    assertEquals(
      setOf("META-INF/proguard/fory-json-example.Shape.pro"),
      sealedGenerator.outputs.keys,
    )
    assertTrue(
      sealedGenerator.dependencies.single().originatingFiles.single() === sealedSource,
    )
  }

  @Test
  fun writesMixinPairArtifacts() {
    val targetSource = sourceFile("JavaTarget.java")
    val firstSource = sourceFile("KotlinMixin.kt")
    val secondSource = sourceFile("SecondMixin.kt")
    val firstName = "KotlinMixin_ForyJsonMixin_example_JavaTarget_ForyJsonCodec"
    val secondName = "SecondMixin_ForyJsonMixin_example_JavaTarget_ForyJsonCodec"
    val first =
      model(
        target = "example.JavaTarget",
        packageName = "mixins",
        companionSimpleName = firstName,
        generateCompanion = true,
        mixinBinaryName = "mixins.KotlinMixin",
        originatingFiles = listOf(firstSource, targetSource),
      )
    val second =
      model(
        target = "example.JavaTarget",
        packageName = "mixins",
        companionSimpleName = secondName,
        generateCompanion = true,
        mixinBinaryName = "mixins.SecondMixin",
        originatingFiles = listOf(secondSource, targetSource),
      )
    val generator = RecordingCodeGenerator()
    val processor = processor(generator)
    val write = processor.javaClass.getDeclaredMethod("write", JsonModel::class.java)
    write.isAccessible = true

    write.invoke(processor, first)
    write.invoke(processor, second)

    val firstPaths =
      setOf(
        "mixins/$firstName.java",
        "mixins/${firstName}_Operations.class",
        "META-INF/proguard/fory-json-mixin-mixins.KotlinMixin.pro",
      )
    val secondPaths =
      setOf(
        "mixins/$secondName.java",
        "mixins/${secondName}_Operations.class",
        "META-INF/proguard/fory-json-mixin-mixins.SecondMixin.pro",
      )
    assertTrue(firstPaths.intersect(secondPaths).isEmpty())
    assertEquals(firstPaths + secondPaths, generator.outputs.keys)
    assertOwners(generator, firstPaths, listOf(firstSource, targetSource))
    assertOwners(generator, secondPaths, listOf(secondSource, targetSource))
    assertContains(
      generator.text("META-INF/proguard/fory-json-mixin-mixins.KotlinMixin.pro"),
      "class example.JavaTarget",
    )
    assertContains(
      generator.text("META-INF/proguard/fory-json-mixin-mixins.KotlinMixin.pro"),
      "class mixins.KotlinMixin",
    )

    val repeatedGenerator = RecordingCodeGenerator()
    val repeated = processor(repeatedGenerator)
    val repeatedWrite = repeated.javaClass.getDeclaredMethod("write", JsonModel::class.java)
    repeatedWrite.isAccessible = true
    repeatedWrite.invoke(repeated, first)
    assertEquals(firstPaths, repeatedGenerator.outputs.keys)
    firstPaths.forEach { path ->
      assertTrue(generator.bytes(path).contentEquals(repeatedGenerator.bytes(path)), path)
    }
  }

  private fun model(
    target: String = "example.Artifact",
    packageName: String = target.substringBeforeLast('.', ""),
    companionSimpleName: String = "${target.substringAfterLast('.')}_ForyJsonCodec",
    generateCompanion: Boolean,
    mixinBinaryName: String? = null,
    originatingFiles: List<KSFile> = emptyList(),
  ): JsonModel {
    return JsonModel(
      packageName = packageName,
      targetBinaryName = target,
      targetSourceName = target,
      companionSimpleName = companionSimpleName,
      operationSimpleName = companionSimpleName + "_Operations",
      generateCompanion = generateCompanion,
      members = emptyList(),
      anySetter = null,
      validators = emptyList(),
      creator = null,
      singleton = false,
      valueClass = null,
      mixinBinaryName = mixinBinaryName,
      originatingFiles = originatingFiles,
      retainedAnnotations = setOf("kotlin.Metadata"),
      retainedTypes = emptySet(),
      mixinMembers = emptyList(),
    )
  }

  private fun processor(generator: RecordingCodeGenerator) =
    ForyJsonKotlinSymbolProcessorProvider()
      .create(
        SymbolProcessorEnvironment(
          emptyMap(),
          KotlinVersion.CURRENT,
          generator,
          SilentLogger,
        )
      )

  private fun assertOwners(
    generator: RecordingCodeGenerator,
    paths: Set<String>,
    files: List<KSFile>,
  ) {
    for (path in paths) {
      val owners = generator.dependenciesByPath.getValue(path).originatingFiles
      assertEquals(files.size, owners.size, path)
      files.indices.forEach { index -> assertSame(files[index], owners[index], path) }
    }
  }
}
