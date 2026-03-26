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

import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.memory.Platform;
import org.testng.annotations.Test;

public class VirtualThreadSafeForyTest {

  static class CustomClassLoader extends ClassLoader {
    CustomClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  @Test
  public void testPlatformDetectsVirtualThreads() throws InterruptedException {
    AtomicReference<Boolean> isVirtual = new AtomicReference<>();
    Thread thread = Thread.startVirtualThread(() -> isVirtual.set(Platform.isCurrentThreadVirtual()));
    thread.join();
    assertTrue(Boolean.TRUE.equals(isVirtual.get()));
  }

  @Test
  public void testBuildThreadSafeForyUsesFastPoolForVirtualThreadClassLoader()
      throws InterruptedException {
    ThreadSafeFory fory = Fory.builder().requireClassRegistration(false).buildThreadSafeFory();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread thread =
        Thread.startVirtualThread(
            () -> {
              ClassLoader original = Thread.currentThread().getContextClassLoader();
              ClassLoader classLoader = new CustomClassLoader(original);
              try {
                assertTrue(Platform.isCurrentThreadVirtual());
                fory.setClassLoader(classLoader);
                assertSame(Thread.currentThread().getContextClassLoader(), classLoader);
                assertSame(fory.getClassLoader(), classLoader);
                fory.clearClassLoader(classLoader);
                assertSame(Thread.currentThread().getContextClassLoader(), classLoader);
              } catch (Throwable t) {
                error.set(t);
              } finally {
                Thread.currentThread().setContextClassLoader(original);
              }
            });
    thread.join();
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
  }
}
