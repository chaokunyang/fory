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

import SwiftCompilerPlugin
import SwiftDiagnostics
import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

@main
struct ForySwiftPlugin: CompilerPlugin {
    let providingMacros: [Macro.Type] = [ForyObjectMacro.self, ForyFieldMacro.self]
}

public struct ForyObjectMacro: MemberMacro, ExtensionMacro {
    public static func expansion(
        of attribute: AttributeSyntax,
        providingMembersOf declaration: some DeclGroupSyntax,
        conformingTo _: [TypeSyntax],
        in _: some MacroExpansionContext
    ) throws -> [DeclSyntax] {
        let accessPrefix = serializerMemberAccessPrefix(declaration)
        let objectConfig = try parseForyObjectConfiguration(attribute)

        if let enumDecl = declaration.as(EnumDeclSyntax.self) {
            let parsedEnum = try parseEnumDecl(enumDecl)
            return buildEnumDecls(parsedEnum, accessPrefix: accessPrefix)
        }

        let parsed = try parseFields(declaration)
        let sortedFields = sortFields(parsed.fields)

        let staticTypeIDDecl: DeclSyntax = """
        \(raw: accessPrefix)static var staticTypeId: TypeId { .structType }
        """
        let evolvingDecl: DeclSyntax = """
        \(raw: accessPrefix)static var foryEvolving: Bool { \(raw: objectConfig.evolving ? "true" : "false") }
        """

        let referenceTrackDecl: DeclSyntax? = parsed.isClass ? """
        \(raw: accessPrefix)static var isRefType: Bool { true }
        """ : nil

        let codecAliasDecls: [DeclSyntax] = parsed.fields
            .filter { $0.dynamicAnyCodec == nil }
            .map { field in
                DeclSyntax(stringLiteral: "private typealias \(field.codecAliasName) = \(field.codecType)")
            }
        let schemaHashDecl: DeclSyntax = DeclSyntax(stringLiteral: buildSchemaHashDecl(fields: parsed.fields))
        let compatibleTypeMetaDecl: DeclSyntax = DeclSyntax(
            stringLiteral: buildCompatibleTypeMetaFieldsDecl(sortedFields: sortedFields, accessPrefix: accessPrefix)
        )
        let defaultDecl: DeclSyntax = DeclSyntax(
            stringLiteral: buildDefaultDecl(isClass: parsed.isClass, fields: parsed.fields, accessPrefix: accessPrefix)
        )
        let writeWrapperDecl: DeclSyntax = DeclSyntax(stringLiteral: buildWriteWrapperDecl(accessPrefix: accessPrefix))
        let readWrapperDecl: DeclSyntax? = parsed.isClass
            ? DeclSyntax(stringLiteral: buildClassReadWrapperDecl(accessPrefix: accessPrefix))
            : nil
        let writeDecl: DeclSyntax = DeclSyntax(
            stringLiteral: buildWriteDataDecl(sortedFields: sortedFields, accessPrefix: accessPrefix)
        )
        let readDecl: DeclSyntax = DeclSyntax(
            stringLiteral: buildReadDataDecl(
                isClass: parsed.isClass,
                fields: parsed.fields,
                sortedFields: sortedFields,
                accessPrefix: accessPrefix
            )
        )
        let readCompatibleDecl: DeclSyntax = DeclSyntax(
            stringLiteral: buildReadCompatibleDataDecl(
                isClass: parsed.isClass,
                fields: parsed.fields,
                sortedFields: sortedFields,
                accessPrefix: accessPrefix
            )
        )
        return codecAliasDecls + [
            staticTypeIDDecl,
            evolvingDecl,
            referenceTrackDecl,
            schemaHashDecl,
            compatibleTypeMetaDecl,
            defaultDecl,
            writeWrapperDecl,
            readWrapperDecl,
            writeDecl,
            readDecl,
            readCompatibleDecl
        ].compactMap { $0 }
    }

    public static func expansion(
        of _: AttributeSyntax,
        attachedTo declaration: some DeclGroupSyntax,
        providingExtensionsOf type: some TypeSyntaxProtocol,
        conformingTo _: [TypeSyntax],
        in _: some MacroExpansionContext
    ) throws -> [ExtensionDeclSyntax] {
        _ = declaration
        let typeName = type.trimmedDescription
        guard !typeName.isEmpty else {
            return []
        }

        let extensionDecl: ExtensionDeclSyntax = try ExtensionDeclSyntax(
            declaration.is(EnumDeclSyntax.self)
                ? "extension \(raw: typeName): Serializer {}"
                : "extension \(raw: typeName): Serializer, StructSerializer {}"
        )
        return [extensionDecl]
    }
}

public struct ForyFieldMacro: PeerMacro {
    public static func expansion(
        of _: AttributeSyntax,
        providingPeersOf _: some DeclSyntaxProtocol,
        in _: some MacroExpansionContext
    ) throws -> [DeclSyntax] {
        []
    }
}

private func serializerMemberAccessPrefix(_ declaration: some DeclGroupSyntax) -> String {
    let isPublicType = declaration.modifiers.contains(where: { modifier in
        modifier.name.tokenKind == .keyword(.public) || modifier.name.tokenKind == .keyword(.open)
    })
    guard isPublicType else {
        return ""
    }
    return "public "
}

private enum FieldEncoding: String {
    case varint
    case fixed
    case tagged
}

enum DynamicAnyCodecKind {
    case anyValue
    case anyHashableValue
    case anyList
    case stringAnyMap
    case int32AnyMap
    case anyHashableAnyMap
}

struct ParsedField {
    let name: String
    let typeText: String
    let originalIndex: Int

    let isOptional: Bool
    let isCollection: Bool
    let fieldID: Int?
    let schemaIdentifier: String
    let fieldIdentifier: String
    let codecType: String
    let codecAliasName: String

    let group: Int
    let typeID: UInt32
    let isCompressedNumeric: Bool
    let primitiveSize: Int
    let dynamicAnyCodec: DynamicAnyCodecKind?
}

private struct ParsedDecl {
    let isClass: Bool
    let fields: [ParsedField]
}

private enum ParsedEnumKind {
    case ordinal
    case taggedUnion
}

private struct ParsedEnumPayloadField {
    let label: String?
    let typeText: String
    let isOptional: Bool
    let codecType: String
    let codecAliasName: String
}

private struct ParsedEnumCase {
    let name: String
    let payload: [ParsedEnumPayloadField]
    let caseID: Int?
    let wireValue: UInt32?
}

private struct ParsedEnumDecl {
    let kind: ParsedEnumKind
    let cases: [ParsedEnumCase]
}

private struct ParsedForyFieldConfiguration {
    let encoding: FieldEncoding?
    let id: Int?
    let typeSpec: ParsedCodecSpec?
    let deferredTypeExpr: ExprSyntax?
}

private struct ParsedForyObjectConfiguration {
    let evolving: Bool
}

private struct ParsedCodecLeaf {
    let valueTypeText: String
    let codecBaseType: String
    let classification: TypeClassification
}

private indirect enum ParsedCodecNode {
    case leaf(ParsedCodecLeaf)
    case list(ParsedCodecSpec)
    case set(ParsedCodecSpec)
    case map(ParsedCodecSpec, ParsedCodecSpec)
}

private struct ParsedCodecSpec {
    let nullable: Bool
    let node: ParsedCodecNode
}

private extension ParsedCodecSpec {
    var codecType: String {
        let base: String
        switch node {
        case .leaf(let leaf):
            base = leaf.codecBaseType
        case .list(let element):
            base = "ListCodec<\(element.codecType)>"
        case .set(let element):
            base = "SetCodec<\(element.codecType)>"
        case .map(let key, let value):
            base = "MapCodec<\(key.codecType), \(value.codecType)>"
        }
        if nullable {
            return "OptionalCodec<\(base)>"
        }
        return base
    }

    var classification: TypeClassification {
        switch node {
        case .leaf(let leaf):
            return leaf.classification
        case .list:
            return .init(
                typeID: MacroTypeId.list,
                isPrimitive: false,
                isBuiltIn: true,
                isCollection: true,
                isMap: false,
                isCompressedNumeric: false,
                primitiveSize: 0
            )
        case .set:
            return .init(
                typeID: MacroTypeId.set,
                isPrimitive: false,
                isBuiltIn: true,
                isCollection: true,
                isMap: false,
                isCompressedNumeric: false,
                primitiveSize: 0
            )
        case .map:
            return .init(
                typeID: MacroTypeId.map,
                isPrimitive: false,
                isBuiltIn: true,
                isCollection: false,
                isMap: true,
                isCompressedNumeric: false,
                primitiveSize: 0
            )
        }
    }
}

private func codecAliasName(forField name: String) -> String {
    "__ForyCodec_\(name)"
}

private func codecAliasName(caseIndex: Int, payloadIndex: Int) -> String {
    "__ForyCodec_case\(caseIndex)_payload\(payloadIndex)"
}

