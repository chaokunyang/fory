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

// swiftlint:disable type_body_length
public enum Wire {
    @inlinable
    @inline(__always)
    public static func writeBool(
        _ value: Bool,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        base[index] = value ? 1 : 0
        return index + 1
    }

    @inlinable
    @inline(__always)
    public static func writeInt8(
        _ value: Int8,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        base[index] = UInt8(bitPattern: value)
        return index + 1
    }

    @inlinable
    @inline(__always)
    public static func writeUInt8(
        _ value: UInt8,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        base[index] = value
        return index + 1
    }

    @inlinable
    @inline(__always)
    public static func writeInt16(
        _ value: Int16,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeUInt16(UInt16(bitPattern: value), to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeUInt16(
        _ value: UInt16,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        UnsafeMutableRawPointer(base).storeBytes(of: value.littleEndian, toByteOffset: index, as: UInt16.self)
        return index + 2
    }

    @inlinable
    @inline(__always)
    public static func writeInt32(
        _ value: Int32,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        let zigzag = UInt32(bitPattern: (value << 1) ^ (value >> 31))
        return writeVarUInt32(zigzag, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeInt64(
        _ value: Int64,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        let zigzag = UInt64(bitPattern: (value << 1) ^ (value >> 63))
        return writeVarUInt64(zigzag, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeInt(
        _ value: Int,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeInt64(Int64(value), to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeUInt32(
        _ value: UInt32,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeVarUInt32(value, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeUInt64(
        _ value: UInt64,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeVarUInt64(value, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeUInt(
        _ value: UInt,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeVarUInt64(UInt64(value), to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeFloat32(
        _ value: Float,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeFixedUInt32(value.bitPattern, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func writeFloat64(
        _ value: Double,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        writeFixedUInt64(value.bitPattern, to: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func varUInt32Size(_ value: UInt32) -> Int {
        if value < 0x80 {
            return 1
        }
        if value < 0x4000 {
            return 2
        }
        if value < 0x20_0000 {
            return 3
        }
        if value < 0x1000_0000 {
            return 4
        }
        return 5
    }

    @inlinable
    @inline(__always)
    public static func varInt32Size(_ value: Int32) -> Int {
        let zigzag = UInt32(bitPattern: (value << 1) ^ (value >> 31))
        return varUInt32Size(zigzag)
    }

    @inlinable
    @inline(__always)
    public static func varUInt64Size(_ value: UInt64) -> Int {
        if value < 0x80 {
            return 1
        }
        if value < 0x4000 {
            return 2
        }
        if value < 0x20_0000 {
            return 3
        }
        if value < 0x1000_0000 {
            return 4
        }
        if value < 0x8_0000_0000 {
            return 5
        }
        if value < 0x400_0000_0000 {
            return 6
        }
        if value < 0x2_0000_0000_0000 {
            return 7
        }
        if value < 0x100_0000_0000_0000 {
            return 8
        }
        return 9
    }

    @inlinable
    @inline(__always)
    public static func varInt64Size(_ value: Int64) -> Int {
        let zigzag = UInt64(bitPattern: (value << 1) ^ (value >> 63))
        return varUInt64Size(zigzag)
    }

    @inlinable
    @inline(__always)
    public static func varIntSize(_ value: Int) -> Int {
        varInt64Size(Int64(value))
    }

    @inlinable
    @inline(__always)
    public static func varUIntSize(_ value: UInt) -> Int {
        varUInt64Size(UInt64(value))
    }

    @inlinable
    @inline(__always)
    public static func writeVarUInt32(
        _ value: UInt32,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        if value < 0x80 {
            base[index] = UInt8(value)
            return index + 1
        }
        var encoded = UInt64((value & 0x7F) | 0x80)
        encoded |= UInt64(value & 0x3F80) << 1
        if value < 0x4000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt16(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt16.self
            )
            return index + 2
        }
        encoded |= (UInt64(value & 0x1FC000) << 2) | 0x8000
        if value < 0x20_0000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt32(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt32.self
            )
            return index + 3
        }
        encoded |= (UInt64(value & 0xFE00000) << 3) | 0x800000
        if value < 0x1000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt32(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt32.self
            )
            return index + 4
        }
        encoded |= (UInt64(value >> 28) << 32) | 0x80000000
        UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
        return index + 5
    }

    @inlinable
    @inline(__always)
    public static func writeVarUInt64(
        _ value: UInt64,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        if value < 0x80 {
            base[index] = UInt8(value)
            return index + 1
        }
        var encoded = (value & 0x7F) | 0x80
        encoded |= (value & 0x3F80) << 1
        if value < 0x4000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt16(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt16.self
            )
            return index + 2
        }
        encoded |= ((value & 0x1FC000) << 2) | 0x8000
        if value < 0x20_0000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt32(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt32.self
            )
            return index + 3
        }
        encoded |= ((value & 0xFE00000) << 3) | 0x800000
        if value < 0x1000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(
                of: UInt32(truncatingIfNeeded: encoded).littleEndian,
                toByteOffset: index,
                as: UInt32.self
            )
            return index + 4
        }
        encoded |= ((value & 0x7F0000000) << 4) | 0x80000000
        if value < 0x8_0000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
            return index + 5
        }
        encoded |= ((value & 0x3F800000000) << 5) | 0x8000000000
        if value < 0x400_0000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
            return index + 6
        }
        encoded |= ((value & 0x1FC0000000000) << 6) | 0x800000000000
        if value < 0x2_0000_0000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
            return index + 7
        }
        encoded |= ((value & 0xFE000000000000) << 7) | 0x80000000000000
        if value < 0x100_0000_0000_0000 {
            UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
            return index + 8
        }
        encoded |= 0x8000000000000000
        UnsafeMutableRawPointer(base).storeBytes(of: encoded.littleEndian, toByteOffset: index, as: UInt64.self)
        base[index + 8] = UInt8(truncatingIfNeeded: value >> 56)
        return index + 9
    }

    @inlinable
    @inline(__always)
    public static func readBoolUnchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> Bool {
        base[index] != 0
    }

    @inlinable
    @inline(__always)
    public static func readInt8Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> Int8 {
        Int8(bitPattern: base[index])
    }

    @inlinable
    @inline(__always)
    public static func readUInt8Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt8 {
        base[index]
    }

    @inlinable
    @inline(__always)
    public static func readInt16Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> Int16 {
        Int16(bitPattern: readUInt16Unchecked(from: base, index: index))
    }

    @inlinable
    @inline(__always)
    public static func readUInt16Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt16 {
        loadUInt16(from: base, index: index)
    }

    @inlinable
    @inline(__always)
    public static func readFloat32Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> Float {
        Float(bitPattern: readFixedUInt32Unchecked(from: base, index: index))
    }

    @inlinable
    @inline(__always)
    public static func readFloat64Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> Double {
        Double(bitPattern: readFixedUInt64Unchecked(from: base, index: index))
    }

    @inlinable
    @inline(__always)
    public static func readBool(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Bool {
        try readUInt8(from: bytes, index: &index) != 0
    }

    @inlinable
    @inline(__always)
    public static func readInt8(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int8 {
        Int8(bitPattern: try readUInt8(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readUInt8(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt8 {
        try checkReadable(bytes, index: index, need: 1)
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 1, length: bytes.count)
        }
        let value = base[index]
        index += 1
        return value
    }

    @inlinable
    @inline(__always)
    public static func readInt16(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int16 {
        Int16(bitPattern: try readUInt16(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readUInt16(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt16 {
        try checkReadable(bytes, index: index, need: 2)
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 2, length: bytes.count)
        }
        let value = readUInt16Unchecked(from: UnsafePointer(base), index: index)
        index += 2
        return value
    }

    @inlinable
    @inline(__always)
    public static func readInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int32 {
        try readVarInt32(from: bytes, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt32 {
        try readVarUInt32(from: bytes, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int64 {
        try readVarInt64(from: bytes, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt64 {
        try readVarUInt64(from: bytes, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readInt(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int {
        Int(try readVarInt64(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readUInt(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt {
        UInt(try readVarUInt64(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readFloat32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Float {
        Float(bitPattern: try readFixedUInt32(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readFloat64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Double {
        Double(bitPattern: try readFixedUInt64(from: bytes, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readVarInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int32 {
        let encoded = try readVarUInt32(from: bytes, index: &index)
        return Int32(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    public static func readVarInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> Int64 {
        let encoded = try readVarUInt64(from: bytes, index: &index)
        return Int64(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    public static func readInt32(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> Int32 {
        try readVarInt32(from: base, length: length, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readUInt32(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt32 {
        try readVarUInt32(from: base, length: length, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readInt64(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> Int64 {
        try readVarInt64(from: base, length: length, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readUInt64(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt64 {
        try readVarUInt64(from: base, length: length, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readInt(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> Int {
        Int(try readVarInt64(from: base, length: length, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readUInt(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt {
        UInt(try readVarUInt64(from: base, length: length, index: &index))
    }

    @inlinable
    @inline(__always)
    public static func readVarUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt32 {
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 1, length: bytes.count)
        }
        return try readVarUInt32(from: base, length: bytes.count, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readVarUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt64 {
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 1, length: bytes.count)
        }
        return try readVarUInt64(from: base, length: bytes.count, index: &index)
    }

    @inlinable
    @inline(__always)
    public static func readVarInt32(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> Int32 {
        let encoded = try readVarUInt32(from: base, length: length, index: &index)
        return Int32(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    public static func readVarInt64(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> Int64 {
        let encoded = try readVarUInt64(from: base, length: length, index: &index)
        return Int64(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    public static func readVarUInt32(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt32 {
        let available = length - index
        guard available > 0 else {
            throw ForyError.outOfBounds(cursor: index, need: 1, length: length)
        }
        if available < 5 {
            return try readVarUInt32Slow(from: base, length: length, index: &index)
        }

        let offset = index
        let bulk = loadUInt32(from: base, index: offset)
        var result = bulk & 0x7F
        if (bulk & 0x80) == 0 {
            index = offset + 1
            return result
        }
        result |= (bulk >> 1) & 0x3F80
        if (bulk & 0x8000) == 0 {
            index = offset + 2
            return result
        }
        result |= (bulk >> 2) & 0x1FC000
        if (bulk & 0x800000) == 0 {
            index = offset + 3
            return result
        }
        result |= (bulk >> 3) & 0xFE00000
        if (bulk & 0x80000000) == 0 {
            index = offset + 4
            return result
        }
        result |= UInt32(base[offset + 4] & 0x7F) << 28
        index = offset + 5
        return result
    }

    @inlinable
    @inline(__always)
    public static func readVarUInt64(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt64 {
        let available = length - index
        if available < 9 {
            return try readVarUInt64Slow(from: base, length: length, index: &index)
        }

        let offset = index
        let bulk = loadUInt64(from: base, index: offset)
        var result = bulk & 0x7F
        if (bulk & 0x80) == 0 {
            index = offset + 1
            return result
        }
        result |= (bulk >> 1) & 0x3F80
        if (bulk & 0x8000) == 0 {
            index = offset + 2
            return result
        }
        result |= (bulk >> 2) & 0x1FC000
        if (bulk & 0x800000) == 0 {
            index = offset + 3
            return result
        }
        result |= (bulk >> 3) & 0xFE00000
        if (bulk & 0x80000000) == 0 {
            index = offset + 4
            return result
        }
        result |= (bulk >> 4) & 0x7F0000000
        if (bulk & 0x8000000000) == 0 {
            index = offset + 5
            return result
        }
        result |= (bulk >> 5) & 0x3F800000000
        if (bulk & 0x800000000000) == 0 {
            index = offset + 6
            return result
        }
        result |= (bulk >> 6) & 0x1FC0000000000
        if (bulk & 0x80000000000000) == 0 {
            index = offset + 7
            return result
        }
        result |= (bulk >> 7) & 0xFE000000000000
        if (bulk & 0x8000000000000000) == 0 {
            index = offset + 8
            return result
        }
        result |= UInt64(base[offset + 8]) << 56
        index = offset + 9
        return result
    }

    @inlinable
    @inline(__always)
    public static func readFixedUInt32(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt32 {
        try checkReadable(bytes, index: index, need: 4)
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 4, length: bytes.count)
        }
        let value = readFixedUInt32Unchecked(from: UnsafePointer(base), index: index)
        index += 4
        return value
    }

    @inlinable
    @inline(__always)
    public static func readFixedUInt64(
        from bytes: UnsafeBufferPointer<UInt8>,
        index: inout Int
    ) throws -> UInt64 {
        try checkReadable(bytes, index: index, need: 8)
        guard let base = bytes.baseAddress else {
            throw ForyError.outOfBounds(cursor: index, need: 8, length: bytes.count)
        }
        let value = readFixedUInt64Unchecked(from: UnsafePointer(base), index: index)
        index += 8
        return value
    }

    @inlinable
    @inline(__always)
    static func writeFixedUInt32(
        _ value: UInt32,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        UnsafeMutableRawPointer(base).storeBytes(of: value.littleEndian, toByteOffset: index, as: UInt32.self)
        return index + 4
    }

    @inlinable
    @inline(__always)
    static func writeFixedUInt64(
        _ value: UInt64,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        UnsafeMutableRawPointer(base).storeBytes(of: value.littleEndian, toByteOffset: index, as: UInt64.self)
        return index + 8
    }

    @inlinable
    @inline(__always)
    static func readFixedUInt32Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt32 {
        loadUInt32(from: base, index: index)
    }

    @inlinable
    @inline(__always)
    static func readFixedUInt64Unchecked(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt64 {
        loadUInt64(from: base, index: index)
    }

    @inlinable
    @inline(__always)
    static func loadUInt16(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt16 {
        let raw = UnsafeRawPointer(base)
        if ((Int(bitPattern: raw) + index) & (MemoryLayout<UInt16>.alignment - 1)) == 0 {
            return UInt16(littleEndian: raw.load(fromByteOffset: index, as: UInt16.self))
        }
        return UInt16(littleEndian: raw.loadUnaligned(fromByteOffset: index, as: UInt16.self))
    }

    @inlinable
    @inline(__always)
    static func loadUInt32(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt32 {
        let raw = UnsafeRawPointer(base)
        if ((Int(bitPattern: raw) + index) & (MemoryLayout<UInt32>.alignment - 1)) == 0 {
            return UInt32(littleEndian: raw.load(fromByteOffset: index, as: UInt32.self))
        }
        return UInt32(littleEndian: raw.loadUnaligned(fromByteOffset: index, as: UInt32.self))
    }

    @inlinable
    @inline(__always)
    static func loadUInt64(
        from base: UnsafePointer<UInt8>,
        index: Int
    ) -> UInt64 {
        let raw = UnsafeRawPointer(base)
        if ((Int(bitPattern: raw) + index) & (MemoryLayout<UInt64>.alignment - 1)) == 0 {
            return UInt64(littleEndian: raw.load(fromByteOffset: index, as: UInt64.self))
        }
        return UInt64(littleEndian: raw.loadUnaligned(fromByteOffset: index, as: UInt64.self))
    }

    @inlinable
    @inline(__always)
    static func readVarUInt32Slow(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt32 {
        var position = index
        var result: UInt32 = 0
        var shift: UInt32 = 0
        for step in 0..<5 {
            if position >= length {
                throw ForyError.outOfBounds(cursor: position, need: 1, length: length)
            }
            let byte = base[position]
            position += 1
            if step == 4, byte >= 0x80 {
                throw ForyError.encodingError("varuint32 overflow")
            }
            result |= UInt32(byte & 0x7F) << shift
            if byte < 0x80 {
                index = position
                return result
            }
            shift += 7
        }
        throw ForyError.encodingError("varuint32 overflow")
    }

    @inlinable
    @inline(__always)
    static func readVarUInt64Slow(
        from base: UnsafePointer<UInt8>,
        length: Int,
        index: inout Int
    ) throws -> UInt64 {
        var position = index
        var result: UInt64 = 0
        var shift: UInt64 = 0
        for _ in 0..<8 {
            if position >= length {
                throw ForyError.outOfBounds(cursor: position, need: 1, length: length)
            }
            let byte = base[position]
            position += 1
            result |= UInt64(byte & 0x7F) << shift
            if byte < 0x80 {
                index = position
                return result
            }
            shift += 7
        }
        if position >= length {
            throw ForyError.outOfBounds(cursor: position, need: 1, length: length)
        }
        let last = base[position]
        position += 1
        result |= UInt64(last) << 56
        index = position
        return result
    }

    @inlinable
    @inline(__always)
    public static func checkReadable(
        _ bytes: UnsafeBufferPointer<UInt8>,
        index: Int,
        need: Int
    ) throws {
        if index < 0 || need < 0 || index + need > bytes.count {
            throw ForyError.outOfBounds(cursor: index, need: need, length: bytes.count)
        }
    }

    @inlinable
    @inline(__always)
    public static func checkReadable(
        length: Int,
        index: Int,
        need: Int
    ) throws {
        if index < 0 || need < 0 || index + need > length {
            throw ForyError.outOfBounds(cursor: index, need: need, length: length)
        }
    }

    @inlinable
    @inline(__always)
    public static func writeRegion(
        buffer: ByteBuffer,
        exactCount: Int,
        _ body: (UnsafeMutablePointer<UInt8>) -> Void
    ) {
        guard exactCount > 0 else {
            return
        }
        buffer.ensureWritable(exactCount)
        let start = buffer.writerIndex
        body(buffer.bytesBaseAddress!.advanced(by: start))
        buffer.advanceWriterIndex(exactCount)
    }

    @inlinable
    @inline(__always)
    public static func writeRegion(
        buffer: ByteBuffer,
        maxCount: Int,
        _ body: (UnsafeMutablePointer<UInt8>) -> Int
    ) {
        guard maxCount > 0 else {
            return
        }
        buffer.ensureWritable(maxCount)
        let start = buffer.writerIndex
        let written = body(buffer.bytesBaseAddress!.advanced(by: start))
        precondition(
            written >= 0 && written <= maxCount,
            "writeRegion wrote \(written) bytes into a maxCount \(maxCount) region"
        )
        buffer.advanceWriterIndex(written)
    }

    @inlinable
    @inline(__always)
    public static func readRegion(
        buffer: ByteBuffer,
        _ body: (UnsafeBufferPointer<UInt8>) throws -> Int
    ) throws {
        let available = buffer.count - buffer.readerIndex
        let start = buffer.bytesBaseAddress.map { UnsafePointer($0.advanced(by: buffer.readerIndex)) }
        let region = UnsafeBufferPointer(start: start, count: available)
        let consumed = try body(region)
        if consumed < 0 || consumed > available {
            throw ForyError.outOfBounds(cursor: buffer.readerIndex, need: consumed, length: buffer.count)
        }
        buffer.advanceReaderIndex(consumed)
    }

    @inline(__always)
    static func copyBytes(
        _ bytes: [UInt8],
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        guard !bytes.isEmpty else {
            return index
        }
        bytes.withUnsafeBufferPointer { source in
            guard let sourceBase = source.baseAddress else {
                return
            }
            UnsafeMutableRawPointer(base.advanced(by: index))
                .copyMemory(from: UnsafeRawPointer(sourceBase), byteCount: source.count)
        }
        return index + bytes.count
    }

    @inline(__always)
    static func copyBytes(
        _ bytes: UnsafeBufferPointer<UInt8>,
        to base: UnsafeMutablePointer<UInt8>,
        index: Int
    ) -> Int {
        guard let sourceBase = bytes.baseAddress, bytes.count > 0 else {
            return index
        }
        UnsafeMutableRawPointer(base.advanced(by: index))
            .copyMemory(from: UnsafeRawPointer(sourceBase), byteCount: bytes.count)
        return index + bytes.count
    }
}
// swiftlint:enable type_body_length
