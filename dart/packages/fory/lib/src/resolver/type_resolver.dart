import 'dart:collection';
import 'dart:typed_data';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/meta/type_meta.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/struct_runtime.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';
import 'package:fory/src/util/hash_util.dart';

abstract final class TypeIds {
  static const int unknown = 0;
  static const int boolType = 1;
  static const int int8 = 2;
  static const int int16 = 3;
  static const int int32 = 4;
  static const int varInt32 = 5;
  static const int int64 = 6;
  static const int varInt64 = 7;
  static const int taggedInt64 = 8;
  static const int uint8 = 9;
  static const int uint16 = 10;
  static const int uint32 = 11;
  static const int varUint32 = 12;
  static const int uint64 = 13;
  static const int varUint64 = 14;
  static const int taggedUint64 = 15;
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
  static const int uint64Array = 51;
  static const int float16Array = 53;
  static const int float32Array = 55;
  static const int float64Array = 56;

  static bool isPrimitive(int typeId) =>
      typeId == boolType ||
      typeId == int8 ||
      typeId == int16 ||
      typeId == int32 ||
      typeId == varInt32 ||
      typeId == int64 ||
      typeId == varInt64 ||
      typeId == taggedInt64 ||
      typeId == uint8 ||
      typeId == uint16 ||
      typeId == uint32 ||
      typeId == varUint32 ||
      typeId == uint64 ||
      typeId == varUint64 ||
      typeId == taggedUint64 ||
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
      typeId == uint64Array ||
      typeId == float16Array ||
      typeId == float32Array ||
      typeId == float64Array;

  static bool supportsRef(int typeId) {
    if (typeId == unknown) {
      return true;
    }
    if (isPrimitive(typeId) || typeId == binary) {
      return false;
    }
    switch (typeId) {
      case boolArray:
      case int8Array:
      case int16Array:
      case int32Array:
      case int64Array:
      case uint8Array:
      case uint16Array:
      case uint32Array:
      case uint64Array:
      case float16Array:
      case float32Array:
      case float64Array:
        return false;
      default:
        return true;
    }
  }
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
  final bool supportsRef;
  final Serializer<Object?>? serializer;
  final StructRuntime? structRuntime;
  final int? userTypeId;
  final String? namespace;
  final String? typeName;
  final EncodedMetaStringInternal? encodedNamespace;
  final EncodedMetaStringInternal? encodedTypeName;
  final StructMetadataInternal? structMetadata;
  final StructMetadataInternal? remoteStructMetadata;

  const ResolvedTypeInternal({
    required this.type,
    required this.kind,
    required this.typeId,
    required this.supportsRef,
    required this.serializer,
    required this.structRuntime,
    required this.userTypeId,
    required this.namespace,
    required this.typeName,
    required this.encodedNamespace,
    required this.encodedTypeName,
    required this.structMetadata,
    required this.remoteStructMetadata,
  });

  bool get isNamed =>
      userTypeId == null && namespace != null && typeName != null;

  bool get isCompatibleStruct =>
      kind == RegistrationKindInternal.struct && structMetadata!.evolving;

  bool get isBasicValue => TypeIds.isBasicValue(typeId);
}

final class TypeResolver {
  final Config config;
  final TypeMetaEncoder _typeMetaEncoder = const TypeMetaEncoder();
  final TypeMetaDecoder _typeMetaDecoder = const TypeMetaDecoder();
  final Map<Type, ResolvedTypeInternal> _registeredByType =
      <Type, ResolvedTypeInternal>{};
  final Map<int, ResolvedTypeInternal> _registeredById =
      <int, ResolvedTypeInternal>{};
  final Map<String, ResolvedTypeInternal> _registeredByName =
      <String, ResolvedTypeInternal>{};
  final Map<EncodedMetaStringInternal,
          Map<EncodedMetaStringInternal, ResolvedTypeInternal>>
      _registeredByEncodedName = LinkedHashMap<EncodedMetaStringInternal,
          Map<EncodedMetaStringInternal, ResolvedTypeInternal>>.identity();
  final Map<String, EncodedMetaStringInternal> _packageMetaStrings =
      <String, EncodedMetaStringInternal>{};
  final Map<String, EncodedMetaStringInternal> _typeNameMetaStrings =
      <String, EncodedMetaStringInternal>{};
  final Map<String, EncodedMetaStringInternal> _fieldNameMetaStrings =
      <String, EncodedMetaStringInternal>{};
  final Map<_EncodedMetaStringKey, EncodedMetaStringInternal>
      _internedEncodedMetaStrings =
      <_EncodedMetaStringKey, EncodedMetaStringInternal>{};

