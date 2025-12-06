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
	"sync"
)

// Serializer is the unified interface for all serialization.
// It provides both any-based API (fast for primitives) and reflect.Value-based API (for containers).
type Serializer interface {
	// Write serializes data using any type (fast path for primitives).
	// Does NOT write ref/type info - caller handles that.
	Write(ctx *WriteContext, value any) error

	// Read deserializes data and returns as any.
	// Does NOT read ref/type info - caller handles that.
	Read(ctx *ReadContext) (any, error)

	// WriteReflect serializes using reflect.Value (efficient when value is already reflect.Value).
	// Does NOT write ref/type info - caller handles that.
	WriteReflect(ctx *WriteContext, value reflect.Value) error

	// ReadReflect deserializes directly into the provided reflect.Value.
	// Does NOT read ref/type info - caller handles that.
	// For non-trivial types (slices, maps), implementations should reuse existing capacity when possible.
	ReadReflect(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error

	// TypeId returns the Fory protocol type ID
	TypeId() TypeId

	// NeedToWriteRef returns true if this type needs reference tracking
	NeedToWriteRef() bool
}

// ============================================================================
// GenericRegistry - stores typed serializers with fast lookup
// ============================================================================

// GenericRegistry stores typed serializers with fast lookup.
type GenericRegistry struct {
	mu          sync.RWMutex
	serializers map[reflect.Type]any
	typeInfos   map[reflect.Type]*TypeInfo
}

// globalGenericRegistry is the default global registry
var globalGenericRegistry = &GenericRegistry{
	serializers: make(map[reflect.Type]any),
	typeInfos:   make(map[reflect.Type]*TypeInfo),
}

// GetGlobalRegistry returns the global registry
func GetGlobalRegistry() *GenericRegistry {
	return globalGenericRegistry
}

// RegisterSerializer adds a serializer to the global registry
func RegisterSerializer(t reflect.Type, serializer Serializer) {
	globalGenericRegistry.mu.Lock()
	globalGenericRegistry.serializers[t] = serializer
	globalGenericRegistry.mu.Unlock()
}

// GetByReflectType retrieves serializer by reflect.Type
func (r *GenericRegistry) GetByReflectType(t reflect.Type) (Serializer, error) {
	r.mu.RLock()
	s, ok := r.serializers[t]
	r.mu.RUnlock()

	if !ok {
		return nil, fmt.Errorf("no serializer for type %v", t)
	}
	return s.(Serializer), nil
}

// GetByTypeId retrieves serializer by TypeId
func (r *GenericRegistry) GetByTypeId(typeId TypeId) (Serializer, error) {
	// Fast path for primitive types
	switch typeId {
	case BOOL:
		return globalBoolSerializer, nil
	case INT8:
		return globalInt8Serializer, nil
	case INT16:
		return globalInt16Serializer, nil
	case INT32:
		return globalInt32Serializer, nil
	case INT64:
		return globalInt64Serializer, nil
	case FLOAT:
		return globalFloat32Serializer, nil
	case DOUBLE:
		return globalFloat64Serializer, nil
	case STRING:
		return globalStringSerializer, nil
	default:
		return nil, fmt.Errorf("no serializer for type ID %d", typeId)
	}
}

// Helper functions for serializer dispatch

func writeBySerializer(ctx *WriteContext, value reflect.Value, serializer Serializer, referencable bool) error {
	buf := ctx.Buffer()
	if referencable {
		if isNull(value) {
			buf.WriteInt8(NullFlag)
			return nil
		}
		// Check for reference
		refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	// If no serializer provided, look it up from typeResolver and write type info
	if serializer == nil {
		typeInfo, err := ctx.TypeResolver().getTypeInfo(value, true)
		if err != nil {
			return err
		}
		// Write type info for dynamic types (so reader can look up the serializer)
		if err := ctx.TypeResolver().writeTypeInfo(buf, typeInfo); err != nil {
			return err
		}
		serializer = typeInfo.Serializer
	}
	return serializer.WriteReflect(ctx, value)
}

func readBySerializer(ctx *ReadContext, value reflect.Value, serializer Serializer, referencable bool) error {
	buf := ctx.Buffer()
	if referencable {
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
	}
	return serializer.ReadReflect(ctx, value.Type(), value)
}
