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

@ForyStruct
private struct CompatibleProfileV1: Equatable {
    var id: Int32 = 0
    var name: String = ""
}

@ForyStruct
private struct CompatibleProfileV2: Equatable {
    var id: Int32 = 0
    var name: String = ""
    var nickname: String = "guest"
    var scores: [Int32] = []
}

@ForyStruct
private struct CompatibleNestedProfileV1: Equatable {
    var id: Int32 = 0
    var name: String = ""
}

@ForyStruct
private struct CompatibleNestedProfileV2: Equatable {
    var id: Int32 = 0
    var name: String = ""
    var alias: String = ""
    var scores: [Int32] = []
}

@ForyStruct
private struct CompatibleNestedArrayV1: Equatable {
    var items: [CompatibleNestedProfileV1] = []
}

@ForyStruct
private struct CompatibleNestedArrayV2: Equatable {
    var items: [CompatibleNestedProfileV2] = []
}

@ForyStruct
private struct CompatibleNestedMapV1: Equatable {
    var items: [Int32: CompatibleNestedProfileV1] = [:]
}

@ForyStruct
private struct CompatibleNestedMapV2: Equatable {
    var items: [Int32: CompatibleNestedProfileV2] = [:]
}

@ForyStruct
private struct OptionalContainersV1: Equatable {
    @ForyField(id: 1)
    var list: [String]?

    @ForyField(id: 2)
    var set: Set<String>?

    @ForyField(id: 3)
    var map: [String: Int32]?
}

@ForyStruct
private struct RequiredListV2: Equatable {
    @ForyField(id: 1)
    var list: [String] = []
}

@ForyStruct
private struct RequiredSetV2: Equatable {
    @ForyField(id: 2)
    var set: Set<String> = []
}

@ForyStruct
private struct RequiredMapV2: Equatable {
    @ForyField(id: 3)
    var map: [String: Int32] = [:]
}

@ForyStruct
private struct RemoteFixedUInt32V1: Equatable {
    @ForyField(id: 1, encoding: .fixed)
    var id: UInt32 = 0

    @ForyField(id: 2)
    var keep: Int32 = 0

}

@ForyStruct
private struct LocalVarUInt32V2: Equatable {
    @ForyField(id: 1)
    var id: UInt32 = 0

    @ForyField(id: 2)
    var keep: Int32 = 0

}

@ForyStruct
private struct ExactEncodedV1: Equatable {
    @ForyField(id: 1, encoding: .fixed)
    var fixed32: Int32 = 0

    @ForyField(id: 2, encoding: .fixed)
    var fixed64: Int64 = 0

    @ForyField(id: 3, encoding: .tagged)
    var tagged64: Int64 = 0

    @ForyField(id: 4)
    var tail: String = ""
}

@ForyStruct
private struct ExactEncodedV2: Equatable {
    @ForyField(id: 0)
    var added: Int32 = 0

    @ForyField(id: 3, encoding: .tagged)
    var tagged64: Int64 = 0

    @ForyField(id: 1, encoding: .fixed)
    var fixed32: Int32 = 0

    @ForyField(id: 4)
    var tail: String = ""

    @ForyField(id: 2, encoding: .fixed)
    var fixed64: Int64 = 0
}

@ForyStruct
private struct ScalarBoolBox: Equatable {
    var value: Bool = false
}

@ForyStruct
private struct ScalarBoolOptBox: Equatable {
    var value: Bool?
}

@ForyStruct
private struct ScalarStringBox: Equatable {
    var value: String = ""
}

@ForyStruct
private struct ScalarStringOptBox: Equatable {
    var value: String?
}

@ForyStruct
private struct ScalarInt32Box: Equatable {
    var value: Int32 = 0
}

@ForyStruct
private struct ScalarUInt8Box: Equatable {
    var value: UInt8 = 0
}

@ForyStruct
private struct ScalarDoubleBox: Equatable {
    var value: Double = 0
}

@ForyStruct
private struct ScalarFloat16Box: Equatable {
    var value: Float16 = Float16(0)
}

@ForyStruct
private struct ScalarBFloat16Box: Equatable {
    var value: BFloat16 = BFloat16()
}

@ForyStruct
private struct ScalarDecimalBox: Equatable {
    var value: Decimal = .zero
}

@ForyStruct
private final class RemovedRefChild {
    var value: Int32 = 0

    required init() {}

    init(value: Int32) {
        self.value = value
    }
}

@ForyStruct
private struct RemovedRefV1 {
    @ForyField(id: 1)
    var first: RemovedRefChild?

    @ForyField(id: 2)
    var second: RemovedRefChild?

    @ForyField(id: 3)
    var keep: Int32 = 0
}

@ForyStruct
private struct RemovedRefV2: Equatable {
    @ForyField(id: 3)
    var keep: Int32 = 0
}

@ForyStruct
private struct RemoteFallbackBoolBox: Equatable {
    var remoteValue: Bool = false
}

@ForyStruct
private struct LocalFallbackStringBox: Equatable {
    var localValue: String = ""
}

@ForyStruct
private struct SkippedDynamicMapV1 {
    @ForyField(id: 1)
    var removed: [AnyHashable: Any] = [:]

    @ForyField(id: 2)
    var keep: Int32 = 0
}

@ForyStruct
private struct SkippedDynamicMapV2 {
    @ForyField(id: 2)
    var keep: Int32 = 0
}

@ForyUnion
private indirect enum SkippedDepthUnion: Equatable {
    @ForyUnknownCase
    case unknown(UnknownCase)
    case empty
    case text(String)
    case child(SkippedDepthUnion)
}

@ForyStruct
private struct SkippedUnionV1 {
    @ForyField(id: 1)
    var removed: SkippedDepthUnion = .empty

    @ForyField(id: 2)
    var keep: Int32 = 0
}

@ForyStruct
private struct SkippedUnionV2: Equatable {
    @ForyField(id: 2)
    var keep: Int32 = 0
}

@ForyStruct
private struct RemoteNestedFixedMapV1: Equatable {
    @ForyField(id: 1)
    @MapField(value: .list(element: .encoding(.fixed)))
    var data: [String: [Int32?]] = [:]

    @ForyField(id: 2)
    var keep: Int32 = 0

    @ForyField(id: 3)
    @SetField(element: .encoding(.fixed))
    var ids: Set<Int32?> = []
}

@ForyStruct
private struct SchemaHashNestedAnnotatedContainer: Equatable {
    @MapField(key: .uint32(encoding: .fixed), value: .list(element: .uint64(encoding: .tagged)))
    var values: [UInt32?: [UInt64?]?] = [:]
}