  TypeResolver(this.config);

  void registerGenerated(
    Type type,
    Serializer serializer, {
    required RegistrationKindInternal kind,
    bool evolving = true,
    List<FieldMetadataInternal> fields = const <FieldMetadataInternal>[],
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _validateRegistrationMode(id: id, namespace: namespace, typeName: typeName);
    final encodedNamespace =
        namespace == null ? null : packageMetaString(namespace);
    final encodedTypeName =
        typeName == null ? null : typeNameMetaString(typeName);
    final payloadSerializer = serializer as Serializer<Object?>;
    final structMetadata = kind == RegistrationKindInternal.struct
        ? StructMetadataInternal(
            evolving: evolving,
            fields: List<FieldMetadataInternal>.unmodifiable(fields),
          )
        : null;
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: kind,
      typeId: _defaultTypeIdForType(type),
      supportsRef: serializer.supportsRef,
      serializer: payloadSerializer,
      structRuntime: structMetadata == null
          ? null
          : StructRuntime(
              payloadSerializer,
              structMetadata,
            ),
      userTypeId: id,
      namespace: namespace,
      typeName: typeName,
      encodedNamespace: encodedNamespace,
      encodedTypeName: encodedTypeName,
      structMetadata: structMetadata,
      remoteStructMetadata: null,
    );
    _storeResolved(type, resolved, id: id, namespace: namespace, typeName: typeName);
  }

