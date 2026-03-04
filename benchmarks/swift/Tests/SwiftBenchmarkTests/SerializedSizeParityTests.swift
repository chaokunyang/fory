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

import Fory
import XCTest
@testable import SwiftBenchmark

final class SerializedSizeParityTests: XCTestCase {
    private var fory: Fory!

    override func setUp() {
        super.setUp()
        fory = Fory(xlang: false, trackRef: false, compatible: true)
        registerBenchmarkTypes()
    }

    func testForyAndProtobufSizesMatchCppBenchmarkTargets() throws {
        try assertSerializedSize(
            name: "Struct",
            value: BenchmarkDataFactory.makeNumericStruct(),
            expectedForyBytes: 58,
            expectedProtobufBytes: 61
        )
        try assertSerializedSize(
            name: "Sample",
            value: BenchmarkDataFactory.makeSample(),
            expectedForyBytes: 446,
            expectedProtobufBytes: 375
        )
        try assertSerializedSize(
            name: "MediaContent",
            value: BenchmarkDataFactory.makeMediaContent(),
            expectedForyBytes: 365,
            expectedProtobufBytes: 301
        )
        try assertSerializedSize(
            name: "StructList",
            value: BenchmarkDataFactory.makeStructList(),
            expectedForyBytes: 184,
            expectedProtobufBytes: 315
        )
        try assertSerializedSize(
            name: "SampleList",
            value: BenchmarkDataFactory.makeSampleList(),
            expectedForyBytes: 1980,
            expectedProtobufBytes: 1890
        )
        try assertSerializedSize(
            name: "MediaContentList",
            value: BenchmarkDataFactory.makeMediaContentList(),
            expectedForyBytes: 1535,
            expectedProtobufBytes: 1520
        )
    }

    private func registerBenchmarkTypes() {
        fory.register(NumericStruct.self, id: 1)
        fory.register(Sample.self, id: 2)
        fory.register(Media.self, id: 3)
        fory.register(Image.self, id: 4)
        fory.register(MediaContent.self, id: 5)
        fory.register(StructList.self, id: 6)
        fory.register(SampleList.self, id: 7)
        fory.register(MediaContentList.self, id: 8)
    }

    private func assertSerializedSize<T: Serializer & Codable & ProtobufConvertible>(
        name: String,
        value: T,
        expectedForyBytes: Int,
        expectedProtobufBytes: Int
    ) throws {
        let foryBytes = try fory.serialize(value)
        XCTAssertEqual(
            foryBytes.count,
            expectedForyBytes,
            "\(name) Fory size mismatch"
        )
        let protobufBytes = try value.toProtobuf().serializedData()
        XCTAssertEqual(
            protobufBytes.count,
            expectedProtobufBytes,
            "\(name) Protobuf size mismatch"
        )
    }
}
