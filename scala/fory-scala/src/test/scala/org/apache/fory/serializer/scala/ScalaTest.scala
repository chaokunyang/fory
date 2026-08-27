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

package org.apache.fory.serializer.scala

import java.math.{BigDecimal => JBigDecimal, BigInteger}
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import org.apache.fory.Fory
import org.apache.fory.exception.ForyException
import org.apache.fory.scala.ForyScala
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

package object SomePackageObject {
  case class SomeClass(value: Int)
}

class ScalaTest extends AnyWordSpec with Matchers {
  def fory: Fory = ForyScala.builder()
    .withXlang(false)
    .withRefTracking(true)
    .requireClassRegistration(false)
    .suppressClassRegistrationWarnings(false).build()

  "fory scala support" should {
    "serialize/deserialize package object" in {
      val p = SomePackageObject.SomeClass(1)
      fory.deserialize(fory.serialize(p)) shouldEqual p
    }
    "serialize/deserialize java.math.BigDecimal" in {
      val values = Seq(
        JBigDecimal.ZERO,
        new JBigDecimal(BigInteger.ZERO, 3),
        JBigDecimal.ONE,
        JBigDecimal.ONE.negate(),
        JBigDecimal.valueOf(12345, 2),
        new JBigDecimal(BigInteger.valueOf(Long.MaxValue), 0),
        new JBigDecimal(BigInteger.valueOf(Long.MinValue), 0),
        new JBigDecimal(BigInteger.valueOf(Long.MaxValue).add(BigInteger.ONE), 0),
        new JBigDecimal(BigInteger.valueOf(Long.MinValue).subtract(BigInteger.ONE), 0),
        new JBigDecimal(new BigInteger("123456789012345678901234567890123456789"), 37)
      )
      Seq(false, true).foreach { xlang =>
        val decimalFory = ForyScala.builder()
          .withXlang(xlang)
          .withRefTracking(true)
              .requireClassRegistration(false)
          .suppressClassRegistrationWarnings(false)
          .build()
        values.foreach { value =>
          decimalFory.deserialize(decimalFory.serialize(value)) shouldEqual value
        }
      }
    }
    "reject a root reentered during bootstrap" in {
      val loader = new BootstrapClassLoader(getClass.getClassLoader, reenterRoot = true)
      val runtime = Fory.builder()
        .withClassLoader(loader)
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()
      loader.runtime = runtime

      intercept[ForyException] {
        ScalaSerializers.registerSerializers(runtime)
      }
      intercept[ForyException] {
        ScalaSerializers.registerSerializers(runtime)
      }
    }
    "retry bootstrap after installation failure" in {
      val loader = new BootstrapClassLoader(getClass.getClassLoader, failOnce = true)
      val runtime = Fory.builder()
        .withClassLoader(loader)
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()

      intercept[IllegalStateException] {
        ScalaSerializers.registerSerializers(runtime)
      }

      ScalaSerializers.registerSerializers(runtime)
      runtime.getTypeResolver.isRegistered(
        Class.forName("scala.collection.immutable.Vector6", false, loader)
      ) shouldBe true
    }
    "reject same-thread bootstrap reentry" in {
      val loader = new BootstrapClassLoader(getClass.getClassLoader, reenterBootstrap = true)
      val runtime = Fory.builder()
        .withClassLoader(loader)
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()
      loader.runtime = runtime

      intercept[ForyException] {
        ScalaSerializers.registerSerializers(runtime)
      }
      ScalaSerializers.registerSerializers(runtime)
      runtime.getTypeResolver.isRegistered(
        Class.forName("scala.collection.immutable.Vector6", false, loader)
      ) shouldBe true
    }
    "allow nested bootstrap for another runtime" in {
      val loader = new BootstrapClassLoader(getClass.getClassLoader, reenterBootstrap = true)
      val nested = Fory.builder()
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()
      val runtime = Fory.builder()
        .withClassLoader(loader)
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()
      loader.runtime = nested

      ScalaSerializers.registerSerializers(runtime)
      nested.getTypeResolver.isRegistered(
        Class.forName("scala.collection.immutable.Vector6")
      ) shouldBe true
      runtime.getTypeResolver.isRegistered(
        Class.forName("scala.collection.immutable.Vector6", false, loader)
      ) shouldBe true
    }
    "install bootstrap exactly once concurrently" in {
      val loader = new BlockingBootstrapLoader(getClass.getClassLoader)
      val runtime = Fory.builder()
        .withClassLoader(loader)
        .withXlang(false)
        .withCodegen(false)
        .requireClassRegistration(false)
        .build()
      val executor = Executors.newFixedThreadPool(2)
      val secondThread = new AtomicReference[Thread]()
      val secondStarted = new CountDownLatch(1)

      try {
        val first = executor.submit(new Callable[Unit] {
          override def call(): Unit = ScalaSerializers.registerSerializers(runtime)
        })
        loader.targetEntered.await(10, TimeUnit.SECONDS) shouldBe true
        val second = executor.submit(new Callable[Unit] {
          override def call(): Unit = {
            secondThread.set(Thread.currentThread())
            secondStarted.countDown()
            ScalaSerializers.registerSerializers(runtime)
          }
        })
        secondStarted.await(10, TimeUnit.SECONDS) shouldBe true
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (secondThread.get().getState != Thread.State.BLOCKED &&
            System.nanoTime() < deadline) {
          Thread.`yield`()
        }
        secondThread.get().getState shouldBe Thread.State.BLOCKED
        loader.releaseTarget.countDown()
        first.get(10, TimeUnit.SECONDS)
        second.get(10, TimeUnit.SECONDS)

        loader.targetLoads.get() shouldBe 1
        runtime.getTypeResolver.isRegistered(
          Class.forName("scala.collection.immutable.Vector6", false, loader)
        ) shouldBe true
      } finally {
        loader.releaseTarget.countDown()
        executor.shutdownNow()
      }
    }
  }
  "serialize/deserialize package object in app" in {
    // If we move code in main here, we can't reproduce https://github.com/apache/fory/issues/1165.
    PkgObjectMain.main(Array())
    PkgObjectMain2.main(Array())
  }
}

