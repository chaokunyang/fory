import 'dart:async';

import 'package:analyzer/dart/element/element.dart';
import 'package:analyzer/dart/element/nullability_suffix.dart';
import 'package:analyzer/dart/element/type.dart';
import 'package:build/build.dart';
import 'package:fory/fory.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:source_gen/source_gen.dart';

final class ForyGenerator extends Generator {
  static final TypeChecker _foryObjectChecker =
      TypeChecker.fromRuntime(ForyObject);
  static final TypeChecker _foryFieldChecker =
      TypeChecker.fromRuntime(ForyField);
  static final TypeChecker _int32Checker = TypeChecker.fromRuntime(Int32Type);
  static final TypeChecker _int64Checker = TypeChecker.fromRuntime(Int64Type);
  static final TypeChecker _uint8Checker = TypeChecker.fromRuntime(Uint8Type);
  static final TypeChecker _uint16Checker = TypeChecker.fromRuntime(Uint16Type);
  static final TypeChecker _uint32Checker = TypeChecker.fromRuntime(Uint32Type);
  static final TypeChecker _uint64Checker = TypeChecker.fromRuntime(Uint64Type);

  @override
  FutureOr<String> generate(LibraryReader library, BuildStep buildStep) {
    final annotatedClasses = library.classes
        .where((element) => _foryObjectChecker.hasAnnotationOf(element))
        .toList(growable: false);
    final enumElements = library.enums.toList(growable: false);
    if (annotatedClasses.isEmpty && enumElements.isEmpty) {
      return '';
    }

    final namespace = _buildNamespace(buildStep.inputId);
    final helperBaseName = _toPascalCase(
      buildStep.inputId.pathSegments.last.split('.').first,
    );
    final registrationHelperName = '_register${helperBaseName}ForyTypes';
    final registrationByTypeHelperName = '_register${helperBaseName}ForyType';

    final enumSpecs = enumElements
        .map((element) => _GeneratedEnumSpec(name: element.name))
        .toList(growable: false);
    final structSpecs =
        annotatedClasses.map(_analyzeStruct).toList(growable: false);

    final output = StringBuffer()
      ..writeln('// GENERATED CODE - DO NOT MODIFY BY HAND')
      ..writeln();

    for (final enumSpec in enumSpecs) {
      _writeEnum(output, enumSpec);
    }
    for (final structSpec in structSpecs) {
      _writeStruct(output, structSpec);
    }

    _writeRegistrationHelpers(
      output,
      enumSpecs: enumSpecs,
      structSpecs: structSpecs,
      namespace: namespace,
      registrationHelperName: registrationHelperName,
      registrationByTypeHelperName: registrationByTypeHelperName,
    );
    return output.toString();
  }

  _GeneratedStructSpec _analyzeStruct(ClassElement element) {
    final objectAnnotation = _foryObjectChecker.firstAnnotationOf(element);
    final objectReader = ConstantReader(objectAnnotation);
    final evolving = objectReader.peek('evolving')?.boolValue ?? true;

    final fields = element.fields
        .where(
          (field) =>
              !field.isStatic &&
              !field.isSynthetic &&
              !field.name.startsWith('_'),
        )
        .where((field) => !_isSkipped(field))
        .map(_analyzeField)
        .toList(growable: false);

    final sortedFields = _sortFields(fields);
    final constructorPlan = _buildConstructorPlan(element, sortedFields);
    return _GeneratedStructSpec(
      name: element.name,
      evolving: evolving,
      fields: sortedFields,
      constructorPlan: constructorPlan,
    );
  }

  _GeneratedFieldSpec _analyzeField(FieldElement field) {
    final annotation = _foryFieldChecker.firstAnnotationOf(field);
    final reader = annotation == null ? null : ConstantReader(annotation);
    final idValue = reader?.peek('id');
    final nullableValue = reader?.peek('nullable');
    final dynamicValue = reader?.peek('dynamic');
    final fieldId = idValue == null || idValue.isNull ? null : idValue.intValue;
    final nullable = nullableValue == null || nullableValue.isNull
        ? _isNullable(field.type)
        : nullableValue.boolValue;
    final dynamic = dynamicValue == null || dynamicValue.isNull
        ? _autoDynamic(field.type)
        : dynamicValue.boolValue;
    final ref = reader?.peek('ref')?.boolValue ?? false;
    return _GeneratedFieldSpec(
      name: field.name,
      type: field.type,
      displayType: field.type.getDisplayString(),
      identifier: fieldId != null && fieldId >= 0
          ? '$fieldId'
          : _toSnakeCase(field.name),
      id: fieldId,
      nullable: nullable,
      ref: ref,
      dynamic: dynamic,
      writable: field.setter != null,
      shape: _shapeForType(
        field.type,
        nullable: nullable,
        ref: ref,
        dynamic: dynamic,
        integerAnnotation: _analyzeIntegerAnnotation(field),
      ),
    );
  }

