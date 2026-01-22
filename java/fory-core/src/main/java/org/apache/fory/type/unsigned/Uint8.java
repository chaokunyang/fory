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

/** Unsigned 8-bit integer backed by a byte. */
public final class Uint8 extends Number implements Comparable<Uint8>, Serializable {
  public static final int SIZE_BITS = 8;
  public static final int SIZE_BYTES = 1;

  public static final Uint8 MIN_VALUE = new Uint8((byte) 0);
  public static final Uint8 MAX_VALUE = new Uint8((byte) -1);

  private final byte data;

  public Uint8(byte data) {
    this.data = data;
  }

  public static Uint8 valueOf(byte value) {
    return new Uint8(value);
  }

  public static Uint8 valueOf(int value) {
    return new Uint8((byte) value);
  }

  public static Uint8 add(byte a, byte b) {
    return new Uint8((byte) (a + b));
  }

  public static Uint8 subtract(byte a, byte b) {
    return new Uint8((byte) (a - b));
  }

  public static Uint8 multiply(byte a, byte b) {
    return new Uint8((byte) (a * b));
  }

  public static Uint8 divide(byte a, byte b) {
    int divisor = b & 0xFF;
    return new Uint8((byte) ((a & 0xFF) / divisor));
  }

  public static Uint8 remainder(byte a, byte b) {
    int divisor = b & 0xFF;
    return new Uint8((byte) ((a & 0xFF) % divisor));
  }

  public static Uint8 min(byte a, byte b) {
    return compare(a, b) <= 0 ? new Uint8(a) : new Uint8(b);
  }

  public static Uint8 max(byte a, byte b) {
    return compare(a, b) >= 0 ? new Uint8(a) : new Uint8(b);
  }

  public byte toByte() {
    return data;
  }

  public static int toInt(byte value) {
    return value & 0xFF;
  }

  public static long toLong(byte value) {
    return toInt(value);
  }

  public short toShort() {
    return (short) (data & 0xFF);
  }

  public int toInt() {
    return data & 0xFF;
  }

  public long toLong() {
    return toInt();
  }

  public static int compare(byte a, byte b) {
    return Integer.compare(a & 0xFF, b & 0xFF);
  }

  public static String toString(byte value) {
    return Integer.toString(value & 0xFF);
  }

  public static String toString(byte value, int radix) {
    return Integer.toString(value & 0xFF, radix);
  }

  @Override
  public int compareTo(Uint8 other) {
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
    return data;
  }

  @Override
  public short shortValue() {
    return (short) (data & 0xFF);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Uint8)) {
      return false;
    }
    return data == ((Uint8) obj).data;
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
