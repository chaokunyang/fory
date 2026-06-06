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
  return buildStructReadDataDecl(
    fields: fields, sortedFields: sortedFields, accessPrefix: accessPrefix)
}

func buildReadCompatibleDataDecl(
  isClass: Bool,
  fields: [ParsedField],
  sortedFields: [ParsedField],
  accessPrefix: String
) -> String {
  if isClass {
    return buildClassReadCompatibleDataDecl(sortedFields: sortedFields, accessPrefix: accessPrefix)
  }
  if fields.isEmpty {
    return buildEmptyStructReadCompatibleDataDecl(accessPrefix: accessPrefix)
  }
  return buildStructReadCompatibleDataDecl(
    fields: fields, sortedFields: sortedFields, accessPrefix: accessPrefix)
}

func buildClassReadWrapperDecl(accessPrefix: String) -> String {
  """
  @inline(__always)
  \(accessPrefix)static func foryRead(
      _ context: ReadContext,
      refMode: RefMode,
      readTypeInfo: Bool
  ) throws -> Self {
      let __buffer = context.buffer
      let __reservedRefID: UInt32?
      if refMode != .none {
          let rawFlag = try __buffer.readInt8()
          guard let flag = RefFlag(rawValue: rawFlag) else {
              throw ForyError.refError("invalid ref flag \\(rawFlag)")
          }

          switch flag {
          case .null:
              return Self.foryDefault()
          case .ref:
              let refID = try __buffer.readVarUInt32()
              return try context.refReader.readRef(refID, as: Self.self)
          case .refValue:
              __reservedRefID = context.trackRef ? context.refReader.reserveRefID() : nil
          case .notNullValue:
              __reservedRefID = nil
          }
      } else {
          __reservedRefID = nil
      }

      return try Self.foryReadPayload(
          context,
          readTypeInfo: readTypeInfo,
          readData: {
              try Self.__foryReadDataImpl(context, reservedRefID: __reservedRefID)
          },
          readCompatibleData: { remoteTypeInfo in
              try Self.__foryReadCompatibleDataImpl(
                  context,
                  remoteTypeInfo: remoteTypeInfo,
                  reservedRefID: __reservedRefID
              )
          }
      )
  }
  """
}

private func buildClassReadDataDecl(
  sortedFields: [ParsedField],
  accessPrefix: String
) -> String {
  let primitiveFastFields = leadingPrimitiveFastPathFields(sortedFields)
  let schemaAssignBody = buildClassAssignBody(
    sortedFields: sortedFields, primitiveFastFields: primitiveFastFields, compatibleAligned: false)

  return """
    @inline(__always)
    private static func __foryReadDataImpl(_ context: ReadContext, reservedRefID: UInt32?) throws -> Self {
        let __buffer = context.buffer
        \(schemaHashCheckExpr())
        let value = Self.init()
        if let reservedRefID {
            context.refReader.storeRef(value, at: reservedRefID)
        }
        \(schemaAssignBody)
        return value
    }

    @inline(__always)
    \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
        try Self.__foryReadDataImpl(context, reservedRefID: nil)
    }
    """
}