  _GeneratedShapeSpec _shapeForType(
    DartType type, {
    required bool nullable,
    required bool ref,
    required bool? dynamic,
    _IntegerAnnotationSpec? integerAnnotation,
  }) {
    if (_isList(type) || _isSet(type)) {
      final argument = (type as InterfaceType).typeArguments.single;
      return _GeneratedShapeSpec(
        typeLiteral: _typeLiteral(type),
        typeId: _typeIdFor(type),
        nullable: nullable,
        ref: ref,
        dynamic: dynamic,
        arguments: <_GeneratedShapeSpec>[
          _shapeForType(
            argument,
            nullable: true,
            ref: false,
            dynamic: _autoDynamic(argument),
          ),
        ],
      );
    }
    if (_isMap(type)) {
      final arguments = (type as InterfaceType).typeArguments;
      return _GeneratedShapeSpec(
        typeLiteral: _typeLiteral(type),
        typeId: _typeIdFor(type),
        nullable: nullable,
        ref: ref,
        dynamic: dynamic,
        arguments: <_GeneratedShapeSpec>[
          _shapeForType(
            arguments[0],
            nullable: true,
            ref: false,
            dynamic: _autoDynamic(arguments[0]),
          ),
          _shapeForType(
            arguments[1],
            nullable: true,
            ref: false,
            dynamic: _autoDynamic(arguments[1]),
          ),
        ],
      );
    }
    return _GeneratedShapeSpec(
      typeLiteral: _typeLiteral(type),
      typeId: _typeIdFor(type, integerAnnotation: integerAnnotation),
      nullable: nullable,
      ref: ref,
      dynamic: dynamic,
      arguments: const <_GeneratedShapeSpec>[],
    );
  }

  _ConstructorPlan _buildConstructorPlan(
    ClassElement element,
    List<_GeneratedFieldSpec> fields,
  ) {
    final unnamedConstructor = element.unnamedConstructor;
    final hasZeroArgConstructor = unnamedConstructor != null &&
        !unnamedConstructor.isFactory &&
        unnamedConstructor.parameters
            .every((parameter) => parameter.isOptional);
    if (hasZeroArgConstructor && fields.every((field) => field.writable)) {
      return const _ConstructorPlan.mutable();
    }

    if (unnamedConstructor == null || unnamedConstructor.isFactory) {
      throw InvalidGenerationSourceError(
        'Generated Fory serializers require either a writable zero-argument constructor '
        'or an unnamed generative constructor whose parameters map to fields.',
        element: element,
      );
    }

    final fieldByName = <String, _GeneratedFieldSpec>{
      for (final field in fields) field.name: field,
    };
    final arguments = <_ConstructorArgumentSpec>[];
    final constructorFieldNames = <String>{};
    for (final parameter in unnamedConstructor.parameters) {
      final field = fieldByName[parameter.name];
      if (field == null) {
        if (parameter.isRequiredNamed || parameter.isRequiredPositional) {
          throw InvalidGenerationSourceError(
            'Constructor parameter ${parameter.name} does not match a serializable field.',
            element: parameter,
          );
        }
        continue;
      }
      constructorFieldNames.add(field.name);
      arguments.add(
        _ConstructorArgumentSpec(
          fieldName: field.name,
          parameterName: parameter.name,
          named: parameter.isNamed,
        ),
      );
    }

    for (final field in fields) {
      if (!constructorFieldNames.contains(field.name) && !field.writable) {
        throw InvalidGenerationSourceError(
          'Field ${field.name} must be writable or provided by the unnamed constructor.',
          element: element,
        );
      }
    }

    final selfRefField = fields
        .where((field) => field.ref)
        .where((field) => _sameType(field.type, element.thisType))
        .firstOrNull;
    if (selfRefField != null) {
      throw InvalidGenerationSourceError(
        'Constructor-based generated serializers cannot bind self references early. '
        'Use a writable zero-argument constructor for ${element.name}.',
        element: selfRefField.type.element,
      );
    }

    final postConstructionFields = fields
        .where((field) => !constructorFieldNames.contains(field.name))
        .map((field) => field.name)
        .toList(growable: false);

    return _ConstructorPlan.constructor(
      arguments: arguments,
      postConstructionFieldNames: postConstructionFields,
    );
  }

  void _writeEnum(StringBuffer output, _GeneratedEnumSpec enumSpec) {
    final serializerClassName = '_${enumSpec.name}ForySerializer';
    final serializerVariableName =
        '_${_toCamelCase(enumSpec.name)}ForySerializer';
    output
      ..writeln(
        'final class $serializerClassName extends Serializer<${enumSpec.name}> {',
      )
      ..writeln('  const $serializerClassName();')
      ..writeln('  bool get isEnum => true;')
      ..writeln('  @override')
      ..writeln('  void write(WriteContext context, ${enumSpec.name} value) {')
      ..writeln('    context.writeVarUint32(value.index);')
      ..writeln('  }')
      ..writeln()
      ..writeln('  @override')
      ..writeln('  ${enumSpec.name} read(ReadContext context) {')
      ..writeln('    return ${enumSpec.name}.values[context.readVarUint32()];')
      ..writeln('  }')
      ..writeln('}')
      ..writeln(
        'const $serializerClassName $serializerVariableName = $serializerClassName();',
      )
      ..writeln();
  }

