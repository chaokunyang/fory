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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Bidirectional map specialized for class registration lookups. */
final class TwoWayClassMap {
  private final Map<String, Class<?>> classesByName;
  private final IdentityHashMap<Class<?>, String> namesByClass;
  private final Inverse inverse = new Inverse();

  TwoWayClassMap(int expectedSize) {
    classesByName = new HashMap<>(expectedSize);
    namesByClass = new IdentityHashMap<>(expectedSize);
  }

  public Class<?> get(String name) {
    return classesByName.get(name);
  }

  public boolean containsKey(String name) {
    return classesByName.containsKey(name);
  }

  public void put(String name, Class<?> cls) {
    classesByName.put(name, cls);
    namesByClass.put(cls, name);
  }

  public Inverse inverse() {
    return inverse;
  }

  final class Inverse {
    private Inverse() {}

    public boolean containsKey(Class<?> cls) {
      return namesByClass.containsKey(cls);
    }

    public String get(Class<?> cls) {
      return namesByClass.get(cls);
    }
  }
}
