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
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/apache/fory/go/fory"
	"github.com/stretchr/testify/require"
)

type registryFreezePooled struct {
	Value int32
}

type registryFreezeRace struct {
	Value int32
}

type reentrantStringSerializer struct {
	f      *Fory
	called chan struct{}
}

func (s *reentrantStringSerializer) String() string {
	select {
	case s.called <- struct{}{}:
	default:
	}
	_, _ = s.f.Serialize(int32(1))
	return "reentrant serializer"
}

func (*reentrantStringSerializer) Write(
	*fory.WriteContext, fory.RefMode, bool, bool, reflect.Value,
) {
}

func (*reentrantStringSerializer) WriteData(*fory.WriteContext, reflect.Value) {}

func (*reentrantStringSerializer) Read(
	*fory.ReadContext, fory.RefMode, bool, bool, reflect.Value,
) {
}

func (*reentrantStringSerializer) ReadData(*fory.ReadContext, reflect.Value) {}

func (*reentrantStringSerializer) ReadWithTypeInfo(
	*fory.ReadContext, fory.RefMode, *fory.TypeInfo, reflect.Value,
) {
}

func TestDuplicateSerializerFormatting(t *testing.T) {
	called := make(chan struct{}, 1)
	serializer := &reentrantStringSerializer{called: called}
	var f *Fory
	f = NewWithFactory(func() *fory.Fory {
		inner := fory.New(fory.WithXlang(false), fory.WithCompatible(false))
		if err := inner.RegisterUnionByName(
			registryFreezePooled{}, "test.DuplicateSerializer", serializer,
		); err != nil {
			panic(err)
		}
		return inner
	})
	serializer.f = f

	result := make(chan error, 1)
	go func() {
		result <- f.RegisterStructByName(
			registryFreezePooled{}, "test.DuplicateSerializer")
	}()
	timer := time.NewTimer(2 * time.Second)
	defer timer.Stop()
	select {
	case err := <-result:
		require.Error(t, err)
	case <-timer.C:
		t.Fatal("duplicate registration deadlocked while formatting the serializer")
	}
	select {
	case <-called:
		t.Fatal("duplicate registration formatted the application serializer")
	default:
	}
	require.False(t, f.registryFrozen.Load())
	require.Empty(t, f.registrations)
	require.Nil(t, f.prepared)
}

func TestFactoryRootReentry(t *testing.T) {
	var f *Fory
	var factoryEntered atomic.Bool
	var factoryUnlocked bool
	var rootErr error
	f = NewWithFactory(func() *fory.Fory {
		if factoryEntered.CompareAndSwap(false, true) {
			factoryUnlocked = f.registrationMu.TryLock()
			if factoryUnlocked {
				f.registrationMu.Unlock()
				_, rootErr = f.Serialize(int32(1))
			}
		}
		return fory.New(fory.WithXlang(false), fory.WithCompatible(false))
	})

	err := f.RegisterStructByName(registryFreezePooled{}, "test.FactoryRootReentry")
	require.True(t, factoryUnlocked)
	require.NoError(t, rootErr)
	require.ErrorIs(t, err, fory.ErrRegistryFrozen)
	require.True(t, f.registryFrozen.Load())
	require.Empty(t, f.registrations)
	require.Nil(t, f.prepared)
}

func TestRegistryFreezePropagation(t *testing.T) {
	var factoryCalls atomic.Int32
	f := NewWithFactory(func() *fory.Fory {
		factoryCalls.Add(1)
		return fory.New(fory.WithXlang(false), fory.WithCompatible(false))
	})
	require.NoError(t, f.RegisterStructByName(registryFreezePooled{}, "test.RegistryFreezePooled"))

	const innerCount = 8
	inners := make([]*fory.Fory, 0, innerCount)
	for i := 0; i < innerCount; i++ {
		inner, err := f.acquire()
		require.NoError(t, err)
		inners = append(inners, inner)
		value := registryFreezePooled{Value: int32(i)}
		data, err := inner.Serialize(&value)
		require.NoError(t, err)
		var result registryFreezePooled
		require.NoError(t, inner.Deserialize(data, &result))
		require.Equal(t, value, result)
	}
	require.Equal(t, int32(innerCount), factoryCalls.Load())
	require.ErrorIs(t,
		f.RegisterStructByName(registryFreezePooled{}, "test.RegistryFreezeLate"),
		fory.ErrRegistryFrozen)
	for _, inner := range inners {
		f.release(inner)
	}
}

func TestRegistryFreezeOnFailure(t *testing.T) {
	f := New(fory.WithXlang(false), fory.WithCompatible(false))
	_, err := f.Serialize(fory.Decimal{Scale: 10_001})
	require.Error(t, err)
	require.ErrorIs(t,
		f.RegisterStructByName(registryFreezePooled{}, "test.RegistryFreezeFailure"),
		fory.ErrRegistryFrozen)
}

func TestRegistryFreezeReplayFailure(t *testing.T) {
	frozenInner := fory.New(fory.WithXlang(false), fory.WithCompatible(false))
	_, err := frozenInner.Serialize(int32(1))
	require.NoError(t, err)

	var factoryCalls atomic.Int32
	f := NewWithFactory(func() *fory.Fory {
		if factoryCalls.Add(1) == 1 {
			return fory.New(fory.WithXlang(false), fory.WithCompatible(false))
		}
		return frozenInner
	})
	require.NoError(t, f.RegisterStructByName(registryFreezePooled{}, "test.RegistryFreezeReplay"))

	prepared, err := f.acquire()
	require.NoError(t, err)
	defer f.release(prepared)
	_, err = f.Serialize(&registryFreezePooled{Value: 1})
	require.ErrorIs(t, err, fory.ErrRegistryFrozen)
}

func TestRegistryFreezeOnFactoryPanic(t *testing.T) {
	f := NewWithFactory(func() *fory.Fory { panic("factory failure") })
	require.Panics(t, func() {
		_, _ = f.Serialize(int32(1))
	})
	require.ErrorIs(t,
		f.RegisterStructByName(registryFreezePooled{}, "test.RegistryFreezeFactory"),
		fory.ErrRegistryFrozen)
}

func TestRegistryFreezeRace(t *testing.T) {
	const iterations = 100
	for i := 0; i < iterations; i++ {
		f := New(fory.WithXlang(false), fory.WithCompatible(false))
		start := make(chan struct{})
		registrationResult := make(chan error, 1)
		rootResult := make(chan error, 1)
		var ready sync.WaitGroup
		ready.Add(2)

		go func() {
			ready.Done()
			<-start
			registrationResult <- f.RegisterStructByName(registryFreezeRace{}, "test.RegistryFreezeRace")
		}()
		go func() {
			ready.Done()
			<-start
			_, err := f.Serialize(&registryFreezeRace{Value: int32(i)})
			rootResult <- err
		}()
		ready.Wait()
		close(start)

		registrationErr := <-registrationResult
		rootErr := <-rootResult
		if registrationErr == nil {
			require.NoError(t, rootErr)
		} else {
			require.ErrorIs(t, registrationErr, fory.ErrRegistryFrozen)
			require.Error(t, rootErr)
		}
	}
}
