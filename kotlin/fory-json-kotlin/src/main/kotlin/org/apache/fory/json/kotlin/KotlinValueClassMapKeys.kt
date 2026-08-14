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

import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.MapCodec
import org.apache.fory.json.codec.MapKeyCodec
import org.apache.fory.json.meta.JsonCreatorFieldInfo
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.writer.JsonWriter
import org.apache.fory.type.Types

/** Cold selection of the fixed typed member-name operation for a value-class key chain. */
internal object KotlinValueClassMapKeys {
  fun create(
    shape: KotlinValueClassShape,
    operations: KotlinValueClassOperations,
    terminal: MapKeyCodec,
  ): MapKeyCodec {
    val type = shape.terminalType
    val typeId = type.typeExtMeta?.typeId() ?: Types.UNKNOWN
    val rawType = type.rawType
    val default = KotlinMapKeyCodecs.keyCodec(type)
      ?: if (rawType == String::class.java || rawType.isEnum || signed(rawType)) {
        MapCodec.keyCodec(rawType)
      } else {
        null
      }
    // A generic value-class layer has an Object carrier even when its substituted terminal is a
    // primitive logical type. That JVM ABI must use the reference operation path; selecting a
    // primitive operation from the substituted TypeRef would invent a carrier the class does not
    // have.
    if (terminal === default && shape.layers.last().carrierClass != Any::class.java) {
      when (typeId) {
        Types.UINT8 -> return UByteKey(operations.require(shape))
        Types.UINT16 -> return UShortKey(operations.require(shape))
        Types.UINT32 -> return UIntKey(operations.require(shape))
        Types.UINT64 -> return ULongKey(operations.require(shape))
      }
      when (rawType) {
        Byte::class.javaPrimitiveType,
        Byte::class.javaObjectType -> return ByteKey(operations.require(shape))
        Short::class.javaPrimitiveType,
        Short::class.javaObjectType -> return ShortKey(operations.require(shape))
        Int::class.javaPrimitiveType,
        Int::class.javaObjectType -> return IntKey(operations.require(shape))
        Long::class.javaPrimitiveType,
        Long::class.javaObjectType -> return LongKey(operations.require(shape))
      }
    }
    return ReferenceKey(boxedOperations(operations), terminal)
  }

  private fun signed(type: Class<*>): Boolean =
    type == Byte::class.javaPrimitiveType ||
      type == Byte::class.javaObjectType ||
      type == Short::class.javaPrimitiveType ||
      type == Short::class.javaObjectType ||
      type == Int::class.javaPrimitiveType ||
      type == Int::class.javaObjectType ||
      type == Long::class.javaPrimitiveType ||
      type == Long::class.javaObjectType

  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T : KotlinValueClassOperations> KotlinValueClassOperations.require(
    shape: KotlinValueClassShape,
  ): T = this as? T ?: throw ForyJsonException(
    "Value-class map-key operations for ${shape.ownerClass.name} do not match " +
      "terminal carrier ${shape.layers.last().carrierClass.name}",
  )
}

private class ReferenceKey(
  private val operations: BoxedValueClassOperations,
  private val terminal: MapKeyCodec,
) : MapKeyCodec {
  override fun toName(key: Any): String = terminal.toName(operations.unbox(key))

  override fun fromName(name: String): Any = operations.constructUncharged(terminal.fromName(name))

  override fun writeName(writer: JsonWriter, key: Any) = terminal.writeName(writer, operations.unbox(key))

  override fun readName(reader: JsonReader): Any = operations.construct(reader, terminal.readName(reader))
}

private class ByteKey(
  private val operations: KotlinByteValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = operations.unboxByte(key).toString()

  override fun fromName(name: String): Any =
    operations.constructByteUncharged(JsonCreatorFieldInfo.checkedByte(name.toInt()))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeIntFieldName(operations.unboxByte(key).toInt())

  override fun readName(reader: JsonReader): Any =
    operations.constructByte(reader, JsonCreatorFieldInfo.checkedByte(reader.readFieldNameInt()))
}

