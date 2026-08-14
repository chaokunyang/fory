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

package org.apache.fory.json.codegen;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.PropertyNamingStrategy;
import org.testng.annotations.Test;

public class JsonCodegenIdentityTest {
  @Test
  public void rejectClassNameCollision() {
    JsonCodegen codegen =
        new JsonCodegen(
            new JsonCodegenKey(
                false, true, PropertyNamingStrategy.LOWER_CAMEL_CASE, "factory", "mixin"),
            getClass().getClassLoader(),
            false);

    codegen.registerGeneratedIdentity("example.Generated", "complete-signature-a");
    codegen.registerGeneratedIdentity("example.Generated", "complete-signature-a");
    assertEquals(
        codegen.generatedClassSignatures().get("example.Generated"), "complete-signature-a");
    expectThrows(
        ForyJsonException.class,
        () -> codegen.registerGeneratedIdentity("example.Generated", "complete-signature-b"));
  }
}
