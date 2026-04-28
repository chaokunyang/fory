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

private let exampleMessageTypeID: UInt32 = 1500

private struct ExampleSchemaEvolutionVariantSpec {
    let fieldName: String
    let assertDecode: (_ payload: Data, _ expected: Example.ExampleMessage) throws -> Void
}

private func buildExampleSchemaEvolutionFory<T: Serializer>(_ type: T.Type) throws -> Fory {
    let fory = Fory(xlang: true, trackRef: false, compatible: true)
    try ExampleCommon.ForyRegistration.register(fory)
    fory.register(type, id: exampleMessageTypeID)
    return fory
}

private func assertExampleVariantDecode<T: Serializer & Equatable>(
    _ type: T.Type,
    payload: Data,
    expected: T,
    file: StaticString = #filePath,
    line: UInt = #line
) throws {
    let fory = try buildExampleSchemaEvolutionFory(type)
    let actual: T = try fory.deserialize(payload)
    XCTAssertEqual(actual, expected, file: file, line: line)
}

private func assertExampleEmptyDecode(payload: Data) throws {
    let fory = try buildExampleSchemaEvolutionFory(ExampleMessageEmpty.self)
    let _: ExampleMessageEmpty = try fory.deserialize(payload)
}

func assertExampleMessageSchemaEvolution(
    payload: Data,
    expected: Example.ExampleMessage
) throws {
    try assertExampleEmptyDecode(payload: payload)
    for spec in makeExampleSchemaEvolutionVariantSpecs() {
        try spec.assertDecode(payload, expected)
    }
}

@ForyObject
private struct ExampleMessageEmpty: Equatable {}

@ForyObject
private struct ExampleFieldBoolValue: Equatable {
    @ForyField(id: 1)
    var boolValue: Bool = false
}

@ForyObject
private struct ExampleFieldInt8Value: Equatable {
    @ForyField(id: 2)
    var int8Value: Int8 = 0
}

@ForyObject
private struct ExampleFieldInt16Value: Equatable {
    @ForyField(id: 3)
    var int16Value: Int16 = 0
}

@ForyObject
private struct ExampleFieldFixedInt32Value: Equatable {
    @ForyField(id: 4, encoding: .fixed)
    var fixedInt32Value: Int32 = 0
}

@ForyObject
private struct ExampleFieldVarint32Value: Equatable {
    @ForyField(id: 5)
    var varint32Value: Int32 = 0
}

@ForyObject
private struct ExampleFieldFixedInt64Value: Equatable {
    @ForyField(id: 6, encoding: .fixed)
    var fixedInt64Value: Int64 = 0
}

@ForyObject
private struct ExampleFieldVarint64Value: Equatable {
    @ForyField(id: 7)
    var varint64Value: Int64 = 0
}

@ForyObject
private struct ExampleFieldTaggedInt64Value: Equatable {
    @ForyField(id: 8, encoding: .tagged)
    var taggedInt64Value: Int64 = 0
}

@ForyObject
private struct ExampleFieldUint8Value: Equatable {
    @ForyField(id: 9)
    var uint8Value: UInt8 = 0
}

@ForyObject
private struct ExampleFieldUint16Value: Equatable {
    @ForyField(id: 10)
    var uint16Value: UInt16 = 0
}

@ForyObject
private struct ExampleFieldFixedUint32Value: Equatable {
    @ForyField(id: 11, encoding: .fixed)
    var fixedUint32Value: UInt32 = 0
}

@ForyObject
private struct ExampleFieldVarUint32Value: Equatable {
    @ForyField(id: 12)
    var varUint32Value: UInt32 = 0
}

@ForyObject
private struct ExampleFieldFixedUint64Value: Equatable {
    @ForyField(id: 13, encoding: .fixed)
    var fixedUint64Value: UInt64 = 0
}

@ForyObject
private struct ExampleFieldVarUint64Value: Equatable {
    @ForyField(id: 14)
    var varUint64Value: UInt64 = 0
}

@ForyObject
private struct ExampleFieldTaggedUint64Value: Equatable {
    @ForyField(id: 15, encoding: .tagged)
    var taggedUint64Value: UInt64 = 0
}

