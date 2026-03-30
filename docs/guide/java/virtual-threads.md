---
title: Virtual Threads
sidebar_position: 11
id: java_virtual_threads
license: |
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
---

Apache Fory Java uses `buildThreadSafeFory()` for JDK 21+ virtual-thread workloads. On JDK 21+ it
builds a fixed-size shared `ThreadPoolFory` sized to `4 * availableProcessors()`. If you need a
different fixed pool size, use `buildThreadSafeForyPool(poolSize)`.

## Use Binary APIs

When you use virtual threads, always use Fory's binary APIs:

- `serialize(Object)` or `serialize(MemoryBuffer, Object)`
- `deserialize(byte[])` or `deserialize(MemoryBuffer)`

Typical usage:

```java
ThreadSafeFory fory = Fory.builder()
    .requireClassRegistration(false)
    .buildThreadSafeFory();

byte[] bytes = fory.serialize(request);
Object value = fory.deserialize(bytes);
```

When sending data over the network, send it chunk by chunk as framed byte data. Let Fory return
bytes for serialization, or pass complete byte chunks into Fory for deserialization.

## Do Not Use Stream APIs For Large Virtual-Thread Counts

Do not use stream or channel based APIs for virtual-thread-heavy workloads:

- `serialize(OutputStream, Object)`
- `deserialize(ForyInputStream)`
- `deserialize(ForyReadableChannel)`

Those APIs keep a pooled `Fory` instance occupied for the whole blocking call. With many virtual
threads, that means many `Fory` instances stay busy while waiting on I/O.

Use stream APIs with virtual threads only when you have at most several virtual threads.

## Why Binary APIs Are The Right Fit

Serialization and deserialization are CPU work. Fory is fast, so this CPU time is usually short
compared with network transfer time.

Most RPC systems also already work with framed byte messages instead of Java object streams. For
example, gRPC uses length-delimited frames, which matches Fory's binary APIs naturally.

A good virtual-thread pattern is:

1. Read one framed message into bytes.
2. Call `fory.deserialize(bytes)`.
3. Produce the response object.
4. Call `fory.serialize(response)`.
5. Write the response bytes as the next framed chunk.

## Recommended Pattern

```java
byte[] requestBytes = readOneFrame(channel);
Request request = (Request) fory.deserialize(requestBytes);

Response response = handle(request);
byte[] responseBytes = fory.serialize(response);
writeOneFrame(channel, responseBytes);
```

This keeps Fory on the fast CPU-bound part and keeps blocking I/O outside the serializer.