@ForyStruct
private struct LocalNestedVarintMapV2: Equatable {
    @ForyField(id: 1)
    var data: [String: [Int32?]] = [:]

    @ForyField(id: 2)
    var keep: Int32 = 0

    @ForyField(id: 3)
    var ids: Set<Int32?> = []
}

@ForyStruct
private struct CompatibleListFieldV1: Equatable {
    @ListField(element: .int32(encoding: .fixed))
    var values: [Int32] = []

    var extra: Int32 = 0
}

@ForyStruct
private struct CompatibleVarintListFieldV1: Equatable {
    @ListField(element: .int32())
    var values: [Int32] = []

    var extra: Int32 = 0
}

@ForyStruct
private struct CompatibleArrayFieldV2: Equatable {
    @ArrayField(element: .int32())
    var values: [Int32] = []
}

@ForyStruct
private struct CompatibleNullableListFieldV1: Equatable {
    @ListField(element: .int32(nullable: true, encoding: .fixed))
    var values: [Int32?] = []

    var extra: Int32 = 0
}

@ForyStruct
private struct CompatibleNestedNullableListV1: Equatable {
    @ListField(element: .list(element: .int32(nullable: true)))
    var values: [[Int32?]] = []
}

@ForyStruct
private struct CompatibleNestedRequiredListV2: Equatable {
    @ListField(element: .list(element: .int32()))
    var values: [[Int32]] = []
}

@ForyStruct
private struct CompatibleNestedListArrayFieldV1: Equatable {
    @ListField(element: .list(element: .int32(encoding: .fixed)))
    var values: [[Int32]] = []

    var keep: Int32 = 0
}

@ForyStruct
private struct CompatibleNestedArrayListFieldV2: Equatable {
    @ListField(element: .array(element: .int32()))
    var values: [[Int32]] = []

    var keep: Int32 = 0
}

@ForyStruct
private struct SchemaVersionV1: Equatable {
    var id: Int32 = 0
    var name: String = ""
}

@ForyStruct
private struct SchemaVersionV2: Equatable {
    var id: Int32 = 0
    var alias: String = ""
    var count: Int32 = 0
}

@ForyStruct
private final class CompatibleGraphNode {
    var value: Int32 = 0
    var next: CompatibleGraphNode?

    required init() {}

    init(value: Int32, next: CompatibleGraphNode? = nil) {
        self.value = value
        self.next = next
    }
}

@ForyStruct
private final class CompatibleGraphContainer {
    var first: CompatibleGraphNode?
    var second: CompatibleGraphNode?
    var items: [CompatibleGraphNode] = []
    var byName: [String: CompatibleGraphNode] = [:]

    required init() {}

    init(
        first: CompatibleGraphNode?,
        second: CompatibleGraphNode?,
        items: [CompatibleGraphNode],
        byName: [String: CompatibleGraphNode]
    ) {
        self.first = first
        self.second = second
        self.items = items
        self.byName = byName
    }
}

private func compatibleDecode<Writer: Serializer, Reader: Serializer>(
    _ value: Writer,
    as _: Reader.Type,
    id: UInt32
) throws -> Reader where Writer.Target == Writer, Reader.Target == Reader {
    try prepareCompatibleDecode(value, as: Reader.self, id: id)()
}

private func prepareCompatibleDecode<Writer: Serializer, Reader: Serializer>(
    _ value: Writer,
    as _: Reader.Type,
    id: UInt32
) throws -> () throws -> Reader where Writer.Target == Writer, Reader.Target == Reader {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(Writer.self, id: id)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(Reader.self, id: id)

    let bytes = try writer.serialize(value)
    return { try reader.deserialize(bytes) }
}

private func expectControlledFailure(_ body: () throws -> Void) {
    #expect(throws: (any Error).self) {
        try body()
    }
}

private func expectCompatibleFailure<Writer: Serializer, Reader: Serializer>(
    _ value: Writer,
    as _: Reader.Type,
    id: UInt32
) throws where Writer.Target == Writer, Reader.Target == Reader {
    let decode = try prepareCompatibleDecode(value, as: Reader.self, id: id)
    expectControlledFailure {
        _ = try decode()
    }
}

@Test
func compatibleModeSupportsAddedAndRemovedFields() throws {
    let writerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV1.register(CompatibleProfileV1.self, id: 9901)

    let readerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV2.register(CompatibleProfileV2.self, id: 9901)

    let sourceV1 = CompatibleProfileV1(id: 7, name: "swift")
    let bytesFromV1 = try writerV1.serialize(sourceV1)
    let decodedAsV2: CompatibleProfileV2 = try readerV2.deserialize(bytesFromV1)
    #expect(decodedAsV2.id == sourceV1.id)
    #expect(decodedAsV2.name == sourceV1.name)
    #expect(decodedAsV2.nickname == "")
    #expect(decodedAsV2.scores.isEmpty)

    let writerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV2.register(CompatibleProfileV2.self, id: 9901)

    let readerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV1.register(CompatibleProfileV1.self, id: 9901)

    let sourceV2 = CompatibleProfileV2(id: 9, name: "fory", nickname: "macro", scores: [1, 2, 3])
    let bytesFromV2 = try writerV2.serialize(sourceV2)
    let decodedAsV1: CompatibleProfileV1 = try readerV1.deserialize(bytesFromV2)
    #expect(decodedAsV1 == CompatibleProfileV1(id: sourceV2.id, name: sourceV2.name))
}

@Test
func compatibleTypeDefCacheHit() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleProfileV1.self, id: 9900)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleProfileV2.self, id: 9900)

    let source = CompatibleProfileV1(id: 7, name: "swift")
    let bytes = try writer.serialize(source)
    let expected = CompatibleProfileV2(
        id: source.id,
        name: source.name,
        nickname: "",
        scores: []
    )

    let first: CompatibleProfileV2 = try reader.deserialize(bytes)
    #expect(first == expected)
    #if DEBUG
        #expect(reader.typeResolver.typeDefCacheCount == 1)
    #endif

    let cached: CompatibleProfileV2 = try reader.deserialize(bytes)
    #expect(cached == expected)
    #if DEBUG
        #expect(reader.typeResolver.typeDefCacheCount == 1)
    #endif
}

