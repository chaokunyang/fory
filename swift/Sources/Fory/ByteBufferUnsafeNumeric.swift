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

import Foundation

public extension ByteBuffer {
    @inlinable
    @inline(__always)
    func writeUnsafeNumericRegion(
        maxBytes: Int,
        _ body: (UnsafeMutablePointer<UInt8>) -> Int
    ) {
        guard maxBytes > 0 else {
            return
        }
        let start = storage.count
        storage.append(contentsOf: repeatElement(0, count: maxBytes))
        let written = storage.withUnsafeMutableBufferPointer { buffer in
            body(buffer.baseAddress!.advanced(by: start))
        }
        precondition(written >= 0 && written <= maxBytes, "invalid numeric region length \(written)")
        if written < maxBytes {
            storage.removeLast(maxBytes - written)
        }
    }

    @inlinable
    @inline(__always)
    func readUnsafeNumericRegion(
        _ body: (UnsafeBufferPointer<UInt8>) throws -> Int
    ) throws {
        let available = storage.count - cursor
        let consumed = try storage.withUnsafeBufferPointer { buffer -> Int in
            let start = buffer.baseAddress.map { $0.advanced(by: cursor) }
            let region = UnsafeBufferPointer(start: start, count: available)
            return try body(region)
        }
        if consumed < 0 || consumed > available {
            throw ForyError.outOfBounds(cursor: cursor, need: consumed, length: storage.count)
        }
        cursor += consumed
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteBool(
        _ value: Bool,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        base[offset] = value ? 1 : 0
        offset += 1
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteInt8(
        _ value: Int8,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        base[offset] = UInt8(bitPattern: value)
        offset += 1
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteUInt8(
        _ value: UInt8,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        base[offset] = value
        offset += 1
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteInt16(
        _ value: Int16,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteUInt16(UInt16(bitPattern: value), to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteUInt16(
        _ value: UInt16,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        var little = value.littleEndian
        withUnsafeBytes(of: &little) { raw in
            guard let source = raw.baseAddress else {
                return
            }
            UnsafeMutableRawPointer(base.advanced(by: offset)).copyMemory(from: source, byteCount: 2)
        }
        offset += 2
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteInt32(
        _ value: Int32,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarInt32(value, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteInt64(
        _ value: Int64,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarInt64(value, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteInt(
        _ value: Int,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarInt64(Int64(value), to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteUInt32(
        _ value: UInt32,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarUInt32(value, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteUInt64(
        _ value: UInt64,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarUInt64(value, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteUInt(
        _ value: UInt,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        unsafeWriteVarUInt64(UInt64(value), to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteFloat32(
        _ value: Float,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        var little = value.bitPattern.littleEndian
        withUnsafeBytes(of: &little) { raw in
            guard let source = raw.baseAddress else {
                return
            }
            UnsafeMutableRawPointer(base.advanced(by: offset)).copyMemory(from: source, byteCount: 4)
        }
        offset += 4
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteFloat64(
        _ value: Double,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        var little = value.bitPattern.littleEndian
        withUnsafeBytes(of: &little) { raw in
            guard let source = raw.baseAddress else {
                return
            }
            UnsafeMutableRawPointer(base.advanced(by: offset)).copyMemory(from: source, byteCount: 8)
        }
        offset += 8
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteVarUInt32(
        _ value: UInt32,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        var remaining = value
        while remaining >= 0x80 {
            base[offset] = (UInt8(truncatingIfNeeded: remaining) & 0x7F) | 0x80
            offset += 1
            remaining >>= 7
        }
        base[offset] = UInt8(truncatingIfNeeded: remaining)
        offset += 1
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteVarInt32(
        _ value: Int32,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        let zigzag = UInt32(bitPattern: (value << 1) ^ (value >> 31))
        unsafeWriteVarUInt32(zigzag, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteVarUInt64(
        _ value: UInt64,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        var remaining = value
        while remaining >= 0x80 {
            base[offset] = (UInt8(truncatingIfNeeded: remaining) & 0x7F) | 0x80
            offset += 1
            remaining >>= 7
        }
        base[offset] = UInt8(truncatingIfNeeded: remaining)
        offset += 1
    }

    @inlinable
    @inline(__always)
    static func unsafeWriteVarInt64(
        _ value: Int64,
        to base: UnsafeMutablePointer<UInt8>,
        offset: inout Int
    ) {
        let zigzag = UInt64(bitPattern: (value << 1) ^ (value >> 63))
        unsafeWriteVarUInt64(zigzag, to: base, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadBool(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Bool {
        try unsafeReadUInt8(from: bytes, offset: &offset) != 0
    }

    @inlinable
    @inline(__always)
    static func unsafeReadInt8(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int8 {
        Int8(bitPattern: try unsafeReadUInt8(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadUInt8(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt8 {
        guard offset < bytes.count, let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 1, length: bytes.count)
        }
        let value = base[offset]
        offset += 1
        return value
    }

    @inlinable
    @inline(__always)
    static func unsafeReadInt16(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int16 {
        Int16(bitPattern: try unsafeReadUInt16(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadUInt16(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt16 {
        guard offset + 2 <= bytes.count, let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 2, length: bytes.count)
        }
        let b0 = UInt16(base[offset])
        let b1 = UInt16(base[offset + 1]) << 8
        offset += 2
        return b0 | b1
    }

    @inlinable
    @inline(__always)
    static func unsafeReadInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int32 {
        try unsafeReadVarInt32(from: bytes, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt32 {
        try unsafeReadVarUInt32(from: bytes, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int64 {
        try unsafeReadVarInt64(from: bytes, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt64 {
        try unsafeReadVarUInt64(from: bytes, offset: &offset)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadInt(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int {
        Int(try unsafeReadVarInt64(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadUInt(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt {
        UInt(try unsafeReadVarUInt64(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadFloat32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Float {
        Float(bitPattern: try unsafeReadFixedUInt32(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadFloat64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Double {
        Double(bitPattern: try unsafeReadFixedUInt64(from: bytes, offset: &offset))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadVarInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int32 {
        let encoded = try unsafeReadVarUInt32(from: bytes, offset: &offset)
        return Int32(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadVarInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> Int64 {
        let encoded = try unsafeReadVarUInt64(from: bytes, offset: &offset)
        return Int64(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    static func unsafeReadVarUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt32 {
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 1, length: bytes.count)
        }
        let available = bytes.count - offset
        guard available > 0 else {
            throw ForyError.outOfBounds(cursor: offset, need: 1, length: bytes.count)
        }

        let b0 = base[offset]
        if b0 < 0x80 {
            offset += 1
            return UInt32(b0)
        }
        guard available >= 2 else {
            throw ForyError.outOfBounds(cursor: offset, need: 2, length: bytes.count)
        }
        let b1 = base[offset + 1]
        if b1 < 0x80 {
            offset += 2
            return UInt32(b0 & 0x7F) | (UInt32(b1) << 7)
        }
        guard available >= 3 else {
            throw ForyError.outOfBounds(cursor: offset, need: 3, length: bytes.count)
        }
        let b2 = base[offset + 2]
        if b2 < 0x80 {
            offset += 3
            return UInt32(b0 & 0x7F) | (UInt32(b1 & 0x7F) << 7) | (UInt32(b2) << 14)
        }
        guard available >= 4 else {
            throw ForyError.outOfBounds(cursor: offset, need: 4, length: bytes.count)
        }
        let b3 = base[offset + 3]
        if b3 < 0x80 {
            offset += 4
            return UInt32(b0 & 0x7F) |
                (UInt32(b1 & 0x7F) << 7) |
                (UInt32(b2 & 0x7F) << 14) |
                (UInt32(b3) << 21)
        }
        guard available >= 5 else {
            throw ForyError.outOfBounds(cursor: offset, need: 5, length: bytes.count)
        }
        let b4 = base[offset + 4]
        if b4 >= 0x80 {
            throw ForyError.encodingError("varuint32 overflow")
        }
        offset += 5
        return UInt32(b0 & 0x7F) |
            (UInt32(b1 & 0x7F) << 7) |
            (UInt32(b2 & 0x7F) << 14) |
            (UInt32(b3 & 0x7F) << 21) |
            (UInt32(b4) << 28)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadVarUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt64 {
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 1, length: bytes.count)
        }
        var value: UInt64 = 0
        var shift: UInt64 = 0
        var index = offset
        while index < bytes.count {
            let byte = base[index]
            value |= UInt64(byte & 0x7F) << shift
            index += 1
            if byte < 0x80 {
                offset = index
                return value
            }
            shift += 7
            if shift >= 64 {
                throw ForyError.encodingError("varuint64 overflow")
            }
        }
        throw ForyError.outOfBounds(cursor: offset, need: index - offset + 1, length: bytes.count)
    }

    @inlinable
    @inline(__always)
    static func unsafeReadFixedUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt32 {
        guard offset + 4 <= bytes.count, let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 4, length: bytes.count)
        }
        let b0 = UInt32(base[offset])
        let b1 = UInt32(base[offset + 1]) << 8
        let b2 = UInt32(base[offset + 2]) << 16
        let b3 = UInt32(base[offset + 3]) << 24
        offset += 4
        return b0 | b1 | b2 | b3
    }

    @inlinable
    @inline(__always)
    static func unsafeReadFixedUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        offset: inout Int
    ) throws -> UInt64 {
        guard offset + 8 <= bytes.count, let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: offset, need: 8, length: bytes.count)
        }
        let b0 = UInt64(base[offset])
        let b1 = UInt64(base[offset + 1]) << 8
        let b2 = UInt64(base[offset + 2]) << 16
        let b3 = UInt64(base[offset + 3]) << 24
        let b4 = UInt64(base[offset + 4]) << 32
        let b5 = UInt64(base[offset + 5]) << 40
        let b6 = UInt64(base[offset + 6]) << 48
        let b7 = UInt64(base[offset + 7]) << 56
        offset += 8
        return b0 | b1 | b2 | b3 | b4 | b5 | b6 | b7
    }
}
