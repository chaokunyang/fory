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
	"strconv"
	"strings"
	"testing"

	"github.com/apache/fory/go/fory/meta"
	"github.com/stretchr/testify/require"
)

type metadataScalarHolder struct {
	Value int32
}

type metadataInterfaceHolder struct {
	Value any
}

type metadataNamedHolder struct {
	Value int32
}

type metadataDuplicateTags struct {
	First  int32 `fory:"id=1"`
	Second int32 `fory:"id=1"`
}

type metadataNormalizedNames struct {
	FooBar  int32
	Foo_bar int32
}

type metadataHighTag struct {
	Value int32 `fory:"id=65551"`
}

type metadataMaxTag struct {
	Value int32 `fory:"id=4294967310"`
}

type metadataOverflowTag struct {
	Value int32 `fory:"id=4294967311"`
}

func metadataField(name string, spec *TypeSpec) FieldDef {
	return FieldDef{
		name:         name,
		nameEncoding: meta.UTF_8,
		typeSpec:     spec,
		tagID:        TagIDUseFieldName,
	}
}

func TestRuntimeMapTypePreflight(t *testing.T) {
	f := New(WithCompatible(true))
	intSpec := NewSimpleTypeSpec(INT32)
	stringSpec := NewSimpleTypeSpec(STRING)
	listSpec := NewCollectionTypeSpec(LIST, intSpec)
	mapSpec := NewMapTypeSpec(MAP, stringSpec, intSpec)

	invalid := []*TypeSpec{
		NewCollectionTypeSpec(SET, listSpec),
		NewCollectionTypeSpec(SET, NewSimpleTypeSpec(BINARY)),
		NewCollectionTypeSpec(SET, mapSpec),
		NewMapTypeSpec(MAP, listSpec, intSpec),
		NewMapTypeSpec(MAP, NewSimpleTypeSpec(DECIMAL), stringSpec),
	}
	for _, spec := range invalid {
		require.NotPanics(t, func() {
			_, err := spec.goTypeForResolver(f.typeResolver)
			require.Error(t, err)
		})
	}

	_, err := NewCollectionTypeSpec(SET, stringSpec).goTypeForResolver(f.typeResolver)
	require.NoError(t, err)
	_, err = NewMapTypeSpec(MAP, stringSpec, listSpec).goTypeForResolver(f.typeResolver)
	require.NoError(t, err)
}

func TestRemoteBindingPreflight(t *testing.T) {
	f := New(WithCompatible(true))
	remoteMap := NewMapTypeSpec(
		MAP,
		NewSimpleTypeSpec(STRING),
		NewCollectionTypeSpec(LIST, NewSimpleTypeSpec(INT32)),
	)

	scalar := newStructSerializerFromTypeDef(
		reflect.TypeOf(metadataScalarHolder{}), "", []FieldDef{metadataField("value", remoteMap)},
	)
	require.NotPanics(t, func() {
		require.Error(t, scalar.initialize(f.typeResolver))
	})

	container := newStructSerializerFromTypeDef(
		reflect.TypeOf(metadataInterfaceHolder{}), "",
		[]FieldDef{metadataField("value", NewCollectionTypeSpec(LIST, NewSimpleTypeSpec(INT32)))},
	)
	require.NotPanics(t, func() {
		err := container.initialize(f.typeResolver)
		require.Error(t, err)
	})
}

func TestRemoteSerializerNoScratch(t *testing.T) {
	f := New(WithCompatible(true))
	remote := newStructSerializerFromTypeDef(
		reflect.TypeOf(metadataScalarHolder{}), "",
		[]FieldDef{metadataField("value", NewSimpleTypeSpec(VARINT32))},
	)
	require.NoError(t, remote.initialize(f.typeResolver))
	require.Nil(t, remote.tempValue)

	local := newStructSerializer(reflect.TypeOf(metadataScalarHolder{}), "")
	require.NoError(t, local.initialize(f.typeResolver))
	require.NotNil(t, local.tempValue)
}

