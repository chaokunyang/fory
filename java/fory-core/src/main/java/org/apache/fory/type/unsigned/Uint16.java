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

/** Unsigned 16-bit integer backed by a short. */
public final class Uint16 extends Number implements Comparable<Uint16>, Serializable {
  public static final int SIZE_BITS = 16;
  public static final int SIZE_BYTES = 2;

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

  public static Uint16 subtract(short a, short b) {
    return new Uint16((short) (a - b));
  }

  public static Uint16 multiply(short a, short b) {
    return new Uint16((short) (a * b));
  }

  public static Uint16 divide(short a, short b) {
    int divisor = b & 0xFFFF;
    return new Uint16((short) ((a & 0xFFFF) / divisor));
  }

  public static Uint16 remainder(short a, short b) {
    int divisor = b & 0xFFFF;
    return new Uint16((short) ((a & 0xFFFF) % divisor));
  }

  public static Uint16 min(short a, short b) {
    return compare(a, b) <= 0 ? new Uint16(a) : new Uint16(b);
  }

  public static Uint16 max(short a, short b) {
    return compare(a, b) >= 0 ? new Uint16(a) : new Uint16(b);
  }

  public short toShort() {
    return data;
  }

  public static int toInt(short value) {
    return value & 0xFFFF;
  }

  public static long toLong(short value) {
    return toInt(value);
  }

  public int toInt() {
    return data & 0xFFFF;
  }

  public long toLong() {
    return toInt();
  }

  public static int compare(short a, short b) {
    return Integer.compare(a & 0xFFFF, b & 0xFFFF);
  }

  public static String toString(short value) {
    return Integer.toString(value & 0xFFFF);
  }

  public static String toString(short value, int radix) {
    return Integer.toString(value & 0xFFFF, radix);
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

  @Override
  public String toString() {
    return Integer.toString(toInt());
  }
}
