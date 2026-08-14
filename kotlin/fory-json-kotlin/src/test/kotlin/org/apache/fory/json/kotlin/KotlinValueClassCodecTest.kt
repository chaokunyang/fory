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

import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.apache.fory.json.ForyJson
import org.apache.fory.json.ForyJsonException
import org.apache.fory.serializer.GraphMemoryEstimates

@JvmInline
internal value class PositiveId(internal val value: Long) {
  init {
    require(value >= 0) { "id must be non-negative" }
  }
}

@JvmInline internal value class NullableText(internal val value: String?)

@JvmInline internal value class GenericValue<T>(internal val value: T)

@JvmInline internal value class NestedId(internal val value: PositiveId)

@JvmInline internal value class GenericKey<T>(internal val value: T)

internal data class ValueClassHolder(
  internal val id: PositiveId,
  internal val nullableId: PositiveId?,
  internal val generic: GenericValue<String>,
  internal val defaultId: PositiveId = PositiveId(41),
)

internal data class NullableValueClassHolder(internal val text: NullableText)

class KotlinValueClassCodecTest {
  private fun fory(maxGraphMemoryBytes: Long = ForyJson.DEFAULT_MAX_GRAPH_MEMORY_BYTES): ForyJson =
    ForyJsonKotlin.builder()
      .withCodegen(false)
      .withMaxGraphMemoryBytes(maxGraphMemoryBytes)
      .build()

  @Test
  fun exactMetadata() {
    val shape = KotlinValueClassMetadata.inspect(jsonTypeRef<PositiveId>())
    assertEquals(PositiveId::class.java, shape.ownerClass)
    assertEquals(Long::class.javaPrimitiveType, shape.terminalType.rawType)
    assertEquals(listOf("(J)J"), shape.layers.map { it.constructorDescriptor })
    assertEquals(
      listOf("(J)Lorg/apache/fory/json/kotlin/PositiveId;"),
      shape.layers.map { it.boxDescriptor },
    )
    assertEquals(listOf("()J"), shape.layers.map { it.unboxDescriptor })
  }

  @Test
  fun rootRoundTrip() {
    val json = fory()
    val type = jsonTypeRef<PositiveId>()
    val value = PositiveId(19)
    assertEquals("19", json.toJson(value, type))
    assertEquals(value, json.fromJson("19", type))
    assertEquals(value, json.fromJson(json.toJsonBytes(value, type), type))
  }

  @Test
  fun constructorInvariant() {
    val json = fory()
    val failure =
      assertFailsWith<ForyJsonException> {
        json.fromJson("-1", jsonTypeRef<PositiveId>())
      }
    assertTrue(generateSequence(failure as Throwable?) { it.cause }.any {
      it is IllegalArgumentException && it.message == "id must be non-negative"
    })
    assertEquals(PositiveId(1), json.fromJson("1", jsonTypeRef<PositiveId>()))
  }

  @Test
  fun nullableOuter() {
    val json = fory()
    val type = jsonTypeRef<PositiveId?>()
    assertEquals("null", json.toJson(null, type))
    assertEquals(null, json.fromJson("null", type))
    assertEquals(PositiveId(7), json.fromJson("7", type))
  }

  @Test
  fun nullableUnderlying() {
    val json = fory()
    val type = jsonTypeRef<NullableText>()
    assertEquals(NullableText(null), json.fromJson("null", type))
    assertEquals("null", json.toJson(NullableText(null), type))
    assertEquals(NullableText("text"), json.fromJson("\"text\"", type))
  }

  @Test
  fun ambiguousNullability() {
    assertFailsWith<ForyJsonException> {
      KotlinValueClassMetadata.inspect(jsonTypeRef<NullableText?>())
    }
  }

