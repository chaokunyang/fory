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

package org.apache.fory.json.scala

import org.apache.fory.json.ForyJsonException
import org.scalatest.funsuite.AnyFunSuite
import org.apache.fory.reflect.TypeRef

enum Result derives ScalaJsonCodec {
  case Ok(value: String)
  case Error(code: Int)
  case Pending
}

enum ExternalResult {
  case Ok(value: String)
  case Error(code: Int)
}

enum Color {
  case Red, Blue
}

enum DisplayColor {
  case Red, Blue

  override def toString: String = "display"
}

class ScalaJsonDerivationSuite extends AnyFunSuite {
  test("EmptyTuple uses an empty JSON array") {
    val json = ForyJsonScala.builder().withCodegen(false).build()
    val emptyType = new TypeRef[EmptyTuple]() {}
    assert(json.toJson(EmptyTuple, emptyType) == "[]")
    assert(json.fromJson("[]", emptyType) == EmptyTuple)
  }

  test("derived enum uses one wrapper-object shape") {
    val json = ForyJsonScala.builder().withAsyncCompilation(false).build()
    val ok: Result = Result.Ok("ready")
    val error: Result = Result.Error(7)
    val pending: Result = Result.Pending

    assert(json.toJson(ok) == "{\"Ok\":{\"value\":\"ready\"}}")
    assert(json.toJson(error) == "{\"Error\":{\"code\":7}}")
    assert(json.toJson(pending) == "{\"Pending\":{}}")
    assertThrows[ForyJsonException](
      json.fromJson("{\"value\":\"raw\"}", classOf[Result.Ok])
    )
    assert(json.fromJson(json.toJson(ok), classOf[Result]) == ok)
    assert(json.fromJson(json.toJson(error), classOf[Result]) == error)
    assert(json.fromJson(json.toJson(pending), classOf[Result]) == pending)
  }

  test("third-party enum uses builder registration") {
    val plainJson = ForyJsonScala.builder().withCodegen(false).build()
    assertThrows[ForyJsonException](plainJson.toJson(ExternalResult.Ok("ready")))

    val json =
      ForyJsonScala.builder().register[ExternalResult].withAsyncCompilation(false).build()
    val result: ExternalResult = ExternalResult.Ok("ready")
    assert(json.toJson(result) == "{\"Ok\":{\"value\":\"ready\"}}")
    assert(json.fromJson(json.toJson(result), classOf[ExternalResult]) == result)
  }

  test("parameterless enum uses its declared root") {
    val json = ForyJsonScala.builder().withCodegen(false).build()
    assert(json.toJson(Color.Blue) == "\"Blue\"")
    assert(json.fromJson("\"Blue\"", classOf[Color]) == Color.Blue)
    assert(json.toJson(DisplayColor.Red) == "\"Red\"")
    assert(json.fromJson("\"Blue\"", classOf[DisplayColor]) == DisplayColor.Blue)
  }
}
