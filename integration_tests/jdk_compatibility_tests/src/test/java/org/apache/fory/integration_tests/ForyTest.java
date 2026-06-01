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

import java.io.Serializable;
import org.apache.fory.Fory;
import org.apache.fory.benchmark.data.MediaContent;
import org.apache.fory.benchmark.data.Sample;
import org.apache.fory.platform.JdkVersion;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ForyTest {

  @Test
  public void testMediaContent() {
    Sample object = new Sample().populate(false);
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    byte[] data = fory.serialize(object);
    Sample sample = (Sample) fory.deserialize(data);
    Assert.assertEquals(sample, object);
  }

  @Test
  public void testSample() {
    MediaContent object = new MediaContent().populate(false);
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    byte[] data = fory.serialize(object);
    MediaContent mediaContent = (MediaContent) fory.deserialize(data);
    Assert.assertEquals(mediaContent, object);
  }

  @Test
  public void testClasspathRuntime() throws Exception {
    if (JdkVersion.MAJOR_VERSION >= 9) {
      Object module = Class.class.getMethod("getModule").invoke(Fory.class);
      boolean named = (boolean) module.getClass().getMethod("isNamed").invoke(module);
      Assert.assertFalse(named);
    }
  }

  @Test
  public void testFinalFieldBean() {
    Fory fory = Fory.builder().withXlang(false).requireClassRegistration(false).build();
    FinalFieldBean value = new FinalFieldBean("amy", 42);
    Object deserialized = fory.deserialize(fory.serialize(value));
    Assert.assertEquals(deserialized, value);
  }

  static final class FinalFieldBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int age;

    private FinalFieldBean(String name, int age) {
      this.name = name;
      this.age = age;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof FinalFieldBean)) {
        return false;
      }
      FinalFieldBean other = (FinalFieldBean) obj;
      return age == other.age && name.equals(other.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31 + age;
    }
  }
}
