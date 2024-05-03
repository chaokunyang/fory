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

package org.apache.fury;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.FuryBuilder;
import org.apache.fury.config.Language;
import org.apache.fury.resolver.MetaContext;
import org.apache.fury.test.bean.Cyclic;
import org.apache.fury.test.bean.FinalCyclic;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CyclicTest extends FuryTestBase {

  public static Object[][] beans() {
    return new Object[][] {
      {Cyclic.create(false), Cyclic.create(true)},
      {FinalCyclic.create(false), FinalCyclic.create(true)}
    };
  }

  @DataProvider
  public static Object[][] fury() {
    return Sets.cartesianProduct(
            ImmutableSet.of(true, false), // enableCodegen
            ImmutableSet.of(true, false), // async compilation
            ImmutableSet.of(
                CompatibleMode.SCHEMA_CONSISTENT, CompatibleMode.COMPATIBLE) // structFieldsRepeat
            )
        .stream()
        .map(List::toArray)
        .map(
            c ->
                new Object[] {
                  Fury.builder()
                      .withLanguage(Language.JAVA)
                      .withCodegen((Boolean) c[0])
                      .withAsyncCompilation((Boolean) c[1])
                      .withCompatibleMode((CompatibleMode) c[2])
                      .requireClassRegistration(false)
                })
        .toArray(Object[][]::new);
  }

  @Test
  public void testBean(FuryBuilder builder) {
    Fury fury = builder.withMetaContextShare(false).withRefTracking(true).build();
    for (Object[] objects : beans()) {
      Object notCyclic = objects[0];
      Object cyclic = objects[1];
      Assert.assertEquals(notCyclic, fury.deserialize(fury.serialize(notCyclic)));
      Assert.assertEquals(cyclic, fury.deserialize(fury.serialize(cyclic)));
      Object[] arr = new Object[2];
      arr[0] = arr;
      arr[1] = cyclic;
      Assert.assertEquals(arr[1], ((Object[]) fury.deserialize(fury.serialize(arr)))[1]);
      List<Object> list = new ArrayList<>();
      list.add(list);
      list.add(cyclic);
      list.add(arr);
      Assert.assertEquals(
          ((Object[]) list.get(2))[1],
          ((Object[]) ((List) fury.deserialize(fury.serialize(list))).get(2))[1]);
    }
  }

  @Test
  public void testBeanMetaShared(FuryBuilder builder) {
    Fury fury = builder.withMetaContextShare(true).withRefTracking(true).build();
    for (Object[] objects : beans()) {
      Object notCyclic = objects[0];
      Object cyclic = objects[1];
      {
        MetaContext context = new MetaContext();
        fury.getSerializationContext().setMetaContext(context);
        byte[] bytes = fury.serialize(notCyclic);
        fury.getSerializationContext().setMetaContext(context);
        Object notCyclic1 = fury.deserialize(bytes);
        Assert.assertEquals(notCyclic, notCyclic1);
      }
      {
        MetaContext context = new MetaContext();
        fury.getSerializationContext().setMetaContext(context);
        byte[] bytes = fury.serialize(cyclic);
        fury.getSerializationContext().setMetaContext(context);
        Object c = fury.deserialize(bytes);
        Assert.assertEquals(cyclic, c);
      }
      Object[] arr = new Object[2];
      arr[0] = arr;
      arr[1] = cyclic;
      Assert.assertEquals(arr[1], ((Object[]) serDeMetaShared(fury, arr))[1]);
      List<Object> list = new ArrayList<>();
      list.add(list);
      list.add(cyclic);
      list.add(arr);
      Assert.assertEquals(
          ((Object[]) list.get(2))[1], ((Object[]) ((List) serDeMetaShared(fury, list)).get(2))[1]);
    }
  }
}
