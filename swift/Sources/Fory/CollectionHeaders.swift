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

enum CollectionHeader {
    static let trackingRef: UInt8 = 0b0000_0001
    static let hasNull: UInt8 = 0b0000_0010
    static let declaredElementType: UInt8 = 0b0000_0100
    static let sameType: UInt8 = 0b0000_1000
}

enum MapHeader {
    static let trackingKeyRef: UInt8 = 0b0000_0001
    static let keyNull: UInt8 = 0b0000_0010
    static let declaredKeyType: UInt8 = 0b0000_0100

    static let trackingValueRef: UInt8 = 0b0000_1000
    static let valueNull: UInt8 = 0b0001_0000
    static let declaredValueType: UInt8 = 0b0010_0000
}
