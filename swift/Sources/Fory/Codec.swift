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

@inline(__always)
private func uncheckedArrayCast<From, To>(_ array: [From], to _: To.Type) -> [To] {
    assert(From.self == To.self)
    return unsafeBitCast(array, to: [To].self)
}

@inline(__always)
private func readArrayUninitialized<Element>(
    count: Int,
    _ initializer: (UnsafeMutablePointer<Element>) throws -> Void
) rethrows -> [Element] {
    try [Element](unsafeUninitializedCapacity: count) { destination, initializedCount in
        if count > 0 {
            try initializer(destination.baseAddress!)
        }
        initializedCount = count
    }
}

public protocol Codec {
    associatedtype Value: Serializer

    static var staticTypeId: TypeId { get }
    static var isNullableType: Bool { get }
    static var isRefType: Bool { get }

    static func defaultValue() -> Value
    static func writeData(_ value: Value, _ context: WriteContext) throws
    static func readData(_ context: ReadContext) throws -> Value

    static func write(
        _ value: Value,
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool
    ) throws

    static func read(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> Value

    static func writeStaticTypeInfo(_ context: WriteContext) throws
    static func readTypeInfo(_ context: ReadContext) throws -> TypeInfo?
    static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType
}

public extension Codec {
    static var isNullableType: Bool { false }

    static var isRefType: Bool { false }

    static func writeStaticTypeInfo(_ context: WriteContext) throws {
        context.writeStaticTypeInfo(staticTypeId)
    }

    static func readTypeInfo(_ context: ReadContext) throws -> TypeInfo? {
        try context.readStaticTypeInfo(staticTypeId)
    }

    static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType {
        TypeMeta.FieldType(
            typeID: compatibleTypeId(staticTypeId).rawValue,
            nullable: isNullableType,
            trackRef: trackRef && isRefType
        )
    }

    static func write(
        _ value: Value,
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool
    ) throws {
        if refMode != .none {
            if refMode == .tracking, isRefType, let object = value as AnyObject? {
                if context.refWriter.tryWriteRef(buffer: context.buffer, object: object) {
                    return
                }
            } else {
                context.buffer.writeInt8(RefFlag.notNullValue.rawValue)
            }
        }

        if writeTypeInfo {
            try writeStaticTypeInfo(context)
        }

        try writeData(value, context)
    }

    static func read(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> Value {
        switch refMode {
        case .none:
            return try readPayload(context, readTypeInfo: readTypeInfo)
        case .nullOnly:
            let rawFlag = try context.buffer.readInt8()
            switch rawFlag {
            case RefFlag.null.rawValue:
                return defaultValue()
            case RefFlag.notNullValue.rawValue:
                return try readPayload(context, readTypeInfo: readTypeInfo)
            case RefFlag.refValue.rawValue:
                if context.trackRef {
                    let reservedRefID = context.refReader.reserveRefID()
                    let value = try readPayload(context, readTypeInfo: readTypeInfo)
                    if let object = value as AnyObject? {
                        context.refReader.storeRef(object, at: reservedRefID)
                    }
                    return value
                }
                return try readPayload(context, readTypeInfo: readTypeInfo)
            case RefFlag.ref.rawValue:
                let refID = try context.buffer.readVarUInt32()
                return try context.refReader.readRef(refID, as: Value.self)
            default:
                throw ForyError.refError("invalid ref flag \(rawFlag)")
            }
        case .tracking:
            let rawFlag = try context.buffer.readInt8()
            guard let flag = RefFlag(rawValue: rawFlag) else {
                throw ForyError.refError("invalid ref flag \(rawFlag)")
            }
            switch flag {
            case .null:
                return defaultValue()
            case .ref:
                let refID = try context.buffer.readVarUInt32()
                return try context.refReader.readRef(refID, as: Value.self)
            case .refValue:
                let reservedRefID = context.trackRef ? context.refReader.reserveRefID() : nil
                let value = try readPayload(context, readTypeInfo: readTypeInfo)
                if let reservedRefID, let object = value as AnyObject? {
                    context.refReader.storeRef(object, at: reservedRefID)
                }
                return value
            case .notNullValue:
                return try readPayload(context, readTypeInfo: readTypeInfo)
            }
        }
    }

    private static func readPayload(
        _ context: ReadContext,
        readTypeInfo: Bool
    ) throws -> Value {
        if readTypeInfo {
            _ = try Self.readTypeInfo(context)
        }
        return try readData(context)
    }
}

@inlinable
func compatibleTypeId(_ typeId: TypeId) -> TypeId {
    typeId == .structType ? .compatibleStruct : typeId
}

public struct SerializerCodec<T: Serializer>: Codec {
    public typealias Value = T

