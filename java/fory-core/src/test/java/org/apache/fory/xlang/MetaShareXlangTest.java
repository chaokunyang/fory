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

package org.apache.fory.xlang;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.annotation.ArrayType;
import org.apache.fory.annotation.Int32Type;
import org.apache.fory.collection.Int32List;
import org.apache.fory.config.Int32Encoding;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.test.bean.BeanB;
import org.apache.fory.xlang.PyCrossLanguageTest.Bar;
import org.apache.fory.xlang.PyCrossLanguageTest.Foo;
import org.testng.annotations.Test;

public class MetaShareXlangTest extends ForyTestBase {

  @Test
  public void testMetaShareBasic() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();
    fory.register(Foo.class, "example.foo");
    fory.register(Bar.class, "example.bar");
    serDeCheck(fory, Bar.create());
    serDeCheck(fory, Foo.create());
  }

  @Test
  public void testMetaShareComplex1() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();
    fory.register(BeanB.class, "example.b");
    serDeCheck(fory, BeanB.createBeanB(2));
  }

  @Data
  static class MDArrayFieldStruct {
    int[][] arr;
  }

  @Test
  public void testMDArrayField() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();
    fory.register(MDArrayFieldStruct.class, "example.a");
    MDArrayFieldStruct s = new MDArrayFieldStruct();
    s.arr = new int[][] {{1, 2}, {3, 4}};
    serDeCheck(fory, s);
  }

  @Data
  static class DirectListField {
    @Int32Type(encoding = Int32Encoding.FIXED)
    Int32List values;
  }

  @Data
  static class DirectNullableListField {
    List<Integer> values;
  }

  @Data
  static class DirectArrayField {
    int[] values;
  }

  @Data
  static class DirectAnnotatedArrayField {
    @ArrayType List<Integer> values;
  }

  @Data
  static class DirectCollectionField {
    Collection<@Int32Type(encoding = Int32Encoding.FIXED) Integer> values;
  }

  @Data
  static class NestedListField {
    List<List<Integer>> values;
  }

  @Data
  static class NestedArrayElementField {
    List<int[]> values;
  }

  @Data
  static class NestedMapArrayValueField {
    Map<String, int[]> values;
  }

  @Data
  static class NestedMapListValueField {
    Map<String, List<@Int32Type(encoding = Int32Encoding.FIXED) Integer>> values;
  }

  @Data
  static class NestedMapArrayKeyField {
    Map<int[], String> values;
  }

  @Data
  static class NestedMapListKeyField {
    Map<List<@Int32Type(encoding = Int32Encoding.FIXED) Integer>, String> values;
  }

  @Data
  static class NestedArrayComponentField {
    int[][] values;
  }

  @Data
  static class NestedListComponentField {
    List<@Int32Type(encoding = Int32Encoding.FIXED) Integer>[] values;
  }

  @Data
  static class NestedLinkedListField {
    List<LinkedList<@Int32Type(encoding = Int32Encoding.FIXED) Integer>> values;
  }

  @Data
  static class NestedCopyOnWriteField {
    List<CopyOnWriteArrayList<@Int32Type(encoding = Int32Encoding.FIXED) Integer>> values;
  }

  @Data
  static class NestedStructWriterField {
    List<NestedStructWriterValue> values;
  }

  @Data
  static class NestedStructReaderField {
    List<NestedStructReaderValue> values;
  }

  @Data
  static class NestedStructWriterValue {
    int value;
  }

  @Data
  static class NestedStructReaderValue {
    long value;
    String added = "default";
  }

  abstract static class InheritedFieldParent {
    public Map<String, BigDecimal> values = new ConcurrentHashMap<>();
  }

  static class InheritedFieldChild extends InheritedFieldParent {}

  @Test
  public void testInheritedField() {
    for (boolean codegen : new boolean[] {false, true}) {
      Fory fory = compatibleFory(InheritedFieldChild.class, codegen);
      InheritedFieldChild value = new InheritedFieldChild();
      value.values.put("one", BigDecimal.ONE);

      InheritedFieldChild decoded = (InheritedFieldChild) fory.deserialize(fory.serialize(value));

      assertEquals(decoded.values, value.values);
    }
  }

  @Test
  public void testTopLevelListArrayCompatibleRead() {
    Fory listFory = compatibleFory(DirectListField.class);
    DirectListField listStruct = new DirectListField();
    listStruct.values = new Int32List(new int[] {1, 2, 3});
    byte[] listBytes = listFory.serialize(listStruct);

    Fory arrayFory = compatibleFory(DirectArrayField.class);
    DirectArrayField arrayStruct = (DirectArrayField) arrayFory.deserialize(listBytes);
    assertTrue(Arrays.equals(arrayStruct.values, new int[] {1, 2, 3}));

    DirectListField emptyListStruct = new DirectListField();
    emptyListStruct.values = new Int32List();
    DirectArrayField emptyArrayStruct =
        (DirectArrayField) arrayFory.deserialize(listFory.serialize(emptyListStruct));
    assertEquals(emptyArrayStruct.values.length, 0);

    DirectArrayField peerArrayStruct = new DirectArrayField();
    peerArrayStruct.values = new int[] {4, 5, 6};
    byte[] arrayBytes = arrayFory.serialize(peerArrayStruct);
    DirectListField readListStruct = (DirectListField) listFory.deserialize(arrayBytes);
    assertEquals(readListStruct.values, Arrays.asList(4, 5, 6));

    DirectArrayField emptyPeerArrayStruct = new DirectArrayField();
    emptyPeerArrayStruct.values = new int[0];
    DirectListField emptyReadListStruct =
        (DirectListField) listFory.deserialize(arrayFory.serialize(emptyPeerArrayStruct));
    assertEquals(emptyReadListStruct.values, java.util.Collections.emptyList());
  }

  @Test
  public void testTopLevelListAnnotatedArrayCompatibleRead() {
    Fory listFory = compatibleFory(DirectListField.class);
    DirectListField listStruct = new DirectListField();
    listStruct.values = new Int32List(new int[] {7, 8});

    Fory annotatedArrayFory = compatibleFory(DirectAnnotatedArrayField.class);
    DirectAnnotatedArrayField annotatedArrayStruct =
        (DirectAnnotatedArrayField) annotatedArrayFory.deserialize(listFory.serialize(listStruct));
    assertEquals(annotatedArrayStruct.values, Arrays.asList(7, 8));
  }

  @Test
  public void testTopLevelArrayCompatibleReadToCollection() {
    for (boolean codegen : new boolean[] {false, true}) {
      Fory arrayFory = compatibleFory(DirectArrayField.class, codegen);
      DirectArrayField peerArrayStruct = new DirectArrayField();
      peerArrayStruct.values = new int[] {9, 10};

      Fory collectionFory = compatibleFory(DirectCollectionField.class, codegen);
      DirectCollectionField collectionStruct =
          (DirectCollectionField) collectionFory.deserialize(arrayFory.serialize(peerArrayStruct));
      assertEquals(collectionStruct.values, Arrays.asList(9, 10));
    }
  }

  @Test
  public void testTopLevelListArrayCompatibleReadWithoutCodegen() {
    Fory listFory = compatibleFory(DirectListField.class, false);
    DirectListField listStruct = new DirectListField();
    listStruct.values = new Int32List(new int[] {1, 2, 3});

    Fory arrayFory = compatibleFory(DirectArrayField.class, false);
    DirectArrayField arrayStruct =
        (DirectArrayField) arrayFory.deserialize(listFory.serialize(listStruct));
    assertTrue(Arrays.equals(arrayStruct.values, new int[] {1, 2, 3}));
  }

  @Test
  public void testNullableListCompatibleReadToArray() {
    for (boolean codegen : new boolean[] {false, true}) {
      Fory listFory = compatibleFory(DirectNullableListField.class, codegen);
      DirectNullableListField listStruct = new DirectNullableListField();
      listStruct.values = Arrays.asList(1, 2, 3);
      byte[] listBytes = listFory.serialize(listStruct);

      Fory arrayFory = compatibleFory(DirectArrayField.class, codegen);
      DirectArrayField arrayStruct = (DirectArrayField) arrayFory.deserialize(listBytes);
      assertTrue(Arrays.equals(arrayStruct.values, new int[] {1, 2, 3}));

      DirectNullableListField nullElementStruct = new DirectNullableListField();
      nullElementStruct.values = Arrays.asList(1, null, 3);
      byte[] nullElementBytes = listFory.serialize(nullElementStruct);
      assertThrows(DeserializationException.class, () -> arrayFory.deserialize(nullElementBytes));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testNestedListArrayRejected() {
    for (boolean codegen : new boolean[] {false, true}) {
      NestedListField nestedList = new NestedListField();
      nestedList.values = Arrays.asList(Arrays.asList(1, 2));
      NestedArrayElementField nestedArray = new NestedArrayElementField();
      nestedArray.values = Arrays.asList(new int[] {1, 2});
      assertNestedReadFails(nestedList, nestedArray, codegen);
      assertNestedReadFails(nestedArray, nestedList, codegen);

      NestedMapArrayValueField mapArrayValue = new NestedMapArrayValueField();
      mapArrayValue.values = new LinkedHashMap<>();
      mapArrayValue.values.put("value", new int[] {1, 2});
      NestedMapListValueField mapListValue = new NestedMapListValueField();
      mapListValue.values = new LinkedHashMap<>();
      mapListValue.values.put("value", Arrays.asList(1, 2));
      assertNestedReadFails(mapArrayValue, mapListValue, codegen);
      assertNestedReadFails(mapListValue, mapArrayValue, codegen);

      NestedMapArrayKeyField mapArrayKey = new NestedMapArrayKeyField();
      mapArrayKey.values = new LinkedHashMap<>();
      mapArrayKey.values.put(new int[] {1, 2}, "value");
      NestedMapListKeyField mapListKey = new NestedMapListKeyField();
      mapListKey.values = new LinkedHashMap<>();
      mapListKey.values.put(Arrays.asList(1, 2), "value");
      assertNestedReadFails(mapArrayKey, mapListKey, codegen);
      assertNestedReadFails(mapListKey, mapArrayKey, codegen);

      NestedArrayComponentField arrayComponent = new NestedArrayComponentField();
      arrayComponent.values = new int[][] {{1, 2}};
      NestedListComponentField listComponent = new NestedListComponentField();
      listComponent.values = new List[] {Arrays.asList(1, 2)};
      assertNestedReadFails(arrayComponent, listComponent, codegen);
      assertNestedReadFails(listComponent, arrayComponent, codegen);

      NestedLinkedListField linkedList = new NestedLinkedListField();
      linkedList.values = Arrays.asList(new LinkedList<>(Arrays.asList(1, 2)));
      NestedCopyOnWriteField copyOnWrite = new NestedCopyOnWriteField();
      copyOnWrite.values = Arrays.asList(new CopyOnWriteArrayList<>(Arrays.asList(1, 2)));
      assertNestedReadFails(nestedArray, linkedList, codegen);
      assertNestedReadFails(nestedArray, copyOnWrite, codegen);
    }
  }

  @Test
  public void testNestedStructFieldEvolution() {
    for (boolean codegen : new boolean[] {false, true}) {
      Fory writer =
          Fory.builder().withXlang(true).withCompatible(true).withCodegen(codegen).build();
      writer.register(NestedStructWriterValue.class, "example.nested_struct_value");
      writer.register(NestedStructWriterField.class, "example.nested_struct_field");
      NestedStructWriterValue writerValue = new NestedStructWriterValue();
      writerValue.value = 42;
      NestedStructWriterField writerField = new NestedStructWriterField();
      writerField.values = Arrays.asList(writerValue);

      Fory reader =
          Fory.builder().withXlang(true).withCompatible(true).withCodegen(codegen).build();
      reader.register(NestedStructReaderValue.class, "example.nested_struct_value");
      reader.register(NestedStructReaderField.class, "example.nested_struct_field");

      NestedStructReaderField result =
          (NestedStructReaderField) reader.deserialize(writer.serialize(writerField));
      assertEquals(result.values.get(0).value, 42L);
    }
  }

  private static void assertNestedReadFails(
      Object writerValue, Object readerValue, boolean codegen) {
    Fory writer = compatibleFory(writerValue.getClass(), codegen);
    byte[] incompatibleBytes = writer.serialize(writerValue);
    Fory reader = compatibleFory(readerValue.getClass(), codegen);
    byte[] validBytes = reader.serialize(readerValue);
    assertThrows(RuntimeException.class, () -> reader.deserialize(incompatibleBytes));
    assertEquals(reader.deserialize(validBytes).getClass(), readerValue.getClass());
  }

  private static Fory compatibleFory(Class<?> type) {
    return compatibleFory(type, true);
  }

  private static Fory compatibleFory(Class<?> type, boolean codegen) {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(codegen).build();
    fory.register(type, "example.list_array_compatible");
    return fory;
  }
}
