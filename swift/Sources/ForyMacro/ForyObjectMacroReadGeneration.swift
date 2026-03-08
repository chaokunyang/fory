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

func buildReadDataDecl(
    isClass: Bool,
    fields: [ParsedField],
    sortedFields: [ParsedField],
    accessPrefix: String
) -> String {
    if isClass {
        return buildClassReadDataDecl(sortedFields: sortedFields, accessPrefix: accessPrefix)
    }
    if fields.isEmpty {
        return buildEmptyStructReadDataDecl(accessPrefix: accessPrefix)
    }
    return buildStructReadDataDecl(fields: fields, sortedFields: sortedFields, accessPrefix: accessPrefix)
}

private func buildClassReadDataDecl(
    sortedFields: [ParsedField],
    accessPrefix: String
) -> String {
    let primitiveFastFields = leadingPrimitiveFastPathFields(sortedFields)
    let schemaAssignBody = buildClassAssignBody(sortedFields: sortedFields, primitiveFastFields: primitiveFastFields, compatibleAligned: false)
    let compatibleAlignedAssignBody = buildClassAssignBody(
        sortedFields: sortedFields,
        primitiveFastFields: primitiveFastFields,
        compatibleAligned: true
    )
    let compatibleCases = buildCompatibleReadCases(sortedFields: sortedFields, indent: "                ") { sortedIndex, field, valueExpr in
        "case \(sortedIndex): value.\(field.name) = \(valueExpr)"
    }

    return """
    @inline(__always)
    \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
        let __buffer = context.buffer
        if context.compatible, let compatibleFields = context.compatibleFields(for: Self.self) {
            if compatibleFields.canUseSchemaFastPath {
                let value = Self.init()
                context.bindPendingRef(value)
                \(schemaAssignBody)
                return value
            }
            if compatibleFields.canUseSchemaOrderReadPath {
                let value = Self.init()
                context.bindPendingRef(value)
                \(compatibleAlignedAssignBody)
                return value
            }
            let value = Self.init()
            context.bindPendingRef(value)
            for remoteField in compatibleFields.fields {
                switch Int(remoteField.fieldID ?? -1) {
            \(compatibleCases)
                default:
                    try context.skipFieldValue(remoteField.fieldType)
                }
            }
            return value
        }
        \(schemaHashCheckExpr())
        let value = Self.init()
        context.bindPendingRef(value)
        \(schemaAssignBody)
        return value
    }
    """
}

private func buildEmptyStructReadDataDecl(accessPrefix: String) -> String {
    """
    @inline(__always)
    \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
        let __buffer = context.buffer
        if context.compatible, let compatibleFields = context.compatibleFields(for: Self.self) {
            if compatibleFields.canUseSchemaFastPath || compatibleFields.canUseSchemaOrderReadPath {
                return Self()
            }
            for remoteField in compatibleFields.fields {
                try context.skipFieldValue(remoteField.fieldType)
            }
            return Self()
        }
        \(schemaHashCheckExpr())
        return Self()
    }
    """
}

private func buildStructReadDataDecl(
    fields: [ParsedField],
    sortedFields: [ParsedField],
    accessPrefix: String
) -> String {
    let primitiveFastFields = leadingPrimitiveFastPathFields(sortedFields)
    let schemaReadBody = buildStructReadBody(
        sortedFields: sortedFields,
        primitiveFastFields: primitiveFastFields,
        compatibleAligned: false
    )
    let compatibleAlignedReadBody = buildStructReadBody(
        sortedFields: sortedFields,
        primitiveFastFields: primitiveFastFields,
        compatibleAligned: true
    )
    let ctorArgs = buildCtorArgs(fields)
    let compatibleDefaults = buildStructCompatibleDefaults(fields)
    let compatibleCases = buildCompatibleReadCases(sortedFields: sortedFields, indent: "                    ") { sortedIndex, field, valueExpr in
        "case \(sortedIndex): __\(field.name) = \(valueExpr)"
    }

    return """
    @inline(__always)
    \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
        let __buffer = context.buffer
        if context.compatible, let compatibleFields = context.compatibleFields(for: Self.self) {
                if compatibleFields.canUseSchemaFastPath {
                    \(schemaReadBody)
                    return Self(
                        \(ctorArgs)
                    )
                }
                if compatibleFields.canUseSchemaOrderReadPath {
                    \(compatibleAlignedReadBody)
                    return Self(
                        \(ctorArgs)
                    )
                }
                \(compatibleDefaults)
                for remoteField in compatibleFields.fields {
                    switch Int(remoteField.fieldID ?? -1) {
                    \(compatibleCases)
                    default:
                        try context.skipFieldValue(remoteField.fieldType)
                    }
                }
                return Self(
                    \(ctorArgs)
                )
            }
        \(schemaHashCheckExpr())
        \(schemaReadBody)
        return Self(
            \(ctorArgs)
        )
    }
    """
}

