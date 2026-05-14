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

package org.apache.fory.serializer.scala

import org.apache.fory.context.{CopyContext, ReadContext, WriteContext}
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.serializer.Serializer
import org.apache.fory.serializer.collection.{CollectionLikeSerializer, MapLikeSerializer}

import java.util
import scala.collection.mutable
import scala.collection.{immutable => simmutable}

abstract class AbstractScalaXlangCollectionSerializer[A, T <: scala.collection.Iterable[A]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends CollectionLikeSerializer[T](typeResolver, cls) {

  override def onCollectionWrite(writeContext: WriteContext, value: T): util.Collection[_] = {
    writeContext.getBuffer.writeVarUInt32Small7(value.size)
    new XlangCollectionAdapter[A](value)
  }

  override def newCollection(readContext: ReadContext): util.Collection[_] = {
    val numElements = readCollectionSize(readContext.getBuffer)
    setNumElements(numElements)
    new XlangCollectionBuilder[A, T](newBuilder(numElements))
  }

  protected def newBuilder(numElements: Int): mutable.Builder[A, T]

  override def onCollectionRead(collection: util.Collection[_]): T = {
    collection.asInstanceOf[XlangCollectionBuilder[A, T]].builder.result()
  }

  override def copy(copyContext: CopyContext, value: T): T = {
    if (isImmutable) {
      value
    } else {
      super.copy(copyContext, value)
    }
  }
}

class ScalaXlangSeqSerializer[A, T <: scala.collection.Seq[A]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends AbstractScalaXlangCollectionSerializer[A, T](typeResolver, cls) {
  override protected def newBuilder(numElements: Int): mutable.Builder[A, T] = {
    val builder = simmutable.List.newBuilder[A]
    builder.sizeHint(numElements)
    builder.asInstanceOf[mutable.Builder[A, T]]
  }
}

class ScalaXlangSetSerializer[A, T <: scala.collection.Set[A]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends AbstractScalaXlangCollectionSerializer[A, T](typeResolver, cls) {
  override protected def newBuilder(numElements: Int): mutable.Builder[A, T] = {
    val builder = simmutable.Set.newBuilder[A]
    builder.sizeHint(numElements)
    builder.asInstanceOf[mutable.Builder[A, T]]
  }
}

class ScalaXlangCollectionSerializer[A, T <: scala.collection.Iterable[A]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends AbstractScalaXlangCollectionSerializer[A, T](typeResolver, cls) {
  override protected def newBuilder(numElements: Int): mutable.Builder[A, T] = {
    val builder = simmutable.List.newBuilder[A]
    builder.sizeHint(numElements)
    builder.asInstanceOf[mutable.Builder[A, T]]
  }
}

private final class XlangCollectionAdapter[A](coll: scala.collection.Iterable[A])
  extends util.AbstractCollection[A] {
  override def iterator(): util.Iterator[A] = new util.Iterator[A] {
    private val it = coll.iterator

    override def hasNext: Boolean = it.hasNext

    override def next(): A = it.next()
  }

  override def size(): Int = coll.size
}

private final class XlangCollectionBuilder[A, T](val builder: mutable.Builder[A, T])
  extends util.AbstractCollection[A] {
  override def add(e: A): Boolean = {
    builder.addOne(e)
    true
  }

  override def iterator(): util.Iterator[A] =
    throw new UnsupportedOperationException("Scala xlang collection builder is write-only")

  override def size(): Int =
    throw new UnsupportedOperationException("Scala xlang collection builder is write-only")
}

abstract class AbstractScalaXlangMapSerializer[K, V, T <: scala.collection.Map[K, V]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends MapLikeSerializer[T](typeResolver, cls) {

  override def onMapWrite(writeContext: WriteContext, value: T): util.Map[_, _] = {
    writeContext.getBuffer.writeVarUInt32Small7(value.size)
    new XlangMapAdapter[K, V](value)
  }

  override def newMap(readContext: ReadContext): util.Map[_, _] = {
    val numElements = readMapSize(readContext.getBuffer)
    setNumElements(numElements)
    val builder = simmutable.Map.newBuilder[K, V]
    builder.sizeHint(numElements)
    new XlangMapBuilder[K, V, T](builder.asInstanceOf[mutable.Builder[(K, V), T]])
  }

  override def onMapRead(map: util.Map[_, _]): T = {
    map.asInstanceOf[XlangMapBuilder[K, V, T]].builder.result()
  }

  override def onMapCopy(map: util.Map[_, _]): T = onMapRead(map)
}

class ScalaXlangMapSerializer[K, V, T <: scala.collection.Map[K, V]](
    typeResolver: TypeResolver,
    cls: Class[T])
  extends AbstractScalaXlangMapSerializer[K, V, T](typeResolver, cls)

private final class XlangMapAdapter[K, V](map: scala.collection.Map[K, V])
  extends util.AbstractMap[K, V] {
  override def entrySet(): util.Set[util.Map.Entry[K, V]] =
    new util.AbstractSet[util.Map.Entry[K, V]] {
      override def size(): Int = map.size

      override def iterator(): util.Iterator[util.Map.Entry[K, V]] =
        new util.Iterator[util.Map.Entry[K, V]] {
          private val it = map.iterator

          override def hasNext: Boolean = it.hasNext

          override def next(): util.Map.Entry[K, V] = {
            val entry = it.next()
            new org.apache.fory.collection.MapEntry[K, V](entry._1, entry._2)
          }
        }
    }
}

private final class XlangMapBuilder[K, V, T](val builder: mutable.Builder[(K, V), T])
  extends util.AbstractMap[K, V] {
  override def entrySet(): util.Set[util.Map.Entry[K, V]] =
    throw new UnsupportedOperationException("Scala xlang map builder is write-only")

  override def put(key: K, value: V): V = {
    builder.addOne((key, value))
    value
  }
}

final class ScalaOptionSerializer(typeResolver: TypeResolver, cls: Class[_])
  extends Serializer[Option[Any]](typeResolver.getConfig, cls.asInstanceOf[Class[Option[Any]]]) {
  override def write(writeContext: WriteContext, value: Option[Any]): Unit = {
    writeContext.writeRef(value.orNull)
  }

  override def read(readContext: ReadContext): Option[Any] = {
    Option(readContext.readRef())
  }

  override def copy(copyContext: CopyContext, value: Option[Any]): Option[Any] = {
    value.map(copyContext.copyObject(_))
  }
}
