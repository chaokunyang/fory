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
func normalizeRegisteredTypeID(_ typeID: TypeId) -> TypeId {
    switch typeID {
    case .namedEnum:
        return .enumType
    case .compatibleStruct, .namedCompatibleStruct, .namedStruct:
        return .structType
    case .namedExt:
        return .ext
    case .namedUnion, .union:
        return .typedUnion
    default:
        return typeID
    }
}

@inline(__always)
func namedRegisteredTypeID(for baseTypeID: TypeId, compatible: Bool) -> TypeId {
    switch baseTypeID {
    case .structType:
        return compatible ? .namedCompatibleStruct : .namedStruct
    case .enumType:
        return .namedEnum
    case .ext:
        return .namedExt
    case .typedUnion:
        return .namedUnion
    default:
        return baseTypeID
    }
}

@inline(__always)
func idRegisteredTypeID(for baseTypeID: TypeId, compatible: Bool) -> TypeId {
    switch baseTypeID {
    case .structType:
        return compatible ? .compatibleStruct : .structType
    default:
        return baseTypeID
    }
}

@inline(__always)
func resolveRegisteredWireTypeID(
    declaredTypeID: TypeId,
    registerByName: Bool,
    compatible: Bool
) -> TypeId {
    let baseTypeID = normalizeRegisteredTypeID(declaredTypeID)
    if registerByName {
        return namedRegisteredTypeID(for: baseTypeID, compatible: compatible)
    }
    return idRegisteredTypeID(for: baseTypeID, compatible: compatible)
}

@inline(__always)
func isAllowedRegisteredWireTypeID(
    _ wireTypeID: TypeId,
    declaredTypeID: TypeId,
    registerByName: Bool,
    compatible: Bool
) -> Bool {
    let baseTypeID = normalizeRegisteredTypeID(declaredTypeID)
    let expected = resolveRegisteredWireTypeID(
        declaredTypeID: declaredTypeID,
        registerByName: registerByName,
        compatible: compatible
    )
    if wireTypeID == expected {
        return true
    }
    if baseTypeID == .structType, compatible {
        return wireTypeID == .compatibleStruct ||
            wireTypeID == .namedCompatibleStruct ||
            wireTypeID == .structType ||
            wireTypeID == .namedStruct
    }
    if baseTypeID == .typedUnion {
        return wireTypeID == .union || (registerByName && wireTypeID == .namedUnion)
    }
    return false
}

@inline(__always)
func registeredWireTypeNeedsUserTypeID(_ wireTypeID: TypeId) -> Bool {
    switch wireTypeID {
    case .enumType, .structType, .ext, .typedUnion, .union:
        return true
    default:
        return false
    }
}

@inline(__always)
private func encodedTypeDefHeader(_ bytes: [UInt8]) throws -> UInt64 {
    guard bytes.count >= 8 else {
        throw ForyError.invalidData("encoded compatible type metadata must include an 8-byte header")
    }
    let buffer = ByteBuffer(bytes: bytes)
    return try buffer.readUInt64()
}

@inline(__always)
private func encodedTypeDefHeaderHash(_ bytes: [UInt8]) throws -> UInt64 {
    guard bytes.count >= 8 else {
        throw ForyError.invalidData("encoded compatible type metadata must include an 8-byte header")
    }
    let buffer = ByteBuffer(bytes: bytes)
    let header = try buffer.readUInt64()
    return header >> 14
}

private func fieldNeedsTypeInfo(_ fieldType: TypeMeta.FieldType) -> Bool {
    if let typeID = TypeId(rawValue: fieldType.typeID),
       TypeId.needsTypeInfoForField(typeID) {
        return true
    }
    return fieldType.generics.contains { fieldNeedsTypeInfo($0) }
}

private func encodedTypeDefHasUserTypeFields(_ fields: [TypeMeta.FieldInfo]) -> Bool {
    fields.contains { fieldNeedsTypeInfo($0.fieldType) }
}