    public static var staticTypeId: TypeId { T.staticTypeId }
    public static var isNullableType: Bool { T.isNullableType }
    public static var isRefType: Bool { T.isRefType }

    public static func defaultValue() -> T {
        T.foryDefault()
    }

    public static func writeData(_ value: T, _ context: WriteContext) throws {
        try value.foryWriteData(context, hasGenerics: false)
    }

    public static func readData(_ context: ReadContext) throws -> T {
        try T.foryReadData(context)
    }

    public static func write(
        _ value: T,
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool
    ) throws {
        try value.foryWrite(context, refMode: refMode, writeTypeInfo: writeTypeInfo, hasGenerics: false)
    }

    public static func read(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> T {
        try T.foryRead(context, refMode: refMode, readTypeInfo: readTypeInfo)
    }

    public static func writeStaticTypeInfo(_ context: WriteContext) throws {
        try T.foryWriteStaticTypeInfo(context)
    }

    public static func readTypeInfo(_ context: ReadContext) throws -> TypeInfo? {
        try T.foryReadTypeInfo(context)
    }
}

public struct BoolCodec: Codec {
    public typealias Value = Bool
    public static var staticTypeId: TypeId { .bool }
    public static func defaultValue() -> Bool { false }
    public static func writeData(_ value: Bool, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Bool { try Bool.foryReadData(context) }
}

public struct Int8Codec: Codec {
    public typealias Value = Int8
    public static var staticTypeId: TypeId { .int8 }
    public static func defaultValue() -> Int8 { 0 }
    public static func writeData(_ value: Int8, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Int8 { try Int8.foryReadData(context) }
}

public struct Int16Codec: Codec {
    public typealias Value = Int16
    public static var staticTypeId: TypeId { .int16 }
    public static func defaultValue() -> Int16 { 0 }
    public static func writeData(_ value: Int16, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Int16 { try Int16.foryReadData(context) }
}

public struct Int32VarintCodec: Codec {
    public typealias Value = Int32
    public static var staticTypeId: TypeId { .varint32 }
    public static func defaultValue() -> Int32 { 0 }
    public static func writeData(_ value: Int32, _ context: WriteContext) throws { context.buffer.writeVarInt32(value) }
    public static func readData(_ context: ReadContext) throws -> Int32 { try context.buffer.readVarInt32() }
}

public struct Int32FixedCodec: Codec {
    public typealias Value = Int32
    public static var staticTypeId: TypeId { .int32 }
    public static func defaultValue() -> Int32 { 0 }
    public static func writeData(_ value: Int32, _ context: WriteContext) throws { context.buffer.writeInt32(value) }
    public static func readData(_ context: ReadContext) throws -> Int32 { try context.buffer.readInt32() }
}

public struct Int64VarintCodec: Codec {
    public typealias Value = Int64
    public static var staticTypeId: TypeId { .varint64 }
    public static func defaultValue() -> Int64 { 0 }
    public static func writeData(_ value: Int64, _ context: WriteContext) throws { context.buffer.writeVarInt64(value) }
    public static func readData(_ context: ReadContext) throws -> Int64 { try context.buffer.readVarInt64() }
}

public struct Int64FixedCodec: Codec {
    public typealias Value = Int64
    public static var staticTypeId: TypeId { .int64 }
    public static func defaultValue() -> Int64 { 0 }
    public static func writeData(_ value: Int64, _ context: WriteContext) throws { context.buffer.writeInt64(value) }
    public static func readData(_ context: ReadContext) throws -> Int64 { try context.buffer.readInt64() }
}

public struct Int64TaggedCodec: Codec {
    public typealias Value = Int64
    public static var staticTypeId: TypeId { .taggedInt64 }
    public static func defaultValue() -> Int64 { 0 }
    public static func writeData(_ value: Int64, _ context: WriteContext) throws { context.buffer.writeTaggedInt64(value) }
    public static func readData(_ context: ReadContext) throws -> Int64 { try context.buffer.readTaggedInt64() }
}

#if arch(arm64) || arch(x86_64)
public struct IntVarintCodec: Codec {
    public typealias Value = Int
    public static var staticTypeId: TypeId { .varint64 }
    public static func defaultValue() -> Int { 0 }
    public static func writeData(_ value: Int, _ context: WriteContext) throws { context.buffer.writeVarInt64(Int64(value)) }
    public static func readData(_ context: ReadContext) throws -> Int { Int(try context.buffer.readVarInt64()) }
}

public struct IntFixedCodec: Codec {
    public typealias Value = Int
    public static var staticTypeId: TypeId { .int64 }
    public static func defaultValue() -> Int { 0 }
    public static func writeData(_ value: Int, _ context: WriteContext) throws { context.buffer.writeInt64(Int64(value)) }
    public static func readData(_ context: ReadContext) throws -> Int { Int(try context.buffer.readInt64()) }
}

public struct IntTaggedCodec: Codec {
    public typealias Value = Int
    public static var staticTypeId: TypeId { .taggedInt64 }
    public static func defaultValue() -> Int { 0 }
    public static func writeData(_ value: Int, _ context: WriteContext) throws { context.buffer.writeTaggedInt64(Int64(value)) }
    public static func readData(_ context: ReadContext) throws -> Int { Int(try context.buffer.readTaggedInt64()) }
}
#endif

public struct UInt8Codec: Codec {
    public typealias Value = UInt8
    public static var staticTypeId: TypeId { .uint8 }
    public static func defaultValue() -> UInt8 { 0 }
    public static func writeData(_ value: UInt8, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> UInt8 { try UInt8.foryReadData(context) }
}

public struct UInt16Codec: Codec {
    public typealias Value = UInt16
    public static var staticTypeId: TypeId { .uint16 }
    public static func defaultValue() -> UInt16 { 0 }
    public static func writeData(_ value: UInt16, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> UInt16 { try UInt16.foryReadData(context) }
}

public struct UInt32VarintCodec: Codec {
    public typealias Value = UInt32
    public static var staticTypeId: TypeId { .varUInt32 }
    public static func defaultValue() -> UInt32 { 0 }
    public static func writeData(_ value: UInt32, _ context: WriteContext) throws { context.buffer.writeVarUInt32(value) }
    public static func readData(_ context: ReadContext) throws -> UInt32 { try context.buffer.readVarUInt32() }
}

public struct UInt32FixedCodec: Codec {
    public typealias Value = UInt32
    public static var staticTypeId: TypeId { .uint32 }
    public static func defaultValue() -> UInt32 { 0 }
    public static func writeData(_ value: UInt32, _ context: WriteContext) throws { context.buffer.writeUInt32(value) }
    public static func readData(_ context: ReadContext) throws -> UInt32 { try context.buffer.readUInt32() }
}

public struct UInt64VarintCodec: Codec {
    public typealias Value = UInt64
    public static var staticTypeId: TypeId { .varUInt64 }
    public static func defaultValue() -> UInt64 { 0 }
    public static func writeData(_ value: UInt64, _ context: WriteContext) throws { context.buffer.writeVarUInt64(value) }
    public static func readData(_ context: ReadContext) throws -> UInt64 { try context.buffer.readVarUInt64() }
}

public struct UInt64FixedCodec: Codec {
    public typealias Value = UInt64
    public static var staticTypeId: TypeId { .uint64 }
    public static func defaultValue() -> UInt64 { 0 }
    public static func writeData(_ value: UInt64, _ context: WriteContext) throws { context.buffer.writeUInt64(value) }
    public static func readData(_ context: ReadContext) throws -> UInt64 { try context.buffer.readUInt64() }
}

public struct UInt64TaggedCodec: Codec {
    public typealias Value = UInt64
    public static var staticTypeId: TypeId { .taggedUInt64 }
    public static func defaultValue() -> UInt64 { 0 }
    public static func writeData(_ value: UInt64, _ context: WriteContext) throws { context.buffer.writeTaggedUInt64(value) }
    public static func readData(_ context: ReadContext) throws -> UInt64 { try context.buffer.readTaggedUInt64() }
}

#if arch(arm64) || arch(x86_64)
public struct UIntVarintCodec: Codec {
    public typealias Value = UInt
    public static var staticTypeId: TypeId { .varUInt64 }
    public static func defaultValue() -> UInt { 0 }
    public static func writeData(_ value: UInt, _ context: WriteContext) throws { context.buffer.writeVarUInt64(UInt64(value)) }
    public static func readData(_ context: ReadContext) throws -> UInt { UInt(try context.buffer.readVarUInt64()) }
}

public struct UIntFixedCodec: Codec {
    public typealias Value = UInt
    public static var staticTypeId: TypeId { .uint64 }
    public static func defaultValue() -> UInt { 0 }
    public static func writeData(_ value: UInt, _ context: WriteContext) throws { context.buffer.writeUInt64(UInt64(value)) }
    public static func readData(_ context: ReadContext) throws -> UInt { UInt(try context.buffer.readUInt64()) }
}

public struct UIntTaggedCodec: Codec {
    public typealias Value = UInt
    public static var staticTypeId: TypeId { .taggedUInt64 }
    public static func defaultValue() -> UInt { 0 }
    public static func writeData(_ value: UInt, _ context: WriteContext) throws { context.buffer.writeTaggedUInt64(UInt64(value)) }
    public static func readData(_ context: ReadContext) throws -> UInt { UInt(try context.buffer.readTaggedUInt64()) }
}
#endif

public struct Float16Codec: Codec {
    public typealias Value = Float16
    public static var staticTypeId: TypeId { .float16 }
    public static func defaultValue() -> Float16 { 0 }
    public static func writeData(_ value: Float16, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Float16 { try Float16.foryReadData(context) }
}

public struct BFloat16Codec: Codec {
    public typealias Value = BFloat16
    public static var staticTypeId: TypeId { .bfloat16 }
    public static func defaultValue() -> BFloat16 { .init(rawValue: 0) }
    public static func writeData(_ value: BFloat16, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> BFloat16 { try BFloat16.foryReadData(context) }
}

public struct Float32Codec: Codec {
    public typealias Value = Float
    public static var staticTypeId: TypeId { .float32 }
    public static func defaultValue() -> Float { 0 }
    public static func writeData(_ value: Float, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Float { try Float.foryReadData(context) }
}

public struct Float64Codec: Codec {
    public typealias Value = Double
    public static var staticTypeId: TypeId { .float64 }
    public static func defaultValue() -> Double { 0 }
    public static func writeData(_ value: Double, _ context: WriteContext) throws { try value.foryWriteData(context, hasGenerics: false) }
    public static func readData(_ context: ReadContext) throws -> Double { try Double.foryReadData(context) }
}

public struct OptionalCodec<Wrapped: Codec>: Codec {
    public typealias Value = Wrapped.Value?

