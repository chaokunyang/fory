/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fory.memory;

/**
 * Provides byte-by-byte memory access operations for platforms that require aligned access. This
 * class is used as a fallback when direct Unsafe operations may fail due to unaligned memory
 * addresses on certain platforms (like ARM64 with newer JDKs).
 *
 * <p>Methods with 'L' suffix read/write in little-endian byte order. Methods with 'B' suffix
 * read/write in big-endian byte order.
 */
final class Unaligned {

  private Unaligned() {}

  static short getShortB(byte[] bytes, int offset) {
    return (short) ((bytes[offset] << 8) | (bytes[offset + 1] & 0xFF));
  }

  static short getShortL(byte[] bytes, int offset) {
    return (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
  }

  static void putShortB(byte[] bytes, int offset, short value) {
    bytes[offset] = (byte) (value >> 8);
    bytes[offset + 1] = (byte) value;
  }

  static void putShortL(byte[] bytes, int offset, short value) {
    bytes[offset] = (byte) value;
    bytes[offset + 1] = (byte) (value >> 8);
  }

  static char getCharB(byte[] bytes, int offset) {
    return (char) ((bytes[offset] << 8) | (bytes[offset + 1] & 0xFF));
  }

  static char getCharL(byte[] bytes, int offset) {
    return (char) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
  }

  static void putCharB(byte[] bytes, int offset, char value) {
    bytes[offset] = (byte) (value >> 8);
    bytes[offset + 1] = (byte) value;
  }

  static void putCharL(byte[] bytes, int offset, char value) {
    bytes[offset] = (byte) value;
    bytes[offset + 1] = (byte) (value >> 8);
  }

  static int getIntB(byte[] bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 24)
        | ((bytes[offset + 1] & 0xFF) << 16)
        | ((bytes[offset + 2] & 0xFF) << 8)
        | (bytes[offset + 3] & 0xFF);
  }

  static int getIntL(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF)
        | ((bytes[offset + 1] & 0xFF) << 8)
        | ((bytes[offset + 2] & 0xFF) << 16)
        | ((bytes[offset + 3] & 0xFF) << 24);
  }

  static void putIntB(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) (value >> 24);
    bytes[offset + 1] = (byte) (value >> 16);
    bytes[offset + 2] = (byte) (value >> 8);
    bytes[offset + 3] = (byte) value;
  }

  static void putIntL(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) value;
    bytes[offset + 1] = (byte) (value >> 8);
    bytes[offset + 2] = (byte) (value >> 16);
    bytes[offset + 3] = (byte) (value >> 24);
  }

  static long getLongB(byte[] bytes, int offset) {
    return ((long) (bytes[offset] & 0xFF) << 56)
        | ((long) (bytes[offset + 1] & 0xFF) << 48)
        | ((long) (bytes[offset + 2] & 0xFF) << 40)
        | ((long) (bytes[offset + 3] & 0xFF) << 32)
        | ((long) (bytes[offset + 4] & 0xFF) << 24)
        | ((long) (bytes[offset + 5] & 0xFF) << 16)
        | ((long) (bytes[offset + 6] & 0xFF) << 8)
        | ((long) (bytes[offset + 7] & 0xFF));
  }

  static long getLongL(byte[] bytes, int offset) {
    return ((long) (bytes[offset] & 0xFF))
        | ((long) (bytes[offset + 1] & 0xFF) << 8)
        | ((long) (bytes[offset + 2] & 0xFF) << 16)
        | ((long) (bytes[offset + 3] & 0xFF) << 24)
        | ((long) (bytes[offset + 4] & 0xFF) << 32)
        | ((long) (bytes[offset + 5] & 0xFF) << 40)
        | ((long) (bytes[offset + 6] & 0xFF) << 48)
        | ((long) (bytes[offset + 7] & 0xFF) << 56);
  }

  static void putLongB(byte[] bytes, int offset, long value) {
    bytes[offset] = (byte) (value >> 56);
    bytes[offset + 1] = (byte) (value >> 48);
    bytes[offset + 2] = (byte) (value >> 40);
    bytes[offset + 3] = (byte) (value >> 32);
    bytes[offset + 4] = (byte) (value >> 24);
    bytes[offset + 5] = (byte) (value >> 16);
    bytes[offset + 6] = (byte) (value >> 8);
    bytes[offset + 7] = (byte) value;
  }

  static void putLongL(byte[] bytes, int offset, long value) {
    bytes[offset] = (byte) value;
    bytes[offset + 1] = (byte) (value >> 8);
    bytes[offset + 2] = (byte) (value >> 16);
    bytes[offset + 3] = (byte) (value >> 24);
    bytes[offset + 4] = (byte) (value >> 32);
    bytes[offset + 5] = (byte) (value >> 40);
    bytes[offset + 6] = (byte) (value >> 48);
    bytes[offset + 7] = (byte) (value >> 56);
  }
}
