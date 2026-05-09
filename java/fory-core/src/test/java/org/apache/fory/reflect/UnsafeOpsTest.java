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

import static org.testng.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.fory.platform.UnsafeOps;
import org.testng.annotations.Test;

public class UnsafeOpsTest {
  @Test
  public void testArrayEquals() {
    byte[] bytes = "123456781234567".getBytes(StandardCharsets.UTF_8);
    byte[] bytes2 = "123456781234567".getBytes(StandardCharsets.UTF_8);
    assert bytes.length == bytes2.length;
    assertTrue(
        UnsafeOps.arrayEquals(
            bytes, UnsafeOps.BYTE_ARRAY_OFFSET, bytes2, UnsafeOps.BYTE_ARRAY_OFFSET, bytes.length));
  }

  @Test(enabled = false)
  public void benchmarkArrayEquals() {
    byte[] bytes = "123456781234567".getBytes(StandardCharsets.UTF_8);
    byte[] bytes2 = "123456781234567".getBytes(StandardCharsets.UTF_8);
    arrayEquals(bytes, bytes2);
    bytes = "1234567812345678".getBytes(StandardCharsets.UTF_8);
    bytes2 = "1234567812345678".getBytes(StandardCharsets.UTF_8);
    arrayEquals(bytes, bytes2);
  }

  private boolean arrayEquals(byte[] bytes, byte[] bytes2) {
    long nums = 200_000_000;
    boolean eq = false;
    {
      // warm
      for (int i = 0; i < nums; i++) {
        eq =
            bytes.length == bytes2.length
                && UnsafeOps.arrayEquals(
                    bytes,
                    UnsafeOps.BYTE_ARRAY_OFFSET,
                    bytes2,
                    UnsafeOps.BYTE_ARRAY_OFFSET,
                    bytes.length);
      }
      long t = System.nanoTime();
      for (int i = 0; i < nums; i++) {
        eq =
            bytes.length == bytes2.length
                && UnsafeOps.arrayEquals(
                    bytes,
                    UnsafeOps.BYTE_ARRAY_OFFSET,
                    bytes2,
                    UnsafeOps.BYTE_ARRAY_OFFSET,
                    bytes.length);
      }
      long duration = System.nanoTime() - t;
      System.out.format("native cost %sns %sms\n", duration, duration / 1000_000);
    }
    {
      // warm
      for (int i = 0; i < nums; i++) {
        eq = Arrays.equals(bytes, bytes2);
      }
      long t = System.nanoTime();
      for (int i = 0; i < nums; i++) {
        eq = Arrays.equals(bytes, bytes2);
      }
      long duration = System.nanoTime() - t;
      System.out.format("Arrays.equals cost %sns %sms\n", duration, duration / 1000_000);
    }
    return eq;
  }
}
