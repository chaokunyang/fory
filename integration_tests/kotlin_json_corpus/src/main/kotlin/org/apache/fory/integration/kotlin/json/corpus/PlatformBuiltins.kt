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

@file:OptIn(ExperimentalUnsignedTypes::class, kotlin.uuid.ExperimentalUuidApi::class)

package org.apache.fory.integration.kotlin.json.corpus

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimedValue
import kotlin.uuid.Uuid
import org.apache.fory.json.annotation.JsonType

@JsonType
public data class PlatformBuiltins(
  public val pair: Pair<Int?, String>,
  public val triple: Triple<Int, String, Boolean>,
  public val ubyte: UByte,
  public val ushort: UShort,
  public val uint: UInt,
  public val ulong: ULong,
  public val ubytes: UByteArray,
  public val ushorts: UShortArray,
  public val uints: UIntArray,
  public val ulongs: ULongArray,
  public val ubyteKeys: Map<UByte, String>,
  public val ushortKeys: Map<UShort, String>,
  public val uintKeys: Map<UInt, String>,
  public val ulongKeys: Map<ULong, String>,
  public val zeroDuration: Duration,
  public val negativeDuration: Duration,
  public val duration: Duration,
  public val infiniteDuration: Duration,
  public val negativeInfiniteDuration: Duration,
  public val instant: Instant,
  public val nanosInstant: Instant,
  public val minInstant: Instant,
  public val maxInstant: Instant,
  public val uuid: Uuid,
  public val unit: Unit,
  public val nullableUnit: Unit?,
  public val nothing: Nothing?,
  public val intRange: IntRange,
  public val uintRange: UIntRange,
  public val intProgression: IntProgression,
  public val ulongProgression: ULongProgression,
  public val timed: TimedValue<String>,
)

internal fun platformBuiltinsValue(): PlatformBuiltins =
  PlatformBuiltins(
    pair = Pair<Int?, String>(null, "r"),
    triple = Triple(1, "two", true),
    ubyte = UByte.MAX_VALUE,
    ushort = UShort.MAX_VALUE,
    uint = UInt.MAX_VALUE,
    ulong = ULong.MAX_VALUE,
    ubytes = ubyteArrayOf(0u, UByte.MAX_VALUE),
    ushorts = ushortArrayOf(0u, UShort.MAX_VALUE),
    uints = uintArrayOf(0u, UInt.MAX_VALUE),
    ulongs = ulongArrayOf(0u, ULong.MAX_VALUE),
    ubyteKeys = linkedMapOf(0.toUByte() to "zero", UByte.MAX_VALUE to "max"),
    ushortKeys = linkedMapOf(0.toUShort() to "zero", UShort.MAX_VALUE to "max"),
    uintKeys = linkedMapOf(0u to "zero", UInt.MAX_VALUE to "max"),
    ulongKeys = linkedMapOf(0uL to "zero", ULong.MAX_VALUE to "max"),
    zeroDuration = Duration.ZERO,
    negativeDuration = -1.nanoseconds,
    duration = 49.hours + 2.minutes + 3.seconds + 456_789.nanoseconds,
    infiniteDuration = Duration.INFINITE,
    negativeInfiniteDuration = -Duration.INFINITE,
    instant = Instant.fromEpochSeconds(0),
    nanosInstant = Instant.fromEpochSeconds(-1, 1),
    minInstant = Instant.fromEpochSeconds(-31_557_014_167_219_200L),
    maxInstant = Instant.fromEpochSeconds(31_556_889_864_403_199L, 999_999_999),
    uuid = Uuid.fromLongs(0x0011223344556677L, 0x8899aabbccddeeffuL.toLong()),
    unit = Unit,
    nullableUnit = null,
    nothing = null,
    intRange = -4..9,
    uintRange = 0u..UInt.MAX_VALUE,
    intProgression = IntProgression.fromClosedRange(20, -10, -3),
    ulongProgression = ULongProgression.fromClosedRange(20uL, 1uL, -3),
    timed = TimedValue("v", 1.nanoseconds),
  )
