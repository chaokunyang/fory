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

final class CompatibleTypeMetaEncoding: @unchecked Sendable {
    let bytes: [UInt8]
    let firstDefinitionBytes: [UInt8]
    let headerHash: UInt64
    let hasUserTypeFields: Bool

    init(bytes: [UInt8], headerHash: UInt64, hasUserTypeFields: Bool) {
        self.bytes = bytes
        var firstDefinitionBytes: [UInt8] = []
        firstDefinitionBytes.reserveCapacity(bytes.count + 1)
        firstDefinitionBytes.append(0)
        firstDefinitionBytes.append(contentsOf: bytes)
        self.firstDefinitionBytes = firstDefinitionBytes
        self.headerHash = headerHash
        self.hasUserTypeFields = hasUserTypeFields
    }
}

private struct ResolvedRegisteredWireType {
    let swiftTypeID: ObjectIdentifier
    let info: RegisteredTypeInfo
    let wireTypeID: TypeId
}

public protocol Serializer {
    static func foryDefault() -> Self
    static var staticTypeId: TypeId { get }

    static var isNullableType: Bool { get }
    static var isReferenceTrackableType: Bool { get }

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
    static func foryCompatibleTypeMetaFields(trackRef: Bool) -> [TypeMetaFieldInfo]
    func foryWriteTypeInfo(_ context: WriteContext) throws
    var foryPrimitiveDataSize: Int? { get }
    func foryWritePrimitiveData(to base: UnsafeMutablePointer<UInt8>, index: inout Int)
}

public extension Serializer {
    @inlinable
    static var isNullableType: Bool { false }

    @inlinable
    static var isReferenceTrackableType: Bool { false }

    @inlinable
    var foryIsNone: Bool { false }

    @inlinable
    func foryWriteTypeInfo(_ context: WriteContext) throws {
        try Self.foryWriteTypeInfo(context)
    }

