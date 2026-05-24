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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import org.apache.fory.annotation.Internal;
import org.apache.fory.platform.internal._JDKAccess;
import sun.misc.Unsafe;

/** A utility class for array memory operations on JDK25+. */
@Internal
@SuppressWarnings("restriction")
public final class UnsafeOps {
  @SuppressWarnings("restriction")
  public static final Unsafe UNSAFE = _JDKAccess.UNSAFE;

  // JDK25 array operations use Java/VarHandle indexes instead of raw Unsafe byte offsets.
  // Keep these constants zero so versioned MemoryBuffer code can preserve the root API shape
  // without mixing Unsafe base-offset domains into the zero-Unsafe runtime.
  public static final int BOOLEAN_ARRAY_OFFSET = 0;
  public static final int BYTE_ARRAY_OFFSET = 0;
  public static final int CHAR_ARRAY_OFFSET = 0;
  public static final int SHORT_ARRAY_OFFSET = 0;
  public static final int INT_ARRAY_OFFSET = 0;
  public static final int LONG_ARRAY_OFFSET = 0;
  public static final int FLOAT_ARRAY_OFFSET = 0;
  public static final int DOUBLE_ARRAY_OFFSET = 0;
  private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
  private static final VarHandle BYTE_ARRAY_CHAR =
      MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_SHORT =
      MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_INT =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_FLOAT =
      MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_DOUBLE =
      MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.nativeOrder());
  private static final boolean unaligned;

  private UnsafeOps() {}

  static {
    String arch = System.getProperty("os.arch", "");
    if ("ppc64le".equals(arch) || "ppc64".equals(arch) || "s390x".equals(arch)) {
      unaligned = true;
    } else {
      unaligned = arch.matches("^(i[3-6]86|x86(_64)?|x64|amd64|aarch64)$");
    }
  }

  /**
   * Returns true when the underlying system is known to support unaligned access. JDK25 array
   * accessors do not use Unsafe, but callers keep this gate for vectorized array scans.
   */
  public static boolean unaligned() {
    return unaligned;
  }

  public static long objectFieldOffset(Field f) {
    throw unsupportedObjectMemory();
  }

  public static int getInt(Object object, long offset) {
    if (object instanceof byte[]) {
      return (int) BYTE_ARRAY_INT.get((byte[]) object, toIntIndex(offset));
    }
    return getIntFromArray(object, offset);
  }

  public static void putInt(Object object, long offset, int value) {
    if (object instanceof byte[]) {
      BYTE_ARRAY_INT.set((byte[]) object, toIntIndex(offset), value);
      return;
    }
    putIntToArray(object, offset, value);
  }

  public static boolean getBoolean(Object object, long offset) {
    if (object instanceof boolean[]) {
      return ((boolean[]) object)[toIntIndex(offset)];
    }
    return getByte(object, offset) != 0;
  }

  public static void putBoolean(Object object, long offset, boolean value) {
    if (object instanceof boolean[]) {
      ((boolean[]) object)[toIntIndex(offset)] = value;
      return;
    }
    putByte(object, offset, value ? (byte) 1 : (byte) 0);
  }

  public static byte getByte(Object object, long offset) {
    return getArrayByte(object, offset);
  }

  public static void putByte(Object object, long offset, byte value) {
    putArrayByte(object, offset, value);
  }

  public static short getShort(Object object, long offset) {
    if (object instanceof byte[]) {
      return (short) BYTE_ARRAY_SHORT.get((byte[]) object, toIntIndex(offset));
    }
    return (short) getIntN(object, offset, Short.BYTES);
  }

  public static void putShort(Object object, long offset, short value) {
    if (object instanceof byte[]) {
      BYTE_ARRAY_SHORT.set((byte[]) object, toIntIndex(offset), value);
      return;
    }
    putIntN(object, offset, value, Short.BYTES);
  }

  public static char getChar(Object obj, long offset) {
    if (obj instanceof byte[]) {
      return (char) BYTE_ARRAY_CHAR.get((byte[]) obj, toIntIndex(offset));
    }
    return (char) getIntN(obj, offset, Character.BYTES);
  }

  public static void putChar(Object obj, long offset, char value) {
    if (obj instanceof byte[]) {
      BYTE_ARRAY_CHAR.set((byte[]) obj, toIntIndex(offset), value);
      return;
    }
    putIntN(obj, offset, value, Character.BYTES);
  }

  public static long getLong(Object object, long offset) {
    if (object instanceof byte[]) {
      return (long) BYTE_ARRAY_LONG.get((byte[]) object, toIntIndex(offset));
    }
    long value = 0;
    if (BIG_ENDIAN) {
      for (int i = 0; i < Long.BYTES; i++) {
        value = (value << Byte.SIZE) | (getArrayByte(object, offset + i) & 0xffL);
      }
    } else {
      for (int i = Long.BYTES - 1; i >= 0; i--) {
        value = (value << Byte.SIZE) | (getArrayByte(object, offset + i) & 0xffL);
      }
    }
    return value;
  }

  public static void putLong(Object object, long offset, long value) {
    if (object instanceof byte[]) {
      BYTE_ARRAY_LONG.set((byte[]) object, toIntIndex(offset), value);
      return;
    }
    if (BIG_ENDIAN) {
      for (int i = Long.BYTES - 1; i >= 0; i--) {
        putArrayByte(object, offset + i, (byte) value);
        value >>>= Byte.SIZE;
      }
    } else {
      for (int i = 0; i < Long.BYTES; i++) {
        putArrayByte(object, offset + i, (byte) value);
        value >>>= Byte.SIZE;
      }
    }
  }

  public static float getFloat(Object object, long offset) {
    if (object instanceof byte[]) {
      return (float) BYTE_ARRAY_FLOAT.get((byte[]) object, toIntIndex(offset));
    }
    return Float.intBitsToFloat(getInt(object, offset));
  }

  public static void putFloat(Object object, long offset, float value) {
    if (object instanceof byte[]) {
      BYTE_ARRAY_FLOAT.set((byte[]) object, toIntIndex(offset), value);
      return;
    }
    putInt(object, offset, Float.floatToRawIntBits(value));
  }

  public static double getDouble(Object object, long offset) {
    if (object instanceof byte[]) {
      return (double) BYTE_ARRAY_DOUBLE.get((byte[]) object, toIntIndex(offset));
    }
    return Double.longBitsToDouble(getLong(object, offset));
  }

  public static void putDouble(Object object, long offset, double value) {
    if (object instanceof byte[]) {
      BYTE_ARRAY_DOUBLE.set((byte[]) object, toIntIndex(offset), value);
      return;
    }
    putLong(object, offset, Double.doubleToRawLongBits(value));
  }

  public static Object getObject(Object o, long offset) {
    throw unsupportedObjectMemory();
  }

  public static void putObject(Object object, long offset, Object value) {
    throw unsupportedObjectMemory();
  }

  public static void copyMemory(
      Object src, long srcOffset, Object dst, long dstOffset, long length) {
    if (src == null || dst == null) {
      throw unsupportedNativeMemory();
    }
    if (length < 0) {
      throw new IllegalArgumentException("length must be non-negative: " + length);
    }
    int len = toIntLength(length);
    if (src instanceof byte[] && dst instanceof byte[]) {
      System.arraycopy((byte[]) src, toIntIndex(srcOffset), (byte[]) dst, toIntIndex(dstOffset), len);
      return;
    }
    if (copySamePrimitiveArray(src, srcOffset, dst, dstOffset, len)) {
      return;
    }
    if (!isPrimitiveArray(src) || !isPrimitiveArray(dst)) {
      throw unsupportedObjectMemory();
    }
    if (src == dst && srcOffset < dstOffset && dstOffset < srcOffset + length) {
      for (long i = length - 1; i >= 0; i--) {
        putArrayByte(dst, dstOffset + i, getArrayByte(src, srcOffset + i));
      }
    } else {
      for (long i = 0; i < length; i++) {
        putArrayByte(dst, dstOffset + i, getArrayByte(src, srcOffset + i));
      }
    }
  }

  private static boolean copySamePrimitiveArray(
      Object src, long srcOffset, Object dst, long dstOffset, int len) {
    if (src.getClass() != dst.getClass()) {
      return false;
    }
    if (src instanceof boolean[]) {
      System.arraycopy((boolean[]) src, toIntIndex(srcOffset), (boolean[]) dst, toIntIndex(dstOffset), len);
      return true;
    } else if (src instanceof char[] && aligned(srcOffset, dstOffset, len, Character.BYTES)) {
      System.arraycopy(
          (char[]) src,
          toIntIndex(srcOffset / Character.BYTES),
          (char[]) dst,
          toIntIndex(dstOffset / Character.BYTES),
          len / Character.BYTES);
      return true;
    } else if (src instanceof short[] && aligned(srcOffset, dstOffset, len, Short.BYTES)) {
      System.arraycopy(
          (short[]) src,
          toIntIndex(srcOffset / Short.BYTES),
          (short[]) dst,
          toIntIndex(dstOffset / Short.BYTES),
          len / Short.BYTES);
      return true;
    } else if (src instanceof int[] && aligned(srcOffset, dstOffset, len, Integer.BYTES)) {
      System.arraycopy(
          (int[]) src,
          toIntIndex(srcOffset / Integer.BYTES),
          (int[]) dst,
          toIntIndex(dstOffset / Integer.BYTES),
          len / Integer.BYTES);
      return true;
    } else if (src instanceof long[] && aligned(srcOffset, dstOffset, len, Long.BYTES)) {
      System.arraycopy(
          (long[]) src,
          toIntIndex(srcOffset / Long.BYTES),
          (long[]) dst,
          toIntIndex(dstOffset / Long.BYTES),
          len / Long.BYTES);
      return true;
    } else if (src instanceof float[] && aligned(srcOffset, dstOffset, len, Float.BYTES)) {
      System.arraycopy(
          (float[]) src,
          toIntIndex(srcOffset / Float.BYTES),
          (float[]) dst,
          toIntIndex(dstOffset / Float.BYTES),
          len / Float.BYTES);
      return true;
    } else if (src instanceof double[] && aligned(srcOffset, dstOffset, len, Double.BYTES)) {
      System.arraycopy(
          (double[]) src,
          toIntIndex(srcOffset / Double.BYTES),
          (double[]) dst,
          toIntIndex(dstOffset / Double.BYTES),
          len / Double.BYTES);
      return true;
    }
    return false;
  }

  public static Object[] copyObjectArray(Object[] arr) {
    Object[] objects = new Object[arr.length];
    System.arraycopy(arr, 0, objects, 0, arr.length);
    return objects;
  }

  /** Create an instance of <code>type</code>. This method does not call constructor. */
  public static <T> T newInstance(Class<T> type) {
    throw new UnsupportedOperationException(
        "Constructor-bypassing allocation is unsupported on JDK25 without sun.misc.Unsafe; "
            + "use a constructor-based serializer path for "
            + type);
  }

  private static int getIntFromArray(Object object, long offset) {
    return getIntN(object, offset, Integer.BYTES);
  }

  private static void putIntToArray(Object object, long offset, int value) {
    putIntN(object, offset, value, Integer.BYTES);
  }

  private static int getIntN(Object object, long offset, int bytes) {
    int value = 0;
    if (BIG_ENDIAN) {
      for (int i = 0; i < bytes; i++) {
        value = (value << Byte.SIZE) | (getArrayByte(object, offset + i) & 0xff);
      }
    } else {
      for (int i = bytes - 1; i >= 0; i--) {
        value = (value << Byte.SIZE) | (getArrayByte(object, offset + i) & 0xff);
      }
    }
    return value;
  }

  private static void putIntN(Object object, long offset, int value, int bytes) {
    if (BIG_ENDIAN) {
      for (int i = bytes - 1; i >= 0; i--) {
        putArrayByte(object, offset + i, (byte) value);
        value >>>= Byte.SIZE;
      }
    } else {
      for (int i = 0; i < bytes; i++) {
        putArrayByte(object, offset + i, (byte) value);
        value >>>= Byte.SIZE;
      }
    }
  }

  private static byte getArrayByte(Object object, long offset) {
    checkOffset(offset);
    if (object instanceof byte[]) {
      return ((byte[]) object)[toIntIndex(offset)];
    } else if (object instanceof boolean[]) {
      return ((boolean[]) object)[toIntIndex(offset)] ? (byte) 1 : (byte) 0;
    } else if (object instanceof char[]) {
      return getIntByte(
          ((char[]) object)[toIntIndex(offset / Character.BYTES)], offset, Character.BYTES);
    } else if (object instanceof short[]) {
      return getIntByte(((short[]) object)[toIntIndex(offset / Short.BYTES)], offset, Short.BYTES);
    } else if (object instanceof int[]) {
      return getIntByte(
          ((int[]) object)[toIntIndex(offset / Integer.BYTES)], offset, Integer.BYTES);
    } else if (object instanceof long[]) {
      return getLongByte(((long[]) object)[toIntIndex(offset / Long.BYTES)], offset);
    } else if (object instanceof float[]) {
      int value = Float.floatToRawIntBits(((float[]) object)[toIntIndex(offset / Float.BYTES)]);
      return getIntByte(value, offset, Float.BYTES);
    } else if (object instanceof double[]) {
      long value =
          Double.doubleToRawLongBits(((double[]) object)[toIntIndex(offset / Double.BYTES)]);
      return getLongByte(value, offset);
    }
    throw unsupportedObjectMemory();
  }

  private static void putArrayByte(Object object, long offset, byte value) {
    checkOffset(offset);
    if (object instanceof byte[]) {
      ((byte[]) object)[toIntIndex(offset)] = value;
    } else if (object instanceof boolean[]) {
      ((boolean[]) object)[toIntIndex(offset)] = value != 0;
    } else if (object instanceof char[]) {
      char[] array = (char[]) object;
      int index = toIntIndex(offset / Character.BYTES);
      array[index] = (char) setIntByte(array[index], offset, value, Character.BYTES);
    } else if (object instanceof short[]) {
      short[] array = (short[]) object;
      int index = toIntIndex(offset / Short.BYTES);
      array[index] = (short) setIntByte(array[index], offset, value, Short.BYTES);
    } else if (object instanceof int[]) {
      int[] array = (int[]) object;
      int index = toIntIndex(offset / Integer.BYTES);
      array[index] = setIntByte(array[index], offset, value, Integer.BYTES);
    } else if (object instanceof long[]) {
      long[] array = (long[]) object;
      int index = toIntIndex(offset / Long.BYTES);
      array[index] = setLongByte(array[index], offset, value);
    } else if (object instanceof float[]) {
      float[] array = (float[]) object;
      int index = toIntIndex(offset / Float.BYTES);
      int bits = Float.floatToRawIntBits(array[index]);
      array[index] = Float.intBitsToFloat(setIntByte(bits, offset, value, Float.BYTES));
    } else if (object instanceof double[]) {
      double[] array = (double[]) object;
      int index = toIntIndex(offset / Double.BYTES);
      long bits = Double.doubleToRawLongBits(array[index]);
      array[index] = Double.longBitsToDouble(setLongByte(bits, offset, value));
    } else {
      throw unsupportedObjectMemory();
    }
  }

  private static byte getIntByte(int value, long offset, int width) {
    int shift = byteShift(offset, width);
    return (byte) (value >>> shift);
  }

  private static byte getLongByte(long value, long offset) {
    int shift = byteShift(offset, Long.BYTES);
    return (byte) (value >>> shift);
  }

  private static int setIntByte(int oldValue, long offset, byte value, int width) {
    int shift = byteShift(offset, width);
    int mask = 0xff << shift;
    return (oldValue & ~mask) | ((value & 0xff) << shift);
  }

  private static long setLongByte(long oldValue, long offset, byte value) {
    int shift = byteShift(offset, Long.BYTES);
    long mask = 0xffL << shift;
    return (oldValue & ~mask) | ((long) (value & 0xff) << shift);
  }

  private static int byteShift(long offset, int width) {
    int byteIndex = (int) Math.floorMod(offset, width);
    return (BIG_ENDIAN ? width - 1 - byteIndex : byteIndex) * Byte.SIZE;
  }

  private static boolean isPrimitiveArray(Object object) {
    Class<?> cls = object.getClass();
    return cls.isArray() && cls.getComponentType().isPrimitive();
  }

  private static boolean aligned(long srcOffset, long dstOffset, int len, int width) {
    return srcOffset % width == 0 && dstOffset % width == 0 && len % width == 0;
  }

  private static int toIntIndex(long offset) {
    if (offset < 0 || offset > Integer.MAX_VALUE) {
      throw new IndexOutOfBoundsException("offset out of int range: " + offset);
    }
    return (int) offset;
  }

  private static int toIntLength(long length) {
    if (length > Integer.MAX_VALUE) {
      throw new IndexOutOfBoundsException("length out of int range: " + length);
    }
    return (int) length;
  }

  private static void checkOffset(long offset) {
    if (offset < 0) {
      throw new IndexOutOfBoundsException("offset must be non-negative: " + offset);
    }
  }

  private static UnsupportedOperationException unsupportedObjectMemory() {
    return new UnsupportedOperationException(
        "Object field and reference-offset memory access is unsupported on JDK25 without "
            + "sun.misc.Unsafe");
  }

  private static UnsupportedOperationException unsupportedNativeMemory() {
    return new UnsupportedOperationException(
        "Raw native-address memory access is unsupported on JDK25 without sun.misc.Unsafe");
  }
}
