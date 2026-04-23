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
	"time"

	"github.com/stretchr/testify/require"
)

func TestDateUsesVarint64InXlangAndInt32InNative(t *testing.T) {
	date := Date{Year: 1969, Month: time.December, Day: 31}
	expectedDays, err := DateToEpochDay(date)
	require.NoError(t, err)

	for _, tc := range []struct {
		name  string
		fory  *Fory
		check func(*testing.T, *ByteBuffer)
	}{
		{
			name: "xlang",
			fory: NewFory(WithTrackRef(false), WithXlang(true)),
			check: func(t *testing.T, buf *ByteBuffer) {
				var err Error
				require.Equal(t, byte(XLangFlag), buf.ReadByte(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, int8(NotNullValueFlag), buf.ReadInt8(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, uint8(DATE), buf.ReadUint8(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, expectedDays, buf.ReadVarint64(&err))
				require.False(t, err.HasError(), err.Error())
			},
		},
		{
			name: "native",
			fory: NewFory(WithTrackRef(false), WithXlang(false)),
			check: func(t *testing.T, buf *ByteBuffer) {
				var err Error
				require.Equal(t, byte(0), buf.ReadByte(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, int8(NotNullValueFlag), buf.ReadInt8(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, uint8(DATE), buf.ReadUint8(&err))
				require.False(t, err.HasError(), err.Error())
				require.Equal(t, int32(expectedDays), buf.ReadInt32(&err))
				require.False(t, err.HasError(), err.Error())
			},
		},
	} {
		t.Run(tc.name, func(t *testing.T) {
			data, err := tc.fory.Serialize(date)
			require.NoError(t, err)

			buf := NewByteBuffer(data)
			tc.check(t, buf)
			require.Equal(t, len(data), buf.ReaderIndex())
		})
	}
}

func TestXlangDateSupportsWideRange(t *testing.T) {
	fory := NewFory(WithTrackRef(false), WithXlang(true))
	date := Date{Year: 200000, Month: time.January, Day: 1}

	expectedDays, err := DateToEpochDay(date)
	require.NoError(t, err)

	data, err := Serialize(fory, &date)
	require.NoError(t, err)

	buf := NewByteBuffer(data)
	var bufErr Error
	require.Equal(t, byte(XLangFlag), buf.ReadByte(&bufErr))
	require.False(t, bufErr.HasError(), bufErr.Error())
	require.Equal(t, int8(NotNullValueFlag), buf.ReadInt8(&bufErr))
	require.False(t, bufErr.HasError(), bufErr.Error())
	require.Equal(t, uint8(DATE), buf.ReadUint8(&bufErr))
	require.False(t, bufErr.HasError(), bufErr.Error())
	require.Equal(t, expectedDays, buf.ReadVarint64(&bufErr))
	require.False(t, bufErr.HasError(), bufErr.Error())
	require.Equal(t, len(data), buf.ReaderIndex())

	var decoded Date
	err = Deserialize(fory, data, &decoded)
	require.NoError(t, err)
	require.Equal(t, date, decoded)
}

func TestXlangDurationUsesNormalizedSecondsAndNanos(t *testing.T) {
	fory := NewFory(WithTrackRef(false), WithXlang(true))

	for _, tc := range []struct {
		name     string
		duration time.Duration
		seconds  int64
		nanos    int32
	}{
		{name: "zero", duration: 0, seconds: 0, nanos: 0},
		{name: "positive", duration: 1500 * time.Millisecond, seconds: 1, nanos: 500_000_000},
		{name: "negative subsecond", duration: -500 * time.Millisecond, seconds: -1, nanos: 500_000_000},
		{name: "negative with nanos", duration: -2*time.Second - time.Nanosecond, seconds: -3, nanos: 999_999_999},
		{name: "min", duration: time.Duration(MinInt64), seconds: -9_223_372_037, nanos: 145_224_192},
		{name: "max", duration: time.Duration(MaxInt64), seconds: 9_223_372_036, nanos: 854_775_807},
	} {
		t.Run(tc.name, func(t *testing.T) {
			data, err := fory.Serialize(tc.duration)
			require.NoError(t, err)

			buf := NewByteBuffer(data)
			var bufErr Error
			require.Equal(t, byte(XLangFlag), buf.ReadByte(&bufErr))
			require.False(t, bufErr.HasError(), bufErr.Error())
			require.Equal(t, int8(NotNullValueFlag), buf.ReadInt8(&bufErr))
			require.False(t, bufErr.HasError(), bufErr.Error())
			require.Equal(t, uint8(DURATION), buf.ReadUint8(&bufErr))
			require.False(t, bufErr.HasError(), bufErr.Error())
			require.Equal(t, tc.seconds, buf.ReadVarint64(&bufErr))
			require.False(t, bufErr.HasError(), bufErr.Error())
			require.Equal(t, tc.nanos, buf.ReadInt32(&bufErr))
			require.False(t, bufErr.HasError(), bufErr.Error())
			require.Equal(t, len(data), buf.ReaderIndex())

			var decoded time.Duration
			err = fory.Deserialize(data, &decoded)
			require.NoError(t, err)
			require.Equal(t, tc.duration, decoded)
		})
	}
}

func TestXlangDurationStructFieldUsesDurationType(t *testing.T) {
	type durationStruct struct {
		Duration time.Duration
	}

	fory := New(WithXlang(true), WithCompatible(false))
	err := fory.RegisterStruct(durationStruct{}, 1004)
	require.NoError(t, err)

	typeInfo, err := fory.typeResolver.getTypeInfo(reflect.ValueOf(durationStruct{}), false)
	require.NoError(t, err)
	structSer, ok := typeInfo.Serializer.(*structSerializer)
	require.True(t, ok)
	require.NoError(t, structSer.initialize(fory.typeResolver))

	var durationField *FieldInfo
	for i := range structSer.fields {
		if structSer.fields[i].Meta.Name == "duration" {
			durationField = &structSer.fields[i]
			break
		}
	}
	require.NotNil(t, durationField)
	require.Equal(t, TypeId(DURATION), durationField.Meta.TypeId)
	require.IsType(t, durationSerializer{}, durationField.Serializer)
	require.Equal(t, UnknownDispatchId, durationField.DispatchId)

	input := durationStruct{Duration: -500 * time.Millisecond}
	data, err := fory.Serialize(&input)
	require.NoError(t, err)

	var decoded durationStruct
	err = fory.Deserialize(data, &decoded)
	require.NoError(t, err)
	require.Equal(t, input, decoded)
}

func TestXlangDurationRejectsNonCanonicalNanos(t *testing.T) {
	fory := NewFory(WithTrackRef(false), WithXlang(true))
	buf := NewByteBuffer(nil)
	buf.WriteByte_(XLangFlag)
	buf.WriteInt8(NotNullValueFlag)
	buf.WriteUint8(uint8(DURATION))
	buf.WriteVarint64(0)
	buf.WriteInt32(-1)

	var decoded time.Duration
	err := fory.Deserialize(buf.Bytes(), &decoded)
	require.Error(t, err)
	require.Contains(t, err.Error(), "outside canonical range")
}
