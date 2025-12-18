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
)

// ============================================================================
// Optimized map serializers for common types
// ============================================================================

// writeMapStringString writes map[string]string using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapStringString(buf *ByteBuffer, m map[string]string) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	// WriteData chunks of entries
	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		// Don't set DECL_TYPE flags - write type info for generic reader compatibility
		buf.WriteUint8(0)
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(STRING)) // key type
		buf.WriteVaruint32Small7(uint32(STRING)) // value type

		// WriteData chunk entries
		count := 0
		for k, v := range m {
			writeString(buf, k)
			writeString(buf, v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapStringString reads map[string]string using chunk protocol
func readMapStringString(buf *ByteBuffer) map[string]string {
	size := int(buf.ReadVaruint32())
	result := make(map[string]string, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()

		// Handle null key/value cases
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			// Skip null entries (strings can't be null in Go)
			size--
			continue
		}

		// ReadData chunk size
		chunkSize := int(buf.ReadUint8())

		// Read type info if not DECL_TYPE
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7() // skip key type
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7() // skip value type
		}

		// ReadData chunk entries
		for i := 0; i < chunkSize && size > 0; i++ {
			k := readString(buf)
			v := readString(buf)
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapStringInt64 writes map[string]int64 using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapStringInt64(buf *ByteBuffer, m map[string]int64) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(STRING))   // key type
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // value type

		count := 0
		for k, v := range m {
			writeString(buf, k)
			buf.WriteVarint64(v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapStringInt64 reads map[string]int64 using chunk protocol
func readMapStringInt64(buf *ByteBuffer) map[string]int64 {
	size := int(buf.ReadVaruint32())
	result := make(map[string]int64, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := readString(buf)
			v := buf.ReadVarint64()
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapStringInt writes map[string]int using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapStringInt(buf *ByteBuffer, m map[string]int) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(STRING))   // key type
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // value type (int serialized as varint64)

		count := 0
		for k, v := range m {
			writeString(buf, k)
			buf.WriteVarint64(int64(v))
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapStringInt reads map[string]int using chunk protocol
func readMapStringInt(buf *ByteBuffer) map[string]int {
	size := int(buf.ReadVaruint32())
	result := make(map[string]int, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := readString(buf)
			v := buf.ReadVarint64()
			result[k] = int(v)
			size--
		}
	}
	return result
}

// writeMapStringFloat64 writes map[string]float64 using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapStringFloat64(buf *ByteBuffer, m map[string]float64) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(STRING)) // key type
		buf.WriteVaruint32Small7(uint32(DOUBLE)) // value type

		count := 0
		for k, v := range m {
			writeString(buf, k)
			buf.WriteFloat64(v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapStringFloat64 reads map[string]float64 using chunk protocol
func readMapStringFloat64(buf *ByteBuffer) map[string]float64 {
	size := int(buf.ReadVaruint32())
	result := make(map[string]float64, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := readString(buf)
			v := buf.ReadFloat64()
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapStringBool writes map[string]bool using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapStringBool(buf *ByteBuffer, m map[string]bool) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(STRING)) // key type
		buf.WriteVaruint32Small7(uint32(BOOL))   // value type

		count := 0
		for k, v := range m {
			writeString(buf, k)
			buf.WriteBool(v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapStringBool reads map[string]bool using chunk protocol
func readMapStringBool(buf *ByteBuffer) map[string]bool {
	size := int(buf.ReadVaruint32())
	result := make(map[string]bool, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())

		// Read type info (written by writeMapStringBool)
		keyDeclType := (chunkHeader & KEY_DECL_TYPE) != 0
		valDeclType := (chunkHeader & VALUE_DECL_TYPE) != 0
		if !keyDeclType {
			buf.ReadVaruint32Small7() // skip key type info
		}
		if !valDeclType {
			buf.ReadVaruint32Small7() // skip value type info
		}

		for i := 0; i < chunkSize && size > 0; i++ {
			k := readString(buf)
			v := buf.ReadBool()
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapInt32Int32 writes map[int32]int32 using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapInt32Int32(buf *ByteBuffer, m map[int32]int32) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(VAR_INT32)) // key type
		buf.WriteVaruint32Small7(uint32(VAR_INT32)) // value type

		count := 0
		for k, v := range m {
			buf.WriteVarint32(k)
			buf.WriteVarint32(v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapInt32Int32 reads map[int32]int32 using chunk protocol
func readMapInt32Int32(buf *ByteBuffer) map[int32]int32 {
	size := int(buf.ReadVaruint32())
	result := make(map[int32]int32, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := buf.ReadVarint32()
			v := buf.ReadVarint32()
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapInt64Int64 writes map[int64]int64 using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapInt64Int64(buf *ByteBuffer, m map[int64]int64) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // key type
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // value type

		count := 0
		for k, v := range m {
			buf.WriteVarint64(k)
			buf.WriteVarint64(v)
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapInt64Int64 reads map[int64]int64 using chunk protocol
func readMapInt64Int64(buf *ByteBuffer) map[int64]int64 {
	size := int(buf.ReadVaruint32())
	result := make(map[int64]int64, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := buf.ReadVarint64()
			v := buf.ReadVarint64()
			result[k] = v
			size--
		}
	}
	return result
}

// writeMapIntInt writes map[int]int using chunk protocol
// Writes type info for generic deserialization compatibility
func writeMapIntInt(buf *ByteBuffer, m map[int]int) {
	length := len(m)
	buf.WriteVaruint32(uint32(length))
	if length == 0 {
		return
	}

	remaining := length
	for remaining > 0 {
		chunkSize := remaining
		if chunkSize > MAX_CHUNK_SIZE {
			chunkSize = MAX_CHUNK_SIZE
		}

		buf.WriteUint8(0) // no DECL_TYPE flags
		buf.WriteUint8(uint8(chunkSize))
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // key type (int serialized as varint64)
		buf.WriteVaruint32Small7(uint32(VAR_INT64)) // value type

		count := 0
		for k, v := range m {
			buf.WriteVarint64(int64(k))
			buf.WriteVarint64(int64(v))
			count++
			if count >= chunkSize {
				break
			}
		}
		remaining -= chunkSize
	}
}

// readMapIntInt reads map[int]int using chunk protocol
func readMapIntInt(buf *ByteBuffer) map[int]int {
	size := int(buf.ReadVaruint32())
	result := make(map[int]int, size)
	if size == 0 {
		return result
	}

	for size > 0 {
		chunkHeader := buf.ReadUint8()
		keyHasNull := (chunkHeader & KEY_HAS_NULL) != 0
		valueHasNull := (chunkHeader & VALUE_HAS_NULL) != 0

		if keyHasNull || valueHasNull {
			size--
			continue
		}

		chunkSize := int(buf.ReadUint8())
		if (chunkHeader & KEY_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		if (chunkHeader & VALUE_DECL_TYPE) == 0 {
			buf.ReadVaruint32Small7()
		}
		for i := 0; i < chunkSize && size > 0; i++ {
			k := buf.ReadVarint64()
			v := buf.ReadVarint64()
			result[int(k)] = int(v)
			size--
		}
	}
	return result
}

// ============================================================================
// Dedicated map serializers
// ============================================================================

type stringStringMapSerializer struct{}

func (s stringStringMapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapStringString(ctx.buffer, value.Interface().(map[string]string))
	return nil
}

func (s stringStringMapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s stringStringMapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapStringString(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s stringStringMapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringStringMapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type stringInt64MapSerializer struct{}

func (s stringInt64MapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapStringInt64(ctx.buffer, value.Interface().(map[string]int64))
	return nil
}

func (s stringInt64MapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s stringInt64MapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapStringInt64(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s stringInt64MapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringInt64MapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type stringIntMapSerializer struct{}

func (s stringIntMapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapStringInt(ctx.buffer, value.Interface().(map[string]int))
	return nil
}

func (s stringIntMapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s stringIntMapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapStringInt(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s stringIntMapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringIntMapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type stringFloat64MapSerializer struct{}

func (s stringFloat64MapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapStringFloat64(ctx.buffer, value.Interface().(map[string]float64))
	return nil
}

func (s stringFloat64MapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s stringFloat64MapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapStringFloat64(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s stringFloat64MapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringFloat64MapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type stringBoolMapSerializer struct{}

func (s stringBoolMapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapStringBool(ctx.buffer, value.Interface().(map[string]bool))
	return nil
}

func (s stringBoolMapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s stringBoolMapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapStringBool(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s stringBoolMapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringBoolMapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type int32Int32MapSerializer struct{}

func (s int32Int32MapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapInt32Int32(ctx.buffer, value.Interface().(map[int32]int32))
	return nil
}

func (s int32Int32MapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int32Int32MapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapInt32Int32(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s int32Int32MapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int32Int32MapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type int64Int64MapSerializer struct{}

func (s int64Int64MapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapInt64Int64(ctx.buffer, value.Interface().(map[int64]int64))
	return nil
}

func (s int64Int64MapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s int64Int64MapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapInt64Int64(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s int64Int64MapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s int64Int64MapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

type intIntMapSerializer struct{}

func (s intIntMapSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	writeMapIntInt(ctx.buffer, value.Interface().(map[int]int))
	return nil
}

func (s intIntMapSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	done, err := writeMapRefAndType(ctx, refMode, writeType, value)
	if done || err != nil {
		return err
	}
	return s.WriteData(ctx, value)
}

func (s intIntMapSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	if value.IsNil() {
		value.Set(reflect.MakeMap(type_))
	}
	ctx.RefResolver().Reference(value)
	result := readMapIntInt(ctx.buffer)
	value.Set(reflect.ValueOf(result))
	return nil
}

func (s intIntMapSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	done, err := readMapRefAndType(ctx, refMode, readType, value)
	if done || err != nil {
		return err
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s intIntMapSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