@Test
func containerNullableChangeIsAdapted() throws {
    let optional = OptionalContainersV1(
        list: ["list"],
        set: ["set"],
        map: ["map": 1]
    )
    let list: RequiredListV2 = try compatibleDecode(optional, as: RequiredListV2.self, id: 9963)
    #expect(list.list == ["list"])
    let set: RequiredSetV2 = try compatibleDecode(optional, as: RequiredSetV2.self, id: 9963)
    #expect(set.set == ["set"])
    let map: RequiredMapV2 = try compatibleDecode(optional, as: RequiredMapV2.self, id: 9963)
    #expect(map.map == ["map": 1])

    let null = OptionalContainersV1(list: nil, set: nil, map: nil)
    let nullList: RequiredListV2 = try compatibleDecode(null, as: RequiredListV2.self, id: 9963)
    #expect(nullList.list.isEmpty)
    let nullSet: RequiredSetV2 = try compatibleDecode(null, as: RequiredSetV2.self, id: 9963)
    #expect(nullSet.set.isEmpty)
    let nullMap: RequiredMapV2 = try compatibleDecode(null, as: RequiredMapV2.self, id: 9963)
    #expect(nullMap.map.isEmpty)

    let wrapped: OptionalContainersV1 = try compatibleDecode(
        RequiredListV2(list: ["list"]),
        as: OptionalContainersV1.self,
        id: 9963
    )
    #expect(wrapped.list == ["list"])
}

@Test
func containerTrackingChangeRejected() throws {
    let config = Config(trackRef: true, compatible: true)
    let resolver = TypeResolver(config: config)
    try resolver.register(OptionalContainersV1.self, id: 9963)
    try resolver.finishRegistration()
    let localTypeInfo = try resolver.requireTypeInfo(for: OptionalContainersV1.self)
    let localTypeMeta = try #require(localTypeInfo.typeMeta)
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")

    for (index, localField) in localTypeMeta.fields.enumerated() {
        // Swift value-container fields cannot emit outer reference tracking. Clone the generated
        // field shape to model foreign metadata without weakening static carrier ownership.
        #expect(!localField.fieldType.trackRef)
        var remoteField = localField
        remoteField.fieldType.trackRef = true
        let remoteTypeMeta = try TypeMeta(
            typeID: TypeId.compatibleStruct.rawValue,
            userTypeID: 9963,
            namespace: empty,
            typeName: empty,
            registerByName: false,
            fields: [remoteField]
        )
        expectControlledFailure {
            _ = try resolver.cacheTypeInfo(
                remoteTypeMeta,
                forHeaderHash: UInt64(index + 1),
                localTypeInfo: localTypeInfo,
                config: config
            )
        }
    }
}

@Test
func skipsDynamicMapNullEntries() throws {
    let writer = Fory(config: .init(trackRef: true, compatible: true))
    try writer.register(SkippedDynamicMapV1.self, id: 9962)

    let reader = Fory(config: .init(trackRef: true, compatible: true))
    try reader.register(SkippedDynamicMapV2.self, id: 9962)

    let source = SkippedDynamicMapV1(
        removed: [
            AnyHashable(ForyAnyNullValue()): "value",
            AnyHashable("null-value"): NSNull()
        ],
        keep: 37
    )
    let decoded: SkippedDynamicMapV2 = try reader.deserialize(try writer.serialize(source))
    #expect(decoded.keep == source.keep)
}

@Test
func compatibleNoneCollectionUsesBudget() throws {
    let sentinel: UInt8 = 0xA5
    let fieldType = TypeMeta.FieldType(
        typeID: TypeId.list.rawValue,
        nullable: false,
        generics: [
            TypeMeta.FieldType(typeID: TypeId.none.rawValue, nullable: false)
        ]
    )

    for declared in [true, false] {
        let buffer = ByteBuffer()
        buffer.writeVarUInt32(3)
        buffer.writeUInt8(
            CollectionHeader.sameType
                | (declared ? CollectionHeader.declaredElementType : 0)
        )
        if !declared {
            buffer.writeUInt8(UInt8(TypeId.none.rawValue))
        }
        let sentinelIndex = buffer.count
        buffer.writeUInt8(sentinel)

        let config = Config(
            trackRef: false,
            compatible: true,
            maxUnbackedContainerItems: 2
        )
        let context = ReadContext(
            buffer: buffer,
            typeResolver: TypeResolver(config: config),
            config: config
        )
        context.remainingUnbackedContainerItems = config.maxUnbackedContainerItems
        #expect(throws: (any Error).self) {
            try context.skipFieldValue(fieldType)
        }

        #expect(buffer.getCursor() == sentinelIndex)
        #expect(try buffer.readUInt8() == sentinel)
        #expect(buffer.remaining == 0)
    }
}

@Test
func compatibleUnionSkipperBoundsDynamicPayload() throws {
    let writer = Fory(config: .init(compatible: true, maxDepth: 8))
    try writer.register(SkippedDepthUnion.self, id: 9964)
    try writer.register(SkippedUnionV1.self, id: 9965)
    let source = SkippedUnionV1(
        removed: .child(.child(.text("leaf"))),
        keep: 43
    )
    let bytes = try writer.serialize(source)

    let limitedReader = Fory(config: .init(compatible: true, maxDepth: 1))
    try limitedReader.register(SkippedDepthUnion.self, id: 9964)
    try limitedReader.register(SkippedUnionV2.self, id: 9965)
    #expect(throws: (any Error).self) {
        let _: SkippedUnionV2 = try limitedReader.deserialize(bytes)
    }

    let boundaryReader = Fory(config: .init(compatible: true, maxDepth: 3))
    try boundaryReader.register(SkippedDepthUnion.self, id: 9964)
    try boundaryReader.register(SkippedUnionV2.self, id: 9965)
    let decoded: SkippedUnionV2 = try boundaryReader.deserialize(bytes)
    #expect(decoded.keep == source.keep)
}

@Test
func scalarBoolStringConverts() throws {
    let boolFromTrue: ScalarBoolBox = try compatibleDecode(
        ScalarStringBox(value: "true"),
        as: ScalarBoolBox.self,
        id: 9930
    )
    #expect(boolFromTrue.value)

    let boolFromZero: ScalarBoolBox = try compatibleDecode(
        ScalarStringBox(value: "0"),
        as: ScalarBoolBox.self,
        id: 9931
    )
    #expect(!boolFromZero.value)

    let stringFromBool: ScalarStringBox = try compatibleDecode(
        ScalarBoolBox(value: true),
        as: ScalarStringBox.self,
        id: 9932
    )
    #expect(stringFromBool.value == "true")
}

@Test
func compatibleScalarRejectsInvalidBoolPayload() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(ScalarBoolBox.self, id: 9953)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(ScalarStringBox.self, id: 9953)

    let bytes = try writer.serialize(ScalarBoolBox(value: true))
    let decoded: ScalarStringBox = try reader.deserialize(bytes)
    #expect(decoded.value == "true")

    var corrupted = bytes
    if let lastIndex = corrupted.indices.last {
        corrupted[lastIndex] = 2
    }
    #expect(throws: (any Error).self) {
        let _: ScalarStringBox = try reader.deserialize(corrupted)
    }
    let afterFailure: ScalarStringBox = try reader.deserialize(bytes)
    #expect(afterFailure.value == "true")
}

