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

package org.apache.fory.serializer.kotlin

import org.apache.fory.annotation.Int32Type
import org.apache.fory.annotation.UInt32Type
import org.apache.fory.annotation.UInt64Type
import org.apache.fory.config.Int32Encoding
import org.testng.Assert
import org.testng.annotations.Test

class TypeUseAnnotationTest {
  private class TypeUseCarrier(
    val fixedId: @Int32Type(encoding = Int32Encoding.FIXED) Int = 1,
    val unsignedId: @UInt32Type Int = 2,
    val unsignedValues: List<@UInt64Type Long> = listOf(3L),
    val unsignedArrayValues: Array<@UInt32Type Int> = arrayOf(4),
  )

  @Test
  fun testJavaScalarAnnotationsCompileAtKotlinTypeUseSites() {
    val carrier = TypeUseCarrier()
    Assert.assertEquals(carrier.fixedId, 1)
    Assert.assertEquals(carrier.unsignedId, 2)
    Assert.assertEquals(carrier.unsignedValues, listOf(3L))
    Assert.assertEquals(carrier.unsignedArrayValues.toList(), listOf(4))
  }
}
