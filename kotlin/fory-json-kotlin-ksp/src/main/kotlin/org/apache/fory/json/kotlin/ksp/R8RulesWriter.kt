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

/** Emits one exact consumer-rule resource for one source-owned JSON request. */
internal object R8RulesWriter {
  fun resourcePath(model: JsonModel): String =
    model.mixinBinaryName?.let { "META-INF/proguard/fory-json-mixin-$it.pro" }
      ?: "META-INF/proguard/fory-json-${model.targetBinaryName}.pro"

  fun write(model: JsonModel): String =
    buildString(4096) {
      append("-keepattributes Signature,RuntimeVisibleAnnotations\n")
      append("-keepattributes RuntimeVisibleParameterAnnotations\n")
      append("-keepattributes AnnotationDefault,MethodParameters\n")
      if (model.targetBinaryName.indexOf('$') >= 0 || model.companionBinaryName.indexOf('$') >= 0) {
        append("-keepattributes InnerClasses,EnclosingMethod\n")
      }
      append('\n')
      memberRules(model).forEach { (owner, members) ->
        val preserveName = owner == model.targetBinaryName || owner == model.mixinBinaryName
        append("-keep,allowoptimization")
        if (!preserveName) append(",allowobfuscation")
        append(" class $owner\n")
        if (members.isNotEmpty()) {
          append("-keepclassmembers,allowoptimization class $owner {\n")
          members.forEach { append("  $it\n") }
          append("}\n")
        }
        append('\n')
      }
      model.retainedAnnotations.sorted().forEach { annotation ->
        append("-keep,allowoptimization,allowobfuscation @interface $annotation\n")
      }
      model.retainedTypes.sorted().forEach { type ->
        append("-keep,allowoptimization class $type\n")
      }
      if (!model.generateCompanion) return@buildString
      append('\n')
      append("-keep,allowoptimization class ${model.companionBinaryName} {\n")
      companionMembers(model).forEach { append("  $it\n") }
      append("}\n\n")
      append("-keep,allowoptimization class ${model.operationBinaryName} {\n")
      operationMembers(model).forEach { append("  $it\n") }
      append("}\n")
    }

  private fun memberRules(model: JsonModel): Map<String, List<String>> {
    val result = linkedMapOf<String, MutableSet<String>>()
    fun add(owner: String, declaration: String) {
      result.getOrPut(owner, ::linkedSetOf).add(declaration)
    }
    result[model.targetBinaryName] = linkedSetOf()
    model.mixinBinaryName?.let { mixin ->
      result[mixin] = model.mixinMembers.toCollection(linkedSetOf())
    }
    model.members.forEach { member ->
      when (member.kind) {
        MemberKind.FIELD ->
          add(member.ownerBinaryName, "${JvmType(member.descriptor).sourceName} ${member.name};")
        MemberKind.GETTER,
        MemberKind.SETTER -> add(member.ownerBinaryName, methodRule(member.name, member.descriptor))
      }
    }
    model.anySetter?.let { add(it.ownerBinaryName, methodRule(it.name, it.descriptor)) }
    model.validators.forEach { add(it.ownerBinaryName, "void ${it.name}();") }
    model.creator?.let { creator ->
      if (creator.factory) {
        add(
          creator.invocationOwner,
          methodRule(creator.invocationName, creator.invocationDescriptor)
        )
      } else {
        add(creator.invocationOwner, constructorRule(creator.invocationDescriptor))
        creator.defaultDescriptor?.let { add(creator.invocationOwner, constructorRule(it)) }
      }
    }
    if (model.singleton) {
      add(model.targetBinaryName, "public static final ${model.targetSourceName} INSTANCE;")
    }
    model.valueClass?.layers?.forEach { layer ->
      val ownerType = sourceName(layer.ownerBinaryName)
      add(
        layer.ownerBinaryName,
        "${layer.carrierType.sourceName} ${layer.constructorName}(${layer.carrierType.sourceName});",
      )
      add(
        layer.ownerBinaryName,
        "$ownerType ${layer.boxName}(${layer.carrierType.sourceName});",
      )
      add(layer.ownerBinaryName, "${layer.carrierType.sourceName} ${layer.unboxName}();")
    }
    return result.mapValues { (_, members) -> members.sorted() }
  }

