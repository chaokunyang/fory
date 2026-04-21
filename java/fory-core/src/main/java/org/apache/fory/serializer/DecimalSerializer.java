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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import org.apache.fory.config.Config;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;

/** Serializers for decimal types in native and xlang modes. */
public final class DecimalSerializer {
  private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

  private DecimalSerializer() {}

  static final class DecimalParts {
    final int scale;
    final BigInteger unscaled;

    DecimalParts(int scale, BigInteger unscaled) {
      this.scale = scale;
      this.unscaled = unscaled;
    }
  }

  static void writeXlangDecimal(MemoryBuffer buffer, int scale, BigInteger unscaled) {
    buffer.writeVarInt32(scale);
    if (canUseSmallEncoding(unscaled)) {
      long smallValue = unscaled.longValue();
      long header = encodeZigZag64(smallValue) << 1;
      buffer.writeVarUint64(header);
      return;
    }

    int sign = unscaled.signum() < 0 ? 1 : 0;
    byte[] payload = toCanonicalLittleEndianMagnitude(unscaled.abs());
    long meta = (((long) payload.length) << 1) | sign;
    long header = (meta << 1) | 1L;
    buffer.writeVarUint64(header);
    buffer.writeBytes(payload);
  }

  static DecimalParts readXlangDecimal(MemoryBuffer buffer) {
    int scale = buffer.readVarInt32();
    long header = buffer.readVarUint64();
    BigInteger unscaled;
    if ((header & 1L) == 0L) {
      unscaled = BigInteger.valueOf(decodeZigZag64(header >>> 1));
    } else {
      long meta = header >>> 1;
      int sign = (int) (meta & 1L);
      long lenLong = meta >>> 1;
      if (lenLong <= 0 || lenLong > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "Invalid decimal magnitude length " + lenLong + " in xlang payload");
      }
      int len = (int) lenLong;
      byte[] payload = buffer.readBytes(len);
      if (payload[len - 1] == 0) {
        throw new IllegalArgumentException("Non-canonical decimal payload: trailing zero byte");
      }
      byte[] magnitude = toBigEndian(payload);
      BigInteger abs = new BigInteger(1, magnitude);
      if (abs.signum() == 0) {
        throw new IllegalArgumentException("Big decimal encoding must not represent zero");
      }
      unscaled = sign == 0 ? abs : abs.negate();
    }
    return new DecimalParts(scale, unscaled);
  }

  public static final class BigDecimalSerializer extends ImmutableSerializer<BigDecimal>
      implements Shareable {
    public BigDecimalSerializer(Config config) {
      super(config, BigDecimal.class);
    }

    @Override
    public void write(WriteContext writeContext, BigDecimal value) {
      MemoryBuffer buffer = writeContext.getBuffer();
      final byte[] bytes = value.unscaledValue().toByteArray();
      buffer.writeVarUint32Small7(value.scale());
      buffer.writeVarUint32Small7(value.precision());
      buffer.writeVarUint32Small7(bytes.length);
      buffer.writeBytes(bytes);
    }

    @Override
    public BigDecimal read(ReadContext readContext) {
      MemoryBuffer buffer = readContext.getBuffer();
      int scale = buffer.readVarUint32Small7();
      int precision = buffer.readVarUint32Small7();
      int len = buffer.readVarUint32Small7();
      byte[] bytes = buffer.readBytes(len);
      final BigInteger bigInteger = new BigInteger(bytes);
      return new BigDecimal(bigInteger, scale, new MathContext(precision));
    }
  }

  public static final class XlangBigDecimalSerializer extends ImmutableSerializer<BigDecimal>
      implements Shareable {
    public XlangBigDecimalSerializer(Config config) {
      super(config, BigDecimal.class);
    }

    @Override
    public void write(WriteContext writeContext, BigDecimal value) {
      writeXlangDecimal(writeContext.getBuffer(), value.scale(), value.unscaledValue());
    }

    @Override
    public BigDecimal read(ReadContext readContext) {
      DecimalParts parts = readXlangDecimal(readContext.getBuffer());
      return new BigDecimal(parts.unscaled, parts.scale);
    }
  }

  public static final class BigIntegerSerializer extends ImmutableSerializer<BigInteger>
      implements Shareable {
    public BigIntegerSerializer(Config config) {
      super(config, BigInteger.class);
    }

    @Override
    public void write(WriteContext writeContext, BigInteger value) {
      MemoryBuffer buffer = writeContext.getBuffer();
      final byte[] bytes = value.toByteArray();
      buffer.writeVarUint32Small7(bytes.length);
      buffer.writeBytes(bytes);
    }

    @Override
    public BigInteger read(ReadContext readContext) {
      MemoryBuffer buffer = readContext.getBuffer();
      int len = buffer.readVarUint32Small7();
      byte[] bytes = buffer.readBytes(len);
      return new BigInteger(bytes);
    }
  }

  public static final class XlangBigIntegerSerializer extends ImmutableSerializer<BigInteger>
      implements Shareable {
    public XlangBigIntegerSerializer(Config config) {
      super(config, BigInteger.class);
    }

    @Override
    public void write(WriteContext writeContext, BigInteger value) {
      writeXlangDecimal(writeContext.getBuffer(), 0, value);
    }

    @Override
    public BigInteger read(ReadContext readContext) {
      DecimalParts parts = readXlangDecimal(readContext.getBuffer());
      if (parts.scale != 0) {
        throw new IllegalArgumentException(
            "Cannot deserialize xlang decimal with scale "
                + parts.scale
                + " into BigInteger");
      }
      return parts.unscaled;
    }
  }

  private static boolean canUseSmallEncoding(BigInteger value) {
    if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
      return false;
    }
    // The small form reserves the low header bit to distinguish small/big encodings,
    // so the zigzag value itself must still fit in 63 bits before the final << 1.
    long zigZag = encodeZigZag64(value.longValue());
    return (zigZag & Long.MIN_VALUE) == 0;
  }

  private static long encodeZigZag64(long value) {
    return (value << 1) ^ (value >> 63);
  }

  private static long decodeZigZag64(long value) {
    return (value >>> 1) ^ -(value & 1L);
  }

  private static byte[] toCanonicalLittleEndianMagnitude(BigInteger abs) {
    byte[] bigEndian = abs.toByteArray();
    int start = 0;
    while (start < bigEndian.length - 1 && bigEndian[start] == 0) {
      start++;
    }
    int len = bigEndian.length - start;
    if (len <= 0) {
      throw new IllegalArgumentException("Zero must use the small decimal encoding");
    }
    byte[] littleEndian = new byte[len];
    for (int i = 0; i < len; i++) {
      littleEndian[i] = bigEndian[bigEndian.length - 1 - i];
    }
    return littleEndian;
  }

  private static byte[] toBigEndian(byte[] littleEndian) {
    byte[] bigEndian = new byte[littleEndian.length];
    for (int i = 0; i < littleEndian.length; i++) {
      bigEndian[i] = littleEndian[littleEndian.length - 1 - i];
    }
    return bigEndian;
  }
}