    public static var staticTypeId: TypeId { Wrapped.staticTypeId }
    public static var isNullableType: Bool { true }
    public static var isRefType: Bool { Wrapped.isRefType }

    public static func defaultValue() -> Wrapped.Value? {
        nil
    }

    public static func writeData(_ value: Wrapped.Value?, _ context: WriteContext) throws {
        guard let value else {
            throw ForyError.invalidData("Option.none cannot write raw payload")
        }
        try Wrapped.writeData(value, context)
    }

    public static func readData(_ context: ReadContext) throws -> Wrapped.Value? {
        .some(try Wrapped.readData(context))
    }

    public static func writeStaticTypeInfo(_ context: WriteContext) throws {
        try Wrapped.writeStaticTypeInfo(context)
    }

    public static func readTypeInfo(_ context: ReadContext) throws -> TypeInfo? {
        try Wrapped.readTypeInfo(context)
    }

    public static func write(
        _ value: Wrapped.Value?,
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool
    ) throws {
        switch refMode {
        case .none:
            guard let value else {
                throw ForyError.invalidData("Option.none with RefMode.none")
            }
            try Wrapped.write(value, context, refMode: .none, writeTypeInfo: writeTypeInfo)
        case .nullOnly:
            guard let value else {
                context.buffer.writeInt8(RefFlag.null.rawValue)
                return
            }
            context.buffer.writeInt8(RefFlag.notNullValue.rawValue)
            try Wrapped.write(value, context, refMode: .none, writeTypeInfo: writeTypeInfo)
        case .tracking:
            guard let value else {
                context.buffer.writeInt8(RefFlag.null.rawValue)
                return
            }
            try Wrapped.write(value, context, refMode: .tracking, writeTypeInfo: writeTypeInfo)
        }
    }

