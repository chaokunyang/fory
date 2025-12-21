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
	"encoding/binary"
	"fmt"
	"io"
	"math"
)

type ByteBuffer struct {
	io.Writer
	io.Reader
	writerIndex int
	readerIndex int
	data        []byte
}

func NewByteBuffer(data []byte) *ByteBuffer {
	return &ByteBuffer{data: data}
}

func (b *ByteBuffer) grow(n int) {
	l := b.writerIndex
	if l+n < len(b.data) {
		return
	}
	if l+n < cap(b.data) {
		b.data = b.data[:cap(b.data)]
	} else {
		newBuf := make([]byte, 2*(l+n), 2*(l+n))
		copy(newBuf, b.data)
		b.data = newBuf
	}
}

func (b *ByteBuffer) WriteBool(value bool) {
	b.grow(1)
	if value {
		b.data[b.writerIndex] = 1
	} else {
		b.data[b.writerIndex] = 0
	}
	b.writerIndex++
}

func (b *ByteBuffer) WriteByte(value byte) error {
	b.grow(1)
	b.data[b.writerIndex] = value
	b.writerIndex++
	return nil
}

func (b *ByteBuffer) WriteByte_(value byte) {
	b.grow(1)
	b.data[b.writerIndex] = value
	b.writerIndex++
}

func (b *ByteBuffer) WriteInt8(value int8) {
	b.grow(1)
	b.data[b.writerIndex] = byte(value)
	b.writerIndex++
}

func (b *ByteBuffer) WriteUint8(value uint8) {
	b.grow(1)
	b.data[b.writerIndex] = value
	b.writerIndex++
}

func (b *ByteBuffer) WriteInt16(value int16) {
	b.grow(2)
	binary.LittleEndian.PutUint16(b.data[b.writerIndex:], uint16(value))
	b.writerIndex += 2
}

func (b *ByteBuffer) WriteInt32(value int32) {
	b.grow(4)
	binary.LittleEndian.PutUint32(b.data[b.writerIndex:], uint32(value))
	b.writerIndex += 4
}

func (b *ByteBuffer) WriteLength(value int) {
	b.grow(4)
	if value >= MaxInt32 {
		panic(fmt.Errorf("too long: %d", value))
	}
	b.WriteVaruint32(uint32(value))
}

func (b *ByteBuffer) ReadLength(err *Error) int {
	return int(b.ReadVaruint32(err))
}

func (b *ByteBuffer) WriteInt64(value int64) {
	b.grow(8)
	binary.LittleEndian.PutUint64(b.data[b.writerIndex:], uint64(value))
	b.writerIndex += 8
}

func (b *ByteBuffer) WriteFloat32(value float32) {
	b.grow(4)
	binary.LittleEndian.PutUint32(b.data[b.writerIndex:], Float32bits(value))
	b.writerIndex += 4
}

func (b *ByteBuffer) WriteFloat64(value float64) {
	b.grow(8)
	binary.LittleEndian.PutUint64(b.data[b.writerIndex:], Float64bits(value))
	b.writerIndex += 8
}

func (b *ByteBuffer) Write(p []byte) (n int, err error) {
	b.grow(len(p))
	l := copy(b.data[b.writerIndex:], p)
	b.writerIndex += len(p)
	return l, nil
}

func (b *ByteBuffer) WriteBinary(p []byte) {
	b.grow(len(p))
	l := copy(b.data[b.writerIndex:], p)
	if l != len(p) {
		panic(fmt.Errorf("should write %d bytes, but written %d bytes", len(p), l))
	}
	b.writerIndex += len(p)
}

// ReadBool reads a bool and sets error on bounds violation
func (b *ByteBuffer) ReadBool(err *Error) bool {
	if b.readerIndex+1 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return false
	}
	v := b.data[b.readerIndex]
	b.readerIndex++
	return v != 0
}

// ReadByte reads a byte and sets error on bounds violation
func (b *ByteBuffer) ReadByte(err *Error) byte {
	if b.readerIndex+1 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return 0
	}
	v := b.data[b.readerIndex]
	b.readerIndex++
	return v
}