private func fieldAttributePayload(from typeSyntax: TypeSyntax) -> (AttributeListSyntax, String) {
    if let attributed = typeSyntax.as(AttributedTypeSyntax.self) {
        return (attributed.attributes, attributed.baseType.trimmedDescription)
    }
    return (AttributeListSyntax([]), typeSyntax.trimmedDescription)
}

private func parsedCodecLeaf(
    valueTypeText: String,
    codecBaseType: String,
    typeID: UInt32,
    isPrimitive: Bool,
    isBuiltIn: Bool,
    isCollection: Bool = false,
    isMap: Bool = false,
    isCompressedNumeric: Bool,
    primitiveSize: Int
) -> ParsedCodecLeaf {
    .init(
        valueTypeText: valueTypeText,
        codecBaseType: codecBaseType,
        classification: .init(
            typeID: typeID,
            isPrimitive: isPrimitive,
            isBuiltIn: isBuiltIn,
            isCollection: isCollection,
            isMap: isMap,
            isCompressedNumeric: isCompressedNumeric,
            primitiveSize: primitiveSize
        )
    )
}

private func defaultCodecSpec(for typeText: String) throws -> ParsedCodecSpec {
    let optional = unwrapOptional(typeText)
    let concreteType = optional.type

    if let elementType = parseArrayElement(concreteType) {
        return .init(nullable: optional.isOptional, node: .list(try defaultCodecSpec(for: elementType)))
    }
    if let elementType = parseSetElement(concreteType) {
        return .init(nullable: optional.isOptional, node: .set(try defaultCodecSpec(for: elementType)))
    }
    if let (keyType, valueType) = parseDictionary(concreteType) {
        return .init(
            nullable: optional.isOptional,
            node: .map(try defaultCodecSpec(for: keyType), try defaultCodecSpec(for: valueType))
        )
    }

    let normalized = trimKnownModulePrefix(trimType(concreteType))
    let leaf: ParsedCodecLeaf
    switch normalized {
    case "Bool":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "BoolCodec", typeID: 1, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)
    case "Int8":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Int8Codec", typeID: 2, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)
    case "Int16":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Int16Codec", typeID: 3, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)
    case "Int32":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Int32VarintCodec", typeID: 5, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 4)
    case "Int64":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Int64VarintCodec", typeID: 7, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
    case "Int":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "IntVarintCodec", typeID: 7, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
    case "UInt8":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "UInt8Codec", typeID: 9, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)
    case "UInt16":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "UInt16Codec", typeID: 10, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)
    case "UInt32":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "UInt32VarintCodec", typeID: 12, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 4)
    case "UInt64":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "UInt64VarintCodec", typeID: 14, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
    case "UInt":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "UIntVarintCodec", typeID: 14, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
    case "Float16":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Float16Codec", typeID: 17, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)
    case "BFloat16":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "BFloat16Codec", typeID: 18, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)
    case "Float":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Float32Codec", typeID: 19, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 4)
    case "Double":
        leaf = parsedCodecLeaf(valueTypeText: concreteType, codecBaseType: "Float64Codec", typeID: 20, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)
    default:
        let classification = classifyType(concreteType)
        leaf = .init(
            valueTypeText: concreteType,
            codecBaseType: "SerializerCodec<\(concreteType)>",
            classification: classification
        )
    }

    return .init(nullable: optional.isOptional, node: .leaf(leaf))
}

private func explicitCodecSpec(
    expr: ExprSyntax,
    expectedTypeText: String
) throws -> ParsedCodecSpec {
    let parsed = try parseFieldTypeSpecExpression(expr)
    let expected = try defaultCodecSpec(for: expectedTypeText)
    guard codecSpecStructurallyMatches(parsed, expected) else {
        throw MacroExpansionErrorMessage("`@ForyField(type:)` does not match declared property shape \(expectedTypeText)")
    }
    return parsed
}

private func codecSpecStructurallyMatches(_ lhs: ParsedCodecSpec, _ rhs: ParsedCodecSpec) -> Bool {
    if lhs.nullable != rhs.nullable {
        return false
    }
    switch (lhs.node, rhs.node) {
    case let (.leaf(left), .leaf(right)):
        return trimKnownModulePrefix(trimType(left.valueTypeText)) == trimKnownModulePrefix(trimType(right.valueTypeText))
    case let (.list(left), .list(right)), let (.set(left), .set(right)):
        return codecSpecStructurallyMatches(left, right)
    case let (.map(leftKey, leftValue), .map(rightKey, rightValue)):
        return codecSpecStructurallyMatches(leftKey, rightKey) && codecSpecStructurallyMatches(leftValue, rightValue)
    default:
        return false
    }
}

private func parseFieldTypeSpecExpression(_ expr: ExprSyntax) throws -> ParsedCodecSpec {
    if let memberAccess = expr.as(MemberAccessExprSyntax.self) {
        let name = memberAccess.declName.baseName.text
        switch name {
        case "bool":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Bool", codecBaseType: "BoolCodec", typeID: 1, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)))
        case "int8":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Int8", codecBaseType: "Int8Codec", typeID: 2, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)))
        case "int16":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Int16", codecBaseType: "Int16Codec", typeID: 3, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)))
        case "uint8":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "UInt8", codecBaseType: "UInt8Codec", typeID: 9, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 1)))
        case "uint16":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "UInt16", codecBaseType: "UInt16Codec", typeID: 10, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)))
        case "float16":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Float16", codecBaseType: "Float16Codec", typeID: 17, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)))
        case "bfloat16":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "BFloat16", codecBaseType: "BFloat16Codec", typeID: 18, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 2)))
        case "float":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Float", codecBaseType: "Float32Codec", typeID: 19, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 4)))
        case "double":
            return .init(nullable: false, node: .leaf(parsedCodecLeaf(valueTypeText: "Double", codecBaseType: "Float64Codec", typeID: 20, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)))
        case "string":
            return try defaultCodecSpec(for: "String")
        case "data":
            return try defaultCodecSpec(for: "Data")
        case "duration":
            return try defaultCodecSpec(for: "Duration")
        case "date":
            return try defaultCodecSpec(for: "Date")
        case "localDate":
            return try defaultCodecSpec(for: "LocalDate")
        case "decimal":
            return try defaultCodecSpec(for: "Decimal")
        default:
            break
        }
    }

    guard let call = expr.as(FunctionCallExprSyntax.self) else {
        throw MacroExpansionErrorMessage("invalid `@ForyField(type:)` expression")
    }
    let callee = trimType(call.calledExpression.trimmedDescription).split(separator: ".").last.map(String.init) ?? ""
    let args = call.arguments

    switch callee {
    case "int32":
        return try integerFieldTypeSpec("Int32", args: args)
    case "int64":
        return try integerFieldTypeSpec("Int64", args: args)
    case "int":
        return try integerFieldTypeSpec("Int", args: args)
    case "uint32":
        return try integerFieldTypeSpec("UInt32", args: args)
    case "uint64":
        return try integerFieldTypeSpec("UInt64", args: args)
    case "uint":
        return try integerFieldTypeSpec("UInt", args: args)
    case "list":
        return try containerFieldTypeSpec(kind: "list", args: args)
    case "set":
        return try containerFieldTypeSpec(kind: "set", args: args)
    case "map":
        return try containerFieldTypeSpec(kind: "map", args: args)
    case "value":
        guard let first = args.first else {
            throw MacroExpansionErrorMessage("`@ForyField(type: .value(...))` requires a metatype argument")
        }
        let rawType = trimType(first.expression.trimmedDescription)
        guard rawType.hasSuffix(".self") else {
            throw MacroExpansionErrorMessage("`@ForyField(type: .value(...))` requires a `.self` metatype")
        }
        let typeText = String(rawType.dropLast(".self".count))
        let nullable = try nullableArg(args, defaultValue: false)
        let leaf = ParsedCodecLeaf(
            valueTypeText: typeText,
            codecBaseType: "SerializerCodec<\(typeText)>",
            classification: classifyType(typeText)
        )
        return .init(nullable: nullable, node: .leaf(leaf))
    default:
        throw MacroExpansionErrorMessage("unsupported `@ForyField(type:)` expression `\(callee)`")
    }
}

private func integerFieldTypeSpec(_ typeText: String, args: LabeledExprListSyntax) throws -> ParsedCodecSpec {
    let nullable = try nullableArg(args, defaultValue: false)
    let encoding = try encodingArg(args, defaultValue: .varint)
    return try integerFieldTypeSpec(typeText, nullable: nullable, encoding: encoding)
}

