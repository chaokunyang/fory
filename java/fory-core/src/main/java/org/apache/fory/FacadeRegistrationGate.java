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

import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.fory.annotation.Internal;
import org.apache.fory.exception.ForyException;

/** Owns the permanent first-root registration freeze for a thread-safe Fory facade. */
@Internal
public final class FacadeRegistrationGate {
  private final Object lock = new Object();
  private volatile boolean frozen;

  public void apply(Runnable action) {
    synchronized (lock) {
      if (frozen) {
        throw new ForyException(
            "Cannot register class/serializer after registration has been frozen. Please register "
                + "all classes before invoking top-level `serialize/deserialize/copy` methods of "
                + "ThreadSafeFory.");
      }
      action.run();
    }
  }

  public <T> T withLock(Supplier<T> action) {
    synchronized (lock) {
      return action.get();
    }
  }

  public void freeze() {
    if (!frozen) {
      synchronized (lock) {
        frozen = true;
      }
    }
  }

  public <R> R execute(Fory fory, Function<Fory, R> action) {
    synchronized (lock) {
      // The callback may return or otherwise retain the raw child. Freeze before exposing it,
      // because a later root through that escaped reference is invisible to the facade.
      frozen = true;
      fory.getTypeResolver().finishRegistration();
    }
    return action.apply(fory);
  }
}