public final class TypeInfo: @unchecked Sendable {
    static let uncached = TypeInfo(typeID: .unknown)

    let swiftTypeID: ObjectIdentifier
    let typeID: TypeId
    let userTypeID: UInt32?
    let registerByName: Bool
    let namespace: MetaString
    let typeName: MetaString
    let typeMeta: TypeMeta?
    public let compatibleTypeMeta: TypeMeta?
    let typeDefBytes: [UInt8]?
    let firstTypeDefBytes: [UInt8]?
    let typeDefHeader: UInt64?
    public let typeDefHeaderHash: UInt64?
    public let typeDefHasUserTypeFields: Bool

    private let reader: (ReadContext) throws -> Any
    private let compatibleReader: (ReadContext, TypeInfo) throws -> Any
    private let nativeWireTypeID: TypeId
    private let compatibleWireTypeID: TypeId

    init(
        swiftTypeID: ObjectIdentifier,
        typeID: TypeId,
        userTypeID: UInt32?,
        registerByName: Bool,
        namespace: MetaString,
        typeName: MetaString,
        typeMeta: TypeMeta? = nil,
        compatibleTypeMeta: TypeMeta? = nil,
        typeDefBytes: [UInt8]? = nil,
        firstTypeDefBytes: [UInt8]? = nil,
        typeDefHeader: UInt64? = nil,
        typeDefHeaderHash: UInt64? = nil,
        typeDefHasUserTypeFields: Bool = true,
        reader: @escaping (ReadContext) throws -> Any,
        compatibleReader: @escaping (ReadContext, TypeInfo) throws -> Any
    ) {
        self.swiftTypeID = swiftTypeID
        self.typeID = typeID
        self.userTypeID = userTypeID
        self.registerByName = registerByName
        self.namespace = namespace
        self.typeName = typeName
        self.typeMeta = typeMeta
        self.compatibleTypeMeta = compatibleTypeMeta ?? typeMeta
        self.typeDefBytes = typeDefBytes
        self.firstTypeDefBytes = firstTypeDefBytes
        self.typeDefHeader = typeDefHeader
        self.typeDefHeaderHash = typeDefHeaderHash
        self.typeDefHasUserTypeFields = typeDefHasUserTypeFields
        self.reader = reader
        self.compatibleReader = compatibleReader
        nativeWireTypeID = resolveRegisteredWireTypeID(
            declaredTypeID: typeID,
            registerByName: registerByName,
            compatible: false
        )
        compatibleWireTypeID = resolveRegisteredWireTypeID(
            declaredTypeID: typeID,
            registerByName: registerByName,
            compatible: true
        )
    }

