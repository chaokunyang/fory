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

import java.util.function.Supplier;
import org.apache.fory.annotation.Internal;
import org.apache.fory.exception.ForyException;

/** Owns the permanent registration freeze before a thread-safe facade's first root or callback. */
@Internal
public final class FacadeRegistrationGate {
  private final Object lock = new Object();
  private final Runnable finishChildren;
  private volatile boolean frozen;

  public FacadeRegistrationGate(Runnable finishChildren) {
    this.finishChildren = finishChildren;
  }

  public void applyRegistration(Runnable action) {
    synchronized (lock) {
      checkRegistrationAllowed();
      action.run();
      checkRegistrationAllowed();
    }
  }

  /** Initializes a child while registration callbacks cannot change. */
  public Fory initializeChild(Supplier<Fory> initializer) {
    synchronized (lock) {
      return initializer.get();
    }
  }

  public void freeze() {
    if (!frozen) {
      synchronized (lock) {
        if (!frozen) {
          // Set the permanent facade state first. If child finalization fails, registration must
          // remain closed rather than reopening a partially finalized facade.
          frozen = true;
          finishChildren.run();
        }
      }
    }
  }

  private void checkRegistrationAllowed() {
    if (frozen) {
      throw new ForyException(
          "Cannot register class/serializer after registration has been frozen. Please register "
              + "all classes before invoking top-level `serialize/deserialize/copy` methods of "
              + "ThreadSafeFory.");
    }
  }
}