  void _writeStruct(StringBuffer output, _GeneratedStructSpec structSpec) {
    final serializerClassName = '_${structSpec.name}ForySerializer';
    final serializerVariableName =
        '_${_toCamelCase(structSpec.name)}ForySerializer';
    final metadataListName = '_${structSpec.name}ForyFields';

    output.writeln(
      'const List<Map<String, Object?>> $metadataListName = <Map<String, Object?>>[',
    );
    for (final field in structSpec.fields) {
      output.writeln(_fieldMetadataLiteral(field));
    }
    output
      ..writeln('];')
      ..writeln()
      ..writeln(
        'final class $serializerClassName extends Serializer<${structSpec.name}> {',
      )
      ..writeln('  const $serializerClassName();')
      ..writeln('  bool get isStruct => true;')
      ..writeln('  @override')
      ..writeln('  bool get evolving => ${structSpec.evolving};')
      ..writeln('  @override')
      ..writeln('  List<Map<String, Object?>> get fields => $metadataListName;')
      ..writeln('  @override')
      ..writeln(
        '  void write(WriteContext context, ${structSpec.name} value) {',
      )
      ..writeln(
        '    final compatibleFields = context.compatibleFieldOrder($metadataListName);',
      )
      ..writeln('    if (compatibleFields != null) {')
      ..writeln('      for (final field in compatibleFields) {')
      ..writeln("        switch (field['identifier'] as String) {");
    for (final field in structSpec.fields) {
      output
        ..writeln("          case '${field.identifier}':")
        ..writeln('            context.writeField(field, value.${field.name});')
        ..writeln('            break;');
    }
    output
      ..writeln('          default:')
      ..writeln('            break;')
      ..writeln('        }')
      ..writeln('      }')
      ..writeln('      return;')
      ..writeln('    }');
    for (var index = 0; index < structSpec.fields.length; index += 1) {
      final field = structSpec.fields[index];
      output.writeln(
        '    context.writeField($metadataListName[$index], value.${field.name});',
      );
    }
    output
      ..writeln('  }')
      ..writeln()
      ..writeln('  @override')
      ..writeln('  ${structSpec.name} read(ReadContext context) {');

    switch (structSpec.constructorPlan.mode) {
      case _ConstructorMode.mutable:
        output
          ..writeln('    final value = ${structSpec.name}();')
          ..writeln('    context.reference(value);');
        for (var index = 0; index < structSpec.fields.length; index += 1) {
          final field = structSpec.fields[index];
          final readerFunctionName = field.readerFunctionName(structSpec.name);
          output.writeln(
            '    value.${field.name} = $readerFunctionName(context.readField<Object?>($metadataListName[$index], value.${field.name}), value.${field.name});',
          );
        }
        output.writeln('    return value;');
      case _ConstructorMode.constructor:
        for (var index = 0; index < structSpec.fields.length; index += 1) {
          final field = structSpec.fields[index];
          final readerFunctionName = field.readerFunctionName(structSpec.name);
          output.writeln(
            '    final ${field.localName} = $readerFunctionName(context.readField<Object?>($metadataListName[$index]));',
          );
        }
        final constructorInvocation = _constructorInvocation(structSpec);
        output
          ..writeln('    final value = $constructorInvocation;')
          ..writeln('    context.reference(value);');
        for (final fieldName
            in structSpec.constructorPlan.postConstructionFieldNames) {
          final field =
              structSpec.fields.firstWhere((item) => item.name == fieldName);
          output.writeln('    value.${field.name} = ${field.localName};');
        }
        output.writeln('    return value;');
    }

    output
      ..writeln('  }')
      ..writeln('}')
      ..writeln(
        'const $serializerClassName $serializerVariableName = $serializerClassName();',
      )
      ..writeln();

    for (final field in structSpec.fields) {
      final readerFunctionName = field.readerFunctionName(structSpec.name);
      output
        ..writeln(
          '${field.displayType} $readerFunctionName(Object? value, [Object? fallback]) {',
        )
        ..writeln(
          '  return ${_conversionExpression(field, 'value', 'fallback')};',
        )
        ..writeln('}')
        ..writeln();
    }
  }

