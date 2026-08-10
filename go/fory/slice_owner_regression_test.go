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

type sliceReuseValue struct {
	Value int32
}

type sliceAggregateValue interface {
	sliceAggregateValue()
}

type sliceAggregate [2]int32

func (sliceAggregate) sliceAggregateValue() {}

type sliceAggregateCodec struct{}

func (sliceAggregateCodec) WriteData(ctx *WriteContext, value reflect.Value) {
	if value.Kind() == reflect.Ptr {
		value = value.Elem()
	}
	for i := 0; i < value.Len(); i++ {
		ctx.Buffer().WriteVarint32(int32(value.Index(i).Int()))
	}
}

func (sliceAggregateCodec) ReadData(ctx *ReadContext, value reflect.Value) {
	for i := 0; i < value.Len(); i++ {
		value.Index(i).SetInt(int64(ctx.Buffer().ReadVarint32(ctx.Err())))
	}
}

func registerSliceAggregate(t *testing.T, f *Fory) {
	t.Helper()
	require.NoError(t, f.RegisterExtension(
		sliceAggregate{}, 4023, sliceAggregateCodec{}))
}

func sliceAggregateSpec() *TypeSpec {
	spec := NewSimpleTypeSpec(EXT)
	spec.GoType = reflect.TypeOf(sliceAggregate{})
	return spec
}

func readSelectedAggregate(
	t *testing.T, source any, budget int64,
) (reflect.Value, error) {
	t.Helper()
	type_ := reflect.TypeOf(source)
	target := reflect.New(type_).Elem()
	return target, readSelectedAggregateInto(t, source, target, budget)
}

func readSelectedAggregateInto(
	t *testing.T, source any, target reflect.Value, budget int64,
) error {
	t.Helper()
	type_ := reflect.TypeOf(source)

	writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
	registerSliceAggregate(t, writer)
	writerSerializer, err := serializerForTypeSpec(
		writer.typeResolver, type_, NewCollectionTypeSpec(LIST, sliceAggregateSpec()))
	require.NoError(t, err)
	writer.writeCtx.Reset()
	writerSerializer.WriteData(writer.writeCtx, reflect.ValueOf(source))
	require.NoError(t, writer.writeCtx.CheckError())
	data := append([]byte(nil), writer.writeCtx.Buffer().Bytes()...)

	reader := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
	registerSliceAggregate(t, reader)
	readerSerializer, err := serializerForTypeSpec(
		reader.typeResolver, type_, NewCollectionTypeSpec(LIST, sliceAggregateSpec()))
	require.NoError(t, err)
	reader.readCtx.SetData(data)
	reader.readCtx.remainingGraphMemoryBytes = budget
	readerSerializer.ReadData(reader.readCtx, target)
	return reader.readCtx.CheckError()
}

func TestSliceDestinationReuse(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
	require.NoError(t, f.RegisterStructByName(sliceReuseValue{}, "test.SliceReuseValue"))

	data, err := f.Serialize([]sliceReuseValue{{Value: 1}})
	require.NoError(t, err)
	target := []sliceReuseValue{{Value: 9}, {Value: 8}, {Value: 7}}
	require.NoError(t, f.Deserialize(data, &target))
	require.Equal(t, []sliceReuseValue{{Value: 1}}, target)

	var nilSource []sliceReuseValue
	nilData, err := f.Serialize(nilSource)
	require.NoError(t, err)
	target = []sliceReuseValue{{Value: 6}}
	require.NoError(t, f.Deserialize(nilData, &target))
	require.Nil(t, target)

	nullableData, err := f.Serialize([]*sliceReuseValue{nil})
	require.NoError(t, err)
	old := &sliceReuseValue{Value: 5}
	nullableTarget := []*sliceReuseValue{old}
	require.NoError(t, f.Deserialize(nullableData, &nullableTarget))
	require.Equal(t, []*sliceReuseValue{nil}, nullableTarget)
}

