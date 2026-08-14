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

import java.io.ByteArrayOutputStream
import org.codehaus.janino.MethodDescriptor
import org.codehaus.janino.util.ClassFile

/** Emits the straight-line JVM operations that source languages cannot name exactly. */
internal object OperationClassWriter {
  private const val ACC_PUBLIC = 0x0001
  private const val ACC_PRIVATE = 0x0002
  private const val ACC_STATIC = 0x0008
  private const val ACC_FINAL = 0x0010
  private const val ACC_SUPER = 0x0020

  fun write(model: JsonModel): ByteArray {
    val classFile =
      ClassFile(
        (ACC_PUBLIC or ACC_FINAL or ACC_SUPER).toShort(),
        classDescriptor(model.operationBinaryName),
        "Ljava/lang/Object;",
        emptyArray(),
      )
    classFile.setVersion(ClassFile.MAJOR_VERSION_JDK_1_8, ClassFile.MINOR_VERSION_JDK_1_8)
    addConstructor(classFile)
    model.members.forEachIndexed { index, member -> addMember(classFile, model, index, member) }
    model.anySetter?.let { addAnySetter(classFile, model, it) }
    model.validators.forEachIndexed { index, validator ->
      addValidator(classFile, model, index, validator)
    }
    model.creator?.let { addCreator(classFile, model, it) }
    if (model.singleton) addSingleton(classFile, model)
    model.valueClass?.let { addValueClassOperations(classFile, model, it) }
    return classFile.toByteArray()
  }

  private fun addConstructor(classFile: ClassFile) {
    val code = ByteArrayOutputStream()
    code.write(ALOAD_0)
    invoke(
      code,
      classFile.addConstantMethodrefInfo("Ljava/lang/Object;", "<init>", "()V"),
      INVOKESPECIAL
    )
    code.write(RETURN)
    addMethod(classFile, ACC_PRIVATE, "<init>", "()V", code.toByteArray(), 1, 1)
  }

  private fun addMember(classFile: ClassFile, model: JsonModel, index: Int, member: JvmMember) {
    val receiver = JvmType(classDescriptor(model.targetBinaryName))
    val owner = classDescriptor(member.ownerBinaryName)
    when (member.kind) {
      MemberKind.FIELD -> {
        val valueType = JvmType(member.descriptor)
        val readCode = ByteArrayOutputStream()
        writeLoad(readCode, receiver, 0)
        invoke(
          readCode,
          classFile.addConstantFieldrefInfo(owner, member.name, valueType.descriptor),
          GETFIELD,
        )
        readCode.write(returnOpcode(valueType))
        addMethod(
          classFile,
          ACC_PUBLIC or ACC_STATIC,
          "get_$index",
          methodDescriptor(listOf(receiver), valueType.descriptor),
          readCode.toByteArray(),
          maxOf(1, valueType.slots),
          1,
        )
        if (member.writable) {
          val writeCode = ByteArrayOutputStream()
          writeLoad(writeCode, receiver, 0)
          writeLoad(writeCode, valueType, 1)
          invoke(
            writeCode,
            classFile.addConstantFieldrefInfo(owner, member.name, valueType.descriptor),
            PUTFIELD,
          )
          writeCode.write(RETURN)
          addMethod(
            classFile,
            ACC_PUBLIC or ACC_STATIC,
            "set_$index",
            methodDescriptor(listOf(receiver, valueType), "V"),
            writeCode.toByteArray(),
            1 + valueType.slots,
            1 + valueType.slots,
          )
        }
      }
      MemberKind.GETTER -> {
        val method = parseMethodDescriptor(member.descriptor)
        require(method.parameters.isEmpty() && method.result != "V") {
          "Invalid generated getter ${member.ownerBinaryName}.${member.name}${member.descriptor}"
        }
        val result = JvmType(method.result)
        val code = ByteArrayOutputStream()
        writeLoad(code, receiver, 0)
        invokeMember(code, classFile, owner, member, 1)
        code.write(returnOpcode(result))
        addMethod(
          classFile,
          ACC_PUBLIC or ACC_STATIC,
          "get_$index",
          methodDescriptor(listOf(receiver), result.descriptor),
          code.toByteArray(),
          maxOf(1, result.slots),
          1,
        )
      }
      MemberKind.SETTER -> {
        val method = parseMethodDescriptor(member.descriptor)
        require(method.parameters.size == 1 && method.result == "V") {
          "Invalid generated setter ${member.ownerBinaryName}.${member.name}${member.descriptor}"
        }
        val valueType = method.parameters.single()
        val code = ByteArrayOutputStream()
        writeLoad(code, receiver, 0)
        writeLoad(code, valueType, 1)
        invokeMember(code, classFile, owner, member, 1 + valueType.slots)
        code.write(RETURN)
        addMethod(
          classFile,
          ACC_PUBLIC or ACC_STATIC,
          "set_$index",
          methodDescriptor(listOf(receiver, valueType), "V"),
          code.toByteArray(),
          1 + valueType.slots,
          1 + valueType.slots,
        )
      }
    }
  }

