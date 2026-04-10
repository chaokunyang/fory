---
title: Xlang Implementation Guide
sidebar_position: 10
id: xlang_implementation_guide
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

This document describes the current Java xlang runtime architecture. The wire format is defined by
[Xlang Serialization Spec](xlang_serialization_spec.md); this guide explains the service
boundaries and control flow that the reference implementation uses today. New runtimes do not need
the same class names, but they should preserve the same ownership model: root operations stay on
the runtime facade, while payload work stays on explicit read and write contexts.

## Runtime ownership model

### `Fory` is the root-operation facade

`Fory` owns the immutable `Config`, the active `TypeResolver`, the `JITContext`, and one reusable
`WriteContext`, `ReadContext`, and `CopyContext` for that runtime instance. Top-level
`serialize(...)`, `deserialize(...)`, and `copy(...)` entry points live here.

Before the first root operation, `Fory` freezes registration by calling
`TypeResolver.finishRegistration()`. After that point, serializers and type IDs are treated as
stable for the lifetime of the runtime.

`Fory` is deliberately not the place where nested serializers do their work. During an active root
operation, nested calls back into `Fory.serializeXXX` or `Fory.deserializeXXX` are rejected. Inside
serializers, nested payload handling must go through `WriteContext` and `ReadContext`.

### `WriteContext` and `ReadContext` hold all operation-local state

`WriteContext` and `ReadContext` are prepared by `Fory` for one root operation and reset in a
`finally` block before reuse. They hold:

- the current `MemoryBuffer`
- the shared `Generics` stack
- the active `TypeResolver`
- the active `RefWriter` or `RefReader`
- meta-string and meta-share state
- operation-local scratch state keyed by object identity
- the logical object-graph depth
- out-of-band buffer state on the read side

Generated and hand-written serializers should treat these contexts as the only source of
operation-local services. Serializers must not keep ambient runtime state in thread locals or in
serializer instance fields.

### Reference tracking is a pluggable service

Reference handling is split behind two small interfaces:

- `RefWriter` writes null, reference, and new-value markers and remembers previously written
  objects by identity.
- `RefReader` decodes those markers, reserves read reference IDs, and resolves previously
  materialized objects.

When reference tracking is enabled, Java uses `MapRefWriter` and `MapRefReader`. When it is
disabled, Java swaps in `NoRefWriter` and `NoRefReader`, which keep the same call shape while
avoiding map and array maintenance.

### Type resolution is a separate service

`TypeResolver` owns serializer lookup, type registration, type metadata encoding, and the caches
used while reading type info from the stream.

In xlang mode, Java uses `XtypeResolver`. In native Java mode, it uses `ClassResolver`. The rest of
the runtime talks to the abstract `TypeResolver` contract.

## Root frame responsibilities

Every root payload starts with a one-byte bitmap written and read by `Fory` itself, not by
serializers:

| Bit | Meaning                         |
| --- | ------------------------------- |
| `0` | null root payload               |
| `1` | xlang payload                   |
| `2` | out-of-band buffers are enabled |

Per-object reference markers are separate from that root bitmap. Java uses these signed marker
bytes throughout the object graph:

| Value | Meaning               |
| ----- | --------------------- |
| `-3`  | `NULL_FLAG`           |
| `-2`  | `REF_FLAG`            |
| `-1`  | `NOT_NULL_VALUE_FLAG` |
| `0`   | `REF_VALUE_FLAG`      |

Keep those two layers separate in every runtime:

- the root bitmap describes the whole payload
- ref flags describe one nested value at a time

## Serialization flow

### Root write path

The current Java xlang write path is:

1. `Fory.serialize(...)` calls `ensureRegistrationFinished()`.
2. `Fory` binds the target buffer and optional `BufferCallback` with `writeContext.prepare(...)`.
3. `Fory` writes the root bitmap.
4. If the root value is non-null, `Fory` locks the `JITContext`, verifies that this is not a
   nested root call, and delegates the root object to `writeContext.writeRef(obj)`.
