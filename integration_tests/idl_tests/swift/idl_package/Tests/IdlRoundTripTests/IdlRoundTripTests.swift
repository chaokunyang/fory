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
import Fory
@testable import IdlGenerated
import XCTest

final class IdlRoundTripTests: XCTestCase {
    func testAddressBookRoundTripCompatible() throws {
        try runIdlMatrixRoundTrip(compatible: true)
    }

    func testAddressBookRoundTripSchemaConsistent() throws {
        try runIdlMatrixRoundTrip(compatible: false)
    }

    func testEvolvingRoundTrip() throws {
        let foryV1 = Fory(xlang: true, trackRef: false, compatible: true)
        try Evolving1.ForyRegistration.register(foryV1)
        let foryV2 = Fory(xlang: true, trackRef: false, compatible: true)
        try Evolving2.ForyRegistration.register(foryV2)

        let messageV1 = Evolving1.EvolvingMessage(id: 1, name: "Alice", city: "NYC")
        let bytes = try foryV1.serialize(messageV1)
        var messageV2: Evolving2.EvolvingMessage = try foryV2.deserialize(bytes)
        XCTAssertEqual(messageV2.id, messageV1.id)
        XCTAssertEqual(messageV2.name, messageV1.name)
        XCTAssertEqual(messageV2.city, messageV1.city)

        messageV2.email = "alice@example.com"
        let roundBytes = try foryV2.serialize(messageV2)
        let roundMessage: Evolving1.EvolvingMessage = try foryV1.deserialize(roundBytes)
        XCTAssertEqual(roundMessage, messageV1)

        let fixedV1 = Evolving1.FixedMessage(id: 10, name: "Bob", score: 90, note: "note")
        let fixedBytes = try foryV1.serialize(fixedV1)

        do {
            let fixedV2: Evolving2.FixedMessage = try foryV2.deserialize(fixedBytes)
            let fixedRoundBytes = try foryV2.serialize(fixedV2)
            let fixedRound: Evolving1.FixedMessage = try foryV1.deserialize(fixedRoundBytes)
            XCTAssertNotEqual(fixedRound, fixedV1)
        } catch {
            // non-evolving type mismatch is allowed to fail
        }

        let evolvingSizeV1 = Evolving1.EvolvingSizeMessage(payload: "payload")
        let fixedSizeV1 = Evolving1.FixedSizeMessage(payload: "payload")
        let evolvingSizeBytes = try foryV1.serialize(evolvingSizeV1)
        let fixedSizeBytes = try foryV1.serialize(fixedSizeV1)
        XCTAssertLessThan(fixedSizeBytes.count, evolvingSizeBytes.count)

        let evolvingSizeV2: Evolving2.EvolvingSizeMessage = try foryV2.deserialize(evolvingSizeBytes)
        XCTAssertEqual(evolvingSizeV2.payload, evolvingSizeV1.payload)
        let evolvingSizeRoundBytes = try foryV2.serialize(evolvingSizeV2)
        let evolvingSizeRound: Evolving1.EvolvingSizeMessage = try foryV1.deserialize(evolvingSizeRoundBytes)
        XCTAssertEqual(evolvingSizeRound, evolvingSizeV1)

        let fixedSizeV2: Evolving2.FixedSizeMessage = try foryV2.deserialize(fixedSizeBytes)
        XCTAssertEqual(fixedSizeV2.payload, fixedSizeV1.payload)
        let fixedSizeRoundBytes = try foryV2.serialize(fixedSizeV2)
        let fixedSizeRound: Evolving1.FixedSizeMessage = try foryV1.deserialize(fixedSizeRoundBytes)
        XCTAssertEqual(fixedSizeRound, fixedSizeV1)
    }

    func testRootToBytesFromBytes() throws {
        let holder = buildRootHolder()
        let payload = try holder.toBytes()
        let decoded = try Root.MultiHolder.fromBytes(payload)
        assertRootHolder(expected: holder, actual: decoded)
    }

    func testToBytesFromBytesHelpers() throws {
        let book = buildAddressBook()
        let bookBytes = try book.toBytes()
        let decodedBook = try Addressbook.AddressBook.fromBytes(bookBytes)
        XCTAssertEqual(decodedBook, book)

        let animal = Addressbook.Animal.dog(
            Addressbook.Dog(name: "Rex", barkVolume: 5)
        )
        let animalBytes = try animal.toBytes()
        let decodedAnimal = try Addressbook.Animal.fromBytes(animalBytes)
        XCTAssertEqual(decodedAnimal, animal)
    }

