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

import addressbook.AddressbookForyRegistration
import auto_id.AutoIdForyRegistration
import collection.CollectionForyRegistration
import complex_fbs.ComplexFbsForyRegistration
import complex_pb.ComplexPbForyRegistration
import example.ExampleForyRegistration
import graph.GraphForyRegistration
import monster.MonsterForyRegistration
import optional_types.OptionalTypesForyRegistration
import org.apache.fory.Fory
import org.apache.fory.serializer.scala.ScalaSerializers
import tree.TreeForyRegistration

import java.nio.file.{Files, Path}

object ScalaIdlRoundTripPeer {
  def main(args: Array[String]): Unit = {
    val compatible = sys.env.get("IDL_COMPATIBLE").forall(_.toBoolean)
    roundTrip("DATA_FILE", compatible, refTracking = false)(AddressbookForyRegistration.register)
    roundTrip("DATA_FILE_AUTO_ID", compatible, refTracking = false)(AutoIdForyRegistration.register)
    roundTrip("DATA_FILE_PRIMITIVES", compatible, refTracking = false) { fory =>
      AddressbookForyRegistration.register(fory)
      ComplexPbForyRegistration.register(fory)
    }
    roundTrip("DATA_FILE_COLLECTION", compatible, refTracking = false)(
      CollectionForyRegistration.register)
    roundTrip("DATA_FILE_COLLECTION_UNION", compatible, refTracking = false)(
      CollectionForyRegistration.register)
    roundTrip("DATA_FILE_COLLECTION_ARRAY", compatible, refTracking = false)(
      CollectionForyRegistration.register)
    roundTrip("DATA_FILE_COLLECTION_ARRAY_UNION", compatible, refTracking = false)(
      CollectionForyRegistration.register)
    roundTrip("DATA_FILE_EXAMPLE", compatible, refTracking = false)(ExampleForyRegistration.register)
    roundTrip("DATA_FILE_EXAMPLE_UNION", compatible, refTracking = false)(
      ExampleForyRegistration.register)
    roundTrip("DATA_FILE_OPTIONAL_TYPES", compatible, refTracking = false)(
      OptionalTypesForyRegistration.register)
    roundTrip("DATA_FILE_TREE", compatible, refTracking = true)(TreeForyRegistration.register)
    roundTrip("DATA_FILE_GRAPH", compatible, refTracking = true)(GraphForyRegistration.register)
    roundTrip("DATA_FILE_FLATBUFFERS_MONSTER", compatible, refTracking = false)(
      MonsterForyRegistration.register)
    roundTrip("DATA_FILE_FLATBUFFERS_TEST2", compatible, refTracking = false) { fory =>
      MonsterForyRegistration.register(fory)
      ComplexFbsForyRegistration.register(fory)
    }
  }

  private def roundTrip(
      envName: String,
      compatible: Boolean,
      refTracking: Boolean)(register: Fory => Unit): Unit = {
    sys.env.get(envName).foreach { file =>
      val fory = Fory.builder()
        .withXlang(true)
        .withCompatible(compatible)
        .withRefTracking(refTracking)
        .withScalaOptimizationEnabled(true)
        .requireClassRegistration(true)
        .build()
      ScalaSerializers.registerSerializers(fory)
      register(fory)
      val path = Path.of(file)
      val value = fory.deserialize(Files.readAllBytes(path))
      Files.write(path, fory.serialize(value))
    }
  }
}
