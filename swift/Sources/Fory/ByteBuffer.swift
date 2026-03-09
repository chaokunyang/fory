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
public final class ByteBuffer {
    @usableFromInline
    internal var storage: UnsafeMutableRawPointer?

    @usableFromInline
    internal var capacity: Int

    @usableFromInline
    internal var writerIndex: Int

    @usableFromInline
    internal var readerIndex: Int

    private var dataBridge = Data()

    @inlinable
    public init(capacity: Int = 256) {
        let initialCapacity = Self.roundCapacity(capacity)
        storage = initialCapacity > 0
            ? UnsafeMutableRawPointer.allocate(
                byteCount: initialCapacity,
                alignment: MemoryLayout<UInt64>.alignment
            )
            : nil
        self.capacity = initialCapacity
        writerIndex = 0
        readerIndex = 0
    }

    @inlinable
    public init(data: Data) {
        let initialCapacity = Self.roundCapacity(data.count)
        storage = initialCapacity > 0
            ? UnsafeMutableRawPointer.allocate(
                byteCount: initialCapacity,
                alignment: MemoryLayout<UInt64>.alignment
            )
            : nil
        capacity = initialCapacity
        writerIndex = data.count
        readerIndex = 0
        if data.count > 0 {
            data.withUnsafeBytes { source in
                guard let sourceBase = source.baseAddress, let storage else {
                    return
                }
                storage.copyMemory(from: sourceBase, byteCount: data.count)
            }
        }
    }

    @inlinable
    public init(bytes: [UInt8]) {
        let initialCapacity = Self.roundCapacity(bytes.count)
        storage = initialCapacity > 0
            ? UnsafeMutableRawPointer.allocate(
                byteCount: initialCapacity,
                alignment: MemoryLayout<UInt64>.alignment
            )
            : nil
        capacity = initialCapacity
        writerIndex = bytes.count
        readerIndex = 0
        if bytes.count > 0 {
            bytes.withUnsafeBytes { source in
                guard let sourceBase = source.baseAddress, let storage else {
                    return
                }
                storage.copyMemory(from: sourceBase, byteCount: bytes.count)
            }
        }
    }

    deinit {
        storage?.deallocate()
    }

    @inlinable
    public var count: Int {
        writerIndex
    }

    @inlinable
    public var remaining: Int {
        writerIndex - readerIndex
    }

    @usableFromInline
    @inline(__always)
    internal var readableCount: Int {
        writerIndex
    }

    @usableFromInline
    @inline(__always)
    internal var bytesBaseAddress: UnsafeMutablePointer<UInt8>? {
        storage?.assumingMemoryBound(to: UInt8.self)
    }

    @usableFromInline
    @inline(__always)
    internal func byte(at index: Int) -> UInt8 {
        bytesBaseAddress![index]
    }

    @usableFromInline
    @inline(__always)
    internal func copyBytes(start: Int, end: Int) -> [UInt8] {
        let length = end - start
        guard length > 0, let storage else {
            return []
        }
        return [UInt8](unsafeUninitializedCapacity: length) { destination, initializedCount in
            guard let destinationBase = destination.baseAddress else {
                initializedCount = 0
                return
            }
            UnsafeMutableRawPointer(destinationBase).copyMemory(
                from: storage.advanced(by: start),
                byteCount: length
            )
            initializedCount = length
        }
    }

    @usableFromInline
    @inline(__always)
    internal func readableBytes() -> [UInt8] {
        copyBytes(start: 0, end: writerIndex)
    }

    @usableFromInline
    @inline(__always)
    internal func matchesBytes(start: Int, bytes: [UInt8]) -> Bool {
        guard !bytes.isEmpty else {
            return true
        }
        guard let storage else {
            return false
        }
        return bytes.withUnsafeBytes { source in
            guard let sourceBase = source.baseAddress else {
                return true
            }
            return memcmp(storage.advanced(by: start), sourceBase, bytes.count) == 0
        }
    }

    @usableFromInline
    @inline(__always)
    internal func withUnsafeReadableBytes<R>(
        _ body: (UnsafeBufferPointer<UInt8>) throws -> R
    ) rethrows -> R {
        let readable = UnsafeBufferPointer(start: bytesBaseAddress, count: writerIndex)
        return try body(readable)
    }

    @inlinable
    public func reserve(_ additional: Int) {
        ensureCapacity(writerIndex + additional)
    }

