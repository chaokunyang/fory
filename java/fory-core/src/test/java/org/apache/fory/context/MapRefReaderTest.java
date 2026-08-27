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

import org.apache.fory.TestUtils;
import org.apache.fory.collection.ObjectArray;
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
    ObjectArray<Object> readObjects = TestUtils.getFieldValue(reader, "readObjects");
    Object[] proportionateTable = readObjects.objects;
    reader.reset();
    Assert.assertSame(readObjects.objects, proportionateTable);
    for (int i = 0; i < 100; i++) {
      Assert.assertNull(proportionateTable[i]);
    }

    reader.preserveRefId();
    reader.reference("small");
    reader.reset();
    Assert.assertEquals(readObjects.objects.length, 3);
    Assert.assertNull(readObjects.objects[0]);
  }
}
