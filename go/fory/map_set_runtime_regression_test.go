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

type runtimeMapValue interface {
	runtimeMapValue()
}

type runtimeMapItem struct {
	Value int32
}

func (*runtimeMapItem) runtimeMapValue() {}

type runtimeHashKey struct {
	Value any
}

type runtimePointerValue interface {
	runtimePointerValue()
}

type runtimePointerItem struct {
	Value int32
}

func (*runtimePointerItem) runtimePointerValue() {}

type runtimePointerCodec struct{}

func (runtimePointerCodec) WriteData(ctx *WriteContext, value reflect.Value) {
	if value.Kind() == reflect.Ptr {
		value = value.Elem()
	}
	ctx.Buffer().WriteVarint32(int32(value.Field(0).Int()))
}

func (runtimePointerCodec) ReadData(ctx *ReadContext, value reflect.Value) {
	value.Field(0).SetInt(int64(ctx.Buffer().ReadVarint32(ctx.Err())))
}

type runtimeAggregateValue interface {
	runtimeAggregateValue()
}

type runtimeAggregate [2]int32

func (runtimeAggregate) runtimeAggregateValue() {}

type runtimeAggregateCodec struct{}

func (runtimeAggregateCodec) WriteData(ctx *WriteContext, value reflect.Value) {
	ctx.Buffer().WriteInt32(int32(value.Index(0).Int()))
	ctx.Buffer().WriteInt32(int32(value.Index(1).Int()))
}

func (runtimeAggregateCodec) ReadData(ctx *ReadContext, value reflect.Value) {
	value.Index(0).SetInt(int64(ctx.Buffer().ReadInt32(ctx.Err())))
	value.Index(1).SetInt(int64(ctx.Buffer().ReadInt32(ctx.Err())))
}

func runtimePointerSpec() *TypeSpec {
	spec := NewSimpleTypeSpec(NAMED_EXT)
	spec.GoType = reflect.TypeOf(runtimePointerItem{})
	spec.TrackRef = true
	return spec
}

func runtimeAggregateSpec() *TypeSpec {
	spec := NewSimpleTypeSpec(EXT)
	spec.GoType = reflect.TypeOf(runtimeAggregate{})
	return spec
}

func readBodyWithBudget(
	t *testing.T, f *Fory, serializer Serializer, data []byte, target reflect.Value, budget int64,
) error {
	t.Helper()
	f.readCtx.SetData(bytes.Clone(data))
	f.readCtx.remainingGraphMemoryBytes = budget
	serializer.ReadData(f.readCtx, target)
	err := f.readCtx.CheckError()
	f.resetReadState()
	return err
}

func TestStandaloneInterfaceMapTarget(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(false), WithTrackRef(true))
	require.NoError(t, f.RegisterStructByName(runtimeMapItem{}, "test.RuntimeMapItem"))

	item := &runtimeMapItem{Value: 7}
	source := map[runtimeMapValue]runtimeMapValue{item: item}
	data, err := f.Serialize(source)
	require.NoError(t, err)

	var target map[runtimeMapValue]runtimeMapValue
	require.NotPanics(t, func() {
		err = f.Deserialize(data, &target)
	})
	require.NoError(t, err)
	require.Len(t, target, 1)
	for key, value := range target {
		require.Equal(t, int32(7), key.(*runtimeMapItem).Value)
		require.Same(t, key, value)
	}

	badData, err := New(WithXlang(true), WithCompatible(false), WithTrackRef(false)).Serialize(
		map[any]any{"key": "value"},
	)
	require.NoError(t, err)
	badReader := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
	target = nil
	require.NotPanics(t, func() {
		err = badReader.Deserialize(badData, &target)
	})
	require.Error(t, err)
	require.Contains(t, err.Error(), "not assignable")
}

func TestMapSetRejectDynamicHashKeys(t *testing.T) {
	key := reflect.ValueOf(runtimeHashKey{Value: []int32{1}})
	require.True(t, key.Type().Comparable())
	require.False(t, key.Comparable())

	t.Run("map", func(t *testing.T) {
		ctx := NewReadContext(false)
		value := reflect.MakeMap(reflect.TypeOf(map[runtimeHashKey]int32{}))
		require.NotPanics(t, func() {
			require.False(t, setMapValue(ctx, value, key, reflect.ValueOf(int32(1))))
		})
		require.Error(t, ctx.CheckError())
	})

	t.Run("set", func(t *testing.T) {
		ctx := NewReadContext(false)
		value := reflect.MakeMap(reflect.TypeOf(Set[runtimeHashKey]{}))
		require.NotPanics(t, func() {
			require.False(t, setMapKey(ctx, value, key, value.Type().Key()))
		})
		require.Error(t, ctx.CheckError())
	})
}

