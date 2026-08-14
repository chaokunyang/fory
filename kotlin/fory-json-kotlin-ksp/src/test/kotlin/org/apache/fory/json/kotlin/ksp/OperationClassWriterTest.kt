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

import java.io.ByteArrayInputStream
import java.lang.reflect.Modifier
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.codehaus.janino.util.ClassFile

internal class OperationFixture(
  @JvmField var number: Int,
  var name: String,
) {
  val extras: MutableMap<String, Any?> = linkedMapOf()
  var validated: Boolean = false

  fun putExtra(name: String, value: Any?) {
    extras[name] = value
  }

  fun validate() {
    validated = true
  }
}

internal data class DefaultOperationFixture(
  val id: Long,
  val name: String = "default-name",
  val enabled: Boolean = true,
)

internal object SingletonOperationFixture

@JvmInline internal value class ValueOperationFixture(val value: Long)

internal class FactoryOperationFixture private constructor(val id: Long, val name: String) {
  companion object {
    @JvmStatic
    fun create(id: Long, name: String): FactoryOperationFixture = FactoryOperationFixture(id, name)
  }
}

internal interface OperationView {
  val label: String
}

internal class OperationViewFixture(override val label: String) : OperationView

class OperationClassWriterTest {
  @Test
  fun emitsDirectMemberOperations() {
    val model =
      model(
        target = OperationFixture::class.java,
        operationName = "OperationFixture_ForyJsonOperations",
        members =
          listOf(
            JvmMember(
              kind = MemberKind.FIELD,
              ownerBinaryName = OperationFixture::class.java.name,
              ownerInterface = false,
              name = "number",
              descriptor = "I",
              writable = true,
            ),
            JvmMember(
              kind = MemberKind.GETTER,
              ownerBinaryName = OperationFixture::class.java.name,
              ownerInterface = false,
              name = "getName",
              descriptor = "()Ljava/lang/String;",
            ),
            JvmMember(
              kind = MemberKind.SETTER,
              ownerBinaryName = OperationFixture::class.java.name,
              ownerInterface = false,
              name = "setName",
              descriptor = "(Ljava/lang/String;)V",
            ),
          ),
        anySetter =
          JvmAnySetter(
            ownerBinaryName = OperationFixture::class.java.name,
            ownerInterface = false,
            name = "putExtra",
            descriptor = "(Ljava/lang/String;Ljava/lang/Object;)V",
          ),
        validators =
          listOf(
            JvmValidator(
              ownerBinaryName = OperationFixture::class.java.name,
              ownerInterface = false,
              name = "validate",
            )
          ),
      )

    val bytes = OperationClassWriter.write(model)
    val operations = define(model.operationBinaryName, bytes)
    val fixture = OperationFixture(7, "before")

    assertEquals(52, majorVersion(bytes))
    assertTrue(Modifier.isFinal(operations.modifiers))
    assertTrue(
      operations.declaredMethods.all {
        Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && !it.isSynthetic
      }
    )
    assertEquals(
      7,
      operations.getMethod("get_0", OperationFixture::class.java).invoke(null, fixture)
    )
    operations
      .getMethod("set_0", OperationFixture::class.java, Int::class.javaPrimitiveType)
      .invoke(null, fixture, 19)
    assertEquals(19, fixture.number)
    assertEquals(
      "before",
      operations.getMethod("get_1", OperationFixture::class.java).invoke(null, fixture),
    )
    operations
      .getMethod("set_2", OperationFixture::class.java, String::class.java)
      .invoke(null, fixture, "after")
    assertEquals("after", fixture.name)
    operations
      .getMethod("setAny", OperationFixture::class.java, String::class.java, Any::class.java)
      .invoke(null, fixture, "extra", 23L)
    assertEquals(23L, fixture.extras["extra"])
    operations.getMethod("validate_0", OperationFixture::class.java).invoke(null, fixture)
    assertTrue(fixture.validated)

    val classFile = ClassFile(ByteArrayInputStream(bytes))
    val references = memberReferences(classFile)
    assertTrue(references.contains("${internalName(OperationFixture::class.java)}.number:I"))
    assertTrue(
      references.contains(
        "${internalName(OperationFixture::class.java)}.getName:()Ljava/lang/String;"
      )
    )
    assertTrue(
      references.contains(
        "${internalName(OperationFixture::class.java)}.setName:(Ljava/lang/String;)V"
      )
    )
    assertFalse(utf8Constants(classFile).any { it.startsWith("java/lang/reflect/") })
    assertFalse(utf8Constants(classFile).any { it.startsWith("java/lang/Integer") })
  }

