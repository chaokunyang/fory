---
title: Thread Safety
sidebar_position: 13
id: thread-safety
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

This guide covers concurrent usage patterns for Fory Go, including the thread-safe wrapper and best practices for multi-goroutine environments.

## Default Fory Instance

The default `Fory` instance is **not thread-safe**:

```go
f := fory.New(fory.WithXlang(true))

// NOT SAFE: Concurrent access from multiple goroutines
go func() {
    f.Serialize(value1)  // Race condition!
}()
go func() {
    f.Serialize(value2)  // Race condition!
}()
```

### Why Not Thread-Safe?

For performance, Fory reuses internal state:

- Buffer is cleared and reused between calls
- Reference resolvers are reset
- Context objects are recycled

This avoids allocations but requires exclusive access.

## Thread-Safe Wrapper

For concurrent use, use the `threadsafe` package:

```go
import "github.com/apache/fory/go/fory/threadsafe"

// Create thread-safe Fory
f := threadsafe.New()

// Safe for concurrent use
go func() {
    data, _ := f.Serialize(value1)
}()
go func() {
    data, _ := f.Serialize(value2)
}()
```

### API

```go
// Create thread-safe instance
f := threadsafe.New()

// Instance methods
data, err := f.Serialize(value)
err = f.Deserialize(data, &target)

// Generic functions
data, err := threadsafe.Serialize(f, &value)
err = threadsafe.Deserialize(f, data, &target)

// Global convenience functions
data, err := threadsafe.Marshal(&value)
err = threadsafe.Unmarshal(data, &target)
```

## Type Registration

Register every type before the first serialization or deserialization attempt. Starting a root
operation permanently freezes registration on that Fory instance, including when the operation
fails:

```go
f := threadsafe.New()

// Register types BEFORE concurrent access
if err := f.RegisterStructByName(User{}, "example.User"); err != nil {
    panic(err)
}
if err := f.RegisterStructByName(Order{}, "example.Order"); err != nil {
    panic(err)
}

// Now safe to use concurrently
go func() {
    f.Serialize(&User{ID: 1})
}()
```

### Thread-Safe Registration

The thread-safe wrapper exposes named struct registration and serializes it against the first root
operation:

```go
f := threadsafe.New()
if err := f.RegisterStructByName(User{}, "example.User"); err != nil {
    panic(err)
}
```

If registration races with the first root, one operation wins the boundary. When the root wins,
the registration call returns `fory.ErrRegistryFrozen` without changing the registry. Register all
types during startup so the application does not depend on race ordering.

## Zero-Copy Considerations

### Non-Thread-Safe Instance

With the default Fory, returned byte slices are views into the internal buffer:

```go
f := fory.New(fory.WithXlang(true))

data1, _ := f.Serialize(value1)
// data1 is valid

data2, _ := f.Serialize(value2)
// data1 is NOW INVALID (buffer was reused)
```

### Thread-Safe Instance

The thread-safe wrapper copies data automatically:

```go
f := threadsafe.New()

data1, _ := f.Serialize(value1)
data2, _ := f.Serialize(value2)
// Both data1 and data2 are valid (independent copies)
```

This is safer but has allocation overhead.

## Performance Comparison

| Scenario            | Non-Thread-Safe | Thread-Safe            |
| ------------------- | --------------- | ---------------------- |
| Single goroutine    | Fastest         | Slower (pool overhead) |
| Multiple goroutines | Unsafe          | Safe, good scaling     |
| Memory allocations  | Minimal         | Per-call copy          |
| Buffer reuse        | Yes             | Per-pool-instance      |

### Benchmarking

```go
func BenchmarkNonThreadSafe(b *testing.B) {
    f := fory.New(fory.WithXlang(true))
    if err := f.RegisterStruct(User{}, 1); err != nil {
        b.Fatal(err)
    }
    user := &User{ID: 1, Name: "Alice"}

    for i := 0; i < b.N; i++ {
        data, _ := f.Serialize(user)
        _ = data
    }
}

func BenchmarkThreadSafe(b *testing.B) {
    f := threadsafe.New()
    if err := f.RegisterStructByName(User{}, "example.User"); err != nil {
        b.Fatal(err)
    }
    user := &User{ID: 1, Name: "Alice"}

    for i := 0; i < b.N; i++ {
        data, _ := f.Serialize(user)
        _ = data
    }
}
```

## Patterns

### Per-Goroutine Instance

For maximum performance with known goroutine count:

```go
func worker(id int) {
    // Each worker has its own Fory instance
    f := fory.New(fory.WithXlang(true))
    if err := f.RegisterStruct(User{}, 1); err != nil {
        panic(err)
    }

    for task := range tasks {
        data, _ := f.Serialize(task)
        process(data)
    }
}

// Start workers
for i := 0; i < numWorkers; i++ {
    go worker(i)
}
```

### Shared Thread-Safe Instance

For dynamic goroutine count or simplicity:

```go
// Single shared instance
var f = threadsafe.New()

func init() {
    if err := f.RegisterStructByName(User{}, "example.User"); err != nil {
        panic(err)
    }
}

func handleRequest(user *User) []byte {
    // Safe from any goroutine
    data, _ := f.Serialize(user)
    return data
}
```

### HTTP Handler Example

```go
var fory = threadsafe.New()

func init() {
    if err := fory.RegisterStructByName(Response{}, "example.Response"); err != nil {
        panic(err)
    }
}

func handler(w http.ResponseWriter, r *http.Request) {
    response := &Response{
        Status: "ok",
        Data:   getData(),
    }

    // Safe: threadsafe.Fory handles concurrency
    data, err := fory.Serialize(response)
    if err != nil {
        http.Error(w, err.Error(), 500)
        return
    }

    w.Header().Set("Content-Type", "application/octet-stream")
    w.Write(data)
}
```

## Common Mistakes

### Sharing Non-Thread-Safe Instance

```go
// WRONG: Race condition
var f = fory.New(fory.WithXlang(true))

func handler1() {
    f.Serialize(value1)  // Race!
}

func handler2() {
    f.Serialize(value2)  // Race!
}
```

**Fix**: Use `threadsafe.New()` or per-goroutine instances.

### Keeping Reference to Buffer

```go
// WRONG: Buffer invalidated on next call
f := fory.New(fory.WithXlang(true))
data, _ := f.Serialize(value1)
savedData := data  // Just copies the slice header!

f.Serialize(value2)  // Invalidates data and savedData
```

**Fix**: Clone the data or use thread-safe wrapper.

```go
// Correct: Clone the data
data, _ := f.Serialize(value1)
savedData := make([]byte, len(data))
copy(savedData, data)

// Or use thread-safe (auto-copies)
f := threadsafe.New()
data, _ := f.Serialize(value1)  // Already copied
```

### Registering Types Concurrently

```go
// The root may freeze the registry first.
go func() {
    if err := f.RegisterStructByName(TypeA{}, "example.TypeA"); err != nil {
        panic(err)
    }
}()
go func() {
    _, _ = f.Serialize(value)
}()
```

If serialization wins, registration returns `fory.ErrRegistryFrozen`. Register all types before
starting concurrent roots.

## Best Practices

1. **Register types at startup**: Before any concurrent operations
2. **Clone data if keeping references**: With non-thread-safe instance
3. **Use per-worker instances for hot paths**: Eliminates pool contention
4. **Profile before optimizing**: Thread-safe overhead may be negligible

## Related Topics

- [Configuration](configuration.md)
- [Basic Serialization](basic-serialization.md)
- [Troubleshooting](troubleshooting.md)
