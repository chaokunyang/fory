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
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.reader.Latin1JsonReader
import org.apache.fory.json.reader.Utf16JsonReader
import org.apache.fory.json.reader.Utf8JsonReader
import org.apache.fory.json.writer.JsonWriter
import org.apache.fory.json.writer.StringJsonWriter
import org.apache.fory.json.writer.Utf8JsonWriter
import org.apache.fory.type.Types

/** Width- and carrier-specialized codecs for Kotlin unsigned scalars. */
@OptIn(ExperimentalUnsignedTypes::class)
internal object KotlinUnsignedCodecs {
  fun scalar(typeId: Int, boxedResult: Boolean, nullable: Boolean): JsonValueCodec<Any?> =
    when (typeId) {
      Types.UINT8 ->
        if (nullable) UByteCodec.NULLABLE
        else if (boxedResult) UByteCodec.BOXED
        else UByteCarrierCodec
      Types.UINT16 ->
        if (nullable) UShortCodec.NULLABLE
        else if (boxedResult) UShortCodec.BOXED
        else UShortCarrierCodec
      Types.UINT32 ->
        if (nullable) UIntCodec.NULLABLE
        else if (boxedResult) UIntCodec.BOXED
        else UIntCarrierCodec
      Types.UINT64 ->
        if (nullable) ULongCodec.NULLABLE
        else if (boxedResult) ULongCodec.BOXED
        else ULongCarrierCodec
      else -> throw ForyJsonException("Unknown Kotlin unsigned JSON type id $typeId")
    }

  private class UByteCodec(private val nullable: Boolean) : JsonValueCodec<Any?> {
    companion object {
      val BOXED: JsonValueCodec<Any?> = UByteCodec(false)
      val NULLABLE: JsonValueCodec<Any?> = UByteCodec(true)
    }

    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) {
        writeNull(writer, nullable)
        return
      }
      writer.writeUnsignedInt((value as UByte).toInt())
    }

    private fun read(reader: JsonReader): Any? {
      if (reader.tryReadNull()) return readNull(nullable)
      val value = reader.readUnsignedInt()
      if (Integer.compareUnsigned(value, UByte.MAX_VALUE.toInt()) > 0) ubyteOverflow()
      return value.toUByte()
    }
  }

  private object UByteCarrierCodec : JsonValueCodec<Any?> {
    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) rejectNull()
      writer.writeUnsignedInt((value as Byte).toInt() and 0xff)
    }

    private fun read(reader: JsonReader): Any {
      if (reader.tryReadNull()) rejectNull()
      val value = reader.readUnsignedInt()
      if (Integer.compareUnsigned(value, UByte.MAX_VALUE.toInt()) > 0) ubyteOverflow()
      return value.toByte()
    }
  }

  private class UShortCodec(private val nullable: Boolean) : JsonValueCodec<Any?> {
    companion object {
      val BOXED: JsonValueCodec<Any?> = UShortCodec(false)
      val NULLABLE: JsonValueCodec<Any?> = UShortCodec(true)
    }

    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) {
        writeNull(writer, nullable)
        return
      }
      writer.writeUnsignedInt((value as UShort).toInt())
    }

    private fun read(reader: JsonReader): Any? {
      if (reader.tryReadNull()) return readNull(nullable)
      val value = reader.readUnsignedInt()
      if (Integer.compareUnsigned(value, UShort.MAX_VALUE.toInt()) > 0) ushortOverflow()
      return value.toUShort()
    }
  }

  private object UShortCarrierCodec : JsonValueCodec<Any?> {
    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) rejectNull()
      writer.writeUnsignedInt((value as Short).toInt() and 0xffff)
    }

    private fun read(reader: JsonReader): Any {
      if (reader.tryReadNull()) rejectNull()
      val value = reader.readUnsignedInt()
      if (Integer.compareUnsigned(value, UShort.MAX_VALUE.toInt()) > 0) ushortOverflow()
      return value.toShort()
    }
  }

  private class UIntCodec(private val nullable: Boolean) : JsonValueCodec<Any?> {
    companion object {
      val BOXED: JsonValueCodec<Any?> = UIntCodec(false)
      val NULLABLE: JsonValueCodec<Any?> = UIntCodec(true)
    }

    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) {
        writeNull(writer, nullable)
        return
      }
      writer.writeUnsignedInt((value as UInt).toInt())
    }

    private fun read(reader: JsonReader): Any? {
      if (reader.tryReadNull()) return readNull(nullable)
      return reader.readUnsignedInt().toUInt()
    }
  }

  private object UIntCarrierCodec : JsonValueCodec<Any?> {
    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) rejectNull()
      writer.writeUnsignedInt(value as Int)
    }

    private fun read(reader: JsonReader): Any {
      if (reader.tryReadNull()) rejectNull()
      return reader.readUnsignedInt()
    }
  }

  private class ULongCodec(private val nullable: Boolean) : JsonValueCodec<Any?> {
    companion object {
      val BOXED: JsonValueCodec<Any?> = ULongCodec(false)
      val NULLABLE: JsonValueCodec<Any?> = ULongCodec(true)
    }

    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) {
        writeNull(writer, nullable)
        return
      }
      writer.writeUnsignedLong((value as ULong).toLong())
    }

    private fun read(reader: JsonReader): Any? {
      if (reader.tryReadNull()) return readNull(nullable)
      return reader.readUnsignedLong().toULong()
    }
  }

  private object ULongCarrierCodec : JsonValueCodec<Any?> {
    override fun writeString(writer: StringJsonWriter, value: Any?) = write(writer, value)

    override fun writeUtf8(writer: Utf8JsonWriter, value: Any?) = write(writer, value)

    override fun readLatin1(reader: Latin1JsonReader): Any? = read(reader)

    override fun readUtf16(reader: Utf16JsonReader): Any? = read(reader)

    override fun readUtf8(reader: Utf8JsonReader): Any? = read(reader)

    private fun write(writer: JsonWriter, value: Any?) {
      if (value == null) rejectNull()
      writer.writeUnsignedLong(value as Long)
    }

    private fun read(reader: JsonReader): Any {
      if (reader.tryReadNull()) rejectNull()
      return reader.readUnsignedLong()
    }
  }

  private fun writeNull(writer: JsonWriter, nullable: Boolean) {
    if (!nullable) rejectNull()
    writer.writeNull()
  }

  private fun readNull(nullable: Boolean): Any? {
    if (!nullable) rejectNull()
    return null
  }

  private fun rejectNull(): Nothing =
    throw ForyJsonException("Kotlin unsigned value is not nullable")

  private fun ubyteOverflow(): Nothing = throw ForyJsonException("UByte overflow")

  private fun ushortOverflow(): Nothing = throw ForyJsonException("UShort overflow")
}
