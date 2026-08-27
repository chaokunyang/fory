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
  private enum RegistrationState {
    OPEN,
    FINALIZING,
    FROZEN,
    FAILED
  }

  private final Object lock = new Object();
  private final Runnable finishChildren;
  private volatile RegistrationState state = RegistrationState.OPEN;

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

  public void applyRegistration(Runnable prepare, Runnable publish) {
    synchronized (lock) {
      checkRegistrationAllowed();
      prepare.run();
      checkRegistrationAllowed();
      publish.run();
    }
  }

  /** Initializes a child while registration callbacks cannot change. */
  public Fory initializeChild(Supplier<Fory> initializer) {
    synchronized (lock) {
      return initializer.get();
    }
  }

  void finishChildIfFrozen(Fory child) {
    if (state == RegistrationState.FROZEN) {
      child.getTypeResolver().finishRegistration();
    }
  }

  public void freeze() {
    RegistrationState current = state;
    if (current == RegistrationState.FROZEN) {
      return;
    }
    synchronized (lock) {
      current = state;
      if (current == RegistrationState.FROZEN) {
        return;
      }
      if (current == RegistrationState.FAILED) {
        throw new ForyException("ThreadSafeFory registration finalization previously failed.");
      }
      if (current == RegistrationState.FINALIZING) {
        throw new ForyException("ThreadSafeFory registration finalization is already in progress.");
      }
      state = RegistrationState.FINALIZING;
      try {
        finishChildren.run();
        state = RegistrationState.FROZEN;
      } catch (RuntimeException | Error e) {
        // Registration remains permanently closed after a failed first finalization.
        state = RegistrationState.FAILED;
        throw e;
      }
    }
  }

  private void checkRegistrationAllowed() {
    if (state != RegistrationState.OPEN) {
      throw new ForyException(
          "Cannot register class/serializer after registration has been frozen. Please register "
              + "all classes before invoking top-level `serialize/deserialize/copy` methods of "
              + "ThreadSafeFory.");
    }
  }
}
