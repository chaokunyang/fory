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

package org.apache.fory.integration_tests;

import java.util.List;
import java.util.Map;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.ForyStruct;
import org.apache.fory.annotation.ForyStruct.Evolution;
import org.apache.fory.annotation.Int32Type;
import org.apache.fory.annotation.Int64Type;
import org.apache.fory.annotation.Int8Type;
import org.apache.fory.annotation.Nullable;
import org.apache.fory.annotation.Ref;
import org.apache.fory.annotation.UInt16Type;
import org.apache.fory.annotation.UInt32Type;
import org.apache.fory.annotation.UInt64Type;
import org.apache.fory.annotation.UInt8Type;
import org.apache.fory.collection.BFloat16List;
import org.apache.fory.collection.BoolList;
import org.apache.fory.collection.Float16List;
import org.apache.fory.collection.Float32List;
import org.apache.fory.collection.Float64List;
import org.apache.fory.collection.Int16List;
import org.apache.fory.collection.Int32List;
import org.apache.fory.collection.Int64List;
import org.apache.fory.collection.Int8List;
import org.apache.fory.collection.UInt16List;
import org.apache.fory.collection.UInt32List;
import org.apache.fory.collection.UInt64List;
import org.apache.fory.collection.UInt8List;
import org.apache.fory.config.Int32Encoding;
import org.apache.fory.config.Int64Encoding;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.BFloat16Array;
import org.apache.fory.type.Float16;
import org.apache.fory.type.Float16Array;

@ForyStruct
public class ExampleMessage {
  @ForyField(id = 1)
  public boolean boolValue;

  @ForyField(id = 2)
  public byte int8Value;

  @ForyField(id = 3)
  public short int16Value;

  @ForyField(id = 4)
  public @Int32Type(encoding = Int32Encoding.FIXED) int fixedI32Value;

  @ForyField(id = 5)
  public int varintI32Value;

  @ForyField(id = 6)
  public @Int64Type(encoding = Int64Encoding.FIXED) long fixedI64Value;

  @ForyField(id = 7)
  public long varintI64Value;

  @ForyField(id = 8)
  public @Int64Type(encoding = Int64Encoding.TAGGED) long taggedI64Value;

  @ForyField(id = 9)
  public @UInt8Type int uint8Value;

  @ForyField(id = 10)
  public @UInt16Type int uint16Value;

  @ForyField(id = 11)
  public @UInt32Type(encoding = Int32Encoding.FIXED) long fixedU32Value;

  @ForyField(id = 12)
  public @UInt32Type long varintU32Value;

  @ForyField(id = 13)
  public @UInt64Type(encoding = Int64Encoding.FIXED) long fixedU64Value;

  @ForyField(id = 14)
  public @UInt64Type long varintU64Value;

  @ForyField(id = 15)
  public @UInt64Type(encoding = Int64Encoding.TAGGED) long taggedU64Value;

  @ForyField(id = 16)
  public Float16 float16Value;

  @ForyField(id = 17)
  public BFloat16 bfloat16Value;

  @ForyField(id = 18)
  public float float32Value;

  @ForyField(id = 19)
  public double float64Value;

  @ForyField(id = 20)
  public String stringValue;

  @ForyField(id = 21)
  public byte[] bytesValue;

  @ForyField(id = 22)
  public java.time.LocalDate dateValue;

  @ForyField(id = 23)
  public java.time.Instant timestampValue;

  @ForyField(id = 24)
  public java.time.Duration durationValue;

  @ForyField(id = 25)
  public java.math.BigDecimal decimalValue;

  @ForyField(id = 26)
  public State enumValue;

  @Nullable
  @ForyField(id = 27)
  public Leaf messageValue;

  @ForyField(id = 101)
  public BoolList boolList;

  @ForyField(id = 102)
  public Int8List int8List;

  @ForyField(id = 103)
  public Int16List int16List;

  @ForyField(id = 104)
  public @Int32Type(encoding = Int32Encoding.FIXED) Int32List fixedI32List;

  @ForyField(id = 105)
  public Int32List varintI32List;

  @ForyField(id = 106)
  public @Int64Type(encoding = Int64Encoding.FIXED) Int64List fixedI64List;

  @ForyField(id = 107)
  public Int64List varintI64List;

  @ForyField(id = 108)
  public @Int64Type(encoding = Int64Encoding.TAGGED) Int64List taggedI64List;

  @ForyField(id = 109)
  public UInt8List uint8List;

