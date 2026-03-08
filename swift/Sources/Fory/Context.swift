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

final class CompatibleTypeDefWriteState {
    private var firstTypeID: ObjectIdentifier?
    private var firstTypeIndex: UInt32 = 0
    private var overflowTypeIndexBySwiftType: [ObjectIdentifier: UInt32] = [:]
    private var nextIndex: UInt32 = 0

    init() {}

    @inline(__always)
    func assignIndexIfAbsent(for typeID: ObjectIdentifier) -> (index: UInt32, isNew: Bool) {
        if firstTypeID == typeID {
            return (firstTypeIndex, false)
        }
        if let existing = overflowTypeIndexBySwiftType[typeID] {
            return (existing, false)
        }

        let index = nextIndex
        nextIndex &+= 1

        if firstTypeID == nil {
            firstTypeID = typeID
            firstTypeIndex = index
        } else {
            overflowTypeIndexBySwiftType[typeID] = index
        }
        return (index, true)
    }

    @inline(__always)
    func assignFirstTypeIndex(for typeID: ObjectIdentifier) {
        firstTypeID = typeID
        firstTypeIndex = 0
        nextIndex = 1
    }

    @inline(__always)
    func reset() {
        if firstTypeID != nil {
            firstTypeID = nil
        }
        if firstTypeIndex != 0 {
            firstTypeIndex = 0
        }
        if !overflowTypeIndexBySwiftType.isEmpty {
            overflowTypeIndexBySwiftType.removeAll(keepingCapacity: true)
        }
        if nextIndex != 0 {
            nextIndex = 0
        }
    }
}

final class CompatibleTypeDefReadState {
    private var firstTypeMeta: TypeMeta?
    private var overflowTypeMetas: [TypeMeta] = []

    init() {}

    @inline(__always)
    fileprivate func typeMetaEntry(at index: Int) -> TypeMeta? {
        if index < 0 {
            return nil
        }
        if index == 0 {
            return firstTypeMeta
        }
        let overflowIndex = index - 1
        guard overflowIndex >= 0, overflowIndex < overflowTypeMetas.count else {
            return nil
        }
        return overflowTypeMetas[overflowIndex]
    }

    func typeMeta(at index: Int) -> TypeMeta? {
        typeMetaEntry(at: index)
    }

    @inline(__always)
    fileprivate func storeTypeMetaEntry(_ typeMeta: TypeMeta, at index: Int) throws {
        if index < 0 {
            throw ForyError.invalidData("negative compatible type definition index")
        }
        if index == 0 {
            firstTypeMeta = typeMeta
            return
        }
        let overflowIndex = index - 1
        if overflowIndex == overflowTypeMetas.count {
            overflowTypeMetas.append(typeMeta)
            return
        }
        if overflowIndex < overflowTypeMetas.count {
            overflowTypeMetas[overflowIndex] = typeMeta
            return
        }
        throw ForyError.invalidData(
            "compatible type definition index gap: index=\(index), count=\(overflowTypeMetas.count + (firstTypeMeta == nil ? 0 : 1))"
        )
    }

    func reset() {
        if firstTypeMeta != nil {
            firstTypeMeta = nil
        }
        if !overflowTypeMetas.isEmpty {
            overflowTypeMetas.removeAll(keepingCapacity: true)
        }
    }
}

private let typeMetaSizeMask = 0xFF

private struct MetaStringCacheKey: Hashable {
    let encoding: MetaStringEncoding
    let bytes: [UInt8]
}

final class MetaStringWriteState {
    private var stringIndexByKey: [MetaStringCacheKey: UInt32] = [:]
    private var nextIndex: UInt32 = 0

    init() {}

    func index(for value: MetaString) -> UInt32? {
        stringIndexByKey[MetaStringCacheKey(encoding: value.encoding, bytes: value.bytes)]
    }

    func assignIndexIfAbsent(for value: MetaString) -> (index: UInt32, isNew: Bool) {
        let key = MetaStringCacheKey(encoding: value.encoding, bytes: value.bytes)
        if let existing = stringIndexByKey[key] {
            return (existing, false)
        }
        let index = nextIndex
        nextIndex &+= 1
        stringIndexByKey[key] = index
        return (index, true)
    }

