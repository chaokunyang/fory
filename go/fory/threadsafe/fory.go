// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

// Package threadsafe provides a thread-safe wrapper around Fory.
package threadsafe

import (
	"fmt"
	"math/rand/v2"
	"runtime"
	"sync"
	"sync/atomic"

	"github.com/apache/fory/go/fory"
)

// Fory is a thread-safe wrapper around fory.Fory backed by a fixed-size pool.
// Struct and enum registration use the same API as fory.Fory and must finish
// before the first serialization or deserialization operation.
type Fory struct {
	slots          []poolSlot
	instances      []poolEntry
	available      chan struct{}
	waiters        atomic.Int32
	registrationMu sync.Mutex
	started        atomic.Bool
}

type poolSlot struct {
	entry atomic.Pointer[poolEntry]
	// Separate the atomics so borrowing unrelated slots does not invalidate
	// the same cache line across processors.
	_ [128]byte
}

type poolEntry struct {
	fory  *fory.Fory
	index int
}

// New creates a thread-safe Fory with 4 * runtime.GOMAXPROCS(0) pooled instances.
func New(opts ...fory.Option) *Fory {
	return NewWithFactory(func() *fory.Fory {
		return fory.New(opts...)
	})
}

// NewWithFactory creates a thread-safe Fory with 4 * runtime.GOMAXPROCS(0)
// pooled instances. It calls the factory sequentially during construction.
// The factory must return a fresh, identically configured Fory instance on every
// call, with any custom registrations completed before returning.
func NewWithFactory(factory func() *fory.Fory) *Fory {
	if factory == nil {
		panic("threadsafe.NewWithFactory requires a non-nil factory")
	}
	poolSize := 4 * runtime.GOMAXPROCS(0)
	f := &Fory{
		slots:     make([]poolSlot, poolSize),
		instances: make([]poolEntry, poolSize),
		available: make(chan struct{}, poolSize),
	}
	for i := range f.instances {
		inner := factory()
		if inner == nil {
			panic("threadsafe.NewWithFactory factory returned nil")
		}
		f.instances[i] = poolEntry{fory: inner, index: i}
		f.slots[i].entry.Store(&f.instances[i])
	}
	return f
}

func (f *Fory) acquire() *poolEntry {
	if !f.started.Load() {
		f.freezeRegistrations()
	}
	// Go has no goroutine-local slot hint. Spread borrowers across the slots
	// without a shared counter on the acquisition path.
	start := rand.IntN(len(f.slots))
	if entry := f.tryAcquire(start); entry != nil {
		return entry
	}
	return f.waitForEntry(start)
}

func (f *Fory) tryAcquire(start int) *poolEntry {
	index := start
	for range f.slots {
		if f.slots[index].entry.Load() != nil {
			if entry := f.slots[index].entry.Swap(nil); entry != nil {
				return entry
			}
		}
		index++
		if index == len(f.slots) {
			index = 0
		}
	}
	return nil
}

//go:noinline
func (f *Fory) waitForEntry(start int) *poolEntry {
	f.waiters.Add(1)
	defer f.waiters.Add(-1)
	for {
		// Rescan after announcing the waiter so a release cannot be missed
		// between the initial scan and blocking for a notification.
		if entry := f.tryAcquire(start); entry != nil {
			return entry
		}
		<-f.available
	}
}

//go:noinline
func (f *Fory) freezeRegistrations() {
	f.registrationMu.Lock()
	defer f.registrationMu.Unlock()
	// Freeze before borrowing, even if the first root operation fails.
	f.started.Store(true)
}

func (f *Fory) release(entry *poolEntry) {
	entry.fory.Reset()
	f.slots[entry.index].entry.Store(entry)
	if f.waiters.Load() > 0 {
		// At most len(slots) free entries need notifications. A borrower always
		// rescans the slots; stale notifications only cause another scan.
		select {
		case f.available <- struct{}{}:
		default:
		}
	}
}

// ============================================================================
// Non-generic methods
// ============================================================================

// Serialize serializes a value using a pooled Fory instance
func (f *Fory) Serialize(v any) ([]byte, error) {
	entry := f.acquire()
	defer f.release(entry)
	data, err := entry.fory.Serialize(v)
	if err != nil {
		return nil, err
	}
	// Copy the data before releasing since the buffer will be reused
	result := make([]byte, len(data))
	copy(result, data)
	return result, nil
}

