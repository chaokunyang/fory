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

public final class CompatibleTypeDefWriteState {
    private var firstTypeID: ObjectIdentifier?
    private var firstTypeIndex: UInt32 = 0
    private var overflowTypeIndexBySwiftType: [ObjectIdentifier: UInt32] = [:]
    private var nextIndex: UInt32 = 0

    public init() {}

    @inline(__always)
    public func lookupIndex(for typeID: ObjectIdentifier) -> UInt32? {
        if firstTypeID == typeID {
            return firstTypeIndex
        }
        return overflowTypeIndexBySwiftType[typeID]
    }

    @inline(__always)
    public func assignIndexIfAbsent(for typeID: ObjectIdentifier) -> (index: UInt32, isNew: Bool) {
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
    public func assignFirstTypeIndex(for typeID: ObjectIdentifier) {
        firstTypeID = typeID
        firstTypeIndex = 0
        nextIndex = 1
    }

    @inline(__always)
    public func reset() {
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

public final class CompatibleTypeDefReadState {
    private var firstTypeMeta: TypeMeta?
    private var overflowTypeMetas: [TypeMeta] = []

    public init() {}

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

    public func typeMeta(at index: Int) -> TypeMeta? {
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

    public func storeTypeMeta(_ typeMeta: TypeMeta, at index: Int) throws {
        try storeTypeMetaEntry(typeMeta, at: index)
    }

    public func reset() {
        if firstTypeMeta != nil {
            firstTypeMeta = nil
        }
        if !overflowTypeMetas.isEmpty {
            overflowTypeMetas.removeAll(keepingCapacity: true)
        }
    }
}

private let compatibleTypeMetaSizeMask = 0xFF

public final class CompatibleReadTypeMeta: @unchecked Sendable {
    public let fields: [TypeMetaFieldInfo]
    public let canUseSchemaFastPath: Bool
    public let canUseSchemaOrderReadPath: Bool

    public init(
        fields: [TypeMetaFieldInfo],
        canUseSchemaFastPath: Bool,
        canUseSchemaOrderReadPath: Bool
    ) {
        self.fields = fields
        self.canUseSchemaFastPath = canUseSchemaFastPath
        self.canUseSchemaOrderReadPath = canUseSchemaOrderReadPath
    }
}

private enum CompatibleTypeMetaReadCache {
    nonisolated(unsafe) static var values: [UInt64: TypeMeta] = [:]
    static let lock = NSLock()
}

private struct CompatibleResolvedTypeMetaCacheKey: Hashable {
    let swiftType: ObjectIdentifier
    let trackRef: Bool
    let remoteHeaderHash: UInt64
    let remoteFieldCount: Int
    let localHeaderHash: UInt64
    let hasLocalHeaderHash: Bool
    let localHasUserTypeFields: Bool
}

private struct CachedCompatibleRootTypeInfo {
    let prefixBytes: [UInt8]
    let bodySize: Int
    let typeMetaIndex: Int
}

private struct MetaStringCacheKey: Hashable {
    let encoding: MetaStringEncoding
    let bytes: [UInt8]
}

private struct CanonicalReferenceSignature: Hashable {
    let typeID: ObjectIdentifier
    let hashLo: UInt64
    let hashHi: UInt64
    let length: Int
}

private struct CanonicalReferenceEntry {
    let bytes: [UInt8]
    let object: AnyObject
}

private struct InternedUTF8StringEntry {
    let bytes: [UInt8]
    let value: String
}

private let utf8InternMaxEntryCount = 512
private let utf8InternMaxByteCount = 32 * 1024
private let utf8InternMaxStringLength = 64

public final class MetaStringWriteState {
    private var stringIndexByKey: [MetaStringCacheKey: UInt32] = [:]
    private var nextIndex: UInt32 = 0

    public init() {}

    public func index(for value: MetaString) -> UInt32? {
        stringIndexByKey[MetaStringCacheKey(encoding: value.encoding, bytes: value.bytes)]
    }

    public func assignIndexIfAbsent(for value: MetaString) -> (index: UInt32, isNew: Bool) {
        let key = MetaStringCacheKey(encoding: value.encoding, bytes: value.bytes)
        if let existing = stringIndexByKey[key] {
            return (existing, false)
        }
        let index = nextIndex
        nextIndex &+= 1
        stringIndexByKey[key] = index
        return (index, true)
    }

    public func reset() {
        if !stringIndexByKey.isEmpty {
            stringIndexByKey.removeAll(keepingCapacity: true)
        }
        if nextIndex != 0 {
            nextIndex = 0
        }
    }
}

public final class MetaStringReadState {
    private var values: [MetaString] = []

    public init() {}

    public func value(at index: Int) -> MetaString? {
        guard index >= 0, index < values.count else {
            return nil
        }
        return values[index]
    }

    public func append(_ value: MetaString) {
        values.append(value)
    }

    public func reset() {
        if !values.isEmpty {
            values.removeAll(keepingCapacity: true)
        }
    }
}

public struct DynamicTypeInfo {
    public let wireTypeID: TypeId
    public let userTypeID: UInt32?
    public let namespace: MetaString?
    public let typeName: MetaString?
    public let compatibleTypeMeta: TypeMeta?

    public init(
        wireTypeID: TypeId,
        userTypeID: UInt32?,
        namespace: MetaString?,
        typeName: MetaString?,
        compatibleTypeMeta: TypeMeta?
    ) {
        self.wireTypeID = wireTypeID
        self.userTypeID = userTypeID
        self.namespace = namespace
        self.typeName = typeName
        self.compatibleTypeMeta = compatibleTypeMeta
    }
}

public final class WriteContext {
    public let buffer: ByteBuffer
    public let typeResolver: TypeResolver
    public let trackRef: Bool
    public let compatible: Bool
    public let checkClassVersion: Bool
    public let maxDepth: Int
    public let refWriter: RefWriter
    public let compatibleTypeDefState: CompatibleTypeDefWriteState
    public let metaStringWriteState: MetaStringWriteState
    private var compatibleTypeDefStateUsed = false
    private var metaStringWriteStateUsed = false
    private var dynamicAnyDepth = 0
    private var lastRegisteredTypeInfoTypeID: ObjectIdentifier?
    private var lastRegisteredTypeInfo: RegisteredTypeInfo?
    private var lastResolvedWireTypeTypeID: ObjectIdentifier?
    private var lastResolvedWireTypeID: TypeId?
    private var lastCompatibleTypeMetaPlanTypeID: ObjectIdentifier?
    private var lastCompatibleTypeMetaPlanWireTypeID: TypeId?
    private var lastCompatibleTypeMetaPlan: CompatibleTypeMetaPlan?
    private var lastCompatibleRootTypeInfoTypeID: ObjectIdentifier?
    private var lastCompatibleRootTypeInfoBytes: [UInt8]?
    private var lastCompatibleRootTypeInfoHasUserTypeFields = true
    private var lastFirstCompatibleTypeMetaTypeID: ObjectIdentifier?
    private var lastFirstCompatibleTypeMetaHasUserTypeFields = false
    private var reusableOutputData = Data()

    public init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool = false,
        checkClassVersion: Bool = true,
        maxDepth: Int = 5,
        compatibleTypeDefState: CompatibleTypeDefWriteState = CompatibleTypeDefWriteState(),
        metaStringWriteState: MetaStringWriteState = MetaStringWriteState()
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
    public func enterDynamicAnyDepth() throws {
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
    public func requireRegisteredTypeInfo<T: Serializer>(for type: T.Type) throws -> RegisteredTypeInfo {
        let typeID = ObjectIdentifier(type)
        if lastRegisteredTypeInfoTypeID == typeID, let cached = lastRegisteredTypeInfo {
            return cached
        }
        let info = try typeResolver.requireRegisteredTypeInfo(for: type)
        lastRegisteredTypeInfoTypeID = typeID
        lastRegisteredTypeInfo = info
        return info
    }

    @inline(__always)
    public func resolvedWireTypeID(for typeID: ObjectIdentifier) -> TypeId? {
        if lastResolvedWireTypeTypeID == typeID {
            return lastResolvedWireTypeID
        }
        return nil
    }

    @inline(__always)
    public func cacheResolvedWireTypeID(_ wireTypeID: TypeId, for typeID: ObjectIdentifier) {
        lastResolvedWireTypeTypeID = typeID
        lastResolvedWireTypeID = wireTypeID
    }

    @inline(__always)
    func compatibleTypeMetaPlan(for typeID: ObjectIdentifier, wireTypeID: TypeId) -> CompatibleTypeMetaPlan? {
        if lastCompatibleTypeMetaPlanTypeID == typeID,
           lastCompatibleTypeMetaPlanWireTypeID == wireTypeID {
            return lastCompatibleTypeMetaPlan
        }
        return nil
    }

    @inline(__always)
    func cacheCompatibleTypeMetaPlan(
        _ plan: CompatibleTypeMetaPlan,
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId
    ) {
        lastCompatibleTypeMetaPlanTypeID = typeID
        lastCompatibleTypeMetaPlanWireTypeID = wireTypeID
        lastCompatibleTypeMetaPlan = plan
    }

    @inline(__always)
    public func leaveDynamicAnyDepth() {
        if dynamicAnyDepth > 0 {
            dynamicAnyDepth -= 1
        }
    }

    @inline(__always)
    func writeCachedCompatibleRootTypeInfoIfAvailable<T: Serializer>(
        for type: T.Type
    ) -> Bool {
        guard compatible, !compatibleTypeDefStateUsed else {
            return false
        }
        let typeID = ObjectIdentifier(type)
        guard lastCompatibleRootTypeInfoTypeID == typeID,
              let bytes = lastCompatibleRootTypeInfoBytes else {
            return false
        }
        buffer.writeBytes(bytes)
        if lastCompatibleRootTypeInfoHasUserTypeFields {
            compatibleTypeDefStateUsed = true
            compatibleTypeDefState.assignFirstTypeIndex(for: typeID)
        }
        return true
    }

    @inline(__always)
    func cacheCompatibleRootTypeInfo<T: Serializer>(
        for type: T.Type,
        bytes: [UInt8]
    ) {
        let typeID = ObjectIdentifier(type)
        lastCompatibleRootTypeInfoTypeID = typeID
        lastCompatibleRootTypeInfoBytes = bytes
        if lastFirstCompatibleTypeMetaTypeID == typeID {
            lastCompatibleRootTypeInfoHasUserTypeFields = lastFirstCompatibleTypeMetaHasUserTypeFields
        } else {
            lastCompatibleRootTypeInfoHasUserTypeFields = true
        }
    }

    @inline(__always)
    func compatibleTypeDefStateIsUsed() -> Bool {
        compatibleTypeDefStateUsed
    }

    public func writeCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        typeMeta: TypeMeta
    ) throws {
        let encodedTypeMeta = try typeMeta.encode()
        try writeCompatibleTypeMeta(for: type, encodedTypeMeta: encodedTypeMeta)
    }

    @inline(__always)
    func writeCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        plan: CompatibleTypeMetaPlan
    ) {
        let typeID = ObjectIdentifier(type)
        if !compatibleTypeDefStateUsed {
            compatibleTypeDefStateUsed = true
            compatibleTypeDefState.assignFirstTypeIndex(for: typeID)
            lastFirstCompatibleTypeMetaTypeID = typeID
            lastFirstCompatibleTypeMetaHasUserTypeFields = plan.hasUserTypeFields
            buffer.writeBytes(plan.firstDefinitionEncodedTypeMeta)
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
            buffer.writeBytes(plan.encodedTypeMeta)
        } else {
            let marker = (assignment.index << 1) | 1
            if marker < 0x80 {
                buffer.writeUInt8(UInt8(truncatingIfNeeded: marker))
            } else {
                buffer.writeVarUInt32(marker)
            }
        }
    }

    public func writeCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        encodedTypeMeta: [UInt8]
    ) throws {
        let plan = CompatibleTypeMetaPlan(
            encodedTypeMeta: encodedTypeMeta,
            headerHash: 0,
            hasUserTypeFields: true
        )
        writeCompatibleTypeMeta(for: type, plan: plan)
    }

    public func resetObjectState() {
        if dynamicAnyDepth != 0 {
            dynamicAnyDepth = 0
        }
        if lastFirstCompatibleTypeMetaTypeID != nil {
            lastFirstCompatibleTypeMetaTypeID = nil
            lastFirstCompatibleTypeMetaHasUserTypeFields = false
        }
        if trackRef {
            refWriter.reset()
        }
    }

    @inline(__always)
    public func markMetaStringWriteStateUsed() {
        metaStringWriteStateUsed = true
    }

    @inline(__always)
    public func materializeOutputData() -> Data {
        let byteCount = buffer.storage.count
        if reusableOutputData.count != byteCount {
            reusableOutputData.count = byteCount
        }
        if byteCount > 0 {
            reusableOutputData.withUnsafeMutableBytes { destination in
                guard let destinationBase = destination.baseAddress else {
                    return
                }
                buffer.storage.withUnsafeBytes { source in
                    guard let sourceBase = source.baseAddress else {
                        return
                    }
                    destinationBase.copyMemory(from: sourceBase, byteCount: byteCount)
                }
            }
        }
        return reusableOutputData
    }

    public func reset() {
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
    public let typeResolver: TypeResolver
    public let trackRef: Bool
    public let compatible: Bool
    public let checkClassVersion: Bool
    public let maxCollectionSize: Int
    public let maxBinarySize: Int
    public let maxDepth: Int
    public let refReader: RefReader
    public let compatibleTypeDefState: CompatibleTypeDefReadState
    public let metaStringReadState: MetaStringReadState
    private var compatibleTypeDefStateUsed = false
    private var metaStringReadStateUsed = false
    private var dynamicAnyDepth = 0

    private var pendingRefStack: [PendingRefSlot] = []
    private var pendingCompatibleTypeMeta: [ObjectIdentifier: CompatibleReadTypeMeta] = [:]
    private var pendingCompatibleTypeMetaTypeID: ObjectIdentifier?
    private var pendingCompatibleTypeMetaValue: CompatibleReadTypeMeta?
    private var compatibleResolvedTypeMetaCache: [CompatibleResolvedTypeMetaCacheKey: CompatibleReadTypeMeta] = [:]
    private var lastCompatibleResolvedTypeMetaCacheKey: CompatibleResolvedTypeMetaCacheKey?
    private var lastCompatibleResolvedTypeMetaCacheValue: CompatibleReadTypeMeta?
    private var pendingDynamicTypeInfo: [ObjectIdentifier: DynamicTypeInfo] = [:]
    private var canonicalReferenceCache: [CanonicalReferenceSignature: [CanonicalReferenceEntry]] = [:]
    private var utf8StringInternCache: [UInt64: [InternedUTF8StringEntry]] = [:]
    private var utf8StringInternEntryCount = 0
    private var utf8StringInternByteCount = 0
    private var lastCompatibleTypeMetaCacheHeader: UInt64?
    private var lastCompatibleTypeMetaCacheEntry: TypeMeta?
    private var lastRegisteredTypeInfoTypeID: ObjectIdentifier?
    private var lastRegisteredTypeInfo: RegisteredTypeInfo?
    private var lastResolvedWireTypeTypeID: ObjectIdentifier?
    private var lastResolvedWireTypeID: TypeId?
    private var lastCompatibleTypeMetaPlanTypeID: ObjectIdentifier?
    private var lastCompatibleTypeMetaPlanWireTypeID: TypeId?
    private var lastCompatibleTypeMetaPlan: CompatibleTypeMetaPlan?
    private var lastValidatedCompatibleTypeID: ObjectIdentifier?
    private var lastValidatedCompatibleWireTypeID: TypeId?
    private var lastValidatedCompatibleHeaderHash: UInt64 = 0
    private var lastCompatibleRootTypeInfoTypeID: ObjectIdentifier?
    private var lastCompatibleRootTypeInfoEntry: CachedCompatibleRootTypeInfo?
    private var lastCompatibleRootTypeInfoMeta: CompatibleReadTypeMeta?
    private var lastCompatibleRootTypeInfoRemoteTypeMeta: TypeMeta?
    private var lastResolvedCompatibleTypeMetaTypeID: ObjectIdentifier?
    private var lastResolvedCompatibleTypeMetaValue: TypeMeta?

    public init(
        buffer: ByteBuffer,
        typeResolver: TypeResolver,
        trackRef: Bool,
        compatible: Bool = false,
        checkClassVersion: Bool = true,
        maxCollectionSize: Int = 1_000_000,
        maxBinarySize: Int = 64 * 1024 * 1024,
        maxDepth: Int = 5,
        compatibleTypeDefState: CompatibleTypeDefReadState = CompatibleTypeDefReadState(),
        metaStringReadState: MetaStringReadState = MetaStringReadState()
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
    public func enterDynamicAnyDepth() throws {
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
    public func leaveDynamicAnyDepth() {
        if dynamicAnyDepth > 0 {
            dynamicAnyDepth -= 1
        }
    }

    @inline(__always)
    public func ensureCollectionLength(_ length: Int, label: String) throws {
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
    public func ensureBinaryLength(_ length: Int, label: String) throws {
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
    public func ensureRemainingBytes(_ byteCount: Int, label: String) throws {
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
    public func requireRegisteredTypeInfo<T: Serializer>(for type: T.Type) throws -> RegisteredTypeInfo {
        let typeID = ObjectIdentifier(type)
        if lastRegisteredTypeInfoTypeID == typeID, let cached = lastRegisteredTypeInfo {
            return cached
        }
        let info = try typeResolver.requireRegisteredTypeInfo(for: type)
        lastRegisteredTypeInfoTypeID = typeID
        lastRegisteredTypeInfo = info
        return info
    }

    @inline(__always)
    public func resolvedWireTypeID(for typeID: ObjectIdentifier) -> TypeId? {
        if lastResolvedWireTypeTypeID == typeID {
            return lastResolvedWireTypeID
        }
        return nil
    }

    @inline(__always)
    public func cacheResolvedWireTypeID(_ wireTypeID: TypeId, for typeID: ObjectIdentifier) {
        lastResolvedWireTypeTypeID = typeID
        lastResolvedWireTypeID = wireTypeID
    }

    @inline(__always)
    func compatibleTypeMetaPlan(for typeID: ObjectIdentifier, wireTypeID: TypeId) -> CompatibleTypeMetaPlan? {
        if lastCompatibleTypeMetaPlanTypeID == typeID,
           lastCompatibleTypeMetaPlanWireTypeID == wireTypeID {
            return lastCompatibleTypeMetaPlan
        }
        return nil
    }

    @inline(__always)
    func cacheCompatibleTypeMetaPlan(
        _ plan: CompatibleTypeMetaPlan,
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId
    ) {
        lastCompatibleTypeMetaPlanTypeID = typeID
        lastCompatibleTypeMetaPlanWireTypeID = wireTypeID
        lastCompatibleTypeMetaPlan = plan
    }

    @inline(__always)
    public func isCompatibleTypeMetaValidationCached(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) -> Bool {
        lastValidatedCompatibleTypeID == typeID &&
            lastValidatedCompatibleWireTypeID == wireTypeID &&
            lastValidatedCompatibleHeaderHash == headerHash
    }

    @inline(__always)
    public func cacheCompatibleTypeMetaValidation(
        for typeID: ObjectIdentifier,
        wireTypeID: TypeId,
        headerHash: UInt64
    ) {
        lastValidatedCompatibleTypeID = typeID
        lastValidatedCompatibleWireTypeID = wireTypeID
        lastValidatedCompatibleHeaderHash = headerHash
    }

    @inline(__always)
    func compatibleTypeDefStateIsUsed() -> Bool {
        compatibleTypeDefStateUsed
    }

    @inline(__always)
    func lastResolvedCompatibleTypeMeta<T: Serializer>(for type: T.Type) -> TypeMeta? {
        let typeID = ObjectIdentifier(type)
        if lastResolvedCompatibleTypeMetaTypeID == typeID {
            return lastResolvedCompatibleTypeMetaValue
        }
        return nil
    }

    @inline(__always)
    func readCachedCompatibleRootTypeInfoIfAvailable<T: Serializer>(
        for type: T.Type
    ) -> Bool {
        guard compatible, !compatibleTypeDefStateUsed else {
            return false
        }
        let typeID = ObjectIdentifier(type)
        guard lastCompatibleRootTypeInfoTypeID == typeID,
              let entry = lastCompatibleRootTypeInfoEntry,
              let meta = lastCompatibleRootTypeInfoMeta else {
            return false
        }
        let start = buffer.getCursor()
        let prefixBytes = entry.prefixBytes
        let prefixLength = prefixBytes.count
        let end = start + prefixLength + entry.bodySize
        guard end <= buffer.storage.count else {
            return false
        }

        var idx = 0
        while idx < prefixLength {
            if buffer.storage[start + idx] != prefixBytes[idx] {
                return false
            }
            idx += 1
        }

        buffer.setCursor(end)
        if let remoteTypeMeta = lastCompatibleRootTypeInfoRemoteTypeMeta {
            do {
                try compatibleTypeDefState.storeTypeMetaEntry(remoteTypeMeta, at: entry.typeMetaIndex)
                compatibleTypeDefStateUsed = true
            } catch {
                buffer.setCursor(start)
                return false
            }
        }
        setPendingCompatibleTypeMeta(typeID: typeID, value: meta)
        return true
    }

    @inline(__always)
    func cacheCompatibleRootTypeInfo<T: Serializer>(
        for type: T.Type,
        bytes: [UInt8],
        compatibleTypeMeta: CompatibleReadTypeMeta?,
        remoteTypeMeta: TypeMeta?
    ) {
        guard compatible, let compatibleTypeMeta else {
            return
        }
        lastCompatibleRootTypeInfoTypeID = ObjectIdentifier(type)
        lastCompatibleRootTypeInfoEntry = Self.parseCompatibleRootTypeInfo(bytes)
        lastCompatibleRootTypeInfoMeta = compatibleTypeMeta
        lastCompatibleRootTypeInfoRemoteTypeMeta = remoteTypeMeta
    }

    public func pushPendingReference(_ refID: UInt32) {
        pendingRefStack.append(PendingRefSlot(refID: refID, bound: false))
    }

    public func bindPendingReference(_ value: Any) {
        guard var last = pendingRefStack.popLast() else {
            return
        }
        last.bound = true
        refReader.storeRef(value, at: last.refID)
        pendingRefStack.append(last)
    }

    public func finishPendingReferenceIfNeeded(_ value: Any) {
        guard var last = pendingRefStack.popLast() else {
            return
        }
        if !last.bound {
            refReader.storeRef(value, at: last.refID)
            last.bound = true
        }
    }

    public func popPendingReference() {
        _ = pendingRefStack.popLast()
    }

    public func readCompatibleTypeMeta() throws -> TypeMeta {
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

        CompatibleTypeMetaReadCache.lock.lock()
        if let cached = CompatibleTypeMetaReadCache.values[header] {
            CompatibleTypeMetaReadCache.lock.unlock()
            try buffer.skip(bodySize)
            lastCompatibleTypeMetaCacheHeader = header
            lastCompatibleTypeMetaCacheEntry = cached
            try compatibleTypeDefState.storeTypeMetaEntry(cached, at: index)
            return cached
        }
        CompatibleTypeMetaReadCache.lock.unlock()

        buffer.setCursor(typeMetaStart)
        let decoded = try TypeMeta.decode(buffer)
        var canonicalEntry = decoded

        CompatibleTypeMetaReadCache.lock.lock()
        if let cached = CompatibleTypeMetaReadCache.values[header] {
            canonicalEntry = cached
        } else {
            CompatibleTypeMetaReadCache.values[header] = decoded
        }
        CompatibleTypeMetaReadCache.lock.unlock()

        lastCompatibleTypeMetaCacheHeader = header
        lastCompatibleTypeMetaCacheEntry = canonicalEntry
        try compatibleTypeDefState.storeTypeMetaEntry(canonicalEntry, at: index)
        return canonicalEntry
    }

    public func pushCompatibleTypeMeta<T: Serializer>(
        for type: T.Type,
        _ typeMeta: TypeMeta,
        localTypeMetaHeaderHash: UInt64? = nil,
        localTypeMetaHasUserTypeFields: Bool = true
    ) {
        let typeID = ObjectIdentifier(type)
        lastResolvedCompatibleTypeMetaTypeID = typeID
        lastResolvedCompatibleTypeMetaValue = typeMeta
        let cacheKey = CompatibleResolvedTypeMetaCacheKey(
            swiftType: typeID,
            trackRef: trackRef,
            remoteHeaderHash: typeMeta.headerHash,
            remoteFieldCount: typeMeta.fields.count,
            localHeaderHash: localTypeMetaHeaderHash ?? 0,
            hasLocalHeaderHash: localTypeMetaHeaderHash != nil,
            localHasUserTypeFields: localTypeMetaHasUserTypeFields
        )
        if lastCompatibleResolvedTypeMetaCacheKey == cacheKey,
           let cached = lastCompatibleResolvedTypeMetaCacheValue {
            setPendingCompatibleTypeMeta(typeID: typeID, value: cached)
            return
        }
        if let cached = compatibleResolvedTypeMetaCache[cacheKey] {
            lastCompatibleResolvedTypeMetaCacheKey = cacheKey
            lastCompatibleResolvedTypeMetaCacheValue = cached
            setPendingCompatibleTypeMeta(typeID: typeID, value: cached)
            return
        }

        let localFields = T.foryCompatibleTypeMetaFields(trackRef: trackRef)
        let resolvedFields = Self.assignCompatibleFieldIDs(
            remoteFields: typeMeta.fields,
            localFields: localFields
        )
        let canUseSchemaOrderReadPath = localTypeMetaHeaderHash != nil &&
            localTypeMetaHeaderHash == typeMeta.headerHash
        let canUseSchemaFastPath = canUseSchemaOrderReadPath && !localTypeMetaHasUserTypeFields
        let resolved = CompatibleReadTypeMeta(
            fields: resolvedFields,
            canUseSchemaFastPath: canUseSchemaFastPath,
            canUseSchemaOrderReadPath: canUseSchemaOrderReadPath
        )

        compatibleResolvedTypeMetaCache[cacheKey] = resolved
        lastCompatibleResolvedTypeMetaCacheKey = cacheKey
        lastCompatibleResolvedTypeMetaCacheValue = resolved
        setPendingCompatibleTypeMeta(typeID: typeID, value: resolved)
    }

    public func consumeCompatibleTypeMeta<T: Serializer>(for type: T.Type) throws -> CompatibleReadTypeMeta {
        let typeID = ObjectIdentifier(type)
        if pendingCompatibleTypeMetaTypeID == typeID,
           let pending = pendingCompatibleTypeMetaValue {
            return pending
        }
        guard let typeMeta = pendingCompatibleTypeMeta[typeID] else {
            throw ForyError.invalidData("missing compatible type metadata for \(type)")
        }
        return typeMeta
    }

    @inline(__always)
    public func consumeCompatibleTypeMetaIfPresent<T: Serializer>(for type: T.Type) -> CompatibleReadTypeMeta? {
        let typeID = ObjectIdentifier(type)
        if pendingCompatibleTypeMetaTypeID == typeID {
            return pendingCompatibleTypeMetaValue
        }
        return pendingCompatibleTypeMeta[typeID]
    }

    @inline(__always)
    private func setPendingCompatibleTypeMeta(typeID: ObjectIdentifier, value: CompatibleReadTypeMeta) {
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

    private static func parseCompatibleRootTypeInfo(_ bytes: [UInt8]) -> CachedCompatibleRootTypeInfo? {
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
        return CachedCompatibleRootTypeInfo(
            prefixBytes: Array(bytes[..<index]),
            bodySize: bodySize,
            typeMetaIndex: Int(indexMarker >> 1)
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

    private static func assignCompatibleFieldIDs(
        remoteFields: [TypeMetaFieldInfo],
        localFields: [TypeMetaFieldInfo]
    ) -> [TypeMetaFieldInfo] {
        var fieldIndexByName: [String: (Int, TypeMetaFieldInfo)] = [:]
        var fieldIndexByID: [Int16: (Int, TypeMetaFieldInfo)] = [:]
        fieldIndexByName.reserveCapacity(localFields.count)
        fieldIndexByID.reserveCapacity(localFields.count)

        for (index, localField) in localFields.enumerated() {
            fieldIndexByName[toSnakeCase(localField.fieldName)] = (index, localField)
            if let fieldID = localField.fieldID, fieldID >= 0 {
                fieldIndexByID[fieldID] = (index, localField)
            }
        }

        return remoteFields.map { remoteField in
            var resolvedField = remoteField

            let localMatch: (Int, TypeMetaFieldInfo)?
            if let fieldID = remoteField.fieldID, fieldID >= 0 {
                localMatch = fieldIndexByID[fieldID]
            } else {
                localMatch = fieldIndexByName[toSnakeCase(remoteField.fieldName)]
            }

            guard let (sortedIndex, localFieldInfo) = localMatch,
                  sortedIndex <= Int(Int16.max),
                  isCompatibleFieldType(remoteField.fieldType, localFieldInfo.fieldType) else {
                resolvedField.fieldID = -1
                return resolvedField
            }

            resolvedField.fieldID = Int16(sortedIndex)
            return resolvedField
        }
    }

    private static func isCompatibleFieldType(
        _ remoteType: TypeMetaFieldType,
        _ localType: TypeMetaFieldType
    ) -> Bool {
        if normalizeCompatibleTypeIDForComparison(remoteType.typeID) != normalizeCompatibleTypeIDForComparison(localType.typeID) {
            return false
        }
        if remoteType.nullable != localType.nullable || remoteType.trackRef != localType.trackRef {
            return false
        }
        if remoteType.generics.count != localType.generics.count {
            return false
        }
        for (remoteGeneric, localGeneric) in zip(remoteType.generics, localType.generics)
        where !isCompatibleFieldType(remoteGeneric, localGeneric) {
            return false
        }
        return true
    }

    private static func normalizeCompatibleTypeIDForComparison(_ typeID: UInt32) -> UInt32 {
        switch typeID {
        case TypeId.structType.rawValue,
             TypeId.compatibleStruct.rawValue,
             TypeId.namedStruct.rawValue,
             TypeId.namedCompatibleStruct.rawValue,
             TypeId.unknown.rawValue:
            return TypeId.structType.rawValue
        case TypeId.enumType.rawValue,
             TypeId.namedEnum.rawValue:
            return TypeId.enumType.rawValue
        case TypeId.ext.rawValue,
             TypeId.namedExt.rawValue:
            return TypeId.ext.rawValue
        case TypeId.binary.rawValue,
             TypeId.int8Array.rawValue,
             TypeId.uint8Array.rawValue:
            return TypeId.binary.rawValue
        case TypeId.union.rawValue,
             TypeId.typedUnion.rawValue,
             TypeId.namedUnion.rawValue:
            return TypeId.union.rawValue
        default:
            return typeID
        }
    }

    private static func toSnakeCase(_ name: String) -> String {
        if name.isEmpty {
            return name
        }

        let chars = Array(name)
        var result = String()
        result.reserveCapacity(name.count + 4)

        for (index, char) in chars.enumerated() {
            if char.isUppercase {
                if index > 0 {
                    let prevUpper = chars[index - 1].isUppercase
                    let nextUpperOrEnd = (index + 1 >= chars.count) || chars[index + 1].isUppercase
                    if !prevUpper || !nextUpperOrEnd {
                        result.append("_")
                    }
                }
                result.append(char.lowercased())
            } else {
                result.append(char)
            }
        }

        return result
    }

    public func setDynamicTypeInfo<T: Serializer>(for type: T.Type, _ typeInfo: DynamicTypeInfo) {
        pendingDynamicTypeInfo[ObjectIdentifier(type)] = typeInfo
    }

    public func dynamicTypeInfo<T: Serializer>(for type: T.Type) -> DynamicTypeInfo? {
        pendingDynamicTypeInfo[ObjectIdentifier(type)]
    }

    public func clearDynamicTypeInfo<T: Serializer>(for type: T.Type) {
        pendingDynamicTypeInfo.removeValue(forKey: ObjectIdentifier(type))
    }

    @inline(__always)
    public func readInternedUTF8String(count: Int) throws -> String {
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
        cacheInternedUTF8String(hash: hash, bytes: Array(buffer.storage[start..<end]), value: decoded)
        return decoded
    }

    public func canonicalizeNonTrackingReference<T>(
        _ value: T,
        start: Int,
        end: Int
    ) -> T {
        guard trackRef else {
            return value
        }
        guard end > start else {
            return value
        }
        guard let object = value as AnyObject? else {
            return value
        }

        let bytes = Array(buffer.storage[start..<end])
        let (hashLo, hashHi) = MurmurHash3.x64_128(bytes, seed: 47)
        let signature = CanonicalReferenceSignature(
            typeID: ObjectIdentifier(type(of: object)),
            hashLo: hashLo,
            hashHi: hashHi,
            length: bytes.count
        )

        if var bucket = canonicalReferenceCache[signature] {
            for entry in bucket where entry.bytes == bytes {
                if let shared = entry.object as? T {
                    return shared
                }
            }
            bucket.append(CanonicalReferenceEntry(bytes: bytes, object: object))
            canonicalReferenceCache[signature] = bucket
            return value
        }

        canonicalReferenceCache[signature] = [CanonicalReferenceEntry(bytes: bytes, object: object)]
        return value
    }

    @inline(__always)
    public func markMetaStringReadStateUsed() {
        metaStringReadStateUsed = true
    }

    public func resetObjectState() {
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
        if !pendingDynamicTypeInfo.isEmpty {
            pendingDynamicTypeInfo.removeAll(keepingCapacity: true)
        }
        if trackRef, !canonicalReferenceCache.isEmpty {
            canonicalReferenceCache.removeAll(keepingCapacity: true)
        }
    }

    public func reset() {
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
        let bytes = buffer.storage
        while index < end {
            hash ^= UInt64(bytes[index])
            hash &*= 0x100000001b3
            index += 1
        }
        hash ^= UInt64(end - start)
        hash &*= 0x100000001b3
        return hash
    }

    @inline(__always)
    private func utf8BytesEqual(_ other: [UInt8], start: Int, end: Int) -> Bool {
        let bytes = buffer.storage
        var index = 0
        while index < other.count {
            if bytes[start + index] != other[index] {
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
