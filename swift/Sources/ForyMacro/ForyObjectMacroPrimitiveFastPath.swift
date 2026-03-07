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

import SwiftSyntax
import SwiftSyntaxBuilder

func leadingPrimitiveFastPathFields(_ fields: [ParsedField]) -> [ParsedField] {
    var result: [ParsedField] = []
    result.reserveCapacity(fields.count)
    for field in fields {
        if isPrimitiveFastPathField(field) {
            result.append(field)
        } else {
            break
        }
    }
    return result
}

private func leadingFixedPrimitiveFields(_ fields: [ParsedField]) -> [ParsedField] {
    var result: [ParsedField] = []
    result.reserveCapacity(fields.count)
    for field in fields {
        if primitiveFixedByteWidth(for: field) != nil {
            result.append(field)
        } else {
            break
        }
    }
    return result
}

private func primitiveFixedPrefixBytes(_ fields: [ParsedField]) -> Int {
    fields.reduce(0) { partial, field in
        partial + (primitiveFixedByteWidth(for: field) ?? 0)
    }
}

private func primitiveFixedByteWidth(for field: ParsedField) -> Int? {
    switch trimType(field.typeText) {
    case "Bool", "Int8", "UInt8":
        return 1
    case "Int16", "UInt16":
        return 2
    case "Float":
        return 4
    case "Double":
        return 8
    default:
        return nil
    }
}

private func isPrimitiveFastPathField(_ field: ParsedField) -> Bool {
    guard !field.isOptional else {
        return false
    }
    guard field.dynamicAnyCodec == nil, field.customCodecType == nil else {
        return false
    }
    guard field.typeID != 27, !compatibleFieldNeedsTypeInfo(field) else {
        return false
    }
    return primitiveUnsafeWriteMethod(for: field) != nil && primitiveUnsafeReadMethod(for: field) != nil
}

func buildPrimitiveFastWriteBlock(_ fields: [ParsedField]) -> String? {
    guard !fields.isEmpty else {
        return nil
    }
    let fixedFields = leadingFixedPrimitiveFields(fields)
    let remainingFields = Array(fields.dropFirst(fixedFields.count))
    let fixedPrefixBytes = primitiveFixedPrefixBytes(fixedFields)
    let locals = fields.map { field in
        "let __\(field.name) = self.\(field.name)"
    }.joined(separator: "\n        ")
    let numericByteTerms = fields.compactMap { field in
        primitiveEncodedByteWidthExpr(for: field, valueExpr: "__\(field.name)")
    }
    guard !numericByteTerms.isEmpty else {
        return nil
    }
    let numericBytesExpr = numericByteTerms.joined(separator: " + ")
    var fixedOffset = 0
    let fixedWrites = fixedFields.compactMap { field -> String? in
        guard let line = primitiveUnsafeWriteFixedLine(for: field, offset: fixedOffset) else {
            return nil
        }
        fixedOffset += primitiveFixedByteWidth(for: field) ?? 0
        return line
    }.joined(separator: "\n            ")
    let remainingWrites = remainingFields.compactMap { field in
        primitiveUnsafeWriteAdvanceLine(for: field, indexExpr: "__writerIndex")
    }.joined(separator: "\n            ")
    var bodySections: [String] = []
    if !fixedWrites.isEmpty {
        bodySections.append(fixedWrites)
    }
    if !remainingWrites.isEmpty {
        bodySections.append(
            """
            var __writerIndex = \(fixedPrefixBytes)
            \(remainingWrites)
            assert(__writerIndex == __numericBytes)
            """
        )
    }
    let writeBody = bodySections.joined(separator: "\n            ")
    return """
    \(locals)
    let __numericBytes = \(numericBytesExpr)
    UnsafeUtil.writeNumericRegionExact(buffer: __buffer, exactBytes: __numericBytes) { __base in
        \(writeBody)
    }
    """
}

private func primitiveEncodedByteWidthExpr(for field: ParsedField, valueExpr: String) -> String? {
    switch trimType(field.typeText) {
    case "Bool", "Int8", "UInt8":
        return "1"
    case "Int16", "UInt16":
        return "2"
    case "Float":
        return "4"
    case "Double":
        return "8"
    case "Int32":
        return "UnsafeUtil.varInt32Size(\(valueExpr))"
    case "UInt32":
        return "UnsafeUtil.varUInt32Size(\(valueExpr))"
    case "Int64":
        return "UnsafeUtil.varInt64Size(\(valueExpr))"
    case "UInt64":
        return "UnsafeUtil.varUInt64Size(\(valueExpr))"
    case "Int":
        return "UnsafeUtil.varInt64Size(Int64(\(valueExpr)))"
    case "UInt":
        return "UnsafeUtil.varUInt64Size(UInt64(\(valueExpr)))"
    default:
        return nil
    }
}

