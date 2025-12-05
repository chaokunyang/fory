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
	"time"
)

// AnySerializer is the non-generic interface for runtime dispatch using the new context-based API.
type AnySerializer interface {
	WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error
	WriteDataAny(ctx *WriteContext, value any) error
	ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error)
	ReadDataAny(ctx *ReadContext) (any, error)
	// ReadToAny deserializes directly into the provided target, avoiding allocation.
	ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error
	// ReadDataToAny deserializes only the data payload directly into target.
	ReadDataToAny(ctx *ReadContext, target any) error
	TypeId() TypeId
	NeedToWriteRef() bool
}

// Serializer is the reflection-based interface for complex types (maps, slices, structs).
// Uses context-based API instead of *Fory.
// Serializer interface for reflection-based serialization of complex types (maps, slices, structs).
// Uses WriteValue/ReadValue to avoid conflicts with TypedSerializer's Write/Read methods.
type Serializer interface {
	WriteValue(ctx *WriteContext, value reflect.Value) error
	// ReadValue deserializes directly into the provided value.
	// For non-trivial types (slices, maps), implementations should reuse existing capacity when possible.
	ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error
	TypeId() TypeId
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

// RegisterAny adds a non-generic serializer to the global registry
func RegisterAny(t reflect.Type, serializer AnySerializer) {
	globalGenericRegistry.mu.Lock()
	globalGenericRegistry.serializers[t] = serializer
	globalGenericRegistry.mu.Unlock()
}

// GetByReflectType retrieves serializer by reflect.Type
func (r *GenericRegistry) GetByReflectType(t reflect.Type) (AnySerializer, error) {
	r.mu.RLock()
	s, ok := r.serializers[t]
	r.mu.RUnlock()

	if !ok {
		return nil, fmt.Errorf("no serializer for type %v", t)
	}
	return s.(AnySerializer), nil
}

// GetByTypeId retrieves serializer by TypeId
func (r *GenericRegistry) GetByTypeId(typeId TypeId) (AnySerializer, error) {
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

// Date represents an imprecise date
type Date struct {
	Year  int
	Month time.Month
	Day   int
}

type dateSerializer struct{}

func (s dateSerializer) TypeId() TypeId       { return LOCAL_DATE }
func (s dateSerializer) NeedToWriteRef() bool { return true }

// AnySerializer interface methods
func (s dateSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(LOCAL_DATE)
	}
	return s.WriteDataAny(ctx, value)
}

func (s dateSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	date := value.(Date)
	diff := time.Date(date.Year, date.Month, date.Day, 0, 0, 0, 0, time.Local).Sub(
		time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local))
	ctx.buffer.WriteInt32(int32(diff.Hours() / 24))
	return nil
}

func (s dateSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataAny(ctx)
}

func (s dateSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	diff := time.Duration(ctx.buffer.ReadInt32()) * 24 * time.Hour
	date := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local).Add(diff)
	return Date{date.Year(), date.Month(), date.Day()}, nil
}

func (s dateSerializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataToAny(ctx, target)
}

func (s dateSerializer) ReadDataToAny(ctx *ReadContext, target any) error {
	diff := time.Duration(ctx.buffer.ReadInt32()) * 24 * time.Hour
	date := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local).Add(diff)
	*target.(*Date) = Date{date.Year(), date.Month(), date.Day()}
	return nil
}

// Serializer interface methods
func (s dateSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	date := value.Interface().(Date)
	diff := time.Date(date.Year, date.Month, date.Day, 0, 0, 0, 0, time.Local).Sub(
		time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local))
	ctx.buffer.WriteInt32(int32(diff.Hours() / 24))
	return nil
}

func (s dateSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	diff := time.Duration(ctx.buffer.ReadInt32()) * 24 * time.Hour
	date := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local).Add(diff)
	value.Set(reflect.ValueOf(Date{date.Year(), date.Month(), date.Day()}))
	return nil
}

type timeSerializer struct{}

func (s timeSerializer) TypeId() TypeId       { return TIMESTAMP }
func (s timeSerializer) NeedToWriteRef() bool { return true }

// AnySerializer interface methods
func (s timeSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(TIMESTAMP)
	}
	return s.WriteDataAny(ctx, value)
}

func (s timeSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	ctx.buffer.WriteInt64(GetUnixMicro(value.(time.Time)))
	return nil
}

func (s timeSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataAny(ctx)
}

func (s timeSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return CreateTimeFromUnixMicro(ctx.buffer.ReadInt64()), nil
}

func (s timeSerializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataToAny(ctx, target)
}

func (s timeSerializer) ReadDataToAny(ctx *ReadContext, target any) error {
	*target.(*time.Time) = CreateTimeFromUnixMicro(ctx.buffer.ReadInt64())
	return nil
}

// Serializer interface methods
func (s timeSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteInt64(GetUnixMicro(value.Interface().(time.Time)))
	return nil
}

func (s timeSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.Set(reflect.ValueOf(CreateTimeFromUnixMicro(ctx.buffer.ReadInt64())))
	return nil
}

// ptrToValueSerializer serializes a pointer to a concrete value
type ptrToValueSerializer struct {
	valueSerializer Serializer
}

func (s *ptrToValueSerializer) TypeId() TypeId {
	if id := s.valueSerializer.TypeId(); id < 0 {
		return id
	}
	return -s.valueSerializer.TypeId()
}

func (s *ptrToValueSerializer) NeedToWriteRef() bool { return true }

// Serializer interface methods
func (s *ptrToValueSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	elemValue := value.Elem()

	// In compatible mode, write typeInfo for struct types so TypeDefs are collected
	if ctx.Compatible() && s.valueSerializer.TypeId() == NAMED_STRUCT {
		typeInfo, err := ctx.TypeResolver().getTypeInfo(elemValue, true)
		if err != nil {
			return err
		}
		if err := ctx.TypeResolver().writeTypeInfo(ctx.Buffer(), typeInfo); err != nil {
			return err
		}
	}

	return s.valueSerializer.WriteValue(ctx, elemValue)
}

func (s *ptrToValueSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	// Allocate new value and read into it
	newVal := reflect.New(type_.Elem())

	// In compatible mode, read typeInfo for struct types
	if ctx.Compatible() && s.valueSerializer.TypeId() == NAMED_STRUCT {
		// Read typeInfo (typeId + metaIndex) to consume the bytes written by WriteValue
		_, err := ctx.TypeResolver().readTypeInfo(ctx.Buffer(), newVal.Elem())
		if err != nil {
			return err
		}
	}

	if err := s.valueSerializer.ReadValue(ctx, type_.Elem(), newVal.Elem()); err != nil {
		return err
	}
	value.Set(newVal)
	return nil
}

// Marshaller interface for custom serialization
type Marshaller interface {
	ExtId() int16
	MarshalFory(f *Fory, buf *ByteBuffer) error
	UnmarshalFory(f *Fory, buf *ByteBuffer) error
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
	return serializer.WriteValue(ctx, value)
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
	return serializer.ReadValue(ctx, value.Type(), value)
}