    @inlinable
    public func clear() {
        readerIndex = 0
        writerIndex = 0
    }

    @inlinable
    public func reset() {
        clear()
    }

    @inlinable
    public func flip() {
        readerIndex = 0
    }

    @inlinable
    public func setCursor(_ value: Int) {
        readerIndex = value
    }

    @inlinable
    public func replace(with data: Data) {
        let dataCount = data.count
        ensureCapacity(dataCount)
        if dataCount > 0 {
            data.withUnsafeBytes { source in
                guard let sourceBase = source.baseAddress, let storage else {
                    return
                }
                storage.copyMemory(from: sourceBase, byteCount: dataCount)
            }
        }
        writerIndex = dataCount
        readerIndex = 0
    }

    @usableFromInline
    @inline(__always)
    internal func swapState(with other: ByteBuffer) {
        swap(&storage, &other.storage)
        swap(&capacity, &other.capacity)
        swap(&writerIndex, &other.writerIndex)
        swap(&readerIndex, &other.readerIndex)
        swap(&dataBridge, &other.dataBridge)
    }

    @usableFromInline
    @inline(__always)
    internal func copyToData() -> Data {
        let byteCount = writerIndex
        if dataBridge.count != byteCount {
            dataBridge.count = byteCount
        }
        if byteCount > 0, let storage {
            dataBridge.withUnsafeMutableBytes { destination in
                guard let destinationBase = destination.baseAddress else {
                    return
                }
                destinationBase.copyMemory(from: storage, byteCount: byteCount)
            }
        }
        return dataBridge
    }

    @usableFromInline
    @inline(__always)
    internal func materializeData(
        byteCount: Int,
        _ body: (UnsafeMutablePointer<UInt8>) -> Void
    ) -> Data {
        if dataBridge.count != byteCount {
            dataBridge.count = byteCount
        }
        if byteCount > 0 {
            dataBridge.withUnsafeMutableBytes { destination in
                guard let base = destination.baseAddress?.assumingMemoryBound(to: UInt8.self) else {
                    return
                }
                body(base)
            }
        }
        return dataBridge
    }

    @inlinable
    public func getCursor() -> Int {
        readerIndex
    }

    @inlinable
    public func moveBack(_ amount: Int) {
        readerIndex -= amount
    }

    @usableFromInline
    @inline(__always)
    internal func ensureWritable(_ need: Int) {
        ensureCapacity(writerIndex + need)
    }

    @usableFromInline
    @inline(__always)
    internal func advanceWriterIndex(_ delta: Int) {
        writerIndex += delta
    }

    @usableFromInline
    @inline(__always)
    internal func advanceReaderIndex(_ delta: Int) {
        readerIndex += delta
    }

    @inlinable
    @inline(__always)
    public func writeUInt8(_ value: UInt8) {
        ensureWritable(1)
        bytesBaseAddress![writerIndex] = value
        writerIndex += 1
    }

    @inlinable
    @inline(__always)
    public func writeInt8(_ value: Int8) {
        ensureWritable(1)
        bytesBaseAddress![writerIndex] = UInt8(bitPattern: value)
        writerIndex += 1
    }

    @inlinable
    @inline(__always)
    public func writeUInt16(_ value: UInt16) {
        ensureWritable(2)
        storage!.storeBytes(of: value.littleEndian, toByteOffset: writerIndex, as: UInt16.self)
        writerIndex += 2
    }

    @inlinable
    public func writeInt16(_ value: Int16) {
        ensureWritable(2)
        storage!.storeBytes(of: UInt16(bitPattern: value).littleEndian, toByteOffset: writerIndex, as: UInt16.self)
        writerIndex += 2
    }

    @inlinable
    @inline(__always)
    public func writeUInt32(_ value: UInt32) {
        ensureWritable(4)
        storage!.storeBytes(of: value.littleEndian, toByteOffset: writerIndex, as: UInt32.self)
        writerIndex += 4
    }

    @inlinable
    public func writeInt32(_ value: Int32) {
        ensureWritable(4)
        storage!.storeBytes(of: UInt32(bitPattern: value).littleEndian, toByteOffset: writerIndex, as: UInt32.self)
        writerIndex += 4
    }

