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

package org.apache.fury.classloader;

import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.Language;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ThreadSafeFuryClassLoaderTest {

  static class MyClassLoader extends ClassLoader {}

  @Test
  void testFuryThreadLocalUseProvidedClassLoader() throws InterruptedException {
    final MyClassLoader myClassLoader = new MyClassLoader();
    final ThreadSafeFury fury =
        Fury.builder()
            .withClassLoader(myClassLoader)
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .buildThreadLocalFury();
    fury.setClassLoader(myClassLoader);

    Thread thread =
        new Thread(
            () -> {
              final ClassLoader t = fury.getClassLoader();
              Assert.assertEquals(t, myClassLoader);
            });
    thread.start();
    thread.join();
  }

  @Test
  void testFuryPoolUseProvidedClassLoader() throws InterruptedException {
    final MyClassLoader myClassLoader = new MyClassLoader();
    final ThreadSafeFury fury =
        Fury.builder()
            .withClassLoader(myClassLoader)
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .buildThreadSafeFuryPool(1, 1);
    fury.setClassLoader(myClassLoader);

    Thread thread =
        new Thread(
            () -> {
              final ClassLoader t = fury.getClassLoader();
              Assert.assertEquals(t, myClassLoader);
            });
    thread.start();
    thread.join();
  }
}
