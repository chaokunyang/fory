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

package org.apache.fury.serializer.kotlin

import org.apache.fury.Fury
import org.apache.fury.memory.MemoryBuffer
import org.apache.fury.serializer.collection.AbstractCollectionSerializer

/** Serializer for kotlin collections. */
@Suppress("UNCHECKED_CAST")
public abstract class AbstractKotlinCollectionSerializer<E, T : Iterable<E>>(
  fury: Fury,
  cls: Class<T>
) : AbstractCollectionSerializer<T>(fury, cls) {
  abstract override fun onCollectionWrite(buffer: MemoryBuffer, value: T): Collection<E>

  override fun read(buffer: MemoryBuffer): T {
    val collection = newCollection(buffer)
    val numElements = getAndClearNumElements()
    if (numElements != 0) readElements(fury, buffer, collection, numElements)
    return onCollectionRead(collection)
  }

  override fun onCollectionRead(collection: Collection<*>): T {
    val builder = collection as CollectionBuilder<E, T>
    return builder.result()
  }
}

/** Serializer for [[kotlin.collections.ArrayDeque]]. */
public class KotlinArrayDequeSerializer<E>(
  fury: Fury,
  cls: Class<ArrayDeque<E>>,
) : AbstractKotlinCollectionSerializer<E, ArrayDeque<E>>(fury, cls) {
  override fun onCollectionWrite(buffer: MemoryBuffer, value: ArrayDeque<E>): Collection<E> {
    val adapter = IterableAdapter<E>(value)
    buffer.writeVarUint32Small7(adapter.size)
    return adapter
  }

  override fun newCollection(buffer: MemoryBuffer): Collection<E> {
    val numElements = buffer.readVarUint32()
    setNumElements(numElements)
    return ArrayDequeBuilder<E>(ArrayDeque<E>(numElements))
  }
}

public typealias AdaptedCollection<E> = java.util.AbstractCollection<E>

/** An adapter which wraps a kotlin iterable into a [[java.util.Collection]]. */
private class IterableAdapter<E>(coll: Iterable<E>) : AdaptedCollection<E>() {
  private val mutableList = coll.toMutableList()

  override val size: Int
    get() = mutableList.count()

  override fun iterator(): MutableIterator<E> = mutableList.iterator()
}