    func reset() {
        if !stringIndexByKey.isEmpty {
            stringIndexByKey.removeAll(keepingCapacity: true)
        }
        if nextIndex != 0 {
            nextIndex = 0
        }
    }
}

final class MetaStringReadState {
    private var values: [MetaString] = []

    init() {}

    func value(at index: Int) -> MetaString? {
        guard index >= 0, index < values.count else {
            return nil
        }
        return values[index]
    }

    func append(_ value: MetaString) {
        values.append(value)
    }

    func reset() {
        if !values.isEmpty {
            values.removeAll(keepingCapacity: true)
        }
    }
}

public final class WriteContext {
    public let buffer: ByteBuffer
    let typeResolver: TypeResolver
    public let trackRef: Bool
    public let compatible: Bool
    public let checkClassVersion: Bool
    public let maxDepth: Int
    public let refWriter: RefWriter
    let typeDefState: CompatibleTypeDefWriteState
    let metaStringWriteState: MetaStringWriteState
    private var typeDefStateUsed = false
    private var metaStringWriteStateUsed = false
    private var dynamicAnyDepth = 0
    private var lastTypeInfoTypeID: ObjectIdentifier?
    private var lastTypeInfo: TypeInfo?

    convenience init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool = false,
        checkClassVersion: Bool = true,
        maxDepth: Int = 5
    ) {
        self.init(
            buffer: buffer,
            typeResolver: typeResolver,
            trackRef: trackRef,
            compatible: compatible,
            checkClassVersion: checkClassVersion,
            maxDepth: maxDepth,
            typeDefState: CompatibleTypeDefWriteState(),
            metaStringWriteState: MetaStringWriteState()
        )
    }

    init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool,
        checkClassVersion: Bool,
        maxDepth: Int,
        typeDefState: CompatibleTypeDefWriteState,
        metaStringWriteState: MetaStringWriteState
    ) {
        self.buffer = buffer
        self.typeResolver = typeResolver
        self.trackRef = trackRef
        self.compatible = compatible
        self.checkClassVersion = checkClassVersion
        self.maxDepth = maxDepth
        self.refWriter = RefWriter()
        self.typeDefState = typeDefState
        self.metaStringWriteState = metaStringWriteState
    }

    @inline(__always)
    func enterDynamicAnyDepth() throws {
        if maxDepth < 0 {
            throw ForyError.invalidData("configured maxDepth \(maxDepth) is negative")
        }
        let nextDepth = dynamicAnyDepth + 1
        if nextDepth > maxDepth {
            throw ForyError.invalidData(
                "dynamic Any nesting depth \(nextDepth) exceeds configured maxDepth \(maxDepth)"
            )
        }
        dynamicAnyDepth = nextDepth
    }

    @inline(__always)
    func typeInfo<T: Serializer>(for type: T.Type) throws -> TypeInfo {
        let typeID = ObjectIdentifier(type)
        if lastTypeInfoTypeID == typeID, let cached = lastTypeInfo {
            return cached
        }
        let info = try typeResolver.requireTypeInfo(for: type)
        lastTypeInfoTypeID = typeID
        lastTypeInfo = info
        return info
    }

    @inline(__always)
    func leaveDynamicAnyDepth() {
        if dynamicAnyDepth > 0 {
            dynamicAnyDepth -= 1
        }
    }

    func writeTypeMeta<T: Serializer>(
        for type: T.Type,
        typeDefBytes: [UInt8]
    ) {
        let typeID = ObjectIdentifier(type)
        if !typeDefStateUsed {
            typeDefStateUsed = true
            typeDefState.assignFirstTypeIndex(for: typeID)
            buffer.writeUInt8(0)
            buffer.writeBytes(typeDefBytes)
            return
        }

        let assignment = typeDefState.assignIndexIfAbsent(for: typeID)
        if assignment.isNew {
            let marker = assignment.index << 1
            if marker < 0x80 {
                buffer.writeUInt8(UInt8(truncatingIfNeeded: marker))
            } else {
                buffer.writeVarUInt32(marker)
            }
            buffer.writeBytes(typeDefBytes)
        } else {
            let marker = (assignment.index << 1) | 1
            if marker < 0x80 {
                buffer.writeUInt8(UInt8(truncatingIfNeeded: marker))
            } else {
                buffer.writeVarUInt32(marker)
            }
        }
    }

    func resetObjectState() {
        if dynamicAnyDepth != 0 {
            dynamicAnyDepth = 0
        }
        if trackRef {
            refWriter.reset()
        }
    }

    @inline(__always)
    func markMetaStringWriteStateUsed() {
        metaStringWriteStateUsed = true
    }

    func reset() {
        resetObjectState()
        if typeDefStateUsed {
            typeDefState.reset()
            typeDefStateUsed = false
        }
        if metaStringWriteStateUsed {
            metaStringWriteState.reset()
            metaStringWriteStateUsed = false
        }
    }
}

