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

// Serializer is the unified interface for all serialization.
// It provides reflect.Value-based API for efficient serialization.
type Serializer interface {
	// Write is the entry point for serialization.
	//
	// This method orchestrates the complete serialization process, handling reference tracking,
	// type information, and delegating to WriteData for the actual data serialization.
	//
	// Unlike Java's unified ref tracking approach, Go uses per-serializer ref/type handling.
	// Map, Slice, Interface, and Pointer types each have different ref tracking requirements,
	// so each serializer controls how to write ref/type info. This allows more efficient
	// serialization by avoiding unnecessary ref checks for value types and enabling
	// type-specific optimizations.
	//
	// Parameters:
	//   - refMode: controls reference/null handling behavior:
	//     - RefModeNone: skip ref handling entirely
	//     - RefModeNullOnly: only write null flag (NullFlag or NotNullValueFlag)
	//     - RefModeTracking: full reference tracking with WriteRefOrNull
	//   - writeType: when true, writes type information; when false, skips it
	Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error

	// WriteData serializes using reflect.Value.
	// Does NOT write ref/type info - caller handles that.
	WriteData(ctx *WriteContext, value reflect.Value) error

	// Read is the entry point for deserialization.
	//
	// This method orchestrates the complete deserialization process, handling reference tracking,
	// type information validation, and delegating to ReadData for the actual data deserialization.
	//
	// Unlike Java's unified ref tracking approach, Go uses per-serializer ref/type handling.
	// Map, Slice, Interface, and Pointer types each have different ref tracking requirements,
	// so each serializer controls how to read ref/type info. This allows more efficient
	// deserialization by avoiding unnecessary ref checks for value types and enabling
	// type-specific optimizations.
	//
	// Parameters:
	//   - refMode: controls reference/null handling behavior:
	//     - RefModeNone: skip ref handling entirely
	//     - RefModeNullOnly: only read null flag
	//     - RefModeTracking: full reference tracking with TryPreserveRefId
	//   - readType: when true, reads type information from buffer; when false, skips it
	Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error

	// ReadData deserializes directly into the provided reflect.Value.
	// Does NOT read ref/type info - caller handles that.
	// For non-trivial types (slices, maps), implementations should reuse existing capacity when possible.
	// This method should ONLY be used by collection serializers for nested element deserialization.
	// For general deserialization, use ReadFull instead.
	ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error

	// ReadWithTypeInfo deserializes with pre-read type information.
	//
	// This method is used when type information has already been read from the buffer
	// and needs to be passed to the deserialization logic. This is common in polymorphic
	// deserialization scenarios where the runtime type differs from the static type.
	//
	// Parameters:
	//   - refMode: controls reference/null handling behavior:
	//     - RefModeNone: skip ref handling entirely
	//     - RefModeNullOnly: only read null flag
	//     - RefModeTracking: full reference tracking with TryPreserveRefId
	//   - typeInfo: pre-read type information; do NOT read type info again from buffer
	//
	// Important: do NOT read type info from the buffer in this method. The typeInfo
	// parameter contains the already-read type metadata. Reading it again will cause
	// buffer position errors.
	ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error
}

// ExtensionSerializer is a simplified interface for user-implemented extension serializers.
// Users implement this interface to provide custom serialization logic for types
// registered via RegisterExtensionTypeByName.
//
// Unlike the full Serializer interface, ExtensionSerializer only requires implementing
// the core data serialization logic - reference tracking, type info, and protocol
// details are handled automatically by Fory.
//
// Example:
//
//	type MyExtSerializer struct{}
//
//	func (s *MyExtSerializer) Write(buf *ByteBuffer, value interface{}) error {
//	    myExt := value.(MyExt)
//	    buf.WriteVarint32(myExt.Id)
//	    return nil
//	}
//
//	func (s *MyExtSerializer) Read(buf *ByteBuffer) (interface{}, error) {
//	    id := buf.ReadVarint32(err)
//	    return MyExt{Id: id}, nil
//	}
//
//	// Register with custom serializer
//	f.RegisterExtensionTypeByName(MyExt{}, "my_ext", &MyExtSerializer{})
type ExtensionSerializer interface {
	// Write serializes the value's data to the buffer.
	// Only write the data fields - don't write ref flags or type info.
	Write(buf *ByteBuffer, value interface{}) error

	// Read deserializes the value's data from the buffer.
	// Only read the data fields - don't read ref flags or type info.
	// Returns the deserialized value.
	Read(buf *ByteBuffer) (interface{}, error)
}

// extensionSerializerAdapter wraps an ExtensionSerializer to implement the full Serializer interface.
// This adapter handles reference tracking, type info writing/reading, and delegates the actual
// data serialization to the user-provided ExtensionSerializer.
type extensionSerializerAdapter struct {
	type_      reflect.Type
	typeTag    string
	userSerial ExtensionSerializer
}

func (s *extensionSerializerAdapter) GetType() reflect.Type { return s.type_ }

func (s *extensionSerializerAdapter) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Delegate to user's serializer
	return s.userSerial.Write(ctx.Buffer(), value.Interface())
}

func (s *extensionSerializerAdapter) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	switch refMode {
	case RefModeTracking:
		refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	case RefModeNullOnly:
		if isNil(value) {
			buf.WriteInt8(NullFlag)
			return nil
		}
		buf.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().WriteTypeInfo(buf, typeInfo); err != nil {
			return err
		}
	}
	return s.WriteData(ctx, value)
}

func (s *extensionSerializerAdapter) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Delegate to user's serializer
	result, err := s.userSerial.Read(ctx.Buffer())
	if err != nil {
		return err
	}
	// Set the result into the value
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s *extensionSerializerAdapter) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	switch refMode {
	case RefModeTracking:
		refID, refErr := ctx.RefResolver().TryPreserveRefId(buf)
		if refErr != nil {
			return refErr
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	case RefModeNullOnly:
		flag := buf.ReadInt8(ctxErr)
		if flag == NullFlag {
			return ctx.CheckError()
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *extensionSerializerAdapter) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
