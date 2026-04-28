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

package org.apache.fory.idl_tests;

import example_common.ExampleLeaf;
import example_common.ExampleLeafUnion;
import example_common.ExampleState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.fory.Fory;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.Int32Type;
import org.apache.fory.annotation.Int64Type;
import org.apache.fory.annotation.Ref;
import org.apache.fory.annotation.Uint16Type;
import org.apache.fory.annotation.Uint32Type;
import org.apache.fory.annotation.Uint64Type;
import org.apache.fory.annotation.Uint8Type;
import org.apache.fory.collection.BFloat16List;
import org.apache.fory.collection.Float16List;
import org.apache.fory.collection.Int16List;
import org.apache.fory.collection.Int32List;
import org.apache.fory.collection.Int64List;
import org.apache.fory.collection.Int8List;
import org.apache.fory.collection.Uint16List;
import org.apache.fory.collection.Uint32List;
import org.apache.fory.collection.Uint64List;
import org.apache.fory.collection.Uint8List;
import org.apache.fory.config.LongEncoding;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Float16;

final class ExampleSchemaEvolutionTypes {
  static final int EXAMPLE_MESSAGE_TYPE_ID = 1500;
  private static final Class<?> EMPTY_TYPE = ExampleMessageEmpty.class;
  private static final List<ExampleVariantSpec> VARIANT_SPECS =
      Collections.unmodifiableList(
          Arrays.asList(
              new ExampleVariantSpec(1, "boolValue", ExampleFieldBoolValue.class),
              new ExampleVariantSpec(2, "int8Value", ExampleFieldInt8Value.class),
              new ExampleVariantSpec(3, "int16Value", ExampleFieldInt16Value.class),
              new ExampleVariantSpec(4, "fixedInt32Value", ExampleFieldFixedInt32Value.class),
              new ExampleVariantSpec(5, "varint32Value", ExampleFieldVarint32Value.class),
              new ExampleVariantSpec(6, "fixedInt64Value", ExampleFieldFixedInt64Value.class),
              new ExampleVariantSpec(7, "varint64Value", ExampleFieldVarint64Value.class),
              new ExampleVariantSpec(8, "taggedInt64Value", ExampleFieldTaggedInt64Value.class),
              new ExampleVariantSpec(9, "uint8Value", ExampleFieldUint8Value.class),
              new ExampleVariantSpec(10, "uint16Value", ExampleFieldUint16Value.class),
              new ExampleVariantSpec(11, "fixedUint32Value", ExampleFieldFixedUint32Value.class),
              new ExampleVariantSpec(12, "varUint32Value", ExampleFieldVarUint32Value.class),
              new ExampleVariantSpec(13, "fixedUint64Value", ExampleFieldFixedUint64Value.class),
              new ExampleVariantSpec(14, "varUint64Value", ExampleFieldVarUint64Value.class),
              new ExampleVariantSpec(15, "taggedUint64Value", ExampleFieldTaggedUint64Value.class),
              new ExampleVariantSpec(16, "float16Value", ExampleFieldFloat16Value.class),
              new ExampleVariantSpec(17, "bfloat16Value", ExampleFieldBfloat16Value.class),
              new ExampleVariantSpec(18, "float32Value", ExampleFieldFloat32Value.class),
              new ExampleVariantSpec(19, "float64Value", ExampleFieldFloat64Value.class),
              new ExampleVariantSpec(20, "stringValue", ExampleFieldStringValue.class),
              new ExampleVariantSpec(21, "bytesValue", ExampleFieldBytesValue.class),
              new ExampleVariantSpec(22, "dateValue", ExampleFieldDateValue.class),
              new ExampleVariantSpec(23, "timestampValue", ExampleFieldTimestampValue.class),
              new ExampleVariantSpec(24, "durationValue", ExampleFieldDurationValue.class),
              new ExampleVariantSpec(25, "decimalValue", ExampleFieldDecimalValue.class),
              new ExampleVariantSpec(26, "enumValue", ExampleFieldEnumValue.class),
              new ExampleVariantSpec(27, "messageValue", ExampleFieldMessageValue.class),
              new ExampleVariantSpec(28, "unionValue", ExampleFieldUnionValue.class),
              new ExampleVariantSpec(101, "boolList", ExampleFieldBoolList.class),
              new ExampleVariantSpec(102, "int8List", ExampleFieldInt8List.class),
              new ExampleVariantSpec(103, "int16List", ExampleFieldInt16List.class),
              new ExampleVariantSpec(104, "fixedInt32List", ExampleFieldFixedInt32List.class),
              new ExampleVariantSpec(105, "varint32List", ExampleFieldVarint32List.class),
              new ExampleVariantSpec(106, "fixedInt64List", ExampleFieldFixedInt64List.class),
              new ExampleVariantSpec(107, "varint64List", ExampleFieldVarint64List.class),
              new ExampleVariantSpec(108, "taggedInt64List", ExampleFieldTaggedInt64List.class),
              new ExampleVariantSpec(109, "uint8List", ExampleFieldUint8List.class),
              new ExampleVariantSpec(110, "uint16List", ExampleFieldUint16List.class),
              new ExampleVariantSpec(111, "fixedUint32List", ExampleFieldFixedUint32List.class),
              new ExampleVariantSpec(112, "varUint32List", ExampleFieldVarUint32List.class),
              new ExampleVariantSpec(113, "fixedUint64List", ExampleFieldFixedUint64List.class),
              new ExampleVariantSpec(114, "varUint64List", ExampleFieldVarUint64List.class),
              new ExampleVariantSpec(115, "taggedUint64List", ExampleFieldTaggedUint64List.class),
              new ExampleVariantSpec(116, "float16List", ExampleFieldFloat16List.class),
              new ExampleVariantSpec(117, "bfloat16List", ExampleFieldBfloat16List.class),
              new ExampleVariantSpec(118, "maybeFloat16List", ExampleFieldMaybeFloat16List.class),
              new ExampleVariantSpec(
                  119, "maybeBfloat16List", ExampleFieldMaybeBfloat16List.class),
              new ExampleVariantSpec(120, "float32List", ExampleFieldFloat32List.class),
              new ExampleVariantSpec(121, "float64List", ExampleFieldFloat64List.class),
              new ExampleVariantSpec(122, "stringList", ExampleFieldStringList.class),
              new ExampleVariantSpec(123, "bytesList", ExampleFieldBytesList.class),
              new ExampleVariantSpec(124, "dateList", ExampleFieldDateList.class),
              new ExampleVariantSpec(125, "timestampList", ExampleFieldTimestampList.class),
              new ExampleVariantSpec(126, "durationList", ExampleFieldDurationList.class),
              new ExampleVariantSpec(127, "decimalList", ExampleFieldDecimalList.class),
              new ExampleVariantSpec(128, "enumList", ExampleFieldEnumList.class),
              new ExampleVariantSpec(129, "messageList", ExampleFieldMessageList.class),
              new ExampleVariantSpec(130, "unionList", ExampleFieldUnionList.class),
              new ExampleVariantSpec(
                  201, "stringValuesByBool", ExampleFieldStringValuesByBool.class),
              new ExampleVariantSpec(
                  202, "stringValuesByInt8", ExampleFieldStringValuesByInt8.class),
              new ExampleVariantSpec(
                  203, "stringValuesByInt16", ExampleFieldStringValuesByInt16.class),
              new ExampleVariantSpec(
                  204, "stringValuesByFixedInt32", ExampleFieldStringValuesByFixedInt32.class),
              new ExampleVariantSpec(
                  205, "stringValuesByVarint32", ExampleFieldStringValuesByVarint32.class),
              new ExampleVariantSpec(
                  206, "stringValuesByFixedInt64", ExampleFieldStringValuesByFixedInt64.class),
              new ExampleVariantSpec(
                  207, "stringValuesByVarint64", ExampleFieldStringValuesByVarint64.class),
              new ExampleVariantSpec(
                  208, "stringValuesByTaggedInt64", ExampleFieldStringValuesByTaggedInt64.class),
              new ExampleVariantSpec(
                  209, "stringValuesByUint8", ExampleFieldStringValuesByUint8.class),
              new ExampleVariantSpec(
                  210, "stringValuesByUint16", ExampleFieldStringValuesByUint16.class),
              new ExampleVariantSpec(
                  211, "stringValuesByFixedUint32", ExampleFieldStringValuesByFixedUint32.class),
              new ExampleVariantSpec(
                  212, "stringValuesByVarUint32", ExampleFieldStringValuesByVarUint32.class),
              new ExampleVariantSpec(
                  213, "stringValuesByFixedUint64", ExampleFieldStringValuesByFixedUint64.class),
              new ExampleVariantSpec(
                  214, "stringValuesByVarUint64", ExampleFieldStringValuesByVarUint64.class),
              new ExampleVariantSpec(
                  215, "stringValuesByTaggedUint64", ExampleFieldStringValuesByTaggedUint64.class),
              new ExampleVariantSpec(
                  218, "stringValuesByString", ExampleFieldStringValuesByString.class),
              new ExampleVariantSpec(
                  219, "stringValuesByTimestamp", ExampleFieldStringValuesByTimestamp.class),
              new ExampleVariantSpec(
                  220, "stringValuesByDuration", ExampleFieldStringValuesByDuration.class),
              new ExampleVariantSpec(
                  221, "stringValuesByEnum", ExampleFieldStringValuesByEnum.class),
              new ExampleVariantSpec(
                  222, "float16ValuesByName", ExampleFieldFloat16ValuesByName.class),
              new ExampleVariantSpec(
                  223,
                  "maybeFloat16ValuesByName",
                  ExampleFieldMaybeFloat16ValuesByName.class),
              new ExampleVariantSpec(
                  224, "bfloat16ValuesByName", ExampleFieldBfloat16ValuesByName.class),
              new ExampleVariantSpec(
                  225,
                  "maybeBfloat16ValuesByName",
                  ExampleFieldMaybeBfloat16ValuesByName.class),
              new ExampleVariantSpec(
                  226, "bytesValuesByName", ExampleFieldBytesValuesByName.class),
              new ExampleVariantSpec(
                  227, "dateValuesByName", ExampleFieldDateValuesByName.class),
              new ExampleVariantSpec(
                  228, "decimalValuesByName", ExampleFieldDecimalValuesByName.class),
              new ExampleVariantSpec(
                  229, "messageValuesByName", ExampleFieldMessageValuesByName.class),
              new ExampleVariantSpec(
                  230, "unionValuesByName", ExampleFieldUnionValuesByName.class)));

