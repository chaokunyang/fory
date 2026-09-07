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

// Package threadsafe provides a thread-safe wrapper around Fory using sync.Pool.
package threadsafe

import (
	"fmt"
	"reflect"
	"sync"
	"sync/atomic"

	"github.com/apache/fory/go/fory"
)

// Fory is a thread-safe wrapper around fory.Fory using sync.Pool.
// It provides the same API as fory.Fory but is safe for concurrent use.
type Fory struct {
	pool           sync.Pool
	registrationMu sync.Mutex
	started        atomic.Bool
	registrations  []structRegistration
}

type structRegistration struct {
	typ  reflect.Type
	name string
}

// New creates a new thread-safe Fory instance.
func New(opts ...fory.Option) *Fory {
	return NewWithFactory(func() *fory.Fory {
		return fory.New(opts...)
	})
}

// NewWithFactory creates a new thread-safe Fory instance using a custom factory.
// The factory must return a fresh, identically configured Fory instance on every
// call, with any custom registrations completed before returning. It may be
// called concurrently.
func NewWithFactory(factory func() *fory.Fory) *Fory {
	if factory == nil {
		panic("threadsafe.NewWithFactory requires a non-nil factory")
	}
	f := &Fory{}
	f.pool = sync.Pool{
		New: func() any {
			inner := factory()
			if inner == nil {
				panic("threadsafe.NewWithFactory factory returned nil")
			}
			// Pool entries are disposable. Registrations belong to the wrapper and
			// must initialize every replacement or concurrently acquired instance.
			for _, registration := range f.registrations {
				if err := inner.RegisterStructByName(registration.typ, registration.name); err != nil {
					panic(fmt.Errorf("threadsafe factory cannot restore registration: %w", err))
				}
			}
			return inner
		},
	}
	return f
}

func (f *Fory) acquire() *fory.Fory {
	if !f.started.Load() {
		f.freezeRegistrations()
	}
	return f.pool.Get().(*fory.Fory)
}

//go:noinline
func (f *Fory) freezeRegistrations() {
	f.registrationMu.Lock()
	defer f.registrationMu.Unlock()
	// Freeze before acquiring an instance, including when the root operation
	// fails. The factory can then read immutable registrations without locking.
	f.started.Store(true)
}

func (f *Fory) release(inner *fory.Fory) {
	inner.Reset()
	f.pool.Put(inner)
}

// ============================================================================
// Non-generic methods
// ============================================================================

// Serialize serializes a value using a pooled Fory instance
func (f *Fory) Serialize(v any) ([]byte, error) {
	inner := f.acquire()
	data, err := inner.Serialize(v)
	if err != nil {
		f.release(inner)
		return nil, err
	}
	// Copy the data before releasing since the buffer will be reused
	result := make([]byte, len(data))
	copy(result, data)
	f.release(inner)
	return result, nil
}

// Deserialize deserializes data into the provided value using a pooled Fory instance
func (f *Fory) Deserialize(data []byte, v any) error {
	inner := f.acquire()
	defer f.release(inner)
	return inner.Deserialize(data, v)
}

// RegisterStructByName registers a struct type by name for cross-language serialization.
// Registration must complete before the first serialization or deserialization,
// including a failed operation. Every pooled instance receives the registration.
func (f *Fory) RegisterStructByName(type_ any, name string) error {
	f.registrationMu.Lock()
	defer f.registrationMu.Unlock()
	if f.started.Load() {
		return fmt.Errorf("types must be registered before the first serialization or deserialization")
	}
	// Validate against the factory and all accepted registrations without
	// publishing a partially configured instance or retaining a failed change.
	inner := f.pool.New().(*fory.Fory)
	if err := inner.RegisterStructByName(type_, name); err != nil {
		return err
	}
	typ, ok := type_.(reflect.Type)
	if !ok {
		typ = reflect.TypeOf(type_)
		if typ.Kind() == reflect.Ptr {
			typ = typ.Elem()
		}
	}
	f.registrations = append(f.registrations, structRegistration{typ: typ, name: name})
	return nil
}

// ============================================================================
// Generic package-level functions
// ============================================================================

// Serialize serializes a value with type T inferred, thread-safe.
// Takes pointer to avoid interface heap allocation and struct copy.
func Serialize[T any](f *Fory, value *T) ([]byte, error) {
	inner := f.acquire()
	data, err := fory.Serialize(inner, value)
	if err != nil {
		f.release(inner)
		return nil, err
	}
	// Copy the data before releasing since the buffer will be reused
	result := make([]byte, len(data))
	copy(result, data)
	f.release(inner)
	return result, nil
}

// Deserialize deserializes data directly into the provided target, thread-safe.
// Takes pointer to avoid interface heap allocation and enable direct writes.
func Deserialize[T any](f *Fory, data []byte, target *T) error {
	inner := f.acquire()
	defer f.release(inner)
	return fory.Deserialize(inner, data, target)
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
