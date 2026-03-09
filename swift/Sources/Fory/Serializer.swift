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

public protocol Serializer {
    static func foryDefault() -> Self
    static var staticTypeId: TypeId { get }

    static var isNullableType: Bool { get }
    static var isRefType: Bool { get }

    var foryIsNone: Bool { get }

    func foryWriteData(_ context: WriteContext, hasGenerics: Bool) throws
    static func foryReadData(_ context: ReadContext) throws -> Self

    func foryWrite(
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool,
        hasGenerics: Bool
    ) throws

    static func foryRead(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> Self

    static func foryWriteTypeInfo(_ context: WriteContext) throws
    static func foryReadTypeInfo(_ context: ReadContext) throws -> TypeInfo?
    static func foryReadCompatibleData(_ context: ReadContext, remoteTypeInfo: TypeInfo) throws -> Self
    static func foryCompatibleTypeMetaFields(trackRef: Bool) -> [TypeMeta.FieldInfo]
    func foryWriteTypeInfo(_ context: WriteContext) throws
}

public extension Serializer {
    @inlinable
    static var isNullableType: Bool { false }

    @inlinable
    static var isRefType: Bool { false }

    @inlinable
    var foryIsNone: Bool { false }

    @inlinable
    func foryWriteTypeInfo(_ context: WriteContext) throws {
        try Self.foryWriteTypeInfo(context)
    }

    @inlinable
    static func foryCompatibleTypeMetaFields(trackRef _: Bool) -> [TypeMeta.FieldInfo] {
        []
    }

    @inlinable
    static func foryReadCompatibleData(_ context: ReadContext, remoteTypeInfo _: TypeInfo) throws -> Self {
        try foryReadData(context)
    }
}

public extension Serializer {

    @inlinable
    func foryWrite(
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool,
        hasGenerics: Bool
    ) throws {
        if refMode != .none {
            if refMode == .tracking, Self.isRefType, let object = self as AnyObject? {
                if context.refWriter.tryWriteRef(buffer: context.buffer, object: object) {
                    return
                }
            } else {
                context.buffer.writeInt8(RefFlag.notNullValue.rawValue)
            }
        }

        if writeTypeInfo {
            try Self.foryWriteTypeInfo(context)
        }

        try foryWriteData(context, hasGenerics: hasGenerics)
    }

    @inlinable
    static func foryRead(
        _ context: ReadContext,
        refMode: RefMode,
        readTypeInfo: Bool
    ) throws -> Self {
        switch refMode {
        case .none:
            if readTypeInfo {
                if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                    return try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                }
            }
            return try Self.foryReadData(context)
        case .nullOnly:
            let rawFlag = try context.buffer.readInt8()
            switch rawFlag {
            case RefFlag.null.rawValue:
                return Self.foryDefault()
            case RefFlag.notNullValue.rawValue:
                if readTypeInfo {
                    if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                        return try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                    }
                }
                return try Self.foryReadData(context)
            case RefFlag.refValue.rawValue:
                if context.trackRef {
                    let reservedRefID = context.refReader.reserveRefID()
                    context.pushPendingRef(reservedRefID)
                    if readTypeInfo {
                        if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                            let value = try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                            context.finishPendingRefIfNeeded(value)
                            context.popPendingRef()
                            return value
                        }
                    }
                    let value = try Self.foryReadData(context)
                    context.finishPendingRefIfNeeded(value)
                    context.popPendingRef()
                    return value
                }
                if readTypeInfo {
                    if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                        return try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                    }
                }
                return try Self.foryReadData(context)
            case RefFlag.ref.rawValue:
                let refID = try context.buffer.readVarUInt32()
                return try context.refReader.readRef(refID, as: Self.self)
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
                return Self.foryDefault()
            case .ref:
                let refID = try context.buffer.readVarUInt32()
                return try context.refReader.readRef(refID, as: Self.self)
            case .refValue:
                let reservedRefID = context.refReader.reserveRefID()
                context.pushPendingRef(reservedRefID)
                if readTypeInfo {
                    if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                        let value = try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                        context.finishPendingRefIfNeeded(value)
                        context.popPendingRef()
                        return value
                    }
                }
                let value = try Self.foryReadData(context)
                context.finishPendingRefIfNeeded(value)
                context.popPendingRef()
                return value
            case .notNullValue:
                if readTypeInfo {
                    if let remoteTypeInfo = try Self.foryReadTypeInfo(context) {
                        return try Self.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
                    }
                }
                return try Self.foryReadData(context)
            }
        }
    }

    static func foryWriteTypeInfo(_ context: WriteContext) throws {
        guard staticTypeId.isUserTypeKind else {
            context.buffer.writeUInt8(UInt8(truncatingIfNeeded: staticTypeId.rawValue))
            return
        }

        let typeInfo = try context.typeInfo(for: Self.self)
        let wireTypeID = typeInfo.wireTypeID(compatible: context.compatible)
        context.buffer.writeUInt8(UInt8(truncatingIfNeeded: wireTypeID.rawValue))
        switch wireTypeID {
        case .compatibleStruct, .namedCompatibleStruct:
            guard let typeDefBytes = typeInfo.typeDefBytes else {
                throw ForyError.invalidData("missing compatible type definition for \(typeInfo.typeID)")
            }
            context.writeTypeMeta(
                for: Self.self,
                typeDefBytes: typeDefBytes
            )
        case .namedEnum, .namedStruct, .namedExt, .namedUnion:
            if context.compatible {
                guard let typeDefBytes = typeInfo.typeDefBytes else {
                    throw ForyError.invalidData("missing compatible type definition for \(typeInfo.typeID)")
                }
                context.writeTypeMeta(
                    for: Self.self,
                    typeDefBytes: typeDefBytes
                )
            } else {
                try writeMetaString(
                    context: context,
                    value: typeInfo.namespace,
                    encodings: namespaceMetaStringEncodings,
                    encoder: .namespace
                )
                try writeMetaString(
                    context: context,
                    value: typeInfo.typeName,
                    encodings: typeNameMetaStringEncodings,
                    encoder: .typeName
                )
            }
        default:
            if !typeInfo.registerByName && registeredWireTypeNeedsUserTypeID(wireTypeID) {
                guard let userTypeID = typeInfo.userTypeID else {
                    throw ForyError.invalidData("missing user type id for id-registered type")
                }
                context.buffer.writeVarUInt32(userTypeID)
            }
        }
    }

    static func foryReadTypeInfo(_ context: ReadContext) throws -> TypeInfo? {
        try context.readTypeInfo(for: Self.self)
    }

}