@ForyObject
private struct ExampleFieldFloat16Value: Equatable {
    @ForyField(id: 16)
    var float16Value: Float16 = Float16.foryDefault()
}

@ForyObject
private struct ExampleFieldBfloat16Value: Equatable {
    @ForyField(id: 17)
    var bfloat16Value: BFloat16 = BFloat16.foryDefault()
}

@ForyObject
private struct ExampleFieldFloat32Value: Equatable {
    @ForyField(id: 18)
    var float32Value: Float = 0
}

@ForyObject
private struct ExampleFieldFloat64Value: Equatable {
    @ForyField(id: 19)
    var float64Value: Double = 0
}

@ForyObject
private struct ExampleFieldStringValue: Equatable {
    @ForyField(id: 20)
    var stringValue: String = ""
}

@ForyObject
private struct ExampleFieldBytesValue: Equatable {
    @ForyField(id: 21)
    var bytesValue: Data = Data()
}

@ForyObject
private struct ExampleFieldDateValue: Equatable {
    @ForyField(id: 22)
    var dateValue: LocalDate = LocalDate.foryDefault()
}

@ForyObject
private struct ExampleFieldTimestampValue: Equatable {
    @ForyField(id: 23)
    var timestampValue: Timestamp = Timestamp.foryDefault()
}

@ForyObject
private struct ExampleFieldDurationValue: Equatable {
    @ForyField(id: 24)
    var durationValue: Duration = Duration.foryDefault()
}

@ForyObject
private struct ExampleFieldDecimalValue: Equatable {
    @ForyField(id: 25)
    var decimalValue: Decimal = Decimal.foryDefault()
}

@ForyObject
private struct ExampleFieldEnumValue: Equatable {
    @ForyField(id: 26)
    var enumValue: ExampleCommon.ExampleState = ExampleCommon.ExampleState.foryDefault()
}

@ForyObject
private struct ExampleFieldMessageValue: Equatable {
    @ForyField(id: 27)
    var messageValue: ExampleCommon.ExampleLeaf? = nil
}

@ForyObject
private struct ExampleFieldUnionValue: Equatable {
    @ForyField(id: 28)
    var unionValue: ExampleCommon.ExampleLeafUnion = ExampleCommon.ExampleLeafUnion.foryDefault()
}

@ForyObject
private struct ExampleFieldBoolList: Equatable {
    @ForyField(id: 101)
    var boolList: [Bool] = []
}

@ForyObject
private struct ExampleFieldInt8List: Equatable {
    @ForyField(id: 102)
    var int8List: [Int8] = []
}

@ForyObject
private struct ExampleFieldInt16List: Equatable {
    @ForyField(id: 103)
    var int16List: [Int16] = []
}

@ForyObject
private struct ExampleFieldFixedInt32List: Equatable {
    @ForyField(id: 104)
    var fixedInt32List: [Int32] = []
}

@ForyObject
private struct ExampleFieldVarint32List: Equatable {
    @ForyField(id: 105)
    var varint32List: [Int32] = []
}

@ForyObject
private struct ExampleFieldFixedInt64List: Equatable {
    @ForyField(id: 106)
    var fixedInt64List: [Int64] = []
}

@ForyObject
private struct ExampleFieldVarint64List: Equatable {
    @ForyField(id: 107)
    var varint64List: [Int64] = []
}

@ForyObject
private struct ExampleFieldTaggedInt64List: Equatable {
    @ForyField(id: 108)
    var taggedInt64List: [Int64] = []
}

@ForyObject
private struct ExampleFieldUint8List: Equatable {
    @ForyField(id: 109)
    var uint8List: [UInt8] = []
}

@ForyObject
private struct ExampleFieldUint16List: Equatable {
    @ForyField(id: 110)
    var uint16List: [UInt16] = []
}

@ForyObject
private struct ExampleFieldFixedUint32List: Equatable {
    @ForyField(id: 111)
    var fixedUint32List: [UInt32] = []
}

