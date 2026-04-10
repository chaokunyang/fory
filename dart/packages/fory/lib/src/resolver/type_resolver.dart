import 'dart:typed_data';

import 'package:fory/src/config.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';

abstract final class TypeIds {
  static const int unknown = 0;
  static const int boolType = 1;
  static const int int8 = 2;
  static const int int16 = 3;
  static const int int32 = 4;
  static const int int64 = 6;
  static const int uint8 = 9;
  static const int uint16 = 10;
  static const int uint32 = 11;
  static const int float16 = 17;
  static const int float32 = 19;
  static const int float64 = 20;
  static const int string = 21;
  static const int list = 22;
  static const int set = 23;
  static const int map = 24;
  static const int enumById = 25;
  static const int namedEnum = 26;
  static const int struct = 27;
  static const int compatibleStruct = 28;
  static const int namedStruct = 29;
  static const int namedCompatibleStruct = 30;
  static const int ext = 31;
  static const int namedExt = 32;
  static const int union = 33;
  static const int typedUnion = 34;
  static const int namedUnion = 35;
  static const int none = 36;
  static const int timestamp = 38;
  static const int date = 39;
  static const int binary = 41;
  static const int boolArray = 43;
  static const int int8Array = 44;
  static const int int16Array = 45;
  static const int int32Array = 46;
  static const int int64Array = 47;
  static const int uint8Array = 48;
  static const int uint16Array = 49;
  static const int uint32Array = 50;
  static const int float16Array = 53;
  static const int float32Array = 55;
  static const int float64Array = 56;

  static bool isPrimitive(int typeId) =>
      typeId == boolType ||
      typeId == int8 ||
      typeId == int16 ||
      typeId == int32 ||
      typeId == int64 ||
      typeId == uint8 ||
      typeId == uint16 ||
      typeId == uint32 ||
      typeId == float16 ||
      typeId == float32 ||
      typeId == float64;

  static bool isContainer(int typeId) =>
      typeId == list || typeId == set || typeId == map;

  static bool isUserType(int typeId) =>
      typeId == enumById ||
      typeId == namedEnum ||
      typeId == struct ||
      typeId == compatibleStruct ||
      typeId == namedStruct ||
      typeId == namedCompatibleStruct ||
      typeId == ext ||
      typeId == namedExt ||
      typeId == union ||
      typeId == typedUnion ||
      typeId == namedUnion;

  static bool isBasicValue(int typeId) =>
      isPrimitive(typeId) ||
      typeId == string ||
      typeId == binary ||
      typeId == timestamp ||
      typeId == date ||
      typeId == boolArray ||
      typeId == int8Array ||
      typeId == int16Array ||
      typeId == int32Array ||
      typeId == int64Array ||
      typeId == uint8Array ||
      typeId == uint16Array ||
      typeId == uint32Array ||
      typeId == float16Array ||
      typeId == float32Array ||
      typeId == float64Array;
}

enum RegistrationKindInternal { builtin, struct, enumType, ext, union }

final class TypeShapeInternal {
  final Type type;
  final int typeId;
  final bool nullable;
  final bool ref;
  final bool? dynamic;
  final List<TypeShapeInternal> arguments;

  const TypeShapeInternal({
    required this.type,
    required this.typeId,
    required this.nullable,
    required this.ref,
    required this.dynamic,
    required this.arguments,
  });

  bool get isDynamic => dynamic == true || typeId == TypeIds.unknown;

  bool get isPrimitive => TypeIds.isPrimitive(typeId);

  bool get isContainer => TypeIds.isContainer(typeId);

  static TypeShapeInternal fromMetadata(Map<String, Object?> metadata) {
    final rawArguments =
        metadata['arguments'] as List<Object?>? ?? const <Object?>[];
    return TypeShapeInternal(
      type: metadata['type'] as Type,
      typeId: metadata['typeId'] as int,
      nullable: metadata['nullable'] as bool? ?? false,
      ref: metadata['ref'] as bool? ?? false,
      dynamic: metadata['dynamic'] as bool?,
      arguments: rawArguments
          .cast<Map<String, Object?>>()
          .map(TypeShapeInternal.fromMetadata)
          .toList(growable: false),
    );
  }
}

