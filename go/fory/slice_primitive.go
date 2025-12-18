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
	"strconv"
)

// isNilSlice checks if a value is a nil slice. Safe to call on any value type.
// Returns false for arrays and other non-slice types.
func isNilSlice(v reflect.Value) bool {
	return v.Kind() == reflect.Slice && v.IsNil()
}

type byteSliceSerializer struct {
}

func (s byteSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	data := value.Interface().([]byte)
	buf := ctx.Buffer()
	// Write length + data directly (like primitive arrays)
	buf.WriteLength(len(data))
	buf.WriteBinary(data)
	return nil
}

func (s byteSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, BINARY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s byteSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s byteSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, refMode, false, value)
}

func (s byteSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	result := reflect.MakeSlice(type_, length, length)
	raw := buf.ReadBytes(length)
	for i := 0; i < length; i++ {
		result.Index(i).SetUint(uint64(raw[i]))
	}
	value.Set(result)
	ctx.RefResolver().Reference(value)
	return nil
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

func (s boolSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]bool)
	size := len(v)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteBool(elem)
	}
	return nil
}

func (s boolSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, BOOL_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s boolSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s boolSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

func (s boolSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool())
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

type int8SliceSerializer struct {
}

func (s int8SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int8)
	size := len(v)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteByte_(byte(elem))
	}
	return nil
}

func (s int8SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, INT8_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int8SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(int8(buf.ReadByte_())))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int8SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int8SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type int16SliceSerializer struct {
}

func (s int16SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int16)
	size := len(v) * 2
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt16(elem)
	}
	return nil
}

func (s int16SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, INT16_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int16SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int16SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int16SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type int32SliceSerializer struct {
}

func (s int32SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt32(elem)
	}
	return nil
}

func (s int32SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, INT32_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int32SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int32SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type int64SliceSerializer struct {
}

func (s int64SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteInt64(elem)
	}
	return nil
}

func (s int64SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, INT64_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int64SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64())
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int64SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type float32SliceSerializer struct {
}

func (s float32SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float32)
	size := len(v) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat32(elem)
	}
	return nil
}

func (s float32SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, FLOAT32_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s float32SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s float32SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type float64SliceSerializer struct {
}

func (s float64SliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]float64)
	size := len(v) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(v))
	}
	buf.WriteLength(size)
	for _, elem := range v {
		buf.WriteFloat64(elem)
	}
	return nil
}

func (s float64SliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, FLOAT64_ARRAY)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s float64SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	r := reflect.MakeSlice(type_, length, length)
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64())
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s float64SliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

// ============================================================================
// Helper functions for primitive slice serialization
// ============================================================================

// writeBoolSlice writes []bool to buffer
func writeBoolSlice(buf *ByteBuffer, value []bool) error {
	size := len(value)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", size)
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteBool(elem)
	}
	return nil
}

// readBoolSlice reads []bool from buffer
func readBoolSlice(buf *ByteBuffer) ([]bool, error) {
	length := buf.ReadLength()
	result := make([]bool, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadBool()
	}
	return result, nil
}

// writeInt8Slice writes []int8 to buffer
func writeInt8Slice(buf *ByteBuffer, value []int8) error {
	size := len(value)
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", size)
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt8(elem)
	}
	return nil
}

// readInt8Slice reads []int8 from buffer
func readInt8Slice(buf *ByteBuffer) ([]int8, error) {
	length := buf.ReadLength()
	result := make([]int8, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt8()
	}
	return result, nil
}

// writeInt16Slice writes []int16 to buffer
func writeInt16Slice(buf *ByteBuffer, value []int16) error {
	size := len(value) * 2
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(value))
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt16(elem)
	}
	return nil
}

// readInt16Slice reads []int16 from buffer
func readInt16Slice(buf *ByteBuffer) ([]int16, error) {
	size := buf.ReadLength()
	length := size / 2
	result := make([]int16, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt16()
	}
	return result, nil
}

// writeInt32Slice writes []int32 to buffer
func writeInt32Slice(buf *ByteBuffer, value []int32) error {
	size := len(value) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(value))
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt32(elem)
	}
	return nil
}

// readInt32Slice reads []int32 from buffer
func readInt32Slice(buf *ByteBuffer) ([]int32, error) {
	size := buf.ReadLength()
	length := size / 4
	result := make([]int32, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt32()
	}
	return result, nil
}

// writeInt64Slice writes []int64 to buffer
func writeInt64Slice(buf *ByteBuffer, value []int64) error {
	size := len(value) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(value))
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteInt64(elem)
	}
	return nil
}

// readInt64Slice reads []int64 from buffer
func readInt64Slice(buf *ByteBuffer) ([]int64, error) {
	size := buf.ReadLength()
	length := size / 8
	result := make([]int64, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadInt64()
	}
	return result, nil
}

// writeIntSlice writes []int to buffer
func writeIntSlice(buf *ByteBuffer, value []int) error {
	if strconv.IntSize == 64 {
		size := len(value) * 8
		if size >= MaxInt32 {
			return fmt.Errorf("too long slice: %d", len(value))
		}
		buf.WriteLength(size)
		for _, elem := range value {
			buf.WriteInt64(int64(elem))
		}
	} else {
		size := len(value) * 4
		if size >= MaxInt32 {
			return fmt.Errorf("too long slice: %d", len(value))
		}
		buf.WriteLength(size)
		for _, elem := range value {
			buf.WriteInt32(int32(elem))
		}
	}
	return nil
}

