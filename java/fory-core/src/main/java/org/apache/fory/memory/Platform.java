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

package org.apache.fory.memory;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.UnsafeOps;
import sun.misc.Unsafe;

/**
 * Compatibility entry point for generated serializers whose source must stay aligned with
 * apache/main.
 */
@SuppressWarnings("restriction")
public final class Platform {
  public static final Unsafe UNSAFE = UnsafeOps.UNSAFE;

  public static final int JAVA_VERSION = JdkVersion.MAJOR_VERSION;
  public static final boolean IS_LITTLE_ENDIAN = NativeByteOrder.IS_LITTLE_ENDIAN;
  public static final Class<?> HEAP_BYTE_BUFFER_CLASS = ByteBuffer.allocate(1).getClass();
  public static final Class<?> DIRECT_BYTE_BUFFER_CLASS = ByteBuffer.allocateDirect(1).getClass();
  public static final int BOOLEAN_ARRAY_OFFSET = UnsafeOps.BOOLEAN_ARRAY_OFFSET;
  public static final int BYTE_ARRAY_OFFSET = UnsafeOps.BYTE_ARRAY_OFFSET;
  public static final int CHAR_ARRAY_OFFSET = UnsafeOps.CHAR_ARRAY_OFFSET;
  public static final int SHORT_ARRAY_OFFSET = UnsafeOps.SHORT_ARRAY_OFFSET;
  public static final int INT_ARRAY_OFFSET = UnsafeOps.INT_ARRAY_OFFSET;
  public static final int LONG_ARRAY_OFFSET = UnsafeOps.LONG_ARRAY_OFFSET;
  public static final int FLOAT_ARRAY_OFFSET = UnsafeOps.FLOAT_ARRAY_OFFSET;
  public static final int DOUBLE_ARRAY_OFFSET = UnsafeOps.DOUBLE_ARRAY_OFFSET;

  private Platform() {}

  public static boolean unaligned() {
    return UnsafeOps.unaligned();
  }

  public static long objectFieldOffset(Field f) {
    return UnsafeOps.objectFieldOffset(f);
  }

  public static int getInt(Object object, long offset) {
    return UnsafeOps.getInt(object, offset);
  }

  public static void putInt(Object object, long offset, int value) {
    UnsafeOps.putInt(object, offset, value);
  }

  public static boolean getBoolean(Object object, long offset) {
    return UnsafeOps.getBoolean(object, offset);
  }

  public static void putBoolean(Object object, long offset, boolean value) {
    UnsafeOps.putBoolean(object, offset, value);
  }

  public static byte getByte(Object object, long offset) {
    return UnsafeOps.getByte(object, offset);
  }

  public static void putByte(Object object, long offset, byte value) {
    UnsafeOps.putByte(object, offset, value);
  }

  public static short getShort(Object object, long offset) {
    return UnsafeOps.getShort(object, offset);
  }

  public static void putShort(Object object, long offset, short value) {
    UnsafeOps.putShort(object, offset, value);
  }

  public static char getChar(Object obj, long offset) {
    return UnsafeOps.getChar(obj, offset);
  }

  public static void putChar(Object obj, long offset, char value) {
    UnsafeOps.putChar(obj, offset, value);
  }

  public static long getLong(Object object, long offset) {
    return UnsafeOps.getLong(object, offset);
  }

  public static void putLong(Object object, long offset, long value) {
    UnsafeOps.putLong(object, offset, value);
  }

  public static float getFloat(Object object, long offset) {
    return UnsafeOps.getFloat(object, offset);
  }

  public static void putFloat(Object object, long offset, float value) {
    UnsafeOps.putFloat(object, offset, value);
  }

  public static double getDouble(Object object, long offset) {
    return UnsafeOps.getDouble(object, offset);
  }

  public static void putDouble(Object object, long offset, double value) {
    UnsafeOps.putDouble(object, offset, value);
  }

  public static Object getObject(Object o, long offset) {
    return UnsafeOps.getObject(o, offset);
  }

  public static void putObject(Object object, long offset, Object value) {
    UnsafeOps.putObject(object, offset, value);
  }

  public static Object getObjectVolatile(Object object, long offset) {
    return UnsafeOps.getObjectVolatile(object, offset);
  }

  public static void putObjectVolatile(Object object, long offset, Object value) {
    UnsafeOps.putObjectVolatile(object, offset, value);
  }

  public static long allocateMemory(long size) {
    return UnsafeOps.allocateMemory(size);
  }

  public static void freeMemory(long address) {
    UnsafeOps.freeMemory(address);
  }

  public static long reallocateMemory(long address, long oldSize, long newSize) {
    return UnsafeOps.reallocateMemory(address, oldSize, newSize);
  }

  public static void setMemory(Object object, long offset, long size, byte value) {
    UnsafeOps.setMemory(object, offset, size, value);
  }

  public static void setMemory(long address, byte value, long size) {
    UnsafeOps.setMemory(address, value, size);
  }

  public static void copyMemory(
      Object src, long srcOffset, Object dst, long dstOffset, long length) {
    UnsafeOps.copyMemory(src, srcOffset, dst, dstOffset, length);
  }

  public static Object[] copyObjectArray(Object[] arr) {
    return UnsafeOps.copyObjectArray(arr);
  }

  public static boolean arrayEquals(
      Object leftBase, long leftOffset, Object rightBase, long rightOffset, long length) {
    return UnsafeOps.arrayEquals(leftBase, leftOffset, rightBase, rightOffset, length);
  }

  /** Raises an exception bypassing compiler checks for checked exceptions. */
  public static void throwException(Throwable t) {
    UNSAFE.throwException(t);
  }

  /** Create an instance of <code>type</code>. This method doesn't call constructor. */
  public static <T> T newInstance(Class<T> type) {
    return UnsafeOps.newInstance(type);
  }
}