  private ExampleSchemaEvolutionTypes() {}

  static Class<?> emptyType() {
    return EMPTY_TYPE;
  }

  static List<ExampleVariantSpec> variantSpecs() {
    return VARIANT_SPECS;
  }

  static void registerEmptyType(Fory fory) {
    fory.register(ExampleMessageEmpty.class, EXAMPLE_MESSAGE_TYPE_ID);
  }

  static void registerVariantType(Fory fory, ExampleVariantSpec spec) {
    fory.register(spec.variantClass, EXAMPLE_MESSAGE_TYPE_ID);
  }

  static final class ExampleVariantSpec {
    final int fieldId;
    final String javaProperty;
    final Class<?> variantClass;

    ExampleVariantSpec(int fieldId, String javaProperty, Class<?> variantClass) {
      this.fieldId = fieldId;
      this.javaProperty = javaProperty;
      this.variantClass = variantClass;
    }
  }

  static final class ExampleMessageEmpty {}

  static final class ExampleFieldBoolValue {
    @ForyField(id = 1)
    private boolean boolValue;

    public boolean getBoolValue() {
      return boolValue;
    }

    public void setBoolValue(boolean boolValue) {
      this.boolValue = boolValue;
    }
  }

  static final class ExampleFieldInt8Value {
    @ForyField(id = 2)
    private byte int8Value;