func TestNamedHashCandidateCheck(t *testing.T) {
	const localName = "a-bcdefghijklmno"
	const collidingName = "b-bcdefgIijklmno"
	f := New(WithCompatible(false))
	require.NoError(t, f.RegisterStructByName(metadataNamedHolder{}, localName))

	info := f.typeResolver.namedTypeToTypeInfo[namedTypeKey{"", localName}]
	require.NotNil(t, info)
	remoteName := NewMetaStringBytes(
		[]byte(collidingName), ComputeMetaStringHash([]byte(collidingName), meta.UTF_8),
	)
	require.Equal(t, info.NameBytes.Hashcode, remoteName.Hashcode)

	var readErr Error
	resolved := f.typeResolver.resolveTypeInfoByMetaBytes(
		info.PkgPathBytes,
		remoteName,
		nsTypeKey{info.PkgPathBytes.Hashcode, remoteName.Hashcode},
		uint32(NAMED_STRUCT),
		&readErr,
	)
	require.Nil(t, resolved)
	require.True(t, readErr.HasError())

	readErr = Error{}
	resolved = f.typeResolver.resolveTypeInfoByMetaBytes(
		info.PkgPathBytes,
		info.NameBytes,
		nsTypeKey{info.PkgPathBytes.Hashcode, info.NameBytes.Hashcode},
		uint32(NAMED_ENUM),
		&readErr,
	)
	require.Nil(t, resolved)
	require.True(t, readErr.HasError())
}

func TestNamedAliasAcceptance(t *testing.T) {
	f := New(WithCompatible(false))
	require.NoError(t, f.RegisterStructByName(metadataNamedHolder{}, "example.MetadataNamed"))
	info := f.typeResolver.namedTypeToTypeInfo[namedTypeKey{"example", "MetadataNamed"}]
	require.NotNil(t, info)

	aliasMeta, err := f.typeResolver.typeNameEncoder.EncodeWithEncoding("MetadataNamed", meta.UTF_8)
	require.NoError(t, err)
	alias := NewMetaStringBytes(aliasMeta.GetEncodedBytes(), ComputeMetaStringHash(aliasMeta.GetEncodedBytes(), meta.UTF_8))
	key := nsTypeKey{info.PkgPathBytes.Hashcode, alias.Hashcode}
	delete(f.typeResolver.nsTypeToTypeInfo, key)

	var wrongKindErr Error
	require.Nil(t, f.typeResolver.resolveTypeInfoByMetaBytes(
		info.PkgPathBytes, alias, key, uint32(NAMED_ENUM), &wrongKindErr,
	))
	require.NotContains(t, f.typeResolver.nsTypeToTypeInfo, key)

	var acceptedErr Error
	require.Same(t, info, f.typeResolver.resolveTypeInfoByMetaBytes(
		info.PkgPathBytes, alias, key, uint32(NAMED_STRUCT), &acceptedErr,
	))
	require.False(t, acceptedErr.HasError())
	require.Same(t, info, f.typeResolver.nsTypeToTypeInfo[key])
}

func TestPackedNameTailBits(t *testing.T) {
	decoder := meta.NewDecoder('.', '_')
	value, err := decoder.Decode([]byte{0}, meta.LOWER_SPECIAL)
	require.NoError(t, err)
	require.Equal(t, "a", value)

	_, err = decoder.Decode([]byte{1}, meta.LOWER_SPECIAL)
	require.Error(t, err)
}

func TestLargeReadCacheOwnership(t *testing.T) {
	resolver := NewMetaStringResolver()
	data := []byte("0123456789abcdefg")
	hash := ComputeMetaStringHash(data, meta.UTF_8)
	buffer := NewByteBuffer(nil)
	buffer.WriteVarUint32Small7(uint32(len(data)) << 1)
	buffer.WriteInt64(hash)
	buffer.Write(data)
	buffer.SetReaderIndex(0)

	var readErr Error
	require.NotNil(t, resolver.ReadMetaStringBytes(buffer, &readErr))
	require.False(t, readErr.HasError())
	require.Empty(t, resolver.hashToMetaStrBytes)
	resolver.ResetRead()
	require.Empty(t, resolver.hashToMetaStrBytes)
}

