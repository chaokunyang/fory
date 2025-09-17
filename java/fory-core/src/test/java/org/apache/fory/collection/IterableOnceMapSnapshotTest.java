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

package org.apache.fory.collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class IterableOnceMapSnapshotTest {

  @Test
  public void testSetMap() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();
    Map<String, Integer> originalMap = new HashMap<>();
    originalMap.put("key1", 1);
    originalMap.put("key2", 2);
    originalMap.put("key3", 3);

    snapshot.setMap(originalMap);

    assertEquals(snapshot.size(), 3);
    assertFalse(snapshot.isEmpty());

    // Verify all entries are present
    Set<Map.Entry<String, Integer>> entries = snapshot.entrySet();
    assertEquals(entries.size(), 3);

    Map<String, Integer> resultMap = new HashMap<>();
    for (Map.Entry<String, Integer> entry : entries) {
      resultMap.put(entry.getKey(), entry.getValue());
    }

    assertEquals(resultMap, originalMap);
  }

  @Test
  public void testIterator() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();
    Map<String, Integer> originalMap = new HashMap<>();
    originalMap.put("a", 1);
    originalMap.put("b", 2);
    originalMap.put("c", 3);

    snapshot.setMap(originalMap);

    Iterator<Map.Entry<String, Integer>> iterator = snapshot.entrySet().iterator();
    assertTrue(iterator.hasNext());

    int count = 0;
    while (iterator.hasNext()) {
      Map.Entry<String, Integer> entry = iterator.next();
      assertNotNull(entry);
      assertTrue(originalMap.containsKey(entry.getKey()));
      assertEquals(originalMap.get(entry.getKey()), entry.getValue());
      count++;
    }

    assertEquals(count, 3);
    assertFalse(iterator.hasNext());
  }

  @Test
  public void testClear() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();
    Map<String, Integer> originalMap = new HashMap<>();
    originalMap.put("key1", 1);
    originalMap.put("key2", 2);

    snapshot.setMap(originalMap);
    assertEquals(snapshot.size(), 2);

    snapshot.clear();

    assertEquals(snapshot.size(), 0);
    assertTrue(snapshot.isEmpty());
    assertTrue(snapshot.entrySet().isEmpty());
  }

  @Test
  public void testReuseAfterClear() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();

    // First use
    Map<String, Integer> map1 = new HashMap<>();
    map1.put("a", 1);
    map1.put("b", 2);
    snapshot.setMap(map1);
    assertEquals(snapshot.size(), 2);

    snapshot.clear();
    assertEquals(snapshot.size(), 0);

    // Second use
    Map<String, Integer> map2 = new HashMap<>();
    map2.put("x", 10);
    map2.put("y", 20);
    map2.put("z", 30);
    snapshot.setMap(map2);
    assertEquals(snapshot.size(), 3);

    // Verify content
    Map<String, Integer> resultMap = new HashMap<>();
    for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
      resultMap.put(entry.getKey(), entry.getValue());
    }
    assertEquals(resultMap, map2);
  }

  @Test
  public void testEmptyMapSet() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();
    Map<String, Integer> emptyMap = new HashMap<>();

    snapshot.setMap(emptyMap);

    assertEquals(snapshot.size(), 0);
    assertTrue(snapshot.isEmpty());
    assertFalse(snapshot.entrySet().iterator().hasNext());
  }

  @Test
  public void testEntrySetSize() {
    IterableOnceMapSnapshot<String, Integer> snapshot = new IterableOnceMapSnapshot<>();
    Map<String, Integer> originalMap = new HashMap<>();
    originalMap.put("key1", 1);
    originalMap.put("key2", 2);
    originalMap.put("key3", 3);
    originalMap.put("key4", 4);

    snapshot.setMap(originalMap);

    assertEquals(snapshot.entrySet().size(), 4);
    assertEquals(snapshot.entrySet().size(), snapshot.size());
  }
}
