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
import Testing
@testable import Fory

@ForyObject
private struct NestedCodecHolder: Equatable {
    @ForyField(type: .map(
        key: .value(String.self),
        value: .list(element: .int32(nullable: true, encoding: .fixed))
    ))
    var data: [String: [Int32?]] = [:]

    @ForyField(type: .set(element: .uint64(encoding: .tagged)))
    var ids: Set<UInt64> = []

    @ForyField(type: .list(element: .uint32(encoding: .fixed)))
    var numbers: [UInt32] = []
}

@ForyObject
private enum NestedCodecUnion: Equatable {
    case plain(String)
    @ForyField(encoding: .fixed)
    case fixed(value: UInt32)
    @ForyField(type: .map(
        key: .value(String.self),
        value: .list(element: .int32(nullable: true, encoding: .fixed))
    ))
    case nested(data: [String: [Int32?]])
}

@Test
func nestedFieldCodecsRoundTripThroughCollections() throws {
    let fory = Fory(config: .init(xlang: false, trackRef: true, compatible: false))
    fory.register(NestedCodecHolder.self, id: 9810)

    let value = NestedCodecHolder(
        data: [
            "alpha": [1, nil, 3],
            "beta": []
        ],
        ids: [0, UInt64(Int32.max), UInt64(Int32.max) + 1, UInt64.max],
        numbers: [0, 1, UInt32(Int32.max), UInt32.max]
    )

    let decoded: NestedCodecHolder = try fory.deserialize(try fory.serialize(value))
    #expect(decoded == value)
}

@Test
func nestedFieldMetadataReflectsRecursiveCodecTree() {
    let fields = Dictionary(uniqueKeysWithValues: NestedCodecHolder.foryFieldsInfo(trackRef: false).map {
        ($0.fieldName, $0.fieldType)
    })

    let dataField = try! #require(fields["data"])
    #expect(dataField.typeID == TypeId.map.rawValue)
    #expect(dataField.generics.count == 2)
    #expect(dataField.generics[0].typeID == TypeId.string.rawValue)
    #expect(dataField.generics[1].typeID == TypeId.list.rawValue)
    #expect(dataField.generics[1].generics.first?.typeID == TypeId.int32.rawValue)
    #expect(dataField.generics[1].generics.first?.nullable == true)

    let idsField = try! #require(fields["ids"])
    #expect(idsField.typeID == TypeId.set.rawValue)
    #expect(idsField.generics.first?.typeID == TypeId.taggedUInt64.rawValue)

    let numbersField = try! #require(fields["numbers"])
    #expect(numbersField.typeID == TypeId.list.rawValue)
    #expect(numbersField.generics.first?.typeID == TypeId.uint32.rawValue)
}

@Test
func taggedUnionPayloadCodecsRoundTrip() throws {
    let fory = Fory(config: .init(xlang: false, trackRef: false, compatible: false))
    fory.register(NestedCodecUnion.self, id: 9811)

    let values: [NestedCodecUnion] = [
        .plain("plain"),
        .fixed(value: UInt32.max),
        .nested(data: ["alpha": [1, nil, 2], "beta": []])
    ]

    for value in values {
        let decoded: NestedCodecUnion = try fory.deserialize(try fory.serialize(value))
        #expect(decoded == value)
    }
}
