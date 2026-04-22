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

package org.apache.fory.type;

import java.io.Serializable;

/**
 * Public carrier for xlang {@code bfloat16} values.
 *
 * <p>This type stores the exact 16-bit wire representation and converts to and from
 * {@code float} using round-to-nearest-even semantics. Use {@link #fromBits(short)} and
 * {@link #toBits()} when you need bit-preserving behavior, or {@link #valueOf(float)} and
 * {@link #toFloat()} when you want numeric conversion.
 */
public final class BFloat16 extends Number implements Comparable<BFloat16>, Serializable {
  private static final long serialVersionUID = 1L;

  private static final int SIGN_MASK = 0x8000;
  private static final int EXP_MASK = 0x7F80;
  private static final int MANT_MASK = 0x007F;

  private static final short BITS_NAN = (short) 0x7FC0;
  private static final short BITS_POS_INF = (short) 0x7F80;
  private static final short BITS_NEG_INF = (short) 0xFF80;
  private static final short BITS_NEG_ZERO = (short) 0x8000;
  private static final short BITS_MAX = (short) 0x7F7F;
  private static final short BITS_ONE = (short) 0x3F80;
  private static final short BITS_MIN_NORMAL = (short) 0x0080;
  private static final short BITS_MIN_VALUE = (short) 0x0001;

  public static final BFloat16 NaN = new BFloat16(BITS_NAN);

  public static final BFloat16 POSITIVE_INFINITY = new BFloat16(BITS_POS_INF);

  public static final BFloat16 NEGATIVE_INFINITY = new BFloat16(BITS_NEG_INF);

  public static final BFloat16 ZERO = new BFloat16((short) 0);

  public static final BFloat16 NEGATIVE_ZERO = new BFloat16(BITS_NEG_ZERO);

  public static final BFloat16 ONE = new BFloat16(BITS_ONE);

  public static final BFloat16 MAX_VALUE = new BFloat16(BITS_MAX);

  public static final BFloat16 MIN_NORMAL = new BFloat16(BITS_MIN_NORMAL);

  public static final BFloat16 MIN_VALUE = new BFloat16(BITS_MIN_VALUE);

  public static final int SIZE_BITS = 16;

  public static final int SIZE_BYTES = 2;

  private final short bits;

  private BFloat16(short bits) {
    this.bits = bits;
  }

  public static BFloat16 fromBits(short bits) {
    return new BFloat16(bits);
  }

  public static BFloat16 valueOf(float value) {
    return new BFloat16(floatToBFloat16Bits(value));
  }

  public static short toBits(float value) {
    return floatToBFloat16Bits(value);
  }

  public short toBits() {
    return bits;
  }

  public static float toFloat(short bits) {
    return bfloat16BitsToFloat(bits);
  }

  public float toFloat() {
    return floatValue();
  }

  private static short floatToBFloat16Bits(float f32) {
    int bits32 = Float.floatToRawIntBits(f32);
    if ((bits32 & 0x7F800000) == 0x7F800000 && (bits32 & 0x007FFFFF) != 0) {
      return BITS_NAN;
    }
    int lsb = (bits32 >>> 16) & 1;
    int roundingBias = 0x7FFF + lsb;
    return (short) ((bits32 + roundingBias) >>> 16);
  }

  private static float bfloat16BitsToFloat(short bits16) {
    return Float.intBitsToFloat((bits16 & 0xFFFF) << 16);
  }

  public boolean isNaN() {
    return (bits & EXP_MASK) == EXP_MASK && (bits & MANT_MASK) != 0;
  }

  public boolean isInfinite() {
    return (bits & EXP_MASK) == EXP_MASK && (bits & MANT_MASK) == 0;
  }

  public boolean isFinite() {
    return (bits & EXP_MASK) != EXP_MASK;
  }

  public boolean isZero() {
    return (bits & (EXP_MASK | MANT_MASK)) == 0;
  }

  public boolean isNormal() {
    int exp = bits & EXP_MASK;
    return exp != 0 && exp != EXP_MASK;
  }

  public boolean isSubnormal() {
    return (bits & EXP_MASK) == 0 && (bits & MANT_MASK) != 0;
  }

  public boolean signbit() {
    return (bits & SIGN_MASK) != 0;
  }

  public BFloat16 add(BFloat16 other) {
    return valueOf(floatValue() + other.floatValue());
  }

  public BFloat16 subtract(BFloat16 other) {
    return valueOf(floatValue() - other.floatValue());
  }

  public BFloat16 multiply(BFloat16 other) {
    return valueOf(floatValue() * other.floatValue());
  }

  public BFloat16 divide(BFloat16 other) {
    return valueOf(floatValue() / other.floatValue());
  }

  public BFloat16 negate() {
    return fromBits((short) (bits ^ SIGN_MASK));
  }

  public BFloat16 abs() {
    return fromBits((short) (bits & ~SIGN_MASK));
  }

  @Override
  public float floatValue() {
    return bfloat16BitsToFloat(bits);
  }

  @Override
  public double doubleValue() {
    return floatValue();
  }

  @Override
  public int intValue() {
    return (int) floatValue();
  }

  @Override
  public long longValue() {
    return (long) floatValue();
  }

  @Override
  public byte byteValue() {
    return (byte) floatValue();
  }

  @Override
  public short shortValue() {
    return (short) floatValue();
  }

  public boolean isNumericEqual(BFloat16 other) {
    if (isNaN() || other.isNaN()) {
      return false;
    }
    if (isZero() && other.isZero()) {
      return true;
    }
    return bits == other.bits;
  }

  public boolean equalsValue(BFloat16 other) {
    return isNumericEqual(other);
  }

  public boolean lessThan(BFloat16 other) {
    if (isNaN() || other.isNaN()) {
      return false;
    }
    return floatValue() < other.floatValue();
  }

  public boolean lessThanOrEqual(BFloat16 other) {
    if (isNaN() || other.isNaN()) {
      return false;
    }
    return floatValue() <= other.floatValue();
  }

  public boolean greaterThan(BFloat16 other) {
    if (isNaN() || other.isNaN()) {
      return false;
    }
    return floatValue() > other.floatValue();
  }

  public boolean greaterThanOrEqual(BFloat16 other) {
    if (isNaN() || other.isNaN()) {
      return false;
    }
    return floatValue() >= other.floatValue();
  }

  public static int compare(BFloat16 a, BFloat16 b) {
    if (a.bits == b.bits) {
      return 0;
    }
    int compare = Float.compare(a.floatValue(), b.floatValue());
    if (compare != 0) {
      return compare;
    }
    return Integer.compare(a.bits & 0xFFFF, b.bits & 0xFFFF);
  }

  public static BFloat16 parse(String s) {
    return valueOf(Float.parseFloat(s));
  }

  @Override
  public int compareTo(BFloat16 other) {
    return compare(this, other);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BFloat16)) {
      return false;
    }
    BFloat16 other = (BFloat16) obj;
    return bits == other.bits;
  }

  @Override
  public int hashCode() {
    return Short.hashCode(bits);
  }

  @Override
  public String toString() {
    return Float.toString(floatValue());
  }
}