// readIntSlice reads []int from buffer
func readIntSlice(buf *ByteBuffer) ([]int, error) {
	size := buf.ReadLength()
	if strconv.IntSize == 64 {
		length := size / 8
		result := make([]int, length)
		for i := 0; i < length; i++ {
			result[i] = int(buf.ReadInt64())
		}
		return result, nil
	} else {
		length := size / 4
		result := make([]int, length)
		for i := 0; i < length; i++ {
			result[i] = int(buf.ReadInt32())
		}
		return result, nil
	}
}

// writeFloat32Slice writes []float32 to buffer
func writeFloat32Slice(buf *ByteBuffer, value []float32) error {
	size := len(value) * 4
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(value))
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteFloat32(elem)
	}
	return nil
}

// readFloat32Slice reads []float32 from buffer
func readFloat32Slice(buf *ByteBuffer) ([]float32, error) {
	size := buf.ReadLength()
	length := size / 4
	result := make([]float32, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadFloat32()
	}
	return result, nil
}

// writeFloat64Slice writes []float64 to buffer
func writeFloat64Slice(buf *ByteBuffer, value []float64) error {
	size := len(value) * 8
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", len(value))
	}
	buf.WriteLength(size)
	for _, elem := range value {
		buf.WriteFloat64(elem)
	}
	return nil
}

// readFloat64Slice reads []float64 from buffer
func readFloat64Slice(buf *ByteBuffer) ([]float64, error) {
	size := buf.ReadLength()
	length := size / 8
	result := make([]float64, length)
	for i := 0; i < length; i++ {
		result[i] = buf.ReadFloat64()
	}
	return result, nil
}

type intSliceSerializer struct {
}

func (s intSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int)
	return writeIntSlice(buf, v)
}

func (s intSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	var typeId TypeId = INT32_ARRAY
	if strconv.IntSize == 64 {
		typeId = INT64_ARRAY
	}
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, typeId)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s intSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s intSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, refMode, false, value)
}

func (s intSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	size := buf.ReadLength()
	var length int
	if strconv.IntSize == 64 {
		length = size / 8
	} else {
		length = size / 4
	}
	r := reflect.MakeSlice(type_, length, length)
	if strconv.IntSize == 64 {
		for i := 0; i < length; i++ {
			r.Index(i).SetInt(buf.ReadInt64())
		}
	} else {
		for i := 0; i < length; i++ {
			r.Index(i).SetInt(int64(buf.ReadInt32()))
		}
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

// uintSliceSerializer handles []uint serialization.
// This serializer only supports pure Go mode (xlang=false) because uint has
// platform-dependent size which doesn't have a direct cross-language equivalent.
type uintSliceSerializer struct {
}

func (s uintSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]uint)
	if strconv.IntSize == 64 {
		size := len(v) * 8
		if size >= MaxInt32 {
			return fmt.Errorf("too long slice: %d", len(v))
		}
		buf.WriteLength(size)
		for _, elem := range v {
			buf.WriteInt64(int64(elem))
		}
	} else {
		size := len(v) * 4
		if size >= MaxInt32 {
			return fmt.Errorf("too long slice: %d", len(v))
		}
		buf.WriteLength(size)
		for _, elem := range v {
			buf.WriteInt32(int32(elem))
		}
	}
	return nil
}

func (s uintSliceSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	var typeId TypeId = INT32_ARRAY
	if strconv.IntSize == 64 {
		typeId = INT64_ARRAY
	}
	done, err := writeSliceRefAndType(ctx, refMode, writeType, value, typeId)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s uintSliceSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readSliceRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s uintSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

func (s uintSliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	size := buf.ReadLength()
	var length int
	if strconv.IntSize == 64 {
		length = size / 8
	} else {
		length = size / 4
	}
	r := reflect.MakeSlice(type_, length, length)
	if strconv.IntSize == 64 {
		for i := 0; i < length; i++ {
			r.Index(i).SetUint(uint64(buf.ReadInt64()))
		}
	} else {
		for i := 0; i < length; i++ {
			r.Index(i).SetUint(uint64(buf.ReadInt32()))
		}
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

type stringSliceSerializer struct {
	strSerializer stringSerializer
}

func (s stringSliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]string)
	if err := ctx.WriteLength(len(v)); err != nil {
		return err
	}
	// Strings don't need reference tracking, but xlang format requires NotNullValueFlag per element
	for _, str := range v {
		buf.WriteInt8(NotNullValueFlag)
		if err := writeString(buf, str); err != nil {
			return err
		}
	}
	return nil
}

func (s stringSliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := ctx.ReadLength()
	r := reflect.MakeSlice(type_, length, length)
	elemTyp := type_.Elem()

	// Strings don't need reference tracking, but xlang format has a flag byte per element
	for i := 0; i < length; i++ {
		refFlag := buf.ReadInt8()
		var str string
		if refFlag == NullFlag {
			str = ""
		} else {
			str = readString(buf)
		}
		if elemTyp.Kind() == reflect.String {
			r.Index(i).SetString(str)
		} else {
			r.Index(i).Set(reflect.ValueOf(str).Convert(elemTyp))
		}
	}
	value.Set(r)
	return nil
}
