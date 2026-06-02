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
import org.apache.fory.integration_tests.constructor.PrivateConstructorBean;
import org.apache.fory.integration_tests.model.PrivateFieldBean;
import org.apache.fory.integration_tests.publicserializer.PublicSerializerValue;
import org.apache.fory.integration_tests.publicserializer.PublicSerializerValueSerializer;
import org.apache.fory.reflect.FieldAccessor;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JpmsFieldAccessorTest {
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
}
