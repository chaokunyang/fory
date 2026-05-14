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
public object KotlinCollectionAdapters {
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
