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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class ScalaXlangSerializerTest extends AnyWordSpec with Matchers {
  def fory: Fory = {
    val runtime = Fory.builder()
      .withXlang(true)
      .withRefTracking(true)
      .withScalaOptimizationEnabled(true)
      .requireClassRegistration(false)
      .suppressClassRegistrationWarnings(false)
      .build()
    ScalaSerializers.registerSerializers(runtime)
    runtime
  }

  "fory scala xlang support" should {
    "serialize collections with canonical xlang serializers" in {
      val runtime = fory
      val list = List("a", "b", "c")
      val set = Set("a", "b", "c")
      val map = Map("a" -> 1, "b" -> 2)
      runtime
        .deserialize(runtime.serialize(list))
        .asInstanceOf[java.util.List[String]]
        .asScala
        .toList shouldEqual list
      runtime
        .deserialize(runtime.serialize(set))
        .asInstanceOf[java.util.Set[String]]
        .asScala
        .toSet shouldEqual set
      runtime
        .deserialize(runtime.serialize(map))
        .asInstanceOf[java.util.Map[String, Int]]
        .asScala
        .toMap shouldEqual map
    }

  }
}
