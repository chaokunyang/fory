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
import org.apache.fory.util.ExceptionUtils;

/** Owns registration/root linearization and the permanent freeze at the first facade root. */
@Internal
public final class FacadeRegistrationGate {
  private enum RegistrationState {
    OPEN,
    REGISTERING,
    FINALIZING,
    FROZEN,
    FAILED
  }

  private final Object lock = new Object();
  private final Runnable finishChildren;
  private boolean childInitializing;
  private volatile RegistrationState state = RegistrationState.OPEN;

  public FacadeRegistrationGate(Runnable finishChildren) {
    this.finishChildren = finishChildren;
  }

  public void applyRegistration(Runnable action) {
    synchronized (lock) {
      beginRegistration();
      try {
        action.run();
        finishRegistration();
      } catch (Throwable e) {
        state = RegistrationState.FAILED;
        throw ExceptionUtils.throwException(e);
      }
    }
  }

  public void applyRegistration(Runnable prepare, Runnable publish) {
    synchronized (lock) {
      beginRegistration();
      try {
        prepare.run();
        requireRegistrationActive();
        publish.run();
        finishRegistration();
      } catch (Throwable e) {
        state = RegistrationState.FAILED;
        throw ExceptionUtils.throwException(e);
      }
    }
  }

  /** Initializes a child while registration callbacks cannot change. */
  public Fory initializeChild(Supplier<Fory> initializer) {
    synchronized (lock) {
      // The monitor is reentrant, so an active initialization here is necessarily the same
      // thread reentering the facade before its provisional child is ready.
      if (childInitializing) {
        throw new IllegalStateException(
            "ThreadSafeFory cannot start a root while a child is being initialized.");
      }
      childInitializing = true;
      try {
        return initializer.get();
      } catch (Throwable e) {
        state = RegistrationState.FAILED;
        throw ExceptionUtils.throwException(e);
      } finally {
        childInitializing = false;
      }
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
      if (current == RegistrationState.REGISTERING) {
        state = RegistrationState.FAILED;
        throw new ForyException(
            "Cannot start a root operation while ThreadSafeFory registration is in progress.");
      }
      if (current == RegistrationState.FINALIZING) {
        throw new ForyException("ThreadSafeFory registration finalization is already in progress.");
      }
      state = RegistrationState.FINALIZING;
      try {
        finishChildren.run();
        state = RegistrationState.FROZEN;
      } catch (Throwable e) {
        // Registration remains permanently closed after a failed first finalization.
        state = RegistrationState.FAILED;
        throw ExceptionUtils.throwException(e);
      }
    }
  }

  private void beginRegistration() {
    RegistrationState current = state;
    if (current == RegistrationState.OPEN) {
      state = RegistrationState.REGISTERING;
      return;
    }
    // The lock is reentrant, so REGISTERING here can only be same-thread facade reentry. Applying
    // its callback now would give existing children and future replay different registration order.
    if (current == RegistrationState.REGISTERING) {
      state = RegistrationState.FAILED;
    }
    throw registrationClosed();
  }

  private void finishRegistration() {
    requireRegistrationActive();
    state = RegistrationState.OPEN;
  }

  private void requireRegistrationActive() {
    if (state != RegistrationState.REGISTERING) {
      throw registrationClosed();
    }
  }

  private ForyException registrationClosed() {
    return new ForyException(
        "Cannot register class/serializer after registration has been frozen or failed. Please "
            + "register all classes before invoking top-level `serialize/deserialize/copy` "
            + "methods of ThreadSafeFory.");
  }
}