5. `writeContext.reset()` runs in `finally`, regardless of success or failure.

`WriteContext.writeRef(...)` is the main object-graph entry point:

1. `RefWriter.writeRefOrNull(...)` emits the null, ref, or new-value marker.
2. If the object is new, `WriteContext` resolves `TypeInfo` from the active `TypeResolver`.
3. For most types, `TypeResolver.writeTypeInfo(...)` writes the xlang type header.
4. `WriteContext.writeData(...)` writes the payload. Primitive and string-like hot paths write
   directly to `MemoryBuffer`; other types delegate to the resolved serializer.

The xlang `UnknownStruct` path is the main special case: it owns its own stream representation and
does not follow the normal "write type info, then payload" sequence.

### Payload serializers write through `WriteContext`

Serializers are responsible only for the payload of their type. They do not write the root bitmap,
own registration, or decide how class metadata is encoded.

Important current Java rules:

- Serializer instances are runtime-local by default. Only serializers that implement `Shareable`
  may be reused across equivalent runtimes.
- Use `WriteContext` helpers such as `writeRef(...)`, `writeNonRef(...)`, `writeStringRef(...)`,
  and `writeBufferObject(...)` when nested values need ref handling or type metadata.
- If several primitive writes happen in a row, fetch `MemoryBuffer` once from
  `WriteContext.getBuffer()` and write directly for better inlining and fewer helper calls.
- `WriteContext` maintains `depth` around nested serializer calls. That depth is also used to block
  illegal nested root operations.

## Deserialization flow

### Root read path

The current Java xlang read path mirrors the write path:

1. `Fory.deserialize(...)` calls `ensureRegistrationFinished()`.
2. `Fory` reads the root bitmap.
3. If the null bit is set, deserialization returns `null` immediately.
4. `Fory` verifies that the payload xlang bit matches the runtime mode.
5. `Fory` validates whether out-of-band buffers must or must not be supplied.
6. `Fory` binds the buffer and optional out-of-band buffer iterator with
   `readContext.prepare(...)`.
7. `Fory` locks the `JITContext`, verifies that this is not a nested root call, and delegates to
   `readContext.readRef()` or the typed `deserializeByType(...)` path.
8. `readContext.reset()` runs in `finally`.

### `ReadContext` owns reference reservation and payload materialization

`ReadContext.readRef()` performs the normal xlang read sequence:

1. `RefReader.tryPreserveRefId(...)` consumes the next ref marker.
2. If the marker is `REF_FLAG`, the previously materialized object is returned immediately.
3. If the marker is `NULL_FLAG`, `null` is returned.
4. If the marker indicates a new value, the reader reserves a dense read reference ID before the
   payload is materialized.
5. `TypeResolver.readTypeInfo(...)` decodes the type header.
6. `ReadContext.readNonRef(typeInfo)` reads the payload.
7. `RefReader.setReadRef(...)` binds the reserved ID to the completed object.

Primitive and string-like hot paths read directly from `MemoryBuffer`; complex payloads delegate to
the resolved serializer. This reservation-before-read pattern is what lets Java support cycles and
back-references to partially built objects inside containers and structs.

### Serializers must bind newly created objects early when needed

Many serializers allocate the target object before all child values have been read. In that case,
the serializer must register the partially built object with `readContext.reference(obj)` or
`readContext.setReadRef(...)` before reading nested children that may point back to it.

That rule is essential for arrays, collections, maps, object serializers, meta-share serializers,
replace/resolve serializers, and any other serializer that can participate in cycles.

### Read-side depth and security

`ReadContext` tracks logical object depth. `increaseDepth()` enforces `Config.maxDepth()` and
throws if the stream looks malicious or unexpectedly deep. New runtimes should keep the same
explicit depth accounting instead of relying on the native call stack alone.

## Type metadata and xlang type resolution

### `TypeResolver` writes and reads all xlang type headers

