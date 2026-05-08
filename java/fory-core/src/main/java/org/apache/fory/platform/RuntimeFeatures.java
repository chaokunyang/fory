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

package org.apache.fory.platform;

import org.apache.fory.annotation.Internal;

/** Runtime feature checks that must be safe to load before Unsafe-backed platform classes. */
@Internal
public final class RuntimeFeatures {
  public static final boolean IS_ANDROID = isAndroid();

  private RuntimeFeatures() {}

  private static boolean isAndroid() {
    return "Dalvik".equals(System.getProperty("java.vm.name", ""))
        || System.getProperty("java.runtime.name", "").contains("Android");
  }
}
