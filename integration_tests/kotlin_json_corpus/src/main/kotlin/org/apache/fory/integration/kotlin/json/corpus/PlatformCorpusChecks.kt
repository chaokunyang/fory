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

@file:OptIn(
  ExperimentalUnsignedTypes::class,
  kotlin.time.ExperimentalTime::class,
  kotlin.uuid.ExperimentalUuidApi::class,
)

package org.apache.fory.integration.kotlin.json.corpus

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.enums.EnumEntries
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.time.Clock
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.TimedValue
import org.apache.fory.json.ForyJson
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.kotlin.jsonTypeRef

private enum class PlatformEntry {
  VALUE,
}

/** Executes the shared success and rejection vectors against one platform configuration. */
public object PlatformCorpusChecks {
  @JvmStatic
  public fun verifyPlatformCases(json: ForyJson) {
    val root =
      json.fromJson(
        KotlinJsonCorpus.caseJson("root"),
        KotlinJsonCorpus.rootType(),
      )
    verifyRoot(root)
    verifyRoot(
      json.fromJson(
        json.toJson(root, KotlinJsonCorpus.rootType()),
        KotlinJsonCorpus.rootType(),
      )
    )

    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("account-default"),
        KotlinJsonCorpus.accountType(),
      ) == PlatformAccount(1, "default")
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("generic-envelope"),
        KotlinJsonCorpus.envelopeType(),
      ) ==
        PlatformEnvelope(
          PlatformAccount(2, "nested"),
          listOf("a"),
          listOf(PlatformBox("b")),
          UInt.MAX_VALUE,
        )
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("recursive-generic"),
        KotlinJsonCorpus.nodeType(),
      ) == PlatformNode("root", listOf(PlatformNode("leaf")))
    )
    check(
      json.fromJson(
        json.toJson(PlatformBox("direct-root"), KotlinJsonCorpus.boxType()),
        KotlinJsonCorpus.boxType(),
      ) == PlatformBox("direct-root")
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("custom-module"),
        KotlinJsonCorpus.tokenType(),
      ) == PlatformToken("module-token")
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("singleton"),
        jsonTypeRef<PlatformMarker>(),
      ) === PlatformMarker
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("property-marker"),
        KotlinJsonCorpus.propertyShapeType(),
      ) === PlatformShapeMarker
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("wrapped-data"),
        KotlinJsonCorpus.wrappedShapeType(),
      ) == PlatformWrappedData("wrapped")
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("wrapped-marker"),
        KotlinJsonCorpus.wrappedShapeType(),
      ) === PlatformWrappedMarker
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("unit"),
        KotlinJsonCorpus.nullableUnitType(),
      ) === Unit
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("null"),
        KotlinJsonCorpus.nullableUnitType(),
      ) == null
    )
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("null"),
        KotlinJsonCorpus.nullableNothingType(),
      ) == null
    )
    verifyMixinCases(json)

    val manifest = KotlinJsonCorpus.manifest(json)
    check(manifest.schemaVersion == 1)
    check(manifest.cases.map { it.id }.toSet() == expectedCases)
  }

  @JvmStatic
  public fun verifyFailureCases(json: ForyJson) {
    expectFailure { json.fromJson("{}", Pair::class.java) }
    expectFailure { json.fromJson("{}", Triple::class.java) }
    expectFailure { json.fromJson("{}", TimedValue::class.java) }
    expectFailure { json.fromJson("0", jsonTypeRef<Nothing?>()) }
    expectFailure { json.fromJson("-1", jsonTypeRef<PlatformPositiveId>()) }
    expectFailure { json.fromJson("null", jsonTypeRef<PlatformNullableText?>()) }
    expectFailure { json.fromJson("{\"kind\":\"unknown\"}", KotlinJsonCorpus.propertyShapeType()) }
    expectFailure { json.toJson(PlatformUnlistedShape(1), KotlinJsonCorpus.propertyShapeType()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<PlatformComputed>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<PlatformDelegated>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<PlatformInnerOwner.InnerModel>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<PlatformStatefulMarker>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<() -> String>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<KClass<String>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<KType>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Continuation<String>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<CoroutineContext>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Result<String>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Lazy<String>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Iterable<Int>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Iterator<Int>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Map.Entry<String, Int>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<EnumEntries<PlatformEntry>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Regex>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Random>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<ClosedRange<Double>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<OpenEndRange<Int>>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<Clock>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<TimeSource>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<TimeMark>()) }
    expectFailure { json.fromJson("{}", jsonTypeRef<ComparableTimeMark>()) }

    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("account-default"),
        KotlinJsonCorpus.accountType(),
      ) == PlatformAccount(1, "default")
    )
  }

  @JvmStatic
  public fun verifyPropertyFailure(json: ForyJson) {
    expectFailure {
      json.fromJson("{\"kind\":\"number\"}", KotlinJsonCorpus.invalidPropertyShapeType())
    }
    check(
      json.fromJson(
        KotlinJsonCorpus.caseJson("account-default"),
        KotlinJsonCorpus.accountType(),
      ) == PlatformAccount(1, "default")
    )
  }

  private fun verifyRoot(actual: PlatformRoot) {
    val expected = KotlinJsonCorpus.rootValue()
    check(actual.account == expected.account)
    check(actual.ordinary.id == expected.ordinary.id)
    check(actual.ordinary.name == expected.ordinary.name)
    check(actual.envelope == expected.envelope)
    check(actual.node == expected.node)
    verifyBuiltins(actual.builtins, expected.builtins)
    check(actual.value == expected.value)
    check(actual.unitHolder == expected.unitHolder)
    check(actual.propertyShape == expected.propertyShape)
    check(actual.wrappedShape == expected.wrappedShape)
    check(actual.annotated == expected.annotated)
    check(actual.nulls == expected.nulls)
    check(actual.token == expected.token)
  }

  private fun verifyBuiltins(actual: PlatformBuiltins, expected: PlatformBuiltins) {
    check(actual.pair == expected.pair)
    check(actual.triple == expected.triple)
    check(actual.ubyte == expected.ubyte)
    check(actual.ushort == expected.ushort)
    check(actual.uint == expected.uint)
    check(actual.ulong == expected.ulong)
    check(actual.ubytes.contentEquals(expected.ubytes))
    check(actual.ushorts.contentEquals(expected.ushorts))
    check(actual.uints.contentEquals(expected.uints))
    check(actual.ulongs.contentEquals(expected.ulongs))
    check(actual.ubyteKeys == expected.ubyteKeys)
    check(actual.ushortKeys == expected.ushortKeys)
    check(actual.uintKeys == expected.uintKeys)
    check(actual.ulongKeys == expected.ulongKeys)
    check(actual.zeroDuration == expected.zeroDuration)
    check(actual.negativeDuration == expected.negativeDuration)
    check(actual.duration == expected.duration)
    check(actual.infiniteDuration == expected.infiniteDuration)
    check(actual.negativeInfiniteDuration == expected.negativeInfiniteDuration)
    check(actual.instant == expected.instant)
    check(actual.nanosInstant == expected.nanosInstant)
    check(actual.minInstant == expected.minInstant)
    check(actual.maxInstant == expected.maxInstant)
    check(actual.uuid == expected.uuid)
    check(actual.unit === Unit)
    check(actual.nullableUnit == null)
    check(actual.nothing == null)
    check(actual.intRange == expected.intRange)
    check(actual.uintRange == expected.uintRange)
    check(actual.intProgression == expected.intProgression)
    check(actual.ulongProgression == expected.ulongProgression)
    check(actual.timed == expected.timed)
  }

  @JvmStatic
  public fun verifyMixinCases(json: ForyJson) {
    val javaType = jsonTypeRef<PlatformJavaProfile>()
    val javaProfile = PlatformJavaProfile("java-mixin")
    val javaText = json.toJson(javaProfile, javaType)
    check(javaText == "{\"display_label\":\"java-mixin\"}")
    check(json.fromJson(javaText, javaType).label == "java-mixin")

    val kotlinType = jsonTypeRef<PlatformKotlinProfile>()
    val kotlinProfile = PlatformKotlinProfile("kotlin-mixin")
    val kotlinText = json.toJson(kotlinProfile, kotlinType)
    check(kotlinText == "{\"display_label\":\"kotlin-mixin\"}")
    check(json.fromJson(kotlinText, kotlinType) == kotlinProfile)
  }

  private fun expectFailure(operation: () -> Unit) {
    try {
      operation()
      error("Rejected Kotlin JSON corpus case unexpectedly succeeded")
    } catch (_: ForyJsonException) {
      // Every rejected vector must fail as a controlled root operation.
    }
  }

  private val expectedCases: Set<String> =
    setOf(
      "root",
      "account-default",
      "generic-envelope",
      "recursive-generic",
      "custom-module",
      "singleton",
      "property-marker",
      "wrapped-data",
      "wrapped-marker",
      "unit-root",
      "nothing-root",
      "missing-companion",
      "unreached-generic",
      "nothing-non-null",
      "value-nullability",
      "sealed-authorization",
      "property-scalar",
      "raw-products",
      "unreconstructible-models",
      "executable-reflection",
      "lazy-cursors",
      "abstract-time-state",
    )
}
