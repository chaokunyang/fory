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

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimedValue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.DirectUnboxedValueCodec
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.writer.JsonWriter
import org.apache.fory.meta.TypeExtMeta
import org.apache.fory.reflect.TypeRef
import org.apache.fory.serializer.GraphMemoryEstimates
import org.apache.fory.type.Types

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalUuidApi::class)
class KotlinBuiltInCodecsTest {
  private enum class EntryValue {
    ONE,
  }

  data class NullOnly(val value: Nothing?)

  data class TimedNode(val name: String, val next: TimedValue<TimedNode>?)

  data class UnsignedArrayHolder(
    val tag: String,
    val direct: UByteArray,
    val nullable: UShortArray?,
    val nested: List<UIntArray>,
  )

  data class UnsignedScalarHolder(
    val ubyte: UByte,
    val ushort: UShort,
    val uint: UInt,
    val ulong: ULong,
    val nullable: UInt?,
  )

  data class DurationHolder(
    val zero: Duration,
    val negative: Duration,
    val nullable: Duration?,
    val tag: String,
  )

  private val fory = ForyJsonKotlin.builder().withAsyncCompilation(false).build()

  @Test
  fun products() {
    val pairType = jsonTypeRef<Pair<Int?, String>>()
    val pair = Pair(null, "right")
    assertEquals("{\"first\":null,\"second\":\"right\"}", fory.toJson(pair, pairType))
    assertEquals(pair, fory.fromJson(fory.toJson(pair, pairType), pairType))

    val tripleType = jsonTypeRef<Triple<Int, String, Boolean>>()
    val triple = Triple(1, "two", true)
    assertEquals(triple, fory.fromJson(fory.toJson(triple, tripleType), tripleType))
    assertFailsWith<ForyJsonException> { fory.fromJson("{}", Pair::class.java) }
  }