final class FieldMetadataInternal {
  final String name;
  final String identifier;
  final int? id;
  final TypeShapeInternal shape;

  const FieldMetadataInternal({
    required this.name,
    required this.identifier,
    required this.id,
    required this.shape,
  });

  static FieldMetadataInternal fromMetadata(Map<String, Object?> metadata) {
    return FieldMetadataInternal(
      name: metadata['name'] as String,
      identifier: metadata['identifier'] as String,
      id: metadata['id'] as int?,
      shape: TypeShapeInternal.fromMetadata(
        metadata['shape'] as Map<String, Object?>,
      ),
    );
  }
}

final class StructMetadataInternal {
  final bool evolving;
  final List<FieldMetadataInternal> fields;

  const StructMetadataInternal({required this.evolving, required this.fields});
}

final class ResolvedTypeInternal {
  final Type type;
  final RegistrationKindInternal kind;
  final int typeId;
  final Serializer<Object?>? serializer;
  final int? userTypeId;
  final String? namespace;
  final String? typeName;
  final StructMetadataInternal? structMetadata;

  const ResolvedTypeInternal({
    required this.type,
    required this.kind,
    required this.typeId,
    required this.serializer,
    required this.userTypeId,
    required this.namespace,
    required this.typeName,
    required this.structMetadata,
  });

  bool get isNamed => userTypeId == null && namespace != null && typeName != null;

  bool get isCompatibleStruct =>
      kind == RegistrationKindInternal.struct && structMetadata!.evolving;

  bool get isBasicValue => TypeIds.isBasicValue(typeId);

  int wireTypeId(Config config) {
    switch (kind) {
      case RegistrationKindInternal.builtin:
        return typeId;
      case RegistrationKindInternal.enumType:
        return isNamed ? TypeIds.namedEnum : TypeIds.enumById;
      case RegistrationKindInternal.ext:
        return isNamed ? TypeIds.namedExt : TypeIds.ext;
      case RegistrationKindInternal.union:
        if (userTypeId != null) {
          return TypeIds.typedUnion;
        }
        return isNamed ? TypeIds.namedUnion : TypeIds.union;
      case RegistrationKindInternal.struct:
        final compatible = config.compatible && structMetadata!.evolving;
        if (compatible) {
          return isNamed ? TypeIds.namedCompatibleStruct : TypeIds.compatibleStruct;
        }
        return isNamed ? TypeIds.namedStruct : TypeIds.struct;
    }
  }
}

final class TypeResolver {
  final Config config;
  final Map<Type, ResolvedTypeInternal> _registeredByType =
      <Type, ResolvedTypeInternal>{};
  final Map<int, ResolvedTypeInternal> _registeredById =
      <int, ResolvedTypeInternal>{};
  final Map<String, ResolvedTypeInternal> _registeredByName =
      <String, ResolvedTypeInternal>{};

  TypeResolver(this.config);

