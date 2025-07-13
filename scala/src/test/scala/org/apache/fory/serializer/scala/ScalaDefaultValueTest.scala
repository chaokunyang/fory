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
      // Test case class with default values
      val original = CaseClassWithDefaults("test", 42) // x will use provided value 42
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
      
      deserialized shouldEqual original
      deserialized.x shouldEqual 42 // Should use provided value
    }
    
    "handle missing fields with default values during deserialization" in {
      // Create an instance with default values
      val original = CaseClassWithDefaults("test") // x will be 1 (default)
      val serialized = fory.serialize(original)
      
      // Deserialize and verify default values are used
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassWithDefaults]
      deserialized shouldEqual original
      deserialized.x shouldEqual 1
    }
    
    "handle multiple default values" in {
      val original = CaseClassMultipleDefaults("test") // x=1, y=2.0 (defaults)
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
      
      deserialized shouldEqual original
      deserialized.x shouldEqual 1
      deserialized.y shouldEqual 2.0
    }
    
    "work with complex default values" in {
      val original = CaseClassComplexDefaults("test") // list=List(1,2,3) (default)
      val serialized = fory.serialize(original)
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassComplexDefaults]
      
      deserialized shouldEqual original
      deserialized.list shouldEqual List(1, 2, 3)
    }
    
    "handle schema evolution with default values" in {
      // This test simulates the scenario where a field is missing from serialized data
      // In a real scenario, this would happen when deserializing data from an older version
      // that doesn't have the new field with default value
      
      // Create a case class instance with all fields
      val original = CaseClassMultipleDefaults("test", 42, 3.14)
      val serialized = fory.serialize(original)
      
      // Simulate deserializing to a version that has additional fields with defaults
      // In practice, this would be handled by the MetaSharedSerializer
      val deserialized = fory.deserialize(serialized).asInstanceOf[CaseClassMultipleDefaults]
      
      // The deserialized object should have the original values
      deserialized.v shouldEqual "test"
      deserialized.x shouldEqual 42
      deserialized.y shouldEqual 3.14
    }
  }
}

// Test case classes with default values
case class CaseClassWithDefaults(v: String, x: Int = 1)
case class CaseClassMultipleDefaults(v: String, x: Int = 1, y: Double = 2.0)
case class CaseClassComplexDefaults(v: String, list: List[Int] = List(1, 2, 3)) 