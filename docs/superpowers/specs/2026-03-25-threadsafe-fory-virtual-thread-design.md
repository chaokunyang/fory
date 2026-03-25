# ThreadSafeFory Virtual Thread Support Design

Date: 2026-03-25

## Summary

This design reduces the cost of using `ThreadSafeFory` from large numbers of JDK 21 virtual
threads without changing the public API. The core change is to extract a per-wrapper
`SharedRegistry` so multiple internal `Fory` instances can reuse the most expensive safe caches,
and to route JDK 21 virtual-thread calls through a lightweight pooled backend named
`FastForyPool`.

Platform threads keep the current `ThreadLocalFory` fast path. Virtual threads use `FastForyPool`
so we do not create one heavy `Fory` instance per virtual thread.

## Problem

Today `ForyBuilder.buildThreadSafeFory()` returns a `ThreadLocalFory`. That is efficient for a
small and stable set of platform threads, but it scales poorly for virtual threads because:

- every virtual thread may allocate its own heavy `Fory`
- `Fory` initialization is expensive
- much of the initialization work is duplicated across equivalent `Fory` instances

The existing pooled implementations avoid some of that cost, but they do not currently reuse the
right internal caches and they do not provide the exact transparent runtime behavior wanted for the
default `ThreadSafeFory` entrypoint.

## Goals

- Keep the public `ThreadSafeFory` API unchanged.
- Make `buildThreadSafeFory()` automatically work well with virtual threads.
- Preserve the current `ThreadLocalFory` behavior for platform threads.
- Keep source compatibility with JDK 8.
- Avoid direct source references to JDK 21 virtual-thread APIs.
- Reduce duplicated `Fory` initialization work by sharing safe caches between internal `Fory`
  instances owned by the same thread-safe wrapper.
- Benchmark creation throughput and average retained memory per `Fory` before and after the
  refactor.

## Non-Goals

- Do not redesign `Fory` into a fully immutable shared core plus tiny per-call shells in this
  change.
- Do not share mutable generic caches such as `objectGenericType`, `genericTypes`, or
  `classGenericTypes`.
- Do not make pooled backends enforce strict classloader isolation in this change.
- Do not change `buildThreadLocalFory()` semantics beyond any constructor wiring needed for shared
  registries.

## Current Constraints

- `Fory` is mutable and not thread-safe.
- `ThreadLocalFory` assumes one reusable `Fory` per Java thread.
- `Fory` construction initializes multiple caches and helper objects, including resolvers and
  codegen-related state.
- Some resolver caches are safe to share across equivalent `Fory` instances, but others currently
  contain mutable per-resolver or per-call state.

## Chosen Approach

Use an adaptive runtime behind the existing `ThreadSafeFory` API:

- On JDK 8 to 20: keep the current `ThreadLocalFory` behavior.
- On JDK 21+ platform threads: keep the current `ThreadLocalFory` behavior.
- On JDK 21+ virtual threads: route operations through `FastForyPool`.

`buildThreadSafeFory()` should return an adaptive `ThreadSafeFory` implementation that chooses
between a thread-local backend and a pooled backend per call. `buildThreadLocalFory()` remains the
explicit thread-local entrypoint.

To reduce the cost of creating pooled `Fory` instances, extract a `SharedRegistry` under
`org.apache.fory.resolver` and pass it into each internal `Fory` created by the same thread-safe
wrapper.

## SharedRegistry

### Ownership

Each thread-safe wrapper instance owns exactly one `SharedRegistry`.

This applies to:

- the adaptive wrapper returned by `buildThreadSafeFory()`
- `ThreadLocalFory`
- `ThreadPoolFory`
- `FastForyPool`

Plain `Fory.builder().build()` still creates an isolated private `SharedRegistry` so standalone
`Fory` instances keep their current isolation model.

### Shared Fields

The first version of `SharedRegistry` contains only caches that are safe to share concurrently
across equivalent `Fory` instances:

- `TypeResolver.typeDefMap`
- `TypeResolver.ExtRegistry.currentLayerTypeDef`
- `TypeResolver.ExtRegistry.descriptorsCache`
- `TypeResolver.ExtRegistry.codeGeneratorMap`
- a new `MetaString -> byte[]` cache used by `MetaStringResolver`

All map-like fields in `SharedRegistry` must use concurrent implementations:

- `ConcurrentHashMap` for regular key semantics
- `ConcurrentIdentityMap` when identity semantics are required

Code must not use `get` followed by `put` on shared caches. Use atomic operations such as:

- `computeIfAbsent`
- `putIfAbsent`

### Explicitly Not Shared

The following fields stay per-`Fory`:

- `ExtRegistry.objectGenericType`
- `ExtRegistry.genericTypes`
- `ExtRegistry.classGenericTypes`

Those caches remain local because `GenericType` currently memoizes mutable resolver-bound state
such as serializer lookups and ref-tracking decisions.

## Fory Construction Changes

Add a new constructor:

```java
Fory(ForyBuilder builder, ClassLoader classLoader, SharedRegistry sharedRegistry)
```

The existing constructor delegates to it by creating a new private `SharedRegistry`.

`TypeResolver`, `ClassResolver`, `XtypeResolver`, and `MetaStringResolver` receive the
`SharedRegistry` through the existing `Fory` object graph rather than by creating their own
independent cache containers.

## Resolver Changes

