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
import org.apache.fory.json.codec.JsonValueCodec
import org.apache.fory.json.codec.ScalarCodecs
import org.apache.fory.json.meta.JsonCreatorFieldInfo
import org.apache.fory.json.reader.Latin1JsonReader
import org.apache.fory.json.reader.Utf16JsonReader
import org.apache.fory.json.reader.Utf8JsonReader
import org.apache.fory.json.resolver.JsonTypeInfo
import org.apache.fory.json.writer.StringJsonWriter
import org.apache.fory.json.writer.Utf8JsonWriter
import org.apache.fory.type.Types

/** Cold selection of the fixed primitive or generic value-class execution capability. */
internal object KotlinValueClassCapabilities {
  fun bind(
    shape: KotlinValueClassShape,
    operations: KotlinValueClassOperations,
    child: JsonTypeInfo,
  ): KotlinValueClassCapability {
    val terminal = shape.terminalType
    val typeId = terminal.typeExtMeta?.typeId() ?: Types.UNKNOWN
    val carrier = shape.layers.last().carrierClass
    when (carrier) {
      java.lang.Boolean.TYPE -> {
        val expected = ScalarCodecs.BooleanCodec.PRIMITIVE
        if (typeId == Types.UNKNOWN && exact(child, expected)) {
          return BooleanCapability(requireOperations(shape, operations))
        }
      }
      java.lang.Byte.TYPE -> {
        if (typeId == Types.UINT8) {
          val expected = KotlinUnsignedCodecs.scalar(typeId, false, false)
          if (exact(child, expected)) return UByteCapability(requireOperations(shape, operations))
        } else if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.ByteCodec.PRIMITIVE)) {
          return ByteCapability(requireOperations(shape, operations))
        }
      }
      java.lang.Short.TYPE -> {
        if (typeId == Types.UINT16) {
          val expected = KotlinUnsignedCodecs.scalar(typeId, false, false)
          if (exact(child, expected)) return UShortCapability(requireOperations(shape, operations))
        } else if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.ShortCodec.PRIMITIVE)) {
          return ShortCapability(requireOperations(shape, operations))
        }
      }
      java.lang.Integer.TYPE -> {
        if (typeId == Types.UINT32) {
          val expected = KotlinUnsignedCodecs.scalar(typeId, false, false)
          if (exact(child, expected)) return UIntCapability(requireOperations(shape, operations))
        } else if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.IntCodec.PRIMITIVE)) {
          return IntCapability(requireOperations(shape, operations))
        }
      }
      java.lang.Long.TYPE -> {
        if (typeId == Types.UINT64) {
          val expected = KotlinUnsignedCodecs.scalar(typeId, false, false)
          if (exact(child, expected)) return ULongCapability(requireOperations(shape, operations))
        } else if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.LongCodec.PRIMITIVE)) {
          return LongCapability(requireOperations(shape, operations))
        }
      }
      java.lang.Float.TYPE ->
        if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.FloatCodec.PRIMITIVE)) {
          return FloatCapability(requireOperations(shape, operations))
        }
      java.lang.Double.TYPE ->
        if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.DoubleCodec.PRIMITIVE)) {
          return DoubleCapability(requireOperations(shape, operations))
        }
      java.lang.Character.TYPE ->
        if (typeId == Types.UNKNOWN && exact(child, ScalarCodecs.CharCodec.PRIMITIVE)) {
          return CharCapability(requireOperations(shape, operations))
        }
    }
    return GenericValueClassCapability(boxedOperations(operations), child)
  }

  private fun exact(child: JsonTypeInfo, expected: JsonValueCodec<*>): Boolean =
    child.stringWriter() === expected &&
      child.utf8Writer() === expected &&
      child.latin1Reader() === expected &&
      child.utf16Reader() === expected &&
      child.utf8Reader() === expected

  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T : KotlinValueClassOperations> requireOperations(
    shape: KotlinValueClassShape,
    operations: KotlinValueClassOperations,
  ): T = operations as? T ?: throw ForyJsonException(
    "Value-class operations for ${shape.ownerClass.name} do not match " +
      "terminal carrier ${shape.layers.last().carrierClass.name}",
  )
}

private class BooleanCapability(
  private val operations: KotlinBooleanValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) =
    writer.writeBoolean(operations.unboxBoolean(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) =
    writer.writeBoolean(operations.unboxBoolean(value))

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructBoolean(reader, reader.readBooleanValue())

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructBoolean(reader, reader.readBooleanValue())

  override fun readUtf8(reader: Utf8JsonReader): Any =
    operations.constructBoolean(reader, reader.readBooleanValue())
}

private class ByteCapability(
  private val operations: KotlinByteValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) =
    writer.writeInt(operations.unboxByte(value).toInt())

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) =
    writer.writeInt(operations.unboxByte(value).toInt())

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructByte(reader, JsonCreatorFieldInfo.checkedByte(reader.readIntValue()))

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructByte(reader, JsonCreatorFieldInfo.checkedByte(reader.readIntValue()))

  override fun readUtf8(reader: Utf8JsonReader): Any =
    operations.constructByte(reader, JsonCreatorFieldInfo.checkedByte(reader.readIntValue()))
}

