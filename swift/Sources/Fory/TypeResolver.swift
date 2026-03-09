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

private struct TypeNameKey: Hashable {
    let namespace: String
    let typeName: String
}

final class TypeResolver {
    private let trackRef: Bool

    private var bySwiftType: [ObjectIdentifier: TypeInfo] = [:]
    private var byUserTypeID: [UInt32: TypeInfo] = [:]
    private var byTypeName: [TypeNameKey: TypeInfo] = [:]
    private var builtinTypeInfoByID: [TypeId: TypeInfo] = [:]
    private var compatibleTypeInfoByHeader: [UInt64: TypeInfo] = [:]

    init(trackRef: Bool = false) {
        self.trackRef = trackRef
    }

    func register<T: Serializer>(_ type: T.Type, id: UInt32) {
        do {
            try registerByID(type, id: id)
        } catch {
            preconditionFailure("conflicting registration for \(type): \(error)")
        }
    }

    private func registerByID<T: Serializer>(_ type: T.Type, id: UInt32) throws {
        let swiftTypeID = ObjectIdentifier(type)
        try validateIDRegistration(key: swiftTypeID, type: type, id: id)

        let typeInfo = try TypeInfo(
            swiftTypeID: swiftTypeID,
            typeID: T.staticTypeId,
            userTypeID: id,
            registerByName: false,
            namespace: MetaString.empty(specialChar1: ".", specialChar2: "_"),
            typeName: MetaString.empty(specialChar1: "$", specialChar2: "_"),
            fields: T.foryCompatibleTypeMetaFields(trackRef: trackRef),
            reader: { context in
                try T.foryRead(context, refMode: .none, readTypeInfo: false)
            },
            compatibleReader: { context, remoteTypeInfo in
                try T.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
            }
        )

        if let existing = bySwiftType[swiftTypeID],
           existing.matches(
               typeID: T.staticTypeId,
               userTypeID: id,
               registerByName: false,
               namespace: "",
               typeName: ""
           ) {
            return
        }

        try store(typeInfo, for: swiftTypeID, userTypeID: id)
    }

    func register<T: Serializer>(_ type: T.Type, namespace: String, typeName: String) throws {
        let namespaceMeta = try MetaStringEncoder.namespace.encode(
            namespace,
            allowedEncodings: namespaceMetaStringEncodings
        )
        let typeNameMeta = try MetaStringEncoder.typeName.encode(
            typeName,
            allowedEncodings: typeNameMetaStringEncodings
        )
        let swiftTypeID = ObjectIdentifier(type)
        try validateNameRegistration(
            key: swiftTypeID,
            type: type,
            namespace: namespace,
            typeName: typeName
        )

        let typeInfo = try TypeInfo(
            swiftTypeID: swiftTypeID,
            typeID: T.staticTypeId,
            userTypeID: nil,
            registerByName: true,
            namespace: namespaceMeta,
            typeName: typeNameMeta,
            fields: T.foryCompatibleTypeMetaFields(trackRef: trackRef),
            reader: { context in
                try T.foryRead(context, refMode: .none, readTypeInfo: false)
            },
            compatibleReader: { context, remoteTypeInfo in
                try T.foryReadCompatibleData(context, remoteTypeInfo: remoteTypeInfo)
            }
        )

        if let existing = bySwiftType[swiftTypeID],
           existing.matches(
               typeID: T.staticTypeId,
               userTypeID: nil,
               registerByName: true,
               namespace: namespace,
               typeName: typeName
           ) {
            return
        }

        try store(typeInfo, for: swiftTypeID, typeNameKey: TypeNameKey(namespace: namespace, typeName: typeName))
    }

    func register<T: Serializer>(_ type: T.Type, name: String) throws {
        let parts = name.components(separatedBy: ".")
        if parts.count <= 1 {
            try register(type, namespace: "", typeName: name)
            return
        }

        let resolvedTypeName = parts[parts.count - 1]
        let resolvedNamespace = parts.dropLast().joined(separator: ".")
        try register(type, namespace: resolvedNamespace, typeName: resolvedTypeName)
    }

    func requireTypeInfo<T: Serializer>(for type: T.Type) throws -> TypeInfo {
        if let info = bySwiftType[ObjectIdentifier(type)] {
            return info
        }
        throw ForyError.typeNotRegistered("\(type) is not registered")
    }

