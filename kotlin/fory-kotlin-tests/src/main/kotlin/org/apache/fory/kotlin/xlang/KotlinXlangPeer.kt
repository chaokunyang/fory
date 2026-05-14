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

@file:OptIn(ExperimentalUnsignedTypes::class)

package org.apache.fory.kotlin.xlang

import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.apache.fory.Fory
import org.apache.fory.annotation.ArrayType
import org.apache.fory.annotation.ForyField
import org.apache.fory.annotation.ForyStruct
import org.apache.fory.config.Language
import org.apache.fory.kotlin.Fixed
import org.apache.fory.kotlin.VarInt
import org.apache.fory.memory.MemoryUtils
import org.apache.fory.serializer.StaticGeneratedStructSerializer
import org.apache.fory.serializer.kotlin.KotlinSerializers
import org.apache.fory.type.Types

@ForyStruct
public data class KotlinUser(
  @ForyField(id = 1) val id: @Fixed UInt,
  @ForyField(id = 2) val name: String = "anonymous",
  @ForyField(id = 3) val score: @VarInt Long,
)

@ForyStruct
public data class KotlinConcreteCollections(
  @ForyField(id = 1) val names: ArrayList<String>,
  @ForyField(id = 2) val values: java.util.LinkedList<Int>,
  @ForyField(id = 3) val tags: CopyOnWriteArrayList<Long>,
  @ForyField(id = 4) val counts: TreeMap<String, Int>,
  @ForyField(id = 5) val mutableNames: MutableList<String>,
  @ForyField(id = 6) val mutableTags: MutableSet<Int>,
  @ForyField(id = 7) val mutableCounts: MutableMap<String, Long>,
  @ForyField(id = 8) val sortedNames: TreeSet<String>,
  @ForyField(id = 9) val concurrentCounts: ConcurrentHashMap<String, Int>,
)

@ForyStruct
public data class KotlinUnsignedCollections(
  @ForyField(id = 1) val ids: List<UInt>,
  @ForyField(id = 2) val optionalIds: List<UInt?>,
  @ForyField(id = 3) val totals: Set<ULong>,
  @ForyField(id = 4) val byName: Map<String, UInt>,
  @ForyField(id = 5) val namesById: Map<UInt, String>,
)

@ForyStruct
public data class KotlinSchemaSurface(
  @ForyField(id = 1) val nullableNames: List<String?>?,
  @ForyField(id = 2) val dynamicList: List<*>,
  @ForyField(id = 3) val dynamicValues: Map<String, *>,
  @field:ArrayType @ForyField(id = 4) val bytesAsArray: ByteArray,
  @ForyField(id = 5) val bits: BooleanArray,
  @ForyField(id = 6) val unsignedLongs: ULongArray,
  @field:ForyField(id = 7) val fieldSiteId: Int,
  @field:ArrayType @ForyField(id = 8) val denseIds: List<Int>,
)

@ForyStruct
public data class KotlinDenseArrays(
  @ForyField(id = 1) val ubytes: UByteArray,
  @ForyField(id = 2) val ushorts: UShortArray,
  @ForyField(id = 3) val uints: UIntArray,
  @ForyField(id = 4) val ulongs: ULongArray,
  @ForyField(id = 5) val ints: IntArray,
  @ForyField(id = 6) val longs: LongArray,
  @ForyField(id = 7) val bytes: ByteArray,
  @ForyField(id = 8) val shorts: ShortArray,
  @ForyField(id = 9) val floats: FloatArray,
  @ForyField(id = 10) val doubles: DoubleArray,
  @ForyField(id = 11) val booleans: BooleanArray,
  @ForyField(id = 12) val nullableUInts: UIntArray?,
)

@ForyStruct public data class KotlinNullableCompatibleWriter(@ForyField(id = 1) val anchor: String)

@ForyStruct
public data class KotlinNullableCompatibleReader(
  @ForyField(id = 1) val anchor: String,
  @ForyField(id = 2) val maybeBoolean: Boolean?,
  @ForyField(id = 3) val maybeInt: Int?,
  @ForyField(id = 4) val maybeLong: Long?,
  @ForyField(id = 5) var maybeUInt: UInt?,
  @ForyField(id = 6) var maybeULong: ULong?,
)

