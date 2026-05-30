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

package org.apache.fory.serializer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import org.testng.annotations.Test;

public class ArrayCompressionUtilsTest {
  @Test
  public void testIntArrayCompressionDetection() {
    // Test byte range compression - make array size >= 512
    int[] byteRangeArray = new int[1024];
    for (int i = 0; i < 1024; i++) {
      byteRangeArray[i] = (i % 256) - 128; // byte range values
    }
    assertEquals(determineIntCompressionType(byteRangeArray), compressionType("INT_TO_BYTE"));

    // Test short range compression - make array size >= 512
    int[] shortRangeArray = new int[1024];
    for (int i = 0; i < 1024; i++) {
      shortRangeArray[i] = (i % 65536) - 32768; // short range values
    }
    assertEquals(determineIntCompressionType(shortRangeArray), compressionType("INT_TO_SHORT"));

    // Test no compression for large values - make array size >= 512
    int[] largeArray = new int[1024];
    for (int i = 0; i < 1024; i++) {
      largeArray[i] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    assertEquals(determineIntCompressionType(largeArray), compressionType("NONE"));

    // Test small arrays don't compress
    int[] smallArray = {1, 2, 3};
    assertEquals(determineIntCompressionType(smallArray), compressionType("NONE"));
  }

  @Test
  public void testLongArrayCompressionDetection() {
    // Test int range compression - make array size >= 512
    long[] intRangeArray = new long[1024];
    for (int i = 0; i < 1024; i++) {
      intRangeArray[i] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    assertEquals(determineLongCompressionType(intRangeArray), compressionType("LONG_TO_INT"));

    // Test no compression for large values - make array size >= 512
    long[] largeArray = new long[1024];
    for (int i = 0; i < 1024; i++) {
      largeArray[i] = i % 2 == 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
    assertEquals(determineLongCompressionType(largeArray), compressionType("NONE"));

    // Test small arrays don't compress
    long[] smallArray = {1L, 2L, 3L};
    assertEquals(determineLongCompressionType(smallArray), compressionType("NONE"));
  }

  @Test
  public void testCompressionRoundTrip() {
    // Test int to byte compression - use arrays >= 512 for proper testing
    int[] originalInts = new int[1024];
    for (int i = 0; i < 1024; i++) {
      originalInts[i] = (i % 201) - 100; // byte range values
    }
    byte[] compressed = (byte[]) invokeUtils("compressToBytes", int[].class, originalInts);
    int[] decompressed = (int[]) invokeUtils("decompressFromBytes", byte[].class, compressed);
    assertEquals(decompressed, originalInts);

    // Test int to short compression
    int[] originalShorts = new int[1024];
    for (int i = 0; i < 1024; i++) {
      originalShorts[i] = (i % 102401) - 30000; // short range values
    }
    short[] compressedShorts =
        (short[]) invokeUtils("compressToShorts", int[].class, originalShorts);
    int[] decompressedShorts =
        (int[]) invokeUtils("decompressFromShorts", short[].class, compressedShorts);
    assertEquals(decompressedShorts, originalShorts);

    // Test long to int compression
    long[] originalLongs = new long[1024];
    for (int i = 0; i < 1024; i++) {
      originalLongs[i] = i % 2 == 0 ? -2000000000L : 2000000000L; // int range values
    }
    int[] compressedInts = (int[]) invokeUtils("compressToInts", long[].class, originalLongs);
    long[] decompressedLongs =
        (long[]) invokeUtils("decompressFromInts", int[].class, compressedInts);
    assertEquals(decompressedLongs, originalLongs);
  }

  @Test
  public void testLargeArraysWithSIMD() {
    Random random = new Random(42);

    // Test large array that compresses to bytes
    int[] largeByteArray = new int[10000];
    for (int i = 0; i < largeByteArray.length; i++) {
      largeByteArray[i] = random.nextInt(256) - 128; // byte range
    }
    assertEquals(determineIntCompressionType(largeByteArray), compressionType("INT_TO_BYTE"));

    // Test large array that compresses to shorts
    int[] largeShortArray = new int[10000];
    for (int i = 0; i < largeShortArray.length; i++) {
      largeShortArray[i] = random.nextInt(65536) - 32768; // short range
    }
    assertEquals(determineIntCompressionType(largeShortArray), compressionType("INT_TO_SHORT"));

    // Test large array that doesn't compress
    int[] largeArray = new int[10000];
    for (int i = 0; i < largeArray.length; i++) {
      largeArray[i] = random.nextInt();
    }
    assertEquals(determineIntCompressionType(largeArray), compressionType("NONE"));
  }

  @Test
  public void testLargeLongArraysWithSIMD() {
    Random random = new Random(42);

    // Test large array that compresses to ints
    long[] largeIntArray = new long[10000];
    for (int i = 0; i < largeIntArray.length; i++) {
      largeIntArray[i] = random.nextInt(); // int range
    }
    assertEquals(determineLongCompressionType(largeIntArray), compressionType("LONG_TO_INT"));

    // Test large array that doesn't compress
    long[] largeArray = new long[10000];
    for (int i = 0; i < largeArray.length; i++) {
      largeArray[i] = random.nextLong();
    }
    assertEquals(determineLongCompressionType(largeArray), compressionType("NONE"));
  }

  @Test
  public void testEdgeCases() {
    // Test empty arrays
    int[] emptyIntArray = {};
    assertEquals(determineIntCompressionType(emptyIntArray), compressionType("NONE"));

    long[] emptyLongArray = {};
    assertEquals(determineLongCompressionType(emptyLongArray), compressionType("NONE"));

    // Test boundary values - use arrays >= 512
    int[] byteBoundary = new int[1024];
    for (int i = 0; i < 1024; i++) {
      byteBoundary[i] = i % 2 == 0 ? Byte.MIN_VALUE : Byte.MAX_VALUE;
    }
    assertEquals(determineIntCompressionType(byteBoundary), compressionType("INT_TO_BYTE"));

    int[] shortBoundary = new int[1024];
    for (int i = 0; i < 1024; i++) {
      shortBoundary[i] = i % 2 == 0 ? Short.MIN_VALUE : Short.MAX_VALUE;
    }
    assertEquals(determineIntCompressionType(shortBoundary), compressionType("INT_TO_SHORT"));

    long[] intBoundary = new long[1024];
    for (int i = 0; i < 1024; i++) {
      intBoundary[i] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    assertEquals(determineLongCompressionType(intBoundary), compressionType("LONG_TO_INT"));

    // Test just outside boundaries
    int[] outsideByte = new int[1024];
    for (int i = 0; i < 1024; i++) {
      outsideByte[i] = i % 2 == 0 ? Byte.MIN_VALUE - 1 : Byte.MAX_VALUE + 1;
    }
    assertEquals(determineIntCompressionType(outsideByte), compressionType("INT_TO_SHORT"));

    int[] outsideShort = new int[1024];
    for (int i = 0; i < 1024; i++) {
      outsideShort[i] = i % 2 == 0 ? Short.MIN_VALUE - 1 : Short.MAX_VALUE + 1;
    }
    assertEquals(determineIntCompressionType(outsideShort), compressionType("NONE"));

    long[] outsideInt = new long[1024];
    for (int i = 0; i < 1024; i++) {
      outsideInt[i] = i % 2 == 0 ? Integer.MIN_VALUE - 1L : Integer.MAX_VALUE + 1L;
    }
    assertEquals(determineLongCompressionType(outsideInt), compressionType("NONE"));
  }

  @Test
  public void testCompressionEffectiveness() {
    // Test compression size reduction
    int[] byteRangeArray = new int[1000];
    for (int i = 0; i < byteRangeArray.length; i++) {
      byteRangeArray[i] = i % 200 - 100; // byte range
    }

    byte[] compressed = (byte[]) invokeUtils("compressToBytes", int[].class, byteRangeArray);
    assertEquals(compressed.length, byteRangeArray.length); // 4x smaller
    assertTrue(compressed.length < byteRangeArray.length * 4);

    // Verify decompression
    int[] decompressed = (int[]) invokeUtils("decompressFromBytes", byte[].class, compressed);
    assertEquals(decompressed, byteRangeArray);
  }

  @Test
  public void testMixedValues() {
    // Test array with mixed compression potential
    int[] mixedArray = new int[1000];
    // First half can compress to byte
    for (int i = 0; i < 500; i++) {
      mixedArray[i] = i % 200 - 100;
    }
    // Second half requires full int
    for (int i = 500; i < 1000; i++) {
      mixedArray[i] = Integer.MAX_VALUE - i;
    }

    assertEquals(determineIntCompressionType(mixedArray), compressionType("NONE"));
  }

  private static Object determineIntCompressionType(int[] array) {
    return invokeUtils("determineIntCompressionType", int[].class, array);
  }

  private static Object determineLongCompressionType(long[] array) {
    return invokeUtils("determineLongCompressionType", long[].class, array);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object compressionType(String name) {
    return Enum.valueOf((Class<? extends Enum>) primitiveArrayCompressionTypeClass(), name);
  }

  private static Object invokeUtils(String name, Class<?> paramType, Object arg) {
    try {
      Method method = arrayCompressionUtilsClass().getMethod(name, paramType);
      return method.invoke(null, arg);
    } catch (IllegalAccessException e) {
      throw new AssertionError("Cannot access " + name, e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new AssertionError("Unexpected checked exception from " + name, cause);
    } catch (NoSuchMethodException e) {
      throw new AssertionError("Missing method " + name, e);
    }
  }

  private static Class<?> arrayCompressionUtilsClass() {
    return Java16CompressionSupport.loadClass("org.apache.fory.util.ArrayCompressionUtils");
  }

  private static Class<?> primitiveArrayCompressionTypeClass() {
    return Java16CompressionSupport.loadClass("org.apache.fory.util.PrimitiveArrayCompressionType");
  }
}
