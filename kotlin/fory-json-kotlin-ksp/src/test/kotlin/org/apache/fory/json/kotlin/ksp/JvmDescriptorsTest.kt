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

package org.apache.fory.json.kotlin.ksp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmDescriptorsTest {
  @Test
  fun parsesMethodDescriptors() {
    val descriptor = parseMethodDescriptor("(ZJ[[ILjava/lang/String;)Ljava/util/List;")

    assertEquals(
      listOf("Z", "J", "[[I", "Ljava/lang/String;"),
      descriptor.parameters.map(JvmType::descriptor),
    )
    assertEquals("Ljava/util/List;", descriptor.result)
    assertEquals(2, descriptor.parameters[1].slots)
    assertEquals(1, descriptor.parameters[2].slots)
  }

  @Test
  fun emitsJavaSourceTypes() {
    assertEquals("int", JvmType("I").sourceName)
    assertEquals("long[][]", JvmType("[[J").sourceName)
    assertEquals("example.Outer.Inner", JvmType("Lexample/Outer\$Inner;").sourceName)
    assertEquals("java.lang.String[]", JvmType("[Ljava/lang/String;").sourceName)
    assertEquals("java.lang.String[].class", JvmType("[Ljava/lang/String;").classLiteral)
  }

  @Test
  fun emitsCreatorArguments() {
    assertEquals("((Long) value).longValue()", JvmType("J").argumentExpression("value"))
    assertEquals(
      "(java.lang.String) value",
      JvmType("Ljava/lang/String;").argumentExpression("value")
    )
    assertEquals("0.0d", JvmType("D").defaultExpression)
    assertEquals("'\\u0000'", JvmType("C").defaultExpression)
    assertEquals("null", JvmType("[I").defaultExpression)
    assertTrue(JvmType("I").primitive)
    assertFalse(JvmType("[I").primitive)
  }

  @Test
  fun buildsMethodDescriptors() {
    val parameters = listOf(JvmType("J"), JvmType("Ljava/lang/String;"))

    assertEquals("(JLjava/lang/String;)V", methodDescriptor(parameters, "V"))
    assertEquals(
      "(JLjava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
      appendParameters(
        "(JLjava/lang/String;)V",
        listOf(JvmType("I"), JvmType("Lkotlin/jvm/internal/DefaultConstructorMarker;")),
      ),
    )
  }

  @Test
  fun rejectsMalformedDescriptors() {
    assertFailsWith<IllegalArgumentException> { JvmType("V") }
    assertFailsWith<IllegalArgumentException> { JvmType("II") }
    assertFailsWith<IllegalArgumentException> { JvmType("[V") }
    assertFailsWith<IllegalArgumentException> { JvmType("Ljava/lang/String") }
    assertFailsWith<IllegalArgumentException> { parseMethodDescriptor("I)V") }
    assertFailsWith<IllegalArgumentException> { parseMethodDescriptor("(I)Vextra") }
  }
}