    public byte getInt8Value() {
      return int8Value;
    }

    public void setInt8Value(byte int8Value) {
      this.int8Value = int8Value;
    }
  }

  static final class ExampleFieldInt16Value {
    @ForyField(id = 3)
    private short int16Value;

    public short getInt16Value() {
      return int16Value;
    }

    public void setInt16Value(short int16Value) {
      this.int16Value = int16Value;
    }
  }

  static final class ExampleFieldFixedInt32Value {
    @ForyField(id = 4)
    @Int32Type(compress = false)
    private int fixedInt32Value;

    public int getFixedInt32Value() {
      return fixedInt32Value;
    }

    public void setFixedInt32Value(int fixedInt32Value) {
      this.fixedInt32Value = fixedInt32Value;
    }
  }

  static final class ExampleFieldVarint32Value {
    @ForyField(id = 5)
    private int varint32Value;

    public int getVarint32Value() {
      return varint32Value;
    }

    public void setVarint32Value(int varint32Value) {
      this.varint32Value = varint32Value;
    }
  }

  static final class ExampleFieldFixedInt64Value {
    @ForyField(id = 6)
    @Int64Type(encoding = LongEncoding.FIXED)
    private long fixedInt64Value;

    public long getFixedInt64Value() {
      return fixedInt64Value;
    }

    public void setFixedInt64Value(long fixedInt64Value) {
      this.fixedInt64Value = fixedInt64Value;
    }
  }

  static final class ExampleFieldVarint64Value {
    @ForyField(id = 7)
    private long varint64Value;

    public long getVarint64Value() {
      return varint64Value;
    }

    public void setVarint64Value(long varint64Value) {
      this.varint64Value = varint64Value;
    }
  }

  static final class ExampleFieldTaggedInt64Value {
    @ForyField(id = 8)
    @Int64Type(encoding = LongEncoding.TAGGED)
    private long taggedInt64Value;

    public long getTaggedInt64Value() {
      return taggedInt64Value;
    }

    public void setTaggedInt64Value(long taggedInt64Value) {
      this.taggedInt64Value = taggedInt64Value;
    }
  }

  static final class ExampleFieldUint8Value {
    @ForyField(id = 9)
    @Uint8Type
    private byte uint8Value;

    public byte getUint8Value() {
      return uint8Value;
    }

    public void setUint8Value(byte uint8Value) {
      this.uint8Value = uint8Value;
    }
  }

  static final class ExampleFieldUint16Value {
    @ForyField(id = 10)
    @Uint16Type
    private short uint16Value;

    public short getUint16Value() {
      return uint16Value;
    }

    public void setUint16Value(short uint16Value) {
      this.uint16Value = uint16Value;
    }
  }

  static final class ExampleFieldFixedUint32Value {
    @ForyField(id = 11)
    @Uint32Type(compress = false)
    private int fixedUint32Value;

    public int getFixedUint32Value() {
      return fixedUint32Value;
    }

    public void setFixedUint32Value(int fixedUint32Value) {
      this.fixedUint32Value = fixedUint32Value;
    }
  }

  static final class ExampleFieldVarUint32Value {
    @ForyField(id = 12)
    @Uint32Type(compress = true)
    private int varUint32Value;

    public int getVarUint32Value() {
      return varUint32Value;
    }

    public void setVarUint32Value(int varUint32Value) {
      this.varUint32Value = varUint32Value;
    }
  }

  static final class ExampleFieldFixedUint64Value {
    @ForyField(id = 13)
    @Uint64Type(encoding = LongEncoding.FIXED)
    private long fixedUint64Value;

    public long getFixedUint64Value() {
      return fixedUint64Value;
    }

    public void setFixedUint64Value(long fixedUint64Value) {
      this.fixedUint64Value = fixedUint64Value;
    }
  }

  static final class ExampleFieldVarUint64Value {
    @ForyField(id = 14)
    @Uint64Type(encoding = LongEncoding.VARINT)
    private long varUint64Value;

    public long getVarUint64Value() {
      return varUint64Value;
    }

    public void setVarUint64Value(long varUint64Value) {
      this.varUint64Value = varUint64Value;
    }
  }

  static final class ExampleFieldTaggedUint64Value {
    @ForyField(id = 15)
    @Uint64Type(encoding = LongEncoding.TAGGED)
    private long taggedUint64Value;

    public long getTaggedUint64Value() {
      return taggedUint64Value;
    }

