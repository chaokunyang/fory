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

final class ObjectIdMap {
    private static let emptyKey = UInt64.max
    private static let defaultLoadFactor = 0.5
    private static let goldenRatio: UInt64 = 0x9E3779B97F4A7C15

    private var keys: [UInt64]
    private var values: [UInt32]
    private var mask: Int
    private var shift: Int
    private var size = 0
    private let loadFactor: Double
    private var growThreshold: Int

    init(initialCapacity: Int = 64, loadFactor: Double = ObjectIdMap.defaultLoadFactor) {
        self.loadFactor = loadFactor
        let capacity = Self.nextPowerOfTwo(max(initialCapacity, 8))
        keys = Array(repeating: Self.emptyKey, count: capacity)
        values = Array(repeating: 0, count: capacity)
        mask = capacity - 1
        shift = UInt64.bitWidth - UInt64(capacity).trailingZeroBitCount
        growThreshold = Int(Double(capacity) * loadFactor)
    }

    var count: Int {
        size
    }

    var capacity: Int {
        keys.count
    }

    var isEmpty: Bool {
        size == 0
    }

    @inline(__always)
    func value(for key: ObjectIdentifier) -> UInt32? {
        let rawKey = rawKey(for: key)
        if rawKey == Self.emptyKey {
            return nil
        }

        var index = place(rawKey)
        while true {
            let existingKey = keys[index]
            if existingKey == rawKey {
                return values[index]
            }
            if existingKey == Self.emptyKey {
                return nil
            }
            index = (index + 1) & mask
        }
    }

    @inline(__always)
    func putIfAbsent(_ value: UInt32, for key: ObjectIdentifier) -> (value: UInt32, inserted: Bool) {
        let rawKey = rawKey(for: key)
        if rawKey == Self.emptyKey {
            return (value, false)
        }
        if size >= growThreshold {
            grow()
        }

        let index = findSlotForInsert(rawKey)
        if keys[index] == Self.emptyKey {
            keys[index] = rawKey
            values[index] = value
            size += 1
            return (value, true)
        }
        return (values[index], false)
    }

    func removeAll(keepingCapacity: Bool = true) {
        if !keepingCapacity {
            let capacity = Self.nextPowerOfTwo(8)
            keys = Array(repeating: Self.emptyKey, count: capacity)
            values = Array(repeating: 0, count: capacity)
            mask = capacity - 1
            shift = UInt64.bitWidth - UInt64(capacity).trailingZeroBitCount
            growThreshold = Int(Double(capacity) * loadFactor)
            size = 0
            return
        }

        if !isEmpty {
            for index in keys.indices {
                keys[index] = Self.emptyKey
            }
            size = 0
        }
    }

    @inline(__always)
    private func rawKey(for key: ObjectIdentifier) -> UInt64 {
        let rawKey = UInt64(UInt(bitPattern: key))
        precondition(rawKey != Self.emptyKey, "ObjectIdentifier raw key collided with reserved empty marker")
        return rawKey
    }

    @inline(__always)
    private func findSlotForInsert(_ rawKey: UInt64) -> Int {
        var index = place(rawKey)
        while keys[index] != Self.emptyKey && keys[index] != rawKey {
            index = (index + 1) & mask
        }
        return index
    }

    @inline(__always)
    private func place(_ rawKey: UInt64) -> Int {
        Int((rawKey &* Self.goldenRatio) >> shift)
    }

    private func grow() {
        let oldKeys = keys
        let oldValues = values
        let newCapacity = capacity * 2
        keys = Array(repeating: Self.emptyKey, count: newCapacity)
        values = Array(repeating: 0, count: newCapacity)
        mask = newCapacity - 1
        shift = UInt64.bitWidth - UInt64(newCapacity).trailingZeroBitCount
        growThreshold = Int(Double(newCapacity) * loadFactor)
        size = 0

        for index in oldKeys.indices where oldKeys[index] != Self.emptyKey {
            let newIndex = findSlotForInsert(oldKeys[index])
            keys[newIndex] = oldKeys[index]
            values[newIndex] = oldValues[index]
            size += 1
        }
    }

    private static func nextPowerOfTwo(_ value: Int) -> Int {
        if value <= 1 {
            return 1
        }
        var valueBits = UInt(value - 1)
        valueBits |= valueBits >> 1
        valueBits |= valueBits >> 2
        valueBits |= valueBits >> 4
        valueBits |= valueBits >> 8
        valueBits |= valueBits >> 16
        if UInt.bitWidth > 32 {
            valueBits |= valueBits >> 32
        }
        return Int(valueBits + 1)
    }
}
