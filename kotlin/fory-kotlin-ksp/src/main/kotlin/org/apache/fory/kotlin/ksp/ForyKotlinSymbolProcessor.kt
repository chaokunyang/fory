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
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.AnnotationUseSiteTarget
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val MAX_CONSTRUCTOR_FIELDS = Long.SIZE_BITS - 1
private const val MAX_DEFAULT_CONSTRUCTOR_FIELDS = 12
private const val REF_NOT_SUPPORTED_DIAGNOSTIC =
  "@Ref is not supported by Kotlin KSP xlang serializers because constructor-based reads cannot publish partially constructed objects"

internal fun constructorFieldLimitDiagnostic(fieldCount: Int): String? =
  if (fieldCount > MAX_CONSTRUCTOR_FIELDS) {
    "Kotlin KSP xlang serializers currently support at most $MAX_CONSTRUCTOR_FIELDS constructor fields"
  } else {
    null
  }

internal fun defaultConstructorFieldLimitDiagnostic(defaultFieldCount: Int): String? =
  if (defaultFieldCount > MAX_DEFAULT_CONSTRUCTOR_FIELDS) {
    "Kotlin KSP xlang serializers currently support at most $MAX_DEFAULT_CONSTRUCTOR_FIELDS defaulted constructor fields because Kotlin source generation must call constructors with omitted default arguments"
  } else {
    null
  }

internal fun unsupportedStructDeclarationDiagnostic(
  classKind: ClassKind,
  modifiers: Set<Modifier>,
): String? {
  if (
    classKind == ClassKind.CLASS && Modifier.ABSTRACT !in modifiers && Modifier.SEALED !in modifiers
  ) {
    return null
  }
  val reason =
    when {
      classKind == ClassKind.OBJECT ->
        "object declarations do not expose primary-constructor schema fields"
      classKind != ClassKind.CLASS && classKind != ClassKind.OBJECT ->
        "interfaces are not concrete serializer targets"
      Modifier.SEALED in modifiers ->
        "sealed declarations are polymorphic bases and do not have a single concrete schema"
      Modifier.ABSTRACT in modifiers ->
        "abstract declarations are polymorphic bases and do not have a single concrete schema"
      else -> "the declaration is not a concrete serializer target"
    }
  return "Kotlin KSP xlang serializers require each @ForyStruct serializer target to be a concrete class; $reason"
}

internal fun unsupportedStructVisibilityDiagnostic(modifiers: Set<Modifier>): String? =
  if (Modifier.PRIVATE in modifiers) {
    "Kotlin KSP xlang serializers require @ForyStruct targets to be public or internal; private targets are inaccessible to generated code"
  } else {
    null
  }

internal fun unsupportedStructTypeParameterDiagnostic(typeParameterCount: Int): String? =
  if (typeParameterCount > 0) {
    "Kotlin KSP xlang serializers do not support generic @ForyStruct targets in phase 1"
  } else {
    null
  }

internal fun unsupportedPrimaryConstructorVisibilityDiagnostic(modifiers: Set<Modifier>): String? =
  if (Modifier.PRIVATE in modifiers || Modifier.PROTECTED in modifiers) {
    "Kotlin KSP xlang serializers require a public or internal primary constructor because generated serializers call it directly"
  } else {
    null
  }

