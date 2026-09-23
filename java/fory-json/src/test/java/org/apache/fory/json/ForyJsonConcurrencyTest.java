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

package org.apache.fory.json;

import static org.apache.fory.json.JsonTestSupport.pooledStateCount;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.json.codec.AbstractJsonValueCodec;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.writer.JsonWriter;
import org.testng.annotations.Test;

public class ForyJsonConcurrencyTest {
  @Test
  public void configuredStateCount() {
    assertEquals(pooledStateCount(ForyJson.builder().withConcurrencyLevel(3).build()), 3);
    assertEquals(pooledStateCount(ForyJson.builder().withConcurrencyLevel(1).build()), 1);
    assertEquals(
        pooledStateCount(ForyJson.builder().build()),
        Math.max(1, Runtime.getRuntime().availableProcessors() * 2));
    assertThrows(IllegalArgumentException.class, () -> ForyJson.builder().withConcurrencyLevel(0));
  }

  @Test
  public void concurrencyLimitWaits() throws Exception {
    CountDownLatch rootEntered = new CountDownLatch(1);
    CountDownLatch releaseRoot = new CountDownLatch(1);
    ForyJson json =
        ForyJson.builder()
            .withCodegen(false)
            .withConcurrencyLevel(1)
            .registerCodec(BlockingValue.class, new BlockingCodec(rootEntered, releaseRoot))
            .build();
    AtomicReference<Throwable> firstFailure = new AtomicReference<>();
    Thread first =
        new Thread(
            () -> {
              try {
                assertEquals(json.toJson(new BlockingValue()), "[\"held\\n\"]");
              } catch (Throwable t) {
                firstFailure.set(t);
              }
            });
    first.start();
    await(rootEntered);

    CountDownLatch secondStarted = new CountDownLatch(1);
    CountDownLatch secondFinished = new CountDownLatch(1);
    AtomicReference<Throwable> secondFailure = new AtomicReference<>();
    Thread second =
        new Thread(
            () -> {
              secondStarted.countDown();
              try {
                assertEquals(json.toJson("waiting"), "\"waiting\"");
              } catch (Throwable t) {
                secondFailure.set(t);
              } finally {
                secondFinished.countDown();
              }
            });
    second.start();
    await(secondStarted);
    try {
      awaitAcquireContention(second);
      assertEquals(secondFinished.getCount(), 1);
    } finally {
      releaseRoot.countDown();
    }
    await(secondFinished);
    first.join();
    second.join();
    assertFailure(firstFailure.get());
    assertFailure(secondFailure.get());
  }

  @Test
  public void concurrentBufferIsolation() throws Exception {
    CountDownLatch rootEntered = new CountDownLatch(1);
    CountDownLatch releaseRoot = new CountDownLatch(1);
    ForyJson json =
        ForyJson.builder()
            .withConcurrencyLevel(2)
            .registerCodec(BlockingValue.class, new BlockingCodec(rootEntered, releaseRoot))
            .build();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread writer =
        new Thread(
            () -> {
              try {
                assertEquals(json.toJson(new BlockingValue()), "[\"held\\n\"]");
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    writer.start();
    try {
      await(rootEntered);
      // The first state holds a partially written document while the second state's readers
      // decode into their workspace. Neither may overwrite the paused writer's bytes.
      assertEquals(json.fromJson("\"overwritten\\n\"", String.class), "overwritten\n");
      assertEquals(
          json.fromJson("\"overwritten\\n\"".getBytes(StandardCharsets.UTF_8), String.class),
          "overwritten\n");
      assertEquals(json.fromJson("\"\u4e2d\\n\"", String.class), "\u4e2d\n");
    } finally {
      releaseRoot.countDown();
      writer.join(TimeUnit.SECONDS.toMillis(30));
    }
    assertTrue(!writer.isAlive(), "Writer did not finish");
    assertFailure(failure.get());
  }

  private static void await(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for test coordination");
  }

  private static void awaitAcquireContention(Thread thread) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      for (StackTraceElement frame : thread.getStackTrace()) {
        if (frame.getClassName().equals(ForyJson.class.getName())
            && frame.getMethodName().equals("acquireContended")) {
          return;
        }
      }
      if (!thread.isAlive()) {
        fail("Root operation did not wait for an execution state");
      }
      Thread.yield();
    }
    fail("Timed out waiting for root operation contention");
  }

  private static void assertFailure(Throwable failure) {
    if (failure != null) {
      fail("Unexpected worker failure", failure);
    }
  }

  private static final class BlockingCodec extends AbstractJsonValueCodec<BlockingValue> {
    private final CountDownLatch entered;
    private final CountDownLatch release;

    private BlockingCodec(CountDownLatch entered, CountDownLatch release) {
      this.entered = entered;
      this.release = release;
    }

    @Override
    public void write(JsonWriter writer, BlockingValue value) {
      writer.writeArrayStart();
      writer.writeString("held\n");
      entered.countDown();
      try {
        assertTrue(release.await(30, TimeUnit.SECONDS), "Timed out waiting to release root codec");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      writer.writeArrayEnd();
    }

    @Override
    public BlockingValue read(JsonReader reader) {
      reader.skipValue();
      return null;
    }
  }

  private static final class BlockingValue {}
}
