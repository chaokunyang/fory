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

private let compatibleTypeMetaSizeMask = 0xFF

private struct CompatibleRootTypeInfoEntry {
    let prefixBytes: [UInt8]
    let bodySize: Int
    let typeMetaIndex: Int
    let requiresTypeMetaState: Bool
}

private struct CompatibleTypeMetaEntry {
    let typeMeta: TypeMeta
    let matchesLocalSchema: Bool
    let localHasUserTypeFields: Bool
}

private struct MetaStringCacheKey: Hashable {
    let encoding: MetaStringEncoding
    let bytes: [UInt8]
}

private struct InternedUTF8StringEntry {
    let bytes: [UInt8]
    let value: String
}

private let utf8InternMaxEntryCount = 512
private let utf8InternMaxByteCount = 32 * 1024
private let utf8InternMaxStringLength = 64

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
    let compatibleTypeDefState: CompatibleTypeDefWriteState
    let metaStringWriteState: MetaStringWriteState
    private var compatibleTypeDefStateUsed = false
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
            compatibleTypeDefState: CompatibleTypeDefWriteState(),
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
        compatibleTypeDefState: CompatibleTypeDefWriteState,
        metaStringWriteState: MetaStringWriteState
    ) {
        self.buffer = buffer
        self.typeResolver = typeResolver
        self.trackRef = trackRef
        self.compatible = compatible
        self.checkClassVersion = checkClassVersion
        self.maxDepth = maxDepth
        self.refWriter = RefWriter()
        self.compatibleTypeDefState = compatibleTypeDefState
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

    func writeCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        typeDefBytes: [UInt8]
    ) {
        let typeID = ObjectIdentifier(type)
        if !compatibleTypeDefStateUsed {
            compatibleTypeDefStateUsed = true
            compatibleTypeDefState.assignFirstTypeIndex(for: typeID)
            buffer.writeUInt8(0)
            buffer.writeBytes(typeDefBytes)
            return
        }

        let assignment = compatibleTypeDefState.assignIndexIfAbsent(for: typeID)
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
        if compatibleTypeDefStateUsed {
            compatibleTypeDefState.reset()
            compatibleTypeDefStateUsed = false
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
    let compatibleTypeDefState: CompatibleTypeDefReadState
    let metaStringReadState: MetaStringReadState
    private var compatibleTypeDefStateUsed = false
    private var metaStringReadStateUsed = false
    private var dynamicAnyDepth = 0

    private var pendingRefStack: [PendingRefSlot] = []
    private var pendingCompatibleTypeMeta: [ObjectIdentifier: CompatibleTypeMetaEntry] = [:]
    private var pendingCompatibleTypeMetaTypeID: ObjectIdentifier?
    private var pendingCompatibleTypeMetaValue: CompatibleTypeMetaEntry?
    private var pendingTypeInfo: [ObjectIdentifier: TypeInfo] = [:]
    private var utf8StringInternCache: [UInt64: [InternedUTF8StringEntry]] = [:]
    private var utf8StringInternEntryCount = 0
    private var utf8StringInternByteCount = 0
    private var lastCompatibleTypeMetaCacheHeader: UInt64?
    private var lastCompatibleTypeMetaCacheEntry: TypeMeta?
    private var lastTypeInfoTypeID: ObjectIdentifier?
    private var lastTypeInfo: TypeInfo?
    private var lastValidatedCompatibleTypeID: ObjectIdentifier?
    private var lastValidatedCompatibleWireTypeID: TypeId?
    private var lastValidatedCompatibleHeaderHash: UInt64 = 0
    private var cachedCompatibleRootTypeInfoTypeID: ObjectIdentifier?
    private var cachedCompatibleRootTypeInfoEntry: CompatibleRootTypeInfoEntry?
    private var cachedCompatibleRootTypeMeta: CompatibleTypeMetaEntry?

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
            compatibleTypeDefState: CompatibleTypeDefReadState(),
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
        compatibleTypeDefState: CompatibleTypeDefReadState,
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
        self.compatibleTypeDefState = compatibleTypeDefState
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
    func isCompatibleTypeMetaValidationCached(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) -> Bool {
        lastValidatedCompatibleTypeID == typeID &&
            lastValidatedCompatibleWireTypeID == wireTypeID &&
            lastValidatedCompatibleHeaderHash == headerHash
    }

    @inline(__always)
    func cacheCompatibleTypeMetaValidation(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) {
        lastValidatedCompatibleTypeID = typeID
        lastValidatedCompatibleWireTypeID = wireTypeID
        lastValidatedCompatibleHeaderHash = headerHash
    }

    @inline(__always)
    func usesCompatibleTypeDefState() -> Bool {
        compatibleTypeDefStateUsed
    }

    @inline(__always)
    func reuseCachedCompatibleRootTypeInfo<T: Serializer>(
        for type: T.Type
    ) -> Bool {
        guard compatible, !compatibleTypeDefStateUsed else {
            return false
        }
        let typeID = ObjectIdentifier(type)
        guard cachedCompatibleRootTypeInfoTypeID == typeID,
              let entry = cachedCompatibleRootTypeInfoEntry,
              let typeMetaEntry = cachedCompatibleRootTypeMeta else {
            return false
        }
        let start = buffer.getCursor()
        let prefixBytes = entry.prefixBytes
        let prefixLength = prefixBytes.count
        let end = start + prefixLength + entry.bodySize
        guard end <= buffer.count else {
            return false
        }

        if !buffer.matchesBytes(start: start, bytes: prefixBytes) {
            return false
        }

        buffer.setCursor(end)
        if entry.requiresTypeMetaState {
            do {
                try compatibleTypeDefState.storeTypeMetaEntry(typeMetaEntry.typeMeta, at: entry.typeMetaIndex)
                compatibleTypeDefStateUsed = true
            } catch {
                buffer.setCursor(start)
                return false
            }
        }
        setPendingCompatibleTypeMeta(typeID: typeID, value: typeMetaEntry)
        return true
    }

    @inline(__always)
    func cacheCompatibleRootTypeInfo<T: Serializer>(
        for type: T.Type,
        bytes: [UInt8],
        remoteTypeMeta: TypeMeta?
    ) {
        guard compatible else {
            return
        }
        cachedCompatibleRootTypeInfoTypeID = ObjectIdentifier(type)
        let typeMetaEntry = remoteTypeMeta.map { typeMeta in
            CompatibleTypeMetaEntry(
                typeMeta: typeMeta,
                matchesLocalSchema: pendingCompatibleTypeMetaMatchesLocalSchema(for: type),
                localHasUserTypeFields: pendingCompatibleTypeMetaHasUserTypeFields(for: type)
            )
        }
        cachedCompatibleRootTypeInfoEntry = Self.parseCompatibleRootTypeInfoEntry(
            bytes,
            requiresTypeMetaState: typeMetaEntry.map { !$0.matchesLocalSchema || $0.localHasUserTypeFields } ?? true
        )
        cachedCompatibleRootTypeMeta = typeMetaEntry
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

    func readCompatibleTypeMeta() throws -> TypeMeta {
        compatibleTypeDefStateUsed = true
        let indexMarker = try buffer.readVarUInt32()
        let isRef = (indexMarker & 1) == 1
        let index = Int(indexMarker >> 1)
        if isRef {
            guard let typeMeta = compatibleTypeDefState.typeMeta(at: index) else {
                throw ForyError.invalidData("unknown compatible type definition ref index \(index)")
            }
            return typeMeta
        }

        let typeMetaStart = buffer.getCursor()
        let header = try buffer.readUInt64()
        var bodySize = Int(header & UInt64(compatibleTypeMetaSizeMask))
        if bodySize == compatibleTypeMetaSizeMask {
            bodySize += Int(try buffer.readVarUInt32())
        }
        if lastCompatibleTypeMetaCacheHeader == header,
           let cachedEntry = lastCompatibleTypeMetaCacheEntry {
            try buffer.skip(bodySize)
            try compatibleTypeDefState.storeTypeMetaEntry(cachedEntry, at: index)
            return cachedEntry
        }

        if let cached = typeResolver.compatibleTypeMeta(forHeader: header) {
            try buffer.skip(bodySize)
            lastCompatibleTypeMetaCacheHeader = header
            lastCompatibleTypeMetaCacheEntry = cached
            try compatibleTypeDefState.storeTypeMetaEntry(cached, at: index)
            return cached
        }

        buffer.setCursor(typeMetaStart)
        let decoded = try TypeMeta.decode(buffer)
        let canonicalEntry = typeResolver.cacheCompatibleTypeMeta(decoded, forHeader: header)

        lastCompatibleTypeMetaCacheHeader = header
        lastCompatibleTypeMetaCacheEntry = canonicalEntry
        try compatibleTypeDefState.storeTypeMetaEntry(canonicalEntry, at: index)
        return canonicalEntry
    }

    func pushCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        _ typeMeta: TypeMeta
    ) {
        let typeInfo = try? typeInfo(for: type)
        pushCompatibleTypeMeta(for: type, typeMeta, localTypeInfo: typeInfo)
    }

    @inline(__always)
    func pushCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        _ typeMeta: TypeMeta,
        localTypeInfo: TypeInfo?
    ) {
        let typeID = ObjectIdentifier(type)
        let matchesLocalSchema: Bool
        let localHasUserTypeFields: Bool
        if let localTypeInfo,
           let localHeaderHash = localTypeInfo.typeDefHeaderHash {
            matchesLocalSchema = typeMeta.headerHash == localHeaderHash
            localHasUserTypeFields = localTypeInfo.typeDefHasUserTypeFields
        } else {
            matchesLocalSchema = false
            localHasUserTypeFields = true
        }
        setPendingCompatibleTypeMeta(
            typeID: typeID,
            value: CompatibleTypeMetaEntry(
                typeMeta: typeMeta,
                matchesLocalSchema: matchesLocalSchema,
                localHasUserTypeFields: localHasUserTypeFields
            )
        )
    }

    // swiftlint:disable large_tuple
    @inline(__always)
    public func compatibleTypeMeta<T: Serializer>(
        for type: T.Type
    ) -> (typeMeta: TypeMeta, matchesLocalSchema: Bool, localHasUserTypeFields: Bool)? {
        let typeID = ObjectIdentifier(type)
        if pendingCompatibleTypeMetaTypeID == typeID {
            guard let entry = pendingCompatibleTypeMetaValue else {
                return nil
            }
            return (
                typeMeta: entry.typeMeta,
                matchesLocalSchema: entry.matchesLocalSchema,
                localHasUserTypeFields: entry.localHasUserTypeFields
            )
        }
        guard let entry = pendingCompatibleTypeMeta[typeID] else {
            return nil
        }
        return (
            typeMeta: entry.typeMeta,
            matchesLocalSchema: entry.matchesLocalSchema,
            localHasUserTypeFields: entry.localHasUserTypeFields
        )
    }
    // swiftlint:enable large_tuple

    @inline(__always)
    private func setPendingCompatibleTypeMeta(typeID: ObjectIdentifier, value: CompatibleTypeMetaEntry) {
        if pendingCompatibleTypeMetaTypeID == typeID {
            pendingCompatibleTypeMetaValue = value
            return
        }
        if let oldTypeID = pendingCompatibleTypeMetaTypeID,
           let oldValue = pendingCompatibleTypeMetaValue {
            pendingCompatibleTypeMeta[oldTypeID] = oldValue
        }
        pendingCompatibleTypeMetaTypeID = typeID
        pendingCompatibleTypeMetaValue = value
    }

    @inline(__always)
    private func pendingCompatibleTypeMetaMatchesLocalSchema<T: Serializer>(for type: T.Type) -> Bool {
        let typeID = ObjectIdentifier(type)
        if pendingCompatibleTypeMetaTypeID == typeID {
            return pendingCompatibleTypeMetaValue?.matchesLocalSchema ?? false
        }
        return pendingCompatibleTypeMeta[typeID]?.matchesLocalSchema ?? false
    }

    @inline(__always)
    private func pendingCompatibleTypeMetaHasUserTypeFields<T: Serializer>(for type: T.Type) -> Bool {
        let typeID = ObjectIdentifier(type)
        if pendingCompatibleTypeMetaTypeID == typeID {
            return pendingCompatibleTypeMetaValue?.localHasUserTypeFields ?? true
        }
        return pendingCompatibleTypeMeta[typeID]?.localHasUserTypeFields ?? true
    }

    private static func parseCompatibleRootTypeInfoEntry(
        _ bytes: [UInt8],
        requiresTypeMetaState: Bool
    ) -> CompatibleRootTypeInfoEntry? {
        var index = 0
        guard decodeVarUInt32(bytes, index: &index) != nil,
              let indexMarker = decodeVarUInt32(bytes, index: &index),
              (indexMarker & 1) == 0,
              let typeMetaHeader = decodeUInt64(bytes, index: &index) else {
            return nil
        }
        var bodySize = Int(typeMetaHeader & UInt64(compatibleTypeMetaSizeMask))
        if bodySize == compatibleTypeMetaSizeMask {
            guard let extendedBodySize = decodeVarUInt32(bytes, index: &index) else {
                return nil
            }
            bodySize += Int(extendedBodySize)
        }
        let end = index + bodySize
        guard end <= bytes.count else {
            return nil
        }
        return CompatibleRootTypeInfoEntry(
            prefixBytes: Array(bytes[..<index]),
            bodySize: bodySize,
            typeMetaIndex: Int(indexMarker >> 1),
            requiresTypeMetaState: requiresTypeMetaState
        )
    }

    @inline(__always)
    private static func decodeVarUInt32(_ bytes: [UInt8], index: inout Int) -> UInt32? {
        guard index < bytes.count else {
            return nil
        }
        let b0 = bytes[index]
        if b0 < 0x80 {
            index += 1
            return UInt32(b0)
        }
        guard index + 1 < bytes.count else {
            return nil
        }
        let b1 = bytes[index + 1]
        if b1 < 0x80 {
            index += 2
            return UInt32(b0 & 0x7F) | (UInt32(b1) << 7)
        }
        guard index + 2 < bytes.count else {
            return nil
        }
        let b2 = bytes[index + 2]
        if b2 < 0x80 {
            index += 3
            return UInt32(b0 & 0x7F) | (UInt32(b1 & 0x7F) << 7) | (UInt32(b2) << 14)
        }
        guard index + 3 < bytes.count else {
            return nil
        }
        let b3 = bytes[index + 3]
        if b3 < 0x80 {
            index += 4
            return UInt32(b0 & 0x7F) |
                (UInt32(b1 & 0x7F) << 7) |
                (UInt32(b2 & 0x7F) << 14) |
                (UInt32(b3) << 21)
        }
        guard index + 4 < bytes.count else {
            return nil
        }
        let b4 = bytes[index + 4]
        guard b4 < 0x80 else {
            return nil
        }
        index += 5
        return UInt32(b0 & 0x7F) |
            (UInt32(b1 & 0x7F) << 7) |
            (UInt32(b2 & 0x7F) << 14) |
            (UInt32(b3 & 0x7F) << 21) |
            (UInt32(b4) << 28)
    }

    @inline(__always)
    private static func decodeUInt64(_ bytes: [UInt8], index: inout Int) -> UInt64? {
        guard index + 8 <= bytes.count else {
            return nil
        }
        let b0 = UInt64(bytes[index])
        let b1 = UInt64(bytes[index + 1]) << 8
        let b2 = UInt64(bytes[index + 2]) << 16
        let b3 = UInt64(bytes[index + 3]) << 24
        let b4 = UInt64(bytes[index + 4]) << 32
        let b5 = UInt64(bytes[index + 5]) << 40
        let b6 = UInt64(bytes[index + 6]) << 48
        let b7 = UInt64(bytes[index + 7]) << 56
        index += 8
        return b0 | b1 | b2 | b3 | b4 | b5 | b6 | b7
    }

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
    func readInternedUTF8String(count: Int) throws -> String {
        if count == 0 {
            return ""
        }
        if count > utf8InternMaxStringLength {
            return try buffer.readUTF8String(count: count)
        }
        let start = buffer.getCursor()
        try buffer.checkBound(count)
        let end = start + count
        let hash = utf8Hash(start: start, end: end)
        if let bucket = utf8StringInternCache[hash] {
            for entry in bucket where entry.bytes.count == count {
                if utf8BytesEqual(entry.bytes, start: start, end: end) {
                    buffer.setCursor(end)
                    return entry.value
                }
            }
        }
        let decoded = try buffer.readUTF8String(count: count)
        cacheInternedUTF8String(hash: hash, bytes: buffer.copyBytes(start: start, end: end), value: decoded)
        return decoded
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
            if pendingCompatibleTypeMetaTypeID != nil {
                pendingCompatibleTypeMetaTypeID = nil
                pendingCompatibleTypeMetaValue = nil
            }
            if !pendingCompatibleTypeMeta.isEmpty {
                pendingCompatibleTypeMeta.removeAll(keepingCapacity: true)
            }
        }
        if !pendingTypeInfo.isEmpty {
            pendingTypeInfo.removeAll(keepingCapacity: true)
        }
    }

    func reset() {
        resetObjectState()
        if compatibleTypeDefStateUsed {
            compatibleTypeDefState.reset()
            compatibleTypeDefStateUsed = false
        }
        if metaStringReadStateUsed {
            metaStringReadState.reset()
            metaStringReadStateUsed = false
        }
    }

    @inline(__always)
    private func cacheInternedUTF8String(hash: UInt64, bytes: [UInt8], value: String) {
        if utf8StringInternEntryCount >= utf8InternMaxEntryCount ||
            utf8StringInternByteCount + bytes.count > utf8InternMaxByteCount {
            utf8StringInternCache.removeAll(keepingCapacity: true)
            utf8StringInternEntryCount = 0
            utf8StringInternByteCount = 0
        }
        let entry = InternedUTF8StringEntry(bytes: bytes, value: value)
        if var bucket = utf8StringInternCache[hash] {
            bucket.append(entry)
            utf8StringInternCache[hash] = bucket
        } else {
            utf8StringInternCache[hash] = [entry]
        }
        utf8StringInternEntryCount += 1
        utf8StringInternByteCount += bytes.count
    }

    @inline(__always)
    private func utf8Hash(start: Int, end: Int) -> UInt64 {
        var hash: UInt64 = 0xcbf29ce484222325
        var index = start
        while index < end {
            hash ^= UInt64(buffer.byte(at: index))
            hash &*= 0x100000001b3
            index += 1
        }
        hash ^= UInt64(end - start)
        hash &*= 0x100000001b3
        return hash
    }

    @inline(__always)
    private func utf8BytesEqual(_ other: [UInt8], start: Int, end: Int) -> Bool {
        var index = 0
        while index < other.count {
            if buffer.byte(at: start + index) != other[index] {
                return false
            }
            index += 1
        }
        return start + other.count == end
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
