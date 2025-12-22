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
	"strconv"
	"unsafe"
)

// isLittleEndian is true if the current system is little-endian
var isLittleEndian = func() bool {
	var x uint16 = 0x0102
	return *(*byte)(unsafe.Pointer(&x)) == 0x02
}()

// isNilSlice checks if a value is a nil slice. Safe to call on any value type.
// Returns false for arrays and other non-slice types.
func isNilSlice(v reflect.Value) bool {
	return v.Kind() == reflect.Slice && v.IsNil()
}

type byteSliceSerializer struct {
}

func (s byteSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	data := value.Interface().([]byte)
	buf := ctx.Buffer()
	// Write length + data directly (like primitive arrays)
	buf.WriteLength(len(data))
	buf.WriteBinary(data)
}

func (s byteSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, BINARY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s byteSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s byteSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	// typeInfo is already read, don't read it again
	s.Read(ctx, refMode, false, value)
}

func (s byteSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	length := buf.ReadLength(ctxErr)
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]byte, length)
	raw := buf.ReadBytes(length, ctxErr)
	copy(result, raw)
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

type ByteSliceBufferObject struct {
	data []byte
}

func (o *ByteSliceBufferObject) TotalBytes() int {
	return len(o.data)
}

func (o *ByteSliceBufferObject) WriteTo(buf *ByteBuffer) {
	buf.WriteBinary(o.data)
}

func (o *ByteSliceBufferObject) ToBuffer() *ByteBuffer {
	return NewByteBuffer(o.data)
}

type boolSliceSerializer struct {
}

func (s boolSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]bool)
	size := len(v)
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if size == 0 {
		return
	}
	// Bools are 1 byte each in Go, direct memory copy works
	buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
}

func (s boolSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, BOOL_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s boolSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s boolSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

func (s boolSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	length := buf.ReadLength(ctxErr)
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]bool, length)
	// Bools are 1 byte each in Go, direct memory copy works
	raw := buf.ReadBytes(length, ctxErr)
	copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), length), raw)
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

type int8SliceSerializer struct {
}

func (s int8SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int8)
	size := len(v)
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if size == 0 {
		return
	}
	// int8 is byte-sized, direct memory copy works
	buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
}

func (s int8SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, INT8_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s int8SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	length := buf.ReadLength(ctxErr)
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]int8, length)
	// int8 is byte-sized, direct memory copy works
	raw := buf.ReadBytes(length, ctxErr)
	copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), length), raw)
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s int8SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s int8SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

type int16SliceSerializer struct {
}

func (s int16SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int16)
	size := len(v) * 2
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if len(v) == 0 {
		return
	}
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
	} else {
		for _, elem := range v {
			buf.WriteInt16(elem)
		}
	}
}

func (s int16SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, INT16_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s int16SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	length := size / 2
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]int16, length)
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		raw := buf.ReadBytes(size, ctxErr)
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt16(ctxErr)
		}
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s int16SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s int16SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

type int32SliceSerializer struct {
}

func (s int32SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int32)
	size := len(v) * 4
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if len(v) == 0 {
		return
	}
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
	} else {
		for _, elem := range v {
			buf.WriteInt32(elem)
		}
	}
}

func (s int32SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, INT32_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s int32SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	length := size / 4
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]int32, length)
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		raw := buf.ReadBytes(size, ctxErr)
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt32(ctxErr)
		}
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s int32SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s int32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

type int64SliceSerializer struct {
}

func (s int64SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int64)
	size := len(v) * 8
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if len(v) == 0 {
		return
	}
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
	} else {
		for _, elem := range v {
			buf.WriteInt64(elem)
		}
	}
}

func (s int64SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, INT64_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s int64SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	length := size / 8
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]int64, length)
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		raw := buf.ReadBytes(size, ctxErr)
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt64(ctxErr)
		}
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s int64SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s int64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

type float32SliceSerializer struct {
}

func (s float32SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]float32)
	size := len(v) * 4
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if len(v) == 0 {
		return
	}
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
	} else {
		for _, elem := range v {
			buf.WriteFloat32(elem)
		}
	}
}

func (s float32SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, FLOAT32_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s float32SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	length := size / 4
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]float32, length)
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		raw := buf.ReadBytes(size, ctxErr)
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadFloat32(ctxErr)
		}
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s float32SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s float32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

type float64SliceSerializer struct {
}

func (s float64SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]float64)
	size := len(v) * 8
	if size >= MaxInt32 {
		ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
		return
	}
	buf.WriteLength(size)
	if len(v) == 0 {
		return
	}
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&v[0])), size))
	} else {
		for _, elem := range v {
			buf.WriteFloat64(elem)
		}
	}
}