internal class ForyKotlinSymbolProcessor(private val environment: SymbolProcessorEnvironment) :
  SymbolProcessor {
  private val codeGenerator: CodeGenerator = environment.codeGenerator
  private val logger: KSPLogger = environment.logger
  private val generatedTypes = linkedSetOf<String>()

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val files = resolver.getAllFiles().toList()
    val symbols =
      resolver.getSymbolsWithAnnotation(FORY_STRUCT).toList() +
        files.flatMap { file -> file.declarations.flatMap { structDeclarations(it) } }.toList()
    val deferred = mutableListOf<KSAnnotated>()
    for (symbol in symbols) {
      val declaration = symbol as? KSClassDeclaration
      if (declaration == null) {
        logger.error("@ForyStruct can only be used on classes and interfaces", symbol)
        continue
      }
      if (declaration.parentDeclaration is KSClassDeclaration) {
        logger.error(
          "Nested Kotlin @ForyStruct declarations are not supported by phase 1 Kotlin KSP xlang serializers",
          declaration,
        )
        continue
      }
      if (!declaration.isConcreteStruct()) {
        reportUnsupportedStructDeclaration(declaration)
        continue
      }
      val qualifiedName = declaration.qualifiedName?.asString()
      if (qualifiedName == null || !generatedTypes.add(qualifiedName)) {
        continue
      }
      val struct = parseStruct(declaration) ?: continue
      KotlinSerializerSourceWriter(struct).writeTo(codeGenerator)
      writeR8Rules(struct)
    }
    return deferred
  }

  private fun structDeclarations(declaration: KSDeclaration): Sequence<KSClassDeclaration> {
    if (declaration !is KSClassDeclaration) {
      return emptySequence()
    }
    val current =
      if (
        hasDeclarationAnnotation(declaration, FORY_STRUCT) &&
          declaration.parentDeclaration !is KSClassDeclaration
      )
        sequenceOf(declaration)
      else emptySequence()
    return current + declaration.declarations.flatMap { structDeclarations(it) }
  }

  private fun parseStruct(declaration: KSClassDeclaration): KotlinSourceStruct? {
    if (declaration.qualifiedName == null) {
      logger.error(
        "@ForyStruct on local or anonymous Kotlin declarations is unsupported",
        declaration
      )
      return null
    }
    if (declaration.packageName.asString().isEmpty()) {
      logger.error(
        "Kotlin KSP xlang serializers require @ForyStruct declarations to be in a named package",
        declaration,
      )
      return null
    }
    val visibilityDiagnostic = unsupportedStructVisibilityDiagnostic(declaration.modifiers)
    if (visibilityDiagnostic != null) {
      logger.error(visibilityDiagnostic, declaration)
      return null
    }
    val typeParameterDiagnostic =
      unsupportedStructTypeParameterDiagnostic(declaration.typeParameters.size)
    if (typeParameterDiagnostic != null) {
      logger.error(typeParameterDiagnostic, declaration)
      return null
    }
    val primaryConstructor = declaration.primaryConstructor
    if (primaryConstructor == null) {
      logger.error("Kotlin KSP xlang serializers require a primary constructor", declaration)
      return null
    }
    val constructorVisibilityDiagnostic =
      unsupportedPrimaryConstructorVisibilityDiagnostic(primaryConstructor.modifiers)
    if (constructorVisibilityDiagnostic != null) {
      logger.error(constructorVisibilityDiagnostic, primaryConstructor)
      return null
    }
    val propertiesByName = declaration.getAllProperties().associateBy { it.simpleName.asString() }
    val fields = mutableListOf<KotlinSourceField>()
    val foryIds = hashSetOf<Int>()
    var nextId = 0
    for (parameter in primaryConstructor.parameters) {
      val parameterName = parameter.name?.asString() ?: continue
      val property = propertiesByName[parameterName]
      if (property == null || (!parameter.isVal && !parameter.isVar)) {
        logger.error(
          "Constructor parameter $parameterName is not a field-backed property",
          parameter
        )
        return null
      }
      if (Modifier.PRIVATE in property.modifiers) {
        logger.error(
          "Private Fory field $parameterName is inaccessible to generated code",
          property
        )
        return null
      }
      val fieldMeta = resolveForyField(property, parameter) ?: return null
      if (fieldMeta.id < -1) {
        logger.error("@ForyField id must be -1 or a non-negative value", property)
        return null
      }
      if (fieldMeta.id >= 0 && !foryIds.add(fieldMeta.id)) {
        logger.error(
          "Duplicate @ForyField id ${fieldMeta.id} in ${declaration.qualifiedName!!.asString()}",
          property
        )
        return null
      }
      if (hasDeclarationAnnotation(property, NULLABLE)) {
        logger.error(
          "Kotlin Fory fields must use '?' for nullable schema types, not @Nullable",
          property
        )
        return null
      }
      val hasFieldRef = resolveFieldRef(property, parameter) ?: return null
      if (hasFieldRef) {
        logger.error(REF_NOT_SUPPORTED_DIAGNOSTIC, property)
        return null
      }
      val type = parameter.type.resolve()
      val typeNode =
        parseType(type, property, arrayType = hasFieldAnnotation(property, ARRAY_TYPE))
          ?: return null
      val constructorFieldLimitDiagnostic = constructorFieldLimitDiagnostic(nextId + 1)
      if (constructorFieldLimitDiagnostic != null) {
        logger.error(constructorFieldLimitDiagnostic, declaration)
        return null
      }
      val id = nextId++
      fields.add(
        KotlinSourceField(
          id = id,
          name = parameterName,
          type = typeNode,
          hasForyField = fieldMeta.hasAnnotation,
          foryFieldId = fieldMeta.id,
          trackingRef = false,
          dynamic = fieldMeta.dynamic,
          arrayType = hasFieldAnnotation(property, ARRAY_TYPE),
          hasDefault = parameter.hasDefault,
          nullable = type.nullability == Nullability.NULLABLE,
          propertyTypeName = kotlinSourceTypeName(type),
        )
      )
    }
    if (fields.isEmpty()) {
      logger.error(
        "Kotlin KSP xlang serializers require at least one primary-constructor field",
        declaration
      )
      return null
    }
    val defaultFieldCount = fields.count { it.hasDefault }
    val defaultFieldLimitDiagnostic = defaultConstructorFieldLimitDiagnostic(defaultFieldCount)
    if (defaultFieldLimitDiagnostic != null) {
      logger.error(defaultFieldLimitDiagnostic, declaration)
      return null
    }
    val packageName = declaration.packageName.asString()
    val typeName = declaration.simpleName.asString()
    return KotlinSourceStruct(
      packageName = packageName,
      typeName = typeName,
      qualifiedTypeName = declaration.qualifiedName!!.asString(),
      serializerName = "${escapeBinarySimpleName(typeName)}_ForySerializer",
      serializerVisibility =
        if (Modifier.INTERNAL in declaration.modifiers) {
          KotlinSerializerVisibility.INTERNAL
        } else {
          KotlinSerializerVisibility.PUBLIC
        },
      fields = fields,
      originatingFiles = listOfNotNull(declaration.containingFile),
    )
  }

  private fun writeR8Rules(struct: KotlinSourceStruct) {
    val dependencies =
      Dependencies(aggregating = false, sources = struct.originatingFiles.toTypedArray())
    val output =
      codeGenerator.createNewFileByPath(
        dependencies,
        "META-INF/proguard/fory-static-generated-${escapedResourceName(struct.qualifiedTypeName)}.pro",
        "",
      )
    output.use { it.write(r8Rules(struct).toByteArray(StandardCharsets.UTF_8)) }
  }

  private fun r8Rules(struct: KotlinSourceStruct): String = buildString {
    append("-keepnames class ").append(struct.qualifiedTypeName).append('\n').append('\n')
    append("-keepattributes RuntimeVisibleAnnotations\n")
    append('\n')
    append("-if class ").append(struct.qualifiedTypeName).append('\n')
    append("-keep,allowoptimization class ").append(struct.qualifiedSerializerName).append(" {\n")
    append("  public <init>();\n")
    append("  public <init>(org.apache.fory.resolver.TypeResolver, java.lang.Class);\n")
    append(
      "  public <init>(org.apache.fory.resolver.TypeResolver, java.lang.Class, org.apache.fory.meta.TypeDef);\n"
    )
    append("}\n")
  }

  private fun KSClassDeclaration.isConcreteStruct(): Boolean {
    return unsupportedStructDeclarationDiagnostic(classKind, modifiers) == null
  }

  private fun reportUnsupportedStructDeclaration(declaration: KSClassDeclaration) {
    logger.error(
      unsupportedStructDeclarationDiagnostic(declaration.classKind, declaration.modifiers)
        ?: "Kotlin KSP xlang serializers require each @ForyStruct serializer target to be a concrete class",
      declaration,
    )
  }

  private fun resolveForyField(
    property: KSPropertyDeclaration,
    parameter: KSValueParameter,
  ): ForyFieldMeta? {
    val propertyMeta = foryFieldMeta(property.annotations)
    val parameterMeta = foryFieldMeta(parameter.annotations)
    val getterHasFory = property.getter?.annotations?.any { isAnnotation(it, FORY_FIELD) } == true
    val setterHasFory = property.setter?.annotations?.any { isAnnotation(it, FORY_FIELD) } == true
    if (getterHasFory || setterHasFory) {
      logger.error(
        "@get:ForyField and @set:ForyField are not valid for Kotlin xlang schema fields",
        property
      )
      return null
    }
    if (propertyMeta != null && parameterMeta != null && propertyMeta != parameterMeta) {
      logger.error(
        "@ForyField metadata on Kotlin property and constructor parameter must match",
        property,
      )
      return null
    }
    // Java annotations on primary-constructor properties commonly land on the constructor
    // parameter when PARAMETER is an allowed target; KSP must preserve schema IDs from that site.
    return propertyMeta ?: parameterMeta ?: ForyFieldMeta.NONE
  }

  private fun hasFieldAnnotation(property: KSPropertyDeclaration, qualifiedName: String): Boolean {
    val getterHas = property.getter?.annotations?.any { isAnnotation(it, qualifiedName) } == true
    val setterHas = property.setter?.annotations?.any { isAnnotation(it, qualifiedName) } == true
    if (getterHas || setterHas) {
      logger.error(
        "@get:${qualifiedName.substringAfterLast('.')} and @set:${qualifiedName.substringAfterLast('.')} are not valid for Kotlin xlang schema fields",
        property
      )
      return false
    }
    return property.annotations.any { isAnnotation(it, qualifiedName) } || false
  }

  private fun resolveFieldRef(
    property: KSPropertyDeclaration,
    parameter: KSValueParameter,
  ): Boolean? {
    val getterHasRef = property.getter?.annotations?.any { isAnnotation(it, REF) } == true
    val setterHasRef = property.setter?.annotations?.any { isAnnotation(it, REF) } == true
    if (getterHasRef || setterHasRef) {
      logger.error("@get:Ref and @set:Ref are not valid for Kotlin xlang schema fields", property)
      return null
    }
    val refs = mutableListOf<KSAnnotation>()
    if (!appendFieldRefAnnotations(refs, property.annotations, property)) {
      return null
    }
    if (!appendParameterRefAnnotations(refs, parameter.annotations, property)) {
      return null
    }
    return refs.isNotEmpty()
  }

  private fun appendFieldRefAnnotations(
    refs: MutableList<KSAnnotation>,
    annotations: Sequence<KSAnnotation>,
    owner: KSAnnotated,
  ): Boolean {
    for (annotation in annotations) {
      if (!isAnnotation(annotation, REF)) {
        continue
      }
      val useSiteTarget = annotation.useSiteTarget
      when (useSiteTarget) {
        null,
        AnnotationUseSiteTarget.PROPERTY,
        AnnotationUseSiteTarget.FIELD -> refs.add(annotation)
        AnnotationUseSiteTarget.GET,
        AnnotationUseSiteTarget.SET ->
          logger.error(
            "@get:Ref and @set:Ref are not valid for Kotlin xlang schema fields",
            owner,
          )
        else ->
          logger.error(
            "@${useSiteTarget.name.lowercase(Locale.ROOT)}:Ref is not valid for Kotlin xlang schema fields",
            owner,
          )
      }
      if (
        useSiteTarget != null &&
          useSiteTarget != AnnotationUseSiteTarget.PROPERTY &&
          useSiteTarget != AnnotationUseSiteTarget.FIELD
      ) {
        return false
      }
    }
    return true
  }

  private fun appendParameterRefAnnotations(
    refs: MutableList<KSAnnotation>,
    annotations: Sequence<KSAnnotation>,
    owner: KSAnnotated,
  ): Boolean {
    for (annotation in annotations) {
      if (!isAnnotation(annotation, REF)) {
        continue
      }
      val useSiteTarget = annotation.useSiteTarget
      when (useSiteTarget) {
        null,
        AnnotationUseSiteTarget.PARAM -> refs.add(annotation)
        else -> {
          logger.error(
            "@${useSiteTarget.name.lowercase(Locale.ROOT)}:Ref is not valid for Kotlin constructor parameters",
            owner,
          )
          return false
        }
      }
    }
    return true
  }

  private fun foryFieldMeta(annotations: Sequence<KSAnnotation>): ForyFieldMeta? {
    val annotation = annotations.firstOrNull { isAnnotation(it, FORY_FIELD) } ?: return null
    var id = -1
    var dynamic = "AUTO"
    for (argument in annotation.arguments) {
      when (argument.name?.asString()) {
        "id" -> id = argument.value as Int
        "dynamic" ->
          dynamic = argument.value.toString().substringAfterLast('.').uppercase(Locale.ROOT)
      }
    }
    return ForyFieldMeta(true, id, dynamic)
  }

  private fun parseType(
    type: KSType,
    owner: KSAnnotated,
    arrayType: Boolean,
    arrayComponent: Boolean = false,
  ): KotlinSourceTypeNode? {
    val declaration = type.declaration
    val qualifiedName = declaration.qualifiedName?.asString()
    if (type.annotations.any { isAnnotation(it, NULLABLE) }) {
      logger.error(
        "Kotlin Fory fields must use '?' for nullable schema types, not @Nullable",
        owner
      )
      return null
    }
    val nullable = type.nullability == Nullability.NULLABLE
    val hasTypeRef = hasRefAnnotation(type, owner) ?: return null
    if (hasTypeRef) {
      logger.error(REF_NOT_SUPPORTED_DIAGNOSTIC, owner)
      return null
    }
    val encoding = encodingAnnotation(type, owner)
    if (encoding == Encoding.Invalid) {
      return null
    }
    val scalar = scalarType(qualifiedName, encoding, owner, arrayComponent) ?: return null
    if (scalar != UnsupportedScalar) {
      return KotlinSourceTypeNode(
        rawClassExpression = scalarRawClassExpression(scalar, nullable),
        kotlinTypeName = scalar.kotlinTypeName,
        valueTypeName = nullableValueType(scalar.kotlinTypeName, nullable),
        typeName = scalar.typeName,
        typeId = scalar.typeId,
        nullable = nullable,
        trackingRef = false,
        primitive = scalar.primitive,
        unsigned = scalar.unsigned,
      )
    }
    if (encoding != null) {
      logger.error(
        "${encoding.sourceName} is only valid on Int, Long, UInt, or ULong type uses",
        owner,
      )
      return null
    }
    if (qualifiedName == "kotlin.ByteArray" && !arrayType) {
      return KotlinSourceTypeNode(
        rawClassExpression = "ByteArray::class.java",
        kotlinTypeName = "ByteArray",
        valueTypeName = nullableValueType("ByteArray", nullable),
        typeName = "byte[]",
        typeId = "Types.BINARY",
        nullable = nullable,
        trackingRef = false,
        primitive = false,
        unsigned = false,
      )
    }
    val denseArrayElement = denseArrayElement(qualifiedName)
    if (denseArrayElement != null) {
      return KotlinSourceTypeNode(
        rawClassExpression = denseArrayRawClass(qualifiedName!!),
        kotlinTypeName = denseArrayKotlinName(qualifiedName),
        valueTypeName = nullableValueType(denseArrayKotlinName(qualifiedName), nullable),
        typeName = denseArrayTypeName(qualifiedName),
        typeId = denseArrayElement.arrayTypeId,
        nullable = nullable,
        trackingRef = false,
        primitive = false,
        unsigned = denseArrayElement.unsigned,
        componentType =
          KotlinSourceTypeNode(
            rawClassExpression = denseArrayElement.rawClassExpression,
            kotlinTypeName = denseArrayElement.kotlinTypeName,
            valueTypeName = denseArrayElement.kotlinTypeName,
            typeName = denseArrayElement.typeName,
            typeId = denseArrayElement.typeId,
            nullable = false,
            trackingRef = false,
            primitive = true,
            unsigned = denseArrayElement.unsigned,
          ),
      )
    }
    if (arrayType && !isList(qualifiedName)) {
      logger.error(
        "@ArrayType is only valid on ByteArray or List<T> Kotlin xlang fields",
        owner,
      )
      return null
    }
    if (isList(qualifiedName) || isSet(qualifiedName)) {
      if (arrayType && isSet(qualifiedName)) {
        logger.error("@ArrayType is not valid on Set<T> Kotlin xlang fields", owner)
        return null
      }
      val argument = requiredArgument(type, 0, owner) ?: return null
      val argumentType = argument.type?.resolve()
      val element =
        if (argumentType == null) {
          if (isSet(qualifiedName)) {
            logger.error(
              "Set<*> and raw Set declarations are not supported in Kotlin xlang schemas",
              owner
            )
            return null
          }
          dynamicAnyNode(nullable = true)
        } else {
          parseType(argumentType, owner, arrayType = false, arrayComponent = arrayType)
            ?: return null
        }
      if (element.componentType != null) {
        logger.error(
          "Dense array types are only supported as top-level Kotlin xlang fields in phase 1",
          owner,
        )
        return null
      }
      if (arrayType && denseListArrayTypeId(element, owner) == null) {
        return null
      }
      val arrayTypeId = if (arrayType) denseListArrayTypeId(element, owner) else null
      val isList = isList(qualifiedName)
      val factory = collectionFactory(qualifiedName)
      if (!validateCollectionFactory(factory, element, owner)) {
        return null
      }
      val valueTypeName =
        if (factory == CollectionFactory.NONE) {
          kotlinSourceTypeName(type)
        } else if (isList) {
          "kotlin.collections.List<${element.valueTypeName}>"
        } else {
          "kotlin.collections.Set<${element.valueTypeName}>"
        }
      return KotlinSourceTypeNode(
        rawClassExpression =
          if (isList) "java.util.List::class.java" else "java.util.Set::class.java",
        kotlinTypeName = if (isList) "java.util.List" else "java.util.Set",
        valueTypeName = valueTypeName,
        typeName = if (isList) "java.util.List" else "java.util.Set",
        typeId = arrayTypeId ?: if (isList) "Types.LIST" else "Types.SET",
        nullable = nullable,
        trackingRef = false,
        primitive = false,
        unsigned = false,
        collectionFactory = factory,
        typeArguments = listOf(element),
      )
    }
    if (isMap(qualifiedName)) {
      if (arrayType) {
        logger.error("@ArrayType is not valid on Map<K, V> Kotlin xlang fields", owner)
        return null
      }
      val keyArgument = requiredArgument(type, 0, owner) ?: return null
      val valueArgument = requiredArgument(type, 1, owner) ?: return null
      val keyType = keyArgument.type?.resolve()
      if (keyType == null) {
        logger.error(
          "Map<*, T>, Map<*, *>, and raw Map declarations are not supported in Kotlin xlang schemas",
          owner
        )
        return null
      }
      if (keyType.nullability == Nullability.NULLABLE) {
        logger.error("Kotlin xlang map keys must be non-null", owner)
        return null
      }
      val valueType = valueArgument.type?.resolve()
      val key = parseType(keyType, owner, arrayType = false) ?: return null
      if (!isValidMapKey(key)) {
        logger.error(
          "Kotlin xlang map keys must be non-null scalar or string schema types",
          owner,
        )
        return null
      }
      val value =
        valueType?.let { parseType(it, owner, arrayType = false) }
          ?: dynamicAnyNode(nullable = true)
      if (key.componentType != null || value.componentType != null) {
        logger.error(
          "Dense array types are only supported as top-level Kotlin xlang fields in phase 1",
          owner,
        )
        return null
      }
      val factory = collectionFactory(qualifiedName)
      if (!validateMapFactory(factory, value, owner)) {
        return null
      }
      val valueTypeName =
        if (factory == CollectionFactory.NONE) {
          kotlinSourceTypeName(type)
        } else {
          "kotlin.collections.Map<${key.valueTypeName}, ${value.valueTypeName}>"
        }
      return KotlinSourceTypeNode(
        rawClassExpression = rawMapClassExpression(qualifiedName),
        kotlinTypeName = rawMapKotlinTypeName(qualifiedName),
        valueTypeName = valueTypeName,
        typeName = rawMapTypeName(qualifiedName),
        typeId = "Types.MAP",
        nullable = nullable,
        trackingRef = false,
        primitive = false,
        unsigned = false,
        collectionFactory = factory,
        typeArguments = listOf(key, value),
      )
    }
    if (qualifiedName == "kotlin.Char") {
      logger.error("Char is not supported in Fory xlang mode", owner)
      return null
    }
    if (qualifiedName == null) {
      logger.error("Unsupported anonymous Kotlin type in Fory xlang schema", owner)
      return null
    }
    if (!hasDeclarationAnnotation(declaration, FORY_STRUCT)) {
      logger.error(
        "Unsupported Kotlin xlang field type $qualifiedName. Use a supported scalar, dense array, collection/map shape, or a @ForyStruct type",
        owner,
      )
      return null
    }
    if (declaration.parentDeclaration is KSClassDeclaration) {
      logger.error(
        "Nested Kotlin @ForyStruct field type $qualifiedName is not supported by phase 1 Kotlin KSP xlang serializers",
        owner,
      )
      return null
    }
    return KotlinSourceTypeNode(
      rawClassExpression = "${qualifiedName}::class.java",
      kotlinTypeName = declaration.simpleName.asString(),
      valueTypeName = nullableValueType(qualifiedName, nullable),
      typeName = qualifiedName,
      typeId = null,
      nullable = nullable,
      trackingRef = false,
      primitive = false,
      unsigned = false,
      nestedCompatibleStruct = true,
    )
  }

  private fun requiredArgument(type: KSType, index: Int, owner: KSAnnotated): KSTypeArgument? {
    if (type.arguments.size <= index) {
      logger.error("Collection and map fields must declare concrete type arguments", owner)
      return null
    }
    return type.arguments[index]
  }

  private fun encodingAnnotation(type: KSType, owner: KSAnnotated): Encoding? {
    val encodings =
      type.annotations
        .mapNotNull {
          when {
            isAnnotation(it, FIXED) -> Encoding.Fixed
            isAnnotation(it, VAR_INT) -> Encoding.VarInt
            isAnnotation(it, TAGGED) -> Encoding.Tagged
            else -> null
          }
        }
        .toList()
    if (encodings.size > 1) {
      logger.error("Only one Fory Kotlin encoding annotation is allowed on a type use", owner)
      return Encoding.Invalid
    }
    return encodings.firstOrNull()
  }

  private fun hasRefAnnotation(type: KSType, owner: KSAnnotated): Boolean? {
    val refs = type.annotations.filter { isAnnotation(it, REF) }.toList()
    if (refs.size > 1) {
      logger.error("Kotlin xlang field types must not repeat @Ref", owner)
      return null
    }
    return refs.isNotEmpty()
  }

  private fun scalarType(
    qualifiedName: String?,
    encoding: Encoding?,
    owner: KSAnnotated,
    arrayComponent: Boolean,
  ): ScalarType? {
    if (arrayComponent && encoding != null) {
      logger.error("Encoding annotations are not valid on dense array elements", owner)
      return null
    }
    return when (qualifiedName) {
      "kotlin.Boolean" ->
        scalar("Boolean::class.javaPrimitiveType!!", "Boolean", "boolean", "Types.BOOL", true)
      "kotlin.Byte" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar("Byte::class.javaPrimitiveType!!", "Byte", "byte", "Types.INT8", true)
      }
      "kotlin.Short" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar("Short::class.javaPrimitiveType!!", "Short", "short", "Types.INT16", true)
      }
      "kotlin.Int" ->
        scalar(
          "Int::class.javaPrimitiveType!!",
          "Int",
          "int",
          if (arrayComponent) "Types.INT32" else int32TypeId(encoding, false, owner) ?: return null,
          true
        )
      "kotlin.Long" ->
        scalar(
          "Long::class.javaPrimitiveType!!",
          "Long",
          "long",
          if (arrayComponent) "Types.INT64" else int64TypeId(encoding, false, owner) ?: return null,
          true
        )
      "kotlin.UByte" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar(
          "Byte::class.javaPrimitiveType!!",
          "UByte",
          "byte",
          "Types.UINT8",
          false,
          unsigned = true
        )
      }
      "kotlin.UShort" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar(
          "Short::class.javaPrimitiveType!!",
          "UShort",
          "short",
          "Types.UINT16",
          false,
          unsigned = true
        )
      }
      "kotlin.UInt" ->
        scalar(
          "Int::class.javaPrimitiveType!!",
          "UInt",
          "int",
          if (arrayComponent) "Types.UINT32" else int32TypeId(encoding, true, owner) ?: return null,
          false,
          unsigned = true
        )
      "kotlin.ULong" ->
        scalar(
          "Long::class.javaPrimitiveType!!",
          "ULong",
          "long",
          if (arrayComponent) "Types.UINT64" else int64TypeId(encoding, true, owner) ?: return null,
          false,
          unsigned = true
        )
      "kotlin.Float" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar("Float::class.javaPrimitiveType!!", "Float", "float", "Types.FLOAT32", true)
      }
      "kotlin.Double" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar("Double::class.javaPrimitiveType!!", "Double", "double", "Types.FLOAT64", true)
      }
      "kotlin.String" -> {
        if (!validateNoEncoding(encoding, owner)) return null
        scalar("String::class.java", "String", "java.lang.String", "Types.STRING", false)
      }
      else -> UnsupportedScalar
    }
  }

  private fun validateNoEncoding(encoding: Encoding?, owner: KSAnnotated): Boolean {
    if (encoding != null) {
      logger.error("${encoding.sourceName} is not valid on this type use", owner)
      return false
    }
    return true
  }

  private fun int32TypeId(encoding: Encoding?, unsigned: Boolean, owner: KSAnnotated): String? {
    return when (encoding) {
      Encoding.Fixed -> if (unsigned) "Types.UINT32" else "Types.INT32"
      Encoding.VarInt,
      null -> if (unsigned) "Types.VAR_UINT32" else "Types.VARINT32"
      Encoding.Tagged -> {
        logger.error("@Tagged is only valid on Long and ULong", owner)
        null
      }
      Encoding.Invalid -> null
    }
  }

  private fun int64TypeId(encoding: Encoding?, unsigned: Boolean, owner: KSAnnotated): String? {
    return when (encoding) {
      Encoding.Fixed -> if (unsigned) "Types.UINT64" else "Types.INT64"
      Encoding.VarInt,
      null -> if (unsigned) "Types.VAR_UINT64" else "Types.VARINT64"
      Encoding.Tagged -> if (unsigned) "Types.TAGGED_UINT64" else "Types.TAGGED_INT64"
      Encoding.Invalid -> null
    }
  }

  private fun dynamicAnyNode(nullable: Boolean): KotlinSourceTypeNode =
    KotlinSourceTypeNode(
      rawClassExpression = "Any::class.java",
      kotlinTypeName = "Any?",
      valueTypeName = "Any?",
      typeName = "java.lang.Object",
      typeId = "Types.UNKNOWN",
      nullable = nullable,
      trackingRef = false,
      primitive = false,
      unsigned = false,
    )

  private fun scalar(
    rawClassExpression: String,
    kotlinTypeName: String,
    typeName: String,
    typeId: String,
    primitive: Boolean,
    unsigned: Boolean = false,
  ): ScalarType =
    ScalarType(
      rawClassExpression,
      kotlinTypeName,
      typeName,
      typeId,
      primitive,
      unsigned,
    )

  private fun nullableValueType(typeName: String, nullable: Boolean): String =
    if (nullable && !typeName.endsWith("?")) "$typeName?" else typeName

  private fun scalarRawClassExpression(scalar: ScalarType, nullable: Boolean): String {
    if (scalar.unsigned) {
      return when (scalar.kotlinTypeName) {
        "UByte" -> "UByte::class.java"
        "UShort" -> "UShort::class.java"
        "UInt" -> "UInt::class.java"
        "ULong" -> "ULong::class.java"
        else -> scalar.rawClassExpression
      }
    }
    if (!nullable) {
      return scalar.rawClassExpression
    }
    return when (scalar.kotlinTypeName) {
      "Boolean" -> "Boolean::class.javaObjectType"
      "Byte" -> "Byte::class.javaObjectType"
      "Short" -> "Short::class.javaObjectType"
      "Int" -> "Int::class.javaObjectType"
      "Long" -> "Long::class.javaObjectType"
      "Float" -> "Float::class.javaObjectType"
      "Double" -> "Double::class.javaObjectType"
      else -> scalar.rawClassExpression
    }
  }

  private fun isValidMapKey(type: KotlinSourceTypeNode): Boolean {
    if (type.nullable || type.typeArguments.isNotEmpty() || type.componentType != null) {
      return false
    }
    return when (type.typeId) {
      "Types.BOOL",
      "Types.INT8",
      "Types.UINT8",
      "Types.INT16",
      "Types.UINT16",
      "Types.INT32",
      "Types.VARINT32",
      "Types.UINT32",
      "Types.VAR_UINT32",
      "Types.INT64",
      "Types.VARINT64",
      "Types.TAGGED_INT64",
      "Types.UINT64",
      "Types.VAR_UINT64",
      "Types.TAGGED_UINT64",
      "Types.STRING" -> true
      else -> false
    }
  }

  private fun denseListArrayTypeId(type: KotlinSourceTypeNode, owner: KSAnnotated): String? {
    if (type.nullable || type.typeArguments.isNotEmpty() || type.componentType != null) {
      logger.error("@ArrayType List<T> requires a non-null bool or numeric element type", owner)
      return null
    }
    return when (type.typeId) {
      "Types.BOOL" -> "Types.BOOL_ARRAY"
      "Types.INT8" -> "Types.INT8_ARRAY"
      "Types.UINT8" -> "Types.UINT8_ARRAY"
      "Types.INT16" -> "Types.INT16_ARRAY"
      "Types.UINT16" -> "Types.UINT16_ARRAY"
      "Types.INT32" -> "Types.INT32_ARRAY"
      "Types.UINT32" -> "Types.UINT32_ARRAY"
      "Types.INT64" -> "Types.INT64_ARRAY"
      "Types.UINT64" -> "Types.UINT64_ARRAY"
      "Types.FLOAT32" -> "Types.FLOAT32_ARRAY"
      "Types.FLOAT64" -> "Types.FLOAT64_ARRAY"
      else -> {
        logger.error("@ArrayType List<T> requires a non-null bool or numeric element type", owner)
        null
      }
    }
  }

  private fun validateCollectionFactory(
    factory: CollectionFactory,
    element: KotlinSourceTypeNode,
    owner: KSAnnotated,
  ): Boolean {
    if (
      factory == CollectionFactory.TREE_SET || factory == CollectionFactory.CONCURRENT_SKIP_LIST_SET
    ) {
      if (!isValidMapKey(element)) {
        logger.error(
          "TreeSet and ConcurrentSkipListSet fields require non-null scalar or string elements because generated xlang serializers reconstruct them with natural ordering",
          owner,
        )
        return false
      }
    }
    return true
  }

  private fun validateMapFactory(
    factory: CollectionFactory,
    value: KotlinSourceTypeNode,
    owner: KSAnnotated,
  ): Boolean {
    if (
      factory == CollectionFactory.CONCURRENT_HASH_MAP ||
        factory == CollectionFactory.CONCURRENT_SKIP_LIST_MAP
    ) {
      if (value.nullable) {
        logger.error(
          "ConcurrentHashMap and ConcurrentSkipListMap fields require non-null values in Kotlin xlang schemas",
          owner,
        )
        return false
      }
    }
    return true
  }

  private fun denseArrayElement(qualifiedName: String?): ScalarType? =
    when (qualifiedName) {
      "kotlin.BooleanArray" ->
        scalar("Boolean::class.javaPrimitiveType!!", "Boolean", "boolean", "Types.BOOL", true)
          .copy(arrayTypeId = "Types.BOOL_ARRAY")
      "kotlin.ByteArray" ->
        scalar("Byte::class.javaPrimitiveType!!", "Byte", "byte", "Types.INT8", true)
          .copy(arrayTypeId = "Types.INT8_ARRAY")
      "kotlin.ShortArray" ->
        scalar("Short::class.javaPrimitiveType!!", "Short", "short", "Types.INT16", true)
          .copy(arrayTypeId = "Types.INT16_ARRAY")
      "kotlin.IntArray" ->
        scalar("Int::class.javaPrimitiveType!!", "Int", "int", "Types.INT32", true)
          .copy(arrayTypeId = "Types.INT32_ARRAY")
      "kotlin.LongArray" ->
        scalar("Long::class.javaPrimitiveType!!", "Long", "long", "Types.INT64", true)
          .copy(arrayTypeId = "Types.INT64_ARRAY")
      "kotlin.FloatArray" ->
        scalar("Float::class.javaPrimitiveType!!", "Float", "float", "Types.FLOAT32", true)
          .copy(arrayTypeId = "Types.FLOAT32_ARRAY")
      "kotlin.DoubleArray" ->
        scalar("Double::class.javaPrimitiveType!!", "Double", "double", "Types.FLOAT64", true)
          .copy(arrayTypeId = "Types.FLOAT64_ARRAY")
      "kotlin.UByteArray" ->
        scalar("Byte::class.javaPrimitiveType!!", "UByte", "byte", "Types.UINT8", false, true)
          .copy(arrayTypeId = "Types.UINT8_ARRAY")
      "kotlin.UShortArray" ->
        scalar("Short::class.javaPrimitiveType!!", "UShort", "short", "Types.UINT16", false, true)
          .copy(arrayTypeId = "Types.UINT16_ARRAY")
      "kotlin.UIntArray" ->
        scalar("Int::class.javaPrimitiveType!!", "UInt", "int", "Types.UINT32", false, true)
          .copy(arrayTypeId = "Types.UINT32_ARRAY")
      "kotlin.ULongArray" ->
        scalar("Long::class.javaPrimitiveType!!", "ULong", "long", "Types.UINT64", false, true)
          .copy(arrayTypeId = "Types.UINT64_ARRAY")
      else -> null
    }

  private fun isList(qualifiedName: String?): Boolean =
    qualifiedName == "kotlin.collections.List" ||
      qualifiedName == "kotlin.collections.MutableList" ||
      qualifiedName == "kotlin.collections.ArrayList" ||
      qualifiedName == "java.util.List" ||
      qualifiedName == "java.util.ArrayList" ||
      qualifiedName == "java.util.LinkedList" ||
      qualifiedName == "java.util.concurrent.CopyOnWriteArrayList"

  private fun isSet(qualifiedName: String?): Boolean =
    qualifiedName == "kotlin.collections.Set" ||
      qualifiedName == "kotlin.collections.MutableSet" ||
      qualifiedName == "kotlin.collections.HashSet" ||
      qualifiedName == "kotlin.collections.LinkedHashSet" ||
      qualifiedName == "java.util.Set" ||
      qualifiedName == "java.util.HashSet" ||
      qualifiedName == "java.util.LinkedHashSet" ||
      qualifiedName == "java.util.TreeSet" ||
      qualifiedName == "java.util.concurrent.CopyOnWriteArraySet" ||
      qualifiedName == "java.util.concurrent.ConcurrentSkipListSet"

  private fun isMap(qualifiedName: String?): Boolean =
    qualifiedName == "kotlin.collections.Map" ||
      qualifiedName == "kotlin.collections.MutableMap" ||
      qualifiedName == "java.util.Map" ||
      qualifiedName == "java.util.HashMap" ||
      qualifiedName == "java.util.LinkedHashMap" ||
      qualifiedName == "java.util.TreeMap" ||
      qualifiedName == "java.util.concurrent.ConcurrentHashMap" ||
      qualifiedName == "java.util.concurrent.ConcurrentSkipListMap"

  private fun collectionFactory(qualifiedName: String?): CollectionFactory =
    when (qualifiedName) {
      "kotlin.collections.MutableList" -> CollectionFactory.MUTABLE_LIST
      "java.util.ArrayList" -> CollectionFactory.ARRAY_LIST
      "kotlin.collections.ArrayList" -> CollectionFactory.ARRAY_LIST
      "java.util.LinkedList" -> CollectionFactory.LINKED_LIST
      "java.util.concurrent.CopyOnWriteArrayList" -> CollectionFactory.COPY_ON_WRITE_ARRAY_LIST
      "kotlin.collections.MutableSet" -> CollectionFactory.MUTABLE_SET
      "java.util.HashSet" -> CollectionFactory.HASH_SET
      "kotlin.collections.HashSet" -> CollectionFactory.HASH_SET
      "java.util.LinkedHashSet" -> CollectionFactory.LINKED_HASH_SET
      "kotlin.collections.LinkedHashSet" -> CollectionFactory.LINKED_HASH_SET
      "java.util.TreeSet" -> CollectionFactory.TREE_SET
      "java.util.concurrent.CopyOnWriteArraySet" -> CollectionFactory.COPY_ON_WRITE_ARRAY_SET
      "java.util.concurrent.ConcurrentSkipListSet" -> CollectionFactory.CONCURRENT_SKIP_LIST_SET
      "kotlin.collections.MutableMap" -> CollectionFactory.MUTABLE_MAP
      "java.util.HashMap" -> CollectionFactory.HASH_MAP
      "java.util.LinkedHashMap" -> CollectionFactory.LINKED_HASH_MAP
      "java.util.TreeMap" -> CollectionFactory.TREE_MAP
      "java.util.concurrent.ConcurrentHashMap" -> CollectionFactory.CONCURRENT_HASH_MAP
      "java.util.concurrent.ConcurrentSkipListMap" -> CollectionFactory.CONCURRENT_SKIP_LIST_MAP
      else -> CollectionFactory.NONE
    }

  private fun rawCollectionClassExpression(qualifiedName: String?): String =
    when (qualifiedName) {
      "kotlin.collections.Set",
      "kotlin.collections.MutableSet",
      "java.util.Set" -> "java.util.Set::class.java"
      else -> "java.util.List::class.java"
    }

  private fun rawCollectionKotlinTypeName(qualifiedName: String?): String =
    if (isSet(qualifiedName)) "java.util.Set" else "java.util.List"

  private fun rawCollectionTypeName(qualifiedName: String?): String =
    if (isSet(qualifiedName)) "java.util.Set" else "java.util.List"

  private fun rawMapClassExpression(qualifiedName: String?): String = "java.util.Map::class.java"

  private fun rawMapKotlinTypeName(qualifiedName: String?): String = "java.util.Map"

  private fun rawMapTypeName(qualifiedName: String?): String = "java.util.Map"

  private fun denseArrayRawClass(qualifiedName: String): String =
    when (qualifiedName) {
      "kotlin.BooleanArray" -> "BooleanArray::class.java"
      "kotlin.ByteArray" -> "ByteArray::class.java"
      "kotlin.ShortArray" -> "ShortArray::class.java"
      "kotlin.IntArray" -> "IntArray::class.java"
      "kotlin.LongArray" -> "LongArray::class.java"
      "kotlin.FloatArray" -> "FloatArray::class.java"
      "kotlin.DoubleArray" -> "DoubleArray::class.java"
      "kotlin.UByteArray" -> "UByteArray::class.java"
      "kotlin.UShortArray" -> "UShortArray::class.java"
      "kotlin.UIntArray" -> "UIntArray::class.java"
      "kotlin.ULongArray" -> "ULongArray::class.java"
      else -> error(qualifiedName)
    }

  private fun denseArrayKotlinName(qualifiedName: String): String =
    qualifiedName.substringAfterLast('.')

  private fun denseArrayTypeName(qualifiedName: String): String =
    when (qualifiedName) {
      "kotlin.BooleanArray" -> "boolean[]"
      "kotlin.ByteArray" -> "byte[]"
      "kotlin.ShortArray" -> "short[]"
      "kotlin.IntArray" -> "int[]"
      "kotlin.LongArray" -> "long[]"
      "kotlin.FloatArray" -> "float[]"
      "kotlin.DoubleArray" -> "double[]"
      else -> qualifiedName
    }

  private fun kotlinSourceTypeName(type: KSType): String {
    val qualifiedName =
      type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString()
    val base =
      when (qualifiedName) {
        "kotlin.collections.List",
        "java.util.List" -> "List"
        "kotlin.collections.MutableList" -> "MutableList"
        "kotlin.collections.Set",
        "java.util.Set" -> "Set"
        "kotlin.collections.MutableSet" -> "MutableSet"
        "kotlin.collections.Map",
        "java.util.Map" -> "Map"
        "kotlin.collections.MutableMap" -> "MutableMap"
        "java.util.ArrayList",
        "kotlin.collections.ArrayList" -> "java.util.ArrayList"
        "java.util.LinkedList" -> "java.util.LinkedList"
        "java.util.concurrent.CopyOnWriteArrayList" -> "java.util.concurrent.CopyOnWriteArrayList"
        "java.util.HashSet",
        "kotlin.collections.HashSet" -> "java.util.HashSet"
        "java.util.LinkedHashSet",
        "kotlin.collections.LinkedHashSet" -> "java.util.LinkedHashSet"
        "java.util.TreeSet" -> "java.util.TreeSet"
        "java.util.concurrent.CopyOnWriteArraySet" -> "java.util.concurrent.CopyOnWriteArraySet"
        "java.util.concurrent.ConcurrentSkipListSet" -> "java.util.concurrent.ConcurrentSkipListSet"
        "java.util.HashMap" -> "java.util.HashMap"
        "java.util.LinkedHashMap" -> "java.util.LinkedHashMap"
        "java.util.TreeMap" -> "java.util.TreeMap"
        "java.util.concurrent.ConcurrentHashMap" -> "java.util.concurrent.ConcurrentHashMap"
        "java.util.concurrent.ConcurrentSkipListMap" -> "java.util.concurrent.ConcurrentSkipListMap"
        else -> type.declaration.simpleName.asString()
      }
    val arguments =
      if (type.arguments.isEmpty()) ""
      else
        type.arguments.joinToString(", ", "<", ">") { arg ->
          arg.type?.resolve()?.let { kotlinSourceTypeName(it) } ?: "*"
        }
    return base + arguments + if (type.nullability == Nullability.NULLABLE) "?" else ""
  }

  private fun hasDeclarationAnnotation(declaration: KSDeclaration, qualifiedName: String): Boolean =
    declaration.annotations.any { isAnnotation(it, qualifiedName) }

  private fun isAnnotation(annotation: KSAnnotation, qualifiedName: String): Boolean =
    annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName

  private fun escapedResourceName(targetBinaryName: String): String {
    val builder = StringBuilder(targetBinaryName.length + 32)
    for (char in targetBinaryName) {
      when {
        char == '.' -> builder.append('.')
        char == '$' -> builder.append('_')
        char == '_' -> builder.append("_u_")
        Character.isJavaIdentifierPart(char) -> builder.append(char)
        else -> builder.append("_x").append(char.code.toString(16)).append('_')
      }
    }
    return builder.toString()
  }

  private sealed class Encoding(val sourceName: String) {
    object Fixed : Encoding("@Fixed")

    object VarInt : Encoding("@VarInt")

    object Tagged : Encoding("@Tagged")

    object Invalid : Encoding("<invalid>")
  }

  private data class ScalarType(
    val rawClassExpression: String,
    val kotlinTypeName: String,
    val typeName: String,
    val typeId: String,
    val primitive: Boolean,
    val unsigned: Boolean = false,
    val arrayTypeId: String = typeId,
  )

  private companion object {
    const val FORY_STRUCT = "org.apache.fory.annotation.ForyStruct"
    const val FORY_FIELD = "org.apache.fory.annotation.ForyField"
    const val ARRAY_TYPE = "org.apache.fory.annotation.ArrayType"
    const val NULLABLE = "org.apache.fory.annotation.Nullable"
    const val REF = "org.apache.fory.annotation.Ref"
    const val FIXED = "org.apache.fory.kotlin.Fixed"
    const val VAR_INT = "org.apache.fory.kotlin.VarInt"
    const val TAGGED = "org.apache.fory.kotlin.Tagged"
    val UnsupportedScalar = ScalarType("", "", "", "", false)
  }
}

internal fun escapeBinarySimpleName(binarySimpleName: String): String {
  val builder = StringBuilder(binarySimpleName.length + 32)
  var index = 0
  while (index < binarySimpleName.length) {
    val codePoint = Character.codePointAt(binarySimpleName, index)
    when {
      codePoint == '$'.code -> builder.append('_')
      codePoint == '_'.code -> builder.append("_u_")
      Character.isJavaIdentifierPart(codePoint) -> builder.appendCodePoint(codePoint)
      else -> builder.append("_x").append(codePoint.toString(16)).append('_')
    }
    index += Character.charCount(codePoint)
  }
  return builder.toString()
}
