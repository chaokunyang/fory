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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import org.apache.fory.codegen.GeneratedClassNames

internal const val JSON_TYPE: String = "org.apache.fory.json.annotation.JsonType"
internal const val JSON_MIXIN: String = "org.apache.fory.json.annotation.JsonMixin"
private const val JSON_SUB_TYPES = "org.apache.fory.json.annotation.JsonSubTypes"
private const val JSON_ANY_SETTER = "org.apache.fory.json.annotation.JsonAnySetter"
private const val JSON_VALIDATOR = "org.apache.fory.json.annotation.JsonValidator"
private const val JSON_CREATOR = "org.apache.fory.json.annotation.JsonCreator"
private const val JSON_MIXIN_REMOVE = "org.apache.fory.json.annotation.JsonMixinRemove"
private const val JSON_ANNOTATION_PACKAGE = "org.apache.fory.json.annotation."
private const val DEFAULT_MARKER = "Lkotlin/jvm/internal/DefaultConstructorMarker;"

/** KSP-side producer for immutable operations of one real Kotlin source declaration. */
@OptIn(KspExperimental::class)
internal class KspModelBuilder(
  private val resolver: Resolver,
  private val logger: KSPLogger,
) {
  fun direct(target: KSClassDeclaration): JsonModel? {
    if (target.origin != Origin.KOTLIN || target.containingFile == null) return null
    if (hasAnnotation(target, JSON_MIXIN)) return null
    return kotlinModel(target, null)
  }

  fun mixin(source: KSClassDeclaration): JsonModel? {
    if (source.containingFile == null || source.origin !in SOURCE_ORIGINS) return null
    val target = mixinTarget(source) ?: return null
    if (source.origin == Origin.JAVA && !isKotlin(target.origin)) return null
    if (!isNameable(source)) return fail(source, "@JsonMixin source must be public or internal")
    return if (isKotlin(target.origin)) {
      kotlinModel(target, source)
    } else {
      javaModel(target, source)
    }
  }

  private fun javaModel(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration,
  ): JsonModel? {
    val targetName = binaryName(target) ?: return fail(target, "@JsonMixin target must be named")
    val generatedPackage = mixin.packageName.asString()
    if (!isAccessibleFrom(target, generatedPackage)) {
      return fail(target, "@JsonMixin target is not accessible from the Mixin package")
    }
    val closed = effectiveTypeAnnotation(target, mixin, JSON_SUB_TYPES)
    val concrete =
      target.classKind == ClassKind.CLASS &&
        Modifier.ABSTRACT !in target.modifiers &&
        Modifier.SEALED !in target.modifiers
    val generateCompanion = concrete && !closed
    val members =
      if (generateCompanion) javaMembers(target, generatedPackage, mixin) else emptyList()
    val anySetter = if (generateCompanion) javaAnySetter(target, mixin) else null
    val validators = if (generateCompanion) validators(target, mixin) else emptyList()
    val creator = if (generateCompanion) javaCreator(target, mixin) else null
    return JsonModel(
      packageName = generatedPackage,
      targetBinaryName = targetName,
      targetSourceName = target.qualifiedName?.asString() ?: targetName.replace('$', '.'),
      companionSimpleName = generatedSimpleName(targetName, mixin),
      operationSimpleName = generatedSimpleName(targetName, mixin) + "_Operations",
      generateCompanion = generateCompanion,
      members = members,
      anySetter = anySetter,
      validators = validators,
      creator = creator,
      singleton = false,
      valueClass = null,
      mixinBinaryName = binaryName(mixin),
      originatingFiles = originatingFiles(target, mixin),
      retainedAnnotations = annotations(target) + annotations(mixin),
      retainedTypes = (annotationTypes(target) + annotationTypes(mixin)) - targetName,
      mixinMembers = mixinMembers(mixin),
    )
  }

  private fun generatedSimpleName(
    targetBinaryName: String,
    mixin: KSClassDeclaration?,
  ): String {
    if (mixin == null) {
      return GeneratedClassNames.withSuffix(targetBinaryName, "_ForyJsonCodec")
        .substringAfterLast('.')
    }
    val mixinName = binaryName(mixin)!!
    return GeneratedClassNames.escapeBinarySimpleName(mixinName.substringAfterLast('.')) +
      "_ForyJsonMixin_" +
      GeneratedClassNames.escapeBinarySimpleName(targetBinaryName) +
      "_ForyJsonCodec"
  }

  private fun mixinTarget(source: KSClassDeclaration): KSClassDeclaration? {
    val annotation =
      source.annotations.firstOrNull { annotationName(it) == JSON_MIXIN }
        ?: return fail(source, "Missing @JsonMixin declaration")
    val value = annotation.arguments.firstOrNull { it.name?.asString() == "target" }?.value
    val type = value as? KSType ?: return fail(source, "@JsonMixin must declare a target type")
    if (type.isError) return null
    return actual(type.declaration) as? KSClassDeclaration
      ?: fail(source, "@JsonMixin target must be a declared type")
  }

  private fun originatingFiles(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration?,
  ) = listOfNotNull(mixin?.containingFile, target.containingFile).distinct()

  private fun isKotlin(origin: Origin): Boolean =
    origin == Origin.KOTLIN || origin == Origin.KOTLIN_LIB

  private fun isNameable(declaration: KSClassDeclaration): Boolean {
    var current: KSClassDeclaration? = declaration
    while (current != null) {
      if (Modifier.PRIVATE in current.modifiers || Modifier.PROTECTED in current.modifiers) {
        return false
      }
      current = current.parentDeclaration as? KSClassDeclaration
    }
    return true
  }

  private fun isAccessibleFrom(declaration: KSClassDeclaration, packageName: String): Boolean {
    var current: KSClassDeclaration? = declaration
    while (current != null) {
      val modifiers = resolver.effectiveJavaModifiers(current)
      if (Modifier.PRIVATE in modifiers) return false
      if (Modifier.PUBLIC !in modifiers && current.packageName.asString() != packageName)
        return false
      current = current.parentDeclaration as? KSClassDeclaration
    }
    return true
  }

  private fun kotlinModel(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration?,
  ): JsonModel? {
    val targetName = binaryName(target) ?: return fail(target, "@JsonType target must be named")
    if (!isNameable(target)) {
      return fail(target, "Kotlin JSON target must be public or internal")
    }
    if (Modifier.INNER in target.modifiers) {
      return fail(target, "Kotlin JSON does not support inner classes")
    }
    val singleton = target.classKind == ClassKind.OBJECT && !target.isCompanionObject
    if (singleton) {
      val state =
        target.declarations.filterIsInstance<KSPropertyDeclaration>().firstOrNull {
          Modifier.CONST !in it.modifiers
        }
      if (state != null) return fail(state, "Kotlin JSON object $targetName is stateful")
    }
    val closed =
      if (mixin == null) hasAnnotation(target, JSON_SUB_TYPES)
      else effectiveTypeAnnotation(target, mixin, JSON_SUB_TYPES)
    val concrete =
      target.classKind == ClassKind.CLASS &&
        Modifier.ABSTRACT !in target.modifiers &&
        Modifier.SEALED !in target.modifiers
    val valueClass = Modifier.VALUE in target.modifiers
    val generateCompanion = singleton || concrete && !closed
    val generatedPackage = mixin?.packageName?.asString() ?: target.packageName.asString()
    val companion = generatedSimpleName(targetName, mixin)
    val valueOperations = if (generateCompanion && valueClass) valueClass(target) else null
    val creator =
      if (generateCompanion && !singleton && !valueClass) kotlinCreator(target, mixin) else null
    val members =
      if (generateCompanion && !singleton && !valueClass) {
        members(target, creator) ?: return null
      } else {
        emptyList()
      }
    val anySetter = effectiveMethods(target, mixin, JSON_ANY_SETTER)
    if (generateCompanion && creator != null && anySetter.isNotEmpty()) {
      return fail(
        anySetter.first(),
        "@JsonAnySetter is not supported on a Kotlin constructor model"
      )
    }
    val validators = validators(target, mixin)
    val files = originatingFiles(target, mixin)
    return JsonModel(
      packageName = generatedPackage,
      targetBinaryName = targetName,
      targetSourceName = targetName.replace('$', '.'),
      companionSimpleName = companion,
      operationSimpleName = companion + "_Operations",
      generateCompanion = generateCompanion,
      members = members,
      anySetter = null,
      validators = validators,
      creator = creator,
      singleton = singleton,
      valueClass = valueOperations,
      mixinBinaryName = mixin?.let(::binaryName),
      originatingFiles = files,
      retainedAnnotations = annotations(target) + (mixin?.let(::annotations) ?: emptySet()),
      retainedTypes =
        (annotationTypes(target) + (mixin?.let(::annotationTypes) ?: emptySet())) - targetName,
      mixinMembers = mixin?.let(::mixinMembers) ?: emptyList(),
    )
  }

  private fun valueClass(target: KSClassDeclaration): ValueClassOperations? {
    val layers = ArrayList<ValueClassLayer>()
    var declaration = target
    var occurrenceExpression = "type"
    var substitutions =
      target.typeParameters
        .mapIndexed { index, parameter -> parameter to "type.getTypeArguments().get($index)" }
        .toMap()
    var terminalType: KSType
    var terminalExpression: String
    while (true) {
      val constructor =
        declaration.primaryConstructor
          ?: return fail(declaration, "Kotlin value class must have a primary constructor")
      if (constructor.parameters.size != 1) {
        return fail(constructor, "Kotlin value class must have one underlying parameter")
      }
      val underlying = constructor.parameters.single().type.resolve()
      val underlyingExpression = typeExpression(underlying, substitutions) ?: return null
      val rawDescriptor =
        descriptor(constructor)
          ?: return fail(
            constructor,
            "Cannot map Kotlin value-class constructor to a JVM descriptor"
          )
      val normalized = normalizeConstructorDescriptor(rawDescriptor, listOf(carrier(underlying)))
      val method = parseMethodDescriptor(normalized)
      if (
        method.parameters.size != 1 ||
          (method.result != "V" && method.result != method.parameters.single().descriptor)
      ) {
        return fail(constructor, "Kotlin value-class constructor-impl has an invalid JVM shape")
      }
      val carrier = method.parameters.single()
      layers +=
        ValueClassLayer(
          ownerBinaryName = binaryName(declaration)!!,
          carrierType = carrier,
          occurrenceTypeExpression = occurrenceExpression,
          underlyingTypeExpression = underlyingExpression,
        )
      val underlyingDeclaration = actual(underlying.declaration) as? KSClassDeclaration
      if (underlyingDeclaration == null || Modifier.VALUE !in underlyingDeclaration.modifiers) {
        terminalType = underlying
        terminalExpression = underlyingExpression
        break
      }
      occurrenceExpression = underlyingExpression
      substitutions =
        underlyingDeclaration.typeParameters
          .mapIndexed { index, parameter ->
            val argument =
              underlying.arguments.getOrNull(index)
                ?: return fail(
                  underlyingDeclaration,
                  "Nested value class needs exact type arguments"
                )
            val argumentType =
              argument.type?.resolve()
                ?: return fail(argument, "Star-projected value-class arguments are unsupported")
            parameter to (typeExpression(argumentType, substitutions) ?: return null)
          }
          .toMap()
      declaration = underlyingDeclaration
    }
    return ValueClassOperations(
      layers = layers,
      terminalType = carrier(terminalType),
      terminalTypeExpression = terminalExpression,
    )
  }

  private fun typeExpression(
    type: KSType,
    substitutions: Map<KSTypeParameter, String>,
    covariant: Boolean = false,
  ): String? {
    val declaration = actual(type.declaration)
    if (declaration is KSTypeParameter) {
      return substitutions[declaration]
        ?: fail(declaration, "Unresolved Kotlin value-class type parameter ${declaration.name}")
    }
    val classDeclaration =
      declaration as? KSClassDeclaration
        ?: return fail(declaration, "Unsupported Kotlin value-class underlying type")
    val classLiteral = logicalClassLiteral(type, classDeclaration)
    val metadata =
      "org.apache.fory.meta.TypeExtMeta.of(" +
        "${semanticTypeId(classDeclaration)}, " +
        "${type.nullability == Nullability.NULLABLE}, false, false, $covariant)"
    if (type.arguments.isEmpty()) {
      return "org.apache.fory.reflect.TypeRef.of($classLiteral, $metadata)"
    }
    val arguments = ArrayList<String>(type.arguments.size)
    for (argument in type.arguments) {
      if (argument.variance == Variance.CONTRAVARIANT) {
        return fail(argument, "Contravariant Kotlin value-class types are unsupported")
      }
      val argumentType =
        argument.type?.resolve()
          ?: return fail(argument, "Star-projected Kotlin value-class types are unsupported")
      arguments +=
        typeExpression(argumentType, substitutions, argument.variance == Variance.COVARIANT)
          ?: return null
    }
    return "org.apache.fory.reflect.TypeRef.ofDeclaredTypeArguments(" +
      "$classLiteral, $metadata, java.util.Arrays.asList(${arguments.joinToString(", ")}), null)"
  }

  private fun logicalClassLiteral(type: KSType, declaration: KSClassDeclaration): String {
    val name = declaration.qualifiedName?.asString().orEmpty()
    if (name in PRIMITIVES) {
      val descriptor =
        if (type.nullability == Nullability.NULLABLE && name in UNSIGNED) {
          "L${name.replace('.', '/')};"
        } else if (type.nullability == Nullability.NULLABLE) {
          BOXES.getValue(PRIMITIVES.getValue(name))
        } else {
          PRIMITIVES.getValue(name)
        }
      return JvmType(descriptor).classLiteral
    }
    ARRAYS[name]?.let {
      return JvmType(it).classLiteral
    }
    val binary = JAVA_TYPES[name] ?: binaryName(declaration) ?: "java.lang.Object"
    return JvmType("L${binary.replace('.', '/')};").classLiteral
  }

  private fun semanticTypeId(declaration: KSClassDeclaration): String =
    when (declaration.qualifiedName?.asString()) {
      "kotlin.UByte" -> "org.apache.fory.type.Types.UINT8"
      "kotlin.UShort" -> "org.apache.fory.type.Types.UINT16"
      "kotlin.UInt" -> "org.apache.fory.type.Types.UINT32"
      "kotlin.ULong" -> "org.apache.fory.type.Types.UINT64"
      "kotlin.UByteArray" -> "org.apache.fory.type.Types.UINT8_ARRAY"
      "kotlin.UShortArray" -> "org.apache.fory.type.Types.UINT16_ARRAY"
      "kotlin.UIntArray" -> "org.apache.fory.type.Types.UINT32_ARRAY"
      "kotlin.ULongArray" -> "org.apache.fory.type.Types.UINT64_ARRAY"
      else -> "0"
    }

  private fun kotlinCreator(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration?,
  ): JsonCreator? {
    val selected = ArrayList<KSFunctionDeclaration>()
    target
      .getConstructors()
      .filter { constructor ->
        if (mixin == null) hasAnnotation(constructor, JSON_CREATOR)
        else hasEffectiveAnnotation(constructor, mixin, JSON_CREATOR)
      }
      .forEach(selected::add)
    kotlinFactories(target)
      .filter { factory ->
        if (mixin == null) hasAnnotation(factory, JSON_CREATOR)
        else hasEffectiveAnnotation(factory, mixin, JSON_CREATOR)
      }
      .forEach(selected::add)
    if (selected.size > 1) {
      return fail(selected[1], "Exactly one effective @JsonCreator is allowed")
    }
    val explicit = selected.singleOrNull()
    if (explicit != null && explicit.simpleName.asString() != "<init>") {
      return kotlinFactory(target, explicit)
    }
    val constructor =
      explicit
        ?: target.primaryConstructor
        ?: return fail(target, "Kotlin JSON model has no selected constructor")
    if (Modifier.PRIVATE in constructor.modifiers || Modifier.PROTECTED in constructor.modifiers) {
      return fail(constructor, "Kotlin JSON constructor must be public or internal")
    }
    if (constructor.parameters.any { it.isVararg }) {
      return fail(constructor, "Kotlin JSON constructor must not be vararg")
    }
    val rawDescriptor =
      descriptor(constructor)
        ?: return fail(constructor, "Cannot map Kotlin constructor to a JVM descriptor")
    val parameterTypes = constructor.parameters.map { carrier(it.type.resolve()) }
    val descriptor = normalizeConstructorDescriptor(rawDescriptor, parameterTypes)
    val parsed = parseMethodDescriptor(descriptor)
    val count = constructor.parameters.size
    val types =
      when {
        parsed.parameters.size == count -> parsed.parameters
        parsed.parameters.size == count + 1 &&
          parsed.parameters.last().descriptor == DEFAULT_MARKER -> parsed.parameters.dropLast(1)
        else -> return fail(constructor, "Kotlin constructor JVM shape is inconsistent")
      }
    var invocation = descriptor
    if (
      parsed.parameters.size == count &&
        constructor.parameters.indices.any { index ->
          isUnboxedValue(constructor.parameters[index].type.resolve(), types[index])
        }
    ) {
      invocation = appendParameters(descriptor, listOf(JvmType(DEFAULT_MARKER)))
    }
    val optional = BooleanArray(count) { constructor.parameters[it].hasDefault }
    val defaultDescriptor =
      if (optional.any { it }) {
        methodDescriptor(
          types + List((count + 31) ushr 5) { JvmType("I") } + JvmType(DEFAULT_MARKER),
          "V",
        )
      } else {
        null
      }
    val names =
      constructor.parameters.mapIndexed { index, parameter ->
        parameter.name?.asString() ?: return fail(parameter, "Unnamed constructor parameter $index")
      }
    return JsonCreator(
      parameterNames = names,
      parameterTypes = types,
      optional = optional,
      invocationOwner = binaryName(target)!!,
      invocationName = "<init>",
      invocationDescriptor = invocation,
      defaultDescriptor = defaultDescriptor,
    )
  }

  private fun kotlinFactory(
    target: KSClassDeclaration,
    factory: KSFunctionDeclaration,
  ): JsonCreator? {
    val owner =
      factory.parentDeclaration as? KSClassDeclaration
        ?: return fail(factory, "Kotlin @JsonCreator factory must be a target member")
    val companionFactory = owner.isCompanionObject
    val modifiers = resolver.effectiveJavaModifiers(factory)
    if (
      Modifier.PRIVATE in factory.modifiers ||
        Modifier.PROTECTED in factory.modifiers ||
        factory.parameters.any { it.isVararg } ||
        factory.typeParameters.isNotEmpty() ||
        factory.parameters.any { it.hasDefault } ||
        companionFactory && !hasAnnotation(factory, "kotlin.jvm.JvmStatic") ||
        !companionFactory &&
          Modifier.JAVA_STATIC !in modifiers &&
          factory.functionKind != FunctionKind.STATIC
    ) {
      return fail(factory, "Invalid Kotlin @JsonCreator static factory")
    }
    val targetName = binaryName(target)!!
    val parameterTypes = factory.parameters.map { carrier(it.type.resolve()) }
    val rawDescriptor =
      descriptor(factory)
        ?: return fail(factory, "Cannot map Kotlin @JsonCreator factory to a JVM descriptor")
    val normalized = normalizeConstructorDescriptor(rawDescriptor, parameterTypes)
    val parsed = parseMethodDescriptor(normalized)
    if (
      parsed.parameters != parameterTypes || parsed.result != "L${targetName.replace('.', '/')};"
    ) {
      return fail(factory, "Kotlin @JsonCreator factory must return its exact owner")
    }
    val names =
      factory.parameters.mapIndexed { index, parameter ->
        parameter.name?.asString() ?: return fail(parameter, "Unnamed factory parameter $index")
      }
    return JsonCreator(
      parameterNames = names,
      parameterTypes = parameterTypes,
      optional = BooleanArray(parameterTypes.size),
      invocationOwner = targetName,
      invocationName = resolver.getJvmName(factory) ?: factory.simpleName.asString(),
      invocationDescriptor = methodDescriptor(parameterTypes, parsed.result),
      defaultDescriptor = null,
    )
  }

  private fun kotlinFactories(target: KSClassDeclaration): Sequence<KSFunctionDeclaration> {
    val direct =
      target.declarations.filterIsInstance<KSFunctionDeclaration>().filter { function ->
        function.simpleName.asString() != "<init>" &&
          (function.functionKind == FunctionKind.STATIC ||
            Modifier.JAVA_STATIC in resolver.effectiveJavaModifiers(function))
      }
    val companions =
      target.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.isCompanionObject }
        .flatMap { companion ->
          companion.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
            hasAnnotation(it, "kotlin.jvm.JvmStatic")
          }
        }
    return direct + companions
  }

  private fun normalizeConstructorDescriptor(
    descriptor: String,
    sourceTypes: List<JvmType>,
  ): String {
    require(descriptor.startsWith('(')) { "Invalid Kotlin constructor descriptor $descriptor" }
    val result = StringBuilder(descriptor.length + 32).append('(')
    var offset = 1
    var parameter = 0
    while (descriptor[offset] != ')') {
      val start = offset
      while (descriptor[offset] == '[') offset++
      offset =
        if (descriptor[offset] == 'L') descriptor.indexOf(';', offset + 1) + 1 else offset + 1
      require(offset > start) { "Invalid Kotlin constructor descriptor $descriptor" }
      if (descriptor[start] == 'V') {
        require(parameter < sourceTypes.size) { "Unexpected void carrier in $descriptor" }
        result.append(sourceTypes[parameter].descriptor)
      } else {
        result.append(descriptor, start, offset)
      }
      parameter++
    }
    return result.append(descriptor.substring(offset)).toString()
  }

  private fun javaMembers(
    target: KSClassDeclaration,
    generatedPackage: String,
    mixin: KSClassDeclaration,
  ): List<JvmMember> {
    val result = linkedMapOf<String, JvmMember>()
    for (property in target.getAllProperties()) {
      if (property.origin !in JAVA_ORIGINS || !property.hasBackingField) continue
      val owner = property.parentDeclaration as? KSClassDeclaration ?: continue
      val modifiers = resolver.effectiveJavaModifiers(property)
      if (
        Modifier.JAVA_STATIC in modifiers ||
          Modifier.JAVA_TRANSIENT in modifiers ||
          !isMemberAccessible(property, owner, generatedPackage)
      ) {
        continue
      }
      val type = carrier(property.type.resolve())
      if (type.descriptor == "Ljava/lang/Class;") continue
      val member =
        JvmMember(
          MemberKind.FIELD,
          binaryName(owner)!!,
          false,
          property.simpleName.asString(),
          type.descriptor,
          Modifier.FINAL !in modifiers,
        )
      result["F#${member.ownerBinaryName}#${member.name}#${member.descriptor}"] = member
    }
    for (function in effectiveFunctions(target)) {
      val owner = function.parentDeclaration as? KSClassDeclaration ?: continue
      val modifiers = resolver.effectiveJavaModifiers(function)
      if (
        Modifier.PUBLIC !in modifiers ||
          Modifier.JAVA_STATIC in modifiers ||
          function.functionKind == FunctionKind.STATIC ||
          function.isAbstract ||
          function.parameters.any { it.isVararg } ||
          function.typeParameters.isNotEmpty() ||
          hasEffectiveAnnotation(function, mixin, JSON_ANY_SETTER)
      ) {
        continue
      }
      val name = resolver.getJvmName(function) ?: function.simpleName.asString()
      val descriptor = descriptor(function) ?: continue
      val method = parseMethodDescriptor(descriptor)
      val annotated = hasEffectiveJsonAnnotation(function, mixin)
      val kind =
        when {
          method.parameters.isEmpty() &&
            method.result != "V" &&
            method.result != "Ljava/lang/Class;" &&
            (annotated || isGetterName(name, method.result)) -> MemberKind.GETTER
          method.parameters.size == 1 &&
            method.result == "V" &&
            (annotated || name.startsWith("set") && name.length > 3) -> MemberKind.SETTER
          else -> continue
        }
      val member =
        JvmMember(
          kind,
          binaryName(owner)!!,
          owner.classKind == ClassKind.INTERFACE,
          name,
          descriptor,
        )
      val parameterKey = method.parameters.joinToString("") { it.descriptor }
      result["M#$name#$parameterKey"] = member
    }
    return result.values.sortedWith(
      compareBy(JvmMember::name, JvmMember::descriptor, JvmMember::kind)
    )
  }

  private fun isGetterName(name: String, result: String): Boolean =
    name.startsWith("get") && name.length > 3 ||
      name.startsWith("is") && name.length > 2 && (result == "Z" || result == "Ljava/lang/Boolean;")

  private fun javaAnySetter(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration,
  ): JvmAnySetter? {
    val methods = effectiveMethods(target, mixin, JSON_ANY_SETTER)
    if (methods.size > 1) {
      return fail(methods[1], "At most one effective @JsonAnySetter method is allowed")
    }
    val method = methods.singleOrNull() ?: return null
    val descriptor =
      descriptor(method) ?: return fail(method, "Cannot map @JsonAnySetter to a JVM descriptor")
    val shape = parseMethodDescriptor(descriptor)
    val modifiers = resolver.effectiveJavaModifiers(method)
    if (
      Modifier.PUBLIC !in modifiers ||
        Modifier.JAVA_STATIC in modifiers ||
        method.functionKind == FunctionKind.STATIC ||
        method.isAbstract ||
        method.parameters.any { it.isVararg } ||
        method.typeParameters.isNotEmpty() ||
        shape.parameters.size != 2 ||
        shape.parameters[0].descriptor != "Ljava/lang/String;" ||
        shape.result != "V"
    ) {
      return fail(method, "Invalid effective @JsonAnySetter method")
    }
    val owner = method.parentDeclaration as KSClassDeclaration
    return JvmAnySetter(
      binaryName(owner)!!,
      owner.classKind == ClassKind.INTERFACE,
      resolver.getJvmName(method) ?: method.simpleName.asString(),
      descriptor,
    )
  }

  private fun validators(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration?,
  ): List<JvmValidator> {
    val result = ArrayList<JvmValidator>()
    for (method in effectiveMethods(target, mixin, JSON_VALIDATOR)) {
      val descriptor = descriptor(method)
      val modifiers = resolver.effectiveJavaModifiers(method)
      if (
        descriptor != "()V" ||
          Modifier.PUBLIC !in modifiers ||
          Modifier.JAVA_STATIC in modifiers ||
          method.functionKind == FunctionKind.STATIC ||
          method.isAbstract ||
          method.parameters.any { it.isVararg } ||
          method.typeParameters.isNotEmpty()
      ) {
        logger.error("Invalid effective @JsonValidator method", method)
        continue
      }
      val owner = method.parentDeclaration as? KSClassDeclaration ?: continue
      result +=
        JvmValidator(
          binaryName(owner)!!,
          owner.classKind == ClassKind.INTERFACE,
          resolver.getJvmName(method) ?: method.simpleName.asString(),
        )
    }
    return result.sortedWith(compareBy(JvmValidator::ownerBinaryName, JvmValidator::name))
  }

  private fun javaCreator(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration,
  ): JsonCreator? {
    val candidates = ArrayList<KSFunctionDeclaration>()
    target
      .getConstructors()
      .filter { hasEffectiveAnnotation(it, mixin, JSON_CREATOR) }
      .forEach(candidates::add)
    target.declarations
      .filterIsInstance<KSFunctionDeclaration>()
      .filter {
        it.simpleName.asString() != "<init>" && hasEffectiveAnnotation(it, mixin, JSON_CREATOR)
      }
      .forEach(candidates::add)
    if (candidates.size > 1) {
      return fail(candidates[1], "Exactly one effective @JsonCreator is allowed")
    }
    val creator = candidates.singleOrNull() ?: return null
    val descriptor =
      descriptor(creator) ?: return fail(creator, "Cannot map @JsonCreator to a JVM descriptor")
    val method = parseMethodDescriptor(descriptor)
    val factory = creator.simpleName.asString() != "<init>"
    val modifiers = resolver.effectiveJavaModifiers(creator)
    if (
      Modifier.PUBLIC !in modifiers ||
        creator.parameters.any { it.isVararg } ||
        creator.typeParameters.isNotEmpty() ||
        creator.parameters.isEmpty() ||
        factory &&
          (Modifier.JAVA_STATIC !in modifiers && creator.functionKind != FunctionKind.STATIC ||
            method.result != "L${binaryName(target)!!.replace('.', '/')};")
    ) {
      return fail(creator, "Invalid effective @JsonCreator executable")
    }
    val names = creatorNames(creator, mixin)
    if (names.size != method.parameters.size || names.toSet().size != names.size) {
      return fail(creator, "@JsonCreator property names must be unique and match its parameters")
    }
    return JsonCreator(
      parameterNames = names,
      parameterTypes = method.parameters,
      optional = BooleanArray(method.parameters.size),
      invocationOwner = binaryName(target)!!,
      invocationName =
        if (factory) resolver.getJvmName(creator) ?: creator.simpleName.asString() else "<init>",
      invocationDescriptor = descriptor,
      defaultDescriptor = null,
    )
  }

  private fun creatorNames(
    creator: KSFunctionDeclaration,
    mixin: KSClassDeclaration,
  ): List<String> {
    val source = matchingMixinFunction(creator, mixin)
    val annotation =
      source?.annotations?.firstOrNull { annotationName(it) == JSON_CREATOR }
        ?: creator.annotations.firstOrNull { annotationName(it) == JSON_CREATOR }
    val explicit =
      annotation?.arguments?.firstOrNull { it.name?.asString() == "value" }?.value as? List<*>
    val names = explicit?.filterIsInstance<String>().orEmpty()
    return if (names.isNotEmpty()) names
    else
      creator.parameters.mapIndexed { index, parameter ->
        parameter.name?.asString() ?: "arg$index"
      }
  }

  private fun effectiveFunctions(target: KSClassDeclaration): List<KSFunctionDeclaration> {
    val result = linkedMapOf<String, KSFunctionDeclaration>()
    for (method in target.getAllFunctions()) {
      if (method.simpleName.asString() == "<init>" || method.origin == Origin.SYNTHETIC) continue
      val descriptor = descriptor(method) ?: continue
      val parsed = parseMethodDescriptor(descriptor)
      val name = resolver.getJvmName(method) ?: method.simpleName.asString()
      val key = name + parsed.parameters.joinToString("") { it.descriptor }
      result.putIfAbsent(key, method)
    }
    return result.values.toList()
  }

  private fun effectiveMethods(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration?,
    annotation: String,
  ): List<KSFunctionDeclaration> =
    effectiveFunctions(target).filter { method ->
      if (mixin == null) hasAnnotation(method, annotation)
      else hasEffectiveAnnotation(method, mixin, annotation)
    }

  private fun hasEffectiveAnnotation(
    target: KSFunctionDeclaration,
    mixin: KSClassDeclaration,
    annotation: String,
  ): Boolean {
    val source = matchingMixinFunction(target, mixin)
    if (source != null) {
      if (hasAnnotation(source, annotation)) return true
      if (removesAnnotation(source, annotation)) return false
    }
    return hasAnnotation(target, annotation)
  }

  private fun hasEffectiveJsonAnnotation(
    target: KSFunctionDeclaration,
    mixin: KSClassDeclaration,
  ): Boolean {
    val source = matchingMixinFunction(target, mixin)
    return hasJsonAnnotation(target) || source != null && hasJsonAnnotation(source)
  }

  private fun matchingMixinFunction(
    target: KSFunctionDeclaration,
    mixin: KSClassDeclaration,
  ): KSFunctionDeclaration? {
    val key = callableKey(target) ?: return null
    return mixinCallables(mixin).firstOrNull { callableKey(it) == key }
  }

  private fun mixinCallables(mixin: KSClassDeclaration): Sequence<KSFunctionDeclaration> =
    mixin.declarations.filterIsInstance<KSFunctionDeclaration>() +
      mixin.getConstructors() +
      mixin.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.isCompanionObject }
        .flatMap { companion ->
          companion.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
            hasAnnotation(it, "kotlin.jvm.JvmStatic")
          }
        }

  private fun callableKey(function: KSFunctionDeclaration): String? {
    val descriptor = descriptor(function) ?: return null
    return (resolver.getJvmName(function) ?: function.simpleName.asString()) + descriptor
  }

  private fun effectiveTypeAnnotation(
    target: KSClassDeclaration,
    mixin: KSClassDeclaration,
    annotation: String,
  ): Boolean =
    when {
      hasAnnotation(mixin, annotation) -> true
      removesAnnotation(mixin, annotation) -> false
      else -> hasAnnotation(target, annotation)
    }

  private fun removesAnnotation(source: KSAnnotated, annotation: String): Boolean {
    val removal =
      source.annotations.firstOrNull { annotationName(it) == JSON_MIXIN_REMOVE } ?: return false
    return removal.arguments.any { argument -> containsType(argument.value, annotation) }
  }

  private fun containsType(value: Any?, typeName: String): Boolean =
    when (value) {
      is KSType ->
        (actual(value.declaration) as? KSClassDeclaration)?.qualifiedName?.asString() == typeName
      is Iterable<*> -> value.any { containsType(it, typeName) }
      else -> false
    }

  private fun hasJsonAnnotation(source: KSAnnotated): Boolean =
    source.annotations.any { annotationName(it).startsWith(JSON_ANNOTATION_PACKAGE) }

  private fun isMemberAccessible(
    declaration: KSDeclaration,
    owner: KSClassDeclaration,
    generatedPackage: String,
  ): Boolean {
    val modifiers = resolver.effectiveJavaModifiers(declaration)
    if (Modifier.PRIVATE in modifiers) return false
    return Modifier.PUBLIC in modifiers || owner.packageName.asString() == generatedPackage
  }

  private fun mixinMembers(mixin: KSClassDeclaration): List<String> {
    val result = linkedSetOf<String>()
    for (property in mixin.declarations.filterIsInstance<KSPropertyDeclaration>()) {
      val selected =
        hasJsonAnnotation(property) ||
          property.getter?.let(::hasJsonAnnotation) == true ||
          property.setter?.let(::hasJsonAnnotation) == true
      if (!selected) continue
      val type = carrier(property.type.resolve())
      if (property.hasBackingField)
        result += "${type.sourceName} ${property.simpleName.asString()};"
      property.getter?.let { getter ->
        val name = resolver.getJvmName(getter) ?: return@let
        result += "${type.sourceName} $name();"
      }
      property.setter?.let { setter ->
        val name = resolver.getJvmName(setter) ?: return@let
        result += "void $name(${type.sourceName});"
      }
    }
    for (function in mixinCallables(mixin)) {
      val selected = hasJsonAnnotation(function) || function.parameters.any(::hasJsonAnnotation)
      if (!selected) continue
      val descriptor = descriptor(function) ?: continue
      val name = resolver.getJvmName(function) ?: function.simpleName.asString()
      result += if (name == "<init>") constructorRule(descriptor) else methodRule(name, descriptor)
    }
    return result.sorted()
  }

  private fun methodRule(name: String, descriptor: String): String {
    val method = parseMethodDescriptor(descriptor)
    val result = if (method.result == "V") "void" else JvmType(method.result).sourceName
    return "$result $name(${method.parameters.joinToString(",") { it.sourceName }});"
  }

  private fun constructorRule(descriptor: String): String =
    "<init>(${parseMethodDescriptor(descriptor).parameters.joinToString(",") { it.sourceName }});"

  private fun members(target: KSClassDeclaration, creator: JsonCreator?): List<JvmMember>? {
    val result = ArrayList<JvmMember>()
    val owner = binaryName(target)!!
    val creatorTypes =
      creator
        ?.parameterNames
        ?.withIndex()
        ?.associate { (index, name) -> name to creator.parameterTypes[index] }
        .orEmpty()
    for (property in target.declarations.filterIsInstance<KSPropertyDeclaration>()) {
      if (Modifier.CONST in property.modifiers || property.isDelegated()) continue
      val carrier = creatorTypes[property.simpleName.asString()] ?: carrier(property.type.resolve())
      if (hasAnnotation(property, "kotlin.jvm.JvmField")) {
        if (
          Modifier.PUBLIC in resolver.effectiveJavaModifiers(property) && property.hasBackingField
        ) {
          result +=
            JvmMember(
              MemberKind.FIELD,
              owner,
              false,
              property.simpleName.asString(),
              carrier.descriptor,
              property.isMutable,
            )
        }
        continue
      }
      property.getter?.let { getter ->
        if (isAccessible(property, getter.modifiers)) {
          val name =
            resolver.getJvmName(getter)
              ?: return fail(getter, "Cannot determine the JVM getter for ${property.simpleName}")
          result +=
            JvmMember(
              MemberKind.GETTER,
              owner,
              false,
              name,
              methodDescriptor(emptyList(), carrier.descriptor),
            )
        }
      }
      property.setter?.let { setter ->
        if (property.isMutable && isAccessible(property, setter.modifiers)) {
          val name =
            resolver.getJvmName(setter)
              ?: return fail(setter, "Cannot determine the JVM setter for ${property.simpleName}")
          result +=
            JvmMember(
              MemberKind.SETTER,
              owner,
              false,
              name,
              methodDescriptor(listOf(carrier), "V"),
            )
        }
      }
    }
    return result.sortedWith(compareBy(JvmMember::name, JvmMember::descriptor, JvmMember::kind))
  }

  private fun isAccessible(
    property: KSPropertyDeclaration,
    accessorModifiers: Set<Modifier>
  ): Boolean =
    Modifier.PRIVATE !in accessorModifiers &&
      Modifier.PROTECTED !in accessorModifiers &&
      Modifier.PUBLIC in resolver.effectiveJavaModifiers(property)

  private fun carrier(type: KSType): JvmType {
    val declaration = actual(type.declaration)
    val name = declaration.qualifiedName?.asString()
    val nullable = type.nullability == Nullability.NULLABLE
    PRIMITIVES[name]?.let { primitive ->
      if (nullable && name in UNSIGNED) return JvmType("L${name!!.replace('.', '/')};")
      return if (nullable) JvmType(BOXES.getValue(primitive)) else JvmType(primitive)
    }
    ARRAYS[name]?.let {
      return JvmType(it)
    }
    if (declaration is com.google.devtools.ksp.symbol.KSTypeParameter) {
      return JvmType("Ljava/lang/Object;")
    }
    val binary =
      JAVA_TYPES[name]
        ?: (declaration as? KSClassDeclaration)?.let(::binaryName)
        ?: "java.lang.Object"
    return JvmType("L${binary.replace('.', '/')};")
  }

  private fun isUnboxedValue(type: KSType, carrier: JvmType): Boolean {
    if (type.nullability == Nullability.NULLABLE) return false
    val declaration = actual(type.declaration) as? KSClassDeclaration ?: return false
    return Modifier.VALUE in declaration.modifiers &&
      carrier.descriptor != "L${binaryName(declaration)!!.replace('.', '/')};"
  }

  private fun annotations(target: KSClassDeclaration): Set<String> {
    val result = linkedSetOf("kotlin.Metadata")
    collectAnnotations(target, result)
    target.declarations.forEach { declaration ->
      collectAnnotations(declaration, result)
      if (declaration is com.google.devtools.ksp.symbol.KSFunctionDeclaration) {
        declaration.parameters.forEach { collectAnnotations(it, result) }
      }
      if (declaration is KSPropertyDeclaration) {
        declaration.getter?.let { collectAnnotations(it, result) }
        declaration.setter?.let { collectAnnotations(it, result) }
      }
    }
    return result
  }

  private fun collectAnnotations(source: KSAnnotated, result: MutableSet<String>) {
    source.annotations
      .map(::annotationName)
      .filter { it.startsWith(JSON_ANNOTATION_PACKAGE) }
      .forEach(result::add)
  }

  private fun annotationTypes(target: KSClassDeclaration): Set<String> {
    val result = linkedSetOf<String>()
    fun collect(source: KSAnnotated) {
      source.annotations
        .filter { annotationName(it).startsWith(JSON_ANNOTATION_PACKAGE) }
        .flatMap { it.arguments.asSequence() }
        .forEach { argument -> collectTypeValue(argument.value, result) }
    }
    collect(target)
    target.declarations.forEach { collect(it) }
    return result
  }

  private fun collectTypeValue(value: Any?, result: MutableSet<String>) {
    when (value) {
      is KSType ->
        (actual(value.declaration) as? KSClassDeclaration)?.let(::binaryName)?.let(result::add)
      is KSAnnotation -> value.arguments.forEach { collectTypeValue(it.value, result) }
      is Iterable<*> -> value.forEach { collectTypeValue(it, result) }
    }
  }

  private fun descriptor(declaration: KSDeclaration): String? {
    val signature =
      try {
        resolver.mapToJvmSignature(declaration)
      } catch (_: RuntimeException) {
        null
      } ?: return null
    val start = signature.indexOf('(')
    return if (start < 0) null else signature.substring(start)
  }

  private fun hasAnnotation(source: KSAnnotated, name: String): Boolean =
    source.annotations.any { annotationName(it) == name }

  private fun annotationName(annotation: KSAnnotation): String =
    annotation.annotationType.resolve().declaration.qualifiedName?.asString().orEmpty()

  private fun actual(declaration: KSDeclaration): KSDeclaration =
    if (declaration is KSTypeAlias) actual(declaration.type.resolve().declaration) else declaration

  private fun binaryName(declaration: KSClassDeclaration): String? {
    val parent = declaration.parentDeclaration as? KSClassDeclaration
    return if (parent == null) declaration.qualifiedName?.asString()
    else binaryName(parent)?.let { "$it\$${declaration.simpleName.asString()}" }
  }

  private fun <T> fail(node: KSNode, message: String): T? {
    logger.error(message, node)
    return null
  }

  private companion object {
    val SOURCE_ORIGINS = setOf(Origin.KOTLIN, Origin.JAVA)
    val JAVA_ORIGINS = setOf(Origin.JAVA, Origin.JAVA_LIB)
    val PRIMITIVES =
      mapOf(
        "kotlin.Boolean" to "Z",
        "kotlin.Byte" to "B",
        "kotlin.Short" to "S",
        "kotlin.Int" to "I",
        "kotlin.Long" to "J",
        "kotlin.Float" to "F",
        "kotlin.Double" to "D",
        "kotlin.Char" to "C",
        "kotlin.UByte" to "B",
        "kotlin.UShort" to "S",
        "kotlin.UInt" to "I",
        "kotlin.ULong" to "J",
      )
    val UNSIGNED = setOf("kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong")
    val BOXES =
      mapOf(
        "Z" to "Ljava/lang/Boolean;",
        "B" to "Ljava/lang/Byte;",
        "S" to "Ljava/lang/Short;",
        "I" to "Ljava/lang/Integer;",
        "J" to "Ljava/lang/Long;",
        "F" to "Ljava/lang/Float;",
        "D" to "Ljava/lang/Double;",
        "C" to "Ljava/lang/Character;",
      )
    val ARRAYS =
      mapOf(
        "kotlin.BooleanArray" to "[Z",
        "kotlin.ByteArray" to "[B",
        "kotlin.ShortArray" to "[S",
        "kotlin.IntArray" to "[I",
        "kotlin.LongArray" to "[J",
        "kotlin.FloatArray" to "[F",
        "kotlin.DoubleArray" to "[D",
        "kotlin.CharArray" to "[C",
        "kotlin.UByteArray" to "[B",
        "kotlin.UShortArray" to "[S",
        "kotlin.UIntArray" to "[I",
        "kotlin.ULongArray" to "[J",
      )
    val JAVA_TYPES =
      mapOf(
        "kotlin.Any" to "java.lang.Object",
        "kotlin.String" to "java.lang.String",
        "kotlin.CharSequence" to "java.lang.CharSequence",
        "kotlin.Throwable" to "java.lang.Throwable",
        "kotlin.Nothing" to "java.lang.Void",
        "kotlin.collections.Iterable" to "java.lang.Iterable",
        "kotlin.collections.Collection" to "java.util.Collection",
        "kotlin.collections.MutableCollection" to "java.util.Collection",
        "kotlin.collections.List" to "java.util.List",
        "kotlin.collections.MutableList" to "java.util.List",
        "kotlin.collections.Set" to "java.util.Set",
        "kotlin.collections.MutableSet" to "java.util.Set",
        "kotlin.collections.Map" to "java.util.Map",
        "kotlin.collections.MutableMap" to "java.util.Map",
      )
  }
}
