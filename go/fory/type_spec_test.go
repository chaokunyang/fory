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

func TestParseNestedTypeOverride(t *testing.T) {
	type example struct {
		Nested map[string][]*int32 `fory:"id=5,type=map(value=list(element=int32(nullable=true,encoding=fixed)))"`
	}

	field := reflect.TypeOf(example{}).Field(0)
	tag := parseForyTag(field)
	require.True(t, tag.TypeSet)
	require.True(t, tag.TypeValid)
	require.NotNil(t, tag.TypeOverride)
	require.Equal(t, "map", tag.TypeOverride.name)
	require.NotNil(t, tag.TypeOverride.value)
	require.Equal(t, "list", tag.TypeOverride.value.name)
	require.NotNil(t, tag.TypeOverride.value.element)
	require.Equal(t, "int32", tag.TypeOverride.value.element.name)
	require.Equal(t, "fixed", tag.TypeOverride.value.element.encoding)
	require.NotNil(t, tag.TypeOverride.value.element.nullable)
	require.True(t, *tag.TypeOverride.value.element.nullable)
}

func TestBuildTypeSpecWithNestedOverrides(t *testing.T) {
	type example struct {
		Size   uint64              `fory:"id=1,encoding=tagged"`
		Values []*int32            `fory:"id=3,type=list(element=int32(nullable=true,encoding=fixed))"`
		Data   map[int32]*int32    `fory:"id=4,type=map(value=int32(nullable=true,encoding=fixed))"`
		Nested map[string][]*int32 `fory:"id=5,type=map(value=list(element=int32(nullable=true,encoding=fixed)))"`
		Packed []int32             `fory:"id=6,type=list(element(encoding=fixed))"`
	}

	f := NewFory()
	resolver := f.GetTypeResolver()
	typ := reflect.TypeOf(example{})

	sizeSpec, err := buildTypeSpecForField(resolver, typ.Field(0).Type, parseForyTag(typ.Field(0)))
	require.NoError(t, err)
	require.Equal(t, TypeId(TAGGED_UINT64), sizeSpec.TypeId())

	valuesSpec, err := buildTypeSpecForField(resolver, typ.Field(1).Type, parseForyTag(typ.Field(1)))
	require.NoError(t, err)
	require.Equal(t, TypeId(LIST), valuesSpec.TypeId())
	require.NotNil(t, valuesSpec.Element())
	require.Equal(t, TypeId(INT32), valuesSpec.Element().TypeId())
	require.True(t, valuesSpec.Element().Nullable())

	dataSpec, err := buildTypeSpecForField(resolver, typ.Field(2).Type, parseForyTag(typ.Field(2)))
	require.NoError(t, err)
	require.Equal(t, TypeId(MAP), dataSpec.TypeId())
	require.Equal(t, TypeId(INT32), dataSpec.Value().TypeId())
	require.True(t, dataSpec.Value().Nullable())

	nestedSpec, err := buildTypeSpecForField(resolver, typ.Field(3).Type, parseForyTag(typ.Field(3)))
	require.NoError(t, err)
	require.Equal(t, TypeId(MAP), nestedSpec.TypeId())
	require.Equal(t, TypeId(LIST), nestedSpec.Value().TypeId())
	require.Equal(t, TypeId(INT32), nestedSpec.Value().Element().TypeId())
	require.True(t, nestedSpec.Value().Element().Nullable())

	packedSpec, err := buildTypeSpecForField(resolver, typ.Field(4).Type, parseForyTag(typ.Field(4)))
	require.NoError(t, err)
	require.Equal(t, TypeId(INT32_ARRAY), packedSpec.TypeId())
}

func TestNestedOverrideRoundTrip(t *testing.T) {
	type example struct {
		Values []*int32         `fory:"id=1,type=list(element=int32(nullable=true,encoding=fixed))"`
		Data   map[int32]*int32 `fory:"id=2,type=map(value=int32(nullable=true,encoding=fixed))"`
		Packed []int32          `fory:"id=3,type=list(element(encoding=fixed))"`
	}

	f := NewFory()
	require.NoError(t, f.RegisterNamedStruct(example{}, "test.NestedOverrideExample"))

	v1 := int32(11)
	v2 := int32(22)
	input := example{
		Values: []*int32{&v1, nil, &v2},
		Data: map[int32]*int32{
			1: &v1,
			2: nil,
		},
		Packed: []int32{3, 4, 5},
	}

	data, err := f.Marshal(&input)
	require.NoError(t, err)

	var output example
	require.NoError(t, f.Unmarshal(data, &output))
	require.Len(t, output.Values, 3)
	require.NotNil(t, output.Values[0])
	require.Equal(t, *input.Values[0], *output.Values[0])
	require.Nil(t, output.Values[1])
	require.NotNil(t, output.Values[2])
	require.Equal(t, *input.Values[2], *output.Values[2])
	require.Equal(t, input.Packed, output.Packed)
	require.Len(t, output.Data, 2)
	require.NotNil(t, output.Data[1])
	require.Equal(t, *input.Data[1], *output.Data[1])
	require.Nil(t, output.Data[2])
}
