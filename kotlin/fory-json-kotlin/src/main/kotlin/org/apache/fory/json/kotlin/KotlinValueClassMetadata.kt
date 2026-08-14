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

package org.apache.fory.json.kotlin

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.Visibility
import kotlin.metadata.isSecondary
import kotlin.metadata.isValue
import kotlin.metadata.kind
import kotlin.metadata.visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.signature
import org.apache.fory.annotation.Internal
import org.apache.fory.json.ForyJsonException
import org.apache.fory.reflect.TypeRef
import org.apache.fory.serializer.GraphMemoryEstimates

/** Immutable cold metadata for one exact, possibly nested, value-class occurrence. */
@Internal
public class KotlinValueClassShape public constructor(
  public val ownerType: TypeRef<*>,
  public val outerNullable: Boolean,
  public val layers: List<KotlinValueClassLayer>,
  public val terminalType: TypeRef<*>,
) {
  public val ownerClass: Class<*> = layers.firstOrNull()?.ownerClass
    ?: throw ForyJsonException("A Kotlin value-class shape requires at least one layer")
  public val underlyingNullable: Boolean = nullable(layers.first().underlyingType)

  init {
    if (layers.isEmpty() || layers.first().occurrenceType != ownerType) {
      throw ForyJsonException("Invalid generated Kotlin value-class layer sequence for $ownerType")
    }
    if (outerNullable && underlyingNullable) {
      throw ForyJsonException(
        "Unsupported Kotlin JSON value class ${ownerClass.name}: nullable outer and " +
        "nullable underlying values need an explicit tagged codec",
      )
    }
    validateCarriers()
  }

  public fun requireMapKey() {
    for (layer in layers) {
      if (nullable(layer.occurrenceType) || nullable(layer.underlyingType)) {
        throw ForyJsonException(
          "Kotlin JSON value-class map key ${ownerClass.name} must be non-null at every layer",
        )
      }
    }
    if (nullable(terminalType)) {
      throw ForyJsonException(
        "Kotlin JSON value-class map key ${ownerClass.name} has a nullable terminal type",
      )
    }
  }

  public companion object {
    public fun nullable(type: TypeRef<*>): Boolean = type.typeExtMeta?.nullable() == true
  }

  private fun validateCarriers() {
    for (index in layers.indices) {
      val layer = layers[index]
      if (layer.occurrenceType.rawType != layer.ownerClass) {
        invalidLayer(layer, "occurrence type does not name the exact owner")
      }
      if (layer.constructorDescriptor != descriptor(arrayOf(layer.carrierClass), layer.carrierClass)) {
        invalidLayer(layer, "constructor-impl descriptor does not match its carrier")
      }
      if (layer.boxDescriptor != descriptor(arrayOf(layer.carrierClass), layer.ownerClass)) {
        invalidLayer(layer, "box-impl descriptor does not match its carrier and owner")
      }
      if (layer.unboxDescriptor != descriptor(emptyArray(), layer.carrierClass)) {
        invalidLayer(layer, "unbox-impl descriptor does not match its carrier")
      }
      if (index < layers.lastIndex) {
        val inner = layers[index + 1]
        if (layer.underlyingType != inner.occurrenceType) {
          invalidLayer(layer, "underlying type does not match the next semantic layer")
        }
        if (layer.carrierClass != inner.ownerClass && layer.carrierClass != inner.carrierClass) {
          invalidLayer(layer, "physical carrier does not match the nested value class")
        }
      } else {
        if (layer.underlyingType != terminalType) {
          invalidLayer(layer, "underlying type does not match the terminal type")
        }
        val terminalClass = terminalType.rawType
        if (layer.carrierClass.isPrimitive) {
          if (layer.carrierClass != terminalClass) {
            invalidLayer(layer, "primitive carrier does not match the terminal type")
          }
        } else if (!layer.carrierClass.isAssignableFrom(box(terminalClass))) {
          invalidLayer(layer, "reference carrier does not accept the terminal type")
        }
      }
    }
  }

  private fun invalidLayer(layer: KotlinValueClassLayer, reason: String): Nothing =
    throw ForyJsonException(
      "Invalid generated Kotlin value-class metadata for ${layer.ownerClass.name}: $reason",
    )
}

/** Exact Kotlin/JVM operations and the logical underlying type for one semantic layer. */
@Internal
public class KotlinValueClassLayer public constructor(
  public val occurrenceType: TypeRef<*>,
  public val ownerClass: Class<*>,
  public val underlyingType: TypeRef<*>,
  public val carrierClass: Class<*>,
  public val constructorDescriptor: String,
  public val boxDescriptor: String,
  public val unboxDescriptor: String,
) {
  public val shallowBytes: Int = GraphMemoryEstimates.shallowObjectBytes(ownerClass)
}

/** Reflection-derived HotSpot operations kept separate from the platform-neutral semantic shape. */
internal class KotlinReflectiveValueClass(
  val shape: KotlinValueClassShape,
  val constructors: List<Method>,
  val boxes: List<Method>,
  val unboxes: List<Method>,
)