    public void setTaggedUint64Value(long taggedUint64Value) {
      this.taggedUint64Value = taggedUint64Value;
    }
  }

  static final class ExampleFieldFloat16Value {
    @ForyField(id = 16)
    private Float16 float16Value;

    public Float16 getFloat16Value() {
      return float16Value;
    }

    public void setFloat16Value(Float16 float16Value) {
      this.float16Value = float16Value;
    }
  }

  static final class ExampleFieldBfloat16Value {
    @ForyField(id = 17)
    private BFloat16 bfloat16Value;

    public BFloat16 getBfloat16Value() {
      return bfloat16Value;
    }

    public void setBfloat16Value(BFloat16 bfloat16Value) {
      this.bfloat16Value = bfloat16Value;
    }
  }

  static final class ExampleFieldFloat32Value {
    @ForyField(id = 18)
    private float float32Value;

    public float getFloat32Value() {
      return float32Value;
    }

    public void setFloat32Value(float float32Value) {
      this.float32Value = float32Value;
    }
  }

  static final class ExampleFieldFloat64Value {
    @ForyField(id = 19)
    private double float64Value;

    public double getFloat64Value() {
      return float64Value;
    }

    public void setFloat64Value(double float64Value) {
      this.float64Value = float64Value;
    }
  }

  static final class ExampleFieldStringValue {
    @ForyField(id = 20)
    private String stringValue;

    public String getStringValue() {
      return stringValue;
    }

    public void setStringValue(String stringValue) {
      this.stringValue = stringValue;
    }
  }

  static final class ExampleFieldBytesValue {
    @ForyField(id = 21)
    private byte[] bytesValue;

    public byte[] getBytesValue() {
      return bytesValue;
    }

    public void setBytesValue(byte[] bytesValue) {
      this.bytesValue = bytesValue;
    }
  }

  static final class ExampleFieldDateValue {
    @ForyField(id = 22)
    private java.time.LocalDate dateValue;

    public java.time.LocalDate getDateValue() {
      return dateValue;
    }

    public void setDateValue(java.time.LocalDate dateValue) {
      this.dateValue = dateValue;
    }
  }

  static final class ExampleFieldTimestampValue {
    @ForyField(id = 23)
    private java.time.Instant timestampValue;

    public java.time.Instant getTimestampValue() {
      return timestampValue;
    }

    public void setTimestampValue(java.time.Instant timestampValue) {
      this.timestampValue = timestampValue;
    }
  }

  static final class ExampleFieldDurationValue {
    @ForyField(id = 24)
    private java.time.Duration durationValue;

    public java.time.Duration getDurationValue() {
      return durationValue;
    }

    public void setDurationValue(java.time.Duration durationValue) {
      this.durationValue = durationValue;
    }
  }

  static final class ExampleFieldDecimalValue {
    @ForyField(id = 25)
    private java.math.BigDecimal decimalValue;

    public java.math.BigDecimal getDecimalValue() {
      return decimalValue;
    }

    public void setDecimalValue(java.math.BigDecimal decimalValue) {
      this.decimalValue = decimalValue;
    }
  }

  static final class ExampleFieldEnumValue {
    @ForyField(id = 26)
    private ExampleState enumValue;

    public ExampleState getEnumValue() {
      return enumValue;
    }

    public void setEnumValue(ExampleState enumValue) {
      this.enumValue = enumValue;
    }
  }

  static final class ExampleFieldMessageValue {
    @ForyField(id = 27, nullable = true)
    private ExampleLeaf messageValue;

    public ExampleLeaf getMessageValue() {
      return messageValue;
    }

    public void setMessageValue(ExampleLeaf messageValue) {
      this.messageValue = messageValue;
    }
  }

  static final class ExampleFieldUnionValue {
    @ForyField(id = 28)
    private ExampleLeafUnion unionValue;

    public ExampleLeafUnion getUnionValue() {
      return unionValue;
    }

    public void setUnionValue(ExampleLeafUnion unionValue) {
      this.unionValue = unionValue;
    }
  }

  static final class ExampleFieldBoolList {
    @ForyField(id = 101)
    private boolean[] boolList;

    public boolean[] getBoolList() {
      return boolList;
    }

    public void setBoolList(boolean[] boolList) {
      this.boolList = boolList;
    }
  }

  static final class ExampleFieldInt8List {
    @ForyField(id = 102)
    private Int8List int8List;

    public Int8List getInt8List() {
      return int8List;
    }

    public void setInt8List(Int8List int8List) {
      this.int8List = int8List;
    }
  }

  static final class ExampleFieldInt16List {
    @ForyField(id = 103)
    private Int16List int16List;

    public Int16List getInt16List() {
      return int16List;
    }

    public void setInt16List(Int16List int16List) {
      this.int16List = int16List;
    }
  }

  static final class ExampleFieldFixedInt32List {
    @ForyField(id = 104)
    private Int32List fixedInt32List;

    public Int32List getFixedInt32List() {
      return fixedInt32List;
    }

    public void setFixedInt32List(Int32List fixedInt32List) {
      this.fixedInt32List = fixedInt32List;
    }
  }

  static final class ExampleFieldVarint32List {
    @ForyField(id = 105)
    private Int32List varint32List;

    public Int32List getVarint32List() {
      return varint32List;
    }

    public void setVarint32List(Int32List varint32List) {
      this.varint32List = varint32List;
    }
  }

  static final class ExampleFieldFixedInt64List {
    @ForyField(id = 106)
    private Int64List fixedInt64List;