func TestLargeCacheCollision(t *testing.T) {
	resolver := NewMetaStringResolver()
	data := []byte("0123456789abcdefg")
	hash := ComputeMetaStringHash(data, meta.UTF_8)
	resolver.hashToMetaStrBytes[hash] = NewMetaStringBytes([]byte("different-body-value"), hash)
	buffer := NewByteBuffer(nil)
	buffer.WriteVarUint32Small7(uint32(len(data)) << 1)
	buffer.WriteInt64(hash)
	buffer.Write(data)
	buffer.SetReaderIndex(0)

	var readErr Error
	require.Nil(t, resolver.ReadMetaStringBytes(buffer, &readErr))
	require.True(t, readErr.HasError())
	require.Empty(t, resolver.dynamicIDToEnumString)
}

func TestMetadataFieldIdentityValidation(t *testing.T) {
	f := New(WithCompatible(true))
	require.Error(t, f.RegisterStructByName(metadataDuplicateTags{}, "example.DuplicateTags"))
	require.Error(t, f.RegisterStructByName(metadataNormalizedNames{}, "example.NormalizedNames"))
	require.NoError(t, f.RegisterStructByName(metadataHighTag{}, "example.HighTag"))

	if strconv.IntSize == 64 {
		maxFory := New(WithCompatible(true))
		require.NoError(t, maxFory.RegisterStructByName(metadataMaxTag{}, "example.MaxTag"))
		overflowFory := New(WithCompatible(true))
		require.Error(t, overflowFory.RegisterStructByName(metadataOverflowTag{}, "example.OverflowTag"))
	}

}

func TestDynamicIntegerWireCodecs(t *testing.T) {
	f := New()
	tests := []struct {
		typeID TypeId
		value  any
		write  func(*ByteBuffer)
	}{
		{INT32, int32(0x12345678), func(b *ByteBuffer) { b.WriteInt32(0x12345678) }},
		{INT64, int64(0x1234567890abcdef), func(b *ByteBuffer) { b.WriteInt64(0x1234567890abcdef) }},
		{TAGGED_INT64, int64(-123456789), func(b *ByteBuffer) { b.WriteTaggedInt64(-123456789) }},
		{UINT32, uint32(0xfedcba98), func(b *ByteBuffer) { b.WriteUint32(0xfedcba98) }},
		{UINT64, uint64(0xfedcba9876543210), func(b *ByteBuffer) { b.WriteUint64(0xfedcba9876543210) }},
		{TAGGED_UINT64, uint64(123456789), func(b *ByteBuffer) { b.WriteTaggedUint64(123456789) }},
	}
	for _, test := range tests {
		t.Run(strconv.Itoa(int(test.typeID)), func(t *testing.T) {
			info := f.typeResolver.typeIDToTypeInfo[uint32(test.typeID)]
			require.NotNil(t, info)
			require.Equal(t, uint32(test.typeID), info.TypeID)

			buffer := NewByteBuffer(nil)
			test.write(buffer)
			ctx := f.readCtx
			ctx.Reset()
			ctx.SetData(buffer.Bytes())
			value := reflect.New(info.Type).Elem()
			info.Serializer.ReadData(ctx, value)
			require.False(t, ctx.HasError())
			require.Equal(t, test.value, value.Interface())
			require.Equal(t, len(buffer.Bytes()), ctx.buffer.ReaderIndex())
		})
	}
}

func TestResolverRegistrationFreeze(t *testing.T) {
	f := New()
	_, err := f.Serialize(int32(1))
	require.NoError(t, err)
	r := f.GetTypeResolver()
	require.ErrorIs(t, r.RegisterStruct(reflect.TypeOf(metadataScalarHolder{}), STRUCT, 1001), ErrRegistryFrozen)
	require.ErrorIs(t, r.RegisterUnion(reflect.TypeOf(metadataScalarHolder{}), 1002, nil), ErrRegistryFrozen)
	require.ErrorIs(t, r.RegisterEnum(reflect.TypeOf(int32(0)), 1003), ErrRegistryFrozen)
	require.ErrorIs(t, r.RegisterExtension(reflect.TypeOf(strings.Builder{}), 1004, nil), ErrRegistryFrozen)
}
