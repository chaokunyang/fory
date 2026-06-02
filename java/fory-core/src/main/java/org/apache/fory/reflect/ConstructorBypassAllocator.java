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

package org.apache.fory.reflect;

import org.apache.fory.annotation.Internal;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._UnsafeUtils;
import sun.misc.Unsafe;

/** Internal JDK8-24 constructor-bypass allocator used by object creators. */
@Internal
final class ConstructorBypassAllocator {
  private static final Unsafe UNSAFE = AndroidSupport.IS_ANDROID ? null : _UnsafeUtils.UNSAFE;

  private ConstructorBypassAllocator() {}

  static <T> T allocate(Class<T> type) {
    if (UNSAFE == null || JdkVersion.MAJOR_VERSION >= 25) {
      throw new ForyException(
          "Constructor-bypassing Unsafe allocation is unsupported in this runtime for " + type);
    }
    try {
      return (T) UNSAFE.allocateInstance(type);
    } catch (InstantiationException e) {
      throw new ForyException("Failed to allocate instance for " + type, e);
    }
  }
}
