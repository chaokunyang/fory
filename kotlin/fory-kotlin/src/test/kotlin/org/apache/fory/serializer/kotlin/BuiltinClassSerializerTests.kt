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

package org.apache.fory.serializer.kotlin

import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import java.util.regex.Pattern
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.apache.fory.Fory
import org.apache.fory.exception.InsecureException
import org.apache.fory.kotlin.ForyKotlin
import org.apache.fory.memory.MemoryUtils
import org.apache.fory.resolver.ClassResolver
import org.apache.fory.type.Types
import org.testng.Assert

class BuiltinClassSerializerTests {
  @Test
  fun testSerializePair() {
    val fory: Fory = ForyKotlin.builder().withXlang(false).requireClassRegistration(true).build()

    val value = Pair(1, "one")
    Assert.assertEquals(value, fory.deserialize(fory.serialize(value)))
  }

  @Test
  fun testSerializeTriple() {
    val fory: Fory = ForyKotlin.builder().withXlang(false).requireClassRegistration(true).build()

    val value = Triple(1, "one", null)
    Assert.assertEquals(value, fory.deserialize(fory.serialize(value)))
  }

  @Suppress("UNCHECKED_CAST")
  @Test
  fun testSerializeResult() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .build()

    val value1 = Result.success(5)
    Assert.assertEquals(value1, fory.deserialize(fory.serialize(value1)))