@Test
func scalarNumberConverts() throws {
    let doubleFromInt: ScalarDoubleBox = try compatibleDecode(
        ScalarInt32Box(value: 123),
        as: ScalarDoubleBox.self,
        id: 9933
    )
    #expect(doubleFromInt.value == 123.0)

    let intFromDouble: ScalarInt32Box = try compatibleDecode(
        ScalarDoubleBox(value: 42.0),
        as: ScalarInt32Box.self,
        id: 9934
    )
    #expect(intFromDouble.value == 42)

    let decimalFromInt: ScalarDecimalBox = try compatibleDecode(
        ScalarInt32Box(value: 77),
        as: ScalarDecimalBox.self,
        id: 9935
    )
    #expect(decimalFromInt.value == Decimal(77))

    let intFromDecimal: ScalarInt32Box = try compatibleDecode(
        ScalarDecimalBox(value: Decimal(88)),
        as: ScalarInt32Box.self,
        id: 9936
    )
    #expect(intFromDecimal.value == 88)
}

@Test
func scalarStringNumberConverts() throws {
    let intFromString: ScalarInt32Box = try compatibleDecode(
        ScalarStringBox(value: "-123"),
        as: ScalarInt32Box.self,
        id: 9937
    )
    #expect(intFromString.value == -123)

    let uintFromNegativeZero: ScalarUInt8Box = try compatibleDecode(
        ScalarStringBox(value: "-0.0"),
        as: ScalarUInt8Box.self,
        id: 9938
    )
    #expect(uintFromNegativeZero.value == 0)

    let stringFromDouble: ScalarStringBox = try compatibleDecode(
        ScalarDoubleBox(value: 0.5),
        as: ScalarStringBox.self,
        id: 9939
    )
    #expect(stringFromDouble.value == "0.5")

    let stringFromNegativeZero: ScalarStringBox = try compatibleDecode(
        ScalarDoubleBox(value: -0.0),
        as: ScalarStringBox.self,
        id: 9940
    )
    #expect(stringFromNegativeZero.value == "-0.0")

    let decimalFromString: ScalarDecimalBox = try compatibleDecode(
        ScalarStringBox(value: "123.5"),
        as: ScalarDecimalBox.self,
        id: 9954
    )
    #expect(decimalFromString.value == Decimal(string: "123.5")!)

    let stringFromDecimal: ScalarStringBox = try compatibleDecode(
        ScalarDecimalBox(value: Decimal(string: "123.5")!),
        as: ScalarStringBox.self,
        id: 9955
    )
    #expect(stringFromDecimal.value == "123.5")
}

@Test
func scalarOptionalComposes() throws {
    let boolFromOptional: ScalarBoolOptBox = try compatibleDecode(
        ScalarStringOptBox(value: "1"),
        as: ScalarBoolOptBox.self,
        id: 9941
    )
    #expect(boolFromOptional.value == true)

    let nilFromOptional: ScalarBoolOptBox = try compatibleDecode(
        ScalarStringOptBox(value: nil),
        as: ScalarBoolOptBox.self,
        id: 9942
    )
    #expect(nilFromOptional.value == nil)

    let primitiveDefault: ScalarBoolBox = try compatibleDecode(
        ScalarStringOptBox(value: nil),
        as: ScalarBoolBox.self,
        id: 9943
    )
    #expect(!primitiveDefault.value)
}

@Test
func sameTypeNullableScalarUsesStrictSourceRead() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(ScalarBoolOptBox.self, id: 9958)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(ScalarBoolBox.self, id: 9958)

    let bytes = try writer.serialize(ScalarBoolOptBox(value: true))
    let flagByte = UInt8(bitPattern: RefFlag.notNullValue.rawValue)
    let refValueByte = UInt8(bitPattern: RefFlag.refValue.rawValue)
    guard let flagIndex = bytes.lastIndex(of: flagByte) else {
        #expect(Bool(false))
        return
    }

    var badFlag = bytes
    badFlag[flagIndex] = refValueByte
    expectControlledFailure {
        let _: ScalarBoolBox = try reader.deserialize(badFlag)
    }

    var badPayload = bytes
    badPayload[badPayload.index(before: badPayload.endIndex)] = 2
    expectControlledFailure {
        let _: ScalarBoolBox = try reader.deserialize(badPayload)
    }
}

@Test
func scalarTrackRefMismatchIsRejected() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "value",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: false))
        ])
    let remoteTracking = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "$tag1",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: false, trackRef: true)
            )
        ])
    expectControlledFailure {
        _ = try remoteTracking.assigningFieldIDs(from: local)
    }

    let localTracking = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "value",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: false, trackRef: true)
            )
        ])
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "$tag1",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: false))
        ])
    expectControlledFailure {
        _ = try remote.assigningFieldIDs(from: localTracking)
    }

    let resolvedBothTracking = try remoteTracking.assigningFieldIDs(from: localTracking)
    #expect(resolvedBothTracking.fields[0].fieldID == 1)
    #expect(resolvedBothTracking.fields[0].matchedFieldID == 0)

    let localNullableTracking = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "value",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: true, trackRef: true)
            )
        ])
    let remoteNullableTracking = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: 1,
                fieldName: "$tag1",
                fieldType: TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: true, trackRef: true)
            )
        ])
    let resolvedBothNullableTracking = try remoteNullableTracking.assigningFieldIDs(
        from: localNullableTracking)
    #expect(resolvedBothNullableTracking.fields[0].fieldID == 1)
    #expect(resolvedBothNullableTracking.fields[0].matchedFieldID == 0)
    expectControlledFailure {
        _ = try remoteNullableTracking.assigningFieldIDs(from: localTracking)
    }
    expectControlledFailure {
        _ = try remoteTracking.assigningFieldIDs(from: localNullableTracking)
    }
}

@Test
func namedRemoteOnlyFieldIsSkipped() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let stringType = TypeMeta.FieldType(typeID: TypeId.string.rawValue, nullable: false)
    let intType = TypeMeta.FieldType(typeID: TypeId.int64.rawValue, nullable: false)
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "username", fieldType: stringType),
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "id", fieldType: intType)
        ])
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "email", fieldType: stringType),
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "id", fieldType: intType)
        ])

    let resolved = try remote.assigningFieldIDs(from: local)
    #expect(resolved.fields[0].fieldID == -1)
    #expect(resolved.fields[0].matchedFieldID == -1)
    #expect(resolved.fields[1].fieldID == -1)
    #expect(resolved.fields[1].matchedFieldID == 2)
}

