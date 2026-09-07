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

package threadsafe

import (
	"reflect"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/apache/fory/go/fory"
	"github.com/stretchr/testify/require"
)

func TestRegistrationAfterGC(t *testing.T) {
	type Item struct{ Value int32 }
	for _, byName := range []bool{false, true} {
		name := "ID"
		if byName {
			name = "Name"
		}
		t.Run(name, func(t *testing.T) {
			f := New()
			if byName {
				require.NoError(t, f.RegisterStructByName(Item{}, "threadsafe.Item"))
			} else {
				require.NoError(t, f.RegisterStruct(Item{}, 1))
			}
			value := Item{Value: 42}
			data, err := f.Serialize(&value)
			require.NoError(t, err)

			// Registrations must survive collections between root operations.
			runtime.GC()
			runtime.GC()
			var result Item
			require.NoError(t, f.Deserialize(data, &result))
			require.Equal(t, value, result)

			runtime.GC()
			runtime.GC()
			data, err = Serialize(f, &value)
			require.NoError(t, err)
			require.NoError(t, Deserialize(f, data, &result))
			require.Equal(t, value, result)
		})
	}
}

func TestRegistrationInstances(t *testing.T) {
	type Item struct{ Value int32 }
	type Order struct{ ID int64 }
	type NamedItem struct{ Name string }
	type FactoryItem struct{ Name string }
	type State int32
	type Color int32
	var factoryCalls atomic.Int32
	f := NewWithFactory(func() *fory.Fory {
		factoryCalls.Add(1)
		inner := fory.New()
		if err := inner.RegisterStruct(FactoryItem{}, 100); err != nil {
			panic(err)
		}
		return inner
	})
	created := factoryCalls.Load()
	require.Equal(t, int32(len(f.instances)), created)
	require.Error(t, f.RegisterStructByName(int32(0), "threadsafe.Invalid"))
	require.NoError(t, f.RegisterStruct(&Item{}, 1))
	require.NoError(t, f.RegisterStruct(Item{}, 1))
	require.Error(t, f.RegisterStruct(Order{}, 1))
	require.Error(t, f.RegisterStructByName(Item{}, "threadsafe.Duplicate"))
	require.NoError(t, f.RegisterStruct(Order{}, 2))
	require.NoError(t, f.RegisterStructByName(NamedItem{}, "threadsafe.NamedItem"))
	require.NoError(t, f.RegisterEnum(State(0), 3))
	require.NoError(t, f.RegisterEnumByName(Color(0), "threadsafe.Color"))
	// Registration configures existing instances without constructing more.
	require.Equal(t, created, factoryCalls.Load())

	// Borrow every instance to verify registration fan-out and independent state.
	borrowed := make([]*poolEntry, len(f.instances))
	seen := make(map[*fory.Fory]bool)
	for i := range borrowed {
		entry := f.acquire()
		borrowed[i] = entry
		inner := entry.fory
		require.False(t, seen[inner])
		seen[inner] = true
		for _, value := range []any{&Item{42}, &Order{7}, &NamedItem{"name"}, &FactoryItem{"factory"}, State(1), Color(2)} {
			data, err := inner.Serialize(value)
			require.NoError(t, err)
			var result any
			require.NoError(t, inner.Deserialize(data, &result))
			require.Equal(t, value, result)
		}
	}
	for _, inner := range borrowed {
		f.release(inner)
	}

	var workers sync.WaitGroup
	for range len(f.instances) + 1 {
		workers.Add(1)
		go func() {
			defer workers.Done()
			value := Item{42}
			for range 20 {
				data, err := Serialize(f, &value)
				if err != nil {
					t.Error(err)
					return
				}
				var result Item
				if err := Deserialize(f, data, &result); err != nil {
					t.Error(err)
					return
				}
				if result != value {
					t.Errorf("got %v, want %v", result, value)
					return
				}
			}
		}()
	}
	workers.Wait()
	require.Equal(t, created, factoryCalls.Load())
}