private func buildEmptyStructReadDataDecl(accessPrefix: String) -> String {
  """
  @inline(__always)
  \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
      let __buffer = context.buffer
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
  let ctorArgs = buildCtorArgs(fields)

  return """
    @inline(__always)
    \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
        let __buffer = context.buffer
        \(schemaHashCheckExpr())
        \(schemaReadBody)
        return Self(
            \(ctorArgs)
        )
    }
    """
}

private func buildClassReadCompatibleDataDecl(
  sortedFields: [ParsedField],
  accessPrefix: String
) -> String {
  let primitiveFastFields = leadingPrimitiveFastPathFields(sortedFields)
  let schemaAssignBody = buildClassAssignBody(
    sortedFields: sortedFields, primitiveFastFields: primitiveFastFields, compatibleAligned: false)
  let compatibleAlignedAssignBody = buildClassAssignBody(
    sortedFields: sortedFields,
    primitiveFastFields: primitiveFastFields,
    compatibleAligned: true
  )
  let compatibleCases = buildCompatibleReadCases(
    sortedFields: sortedFields, indent: "                "
  ) { sortedIndex, field, valueExpr in
    "case \(sortedIndex): value.\(field.name) = \(valueExpr)"
  }
  let bufferBinding =
    (schemaAssignBody.contains("__buffer") || compatibleAlignedAssignBody.contains("__buffer")
      || compatibleCases.contains("__buffer")) ? "let __buffer = context.buffer\n        " : ""

  return """
    @inline(never)
    private static func __foryReadCompatibleDataImpl(
        _ context: ReadContext,
        remoteTypeInfo: TypeInfo,
        reservedRefID: UInt32?
    ) throws -> Self {
        \(bufferBinding)guard let typeMeta = remoteTypeInfo.compatibleTypeMeta else {
            throw ForyError.invalidData("compatible type metadata is required")
        }
        let value = Self.init()
        if let reservedRefID {
            context.refReader.storeRef(value, at: reservedRefID)
        }
        if let localHeaderHash = remoteTypeInfo.typeDefHeaderHash,
           typeMeta.headerHash == localHeaderHash,
           typeMeta.fields == Self.foryFieldsInfo(trackRef: context.trackRef) {
            if !remoteTypeInfo.typeDefHasUserTypeFields {
                \(schemaAssignBody)
                return value
            }
            \(compatibleAlignedAssignBody)
            return value
        }
        for remoteField in typeMeta.fields {
            switch Int(remoteField.fieldID ?? -1) {
        \(compatibleCases)
            case -1:
                try context.skipFieldValue(remoteField.fieldType)
            default:
                throw ForyError.invalidData("invalid compatible matched id \\(remoteField.fieldID ?? -2)")
            }
        }
        return value
    }

    @inline(never)
    \(accessPrefix)static func foryReadCompatibleData(_ context: ReadContext, remoteTypeInfo: TypeInfo) throws -> Self {
        try Self.__foryReadCompatibleDataImpl(context, remoteTypeInfo: remoteTypeInfo, reservedRefID: nil)
    }
    """
}

private func buildEmptyStructReadCompatibleDataDecl(accessPrefix: String) -> String {
  """
  @inline(never)
  \(accessPrefix)static func foryReadCompatibleData(_ context: ReadContext, remoteTypeInfo: TypeInfo) throws -> Self {
      guard let typeMeta = remoteTypeInfo.compatibleTypeMeta else {
          throw ForyError.invalidData("compatible type metadata is required")
      }
      if let localHeaderHash = remoteTypeInfo.typeDefHeaderHash,
         typeMeta.headerHash == localHeaderHash,
         typeMeta.fields == Self.foryFieldsInfo(trackRef: context.trackRef) {
          return Self()
      }
      for remoteField in typeMeta.fields {
          try context.skipFieldValue(remoteField.fieldType)
      }
      return Self()
  }
  """
}

private func buildStructReadCompatibleDataDecl(
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
  let compatibleCases = buildCompatibleReadCases(
    sortedFields: sortedFields, indent: "                    "
  ) { sortedIndex, field, valueExpr in
    "case \(sortedIndex): __\(field.name) = \(valueExpr)"
  }
  let changedFallbackDecl = buildStructChangedFallbackDecl(
    defaults: compatibleDefaults,
    cases: compatibleCases,
    ctorArgs: ctorArgs,
    sortedFields: sortedFields
  )
  let sequentialLeadingCompatBody = buildSequentialCompatStructBody(
    fields: fields,
    sortedFields: sortedFields,
    defaults: compatibleDefaults,
    declareIndex: true,
    includeExact: false,
    includeLeadingCompat: true
  )
  let sequentialCompatBody = buildSequentialCompatStructBody(
    fields: fields,
    sortedFields: sortedFields,
    defaults: compatibleDefaults,
    declareIndex: false,
    includeExact: true,
    includeLeadingCompat: false
  )
  let bufferBinding =
    (schemaReadBody.contains("__buffer") || compatibleAlignedReadBody.contains("__buffer")
      || sequentialLeadingCompatBody.contains("__buffer")
      || sequentialCompatBody.contains("__buffer"))
    ? "let __buffer = context.buffer\n        " : ""

  return """
    \(changedFallbackDecl)

    @inline(never)
    \(accessPrefix)static func foryReadCompatibleData(_ context: ReadContext, remoteTypeInfo: TypeInfo) throws -> Self {
        \(bufferBinding)guard let typeMeta = remoteTypeInfo.compatibleTypeMeta else {
            throw ForyError.invalidData("compatible type metadata is required")
        }
        \(sequentialLeadingCompatBody)
        if let localHeaderHash = remoteTypeInfo.typeDefHeaderHash,
           typeMeta.headerHash == localHeaderHash,
           typeMeta.fields == Self.foryFieldsInfo(trackRef: context.trackRef) {
            if !remoteTypeInfo.typeDefHasUserTypeFields {
                \(schemaReadBody)
                return Self(
                    \(ctorArgs)
                )
            }
            \(compatibleAlignedReadBody)
            return Self(
                \(ctorArgs)
            )
        }
        \(sequentialCompatBody)
        return try Self.__foryReadChangedData(
            context,
            typeMeta: typeMeta,
            readPlan: __compatibleIndex
        )
    }
    """
}

private func buildStructChangedFallbackDecl(
  defaults: String,
  cases: String,
  ctorArgs: String,
  sortedFields: [ParsedField]
) -> String {
  let remoteOrderFastPaths = buildRemoteOrderSingleCompatStructPaths(
    sortedFields: sortedFields,
    ctorArgs: ctorArgs
  )
  let bufferBinding =
    (cases.contains("__buffer") || remoteOrderFastPaths.contains("__buffer"))
    ? "let __buffer = context.buffer\n        " : ""
  return """
      @inline(never)
      private static func __foryReadChangedData(
          _ context: ReadContext,
          typeMeta: TypeMeta,
          readPlan: Int
      ) throws -> Self {
          \(bufferBinding)\(remoteOrderFastPaths)
          \(defaults)
          for remoteField in typeMeta.fields {
              switch Int(remoteField.fieldID ?? -1) {
              \(cases)
              case -1:
                  try context.skipFieldValue(remoteField.fieldType)
              default:
                  throw ForyError.invalidData("invalid compatible matched id \\(remoteField.fieldID ?? -2)")
              }
          }
          return Self(
              \(ctorArgs)
          )
      }
    """
}

private func buildRemoteOrderSingleCompatStructPaths(
  sortedFields: [ParsedField],
  ctorArgs: String
) -> String {
  var sections: [String] = []
  for compatibleIndex in sortedFields.indices {
    let compatibleField = sortedFields[compatibleIndex]
    guard remoteOrderSingleCompatEligible(compatibleField) else {
      continue
    }
    let remoteOrder = remoteOrderSingleVarint32(sortedFields, compatibleIndex: compatibleIndex)
    guard !remoteOrder.isEmpty,
      !remoteOrder.enumerated().allSatisfy({ $0.offset == $0.element })
    else {
      continue
    }
    let readPlan = -3 - compatibleIndex
    let readBody = remoteOrderReadBody(
      sortedFields: sortedFields,
      remoteOrder: remoteOrder,
      compatibleIndex: compatibleIndex
    )
    sections.append(
      """
      if readPlan == \(readPlan) {
          \(readBody)
          return Self(
              \(ctorArgs)
          )
      }
      """)
  }
  return sections.joined(separator: "\n        ")
}

private func remoteOrderReadBody(
  sortedFields: [ParsedField],
  remoteOrder: [Int],
  compatibleIndex: Int
) -> String {
  var sections: [String] = []
  var primitiveLines: [String] = []
  var declaredPrimitives: Set<String> = []

  func flushPrimitiveLines() {
    guard !primitiveLines.isEmpty else {
      return
    }
    let body = primitiveLines.joined(separator: "\n              ")
    sections.append(
      """
      try UnsafeUtil.readRegion(buffer: __buffer) { __base, __length in
          var __readerIndex = 0
          \(body)
          return __readerIndex
      }
      """)
    primitiveLines.removeAll(keepingCapacity: true)
  }

  for localIndex in remoteOrder {
    let field = sortedFields[localIndex]
    if localIndex == compatibleIndex {
      if declaredPrimitives.insert(field.name).inserted {
        sections.append("var __\(field.name): \(field.typeText) = \(field.typeText).foryDefault()")
      }
      primitiveLines.append(
        "__\(field.name) = Int64(try UnsafeUtil.readInt32(from: __base, length: __length, index: &__readerIndex))"
      )
      continue
    }
    if let readExpr = primitiveRemoteOrderReadAdvanceExpr(for: field) {
      if declaredPrimitives.insert(field.name).inserted {
        sections.append("var __\(field.name): \(field.typeText) = \(field.typeText).foryDefault()")
      }
      primitiveLines.append(readExpr)
      continue
    }
    flushPrimitiveLines()
    sections.append("let __\(field.name) = \(compatibleSchemaReadFieldExpr(field))")
  }
  flushPrimitiveLines()
  return sections.joined(separator: "\n            ")
}

private func primitiveRemoteOrderReadAdvanceExpr(for field: ParsedField) -> String? {
  let fixedRead: String
  let width: Int
  switch trimType(field.typeText) {
  case "Bool":
    fixedRead = "UnsafeUtil.readBoolUnchecked(from: __base, index: __readerIndex)"
    width = 1
  case "Int8":
    fixedRead = "UnsafeUtil.readInt8Unchecked(from: __base, index: __readerIndex)"
    width = 1
  case "UInt8":
    fixedRead = "UnsafeUtil.readUInt8Unchecked(from: __base, index: __readerIndex)"
    width = 1
  case "Int16":
    fixedRead = "UnsafeUtil.readInt16Unchecked(from: __base, index: __readerIndex)"
    width = 2
  case "UInt16":
    fixedRead = "UnsafeUtil.readUInt16Unchecked(from: __base, index: __readerIndex)"
    width = 2
  case "Float":
    fixedRead = "UnsafeUtil.readFloat32Unchecked(from: __base, index: __readerIndex)"
    width = 4
  case "Double":
    fixedRead = "UnsafeUtil.readFloat64Unchecked(from: __base, index: __readerIndex)"
    width = 8
  default:
    if let readExpr = primitiveUnsafePointerReadAdvanceExpr(for: field) {
      return "__\(field.name) = \(readExpr)"
    }
    return nil
  }
  return """
    try UnsafeUtil.checkReadable(length: __length, index: __readerIndex, need: \(width))
    __\(field.name) = \(fixedRead)
    __readerIndex += \(width)
    """
}

private func remoteOrderSingleCompatEligible(_ field: ParsedField) -> Bool {
  !field.isOptional && field.dynamicAnyCodec == nil && field.customCodecType == nil
    && compatibleScalarPayloadType(field.typeText) == "Int64"
}

private struct RemoteOrderSortField {
  let field: ParsedField
  let localIndex: Int
  let group: Int
  let typeID: UInt32
  let isCompressedNumeric: Bool
  let primitiveSize: Int
}

private func remoteOrderSingleVarint32(
  _ sortedFields: [ParsedField],
  compatibleIndex: Int
) -> [Int] {
  let fields = sortedFields.enumerated().map { index, field in
    if index == compatibleIndex {
      return RemoteOrderSortField(
        field: field,
        localIndex: index,
        group: field.group,
        typeID: 5,
        isCompressedNumeric: true,
        primitiveSize: 4
      )
    }
    return RemoteOrderSortField(
      field: field,
      localIndex: index,
      group: field.group,
      typeID: field.typeID,
      isCompressedNumeric: field.isCompressedNumeric,
      primitiveSize: field.primitiveSize
    )
  }
  return fields.sorted(by: remoteOrderSortLess).map(\.localIndex)
}

private func remoteOrderSortLess(
  _ lhs: RemoteOrderSortField,
  _ rhs: RemoteOrderSortField
) -> Bool {
  if lhs.group != rhs.group {
    return lhs.group < rhs.group
  }
  switch lhs.group {
  case 1, 2:
    let lhsCompressed = lhs.isCompressedNumeric ? 1 : 0
    let rhsCompressed = rhs.isCompressedNumeric ? 1 : 0
    if lhsCompressed != rhsCompressed {
      return lhsCompressed < rhsCompressed
    }
    if lhs.primitiveSize != rhs.primitiveSize {
      return lhs.primitiveSize > rhs.primitiveSize
    }
    if lhs.typeID != rhs.typeID {
      return lhs.typeID < rhs.typeID
    }
    if let identifierOrder = remoteOrderIdentifierLess(lhs.field, rhs.field) {
      return identifierOrder
    }
  default:
    if let identifierOrder = remoteOrderIdentifierLess(lhs.field, rhs.field) {
      return identifierOrder
    }
  }
  return lhs.field.name < rhs.field.name
}

private func remoteOrderIdentifierLess(_ lhs: ParsedField, _ rhs: ParsedField) -> Bool? {
  if let lhsID = lhs.fieldID, let rhsID = rhs.fieldID, lhsID != rhsID {
    return lhsID < rhsID
  }
  if lhs.fieldID != nil && rhs.fieldID == nil {
    return true
  }
  if lhs.fieldID == nil && rhs.fieldID != nil {
    return false
  }
  if lhs.fieldIdentifier != rhs.fieldIdentifier {
    return lhs.fieldIdentifier < rhs.fieldIdentifier
  }
  return nil
}

private func buildClassAssignBody(
  sortedFields: [ParsedField],
  primitiveFastFields: [ParsedField],
  compatibleAligned: Bool
) -> String {
  let remainingAssignLines = sortedFields.dropFirst(primitiveFastFields.count).map {
    field -> String in
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
  let remainingReadLines = sortedFields.dropFirst(primitiveFastFields.count).map {
    field -> String in
    let valueExpr =
      compatibleAligned ? compatibleSchemaReadFieldExpr(field) : schemaReadFieldExpr(field)
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
    .map(compatibleDefaultDecl)
    .joined(separator: "\n                ")
}

private func buildSequentialCompatStructBody(
  fields: [ParsedField],
  sortedFields: [ParsedField],
  defaults: String,
  declareIndex: Bool = true,
  includeExact: Bool = true,
  includeLeadingCompat: Bool = true
) -> String {
  let ctorArgs = buildCtorArgs(fields)
  let exactAssignBody = buildSequentialExactAssignBody(sortedFields)
  let leadingCompatBody =
    sortedFields.first.map { compatibleField in
      buildSequentialSingleCompatAssignBody(
        sortedFields: sortedFields,
        compatibleIndex: 0,
        compatibleField: compatibleField
      )
    } ?? ""

  var sections: [String] = []
  if declareIndex {
    sections.append("let __compatibleIndex = remoteTypeInfo.compatibleSequentialReadPlan")
  }
  if includeExact {
    sections.append(
      """
          if __compatibleIndex == -1 {
              \(defaults)
              \(exactAssignBody)
              return Self(
                  \(ctorArgs)
              )
          }
      """)
  }
  if includeLeadingCompat {
    sections.append(
      """
          if __compatibleIndex == 0 {
              \(defaults)
              \(leadingCompatBody)
              return Self(
                  \(ctorArgs)
              )
          }
      """)
  }
  return sections.joined(separator: "\n          ")
}

private func buildSequentialExactAssignBody(_ fields: [ParsedField]) -> String {
  buildSequentialExactAssignBody(fields, excludingIndex: -1)
}

private func buildSequentialSingleCompatAssignBody(
  sortedFields: [ParsedField],
  compatibleIndex: Int,
  compatibleField: ParsedField
) -> String {
  let compatibleValueExpr = readFieldExpr(
    compatibleField,
    refModeExpr:
      "RefMode.from(nullable: remoteField.fieldType.nullable, trackRef: remoteField.fieldType.trackRef)",
    readTypeInfoExpr:
      "TypeId.needsTypeInfoForField(TypeId(rawValue: remoteField.fieldType.typeID) ?? .unknown)"
  )
  let valueExpr = compatibleScalarReadExpr(
    compatibleField,
    compatibleValueExpr: compatibleValueExpr
  )
  if let fastBody = leadingCompatInt64Body(
    sortedFields: sortedFields,
    compatibleIndex: compatibleIndex,
    compatibleField: compatibleField,
    fallbackExpr: valueExpr
  ) {
    return fastBody
  }
  let compatibleAssign =
    compatScalarAssignBody(
      field: compatibleField,
      fieldIndex: compatibleIndex,
      fallbackExpr: valueExpr
    )
      ?? """
      let remoteField = typeMeta.fields[\(compatibleIndex)]
                      __\(compatibleField.name) = \(valueExpr)
      """
  var sections: [String] = []
  let prefixFields = Array(sortedFields.prefix(compatibleIndex))
  if !prefixFields.isEmpty {
    sections.append(buildSequentialExactAssignBody(prefixFields))
  }
  sections.append(compatibleAssign)
  let suffixFields = Array(sortedFields.dropFirst(compatibleIndex + 1))
  if !suffixFields.isEmpty {
    sections.append(buildSequentialExactAssignBody(suffixFields))
  }
  return sections.joined(separator: "\n                    ")
}

private func leadingCompatInt64Body(
  sortedFields: [ParsedField],
  compatibleIndex: Int,
  compatibleField: ParsedField,
  fallbackExpr: String
) -> String? {
  guard
    compatibleIndex == 0,
    !compatibleField.isOptional,
    compatibleField.dynamicAnyCodec == nil,
    compatibleField.customCodecType == nil,
    compatibleScalarPayloadType(compatibleField.typeText) == "Int64"
  else {
    return nil
  }
  let suffixFields = Array(sortedFields.dropFirst())
  guard leadingPrimitiveFastPathFields(suffixFields).count == suffixFields.count else {
    return nil
  }
  guard
    let fastReadBlock = compatInt64Varint32Block(
      compatibleField: compatibleField,
      suffixFields: suffixFields
    )
  else {
    return nil
  }
  let fallbackSuffix = buildSequentialExactAssignBody(suffixFields)
  return """
    let remoteField = typeMeta.fields[0]
                    if !remoteField.fieldType.nullable && !remoteField.fieldType.trackRef
                        && remoteField.fieldType.typeID == TypeId.varint32.rawValue {
                        \(fastReadBlock)
                    } else {
                        __\(compatibleField.name) = \(fallbackExpr)
                        \(fallbackSuffix)
                    }
    """
}

private func compatInt64Varint32Block(
  compatibleField: ParsedField,
  suffixFields: [ParsedField]
) -> String? {
  var readLines = [
    "__\(compatibleField.name) = Int64(try UnsafeUtil.readInt32(from: __base, length: __length, index: &__readerIndex))"
  ]
  for field in suffixFields {
    guard let readExpr = primitiveRemoteOrderReadAdvanceExpr(for: field) else {
      return nil
    }
    readLines.append(readExpr)
  }
  let readBody = readLines.joined(separator: "\n            ")
  return """
    try UnsafeUtil.readRegion(buffer: __buffer) { __base, __length in
        var __readerIndex = 0
        \(readBody)
        return __readerIndex
    }
    """
}

private func compatScalarAssignBody(
  field: ParsedField,
  fieldIndex: Int,
  fallbackExpr: String
) -> String? {
  guard
    !field.isOptional,
    field.dynamicAnyCodec == nil,
    field.customCodecType == nil,
    compatibleScalarPayloadType(field.typeText) == "Int64"
  else {
    return nil
  }
  return """
    let remoteField = typeMeta.fields[\(fieldIndex)]
                    if !remoteField.fieldType.nullable && !remoteField.fieldType.trackRef {
                        switch TypeId(rawValue: remoteField.fieldType.typeID) ?? .unknown {
                        case .int8:
                            __\(field.name) = Int64(try __buffer.readInt8())
                        case .int16:
                            __\(field.name) = Int64(try __buffer.readInt16())
                        case .int32:
                            __\(field.name) = Int64(try __buffer.readInt32())
                        case .varint32:
                            __\(field.name) = Int64(try __buffer.readVarInt32())
                        case .int64:
                            __\(field.name) = try __buffer.readInt64()
                        case .varint64:
                            __\(field.name) = try __buffer.readVarInt64()
                        case .taggedInt64:
                            __\(field.name) = try __buffer.readTaggedInt64()
                        default:
                            __\(field.name) = \(fallbackExpr)
                        }
                    } else {
                        __\(field.name) = \(fallbackExpr)
                    }
    """
}

private func buildSequentialExactAssignBody(
  _ fields: [ParsedField],
  excludingIndex: Int
) -> String {
  var sections: [String] = []
  var index = 0
  while index < fields.count {
    if index == excludingIndex {
      index += 1
      continue
    }
    let remaining = Array(fields.dropFirst(index))
    let primitiveFields = leadingPrimitiveFastPathFields(remaining)
    if !primitiveFields.isEmpty {
      if let primitiveReadBlock = buildPrimitiveFastStructReadBlock(primitiveFields) {
        sections.append(primitiveReadBlock)
      }
      index += primitiveFields.count
      continue
    }
    let field = fields[index]
    sections.append("__\(field.name) = \(compatibleSchemaReadFieldExpr(field))")
    index += 1
  }
  return sections.joined(separator: "\n                    ")
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
    let directValueExpr = compatibleSchemaReadFieldExpr(field)
    let compatibleValueExpr = readFieldExpr(
      field,
      refModeExpr:
        "RefMode.from(nullable: remoteField.fieldType.nullable, trackRef: remoteField.fieldType.trackRef)",
      readTypeInfoExpr:
        "TypeId.needsTypeInfoForField(TypeId(rawValue: remoteField.fieldType.typeID) ?? .unknown)"
    )
    let compatibleCaseExpr = compatibleScalarReadExpr(
      field,
      compatibleValueExpr: compatibleValueExpr
    )
    return [
      assignCase(sortedIndex * 2, field, directValueExpr),
      assignCase(sortedIndex * 2 + 1, field, compatibleCaseExpr),
    ].joined(separator: "\n\(indent)")
  }.joined(separator: "\n\(indent)")
}

private func compatibleScalarReadExpr(_ field: ParsedField, compatibleValueExpr: String) -> String {
  guard
    field.dynamicAnyCodec == nil,
    let helperTarget = compatibleScalarReaderTarget(field)
  else {
    return compatibleValueExpr
  }
  let fieldName = swiftStringLiteral(field.schemaIdentifier)
  let helperName =
    field.isOptional
    ? "foryReadCompatibleOptional\(helperTarget)Field"
    : "foryReadCompatible\(helperTarget)Field"
  return """
    try \(helperName)(
        context,
        remoteFieldType: remoteField.fieldType,
        localTypeID: \(field.typeID),
        fieldName: \(fieldName)
    )
    """
}

private func compatibleScalarReaderTarget(_ field: ParsedField) -> String? {
  guard compatibleScalarTypeID(field.typeID) else {
    return nil
  }
  switch compatibleScalarPayloadType(field.typeText) {
  case "Bool":
    return "Bool"
  case "Int8":
    return "Int8"
  case "Int16":
    return "Int16"
  case "Int32":
    return "Int32"
  case "Int64":
    return "Int64"
  case "Int":
    return "Int"
  case "UInt8":
    return "UInt8"
  case "UInt16":
    return "UInt16"
  case "UInt32":
    return "UInt32"
  case "UInt64":
    return "UInt64"
  case "UInt":
    return "UInt"
  case "Float16":
    return "Float16"
  case "BFloat16":
    return "BFloat16"
  case "Float":
    return "Float"
  case "Double":
    return "Double"
  case "String":
    return "String"
  case "Decimal":
    return "Decimal"
  default:
    return nil
  }
}

private func compatibleScalarPayloadType(_ typeText: String) -> String {
  var type = trimType(typeText)
  if type.hasSuffix("?") {
    type.removeLast()
  } else if type.hasPrefix("Optional<"), type.hasSuffix(">") {
    type = String(type.dropFirst("Optional<".count).dropLast())
  }
  for prefix in ["Swift.", "Foundation.", "Fory."] where type.hasPrefix(prefix) {
    return String(type.dropFirst(prefix.count))
  }
  return type
}

private func compatibleScalarTypeID(_ typeID: UInt32) -> Bool {
  switch typeID {
  case 1...15, 17...21, 40:
    return true
  default:
    return false
  }
}

private func swiftStringLiteral(_ value: String) -> String {
  let escaped =
    value
    .replacingOccurrences(of: "\\", with: "\\\\")
    .replacingOccurrences(of: "\"", with: "\\\"")
  return "\"\(escaped)\""
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
    let fieldCodec = field.isOptional ? "OptionalFieldCodec<\(codecType)>" : codecType
    if readTypeInfoExpr.contains("remoteField.fieldType") {
      return """
        try \(fieldCodec).readCompatibleField(
            context,
            remoteFieldType: remoteField.fieldType,
            refMode: \(refModeExpr)
        )
        """
    }
    return "try \(fieldCodec).read(context, refMode: \(refModeExpr), readTypeInfo: false)"
  }
  return
    "try \(field.typeText).foryRead(context, refMode: \(refModeExpr), readTypeInfo: \(readTypeInfoExpr))"
}

private func schemaReadFieldExpr(_ field: ParsedField) -> String {
  if fieldNeedsGeneralSchemaRead(field) {
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
  if fieldNeedsGeneralCompatibleRead(field) {
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
  let readTypeInfoExpr =
    dynamicAnyReadsTypeInfo(dynamicAnyCodec)
    ? ", readTypeInfo: true"
    : ""
  return
    "try castAnyDynamicValue(context.\(method)(refMode: \(refModeExpr)\(readTypeInfoExpr)), to: \(metatypeExpr))"
}

private func compatibleDefaultDecl(_ field: ParsedField) -> String {
  let explicitType =
    (field.dynamicAnyCodec != nil || field.customCodecType != nil) ? ": \(field.typeText)" : ""
  return "var __\(field.name)\(explicitType) = \(fieldDefaultExpr(field))"
}

private func fieldNeedsGeneralSchemaRead(_ field: ParsedField) -> Bool {
  field.dynamicAnyCodec != nil || field.customCodecType != nil || field.isOptional
    || field.typeID == 27
}

private func fieldNeedsGeneralCompatibleRead(_ field: ParsedField) -> Bool {
  fieldNeedsGeneralSchemaRead(field) || compatibleFieldNeedsTypeInfo(field)
}
