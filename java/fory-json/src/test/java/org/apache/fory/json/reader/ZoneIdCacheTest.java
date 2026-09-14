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

package org.apache.fory.json.reader;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonConfig;
import org.apache.fory.json.resolver.JsonSharedRegistry;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.testng.annotations.Test;

public class ZoneIdCacheTest {
  private static final JsonConfig CONFIG = config();
  private static final JsonSharedRegistry REGISTRY = new JsonSharedRegistry(CONFIG);

  @Test
  public void hashCollisions() {
    ZoneIdCache cache = new ZoneIdCache();
    // Supply equal hashes to exercise comparison after both local and global candidate hits.
    long hash = Long.MIN_VALUE;
    ZoneId paris = read(cache, "Europe/Paris", hash);
    assertEquals(read(cache, "Europe/Vaduz", hash), ZoneId.of("Europe/Vaduz"));
    assertSame(read(cache, "Europe/Paris", hash), paris);
    ZoneIdCache other = new ZoneIdCache();
    assertEquals(read(other, "Europe/Vaduz", hash), ZoneId.of("Europe/Vaduz"));
    assertSame(read(other, "Europe/Paris", hash), paris);
    assertNotSame(read(other, "Europe/Vaduz", hash), read(other, "Europe/Vaduz", hash));
  }

  @Test
  public void localEntries() {
    ZoneIdCache cache = new ZoneIdCache();
    ZoneId zeroHash = read(cache, "GMT", 0L);
    List<String> ids = new ArrayList<>(ZoneId.getAvailableZoneIds());
    // Other tests register custom providers, whose IDs are deliberately excluded from the cache.
    // Keep only built-in IDs that ZoneId can parse; TimeZone also lists legacy short aliases.
    ids.retainAll(Arrays.asList(TimeZone.getAvailableIDs()));
    for (int quarter = -72; quarter <= 72; quarter++) {
      String offset = ZoneOffset.ofTotalSeconds(quarter * 900).getId();
      ids.add(offset);
      for (String prefix : new String[] {"UT", "UTC", "GMT"}) {
        ids.add(quarter == 0 ? prefix : prefix + offset);
      }
    }
    // More than 1024 common IDs exercise growth and reads after local retention has stopped.
    assertTrue(ids.size() > 1024);
    ZoneIdCache other = new ZoneIdCache();
    for (int pass = 0; pass < 2; pass++) {
      for (String id : ids) {
        long hash = ZoneIdCache.HASH_SEED;
        for (int i = 0; i < id.length(); i++) {
          hash = hash * ZoneIdCache.HASH_MULTIPLIER ^ id.charAt(i);
        }
        ZoneId expected = ZoneId.of(id);
        ZoneId decoded = read(cache, id, hash);
        assertEquals(decoded, expected);
        assertSame(read(other, id, hash), decoded);
      }
    }
    assertSame(read(cache, "GMT", 0L), zeroHash);
  }

  @Test
  public void textRanges() {
    for (int length = 1; length <= 128; length++) {
      byte[] expected = new byte[length];
      for (int i = 0; i < length; i++) {
        expected[i] = (byte) ('a' + i % 26);
      }
      for (int start = 0; start < 8; start++) {
        byte[] bytes = new byte[start + length];
        Arrays.fill(bytes, (byte) '!');
        System.arraycopy(expected, 0, bytes, start, length);
        Utf8JsonReader utf8 = new Utf8JsonReader(CONFIG, new JsonTypeResolver(REGISTRY), bytes);
        Latin1JsonReader latin1 =
            new Latin1JsonReader(CONFIG, new JsonTypeResolver(REGISTRY), bytes);
        for (JsonReader reader : new JsonReader[] {utf8, latin1}) {
          assertTrue(reader.matchesZoneId(start, bytes.length, expected));
          assertFalse(reader.matchesZoneId(start, bytes.length - 1, expected));
          for (int i = 0; i < length; i++) {
            bytes[start + i] ^= 1;
            assertFalse(reader.matchesZoneId(start, bytes.length, expected));
            bytes[start + i] ^= 1;
          }
        }
      }
    }
  }

  @Test
  public void concurrentPublication() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(4);
    CyclicBarrier barrier = new CyclicBarrier(4);
    try {
      List<Future<ZoneId>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        futures.add(
            executor.submit(
                () -> {
                  ZoneIdCache cache = new ZoneIdCache();
                  barrier.await();
                  return read(cache, "Europe/London", Long.MIN_VALUE + 1);
                }));
      }
      ZoneId zone = futures.get(0).get();
      assertEquals(zone, ZoneId.of("Europe/London"));
      for (Future<ZoneId> future : futures) {
        assertSame(future.get(), zone);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static ZoneId read(ZoneIdCache cache, String id, long hash) {
    Utf8JsonReader reader =
        new Utf8JsonReader(
            CONFIG, new JsonTypeResolver(REGISTRY), id.getBytes(StandardCharsets.UTF_8));
    return cache.get(reader, 0, id.length(), hash);
  }

  private static JsonConfig config() {
    try {
      Field field = ForyJson.class.getDeclaredField("config");
      field.setAccessible(true);
      return (JsonConfig) field.get(ForyJson.builder().withCodegen(false).build());
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