private func integerFieldTypeSpec(
    _ typeText: String,
    nullable: Bool,
    encoding: FieldEncoding
) throws -> ParsedCodecSpec {
    let leaf: ParsedCodecLeaf
    switch typeText {
    case "Int32":
        switch encoding {
        case .varint:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "Int32VarintCodec", typeID: 5, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 4)
        case .fixed:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "Int32FixedCodec", typeID: 4, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 4)
        case .tagged:
            throw MacroExpansionErrorMessage("`.tagged` is not supported for Int32")
        }
    case "UInt32":
        switch encoding {
        case .varint:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UInt32VarintCodec", typeID: 12, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 4)
        case .fixed:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UInt32FixedCodec", typeID: 11, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 4)
        case .tagged:
            throw MacroExpansionErrorMessage("`.tagged` is not supported for UInt32")
        }
    case "Int64":
        switch encoding {
        case .varint:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "Int64VarintCodec", typeID: 7, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        case .fixed:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "Int64FixedCodec", typeID: 6, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)
        case .tagged:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "Int64TaggedCodec", typeID: 8, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        }
    case "UInt64":
        switch encoding {
        case .varint:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UInt64VarintCodec", typeID: 14, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        case .fixed:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UInt64FixedCodec", typeID: 13, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)
        case .tagged:
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UInt64TaggedCodec", typeID: 15, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        }
    case "Int", "UInt":
        switch (typeText, encoding) {
        case ("Int", .varint):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "IntVarintCodec", typeID: 7, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        case ("Int", .fixed):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "IntFixedCodec", typeID: 6, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)
        case ("Int", .tagged):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "IntTaggedCodec", typeID: 8, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        case ("UInt", .varint):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UIntVarintCodec", typeID: 14, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        case ("UInt", .fixed):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UIntFixedCodec", typeID: 13, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: false, primitiveSize: 8)
        case ("UInt", .tagged):
            leaf = parsedCodecLeaf(valueTypeText: typeText, codecBaseType: "UIntTaggedCodec", typeID: 15, isPrimitive: true, isBuiltIn: true, isCompressedNumeric: true, primitiveSize: 8)
        default:
            throw MacroExpansionErrorMessage("unsupported integer field type `\(typeText)`")
        }
    default:
        throw MacroExpansionErrorMessage("unsupported integer field type `\(typeText)`")
    }
    return .init(nullable: nullable, node: .leaf(leaf))
}

private func containerFieldTypeSpec(kind: String, args: LabeledExprListSyntax) throws -> ParsedCodecSpec {
    let nullable = try nullableArg(args, defaultValue: false)
    switch kind {
    case "list", "set":
        let elementExpr = args.first(where: { arg in
            let label = arg.label?.text
            return label == nil || label == "element"
        })?.expression
        guard let elementExpr else {
            throw MacroExpansionErrorMessage("`.\(kind)` requires an element type")
        }
        let element = try parseFieldTypeSpecExpression(elementExpr)
        return .init(nullable: nullable, node: kind == "list" ? .list(element) : .set(element))
    case "map":
        guard let keyExpr = args.first(where: { $0.label?.text == "key" })?.expression,
              let valueExpr = args.first(where: { $0.label?.text == "value" })?.expression else {
            throw MacroExpansionErrorMessage("`.map` requires `key:` and `value:` arguments")
        }
        return .init(nullable: nullable, node: .map(try parseFieldTypeSpecExpression(keyExpr), try parseFieldTypeSpecExpression(valueExpr)))
    default:
        throw MacroExpansionErrorMessage("unsupported container kind `\(kind)`")
    }
}

private func nullableArg(_ args: LabeledExprListSyntax, defaultValue: Bool) throws -> Bool {
    guard let expr = args.first(where: { $0.label?.text == "nullable" })?.expression else {
        return defaultValue
    }
    return try parseBoolLiteralExpression(expr, message: "`nullable` must be a boolean literal")
}

private func encodingArg(_ args: LabeledExprListSyntax, defaultValue: FieldEncoding) throws -> FieldEncoding {
    guard let expr = args.first(where: { $0.label?.text == "encoding" })?.expression else {
        return defaultValue
    }
    return try parseFieldEncodingExpression(expr)
}

private func resolveCodecSpec(
    declaredTypeText: String,
    fieldConfig: ParsedForyFieldConfiguration?
) throws -> ParsedCodecSpec {
    if let typeSpec = fieldConfig?.typeSpec {
        return typeSpec
    }
    if let deferredTypeExpr = fieldConfig?.deferredTypeExpr {
        return try explicitCodecSpec(expr: deferredTypeExpr, expectedTypeText: declaredTypeText)
    }
    if let encoding = fieldConfig?.encoding {
        return try encodedScalarCodecSpec(declaredTypeText: declaredTypeText, encoding: encoding)
    }
    return try defaultCodecSpec(for: declaredTypeText)
}

private func encodedScalarCodecSpec(declaredTypeText: String, encoding: FieldEncoding) throws -> ParsedCodecSpec {
    let optional = unwrapOptional(declaredTypeText)
    let concreteType = trimKnownModulePrefix(trimType(optional.type))
    switch concreteType {
    case "Int32":
        return try integerFieldTypeSpec("Int32", nullable: optional.isOptional, encoding: encoding)
    case "UInt32":
        return try integerFieldTypeSpec("UInt32", nullable: optional.isOptional, encoding: encoding)
    case "Int64":
        return try integerFieldTypeSpec("Int64", nullable: optional.isOptional, encoding: encoding)
    case "UInt64":
        return try integerFieldTypeSpec("UInt64", nullable: optional.isOptional, encoding: encoding)
    case "Int":
        return try integerFieldTypeSpec("Int", nullable: optional.isOptional, encoding: encoding)
    case "UInt":
        return try integerFieldTypeSpec("UInt", nullable: optional.isOptional, encoding: encoding)
    default:
        throw MacroExpansionErrorMessage(
            "@ForyField(encoding:) is only supported for Int32/UInt32/Int64/UInt64/Int/UInt fields"
        )
    }
}

private func parseEnumDecl(_ enumDecl: EnumDeclSyntax) throws -> ParsedEnumDecl {
    var cases: [ParsedEnumCase] = []
    let integerRawEnum = enumDeclUsesExplicitIntegerRawValues(enumDecl)

    for member in enumDecl.memberBlock.members {
        guard let caseDecl = member.decl.as(EnumCaseDeclSyntax.self) else {
            continue
        }

        let caseConfig = try parseForyFieldConfiguration(
            from: caseDecl.attributes,
            supportsEncoding: true,
            supportsType: true
        )
        if (caseConfig?.id != nil || caseConfig?.encoding != nil || caseConfig?.typeSpec != nil),
           caseDecl.elements.count != 1 {
            throw MacroExpansionErrorMessage(
                "@ForyField enum case declarations with id/type/encoding must contain exactly one case"
            )
        }

        for element in caseDecl.elements {
            let caseName = element.name.text
            if caseName.isEmpty {
                continue
            }

            let currentCaseIndex = cases.count
            var payloadFields: [ParsedEnumPayloadField] = []
            if let parameterClause = element.parameterClause {
                if (caseConfig?.encoding != nil || caseConfig?.typeSpec != nil), parameterClause.parameters.count != 1 {
                    throw MacroExpansionErrorMessage(
                        "@ForyField(type:/encoding:) on enum cases is only supported for single-payload cases"
                    )
                }
                for (payloadIndex, parameter) in parameterClause.parameters.enumerated() {
                    if parameter.defaultValue != nil {
                        throw MacroExpansionErrorMessage(
                            "@ForyObject enum associated values cannot have default values"
                        )
                    }

                    let (payloadAttributes, payloadType) = fieldAttributePayload(from: parameter.type)
                    let parameterConfig = try parseForyFieldConfiguration(
                        from: payloadAttributes,
                        supportsEncoding: true,
                        supportsType: true,
                        expectedTypeText: payloadType
                    )
                    if parameterConfig != nil,
                       (caseConfig?.encoding != nil || caseConfig?.typeSpec != nil) {
                        throw MacroExpansionErrorMessage(
                            "use either case-level or payload-level @ForyField(type:/encoding:), not both"
                        )
                    }
                    let effectiveConfig: ParsedForyFieldConfiguration?
                    if parameterConfig != nil {
                        effectiveConfig = parameterConfig
                    } else if payloadIndex == 0 && parameterClause.parameters.count == 1 {
                        effectiveConfig = caseConfig
                    } else {
                        effectiveConfig = nil
                    }
                    let payloadDynamicAnyCodec = try resolveDynamicAnyCodec(rawType: payloadType)
                    if payloadDynamicAnyCodec != nil,
                       (effectiveConfig?.typeSpec != nil || effectiveConfig?.encoding != nil) {
                        throw MacroExpansionErrorMessage("@ForyField(type:/encoding:) is not supported for dynamic Any union payloads")
                    }
                    let codecSpec = try resolveCodecSpec(
                        declaredTypeText: payloadType,
                        fieldConfig: effectiveConfig
                    )
                    let label: String?
                    if let firstName = parameter.firstName, firstName.text != "_" {
                        label = firstName.text
                    } else {
                        label = nil
                    }

                    payloadFields.append(
                        .init(
                            label: label,
                            typeText: payloadType,
                            isOptional: codecSpec.nullable,
                            codecType: codecSpec.codecType,
                            codecAliasName: codecAliasName(caseIndex: currentCaseIndex, payloadIndex: payloadIndex)
                        )
                    )
                }
            }
            cases.append(
                .init(
                    name: caseName,
                    payload: payloadFields,
                    caseID: caseConfig?.id,
                    wireValue: integerRawEnum ? parseEnumCaseWireValue(element) : nil
                )
            )
        }
    }

    guard !cases.isEmpty else {
        throw MacroExpansionErrorMessage("@ForyObject enum must define at least one case")
    }

    var seenCaseIDs: [Int: String] = [:]
    for enumCase in cases {
        guard let caseID = enumCase.caseID else {
            continue
        }
        if let existing = seenCaseIDs[caseID], existing != enumCase.name {
            throw MacroExpansionErrorMessage(
                "duplicate @ForyField(id:) value \(caseID) used by enum cases '\(existing)' and '\(enumCase.name)'"
            )
        }
        seenCaseIDs[caseID] = enumCase.name
    }

    let hasPayload = cases.contains { !$0.payload.isEmpty }
    if hasPayload {
        return .init(kind: .taggedUnion, cases: cases)
    }

    return .init(kind: .ordinal, cases: cases)
}

