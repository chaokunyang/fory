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
import org.apache.fory.config.Language
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object NestedCases {
  case class NestedCaseClass(a: String, b: Int = 99, c: Option[String] = Some("nested"))
}

class ScalaDefaultValueTest extends AnyWordSpec with Matchers {
  def fory: Fory = Fory.builder()
    .withLanguage(Language.JAVA)
    .withRefTracking(true)
    .withScalaOptimizationEnabled(true)
    .requireClassRegistration(false)
    .suppressClassRegistrationWarnings(false)
    .withCodegen(false)
    .build()

  "Fury Scala default value support" should {
    "serialize/deserialize case class with default values" in {
      val original = CaseClassWithDefaults("test", 42)
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
      deserialized shouldEqual original
      deserialized.x shouldEqual 42
    }
    "handle missing fields with default values during deserialization" in {
      val original = CaseClassWithDefaults("test")
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
      deserialized shouldEqual original
      deserialized.x shouldEqual 1
    }
    "handle multiple default values" in {
      val original = CaseClassMultipleDefaults("test")
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
      deserialized shouldEqual original
      deserialized.x shouldEqual 1
      deserialized.y shouldEqual 2.0
    }
    "work with complex default values" in {
      val original = CaseClassComplexDefaults("test")
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassComplexDefaults]
      deserialized shouldEqual original
      deserialized.list shouldEqual List(1, 2, 3)
    }
    "handle schema evolution with default values" in {
      val original = CaseClassMultipleDefaults("test", 42, 3.14)
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
      deserialized.v shouldEqual "test"
      deserialized.x shouldEqual 42
      deserialized.y shouldEqual 3.14
    }
    "serialize/deserialize nested case class with default values" in {
      import NestedCases._
      val original = NestedCaseClass("nestedTest", 123)
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[NestedCaseClass]
      deserialized shouldEqual original
      deserialized.b shouldEqual 123
      deserialized.c shouldEqual Some("nested")
    }
    "handle missing fields with default values in nested case class" in {
      import NestedCases._
      val original = NestedCaseClass("nestedTest") // b=99, c=Some("nested")
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[NestedCaseClass]
      deserialized shouldEqual original
      deserialized.b shouldEqual 99
      deserialized.c shouldEqual Some("nested")
    }
  }
}

// Test case classes with default values
case class CaseClassWithDefaults(v: String, x: Int = 1)
case class CaseClassMultipleDefaults(v: String, x: Int = 1, y: Double = 2.0)
case class CaseClassComplexDefaults(v: String, list: List[Int] = List(1, 2, 3)) 