    public Int64List getFixedInt64List() {
      return fixedInt64List;
    }

    public void setFixedInt64List(Int64List fixedInt64List) {
      this.fixedInt64List = fixedInt64List;
    }
  }

  static final class ExampleFieldVarint64List {
    @ForyField(id = 107)
    private Int64List varint64List;

    public Int64List getVarint64List() {
      return varint64List;
    }

    public void setVarint64List(Int64List varint64List) {
      this.varint64List = varint64List;
    }
  }

  static final class ExampleFieldTaggedInt64List {
    @ForyField(id = 108)
    private Int64List taggedInt64List;

    public Int64List getTaggedInt64List() {
      return taggedInt64List;
    }

    public void setTaggedInt64List(Int64List taggedInt64List) {
      this.taggedInt64List = taggedInt64List;
    }
  }

  static final class ExampleFieldUint8List {
    @ForyField(id = 109)
    private Uint8List uint8List;

    public Uint8List getUint8List() {
      return uint8List;
    }

    public void setUint8List(Uint8List uint8List) {
      this.uint8List = uint8List;
    }
  }

  static final class ExampleFieldUint16List {
    @ForyField(id = 110)
    private Uint16List uint16List;

    public Uint16List getUint16List() {
      return uint16List;
    }

    public void setUint16List(Uint16List uint16List) {
      this.uint16List = uint16List;
    }
  }

  static final class ExampleFieldFixedUint32List {
    @ForyField(id = 111)
    private Uint32List fixedUint32List;

    public Uint32List getFixedUint32List() {
      return fixedUint32List;
    }

    public void setFixedUint32List(Uint32List fixedUint32List) {
      this.fixedUint32List = fixedUint32List;
    }
  }

  static final class ExampleFieldVarUint32List {
    @ForyField(id = 112)
    private Uint32List varUint32List;

    public Uint32List getVarUint32List() {
      return varUint32List;
    }

    public void setVarUint32List(Uint32List varUint32List) {
      this.varUint32List = varUint32List;
    }
  }

  static final class ExampleFieldFixedUint64List {
    @ForyField(id = 113)
    private Uint64List fixedUint64List;

    public Uint64List getFixedUint64List() {
      return fixedUint64List;
    }

    public void setFixedUint64List(Uint64List fixedUint64List) {
      this.fixedUint64List = fixedUint64List;
    }
  }

  static final class ExampleFieldVarUint64List {
    @ForyField(id = 114)
    private Uint64List varUint64List;

    public Uint64List getVarUint64List() {
      return varUint64List;
    }

    public void setVarUint64List(Uint64List varUint64List) {
      this.varUint64List = varUint64List;
    }
  }

  static final class ExampleFieldTaggedUint64List {
    @ForyField(id = 115)
    private Uint64List taggedUint64List;

    public Uint64List getTaggedUint64List() {
      return taggedUint64List;
    }

    public void setTaggedUint64List(Uint64List taggedUint64List) {
      this.taggedUint64List = taggedUint64List;
    }
  }

  static final class ExampleFieldFloat16List {
    @ForyField(id = 116)
    private Float16List float16List;

    public Float16List getFloat16List() {
      return float16List;
    }

    public void setFloat16List(Float16List float16List) {
      this.float16List = float16List;
    }
  }

  static final class ExampleFieldBfloat16List {
    @ForyField(id = 117)
    private BFloat16List bfloat16List;

    public BFloat16List getBfloat16List() {
      return bfloat16List;
    }

    public void setBfloat16List(BFloat16List bfloat16List) {
      this.bfloat16List = bfloat16List;
    }
  }

  static final class ExampleFieldMaybeFloat16List {
    @ForyField(id = 118)
    private List<Float16> maybeFloat16List;

    public List<Float16> getMaybeFloat16List() {
      return maybeFloat16List;
    }

    public void setMaybeFloat16List(List<Float16> maybeFloat16List) {
      this.maybeFloat16List = maybeFloat16List;
    }
  }

  static final class ExampleFieldMaybeBfloat16List {
    @ForyField(id = 119)
    private List<BFloat16> maybeBfloat16List;

    public List<BFloat16> getMaybeBfloat16List() {
      return maybeBfloat16List;
    }

    public void setMaybeBfloat16List(List<BFloat16> maybeBfloat16List) {
      this.maybeBfloat16List = maybeBfloat16List;
    }
  }

  static final class ExampleFieldFloat32List {
    @ForyField(id = 120)
    private float[] float32List;

    public float[] getFloat32List() {
      return float32List;
    }

    public void setFloat32List(float[] float32List) {
      this.float32List = float32List;
    }
  }

  static final class ExampleFieldFloat64List {
    @ForyField(id = 121)
    private double[] float64List;

    public double[] getFloat64List() {
      return float64List;
    }

    public void setFloat64List(double[] float64List) {
      this.float64List = float64List;
    }
  }

  static final class ExampleFieldStringList {
    @ForyField(id = 122)
    private List<String> stringList;

    public List<String> getStringList() {
      return stringList;
    }

    public void setStringList(List<String> stringList) {
      this.stringList = stringList;
    }
  }

  static final class ExampleFieldBytesList {
    @ForyField(id = 123)
    private List<byte[]> bytesList;

    public List<byte[]> getBytesList() {
      return bytesList;
    }

    public void setBytesList(List<byte[]> bytesList) {
      this.bytesList = bytesList;
    }
  }

