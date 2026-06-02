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

import java.lang.reflect.Field;
import org.apache.fory.Fory;
import org.apache.fory.serializer.CodegenSerializer.LazyInitBeanSerializer;
import org.apache.fory.serializer.Serializer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class Jdk25GeneratedFieldAccessorTest {
  private static final String INSTANCE_ACCESSOR =
      "org.apache.fory.reflect.InstanceFieldAccessors$InstanceAccessor";

  @Test
  public void testConcreteAccessorField() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .withCodegen(true)
            .withRefTracking(false)
            .requireClassRegistration(false)
            .build();
    PrivateFinalBean value = new PrivateFinalBean(7, "name");

    PrivateFinalBean copy = (PrivateFinalBean) fory.deserialize(fory.serialize(value));

    Assert.assertEquals(copy.number(), 7);
    Assert.assertEquals(copy.name(), "name");
    Class<?> serializerClass = serializerClass(fory, PrivateFinalBean.class);
    assertAccessorField(serializerClass, "number");
    assertAccessorField(serializerClass, "name");
  }

  private static Class<?> serializerClass(Fory fory, Class<?> type) {
    Serializer<?> serializer = fory.getTypeResolver().getSerializer(type);
    if (serializer instanceof LazyInitBeanSerializer) {
      return ((LazyInitBeanSerializer<?>) serializer).getGeneratedSerializerClass();
    }
    return serializer.getClass();
  }

  private static void assertAccessorField(Class<?> serializerClass, String fieldName) {
    for (Field field : serializerClass.getDeclaredFields()) {
      if (field.getName().contains(fieldName + "_accessor_")) {
        Assert.assertEquals(field.getType().getName(), INSTANCE_ACCESSOR);
        return;
      }
    }
    Assert.fail("Missing generated accessor field for " + fieldName + " in " + serializerClass);
  }

  public static final class PrivateFinalBean {
    private final int number;
    private final String name;

    public PrivateFinalBean() {
      this(0, null);
    }

    public PrivateFinalBean(int number, String name) {
      this.number = number;
      this.name = name;
    }

    public int number() {
      return number;
    }

    public String name() {
      return name;
    }
  }
}