  @Test
  fun ranges() {
    assertRoundTrip('a'..'z', jsonTypeRef<CharRange>())
    assertRoundTrip('\u4e2d'..'\u9fa0', jsonTypeRef<CharRange>())
    assertRoundTrip(-4..9, jsonTypeRef<IntRange>())
    assertRoundTrip(-4L..9L, jsonTypeRef<LongRange>())
    assertRoundTrip(0u..UInt.MAX_VALUE, jsonTypeRef<UIntRange>())
    assertRoundTrip(0uL..ULong.MAX_VALUE, jsonTypeRef<ULongRange>())
    val intType = jsonTypeRef<IntRange>()
    assertEquals("{\"start\":-4,\"endInclusive\":9}", fory.toJson(-4..9, intType))
    assertEquals(-4..9, fory.fromJson(fory.toJsonBytes(-4..9, intType), intType))
    assertEquals(
      0u..UInt.MAX_VALUE,
      fory.fromJson(
        "{\"endInclusive\":4294967295,\"start\":0}",
        jsonTypeRef<UIntRange>(),
      ),
    )
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"start\":1}", intType)
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"start\":1,\"start\":2,\"endInclusive\":3}", intType)
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"start\":1,\"unknown\":3}", intType)
    }

    val ownerBytes = GraphMemoryEstimates.shallowObjectBytes(IntRange::class.java)
    val exact =
      ForyJsonKotlin.builder().withMaxGraphMemoryBytes(ownerBytes.toLong()).build()
    assertEquals(1..2, exact.fromJson("{\"start\":1,\"endInclusive\":2}", intType))
    val short =
      ForyJsonKotlin.builder().withMaxGraphMemoryBytes((ownerBytes - 1).toLong()).build()
    assertFailsWith<ForyJsonException> {
      short.fromJson("{\"start\":1,\"endInclusive\":2}", intType)
    }
  }

  @Test
  fun progressions() {
    assertRoundTrip(CharProgression.fromClosedRange('a', 'z', 3), jsonTypeRef<CharProgression>())
    assertRoundTrip(IntProgression.fromClosedRange(-10, 20, 3), jsonTypeRef<IntProgression>())
    assertRoundTrip(LongProgression.fromClosedRange(20, -10, -3), jsonTypeRef<LongProgression>())
    assertRoundTrip(UIntProgression.fromClosedRange(1u, 20u, 3), jsonTypeRef<UIntProgression>())
    assertRoundTrip(ULongProgression.fromClosedRange(20uL, 1uL, -3), jsonTypeRef<ULongProgression>())

    assertFailsWith<ForyJsonException> {
      fory.fromJson(
        "{\"first\":1,\"last\":8,\"step\":3}",
        jsonTypeRef<IntProgression>(),
      )
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson(
        "{\"first\":1,\"last\":7,\"step\":0}",
        jsonTypeRef<IntProgression>(),
      )
    }
    val type = jsonTypeRef<IntProgression>()
    val ownerBytes = GraphMemoryEstimates.shallowObjectBytes(IntProgression::class.java)
    val exact =
      ForyJsonKotlin.builder().withMaxGraphMemoryBytes(ownerBytes.toLong()).build()
    assertEquals(
      IntProgression.fromClosedRange(1, 7, 3),
      exact.fromJson("{\"first\":1,\"last\":7,\"step\":3}", type),
    )
    val short =
      ForyJsonKotlin.builder().withMaxGraphMemoryBytes((ownerBytes - 1).toLong()).build()
    assertFailsWith<ForyJsonException> {
      short.fromJson("{\"first\":1,\"last\":7,\"step\":3}", type)
    }
  }

  @Test
  fun unsignedMapKeys() {
    assertMapRoundTrip(
      linkedMapOf(0.toUByte() to "zero", UByte.MAX_VALUE to "max"),
      jsonTypeRef<Map<UByte, String>>(),
      "{\"0\":\"zero\",\"255\":\"max\"}",
    )
    assertMapRoundTrip(
      linkedMapOf(0.toUShort() to "zero", UShort.MAX_VALUE to "max"),
      jsonTypeRef<Map<UShort, String>>(),
      "{\"0\":\"zero\",\"65535\":\"max\"}",
    )
    assertMapRoundTrip(
      linkedMapOf(0u to "zero", UInt.MAX_VALUE to "max"),
      jsonTypeRef<Map<UInt, String>>(),
      "{\"0\":\"zero\",\"4294967295\":\"max\"}",
    )
    assertMapRoundTrip(
      linkedMapOf(0uL to "zero", ULong.MAX_VALUE to "max"),
      jsonTypeRef<Map<ULong, String>>(),
      "{\"0\":\"zero\",\"18446744073709551615\":\"max\"}",
    )
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"4294967295\":\"bad\"}", jsonTypeRef<Map<UByte, String>>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"4294967295\":\"bad\"}", jsonTypeRef<Map<UShort, String>>())
    }
    for ((carrier, typeId) in
      listOf(
        java.lang.Byte.TYPE to Types.UINT8,
        java.lang.Short.TYPE to Types.UINT16,
        java.lang.Integer.TYPE to Types.UINT32,
        java.lang.Long.TYPE to Types.UINT64,
      )) {
      val physical = TypeRef.of(carrier, TypeExtMeta.of(typeId, false, false))
      assertTrue(KotlinMapKeyCodecs.keyCodec(physical) != null)
    }
    val mismatched = TypeRef.of(String::class.java, TypeExtMeta.of(Types.UINT32, false, false))
    assertFailsWith<ForyJsonException> { KotlinMapKeyCodecs.keyCodec(mismatched) }
  }

  @Test
  fun unsignedBounds() {
    assertEquals(UByte.MAX_VALUE, fory.fromJson("255", jsonTypeRef<UByte>()))
    assertEquals(UShort.MAX_VALUE, fory.fromJson("65535", jsonTypeRef<UShort>()))
    assertEquals(UInt.MAX_VALUE, fory.fromJson("4294967295", jsonTypeRef<UInt>()))
    assertEquals(ULong.MAX_VALUE, fory.fromJson("18446744073709551615", jsonTypeRef<ULong>()))
    assertFailsWith<ForyJsonException> {
      fory.fromJson("4294967295", jsonTypeRef<UByte>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("4294967295", jsonTypeRef<UShort>())
    }

    val interpreted = ForyJsonKotlin.builder().withCodegen(false).build()
    val type = jsonTypeRef<UnsignedScalarHolder>()
    val value =
      UnsignedScalarHolder(
        UByte.MAX_VALUE,
        UShort.MAX_VALUE,
        UInt.MAX_VALUE,
        ULong.MAX_VALUE,
        null,
      )
    assertEquals(value, interpreted.fromJson(interpreted.toJson(value, type), type))
    assertEquals(null, interpreted.fromJson("null", jsonTypeRef<UInt?>()))
  }

  @Test
  fun unsignedArrays() {
    val ubytes = ubyteArrayOf(0u, UByte.MAX_VALUE)
    val ushorts = ushortArrayOf(0u, UShort.MAX_VALUE)
    val uints = uintArrayOf(0u, UInt.MAX_VALUE)
    val ulongs = ulongArrayOf(0u, ULong.MAX_VALUE)
    assertTrue(ubytes.contentEquals(roundTrip(ubytes, jsonTypeRef<UByteArray>())))
    assertTrue(ushorts.contentEquals(roundTrip(ushorts, jsonTypeRef<UShortArray>())))
    assertTrue(uints.contentEquals(roundTrip(uints, jsonTypeRef<UIntArray>())))
    assertTrue(ulongs.contentEquals(roundTrip(ulongs, jsonTypeRef<ULongArray>())))
  }

  @Test
  fun unsignedArrayRepresentations() {
    val bytes = ubyteArrayOf(0u, UByte.MAX_VALUE)
    val rootType = jsonTypeRef<UByteArray>()
    val text = fory.toJson(bytes, rootType)
    assertEquals("[0,255]", text)
    assertTrue(bytes.contentEquals(fory.fromJson(text, rootType)))

    val utf8 = fory.toJsonBytes(bytes, rootType)
    assertEquals(text, utf8.toString(Charsets.UTF_8))
    assertTrue(bytes.contentEquals(fory.fromJson(utf8, rootType)))

    val holderType = jsonTypeRef<UnsignedArrayHolder>()
    val holder =
      UnsignedArrayHolder(
        "\u4e2d",
        bytes,
        ushortArrayOf(0u, UShort.MAX_VALUE),
        listOf(uintArrayOf(0u, UInt.MAX_VALUE)),
      )
    val decoded = fory.fromJson(fory.toJson(holder, holderType), holderType)
    assertEquals(holder.tag, decoded.tag)
    assertTrue(holder.direct.contentEquals(decoded.direct))
    assertTrue(holder.nullable!!.contentEquals(decoded.nullable!!))
    assertTrue(holder.nested.single().contentEquals(decoded.nested.single()))
  }

  @Test
  fun unsignedArrayOverflow() {
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[256]", jsonTypeRef<UByteArray>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[65536]", jsonTypeRef<UShortArray>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[4294967296]", jsonTypeRef<UIntArray>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[18446744073709551616]", jsonTypeRef<ULongArray>())
    }
    val mismatched =
      TypeRef.of(UByteArray::class.java, TypeExtMeta.of(Types.UINT16_ARRAY, false, false))
    assertFailsWith<ForyJsonException> { fory.fromJson("[]", mismatched) }
  }

  @Test
  fun unsignedArrayBudget() {
    val arrayBytes = GraphMemoryEstimates.objectArrayBytes() + Byte.SIZE_BYTES
    val wrapperBytes = GraphMemoryEstimates.shallowObjectBytes(UByteArray::class.java)
    val type = jsonTypeRef<UByteArray>()
    val exact =
      ForyJsonKotlin.builder()
        .withAsyncCompilation(false)
        .withMaxGraphMemoryBytes((arrayBytes + wrapperBytes).toLong())
        .build()
    assertTrue(ubyteArrayOf(1u).contentEquals(exact.fromJson("[1]", type)))

    val short =
      ForyJsonKotlin.builder()
        .withAsyncCompilation(false)
        .withMaxGraphMemoryBytes((arrayBytes + wrapperBytes - 1).toLong())
        .build()
    assertFailsWith<ForyJsonException> { short.fromJson("[1]", type) }
  }

  @Test
  fun duration() {
    val values =
      listOf(
        Duration.ZERO,
        1.nanoseconds,
        -1.nanoseconds,
        999_999_999.nanoseconds,
        -(1.seconds + 1.nanoseconds),
        49.hours + 2.minutes + 3.seconds + 456_789.nanoseconds,
        Long.MAX_VALUE.nanoseconds,
        Duration.INFINITE,
        -Duration.INFINITE,
      )
    for (value in values) {
      assertEquals("\"${value.toIsoString()}\"", fory.toJson(value, jsonTypeRef<Duration>()))
      assertRoundTrip(value, jsonTypeRef<Duration>())
    }
    for (text in
      listOf(
        "+PT1S",
        "P1D",
        "P1DT2H3M4.1234567899S",
        "PT61M",
        "-PT0.000000001S",
        "PT-1H+30M",
        "-PT-1H+30M",
        "P+1DT-2H+3M-4.0000000005S",
        "PT0.0000000005S",
        "PT0.9999999995S",
        "PT9999999999999H",
        "PT10000000000000H",
      )) {
      assertEquals(
        Duration.parseIsoString(text),
        fory.fromJson("\"$text\"", jsonTypeRef<Duration>()),
      )
    }
    for (text in
      listOf(
        "PT",
        "P1DT",
        "PT1.5H",
        "PT1H1H",
        "PT1M1H",
        "P9999999999999DT-9999999999999H",
      )) {
      assertFailsWith<IllegalArgumentException> { Duration.parseIsoString(text) }
      assertFailsWith<ForyJsonException> {
        fory.fromJson("\"$text\"", jsonTypeRef<Duration>())
      }
    }
    assertEquals(1.seconds, fory.fromJson("\"PT\\u0031S\"", jsonTypeRef<Duration>()))
    assertEquals(2.seconds, fory.fromJson("\"PT2S\"", jsonTypeRef<Duration>()))

    val direct = KotlinTemporalCodecs.create(jsonTypeRef<Duration>()) as DirectUnboxedValueCodec
    assertEquals(java.lang.Long.TYPE, direct.carrierType())
    val readMethod = direct.readCarrierMethod()
    assertTrue(Modifier.isStatic(readMethod.modifiers))
    assertEquals(java.lang.Long.TYPE, readMethod.returnType)
    assertEquals(listOf(JsonReader::class.java), readMethod.parameterTypes.toList())
    val writeMethod = direct.writeCarrierMethod()
    assertTrue(Modifier.isStatic(writeMethod.modifiers))
    assertEquals(java.lang.Void.TYPE, writeMethod.returnType)
    assertEquals(
      listOf(JsonWriter::class.java, java.lang.Long.TYPE),
      writeMethod.parameterTypes.toList(),
    )

    val latin1Holder = DurationHolder(Duration.ZERO, -1.nanoseconds, null, "ascii")
    val utf16Holder = latin1Holder.copy(tag = "\u4e2d")
    val holderType = jsonTypeRef<DurationHolder>()
    val latin1Json =
      "{\"zero\":\"PT0S\",\"negative\":\"-PT0.000000001S\",\"nullable\":null,\"tag\":\"ascii\"}"
    val utf16Json =
      "{\"zero\":\"PT0S\",\"negative\":\"-PT0.000000001S\",\"nullable\":null,\"tag\":\"\u4e2d\"}"
    forEachJsonMode { json ->
      assertEquals(latin1Json, json.toJson(latin1Holder, holderType))
      assertEquals(latin1Holder, json.fromJson(latin1Json, holderType))
      assertEquals(utf16Holder, json.fromJson(utf16Json, holderType))
      val utf8 = json.toJsonBytes(latin1Holder, holderType)
      assertEquals(latin1Json, utf8.toString(Charsets.UTF_8))
      assertEquals(latin1Holder, json.fromJson(utf8, holderType))
    }
  }

  @Test
  fun instant() {
    val values =
      listOf(
        Instant.fromEpochSeconds(0),
        Instant.fromEpochSeconds(-1, 1),
        Instant.fromEpochSeconds(1, 999_999_999),
        Instant.fromEpochSeconds(-31_557_014_167_219_200L),
        Instant.fromEpochSeconds(31_556_889_864_403_199L, 999_999_999),
      )
    for (value in values) {
      assertEquals("\"$value\"", fory.toJson(value, jsonTypeRef<Instant>()))
      assertRoundTrip(value, jsonTypeRef<Instant>())
    }
    for (text in
      listOf(
        "1970-01-01T00:00:00Z",
        "1970-01-01t00:00:00z",
        "1970-01-01T01:00:00+01",
        "1970-01-01T01:30:00+01:30",
        "1970-01-01T01:02:03+01:02:03",
        "+10000-01-01T00:00:00.123456789Z",
      )) {
      assertEquals(Instant.parse(text), fory.fromJson("\"$text\"", jsonTypeRef<Instant>()))
    }
    for (text in
      listOf(
        "1970-01-01T00:00Z",
        "1970-01-01T00:00:60Z",
        "1970-01-01T24:00:00Z",
        "1970-01-01T00:00:00.1234567890Z",
        "1970-01-01T00:00:00+18:01",
        "-1000000000-01-01T00:00:00+00:00:01",
        "+1000000000-12-31T23:59:59-00:00:01",
      )) {
      assertFailsWith<IllegalArgumentException> { Instant.parse(text) }
      assertFailsWith<ForyJsonException> {
        fory.fromJson("\"$text\"", jsonTypeRef<Instant>())
      }
    }
  }

  @Test
  fun uuid() {
    val value = Uuid.fromLongs(0x0011223344556677L, 0x8899aabbccddeeffuL.toLong())
    val json = "\"00112233-4455-6677-8899-aabbccddeeff\""
    assertEquals(json, fory.toJson(value, jsonTypeRef<Uuid>()))
    assertEquals(value, fory.fromJson(json, jsonTypeRef<Uuid>()))
    assertEquals(
      value,
      fory.fromJson("\"00112233-4455-6677-8899-AABBCCDDEEFF\"", jsonTypeRef<Uuid>()),
    )
    assertEquals(
      value,
      fory.fromJson("\"00112233-4455-6677-8899-\\u0061abbccddeeff\"", jsonTypeRef<Uuid>()),
    )
    for (text in
      listOf(
        "00112233445566778899aabbccddeeff",
        "{00112233-4455-6677-8899-aabbccddeeff}",
        "00112233-4455-6677-8899-aabbccddeefg",
      )) {
      assertFailsWith<ForyJsonException> {
        fory.fromJson("\"$text\"", jsonTypeRef<Uuid>())
      }
    }
  }

  @Test
  fun timedValue() {
    val type = jsonTypeRef<TimedValue<String>>()
    val value = TimedValue("done", 2.seconds + 17.nanoseconds)
    assertEquals(value, fory.fromJson(fory.toJson(value, type), type))
    val nullableType = jsonTypeRef<TimedValue<String?>>()
    val nullable = TimedValue<String?>(null, -1.nanoseconds)
    assertEquals(nullable, fory.fromJson(fory.toJson(nullable, nullableType), nullableType))
    val nullableUnitType = jsonTypeRef<TimedValue<Unit?>>()
    assertRoundTrip(TimedValue<Unit?>(null, 1.nanoseconds), nullableUnitType)
    assertRoundTrip(TimedValue<Unit?>(Unit, 2.nanoseconds), nullableUnitType)
    val recursiveType = jsonTypeRef<TimedNode>()
    val recursive = TimedNode("outer", TimedValue(TimedNode("leaf", null), 3.seconds))
    assertEquals(recursive, fory.fromJson(fory.toJson(recursive, recursiveType), recursiveType))
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"value\":\"bad\",\"duration\":null}", type)
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"value\":null,\"duration\":\"PT1S\"}", type)
    }
    @Suppress("UNCHECKED_CAST")
    val invalid = TimedValue(null, 1.seconds) as TimedValue<String>
    assertFailsWith<ForyJsonException> {
      fory.toJson(invalid, type)
    }
    assertEquals(value, fory.fromJson(fory.toJson(value, type), type))
  }

  @Test
  fun nullOnly() {
    val value = NullOnly(null)
    assertEquals(value, fory.fromJson(fory.toJson(value, jsonTypeRef<NullOnly>()), jsonTypeRef<NullOnly>()))
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{\"value\":0}", jsonTypeRef<NullOnly>())
    }
  }

  @Test
  fun unitNullability() {
    val nullableType = jsonTypeRef<Unit?>()
    assertEquals("null", fory.toJson(null, nullableType))
    assertEquals(null, fory.fromJson("null", nullableType))
    assertEquals("{}", fory.toJson(Unit, nullableType))
    assertEquals(Unit, fory.fromJson("{}", nullableType))
    assertFailsWith<ForyJsonException> {
      fory.fromJson("null", jsonTypeRef<Unit>())
    }
  }

  @Test
  fun rejectedTypes() {
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[]", jsonTypeRef<Sequence<Int>>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("\"a+\"", jsonTypeRef<Regex>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("{}", jsonTypeRef<kotlin.random.Random>())
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[]", emptyList<Int>()::class.java)
    }
    assertFailsWith<ForyJsonException> {
      fory.fromJson("[]", EntryValue.entries::class.java)
    }
  }

  private fun <K> assertMapRoundTrip(
    value: Map<K, String>,
    type: org.apache.fory.reflect.TypeRef<Map<K, String>>,
    expectedJson: String,
  ) {
    val json = fory.toJson(value, type)
    assertEquals(expectedJson, json)
    assertEquals(value, fory.fromJson(json, type))
  }

  private fun <T> assertRoundTrip(value: T, type: org.apache.fory.reflect.TypeRef<T>) {
    assertEquals(value, roundTrip(value, type))
  }

  private fun <T> roundTrip(value: T, type: org.apache.fory.reflect.TypeRef<T>): T =
    fory.fromJson(fory.toJson(value, type), type)
}
