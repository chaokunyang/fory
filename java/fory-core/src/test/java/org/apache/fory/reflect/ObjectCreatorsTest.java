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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.fory.TestUtils;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.ObjectCreators.ParentNoArgCtrObjectCreator;
import org.testng.Assert;
import org.testng.annotations.Test;

@SuppressWarnings("rawtypes")
public class ObjectCreatorsTest {

  static class NoCtrTestClass {
    int f1;

    public NoCtrTestClass(int f1) {
      this.f1 = f1;
    }
  }

  @Test
  public void testObjectCreator() {
    if (JdkVersion.MAJOR_VERSION >= 25) {
      return;
    }
    ParentNoArgCtrObjectCreator<ArrayBlockingQueue> creator =
        new ParentNoArgCtrObjectCreator<>(ArrayBlockingQueue.class);
    Assert.assertEquals(creator.newInstance().getClass(), ArrayBlockingQueue.class);
    Assert.assertEquals(
        new ParentNoArgCtrObjectCreator<>(NoCtrTestClass.class).newInstance().getClass(),
        NoCtrTestClass.class);
  }

  @Test
  public void testAndroidObjectCreators() throws Exception {
    Process process =
        new ProcessBuilder(TestUtils.javaCommand(AndroidObjectCreatorProbe.class))
            .redirectErrorStream(true)
            .start();
    String output = readFully(process.getInputStream());
    Assert.assertEquals(process.waitFor(), 0, output);
  }

  private static String readFully(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, read);
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  public static final class AndroidObjectCreatorProbe {
    public static void main(String[] args) {
      System.setProperty("java.vm.name", "Dalvik");
      System.setProperty("java.runtime.name", "Android Runtime");
      check(AndroidSupport.IS_ANDROID, "AndroidSupport should detect Dalvik runtime");

      ObjectCreator<AndroidPrivateNoArg> creator =
          ObjectCreators.getObjectCreator(AndroidPrivateNoArg.class);
      AndroidPrivateNoArg instance = creator.newInstance();
      check(instance.value == 7, "Android reflective constructor should initialize fields");

      ObjectCreator<AndroidNoNoArg> unsupported =
          ObjectCreators.getObjectCreator(AndroidNoNoArg.class);
      try {
        unsupported.newInstance();
        throw new AssertionError("Android creator without no-arg constructor should fail");
      } catch (ForyException expected) {
        check(
            expected.getMessage().contains("without an accessible no-arg constructor"),
            "Unexpected message: " + expected.getMessage());
      }
    }

    private static void check(boolean value, String message) {
      if (!value) {
        throw new AssertionError(message);
      }
    }
  }

  private static final class AndroidPrivateNoArg {
    private final int value = 7;

    private AndroidPrivateNoArg() {}
  }

  private static final class AndroidNoNoArg {
    @SuppressWarnings("unused")
    private final int value;

    private AndroidNoNoArg(int value) {
      this.value = value;
    }
  }
}
