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
)

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
	result := reflect.MakeSlice(type_, length, length)
	raw := buf.ReadBytes(length, ctxErr)
	for i := 0; i < length; i++ {
		result.Index(i).SetUint(uint64(raw[i]))
	}
	value.Set(result)
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
	for _, elem := range v {
		buf.WriteBool(elem)
	}
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
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool(ctxErr))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteByte_(byte(elem))
	}
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
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(int8(buf.ReadByte(ctxErr))))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteInt16(elem)
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
	length := buf.ReadLength(ctxErr) / 2
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16(ctxErr)))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteInt32(elem)
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
	length := buf.ReadLength(ctxErr) / 4
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32(ctxErr)))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteInt64(elem)
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
	length := buf.ReadLength(ctxErr) / 8
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64(ctxErr))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteFloat32(elem)
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
	length := buf.ReadLength(ctxErr) / 4
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32(ctxErr)))
	}
	value.Set(r)
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
	for _, elem := range v {
		buf.WriteFloat64(elem)
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
	length := buf.ReadLength(ctxErr) / 8
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64(ctxErr))
	}
	value.Set(r)
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
// Helper functions for primitive slice serialization
// ============================================================================

// writeBoolSlice writes []bool to buffer
func writeBoolSlice(buf *ByteBuffer, value []bool, err *Error) {
	size := len(value)
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", size))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteBool(elem)
	}
}

// readBoolSlice reads []bool from buffer
func readBoolSlice(buf *ByteBuffer, err *Error) []bool {
	length := buf.ReadLength(err)
	result := make([]bool, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadBool(err)
	}
	return result
}

// writeInt8Slice writes []int8 to buffer
func writeInt8Slice(buf *ByteBuffer, value []int8, err *Error) {
	size := len(value)
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", size))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt8(elem)
	}
}

// readInt8Slice reads []int8 from buffer
func readInt8Slice(buf *ByteBuffer, err *Error) []int8 {
	length := buf.ReadLength(err)
	result := make([]int8, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt8(err)
	}
	return result
}

// writeInt16Slice writes []int16 to buffer
func writeInt16Slice(buf *ByteBuffer, value []int16, err *Error) {
	size := len(value) * 2
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt16(elem)
	}
}

// readInt16Slice reads []int16 from buffer
func readInt16Slice(buf *ByteBuffer, err *Error) []int16 {
	size := buf.ReadLength(err)
	length := size / 2
	result := make([]int16, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt16(err)
	}
	return result
}

// writeInt32Slice writes []int32 to buffer
func writeInt32Slice(buf *ByteBuffer, value []int32, err *Error) {
	size := len(value) * 4
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt32(elem)
	}
}

// readInt32Slice reads []int32 from buffer
func readInt32Slice(buf *ByteBuffer, err *Error) []int32 {
	size := buf.ReadLength(err)
	length := size / 4
	result := make([]int32, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt32(err)
	}
	return result
}

// writeInt64Slice writes []int64 to buffer
func writeInt64Slice(buf *ByteBuffer, value []int64, err *Error) {
	size := len(value) * 8
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt64(elem)
	}
}

// readInt64Slice reads []int64 from buffer
func readInt64Slice(buf *ByteBuffer, err *Error) []int64 {
	size := buf.ReadLength(err)
	length := size / 8
	result := make([]int64, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt64(err)
	}
	return result
}

// writeIntSlice writes []int to buffer
func writeIntSlice(buf *ByteBuffer, value []int, err *Error) {
	if strconv.IntSize == 64 {
		size := len(value) * 8
		if size >= MaxInt32 {
			err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
			return
		}
		buf.WriteLength(size)
		for _, elem := range value {
			buf.WriteInt64(int64(elem))
		}
	} else {
		size := len(value) * 4
		if size >= MaxInt32 {
			err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
			return
		}
		buf.WriteLength(size)
		for _, elem := range value {
			buf.WriteInt32(int32(elem))
		}
	}
}

// readIntSlice reads []int from buffer
func readIntSlice(buf *ByteBuffer, err *Error) []int {
	size := buf.ReadLength(err)
	if strconv.IntSize == 64 {
		length := size / 8
		result := make([]int, length)
		for i := 0; i < length; i++ {
			result[i] = int(buf.ReadInt64(err))
		}
		return result
	} else {
		length := size / 4
		result := make([]int, length)
		for i := 0; i < length; i++ {
			result[i] = int(buf.ReadInt32(err))
		}
		return result
	}
}

// writeFloat32Slice writes []float32 to buffer
func writeFloat32Slice(buf *ByteBuffer, value []float32, err *Error) {
	size := len(value) * 4
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteFloat32(elem)
	}
}

// readFloat32Slice reads []float32 from buffer
func readFloat32Slice(buf *ByteBuffer, err *Error) []float32 {
	size := buf.ReadLength(err)
	length := size / 4
	result := make([]float32, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadFloat32(err)
	}
	return result
}

// writeFloat64Slice writes []float64 to buffer
func writeFloat64Slice(buf *ByteBuffer, value []float64, err *Error) {
	size := len(value) * 8
	if size >= MaxInt32 {
		err.SetIfOk(SerializationErrorf("too long slice: %d", len(value)))
		return
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteFloat64(elem)
	}
}

// readFloat64Slice reads []float64 from buffer
func readFloat64Slice(buf *ByteBuffer, err *Error) []float64 {
	size := buf.ReadLength(err)
	length := size / 8
	result := make([]float64, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadFloat64(err)
	}
	return result
}

type intSliceSerializer struct {
}

func (s intSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]int)
	writeIntSlice(buf, v, ctx.Err())
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
	strSerializer stringSerializer
}

func (s stringSliceSerializer) Write(ctx *WriteContext, value reflect.Value) {
	buf := ctx.Buffer()
	v := value.Interface().([]string)
	ctx.WriteLength(len(v))
	// Strings don't need reference tracking, but xlang format requires NotNullValueFlag per element
	for _, str := range v {
		buf.WriteInt8(NotNullValueFlag)
		writeString(buf, str)
	}
}

func (s stringSliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) {
	buf := ctx.Buffer()
	ctxErr := ctx.Err()
	length := ctx.ReadLength()
	r := reflect.MakeSlice(type_, length, length)
	elemTyp := type_.Elem()

	// Strings don't need reference tracking, but xlang format has a flag byte per element
	for i := 0; i < length; i++ {
		refFlag := buf.ReadInt8(ctxErr)
		var str string
		if refFlag == NullFlag {
			str = ""
		} else {
			str = readStringE(buf, ctxErr)
		}
		if elemTyp.Kind() == reflect.String {
			r.Index(i).SetString(str)
		} else {
			r.Index(i).Set(reflect.ValueOf(str).Convert(elemTyp))
		}
	}
	value.Set(r)
}
