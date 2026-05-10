---
title: Android Support
sidebar_position: 14
id: android_support
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

## Android Support

This page documents the Java `fory-core` Android runtime surface.

The target runtime is Android 8.0+ (API level 26+) in the existing `fory-core` artifact. Android
support is selected at runtime by `org.apache.fory.platform.AndroidSupport`; no separate Android
artifact is required for core object serialization.

`java/fory-format` is not part of the Android support surface. Row-format direct-memory APIs remain
JVM-only.

Android does not allow Fory runtime serialization paths to rely on `sun.misc.Unsafe`, private-field
`MethodHandle` access, dynamic bytecode loading, or `LambdaMetafactory`. Android-specific code paths
must use public platform APIs or fail with targeted exceptions when Android cannot preserve JVM
semantics.

## Target Support Surface

The Android target includes:

- `Fory#serialize(Object)` returning `byte[]`.
- `Fory#deserialize(byte[])`.
- `BaseFory#deserialize(ByteBuffer)` through copy into a Fory-owned heap `MemoryBuffer`.
- Stream, channel, and out-of-band buffer APIs through safe heap, byte-array, or `ByteBuffer` copy
  paths.
- Interpreter object serializers with reflection-backed field access.
- Normal Java collections/maps and xlang collection/map protocols.

Unsupported or removed behavior:

- Runtime serializer code generation and async compilation.
- Lambda and `SerializedLambda` serialization. Registration still succeeds for stable internal type
  ids, but write/read/copy operations throw an unsupported exception.
- Native-address serialize/deserialize APIs and native-address `MemoryBuffer` wrapping.
- Raw-address direct `ByteBuffer` zero-copy.
- Raw unsafe `MemoryBuffer` copy APIs remain JVM-only and throw on Android.
- `java/fory-format` row-format APIs, including direct-memory binary row copy paths.
- Field type-use annotation metadata that depends on `Field#getAnnotatedType()` on the JVM.
  Android API 26 field metadata uses generic field types and field annotations exposed by Android
  reflection.
- Object deserialization that cannot be completed through reflection.

## Codegen

`ForyBuilder` stores the codegen request as a nullable `Boolean`. Unset codegen defaults to disabled
on Android and GraalVM native image, and enabled on ordinary JVM. Explicit `withCodegen(true)` on
Android or GraalVM does not throw; build finalization forces codegen off and emits an explicit
warning. Explicit `withCodegen(false)` and platform-default disabled codegen do not warn.

Android runtime codegen entry points must fail before Janino, class definition, generated accessor
definition, or generated serializer loading starts.

## ByteBuffer

On Android, `BaseFory#deserialize(ByteBuffer)` copies the remaining input bytes into a Fory-owned
heap `MemoryBuffer`, then deserializes from that buffer. Heap, direct, and readonly inputs are
supported through the copy path. The caller buffer position and limit are not changed.

Raw direct-buffer address wrapping remains a JVM-only fast path and is not used on Android.

## Direct Memory And Row Format

Android `fory-core` paths do not execute `sun.misc.Unsafe` operations. Direct-memory copy APIs are
JVM-only paths for existing JVM users such as `java/fory-format`; they throw before unsafe execution
when Android is detected.

Use core object serialization on Android. Do not use `java/fory-format` row-format APIs on Android.

## JDK Collection And Map Wrappers

In native Java mode, Android does not add a new wrapper protocol branch and does not rewrite normal
collection/map serializers globally.

For `UnmodifiableSerializers` and `SynchronizedSerializers`, Android keeps the outer wrapper
serializer and writes a public source collection/map payload in the existing backing-value slot:

- list wrappers use `ArrayList` source type info.
- set wrappers use `HashSet` source type info.
- sorted or navigable set wrappers use `TreeSet` source type info.
- map wrappers use `HashMap` source type info.
- sorted or navigable map wrappers use `TreeMap` source type info.

The wrapper read path rewraps that source through `Collections.unmodifiable*` or
`Collections.synchronized*`. Synchronized wrapper write and copy paths must hold the wrapper lock
while iterating public contents.

Sublist views keep a unified serializer protocol. Android writes visible elements; JVM may write
source-list view metadata when supported. Both Android and JVM readers accept both payload modes.

Other JDK collection serializers keep their existing Java native protocol shape while avoiding
hidden-field unsafe access on Android. `Arrays.asList` writes the existing array payload,
`Collections.newSetFromMap` writes a `HashMap` backing-map payload, bounded blocking queues derive
capacity through public APIs, non-empty `EnumMap` derives its key type from the first key, empty
Android `EnumMap` writes a self-describing Java-serialization fallback, and immutable JDK
collections are materialized through public unmodifiable containers on Android.

In xlang mode, collection and map serialization uses the xlang collection/map protocol and does not
encode Java wrapper/view internals.

## JDK Dynamic Proxies

The Android design supports `java.lang.reflect.Proxy` serialization.

The Android proxy path must use only public proxy APIs:

- `Proxy.getInvocationHandler(proxy)` to read the handler during write and copy.
- `Proxy.newProxyInstance(classLoader, interfaces, handler)` to construct proxies during read and
  copy.
- Normal Fory reference serialization for the proxy interface array and invocation handler.

Android must not read or write the private `Proxy.h` field, request a field offset for that field,
or use `Unsafe` to replace the handler after proxy construction.

For non-cyclic proxies, read constructs the proxy directly with the deserialized invocation handler.
For cyclic proxy graphs with reference tracking, Android uses a private deferred invocation handler:

1. Create the proxy with a deferred handler.
2. Register the proxy in the read or copy reference table before reading or copying the real handler.
3. Read or copy the real handler through the normal Fory object path.
4. Install the real handler into the deferred handler.

This preserves cycles where an invocation handler references its own proxy without mutating private
JDK fields. The deferred handler is an internal implementation detail and must never be written as
the user handler. When writing or copying a proxy, `JdkProxySerializer` unwraps any deferred handler
before serializing or copying the handler.

A proxy must not be invoked, logged, or used as a key whose hash or equality calls the handler while
the deferred handler is still unresolved during deserialization or copy. If that happens, Fory throws
a targeted exception instead of silently invoking an incomplete proxy.

The JVM path may keep the existing optimized private-handler replacement path when benchmarks
require it. The Android path must remain separate and must not resolve JVM-only proxy handler offset
state.