func TestInterfaceSlotReuse(t *testing.T) {
	t.Run("concrete_array", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		require.NoError(t, f.RegisterStructByName(
			sliceReuseValue{}, "test.ArrayReuseValue"))
		input := [2]*sliceReuseValue{{Value: 4}, nil}
		data, err := f.Serialize(input)
		require.NoError(t, err)
		target := [2]*sliceReuseValue{{Value: 6}, {Value: 7}}
		require.NoError(t, f.Deserialize(data, &target))
		require.Equal(t, input, target)
	})

	t.Run("dynamic_array", func(t *testing.T) {
		f := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		data, err := f.Serialize([1]any{nil})
		require.NoError(t, err)
		target := [1]any{"stale"}
		require.NoError(t, f.Deserialize(data, &target))
		require.Nil(t, target[0])
	})

	t.Run("selected_array", func(t *testing.T) {
		source := [1]sliceAggregateValue{nil}
		target := [1]sliceAggregateValue{sliceAggregate{8, 9}}
		readErr := readSelectedAggregateInto(
			t, source, reflect.ValueOf(&target).Elem(), 1)
		require.NoError(t, readErr)
		require.Nil(t, target[0])
	})
}

func TestStringSliceNullable(t *testing.T) {
	buf := NewByteBuffer(nil)
	buf.WriteVarUint32(3)
	buf.WriteInt8(int8(CollectionDeclSameType | CollectionHasNull))
	buf.WriteInt8(NullFlag)
	buf.WriteInt8(NotNullValueFlag)
	writeString(buf, "value")
	buf.WriteInt8(NullFlag)

	f := New(WithMaxGraphMemoryBytes(
		int64(graphSliceOwnerBytes) + 3*int64(stringElementBytes)))
	f.readCtx.SetData(buf.Bytes())
	f.readCtx.remainingGraphMemoryBytes = f.config.MaxGraphMemoryBytes
	var target []string
	stringSliceSerializer{}.ReadData(f.readCtx, reflect.ValueOf(&target).Elem())
	require.NoError(t, f.readCtx.CheckError())
	require.Equal(t, []string{"", "value", ""}, target)
}

