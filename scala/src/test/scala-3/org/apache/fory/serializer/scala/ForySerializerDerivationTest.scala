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
import org.apache.fory.annotation.{ForyCase, ForyField, ForyStruct, ForyUnion, Ref}
import org.apache.fory.scala.ForySerializer
import org.apache.fory.serializer.StaticGeneratedStructSerializerFactory
import org.apache.fory.`type`.TypeUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

object ForySerializerDerivationTest {
  @ForyStruct
  final case class Person(
      @ForyField(id = 1) name: String,
      @ForyField(id = 2) age: Int,
      @ForyField(id = 3) email: Option[String])
      derives ForySerializer

  @ForyStruct
  final case class SearchUser(@ForyField(id = 1) name: String) derives ForySerializer

  @ForyStruct
  final case class CollectionBox(
      @ForyField(id = 1) names: List[String],
      @ForyField(id = 2) tags: Set[String],
      @ForyField(id = 3) scores: Map[String, Int])
      derives ForySerializer

  @ForyStruct
  final case class CopyBox(
      @ForyField(id = 1) user: SearchUser,
      @ForyField(id = 2) names: List[String],
      @ForyField(id = 3) values: Array[Int])
      derives ForySerializer

  @ForyStruct
  final class RefNode() derives ForySerializer {
    @ForyField(id = 1)
    var children: List[RefNode @Ref] = List.empty

    @Ref
    @ForyField(id = 2)
    var parent: Option[RefNode @Ref] = None
  }

  @ForyStruct
  final class UnionRefNode() derives ForySerializer {
    @ForyField(id = 1)
    var name: String = ""

    @Ref
    @ForyField(id = 2)
    var choice: Option[UnionCycle @Ref] = None
  }

  @ForyStruct
  final class MixedRecord(@ForyField(id = 1) val id: Int) derives ForySerializer {
    @ForyField(id = 2)
    var name: String = ""
  }

  @ForyUnion
  enum SearchTarget derives ForySerializer {
    @ForyCase(id = 0)
    case UnknownCase(caseId: Int, value: Any)

    @ForyCase(id = 1)
    case UserCase(value: SearchUser)

    @ForyCase(id = 2)
    case FixedIdCase(value: Int)

    @ForyCase(id = 3)
    case OptionalUserCase(value: Option[SearchUser])
  }

  @ForyUnion
  enum UnionCycle derives ForySerializer {
    @ForyCase(id = 0)
    case UnknownCase(caseId: Int, value: Any)

    @ForyCase(id = 1)
    case NodeCase(value: UnionRefNode)
  }

  def xlangFory(): Fory = {
    val fory = Fory.builder()
      .withXlang(true)
      .withRefTracking(true)
      .withRefCopy(true)
      .withScalaOptimizationEnabled(true)
      .requireClassRegistration(true)
      .suppressClassRegistrationWarnings(false)
      .build()
    ScalaSerializers.registerSerializers(fory)
    ForySerializer.register(fory, classOf[Person], "scala_test", "Person")
    ForySerializer.register(fory, classOf[SearchUser], "scala_test", "SearchUser")
    ForySerializer.register(fory, classOf[CollectionBox], "scala_test", "CollectionBox")
    ForySerializer.register(fory, classOf[CopyBox], "scala_test", "CopyBox")
    ForySerializer.register(fory, classOf[RefNode], "scala_test", "RefNode")
    ForySerializer.register(fory, classOf[UnionRefNode], "scala_test", "UnionRefNode")
    ForySerializer.register(fory, classOf[MixedRecord], "scala_test", "MixedRecord")
    ForySerializer.register(fory, classOf[SearchTarget], "scala_test", "SearchTarget")
    ForySerializer.register(fory, classOf[UnionCycle], "scala_test", "UnionCycle")
    fory
  }
}

class ForySerializerDerivationTest extends AnyWordSpec with Matchers {
  import ForySerializerDerivationTest._