  static final class ExampleFieldDateList {
    @ForyField(id = 124)
    private List<java.time.LocalDate> dateList;

    public List<java.time.LocalDate> getDateList() {
      return dateList;
    }

    public void setDateList(List<java.time.LocalDate> dateList) {
      this.dateList = dateList;
    }
  }

  static final class ExampleFieldTimestampList {
    @ForyField(id = 125)
    private List<java.time.Instant> timestampList;

    public List<java.time.Instant> getTimestampList() {
      return timestampList;
    }

    public void setTimestampList(List<java.time.Instant> timestampList) {
      this.timestampList = timestampList;
    }
  }

  static final class ExampleFieldDurationList {
    @ForyField(id = 126)
    private List<java.time.Duration> durationList;

    public List<java.time.Duration> getDurationList() {
      return durationList;
    }

    public void setDurationList(List<java.time.Duration> durationList) {
      this.durationList = durationList;
    }
  }

  static final class ExampleFieldDecimalList {
    @ForyField(id = 127)
    private List<java.math.BigDecimal> decimalList;

    public List<java.math.BigDecimal> getDecimalList() {
      return decimalList;
    }

    public void setDecimalList(List<java.math.BigDecimal> decimalList) {
      this.decimalList = decimalList;
    }
  }

  static final class ExampleFieldEnumList {
    @ForyField(id = 128)
    private List<ExampleState> enumList;

    public List<ExampleState> getEnumList() {
      return enumList;
    }

    public void setEnumList(List<ExampleState> enumList) {
      this.enumList = enumList;
    }
  }

  static final class ExampleFieldMessageList {
    @ForyField(id = 129)
    private List<@Ref(enable = false) ExampleLeaf> messageList;

    public List<@Ref(enable = false) ExampleLeaf> getMessageList() {
      return messageList;
    }

    public void setMessageList(List<@Ref(enable = false) ExampleLeaf> messageList) {
      this.messageList = messageList;
    }
  }

  static final class ExampleFieldUnionList {
    @ForyField(id = 130)
    private List<@Ref(enable = false) ExampleLeafUnion> unionList;

    public List<@Ref(enable = false) ExampleLeafUnion> getUnionList() {
      return unionList;
    }

    public void setUnionList(List<@Ref(enable = false) ExampleLeafUnion> unionList) {
      this.unionList = unionList;
    }
  }

  static final class ExampleFieldStringValuesByBool {
    @ForyField(id = 201)
    private Map<Boolean, String> stringValuesByBool;

    public Map<Boolean, String> getStringValuesByBool() {
      return stringValuesByBool;
    }

    public void setStringValuesByBool(Map<Boolean, String> stringValuesByBool) {
      this.stringValuesByBool = stringValuesByBool;
    }
  }

  static final class ExampleFieldStringValuesByInt8 {
    @ForyField(id = 202)
    private Map<Byte, String> stringValuesByInt8;

    public Map<Byte, String> getStringValuesByInt8() {
      return stringValuesByInt8;
    }

    public void setStringValuesByInt8(Map<Byte, String> stringValuesByInt8) {
      this.stringValuesByInt8 = stringValuesByInt8;
    }
  }

  static final class ExampleFieldStringValuesByInt16 {
    @ForyField(id = 203)
    private Map<Short, String> stringValuesByInt16;

    public Map<Short, String> getStringValuesByInt16() {
      return stringValuesByInt16;
    }

    public void setStringValuesByInt16(Map<Short, String> stringValuesByInt16) {
      this.stringValuesByInt16 = stringValuesByInt16;
    }
  }

  static final class ExampleFieldStringValuesByFixedInt32 {
    @ForyField(id = 204)
    private Map<@Int32Type(compress = false) Integer, String> stringValuesByFixedInt32;

    public Map<@Int32Type(compress = false) Integer, String> getStringValuesByFixedInt32() {
      return stringValuesByFixedInt32;
    }

    public void setStringValuesByFixedInt32(
        Map<@Int32Type(compress = false) Integer, String> stringValuesByFixedInt32) {
      this.stringValuesByFixedInt32 = stringValuesByFixedInt32;
    }
  }

  static final class ExampleFieldStringValuesByVarint32 {
    @ForyField(id = 205)
    private Map<@Int32Type(compress = true) Integer, String> stringValuesByVarint32;

    public Map<@Int32Type(compress = true) Integer, String> getStringValuesByVarint32() {
      return stringValuesByVarint32;
    }

    public void setStringValuesByVarint32(
        Map<@Int32Type(compress = true) Integer, String> stringValuesByVarint32) {
      this.stringValuesByVarint32 = stringValuesByVarint32;
    }
  }

  static final class ExampleFieldStringValuesByFixedInt64 {
    @ForyField(id = 206)
    private Map<@Int64Type(encoding = LongEncoding.FIXED) Long, String>
        stringValuesByFixedInt64;

    public Map<@Int64Type(encoding = LongEncoding.FIXED) Long, String>
        getStringValuesByFixedInt64() {
      return stringValuesByFixedInt64;
    }

    public void setStringValuesByFixedInt64(
        Map<@Int64Type(encoding = LongEncoding.FIXED) Long, String> stringValuesByFixedInt64) {
      this.stringValuesByFixedInt64 = stringValuesByFixedInt64;
    }
  }

  static final class ExampleFieldStringValuesByVarint64 {
    @ForyField(id = 207)
    private Map<@Int64Type(encoding = LongEncoding.VARINT) Long, String> stringValuesByVarint64;

