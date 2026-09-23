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

package org.apache.fory.platform.internal;

import static org.testng.Assert.assertEquals;

import org.apache.fory.memory.NativeByteOrder;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.JdkVersion;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class UnsafeUtilsTest {
  private static final long BYTE_ARRAY_OFFSET =
      JdkVersion.MAJOR_VERSION < 25 && !AndroidSupport.IS_ANDROID
          ? _UnsafeUtils.UNSAFE.arrayBaseOffset(byte[].class)
          : 0;

  private static void requireUnsafe() {
    if (JdkVersion.MAJOR_VERSION >= 25 || AndroidSupport.IS_ANDROID) {
      throw new SkipException("Indexed Unsafe access is only used by the JDK 8-24 runtime");
    }
  }

  @Test
  public void testScaledReads() {
    requireUnsafe();
    byte[] bytes = new byte[1024];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) (i * 37 + 137);
    }
    long[][] expected = new long[3][32];
    for (int i = 0; i < 32; i++) {
      expected[0][i] = word(bytes, (i << 2), 2);
      expected[1][i] = word(bytes, (i << 1), 4);
      expected[2][i] = word(bytes, (i << 1), 8);
    }
    // Cross the C2 compilation threshold and vary nonzero indices. Index zero hides the bug.
    for (int call = 0; call < 100_000; call++) {
      int index = call & 31;
      assertEquals(readChar(bytes, index), (char) expected[0][index]);
      assertEquals(readShort(bytes, index), (short) expected[0][index]);
      assertEquals(readInt(bytes, index), (int) expected[1][index]);
      assertEquals(readLong(bytes, index), expected[2][index]);
      assertEquals((int) readLong(bytes, index), (int) expected[2][index]);
    }
  }

  @Test
  public void testScaledWrites() {
    requireUnsafe();
    byte[] bytes = new byte[1024];
    for (int call = 0; call < 100_000; call++) {
      int index = call & 31;
      long value = 0xfedcba9876543210L ^ ((long) call << 29);
      writeChar(bytes, index, (char) value);
      assertEquals(word(bytes, (index << 2), 2), value & 0xffff);
      writeShort(bytes, index, (short) value);
      assertEquals(word(bytes, (index << 2), 2), value & 0xffff);
      writeInt(bytes, index, (int) value);
      assertEquals(word(bytes, (index << 1), 4), value & 0xffffffffL);
      writeLong(bytes, index, value);
      assertEquals(word(bytes, (index << 1), 8), value);
    }
  }

  private static long word(byte[] bytes, int index, int size) {
    long value = 0;
    for (int i = 0; i < size; i++) {
      int shift = NativeByteOrder.IS_LITTLE_ENDIAN ? i * 8 : (size - i - 1) * 8;
      value |= (bytes[index + i] & 0xffL) << shift;
    }
    return value;
  }

  private static char readChar(byte[] bytes, int index) {
    return _UnsafeUtils.getChar(bytes, BYTE_ARRAY_OFFSET + ((long) index << 2));
  }

  private static short readShort(byte[] bytes, int index) {
    return _UnsafeUtils.getShort(bytes, BYTE_ARRAY_OFFSET + ((long) index << 2));
  }

  private static int readInt(byte[] bytes, int index) {
    return _UnsafeUtils.getInt(bytes, BYTE_ARRAY_OFFSET + ((long) index << 1));
  }

  private static long readLong(byte[] bytes, int index) {
    return _UnsafeUtils.getLong(bytes, BYTE_ARRAY_OFFSET + ((long) index << 1));
  }

  private static void writeChar(byte[] bytes, int index, char value) {
    _UnsafeUtils.putChar(bytes, BYTE_ARRAY_OFFSET + ((long) index << 2), value);
  }

  private static void writeShort(byte[] bytes, int index, short value) {
    _UnsafeUtils.putShort(bytes, BYTE_ARRAY_OFFSET + ((long) index << 2), value);
  }

  private static void writeInt(byte[] bytes, int index, int value) {
    _UnsafeUtils.putInt(bytes, BYTE_ARRAY_OFFSET + ((long) index << 1), value);
  }

  private static void writeLong(byte[] bytes, int index, long value) {
    _UnsafeUtils.putLong(bytes, BYTE_ARRAY_OFFSET + ((long) index << 1), value);
  }
}