  "Scala 3 ForySerializer derivation" should {
    "serialize derived case classes with Option fields" in {
      val fory = xlangFory()
      fory.deserialize(fory.serialize(Person("Ada", 36, Some("ada@example.com")))) shouldEqual
        Person("Ada", 36, Some("ada@example.com"))
      fory.deserialize(fory.serialize(Person("Grace", 85, None))) shouldEqual
        Person("Grace", 85, None)
    }

    "serialize derived union enum cases" in {
      val fory = xlangFory()
      val user = SearchTarget.UserCase(SearchUser("Ada"))
      val fixed = SearchTarget.FixedIdCase(7)
      fory.deserialize(fory.serialize(user)) shouldEqual user
      fory.deserialize(fory.serialize(fixed)) shouldEqual fixed
    }

    "serialize derived case classes with Scala collection fields" in {
      val fory = xlangFory()
      val box = CollectionBox(List("a", "b"), Set("x", "y"), Map("a" -> 1, "b" -> 2))
      fory.deserialize(fory.serialize(box)) shouldEqual box
    }

    "serialize mixed constructor and mutable field classes" in {
      val fory = xlangFory()
      val record = new MixedRecord(7)
      record.name = "Ada"
      val restored = fory.deserialize(fory.serialize(record)).asInstanceOf[MixedRecord]
      restored.id shouldBe 7
      restored.name shouldBe "Ada"
    }

    "preserve nested reference metadata in generated descriptors" in {
      val factory =
        summon[ForySerializer[RefNode]]
          .asInstanceOf[StaticGeneratedStructSerializerFactory[RefNode]]
      val descriptors = factory.getGeneratedDescriptors.asScala
      val children = descriptors.find(_.getName == "children").get
      val parent = descriptors.find(_.getName == "parent").get

      children.isTrackingRef shouldBe false
      TypeUtils.getElementType(children.getTypeRef).getTypeExtMeta.trackingRef() shouldBe true
      parent.isNullable shouldBe true
      parent.isTrackingRef shouldBe true
    }

    "serialize derived union unknown cases with original ids" in {
      val fory = xlangFory()
      val unknown = SearchTarget.UnknownCase(99, SearchUser("Future"))
      fory.deserialize(fory.serialize(unknown)) shouldEqual unknown
    }

    "serialize and copy derived union Option payloads" in {
      val fory = xlangFory()
      val some: SearchTarget.OptionalUserCase =
        SearchTarget.OptionalUserCase(Some(SearchUser("Ada")))
      val none: SearchTarget.OptionalUserCase = SearchTarget.OptionalUserCase(None)

      fory.deserialize(fory.serialize(some)) shouldEqual some
      fory.deserialize(fory.serialize(none)) shouldEqual none

      val copiedSome = fory.copy(some).asInstanceOf[SearchTarget.OptionalUserCase]
      val copiedNone = fory.copy(none).asInstanceOf[SearchTarget.OptionalUserCase]

      copiedSome shouldEqual some
      copiedSome should not be theSameInstanceAs(some)
      copiedSome.value.get should not be theSameInstanceAs(some.value.get)
      copiedNone shouldEqual none
      copiedNone should not be theSameInstanceAs(none)
    }

    "copy derived case classes through field serializers" in {
      val fory = xlangFory()
      val box = CopyBox(SearchUser("Ada"), List("compiler", "runtime"), Array(1, 2, 3))

      val copied = fory.copy(box)

      copied should not be theSameInstanceAs(box)
      copied.user shouldEqual box.user
      copied.user should not be theSameInstanceAs(box.user)
      copied.names shouldEqual box.names
      copied.names should not be theSameInstanceAs(box.names)
      copied.values.sameElements(box.values) shouldBe true
      copied.values should not be theSameInstanceAs(box.values)
    }

    "copy derived normal classes with ref cycles" in {
      val fory = xlangFory()
      val root = new RefNode()
      val child = new RefNode()
      child.parent = Some(root)
      root.children = List(child)

      val copied = fory.copy(root)

      copied should not be theSameInstanceAs(root)
      copied.children.head should not be theSameInstanceAs(child)
      copied.children.head.parent.get shouldBe theSameInstanceAs(copied)
    }

    "copy cyclic graphs rooted at mutable classes with union edges" in {
      val fory = xlangFory()
      val root = new UnionRefNode()
      root.name = "root"
      val choice = UnionCycle.NodeCase(root)
      root.choice = Some(choice)

      val copied = fory.copy(root)

      copied should not be theSameInstanceAs(root)
      copied.name shouldBe "root"
      copied.choice.get should not be theSameInstanceAs(choice)
      copied.choice.get match {
        case UnionCycle.NodeCase(value) => value shouldBe theSameInstanceAs(copied)
        case other => fail(s"Unexpected copied union case $other")
      }
    }

    "reject cyclic copies rooted at immutable union values" in {
      val fory = xlangFory()
      val root = new UnionRefNode()
      val choice = UnionCycle.NodeCase(root)
      root.choice = Some(choice)

      val error = intercept[org.apache.fory.exception.CopyException] {
        fory.copy(choice)
      }

      error.getMessage should include("constructor-owned immutable value")
      error.getMessage should include(classOf[UnionCycle.NodeCase].getName)
    }

    "copy derived union cases through payload serializers" in {
      val fory = xlangFory()
      val target = SearchTarget.UserCase(SearchUser("Ada"))

      val copied = fory.copy(target)

      copied shouldEqual target
      copied should not be theSameInstanceAs(target)
      copied.asInstanceOf[SearchTarget.UserCase].value should not be theSameInstanceAs(
        target.asInstanceOf[SearchTarget.UserCase].value)
    }

    "copy derived union unknown cases" in {
      val fory = xlangFory()
      val unknown: SearchTarget.UnknownCase =
        SearchTarget.UnknownCase(99, SearchUser("Future"))

      val copied = fory.copy(unknown).asInstanceOf[SearchTarget.UnknownCase]

      copied.caseId shouldBe 99
      copied.value.asInstanceOf[SearchUser] shouldEqual unknown.value.asInstanceOf[SearchUser]
      copied.value.asInstanceOf[SearchUser] should not be theSameInstanceAs(
        unknown.value.asInstanceOf[SearchUser])
    }
  }
}
