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
	"strconv"
	"testing"
	"time"

	"github.com/apache/fory/go/fory/optional"
	"github.com/stretchr/testify/require"
)

type scalarOptionalInt64Ptr struct {
	Value optional.Optional[*int64]
}

type scalarInt64Ptr struct {
	Value *int64
}

func TestCompatibleScalarPointer(t *testing.T) {
	writer := NewForyWithOptions(WithXlang(true), WithCompatible(true))
	require.NoError(t, writer.RegisterStructByName(scalarInt32{}, "CompatiblePointerScalar"))
	data, err := writer.Marshal(&scalarInt32{Value: 7})
	require.NoError(t, err)

	valueBytes := int64(reflect.TypeFor[int64]().Size())
	newReader := func(budget int64) *Fory {
		reader := NewForyWithOptions(
			WithXlang(true),
			WithCompatible(true),
			WithMaxGraphMemoryBytes(budget),
		)
		require.NoError(t, reader.RegisterStructByName(scalarInt64Ptr{}, "CompatiblePointerScalar"))
		return reader
	}

	var rejected scalarInt64Ptr
	err = newReader(valueBytes-1).Unmarshal(data, &rejected)
	require.Error(t, err)
	require.Contains(t, err.Error(), "maxGraphMemoryBytes")
	require.Nil(t, rejected.Value)

	var decoded scalarInt64Ptr
	require.NoError(t, newReader(valueBytes).Unmarshal(data, &decoded))
	require.NotNil(t, decoded.Value)
	require.Equal(t, int64(7), *decoded.Value)

	existing := int64(1)
	decoded.Value = &existing
	require.NoError(t, newReader(1).Unmarshal(data, &decoded))
	require.Same(t, &existing, decoded.Value)
	require.Equal(t, int64(7), existing)
}

func TestCompatibleOptionalPointer(t *testing.T) {
	writer := NewForyWithOptions(WithXlang(true), WithCompatible(true))
	require.NoError(t, writer.RegisterStructByName(scalarInt32{}, "OptionalPointerScalar"))
	data, err := writer.Marshal(&scalarInt32{Value: 0})
	require.NoError(t, err)

	valueBytes := int64(reflect.TypeFor[int64]().Size())
	reader := NewForyWithOptions(
		WithXlang(true),
		WithCompatible(true),
		WithMaxGraphMemoryBytes(valueBytes-1),
	)
	require.NoError(t, reader.RegisterStructByName(scalarOptionalInt64Ptr{}, "OptionalPointerScalar"))
	var rejected scalarOptionalInt64Ptr
	err = reader.Unmarshal(data, &rejected)
	require.Error(t, err)
	require.Contains(t, err.Error(), "maxGraphMemoryBytes")
	require.True(t, rejected.Value.IsNone())

	reader = NewForyWithOptions(
		WithXlang(true),
		WithCompatible(true),
		WithMaxGraphMemoryBytes(valueBytes),
	)
	require.NoError(t, reader.RegisterStructByName(scalarOptionalInt64Ptr{}, "OptionalPointerScalar"))
	var decoded scalarOptionalInt64Ptr
	require.NoError(t, reader.Unmarshal(data, &decoded))
	require.True(t, decoded.Value.IsSome())
	require.NotNil(t, decoded.Value.Unwrap())
	require.Equal(t, int64(0), *decoded.Value.Unwrap())

	existing := int64(7)
	reused := scalarOptionalInt64Ptr{Value: optional.Some(&existing)}
	reader = NewForyWithOptions(
		WithXlang(true),
		WithCompatible(true),
		WithMaxGraphMemoryBytes(1),
	)
	require.NoError(t, reader.RegisterStructByName(scalarOptionalInt64Ptr{}, "OptionalPointerScalar"))
	require.NoError(t, reader.Unmarshal(data, &reused))
	require.Same(t, &existing, reused.Value.Unwrap())
	require.Equal(t, int64(0), existing)
}

func TestCompatibleDecimalBounds(t *testing.T) {
	atLimit, ok := decimalRat(NewDecimal(big.NewInt(1), int32(maxCompatibleDecimalDigits)))
	require.True(t, ok)
	require.NotNil(t, atLimit)

	expanded := new(big.Int).Exp(big.NewInt(10), big.NewInt(maxCompatibleDecimalDigits+1), nil)
	normalized, ok := decimalRat(NewDecimal(expanded, int32(maxCompatibleDecimalDigits+1)))
	require.True(t, ok)
	require.Zero(t, normalized.Cmp(big.NewRat(1, 1)))
	maxScaleValue := new(big.Int).Exp(big.NewInt(10), big.NewInt(int64(maxDecimalScale)), nil)
	normalized, ok = decimalRat(NewDecimal(maxScaleValue, maxDecimalScale))
	require.True(t, ok)
	require.Zero(t, normalized.Cmp(big.NewRat(1, 1)))

	_, ok = decimalRat(NewDecimal(big.NewInt(1), int32(maxCompatibleDecimalDigits+1)))
	require.False(t, ok)
	tooManyDigits := new(big.Int).Lsh(big.NewInt(1), 1000)
	tooManyDigits.Mul(tooManyDigits, big.NewInt(10))
	_, ok = decimalRat(NewDecimal(tooManyDigits, 1))
	require.False(t, ok)
}

func TestCompatibleFloatScaleBound(t *testing.T) {
	atLimit := new(big.Int).Lsh(big.NewInt(1), uint(maxCompatibleDecimalDigits))
	count, ok := boundedFactorCount(atLimit, 2)
	require.True(t, ok)
	require.Equal(t, maxCompatibleDecimalDigits, count)

	overLimit := new(big.Int).Lsh(big.NewInt(1), uint(maxCompatibleDecimalDigits+1))
	_, ok = boundedFactorCount(overLimit, 2)
	require.False(t, ok)
}

func TestEpochDayInt64Range(t *testing.T) {
	cases := []struct {
		days  int64
		year  int64
		month time.Month
		day   int
	}{
		{MinInt64, -25252734927764585, time.June, 7},
		{MaxInt64, 25252734927768524, time.July, 27},
	}
	for _, tc := range cases {
		date, err := DateFromEpochDay(tc.days)
		if strconv.IntSize == 32 {
			require.Error(t, err)
			continue
		}
		require.NoError(t, err)
		require.Equal(t, tc.year, int64(date.Year))
		require.Equal(t, tc.month, date.Month)
		require.Equal(t, tc.day, date.Day)
		roundTrip, err := DateToEpochDay(date)
		require.NoError(t, err)
		require.Equal(t, tc.days, roundTrip)
	}
}
