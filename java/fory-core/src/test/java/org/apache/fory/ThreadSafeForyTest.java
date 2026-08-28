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

package org.apache.fory;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import lombok.Data;
import org.apache.fory.context.MetaReadContext;
import org.apache.fory.context.MetaWriteContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.pool.ThreadPoolFory;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.SharedRegistry;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.test.bean.BeanA;
import org.apache.fory.test.bean.BeanB;
import org.apache.fory.util.ExceptionUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ThreadSafeForyTest extends ForyTestBase {

  @Test
  public void testBuildThreadSafeForyPool() {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeFory();
    assertTrue(fory instanceof ThreadPoolFory);
  }

  @Test
  public void testThreadSafeBuilderNames() {
    ThreadSafeFory threadSafe =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeFory();
    ThreadSafeFory threadLocal =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadLocalFory();
    ThreadSafeFory threadPool =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeForyPool(1);

    String threadSafeName = threadSafe.execute(fory -> fory.getConfig().getName());
    String threadLocalName = threadLocal.execute(fory -> fory.getConfig().getName());
    String threadPoolName = threadPool.execute(fory -> fory.getConfig().getName());
    assertNotNull(threadSafeName);
    assertNotNull(threadLocalName);
    assertNotNull(threadPoolName);
    Assert.assertNotEquals(threadSafeName, threadLocalName);
    Assert.assertNotEquals(threadSafeName, threadPoolName);
    Assert.assertNotEquals(threadLocalName, threadPoolName);

    ThreadSafeFory named =
        Fory.builder()
            .withXlang(false)
            .withName("explicit-thread-safe-name")
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeForyPool(1);
    assertEquals(named.execute(fory -> fory.getConfig().getName()), "explicit-thread-safe-name");
  }

  @Test
  public void testFactoryConstructorsClassLoader() {
    ClassLoader custom = new ClassLoader(ClassLoader.getSystemClassLoader()) {};
    ThreadLocalFory threadLocal =
        new ThreadLocalFory(
            builder -> builder.withClassLoader(custom).requireClassRegistration(false).build());
    ThreadPoolFory threadPool =
        new ThreadPoolFory(
            builder -> builder.withClassLoader(custom).requireClassRegistration(false).build(), 2);
    assertSame(threadLocal.execute(Fory::getClassLoader), custom);
    assertSame(threadPool.execute(Fory::getClassLoader), custom);
  }

  @Test
  public void testThreadSafeRuntimesShareRegistry() throws Exception {
    ThreadLocalFory threadLocal =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadLocalFory();
    AtomicReference<SharedRegistry> threadLocalRegistry1 = new AtomicReference<>();
    AtomicReference<SharedRegistry> threadLocalRegistry2 = new AtomicReference<>();
    Thread thread1 =
        new Thread(() -> threadLocalRegistry1.set(threadLocal.execute(Fory::getSharedRegistry)));
    Thread thread2 =
        new Thread(() -> threadLocalRegistry2.set(threadLocal.execute(Fory::getSharedRegistry)));
    thread1.start();
    thread1.join();
    thread2.start();
    thread2.join();
    assertSame(threadLocalRegistry1.get(), threadLocalRegistry2.get());

    ThreadPoolFory threadPool =
        (ThreadPoolFory)
            Fory.builder()
                .withXlang(false)
                .requireClassRegistration(false)
                .withCompatible(false)
                .buildThreadSafeForyPool(2);
    CountDownLatch acquired = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<SharedRegistry> threadPoolRegistry1 = new AtomicReference<>();
    AtomicReference<SharedRegistry> threadPoolRegistry2 = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread poolThread1 =
        new Thread(
            () -> {
              try {
                threadPool.execute(
                    fory -> {
                      threadPoolRegistry1.set(fory.getSharedRegistry());
                      acquired.countDown();
                      awaitUnchecked(release);
                      return null;
                    });
              } catch (Throwable t) {
                error.compareAndSet(null, t);
              }
            });
    Thread poolThread2 =
        new Thread(
            () -> {
              try {
                threadPool.execute(
                    fory -> {
                      threadPoolRegistry2.set(fory.getSharedRegistry());
                      acquired.countDown();
                      awaitUnchecked(release);
                      return null;
                    });
              } catch (Throwable t) {
                error.compareAndSet(null, t);
              }
            });
    poolThread1.start();
    poolThread2.start();
    assertTrue(acquired.await(30, TimeUnit.SECONDS));
    release.countDown();
    poolThread1.join();
    poolThread2.join();
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
    assertSame(threadPoolRegistry1.get(), threadPoolRegistry2.get());
  }

  private static void awaitUnchecked(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test
  public void testThreadSafeSerialize() throws InterruptedException {
    BeanA beanA = BeanA.createBeanA(2);
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withAsyncCompilation(true)
            .withCompatible(false)
            .buildThreadSafeFory();
    assertConcurrentRoundTrip(fory, beanA);
  }

  @Test
  public void testPoolSerialize() throws InterruptedException {
    BeanA beanA = BeanA.createBeanA(2);
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .withAsyncCompilation(true)
            .withCompatible(false)
            .buildThreadSafeForyPool(10);
    assertConcurrentRoundTrip(fory, beanA);
  }

  @Test
  public void testRegistration() throws Exception {
    BeanB bean = BeanB.createBeanB(2);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      AtomicReference<Throwable> error = new AtomicReference<>();
      ThreadSafeFory pooled =
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(true)
              .withCompatible(false)
              .buildThreadSafeForyPool(4);
      pooled.register(BeanB.class);
      assertEquals(pooled.deserialize(pooled.serialize(bean)), bean);
      executor.execute(
          () -> {
            try {
              assertEquals(pooled.deserialize(pooled.serialize(bean)), bean);
            } catch (Throwable t) {
              error.set(t);
            }
          });
      executor.shutdown();
      assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
      assertNull(error.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testThreadPoolReusesForyAcrossThreads() throws InterruptedException {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeForyPool(1);
    AtomicReference<Integer> firstForyId = new AtomicReference<>();
    AtomicReference<Integer> secondForyId = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    try {
      Thread first =
          new Thread(
              () -> {
                try {
                  firstForyId.set(fory.execute(System::identityHashCode));
                } catch (Throwable t) {
                  error.compareAndSet(null, t);
                }
              });
      Thread second =
          new Thread(
              () -> {
                try {
                  secondForyId.set(fory.execute(System::identityHashCode));
                } catch (Throwable t) {
                  error.compareAndSet(null, t);
                }
              });
      first.start();
      first.join();
      second.start();
      second.join();
      if (error.get() != null) {
        throw new AssertionError(error.get());
      }
      assertNotNull(firstForyId.get());
      assertEquals(secondForyId.get(), firstForyId.get());
    } finally {
      // no-op
    }
  }

  @Test
  public void testSerializeWithMetaShare() throws InterruptedException {
    ThreadSafeFory plain =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeFory();
    ThreadSafeFory shared =
        Fory.builder()
            .withXlang(false)
            .withMetaShare(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeFory();
    BeanA beanA = BeanA.createBeanA(2);
    ExecutorService executorService = Executors.newFixedThreadPool(12);
    AtomicReference<Throwable> error = new AtomicReference<>();
    for (int i = 0; i < 200; i++) {
      executorService.execute(
          () -> {
            try {
              for (int j = 0; j < 10; j++) {
                byte[] serialized = plain.execute(f -> f.serialize(beanA));
                assertEquals(plain.execute(f -> f.deserialize(serialized)), beanA);

                byte[] sharedBytes =
                    shared.execute(
                        f -> {
                          f.setMetaWriteContext(new MetaWriteContext());
                          return f.serialize(beanA);
                        });
                Object sharedObj =
                    shared.execute(
                        f -> {
                          f.setMetaReadContext(new MetaReadContext());
                          return f.deserialize(sharedBytes);
                        });
                assertEquals(sharedObj, beanA);
              }
            } catch (Throwable t) {
              error.compareAndSet(null, t);
            }
          });
    }
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
  }

  @Test
  public void testThreadLocalMetaShare() throws InterruptedException {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .withMetaShare(true)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadLocalFory();
    BeanA beanA = BeanA.createBeanA(2);
    ExecutorService executorService = Executors.newFixedThreadPool(12);
    ConcurrentHashMap<Thread, MetaWriteContext> writeMetaMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<Thread, MetaReadContext> readMetaMap = new ConcurrentHashMap<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    for (int i = 0; i < 200; i++) {
      executorService.execute(
          () -> {
            try {
              for (int j = 0; j < 10; j++) {
                MetaWriteContext metaWriteContext =
                    writeMetaMap.computeIfAbsent(
                        Thread.currentThread(), t -> new MetaWriteContext());
                MetaReadContext metaReadContext =
                    readMetaMap.computeIfAbsent(Thread.currentThread(), t -> new MetaReadContext());
                byte[] serialized =
                    fory.execute(
                        f -> {
                          f.setMetaWriteContext(metaWriteContext);
                          return f.serialize(beanA);
                        });
                Object newObj =
                    fory.execute(
                        f -> {
                          f.setMetaReadContext(metaReadContext);
                          return f.deserialize(serialized);
                        });
                assertEquals(newObj, beanA);
              }
            } catch (Throwable t) {
              error.compareAndSet(null, t);
            }
          });
    }
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
  }

  @Test
  public void testSerializeDeserializeWithType() {
    for (ThreadSafeFory fory :
        new ThreadSafeFory[] {
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .buildThreadSafeFory(),
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .buildThreadSafeForyPool(2)
        }) {
      byte[] bytes = fory.serialize("abc");
      Assert.assertEquals(fory.deserialize(bytes, String.class), "abc");
      MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(8);
      fory.serialize(buffer, "abc");
      Assert.assertEquals(fory.deserialize(buffer, String.class), "abc");
    }
  }

  @Test
  public void testByteBufferPositionLimit() {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    String value = "thread-safe-byte-buffer";
    byte[] payload = writer.serialize(value);
    for (BaseFory fory :
        new BaseFory[] {
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .build(),
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .buildThreadSafeFory(),
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .buildThreadLocalFory(),
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(false)
              .withCompatible(false)
              .buildThreadSafeForyPool(2)
        }) {
      for (ByteBuffer buffer : byteBufferViews(payload)) {
        int position = buffer.position();
        int limit = buffer.limit();
        assertEquals(fory.deserialize(buffer), value);
        assertEquals(buffer.position(), position);
        assertEquals(buffer.limit(), limit);
      }
    }
  }

  private static ByteBuffer[] byteBufferViews(byte[] payload) {
    ByteBuffer heap = ByteBuffer.wrap(wrapWithPadding(payload));
    heap.position(3);
    heap.limit(3 + payload.length);

    ByteBuffer heapReadOnly = ByteBuffer.wrap(wrapWithPadding(payload)).asReadOnlyBuffer();
    heapReadOnly.position(3);
    heapReadOnly.limit(3 + payload.length);

    ByteBuffer direct = ByteBuffer.allocateDirect(payload.length + 6);
    direct.position(3);
    direct.put(payload);
    direct.position(3);
    direct.limit(3 + payload.length);

    ByteBuffer directReadOnly = direct.asReadOnlyBuffer();
    directReadOnly.position(3);
    directReadOnly.limit(3 + payload.length);

    return new ByteBuffer[] {heap, heapReadOnly, direct, directReadOnly};
  }

  private static byte[] wrapWithPadding(byte[] payload) {
    byte[] bytes = new byte[payload.length + 6];
    System.arraycopy(payload, 0, bytes, 3, payload.length);
    return bytes;
  }

  @Data
  static class Foo {
    int f1;
  }

  public static class FooSerializer extends Serializer<Foo> {
    public FooSerializer(TypeResolver typeResolver, Class<Foo> type) {
      super(typeResolver.getConfig(), type);
    }

    @Override
    public void write(WriteContext writeContext, Foo value) {
      writeContext.getBuffer().writeInt32(value.f1);
    }

    @Override
    public Foo read(ReadContext readContext) {
      Foo foo = new Foo();
      foo.f1 = readContext.getBuffer().readInt32();
      return foo;
    }
  }

  public static class CustomClassLoader extends ClassLoader {
    public CustomClassLoader(ClassLoader parent) {
      super(parent);
    }
  }

  @Test
  public void testBuilderClassLoaderStaysFixed() throws Exception {
    ClassLoader loader = new CustomClassLoader(ClassLoader.getSystemClassLoader());
    ThreadSafeFory threadSafe =
        Fory.builder()
            .withXlang(false)
            .withClassLoader(loader)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeFory();
    ThreadSafeFory threadLocal =
        Fory.builder()
            .withXlang(false)
            .withClassLoader(loader)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadLocalFory();
    ThreadSafeFory threadPool =
        Fory.builder()
            .withXlang(false)
            .withClassLoader(loader)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeForyPool(2);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      for (ThreadSafeFory fory : new ThreadSafeFory[] {threadSafe, threadLocal, threadPool}) {
        AtomicReference<ClassLoader> seen = new AtomicReference<>();
        executor.submit(() -> seen.set(fory.execute(Fory::getClassLoader))).get();
        assertSame(seen.get(), loader);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testSerializerRegister() {
    ThreadSafeFory threadSafeFory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .buildThreadSafeForyPool(2);
    threadSafeFory.registerSerializer(Foo.class, FooSerializer.class);
    threadSafeFory.execute(
        fory -> {
          Assert.assertEquals(
              fory.getTypeResolver().getSerializer(Foo.class).getClass(), FooSerializer.class);
          return null;
        });
  }

  @Test
  public void testRegisterAfterSerializeThrows() {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    fory.register(BeanA.class);
    fory.serialize("ok");
    Assert.assertThrows(ForyException.class, () -> fory.register(BeanB.class));
  }

  @Test
  public void testForyRegisterAfterSerializeThrows() {
    Fory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    fory.register(BeanA.class);
    fory.serialize("ok");
    Assert.assertThrows(ForyException.class, () -> fory.register(BeanB.class));
  }

  @Test
  public void testPoolRegisterAfterSerializeThrows() {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadSafeForyPool(2);
    fory.register(BeanA.class);
    fory.serialize("ok");
    Assert.assertThrows(ForyException.class, () -> fory.register(BeanB.class));
  }

  @Test
  public void testExecuteFreezesThreadLocal() throws Exception {
    ThreadSafeFory fory =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    fory.register(BeanA.class);

    Fory escaped = fory.execute(value -> value);
    Assert.assertThrows(ForyException.class, () -> escaped.register(BeanB.class));
    assertNull(((ClassResolver) escaped.getTypeResolver()).getRegisteredClassId(BeanB.class));

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Fory otherThreadFory =
          executor.submit(() -> fory.execute(value -> value)).get(10, TimeUnit.SECONDS);
      ClassResolver otherResolver = (ClassResolver) otherThreadFory.getTypeResolver();
      assertNotNull(otherResolver.getRegisteredClassId(BeanA.class));
      assertTrue(otherResolver.isRegistrationFinished());
      Assert.assertThrows(ForyException.class, () -> otherThreadFory.register(BeanB.class));
      assertNull(otherResolver.getRegisteredClassId(BeanB.class));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testRegistrationGateLinearization() throws Exception {
    FacadeRegistrationGate gate = new FacadeRegistrationGate(() -> {});
    CountDownLatch registrationEntered = new CountDownLatch(1);
    CountDownLatch freezeEntered = new CountDownLatch(1);
    CountDownLatch releaseRegistration = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> registration =
          executor.submit(
              () ->
                  gate.applyRegistration(
                      () -> {
                        registrationEntered.countDown();
                        awaitUnchecked(releaseRegistration);
                      }));
      assertTrue(registrationEntered.await(10, TimeUnit.SECONDS));
      Future<?> freeze =
          executor.submit(
              () -> {
                freezeEntered.countDown();
                gate.freeze();
              });
      assertTrue(freezeEntered.await(10, TimeUnit.SECONDS));
      Assert.assertThrows(TimeoutException.class, () -> freeze.get(100, TimeUnit.MILLISECONDS));

      releaseRegistration.countDown();
      registration.get(10, TimeUnit.SECONDS);
      freeze.get(10, TimeUnit.SECONDS);
      Assert.assertThrows(
          ForyException.class, () -> gate.applyRegistration(() -> Assert.fail("must not run")));
    } finally {
      releaseRegistration.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void testFreezeWaitsForChildPublish() throws Exception {
    AtomicReference<Fory> published = new AtomicReference<>();
    AtomicInteger finishSawChild = new AtomicInteger();
    FacadeRegistrationGate gate =
        new FacadeRegistrationGate(
            () -> {
              Fory child = published.get();
              assertNotNull(child);
              finishSawChild.incrementAndGet();
              child.getTypeResolver().finishRegistration();
            });
    Fory child =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .build();
    CountDownLatch publishEntered = new CountDownLatch(1);
    CountDownLatch releasePublish = new CountDownLatch(1);
    CountDownLatch freezeStarted = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Fory> initialization =
          executor.submit(
              () ->
                  gate.initializeChild(
                      () -> child,
                      value -> {
                        publishEntered.countDown();
                        awaitUnchecked(releasePublish);
                        published.set(value);
                      }));
      assertTrue(publishEntered.await(10, TimeUnit.SECONDS));
      Future<?> freeze =
          executor.submit(
              () -> {
                freezeStarted.countDown();
                gate.freeze();
              });
      assertTrue(freezeStarted.await(10, TimeUnit.SECONDS));
      Assert.assertThrows(TimeoutException.class, () -> freeze.get(100, TimeUnit.MILLISECONDS));

      releasePublish.countDown();
      assertSame(initialization.get(10, TimeUnit.SECONDS), child);
      freeze.get(10, TimeUnit.SECONDS);
      assertSame(published.get(), child);
      assertEquals(finishSawChild.get(), 1);
      assertTrue(child.getTypeResolver().isRegistrationFinished());
    } finally {
      releasePublish.countDown();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testFreezeWaitsForChildren() throws Exception {
    CountDownLatch finishEntered = new CountDownLatch(1);
    CountDownLatch releaseFinish = new CountDownLatch(1);
    FacadeRegistrationGate gate =
        new FacadeRegistrationGate(
            () -> {
              finishEntered.countDown();
              awaitUnchecked(releaseFinish);
            });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = executor.submit(gate::freeze);
      assertTrue(finishEntered.await(10, TimeUnit.SECONDS));
      CountDownLatch secondStarted = new CountDownLatch(1);
      Future<?> second =
          executor.submit(
              () -> {
                secondStarted.countDown();
                gate.freeze();
              });
      assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
      Assert.assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));

      releaseFinish.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      releaseFinish.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void testFailedFreezeStaysClosed() {
    AtomicInteger finishCalls = new AtomicInteger();
    FacadeRegistrationGate gate =
        new FacadeRegistrationGate(
            () -> {
              finishCalls.incrementAndGet();
              throw new IllegalStateException("failed");
            });

    Assert.assertThrows(IllegalStateException.class, gate::freeze);
    Assert.assertThrows(ForyException.class, gate::freeze);
    Assert.assertThrows(ForyException.class, () -> gate.applyRegistration(() -> {}));
    assertEquals(finishCalls.get(), 1);
  }

  @Test
  public void testCheckedFailureStaysClosed() {
    FacadeRegistrationGate gate = new FacadeRegistrationGate(() -> {});

    Assert.assertThrows(
        Exception.class,
        () ->
            gate.applyRegistration(
                () -> {
                  throw ExceptionUtils.throwException(new Exception("failed"));
                }));
    Assert.assertThrows(ForyException.class, gate::freeze);
    Assert.assertThrows(ForyException.class, () -> gate.applyRegistration(() -> {}));
  }

  @Test
  public void testRejectedCallbackNotReplayed() throws Exception {
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    AtomicInteger callbackCalls = new AtomicInteger();
    Assert.assertThrows(
        ForyException.class,
        () ->
            facade.registerCallback(
                child -> {
                  callbackCalls.incrementAndGet();
                  facade.serialize("freeze");
                }));
    assertEquals(callbackCalls.get(), 1);
    Assert.assertThrows(ForyException.class, () -> facade.serialize("closed"));
    assertEquals(callbackCalls.get(), 1);
  }

  @Test
  public void testNestedRegistrationCloses() throws Exception {
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    threadLocalChildren(facade);
    AtomicInteger callbackCalls = new AtomicInteger();

    Assert.assertThrows(
        ForyException.class,
        () ->
            facade.registerCallback(
                child -> {
                  callbackCalls.incrementAndGet();
                  try {
                    facade.register(BeanB.class);
                  } catch (ForyException ignored) {
                    // The outer callback must still observe the failed gate before publication.
                  }
                  child.register(BeanA.class);
                }));

    assertTrue(callbackCalls.get() > 0);
    Assert.assertThrows(ForyException.class, () -> facade.serialize("closed"));
    Assert.assertThrows(ForyException.class, () -> facade.register(BeanA.class));
  }

  @Test
  public void testReentrantRegistrationFreeze() throws Exception {
    ThreadLocalFory threadLocal =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    assertReentrantRegistrationRejected(threadLocal, threadLocalChildren(threadLocal));

    ThreadPoolFory threadPool =
        (ThreadPoolFory)
            Fory.builder()
                .withXlang(false)
                .requireClassRegistration(true)
                .withCompatible(false)
                .buildThreadSafeForyPool(2);
    Fory[] pooledFory = TestUtils.getFieldValue(threadPool, "pooledFory");
    assertReentrantRegistrationRejected(threadPool, pooledFory);
  }

  private static void assertReentrantRegistrationRejected(ThreadSafeFory facade, Fory[] children) {
    AtomicInteger creatorCalls = new AtomicInteger();
    Assert.assertThrows(
        ForyException.class,
        () ->
            facade.registerSerializerAndType(
                Foo.class,
                resolver -> {
                  creatorCalls.incrementAndGet();
                  facade.serialize("freeze");
                  return new FooSerializer(resolver, Foo.class);
                }));
    Assert.assertEquals(creatorCalls.get(), 1);
    for (Fory child : children) {
      TypeResolver resolver = child.getTypeResolver();
      assertNull(((ClassResolver) resolver).getRegisteredClassId(Foo.class));
    }
    Assert.assertThrows(ForyException.class, () -> facade.serialize("closed"));
  }

  private static Fory[] threadLocalChildren(ThreadLocalFory facade) throws Exception {
    ThreadLocal<Fory> local = TestUtils.getFieldValue(facade, "foryThreadLocal");
    Fory first = local.get();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Fory second = executor.submit(local::get).get(10, TimeUnit.SECONDS);
      return new Fory[] {first, second};
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testBuilderModuleForLateChild() throws Exception {
    AtomicInteger installs = new AtomicInteger();
    ForyModule module =
        child -> {
          installs.incrementAndGet();
          child.registerSerializerAndType(Foo.class, FooSerializer.class);
        };
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withModule(module)
            .withCompatible(false)
            .buildThreadLocalFory();
    assertEquals(installs.get(), 1);
    facade.serialize("freeze");

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Foo value = new Foo();
      value.f1 = 42;
      Foo result =
          executor
              .submit(() -> facade.deserialize(facade.serialize(value), Foo.class))
              .get(10, TimeUnit.SECONDS);
      assertEquals(result, value);
      assertEquals(installs.get(), 2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testLateChildReplaysRegistration() throws Exception {
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    facade.registerSerializerAndType(Foo.class, FooSerializer.class);
    facade.serialize("freeze");

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Foo value = new Foo();
      value.f1 = 42;
      Foo result =
          executor
              .submit(() -> facade.deserialize(facade.serialize(value), Foo.class))
              .get(10, TimeUnit.SECONDS);
      assertEquals(result, value);
      Class<?> serializerType =
          executor
              .submit(
                  () ->
                      facade.execute(
                          child -> child.getTypeResolver().getSerializer(Foo.class).getClass()))
              .get(10, TimeUnit.SECONDS);
      assertSame(serializerType, FooSerializer.class);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testReentrantReplayCleanup() throws Exception {
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    Map<Fory, Object> children = TestUtils.getFieldValue(facade, "allFory");
    AtomicInteger callbackCalls = new AtomicInteger();
    AtomicInteger replayPublished = new AtomicInteger(-1);
    facade.registerCallback(
        child -> {
          if (callbackCalls.incrementAndGet() > 1) {
            replayPublished.set(children.containsKey(child) ? 1 : 0);
            facade.serialize("nested");
          }
          child.register(BeanA.class);
        });
    facade.serialize("freeze");

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Assert.expectThrows(
          ExecutionException.class,
          () -> executor.submit(() -> facade.execute(child -> child)).get(10, TimeUnit.SECONDS));
      assertEquals(children.size(), 1);
      assertEquals(callbackCalls.get(), 2);
      assertEquals(replayPublished.get(), 0);

      Assert.assertThrows(ForyException.class, () -> facade.serialize("closed"));
      assertEquals(callbackCalls.get(), 2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void testLateChildFailureRace() throws Exception {
    ThreadLocalFory facade =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .buildThreadLocalFory();
    Map<Fory, Object> children = TestUtils.getFieldValue(facade, "allFory");
    AtomicInteger callbackCalls = new AtomicInteger();
    CountDownLatch replayEntered = new CountDownLatch(1);
    CountDownLatch releaseReplay = new CountDownLatch(1);
    facade.registerCallback(
        child -> {
          int call = callbackCalls.incrementAndGet();
          if (call == 2) {
            replayEntered.countDown();
            awaitUnchecked(releaseReplay);
            throw new IllegalStateException("failed replay");
          }
          child.register(BeanA.class);
        });
    facade.serialize("freeze");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicReference<Thread> waitingThread = new AtomicReference<>();
    CountDownLatch waitingStarted = new CountDownLatch(1);
    try {
      Future<?> failing = executor.submit(() -> facade.execute(child -> child));
      assertTrue(replayEntered.await(10, TimeUnit.SECONDS));
      Future<?> waiting =
          executor.submit(
              () -> {
                waitingThread.set(Thread.currentThread());
                waitingStarted.countDown();
                return facade.execute(child -> child);
              });
      assertTrue(waitingStarted.await(10, TimeUnit.SECONDS));
      awaitBlocked(waitingThread.get());

      releaseReplay.countDown();
      ExecutionException replayFailure =
          Assert.expectThrows(ExecutionException.class, () -> failing.get(10, TimeUnit.SECONDS));
      assertTrue(replayFailure.getCause() instanceof IllegalStateException);
      ExecutionException waitingFailure =
          Assert.expectThrows(ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
      assertTrue(waitingFailure.getCause() instanceof ForyException);
      assertEquals(callbackCalls.get(), 2);
      assertEquals(children.size(), 1);
    } finally {
      releaseReplay.countDown();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testPoolGatePrecedesBorrow() throws Exception {
    ThreadPoolFory facade =
        (ThreadPoolFory)
            Fory.builder()
                .withXlang(false)
                .requireClassRegistration(true)
                .withCompatible(false)
                .buildThreadSafeForyPool(1);
    AtomicReferenceArray<?> slots = TestUtils.getFieldValue(facade, "slots");
    CountDownLatch callbackEntered = new CountDownLatch(1);
    CountDownLatch allowReentrantRoot = new CountDownLatch(1);
    CountDownLatch rootStarted = new CountDownLatch(1);
    AtomicReference<Thread> rootThread = new AtomicReference<>();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> registration =
          executor.submit(
              () ->
                  facade.registerCallback(
                      child -> {
                        callbackEntered.countDown();
                        awaitUnchecked(allowReentrantRoot);
                        facade.execute(value -> null);
                      }));
      assertTrue(callbackEntered.await(10, TimeUnit.SECONDS));
      Future<?> root =
          executor.submit(
              () -> {
                rootThread.set(Thread.currentThread());
                rootStarted.countDown();
                return facade.execute(value -> null);
              });
      assertTrue(rootStarted.await(10, TimeUnit.SECONDS));
      awaitBlocked(rootThread.get());
      assertNotNull(slots.get(0));

      allowReentrantRoot.countDown();
      ExecutionException registrationFailure =
          Assert.expectThrows(
              ExecutionException.class, () -> registration.get(10, TimeUnit.SECONDS));
      assertTrue(registrationFailure.getCause() instanceof ForyException);
      ExecutionException rootFailure =
          Assert.expectThrows(ExecutionException.class, () -> root.get(10, TimeUnit.SECONDS));
      assertTrue(rootFailure.getCause() instanceof ForyException);
    } finally {
      allowReentrantRoot.countDown();
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  private static void awaitBlocked(Thread thread) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
      Thread.yield();
    }
    Assert.assertEquals(thread.getState(), Thread.State.BLOCKED);
  }

  @Test
  public void testExecuteFreezesPool() {
    ThreadPoolFory fory =
        (ThreadPoolFory)
            Fory.builder()
                .withXlang(false)
                .requireClassRegistration(true)
                .withCompatible(false)
                .buildThreadSafeForyPool(2);
    fory.register(BeanA.class);

    Fory escaped = fory.execute(value -> value);
    Assert.assertThrows(ForyException.class, () -> escaped.register(BeanB.class));

    Fory[] pooledFory = TestUtils.getFieldValue(fory, "pooledFory");
    for (Fory child : pooledFory) {
      ClassResolver resolver = (ClassResolver) child.getTypeResolver();
      assertNotNull(resolver.getRegisteredClassId(BeanA.class));
      assertNull(resolver.getRegisteredClassId(BeanB.class));
    }
  }

  @Test
  public void testFailedRootFreezesFacade() {
    ThreadSafeFory[] runtimes =
        new ThreadSafeFory[] {
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(true)
              .withCompatible(false)
              .buildThreadLocalFory(),
          Fory.builder()
              .withXlang(false)
              .requireClassRegistration(true)
              .withCompatible(false)
              .buildThreadSafeForyPool(2)
        };
    for (ThreadSafeFory fory : runtimes) {
      Assert.assertThrows(RuntimeException.class, () -> fory.deserialize(new byte[0]));
      Assert.assertThrows(ForyException.class, () -> fory.register(BeanB.class));
    }
  }

  private void assertConcurrentRoundTrip(ThreadSafeFory fory, BeanA beanA)
      throws InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(12);
    AtomicReference<Throwable> error = new AtomicReference<>();
    for (int i = 0; i < 2000; i++) {
      executorService.execute(
          () -> {
            for (int j = 0; j < 10; j++) {
              try {
                assertEquals(fory.deserialize(fory.serialize(beanA)), beanA);
              } catch (Throwable t) {
                error.compareAndSet(null, t);
              }
            }
          });
    }
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(30, TimeUnit.SECONDS));
    if (error.get() != null) {
      throw new AssertionError(error.get());
    }
  }
}
