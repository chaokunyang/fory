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
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

type registryFreezeStruct struct {
	Value int32
}

type registryFreezeUnion struct{}

type registryFreezeEnum int32

type registryIdentityEnum int32

type pointerRegistrationStruct struct {
	Value int32
}

type pointerRegistrationEnum int32

type pointerRegistrationUnion struct {
	caseID uint32
	value  any
}

func (pointerRegistrationUnion) ForyUnionMarker() {}

func (u pointerRegistrationUnion) ForyUnionGet() (uint32, any) {
	return u.caseID, u.value
}

func (u *pointerRegistrationUnion) ForyUnionSet(caseID uint32, value any) {
	u.caseID = caseID
	u.value = value
}

type pointerRegistrationExtension struct {
	Value int32
}

type pointerExtensionSerializer struct{}

func (pointerExtensionSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	if value.Kind() == reflect.Ptr {
		value = value.Elem()
	}
	ctx.Buffer().WriteInt32(int32(value.FieldByName("Value").Int()))
}

func (pointerExtensionSerializer) ReadData(ctx *ReadContext, value reflect.Value) {
	if value.Kind() == reflect.Ptr {
		value = value.Elem()
	}
	value.FieldByName("Value").SetInt(int64(ctx.Buffer().ReadInt32(ctx.Err())))
}

type registryFreezeExtension struct {
	Value int32
}

type registryPanicSerializer struct{}

func (registryPanicSerializer) WriteData(ctx *WriteContext, _ reflect.Value) {
	ctx.Err().SetError(SerializationError("write failure"))
	panic("write failure")
}

func (registryPanicSerializer) ReadData(*ReadContext, reflect.Value) {}

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
	type_ := reflect.TypeOf(registryFreezeStruct{})
	require.ErrorIs(t,
		f.typeResolver.RegisterStruct(type_, f.typeResolver.structTypeID(type_, false), 7105),
		ErrRegistryFrozen)
	require.Equal(t, before, takeRegistryFreezeSnapshot(f.typeResolver))
}

