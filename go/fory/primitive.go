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
	"unsafe"
)

// ============================================================================
// FastType for switch-based fast path (avoids interface virtual method cost)
// ============================================================================

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
// Primitive Serializers - implement both TypedSerializer and AnySerializer
// ============================================================================

// boolSerializer handles bool type
type boolSerializer struct{}

var globalBoolSerializer = boolSerializer{}

func (s boolSerializer) TypeId() TypeId       { return BOOL }
func (s boolSerializer) NeedToWriteRef() bool { return false }

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

func (s boolSerializer) ReadTo(ctx *ReadContext, target *bool, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s boolSerializer) ReadDataTo(ctx *ReadContext, target *bool) error {
	*target = ctx.buffer.ReadBool()
	return nil
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

func (s boolSerializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*bool), readRefInfo, readTypeInfo)
}

func (s boolSerializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*bool))
}

// Serializer interface methods (for reflection-based serialization)
func (s boolSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	return s.WriteData(ctx, value.Bool())
}

func (s boolSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	v, _ := s.ReadData(ctx)
	value.SetBool(v)
	return nil
}

// int8Serializer handles int8 type
type int8Serializer struct{}

var globalInt8Serializer = int8Serializer{}

func (s int8Serializer) TypeId() TypeId       { return INT8 }
func (s int8Serializer) NeedToWriteRef() bool { return false }

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

func (s int8Serializer) ReadTo(ctx *ReadContext, target *int8, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s int8Serializer) ReadDataTo(ctx *ReadContext, target *int8) error {
	*target = ctx.buffer.ReadInt8()
	return nil
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

func (s int8Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*int8), readRefInfo, readTypeInfo)
}

func (s int8Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*int8))
}

// Serializer interface methods
func (s int8Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteInt8(int8(value.Int()))
	return nil
}

func (s int8Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(ctx.buffer.ReadInt8()))
	return nil
}

// byteSerializer handles byte/uint8 type
type byteSerializer struct{}

func (s byteSerializer) TypeId() TypeId       { return UINT8 }
func (s byteSerializer) NeedToWriteRef() bool { return false }

// AnySerializer interface methods
func (s byteSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(UINT8)
	}
	return s.WriteDataAny(ctx, value)
}

func (s byteSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	ctx.buffer.WriteByte_(value.(byte))
	return nil
}

func (s byteSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataAny(ctx)
}

func (s byteSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	return ctx.buffer.ReadByte_(), nil
}

func (s byteSerializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataToAny(ctx, target)
}

func (s byteSerializer) ReadDataToAny(ctx *ReadContext, target any) error {
	*target.(*byte) = ctx.buffer.ReadByte_()
	return nil
}

// Serializer interface methods
func (s byteSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteByte_(byte(value.Uint()))
	return nil
}

func (s byteSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetUint(uint64(ctx.buffer.ReadByte_()))
	return nil
}

// int16Serializer handles int16 type
type int16Serializer struct{}

var globalInt16Serializer = int16Serializer{}

func (s int16Serializer) TypeId() TypeId       { return INT16 }
func (s int16Serializer) NeedToWriteRef() bool { return false }

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

func (s int16Serializer) ReadTo(ctx *ReadContext, target *int16, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s int16Serializer) ReadDataTo(ctx *ReadContext, target *int16) error {
	*target = ctx.buffer.ReadInt16()
	return nil
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

func (s int16Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*int16), readRefInfo, readTypeInfo)
}

func (s int16Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*int16))
}

// Serializer interface methods
func (s int16Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteInt16(int16(value.Int()))
	return nil
}

func (s int16Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(ctx.buffer.ReadInt16()))
	return nil
}

// int32Serializer handles int32 type
type int32Serializer struct{}

var globalInt32Serializer = int32Serializer{}

func (s int32Serializer) TypeId() TypeId       { return INT32 }
func (s int32Serializer) NeedToWriteRef() bool { return false }

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
	ctx.buffer.WriteVarint32(value)
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
	return ctx.buffer.ReadVarint32(), nil
}

func (s int32Serializer) ReadTo(ctx *ReadContext, target *int32, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s int32Serializer) ReadDataTo(ctx *ReadContext, target *int32) error {
	*target = ctx.buffer.ReadVarint32()
	return nil
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

func (s int32Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*int32), readRefInfo, readTypeInfo)
}

func (s int32Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*int32))
}

// Serializer interface methods
func (s int32Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteVarint32(int32(value.Int()))
	return nil
}

func (s int32Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(int64(ctx.buffer.ReadVarint32()))
	return nil
}

// int64Serializer handles int64 type
type int64Serializer struct{}

var globalInt64Serializer = int64Serializer{}