private final class BootstrapClassLoader(
    parent: ClassLoader,
    reenterRoot: Boolean = false,
    failOnce: Boolean = false,
    reenterBootstrap: Boolean = false
) extends ClassLoader(parent) {
  @volatile var runtime: Fory = _
  private var shouldReenter = reenterRoot
  private var shouldFail = failOnce
  private var shouldReenterBootstrap = reenterBootstrap

  override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (name == "scala.collection.immutable.VectorImpl") {
      if (shouldReenter) {
        shouldReenter = false
        runtime.serialize("freeze")
      }
      if (shouldReenterBootstrap) {
        shouldReenterBootstrap = false
        ScalaSerializers.registerSerializers(runtime)
      }
      if (shouldFail) {
        shouldFail = false
        throw new IllegalStateException("bootstrap class loading failed")
      }
    }
    super.loadClass(name, resolve)
  }
}

private final class BlockingBootstrapLoader(parent: ClassLoader) extends ClassLoader(parent) {
  val targetEntered = new CountDownLatch(1)
  val releaseTarget = new CountDownLatch(1)
  val targetLoads = new AtomicInteger()

  override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (name == "scala.collection.immutable.VectorImpl" && targetLoads.incrementAndGet() == 1) {
      targetEntered.countDown()
      if (!releaseTarget.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out waiting to finish bootstrap class loading")
      }
    }
    super.loadClass(name, resolve)
  }
}


package object PkgObject {
  case class Id(value: Int)
  case class IdAnyVal(value: Int) extends AnyVal
}

// Test for https://github.com/apache/fory/issues/1165
object PkgObjectMain extends App {

  val fory = Fory
    .builder()
    .withXlang(false)
    .requireClassRegistration(false)
    .withRefTracking(true).suppressClassRegistrationWarnings(false)
    .build()

  import PkgObject._

  case class SomeClass(v: Id)
  val o1 = SomeClass(Id(1))
  val o2 = fory.deserialize(fory.serialize(o1))
  if (o1 != o2) {
    throw new RuntimeException(s"$o1 is not equal to $o2")
  }
}

// Test for https://github.com/apache/fory/issues/1175
object PkgObjectMain2 extends App {
  val fory = Fory
    .builder()
    .withXlang(false)
    .requireClassRegistration(false)
    .withRefTracking(true)
    .suppressClassRegistrationWarnings(false)
    .build()

  import PkgObject._

  case class SomeClass(v: List[IdAnyVal])
  val p = SomeClass(List.empty)
  val result = fory.deserialize(fory.serialize(p))
  if (result != p) {
    throw new RuntimeException(s"$result is not equal to $p")
  }
}