// Deserialize deserializes data into the provided value using a pooled Fory instance
func (f *Fory) Deserialize(data []byte, v any) error {
	entry := f.acquire()
	defer f.release(entry)
	return entry.fory.Deserialize(data, v)
}

func (f *Fory) registerCallback(registration func(*fory.Fory) error) error {
	f.registrationMu.Lock()
	defer f.registrationMu.Unlock()
	if f.started.Load() {
		return fmt.Errorf("types must be registered before the first serialization or deserialization")
	}
	// Like Java ThreadPoolFory, configure every retained instance through the
	// ordinary registration API. GC must never replace a configured instance
	// with an unregistered one, and registration must not borrow just one entry.
	for i := range f.instances {
		if err := registration(f.instances[i].fory); err != nil {
			return err
		}
	}
	return nil
}

// RegisterStruct registers a struct type with a numeric ID in every pooled instance.
// Registration must complete before the first serialization or deserialization,
// including a failed operation. Arguments follow fory.Fory.RegisterStruct.
func (f *Fory) RegisterStruct(type_ any, typeID uint32) error {
	return f.registerCallback(func(inner *fory.Fory) error {
		return inner.RegisterStruct(type_, typeID)
	})
}

// RegisterStructByName registers a struct type by name in every pooled instance.
// Registration must complete before the first serialization or deserialization,
// including a failed operation. Arguments follow fory.Fory.RegisterStructByName.
func (f *Fory) RegisterStructByName(type_ any, name string) error {
	return f.registerCallback(func(inner *fory.Fory) error {
		return inner.RegisterStructByName(type_, name)
	})
}

// RegisterEnum registers an enum type with a numeric ID in every pooled instance.
// Registration must complete before the first serialization or deserialization,
// including a failed operation. Arguments follow fory.Fory.RegisterEnum.
func (f *Fory) RegisterEnum(type_ any, typeID uint32) error {
	return f.registerCallback(func(inner *fory.Fory) error {
		return inner.RegisterEnum(type_, typeID)
	})
}

// RegisterEnumByName registers an enum type by name in every pooled instance.
// Registration must complete before the first serialization or deserialization,
// including a failed operation. Arguments follow fory.Fory.RegisterEnumByName.
func (f *Fory) RegisterEnumByName(type_ any, name string) error {
	return f.registerCallback(func(inner *fory.Fory) error {
		return inner.RegisterEnumByName(type_, name)
	})
}

// ============================================================================
// Generic package-level functions
// ============================================================================

// Serialize serializes a value with type T inferred, thread-safe.
// Takes pointer to avoid interface heap allocation and struct copy.
func Serialize[T any](f *Fory, value *T) ([]byte, error) {
	entry := f.acquire()
	defer f.release(entry)
	data, err := fory.Serialize(entry.fory, value)
	if err != nil {
		return nil, err
	}
	// Copy the data before releasing since the buffer will be reused
	result := make([]byte, len(data))
	copy(result, data)
	return result, nil
}

// Deserialize deserializes data directly into the provided target, thread-safe.
// Takes pointer to avoid interface heap allocation and enable direct writes.
func Deserialize[T any](f *Fory, data []byte, target *T) error {
	entry := f.acquire()
	defer f.release(entry)
	return fory.Deserialize(entry.fory, data, target)
}

// ============================================================================
// Global convenience functions
// ============================================================================

// Global thread-safe Fory instance for convenience
var globalFory = New()

// Marshal serializes a value using the global thread-safe instance.
// Takes pointer to avoid interface heap allocation and struct copy.
func Marshal[T any](value *T) ([]byte, error) {
	return Serialize(globalFory, value)
}

// Unmarshal deserializes data into the provided target using the global thread-safe instance.
// Takes pointer to avoid interface heap allocation and enable direct writes.
func Unmarshal[T any](data []byte, target *T) error {
	return Deserialize(globalFory, data, target)
}

// UnmarshalTo deserializes data into the provided pointer using the global thread-safe instance.
// This is for non-generic use cases.
func UnmarshalTo(data []byte, v any) error {
	return globalFory.Deserialize(data, v)
}