    private func runIdlMatrixRoundTrip(compatible: Bool) throws {
        let fory = Fory(xlang: true, trackRef: false, compatible: compatible)
        try Addressbook.ForyRegistration.register(fory)
        try AutoId.ForyRegistration.register(fory)
        try Collection.ForyRegistration.register(fory)
        try OptionalTypes.ForyRegistration.register(fory)
        try AnyExample.ForyRegistration.register(fory)
        try MonsterNamespace.ForyRegistration.register(fory)
        try ComplexFbs.ForyRegistration.register(fory)
        try ExampleCommon.ForyRegistration.register(fory)
        try Example.ForyRegistration.register(fory)

        let book = buildAddressBook()
        let bookRoundTrip: Addressbook.AddressBook = try roundTrip(fory, value: book)
        XCTAssertEqual(bookRoundTrip, book)
        try roundTripFile(fory, env: "DATA_FILE", expected: book) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let envelope = buildAutoIdEnvelope()
        let envelopeRoundTrip: AutoId.Envelope = try roundTrip(fory, value: envelope)
        assertAutoIdEnvelope(expected: envelope, actual: envelopeRoundTrip)
        try roundTripFile(fory, env: "DATA_FILE_AUTO_ID", expected: envelope) { expected, actual in
            self.assertAutoIdEnvelope(expected: expected, actual: actual)
        }

        let wrapper = AutoId.Wrapper.envelope(envelope)
        let wrapperRoundTrip: AutoId.Wrapper = try roundTrip(fory, value: wrapper)
        assertAutoIdWrapper(expected: wrapper, actual: wrapperRoundTrip)

        let primitiveTypes = buildPrimitiveTypes()
        let primitiveRoundTrip: ComplexPb.PrimitiveTypes = try roundTrip(fory, value: primitiveTypes)
        XCTAssertEqual(primitiveRoundTrip, primitiveTypes)
        try roundTripFile(fory, env: "DATA_FILE_PRIMITIVES", expected: primitiveTypes) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let collections = buildNumericCollections()
        let collectionsRoundTrip: Collection.NumericCollections = try roundTrip(fory, value: collections)
        XCTAssertEqual(collectionsRoundTrip, collections)
        try roundTripFile(fory, env: "DATA_FILE_COLLECTION", expected: collections) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let collectionUnion = Collection.NumericCollectionUnion.int32Values([7, 8, 9])
        let collectionUnionRoundTrip: Collection.NumericCollectionUnion = try roundTrip(fory, value: collectionUnion)
        XCTAssertEqual(collectionUnionRoundTrip, collectionUnion)
        try roundTripFile(fory, env: "DATA_FILE_COLLECTION_UNION", expected: collectionUnion) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let collectionsArray = buildNumericCollectionsArray()
        let collectionsArrayRoundTrip: Collection.NumericCollectionsArray = try roundTrip(fory, value: collectionsArray)
        XCTAssertEqual(collectionsArrayRoundTrip, collectionsArray)
        try roundTripFile(fory, env: "DATA_FILE_COLLECTION_ARRAY", expected: collectionsArray) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let collectionArrayUnion = Collection.NumericCollectionArrayUnion.uint16Values([1000, 2000, 3000])
        let collectionArrayUnionRoundTrip: Collection.NumericCollectionArrayUnion = try roundTrip(fory, value: collectionArrayUnion)
        XCTAssertEqual(collectionArrayUnionRoundTrip, collectionArrayUnion)
        try roundTripFile(
            fory,
            env: "DATA_FILE_COLLECTION_ARRAY_UNION",
            expected: collectionArrayUnion
        ) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let optionalHolder = buildOptionalHolder()
        let optionalRoundTrip: OptionalTypes.OptionalHolder = try roundTrip(fory, value: optionalHolder)
        XCTAssertEqual(optionalRoundTrip, optionalHolder)
        try roundTripFile(fory, env: "DATA_FILE_OPTIONAL_TYPES", expected: optionalHolder) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let anyHolder = buildAnyHolder()
        do {
            let anyRoundTrip: AnyExample.AnyHolder = try roundTrip(fory, value: anyHolder)
            assertAnyHolder(expected: anyHolder, actual: anyRoundTrip)
            try roundTripFile(fory, env: "DATA_FILE_ANY", expected: anyHolder) { expected, actual in
                self.assertAnyHolder(expected: expected, actual: actual)
            }
        } catch {
            throw NSError(
                domain: "IdlRoundTripTests",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "any_example roundtrip failed: \(error)"]
            )
        }

