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
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.annotation.ArrayType;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.test.bean.BeanB;
import org.apache.fory.xlang.PyCrossLanguageTest.Bar;
import org.apache.fory.xlang.PyCrossLanguageTest.Foo;
import org.testng.annotations.Test;

public class MetaSharedXlangTest extends ForyTestBase {

  @Test
  public void testMetaSharedBasic() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();
    fory.register(Foo.class, "example.foo");
    fory.register(Bar.class, "example.bar");
    serDeCheck(fory, Bar.create());
    serDeCheck(fory, Foo.create());
  }

  @Test
  public void testMetaSharedComplex1() {
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
  static class NestedListField {
    List<List<Integer>> values;
  }

  @Data
  static class NestedArrayElementField {
    List<int[]> values;
  }

  @Test
  public void testTopLevelListArrayCompatibleRead() {
    Fory listFory = compatibleFory(DirectListField.class);
    DirectListField listStruct = new DirectListField();
    listStruct.values = Arrays.asList(1, 2, 3);
    byte[] listBytes = listFory.serialize(listStruct);

    Fory arrayFory = compatibleFory(DirectArrayField.class);
    DirectArrayField arrayStruct = (DirectArrayField) arrayFory.deserialize(listBytes);
    assertTrue(Arrays.equals(arrayStruct.values, new int[] {1, 2, 3}));

    DirectListField emptyListStruct = new DirectListField();
    emptyListStruct.values = java.util.Collections.emptyList();
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
    listStruct.values = Arrays.asList(7, 8);

    Fory annotatedArrayFory = compatibleFory(DirectAnnotatedArrayField.class);
    DirectAnnotatedArrayField annotatedArrayStruct =
        (DirectAnnotatedArrayField) annotatedArrayFory.deserialize(listFory.serialize(listStruct));
    assertEquals(annotatedArrayStruct.values, Arrays.asList(7, 8));
  }

  @Test
  public void testTopLevelListArrayCompatibleReadWithoutCodegen() {
    Fory listFory = compatibleFory(DirectListField.class, false);
    DirectListField listStruct = new DirectListField();
    listStruct.values = Arrays.asList(1, 2, 3);

    Fory arrayFory = compatibleFory(DirectArrayField.class, false);
    DirectArrayField arrayStruct =
        (DirectArrayField) arrayFory.deserialize(listFory.serialize(listStruct));
    assertTrue(Arrays.equals(arrayStruct.values, new int[] {1, 2, 3}));
  }

  @Test
  public void testNullableListElementsSkippedForArrayCompatibleRead() {
    for (boolean codegen : new boolean[] {false, true}) {
      Fory listFory = compatibleFory(DirectListField.class, codegen);
      DirectListField listStruct = new DirectListField();
      listStruct.values = Arrays.asList(1, null, 3);
      byte[] listBytes = listFory.serialize(listStruct);

      Fory arrayFory = compatibleFory(DirectArrayField.class, codegen);
      DirectArrayField arrayStruct = (DirectArrayField) arrayFory.deserialize(listBytes);
      assertEquals(arrayStruct.values, null);
    }
  }

  @Test
  public void testNestedListArrayCompatibleReadUnsupported() {
    Fory nestedListFory = compatibleFory(NestedListField.class);
    NestedListField nestedListStruct = new NestedListField();
    nestedListStruct.values = Arrays.asList(Arrays.asList(1, 2));
    byte[] nestedListBytes = nestedListFory.serialize(nestedListStruct);

    Fory nestedArrayFory = compatibleFory(NestedArrayElementField.class);
    try {
      NestedArrayElementField readArrayStruct =
          (NestedArrayElementField) nestedArrayFory.deserialize(nestedListBytes);
      assertEquals(readArrayStruct.values, null);
    } catch (DeserializationException expected) {
      // Nested list/array positions are unsupported; generated and runtime owners may fail
      // through different existing mismatch paths.
    }

    NestedArrayElementField nestedArrayStruct = new NestedArrayElementField();
    nestedArrayStruct.values = Arrays.asList(new int[] {1, 2});
    byte[] nestedArrayBytes = nestedArrayFory.serialize(nestedArrayStruct);
    try {
      NestedListField readListStruct =
          (NestedListField) nestedListFory.deserialize(nestedArrayBytes);
      assertEquals(readListStruct.values, null);
    } catch (DeserializationException expected) {
      // Nested list/array positions are unsupported; generated and runtime owners may fail
      // through different existing mismatch paths.
    }
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