    public Map<@Int64Type(encoding = LongEncoding.VARINT) Long, String>
        getStringValuesByVarint64() {
      return stringValuesByVarint64;
    }

    public void setStringValuesByVarint64(
        Map<@Int64Type(encoding = LongEncoding.VARINT) Long, String> stringValuesByVarint64) {
      this.stringValuesByVarint64 = stringValuesByVarint64;
    }
  }

  static final class ExampleFieldStringValuesByTaggedInt64 {
    @ForyField(id = 208)
    private Map<@Int64Type(encoding = LongEncoding.TAGGED) Long, String>
        stringValuesByTaggedInt64;

    public Map<@Int64Type(encoding = LongEncoding.TAGGED) Long, String>
        getStringValuesByTaggedInt64() {
      return stringValuesByTaggedInt64;
    }

    public void setStringValuesByTaggedInt64(
        Map<@Int64Type(encoding = LongEncoding.TAGGED) Long, String>
            stringValuesByTaggedInt64) {
      this.stringValuesByTaggedInt64 = stringValuesByTaggedInt64;
    }
  }

  static final class ExampleFieldStringValuesByUint8 {
    @ForyField(id = 209)
    private Map<@Uint8Type Byte, String> stringValuesByUint8;

    public Map<@Uint8Type Byte, String> getStringValuesByUint8() {
      return stringValuesByUint8;
    }

    public void setStringValuesByUint8(Map<@Uint8Type Byte, String> stringValuesByUint8) {
      this.stringValuesByUint8 = stringValuesByUint8;
    }
  }

  static final class ExampleFieldStringValuesByUint16 {
    @ForyField(id = 210)
    private Map<@Uint16Type Short, String> stringValuesByUint16;

    public Map<@Uint16Type Short, String> getStringValuesByUint16() {
      return stringValuesByUint16;
    }

    public void setStringValuesByUint16(Map<@Uint16Type Short, String> stringValuesByUint16) {
      this.stringValuesByUint16 = stringValuesByUint16;
    }
  }

  static final class ExampleFieldStringValuesByFixedUint32 {
    @ForyField(id = 211)
    private Map<@Uint32Type(compress = false) Integer, String> stringValuesByFixedUint32;

    public Map<@Uint32Type(compress = false) Integer, String> getStringValuesByFixedUint32() {
      return stringValuesByFixedUint32;
    }

    public void setStringValuesByFixedUint32(
        Map<@Uint32Type(compress = false) Integer, String> stringValuesByFixedUint32) {
      this.stringValuesByFixedUint32 = stringValuesByFixedUint32;
    }
  }

  static final class ExampleFieldStringValuesByVarUint32 {
    @ForyField(id = 212)
    private Map<@Uint32Type(compress = true) Integer, String> stringValuesByVarUint32;

    public Map<@Uint32Type(compress = true) Integer, String> getStringValuesByVarUint32() {
      return stringValuesByVarUint32;
    }

    public void setStringValuesByVarUint32(
        Map<@Uint32Type(compress = true) Integer, String> stringValuesByVarUint32) {
      this.stringValuesByVarUint32 = stringValuesByVarUint32;
    }
  }

  static final class ExampleFieldStringValuesByFixedUint64 {
    @ForyField(id = 213)
    private Map<@Uint64Type(encoding = LongEncoding.FIXED) Long, String>
        stringValuesByFixedUint64;

    public Map<@Uint64Type(encoding = LongEncoding.FIXED) Long, String>
        getStringValuesByFixedUint64() {
      return stringValuesByFixedUint64;
    }

    public void setStringValuesByFixedUint64(
        Map<@Uint64Type(encoding = LongEncoding.FIXED) Long, String> stringValuesByFixedUint64) {
      this.stringValuesByFixedUint64 = stringValuesByFixedUint64;
    }
  }

  static final class ExampleFieldStringValuesByVarUint64 {
    @ForyField(id = 214)
    private Map<@Uint64Type(encoding = LongEncoding.VARINT) Long, String> stringValuesByVarUint64;

    public Map<@Uint64Type(encoding = LongEncoding.VARINT) Long, String>
        getStringValuesByVarUint64() {
      return stringValuesByVarUint64;
    }

    public void setStringValuesByVarUint64(
        Map<@Uint64Type(encoding = LongEncoding.VARINT) Long, String> stringValuesByVarUint64) {
      this.stringValuesByVarUint64 = stringValuesByVarUint64;
    }
  }

  static final class ExampleFieldStringValuesByTaggedUint64 {
    @ForyField(id = 215)
    private Map<@Uint64Type(encoding = LongEncoding.TAGGED) Long, String>
        stringValuesByTaggedUint64;

    public Map<@Uint64Type(encoding = LongEncoding.TAGGED) Long, String>
        getStringValuesByTaggedUint64() {
      return stringValuesByTaggedUint64;
    }

    public void setStringValuesByTaggedUint64(
        Map<@Uint64Type(encoding = LongEncoding.TAGGED) Long, String>
            stringValuesByTaggedUint64) {
      this.stringValuesByTaggedUint64 = stringValuesByTaggedUint64;
    }
  }

  static final class ExampleFieldStringValuesByString {
    @ForyField(id = 218)
    private Map<String, String> stringValuesByString;

    public Map<String, String> getStringValuesByString() {
      return stringValuesByString;
    }

    public void setStringValuesByString(Map<String, String> stringValuesByString) {
      this.stringValuesByString = stringValuesByString;
    }
  }

