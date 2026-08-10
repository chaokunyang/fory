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

package org.apache.fory.context;

import org.apache.fory.Fory;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.util.ExceptionUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MapRefReaderTest {
  @Test
  public void testResetUsesRecentSize() {
    MapRefReader reader = new MapRefReader();
    for (int i = 0; i < 100; i++) {
      reader.preserveRefId();
      reader.reference(i);
    }
    Object[] proportionateTable = reader.getReadRefs().objects;
    reader.reset();
    Assert.assertSame(reader.getReadRefs().objects, proportionateTable);

    reader.preserveRefId();
    reader.reference("small");
    reader.reset();
    Assert.assertEquals(reader.getReadRefs().objects.length, 3);
  }

  @Test
  public void testFailureUsesRefSnapshot() {
    Fory fory = Fory.builder().withRefTracking(true).build();
    MapRefReader reader = (MapRefReader) fory.getReadContext().getRefReader();
    reader.preserveRefId();
    reader.reference("original");

    DeserializationException exception =
        Assert.expectThrows(
            DeserializationException.class,
            () -> ExceptionUtils.handleReadFailed(fory, new NullPointerException()));
    reader.getReadRefs().objects[0] = "changed";
    Assert.assertTrue(exception.getMessage().contains("original"));
    Assert.assertFalse(exception.getMessage().contains("changed"));
  }

  @Test
  public void testUnresolvedRefFails() {
    MapRefReader reader = new MapRefReader();
    int refId = reader.preserveRefId();
    Assert.assertThrows(RuntimeException.class, () -> reader.getReadRef(refId));
  }
}