  void register(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _validateRegistrationMode(id: id, namespace: namespace, typeName: typeName);
    final registrationKind = _inferKind(serializer);
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: registrationKind,
      typeId: _defaultTypeIdForType(type),
      serializer: serializer as Serializer<Object?>,
      userTypeId: id,
      namespace: namespace,
      typeName: typeName,
      structMetadata: registrationKind == RegistrationKindInternal.struct
          ? _parseStructMetadata(serializer)
          : null,
    );
    _registeredByType[type] = resolved;
    if (id != null) {
      _registeredById[id] = resolved;
    } else {
      _registeredByName[_nameKey(namespace!, typeName!)] = resolved;
    }
  }

  void registerUnion(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _validateRegistrationMode(id: id, namespace: namespace, typeName: typeName);
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: RegistrationKindInternal.union,
      typeId: TypeIds.union,
      serializer: serializer as Serializer<Object?>,
      userTypeId: id,
      namespace: namespace,
      typeName: typeName,
      structMetadata: null,
    );
    _registeredByType[type] = resolved;
    if (id != null) {
      _registeredById[id] = resolved;
    } else {
      _registeredByName[_nameKey(namespace!, typeName!)] = resolved;
    }
  }

  ResolvedTypeInternal resolveValue(Object value) {
    if (value is bool) {
      return _builtin(bool, TypeIds.boolType);
    }
    if (value is Int8) {
      return _builtin(Int8, TypeIds.int8);
    }
    if (value is Int16) {
      return _builtin(Int16, TypeIds.int16);
    }
    if (value is Int32) {
      return _builtin(Int32, TypeIds.int32);
    }
    if (value is int) {
      return _builtin(int, TypeIds.int64);
    }
    if (value is UInt8) {
      return _builtin(UInt8, TypeIds.uint8);
    }
    if (value is UInt16) {
      return _builtin(UInt16, TypeIds.uint16);
    }
    if (value is UInt32) {
      return _builtin(UInt32, TypeIds.uint32);
    }
    if (value is Float16) {
      return _builtin(Float16, TypeIds.float16);
    }
    if (value is Float32) {
      return _builtin(Float32, TypeIds.float32);
    }
    if (value is double) {
      return _builtin(double, TypeIds.float64);
    }
    if (value is String) {
      return _builtin(String, TypeIds.string);
    }
    if (value is List) {
      return _builtin(List, TypeIds.list);
    }
    if (value is Set) {
      return _builtin(Set, TypeIds.set);
    }
    if (value is Map) {
      return _builtin(Map, TypeIds.map);
    }
    if (value is Uint8List) {
      return _builtin(Uint8List, TypeIds.binary);
    }
    if (value is Int8List) {
      return _builtin(Int8List, TypeIds.int8Array);
    }
    if (value is Int16List) {
      return _builtin(Int16List, TypeIds.int16Array);
    }
    if (value is Int32List) {
      return _builtin(Int32List, TypeIds.int32Array);
    }
    if (value is Int64List) {
      return _builtin(Int64List, TypeIds.int64Array);
    }
    if (value is Uint16List) {
      return _builtin(Uint16List, TypeIds.uint16Array);
    }
    if (value is Uint32List) {
      return _builtin(Uint32List, TypeIds.uint32Array);
    }
    if (value is Float32List) {
      return _builtin(Float32List, TypeIds.float32Array);
    }
    if (value is Float64List) {
      return _builtin(Float64List, TypeIds.float64Array);
    }
    if (value is LocalDate) {
      return _builtin(LocalDate, TypeIds.date);
    }
    if (value is Timestamp) {
      return _builtin(Timestamp, TypeIds.timestamp);
    }
    final resolved = _registeredByType[value.runtimeType];
    if (resolved != null) {
      return resolved;
    }
    throw StateError(
      'Type ${value.runtimeType} is not registered. Call the generated registration helper or register a serializer explicitly.',
    );
  }

  ResolvedTypeInternal resolveShape(TypeShapeInternal shape) {
    switch (shape.typeId) {
      case TypeIds.unknown:
      case TypeIds.boolType:
      case TypeIds.int8:
      case TypeIds.int16:
      case TypeIds.int32:
      case TypeIds.int64:
      case TypeIds.uint8:
      case TypeIds.uint16:
      case TypeIds.uint32:
      case TypeIds.float16:
      case TypeIds.float32:
      case TypeIds.float64:
      case TypeIds.string:
      case TypeIds.list:
      case TypeIds.set:
      case TypeIds.map:
      case TypeIds.binary:
      case TypeIds.timestamp:
      case TypeIds.date:
      case TypeIds.boolArray:
      case TypeIds.int8Array:
      case TypeIds.int16Array:
      case TypeIds.int32Array:
      case TypeIds.int64Array:
      case TypeIds.uint8Array:
      case TypeIds.uint16Array:
      case TypeIds.uint32Array:
      case TypeIds.float16Array:
      case TypeIds.float32Array:
      case TypeIds.float64Array:
        return _builtin(shape.type, shape.typeId);
      default:
        final resolved = _registeredByType[shape.type];
        if (resolved == null) {
          throw StateError('Type ${shape.type} is not registered.');
        }
        return resolved;
    }
  }

  ResolvedTypeInternal resolveUserById(int id) {
    final resolved = _registeredById[id];
    if (resolved == null) {
      throw StateError('Unknown registered type id $id.');
    }
    return resolved;
  }

  ResolvedTypeInternal resolveUserByName(String namespace, String typeName) {
    final resolved = _registeredByName[_nameKey(namespace, typeName)];
    if (resolved == null) {
      throw StateError('Unknown named type $namespace.$typeName.');
    }
    return resolved;
  }

  ResolvedTypeInternal _builtin(Type type, int typeId) {
    return ResolvedTypeInternal(
      type: type,
      kind: RegistrationKindInternal.builtin,
      typeId: typeId,
      serializer: null,
      userTypeId: null,
      namespace: null,
      typeName: null,
      structMetadata: null,
    );
  }

  RegistrationKindInternal _inferKind(Serializer serializer) {
    if (_readGeneratedFlag(serializer, (value) => value.isStruct)) {
      return RegistrationKindInternal.struct;
    }
    if (_readGeneratedFlag(serializer, (value) => value.isEnum)) {
      return RegistrationKindInternal.enumType;
    }
    if (_readGeneratedFlag(serializer, (value) => value.isUnion)) {
      return RegistrationKindInternal.union;
    }
    return RegistrationKindInternal.ext;
  }

  int _defaultTypeIdForType(Type type) {
    if (type == bool) {
      return TypeIds.boolType;
    }
    if (type == Int8) {
      return TypeIds.int8;
    }
    if (type == Int16) {
      return TypeIds.int16;
    }
    if (type == Int32) {
      return TypeIds.int32;
    }
    if (type == int) {
      return TypeIds.int64;
    }
    if (type == UInt8) {
      return TypeIds.uint8;
    }
    if (type == UInt16) {
      return TypeIds.uint16;
    }
    if (type == UInt32) {
      return TypeIds.uint32;
    }
    if (type == Float16) {
      return TypeIds.float16;
    }
    if (type == Float32) {
      return TypeIds.float32;
    }
    if (type == double) {
      return TypeIds.float64;
    }
    if (type == String) {
      return TypeIds.string;
    }
    if (type == List) {
      return TypeIds.list;
    }
    if (type == Set) {
      return TypeIds.set;
    }
    if (type == Map) {
      return TypeIds.map;
    }
    if (type == Uint8List) {
      return TypeIds.binary;
    }
    if (type == Timestamp) {
      return TypeIds.timestamp;
    }
    if (type == LocalDate) {
      return TypeIds.date;
    }
    return TypeIds.unknown;
  }

  StructMetadataInternal _parseStructMetadata(Serializer serializer) {
    final dynamic generated = serializer;
    final rawFields = generated.fields as List<Object?>? ?? const <Object?>[];
    return StructMetadataInternal(
      evolving: generated.evolving as bool? ?? true,
      fields: rawFields
          .cast<Map<String, Object?>>()
          .map(FieldMetadataInternal.fromMetadata)
          .toList(growable: false),
    );
  }

  bool _readGeneratedFlag(
    Serializer serializer,
    bool Function(dynamic serializer) selector,
  ) {
    try {
      final dynamic generated = serializer;
      return selector(generated) == true;
    } on NoSuchMethodError {
      return false;
    }
  }

  void _validateRegistrationMode({
    required int? id,
    required String? namespace,
    required String? typeName,
  }) {
    final hasNumeric = id != null;
    final hasNamed = namespace != null || typeName != null;
    if (hasNumeric == hasNamed) {
      throw ArgumentError(
        'Exactly one registration mode is required: id, or namespace + typeName.',
      );
    }
    if (hasNamed && (namespace == null || typeName == null)) {
      throw ArgumentError(
        'Both namespace and typeName are required for named registration.',
      );
    }
  }

  static String _nameKey(String namespace, String typeName) =>
      '$namespace::$typeName';
}
