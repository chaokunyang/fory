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

/** A reusable Fory runtime module installed during or after runtime construction. */
@FunctionalInterface
public interface ForyModule {
  /**
   * Installs registration setup into the concrete runtime.
   *
   * <p>An installation may register nested modules and child-specific serializers, but it must not
   * start a root through {@code fory} or the direct or thread-safe facade installing the module.
   */
  void install(Fory fory);
}