    @inlinable
    @inline(__always)
    public func writeUInt64(_ value: UInt64) {
        ensureWritable(8)
        storage!.storeBytes(of: value.littleEndian, toByteOffset: writerIndex, as: UInt64.self)
        writerIndex += 8
    }

    @inlinable
    public func writeInt64(_ value: Int64) {
        ensureWritable(8)
        storage!.storeBytes(of: UInt64(bitPattern: value).littleEndian, toByteOffset: writerIndex, as: UInt64.self)
        writerIndex += 8
    }

    @inlinable
    @inline(__always)
    public func writeVarUInt32(_ value: UInt32) {
        ensureWritable(8)
        writerIndex += putVarUInt32(at: writerIndex, value: value)
    }

    @inlinable
    @inline(__always)
    public func writeVarUInt64(_ value: UInt64) {
        ensureWritable(9)
        writerIndex += putVarUInt64(at: writerIndex, value: value)
    }

    @inlinable
    public func writeVarUInt36Small(_ value: UInt64) {
        precondition(value < (1 << 36), "varuint36small overflow")
        ensureWritable(8)
        writerIndex += putVarUInt36Small(at: writerIndex, value: value)
    }

    @inlinable
    @inline(__always)
    public func writeVarInt32(_ value: Int32) {
        let zigzag = UInt32(bitPattern: (value << 1) ^ (value >> 31))
        writeVarUInt32(zigzag)
    }

    @inlinable
    @inline(__always)
    public func writeVarInt64(_ value: Int64) {
        let zigzag = UInt64(bitPattern: (value << 1) ^ (value >> 63))
        writeVarUInt64(zigzag)
    }

    @inlinable
    public func writeTaggedInt64(_ value: Int64) {
        if (-1_073_741_824 ... 1_073_741_823).contains(value) {
            writeInt32(Int32(truncatingIfNeeded: value) << 1)
            return
        }
        ensureWritable(9)
        bytesBaseAddress![writerIndex] = 0x01
        storage!.storeBytes(
            of: UInt64(bitPattern: value).littleEndian,
            toByteOffset: writerIndex + 1,
            as: UInt64.self
        )
        writerIndex += 9
    }

    @inlinable
    public func writeTaggedUInt64(_ value: UInt64) {
        if value <= UInt64(Int32.max) {
            writeUInt32(UInt32(truncatingIfNeeded: value) << 1)
            return
        }
        ensureWritable(9)
        bytesBaseAddress![writerIndex] = 0x01
        storage!.storeBytes(of: value.littleEndian, toByteOffset: writerIndex + 1, as: UInt64.self)
        writerIndex += 9
    }

    @inlinable
    public func writeFloat32(_ value: Float) {
        writeUInt32(value.bitPattern)
    }

    @inlinable
    public func writeFloat64(_ value: Double) {
        writeUInt64(value.bitPattern)
    }

    @inlinable
    public func writeBytes(_ bytes: some Collection<UInt8>) {
        let length = bytes.count
        guard length > 0 else {
            return
        }
        ensureWritable(length)
        let destination = bytesBaseAddress!.advanced(by: writerIndex)
        if bytes.withContiguousStorageIfAvailable({ contiguousBytes in
            guard let sourceBase = contiguousBytes.baseAddress else {
                return
            }
            UnsafeMutableRawPointer(destination).copyMemory(
                from: UnsafeRawPointer(sourceBase),
                byteCount: contiguousBytes.count
            )
        }) != nil {
            writerIndex += length
            return
        }
        var offset = 0
        for byte in bytes {
            destination[offset] = byte
            offset += 1
        }
        writerIndex += length
    }

    @inlinable
    @inline(__always)
    public func writeBytes(_ bytes: [UInt8]) {
        let length = bytes.count
        guard length > 0 else {
            return
        }
        ensureWritable(length)
        bytes.withUnsafeBytes { source in
            guard let sourceBase = source.baseAddress, let destination = bytesBaseAddress else {
                return
            }
            UnsafeMutableRawPointer(destination.advanced(by: writerIndex))
                .copyMemory(from: sourceBase, byteCount: length)
        }
        writerIndex += length
    }

    @inlinable
    public func writeBytes(_ bytes: UnsafeRawBufferPointer) {
        let length = bytes.count
        guard length > 0, let sourceBase = bytes.baseAddress else {
            return
        }
        ensureWritable(length)
        UnsafeMutableRawPointer(bytesBaseAddress!.advanced(by: writerIndex))
            .copyMemory(from: sourceBase, byteCount: length)
        writerIndex += length
    }