  void _writeRegistrationHelpers(
    StringBuffer output, {
    required List<_GeneratedEnumSpec> enumSpecs,
    required List<_GeneratedStructSpec> structSpecs,
    required String namespace,
    required String registrationHelperName,
    required String registrationByTypeHelperName,
  }) {
    output.writeln(
      'Serializer _serializerForGeneratedType(Type type) {',
    );
    for (final enumSpec in enumSpecs) {
      output.writeln(
        '  if (type == ${enumSpec.name}) return _${_toCamelCase(enumSpec.name)}ForySerializer;',
      );
    }
    for (final structSpec in structSpecs) {
      output.writeln(
        '  if (type == ${structSpec.name}) return _${_toCamelCase(structSpec.name)}ForySerializer;',
      );
    }
    output
      ..writeln(
        "  throw ArgumentError.value(type, 'type', 'No generated serializer for this library.');",
      )
      ..writeln('}')
      ..writeln()
      ..writeln(
        'void $registrationByTypeHelperName(Fory fory, Type type, {int? id, String? namespace, String? typeName}) {',
      )
      ..writeln(
        '  fory.register(type, _serializerForGeneratedType(type), id: id, namespace: namespace, typeName: typeName);',
      )
      ..writeln('}')
      ..writeln()
      ..writeln('void $registrationHelperName(Fory fory) {');

    for (final enumSpec in enumSpecs) {
      output.writeln(
        "  fory.register(${enumSpec.name}, _${_toCamelCase(enumSpec.name)}ForySerializer, namespace: '$namespace', typeName: '${enumSpec.name}');",
      );
    }
    for (final structSpec in structSpecs) {
      output.writeln(
        "  fory.register(${structSpec.name}, _${_toCamelCase(structSpec.name)}ForySerializer, namespace: '$namespace', typeName: '${structSpec.name}');",
      );
    }
    output
      ..writeln('}')
      ..writeln();
  }

  String _constructorInvocation(_GeneratedStructSpec structSpec) {
    final positionalArguments = <String>[];
    final namedArguments = <String>[];
    for (final argument in structSpec.constructorPlan.arguments) {
      final field = structSpec.fields.firstWhere(
        (item) => item.name == argument.fieldName,
      );
      if (argument.named) {
        namedArguments.add('${argument.parameterName}: ${field.localName}');
      } else {
        positionalArguments.add(field.localName);
      }
    }
    final arguments =
        <String>[...positionalArguments, ...namedArguments].join(', ');
    return '${structSpec.name}($arguments)';
  }

  bool _isSkipped(FieldElement field) {
    final annotation = _foryFieldChecker.firstAnnotationOf(field);
    if (annotation == null) {
      return false;
    }
    return ConstantReader(annotation).peek('skip')?.boolValue ?? false;
  }

  String _fieldMetadataLiteral(_GeneratedFieldSpec field) {
    final idLiteral = field.id == null ? 'null' : '${field.id}';
    return '''
  <String, Object?>{
    'name': '${field.name}',
    'identifier': '${field.identifier}',
    'id': $idLiteral,
    'shape': ${_shapeLiteral(field.shape)},
  },''';
  }

  String _shapeLiteral(_GeneratedShapeSpec shape) {
    final argumentsLiteral = shape.arguments.isEmpty
        ? 'const <Object?>[]'
        : '<Object?>[\n${shape.arguments.map(_shapeLiteral).join(',\n')}\n      ]';
    final dynamicLiteral = switch (shape.dynamic) {
      true => 'true',
      false => 'false',
      null => 'null',
    };
    return '''
<String, Object?>{
      'type': ${shape.typeLiteral},
      'typeId': ${shape.typeId},
      'nullable': ${shape.nullable},
      'ref': ${shape.ref},
      'dynamic': $dynamicLiteral,
      'arguments': $argumentsLiteral,
    }''';
  }

  String _conversionExpression(
    _GeneratedFieldSpec field,
    String valueExpression,
    String fallbackExpression,
  ) {
    return _conversionExpressionForType(
      field.type,
      field.shape,
      valueExpression,
      nullExpression: _nullExpression(
        field.type,
        errorTarget: 'field ${field.name}',
        fallbackExpression: fallbackExpression,
      ),
    );
  }

  String _conversionExpressionForType(
    DartType type,
    _GeneratedShapeSpec shape,
    String valueExpression, {
    required String nullExpression,
  }) {
    if (_isNullable(type)) {
      final nonNullableType = _withoutNullability(type);
      final nonNullableShape = _nonNullableShape(shape);
      final converted = _conversionExpressionForType(
        nonNullableType,
        nonNullableShape,
        valueExpression,
        nullExpression: _nullExpression(
          nonNullableType,
          errorTarget: 'value',
        ),
      );
      return '$valueExpression == null ? $nullExpression : $converted';
    }
    final converted = _conversionExpressionWithoutNullCheck(
      type,
      shape,
      valueExpression,
    );
    return '$valueExpression == null ? $nullExpression : $converted';
  }

