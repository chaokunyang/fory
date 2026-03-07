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

public extension ForyUnsafe {
    @inlinable
    @inline(__always)
    static func writeNumericRegionExact(
        buffer: ByteBuffer,
        exactBytes: Int,
        _ body: (UnsafeMutablePointer<UInt8>) -> Void
    ) {
        guard exactBytes > 0 else {
            return
        }
        let start = buffer.storage.count
        buffer.storage.append(contentsOf: repeatElement(0, count: exactBytes))
        buffer.storage.withUnsafeMutableBufferPointer { storage in
            body(storage.baseAddress!.advanced(by: start))
        }
    }

    @inlinable
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
                .copyMemory(from: UnsafeRawPointer(sourceBase), byteCount: bytes.count)
        }
        return index + bytes.count
    }

    @inlinable
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

    @inlinable
    @inline(__always)
    static func readNumericRegion(
        buffer: ByteBuffer,
        _ body: (UnsafeBufferPointer<UInt8>) throws -> Int
    ) throws {
        let available = buffer.storage.count - buffer.cursor
        let consumed = try buffer.storage.withUnsafeBufferPointer { storage -> Int in
            let start = storage.baseAddress.map { $0.advanced(by: buffer.cursor) }
            let region = UnsafeBufferPointer(start: start, count: available)
            return try body(region)
        }
        if consumed < 0 || consumed > available {
            throw ForyError.outOfBounds(cursor: buffer.cursor, need: consumed, length: buffer.storage.count)
        }
        buffer.cursor += consumed
    }

    @inlinable
    @inline(__always)
    static func checkReadable(
        _ bytes: UnsafeBufferPointer<UInt8>,
        index: Int,
        need: Int
    ) throws {
        if index < 0 || need < 0 || index + need > bytes.count {
            throw ForyError.outOfBounds(cursor: index, need: need, length: bytes.count)
        }
    }
}
