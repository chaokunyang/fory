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

package org.apache.fury.benchmark;

import java.io.IOException;
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.apache.fury.logging.Logger;
import org.apache.fury.logging.LoggerFactory;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.SingleShotTime)
@CompilerControl(value = CompilerControl.Mode.INLINE)
@State(Scope.Benchmark)
@OutputTimeUnit(java.util.concurrent.TimeUnit.MILLISECONDS)
public class ThreadPoolFurySuite {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolFurySuite.class);

  private ThreadSafeFury fury =
      Fury.builder()
          .withLanguage(Language.JAVA)
          .requireClassRegistration(false)
          .withJdkClassSerializableCheck(false)
          .withRefTracking(false)
          .withCompatibleMode(CompatibleMode.COMPATIBLE)
          .withAsyncCompilation(true)
          .withRefTracking(true)
          .buildThreadSafeFuryPool(10, 60);

  private static StructBenchmark.NumericStruct struct;

  static {
    struct = StructBenchmark.NumericStruct.build();
    struct.f1 = 1;
    struct.f2 = 2;
    struct.f3 = 3;
    struct.f4 = 4;
    struct.f5 = 5;
    struct.f6 = 6;
    struct.f7 = 7;
    struct.f8 = 8;
  }

  @Benchmark()
  @Threads(10000)
  public void testObjectPool(Blackhole bh) {
    bh.consume(fury.serialize(struct));
  }

  @TearDown
  public void tearDown() {}

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      String commandLine =
          "org.apache.fury.*ObjectPoolBenchmark.* -f 1 -wi 0 -i 5 -w 2s -r 2s -rf csv";
      System.out.println(commandLine);
      args = commandLine.split(" ");
    }
    Main.main(args);
  }
}