  String _conversionExpressionWithoutNullCheck(
    DartType type,
    _GeneratedShapeSpec shape,
    String valueExpression,
  ) {
    if (_isList(type)) {
      final elementType = (type as InterfaceType).typeArguments.single;
      final elementShape = shape.arguments.single;
      final convertedElement = _conversionExpressionForType(
        elementType,
        elementShape,
        'item',
        nullExpression: _nullExpression(
          elementType,
          errorTarget: 'list item',
        ),
      );
      return 'List<${elementType.getDisplayString()}>.of((($valueExpression as List)).map((item) => $convertedElement))';
    }
    if (_isSet(type)) {
      final elementType = (type as InterfaceType).typeArguments.single;
      final elementShape = shape.arguments.single;
      final convertedElement = _conversionExpressionForType(
        elementType,
        elementShape,
        'item',
        nullExpression: _nullExpression(
          elementType,
          errorTarget: 'set item',
        ),
      );
      return 'Set<${elementType.getDisplayString()}>.of((($valueExpression as Set)).map((item) => $convertedElement))';
    }
    if (_isMap(type)) {
      final arguments = (type as InterfaceType).typeArguments;
      final keyType = arguments[0];
      final valueType = arguments[1];
      final keyShape = shape.arguments[0];
      final valueShape = shape.arguments[1];
      final convertedKey = _conversionExpressionForType(
        keyType,
        keyShape,
        'key',
        nullExpression: _nullExpression(
          keyType,
          errorTarget: 'map key',
        ),
      );
      final convertedValue = _conversionExpressionForType(
        valueType,
        valueShape,
        'value',
        nullExpression: _nullExpression(
          valueType,
          errorTarget: 'map value',
        ),
      );
      return 'Map<${keyType.getDisplayString()}, ${valueType.getDisplayString()}>.of((($valueExpression as Map)).map((key, value) => MapEntry($convertedKey, $convertedValue)))';
    }
    if (type.isDartCoreInt) {
      if (shape.typeId == TypeIds.int32 || shape.typeId == TypeIds.varInt32) {
        return '($valueExpression as Int32).value';
      }
      return '$valueExpression as int';
    }
    if (type.isDartCoreDouble) {
      return '$valueExpression as double';
    }
    if (type.isDartCoreBool) {
      return '$valueExpression as bool';
    }
    if (type.isDartCoreString) {
      return '$valueExpression as String';
    }
    return '$valueExpression as ${type.getDisplayString()}';
  }

  String _nullExpression(
    DartType type, {
    required String errorTarget,
    String? fallbackExpression,
  }) {
    final displayType = type.getDisplayString();
    if (_isNullable(type)) {
      return 'null as $displayType';
    }
    if (fallbackExpression != null) {
      return '($fallbackExpression != null ? $fallbackExpression as $displayType : (throw StateError(\'Received null for non-nullable $errorTarget.\')))';
    }
    return '(throw StateError(\'Received null for non-nullable $errorTarget.\'))';
  }

  _GeneratedShapeSpec _nonNullableShape(_GeneratedShapeSpec shape) {
    if (!shape.nullable) {
      return shape;
    }
    return _GeneratedShapeSpec(
      typeLiteral: shape.typeLiteral,
      typeId: shape.typeId,
      nullable: false,
      ref: shape.ref,
      dynamic: shape.dynamic,
      arguments: shape.arguments,
    );
  }

  List<_GeneratedFieldSpec> _sortFields(List<_GeneratedFieldSpec> fields) {
    final primitiveFields = <_GeneratedFieldSpec>[];
    final boxedPrimitiveFields = <_GeneratedFieldSpec>[];
    final builtInFields = <_GeneratedFieldSpec>[];
    final collectionFields = <_GeneratedFieldSpec>[];
    final mapFields = <_GeneratedFieldSpec>[];
    final otherFields = <_GeneratedFieldSpec>[];

    for (final field in fields) {
      if (_isPrimitiveTypeId(field.shape.typeId)) {
        if (field.nullable) {
          boxedPrimitiveFields.add(field);
        } else {
          primitiveFields.add(field);
        }
      } else if (field.shape.typeId == TypeIds.list ||
          field.shape.typeId == TypeIds.set) {
        collectionFields.add(field);
      } else if (field.shape.typeId == TypeIds.map) {
        mapFields.add(field);
      } else if (_isBuiltInTypeId(field.shape.typeId)) {
        builtInFields.add(field);
      } else {
        otherFields.add(field);
      }
    }

    primitiveFields.sort(_comparePrimitiveFields);
    boxedPrimitiveFields.sort(_comparePrimitiveFields);
    builtInFields.sort(_compareNonPrimitiveFields);
    collectionFields.sort(_compareNonPrimitiveFields);
    mapFields.sort(_compareNonPrimitiveFields);
    otherFields.sort(_compareOtherFields);

    return <_GeneratedFieldSpec>[
      ...primitiveFields,
      ...boxedPrimitiveFields,
      ...builtInFields,
      ...collectionFields,
      ...mapFields,
      ...otherFields,
    ];
  }