private struct PendingRefSlot {
    var refID: UInt32
    var bound: Bool
}

public final class ReadContext {
    public let buffer: ByteBuffer
    let typeResolver: TypeResolver
    public let trackRef: Bool
    public let compatible: Bool
    public let checkClassVersion: Bool
    public let maxCollectionSize: Int
    public let maxBinarySize: Int
    public let maxDepth: Int
    public let refReader: RefReader
    let typeDefState: CompatibleTypeDefReadState
    let metaStringReadState: MetaStringReadState
    private var typeDefStateUsed = false
    private var metaStringReadStateUsed = false
    private var dynamicAnyDepth = 0

    private var pendingRefStack: [PendingRefSlot] = []
    // swiftlint:disable large_tuple
    private var pendingTypeMeta: [
        ObjectIdentifier: (
            typeMeta: TypeMeta,
            matchesSchema: Bool,
            hasUserTypeFields: Bool
        )
    ] = [:]
    private var pendingTypeMetaTypeID: ObjectIdentifier?
    private var pendingTypeMetaValue: (
        typeMeta: TypeMeta,
        matchesSchema: Bool,
        hasUserTypeFields: Bool
    )?
    // swiftlint:enable large_tuple
    private var pendingTypeInfo: [ObjectIdentifier: TypeInfo] = [:]
    private var lastTypeMetaCacheHeader: UInt64?
    private var lastTypeMetaCacheEntry: TypeMeta?
    private var lastTypeInfoTypeID: ObjectIdentifier?
    private var lastTypeInfo: TypeInfo?
    private var lastValidatedTypeID: ObjectIdentifier?
    private var lastValidatedWireTypeID: TypeId?
    private var lastValidatedHeaderHash: UInt64 = 0

