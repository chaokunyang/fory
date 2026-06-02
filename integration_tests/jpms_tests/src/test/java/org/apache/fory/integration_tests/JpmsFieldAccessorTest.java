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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.fory.Fory;
import org.apache.fory.integration_tests.constructor.PrivateConstructorBean;
import org.apache.fory.integration_tests.model.PrivateFieldBean;
import org.apache.fory.integration_tests.publicserializer.PublicSerializerValue;
import org.apache.fory.integration_tests.publicserializer.PublicSerializerValueSerializer;
import org.apache.fory.reflect.FieldAccessor;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JpmsFieldAccessorTest {
  private static final int JDK_MAJOR_VERSION = Runtime.version().feature();

  @Test
  public void testPrivateFieldAccess() throws Exception {
    PrivateFieldBean bean = new PrivateFieldBean(7);
    Field field = PrivateFieldBean.class.getDeclaredField("value");
    FieldAccessor accessor = FieldAccessor.createAccessor(field);
    Assert.assertEquals(accessor.getInt(bean), 7);
    accessor.putInt(bean, 9);
    Assert.assertEquals(bean.value(), 9);
  }

  @Test
  public void testPrivateFinalFieldSerialization() {
    Fory fory =
        Fory.builder().withXlang(false).withCodegen(false).requireClassRegistration(false).build();
    PrivateFieldBean result =
        (PrivateFieldBean) fory.deserialize(fory.serialize(new PrivateFieldBean(13)));
    Assert.assertEquals(result.value(), 13);
  }

  @Test
  public void testTrustedLookupFinalWrite() throws Throwable {
    PrivateFieldBean bean = new PrivateFieldBean(17);
    MethodHandles.Lookup lookup = trustedLookup(PrivateFieldBean.class);
    VarHandle handle = lookup.findVarHandle(PrivateFieldBean.class, "value", int.class);
    handle.set(bean, 19);
    Assert.assertEquals(bean.value(), 19);

    MethodHandle setter =
        lookup.findSetter(PrivateFieldBean.class, "value", int.class).asType(setterType());
    setter.invokeExact(bean, 23);
    Assert.assertEquals(bean.value(), 23);
  }

  @Test
  public void testReflectionFinalWriteDenied() throws Exception {
    if (JDK_MAJOR_VERSION < 26) {
      return;
    }
    PrivateFieldBean bean = new PrivateFieldBean(29);
    Field field = PrivateFieldBean.class.getDeclaredField("value");
    field.setAccessible(true);
    try {
      field.setInt(bean, 31);
      Assert.fail("JDK26 denial mode should reject ordinary final-field reflection writes");
    } catch (IllegalAccessException | RuntimeException expected) {
      Assert.assertEquals(bean.value(), 29);
    }
  }

  @Test
  public void testPrivateConstructorBinding() {
    Fory fory =
        Fory.builder().withXlang(false).withCodegen(false).requireClassRegistration(false).build();
    PrivateConstructorBean result =
        (PrivateConstructorBean)
            fory.deserialize(fory.serialize(PrivateConstructorBean.of("Ada", 37)));
    Assert.assertEquals(result.name(), "Ada");
    Assert.assertEquals(result.age(), 37);
  }

  @Test
  public void testPublicSerializerInExportedPackage() {
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    fory.registerSerializer(PublicSerializerValue.class, PublicSerializerValueSerializer.class);
    PublicSerializerValue result =
        (PublicSerializerValue) fory.deserialize(fory.serialize(new PublicSerializerValue(11)));
    Assert.assertEquals(result.value, 11);
  }

  private static MethodHandles.Lookup trustedLookup(Class<?> type) throws Exception {
    Class<?> jdkAccess = Class.forName("org.apache.fory.platform.internal._JDKAccess");
    Method method = jdkAccess.getMethod("_trustedLookup", Class.class);
    return (MethodHandles.Lookup) method.invoke(null, type);
  }

  private static MethodType setterType() {
    return MethodType.methodType(void.class, PrivateFieldBean.class, int.class);
  }
}