  static final class ExampleFieldStringValuesByTimestamp {
    @ForyField(id = 219)
    private Map<java.time.Instant, String> stringValuesByTimestamp;

    public Map<java.time.Instant, String> getStringValuesByTimestamp() {
      return stringValuesByTimestamp;
    }

    public void setStringValuesByTimestamp(Map<java.time.Instant, String> stringValuesByTimestamp) {
      this.stringValuesByTimestamp = stringValuesByTimestamp;
    }
  }

  static final class ExampleFieldStringValuesByDuration {
    @ForyField(id = 220)
    private Map<java.time.Duration, String> stringValuesByDuration;

    public Map<java.time.Duration, String> getStringValuesByDuration() {
      return stringValuesByDuration;
    }

    public void setStringValuesByDuration(Map<java.time.Duration, String> stringValuesByDuration) {
      this.stringValuesByDuration = stringValuesByDuration;
    }
  }

  static final class ExampleFieldStringValuesByEnum {
    @ForyField(id = 221)
    private Map<ExampleState, String> stringValuesByEnum;

    public Map<ExampleState, String> getStringValuesByEnum() {
      return stringValuesByEnum;
    }

    public void setStringValuesByEnum(Map<ExampleState, String> stringValuesByEnum) {
      this.stringValuesByEnum = stringValuesByEnum;
    }
  }

  static final class ExampleFieldFloat16ValuesByName {
    @ForyField(id = 222)
    private Map<String, Float16> float16ValuesByName;

    public Map<String, Float16> getFloat16ValuesByName() {
      return float16ValuesByName;
    }

    public void setFloat16ValuesByName(Map<String, Float16> float16ValuesByName) {
      this.float16ValuesByName = float16ValuesByName;
    }
  }

  static final class ExampleFieldMaybeFloat16ValuesByName {
    @ForyField(id = 223)
    private Map<String, Float16> maybeFloat16ValuesByName;

    public Map<String, Float16> getMaybeFloat16ValuesByName() {
      return maybeFloat16ValuesByName;
    }

    public void setMaybeFloat16ValuesByName(Map<String, Float16> maybeFloat16ValuesByName) {
      this.maybeFloat16ValuesByName = maybeFloat16ValuesByName;
    }
  }

  static final class ExampleFieldBfloat16ValuesByName {
    @ForyField(id = 224)
    private Map<String, BFloat16> bfloat16ValuesByName;

    public Map<String, BFloat16> getBfloat16ValuesByName() {
      return bfloat16ValuesByName;
    }

    public void setBfloat16ValuesByName(Map<String, BFloat16> bfloat16ValuesByName) {
      this.bfloat16ValuesByName = bfloat16ValuesByName;
    }
  }

  static final class ExampleFieldMaybeBfloat16ValuesByName {
    @ForyField(id = 225)
    private Map<String, BFloat16> maybeBfloat16ValuesByName;

    public Map<String, BFloat16> getMaybeBfloat16ValuesByName() {
      return maybeBfloat16ValuesByName;
    }

    public void setMaybeBfloat16ValuesByName(
        Map<String, BFloat16> maybeBfloat16ValuesByName) {
      this.maybeBfloat16ValuesByName = maybeBfloat16ValuesByName;
    }
  }

  static final class ExampleFieldBytesValuesByName {
    @ForyField(id = 226)
    private Map<String, byte[]> bytesValuesByName;

    public Map<String, byte[]> getBytesValuesByName() {
      return bytesValuesByName;
    }

    public void setBytesValuesByName(Map<String, byte[]> bytesValuesByName) {
      this.bytesValuesByName = bytesValuesByName;
    }
  }

  static final class ExampleFieldDateValuesByName {
    @ForyField(id = 227)
    private Map<String, java.time.LocalDate> dateValuesByName;

    public Map<String, java.time.LocalDate> getDateValuesByName() {
      return dateValuesByName;
    }

    public void setDateValuesByName(Map<String, java.time.LocalDate> dateValuesByName) {
      this.dateValuesByName = dateValuesByName;
    }
  }

  static final class ExampleFieldDecimalValuesByName {
    @ForyField(id = 228)
    private Map<String, java.math.BigDecimal> decimalValuesByName;

    public Map<String, java.math.BigDecimal> getDecimalValuesByName() {
      return decimalValuesByName;
    }

    public void setDecimalValuesByName(Map<String, java.math.BigDecimal> decimalValuesByName) {
      this.decimalValuesByName = decimalValuesByName;
    }
  }

  static final class ExampleFieldMessageValuesByName {
    @ForyField(id = 229)
    private Map<String, @Ref(enable = false) ExampleLeaf> messageValuesByName;

    public Map<String, @Ref(enable = false) ExampleLeaf> getMessageValuesByName() {
      return messageValuesByName;
    }

    public void setMessageValuesByName(
        Map<String, @Ref(enable = false) ExampleLeaf> messageValuesByName) {
      this.messageValuesByName = messageValuesByName;
    }
  }

  static final class ExampleFieldUnionValuesByName {
    @ForyField(id = 230)
    private Map<String, @Ref(enable = false) ExampleLeafUnion> unionValuesByName;

    public Map<String, @Ref(enable = false) ExampleLeafUnion> getUnionValuesByName() {
      return unionValuesByName;
    }

    public void setUnionValuesByName(
        Map<String, @Ref(enable = false) ExampleLeafUnion> unionValuesByName) {
      this.unionValuesByName = unionValuesByName;
    }
  }
}
