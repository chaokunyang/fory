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

package org.apache.fory.kotlin

/** Declared Kotlin/JVM collection target adapters for generated xlang serializers. */
@Suppress("UNCHECKED_CAST")
public object KotlinCollectionAdapters {
  @JvmStatic
  public fun toByteList(value: Collection<*>): List<Byte> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toByte()
      }
      return list as List<Byte>
    }
    val result = java.util.ArrayList<Byte>(value.size)
    for (element in value) {
      result.add((element as Number).toByte())
    }
    return result
  }

  @JvmStatic
  public fun toShortList(value: Collection<*>): List<Short> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toShort()
      }
      return list as List<Short>
    }
    val result = java.util.ArrayList<Short>(value.size)
    for (element in value) {
      result.add((element as Number).toShort())
    }
    return result
  }

  @JvmStatic
  public fun toIntList(value: Collection<*>): List<Int> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toInt()
      }
      return list as List<Int>
    }
    val result = java.util.ArrayList<Int>(value.size)
    for (element in value) {
      result.add((element as Number).toInt())
    }
    return result
  }

  @JvmStatic
  public fun toLongList(value: Collection<*>): List<Long> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toLong()
      }
      return list as List<Long>
    }
    val result = java.util.ArrayList<Long>(value.size)
    for (element in value) {
      result.add((element as Number).toLong())
    }
    return result
  }

  @JvmStatic
  public fun toUByteList(value: Collection<*>): List<UByte> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toByte().toUByte()
      }
      return list as List<UByte>
    }
    val result = java.util.ArrayList<UByte>(value.size)
    for (element in value) {
      result.add((element as Number).toByte().toUByte())
    }
    return result
  }

  @JvmStatic
  public fun toUShortList(value: Collection<*>): List<UShort> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toShort().toUShort()
      }
      return list as List<UShort>
    }
    val result = java.util.ArrayList<UShort>(value.size)
    for (element in value) {
      result.add((element as Number).toShort().toUShort())
    }
    return result
  }

  @JvmStatic
  public fun toUIntList(value: Collection<*>): List<UInt> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toInt().toUInt()
      }
      return list as List<UInt>
    }
    val result = java.util.ArrayList<UInt>(value.size)
    for (element in value) {
      result.add((element as Number).toInt().toUInt())
    }
    return result
  }

  @JvmStatic
  public fun toULongList(value: Collection<*>): List<ULong> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toLong().toULong()
      }
      return list as List<ULong>
    }
    val result = java.util.ArrayList<ULong>(value.size)
    for (element in value) {
      result.add((element as Number).toLong().toULong())
    }
    return result
  }

  @JvmStatic
  public fun toFloatList(value: Collection<*>): List<Float> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toFloat()
      }
      return list as List<Float>
    }
    val result = java.util.ArrayList<Float>(value.size)
    for (element in value) {
      result.add((element as Number).toFloat())
    }
    return result
  }

  @JvmStatic
  public fun toDoubleList(value: Collection<*>): List<Double> {
    if (value is MutableList<*>) {
      val list = value as MutableList<Any?>
      for (i in list.indices) {
        list[i] = (list[i] as Number).toDouble()
      }
      return list as List<Double>
    }
    val result = java.util.ArrayList<Double>(value.size)
    for (element in value) {
      result.add((element as Number).toDouble())
    }
    return result
  }

  @JvmStatic
  public fun <E> toMutableList(value: Collection<E>): MutableList<E> =
    if (value is MutableList<*>) value as MutableList<E> else java.util.ArrayList(value)

  @JvmStatic
  public fun <E> toArrayList(value: Collection<E>): java.util.ArrayList<E> =
    if (value is java.util.ArrayList<*>) value as java.util.ArrayList<E>
    else java.util.ArrayList(value)

  @JvmStatic
  public fun <E> toLinkedList(value: Collection<E>): java.util.LinkedList<E> =
    if (value is java.util.LinkedList<*>) value as java.util.LinkedList<E>
    else java.util.LinkedList(value)

  @JvmStatic
  public fun <E> toCopyOnWriteArrayList(
    value: Collection<E>
  ): java.util.concurrent.CopyOnWriteArrayList<E> =
    if (value is java.util.concurrent.CopyOnWriteArrayList<*>)
      value as java.util.concurrent.CopyOnWriteArrayList<E>
    else java.util.concurrent.CopyOnWriteArrayList(value)

  @JvmStatic
  public fun <E> toMutableSet(value: Collection<E>): MutableSet<E> =
    if (value is MutableSet<*>) value as MutableSet<E> else java.util.LinkedHashSet(value)

  @JvmStatic
  public fun <E> toHashSet(value: Collection<E>): java.util.HashSet<E> =
    if (value is java.util.HashSet<*>) value as java.util.HashSet<E> else java.util.HashSet(value)

  @JvmStatic
  public fun <E> toLinkedHashSet(value: Collection<E>): java.util.LinkedHashSet<E> =
    if (value is java.util.LinkedHashSet<*>) value as java.util.LinkedHashSet<E>
    else java.util.LinkedHashSet(value)

  @JvmStatic
  public fun <E> toTreeSet(value: Collection<E>): java.util.TreeSet<E> =
    if (value is java.util.TreeSet<*>) value as java.util.TreeSet<E> else java.util.TreeSet(value)

  @JvmStatic
  public fun <E> toCopyOnWriteArraySet(
    value: Collection<E>
  ): java.util.concurrent.CopyOnWriteArraySet<E> =
    if (value is java.util.concurrent.CopyOnWriteArraySet<*>)
      value as java.util.concurrent.CopyOnWriteArraySet<E>
    else java.util.concurrent.CopyOnWriteArraySet(value)

  @JvmStatic
  public fun <E> toConcurrentSkipListSet(
    value: Collection<E>
  ): java.util.concurrent.ConcurrentSkipListSet<E> =
    if (value is java.util.concurrent.ConcurrentSkipListSet<*>)
      value as java.util.concurrent.ConcurrentSkipListSet<E>
    else java.util.concurrent.ConcurrentSkipListSet(value)

  @JvmStatic
  public fun <K, V> toMutableMap(value: Map<K, V>): MutableMap<K, V> =
    if (value is MutableMap<*, *>) value as MutableMap<K, V> else java.util.LinkedHashMap(value)

  @JvmStatic
  public fun <K, V> toHashMap(value: Map<K, V>): java.util.HashMap<K, V> =
    if (value is java.util.HashMap<*, *>) value as java.util.HashMap<K, V>
    else java.util.HashMap(value)

  @JvmStatic
  public fun <K, V> toLinkedHashMap(value: Map<K, V>): java.util.LinkedHashMap<K, V> =
    if (value is java.util.LinkedHashMap<*, *>) value as java.util.LinkedHashMap<K, V>
    else java.util.LinkedHashMap(value)

  @JvmStatic
  public fun <K, V> toTreeMap(value: Map<K, V>): java.util.TreeMap<K, V> =
    if (value is java.util.TreeMap<*, *>) value as java.util.TreeMap<K, V>
    else java.util.TreeMap(value)

  @JvmStatic
  public fun <K, V> toConcurrentHashMap(
    value: Map<K, V>
  ): java.util.concurrent.ConcurrentHashMap<K, V> =
    if (value is java.util.concurrent.ConcurrentHashMap<*, *>)
      value as java.util.concurrent.ConcurrentHashMap<K, V>
    else java.util.concurrent.ConcurrentHashMap(value)

  @JvmStatic
  public fun <K, V> toConcurrentSkipListMap(
    value: Map<K, V>
  ): java.util.concurrent.ConcurrentSkipListMap<K, V> =
    if (value is java.util.concurrent.ConcurrentSkipListMap<*, *>)
      value as java.util.concurrent.ConcurrentSkipListMap<K, V>
    else java.util.concurrent.ConcurrentSkipListMap(value)
}