// ReadInt8 reads an int8 and sets error on bounds violation
func (b *ByteBuffer) ReadInt8(err *Error) int8 {
	if b.readerIndex+1 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return 0
	}
	v := int8(b.data[b.readerIndex])
	b.readerIndex++
	return v
}

// ReadInt16 reads an int16 and sets error on bounds violation
func (b *ByteBuffer) ReadInt16(err *Error) int16 {
	if b.readerIndex+2 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 2, len(b.data))
		return 0
	}
	v := int16(binary.LittleEndian.Uint16(b.data[b.readerIndex:]))
	b.readerIndex += 2
	return v
}

// ReadUint32 reads a uint32 and sets error on bounds violation
func (b *ByteBuffer) ReadUint32(err *Error) uint32 {
	if b.readerIndex+4 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 4, len(b.data))
		return 0
	}
	i := binary.LittleEndian.Uint32(b.data[b.readerIndex:])
	b.readerIndex += 4
	return i
}

// ReadUint64 reads a uint64 and sets error on bounds violation
func (b *ByteBuffer) ReadUint64(err *Error) uint64 {
	if b.readerIndex+8 > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 8, len(b.data))
		return 0
	}
	i := binary.LittleEndian.Uint64(b.data[b.readerIndex:])
	b.readerIndex += 8
	return i
}

// ReadInt32 reads an int32 and sets error on bounds violation
func (b *ByteBuffer) ReadInt32(err *Error) int32 {
	return int32(b.ReadUint32(err))
}

// ReadInt64 reads an int64 and sets error on bounds violation
func (b *ByteBuffer) ReadInt64(err *Error) int64 {
	return int64(b.ReadUint64(err))
}

// ReadFloat32 reads a float32 and sets error on bounds violation
func (b *ByteBuffer) ReadFloat32(err *Error) float32 {
	return Float32frombits(b.ReadUint32(err))
}

// ReadFloat64 reads a float64 and sets error on bounds violation
func (b *ByteBuffer) ReadFloat64(err *Error) float64 {
	return Float64frombits(b.ReadUint64(err))
}

func (b *ByteBuffer) Read(p []byte) (n int, err error) {
	copied := copy(p, b.data[b.readerIndex:])
	b.readerIndex += copied
	return copied, nil
}

// ReadBinary reads n bytes and sets error on bounds violation
func (b *ByteBuffer) ReadBinary(length int, err *Error) []byte {
	if b.readerIndex+length > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, length, len(b.data))
		return nil
	}
	v := b.data[b.readerIndex : b.readerIndex+length]
	b.readerIndex += length
	return v
}

func (b *ByteBuffer) GetData() []byte {
	return b.data
}

func (b *ByteBuffer) GetByteSlice(start, end int) []byte {
	return b.data[start:end]
}

func (b *ByteBuffer) Slice(start, length int) *ByteBuffer {
	return NewByteBuffer(b.data[start : start+length])
}

func (b *ByteBuffer) WriterIndex() int {
	return b.writerIndex
}

// Bytes returns all written bytes from the buffer (from 0 to writerIndex).
func (b *ByteBuffer) Bytes() []byte {
	return b.GetByteSlice(0, b.writerIndex)
}

func (b *ByteBuffer) SetWriterIndex(index int) {
	b.writerIndex = index
}

func (b *ByteBuffer) ReaderIndex() int {
	return b.readerIndex
}

func (b *ByteBuffer) SetReaderIndex(index int) {
	b.readerIndex = index
}

func (b *ByteBuffer) Reset() {
	b.readerIndex = 0
	b.writerIndex = 0
	// Keep the underlying buffer if it's reasonable sized to reduce allocations
	// Only nil it out if we want to release memory
	if cap(b.data) > 64*1024 {
		b.data = nil
	}
	// Note: We keep b.data as-is (with its current length) to avoid issues
	// with grow() needing to expand the slice on first write
}

// Reserve ensures buffer has at least n bytes available for writing from current position.
// Call this before multiple unsafe writes to avoid repeated grow() calls.
func (b *ByteBuffer) Reserve(n int) {
	needed := b.writerIndex + n
	if needed <= len(b.data) {
		return // Already have enough space
	}
	// Need to expand - calculate new size
	if needed <= cap(b.data) {
		b.data = b.data[:cap(b.data)]
	} else {
		newCap := 2 * needed
		newBuf := make([]byte, newCap, newCap)
		copy(newBuf, b.data)
		b.data = newBuf
	}
}