`TypeResolver.writeTypeInfo(...)` always writes the 8-bit type ID first, then emits any extra type
metadata required by that kind:

- registered user enum, struct, ext, and typed union types write the user type ID
- named types write namespace and type-name meta strings when meta share is disabled
- compatible struct modes write shared `TypeDef` metadata
- built-in types write only the internal type ID

`TypeResolver.readTypeInfo(...)` is the inverse operation. It decodes the type ID, consumes any
attached metadata, returns the matching `TypeInfo`, and ensures that a serializer exists before the
payload is read.

### `XtypeResolver` is the xlang-specific implementation

`XtypeResolver` extends `TypeResolver` with xlang-specific registration and lookup rules:

- it assigns xlang user type IDs
- it registers built-in xlang serializers
- it resolves named types from namespace and type-name bytes
- it handles `UnknownStruct` and other unknown-class cases
- it builds or loads meta-shared serializers when compatible struct metadata is used

The important design point is that serializers do not resolve class metadata themselves. They ask
the current context for nested reads and writes, and the context delegates type work to
`TypeResolver`.

For typed Java entry points, `Fory.deserialize(..., Class<T>)` also pushes the expected generic
type onto the shared `Generics` stack before reading and pops it afterward.

## Meta strings and meta-share state

Two pieces of explicit runtime state back xlang type metadata:

- `MetaStringWriter` and `MetaStringReader` deduplicate and decode namespace and type-name strings
- `MetaWriteContext` and `MetaReadContext` track shared `TypeDef` announcements for meta-share mode

When scoped meta share is enabled, each `WriteContext` and `ReadContext` owns its own meta-share
state for one root operation and clears it during `reset()`.

When scoped meta share is disabled, callers may install externally owned `MetaWriteContext` and
`MetaReadContext` instances through `setMetaWriteContext(...)` and `setMetaReadContext(...)` so the
same meta-share session can span multiple root operations.

This state is explicit on the contexts. It is not hidden in globals or thread-local caches.

## Enums in xlang mode

In Java xlang mode, enums are serialized by numeric tag, not by name.

- By default, the tag is the declaration ordinal.
- If the enum is configured with `@ForyEnumId`, Java writes that explicit stable tag instead.
- `serializeEnumByName(true)` only changes native Java mode; xlang still uses numeric tags.

`EnumSerializer` precomputes two structures from the chosen tags:

- `tagByOrdinal` for the write path
- either a dense `Enum[]` lookup table or a sparse `Map<Integer, Enum>` for the read path

Small explicit ID spaces use the array fast path. Large sparse ID spaces use the map fast path.

## Out-of-band buffer objects

`WriteContext.writeBufferObject(...)` and `ReadContext.readBufferObject()` implement the current
buffer-object contract:

- one boolean says whether the bytes are in-band or out-of-band
- in-band payloads encode the byte length and then the raw bytes
- out-of-band payloads rely on the caller-supplied `BufferCallback` and out-of-band buffer iterator

The root bitmap advertises whether out-of-band buffers are in play for the whole payload. Runtime
validation happens in `Fory.deserialize(...)` before nested serializers start reading.

## Serializer design rules for new runtimes

Any new xlang runtime should follow these rules even if its surface API looks different:

1. Keep root operations on the runtime facade and nested payload work on explicit read and write
   contexts.
2. Keep reference tracking behind dedicated read-side and write-side services so the disabled path
   stays cheap.
3. Make serializers payload-only. Type metadata, registration, and root framing belong to the
   runtime and type resolver layers.
4. Track per-operation state explicitly. Do not rely on ambient thread-local runtime state.
5. Reserve read reference IDs before materializing new objects, and bind partially built objects as
   soon as a nested child may refer back to them.
6. Keep meta-share session state explicit and resettable.
7. Preserve the separation between the root bitmap, per-object ref flags, type headers, and
   payload bytes.
8. After any xlang protocol change, run the cross-language test matrix and update both this guide
   and [Xlang Serialization Spec](xlang_serialization_spec.md).
