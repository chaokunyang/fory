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

package org.apache.fory;

import static org.testng.Assert.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fory.serializer.Serializer;
import org.testng.annotations.Test;

public class ApiSurfaceCleanupTest {

  @Test
  public void testSerializerBaseHasNoRuntimeFacadeField() {
    Set<String> fieldNames =
        Arrays.stream(Serializer.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());
    assertFalse(fieldNames.contains("fory"));
  }

  @Test(expectedExceptions = ClassNotFoundException.class)
  public void testSerializerRuntimeFacadeRemoved() throws ClassNotFoundException {
    Class.forName("org.apache.fory.serializer.SerializerRuntime");
  }

  @Test
  public void testForyHasNoInnerIoHelperSurface() {
    Set<String> methodNames =
        Arrays.stream(Fory.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toSet());
    for (String methodName :
        Arrays.asList(
            "getBuffer",
            "resetBuffer",
            "writeRef",
            "writeNonRef",
            "writeData",
            "writeBufferObject",
            "writeString",
            "writeStringRef",
            "writeInt64",
            "readRef",
            "readNonRef",
            "readNullable",
            "readData",
            "readBufferObject",
            "readString",
            "readStringRef",
            "readInt64")) {
      assertFalse(methodNames.contains(methodName), methodName);
    }
  }
}