func TestNamedEncoderPreflight(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	before := takeRegistryFreezeSnapshot(f.typeResolver)
	overlong := strings.Repeat("a", 32_768)
	attempts := []struct {
		name     string
		wireName string
	}{
		{"namespace", overlong + ".RegistryFreezeStruct"},
		{"type name", overlong},
	}
	for _, attempt := range attempts {
		t.Run(attempt.name, func(t *testing.T) {
			err := f.RegisterStructByName(registryFreezeStruct{}, attempt.wireName)
			require.Error(t, err)
			require.Equal(t, before, takeRegistryFreezeSnapshot(f.typeResolver))
		})
	}

	require.NoError(t,
		f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezeStruct"))
}

func TestNamedRegistryIdentity(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	const name = "test.RegistryIdentity"
	require.NoError(t, f.RegisterStructByName(registryFreezeStruct{}, name))

	nameKey := namedTypeKey{"test", "RegistryIdentity"}
	owner := f.typeResolver.namedTypeToTypeInfo[nameKey]
	require.NotNil(t, owner)
	hashKey := nsTypeKey{owner.PkgPathBytes.Hashcode, owner.NameBytes.Hashcode}
	hashOwner := f.typeResolver.nsTypeToTypeInfo[hashKey]
	require.NotNil(t, hashOwner)
	before := takeRegistryFreezeSnapshot(f.typeResolver)
	attempts := []struct {
		name string
		call func() error
	}{
		{"same name struct", func() error {
			return f.RegisterStructByName(registryFreezeUnion{}, name)
		}},
		{"same name enum", func() error {
			return f.RegisterEnumByName(registryFreezeEnum(0), name)
		}},
		{"same name union", func() error {
			return f.RegisterUnionByName(registryFreezeUnion{}, name, NewUnionSerializer(
				UnionCase{ID: 0, Type: reflect.TypeOf(int32(0)), TypeID: INT32}))
		}},
		{"same name extension", func() error {
			return f.RegisterExtensionByName(
				registryFreezeExtension{}, name, registryPanicSerializer{})
		}},
		{"same type name", func() error {
			return f.RegisterStructByName(&registryFreezeStruct{}, "test.OtherIdentity")
		}},
		{"same type ID", func() error {
			return f.RegisterStruct(&registryFreezeStruct{}, 7110)
		}},
	}
	for _, attempt := range attempts {
		t.Run(attempt.name, func(t *testing.T) {
			require.Error(t, attempt.call())
			require.Equal(t, before, takeRegistryFreezeSnapshot(f.typeResolver))
			require.Same(t, owner, f.typeResolver.namedTypeToTypeInfo[nameKey])
			require.Same(t, hashOwner, f.typeResolver.nsTypeToTypeInfo[hashKey])
		})
	}

	want := registryFreezeStruct{Value: 7}
	data, err := f.Serialize(&want)
	require.NoError(t, err)
	var got registryFreezeStruct
	require.NoError(t, f.Deserialize(data, &got))
	require.Equal(t, want, got)
}

func TestNumericRegistryIdentity(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	const typeID = 7111
	require.NoError(t, f.RegisterEnum(registryFreezeEnum(0), typeID))

	owner := f.typeResolver.userTypeIdToTypeInfo[typeID]
	require.NotNil(t, owner)
	before := takeRegistryFreezeSnapshot(f.typeResolver)
	attempts := []struct {
		name string
		call func() error
	}{
		{"same type new ID", func() error {
			value := registryFreezeEnum(0)
			return f.RegisterEnum(&value, typeID+1)
		}},
		{"same ID new type", func() error {
			return f.RegisterEnum(registryIdentityEnum(0), typeID)
		}},
		{"same ID struct", func() error {
			return f.RegisterStruct(registryFreezeStruct{}, typeID)
		}},
		{"same ID union", func() error {
			return f.RegisterUnion(registryFreezeUnion{}, typeID, NewUnionSerializer(
				UnionCase{ID: 0, Type: reflect.TypeOf(int32(0)), TypeID: INT32}))
		}},
		{"same ID extension", func() error {
			return f.RegisterExtension(
				registryFreezeExtension{}, typeID, registryPanicSerializer{})
		}},
		{"same type name", func() error {
			value := registryFreezeEnum(0)
			return f.RegisterEnumByName(&value, "test.RegistryIdentityEnum")
		}},
	}
	for _, attempt := range attempts {
		t.Run(attempt.name, func(t *testing.T) {
			require.Error(t, attempt.call())
			require.Equal(t, before, takeRegistryFreezeSnapshot(f.typeResolver))
			require.Same(t, owner, f.typeResolver.userTypeIdToTypeInfo[typeID])
			require.Same(t, owner, f.typeResolver.typesInfo[reflect.TypeOf(registryFreezeEnum(0))])
		})
	}

	want := registryFreezeEnum(7)
	data, err := f.Serialize(want)
	require.NoError(t, err)
	var got registryFreezeEnum
	require.NoError(t, f.Deserialize(data, &got))
	require.Equal(t, want, got)
}

func requireRegisteredRoundTrip[T any](t *testing.T, f *Fory, want T) {
	t.Helper()
	data, err := f.Serialize(&want)
	require.NoError(t, err)
	var got T
	require.NoError(t, f.Deserialize(data, &got))
	require.Equal(t, want, got)
}

func requireValueRegistrationOwner(
	t *testing.T,
	f *Fory,
	valueType reflect.Type,
	owner *TypeInfo,
) {
	t.Helper()
	pointerType := reflect.PointerTo(valueType)
	doublePointerType := reflect.PointerTo(pointerType)
	require.NotNil(t, owner)
	require.Equal(t, valueType, owner.Type)
	valueInfo := f.typeResolver.typesInfo[valueType]
	pointerInfo := f.typeResolver.typesInfo[pointerType]
	require.NotNil(t, valueInfo)
	require.NotNil(t, pointerInfo)
	require.Equal(t, valueInfo.TypeID, pointerInfo.TypeID)
	require.Equal(t, valueInfo.UserTypeID, pointerInfo.UserTypeID)
	require.Contains(t, f.typeResolver.typeToSerializers, valueType)
	require.NotContains(t, f.typeResolver.typeToSerializers, doublePointerType)
	require.NotContains(t, f.typeResolver.typeToTypeInfo, doublePointerType)
	require.NotContains(t, f.typeResolver.typesInfo, doublePointerType)
	require.NotContains(t, f.typeResolver.typeToTypeDef, doublePointerType)
	require.NotContains(t, f.typeResolver.unionTypeCache, doublePointerType)
	require.NotContains(t, f.typeResolver.typePointerCache, typePointer(doublePointerType))
}

func TestPointerReflectTypeRegistration(t *testing.T) {
	type registrationFamily struct {
		name             string
		valueType        reflect.Type
		pointerType      reflect.Type
		userTypeID       uint32
		wireName         string
		registerID       func(*Fory, reflect.Type) error
		registerName     func(*Fory, reflect.Type) error
		registerResolver func(*Fory, reflect.Type) error
		roundTrip        func(*testing.T, *Fory)
	}
	unionSerializer := func() *UnionSerializer {
		return NewUnionSerializer(
			UnionCase{ID: 0, Type: reflect.TypeOf(int32(0)), TypeID: INT32})
	}
	families := []registrationFamily{
		{
			name:        "struct",
			valueType:   reflect.TypeOf(pointerRegistrationStruct{}),
			pointerType: reflect.TypeOf((*pointerRegistrationStruct)(nil)),
			userTypeID:  7120,
			wireName:    "test.PointerRegistrationStruct",
			registerID: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterStruct(type_, 7120)
			},
			registerName: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterStructByName(type_, "test.PointerRegistrationStruct")
			},
			registerResolver: func(f *Fory, type_ reflect.Type) error {
				return f.GetTypeResolver().RegisterStruct(type_, STRUCT, 7120)
			},
			roundTrip: func(t *testing.T, f *Fory) {
				requireRegisteredRoundTrip(t, f, pointerRegistrationStruct{Value: 7})
			},
		},
		{
			name:        "enum",
			valueType:   reflect.TypeOf(pointerRegistrationEnum(0)),
			pointerType: reflect.TypeOf((*pointerRegistrationEnum)(nil)),
			userTypeID:  7121,
			wireName:    "test.PointerRegistrationEnum",
			registerID: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterEnum(type_, 7121)
			},
			registerName: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterEnumByName(type_, "test.PointerRegistrationEnum")
			},
			registerResolver: func(f *Fory, type_ reflect.Type) error {
				return f.GetTypeResolver().RegisterEnum(type_, 7121)
			},
			roundTrip: func(t *testing.T, f *Fory) {
				requireRegisteredRoundTrip(t, f, pointerRegistrationEnum(7))
			},
		},
		{
			name:        "union",
			valueType:   reflect.TypeOf(pointerRegistrationUnion{}),
			pointerType: reflect.TypeOf((*pointerRegistrationUnion)(nil)),
			userTypeID:  7122,
			wireName:    "test.PointerRegistrationUnion",
			registerID: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterUnion(type_, 7122, unionSerializer())
			},
			registerName: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterUnionByName(
					type_, "test.PointerRegistrationUnion", unionSerializer())
			},
			registerResolver: func(f *Fory, type_ reflect.Type) error {
				return f.GetTypeResolver().RegisterUnion(type_, 7122, unionSerializer())
			},
			roundTrip: func(t *testing.T, f *Fory) {
				requireRegisteredRoundTrip(t, f, pointerRegistrationUnion{
					caseID: 0,
					value:  int32(7),
				})
			},
		},
		{
			name:        "extension",
			valueType:   reflect.TypeOf(pointerRegistrationExtension{}),
			pointerType: reflect.TypeOf((*pointerRegistrationExtension)(nil)),
			userTypeID:  7123,
			wireName:    "test.PointerRegistrationExtension",
			registerID: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterExtension(type_, 7123, pointerExtensionSerializer{})
			},
			registerName: func(f *Fory, type_ reflect.Type) error {
				return f.RegisterExtensionByName(
					type_, "test.PointerRegistrationExtension", pointerExtensionSerializer{})
			},
			registerResolver: func(f *Fory, type_ reflect.Type) error {
				return f.GetTypeResolver().RegisterExtension(
					type_, 7123, pointerExtensionSerializer{})
			},
			roundTrip: func(t *testing.T, f *Fory) {
				requireRegisteredRoundTrip(t, f, pointerRegistrationExtension{Value: 7})
			},
		},
	}

	for _, family := range families {
		pointerType := family.pointerType
		require.Equal(t, family.valueType, pointerType.Elem())
		t.Run(family.name+" facade ID", func(t *testing.T) {
			f := New(WithXlang(true), WithCompatible(false))
			require.NoError(t, family.registerID(f, pointerType))
			family.roundTrip(t, f)
			requireValueRegistrationOwner(
				t, f, family.valueType, f.typeResolver.userTypeIdToTypeInfo[family.userTypeID])
		})
		t.Run(family.name+" facade name", func(t *testing.T) {
			f := New(WithXlang(true), WithCompatible(false))
			require.NoError(t, family.registerName(f, pointerType))
			family.roundTrip(t, f)
			namespace, typeName, err := splitRegisteredName(family.wireName)
			require.NoError(t, err)
			requireValueRegistrationOwner(t, f, family.valueType,
				f.typeResolver.namedTypeToTypeInfo[namedTypeKey{namespace, typeName}])
		})
		t.Run(family.name+" resolver", func(t *testing.T) {
			f := New(WithXlang(true), WithCompatible(false))
			require.NoError(t, family.registerResolver(f, pointerType))
			family.roundTrip(t, f)
			requireValueRegistrationOwner(
				t, f, family.valueType, f.typeResolver.userTypeIdToTypeInfo[family.userTypeID])
		})
	}
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
	var value int32
	require.NoError(t, f.Deserialize(data, &value))
	require.Equal(t, int32(7), value)
	require.ErrorIs(t,
		f.RegisterStructByName(registryFreezeStruct{}, "test.RegistryFreezePanic"),
		ErrRegistryFrozen)
}