    @inline(__always)
    func compatibleTypeInfo(forHeader header: UInt64) -> TypeInfo? {
        compatibleTypeInfoByHeader[header]
    }

    @inline(__always)
    func cacheCompatibleTypeInfo(_ typeMeta: TypeMeta, forHeader header: UInt64) throws -> TypeInfo {
        if let cached = compatibleTypeInfoByHeader[header] {
            return cached
        }
        let localTypeInfo = try requireCompatibleTypeInfo(for: typeMeta)
        let canonicalTypeMeta: TypeMeta
        if let localTypeMeta = localTypeInfo.typeMeta,
           let remapped = try? typeMeta.assigningFieldIDs(from: localTypeMeta) {
            canonicalTypeMeta = remapped
        } else {
            canonicalTypeMeta = typeMeta
        }
        let compatibleTypeInfo = TypeInfo(dynamic: localTypeInfo, compatibleTypeMeta: canonicalTypeMeta)
        compatibleTypeInfoByHeader[header] = compatibleTypeInfo
        return compatibleTypeInfo
    }

    func readByUserTypeID(_ userTypeID: UInt32, context: ReadContext) throws -> Any {
        try readByUserTypeID(userTypeID, context: context, compatibleTypeInfo: nil)
    }

    func readByUserTypeID(
        _ userTypeID: UInt32,
        context: ReadContext,
        compatibleTypeInfo: TypeInfo?
    ) throws -> Any {
        guard let typeInfo = byUserTypeID[userTypeID] else {
            throw ForyError.typeNotRegistered("user_type_id=\(userTypeID)")
        }
        return try typeInfo.read(context, compatibleTypeInfo: compatibleTypeInfo)
    }

    func readByTypeName(namespace: String, typeName: String, context: ReadContext) throws -> Any {
        try readByTypeName(namespace: namespace, typeName: typeName, context: context, compatibleTypeInfo: nil)
    }

    func readByTypeName(
        namespace: String,
        typeName: String,
        context: ReadContext,
        compatibleTypeInfo: TypeInfo?
    ) throws -> Any {
        guard let typeInfo = byTypeName[TypeNameKey(namespace: namespace, typeName: typeName)] else {
            throw ForyError.typeNotRegistered("namespace=\(namespace), type=\(typeName)")
        }
        return try typeInfo.read(context, compatibleTypeInfo: compatibleTypeInfo)
    }

    func readAnyTypeInfo(context: ReadContext) throws -> TypeInfo {
        let rawTypeID = try context.buffer.readVarUInt32()
        guard let wireTypeID = TypeId(rawValue: rawTypeID) else {
            throw ForyError.invalidData("unknown dynamic type id \(rawTypeID)")
        }

        switch wireTypeID {
        case .compatibleStruct, .namedCompatibleStruct:
            return try compatibleAnyTypeInfo(context: context)
        case .namedStruct, .namedEnum, .namedExt, .namedUnion:
            if context.compatible {
                return try compatibleAnyTypeInfo(context: context)
            }
            let namespace = try readMetaString(
                buffer: context.buffer,
                decoder: MetaStringDecoder.namespace,
                encodings: namespaceMetaStringEncodings
            )
            let typeName = try readMetaString(
                buffer: context.buffer,
                decoder: MetaStringDecoder.typeName,
                encodings: typeNameMetaStringEncodings
            )
            return try requireTypeInfo(namespace: namespace.value, typeName: typeName.value)
        case .structType, .enumType, .ext, .typedUnion, .union:
            let userTypeID = try context.buffer.readVarUInt32()
            return try requireTypeInfo(userTypeID: userTypeID)
        default:
            return builtinTypeInfo(for: wireTypeID)
        }
    }

    private func store(
        _ typeInfo: TypeInfo,
        for swiftTypeID: ObjectIdentifier,
        userTypeID: UInt32? = nil,
        typeNameKey: TypeNameKey? = nil
    ) throws {
        bySwiftType[swiftTypeID] = typeInfo
        if let userTypeID {
            byUserTypeID[userTypeID] = typeInfo
        }
        if let typeNameKey {
            byTypeName[typeNameKey] = typeInfo
        }
        if let typeMeta = typeInfo.typeMeta,
           let typeDefHeader = typeInfo.typeDefHeader {
            compatibleTypeInfoByHeader[typeDefHeader] = TypeInfo(
                dynamic: typeInfo,
                compatibleTypeMeta: typeMeta
            )
        }
    }