  int _comparePrimitiveFields(
    _GeneratedFieldSpec left,
    _GeneratedFieldSpec right,
  ) {
    final leftCompressed = _isCompressedTypeId(left.shape.typeId);
    final rightCompressed = _isCompressedTypeId(right.shape.typeId);
    if (leftCompressed != rightCompressed) {
      return leftCompressed ? 1 : -1;
    }
    final sizeCompare =
        _primitiveSize(right.shape.typeId) - _primitiveSize(left.shape.typeId);
    if (sizeCompare != 0) {
      return sizeCompare;
    }
    final typeCompare = right.shape.typeId - left.shape.typeId;
    if (typeCompare != 0) {
      return typeCompare;
    }
    final keyCompare = left.sortKey.compareTo(right.sortKey);
    if (keyCompare != 0) {
      return keyCompare;
    }
    return left.name.compareTo(right.name);
  }

  int _compareNonPrimitiveFields(
    _GeneratedFieldSpec left,
    _GeneratedFieldSpec right,
  ) {
    final typeCompare = left.shape.typeId - right.shape.typeId;
    if (typeCompare != 0) {
      return typeCompare;
    }
    final keyCompare = left.sortKey.compareTo(right.sortKey);
    if (keyCompare != 0) {
      return keyCompare;
    }
    return left.name.compareTo(right.name);
  }

  int _compareOtherFields(
    _GeneratedFieldSpec left,
    _GeneratedFieldSpec right,
  ) {
    final keyCompare = left.sortKey.compareTo(right.sortKey);
    if (keyCompare != 0) {
      return keyCompare;
    }
    return left.name.compareTo(right.name);
  }

  int _primitiveSize(int typeId) {
    switch (typeId) {
      case TypeIds.boolType:
      case TypeIds.int8:
      case TypeIds.uint8:
        return 1;
      case TypeIds.int16:
      case TypeIds.uint16:
      case TypeIds.float16:
        return 2;
      case TypeIds.int32:
      case TypeIds.varInt32:
      case TypeIds.uint32:
      case TypeIds.varUint32:
      case TypeIds.float32:
        return 4;
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
      case TypeIds.uint64:
      case TypeIds.varUint64:
      case TypeIds.taggedUint64:
      case TypeIds.float64:
        return 8;
      default:
        return 0;
    }
  }

  bool _isCompressedTypeId(int typeId) {
    switch (typeId) {
      case TypeIds.varInt32:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
      case TypeIds.varUint32:
      case TypeIds.varUint64:
      case TypeIds.taggedUint64:
        return true;
      default:
        return false;
    }
  }

  bool _isPrimitiveTypeId(int typeId) {
    switch (typeId) {
      case TypeIds.boolType:
      case TypeIds.int8:
      case TypeIds.int16:
      case TypeIds.int32:
      case TypeIds.varInt32:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
      case TypeIds.int64:
      case TypeIds.uint8:
      case TypeIds.uint16:
      case TypeIds.uint32:
      case TypeIds.varUint32:
      case TypeIds.uint64:
      case TypeIds.varUint64:
      case TypeIds.taggedUint64:
      case TypeIds.float16:
      case TypeIds.float32:
      case TypeIds.float64:
        return true;
      default:
        return false;
    }
  }

  bool _isBuiltInTypeId(int typeId) {
    switch (typeId) {
      case TypeIds.string:
      case TypeIds.binary:
      case TypeIds.date:
      case TypeIds.timestamp:
      case TypeIds.boolArray:
      case TypeIds.int8Array:
      case TypeIds.int16Array:
      case TypeIds.int32Array:
      case TypeIds.int64Array:
      case TypeIds.uint8Array:
      case TypeIds.uint16Array:
      case TypeIds.uint32Array:
      case TypeIds.uint64Array:
      case TypeIds.float16Array:
      case TypeIds.float32Array:
      case TypeIds.float64Array:
        return true;
      default:
        return false;
    }
  }

