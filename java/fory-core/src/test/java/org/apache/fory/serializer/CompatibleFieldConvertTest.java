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

import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.Nullable;
import org.apache.fory.annotation.Ref;
import org.apache.fory.annotation.UInt64Type;
import org.apache.fory.config.Int64Encoding;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.serializer.converter.FieldConverter;
import org.apache.fory.serializer.converter.FieldConverters;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.Float16;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CompatibleFieldConvertTest extends ForyTestBase {
  public static final class CompatibleFieldConvert1 {
    public boolean ftrue;
    public Boolean ffalse;
    public byte f3;
    public Byte f4;
    public short f5;
    public Short f6;
    public int f7;
    public Integer f8;
    public long f9;
    public Long f10;
    public float f11;
    public Float f12;
    public double f13;
    public Double f14;

    public String toString() {
      return "" + ftrue + ffalse + f3 + f4 + f5 + f6 + f7 + f8 + f9 + f10 + f11 + f12 + f13 + f14;
    }
  }

  public static final class CompatibleFieldConvert2 {
    public Boolean ftrue;
    public boolean ffalse;
    public Byte f3;
    public byte f4;
    public Short f5;
    public short f6;
    public Integer f7;
    public int f8;
    public Long f9;
    public long f10;
    public Float f11;
    public float f12;
    public Double f13;
    public double f14;

    public String toString() {
      return "" + ftrue + ffalse + f3 + f4 + f5 + f6 + f7 + f8 + f9 + f10 + f11 + f12 + f13 + f14;
    }
  }

  public static final class CompatibleFieldConvert3 {
    public String ftrue;
    public String ffalse;
    public String f3;
    public String f4;
    public String f5;
    public String f6;
    public String f7;
    public String f8;
    public String f9;
    public String f10;
    public String f11;
    public String f12;
    public String f13;
    public String f14;

    public String toString() {
      return ftrue + ffalse + f3 + f4 + f5 + f6 + f7 + f8 + f9 + f10 + f11 + f12 + f13 + f14;
    }
  }

  public static final class ValidScalarWriter {
    @ForyField(id = 0)
    public String boolText = "true";

    @ForyField(id = 1)
    public int boolNumber = 1;

    @ForyField(id = 2)
    public boolean stringBool = true;

    @ForyField(id = 3)
    public long narrowInt = 127L;

    @ForyField(id = 4)
    public String intText = "1e2";

    @ForyField(id = 5)
    public float exactFloat = 0.5f;

    @ForyField(id = 6)
    public BigDecimal decimalInt = new BigDecimal("1.0");

    @ForyField(id = 7)
    public int decimalFromInt = 5;

    @Nullable
    @ForyField(id = 8)
    public String nullableBool = null;

    @UInt64Type(encoding = Int64Encoding.FIXED)
    @ForyField(id = 9)
    public long unsignedValue = Long.MAX_VALUE;

    @ForyField(id = 10)
    public boolean trueNumber = true;

    @ForyField(id = 11)
    public boolean falseNumber = false;
  }

  public static final class ValidScalarReader {
    @ForyField(id = 0)
    public boolean boolText;

    @ForyField(id = 1)
    public boolean boolNumber;

    @ForyField(id = 2)
    public String stringBool;

    @ForyField(id = 3)
    public byte narrowInt;

    @ForyField(id = 4)
    public int intText;

    @ForyField(id = 5)
    public String exactFloat;

    @ForyField(id = 6)
    public int decimalInt;

    @ForyField(id = 7)
    public BigDecimal decimalFromInt;

    @ForyField(id = 8)
    public boolean nullableBool;

    @ForyField(id = 9)
    public long unsignedValue;

    @ForyField(id = 10)
    public int trueNumber;

    @ForyField(id = 11)
    public int falseNumber;
  }

  public static final class StringBoolWriter {
    @ForyField(id = 0)
    public String value = "TRUE";
  }

  public static final class RefStringBoolWriter {
    @Ref
    @ForyField(id = 0)
    public String value = "true";
  }

  public static final class RefBooleanReader {
    @Ref
    @ForyField(id = 0)
    public Boolean value;
  }

  public static final class BoolReader {
    @ForyField(id = 0)
    public boolean value;
  }

  public static final class IntBoolWriter {
    @ForyField(id = 0)
    public int value = 2;
  }

  public static final class LongByteWriter {
    @ForyField(id = 0)
    public long value = 128L;
  }

  public static final class ByteReader {
    @ForyField(id = 0)
    public byte value;
  }

  public static final class StringFloatWriter {
    @ForyField(id = 0)
    public String value = "0.1";
  }

  public static final class FloatReader {
    @ForyField(id = 0)
    public float value;
  }

  public static final class FloatIntWriter {
    @ForyField(id = 0)
    public float value = 0.5f;
  }

  public static final class IntReader {
    @ForyField(id = 0)
    public int value;
  }

  public static final class DecimalIntWriter {
    @ForyField(id = 0)
    public BigDecimal value = new BigDecimal("0.5");
  }

  public static final class NanStringWriter {
    @ForyField(id = 0)
    public double value = Double.NaN;
  }

  public static final class StringReader {
    @ForyField(id = 0)
    public String value;
  }

  public static final class UnsignedLongWriter {
    @UInt64Type(encoding = Int64Encoding.FIXED)
    @ForyField(id = 0)
    public long value = -1L;
  }

  public static final class SignedLongWriter {
    @ForyField(id = 0)
    public long value = -1L;
  }

  public static final class LongReader {
    @ForyField(id = 0)
    public long value;
  }

  public static final class UnsignedLongReader {
    @UInt64Type(encoding = Int64Encoding.FIXED)
    @ForyField(id = 0)
    public long value;
  }

  public static final class HugeDecimalStringWriter {
    @ForyField(id = 0)
    public String value = "1e4097";
  }

  public static final class HugeExponentStringWriter {
    @ForyField(id = 0)
    public String value = "1e2147483647";
  }

  public static final class DecimalReader {
    @ForyField(id = 0)
    public BigDecimal value;
  }

  public static final class Float16Writer {
    @ForyField(id = 0)
    public Float16 value = Float16.ONE;
  }

  public static final class Float16NanWriter {
    @ForyField(id = 0)
    public Float16 value = Float16.NaN;
  }

  public static final class BFloat16Reader {
    @ForyField(id = 0)
    public BFloat16 value;
  }

  public static final class StringObjectWriter {
    @ForyField(id = 0)
    public String value = "hello";
  }

  public static final class ObjectReader {
    @ForyField(id = 0)
    public Object value;
  }

  public static final class IntegerNumberWriter {
    @ForyField(id = 0)
    public Integer value = 7;
  }

  public static final class NumberReader {
    @ForyField(id = 0)
    public Number value;
  }

  @DataProvider
  public static Object[][] xlangAndCodegen() {
    return new Object[][] {{false, false}, {false, true}, {true, false}, {true, true}};
  }

  @DataProvider
  public static Object[][] codegenModes() {
    return new Object[][] {{false}, {true}};
  }

  @Test(dataProvider = "xlang")
  public void testCompatibleFieldConvert(boolean xlang) throws Exception {
    byte[] bytes;
    Object o1;
    ImmutableSet<String> floatFields = ImmutableSet.of("f11", "f12", "f13", "f14");
    {
      Class<?> cls = CompatibleFieldConvert1.class;
      o1 = cls.newInstance();
      for (Field field : ReflectionUtils.getSortedFields(cls, false)) {
        String name = field.getName();
        field.setAccessible(true);
        FieldConverter<?> converter = FieldConverters.getConverter(String.class, field);
        Assert.assertNotNull(converter);
        Object converted = converter.convert(name.substring(1));
        field.set(o1, converted);
      }
      Fory fory = builder().withXlang(xlang).withCompatible(true).build();
      fory.register(cls);
      bytes = fory.serialize(o1);
    }
    {
      Class<?> cls = CompatibleFieldConvert2.class;
      Assert.assertNotEquals(o1.getClass(), cls);
      Fory fory = builder().withXlang(xlang).withCompatible(true).build();
      fory.register(cls);
      Object o = fory.deserialize(bytes);
      Assert.assertEquals(o.getClass(), cls);
      List<Field> fields = ReflectionUtils.getSortedFields(cls, false);
      for (Field field : fields) {
        field.setAccessible(true);
        Object fieldValue = field.get(o);
        if (fieldValue instanceof Float || fieldValue instanceof Double) {
          Assert.assertEquals(fieldValue.toString(), field.getName().substring(1) + ".0");
        } else {
          Assert.assertEquals(
              fieldValue.toString(), field.getName().substring(1), field.getName() + " not equal");
        }
      }
      Assert.assertEquals(o.toString(), o1.toString());
    }
    {
      Fory fory = builder().withXlang(xlang).withCompatible(true).build();
      Class<?> cls = CompatibleFieldConvert3.class;
      Assert.assertNotEquals(o1.getClass(), cls);
      fory.register(cls);
      Object o = fory.deserialize(bytes);
      Assert.assertEquals(o.getClass(), cls);
      List<Field> fields = ReflectionUtils.getSortedFields(cls, false);
      for (Field field : fields) {
        field.setAccessible(true);
        Object fieldValue = field.get(o);
        if (floatFields.contains(field.getName())) {
          Assert.assertEquals(fieldValue.toString(), field.getName().substring(1) + ".0");
        } else {
          Assert.assertEquals(fieldValue.toString(), field.getName().substring(1));
        }
      }
      Assert.assertEquals(o.toString(), o1.toString());
    }
  }

  @Test(dataProvider = "xlangAndCodegen")
  public void testScalarConversions(boolean xlang, boolean codegen) {
    ValidScalarReader reader =
        readAs(new ValidScalarWriter(), ValidScalarReader.class, xlang, codegen);
    Assert.assertTrue(reader.boolText);
    Assert.assertTrue(reader.boolNumber);
    Assert.assertEquals(reader.stringBool, "true");
    Assert.assertEquals(reader.narrowInt, 127);
    Assert.assertEquals(reader.intText, 100);
    Assert.assertEquals(reader.exactFloat, "0.5");
    Assert.assertEquals(reader.decimalInt, 1);
    Assert.assertEquals(reader.decimalFromInt, BigDecimal.valueOf(5));
    Assert.assertFalse(reader.nullableBool);
    Assert.assertEquals(reader.unsignedValue, Long.MAX_VALUE);
    Assert.assertEquals(reader.trueNumber, 1);
    Assert.assertEquals(reader.falseNumber, 0);

    BFloat16Reader bfloat16Reader =
        readAs(new Float16Writer(), BFloat16Reader.class, xlang, codegen);
    Assert.assertEquals(bfloat16Reader.value, BFloat16.ONE);
  }

  @Test(dataProvider = "codegenModes")
  public void testScalarAssignableSupertype(boolean codegen) {
    ObjectReader objectReader =
        readAs(new StringObjectWriter(), ObjectReader.class, false, codegen);
    Assert.assertEquals(objectReader.value, "hello");

    NumberReader numberReader =
        readAs(new IntegerNumberWriter(), NumberReader.class, false, codegen);
    Assert.assertEquals(numberReader.value, 7);
  }

  @Test(dataProvider = "codegenModes")
  public void testScalarTrackingRefConversionRejected(boolean codegen) {
    Fory writer =
        Fory.builder()
            .withXlang(true)
            .withCompatible(true)
            .withCodegen(codegen)
            .withRefTracking(true)
            .build();
    writer.register(RefStringBoolWriter.class, 28000);
    byte[] bytes = writer.serialize(new RefStringBoolWriter());

    Fory reader =
        Fory.builder()
            .withXlang(true)
            .withCompatible(true)
            .withCodegen(codegen)
            .withRefTracking(true)
            .build();
    reader.register(BoolReader.class, 28000);
    BoolReader value = (BoolReader) reader.deserialize(bytes);
    Assert.assertFalse(value.value);
  }

  @Test
  public void testScalarTrackingRefClassifier() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withRefTracking(true).build();
    SerializationFieldInfo remoteRef =
        new SerializationFieldInfo(
            fory.getTypeResolver(),
            Descriptor.getDescriptorsMap(RefStringBoolWriter.class).get("value"));
    SerializationFieldInfo localBool =
        new SerializationFieldInfo(
            fory.getTypeResolver(), Descriptor.getDescriptorsMap(BoolReader.class).get("value"));
    SerializationFieldInfo localRef =
        new SerializationFieldInfo(
            fory.getTypeResolver(),
            Descriptor.getDescriptorsMap(RefBooleanReader.class).get("value"));
    Assert.assertTrue(remoteRef.trackingRef);
    Assert.assertTrue(localRef.trackingRef);
    Assert.assertFalse(FieldConverters.canConvert(remoteRef, localBool));
    Assert.assertFalse(FieldConverters.canConvert(localBool, localRef));
    Assert.assertTrue(FieldConverters.canConvert(localRef, localRef));
  }

  @Test(dataProvider = "xlangAndCodegen")
  public void testScalarConversionFailures(boolean xlang, boolean codegen) {
    assertConversionFails(new StringBoolWriter(), BoolReader.class, xlang, codegen);
    assertConversionFails(new IntBoolWriter(), BoolReader.class, xlang, codegen);
    assertConversionFails(new LongByteWriter(), ByteReader.class, xlang, codegen);
    assertConversionFails(new StringFloatWriter(), FloatReader.class, xlang, codegen);
    assertConversionFails(new FloatIntWriter(), IntReader.class, xlang, codegen);
    assertConversionFails(new DecimalIntWriter(), IntReader.class, xlang, codegen);
    assertConversionFails(new NanStringWriter(), StringReader.class, xlang, codegen);
    assertConversionFails(new UnsignedLongWriter(), IntReader.class, xlang, codegen);
    assertConversionFails(new UnsignedLongWriter(), LongReader.class, xlang, codegen);
    assertConversionFails(new SignedLongWriter(), UnsignedLongReader.class, xlang, codegen);
    assertConversionFails(new HugeDecimalStringWriter(), DecimalReader.class, xlang, codegen);
    assertConversionFails(new HugeExponentStringWriter(), DecimalReader.class, xlang, codegen);
    assertConversionFails(new Float16NanWriter(), BFloat16Reader.class, xlang, codegen);
  }

  private static <T> T readAs(
      Object writerObject, Class<T> readerClass, boolean xlang, boolean codegen) {
    Fory writer = compatibleFory(xlang, codegen);
    writer.register(writerObject.getClass(), 28000);
    byte[] bytes = writer.serialize(writerObject);
    Fory reader = compatibleFory(xlang, codegen);
    reader.register(readerClass, 28000);
    return readerClass.cast(reader.deserialize(bytes));
  }

  private static void assertConversionFails(
      Object writerObject, Class<?> readerClass, boolean xlang, boolean codegen) {
    Assert.assertThrows(
        DeserializationException.class, () -> readAs(writerObject, readerClass, xlang, codegen));
  }

  private static Fory compatibleFory(boolean xlang, boolean codegen) {
    return Fory.builder().withXlang(xlang).withCompatible(true).withCodegen(codegen).build();
  }
}