### TypeResolver

The selected shared caches currently owned by `TypeResolver` and `TypeResolver.ExtRegistry` should
move into the top-level `SharedRegistry` type. The resolver keeps resolver-local state that cannot
be safely shared, and reads shared caches from the provided registry.

All call sites that currently use unsafe `get`-then-`put` patterns on shared caches must be
rewritten to atomic concurrent-map operations.

### MetaStringResolver

`MetaStringResolver.metaString2BytesMap` is not safe to share directly because `MetaStringBytes`
contains mutable `dynamicWriteStringId` state.

Instead:

- `SharedRegistry` stores `ConcurrentHashMap<MetaString, byte[]>`
- each `MetaStringResolver` keeps its own `MetaStringBytes` wrapper objects
- each resolver keeps its own dynamic read and write ID arrays and counters

This preserves resolver-local dynamic string state while still sharing the immutable encoded bytes.

## FastForyPool

Rename `SimpleForyPool` to `FastForyPool`.

`FastForyPool` is the virtual-thread backend used by `buildThreadSafeFory()` on JDK 21+ virtual
threads.

### Responsibilities

- borrow a `Fory`
- run one operation
- reset and return the `Fory`

### Simplifications

- remove `private final ThreadLocal<MemoryBuffer> bufferLocal`
- use the borrowed `Fory` directly for `serialize(Object)` and related calls
- keep pooling simple and focused on minimizing `Fory` creation overhead

### ClassLoader Behavior

For `FastForyPool`:

- `setClassLoader(...)` and `setClassLoader(..., stagingType)` call
  `Thread.currentThread().setContextClassLoader(...)`
- `clearClassLoader(...)` is a no-op
- `getClassLoader()` returns the current thread context classloader, with the existing fallback to
  `Fory.class.getClassLoader()`

This backend treats the thread context classloader as the classloader control surface instead of
maintaining its own loader-binding cache.

## Runtime Selection

### Platform Helper

Add a helper in `org.apache.fory.memory.Platform` that checks whether the current thread is a
virtual thread.

Requirements:

- use `MethodHandle`
- do not directly reference JDK 21 APIs at source level
- return `false` on JDK 8 to 20

Expected shape:

- cache a `MethodHandle` for `Thread.isVirtual()` when available
- expose a helper such as `Platform.isCurrentThreadVirtual()`

### ThreadSafeFory Dispatch

`buildThreadSafeFory()` remains the public default entrypoint.

Its behavior becomes:

- it returns an adaptive wrapper that holds both a `ThreadLocalFory` backend and a `FastForyPool`
  backend, wired to the same `SharedRegistry`
- if the current runtime does not support virtual threads, the adaptive wrapper always uses
  `ThreadLocalFory`
- if the runtime supports virtual threads and the calling thread is a platform thread, the
  adaptive wrapper uses `ThreadLocalFory`
- if the runtime supports virtual threads and the calling thread is a virtual thread, the adaptive
  wrapper uses `FastForyPool`

This check is performed per call on JDK 21+ so platform threads do not pay the pooled-backend
semantic change.

`buildThreadLocalFory()` remains available for callers who explicitly want the thread-local
implementation regardless of runtime.

## Compatibility Notes

- JDK 8 source compatibility is preserved because virtual-thread detection is reflective.
- Platform-thread behavior remains unchanged.
- The public `ThreadSafeFory` API remains unchanged.
- `FastForyPool` intentionally keeps `clearClassLoader(...)` as a no-op and relies on TCCL for
  classloader control.

## Testing Plan

### Functional Tests

- `SharedRegistry` cache reuse across multiple `Fory` instances created from the same wrapper
- `MetaStringResolver` correctness when sharing encoded byte arrays but keeping dynamic IDs local
- `FastForyPool` classloader behavior:
  - `setClassLoader(...)` updates TCCL
  - `clearClassLoader(...)` is a no-op
- `buildThreadSafeFory()` dispatch behavior:
  - JDK 8 to 20 path stays on `ThreadLocalFory`
  - JDK 21 platform thread path stays on `ThreadLocalFory`
  - JDK 21 virtual thread path uses `FastForyPool`

### Verification

- focused Java tests for the touched runtime and resolver code
- style checks for `java/fory-core`
- compile verification that no direct JDK 21 API reference leaks into source

## Benchmark Plan

### Fory Creation TPS

Measure repeated `Fory` creation throughput before and after the shared-registry refactor.

Report:

- command used
- baseline TPS
- new TPS
- ratio improvement

### Memory Per Fory

Measure average retained memory per `Fory` instance before and after the refactor by allocating
many equivalent `Fory` instances, stabilizing the heap, and dividing retained heap delta by object
count.

Report:

- command or harness used
- baseline bytes per `Fory`
- new bytes per `Fory`
- delta and ratio

## Risks

- Sharing caches that still have hidden mutable state could introduce cross-instance corruption.
- `FastForyPool` changes classloader behavior from internal loader binding to TCCL-based control,
  so tests must verify that the compatibility surface still behaves as intended.
- Runtime dispatch on every call must stay cheap enough not to erase the virtual-thread win.

## Out of Scope Follow-Ups

- sharing generic caches after `GenericType` is made safe for cross-resolver reuse
- stronger classloader isolation in pooled backends
- deeper `Fory` decomposition into immutable shared core plus tiny mutable shells