  _IntegerAnnotationSpec? _analyzeIntegerAnnotation(FieldElement field) {
    final int32Annotation = _int32Checker.firstAnnotationOf(field);
    if (int32Annotation != null) {
      final reader = ConstantReader(int32Annotation);
      final compress = reader.peek('compress')?.boolValue ?? true;
      return _IntegerAnnotationSpec(
        typeId: compress ? TypeIds.varInt32 : TypeIds.int32,
      );
    }
    final int64Annotation = _int64Checker.firstAnnotationOf(field);
    if (int64Annotation != null) {
      final reader = ConstantReader(int64Annotation);
      final encodingReader = reader.peek('encoding');
      final encodingValue = encodingReader == null || encodingReader.isNull
          ? 'varint'
          : encodingReader.revive().accessor.split('.').last;
      final typeId = switch (encodingValue) {
        'varint' => TypeIds.varInt64,
        'tagged' => TypeIds.taggedInt64,
        'fixed' => TypeIds.int64,
        _ => throw InvalidGenerationSourceError(
            'Unsupported Int64Type encoding: $encodingValue.',
            element: field,
          ),
      };
      return _IntegerAnnotationSpec(typeId: typeId);
    }
    final uint8Annotation = _uint8Checker.firstAnnotationOf(field);
    if (uint8Annotation != null) {
      return const _IntegerAnnotationSpec(typeId: TypeIds.uint8);
    }
    final uint16Annotation = _uint16Checker.firstAnnotationOf(field);
    if (uint16Annotation != null) {
      return const _IntegerAnnotationSpec(typeId: TypeIds.uint16);
    }
    final uint32Annotation = _uint32Checker.firstAnnotationOf(field);
    if (uint32Annotation != null) {
      final reader = ConstantReader(uint32Annotation);
      final compress = reader.peek('compress')?.boolValue ?? true;
      return _IntegerAnnotationSpec(
        typeId: compress ? TypeIds.varUint32 : TypeIds.uint32,
      );
    }
    final uint64Annotation = _uint64Checker.firstAnnotationOf(field);
    if (uint64Annotation != null) {
      final reader = ConstantReader(uint64Annotation);
      final encodingReader = reader.peek('encoding');
      final encodingValue = encodingReader == null || encodingReader.isNull
          ? 'varint'
          : encodingReader.revive().accessor.split('.').last;
      final typeId = switch (encodingValue) {
        'varint' => TypeIds.varUint64,
        'tagged' => TypeIds.taggedUint64,
        'fixed' => TypeIds.uint64,
        _ => throw InvalidGenerationSourceError(
            'Unsupported Uint64Type encoding: $encodingValue.',
            element: field,
          ),
      };
      return _IntegerAnnotationSpec(typeId: typeId);
    }
    return null;
  }

  int _typeIdFor(DartType type, {_IntegerAnnotationSpec? integerAnnotation}) {
    if (integerAnnotation != null) {
      return integerAnnotation.typeId;
    }
    final nonNullable = _withoutNullability(type);
    if (nonNullable.isDartCoreBool) {
      return TypeIds.boolType;
    }
    if (nonNullable.isDartCoreInt) {
      return TypeIds.varInt64;
    }
    if (nonNullable.isDartCoreDouble) {
      return TypeIds.float64;
    }
    if (nonNullable.isDartCoreString) {
      return TypeIds.string;
    }
    if (_isList(nonNullable)) {
      return TypeIds.list;
    }
    if (_isSet(nonNullable)) {
      return TypeIds.set;
    }
    if (_isMap(nonNullable)) {
      return TypeIds.map;
    }
    final display = _typeLiteral(nonNullable);
    switch (display) {
      case 'Int8':
        return TypeIds.int8;
      case 'Int16':
        return TypeIds.int16;
      case 'Int32':
        return TypeIds.varInt32;
      case 'UInt8':
        return TypeIds.uint8;
      case 'UInt16':
        return TypeIds.uint16;
      case 'UInt32':
        return TypeIds.uint32;
      case 'Float16':
        return TypeIds.float16;
      case 'Float32':
        return TypeIds.float32;
      case 'Timestamp':
        return TypeIds.timestamp;
      case 'LocalDate':
        return TypeIds.date;
      case 'Uint8List':
        return TypeIds.binary;
      case 'List<bool>':
        return TypeIds.boolArray;
      case 'Int8List':
        return TypeIds.int8Array;
      case 'Int16List':
        return TypeIds.int16Array;
      case 'Int32List':
        return TypeIds.int32Array;
      case 'Int64List':
        return TypeIds.int64Array;
      case 'Uint16List':
        return TypeIds.uint16Array;
      case 'Uint32List':
        return TypeIds.uint32Array;
      case 'Uint64List':
        return TypeIds.uint64Array;
      case 'Float32List':
        return TypeIds.float32Array;
      case 'Float64List':
        return TypeIds.float64Array;
      case 'Object':
        return TypeIds.unknown;
      default:
        if (nonNullable.element is EnumElement) {
          return TypeIds.enumById;
        }
        return TypeIds.compatibleStruct;
    }
  }

  bool _sameType(DartType left, DartType right) =>
      _typeLiteral(_withoutNullability(left)) ==
      _typeLiteral(_withoutNullability(right));

  bool? _autoDynamic(DartType type) {
    final nonNullable = _withoutNullability(type);
    if (nonNullable is DynamicType || nonNullable is InvalidType) {
      return true;
    }
    if (nonNullable.isDartCoreObject) {
      return true;
    }
    if (_isList(nonNullable) || _isSet(nonNullable) || _isMap(nonNullable)) {
      return null;
    }
    final typeId = _typeIdFor(nonNullable);
    if (_isPrimitiveTypeId(typeId) ||
        _isBuiltInTypeId(typeId) ||
        typeId == TypeIds.enumById) {
      return null;
    }
    final element = nonNullable.element;
    if (element is ClassElement && element.isAbstract) {
      return true;
    }
    return null;
  }