    convenience init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool = false,
        checkClassVersion: Bool = true,
        maxCollectionSize: Int = 1_000_000,
        maxBinarySize: Int = 64 * 1024 * 1024,
        maxDepth: Int = 5
    ) {
        self.init(
            buffer: buffer,
            typeResolver: typeResolver,
            trackRef: trackRef,
            compatible: compatible,
            checkClassVersion: checkClassVersion,
            maxCollectionSize: maxCollectionSize,
            maxBinarySize: maxBinarySize,
            maxDepth: maxDepth,
            typeDefState: CompatibleTypeDefReadState(),
            metaStringReadState: MetaStringReadState()
        )
    }

    init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool,
        checkClassVersion: Bool,
        maxCollectionSize: Int,
        maxBinarySize: Int,
        maxDepth: Int,
        typeDefState: CompatibleTypeDefReadState,
        metaStringReadState: MetaStringReadState
    ) {
        self.buffer = buffer
        self.typeResolver = typeResolver
        self.trackRef = trackRef
        self.compatible = compatible
        self.checkClassVersion = checkClassVersion
        self.maxCollectionSize = maxCollectionSize
        self.maxBinarySize = maxBinarySize
        self.maxDepth = maxDepth
        self.refReader = RefReader()
        self.typeDefState = typeDefState
        self.metaStringReadState = metaStringReadState
    }

    @inline(__always)
    func enterDynamicAnyDepth() throws {
        if maxDepth < 0 {
            throw ForyError.invalidData("configured maxDepth \(maxDepth) is negative")
        }
        let nextDepth = dynamicAnyDepth + 1
        if nextDepth > maxDepth {
            throw ForyError.invalidData(
                "dynamic Any nesting depth \(nextDepth) exceeds configured maxDepth \(maxDepth)"
            )
        }
        dynamicAnyDepth = nextDepth
    }

    @inline(__always)
    func leaveDynamicAnyDepth() {
        if dynamicAnyDepth > 0 {
            dynamicAnyDepth -= 1
        }
    }

    @inline(__always)
    func ensureCollectionLength(_ length: Int, label: String) throws {
        if length < 0 {
            throw ForyError.invalidData("\(label) length is negative")
        }
        if length > maxCollectionSize {
            throw ForyError.invalidData(
                "\(label) length \(length) exceeds configured maxCollectionSize \(maxCollectionSize)"
            )
        }
    }

    @inline(__always)
    func ensureBinaryLength(_ length: Int, label: String) throws {
        if length < 0 {
            throw ForyError.invalidData("\(label) size is negative")
        }
        if length > maxBinarySize {
            throw ForyError.invalidData(
                "\(label) size \(length) exceeds configured maxBinarySize \(maxBinarySize)"
            )
        }
    }

    @inline(__always)
    func ensureRemainingBytes(_ byteCount: Int, label: String) throws {
        if byteCount < 0 {
            throw ForyError.invalidData("\(label) size is negative")
        }
        let remainingBytes = buffer.remaining
        if byteCount > remainingBytes {
            throw ForyError.invalidData(
                "\(label) requires \(byteCount) bytes but only \(remainingBytes) remain in buffer"
            )
        }
    }

    @inline(__always)
    func typeInfo<T: Serializer>(for type: T.Type) throws -> TypeInfo {
        let typeID = ObjectIdentifier(type)
        if lastTypeInfoTypeID == typeID, let cached = lastTypeInfo {
            return cached
        }
        let info = try typeResolver.requireTypeInfo(for: type)
        lastTypeInfoTypeID = typeID
        lastTypeInfo = info
        return info
    }

    @inline(__always)
    func isTypeMetaValidationCached(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) -> Bool {
        lastValidatedTypeID == typeID &&
            lastValidatedWireTypeID == wireTypeID &&
            lastValidatedHeaderHash == headerHash
    }

    @inline(__always)
    func cacheTypeMetaValidation(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) {
        lastValidatedTypeID = typeID
        lastValidatedWireTypeID = wireTypeID
        lastValidatedHeaderHash = headerHash
    }

    @inline(__always)
    private func validateTypeMeta(
        _ remoteTypeMeta: TypeMeta,
        localTypeInfo: TypeInfo,
        actualWireTypeID: TypeId
    ) throws {
        if !isTypeMetaValidationCached(
            for: localTypeInfo.swiftTypeID,
            wireTypeID: actualWireTypeID,
            headerHash: remoteTypeMeta.headerHash
        ) {
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

            cacheTypeMetaValidation(
                for: localTypeInfo.swiftTypeID,
                wireTypeID: actualWireTypeID,
                headerHash: remoteTypeMeta.headerHash
            )
        }
    }

    public func pushPendingRef(_ refID: UInt32) {
        pendingRefStack.append(PendingRefSlot(refID: refID, bound: false))
    }

    public func bindPendingRef(_ value: Any) {
        guard var last = pendingRefStack.popLast() else {
            return
        }
        last.bound = true
        refReader.storeRef(value, at: last.refID)
        pendingRefStack.append(last)
    }

    public func finishPendingRefIfNeeded(_ value: Any) {
        guard var last = pendingRefStack.popLast() else {
            return
        }
        if !last.bound {
            refReader.storeRef(value, at: last.refID)
            last.bound = true
        }
    }

    public func popPendingRef() {
        _ = pendingRefStack.popLast()
    }

    func readTypeMeta() throws -> TypeMeta {
        typeDefStateUsed = true
        let indexMarker = try buffer.readVarUInt32()
        let isRef = (indexMarker & 1) == 1
        let index = Int(indexMarker >> 1)
        if isRef {
            guard let typeMeta = typeDefState.typeMeta(at: index) else {
                throw ForyError.invalidData("unknown compatible type definition ref index \(index)")
            }
            return typeMeta
        }

        let typeMetaStart = buffer.getCursor()
        let header = try buffer.readUInt64()
        var bodySize = Int(header & UInt64(typeMetaSizeMask))
        if bodySize == typeMetaSizeMask {
            bodySize += Int(try buffer.readVarUInt32())
        }
        if lastTypeMetaCacheHeader == header,
           let cachedEntry = lastTypeMetaCacheEntry {
            try buffer.skip(bodySize)
            try typeDefState.storeTypeMetaEntry(cachedEntry, at: index)
            return cachedEntry
        }

        if let cached = typeResolver.typeMeta(forHeader: header) {
            try buffer.skip(bodySize)
            lastTypeMetaCacheHeader = header
            lastTypeMetaCacheEntry = cached
            try typeDefState.storeTypeMetaEntry(cached, at: index)
            return cached
        }

        buffer.setCursor(typeMetaStart)
        let decoded = try TypeMeta.decode(buffer)
        let canonicalEntry = typeResolver.cacheTypeMeta(decoded, forHeader: header)

        lastTypeMetaCacheHeader = header
        lastTypeMetaCacheEntry = canonicalEntry
        try typeDefState.storeTypeMetaEntry(canonicalEntry, at: index)
        return canonicalEntry
    }

    @inline(__always)
    func readTypeMeta(for localTypeInfo: TypeInfo) throws -> TypeMeta {
        guard !typeDefStateUsed,
              !localTypeInfo.typeDefHasUserTypeFields,
              let localTypeMeta = localTypeInfo.typeMeta,
              let localHeaderHash = localTypeInfo.typeDefHeaderHash else {
            return try readTypeMeta()
        }

        let typeMetaStart = buffer.getCursor()
        let indexMarker = try buffer.readVarUInt32()
        guard indexMarker == 0 else {
            buffer.setCursor(typeMetaStart)
            return try readTypeMeta()
        }

        let header = try buffer.readUInt64()
        let headerHash = header >> 14
        var bodySize = Int(header & UInt64(typeMetaSizeMask))
        if bodySize == typeMetaSizeMask {
            bodySize += Int(try buffer.readVarUInt32())
        }

        if headerHash == localHeaderHash {
            try buffer.skip(bodySize)
            lastTypeMetaCacheHeader = header
            lastTypeMetaCacheEntry = localTypeMeta
            return localTypeMeta
        }

        buffer.setCursor(typeMetaStart)
        return try readTypeMeta()
    }

    func pushTypeMeta<T: Serializer>(
        for type: T.Type,
        _ typeMeta: TypeMeta
    ) {
        let typeInfo = try? typeInfo(for: type)
        pushTypeMeta(for: type, typeMeta, localTypeInfo: typeInfo)
    }

    @inline(__always)
    func pushTypeMeta<T: Serializer>(
        for type: T.Type,
        _ typeMeta: TypeMeta,
        localTypeInfo: TypeInfo?
    ) {
        let typeID = ObjectIdentifier(type)
        let matchesSchema: Bool
        let hasUserTypeFields: Bool
        if let localTypeInfo,
           let localHeaderHash = localTypeInfo.typeDefHeaderHash {
            matchesSchema = typeMeta.headerHash == localHeaderHash
            hasUserTypeFields = localTypeInfo.typeDefHasUserTypeFields
        } else {
            matchesSchema = false
            hasUserTypeFields = true
        }
        setPendingTypeMeta(
            typeID: typeID,
            value: (
                typeMeta: typeMeta,
                matchesSchema: matchesSchema,
                hasUserTypeFields: hasUserTypeFields
            )
        )
    }

    @inline(__always)
    // swiftlint:disable large_tuple
    public func typeMeta<T: Serializer>(
        for type: T.Type
    ) -> (typeMeta: TypeMeta, matchesSchema: Bool, hasUserTypeFields: Bool)? {
        let typeID = ObjectIdentifier(type)
        if pendingTypeMetaTypeID == typeID {
            return pendingTypeMetaValue
        }
        return pendingTypeMeta[typeID]
    }
    // swiftlint:enable large_tuple

    @inline(__always)
    // swiftlint:disable large_tuple
    private func setPendingTypeMeta(
        typeID: ObjectIdentifier,
        value: (typeMeta: TypeMeta, matchesSchema: Bool, hasUserTypeFields: Bool)
    ) {
        if pendingTypeMetaTypeID == typeID {
            pendingTypeMetaValue = value
            return
        }
        if let oldTypeID = pendingTypeMetaTypeID,
           let oldValue = pendingTypeMetaValue {
            pendingTypeMeta[oldTypeID] = oldValue
        }
        pendingTypeMetaTypeID = typeID
        pendingTypeMetaValue = value
    }
    // swiftlint:enable large_tuple

    func setPendingTypeInfo<T: Serializer>(for type: T.Type, _ typeInfo: TypeInfo) {
        pendingTypeInfo[ObjectIdentifier(type)] = typeInfo
    }

    func pendingTypeInfo<T: Serializer>(for type: T.Type) -> TypeInfo? {
        pendingTypeInfo[ObjectIdentifier(type)]
    }

    func clearPendingTypeInfo<T: Serializer>(for type: T.Type) {
        pendingTypeInfo.removeValue(forKey: ObjectIdentifier(type))
    }

    @inline(__always)
    func markMetaStringReadStateUsed() {
        metaStringReadStateUsed = true
    }

    func resetObjectState() {
        if dynamicAnyDepth != 0 {
            dynamicAnyDepth = 0
        }
        if trackRef {
            refReader.reset()
            if !pendingRefStack.isEmpty {
                pendingRefStack.removeAll(keepingCapacity: true)
            }
        }
        if compatible {
            if pendingTypeMetaTypeID != nil {
                pendingTypeMetaTypeID = nil
                pendingTypeMetaValue = nil
            }
            if !pendingTypeMeta.isEmpty {
                pendingTypeMeta.removeAll(keepingCapacity: true)
            }
        }
        if !pendingTypeInfo.isEmpty {
            pendingTypeInfo.removeAll(keepingCapacity: true)
        }
    }

    func reset() {
        resetObjectState()
        if typeDefStateUsed {
            typeDefState.reset()
            typeDefStateUsed = false
        }
        if metaStringReadStateUsed {
            metaStringReadState.reset()
            metaStringReadStateUsed = false
        }
    }

}