public fun main(args: Array<String>) {
  if (args.size < 2) {
    throw IllegalArgumentException("Usage: <case> <data-file>")
  }
  when (args[0]) {
    "static_serializer_round_trip" -> staticSerializerRoundTrip(args[1])
    "service_loader_static_serializer_round_trip" -> serviceLoaderStaticSerializerRoundTrip(args[1])
    "dense_array_round_trip" -> denseArrayRoundTrip(args[1])
    "unsigned_collection_round_trip" -> unsignedCollectionRoundTrip(args[1])
    else -> throw IllegalArgumentException("Unsupported Kotlin xlang case ${args[0]}")
  }
}

private fun staticSerializerRoundTrip(dataFile: String) {
  val fory = newFory()
  fory.register(KotlinUser::class.java, "kotlin", "KotlinUser")
  fory.register(KotlinConcreteCollections::class.java, "kotlin", "KotlinConcreteCollections")
  fory.register(KotlinUnsignedCollections::class.java, "kotlin", "KotlinUnsignedCollections")
  fory.register(KotlinDenseArrays::class.java, "kotlin", "KotlinDenseArrays")

  val javaRequest =
    fory.deserialize(MemoryUtils.wrap(java.io.File(dataFile).readBytes()), KotlinUser::class.java)
  check(
    javaRequest == KotlinUser(id = UInt.MAX_VALUE, name = "java-to-kotlin", score = -123456789L)
  )
  val response = KotlinUser(id = UInt.MAX_VALUE - 1u, name = "kotlin-to-java", score = 987654321L)
  java.io.File(dataFile).writeBytes(fory.serialize(response))

  val user = KotlinUser(id = UInt.MAX_VALUE, name = "fory", score = -123456789L)
  check(fory.deserialize(fory.serialize(user), KotlinUser::class.java) == user)
  val userSerializer = fory.getSerializer(KotlinUser::class.java)
  check(userSerializer is StaticGeneratedStructSerializer<*>) {
    "KotlinUser did not load a static generated serializer: ${userSerializer::class.java.name}"
  }
  val descriptors = userSerializer.generatedDescriptors
  check(descriptors.size == 3)
  check(descriptors[0].foryFieldId == 1)
  check(descriptors[0].typeRef.typeExtMeta.typeId() == Types.UINT32)
  check(descriptors[2].typeRef.typeExtMeta.typeId() == Types.VARINT64)
  val schemaDescriptors = KotlinSchemaSurface__ForySerializer__().generatedDescriptors
  check(schemaDescriptors[3].isArrayType)
  check(schemaDescriptors[6].hasForyField())
  check(schemaDescriptors[6].foryFieldId == 7)
  check(schemaDescriptors[7].isArrayType)
  check(schemaDescriptors[7].typeRef.typeExtMeta.typeId() == Types.INT32_ARRAY)
  val arrayDescriptors = KotlinDenseArrays__ForySerializer__().generatedDescriptors
  check(arrayDescriptors[2].typeRef.componentType.typeExtMeta.typeId() == Types.UINT32)
  check(arrayDescriptors[3].typeRef.componentType.typeExtMeta.typeId() == Types.UINT64)
  check(arrayDescriptors[4].typeRef.componentType.typeExtMeta.typeId() == Types.INT32)
  check(arrayDescriptors[5].typeRef.componentType.typeExtMeta.typeId() == Types.INT64)

  val collections =
    KotlinConcreteCollections(
      names = arrayListOf("a", "b"),
      values = java.util.LinkedList(listOf(1, 2, 3)),
      tags = CopyOnWriteArrayList(listOf(4L, 5L)),
      counts = TreeMap(mapOf("x" to 7, "y" to 8)),
      mutableNames = mutableListOf("c", "d"),
      mutableTags = mutableSetOf(9, 10),
      mutableCounts = mutableMapOf("z" to 11L),
      sortedNames = TreeSet(listOf("e", "f")),
      concurrentCounts = ConcurrentHashMap(mapOf("q" to 12)),
    )
  val decoded = fory.deserialize(fory.serialize(collections), KotlinConcreteCollections::class.java)
  check(decoded == collections)
  check(decoded.names.javaClass == java.util.ArrayList::class.java)
  check(decoded.values.javaClass == java.util.LinkedList::class.java)
  check(decoded.tags.javaClass == CopyOnWriteArrayList::class.java)
  check(decoded.counts.javaClass == TreeMap::class.java)
  decoded.mutableNames.add("round-trip-mutable-list")
  decoded.mutableTags.add(13)
  decoded.mutableCounts["round-trip-mutable-map"] = 14L
  check(decoded.sortedNames.javaClass == TreeSet::class.java)
  check(decoded.concurrentCounts.javaClass == ConcurrentHashMap::class.java)

  val unsignedCollections =
    KotlinUnsignedCollections(
      ids = listOf(0u, UInt.MAX_VALUE),
      optionalIds = listOf(1u, null, UInt.MAX_VALUE),
      totals = linkedSetOf(0uL, ULong.MAX_VALUE),
      byName = linkedMapOf("min" to 0u, "max" to UInt.MAX_VALUE),
      namesById = linkedMapOf(0u to "min", UInt.MAX_VALUE to "max"),
    )
  val decodedUnsignedCollections =
    fory.deserialize(fory.serialize(unsignedCollections), KotlinUnsignedCollections::class.java)
  check(decodedUnsignedCollections == unsignedCollections)
  check(fory.copy(unsignedCollections) == unsignedCollections)

  val arrays =
    KotlinDenseArrays(
      ubytes = ubyteArrayOf(0u, 255u),
      ushorts = ushortArrayOf(0u, 65535u),
      uints = uintArrayOf(0u, UInt.MAX_VALUE),
      ulongs = ulongArrayOf(0u, ULong.MAX_VALUE),
      ints = intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE),
      longs = longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE),
      bytes = byteArrayOf(Byte.MIN_VALUE, Byte.MAX_VALUE),
      shorts = shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE),
      floats = floatArrayOf(1.25f, -3.5f),
      doubles = doubleArrayOf(1.25, -3.5),
      booleans = booleanArrayOf(true, false),
      nullableUInts = uintArrayOf(42u, UInt.MAX_VALUE),
    )
  val decodedArrays = fory.deserialize(fory.serialize(arrays), KotlinDenseArrays::class.java)
  check(decodedArrays.ubytes contentEquals arrays.ubytes)
  check(decodedArrays.ushorts contentEquals arrays.ushorts)
  check(decodedArrays.uints contentEquals arrays.uints)
  check(decodedArrays.ulongs contentEquals arrays.ulongs)
  check(decodedArrays.ints contentEquals arrays.ints)
  check(decodedArrays.longs contentEquals arrays.longs)
  check(decodedArrays.bytes contentEquals arrays.bytes)
  check(decodedArrays.shorts contentEquals arrays.shorts)
  check(decodedArrays.floats contentEquals arrays.floats)
  check(decodedArrays.doubles contentEquals arrays.doubles)
  check(decodedArrays.booleans contentEquals arrays.booleans)
  check(decodedArrays.nullableUInts!!.contentEquals(arrays.nullableUInts!!))

  val nullArrays = arrays.copy(nullableUInts = null)
  val decodedNullArrays =
    fory.deserialize(fory.serialize(nullArrays), KotlinDenseArrays::class.java)
  check(decodedNullArrays.nullableUInts == null)

  val writer = newCompatibleFory()
  writer.register(KotlinNullableCompatibleWriter::class.java, "kotlin", "NullableCompatible")
  val reader = newCompatibleFory()
  reader.register(KotlinNullableCompatibleReader::class.java, "kotlin", "NullableCompatible")
  val compatibleDecoded =
    reader.deserialize(
      writer.serialize(KotlinNullableCompatibleWriter("anchor")),
      KotlinNullableCompatibleReader::class.java,
    )
  check(compatibleDecoded.anchor == "anchor")
  check(compatibleDecoded.maybeBoolean == null)
  check(compatibleDecoded.maybeInt == null)
  check(compatibleDecoded.maybeLong == null)
  check(compatibleDecoded.maybeUInt == null)
  check(compatibleDecoded.maybeULong == null)
}

