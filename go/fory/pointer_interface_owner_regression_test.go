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
	"math/big"
	"reflect"
	"testing"
	"time"

	"github.com/apache/fory/go/fory/optional"
	"github.com/stretchr/testify/require"
)

type ownerScalarHolder struct {
	Value *int32
}

type ownerStringHolder struct {
	Value *string
}

type ownerTrackedStringHolder struct {
	Value *string `fory:"ref"`
}

type ownerOptionalSourceNode struct {
	Value int32
	Extra int32
}

type ownerOptionalTargetNode struct {
	Value int32
}

type ownerOptionalSource struct {
	AValue optional.Optional[*ownerOptionalSourceNode]
	ZAlias *ownerOptionalSourceNode
}

type ownerOptionalTarget struct {
	AValue optional.Optional[*ownerOptionalTargetNode]
	ZAlias *ownerOptionalTargetNode
}

type ownerInterfaceTarget struct {
	Value any
}

type ownerDateSource struct {
	Value Date
}

type ownerTimeSource struct {
	Value time.Time
}

type ownerDecimalSource struct {
	Value Decimal
}

type ownerUnion struct {
	caseID uint32
	value  any
}

func (ownerUnion) ForyUnionMarker() {}

func (u ownerUnion) ForyUnionGet() (uint32, any) {
	return u.caseID, u.value
}

func (u *ownerUnion) ForyUnionSet(caseID uint32, value any) {
	u.caseID = caseID
	u.value = value
}

type ownerNullNode struct {
	Value int32
}

type ownerNullHolder struct {
	Count   *int32
	Fast    *string
	Tracked *string `fory:"ref"`
	Node    *ownerNullNode
	Value   any
}

type ownerSkippedNullable struct {
	Optional *string `fory:"id=1"`
	Tail     int32   `fory:"id=2"`
}

type ownerSkippedNullableSource struct {
	Removed *ownerSkippedNullable `fory:"id=1,ref=true"`
	Kept    int32                 `fory:"id=2"`
}

type ownerSkippedNullableTarget struct {
	Kept int32 `fory:"id=2"`
}

func TestPointerOwnerBudget(t *testing.T) {
	value := int32(7)
	writer := New(WithCompatible(false))
	require.NoError(t, writer.RegisterStructByName(ownerScalarHolder{}, "test.OwnerScalarHolder"))
	data, err := writer.Serialize(&ownerScalarHolder{Value: &value})
	require.NoError(t, err)

	required := int64(reflect.TypeFor[int32]().Size())
	reader := New(WithCompatible(false), WithMaxGraphMemoryBytes(required-1))
	require.NoError(t, reader.RegisterStructByName(ownerScalarHolder{}, "test.OwnerScalarHolder"))
	var rejected ownerScalarHolder
	err = reader.Deserialize(data, &rejected)
	require.Error(t, err)

	reader = New(WithCompatible(false), WithMaxGraphMemoryBytes(required))
	require.NoError(t, reader.RegisterStructByName(ownerScalarHolder{}, "test.OwnerScalarHolder"))
	var decoded ownerScalarHolder
	require.NoError(t, reader.Deserialize(data, &decoded))
	require.NotNil(t, decoded.Value)
	require.Equal(t, value, *decoded.Value)

	existing := int32(1)
	decoded.Value = &existing
	reader = New(WithCompatible(false), WithMaxGraphMemoryBytes(1))
	require.NoError(t, reader.RegisterStructByName(ownerScalarHolder{}, "test.OwnerScalarHolder"))
	require.NoError(t, reader.Deserialize(data, &decoded))
	require.Same(t, &existing, decoded.Value)
	require.Equal(t, value, existing)
}

func TestStringPointerOwnerBudget(t *testing.T) {
	text := "value"
	tests := []struct {
		name       string
		value      any
		newTarget  func() any
		checkValue func(*testing.T, any)
	}{
		{
			name:      "fast",
			value:     &ownerStringHolder{Value: &text},
			newTarget: func() any { return &ownerStringHolder{} },
			checkValue: func(t *testing.T, target any) {
				require.Equal(t, text, *target.(*ownerStringHolder).Value)
			},
		},
		{
			name:      "tracked",
			value:     &ownerTrackedStringHolder{Value: &text},
			newTarget: func() any { return &ownerTrackedStringHolder{} },
			checkValue: func(t *testing.T, target any) {
				require.Equal(t, text, *target.(*ownerTrackedStringHolder).Value)
			},
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			writer := New(WithCompatible(false))
			require.NoError(t, writer.RegisterStructByName(test.value, "test.OwnerStringHolder"))
			data, err := writer.Serialize(test.value)
			require.NoError(t, err)

			required := int64(stringElementBytes)
			reader := New(WithCompatible(false), WithMaxGraphMemoryBytes(required-1))
			require.NoError(t, reader.RegisterStructByName(test.newTarget(), "test.OwnerStringHolder"))
			err = reader.Deserialize(data, test.newTarget())
			require.Error(t, err)

			reader = New(WithCompatible(false), WithMaxGraphMemoryBytes(required))
			require.NoError(t, reader.RegisterStructByName(test.newTarget(), "test.OwnerStringHolder"))
			target := test.newTarget()
			require.NoError(t, reader.Deserialize(data, target))
			test.checkValue(t, target)
		})
	}
}

