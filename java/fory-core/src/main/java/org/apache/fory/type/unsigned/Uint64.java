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

/** Unsigned 64-bit integer backed by a long. */
public final class Uint64 extends Number implements Comparable<Uint64>, Serializable {
  public static final int SIZE_BITS = 64;
  public static final int SIZE_BYTES = 8;

  public static final Uint64 MIN_VALUE = new Uint64(0L);
  public static final Uint64 MAX_VALUE = new Uint64(-1L);

  private final long data;

  public Uint64(long data) {
    this.data = data;
  }

  public static Uint64 valueOf(long value) {
    return new Uint64(value);
  }

  public static Uint64 add(long a, long b) {
    return new Uint64(a + b);
  }

  public static Uint64 subtract(long a, long b) {
    return new Uint64(a - b);
  }

  public static Uint64 multiply(long a, long b) {
    return new Uint64(a * b);
  }

  public static Uint64 divide(long a, long b) {
    return new Uint64(Long.divideUnsigned(a, b));
  }

  public static Uint64 remainder(long a, long b) {
    return new Uint64(Long.remainderUnsigned(a, b));
  }

  public static Uint64 min(long a, long b) {
    return compare(a, b) <= 0 ? new Uint64(a) : new Uint64(b);
  }

  public static Uint64 max(long a, long b) {
    return compare(a, b) >= 0 ? new Uint64(a) : new Uint64(b);
  }

  public long toLong() {
    return data;
  }

  public static int compare(long a, long b) {
    return Long.compareUnsigned(a, b);
  }

  public static String toString(long value) {
    return Long.toUnsignedString(value);
  }

  public static String toString(long value, int radix) {
    return Long.toUnsignedString(value, radix);
  }

  @Override
  public int compareTo(Uint64 other) {
    return compare(data, other.data);
  }

  @Override
  public int intValue() {
    return (int) data;
  }

  @Override
  public long longValue() {
    return data;
  }

  @Override
  public float floatValue() {
    return (float) toUnsignedDouble(data);
  }

  @Override
  public double doubleValue() {
    return toUnsignedDouble(data);
  }

  @Override
  public byte byteValue() {
    return (byte) data;
  }

  @Override
  public short shortValue() {
    return (short) data;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Uint64)) {
      return false;
    }
    return data == ((Uint64) obj).data;
  }

  @Override
  public int hashCode() {
    return (int) (data ^ (data >>> 32));
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(data);
  }

  private static double toUnsignedDouble(long value) {
    double high = (double) (value >>> 1);
    return high * 2.0 + (value & 1L);
  }
}
