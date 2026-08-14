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

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import org.apache.fory.annotation.Internal
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.GeneratedJsonCodec
import org.apache.fory.json.codec.GeneratedJsonCodec.GeneratedMapKey
import org.apache.fory.json.codec.JsonValueCodec
import org.apache.fory.json.meta.JsonFieldAccessor
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.platform.AndroidSupport
import org.apache.fory.platform.GraalvmSupport
import org.apache.fory.platform.internal._JDKAccess
import org.apache.fory.reflect.TypeRef

/** Marker for immutable operations that implement one exact value-class layer chain. */
@Internal
public interface KotlinValueClassOperations

/** Interpreted and direct-codegen operations for one unboxed parent occurrence. */
@Internal
public interface KotlinUnboxedValueClassOperations {
  public fun constructCarrier(reader: JsonReader, value: Any?): Any?

  public fun extractValue(carrier: Any?): Any?

  public fun boxCarrier(carrier: Any?): Any

  public fun unboxValue(value: Any): Any?

  public fun constructMethods(): Array<Method>

  public fun constructBoxBytes(): IntArray

  public fun extractMethods(): Array<Method>

  public fun boxMethod(): Method

  public fun unboxMethod(): Method

  public fun boxBytes(): Int
}

/** Generated and runtime operations expose the same exact unboxed occurrence capability. */
@Internal
public interface KotlinValueClassOperationsOwner : KotlinValueClassOperations {
  public fun unboxedOperations(): KotlinUnboxedValueClassOperations
}

@Internal
public interface KotlinBooleanValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructBoolean(reader: JsonReader, value: Boolean): T

  public fun constructBooleanUncharged(value: Boolean): T

  public fun unboxBoolean(value: T): Boolean
}

@Internal
public interface KotlinByteValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructByte(reader: JsonReader, value: Byte): T

  public fun constructByteUncharged(value: Byte): T

  public fun unboxByte(value: T): Byte
}

@Internal
public interface KotlinShortValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructShort(reader: JsonReader, value: Short): T

  public fun constructShortUncharged(value: Short): T

  public fun unboxShort(value: T): Short
}

@Internal
public interface KotlinIntValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructInt(reader: JsonReader, value: Int): T

  public fun constructIntUncharged(value: Int): T

  public fun unboxInt(value: T): Int
}

@Internal
public interface KotlinLongValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructLong(reader: JsonReader, value: Long): T

  public fun constructLongUncharged(value: Long): T

  public fun unboxLong(value: T): Long
}

@Internal
public interface KotlinFloatValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructFloat(reader: JsonReader, value: Float): T

  public fun constructFloatUncharged(value: Float): T

  public fun unboxFloat(value: T): Float
}

@Internal
public interface KotlinDoubleValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructDouble(reader: JsonReader, value: Double): T

  public fun constructDoubleUncharged(value: Double): T

  public fun unboxDouble(value: T): Double
}

@Internal
public interface KotlinCharValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructChar(reader: JsonReader, value: Char): T

  public fun constructCharUncharged(value: Char): T

  public fun unboxChar(value: T): Char
}

@Internal
public interface KotlinReferenceValueClassOperations<T : Any> : KotlinValueClassOperations {
  public fun constructValue(reader: JsonReader, value: Any?): T

  public fun constructValueUncharged(value: Any?): T

  public fun unboxValue(value: T): Any?
}

/**
 * Immutable generated companion base. The shared companion owns only direct operations; each call
 * to [newTypeCodec] returns a resolver-local composite shell with its own child capabilities.
 */
@Internal
public abstract class KotlinGeneratedValueClassCodec<T : Any> :
  GeneratedJsonCodec<T>(),
  KotlinValueClassOperationsOwner {
  protected abstract fun valueClassShape(type: TypeRef<*>): KotlinValueClassShape

  final override fun fieldAccessors(): Array<JsonFieldAccessor> = EMPTY_ACCESSORS

  final override fun newTypeCodec(type: TypeRef<*>): JsonValueCodec<*> =
    KotlinValueClassCodecs.createGenerated(valueClassShape(type), this)

  final override fun newMapKey(type: TypeRef<*>): GeneratedMapKey {
    val shape = valueClassShape(type)
    val codec = KotlinValueClassCodecs.generatedMapKey(shape, this)
    val secureTypes = Array(shape.layers.size + 1) { index ->
      if (index == shape.layers.size) shape.terminalType.rawType else shape.layers[index].ownerClass
    }
    return GeneratedMapKey(codec, secureTypes)
  }

  private companion object {
    private val EMPTY_ACCESSORS: Array<JsonFieldAccessor> = emptyArray()
  }
}