private func primitiveUnsafeWriteFixedLine(for field: ParsedField, offset: Int) -> String? {
    guard let method = primitiveUnsafeWriteMethod(for: field) else {
        return nil
    }
    return "_ = UnsafeUtil.\(method)(__\(field.name), to: __base, index: \(offset))"
}

private func primitiveUnsafeWriteAdvanceLine(for field: ParsedField, indexExpr: String) -> String? {
    guard let method = primitiveUnsafeWriteMethod(for: field) else {
        return nil
    }
    return "__writerIndex = UnsafeUtil.\(method)(__\(field.name), to: __base, index: \(indexExpr))"
}

private func primitiveUnsafeWriteMethod(for field: ParsedField) -> String? {
    switch trimType(field.typeText) {
    case "Bool":
        return "writeBool"
    case "Int8":
        return "writeInt8"
    case "Int16":
        return "writeInt16"
    case "Int32":
        return "writeInt32"
    case "Int64":
        return "writeInt64"
    case "Int":
        return "writeInt"
    case "UInt8":
        return "writeUInt8"
    case "UInt16":
        return "writeUInt16"
    case "UInt32":
        return "writeUInt32"
    case "UInt64":
        return "writeUInt64"
    case "UInt":
        return "writeUInt"
    case "Float":
        return "writeFloat32"
    case "Double":
        return "writeFloat64"
    default:
        return nil
    }
}

private func isDirectPrimitiveSerializeType(_ fields: [ParsedField]) -> Bool {
    guard !fields.isEmpty else {
        return false
    }
    return leadingPrimitiveFastPathFields(fields).count == fields.count
}

func buildDirectPrimitiveSerializeDecls(
    sortedFields: [ParsedField],
    accessPrefix: String
) -> [DeclSyntax] {
    guard isDirectPrimitiveSerializeType(sortedFields) else {
        return []
    }
    let locals = sortedFields.map { field in
        "let __\(field.name) = self.\(field.name)"
    }.joined(separator: "\n        ")
    let byteTerms = sortedFields.compactMap { field in
        primitiveEncodedByteWidthExpr(for: field, valueExpr: "__\(field.name)")
    }
    guard !byteTerms.isEmpty else {
        return []
    }
    let sizeExpr = byteTerms.joined(separator: " + ")
    let fixedFields = leadingFixedPrimitiveFields(sortedFields)
    let fixedPrefixBytes = primitiveFixedPrefixBytes(fixedFields)
    let remainingFields = Array(sortedFields.dropFirst(fixedFields.count))

    var fixedOffset = 0
    let fixedWrites = fixedFields.compactMap { field -> String? in
        guard let method = primitiveUnsafeWriteMethod(for: field) else {
            return nil
        }
        let line = "_ = UnsafeUtil.\(method)(__\(field.name), to: __base, index: __start + \(fixedOffset))"
        fixedOffset += primitiveFixedByteWidth(for: field) ?? 0
        return line
    }.joined(separator: "\n        ")
    let remainingWrites = remainingFields.compactMap { field -> String? in
        guard let method = primitiveUnsafeWriteMethod(for: field) else {
            return nil
        }
        return "__writerIndex = UnsafeUtil.\(method)(__\(field.name), to: __base, index: __writerIndex)"
    }.joined(separator: "\n        ")

    var writeSections: [String] = [locals, "let __start = index"]
    if !fixedWrites.isEmpty {
        writeSections.append(fixedWrites)
    }
    if !remainingWrites.isEmpty {
        writeSections.append("var __writerIndex = __start + \(fixedPrefixBytes)")
        writeSections.append(remainingWrites)
        writeSections.append("index = __writerIndex")
    } else {
        writeSections.append("index = __start + \(fixedPrefixBytes)")
    }
    let writeBody = writeSections.joined(separator: "\n        ")

    let sizeDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)var foryPrimitiveDataSize: Int? {
            \(locals)
            return \(sizeExpr)
        }
        """
    )
    let writeDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)func foryWritePrimitiveData(to __base: UnsafeMutablePointer<UInt8>, index: inout Int) {
            \(writeBody)
        }
        """
    )
    return [sizeDecl, writeDecl]
}

