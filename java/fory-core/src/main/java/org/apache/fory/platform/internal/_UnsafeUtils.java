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

import static org.apache.fory.platform.JdkVersion.JDK8_ARM;

import java.lang.reflect.Field;
import org.apache.fory.memory.NativeByteOrder;
import sun.misc.Unsafe;

/** Root-runtime owner for {@link Unsafe}. Java25+ code must use overlay classes instead. */
// CHECKSTYLE.OFF:TypeName
public final class _UnsafeUtils {
  // CHECKSTYLE.ON:TypeName
  public static final Unsafe UNSAFE;

  static {
    try {
      Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      UNSAFE = (Unsafe) unsafeField.get(null);
    } catch (Throwable cause) {
      throw new UnsupportedOperationException("Unsafe is not supported in this platform.");
    }
  }

  private _UnsafeUtils() {}

  // Oracle JDK 8 ARM C2 can replace an address scale with the load/store width, e.g. turn
  // charIndex * 2 into charIndex * 8 for getLong(char[], offset). A wrapper around the same
  // wide Unsafe operation is insufficient: inlining recreates the faulty address expression.
  // The separate methods below share each computed address between two narrower accesses so C2
  // materializes the address instead of folding its scale into a single mismatched instruction.

  /** Reads a native-order char at an unchecked object or native-memory byte offset. */
  public static char getChar(Object base, long offset) {
    if (JDK8_ARM) {
      return (char) getShortBytes(base, offset);
    }
    return UNSAFE.getChar(base, offset);
  }

  /** Reads a native-order short at an unchecked object or native-memory byte offset. */
  public static short getShort(Object base, long offset) {
    if (JDK8_ARM) {
      return getShortBytes(base, offset);
    }
    return UNSAFE.getShort(base, offset);
  }

  /** Reads a native-order int at an unchecked object or native-memory byte offset. */
  public static int getInt(Object base, long offset) {
    if (JDK8_ARM) {
      return getIntFromShorts(base, offset);
    }
    return UNSAFE.getInt(base, offset);
  }

  /** Reads a native-order long at an unchecked object or native-memory byte offset. */
  public static long getLong(Object base, long offset) {
    if (JDK8_ARM) {
      return getLongFromInts(base, offset);
    }
    return UNSAFE.getLong(base, offset);
  }

  /** Writes a native-order char at an unchecked object or native-memory byte offset. */
  public static void putChar(Object base, long offset, char value) {
    if (JDK8_ARM) {
      putShortBytes(base, offset, (short) value);
    } else {
      UNSAFE.putChar(base, offset, value);
    }
  }

  /** Writes a native-order short at an unchecked object or native-memory byte offset. */
  public static void putShort(Object base, long offset, short value) {
    if (JDK8_ARM) {
      putShortBytes(base, offset, value);
    } else {
      UNSAFE.putShort(base, offset, value);
    }
  }

  /** Writes a native-order int at an unchecked object or native-memory byte offset. */
  public static void putInt(Object base, long offset, int value) {
    if (JDK8_ARM) {
      putIntAsShorts(base, offset, value);
    } else {
      UNSAFE.putInt(base, offset, value);
    }
  }

  /** Writes a native-order long at an unchecked object or native-memory byte offset. */
  public static void putLong(Object base, long offset, long value) {
    if (JDK8_ARM) {
      putLongAsInts(base, offset, value);
    } else {
      UNSAFE.putLong(base, offset, value);
    }
  }

  /** Writes a native-order float at an unchecked object or native-memory byte offset. */
  public static void putFloat(Object base, long offset, float value) {
    if (JDK8_ARM) {
      putIntAsShorts(base, offset, Float.floatToRawIntBits(value));
    } else {
      UNSAFE.putFloat(base, offset, value);
    }
  }

  /** Writes a native-order double at an unchecked object or native-memory byte offset. */
  public static void putDouble(Object base, long offset, double value) {
    if (JDK8_ARM) {
      putLongAsInts(base, offset, Double.doubleToRawLongBits(value));
    } else {
      UNSAFE.putDouble(base, offset, value);
    }
  }

  private static short getShortBytes(Object base, long offset) {
    int value = (UNSAFE.getByte(base, offset) & 0xff) | (UNSAFE.getByte(base, offset + 1) << 8);
    return NativeByteOrder.IS_LITTLE_ENDIAN ? (short) value : Short.reverseBytes((short) value);
  }

  private static int getIntFromShorts(Object base, long offset) {
    int first = UNSAFE.getShort(base, offset) & 0xffff;
    int second = UNSAFE.getShort(base, offset + 2) & 0xffff;
    return NativeByteOrder.IS_LITTLE_ENDIAN ? first | (second << 16) : (first << 16) | second;
  }

  private static long getLongFromInts(Object base, long offset) {
    long first = UNSAFE.getInt(base, offset) & 0xffffffffL;
    long second = UNSAFE.getInt(base, offset + 4) & 0xffffffffL;
    return NativeByteOrder.IS_LITTLE_ENDIAN ? first | (second << 32) : (first << 32) | second;
  }

  private static void putShortBytes(Object base, long offset, short value) {
    if (!NativeByteOrder.IS_LITTLE_ENDIAN) {
      value = Short.reverseBytes(value);
    }
    UNSAFE.putByte(base, offset, (byte) value);
    UNSAFE.putByte(base, offset + 1, (byte) (value >>> 8));
  }

  private static void putIntAsShorts(Object base, long offset, int value) {
    if (NativeByteOrder.IS_LITTLE_ENDIAN) {
      UNSAFE.putShort(base, offset, (short) value);
      UNSAFE.putShort(base, offset + 2, (short) (value >>> 16));
    } else {
      UNSAFE.putShort(base, offset, (short) (value >>> 16));
      UNSAFE.putShort(base, offset + 2, (short) value);
    }
  }

  private static void putLongAsInts(Object base, long offset, long value) {
    if (NativeByteOrder.IS_LITTLE_ENDIAN) {
      UNSAFE.putInt(base, offset, (int) value);
      UNSAFE.putInt(base, offset + 4, (int) (value >>> 32));
    } else {
      UNSAFE.putInt(base, offset, (int) (value >>> 32));
      UNSAFE.putInt(base, offset + 4, (int) value);
    }
  }
}