/** HotSpot direct-call owner. Reflection and MethodHandle composition are cold-only. */
internal object KotlinMethodHandleValueClassOperations {
  private val LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()
  private val RESERVE: MethodHandle =
    LOOKUP.findStatic(
      KotlinMethodHandleValueClassOperations::class.java,
      "reserve",
      MethodType.methodType(Void.TYPE, JsonReader::class.java, Int::class.javaPrimitiveType),
    )

  fun create(model: KotlinReflectiveValueClass): KotlinValueClassOperations {
    if (AndroidSupport.IS_ANDROID || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      throw ForyJsonException(
        "Kotlin JSON value class ${model.shape.ownerClass.name} requires its exact " +
          "KSP-generated operations on Android and in a Native Image",
      )
    }
    val constructors = bind(model.shape, model.constructors, "constructor-impl")
    val boxes = bind(model.shape, model.boxes, "box-impl")
    val unboxes = bind(model.shape, model.unboxes, "unbox-impl")
    val charged = buildConstruct(model.shape, constructors, boxes, true)
    val uncharged = buildConstruct(model.shape, constructors, boxes, false)
    val unbox = buildUnbox(model.shape, unboxes)
    val carrier = model.shape.layers.last().carrierClass
    val invocationCarrier = if (carrier.isPrimitive) carrier else Any::class.java
    val unboxed = createUnboxed(model, constructors, boxes, unboxes)
    return KotlinExactValueClassOperations.create(
      model.shape.ownerClass,
      carrier,
      charged.asType(
        MethodType.methodType(
          Any::class.java,
          JsonReader::class.java,
          invocationCarrier,
        ),
      ),
      uncharged.asType(MethodType.methodType(Any::class.java, invocationCarrier)),
      unbox.asType(MethodType.methodType(invocationCarrier, Any::class.java)),
      unboxed,
    )
  }

  private fun createUnboxed(
    model: KotlinReflectiveValueClass,
    constructors: List<MethodHandle>,
    boxes: List<MethodHandle>,
    unboxes: List<MethodHandle>,
  ): KotlinUnboxedValueClassOperations {
    val shape = model.shape
    val carrier = shape.layers.first().carrierClass
    val valueCarrier = shape.layers.last().carrierClass
    val construct = buildCarrierConstruct(shape, constructors, boxes)
      .asType(
        MethodType.methodType(
          Any::class.java,
          JsonReader::class.java,
          Any::class.java,
        ),
      )
    val extract = buildCarrierExtract(shape, unboxes)
      .asType(MethodType.methodType(Any::class.java, Any::class.java))
    val box = boxes.first()
      .asType(MethodType.methodType(Any::class.java, Any::class.java))
    val unbox = unboxes.first()
      .asType(MethodType.methodType(Any::class.java, Any::class.java))
    val constructMethods = ArrayList<Method>(shape.layers.size * 2)
    val constructBytes = ArrayList<Int>(shape.layers.size * 2)
    var index = shape.layers.lastIndex
    constructMethods += model.constructors[index]
    constructBytes += 0
    while (index > 0) {
      val inner = shape.layers[index]
      val outer = shape.layers[index - 1]
      if (outer.carrierClass == inner.ownerClass) {
        constructMethods += model.boxes[index]
        constructBytes += inner.shallowBytes
      }
      index--
      constructMethods += model.constructors[index]
      constructBytes += 0
    }
    val extractMethods = ArrayList<Method>(shape.layers.size - 1)
    for (innerIndex in 1 until shape.layers.size) {
      if (shape.layers[innerIndex - 1].carrierClass == shape.layers[innerIndex].ownerClass) {
        extractMethods += model.unboxes[innerIndex]
      }
    }
    return KotlinExactUnboxedValueOperations.create(
      shape.ownerClass,
      carrier,
      valueCarrier,
      construct,
      extract,
      box,
      unbox,
      constructMethods.toTypedArray(),
      constructBytes.toIntArray(),
      extractMethods.toTypedArray(),
      model.boxes.first(),
      model.unboxes.first(),
      shape.layers.first().shallowBytes,
    )
  }