func TestCallbackBufferRestoreOrder(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	owned := f.readCtx.buffer
	borrowed := NewByteBuffer(nil)
	// Force root cleanup to panic so restoring the owned buffer after Reset
	// cannot accidentally satisfy this test.
	f.readCtx.refReader = nil

	require.Panics(t, func() {
		_ = f.DeserializeWithCallbackBuffers(borrowed, nil, nil)
	})
	require.Same(t, owned, f.readCtx.buffer)
}

func TestStreamBufferPanicRestore(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	owned := f.readCtx.buffer
	stream := NewInputStream(bytes.NewReader(nil))

	require.Panics(t, func() {
		_ = f.DeserializeFromStream(stream, int32(0))
	})
	require.Same(t, owned, f.readCtx.buffer)
}

func TestSerializeToPanicRestore(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	require.NoError(t, f.RegisterExtension(registryFreezeExtension{}, 7104, registryPanicSerializer{}))
	owned := f.writeCtx.buffer
	borrowed := NewByteBuffer(nil)

	require.Panics(t, func() {
		_ = f.SerializeTo(borrowed, &registryFreezeExtension{Value: 1})
	})
	require.Same(t, owned, f.writeCtx.buffer)
	require.NotZero(t, borrowed.WriterIndex())

	data, err := f.Serialize(int32(7))
	require.NoError(t, err)
	require.NotEmpty(t, data)
}

func TestCallbackPanicCleanup(t *testing.T) {
	f := New(WithXlang(false), WithCompatible(false))
	require.NoError(t, f.RegisterExtension(registryFreezeExtension{}, 7104, registryPanicSerializer{}))

	require.Panics(t, func() {
		_ = f.SerializeWithCallback(
			NewByteBuffer(nil), &registryFreezeExtension{Value: 1}, nil)
	})

	require.NoError(t, f.SerializeWithCallback(NewByteBuffer(nil), int32(7), nil))
}