    public static func read(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> Wrapped.Value? {
        let typeInfo = readTypeInfo ? nil : context.getTypeInfo(for: Value.self)
        switch refMode {
        case .none:
            return .some(
                try context.withTypeInfo(typeInfo, for: Wrapped.Value.self) {
                    try Wrapped.read(context, refMode: .none, readTypeInfo: readTypeInfo)
                }
            )
        case .nullOnly:
            let refFlag = try context.buffer.readInt8()
            if refFlag == RefFlag.null.rawValue {
                return nil
            }
            return .some(
                try context.withTypeInfo(typeInfo, for: Wrapped.Value.self) {
                    try Wrapped.read(context, refMode: .none, readTypeInfo: readTypeInfo)
                }
            )
        case .tracking:
            let refFlag = try context.buffer.readInt8()
            if refFlag == RefFlag.null.rawValue {
                return nil
            }
            context.buffer.moveBack(1)
            return .some(
                try context.withTypeInfo(typeInfo, for: Wrapped.Value.self) {
                    try Wrapped.read(context, refMode: .tracking, readTypeInfo: readTypeInfo)
                }
            )
        }
    }

    public static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType {
        var fieldType = Wrapped.compatibleFieldType(trackRef: trackRef)
        fieldType.nullable = true
        return fieldType
    }
}

public struct ListCodec<Element: Codec>: Codec {
    public typealias Value = [Element.Value]

    public static var staticTypeId: TypeId {
        primitiveArrayTypeId ?? .list
    }