// UnsafeWriteVarint32 writes a varint32 without grow check.
// Caller must have called Reserve(5) beforehand.
func (b *ByteBuffer) UnsafeWriteVarint32(value int32) int8 {
	u := uint32((value << 1) ^ (value >> 31))
	return b.UnsafeWriteVaruint32(u)
}

// UnsafeWriteVaruint32 writes a varuint32 without grow check.
// Caller must have called Reserve(5) beforehand.
func (b *ByteBuffer) UnsafeWriteVaruint32(value uint32) int8 {
	if value>>7 == 0 {
		b.data[b.writerIndex] = byte(value)
		b.writerIndex++
		return 1
	}
	if value>>14 == 0 {
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value >> 7)
		b.writerIndex += 2
		return 2
	}
	if value>>21 == 0 {
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
		b.data[b.writerIndex+2] = byte(value >> 14)
		b.writerIndex += 3
		return 3
	}
	if value>>28 == 0 {
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
		b.data[b.writerIndex+2] = byte(value>>14 | 0x80)
		b.data[b.writerIndex+3] = byte(value >> 21)
		b.writerIndex += 4
		return 4
	}
	b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
	b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
	b.data[b.writerIndex+2] = byte(value>>14 | 0x80)
	b.data[b.writerIndex+3] = byte(value>>21 | 0x80)
	b.data[b.writerIndex+4] = byte(value >> 28)
	b.writerIndex += 5
	return 5
}

// UnsafeWriteByte writes a single byte without grow check.
func (b *ByteBuffer) UnsafeWriteByte(v byte) {
	b.data[b.writerIndex] = v
	b.writerIndex++
}

// UnsafeWriteInt16 writes an int16 without grow check.
func (b *ByteBuffer) UnsafeWriteInt16(v int16) {
	binary.LittleEndian.PutUint16(b.data[b.writerIndex:], uint16(v))
	b.writerIndex += 2
}

// UnsafeWriteFloat32 writes a float32 without grow check.
func (b *ByteBuffer) UnsafeWriteFloat32(v float32) {
	binary.LittleEndian.PutUint32(b.data[b.writerIndex:], math.Float32bits(v))
	b.writerIndex += 4
}

// UnsafeWriteFloat64 writes a float64 without grow check.
func (b *ByteBuffer) UnsafeWriteFloat64(v float64) {
	binary.LittleEndian.PutUint64(b.data[b.writerIndex:], math.Float64bits(v))
	b.writerIndex += 8
}

// UnsafeWriteBool writes a bool without grow check.
func (b *ByteBuffer) UnsafeWriteBool(v bool) {
	if v {
		b.data[b.writerIndex] = 1
	} else {
		b.data[b.writerIndex] = 0
	}
	b.writerIndex++
}

// UnsafeWriteVarint64 writes a varint64 without grow check.
// Caller must have called Reserve(10) beforehand.
func (b *ByteBuffer) UnsafeWriteVarint64(value int64) {
	u := uint64((value << 1) ^ (value >> 63))
	for u >= 0x80 {
		b.data[b.writerIndex] = byte(u) | 0x80
		b.writerIndex++
		u >>= 7
	}
	b.data[b.writerIndex] = byte(u)
	b.writerIndex++
}

func (b *ByteBuffer) PutInt32(index int, value int32) {
	b.grow(4)
	binary.LittleEndian.PutUint32(b.data[index:], uint32(value))
}

