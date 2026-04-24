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

private let secondsPerDay = 86_400.0

@ForyObject
private struct TimestampMacroHolder {
    var day: LocalDate = .foryDefault()

    var instant: Timestamp = .foryDefault()
    var timestamp: Timestamp = .foryDefault()
}

@ForyObject
private struct DurationMacroHolder: Equatable {
    var label: String = ""
    var elapsed: Duration = .zero
    var maybeElapsed: Duration?
    var qualifiedElapsed: Swift.Duration = .zero
    var day: LocalDate = .foryDefault()
}

private func midnightUTC(daysSinceEpoch: Int32) -> Date {
    Date(timeIntervalSince1970: Double(daysSinceEpoch) * secondsPerDay)
}

private func localDate(_ daysSinceEpoch: Int32) -> LocalDate {
    .init(daysSinceEpoch: daysSinceEpoch)
}

private func encodedDurationComponents(_ duration: Duration) throws -> (seconds: Int64, nanos: Int32) {
    let buffer = ByteBuffer()
    let context = WriteContext(
        buffer: buffer,
        typeResolver: TypeResolver(trackRef: false),
        xlang: true,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxDepth: 5
    )
    try duration.foryWriteData(context, hasGenerics: false)

    let readBuffer = ByteBuffer(data: buffer.copyToData())
    return (try readBuffer.readVarInt64(), try readBuffer.readInt32())
}

@Test
func dateAndTimestampTypeIds() {
    #expect(Duration.staticTypeId == .duration)
    #expect(LocalDate.staticTypeId == .date)
    #expect(Timestamp.staticTypeId == .timestamp)
    #expect(Date.staticTypeId == .timestamp)
}

@Test
func durationWritesCanonicalXlangComponents() throws {
    let negativeSubsecond = try encodedDurationComponents(.nanoseconds(-500_000_000))
    #expect(negativeSubsecond.seconds == -1)
    #expect(negativeSubsecond.nanos == 500_000_000)

    let negativeWithPositiveAdjustment = try encodedDurationComponents(
        .seconds(-2) + .nanoseconds(123_456_789)
    )
    #expect(negativeWithPositiveAdjustment.seconds == -2)
    #expect(negativeWithPositiveAdjustment.nanos == 123_456_789)
}

@Test
func dateAndTimestampRoundTrip() throws {
    let fory = Fory(config: .init(xlang: true, trackRef: false, compatible: true))

    let day = localDate(18_745)
    let dayData = try fory.serialize(day)
    let dayDecoded: LocalDate = try fory.deserialize(dayData)
    #expect(dayDecoded == day)

    let duration = Duration.seconds(-7) + Duration.nanoseconds(12_000_000)
    let durationData = try fory.serialize(duration)
    let durationDecoded: Duration = try fory.deserialize(durationData)
    #expect(durationDecoded == duration)

    let instant = Timestamp(seconds: 1_731_234_567, nanos: 123_456_700)
    let instantData = try fory.serialize(instant)
    let instantDecoded: Timestamp = try fory.deserialize(instantData)
    #expect(instantDecoded == instant)

    let bridgedInstant = Date(timeIntervalSince1970: 1_731_234_567.123_456_7)
    let bridgedInstantData = try fory.serialize(bridgedInstant)
    let bridgedInstantDecoded: Date = try fory.deserialize(bridgedInstantData)
    let diff = abs(bridgedInstantDecoded.timeIntervalSince1970 - bridgedInstant.timeIntervalSince1970)
    #expect(diff < 0.000_001)
}

@Test
func localDateConvenienceMethodsExposeEpochAndCalendarViews() throws {
    let beforeEpoch = LocalDate.fromEpochDay(-1)
    let leapDay = LocalDate.fromEpochDay(19_782)
    let epoch = try LocalDate(date: Date(timeIntervalSince1970: 0))

    #expect(beforeEpoch.toEpochDay() == -1)
    #expect(beforeEpoch.year == 1969)
    #expect(beforeEpoch.month == 12)
    #expect(beforeEpoch.day == 31)
    #expect(leapDay.year == 2024)
    #expect(leapDay.month == 2)
    #expect(leapDay.day == 29)
    #expect(epoch == .fromEpochDay(0))
    #expect(beforeEpoch < epoch)
    #expect(abs(epoch.toDate().timeIntervalSince1970) < 0.000_001)
}