    func readAnyValue(typeInfo: TypeInfo, context: ReadContext) throws -> Any {
        try context.enterDynamicAnyDepth()
        defer { context.leaveDynamicAnyDepth() }

        let value: Any
        switch typeInfo.typeID {
        case .bool:
            value = try Bool.foryRead(context, refMode: .none, readTypeInfo: false)
        case .int8:
            value = try Int8.foryRead(context, refMode: .none, readTypeInfo: false)
        case .int16:
            value = try Int16.foryRead(context, refMode: .none, readTypeInfo: false)
        case .int32:
            value = try ForyInt32Fixed.foryRead(context, refMode: .none, readTypeInfo: false)
        case .varint32:
            value = try Int32.foryRead(context, refMode: .none, readTypeInfo: false)
        case .int64:
            value = try ForyInt64Fixed.foryRead(context, refMode: .none, readTypeInfo: false)
        case .varint64:
            value = try Int64.foryRead(context, refMode: .none, readTypeInfo: false)
        case .taggedInt64:
            value = try ForyInt64Tagged.foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint8:
            value = try UInt8.foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint16:
            value = try UInt16.foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint32:
            value = try ForyUInt32Fixed.foryRead(context, refMode: .none, readTypeInfo: false)
        case .varUInt32:
            value = try UInt32.foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint64:
            value = try ForyUInt64Fixed.foryRead(context, refMode: .none, readTypeInfo: false)
        case .varUInt64:
            value = try UInt64.foryRead(context, refMode: .none, readTypeInfo: false)
        case .taggedUInt64:
            value = try ForyUInt64Tagged.foryRead(context, refMode: .none, readTypeInfo: false)
        case .float16:
            value = try Float16.foryRead(context, refMode: .none, readTypeInfo: false)
        case .bfloat16:
            value = try BFloat16.foryRead(context, refMode: .none, readTypeInfo: false)
        case .float32:
            value = try Float.foryRead(context, refMode: .none, readTypeInfo: false)
        case .float64:
            value = try Double.foryRead(context, refMode: .none, readTypeInfo: false)
        case .string:
            value = try String.foryRead(context, refMode: .none, readTypeInfo: false)
        case .duration:
            value = try Duration.foryRead(context, refMode: .none, readTypeInfo: false)
        case .timestamp:
            value = try Date.foryRead(context, refMode: .none, readTypeInfo: false)
        case .date:
            value = try ForyDate.foryRead(context, refMode: .none, readTypeInfo: false)
        case .binary, .uint8Array:
            value = try Data.foryRead(context, refMode: .none, readTypeInfo: false)
        case .boolArray:
            value = try [Bool].foryRead(context, refMode: .none, readTypeInfo: false)
        case .int8Array:
            value = try [Int8].foryRead(context, refMode: .none, readTypeInfo: false)
        case .int16Array:
            value = try [Int16].foryRead(context, refMode: .none, readTypeInfo: false)
        case .int32Array:
            value = try [Int32].foryRead(context, refMode: .none, readTypeInfo: false)
        case .int64Array:
            value = try [Int64].foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint16Array:
            value = try [UInt16].foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint32Array:
            value = try [UInt32].foryRead(context, refMode: .none, readTypeInfo: false)
        case .uint64Array:
            value = try [UInt64].foryRead(context, refMode: .none, readTypeInfo: false)
        case .float16Array:
            value = try [Float16].foryRead(context, refMode: .none, readTypeInfo: false)
        case .bfloat16Array:
            value = try [BFloat16].foryRead(context, refMode: .none, readTypeInfo: false)
        case .float32Array:
            value = try [Float].foryRead(context, refMode: .none, readTypeInfo: false)
        case .float64Array:
            value = try [Double].foryRead(context, refMode: .none, readTypeInfo: false)
        case .array, .list:
            value = try context.readAnyList(refMode: .none) ?? []
        case .set:
            value = try Set<AnyHashable>.foryRead(context, refMode: .none, readTypeInfo: false)
        case .map:
            value = try readDynamicAnyMapValue(context: context)
        case .none:
            value = ForyAnyNullValue()
        default:
            if typeInfo.typeID.isUserTypeKind {
                value = try typeInfo.read(context)
            } else {
                throw ForyError.invalidData("unsupported dynamic type id \(typeInfo.typeID)")
            }
        }
        return value
    }