private class ShortKey(
  private val operations: KotlinShortValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = operations.unboxShort(key).toString()

  override fun fromName(name: String): Any =
    operations.constructShortUncharged(JsonCreatorFieldInfo.checkedShort(name.toInt()))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeIntFieldName(operations.unboxShort(key).toInt())

  override fun readName(reader: JsonReader): Any =
    operations.constructShort(reader, JsonCreatorFieldInfo.checkedShort(reader.readFieldNameInt()))
}

private class IntKey(
  private val operations: KotlinIntValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = operations.unboxInt(key).toString()

  override fun fromName(name: String): Any = operations.constructIntUncharged(name.toInt())

  override fun writeName(writer: JsonWriter, key: Any) = writer.writeIntFieldName(operations.unboxInt(key))

  override fun readName(reader: JsonReader): Any = operations.constructInt(reader, reader.readFieldNameInt())
}

private class LongKey(
  private val operations: KotlinLongValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = operations.unboxLong(key).toString()

  override fun fromName(name: String): Any = operations.constructLongUncharged(name.toLong())

  override fun writeName(writer: JsonWriter, key: Any) = writer.writeLongFieldName(operations.unboxLong(key))

  override fun readName(reader: JsonReader): Any = operations.constructLong(reader, reader.readFieldNameLong())
}

private class UByteKey(
  private val operations: KotlinByteValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = (operations.unboxByte(key).toInt() and 0xff).toString()

  override fun fromName(name: String): Any = operations.constructByteUncharged(checked(name.toUInt().toInt()))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeUnsignedIntFieldName(operations.unboxByte(key).toInt() and 0xff)

  override fun readName(reader: JsonReader): Any = operations.constructByte(reader, checked(reader.readFieldNameUnsignedInt()))

  private fun checked(value: Int): Byte {
    if (Integer.compareUnsigned(value, UByte.MAX_VALUE.toInt()) > 0) {
      throw ForyJsonException("UByte map-key overflow")
    }
    return value.toByte()
  }
}

private class UShortKey(
  private val operations: KotlinShortValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = (operations.unboxShort(key).toInt() and 0xffff).toString()

  override fun fromName(name: String): Any = operations.constructShortUncharged(checked(name.toUInt().toInt()))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeUnsignedIntFieldName(operations.unboxShort(key).toInt() and 0xffff)

  override fun readName(reader: JsonReader): Any = operations.constructShort(reader, checked(reader.readFieldNameUnsignedInt()))

  private fun checked(value: Int): Short {
    if (Integer.compareUnsigned(value, UShort.MAX_VALUE.toInt()) > 0) {
      throw ForyJsonException("UShort map-key overflow")
    }
    return value.toShort()
  }
}

private class UIntKey(
  private val operations: KotlinIntValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = Integer.toUnsignedString(operations.unboxInt(key))

  override fun fromName(name: String): Any = operations.constructIntUncharged(Integer.parseUnsignedInt(name))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeUnsignedIntFieldName(operations.unboxInt(key))

  override fun readName(reader: JsonReader): Any = operations.constructInt(reader, reader.readFieldNameUnsignedInt())
}

private class ULongKey(
  private val operations: KotlinLongValueClassOperations<Any>,
) : MapKeyCodec {
  override fun toName(key: Any): String = java.lang.Long.toUnsignedString(operations.unboxLong(key))

  override fun fromName(name: String): Any =
    operations.constructLongUncharged(java.lang.Long.parseUnsignedLong(name))

  override fun writeName(writer: JsonWriter, key: Any) =
    writer.writeUnsignedLongFieldName(operations.unboxLong(key))

  override fun readName(reader: JsonReader): Any =
    operations.constructLong(reader, reader.readFieldNameUnsignedLong())
}
