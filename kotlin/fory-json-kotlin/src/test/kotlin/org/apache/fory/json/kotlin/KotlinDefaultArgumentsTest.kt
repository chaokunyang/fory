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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import org.apache.fory.json.ForyJson
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.annotation.JsonCreator
import org.apache.fory.json.annotation.JsonInclude
import org.apache.fory.json.annotation.JsonMixin
import org.apache.fory.json.annotation.JsonProperty
import org.apache.fory.json.annotation.JsonProperty.Include
import org.apache.fory.json.resolver.JsonTypeResolver
import org.apache.fory.reflect.ReflectionUtils

class KotlinDefaultArgumentsTest {
  private fun assertWriterGeneration(json: ForyJson, model: Class<*>, enabled: Boolean) {
    val slots = ReflectionUtils.getObjectFieldValue(json, "slots") as Array<*>
    val state = ReflectionUtils.getObjectFieldValue(slots[0], "state")
    val resolver = ReflectionUtils.getObjectFieldValue(state, "typeResolver") as JsonTypeResolver
    resolver.lockJIT()
    try {
      val info = resolver.getRuntimeTypeInfo(model)
      val owner = resolver.canonicalObjectCodec(info)
      assertEquals(enabled, info.stringWriter() !== owner)
      assertEquals(enabled, info.utf8Writer() !== owner)
    } finally {
      resolver.unlockJIT()
    }
  }

  @JsonInclude(Include.NON_DEFAULT)
  data class Limits(
    val low: Int = 1,
    @JsonProperty(include = Include.ALWAYS) val high: Int = low + 1
  )

  @JsonInclude(Include.NON_DEFAULT)
  data class ReferenceDefault(
    val value: Int = 1,
    val label: String? = "new",
    val values: MutableList<Int> = mutableListOf(1)
  ) {
    init {
      constructions++
    }

    companion object {
      var constructions = 0
    }
  }

  data class RequiredDefault(
    val required: Int,
    @JsonProperty(include = Include.NON_DEFAULT) val value: Int = 1
  )

  @JsonInclude(Include.NON_DEFAULT)
  class DeferredDefault(val count: Int = 3) {
    lateinit var required: String
  }

  @JsonInclude(Include.NON_DEFAULT)
  data class RequiredValues(
    val count: Int,
    @JsonProperty(include = Include.NON_DEFAULT) val flag: Boolean,
    val label: String?,
    val items: List<Int>
  )

  @JsonMixin(target = RequiredDefault::class)
  @JsonInclude(Include.NON_DEFAULT)
  abstract class RequiredDefaultMixin(
    val required: Int,
    // Match the target's default use sites: Kotlin 2.4 also annotates the backing field.
    @JsonProperty(include = Include.ALWAYS) val value: Int
  )

  data class PlainDefault(val value: Int = 1) {
    init {
      constructions++
    }

    companion object {
      var constructions = 0
    }
  }

  @JsonMixin(target = PlainDefault::class) @JsonInclude(Include.NON_DEFAULT) interface DefaultMixin

  @JsonInclude(Include.NON_DEFAULT)
  class FailingDefault(val value: Int = 1) {
    init {
      check(!fail) { "constructor failed" }
    }

    companion object {
      var fail = false
    }
  }

  @JsonInclude(Include.NON_DEFAULT)
  class SelectedCreator @JsonCreator("value") constructor(val value: Int) {
    constructor() : this(1)
  }