private fun serviceLoaderStaticSerializerRoundTrip(dataFile: String) {
  val fory = newFory()
  fory.register(KotlinUser::class.java, "kotlin", "KotlinUser")
  val javaRequest =
    fory.deserialize(MemoryUtils.wrap(java.io.File(dataFile).readBytes()), KotlinUser::class.java)
  check(
    javaRequest == KotlinUser(id = UInt.MAX_VALUE, name = "java-to-kotlin", score = -123456789L)
  )
  val response =
    KotlinUser(id = UInt.MAX_VALUE - 2u, name = "kotlin-service-loader", score = 123456789L)
  val bytes = fory.serialize(response)
  val userSerializer = fory.getSerializer(KotlinUser::class.java)
  check(userSerializer is StaticGeneratedStructSerializer<*>) {
    "KotlinUser did not load a static generated serializer from SPI: ${userSerializer::class.java.name}"
  }
  java.io.File(dataFile).writeBytes(bytes)
}

private fun denseArrayRoundTrip(dataFile: String) {
  val fory = newFory()
  fory.register(KotlinDenseArrays::class.java, "kotlin", "KotlinDenseArrays")
  val request =
    fory.deserialize(
      MemoryUtils.wrap(java.io.File(dataFile).readBytes()),
      KotlinDenseArrays::class.java
    )
  check(request.ubytes contentEquals ubyteArrayOf(1u, 255u))
  check(request.ushorts contentEquals ushortArrayOf(2u, 65535u))
  check(request.uints contentEquals uintArrayOf(3u, UInt.MAX_VALUE))
  check(request.ulongs contentEquals ulongArrayOf(4u, ULong.MAX_VALUE))
  check(request.ints contentEquals intArrayOf(-1, 1))
  check(request.longs contentEquals longArrayOf(-2L, 2L))
  check(request.bytes contentEquals byteArrayOf(5, 6))
  check(request.shorts contentEquals shortArrayOf(-7, 7))
  check(request.floats contentEquals floatArrayOf(1.5f, -1.5f))
  check(request.doubles contentEquals doubleArrayOf(2.5, -2.5))
  check(request.booleans contentEquals booleanArrayOf(true, false))
  check(request.nullableUInts!!.contentEquals(uintArrayOf(8u, UInt.MAX_VALUE)))
  val response =
    KotlinDenseArrays(
      ubytes = ubyteArrayOf(9u, 255u),
      ushorts = ushortArrayOf(10u, 65535u),
      uints = uintArrayOf(11u, UInt.MAX_VALUE),
      ulongs = ulongArrayOf(12u, ULong.MAX_VALUE),
      ints = intArrayOf(-13, 13),
      longs = longArrayOf(-14L, 14L),
      bytes = byteArrayOf(15, 16),
      shorts = shortArrayOf(-17, 17),
      floats = floatArrayOf(18.5f, -18.5f),
      doubles = doubleArrayOf(19.5, -19.5),
      booleans = booleanArrayOf(false, true),
      nullableUInts = null,
    )
  java.io.File(dataFile).writeBytes(fory.serialize(response))
}

