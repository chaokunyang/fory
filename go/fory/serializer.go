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
	"unsafe"
)

// TypedSerializer is the core interface for all serialization (Go 1.23+ generics).
// Each serializer handles its own ref/type info writing internally.
type TypedSerializer[T any] interface {
	// Write is the entry point for serialization.
	// It handles: 1) reference tracking, 2) type info, 3) data
	Write(ctx *WriteContext, value T, writeRefInfo, writeTypeInfo bool) error

	// WriteData serializes only the data payload (no ref/type info).
	WriteData(ctx *WriteContext, value T) error

	// Read is the entry point for deserialization.
	// It handles: 1) reference tracking, 2) type info, 3) data
	Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (T, error)

	// ReadData deserializes only the data payload (no ref/type info).
	ReadData(ctx *ReadContext) (T, error)

	// TypeId returns the Fory protocol type ID
	TypeId() TypeId

	// NeedToWriteRef returns true if this type needs reference tracking.
	NeedToWriteRef() bool
}

// AnySerializer is the non-generic interface for runtime dispatch.
type AnySerializer interface {
	WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error
	WriteDataAny(ctx *WriteContext, value any) error
	ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error)
	ReadDataAny(ctx *ReadContext) (any, error)
	TypeId() TypeId
	NeedToWriteRef() bool
}

// Serializer is the reflection-based interface for runtime dispatch.
// Used by existing code that works with reflect.Value.
type Serializer interface {
	TypeId() TypeId
	WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error
	ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error
	NeedWriteRef() bool
}

// TypeMeta holds type information for chunk-based encoding.
type TypeMeta struct {
	TypeId     TypeId
	Serializer AnySerializer
	KeyMeta    *TypeMeta // For map keys
	ValueMeta  *TypeMeta // For map values / slice elements
}

// FastType for switch-based fast path (avoids interface virtual method cost)
type FastType uint8

const (
	FastTypeOther FastType = iota
	FastTypeBool
	FastTypeInt8
	FastTypeInt16
	FastTypeInt32
	FastTypeInt64
	FastTypeFloat32
	FastTypeFloat64
	FastTypeString
)

// GetFastType returns the FastType for a reflect.Type
func GetFastType(t reflect.Type) FastType {
	switch t.Kind() {
	case reflect.Bool:
		return FastTypeBool
	case reflect.Int8:
		return FastTypeInt8
	case reflect.Int16:
		return FastTypeInt16
	case reflect.Int32:
		return FastTypeInt32
	case reflect.Int64:
		return FastTypeInt64
	case reflect.Float32:
		return FastTypeFloat32
	case reflect.Float64:
		return FastTypeFloat64
	case reflect.String:
		return FastTypeString
	default:
		return FastTypeOther
	}
}

// GetFastTypeAndId returns both FastType and TypeId for a reflect.Type
func GetFastTypeAndId(t reflect.Type) (FastType, TypeId) {
	switch t.Kind() {
	case reflect.Bool:
		return FastTypeBool, BOOL
	case reflect.Int8:
		return FastTypeInt8, INT8
	case reflect.Int16:
		return FastTypeInt16, INT16
	case reflect.Int32:
		return FastTypeInt32, INT32
	case reflect.Int64:
		return FastTypeInt64, INT64
	case reflect.Float32:
		return FastTypeFloat32, FLOAT
	case reflect.Float64:
		return FastTypeFloat64, DOUBLE
	case reflect.String:
		return FastTypeString, STRING
	default:
		return FastTypeOther, 0
	}
}

// IsPrimitiveTypeId checks if a type ID is a primitive type
func IsPrimitiveTypeId(typeId TypeId) bool {
	switch typeId {
	case BOOL, INT8, INT16, INT32, INT64, FLOAT, DOUBLE, STRING:
		return true
	default:
		return false
	}
}

// WriteFast writes a value using fast path based on FastType
func WriteFast(buf *ByteBuffer, ptr unsafe.Pointer, ft FastType) {
	switch ft {
	case FastTypeBool:
		buf.WriteBool(*(*bool)(ptr))
	case FastTypeInt8:
		buf.WriteByte_(*(*byte)(ptr))
	case FastTypeInt16:
		buf.WriteInt16(*(*int16)(ptr))
	case FastTypeInt32:
		buf.WriteVarint32(*(*int32)(ptr))
	case FastTypeInt64:
		buf.WriteVarint64(*(*int64)(ptr))
	case FastTypeFloat32:
		buf.WriteFloat32(*(*float32)(ptr))
	case FastTypeFloat64:
		buf.WriteFloat64(*(*float64)(ptr))
	case FastTypeString:
		writeString(buf, *(*string)(ptr))
	}
}