func TestPoolExhaustion(t *testing.T) {
	f := New()
	borrowed := make([]*poolEntry, len(f.instances))
	for i := range borrowed {
		borrowed[i] = f.acquire()
	}
	acquired := make(chan *poolEntry, len(borrowed))
	for range borrowed {
		go func() { acquired <- f.acquire() }()
	}
	require.Eventually(t, func() bool {
		return f.waiters.Load() == int32(len(borrowed))
	}, 5*time.Second, time.Millisecond)
	for _, entry := range borrowed {
		f.release(entry)
	}
	seen := make(map[*poolEntry]bool)
	for range borrowed {
		select {
		case entry := <-acquired:
			require.False(t, seen[entry])
			seen[entry] = true
		case <-time.After(5 * time.Second):
			t.Fatal("pool waiter was not woken after instances were returned")
		}
	}
	for entry := range seen {
		f.release(entry)
	}
}

type panicValue struct{}
type panicSerializer struct{}

func (*panicSerializer) WriteData(*fory.WriteContext, reflect.Value) {
	panic("custom serializer failed")
}

func (*panicSerializer) ReadData(*fory.ReadContext, reflect.Value) {
	panic("custom serializer failed")
}

func TestPoolRecovery(t *testing.T) {
	f := NewWithFactory(func() *fory.Fory {
		inner := fory.New()
		require.NoError(t, inner.RegisterExtension(panicValue{}, 100, &panicSerializer{}))
		return inner
	})
	for _, serialize := range []func(){
		func() { _, _ = f.Serialize(&panicValue{}) },
		func() { _, _ = Serialize(f, &panicValue{}) },
	} {
		require.Panics(t, serialize)
		for i := range f.slots {
			require.NotNil(t, f.slots[i].entry.Load(), "panic must not lose a pooled instance")
		}
		data, err := f.Serialize(int32(42))
		require.NoError(t, err)
		var result int32
		require.NoError(t, f.Deserialize(data, &result))
		require.Equal(t, int32(42), result)
	}
}

func TestRegistrationFreeze(t *testing.T) {
	type Item struct{ Value int32 }
	value := int32(42)
	data, err := fory.New().Serialize(value)
	require.NoError(t, err)
	tests := []struct {
		name string
		fail bool
		call func(*Fory) error
	}{
		{"Serialize", false, func(f *Fory) error { _, err := f.Serialize(value); return err }},
		{"SerializeError", true, func(f *Fory) error { _, err := f.Serialize(Item{}); return err }},
		{"GenericSerialize", false, func(f *Fory) error { _, err := Serialize(f, &value); return err }},
		{"GenericSerializeError", true, func(f *Fory) error { _, err := Serialize(f, &Item{}); return err }},
		{"Deserialize", false, func(f *Fory) error { return f.Deserialize(data, new(int32)) }},
		{"DeserializeError", true, func(f *Fory) error { return f.Deserialize(nil, new(int32)) }},
		{"GenericDeserialize", false, func(f *Fory) error { return Deserialize(f, data, new(int32)) }},
		{"GenericDeserializeError", true, func(f *Fory) error { return Deserialize(f, nil, new(int32)) }},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			f := New()
			err := test.call(f)
			if test.fail {
				require.Error(t, err)
			} else {
				require.NoError(t, err)
			}
			require.Error(t, f.RegisterStruct(Item{}, 1))
			require.Error(t, f.RegisterStructByName(Item{}, "threadsafe.Item"))
			type State int32
			require.Error(t, f.RegisterEnum(State(0), 2))
			require.Error(t, f.RegisterEnumByName(State(0), "threadsafe.State"))
		})
	}
}