@Test
func remoteOnlyFieldsSkipWhenLocalSchemaIsEmpty() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let stringType = TypeMeta.FieldType(typeID: TypeId.string.rawValue, nullable: false)
    let intType = TypeMeta.FieldType(typeID: TypeId.int64.rawValue, nullable: false)
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [])
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: 1, fieldName: "$tag1", fieldType: stringType),
            TypeMeta.FieldInfo(fieldID: 2, fieldName: "$tag2", fieldType: intType)
        ])

    let resolved = try remote.assigningFieldIDs(from: local)
    #expect(resolved.fields[0].fieldID == 1)
    #expect(resolved.fields[0].matchedFieldID == -1)
    #expect(resolved.fields[1].fieldID == 2)
    #expect(resolved.fields[1].matchedFieldID == -1)
}

@Test
func nameRemoteFieldDoesNotMatchTaggedLocalField() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let stringType = TypeMeta.FieldType(typeID: TypeId.string.rawValue, nullable: false)
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: 1, fieldName: "value", fieldType: stringType)
        ])
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "value", fieldType: stringType)
        ])

    let resolved = try remote.assigningFieldIDs(from: local)
    #expect(resolved.fields[0].fieldID == -1)
    #expect(resolved.fields[0].matchedFieldID == -1)
}

@Test
func duplicateRemoteNamesFail() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let stringType = TypeMeta.FieldType(typeID: TypeId.string.rawValue, nullable: false)
    #expect(throws: (any Error).self) {
        _ = try TypeMeta(
            typeID: TypeId.compatibleStruct.rawValue,
            userTypeID: 1,
            namespace: empty,
            typeName: empty,
            registerByName: false,
            fields: [
                TypeMeta.FieldInfo(fieldID: -1, fieldName: "fooBar", fieldType: stringType),
                TypeMeta.FieldInfo(fieldID: -1, fieldName: "foo_bar", fieldType: stringType)
            ])
    }
}

@Test
func duplicateRemoteTagsFail() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let fieldType = TypeMeta.FieldType(typeID: TypeId.int32.rawValue, nullable: false)
    #expect(throws: (any Error).self) {
        _ = try TypeMeta(
            typeID: TypeId.compatibleStruct.rawValue,
            userTypeID: 1,
            namespace: empty,
            typeName: empty,
            registerByName: false,
            fields: [
                TypeMeta.FieldInfo(fieldID: 1, fieldName: "first", fieldType: fieldType),
                TypeMeta.FieldInfo(fieldID: 1, fieldName: "second", fieldType: fieldType)
            ]
        )
    }
}

@Test
func namedCompatibleKindRejected() throws {
    let resolver = TypeResolver(config: Config(compatible: true))
    try resolver.register(
        CompatibleProfileV1.self,
        namespace: "kind",
        typeName: "Value"
    )
    try resolver.finishRegistration()
    let incompatible = try TypeMeta(
        typeID: TypeId.namedEnum.rawValue,
        userTypeID: nil,
        namespace: try MetaStringEncoder.namespace.encode("kind"),
        typeName: try MetaStringEncoder.typeName.encode("Value"),
        registerByName: true,
        fields: []
    )

    #expect(throws: (any Error).self) {
        _ = try resolver.requireTypeInfo(for: incompatible)
    }
    let wrongRegistration = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: nil,
        namespace: try MetaStringEncoder.namespace.encode("kind"),
        typeName: try MetaStringEncoder.typeName.encode("Value"),
        registerByName: true,
        fields: []
    )
    #expect(throws: (any Error).self) {
        _ = try resolver.requireTypeInfo(for: wrongRegistration)
    }
}

@Test
func idCompatibleKindRejected() throws {
    let resolver = TypeResolver(config: Config(compatible: true))
    try resolver.register(CompatibleProfileV1.self, id: 9971)
    try resolver.finishRegistration()
    let incompatible = try TypeMeta(
        typeID: TypeId.enumType.rawValue,
        userTypeID: 9971,
        namespace: .empty(specialChar1: ".", specialChar2: "_"),
        typeName: .empty(specialChar1: "$", specialChar2: "_"),
        registerByName: false,
        fields: []
    )

    #expect(throws: (any Error).self) {
        _ = try resolver.requireTypeInfo(for: incompatible)
    }
}

@Test
func matchedFieldIdOverflowFails() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let fieldType = TypeMeta.FieldType(typeID: TypeId.bool.rawValue, nullable: false)
    let overflowIndex = Int(Int16.max) / 2 + 1
    let localFields = (0...overflowIndex).map {
        TypeMeta.FieldInfo(fieldID: -1, fieldName: "f\($0)", fieldType: fieldType)
    }
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: localFields)
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: -1,
                fieldName: "f\(overflowIndex)",
                fieldType: fieldType)
        ])

    expectControlledFailure {
        _ = try remote.assigningFieldIDs(from: local)
    }
}

@Test
func matchedByteFamilyClassification() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let binaryType = TypeMeta.FieldType(typeID: TypeId.binary.rawValue, nullable: false)
    let uint8ArrayType = TypeMeta.FieldType(typeID: TypeId.uint8Array.rawValue, nullable: false)
    let int8ArrayType = TypeMeta.FieldType(typeID: TypeId.int8Array.rawValue, nullable: false)
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "payload", fieldType: binaryType)
        ])
    let remoteUInt8Array = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "payload", fieldType: uint8ArrayType)
        ])
    let remoteInt8Array = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(fieldID: -1, fieldName: "payload", fieldType: int8ArrayType)
        ])

    let resolved = try remoteUInt8Array.assigningFieldIDs(from: local)
    #expect(resolved.fields[0].fieldID == -1)
    #expect(resolved.fields[0].matchedFieldID == 1)
    expectControlledFailure {
        _ = try remoteInt8Array.assigningFieldIDs(from: local)
    }
}

@Test
func matchedNestedScalarShapeRejectsRefDrift() throws {
    let empty = MetaString.empty(specialChar1: "_", specialChar2: "_")
    let local = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: -1,
                fieldName: "values",
                fieldType: TypeMeta.FieldType(
                    typeID: TypeId.list.rawValue,
                    nullable: false,
                    generics: [TypeMeta.FieldType(typeID: TypeId.string.rawValue, nullable: false)]))
        ])
    let remote = try TypeMeta(
        typeID: TypeId.compatibleStruct.rawValue,
        userTypeID: 1,
        namespace: empty,
        typeName: empty,
        registerByName: false,
        fields: [
            TypeMeta.FieldInfo(
                fieldID: -1,
                fieldName: "values",
                fieldType: TypeMeta.FieldType(
                    typeID: TypeId.list.rawValue,
                    nullable: false,
                    generics: [
                        TypeMeta.FieldType(
                            typeID: TypeId.string.rawValue,
                            nullable: false,
                            trackRef: true)
                    ]))
        ])

    expectControlledFailure {
        _ = try remote.assigningFieldIDs(from: local)
    }
}

