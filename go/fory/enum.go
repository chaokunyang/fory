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
// For xlang serialization, enums are written as Varuint32Small7 of their ordinal value
type enumSerializer struct {
	type_  reflect.Type
	typeID uint32 // Full type ID including user ID
}

func (s *enumSerializer) TypeId() TypeId {
	// Return the appropriate type ID based on how the enum was registered
	// For xlang with namespace/typename, typeID will be NAMED_ENUM
	// For numeric ID registration, it might be a user-defined ID
	if s.typeID == uint32(NAMED_ENUM) || IsNamespacedType(TypeId(s.typeID)) {
		return NAMED_ENUM
	}
	return ENUM
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
	ctx.buffer.WriteVaruint32Small7(ordinal)
	return nil
}

func (s *enumSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// For NAMED_ENUM, need to write type info including namespace and typename meta strings
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.buffer, typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

func (s *enumSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	ordinal := ctx.buffer.ReadVaruint32Small7()

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

func (s *enumSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
	}
	if readType {
		_ = ctx.buffer.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *enumSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

func (s *enumSerializer) GetType() reflect.Type {
	return s.type_
}