  @ForyField(id = 110)
  public UInt16List uint16List;

  @ForyField(id = 111)
  public @UInt32Type(encoding = Int32Encoding.FIXED) UInt32List fixedU32List;

  @ForyField(id = 112)
  public UInt32List varintU32List;

  @ForyField(id = 113)
  public @UInt64Type(encoding = Int64Encoding.FIXED) UInt64List fixedU64List;

  @ForyField(id = 114)
  public UInt64List varintU64List;

  @ForyField(id = 115)
  public @UInt64Type(encoding = Int64Encoding.TAGGED) UInt64List taggedU64List;

  @ForyField(id = 116)
  public Float16List float16List;

  @ForyField(id = 117)
  public BFloat16List bfloat16List;

  @ForyField(id = 118)
  public List<Float16> maybeFloat16List;

  @ForyField(id = 119)
  public List<BFloat16> maybeBfloat16List;

  @ForyField(id = 120)
  public Float32List float32List;

  @ForyField(id = 121)
  public Float64List float64List;

  @ForyField(id = 122)
  public List<String> stringList;

  @ForyField(id = 123)
  public List<byte[]> bytesList;

  @ForyField(id = 124)
  public List<java.time.LocalDate> dateList;

  @ForyField(id = 125)
  public List<java.time.Instant> timestampList;

  @ForyField(id = 126)
  public List<java.time.Duration> durationList;

  @ForyField(id = 127)
  public List<java.math.BigDecimal> decimalList;

  @ForyField(id = 128)
  public List<State> enumList;

  @ForyField(id = 129)
  public List<@Ref(enable = false) Leaf> messageList;

  @ForyField(id = 131)
  public List<@Int32Type(encoding = Int32Encoding.FIXED) Integer> maybeFixedI32List;

  @ForyField(id = 132)
  public List<@UInt64Type Long> maybeUint64List;

  @ForyField(id = 301)
  public boolean[] boolArray;

  @ForyField(id = 302)
  public @Int8Type byte[] int8Array;

  @ForyField(id = 303)
  public short[] int16Array;

  @ForyField(id = 304)
  public int[] int32Array;

  @ForyField(id = 305)
  public long[] int64Array;

  @ForyField(id = 306)
  public @UInt8Type byte[] uint8Array;

  @ForyField(id = 307)
  public @UInt16Type short[] uint16Array;

  @ForyField(id = 308)
  public @UInt32Type int[] uint32Array;

  @ForyField(id = 309)
  public @UInt64Type long[] uint64Array;

  @ForyField(id = 310)
  public Float16Array float16Array;

  @ForyField(id = 311)
  public BFloat16Array bfloat16Array;

  @ForyField(id = 312)
  public float[] float32Array;

  @ForyField(id = 313)
  public double[] float64Array;

  @ForyField(id = 314)
  public List<int[]> int32ArrayList;

  @ForyField(id = 315)
  public List<@UInt8Type byte[]> uint8ArrayList;

  @ForyField(id = 201)
  public Map<Boolean, String> stringValuesByBool;

  @ForyField(id = 202)
  public Map<Byte, String> stringValuesByInt8;

  @ForyField(id = 203)
  public Map<Short, String> stringValuesByInt16;

  @ForyField(id = 204)
  public Map<@Int32Type(encoding = Int32Encoding.FIXED) Integer, String> stringValuesByFixedI32;

  @ForyField(id = 205)
  public Map<Integer, String> stringValuesByVarintI32;

  @ForyField(id = 206)
  public Map<@Int64Type(encoding = Int64Encoding.FIXED) Long, String> stringValuesByFixedI64;

  @ForyField(id = 207)
  public Map<Long, String> stringValuesByVarintI64;

  @ForyField(id = 208)
  public Map<@Int64Type(encoding = Int64Encoding.TAGGED) Long, String> stringValuesByTaggedI64;

  @ForyField(id = 209)
  public Map<@UInt8Type Integer, String> stringValuesByUint8;

  @ForyField(id = 210)
  public Map<@UInt16Type Integer, String> stringValuesByUint16;

  @ForyField(id = 211)
  public Map<@UInt32Type(encoding = Int32Encoding.FIXED) Long, String> stringValuesByFixedU32;

  @ForyField(id = 212)
  public Map<@UInt32Type Long, String> stringValuesByVarintU32;

  @ForyField(id = 213)
  public Map<@UInt64Type(encoding = Int64Encoding.FIXED) Long, String> stringValuesByFixedU64;

