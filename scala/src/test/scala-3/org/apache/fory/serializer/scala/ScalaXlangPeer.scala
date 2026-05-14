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
import org.apache.fory.annotation.{ForyCase, ForyField, ForyStruct, ForyUnion}
import org.apache.fory.config.Language
import org.apache.fory.scala.ForySerializer

import java.nio.file.{Files, Path}

@ForyStruct
final case class ScalaPeerUser(
    @ForyField(id = 1) id: Int,
    @ForyField(id = 2) name: String,
    @ForyField(id = 3) email: Option[String])
    derives ForySerializer

@ForyUnion
enum ScalaPeerTarget derives ForySerializer {
  @ForyCase(id = 0)
  case UnknownCase(caseId: Int, value: Any)

  @ForyCase(id = 1)
  case UserCase(value: ScalaPeerUser)
}

object ScalaXlangPeer {
  private val Namespace = "scala_peer"

  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Usage: ScalaXlangPeer <caseName> <dataFile>")
    args(0) match {
      case "derived_struct_round_trip" =>
        roundTripUser(Path.of(args(1)))
      case "known_union_case_round_trip" =>
        roundTripTarget(Path.of(args(1)), preserveUnknownCase = false)
      case "unknown_union_case_round_trip" =>
        roundTripTarget(Path.of(args(1)), preserveUnknownCase = true)
      case other =>
        throw new IllegalArgumentException(s"Unknown Scala xlang peer case: $other")
    }
  }

  private def roundTripUser(dataFile: Path): Unit = {
    val fory = newFory()
    val request = fory.deserialize(Files.readAllBytes(dataFile)).asInstanceOf[ScalaPeerUser]
    Files.write(
      dataFile,
      fory.serialize(request.copy(id = request.id + 1, name = "scala-" + request.name, email = None)))
  }

  private def roundTripTarget(dataFile: Path, preserveUnknownCase: Boolean): Unit = {
    val fory = newFory()
    val request = fory.deserialize(Files.readAllBytes(dataFile)).asInstanceOf[ScalaPeerTarget]
    val response = request match {
      case ScalaPeerTarget.UserCase(user) =>
        ScalaPeerTarget.UserCase(
          user.copy(id = user.id + 1, name = "scala-" + user.name, email = None))
      case ScalaPeerTarget.UnknownCase(caseId, value: ScalaPeerUser) if preserveUnknownCase =>
        ScalaPeerTarget.UnknownCase(
          caseId,
          value.copy(id = value.id + 1, name = "scala-" + value.name, email = None))
      case ScalaPeerTarget.UnknownCase(caseId, value) =>
        ScalaPeerTarget.UnknownCase(caseId, value)
    }
    Files.write(dataFile, fory.serialize(response))
  }

  private def newFory(): Fory = {
    val fory = Fory.builder()
      .withLanguage(Language.XLANG)
      .withCompatible(true)
      .withScalaOptimizationEnabled(true)
      .requireClassRegistration(true)
      .build()
    ScalaSerializers.registerSerializers(fory)
    ForySerializer.register(fory, classOf[ScalaPeerUser], Namespace, "ScalaPeerUser")
    ForySerializer.register(fory, classOf[ScalaPeerTarget], Namespace, "ScalaPeerTarget")
    fory
  }
}
