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

type registryFreezeSnapshot struct {
	serializers       int
	typeNames         int
	typeIDs           int
	userTypeIDs       int
	types             int
	namespacedTypes   int
	namedTypes        int
	typeDefs          int
	definitionIDs     int
	typePointers      int
	unionTypes        int
	typeIDCounter     uint32
	dynamicWriteIndex uint32
}

func takeRegistryFreezeSnapshot(r *TypeResolver) registryFreezeSnapshot {
	return registryFreezeSnapshot{
		serializers:       len(r.typeToSerializers),
		typeNames:         len(r.typeToTypeInfo),
		typeIDs:           len(r.typeIDToTypeInfo),
		userTypeIDs:       len(r.userTypeIdToTypeInfo),
		types:             len(r.typesInfo),
		namespacedTypes:   len(r.nsTypeToTypeInfo),
		namedTypes:        len(r.namedTypeToTypeInfo),
		typeDefs:          len(r.typeToTypeDef),
		definitionIDs:     len(r.defIdToTypeDef),
		typePointers:      len(r.typePointerCache),
		unionTypes:        len(r.unionTypeCache),
		typeIDCounter:     r.typeIDCounter,
		dynamicWriteIndex: r.dynamicWriteStringID,
	}
}

func TestRegistryFreezeRegistrations(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	_, err := f.Serialize(int32(1))
	require.NoError(t, err)
	f.Reset()

	before := takeRegistryFreezeSnapshot(f.typeResolver)
	attempts := []struct {
		name string
		call func() error
	}{
		{"struct ID", func() error { return f.RegisterStruct(registryFreezeStruct{}, 7101) }},
		{"struct name", func() error { return f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezeStruct") }},
		{"union ID", func() error { return f.RegisterUnion(registryFreezeUnion{}, 7102, nil) }},
		{"union name", func() error { return f.RegisterUnionByName(registryFreezeUnion{}, "test.RegistryFreezeUnion", nil) }},
		{"enum ID", func() error { return f.RegisterEnum(registryFreezeEnum(0), 7103) }},
		{"enum name", func() error { return f.RegisterEnumByName(registryFreezeEnum(0), "test.RegistryFreezeEnum") }},
		{"extension ID", func() error { return f.RegisterExtension(registryFreezeExtension{}, 7104, nil) }},
		{"extension name", func() error {
			return f.RegisterExtensionByName(registryFreezeExtension{}, "test.RegistryFreezeExtension", nil)
		}},
	}
	for _, attempt := range attempts {
		t.Run(attempt.name, func(t *testing.T) {
			require.ErrorIs(t, attempt.call(), ErrRegistryFrozen)
		})
	}
	require.Equal(t, before, takeRegistryFreezeSnapshot(f.typeResolver))
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
		{"DeserializeFrom", func(f *Fory) error { return f.DeserializeFrom(NewByteBuffer(nil), new(int32)) }},
		{"SerializeWithCallback", func(f *Fory) error { return f.SerializeWithCallback(NewByteBuffer(nil), badDecimal, nil) }},
		{"DeserializeWithCallbackBuffers", func(f *Fory) error {
			return f.DeserializeWithCallbackBuffers(NewByteBuffer(nil), nil, nil)
		}},
		{"generic Serialize", func(f *Fory) error { _, err := Serialize(f, badDecimal); return err }},
		{"generic Deserialize", func(f *Fory) error { return Deserialize(f, nil, new(int32)) }},
		{"DeserializeFromStream", func(f *Fory) error {
			return f.DeserializeFromStream(NewInputStream(bytes.NewReader(nil)), new(int32))
		}},
		{"DeserializeFromReader", func(f *Fory) error { return f.DeserializeFromReader(bytes.NewReader(nil), new(int32)) }},
		{"Marshal", func(f *Fory) error { _, err := f.Marshal(badDecimal); return err }},
		{"Unmarshal", func(f *Fory) error { return f.Unmarshal(nil, new(int32)) }},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			f := New(WithXlang(false), WithCompatible(false))
			require.Error(t, test.root(f))
			require.ErrorIs(t,
				f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezeRoot"),
				ErrRegistryFrozen)
		})
	}
}

func TestBorrowedBufferPanicRestore(t *testing.T) {
	writer := New(WithXlang(false), WithCompatible(false))
	data, err := writer.Serialize(int32(7))
	require.NoError(t, err)
	data = bytes.Clone(data)

	f := New(WithXlang(false), WithCompatible(false))
	borrowed := NewByteBuffer(bytes.Clone(data))
	require.Panics(t, func() {
		_ = f.DeserializeFrom(borrowed, int32(0))
	})
	require.Zero(t, borrowed.ReaderIndex())

	var value int32
	require.NoError(t, f.Deserialize(data, &value))
	require.Equal(t, int32(7), value)
	require.Zero(t, borrowed.ReaderIndex())
	require.ErrorIs(t,
		f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezePanic"),
		ErrRegistryFrozen)
}
