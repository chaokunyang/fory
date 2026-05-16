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

import addressbook.AddressbookForyModule
import auto_id.AutoIdForyModule
import collection.CollectionForyModule
import complex_fbs.ComplexFbsForyModule
import complex_pb.ComplexPbForyModule
import example.ExampleForyModule
import graph.GraphForyModule
import monster.MonsterForyModule
import nested_name.NestedNameForyModule
import optional_types.OptionalTypesForyModule
import org.apache.fory.Fory
import org.apache.fory.scala.ForyScala
import tree.TreeForyModule

import java.nio.file.{Files, Path}

object ScalaIdlRoundTripPeer {
  def main(args: Array[String]): Unit = {
    val compatible = sys.env.get("IDL_COMPATIBLE").forall(_.toBoolean)
    roundTrip("DATA_FILE", compatible, refTracking = false)(fory => fory.register(AddressbookForyModule))
    roundTrip("DATA_FILE_AUTO_ID", compatible, refTracking = false)(fory => fory.register(AutoIdForyModule))
    roundTrip("DATA_FILE_NESTED_NAME", compatible, refTracking = true)(fory =>
      fory.register(NestedNameForyModule))
    roundTrip("DATA_FILE_PRIMITIVES", compatible, refTracking = false) { fory =>
      fory.register(AddressbookForyModule)
      fory.register(ComplexPbForyModule)
    }
    roundTrip("DATA_FILE_COLLECTION", compatible, refTracking = false)(fory =>
      fory.register(CollectionForyModule))
    roundTrip("DATA_FILE_COLLECTION_UNION", compatible, refTracking = false)(fory =>
      fory.register(CollectionForyModule))
    roundTrip("DATA_FILE_COLLECTION_ARRAY", compatible, refTracking = false)(fory =>
      fory.register(CollectionForyModule))
    roundTrip("DATA_FILE_COLLECTION_ARRAY_UNION", compatible, refTracking = false)(fory =>
      fory.register(CollectionForyModule))
    roundTrip("DATA_FILE_EXAMPLE", compatible, refTracking = false)(fory => fory.register(ExampleForyModule))
    roundTrip("DATA_FILE_EXAMPLE_UNION", compatible, refTracking = false)(fory =>
      fory.register(ExampleForyModule))
    roundTrip("DATA_FILE_OPTIONAL_TYPES", compatible, refTracking = false)(fory =>
      fory.register(OptionalTypesForyModule))
    roundTrip("DATA_FILE_TREE", compatible, refTracking = true)(fory => fory.register(TreeForyModule))
    roundTrip("DATA_FILE_GRAPH", compatible, refTracking = true)(fory => fory.register(GraphForyModule))
    roundTrip("DATA_FILE_FLATBUFFERS_MONSTER", compatible, refTracking = false)(fory =>
      fory.register(MonsterForyModule))
    roundTrip("DATA_FILE_FLATBUFFERS_TEST2", compatible, refTracking = false) { fory =>
      fory.register(MonsterForyModule)
      fory.register(ComplexFbsForyModule)
    }
  }

  private def roundTrip(
      envName: String,
      compatible: Boolean,
      refTracking: Boolean)(register: Fory => Unit): Unit = {
    sys.env.get(envName).foreach { file =>
      val fory = ForyScala.builder()
        .withXlang(true)
        .withCompatible(compatible)
        .withRefTracking(refTracking)
        .requireClassRegistration(true)
        .build()
      register(fory)
      val path = Path.of(file)
      val value = fory.deserialize(Files.readAllBytes(path))
      Files.write(path, fory.serialize(value))
    }
  }
}
