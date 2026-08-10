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

type skipUnknownRefChild struct {
	Value int32 `fory:"id=1"`
}

type skipUnknownRefWriter struct {
	Removed *skipUnknownRefChild `fory:"id=1,ref=true"`
	Kept    any                  `fory:"id=2,ref=true"`
}

type skipUnknownRefReader struct {
	Kept any `fory:"id=2,ref=true"`
}

func TestSkipDeclaredCollectionProgress(t *testing.T) {
	t.Run("bodyless element uses allowance", func(t *testing.T) {
		f := New(WithMaxUnbackedContainerItems(0))
		buf := NewByteBuffer(nil)
		buf.WriteLength(1)
		buf.WriteByte(CollectionIsDeclElementType)

		f.readCtx.SetData(buf.Bytes())
		f.readCtx.remainingUnbackedContainerItems = f.config.MaxUnbackedContainerItems
		skipCollection(f.readCtx, FieldDef{
			typeSpec: NewCollectionTypeSpec(LIST, NewSimpleTypeSpec(NONE)),
		})
		require.Error(t, f.readCtx.CheckError())
	})

	t.Run("progressing element stays direct", func(t *testing.T) {
		f := New(WithMaxUnbackedContainerItems(0))
		buf := NewByteBuffer(nil)
		buf.WriteLength(1)
		buf.WriteByte(CollectionIsDeclElementType)
		buf.WriteInt32(7)
		buf.WriteByte(0x7f)

		f.readCtx.SetData(buf.Bytes())
		f.readCtx.remainingUnbackedContainerItems = f.config.MaxUnbackedContainerItems
		skipCollection(f.readCtx, FieldDef{
			typeSpec: NewCollectionTypeSpec(LIST, NewSimpleTypeSpec(INT32)),
		})
		require.NoError(t, f.readCtx.CheckError())
		require.Equal(t, byte(0x7f), f.readCtx.Buffer().ReadByte(f.readCtx.Err()))
	})
}

func TestSkippedStructRefPublication(t *testing.T) {
	writer := New(WithXlang(true), WithCompatible(true), WithTrackRef(true))
	require.NoError(t, writer.RegisterStruct(skipUnknownRefChild{}, 7100))
	require.NoError(t, writer.RegisterStruct(skipUnknownRefWriter{}, 7101))

	reader := New(WithXlang(true), WithCompatible(true), WithTrackRef(true))
	require.NoError(t, reader.RegisterStruct(skipUnknownRefReader{}, 7101))

	child := &skipUnknownRefChild{Value: 7}
	data, err := writer.Serialize(&skipUnknownRefWriter{Removed: child, Kept: child})
	require.NoError(t, err)

	var output skipUnknownRefReader
	require.NoError(t, reader.Deserialize(data, &output))
	value, ok := output.Kept.(*struct{})
	require.True(t, ok, "unexpected kept value %T: %#v", output.Kept, output.Kept)
	require.NotNil(t, value)
}