// WriteVaruint32 writes a 1-5 byte positive int (no zigzag encoding), returns the number of bytes written.
// Use this for lengths, type IDs, and other non-negative values.
func (b *ByteBuffer) WriteVaruint32(value uint32) int8 {
	if value>>7 == 0 {
		b.grow(1)
		b.data[b.writerIndex] = byte(value)
		b.writerIndex++
		return 1
	}
	if value>>14 == 0 {
		b.grow(2)
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value >> 7)
		b.writerIndex += 2
		return 2
	}
	if value>>21 == 0 {
		b.grow(3)
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
		b.data[b.writerIndex+2] = byte(value >> 14)
		b.writerIndex += 3
		return 3
	}
	if value>>28 == 0 {
		b.grow(4)
		b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
		b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
		b.data[b.writerIndex+2] = byte(value>>14 | 0x80)
		b.data[b.writerIndex+3] = byte(value >> 21)
		b.writerIndex += 4
		return 4
	}
	b.grow(5)
	b.data[b.writerIndex] = byte((value & 0x7F) | 0x80)
	b.data[b.writerIndex+1] = byte(value>>7 | 0x80)
	b.data[b.writerIndex+2] = byte(value>>14 | 0x80)
	b.data[b.writerIndex+3] = byte(value>>21 | 0x80)
	b.data[b.writerIndex+4] = byte(value >> 28)
	b.writerIndex += 5
	return 5
}

type BufferObject interface {
	TotalBytes() int
	WriteTo(buf *ByteBuffer)
	ToBuffer() *ByteBuffer
}

// WriteVarint64 writes the zig-zag encoded varint (compatible with Java's writeVarint64).
func (b *ByteBuffer) WriteVarint64(value int64) {
	u := uint64((value << 1) ^ (value >> 63))
	b.WriteVaruint64(u)
}

// WriteVaruint64 writes to unsigned varint (up to 9 bytes)
func (b *ByteBuffer) WriteVaruint64(value uint64) {
	b.grow(9)
	offset := b.writerIndex
	data := b.data[offset : offset+9]

	i := 0
	for i < 8 && value >= 0x80 {
		data[i] = byte(value&0x7F) | 0x80
		value >>= 7
		i++
	}
	data[i] = byte(value)
	i++
	b.writerIndex += i
}

// WriteVaruint36Small writes a varint optimized for small values (up to 36 bits)
// Used for string headers: (length << 2) | encoding
func (b *ByteBuffer) WriteVaruint36Small(value uint64) {
	b.grow(5)
	offset := b.writerIndex
	data := b.data[offset:]

	if value < 0x80 {
		data[0] = byte(value)
		b.writerIndex += 1
	} else if value < 0x4000 {
		data[0] = byte(value&0x7F) | 0x80
		data[1] = byte(value >> 7)
		b.writerIndex += 2
	} else if value < 0x200000 {
		data[0] = byte(value&0x7F) | 0x80
		data[1] = byte((value>>7)&0x7F) | 0x80
		data[2] = byte(value >> 14)
		b.writerIndex += 3
	} else if value < 0x10000000 {
		data[0] = byte(value&0x7F) | 0x80
		data[1] = byte((value>>7)&0x7F) | 0x80
		data[2] = byte((value>>14)&0x7F) | 0x80
		data[3] = byte(value >> 21)
		b.writerIndex += 4
	} else {
		data[0] = byte(value&0x7F) | 0x80
		data[1] = byte((value>>7)&0x7F) | 0x80
		data[2] = byte((value>>14)&0x7F) | 0x80
		data[3] = byte((value>>21)&0x7F) | 0x80
		data[4] = byte(value >> 28)
		b.writerIndex += 5
	}
}

// ReadVaruint36Small reads a varint optimized for small values (up to 36 bits)
// Used for string headers: (length << 2) | encoding
func (b *ByteBuffer) ReadVaruint36Small(err *Error) uint64 {
	if b.remaining() >= 8 {
		return b.readVaruint36SmallFast()
	}
	return b.readVaruint36SmallSlow(err)
}

func (b *ByteBuffer) readVaruint36SmallFast() uint64 {
	data := b.data[b.readerIndex:]
	bulk := uint64(data[0]) | uint64(data[1])<<8 | uint64(data[2])<<16 | uint64(data[3])<<24 |
		uint64(data[4])<<32 | uint64(data[5])<<40 | uint64(data[6])<<48 | uint64(data[7])<<56

	result := bulk & 0x7F
	readLen := 1

	if (bulk & 0x80) != 0 {
		readLen = 2
		result |= (bulk >> 1) & 0x3F80
		if (bulk & 0x8000) != 0 {
			readLen = 3
			result |= (bulk >> 2) & 0x1FC000
			if (bulk & 0x800000) != 0 {
				readLen = 4
				result |= (bulk >> 3) & 0xFE00000
				if (bulk & 0x80000000) != 0 {
					readLen = 5
					result |= (bulk >> 4) & 0xFF0000000
				}
			}
		}
	}
	b.readerIndex += readLen
	return result
}