    @inlinable
    public func writeData(_ data: Data) {
        let length = data.count
        guard length > 0 else {
            return
        }
        ensureWritable(length)
        data.withUnsafeBytes { source in
            guard let sourceBase = source.baseAddress, let destination = bytesBaseAddress else {
                return
            }
            UnsafeMutableRawPointer(destination.advanced(by: writerIndex))
                .copyMemory(from: sourceBase, byteCount: length)
        }
        writerIndex += length
    }

    @inlinable
    public func setByte(at index: Int, to value: UInt8) {
        bytesBaseAddress![index] = value
    }

    @inlinable
    public func setBytes(at index: Int, to bytes: some Collection<UInt8>) {
        if bytes.withContiguousStorageIfAvailable({ contiguousBytes in
            guard let sourceBase = contiguousBytes.baseAddress, let storage else {
                return
            }
            storage.advanced(by: index).copyMemory(
                from: UnsafeRawPointer(sourceBase),
                byteCount: contiguousBytes.count
            )
        }) != nil {
            return
        }
        var offset = index
        for byte in bytes {
            bytesBaseAddress![offset] = byte
            offset += 1
        }
    }

    @inlinable
    @inline(__always)
    public func checkBound(_ need: Int) throws {
        if readerIndex < 0 || need < 0 || readerIndex + need > writerIndex {
            throw ForyError.outOfBounds(cursor: readerIndex, need: need, length: writerIndex)
        }
    }

    @inlinable
    public func readBytes(into destination: UnsafeMutableRawBufferPointer) throws {
        try checkBound(destination.count)
        guard destination.count > 0, let destinationBase = destination.baseAddress, let storage else {
            return
        }
        destinationBase.copyMemory(from: storage.advanced(by: readerIndex), byteCount: destination.count)
        readerIndex += destination.count
    }

    @inlinable
    @inline(__always)
    public func readUInt8() throws -> UInt8 {
        try checkBound(1)
        defer { readerIndex += 1 }
        return bytesBaseAddress![readerIndex]
    }

    @inlinable
    @inline(__always)
    public func readInt8() throws -> Int8 {
        Int8(bitPattern: try readUInt8())
    }

    @inlinable
    @inline(__always)
    public func readUInt16() throws -> UInt16 {
        try checkBound(2)
        let value = loadUInt16(at: readerIndex)
        readerIndex += 2
        return value
    }

    @inlinable
    public func readInt16() throws -> Int16 {
        Int16(bitPattern: try readUInt16())
    }

    @inlinable
    @inline(__always)
    public func readUInt32() throws -> UInt32 {
        try checkBound(4)
        let value = loadUInt32(at: readerIndex)
        readerIndex += 4
        return value
    }

    @inlinable
    public func readInt32() throws -> Int32 {
        Int32(bitPattern: try readUInt32())
    }

    @inlinable
    @inline(__always)
    public func readUInt64() throws -> UInt64 {
        try checkBound(8)
        let value = loadUInt64(at: readerIndex)
        readerIndex += 8
        return value
    }

    @inlinable
    public func readInt64() throws -> Int64 {
        Int64(bitPattern: try readUInt64())
    }

    @inlinable
    @inline(__always)
    public func readVarUInt32() throws -> UInt32 {
        try checkBound(1)
        if writerIndex - readerIndex < 5 {
            return try readVarUInt32Slow()
        }
        let offset = readerIndex
        let bulk = loadUInt32(at: offset)
        var result = bulk & 0x7F
        if (bulk & 0x80) == 0 {
            readerIndex = offset + 1
            return result
        }
        result |= (bulk >> 1) & 0x3F80
        if (bulk & 0x8000) == 0 {
            readerIndex = offset + 2
            return result
        }
        result |= (bulk >> 2) & 0x1FC000
        if (bulk & 0x800000) == 0 {
            readerIndex = offset + 3
            return result
        }
        result |= (bulk >> 3) & 0xFE00000
        if (bulk & 0x80000000) == 0 {
            readerIndex = offset + 4
            return result
        }
        result |= UInt32(byte(at: offset + 4) & 0x7F) << 28
        readerIndex = offset + 5
        return result
    }