func (s int64Serializer) TypeId() TypeId       { return INT64 }
func (s int64Serializer) NeedToWriteRef() bool { return false }

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
	ctx.buffer.WriteVarint64(value)
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
	return ctx.buffer.ReadVarint64(), nil
}

func (s int64Serializer) ReadTo(ctx *ReadContext, target *int64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s int64Serializer) ReadDataTo(ctx *ReadContext, target *int64) error {
	*target = ctx.buffer.ReadVarint64()
	return nil
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

func (s int64Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*int64), readRefInfo, readTypeInfo)
}

func (s int64Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*int64))
}

// Serializer interface methods
func (s int64Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteVarint64(value.Int())
	return nil
}

func (s int64Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(ctx.buffer.ReadVarint64())
	return nil
}

// intSerializer handles int type
type intSerializer struct{}

func (s intSerializer) TypeId() TypeId       { return -INT64 }
func (s intSerializer) NeedToWriteRef() bool { return false }

// AnySerializer interface methods
func (s intSerializer) WriteAny(ctx *WriteContext, value any, writeRefInfo, writeTypeInfo bool) error {
	if writeRefInfo {
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeTypeInfo {
		ctx.WriteTypeId(INT64)
	}
	return s.WriteDataAny(ctx, value)
}

func (s intSerializer) WriteDataAny(ctx *WriteContext, value any) error {
	ctx.buffer.WriteVarint64(int64(value.(int)))
	return nil
}

func (s intSerializer) ReadAny(ctx *ReadContext, readRefInfo, readTypeInfo bool) (any, error) {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataAny(ctx)
}

func (s intSerializer) ReadDataAny(ctx *ReadContext) (any, error) {
	v := ctx.buffer.ReadVarint64()
	if v > MaxInt || v < MinInt {
		return 0, fmt.Errorf("int64 %d exceed int range", v)
	}
	return int(v), nil
}

func (s intSerializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataToAny(ctx, target)
}

func (s intSerializer) ReadDataToAny(ctx *ReadContext, target any) error {
	v := ctx.buffer.ReadVarint64()
	if v > MaxInt || v < MinInt {
		return fmt.Errorf("int64 %d exceed int range", v)
	}
	*target.(*int) = int(v)
	return nil
}

// Serializer interface methods
func (s intSerializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteVarint64(value.Int())
	return nil
}

func (s intSerializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetInt(ctx.buffer.ReadVarint64())
	return nil
}

// float32Serializer handles float32 type
type float32Serializer struct{}

var globalFloat32Serializer = float32Serializer{}

func (s float32Serializer) TypeId() TypeId       { return FLOAT }
func (s float32Serializer) NeedToWriteRef() bool { return false }

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

func (s float32Serializer) ReadTo(ctx *ReadContext, target *float32, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s float32Serializer) ReadDataTo(ctx *ReadContext, target *float32) error {
	*target = ctx.buffer.ReadFloat32()
	return nil
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

func (s float32Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*float32), readRefInfo, readTypeInfo)
}

func (s float32Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*float32))
}

// Serializer interface methods
func (s float32Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteFloat32(float32(value.Float()))
	return nil
}

func (s float32Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetFloat(float64(ctx.buffer.ReadFloat32()))
	return nil
}

// float64Serializer handles float64 type
type float64Serializer struct{}

var globalFloat64Serializer = float64Serializer{}

func (s float64Serializer) TypeId() TypeId       { return DOUBLE }
func (s float64Serializer) NeedToWriteRef() bool { return false }

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

func (s float64Serializer) ReadTo(ctx *ReadContext, target *float64, readRefInfo, readTypeInfo bool) error {
	if readRefInfo {
		_ = ctx.buffer.ReadInt8()
	}
	if readTypeInfo {
		_ = ctx.buffer.ReadInt16()
	}
	return s.ReadDataTo(ctx, target)
}

func (s float64Serializer) ReadDataTo(ctx *ReadContext, target *float64) error {
	*target = ctx.buffer.ReadFloat64()
	return nil
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

func (s float64Serializer) ReadToAny(ctx *ReadContext, target any, readRefInfo, readTypeInfo bool) error {
	return s.ReadTo(ctx, target.(*float64), readRefInfo, readTypeInfo)
}

func (s float64Serializer) ReadDataToAny(ctx *ReadContext, target any) error {
	return s.ReadDataTo(ctx, target.(*float64))
}

// Serializer interface methods
func (s float64Serializer) WriteValue(ctx *WriteContext, value reflect.Value) error {
	ctx.buffer.WriteFloat64(value.Float())
	return nil
}

func (s float64Serializer) ReadValue(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	value.SetFloat(ctx.buffer.ReadFloat64())
	return nil
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
