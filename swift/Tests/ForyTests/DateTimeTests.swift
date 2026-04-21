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
private struct DateMacroHolder {
    var day: LocalDate = .foryDefault()

    var instant: Date = .foryDefault()
    var timestamp: Date = .foryDefault()
}

private func midnightUTC(daysSinceEpoch: Int32) -> Date {
    Date(timeIntervalSince1970: Double(daysSinceEpoch) * secondsPerDay)
}

private func localDate(_ daysSinceEpoch: Int32) -> LocalDate {
    .init(daysSinceEpoch: daysSinceEpoch)
}

@Test
func dateAndTimestampTypeIds() {
    #expect(Duration.staticTypeId == .duration)
    #expect(LocalDate.staticTypeId == .date)
    #expect(Date.staticTypeId == .timestamp)
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

    let instant = Date(timeIntervalSince1970: 1_731_234_567.123_456_7)
    let instantData = try fory.serialize(instant)
    let instantDecoded: Date = try fory.deserialize(instantData)
    let diff = abs(instantDecoded.timeIntervalSince1970 - instant.timeIntervalSince1970)
    #expect(diff < 0.000_001)
}

@Test
func dateAndTimestampContextHelpersUseExpectedWireProtocols() throws {
    let writeBuffer = ByteBuffer()
    let typeResolver = TypeResolver(trackRef: false)
    let writeContext = WriteContext(
        buffer: writeBuffer,
        typeResolver: typeResolver,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxDepth: 5
    )

    let localDate = localDate(20_001)
    let instant = Date(timeIntervalSince1970: 123_456.000_001)

    try writeContext.writeLocalDate(localDate, refMode: .nullOnly, writeTypeInfo: true)
    try writeContext.writeTimestamp(instant, refMode: .nullOnly, writeTypeInfo: true)

    let readContext = ReadContext(
        buffer: ByteBuffer(data: writeBuffer.copyToData()),
        typeResolver: typeResolver,
        trackRef: false,
        compatible: true,
        checkClassVersion: true,
        maxCollectionSize: 1_000_000,
        maxBinarySize: 64 * 1024 * 1024,
        maxDepth: 5
    )

    let localDateDecoded = try readContext.readLocalDate(refMode: RefMode.nullOnly, readTypeInfo: true)
    let timestampDecoded = try readContext.readTimestamp(refMode: RefMode.nullOnly, readTypeInfo: true)

    #expect(localDateDecoded == localDate)
    #expect(abs(timestampDecoded.timeIntervalSince1970 - instant.timeIntervalSince1970) < 0.000_001)
}

@Test
func dateAndTimestampMacroFieldRoundTrip() throws {
    let fory = Fory(config: .init(xlang: true, trackRef: false, compatible: true))
    fory.register(DateMacroHolder.self, id: 901)

    let value = DateMacroHolder(
        day: localDate(20_001),
        instant: Date(timeIntervalSince1970: 123_456.000_001),
        timestamp: Date(timeIntervalSince1970: 44.000_012_345)
    )

    let data = try fory.serialize(value)
    let decoded: DateMacroHolder = try fory.deserialize(data)

    #expect(decoded.day == value.day)
    let instantDiff = abs(decoded.instant.timeIntervalSince1970 - value.instant.timeIntervalSince1970)
    #expect(instantDiff < 0.000_001)
    let timestampDiff = abs(decoded.timestamp.timeIntervalSince1970 - value.timestamp.timeIntervalSince1970)
    #expect(timestampDiff < 0.000_001)
}
