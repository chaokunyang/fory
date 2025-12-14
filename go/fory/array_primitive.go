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
)

// boolArraySerializer handles [N]bool fixed-size arrays
type boolArraySerializer struct {
	arrayType reflect.Type
}

func (s boolArraySerializer) TypeId() TypeId {
	return BOOL_ARRAY
}

func (s boolArraySerializer) NeedToWriteRef() bool {
	return false // Arrays are value types, no ref tracking needed
}

func (s boolArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length)
	for i := 0; i < length; i++ {
		buf.WriteBool(value.Index(i).Bool())
	}
	return nil
}

func (s boolArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, BOOL_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s boolArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetBool(buf.ReadBool())
	}
	return nil
}

func (s boolArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s boolArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// int8ArraySerializer handles [N]int8 fixed-size arrays
type int8ArraySerializer struct {
	arrayType reflect.Type
}

func (s int8ArraySerializer) TypeId() TypeId {
	return INT8_ARRAY
}

func (s int8ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s int8ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length)
	for i := 0; i < length; i++ {
		buf.WriteByte_(byte(value.Index(i).Int()))
	}
	return nil
}

func (s int8ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, INT8_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int8ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetInt(int64(int8(buf.ReadByte_())))
	}
	return nil
}

func (s int8ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int8ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// int16ArraySerializer handles [N]int16 fixed-size arrays
type int16ArraySerializer struct {
	arrayType reflect.Type
}

func (s int16ArraySerializer) TypeId() TypeId {
	return INT16_ARRAY
}

func (s int16ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s int16ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length * 2)
	for i := 0; i < length; i++ {
		buf.WriteInt16(int16(value.Index(i).Int()))
	}
	return nil
}

func (s int16ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, INT16_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int16ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 2
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetInt(int64(buf.ReadInt16()))
	}
	return nil
}

func (s int16ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int16ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// int32ArraySerializer handles [N]int32 fixed-size arrays
type int32ArraySerializer struct {
	arrayType reflect.Type
}

func (s int32ArraySerializer) TypeId() TypeId {
	return INT32_ARRAY
}

func (s int32ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s int32ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length * 4)
	for i := 0; i < length; i++ {
		buf.WriteInt32(int32(value.Index(i).Int()))
	}
	return nil
}

func (s int32ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, INT32_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int32ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetInt(int64(buf.ReadInt32()))
	}
	return nil
}

func (s int32ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int32ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// int64ArraySerializer handles [N]int64 fixed-size arrays
type int64ArraySerializer struct {
	arrayType reflect.Type
}

func (s int64ArraySerializer) TypeId() TypeId {
	return INT64_ARRAY
}

func (s int64ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s int64ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length * 8)
	for i := 0; i < length; i++ {
		buf.WriteInt64(value.Index(i).Int())
	}
	return nil
}

func (s int64ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, INT64_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int64ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetInt(buf.ReadInt64())
	}
	return nil
}

func (s int64ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int64ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// float32ArraySerializer handles [N]float32 fixed-size arrays
type float32ArraySerializer struct {
	arrayType reflect.Type
}

func (s float32ArraySerializer) TypeId() TypeId {
	return FLOAT32_ARRAY
}

func (s float32ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s float32ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length * 4)
	for i := 0; i < length; i++ {
		buf.WriteFloat32(float32(value.Index(i).Float()))
	}
	return nil
}

func (s float32ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, FLOAT32_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s float32ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 4
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetFloat(float64(buf.ReadFloat32()))
	}
	return nil
}

func (s float32ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float32ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// float64ArraySerializer handles [N]float64 fixed-size arrays
type float64ArraySerializer struct {
	arrayType reflect.Type
}

func (s float64ArraySerializer) TypeId() TypeId {
	return FLOAT64_ARRAY
}

func (s float64ArraySerializer) NeedToWriteRef() bool {
	return false
}

func (s float64ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length * 8)
	for i := 0; i < length; i++ {
		buf.WriteFloat64(value.Index(i).Float())
	}
	return nil
}

func (s float64ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, FLOAT64_ARRAY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s float64ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength() / 8
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetFloat(buf.ReadFloat64())
	}
	return nil
}

func (s float64ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s float64ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}

// uint8ArraySerializer handles [N]uint8 (byte) fixed-size arrays
type uint8ArraySerializer struct {
	arrayType reflect.Type
}

func (s uint8ArraySerializer) TypeId() TypeId {
	return BINARY
}

func (s uint8ArraySerializer) NeedToWriteRef() bool {
	return false // Arrays are value types, no ref tracking needed
}

func (s uint8ArraySerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	buf := ctx.Buffer()
	length := value.Len()
	buf.WriteLength(length)
	for i := 0; i < length; i++ {
		buf.WriteByte_(byte(value.Index(i).Uint()))
	}
	return nil
}

func (s uint8ArraySerializer) Write(ctx *WriteContext, writeRef bool, writeType bool, value reflect.Value) error {
	_, err := writeArrayRefAndType(ctx, writeRef, writeType, value, BINARY)
	if err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s uint8ArraySerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	buf := ctx.Buffer()
	length := buf.ReadLength()
	if length != type_.Len() {
		return fmt.Errorf("array length %d does not match type %v", length, type_)
	}
	for i := 0; i < length; i++ {
		value.Index(i).SetUint(uint64(buf.ReadByte_()))
	}
	return nil
}

func (s uint8ArraySerializer) Read(ctx *ReadContext, readRef bool, readType bool, value reflect.Value) error {
	done, err := readArrayRefAndType(ctx, readRef, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s uint8ArraySerializer) ReadWithTypeInfo(ctx *ReadContext, readRef bool, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, readRef, false, value)
}
