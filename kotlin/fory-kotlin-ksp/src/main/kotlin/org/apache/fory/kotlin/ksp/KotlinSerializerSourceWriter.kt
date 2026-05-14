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

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal class KotlinSerializerSourceWriter(private val struct: KotlinSourceStruct) {
  private val builder = StringBuilder(32768)

  fun writeTo(codeGenerator: CodeGenerator) {
    val dependencies =
      Dependencies(aggregating = false, sources = struct.originatingFiles.toTypedArray())
    val output =
      codeGenerator.createNewFile(dependencies, struct.packageName, struct.serializerName, "kt")
    OutputStreamWriter(output, StandardCharsets.UTF_8).use { it.write(write()) }
  }

  fun write(): String {
    writeHeader()
    writeClassStart()
    writeDescriptors()
    writeConstructors()
    writeWrite()
    writeRead()
    writeCompatibleRead()
    writeCopy()
    builder.append("}\n")
    return builder.toString()
  }

  private fun writeHeader() {
    builder.append("@file:OptIn(ExperimentalUnsignedTypes::class)\n\n")
    if (struct.packageName.isNotEmpty()) {
      builder.append("package ").append(struct.packageName).append("\n\n")
    }
    builder.append("import java.util.Collections\n")
    builder.append("import org.apache.fory.Fory\n")
    builder.append("import org.apache.fory.annotation.ForyField\n")
    builder.append("import org.apache.fory.context.CopyContext\n")
    builder.append("import org.apache.fory.context.ReadContext\n")
    builder.append("import org.apache.fory.context.WriteContext\n")
    builder.append("import org.apache.fory.exception.DeserializationException\n")
    builder.append("import org.apache.fory.kotlin.KotlinCollectionAdapters\n")
    builder.append("import org.apache.fory.kotlin.KotlinXlangArrayEncoding\n")
    builder.append("import org.apache.fory.serializer.kotlin.KotlinXlangUnsignedSerializers\n")
    builder.append("import org.apache.fory.meta.TypeDef\n")
    builder.append("import org.apache.fory.meta.TypeExtMeta\n")
    builder.append("import org.apache.fory.reflect.TypeRef\n")
    builder.append("import org.apache.fory.resolver.TypeResolver\n")
    builder.append("import org.apache.fory.serializer.FieldGroups\n")
    builder.append("import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo\n")
    builder.append("import org.apache.fory.serializer.StaticGeneratedStructSerializer\n")
    builder.append("import org.apache.fory.type.Descriptor\n")
    builder.append("import org.apache.fory.type.Types\n\n")
  }

  private fun writeClassStart() {
    builder.append(
      "@Suppress(\"UNCHECKED_CAST\", \"PLATFORM_CLASS_MAPPED_TO_KOTLIN\", \"UNNECESSARY_NOT_NULL_ASSERTION\")\n"
    )
    builder
      .append("public class ")
      .append(struct.serializerName)
      .append(" : StaticGeneratedStructSerializer<")
      .append(struct.typeName)
      .append("> {\n")
    builder.append("  private val allFields: Array<SerializationFieldInfo>\n")
    builder.append("  private val allFieldIds: IntArray\n")
    builder.append("  private val fieldsById: Array<SerializationFieldInfo?>\n")
    builder.append("  private val classVersionHash: Int\n")
    builder.append("  private val sameSchemaCompatible: Boolean\n\n")
  }

  private fun writeDescriptors() {
    builder.append("  public companion object {\n")
    builder
      .append("    private const val HAS_NESTED_COMPATIBLE_STRUCT_FIELDS: Boolean = ")
      .append(struct.hasNestedCompatibleStructFields)
      .append("\n\n")
    builder.append("    @JvmField\n")
    builder.append("    public val DESCRIPTORS: List<Descriptor> = buildDescriptors()\n\n")
    builder.append("    private fun buildDescriptors(): List<Descriptor> {\n")
    builder
      .append("      val descriptors = ArrayList<Descriptor>(")
      .append(struct.fields.size)
      .append(")\n")
    for (field in struct.fields) {
      builder.append("      descriptors.add(Descriptor(")
      builder.append(field.type.typeRefExpression()).append(", ")
      builder.append("\"").append(escape(field.type.typeName)).append("\", ")
      builder.append("\"").append(escape(field.name)).append("\", ")
      builder.append("0, ")
      builder.append("\"").append(escape(struct.qualifiedTypeName)).append("\", ")
      builder.append(field.hasForyField).append(", ")
      builder.append(field.foryFieldId).append(", ")
      builder.append(field.nullable).append(", ")
      builder.append(field.trackingRef).append(", ")
      builder.append("ForyField.Dynamic.").append(field.dynamic).append(", ")
      builder.append(field.arrayType).append("))\n")
    }
    builder.append("      return Collections.unmodifiableList(descriptors)\n")
    builder.append("    }\n")
    builder.append("  }\n\n")
    builder.append("  override fun getGeneratedDescriptors(): List<Descriptor> = DESCRIPTORS\n\n")
  }

  private fun writeConstructors() {
    builder.append("  internal constructor() : super() {\n")
    builder.append("    this.allFields = emptyArray()\n")
    builder.append("    this.allFieldIds = IntArray(0)\n")
    builder.append("    this.fieldsById = arrayOfNulls(0)\n")
    builder.append("    this.classVersionHash = 0\n")
    builder.append("    this.sameSchemaCompatible = false\n")
    builder.append("  }\n\n")

    builder.append(
      "  public constructor(typeResolver: TypeResolver, type: Class<*>) : super(typeResolver, type) {\n"
    )
    writeConstructorBody("buildFieldGroups(DESCRIPTORS)", "false")
    builder.append("  }\n\n")

    builder.append(
      "  public constructor(typeResolver: TypeResolver, type: Class<*>, typeDef: TypeDef?) : super(typeResolver, type, typeDef, DESCRIPTORS) {\n"
    )
    writeConstructorBody(
      "buildLocalFieldGroups(DESCRIPTORS)",
      "typeDef != null && !HAS_NESTED_COMPATIBLE_STRUCT_FIELDS && typeDef.id == TypeDef.buildTypeDef(typeResolver, type).id",
    )
    builder.append("  }\n\n")
  }

  private fun writeConstructorBody(fieldGroupsExpression: String, sameSchemaExpression: String) {
    builder.append("    val fieldGroups: FieldGroups = ").append(fieldGroupsExpression).append("\n")
    builder.append("    this.allFields = fieldGroups.allFields\n")
    builder.append("    this.allFieldIds = localFieldIds(this.allFields, DESCRIPTORS)\n")
    builder.append("    this.fieldsById = arrayOfNulls(DESCRIPTORS.size)\n")
    builder.append("    for (i in this.allFields.indices) {\n")
    builder.append("      this.fieldsById[this.allFieldIds[i]] = this.allFields[i]\n")
    builder.append("    }\n")
    writeUnsignedSerializerBindings()
    builder.append(
      "    this.classVersionHash = if (typeResolver.checkClassVersion()) computeClassVersionHash(DESCRIPTORS) else 0\n"
    )
    builder.append("    this.sameSchemaCompatible = ").append(sameSchemaExpression).append("\n")
  }

  private fun writeUnsignedSerializerBindings() {
    for (field in struct.fields) {
      if (field.type.typeArguments.isEmpty() || !containsUnsignedScalar(field.type)) {
        continue
      }
      writeUnsignedSerializerBinding(field.type, "this.fieldsById[${field.id}]!!.genericType")
    }
  }

  private fun writeUnsignedSerializerBinding(
    type: KotlinSourceTypeNode,
    genericExpression: String
  ) {
    if (type.unsigned && type.componentType == null) {
      builder
        .append("    ")
        .append(genericExpression)
        .append(".setSerializer(KotlinXlangUnsignedSerializers.serializer(typeResolver.config, ")
        .append(type.typeId ?: "Types.UNKNOWN")
        .append(", ")
        .append("\"")
        .append(type.valueTypeName.removeSuffix("?"))
        .append("\"")
        .append("))\n")
      return
    }
    for (i in type.typeArguments.indices) {
      writeUnsignedSerializerBinding(
        type.typeArguments[i],
        "$genericExpression.getTypeParameter$i()"
      )
    }
  }

  private fun writeWrite() {
    builder
      .append("  override fun write(writeContext: WriteContext, value: ")
      .append(struct.typeName)
      .append(") {\n")
    builder.append("    val buffer = writeContext.buffer\n")
    builder.append("    if (typeResolver.checkClassVersion()) {\n")
    builder.append("      buffer.writeInt32(classVersionHash)\n")
    builder.append("    }\n")
    builder.append("    for (i in allFields.indices) {\n")
    builder.append("      val fieldInfo = allFields[i]\n")
    builder.append("      when (allFieldIds[i]) {\n")
    for (field in struct.fields) {
      builder.append("        ").append(field.id).append(" -> ")
      val direct = directWriteStatement(field, "value.${field.name}")
      if (direct == null) {
        builder
          .append("writeFieldValue(writeContext, fieldInfo, ")
          .append("value.${field.name}")
          .append(")\n")
      } else {
        builder.append("{ ").append(direct).append(" }\n")
      }
    }
    builder.append(
      "        else -> throw IllegalStateException(\"Unknown generated field id \${allFieldIds[i]}\")\n"
    )
    builder.append("      }\n")
    builder.append("    }\n")
    builder.append("  }\n\n")
  }

  private fun writeRead() {
    builder
      .append("  override fun read(readContext: ReadContext): ")
      .append(struct.typeName)
      .append(" {\n")
    builder.append("    if (typeDef != null) {\n")
    builder.append(
      "      return if (sameSchemaCompatible) readSchemaConsistent(readContext) else readCompatible(readContext)\n"
    )
    builder.append("    }\n")
    builder.append("    return readSchemaConsistent(readContext)\n")
    builder.append("  }\n\n")

    builder
      .append("  private fun readSchemaConsistent(readContext: ReadContext): ")
      .append(struct.typeName)
      .append(" {\n")
    builder.append("    val buffer = readContext.buffer\n")
    builder.append("    if (typeResolver.checkClassVersion()) {\n")
    builder.append("      checkClassVersion(buffer.readInt32(), classVersionHash)\n")
    builder.append("    }\n")
    writeLocalDeclarations()
    builder.append("    for (i in allFields.indices) {\n")
    builder.append("      val fieldInfo = allFields[i]\n")
    builder.append("      when (allFieldIds[i]) {\n")
    for (field in struct.fields) {
      builder.append("        ").append(field.id).append(" -> ")
      val direct = directReadExpression(field)
      if (direct == null) {
        builder
          .append(field.localName)
          .append(" = ")
          .append(castReadExpression(field, "readFieldValue(readContext, fieldInfo)"))
          .append("\n")
      } else {
        builder.append(field.localName).append(" = ").append(direct).append("\n")
      }
    }
    builder.append(
      "        else -> throw IllegalStateException(\"Unknown generated field id \${allFieldIds[i]}\")\n"
    )
    builder.append("      }\n")
    builder.append("    }\n")
    builder.append("    return ")
    appendConstructorCall(defaultMask = 0L)
    builder.append("\n")
    builder.append("  }\n\n")
  }

  private fun writeCompatibleRead() {
    builder
      .append("  override fun readCompatible(readContext: ReadContext): ")
      .append(struct.typeName)
      .append(" {\n")
    builder.append("    if (sameSchemaCompatible) {\n")
    builder.append("      return readSchemaConsistent(readContext)\n")
    builder.append("    }\n")
    builder.append("    var presentMask = 0L\n")
    writeLocalDeclarations()
    builder.append("    for (i in remoteFields.indices) {\n")
    builder.append("      val remoteField = remoteFields[i]\n")
    builder.append("      when (remoteField.matchedId) {\n")
    for (field in struct.fields) {
      builder.append("        ").append(field.id).append(" -> {\n")
      builder.append("          val localField = fieldsById[").append(field.id).append("]!!\n")
      builder.append("          if (canReadRemoteField(remoteField, localField)) {\n")
      builder
        .append("            ")
        .append(field.localName)
        .append(" = ")
        .append(
          castReadExpression(
            field,
            "readCompatibleFieldValue(readContext, remoteField, localField)",
            compatible = true,
          )
        )
        .append("\n")
      builder
        .append("            presentMask = presentMask or ")
        .append(field.presentMask)
        .append("L\n")
      builder.append("          } else {\n")
      builder.append("            skipField(readContext, remoteField)\n")
      builder.append("          }\n")
      builder.append("        }\n")
    }
    builder.append("        else -> skipField(readContext, remoteField)\n")
    builder.append("      }\n")
    builder.append("    }\n")
    builder.append("    var missingDefaultMask = 0L\n")
    for (field in struct.fields) {
      if (!field.hasDefault && field.nullable) {
        continue
      }
      builder.append("    if ((presentMask and ").append(field.presentMask).append("L) == 0L) {\n")
      when {
        field.hasDefault ->
          builder
            .append("      missingDefaultMask = missingDefaultMask or ")
            .append(field.presentMask)
            .append("L\n")
        else ->
          builder
            .append("      throw DeserializationException(\"Required Kotlin field ")
            .append(struct.qualifiedTypeName)
            .append('.')
            .append(field.name)
            .append(" is missing in compatible xlang payload\")\n")
      }
      builder.append("    }\n")
    }
    writeDefaultConstructorDispatch()
    builder.append("  }\n\n")
  }

  private fun writeCopy() {
    builder
      .append("  override fun copy(copyContext: CopyContext, value: ")
      .append(struct.typeName)
      .append("): ")
      .append(struct.typeName)
      .append(" {\n")
    builder.append("    if (immutable) {\n")
    builder.append("      return value\n")
    builder.append("    }\n")
    for (field in struct.fields) {
      if (field.type.primitive || isScalarUnsigned(field)) {
        builder
          .append("    val ")
          .append(field.localName)
          .append(" = value.")
          .append(field.name)
          .append("\n")
      } else if (isDenseUnsignedArray(field)) {
        builder.append("    val ").append(field.localName).append(" = value.").append(field.name)
        if (field.nullable) {
          builder.append("?.copyOf()\n")
        } else {
          builder.append(".copyOf()\n")
        }
      } else if (field.type.isCollectionOrMap()) {
        builder
          .append("    val ")
          .append(field.localName)
          .append(": ")
          .append(localVariableType(field))
          .append(" = ")
          .append(copyContainerExpression(field.type, "value.${field.name}", 0))
          .append("\n")
      } else {
        builder
          .append("    val ")
          .append(field.localName)
          .append(": ")
          .append(localVariableType(field))
          .append(" = ")
          .append(
            castReadExpression(
              field,
              "copyFieldValue(copyContext, value.${field.name}, fieldsById[${field.id}]!!)"
            )
          )
          .append("\n")
      }
    }
    builder.append("    return ")
    appendConstructorCall(defaultMask = 0L)
    builder.append("\n")
    builder.append("  }\n")
  }

  private fun writeLocalDeclarations() {
    for (field in struct.fields) {
      builder
        .append("    var ")
        .append(field.localName)
        .append(": ")
        .append(localVariableType(field))
        .append(" = ")
        .append(defaultValue(field))
        .append("\n")
    }
  }

  private fun writeDefaultConstructorDispatch() {
    val defaultFields = struct.fields.filter { it.hasDefault }
    if (defaultFields.isEmpty()) {
      builder.append("    return ")
      appendConstructorCall(defaultMask = 0L)
      builder.append("\n")
      return
    }
    builder.append("    return when (missingDefaultMask) {\n")
    val combinations = 1L shl defaultFields.size
    for (combination in 0 until combinations) {
      var mask = 0L
      for (i in defaultFields.indices) {
        if ((combination and (1L shl i)) != 0L) {
          mask = mask or defaultFields[i].presentMask
        }
      }
      builder.append("      ").append(mask).append("L -> ")
      appendConstructorCall(defaultMask = mask)
      builder.append("\n")
    }
    builder.append(
      "      else -> throw DeserializationException(\"Unsupported Kotlin default argument mask \${missingDefaultMask} for "
    )
    builder.append(struct.qualifiedTypeName).append("\")\n")
    builder.append("    }\n")
  }

  private fun appendConstructorCall(defaultMask: Long) {
    builder.append(struct.typeName).append("(")
    var first = true
    for (field in struct.fields) {
      if (field.hasDefault && (defaultMask and field.presentMask) != 0L) {
        continue
      }
      if (!first) {
        builder.append(", ")
      }
      builder.append(field.name).append(" = ").append(constructorValueExpression(field))
      first = false
    }
    builder.append(")")
  }

  private fun constructorValueExpression(field: KotlinSourceField): String {
    val localValue =
      if (field.nullable || field.type.primitive || isScalarUnsigned(field)) {
        field.localName
      } else {
        "${field.localName}!!"
      }
    if (field.type.collectionFactory == CollectionFactory.NONE) {
      return localValue
    }
    val conversion = collectionConversionFunction(field.type.collectionFactory)
    return if (field.nullable) "$localValue?.let { $conversion(it) }"
    else "$conversion($localValue)"
  }

  private fun localVariableType(field: KotlinSourceField): String {
    if (field.nullable || field.type.primitive || isScalarUnsigned(field)) {
      return field.type.valueTypeName
    }
    return field.type.valueTypeName + "?"
  }

  private fun defaultValue(field: KotlinSourceField): String {
    if (field.nullable) {
      return "null"
    }
    return when (field.propertyTypeName) {
      "Boolean" -> "false"
      "Byte" -> "0"
      "Short" -> "0"
      "Int" -> "0"
      "Long" -> "0L"
      "UByte" -> "0u"
      "UShort" -> "0u"
      "UInt" -> "0u"
      "ULong" -> "0uL"
      "Float" -> "0.0f"
      "Double" -> "0.0"
      else -> "null"
    }
  }

  private fun castReadExpression(
    field: KotlinSourceField,
    expression: String,
    compatible: Boolean = false,
  ): String {
    val denseListConversion = denseArrayListConversion(field)
    if (denseListConversion != null) {
      if (field.nullable) {
        return "($expression as Collection<*>?)?.let { KotlinCollectionAdapters.$denseListConversion(it) }"
      }
      return "KotlinCollectionAdapters.$denseListConversion($expression as Collection<*>)"
    }
    val denseUnsigned = denseUnsignedArrayConversion(field)
    if (denseUnsigned != null) {
      if (field.nullable) {
        return "($expression as ${denseUnsignedArrayDelegateType(field)}?)?.let { KotlinXlangArrayEncoding.$denseUnsigned(it) }"
      }
      return "KotlinXlangArrayEncoding.$denseUnsigned($expression as ${denseUnsignedArrayDelegateType(field)})"
    }
    if (compatible && containsUnsignedScalar(field.type)) {
      return fromJavaUnsignedCompatibleExpression(field.type, expression)
    }
    return when (field.type.valueTypeName.removeSuffix("?")) {
      else -> "($expression as ${field.type.valueTypeName})"
    }
  }

  private fun containsUnsignedScalar(type: KotlinSourceTypeNode): Boolean =
    (type.unsigned && type.componentType == null) ||
      type.typeArguments.any { containsUnsignedScalar(it) }

  private fun KotlinSourceTypeNode.isCollectionOrMap(): Boolean =
    typeId == "Types.LIST" || typeId == "Types.SET" || typeId == "Types.MAP"

  private fun copyContainerExpression(
    type: KotlinSourceTypeNode,
    expression: String,
    depth: Int
  ): String {
    if (type.nullable) {
      val source = "copySource$depth"
      return "$expression?.let { $source -> ${copyContainerExpression(type.copy(nullable = false), source, depth + 1)} }"
    }
    val source = "copySource$depth"
    val target = "copyTarget$depth"
    val valueType = type.valueTypeName.removeSuffix("?")
    return when (type.typeId) {
      "Types.LIST" -> {
        val element = "copyElement$depth"
        val copiedElement = copyElementExpression(type.typeArguments[0], element, depth + 1)
        "run { val $source = $expression; val $target = java.util.ArrayList<Any?>($source.size); for ($element in $source) { $target.add($copiedElement) }; $target as $valueType }"
      }
      "Types.SET" -> {
        val element = "copyElement$depth"
        val copiedElement = copyElementExpression(type.typeArguments[0], element, depth + 1)
        "run { val $source = $expression; val $target = java.util.LinkedHashSet<Any?>($source.size); for ($element in $source) { $target.add($copiedElement) }; $target as $valueType }"
      }
      "Types.MAP" -> {
        val entry = "copyEntry$depth"
        val copiedKey = copyElementExpression(type.typeArguments[0], "$entry.key", depth + 1)
        val copiedValue = copyElementExpression(type.typeArguments[1], "$entry.value", depth + 2)
        "run { val $source = $expression; val $target = java.util.LinkedHashMap<Any?, Any?>($source.size); for ($entry in $source.entries) { $target[$copiedKey] = $copiedValue }; $target as $valueType }"
      }
      else -> "copyContext.copyObject($expression) as $valueType"
    }
  }

  private fun copyElementExpression(
    type: KotlinSourceTypeNode,
    expression: String,
    depth: Int
  ): String {
    if (type.nullable) {
      val value = "copyValue$depth"
      return "$expression?.let { $value -> ${copyElementExpression(type.copy(nullable = false), value, depth + 1)} }"
    }
    if (
      type.primitive || type.unsigned || type.typeId == "Types.STRING" || type.componentType != null
    ) {
      return expression
    }
    if (type.isCollectionOrMap()) {
      return copyContainerExpression(type, expression, depth + 1)
    }
    return "(copyContext.copyObject($expression) as ${type.valueTypeName.removeSuffix("?")})"
  }

  private fun fromJavaUnsignedCompatibleExpression(
    type: KotlinSourceTypeNode,
    expression: String,
    depth: Int = 0
  ): String {
    if (type.nullable) {
      val valueName = "value$depth"
      return "($expression)?.let { $valueName -> ${fromJavaUnsignedCompatibleExpression(type.copy(nullable = false), valueName, depth + 1)} }"
    }
    if (type.unsigned && type.componentType == null) {
      val compatibleValue = "compatibleValue$depth"
      return when (type.valueTypeName.removeSuffix("?")) {
        "UByte" ->
          "run { val $compatibleValue = $expression; if ($compatibleValue is UByte) $compatibleValue else ($compatibleValue as Number).toInt().toUByte() }"
        "UShort" ->
          "run { val $compatibleValue = $expression; if ($compatibleValue is UShort) $compatibleValue else ($compatibleValue as Number).toInt().toUShort() }"
        "UInt" ->
          "run { val $compatibleValue = $expression; if ($compatibleValue is UInt) $compatibleValue else ($compatibleValue as Number).toLong().toUInt() }"
        "ULong" ->
          "run { val $compatibleValue = $expression; if ($compatibleValue is ULong) $compatibleValue else ($compatibleValue as Number).toLong().toULong() }"
        else -> expression
      }
    }
    if (type.typeArguments.isEmpty()) {
      return "($expression as ${type.valueTypeName})"
    }
    return when (type.typeId) {
      "Types.LIST",
      "Types.SET" -> {
        val element = type.typeArguments[0]
        if (containsUnsignedScalar(element)) {
          val elementName = "element$depth"
          if (type.typeId == "Types.SET") {
            "($expression as java.util.Collection<*>).mapTo(java.util.LinkedHashSet<Any?>()) { $elementName -> ${fromJavaUnsignedCompatibleExpression(element, elementName, depth + 1)} } as ${type.valueTypeName}"
          } else {
            "($expression as java.util.Collection<*>).map { $elementName -> ${fromJavaUnsignedCompatibleExpression(element, elementName, depth + 1)} } as ${type.valueTypeName}"
          }
        } else {
          "($expression as ${type.valueTypeName})"
        }
      }
      "Types.MAP" -> {
        val key = type.typeArguments[0]
        val value = type.typeArguments[1]
        if (containsUnsignedScalar(key) || containsUnsignedScalar(value)) {
          val entryName = "entry$depth"
          "($expression as kotlin.collections.Map<*, *>).entries.associate { $entryName -> (${fromJavaUnsignedCompatibleExpression(key, "$entryName.key", depth + 1)}) to (${fromJavaUnsignedCompatibleExpression(value, "$entryName.value", depth + 1)}) } as ${type.valueTypeName}"
        } else {
          "($expression as ${type.valueTypeName})"
        }
      }
      else -> "($expression as ${type.valueTypeName})"
    }
  }

  private fun canDirect(field: KotlinSourceField): Boolean =
    !field.nullable &&
      !field.trackingRef &&
      field.type.typeArguments.isEmpty() &&
      field.type.componentType == null

  private fun isScalarUnsigned(field: KotlinSourceField): Boolean =
    field.type.unsigned && field.type.componentType == null

  private fun isDenseUnsignedArray(field: KotlinSourceField): Boolean =
    field.type.unsigned && field.type.componentType != null

  private fun directWriteStatement(field: KotlinSourceField, value: String): String? {
    val denseWrite = denseUnsignedArrayWrite(field)
    if (denseWrite != null && !field.nullable && !field.trackingRef) {
      return "KotlinXlangArrayEncoding.$denseWrite(writeContext, $value)"
    }
    if (denseWrite != null && field.nullable && !field.trackingRef) {
      val nullableArray = "nullableArray${field.id}"
      return "val $nullableArray = $value; if ($nullableArray == null) { buffer.writeByte(Fory.NULL_FLAG) } else { buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG); KotlinXlangArrayEncoding.$denseWrite(writeContext, $nullableArray) }"
    }
    if (isScalarUnsigned(field) && field.nullable && !field.trackingRef) {
      val nullableValue = "nullableValue${field.id}"
      val writeValue =
        when (field.type.typeId) {
          "Types.UINT8" -> "buffer.writeByte($nullableValue.toInt())"
          "Types.UINT16" -> "buffer.writeInt16($nullableValue.toShort())"
          "Types.UINT32" -> "buffer.writeInt32($nullableValue.toInt())"
          "Types.VAR_UINT32" -> "buffer.writeVarUInt32($nullableValue.toInt())"
          "Types.UINT64" -> "buffer.writeInt64($nullableValue.toLong())"
          "Types.VAR_UINT64" -> "buffer.writeVarUInt64($nullableValue.toLong())"
          "Types.TAGGED_UINT64" -> "buffer.writeTaggedUInt64($nullableValue.toLong())"
          else -> return null
        }
      return "val $nullableValue = $value; if ($nullableValue == null) { buffer.writeByte(Fory.NULL_FLAG) } else { buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG); $writeValue }"
    }
    if (!canDirect(field)) {
      return null
    }
    return when (field.type.typeId) {
      "Types.BOOL" -> "buffer.writeBoolean($value)"
      "Types.INT8" -> "buffer.writeByte($value.toInt())"
      "Types.INT16" -> "buffer.writeInt16($value)"
      "Types.INT32" -> "buffer.writeInt32($value)"
      "Types.VARINT32" -> "buffer.writeVarInt32($value)"
      "Types.INT64" -> "buffer.writeInt64($value)"
      "Types.VARINT64" -> "buffer.writeVarInt64($value)"
      "Types.TAGGED_INT64" -> "buffer.writeTaggedInt64($value)"
      "Types.UINT8" -> "buffer.writeByte($value.toInt())"
      "Types.UINT16" -> "buffer.writeInt16($value.toShort())"
      "Types.UINT32" -> "buffer.writeInt32($value.toInt())"
      "Types.VAR_UINT32" -> "buffer.writeVarUInt32($value.toInt())"
      "Types.UINT64" -> "buffer.writeInt64($value.toLong())"
      "Types.VAR_UINT64" -> "buffer.writeVarUInt64($value.toLong())"
      "Types.TAGGED_UINT64" -> "buffer.writeTaggedUInt64($value.toLong())"
      "Types.FLOAT32" -> "buffer.writeFloat32($value)"
      "Types.FLOAT64" -> "buffer.writeFloat64($value)"
      "Types.STRING" -> "writeContext.writeString($value)"
      else -> null
    }
  }

  private fun directReadExpression(field: KotlinSourceField): String? {
    val denseRead = denseUnsignedArrayRead(field)
    if (denseRead != null && !field.nullable) {
      return "KotlinXlangArrayEncoding.$denseRead(readContext, typeResolver.config.maxBinarySize())"
    }
    if (denseRead != null && field.nullable && !field.trackingRef) {
      return "if (buffer.readByte() == Fory.NULL_FLAG) null else KotlinXlangArrayEncoding.$denseRead(readContext, typeResolver.config.maxBinarySize())"
    }
    if (isScalarUnsigned(field) && field.nullable && !field.trackingRef) {
      val readValue =
        when (field.type.typeId) {
          "Types.UINT8" -> "buffer.readByte().toUByte()"
          "Types.UINT16" -> "buffer.readInt16().toUShort()"
          "Types.UINT32" -> "buffer.readInt32().toUInt()"
          "Types.VAR_UINT32" -> "buffer.readVarUInt32().toUInt()"
          "Types.UINT64" -> "buffer.readInt64().toULong()"
          "Types.VAR_UINT64" -> "buffer.readVarUInt64().toULong()"
          "Types.TAGGED_UINT64" -> "buffer.readTaggedUInt64().toULong()"
          else -> return null
        }
      return "if (buffer.readByte() == Fory.NULL_FLAG) null else $readValue"
    }
    if (!canDirect(field)) {
      return null
    }
    return when (field.type.typeId) {
      "Types.BOOL" -> "buffer.readBoolean()"
      "Types.INT8" -> "buffer.readByte()"
      "Types.INT16" -> "buffer.readInt16()"
      "Types.INT32" -> "buffer.readInt32()"
      "Types.VARINT32" -> "buffer.readVarInt32()"
      "Types.INT64" -> "buffer.readInt64()"
      "Types.VARINT64" -> "buffer.readVarInt64()"
      "Types.TAGGED_INT64" -> "buffer.readTaggedInt64()"
      "Types.UINT8" -> "buffer.readByte().toUByte()"
      "Types.UINT16" -> "buffer.readInt16().toUShort()"
      "Types.UINT32" -> "buffer.readInt32().toUInt()"
      "Types.VAR_UINT32" -> "buffer.readVarUInt32().toUInt()"
      "Types.UINT64" -> "buffer.readInt64().toULong()"
      "Types.VAR_UINT64" -> "buffer.readVarUInt64().toULong()"
      "Types.TAGGED_UINT64" -> "buffer.readTaggedUInt64().toULong()"
      "Types.FLOAT32" -> "buffer.readFloat32()"
      "Types.FLOAT64" -> "buffer.readFloat64()"
      "Types.STRING" -> "readContext.readString()"
      else -> null
    }
  }

  private fun denseUnsignedArrayRead(field: KotlinSourceField): String? =
    when (field.type.valueTypeName.removeSuffix("?")) {
      "UByteArray" -> "readUByteArray"
      "UShortArray" -> "readUShortArray"
      "UIntArray" -> "readUIntArray"
      "ULongArray" -> "readULongArray"
      else -> null
    }

  private fun denseArrayListConversion(field: KotlinSourceField): String? {
    if (!field.arrayType || field.type.typeArguments.size != 1) {
      return null
    }
    return when (field.type.typeArguments.single().valueTypeName.removeSuffix("?")) {
      "Byte" -> "toByteList"
      "Short" -> "toShortList"
      "Int" -> "toIntList"
      "Long" -> "toLongList"
      "UByte" -> "toUByteList"
      "UShort" -> "toUShortList"
      "UInt" -> "toUIntList"
      "ULong" -> "toULongList"
      "Float" -> "toFloatList"
      "Double" -> "toDoubleList"
      else -> null
    }
  }

  private fun denseUnsignedArrayConversion(field: KotlinSourceField): String? =
    when (field.type.valueTypeName.removeSuffix("?")) {
      "UByteArray" -> "toUByteArray"
      "UShortArray" -> "toUShortArray"
      "UIntArray" -> "toUIntArray"
      "ULongArray" -> "toULongArray"
      else -> null
    }

  private fun denseUnsignedArrayWrite(field: KotlinSourceField): String? =
    when (field.type.valueTypeName.removeSuffix("?")) {
      "UByteArray" -> "writeUByteArray"
      "UShortArray" -> "writeUShortArray"
      "UIntArray" -> "writeUIntArray"
      "ULongArray" -> "writeULongArray"
      else -> null
    }

  private fun denseUnsignedArrayDelegateType(field: KotlinSourceField): String =
    when (field.type.valueTypeName.removeSuffix("?")) {
      "UByteArray" -> "ByteArray"
      "UShortArray" -> "ShortArray"
      "UIntArray" -> "IntArray"
      "ULongArray" -> "LongArray"
      else -> error("No dense unsigned array delegate for ${field.type.valueTypeName}")
    }

  private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

  private fun collectionConversionFunction(factory: CollectionFactory): String =
    when (factory) {
      CollectionFactory.MUTABLE_LIST -> "KotlinCollectionAdapters.toMutableList"
      CollectionFactory.ARRAY_LIST -> "KotlinCollectionAdapters.toArrayList"
      CollectionFactory.LINKED_LIST -> "KotlinCollectionAdapters.toLinkedList"
      CollectionFactory.COPY_ON_WRITE_ARRAY_LIST ->
        "KotlinCollectionAdapters.toCopyOnWriteArrayList"
      CollectionFactory.MUTABLE_SET -> "KotlinCollectionAdapters.toMutableSet"
      CollectionFactory.HASH_SET -> "KotlinCollectionAdapters.toHashSet"
      CollectionFactory.LINKED_HASH_SET -> "KotlinCollectionAdapters.toLinkedHashSet"
      CollectionFactory.TREE_SET -> "KotlinCollectionAdapters.toTreeSet"
      CollectionFactory.COPY_ON_WRITE_ARRAY_SET -> "KotlinCollectionAdapters.toCopyOnWriteArraySet"
      CollectionFactory.CONCURRENT_SKIP_LIST_SET ->
        "KotlinCollectionAdapters.toConcurrentSkipListSet"
      CollectionFactory.MUTABLE_MAP -> "KotlinCollectionAdapters.toMutableMap"
      CollectionFactory.HASH_MAP -> "KotlinCollectionAdapters.toHashMap"
      CollectionFactory.LINKED_HASH_MAP -> "KotlinCollectionAdapters.toLinkedHashMap"
      CollectionFactory.TREE_MAP -> "KotlinCollectionAdapters.toTreeMap"
      CollectionFactory.CONCURRENT_HASH_MAP -> "KotlinCollectionAdapters.toConcurrentHashMap"
      CollectionFactory.CONCURRENT_SKIP_LIST_MAP ->
        "KotlinCollectionAdapters.toConcurrentSkipListMap"
      CollectionFactory.NONE -> error("No conversion function for NONE")
    }
}
