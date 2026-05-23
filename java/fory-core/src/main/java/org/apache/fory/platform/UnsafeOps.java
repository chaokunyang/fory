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

package org.apache.fory.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.fory.annotation.Internal;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.unsafe._JDKAccess;
import sun.misc.Unsafe;

// Derived from Apache Spark's unsafe memory utility.

/** A utility class for unsafe memory operations. */
@Internal
@SuppressWarnings("restriction")
public final class UnsafeOps {
  @SuppressWarnings("restriction")
  public static final Unsafe UNSAFE = _JDKAccess.UNSAFE;

  public static final int BOOLEAN_ARRAY_OFFSET;
  public static final int BYTE_ARRAY_OFFSET;
  public static final int CHAR_ARRAY_OFFSET;
  public static final int SHORT_ARRAY_OFFSET;
  public static final int INT_ARRAY_OFFSET;
  public static final int LONG_ARRAY_OFFSET;
  public static final int FLOAT_ARRAY_OFFSET;
  public static final int DOUBLE_ARRAY_OFFSET;
  private static final boolean unaligned;

  /**
   * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to allow
   * safepoint polling during a large copy.
   */
  private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

  private UnsafeOps() {}

  static {
    BOOLEAN_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(boolean[].class);
    BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    CHAR_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(char[].class);
    SHORT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
    INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
    LONG_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(long[].class);
    FLOAT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(float[].class);
    DOUBLE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(double[].class);
  }

  // This requires `JdkVersion.MAJOR_VERSION` and `_UNSAFE`.
  static {
    boolean unalign;
    String arch = System.getProperty("os.arch", "");
    if ("ppc64le".equals(arch) || "ppc64".equals(arch) || "s390x".equals(arch)) {
      // Since java.nio.Bits.unaligned() doesn't return true on ppc (See JDK-8165231), but
      // ppc64 and ppc64le support it
      unalign = true;
    } else {
      try {
        Class<?> bitsClass =
            Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
        if (JdkVersion.MAJOR_VERSION >= 9) {
          // Java 9/10 and 11/12 have different field names.
          Field unalignedField =
              bitsClass.getDeclaredField(
                  JdkVersion.MAJOR_VERSION >= 11 ? "UNALIGNED" : "unaligned");
          unalign =
              UNSAFE.getBoolean(
                  UNSAFE.staticFieldBase(unalignedField), UNSAFE.staticFieldOffset(unalignedField));
        } else {
          Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
          unalignedMethod.setAccessible(true);
          unalign = Boolean.TRUE.equals(unalignedMethod.invoke(null));
        }
      } catch (Throwable t) {
        // We at least know x86 and x64 support unaligned access.
        //noinspection DynamicRegexReplaceableByCompiledPattern
        unalign = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64|aarch64)$");
      }
    }
    unaligned = unalign;
  }

  /**
   * Returns true when running JVM is having sun's Unsafe package available in it and underlying
   * system having unaligned-access capability.
   */
  public static boolean unaligned() {
    return unaligned;
  }

  public static long objectFieldOffset(Field f) {
    return UNSAFE.objectFieldOffset(f);
  }

  public static int getInt(Object object, long offset) {
    return UNSAFE.getInt(object, offset);
  }

  public static void putInt(Object object, long offset, int value) {
    UNSAFE.putInt(object, offset, value);
  }

  public static boolean getBoolean(Object object, long offset) {
    return UNSAFE.getBoolean(object, offset);
  }

  public static void putBoolean(Object object, long offset, boolean value) {
    UNSAFE.putBoolean(object, offset, value);
  }

  public static byte getByte(Object object, long offset) {
    return UNSAFE.getByte(object, offset);
  }

  public static void putByte(Object object, long offset, byte value) {
    UNSAFE.putByte(object, offset, value);
  }

  public static short getShort(Object object, long offset) {
    return UNSAFE.getShort(object, offset);
  }

  public static void putShort(Object object, long offset, short value) {
    UNSAFE.putShort(object, offset, value);
  }

  public static char getChar(Object obj, long offset) {
    return UnsafeOps.UNSAFE.getChar(obj, offset);
  }

  public static void putChar(Object obj, long offset, char value) {
    UnsafeOps.UNSAFE.putChar(obj, offset, value);
  }

  public static long getLong(Object object, long offset) {
    return UNSAFE.getLong(object, offset);
  }

  public static void putLong(Object object, long offset, long value) {
    UNSAFE.putLong(object, offset, value);
  }

  public static float getFloat(Object object, long offset) {
    return UNSAFE.getFloat(object, offset);
  }

  public static void putFloat(Object object, long offset, float value) {
    UNSAFE.putFloat(object, offset, value);
  }

  public static double getDouble(Object object, long offset) {
    return UNSAFE.getDouble(object, offset);
  }

  public static void putDouble(Object object, long offset, double value) {
    UNSAFE.putDouble(object, offset, value);
  }

  public static Object getObject(Object o, long offset) {
    return UNSAFE.getObject(o, offset);
  }

  public static void putObject(Object object, long offset, Object value) {
    UNSAFE.putObject(object, offset, value);
  }

  public static void copyMemory(
      Object src, long srcOffset, Object dst, long dstOffset, long length) {
    if (length < UNSAFE_COPY_THRESHOLD) {
      UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, length);
    } else {
      while (length > 0) {
        long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
        UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
        length -= size;
        srcOffset += size;
        dstOffset += size;
      }
    }
  }

  /** Create an instance of <code>type</code>. This method don't call constructor. */
  public static <T> T newInstance(Class<T> type) {
    try {
      return type.cast(UNSAFE.allocateInstance(type));
    } catch (InstantiationException e) {
      ExceptionUtils.throwException(e);
    }
    throw new IllegalStateException("unreachable");
  }
}
