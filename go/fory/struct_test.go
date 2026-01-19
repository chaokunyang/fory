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
	"reflect"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestUnsignedTypeSerialization(t *testing.T) {
	type TestStruct struct {
		U32Var    uint32 `fory:"compress=true"`
		U32Fixed  uint32 `fory:"compress=false"`
		U64Var    uint64 `fory:"encoding=varint"`
		U64Fixed  uint64 `fory:"encoding=fixed"`
		U64Tagged uint64 `fory:"encoding=tagged"`
	}

	f := New(WithXlang(true), WithCompatible(false))
	f.RegisterStruct(TestStruct{}, 9999)

	obj := TestStruct{
		U32Var:    3000000000,
		U32Fixed:  4000000000,
		U64Var:    10000000000,
		U64Fixed:  15000000000,
		U64Tagged: 1000000000,
	}

	data, err := f.Serialize(obj)
	if err != nil {
		t.Fatalf("Serialize failed: %v", err)
	}

	var result any
	err = f.Deserialize(data, &result)
	if err != nil {
		t.Fatalf("Deserialize failed: %v", err)
	}

	resultObj := result.(*TestStruct)
	if resultObj.U32Var != obj.U32Var {
		t.Errorf("U32Var mismatch: expected %d, got %d", obj.U32Var, resultObj.U32Var)
	}
	if resultObj.U32Fixed != obj.U32Fixed {
		t.Errorf("U32Fixed mismatch: expected %d, got %d", obj.U32Fixed, resultObj.U32Fixed)
	}
	if resultObj.U64Var != obj.U64Var {
		t.Errorf("U64Var mismatch: expected %d, got %d", obj.U64Var, resultObj.U64Var)
	}
	if resultObj.U64Fixed != obj.U64Fixed {
		t.Errorf("U64Fixed mismatch: expected %d, got %d", obj.U64Fixed, resultObj.U64Fixed)
	}
	if resultObj.U64Tagged != obj.U64Tagged {
		t.Errorf("U64Tagged mismatch: expected %d, got %d", obj.U64Tagged, resultObj.U64Tagged)
	}
}

// Test struct for compatible mode tests (must be named struct at package level)
type SetFieldsStruct struct {
	SetField    Set[string]
	NullableSet Set[string] `fory:"nullable"`
	MapField    map[string]bool
	NullableMap map[string]bool `fory:"nullable"`
}

func TestSetFieldSerializationSchemaConsistent(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(false))
	err := f.RegisterStruct(SetFieldsStruct{}, 1001)
	require.NoError(t, err, "register struct error")

	// Create test object with Set and Map fields
	obj := SetFieldsStruct{
		SetField:    NewSet[string](),
		NullableSet: NewSet[string](),
		MapField:    map[string]bool{"key1": true, "key2": true},
		NullableMap: map[string]bool{"nk1": true},
	}
	obj.SetField.Add("x", "y")
	obj.NullableSet.Add("m", "n")

	// Serialize
	data, err := f.Serialize(obj)
	require.NoError(t, err, "Serialize failed")
	t.Logf("Serialized %d bytes", len(data))

	// Deserialize
	var result any
	err = f.Deserialize(data, &result)
	require.NoError(t, err, "Deserialize failed")

	resultObj := result.(*SetFieldsStruct)

	// Verify SetField
	require.Equal(t, 2, len(resultObj.SetField), "SetField length mismatch")
	require.True(t, resultObj.SetField.Contains("x"), "SetField should contain 'x'")
	require.True(t, resultObj.SetField.Contains("y"), "SetField should contain 'y'")

	// Verify NullableSet
	require.Equal(t, 2, len(resultObj.NullableSet), "NullableSet length mismatch")
	require.True(t, resultObj.NullableSet.Contains("m"), "NullableSet should contain 'm'")
	require.True(t, resultObj.NullableSet.Contains("n"), "NullableSet should contain 'n'")

	// Verify MapField
	require.Equal(t, 2, len(resultObj.MapField), "MapField length mismatch")
	require.True(t, resultObj.MapField["key1"])
	require.True(t, resultObj.MapField["key2"])

	// Verify NullableMap
	require.Equal(t, 1, len(resultObj.NullableMap), "NullableMap length mismatch")
	require.True(t, resultObj.NullableMap["nk1"])
}