  @Test
  fun genericSubstitution() {
    val nonNull = KotlinValueClassMetadata.inspect(jsonTypeRef<GenericValue<String>>())
    assertEquals(String::class.java, nonNull.terminalType.rawType)
    assertEquals(false, nonNull.terminalType.typeExtMeta.nullable())
    val nullable = KotlinValueClassMetadata.inspect(jsonTypeRef<GenericValue<String?>>())
    assertEquals(String::class.java, nullable.terminalType.rawType)
    assertEquals(true, nullable.terminalType.typeExtMeta.nullable())

    val json = fory()
    val type = jsonTypeRef<List<GenericValue<String>>>()
    val value = listOf(GenericValue("a"), GenericValue("b"))
    assertEquals(value, json.fromJson(json.toJson(value, type), type))
  }

  @Test
  fun nestedValueClass() {
    val shape = KotlinValueClassMetadata.inspect(jsonTypeRef<NestedId>())
    assertEquals(
      listOf(NestedId::class.java, PositiveId::class.java),
      shape.layers.map { it.ownerClass },
    )
    assertEquals(Long::class.javaPrimitiveType, shape.terminalType.rawType)

    val json = fory()
    val type = jsonTypeRef<NestedId>()
    val value = NestedId(PositiveId(23))
    assertEquals("23", json.toJson(value, type))
    assertEquals(value, json.fromJson("23", type))
    assertFailsWith<ForyJsonException> { json.fromJson("-1", type) }
  }

  @Test
  fun objectOccurrences() {
    forEachJsonMode { json ->
      val type = jsonTypeRef<ValueClassHolder>()
      val value =
        ValueClassHolder(
          PositiveId(7),
          PositiveId(8),
          GenericValue("value"),
          PositiveId(9),
        )
      assertEquals(value, json.fromJson(json.toJson(value, type), type))
      assertEquals(
        ValueClassHolder(PositiveId(1), null, GenericValue("default")),
        json.fromJson(
          """{"id":1,"nullableId":null,"generic":"default"}""",
          type,
        ),
      )
    }
  }

  @Test
  fun nullableUnderlyingProperty() {
    forEachJsonMode { json ->
      val type = jsonTypeRef<NullableValueClassHolder>()
      val value = NullableValueClassHolder(NullableText(null))
      assertEquals(value, json.fromJson(json.toJson(value, type), type))
      assertEquals(value, json.fromJson("""{"text":null}""", type))
    }
  }

  @Test
  fun mapKeyChain() {
    val json = fory()
    val signedType = jsonTypeRef<Map<GenericKey<Int>, String>>()
    val signed = linkedMapOf(GenericKey(31) to "value")
    assertEquals("{\"31\":\"value\"}", json.toJson(signed, signedType))
    assertEquals(signed, json.fromJson("{\"31\":\"value\"}", signedType))

    val unsignedType = jsonTypeRef<Map<GenericKey<UInt>, String>>()
    val unsigned = linkedMapOf(GenericKey(UInt.MAX_VALUE) to "maximum")
    assertEquals(
      "{\"4294967295\":\"maximum\"}",
      json.toJson(unsigned, unsignedType),
    )
    assertEquals(
      unsigned,
      json.fromJson("{\"4294967295\":\"maximum\"}", unsignedType),
    )
  }

  @Test
  fun mapKeyBoxIsCharged() {
    val mapBytes = GraphMemoryEstimates.shallowObjectBytes(LinkedHashMap::class.java)
    val wrapperBytes = GraphMemoryEstimates.shallowObjectBytes(GenericKey::class.java)
    val json = fory((mapBytes + wrapperBytes - 1).toLong())
    assertFailsWith<ForyJsonException> {
      json.fromJson("{\"1\":\"value\"}", jsonTypeRef<Map<GenericKey<Int>, String>>())
    }
  }

  @Test
  fun boxedRootIsCharged() {
    val wrapperBytes = GraphMemoryEstimates.shallowObjectBytes(PositiveId::class.java)
    val json = fory((wrapperBytes - 1).toLong())
    assertFailsWith<ForyJsonException> {
      json.fromJson("1", jsonTypeRef<PositiveId>())
    }
  }
}
