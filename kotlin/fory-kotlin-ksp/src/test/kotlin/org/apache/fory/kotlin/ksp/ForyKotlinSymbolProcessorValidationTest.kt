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

package org.apache.fory.kotlin.ksp

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier
import org.testng.Assert.assertEquals
import org.testng.Assert.assertNull
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class ForyKotlinSymbolProcessorValidationTest {
  @Test
  fun rejectsUnsupportedSerializerTargets() {
    assertNull(unsupportedStructDeclarationDiagnostic(ClassKind.CLASS, emptySet()))

    val objectDiagnostic =
      unsupportedStructDeclarationDiagnostic(ClassKind.OBJECT, emptySet()).orEmpty()
    assertTrue(objectDiagnostic.contains("concrete class"))
    assertTrue(objectDiagnostic.contains("object declarations do not expose primary-constructor"))

    val interfaceDiagnostic =
      unsupportedStructDeclarationDiagnostic(ClassKind.INTERFACE, emptySet()).orEmpty()
    assertTrue(interfaceDiagnostic.contains("interfaces are not concrete serializer targets"))

    val abstractDiagnostic =
      unsupportedStructDeclarationDiagnostic(ClassKind.CLASS, setOf(Modifier.ABSTRACT)).orEmpty()
    assertTrue(abstractDiagnostic.contains("abstract declarations are polymorphic bases"))

    val sealedDiagnostic =
      unsupportedStructDeclarationDiagnostic(ClassKind.CLASS, setOf(Modifier.SEALED)).orEmpty()
    assertTrue(sealedDiagnostic.contains("sealed declarations are polymorphic bases"))
  }

  @Test
  fun rejectsNonPublicSerializerTargets() {
    assertNull(unsupportedStructVisibilityDiagnostic(emptySet()))
    assertTrue(unsupportedStructVisibilityDiagnostic(setOf(Modifier.PRIVATE))!!.contains("private"))
    assertTrue(
      unsupportedStructVisibilityDiagnostic(setOf(Modifier.INTERNAL))!!.contains("internal")
    )
  }

  @Test
  fun rejectsGenericSerializerTargets() {
    assertNull(unsupportedStructTypeParameterDiagnostic(0))
    assertTrue(unsupportedStructTypeParameterDiagnostic(1)!!.contains("generic @ForyStruct"))
  }

  @Test
  fun rejectsInaccessiblePrimaryConstructors() {
    assertNull(unsupportedPrimaryConstructorVisibilityDiagnostic(emptySet()))
    assertNull(unsupportedPrimaryConstructorVisibilityDiagnostic(setOf(Modifier.INTERNAL)))
    assertTrue(
      unsupportedPrimaryConstructorVisibilityDiagnostic(setOf(Modifier.PRIVATE))!!.contains(
        "primary constructor"
      )
    )
    assertTrue(
      unsupportedPrimaryConstructorVisibilityDiagnostic(setOf(Modifier.PROTECTED))!!.contains(
        "primary constructor"
      )
    )
  }

  @Test
  fun rejectsConstructorFieldCountOverflow() {
    assertNull(constructorFieldLimitDiagnostic(63))
    assertEquals(
      constructorFieldLimitDiagnostic(64),
      "Kotlin KSP xlang serializers currently support at most 63 constructor fields",
    )
  }

  @Test
  fun rejectsDefaultConstructorFieldCountOverflow() {
    assertNull(defaultConstructorFieldLimitDiagnostic(12))
    assertEquals(
      defaultConstructorFieldLimitDiagnostic(13),
      "Kotlin KSP xlang serializers currently support at most 12 defaulted constructor fields because Kotlin source generation must call constructors with omitted default arguments",
    )
  }

  @Test
  fun staticProviderNameIsDeterministicAndModuleSpecific() {
    val first =
      listOf(
        ProviderEntry("example", "example.User", "example.User__ForySerializer__"),
        ProviderEntry("example", "example.Team", "example.Team__ForySerializer__"),
      )
    val firstReordered = listOf(first[1], first[0])
    val second = listOf(ProviderEntry("other", "other.User", "other.User__ForySerializer__"))

    assertEquals(staticProviderName(first), staticProviderName(firstReordered))
    assertTrue(
      staticProviderName(first).startsWith("__ForyKotlinStaticGeneratedSerializerProvider_")
    )
    assertTrue(staticProviderName(first).endsWith("__"))
    assertTrue(staticProviderName(first) != staticProviderName(second))
  }

  @Test
  fun nullableUnsignedDenseArrayUsesDirectGeneratedPath() {
    val uintType =
      KotlinSourceTypeNode(
        rawClassExpression = "UInt::class.javaObjectType!!",
        kotlinTypeName = "kotlin.UInt",
        valueTypeName = "UInt",
        typeName = "kotlin.UInt",
        typeId = "Types.UINT32",
        nullable = false,
        trackingRef = false,
        primitive = false,
        unsigned = true,
      )
    val nullableUIntArray =
      KotlinSourceTypeNode(
        rawClassExpression = "UIntArray::class.java",
        kotlinTypeName = "kotlin.UIntArray",
        valueTypeName = "UIntArray?",
        typeName = "kotlin.UIntArray",
        typeId = "Types.UINT32_ARRAY",
        nullable = true,
        trackingRef = false,
        primitive = false,
        unsigned = true,
        componentType = uintType,
      )
    val source =
      KotlinSerializerSourceWriter(
          KotlinSourceStruct(
            packageName = "example",
            typeName = "NullableUIntArrayHolder",
            qualifiedTypeName = "example.NullableUIntArrayHolder",
            serializerName = "NullableUIntArrayHolder__ForySerializer__",
            fields =
              listOf(
                KotlinSourceField(
                  id = 0,
                  name = "values",
                  type = nullableUIntArray,
                  hasForyField = true,
                  foryFieldId = 1,
                  trackingRef = false,
                  dynamic = "AUTO",
                  arrayType = false,
                  hasDefault = false,
                  nullable = true,
                  propertyTypeName = "UIntArray?",
                )
              ),
            originatingFiles = emptyList(),
          )
        )
        .write()

    assertTrue(
      source.contains("KotlinXlangArrayEncoding.writeUIntArray(writeContext, nullableArray0)")
    )
    assertTrue(source.contains("KotlinXlangArrayEncoding.readUIntArray(readContext"))
    assertTrue(!source.contains("KotlinXlangArrayEncoding.toIntArray"))
  }

  @Test
  fun nullableUnsignedScalarUsesStableLocal() {
    val nullableUInt =
      KotlinSourceTypeNode(
        rawClassExpression = "UInt::class.javaObjectType!!",
        kotlinTypeName = "kotlin.UInt",
        valueTypeName = "UInt?",
        typeName = "kotlin.UInt",
        typeId = "Types.UINT32",
        nullable = true,
        trackingRef = false,
        primitive = false,
        unsigned = true,
      )
    val source =
      KotlinSerializerSourceWriter(
          KotlinSourceStruct(
            packageName = "example",
            typeName = "NullableUIntHolder",
            qualifiedTypeName = "example.NullableUIntHolder",
            serializerName = "NullableUIntHolder__ForySerializer__",
            fields =
              listOf(
                KotlinSourceField(
                  id = 0,
                  name = "value",
                  type = nullableUInt,
                  hasForyField = true,
                  foryFieldId = 1,
                  trackingRef = false,
                  dynamic = "AUTO",
                  arrayType = false,
                  hasDefault = false,
                  nullable = true,
                  propertyTypeName = "UInt?",
                )
              ),
            originatingFiles = emptyList(),
          )
        )
        .write()

    assertTrue(source.contains("val nullableValue0 = value.value"))
    assertTrue(source.contains("buffer.writeInt32(nullableValue0.toInt())"))
    assertTrue(!source.contains("value.value.toInt()"))
  }
}