@inline(__always)
private func writeAnyGlobal(
    _ value: Any?,
    context: WriteContext,
    refMode: RefMode,
    writeTypeInfo: Bool,
    hasGenerics: Bool
) throws {
    try writeAny(
        value,
        context: context,
        refMode: refMode,
        writeTypeInfo: writeTypeInfo,
        hasGenerics: hasGenerics
    )
}

@inline(__always)
private func writeAnyListGlobal(
    _ value: [Any]?,
    context: WriteContext,
    refMode: RefMode,
    writeTypeInfo: Bool,
    hasGenerics: Bool
) throws {
    try writeAnyList(
        value,
        context: context,
        refMode: refMode,
        writeTypeInfo: writeTypeInfo,
        hasGenerics: hasGenerics
    )
}

@inline(__always)
private func writeStringAnyMapGlobal(
    _ value: [String: Any]?,
    context: WriteContext,
    refMode: RefMode,
    writeTypeInfo: Bool,
    hasGenerics: Bool
) throws {
    try writeStringAnyMap(
        value,
        context: context,
        refMode: refMode,
        writeTypeInfo: writeTypeInfo,
        hasGenerics: hasGenerics
    )
}

@inline(__always)
private func writeInt32AnyMapGlobal(
    _ value: [Int32: Any]?,
    context: WriteContext,
    refMode: RefMode,
    writeTypeInfo: Bool,
    hasGenerics: Bool
) throws {
    try writeInt32AnyMap(
        value,
        context: context,
        refMode: refMode,
        writeTypeInfo: writeTypeInfo,
        hasGenerics: hasGenerics
    )
}