    @inlinable
    static func foryCompatibleTypeMetaFields(trackRef _: Bool) -> [TypeMetaFieldInfo] {
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
            if refMode == .tracking, Self.isReferenceTrackableType, let object = self as AnyObject? {
                if context.refWriter.tryWriteReference(buffer: context.buffer, object: object) {
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
                context.pushPendingReference(reservedRefID)
                if readTypeInfo {
                    try Self.foryReadTypeInfo(context)
                }
                let value = try Self.foryReadData(context)
                context.finishPendingReferenceIfNeeded(value)
                context.popPendingReference()
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

        let resolved = try resolvedRegisteredWireType(in: context)
        let swiftTypeID = resolved.swiftTypeID
        let info = resolved.info
        let wireTypeID = resolved.wireTypeID
        context.buffer.writeUInt8(UInt8(truncatingIfNeeded: wireTypeID.rawValue))
        switch wireTypeID {
        case .compatibleStruct, .namedCompatibleStruct:
            let cachedTypeMeta = try compatibleTypeMetaEncoding(
                in: context,
                swiftTypeID: swiftTypeID,
                info: info,
                wireTypeID: wireTypeID
            )
            context.writeCompatibleTypeMeta(
                for: Self.self,
                encoding: cachedTypeMeta
            )
        case .namedEnum, .namedStruct, .namedExt, .namedUnion:
            if context.compatible {
                let cachedTypeMeta = try compatibleTypeMetaEncoding(
                    in: context,
                    swiftTypeID: swiftTypeID,
                    info: info,
                    wireTypeID: wireTypeID
                )
                context.writeCompatibleTypeMeta(
                    for: Self.self,
                    encoding: cachedTypeMeta
                )
            } else {
                guard let namespace = info.namespace else {
                    throw ForyError.invalidData("missing namespace metadata for name-registered type")
                }
                try writeMetaString(
                    context: context,
                    value: namespace,
                    encodings: namespaceMetaStringEncodings,
                    encoder: .namespace
                )
                try writeMetaString(
                    context: context,
                    value: info.typeName,
                    encodings: typeNameMetaStringEncodings,
                    encoder: .typeName
                )
            }
        default:
            if !info.registerByName && wireTypeNeedsUserTypeID(wireTypeID) {
                guard let userTypeID = info.userTypeID else {
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

        let resolved = try resolvedRegisteredWireType(in: context)
        let swiftTypeID = resolved.swiftTypeID
        let info = resolved.info
        let expectedWireTypeID = resolved.wireTypeID
        if !isAllowedWireTypeID(
            typeID,
            declaredKind: info.kind,
            registerByName: info.registerByName,
            compatible: context.compatible
        ) {
            throw ForyError.typeMismatch(expected: expectedWireTypeID.rawValue, actual: rawTypeID)
        }

        switch typeID {
        case .compatibleStruct, .namedCompatibleStruct:
            let remoteTypeMeta = try readValidatedCompatibleTypeMeta(
                in: context,
                swiftTypeID: swiftTypeID,
                info: info,
                wireTypeID: typeID
            )
            let localTypeMeta = try compatibleTypeMetaEncoding(
                in: context,
                swiftTypeID: swiftTypeID,
                info: info,
                wireTypeID: typeID
            )
            context.pushCompatibleTypeMeta(
                for: Self.self,
                remoteTypeMeta,
                localTypeMetaHeaderHash: localTypeMeta.headerHash,
                localTypeMetaHasUserTypeFields: localTypeMeta.hasUserTypeFields
            )
        case .namedEnum, .namedStruct, .namedExt, .namedUnion:
            if context.compatible {
                let remoteTypeMeta = try readValidatedCompatibleTypeMeta(
                    in: context,
                    swiftTypeID: swiftTypeID,
                    info: info,
                    wireTypeID: typeID
                )
                if typeID == .namedStruct {
                    let localTypeMeta = try compatibleTypeMetaEncoding(
                        in: context,
                        swiftTypeID: swiftTypeID,
                        info: info,
                        wireTypeID: typeID
                    )
                    context.pushCompatibleTypeMeta(
                        for: Self.self,
                        remoteTypeMeta,
                        localTypeMetaHeaderHash: localTypeMeta.headerHash,
                        localTypeMetaHasUserTypeFields: localTypeMeta.hasUserTypeFields
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
                guard info.registerByName else {
                    throw ForyError.invalidData("received name-registered type info for id-registered local type")
                }
                guard let localNamespace = info.namespace else {
                    throw ForyError.invalidData("missing local namespace metadata for name-registered type")
                }
                if namespace.value != localNamespace.value || typeName.value != info.typeName.value {
                    throw ForyError.invalidData(
                        "type name mismatch: expected \(localNamespace.value)::\(info.typeName.value), got \(namespace.value)::\(typeName.value)"
                    )
                }
            }
        default:
            if !info.registerByName && wireTypeNeedsUserTypeID(typeID) {
                guard let localUserTypeID = info.userTypeID else {
                    throw ForyError.invalidData("missing user type id for id-registered type")
                }
                let remoteUserTypeID = try context.buffer.readVarUInt32()
                if remoteUserTypeID != localUserTypeID {
                    throw ForyError.typeMismatch(expected: localUserTypeID, actual: remoteUserTypeID)
                }
            }
        }
    }

    private static func normalizeBaseKind(_ kind: TypeId) -> TypeId {
        switch kind {
        case .namedEnum:
            return .enumType
        case .compatibleStruct, .namedCompatibleStruct, .namedStruct:
            return .structType
        case .namedExt:
            return .ext
        case .namedUnion, .union:
            return .typedUnion
        default:
            return kind
        }
    }

    private static func namedKind(for baseKind: TypeId, compatible: Bool) -> TypeId {
        switch baseKind {
        case .structType:
            return compatible ? .namedCompatibleStruct : .namedStruct
        case .enumType:
            return .namedEnum
        case .ext:
            return .namedExt
        case .typedUnion:
            return .namedUnion
        default:
            return baseKind
        }
    }

    private static func idKind(for baseKind: TypeId, compatible: Bool) -> TypeId {
        switch baseKind {
        case .structType:
            return compatible ? .compatibleStruct : .structType
        default:
            return baseKind
        }
    }

    private static func resolveWireTypeID(
        declaredKind: TypeId,
        registerByName: Bool,
        compatible: Bool
    ) -> TypeId {
        let baseKind = normalizeBaseKind(declaredKind)
        if registerByName {
            return namedKind(for: baseKind, compatible: compatible)
        }
        return idKind(for: baseKind, compatible: compatible)
    }

    private static func isAllowedWireTypeID(
        _ typeID: TypeId,
        declaredKind: TypeId,
        registerByName: Bool,
        compatible: Bool
    ) -> Bool {
        let baseKind = normalizeBaseKind(declaredKind)
        let expected = resolveWireTypeID(
            declaredKind: declaredKind,
            registerByName: registerByName,
            compatible: compatible
        )
        if typeID == expected {
            return true
        }
        if baseKind == .structType, compatible {
            return typeID == .compatibleStruct ||
                typeID == .namedCompatibleStruct ||
                typeID == .structType ||
                typeID == .namedStruct
        }
        if baseKind == .typedUnion {
            return typeID == .union || (registerByName && typeID == .namedUnion)
        }
        return false
    }

    private static func wireTypeNeedsUserTypeID(_ typeID: TypeId) -> Bool {
        switch typeID {
        case .enumType, .structType, .ext, .typedUnion, .union:
            return true
        default:
            return false
        }
    }

    @inline(__always)
    private static func resolvedRegisteredWireType(
        in context: WriteContext
    ) throws -> ResolvedRegisteredWireType {
        let swiftTypeID = ObjectIdentifier(Self.self)
        let info = try context.requireRegisteredTypeInfo(for: Self.self)
        if let wireTypeID = context.resolvedWireTypeID(for: swiftTypeID) {
            return ResolvedRegisteredWireType(swiftTypeID: swiftTypeID, info: info, wireTypeID: wireTypeID)
        }
        let wireTypeID = resolveWireTypeID(
            declaredKind: info.kind,
            registerByName: info.registerByName,
            compatible: context.compatible
        )
        context.cacheResolvedWireTypeID(wireTypeID, for: swiftTypeID)
        return ResolvedRegisteredWireType(swiftTypeID: swiftTypeID, info: info, wireTypeID: wireTypeID)
    }

    @inline(__always)
    private static func resolvedRegisteredWireType(
        in context: ReadContext
    ) throws -> ResolvedRegisteredWireType {
        let swiftTypeID = ObjectIdentifier(Self.self)
        let info = try context.requireRegisteredTypeInfo(for: Self.self)
        if let wireTypeID = context.resolvedWireTypeID(for: swiftTypeID) {
            return ResolvedRegisteredWireType(swiftTypeID: swiftTypeID, info: info, wireTypeID: wireTypeID)
        }
        let wireTypeID = resolveWireTypeID(
            declaredKind: info.kind,
            registerByName: info.registerByName,
            compatible: context.compatible
        )
        context.cacheResolvedWireTypeID(wireTypeID, for: swiftTypeID)
        return ResolvedRegisteredWireType(swiftTypeID: swiftTypeID, info: info, wireTypeID: wireTypeID)
    }

    @inline(__always)
    private static func compatibleTypeMetaEncoding(
        in context: WriteContext,
        swiftTypeID: ObjectIdentifier,
        info: RegisteredTypeInfo,
        wireTypeID: TypeId
    ) throws -> CompatibleTypeMetaEncoding {
        if let cached = context.compatibleTypeMetaEncoding(for: swiftTypeID, wireTypeID: wireTypeID) {
            return cached
        }
        let encoding = try context.typeResolver.compatibleTypeMetaEncoding(
            swiftTypeID: swiftTypeID,
            wireTypeID: wireTypeID,
            trackRef: context.trackRef
        ) {
            try buildCompatibleTypeMetaEncoding(
                info: info,
                wireTypeID: wireTypeID,
                trackRef: context.trackRef
            )
        }
        context.cacheCompatibleTypeMetaEncoding(encoding, for: swiftTypeID, wireTypeID: wireTypeID)
        return encoding
    }

    @inline(__always)
    private static func compatibleTypeMetaEncoding(
        in context: ReadContext,
        swiftTypeID: ObjectIdentifier,
        info: RegisteredTypeInfo,
        wireTypeID: TypeId
    ) throws -> CompatibleTypeMetaEncoding {
        if let cached = context.compatibleTypeMetaEncoding(for: swiftTypeID, wireTypeID: wireTypeID) {
            return cached
        }
        let encoding = try context.typeResolver.compatibleTypeMetaEncoding(
            swiftTypeID: swiftTypeID,
            wireTypeID: wireTypeID,
            trackRef: context.trackRef
        ) {
            try buildCompatibleTypeMetaEncoding(
                info: info,
                wireTypeID: wireTypeID,
                trackRef: context.trackRef
            )
        }
        context.cacheCompatibleTypeMetaEncoding(encoding, for: swiftTypeID, wireTypeID: wireTypeID)
        return encoding
    }

    @inline(__always)
    private static func readValidatedCompatibleTypeMeta(
        in context: ReadContext,
        swiftTypeID: ObjectIdentifier,
        info: RegisteredTypeInfo,
        wireTypeID: TypeId
    ) throws -> TypeMeta {
        let remoteTypeMeta = try context.readCompatibleTypeMeta()
        if !context.isCompatibleTypeMetaValidationCached(
            for: swiftTypeID,
            wireTypeID: wireTypeID,
            headerHash: remoteTypeMeta.headerHash
        ) {
            try validateCompatibleTypeMeta(
                remoteTypeMeta,
                localInfo: info,
                compatible: context.compatible,
                actualWireTypeID: wireTypeID
            )
            context.cacheCompatibleTypeMetaValidation(
                for: swiftTypeID,
                wireTypeID: wireTypeID,
                headerHash: remoteTypeMeta.headerHash
            )
        }
        return remoteTypeMeta
    }

    private static func buildCompatibleTypeMetaEncoding(
        info: RegisteredTypeInfo,
        wireTypeID: TypeId,
        trackRef: Bool
    ) throws -> CompatibleTypeMetaEncoding {
        let typeMeta = try buildCompatibleTypeMeta(
            info: info,
            wireTypeID: wireTypeID,
            trackRef: trackRef
        )
        let encodedTypeMeta = try typeMeta.encode()
        return CompatibleTypeMetaEncoding(
            bytes: encodedTypeMeta,
            headerHash: try decodeTypeMetaHeaderHash(encodedTypeMeta),
            hasUserTypeFields: hasCompatibleUserTypeField(typeMeta.fields)
        )
    }

    private static func buildCompatibleTypeMeta(
        info: RegisteredTypeInfo,
        wireTypeID: TypeId,
        trackRef: Bool
    ) throws -> TypeMeta {
        let fields = foryCompatibleTypeMetaFields(trackRef: trackRef)
        let hasFieldsMeta = !fields.isEmpty
        if info.registerByName {
            guard let namespace = info.namespace else {
                throw ForyError.invalidData("missing namespace metadata for name-registered type")
            }
            return try TypeMeta(
                typeID: wireTypeID.rawValue,
                userTypeID: nil,
                namespace: namespace,
                typeName: info.typeName,
                registerByName: true,
                fields: fields,
                hasFieldsMeta: hasFieldsMeta
            )
        }

        guard let userTypeID = info.userTypeID else {
            throw ForyError.invalidData("missing user type id metadata for id-registered type")
        }
        return try TypeMeta(
            typeID: wireTypeID.rawValue,
            userTypeID: userTypeID,
            namespace: MetaString.empty(specialChar1: ".", specialChar2: "_"),
            typeName: MetaString.empty(specialChar1: "$", specialChar2: "_"),
            registerByName: false,
            fields: fields,
            hasFieldsMeta: hasFieldsMeta
        )
    }

    private static func decodeTypeMetaHeaderHash(_ encodedTypeMeta: [UInt8]) throws -> UInt64 {
        guard encodedTypeMeta.count >= 8 else {
            throw ForyError.invalidData("encoded compatible type metadata must include an 8-byte header")
        }
        let headerReader = ByteBuffer(bytes: encodedTypeMeta)
        let header = try headerReader.readUInt64()
        return header >> 14
    }

    private static func hasCompatibleUserTypeField(_ fields: [TypeMetaFieldInfo]) -> Bool {
        fields.contains { compatibleFieldNeedsTypeMeta($0.fieldType) }
    }

    private static func compatibleFieldNeedsTypeMeta(_ fieldType: TypeMetaFieldType) -> Bool {
        if let typeID = TypeId(rawValue: fieldType.typeID),
           TypeId.needsTypeInfoForField(typeID) {
            return true
        }
        return fieldType.generics.contains { compatibleFieldNeedsTypeMeta($0) }
    }

    private static func validateCompatibleTypeMeta(
        _ remoteTypeMeta: TypeMeta,
        localInfo: RegisteredTypeInfo,
        compatible: Bool,
        actualWireTypeID: TypeId
    ) throws {
        if remoteTypeMeta.registerByName {
            guard localInfo.registerByName else {
                throw ForyError.invalidData("received name-registered compatible metadata for id-registered local type")
            }
            guard let localNamespace = localInfo.namespace else {
                throw ForyError.invalidData("missing local namespace metadata for name-registered type")
            }
            if remoteTypeMeta.namespace.value != localNamespace.value {
                throw ForyError.invalidData(
                    "namespace mismatch: expected \(localNamespace.value), got \(remoteTypeMeta.namespace.value)"
                )
            }
            if remoteTypeMeta.typeName.value != localInfo.typeName.value {
                throw ForyError.invalidData(
                    "type name mismatch: expected \(localInfo.typeName.value), got \(remoteTypeMeta.typeName.value)"
                )
            }
        } else {
            guard !localInfo.registerByName else {
                throw ForyError.invalidData("received id-registered compatible metadata for name-registered local type")
            }
            guard let remoteUserTypeID = remoteTypeMeta.userTypeID else {
                throw ForyError.invalidData("missing user type id in compatible type metadata")
            }
            guard let localUserTypeID = localInfo.userTypeID else {
                throw ForyError.invalidData("missing local user type id metadata for id-registered type")
            }
            if remoteUserTypeID != localUserTypeID {
                throw ForyError.typeMismatch(expected: localUserTypeID, actual: remoteUserTypeID)
            }
        }

        if let remoteTypeID = remoteTypeMeta.typeID,
           let remoteWireTypeID = TypeId(rawValue: remoteTypeID),
           !isAllowedWireTypeID(
               remoteWireTypeID,
               declaredKind: localInfo.kind,
               registerByName: localInfo.registerByName,
               compatible: compatible
           ) {
            throw ForyError.typeMismatch(expected: actualWireTypeID.rawValue, actual: remoteTypeID)
        }
    }

    private static func writeMetaString(
        context: WriteContext,
        value: MetaString,
        encodings: [MetaStringEncoding],
        encoder: MetaStringEncoder
    ) throws {
        let normalized: MetaString
        if encodings.contains(value.encoding) {
            normalized = value
        } else {
            normalized = try encoder.encode(value.value, allowedEncodings: encodings)
        }

        guard encodings.contains(normalized.encoding) else {
            throw ForyError.encodingError("failed to normalize meta string encoding")
        }

        context.markMetaStringWriteStateUsed()
        let bytes = normalized.bytes
        let assignment = context.metaStringWriteState.assignIndexIfAbsent(for: normalized)
        if assignment.isNew {
            context.buffer.writeVarUInt32(UInt32(bytes.count) << 1)
            if bytes.count > 16 {
                context.buffer.writeInt64(Int64(bitPattern: javaMetaStringHash(metaString: normalized)))
            } else if !bytes.isEmpty {
                context.buffer.writeUInt8(normalized.encoding.rawValue)
            }
            context.buffer.writeBytes(bytes)
        } else {
            context.buffer.writeVarUInt32(((assignment.index + 1) << 1) | 1)
        }
    }

    private static func readMetaString(
        context: ReadContext,
        decoder: MetaStringDecoder,
        encodings: [MetaStringEncoding]
    ) throws -> MetaString {
        context.markMetaStringReadStateUsed()
        let header = try context.buffer.readVarUInt32()
        let length = Int(header >> 1)
        let isRef = (header & 1) == 1
        if isRef {
            let index = length - 1
            guard let cached = context.metaStringReadState.value(at: index) else {
                throw ForyError.invalidData("unknown meta string ref index \(index)")
            }
            return cached
        }

        let value: MetaString
        if length == 0 {
            value = MetaString.empty(
                specialChar1: decoder.specialChar1,
                specialChar2: decoder.specialChar2
            )
        } else {
            let encoding: MetaStringEncoding
            if length > 16 {
                let hash = try context.buffer.readInt64()
                let rawEncoding = UInt8(truncatingIfNeeded: hash & 0xFF)
                guard let resolved = MetaStringEncoding(rawValue: rawEncoding) else {
                    throw ForyError.invalidData("invalid meta string encoding \(rawEncoding)")
                }
                encoding = resolved
            } else {
                let rawEncoding = try context.buffer.readUInt8()
                guard let resolved = MetaStringEncoding(rawValue: rawEncoding) else {
                    throw ForyError.invalidData("invalid meta string encoding \(rawEncoding)")
                }
                encoding = resolved
            }
            guard encodings.contains(encoding) else {
                throw ForyError.invalidData("meta string encoding \(encoding) not allowed in this context")
            }
            let bytes = try context.buffer.readBytes(count: length)
            value = try decoder.decode(bytes: bytes, encoding: encoding)
        }
        context.metaStringReadState.append(value)
        return value
    }

    private static func javaMetaStringHash(metaString: MetaString) -> UInt64 {
        var hash = Int64(bitPattern: MurmurHash3.x64_128(metaString.bytes, seed: 47).0)
        if hash != Int64.min {
            hash = Swift.abs(hash)
        }
        var result = UInt64(bitPattern: hash)
        if result == 0 {
            result &+= 256
        }
        result &= 0xffffffffffffff00
        result |= UInt64(metaString.encoding.rawValue & 0xFF)
        return result
    }
}
