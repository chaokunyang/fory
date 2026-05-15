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

package org.apache.fory.idl_tests

import basic.{BasicEnvelope, BasicForyRegistration, BasicValue, Money}
import collection.{
  CollectionForyRegistration,
  NumericCollectionArrayUnion,
  NumericCollectionUnion,
  NumericCollections,
  NumericCollectionsArray
}
import example.{ExampleForyRegistration, ExampleMessage, ExampleState}
import nested_name.NestedNameForyRegistration
import org.apache.fory.Fory
import org.apache.fory.annotation.ForyEnumId
import org.apache.fory.meta.FieldTypes
import org.apache.fory.scala.ForySerializer
import org.apache.fory.serializer.StaticGeneratedStructSerializer
import org.apache.fory.`type`.{ScalaTypes, TypeUtils, Types}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import tree.{TreeForyRegistration, TreeNode}

import java.math.BigDecimal
import scala.jdk.CollectionConverters._

final class ScalaIdlRoundTripTest extends AnyWordSpec with Matchers {
  "generated Scala IDL models" should {
    "round trip case classes, Option fields, and ADT union cases" in {
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(true)
        .withRefTracking(true)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      BasicForyRegistration.register(fory)

      val envelope = BasicEnvelope(
        Some(Money(new BigDecimal("12.34"), "USD")),
        BasicValue.MoneyCase(Money(new BigDecimal("56.78"), "EUR")),
        None)

      fory.deserialize(fory.serialize(envelope)) shouldEqual envelope
    }

    "round trip generated Scala collection metadata" in {
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(true)
        .withRefTracking(true)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      CollectionForyRegistration.register(fory)

      val collections = NumericCollections(
        int8Values = List(1.toByte, (-2).toByte),
        int16Values = List(3.toShort, (-4).toShort),
        int32Values = List(5, -6),
        int64Values = List(7L, -8L),
        uint8Values = List(9, 255),
        uint16Values = List(10, 65535),
        uint32Values = List(11L, 4294967295L),
        uint64Values = List(12L, -1L),
        float32Values = List(1.5f, -2.5f),
        float64Values = List(3.5d, -4.5d))
      val union = NumericCollectionUnion.Uint32ValuesCase(List(1L, 4294967295L))
      val arrays = NumericCollectionsArray(
        int8Values = Array[Byte](1, -2),
        int16Values = Array[Short](3, -4),
        int32Values = Array[Int](5, -6),
        int64Values = Array[Long](7L, -8L),
        uint8Values = Array[Byte](9, -1),
        uint16Values = Array[Short](10, -1),
        uint32Values = Array[Int](11, -1),
        uint64Values = Array[Long](12L, -1L),
        float32Values = Array[Float](1.5f, -2.5f),
        float64Values = Array[Double](3.5d, -4.5d))
      val arrayUnion = NumericCollectionArrayUnion.Uint32ValuesCase(Array[Int](1, -1))

      fory.deserialize(fory.serialize(collections)) shouldEqual collections
      fory.deserialize(fory.serialize(union)) shouldEqual union
      val arraysRoundTrip = fory.deserialize(fory.serialize(arrays)).asInstanceOf[NumericCollectionsArray]
      arraysRoundTrip.int8Values.sameElements(arrays.int8Values) shouldBe true
      arraysRoundTrip.uint32Values.sameElements(arrays.uint32Values) shouldBe true
      val arrayUnionRoundTrip =
        fory.deserialize(fory.serialize(arrayUnion)).asInstanceOf[NumericCollectionArrayUnion]
      arrayUnionRoundTrip.asInstanceOf[NumericCollectionArrayUnion.Uint32ValuesCase].value
        .sameElements(arrayUnion.asInstanceOf[NumericCollectionArrayUnion.Uint32ValuesCase].value) shouldBe true
    }

    "preserve generated Scala enum metadata in nested descriptors" in {
      ScalaTypes.isScalaEnumType(classOf[ExampleState]) shouldBe true
      val readyCase =
        Class.forName("example.ExampleState$").getDeclaredField("Ready").getAnnotation(classOf[ForyEnumId])
      readyCase.value() shouldBe 1
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(false)
        .withRefTracking(false)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      ExampleForyRegistration.register(fory)
      val serializer =
        summon[ForySerializer[ExampleMessage]]
          .createSerializer(fory.getTypeResolver)
          .asInstanceOf[StaticGeneratedStructSerializer[ExampleMessage]]
      val descriptors = serializer.getGeneratedDescriptors.asScala
      val enumValue = descriptors.find(_.getName == "enumValue").get
      val enumList = descriptors.find(_.getName == "enumList").get
      val enumMap = descriptors.find(_.getName == "enumValuesByName").get
      val uint8ArrayList = descriptors.find(_.getName == "uint8ArrayList").get
      val uint8ArrayMap = descriptors.find(_.getName == "uint8ArrayValuesByName").get

      enumValue.getTypeRef.getTypeExtMeta.typeId() shouldBe Types.ENUM
      TypeUtils.getElementType(enumList.getTypeRef).getTypeExtMeta.typeId() shouldBe Types.ENUM
      TypeUtils.getMapKeyValueType(enumMap.getTypeRef).f1.getTypeExtMeta.typeId() shouldBe Types.ENUM
      TypeUtils.getElementType(uint8ArrayList.getTypeRef).getComponentType.getTypeExtMeta
        .typeId() shouldBe Types.UINT8
      TypeUtils.getMapKeyValueType(uint8ArrayMap.getTypeRef).f1.getComponentType.getTypeExtMeta
        .typeId() shouldBe Types.UINT8

      val fieldGroups = serializer.buildLocalFieldGroups(serializer.getGeneratedDescriptors)
      val enumListInfo = fieldGroups.allFields.find(_.descriptor.getName == "enumList").get
      enumListInfo.genericType.getTypeParameter0.getTypeRef.getTypeExtMeta.typeId() shouldBe
        Types.ENUM
      enumListInfo.genericType.getTypeParameter0.isMonomorphic shouldBe true
      FieldTypes
        .buildFieldType(fory.getTypeResolver, uint8ArrayList)
        .asInstanceOf[FieldTypes.CollectionFieldType]
        .getElementType
        .getTypeId shouldBe Types.UINT8_ARRAY
      FieldTypes
        .buildFieldType(fory.getTypeResolver, uint8ArrayMap)
        .asInstanceOf[FieldTypes.MapFieldType]
        .getValueType
        .getTypeId shouldBe Types.UINT8_ARRAY
    }

    "round trip generated cycle-owned normal classes" in {
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(true)
        .withRefTracking(true)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      TreeForyRegistration.register(fory)

      val root = new TreeNode()
      root.id = "root"
      root.name = "Root"
      val child = new TreeNode()
      child.id = "child"
      child.name = "Child"
      child.parent = Some(root)
      root.children = List(child)

      val roundTrip = fory.deserialize(fory.serialize(root)).asInstanceOf[TreeNode]
      roundTrip.id shouldEqual "root"
      roundTrip.children.head.id shouldEqual "child"
      roundTrip.children.head.parent.get shouldBe theSameInstanceAs(roundTrip)
    }

    "round trip name-registered nested messages, enums, and unions" in {
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(true)
        .withRefTracking(true)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      NestedNameForyRegistration.register(fory)

      val root = new nested_name.Envelope.Node()
      root.id = "root"
      val child = new nested_name.Envelope.Node()
      child.id = "child"
      child.parent = Some(root)
      child.children = List.empty
      root.children = List(child)
      val envelope = nested_name.Envelope(
        Some(root),
        nested_name.Envelope.Kind.Active,
        nested_name.Envelope.Choice.NodeCase(child))

      val roundTrip = fory.deserialize(fory.serialize(envelope)).asInstanceOf[nested_name.Envelope]
      roundTrip.kind shouldBe nested_name.Envelope.Kind.Active
      val roundTripRoot = roundTrip.root.get
      roundTripRoot.id shouldEqual "root"
      val roundTripChild = roundTripRoot.children.head
      roundTripChild.id shouldEqual "child"
      roundTripChild.parent.get shouldBe theSameInstanceAs(roundTripRoot)
      roundTrip.choice.asInstanceOf[nested_name.Envelope.Choice.NodeCase].value shouldBe
        theSameInstanceAs(roundTripChild)
    }
  }
}