func TestOptionalPointerOwner(t *testing.T) {
	node := &ownerOptionalSourceNode{Value: 7, Extra: 9}
	source := &ownerOptionalSource{
		AValue: optional.Some(node),
		ZAlias: node,
	}
	writer := New(WithCompatible(true), WithTrackRef(true))
	require.NoError(t, writer.RegisterStructByName(ownerOptionalSourceNode{}, "test.OwnerOptionalNode"))
	require.NoError(t, writer.RegisterStructByName(ownerOptionalSource{}, "test.OwnerOptional"))
	data, err := writer.Serialize(source)
	require.NoError(t, err)

	required := int64(reflect.TypeFor[ownerOptionalTargetNode]().Size())
	newReader := func(budget int64) *Fory {
		reader := New(WithCompatible(true), WithTrackRef(true), WithMaxGraphMemoryBytes(budget))
		require.NoError(t, reader.RegisterStructByName(ownerOptionalTargetNode{}, "test.OwnerOptionalNode"))
		require.NoError(t, reader.RegisterStructByName(ownerOptionalTarget{}, "test.OwnerOptional"))
		return reader
	}

	var rejected ownerOptionalTarget
	err = newReader(required-1).Deserialize(data, &rejected)
	require.Error(t, err)

	var decoded ownerOptionalTarget
	require.NoError(t, newReader(required).Deserialize(data, &decoded))
	require.True(t, decoded.AValue.IsSome())
	value := decoded.AValue.Unwrap()
	require.Equal(t, int32(7), value.Value)
	require.Same(t, value, decoded.ZAlias)
}

func TestDirectInterfaceOwners(t *testing.T) {
	decimal := NewDecimal(big.NewInt(123), 2)
	tests := []struct {
		name  string
		value any
		check func(*testing.T, any)
	}{
		{"date", Date{Year: 2026, Month: time.August, Day: 10}, func(t *testing.T, value any) {
			require.Equal(t, Date{Year: 2026, Month: time.August, Day: 10}, value)
		}},
		{"timestamp", time.Unix(123, 456).UTC(), func(t *testing.T, value any) {
			require.True(t, time.Unix(123, 456).UTC().Equal(value.(time.Time)))
		}},
		{"decimal", decimal, func(t *testing.T, value any) {
			require.True(t, decimal.Equal(value.(Decimal)))
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			writer := New(WithCompatible(false))
			var data []byte
			var err error
			switch value := test.value.(type) {
			case Decimal:
				data, err = Serialize(writer, value)
			default:
				data, err = writer.Serialize(value)
			}
			require.NoError(t, err)
			required := int64(reflect.TypeOf(test.value).Size())

			var rejected any
			err = New(WithCompatible(false), WithMaxGraphMemoryBytes(required-1)).Deserialize(data, &rejected)
			require.Error(t, err)

			var decoded any
			require.NoError(t, New(WithCompatible(false), WithMaxGraphMemoryBytes(required)).Deserialize(data, &decoded))
			test.check(t, decoded)
		})
	}
}

func TestUnionInterfaceBudget(t *testing.T) {
	newFory := func(budget int64) *Fory {
		f := New(
			WithXlang(true),
			WithCompatible(false),
			WithTrackRef(false),
			WithMaxGraphMemoryBytes(budget),
		)
		require.NoError(t, f.RegisterUnion(
			ownerUnion{},
			4024,
			NewUnionSerializer(UnionCase{ID: 0, Type: reflect.TypeFor[string]()}),
		))
		return f
	}

	input := &ownerUnion{caseID: 0, value: "value"}
	data, err := newFory(1).Serialize(input)
	require.NoError(t, err)

	var output any
	require.NoError(t, newFory(1).Deserialize(data, &output))
	require.Equal(t, *input, output)

	sliceInput := []any{*input}
	sliceData, err := newFory(1).Serialize(sliceInput)
	require.NoError(t, err)
	sliceBudget := int64(graphSliceOwnerBytes) + int64(reflect.TypeFor[any]().Size())
	var sliceOutput []any
	require.NoError(t, newFory(sliceBudget).Deserialize(sliceData, &sliceOutput))
	require.Equal(t, sliceInput, sliceOutput)
}

