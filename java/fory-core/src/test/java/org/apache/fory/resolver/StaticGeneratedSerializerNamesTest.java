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

package org.apache.fory.resolver;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StaticGeneratedSerializerNamesTest {
  @Test
  public void testGoldenGeneratedSerializerNames() {
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.User", StaticGeneratedSerializerRegistry.Mode.XLANG),
        "com.example.User_ForySerializer");
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.User", StaticGeneratedSerializerRegistry.Mode.NATIVE),
        "com.example.User_ForyNativeSerializer");
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.Outer$Inner", StaticGeneratedSerializerRegistry.Mode.XLANG),
        "com.example.Outer_Inner_ForySerializer");
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.Outer_Inner", StaticGeneratedSerializerRegistry.Mode.XLANG),
        "com.example.Outer_u_Inner_ForySerializer");
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.Outer__Inner", StaticGeneratedSerializerRegistry.Mode.XLANG),
        "com.example.Outer_u__u_Inner_ForySerializer");
    Assert.assertEquals(
        StaticGeneratedSerializerNames.generatedSerializerBinaryName(
            "com.example.Outer-Inner", StaticGeneratedSerializerRegistry.Mode.XLANG),
        "com.example.Outer_x2d_Inner_ForySerializer");
  }
}