@Test
func dateAndTimestampContextHelpersUseExpectedWireProtocols() throws {
    let xlangWriteBuffer = ByteBuffer()
    let xlangTypeResolver = TypeResolver(trackRef: false)
    let xlangWriteContext = WriteContext(
        buffer: xlangWriteBuffer,
        typeResolver: xlangTypeResolver,
        xlang: true,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxDepth: 5
    )

    let xlangLocalDate = localDate(-1)
    try xlangWriteContext.writeLocalDate(xlangLocalDate, refMode: .nullOnly, writeTypeInfo: true)
    #expect(
        Array(xlangWriteBuffer.copyToData()) == [
            UInt8(bitPattern: RefFlag.notNullValue.rawValue),
            UInt8(LocalDate.staticTypeId.rawValue),
            0x01
        ]
    )

    let xlangReadContext = ReadContext(
        buffer: ByteBuffer(data: xlangWriteBuffer.copyToData()),
        typeResolver: xlangTypeResolver,
        xlang: true,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxCollectionSize: 1_000_000,
        maxBinarySize: 64 * 1024 * 1024,
        maxDepth: 5
    )
    let xlangLocalDateDecoded = try xlangReadContext.readLocalDate(refMode: RefMode.nullOnly, readTypeInfo: true)
    #expect(xlangLocalDateDecoded == xlangLocalDate)

    let writeBuffer = ByteBuffer()
    let typeResolver = TypeResolver(trackRef: false)
    let writeContext = WriteContext(
        buffer: writeBuffer,
        typeResolver: typeResolver,
        xlang: false,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxDepth: 5
    )

    let localDate = localDate(-1)

    try writeContext.writeLocalDate(localDate, refMode: .nullOnly, writeTypeInfo: true)

    let readContext = ReadContext(
        buffer: ByteBuffer(data: writeBuffer.copyToData()),
        typeResolver: typeResolver,
        xlang: false,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxCollectionSize: 1_000_000,
        maxBinarySize: 64 * 1024 * 1024,
        maxDepth: 5
    )

    let localDateDecoded = try readContext.readLocalDate(refMode: RefMode.nullOnly, readTypeInfo: true)

    #expect(localDateDecoded == localDate)
    #expect(Array(writeBuffer.copyToData()) == [
        UInt8(bitPattern: RefFlag.notNullValue.rawValue),
        UInt8(LocalDate.staticTypeId.rawValue),
        0xFF,
        0xFF,
        0xFF,
        0xFF
    ])

    let timestampBuffer = ByteBuffer()
    let timestampWriteContext = WriteContext(
        buffer: timestampBuffer,
        typeResolver: typeResolver,
        xlang: false,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxDepth: 5
    )
    let instant = Timestamp(seconds: 123_456, nanos: 1_000)
    try timestampWriteContext.writeTimestamp(instant, refMode: .nullOnly, writeTypeInfo: true)

    let timestampReadContext = ReadContext(
        buffer: ByteBuffer(data: timestampBuffer.copyToData()),
        typeResolver: typeResolver,
        xlang: false,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxCollectionSize: 1_000_000,
        maxBinarySize: 64 * 1024 * 1024,
        maxDepth: 5
    )
    let timestampDecoded = try timestampReadContext.readTimestampValue(
        refMode: RefMode.nullOnly,
        readTypeInfo: true
    )
    #expect(timestampDecoded == instant)
}

@Test
func durationMacroFieldsUseXlangTypeId() throws {
    let fields = DurationMacroHolder.foryFieldsInfo(trackRef: false)
    #expect(fields.map(\.fieldName) == ["label", "elapsed", "maybeElapsed", "qualifiedElapsed", "day"])
    #expect(fields.map(\.fieldType.typeID) == [
        TypeId.string.rawValue,
        TypeId.duration.rawValue,
        TypeId.duration.rawValue,
        TypeId.duration.rawValue,
        TypeId.date.rawValue
    ])
    #expect(!fields[1].fieldType.nullable)
    #expect(fields[2].fieldType.nullable)
    #expect(!fields[3].fieldType.nullable)

    let fory = Fory(config: .init(xlang: true, trackRef: false, compatible: true))
    fory.register(DurationMacroHolder.self, id: 902)

    let value = DurationMacroHolder(
        label: "elapsed",
        elapsed: .nanoseconds(-500_000_000),
        maybeElapsed: .seconds(4) + .nanoseconds(5),
        qualifiedElapsed: .seconds(-2) + .nanoseconds(123_456_789),
        day: localDate(20_001)
    )
    let data = try fory.serialize(value)
    let decoded: DurationMacroHolder = try fory.deserialize(data)

    #expect(decoded == value)
}

@Test
func dateAndTimestampMacroFieldRoundTrip() throws {
    let fory = Fory(config: .init(xlang: true, trackRef: false, compatible: true))
    fory.register(TimestampMacroHolder.self, id: 901)

    let value = TimestampMacroHolder(
        day: localDate(20_001),
        instant: Timestamp(seconds: 123_456, nanos: 1_000),
        timestamp: Timestamp(seconds: 44, nanos: 12_345)
    )

    let data = try fory.serialize(value)
    let decoded: TimestampMacroHolder = try fory.deserialize(data)

    #expect(decoded.day == value.day)
    #expect(decoded.instant == value.instant)
    #expect(decoded.timestamp == value.timestamp)
}