private func buildClassAssignBody(
    sortedFields: [ParsedField],
    primitiveFastFields: [ParsedField],
    compatibleAligned: Bool
) -> String {
    let remainingAssignLines = sortedFields.dropFirst(primitiveFastFields.count).map { field -> String in
        let valueExpr: String
        if compatibleAligned {
            valueExpr = compatibleSchemaReadFieldExpr(field)
        } else {
            valueExpr = readFieldExpr(
                field,
                refModeExpr: fieldRefModeExpression(field),
                readTypeInfoExpr: "false"
            )
        }
        return "value.\(field.name) = \(valueExpr)"
    }

    var sections: [String] = []
    if let primitiveReadBlock = buildPrimitiveFastClassReadBlock(primitiveFastFields) {
        sections.append(primitiveReadBlock)
    }
    if !remainingAssignLines.isEmpty {
        sections.append(remainingAssignLines.joined(separator: "\n        "))
    }
    if sections.isEmpty {
        sections.append("_ = context")
    }
    return sections.joined(separator: "\n        ")
}

private func buildStructReadBody(
    sortedFields: [ParsedField],
    primitiveFastFields: [ParsedField],
    compatibleAligned: Bool
) -> String {
    let remainingReadLines = sortedFields.dropFirst(primitiveFastFields.count).map { field -> String in
        let valueExpr = compatibleAligned ? compatibleSchemaReadFieldExpr(field) : schemaReadFieldExpr(field)
        return "let __\(field.name) = \(valueExpr)"
    }

    var sections: [String] = []
    if let primitiveDeclarations = buildPrimitiveFastStructReadDeclarations(primitiveFastFields) {
        sections.append(primitiveDeclarations)
    }
    if let primitiveReadBlock = buildPrimitiveFastStructReadBlock(primitiveFastFields) {
        sections.append(primitiveReadBlock)
    }
    if !remainingReadLines.isEmpty {
        sections.append(remainingReadLines.joined(separator: "\n        "))
    }
    return sections.joined(separator: "\n        ")
}

private func buildCtorArgs(_ fields: [ParsedField]) -> String {
    fields
        .sorted(by: { $0.originalIndex < $1.originalIndex })
        .map { "\($0.name): __\($0.name)" }
        .joined(separator: ",\n            ")
}

private func buildStructCompatibleDefaults(_ fields: [ParsedField]) -> String {
    fields
        .sorted(by: { $0.originalIndex < $1.originalIndex })
        .map { field in
            if field.dynamicAnyCodec != nil {
                return "var __\(field.name): \(field.typeText) = \(fieldDefaultExpr(field))"
            }
            return "var __\(field.name) = \(fieldDefaultExpr(field))"
        }
        .joined(separator: "\n                ")
}

private func schemaHashCheckExpr(indent: String = "        ") -> String {
    """
    \(indent)if context.checkClassVersion {
    \(indent)    let __schemaHash = UInt32(bitPattern: try __buffer.readInt32())
    \(indent)    let __expectedHash = Self.__forySchemaHash(context.trackRef)
    \(indent)    if __schemaHash != __expectedHash {
    \(indent)        throw ForyError.invalidData("class version hash mismatch: expected \\(__expectedHash), got \\(__schemaHash)")
    \(indent)    }
    \(indent)}
    """
}

