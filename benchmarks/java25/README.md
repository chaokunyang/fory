# Java 25 Direct Memory Access Benchmark

This diagnostic JMH module compares direct-buffer scalar access paths used to reason about
`MemoryBuffer` on JDK 25:

- `MemorySegment.get/set` with native-order unaligned layouts.
- `MethodHandles.byteBufferViewVarHandle` over a direct `ByteBuffer`.
- `sun.misc.Unsafe` raw native-address access over the same direct `ByteBuffer`.

Each benchmark invocation performs one absolute scalar access at a rolling aligned offset. This is
closer to generated serializer calls into `MemoryBuffer.writeInt32`, `_unsafePutInt64`, and matching
read paths than a bulk array-copy benchmark.

Build and run with JDK 25:

```bash
cd benchmarks/java25
mvn package
java -jar target/java25-memory-access-benchmarks.jar \
  'org.apache.fory.benchmark.java25.DirectMemoryAccessBenchmark.*' \
  -f 1 -wi 5 -i 5 -t 1 -w 1s -r 1s
```

Run the direct-to-heap copy benchmark:

```bash
java -jar target/java25-memory-access-benchmarks.jar \
  'org.apache.fory.benchmark.java25.DirectToHeapCopyBenchmark.*' \
  -f 1 -wi 5 -i 5 -t 1 -w 1s -r 1s
```

The benchmark class adds fork JVM options only for the explicit Unsafe baseline:

```text
--add-opens=java.base/java.nio=ALL-UNNAMED
--sun-misc-unsafe-memory-access=allow
```

These flags are not part of Fory's JDK25 zero-Unsafe deployment contract. They are present here so
the benchmark can compare supported `ByteBuffer`/FFM access with the old raw-address Unsafe
baseline in the same process shape.

If you run with `-f 0`, pass those options to the outer `java` command because JMH will not fork a
child JVM.
