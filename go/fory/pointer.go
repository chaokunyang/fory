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
)

// ptrToValueSerializer serializes a pointer to a concrete (non-interface) value
type ptrToValueSerializer struct {
	valueSerializer Serializer
}

// ptrToInterfaceSerializer serializes a pointer to an interface value
type ptrToInterfaceSerializer struct {
}

// ============================================================================
// ptrToValueSerializer - pointer to concrete type
// ============================================================================

func (s *ptrToValueSerializer) TypeId() TypeId {
	return s.valueSerializer.TypeId()
}

func (s *ptrToValueSerializer) NeedToWriteRef() bool { return true }

func (s *ptrToValueSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	elemValue := value.Elem()
	// Type info is written by Write() when writeType=true, not here.
	// This prevents double-writing of type info for nested struct fields.
	return s.valueSerializer.WriteData(ctx, elemValue)
}

func (s *ptrToValueSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	switch refMode {
	case RefModeTracking:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	case RefModeNullOnly:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}
	if writeType {
		// Always use TypeResolver to get the correct TypeID from registered TypeInfo
		// This ensures compatible mode uses NAMED_COMPATIBLE_STRUCT instead of NAMED_STRUCT
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value.Elem(), true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.Buffer(), typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

func (s *ptrToValueSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Check if value is already allocated (for circular reference handling)
	var newVal reflect.Value
	if value.IsNil() {
		// Allocate new value
		newVal = reflect.New(type_.Elem())
		value.Set(newVal)
	} else {
		// Value already allocated (circular reference case)
		newVal = value
	}

	// Register the pointer for reference tracking BEFORE reading data
	// This allows circular references to work correctly
	ctx.RefResolver().Reference(value)

	// Type info is read by Read() when readType=true, not here.
	// This prevents double-reading of type info for nested struct fields.
	if err := s.valueSerializer.ReadData(ctx, type_.Elem(), newVal.Elem()); err != nil {
		return err
	}

	return nil
}

func (s *ptrToValueSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	switch refMode {
	case RefModeTracking:
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8()
		if flag == NullFlag {
			return nil
		}
	}
	if readType {
		// Read type info - in compatible mode this contains the serializer with fieldDefs
		typeID := buf.ReadVaruint32Small7()
		internalTypeID := TypeId(typeID & 0xFF)
		// Check if this is a struct type that needs type meta reading
		if IsNamespacedType(TypeId(typeID)) || internalTypeID == COMPATIBLE_STRUCT || internalTypeID == STRUCT {
			typeInfo, err := ctx.TypeResolver().readTypeInfoWithTypeID(buf, typeID)
			if err != nil {
				return err
			}
			// Use the serializer from TypeInfo which has the remote field definitions
			if structSer, ok := typeInfo.Serializer.(*structSerializer); ok && len(structSer.fieldDefs) > 0 {
				// Allocate the pointer value if needed
				if value.IsNil() {
					value.Set(reflect.New(value.Type().Elem()))
				}
				ctx.RefResolver().Reference(value)
				return structSer.ReadData(ctx, value.Type().Elem(), value.Elem())
			}
		}
	}

	return s.ReadData(ctx, value.Type(), value)
}

func (s *ptrToValueSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

// ============================================================================
// ptrToInterfaceSerializer - pointer to interface type
// ============================================================================

func (s *ptrToInterfaceSerializer) TypeId() TypeId {
	// Pointer to interface is polymorphic, return 0 to indicate dynamic type
	return 0
}

func (s *ptrToInterfaceSerializer) NeedToWriteRef() bool { return true }

func (s *ptrToInterfaceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Get the concrete element that the interface pointer points to
	elemValue := value.Elem()

	// Use WriteValue to handle the polymorphic interface value
	return ctx.WriteValue(elemValue)
}

func (s *ptrToInterfaceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	switch refMode {
	case RefModeTracking:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	case RefModeNullOnly:
		if value.IsNil() {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		ctx.Buffer().WriteInt8(NotNullValueFlag)
	}

	// For interface pointers, we don't write type info here
	// WriteValue will handle the type info for the concrete value
	return s.WriteData(ctx, value)
}

func (s *ptrToInterfaceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Create a new interface pointer
	newVal := reflect.New(type_.Elem())

	// Use ReadValue to handle the polymorphic interface value
	if err := ctx.ReadValue(newVal.Elem()); err != nil {
		return err
	}

	value.Set(newVal)
	return nil
}

func (s *ptrToInterfaceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	switch refMode {
	case RefModeTracking:
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			// Reference found
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8()
		if flag == NullFlag {
			return nil
		}
	}

	return s.ReadData(ctx, value.Type(), value)
}

func (s *ptrToInterfaceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
