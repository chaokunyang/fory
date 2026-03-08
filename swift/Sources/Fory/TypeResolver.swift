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

struct TypeDef {
    let bytes: [UInt8]
    let rootBytes: [UInt8]
    let headerHash: UInt64
    let hasUserTypeFields: Bool
}

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

private func typeDefHeaderHash(_ bytes: [UInt8]) throws -> UInt64 {
    guard bytes.count >= 8 else {
        throw ForyError.invalidData("encoded compatible type metadata must include an 8-byte header")
    }
    let headerReader = ByteBuffer(bytes: bytes)
    let header = try headerReader.readUInt64()
    return header >> 14
}

private func typeDefHasUserTypeFields(_ fields: [TypeMeta.FieldInfo]) -> Bool {
    fields.contains { typeDefFieldNeedsTypeInfo($0.fieldType) }
}

private func typeDefFieldNeedsTypeInfo(_ fieldType: TypeMeta.FieldType) -> Bool {
    if let typeID = TypeId(rawValue: fieldType.typeID),
       TypeId.needsTypeInfoForField(typeID) {
        return true
    }
    return fieldType.generics.contains { typeDefFieldNeedsTypeInfo($0) }
}

final class TypeInfo: @unchecked Sendable {
    let swiftTypeID: ObjectIdentifier
    let typeID: TypeId
    let userTypeID: UInt32?
    let registerByName: Bool
    let namespace: MetaString
    let typeName: MetaString
    let compatibleTypeMeta: TypeMeta?

    private let fields: [TypeMeta.FieldInfo]
    private let reader: (ReadContext) throws -> Any
    private let compatibleReader: (ReadContext, TypeMeta) throws -> Any
    private let nativeWireTypeIDValue: TypeId
    private let compatibleWireTypeIDValue: TypeId
    private var compatibleRootBytesValue: [UInt8]?
    private var compatibleTypeDefValue: TypeDef?
    private var typeDefByWireType: [TypeId: TypeDef] = [:]

    init(
        swiftTypeID: ObjectIdentifier,
        typeID: TypeId,
        userTypeID: UInt32?,
        registerByName: Bool,
        namespace: MetaString,
        typeName: MetaString,
        compatibleTypeMeta: TypeMeta? = nil,
        fields: [TypeMeta.FieldInfo],
        reader: @escaping (ReadContext) throws -> Any,
        compatibleReader: @escaping (ReadContext, TypeMeta) throws -> Any
    ) {
        self.swiftTypeID = swiftTypeID
        self.typeID = typeID
        self.userTypeID = userTypeID
        self.registerByName = registerByName
        self.namespace = namespace
        self.typeName = typeName
        self.compatibleTypeMeta = compatibleTypeMeta
        self.fields = fields
        self.reader = reader
        self.compatibleReader = compatibleReader
        nativeWireTypeIDValue = resolveRegisteredWireTypeID(
            declaredTypeID: typeID,
            registerByName: registerByName,
            compatible: false
        )
        compatibleWireTypeIDValue = resolveRegisteredWireTypeID(
            declaredTypeID: typeID,
            registerByName: registerByName,
            compatible: true
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
            fields: [],
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
            compatibleTypeMeta: compatibleTypeMeta,
            fields: typeInfo.fields,
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
        compatible ? compatibleWireTypeIDValue : nativeWireTypeIDValue
    }

    @inline(__always)
    func read(_ context: ReadContext, compatibleTypeMeta: TypeMeta? = nil) throws -> Any {
        if let compatibleTypeMeta = compatibleTypeMeta ?? self.compatibleTypeMeta {
            return try compatibleReader(context, compatibleTypeMeta)
        }
        return try reader(context)
    }

    @inline(__always)
    func compatibleTypeDef() throws -> TypeDef {
        if let cached = compatibleTypeDefValue {
            return cached
        }
        let typeDef = try buildTypeDef(wireTypeID: compatibleWireTypeIDValue)
        if (compatibleWireTypeIDValue == .compatibleStruct || compatibleWireTypeIDValue == .namedCompatibleStruct) &&
            !typeDef.hasUserTypeFields {
            compatibleRootBytesValue = typeDef.rootBytes
        }
        compatibleTypeDefValue = typeDef
        return typeDef
    }

    @inline(__always)
    func warmCompatibleTypeDef() throws {
        _ = try compatibleTypeDef()
    }

    @inline(__always)
    func compatibleRootBytes() -> [UInt8]? {
        compatibleRootBytesValue
    }

    private func buildTypeDef(wireTypeID: TypeId) throws -> TypeDef {
        let hasFieldsMeta = !fields.isEmpty
        let typeMeta: TypeMeta
        if registerByName {
            typeMeta = try TypeMeta(
                typeID: wireTypeID.rawValue,
                userTypeID: nil,
                namespace: namespace,
                typeName: typeName,
                registerByName: true,
                fields: fields,
                hasFieldsMeta: hasFieldsMeta
            )
        } else {
            guard let userTypeID else {
                throw ForyError.invalidData("missing user type id metadata for id-registered type")
            }
            typeMeta = try TypeMeta(
                typeID: wireTypeID.rawValue,
                userTypeID: userTypeID,
                namespace: namespace,
                typeName: typeName,
                registerByName: false,
                fields: fields,
                hasFieldsMeta: hasFieldsMeta
            )
        }

        let bytes = try typeMeta.encode()
        var rootBytes: [UInt8] = []
        rootBytes.reserveCapacity(bytes.count + 2)
        rootBytes.append(UInt8(truncatingIfNeeded: wireTypeID.rawValue))
        rootBytes.append(0)
        rootBytes.append(contentsOf: bytes)
        let typeDef = try TypeDef(
            bytes: bytes,
            rootBytes: rootBytes,
            headerHash: typeDefHeaderHash(bytes),
            hasUserTypeFields: typeDefHasUserTypeFields(typeMeta.fields)
        )
        return typeDef
    }

    @inline(__always)
    func typeDef(
        wireTypeID: TypeId
    ) throws -> TypeDef {
        if wireTypeID == compatibleWireTypeIDValue {
            return try compatibleTypeDef()
        }
        if let cached = typeDefByWireType[wireTypeID] {
            return cached
        }

        let typeDef = try buildTypeDef(wireTypeID: wireTypeID)
        typeDefByWireType[wireTypeID] = typeDef
        return typeDef
    }
}

final class TypeResolver {
    private let trackRef: Bool

