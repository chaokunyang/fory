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

private let uint64MapEmptyKey = UInt64.max
private let uint64MapDefaultLoadFactor = 0.5
private let uint64MapGoldenRatio: UInt64 = 0x9E3779B97F4A7C15

/// Internal open-addressed UInt64-key map for runtime caches in Fory Swift
/// (for example type metadata caches in resolver/read/write contexts).
/// This is not part of the public API surface.
final class UInt64Map<Value> {

    private struct Slot {
        var key: UInt64
        var value: Value?
    }

    private var entries: [Slot]
    private var mask: Int
    private var shift: Int
    private var size = 0
    private let loadFactor: Double
    private var growThreshold: Int

    private var hasMaxKey = false
    private var maxKeyValue: Value?

    init(initialCapacity: Int = 2, loadFactor: Double = uint64MapDefaultLoadFactor) {
        self.loadFactor = loadFactor
        let capacity = Self.nextPowerOfTwo(max(initialCapacity, 2))
        let emptySlot = Slot(key: uint64MapEmptyKey, value: nil)
        entries = Array(repeating: emptySlot, count: capacity)
        mask = capacity - 1
        shift = UInt64.bitWidth - UInt64(capacity).trailingZeroBitCount
        growThreshold = Int(Double(capacity) * loadFactor)
    }

    var count: Int {
        size
    }

    var capacity: Int {
        entries.count
    }

    var isEmpty: Bool {
        size == 0
    }

    @inline(__always)
    func value(for key: UInt64) -> Value? {
        if key == uint64MapEmptyKey {
            return hasMaxKey ? maxKeyValue : nil
        }

        var index = place(key)
        while true {
            let entry = entries[index]
            if entry.key == key {
                return entry.value
            }
            if entry.key == uint64MapEmptyKey {
                return nil
            }
            index = (index + 1) & mask
        }
    }

    @inline(__always)
    func set(_ value: Value, for key: UInt64) {
        if key == uint64MapEmptyKey {
            if !hasMaxKey {
                hasMaxKey = true
                size += 1
            }
            maxKeyValue = value
            return
        }

        if size >= growThreshold {
            grow()
        }

        let index = findSlotForInsert(key)
        if entries[index].key == uint64MapEmptyKey {
            entries[index].key = key
            size += 1
        }
        entries[index].value = value
    }

    @inline(__always)
    func putIfAbsent(_ value: Value, for key: UInt64) -> (value: Value, inserted: Bool) {
        if key == uint64MapEmptyKey {
            if hasMaxKey, let maxKeyValue {
                return (maxKeyValue, false)
            }
            hasMaxKey = true
            maxKeyValue = value
            size += 1
            return (value, true)
        }

        if size >= growThreshold {
            grow()
        }

        let index = findSlotForInsert(key)
        if entries[index].key == uint64MapEmptyKey {
            entries[index].key = key
            entries[index].value = value
            size += 1
            return (value, true)
        }

        guard let existingValue = entries[index].value else {
            fatalError("corrupted UInt64Map state: occupied slot has nil value")
        }
        return (existingValue, false)
    }

    @discardableResult
    func removeValue(for key: UInt64) -> Value? {
        if key == uint64MapEmptyKey {
            guard hasMaxKey else {
                return nil
            }
            hasMaxKey = false
            size -= 1
            let removed = maxKeyValue
            maxKeyValue = nil
            return removed
        }

        var index = place(key)
        while true {
            let entry = entries[index]
            if entry.key == key {
                let removed = entry.value
                removeEntry(at: index)
                return removed
            }
            if entry.key == uint64MapEmptyKey {
                return nil
            }
            index = (index + 1) & mask
        }
    }

    func clear() {
        if !isEmpty {
            entries.withUnsafeMutableBufferPointer { buffer in
                guard let base = buffer.baseAddress else {
                    return
                }
                base.update(
                    repeating: Slot(key: uint64MapEmptyKey, value: nil),
                    count: buffer.count
                )
            }
            hasMaxKey = false
            maxKeyValue = nil
            size = 0
        }
    }

    @inline(__always)
    private func findSlotForInsert(_ key: UInt64) -> Int {
        var index = place(key)
        while entries[index].key != uint64MapEmptyKey && entries[index].key != key {
            index = (index + 1) & mask
        }
        return index
    }

    @inline(__always)
    private func place(_ key: UInt64) -> Int {
        Int((key &* uint64MapGoldenRatio) >> shift)
    }

    private func grow() {
        let oldEntries = entries
        let newCapacity = oldEntries.count * 2

        entries = Array(
            repeating: Slot(key: uint64MapEmptyKey, value: nil),
            count: newCapacity
        )
        mask = newCapacity - 1
        shift = UInt64.bitWidth - UInt64(newCapacity).trailingZeroBitCount
        growThreshold = Int(Double(newCapacity) * loadFactor)
        size = hasMaxKey ? 1 : 0

        for oldEntry in oldEntries where oldEntry.key != uint64MapEmptyKey {
            let newIndex = findSlotForInsert(oldEntry.key)
            entries[newIndex] = oldEntry
            size += 1
        }
    }

    private func removeEntry(at index: Int) {
        var hole = index
        var cursor = (hole + 1) & mask

        while entries[cursor].key != uint64MapEmptyKey {
            let idealSlot = place(entries[cursor].key)
            if shouldMoveEntry(idealSlot: idealSlot, candidateSlot: cursor, emptySlot: hole) {
                entries[hole] = entries[cursor]
                hole = cursor
            }
            cursor = (cursor + 1) & mask
        }

        entries[hole] = Slot(key: uint64MapEmptyKey, value: nil)
        size -= 1
    }

    @inline(__always)
    private func shouldMoveEntry(idealSlot: Int, candidateSlot: Int, emptySlot: Int) -> Bool {
        if emptySlot <= candidateSlot {
            return idealSlot <= emptySlot || idealSlot > candidateSlot
        }
        return idealSlot <= emptySlot && idealSlot > candidateSlot
    }

    private static func nextPowerOfTwo(_ value: Int) -> Int {
        if value <= 1 {
            return 1
        }
        var valueBits = UInt64(value - 1)
        valueBits |= valueBits >> 1
        valueBits |= valueBits >> 2
        valueBits |= valueBits >> 4
        valueBits |= valueBits >> 8
        valueBits |= valueBits >> 16
        if UInt64.bitWidth > 32 {
            valueBits |= valueBits >> 32
        }
        return Int(valueBits + 1)
    }
}