// ReadFast reads a value using fast path based on FastType
func ReadFast(buf *ByteBuffer, ptr unsafe.Pointer, ft FastType) {
	switch ft {
	case FastTypeBool:
		*(*bool)(ptr) = buf.ReadBool()
	case FastTypeInt8:
		*(*int8)(ptr) = int8(buf.ReadByte_())
	case FastTypeInt16:
		*(*int16)(ptr) = buf.ReadInt16()
	case FastTypeInt32:
		*(*int32)(ptr) = buf.ReadVarint32()
	case FastTypeInt64:
		*(*int64)(ptr) = buf.ReadVarint64()
	case FastTypeFloat32:
		*(*float32)(ptr) = buf.ReadFloat32()
	case FastTypeFloat64:
		*(*float64)(ptr) = buf.ReadFloat64()
	case FastTypeString:
		*(*string)(ptr) = readString(buf)
	}
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

// Register adds a typed serializer to the global registry
func Register[T any](serializer TypedSerializer[T]) {
	t := reflect.TypeFor[T]()
	globalGenericRegistry.mu.Lock()
	globalGenericRegistry.serializers[t] = serializer
	globalGenericRegistry.mu.Unlock()
}

// RegisterAny adds a non-generic serializer to the global registry
func RegisterAny(t reflect.Type, serializer AnySerializer) {
	globalGenericRegistry.mu.Lock()
	globalGenericRegistry.serializers[t] = serializer
	globalGenericRegistry.mu.Unlock()
}

// GetSerializer retrieves serializer with zero allocation (compile-time typed)
func GetSerializer[T any](r *GenericRegistry) TypedSerializer[T] {
	t := reflect.TypeFor[T]()
	r.mu.RLock()
	s, ok := r.serializers[t]
	r.mu.RUnlock()

	if !ok {
		panic("fory: no serializer for type " + t.String())
	}
	return s.(TypedSerializer[T])
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

// ============================================================================
// Primitive Serializers - implement both TypedSerializer and AnySerializer
// ============================================================================

// boolSerializer handles bool type
type boolSerializer struct{}

var globalBoolSerializer = boolSerializer{}

func (s boolSerializer) TypeId() TypeId       { return BOOL }
func (s boolSerializer) NeedToWriteRef() bool { return false }
func (s boolSerializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s boolSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteBool(value.Bool())
	return nil
}

func (s boolSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetBool(buf.ReadBool())
	return nil
}

// TypedSerializer interface methods
func (s boolSerializer) Write(ctx *WriteContext, value bool, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(BOOL)
	}
	return s.WriteData(ctx, value)
}

func (s boolSerializer) WriteData(ctx *WriteContext, value bool) error {
	ctx.buffer.WriteBool(value)
	return nil
}

func (s boolSerializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (bool, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s boolSerializer) ReadData(ctx *ReadContext) (bool, error) {
	return ctx.buffer.ReadBool(), nil
}

// AnySerializer interface methods
func (s boolSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(bool), writeRefInfo, writeTypeInfo)
}

func (s boolSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(bool))
}

func (s boolSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s boolSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// int8Serializer handles int8 type
type int8Serializer struct{}

var globalInt8Serializer = int8Serializer{}

func (s int8Serializer) TypeId() TypeId       { return INT8 }
func (s int8Serializer) NeedToWriteRef() bool { return false }
func (s int8Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s int8Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteByte_(byte(value.Int()))
	return nil
}

func (s int8Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(int8(buf.ReadByte_())))
	return nil
}

// TypedSerializer interface methods
func (s int8Serializer) Write(ctx *WriteContext, value int8, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(INT8)
	}
	return s.WriteData(ctx, value)
}

func (s int8Serializer) WriteData(ctx *WriteContext, value int8) error {
	ctx.buffer.WriteInt8(value)
	return nil
}

func (s int8Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (int8, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s int8Serializer) ReadData(ctx *ReadContext) (int8, error) {
	return ctx.buffer.ReadInt8(), nil
}

// AnySerializer interface methods
func (s int8Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(int8), writeRefInfo, writeTypeInfo)
}

func (s int8Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(int8))
}

