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
		if value.IsNil() {
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
	// Read length + data directly (like primitive arrays)
	length := buf.ReadLength()

	var result reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		result = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("byte array len %d ≠ %d", length, type_.Len())
		}
		result = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v; want slice or array", type_.Kind())
	}

	// Now read the bytes into the slice/array
	raw := buf.ReadBytes(length)
	if type_.Kind() == reflect.Slice {
		// For slice, copy the bytes
		for i := 0; i < length; i++ {
			result.Index(i).SetUint(uint64(raw[i]))
		}
	} else {
		// For array, use reflect.Copy
		reflect.Copy(result, reflect.ValueOf(raw))
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

type boolArraySerializer struct {
}

func (s boolArraySerializer) TypeId() TypeId {
	return BOOL_ARRAY
}

func (s boolArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s boolArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s boolArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s boolArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s boolArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
	return s.Read(ctx, readRef, false, value)
}

func (s boolArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	// Fill data first
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool())
	}
	// Then set and register reference
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

type int8ArraySerializer struct {
}

func (s int8ArraySerializer) TypeId() TypeId {
	return INT8_ARRAY
}

func (s int8ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int8ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s int8ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s int8ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(int8(buf.ReadByte_())))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int8ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s int8ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int16ArraySerializer struct {
}

func (s int16ArraySerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int16ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	size := length * 2
	if size >= MaxInt32 {
		return fmt.Errorf("too long slice: %d", length)
	}
	buf.WriteLength(size)
	for i := 0; i < length; i++ {
		buf.WriteInt16(int16(value.Index(i).Int()))
	}
	return nil
}

func (s int16ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s int16ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int16ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s int16ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int32ArraySerializer struct {
}

func (s int32ArraySerializer) TypeId() TypeId {
	return INT32_ARRAY
}

func (s int32ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int32ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s int32ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s int32ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int32ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s int32ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type int64ArraySerializer struct {
}

func (s int64ArraySerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s int64ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s int64ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s int64ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64())
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int64ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s int64ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type float32ArraySerializer struct {
}

func (s float32ArraySerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s float32ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s float32ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s float32ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s float32ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s float32ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

type float64ArraySerializer struct {
}

func (s float64ArraySerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64ArraySerializer) NeedToWriteRef() bool {
	return true
}

func (s float64ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
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

func (s float64ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s float64ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64())
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s float64ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
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

func (s float64ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
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

// ============================================================================
// Legacy slice serializers - kept for backward compatibility but not used for xlang
// ============================================================================

type boolSliceSerializer struct {
}

func (s boolSliceSerializer) TypeId() TypeId {
	return BOOL_ARRAY // Use legacy type ID to avoid conflicts
}

func (s boolSliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s boolSliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
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

func (s boolSliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetBool(buf.ReadBool())
	}
	value.Set(r)
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

func (s int8SliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	v := value.Interface().([]int8)
	return writeInt8Slice(buf, v)
}

func (s int8SliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt8()))
	}
	value.Set(r)
	return nil
}

type int16SliceSerializer struct {
}

func (s int16SliceSerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s int16SliceSerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	if writeRef {
		if value.IsNil() {
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

func (s int16SliceSerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	buf := ctx.Buffer()
	if readRef {
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
	if readType {
		typeID := int32(buf.ReadVaruint32Small7())
		if typeID != int32(s.TypeId()) {
			return fmt.Errorf("type mismatch: expected %d, got %d", s.TypeId(), typeID)
		}
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int16SliceSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	value.Set(r)
	ctx.RefResolver().Reference(value)
	return nil
}

func (s int16SliceSerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	// typeInfo is already read, don't read it again
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

func (s int32SliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
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

func (s int32SliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	value.Set(r)
	return nil
}

type int64SliceSerializer struct {
}

func (s int64SliceSerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s int64SliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
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

func (s int64SliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetInt(buf.ReadInt64())
	}
	value.Set(r)
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
		if value.IsNil() {
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
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
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
	return nil
}

type float32SliceSerializer struct {
}

func (s float32SliceSerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s float32SliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
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

func (s float32SliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	value.Set(r)
	return nil
}

type float64SliceSerializer struct {
}

func (s float64SliceSerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64SliceSerializer) NeedToWriteRef() bool {
	return true
}

func (s float64SliceSerializer) Write(ctx *WriteContext, value reflect.Value) error {
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

func (s float64SliceSerializer) Read(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}
	for i := 0; i < length; i++ {
		r.Index(i).SetFloat(buf.ReadFloat64())
	}
	value.Set(r)
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
	var r reflect.Value
	switch type_.Kind() {
	case reflect.Slice:
		r = reflect.MakeSlice(type_, length, length)
	case reflect.Array:
		if length != type_.Len() {
			return fmt.Errorf("length %d does not match array type %v", length, type_)
		}
		r = reflect.New(type_).Elem()
	default:
		return fmt.Errorf("unsupported kind %v, want slice/array", type_.Kind())
	}

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
