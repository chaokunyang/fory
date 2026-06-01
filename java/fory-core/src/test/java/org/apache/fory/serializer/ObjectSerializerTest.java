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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.TestUtils;
import org.apache.fory.annotation.ForyConstructor;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.builder.CodecUtils;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.test.bean.Cyclic;
import org.apache.fory.util.Preconditions;
import org.testng.Assert;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class ObjectSerializerTest extends ForyTestBase {

  @Test
  public void testLocalClass() {
    String str = "str";
    class Foo {
      public String foo(String s) {
        return str + s;
      }
    }
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(false)
            .requireClassRegistration(false)
            .build();
    ObjectSerializer serializer = new ObjectSerializer(fory.getTypeResolver(), Foo.class);
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    Foo foo = new Foo();
    writeSerializer(fory, serializer, buffer, foo);
    Object obj = readSerializer(fory, serializer, buffer);
    assertEquals(foo.foo("str"), ((Foo) obj).foo("str"));
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testLocalClass(Fory fory) {
    String str = "str";
    class Foo {
      public String foo(String s) {
        return str + s;
      }
    }
    ObjectSerializer serializer = new ObjectSerializer(fory.getTypeResolver(), Foo.class);
    Foo foo = new Foo();
    Object obj = withCopyContext(fory, context -> serializer.copy(context, foo));
    assertEquals(foo.foo("str"), ((Foo) obj).foo("str"));
    Assert.assertNotSame(foo, obj);
  }

  @Test
  public void testAnonymousClass() {
    String str = "str";
    class Foo {
      public String foo(String s) {
        return str + s;
      }
    }
    Foo foo =
        new Foo() {
          @Override
          public String foo(String s) {
            return "Anonymous " + s;
          }
        };
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(false)
            .requireClassRegistration(false)
            .build();
    ObjectSerializer serializer = new ObjectSerializer(fory.getTypeResolver(), foo.getClass());
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    writeSerializer(fory, serializer, buffer, foo);
    Object obj = readSerializer(fory, serializer, buffer);
    assertEquals(foo.foo("str"), ((Foo) obj).foo("str"));
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testAnonymousClass(Fory fory) {
    String str = "str";
    class Foo {
      public String foo(String s) {
        return str + s;
      }
    }
    Foo foo =
        new Foo() {
          @Override
          public String foo(String s) {
            return "Anonymous " + s;
          }
        };
    ObjectSerializer serializer = new ObjectSerializer(fory.getTypeResolver(), foo.getClass());
    Object obj = withCopyContext(fory, context -> serializer.copy(context, foo));
    assertEquals(foo.foo("str"), ((Foo) obj).foo("str"));
    assertNotSame(foo, obj);
  }

  @Test
  public void testSerializeCircularReference() {
    Cyclic cyclic = Cyclic.create(true);
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);

    ObjectSerializer<Cyclic> serializer =
        new ObjectSerializer<>(fory.getTypeResolver(), Cyclic.class);
    withWriteContext(
        fory,
        buffer,
        context -> {
          context.writeRefOrNull(cyclic);
          serializer.write(context, cyclic);
        });
    Cyclic cyclic1 =
        withReadContext(
            fory,
            buffer,
            context -> {
              byte tag = context.readRefOrNull();
              Preconditions.checkArgument(tag == Fory.REF_VALUE_FLAG);
              context.preserveRefId();
              return serializer.read(context);
            });
    fory.reset();
    assertEquals(cyclic1, cyclic);
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testCopyCircularReference(Fory fory) {
    Cyclic cyclic = Cyclic.create(true);
    ObjectSerializer<Cyclic> serializer =
        new ObjectSerializer<>(fory.getTypeResolver(), Cyclic.class);
    Cyclic cyclic1 = withCopyContext(fory, context -> serializer.copy(context, cyclic));
    assertEquals(cyclic1, cyclic);
    assertNotSame(cyclic1, cyclic);
  }

  public static final class ConstructorCycle {
    private final String name;
    private ConstructorCycle next;

    @ForyConstructor("name")
    public ConstructorCycle(String name) {
      this.name = name;
    }
  }

  public static final class ConstructorCycleBeforeFinal {
    @ForyField(id = 0)
    private ConstructorCycleBeforeFinal next;

    @ForyField(id = 1)
    private final String name;

    @ForyConstructor("name")
    public ConstructorCycleBeforeFinal(String name) {
      this.name = name;
    }
  }

  public static final class ConstructorOrder {
    private int id;
    private final String name;

    @ForyConstructor("name")
    public ConstructorOrder(String name) {
      this.name = name;
    }
  }

  public static final class ConstructorInterveningRef {
    @ForyField(id = 0)
    private Object first;

    @ForyField(id = 1)
    private final String name;

    @ForyField(id = 2)
    private Object second;

    @ForyConstructor("name")
    public ConstructorInterveningRef(String name) {
      this.name = name;
    }
  }

  public static final class ConstructorBackrefRoot {
    private final ConstructorBackrefChild child;

    @ForyConstructor("child")
    public ConstructorBackrefRoot(ConstructorBackrefChild child) {
      this.child = child;
    }
  }

  public static final class ConstructorBackrefChild {
    private ConstructorBackrefRoot root;
  }

  public static final class RegisteredCtorBean {
    @ForyField(id = 0)
    private final String name;

    @ForyField(id = 1)
    private final int age;

    private RegisteredCtorBean(int age, String name) {
      this.name = name;
      this.age = age;
    }
  }

  public static final class FinalNoArgBean {
    private final int id;
    private final String name;
    private int count;

    public FinalNoArgBean() {
      id = -1;
      name = "default";
    }

    private FinalNoArgBean(int value, String text, int total) {
      id = value;
      name = text;
      count = total;
    }
  }

  public static final class FinalPostCtorBean {
    private final int id;
    private String label;

    @ForyConstructor("label")
    public FinalPostCtorBean(String label) {
      id = -1;
      this.label = label;
    }

    private FinalPostCtorBean(int value, String label) {
      id = value;
      this.label = label;
    }
  }

  @Test
  public void testFinalNoArgRestore() {
    FinalNoArgBean value = new FinalNoArgBean(7, "source", 9);
    for (boolean codegen : new boolean[] {false, true}) {
      Fory fory =
          Fory.builder()
              .withXlang(false)
              .withRefTracking(true)
              .withCodegen(codegen)
              .requireClassRegistration(false)
              .build();
      FinalNoArgBean newValue = (FinalNoArgBean) fory.deserialize(fory.serialize(value));
      assertEquals(newValue.id, value.id);
      assertEquals(newValue.name, value.name);
      assertEquals(newValue.count, value.count);
    }
  }

  @Test
  public void testFinalNoArgRestoreCodegen() {
    FinalNoArgBean value = new FinalNoArgBean(7, "source", 9);
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    Serializer<FinalNoArgBean> serializer =
        Serializers.newSerializer(
            fory,
            FinalNoArgBean.class,
            CodecUtils.loadOrGenObjectCodecClass(FinalNoArgBean.class, fory));
    FinalNoArgBean newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.id, value.id);
    assertEquals(newValue.name, value.name);
    assertEquals(newValue.count, value.count);
  }

  @Test
  public void testFinalPostCtorRestore() {
    FinalPostCtorBean value = new FinalPostCtorBean(8, "ctor");
    for (boolean codegen : new boolean[] {false, true}) {
      Fory fory =
          Fory.builder()
              .withXlang(false)
              .withRefTracking(true)
              .withCodegen(codegen)
              .requireClassRegistration(false)
              .build();
      FinalPostCtorBean newValue = (FinalPostCtorBean) fory.deserialize(fory.serialize(value));
      assertEquals(newValue.id, value.id);
      assertEquals(newValue.label, value.label);
    }
  }

  @Test
  public void testFinalPostCtorCodegen() {
    FinalPostCtorBean value = new FinalPostCtorBean(8, "ctor");
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    Serializer<FinalPostCtorBean> serializer =
        Serializers.newSerializer(
            fory,
            FinalPostCtorBean.class,
            CodecUtils.loadOrGenObjectCodecClass(FinalPostCtorBean.class, fory));
    FinalPostCtorBean newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.id, value.id);
    assertEquals(newValue.label, value.label);
  }

  @Test
  public void testConstructorFieldProtocolOrder() {
    ConstructorOrder value = new ConstructorOrder("root");
    value.id = 42;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(false)
            .withNumberCompressed(false)
            .requireClassRegistration(false)
            .build();
    ObjectSerializer<ConstructorOrder> serializer =
        new ObjectSerializer<>(fory.getTypeResolver(), ConstructorOrder.class);
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    withWriteContext(fory, buffer, context -> serializer.write(context, value));
    assertEquals(buffer.readInt32(), 42);
  }

  @Test
  public void testConstructorFieldProtocolOrderCodegen() {
    ConstructorOrder value = new ConstructorOrder("root");
    value.id = 42;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .withNumberCompressed(false)
            .requireClassRegistration(false)
            .build();
    Serializer<ConstructorOrder> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorOrder.class,
            CodecUtils.loadOrGenObjectCodecClass(ConstructorOrder.class, fory));
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    withWriteContext(fory, buffer, context -> serializer.write(context, value));
    assertEquals(buffer.readInt32(), 42);
  }

  @Test
  public void testRegisterConstructor() throws Exception {
    Constructor<RegisteredCtorBean> constructor =
        RegisteredCtorBean.class.getDeclaredConstructor(int.class, String.class);
    for (boolean codegen : new boolean[] {false, true}) {
      Fory fory =
          Fory.builder()
              .withXlang(false)
              .withRefTracking(true)
              .withCodegen(codegen)
              .requireClassRegistration(false)
              .build();
      fory.registerConstructor(RegisteredCtorBean.class, constructor, "age", "name");
      assertEquals(
          fory.getTypeResolver()
              .getObjectCreator(RegisteredCtorBean.class)
              .getConstructorFieldNames(),
          new String[] {"age", "name"});
      RegisteredCtorBean value = new RegisteredCtorBean(42, "amy");
      RegisteredCtorBean newValue = (RegisteredCtorBean) fory.deserialize(fory.serialize(value));
      assertEquals(newValue.name, value.name);
      assertEquals(newValue.age, value.age);
    }
  }

  @Test
  public void testCtorInterveningRef() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    ConstructorInterveningRef newValue =
        roundTripWithSerializer(
            fory,
            new ObjectSerializer<>(fory.getTypeResolver(), ConstructorInterveningRef.class),
            newConstructorInterveningRef());
    assertInterveningRef(newValue);
  }

  @Test
  public void testCtorInterveningRefCodegen() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    Serializer<ConstructorInterveningRef> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorInterveningRef.class,
            CodecUtils.loadOrGenObjectCodecClass(ConstructorInterveningRef.class, fory));
    ConstructorInterveningRef newValue =
        roundTripWithSerializer(fory, serializer, newConstructorInterveningRef());
    assertInterveningRef(newValue);
  }

  @Test
  public void testCtorInterveningRefCompat() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorInterveningRef.class, true);
    CompatibleSerializer<ConstructorInterveningRef> serializer =
        new CompatibleSerializer<>(
            fory.getTypeResolver(), ConstructorInterveningRef.class, typeDef);
    ConstructorInterveningRef newValue =
        roundTripWithSerializer(fory, serializer, newConstructorInterveningRef());
    assertInterveningRef(newValue);
  }

  @Test
  public void testCtorInterveningRefCompatGen() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorInterveningRef.class, true);
    Serializer<ConstructorInterveningRef> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorInterveningRef.class,
            CodecUtils.loadOrGenCompatibleCodecClass(
                fory, ConstructorInterveningRef.class, typeDef));
    ConstructorInterveningRef newValue =
        roundTripWithSerializer(fory, serializer, newConstructorInterveningRef());
    assertInterveningRef(newValue);
  }

  @Test
  public void testConstructorFieldBackrefRejected() {
    if (JdkVersion.MAJOR_VERSION < 25) {
      return;
    }
    ConstructorBackrefChild child = new ConstructorBackrefChild();
    ConstructorBackrefRoot value = new ConstructorBackrefRoot(child);
    child.root = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    ObjectSerializer<ConstructorBackrefRoot> serializer =
        new ObjectSerializer<>(fory.getTypeResolver(), ConstructorBackrefRoot.class);
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    withWriteContext(
        fory,
        buffer,
        context -> {
          context.writeRefOrNull(value);
          serializer.write(context, value);
        });
    Assert.assertThrows(
        org.apache.fory.exception.ForyException.class,
        () ->
            withReadContext(
                fory,
                buffer,
                context -> {
                  byte tag = context.readRefOrNull();
                  Preconditions.checkArgument(tag == Fory.REF_VALUE_FLAG);
                  context.preserveRefId();
                  return serializer.read(context);
                }));
  }

  @Test
  public void testConstructorFieldCycle() {
    ConstructorCycle value = new ConstructorCycle("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    ConstructorCycle newValue =
        roundTripWithSerializer(
            fory, new ObjectSerializer<>(fory.getTypeResolver(), ConstructorCycle.class), value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleBeforeFinal() {
    ConstructorCycleBeforeFinal value = new ConstructorCycleBeforeFinal("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    ConstructorCycleBeforeFinal newValue =
        roundTripWithSerializer(
            fory,
            new ObjectSerializer<>(fory.getTypeResolver(), ConstructorCycleBeforeFinal.class),
            value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleCodegen() {
    ConstructorCycle value = new ConstructorCycle("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    Serializer<ConstructorCycle> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorCycle.class,
            CodecUtils.loadOrGenObjectCodecClass(ConstructorCycle.class, fory));
    ConstructorCycle newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleBeforeFinalCodegen() {
    ConstructorCycleBeforeFinal value = new ConstructorCycleBeforeFinal("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    Serializer<ConstructorCycleBeforeFinal> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorCycleBeforeFinal.class,
            CodecUtils.loadOrGenObjectCodecClass(ConstructorCycleBeforeFinal.class, fory));
    ConstructorCycleBeforeFinal newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleCompatible() {
    ConstructorCycle value = new ConstructorCycle("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorCycle.class, true);
    Serializer<ConstructorCycle> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorCycle.class,
            CodecUtils.loadOrGenCompatibleCodecClass(fory, ConstructorCycle.class, typeDef));
    ConstructorCycle newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleBeforeFinalCompatible() {
    ConstructorCycleBeforeFinal value = new ConstructorCycleBeforeFinal("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorCycleBeforeFinal.class, true);
    Serializer<ConstructorCycleBeforeFinal> serializer =
        Serializers.newSerializer(
            fory,
            ConstructorCycleBeforeFinal.class,
            CodecUtils.loadOrGenCompatibleCodecClass(
                fory, ConstructorCycleBeforeFinal.class, typeDef));
    ConstructorCycleBeforeFinal newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldCycleCompatibleNonCodegen() {
    ConstructorCycleBeforeFinal value = new ConstructorCycleBeforeFinal("root");
    value.next = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorCycleBeforeFinal.class, true);
    CompatibleSerializer<ConstructorCycleBeforeFinal> serializer =
        new CompatibleSerializer<>(
            fory.getTypeResolver(), ConstructorCycleBeforeFinal.class, typeDef);
    ConstructorCycleBeforeFinal newValue = roundTripWithSerializer(fory, serializer, value);
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  @Test
  public void testConstructorFieldBackrefCompatibleRejected() {
    if (JdkVersion.MAJOR_VERSION < 25) {
      return;
    }
    ConstructorBackrefChild child = new ConstructorBackrefChild();
    ConstructorBackrefRoot value = new ConstructorBackrefRoot(child);
    child.root = value;
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .withCompatible(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    TypeDef typeDef = fory.getTypeResolver().getTypeDef(ConstructorBackrefRoot.class, true);
    CompatibleSerializer<ConstructorBackrefRoot> serializer =
        new CompatibleSerializer<>(fory.getTypeResolver(), ConstructorBackrefRoot.class, typeDef);
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    withWriteContext(
        fory,
        buffer,
        context -> {
          context.writeRefOrNull(value);
          serializer.write(context, value);
        });
    Assert.assertThrows(
        org.apache.fory.exception.ForyException.class,
        () ->
            withReadContext(
                fory,
                buffer,
                context -> {
                  byte tag = context.readRefOrNull();
                  Preconditions.checkArgument(tag == Fory.REF_VALUE_FLAG);
                  context.preserveRefId();
                  return serializer.read(context);
                }));
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testConstructorFieldCycleCopy(Fory fory) {
    ConstructorCycle value = new ConstructorCycle("root");
    value.next = value;
    ObjectSerializer<ConstructorCycle> serializer =
        new ObjectSerializer<>(fory.getTypeResolver(), ConstructorCycle.class);
    ConstructorCycle newValue = withCopyContext(fory, context -> serializer.copy(context, value));
    assertEquals(newValue.name, value.name);
    assertSame(newValue.next, newValue);
  }

  private static <T> T roundTripWithSerializer(Fory fory, Serializer<T> serializer, T value) {
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    withWriteContext(
        fory,
        buffer,
        context -> {
          context.writeRefOrNull(value);
          serializer.write(context, value);
        });
    T newValue =
        withReadContext(
            fory,
            buffer,
            context -> {
              byte tag = context.readRefOrNull();
              Preconditions.checkArgument(tag == Fory.REF_VALUE_FLAG);
              context.preserveRefId();
              return serializer.read(context);
            });
    fory.reset();
    return newValue;
  }

  private static ConstructorInterveningRef newConstructorInterveningRef() {
    ConstructorInterveningRef value = new ConstructorInterveningRef("root");
    Object shared = new String("shared");
    value.first = shared;
    value.second = shared;
    return value;
  }

  private static void assertInterveningRef(ConstructorInterveningRef value) {
    assertEquals(value.name, "root");
    assertEquals(value.first, "shared");
    assertSame(value.second, value.first);
    Assert.assertNotSame(value.second, value);
  }

  @Data
  public static class A {
    Integer f1;
    Integer f2;
    Long f3;
    int f4;
    int f5;
    Integer f6;
    Long f7;
  }

  @Test
  public void testSerialization() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(false)
            .requireClassRegistration(false)
            .build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    ObjectSerializer<A> serializer = new ObjectSerializer<>(fory.getTypeResolver(), A.class);
    A a = new A();
    writeSerializer(fory, serializer, buffer, a);
    assertEquals(a, readSerializer(fory, serializer, buffer));
    assertEquals(a, withCopyContext(fory, context -> serializer.copy(context, a)));
  }

  @Test
  public void testAndroidObjectSerializerReflectionPaths() throws Exception {
    ProcessBuilder processBuilder =
        new ProcessBuilder(TestUtils.javaCommand(AndroidObjectSerializerProbe.class))
            .redirectErrorStream(true);
    processBuilder.environment().put("FORY_ANDROID_ENABLED", "1");
    Process process = processBuilder.start();
    String output = readFully(process.getInputStream());
    Assert.assertEquals(process.waitFor(), 0, output);
  }

  private static String readFully(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, read);
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  public static final class AndroidObjectSerializerProbe {
    public static void main(String[] args) {
      System.setProperty("java.vm.name", "Dalvik");
      System.setProperty("java.runtime.name", "Android Runtime");
      check(AndroidSupport.IS_ANDROID, "AndroidSupport should detect Dalvik runtime");

      Fory fory =
          Fory.builder()
              .withXlang(false)
              .withCodegen(true)
              .withRefTracking(true)
              .requireClassRegistration(false)
              .build();
      check(!fory.getConfig().isCodeGenEnabled(), "Android must force codegen off");

      PrivateAndroidBean bean = PrivateAndroidBean.create();
      PrivateAndroidBean restored = (PrivateAndroidBean) fory.deserialize(fory.serialize(bean));
      bean.assertSameData(restored);
      check(bean != restored, "Deserialization should create a new object");

      PrivateAndroidBean copied = fory.copy(bean);
      bean.assertSameData(copied);
      check(bean != copied, "Copy should create a new object");
      check(bean.child != copied.child, "Nested object should be copied");
    }

    private static void check(boolean value, String message) {
      if (!value) {
        throw new AssertionError(message);
      }
    }
  }

  private static final class PrivateAndroidBean {
    private int id;
    private long count;
    private Integer boxed;
    private NestedAndroidBean child;

    private PrivateAndroidBean() {}

    private static PrivateAndroidBean create() {
      PrivateAndroidBean bean = new PrivateAndroidBean();
      bean.id = 42;
      bean.count = 123456789L;
      bean.boxed = 77;
      bean.child = new NestedAndroidBean();
      bean.child.value = 9;
      return bean;
    }

    private void assertSameData(PrivateAndroidBean other) {
      Assert.assertEquals(other.id, id);
      Assert.assertEquals(other.count, count);
      Assert.assertEquals(other.boxed, boxed);
      Assert.assertNotNull(other.child);
      Assert.assertEquals(other.child.value, child.value);
    }
  }

  private static final class NestedAndroidBean {
    private int value;

    private NestedAndroidBean() {}
  }
}