func (s int8Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s int8Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// byteSerializer handles byte/uint8 type
type byteSerializer struct{}

func (s byteSerializer) TypeId() TypeId     { return UINT8 }
func (s byteSerializer) NeedWriteRef() bool { return false }

func (s byteSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteByte_(byte(value.Uint()))
	return nil
}

func (s byteSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetUint(uint64(buf.ReadByte_()))
	return nil
}

// int16Serializer handles int16 type
type int16Serializer struct{}

var globalInt16Serializer = int16Serializer{}

func (s int16Serializer) TypeId() TypeId       { return INT16 }
func (s int16Serializer) NeedToWriteRef() bool { return false }
func (s int16Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s int16Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteInt16(int16(value.Int()))
	return nil
}

func (s int16Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(buf.ReadInt16()))
	return nil
}

// TypedSerializer interface methods
func (s int16Serializer) Write(ctx *WriteContext, value int16, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(INT16)
	}
	return s.WriteData(ctx, value)
}

func (s int16Serializer) WriteData(ctx *WriteContext, value int16) error {
	ctx.buffer.WriteInt16(value)
	return nil
}

func (s int16Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (int16, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s int16Serializer) ReadData(ctx *ReadContext) (int16, error) {
	return ctx.buffer.ReadInt16(), nil
}

// AnySerializer interface methods
func (s int16Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(int16), writeRefInfo, writeTypeInfo)
}

func (s int16Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(int16))
}

func (s int16Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s int16Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// int32Serializer handles int32 type
type int32Serializer struct{}

var globalInt32Serializer = int32Serializer{}

func (s int32Serializer) TypeId() TypeId       { return INT32 }
func (s int32Serializer) NeedToWriteRef() bool { return false }
func (s int32Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s int32Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteVarint32(int32(value.Int()))
	return nil
}

func (s int32Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(buf.ReadVarint32()))
	return nil
}

// TypedSerializer interface methods
func (s int32Serializer) Write(ctx *WriteContext, value int32, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(INT32)
	}
	return s.WriteData(ctx, value)
}

func (s int32Serializer) WriteData(ctx *WriteContext, value int32) error {
	ctx.buffer.WriteInt32(value)
	return nil
}

func (s int32Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (int32, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s int32Serializer) ReadData(ctx *ReadContext) (int32, error) {
	return ctx.buffer.ReadInt32(), nil
}

// AnySerializer interface methods
func (s int32Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(int32), writeRefInfo, writeTypeInfo)
}

func (s int32Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(int32))
}

func (s int32Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s int32Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// int64Serializer handles int64 type
type int64Serializer struct{}

var globalInt64Serializer = int64Serializer{}

func (s int64Serializer) TypeId() TypeId       { return INT64 }
func (s int64Serializer) NeedToWriteRef() bool { return false }
func (s int64Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s int64Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteVarint64(value.Int())
	return nil
}

func (s int64Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(buf.ReadVarint64())
	return nil
}

// TypedSerializer interface methods
func (s int64Serializer) Write(ctx *WriteContext, value int64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(INT64)
	}
	return s.WriteData(ctx, value)
}

func (s int64Serializer) WriteData(ctx *WriteContext, value int64) error {
	ctx.buffer.WriteInt64(value)
	return nil
}

func (s int64Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (int64, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s int64Serializer) ReadData(ctx *ReadContext) (int64, error) {
	return ctx.buffer.ReadInt64(), nil
}

// AnySerializer interface methods
func (s int64Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(int64), writeRefInfo, writeTypeInfo)
}

func (s int64Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(int64))
}

func (s int64Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s int64Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// intSerializer handles int type
type intSerializer struct{}

func (s intSerializer) TypeId() TypeId     { return -INT64 }
func (s intSerializer) NeedWriteRef() bool { return false }

func (s intSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteInt64(value.Int())
	return nil
}

func (s intSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	v := buf.ReadInt64()
	if v > MaxInt || v < MinInt {
		return fmt.Errorf("int64 %d exceed int range", v)
	}
	value.SetInt(v)
	return nil
}

// float32Serializer handles float32 type
type float32Serializer struct{}

var globalFloat32Serializer = float32Serializer{}

func (s float32Serializer) TypeId() TypeId       { return FLOAT }
func (s float32Serializer) NeedToWriteRef() bool { return false }
func (s float32Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s float32Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteFloat32(float32(value.Float()))
	return nil
}

func (s float32Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetFloat(float64(buf.ReadFloat32()))
	return nil
}

// TypedSerializer interface methods
func (s float32Serializer) Write(ctx *WriteContext, value float32, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(FLOAT)
	}
	return s.WriteData(ctx, value)
}

func (s float32Serializer) WriteData(ctx *WriteContext, value float32) error {
	ctx.buffer.WriteFloat32(value)
	return nil
}

func (s float32Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (float32, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s float32Serializer) ReadData(ctx *ReadContext) (float32, error) {
	return ctx.buffer.ReadFloat32(), nil
}

// AnySerializer interface methods
func (s float32Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(float32), writeRefInfo, writeTypeInfo)
}

