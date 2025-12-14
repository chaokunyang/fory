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

func (s byteSliceSerializer) TypeId() TypeId {
	return BINARY
}

func (s byteSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s byteSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	data := value.Interface().([]byte)
	buf := ctx.Buffer()
	// Write length + data directly (like primitive arrays)
	buf.WriteLength(len(data))
	buf.WriteBinary(data)
	return nil
}

func (s byteSliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(BINARY))
	}
	return s.WriteData(ctx, value)
}

func (s byteSliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s byteSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
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

func (s boolSliceSerializer) TypeId() TypeId {
	return BOOL_ARRAY
}

func (s boolSliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s boolSliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(BOOL_ARRAY))
	}
	return s.WriteData(ctx, value)
}

func (s boolSliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s boolSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
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

func (s int8SliceSerializer) TypeId() TypeId {
	return INT8_ARRAY
}

func (s int8SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s int8SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(INT8_ARRAY))
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

func (s int8SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int8SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int16SliceSerializer struct {
}

func (s int16SliceSerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s int16SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(INT16_ARRAY))
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

func (s int16SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int16SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int32SliceSerializer struct {
}

func (s int32SliceSerializer) TypeId() TypeId {
	return INT32_ARRAY
}

func (s int32SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s int32SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(INT32_ARRAY))
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

func (s int32SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int64SliceSerializer struct {
}

func (s int64SliceSerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s int64SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(INT64_ARRAY))
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

func (s int64SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type float32SliceSerializer struct {
}

func (s float32SliceSerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s float32SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(FLOAT32_ARRAY))
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

func (s float32SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float32SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type float64SliceSerializer struct {
}

func (s float64SliceSerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64SliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s float64SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(FLOAT64_ARRAY))
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

func (s float64SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float64SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
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

// readBoolSliceInto reads []bool into target, reusing capacity when possible
func readBoolSliceInto(buf *ByteBuffer, target *[]bool) error {
	length := buf.ReadLength()
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]bool, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadBool()
	}
	return nil
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

// readInt8SliceInto reads []int8 into target, reusing capacity when possible
func readInt8SliceInto(buf *ByteBuffer, target *[]int8) error {
	length := buf.ReadLength()
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int8, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadInt8()
	}
	return nil
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

// readInt16SliceInto reads []int16 into target, reusing capacity when possible
func readInt16SliceInto(buf *ByteBuffer, target *[]int16) error {
	size := buf.ReadLength()
	length := size / 2
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int16, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadInt16()
	}
	return nil
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

// readInt32SliceInto reads []int32 into target, reusing capacity when possible
func readInt32SliceInto(buf *ByteBuffer, target *[]int32) error {
	size := buf.ReadLength()
	length := size / 4
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int32, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadInt32()
	}
	return nil
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

// readInt64SliceInto reads []int64 into target, reusing capacity when possible
func readInt64SliceInto(buf *ByteBuffer, target *[]int64) error {
	size := buf.ReadLength()
	length := size / 8
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]int64, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadInt64()
	}
	return nil
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

// readIntSliceInto reads []int into target, reusing capacity when possible
func readIntSliceInto(buf *ByteBuffer, target *[]int) error {
	size := buf.ReadLength()
	if strconv.IntSize == 64 {
		length := size / 8
		if cap(*target) >= length {
			*target = (*target)[:length]
		} else {
			*target = make([]int, length)
		}
		for i := 0; i < length; i++ {
			(*target)[i] = int(buf.ReadInt64())
		}
	} else {
		length := size / 4
		if cap(*target) >= length {
			*target = (*target)[:length]
		} else {
			*target = make([]int, length)
		}
		for i := 0; i < length; i++ {
			(*target)[i] = int(buf.ReadInt32())
		}
	}
	return nil
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

// readFloat32SliceInto reads []float32 into target, reusing capacity when possible
func readFloat32SliceInto(buf *ByteBuffer, target *[]float32) error {
	size := buf.ReadLength()
	length := size / 4
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]float32, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadFloat32()
	}
	return nil
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

// readFloat64SliceInto reads []float64 into target, reusing capacity when possible
func readFloat64SliceInto(buf *ByteBuffer, target *[]float64) error {
	size := buf.ReadLength()
	length := size / 8
	if cap(*target) >= length {
		*target = (*target)[:length]
	} else {
		*target = make([]float64, length)
	}
	for i := 0; i < length; i++ {
		(*target)[i] = buf.ReadFloat64()
	}
	return nil
}

type intSliceSerializer struct {
}

func (s intSliceSerializer) TypeId() TypeId {
	if strconv.IntSize == 64 {
		return INT64_ARRAY
	}
	return INT32_ARRAY
}

func (s intSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s intSliceSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int)
	return writeIntSlice(buf, v)
}

func (s intSliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(s.TypeId()))
	}
	return s.WriteData(ctx, value)
}

func (s intSliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s intSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
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

func (s uintSliceSerializer) TypeId() TypeId {
	if strconv.IntSize == 64 {
		return INT64_ARRAY
	}
	return INT32_ARRAY
}

func (s uintSliceSerializer) NeedToWriteRef() bool {
	return true
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

func (s uintSliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if isNilSlice(value) {
			ctx.Buffer().WriteInt8(NullFlag)
			return nil
		}
		refWritten, err := ctx.RefResolver().WriteRefOrNull(ctx.Buffer(), value)
		if err != nil {
			return err
		}
		if refWritten {
			return nil
		}
	}
	if writeType {
		ctx.Buffer().WriteVaruint32Small7(uint32(s.TypeId()))
	}
	return s.WriteData(ctx, value)
}

func (s uintSliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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
	if readType {
		_ = buf.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s uintSliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
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

func (s stringSliceSerializer) TypeId() TypeId {
	return LIST
}

func (s stringSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s stringSliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]string)
	err := ctx.WriteLength(len(v))
	if err != nil {
		return err
	}
	for _, str := range v {
		if refWritten, err := ctx.RefResolver().WriteRefOrNull(buf, reflect.ValueOf(str)); err == nil {
			if !refWritten {
				if err := writeString(buf, str); err != nil {
					return err
				}
			}
		} else {
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
	set := func(i int, s string) {
		if elemTyp.Kind() == reflect.String {
			r.Index(i).SetString(s)
		} else {
			r.Index(i).Set(reflect.ValueOf(s).Convert(elemTyp))
		}
	}

	for i := 0; i < length; i++ {
		refFlag := ctx.RefResolver().ReadRefOrNull(buf)
		if refFlag == RefValueFlag || refFlag == NotNullValueFlag {
			var nextReadRefId int32
			if refFlag == RefValueFlag {
				var err error
				nextReadRefId, err = ctx.RefResolver().PreserveRefId()
				if err != nil {
					return err
				}
			}
			elem := readString(buf)
			if ctx.TrackRef() && refFlag == RefValueFlag {
				// If value is not nil(reflect), then value is a pointer to some variable, we can update the `value`,
				// then record `value` in the reference resolver.
				ctx.RefResolver().SetReadObject(nextReadRefId, reflect.ValueOf(elem))
			}
			set(i, elem)
		} else if refFlag == NullFlag {
			set(i, "")
		} else { // RefNoneFlag
			set(i, ctx.RefResolver().GetCurrentReadObject().Interface().(string))
		}
	}
	value.Set(r)
	return nil
}
