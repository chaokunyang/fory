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

import javax.annotation.concurrent.ThreadSafe;

/**
 * Abstract base class for creating instances of a given type.
 *
 * <p>This class provides a unified interface for object instantiation across different creation
 * strategies such as constructor invocation, unsafe allocation, and record creation.
 * Implementations handle various scenarios including no-arg constructors, parameterized
 * constructors for records, and platform-specific optimizations.
 *
 * <p><strong>Thread Safety:</strong> All implementations of ObjectCreator are thread-safe and can
 * be safely used across multiple threads concurrently. The underlying creation mechanisms
 * (MethodHandle, Constructor, and supported constructor-bypassing allocation) are all thread-safe.
 *
 * @param <T> the type of objects this creator can instantiate
 */
@ThreadSafe
public abstract class ObjectCreator<T> {
  private static final String[] NO_FIELDS = new String[0];
  private static final Class<?>[] NO_TYPES = new Class<?>[0];
  private static final boolean[] NO_FINAL_FIELDS = new boolean[0];

  protected final Class<T> type;

  protected ObjectCreator(Class<T> type) {
    this.type = type;
  }

  /**
   * Creates a new instance of type T using the default creation strategy.
   *
   * @return a new instance of type T
   * @throws RuntimeException if instance creation fails
   * @throws UnsupportedOperationException if this creator doesn't support parameterless creation
   */
  public abstract T newInstance();

  public boolean hasConstructorFields() {
    return false;
  }

  public String[] getConstructorFieldNames() {
    return NO_FIELDS;
  }

  public Class<?>[] getConstructorFieldDeclaringClasses() {
    return null;
  }

  public Class<?>[] getConstructorFieldTypes() {
    return NO_TYPES;
  }

  public boolean[] getConstructorFieldFinal() {
    return NO_FINAL_FIELDS;
  }

  public boolean isConstructorPublic() {
    return false;
  }

  public boolean isOnlyPublicConstructor() {
    return false;
  }

  /**
   * Creates a new instance of type T using the provided arguments.
   *
   * <p>This method is primarily used for record types that require constructor arguments. Most
   * implementations will throw UnsupportedOperationException.
   *
   * @param arguments the arguments to pass to the constructor
   * @return a new instance of type T
   * @throws RuntimeException if instance creation fails
   * @throws UnsupportedOperationException if this creator doesn't support parameterized creation
   */
  public abstract T newInstanceWithArguments(Object... arguments);
}