private func primitiveUnsafeReadMethod(for field: ParsedField) -> String? {
    switch trimType(field.typeText) {
    case "Bool":
        return "readBool"
    case "Int8":
        return "readInt8"
    case "Int16":
        return "readInt16"
    case "Int32":
        return "readInt32"
    case "Int64":
        return "readInt64"
    case "Int":
        return "readInt"
    case "UInt8":
        return "readUInt8"
    case "UInt16":
        return "readUInt16"
    case "UInt32":
        return "readUInt32"
    case "UInt64":
        return "readUInt64"
    case "UInt":
        return "readUInt"
    case "Float":
        return "readFloat32"
    case "Double":
        return "readFloat64"
    default:
        return nil
    }
}

private func primitiveUnsafeFixedReadMethod(for field: ParsedField) -> String? {
    switch trimType(field.typeText) {
    case "Bool":
        return "readBoolUnchecked"
    case "Int8":
        return "readInt8Unchecked"
    case "UInt8":
        return "readUInt8Unchecked"
    case "Int16":
        return "readInt16Unchecked"
    case "UInt16":
        return "readUInt16Unchecked"
    case "Float":
        return "readFloat32Unchecked"
    case "Double":
        return "readFloat64Unchecked"
    default:
        return nil
    }
}

private func primitiveUnsafeFixedReadExpr(for field: ParsedField, baseExpr: String, offset: Int) -> String? {
    guard let method = primitiveUnsafeFixedReadMethod(for: field) else {
        return nil
    }
    return "UnsafeUtil.\(method)(from: \(baseExpr), index: \(offset))"
}

private func primitiveUnsafeReadAdvanceExpr(for field: ParsedField) -> String? {
    guard let method = primitiveUnsafeReadMethod(for: field) else {
        return nil
    }
    return "try UnsafeUtil.\(method)(from: __bytes, index: &__readerIndex)"
}

private func buildPrimitiveFastReadBlock(
    _ fields: [ParsedField],
    assignLine: (ParsedField, String) -> String
) -> String? {
    guard !fields.isEmpty else {
        return nil
    }
    let fixedFields = leadingFixedPrimitiveFields(fields)
    let remainingFields = Array(fields.dropFirst(fixedFields.count))
    let fixedPrefixBytes = primitiveFixedPrefixBytes(fixedFields)
    var fixedOffset = 0
    let fixedReads = fixedFields.compactMap { field -> String? in
        guard let readExpr = primitiveUnsafeFixedReadExpr(for: field, baseExpr: "__base", offset: fixedOffset) else {
            return nil
        }
        fixedOffset += primitiveFixedByteWidth(for: field) ?? 0
        return assignLine(field, readExpr)
    }.joined(separator: "\n            ")
    let remainingReads = remainingFields.compactMap { field -> String? in
        guard let readExpr = primitiveUnsafeReadAdvanceExpr(for: field) else {
            return nil
        }
        return assignLine(field, readExpr)
    }.joined(separator: "\n            ")
    var readSections: [String] = []
    if fixedPrefixBytes > 0 {
        readSections.append("try UnsafeUtil.checkReadable(__bytes, index: 0, need: \(fixedPrefixBytes))")
        readSections.append(
            """
            guard let __base = __bytes.baseAddress else {
                throw ForyError.outOfBounds(cursor: 0, need: \(fixedPrefixBytes), length: __bytes.count)
            }
            """
        )
        if !fixedReads.isEmpty {
            readSections.append(fixedReads)
        }
    }
    if !remainingReads.isEmpty {
        readSections.append("var __readerIndex = \(fixedPrefixBytes)")
        readSections.append(remainingReads)
    }
    let returnExpr = remainingReads.isEmpty ? "\(fixedPrefixBytes)" : "__readerIndex"
    readSections.append("return \(returnExpr)")
    let readBody = readSections.joined(separator: "\n            ")
    return """
    try UnsafeUtil.readNumericRegion(buffer: __buffer) { __bytes in
        \(readBody)
    }
    """
}

func buildPrimitiveFastClassReadBlock(_ fields: [ParsedField]) -> String? {
    buildPrimitiveFastReadBlock(fields) { field, readExpr in
        "value.\(field.name) = \(readExpr)"
    }
}

func buildPrimitiveFastStructReadDeclarations(_ fields: [ParsedField]) -> String? {
    guard !fields.isEmpty else {
        return nil
    }
    return fields.map { field in
        "var __\(field.name): \(field.typeText) = \(field.typeText).foryDefault()"
    }.joined(separator: "\n        ")
}

func buildPrimitiveFastStructReadBlock(_ fields: [ParsedField]) -> String? {
    buildPrimitiveFastReadBlock(fields) { field, readExpr in
        "__\(field.name) = \(readExpr)"
    }
}