@Test
func scalarConversionFailures() throws {
    try expectCompatibleFailure(
        ScalarStringBox(value: "True"), as: ScalarBoolBox.self, id: 9944)
    try expectCompatibleFailure(
        ScalarStringBox(value: "256"), as: ScalarUInt8Box.self, id: 9945)
    try expectCompatibleFailure(
        ScalarDoubleBox(value: 1.5), as: ScalarInt32Box.self, id: 9946)
    try expectCompatibleFailure(
        ScalarDoubleBox(value: .nan), as: ScalarStringBox.self, id: 9947)
    try expectCompatibleFailure(
        ScalarStringBox(value: String(repeating: "1", count: 257)),
        as: ScalarDecimalBox.self,
        id: 9949
    )
    try expectCompatibleFailure(
        ScalarStringBox(value: "0." + String(repeating: "0", count: 319)),
        as: ScalarDecimalBox.self,
        id: 9950
    )
    try expectCompatibleFailure(
        ScalarStringBox(value: "1e1000000"), as: ScalarDecimalBox.self, id: 9956)
    try expectCompatibleFailure(
        ScalarStringBox(value: "1e256"), as: ScalarDecimalBox.self, id: 9957)
    try expectCompatibleFailure(
        ScalarFloat16Box(value: Float16(bitPattern: 0x7e00)),
        as: ScalarBFloat16Box.self,
        id: 9951
    )
    try expectCompatibleFailure(
        ScalarDoubleBox(value: .greatestFiniteMagnitude),
        as: ScalarStringBox.self,
        id: 9966
    )
    try expectCompatibleFailure(
        ScalarDoubleBox(value: .greatestFiniteMagnitude),
        as: ScalarDecimalBox.self,
        id: 9967
    )
}

@Test
func unregisteredRemovedStructSkips() throws {
    let writer = Fory(config: .init(trackRef: true, compatible: true))
    try writer.register(RemovedRefChild.self, id: 9969)
    try writer.register(RemovedRefV1.self, id: 9970)

    let child = RemovedRefChild(value: 7)
    let source = RemovedRefV1(first: child, second: child, keep: 9)
    let holderBytes = try writer.serialize(source)
    let emptyHolderBytes = try writer.serialize(RemovedRefV1(first: nil, second: nil, keep: 10))
    let childBytes = try writer.serialize(child)

    let reader = Fory(config: .init(trackRef: true, compatible: true))
    try reader.register(RemovedRefV2.self, id: 9970)
    let decoded: RemovedRefV2 = try reader.deserialize(holderBytes)
    #expect(decoded == RemovedRefV2(keep: source.keep))

    let limitedReader = Fory(
        config: .init(trackRef: true, compatible: true, maxDepth: 0)
    )
    try limitedReader.register(RemovedRefV2.self, id: 9970)
    #expect(throws: (any Error).self) {
        let _: RemovedRefV2 = try limitedReader.deserialize(holderBytes)
    }
    let afterFailure: RemovedRefV2 = try limitedReader.deserialize(emptyHolderBytes)
    #expect(afterFailure == RemovedRefV2(keep: 10))

    let dynamicReader = Fory(config: .init(trackRef: true, compatible: true))
    #expect(throws: (any Error).self) {
        let _: Any = try dynamicReader.deserialize(childBytes)
    }
}

@Test
func sameSchemaScalarPreserved() throws {
    let fory = Fory(config: .init(trackRef: false, compatible: true))
    try fory.register(ScalarStringBox.self, id: 9948)

    let value = ScalarStringBox(value: "+1")
    let decoded: ScalarStringBox = try fory.deserialize(try fory.serialize(value))
    #expect(decoded == value)
}

@Test
func encodedExactReadsUseCodecs() throws {
    let value = ExactEncodedV1(
        fixed32: 0x0102_0304,
        fixed64: 0x0102_0304_0506_0708,
        tagged64: Int64(Int32.max) + 0x1020,
        tail: "aligned"
    )

    let sameSchema = Fory(config: .init(trackRef: false, compatible: true))
    try sameSchema.register(ExactEncodedV1.self, id: 9960)
    let sameDecoded: ExactEncodedV1 = try sameSchema.deserialize(try sameSchema.serialize(value))
    #expect(sameDecoded == value)

    let changedDecoded: ExactEncodedV2 = try compatibleDecode(value, as: ExactEncodedV2.self, id: 9961)
    #expect(changedDecoded.added == 0)
    #expect(changedDecoded.fixed32 == value.fixed32)
    #expect(changedDecoded.fixed64 == value.fixed64)
    #expect(changedDecoded.tagged64 == value.tagged64)
    #expect(changedDecoded.tail == value.tail)
}

@Test
func scalarConversionDoesNotDriveFallbackMatch() throws {
    let decoded: LocalFallbackStringBox = try compatibleDecode(
        RemoteFallbackBoolBox(remoteValue: true),
        as: LocalFallbackStringBox.self,
        id: 9952
    )
    #expect(decoded.localValue == "")
}

@Test
func schemaConsistentModeRejectsVersionHashMismatch() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: false, checkClassVersion: true))
    try writer.register(SchemaVersionV1.self, id: 9902)

    let reader = Fory(config: .init(trackRef: false, compatible: false, checkClassVersion: true))
    try reader.register(SchemaVersionV2.self, id: 9902)

    let bytes = try writer.serialize(SchemaVersionV1(id: 1, name: "shape"))
    do {
        let _: SchemaVersionV2 = try reader.deserialize(bytes)
        #expect(Bool(false))
    } catch {
        #expect("\(error)".contains("class version hash mismatch"))
    }
}

@Test
func compatibleModePreservesSharedAndCircularReferencesForMacroObjects() throws {
    let fory = Fory(config: .init(trackRef: true, compatible: true))
    try fory.register(CompatibleGraphNode.self, id: 9903)
    try fory.register(CompatibleGraphContainer.self, id: 9904)

    let shared = CompatibleGraphNode(value: 11)
    shared.next = shared
    let value = CompatibleGraphContainer(
        first: shared,
        second: shared,
        items: [shared, shared],
        byName: [
            "left": shared,
            "right": shared
        ]
    )

    let decoded: CompatibleGraphContainer = try fory.deserialize(try fory.serialize(value))

    #expect(decoded.first != nil)
    #expect(decoded.first === decoded.second)
    #expect(decoded.first === decoded.items[0])
    #expect(decoded.items[0] === decoded.items[1])
    #expect(decoded.byName["left"] === decoded.byName["right"])
    #expect(decoded.byName["left"] === decoded.first)
    #expect(decoded.first?.next === decoded.first)
}

