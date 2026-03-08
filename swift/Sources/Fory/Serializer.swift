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
    static func foryReadTypeInfo(_ context: ReadContext) throws
    static func foryCompatibleTypeMetaFields(trackRef: Bool) -> [TypeMeta.FieldInfo]
    func foryWriteTypeInfo(_ context: WriteContext) throws
    var foryPrimitiveDataSize: Int? { get }
    func foryWritePrimitiveData(to base: UnsafeMutablePointer<UInt8>, index: inout Int)
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
    var foryPrimitiveDataSize: Int? { nil }

    @inlinable
    func foryWritePrimitiveData(to _: UnsafeMutablePointer<UInt8>, index _: inout Int) {}
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
        if refMode != .none {
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
                    try Self.foryReadTypeInfo(context)
                }
                let value = try Self.foryReadData(context)
                context.finishPendingRefIfNeeded(value)
                context.popPendingRef()
                return value
            case .notNullValue:
                break
            }
        }

        if readTypeInfo {
            try Self.foryReadTypeInfo(context)
        }
        return try Self.foryReadData(context)
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

    static func foryReadTypeInfo(_ context: ReadContext) throws {
        let rawTypeID = try context.buffer.readVarUInt32()
        guard let typeID = TypeId(rawValue: rawTypeID) else {
            throw ForyError.invalidData("unknown type id \(rawTypeID)")
        }

        guard staticTypeId.isUserTypeKind else {
            if typeID != staticTypeId {
                throw ForyError.typeMismatch(expected: staticTypeId.rawValue, actual: rawTypeID)
            }
            return
        }

        let typeInfo = try context.typeInfo(for: Self.self)
        let expectedWireTypeID = typeInfo.wireTypeID(compatible: context.compatible)
        if !isAllowedRegisteredWireTypeID(
            typeID,
            declaredTypeID: typeInfo.typeID,
            registerByName: typeInfo.registerByName,
            compatible: context.compatible
        ) {
            throw ForyError.typeMismatch(expected: expectedWireTypeID.rawValue, actual: rawTypeID)
        }

        switch typeID {
        case .compatibleStruct, .namedCompatibleStruct:
            let remoteTypeMeta = try readValidatedTypeMeta(
                in: context,
                typeInfo: typeInfo,
                wireTypeID: typeID
            )
            context.pushTypeMeta(
                for: Self.self,
                remoteTypeMeta,
                localTypeInfo: typeInfo
            )
        case .namedEnum, .namedStruct, .namedExt, .namedUnion:
            if context.compatible {
                let remoteTypeMeta = try readValidatedTypeMeta(
                    in: context,
                    typeInfo: typeInfo,
                    wireTypeID: typeID
                )
                if typeID == .namedStruct {
                    context.pushTypeMeta(
                        for: Self.self,
                        remoteTypeMeta,
                        localTypeInfo: typeInfo
                    )
                }
            } else {
                let namespace = try readMetaString(
                    context: context,
                    decoder: .namespace,
                    encodings: namespaceMetaStringEncodings
                )
                let typeName = try readMetaString(
                    context: context,
                    decoder: .typeName,
                    encodings: typeNameMetaStringEncodings
                )
                guard typeInfo.registerByName else {
                    throw ForyError.invalidData("received name-registered type info for id-registered local type")
                }
                if namespace.value != typeInfo.namespace.value || typeName.value != typeInfo.typeName.value {
                    throw ForyError.invalidData(
                        "type name mismatch: expected \(typeInfo.namespace.value)::\(typeInfo.typeName.value), got \(namespace.value)::\(typeName.value)"
                    )
                }
            }
        default:
            if !typeInfo.registerByName && registeredWireTypeNeedsUserTypeID(typeID) {
                guard let localUserTypeID = typeInfo.userTypeID else {
                    throw ForyError.invalidData("missing user type id for id-registered type")
                }
                let remoteUserTypeID = try context.buffer.readVarUInt32()
                if remoteUserTypeID != localUserTypeID {
                    throw ForyError.typeMismatch(expected: localUserTypeID, actual: remoteUserTypeID)
                }
            }
        }
    }

    @inline(__always)
    private static func readValidatedTypeMeta(
        in context: ReadContext,
        typeInfo: TypeInfo,
        wireTypeID: TypeId
    ) throws -> TypeMeta {
        let remoteTypeMeta = try context.readTypeMeta()
        if !context.isTypeMetaValidationCached(
            for: typeInfo.swiftTypeID,
            wireTypeID: wireTypeID,
            headerHash: remoteTypeMeta.headerHash
        ) {
            try validateTypeMeta(
                remoteTypeMeta,
                localTypeInfo: typeInfo,
                compatible: context.compatible,
                actualWireTypeID: wireTypeID
            )
            context.cacheTypeMetaValidation(
                for: typeInfo.swiftTypeID,
                wireTypeID: wireTypeID,
                headerHash: remoteTypeMeta.headerHash
            )
        }
        return remoteTypeMeta
    }

    private static func validateTypeMeta(
        _ remoteTypeMeta: TypeMeta,
        localTypeInfo: TypeInfo,
        compatible: Bool,
        actualWireTypeID: TypeId
    ) throws {
        if remoteTypeMeta.registerByName {
            guard localTypeInfo.registerByName else {
                throw ForyError.invalidData("received name-registered compatible metadata for id-registered local type")
            }
            if remoteTypeMeta.namespace.value != localTypeInfo.namespace.value {
                throw ForyError.invalidData(
                    "namespace mismatch: expected \(localTypeInfo.namespace.value), got \(remoteTypeMeta.namespace.value)"
                )
            }
            if remoteTypeMeta.typeName.value != localTypeInfo.typeName.value {
                throw ForyError.invalidData(
                    "type name mismatch: expected \(localTypeInfo.typeName.value), got \(remoteTypeMeta.typeName.value)"
                )
            }
        } else {
            guard !localTypeInfo.registerByName else {
                throw ForyError.invalidData("received id-registered compatible metadata for name-registered local type")
            }
            guard let remoteUserTypeID = remoteTypeMeta.userTypeID else {
                throw ForyError.invalidData("missing user type id in compatible type metadata")
            }
            guard let localUserTypeID = localTypeInfo.userTypeID else {
                throw ForyError.invalidData("missing local user type id metadata for id-registered type")
            }
            if remoteUserTypeID != localUserTypeID {
                throw ForyError.typeMismatch(expected: localUserTypeID, actual: remoteUserTypeID)
            }
        }

        if let remoteTypeID = remoteTypeMeta.typeID,
           let remoteWireTypeID = TypeId(rawValue: remoteTypeID),
           !isAllowedRegisteredWireTypeID(
               remoteWireTypeID,
               declaredTypeID: localTypeInfo.typeID,
               registerByName: localTypeInfo.registerByName,
               compatible: compatible
           ) {
            throw ForyError.typeMismatch(expected: actualWireTypeID.rawValue, actual: remoteTypeID)
        }
    }

}