    convenience init(
        swiftTypeID: ObjectIdentifier,
        typeID: TypeId,
        userTypeID: UInt32?,
        registerByName: Bool,
        namespace: MetaString,
        typeName: MetaString,
        fields: [TypeMeta.FieldInfo],
        reader: @escaping (ReadContext) throws -> Any,
        compatibleReader: @escaping (ReadContext, TypeInfo) throws -> Any
    ) throws {
        let compatibleWireTypeID = resolveRegisteredWireTypeID(
            declaredTypeID: typeID,
            registerByName: registerByName,
            compatible: true
        )
        let typeMeta = try TypeMeta(
            typeID: registerByName ? nil : compatibleWireTypeID.rawValue,
            userTypeID: registerByName ? nil : userTypeID,
            namespace: namespace,
            typeName: typeName,
            registerByName: registerByName,
            fields: fields,
            hasFieldsMeta: !fields.isEmpty
        )
        let typeDefBytes = try typeMeta.encode()
        var firstTypeDefBytes = [UInt8]()
        firstTypeDefBytes.reserveCapacity(typeDefBytes.count + 1)
        firstTypeDefBytes.append(0)
        firstTypeDefBytes.append(contentsOf: typeDefBytes)
        let typeDefHeader = try encodedTypeDefHeader(typeDefBytes)
        let typeDefHeaderHash = try encodedTypeDefHeaderHash(typeDefBytes)
        let canonicalTypeMeta = try TypeMeta(
            typeID: registerByName ? nil : compatibleWireTypeID.rawValue,
            userTypeID: registerByName ? nil : userTypeID,
            namespace: namespace,
            typeName: typeName,
            registerByName: registerByName,
            fields: fields,
            hasFieldsMeta: !fields.isEmpty,
            headerHash: typeDefHeaderHash
        )
        self.init(
            swiftTypeID: swiftTypeID,
            typeID: typeID,
            userTypeID: userTypeID,
            registerByName: registerByName,
            namespace: namespace,
            typeName: typeName,
            typeMeta: canonicalTypeMeta,
            compatibleTypeMeta: canonicalTypeMeta,
            typeDefBytes: typeDefBytes,
            firstTypeDefBytes: firstTypeDefBytes,
            typeDefHeader: typeDefHeader,
            typeDefHeaderHash: typeDefHeaderHash,
            typeDefHasUserTypeFields: encodedTypeDefHasUserTypeFields(fields),
            reader: reader,
            compatibleReader: compatibleReader
        )
    }

    convenience init(typeID: TypeId) {
        self.init(
            swiftTypeID: ObjectIdentifier(TypeInfo.self),
            typeID: typeID,
            userTypeID: nil,
            registerByName: false,
            namespace: MetaString.empty(specialChar1: ".", specialChar2: "_"),
            typeName: MetaString.empty(specialChar1: "$", specialChar2: "_"),
            reader: { _ in
                throw ForyError.invalidData("dynamic type \(typeID) uses runtime-only decode path")
            },
            compatibleReader: { _, _ in
                throw ForyError.invalidData("dynamic compatible type \(typeID) uses runtime-only decode path")
            }
        )
    }

    convenience init(dynamic typeInfo: TypeInfo, compatibleTypeMeta: TypeMeta) {
        self.init(
            swiftTypeID: typeInfo.swiftTypeID,
            typeID: typeInfo.typeID,
            userTypeID: typeInfo.userTypeID,
            registerByName: typeInfo.registerByName,
            namespace: typeInfo.namespace,
            typeName: typeInfo.typeName,
            typeMeta: typeInfo.typeMeta,
            compatibleTypeMeta: compatibleTypeMeta,
            typeDefBytes: typeInfo.typeDefBytes,
            firstTypeDefBytes: typeInfo.firstTypeDefBytes,
            typeDefHeader: typeInfo.typeDefHeader,
            typeDefHeaderHash: typeInfo.typeDefHeaderHash,
            typeDefHasUserTypeFields: typeInfo.typeDefHasUserTypeFields,
            reader: typeInfo.reader,
            compatibleReader: typeInfo.compatibleReader
        )
    }

    @inline(__always)
    func matches(
        typeID: TypeId,
        userTypeID: UInt32?,
        registerByName: Bool,
        namespace: String,
        typeName: String
    ) -> Bool {
        self.typeID == typeID &&
            self.userTypeID == userTypeID &&
            self.registerByName == registerByName &&
            self.namespace.value == namespace &&
            self.typeName.value == typeName
    }

    @inline(__always)
    func wireTypeID(compatible: Bool) -> TypeId {
        compatible ? compatibleWireTypeID : nativeWireTypeID
    }

    @inline(__always)
    func read(_ context: ReadContext, compatibleTypeInfo: TypeInfo? = nil) throws -> Any {
        if let compatibleTypeInfo {
            return try compatibleReader(context, compatibleTypeInfo)
        }
        if compatibleTypeMeta !== typeMeta {
            return try compatibleReader(context, self)
        }
        return try reader(context)
    }
}