private func buildEnumDecls(_ parsedEnum: ParsedEnumDecl, accessPrefix: String) -> [DeclSyntax] {
    switch parsedEnum.kind {
    case .ordinal:
        return buildOrdinalEnumDecls(parsedEnum.cases, accessPrefix: accessPrefix)
    case .taggedUnion:
        return buildTaggedUnionEnumDecls(parsedEnum.cases, accessPrefix: accessPrefix)
    }
}

private func buildOrdinalEnumDecls(_ cases: [ParsedEnumCase], accessPrefix: String) -> [DeclSyntax] {
    let defaultCase = cases[0].name
    let useExplicitWireValues = cases.allSatisfy { $0.wireValue != nil }
    let writeSwitchCases = cases.enumerated().map { index, enumCase in
        let wireValue = enumCase.wireValue ?? UInt32(index)
        return """
        case .\(enumCase.name):
            context.buffer.writeVarUInt32(\(wireValue))
        """
    }.joined(separator: "\n        ")
    let readSwitchCases = cases.enumerated().map { index, enumCase in
        let wireValue = enumCase.wireValue ?? UInt32(index)
        return "case \(wireValue): return .\(enumCase.name)"
    }.joined(separator: "\n        ")
    let errorLabel = useExplicitWireValues ? "enum value" : "enum ordinal"

    let defaultDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        \(accessPrefix)static func foryDefault() -> Self {
            .\(defaultCase)
        }
        """
    )

    let staticTypeIDDecl: DeclSyntax = """
    \(raw: accessPrefix)static var staticTypeId: TypeId { .enumType }
    """
    let writeWrapperDecl: DeclSyntax = DeclSyntax(stringLiteral: buildWriteWrapperDecl(accessPrefix: accessPrefix))

    let writeDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)func foryWriteData(_ context: WriteContext, hasGenerics: Bool) throws {
            _ = hasGenerics
            switch self {
            \(writeSwitchCases)
            }
        }
        """
    )

    let readDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
            let ordinal = try context.buffer.readVarUInt32()
            switch ordinal {
            \(readSwitchCases)
            default:
                throw ForyError.invalidData("unknown \(errorLabel) \\(ordinal)")
            }
        }
        """
    )

    return [defaultDecl, staticTypeIDDecl, writeWrapperDecl, writeDecl, readDecl]
}

private func enumDeclUsesExplicitIntegerRawValues(_ enumDecl: EnumDeclSyntax) -> Bool {
    guard let inheritanceClause = enumDecl.inheritanceClause else {
        return false
    }
    let inheritedTypes = inheritanceClause.inheritedTypes.map { $0.type.trimmedDescription }
    return inheritedTypes.contains {
        [
            "Int",
            "Int8",
            "Int16",
            "Int32",
            "Int64",
            "UInt",
            "UInt8",
            "UInt16",
            "UInt32",
            "UInt64",
        ].contains($0)
    }
}

private func parseEnumCaseWireValue(_ element: EnumCaseElementSyntax) -> UInt32? {
    guard let rawValue = element.rawValue?.value.trimmedDescription,
          let parsed = UInt32(rawValue)
    else {
        return nil
    }
    return parsed
}

private func buildTaggedUnionEnumDecls(_ cases: [ParsedEnumCase], accessPrefix: String) -> [DeclSyntax] {
    let defaultExpr = enumCaseDefaultExpr(cases[0])
    let writeSwitchCases = cases.enumerated().map { index, enumCase in
        let caseID = enumCase.caseID ?? index
        var lines: [String] = []
        lines.append("case \(enumCasePattern(enumCase)):")
        lines.append("    context.buffer.writeVarUInt32(\(caseID))")
        for (payloadIndex, payloadField) in enumCase.payload.enumerated() {
            let variableName = "__value\(payloadIndex)"
            lines.append(
                "    try \(payloadField.codecAliasName).write(\(variableName), context, refMode: .tracking, writeTypeInfo: true)"
            )
        }
        return lines.joined(separator: "\n")
    }.joined(separator: "\n        ")

    let readSwitchCases = cases.enumerated().map { index, enumCase in
        let caseID = enumCase.caseID ?? index
        if enumCase.payload.isEmpty {
            return """
            case \(caseID):
                return .\(enumCase.name)
            """
        }

        var lines: [String] = ["case \(caseID):"]
        for (payloadIndex, payloadField) in enumCase.payload.enumerated() {
            lines.append(
                "    let __value\(payloadIndex) = try \(payloadField.codecAliasName).read(context, refMode: .tracking, readTypeInfo: true)"
            )
        }
        let ctorArgs = enumCase.payload.enumerated().map { payloadIndex, payloadField in
            if let label = payloadField.label {
                return "\(label): __value\(payloadIndex)"
            }
            return "__value\(payloadIndex)"
        }.joined(separator: ", ")
        lines.append("    return .\(enumCase.name)(\(ctorArgs))")
        return lines.joined(separator: "\n")
    }.joined(separator: "\n        ")

    let defaultDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        \(accessPrefix)static func foryDefault() -> Self {
            \(defaultExpr)
        }
        """
    )

    let staticTypeIDDecl: DeclSyntax = """
    \(raw: accessPrefix)static var staticTypeId: TypeId { .typedUnion }
    """
    let writeWrapperDecl: DeclSyntax = DeclSyntax(stringLiteral: buildWriteWrapperDecl(accessPrefix: accessPrefix))

    let writeDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)func foryWriteData(_ context: WriteContext, hasGenerics: Bool) throws {
            _ = hasGenerics
            switch self {
            \(writeSwitchCases)
            }
        }
        """
    )

    let readDecl: DeclSyntax = DeclSyntax(
        stringLiteral: """
        @inline(__always)
        \(accessPrefix)static func foryReadData(_ context: ReadContext) throws -> Self {
            let caseID = try context.buffer.readVarUInt32()
            switch caseID {
            \(readSwitchCases)
            default:
                throw ForyError.invalidData("unknown union tag \\(caseID)")
            }
        }
        """
    )

    let codecAliases = cases
        .flatMap(\.payload)
        .map { "private typealias \($0.codecAliasName) = \($0.codecType)" }
    let aliasDecls = codecAliases.map { DeclSyntax(stringLiteral: $0) }

    return aliasDecls + [defaultDecl, staticTypeIDDecl, writeWrapperDecl, writeDecl, readDecl]
}

private func enumCasePattern(_ enumCase: ParsedEnumCase) -> String {
    guard !enumCase.payload.isEmpty else {
        return ".\(enumCase.name)"
    }
    let bindings = enumCase.payload.indices.map { "let __value\($0)" }.joined(separator: ", ")
    return ".\(enumCase.name)(\(bindings))"
}

private func enumCaseDefaultExpr(_ enumCase: ParsedEnumCase) -> String {
    guard !enumCase.payload.isEmpty else {
        return ".\(enumCase.name)"
    }
    let args = enumCase.payload.map { payloadField in
        let defaultValue = "\(payloadField.codecAliasName).defaultValue()"
        if let label = payloadField.label {
            return "\(label): \(defaultValue)"
        }
        return defaultValue
    }.joined(separator: ", ")
    return ".\(enumCase.name)(\(args))"
}

private func parseFields(_ declaration: some DeclGroupSyntax) throws -> ParsedDecl {
    let isClass = declaration.is(ClassDeclSyntax.self)
    guard isClass || declaration.is(StructDeclSyntax.self) else {
        throw MacroExpansionErrorMessage("@ForyObject supports struct and class only")
    }

    var fields: [ParsedField] = []
    var originalIndex = 0

    for member in declaration.memberBlock.members {
        guard let varDecl = member.decl.as(VariableDeclSyntax.self) else {
            continue
        }

        if varDecl.modifiers.contains(where: { $0.name.tokenKind == .keyword(.static) || $0.name.tokenKind == .keyword(.class) }) {
            continue
        }

        let hasFieldConfig = varDecl.attributes.contains { element in
            guard let attr = element.as(AttributeSyntax.self) else {
                return false
            }
            let attrName = trimType(attr.attributeName.trimmedDescription)
            return attrName == "ForyField" || attrName.hasSuffix(".ForyField")
        }
        if hasFieldConfig, varDecl.bindings.count != 1 {
            throw MacroExpansionErrorMessage("@ForyField can only be used on a single stored property")
        }

        for binding in varDecl.bindings {
            guard let pattern = binding.pattern.as(IdentifierPatternSyntax.self) else {
                continue
            }
            guard binding.accessorBlock == nil else {
                continue
            }
            guard let typeAnnotation = binding.typeAnnotation else {
                throw MacroExpansionErrorMessage("@ForyObject requires explicit types for stored properties")
            }

            let name = pattern.identifier.text
            let rawType = typeAnnotation.type.trimmedDescription
            let fieldConfig = try parseForyFieldConfiguration(
                from: varDecl.attributes,
                supportsEncoding: true,
                supportsType: true,
                expectedTypeText: rawType
            )

            let codecSpec = try resolveCodecSpec(declaredTypeText: rawType, fieldConfig: fieldConfig)
            let dynamicAnyCodec = try resolveDynamicAnyCodec(rawType: rawType)
            if dynamicAnyCodec != nil,
               (fieldConfig?.typeSpec != nil || fieldConfig?.encoding != nil) {
                throw MacroExpansionErrorMessage("@ForyField(type:/encoding:) is not supported for dynamic Any fields")
            }
            let classification = codecSpec.classification
            let fieldID = fieldConfig?.id
            let baseIdentifier = toSnakeCase(name)
            let schemaIdentifier = fieldID.map(String.init) ?? baseIdentifier
            let fieldIdentifier = fieldID.map { "$tag\($0)" } ?? baseIdentifier
            let group: Int
            if classification.isPrimitive {
                group = codecSpec.nullable ? 2 : 1
            } else if classification.isMap {
                group = 5
            } else if classification.isCollection {
                group = 4
            } else if classification.isBuiltIn {
                group = 3
            } else {
                group = 6
            }

            fields.append(
                ParsedField(
                    name: name,
                    typeText: rawType,
                    originalIndex: originalIndex,
                    isOptional: codecSpec.nullable,
                    isCollection: classification.isCollection || classification.isMap,
                    fieldID: fieldID,
                    schemaIdentifier: schemaIdentifier,
                    fieldIdentifier: fieldIdentifier,
                    codecType: codecSpec.codecType,
                    codecAliasName: codecAliasName(forField: name),
                    group: group,
                    typeID: classification.typeID,
                    isCompressedNumeric: classification.isCompressedNumeric,
                    primitiveSize: classification.primitiveSize,
                    dynamicAnyCodec: dynamicAnyCodec
                )
            )
            originalIndex += 1
        }
    }

    var seenFieldIDs: [Int: String] = [:]
    for field in fields {
        guard let fieldID = field.fieldID else {
            continue
        }
        if let existing = seenFieldIDs[fieldID], existing != field.name {
            throw MacroExpansionErrorMessage(
                "duplicate @ForyField(id:) value \(fieldID) used by fields '\(existing)' and '\(field.name)'"
            )
        }
        seenFieldIDs[fieldID] = field.name
    }

    return ParsedDecl(isClass: isClass, fields: fields)
}

private func parseForyFieldConfiguration(
    from attributes: AttributeListSyntax,
    supportsEncoding: Bool,
    supportsType: Bool,
    expectedTypeText: String? = nil
) throws -> ParsedForyFieldConfiguration? {
    var parsedEncoding: FieldEncoding?
    var parsedID: Int?
    var parsedTypeSpec: ParsedCodecSpec?
    var parsedDeferredTypeExpr: ExprSyntax?
    for element in attributes {
        guard let attr = element.as(AttributeSyntax.self) else {
            continue
        }

        let attrName = trimType(attr.attributeName.trimmedDescription)
        if attrName != "ForyField" && !attrName.hasSuffix(".ForyField") {
            continue
        }

        guard let args = attr.arguments else {
            throw MacroExpansionErrorMessage("@ForyField requires at least one argument")
        }
        guard case .argumentList(let argList) = args else {
            throw MacroExpansionErrorMessage("@ForyField arguments are invalid")
        }
        guard !argList.isEmpty else {
            throw MacroExpansionErrorMessage("@ForyField requires at least one argument")
        }

        for arg in argList {
            let label = arg.label?.text
            if label == nil || label == "encoding" {
                guard supportsEncoding else {
                    throw MacroExpansionErrorMessage("@ForyField(encoding:) is not supported here")
                }
                let encoding = try parseFieldEncodingExpression(arg.expression)
                if let existing = parsedEncoding, existing != encoding {
                    throw MacroExpansionErrorMessage("conflicting @ForyField encoding values on the same declaration")
                }
                parsedEncoding = encoding
                continue
            }

            if label == "id" {
                let idValue = try parseFieldIDExpression(arg.expression)
                if let existing = parsedID, existing != idValue {
                    throw MacroExpansionErrorMessage("conflicting @ForyField id values on the same declaration")
                }
                parsedID = idValue
                continue
            }

            if label == "type" {
                guard supportsType else {
                    throw MacroExpansionErrorMessage("@ForyField(type:) is not supported here")
                }
                if let expectedTypeText {
                    let typeSpec = try explicitCodecSpec(expr: arg.expression, expectedTypeText: expectedTypeText)
                    if let existing = parsedTypeSpec, existing.codecType != typeSpec.codecType {
                        throw MacroExpansionErrorMessage("conflicting @ForyField type specifications on the same declaration")
                    }
                    parsedTypeSpec = typeSpec
                } else {
                    let deferredTypeExpr = ExprSyntax(arg.expression)
                    if let existing = parsedDeferredTypeExpr,
                       existing.trimmedDescription != deferredTypeExpr.trimmedDescription {
                        throw MacroExpansionErrorMessage("conflicting @ForyField type specifications on the same declaration")
                    }
                    parsedDeferredTypeExpr = deferredTypeExpr
                }
                continue
            }

            throw MacroExpansionErrorMessage(
                "@ForyField supports only 'id', 'encoding', and 'type' arguments"
            )
        }
    }

    if parsedEncoding != nil, (parsedTypeSpec != nil || parsedDeferredTypeExpr != nil) {
        throw MacroExpansionErrorMessage("@ForyField cannot use both `encoding` and `type` on the same declaration")
    }

    if parsedEncoding == nil, parsedID == nil, parsedTypeSpec == nil, parsedDeferredTypeExpr == nil {
        return nil
    }

    return ParsedForyFieldConfiguration(
        encoding: parsedEncoding,
        id: parsedID,
        typeSpec: parsedTypeSpec,
        deferredTypeExpr: parsedDeferredTypeExpr
    )
}

private func parseForyObjectConfiguration(_ attribute: AttributeSyntax) throws -> ParsedForyObjectConfiguration {
    guard let args = attribute.arguments else {
        return .init(evolving: true)
    }
    guard case .argumentList(let argList) = args else {
        throw MacroExpansionErrorMessage("@ForyObject arguments are invalid")
    }
    guard !argList.isEmpty else {
        return .init(evolving: true)
    }

    var evolving = true
    for arg in argList {
        let label = arg.label?.text
        if label == nil || label == "evolving" {
            evolving = try parseBoolLiteralExpression(
                arg.expression,
                message: "@ForyObject evolving must be a boolean literal"
            )
            continue
        }
        throw MacroExpansionErrorMessage("@ForyObject supports only the 'evolving' argument")
    }
    return .init(evolving: evolving)
}

private func parseBoolLiteralExpression(_ expr: ExprSyntax, message: String) throws -> Bool {
    let raw = trimType(expr.trimmedDescription)
    switch raw {
    case "true":
        return true
    case "false":
        return false
    default:
        throw MacroExpansionErrorMessage(message)
    }
}

private func parseFieldEncodingExpression(_ expr: ExprSyntax) throws -> FieldEncoding {
    let raw = trimType(expr.trimmedDescription)
    let candidate: String

    if raw.hasPrefix("\""), raw.hasSuffix("\""), raw.count >= 2 {
        candidate = String(raw.dropFirst().dropLast())
    } else if let dot = raw.lastIndex(of: ".") {
        candidate = String(raw[raw.index(after: dot)...])
    } else {
        candidate = raw
    }

    guard let encoding = FieldEncoding(rawValue: candidate.lowercased()) else {
        throw MacroExpansionErrorMessage(
            "@ForyField encoding must be one of: .varint, .fixed, .tagged"
        )
    }
    return encoding
}

private func parseFieldIDExpression(_ expr: ExprSyntax) throws -> Int {
    let raw = trimType(expr.trimmedDescription)
    guard let value = Int(raw) else {
        throw MacroExpansionErrorMessage("@ForyField id must be an integer literal")
    }
    if value < 0 {
        throw MacroExpansionErrorMessage("@ForyField id must be non-negative")
    }
    if value > Int(Int16.max) {
        throw MacroExpansionErrorMessage("@ForyField id must be <= \(Int16.max)")
    }
    return value
}

private func resolveDynamicAnyCodec(rawType: String) throws -> DynamicAnyCodecKind? {
    let optional = unwrapOptional(rawType)
    let concreteType = trimType(optional.type)

    if concreteType == "AnyHashable" {
        return .anyHashableValue
    }

    if isDynamicAnyConcreteType(concreteType) {
        return .anyValue
    }

    if let elementType = parseArrayElement(concreteType), containsDynamicAny(typeText: elementType) {
        return .anyList
    }

    if let elementType = parseSetElement(concreteType), containsDynamicAny(typeText: elementType) {
        throw MacroExpansionErrorMessage("Set<...> with Any elements is not supported by @ForyObject yet")
    }

    if let (keyType, valueType) = parseDictionary(concreteType),
       containsDynamicAny(typeText: keyType) || containsDynamicAny(typeText: valueType) {
        let normalizedKeyType = trimType(unwrapOptional(keyType).type)
        if normalizedKeyType == "String" {
            return .stringAnyMap
        }
        if normalizedKeyType == "Int32" {
            return .int32AnyMap
        }
        if normalizedKeyType == "AnyHashable" {
            return .anyHashableAnyMap
        }
        throw MacroExpansionErrorMessage(
            "Dictionary<\(keyType), ...> with Any values is only supported for String, Int32, or AnyHashable keys"
        )
    }

    return nil
}

private func containsDynamicAny(typeText: String) -> Bool {
    let optional = unwrapOptional(typeText)
    let concreteType = trimType(optional.type)

    if isDynamicAnyConcreteType(concreteType) {
        return true
    }

    if let elementType = parseArrayElement(concreteType) {
        return containsDynamicAny(typeText: elementType)
    }

    if let elementType = parseSetElement(concreteType) {
        return containsDynamicAny(typeText: elementType)
    }

    if let (keyType, valueType) = parseDictionary(concreteType) {
        return containsDynamicAny(typeText: keyType) || containsDynamicAny(typeText: valueType)
    }

    return false
}

private func compareFieldIdentifier(_ lhs: ParsedField, _ rhs: ParsedField) -> Bool? {
    if let lhsID = lhs.fieldID, let rhsID = rhs.fieldID, lhsID != rhsID {
        return lhsID < rhsID
    }
    if lhs.fieldIdentifier != rhs.fieldIdentifier {
        return lhs.fieldIdentifier < rhs.fieldIdentifier
    }
    return nil
}

private func sortFields(_ fields: [ParsedField]) -> [ParsedField] {
    fields.sorted { lhs, rhs in
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
            if let identifierOrder = compareFieldIdentifier(lhs, rhs) {
                return identifierOrder
            }
        case 3, 4, 5:
            if lhs.typeID != rhs.typeID {
                return lhs.typeID < rhs.typeID
            }
            if let identifierOrder = compareFieldIdentifier(lhs, rhs) {
                return identifierOrder
            }
        default:
            if let identifierOrder = compareFieldIdentifier(lhs, rhs) {
                return identifierOrder
            }
        }

        if lhs.name != rhs.name {
            return lhs.name < rhs.name
        }
        return lhs.originalIndex < rhs.originalIndex
    }
}

private func buildSchemaHashDecl(fields: [ParsedField]) -> String {
    let fingerprintTrackRefDisabled = buildSchemaFingerprint(fields: fields, trackRefExpression: "false")
    let fingerprintTrackRefEnabled = buildSchemaFingerprint(fields: fields, trackRefExpression: "true")
    return """
    private static let __forySchemaHashTrackRefDisabled: UInt32 = SchemaHash.structHash32(\(fingerprintTrackRefDisabled))
    private static let __forySchemaHashTrackRefEnabled: UInt32 = SchemaHash.structHash32(\(fingerprintTrackRefEnabled))

    private static func __forySchemaHash(_ trackRef: Bool) -> UInt32 {
        trackRef ? Self.__forySchemaHashTrackRefEnabled : Self.__forySchemaHashTrackRefDisabled
    }
    """
}

private func buildCompatibleTypeMetaFieldsDecl(sortedFields: [ParsedField], accessPrefix: String) -> String {
    let disabledExpr = compatibleTypeMetaFieldsExpr(sortedFields: sortedFields, trackRefExpression: "false")
    let enabledExpr = compatibleTypeMetaFieldsExpr(sortedFields: sortedFields, trackRefExpression: "true")
    return """
    private static let __foryFieldsInfoTrackRefDisabled: [TypeMeta.FieldInfo] = \(disabledExpr)
    private static let __foryFieldsInfoTrackRefEnabled: [TypeMeta.FieldInfo] = \(enabledExpr)

    \(accessPrefix)static func foryFieldsInfo(trackRef: Bool) -> [TypeMeta.FieldInfo] {
        trackRef ? Self.__foryFieldsInfoTrackRefEnabled : Self.__foryFieldsInfoTrackRefDisabled
    }
    """
}

private func compatibleTypeMetaFieldsExpr(
    sortedFields: [ParsedField],
    trackRefExpression: String
) -> String {
    let fieldInfos = sortedFields.map { field in
        let fieldTypeExpr: String
        if field.dynamicAnyCodec != nil {
            fieldTypeExpr = compatibleTypeMetaFieldExpression(field, trackRefExpression: trackRefExpression)
        } else {
            fieldTypeExpr = "\(field.codecAliasName).compatibleFieldType(trackRef: \(trackRefExpression))"
        }
        return "TypeMeta.FieldInfo(fieldID: \(compatibleFieldIDExpr(field)), fieldName: \"\(field.name)\", fieldType: \(fieldTypeExpr))"
    }
    guard !fieldInfos.isEmpty else {
        return "[]"
    }
    return "[\n            \(fieldInfos.joined(separator: ",\n            "))\n        ]"
}

private func compatibleFieldIDExpr(_ field: ParsedField) -> String {
    if let fieldID = field.fieldID {
        return "\(fieldID)"
    }
    return "nil"
}

private func buildSchemaFingerprint(fields: [ParsedField], trackRefExpression: String) -> String {
    let entries = fields
        .sorted { lhs, rhs in
            if lhs.schemaIdentifier != rhs.schemaIdentifier {
                return lhs.schemaIdentifier < rhs.schemaIdentifier
            }
            return lhs.originalIndex < rhs.originalIndex
        }
        .map { field -> String in
            if field.dynamicAnyCodec != nil {
                let fieldTypeExpr = singleLineExpression(
                    compatibleTypeMetaFieldExpression(field, trackRefExpression: trackRefExpression)
                )
                return "\"\(field.schemaIdentifier),\\(\(fieldTypeExpr).schemaFingerprintString());\""
            }
            let trackRefExpr = "\(field.codecAliasName).compatibleFieldType(trackRef: \(trackRefExpression)).schemaFingerprintString()"
            return "\"\(field.schemaIdentifier),\\(\(trackRefExpr));\""
        }
    if entries.isEmpty {
        return "\"\""
    }
    return entries.joined(separator: " + ")
}

private func buildDefaultDecl(isClass: Bool, fields: [ParsedField], accessPrefix: String) -> String {
    if isClass {
        return """
        \(accessPrefix)static func foryDefault() -> Self {
            Self.init()
        }
        """
    }

    if fields.isEmpty {
        return """
        \(accessPrefix)static func foryDefault() -> Self {
            Self()
        }
        """
    }

    let args = fields
        .sorted(by: { $0.originalIndex < $1.originalIndex })
        .map { field in
            "\(field.name): \(fieldDefaultExpr(field))"
        }
        .joined(separator: ",\n            ")

    return """
    \(accessPrefix)static func foryDefault() -> Self {
        Self(
            \(args)
        )
    }
    """
}

private func singleLineExpression(_ expression: String) -> String {
    expression.split(whereSeparator: \.isWhitespace).joined(separator: " ")
}

private func buildWriteWrapperDecl(accessPrefix: String) -> String {
    """
    \(accessPrefix)func foryWrite(
        _ context: WriteContext,
        refMode: RefMode,
        writeTypeInfo: Bool,
        hasGenerics: Bool
    ) throws {
        let __buffer = context.buffer
        if refMode != .none {
            if refMode == .tracking, Self.isRefType, let object = self as AnyObject? {
                if context.refWriter.tryWriteRef(buffer: __buffer, object: object) {
                    return
                }
            } else {
                __buffer.writeInt8(RefFlag.notNullValue.rawValue)
            }
        }

        if writeTypeInfo {
            try Self.foryWriteStaticTypeInfo(context)
        }

        try foryWriteData(context, hasGenerics: hasGenerics)
    }
    """
}

private func buildWriteDataDecl(sortedFields: [ParsedField], accessPrefix: String) -> String {
    let allFieldLines = sortedFields.map { field in
        writeLine(for: field)
    }
    let schemaHeaderLines = [
        "if context.checkClassVersion {",
        "    __buffer.writeInt32(Int32(bitPattern: Self.__forySchemaHash(context.trackRef)))",
        "}"
    ]
    let schemaHeader = schemaHeaderLines.joined(separator: "\n            ")

    let fieldBody: String
    if allFieldLines.isEmpty {
        fieldBody = "_ = hasGenerics"
    } else {
        fieldBody = allFieldLines.joined(separator: "\n        ")
    }

    return """
    @inline(__always)
    \(accessPrefix)func foryWriteData(_ context: WriteContext, hasGenerics: Bool) throws {
        let __buffer = context.buffer
        if !context.compatible {
            \(schemaHeader)
        }
        \(fieldBody)
    }
    """
}

private func writeLine(for field: ParsedField) -> String {
    if let dynamicAnyCodec = field.dynamicAnyCodec {
        let refMode = fieldRefModeExpression(field)
        return dynamicAnyWriteLine(
            field: field,
            dynamicAnyCodec: dynamicAnyCodec,
            refModeExpr: refMode
        )
    }
    let refMode = fieldRefModeExpression(field)
    let writeTypeInfoExpr = "context.compatible ? TypeId.needsTypeInfoForField(\(field.codecAliasName).staticTypeId) : false"
    return """
    try \(field.codecAliasName).write(
        self.\(field.name),
        context,
        refMode: \(refMode),
        writeTypeInfo: \(writeTypeInfoExpr)
    )
    """
}

private enum MacroTypeId {
    static let unknown: UInt32 = 0
    static let list: UInt32 = 22
    static let set: UInt32 = 23
    static let map: UInt32 = 24
    static let compatibleStruct: UInt32 = 27
    static let namedStruct: UInt32 = 28
    static let namedCompatibleStruct: UInt32 = 29
    static let enumType: UInt32 = 30
    static let namedEnum: UInt32 = 31
    static let ext: UInt32 = 32
}

func compatibleFieldNeedsTypeInfo(_ field: ParsedField) -> Bool {
    switch field.typeID {
    case MacroTypeId.unknown,
         MacroTypeId.compatibleStruct,
         MacroTypeId.namedStruct,
         MacroTypeId.namedCompatibleStruct,
         MacroTypeId.enumType,
         MacroTypeId.namedEnum,
         MacroTypeId.ext:
        return true
    default:
        return false
    }
}

private func dynamicAnyWriteLine(
    field: ParsedField,
    dynamicAnyCodec: DynamicAnyCodecKind,
    refModeExpr: String
) -> String {
    if dynamicAnyCodec == .anyValue || dynamicAnyCodec == .anyHashableValue {
        return "try context.writeAny(self.\(field.name), refMode: \(refModeExpr), writeTypeInfo: true, hasGenerics: false)"
    }
    let method = dynamicAnyWriteMethodName(dynamicAnyCodec)
    let castType = dynamicAnyCastType(dynamicAnyCodec)
    let optionalSuffix = field.isOptional ? "?" : ""
    return "try context.\(method)(self.\(field.name) as \(castType)\(optionalSuffix), refMode: \(refModeExpr), hasGenerics: true)"
}

func fieldRefModeExpression(_ field: ParsedField) -> String {
    let nullable = field.isOptional ? "true" : "false"
    if let dynamicAnyCodec = field.dynamicAnyCodec {
        let trackRefExpr = dynamicAnyUsesContextTrackRef(dynamicAnyCodec) ? "context.trackRef" : "false"
        return "RefMode.from(nullable: \(nullable), trackRef: \(trackRefExpr))"
    }
    return "RefMode.from(nullable: \(nullable), trackRef: context.trackRef && \(field.codecAliasName).isRefType)"
}

private func compatibleTypeMetaFieldExpression(
    _ field: ParsedField,
    trackRefExpression: String
) -> String {
    let fieldTrackRefExpression: String
    if let dynamicAnyCodec = field.dynamicAnyCodec {
        fieldTrackRefExpression = dynamicAnyUsesContextTrackRef(dynamicAnyCodec) ? trackRefExpression : "false"
    } else {
        fieldTrackRefExpression = "\(trackRefExpression) && \(field.codecAliasName).isRefType"
    }

    return buildCompatibleFieldTypeExpression(
        typeText: field.typeText,
        nullableExpression: field.isOptional ? "true" : "false",
        trackRefExpression: fieldTrackRefExpression,
        explicitTypeID: nil
    )
}

func dynamicAnyWriteMethodName(_ codec: DynamicAnyCodecKind) -> String {
    switch codec {
    case .anyValue, .anyHashableValue:
        return "writeAny"
    case .anyList:
        return "writeListOfAny"
    case .stringAnyMap:
        return "writeMapStringToAny"
    case .int32AnyMap:
        return "writeMapInt32ToAny"
    case .anyHashableAnyMap:
        return "writeMapAnyHashableToAny"
    }
}

func dynamicAnyReadMethodName(_ codec: DynamicAnyCodecKind) -> String {
    switch codec {
    case .anyValue, .anyHashableValue:
        return "readAny"
    case .anyList:
        return "readListOfAny"
    case .stringAnyMap:
        return "readMapStringToAny"
    case .int32AnyMap:
        return "readMapInt32ToAny"
    case .anyHashableAnyMap:
        return "readMapAnyHashableToAny"
    }
}

func dynamicAnyCastType(_ codec: DynamicAnyCodecKind) -> String {
    switch codec {
    case .anyList:
        return "[Any]"
    case .stringAnyMap:
        return "[String: Any]"
    case .int32AnyMap:
        return "[Int32: Any]"
    case .anyHashableAnyMap:
        return "[AnyHashable: Any]"
    case .anyValue, .anyHashableValue:
        return "Any"
    }
}

func dynamicAnyUsesContextTrackRef(_ codec: DynamicAnyCodecKind) -> Bool {
    codec == .anyValue
}

func dynamicAnyReadsTypeInfo(_ codec: DynamicAnyCodecKind) -> Bool {
    codec == .anyValue || codec == .anyHashableValue
}

func fieldDefaultExpr(_ field: ParsedField) -> String {
    if field.dynamicAnyCodec != nil {
        return dynamicAnyDefaultExpr(typeText: field.typeText)
    }
    return "\(field.codecAliasName).defaultValue()"
}

private func buildCompatibleFieldTypeExpression(
    typeText: String,
    nullableExpression: String,
    trackRefExpression: String,
    explicitTypeID: UInt32? = nil
) -> String {
    let normalized = trimType(typeText)
    let optional = unwrapOptional(normalized)
    let concreteType = optional.type
    let outerClassification = classifyType(concreteType)

    if outerClassification.typeID == 22, let elementType = parseArrayElement(concreteType) {
        let elementNullable = compatibleGenericNullableExpression(elementType)
        let elementExpr = buildCompatibleFieldTypeExpression(
            typeText: elementType,
            nullableExpression: elementNullable,
            trackRefExpression: "false"
        )
        return """