        let anyProtoFory = Fory(xlang: true, trackRef: false, compatible: compatible)
        try AnyExamplePb.ForyRegistration.register(anyProtoFory)
        let anyProtoHolder = buildAnyProtoHolder()
        do {
            let anyProtoRoundTrip: AnyExamplePb.AnyHolder = try roundTrip(anyProtoFory, value: anyProtoHolder)
            assertAnyProtoHolder(expected: anyProtoHolder, actual: anyProtoRoundTrip)
            try roundTripFile(anyProtoFory, env: "DATA_FILE_ANY_PROTO", expected: anyProtoHolder) { expected, actual in
                self.assertAnyProtoHolder(expected: expected, actual: actual)
            }
        } catch {
            throw NSError(
                domain: "IdlRoundTripTests",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "any_example_pb roundtrip failed: \(error)"]
            )
        }

        let monster = buildMonster()
        let monsterRoundTrip: MonsterNamespace.Monster = try roundTrip(fory, value: monster)
        XCTAssertEqual(monsterRoundTrip, monster)
        try roundTripFile(fory, env: "DATA_FILE_FLATBUFFERS_MONSTER", expected: monster) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let container = buildContainer()
        let containerRoundTrip: ComplexFbs.Container = try roundTrip(fory, value: container)
        XCTAssertEqual(containerRoundTrip, container)
        try roundTripFile(fory, env: "DATA_FILE_FLATBUFFERS_TEST2", expected: container) { expected, actual in
            XCTAssertEqual(actual, expected)
        }

        let exampleMessage = buildExampleMessage()
        let exampleMessageRoundTrip: Example.ExampleMessage = try roundTrip(fory, value: exampleMessage)
        assertExampleMessage(expected: exampleMessage, actual: exampleMessageRoundTrip)
        try roundTripExampleMessageFile(fory, compatible: compatible, expected: exampleMessage)

        let exampleUnion = buildExampleMessageUnion()
        let exampleUnionRoundTrip: Example.ExampleMessageUnion = try roundTrip(fory, value: exampleUnion)
        assertExampleMessageUnion(expected: exampleUnion, actual: exampleUnionRoundTrip)
        try roundTripFile(fory, env: "DATA_FILE_EXAMPLE_UNION", expected: exampleUnion) { expected, actual in
            self.assertExampleMessageUnion(expected: expected, actual: actual)
        }

        let refFory = Fory(xlang: true, trackRef: true, compatible: compatible)
        try Tree.ForyRegistration.register(refFory)
        try GraphNamespace.ForyRegistration.register(refFory)
        try Root.ForyRegistration.register(refFory)

        let tree = buildTree()
        let treeRoundTrip: Tree.TreeNode = try roundTrip(refFory, value: tree)
        assertTree(treeRoundTrip)
        try roundTripFile(refFory, env: "DATA_FILE_TREE", expected: tree) { _, actual in
            self.assertTree(actual)
        }

        let graph = buildGraph()
        let graphRoundTrip: GraphNamespace.Graph = try roundTrip(refFory, value: graph)
        assertGraph(graphRoundTrip)
        try roundTripFile(refFory, env: "DATA_FILE_GRAPH", expected: graph) { _, actual in
            self.assertGraph(actual)
        }

        let rootHolder = buildRootHolder()
        let rootRoundTrip: Root.MultiHolder = try roundTrip(refFory, value: rootHolder)
        assertRootHolder(expected: rootHolder, actual: rootRoundTrip)
        try roundTripFile(refFory, env: "DATA_FILE_ROOT", expected: rootHolder) { expected, actual in
            self.assertRootHolder(expected: expected, actual: actual)
        }
    }

    private func roundTrip<T: Serializer>(_ fory: Fory, value: T) throws -> T {
        let payload = try fory.serialize(value)
        return try fory.deserialize(payload)
    }

    private func roundTripFile<T: Serializer>(
        _ fory: Fory,
        env: String,
        expected: T,
        assertRoundTrip: (T, T) throws -> Void
    ) throws {
        guard let path = ProcessInfo.processInfo.environment[env], !path.isEmpty else {
            return
        }
        let url = URL(fileURLWithPath: path)
        let fileManager = FileManager.default
        if !fileManager.fileExists(atPath: path) {
            fileManager.createFile(atPath: path, contents: nil)
        }

        let inputData: Data
        if let existing = try? Data(contentsOf: url), !existing.isEmpty {
            inputData = existing
        } else {
            inputData = try fory.serialize(expected)
            try inputData.write(to: url)
        }

        let decoded: T = try fory.deserialize(inputData)
        try assertRoundTrip(expected, decoded)
        let output = try fory.serialize(decoded)
        try output.write(to: url)
    }

    private func roundTripExampleMessageFile(
        _ fory: Fory,
        compatible: Bool,
        expected: Example.ExampleMessage
    ) throws {
        guard let path = ProcessInfo.processInfo.environment["DATA_FILE_EXAMPLE_MESSAGE"], !path.isEmpty else {
            return
        }
        let url = URL(fileURLWithPath: path)
        let fileManager = FileManager.default
        if !fileManager.fileExists(atPath: path) {
            fileManager.createFile(atPath: path, contents: nil)
        }

        let inputData: Data
        if let existing = try? Data(contentsOf: url), !existing.isEmpty {
            inputData = existing
        } else {
            inputData = try fory.serialize(expected)
            try inputData.write(to: url)
        }

        if compatible {
            try assertExampleMessageSchemaEvolution(payload: inputData, expected: expected)
        }

        let decoded: Example.ExampleMessage = try fory.deserialize(inputData)
        assertExampleMessage(expected: expected, actual: decoded)
        let output = try fory.serialize(decoded)
        try output.write(to: url)
    }

    private func buildAddressBook() -> Addressbook.AddressBook {
        let mobile = Addressbook.Person.PhoneNumber(number: "555-0100", phoneType: .mobile)
        let work = Addressbook.Person.PhoneNumber(number: "555-0111", phoneType: .work)

        var pet = Addressbook.Animal.dog(Addressbook.Dog(name: "Rex", barkVolume: 5))
        pet = .cat(Addressbook.Cat(name: "Mimi", lives: 9))

        let person = Addressbook.Person(
            name: "Alice",
            id: 123,
            email: "alice@example.com",
            tags: ["friend", "colleague"],
            scores: ["math": 100, "science": 98],
            salary: 120000.5,
            phones: [mobile, work],
            pet: pet
        )

        return Addressbook.AddressBook(
            people: [person],
            peopleByName: [person.name: person]
        )
    }

    private func buildAutoIdEnvelope() -> AutoId.Envelope {
        let payload = AutoId.Envelope.Payload(value: 42)
        return AutoId.Envelope(
            id: "env-1",
            payload: payload,
            detail: .payload(payload),
            status: .ok
        )
    }

    private func assertAutoIdEnvelope(expected: AutoId.Envelope, actual: AutoId.Envelope) {
        XCTAssertEqual(actual.id, expected.id)
        XCTAssertEqual(actual.status, expected.status)
        XCTAssertEqual(actual.payload?.value, expected.payload?.value)

        switch (expected.detail, actual.detail) {
        case let (.payload(left), .payload(right)):
            XCTAssertEqual(right, left)
        case let (.note(left), .note(right)):
            XCTAssertEqual(right, left)
        default:
            XCTFail("AutoId.Envelope.Detail case mismatch")
        }
    }

    private func assertAutoIdWrapper(expected: AutoId.Wrapper, actual: AutoId.Wrapper) {
        switch (expected, actual) {
        case let (.envelope(expectedEnvelope), .envelope(actualEnvelope)):
            assertAutoIdEnvelope(expected: expectedEnvelope, actual: actualEnvelope)
        case let (.raw(expectedRaw), .raw(actualRaw)):
            XCTAssertEqual(actualRaw, expectedRaw)
        default:
            XCTFail("AutoId.Wrapper case mismatch")
        }
    }

    private func buildPrimitiveTypes() -> ComplexPb.PrimitiveTypes {
        var contact = ComplexPb.PrimitiveTypes.Contact.email("alice@example.com")
        contact = .phone(12345)

        return ComplexPb.PrimitiveTypes(
            boolValue: true,
            int8Value: 12,
            int16Value: 1234,
            int32Value: -123456,
            varint32Value: -12345,
            int64Value: -123456789,
            varint64Value: -987654321,
            taggedInt64Value: 123456789,
            uint8Value: 200,
            uint16Value: 60000,
            uint32Value: 1234567890,
            varUint32Value: 1234567890,
            uint64Value: 9876543210,
            varUint64Value: 12345678901,
            taggedUint64Value: 2222222222,
            float32Value: 2.5,
            float64Value: 3.5,
            contact: contact
        )
    }

    private func buildNumericCollections() -> Collection.NumericCollections {
        Collection.NumericCollections(
            int8Values: [1, -2, 3],
            int16Values: [100, -200, 300],
            int32Values: [1000, -2000, 3000],
            int64Values: [10000, -20000, 30000],
            uint8Values: [200, 250],
            uint16Values: [50000, 60000],
            uint32Values: [2000000000, 2100000000],
            uint64Values: [9000000000, 12000000000],
            float32Values: [1.5, 2.5],
            float64Values: [3.5, 4.5]
        )
    }

    private func buildNumericCollectionsArray() -> Collection.NumericCollectionsArray {
        Collection.NumericCollectionsArray(
            int8Values: [1, -2, 3],
            int16Values: [100, -200, 300],
            int32Values: [1000, -2000, 3000],
            int64Values: [10000, -20000, 30000],
            uint8Values: [200, 250],
            uint16Values: [50000, 60000],
            uint32Values: [2000000000, 2100000000],
            uint64Values: [9000000000, 12000000000],
            float32Values: [1.5, 2.5],
            float64Values: [3.5, 4.5]
        )
    }

    private func buildOptionalHolder() -> OptionalTypes.OptionalHolder {
        let allTypes = OptionalTypes.AllOptionalTypes(
            boolValue: true,
            int8Value: 12,
            int16Value: 1234,
            int32Value: -123456,
            fixedInt32Value: -123456,
            varint32Value: -12345,
            int64Value: -123456789,
            fixedInt64Value: -123456789,
            varint64Value: -987654321,
            taggedInt64Value: 123456789,
            uint8Value: 200,
            uint16Value: 60000,
            uint32Value: 1234567890,
            fixedUint32Value: 1234567890,
            varUint32Value: 1234567890,
            uint64Value: 9876543210,
            fixedUint64Value: 9876543210,
            varUint64Value: 12345678901,
            taggedUint64Value: 2222222222,
            float32Value: 2.5,
            float64Value: 3.5,
            stringValue: "optional",
            bytesValue: Data([1, 2, 3]),
            dateValue: LocalDate(daysSinceEpoch: 19724),
            timestampValue: Timestamp(seconds: 1_704_164_645, nanos: 0),
            int32List: [1, 2, 3],
            stringList: ["alpha", "beta"],
            int64Map: ["alpha": 10, "beta": 20]
        )
        return OptionalTypes.OptionalHolder(
            allTypes: allTypes,
            choice: .note("optional")
        )
    }

    private func buildAnyHolder() -> AnyExample.AnyHolder {
        AnyExample.AnyHolder(
            boolValue: true,
            stringValue: "hello",
            dateValue: LocalDate(daysSinceEpoch: 19724),
            timestampValue: Timestamp(seconds: 1_704_164_645, nanos: 0),
            messageValue: AnyExample.AnyInner(name: "inner"),
            unionValue: AnyExample.AnyUnion.text("union"),
            listValue: ["alpha", "beta"],
            mapValue: ["k1": "v1", "k2": "v2"]
        )
    }

    private func buildAnyProtoHolder() -> AnyExamplePb.AnyHolder {
        AnyExamplePb.AnyHolder(
            boolValue: true,
            stringValue: "hello",
            dateValue: LocalDate(daysSinceEpoch: 19724),
            timestampValue: Timestamp(seconds: 1_704_164_645, nanos: 0),
            messageValue: AnyExamplePb.AnyInner(name: "inner"),
            unionValue: AnyExamplePb.AnyUnion(kind: .text("proto-union")),
            listValue: ["alpha", "beta"],
            mapValue: ["k1": "v1", "k2": "v2"]
        )
    }

    private func buildExampleMessage() -> Example.ExampleMessage {
        let leafA = ExampleCommon.ExampleLeaf(label: "leaf-a", count: 7)
        let leafB = ExampleCommon.ExampleLeaf(label: "leaf-b", count: -3)
        let timestamp = exampleTimestamp()
        let duration = exampleDuration()
        let decimal = exampleDecimal()

        return Example.ExampleMessage(
            boolValue: true,
            int8Value: -12,
            int16Value: 1234,
            fixedInt32Value: 123_456_789,
            varint32Value: -1_234_567,
            fixedInt64Value: 1_234_567_890_123_456_789,
            varint64Value: -1_234_567_890_123_456_789,
            taggedInt64Value: 1_073_741_824,
            uint8Value: 200,
            uint16Value: 60_000,
            fixedUint32Value: 2_000_000_000,
            varUint32Value: 2_100_000_000,
            fixedUint64Value: 9_000_000_000,
            varUint64Value: 12_000_000_000,
            taggedUint64Value: 2_222_222_222,
            float16Value: Float16(1.5),
            bfloat16Value: BFloat16(rawValue: 0xC030),
            float32Value: 3.25,
            float64Value: -4.5,
            stringValue: "example-string",
            bytesValue: Data([1, 2, 3, 4]),
            dateValue: LocalDate.fromEpochDay(19_782),
            timestampValue: timestamp,
            durationValue: duration,
            decimalValue: decimal,
            enumValue: .ready,
            messageValue: leafA,
            unionValue: .leaf(leafB),
            boolList: [true, false],
            int8List: [-12, 7],
            int16List: [1234, -2345],
            fixedInt32List: [123_456_789, -123_456_789],
            varint32List: [-1_234_567, 7_654_321],
            fixedInt64List: [1_234_567_890_123_456_789, -123_456_789_012_345_678],
            varint64List: [-1_234_567_890_123_456_789, 123_456_789_012_345_678],
            taggedInt64List: [1_073_741_824, -1_073_741_824],
            uint8List: [200, 42],
            uint16List: [60_000, 12_345],
            fixedUint32List: [2_000_000_000, 1_234_567_890],
            varUint32List: [2_100_000_000, 1_234_567_890],
            fixedUint64List: [9_000_000_000, 4_000_000_000],
            varUint64List: [12_000_000_000, 5_000_000_000],
            taggedUint64List: [2_222_222_222, 3_333_333_333],
            float16List: [Float16(1.5), Float16(-0.5)],
            bfloat16List: [BFloat16(rawValue: 0xC030), BFloat16(rawValue: 0x4010)],
            maybeFloat16List: [Float16(1.5), nil, Float16(-0.5)],
            maybeBfloat16List: [nil, BFloat16(rawValue: 0x4010), BFloat16(rawValue: 0xBF80)],
            float32List: [3.25, -0.5],
            float64List: [-4.5, 6.75],
            stringList: ["example-string", "secondary"],
            bytesList: [Data([1, 2, 3, 4]), Data([5, 6])],
            dateList: [LocalDate.fromEpochDay(19_782), LocalDate.fromEpochDay(19_783)],
            timestampList: [timestamp, Timestamp(seconds: 1_709_251_200, nanos: 123_456_000)],
            durationList: [duration, .seconds(1) + .nanoseconds(234_567_000)],
            decimalList: [decimal, decimalWithString("-0.5")],
            enumList: [.ready, .failed],
            messageList: [leafA, leafB],
            unionList: [.leaf(leafA), .leaf(leafB)],
            stringValuesByBool: [true: "true-value", false: "false-value"],
            stringValuesByInt8: [-12: "minus-twelve"],
            stringValuesByInt16: [1234: "twelve-thirty-four"],
            stringValuesByFixedInt32: [.init(rawValue: 123_456_789): "fixed-int32"],
            stringValuesByVarint32: [-1_234_567: "varint32"],
            stringValuesByFixedInt64: [.init(rawValue: 1_234_567_890_123_456_789): "fixed-int64"],
            stringValuesByVarint64: [-1_234_567_890_123_456_789: "varint64"],
            stringValuesByTaggedInt64: [.init(rawValue: 1_073_741_824): "tagged-int64"],
            stringValuesByUint8: [200: "uint8"],
            stringValuesByUint16: [60_000: "uint16"],
            stringValuesByFixedUint32: [.init(rawValue: 2_000_000_000): "fixed-uint32"],
            stringValuesByVarUint32: [2_100_000_000: "var-uint32"],
            stringValuesByFixedUint64: [.init(rawValue: 9_000_000_000): "fixed-uint64"],
            stringValuesByVarUint64: [12_000_000_000: "var-uint64"],
            stringValuesByTaggedUint64: [.init(rawValue: 2_222_222_222): "tagged-uint64"],
            stringValuesByString: ["example-string": "string"],
            stringValuesByTimestamp: [timestamp: "timestamp"],
            stringValuesByDuration: [duration: "duration"],
            stringValuesByEnum: [.ready: "ready"],
            float16ValuesByName: ["primary": Float16(1.5)],
            maybeFloat16ValuesByName: ["primary": Float16(1.5), "missing": nil],
            bfloat16ValuesByName: ["primary": BFloat16(rawValue: 0xC030)],
            maybeBfloat16ValuesByName: ["missing": nil, "secondary": BFloat16(rawValue: 0x4010)],
            bytesValuesByName: ["payload": Data([1, 2, 3, 4])],
            dateValuesByName: ["leap-day": LocalDate.fromEpochDay(19_782)],
            decimalValuesByName: ["amount": decimal],
            messageValuesByName: ["leaf-a": leafA, "leaf-b": leafB],
            unionValuesByName: ["leaf-b": .leaf(leafB)]
        )
    }

    private func buildExampleMessageUnion() -> Example.ExampleMessageUnion {
        .unionValue(.leaf(ExampleCommon.ExampleLeaf(label: "leaf-b", count: -3)))
    }

    private func assertAnyHolder(expected: AnyExample.AnyHolder, actual: AnyExample.AnyHolder) {
        XCTAssertEqual(actual.boolValue as? Bool, expected.boolValue as? Bool)
        XCTAssertEqual(actual.stringValue as? String, expected.stringValue as? String)
        XCTAssertEqual(actual.dateValue as? LocalDate, expected.dateValue as? LocalDate)
        XCTAssertEqual(normalizeTimestampAny(actual.timestampValue), normalizeTimestampAny(expected.timestampValue))
        XCTAssertEqual(actual.messageValue as? AnyExample.AnyInner, expected.messageValue as? AnyExample.AnyInner)
        XCTAssertEqual(actual.unionValue as? AnyExample.AnyUnion, expected.unionValue as? AnyExample.AnyUnion)
        XCTAssertEqual(normalizeStringList(actual.listValue), normalizeStringList(expected.listValue))
        XCTAssertEqual(normalizeStringMap(actual.mapValue), normalizeStringMap(expected.mapValue))
    }

    private func assertAnyProtoHolder(expected: AnyExamplePb.AnyHolder, actual: AnyExamplePb.AnyHolder) {
        XCTAssertEqual(actual.boolValue as? Bool, expected.boolValue as? Bool)
        XCTAssertEqual(actual.stringValue as? String, expected.stringValue as? String)
        XCTAssertEqual(actual.dateValue as? LocalDate, expected.dateValue as? LocalDate)
        XCTAssertEqual(normalizeTimestampAny(actual.timestampValue), normalizeTimestampAny(expected.timestampValue))
        XCTAssertEqual(actual.messageValue as? AnyExamplePb.AnyInner, expected.messageValue as? AnyExamplePb.AnyInner)
        XCTAssertEqual(actual.unionValue as? AnyExamplePb.AnyUnion, expected.unionValue as? AnyExamplePb.AnyUnion)
        XCTAssertEqual(normalizeStringList(actual.listValue), normalizeStringList(expected.listValue))
        XCTAssertEqual(normalizeStringMap(actual.mapValue), normalizeStringMap(expected.mapValue))
    }

    private func normalizeTimestampAny(_ value: Any?) -> Timestamp? {
        switch value {
        case let timestamp as Timestamp:
            return timestamp
        case let date as Date:
            return Timestamp(date: date)
        default:
            return nil
        }
    }

    private func normalizeStringList(_ value: Any) -> [String]? {
        if let list = value as? [String] {
            return list
        }
        guard let list = value as? [Any] else {
            return nil
        }
        return list.compactMap { $0 as? String }.count == list.count ? list.compactMap { $0 as? String } : nil
    }

    private func normalizeStringMap(_ value: Any) -> [String: String]? {
        if let map = value as? [String: String] {
            return map
        }
        if let map = value as? [String: Any] {
            var normalized: [String: String] = [:]
            for (key, item) in map {
                guard let stringValue = item as? String else {
                    return nil
                }
                normalized[key] = stringValue
            }
            return normalized
        }
        if let map = value as? [AnyHashable: Any] {
            var normalized: [String: String] = [:]
            for (key, item) in map {
                guard let stringKey = key.base as? String, let stringValue = item as? String else {
                    return nil
                }
                normalized[stringKey] = stringValue
            }
            return normalized
        }
        return nil
    }

    private func exampleTimestamp() -> Timestamp {
        Timestamp(seconds: 1_709_210_096, nanos: 789_123_000)
    }

    private func exampleDuration() -> Duration {
        .seconds(3723) + .nanoseconds(456_789_000)
    }

    private func exampleDecimal() -> Decimal {
        decimalWithString("123456789012345.6789")
    }

    private func decimalWithString(_ value: String) -> Decimal {
        Decimal(string: value, locale: Locale(identifier: "en_US_POSIX"))!
    }

    private func assertExampleMessage(expected: Example.ExampleMessage, actual: Example.ExampleMessage) {
        XCTAssertEqual(actual.boolValue, expected.boolValue)
        XCTAssertEqual(actual.int8Value, expected.int8Value)
        XCTAssertEqual(actual.int16Value, expected.int16Value)
        XCTAssertEqual(actual.fixedInt32Value, expected.fixedInt32Value)
        XCTAssertEqual(actual.varint32Value, expected.varint32Value)
        XCTAssertEqual(actual.fixedInt64Value, expected.fixedInt64Value)
        XCTAssertEqual(actual.varint64Value, expected.varint64Value)
        XCTAssertEqual(actual.taggedInt64Value, expected.taggedInt64Value)
        XCTAssertEqual(actual.uint8Value, expected.uint8Value)
        XCTAssertEqual(actual.uint16Value, expected.uint16Value)
        XCTAssertEqual(actual.fixedUint32Value, expected.fixedUint32Value)
        XCTAssertEqual(actual.varUint32Value, expected.varUint32Value)
        XCTAssertEqual(actual.fixedUint64Value, expected.fixedUint64Value)
        XCTAssertEqual(actual.varUint64Value, expected.varUint64Value)
        XCTAssertEqual(actual.taggedUint64Value, expected.taggedUint64Value)
        XCTAssertEqual(actual.float16Value, expected.float16Value)
        XCTAssertEqual(actual.bfloat16Value, expected.bfloat16Value)
        XCTAssertEqual(actual.float32Value, expected.float32Value)
        XCTAssertEqual(actual.float64Value, expected.float64Value)
        XCTAssertEqual(actual.stringValue, expected.stringValue)
        XCTAssertEqual(actual.bytesValue, expected.bytesValue)
        XCTAssertEqual(actual.dateValue, expected.dateValue)
        assertTimestampsEqual(expected: expected.timestampValue, actual: actual.timestampValue)
        XCTAssertEqual(actual.durationValue, expected.durationValue)
        assertDecimalsEqual(expected: expected.decimalValue, actual: actual.decimalValue)
        XCTAssertEqual(actual.enumValue, expected.enumValue)
        XCTAssertEqual(actual.messageValue, expected.messageValue)
        assertExampleLeafUnion(expected: expected.unionValue, actual: actual.unionValue)
        XCTAssertEqual(actual.boolList, expected.boolList)
        XCTAssertEqual(actual.int8List, expected.int8List)
        XCTAssertEqual(actual.int16List, expected.int16List)
        XCTAssertEqual(actual.fixedInt32List, expected.fixedInt32List)
        XCTAssertEqual(actual.varint32List, expected.varint32List)
        XCTAssertEqual(actual.fixedInt64List, expected.fixedInt64List)
        XCTAssertEqual(actual.varint64List, expected.varint64List)
        XCTAssertEqual(actual.taggedInt64List, expected.taggedInt64List)
        XCTAssertEqual(actual.uint8List, expected.uint8List)
        XCTAssertEqual(actual.uint16List, expected.uint16List)
        XCTAssertEqual(actual.fixedUint32List, expected.fixedUint32List)
        XCTAssertEqual(actual.varUint32List, expected.varUint32List)
        XCTAssertEqual(actual.fixedUint64List, expected.fixedUint64List)
        XCTAssertEqual(actual.varUint64List, expected.varUint64List)
        XCTAssertEqual(actual.taggedUint64List, expected.taggedUint64List)
        XCTAssertEqual(actual.float16List, expected.float16List)
        XCTAssertEqual(actual.bfloat16List, expected.bfloat16List)
        XCTAssertEqual(actual.maybeFloat16List, expected.maybeFloat16List)
        XCTAssertEqual(actual.maybeBfloat16List, expected.maybeBfloat16List)
        XCTAssertEqual(actual.float32List, expected.float32List)
        XCTAssertEqual(actual.float64List, expected.float64List)
        XCTAssertEqual(actual.stringList, expected.stringList)
        XCTAssertEqual(actual.bytesList, expected.bytesList)
        XCTAssertEqual(actual.dateList, expected.dateList)
        assertTimestampListsEqual(expected: expected.timestampList, actual: actual.timestampList)
        XCTAssertEqual(actual.durationList, expected.durationList)
        assertDecimalListsEqual(expected: expected.decimalList, actual: actual.decimalList)
        XCTAssertEqual(actual.enumList, expected.enumList)
        XCTAssertEqual(actual.messageList, expected.messageList)
        assertExampleLeafUnionListsEqual(expected: expected.unionList, actual: actual.unionList)
        XCTAssertEqual(actual.stringValuesByBool, expected.stringValuesByBool)
        XCTAssertEqual(actual.stringValuesByInt8, expected.stringValuesByInt8)
        XCTAssertEqual(actual.stringValuesByInt16, expected.stringValuesByInt16)
        XCTAssertEqual(actual.stringValuesByFixedInt32, expected.stringValuesByFixedInt32)
        XCTAssertEqual(actual.stringValuesByVarint32, expected.stringValuesByVarint32)
        XCTAssertEqual(actual.stringValuesByFixedInt64, expected.stringValuesByFixedInt64)
        XCTAssertEqual(actual.stringValuesByVarint64, expected.stringValuesByVarint64)
        XCTAssertEqual(actual.stringValuesByTaggedInt64, expected.stringValuesByTaggedInt64)
        XCTAssertEqual(actual.stringValuesByUint8, expected.stringValuesByUint8)
        XCTAssertEqual(actual.stringValuesByUint16, expected.stringValuesByUint16)
        XCTAssertEqual(actual.stringValuesByFixedUint32, expected.stringValuesByFixedUint32)
        XCTAssertEqual(actual.stringValuesByVarUint32, expected.stringValuesByVarUint32)
        XCTAssertEqual(actual.stringValuesByFixedUint64, expected.stringValuesByFixedUint64)
        XCTAssertEqual(actual.stringValuesByVarUint64, expected.stringValuesByVarUint64)
        XCTAssertEqual(actual.stringValuesByTaggedUint64, expected.stringValuesByTaggedUint64)
        XCTAssertEqual(actual.stringValuesByString, expected.stringValuesByString)
        assertTimestampMapsEqual(expected: expected.stringValuesByTimestamp, actual: actual.stringValuesByTimestamp)
        XCTAssertEqual(actual.stringValuesByDuration, expected.stringValuesByDuration)
        XCTAssertEqual(actual.stringValuesByEnum, expected.stringValuesByEnum)
        XCTAssertEqual(actual.float16ValuesByName, expected.float16ValuesByName)
        XCTAssertEqual(actual.maybeFloat16ValuesByName, expected.maybeFloat16ValuesByName)
        XCTAssertEqual(actual.bfloat16ValuesByName, expected.bfloat16ValuesByName)
        XCTAssertEqual(actual.maybeBfloat16ValuesByName, expected.maybeBfloat16ValuesByName)
        XCTAssertEqual(actual.bytesValuesByName, expected.bytesValuesByName)
        XCTAssertEqual(actual.dateValuesByName, expected.dateValuesByName)
        assertDecimalMapsEqual(expected: expected.decimalValuesByName, actual: actual.decimalValuesByName)
        XCTAssertEqual(actual.messageValuesByName, expected.messageValuesByName)
        assertExampleLeafUnionMapsEqual(expected: expected.unionValuesByName, actual: actual.unionValuesByName)
    }

    private func assertExampleMessageUnion(expected: Example.ExampleMessageUnion, actual: Example.ExampleMessageUnion) {
        switch (expected, actual) {
        case let (.unionValue(expectedValue), .unionValue(actualValue)):
            assertExampleLeafUnion(expected: expectedValue, actual: actualValue)
        default:
            XCTFail("Example.ExampleMessageUnion case mismatch")
        }
    }

    private func assertExampleLeafUnion(expected: ExampleCommon.ExampleLeafUnion, actual: ExampleCommon.ExampleLeafUnion) {
        switch (expected, actual) {
        case let (.note(expectedValue), .note(actualValue)):
            XCTAssertEqual(actualValue, expectedValue)
        case let (.code(expectedValue), .code(actualValue)):
            XCTAssertEqual(actualValue, expectedValue)
        case let (.leaf(expectedValue), .leaf(actualValue)):
            XCTAssertEqual(actualValue, expectedValue)
        default:
            XCTFail("ExampleCommon.ExampleLeafUnion case mismatch")
        }
    }

    private func assertExampleLeafUnionListsEqual(
        expected: [ExampleCommon.ExampleLeafUnion],
        actual: [ExampleCommon.ExampleLeafUnion]
    ) {
        XCTAssertEqual(actual.count, expected.count)
        for (expectedValue, actualValue) in zip(expected, actual) {
            assertExampleLeafUnion(expected: expectedValue, actual: actualValue)
        }
    }

    private func assertExampleLeafUnionMapsEqual(
        expected: [String: ExampleCommon.ExampleLeafUnion],
        actual: [String: ExampleCommon.ExampleLeafUnion]
    ) {
        XCTAssertEqual(actual.keys.sorted(), expected.keys.sorted())
        for key in expected.keys.sorted() {
            guard let expectedValue = expected[key], let actualValue = actual[key] else {
                XCTFail("Missing union value for key \(key)")
                continue
            }
            assertExampleLeafUnion(expected: expectedValue, actual: actualValue)
        }
    }

    private func assertDecimalsEqual(expected: Decimal, actual: Decimal) {
        do {
            let fory = Fory(xlang: true, trackRef: false, compatible: true)
            XCTAssertEqual(try fory.serialize(actual), try fory.serialize(expected))
        } catch {
            XCTFail("Failed to compare decimal wire form: \(error)")
        }
    }

    private func assertDecimalListsEqual(expected: [Decimal], actual: [Decimal]) {
        XCTAssertEqual(actual.count, expected.count)
        for (expectedValue, actualValue) in zip(expected, actual) {
            assertDecimalsEqual(expected: expectedValue, actual: actualValue)
        }
    }

    private func assertDecimalMapsEqual(expected: [String: Decimal], actual: [String: Decimal]) {
        XCTAssertEqual(actual.keys.sorted(), expected.keys.sorted())
        for key in expected.keys.sorted() {
            guard let expectedValue = expected[key], let actualValue = actual[key] else {
                XCTFail("Missing decimal value for key \(key)")
                continue
            }
            assertDecimalsEqual(expected: expectedValue, actual: actualValue)
        }
    }

    private func assertTimestampsEqual(expected: Timestamp, actual: Timestamp) {
        XCTAssertEqual(actual, expected)
    }

    private func assertTimestampListsEqual(expected: [Timestamp], actual: [Timestamp]) {
        XCTAssertEqual(actual.count, expected.count)
        for (expectedValue, actualValue) in zip(expected, actual) {
            assertTimestampsEqual(expected: expectedValue, actual: actualValue)
        }
    }

    private func assertTimestampMapsEqual(expected: [Timestamp: String], actual: [Timestamp: String]) {
        let expectedEntries = expected.map { ($0.key.seconds, $0.key.nanos, $0.value) }.sorted {
            ($0.0, $0.1) < ($1.0, $1.1)
        }
        let actualEntries = actual.map { ($0.key.seconds, $0.key.nanos, $0.value) }.sorted {
            ($0.0, $0.1) < ($1.0, $1.1)
        }
        XCTAssertEqual(actualEntries.count, expectedEntries.count)
        for (expectedEntry, actualEntry) in zip(expectedEntries, actualEntries) {
            XCTAssertEqual(actualEntry.0, expectedEntry.0)
            XCTAssertEqual(actualEntry.1, expectedEntry.1)
            XCTAssertEqual(actualEntry.2, expectedEntry.2)
        }
    }

    private func buildMonster() -> MonsterNamespace.Monster {
        MonsterNamespace.Monster(
            pos: MonsterNamespace.Vec3(x: 1.0, y: 2.0, z: 3.0),
            mana: 200,
            hp: 80,
            name: "Orc",
            friendly: true,
            inventory: [1, 2, 3],
            color: .blue
        )
    }

    private func buildContainer() -> ComplexFbs.Container {
        let scalars = ComplexFbs.ScalarPack(
            b: -8,
            ub: 200,
            s: -1234,
            us: 40000,
            i: -123456,
            ui: 123456,
            l: -123456789,
            ul: 987654321,
            f: 1.5,
            d: 2.5,
            ok: true
        )
        return ComplexFbs.Container(
            id: 9876543210,
            status: .started,
            bytes: [1, 2, 3],
            numbers: [10, 20, 30],
            scalars: scalars,
            names: ["alpha", "beta"],
            flags: [true, false],
            payload: .metric(ComplexFbs.Metric(value: 42.0))
        )
    }

    private func buildTree() -> Tree.TreeNode {
        let childA = Tree.TreeNode()
        childA.id = "child-a"
        childA.name = "child-a"

        let childB = Tree.TreeNode()
        childB.id = "child-b"
        childB.name = "child-b"

        childA.parent = childB
        childB.parent = childA

        let root = Tree.TreeNode()
        root.id = "root"
        root.name = "root"
        root.children = [childA, childA, childB]
        return root
    }

    private func assertTree(_ root: Tree.TreeNode) {
        XCTAssertEqual(root.children.count, 3)
        XCTAssertTrue(root.children[0] === root.children[1])
        XCTAssertFalse(root.children[0] === root.children[2])
        XCTAssertTrue(root.children[0].parent === root.children[2])
        XCTAssertTrue(root.children[2].parent === root.children[0])
    }

    private func buildGraph() -> GraphNamespace.Graph {
        let nodeA = GraphNamespace.Node()
        nodeA.id = "node-a"

        let nodeB = GraphNamespace.Node()
        nodeB.id = "node-b"

        let edge = GraphNamespace.Edge()
        edge.id = "edge-1"
        edge.weight = 1.5
        edge.from = nodeA
        edge.to = nodeB

        nodeA.outEdges = [edge]
        nodeA.inEdges = [edge]
        nodeB.inEdges = [edge]
        let graph = GraphNamespace.Graph()
        graph.nodes = [nodeA, nodeB]
        graph.edges = [edge]
        return graph
    }

    private func assertGraph(_ value: GraphNamespace.Graph) {
        XCTAssertEqual(value.nodes.count, 2)
        XCTAssertEqual(value.edges.count, 1)

        let nodeA = value.nodes[0]
        let nodeB = value.nodes[1]
        let edge = value.edges[0]

        XCTAssertTrue(nodeA.outEdges[0] === nodeA.inEdges[0])
        XCTAssertTrue(nodeA.outEdges[0] === edge)
        XCTAssertTrue(edge.from === nodeA)
        XCTAssertTrue(edge.to === nodeB)
    }

    private func buildRootHolder() -> Root.MultiHolder {
        let owner = Addressbook.Person(
            name: "Alice",
            id: 123,
            email: "",
            tags: [],
            scores: [:],
            salary: 0,
            phones: [],
            pet: .dog(Addressbook.Dog(name: "Rex", barkVolume: 5))
        )
        let book = Addressbook.AddressBook(
            people: [owner],
            peopleByName: [owner.name: owner]
        )
        let rootNode = Tree.TreeNode()
        rootNode.id = "root"
        rootNode.name = "root"
        rootNode.children = []
        return Root.MultiHolder(
            book: book,
            root: rootNode,
            owner: owner
        )
    }

    private func assertRootHolder(expected: Root.MultiHolder, actual: Root.MultiHolder) {
        XCTAssertEqual(actual.book, expected.book)
        XCTAssertEqual(actual.owner, expected.owner)

        if let expectedRoot = expected.root, let actualRoot = actual.root {
            XCTAssertEqual(actualRoot.id, expectedRoot.id)
            XCTAssertEqual(actualRoot.name, expectedRoot.name)
            XCTAssertEqual(actualRoot.children.count, expectedRoot.children.count)
        } else {
            XCTAssertNil(actual.root)
            XCTAssertNil(expected.root)
        }
    }
}