private class ReflectedLayer(
  val shape: KotlinValueClassLayer,
  val constructor: Method,
  val box: Method,
  val unbox: Method,
)

/** Strict Kotlin 2.3 metadata and JVM-descriptor validation for value-class codec construction. */
internal object KotlinValueClassMetadata {
  fun inspect(ownerType: TypeRef<*>): KotlinValueClassShape = inspectRuntime(ownerType).shape

  fun inspectRuntime(ownerType: TypeRef<*>): KotlinReflectiveValueClass {
    val layers = ArrayList<KotlinValueClassLayer>(2)
    val constructors = ArrayList<Method>(2)
    val boxes = ArrayList<Method>(2)
    val unboxes = ArrayList<Method>(2)
    val active = IdentityHashMap<Class<*>, Boolean>()
    var occurrence = ownerType
    var reflected = inspectLayer(occurrence, active)
    while (true) {
      val layer = reflected
      layers += layer.shape
      constructors += layer.constructor
      boxes += layer.box
      unboxes += layer.unbox
      val underlying = layer.shape.underlyingType
      // Generic value-class layers retain their erased Object carrier. Descending into a
      // substituted value-class argument would invent a flattened ABI that the outer class does
      // not have; the substituted logical type remains the terminal codec owner.
      if (layer.shape.carrierClass == Any::class.java ||
        !isValueClass(underlying.rawType) ||
        KotlinTemporalCodecs.supports(underlying.rawType) ||
        KotlinUnsupportedTypes.rejects(underlying.rawType)
      ) {
        return KotlinReflectiveValueClass(
          KotlinValueClassShape(
            ownerType,
            ownerType.typeExtMeta?.nullable() ?: true,
            layers,
            underlying,
          ),
          constructors,
          boxes,
          unboxes,
        )
      }
      val nested = inspectLayer(underlying, active)
      if (layer.shape.carrierClass != nested.shape.ownerClass &&
        layer.shape.carrierClass != nested.shape.carrierClass
      ) {
        return KotlinReflectiveValueClass(
          KotlinValueClassShape(
            ownerType,
            ownerType.typeExtMeta?.nullable() ?: true,
            layers,
            underlying,
          ),
          constructors,
          boxes,
          unboxes,
        )
      }
      occurrence = underlying
      reflected = nested
    }
  }

  fun isValueClass(type: Class<*>): Boolean {
    val metadata = type.getAnnotation(Metadata::class.java) ?: return false
    val classMetadata =
      try {
        KotlinClassMetadata.readStrict(metadata)
      } catch (_: IllegalArgumentException) {
        return false
      }
    return classMetadata is KotlinClassMetadata.Class && classMetadata.kmClass.isValue
  }

  private fun inspectLayer(
    occurrenceType: TypeRef<*>,
    active: IdentityHashMap<Class<*>, Boolean>,
  ): ReflectedLayer {
    val rawType = occurrenceType.rawType
    if (active.put(rawType, true) != null) {
      unsupported(rawType, "recursive value-class underlying type")
    }
    val metadata = rawType.getAnnotation(Metadata::class.java)
      ?: unsupported(rawType, "missing Kotlin metadata")
    val classMetadata =
      try {
        KotlinClassMetadata.readStrict(metadata)
      } catch (cause: IllegalArgumentException) {
        throw ForyJsonException("Unsupported Kotlin metadata on ${rawType.name}", cause)
      }
    if (classMetadata !is KotlinClassMetadata.Class) {
      unsupported(rawType, "metadata is not a class declaration")
    }
    val version = classMetadata.version
    if (version.major != 2 || version.minor != 3) {
      unsupported(rawType, "metadata ABI $version; expected 2.3")
    }
    val model = classMetadata.kmClass
    if (!model.isValue || model.kind != ClassKind.CLASS) {
      unsupported(rawType, "declaration is not a value class")
    }
    val primary = model.constructors.singleOrNull { !it.isSecondary }
      ?: unsupported(rawType, "missing unique primary constructor")
    if (primary.visibility != Visibility.PUBLIC && primary.visibility != Visibility.INTERNAL) {
      unsupported(rawType, "primary constructor is not JVM-accessible")
    }
    if (primary.valueParameters.size != 1) {
      unsupported(rawType, "primary constructor must have one underlying parameter")
    }
    val underlyingName = model.inlineClassUnderlyingPropertyName
      ?: unsupported(rawType, "missing underlying property name")
    val underlyingKmType = model.inlineClassUnderlyingType
      ?: unsupported(rawType, "missing underlying property type")
    val parameter = primary.valueParameters.single()
    if (parameter.name != underlyingName || parameter.type != underlyingKmType) {
      unsupported(rawType, "primary parameter and underlying property metadata disagree")
    }
    val substitutions = substitutions(occurrenceType, model)
    val underlyingType =
      KotlinMetadataTypes.resolve(underlyingKmType, rawType.classLoader, substitutions, false)
    val constructorSignature = primary.signature
      ?: unsupported(rawType, "primary constructor has no JVM signature")
    val underlyingField = rawType.declaredFields.singleOrNull {
      !Modifier.isStatic(it.modifiers) && it.name == underlyingName
    } ?: unsupported(rawType, "underlying field $underlyingName was not found exactly")
    if (!Modifier.isPrivate(underlyingField.modifiers) || !Modifier.isFinal(underlyingField.modifiers)) {
      unsupported(rawType, "underlying field is not an exact private final carrier")
    }
    val carrier = underlyingField.type
    val constructorImpl = exactMethod(rawType, "constructor-impl", arrayOf(carrier), carrier, true)
    if (constructorSignature.name != constructorImpl.name ||
      constructorSignature.descriptor != methodDescriptor(constructorImpl)
    ) {
      unsupported(rawType, "primary constructor metadata does not name the exact constructor-impl")
    }
    val boxedConstructor = rawType.declaredConstructors.singleOrNull {
      it.parameterTypes.contentEquals(arrayOf(carrier))
    } ?: unsupported(rawType, "boxed constructor does not have the exact carrier descriptor")
    if (!Modifier.isPrivate(boxedConstructor.modifiers)) {
      unsupported(rawType, "boxed constructor is not private")
    }
    val boxImpl = exactMethod(rawType, "box-impl", arrayOf(carrier), rawType, true)
    val unboxImpl = exactMethod(rawType, "unbox-impl", emptyArray(), carrier, false)
    return ReflectedLayer(
      KotlinValueClassLayer(
        occurrenceType,
        rawType,
        underlyingType,
        carrier,
        methodDescriptor(constructorImpl),
        methodDescriptor(boxImpl),
        methodDescriptor(unboxImpl),
      ),
      constructorImpl,
      boxImpl,
      unboxImpl,
    )
  }