  @Test
  fun defaultInclusion() {
    for (codegen in listOf(false, true)) {
      val json = ForyJsonKotlin.builder().withCodegen(codegen).withAsyncCompilation(false).build()
      assertEquals("{\"high\":2}", json.toJson(Limits()))
      assertEquals("{\"low\":5,\"high\":2}", json.toJson(Limits(5, 2)))
      assertEquals("{\"low\":5,\"high\":2}", json.toJsonBytes(Limits(5, 2)).decodeToString())
      assertEquals(Limits(5, 6), json.fromJson("{\"low\":5}", Limits::class.java))
      assertEquals(
        Limits(5, 2),
        json.fromJson("{\"low\":5,\"high\":2}".encodeToByteArray(), Limits::class.java)
      )
      val value = ReferenceDefault(label = null)
      val constructions = ReferenceDefault.constructions
      val expected = "{\"label\":null}"
      assertEquals(expected, json.toJson(value))
      assertEquals(expected, json.toJsonBytes(value).decodeToString())
      assertEquals("{\n  \"label\" : null\n}", json.toPrettyJson(value))
      assertEquals(json.toPrettyJson(value), json.toPrettyJsonBytes(value).decodeToString())
      assertEquals(constructions + 1, ReferenceDefault.constructions)
      assertWriterGeneration(json, ReferenceDefault::class.java, codegen)
      val first = json.fromJson("{}", ReferenceDefault::class.java)
      // Declared reading and dynamic writing own distinct language-model metadata.
      val afterRead = ReferenceDefault.constructions
      val second = json.fromJson("{}".encodeToByteArray(), ReferenceDefault::class.java)
      assertNotSame(first.values, second.values)
      first.values += 2
      assertEquals(listOf(1), second.values)
      assertEquals("{}", json.toJson(second))
      assertEquals(afterRead + 1, ReferenceDefault.constructions)
      assertFailsWith<ForyJsonException> {
        json.fromJson("{\"value\":null}", ReferenceDefault::class.java)
      }
      assertFailsWith<ForyJsonException> {
        json.fromJson("{\"values\":null}".encodeToByteArray(), ReferenceDefault::class.java)
      }
      assertFailsWith<ForyJsonException> { json.toJson(RequiredDefault(1)) }
      assertEquals("{\"value\":1}", json.toJson(SelectedCreator()))
      assertEquals("{\"value\":0}", json.toJsonBytes(SelectedCreator(0)).decodeToString())
      val failing = FailingDefault()
      FailingDefault.fail = true
      try {
        assertFailsWith<ForyJsonException> { json.toJson(failing) }
        assertFailsWith<ForyJsonException> { json.toJsonBytes(failing) }
      } finally {
        FailingDefault.fail = false
      }
      val plain = PlainDefault()
      val calls = PlainDefault.constructions
      assertEquals("{\"value\":1}", json.toJson(plain))
      assertEquals(calls, PlainDefault.constructions)
      val mixed =
        ForyJsonKotlin.builder()
          .withCodegen(codegen)
          .withAsyncCompilation(false)
          .registerMixin(DefaultMixin::class.java)
          .build()
      assertEquals("{}", mixed.toJson(plain))
      assertEquals("{}", mixed.toJsonBytes(plain).decodeToString())
      assertEquals(calls + 1, PlainDefault.constructions)
    }
  }

  @Test
  fun requiredInclusion() {
    for (codegen in listOf(false, true)) {
      val json = ForyJsonKotlin.builder().withCodegen(codegen).withAsyncCompilation(false).build()
      val value = RequiredValues(0, false, null, emptyList())
      val expected = "{\"count\":0,\"flag\":false,\"label\":null,\"items\":[]}"
      val pretty =
        "{\n  \"count\" : 0,\n  \"flag\" : false,\n  \"label\" : null,\n  \"items\" : [ ]\n}"
      assertEquals(expected, json.toJson(value))
      assertEquals(expected, json.toJsonBytes(value).decodeToString())
      assertEquals(pretty, json.toPrettyJson(value))
      assertEquals(pretty, json.toPrettyJsonBytes(value).decodeToString())
      assertEquals(value, json.fromJson(expected, RequiredValues::class.java))
      assertEquals(value, json.fromJson(pretty.encodeToByteArray(), RequiredValues::class.java))
      assertWriterGeneration(json, RequiredValues::class.java, codegen)

      val mixed =
        ForyJsonKotlin.builder()
          .withCodegen(codegen)
          .withAsyncCompilation(false)
          .registerMixin(RequiredDefaultMixin::class.java)
          .build()
      assertEquals("{\"required\":0,\"value\":1}", mixed.toJson(RequiredDefault(0)))
      assertEquals(
        "{\"required\":0,\"value\":1}",
        mixed.toJsonBytes(RequiredDefault(0)).decodeToString()
      )
      assertWriterGeneration(mixed, RequiredDefault::class.java, codegen)
    }
  }