func TestMapSetReplaceDestination(t *testing.T) {
	t.Run("map", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		for _, source := range []map[int32]string{
			nil,
			{},
			{1: "one"},
			{1: "one", 2: "two"},
			{1: "one", 2: "two", 3: "three"},
		} {
			data, err := f.Serialize(source)
			require.NoError(t, err)
			target := map[int32]string{8: "stale", 9: "stale"}
			previous := target
			require.NoError(t, f.Deserialize(data, &target))
			require.Equal(t, source, target)
			if source == nil {
				require.Nil(t, target)
				continue
			}
			previous[10] = "old"
			target[11] = "new"
			if len(source) < 2 {
				require.NotContains(t, target, int32(10))
				require.NotContains(t, previous, int32(11))
			} else {
				require.Contains(t, target, int32(10))
				require.Contains(t, previous, int32(11))
			}
		}
	})

	t.Run("set", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		for _, source := range []Set[int32]{
			nil,
			{},
			{1: {}},
			{1: {}, 2: {}},
			{1: {}, 2: {}, 3: {}},
		} {
			data, err := f.Serialize(source)
			require.NoError(t, err)
			target := Set[int32]{8: {}, 9: {}}
			previous := target
			require.NoError(t, f.Deserialize(data, &target))
			require.Equal(t, source, target)
			if source == nil {
				require.Nil(t, target)
				continue
			}
			previous[10] = struct{}{}
			target[11] = struct{}{}
			if len(source) < 2 {
				require.NotContains(t, target, int32(10))
				require.NotContains(t, previous, int32(11))
			} else {
				require.Contains(t, target, int32(10))
				require.Contains(t, previous, int32(11))
			}
		}
	})
}

func TestSelectedPointerInterfaceOwners(t *testing.T) {
	t.Run("map", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(true))
		require.NoError(t, f.RegisterExtensionByName(
			runtimePointerItem{}, "test.RuntimePointerMapItem", runtimePointerCodec{}))
		mapType := reflect.TypeOf(map[runtimePointerValue]runtimePointerValue{})
		serializer, err := serializerForTypeSpec(
			f.typeResolver, mapType,
			NewMapTypeSpec(MAP, runtimePointerSpec(), runtimePointerSpec()),
		)
		require.NoError(t, err)
		item := &runtimePointerItem{Value: 7}
		f.writeCtx.Reset()
		serializer.WriteData(f.writeCtx, reflect.ValueOf(
			map[runtimePointerValue]runtimePointerValue{item: item},
		))
		require.NoError(t, f.writeCtx.CheckError())
		data := bytes.Clone(f.writeCtx.Buffer().Bytes())
		f.resetWriteState()

		entryBytes := int64(mapType.Key().Size() + mapType.Elem().Size())
		required := int64(graphMapOwnerBytes) + entryBytes + int64(reflect.TypeOf(runtimePointerItem{}).Size())
		for _, budget := range []int64{required - 1, required} {
			target := reflect.New(mapType).Elem()
			err := readBodyWithBudget(t, f, serializer, data, target, budget)
			if budget < required {
				require.Error(t, err)
				require.Contains(t, err.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, err)
			result := target.Interface().(map[runtimePointerValue]runtimePointerValue)
			require.Len(t, result, 1)
			for key, value := range result {
				require.Same(t, key, value)
			}
		}
	})

	t.Run("set_ref", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(true))
		require.NoError(t, f.RegisterExtensionByName(
			runtimePointerItem{}, "test.RuntimePointerSetItem", runtimePointerCodec{}))
		setType := reflect.TypeOf(Set[runtimePointerValue]{})
		serializer, err := serializerForTypeSpec(
			f.typeResolver, setType,
			NewCollectionTypeSpec(SET, runtimePointerSpec()),
		)
		require.NoError(t, err)

		buf := NewByteBuffer(nil)
		buf.WriteVarUint32(2)
		buf.WriteInt8(CollectionIsSameType | CollectionIsDeclElementType | CollectionTrackingRef)
		buf.WriteInt8(RefValueFlag)
		buf.WriteVarint32(8)
		buf.WriteInt8(RefFlag)
		buf.WriteVarUint32(0)
		data := bytes.Clone(buf.Bytes())

		entryBytes := int64(setType.Key().Size() + setType.Elem().Size())
		required := int64(graphSetOwnerBytes) + 2*entryBytes + int64(reflect.TypeOf(runtimePointerItem{}).Size())
		for _, budget := range []int64{required - 1, required} {
			target := reflect.New(setType).Elem()
			err := readBodyWithBudget(t, f, serializer, data, target, budget)
			if budget < required {
				require.Error(t, err)
				require.Contains(t, err.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, err)
			result := target.Interface().(Set[runtimePointerValue])
			require.Len(t, result, 1)
			for value := range result {
				require.Equal(t, int32(8), value.(*runtimePointerItem).Value)
			}
		}
	})
}

