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

@ForyStruct
private final class StaticDepthNode {
    var value: Int32 = 0
    var next: StaticDepthNode?
}

@ForyStruct
private struct StaticDepthLeaf: Equatable {
    var count: Int32 = 0
    var enabled: Bool = false
}

@ForyStruct
private struct StaticDepthArrayLeaf: Equatable {
    @ForyField(type: .array(element: .int32()))
    var values: [Int32] = []
}

@ForyUnion
private enum StaticDepthLeafUnion: Equatable {
    @ForyUnknownCase
    case unknown(UnknownCase)
    case empty
    case count(Int32)
}

@ForyStruct
private struct WideTagSource {
    @ForyField(id: 32768)
    var first: String = ""

    @ForyField(id: 65551)
    var second: String = ""

    @ForyField(id: 268435456)
    var third: String = ""

    @ForyField(id: 536870911)
    var retained: String = ""
}

@ForyStruct
private struct WideTagTarget {
    @ForyField(id: 536870911)
    var renamed: String = ""
}

@Test
func staticCompoundDepthIsBounded() throws {
    for compatible in [false, true] {
        let writer = Fory(config: .init(compatible: compatible, maxDepth: 8))
        try writer.register(StaticDepthNode.self, id: 11_990)
        let deepBytes = try writer.serialize(depthNode(count: 3))
        let shallowBytes = try writer.serialize(depthNode(count: 2))

        let limited = Fory(config: .init(compatible: compatible, maxDepth: 2))
        try limited.register(StaticDepthNode.self, id: 11_990)
        #expect(throws: (any Error).self) {
            let _: StaticDepthNode = try limited.deserialize(deepBytes)
        }

        let reused: StaticDepthNode = try limited.deserialize(shallowBytes)
        #expect(reused.value == 1)
        #expect(reused.next?.value == 2)
        #expect(reused.next?.next == nil)

        let boundary = Fory(config: .init(compatible: compatible, maxDepth: 3))
        try boundary.register(StaticDepthNode.self, id: 11_990)
        let decoded: StaticDepthNode = try boundary.deserialize(deepBytes)
        #expect(decoded.next?.next?.value == 3)
    }
}

@Test
func leafCompoundReadersSkipDepth() throws {
    for compatible in [false, true] {
        let fory = Fory(config: .init(compatible: compatible, maxDepth: 0))
        try fory.register(StaticDepthLeaf.self, id: 11_992)
        try fory.register(StaticDepthLeafUnion.self, id: 11_993)
        try fory.register(StaticDepthArrayLeaf.self, id: 11_995)

        let leaf = StaticDepthLeaf(count: 42, enabled: true)
        let decodedLeaf: StaticDepthLeaf = try fory.deserialize(try fory.serialize(leaf))
        #expect(decodedLeaf == leaf)

        let union = StaticDepthLeafUnion.count(42)
        let decodedUnion: StaticDepthLeafUnion = try fory.deserialize(try fory.serialize(union))
        #expect(decodedUnion == union)

        let arrayLeaf = StaticDepthArrayLeaf(values: [1, 2, 3])
        let decodedArrayLeaf: StaticDepthArrayLeaf = try fory.deserialize(
            try fory.serialize(arrayLeaf))
        #expect(decodedArrayLeaf == arrayLeaf)
    }
}

@Test
func wideTagsSkipAndAlign() throws {
    let expectedIDs: [Int32] = [32_768, 65_551, 268_435_456, 536_870_911]
    let fields = WideTagSource.foryFieldsInfo(trackRef: false)
    #expect(fields.map(\.fieldID) == expectedIDs)

    let writer = Fory(config: .init(compatible: true))
    try writer.register(WideTagSource.self, id: 11_991)
    let bytes = try writer.serialize(
        WideTagSource(first: "skip-1", second: "skip-2", third: "skip-3", retained: "kept")
    )
    let sourceInfo = try writer.typeResolver.requireTypeInfo(for: WideTagSource.self)
    let sourceMeta = try #require(sourceInfo.typeMeta)
    #expect(sourceMeta.fields.map(\.fieldID) == expectedIDs)
    let decodedMeta = try TypeMeta.decode(sourceMeta.encode())
    #expect(decodedMeta.fields.map(\.fieldID) == expectedIDs)

    let reader = Fory(config: .init(compatible: true))
    try reader.register(WideTagTarget.self, id: 11_991)
    let decoded: WideTagTarget = try reader.deserialize(bytes)
    #expect(decoded.renamed == "kept")

    let targetInfo = try reader.typeResolver.requireTypeInfo(for: WideTagTarget.self)
    let targetMeta = try #require(targetInfo.typeMeta)
    let matchedMeta = try decodedMeta.assigningFieldIDs(from: targetMeta)
    #expect(matchedMeta.fields.map(\.matchedFieldID) == [-1, -1, -1, 0])
}

@Test
func fieldTagDomainIsBounded() throws {
    let fieldType = TypeMeta.FieldType(typeID: TypeId.int32.rawValue, nullable: false)
    let maxField = TypeMeta.FieldInfo(
        fieldID: 536_870_911,
        fieldName: "maximum",
        fieldType: fieldType
    )
    let meta = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 11_994,
        namespace: .empty(specialChar1: ".", specialChar2: "_"),
        typeName: .empty(specialChar1: "$", specialChar2: "_"),
        registerByName: false,
        fields: [maxField]
    )
    let decoded = try TypeMeta.decode(meta.encode())
    #expect(decoded.fields.map(\.fieldID) == [536_870_911])

    #expect(throws: (any Error).self) {
        _ = try TypeMeta(
            typeID: TypeId.compatibleStruct.rawValue,
            userTypeID: 11_994,
            namespace: .empty(specialChar1: ".", specialChar2: "_"),
            typeName: .empty(specialChar1: "$", specialChar2: "_"),
            registerByName: false,
            fields: [TypeMeta.FieldInfo(fieldID: -2, fieldName: "invalid", fieldType: fieldType)]
        )
    }
    #expect(throws: (any Error).self) {
        _ = try TypeMeta(
            typeID: TypeId.compatibleStruct.rawValue,
            userTypeID: 11_994,
            namespace: .empty(specialChar1: ".", specialChar2: "_"),
            typeName: .empty(specialChar1: "$", specialChar2: "_"),
            registerByName: false,
            fields: [
                TypeMeta.FieldInfo(fieldID: 536_870_912, fieldName: "invalid", fieldType: fieldType)
            ]
        )
    }
}

private func depthNode(count: Int) -> StaticDepthNode {
    precondition(count > 0)
    let root = StaticDepthNode()
    root.value = 1
    var current = root
    if count > 1 {
        for value in 2...count {
            let next = StaticDepthNode()
            next.value = Int32(value)
            current.next = next
            current = next
        }
    }
    return root
}
