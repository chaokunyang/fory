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
private struct LargeTagSource {
    @ForyField(id: 65551)
    var original: Int32 = 0
}

@ForyStruct
private struct LargeTagTarget {
    @ForyField(id: 65551)
    var renamed: Int32 = 0
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
        do {
            let _: StaticDepthNode = try limited.deserialize(deepBytes)
            Issue.record("expected maxDepth failure")
        } catch ForyError.invalidData(let message) {
            #expect(message.contains("maxDepth"))
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
func largeFieldTagCompatiblePath() throws {
    let fields = LargeTagSource.foryFieldsInfo(trackRef: false)
    #expect(fields.map(\.fieldID) == [65_551])

    let writer = Fory(config: .init(compatible: true))
    try writer.register(LargeTagSource.self, id: 11_991)
    let bytes = try writer.serialize(LargeTagSource(original: 73))
    let sourceInfo = try writer.typeResolver.requireTypeInfo(for: LargeTagSource.self)
    let sourceMeta = try #require(sourceInfo.typeMeta)
    #expect(sourceMeta.fields[0].fieldID == 65_551)
    let decodedMeta = try TypeMeta.decode(sourceMeta.encode())
    #expect(decodedMeta.fields[0].fieldID == 65_551)

    let reader = Fory(config: .init(compatible: true))
    try reader.register(LargeTagTarget.self, id: 11_991)
    let decoded: LargeTagTarget = try reader.deserialize(bytes)
    #expect(decoded.renamed == 73)

    let targetInfo = try reader.typeResolver.requireTypeInfo(for: LargeTagTarget.self)
    let targetMeta = try #require(targetInfo.typeMeta)
    let matchedMeta = try decodedMeta.assigningFieldIDs(from: targetMeta)
    #expect(matchedMeta.fields[0].fieldID == 65_551)
    #expect(matchedMeta.fields[0].matchedFieldID == 0)
}

@Test
func fullFieldTagTypeMetaRoundTrip() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let meta = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 11_992,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: UInt32.max,
                fieldName: "maximum",
                fieldType: TypeMeta.FieldType(
                    typeID: TypeId.int32.rawValue,
                    nullable: false
                )
            )
        ]
    )

    let decoded = try TypeMeta.decode(meta.encode())
    #expect(decoded.fields[0].fieldID == UInt32.max)
    #expect(decoded.fields[0].matchedFieldID == nil)
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