func TestSetFieldSerializationCompatible(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(true))
	err := f.RegisterStruct(SetFieldsStruct{}, 1002)
	require.NoError(t, err, "register struct error")

	// Create test object with Set and Map fields
	obj := SetFieldsStruct{
		SetField:    NewSet[string](),
		NullableSet: NewSet[string](),
		MapField:    map[string]bool{"key1": true, "key2": true},
		NullableMap: map[string]bool{"nk1": true},
	}
	obj.SetField.Add("x", "y")
	obj.NullableSet.Add("m", "n")

	// Serialize
	data, err := f.Serialize(obj)
	require.NoError(t, err, "Serialize failed")
	t.Logf("Serialized %d bytes", len(data))

	// Deserialize
	var result any
	err = f.Deserialize(data, &result)
	require.NoError(t, err, "Deserialize failed")

	resultObj := result.(*SetFieldsStruct)

	// Verify SetField
	require.Equal(t, 2, len(resultObj.SetField), "SetField length mismatch")
	require.True(t, resultObj.SetField.Contains("x"), "SetField should contain 'x'")
	require.True(t, resultObj.SetField.Contains("y"), "SetField should contain 'y'")

	// Verify NullableSet
	require.Equal(t, 2, len(resultObj.NullableSet), "NullableSet length mismatch")
	require.True(t, resultObj.NullableSet.Contains("m"), "NullableSet should contain 'm'")
	require.True(t, resultObj.NullableSet.Contains("n"), "NullableSet should contain 'n'")

	// Verify MapField
	require.Equal(t, 2, len(resultObj.MapField), "MapField length mismatch")
	require.True(t, resultObj.MapField["key1"])
	require.True(t, resultObj.MapField["key2"])

	// Verify NullableMap
	require.Equal(t, 1, len(resultObj.NullableMap), "NullableMap length mismatch")
	require.True(t, resultObj.NullableMap["nk1"])
}

func TestSetFieldTypeId(t *testing.T) {
	// Test that Set fields have the correct TypeId in fingerprint
	type TestStruct struct {
		SetField Set[string]
		MapField map[string]bool
	}

	f := New(WithXlang(true), WithCompatible(false))
	err := f.RegisterStruct(TestStruct{}, 1003)
	require.NoError(t, err, "register struct error")

	// Get the struct serializer to inspect the fields
	typeInfo, err := f.typeResolver.getTypeInfo(reflect.ValueOf(TestStruct{}), false)
	require.NoError(t, err, "getTypeInfo failed")
	require.NotNil(t, typeInfo, "typeInfo is nil")

	structSer, ok := typeInfo.Serializer.(*structSerializer)
	require.True(t, ok, "serializer is not structSerializer")

	// Check each field
	for _, field := range structSer.fields {
		t.Logf("Field: %s, Type: %v, TypeId: %d, Serializer: %T",
			field.Meta.Name, field.Meta.Type, field.Meta.TypeId, field.Serializer)

		if field.Meta.Name == "set_field" {
			require.Equal(t, SET, field.Meta.TypeId, "SetField should have TypeId=SET(21)")
			require.NotNil(t, field.Serializer, "SetField serializer should not be nil")
			_, isSetSerializer := field.Serializer.(setSerializer)
			require.True(t, isSetSerializer, "SetField serializer should be setSerializer")
		}

		if field.Meta.Name == "map_field" {
			require.Equal(t, MAP, field.Meta.TypeId, "MapField should have TypeId=MAP(22)")
			require.NotNil(t, field.Serializer, "MapField serializer should not be nil")
		}
	}
}

func TestSkipAnyValueReadsSharedTypeMeta(t *testing.T) {
	type First struct {
		ID int
	}
	type Second struct {
		Name string
	}

	f := New(WithXlang(true), WithCompatible(true))
	require.NoError(t, f.RegisterStruct(First{}, 2001))
	require.NoError(t, f.RegisterStruct(Second{}, 2002))

	buf := NewByteBuffer(nil)
	require.NoError(t, f.SerializeTo(buf, First{ID: 10}))
	require.NoError(t, f.SerializeTo(buf, Second{Name: "ok"}))

	f.resetReadState()
	f.readCtx.SetData(buf.Bytes())

	isNull := readHeader(f.readCtx)
	require.False(t, isNull)
	SkipAnyValue(f.readCtx, true)
	require.NoError(t, f.readCtx.CheckError())

	f.resetReadState()
	isNull = readHeader(f.readCtx)
	require.False(t, isNull)

	var out any
	f.readCtx.ReadValue(reflect.ValueOf(&out).Elem(), RefModeTracking, true)
	require.NoError(t, f.readCtx.CheckError())

	result, ok := out.(*Second)
	require.True(t, ok)
	require.Equal(t, "ok", result.Name)
}
