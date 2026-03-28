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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.config.Language;
import org.apache.fory.exception.ForyException;
import org.apache.fory.test.bean.BeanA;
import org.apache.fory.test.bean.BeanB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ThreadSafeForyPoolTest extends ForyTestBase {

  @Test
  public void testBuilderReturnsThreadSafeForyPool() {
    ThreadSafeFory fory = Fory.builder().requireClassRegistration(false).buildThreadSafeFory();
    assertTrue(fory instanceof ThreadSafeForyPool);
  }

  @Test
  public void testPoolSerializeAcrossThreads() throws Exception {
    BeanA beanA = BeanA.createBeanA(2);
    ThreadSafeForyPool fory =
        (ThreadSafeForyPool)
            Fory.builder()
                .withLanguage(Language.JAVA)
                .withRefTracking(true)
                .requireClassRegistration(false)
                .withAsyncCompilation(true)
                .buildThreadSafeForyPool(4);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    for (int i = 0; i < 200; i++) {
      executor.execute(
          () -> {
            try {
              for (int j = 0; j < 10; j++) {
                assertEquals(fory.deserialize(fory.serialize(beanA)), beanA);
              }
            } catch (Throwable t) {
              failure.compareAndSet(null, t);
            }
          });
    }
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  @Test
  public void testRegisterAfterFirstUseThrowsException() {
    ThreadSafeForyPool fory =
        (ThreadSafeForyPool)
            Fory.builder().requireClassRegistration(true).buildThreadSafeForyPool(2);
    fory.register(BeanA.class);
    fory.serialize("ok");
    Assert.assertThrows(ForyException.class, () -> fory.register(BeanB.class));
  }
}