func (s float64SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, FLOAT64_ARRAY)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s float64SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	length := size / 8
	if length == 0 {
		value.Set(reflect.MakeSlice(type_, 0, 0))
		return
	}
	// Allocate slice and read directly into it
	result := make([]float64, length)
	if isLittleEndian {
		// Direct memory copy for little-endian systems
		raw := buf.ReadBytes(size, ctxErr)
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadFloat64(ctxErr)
		}
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}

func (s float64SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s float64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

// ============================================================================
// Exported helper functions for primitive slice serialization (ARRAY protocol)
// These functions write: size_bytes + binary_data
// They are used by struct serializers and generated code
// ============================================================================

// WriteByteSlice writes []byte to buffer using ARRAY protocol
func WriteByteSlice(buf *ByteBuffer, value []byte) {
	buf.WriteLength(len(value))
	if len(value) > 0 {
		buf.WriteBinary(value)
	}
}

// ReadByteSlice reads []byte from buffer using ARRAY protocol
func ReadByteSlice(buf *ByteBuffer, err *Error) []byte {
	size := buf.ReadLength(err)
	if size == 0 {
		return make([]byte, 0)
	}
	result := make([]byte, size)
	raw := buf.ReadBinary(size, err)
	if raw != nil {
		copy(result, raw)
	}
	return result
}

// WriteBoolSlice writes []bool to buffer using ARRAY protocol
func WriteBoolSlice(buf *ByteBuffer, value []bool) {
	size := len(value)
	buf.WriteLength(size)
	if size > 0 {
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
	}
}

// ReadBoolSlice reads []bool from buffer using ARRAY protocol
func ReadBoolSlice(buf *ByteBuffer, err *Error) []bool {
	size := buf.ReadLength(err)
	if size == 0 {
		return make([]bool, 0)
	}
	result := make([]bool, size)
	raw := buf.ReadBinary(size, err)
	if raw != nil {
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	}
	return result
}

// WriteInt8Slice writes []int8 to buffer using ARRAY protocol
func WriteInt8Slice(buf *ByteBuffer, value []int8) {
	size := len(value)
	buf.WriteLength(size)
	if size > 0 {
		buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
	}
}

// ReadInt8Slice reads []int8 from buffer using ARRAY protocol
func ReadInt8Slice(buf *ByteBuffer, err *Error) []int8 {
	size := buf.ReadLength(err)
	if size == 0 {
		return make([]int8, 0)
	}
	result := make([]int8, size)
	raw := buf.ReadBinary(size, err)
	if raw != nil {
		copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
	}
	return result
}

// WriteInt16Slice writes []int16 to buffer using ARRAY protocol
func WriteInt16Slice(buf *ByteBuffer, value []int16) {
	size := len(value) * 2
	buf.WriteLength(size)
	if len(value) > 0 {
		if isLittleEndian {
			buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
		} else {
			for _, elem := range value {
				buf.WriteInt16(elem)
			}
		}
	}
}

// ReadInt16Slice reads []int16 from buffer using ARRAY protocol
func ReadInt16Slice(buf *ByteBuffer, err *Error) []int16 {
	size := buf.ReadLength(err)
	length := size / 2
	if length == 0 {
		return make([]int16, 0)
	}
	result := make([]int16, length)
	if isLittleEndian {
		raw := buf.ReadBinary(size, err)
		if raw != nil {
			copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
		}
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt16(err)
		}
	}
	return result
}

// WriteInt32Slice writes []int32 to buffer using ARRAY protocol
func WriteInt32Slice(buf *ByteBuffer, value []int32) {
	size := len(value) * 4
	buf.WriteLength(size)
	if len(value) > 0 {
		if isLittleEndian {
			buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
		} else {
			for _, elem := range value {
				buf.WriteInt32(elem)
			}
		}
	}
}

// ReadInt32Slice reads []int32 from buffer using ARRAY protocol
func ReadInt32Slice(buf *ByteBuffer, err *Error) []int32 {
	size := buf.ReadLength(err)
	length := size / 4
	if length == 0 {
		return make([]int32, 0)
	}
	result := make([]int32, length)
	if isLittleEndian {
		raw := buf.ReadBinary(size, err)
		if raw != nil {
			copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
		}
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt32(err)
		}
	}
	return result
}

// WriteInt64Slice writes []int64 to buffer using ARRAY protocol
func WriteInt64Slice(buf *ByteBuffer, value []int64) {
	size := len(value) * 8
	buf.WriteLength(size)
	if len(value) > 0 {
		if isLittleEndian {
			buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
		} else {
			for _, elem := range value {
				buf.WriteInt64(elem)
			}
		}
	}
}