private fun unsignedCollectionRoundTrip(dataFile: String) {
  val fory = newFory()
  fory.register(KotlinUnsignedCollections::class.java, "kotlin", "KotlinUnsignedCollections")
  val request =
    fory.deserialize(
      MemoryUtils.wrap(java.io.File(dataFile).readBytes()),
      KotlinUnsignedCollections::class.java,
    )
  check(request.ids == listOf(1u, UInt.MAX_VALUE))
  check(request.optionalIds == listOf(2u, null, UInt.MAX_VALUE))
  check(request.totals == linkedSetOf(3uL, ULong.MAX_VALUE))
  check(request.byName == linkedMapOf("a" to 4u, "max" to UInt.MAX_VALUE))
  check(request.namesById == linkedMapOf(5u to "five", UInt.MAX_VALUE to "max"))
  val response =
    KotlinUnsignedCollections(
      ids = listOf(6u, UInt.MAX_VALUE),
      optionalIds = listOf(7u, null, UInt.MAX_VALUE),
      totals = linkedSetOf(8uL, ULong.MAX_VALUE),
      byName = linkedMapOf("b" to 9u, "max" to UInt.MAX_VALUE),
      namesById = linkedMapOf(10u to "ten", UInt.MAX_VALUE to "max"),
    )
  java.io.File(dataFile).writeBytes(fory.serialize(response))
}

private fun newFory(): Fory =
  Fory.builder()
    .withLanguage(Language.XLANG)
    .requireClassRegistration(true)
    .withRefTracking(false)
    .build()
    .also { KotlinSerializers.registerSerializers(it) }

private fun newCompatibleFory(): Fory =
  Fory.builder()
    .withLanguage(Language.XLANG)
    .withCompatible(true)
    .requireClassRegistration(true)
    .withRefTracking(false)
    .build()
    .also { KotlinSerializers.registerSerializers(it) }
