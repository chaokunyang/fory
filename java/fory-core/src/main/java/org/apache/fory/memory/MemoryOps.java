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

/**
 * Android-safe byte-array memory operations.
 *
 * <p>All multi-byte primitive operations are little-endian. Varint helpers intentionally read and
 * write one byte at a time and must not use chunked primitive loads.
 */
final class MemoryOps {
  private MemoryOps() {}

  static boolean getBoolean(byte[] source, int index) {
    return source[index] != 0;
  }

  static void putBoolean(byte[] target, int index, boolean value) {
    target[index] = (byte) (value ? 1 : 0);
  }

  static byte getByte(byte[] source, int index) {
    return source[index];
  }

  static void putByte(byte[] target, int index, byte value) {
    target[index] = value;
  }

  static short getInt16(byte[] source, int index) {
    return (short) ((source[index] & 0xFF) | (source[index + 1] << 8));
  }

  static void putInt16(byte[] target, int index, short value) {
    target[index] = (byte) value;
    target[index + 1] = (byte) (value >>> 8);
  }

  static int getInt32(byte[] source, int index) {
    return (source[index] & 0xFF)
        | ((source[index + 1] & 0xFF) << 8)
        | ((source[index + 2] & 0xFF) << 16)
        | (source[index + 3] << 24);
  }

  static void putInt32(byte[] target, int index, int value) {
    target[index] = (byte) value;
    target[index + 1] = (byte) (value >>> 8);
    target[index + 2] = (byte) (value >>> 16);
    target[index + 3] = (byte) (value >>> 24);
  }

  static long getInt64(byte[] source, int index) {
    return ((long) source[index] & 0xFF)
        | (((long) source[index + 1] & 0xFF) << 8)
        | (((long) source[index + 2] & 0xFF) << 16)
        | (((long) source[index + 3] & 0xFF) << 24)
        | (((long) source[index + 4] & 0xFF) << 32)
        | (((long) source[index + 5] & 0xFF) << 40)
        | (((long) source[index + 6] & 0xFF) << 48)
        | ((long) source[index + 7] << 56);
  }

  static void putInt64(byte[] target, int index, long value) {
    target[index] = (byte) value;
    target[index + 1] = (byte) (value >>> 8);
    target[index + 2] = (byte) (value >>> 16);
    target[index + 3] = (byte) (value >>> 24);
    target[index + 4] = (byte) (value >>> 32);
    target[index + 5] = (byte) (value >>> 40);
    target[index + 6] = (byte) (value >>> 48);
    target[index + 7] = (byte) (value >>> 56);
  }

  static float getFloat32(byte[] source, int index) {
    return Float.intBitsToFloat(getInt32(source, index));
  }

  static void putFloat32(byte[] target, int index, float value) {
    putInt32(target, index, Float.floatToRawIntBits(value));
  }

  static double getFloat64(byte[] source, int index) {
    return Double.longBitsToDouble(getInt64(source, index));
  }

  static void putFloat64(byte[] target, int index, double value) {
    putInt64(target, index, Double.doubleToRawLongBits(value));
  }

  static void copy(byte[] source, int sourceIndex, byte[] target, int targetIndex, int length) {
    System.arraycopy(source, sourceIndex, target, targetIndex, length);
  }

  static int putVarInt32(byte[] target, int index, int value) {
    return putVarUInt32(target, index, (value << 1) ^ (value >> 31));
  }

