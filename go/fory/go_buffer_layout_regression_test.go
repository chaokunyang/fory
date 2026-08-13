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
	"unsafe"

	"github.com/stretchr/testify/require"
)

type wideFixedFieldStruct struct {
	F00 float64
	F01 float64
	F02 float64
	F03 float64
	F04 float64
	F05 float64
	F06 float64
	F07 float64
	F08 float64
	F09 float64
	F10 float64
	F11 float64
	F12 float64
	F13 float64
	F14 float64
	F15 float64
	F16 float64
	F17 float64
	F18 float64
	F19 float64
	F20 float64
	F21 float64
	F22 float64
	F23 float64
	F24 float64
	F25 float64
	F26 float64
	F27 float64
	F28 float64
	F29 float64
	F30 float64
	F31 float64
	F32 float64
	F33 float64
}

func TestWideFixedFieldOffsets(t *testing.T) {
	f := New(WithXlang(true), WithCompatible(false))
	require.NoError(t, f.RegisterStruct(wideFixedFieldStruct{}, 1700))
	expectedInfoSize := uintptr(24)
	if unsafe.Sizeof(uintptr(0)) == 4 {
		expectedInfoSize = 16
	}
	require.Equal(t, expectedInfoSize, unsafe.Sizeof(PrimitiveFieldInfo{}))

	var input wideFixedFieldStruct
	inputValue := reflect.ValueOf(&input).Elem()
	for i := 0; i < inputValue.NumField(); i++ {
		inputValue.Field(i).SetFloat(float64(i) + 0.25)
	}

	data, err := f.Marshal(&input)
	require.NoError(t, err)
	var output wideFixedFieldStruct
	require.NoError(t, f.Unmarshal(data, &output))
	require.Equal(t, input, output)
}

func TestUnalignedBufferReadPaths(t *testing.T) {
	t.Run("Varuint36", func(t *testing.T) {
		const value = uint64(1<<35 | 0x12345)
		buf := newUnalignedReadBuffer(func(buf *ByteBuffer) {
			buf.WriteVaruint36Small(value)
		})
		require.Equal(t, value, buf.ReadVaruint36Small(nil))
	})

	t.Run("VarUint64", func(t *testing.T) {
		const value = uint64(0xfedcba9876543210)
		buf := newUnalignedReadBuffer(func(buf *ByteBuffer) {
			buf.WriteVarUint64(value)
		})
		require.Equal(t, value, buf.ReadVarUint64(nil))
	})

	t.Run("TaggedUint64", func(t *testing.T) {
		const value = uint64(0x1122334455667788)
		buf := newUnalignedReadBuffer(func(buf *ByteBuffer) {
			buf.WriteTaggedUint64(value)
		})
		require.Equal(t, value, buf.ReadTaggedUint64(nil))
	})
}

func TestVaruint36SixByteBoundary(t *testing.T) {
	const value = uint64(1<<35 | 0x1234567)

	encoded := NewByteBuffer(make([]byte, 16))
	encoded.WriteVaruint36Small(value)
	standard := NewByteBuffer(make([]byte, 16))
	standard.WriteVarUint64(value)
	require.Equal(t, standard.Bytes(), encoded.Bytes())
	require.Len(t, encoded.Bytes(), 6)
	require.NotZero(t, encoded.Bytes()[4]&0x80)

	fastData := make([]byte, 8)
	copy(fastData, encoded.Bytes())
	fast := NewByteBuffer(fastData)
	var fastErr Error
	require.Equal(t, value, fast.ReadVaruint36Small(&fastErr))
	require.True(t, fastErr.Ok())
	require.Equal(t, 6, fast.ReaderIndex())

	slow := NewByteBuffer(encoded.Bytes())
	var slowErr Error
	require.Equal(t, value, slow.ReadVaruint36Small(&slowErr))
	require.True(t, slowErr.Ok())
	require.Equal(t, 6, slow.ReaderIndex())
}

func newUnalignedReadBuffer(writeValue func(*ByteBuffer)) *ByteBuffer {
	buf := NewByteBuffer(make([]byte, 32))
	buf.WriteByte_(0)
	writeValue(buf)
	buf.SetReaderIndex(1)
	return buf
}