@ForyObject
private struct ExampleFieldVarUint32List: Equatable {
    @ForyField(id: 112)
    var varUint32List: [UInt32] = []
}

@ForyObject
private struct ExampleFieldFixedUint64List: Equatable {
    @ForyField(id: 113)
    var fixedUint64List: [UInt64] = []
}

@ForyObject
private struct ExampleFieldVarUint64List: Equatable {
    @ForyField(id: 114)
    var varUint64List: [UInt64] = []
}

@ForyObject
private struct ExampleFieldTaggedUint64List: Equatable {
    @ForyField(id: 115)
    var taggedUint64List: [UInt64] = []
}

@ForyObject
private struct ExampleFieldFloat16List: Equatable {
    @ForyField(id: 116)
    var float16List: [Float16] = []
}

@ForyObject
private struct ExampleFieldBfloat16List: Equatable {
    @ForyField(id: 117)
    var bfloat16List: [BFloat16] = []
}

@ForyObject
private struct ExampleFieldMaybeFloat16List: Equatable {
    @ForyField(id: 118)
    var maybeFloat16List: [Float16?] = []
}

@ForyObject
private struct ExampleFieldMaybeBfloat16List: Equatable {
    @ForyField(id: 119)
    var maybeBfloat16List: [BFloat16?] = []
}

@ForyObject
private struct ExampleFieldFloat32List: Equatable {
    @ForyField(id: 120)
    var float32List: [Float] = []
}

@ForyObject
private struct ExampleFieldFloat64List: Equatable {
    @ForyField(id: 121)
    var float64List: [Double] = []
}

@ForyObject
private struct ExampleFieldStringList: Equatable {
    @ForyField(id: 122)
    var stringList: [String] = []
}

@ForyObject
private struct ExampleFieldBytesList: Equatable {
    @ForyField(id: 123)
    var bytesList: [Data] = []
}

@ForyObject
private struct ExampleFieldDateList: Equatable {
    @ForyField(id: 124)
    var dateList: [LocalDate] = []
}

@ForyObject
private struct ExampleFieldTimestampList: Equatable {
    @ForyField(id: 125)
    var timestampList: [Timestamp] = []
}

@ForyObject
private struct ExampleFieldDurationList: Equatable {
    @ForyField(id: 126)
    var durationList: [Duration] = []
}

@ForyObject
private struct ExampleFieldDecimalList: Equatable {
    @ForyField(id: 127)
    var decimalList: [Decimal] = []
}

@ForyObject
private struct ExampleFieldEnumList: Equatable {
    @ForyField(id: 128)
    var enumList: [ExampleCommon.ExampleState] = []
}

@ForyObject
private struct ExampleFieldMessageList: Equatable {
    @ForyField(id: 129)
    var messageList: [ExampleCommon.ExampleLeaf] = []
}

@ForyObject
private struct ExampleFieldUnionList: Equatable {
    @ForyField(id: 130)
    var unionList: [ExampleCommon.ExampleLeafUnion] = []
}