  void registerSerializer(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _validateRegistrationMode(id: id, namespace: namespace, typeName: typeName);
    final registrationKind = _inferKind(serializer);
    final encodedNamespace =
        namespace == null ? null : packageMetaString(namespace);
    final encodedTypeName =
        typeName == null ? null : typeNameMetaString(typeName);
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: registrationKind,
      typeId: _defaultTypeIdForType(type),
      supportsRef: serializer.supportsRef,
      serializer: serializer as Serializer<Object?>,
      structRuntime: null,
      userTypeId: id,
      namespace: namespace,
      typeName: typeName,
      encodedNamespace: encodedNamespace,
      encodedTypeName: encodedTypeName,
      structMetadata: null,
      remoteStructMetadata: null,
    );
    _storeResolved(type, resolved, id: id, namespace: namespace, typeName: typeName);
  }

  EncodedMetaStringInternal packageMetaString(String value) {
    return _packageMetaStrings.putIfAbsent(
      value,
      () => _canonicalMetaString(encodePackageMetaStringInternal(value)),
    );
  }

  EncodedMetaStringInternal typeNameMetaString(String value) {
    return _typeNameMetaStrings.putIfAbsent(
      value,
      () => _canonicalMetaString(encodeTypeNameMetaStringInternal(value)),
    );
  }

  EncodedMetaStringInternal fieldNameMetaString(String value) {
    return _fieldNameMetaStrings.putIfAbsent(
      value,
      () => _canonicalMetaString(encodeFieldNameMetaStringInternal(value)),
    );
  }

  EncodedMetaStringInternal internEncodedMetaString(
    Uint8List bytes, {
    required int encoding,
  }) {
    if (bytes.isEmpty) {
      return EncodedMetaStringInternal.empty;
    }
    final key = _EncodedMetaStringKey(encoding, bytes);
    final existing = _internedEncodedMetaStrings[key];
    if (existing != null) {
      return existing;
    }
    final encoded = EncodedMetaStringInternal(bytes, encoding);
    _internedEncodedMetaStrings[key] = encoded;
    return encoded;
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
      return _builtin(Int32, TypeIds.varInt32);
    }
    if (value is int) {
      return _builtin(int, TypeIds.varInt64);
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
    if (value is Uint64List) {
      return _builtin(Uint64List, TypeIds.uint64Array);
    }
    if (value is Float32List) {
      return _builtin(Float32List, TypeIds.float32Array);
    }
    if (value is Float64List) {
      return _builtin(Float64List, TypeIds.float64Array);
    }
    if (value is List<bool>) {
      return _builtin(List<bool>, TypeIds.boolArray);
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
      case TypeIds.varInt32:
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
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
      case TypeIds.uint64Array:
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

  ResolvedTypeInternal resolveUserByEncodedName(
    EncodedMetaStringInternal namespace,
    EncodedMetaStringInternal typeName,
  ) {
    final resolved = _registeredByEncodedName[namespace]?[typeName];
    if (resolved == null) {
      throw StateError(
        'Unknown named type ${decodePackageMetaStringInternal(namespace.bytes, namespace.encoding)}.'
        '${decodeTypeNameMetaStringInternal(typeName.bytes, typeName.encoding)}.',
      );
    }
    return resolved;
  }

  TypeMeta typeMetaForResolved(ResolvedTypeInternal resolved) {
    return _typeMetaEncoder.typeMetaFor(config, resolved);
  }

  void writeTypeMeta(
    Buffer buffer,
    ResolvedTypeInternal resolved, {
    required List<FieldMetadataInternal>? sharedTypeDefFields,
    required int? Function(String identity) lookupSharedTypeDefId,
    required int Function(String identity) reserveSharedTypeDefId,
    required void Function(EncodedMetaStringInternal value)
        writePackageMetaString,
    required void Function(EncodedMetaStringInternal value)
        writeTypeNameMetaString,
  }) {
    _typeMetaEncoder.write(
      buffer,
      typeMetaForResolved(resolved),
      writeSharedTypeDef: (typeMeta) => _writeSharedTypeDef(
        buffer,
        typeMeta,
        sharedTypeDefFields:
            sharedTypeDefFields ??
            resolved.structMetadata?.fields ??
            const <FieldMetadataInternal>[],
        lookupSharedTypeDefId: lookupSharedTypeDefId,
        reserveSharedTypeDefId: reserveSharedTypeDefId,
      ),
      writePackageMetaString: writePackageMetaString,
      writeTypeNameMetaString: writeTypeNameMetaString,
    );
  }

  ResolvedTypeInternal readTypeMeta(
    Buffer buffer, {
    required ResolvedTypeInternal Function(int index) sharedTypeAt,
    required void Function(ResolvedTypeInternal resolved) addSharedType,
    required EncodedMetaStringInternal Function() readPackageMetaString,
    required EncodedMetaStringInternal Function() readTypeNameMetaString,
  }) {
    final typeMeta = _typeMetaDecoder.read(
      buffer,
      config: config,
      resolveBuiltinWireType: resolveBuiltinWireType,
      resolveUserById: resolveUserById,
      resolveUserByEncodedName: resolveUserByEncodedName,
      readSharedTypeDef: () => _readSharedTypeDef(
        buffer,
        sharedTypeAt: sharedTypeAt,
        addSharedType: addSharedType,
      ),
      readPackageMetaString: readPackageMetaString,
      readTypeNameMetaString: readTypeNameMetaString,
    );
    return typeMeta.resolvedType;
  }

  void _writeSharedTypeDef(
    Buffer buffer,
    TypeMeta typeMeta, {
    required List<FieldMetadataInternal> sharedTypeDefFields,
    required int? Function(String identity) lookupSharedTypeDefId,
    required int Function(String identity) reserveSharedTypeDefId,
  }) {
    final resolved = typeMeta.resolvedType;
    final identity = resolved.userTypeId != null
        ? 'id:${resolved.userTypeId}'
        : 'name:${resolved.namespace}:${resolved.typeName}';
    final index = lookupSharedTypeDefId(identity);
    if (index != null) {
      buffer.writeVarUint32((index << 1) | 1);
      return;
    }
    final newIndex = reserveSharedTypeDefId(identity);
    buffer.writeVarUint32(newIndex << 1);
    buffer.writeBytes(_encodeTypeDef(resolved, sharedTypeDefFields));
  }

  Uint8List _encodeTypeDef(
    ResolvedTypeInternal resolved,
    List<FieldMetadataInternal> fields,
  ) {
    final metaBuffer = Buffer();
    var classHeader = fields.length;
    metaBuffer.writeByte(0xff);
    if (fields.length >= typeDefSmallFieldCountThreshold) {
      classHeader = typeDefSmallFieldCountThreshold;
      metaBuffer.writeVarUint32Small7(
        fields.length - typeDefSmallFieldCountThreshold,
      );
    }
    if (resolved.isNamed) {
      classHeader |= typeDefRegisterByNameFlag;
      _writeTypeDefName(
        metaBuffer,
        resolved.encodedNamespace!.bytes,
        encoding: packageNameCompactEncodingInternal(
          resolved.encodedNamespace!.encoding,
        ),
      );
      _writeTypeDefName(
        metaBuffer,
        resolved.encodedTypeName!.bytes,
        encoding: typeNameCompactEncodingInternal(
          resolved.encodedTypeName!.encoding,
        ),
      );
    } else {
      metaBuffer.writeUint8(_typeDefTypeId(resolved));
      metaBuffer.writeVarUint32(resolved.userTypeId!);
    }
    metaBuffer.toBytes()[0] = classHeader;
    for (final field in fields) {
      _writeTypeDefField(metaBuffer, field);
    }
    final body = metaBuffer.toBytes();
    final buffer = Buffer();
    buffer.writeInt64(
      typeDefHeaderInternal(body, hasFieldsMeta: fields.isNotEmpty),
    );
    if (body.length >= 0xff) {
      buffer.writeVarUint32(body.length - 0xff);
    }
    buffer.writeBytes(body);
    return buffer.toBytes();
  }

  void _writeTypeDefField(Buffer target, FieldMetadataInternal field) {
    final shape = field.shape;
    final usesTag = field.id != null;
    final encodedName = usesTag ? null : fieldNameMetaString(field.identifier);
    var size = usesTag ? field.id! : encodedName!.bytes.length - 1;
    var header = shape.ref ? 1 : 0;
    if (shape.nullable) {
      header |= 1 << 1;
    }
    header |= ((size >= typeDefBigFieldNameThreshold
            ? typeDefBigFieldNameThreshold
            : size) <<
        2);
    header |= ((usesTag
            ? 3
            : fieldNameCompactEncodingInternal(encodedName!.encoding)) <<
        6);
    target.writeByte(header);
    if (size >= typeDefBigFieldNameThreshold) {
      target.writeVarUint32Small7(size - typeDefBigFieldNameThreshold);
    }
    _writeTypeDefShape(target, shape, writeFlags: false);
    if (!usesTag) {
      target.writeBytes(encodedName!.bytes);
    }
  }

  void _writeTypeDefShape(
    Buffer target,
    TypeShapeInternal shape, {
    required bool writeFlags,
  }) {
    final typeId = _typeDefFieldTypeId(shape);
    if (writeFlags) {
      var encoded = typeId << 2;
      if (shape.nullable) {
        encoded |= 1 << 1;
      }
      if (shape.ref) {
        encoded |= 1;
      }
      target.writeVarUint32Small7(encoded);
    } else {
      target.writeUint8(typeId);
    }
    if (typeId == TypeIds.list || typeId == TypeIds.set) {
      _writeTypeDefShape(target, shape.arguments.single, writeFlags: true);
    } else if (typeId == TypeIds.map) {
      _writeTypeDefShape(target, shape.arguments[0], writeFlags: true);
      _writeTypeDefShape(target, shape.arguments[1], writeFlags: true);
    }
  }

  void _writeTypeDefName(
    Buffer target,
    List<int> bytes, {
    required int encoding,
  }) {
    if (bytes.length >= typeDefBigNameThreshold) {
      target.writeByte((typeDefBigNameThreshold << 2) | encoding);
      target.writeVarUint32Small7(bytes.length - typeDefBigNameThreshold);
    } else {
      target.writeByte((bytes.length << 2) | encoding);
    }
    target.writeBytes(bytes);
  }

  int _typeDefTypeId(ResolvedTypeInternal resolved) {
    switch (resolved.kind) {
      case RegistrationKindInternal.struct:
        return TypeIds.struct;
      case RegistrationKindInternal.enumType:
        return TypeIds.enumById;
      case RegistrationKindInternal.ext:
        return TypeIds.ext;
      case RegistrationKindInternal.union:
        return TypeIds.typedUnion;
      case RegistrationKindInternal.builtin:
        return resolved.typeId;
    }
  }

  int _typeDefFieldTypeId(TypeShapeInternal shape) {
    if (TypeIds.isPrimitive(shape.typeId) ||
        TypeIds.isContainer(shape.typeId) ||
        shape.typeId == TypeIds.string ||
        shape.typeId == TypeIds.binary ||
        shape.typeId == TypeIds.date ||
        shape.typeId == TypeIds.timestamp) {
      return shape.typeId;
    }
    return shape.ref ? TypeIds.unknown : shape.typeId;
  }

  TypeMeta _readSharedTypeDef(
    Buffer buffer, {
    required ResolvedTypeInternal Function(int index) sharedTypeAt,
    required void Function(ResolvedTypeInternal resolved) addSharedType,
  }) {
    final marker = buffer.readVarUint32Small14();
    final isRef = (marker & 1) == 1;
    final index = marker >>> 1;
    if (isRef) {
      return typeMetaForResolved(sharedTypeAt(index));
    }
    final resolved = _readTypeDef(buffer);
    addSharedType(resolved);
    return typeMetaForResolved(resolved);
  }

  ResolvedTypeInternal _readTypeDef(Buffer buffer) {
    final header = buffer.readInt64();
    final metaSizeLowBits = header & 0xff;
    final metaSize = metaSizeLowBits == 0xff
        ? 0xff + buffer.readVarUint32Small14()
        : metaSizeLowBits.toInt();
    final typeDefBytes = Buffer.wrap(buffer.readBytes(metaSize));
    final classHeader = typeDefBytes.readUint8();
    var fieldCount = classHeader & typeDefSmallFieldCountThreshold;
    if (fieldCount == typeDefSmallFieldCountThreshold) {
      fieldCount += typeDefBytes.readVarUint32Small7();
    }
    final byName = (classHeader & typeDefRegisterByNameFlag) != 0;
    final encodedNamespace = byName
        ? _readTypeDefName(typeDefBytes, packageNameEncodingInternal)
        : null;
    final encodedTypeName = byName
        ? _readTypeDefName(typeDefBytes, typeNameEncodingInternal)
        : null;
    int? userTypeId;
    if (!byName) {
      typeDefBytes.readUint8();
      userTypeId = typeDefBytes.readVarUint32();
    }
    final fields = <FieldMetadataInternal>[];
    for (var i = 0; i < fieldCount; i += 1) {
      fields.add(_readTypeDefField(typeDefBytes));
    }
    final resolved = userTypeId != null
        ? resolveUserById(userTypeId)
        : resolveUserByEncodedName(
            encodedNamespace!,
            encodedTypeName!,
          );
    return ResolvedTypeInternal(
      type: resolved.type,
      kind: resolved.kind,
      typeId: resolved.typeId,
      supportsRef: resolved.supportsRef,
      serializer: resolved.serializer,
      structRuntime: resolved.structRuntime,
      userTypeId: resolved.userTypeId,
      namespace: resolved.namespace,
      typeName: resolved.typeName,
      encodedNamespace: resolved.encodedNamespace,
      encodedTypeName: resolved.encodedTypeName,
      structMetadata: resolved.structMetadata,
      remoteStructMetadata: StructMetadataInternal(
        evolving: true,
        fields: fields,
      ),
    );
  }

  EncodedMetaStringInternal _readTypeDefName(
    Buffer source,
    int Function(int compactEncoding) decodeEncoding,
  ) {
    final header = source.readUint8();
    final compactEncoding = header & 0x03;
    var size = header >>> 2;
    if (size == typeDefBigNameThreshold) {
      size += source.readVarUint32Small7();
    }
    return internEncodedMetaString(
      Uint8List.fromList(source.readBytes(size)),
      encoding: decodeEncoding(compactEncoding),
    );
  }

  FieldMetadataInternal _readTypeDefField(Buffer source) {
    final fieldHeader = source.readByte();
    final encoding = (fieldHeader >>> 6) & 0x03;
    final fieldRef = (fieldHeader & 1) == 1;
    final fieldNullable = (fieldHeader & (1 << 1)) != 0;
    var size = (fieldHeader >> 2) & 0x0f;
    if (size == typeDefBigFieldNameThreshold) {
      size += source.readVarUint32Small7();
    }
    size += 1;
    final isTag = encoding == 3;
    final tagId = isTag ? size - 1 : null;
    final shape = _readTypeDefShape(
      source,
      typeId: source.readUint8(),
      nullable: fieldNullable,
      ref: fieldRef,
    );
    final identifier = isTag
        ? tagId.toString()
        : decodeFieldNameInternal(source.readBytes(size), encoding);
    return FieldMetadataInternal(
      name: identifier,
      identifier: identifier,
      id: tagId,
      shape: shape,
    );
  }

  TypeShapeInternal _readTypeDefShape(
    Buffer source, {
    required int typeId,
    required bool nullable,
    required bool ref,
  }) {
    final arguments = <TypeShapeInternal>[];
    if (typeId == TypeIds.list || typeId == TypeIds.set) {
      arguments.add(_readNestedTypeShape(source));
    } else if (typeId == TypeIds.map) {
      arguments.add(_readNestedTypeShape(source));
      arguments.add(_readNestedTypeShape(source));
    }
    return TypeShapeInternal(
      type: Object,
      typeId: typeId,
      nullable: nullable,
      ref: ref,
      dynamic: typeId == TypeIds.unknown ? true : false,
      arguments: arguments,
    );
  }

  TypeShapeInternal _readNestedTypeShape(Buffer source) {
    final encoded = source.readVarUint32Small7();
    return _readTypeDefShape(
      source,
      typeId: encoded >>> 2,
      nullable: ((encoded >> 1) & 1) == 1,
      ref: (encoded & 1) == 1,
    );
  }

  ResolvedTypeInternal resolveBuiltinWireType(int wireTypeId) {
    switch (wireTypeId) {
      case TypeIds.boolType:
        return _builtin(bool, TypeIds.boolType);
      case TypeIds.int8:
        return _builtin(Int8, TypeIds.int8);
      case TypeIds.int16:
        return _builtin(Int16, TypeIds.int16);
      case TypeIds.int32:
        return _builtin(Int32, TypeIds.int32);
      case TypeIds.varInt32:
        return _builtin(Int32, TypeIds.varInt32);
      case TypeIds.int64:
        return _builtin(int, TypeIds.int64);
      case TypeIds.varInt64:
        return _builtin(int, TypeIds.varInt64);
      case TypeIds.taggedInt64:
        return _builtin(int, TypeIds.taggedInt64);
      case TypeIds.uint8:
        return _builtin(UInt8, TypeIds.uint8);
      case TypeIds.uint16:
        return _builtin(UInt16, TypeIds.uint16);
      case TypeIds.uint32:
        return _builtin(UInt32, TypeIds.uint32);
      case TypeIds.varUint32:
        return _builtin(UInt32, TypeIds.varUint32);
      case TypeIds.uint64:
        return _builtin(int, TypeIds.uint64);
      case TypeIds.varUint64:
        return _builtin(int, TypeIds.varUint64);
      case TypeIds.taggedUint64:
        return _builtin(int, TypeIds.taggedUint64);
      case TypeIds.float16:
        return _builtin(Float16, TypeIds.float16);
      case TypeIds.float32:
        return _builtin(Float32, TypeIds.float32);
      case TypeIds.float64:
        return _builtin(double, TypeIds.float64);
      case TypeIds.string:
        return _builtin(String, TypeIds.string);
      case TypeIds.list:
        return _builtin(List, TypeIds.list);
      case TypeIds.set:
        return _builtin(Set, TypeIds.set);
      case TypeIds.map:
        return _builtin(Map, TypeIds.map);
      case TypeIds.binary:
        return _builtin(Uint8List, TypeIds.binary);
      case TypeIds.date:
        return _builtin(LocalDate, TypeIds.date);
      case TypeIds.timestamp:
        return _builtin(Timestamp, TypeIds.timestamp);
      case TypeIds.boolArray:
        return _builtin(List<bool>, TypeIds.boolArray);
      case TypeIds.int8Array:
        return _builtin(Int8List, TypeIds.int8Array);
      case TypeIds.int16Array:
        return _builtin(Int16List, TypeIds.int16Array);
      case TypeIds.int32Array:
        return _builtin(Int32List, TypeIds.int32Array);
      case TypeIds.int64Array:
        return _builtin(Int64List, TypeIds.int64Array);
      case TypeIds.uint8Array:
        return _builtin(Uint8List, TypeIds.uint8Array);
      case TypeIds.uint16Array:
        return _builtin(Uint16List, TypeIds.uint16Array);
      case TypeIds.uint32Array:
        return _builtin(Uint32List, TypeIds.uint32Array);
      case TypeIds.uint64Array:
        return _builtin(Uint64List, TypeIds.uint64Array);
      case TypeIds.float16Array:
        return _builtin(Uint16List, TypeIds.float16Array);
      case TypeIds.float32Array:
        return _builtin(Float32List, TypeIds.float32Array);
      case TypeIds.float64Array:
        return _builtin(Float64List, TypeIds.float64Array);
      default:
        throw StateError('Unsupported builtin wire type id $wireTypeId.');
    }
  }

  ResolvedTypeInternal _builtin(Type type, int typeId) {
    return ResolvedTypeInternal(
      type: type,
      kind: RegistrationKindInternal.builtin,
      typeId: typeId,
      supportsRef: TypeIds.supportsRef(typeId),
      serializer: null,
      structRuntime: null,
      userTypeId: null,
      namespace: null,
      typeName: null,
      encodedNamespace: null,
      encodedTypeName: null,
      structMetadata: null,
      remoteStructMetadata: null,
    );
  }

  RegistrationKindInternal _inferKind(Serializer serializer) {
    if (serializer is EnumSerializer) {
      return RegistrationKindInternal.enumType;
    }
    if (serializer is UnionSerializer) {
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
      return TypeIds.varInt32;
    }
    if (type == int) {
      return TypeIds.varInt64;
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
    if (type == Uint64List) {
      return TypeIds.uint64Array;
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

  void _storeResolved(
    Type type,
    ResolvedTypeInternal resolved, {
    required int? id,
    required String? namespace,
    required String? typeName,
  }) {
    _registeredByType[type] = resolved;
    if (id != null) {
      _registeredById[id] = resolved;
      return;
    }
    _registeredByName[_nameKey(namespace!, typeName!)] = resolved;
    (_registeredByEncodedName[resolved.encodedNamespace!] ??= LinkedHashMap<
        EncodedMetaStringInternal,
        ResolvedTypeInternal>.identity())[resolved.encodedTypeName!] = resolved;
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

  EncodedMetaStringInternal _canonicalMetaString(
    EncodedMetaStringInternal encoded,
  ) {
    return internEncodedMetaString(
      encoded.bytes,
      encoding: encoded.encoding,
    );
  }
}

final class _EncodedMetaStringKey {
  final int encoding;
  final Uint8List bytes;
  final int _hashCode;

  _EncodedMetaStringKey(this.encoding, this.bytes)
      : _hashCode = Object.hash(encoding, Object.hashAll(bytes));

  @override
  int get hashCode => _hashCode;

  @override
  bool operator ==(Object other) {
    if (other is! _EncodedMetaStringKey || other.encoding != encoding) {
      return false;
    }
    if (identical(other.bytes, bytes)) {
      return true;
    }
    if (other.bytes.length != bytes.length) {
      return false;
    }
    for (var index = 0; index < bytes.length; index += 1) {
      if (other.bytes[index] != bytes[index]) {
        return false;
      }
    }
    return true;
  }
}