func TestCompatibleInterfaceOwners(t *testing.T) {
	decimal := NewDecimal(big.NewInt(123), 2)
	tests := []struct {
		name   string
		source any
		value  any
		check  func(*testing.T, any)
	}{
		{"date", ownerDateSource{Value: Date{Year: 2026, Month: time.August, Day: 10}}, Date{Year: 2026, Month: time.August, Day: 10}, func(t *testing.T, value any) {
			require.Equal(t, Date{Year: 2026, Month: time.August, Day: 10}, value)
		}},
		{"timestamp", ownerTimeSource{Value: time.Unix(123, 456).UTC()}, time.Unix(123, 456).UTC(), func(t *testing.T, value any) {
			require.True(t, time.Unix(123, 456).UTC().Equal(value.(time.Time)))
		}},
		{"decimal", ownerDecimalSource{Value: decimal}, decimal, func(t *testing.T, value any) {
			require.True(t, decimal.Equal(value.(Decimal)))
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			writer := New(WithCompatible(true))
			require.NoError(t, writer.RegisterStructByName(test.source, "test.OwnerInterface"))
			source := reflect.New(reflect.TypeOf(test.source))
			source.Elem().Set(reflect.ValueOf(test.source))
			data, err := writer.Serialize(source.Interface())
			require.NoError(t, err)

			required := int64(reflect.TypeOf(test.value).Size())
			newReader := func(budget int64) *Fory {
				reader := New(WithCompatible(true), WithMaxGraphMemoryBytes(budget))
				require.NoError(t, reader.RegisterStructByName(ownerInterfaceTarget{}, "test.OwnerInterface"))
				return reader
			}
			var rejected ownerInterfaceTarget
			err = newReader(required-1).Deserialize(data, &rejected)
			require.Error(t, err)

			var decoded ownerInterfaceTarget
			require.NoError(t, newReader(required).Deserialize(data, &decoded))
			test.check(t, decoded.Value)
		})
	}
}

func TestInterfacePointerOwner(t *testing.T) {
	writer := New(WithCompatible(false))
	writer.writeCtx.Reset()
	writer.writeCtx.WriteValue(reflect.ValueOf(int32(7)), RefModeTracking, true)
	require.NoError(t, writer.writeCtx.CheckError())
	data := append([]byte(nil), writer.writeCtx.Buffer().Bytes()...)

	read := func(budget int64, target **any) error {
		reader := New(WithCompatible(false), WithMaxGraphMemoryBytes(budget))
		reader.readCtx.SetData(data)
		reader.readCtx.remainingGraphMemoryBytes = budget
		value := reflect.ValueOf(target).Elem()
		(&ptrToInterfaceSerializer{}).ReadData(reader.readCtx, value)
		return reader.readCtx.CheckError()
	}

	required := int64(reflect.TypeFor[any]().Size())
	var rejected *any
	err := read(required-1, &rejected)
	require.Error(t, err)

	var decoded *any
	require.NoError(t, read(required, &decoded))
	require.NotNil(t, decoded)
	require.Equal(t, int32(7), *decoded)
}

func TestNullDestinationClearing(t *testing.T) {
	writer := New(WithCompatible(false))
	require.NoError(t, writer.RegisterStructByName(ownerNullNode{}, "test.OwnerNullNode"))
	require.NoError(t, writer.RegisterStructByName(ownerNullHolder{}, "test.OwnerNullHolder"))
	data, err := writer.Serialize(&ownerNullHolder{})
	require.NoError(t, err)

	count := int32(1)
	fast := "fast"
	tracked := "tracked"
	target := ownerNullHolder{
		Count:   &count,
		Fast:    &fast,
		Tracked: &tracked,
		Node:    &ownerNullNode{Value: 2},
		Value:   "stale",
	}
	reader := New(WithCompatible(false))
	require.NoError(t, reader.RegisterStructByName(ownerNullNode{}, "test.OwnerNullNode"))
	require.NoError(t, reader.RegisterStructByName(ownerNullHolder{}, "test.OwnerNullHolder"))
	require.NoError(t, reader.Deserialize(data, &target))
	require.Nil(t, target.Count)
	require.Nil(t, target.Fast)
	require.Nil(t, target.Tracked)
	require.Nil(t, target.Node)
	require.Nil(t, target.Value)
}

func TestSkipNullableEnvelope(t *testing.T) {
	writer := New(WithXlang(true), WithCompatible(true), WithTrackRef(true))
	require.NoError(t, writer.RegisterStruct(ownerSkippedNullable{}, 4401))
	require.NoError(t, writer.RegisterStruct(ownerSkippedNullableSource{}, 4402))
	reader := New(WithXlang(true), WithCompatible(true), WithTrackRef(true))
	require.NoError(t, reader.RegisterStruct(ownerSkippedNullableTarget{}, 4402))

	text := "value"
	for _, test := range []struct {
		name     string
		optional *string
	}{
		{"null", nil},
		{"value", &text},
	} {
		t.Run(test.name, func(t *testing.T) {
			data, err := writer.Serialize(&ownerSkippedNullableSource{
				Removed: &ownerSkippedNullable{Optional: test.optional, Tail: 11},
				Kept:    17,
			})
			require.NoError(t, err)
			var target ownerSkippedNullableTarget
			require.NoError(t, reader.Deserialize(data, &target))
			require.Equal(t, int32(17), target.Kept)
		})
	}
}
