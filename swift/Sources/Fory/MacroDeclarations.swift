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

@attached(
    member,
    names: arbitrary
)
@attached(extension, conformances: Serializer, StructSerializer)
public macro ForyObject(evolving: Bool = true) = #externalMacro(module: "ForyMacro", type: "ForyObjectMacro")

public enum ForyFieldEncoding: String {
    case varint
    case fixed
    case tagged
}

public struct ForyFieldType {
    public init() {}

    public static var bool: Self { .init() }
    public static var int8: Self { .init() }
    public static var int16: Self { .init() }
    public static func int32(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static func int64(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static func int(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static var uint8: Self { .init() }
    public static var uint16: Self { .init() }
    public static func uint32(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static func uint64(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static func uint(nullable: Bool = false, encoding: ForyFieldEncoding = .varint) -> Self { .init() }
    public static var float16: Self { .init() }
    public static var bfloat16: Self { .init() }
    public static var float: Self { .init() }
    public static var double: Self { .init() }
    public static var string: Self { .init() }
    public static var data: Self { .init() }
    public static var duration: Self { .init() }
    public static var date: Self { .init() }
    public static var localDate: Self { .init() }
    public static var decimal: Self { .init() }

    public static func list(_ element: Self, nullable: Bool = false) -> Self { .init() }
    public static func list(element: Self, nullable: Bool = false) -> Self { .init() }
    public static func set(_ element: Self, nullable: Bool = false) -> Self { .init() }
    public static func set(element: Self, nullable: Bool = false) -> Self { .init() }
    public static func map(key: Self, value: Self, nullable: Bool = false) -> Self { .init() }

    public static func value<T: Serializer>(_ type: T.Type, nullable: Bool = false) -> Self { .init() }
}

@attached(peer)
public macro ForyField(
    id: Int? = nil,
    encoding: ForyFieldEncoding? = nil,
    type: ForyFieldType? = nil
) = #externalMacro(module: "ForyMacro", type: "ForyFieldMacro")
