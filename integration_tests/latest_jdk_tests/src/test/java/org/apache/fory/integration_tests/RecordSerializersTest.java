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

import static org.apache.fory.collection.Collections.ofArrayList;
import static org.apache.fory.collection.Collections.ofHashMap;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fory.Fory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.resolver.MetaContext;
import org.apache.fory.test.bean.Struct;
import org.apache.fory.util.record.RecordComponent;
import org.apache.fory.util.record.RecordUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RecordSerializersTest {

  public record Foo(int f1, String f2, List<String> f3, char f4) {}

  @Test
  public void testIsRecord() {
    Assert.assertTrue(RecordUtils.isRecord(Foo.class));
  }

  @Test
  public void testGetRecordComponents() {
    RecordComponent[] recordComponents = RecordUtils.getRecordComponents(Foo.class);
    Assert.assertNotNull(recordComponents);
    java.lang.reflect.RecordComponent[] expectComponents = Foo.class.getRecordComponents();
    Assert.assertEquals(recordComponents.length, expectComponents.length);
    Assert.assertEquals(
        recordComponents[0].getDeclaringRecord(), expectComponents[0].getDeclaringRecord());
    Assert.assertEquals(recordComponents[0].getType(), expectComponents[0].getType());
    Assert.assertEquals(recordComponents[0].getName(), expectComponents[0].getName());
  }

  @Test
  public void testGetRecordGenerics() {
    RecordComponent[] recordComponents = RecordUtils.getRecordComponents(Foo.class);
    Assert.assertNotNull(recordComponents);
    Type genericType = recordComponents[2].getGenericType();
    ParameterizedType parameterizedType = (ParameterizedType) genericType;
    Assert.assertEquals(parameterizedType.getActualTypeArguments()[0], String.class);
  }

  @DataProvider
  public static Object[][] codegen() {
    return new Object[][] {{false}, {true}};
  }

  @Test(dataProvider = "codegen")
  public void testSimpleRecord(boolean codegen) {
    Fory fory = Fory.builder().requireClassRegistration(false).withCodegen(codegen).build();
    Foo foo = new Foo(10, "abc", new ArrayList<>(Arrays.asList("a", "b")), 'x');
    Assert.assertEquals(fory.deserialize(fory.serialize(foo)), foo);
  }

  @Test(dataProvider = "codegen")
  public void testSimpleRecordMetaShared(boolean codegen) {
    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withCodegen(codegen)
            .withMetaShare(true)
            .build();
    Foo foo = new Foo(10, "abc", new ArrayList<>(Arrays.asList("a", "b")), 'x');
    MetaContext context = new MetaContext();
    fory.getSerializationContext().setMetaContext(context);
    byte[] bytes = fory.serialize(foo);
    fory.getSerializationContext().setMetaContext(context);
    Assert.assertEquals(fory.deserialize(bytes), foo);
  }

  @Test(dataProvider = "codegen")
  public void testRecordCompatible(boolean codegen) throws Throwable {
    String code1 =
        "import java.util.*;"
            + "public record TestRecord(int f1, String f2, List<String> f3, char f4, Map<String, Integer> f5) {}";
    Class<?> cls1 =
        Struct.createStructClass(
            "TestRecord", code1, RecordSerializersTest.class + "testRecordCompatible_1");
    Object record1 =
        RecordUtils.getRecordConstructor(cls1)
            .f1
            .invoke(1, "abc", ofArrayList("a", "b"), 'a', ofHashMap("a", 1));
    Fory fory =
        Fory.builder()
            .requireClassRegistration(false)
            .withCodegen(codegen)
            .withClassLoader(cls1.getClassLoader())
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .build();
    byte[] bytes1 = fory.serialize(record1);
    Object o = fory.deserialize(bytes1);
    Assert.assertEquals(o, record1);
    String code2 =
        "import java.util.*;"
            + "public record TestRecord(int f1, String f2, char f4, Map<String, Integer> f5) {}";
    Class<?> cls2 =
        Struct.createStructClass(
            "TestRecord", code2, RecordSerializersTest.class + "testRecordCompatible_2");
    Object record2 =
        RecordUtils.getRecordConstructor(cls2).f1.invoke(1, "abc", 'a', ofHashMap("a", 1));
    Fory fory2 =
        Fory.builder()
            .requireClassRegistration(false)
            .withCodegen(codegen)
            .withClassLoader(cls2.getClassLoader())
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .build();
    Object o2 = fory2.deserialize(fory2.serialize(record2));
    Assert.assertEquals(o2, record2);
    // test compatible
    Assert.assertEquals(fory2.deserialize(bytes1), record2);
  }

  @Test(dataProvider = "codegen")
  public void testRecordMetaShare(boolean codegen) throws Throwable {
    String code1 =
        "import java.util.*;"
            + "public record TestRecord(int f1, String f2, List<String> f3, char f4, Map<String, Integer> f5) {}";
    Class<?> cls1 =
        Struct.createStructClass(
            "TestRecord", code1, RecordSerializersTest.class + "testRecordMetaShare_1");
    Object record1 =
        RecordUtils.getRecordConstructor(cls1)
            .f1
            .invoke(1, "abc", ofArrayList("a", "b"), 'a', ofHashMap("a", 1));
    Fory fory1 =
        Fory.builder()
            .requireClassRegistration(false)
            .withCodegen(codegen)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withMetaShare(true)
            .withClassLoader(cls1.getClassLoader())
            .build();
    String code2 =
        "import java.util.*;"
            + "public record TestRecord(String f2, char f4, Map<String, Integer> f5) {}";
    Class<?> cls2 =
        Struct.createStructClass(
            "TestRecord", code2, RecordSerializersTest.class + "testRecordMetaShare_2");
    Object record2 =
        RecordUtils.getRecordConstructor(cls2).f1.invoke("abc", 'a', ofHashMap("a", 1));
    Fory fory2 =
        Fory.builder()
            .requireClassRegistration(false)
            .withCodegen(codegen)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withMetaShare(true)
            .withClassLoader(cls2.getClassLoader())
            .build();
    MetaContext metaContext1 = new MetaContext();
    MetaContext metaContext2 = new MetaContext();
    fory1.getSerializationContext().setMetaContext(metaContext1);
    byte[] bytes1 = fory1.serialize(record1);
    fory2.getSerializationContext().setMetaContext(metaContext2);
    Object o21 = fory2.deserialize(bytes1);
    fory2.getSerializationContext().setMetaContext(metaContext2);
    byte[] bytes2 = fory2.serialize(o21);
    fory1.getSerializationContext().setMetaContext(metaContext1);
    Object o12 = fory1.deserialize(bytes2);
    System.out.println(o12);
  }

  @Test(dataProvider = "codegen")
  public void testPrivateRecords(boolean codegen) {
    {
      Fory fory = Fory.builder().requireClassRegistration(false).withCodegen(codegen).build();
      Object o1 = Records.createPrivateRecord(11);
      Assert.assertEquals(fory.deserialize(fory.serialize(o1)), o1);
      Object o2 = Records.createPublicRecord(11, o1);
      Assert.assertEquals(fory.deserialize(fory.serialize(o2)), o2);
    }
    {
      Fory fory =
          Fory.builder()
              .requireClassRegistration(false)
              .withCodegen(codegen)
              .withCompatibleMode(CompatibleMode.COMPATIBLE)
              .build();
      Object o1 = Records.createPrivateRecord(11);
      Assert.assertEquals(fory.deserialize(fory.serialize(o1)), o1);
      Object o2 = Records.createPublicRecord(11, o1);
      Assert.assertEquals(fory.deserialize(fory.serialize(o2)), o2);
    }
    {
      Fory fory =
          Fory.builder()
              .requireClassRegistration(false)
              .withCodegen(codegen)
              .withMetaShare(true)
              .build();
      Object o1 = Records.createPrivateRecord(11);
      Object o2 = Records.createPublicRecord(11, o1);
      MetaContext context = new MetaContext();
      fory.getSerializationContext().setMetaContext(context);
      byte[] bytes = fory.serialize(o2);
      fory.getSerializationContext().setMetaContext(context);
      Assert.assertEquals(fory.deserialize(bytes), o2);
    }
  }

  @Test(dataProvider = "codegen")
  public void testPrivateRecord(boolean codegen) {
    Fory fory = Fory.builder().withCodegen(codegen).build();
    fory.register(PrivateRecord.class);
    byte[] serialized = fory.serialize(new PrivateRecord("foo")); // fails
    Object deserialized = fory.deserialize(serialized);
    System.out.println(deserialized);
  }

  private record PrivateRecord(String foo) {}

  @Test(dataProvider = "codegen")
  public void testCopy(boolean codegen) {
    Fory fory = Fory.builder().withCodegen(codegen).build();
    fory.register(Foo.class);
    fory.register(PrivateRecord.class);
    Assert.assertEquals(fory.copy(new PrivateRecord("foo")), new PrivateRecord("foo"));
    Foo foo = new Foo(10, "abc", new ArrayList<>(Arrays.asList("a", "b")), 'x');
    Assert.assertEquals(fory.copy(foo), foo);
  }
}