@Test
func schemaHashMatchesJavaFingerprintForTaggedUnsignedFields() {
    #expect(
        SchemaHash.structHash32("u64_tagged,15,0,0;u64_tagged_nullable,15,0,1;")
            == UInt32(2_653_134_377)
    )
}

@Test
func schemaHashUsesNestedAnnotatedContainerTypeIDs() throws {
    let fory = Fory(config: .init(trackRef: false, compatible: false, checkClassVersion: true))
    try fory.register(SchemaHashNestedAnnotatedContainer.self, id: 9912)

    let bytes = try fory.serialize(
        SchemaHashNestedAnnotatedContainer(
            values: [
                UInt32(7): [UInt64(11), nil]
            ]
        )
    )
    let buffer = ByteBuffer(data: bytes)
    _ = try fory.readHead(buffer: buffer)
    _ = try buffer.readInt8()
    _ = try buffer.readVarUInt32()
    _ = try buffer.readVarUInt32()
    let schemaHash = UInt32(bitPattern: try buffer.readInt32())

    #expect(
        schemaHash == SchemaHash.structHash32("values,24,0,0[11,0,0|22,0,0[15,0,0]];")
    )
}

@Test
func compatibleNestedArrayEvolves() throws {
    let writerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV1.register(CompatibleNestedProfileV1.self, id: 9910)
    try writerV1.register(CompatibleNestedArrayV1.self, id: 9911)

    let readerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV2.register(CompatibleNestedProfileV2.self, id: 9910)
    try readerV2.register(CompatibleNestedArrayV2.self, id: 9911)

    let sourceV1 = CompatibleNestedArrayV1(
        items: [
            CompatibleNestedProfileV1(id: 1, name: "alpha"),
            CompatibleNestedProfileV1(id: 2, name: "beta")
        ]
    )
    let decodedAsV2: CompatibleNestedArrayV2 = try readerV2.deserialize(
        try writerV1.serialize(sourceV1))
    #expect(decodedAsV2.items.map(\.id) == [1, 2])
    #expect(decodedAsV2.items.map(\.name) == ["alpha", "beta"])
    #expect(decodedAsV2.items.allSatisfy { $0.alias.isEmpty })
    #expect(decodedAsV2.items.allSatisfy { $0.scores.isEmpty })

    let writerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV2.register(CompatibleNestedProfileV2.self, id: 9910)
    try writerV2.register(CompatibleNestedArrayV2.self, id: 9911)

    let readerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV1.register(CompatibleNestedProfileV1.self, id: 9910)
    try readerV1.register(CompatibleNestedArrayV1.self, id: 9911)

    let sourceV2 = CompatibleNestedArrayV2(
        items: [
            CompatibleNestedProfileV2(id: 3, name: "gamma", alias: "g", scores: [3, 4]),
            CompatibleNestedProfileV2(id: 4, name: "delta", alias: "d", scores: [])
        ]
    )
    let decodedAsV1: CompatibleNestedArrayV1 = try readerV1.deserialize(
        try writerV2.serialize(sourceV2))
    #expect(
        decodedAsV1.items == [
            CompatibleNestedProfileV1(id: 3, name: "gamma"),
            CompatibleNestedProfileV1(id: 4, name: "delta")
        ])
}

@Test
func compatibleReadConvertsFixedUInt32() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(RemoteFixedUInt32V1.self, id: 9920)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(LocalVarUInt32V2.self, id: 9920)

    let source = RemoteFixedUInt32V1(id: UInt32.max, keep: 42)
    let decoded: LocalVarUInt32V2 = try reader.deserialize(try writer.serialize(source))
    #expect(decoded.id == source.id)
    #expect(decoded.keep == source.keep)
}

@Test
func rejectsNestedScalarConversion() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(RemoteNestedFixedMapV1.self, id: 9921)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(LocalNestedVarintMapV2.self, id: 9921)

    let source = RemoteNestedFixedMapV1(
        data: [
            "a": [1, nil, Int32.max],
            "b": []
        ],
        keep: 84,
        ids: [nil, -1, Int32.max]
    )
    let bytes = try writer.serialize(source)
    #expect(throws: (any Error).self) {
        let _: LocalNestedVarintMapV2 = try reader.deserialize(bytes)
    }

    let matchingWriter = Fory(config: .init(trackRef: false, compatible: true))
    try matchingWriter.register(LocalNestedVarintMapV2.self, id: 9921)
    let valid = LocalNestedVarintMapV2(data: ["c": [2, nil]], keep: 85, ids: [3])
    let decoded: LocalNestedVarintMapV2 = try reader.deserialize(
        try matchingWriter.serialize(valid)
    )
    #expect(decoded == valid)
}

@Test
func compatibleReadAdaptsImmediateListAndArrayFieldPair() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleListFieldV1.self, id: 9922)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleArrayFieldV2.self, id: 9922)

    let decoded: CompatibleArrayFieldV2 = try reader.deserialize(
        try writer.serialize(CompatibleListFieldV1(values: [1, 2, 3], extra: 9))
    )
    #expect(decoded.values == [1, 2, 3])
}

@Test
func compatibleReadAdaptsDefaultVarintListAndArrayFieldPair() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleVarintListFieldV1.self, id: 9924)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleArrayFieldV2.self, id: 9924)

    let decoded: CompatibleArrayFieldV2 = try reader.deserialize(
        try writer.serialize(CompatibleVarintListFieldV1(values: [-1, 2, 3], extra: 9))
    )
    #expect(decoded.values == [-1, 2, 3])
}

@Test
func listToArrayChecksFixedPayloadBytes() throws {
    let buffer = ByteBuffer()
    buffer.writeVarUInt32(2)
    buffer.writeUInt8(CollectionHeader.sameType | CollectionHeader.declaredElementType)
    buffer.writeBytes([0, 0])

    let config = Config(trackRef: false, compatible: true)
    let context = ReadContext(
        buffer: buffer,
        typeResolver: TypeResolver(config: config),
        config: config
    )
    let remoteFieldType = TypeMeta.FieldType(
        typeID: TypeId.list.rawValue,
        nullable: false,
        generics: [
            TypeMeta.FieldType(typeID: TypeId.float64.rawValue, nullable: false)
        ]
    )

    #expect(throws: (any Error).self) {
        let _: [Double] = try ArrayFieldCodec<DoubleCodec>.readCompatibleField(
            context,
            remoteFieldType: remoteFieldType,
            refMode: .none
        )
    }
}

