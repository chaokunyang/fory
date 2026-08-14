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

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class R8RulesWriterTest {
  @Test
  fun emitsExactOrdinaryRules() {
    val creator =
      JsonCreator(
        parameterNames = listOf("id", "name"),
        parameterTypes = listOf(JvmType("J"), JvmType("Ljava/lang/String;")),
        optional = booleanArrayOf(false, true),
        invocationOwner = "example.Profile",
        invocationName = "<init>",
        invocationDescriptor = "(JLjava/lang/String;)V",
        defaultDescriptor = "(JLjava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
      )
    val model =
      model(
        members =
          listOf(
            JvmMember(MemberKind.FIELD, "example.Profile", false, "id", "J", writable = true),
            JvmMember(
              MemberKind.GETTER,
              "example.Profile",
              false,
              "getName",
              "()Ljava/lang/String;",
            ),
          ),
        anySetter =
          JvmAnySetter(
            "example.Profile",
            false,
            "putExtra",
            "(Ljava/lang/String;Ljava/lang/Object;)V",
          ),
        validators = listOf(JvmValidator("example.Profile", false, "validate")),
        creator = creator,
        retainedAnnotations = setOf("kotlin.Metadata", "org.apache.fory.json.annotation.JsonType"),
        retainedTypes = setOf("example.ProfileSubtype"),
      )

    val rules = R8RulesWriter.write(model)

    assertEquals(
      "META-INF/proguard/fory-json-example.Profile.pro",
      R8RulesWriter.resourcePath(model)
    )
    assertContains(rules, "-keepattributes Signature,RuntimeVisibleAnnotations")
    assertContains(rules, "-keep,allowoptimization class example.Profile")
    assertContains(rules, "long id;")
    assertContains(rules, "java.lang.String getName();")
    assertContains(rules, "void putExtra(java.lang.String,java.lang.Object);")
    assertContains(rules, "void validate();")
    assertContains(rules, "<init>(long,java.lang.String);")
    assertContains(
      rules,
      "<init>(long,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);",
    )
    assertContains(
      rules,
      "-keep,allowoptimization,allowobfuscation @interface kotlin.Metadata",
    )
    assertContains(rules, "-keep,allowoptimization class example.ProfileSubtype")
    assertContains(
      rules,
      "public org.apache.fory.json.meta.JsonAnySetterAccessor anySetterAccessor();",
    )
    assertContains(rules, "public java.lang.reflect.Method[] validatorMethods();")
    assertContains(rules, "public void invokeValidators(java.lang.Object);")
    assertContains(
      rules,
      "public static void setAny(example.Profile,java.lang.String,java.lang.Object);",
    )
    assertContains(rules, "public static void validate_0(example.Profile);")
    assertContains(rules, "public static example.Profile createDefault(long,java.lang.String,int);")
    assertFalse(rules.contains('*'), rules)
    assertFalse(rules.contains("example.**"), rules)
  }

  @Test
  fun emitsExactSealedRulesOnly() {
    val model =
      model(
        target = "example.Shape",
        generateCompanion = false,
        retainedAnnotations =
          setOf("kotlin.Metadata", "org.apache.fory.json.annotation.JsonSubTypes"),
        retainedTypes = setOf("example.Circle", "example.Square"),
      )

    val rules = R8RulesWriter.write(model)

    assertContains(rules, "-keep,allowoptimization class example.Shape")
    assertContains(rules, "-keep,allowoptimization class example.Circle")
    assertContains(rules, "-keep,allowoptimization class example.Square")
    assertFalse(rules.contains(model.companionBinaryName), rules)
    assertFalse(rules.contains(model.operationBinaryName), rules)
    assertFalse(rules.contains("-keepclassmembers"), rules)
    assertFalse(rules.contains('*'), rules)
  }

  @Test
  fun emitsExactValueOperations() {
    val model =
      model(
        target = "example.UserId",
        valueClass =
          ValueClassOperations(
            layers =
              listOf(
                ValueClassLayer(
                  ownerBinaryName = "example.UserId",
                  carrierType = JvmType("J"),
                )
              ),
            terminalType = JvmType("J"),
          ),
        retainedAnnotations = setOf("kotlin.Metadata"),
      )

    val rules = R8RulesWriter.write(model)

    assertContains(rules, "long constructor-impl(long);")
    assertContains(rules, "example.UserId box-impl(long);")
    assertContains(rules, "long unbox-impl();")
    assertContains(rules, "public static long valueConstruct_0(long);")
    assertContains(rules, "public static example.UserId valueBox_0(long);")
    assertContains(rules, "public static long valueUnbox_0(example.UserId);")
    assertFalse(rules.contains('*'), rules)
  }

  @Test
  fun mixinResourceOwnsPairIdentity() {
    val direct = model()
    val mixin =
      model(
        companionSimpleName = "ProfileMixin_ForyJsonMixin_example_Profile_ForyJsonCodec",
        mixinBinaryName = "example.ProfileMixin",
      )

    assertNotEquals(R8RulesWriter.resourcePath(direct), R8RulesWriter.resourcePath(mixin))
    assertContains(R8RulesWriter.resourcePath(mixin), "ProfileMixin")
  }

  private fun model(
    target: String = "example.Profile",
    companionSimpleName: String = "${target.substringAfterLast('.')}_ForyJsonCodec",
    generateCompanion: Boolean = true,
    members: List<JvmMember> = emptyList(),
    anySetter: JvmAnySetter? = null,
    validators: List<JvmValidator> = emptyList(),
    creator: JsonCreator? = null,
    valueClass: ValueClassOperations? = null,
    mixinBinaryName: String? = null,
    retainedAnnotations: Set<String> = emptySet(),
    retainedTypes: Set<String> = emptySet(),
  ): JsonModel =
    JsonModel(
      packageName = "example",
      targetBinaryName = target,
      targetSourceName = target.replace('$', '.'),
      companionSimpleName = companionSimpleName,
      operationSimpleName = companionSimpleName + "_Operations",
      generateCompanion = generateCompanion,
      members = members,
      anySetter = anySetter,
      validators = validators,
      creator = creator,
      singleton = false,
      valueClass = valueClass,
      mixinBinaryName = mixinBinaryName,
      originatingFiles = emptyList(),
      retainedAnnotations = retainedAnnotations,
      retainedTypes = retainedTypes,
      mixinMembers = emptyList(),
    )
}