  @Test
  fun deferredDefaultInclusion() {
    for (codegen in listOf(false, true)) {
      val json = ForyJsonKotlin.builder().withCodegen(codegen).withAsyncCompilation(false).build()
      for (count in listOf(3, 4)) {
        val value = DeferredDefault(count).apply { required = "ready" }
        val expected =
          if (count == 3) "{\"required\":\"ready\"}" else "{\"count\":4,\"required\":\"ready\"}"
        val pretty =
          if (count == 3) "{\n  \"required\" : \"ready\"\n}"
          else "{\n  \"count\" : 4,\n  \"required\" : \"ready\"\n}"
        assertEquals(expected, json.toJson(value))
        assertEquals(expected, json.toJsonBytes(value).decodeToString())
        assertEquals(pretty, json.toPrettyJson(value))
        assertEquals(pretty, json.toPrettyJsonBytes(value).decodeToString())
        for (decoded in
          listOf(
            json.fromJson(expected, DeferredDefault::class.java),
            json.fromJson(pretty.encodeToByteArray(), DeferredDefault::class.java)
          )) {
          assertEquals(count, decoded.count)
          assertEquals("ready", decoded.required)
        }
      }
      assertWriterGeneration(json, DeferredDefault::class.java, codegen)
      assertFailsWith<ForyJsonException> { json.fromJson("{}", DeferredDefault::class.java) }
      assertFailsWith<ForyJsonException> {
        json.fromJson("{}".encodeToByteArray(), DeferredDefault::class.java)
      }
    }
  }

  @JsonInclude(Include.NON_DEFAULT)
  class MaskDefaults(
    val p0: Int = 0,
    val p1: Int = 1,
    val p2: Int = 2,
    val p3: Int = 3,
    val p4: Int = 4,
    val p5: Int = 5,
    val p6: Int = 6,
    val p7: Int = 7,
    val p8: Int = 8,
    val p9: Int = 9,
    val p10: Int = 10,
    val p11: Int = 11,
    val p12: Int = 12,
    val p13: Int = 13,
    val p14: Int = 14,
    val p15: Int = 15,
    val p16: Int = 16,
    val p17: Int = 17,
    val p18: Int = 18,
    val p19: Int = 19,
    val p20: Int = 20,
    val p21: Int = 21,
    val p22: Int = 22,
    val p23: Int = 23,
    val p24: Int = 24,
    val p25: Int = 25,
    val p26: Int = 26,
    val p27: Int = 27,
    val p28: Int = 28,
    val p29: Int = 29,
    val p30: Int = 30,
    val p31: Int = 31,
    val p32: Int = 32,
    val p33: Int = 33,
    val p34: Int = 34,
    val p35: Int = 35,
    val p36: Int = 36,
    val p37: Int = 37,
    val p38: Int = 38,
    val p39: Int = 39,
    val p40: Int = 40,
    val p41: Int = 41,
    val p42: Int = 42,
    val p43: Int = 43,
    val p44: Int = 44,
    val p45: Int = 45,
    val p46: Int = 46,
    val p47: Int = 47,
    val p48: Int = 48,
    val p49: Int = 49,
    val p50: Int = 50,
    val p51: Int = 51,
    val p52: Int = 52,
    val p53: Int = 53,
    val p54: Int = 54,
    val p55: Int = 55,
    val p56: Int = 56,
    val p57: Int = 57,
    val p58: Int = 58,
    val p59: Int = 59,
    val p60: Int = 60,
    val p61: Int = 61,
    val p62: Int = 62,
    val p63: Int = 63,
    val p64: Int = 64,
  )

  @Test
  fun maskWords() {
    val fory = ForyJsonKotlin.builder().withAsyncCompilation(false).build()
    val type = jsonTypeRef<MaskDefaults>()
    val latin1 = "{\"p0\":100,\"p31\":131,\"p32\":132,\"p63\":163,\"p64\":164}"
    assertMaskValues(fory.fromJson(latin1, type))
    assertMaskValues(fory.fromJson(latin1.dropLast(1) + ",\"ignored\":\"漢\"}", type))
    assertMaskValues(fory.fromJson(latin1.toByteArray(), type))
    assertEquals("{}", fory.toJson(MaskDefaults()))
    val value = fory.fromJson(latin1, type)
    assertEquals(latin1, fory.toJson(value))
    assertEquals(latin1, fory.toJsonBytes(value).decodeToString())
    assertMaskValues(fory.fromJson(fory.toPrettyJson(value), type))
    assertMaskValues(fory.fromJson(fory.toPrettyJsonBytes(value), type))
  }

  private fun assertMaskValues(value: MaskDefaults) {
    assertEquals(100, value.p0)
    assertEquals(1, value.p1)
    assertEquals(30, value.p30)
    assertEquals(131, value.p31)
    assertEquals(132, value.p32)
    assertEquals(33, value.p33)
    assertEquals(62, value.p62)
    assertEquals(163, value.p63)
    assertEquals(164, value.p64)
  }
}