func (b *ByteBuffer) readVaruint36SmallSlow(err *Error) uint64 {
	var result uint64
	var shift uint

	for b.readerIndex < len(b.data) {
		byteVal := b.data[b.readerIndex]
		b.readerIndex++
		result |= uint64(byteVal&0x7F) << shift
		if (byteVal & 0x80) == 0 {
			return result
		}
		shift += 7
		if shift >= 36 {
			*err = DeserializationError("varuint36small overflow")
			return 0
		}
	}
	*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
	return 0
}

// ReadVarint64 reads the varint encoded with zig-zag (compatible with Java's readVarint64).
func (b *ByteBuffer) ReadVarint64(err *Error) int64 {
	u := b.ReadVaruint64(err)
	v := int64(u >> 1)
	if u&1 != 0 {
		v = ^v
	}
	return v
}

// ReadVaruint64 reads unsigned varint
func (b *ByteBuffer) ReadVaruint64(err *Error) uint64 {
	if b.remaining() >= 9 {
		return b.readVaruint64Fast()
	}
	return b.readVaruint64Slow(err)
}

// Fast path (when the remaining bytes are sufficient)
func (b *ByteBuffer) readVaruint64Fast() uint64 {
	data := b.data[b.readerIndex:]
	var result uint64
	var readLength int

	b0 := data[0]
	result = uint64(b0 & 0x7F)
	if b0 < 0x80 {
		readLength = 1
	} else {
		b1 := data[1]
		result |= uint64(b1&0x7F) << 7
		if b1 < 0x80 {
			readLength = 2
		} else {
			b2 := data[2]
			result |= uint64(b2&0x7F) << 14
			if b2 < 0x80 {
				readLength = 3
			} else {
				b3 := data[3]
				result |= uint64(b3&0x7F) << 21
				if b3 < 0x80 {
					readLength = 4
				} else {
					b4 := data[4]
					result |= uint64(b4&0x7F) << 28
					if b4 < 0x80 {
						readLength = 5
					} else {
						b5 := data[5]
						result |= uint64(b5&0x7F) << 35
						if b5 < 0x80 {
							readLength = 6
						} else {
							b6 := data[6]
							result |= uint64(b6&0x7F) << 42
							if b6 < 0x80 {
								readLength = 7
							} else {
								b7 := data[7]
								result |= uint64(b7&0x7F) << 49
								if b7 < 0x80 {
									readLength = 8
								} else {
									b8 := data[8]
									result |= uint64(b8) << 56
									readLength = 9
								}
							}
						}
					}
				}
			}
		}
	}
	b.readerIndex += readLength
	return result
}

// Slow path (read byte by byte)
func (b *ByteBuffer) readVaruint64Slow(err *Error) uint64 {
	var result uint64
	var shift uint
	for {
		if b.readerIndex >= len(b.data) {
			*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
			return 0
		}
		byteVal := b.data[b.readerIndex]
		b.readerIndex++
		result |= (uint64(byteVal) & 0x7F) << shift
		if byteVal < 0x80 {
			break
		}
		shift += 7
		if shift >= 64 {
			*err = DeserializationError("varuint64 overflow")
			return 0
		}
	}
	return result
}

// Auxiliary function
func (b *ByteBuffer) remaining() int {
	return len(b.data) - b.readerIndex
}

// ReadUint8 reads a uint8 and sets error on bounds violation
func (b *ByteBuffer) ReadUint8(err *Error) uint8 {
	if b.readerIndex >= len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return 0
	}
	v := b.data[b.readerIndex]
	b.readerIndex++
	return v
}

// WriteVarint32 writes a signed int32 using zigzag encoding (compatible with Java's writeVarint32).
func (b *ByteBuffer) WriteVarint32(value int32) int8 {
	u := uint32((value << 1) ^ (value >> 31))
	return b.WriteVaruint32(u)
}

// ReadVarint32 reads a signed int32 using zigzag decoding (compatible with Java's readVarint32).
func (b *ByteBuffer) ReadVarint32(err *Error) int32 {
	u := b.ReadVaruint32(err)
	v := int32(u >> 1)
	if u&1 != 0 {
		v = ^v
	}
	return v
}