@inline(__always)
private func writeAnyHashableAnyMapGlobal(
    _ value: [AnyHashable: Any]?,
    context: WriteContext,
    refMode: RefMode,
    writeTypeInfo: Bool,
    hasGenerics: Bool
) throws {
    try writeAnyHashableAnyMap(
        value,
        context: context,
        refMode: refMode,
        writeTypeInfo: writeTypeInfo,
        hasGenerics: hasGenerics
    )
}

@inline(__always)
private func readAnyGlobal(
    context: ReadContext,
    refMode: RefMode,
    readTypeInfo: Bool
) throws -> Any? {
    try readAny(
        context: context,
        refMode: refMode,
        readTypeInfo: readTypeInfo
    )
}

@inline(__always)
private func readAnyListGlobal(
    context: ReadContext,
    refMode: RefMode,
    readTypeInfo: Bool
) throws -> [Any]? {
    try readAnyList(
        context: context,
        refMode: refMode,
        readTypeInfo: readTypeInfo
    )
}

@inline(__always)
private func readStringAnyMapGlobal(
    context: ReadContext,
    refMode: RefMode,
    readTypeInfo: Bool
) throws -> [String: Any]? {
    try readStringAnyMap(
        context: context,
        refMode: refMode,
        readTypeInfo: readTypeInfo
    )
}

