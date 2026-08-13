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

import org.apache.fory.Fory
import org.apache.fory.exception.ForyException
import org.apache.fory.scala.ForyScala
import org.apache.fory.serializer.GraphMemoryEstimates
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.Factory
import scala.collection.immutable.ListMap
import scala.collection.mutable

class CollectionSerializerTest extends AnyWordSpec with Matchers {
  val params: Seq[Boolean] = List(false, true)
  params.foreach { setFactory =>
    val builder = ForyScala.builder()
      .withXlang(false)
      .withRefTracking(true)
      .requireClassRegistration(false)
      .suppressClassRegistrationWarnings(false)
    if (setFactory) {
      builder.withSerializerFactory(new ScalaSerializerFactory())
    }
    val fory1: Fory = builder.build()
    s"fory scala collection support: setFactory $setFactory" should {
      "serialize/deserialize Seq" in {
        val seq = Seq(100, 10000L)
        fory1.deserialize(fory1.serialize(seq)) shouldEqual seq
      }
      "serialize/deserialize List" in {
        val list = List(100, 10000L)
        fory1.deserialize(fory1.serialize(list)) shouldEqual list
        val list2 = List(100, 10000L, 10000L, 10000L)
        fory1.deserialize(fory1.serialize(list2)) shouldEqual list2
      }
      "serialize/deserialize empty List" in {
        fory1.deserialize(fory1.serialize(List.empty)) shouldEqual List.empty
        fory1.deserialize(fory1.serialize(Nil)) shouldEqual Nil
      }
      "serialize/deserialize Set" in {
        val set = Set(100, 10000L)
        fory1.deserialize(fory1.serialize(set)) shouldEqual set
      }
      "serialize/deserialize CollectionStruct1" in {
        val struct = CollectionStruct1(List("a", "b"))
        fory1.deserialize(fory1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize CollectionStruct1 with empty List" in {
        val struct1 = CollectionStruct1(List.empty)
        fory1.deserialize(fory1.serialize(struct1)) shouldEqual struct1
        val struct2 = CollectionStruct1(Nil)
        fory1.deserialize(fory1.serialize(struct2)) shouldEqual struct2
      }
      "serialize/deserialize NestedCollectionStruct" in {
        val struct = NestedCollectionStruct(List(List("a", "b"), List("a", "b")), Set(Set("c", "d")))
        fory1.deserialize(fory1.serialize(struct)) shouldEqual struct
      }
    }
    s"fory scala map support: setFactory $setFactory" should {
      "serialize/deserialize Map" in {
        val map = Map("a" -> 100, "b" -> 10000L)
        fory1.deserialize(fory1.serialize(map)) shouldEqual map
      }
      "serialize/deserialize MapStruct1" in {
        val struct = MapStruct1(Map("k1" -> "v1", "k2" -> "v2"))
        fory1.deserialize(fory1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize MapStruct1 with empty map" in {
        val struct = MapStruct1(Map.empty)
        fory1.deserialize(fory1.serialize(struct)) shouldEqual struct
      }
      "serialize/deserialize NestedMapStruct" in {
        val struct = NestedMapStruct(Map("K1" -> Map("k1" -> "v1", "k2" -> "v2"), "K2" -> Map("k1" -> "v1")))
        fory1.deserialize(fory1.serialize(struct)) shouldEqual struct
      }
    }
  }

  "fory scala graph memory budget" should {
    def runtime(maxGraphMemoryBytes: Option[Long] = None): Fory = {
      val builder = ForyScala.builder()
        .withXlang(false)
        .withRefTracking(true)
        .requireClassRegistration(false)
        .suppressClassRegistrationWarnings(false)
        .withSerializerFactory(new ScalaSerializerFactory())
      maxGraphMemoryBytes.foreach(builder.withMaxGraphMemoryBytes)
      builder.build()
    }

    "reserve scala collection storage" in {
      val writer = runtime()
      val reader = runtime(maxGraphMemoryBytes = Some(23))
      val bytes = writer.serialize(List.fill(6)("v"))
      intercept[ForyException] {
        reader.deserialize(bytes)
      }
    }

    "reserve scala map storage" in {
      val writer = runtime()
      val reader = runtime(maxGraphMemoryBytes = Some(23))
      val bytes = writer.serialize(Map("a" -> 1, "b" -> 2, "c" -> 3))
      intercept[ForyException] {
        reader.deserialize(bytes)
      }
    }

    "reserve Some wrappers" in {
      val writer = runtime()
      val some = Some("value")
      writer.register(some.getClass)
      val ownerBytes = GraphMemoryEstimates.shallowObjectBytes(some.getClass)
      val bytes = writer.serialize(some)

      intercept[RuntimeException] {
        val reader = runtime(maxGraphMemoryBytes = Some(ownerBytes - 1))
        reader.register(some.getClass)
        reader.deserialize(bytes)
      }
      val reader = runtime(maxGraphMemoryBytes = Some(ownerBytes))
      reader.register(some.getClass)
      reader.deserialize(bytes) shouldEqual some
    }

    "reserve ToFactory wrappers" in {
      val writer = runtime()
      val iterableFactory: Factory[Int, List[Int]] =
        List.empty[Int].iterableFactory.iterableFactory
      val mapFactory: Factory[(String, Int), Map[String, Int]] =
        Map.empty[String, Int].mapFactory.mapFactory[String, Int]

      Seq(iterableFactory, mapFactory).foreach { factory =>
        val ownerBytes = GraphMemoryEstimates.shallowObjectBytes(factory.getClass)
        val bytes = writer.serialize(factory)
        intercept[RuntimeException] {
          runtime(maxGraphMemoryBytes = Some(ownerBytes - 1)).deserialize(bytes)
        }
        runtime(maxGraphMemoryBytes = Some(ownerBytes)).deserialize(bytes).getClass shouldBe
          factory.getClass
      }
    }

    "reserve linked collection nodes" in {
      val writer = runtime()
      val list = List.fill(6)("v")
      val listBytes =
        list.size.toLong * GraphMemoryEstimates
          .shallowObjectBytes(classOf[scala.collection.immutable.::[_]]) +
          GraphMemoryEstimates.shallowObjectBytes(list.iterableFactory.iterableFactory.getClass)
      val encodedList = writer.serialize(list)

      intercept[RuntimeException] {
        runtime(maxGraphMemoryBytes = Some(listBytes - 1)).deserialize(encodedList)
      }
      runtime(maxGraphMemoryBytes = Some(listBytes)).deserialize(encodedList) shouldEqual list

      val listMap = ListMap("a" -> 1, "b" -> 2, "c" -> 3, "d" -> 4)
      val listMapBytes =
        listMap.size.toLong * GraphMemoryEstimates.shallowObjectBytes(
          Class.forName("scala.collection.immutable.ListMap$Node")) +
          GraphMemoryEstimates.shallowObjectBytes(listMap.mapFactory.mapFactory.getClass)
      val encodedListMap = writer.serialize(listMap)
      intercept[RuntimeException] {
        runtime(maxGraphMemoryBytes = Some(listMapBytes - 1)).deserialize(encodedListMap)
      }
      runtime(maxGraphMemoryBytes = Some(listMapBytes)).deserialize(encodedListMap) shouldEqual listMap
    }

    "reserve mutable BitSet span storage" in {
      val writer = runtime()
      val bitSet = mutable.BitSet(65536)
      val words = 2048L
      val bitSetBytes =
        GraphMemoryEstimates.shallowObjectBytes(classOf[mutable.BitSet]).toLong +
          GraphMemoryEstimates.objectArrayBytes() + words * java.lang.Long.BYTES +
          GraphMemoryEstimates.shallowObjectBytes(
            bitSet.sortedIterableFactory
              .evidenceIterableFactory[Any](bitSet.ordering.asInstanceOf[Ordering[Any]])
              .getClass)
      val bytes = writer.serialize(bitSet)

      intercept[RuntimeException] {
        runtime(maxGraphMemoryBytes = Some(bitSetBytes - 1)).deserialize(bytes)
      }
      runtime(maxGraphMemoryBytes = Some(bitSetBytes)).deserialize(bytes) shouldEqual bitSet
    }
  }
}

case class CollectionStruct1(list: List[String])

case class NestedCollectionStruct(list: List[List[String]], set: Set[Set[String]])

case class MapStruct1(map: Map[String, String])

case class NestedMapStruct(map: Map[String, Map[String, String]])
