import 'dart:collection';
import 'dart:typed_data';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/meta_string_codec.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/meta/type_meta.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/serializer/struct_codec.dart';
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

  TypeShapeInternal withRootOverrides({
    required bool nullable,
    required bool ref,
  }) {
    if (this.nullable == nullable && this.ref == ref) {
      return this;
    }
    return TypeShapeInternal(
      type: type,
      typeId: typeId,
      nullable: nullable,
      ref: ref,
      dynamic: dynamic,
      arguments: arguments,
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
}

final class StructMetadataInternal {
  final bool evolving;
  final List<FieldMetadataInternal> fields;

  const StructMetadataInternal({required this.evolving, required this.fields});
}

final class SharedTypeDefInternal {
  final int header;
  final Uint8List encoded;

  const SharedTypeDefInternal({
    required this.header,
    required this.encoded,
  });
}

final class ResolvedTypeInternal {
  final Type type;
  final RegistrationKindInternal kind;
  final int typeId;
  final bool supportsRef;
  final Serializer<Object?>? serializer;
  final StructCodec? structCodec;
  final int? userTypeId;
  final String? namespace;
  final String? typeName;
  final EncodedMetaStringInternal? encodedNamespace;
  final EncodedMetaStringInternal? encodedTypeName;
  final StructMetadataInternal? structMetadata;
  final StructMetadataInternal? remoteStructMetadata;
  final SharedTypeDefInternal? sharedTypeDef;

  const ResolvedTypeInternal({
    required this.type,
    required this.kind,
    required this.typeId,
    required this.supportsRef,
    required this.serializer,
    required this.structCodec,
    required this.userTypeId,
    required this.namespace,
    required this.typeName,
    required this.encodedNamespace,
    required this.encodedTypeName,
    required this.structMetadata,
    required this.remoteStructMetadata,
    required this.sharedTypeDef,
  });

  bool get isNamed =>
      userTypeId == null && namespace != null && typeName != null;

  bool get isCompatibleStruct =>
      kind == RegistrationKindInternal.struct && structMetadata!.evolving;

  bool get isBasicValue => TypeIds.isBasicValue(typeId);
}

final class DeclaredValueBindingInternal {
  final FieldMetadataInternal metadata;
  final TypeShapeInternal shape;
  final ResolvedTypeInternal? resolved;
  final bool usesDeclaredType;

  const DeclaredValueBindingInternal({
    required this.metadata,
    required this.shape,
    required this.resolved,
    required this.usesDeclaredType,
  });
}

bool usesDeclaredTypeInfo(
  bool compatible,
  TypeShapeInternal shape,
  ResolvedTypeInternal resolved,
) {
  if (shape.isDynamic) {
    return false;
  }
  if (!compatible) {
    return true;
  }
  switch (resolved.kind) {
    case RegistrationKindInternal.builtin:
    case RegistrationKindInternal.enumType:
    case RegistrationKindInternal.union:
      return true;
    case RegistrationKindInternal.struct:
    case RegistrationKindInternal.ext:
      return false;
  }
}

final class TypeResolver {
  final Config config;
  final TypeMetaEncoder _typeMetaEncoder = const TypeMetaEncoder();
  final TypeMetaDecoder _typeMetaDecoder = const TypeMetaDecoder();
  final ParsedTypeMetaCache _parsedTypeMetaCache = ParsedTypeMetaCache();
  final List<ResolvedTypeInternal?> _lastNamedTypeByWireType =
      List<ResolvedTypeInternal?>.filled(64, null);
  final List<ResolvedTypeInternal?> _builtinByTypeId =
      List<ResolvedTypeInternal?>.filled(64, null);
  final List<_NamedTypeReadCacheEntry?> _namedTypeLookupCache =
      List<_NamedTypeReadCacheEntry?>.filled(128, null);
  final Map<Type, ResolvedTypeInternal> _runtimeTypeValueCache =
      <Type, ResolvedTypeInternal>{};
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
  final Map<FieldMetadataInternal, DeclaredValueBindingInternal>
      _declaredFieldBindingCache = LinkedHashMap<FieldMetadataInternal,
          DeclaredValueBindingInternal>.identity();
  final Map<_DeclaredShapeBindingCacheKey, DeclaredValueBindingInternal>
      _declaredShapeBindingCache =
      <_DeclaredShapeBindingCacheKey, DeclaredValueBindingInternal>{};
  final Map<Type, _GeneratedBindingInternal> _generatedByType =
      <Type, _GeneratedBindingInternal>{};

  TypeResolver(this.config);

  void bindGeneratedEnum(
    Type type,
    Serializer<Object?> Function() serializerFactory,
  ) {
    _generatedByType[type] = _GeneratedBindingInternal(
      kind: RegistrationKindInternal.enumType,
      serializerFactory: serializerFactory,
    );
  }

  void bindGeneratedStruct(
    Type type,
    Serializer<Object?> Function() serializerFactory, {
    required bool evolving,
    required List<FieldMetadataInternal> fields,
    GeneratedStructCompatibleFactory<Object>? compatibleFactory,
    List<GeneratedStructCompatibleFieldReader<Object>>? compatibleReadersBySlot,
  }) {
    _generatedByType[type] = _GeneratedBindingInternal(
      kind: RegistrationKindInternal.struct,
      serializerFactory: serializerFactory,
      evolving: evolving,
      fields: fields,
      compatibleFactory: compatibleFactory,
      compatibleReadersBySlot: compatibleReadersBySlot,
    );
  }

  void registerGenerated(
    Type type, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    final binding = _generatedByType[type];
    if (binding == null) {
      throw StateError(
        'Type $type has no generated registration binding. Call the generated registration helper for this library first.',
      );
    }
    _registerResolvedSerializer(
      type,
      binding.serializerFactory(),
      binding.kind,
      evolving: binding.evolving,
      fields: binding.fields,
      compatibleFactory: binding.compatibleFactory,
      compatibleReadersBySlot: binding.compatibleReadersBySlot,
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  void registerSerializer(
    Type type,
    Serializer serializer, {
    int? id,
    String? namespace,
    String? typeName,
  }) {
    final registrationKind = _inferKind(serializer);
    _registerResolvedSerializer(
      type,
      serializer as Serializer<Object?>,
      registrationKind,
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
  }

  void _registerResolvedSerializer(
    Type type,
    Serializer<Object?> payloadSerializer,
    RegistrationKindInternal registrationKind, {
    bool evolving = true,
    List<FieldMetadataInternal> fields = const <FieldMetadataInternal>[],
    GeneratedStructCompatibleFactory<Object>? compatibleFactory,
    List<GeneratedStructCompatibleFieldReader<Object>>? compatibleReadersBySlot,
    int? id,
    String? namespace,
    String? typeName,
  }) {
    _validateRegistrationMode(id: id, namespace: namespace, typeName: typeName);
    final encodedNamespace =
        namespace == null ? null : packageMetaString(namespace);
    final encodedTypeName =
        typeName == null ? null : typeNameMetaString(typeName);
    final structMetadata = registrationKind == RegistrationKindInternal.struct
        ? StructMetadataInternal(
            evolving: evolving,
            fields: List<FieldMetadataInternal>.unmodifiable(fields),
          )
        : null;
    final sharedTypeDef = _buildSharedTypeDef(
      kind: registrationKind,
      userTypeId: id,
      encodedNamespace: encodedNamespace,
      encodedTypeName: encodedTypeName,
      fields: structMetadata?.fields ?? const <FieldMetadataInternal>[],
    );
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: registrationKind,
      typeId: _defaultTypeIdForType(type),
      supportsRef: payloadSerializer.supportsRef,
      serializer: payloadSerializer,
      structCodec: structMetadata == null
          ? null
          : StructCodec(
              payloadSerializer,
              structMetadata,
              this,
              compatibleFactory: compatibleFactory,
              compatibleReadersBySlot: compatibleReadersBySlot,
            ),
      userTypeId: id,
      namespace: namespace,
      typeName: typeName,
      encodedNamespace: encodedNamespace,
      encodedTypeName: encodedTypeName,
      structMetadata: structMetadata,
      remoteStructMetadata: null,
      sharedTypeDef: sharedTypeDef,
    );
    _parsedTypeMetaCache.remember(TypeHeader(sharedTypeDef.header), resolved);
    _storeResolved(type, resolved,
        id: id, namespace: namespace, typeName: typeName);
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
    final runtimeType = value.runtimeType;
    final cached = _runtimeTypeValueCache[runtimeType];
    if (cached != null) {
      return cached;
    }
    final resolved = _resolveValueSlow(value, runtimeType);
    _runtimeTypeValueCache[runtimeType] = resolved;
    return resolved;
  }

  ResolvedTypeInternal _resolveValueSlow(Object value, Type runtimeType) {
    final registered = _registeredByType[runtimeType];
    if (registered != null) {
      return registered;
    }
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
    throw StateError(
      'Type $runtimeType is not registered. Call the generated registration helper or register a serializer explicitly.',
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

  ResolvedTypeInternal resolvedRegisteredType(Type type) {
    final resolved = _registeredByType[type];
    if (resolved == null) {
      throw StateError('Type $type is not registered.');
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

  ResolvedTypeInternal resolveUserByEncodedNameCached(
    int wireTypeId,
    EncodedMetaStringInternal namespace,
    EncodedMetaStringInternal typeName,
  ) {
    final slot = _namedTypeLookupCacheIndex(wireTypeId, namespace, typeName) &
        (_namedTypeLookupCache.length - 1);
    final cached = _namedTypeLookupCache[slot];
    if (cached != null &&
        cached.wireTypeId == wireTypeId &&
        identical(cached.namespace, namespace) &&
        identical(cached.typeName, typeName)) {
      return cached.resolved;
    }
    final resolved = resolveUserByEncodedName(namespace, typeName);
    _namedTypeLookupCache[slot] = _NamedTypeReadCacheEntry(
      wireTypeId,
      namespace,
      typeName,
      resolved,
    );
    if (wireTypeId < _lastNamedTypeByWireType.length) {
      _lastNamedTypeByWireType[wireTypeId] = resolved;
    }
    return resolved;
  }

  DeclaredValueBindingInternal declaredFieldBinding(
    FieldMetadataInternal field,
  ) {
    final cached = _declaredFieldBindingCache[field];
    if (cached != null) {
      return cached;
    }
    final binding = createDeclaredFieldBinding(field);
    _declaredFieldBindingCache[field] = binding;
    return binding;
  }

  DeclaredValueBindingInternal createDeclaredFieldBinding(
    FieldMetadataInternal field,
  ) {
    final shape = field.shape;
    if (shape.isDynamic || (shape.isPrimitive && !shape.nullable)) {
      return DeclaredValueBindingInternal(
        metadata: field,
        shape: shape,
        resolved: null,
        usesDeclaredType: false,
      );
    }
    final resolved = resolveShape(shape);
    return DeclaredValueBindingInternal(
      metadata: field,
      shape: shape,
      resolved: resolved,
      usesDeclaredType: usesDeclaredTypeInfo(
        config.compatible,
        shape,
        resolved,
      ),
    );
  }

  DeclaredValueBindingInternal declaredValueBindingForShape(
    TypeShapeInternal shape, {
    required String identifier,
    bool nullable = false,
    bool ref = false,
  }) {
    final key = _DeclaredShapeBindingCacheKey(
      shape,
      identifier,
      nullable,
      ref,
    );
    final cached = _declaredShapeBindingCache[key];
    if (cached != null) {
      return cached;
    }
    final binding = createDeclaredFieldBinding(
      FieldMetadataInternal(
        name: identifier,
        identifier: identifier,
        id: null,
        shape: shape.withRootOverrides(
          nullable: nullable,
          ref: ref,
        ),
      ),
    );
    _declaredShapeBindingCache[key] = binding;
    return binding;
  }

  TypeMeta typeMetaForResolved(ResolvedTypeInternal resolved) {
    return _typeMetaEncoder.typeMetaFor(config, resolved);
  }

  SharedTypeDefInternal sharedTypeDefForResolved(
    ResolvedTypeInternal resolved, {
    List<FieldMetadataInternal>? fields,
  }) {
    final resolvedFields = resolved.structMetadata?.fields;
    if (fields == null || identical(fields, resolvedFields)) {
      return resolved.sharedTypeDef!;
    }
    return _buildSharedTypeDef(
      kind: resolved.kind,
      userTypeId: resolved.userTypeId,
      encodedNamespace: resolved.encodedNamespace,
      encodedTypeName: resolved.encodedTypeName,
      fields: fields,
    );
  }

  void writeTypeMeta(
    Buffer buffer,
    ResolvedTypeInternal resolved, {
    required SharedTypeDefInternal? sharedTypeDef,
    required LinkedHashMap<SharedTypeDefInternal, int> sharedTypeDefIds,
    required MetaStringWriteSink metaStringWriter,
  }) {
    _typeMetaEncoder.write(
      buffer,
      typeMetaForResolved(resolved),
      writeSharedTypeDef: (typeMeta) => _writeSharedTypeDef(
        buffer,
        sharedTypeDef ?? typeMeta.resolvedType.sharedTypeDef!,
        sharedTypeDefIds: sharedTypeDefIds,
      ),
      writePackageMetaString: (value) => metaStringWriter.writeMetaString(
        buffer,
        value,
      ),
      writeTypeNameMetaString: (value) => metaStringWriter.writeMetaString(
        buffer,
        value,
      ),
    );
  }

  ResolvedTypeInternal readTypeMeta(
    Buffer buffer, {
    ResolvedTypeInternal? expectedNamedType,
    required List<ResolvedTypeInternal> sharedTypes,
    required MetaStringReadSource metaStringReader,
  }) {
    final typeMeta = _typeMetaDecoder.read(
      buffer,
      config: config,
      resolveBuiltinWireType: resolveBuiltinWireType,
      resolveUserById: resolveUserById,
      resolveUserByEncodedNameCached: resolveUserByEncodedNameCached,
      expectedNamedType: (wireTypeId) {
        final expected = expectedNamedType;
        if (expected != null && _matchesNamedWireType(expected, wireTypeId)) {
          return expected;
        }
        return wireTypeId < _lastNamedTypeByWireType.length
            ? _lastNamedTypeByWireType[wireTypeId]
            : null;
      },
      readSharedTypeDef: () => _readSharedTypeDef(
        buffer,
        sharedTypes: sharedTypes,
      ),
      readPackageMetaString: ([expected]) =>
          metaStringReader.readMetaString(buffer, expected),
      readTypeNameMetaString: ([expected]) =>
          metaStringReader.readMetaString(buffer, expected),
    );
    if (typeMeta.writesNamedType) {
      _rememberNamedType(typeMeta.wireTypeId, typeMeta.resolvedType);
    }
    return typeMeta.resolvedType;
  }

  void _writeSharedTypeDef(
    Buffer buffer,
    SharedTypeDefInternal sharedTypeDef, {
    required LinkedHashMap<SharedTypeDefInternal, int> sharedTypeDefIds,
  }) {
    final index = sharedTypeDefIds[sharedTypeDef];
    if (index != null) {
      buffer.writeVarUint32((index << 1) | 1);
      return;
    }
    final newIndex = sharedTypeDefIds.length;
    sharedTypeDefIds[sharedTypeDef] = newIndex;
    buffer.writeVarUint32(newIndex << 1);
    buffer.writeBytes(sharedTypeDef.encoded);
  }

  SharedTypeDefInternal _buildSharedTypeDef({
    required RegistrationKindInternal kind,
    required int? userTypeId,
    required EncodedMetaStringInternal? encodedNamespace,
    required EncodedMetaStringInternal? encodedTypeName,
    required List<FieldMetadataInternal> fields,
  }) {
    final encoded = _encodeTypeDef(
      kind: kind,
      userTypeId: userTypeId,
      encodedNamespace: encodedNamespace,
      encodedTypeName: encodedTypeName,
      fields: fields,
    );
    final header = Buffer.wrap(encoded).readInt64();
    return SharedTypeDefInternal(header: header, encoded: encoded);
  }

  Uint8List _encodeTypeDef({
    required RegistrationKindInternal kind,
    required int? userTypeId,
    required EncodedMetaStringInternal? encodedNamespace,
    required EncodedMetaStringInternal? encodedTypeName,
    required List<FieldMetadataInternal> fields,
  }) {
    final metaBuffer = Buffer();
    var classHeader = fields.length;
    metaBuffer.writeByte(0xff);
    if (fields.length >= typeDefSmallFieldCountThreshold) {
      classHeader = typeDefSmallFieldCountThreshold;
      metaBuffer.writeVarUint32Small7(
        fields.length - typeDefSmallFieldCountThreshold,
      );
    }
    if (userTypeId == null &&
        encodedNamespace != null &&
        encodedTypeName != null) {
      classHeader |= typeDefRegisterByNameFlag;
      _writeTypeDefName(
        metaBuffer,
        encodedNamespace.bytes,
        encoding: packageNameCompactEncodingInternal(
          encodedNamespace.encoding,
        ),
      );
      _writeTypeDefName(
        metaBuffer,
        encodedTypeName.bytes,
        encoding: typeNameCompactEncodingInternal(
          encodedTypeName.encoding,
        ),
      );
    } else {
      metaBuffer.writeUint8(_typeDefTypeId(kind));
      metaBuffer.writeVarUint32(userTypeId!);
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

  int _typeDefTypeId(RegistrationKindInternal kind) {
    switch (kind) {
      case RegistrationKindInternal.struct:
        return TypeIds.struct;
      case RegistrationKindInternal.enumType:
        return TypeIds.enumById;
      case RegistrationKindInternal.ext:
        return TypeIds.ext;
      case RegistrationKindInternal.union:
        return TypeIds.typedUnion;
      case RegistrationKindInternal.builtin:
        throw StateError(
            'Built-in types do not write shared TypeDef metadata.');
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
    required List<ResolvedTypeInternal> sharedTypes,
  }) {
    final marker = buffer.readVarUint32Small14();
    final isRef = (marker & 1) == 1;
    final index = marker >>> 1;
    if (isRef) {
      return typeMetaForResolved(sharedTypes[index]);
    }
    final header = TypeHeader(buffer.readInt64());
    final cached = _parsedTypeMetaCache.lookup(header);
    if (cached != null) {
      header.skipRemaining(buffer);
      sharedTypes.add(cached);
      return typeMetaForResolved(cached);
    }
    final resolved = _readTypeDefWithHeader(buffer, header);
    _parsedTypeMetaCache.remember(header, resolved);
    sharedTypes.add(resolved);
    return typeMetaForResolved(resolved);
  }

  ResolvedTypeInternal _readTypeDefWithHeader(
      Buffer buffer, TypeHeader header) {
    final metaSize = header.readMetaSize(buffer);
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
    if (resolved.kind != RegistrationKindInternal.struct) {
      return resolved;
    }
    final remoteMetadata = StructMetadataInternal(
      evolving: true,
      fields: fields,
    );
    final localMetadata = resolved.structMetadata;
    if (localMetadata != null &&
        _sameStructMetadata(localMetadata, remoteMetadata)) {
      return resolved;
    }
    return ResolvedTypeInternal(
      type: resolved.type,
      kind: resolved.kind,
      typeId: resolved.typeId,
      supportsRef: resolved.supportsRef,
      serializer: resolved.serializer,
      structCodec: resolved.structCodec,
      userTypeId: resolved.userTypeId,
      namespace: resolved.namespace,
      typeName: resolved.typeName,
      encodedNamespace: resolved.encodedNamespace,
      encodedTypeName: resolved.encodedTypeName,
      structMetadata: resolved.structMetadata,
      remoteStructMetadata: remoteMetadata,
      sharedTypeDef: resolved.sharedTypeDef,
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
    final cached = _builtinByTypeId[typeId];
    if (cached != null) {
      return cached;
    }
    final resolved = ResolvedTypeInternal(
      type: type,
      kind: RegistrationKindInternal.builtin,
      typeId: typeId,
      supportsRef: TypeIds.supportsRef(typeId),
      serializer: null,
      structCodec: null,
      userTypeId: null,
      namespace: null,
      typeName: null,
      encodedNamespace: null,
      encodedTypeName: null,
      structMetadata: null,
      remoteStructMetadata: null,
      sharedTypeDef: null,
    );
    _builtinByTypeId[typeId] = resolved;
    return resolved;
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

  void _rememberNamedType(int wireTypeId, ResolvedTypeInternal resolved) {
    if (wireTypeId < _lastNamedTypeByWireType.length) {
      _lastNamedTypeByWireType[wireTypeId] = resolved;
    }
    final namespace = resolved.encodedNamespace;
    final typeName = resolved.encodedTypeName;
    if (namespace == null || typeName == null) {
      return;
    }
    final slot = _namedTypeLookupCacheIndex(wireTypeId, namespace, typeName) &
        (_namedTypeLookupCache.length - 1);
    _namedTypeLookupCache[slot] = _NamedTypeReadCacheEntry(
      wireTypeId,
      namespace,
      typeName,
      resolved,
    );
  }

  int _namedTypeLookupCacheIndex(
    int wireTypeId,
    EncodedMetaStringInternal namespace,
    EncodedMetaStringInternal typeName,
  ) {
    return Object.hash(wireTypeId, namespace.hash, typeName.hash);
  }

  bool _sameStructMetadata(
    StructMetadataInternal left,
    StructMetadataInternal right,
  ) {
    if (left.fields.length != right.fields.length) {
      return false;
    }
    for (var index = 0; index < left.fields.length; index += 1) {
      final leftField = left.fields[index];
      final rightField = right.fields[index];
      if (leftField.identifier != rightField.identifier ||
          leftField.id != rightField.id ||
          !_sameTypeShape(leftField.shape, rightField.shape)) {
        return false;
      }
    }
    return true;
  }

  bool _sameTypeShape(TypeShapeInternal left, TypeShapeInternal right) {
    if (left.typeId != right.typeId ||
        left.nullable != right.nullable ||
        left.ref != right.ref ||
        left.dynamic != right.dynamic ||
        left.arguments.length != right.arguments.length) {
      return false;
    }
    for (var index = 0; index < left.arguments.length; index += 1) {
      if (!_sameTypeShape(left.arguments[index], right.arguments[index])) {
        return false;
      }
    }
    return true;
  }

  bool _matchesNamedWireType(ResolvedTypeInternal resolved, int wireTypeId) {
    if (!resolved.isNamed) {
      return false;
    }
    switch (resolved.kind) {
      case RegistrationKindInternal.enumType:
        return wireTypeId == TypeIds.namedEnum;
      case RegistrationKindInternal.struct:
        return wireTypeId == TypeIds.namedStruct;
      case RegistrationKindInternal.ext:
        return wireTypeId == TypeIds.namedExt;
      case RegistrationKindInternal.union:
        return wireTypeId == TypeIds.namedUnion;
      case RegistrationKindInternal.builtin:
        return false;
    }
  }
}

final class _GeneratedBindingInternal {
  final RegistrationKindInternal kind;
  final Serializer<Object?> Function() serializerFactory;
  final bool evolving;
  final List<FieldMetadataInternal> fields;
  final GeneratedStructCompatibleFactory<Object>? compatibleFactory;
  final List<GeneratedStructCompatibleFieldReader<Object>>?
      compatibleReadersBySlot;

  const _GeneratedBindingInternal({
    required this.kind,
    required this.serializerFactory,
    this.evolving = true,
    this.fields = const <FieldMetadataInternal>[],
    this.compatibleFactory,
    this.compatibleReadersBySlot,
  });
}

final class _NamedTypeReadCacheEntry {
  final int wireTypeId;
  final EncodedMetaStringInternal namespace;
  final EncodedMetaStringInternal typeName;
  final ResolvedTypeInternal resolved;

  const _NamedTypeReadCacheEntry(
    this.wireTypeId,
    this.namespace,
    this.typeName,
    this.resolved,
  );
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

final class _DeclaredShapeBindingCacheKey {
  final TypeShapeInternal shape;
  final String identifier;
  final bool nullable;
  final bool ref;

  const _DeclaredShapeBindingCacheKey(
    this.shape,
    this.identifier,
    this.nullable,
    this.ref,
  );

  @override
  bool operator ==(Object other) {
    return other is _DeclaredShapeBindingCacheKey &&
        identical(shape, other.shape) &&
        identifier == other.identifier &&
        nullable == other.nullable &&
        ref == other.ref;
  }

  @override
  int get hashCode => Object.hash(
        identityHashCode(shape),
        identifier,
        nullable,
        ref,
      );
}