  @JvmStatic
  private fun reserve(reader: JsonReader, bytes: Int) {
    reader.reserveGraphMemory(bytes)
  }

  private fun bind(
    shape: KotlinValueClassShape,
    methods: List<java.lang.reflect.Method>,
    operation: String,
  ): List<MethodHandle> =
    methods.mapIndexed { index, method ->
      try {
        _JDKAccess._trustedLookup(shape.layers[index].ownerClass).unreflect(method)
      } catch (cause: IllegalAccessException) {
        throw ForyJsonException(
          "Cannot bind exact Kotlin value-class $operation for ${shape.layers[index].ownerClass.name}",
          cause,
        )
      }
    }

  private fun buildConstruct(
    shape: KotlinValueClassShape,
    constructors: List<MethodHandle>,
    boxes: List<MethodHandle>,
    charge: Boolean,
  ): MethodHandle {
    val layers = shape.layers
    var index = layers.lastIndex
    var current = constructors[index]
    if (charge) current = MethodHandles.dropArguments(current, 0, JsonReader::class.java)
    while (index > 0) {
      val inner = layers[index]
      val outer = layers[index - 1]
      if (outer.carrierClass == inner.ownerClass) {
        current =
          if (charge) composeCharged(current, boxes[index], inner.shallowBytes)
          else MethodHandles.filterReturnValue(current, boxes[index])
      }
      index--
      current = MethodHandles.filterReturnValue(current, constructors[index])
    }
    return if (charge) composeCharged(current, boxes[0], layers[0].shallowBytes)
    else MethodHandles.filterReturnValue(current, boxes[0])
  }

  private fun buildCarrierConstruct(
    shape: KotlinValueClassShape,
    constructors: List<MethodHandle>,
    boxes: List<MethodHandle>,
  ): MethodHandle {
    val layers = shape.layers
    var index = layers.lastIndex
    var current = MethodHandles.dropArguments(constructors[index], 0, JsonReader::class.java)
    while (index > 0) {
      val inner = layers[index]
      val outer = layers[index - 1]
      if (outer.carrierClass == inner.ownerClass) {
        current = composeCharged(current, boxes[index], inner.shallowBytes)
      }
      index--
      current = MethodHandles.filterReturnValue(current, constructors[index])
    }
    return current
  }

  private fun composeCharged(current: MethodHandle, box: MethodHandle, bytes: Int): MethodHandle {
    val chargedBox = chargedBox(box, bytes)
    val filtered = MethodHandles.collectArguments(chargedBox, 1, current)
    val input = current.type().parameterType(1)
    return MethodHandles.permuteArguments(
      filtered,
      MethodType.methodType(box.type().returnType(), JsonReader::class.java, input),
      0,
      0,
      1,
    )
  }

  private fun chargedBox(box: MethodHandle, bytes: Int): MethodHandle {
    val target = MethodHandles.dropArguments(box, 0, JsonReader::class.java)
    val reserve = MethodHandles.insertArguments(RESERVE, 1, bytes)
    return MethodHandles.foldArguments(target, reserve)
  }

  private fun buildUnbox(
    shape: KotlinValueClassShape,
    unboxes: List<MethodHandle>,
  ): MethodHandle {
    val layers = shape.layers
    var current = unboxes[0]
    for (index in 1 until layers.size) {
      if (layers[index - 1].carrierClass == layers[index].ownerClass) {
        current = MethodHandles.filterReturnValue(current, unboxes[index])
      }
    }
    return current
  }

  private fun buildCarrierExtract(
    shape: KotlinValueClassShape,
    unboxes: List<MethodHandle>,
  ): MethodHandle {
    val layers = shape.layers
    var current = MethodHandles.identity(layers.first().carrierClass)
    for (index in 1 until layers.size) {
      if (layers[index - 1].carrierClass == layers[index].ownerClass) {
        current = MethodHandles.filterReturnValue(current, unboxes[index])
      }
    }
    return current
  }
}