TypeMeta.FieldType(
    typeID: TypeId.list.rawValue,
    nullable: \(nullableExpression),
    trackRef: \(trackRefExpression),
    generics: [\(elementExpr)]
)
"""
    }

    if outerClassification.typeID == 23, let elementType = parseSetElement(concreteType) {
        let elementNullable = compatibleGenericNullableExpression(elementType)
        let elementExpr = buildCompatibleFieldTypeExpression(
            typeText: elementType,
            nullableExpression: elementNullable,
            trackRefExpression: "false"
        )
        return """
TypeMeta.FieldType(
    typeID: TypeId.set.rawValue,
    nullable: \(nullableExpression),
    trackRef: \(trackRefExpression),
    generics: [\(elementExpr)]
)
"""
    }

    if outerClassification.typeID == 24, let (keyType, valueType) = parseDictionary(concreteType) {
        let keyNullable = compatibleGenericNullableExpression(keyType)
        let valueNullable = compatibleGenericNullableExpression(valueType)
        let keyExpr = buildCompatibleFieldTypeExpression(
            typeText: keyType,
            nullableExpression: keyNullable,
            trackRefExpression: "false"
        )
        let valueExpr = buildCompatibleFieldTypeExpression(
            typeText: valueType,
            nullableExpression: valueNullable,
            trackRefExpression: "false"
        )
        return """
