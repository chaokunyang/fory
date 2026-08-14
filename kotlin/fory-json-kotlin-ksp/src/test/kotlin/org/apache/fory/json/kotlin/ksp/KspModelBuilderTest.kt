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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KspModelBuilderTest {
  @Test
  fun ownsCrossLanguageMixins() {
    val builder = KspModelBuilder(resolver(), SilentLogger)
    val javaTargetFile = sourceFile("JavaTarget.java")
    val javaTarget = declaration("example.JavaTarget", Origin.JAVA, ClassKind.CLASS, javaTargetFile)
    val kotlinMixinFile = sourceFile("KotlinMixin.kt")
    val kotlinMixin = mixin("mixins.KotlinMixin", Origin.KOTLIN, kotlinMixinFile, javaTarget)

    val kotlinToJava = assertNotNull(builder.mixin(kotlinMixin))

    assertEquals("example.JavaTarget", kotlinToJava.targetBinaryName)
    assertEquals("mixins.KotlinMixin", kotlinToJava.mixinBinaryName)
    assertEquals("mixins", kotlinToJava.packageName)
    assertEquals(
      "KotlinMixin_ForyJsonMixin_example_x2e_JavaTarget_ForyJsonCodec",
      kotlinToJava.companionSimpleName,
    )
    assertTrue(kotlinToJava.generateCompanion)
    assertOwners(kotlinToJava, kotlinMixinFile, javaTargetFile)
    assertEquals(
      "META-INF/proguard/fory-json-mixin-mixins.KotlinMixin.pro",
      R8RulesWriter.resourcePath(kotlinToJava),
    )
    val repeated = assertNotNull(builder.mixin(kotlinMixin))
    assertEquals(kotlinToJava.companionBinaryName, repeated.companionBinaryName)
    assertEquals(R8RulesWriter.resourcePath(kotlinToJava), R8RulesWriter.resourcePath(repeated))

    val secondMixin =
      mixin("mixins.SecondMixin", Origin.KOTLIN, sourceFile("SecondMixin.kt"), javaTarget)
    val second = assertNotNull(builder.mixin(secondMixin))
    assertNotEquals(kotlinToJava.companionBinaryName, second.companionBinaryName)
    assertNotEquals(R8RulesWriter.resourcePath(kotlinToJava), R8RulesWriter.resourcePath(second))

    val kotlinTargetFile = sourceFile("KotlinObject.kt")
    val kotlinTarget =
      declaration("example.KotlinObject", Origin.KOTLIN, ClassKind.OBJECT, kotlinTargetFile)
    val javaMixinFile = sourceFile("JavaMixin.java")
    val javaMixin = mixin("mixins.JavaMixin", Origin.JAVA, javaMixinFile, kotlinTarget)

    val javaToKotlin = assertNotNull(builder.mixin(javaMixin))

    assertEquals("example.KotlinObject", javaToKotlin.targetBinaryName)
    assertEquals("mixins.JavaMixin", javaToKotlin.mixinBinaryName)
    assertTrue(javaToKotlin.singleton)
    assertTrue(javaToKotlin.generateCompanion)
    assertOwners(javaToKotlin, javaMixinFile, kotlinTargetFile)

    val javaOwner = mixin("mixins.JavaOwned", Origin.JAVA, sourceFile("JavaOwned.java"), javaTarget)
    assertNull(builder.mixin(javaOwner))
    val libraryMixin =
      mixin("mixins.LibraryMixin", Origin.KOTLIN_LIB, sourceFile("library.jar"), javaTarget)
    assertNull(builder.mixin(libraryMixin))
  }

  private fun assertOwners(model: JsonModel, mixin: KSFile, target: KSFile) {
    assertEquals(2, model.originatingFiles.size)
    assertSame(mixin, model.originatingFiles[0])
    assertSame(target, model.originatingFiles[1])
  }

  private fun mixin(
    name: String,
    origin: Origin,
    file: KSFile,
    target: KSClassDeclaration,
  ): KSClassDeclaration =
    declaration(
      name,
      origin,
      ClassKind.CLASS,
      file,
      sequenceOf(mixinAnnotation(target)),
    )

  private fun mixinAnnotation(target: KSClassDeclaration): KSAnnotation {
    val annotationDeclaration =
      declaration(JSON_MIXIN, Origin.KOTLIN_LIB, ClassKind.ANNOTATION_CLASS, null)
    val annotationType = type(annotationDeclaration)
    val reference =
      proxy(
        KSTypeReference::class.java,
        mapOf(
          "resolve" to annotationType,
          "getAnnotations" to emptySequence<KSAnnotation>(),
          "getModifiers" to emptySet<Modifier>(),
        ),
      )
    val targetArgument =
      proxy(
        KSValueArgument::class.java,
        mapOf(
          "getName" to name("target"),
          "getValue" to type(target),
          "isSpread" to false,
          "getAnnotations" to emptySequence<KSAnnotation>(),
        ),
      )
    return proxy(
      KSAnnotation::class.java,
      mapOf(
        "getAnnotationType" to reference,
        "getArguments" to listOf(targetArgument),
        "getDefaultArguments" to emptyList<KSValueArgument>(),
        "getShortName" to name("JsonMixin"),
        "getUseSiteTarget" to null,
      ),
    )
  }

  private fun type(declaration: KSClassDeclaration): KSType =
    proxy(
      KSType::class.java,
      mapOf(
        "getDeclaration" to declaration,
        "getNullability" to Nullability.NOT_NULL,
        "getArguments" to emptyList<Any>(),
        "getAnnotations" to emptySequence<KSAnnotation>(),
        "isError" to false,
        "isMarkedNullable" to false,
      ),
    )

  private fun declaration(
    binaryName: String,
    origin: Origin,
    kind: ClassKind,
    file: KSFile?,
    annotations: Sequence<KSAnnotation> = emptySequence(),
  ): KSClassDeclaration {
    val packageName = binaryName.substringBeforeLast('.', "")
    val simpleName = binaryName.substringAfterLast('.')
    return proxy(
      KSClassDeclaration::class.java,
      mapOf(
        "getOrigin" to origin,
        "getContainingFile" to file,
        "getAnnotations" to annotations,
        "getQualifiedName" to name(binaryName),
        "getSimpleName" to name(simpleName),
        "getPackageName" to name(packageName),
        "getParentDeclaration" to null,
        "getModifiers" to setOf(Modifier.PUBLIC),
        "getDeclarations" to emptySequence<Any>(),
        "getTypeParameters" to emptyList<Any>(),
        "getClassKind" to kind,
        "getPrimaryConstructor" to null,
        "getSuperTypes" to emptySequence<Any>(),
        "getSealedSubclasses" to emptySequence<Any>(),
        "getAllFunctions" to emptySequence<Any>(),
        "getAllProperties" to emptySequence<Any>(),
        "isCompanionObject" to false,
      ),
    )
  }

  private fun resolver(): Resolver =
    proxy(
      Resolver::class.java,
      mapOf("effectiveJavaModifiers" to setOf(Modifier.PUBLIC)),
    )

  private fun name(value: String): KSName =
    proxy(
      KSName::class.java,
      mapOf(
        "asString" to value,
        "getQualifier" to value.substringBeforeLast('.', ""),
        "getShortName" to value.substringAfterLast('.'),
      ),
    )

  private fun <T> proxy(type: Class<T>, values: Map<String, Any?>): T {
    val instance =
      Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
        when (method.name) {
          "equals" -> proxy === arguments?.singleOrNull()
          "hashCode" -> System.identityHashCode(proxy)
          "toString" -> "${type.simpleName}${values["getQualifiedName"] ?: ""}"
          else -> if (values.containsKey(method.name)) values[method.name] else defaultValue(method)
        }
      }
    @Suppress("UNCHECKED_CAST") return instance as T
  }

  private fun defaultValue(method: Method): Any? =
    when {
      method.returnType == Boolean::class.javaPrimitiveType -> false
      method.returnType == Int::class.javaPrimitiveType -> 0
      method.returnType == Long::class.javaPrimitiveType -> 0L
      method.returnType.isArray ->
        java.lang.reflect.Array.newInstance(method.returnType.componentType, 0)
      Sequence::class.java.isAssignableFrom(method.returnType) -> emptySequence<Any>()
      List::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
      Set::class.java.isAssignableFrom(method.returnType) -> emptySet<Any>()
      Map::class.java.isAssignableFrom(method.returnType) -> emptyMap<Any, Any>()
      else -> null
    }
}
