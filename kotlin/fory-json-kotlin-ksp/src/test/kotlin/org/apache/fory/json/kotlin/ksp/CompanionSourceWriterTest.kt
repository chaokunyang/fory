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

import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.GeneratedJsonCodec
import org.apache.fory.json.kotlin.KotlinGeneratedValueClassCodec
import org.apache.fory.json.kotlin.KotlinLongValueClassOperations
import org.apache.fory.json.kotlin.KotlinValueClassOperationsOwner
import org.apache.fory.json.meta.JsonCreatorInfo
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.reflect.TypeRef

class CompanionSourceWriterTest {
  @Test
  fun emitsDirectAccessorsAndCreator() {
    val creator =
      JsonCreator(
        parameterNames = listOf("id", "display\"name", "active"),
        parameterTypes = listOf(JvmType("J"), JvmType("Ljava/lang/String;"), JvmType("Z")),
        optional = booleanArrayOf(false, true, true),
        invocationOwner = "example.User",
        invocationName = "<init>",
        invocationDescriptor = "(JLjava/lang/String;Z)V",
        defaultDescriptor =
          "(JLjava/lang/String;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V",
      )
    val source =
      CompanionSourceWriter.write(
        model(
          members =
            listOf(
              JvmMember(MemberKind.FIELD, "example.User", false, "id", "J", writable = true),
              JvmMember(
                MemberKind.GETTER,
                "example.User",
                false,
                "getName",
                "()Ljava/lang/String;",
              ),
              JvmMember(MemberKind.SETTER, "example.User", false, "setActive", "(Z)V"),
            ),
          creator = creator,
        )
      )

    assertContains(source, "extends org.apache.fory.json.codec.GeneratedJsonCodec<example.User>")
    assertContains(source, "return User_ForyJsonOperations.get_0((example.User) target);")
    assertContains(source, "User_ForyJsonOperations.set_0((example.User) target, value);")
    assertContains(source, "return User_ForyJsonOperations.get_1((example.User) target);")
    assertContains(source, "User_ForyJsonOperations.set_2((example.User) target, value);")
    assertContains(source, "\"display\\\"name\"")
    assertContains(source, "if (missing0)")
    assertContains(source, "int mask0 = 0;")
    assertContains(source, "if (missing1) mask0 |= 2;")
    assertContains(source, "if (missing2) mask0 |= 4;")
    assertContains(source, "if ((mask0) == 0)")
    assertContains(source, "return User_ForyJsonOperations.createFull(")
    assertContains(source, "return User_ForyJsonOperations.createDefault(")
    assertFalse(source.contains("java.lang.reflect.Method.invoke"))
    assertFalse(source.contains("java.lang.reflect.Constructor"))
    assertFalse(source.contains("Class.forName"))
    assertFalse(source.contains("TypeResolver"))
  }

  @Test
  fun aggregatesDefaultMasksOnce() {
    val parameterTypes = List(33) { JvmType("I") }
    val creator =
      JsonCreator(
        parameterNames = List(33) { "value$it" },
        parameterTypes = parameterTypes,
        optional = BooleanArray(33) { true },
        invocationOwner = "example.Large",
        invocationName = "<init>",
        invocationDescriptor = methodDescriptor(parameterTypes, "V"),
        defaultDescriptor =
          appendParameters(
            methodDescriptor(parameterTypes, "V"),
            listOf(
              JvmType("I"),
              JvmType("I"),
              JvmType("Lkotlin/jvm/internal/DefaultConstructorMarker;"),
            ),
          ),
      )

    val source = CompanionSourceWriter.write(model(target = "example.Large", creator = creator))

    assertContains(source, "int mask0 = 0;")
    assertContains(source, "int mask1 = 0;")
    assertContains(source, "if ((mask0 | mask1) == 0)")
    assertEquals(1, Regex("mask0 \\| mask1").findAll(source).count())
    assertEquals(1, Regex("createFull\\(").findAll(source).count())
    assertEquals(1, Regex("createDefault\\(").findAll(source).count())
  }

  @Test
  fun emitsColdMetadataAndDirectCallbacks() {
    val source =
      CompanionSourceWriter.write(
        model(
          anySetter =
            JvmAnySetter(
              ownerBinaryName = "example.User",
              ownerInterface = false,
              name = "putExtra",
              descriptor = "(Ljava/lang/String;Ljava/lang/Object;)V",
            ),
          validators = listOf(JvmValidator("example.User", false, "validate")),
          singleton = true,
        )
      )

    assertContains(source, "return User_ForyJsonOperations.instance();")
    assertContains(source, "User_ForyJsonOperations.setAny(")
    assertContains(source, "User_ForyJsonOperations.validate_0(")
    assertContains(source, "private static java.lang.reflect.Method declaredMethod(")
    assertContains(source, "owner.getDeclaredMethod(name, parameterTypes)")
    assertFalse(source.contains(".invoke(target"))
    assertFalse(source.contains("setAccessible"))
    assertTrue(
      source.indexOf("private static final java.lang.reflect.Method VALIDATOR_0") <
        source.indexOf("invokeValidators")
    )
  }