  DartType _withoutNullability(DartType type) {
    if (type.nullabilitySuffix != NullabilitySuffix.question) {
      return type;
    }
    if (type is InterfaceType) {
      return type.element.instantiate(
        typeArguments: type.typeArguments,
        nullabilitySuffix: NullabilitySuffix.none,
      );
    }
    if (type is TypeParameterType) {
      return type.element.instantiate(
        nullabilitySuffix: NullabilitySuffix.none,
      );
    }
    return type;
  }

  bool _isNullable(DartType type) =>
      type.nullabilitySuffix == NullabilitySuffix.question;

  bool _isList(DartType type) => type.isDartCoreList;

  bool _isSet(DartType type) =>
      type is InterfaceType && type.element.name == 'Set';

  bool _isMap(DartType type) => type.isDartCoreMap;

  String _typeLiteral(DartType type) {
    if (type is DynamicType || type is InvalidType) {
      return 'Object';
    }
    if (type is InterfaceType) {
      return type.element.name;
    }
    return type.getDisplayString().replaceAll('?', '');
  }

  String _buildNamespace(AssetId id) {
    final normalizedPath = id.path.replaceFirst(RegExp(r'^lib/'), '');
    return '${id.package}/${normalizedPath.replaceAll('.dart', '')}';
  }

  String _toPascalCase(String value) => value
      .split(RegExp(r'[_\-\s]+'))
      .where((part) => part.isNotEmpty)
      .map((part) => '${part[0].toUpperCase()}${part.substring(1)}')
      .join();

  String _toCamelCase(String value) {
    final pascal = _toPascalCase(value);
    if (pascal.isEmpty) {
      return pascal;
    }
    return '${pascal[0].toLowerCase()}${pascal.substring(1)}';
  }

  String _toSnakeCase(String value) {
    final buffer = StringBuffer();
    for (var index = 0; index < value.length; index += 1) {
      final codeUnit = value.codeUnitAt(index);
      final isUpper = codeUnit >= 65 && codeUnit <= 90;
      if (isUpper && index > 0) {
        buffer.write('_');
      }
      buffer.write(String.fromCharCode(isUpper ? codeUnit + 32 : codeUnit));
    }
    return buffer.toString();
  }
}

final class _GeneratedEnumSpec {
  final String name;

  const _GeneratedEnumSpec({required this.name});
}

final class _GeneratedStructSpec {
  final String name;
  final bool evolving;
  final List<_GeneratedFieldSpec> fields;
  final _ConstructorPlan constructorPlan;

  const _GeneratedStructSpec({
    required this.name,
    required this.evolving,
    required this.fields,
    required this.constructorPlan,
  });
}

final class _GeneratedFieldSpec {
  final String name;
  final DartType type;
  final String displayType;
  final String identifier;
  final int? id;
  final bool nullable;
  final bool ref;
  final bool? dynamic;
  final bool writable;
  final _GeneratedShapeSpec shape;

  const _GeneratedFieldSpec({
    required this.name,
    required this.type,
    required this.displayType,
    required this.identifier,
    required this.id,
    required this.nullable,
    required this.ref,
    required this.dynamic,
    required this.writable,
    required this.shape,
  });

  String get sortKey => id != null && id! >= 0 ? '$id' : identifier;

  String readerFunctionName(String structName) {
    final fieldName = '${name[0].toUpperCase()}${name.substring(1)}';
    return '_read$structName$fieldName';
  }

  String get localName => '_${name}Value';
}

final class _GeneratedShapeSpec {
  final String typeLiteral;
  final int typeId;
  final bool nullable;
  final bool ref;
  final bool? dynamic;
  final List<_GeneratedShapeSpec> arguments;

  const _GeneratedShapeSpec({
    required this.typeLiteral,
    required this.typeId,
    required this.nullable,
    required this.ref,
    required this.dynamic,
    required this.arguments,
  });
}

enum _ConstructorMode { mutable, constructor }

final class _ConstructorPlan {
  final _ConstructorMode mode;
  final List<_ConstructorArgumentSpec> arguments;
  final List<String> postConstructionFieldNames;

  const _ConstructorPlan.mutable()
      : mode = _ConstructorMode.mutable,
        arguments = const <_ConstructorArgumentSpec>[],
        postConstructionFieldNames = const <String>[];

  const _ConstructorPlan.constructor({
    required this.arguments,
    required this.postConstructionFieldNames,
  }) : mode = _ConstructorMode.constructor;
}

final class _ConstructorArgumentSpec {
  final String fieldName;
  final String parameterName;
  final bool named;

  const _ConstructorArgumentSpec({
    required this.fieldName,
    required this.parameterName,
    required this.named,
  });
}

final class _IntegerAnnotationSpec {
  final int typeId;

  const _IntegerAnnotationSpec({required this.typeId});
}
