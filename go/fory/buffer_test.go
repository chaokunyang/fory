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
	"testing"

	"github.com/stretchr/testify/require"
)

func TestVarint(t *testing.T) {
	for i := 1; i <= 32; i++ {
		buf := NewByteBuffer(nil)
		for j := 0; j < i; j++ {
			buf.WriteByte_(1) // make address unaligned.
			buf.ReadByte_()
		}
		checkVarint(t, buf, 1, 1)
		checkVarint(t, buf, 1<<6, 1)
		checkVarint(t, buf, 1<<7, 2)
		checkVarint(t, buf, 1<<13, 2)
		checkVarint(t, buf, 1<<14, 3)
		checkVarint(t, buf, 1<<20, 3)
		checkVarint(t, buf, 1<<21, 4)
		checkVarint(t, buf, 1<<27, 4)
		checkVarint(t, buf, 1<<28, 5)
		checkVarint(t, buf, MaxInt32, 5)
		checkVarintWrite(t, buf, -1)
		checkVarintWrite(t, buf, -1<<6)
		checkVarintWrite(t, buf, -1<<7)
		checkVarintWrite(t, buf, -1<<13)
		checkVarintWrite(t, buf, -1<<14)
		checkVarintWrite(t, buf, -1<<20)
		checkVarintWrite(t, buf, -1<<21)
		checkVarintWrite(t, buf, -1<<27)
		checkVarintWrite(t, buf, -1<<28)
		checkVarintWrite(t, buf, MinInt8)
		checkVarintWrite(t, buf, MinInt16)
		checkVarintWrite(t, buf, MinInt32)
	}
}

func checkVarint(t *testing.T, buf *ByteBuffer, value int32, bytesWritten int8) {
	require.Equal(t, buf.WriterIndex(), buf.ReaderIndex())
	actualBytesWritten := buf.WriteVarint32(value)
	require.Equal(t, bytesWritten, actualBytesWritten)
	varInt := buf.ReadVaruint32()
	require.Equal(t, buf.ReaderIndex(), buf.WriterIndex())
	require.Equal(t, value, varInt)
}

func checkVarintWrite(t *testing.T, buf *ByteBuffer, value int32) {
	require.Equal(t, buf.WriterIndex(), buf.ReaderIndex())
	buf.WriteVarint32(value)
	varInt := buf.ReadVarint32()
	require.Equal(t, buf.ReaderIndex(), buf.WriterIndex())
	require.Equal(t, value, varInt)
}
