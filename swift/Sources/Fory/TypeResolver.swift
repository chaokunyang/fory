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
            fields: T.foryFieldsInfo(trackRef: trackRef),
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
            fields: T.foryFieldsInfo(trackRef: trackRef),
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
        if header == localTypeInfo.typeDefHeader {
            compatibleTypeInfoByHeader[header] = localTypeInfo
            return localTypeInfo
        }
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

    @inline(__always)
    func builtinTypeInfo(for typeID: TypeId) -> TypeInfo {
        if let cached = builtinTypeInfoByID[typeID] {
            return cached
        }
        let info = TypeInfo(typeID: typeID)
        builtinTypeInfoByID[typeID] = info
        return info
    }

    @inline(__always)
    func requireTypeInfo(userTypeID: UInt32) throws -> TypeInfo {
        guard let typeInfo = byUserTypeID[userTypeID] else {
            throw ForyError.typeNotRegistered("user_type_id=\(userTypeID)")
        }
        return typeInfo
    }

    @inline(__always)
    func requireTypeInfo(namespace: String, typeName: String) throws -> TypeInfo {
        guard let typeInfo = byTypeName[TypeNameKey(namespace: namespace, typeName: typeName)] else {
            throw ForyError.typeNotRegistered("namespace=\(namespace), type=\(typeName)")
        }
        return typeInfo
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