private class ShortCapability(
  private val operations: KotlinShortValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) =
    writer.writeInt(operations.unboxShort(value).toInt())

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) =
    writer.writeInt(operations.unboxShort(value).toInt())

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructShort(reader, JsonCreatorFieldInfo.checkedShort(reader.readIntValue()))

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructShort(reader, JsonCreatorFieldInfo.checkedShort(reader.readIntValue()))

  override fun readUtf8(reader: Utf8JsonReader): Any =
    operations.constructShort(reader, JsonCreatorFieldInfo.checkedShort(reader.readIntValue()))
}

private class IntCapability(
  private val operations: KotlinIntValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeInt(operations.unboxInt(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeInt(operations.unboxInt(value))

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructInt(reader, reader.readIntValue())

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructInt(reader, reader.readIntValue())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructInt(reader, reader.readIntValue())
}

private class LongCapability(
  private val operations: KotlinLongValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeLong(operations.unboxLong(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeLong(operations.unboxLong(value))

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructLong(reader, reader.readLongValue())

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructLong(reader, reader.readLongValue())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructLong(reader, reader.readLongValue())
}

private class FloatCapability(
  private val operations: KotlinFloatValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeFloat(operations.unboxFloat(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeFloat(operations.unboxFloat(value))

  override fun readLatin1(reader: Latin1JsonReader): Any = operations.constructFloat(reader, reader.readFloat())

  override fun readUtf16(reader: Utf16JsonReader): Any = operations.constructFloat(reader, reader.readFloat())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructFloat(reader, reader.readFloat())
}

private class DoubleCapability(
  private val operations: KotlinDoubleValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeDouble(operations.unboxDouble(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeDouble(operations.unboxDouble(value))

  override fun readLatin1(reader: Latin1JsonReader): Any = operations.constructDouble(reader, reader.readDouble())

  override fun readUtf16(reader: Utf16JsonReader): Any = operations.constructDouble(reader, reader.readDouble())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructDouble(reader, reader.readDouble())
}

private class CharCapability(
  private val operations: KotlinCharValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeChar(operations.unboxChar(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeChar(operations.unboxChar(value))

  override fun readLatin1(reader: Latin1JsonReader): Any = operations.constructChar(reader, reader.readChar())

  override fun readUtf16(reader: Utf16JsonReader): Any = operations.constructChar(reader, reader.readChar())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructChar(reader, reader.readChar())
}

private class UByteCapability(
  private val operations: KotlinByteValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) =
    writer.writeUnsignedInt(operations.unboxByte(value).toInt() and 0xff)

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) =
    writer.writeUnsignedInt(operations.unboxByte(value).toInt() and 0xff)

  override fun readLatin1(reader: Latin1JsonReader): Any = operations.constructByte(reader, read(reader))

  override fun readUtf16(reader: Utf16JsonReader): Any = operations.constructByte(reader, read(reader))

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructByte(reader, read(reader))

  private fun read(reader: org.apache.fory.json.reader.JsonReader): Byte {
    val value = reader.readUnsignedInt()
    if (Integer.compareUnsigned(value, UByte.MAX_VALUE.toInt()) > 0) {
      throw ForyJsonException("UByte overflow")
    }
    return value.toByte()
  }
}

private class UShortCapability(
  private val operations: KotlinShortValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) =
    writer.writeUnsignedInt(operations.unboxShort(value).toInt() and 0xffff)

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) =
    writer.writeUnsignedInt(operations.unboxShort(value).toInt() and 0xffff)

  override fun readLatin1(reader: Latin1JsonReader): Any = operations.constructShort(reader, read(reader))

  override fun readUtf16(reader: Utf16JsonReader): Any = operations.constructShort(reader, read(reader))

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructShort(reader, read(reader))

  private fun read(reader: org.apache.fory.json.reader.JsonReader): Short {
    val value = reader.readUnsignedInt()
    if (Integer.compareUnsigned(value, UShort.MAX_VALUE.toInt()) > 0) {
      throw ForyJsonException("UShort overflow")
    }
    return value.toShort()
  }
}

private class UIntCapability(
  private val operations: KotlinIntValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeUnsignedInt(operations.unboxInt(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeUnsignedInt(operations.unboxInt(value))

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructInt(reader, reader.readUnsignedInt())

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructInt(reader, reader.readUnsignedInt())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructInt(reader, reader.readUnsignedInt())
}

private class ULongCapability(
  private val operations: KotlinLongValueClassOperations<Any>,
) : KotlinValueClassCapability {
  override fun writeString(writer: StringJsonWriter, value: Any) = writer.writeUnsignedLong(operations.unboxLong(value))

  override fun writeUtf8(writer: Utf8JsonWriter, value: Any) = writer.writeUnsignedLong(operations.unboxLong(value))

  override fun readLatin1(reader: Latin1JsonReader): Any =
    operations.constructLong(reader, reader.readUnsignedLong())

  override fun readUtf16(reader: Utf16JsonReader): Any =
    operations.constructLong(reader, reader.readUnsignedLong())

  override fun readUtf8(reader: Utf8JsonReader): Any = operations.constructLong(reader, reader.readUnsignedLong())
}
