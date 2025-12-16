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
	"unicode/utf16"
)

// Encoding type constants
const (
	encodingLatin1  = iota // Latin1/ISO-8859-1 encoding
	encodingUTF16LE        // UTF-16 Little Endian encoding
	encodingUTF8           // UTF-8 encoding (default)
)

// writeString implements string serialization
// Uses Latin1 encoding for ASCII strings (matching Java behavior), UTF-8 for others
func writeString(buf *ByteBuffer, value string) error {
	data := unsafeGetBytes(value)
	var encoding uint64
	if isLatin1(value) {
		encoding = encodingLatin1
	} else {
		encoding = encodingUTF8
	}
	header := (uint64(len(data)) << 2) | encoding
	buf.WriteVaruint36Small(header)
	buf.WriteBinary(data)
	return nil
}

// readString implements string deserialization with encoding parsing
func readString(buf *ByteBuffer) string {
	header := buf.ReadVaruint36Small()
	size := header >> 2       // Extract byte count
	encoding := header & 0b11 // Extract encoding type

	switch encoding {
	case encodingLatin1:
		return readLatin1(buf, int(size))
	case encodingUTF16LE:
		// For UTF16LE, size is byte count, need to convert to char count
		return readUTF16LE(buf, int(size))
	case encodingUTF8:
		return readUTF8(buf, int(size))
	default:
		panic(fmt.Sprintf("invalid string encoding: %d", encoding))
	}
}

// Encoding detection helper functions
// isLatin1 checks if a string contains only ASCII characters (0-127)
// For xlang compatibility with Java, we use Latin1 encoding for pure ASCII strings
func isLatin1(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] > 127 {
			return false
		}
	}
	return true
}

func tryUTF16LE(s string) ([]byte, bool) {
	runes := []rune(s)
	utf16Runes := utf16.Encode(runes)

	// Check for surrogate pairs (indicates complex Unicode)
	hasSurrogate := false
	for _, r := range utf16Runes {
		if r >= 0xD800 && r <= 0xDFFF {
			hasSurrogate = true
			break
		}
	}

	if hasSurrogate {
		return nil, false
	}

	// Convert to Little Endian byte order
	buf := make([]byte, 2*len(utf16Runes))
	for i, r := range utf16Runes {
		buf[2*i] = byte(r)        // Low byte
		buf[2*i+1] = byte(r >> 8) // High byte
	}
	return buf, true
}

// Specific encoding write methods
func writeLatin1(buf *ByteBuffer, s string) error {
	// For Latin1 encoding, each rune becomes one byte
	runes := []rune(s)
	length := len(runes)
	header := (uint64(length) << 2) | encodingLatin1 // Pack byte count and encoding

	buf.WriteVaruint36Small(header)
	// Convert runes to Latin1 bytes
	data := make([]byte, length)
	for i, r := range runes {
		data[i] = byte(r)
	}
	buf.WriteBinary(data)
	return nil
}

func writeUTF16LE(buf *ByteBuffer, data []byte) error {
	length := len(data) // Byte count (not character count)
	header := (uint64(length) << 2) | encodingUTF16LE

	buf.WriteVaruint36Small(header)
	buf.WriteBinary(data)
	return nil
}

func writeUTF8(buf *ByteBuffer, s string) error {
	data := unsafeGetBytes(s)
	header := (uint64(len(data)) << 2) | encodingUTF8

	buf.WriteVaruint36Small(header)
	buf.WriteBinary(data)
	return nil
}

// Specific encoding read methods
func readLatin1(buf *ByteBuffer, size int) string {
	data := buf.ReadBinary(size)
	// Latin1 bytes need to be converted to UTF-8
	// Each Latin1 byte is a single Unicode code point (0-255)
	runes := make([]rune, size)
	for i, b := range data {
		runes[i] = rune(b)
	}
	return string(runes)
}

func readUTF16LE(buf *ByteBuffer, byteCount int) string {
	data := buf.ReadBinary(byteCount)

	// Reconstruct UTF-16 code units
	charCount := byteCount / 2
	u16s := make([]uint16, charCount)
	for i := 0; i < byteCount; i += 2 {
		u16s[i/2] = uint16(data[i]) | uint16(data[i+1])<<8
	}

	return string(utf16.Decode(u16s))
}

func readUTF8(buf *ByteBuffer, size int) string {
	data := buf.ReadBinary(size)
	return string(data) // Direct UTF-8 conversion
}

// WriteString provides public API for zero-reflection string serialization
// This method is specifically designed for code generation to avoid reflection overhead
func WriteString(buf *ByteBuffer, value string) error {
	return writeString(buf, value)
}

// ReadString provides public API for zero-reflection string deserialization
// This method is specifically designed for code generation to avoid reflection overhead
func ReadString(buf *ByteBuffer) string {
	return readString(buf)
}

// ============================================================================
// String Serializers - implement unified Serializer interface
// ============================================================================

// stringSerializer handles string type
type stringSerializer struct{}

var globalStringSerializer = stringSerializer{}

func (s stringSerializer) TypeId() TypeId       { return STRING }
func (s stringSerializer) NeedToWriteRef() bool { return false }

func (s stringSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	return writeString(ctx.buffer, value.String())
}

func (s stringSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		// String is non-primitive, needs ref flag
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		ctx.buffer.WriteVaruint32Small7(uint32(STRING))
	}
	return s.WriteData(ctx, value)
}

func (s stringSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	str := readString(ctx.buffer)
	value.SetString(str)
	return nil
}

func (s stringSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		// String is non-primitive, needs ref flag
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			value.SetString("")
			return nil
		}
	}
	if readType {
		_ = ctx.buffer.ReadVaruint32Small7()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s stringSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}

// ptrToStringSerializer serializes a pointer to string
type ptrToStringSerializer struct{}

func (s ptrToStringSerializer) TypeId() TypeId       { return -STRING }
func (s ptrToStringSerializer) NeedToWriteRef() bool { return false }

func (s ptrToStringSerializer) WriteData(ctx *WriteContext, value reflect.Value) error {
	str := value.Interface().(*string)
	return writeString(ctx.buffer, *str)
}

func (s ptrToStringSerializer) Write(ctx *WriteContext, refMode RefMode, writeType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		if value.IsNil() {
			ctx.buffer.WriteInt8(NullFlag)
			return nil
		}
		ctx.buffer.WriteInt8(NotNullValueFlag)
	}
	if writeType {
		ctx.buffer.WriteVaruint32Small7(uint32(STRING))
	}
	return s.WriteData(ctx, value)
}

func (s ptrToStringSerializer) ReadData(ctx *ReadContext, type_ reflect.Type, value reflect.Value) error {
	str := readString(ctx.buffer)
	ptr := new(string)
	*ptr = str
	value.Set(reflect.ValueOf(ptr))
	return nil
}

func (s ptrToStringSerializer) Read(ctx *ReadContext, refMode RefMode, readType bool, value reflect.Value) error {
	if refMode != RefModeNone {
		refFlag := ctx.buffer.ReadInt8()
		if refFlag == NullFlag {
			return nil
		}
	}
	if readType {
		_ = ctx.buffer.ReadVaruint32()
	}
	return s.ReadData(ctx, value.Type(), value)
}

func (s ptrToStringSerializer) ReadWithTypeInfo(ctx *ReadContext, refMode RefMode, typeInfo *TypeInfo, value reflect.Value) error {
	return s.Read(ctx, refMode, false, value)
}
