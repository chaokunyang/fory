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

package org.apache.fory.json;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class JsonClassCache {
  private final ConcurrentMap<Class<?>, JsonClassInfo> classes = new ConcurrentHashMap<>();
  private final JsonCodegen codegen;

  JsonClassCache(boolean codegenEnabled, boolean writeNullFields) {
    codegen = codegenEnabled ? new JsonCodegen(writeNullFields) : null;
  }

  public JsonClassInfo get(Class<?> type) {
    JsonClassInfo classInfo = classes.get(type);
    if (classInfo != null) {
      return classInfo;
    }
    return buildAndPublish(type);
  }

  private synchronized JsonClassInfo buildAndPublish(Class<?> type) {
    JsonClassInfo cached = classes.get(type);
    if (cached != null) {
      return cached;
    }
    JsonClassInfo classInfo = JsonClassInfo.build(type);
    // Codegen may ask for nested class metadata that points back to this type.
    // Publishing metadata before compiling the writer keeps that recursion cache-owned.
    classes.put(type, classInfo);
    if (codegen != null) {
      classInfo.setObjectWriter(codegen.compile(classInfo, this));
    }
    return classInfo;
  }
}