  @Test
  fun invokesFullAndDefaultConstructors() {
    val target = DefaultOperationFixture::class.java
    val marker = "Lkotlin/jvm/internal/DefaultConstructorMarker;"
    val parameters = listOf(JvmType("J"), JvmType("Ljava/lang/String;"), JvmType("Z"))
    val creator =
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
            listOf(JvmType("I"), JvmType(marker))
          ),
      )
    val model =
      model(
        target = target,
        operationName = "DefaultOperationFixture_ForyJsonOperations",
        creator = creator,
      )

    val bytes = OperationClassWriter.write(model)
    val operations = define(model.operationBinaryName, bytes)
    val full =
      operations
        .getMethod(
          "createFull",
          Long::class.javaPrimitiveType,
          String::class.java,
          Boolean::class.javaPrimitiveType,
        )
        .invoke(null, 31L, "explicit", false) as DefaultOperationFixture
    val defaults =
      operations
        .getMethod(
          "createDefault",
          Long::class.javaPrimitiveType,
          String::class.java,
          Boolean::class.javaPrimitiveType,
          Int::class.javaPrimitiveType,
        )
        .invoke(null, 47L, null, false, 0b110) as DefaultOperationFixture

    assertEquals(DefaultOperationFixture(31L, "explicit", false), full)
    assertEquals(DefaultOperationFixture(47L), defaults)
    val references = memberReferences(ClassFile(ByteArrayInputStream(bytes)))
    assertTrue(
      references.contains(
        "${internalName(target)}.<init>:(JLjava/lang/String;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V"
      )
    )
  }

  @Test
  fun emitsSingletonAndValueOperations() {
    val singletonModel =
      model(
        target = SingletonOperationFixture::class.java,
        operationName = "SingletonOperationFixture_ForyJsonOperations",
        singleton = true,
      )
    val singletonOperations =
      define(singletonModel.operationBinaryName, OperationClassWriter.write(singletonModel))
    assertSame(
      SingletonOperationFixture,
      singletonOperations.getMethod("instance").invoke(null),
    )

    val valueModel =
      model(
        target = ValueOperationFixture::class.java,
        operationName = "ValueOperationFixture_ForyJsonOperations",
        valueClass =
          ValueClassOperations(
            layers =
              listOf(
                ValueClassLayer(
                  ownerBinaryName = ValueOperationFixture::class.java.name,
                  carrierType = JvmType("J"),
                )
              ),
            terminalType = JvmType("J"),
          ),
      )
    val valueBytes = OperationClassWriter.write(valueModel)
    val valueOperations = define(valueModel.operationBinaryName, valueBytes)
    val constructed =
      valueOperations.getMethod("valueConstruct_0", Long::class.javaPrimitiveType).invoke(null, 59L)
    val boxed =
      valueOperations
        .getMethod("valueBox_0", Long::class.javaPrimitiveType)
        .invoke(null, constructed)
    val unboxed =
      valueOperations
        .getMethod("valueUnbox_0", ValueOperationFixture::class.java)
        .invoke(null, boxed)

    assertEquals(59L, constructed)
    assertEquals(ValueOperationFixture(59L), boxed)
    assertEquals(59L, unboxed)
    val references = memberReferences(ClassFile(ByteArrayInputStream(valueBytes)))
    assertTrue(
      references.contains(
        "${internalName(ValueOperationFixture::class.java)}.constructor-impl:(J)J"
      )
    )
    assertTrue(
      references.contains(
        "${internalName(ValueOperationFixture::class.java)}.box-impl:(J)L${internalName(ValueOperationFixture::class.java)};"
      )
    )
    assertTrue(
      references.contains("${internalName(ValueOperationFixture::class.java)}.unbox-impl:()J")
    )
  }

  @Test
  fun emitsFactoryAndInterfaceOperations() {
    val target = FactoryOperationFixture::class.java
    val parameters = listOf(JvmType("J"), JvmType("Ljava/lang/String;"))
    val factoryModel =
      model(
        target = target,
        operationName = "FactoryOperationFixture_ForyJsonOperations",
        creator =
          JsonCreator(
            parameterNames = listOf("id", "name"),
            parameterTypes = parameters,
            optional = booleanArrayOf(false, false),
            invocationOwner = target.name,
            invocationName = "create",
            invocationDescriptor = methodDescriptor(parameters, "L${internalName(target)};"),
            defaultDescriptor = null,
          ),
      )
    val factoryBytes = OperationClassWriter.write(factoryModel)
    val factoryOperations = define(factoryModel.operationBinaryName, factoryBytes)
    val created =
      factoryOperations
        .getMethod("createFull", Long::class.javaPrimitiveType, String::class.java)
        .invoke(null, 71L, "factory") as FactoryOperationFixture

    assertEquals(71L, created.id)
    assertEquals("factory", created.name)
    assertTrue(
      memberReferences(ClassFile(ByteArrayInputStream(factoryBytes)))
        .contains("${internalName(target)}.create:(JLjava/lang/String;)L${internalName(target)};")
    )

    val viewModel =
      model(
        target = OperationViewFixture::class.java,
        operationName = "OperationViewFixture_ForyJsonOperations",
        members =
          listOf(
            JvmMember(
              kind = MemberKind.GETTER,
              ownerBinaryName = OperationView::class.java.name,
              ownerInterface = true,
              name = "getLabel",
              descriptor = "()Ljava/lang/String;",
            )
          ),
      )
    val viewBytes = OperationClassWriter.write(viewModel)
    val viewOperations = define(viewModel.operationBinaryName, viewBytes)
    assertEquals(
      "interface",
      viewOperations
        .getMethod("get_0", OperationViewFixture::class.java)
        .invoke(null, OperationViewFixture("interface")),
    )
    assertTrue(
      memberReferences(ClassFile(ByteArrayInputStream(viewBytes)))
        .contains("${internalName(OperationView::class.java)}.getLabel:()Ljava/lang/String;")
    )
  }

  private fun model(
    target: Class<*>,
    operationName: String,
    members: List<JvmMember> = emptyList(),
    anySetter: JvmAnySetter? = null,
    validators: List<JvmValidator> = emptyList(),
    creator: JsonCreator? = null,
    singleton: Boolean = false,
    valueClass: ValueClassOperations? = null,
  ): JsonModel =
    JsonModel(
      packageName = target.packageName,
      targetBinaryName = target.name,
      targetSourceName = target.canonicalName,
      companionSimpleName = "UnusedCompanion",
      operationSimpleName = operationName,
      generateCompanion = true,
      members = members,
      anySetter = anySetter,
      validators = validators,
      creator = creator,
      singleton = singleton,
      valueClass = valueClass,
      mixinBinaryName = null,
      originatingFiles = emptyList(),
      retainedAnnotations = emptySet(),
      retainedTypes = emptySet(),
      mixinMembers = emptyList(),
    )

  private fun define(binaryName: String, bytes: ByteArray): Class<*> =
    ByteClassLoader(javaClass.classLoader).define(binaryName, bytes)

  private fun majorVersion(bytes: ByteArray): Int =
    (bytes[6].toInt() and 0xff shl 8) or (bytes[7].toInt() and 0xff)

  private fun internalName(type: Class<*>): String = type.name.replace('.', '/')

  private fun memberReferences(classFile: ClassFile): Set<String> = buildSet {
    for (index in 1 until classFile.constantPoolSize) {
      val constant = classFile.getConstantPoolInfo(index.toShort()) ?: continue
      when (constant) {
        is ClassFile.ConstantFieldrefInfo ->
          addReference(
            classFile,
            constant.getClassInfo(classFile).getName(classFile),
            constant.getNameAndType(classFile),
          )
        is ClassFile.ConstantMethodrefInfo ->
          addReference(
            classFile,
            constant.getClassInfo(classFile).getName(classFile),
            constant.getNameAndType(classFile),
          )
        is ClassFile.ConstantInterfaceMethodrefInfo ->
          addReference(
            classFile,
            constant.getClassInfo(classFile).getName(classFile),
            constant.getNameAndType(classFile),
          )
      }
    }
  }

  private fun MutableSet<String>.addReference(
    classFile: ClassFile,
    owner: String,
    nameAndType: ClassFile.ConstantNameAndTypeInfo,
  ) {
    add("$owner.${nameAndType.getName(classFile)}:${nameAndType.getDescriptor(classFile)}")
  }

  private fun utf8Constants(classFile: ClassFile): List<String> = buildList {
    for (index in 1 until classFile.constantPoolSize) {
      val constant = classFile.getConstantPoolInfo(index.toShort())
      if (constant is ClassFile.ConstantUtf8Info) add(constant.string)
    }
  }

  private class ByteClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun define(binaryName: String, bytes: ByteArray): Class<*> =
      defineClass(binaryName, bytes, 0, bytes.size)
  }
}