func TestCompatibleEmptySliceBudget(t *testing.T) {
	listSerializer, ok := newPrimitiveListSerializer(reflect.TypeOf([]int32{}), INT32)
	require.True(t, ok)
	listReader := listSerializer.(primitiveListSerializer)
	compatible := compatiblePrimitiveListToArraySerializer{listReader: listReader}

	writer := New()
	writer.writeCtx.Reset()
	listReader.WriteData(writer.writeCtx, reflect.ValueOf([]int32{}))
	require.NoError(t, writer.writeCtx.CheckError())
	data := append([]byte(nil), writer.writeCtx.Buffer().Bytes()...)

	t.Run("slice", func(t *testing.T) {
		for _, budget := range []int64{
			int64(graphSliceOwnerBytes) - 1,
			int64(graphSliceOwnerBytes),
		} {
			reader := New()
			reader.readCtx.SetData(data)
			reader.readCtx.remainingGraphMemoryBytes = budget
			var target []int32
			compatible.ReadData(reader.readCtx, reflect.ValueOf(&target).Elem())
			readErr := reader.readCtx.CheckError()
			if budget < int64(graphSliceOwnerBytes) {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.NotNil(t, target)
			require.Empty(t, target)
		}
	})

	t.Run("array", func(t *testing.T) {
		reader := New()
		reader.readCtx.SetData(data)
		reader.readCtx.remainingGraphMemoryBytes = 1
		var target [0]int32
		compatible.ReadData(reader.readCtx, reflect.ValueOf(&target).Elem())
		require.NoError(t, reader.readCtx.CheckError())
	})
}

func TestInterfaceAggregateBudget(t *testing.T) {
	aggregate := sliceAggregate{1, 2}
	aggregateBytes := int64(reflect.TypeOf(aggregate).Size())
	sliceBytes := int64(graphSliceOwnerBytes) +
		int64(reflect.TypeOf([]any{}).Elem().Size())

	t.Run("dynamic_slice", func(t *testing.T) {
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		registerSliceAggregate(t, writer)
		data, err := writer.Serialize([]any{aggregate})
		require.NoError(t, err)

		for _, budget := range []int64{sliceBytes + aggregateBytes - 1, sliceBytes + aggregateBytes} {
			reader := New(
				WithXlang(true), WithCompatible(false), WithTrackRef(false),
				WithMaxGraphMemoryBytes(budget),
			)
			registerSliceAggregate(t, reader)
			var target []any
			readErr := reader.Deserialize(data, &target)
			if budget < sliceBytes+aggregateBytes {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, aggregate, target[0])
		}
	})

	t.Run("dynamic_array", func(t *testing.T) {
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		registerSliceAggregate(t, writer)
		data, err := writer.Serialize([1]any{aggregate})
		require.NoError(t, err)

		for _, budget := range []int64{aggregateBytes - 1, aggregateBytes} {
			reader := New(
				WithXlang(true), WithCompatible(false), WithTrackRef(false),
				WithMaxGraphMemoryBytes(budget),
			)
			registerSliceAggregate(t, reader)
			var target [1]any
			readErr := reader.Deserialize(data, &target)
			if budget < aggregateBytes {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, aggregate, target[0])
		}
	})

	t.Run("selected_slice", func(t *testing.T) {
		source := []sliceAggregateValue{aggregate}
		for _, budget := range []int64{sliceBytes + aggregateBytes - 1, sliceBytes + aggregateBytes} {
			target, readErr := readSelectedAggregate(t, source, budget)
			if budget < sliceBytes+aggregateBytes {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, source, target.Interface())
		}
	})

	t.Run("selected_array", func(t *testing.T) {
		source := [1]sliceAggregateValue{aggregate}
		for _, budget := range []int64{aggregateBytes - 1, aggregateBytes} {
			target, readErr := readSelectedAggregate(t, source, budget)
			if budget < aggregateBytes {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, source, target.Interface())
		}
	})

	t.Run("mixed_dynamic_slice", func(t *testing.T) {
		source := []any{aggregate, "leaf"}
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		registerSliceAggregate(t, writer)
		data, err := writer.Serialize(source)
		require.NoError(t, err)
		required := int64(graphSliceOwnerBytes) +
			2*int64(reflect.TypeOf([]any{}).Elem().Size()) + aggregateBytes

		for _, budget := range []int64{required - 1, required} {
			reader := New(
				WithXlang(true), WithCompatible(false), WithTrackRef(false),
				WithMaxGraphMemoryBytes(budget),
			)
			registerSliceAggregate(t, reader)
			var target []any
			readErr := reader.Deserialize(data, &target)
			if budget < required {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, source, target)
		}
	})

	t.Run("mixed_dynamic_array", func(t *testing.T) {
		source := [2]any{aggregate, "leaf"}
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		registerSliceAggregate(t, writer)
		data, err := writer.Serialize(source)
		require.NoError(t, err)

		for _, budget := range []int64{aggregateBytes - 1, aggregateBytes} {
			reader := New(
				WithXlang(true), WithCompatible(false), WithTrackRef(false),
				WithMaxGraphMemoryBytes(budget),
			)
			registerSliceAggregate(t, reader)
			var target [2]any
			readErr := reader.Deserialize(data, &target)
			if budget < aggregateBytes {
				require.Error(t, readErr)
				require.Contains(t, readErr.Error(), "maxGraphMemoryBytes")
				continue
			}
			require.NoError(t, readErr)
			require.Equal(t, source, target)
		}
	})

	t.Run("pointer", func(t *testing.T) {
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		require.NoError(t, writer.RegisterExtensionByName(
			sliceAggregate{}, "test.SliceAggregatePointer", sliceAggregateCodec{}))
		data, err := writer.Serialize([]any{&aggregate})
		require.NoError(t, err)

		required := sliceBytes + aggregateBytes
		reader := New(
			WithXlang(true), WithCompatible(false), WithTrackRef(false),
			WithMaxGraphMemoryBytes(required),
		)
		require.NoError(t, reader.RegisterExtensionByName(
			sliceAggregate{}, "test.SliceAggregatePointer", sliceAggregateCodec{}))
		var target []any
		require.NoError(t, reader.Deserialize(data, &target))
		require.Equal(t, &aggregate, target[0])
	})

	t.Run("leaf", func(t *testing.T) {
		writer := New(WithXlang(true), WithCompatible(false), WithTrackRef(false))
		data, err := writer.Serialize([]any{"value"})
		require.NoError(t, err)
		reader := New(
			WithXlang(true), WithCompatible(false), WithTrackRef(false),
			WithMaxGraphMemoryBytes(sliceBytes),
		)
		var target []any
		require.NoError(t, reader.Deserialize(data, &target))
		require.Equal(t, []any{"value"}, target)
	})
}