func (s float32Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(float32))
}

func (s float32Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s float32Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// float64Serializer handles float64 type
type float64Serializer struct{}

var globalFloat64Serializer = float64Serializer{}

func (s float64Serializer) TypeId() TypeId       { return DOUBLE }
func (s float64Serializer) NeedToWriteRef() bool { return false }
func (s float64Serializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s float64Serializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteFloat64(value.Float())
	return nil
}

func (s float64Serializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetFloat(buf.ReadFloat64())
	return nil
}

// TypedSerializer interface methods
func (s float64Serializer) Write(ctx *WriteContext, value float64, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(DOUBLE)
	}
	return s.WriteData(ctx, value)
}

func (s float64Serializer) WriteData(ctx *WriteContext, value float64) error {
	ctx.buffer.WriteFloat64(value)
	return nil
}

func (s float64Serializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (float64, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s float64Serializer) ReadData(ctx *ReadContext) (float64, error) {
	return ctx.buffer.ReadFloat64(), nil
}

// AnySerializer interface methods
func (s float64Serializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(float64), writeRefInfo, writeTypeInfo)
}

func (s float64Serializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(float64))
}

func (s float64Serializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s float64Serializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// stringSerializer handles string type
type stringSerializer struct{}

var globalStringSerializer = stringSerializer{}

func (s stringSerializer) TypeId() TypeId       { return STRING }
func (s stringSerializer) NeedToWriteRef() bool { return false }
func (s stringSerializer) NeedWriteRef() bool   { return false }

// Serializer interface methods (reflection-based)
func (s stringSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	return writeString(buf, value.String())
}

func (s stringSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.SetString(readString(buf))
	return nil
}

// TypedSerializer interface methods
func (s stringSerializer) Write(ctx *WriteContext, value string, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(STRING)
	}
	return s.WriteData(ctx, value)
}

func (s stringSerializer) WriteData(ctx *WriteContext, value string) error {
	ctx.buffer.WriteVarUint32(uint32(len(value)))
	if len(value) > 0 {
		ctx.buffer.WriteBinary(unsafe.Slice(unsafe.StringData(value), len(value)))
	}
	return nil
}

func (s stringSerializer) Read(ctx *ReadContext, readRefInfo, readTypeInfo bool) (string, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadData(ctx)
}

func (s stringSerializer) ReadData(ctx *ReadContext) (string, error) {
	length := ctx.buffer.ReadVarUint32()
	if length == 0 {
		return "", nil
	}
	data := ctx.buffer.ReadBinary(int(length))
	return string(data), nil
}

// AnySerializer interface methods
func (s stringSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	return s.Write(ctx, value.(string), writeRefInfo, writeTypeInfo)
}

func (s stringSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	return s.WriteData(ctx, value.(string))
}

func (s stringSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	return s.Read(ctx, readRefInfo, readTypeInfo)
}

func (s stringSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return s.ReadData(ctx)
}

// ptrToStringSerializer serializes a pointer to string
type ptrToStringSerializer struct{}

func (s ptrToStringSerializer) TypeId() TypeId     { return -STRING }
func (s ptrToStringSerializer) NeedWriteRef() bool { return true }

func (s ptrToStringSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	if value.Kind() != reflect.Ptr || value.IsNil() {
		return fmt.Errorf("expected non-nil string pointer, got %v", value.Type())
	}
	return writeString(buf, value.Elem().String())
}

func (s ptrToStringSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	str := readString(buf)
	value.Set(reflect.ValueOf(&str))
	return nil
}

// Date represents an imprecise date
type Date struct {
	Year  int
	Month time.Month
	Day   int
}

type dateSerializer struct{}

func (s dateSerializer) TypeId() TypeId     { return LOCAL_DATE }
func (s dateSerializer) NeedWriteRef() bool { return true }

func (s dateSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	date := value.Interface().(Date)
	diff := time.Date(date.Year, date.Month, date.Day, 0, 0, 0, 0, time.Local).Sub(
		time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local))
	buf.WriteInt32(int32(diff.Hours() / 24))
	return nil
}

func (s dateSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	diff := time.Duration(buf.ReadInt32()) * 24 * time.Hour
	date := time.Date(1970, 1, 1, 0, 0, 0, 0, time.Local).Add(diff)
	value.Set(reflect.ValueOf(Date{date.Year(), date.Month(), date.Day()}))
	return nil
}

type timeSerializer struct{}

func (s timeSerializer) TypeId() TypeId     { return TIMESTAMP }
func (s timeSerializer) NeedWriteRef() bool { return true }