// UnsafeReadVarint32 reads a varint32 without bounds checking.
// Caller must ensure remaining() >= 5 before calling.
func (b *ByteBuffer) UnsafeReadVarint32() int32 {
	u := b.readVaruint32Fast()
	v := int32(u >> 1)
	if u&1 != 0 {
		v = ^v
	}
	return v
}

// UnsafeReadVarint64 reads a varint64 without bounds checking.
// Caller must ensure remaining() >= 10 before calling.
func (b *ByteBuffer) UnsafeReadVarint64() int64 {
	u := b.readVaruint64Fast()
	v := int64(u >> 1)
	if u&1 != 0 {
		v = ^v
	}
	return v
}

// ReadVaruint32 reads a varuint32 and sets error on bounds violation
func (b *ByteBuffer) ReadVaruint32(err *Error) uint32 {
	if b.remaining() >= 5 {
		return b.readVaruint32Fast()
	}
	return b.readVaruint32Slow(err)
}

// Fast path reading (when the remaining bytes are sufficient)
func (b *ByteBuffer) readVaruint32Fast() uint32 {
	data := b.data[b.readerIndex:]
	var result uint32
	var readLength int

	b0 := data[0]
	result = uint32(b0 & 0x7F)
	if b0 < 0x80 {
		readLength = 1
	} else {
		b1 := data[1]
		result |= uint32(b1&0x7F) << 7
		if b1 < 0x80 {
			readLength = 2
		} else {
			b2 := data[2]
			result |= uint32(b2&0x7F) << 14
			if b2 < 0x80 {
				readLength = 3
			} else {
				b3 := data[3]
				result |= uint32(b3&0x7F) << 21
				if b3 < 0x80 {
					readLength = 4
				} else {
					b4 := data[4]
					result |= uint32(b4&0x7F) << 28
					readLength = 5
				}
			}
		}
	}
	b.readerIndex += readLength
	return result
}

// Slow path reading (processing byte by byte)
func (b *ByteBuffer) readVaruint32Slow(err *Error) uint32 {
	var result uint32
	var shift uint
	for {
		if b.readerIndex >= len(b.data) {
			*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
			return 0
		}
		byteVal := b.data[b.readerIndex]
		b.readerIndex++
		result |= (uint32(byteVal) & 0x7F) << shift
		if byteVal < 0x80 {
			break
		}
		shift += 7
		if shift >= 35 {
			*err = DeserializationError("varuint32 overflow")
			return 0
		}
	}
	return result
}

func (b *ByteBuffer) PutUint8(writerIndex int, value uint8) {
	b.data[writerIndex] = byte(value)
}

// WriteVaruint32Small7 writes a uint32 in variable-length small-7 format
func (b *ByteBuffer) WriteVaruint32Small7(value uint32) int {
	b.grow(8)
	if value>>7 == 0 {
		b.data[b.writerIndex] = byte(value)
		b.writerIndex++
		return 1
	}
	return b.continueWriteVaruint32Small7(value)
}

func (b *ByteBuffer) continueWriteVaruint32Small7(value uint32) int {
	encoded := uint64(value & 0x7F)
	encoded |= uint64((value&0x3f80)<<1) | 0x80
	idx := b.writerIndex
	if value>>14 == 0 {
		b.unsafePutInt32(idx, int32(encoded))
		b.writerIndex += 2
		return 2
	}
	d := b.continuePutVarint36(idx, encoded, uint64(value))
	b.writerIndex += d
	return d
}

func (b *ByteBuffer) continuePutVarint36(index int, encoded, value uint64) int {
	// bits 14
	encoded |= ((value & 0x1fc000) << 2) | 0x8000
	if value>>21 == 0 {
		b.unsafePutInt32(index, int32(encoded))
		return 3
	}
	// bits 21
	encoded |= ((value & 0xfe00000) << 3) | 0x800000
	if value>>28 == 0 {
		b.unsafePutInt32(index, int32(encoded))
		return 4
	}
	// bits 28
	encoded |= ((value & 0xff0000000) << 4) | 0x80000000
	b.unsafePutInt64(index, encoded)
	return 5
}