  static int putVarUInt32(byte[] target, int index, int value) {
    int start = index;
    while ((value & 0xFFFFFF80) != 0) {
      target[index++] = (byte) ((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    target[index++] = (byte) value;
    return index - start;
  }

  static int putVarUInt32Small7(byte[] target, int index, int value) {
    return putVarUInt32(target, index, value);
  }

  static int putVarUint36Small(byte[] target, int index, long value) {
    int start = index;
    for (int i = 0; i < 4; i++) {
      if ((value >>> 7) == 0) {
        target[index++] = (byte) value;
        return index - start;
      }
      target[index++] = (byte) ((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    target[index++] = (byte) value;
    return index - start;
  }

  static int putVarInt64(byte[] target, int index, long value) {
    return putVarUInt64(target, index, (value << 1) ^ (value >> 63));
  }

  static int putVarUInt64(byte[] target, int index, long value) {
    int start = index;
    for (int i = 0; i < 8; i++) {
      if ((value >>> 7) == 0) {
        target[index++] = (byte) value;
        return index - start;
      }
      target[index++] = (byte) ((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    target[index++] = (byte) value;
    return index - start;
  }

  static int readVarInt32(byte[] source, int index) {
    int result = readVarUInt32(source, index);
    return (result >>> 1) ^ -(result & 1);
  }

  static int readVarUInt32(byte[] source, int index) {
    int result = 0;
    int shift = 0;
    for (int i = 0; i < 4; i++) {
      int b = source[index + i] & 0xFF;
      result |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    int fifthByte = source[index + 4] & 0xFF;
    if ((fifthByte & 0xF0) != 0) {
      throwMalformedVarUInt32(fifthByte);
    }
    return result | (fifthByte << 28);
  }

  static int varUInt32Bytes(byte[] source, int index) {
    for (int i = 0; i < 4; i++) {
      if ((source[index + i] & 0x80) == 0) {
        return i + 1;
      }
    }
    int fifthByte = source[index + 4] & 0xFF;
    if ((fifthByte & 0xF0) != 0) {
      throwMalformedVarUInt32(fifthByte);
    }
    return 5;
  }

  static long readVarUint36Small(byte[] source, int index) {
    long result = 0;
    int shift = 0;
    for (int i = 0; i < 4; i++) {
      int b = source[index + i] & 0xFF;
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    return result | ((long) source[index + 4] & 0xFF) << 28;
  }

  static int varUint36SmallBytes(byte[] source, int index) {
    for (int i = 0; i < 4; i++) {
      if ((source[index + i] & 0x80) == 0) {
        return i + 1;
      }
    }
    return 5;
  }

  static long readVarInt64(byte[] source, int index) {
    long result = readVarUInt64(source, index);
    return (result >>> 1) ^ -(result & 1);
  }

  static long readVarUInt64(byte[] source, int index) {
    long result = 0;
    int shift = 0;
    for (int i = 0; i < 8; i++) {
      int b = source[index + i] & 0xFF;
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    return result | ((long) source[index + 8] & 0xFF) << 56;
  }

  static int varUInt64Bytes(byte[] source, int index) {
    for (int i = 0; i < 8; i++) {
      if ((source[index + i] & 0x80) == 0) {
        return i + 1;
      }
    }
    return 9;
  }

  private static void throwMalformedVarUInt32(int fifthByte) {
    throw new IllegalArgumentException(
        "Malformed varuint32 fifth byte " + fifthByte + " exceeds 32 bits");
  }

  static void writeBooleans(
      byte[] target, int targetIndex, boolean[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = (byte) (source[sourceIndex + i] ? 1 : 0);
    }
  }

  static void readBooleans(
      byte[] source, int sourceIndex, boolean[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = source[sourceIndex + i] != 0;
    }
  }

  static void writeChars(
      byte[] target, int targetIndex, char[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putInt16(target, targetIndex + i * 2, (short) source[sourceIndex + i]);
    }
  }

  static void readChars(
      byte[] source, int sourceIndex, char[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = (char) getInt16(source, sourceIndex + i * 2);
    }
  }

  static void writeShorts(
      byte[] target, int targetIndex, short[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putInt16(target, targetIndex + i * 2, source[sourceIndex + i]);
    }
  }

  static void readShorts(
      byte[] source, int sourceIndex, short[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = getInt16(source, sourceIndex + i * 2);
    }
  }

  static void writeInts(byte[] target, int targetIndex, int[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putInt32(target, targetIndex + i * 4, source[sourceIndex + i]);
    }
  }

  static void readInts(byte[] source, int sourceIndex, int[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = getInt32(source, sourceIndex + i * 4);
    }
  }

  static void writeLongs(
      byte[] target, int targetIndex, long[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putInt64(target, targetIndex + i * 8, source[sourceIndex + i]);
    }
  }

  static void readLongs(
      byte[] source, int sourceIndex, long[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = getInt64(source, sourceIndex + i * 8);
    }
  }

  static void writeFloats(
      byte[] target, int targetIndex, float[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putFloat32(target, targetIndex + i * 4, source[sourceIndex + i]);
    }
  }

  static void readFloats(
      byte[] source, int sourceIndex, float[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = getFloat32(source, sourceIndex + i * 4);
    }
  }

  static void writeDoubles(
      byte[] target, int targetIndex, double[] source, int sourceIndex, int length) {
    for (int i = 0; i < length; i++) {
      putFloat64(target, targetIndex + i * 8, source[sourceIndex + i]);
    }
  }

  static void readDoubles(
      byte[] source, int sourceIndex, double[] target, int targetIndex, int length) {
    for (int i = 0; i < length; i++) {
      target[targetIndex + i] = getFloat64(source, sourceIndex + i * 8);
    }
  }
}