    @inline(__always)
    private func builtinTypeInfo(for typeID: TypeId) -> TypeInfo {
        if let cached = builtinTypeInfoByID[typeID] {
            return cached
        }
        let info = TypeInfo(typeID: typeID)
        builtinTypeInfoByID[typeID] = info
        return info
    }

    @inline(__always)
    private func requireTypeInfo(userTypeID: UInt32) throws -> TypeInfo {
        guard let typeInfo = byUserTypeID[userTypeID] else {
            throw ForyError.typeNotRegistered("user_type_id=\(userTypeID)")
        }
        return typeInfo
    }

    @inline(__always)
    private func requireTypeInfo(namespace: String, typeName: String) throws -> TypeInfo {
        guard let typeInfo = byTypeName[TypeNameKey(namespace: namespace, typeName: typeName)] else {
            throw ForyError.typeNotRegistered("namespace=\(namespace), type=\(typeName)")
        }
        return typeInfo
    }

    @inline(__always)
    private func compatibleAnyTypeInfo(context: ReadContext) throws -> TypeInfo {
        try context.readCompatibleTypeInfo()
    }

    private func validateIDRegistration<T: Serializer>(
        key: ObjectIdentifier,
        type: T.Type,
        id: UInt32
    ) throws {
        if let existing = bySwiftType[key] {
            if existing.registerByName {
                throw ForyError.invalidData(
                    "\(type) was already registered by name, cannot re-register by id"
                )
            }
            if existing.typeID != T.staticTypeId || existing.userTypeID != id {
                let existingID = existing.userTypeID.map { String($0) } ?? "nil"
                throw ForyError.invalidData(
                    "\(type) registration conflict: existing id=\(existingID), new id=\(id)"
                )
            }
        }

        if let existing = byUserTypeID[id], existing.swiftTypeID != key {
            throw ForyError.invalidData("user type id \(id) is already registered by another type")
        }
    }

    private func validateNameRegistration<T: Serializer>(
        key: ObjectIdentifier,
        type: T.Type,
        namespace: String,
        typeName: String
    ) throws {
        if let existing = bySwiftType[key] {
            if !existing.registerByName {
                throw ForyError.invalidData(
                    "\(type) was already registered by id, cannot re-register by name"
                )
            }
            if existing.typeID != T.staticTypeId ||
                existing.namespace.value != namespace ||
                existing.typeName.value != typeName {
                throw ForyError.invalidData(
                    """
                    \(type) registration conflict: existing name=\(existing.namespace.value)::\(existing.typeName.value), \
                    new name=\(namespace)::\(typeName)
                    """
                )
            }
        }

        let nameKey = TypeNameKey(namespace: namespace, typeName: typeName)
        if let existing = byTypeName[nameKey], existing.swiftTypeID != key {
            throw ForyError.invalidData("type name \(namespace)::\(typeName) is already registered by another type")
        }
    }

    @inline(__always)
    private func requireCompatibleTypeInfo(for typeMeta: TypeMeta) throws -> TypeInfo {
        if typeMeta.registerByName {
            guard let typeInfo = byTypeName[TypeNameKey(namespace: typeMeta.namespace.value, typeName: typeMeta.typeName.value)] else {
                throw ForyError.typeNotRegistered(
                    "namespace=\(typeMeta.namespace.value), type=\(typeMeta.typeName.value)"
                )
            }
            return typeInfo
        }
        if let userTypeID = typeMeta.userTypeID {
            guard let typeInfo = byUserTypeID[userTypeID] else {
                throw ForyError.typeNotRegistered("user_type_id=\(userTypeID)")
            }
            return typeInfo
        }
        throw ForyError.invalidData("missing user type id in compatible dynamic type meta")
    }

}