    val value2 = Result.failure<Int>(IllegalStateException("my exception"))
    val roundtrip = fory.deserialize(fory.serialize(value2)) as Result<Int>
    Assert.assertTrue(roundtrip.isFailure)
    Assert.assertEquals(roundtrip.exceptionOrNull()!!.message, "my exception")
  }

  @Test
  fun testSerializeRanges() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .build()

    val value1 = 1..4
    Assert.assertEquals(value1, fory.deserialize(fory.serialize(value1)))

    val value2 = 4 downTo 1
    Assert.assertEquals(value2, fory.deserialize(fory.serialize(value2)))

    val value3 = 0 ..< 8 step 2
    Assert.assertEquals(value3, fory.deserialize(fory.serialize(value3)))

    val value4 = 'a'..'d'
    Assert.assertEquals(value4, fory.deserialize(fory.serialize(value4)))

    val value5 = 'c' downTo 'a'
    Assert.assertEquals(value5, fory.deserialize(fory.serialize(value5)))

    val value6 = 0L..10L
    Assert.assertEquals(value6, fory.deserialize(fory.serialize(value6)))

    val value7 = 4L downTo 0L
    Assert.assertEquals(value7, fory.deserialize(fory.serialize(value7)))

    val value8 = 0u..4u
    Assert.assertEquals(value8, fory.deserialize(fory.serialize(value8)))

    val value9 = 4u downTo 0u
    Assert.assertEquals(value9, fory.deserialize(fory.serialize(value9)))

    val value10 = 0uL..4uL
    Assert.assertEquals(value10, fory.deserialize(fory.serialize(value10)))

    val value11 = 4uL downTo 0uL
    Assert.assertEquals(value11, fory.deserialize(fory.serialize(value11)))
  }

  @Test
  fun testSerializeRandom() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .build()

    val value1 = Random(123)
    val roundtrip1 = fory.deserialize(fory.serialize(value1)) as Random

    Assert.assertEquals(roundtrip1.nextInt(), value1.nextInt())

    // The default random object will be roundtripped to the platform default random singleton
    // object.
    val value2 = Random.Default
    val roundtrip2 = fory.deserialize(fory.serialize(value2)) as Random

    Assert.assertEquals(roundtrip2, value2)
  }

  @Test
  fun testSerializeDuration() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .build()

    val value1 = Duration.ZERO
    Assert.assertEquals(value1, fory.deserialize(fory.serialize(value1)))

    val value2 = Duration.INFINITE
    Assert.assertEquals(value2, fory.deserialize(fory.serialize(value2)))

    val value3 = -Duration.INFINITE
    Assert.assertEquals(value3, fory.deserialize(fory.serialize(value3)))

    val value4 = 1.nanoseconds
    Assert.assertEquals(value4, fory.deserialize(fory.serialize(value4)))

    val value5 = 1.microseconds
    Assert.assertEquals(value5, fory.deserialize(fory.serialize(value5)))

    val value6 = 1.milliseconds
    Assert.assertEquals(value6, fory.deserialize(fory.serialize(value6)))

    val value7 = 3.141.milliseconds
    Assert.assertEquals(value7, fory.deserialize(fory.serialize(value7)))

    val value8 = 1.seconds
    Assert.assertEquals(value8, fory.deserialize(fory.serialize(value8)))

    val value9 = 3.141.seconds
    Assert.assertEquals(value9, fory.deserialize(fory.serialize(value9)))

    val value10 = 5.minutes
    Assert.assertEquals(value10, fory.deserialize(fory.serialize(value10)))

    val value11 = 60.minutes
    Assert.assertEquals(value11, fory.deserialize(fory.serialize(value11)))

    val value12 = 12.hours
    Assert.assertEquals(value12, fory.deserialize(fory.serialize(value12)))

    val value13 = 13.days
    Assert.assertEquals(value13, fory.deserialize(fory.serialize(value13)))

    val value14 = 356.days
    Assert.assertEquals(value14, fory.deserialize(fory.serialize(value14)))
  }

  @OptIn(ExperimentalUuidApi::class)
  @Test
  fun testSerializeUuid() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .build()

    val value = Uuid.fromLongs(1234L, 56789L)
    Assert.assertEquals(value, fory.deserialize(fory.serialize(value)))
  }

  @Test
  fun testSerializeRegex() {
    val fory: Fory =
      ForyKotlin.builder()
        .withXlang(false)
        .requireClassRegistration(true)
        .withRefTracking(true)
        .withAsyncCompilation(true)
        .build()

    val value = Regex("12345", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    val result = fory.deserialize(fory.serialize(value)) as Regex
    Assert.assertEquals(result.pattern, value.pattern)
    Assert.assertEquals(result.options, value.options)

    // Emitted by the original ReplaceResolveSerializer family before the read guard was added.
    val oldPayload = Base64.getDecoder().decode("AAAfHQEAHx4APh6UAf8UMTIzNDU=")
    val oldResult = fory.deserialize(oldPayload) as Regex
    Assert.assertEquals(oldResult.pattern, value.pattern)
    Assert.assertEquals(oldResult.options, value.options)
    fory.ensureSerializersCompiled()

    val canonEq = Regex("abc", RegexOption.CANON_EQ)
    val bytes = fory.serialize(canonEq)
    assertFailsWith<InsecureException> { fory.deserialize(bytes) }

    val serializedClass = Class.forName("kotlin.text.Regex\$Serialized")
    val constructor =
      serializedClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType!!)
    constructor.isAccessible = true
    val carrier = constructor.newInstance("abc", Pattern.CANON_EQ)
    val carrierBytes = fory.serialize(carrier)
    assertFailsWith<InsecureException> { fory.deserialize(carrierBytes) }
  }

  @Test
  fun testRegexCarrierStub() {
    val serializedClass = Class.forName("kotlin.text.Regex\$Serialized")
    val constructor =
      serializedClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType!!)
    constructor.isAccessible = true
    for (codegen in listOf(false, true)) {
      val fory =
        ForyKotlin.builder()
          .withXlang(false)
          .requireClassRegistration(true)
          .withRefTracking(true)
          .withCodegen(codegen)
          .withAsyncCompilation(true)
          .build()
      val canonEq = regexCarrierStub(fory, constructor.newInstance("a", Pattern.CANON_EQ))
      val ordinary = regexCarrierStub(fory, constructor.newInstance("a", 0))

      assertFailsWith<InsecureException> { fory.deserialize(canonEq) }
      Assert.assertEquals((fory.deserialize(ordinary) as Regex).pattern, "a")
      fory.ensureSerializersCompiled()
      assertFailsWith<InsecureException> { fory.deserialize(canonEq) }
      Assert.assertEquals((fory.deserialize(ordinary) as Regex).pattern, "a")
    }
  }

  private fun regexCarrierStub(fory: Fory, carrier: Any): ByteArray {
    val bytes = fory.serialize(carrier)
    val buffer = MemoryUtils.wrap(bytes)
    buffer.readByte() // Root bitmap.
    Assert.assertEquals(buffer.readByte(), Fory.REF_VALUE_FLAG)
    Assert.assertEquals(buffer.readUInt8(), Types.EXT)
    buffer.readVarUInt32() // Registered carrier user type id.
    // Preserve the carrier body and its inner class token; only its outer dispatch changes.
    return bytes.copyOfRange(0, 2) +
      byteArrayOf(ClassResolver.REPLACE_STUB_ID.toByte()) +
      bytes.copyOfRange(buffer.readerIndex(), bytes.size)
  }

  @Test
  fun testSerializeBigDecimal() {
    val values =
      listOf(
        BigDecimal.ZERO,
        BigDecimal(BigInteger.ZERO, 3),
        BigDecimal.ONE,
        BigDecimal.ONE.negate(),
        BigDecimal.valueOf(12345, 2),
        BigDecimal(BigInteger.valueOf(Long.MAX_VALUE), 0),
        BigDecimal(BigInteger.valueOf(Long.MIN_VALUE), 0),
        BigDecimal(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), 0),
        BigDecimal(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), 0),
        BigDecimal(BigInteger("123456789012345678901234567890123456789"), 37),
      )
    for (xlang in listOf(false, true)) {
      val fory =
        ForyKotlin.builder()
          .withXlang(xlang)
          .requireClassRegistration(true)
          .withRefTracking(true)
          .build()
      for (value in values) {
        Assert.assertEquals(value, fory.deserialize(fory.serialize(value)))
      }
    }
  }
}
