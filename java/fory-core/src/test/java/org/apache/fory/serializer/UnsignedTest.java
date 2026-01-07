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

import java.util.Objects;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.Uint16Type;
import org.apache.fory.annotation.Uint32Type;
import org.apache.fory.annotation.Uint64Type;
import org.apache.fory.annotation.Uint8Type;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.LongEncoding;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class UnsignedTest extends ForyTestBase {

  // Max values for unsigned types
  public static final short UINT8_MAX = 255;
  public static final int UINT16_MAX = 65535;
  public static final long UINT32_MAX = 4294967295L;
  public static final long UINT64_MAX = -1L; // 0xFFFFFFFFFFFFFFFF as signed long

  // Common test values
  public static final short UINT8_MID = 128;
  public static final int UINT16_MID = 32768;
  public static final long UINT32_MID = 2147483648L;
  public static final long UINT64_MID = Long.MIN_VALUE; // 0x8000000000000000

  @Data
  public static class UnsignedSchemaConsistent {
    @Uint8Type short u8;

    @Uint16Type int u16;

    @Uint32Type(compress = true)
    long u32Var;

    @Uint32Type(compress = false)
    long u32Fixed;

    @Uint64Type(encoding = LongEncoding.VARINT64)
    long u64Var;

    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    long u64Fixed;

    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    long u64Tagged;

    @ForyField(nullable = true)
    @Uint8Type
    Short u8Nullable;

    @ForyField(nullable = true)
    @Uint16Type
    Integer u16Nullable;

    @ForyField(nullable = true)
    @Uint32Type(compress = true)
    Long u32VarNullable;

    @ForyField(nullable = true)
    @Uint32Type(compress = false)
    Long u32FixedNullable;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.VARINT64)
    Long u64VarNullable;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    Long u64FixedNullable;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    Long u64TaggedNullable;
  }

  public static class UnsignedSchemaCompatible {
    @Uint8Type short u8;

    @Uint16Type int u16;

    @Uint32Type(compress = true)
    long u32Var;

    @Uint32Type(compress = false)
    long u32Fixed;

    @Uint64Type(encoding = LongEncoding.VARINT64)
    long u64Var;

    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    long u64Fixed;

    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    long u64Tagged;

    @ForyField(nullable = true)
    @Uint8Type
    Short u8Field2;

    @ForyField(nullable = true)
    @Uint16Type
    Integer u16Field2;

    @ForyField(nullable = true)
    @Uint32Type(compress = true)
    Long u32VarField2;

    @ForyField(nullable = true)
    @Uint32Type(compress = false)
    Long u32FixedField2;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.VARINT64)
    Long u64VarField2;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    Long u64FixedField2;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    Long u64TaggedField2;
  }

  private static UnsignedSchemaConsistent createConsistentWithNormalValues() {
    UnsignedSchemaConsistent obj = new UnsignedSchemaConsistent();
    obj.u8 = 200;
    obj.u16 = 60000;
    obj.u32Var = 3000000000L;
    obj.u32Fixed = 4000000000L;
    obj.u64Var = 10000000000L;
    obj.u64Fixed = 15000000000L;
    obj.u64Tagged = 1000000000L;
    obj.u8Nullable = (short) 128;
    obj.u16Nullable = 40000;
    obj.u32VarNullable = 2500000000L;
    obj.u32FixedNullable = 3500000000L;
    obj.u64VarNullable = 8000000000L;
    obj.u64FixedNullable = 12000000000L;
    obj.u64TaggedNullable = 500000000L;
    return obj;
  }

  private static UnsignedSchemaConsistent createConsistentWithZeroValues() {
    UnsignedSchemaConsistent obj = new UnsignedSchemaConsistent();
    obj.u8 = 0;
    obj.u16 = 0;
    obj.u32Var = 0;
    obj.u32Fixed = 0;
    obj.u64Var = 0;
    obj.u64Fixed = 0;
    obj.u64Tagged = 0;
    obj.u8Nullable = 0;
    obj.u16Nullable = 0;
    obj.u32VarNullable = 0L;
    obj.u32FixedNullable = 0L;
    obj.u64VarNullable = 0L;
    obj.u64FixedNullable = 0L;
    obj.u64TaggedNullable = 0L;
    return obj;
  }

  private static UnsignedSchemaConsistent createConsistentWithMaxValues() {
    UnsignedSchemaConsistent obj = new UnsignedSchemaConsistent();
    obj.u8 = UINT8_MAX;
    obj.u16 = UINT16_MAX;
    obj.u32Var = UINT32_MAX;
    obj.u32Fixed = UINT32_MAX;
    obj.u64Var = UINT64_MAX;
    obj.u64Fixed = UINT64_MAX;
    obj.u64Tagged = UINT64_MAX;
    obj.u8Nullable = UINT8_MAX;
    obj.u16Nullable = UINT16_MAX;
    obj.u32VarNullable = UINT32_MAX;
    obj.u32FixedNullable = UINT32_MAX;
    obj.u64VarNullable = UINT64_MAX;
    obj.u64FixedNullable = UINT64_MAX;
    obj.u64TaggedNullable = UINT64_MAX;
    return obj;
  }

  private static UnsignedSchemaConsistent createConsistentWithMidValues() {
    UnsignedSchemaConsistent obj = new UnsignedSchemaConsistent();
    obj.u8 = UINT8_MID;
    obj.u16 = UINT16_MID;
    obj.u32Var = UINT32_MID;
    obj.u32Fixed = UINT32_MID;
    obj.u64Var = UINT64_MID;
    obj.u64Fixed = UINT64_MID;
    obj.u64Tagged = UINT64_MID;
    obj.u8Nullable = UINT8_MID;
    obj.u16Nullable = UINT16_MID;
    obj.u32VarNullable = UINT32_MID;
    obj.u32FixedNullable = UINT32_MID;
    obj.u64VarNullable = UINT64_MID;
    obj.u64FixedNullable = UINT64_MID;
    obj.u64TaggedNullable = UINT64_MID;
    return obj;
  }

  private static UnsignedSchemaConsistent createConsistentWithNullValues() {
    UnsignedSchemaConsistent obj = new UnsignedSchemaConsistent();
    obj.u8 = 100;
    obj.u16 = 30000;
    obj.u32Var = 1500000000L;
    obj.u32Fixed = 2000000000L;
    obj.u64Var = 5000000000L;
    obj.u64Fixed = 7500000000L;
    obj.u64Tagged = 250000000L;
    obj.u8Nullable = null;
    obj.u16Nullable = null;
    obj.u32VarNullable = null;
    obj.u32FixedNullable = null;
    obj.u64VarNullable = null;
    obj.u64FixedNullable = null;
    obj.u64TaggedNullable = null;
    return obj;
  }

  private static UnsignedSchemaCompatible createCompatibleWithNormalValues() {
    UnsignedSchemaCompatible obj = new UnsignedSchemaCompatible();
    obj.u8 = 200;
    obj.u16 = 60000;
    obj.u32Var = 3000000000L;
    obj.u32Fixed = 4000000000L;
    obj.u64Var = 10000000000L;
    obj.u64Fixed = 15000000000L;
    obj.u64Tagged = 1000000000L;
    obj.u8Field2 = (short) 128;
    obj.u16Field2 = 40000;
    obj.u32VarField2 = 2500000000L;
    obj.u32FixedField2 = 3500000000L;
    obj.u64VarField2 = 8000000000L;
    obj.u64FixedField2 = 12000000000L;
    obj.u64TaggedField2 = 500000000L;
    return obj;
  }

  private static UnsignedSchemaCompatible createCompatibleWithZeroValues() {
    UnsignedSchemaCompatible obj = new UnsignedSchemaCompatible();
    obj.u8 = 0;
    obj.u16 = 0;
    obj.u32Var = 0;
    obj.u32Fixed = 0;
    obj.u64Var = 0;
    obj.u64Fixed = 0;
    obj.u64Tagged = 0;
    obj.u8Field2 = 0;
    obj.u16Field2 = 0;
    obj.u32VarField2 = 0L;
    obj.u32FixedField2 = 0L;
    obj.u64VarField2 = 0L;
    obj.u64FixedField2 = 0L;
    obj.u64TaggedField2 = 0L;
    return obj;
  }

  private static UnsignedSchemaCompatible createCompatibleWithMaxValues() {
    UnsignedSchemaCompatible obj = new UnsignedSchemaCompatible();
    obj.u8 = UINT8_MAX;
    obj.u16 = UINT16_MAX;
    obj.u32Var = UINT32_MAX;
    obj.u32Fixed = UINT32_MAX;
    obj.u64Var = UINT64_MAX;
    obj.u64Fixed = UINT64_MAX;
    obj.u64Tagged = UINT64_MAX;
    obj.u8Field2 = UINT8_MAX;
    obj.u16Field2 = UINT16_MAX;
    obj.u32VarField2 = UINT32_MAX;
    obj.u32FixedField2 = UINT32_MAX;
    obj.u64VarField2 = UINT64_MAX;
    obj.u64FixedField2 = UINT64_MAX;
    obj.u64TaggedField2 = UINT64_MAX;
    return obj;
  }

  private static UnsignedSchemaCompatible createCompatibleWithMidValues() {
    UnsignedSchemaCompatible obj = new UnsignedSchemaCompatible();
    obj.u8 = UINT8_MID;
    obj.u16 = UINT16_MID;
    obj.u32Var = UINT32_MID;
    obj.u32Fixed = UINT32_MID;
    obj.u64Var = UINT64_MID;
    obj.u64Fixed = UINT64_MID;
    obj.u64Tagged = UINT64_MID;
    obj.u8Field2 = UINT8_MID;
    obj.u16Field2 = UINT16_MID;
    obj.u32VarField2 = UINT32_MID;
    obj.u32FixedField2 = UINT32_MID;
    obj.u64VarField2 = UINT64_MID;
    obj.u64FixedField2 = UINT64_MID;
    obj.u64TaggedField2 = UINT64_MID;
    return obj;
  }

  private static UnsignedSchemaCompatible createCompatibleWithNullValues() {
    UnsignedSchemaCompatible obj = new UnsignedSchemaCompatible();
    obj.u8 = 100;
    obj.u16 = 30000;
    obj.u32Var = 1500000000L;
    obj.u32Fixed = 2000000000L;
    obj.u64Var = 5000000000L;
    obj.u64Fixed = 7500000000L;
    obj.u64Tagged = 250000000L;
    obj.u8Field2 = null;
    obj.u16Field2 = null;
    obj.u32VarField2 = null;
    obj.u32FixedField2 = null;
    obj.u64VarField2 = null;
    obj.u64FixedField2 = null;
    obj.u64TaggedField2 = null;
    return obj;
  }

  @DataProvider(name = "fory")
  public static Object[][] javaForyConfig() {
    ForyBuilder builder = Fory.builder().withXlang(false).requireClassRegistration(false);
    return new Object[][] {
      {builder.withCompatible(true).withCodegen(false).build()},
      {builder.withCompatible(true).withCodegen(true).build()},
      {builder.withCompatible(false).withCodegen(false).build()},
      {builder.withCompatible(false).withCodegen(true).build()}
    };
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaConsistentNormalValues(Fory fory) {
    serDeCheck(fory, createConsistentWithNormalValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaConsistentZeroValues(Fory fory) {
    serDeCheck(fory, createConsistentWithZeroValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaConsistentMaxValues(Fory fory) {
    serDeCheck(fory, createConsistentWithMaxValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaConsistentMidValues(Fory fory) {
    serDeCheck(fory, createConsistentWithMidValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaConsistentNullValues(Fory fory) {
    serDeCheck(fory, createConsistentWithNullValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaCompatibleNormalValues(Fory fory) {
    serDeCheck(fory, createCompatibleWithNormalValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaCompatibleZeroValues(Fory fory) {
    serDeCheck(fory, createCompatibleWithZeroValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaCompatibleMaxValues(Fory fory) {
    serDeCheck(fory, createCompatibleWithMaxValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaCompatibleMidValues(Fory fory) {
    serDeCheck(fory, createCompatibleWithMidValues());
  }

  @Test(dataProvider = "fory")
  public void testUnsignedSchemaCompatibleNullValues(Fory fory) {
    serDeCheck(fory, createCompatibleWithNullValues());
  }

  // Test specific edge cases for each unsigned type
  public static class Uint8OnlyStruct {
    @Uint8Type short value;

    @ForyField(nullable = true)
    @Uint8Type
    Short nullableValue;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Uint8OnlyStruct that = (Uint8OnlyStruct) o;
      return value == that.value && Objects.equals(nullableValue, that.nullableValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, nullableValue);
    }
  }

  @Test(dataProvider = "fory")
  public void testUint8EdgeCases(Fory fory) {
    // Test 0
    Uint8OnlyStruct zero = new Uint8OnlyStruct();
    zero.value = 0;
    zero.nullableValue = 0;
    serDeCheck(fory, zero);

    // Test 1
    Uint8OnlyStruct one = new Uint8OnlyStruct();
    one.value = 1;
    one.nullableValue = 1;
    serDeCheck(fory, one);

    // Test 127 (max signed byte)
    Uint8OnlyStruct maxSignedByte = new Uint8OnlyStruct();
    maxSignedByte.value = 127;
    maxSignedByte.nullableValue = 127;
    serDeCheck(fory, maxSignedByte);

    // Test 128 (min negative as unsigned)
    Uint8OnlyStruct minUnsignedOver127 = new Uint8OnlyStruct();
    minUnsignedOver127.value = 128;
    minUnsignedOver127.nullableValue = 128;
    serDeCheck(fory, minUnsignedOver127);

    // Test 255 (max uint8)
    Uint8OnlyStruct maxUint8 = new Uint8OnlyStruct();
    maxUint8.value = 255;
    maxUint8.nullableValue = 255;
    serDeCheck(fory, maxUint8);

    // Test null
    Uint8OnlyStruct withNull = new Uint8OnlyStruct();
    withNull.value = 200;
    withNull.nullableValue = null;
    serDeCheck(fory, withNull);
  }

  public static class Uint16OnlyStruct {
    @Uint16Type int value;

    @ForyField(nullable = true)
    @Uint16Type
    Integer nullableValue;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Uint16OnlyStruct that = (Uint16OnlyStruct) o;
      return value == that.value && Objects.equals(nullableValue, that.nullableValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, nullableValue);
    }
  }

  @Test(dataProvider = "fory")
  public void testUint16EdgeCases(Fory fory) {
    // Test 0
    Uint16OnlyStruct zero = new Uint16OnlyStruct();
    zero.value = 0;
    zero.nullableValue = 0;
    serDeCheck(fory, zero);

    // Test 1
    Uint16OnlyStruct one = new Uint16OnlyStruct();
    one.value = 1;
    one.nullableValue = 1;
    serDeCheck(fory, one);

    // Test 32767 (max signed short)
    Uint16OnlyStruct maxSignedShort = new Uint16OnlyStruct();
    maxSignedShort.value = 32767;
    maxSignedShort.nullableValue = 32767;
    serDeCheck(fory, maxSignedShort);

    // Test 32768 (min unsigned over signed max)
    Uint16OnlyStruct minUnsignedOver32767 = new Uint16OnlyStruct();
    minUnsignedOver32767.value = 32768;
    minUnsignedOver32767.nullableValue = 32768;
    serDeCheck(fory, minUnsignedOver32767);

    // Test 65535 (max uint16)
    Uint16OnlyStruct maxUint16 = new Uint16OnlyStruct();
    maxUint16.value = 65535;
    maxUint16.nullableValue = 65535;
    serDeCheck(fory, maxUint16);

    // Test null
    Uint16OnlyStruct withNull = new Uint16OnlyStruct();
    withNull.value = 50000;
    withNull.nullableValue = null;
    serDeCheck(fory, withNull);
  }

  public static class Uint32OnlyStruct {
    @Uint32Type(compress = true)
    long varValue;

    @Uint32Type(compress = false)
    long fixedValue;

    @ForyField(nullable = true)
    @Uint32Type(compress = true)
    Long varNullableValue;

    @ForyField(nullable = true)
    @Uint32Type(compress = false)
    Long fixedNullableValue;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Uint32OnlyStruct that = (Uint32OnlyStruct) o;
      return varValue == that.varValue
          && fixedValue == that.fixedValue
          && Objects.equals(varNullableValue, that.varNullableValue)
          && Objects.equals(fixedNullableValue, that.fixedNullableValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(varValue, fixedValue, varNullableValue, fixedNullableValue);
    }
  }

  @Test(dataProvider = "fory")
  public void testUint32EdgeCases(Fory fory) {
    // Test 0
    Uint32OnlyStruct zero = new Uint32OnlyStruct();
    zero.varValue = 0;
    zero.fixedValue = 0;
    zero.varNullableValue = 0L;
    zero.fixedNullableValue = 0L;
    serDeCheck(fory, zero);

    // Test 1
    Uint32OnlyStruct one = new Uint32OnlyStruct();
    one.varValue = 1;
    one.fixedValue = 1;
    one.varNullableValue = 1L;
    one.fixedNullableValue = 1L;
    serDeCheck(fory, one);

    // Test 2147483647 (max signed int)
    Uint32OnlyStruct maxSignedInt = new Uint32OnlyStruct();
    maxSignedInt.varValue = 2147483647L;
    maxSignedInt.fixedValue = 2147483647L;
    maxSignedInt.varNullableValue = 2147483647L;
    maxSignedInt.fixedNullableValue = 2147483647L;
    serDeCheck(fory, maxSignedInt);

    // Test 2147483648 (min unsigned over signed max)
    Uint32OnlyStruct minUnsignedOver = new Uint32OnlyStruct();
    minUnsignedOver.varValue = 2147483648L;
    minUnsignedOver.fixedValue = 2147483648L;
    minUnsignedOver.varNullableValue = 2147483648L;
    minUnsignedOver.fixedNullableValue = 2147483648L;
    serDeCheck(fory, minUnsignedOver);

    // Test 4294967295 (max uint32)
    Uint32OnlyStruct maxUint32 = new Uint32OnlyStruct();
    maxUint32.varValue = 4294967295L;
    maxUint32.fixedValue = 4294967295L;
    maxUint32.varNullableValue = 4294967295L;
    maxUint32.fixedNullableValue = 4294967295L;
    serDeCheck(fory, maxUint32);

    // Test null
    Uint32OnlyStruct withNull = new Uint32OnlyStruct();
    withNull.varValue = 3000000000L;
    withNull.fixedValue = 3000000000L;
    withNull.varNullableValue = null;
    withNull.fixedNullableValue = null;
    serDeCheck(fory, withNull);
  }

  public static class Uint64OnlyStruct {
    @Uint64Type(encoding = LongEncoding.VARINT64)
    long varValue;

    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    long fixedValue;

    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    long taggedValue;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.VARINT64)
    Long varNullableValue;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.FIXED_INT64)
    Long fixedNullableValue;

    @ForyField(nullable = true)
    @Uint64Type(encoding = LongEncoding.TAGGED_INT64)
    Long taggedNullableValue;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Uint64OnlyStruct that = (Uint64OnlyStruct) o;
      return varValue == that.varValue
          && fixedValue == that.fixedValue
          && taggedValue == that.taggedValue
          && Objects.equals(varNullableValue, that.varNullableValue)
          && Objects.equals(fixedNullableValue, that.fixedNullableValue)
          && Objects.equals(taggedNullableValue, that.taggedNullableValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          varValue,
          fixedValue,
          taggedValue,
          varNullableValue,
          fixedNullableValue,
          taggedNullableValue);
    }
  }

  @Test(dataProvider = "fory")
  public void testUint64EdgeCases(Fory fory) {
    // Test 0
    Uint64OnlyStruct zero = new Uint64OnlyStruct();
    zero.varValue = 0;
    zero.fixedValue = 0;
    zero.taggedValue = 0;
    zero.varNullableValue = 0L;
    zero.fixedNullableValue = 0L;
    zero.taggedNullableValue = 0L;
    serDeCheck(fory, zero);

    // Test 1
    Uint64OnlyStruct one = new Uint64OnlyStruct();
    one.varValue = 1;
    one.fixedValue = 1;
    one.taggedValue = 1;
    one.varNullableValue = 1L;
    one.fixedNullableValue = 1L;
    one.taggedNullableValue = 1L;
    serDeCheck(fory, one);

    // Test Long.MAX_VALUE (max signed long)
    Uint64OnlyStruct maxSignedLong = new Uint64OnlyStruct();
    maxSignedLong.varValue = Long.MAX_VALUE;
    maxSignedLong.fixedValue = Long.MAX_VALUE;
    maxSignedLong.taggedValue = Long.MAX_VALUE;
    maxSignedLong.varNullableValue = Long.MAX_VALUE;
    maxSignedLong.fixedNullableValue = Long.MAX_VALUE;
    maxSignedLong.taggedNullableValue = Long.MAX_VALUE;
    serDeCheck(fory, maxSignedLong);

    // Test Long.MIN_VALUE (this represents 2^63 as unsigned)
    Uint64OnlyStruct minValue = new Uint64OnlyStruct();
    minValue.varValue = Long.MIN_VALUE;
    minValue.fixedValue = Long.MIN_VALUE;
    minValue.taggedValue = Long.MIN_VALUE;
    minValue.varNullableValue = Long.MIN_VALUE;
    minValue.fixedNullableValue = Long.MIN_VALUE;
    minValue.taggedNullableValue = Long.MIN_VALUE;
    serDeCheck(fory, minValue);

    // Test -1 (this represents max uint64: 0xFFFFFFFFFFFFFFFF)
    Uint64OnlyStruct maxUint64 = new Uint64OnlyStruct();
    maxUint64.varValue = -1L;
    maxUint64.fixedValue = -1L;
    maxUint64.taggedValue = -1L;
    maxUint64.varNullableValue = -1L;
    maxUint64.fixedNullableValue = -1L;
    maxUint64.taggedNullableValue = -1L;
    serDeCheck(fory, maxUint64);

    // Test null
    Uint64OnlyStruct withNull = new Uint64OnlyStruct();
    withNull.varValue = 10000000000L;
    withNull.fixedValue = 10000000000L;
    withNull.taggedValue = 10000000000L;
    withNull.varNullableValue = null;
    withNull.fixedNullableValue = null;
    withNull.taggedNullableValue = null;
    serDeCheck(fory, withNull);
  }

  // Test tagged encoding boundary values
  @Test(dataProvider = "fory")
  public void testTaggedEncodingBoundaryValues(Fory fory) {
    Uint64OnlyStruct obj = new Uint64OnlyStruct();

    // Test value at tagged 4-byte boundary: -1073741824 (HALF_MIN_INT_VALUE)
    obj.varValue = -1073741824L;
    obj.fixedValue = -1073741824L;
    obj.taggedValue = -1073741824L;
    obj.varNullableValue = -1073741824L;
    obj.fixedNullableValue = -1073741824L;
    obj.taggedNullableValue = -1073741824L;
    serDeCheck(fory, obj);

    // Test value at tagged 4-byte boundary: 1073741823 (HALF_MAX_INT_VALUE)
    obj.varValue = 1073741823L;
    obj.fixedValue = 1073741823L;
    obj.taggedValue = 1073741823L;
    obj.varNullableValue = 1073741823L;
    obj.fixedNullableValue = 1073741823L;
    obj.taggedNullableValue = 1073741823L;
    serDeCheck(fory, obj);

    // Test value just below tagged 4-byte boundary
    obj.varValue = -1073741825L;
    obj.fixedValue = -1073741825L;
    obj.taggedValue = -1073741825L;
    obj.varNullableValue = -1073741825L;
    obj.fixedNullableValue = -1073741825L;
    obj.taggedNullableValue = -1073741825L;
    serDeCheck(fory, obj);

    // Test value just above tagged 4-byte boundary
    obj.varValue = 1073741824L;
    obj.fixedValue = 1073741824L;
    obj.taggedValue = 1073741824L;
    obj.varNullableValue = 1073741824L;
    obj.fixedNullableValue = 1073741824L;
    obj.taggedNullableValue = 1073741824L;
    serDeCheck(fory, obj);
  }

  // Test varint encoding boundary values
  @Test(dataProvider = "fory")
  public void testVarintEncodingBoundaryValues(Fory fory) {
    Uint32OnlyStruct obj32 = new Uint32OnlyStruct();

    // 1-byte varint boundary (0-127)
    obj32.varValue = 127;
    obj32.fixedValue = 127;
    obj32.varNullableValue = 127L;
    obj32.fixedNullableValue = 127L;
    serDeCheck(fory, obj32);

    // 2-byte varint boundary (128-16383)
    obj32.varValue = 128;
    obj32.fixedValue = 128;
    obj32.varNullableValue = 128L;
    obj32.fixedNullableValue = 128L;
    serDeCheck(fory, obj32);

    obj32.varValue = 16383;
    obj32.fixedValue = 16383;
    obj32.varNullableValue = 16383L;
    obj32.fixedNullableValue = 16383L;
    serDeCheck(fory, obj32);

    // 3-byte varint boundary (16384-2097151)
    obj32.varValue = 16384;
    obj32.fixedValue = 16384;
    obj32.varNullableValue = 16384L;
    obj32.fixedNullableValue = 16384L;
    serDeCheck(fory, obj32);

    obj32.varValue = 2097151;
    obj32.fixedValue = 2097151;
    obj32.varNullableValue = 2097151L;
    obj32.fixedNullableValue = 2097151L;
    serDeCheck(fory, obj32);

    // 4-byte varint boundary (2097152-268435455)
    obj32.varValue = 2097152;
    obj32.fixedValue = 2097152;
    obj32.varNullableValue = 2097152L;
    obj32.fixedNullableValue = 2097152L;
    serDeCheck(fory, obj32);

    obj32.varValue = 268435455;
    obj32.fixedValue = 268435455;
    obj32.varNullableValue = 268435455L;
    obj32.fixedNullableValue = 268435455L;
    serDeCheck(fory, obj32);

    // 5-byte varint boundary (268435456+)
    obj32.varValue = 268435456;
    obj32.fixedValue = 268435456;
    obj32.varNullableValue = 268435456L;
    obj32.fixedNullableValue = 268435456L;
    serDeCheck(fory, obj32);
  }
}