    public static func defaultValue() -> [Element.Value] {
        []
    }

    public static func writeStaticTypeInfo(_ context: WriteContext) throws {
        if Element.self == UInt8Codec.self, context.compatible {
            context.buffer.writeUInt8(UInt8(truncatingIfNeeded: TypeId.binary.rawValue))
            return
        }
        context.buffer.writeUInt8(UInt8(truncatingIfNeeded: staticTypeId.rawValue))
    }

    public static func readTypeInfo(_ context: ReadContext) throws -> TypeInfo? {
        if let primitiveArrayTypeId {
            let rawTypeID = try context.buffer.readVarUInt32()
            guard let actualTypeID = TypeId(rawValue: rawTypeID) else {
                throw ForyError.invalidData("unknown type id \(rawTypeID)")
            }
            if Element.self == UInt8Codec.self {
                if actualTypeID == .uint8Array || actualTypeID == .binary {
                    return nil
                }
                throw ForyError.typeMismatch(expected: TypeId.uint8Array.rawValue, actual: rawTypeID)
            }
            if actualTypeID != primitiveArrayTypeId {
                throw ForyError.typeMismatch(expected: primitiveArrayTypeId.rawValue, actual: rawTypeID)
            }
            return nil
        }
        return try context.readStaticTypeInfo(.list)
    }

    public static func writeData(_ value: [Element.Value], _ context: WriteContext) throws {
        if try writePrimitiveArray(value, context) {
            return
        }

        let buffer = context.buffer
        buffer.writeVarUInt32(UInt32(value.count))
        if value.isEmpty {
            return
        }

        let hasNull = Element.isNullableType && value.contains(where: \.foryIsNone)
        let trackRef = context.trackRef && Element.isRefType
        let dynamicElementType = Element.staticTypeId == .unknown
        let declaredElementType = !TypeId.needsTypeInfoForField(Element.staticTypeId)

        var header: UInt8 = dynamicElementType ? 0 : CollectionHeader.sameType
        if trackRef {
            header |= CollectionHeader.trackingRef
        }
        if hasNull {
            header |= CollectionHeader.hasNull
        }
        if declaredElementType {
            header |= CollectionHeader.declaredElementType
        }

        buffer.writeUInt8(header)
        if !dynamicElementType && !declaredElementType {
            try Element.writeStaticTypeInfo(context)
        }

        if dynamicElementType {
            let refMode: RefMode
            if trackRef {
                refMode = .tracking
            } else if hasNull {
                refMode = .nullOnly
            } else {
                refMode = .none
            }
            for element in value {
                try Element.write(element, context, refMode: refMode, writeTypeInfo: true)
            }
            return
        }

        if trackRef {
            for element in value {
                try Element.write(element, context, refMode: .tracking, writeTypeInfo: false)
            }
        } else if hasNull {
            for element in value {
                if element.foryIsNone {
                    buffer.writeInt8(RefFlag.null.rawValue)
                } else {
                    buffer.writeInt8(RefFlag.notNullValue.rawValue)
                    try Element.writeData(element, context)
                }
            }
        } else {
            for element in value {
                try Element.writeData(element, context)
            }
        }
    }

    public static func readData(_ context: ReadContext) throws -> [Element.Value] {
        if let primitiveValue = try readPrimitiveArray(context) {
            return primitiveValue
        }

        let buffer = context.buffer
        let length = Int(try buffer.readVarUInt32())
        try context.ensureCollectionLength(length, label: "array")
        if length == 0 {
            return []
        }

        let header = try buffer.readUInt8()
        let trackRef = (header & CollectionHeader.trackingRef) != 0
        let hasNull = (header & CollectionHeader.hasNull) != 0
        let declared = (header & CollectionHeader.declaredElementType) != 0
        let sameType = (header & CollectionHeader.sameType) != 0
        if !sameType {
            if trackRef {
                return try readArrayUninitialized(count: length) { destination in
                    for index in 0..<length {
                        destination.advanced(by: index).initialize(
                            to: try Element.read(context, refMode: .tracking, readTypeInfo: true)
                        )
                    }
                }
            }

            if hasNull {
                return try readArrayUninitialized(count: length) { destination in
                    for index in 0..<length {
                        let refFlag = try buffer.readInt8()
                        if refFlag == RefFlag.null.rawValue {
                            destination.advanced(by: index).initialize(to: Element.defaultValue())
                        } else if refFlag == RefFlag.notNullValue.rawValue {
                            destination.advanced(by: index).initialize(
                                to: try Element.read(context, refMode: .none, readTypeInfo: true)
                            )
                        } else {
                            throw ForyError.refError("invalid nullability flag \(refFlag)")
                        }
                    }
                }
            }

            return try readArrayUninitialized(count: length) { destination in
                for index in 0..<length {
                    destination.advanced(by: index).initialize(
                        to: try Element.read(context, refMode: .none, readTypeInfo: true)
                    )
                }
            }
        }

        let elementTypeInfo = declared ? nil : try Element.readTypeInfo(context)
        return try context.withTypeInfo(elementTypeInfo, for: Element.Value.self) {
            if trackRef {
                return try readArrayUninitialized(count: length) { destination in
                    for index in 0..<length {
                        destination.advanced(by: index).initialize(
                            to: try Element.read(context, refMode: .tracking, readTypeInfo: false)
                        )
                    }
                }
            }

            if hasNull {
                return try readArrayUninitialized(count: length) { destination in
                    for index in 0..<length {
                        let refFlag = try buffer.readInt8()
                        if refFlag == RefFlag.null.rawValue {
                            destination.advanced(by: index).initialize(to: Element.defaultValue())
                        } else if refFlag == RefFlag.notNullValue.rawValue {
                            destination.advanced(by: index).initialize(
                                to: try Element.read(context, refMode: .none, readTypeInfo: false)
                            )
                        } else {
                            throw ForyError.refError("invalid nullability flag \(refFlag)")
                        }
                    }
                }
            }

            return try readArrayUninitialized(count: length) { destination in
                for index in 0..<length {
                    destination.advanced(by: index).initialize(
                        to: try Element.read(context, refMode: .none, readTypeInfo: false)
                    )
                }
            }
        }
    }

