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

type structRegistration struct {
	typ  reflect.Type
	name string
}

// Fory is a thread-safe wrapper around fory.Fory using sync.Pool.
// It provides the same API as fory.Fory but is safe for concurrent use.
// Registration must finish before its first root operation.
type Fory struct {
	pool           sync.Pool
	registrationMu sync.Mutex
	registryFrozen atomic.Bool
	factory        func() *fory.Fory
	registrations  []structRegistration
	prepared       *fory.Fory
}

// New creates a new thread-safe Fory instance.
func New(opts ...fory.Option) *Fory {
	return NewWithFactory(func() *fory.Fory {
		return fory.New(opts...)
	})
}

// NewWithFactory creates a new thread-safe Fory instance using a custom factory.
func NewWithFactory(factory func() *fory.Fory) *Fory {
	if factory == nil {
		panic("threadsafe.NewWithFactory requires a non-nil factory")
	}
	return &Fory{factory: factory}
}

func (f *Fory) createInner() *fory.Fory {
	inner := f.factory()
	if inner == nil {
		panic("threadsafe.NewWithFactory factory returned nil")
	}
	return inner
}

func (f *Fory) applyRegistrations(inner *fory.Fory) error {
	for _, registration := range f.registrations {
		if err := inner.RegisterStructByName(registration.typ, registration.name); err != nil {
			return fmt.Errorf("apply registration %q to new Fory instance: %w", registration.name, err)
		}
	}
	return nil
}

func (f *Fory) newInner() (*fory.Fory, error) {
	inner := f.createInner()
	// Registry freeze is published before pool misses reach this path, so the
	// registration log is immutable and needs no root hot-path lock.
	if err := f.applyRegistrations(inner); err != nil {
		return nil, err
	}
	return inner, nil
}

func (f *Fory) acquire() (*fory.Fory, error) {
	if !f.registryFrozen.Load() {
		f.registrationMu.Lock()
		if !f.registryFrozen.Load() {
			f.registryFrozen.Store(true)
			inner := f.prepared
			f.prepared = nil
			f.registrationMu.Unlock()
			if inner != nil {
				return inner, nil
			}
			return f.newInner()
		}
		f.registrationMu.Unlock()
	}
	if pooled := f.pool.Get(); pooled != nil {
		return pooled.(*fory.Fory), nil
	}
	return f.newInner()
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
	inner, err := f.acquire()
	if err != nil {
		return nil, err
	}
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
	inner, err := f.acquire()
	if err != nil {
		return err
	}
	defer f.release(inner)
	return inner.Deserialize(data, v)
}

// RegisterStructByName registers a struct type by name before the first root operation.
func (f *Fory) RegisterStructByName(type_ any, name string) error {
	f.registrationMu.Lock()
	if f.registryFrozen.Load() {
		f.registrationMu.Unlock()
		return fory.ErrRegistryFrozen
	}
	if f.prepared != nil {
		defer f.registrationMu.Unlock()
		return f.registerPrepared(type_, name)
	}
	f.registrationMu.Unlock()

	// The factory is application code and may reenter a root. Never hold the
	// registration mutex across it, and recheck freeze before publishing its result.
	inner := f.createInner()

	f.registrationMu.Lock()
	defer f.registrationMu.Unlock()
	if f.registryFrozen.Load() {
		return fory.ErrRegistryFrozen
	}
	if f.prepared == nil {
		if err := f.applyRegistrations(inner); err != nil {
			return err
		}
		f.prepared = inner
	}
	return f.registerPrepared(type_, name)
}

func (f *Fory) registerPrepared(type_ any, name string) error {
	registration := structRegistration{name: name}
	if err := f.prepared.RegisterStructByName(type_, name); err != nil {
		// A failed registration is not part of the facade registry. Rebuild from
		// the successful log before the next registration or first root.
		f.prepared = nil
		return err
	}
	if registeredType, ok := type_.(reflect.Type); ok {
		registration.typ = registeredType
	} else {
		registration.typ = reflect.TypeOf(type_)
		if registration.typ.Kind() == reflect.Ptr {
			registration.typ = registration.typ.Elem()
		}
	}
	f.registrations = append(f.registrations, registration)
	return nil
}

// ============================================================================
// Generic package-level functions
// ============================================================================

// Serialize serializes a value with type T inferred, thread-safe.
// Takes pointer to avoid interface heap allocation and struct copy.
func Serialize[T any](f *Fory, value *T) ([]byte, error) {
	inner, err := f.acquire()
	if err != nil {
		return nil, err
	}
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
	inner, err := f.acquire()
	if err != nil {
		return err
	}
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