@ForyObject
private struct ExampleFieldStringValuesByBool: Equatable {
    @ForyField(id: 201)
    var stringValuesByBool: [Bool: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByInt8: Equatable {
    @ForyField(id: 202)
    var stringValuesByInt8: [Int8: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByInt16: Equatable {
    @ForyField(id: 203)
    var stringValuesByInt16: [Int16: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByFixedInt32: Equatable {
    @ForyField(id: 204)
    var stringValuesByFixedInt32: [ForyInt32Fixed: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByVarint32: Equatable {
    @ForyField(id: 205)
    var stringValuesByVarint32: [Int32: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByFixedInt64: Equatable {
    @ForyField(id: 206)
    var stringValuesByFixedInt64: [ForyInt64Fixed: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByVarint64: Equatable {
    @ForyField(id: 207)
    var stringValuesByVarint64: [Int64: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByTaggedInt64: Equatable {
    @ForyField(id: 208)
    var stringValuesByTaggedInt64: [ForyInt64Tagged: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByUint8: Equatable {
    @ForyField(id: 209)
    var stringValuesByUint8: [UInt8: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByUint16: Equatable {
    @ForyField(id: 210)
    var stringValuesByUint16: [UInt16: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByFixedUint32: Equatable {
    @ForyField(id: 211)
    var stringValuesByFixedUint32: [ForyUInt32Fixed: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByVarUint32: Equatable {
    @ForyField(id: 212)
    var stringValuesByVarUint32: [UInt32: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByFixedUint64: Equatable {
    @ForyField(id: 213)
    var stringValuesByFixedUint64: [ForyUInt64Fixed: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByVarUint64: Equatable {
    @ForyField(id: 214)
    var stringValuesByVarUint64: [UInt64: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByTaggedUint64: Equatable {
    @ForyField(id: 215)
    var stringValuesByTaggedUint64: [ForyUInt64Tagged: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByString: Equatable {
    @ForyField(id: 218)
    var stringValuesByString: [String: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByTimestamp: Equatable {
    @ForyField(id: 219)
    var stringValuesByTimestamp: [Timestamp: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByDuration: Equatable {
    @ForyField(id: 220)
    var stringValuesByDuration: [Duration: String] = [:]
}

@ForyObject
private struct ExampleFieldStringValuesByEnum: Equatable {
    @ForyField(id: 221)
    var stringValuesByEnum: [ExampleCommon.ExampleState: String] = [:]
}

@ForyObject
private struct ExampleFieldFloat16ValuesByName: Equatable {
    @ForyField(id: 222)
    var float16ValuesByName: [String: Float16] = [:]
}

@ForyObject
private struct ExampleFieldMaybeFloat16ValuesByName: Equatable {
    @ForyField(id: 223)
    var maybeFloat16ValuesByName: [String: Float16?] = [:]
}

@ForyObject
private struct ExampleFieldBfloat16ValuesByName: Equatable {
    @ForyField(id: 224)
    var bfloat16ValuesByName: [String: BFloat16] = [:]
}

@ForyObject
private struct ExampleFieldMaybeBfloat16ValuesByName: Equatable {
    @ForyField(id: 225)
    var maybeBfloat16ValuesByName: [String: BFloat16?] = [:]
}

@ForyObject
private struct ExampleFieldBytesValuesByName: Equatable {
    @ForyField(id: 226)
    var bytesValuesByName: [String: Data] = [:]
}

@ForyObject
private struct ExampleFieldDateValuesByName: Equatable {
    @ForyField(id: 227)
    var dateValuesByName: [String: LocalDate] = [:]
}

@ForyObject
private struct ExampleFieldDecimalValuesByName: Equatable {
    @ForyField(id: 228)
    var decimalValuesByName: [String: Decimal] = [:]
}

@ForyObject
private struct ExampleFieldMessageValuesByName: Equatable {
    @ForyField(id: 229)
    var messageValuesByName: [String: ExampleCommon.ExampleLeaf] = [:]
}

@ForyObject
private struct ExampleFieldUnionValuesByName: Equatable {
    @ForyField(id: 230)
    var unionValuesByName: [String: ExampleCommon.ExampleLeafUnion] = [:]
}

private func makeExampleSchemaEvolutionVariantSpecs() -> [ExampleSchemaEvolutionVariantSpec] {
    [
    .init(
        fieldName: "boolValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBoolValue.self,
                payload: payload,
                expected: ExampleFieldBoolValue(boolValue: expected.boolValue)
            )
        }
    ),
    .init(
        fieldName: "int8Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldInt8Value.self,
                payload: payload,
                expected: ExampleFieldInt8Value(int8Value: expected.int8Value)
            )
        }
    ),
    .init(
        fieldName: "int16Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldInt16Value.self,
                payload: payload,
                expected: ExampleFieldInt16Value(int16Value: expected.int16Value)
            )
        }
    ),
    .init(
        fieldName: "fixedInt32Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedInt32Value.self,
                payload: payload,
                expected: ExampleFieldFixedInt32Value(fixedInt32Value: expected.fixedInt32Value)
            )
        }
    ),
    .init(
        fieldName: "varint32Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarint32Value.self,
                payload: payload,
                expected: ExampleFieldVarint32Value(varint32Value: expected.varint32Value)
            )
        }
    ),
    .init(
        fieldName: "fixedInt64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedInt64Value.self,
                payload: payload,
                expected: ExampleFieldFixedInt64Value(fixedInt64Value: expected.fixedInt64Value)
            )
        }
    ),
    .init(
        fieldName: "varint64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarint64Value.self,
                payload: payload,
                expected: ExampleFieldVarint64Value(varint64Value: expected.varint64Value)
            )
        }
    ),
    .init(
        fieldName: "taggedInt64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTaggedInt64Value.self,
                payload: payload,
                expected: ExampleFieldTaggedInt64Value(taggedInt64Value: expected.taggedInt64Value)
            )
        }
    ),
    .init(
        fieldName: "uint8Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUint8Value.self,
                payload: payload,
                expected: ExampleFieldUint8Value(uint8Value: expected.uint8Value)
            )
        }
    ),
    .init(
        fieldName: "uint16Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUint16Value.self,
                payload: payload,
                expected: ExampleFieldUint16Value(uint16Value: expected.uint16Value)
            )
        }
    ),
    .init(
        fieldName: "fixedUint32Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedUint32Value.self,
                payload: payload,
                expected: ExampleFieldFixedUint32Value(fixedUint32Value: expected.fixedUint32Value)
            )
        }
    ),
    .init(
        fieldName: "varUint32Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarUint32Value.self,
                payload: payload,
                expected: ExampleFieldVarUint32Value(varUint32Value: expected.varUint32Value)
            )
        }
    ),
    .init(
        fieldName: "fixedUint64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedUint64Value.self,
                payload: payload,
                expected: ExampleFieldFixedUint64Value(fixedUint64Value: expected.fixedUint64Value)
            )
        }
    ),
    .init(
        fieldName: "varUint64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarUint64Value.self,
                payload: payload,
                expected: ExampleFieldVarUint64Value(varUint64Value: expected.varUint64Value)
            )
        }
    ),
    .init(
        fieldName: "taggedUint64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTaggedUint64Value.self,
                payload: payload,
                expected: ExampleFieldTaggedUint64Value(taggedUint64Value: expected.taggedUint64Value)
            )
        }
    ),
    .init(
        fieldName: "float16Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat16Value.self,
                payload: payload,
                expected: ExampleFieldFloat16Value(float16Value: expected.float16Value)
            )
        }
    ),
    .init(
        fieldName: "bfloat16Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBfloat16Value.self,
                payload: payload,
                expected: ExampleFieldBfloat16Value(bfloat16Value: expected.bfloat16Value)
            )
        }
    ),
    .init(
        fieldName: "float32Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat32Value.self,
                payload: payload,
                expected: ExampleFieldFloat32Value(float32Value: expected.float32Value)
            )
        }
    ),
    .init(
        fieldName: "float64Value",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat64Value.self,
                payload: payload,
                expected: ExampleFieldFloat64Value(float64Value: expected.float64Value)
            )
        }
    ),
    .init(
        fieldName: "stringValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValue.self,
                payload: payload,
                expected: ExampleFieldStringValue(stringValue: expected.stringValue)
            )
        }
    ),
    .init(
        fieldName: "bytesValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBytesValue.self,
                payload: payload,
                expected: ExampleFieldBytesValue(bytesValue: expected.bytesValue)
            )
        }
    ),
    .init(
        fieldName: "dateValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDateValue.self,
                payload: payload,
                expected: ExampleFieldDateValue(dateValue: expected.dateValue)
            )
        }
    ),
    .init(
        fieldName: "timestampValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTimestampValue.self,
                payload: payload,
                expected: ExampleFieldTimestampValue(timestampValue: expected.timestampValue)
            )
        }
    ),
    .init(
        fieldName: "durationValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDurationValue.self,
                payload: payload,
                expected: ExampleFieldDurationValue(durationValue: expected.durationValue)
            )
        }
    ),
    .init(
        fieldName: "decimalValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDecimalValue.self,
                payload: payload,
                expected: ExampleFieldDecimalValue(decimalValue: expected.decimalValue)
            )
        }
    ),
    .init(
        fieldName: "enumValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldEnumValue.self,
                payload: payload,
                expected: ExampleFieldEnumValue(enumValue: expected.enumValue)
            )
        }
    ),
    .init(
        fieldName: "messageValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMessageValue.self,
                payload: payload,
                expected: ExampleFieldMessageValue(messageValue: expected.messageValue)
            )
        }
    ),
    .init(
        fieldName: "unionValue",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUnionValue.self,
                payload: payload,
                expected: ExampleFieldUnionValue(unionValue: expected.unionValue)
            )
        }
    ),
    .init(
        fieldName: "boolList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBoolList.self,
                payload: payload,
                expected: ExampleFieldBoolList(boolList: expected.boolList)
            )
        }
    ),
    .init(
        fieldName: "int8List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldInt8List.self,
                payload: payload,
                expected: ExampleFieldInt8List(int8List: expected.int8List)
            )
        }
    ),
    .init(
        fieldName: "int16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldInt16List.self,
                payload: payload,
                expected: ExampleFieldInt16List(int16List: expected.int16List)
            )
        }
    ),
    .init(
        fieldName: "fixedInt32List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedInt32List.self,
                payload: payload,
                expected: ExampleFieldFixedInt32List(fixedInt32List: expected.fixedInt32List)
            )
        }
    ),
    .init(
        fieldName: "varint32List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarint32List.self,
                payload: payload,
                expected: ExampleFieldVarint32List(varint32List: expected.varint32List)
            )
        }
    ),
    .init(
        fieldName: "fixedInt64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedInt64List.self,
                payload: payload,
                expected: ExampleFieldFixedInt64List(fixedInt64List: expected.fixedInt64List)
            )
        }
    ),
    .init(
        fieldName: "varint64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarint64List.self,
                payload: payload,
                expected: ExampleFieldVarint64List(varint64List: expected.varint64List)
            )
        }
    ),
    .init(
        fieldName: "taggedInt64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTaggedInt64List.self,
                payload: payload,
                expected: ExampleFieldTaggedInt64List(taggedInt64List: expected.taggedInt64List)
            )
        }
    ),
    .init(
        fieldName: "uint8List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUint8List.self,
                payload: payload,
                expected: ExampleFieldUint8List(uint8List: expected.uint8List)
            )
        }
    ),
    .init(
        fieldName: "uint16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUint16List.self,
                payload: payload,
                expected: ExampleFieldUint16List(uint16List: expected.uint16List)
            )
        }
    ),
    .init(
        fieldName: "fixedUint32List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedUint32List.self,
                payload: payload,
                expected: ExampleFieldFixedUint32List(fixedUint32List: expected.fixedUint32List)
            )
        }
    ),
    .init(
        fieldName: "varUint32List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarUint32List.self,
                payload: payload,
                expected: ExampleFieldVarUint32List(varUint32List: expected.varUint32List)
            )
        }
    ),
    .init(
        fieldName: "fixedUint64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFixedUint64List.self,
                payload: payload,
                expected: ExampleFieldFixedUint64List(fixedUint64List: expected.fixedUint64List)
            )
        }
    ),
    .init(
        fieldName: "varUint64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldVarUint64List.self,
                payload: payload,
                expected: ExampleFieldVarUint64List(varUint64List: expected.varUint64List)
            )
        }
    ),
    .init(
        fieldName: "taggedUint64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTaggedUint64List.self,
                payload: payload,
                expected: ExampleFieldTaggedUint64List(taggedUint64List: expected.taggedUint64List)
            )
        }
    ),
    .init(
        fieldName: "float16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat16List.self,
                payload: payload,
                expected: ExampleFieldFloat16List(float16List: expected.float16List)
            )
        }
    ),
    .init(
        fieldName: "bfloat16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBfloat16List.self,
                payload: payload,
                expected: ExampleFieldBfloat16List(bfloat16List: expected.bfloat16List)
            )
        }
    ),
    .init(
        fieldName: "maybeFloat16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMaybeFloat16List.self,
                payload: payload,
                expected: ExampleFieldMaybeFloat16List(maybeFloat16List: expected.maybeFloat16List)
            )
        }
    ),
    .init(
        fieldName: "maybeBfloat16List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMaybeBfloat16List.self,
                payload: payload,
                expected: ExampleFieldMaybeBfloat16List(maybeBfloat16List: expected.maybeBfloat16List)
            )
        }
    ),
    .init(
        fieldName: "float32List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat32List.self,
                payload: payload,
                expected: ExampleFieldFloat32List(float32List: expected.float32List)
            )
        }
    ),
    .init(
        fieldName: "float64List",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat64List.self,
                payload: payload,
                expected: ExampleFieldFloat64List(float64List: expected.float64List)
            )
        }
    ),
    .init(
        fieldName: "stringList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringList.self,
                payload: payload,
                expected: ExampleFieldStringList(stringList: expected.stringList)
            )
        }
    ),
    .init(
        fieldName: "bytesList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBytesList.self,
                payload: payload,
                expected: ExampleFieldBytesList(bytesList: expected.bytesList)
            )
        }
    ),
    .init(
        fieldName: "dateList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDateList.self,
                payload: payload,
                expected: ExampleFieldDateList(dateList: expected.dateList)
            )
        }
    ),
    .init(
        fieldName: "timestampList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldTimestampList.self,
                payload: payload,
                expected: ExampleFieldTimestampList(timestampList: expected.timestampList)
            )
        }
    ),
    .init(
        fieldName: "durationList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDurationList.self,
                payload: payload,
                expected: ExampleFieldDurationList(durationList: expected.durationList)
            )
        }
    ),
    .init(
        fieldName: "decimalList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDecimalList.self,
                payload: payload,
                expected: ExampleFieldDecimalList(decimalList: expected.decimalList)
            )
        }
    ),
    .init(
        fieldName: "enumList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldEnumList.self,
                payload: payload,
                expected: ExampleFieldEnumList(enumList: expected.enumList)
            )
        }
    ),
    .init(
        fieldName: "messageList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMessageList.self,
                payload: payload,
                expected: ExampleFieldMessageList(messageList: expected.messageList)
            )
        }
    ),
    .init(
        fieldName: "unionList",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUnionList.self,
                payload: payload,
                expected: ExampleFieldUnionList(unionList: expected.unionList)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByBool",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByBool.self,
                payload: payload,
                expected: ExampleFieldStringValuesByBool(stringValuesByBool: expected.stringValuesByBool)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByInt8",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByInt8.self,
                payload: payload,
                expected: ExampleFieldStringValuesByInt8(stringValuesByInt8: expected.stringValuesByInt8)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByInt16",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByInt16.self,
                payload: payload,
                expected: ExampleFieldStringValuesByInt16(stringValuesByInt16: expected.stringValuesByInt16)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByFixedInt32",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByFixedInt32.self,
                payload: payload,
                expected: ExampleFieldStringValuesByFixedInt32(stringValuesByFixedInt32: expected.stringValuesByFixedInt32)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByVarint32",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByVarint32.self,
                payload: payload,
                expected: ExampleFieldStringValuesByVarint32(stringValuesByVarint32: expected.stringValuesByVarint32)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByFixedInt64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByFixedInt64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByFixedInt64(stringValuesByFixedInt64: expected.stringValuesByFixedInt64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByVarint64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByVarint64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByVarint64(stringValuesByVarint64: expected.stringValuesByVarint64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByTaggedInt64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByTaggedInt64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByTaggedInt64(stringValuesByTaggedInt64: expected.stringValuesByTaggedInt64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByUint8",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByUint8.self,
                payload: payload,
                expected: ExampleFieldStringValuesByUint8(stringValuesByUint8: expected.stringValuesByUint8)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByUint16",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByUint16.self,
                payload: payload,
                expected: ExampleFieldStringValuesByUint16(stringValuesByUint16: expected.stringValuesByUint16)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByFixedUint32",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByFixedUint32.self,
                payload: payload,
                expected: ExampleFieldStringValuesByFixedUint32(stringValuesByFixedUint32: expected.stringValuesByFixedUint32)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByVarUint32",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByVarUint32.self,
                payload: payload,
                expected: ExampleFieldStringValuesByVarUint32(stringValuesByVarUint32: expected.stringValuesByVarUint32)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByFixedUint64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByFixedUint64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByFixedUint64(stringValuesByFixedUint64: expected.stringValuesByFixedUint64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByVarUint64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByVarUint64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByVarUint64(stringValuesByVarUint64: expected.stringValuesByVarUint64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByTaggedUint64",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByTaggedUint64.self,
                payload: payload,
                expected: ExampleFieldStringValuesByTaggedUint64(stringValuesByTaggedUint64: expected.stringValuesByTaggedUint64)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByString",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByString.self,
                payload: payload,
                expected: ExampleFieldStringValuesByString(stringValuesByString: expected.stringValuesByString)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByTimestamp",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByTimestamp.self,
                payload: payload,
                expected: ExampleFieldStringValuesByTimestamp(stringValuesByTimestamp: expected.stringValuesByTimestamp)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByDuration",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByDuration.self,
                payload: payload,
                expected: ExampleFieldStringValuesByDuration(stringValuesByDuration: expected.stringValuesByDuration)
            )
        }
    ),
    .init(
        fieldName: "stringValuesByEnum",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldStringValuesByEnum.self,
                payload: payload,
                expected: ExampleFieldStringValuesByEnum(stringValuesByEnum: expected.stringValuesByEnum)
            )
        }
    ),
    .init(
        fieldName: "float16ValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldFloat16ValuesByName.self,
                payload: payload,
                expected: ExampleFieldFloat16ValuesByName(float16ValuesByName: expected.float16ValuesByName)
            )
        }
    ),
    .init(
        fieldName: "maybeFloat16ValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMaybeFloat16ValuesByName.self,
                payload: payload,
                expected: ExampleFieldMaybeFloat16ValuesByName(maybeFloat16ValuesByName: expected.maybeFloat16ValuesByName)
            )
        }
    ),
    .init(
        fieldName: "bfloat16ValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBfloat16ValuesByName.self,
                payload: payload,
                expected: ExampleFieldBfloat16ValuesByName(bfloat16ValuesByName: expected.bfloat16ValuesByName)
            )
        }
    ),
    .init(
        fieldName: "maybeBfloat16ValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMaybeBfloat16ValuesByName.self,
                payload: payload,
                expected: ExampleFieldMaybeBfloat16ValuesByName(maybeBfloat16ValuesByName: expected.maybeBfloat16ValuesByName)
            )
        }
    ),
    .init(
        fieldName: "bytesValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldBytesValuesByName.self,
                payload: payload,
                expected: ExampleFieldBytesValuesByName(bytesValuesByName: expected.bytesValuesByName)
            )
        }
    ),
    .init(
        fieldName: "dateValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDateValuesByName.self,
                payload: payload,
                expected: ExampleFieldDateValuesByName(dateValuesByName: expected.dateValuesByName)
            )
        }
    ),
    .init(
        fieldName: "decimalValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldDecimalValuesByName.self,
                payload: payload,
                expected: ExampleFieldDecimalValuesByName(decimalValuesByName: expected.decimalValuesByName)
            )
        }
    ),
    .init(
        fieldName: "messageValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldMessageValuesByName.self,
                payload: payload,
                expected: ExampleFieldMessageValuesByName(messageValuesByName: expected.messageValuesByName)
            )
        }
    ),
    .init(
        fieldName: "unionValuesByName",
        assertDecode: { payload, expected in
            try assertExampleVariantDecode(
                ExampleFieldUnionValuesByName.self,
                payload: payload,
                expected: ExampleFieldUnionValuesByName(unionValuesByName: expected.unionValuesByName)
            )
        }
    ),
    ]
}