    @inlinable
    @inline(__always)
    public func readVarUInt64() throws -> UInt64 {
        try checkBound(1)
        if writerIndex - readerIndex < 9 {
            return try readVarUInt64Slow()
        }
        let offset = readerIndex
        let bulk = loadUInt64(at: offset)
        var result = bulk & 0x7F
        if (bulk & 0x80) == 0 {
            readerIndex = offset + 1
            return result
        }
        result |= (bulk >> 1) & 0x3F80
        if (bulk & 0x8000) == 0 {
            readerIndex = offset + 2
            return result
        }
        result |= (bulk >> 2) & 0x1FC000
        if (bulk & 0x800000) == 0 {
            readerIndex = offset + 3
            return result
        }
        result |= (bulk >> 3) & 0xFE00000
        if (bulk & 0x80000000) == 0 {
            readerIndex = offset + 4
            return result
        }
        result |= (bulk >> 4) & 0x7F0000000
        if (bulk & 0x8000000000) == 0 {
            readerIndex = offset + 5
            return result
        }
        result |= (bulk >> 5) & 0x3F800000000
        if (bulk & 0x800000000000) == 0 {
            readerIndex = offset + 6
            return result
        }
        result |= (bulk >> 6) & 0x1FC0000000000
        if (bulk & 0x80000000000000) == 0 {
            readerIndex = offset + 7
            return result
        }
        result |= (bulk >> 7) & 0xFE000000000000
        if (bulk & 0x8000000000000000) == 0 {
            readerIndex = offset + 8
            return result
        }
        result |= UInt64(byte(at: offset + 8)) << 56
        readerIndex = offset + 9
        return result
    }

    @inlinable
    public func readVarUInt36Small() throws -> UInt64 {
        try checkBound(1)
        if writerIndex - readerIndex < 8 {
            return try readVarUInt36SmallSlow()
        }
        let offset = readerIndex
        let bulk = loadUInt64(at: offset)
        var result = bulk & 0x7F
        if (bulk & 0x80) == 0 {
            readerIndex = offset + 1
            return result
        }
        result |= (bulk >> 1) & 0x3F80
        if (bulk & 0x8000) == 0 {
            readerIndex = offset + 2
            return result
        }
        result |= (bulk >> 2) & 0x1FC000
        if (bulk & 0x800000) == 0 {
            readerIndex = offset + 3
            return result
        }
        result |= (bulk >> 3) & 0xFE00000
        if (bulk & 0x80000000) == 0 {
            readerIndex = offset + 4
            return result
        }
        result |= (bulk >> 4) & 0xFF0000000
        readerIndex = offset + 5
        return result
    }

