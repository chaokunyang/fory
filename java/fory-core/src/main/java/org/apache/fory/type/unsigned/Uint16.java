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

package org.apache.fory.type.unsigned;

import java.io.Serializable;

/**
 * Unsigned 16-bit integer backed by a short.
 *
 * <p>Arithmetic, bitwise, and shift operations wrap modulo {@code 2^16}. Use {@link #toInt()} or
 * {@link #toLong()} to obtain an unsigned magnitude for interoperability with APIs that treat the
 * value as unsigned.</p>
 */
public final class Uint16 extends Number implements Comparable<Uint16>, Serializable {
  public static final int SIZE_BITS = 16;
  public static final int SIZE_BYTES = 2;

  private static final int MASK = 0xFFFF;

  public static final Uint16 MIN_VALUE = new Uint16((short) 0);
  public static final Uint16 MAX_VALUE = new Uint16((short) -1);

  private final short data;

  public Uint16(short data) {
    this.data = data;
  }

  public static Uint16 valueOf(short value) {
    return new Uint16(value);
  }

  public static Uint16 valueOf(int value) {
    return new Uint16((short) value);
  }

  public static Uint16 add(short a, short b) {
    return new Uint16((short) (a + b));
  }

  /** Adds {@code other} with wrapping semantics. */
  public Uint16 add(Uint16 other) {
    return add(data, other.data);
  }

  public static Uint16 subtract(short a, short b) {
    return new Uint16((short) (a - b));
  }

  /** Subtracts {@code other} with wrapping semantics. */
  public Uint16 subtract(Uint16 other) {
    return subtract(data, other.data);
  }

  public static Uint16 multiply(short a, short b) {
    return new Uint16((short) (a * b));
  }

  /** Multiplies by {@code other} with wrapping semantics. */
  public Uint16 multiply(Uint16 other) {
    return multiply(data, other.data);
  }

  public static Uint16 divide(short a, short b) {
    int divisor = b & 0xFFFF;
    return new Uint16((short) ((a & 0xFFFF) / divisor));
  }

  /** Divides by {@code other} treating both operands as unsigned. */
  public Uint16 divide(Uint16 other) {
    return divide(data, other.data);
  }

  public static Uint16 remainder(short a, short b) {
    int divisor = b & 0xFFFF;
    return new Uint16((short) ((a & 0xFFFF) % divisor));
  }

  /** Computes the remainder of the unsigned division by {@code other}. */
  public Uint16 remainder(Uint16 other) {
    return remainder(data, other.data);
  }

  public static Uint16 min(short a, short b) {
    return compare(a, b) <= 0 ? new Uint16(a) : new Uint16(b);
  }

  public static Uint16 max(short a, short b) {
    return compare(a, b) >= 0 ? new Uint16(a) : new Uint16(b);
  }

  /** Parses an unsigned decimal string into a {@link Uint16}. */
  public static Uint16 parse(String value) {
    return parse(value, 10);
  }

  /** Parses an unsigned string in {@code radix} into a {@link Uint16}. */
  public static Uint16 parse(String value, int radix) {
    int parsed = Integer.parseUnsignedInt(value, radix);
    if ((parsed & ~MASK) != 0) {
      throw new NumberFormatException("Value out of range for Uint16: " + value);
    }
    return new Uint16((short) parsed);
  }

  public short toShort() {
    return data;
  }

  public static int toInt(short value) {
    return value & MASK;
  }

  public int toInt() {
    return data & MASK;
  }

  public static long toLong(short value) {
    return toInt(value);
  }

  public long toLong() {
    return toInt();
  }

  public static int compare(short a, short b) {
    return Integer.compare(a & MASK, b & MASK);
  }

  public static String toString(short value) {
    return Integer.toString(value & MASK);
  }

  public static String toString(short value, int radix) {
    return Integer.toString(value & MASK, radix);
  }

  @Override
  public String toString() {
    return Integer.toString(toInt());
  }

  /** Returns the hexadecimal string representation without sign-extension. */
  public String toHexString() {
    return Integer.toHexString(toInt());
  }

  /** Returns the unsigned string representation using the provided {@code radix}. */
  public String toUnsignedString(int radix) {
    return Integer.toString(toInt(), radix);
  }

  /** Returns {@code true} if the value equals zero. */
  public boolean isZero() {
    return data == 0;
  }

  /** Returns {@code true} if the value equals {@link #MAX_VALUE}. */
  public boolean isMaxValue() {
    return data == (short) -1;
  }

  /** Bitwise AND with {@code other}. */
  public Uint16 and(Uint16 other) {
    return new Uint16((short) ((data & MASK) & (other.data & MASK)));
  }

  /** Bitwise OR with {@code other}. */
  public Uint16 or(Uint16 other) {
    return new Uint16((short) ((data & MASK) | (other.data & MASK)));
  }

  /** Bitwise XOR with {@code other}. */
  public Uint16 xor(Uint16 other) {
    return new Uint16((short) ((data & MASK) ^ (other.data & MASK)));
  }

  /** Bitwise NOT. */
  public Uint16 not() {
    return new Uint16((short) (~toInt()));
  }

  /** Logical left shift; bits shifted out are discarded. */
  public Uint16 shiftLeft(int bits) {
    int shift = bits & 0x1F;
    return new Uint16((short) ((toInt() << shift) & MASK));
  }

  /** Logical right shift; zeros are shifted in from the left. */
  public Uint16 shiftRight(int bits) {
    int shift = bits & 0x1F;
    return new Uint16((short) (toInt() >>> shift));
  }

  @Override
  public int compareTo(Uint16 other) {
    return compare(data, other.data);
  }

  @Override
  public int intValue() {
    return toInt();
  }

  @Override
  public long longValue() {
    return toLong();
  }

  @Override
  public float floatValue() {
    return toInt();
  }

  @Override
  public double doubleValue() {
    return toInt();
  }

  @Override
  public byte byteValue() {
    return (byte) data;
  }

  @Override
  public short shortValue() {
    return data;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Uint16)) {
      return false;
    }
    return data == ((Uint16) obj).data;
  }

  @Override
  public int hashCode() {
    return data;
  }
}