// ReadInt64Slice reads []int64 from buffer using ARRAY protocol
func ReadInt64Slice(buf *ByteBuffer, err *Error) []int64 {
	size := buf.ReadLength(err)
	length := size / 8
	if length == 0 {
		return make([]int64, 0)
	}
	result := make([]int64, length)
	if isLittleEndian {
		raw := buf.ReadBinary(size, err)
		if raw != nil {
			copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
		}
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadInt64(err)
		}
	}
	return result
}

// WriteFloat32Slice writes []float32 to buffer using ARRAY protocol
func WriteFloat32Slice(buf *ByteBuffer, value []float32) {
	size := len(value) * 4
	buf.WriteLength(size)
	if len(value) > 0 {
		if isLittleEndian {
			buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
		} else {
			for _, elem := range value {
				buf.WriteFloat32(elem)
			}
		}
	}
}

// ReadFloat32Slice reads []float32 from buffer using ARRAY protocol
func ReadFloat32Slice(buf *ByteBuffer, err *Error) []float32 {
	size := buf.ReadLength(err)
	length := size / 4
	if length == 0 {
		return make([]float32, 0)
	}
	result := make([]float32, length)
	if isLittleEndian {
		raw := buf.ReadBinary(size, err)
		if raw != nil {
			copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
		}
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadFloat32(err)
		}
	}
	return result
}

// WriteFloat64Slice writes []float64 to buffer using ARRAY protocol
func WriteFloat64Slice(buf *ByteBuffer, value []float64) {
	size := len(value) * 8
	buf.WriteLength(size)
	if len(value) > 0 {
		if isLittleEndian {
			buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
		} else {
			for _, elem := range value {
				buf.WriteFloat64(elem)
			}
		}
	}
}

// ReadFloat64Slice reads []float64 from buffer using ARRAY protocol
func ReadFloat64Slice(buf *ByteBuffer, err *Error) []float64 {
	size := buf.ReadLength(err)
	length := size / 8
	if length == 0 {
		return make([]float64, 0)
	}
	result := make([]float64, length)
	if isLittleEndian {
		raw := buf.ReadBinary(size, err)
		if raw != nil {
			copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
		}
	} else {
		for i := 0; i < length; i++ {
			result[i] = buf.ReadFloat64(err)
		}
	}
	return result
}

// WriteIntSlice writes []int to buffer using ARRAY protocol
func WriteIntSlice(buf *ByteBuffer, value []int) {
	if strconv.IntSize == 64 {
		size := len(value) * 8
		buf.WriteLength(size)
		if len(value) > 0 {
			if isLittleEndian {
				buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
			} else {
				for _, elem := range value {
					buf.WriteInt64(int64(elem))
				}
			}
		}
	} else {
		size := len(value) * 4
		buf.WriteLength(size)
		if len(value) > 0 {
			if isLittleEndian {
				buf.WriteBinary(unsafe.Slice((*byte)(unsafe.Pointer(&value[0])), size))
			} else {
				for _, elem := range value {
					buf.WriteInt32(int32(elem))
				}
			}
		}
	}
}

// ReadIntSlice reads []int from buffer using ARRAY protocol
func ReadIntSlice(buf *ByteBuffer, err *Error) []int {
	size := buf.ReadLength(err)
	if strconv.IntSize == 64 {
		length := size / 8
		if length == 0 {
			return make([]int, 0)
		}
		result := make([]int, length)
		if isLittleEndian {
			raw := buf.ReadBinary(size, err)
			if raw != nil {
				copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
			}
		} else {
			for i := 0; i < length; i++ {
				result[i] = int(buf.ReadInt64(err))
			}
		}
		return result
	} else {
		length := size / 4
		if length == 0 {
			return make([]int, 0)
		}
		result := make([]int, length)
		if isLittleEndian {
			raw := buf.ReadBinary(size, err)
			if raw != nil {
				copy(unsafe.Slice((*byte)(unsafe.Pointer(&result[0])), size), raw)
			}
		} else {
			for i := 0; i < length; i++ {
				result[i] = int(buf.ReadInt32(err))
			}
		}
		return result
	}
}

type intSliceSerializer struct {
}

func (s intSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int)
	WriteIntSlice(buf, v)
}

func (s intSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	var typeId TypeId = INT32_ARRAY
	if strconv.IntSize == 64 {
		typeId = INT64_ARRAY
	}
	done := writeSliceRefAndType(ctx, refMode, writeType, value, typeId)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s intSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s intSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	// typeInfo is already read, don't read it again
	s.Read(ctx, refMode, false, value)
}

