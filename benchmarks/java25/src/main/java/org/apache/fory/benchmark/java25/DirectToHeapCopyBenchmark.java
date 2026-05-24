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

package org.apache.fory.benchmark.java25;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 1,
    jvmArgsAppend = {
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--sun-misc-unsafe-memory-access=allow"
    })
@Threads(1)
public class DirectToHeapCopyBenchmark {
  private static final int BUFFER_BYTES = 64 * 1024;
  private static final Unsafe UNSAFE = loadUnsafe();
  private static final long BUFFER_ADDRESS_OFFSET = bufferAddressOffset();
  private static final int BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

  @State(Scope.Thread)
  public static class CopyState {
    @Param({"128", "256", "512", "1024", "2048"})
    int copySize;

    ByteBuffer directBuffer;
    MemorySegment directSegment;
    byte[] heapBuffer;
    MemorySegment heapSegment;
    long directAddress;

    @Setup
    public void setup() {
      directBuffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
      directSegment = MemorySegment.ofBuffer(directBuffer);
      heapBuffer = new byte[BUFFER_BYTES];
      heapSegment = MemorySegment.ofArray(heapBuffer);
      directAddress = UNSAFE.getLong(directBuffer, BUFFER_ADDRESS_OFFSET);
      for (int i = 0; i < BUFFER_BYTES; i++) {
        UNSAFE.putByte(null, directAddress + i, (byte) (i * 31));
      }
    }
  }

  @Benchmark
  public int byteBufferGet(CopyState state) {
    int copySize = state.copySize;
    byte[] heap = state.heapBuffer;
    state.directBuffer.get(0, heap, 0, copySize);
    return heap[copySize - 1];
  }

  @Benchmark
  public int memorySegmentCopy(CopyState state) {
    int copySize = state.copySize;
    byte[] heap = state.heapBuffer;
    MemorySegment.copy(state.directSegment, 0, state.heapSegment, 0, copySize);
    return heap[copySize - 1];
  }

  @Benchmark
  public int unsafeCopyMemory(CopyState state) {
    int copySize = state.copySize;
    byte[] heap = state.heapBuffer;
    UNSAFE.copyMemory(null, state.directAddress, heap, BYTE_ARRAY_OFFSET, copySize);
    return heap[copySize - 1];
  }

  private static Unsafe loadUnsafe() {
    try {
      Field field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (Unsafe) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static long bufferAddressOffset() {
    try {
      return UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}