    public static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType {
        TypeMeta.FieldType(
            typeID: primitiveArrayTypeId?.rawValue ?? TypeId.list.rawValue,
            nullable: false,
            trackRef: trackRef && isRefType,
            generics: primitiveArrayTypeId == nil ? [Element.compatibleFieldType(trackRef: false)] : []
        )
    }

    private static var primitiveArrayTypeId: TypeId? {
        switch Element.self {
        case is UInt8Codec.Type: return .uint8Array
        case is BoolCodec.Type: return .boolArray
        case is Int8Codec.Type: return .int8Array
        case is Int16Codec.Type: return .int16Array
        case is Int32VarintCodec.Type: return .int32Array
        case is Int64VarintCodec.Type: return .int64Array
        case is UInt16Codec.Type: return .uint16Array
        case is UInt32VarintCodec.Type: return .uint32Array
        case is UInt64VarintCodec.Type: return .uint64Array
        case is Float16Codec.Type: return .float16Array
        case is BFloat16Codec.Type: return .bfloat16Array
        case is Float32Codec.Type: return .float32Array
        case is Float64Codec.Type: return .float64Array
        default: return nil
        }
    }

    private static func writePrimitiveArray(_ value: [Element.Value], _ context: WriteContext) throws -> Bool {
        switch Element.self {
        case is UInt8Codec.Type:
            try uncheckedArrayCast(value, to: UInt8.self).foryWriteData(context, hasGenerics: false)
        case is BoolCodec.Type:
            try uncheckedArrayCast(value, to: Bool.self).foryWriteData(context, hasGenerics: false)
        case is Int8Codec.Type:
            try uncheckedArrayCast(value, to: Int8.self).foryWriteData(context, hasGenerics: false)
        case is Int16Codec.Type:
            try uncheckedArrayCast(value, to: Int16.self).foryWriteData(context, hasGenerics: false)
        case is Int32VarintCodec.Type:
            try uncheckedArrayCast(value, to: Int32.self).foryWriteData(context, hasGenerics: false)
        case is Int64VarintCodec.Type:
            try uncheckedArrayCast(value, to: Int64.self).foryWriteData(context, hasGenerics: false)
        case is UInt16Codec.Type:
            try uncheckedArrayCast(value, to: UInt16.self).foryWriteData(context, hasGenerics: false)
        case is UInt32VarintCodec.Type:
            try uncheckedArrayCast(value, to: UInt32.self).foryWriteData(context, hasGenerics: false)
        case is UInt64VarintCodec.Type:
            try uncheckedArrayCast(value, to: UInt64.self).foryWriteData(context, hasGenerics: false)
        case is Float16Codec.Type:
            try uncheckedArrayCast(value, to: Float16.self).foryWriteData(context, hasGenerics: false)
        case is BFloat16Codec.Type:
            try uncheckedArrayCast(value, to: BFloat16.self).foryWriteData(context, hasGenerics: false)
        case is Float32Codec.Type:
            try uncheckedArrayCast(value, to: Float.self).foryWriteData(context, hasGenerics: false)
        case is Float64Codec.Type:
            try uncheckedArrayCast(value, to: Double.self).foryWriteData(context, hasGenerics: false)
        default:
            return false
        }
        return true
    }