  @Test
  fun compilesAndLoadsGeneratedArtifacts() {
    val target = DefaultOperationFixture::class.java
    val parameters = listOf(JvmType("J"), JvmType("Ljava/lang/String;"), JvmType("Z"))
    val model =
      JsonModel(
        packageName = target.packageName,
        targetBinaryName = target.name,
        targetSourceName = target.canonicalName,
        companionSimpleName = "DefaultOperationFixture_ForyJsonCodec",
        operationSimpleName = "DefaultOperationFixture_ForyJsonOperations",
        generateCompanion = true,
        members = emptyList(),
        anySetter = null,
        validators = emptyList(),
        creator =
          JsonCreator(
            parameterNames = listOf("id", "name", "enabled"),
            parameterTypes = parameters,
            optional = booleanArrayOf(false, true, true),
            invocationOwner = target.name,
            invocationName = "<init>",
            invocationDescriptor = methodDescriptor(parameters, "V"),
            defaultDescriptor =
              appendParameters(
                methodDescriptor(parameters, "V"),
                listOf(
                  JvmType("I"),
                  JvmType("Lkotlin/jvm/internal/DefaultConstructorMarker;"),
                ),
              ),
          ),
        singleton = false,
        valueClass = null,
        mixinBinaryName = null,
        originatingFiles = emptyList(),
        retainedAnnotations = emptySet(),
        retainedTypes = emptySet(),
        mixinMembers = emptyList(),
      )
    val directory = Files.createTempDirectory("fory-json-ksp-generated")
    try {
      compileGenerated(model, directory)

      URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
        val codec =
          loader.loadClass(model.companionBinaryName).getConstructor().newInstance()
            as GeneratedJsonCodec<*>
        val value = codec.newInstance(arrayOf<Any?>(83L, "generated", false))
        assertEquals(DefaultOperationFixture(83L, "generated", false), value)
        val missing = missingValue()
        assertEquals(
          DefaultOperationFixture(89L),
          codec.newInstance(arrayOf<Any?>(89L, missing, missing)),
        )
        val failure =
          assertFailsWith<ForyJsonException> {
            codec.newInstance(arrayOf<Any?>(missing, "required", true))
          }
        assertContains(failure.message.orEmpty(), "Missing required JSON constructor property id")
      }
    } finally {
      directory.toFile().deleteRecursively()
    }
  }

  @Test
  fun compilesValueCompanion() {
    val target = ValueOperationFixture::class.java
    val underlyingType =
      "org.apache.fory.reflect.TypeRef.of(long.class, " +
        "org.apache.fory.meta.TypeExtMeta.of(0, false, false, false, false))"
    val model =
      JsonModel(
        packageName = target.packageName,
        targetBinaryName = target.name,
        targetSourceName = target.canonicalName,
        companionSimpleName = "ValueOperationFixture_ForyJsonCodec",
        operationSimpleName = "ValueOperationFixture_ForyJsonOperations",
        generateCompanion = true,
        members = emptyList(),
        anySetter = null,
        validators = emptyList(),
        creator = null,
        singleton = false,
        valueClass =
          ValueClassOperations(
            layers =
              listOf(
                ValueClassLayer(
                  ownerBinaryName = target.name,
                  carrierType = JvmType("J"),
                  occurrenceTypeExpression = "type",
                  underlyingTypeExpression = underlyingType,
                )
              ),
            terminalType = JvmType("J"),
            terminalTypeExpression = underlyingType,
          ),
        mixinBinaryName = null,
        originatingFiles = emptyList(),
        retainedAnnotations = setOf("kotlin.Metadata"),
        retainedTypes = emptySet(),
        mixinMembers = emptyList(),
      )
    val source = CompanionSourceWriter.write(model)

    assertContains(source, "extends org.apache.fory.json.kotlin.KotlinGeneratedValueClassCodec")
    assertContains(source, "implements org.apache.fory.json.kotlin.KotlinLongValueClassOperations")
    assertContains(source, "ValueOperationFixture_ForyJsonOperations.valueConstruct_0(value)")
    assertContains(source, "ValueOperationFixture_ForyJsonOperations.valueBox_0(carrier_0)")
    assertContains(source, "ValueOperationFixture_ForyJsonOperations.valueUnbox_0(value)")
    assertFalse(source.contains("java.lang.reflect.Method.invoke"))
    assertFalse(source.contains("java.lang.invoke.MethodHandle"))

    val directory = Files.createTempDirectory("fory-json-ksp-value")
    try {
      compileGenerated(model, directory)
      URLClassLoader(arrayOf(directory.toUri().toURL()), javaClass.classLoader).use { loader ->
        val codec = loader.loadClass(model.companionBinaryName).getConstructor().newInstance()
        assertTrue(codec is KotlinGeneratedValueClassCodec<*>)
        @Suppress("UNCHECKED_CAST")
        val operations = codec as KotlinLongValueClassOperations<ValueOperationFixture>
        val value = operations.constructLongUncharged(97L)
        assertEquals(ValueOperationFixture(97L), value)
        assertEquals(97L, operations.unboxLong(value))

        val owner = codec as KotlinValueClassOperationsOwner
        val unboxed = owner.unboxedOperations()
        assertEquals(
          103L,
          org.apache.fory.json.kotlin.KotlinUnboxedValueClassOperations::class
            .java
            .getMethod("constructCarrier", JsonReader::class.java, Any::class.java)
            .invoke(unboxed, null, 103L),
        )
        assertEquals(107L, unboxed.extractValue(107L))
        val boxed = unboxed.boxCarrier(109L)
        assertEquals(ValueOperationFixture(109L), boxed)
        assertEquals(109L, unboxed.unboxValue(boxed))
        assertEquals("constructor-impl", unboxed.constructMethods().single().name)
        assertEquals("box-impl", unboxed.boxMethod().name)
        assertEquals("unbox-impl", unboxed.unboxMethod().name)

        val generated = codec as GeneratedJsonCodec<*>
        assertNotNull(generated.newTypeCodec(TypeRef.of(target)))
        assertNotNull(generated.newMapKey(TypeRef.of(target)))
      }
    } finally {
      directory.toFile().deleteRecursively()
    }
  }

  private fun compileGenerated(model: JsonModel, directory: java.nio.file.Path) {
    val operationFile =
      directory.resolve(model.operationBinaryName.replace('.', File.separatorChar) + ".class")
    Files.createDirectories(operationFile.parent)
    Files.write(operationFile, OperationClassWriter.write(model))
    val sourceFile =
      directory.resolve(model.companionBinaryName.replace('.', File.separatorChar) + ".java")
    Files.createDirectories(sourceFile.parent)
    Files.writeString(
      sourceFile,
      CompanionSourceWriter.write(model),
      StandardCharsets.UTF_8,
    )

    val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler())
    val diagnostics = DiagnosticCollector<JavaFileObject>()
    compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
      val units = fileManager.getJavaFileObjects(sourceFile.toFile())
      val classPath = System.getProperty("java.class.path") + File.pathSeparator + directory
      val compiled =
        compiler
          .getTask(
            null,
            fileManager,
            diagnostics,
            listOf(
              "-source",
              "8",
              "-target",
              "8",
              "-classpath",
              classPath,
              "-d",
              directory.toString()
            ),
            null,
            units,
          )
          .call()
      assertTrue(
        compiled,
        diagnostics.diagnostics.joinToString(System.lineSeparator()),
      )
    }
  }

  private fun missingValue(): Any {
    val field = JsonCreatorInfo::class.java.getDeclaredField("MISSING")
    field.isAccessible = true
    return field.get(null)
  }

  private fun model(
    target: String = "example.User",
    members: List<JvmMember> = emptyList(),
    anySetter: JvmAnySetter? = null,
    validators: List<JvmValidator> = emptyList(),
    creator: JsonCreator? = null,
    singleton: Boolean = false,
  ): JsonModel {
    val packageName = target.substringBeforeLast('.', "")
    val simpleName = target.substringAfterLast('.')
    return JsonModel(
      packageName = packageName,
      targetBinaryName = target,
      targetSourceName = target,
      companionSimpleName = "${simpleName}_ForyJsonCodec",
      operationSimpleName = "${simpleName}_ForyJsonOperations",
      generateCompanion = true,
      members = members,
      anySetter = anySetter,
      validators = validators,
      creator = creator,
      singleton = singleton,
      valueClass = null,
      mixinBinaryName = null,
      originatingFiles = emptyList(),
      retainedAnnotations = emptySet(),
      retainedTypes = emptySet(),
      mixinMembers = emptyList(),
    )
  }
}
