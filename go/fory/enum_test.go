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

type auditEnum int32
type otherAuditEnum int32
type namedAuditEnum int32

func TestEnumReadConsumesRegisteredTypeInfo(t *testing.T) {
	f := NewFory(WithXlang(true), WithCompatible(false))
	require.NoError(t, f.RegisterEnum(auditEnum(0), 101))
	enumType := reflect.TypeOf(auditEnum(0))
	serializer, err := f.typeResolver.getSerializerByType(enumType, false)
	require.NoError(t, err)
	typeInfo := f.typeResolver.typesInfo[enumType]
	require.NotNil(t, typeInfo)

	buf := NewByteBuffer(nil)
	bufErr := &Error{}
	f.typeResolver.WriteTypeInfo(buf, typeInfo, bufErr)
	require.NoError(t, bufErr.CheckError())
	buf.WriteVarUint32Small7(2)

	f.readCtx.SetData(buf.Bytes())
	var result auditEnum
	serializer.Read(f.readCtx, RefModeNone, true, false, reflect.ValueOf(&result).Elem())
	require.NoError(t, f.readCtx.CheckError())
	require.Equal(t, auditEnum(2), result)
	require.Equal(t, buf.WriterIndex(), f.readCtx.Buffer().ReaderIndex())
}

func TestEnumReadRejectsMismatchedRegisteredTypeInfo(t *testing.T) {
	f := NewFory(WithXlang(true), WithCompatible(false))
	require.NoError(t, f.RegisterEnum(auditEnum(0), 101))
	require.NoError(t, f.RegisterEnum(otherAuditEnum(0), 102))
	enumType := reflect.TypeOf(auditEnum(0))
	otherType := reflect.TypeOf(otherAuditEnum(0))
	serializer, err := f.typeResolver.getSerializerByType(enumType, false)
	require.NoError(t, err)
	otherTypeInfo := f.typeResolver.typesInfo[otherType]
	require.NotNil(t, otherTypeInfo)

	buf := NewByteBuffer(nil)
	bufErr := &Error{}
	f.typeResolver.WriteTypeInfo(buf, otherTypeInfo, bufErr)
	require.NoError(t, bufErr.CheckError())
	buf.WriteVarUint32Small7(2)

	f.readCtx.SetData(buf.Bytes())
	var result auditEnum
	serializer.Read(f.readCtx, RefModeNone, true, false, reflect.ValueOf(&result).Elem())
	require.Error(t, f.readCtx.CheckError())
}

func TestNamedEnumReadConsumesNamedTypeInfo(t *testing.T) {
	f := NewFory(WithXlang(true), WithCompatible(false))
	require.NoError(t, f.RegisterNamedEnum(namedAuditEnum(0), "example.NamedAuditEnum"))
	enumType := reflect.TypeOf(namedAuditEnum(0))
	serializer, err := f.typeResolver.getSerializerByType(enumType, false)
	require.NoError(t, err)
	typeInfo := f.typeResolver.typesInfo[enumType]
	require.NotNil(t, typeInfo)

	buf := NewByteBuffer(nil)
	bufErr := &Error{}
	f.typeResolver.WriteTypeInfo(buf, typeInfo, bufErr)
	require.NoError(t, bufErr.CheckError())
	buf.WriteVarUint32Small7(3)

	f.readCtx.SetData(buf.Bytes())
	var result namedAuditEnum
	serializer.Read(f.readCtx, RefModeNone, true, false, reflect.ValueOf(&result).Elem())
	require.NoError(t, f.readCtx.CheckError())
	require.Equal(t, namedAuditEnum(3), result)
	require.Equal(t, buf.WriterIndex(), f.readCtx.Buffer().ReaderIndex())
}
