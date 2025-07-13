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

// Regular Scala class with default values (not a case class)
class RegularScalaClass(val name: String, val age: Int = 25, val city: String = "Unknown") {
  override def equals(obj: Any): Boolean = obj match {
    case that: RegularScalaClass => 
      this.name == that.name && this.age == that.age && this.city == that.city
    case _ => false
  }
  
  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (name == null) 0 else name.hashCode)
    result = prime * result + age
    result = prime * result + (if (city == null) 0 else city.hashCode)
    result
  }
  
  override def toString: String = s"RegularScalaClass($name, $age, $city)"
}

class ScalaDefaultValueTest extends AnyWordSpec with Matchers {
  
  // Test both runtime mode (MetaSharedSerializer) and codegen mode (MetaSharedCodecBuilder)
  val testModes = Seq(
    ("Runtime Mode", false),
    ("Codegen Mode", true)
  )

  def createFory(codegen: Boolean): Fory = Fory.builder()
    .withLanguage(Language.JAVA)
    .withRefTracking(true)
    .withScalaOptimizationEnabled(true)
    .requireClassRegistration(false)
    .suppressClassRegistrationWarnings(false)
    .withCodegen(codegen)
    .build()

  "Fory Scala default value support" should {
    testModes.foreach { case (modeName, codegen) =>
      s"serialize/deserialize case class with default values in $modeName" in {
        val fory = createFory(codegen)
        val original = CaseClassWithDefaults("test", 42)
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
        deserialized shouldEqual original
        deserialized.x shouldEqual 42
      }

      s"handle missing fields with default values during deserialization in $modeName" in {
        val fory = createFory(codegen)
        val original = CaseClassWithDefaults("test")
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
        deserialized shouldEqual original
        deserialized.x shouldEqual 1
      }

      s"handle multiple default values in $modeName" in {
        val fory = createFory(codegen)
        val original = CaseClassMultipleDefaults("test")
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
        deserialized shouldEqual original
        deserialized.x shouldEqual 1
        deserialized.y shouldEqual 2.0
      }

      s"work with complex default values in $modeName" in {
        val fory = createFory(codegen)
        val original = CaseClassComplexDefaults("test")
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassComplexDefaults]
        deserialized shouldEqual original
        deserialized.list shouldEqual List(1, 2, 3)
      }

      s"handle schema evolution with default values in $modeName" in {
        val fory = createFory(codegen)
        val original = CaseClassMultipleDefaults("test", 42, 3.14)
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
        deserialized.v shouldEqual "test"
        deserialized.x shouldEqual 42
        deserialized.y shouldEqual 3.14
      }

      s"serialize/deserialize nested case class with default values in $modeName" in {
        val fory = createFory(codegen)
        import NestedCases._
        val original = NestedCaseClass("nestedTest", 123)
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[NestedCaseClass]
        deserialized shouldEqual original
        deserialized.b shouldEqual 123
        deserialized.c shouldEqual Some("nested")
      }

      s"handle missing fields with default values in nested case class in $modeName" in {
        val fory = createFory(codegen)
        import NestedCases._
        val original = NestedCaseClass("nestedTest") // b=99, c=Some("nested")
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[NestedCaseClass]
        deserialized shouldEqual original
        deserialized.b shouldEqual 99
        deserialized.c shouldEqual Some("nested")
      }

      s"serialize/deserialize regular Scala class with default values in $modeName" in {
        val fory = createFory(codegen)
        val original = new RegularScalaClass("John", 30, "New York")
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[RegularScalaClass]
        deserialized shouldEqual original
        deserialized.name shouldEqual "John"
        deserialized.age shouldEqual 30
        deserialized.city shouldEqual "New York"
      }

      s"handle missing fields with default values in regular Scala class in $modeName" in {
        val fory = createFory(codegen)
        val original = new RegularScalaClass("Jane") // age=25, city="Unknown"
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[RegularScalaClass]
        deserialized shouldEqual original
        deserialized.name shouldEqual "Jane"
        deserialized.age shouldEqual 25
        deserialized.city shouldEqual "Unknown"
      }

      s"handle partial default values in regular Scala class in $modeName" in {
        val fory = createFory(codegen)
        val original = new RegularScalaClass("Bob", 35) // city="Unknown"
        val serialized = fory.serialize(original)
        val deserialized = fory.deserialize(serialized).asInstanceOf[RegularScalaClass]
        deserialized shouldEqual original
        deserialized.name shouldEqual "Bob"
        deserialized.age shouldEqual 35
        deserialized.city shouldEqual "Unknown"
      }
    }
  }
}

// Test case classes with default values
case class CaseClassWithDefaults(v: String, x: Int = 1)
case class CaseClassMultipleDefaults(v: String, x: Int = 1, y: Double = 2.0)
case class CaseClassComplexDefaults(v: String, list: List[Int] = List(1, 2, 3)) 