  private fun substitutions(ownerType: TypeRef<*>, model: KmClass): Map<Int, TypeRef<*>> {
    if (model.typeParameters.isEmpty()) return emptyMap()
    val arguments = ownerType.typeArguments
    if (arguments.size != model.typeParameters.size) {
      throw ForyJsonException(
        "Kotlin JSON value class ${ownerType.type} requires exact type arguments",
      )
    }
    for (argument in arguments) {
      if (argument.typeExtMeta == null) {
        throw ForyJsonException(
          "Kotlin JSON value class ${ownerType.type} has a platform-typed argument $argument",
        )
      }
    }
    return model.typeParameters.indices.associate { model.typeParameters[it].id to arguments[it] }
  }

  private fun exactMethod(
    owner: Class<*>,
    name: String,
    parameters: Array<Class<*>>,
    result: Class<*>,
    static: Boolean,
  ): Method {
    val method = owner.declaredMethods.singleOrNull {
      it.name == name &&
        it.parameterTypes.contentEquals(parameters) &&
        it.returnType == result
    } ?: unsupported(owner, "method $name${descriptor(parameters, result)} was not found exactly")
    if (!Modifier.isPublic(method.modifiers) || Modifier.isStatic(method.modifiers) != static) {
      unsupported(owner, "method $name${descriptor(parameters, result)} is not directly callable")
    }
    if (!static && !Modifier.isFinal(method.modifiers)) {
      unsupported(owner, "method $name${descriptor(parameters, result)} is not final")
    }
    return method
  }

  private fun unsupported(type: Class<*>, reason: String): Nothing =
    throw ForyJsonException("Unsupported Kotlin JSON value class ${type.name}: $reason")
}

private fun nullable(type: TypeRef<*>): Boolean = type.typeExtMeta?.nullable() == true

private fun methodDescriptor(method: Method): String =
  descriptor(method.parameterTypes, method.returnType)

private fun descriptor(parameters: Array<Class<*>>, result: Class<*>): String = buildString {
  append('(')
  parameters.forEach { append(descriptor(it)) }
  append(')')
  append(descriptor(result))
}

private fun descriptor(type: Class<*>): String =
  when {
    type.isArray -> type.name.replace('.', '/')
    !type.isPrimitive -> "L${type.name.replace('.', '/')};"
    type == Void.TYPE -> "V"
    type == java.lang.Boolean.TYPE -> "Z"
    type == java.lang.Byte.TYPE -> "B"
    type == java.lang.Short.TYPE -> "S"
    type == java.lang.Integer.TYPE -> "I"
    type == java.lang.Long.TYPE -> "J"
    type == java.lang.Float.TYPE -> "F"
    type == java.lang.Double.TYPE -> "D"
    type == java.lang.Character.TYPE -> "C"
    else -> error("Unsupported primitive carrier $type")
  }

private fun box(type: Class<*>): Class<*> =
  when (type) {
    java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
    java.lang.Byte.TYPE -> Byte::class.javaObjectType
    java.lang.Short.TYPE -> Short::class.javaObjectType
    java.lang.Integer.TYPE -> Int::class.javaObjectType
    java.lang.Long.TYPE -> Long::class.javaObjectType
    java.lang.Float.TYPE -> Float::class.javaObjectType
    java.lang.Double.TYPE -> Double::class.javaObjectType
    java.lang.Character.TYPE -> Char::class.javaObjectType
    java.lang.Void.TYPE -> java.lang.Void::class.java
    else -> type
  }
