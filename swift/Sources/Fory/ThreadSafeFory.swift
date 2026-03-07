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

/// Thread-safe wrapper around a single `Fory` instance.
///
/// Use this when one configured runtime must be shared across threads. Calls
/// are serialized behind a lock, so plain `Fory` remains the fastest option for
/// thread-confined workloads.
public final class ThreadSafeFory: @unchecked Sendable {
    public let config: ForyConfig

    private let base: Fory
    private let lock = NSLock()

    public init(
        xlang: Bool = true,
        trackRef: Bool = false,
        compatible: Bool = false,
        checkClassVersion: Bool? = nil,
        maxCollectionSize: Int = 1_000_000,
        maxBinarySize: Int = 64 * 1024 * 1024,
        maxDepth: Int = 5
    ) {
        let config = ForyConfig.resolved(
            xlang: xlang,
            trackRef: trackRef,
            compatible: compatible,
            checkClassVersion: checkClassVersion,
            maxCollectionSize: maxCollectionSize,
            maxBinarySize: maxBinarySize,
            maxDepth: maxDepth
        )
        self.config = config
        self.base = Fory(config: config)
    }

    public convenience init(config: ForyConfig) {
        self.init(
            xlang: config.xlang,
            trackRef: config.trackRef,
            compatible: config.compatible,
            checkClassVersion: config.checkClassVersion,
            maxCollectionSize: config.maxCollectionSize,
            maxBinarySize: config.maxBinarySize,
            maxDepth: config.maxDepth
        )
    }

    @inline(__always)
    private func withLock<T>(_ body: () throws -> T) rethrows -> T {
        lock.lock()
        defer { lock.unlock() }
        return try body()
    }

    @inline(__always)
    private func withBase<T>(_ body: (Fory) throws -> T) rethrows -> T {
        try withLock {
            try body(base)
        }
    }

    public func register<T: Serializer>(_ type: T.Type, id: UInt32) {
        withBase {
            $0.register(type, id: id)
        }
    }

    public func register<T: Serializer>(_ type: T.Type, name: String) throws {
        try withBase {
            try $0.register(type, name: name)
        }
    }

    public func register<T: Serializer>(_ type: T.Type, namespace: String, name: String) throws {
        try withBase {
            try $0.register(type, namespace: namespace, name: name)
        }
    }

    public func serialize<T: Serializer>(_ value: T) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    public func deserialize<T: Serializer>(_ data: Data, as _: T.Type = T.self) throws -> T {
        try withBase {
            try $0.deserialize(data, as: T.self)
        }
    }

    public func serialize<T: Serializer>(_ value: T, to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    public func deserialize<T: Serializer>(from buffer: ByteBuffer, as _: T.Type = T.self) throws -> T {
        try withBase {
            try $0.deserialize(from: buffer, as: T.self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: Any) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: Any.Type = Any.self) throws -> Any {
        try withBase {
            try $0.deserialize(data, as: Any.self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: AnyObject) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: AnyObject.Type = AnyObject.self) throws -> AnyObject {
        try withBase {
            try $0.deserialize(data, as: AnyObject.self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: any Serializer) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: (any Serializer).Type = (any Serializer).self) throws -> any Serializer {
        try withBase {
            try $0.deserialize(data, as: (any Serializer).self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [Any]) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: [Any].Type = [Any].self) throws -> [Any] {
        try withBase {
            try $0.deserialize(data, as: [Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [String: Any]) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: [String: Any].Type = [String: Any].self) throws -> [String: Any] {
        try withBase {
            try $0.deserialize(data, as: [String: Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [Int32: Any]) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: [Int32: Any].Type = [Int32: Any].self) throws -> [Int32: Any] {
        try withBase {
            try $0.deserialize(data, as: [Int32: Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [AnyHashable: Any]) throws -> Data {
        try withBase {
            try $0.serialize(value)
        }
    }

    @_disfavoredOverload
    public func deserialize(_ data: Data, as _: [AnyHashable: Any].Type = [AnyHashable: Any].self) throws -> [AnyHashable: Any] {
        try withBase {
            try $0.deserialize(data, as: [AnyHashable: Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [Any], to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: Any, to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: Any.Type = Any.self) throws -> Any {
        try withBase {
            try $0.deserialize(from: buffer, as: Any.self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: AnyObject, to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: AnyObject.Type = AnyObject.self) throws -> AnyObject {
        try withBase {
            try $0.deserialize(from: buffer, as: AnyObject.self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: any Serializer, to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func deserialize(
        from buffer: ByteBuffer,
        as _: (any Serializer).Type = (any Serializer).self
    ) throws -> any Serializer {
        try withBase {
            try $0.deserialize(from: buffer, as: (any Serializer).self)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: [Any].Type = [Any].self) throws -> [Any] {
        try withBase {
            try $0.deserialize(from: buffer, as: [Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [String: Any], to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: [String: Any].Type = [String: Any].self) throws -> [String: Any] {
        try withBase {
            try $0.deserialize(from: buffer, as: [String: Any].self)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [Int32: Any], to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func serialize(_ value: [AnyHashable: Any], to buffer: inout Data) throws {
        try withBase {
            try $0.serialize(value, to: &buffer)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: [Int32: Any].Type = [Int32: Any].self) throws -> [Int32: Any] {
        try withBase {
            try $0.deserialize(from: buffer, as: [Int32: Any].self)
        }
    }

    @_disfavoredOverload
    public func deserialize(from buffer: ByteBuffer, as _: [AnyHashable: Any].Type = [AnyHashable: Any].self) throws -> [AnyHashable: Any] {
        try withBase {
            try $0.deserialize(from: buffer, as: [AnyHashable: Any].self)
        }
    }
}
