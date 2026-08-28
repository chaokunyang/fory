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
import org.apache.fory.scala.ForyScala
import org.apache.fory.annotation.ForyEnumId
import org.apache.fory.exception.ForyException
import org.apache.fory.resolver.TypeResolver
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ScalaEnumTest {
  enum ColorEnum { case Red, Green, Blue }

  enum StableColorV1 {
    @ForyEnumId(7)
    case Red
    @ForyEnumId(3)
    case Green
  }

  enum StableColorV2 {
    @ForyEnumId(3)
    case Green
    @ForyEnumId(7)
    case Red
  }

  object EnumDiscoveryProbe {
    var initialized: Int = 0
  }

  enum CountingEnum { case Value }

  object CountingEnum {
    EnumDiscoveryProbe.initialized += 1
  }

  case class Colors(set: Set[ColorEnum])
}

class ScalaEnumTest extends AnyWordSpec with Matchers {
  import ScalaEnumTest._

  val fory: Fory = ForyScala.builder()
    .withXlang(false)
    .withRefTracking(true)
    .requireClassRegistration(false).build()

  "fory scala enum support" should {
    "serialize/deserialize ColorEnum" in {
      val bytes = fory.serialize(ColorEnum.Green)
      fory.deserialize(bytes) shouldBe ColorEnum.Green
    }
    "serialize/deserialize Colors" in {
      val colors = Colors(Set(ColorEnum.Green, ColorEnum.Red))
      val bytes = fory.serialize(colors)
      fory.deserialize(bytes) shouldEqual colors
    }
    "use case-level ForyEnumId metadata for stable xlang enum tags" in {
      val writer = ForyScala.builder()
        .withXlang(true)
        .withRefTracking(false)
        .requireClassRegistration(true)
        .build()
      val reader = ForyScala.builder()
        .withXlang(true)
        .withRefTracking(false)
        .requireClassRegistration(true)
        .build()
      ScalaSerializers.registerEnum(writer, classOf[StableColorV1], "scala_test.StableColor")
      ScalaSerializers.registerEnum(reader, classOf[StableColorV2], "scala_test.StableColor")
      intercept[IllegalArgumentException] {
        ScalaSerializers.registerEnum(writer, classOf[ColorEnum], "scala_test", "Bad.Name")
      }

      reader.deserialize(writer.serialize(StableColorV1.Green)) shouldBe StableColorV2.Green
    }
    "reject late bootstrap before enum discovery" in {
      val frozen = ForyScala.builder()
        .withXlang(false)
        .requireClassRegistration(false)
        .build()
      frozen.serialize(1)

      intercept[ForyException] {
        ScalaSerializers.registerSerializers(frozen)
      }
      EnumDiscoveryProbe.initialized = 0
      intercept[ForyException] {
        ScalaSerializers.registerEnum(frozen, classOf[CountingEnum], 712L)
      }
      EnumDiscoveryProbe.initialized shouldBe 0
    }
    "reject discovery after failed finalization" in {
      val failed = ForyScala.builder()
        .withXlang(false)
        .requireClassRegistration(false)
        .build()
      intercept[ForyException] {
        failed.registerSerializerAndType(
          classOf[Colors],
          (_: TypeResolver) => {
            failed.serialize("freeze")
            null
          })
      }
      failed.getTypeResolver.isRegistrationFrozen shouldBe true
      failed.getTypeResolver.isRegistrationFinished shouldBe false

      EnumDiscoveryProbe.initialized = 0
      intercept[ForyException] {
        ScalaSerializers.registerEnum(failed, classOf[CountingEnum], 713L)
      }
      EnumDiscoveryProbe.initialized shouldBe 0
    }
  }
}
