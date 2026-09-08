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
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.reflect.ReflectionUtils;
import org.testng.annotations.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
public class JdkProxySerializerTest extends ForyTestBase {

  private static class TestInvocationHandler implements InvocationHandler, Serializable {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return 1;
    }
  }

  private static class NotAHandler implements Serializable {}

  @Test(dataProvider = "referenceTrackingConfig")
  public void testJdkProxy(boolean referenceTracking) {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(referenceTracking)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    Function function =
        (Function)
            Proxy.newProxyInstance(
                fory.getClassLoader(), new Class[] {Function.class}, new TestInvocationHandler());
    Function deserializedFunction = (Function) fory.deserialize(fory.serialize(function));
    assertEquals(deserializedFunction.apply(null), 1);
  }

  @Test
  public void testRemoteProxyShapeLimit() throws Exception {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    Function function =
        (Function)
            Proxy.newProxyInstance(
                writer.getClassLoader(), new Class[] {Function.class}, new TestInvocationHandler());
    Runnable runnable =
        (Runnable)
            Proxy.newProxyInstance(
                writer.getClassLoader(), new Class[] {Runnable.class}, new TestInvocationHandler());
    byte[] functionBytes = writer.serialize(function);
    byte[] runnableBytes = writer.serialize(runnable);

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    assertEquals(((Function) reader.deserialize(functionBytes)).apply(null), 1);

    JdkProxySerializer serializer =
        (JdkProxySerializer)
            reader
                .getTypeResolver()
                .getTypeInfo(JdkProxySerializer.ReplaceStub.class)
                .getSerializer();
    Field shapesField = JdkProxySerializer.class.getDeclaredField("acceptedProxyShapes");
    shapesField.setAccessible(true);
    Map<List<Class<?>>, Class<?>[]> shapes =
        (Map<List<Class<?>>, Class<?>[]>) shapesField.get(serializer);
    Field limitField = JdkProxySerializer.class.getDeclaredField("MAX_REMOTE_PROXY_SHAPES");
    limitField.setAccessible(true);
    int limit = limitField.getInt(null);
    for (int bits = 0; shapes.size() < limit; bits++) {
      Class<?>[] shape = new Class<?>[16];
      for (int i = 0; i < shape.length; i++) {
        shape[i] = (bits & (1 << i)) == 0 ? int.class : long.class;
      }
      shapes.put(Arrays.asList(shape), shape);
    }

    assertEquals(((Function) reader.deserialize(functionBytes)).apply(null), 1);
    DeserializationException exception =
        expectThrows(DeserializationException.class, () -> reader.deserialize(runnableBytes));
    assertTrue(exception.getMessage().contains("proxy shape limit"), exception.getMessage());
    assertEquals(shapes.size(), limit);
  }

  @Test
  public void testProxyShapeReuse() throws Exception {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    writer.register(TestInvocationHandler.class);
    Class<?>[] firstShape = {Function.class, Runnable.class};
    Class<?>[] secondShape = {Runnable.class, Function.class};
    byte[][] bytes = new byte[2][];
    Class<?>[][] interfaces = {firstShape, secondShape};
    for (int i = 0; i < interfaces.length; i++) {
      Object proxy =
          Proxy.newProxyInstance(
              writer.getClassLoader(), interfaces[i], new TestInvocationHandler());
      bytes[i] = writer.serialize(proxy);
    }

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    reader.register(TestInvocationHandler.class);
    JdkProxySerializer serializer =
        (JdkProxySerializer)
            reader
                .getTypeResolver()
                .getTypeInfo(JdkProxySerializer.ReplaceStub.class)
                .getSerializer();
    Field shapesField = JdkProxySerializer.class.getDeclaredField("acceptedProxyShapes");
    shapesField.setAccessible(true);
    Map<List<Class<?>>, Class<?>[]> shapes =
        (Map<List<Class<?>>, Class<?>[]>) shapesField.get(serializer);
    Field lastShapeField = JdkProxySerializer.class.getDeclaredField("lastAcceptedProxyShape");
    lastShapeField.setAccessible(true);
    Class<?>[][] accepted = new Class<?>[2][];
    for (int i = 0; i < interfaces.length; i++) {
      Object proxy = reader.deserialize(bytes[i]);
      assertEquals(proxy.getClass().getInterfaces(), interfaces[i]);
      accepted[i] = shapes.get(Arrays.asList(interfaces[i]));
    }
    for (int round = 0; round < 3; round++) {
      for (int i = 0; i < interfaces.length; i++) {
        Object proxy = reader.deserialize(bytes[i]);
        assertEquals(proxy.getClass().getInterfaces(), interfaces[i]);
        assertSame(lastShapeField.get(serializer), accepted[i]);
      }
    }
    assertEquals(shapes.size(), 2);
  }

  @Test
  public void testFailedHandlerRecordsProxyShape() throws Exception {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    writer.register(TestInvocationHandler.class, "test.ProxyHandler");
    Function function =
        (Function)
            Proxy.newProxyInstance(
                writer.getClassLoader(), new Class[] {Function.class}, new TestInvocationHandler());
    byte[] bytes = writer.serialize(function);

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    reader.register(NotAHandler.class, "test.ProxyHandler");
    JdkProxySerializer serializer =
        (JdkProxySerializer)
            reader
                .getTypeResolver()
                .getTypeInfo(JdkProxySerializer.ReplaceStub.class)
                .getSerializer();
    Field shapesField = JdkProxySerializer.class.getDeclaredField("acceptedProxyShapes");
    shapesField.setAccessible(true);
    Map<List<Class<?>>, Class<?>[]> shapes =
        (Map<List<Class<?>>, Class<?>[]>) shapesField.get(serializer);

    expectThrows(DeserializationException.class, () -> reader.deserialize(bytes));
    assertEquals(shapes.size(), 1);
    expectThrows(DeserializationException.class, () -> reader.deserialize(bytes));
    assertEquals(shapes.size(), 1);
  }

  @Test
  public void testJdkProxyInterfaceClassHonorsTypeCheckerFalse() {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    Function function =
        (Function)
            Proxy.newProxyInstance(
                writer.getClassLoader(), new Class[] {Function.class}, new TestInvocationHandler());
    byte[] bytes = writer.serialize(function);

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withTypeChecker((resolver, className) -> !className.equals(Function.class.getName()))
            .withCompatible(false)
            .build();
    assertThrows(InsecureException.class, () -> reader.deserialize(bytes));
  }

  @Test
  public void testJdkProxyStrictInterfaces() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.register(TestInvocationHandler.class);
    Function function =
        (Function)
            Proxy.newProxyInstance(
                fory.getClassLoader(),
                new Class[] {Function.class, Serializable.class},
                new TestInvocationHandler());

    Function deserializedFunction = (Function) fory.deserialize(fory.serialize(function));
    assertEquals(deserializedFunction.apply(null), 1);
  }

  @Test
  public void testJdkProxyStrictNoDefaultInterface() {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    TestInterface function =
        (TestInterface)
            Proxy.newProxyInstance(
                writer.getClassLoader(),
                new Class[] {TestInterface.class},
                new TestInvocationHandler());
    byte[] bytes = writer.serialize(function);

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    reader.register(TestInvocationHandler.class);
    assertThrows(InsecureException.class, () -> reader.deserialize(bytes));
  }

  @Test
  public void testJdkProxyStrictRejectsDefaultInterface() {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    TestDefaultInterface function =
        (TestDefaultInterface)
            Proxy.newProxyInstance(
                writer.getClassLoader(),
                new Class[] {TestDefaultInterface.class},
                new TestInvocationHandler());
    byte[] bytes = writer.serialize(function);

    Fory reader =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    reader.register(TestInvocationHandler.class);
    assertThrows(InsecureException.class, () -> reader.deserialize(bytes));
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testJdkProxy(Fory fory) {
    Function function =
        (Function)
            Proxy.newProxyInstance(
                fory.getClassLoader(), new Class[] {Function.class}, new TestInvocationHandler());
    Function copy = fory.copy(function);
    assertNotSame(copy, function);
    assertEquals(copy.apply(null), 1);
  }

  private static class RefTestInvocationHandler implements InvocationHandler, Serializable {

    private Function proxy;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getName().equals("equals")) {
        return args[0] == this.proxy;
      }
      return "Hello world from "
          + (proxy == null
              ? "null"
              : proxy.getClass().getName() + "@" + System.identityHashCode(proxy));
    }

    private void setProxy(Function myProxy) {
      this.proxy = myProxy;
    }

    private Function getProxy() {
      return proxy;
    }
  }

  @Test
  public void testJdkProxyRef() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    RefTestInvocationHandler hdlr = new RefTestInvocationHandler();
    Function function =
        (Function)
            Proxy.newProxyInstance(fory.getClassLoader(), new Class[] {Function.class}, hdlr);
    hdlr.setProxy(function);
    assertEquals(hdlr.getProxy(), function);

    Function deserializedFunction = (Function) fory.deserialize(fory.serialize(function));
    RefTestInvocationHandler deserializedHandler =
        (RefTestInvocationHandler) Proxy.getInvocationHandler(deserializedFunction);
    assertEquals(deserializedHandler.getProxy(), deserializedFunction);
  }

  @Test(dataProvider = "foryCopyConfig")
  public void testJdkProxyRef(Fory fory) {
    RefTestInvocationHandler hdlr = new RefTestInvocationHandler();
    Function function =
        (Function)
            Proxy.newProxyInstance(fory.getClassLoader(), new Class[] {Function.class}, hdlr);
    hdlr.setProxy(function);
    assertEquals(hdlr.getProxy(), function);

    Function copy = fory.copy(function);
    RefTestInvocationHandler copyHandler =
        (RefTestInvocationHandler) Proxy.getInvocationHandler(copy);
    assertEquals(copyHandler.getProxy(), copy);
  }

  @Test
  public void testDeferredInvocationHandlerIsUnwrapped() throws Exception {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    InvocationHandler deferredHandler = newDeferredInvocationHandler(new TestInvocationHandler());
    Function function =
        (Function)
            Proxy.newProxyInstance(
                fory.getClassLoader(), new Class[] {Function.class}, deferredHandler);

    Function deserializedFunction = (Function) fory.deserialize(fory.serialize(function));
    assertEquals(deserializedFunction.apply(null), 1);
    assertTrue(Proxy.getInvocationHandler(deserializedFunction) instanceof TestInvocationHandler);

    Function copy = fory.copy(function);
    assertEquals(copy.apply(null), 1);
    assertTrue(Proxy.getInvocationHandler(copy) instanceof TestInvocationHandler);
  }

  private static InvocationHandler newDeferredInvocationHandler(InvocationHandler delegate)
      throws Exception {
    Class<?> deferredHandlerClass =
        Class.forName(JdkProxySerializer.class.getName() + "$DeferredInvocationHandler");
    Constructor<?> constructor = deferredHandlerClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object deferredHandler = constructor.newInstance();
    Method setDelegate =
        deferredHandlerClass.getDeclaredMethod("setDelegate", InvocationHandler.class);
    setDelegate.setAccessible(true);
    setDelegate.invoke(deferredHandler, delegate);
    return (InvocationHandler) deferredHandler;
  }

  @Test
  public void testSerializeProxyWriteReplace() {
    final Fory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();

    final Object o = ProxyFactory.createProxy(TestInterface.class);
    final byte[] s = fory.serialize(o);
    assertTrue(ReflectionUtils.isJdkProxy(fory.deserialize(s).getClass()));
  }

  interface TestInterface {
    int test();
  }

  interface TestDefaultInterface {
    default int test() {
      return 1;
    }
  }

  static class ProxyFactory {

    static <T> T createProxy(final Class<T> type) {
      return new JdkProxyFactory().createProxy(type);
    }

    public interface IWriteReplace {
      Object writeReplace() throws ObjectStreamException;
    }

    static final class JdkProxyFactory {

      @SuppressWarnings("unchecked")
      <T> T createProxy(final Class<T> type) {
        final JdkHandler handler = new JdkHandler(type);
        try {
          final ClassLoader cl = Thread.currentThread().getContextClassLoader();
          return (T)
              Proxy.newProxyInstance(
                  cl, new Class[] {type, IWriteReplace.class, Serializable.class}, handler);
        } catch (IllegalArgumentException e) {
          throw new RuntimeException("Could not create proxy for type [" + type.getName() + "]", e);
        }
      }

      static class JdkHandler implements InvocationHandler, IWriteReplace, Serializable {

        private final String typeName;

        private JdkHandler(Class<?> type) {
          typeName = type.getName();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if (isWriteReplaceMethod(method)) {
            return writeReplace();
          }
          return null;
        }

        public Object writeReplace() throws ObjectStreamException {
          return new ProxyReplacement(typeName);
        }

        static boolean isWriteReplaceMethod(final Method method) {
          return (method.getReturnType() == Object.class)
              && (method.getParameterTypes().length == 0)
              && method.getName().equals("writeReplace");
        }
      }

      public static final class ProxyReplacement implements Serializable {

        private final String type;

        public ProxyReplacement(final String type) {
          this.type = type;
        }

        private Object readResolve() throws ObjectStreamException {
          try {
            final Class<?> clazz =
                Class.forName(type, false, Thread.currentThread().getContextClassLoader());
            return ProxyFactory.createProxy(clazz);
          } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
          }
        }
      }
    }
  }
}
