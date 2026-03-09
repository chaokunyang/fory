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

import Testing
@testable import Fory

private final class ObjectIdBox {}

@Test
func objectIdMapInsertFindAndUpdate() {
    let map = ObjectIdMap(initialCapacity: 8)
    let first = ObjectIdBox()
    let second = ObjectIdBox()

    let firstInsert = map.putIfAbsent(3, for: ObjectIdentifier(first))
    #expect(firstInsert.inserted)
    #expect(firstInsert.value == 3)

    let secondInsert = map.putIfAbsent(9, for: ObjectIdentifier(second))
    #expect(secondInsert.inserted)
    #expect(secondInsert.value == 9)

    let firstExisting = map.putIfAbsent(11, for: ObjectIdentifier(first))
    #expect(!firstExisting.inserted)
    #expect(firstExisting.value == 3)

    #expect(map.value(for: ObjectIdentifier(first)) == 3)
    #expect(map.value(for: ObjectIdentifier(second)) == 9)
    #expect(map.count == 2)
}

@Test
func objectIdMapGrowRetainsEntries() {
    let map = ObjectIdMap(initialCapacity: 8)
    var boxes: [ObjectIdBox] = []

    for index in 0 ..< 64 {
        let box = ObjectIdBox()
        boxes.append(box)
        let insert = map.putIfAbsent(UInt32(index), for: ObjectIdentifier(box))
        #expect(insert.inserted)
        #expect(insert.value == UInt32(index))
    }

    #expect(map.capacity >= 64)
    #expect(map.count == 64)

    for index in 0 ..< boxes.count {
        #expect(map.value(for: ObjectIdentifier(boxes[index])) == UInt32(index))
    }
}

@Test
func objectIdMapRemoveAllKeepingCapacityClearsEntries() {
    let map = ObjectIdMap(initialCapacity: 8)
    let box = ObjectIdBox()
    let boxID = ObjectIdentifier(box)
    _ = map.putIfAbsent(7, for: boxID)
    let previousCapacity = map.capacity

    map.removeAll(keepingCapacity: true)

    #expect(map.isEmpty)
    #expect(map.capacity == previousCapacity)
    #expect(map.value(for: boxID) == nil)
}
