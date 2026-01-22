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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class UintUnsignedTest {
  @Test
  public void uint8ParsingAndFormatting() {
    Uint8 value = Uint8.parse("255");
    assertEquals(value.toInt(), 255);
    assertEquals(Uint8.parse("ff", 16).toInt(), 255);
    assertEquals(value.toUnsignedString(16), "ff");
    assertEquals(value.toHexString(), "ff");
    assertTrue(Uint8.parse("0").isZero());
    assertTrue(Uint8.MAX_VALUE.isMaxValue());
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void uint8ParseOutOfRange() {
    Uint8.parse("256");
  }

  @Test
  public void uint8ArithmeticAndBitwise() {
    Uint8 a = Uint8.parse("255");
    Uint8 b = Uint8.valueOf(1);

    assertEquals(a.add(b).toInt(), 0); // wraps
    assertEquals(Uint8.MIN_VALUE.subtract(b).toInt(), 255);
    assertEquals(a.divide(Uint8.valueOf(2)).toInt(), 127);
    assertEquals(a.remainder(Uint8.valueOf(2)).toInt(), 1);

    Uint8 lo = Uint8.valueOf(0x0F);
    Uint8 hi = Uint8.valueOf(0xF0);
    assertEquals(lo.or(hi).toInt(), 0xFF);
    assertEquals(hi.and(lo).toInt(), 0);
    assertEquals(lo.xor(hi).toInt(), 0xFF);
    assertEquals(lo.not().toInt(), 0xF0);

    assertEquals(Uint8.valueOf(0x81).shiftRight(1).toInt(), 0x40);
    assertEquals(Uint8.valueOf(0x01).shiftLeft(7).toInt(), 0x80);
  }

  @Test
  public void uint16ParsingAndFormatting() {
    Uint16 value = Uint16.parse("65535");
    assertEquals(value.toInt(), 65535);
    assertEquals(Uint16.parse("ffff", 16).toInt(), 65535);
    assertEquals(value.toUnsignedString(16), "ffff");
    assertEquals(value.toHexString(), "ffff");
    assertTrue(Uint16.parse("0").isZero());
    assertTrue(Uint16.MAX_VALUE.isMaxValue());
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void uint16ParseOutOfRange() {
    Uint16.parse("65536");
  }

  @Test
  public void uint16ArithmeticAndBitwise() {
    Uint16 a = Uint16.parse("65535");
    Uint16 b = Uint16.valueOf(1);

    assertEquals(a.add(b).toInt(), 0); // wraps
    assertEquals(Uint16.MIN_VALUE.subtract(b).toInt(), 65535);
    assertEquals(a.divide(Uint16.valueOf(2)).toInt(), 32767);
    assertEquals(a.remainder(Uint16.valueOf(2)).toInt(), 1);

    Uint16 lo = Uint16.valueOf(0x00FF);
    Uint16 hi = Uint16.valueOf(0xFF00);
    assertEquals(lo.or(hi).toInt(), 0xFFFF);
    assertEquals(hi.and(lo).toInt(), 0);
    assertEquals(lo.xor(hi).toInt(), 0xFFFF);
    assertEquals(lo.not().toInt(), 0xFF00);

    assertEquals(Uint16.valueOf(0x8001).shiftRight(1).toInt(), 0x4000);
    assertEquals(Uint16.valueOf(0x0001).shiftLeft(15).toInt(), 0x8000);
  }

  @Test
  public void uint32ParsingAndArithmetic() {
    Uint32 value = Uint32.parse("4294967295");
    assertEquals(value.toLong(), 4294967295L);
    assertEquals(Uint32.parse("ffffffff", 16).toLong(), 4294967295L);
    assertEquals(value.toUnsignedString(16), "ffffffff");
    assertEquals(value.toHexString(), "ffffffff");
    assertFalse(value.isZero());
    assertTrue(value.isMaxValue());

    Uint32 wrap = Uint32.valueOf(-1); // MAX
    assertEquals(wrap.add(Uint32.valueOf(1)).toLong(), 0L);
    assertEquals(wrap.divide(Uint32.valueOf(2)).toLong(), 2147483647L);
    assertEquals(wrap.remainder(Uint32.valueOf(2)).toLong(), 1L);

    Uint32 lo = Uint32.valueOf(0x00FF00FF);
    Uint32 hi = Uint32.valueOf(0xFF00FF00);
    assertEquals(lo.or(hi).toLong(), 0xFFFFFFFFL);
    assertEquals(hi.and(lo).toLong(), 0L);
    assertEquals(lo.xor(hi).toLong(), 0xFFFFFFFFL);
    assertEquals(lo.not().toLong(), 0xFF00FF00L);

    assertEquals(Uint32.valueOf(0x80000001).shiftRight(1).toLong(), 0x40000000L);
    assertEquals(Uint32.valueOf(0x00000001).shiftLeft(31).toLong(), 0x80000000L);
  }

  @Test
  public void uint64ParsingAndArithmetic() {
    Uint64 value = Uint64.parse("18446744073709551615");
    assertEquals(value.toUnsignedString(16), "ffffffffffffffff");
    assertEquals(value.toHexString(), "ffffffffffffffff");
    assertFalse(value.isZero());
    assertTrue(value.isMaxValue());

    Uint64 wrap = Uint64.valueOf(-1L); // MAX
    assertEquals(wrap.add(Uint64.valueOf(1)).longValue(), 0L);
    assertEquals(wrap.divide(Uint64.valueOf(2)).longValue(), Long.parseUnsignedLong("9223372036854775807"));
    assertEquals(wrap.remainder(Uint64.valueOf(2)).longValue(), 1L);

    Uint64 lo = Uint64.valueOf(0x00FF00FF00FF00FFL);
    Uint64 hi = Uint64.valueOf(0xFF00FF00FF00FF00L);
    assertEquals(lo.or(hi).toLong(), -1L);
    assertEquals(hi.and(lo).toLong(), 0L);
    assertEquals(lo.xor(hi).toLong(), -1L);
    assertEquals(lo.not().toLong(), 0xFF00FF00FF00FF00L);

    assertEquals(Uint64.valueOf(0x8000000000000001L).shiftRight(1).toLong(), 0x4000000000000000L);
    assertEquals(Uint64.valueOf(0x0000000000000001L).shiftLeft(63).toLong(), Long.MIN_VALUE);
  }
}