    private static func readPrimitiveArray(_ context: ReadContext) throws -> [Element.Value]? {
        switch Element.self {
        case is UInt8Codec.Type:
            return uncheckedArrayCast(try [UInt8].foryReadData(context), to: Element.Value.self)
        case is BoolCodec.Type:
            return uncheckedArrayCast(try [Bool].foryReadData(context), to: Element.Value.self)
        case is Int8Codec.Type:
            return uncheckedArrayCast(try [Int8].foryReadData(context), to: Element.Value.self)
        case is Int16Codec.Type:
            return uncheckedArrayCast(try [Int16].foryReadData(context), to: Element.Value.self)
        case is Int32VarintCodec.Type:
            return uncheckedArrayCast(try [Int32].foryReadData(context), to: Element.Value.self)
        case is Int64VarintCodec.Type:
            return uncheckedArrayCast(try [Int64].foryReadData(context), to: Element.Value.self)
        case is UInt16Codec.Type:
            return uncheckedArrayCast(try [UInt16].foryReadData(context), to: Element.Value.self)
        case is UInt32VarintCodec.Type:
            return uncheckedArrayCast(try [UInt32].foryReadData(context), to: Element.Value.self)
        case is UInt64VarintCodec.Type:
            return uncheckedArrayCast(try [UInt64].foryReadData(context), to: Element.Value.self)
        case is Float16Codec.Type:
            return uncheckedArrayCast(try [Float16].foryReadData(context), to: Element.Value.self)
        case is BFloat16Codec.Type:
            return uncheckedArrayCast(try [BFloat16].foryReadData(context), to: Element.Value.self)
        case is Float32Codec.Type:
            return uncheckedArrayCast(try [Float].foryReadData(context), to: Element.Value.self)
        case is Float64Codec.Type:
            return uncheckedArrayCast(try [Double].foryReadData(context), to: Element.Value.self)
        default:
            return nil
        }
    }
}

public struct SetCodec<Element: Codec>: Codec where Element.Value: Hashable {
    public typealias Value = Set<Element.Value>

    public static var staticTypeId: TypeId { .set }
    public static func defaultValue() -> Set<Element.Value> { [] }

    public static func writeData(_ value: Set<Element.Value>, _ context: WriteContext) throws {
        try ListCodec<Element>.writeData(Array(value), context)
    }

    public static func readData(_ context: ReadContext) throws -> Set<Element.Value> {
        Set(try ListCodec<Element>.readData(context))
    }

    public static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType {
        TypeMeta.FieldType(
            typeID: TypeId.set.rawValue,
            nullable: false,
            trackRef: trackRef && isRefType,
            generics: [Element.compatibleFieldType(trackRef: false)]
        )
    }
}

public struct MapCodec<Key: Codec, ValueCodec: Codec>: Codec where Key.Value: Hashable {
    public typealias Value = [Key.Value: ValueCodec.Value]

    public static var staticTypeId: TypeId { .map }
    public static func defaultValue() -> [Key.Value: ValueCodec.Value] { [:] }

