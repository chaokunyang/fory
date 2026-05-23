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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.reflect.FieldAccessor.GeneratedAccessor;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FieldAccessorTest {
  @AllArgsConstructor
  private static final class TestStruct {
    private int f1;
    private boolean f2;
    private String f3;
  }

  @Test
  public void testGeneratedAccessor() throws Exception {
    TestStruct struct = new TestStruct(10, true, "str");
    GeneratedAccessor f1 = new GeneratedAccessor(TestStruct.class.getDeclaredField("f1"));
    Assert.assertEquals(f1.get(struct), 10);
    f1.set(struct, 20);
    Assert.assertEquals(f1.get(struct), 20);
    Assert.assertEquals(f1.getInt(struct), 20);
    f1.putInt(struct, 30);
    Assert.assertEquals(f1.getInt(struct), 30);
    GeneratedAccessor f2 = new GeneratedAccessor(TestStruct.class.getDeclaredField("f2"));
    Assert.assertEquals(f2.get(struct), true);
    f2.set(struct, false);
    Assert.assertEquals(f2.get(struct), false);
    Assert.assertFalse(f2.getBoolean(struct));
    f2.putBoolean(struct, true);
    Assert.assertTrue(f2.getBoolean(struct));
    GeneratedAccessor f3 = new GeneratedAccessor(TestStruct.class.getDeclaredField("f3"));
    Assert.assertEquals(f3.get(struct), "str");
    f3.set(struct, "a");
    Assert.assertEquals(f3.get(struct), "a");
    Assert.assertEquals(f3.getObject(struct), "a");
    f3.putObject(struct, "b");
    Assert.assertEquals(f3.getObject(struct), "b");
  }

  @Test
  public void testAndroidReflectionFieldAccessorPaths() throws Exception {
    String javaBin =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    Process process =
        new ProcessBuilder(
                javaBin,
                "-cp",
                System.getProperty("java.class.path"),
                AndroidReflectionFieldAccessorProbe.class.getName())
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

  public static final class AndroidReflectionFieldAccessorProbe {
    public static void main(String[] args) throws Exception {
      System.setProperty("java.vm.name", "Dalvik");
      System.setProperty("java.runtime.name", "Android Runtime");
      check(AndroidSupport.IS_ANDROID, "AndroidSupport should detect Dalvik runtime");

      AndroidFields fields = new AndroidFields();
      assertAccessor("boolValue", fields, true, false);
      assertAccessor("byteValue", fields, (byte) 1, (byte) 2);
      assertAccessor("charValue", fields, 'a', 'z');
      assertAccessor("shortValue", fields, (short) 3, (short) 4);
      assertAccessor("intValue", fields, 5, 6);
      assertAccessor("longValue", fields, 7L, 8L);
      assertAccessor("floatValue", fields, 1.25f, 2.5f);
      assertAccessor("doubleValue", fields, 3.5d, 4.5d);
      assertAccessor("objectValue", fields, "before", "after");
    }

    private static void assertAccessor(
        String fieldName, AndroidFields fields, Object expected, Object replacement)
        throws Exception {
      Field field = AndroidFields.class.getDeclaredField(fieldName);
      FieldAccessor accessor = FieldAccessor.createAccessor(field);
      check(
          accessor instanceof ReflectionFieldAccessor, "Expected reflection accessor for " + field);
      checkEquals(accessor.get(fields), expected, "initial " + fieldName);
      accessor.set(fields, replacement);
      checkEquals(accessor.get(fields), replacement, "updated " + fieldName);
    }

    private static void check(boolean value, String message) {
      if (!value) {
        throw new AssertionError(message);
      }
    }

    private static void checkEquals(Object actual, Object expected, String message) {
      if (!expected.equals(actual)) {
        throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
      }
    }
  }

  private static final class AndroidFields {
    private boolean boolValue = true;
    private byte byteValue = 1;
    private char charValue = 'a';
    private short shortValue = 3;
    private int intValue = 5;
    private long longValue = 7;
    private float floatValue = 1.25f;
    private double doubleValue = 3.5d;
    private Object objectValue = "before";
  }
}
