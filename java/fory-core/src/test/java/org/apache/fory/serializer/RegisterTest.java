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

import java.util.Date;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.Language;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.MemoryBuffer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RegisterTest extends ForyTestBase {

  private static Fory newCodegenJavaFory() {
    return Fory.builder()
        .withLanguage(Language.JAVA)
        .requireClassRegistration(false)
        .withCodegen(true)
        .build();
  }

  private static CodegenWrapper newCodegenWrapper(String id, int number) {
    CodegenWrapper wrapper = new CodegenWrapper();
    wrapper.name = "wrapper-" + number;
    wrapper.number = number;
    wrapper.myExt = new MyExt();
    wrapper.myExt.id = id;
    return wrapper;
  }

  private static Fory newCodegenJavaForyIgnoringTimeRef() {
    return Fory.builder()
        .withLanguage(Language.JAVA)
        .requireClassRegistration(false)
        .withCodegen(true)
        .ignoreTimeRef(true)
        .build();
  }

  @Test(dataProvider = "enableCodegen")
  public void testRegisterForCompatible(boolean enableCodegen) {
    A a = new A();
    a.setB(new B());
    ForyBuilder builder =
        Fory.builder()
            .withLanguage(Language.JAVA)
            .withCodegen(enableCodegen)
            .withCompatibleMode(CompatibleMode.COMPATIBLE);

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

  public static class CodegenWrapper {
    public String name;
    public int number;
    public MyExt myExt;
  }

  @Test
  public void testRegisterThenRegisterSerializer() {
    Fory fory =
        Fory.builder()
            .withLanguage(Language.XLANG)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withCodegen(false)
            .build();

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
    Fory fory =
        Fory.builder()
            .withLanguage(Language.XLANG)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withCodegen(false)
            .build();
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
    Fory fory =
        Fory.builder()
            .withLanguage(Language.XLANG)
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .withCodegen(false)
            .build();

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
  public void testCodegenCacheIsolation() {
    Fory foryA = newCodegenJavaFory();
    foryA.registerSerializer(MyExt.class, MyExtSerializer.class);
    foryA.serialize(newCodegenWrapper("20", 20));

    Fory foryB = newCodegenJavaFory();
    CodegenWrapper deserialized =
        foryB.deserialize(foryB.serialize(newCodegenWrapper("40", 40)), CodegenWrapper.class);

    Assert.assertNotNull(deserialized);
    Assert.assertNotNull(deserialized.myExt);
    Assert.assertEquals(deserialized.myExt.id, "40");
  }

  @Test
  public void testCodegenCacheIsolationWithRegisterSerializerAndType() {
    Fory foryA = newCodegenJavaFory();
    foryA.registerSerializerAndType(MyExt.class, MyExtSerializer.class);
    foryA.serialize(newCodegenWrapper("20", 20));

    Fory foryB = newCodegenJavaFory();
    CodegenWrapper deserialized =
        foryB.deserialize(foryB.serialize(newCodegenWrapper("40", 40)), CodegenWrapper.class);

    Assert.assertNotNull(deserialized);
    Assert.assertNotNull(deserialized.myExt);
    Assert.assertEquals(deserialized.myExt.id, "40");
  }

  @Test
  public void testConfigHashTracksAdditionalRegistrationPaths() {
    int baseHash = newCodegenJavaFory().getConfigHash();

    Fory registerByName = newCodegenJavaFory();
    registerByName.register(MyExt.class, "test.pkg", "MyExt");
    int registerByNameHash = registerByName.getConfigHash();
    Assert.assertNotEquals(registerByNameHash, baseHash);

    Fory registerByName2 = newCodegenJavaFory();
    registerByName2.register(MyExt.class, "test.pkg", "MyExt");
    Assert.assertNotEquals(registerByName2.getConfigHash(), baseHash);

    Fory serializerBase = newCodegenJavaFory();
    serializerBase.register(MyExt.class, 103);
    int serializerBaseHash = serializerBase.getConfigHash();

    Fory serializerByFunction = newCodegenJavaFory();
    serializerByFunction.register(MyExt.class, 103);
    serializerByFunction.registerSerializer(MyExt.class, MyExtSerializer::new);
    int serializerByFunctionHash = serializerByFunction.getConfigHash();
    Assert.assertNotEquals(serializerByFunctionHash, serializerBaseHash);

    Fory serializerAndType = newCodegenJavaFory();
    serializerAndType.registerSerializerAndType(MyExt.class, MyExtSerializer.class);
    int serializerAndTypeHash = serializerAndType.getConfigHash();
    Assert.assertNotEquals(serializerAndTypeHash, baseHash);

    Fory union = newCodegenJavaFory();
    union.registerUnion(MyExt.class, 103, new MyExtSerializer(union));
    int unionHash = union.getConfigHash();
    Assert.assertNotEquals(unionHash, baseHash);

    Fory union2 = newCodegenJavaFory();
    union2.registerUnion(MyExt.class, 103, new MyExtSerializer(union2));
    Assert.assertNotEquals(union2.getConfigHash(), baseHash);
  }

  @Test
  public void testConfigHashTracksSerializerState() {
    int baseHash = newCodegenJavaForyIgnoringTimeRef().getConfigHash();

    Fory customDateSerializer1 = newCodegenJavaForyIgnoringTimeRef();
    customDateSerializer1.registerSerializer(
        Date.class, new TimeSerializers.DateSerializer(customDateSerializer1, true));
    int customHash1 = customDateSerializer1.getConfigHash();

    Fory customDateSerializer2 = newCodegenJavaForyIgnoringTimeRef();
    customDateSerializer2.registerSerializer(
        Date.class, new TimeSerializers.DateSerializer(customDateSerializer2, true));
    int customHash2 = customDateSerializer2.getConfigHash();

    Assert.assertNotEquals(customHash1, baseHash);
    Assert.assertEquals(customHash1, customHash2);
  }

  @Test
  public void testConfigHashFinalizesAfterHashAccess() {
    Fory fory = newCodegenJavaFory();
    fory.getConfigHash();
    Assert.expectThrows(ForyException.class, () -> fory.register(MyExt.class, 103));
  }

  @Test
  public void testConfigHashFinalizesAfterSerialize() {
    Fory fory = newCodegenJavaFory();
    fory.serialize(newCodegenWrapper("20", 20));
    Assert.expectThrows(
        ForyException.class, () -> fory.registerSerializer(MyExt.class, MyExtSerializer.class));
  }

  @Test
  public void testConfigHashFinalizesAfterEnsureSerializersCompiled() {
    Fory fory = newCodegenJavaFory();
    fory.register(CodegenWrapper.class);
    fory.ensureSerializersCompiled();
    Assert.expectThrows(ForyException.class, () -> fory.register(MyExt.class, 103));
  }

  public static class MyExt {
    public String id;
  }

  public static class MyExtSerializer extends Serializer<MyExt> {
    public MyExtSerializer(Fory fory) {
      super(fory, MyExt.class);
    }

    @Override
    public void write(MemoryBuffer buffer, MyExt value) {
      if (isJava) {
        fory.writeString(buffer, value.id);
      } else {
        fory.writeString(buffer, value.id);
      }
    }

    @Override
    public MyExt read(MemoryBuffer buffer) {
      MyExt result = new MyExt();
      if (isJava) {
        result.id = fory.readString(buffer);
      } else {
        result.id = fory.readString(buffer);
      }
      return result;
    }
  }
}