    @inlinable
    @inline(__always)
    public func readVarInt32() throws -> Int32 {
        let encoded = try readVarUInt32()
        return Int32(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    @inline(__always)
    public func readVarInt64() throws -> Int64 {
        let encoded = try readVarUInt64()
        return Int64(bitPattern: (encoded >> 1) ^ (~(encoded & 1) &+ 1))
    }

    @inlinable
    public func readTaggedInt64() throws -> Int64 {
        let first = try readInt32()
        if (first & 1) == 0 {
            return Int64(first >> 1)
        }
        moveBack(3)
        return try readInt64()
    }

    @inlinable
    public func readTaggedUInt64() throws -> UInt64 {
        let first = try readUInt32()
        if (first & 1) == 0 {
            return UInt64(first >> 1)
        }
        moveBack(3)
        return try readUInt64()
    }

    @inlinable
    public func readFloat32() throws -> Float {
        Float(bitPattern: try readUInt32())
    }

    @inlinable
    public func readFloat64() throws -> Double {
        Double(bitPattern: try readUInt64())
    }

    @inlinable
    public func readBytes(count: Int) throws -> [UInt8] {
        if count == 0 {
            return []
        }
        return try [UInt8](unsafeUninitializedCapacity: count) { destination, initializedCount in
            try readBytes(into: UnsafeMutableRawBufferPointer(destination))
            initializedCount = count
        }
    }

    @inlinable
    @inline(__always)
    public func readUTF8String(count: Int) throws -> String {
        try checkBound(count)
        if count == 0 {
            return ""
        }
        let start = readerIndex
        readerIndex = start + count
        let decoded = String(
            bytes: UnsafeBufferPointer(
                start: bytesBaseAddress!.advanced(by: start),
                count: count
            ),
            encoding: .utf8
        )
        guard let decoded else {
            throw ForyError.invalidData("invalid UTF-8 sequence")
        }
        return decoded
    }

    @inlinable
    public func skip(_ count: Int) throws {
        try checkBound(count)
        readerIndex += count
    }

    @inlinable
    public func toData() -> Data {
        guard writerIndex > 0, let storage else {
            return Data()
        }
        return Data(bytes: storage, count: writerIndex)
    }

    @usableFromInline
    @inline(__always)
    internal static func roundCapacity(_ value: Int) -> Int {
        guard value > 0 else {
            return 0
        }
        let word = MemoryLayout<UInt64>.size
        return ((value + word - 1) / word) * word
    }

    @usableFromInline
    @inline(__always)
    internal func ensureCapacity(_ minimumCapacity: Int) {
        guard minimumCapacity > capacity else {
            return
        }
        let newCapacity = Self.roundCapacity(max(minimumCapacity, max(capacity * 2, 8)))
        let newStorage = UnsafeMutableRawPointer.allocate(
            byteCount: newCapacity,
            alignment: MemoryLayout<UInt64>.alignment
        )
        if writerIndex > 0, let storage {
            newStorage.copyMemory(from: storage, byteCount: writerIndex)
            storage.deallocate()
        } else {
            storage?.deallocate()
        }
        storage = newStorage
        capacity = newCapacity
    }

    @usableFromInline
    @inline(__always)
    internal func loadUInt16(at offset: Int) -> UInt16 {
        let raw = UnsafeRawPointer(storage!)
        if ((Int(bitPattern: raw) + offset) & (MemoryLayout<UInt16>.alignment - 1)) == 0 {
            return UInt16(littleEndian: raw.load(fromByteOffset: offset, as: UInt16.self))
        }
        return UInt16(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt16.self))
    }

    @usableFromInline
    @inline(__always)
    internal func loadUInt32(at offset: Int) -> UInt32 {
        let raw = UnsafeRawPointer(storage!)
        if ((Int(bitPattern: raw) + offset) & (MemoryLayout<UInt32>.alignment - 1)) == 0 {
            return UInt32(littleEndian: raw.load(fromByteOffset: offset, as: UInt32.self))
        }
        return UInt32(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt32.self))
    }

    @usableFromInline
    @inline(__always)
    internal func loadUInt64(at offset: Int) -> UInt64 {
        let raw = UnsafeRawPointer(storage!)
        if ((Int(bitPattern: raw) + offset) & (MemoryLayout<UInt64>.alignment - 1)) == 0 {
            return UInt64(littleEndian: raw.load(fromByteOffset: offset, as: UInt64.self))
        }
        return UInt64(littleEndian: raw.loadUnaligned(fromByteOffset: offset, as: UInt64.self))
    }

