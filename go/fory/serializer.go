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

// Helper functions for serializer dispatch