    private var bySwiftType: [ObjectIdentifier: TypeInfo] = [:]
    private var byUserTypeID: [UInt32: TypeInfo] = [:]
    private var byTypeName: [TypeNameKey: TypeInfo] = [:]
    private var builtinTypeInfoByID: [TypeId: TypeInfo] = [:]
    private var compatibleTypeMetaByHeader: [UInt64: TypeMeta] = [:]

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

        let typeInfo = TypeInfo(
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
            compatibleReader: { context, typeMeta in
                context.pushCompatibleTypeMeta(for: T.self, typeMeta)
                return try T.foryRead(context, refMode: .none, readTypeInfo: false)
            }
        )
        try typeInfo.warmCompatibleTypeDef()

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

        bySwiftType[swiftTypeID] = typeInfo
        byUserTypeID[id] = typeInfo
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

        let typeInfo = TypeInfo(
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
            compatibleReader: { context, typeMeta in
                context.pushCompatibleTypeMeta(for: T.self, typeMeta)
                return try T.foryRead(context, refMode: .none, readTypeInfo: false)
            }
        )
        try typeInfo.warmCompatibleTypeDef()

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

        bySwiftType[swiftTypeID] = typeInfo
        byTypeName[TypeNameKey(namespace: namespace, typeName: typeName)] = typeInfo
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
    func compatibleTypeMeta(forHeader header: UInt64) -> TypeMeta? {
        compatibleTypeMetaByHeader[header]
    }

    @inline(__always)
    func cacheCompatibleTypeMeta(_ typeMeta: TypeMeta, forHeader header: UInt64) -> TypeMeta {
        if let cached = compatibleTypeMetaByHeader[header] {
            return cached
        }
        compatibleTypeMetaByHeader[header] = typeMeta
        return typeMeta
    }

    func readByUserTypeID(_ userTypeID: UInt32, context: ReadContext) throws -> Any {
        try readByUserTypeID(userTypeID, context: context, compatibleTypeMeta: nil)
    }

    func readByUserTypeID(
        _ userTypeID: UInt32,
        context: ReadContext,
        compatibleTypeMeta: TypeMeta?
    ) throws -> Any {
        guard let typeInfo = byUserTypeID[userTypeID] else {
            throw ForyError.typeNotRegistered("user_type_id=\(userTypeID)")
        }
        return try typeInfo.read(context, compatibleTypeMeta: compatibleTypeMeta)
    }

    func readByTypeName(namespace: String, typeName: String, context: ReadContext) throws -> Any {
        try readByTypeName(namespace: namespace, typeName: typeName, context: context, compatibleTypeMeta: nil)
    }

    func readByTypeName(
        namespace: String,
        typeName: String,
        context: ReadContext,
        compatibleTypeMeta: TypeMeta?
    ) throws -> Any {
        guard let typeInfo = byTypeName[TypeNameKey(namespace: namespace, typeName: typeName)] else {
            throw ForyError.typeNotRegistered("namespace=\(namespace), type=\(typeName)")
        }
        return try typeInfo.read(context, compatibleTypeMeta: compatibleTypeMeta)
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
            let namespace = try Self.readMetaString(
                buffer: context.buffer,
                decoder: .namespace,
                encodings: namespaceMetaStringEncodings
            )
            let typeName = try Self.readMetaString(
                buffer: context.buffer,
                decoder: .typeName,
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
        let typeMeta = try context.readCompatibleTypeMeta()
        if typeMeta.registerByName {
            let local = try requireTypeInfo(
                namespace: typeMeta.namespace.value,
                typeName: typeMeta.typeName.value
            )
            return TypeInfo(dynamic: local, compatibleTypeMeta: typeMeta)
        }
        guard let userTypeID = typeMeta.userTypeID else {
            throw ForyError.invalidData("missing user type id in compatible dynamic type meta")
        }
        let local = try requireTypeInfo(userTypeID: userTypeID)
        return TypeInfo(dynamic: local, compatibleTypeMeta: typeMeta)
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

    private static func readMetaString(
        buffer: ByteBuffer,
        decoder: MetaStringDecoder,
        encodings: [MetaStringEncoding]
    ) throws -> MetaString {
        let header = try buffer.readUInt8()
        let encodingIndex = Int(header & 0b11)
        guard encodingIndex < encodings.count else {
            throw ForyError.invalidData("invalid meta string encoding index")
        }

        var length = Int(header >> 2)
        if length >= 0b11_1111 {
            length = 0b11_1111 + Int(try buffer.readVarUInt32())
        }
        let bytes = try buffer.readBytes(count: length)
        return try decoder.decode(bytes: bytes, encoding: encodings[encodingIndex])
    }
}