    @usableFromInline
    @inline(__always)
    internal func putVarUInt32(at offset: Int, value: UInt32) -> Int {
        if value < 0x80 {
            bytesBaseAddress![offset] = UInt8(value)
            return 1
        }
        var encoded = UInt64((value & 0x7F) | 0x80)
        encoded |= UInt64(value & 0x3F80) << 1
        if value < 0x4000 {
            storage!.storeBytes(of: UInt16(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt16.self)
            return 2
        }
        encoded |= (UInt64(value & 0x1FC000) << 2) | 0x8000
        if value < 0x20_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 3
        }
        encoded |= (UInt64(value & 0xFE00000) << 3) | 0x800000
        if value < 0x1000_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 4
        }
        encoded |= (UInt64(value >> 28) << 32) | 0x80000000
        storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
        return 5
    }

    @usableFromInline
    @inline(__always)
    internal func putVarUInt64(at offset: Int, value: UInt64) -> Int {
        if value < 0x80 {
            bytesBaseAddress![offset] = UInt8(value)
            return 1
        }
        var encoded = (value & 0x7F) | 0x80
        encoded |= (value & 0x3F80) << 1
        if value < 0x4000 {
            storage!.storeBytes(of: UInt16(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt16.self)
            return 2
        }
        encoded |= ((value & 0x1FC000) << 2) | 0x8000
        if value < 0x20_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 3
        }
        encoded |= ((value & 0xFE00000) << 3) | 0x800000
        if value < 0x1000_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 4
        }
        encoded |= ((value & 0x7F0000000) << 4) | 0x80000000
        if value < 0x8_0000_0000 {
            storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
            return 5
        }
        encoded |= ((value & 0x3F800000000) << 5) | 0x8000000000
        if value < 0x400_0000_0000 {
            storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
            return 6
        }
        encoded |= ((value & 0x1FC0000000000) << 6) | 0x800000000000
        if value < 0x2_0000_0000_0000 {
            storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
            return 7
        }
        encoded |= ((value & 0xFE000000000000) << 7) | 0x80000000000000
        if value < 0x100_0000_0000_0000 {
            storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
            return 8
        }
        encoded |= 0x8000000000000000
        storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
        bytesBaseAddress![offset + 8] = UInt8(truncatingIfNeeded: value >> 56)
        return 9
    }

    @usableFromInline
    @inline(__always)
    internal func putVarUInt36Small(at offset: Int, value: UInt64) -> Int {
        if value < 0x80 {
            bytesBaseAddress![offset] = UInt8(value)
            return 1
        }
        var encoded = (value & 0x7F) | 0x80
        encoded |= (value & 0x3F80) << 1
        if value < 0x4000 {
            storage!.storeBytes(of: UInt16(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt16.self)
            return 2
        }
        encoded |= ((value & 0x1FC000) << 2) | 0x8000
        if value < 0x20_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 3
        }
        encoded |= ((value & 0xFE00000) << 3) | 0x800000
        if value < 0x1000_0000 {
            storage!.storeBytes(of: UInt32(truncatingIfNeeded: encoded).littleEndian, toByteOffset: offset, as: UInt32.self)
            return 4
        }
        encoded |= ((value & 0xFF0000000) << 4) | 0x80000000
        storage!.storeBytes(of: encoded.littleEndian, toByteOffset: offset, as: UInt64.self)
        return 5
    }

    @usableFromInline
    @inline(__always)
    internal func readVarUInt32Slow() throws -> UInt32 {
        var position = readerIndex
        var result: UInt32 = 0
        var shift: UInt32 = 0
        for index in 0..<5 {
            if position >= writerIndex {
                throw ForyError.outOfBounds(cursor: position, need: 1, length: writerIndex)
            }
            let byte = self.byte(at: position)
            position += 1
            if index == 4, byte >= 0x80 {
                throw ForyError.encodingError("varuint32 overflow")
            }
            result |= UInt32(byte & 0x7F) << shift
            if byte < 0x80 {
                readerIndex = position
                return result
            }
            shift += 7
        }
        throw ForyError.encodingError("varuint32 overflow")
    }

    @usableFromInline
    @inline(__always)
    internal func readVarUInt64Slow() throws -> UInt64 {
        var position = readerIndex
        var result: UInt64 = 0
        var shift: UInt64 = 0
        for _ in 0..<8 {
            if position >= writerIndex {
                throw ForyError.outOfBounds(cursor: position, need: 1, length: writerIndex)
            }
            let byte = self.byte(at: position)
            position += 1
            result |= UInt64(byte & 0x7F) << shift
            if byte < 0x80 {
                readerIndex = position
                return result
            }
            shift += 7
        }
        if position >= writerIndex {
            throw ForyError.outOfBounds(cursor: position, need: 1, length: writerIndex)
        }
        let last = self.byte(at: position)
        position += 1
        result |= UInt64(last) << 56
        readerIndex = position
        return result
    }

    @usableFromInline
    @inline(__always)
    internal func readVarUInt36SmallSlow() throws -> UInt64 {
        var position = readerIndex
        var result: UInt64 = 0
        var shift: UInt64 = 0
        for _ in 0..<4 {
            if position >= writerIndex {
                throw ForyError.outOfBounds(cursor: position, need: 1, length: writerIndex)
            }
            let byte = self.byte(at: position)
            position += 1
            result |= UInt64(byte & 0x7F) << shift
            if byte < 0x80 {
                readerIndex = position
                return result
            }
            shift += 7
        }
        if position >= writerIndex {
            throw ForyError.outOfBounds(cursor: position, need: 1, length: writerIndex)
        }
        let last = self.byte(at: position)
        if last >= 0x80 {
            throw ForyError.encodingError("varuint36small overflow")
        }
        position += 1
        result |= UInt64(last) << 28
        readerIndex = position
        return result
    }
}
// swiftlint:enable type_body_length