TypeMeta.FieldType(
    typeID: TypeId.map.rawValue,
    nullable: \(nullableExpression),
    trackRef: \(trackRefExpression),
    generics: [\(keyExpr), \(valueExpr)]
)
"""
    }

    let typeIDExpr: String
    if let explicitTypeID {
        typeIDExpr = "\(explicitTypeID)"
    } else if isDynamicAnyConcreteType(concreteType) {
        typeIDExpr = "UInt32(TypeId.unknown.rawValue)"
    } else {
        typeIDExpr = compatibleFieldTypeIDExpression(concreteType)
    }

    return """
TypeMeta.FieldType(
    typeID: \(typeIDExpr),
    nullable: \(nullableExpression),
    trackRef: \(trackRefExpression)
)
"""
}

private func compatibleFieldTypeIDExpression(_ typeText: String) -> String {
    let staticTypeIDExpr = "\(typeText).staticTypeId"
    return "UInt32((\(staticTypeIDExpr) == .structType ? TypeId.compatibleStruct : \(staticTypeIDExpr)).rawValue)"
}

private func compatibleGenericNullableExpression(_ typeText: String) -> String {
    let optional = unwrapOptional(typeText)
    if optional.isOptional {
        return "true"
    }
    return classifyType(optional.type).isPrimitive ? "false" : "true"
}

private func unwrapOptional(_ typeText: String) -> (isOptional: Bool, type: String) {
    let trimmed = trimType(typeText)
    if trimmed.hasSuffix("?") {
        return (true, String(trimmed.dropLast()))
    }
    if let inner = extractGenericTypeContent(trimmed, baseNames: ["Optional", "Swift.Optional"]) {
        return (true, inner)
    }
    return (false, trimmed)
}

func trimType(_ type: String) -> String {
    type.replacingOccurrences(of: " ", with: "")
}

private struct TypeClassification {
    let typeID: UInt32
    let isPrimitive: Bool
    let isBuiltIn: Bool
    let isCollection: Bool
    let isMap: Bool
    let isCompressedNumeric: Bool
    let primitiveSize: Int
}

private func classifyType(
    _ typeText: String
) -> TypeClassification {
    let normalized = trimKnownModulePrefix(trimType(typeText))
    if isDynamicAnyConcreteType(normalized) {
        return .init(typeID: 0, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    }

    switch normalized {
    case "Bool":
        return .init(typeID: 1, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 1)
    case "Int8":
        return .init(typeID: 2, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 1)
    case "Int16":
        return .init(typeID: 3, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 2)
    case "Int32":
        return .init(typeID: 5, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: true, primitiveSize: 4)
    case "Int64", "Int":
        return .init(typeID: 7, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: true, primitiveSize: 8)
    case "UInt8":
        return .init(typeID: 9, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 1)
    case "UInt16":
        return .init(typeID: 10, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 2)
    case "UInt32":
        return .init(typeID: 12, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: true, primitiveSize: 4)
    case "UInt64", "UInt":
        return .init(typeID: 14, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: true, primitiveSize: 8)
    case "Float16":
        return .init(typeID: 17, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 2)
    case "BFloat16":
        return .init(typeID: 18, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 2)
    case "Float":
        return .init(typeID: 19, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 4)
    case "Double":
        return .init(typeID: 20, isPrimitive: true, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 8)
    case "String":
        return .init(typeID: 21, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    case "Data":
        return .init(typeID: 41, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    case "Duration":
        return .init(typeID: 37, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    case "Date":
        return .init(
            typeID: 38,
            isPrimitive: false,
            isBuiltIn: true,
            isCollection: false,
            isMap: false,
            isCompressedNumeric: false,
            primitiveSize: 0
        )
    case "LocalDate":
        return .init(typeID: 39, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    case "Decimal":
        return .init(typeID: 40, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    default:
        break
    }

    if let arrayElement = parseArrayElement(normalized) {
        let elem = classifyType(arrayElement)
        if elem.typeID == 9 { // UInt8
            return .init(typeID: 48, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
        }
        if elem.typeID == 1 {
            return .init(
                typeID: 43, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 2 {
            return .init(
                typeID: 44, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 3 {
            return .init(
                typeID: 45, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 5 {
            return .init(
                typeID: 46, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 7 {
            return .init(
                typeID: 47, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 10 {
            return .init(
                typeID: 49, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 12 {
            return .init(
                typeID: 50, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 14 {
            return .init(
                typeID: 51, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 17 {
            return .init(
                typeID: 53, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 18 {
            return .init(
                typeID: 54, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 19 {
            return .init(
                typeID: 55, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        if elem.typeID == 20 {
            return .init(
                typeID: 56, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: false,
                isCompressedNumeric: false, primitiveSize: 0
            )
        }
        return .init(typeID: 22, isPrimitive: false, isBuiltIn: true, isCollection: true, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    }

    if parseSetElement(normalized) != nil {
        return .init(typeID: 23, isPrimitive: false, isBuiltIn: true, isCollection: true, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
    }

    if parseDictionary(normalized) != nil {
        return .init(typeID: 24, isPrimitive: false, isBuiltIn: true, isCollection: false, isMap: true, isCompressedNumeric: false, primitiveSize: 0)
    }

    return .init(typeID: 27, isPrimitive: false, isBuiltIn: false, isCollection: false, isMap: false, isCompressedNumeric: false, primitiveSize: 0)
}

private func parseArrayElement(_ type: String) -> String? {
    let normalized = trimType(type)
    if normalized.hasPrefix("[") && normalized.hasSuffix("]") {
        let content = String(normalized.dropFirst().dropLast())
        if content.contains(":") {
            return nil
        }
        return content
    }
    return extractGenericTypeContent(normalized, baseNames: ["Array", "Swift.Array"])
}

func dynamicAnyDefaultExpr(typeText: String) -> String {
    let optional = unwrapOptional(typeText)
    if optional.isOptional {
        return "nil"
    }

    let concreteType = normalizeTypeForDynamicAny(optional.type)
    if concreteType == "AnyObject" {
        return "NSNull()"
    }
    if concreteType == "AnyHashable" {
        return "AnyHashable(Int32(0))"
    }
    if concreteType == "Any" || isAnySerializerExistentialType(concreteType) {
        return "ForyAnyNullValue()"
    }
    if parseArrayElement(concreteType) != nil {
        return "[]"
    }
    if parseDictionary(concreteType) != nil {
        return "[:]"
    }
    return "\(typeText)()"
}

private func isDynamicAnyConcreteType(_ typeText: String) -> Bool {
    let normalized = normalizeTypeForDynamicAny(typeText)
    if normalized == "Any" || normalized == "AnyObject" {
        return true
    }
    return isAnySerializerExistentialType(normalized)
}

private func isAnySerializerExistentialType(_ normalizedType: String) -> Bool {
    let normalized = normalizeTypeForDynamicAny(normalizedType)
    guard normalized.hasPrefix("any") else {
        return false
    }

    let protocolType = String(normalized.dropFirst(3))
    if protocolType == "Serializer" {
        return true
    }
    return protocolType.hasSuffix(".Serializer")
}

private func normalizeTypeForDynamicAny(_ typeText: String) -> String {
    var normalized = trimType(typeText)
    while normalized.hasPrefix("("), normalized.hasSuffix(")"), normalized.count > 1 {
        normalized = String(normalized.dropFirst().dropLast())
    }
    return normalized
}

private func parseSetElement(_ type: String) -> String? {
    extractGenericTypeContent(trimType(type), baseNames: ["Set", "Swift.Set"])
}

private func parseDictionary(_ type: String) -> (String, String)? {
    let normalized = trimType(type)
    if normalized.hasPrefix("[") && normalized.hasSuffix("]") {
        let content = String(normalized.dropFirst().dropLast())
        if let colon = findTopLevelSeparatorIndex(in: content, separator: ":") {
            let key = String(content[..<colon])
            let value = String(content[content.index(after: colon)...])
            return (trimType(key), trimType(value))
        }
    }

    if let content = extractGenericTypeContent(normalized, baseNames: ["Dictionary", "Swift.Dictionary"]) {
        if let comma = findTopLevelSeparatorIndex(in: content, separator: ",") {
            let key = String(content[..<comma])
            let value = String(content[content.index(after: comma)...])
            return (trimType(key), trimType(value))
        }
    }

    return nil
}

private func trimKnownModulePrefix(_ type: String) -> String {
    if type.hasPrefix("Swift.") {
        return String(type.dropFirst("Swift.".count))
    }
    if type.hasPrefix("Foundation.") {
        return String(type.dropFirst("Foundation.".count))
    }
    return type
}

private func extractGenericTypeContent(_ type: String, baseNames: [String]) -> String? {
    for baseName in baseNames {
        let prefix = "\(baseName)<"
        if type.hasPrefix(prefix), type.hasSuffix(">") {
            let start = type.index(type.startIndex, offsetBy: prefix.count)
            return String(type[start..<type.index(before: type.endIndex)])
        }
    }
    return nil
}

private func findTopLevelSeparatorIndex(in content: String, separator: Character) -> String.Index? {
    var angleDepth = 0
    var squareDepth = 0
    var roundDepth = 0

    for index in content.indices {
        let character = content[index]
        switch character {
        case "<":
            angleDepth += 1
        case ">":
            angleDepth = max(0, angleDepth - 1)
        case "[":
            squareDepth += 1
        case "]":
            squareDepth = max(0, squareDepth - 1)
        case "(":
            roundDepth += 1
        case ")":
            roundDepth = max(0, roundDepth - 1)
        default:
            break
        }

        if character == separator && angleDepth == 0 && squareDepth == 0 && roundDepth == 0 {
            return index
        }
    }
    return nil
}

private func toSnakeCase(_ name: String) -> String {
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
