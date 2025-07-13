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

package org.apache.fory.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.apache.fory.util.ScalaDefaultValueUtils

// Move case classes to top level for proper companion object access
case class SimpleCaseClass(name: String, age: Int = 25)
case class ComplexCaseClass(a: String, b: Int = 42, c: Option[String] = Some("default"))
class NotACaseClass(val x: Int)

class ScalaDefaultValueUtilsTest extends AnyWordSpec with Matchers {

  "ScalaDefaultValueUtils" should {
    "detect Scala case classes correctly" in {
        ScalaDefaultValueUtils.isScalaCaseClass(classOf[SimpleCaseClass]) shouldBe true
        ScalaDefaultValueUtils.isScalaCaseClass(classOf[ComplexCaseClass]) shouldBe true
        ScalaDefaultValueUtils.isScalaCaseClass(classOf[NotACaseClass]) shouldBe false
        ScalaDefaultValueUtils.isScalaCaseClass(classOf[String]) shouldBe false
        ScalaDefaultValueUtils.isScalaCaseClass(classOf[Int]) shouldBe false
    }

    "return default values for constructor parameters by index" in {
        ScalaDefaultValueUtils.getDefaultValue(classOf[SimpleCaseClass], 1) shouldBe null // name has no default
        ScalaDefaultValueUtils.getDefaultValue(classOf[SimpleCaseClass], 2) shouldBe 25 // age has default 25
        ScalaDefaultValueUtils.getDefaultValue(classOf[ComplexCaseClass], 1) shouldBe null // a has no default
        ScalaDefaultValueUtils.getDefaultValue(classOf[ComplexCaseClass], 2) shouldBe 42 // b has default 42
        ScalaDefaultValueUtils.getDefaultValue(classOf[ComplexCaseClass], 3) shouldBe Some("default") // c has default
        ScalaDefaultValueUtils.getDefaultValue(classOf[String], 1) shouldBe null
    }

    "return default values for fields by name" in {
        ScalaDefaultValueUtils.getDefaultValueForField(classOf[SimpleCaseClass], "name") shouldBe null
        ScalaDefaultValueUtils.getDefaultValueForField(classOf[SimpleCaseClass], "age") shouldBe 25
        ScalaDefaultValueUtils.getDefaultValueForField(classOf[ComplexCaseClass], "a") shouldBe null
        ScalaDefaultValueUtils.getDefaultValueForField(classOf[ComplexCaseClass], "b") shouldBe 42
        ScalaDefaultValueUtils.getDefaultValueForField(classOf[ComplexCaseClass], "c") shouldBe Some("default")
        // String is not a Scala case class, so this should throw an exception
              an[IllegalArgumentException] should be thrownBy {
          ScalaDefaultValueUtils.getDefaultValueForField(classOf[String], "field")
        }
    }

    "handle edge cases for invalid indices" in {
        ScalaDefaultValueUtils.getDefaultValue(classOf[SimpleCaseClass], 0) shouldBe null // 0 is invalid (1-based)
        ScalaDefaultValueUtils.getDefaultValue(classOf[SimpleCaseClass], 10) shouldBe null // out of bounds
    }
  }
} 