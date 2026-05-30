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

package org.apache.fory.util;

/** Utility methods for optional primitive array compression. */
public final class ArrayCompressionUtils {
  static final int MIN_COMPRESSION_SIZE = 1 << 9;
  private static final CompressionSupport SCALAR_SUPPORT = new ScalarCompressionSupport();
  private static final CompressionSupport COMPRESSION_SUPPORT = loadCompressionSupport();

  private ArrayCompressionUtils() {}

  public static PrimitiveArrayCompressionType determineIntCompressionType(int[] array) {
    if (array == null) {
      throw new NullPointerException("Input array cannot be null");
    }
    if (array.length < MIN_COMPRESSION_SIZE) {
      return PrimitiveArrayCompressionType.NONE;
    }
    return COMPRESSION_SUPPORT.determineIntCompressionType(array);
  }

  public static PrimitiveArrayCompressionType determineLongCompressionType(long[] array) {
    if (array == null) {
      throw new NullPointerException("Input array cannot be null");
    }
    if (array.length < MIN_COMPRESSION_SIZE) {
      return PrimitiveArrayCompressionType.NONE;
    }
    return COMPRESSION_SUPPORT.determineLongCompressionType(array);
  }

  public static byte[] compressToBytes(int[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    byte[] compressed = new byte[array.length];
    for (int i = 0; i < array.length; i++) {
      compressed[i] = (byte) array[i];
    }
    return compressed;
  }

  public static short[] compressToShorts(int[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    short[] compressed = new short[array.length];
    for (int i = 0; i < array.length; i++) {
      compressed[i] = (short) array[i];
    }
    return compressed;
  }

  public static int[] compressToInts(long[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    int[] compressed = new int[array.length];
    for (int i = 0; i < array.length; i++) {
      compressed[i] = (int) array[i];
    }
    return compressed;
  }

  public static int[] decompressFromBytes(byte[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    int[] decompressed = new int[array.length];
    for (int i = 0; i < array.length; i++) {
      decompressed[i] = array[i];
    }
    return decompressed;
  }

  public static int[] decompressFromShorts(short[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    int[] decompressed = new int[array.length];
    for (int i = 0; i < array.length; i++) {
      decompressed[i] = array[i];
    }
    return decompressed;
  }

  public static long[] decompressFromInts(int[] array) {
    if (array == null) {
      throw new NullPointerException("Array cannot be null");
    }
    long[] decompressed = new long[array.length];
    for (int i = 0; i < array.length; i++) {
      decompressed[i] = array[i];
    }
    return decompressed;
  }

  static boolean isVectorCompressionEnabled() {
    return COMPRESSION_SUPPORT != SCALAR_SUPPORT;
  }

  interface CompressionSupport {
    PrimitiveArrayCompressionType determineIntCompressionType(int[] array);

    PrimitiveArrayCompressionType determineLongCompressionType(long[] array);
  }

  private static CompressionSupport loadCompressionSupport() {
    try {
      // The Vector implementation lives in the Java 16 multi-release tree and links to the
      // optional incubator module. Missing classes or read edges must fall back to scalar code so
      // minimal jlink images can start without resolving jdk.incubator.vector.
      Class<?> cls =
          Class.forName(
              "org.apache.fory.util.VectorArrayCompression",
              true,
              ArrayCompressionUtils.class.getClassLoader());
      return (CompressionSupport) cls.getDeclaredConstructor().newInstance();
    } catch (ClassCastException | LinkageError | ReflectiveOperationException e) {
      return SCALAR_SUPPORT;
    }
  }

  private static final class ScalarCompressionSupport implements CompressionSupport {
    @Override
    public PrimitiveArrayCompressionType determineIntCompressionType(int[] array) {
      boolean canCompressToByte = true;
      boolean canCompressToShort = true;
      for (int value : array) {
        if (canCompressToByte && (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)) {
          canCompressToByte = false;
        }
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
          canCompressToShort = false;
        }
        if (!canCompressToByte && !canCompressToShort) {
          return PrimitiveArrayCompressionType.NONE;
        }
      }
      if (canCompressToByte) {
        return PrimitiveArrayCompressionType.INT_TO_BYTE;
      }
      return canCompressToShort
          ? PrimitiveArrayCompressionType.INT_TO_SHORT
          : PrimitiveArrayCompressionType.NONE;
    }

    @Override
    public PrimitiveArrayCompressionType determineLongCompressionType(long[] array) {
      for (long value : array) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
          return PrimitiveArrayCompressionType.NONE;
        }
      }
      return PrimitiveArrayCompressionType.LONG_TO_INT;
    }
  }
}