// TestFory tests the thread-safe Fory wrapper
func TestFory(t *testing.T) {
	f := New(fory.WithXlang(false), fory.WithRefTracking(true), fory.WithCompatible(false))

	t.Run("BasicSerialization", func(t *testing.T) {
		data, err := f.Serialize(int32(42))
		require.NoError(t, err)

		var result int32
		err = f.Deserialize(data, &result)
		require.NoError(t, err)
		require.Equal(t, int32(42), result)
	})

	t.Run("GenericSerialization", func(t *testing.T) {
		val := "hello world"
		data, err := Serialize(f, &val)
		require.NoError(t, err)

		var result string
		err = Deserialize(f, data, &result)
		require.NoError(t, err)
		require.Equal(t, "hello world", result)
	})

	t.Run("ConcurrentAccess", func(t *testing.T) {
		done := make(chan bool, 10)
		for i := 0; i < 10; i++ {
			go func(val int32) {
				data, err := f.Serialize(val)
				require.NoError(t, err)

				var result int32
				err = f.Deserialize(data, &result)
				require.NoError(t, err)
				require.Equal(t, val, result)
				done <- true
			}(int32(i))
		}
		for i := 0; i < 10; i++ {
			<-done
		}
	})

	t.Run("ConcurrentGenericAccess", func(t *testing.T) {
		done := make(chan bool, 10)
		for i := 0; i < 10; i++ {
			go func(val int64) {
				data, err := Serialize(f, &val)
				require.NoError(t, err)

				var result int64
				err = Deserialize(f, data, &result)
				require.NoError(t, err)
				require.Equal(t, val, result)
				done <- true
			}(int64(i * 1000))
		}
		for i := 0; i < 10; i++ {
			<-done
		}
	})
}

// TestSerializeAny tests the Serialize/Deserialize methods
func TestSerializeAny(t *testing.T) {
	f := New(fory.WithXlang(false), fory.WithRefTracking(true), fory.WithCompatible(false))

	t.Run("Primitives", func(t *testing.T) {
		data, err := f.Serialize(int32(42))
		require.NoError(t, err)

		var result int32
		err = f.Deserialize(data, &result)
		require.NoError(t, err)
		require.Equal(t, int32(42), result)
	})

	t.Run("String", func(t *testing.T) {
		data, err := f.Serialize("hello")
		require.NoError(t, err)

		var result string
		err = f.Deserialize(data, &result)
		require.NoError(t, err)
		require.Equal(t, "hello", result)
	})
}

// TestDeserialize tests the Deserialize generic function
func TestDeserialize(t *testing.T) {
	f := New(fory.WithXlang(false), fory.WithRefTracking(true), fory.WithCompatible(false))

	t.Run("Int32", func(t *testing.T) {
		val := int32(42)
		data, err := Serialize(f, &val)
		require.NoError(t, err)

		var result int32
		err = Deserialize(f, data, &result)
		require.NoError(t, err)
		require.Equal(t, int32(42), result)
	})

	t.Run("String", func(t *testing.T) {
		val := "hello"
		data, err := Serialize(f, &val)
		require.NoError(t, err)

		var result string
		err = Deserialize(f, data, &result)
		require.NoError(t, err)
		require.Equal(t, "hello", result)
	})

	t.Run("Slice", func(t *testing.T) {
		f := New(fory.WithXlang(false), fory.WithRefTracking(true), fory.WithCompatible(false))
		// Serialize a struct containing the slice since *[]T is not supported
		type SliceWrapper struct {
			Items []int32
		}
		require.NoError(t, f.RegisterStructByName(SliceWrapper{}, "threadsafe.SliceWrapper"))
		original := SliceWrapper{Items: []int32{1, 2, 3, 4, 5}}
		data, err := Serialize(f, &original)
		require.NoError(t, err)

		var result SliceWrapper
		err = Deserialize(f, data, &result)
		require.NoError(t, err)
		require.Equal(t, original.Items, result.Items)
	})
}

// TestGlobalFunctions tests the global convenience functions
func TestGlobalFunctions(t *testing.T) {
	t.Run("Marshal", func(t *testing.T) {
		val := int32(42)
		data, err := Marshal(&val)
		require.NoError(t, err)

		var result int32
		err = Unmarshal(data, &result)
		require.NoError(t, err)
		require.Equal(t, int32(42), result)
	})

	t.Run("UnmarshalTo", func(t *testing.T) {
		// Use non-generic Serialize for compatibility with non-generic UnmarshalTo
		data, err := globalFory.Serialize("hello")
		require.NoError(t, err)

		var result string
		err = UnmarshalTo(data, &result)
		require.NoError(t, err)
		require.Equal(t, "hello", result)
	})
}