  @ForyField(id = 214)
  public Map<@UInt64Type Long, String> stringValuesByVarintU64;

  @ForyField(id = 215)
  public Map<@UInt64Type(encoding = Int64Encoding.TAGGED) Long, String> stringValuesByTaggedU64;

  @ForyField(id = 218)
  public Map<String, String> stringValuesByString;

  @ForyField(id = 219)
  public Map<java.time.Instant, String> stringValuesByTimestamp;

  @ForyField(id = 220)
  public Map<java.time.Duration, String> stringValuesByDuration;

  @ForyField(id = 221)
  public Map<State, String> stringValuesByEnum;

  @ForyField(id = 222)
  public Map<String, Float16> float16ValuesByName;

  @ForyField(id = 223)
  public Map<String, Float16> maybeFloat16ValuesByName;

  @ForyField(id = 224)
  public Map<String, BFloat16> bfloat16ValuesByName;

  @ForyField(id = 225)
  public Map<String, BFloat16> maybeBfloat16ValuesByName;

  @ForyField(id = 226)
  public Map<String, byte[]> bytesValuesByName;

  @ForyField(id = 227)
  public Map<String, java.time.LocalDate> dateValuesByName;

  @ForyField(id = 228)
  public Map<String, java.math.BigDecimal> decimalValuesByName;

  @ForyField(id = 229)
  public Map<String, @Ref(enable = false) Leaf> messageValuesByName;

  @ForyField(id = 231)
  public Map<String, @UInt8Type byte[]> uint8ArrayValuesByName;

  @ForyField(id = 232)
  public Map<String, float[]> float32ArrayValuesByName;

  @ForyField(id = 233)
  public Map<String, int[]> int32ArrayValuesByName;

  @ForyField(id = 234)
  public Map<java.time.LocalDate, String> stringValuesByDate;

  @ForyField(id = 236)
  public Map<String, Boolean> boolValuesByName;

  @ForyField(id = 237)
  public Map<String, Byte> int8ValuesByName;

  @ForyField(id = 238)
  public Map<String, Short> int16ValuesByName;

  @ForyField(id = 239)
  public Map<String, @Int32Type(encoding = Int32Encoding.FIXED) Integer> fixedI32ValuesByName;

  @ForyField(id = 240)
  public Map<String, Integer> varintI32ValuesByName;

  @ForyField(id = 241)
  public Map<String, @Int64Type(encoding = Int64Encoding.FIXED) Long> fixedI64ValuesByName;

  @ForyField(id = 242)
  public Map<String, Long> varintI64ValuesByName;

  @ForyField(id = 243)
  public Map<String, @Int64Type(encoding = Int64Encoding.TAGGED) Long> taggedI64ValuesByName;

  @ForyField(id = 244)
  public Map<String, @UInt8Type Integer> uint8ValuesByName;

  @ForyField(id = 245)
  public Map<String, @UInt16Type Integer> uint16ValuesByName;

  @ForyField(id = 246)
  public Map<String, @UInt32Type(encoding = Int32Encoding.FIXED) Long> fixedU32ValuesByName;

  @ForyField(id = 247)
  public Map<String, @UInt32Type Long> varintU32ValuesByName;

  @ForyField(id = 248)
  public Map<String, @UInt64Type(encoding = Int64Encoding.FIXED) Long> fixedU64ValuesByName;

  @ForyField(id = 249)
  public Map<String, @UInt64Type Long> varintU64ValuesByName;

  @ForyField(id = 250)
  public Map<String, @UInt64Type(encoding = Int64Encoding.TAGGED) Long> taggedU64ValuesByName;

  @ForyField(id = 251)
  public Map<String, Float> float32ValuesByName;

  @ForyField(id = 252)
  public Map<String, Double> float64ValuesByName;

  @ForyField(id = 253)
  public Map<String, java.time.Instant> timestampValuesByName;

  @ForyField(id = 254)
  public Map<String, java.time.Duration> durationValuesByName;

  @ForyField(id = 255)
  public Map<String, State> enumValuesByName;

  public ExampleMessage() {}

  @ForyStruct(evolution = Evolution.DISABLED)
  public static class Leaf {
    @ForyField(id = 1)
    public String label;

    @ForyField(id = 2)
    public int count;

    public Leaf() {}
  }

  public enum State {
    UNKNOWN(0),
    READY(1),
    FAILED(2);

    @org.apache.fory.annotation.ForyEnumId public final int id;

    State(int id) {
      this.id = id;
    }
  }
}