func (s timeSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	buf.WriteInt64(GetUnixMicro(value.Interface().(time.Time)))
	return nil
}

func (s timeSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	value.Set(reflect.ValueOf(CreateTimeFromUnixMicro(buf.ReadInt64())))
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

func (s *ptrToValueSerializer) NeedWriteRef() bool { return true }

func (s *ptrToValueSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	return s.valueSerializer.WriteReflect(f, buf, value.Elem())
}

func (s *ptrToValueSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	newValue := reflect.New(type_.Elem())
	value.Set(newValue)
	return s.valueSerializer.ReadReflect(f, buf, type_.Elem(), newValue.Elem())
}

// Array serializers

type arraySerializer struct{}

func (s arraySerializer) TypeId() TypeId     { return -LIST }
func (s arraySerializer) NeedWriteRef() bool { return true }

func (s arraySerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	length := value.Len()
	if err := f.writeLength(buf, length); err != nil {
		return err
	}
	for i := 0; i < length; i++ {
		if err := f.WriteReferencable(buf, value.Index(i)); err != nil {
			return err
		}
	}
	return nil
}

func (s arraySerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	length := f.readLength(buf)
	for i := 0; i < length; i++ {
		if err := f.ReadReferencable(buf, value.Index(i)); err != nil {
			return err
		}
	}
	return nil
}

// arrayConcreteValueSerializer serialize an array/*array
type arrayConcreteValueSerializer struct {
	type_          reflect.Type
	elemSerializer Serializer
	referencable   bool
}

func (s *arrayConcreteValueSerializer) TypeId() TypeId    { return -LIST }
func (s arrayConcreteValueSerializer) NeedWriteRef() bool { return true }

func (s *arrayConcreteValueSerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	length := value.Len()
	if err := f.writeLength(buf, length); err != nil {
		return err
	}
	for i := 0; i < length; i++ {
		if err := writeBySerializer(f, buf, value.Index(i), s.elemSerializer, s.referencable); err != nil {
			return err
		}
	}
	return nil
}

func (s *arrayConcreteValueSerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	length := buf.ReadLength()
	for i := 0; i < length; i++ {
		if err := readBySerializer(f, buf, value.Index(i), s.elemSerializer, s.referencable); err != nil {
			return err
		}
	}
	return nil
}

type byteArraySerializer struct{}

func (s byteArraySerializer) TypeId() TypeId     { return -BINARY }
func (s byteArraySerializer) NeedWriteRef() bool { return false }

func (s byteArraySerializer) WriteReflect(f *Fory, buf *ByteBuffer, value reflect.Value) error {
	length := value.Len()
	if err := f.writeLength(buf, length); err != nil {
		return err
	}
	if value.CanAddr() {
		buf.WriteBinary(value.Slice(0, length).Bytes())
		return nil
	}
	buf.grow(length)
	reflect.Copy(reflect.ValueOf(buf.data[buf.writerIndex:]), value)
	buf.writerIndex += length
	return nil
}

func (s byteArraySerializer) ReadReflect(f *Fory, buf *ByteBuffer, type_ reflect.Type, value reflect.Value) error {
	length := buf.ReadInt32()
	if int(length) != value.Len() {
		return fmt.Errorf("%s has len %d, but fory has len elements %d", value.Type(), value.Len(), length)
	}
	_, err := buf.Read(value.Slice(0, int(length)).Bytes())
	return err
}

// Helper functions

func writeBySerializer(f *Fory, buf *ByteBuffer, value reflect.Value, serializer Serializer, referencable bool) error {
	if referencable {
		return f.writeReferencableBySerializer(buf, value, serializer)
	}
	return f.writeNonReferencableBySerializer(buf, value, serializer)
}

func readBySerializer(f *Fory, buf *ByteBuffer, value reflect.Value, serializer Serializer, referencable bool) error {
	if referencable {
		return f.readReferencableBySerializer(buf, value, serializer)
	}
	return f.readData(buf, value, serializer)
}

// Marshaller interface for custom serialization
type Marshaller interface {
	ExtId() int16
	MarshalFory(f *Fory, buf *ByteBuffer) error
	UnmarshalFory(f *Fory, buf *ByteBuffer) error
}

// init registers all primitive serializers
func init() {
	Register(globalBoolSerializer)
	Register(globalInt8Serializer)
	Register(globalInt16Serializer)
	Register(globalInt32Serializer)
	Register(globalInt64Serializer)
	Register(globalFloat32Serializer)
	Register(globalFloat64Serializer)
	Register(globalStringSerializer)
}
