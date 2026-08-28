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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.Fory;
import org.apache.fory.ForyModule;
import org.apache.fory.ForyTestBase;
import org.apache.fory.TestUtils;
import org.apache.fory.builder.Generated;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.SharedRegistry;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.type.Descriptor;
import org.apache.fory.util.ExceptionUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RegisterTest extends ForyTestBase {

  @Test(dataProvider = "enableCodegen")
  public void testRegisterForCompatible(boolean enableCodegen) {
    A a = new A();
    a.setB(new B());
    ForyBuilder builder =
        Fory.builder().withXlang(false).withCodegen(enableCodegen).withCompatible(true);

    Fory fory1 = builder.build();
    fory1.register(A.class, (short) 1000);

    Fory fory2 = builder.build();
    fory2.register(A.class, (short) 1000);
    fory2.register(B.class, (short) 1001);

    A a1 = fory1.deserialize(fory2.serialize(a), A.class);
    Assert.assertNotNull(a1);
    Object b = a1.b;
    Assert.assertNotNull(b);
    Assert.assertEquals(b.getClass(), B.class);

    Fory fory3 = builder.requireClassRegistration(false).build();
    fory3.register(A.class, (short) 1000);

    A a2 = fory2.deserialize(fory3.serialize(a), A.class);
    Assert.assertNotNull(a2);
    Assert.assertEquals(a2.b.getClass(), B.class);
  }

  public static class A {
    private B b;

    public void setB(B b) {
      this.b = b;
    }
  }

  public static class B {}

  @Test
  public void testRegisterThenRegisterSerializer() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();

    fory.register(MyExt.class, 103);

    fory.registerSerializer(MyExt.class, MyExtSerializer.class);

    MyExt original = new MyExt();
    original.id = "test-123";

    byte[] bytes = fory.serialize(original);
    MyExt deserialized = (MyExt) fory.deserialize(bytes);

    Assert.assertNotNull(deserialized);
    Assert.assertEquals(deserialized.id, "test-123");
  }

  @Test
  public void testRegisterSerializerThenRegister() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();
    fory.register(MyExt.class, "test.pkg", "MyExt");
    fory.registerSerializer(MyExt.class, MyExtSerializer.class);

    MyExt original = new MyExt();
    original.id = "reverse-order-test";

    byte[] bytes = fory.serialize(original);
    MyExt deserialized = (MyExt) fory.deserialize(bytes);

    Assert.assertNotNull(deserialized);
    Assert.assertEquals(deserialized.id, "reverse-order-test");
  }

  @Test
  public void testMultipleRegisterSerializer() {
    Fory fory = Fory.builder().withXlang(true).withCompatible(true).withCodegen(false).build();

    fory.register(MyExt.class, 104);

    fory.registerSerializer(MyExt.class, MyExtSerializer.class);
    fory.registerSerializer(MyExt.class, MyExtSerializer.class);

    MyExt original = new MyExt();
    original.id = "idempotent-test";

    byte[] bytes = fory.serialize(original);
    MyExt deserialized = (MyExt) fory.deserialize(bytes);

    Assert.assertNotNull(deserialized);
    Assert.assertEquals(deserialized.id, "idempotent-test");
  }

  @Test
  public void testRegisterExtSerializerWithSharedGeneratedCodec() {
    ForyBuilder builder =
        Fory.builder()
            .withXlang(false)
            .withCodegen(true)
            .requireClassRegistration(false)
            .suppressClassRegistrationWarnings(true)
            .withCompatible(false)
            .withName("testRegisterExtSerializerWithSharedGeneratedCodec");

    Fory fory1 = builder.build();
    fory1.register(MyExt.class, 105);
    fory1.registerSerializer(MyExt.class, MyExtSerializer.class);
    ExtHolder holder1 = new ExtHolder();
    holder1.ext = new MyExt();
    holder1.ext.id = "first";

    ExtHolder copy1 = serDe(fory1, holder1);
    Assert.assertEquals(copy1.ext.id, "first");

    Fory fory2 = builder.build();
    fory2.register(MyExt.class, 105);
    fory2.registerSerializer(MyExt.class, AlternativeMyExtSerializer.class);
    ExtHolder holder2 = new ExtHolder();
    holder2.ext = new MyExt();
    holder2.ext.id = "second";

    ExtHolder copy2 = serDe(fory2, holder2);
    Assert.assertEquals(copy2.ext.id, "second");
    Assert.assertEquals(
        fory2.getTypeResolver().getSerializer(MyExt.class).getClass(),
        AlternativeMyExtSerializer.class);
  }

  public static class ExtHolder {
    public MyExt ext;
  }

  public static class MyExt {
    public String id;
  }

  public static class ParentValue {
    public int parent;
  }

  public static class ChildValue extends ParentValue {
    public int child;
  }

  public static class RecursiveValue {
    public int value;
    public RecursiveValue next;
  }

  public static class LeftValue {
    public int value;
    public RightValue right;
  }

  public static class RightValue {
    public int value;
    public LeftValue left;
  }

  public static class CustomList extends ArrayList<Object> {}

  @Test
  public void testCombinedObjectFields() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.registerSerializerAndType(ChildValue.class, ObjectSerializer.class);
    ChildValue value = new ChildValue();
    value.parent = 1;
    value.child = 2;

    ChildValue result = fory.deserialize(fory.serialize(value), ChildValue.class);

    Assert.assertEquals(result.parent, 1);
    Assert.assertEquals(result.child, 2);
  }

  @Test
  public void testCombinedRecursiveObject() {
    Fory fory = newStrictNativeFory();
    fory.registerSerializerAndType(RecursiveValue.class, ObjectSerializer.class);
    RecursiveValue value = new RecursiveValue();
    value.value = 1;
    value.next = value;

    RecursiveValue result = fory.deserialize(fory.serialize(value), RecursiveValue.class);

    Assert.assertEquals(result.value, 1);
    Assert.assertSame(result.next, result);
  }

  @Test
  public void testCombinedMutualObject() {
    Fory fory = newStrictNativeFory();
    fory.register(RightValue.class);
    fory.registerSerializerAndType(
        LeftValue.class, resolver -> new ObjectSerializer<>(resolver, LeftValue.class));
    LeftValue value = new LeftValue();
    value.value = 1;
    value.right = new RightValue();
    value.right.value = 2;
    value.right.left = value;

    LeftValue result = fory.deserialize(fory.serialize(value), LeftValue.class);

    Assert.assertEquals(result.value, 1);
    Assert.assertEquals(result.right.value, 2);
    Assert.assertSame(result.right.left, result);
  }

  @Test(dataProvider = "xlang")
  public void testCombinedInstanceValidation(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    Serializer<MyExt> serializer = new MyExtSerializer(fory.getTypeResolver());

    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> fory.registerSerializerAndType(CustomList.class, serializer));
    Assert.assertFalse(fory.getTypeResolver().isRegistered(CustomList.class));
  }

  @Test
  public void testSerializerKeepsTypeOwner() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.register(MyExt.class);
    TypeInfo typeInfo = fory.getTypeResolver().getTypeInfo(MyExt.class, false);

    fory.registerSerializer(MyExt.class, ObjectSerializer.class);

    Assert.assertSame(fory.getTypeResolver().getTypeInfo(MyExt.class, false), typeInfo);
    Assert.assertTrue(
        fory.getTypeResolver().getRawSerializer(MyExt.class) instanceof ObjectSerializer);
  }

  @Test(dataProvider = "xlang")
  public void testNestedCombinedRegistration(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();

    Assert.assertThrows(
        ForyException.class,
        () ->
            fory.registerSerializerAndType(
                MyExt.class,
                resolver -> {
                  resolver.register(ObjectField.class);
                  return new MyExtSerializer(resolver);
                }));
    Assert.assertFalse(fory.getTypeResolver().isRegistered(MyExt.class));
    Assert.assertFalse(fory.getTypeResolver().isRegistered(ObjectField.class));
  }

  @Test(dataProvider = "xlang")
  public void testCombinedConstructorFailure(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();

    Assert.assertThrows(
        IllegalStateException.class,
        () -> fory.registerSerializerAndType(MyExt.class, FailingSerializer.class));
    Assert.assertFalse(fory.getTypeResolver().isRegistered(MyExt.class));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(MyExt.class, false));
  }

  @Test
  public void testFailedDependencyCache() {
    Fory fory = newStrictNativeFory();
    AtomicReference<Serializer<?>> stagedSerializer = new AtomicReference<>();

    Assert.assertThrows(
        IllegalStateException.class,
        () ->
            fory.registerSerializerAndType(
                MyExt.class,
                resolver -> {
                  stagedSerializer.set(resolver.getSerializer(ObjectField.class));
                  throw new IllegalStateException("failed");
                }));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(ObjectField.class, false));
    Assert.assertNotSame(
        fory.getTypeResolver().getSerializer(ObjectField.class), stagedSerializer.get());
  }

  @Test
  public void testShareConflictIsAtomic() {
    SharedRegistry sharedRegistry = new SharedRegistry();
    ForyBuilder builder =
        Fory.builder()
            .withSharedRegistry(sharedRegistry)
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false);
    Fory first = builder.build();
    first.registerSerializer(MyExt.class, new FirstShareableSerializer(first.getTypeResolver()));
    Serializer<?> sharedSerializer =
        first.getTypeResolver().getTypeInfo(MyExt.class, false).getSerializer();
    Fory registered = builder.build();
    registered.registerSerializerAndType(MyExt.class, FirstShareableSerializer.class);
    Assert.assertTrue(registered.getTypeResolver().isRegistered(MyExt.class));
    TypeInfo registeredInfo = registered.getTypeResolver().getTypeInfo(MyExt.class, false);
    Assert.assertTrue(registeredInfo.getUserTypeId() >= 0);
    Assert.assertSame(registeredInfo.getSerializer(), sharedSerializer);
    Assert.assertNull(registered.getTypeResolver().getTypeInfo(ObjectField.class, false));
    Fory second = builder.build();

    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> second.registerSerializerAndType(MyExt.class, SecondShareableSerializer.class));
    Assert.assertFalse(second.getTypeResolver().isRegistered(MyExt.class));
    Assert.assertNull(second.getTypeResolver().getTypeInfo(MyExt.class, false));
    Assert.assertNull(second.getTypeResolver().getTypeInfo(ObjectField.class, false));
  }

  @Test(dataProvider = "xlang")
  public void testReentrantSerializerCreator(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.register(MyExt.class);
    TypeInfo typeInfo = fory.getTypeResolver().getTypeInfo(MyExt.class, false);
    Serializer<?> serializer = typeInfo.getSerializer();

    Assert.assertThrows(
        ForyException.class,
        () ->
            fory.registerSerializer(
                MyExt.class,
                resolver -> {
                  Assert.assertThrows(ForyException.class, () -> fory.serialize("freeze"));
                  Assert.assertThrows(ForyException.class, () -> fory.serialize("still frozen"));
                  Assert.assertThrows(ForyException.class, () -> fory.register(runtime -> {}));
                  return new MyExtSerializer(resolver);
                }));
    Assert.assertTrue(fory.getTypeResolver().isRegistrationFrozen());
    Assert.assertFalse(fory.getTypeResolver().isRegistrationFinished());
    Assert.assertSame(fory.getTypeResolver().getTypeInfo(MyExt.class, false), typeInfo);
    Assert.assertSame(typeInfo.getSerializer(), serializer);
  }

  @Test(dataProvider = "xlang")
  public void testReentrantSerializerClass(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.register(MyExt.class);
    TypeInfo typeInfo = fory.getTypeResolver().getTypeInfo(MyExt.class, false);
    Serializer<?> serializer = typeInfo.getSerializer();
    ReentrantSerializer.CONSTRUCTION.set(() -> fory.serialize("freeze"));
    try {
      Assert.assertThrows(
          ForyException.class,
          () -> fory.registerSerializer(MyExt.class, ReentrantSerializer.class));
    } finally {
      ReentrantSerializer.CONSTRUCTION.set(null);
    }

    Assert.assertTrue(fory.getTypeResolver().isRegistrationFrozen());
    Assert.assertFalse(fory.getTypeResolver().isRegistrationFinished());
    Assert.assertSame(fory.getTypeResolver().getTypeInfo(MyExt.class, false), typeInfo);
    Assert.assertSame(typeInfo.getSerializer(), serializer);
  }

  private static Fory newStrictNativeFory() {
    return Fory.builder()
        .withXlang(false)
        .withCodegen(false)
        .withRefTracking(true)
        .requireClassRegistration(true)
        .withCompatible(false)
        .build();
  }

  @Test
  public void testFrozenFacadeRegistration() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    fory.serialize(new MyExt());

    AtomicBoolean moduleInstalled = new AtomicBoolean();
    Assert.assertThrows(
        ForyException.class,
        () -> fory.register((ForyModule) runtime -> moduleInstalled.set(true)));
    Assert.assertFalse(moduleInstalled.get());

    AtomicBoolean creatorCalled = new AtomicBoolean();
    Assert.assertThrows(
        ForyException.class,
        () ->
            fory.registerSerializer(
                MyExt.class,
                resolver -> {
                  creatorCalled.set(true);
                  return new MyExtSerializer(resolver);
                }));
    Assert.assertFalse(creatorCalled.get());
  }

  @Test
  public void testReentrantModuleFreeze() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    AtomicBoolean installReturned = new AtomicBoolean();
    ForyModule module =
        runtime -> {
          runtime.serialize("freeze");
          installReturned.set(true);
        };

    Assert.assertThrows(ForyException.class, () -> fory.register(module));
    Assert.assertTrue(installReturned.get());
    Set<ForyModule> modules = TestUtils.getFieldValue(fory, "moduleRegistrations");
    Assert.assertFalse(modules.contains(module));
  }

  @Test
  public void testCheckedModuleFailure() {
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    AtomicBoolean fail = new AtomicBoolean(true);
    ForyModule module =
        runtime -> {
          if (fail.getAndSet(false)) {
            throw ExceptionUtils.throwException(new Exception("failed"));
          }
        };

    Assert.assertThrows(Exception.class, () -> fory.register(module));
    Set<ForyModule> modules = TestUtils.getFieldValue(fory, "moduleRegistrations");
    Assert.assertFalse(modules.contains(module));

    fory.register(module);
    Assert.assertTrue(modules.contains(module));
  }

  @Test
  public void testFrozenModuleDuplicateRejected() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    AtomicInteger installs = new AtomicInteger();
    ForyModule module = runtime -> installs.incrementAndGet();
    fory.register(module);
    fory.serialize("freeze");

    Assert.assertThrows(ForyException.class, () -> fory.register(module));
    Assert.assertEquals(installs.get(), 1);
  }

  @Test
  public void testModuleCycle() {
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    AtomicInteger firstInstalls = new AtomicInteger();
    AtomicInteger secondInstalls = new AtomicInteger();
    ForyModule[] modules = new ForyModule[2];
    modules[0] =
        runtime -> {
          firstInstalls.incrementAndGet();
          runtime.register(modules[1]);
        };
    modules[1] =
        runtime -> {
          secondInstalls.incrementAndGet();
          runtime.register(modules[0]);
        };

    fory.register(modules[0]);
    fory.register(modules[1]);

    Assert.assertEquals(firstInstalls.get(), 1);
    Assert.assertEquals(secondInstalls.get(), 1);
  }

  @Test(dataProvider = "xlang")
  public void testReentrantCombinedRegistration(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    ReentrantSerializer.CONSTRUCTION.set(() -> fory.serialize("freeze"));
    try {
      Assert.assertThrows(
          ForyException.class,
          () -> fory.registerSerializerAndType(MyExt.class, ReentrantSerializer.class));
    } finally {
      ReentrantSerializer.CONSTRUCTION.set(null);
    }

    Assert.assertTrue(fory.getTypeResolver().isRegistrationFrozen());
    Assert.assertFalse(fory.getTypeResolver().isRegistrationFinished());
    Assert.assertFalse(fory.getTypeResolver().isRegistered(MyExt.class));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(MyExt.class, false));
  }

  @Test
  public void testReentrantObjectRegistration() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    AtomicInteger factoryCalls = new AtomicInteger();
    fory.registerSerializerFactory(
        (resolver, type) -> {
          if (type != ObjectField.class) {
            return null;
          }
          factoryCalls.incrementAndGet();
          fory.serialize("freeze");
          return new ObjectFieldSerializer(resolver);
        });

    Assert.assertThrows(
        ForyException.class,
        () -> fory.registerSerializerAndType(ObjectHolder.class, ObjectSerializer.class));
    Assert.assertEquals(factoryCalls.get(), 1);
    Assert.assertTrue(fory.getTypeResolver().isRegistrationFrozen());
    Assert.assertFalse(fory.getTypeResolver().isRegistrationFinished());
    Assert.assertFalse(fory.getTypeResolver().isRegistered(ObjectHolder.class));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(ObjectHolder.class, false));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(ObjectField.class, false));
  }

  @Test(dataProvider = "xlang")
  public void testStaticGeneratedClassRejected(boolean xlang) {
    Fory fory =
        Fory.builder()
            .withXlang(xlang)
            .withCodegen(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    RejectedStaticSerializer.CONSTRUCTIONS.set(0);

    Assert.assertThrows(
        ForyException.class,
        () -> fory.registerSerializerAndType(ObjectHolder.class, RejectedStaticSerializer.class));
    Assert.assertEquals(RejectedStaticSerializer.CONSTRUCTIONS.get(), 0);
    Assert.assertFalse(fory.getTypeResolver().isRegistered(ObjectHolder.class));
    Assert.assertNull(fory.getTypeResolver().getTypeInfo(ObjectHolder.class, false));
  }

  public static class ReentrantSerializer extends MyExtSerializer {
    private static final AtomicReference<Runnable> CONSTRUCTION = new AtomicReference<>();

    public ReentrantSerializer(TypeResolver typeResolver) {
      super(typeResolver);
      CONSTRUCTION.get().run();
    }
  }

  public static class FailingSerializer extends MyExtSerializer {
    public FailingSerializer(TypeResolver typeResolver) {
      super(typeResolver);
      typeResolver.setSerializer(MyExt.class, this);
      throw new IllegalStateException("failed");
    }
  }

  public static final class FirstShareableSerializer extends MyExtSerializer implements Shareable {
    public FirstShareableSerializer(TypeResolver typeResolver) {
      super(typeResolver);
      typeResolver.getTypeInfo(ObjectField.class);
    }
  }

  public static final class SecondShareableSerializer extends MyExtSerializer implements Shareable {
    public SecondShareableSerializer(TypeResolver typeResolver) {
      super(typeResolver);
      typeResolver.getTypeInfo(ObjectField.class);
    }
  }

  public static class ObjectHolder {
    public ObjectField field;
  }

  public static final class ObjectField {}

  public static class ObjectFieldSerializer extends Serializer<ObjectField> {
    public ObjectFieldSerializer(TypeResolver typeResolver) {
      super(typeResolver.getConfig(), ObjectField.class);
    }

    @Override
    public void write(WriteContext writeContext, ObjectField value) {}

    @Override
    public ObjectField read(ReadContext readContext) {
      return new ObjectField();
    }
  }

  public static final class RejectedStaticSerializer
      extends Generated.GeneratedStaticCompatibleSerializer {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    public RejectedStaticSerializer(TypeResolver resolver, Class<?> type, TypeDef typeDef) {
      super(resolver, type, typeDef, Collections.emptyList());
      CONSTRUCTIONS.incrementAndGet();
    }

    @Override
    public List<Descriptor> getGeneratedDescriptors() {
      return Collections.emptyList();
    }

    @Override
    public Object readCompatible(ReadContext readContext) {
      throw new UnsupportedOperationException();
    }
  }

  public static class MyExtSerializer extends Serializer<MyExt> {
    public MyExtSerializer(TypeResolver typeResolver) {
      super(typeResolver.getConfig(), MyExt.class);
    }

    @Override
    public void write(WriteContext writeContext, MyExt value) {
      writeContext.writeString(value.id);
    }

    @Override
    public MyExt read(ReadContext readContext) {
      MyExt result = new MyExt();
      result.id = readContext.readString();
      return result;
    }
  }

  public static class AlternativeMyExtSerializer extends Serializer<MyExt> {
    public AlternativeMyExtSerializer(TypeResolver typeResolver) {
      super(typeResolver.getConfig(), MyExt.class);
    }

    @Override
    public void write(WriteContext writeContext, MyExt value) {
      writeContext.writeString(value.id);
    }

    @Override
    public MyExt read(ReadContext readContext) {
      MyExt result = new MyExt();
      result.id = readContext.readString();
      return result;
    }
  }
}