private func buildCompatibleReadCases(
    sortedFields: [ParsedField],
    indent: String,
    assignCase: (Int, ParsedField, String) -> String
) -> String {
    sortedFields.enumerated().map { sortedIndex, field -> String in
        let valueExpr = readFieldExpr(
            field,
            refModeExpr: "RefMode.from(nullable: remoteField.fieldType.nullable, trackRef: remoteField.fieldType.trackRef)",
            readTypeInfoExpr: "TypeId.needsTypeInfoForField(TypeId(rawValue: remoteField.fieldType.typeID) ?? .unknown)"
        )
        return assignCase(sortedIndex, field, valueExpr)
    }.joined(separator: "\n\(indent)")
}

private func readFieldExpr(
    _ field: ParsedField,
    refModeExpr: String,
    readTypeInfoExpr: String
) -> String {
    if let dynamicAnyCodec = field.dynamicAnyCodec {
        return dynamicAnyReadExpr(
            field: field,
            dynamicAnyCodec: dynamicAnyCodec,
            refModeExpr: refModeExpr
        )
    }
    if let codecType = field.customCodecType {
        if field.isOptional {
            return "try \(codecType)?.foryRead(context, refMode: \(refModeExpr), readTypeInfo: false)?.rawValue"
        }
        return "try \(codecType).foryRead(context, refMode: \(refModeExpr), readTypeInfo: false).rawValue"
    }
    return "try \(field.typeText).foryRead(context, refMode: \(refModeExpr), readTypeInfo: \(readTypeInfoExpr))"
}

private func schemaReadFieldExpr(_ field: ParsedField) -> String {
    if field.dynamicAnyCodec != nil || field.customCodecType != nil || field.isOptional || field.typeID == 27 {
        return readFieldExpr(
            field,
            refModeExpr: fieldRefModeExpression(field),
            readTypeInfoExpr: "false"
        )
    }
    if let primitiveExpr = primitiveSchemaReadExpr(field) {
        return primitiveExpr
    }
    return "try \(field.typeText).foryReadData(context)"
}

private func compatibleSchemaReadFieldExpr(_ field: ParsedField) -> String {
    if field.dynamicAnyCodec != nil || field.customCodecType != nil || field.isOptional || field.typeID == 27 || compatibleFieldNeedsTypeInfo(field) {
        return readFieldExpr(
            field,
            refModeExpr: fieldRefModeExpression(field),
            readTypeInfoExpr: "TypeId.needsTypeInfoForField(\(field.typeText).staticTypeId)"
        )
    }
    if let primitiveExpr = primitiveSchemaReadExpr(field) {
        return primitiveExpr
    }
    return "try \(field.typeText).foryReadData(context)"
}

private func primitiveSchemaReadExpr(_ field: ParsedField) -> String? {
    let type = trimType(field.typeText)
    switch type {
    case "Bool":
        return "try __buffer.readUInt8() != 0"
    case "Int8":
        return "try __buffer.readInt8()"
    case "Int16":
        return "try __buffer.readInt16()"
    case "Int32":
        return "try __buffer.readVarInt32()"
    case "Int64":
        return "try __buffer.readVarInt64()"
    case "Int":
        return "Int(try __buffer.readVarInt64())"
    case "UInt8":
        return "try __buffer.readUInt8()"
    case "UInt16":
        return "try __buffer.readUInt16()"
    case "UInt32":
        return "try __buffer.readVarUInt32()"
    case "UInt64":
        return "try __buffer.readVarUInt64()"
    case "UInt":
        return "UInt(try __buffer.readVarUInt64())"
    case "Float":
        return "try __buffer.readFloat32()"
    case "Double":
        return "try __buffer.readFloat64()"
    default:
        return nil
    }
}

private func dynamicAnyReadExpr(
    field: ParsedField,
    dynamicAnyCodec: DynamicAnyCodecKind,
    refModeExpr: String
) -> String {
    let metatypeExpr = "(\(field.typeText)).self"
    let method = dynamicAnyReadMethodName(dynamicAnyCodec)
    let readTypeInfoExpr = dynamicAnyCodec == .anyValue || dynamicAnyCodec == .anyHashableValue
        ? ", readTypeInfo: true"
        : ""
    return "try castAnyDynamicValue(context.\(method)(refMode: \(refModeExpr)\(readTypeInfoExpr)), to: \(metatypeExpr))"
}
