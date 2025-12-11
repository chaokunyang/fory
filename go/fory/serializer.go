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
	// Write is the entry point for serialization (mirrors Rust's fory_write).
	//
	// This method orchestrates the complete serialization process, handling reference tracking,
	// type information, and delegating to Write for the actual data serialization.
	//
	// Parameters:
	//
	// * writeRef - When true, WRITES reference flag. When false, SKIPS writing ref flag.
	// * writeType - When true, WRITES type information. When false, SKIPS writing type info.
	//
	Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error

	// WriteData serializes using reflect.Value.
	// Does NOT write ref/type info - caller handles that.
	WriteData(ctx *WriteContext, value reflect.Value) error

	// Read is the entry point for deserialization.
	//
	// This method orchestrates the complete deserialization process, handling reference tracking,
	// type information validation, and delegating to Read for the actual data deserialization.
	//
	// Parameters:
	//
	// * readRef - When true, READS reference flag from buffer. When false, SKIPS reading ref flag.
	// * readType - When true, READS type information from buffer. When false, SKIPS reading type info.
	//
	Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error

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
	//
	// * readRef - When true, READS reference flag from buffer. When false, SKIPS reading ref flag.
	// * typeInfo - Type information that has already been read ahead. DO NOT read type info again from buffer.
	//
	// Important:
	//
	// DO NOT read type info from the buffer in this method. The typeInfo parameter
	// contains the already-read type metadata. Reading it again will cause buffer position errors.
	//
	ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error

	// TypeId returns the Fory protocol type ID
	TypeId() TypeId

	// NeedToWriteRef returns true if this type needs reference tracking
	NeedToWriteRef() bool
}

// ExtensionSerializer is a simplified interface for user-implemented extension serializers.
// Users implement this interface to provide custom serialization logic for types
// registered via RegisterExtensionType.
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
//	    buf.WriteVarInt32(myExt.Id)
//	    return nil
//	}
//
//	func (s *MyExtSerializer) Read(buf *ByteBuffer) (interface{}, error) {
//	    id := buf.ReadVarInt32()
//	    return MyExt{Id: id}, nil
//	}
//
//	// Register with custom serializer
//	f.RegisterExtensionType(MyExt{}, "my_ext", &MyExtSerializer{})
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

func (s *extensionSerializerAdapter) TypeId() TypeId { return NAMED_STRUCT }

func (s *extensionSerializerAdapter) NeedToWriteRef() bool { return true }

func (s *extensionSerializerAdapter) GetType() reflect.Type { return s.type_ }

func (s *extensionSerializerAdapter) WriteData(ctx *WriteContext, value reflect.Value) error {
	// Delegate to user's serializer
	return s.userSerial.Write(ctx.Buffer(), value.Interface())
}

func (s *extensionSerializerAdapter) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if writeRef {
		refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
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

func (s *extensionSerializerAdapter) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
		refID, err := ctx.RefResolver().TryPreserveRefId(buf)
		if err != nil {
			return err
		}
		if int8(refID) < NotNullValueFlag {
			obj := ctx.RefResolver().GetReadObject(refID)
			if obj.IsValid() {
				value.Set(obj)
			}
			return nil
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s *extensionSerializerAdapter) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// Helper functions for serializer dispatch