func (b *ByteBuffer) unsafePutInt32(index int, v int32) {
	binary.LittleEndian.PutUint32(b.data[index:], uint32(v))
}

func (b *ByteBuffer) unsafePutInt64(index int, v uint64) {
	binary.LittleEndian.PutUint64(b.data[index:], v)
}

// ReadVaruint32Small7 reads a varuint32 in small-7 format with error checking
func (b *ByteBuffer) ReadVaruint32Small7(err *Error) uint32 {
	if b.readerIndex >= len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return 0
	}
	readIdx := b.readerIndex
	v := b.data[readIdx]
	readIdx++
	if v&0x80 == 0 {
		b.readerIndex = readIdx
		return uint32(v)
	}
	return b.readVaruint32Small14(err)
}

func (b *ByteBuffer) readVaruint32Small14(err *Error) uint32 {
	readIdx := b.readerIndex
	if len(b.data)-readIdx >= 5 {
		four := binary.LittleEndian.Uint32(b.data[readIdx:])
		readIdx++
		value := four & 0x7F
		if four&0x80 != 0 {
			readIdx++
			value |= (four >> 1) & 0x3f80
			if four&0x8000 != 0 {
				return b.continueReadVaruint32(readIdx, four, value)
			}
		}
		b.readerIndex = readIdx
		return value
	}
	return uint32(b.readVaruint36Slow(err))
}

func (b *ByteBuffer) continueReadVaruint32(readIdx int, bulkRead, value uint32) uint32 {
	readIdx++
	value |= (bulkRead >> 2) & 0x1fc000
	if bulkRead&0x800000 != 0 {
		readIdx++
		value |= (bulkRead >> 3) & 0xfe00000
		if bulkRead&0x80000000 != 0 {
			v := b.data[readIdx]
			readIdx++
			value |= uint32(v&0x7F) << 28
		}
	}
	b.readerIndex = readIdx
	return value
}

func (b *ByteBuffer) readVaruint36Slow(err *Error) uint64 {
	if b.readerIndex >= len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
		return 0
	}
	b0 := b.data[b.readerIndex]
	b.readerIndex++
	result := uint64(b0 & 0x7F)
	if b0&0x80 != 0 {
		if b.readerIndex >= len(b.data) {
			*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
			return 0
		}
		b1 := b.data[b.readerIndex]
		b.readerIndex++
		result |= uint64(b1&0x7F) << 7
		if b1&0x80 != 0 {
			if b.readerIndex >= len(b.data) {
				*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
				return 0
			}
			b2 := b.data[b.readerIndex]
			b.readerIndex++
			result |= uint64(b2&0x7F) << 14
			if b2&0x80 != 0 {
				if b.readerIndex >= len(b.data) {
					*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
					return 0
				}
				b3 := b.data[b.readerIndex]
				b.readerIndex++
				result |= uint64(b3&0x7F) << 21
				if b3&0x80 != 0 {
					if b.readerIndex >= len(b.data) {
						*err = BufferOutOfBoundError(b.readerIndex, 1, len(b.data))
						return 0
					}
					b4 := b.data[b.readerIndex]
					b.readerIndex++
					result |= uint64(b4) << 28
				}
			}
		}
	}
	return result
}

// unsafeGetInt32 reads little-endian int32 at index
func (b *ByteBuffer) unsafeGetInt32(idx int) int {
	return int(int32(binary.LittleEndian.Uint32(b.data[idx:])))
}

// IncreaseReaderIndex advances readerIndex
func (b *ByteBuffer) IncreaseReaderIndex(n int) {
	b.readerIndex += n
}

// ReadBytes reads n bytes and sets error on bounds violation
func (b *ByteBuffer) ReadBytes(n int, err *Error) []byte {
	if b.readerIndex+n > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, n, len(b.data))
		return nil
	}
	p := b.data[b.readerIndex : b.readerIndex+n]
	b.readerIndex += n
	return p
}

// Skip skips n bytes and sets error on bounds violation
func (b *ByteBuffer) Skip(length int, err *Error) {
	if b.readerIndex+length > len(b.data) {
		*err = BufferOutOfBoundError(b.readerIndex, length, len(b.data))
		return
	}
	b.readerIndex += length
}