func TestExtensionInterfaceOwners(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
	require.NoError(t, f.RegisterExtension(runtimeAggregate{}, 4301, runtimeAggregateCodec{}))
	aggregateBytes := int64(reflect.TypeOf(runtimeAggregate{}).Size())

	t.Run("map", func(t *testing.T) {
		mapType := reflect.TypeOf(map[int32]runtimeAggregateValue{})
		for _, mode := range []string{"dynamic", "selected"} {
			t.Run(mode, func(t *testing.T) {
				var serializer Serializer
				var err error
				if mode == "dynamic" {
					serializer, err = f.typeResolver.getSerializerByType(mapType, false)
				} else {
					serializer, err = serializerForTypeSpec(
						f.typeResolver, mapType,
						NewMapTypeSpec(MAP, NewSimpleTypeSpec(VARINT32), runtimeAggregateSpec()),
					)
				}
				require.NoError(t, err)
				f.writeCtx.Reset()
				serializer.WriteData(f.writeCtx, reflect.ValueOf(
					map[int32]runtimeAggregateValue{1: runtimeAggregate{2, 3}},
				))
				require.NoError(t, f.writeCtx.CheckError())
				data := bytes.Clone(f.writeCtx.Buffer().Bytes())
				f.resetWriteState()

				entryBytes := int64(mapType.Key().Size() + mapType.Elem().Size())
				required := int64(graphMapOwnerBytes) + entryBytes + aggregateBytes
				for _, budget := range []int64{required - 1, required} {
					target := reflect.New(mapType).Elem()
					err := readBodyWithBudget(t, f, serializer, data, target, budget)
					if budget < required {
						require.Error(t, err)
						continue
					}
					require.NoError(t, err)
					require.Equal(t, runtimeAggregate{2, 3}, target.Interface().(map[int32]runtimeAggregateValue)[1])
				}
			})
		}
	})

	t.Run("set", func(t *testing.T) {
		setType := reflect.TypeOf(Set[runtimeAggregateValue]{})
		for _, mode := range []string{"dynamic", "selected"} {
			t.Run(mode, func(t *testing.T) {
				var serializer Serializer
				var err error
				if mode == "dynamic" {
					serializer, err = f.typeResolver.getSerializerByType(setType, false)
				} else {
					serializer, err = serializerForTypeSpec(
						f.typeResolver, setType,
						NewCollectionTypeSpec(SET, runtimeAggregateSpec()),
					)
				}
				require.NoError(t, err)
				f.writeCtx.Reset()
				serializer.WriteData(f.writeCtx, reflect.ValueOf(
					Set[runtimeAggregateValue]{runtimeAggregate{4, 5}: {}},
				))
				require.NoError(t, f.writeCtx.CheckError())
				data := bytes.Clone(f.writeCtx.Buffer().Bytes())
				f.resetWriteState()

				entryBytes := int64(setType.Key().Size() + setType.Elem().Size())
				required := int64(graphSetOwnerBytes) + entryBytes + aggregateBytes
				for _, budget := range []int64{required - 1, required} {
					target := reflect.New(setType).Elem()
					err := readBodyWithBudget(t, f, serializer, data, target, budget)
					if budget < required {
						require.Error(t, err)
						continue
					}
					require.NoError(t, err)
					require.Contains(t, target.Interface().(Set[runtimeAggregateValue]), runtimeAggregate{4, 5})
				}
			})
		}
	})

	t.Run("positive_value_marker", func(t *testing.T) {
		tracked := New(WithXlang(true), WithCompatible(false), WithTrackRef(true))
		require.NoError(t, tracked.RegisterExtension(runtimeAggregate{}, 4302, runtimeAggregateCodec{}))
		valueSpec := runtimeAggregateSpec()
		valueSpec.TrackRef = true
		mapType := reflect.TypeOf(map[int32]runtimeAggregateValue{})
		serializer, err := serializerForTypeSpec(
			tracked.typeResolver, mapType,
			NewMapTypeSpec(MAP, NewSimpleTypeSpec(VARINT32), valueSpec),
		)
		require.NoError(t, err)

		buf := NewByteBuffer(nil)
		buf.WriteVarUint32(1)
		buf.WriteInt8(KEY_DECL_TYPE | VALUE_DECL_TYPE | TRACKING_VALUE_REF)
		buf.WriteUint8(1)
		buf.WriteVarint32(1)
		buf.WriteInt8(1)
		buf.WriteInt32(6)
		buf.WriteInt32(7)

		entryBytes := int64(mapType.Key().Size() + mapType.Elem().Size())
		required := int64(graphMapOwnerBytes) + entryBytes + aggregateBytes
		for _, budget := range []int64{required - 1, required} {
			target := reflect.New(mapType).Elem()
			err := readBodyWithBudget(t, tracked, serializer, buf.Bytes(), target, budget)
			if budget < required {
				require.Error(t, err)
				require.Contains(t, err.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, err)
			require.Equal(t, runtimeAggregate{6, 7}, target.Interface().(map[int32]runtimeAggregateValue)[1])
		}
	})
}