func (s intSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	var length int
	if strconv.IntSize == 64 {
		length = size / 8
	} else {
		length = size / 4
	}
	r := reflect.MakeSlice(type_, length, length)
	if strconv.IntSize == 64 {
		for i := 0; i < length; i++ {
			r.Index(i).SetInt(buf.ReadInt64(ctxErr))
		}
	} else {
		for i := 0; i < length; i++ {
			r.Index(i).SetInt(int64(buf.ReadInt32(ctxErr)))
		}
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
}

// uintSliceSerializer handles []uint serialization.
// This serializer only supports pure Go mode (xlang=false) because uint has
// platform-dependent size which doesn't have a direct cross-language equivalent.
type uintSliceSerializer struct {
}

func (s uintSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]uint)
	if strconv.IntSize == 64 {
		size := len(v) * 8
		if size >= MaxInt32 {
			ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
			return
		}
		buf.WriteLength(size)
		for _, elem := range v {
			buf.WriteInt64(int64(elem))
		}
	} else {
		size := len(v) * 4
		if size >= MaxInt32 {
			ctx.SetError(SerializationErrorf("too long slice: %d", len(v)))
			return
		}
		buf.WriteLength(size)
		for _, elem := range v {
			buf.WriteInt32(int32(elem))
		}
	}
}

func (s uintSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	var typeId TypeId = INT32_ARRAY
	if strconv.IntSize == 64 {
		typeId = INT64_ARRAY
	}
	done := writeSliceRefAndType(ctx, refMode, writeType, value, typeId)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s uintSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s uintSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	s.Read(ctx, refMode, false, value)
}

func (s uintSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	size := buf.ReadLength(ctxErr)
	var length int
	if strconv.IntSize == 64 {
		length = size / 8
	} else {
		length = size / 4
	}
	r := reflect.MakeSlice(type_, length, length)
	if strconv.IntSize == 64 {
		for i := 0; i < length; i++ {
			r.Index(i).SetUint(uint64(buf.ReadInt64(ctxErr)))
		}
	} else {
		for i := 0; i < length; i++ {
			r.Index(i).SetUint(uint64(buf.ReadInt32(ctxErr)))
		}
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
}

type stringSliceSerializer struct {
}

func (s stringSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]string)
	length := len(v)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}
	// Write collection flags: CollectionIsSameType only
	// Note: Strings don't need reference tracking per xlang spec (NeedWriteRef(STRING) = false)
	// We don't set CollectionIsDeclElementType so element type is written for polymorphic deserialization
	collectFlag := CollectionIsSameType
	buf.WriteInt8(int8(collectFlag))
	// Write element type info (STRING)
	buf.WriteVaruint32Small7(uint32(STRING))

	// Write elements directly (no ref flag for strings)
	for _, str := range v {
		writeString(buf, str)
	}
}

func (s stringSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) {
	done := writeSliceRefAndType(ctx, refMode, writeType, value, LIST)
	if done || ctx.HasError() {
		return
	}
	s.WriteData(ctx, value)
}

func (s stringSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) {
	done := readSliceRefAndType(ctx, refMode, readType, value)
	if done || ctx.HasError() {
		return
	}
	s.ReadData(ctx, value.Type(), value)
}

func (s stringSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) {
	// typeInfo is already read, don't read it again
	s.Read(ctx, refMode, false, value)
}

func (s stringSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	length := int(buf.ReadVaruint32(ctxErr))
	if length == 0 {
		value.Set(reflect.ValueOf(make([]string, 0)))
		return
	}

	// Read collection flags
	collectFlag := buf.ReadInt8(ctxErr)

	// Read element type info if present (when CollectionIsSameType but not CollectionIsDeclElementType)
	if (collectFlag&CollectionIsSameType) != 0 && (collectFlag&CollectionIsDeclElementType) == 0 {
		_ = buf.ReadVaruint32Small7(ctxErr) // Read and discard type ID (we know it's STRING)
	}

	result := make([]string, length)

	// Check if remote sent with ref tracking (handle both cases for compatibility)
	trackRefs := (collectFlag & CollectionTrackingRef) != 0

	// Read elements
	for i := 0; i < length; i++ {
		if trackRefs {
			refFlag := buf.ReadInt8(ctxErr)
			if refFlag == NullFlag {
				// null string, leave as zero value
				continue
			}
		}
		result[i] = readString(buf, ctxErr)
	}
	value.Set(reflect.ValueOf(result))
	ctx.RefResolver().Reference(value)
}