  private fun addCreator(classFile: ClassFile, model: JsonModel, creator: JsonCreator) {
    val target = classDescriptor(model.targetBinaryName)
    if (creator.factory) {
      addFactoryBridge(classFile, target, creator)
    } else {
      addConstructorBridge(
        classFile,
        target,
        "createFull",
        creator.parameterTypes,
        creator.invocationDescriptor,
      )
    }
    val defaultDescriptor = creator.defaultDescriptor ?: return
    val bridgeParameters = creator.parameterTypes + List(creator.maskCount) { JvmType("I") }
    addConstructorBridge(classFile, target, "createDefault", bridgeParameters, defaultDescriptor)
  }

  private fun addFactoryBridge(
    classFile: ClassFile,
    target: String,
    creator: JsonCreator,
  ) {
    require(creator.defaultDescriptor == null) {
      "Static creator factories cannot use constructor masks"
    }
    val invocation = parseMethodDescriptor(creator.invocationDescriptor)
    require(invocation.parameters == creator.parameterTypes && invocation.result == target) {
      "Invalid generated creator factory ${creator.invocationName}${creator.invocationDescriptor}"
    }
    val code = ByteArrayOutputStream()
    var local = 0
    for (type in creator.parameterTypes) {
      writeLoad(code, type, local)
      local += type.slots
    }
    invoke(
      code,
      classFile.addConstantMethodrefInfo(
        classDescriptor(creator.invocationOwner),
        creator.invocationName,
        creator.invocationDescriptor,
      ),
      INVOKESTATIC,
    )
    code.write(ARETURN)
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      "createFull",
      methodDescriptor(creator.parameterTypes, target),
      code.toByteArray(),
      maxOf(local, 1),
      local,
    )
  }

  private fun addConstructorBridge(
    classFile: ClassFile,
    target: String,
    name: String,
    bridgeParameters: List<JvmType>,
    invocationDescriptor: String,
  ) {
    val invocation = parseMethodDescriptor(invocationDescriptor)
    require(invocation.result == "V") { "Constructor descriptor must return void" }
    require(
      invocation.parameters.size == bridgeParameters.size ||
        invocation.parameters.size == bridgeParameters.size + 1 &&
          invocation.parameters.last().descriptor == DEFAULT_MARKER,
    ) {
      "Invalid Kotlin constructor bridge shape $invocationDescriptor"
    }
    for (index in bridgeParameters.indices) {
      require(invocation.parameters[index] == bridgeParameters[index]) {
        "Kotlin constructor bridge carrier mismatch at $index"
      }
    }
    val code = ByteArrayOutputStream()
    invoke(code, classFile.addConstantClassInfo(target), NEW)
    code.write(DUP)
    var local = 0
    for (type in bridgeParameters) {
      writeLoad(code, type, local)
      local += type.slots
    }
    if (invocation.parameters.size != bridgeParameters.size) code.write(ACONST_NULL)
    invoke(
      code,
      classFile.addConstantMethodrefInfo(target, "<init>", invocationDescriptor),
      INVOKESPECIAL,
    )
    code.write(ARETURN)
    val parameterSlots = bridgeParameters.sumOf { it.slots }
    val markerSlots = if (invocation.parameters.size == bridgeParameters.size) 0 else 1
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      name,
      methodDescriptor(bridgeParameters, target),
      code.toByteArray(),
      parameterSlots + markerSlots + 2,
      parameterSlots,
    )
  }

  private fun addSingleton(classFile: ClassFile, model: JsonModel) {
    val target = classDescriptor(model.targetBinaryName)
    val code = ByteArrayOutputStream()
    invoke(code, classFile.addConstantFieldrefInfo(target, "INSTANCE", target), GETSTATIC)
    code.write(ARETURN)
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      "instance",
      methodDescriptor(emptyList(), target),
      code.toByteArray(),
      1,
      0,
    )
  }

  private fun addAnySetter(
    classFile: ClassFile,
    model: JsonModel,
    setter: JvmAnySetter,
  ) {
    val receiver = JvmType(classDescriptor(model.targetBinaryName))
    val method = parseMethodDescriptor(setter.descriptor)
    require(
      method.parameters.size == 2 &&
        method.parameters[0].descriptor == "Ljava/lang/String;" &&
        method.result == "V"
    ) {
      "Invalid generated JSON Any setter ${setter.ownerBinaryName}.${setter.name}${setter.descriptor}"
    }
    val code = ByteArrayOutputStream()
    writeLoad(code, receiver, 0)
    writeLoad(code, method.parameters[0], 1)
    writeLoad(code, method.parameters[1], 2)
    invokeMethod(
      code,
      classFile,
      classDescriptor(setter.ownerBinaryName),
      setter.ownerInterface,
      setter.name,
      setter.descriptor,
      2 + method.parameters[1].slots,
    )
    code.write(RETURN)
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      "setAny",
      methodDescriptor(listOf(receiver) + method.parameters, "V"),
      code.toByteArray(),
      2 + method.parameters[1].slots,
      2 + method.parameters[1].slots,
    )
  }

  private fun addValidator(
    classFile: ClassFile,
    model: JsonModel,
    index: Int,
    validator: JvmValidator,
  ) {
    val receiver = JvmType(classDescriptor(model.targetBinaryName))
    val code = ByteArrayOutputStream()
    writeLoad(code, receiver, 0)
    invokeMethod(
      code,
      classFile,
      classDescriptor(validator.ownerBinaryName),
      validator.ownerInterface,
      validator.name,
      "()V",
      1,
    )
    code.write(RETURN)
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      "validate_$index",
      methodDescriptor(listOf(receiver), "V"),
      code.toByteArray(),
      1,
      1,
    )
  }

  private fun addValueClassOperations(
    classFile: ClassFile,
    model: JsonModel,
    operations: ValueClassOperations,
  ) {
    operations.layers.forEachIndexed { index, layer ->
      val owner = classDescriptor(layer.ownerBinaryName)
      addStaticForward(
        classFile,
        owner,
        "valueConstruct_$index",
        layer.constructorName,
        listOf(layer.carrierType),
        layer.carrierType.descriptor,
      )
      addStaticForward(
        classFile,
        owner,
        "valueBox_$index",
        layer.boxName,
        listOf(layer.carrierType),
        owner,
      )
      val code = ByteArrayOutputStream()
      writeLoad(code, JvmType(owner), 0)
      invoke(
        code,
        classFile.addConstantMethodrefInfo(
          owner,
          layer.unboxName,
          methodDescriptor(emptyList(), layer.carrierType.descriptor),
        ),
        INVOKEVIRTUAL,
      )
      code.write(returnOpcode(layer.carrierType))
      addMethod(
        classFile,
        ACC_PUBLIC or ACC_STATIC,
        "valueUnbox_$index",
        methodDescriptor(listOf(JvmType(owner)), layer.carrierType.descriptor),
        code.toByteArray(),
        maxOf(1, layer.carrierType.slots),
        1,
      )
    }
  }

  private fun addStaticForward(
    classFile: ClassFile,
    owner: String,
    bridgeName: String,
    targetName: String,
    parameters: List<JvmType>,
    result: String,
  ) {
    val code = ByteArrayOutputStream()
    var local = 0
    for (type in parameters) {
      writeLoad(code, type, local)
      local += type.slots
    }
    invoke(
      code,
      classFile.addConstantMethodrefInfo(owner, targetName, methodDescriptor(parameters, result)),
      INVOKESTATIC,
    )
    code.write(returnOpcode(JvmType(result)))
    addMethod(
      classFile,
      ACC_PUBLIC or ACC_STATIC,
      bridgeName,
      methodDescriptor(parameters, result),
      code.toByteArray(),
      maxOf(local, JvmType(result).slots),
      local,
    )
  }

  private fun invokeMember(
    code: ByteArrayOutputStream,
    classFile: ClassFile,
    owner: String,
    member: JvmMember,
    argumentSlots: Int,
  ) {
    invokeMethod(
      code,
      classFile,
      owner,
      member.ownerInterface,
      member.name,
      member.descriptor,
      argumentSlots,
    )
  }

  private fun invokeMethod(
    code: ByteArrayOutputStream,
    classFile: ClassFile,
    owner: String,
    ownerInterface: Boolean,
    name: String,
    descriptor: String,
    argumentSlots: Int,
  ) {
    if (ownerInterface) {
      invoke(
        code,
        classFile.addConstantInterfaceMethodrefInfo(owner, name, descriptor),
        INVOKEINTERFACE,
      )
      code.write(argumentSlots)
      code.write(0)
    } else {
      invoke(
        code,
        classFile.addConstantMethodrefInfo(owner, name, descriptor),
        INVOKEVIRTUAL,
      )
    }
  }

  private fun addMethod(
    classFile: ClassFile,
    access: Int,
    name: String,
    descriptor: String,
    code: ByteArray,
    maxStack: Int,
    maxLocals: Int,
  ) {
    require(maxStack <= 65535 && maxLocals <= 255) { "Generated JVM method exceeds limits" }
    val method = classFile.addMethodInfo(access.toShort(), name, MethodDescriptor(descriptor))
    method.addAttribute(
      ClassFile.CodeAttribute(
        classFile.addConstantUtf8Info("Code"),
        maxStack.toShort(),
        maxLocals.toShort(),
        code,
        emptyArray(),
        emptyArray(),
      )
    )
  }

  private fun writeLoad(code: ByteArrayOutputStream, type: JvmType, index: Int) {
    require(index <= 255) { "Generated JVM local index exceeds 255" }
    val opcode =
      when (type.descriptor[0]) {
        'Z',
        'B',
        'S',
        'I',
        'C' -> ILOAD
        'J' -> LLOAD
        'F' -> FLOAD
        'D' -> DLOAD
        else -> ALOAD
      }
    val compact =
      when (opcode) {
        ILOAD -> ILOAD_0
        LLOAD -> LLOAD_0
        FLOAD -> FLOAD_0
        DLOAD -> DLOAD_0
        else -> ALOAD_0
      }
    if (index <= 3) {
      code.write(compact + index)
    } else {
      code.write(opcode)
      code.write(index)
    }
  }

  private fun returnOpcode(type: JvmType): Int =
    when (type.descriptor[0]) {
      'Z',
      'B',
      'S',
      'I',
      'C' -> IRETURN
      'J' -> LRETURN
      'F' -> FRETURN
      'D' -> DRETURN
      else -> ARETURN
    }

  private fun invoke(
    code: ByteArrayOutputStream,
    constantPoolIndex: Short,
    opcode: Int,
  ) {
    code.write(opcode)
    code.write(constantPoolIndex.toInt() ushr 8)
    code.write(constantPoolIndex.toInt())
  }

  private fun classDescriptor(binaryName: String): String =
    if (binaryName.startsWith('L') && binaryName.endsWith(';')) binaryName
    else "L${binaryName.replace('.', '/')};"

  private const val DEFAULT_MARKER = "Lkotlin/jvm/internal/DefaultConstructorMarker;"
  private const val ACONST_NULL = 0x01
  private const val ILOAD = 0x15
  private const val LLOAD = 0x16
  private const val FLOAD = 0x17
  private const val DLOAD = 0x18
  private const val ALOAD = 0x19
  private const val ILOAD_0 = 0x1a
  private const val LLOAD_0 = 0x1e
  private const val FLOAD_0 = 0x22
  private const val DLOAD_0 = 0x26
  private const val ALOAD_0 = 0x2a
  private const val DUP = 0x59
  private const val IRETURN = 0xac
  private const val LRETURN = 0xad
  private const val FRETURN = 0xae
  private const val DRETURN = 0xaf
  private const val ARETURN = 0xb0
  private const val RETURN = 0xb1
  private const val GETSTATIC = 0xb2
  private const val GETFIELD = 0xb4
  private const val PUTFIELD = 0xb5
  private const val INVOKEVIRTUAL = 0xb6
  private const val INVOKESPECIAL = 0xb7
  private const val INVOKESTATIC = 0xb8
  private const val INVOKEINTERFACE = 0xb9
  private const val NEW = 0xbb
}