  private fun companionMembers(model: JsonModel): List<String> = buildList {
    add("public <init>();")
    add("public java.lang.Class type();")
    if (model.valueClass == null) {
      add("public org.apache.fory.json.meta.JsonFieldAccessor[] fieldAccessors();")
    } else {
      add(
        "protected org.apache.fory.json.kotlin.KotlinValueClassShape valueClassShape(" +
          "org.apache.fory.reflect.TypeRef);",
      )
      add(
        "public org.apache.fory.json.kotlin.KotlinUnboxedValueClassOperations unboxedOperations();"
      )
      val terminal = model.valueClass.terminalType
      val parameter = if (terminal.primitive) terminal.sourceName else "java.lang.Object"
      val suffix = valueOperationName(terminal)
      add(
        "public ${model.targetSourceName} construct$suffix(" +
          "org.apache.fory.json.reader.JsonReader,$parameter);",
      )
      add("public ${model.targetSourceName} construct${suffix}Uncharged($parameter);")
      add("public $parameter unbox$suffix(${model.targetSourceName});")
    }
    if (model.anySetter != null) {
      add("public org.apache.fory.json.meta.JsonAnySetterAccessor anySetterAccessor();")
    }
    if (model.creator != null) {
      add("public java.lang.String[] creatorParameterNames();")
      add("public java.lang.Class[] creatorParameterTypes();")
      add("public ${model.targetSourceName} newInstance(java.lang.Object[]);")
      if (model.creator.factory) add("public java.lang.String creatorFactoryName();")
    }
    if (model.singleton) add("public java.lang.Object fixedInstance();")
    if (model.validators.isNotEmpty()) {
      add("public java.lang.reflect.Method[] validatorMethods();")
      add("public void invokeValidators(java.lang.Object);")
    }
  }

  private fun operationMembers(model: JsonModel): List<String> = buildList {
    model.members.forEachIndexed { index, member ->
      when (member.kind) {
        MemberKind.FIELD -> {
          val type = JvmType(member.descriptor)
          add("public static ${type.sourceName} get_$index(${model.targetSourceName});")
          if (member.writable) {
            add("public static void set_$index(${model.targetSourceName},${type.sourceName});")
          }
        }
        MemberKind.GETTER -> {
          val result = JvmType(parseMethodDescriptor(member.descriptor).result).sourceName
          add("public static $result get_$index(${model.targetSourceName});")
        }
        MemberKind.SETTER -> {
          val value = parseMethodDescriptor(member.descriptor).parameters.single().sourceName
          add("public static void set_$index(${model.targetSourceName},$value);")
        }
      }
    }
    model.creator?.let { creator ->
      val parameters = creator.parameterTypes.joinToString(",") { it.sourceName }
      add("public static ${model.targetSourceName} createFull($parameters);")
      if (creator.defaultDescriptor != null) {
        val masks = List(creator.maskCount) { "int" }
        val all = (creator.parameterTypes.map { it.sourceName } + masks).joinToString(",")
        add("public static ${model.targetSourceName} createDefault($all);")
      }
    }
    if (model.singleton) add("public static ${model.targetSourceName} instance();")
    model.anySetter?.let { setter ->
      val parameters = parseMethodDescriptor(setter.descriptor).parameters
      add(
        "public static void setAny(${model.targetSourceName}," +
          "${parameters.joinToString(",") { it.sourceName }});",
      )
    }
    model.validators.indices.forEach { index ->
      add("public static void validate_$index(${model.targetSourceName});")
    }
    model.valueClass?.layers?.forEachIndexed { index, layer ->
      val ownerType = sourceName(layer.ownerBinaryName)
      add(
        "public static ${layer.carrierType.sourceName} valueConstruct_$index(" +
          "${layer.carrierType.sourceName});",
      )
      add(
        "public static $ownerType valueBox_$index(${layer.carrierType.sourceName});",
      )
      add("public static ${layer.carrierType.sourceName} valueUnbox_$index($ownerType);")
    }
  }

  private fun valueOperationName(type: JvmType): String =
    when (type.descriptor) {
      "Z" -> "Boolean"
      "B" -> "Byte"
      "S" -> "Short"
      "I" -> "Int"
      "J" -> "Long"
      "F" -> "Float"
      "D" -> "Double"
      "C" -> "Char"
      else -> "Value"
    }

  private fun sourceName(binaryName: String): String = binaryName.replace('$', '.')

  private fun methodRule(name: String, descriptor: String): String {
    val method = parseMethodDescriptor(descriptor)
    val result = if (method.result == "V") "void" else JvmType(method.result).sourceName
    return "$result $name(${method.parameters.joinToString(",") { it.sourceName }});"
  }

  private fun constructorRule(descriptor: String): String {
    val parameters = parseMethodDescriptor(descriptor).parameters
    return "<init>(${parameters.joinToString(",") { it.sourceName }});"
  }
}