@inline(__always)
private func readInt32AnyMapGlobal(
    context: ReadContext,
    refMode: RefMode,
    readTypeInfo: Bool
) throws -> [Int32: Any]? {
    try readInt32AnyMap(
        context: context,
        refMode: refMode,
        readTypeInfo: readTypeInfo
    )
}

@inline(__always)
private func readAnyHashableAnyMapGlobal(
    context: ReadContext,
    refMode: RefMode,
    readTypeInfo: Bool
) throws -> [AnyHashable: Any]? {
    try readAnyHashableAnyMap(
        context: context,
        refMode: refMode,
        readTypeInfo: readTypeInfo
    )
}

public extension WriteContext {
    func writeAny(
        _ value: Any?,
        refMode: RefMode,
        writeTypeInfo: Bool = true,
        hasGenerics: Bool = false
    ) throws {
        try writeAnyGlobal(
            value,
            context: self,
            refMode: refMode,
            writeTypeInfo: writeTypeInfo,
            hasGenerics: hasGenerics
        )
    }

    func writeAnyList(
        _ value: [Any]?,
        refMode: RefMode,
        writeTypeInfo: Bool = false,
        hasGenerics: Bool = true
    ) throws {
        try writeAnyListGlobal(
            value,
            context: self,
            refMode: refMode,
            writeTypeInfo: writeTypeInfo,
            hasGenerics: hasGenerics
        )
    }

    func writeStringAnyMap(
        _ value: [String: Any]?,
        refMode: RefMode,
        writeTypeInfo: Bool = false,
        hasGenerics: Bool = true
    ) throws {
        try writeStringAnyMapGlobal(
            value,
            context: self,
            refMode: refMode,
            writeTypeInfo: writeTypeInfo,
            hasGenerics: hasGenerics
        )
    }

    func writeInt32AnyMap(
        _ value: [Int32: Any]?,
        refMode: RefMode,
        writeTypeInfo: Bool = false,
        hasGenerics: Bool = true
    ) throws {
        try writeInt32AnyMapGlobal(
            value,
            context: self,
            refMode: refMode,
            writeTypeInfo: writeTypeInfo,
            hasGenerics: hasGenerics
        )
    }

    func writeAnyHashableAnyMap(
        _ value: [AnyHashable: Any]?,
        refMode: RefMode,
        writeTypeInfo: Bool = false,
        hasGenerics: Bool = true
    ) throws {
        try writeAnyHashableAnyMapGlobal(
            value,
            context: self,
            refMode: refMode,
            writeTypeInfo: writeTypeInfo,
            hasGenerics: hasGenerics
        )
    }
}

public extension ReadContext {
    func readAny(
        refMode: RefMode,
        readTypeInfo: Bool = true
    ) throws -> Any? {
        try readAnyGlobal(
            context: self,
            refMode: refMode,
            readTypeInfo: readTypeInfo
        )
    }

    func readAnyList(
        refMode: RefMode,
        readTypeInfo: Bool = false
    ) throws -> [Any]? {
        try readAnyListGlobal(
            context: self,
            refMode: refMode,
            readTypeInfo: readTypeInfo
        )
    }

    func readStringAnyMap(
        refMode: RefMode,
        readTypeInfo: Bool = false
    ) throws -> [String: Any]? {
        try readStringAnyMapGlobal(
            context: self,
            refMode: refMode,
            readTypeInfo: readTypeInfo
        )
    }

    func readInt32AnyMap(
        refMode: RefMode,
        readTypeInfo: Bool = false
    ) throws -> [Int32: Any]? {
        try readInt32AnyMapGlobal(
            context: self,
            refMode: refMode,
            readTypeInfo: readTypeInfo
        )
    }

    func readAnyHashableAnyMap(
        refMode: RefMode,
        readTypeInfo: Bool = false
    ) throws -> [AnyHashable: Any]? {
        try readAnyHashableAnyMapGlobal(
            context: self,
            refMode: refMode,
            readTypeInfo: readTypeInfo
        )
    }
}