@Test
func compatibleReadAdaptsArrayFieldToDefaultVarintListField() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleArrayFieldV2.self, id: 9925)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleVarintListFieldV1.self, id: 9925)

    let decoded: CompatibleVarintListFieldV1 = try reader.deserialize(
        try writer.serialize(CompatibleArrayFieldV2(values: [-1, 2, 3]))
    )
    #expect(decoded.values == [-1, 2, 3])
}

@Test
func rejectsNullableListArrayElement() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleNullableListFieldV1.self, id: 9923)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleArrayFieldV2.self, id: 9923)

    let bytes = try writer.serialize(CompatibleNullableListFieldV1(values: [1, 2, 3], extra: 9))
    #expect(throws: (any Error).self) {
        let _: CompatibleArrayFieldV2 = try reader.deserialize(bytes)
    }

    let nullBytes = try writer.serialize(CompatibleNullableListFieldV1(values: [1, nil, 3], extra: 9))
    #expect(throws: (any Error).self) {
        let _: CompatibleArrayFieldV2 = try reader.deserialize(nullBytes)
    }

    let matchingWriter = Fory(config: .init(trackRef: false, compatible: true))
    try matchingWriter.register(CompatibleVarintListFieldV1.self, id: 9923)
    let decoded: CompatibleArrayFieldV2 = try reader.deserialize(
        try matchingWriter.serialize(CompatibleVarintListFieldV1(values: [4, 5], extra: 6))
    )
    #expect(decoded.values == [4, 5])
}

@Test
func rejectsNestedListArray() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleNestedListArrayFieldV1.self, id: 9926)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleNestedArrayListFieldV2.self, id: 9926)

    let bytes = try writer.serialize(CompatibleNestedListArrayFieldV1(values: [[1, 2]], keep: 7))
    #expect(throws: (any Error).self) {
        let _: CompatibleNestedArrayListFieldV2 = try reader.deserialize(bytes)
    }

    let matchingWriter = Fory(config: .init(trackRef: false, compatible: true))
    try matchingWriter.register(CompatibleNestedArrayListFieldV2.self, id: 9926)
    let valid = CompatibleNestedArrayListFieldV2(values: [[3, 4]], keep: 8)
    let decoded: CompatibleNestedArrayListFieldV2 = try reader.deserialize(
        try matchingWriter.serialize(valid)
    )
    #expect(decoded == valid)
}

@Test
func rejectsNestedNullableChange() throws {
    let writer = Fory(config: .init(trackRef: false, compatible: true))
    try writer.register(CompatibleNestedNullableListV1.self, id: 9927)

    let reader = Fory(config: .init(trackRef: false, compatible: true))
    try reader.register(CompatibleNestedRequiredListV2.self, id: 9927)

    let bytes = try writer.serialize(CompatibleNestedNullableListV1(values: [[1, 2]]))
    #expect(throws: (any Error).self) {
        let _: CompatibleNestedRequiredListV2 = try reader.deserialize(bytes)
    }

    let nullBytes = try writer.serialize(CompatibleNestedNullableListV1(values: [[1, nil, 2]]))
    #expect(throws: (any Error).self) {
        let _: CompatibleNestedRequiredListV2 = try reader.deserialize(nullBytes)
    }

    let matchingWriter = Fory(config: .init(trackRef: false, compatible: true))
    try matchingWriter.register(CompatibleNestedRequiredListV2.self, id: 9927)
    let valid = CompatibleNestedRequiredListV2(values: [[3, 4]])
    let decoded: CompatibleNestedRequiredListV2 = try reader.deserialize(
        try matchingWriter.serialize(valid)
    )
    #expect(decoded.values == valid.values)
}

@Test
func compatibleNestedMapEvolves() throws {
    let writerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV1.register(CompatibleNestedProfileV1.self, id: 9910)
    try writerV1.register(CompatibleNestedMapV1.self, id: 9912)

    let readerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV2.register(CompatibleNestedProfileV2.self, id: 9910)
    try readerV2.register(CompatibleNestedMapV2.self, id: 9912)

    let sourceV1 = CompatibleNestedMapV1(
        items: [
            1: CompatibleNestedProfileV1(id: 10, name: "first"),
            2: CompatibleNestedProfileV1(id: 20, name: "second")
        ]
    )
    let decodedAsV2: CompatibleNestedMapV2 = try readerV2.deserialize(
        try writerV1.serialize(sourceV1))
    #expect(decodedAsV2.items[1]?.id == 10)
    #expect(decodedAsV2.items[1]?.name == "first")
    #expect(decodedAsV2.items[1]?.alias == "")
    #expect(decodedAsV2.items[1]?.scores.isEmpty == true)
    #expect(decodedAsV2.items[2]?.id == 20)
    #expect(decodedAsV2.items[2]?.name == "second")
    #expect(decodedAsV2.items[2]?.alias == "")
    #expect(decodedAsV2.items[2]?.scores.isEmpty == true)
}

@Test
func compatibleNestedReadsReuseTypeMeta() throws {
    let writerV1 = Fory(config: .init(trackRef: false, compatible: true))
    try writerV1.register(CompatibleNestedProfileV1.self, id: 9910)
    try writerV1.register(CompatibleNestedArrayV1.self, id: 9911)

    let readerV2 = Fory(config: .init(trackRef: false, compatible: true))
    try readerV2.register(CompatibleNestedProfileV2.self, id: 9910)
    try readerV2.register(CompatibleNestedArrayV2.self, id: 9911)

    let first = CompatibleNestedArrayV1(
        items: [
            CompatibleNestedProfileV1(id: 1, name: "alpha"),
            CompatibleNestedProfileV1(id: 2, name: "beta")
        ]
    )
    let second = CompatibleNestedArrayV1(
        items: [
            CompatibleNestedProfileV1(id: 3, name: "gamma"),
            CompatibleNestedProfileV1(id: 4, name: "delta"),
            CompatibleNestedProfileV1(id: 5, name: "epsilon")
        ]
    )

    let decodedFirst: CompatibleNestedArrayV2 = try readerV2.deserialize(
        try writerV1.serialize(first))
    let decodedSecond: CompatibleNestedArrayV2 = try readerV2.deserialize(
        try writerV1.serialize(second))

    #expect(decodedFirst.items.map(\.id) == [1, 2])
    #expect(decodedSecond.items.map(\.id) == [3, 4, 5])
    #expect(decodedSecond.items.map(\.name) == ["gamma", "delta", "epsilon"])
    #expect(decodedSecond.items.allSatisfy { $0.alias.isEmpty })
    #expect(decodedSecond.items.allSatisfy { $0.scores.isEmpty })
}
