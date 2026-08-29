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

package fory

import (
	"bytes"
	"reflect"
	"testing"

	"github.com/stretchr/testify/require"
)

type registryFreezeStruct struct {
	Value int32
}

type registryFreezeUnion struct{}

type registryFreezeEnum int32

type registryFreezeExtension struct {
	Value int32
}

type registryPanicSerializer struct{}

func (registryPanicSerializer) WriteData(ctx *WriteContext, _ reflect.Value) {
	ctx.Err().SetError(SerializationError("write failure"))
	panic("write failure")
}

func (registryPanicSerializer) ReadData(*ReadContext, reflect.Value) {}

func TestRegistryFreezeRegistrations(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	_, err := f.Serialize(int32(1))
	require.NoError(t, err)
	f.Reset()

	attempts := []struct {
		name string
		call func() error
	}{
		{"struct ID", func() error { return f.RegisterStruct(registryFreezeStruct{}, 7101) }},
		{"struct name", func() error {
			return f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezeStruct")
		}},
		{"union ID", func() error { return f.RegisterUnion(registryFreezeUnion{}, 7102, nil) }},
		{"union name", func() error {
			return f.RegisterUnionByName(registryFreezeUnion{}, "test.RegistryFreezeUnion", nil)
		}},
		{"enum ID", func() error { return f.RegisterEnum(registryFreezeEnum(0), 7103) }},
		{"enum name", func() error {
			return f.RegisterEnumByName(registryFreezeEnum(0), "test.RegistryFreezeEnum")
		}},
		{"extension ID", func() error {
			return f.RegisterExtension(registryFreezeExtension{}, 7104, nil)
		}},
		{"extension name", func() error {
			return f.RegisterExtensionByName(
				registryFreezeExtension{}, "test.RegistryFreezeExtension", nil)
		}},
	}
	for _, attempt := range attempts {
		t.Run(attempt.name, func(t *testing.T) {
			require.ErrorIs(t, attempt.call(), ErrRegistryFrozen)
		})
	}

	structType := reflect.TypeOf(registryFreezeStruct{})
	resolverAttempts := []func() error{
		func() error {
			return f.typeResolver.RegisterStruct(
				structType, f.typeResolver.structTypeID(structType, false), 7105)
		},
		func() error {
			return f.typeResolver.RegisterUnion(
				reflect.TypeOf(registryFreezeUnion{}), 7106, nil)
		},
		func() error {
			return f.typeResolver.RegisterEnum(
				reflect.TypeOf(registryFreezeEnum(0)), 7107)
		},
		func() error {
			return f.typeResolver.RegisterExtension(
				reflect.TypeOf(registryFreezeExtension{}), 7108, nil)
		},
	}
	for _, attempt := range resolverAttempts {
		require.ErrorIs(t, attempt(), ErrRegistryFrozen)
	}
}

func TestFrozenAllowsLazySerializer(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	_, err := f.Serialize(int32(1))
	require.NoError(t, err)

	want := map[int32]string{7: "value"}
	data, err := f.Serialize(want)
	require.NoError(t, err)
	var got map[int32]string
	require.NoError(t, f.Deserialize(data, &got))
	require.Equal(t, want, got)
}

func TestRegistryFreezeRoots(t *testing.T) {
	badDecimal := Decimal{Scale: maxDecimalScale + 1}
	tests := []struct {
		name string
		root func(*Fory) error
	}{
		{"Serialize", func(f *Fory) error { _, err := f.Serialize(badDecimal); return err }},
		{"Deserialize", func(f *Fory) error { return f.Deserialize(nil, new(int32)) }},
		{"SerializeTo", func(f *Fory) error { return f.SerializeTo(NewByteBuffer(nil), badDecimal) }},
		{"DeserializeFrom", func(f *Fory) error {
			return f.DeserializeFrom(NewByteBuffer(nil), new(int32))
		}},
		{"SerializeWithCallback", func(f *Fory) error {
			return f.SerializeWithCallback(NewByteBuffer(nil), badDecimal, nil)
		}},
		{"DeserializeWithCallbackBuffers", func(f *Fory) error {
			return f.DeserializeWithCallbackBuffers(NewByteBuffer(nil), nil, nil)
		}},
		{"generic Serialize", func(f *Fory) error { _, err := Serialize(f, badDecimal); return err }},
		{"generic Deserialize", func(f *Fory) error { return Deserialize(f, nil, new(int32)) }},
		{"DeserializeFromStream", func(f *Fory) error {
			return f.DeserializeFromStream(NewInputStream(bytes.NewReader(nil)), new(int32))
		}},
		{"DeserializeFromReader", func(f *Fory) error {
			return f.DeserializeFromReader(bytes.NewReader(nil), new(int32))
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			f := New(WithXlang(false), WithCompatible(false))
			require.Error(t, test.root(f))
			require.ErrorIs(t, f.RegisterStructByName(
				registryFreezeStruct{}, "test.RegistryFreezeRoot"), ErrRegistryFrozen)
		})
	}
}

func TestBorrowedBufferPanicCleanup(t *testing.T) {
	writer := New(WithXlang(false), WithCompatible(false))
	data, err := writer.Serialize(int32(7))
	require.NoError(t, err)
	data = bytes.Clone(data)

	f := New(WithXlang(false), WithCompatible(false))
	borrowed := NewByteBuffer(bytes.Clone(data))
	require.Panics(t, func() {
		_ = f.DeserializeFrom(borrowed, int32(0))
	})
	var value int32
	require.NoError(t, f.Deserialize(data, &value))
	require.Equal(t, int32(7), value)
}

func TestStreamBufferPanicCleanup(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	owned := f.readCtx.buffer
	stream := NewInputStream(bytes.NewReader(nil))

	require.Panics(t, func() {
		_ = f.DeserializeFromStream(stream, int32(0))
	})
	require.Same(t, owned, f.readCtx.buffer)
}

func TestSerializePanicCleanup(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	require.NoError(t, f.RegisterExtension(
		registryFreezeExtension{}, 7104, registryPanicSerializer{}))
	owned := f.writeCtx.buffer
	borrowed := NewByteBuffer(nil)

	require.Panics(t, func() {
		_ = f.SerializeTo(borrowed, &registryFreezeExtension{Value: 1})
	})
	require.Same(t, owned, f.writeCtx.buffer)

	data, err := f.Serialize(int32(7))
	require.NoError(t, err)
	require.NotEmpty(t, data)
}

func TestCallbackPanicCleanup(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	require.NoError(t, f.RegisterExtension(
		registryFreezeExtension{}, 7104, registryPanicSerializer{}))

	require.Panics(t, func() {
		_ = f.SerializeWithCallback(
			NewByteBuffer(nil), &registryFreezeExtension{Value: 1}, nil)
	})
	require.NoError(t, f.SerializeWithCallback(NewByteBuffer(nil), int32(7), nil))
}
