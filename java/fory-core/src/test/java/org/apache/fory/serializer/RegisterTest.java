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

import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.config.Language;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RegisterTest extends ForyTestBase {

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
    Assert.assertNull(a1.b);

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
}
