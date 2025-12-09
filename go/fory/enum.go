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
	"fmt"
	"reflect"
)

// enumSerializer serializes Go enum types (which are typically int-based types)
// For xlang serialization, enums are written as VarUint32Small7 of their ordinal value
type enumSerializer struct {
	type_  reflect.Type
	typeID int32 // Full type ID including user ID
}

func (s *enumSerializer) TypeId() TypeId {
	return ENUM
}

func (s *enumSerializer) NeedToWriteRef() bool {
	// Enums are primitive values, no reference tracking needed
	return false
}

func (s *enumSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Convert the enum value to its integer ordinal
	var ordinal uint32
	switch value.Kind() {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		ordinal = uint32(value.Int())
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		ordinal = uint32(value.Uint())
	default:
		return fmt.Errorf("enum serializer: unsupported kind %v", value.Kind())
	}
	ctx.buffer.WriteVarUint32Small7(ordinal)
	return nil
}

func (s *enumSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		ctx.buffer.WriteVarUint32Small7(uint32(s.typeID))
	}
	return s.WriteData(ctx, value)
}

func (s *enumSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	ordinal := ctx.buffer.ReadVarUint32Small7()

	// Set the value based on the underlying kind
	switch value.Kind() {
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		value.SetInt(int64(ordinal))
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		value.SetUint(uint64(ordinal))
	default:
		return fmt.Errorf("enum serializer: unsupported kind %v", value.Kind())
	}
	return nil
}

func (s *enumSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	if readRef {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
	}
	if readType {
		_ = ctx.buffer.ReadVarUint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *enumSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

func (s *enumSerializer) GetType() reflect.Type {
	return s.type_
}
