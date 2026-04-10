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

  @override
  FutureOr<String> generate(LibraryReader library, BuildStep buildStep) {
    final classes = library.classes
        .where((element) => _foryObjectChecker.hasAnnotationOf(element))
        .toList(growable: false);
    final enums = library.enums.toList(growable: false);
    if (classes.isEmpty && enums.isEmpty) {
      return '';
    }

    final helperName =
        '_register${_toPascalCase(buildStep.inputId.pathSegments.last.split('.').first)}ForyTypes';
    final namespace = _buildNamespace(buildStep.inputId);
    final output = StringBuffer();
    output
      ..writeln('// GENERATED CODE - DO NOT MODIFY BY HAND')
      ..writeln();

    for (final enumElement in enums) {
      _writeEnum(output, enumElement, namespace);
    }
    for (final classElement in classes) {
      _writeStruct(output, classElement, namespace);
    }

    output.writeln('void $helperName(Fory fory) {');
    for (final enumElement in enums) {
      final serializerName = '_${enumElement.name}ForySerializer';
      output.writeln(
        "  fory.register(${enumElement.name}, const $serializerName(), namespace: '$namespace', typeName: '${enumElement.name}');",
      );
    }
    for (final classElement in classes) {
      final serializerName = '_${classElement.name}ForySerializer';
      output.writeln(
        "  fory.register(${classElement.name}, const $serializerName(), namespace: '$namespace', typeName: '${classElement.name}');",
      );
    }
    output.writeln('}');

    return output.toString();
  }

  void _writeEnum(StringBuffer output, EnumElement element, String namespace) {
    final serializerName = '_${element.name}ForySerializer';
    output
      ..writeln('final class $serializerName extends Serializer<${element.name}> {')
      ..writeln('  const $serializerName();')
      ..writeln('  bool get isEnum => true;')
      ..writeln('  @override')
      ..writeln('  void write(WriteContext context, ${element.name} value) {')
      ..writeln('    context.writeVarUint32(value.index);')
      ..writeln('  }')
      ..writeln('  @override')
      ..writeln('  ${element.name} read(ReadContext context) {')
      ..writeln('    return ${element.name}.values[context.readVarUint32()];')
      ..writeln('  }')
      ..writeln('}')
      ..writeln();
  }

  void _writeStruct(
    StringBuffer output,
    ClassElement element,
    String namespace,
  ) {
    final constructor = element.unnamedConstructor;
    if (constructor == null || constructor.parameters.any((p) => p.isRequiredPositional || p.isRequiredNamed)) {
      throw InvalidGenerationSourceError(
        'Only classes with a zero-argument unnamed constructor are supported.',
        element: element,
      );
    }
    final objectAnnotation = _foryObjectChecker.firstAnnotationOf(element)!;
    final objectReader = ConstantReader(objectAnnotation);
    final evolving = objectReader.peek('evolving')?.boolValue ?? true;
    final fields = element.fields
        .where((field) => !field.isStatic && !field.isSynthetic && !field.name.startsWith('_'))
        .where((field) => !_isSkipped(field))
        .toList(growable: false);
    for (final field in fields) {
      if (field.isFinal) {
        throw InvalidGenerationSourceError(
          'Final fields are not supported by the current Dart generator.',
          element: field,
        );
      }
    }

    final serializerName = '_${element.name}ForySerializer';
    final metadataListName = '_${element.name}ForyFields';

    output.writeln(
      'const List<Map<String, Object?>> $metadataListName = <Map<String, Object?>>[',
    );
    for (final field in fields) {
      output.writeln(_fieldMetadataLiteral(field));
    }
    output
      ..writeln('];')
      ..writeln()
      ..writeln('final class $serializerName extends Serializer<${element.name}> {')
      ..writeln('  const $serializerName();')
      ..writeln('  bool get isStruct => true;')
      ..writeln('  @override')
      ..writeln('  bool get evolving => ${evolving ? 'true' : 'false'};')
      ..writeln('  @override')
      ..writeln('  List<Map<String, Object?>> get fields => $metadataListName;')
      ..writeln('  @override')
      ..writeln('  void write(WriteContext context, ${element.name} value) {');
    for (var index = 0; index < fields.length; index += 1) {
      final field = fields[index];
      output.writeln(
        '    context.writeField($metadataListName[$index], value.${field.name});',
      );
    }
    output
      ..writeln('  }')
      ..writeln('  @override')
      ..writeln('  ${element.name} read(ReadContext context) {')
      ..writeln('    final value = ${element.name}();')
      ..writeln('    context.reference(value);');
    for (var index = 0; index < fields.length; index += 1) {
      final field = fields[index];
      final readerName = '_read${element.name}${_toPascalCase(field.name)}';
      output.writeln(
        '    value.${field.name} = $readerName(context.readField<Object?>($metadataListName[$index], value.${field.name}));',
      );
    }
    output
      ..writeln('    return value;')
      ..writeln('  }')
      ..writeln('}')
      ..writeln();

    for (final field in fields) {
      final readerName = '_read${element.name}${_toPascalCase(field.name)}';
      output
        ..writeln(
          '${field.type.getDisplayString()} $readerName(Object? value) {',
        )
        ..writeln('  return ${_conversionExpression(field.type, 'value')};')
        ..writeln('}')
        ..writeln();
    }
  }

  bool _isSkipped(FieldElement field) {
    final annotation = _foryFieldChecker.firstAnnotationOf(field);
    if (annotation == null) {
      return false;
    }
    return ConstantReader(annotation).peek('skip')?.boolValue ?? false;
  }

  String _fieldMetadataLiteral(FieldElement field) {
    final annotation = _foryFieldChecker.firstAnnotationOf(field);
    final reader = annotation == null ? null : ConstantReader(annotation);
    final idLiteral = reader?.peek('id')?.isNull == false
        ? '${reader!.peek('id')!.intValue}'
        : 'null';
    final dynamicLiteral = reader?.peek('dynamic') == null || reader!.peek('dynamic')!.isNull
        ? 'null'
        : (reader.peek('dynamic')!.boolValue ? 'true' : 'false');
    final nullableLiteral = reader?.peek('nullable') != null && !reader!.peek('nullable')!.isNull
        ? (reader.peek('nullable')!.boolValue ? 'true' : 'false')
        : (_isNullable(field.type) ? 'true' : 'false');
    final refLiteral = reader?.peek('ref')?.boolValue ?? false;
    final identifier = reader?.peek('id')?.isNull == false
        ? '${reader!.peek('id')!.intValue}'
        : _toSnakeCase(field.name);
    return '''
  <String, Object?>{
    'name': '${field.name}',
    'identifier': '$identifier',
    'id': $idLiteral,
    'shape': ${_shapeLiteral(field.type, nullableLiteral, refLiteral, dynamicLiteral)},
  },''';
  }

  String _shapeLiteral(
    DartType type,
    String nullableLiteral,
    bool ref,
    String dynamicLiteral,
  ) {
    final typeId = _typeIdFor(type);
    final typeLiteral = _typeLiteral(type);
    if (_isList(type) || _isSet(type)) {
      final argument = (type as InterfaceType).typeArguments.single;
      return '''
<String, Object?>{
      'type': $typeLiteral,
      'typeId': $typeId,
      'nullable': $nullableLiteral,
      'ref': $ref,
      'dynamic': $dynamicLiteral,
      'arguments': <Object?>[
        ${_shapeLiteral(argument, _isNullable(argument) ? 'true' : 'false', false, _autoDynamicLiteral(argument))}
      ],
    }''';
    }
    if (_isMap(type)) {
      final arguments = (type as InterfaceType).typeArguments;
      return '''
<String, Object?>{
      'type': $typeLiteral,
      'typeId': $typeId,
      'nullable': $nullableLiteral,
      'ref': $ref,
      'dynamic': $dynamicLiteral,
      'arguments': <Object?>[
        ${_shapeLiteral(arguments[0], _isNullable(arguments[0]) ? 'true' : 'false', false, _autoDynamicLiteral(arguments[0]))},
        ${_shapeLiteral(arguments[1], _isNullable(arguments[1]) ? 'true' : 'false', false, _autoDynamicLiteral(arguments[1]))}
      ],
    }''';
    }
    return '''
<String, Object?>{
      'type': $typeLiteral,
      'typeId': $typeId,
      'nullable': $nullableLiteral,
      'ref': $ref,
      'dynamic': $dynamicLiteral,
      'arguments': const <Object?>[],
    }''';
  }

  String _conversionExpression(DartType type, String valueExpression) {
    if (_isNullable(type)) {
      final nonNullable = type is InterfaceType
          ? type.element.thisType
          : type;
      final converted = _conversionExpression(nonNullable, valueExpression);
      return '$valueExpression == null ? null as ${type.getDisplayString()} : $converted';
    }
    if (_isList(type)) {
      final elementType = (type as InterfaceType).typeArguments.single;
      return 'List<${elementType.getDisplayString()}>.of((($valueExpression as List)).map((item) => ${_conversionExpression(elementType, 'item')}))';
    }
    if (_isSet(type)) {
      final elementType = (type as InterfaceType).typeArguments.single;
      return 'Set<${elementType.getDisplayString()}>.of((($valueExpression as Set)).map((item) => ${_conversionExpression(elementType, 'item')}))';
    }
    if (_isMap(type)) {
      final arguments = (type as InterfaceType).typeArguments;
      final keyType = arguments[0];
      final valueType = arguments[1];
      return 'Map<${keyType.getDisplayString()}, ${valueType.getDisplayString()}>.of((($valueExpression as Map)).map((key, value) => MapEntry(${_conversionExpression(keyType, 'key')}, ${_conversionExpression(valueType, 'value')})))';
    }
    return '$valueExpression as ${type.getDisplayString()}';
  }

  String _autoDynamicLiteral(DartType type) {
    if (type is DynamicType || type is InvalidType) {
      return 'true';
    }
    if (type.isDartCoreObject) {
      return 'true';
    }
    final element = type.element;
    if (element is ClassElement && element.isAbstract) {
      return 'true';
    }
    return 'null';
  }

  bool _isNullable(DartType type) => type.nullabilitySuffix == NullabilitySuffix.question;

  bool _isList(DartType type) => type.isDartCoreList;

  bool _isSet(DartType type) => type is InterfaceType && type.element.name == 'Set';

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

  int _typeIdFor(DartType type) {
    final nonNullable = type is InterfaceType ? type.element.thisType : type;
    if (nonNullable.isDartCoreBool) {
      return TypeIds.boolType;
    }
    if (nonNullable.isDartCoreInt) {
      return TypeIds.int64;
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
        return TypeIds.int32;
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

  String _buildNamespace(AssetId id) {
    final normalizedPath = id.path.replaceFirst(RegExp(r'^lib/'), '');
    return '${id.package}/${normalizedPath.replaceAll('.dart', '')}';
  }

  String _toPascalCase(String value) =>
      value.split(RegExp(r'[_\-\s]+')).where((part) => part.isNotEmpty).map(
            (part) => '${part[0].toUpperCase()}${part.substring(1)}',
          ).join();

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