    public static func writeData(_ value: [Key.Value: ValueCodec.Value], _ context: WriteContext) throws {
        context.buffer.writeVarUInt32(UInt32(value.count))
        if value.isEmpty {
            return
        }

        let trackKeyRef = context.trackRef && Key.isRefType
        let trackValueRef = context.trackRef && ValueCodec.isRefType
        let keyDeclared = !TypeId.needsTypeInfoForField(Key.staticTypeId)
        let valueDeclared = !TypeId.needsTypeInfoForField(ValueCodec.staticTypeId)

        var iterator = value.makeIterator()
        var pendingPair = iterator.next()

        while let pair = pendingPair {
            let keyIsNil = pair.key.foryIsNone
            let valueIsNil = pair.value.foryIsNone

            if keyIsNil || valueIsNil {
                var header: UInt8 = 0
                if trackKeyRef { header |= MapHeader.trackingKeyRef }
                if trackValueRef { header |= MapHeader.trackingValueRef }
                if keyIsNil { header |= MapHeader.keyNull }
                if valueIsNil { header |= MapHeader.valueNull }
                if !keyIsNil && keyDeclared { header |= MapHeader.declaredKeyType }
                if !valueIsNil && valueDeclared { header |= MapHeader.declaredValueType }

                context.buffer.writeUInt8(header)
                if !keyIsNil {
                    if !keyDeclared {
                        try Key.writeStaticTypeInfo(context)
                    }
                    if trackKeyRef {
                        try Key.write(pair.key, context, refMode: .tracking, writeTypeInfo: false)
                    } else {
                        try Key.writeData(pair.key, context)
                    }
                }
                if !valueIsNil {
                    if !valueDeclared {
                        try ValueCodec.writeStaticTypeInfo(context)
                    }
                    if trackValueRef {
                        try ValueCodec.write(pair.value, context, refMode: .tracking, writeTypeInfo: false)
                    } else {
                        try ValueCodec.writeData(pair.value, context)
                    }
                }
                pendingPair = iterator.next()
                continue
            }

            var header: UInt8 = 0
            if trackKeyRef { header |= MapHeader.trackingKeyRef }
            if trackValueRef { header |= MapHeader.trackingValueRef }
            if keyDeclared { header |= MapHeader.declaredKeyType }
            if valueDeclared { header |= MapHeader.declaredValueType }

            context.buffer.writeUInt8(header)
            let chunkSizeOffset = context.buffer.count
            context.buffer.writeUInt8(0)

            if !keyDeclared {
                try Key.writeStaticTypeInfo(context)
            }
            if !valueDeclared {
                try ValueCodec.writeStaticTypeInfo(context)
            }

            var chunkSize: UInt8 = 0
            while chunkSize < UInt8.max, let current = pendingPair {
                if current.key.foryIsNone || current.value.foryIsNone {
                    break
                }
                if trackKeyRef {
                    try Key.write(current.key, context, refMode: .tracking, writeTypeInfo: false)
                } else {
                    try Key.writeData(current.key, context)
                }
                if trackValueRef {
                    try ValueCodec.write(current.value, context, refMode: .tracking, writeTypeInfo: false)
                } else {
                    try ValueCodec.writeData(current.value, context)
                }
                chunkSize &+= 1
                pendingPair = iterator.next()
            }
            context.buffer.setByte(at: chunkSizeOffset, to: chunkSize)
        }
    }

    public static func readData(_ context: ReadContext) throws -> [Key.Value: ValueCodec.Value] {
        let totalLength = Int(try context.buffer.readVarUInt32())
        try context.ensureCollectionLength(totalLength, label: "map")
        if totalLength == 0 {
            return [:]
        }

        var map: [Key.Value: ValueCodec.Value] = [:]
        map.reserveCapacity(Swift.min(totalLength, context.buffer.remaining))

        var readCount = 0
        while readCount < totalLength {
            let header = try context.buffer.readUInt8()
            let trackKeyRef = (header & MapHeader.trackingKeyRef) != 0
            let keyNull = (header & MapHeader.keyNull) != 0
            let keyDeclared = (header & MapHeader.declaredKeyType) != 0

            let trackValueRef = (header & MapHeader.trackingValueRef) != 0
            let valueNull = (header & MapHeader.valueNull) != 0
            let valueDeclared = (header & MapHeader.declaredValueType) != 0

            if keyNull && valueNull {
                map[Key.defaultValue()] = ValueCodec.defaultValue()
                readCount += 1
                continue
            }

            if keyNull {
                let value = try ValueCodec.read(
                    context,
                    refMode: trackValueRef ? .tracking : .none,
                    readTypeInfo: !valueDeclared
                )
                map[Key.defaultValue()] = value
                readCount += 1
                continue
            }

            if valueNull {
                let key = try Key.read(
                    context,
                    refMode: trackKeyRef ? .tracking : .none,
                    readTypeInfo: !keyDeclared
                )
                map[key] = ValueCodec.defaultValue()
                readCount += 1
                continue
            }

            let chunkSize = Int(try context.buffer.readUInt8())
            if chunkSize > (totalLength - readCount) {
                throw ForyError.invalidData("map chunk size exceeds remaining entries")
            }
            let keyTypeInfo = keyDeclared ? nil : try Key.readTypeInfo(context)
            let valueTypeInfo = valueDeclared ? nil : try ValueCodec.readTypeInfo(context)
            for _ in 0..<chunkSize {
                let key = try context.withTypeInfo(keyTypeInfo, for: Key.Value.self) {
                    try Key.read(
                        context,
                        refMode: trackKeyRef ? .tracking : .none,
                        readTypeInfo: false
                    )
                }
                let value = try context.withTypeInfo(valueTypeInfo, for: ValueCodec.Value.self) {
                    try ValueCodec.read(
                        context,
                        refMode: trackValueRef ? .tracking : .none,
                        readTypeInfo: false
                    )
                }
                map[key] = value
            }
            readCount += chunkSize
        }

        return map
    }

    public static func compatibleFieldType(trackRef: Bool) -> TypeMeta.FieldType {
        TypeMeta.FieldType(
            typeID: TypeId.map.rawValue,
            nullable: false,
            trackRef: trackRef && isRefType,
            generics: [
                Key.compatibleFieldType(trackRef: false),
                ValueCodec.compatibleFieldType(trackRef: false)
            ]
        )
    }
}
