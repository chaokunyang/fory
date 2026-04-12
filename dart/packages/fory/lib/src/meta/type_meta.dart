import 'dart:collection';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/resolver/type_resolver.dart';

/// Internal representation of the wire-level type metadata for one value.
final class TypeMeta {
  final TypeInfoInternal resolvedType;
  final int wireTypeId;
  final bool sharedTypeDef;

  const TypeMeta({
    required this.resolvedType,
    required this.wireTypeId,
    required this.sharedTypeDef,
  });

  bool get writesUserTypeId =>
      wireTypeId == TypeIds.enumById ||
      wireTypeId == TypeIds.struct ||
      wireTypeId == TypeIds.ext ||
      wireTypeId == TypeIds.typedUnion;

  bool get writesNamedType =>
      !sharedTypeDef &&
      (wireTypeId == TypeIds.namedEnum ||
          wireTypeId == TypeIds.namedStruct ||
          wireTypeId == TypeIds.namedExt ||
          wireTypeId == TypeIds.namedUnion);
}

final class TypeHeader {
  final int value;

  const TypeHeader(this.value);

  int readMetaSize(Buffer buffer) {
    final lowBits = value & 0xff;
    if (lowBits == 0xff) {
      return 0xff + buffer.readVarUint32Small14();
    }
    return lowBits;
  }

  void skipRemaining(Buffer buffer) {
    buffer.skip(readMetaSize(buffer));
  }
}

final class ParsedTypeMetaCache {
  static const int maxEntries = 8192;

  final LinkedHashMap<int, TypeInfoInternal> _entries =
      LinkedHashMap<int, TypeInfoInternal>();
  int? _lastHeader;
  TypeInfoInternal? _lastResolved;

  TypeInfoInternal? lookup(TypeHeader header) {
    if (_lastHeader == header.value) {
      return _lastResolved;
    }
    final resolved = _entries[header.value];
    if (resolved != null) {
      _lastHeader = header.value;
      _lastResolved = resolved;
    }
    return resolved;
  }

  void remember(TypeHeader header, TypeInfoInternal resolved) {
    if (!_entries.containsKey(header.value) && _entries.length >= maxEntries) {
      _entries.remove(_entries.keys.first);
    }
    _entries[header.value] = resolved;
    _lastHeader = header.value;
    _lastResolved = resolved;
  }
}

/// Encodes type metadata into the xlang wire format.
final class TypeMetaEncoder {
  const TypeMetaEncoder();

  TypeMeta typeMetaFor(Config config, TypeInfoInternal resolvedType) {
    final wireTypeId = _wireTypeIdFor(config, resolvedType);
    final sharedTypeDef = wireTypeId == TypeIds.compatibleStruct ||
        wireTypeId == TypeIds.namedCompatibleStruct ||
        (config.compatible &&
            (wireTypeId == TypeIds.namedEnum ||
                wireTypeId == TypeIds.namedStruct ||
                wireTypeId == TypeIds.namedExt ||
                wireTypeId == TypeIds.namedUnion));
    return TypeMeta(
      resolvedType: resolvedType,
      wireTypeId: wireTypeId,
      sharedTypeDef: sharedTypeDef,
    );
  }

  void write(
    Buffer buffer,
    TypeMeta typeMeta, {
    required void Function(TypeMeta typeMeta) writeSharedTypeDef,
    required void Function(EncodedMetaStringInternal value)
        writePackageMetaString,
    required void Function(EncodedMetaStringInternal value)
        writeTypeNameMetaString,
  }) {
    buffer.writeVarUint32Small7(typeMeta.wireTypeId);
    if (typeMeta.writesUserTypeId) {
      buffer.writeVarUint32(typeMeta.resolvedType.userTypeId!);
      return;
    }
    if (typeMeta.sharedTypeDef) {
      writeSharedTypeDef(typeMeta);
      return;
    }
    if (typeMeta.writesNamedType) {
      writePackageMetaString(typeMeta.resolvedType.encodedNamespace!);
      writeTypeNameMetaString(typeMeta.resolvedType.encodedTypeName!);
    }
  }

  int _wireTypeIdFor(Config config, TypeInfoInternal resolvedType) {
    switch (resolvedType.kind) {
      case RegistrationKindInternal.builtin:
        return resolvedType.typeId;
      case RegistrationKindInternal.enumType:
        return resolvedType.isNamed ? TypeIds.namedEnum : TypeIds.enumById;
      case RegistrationKindInternal.ext:
        return resolvedType.isNamed ? TypeIds.namedExt : TypeIds.ext;
      case RegistrationKindInternal.union:
        if (resolvedType.userTypeId != null) {
          return TypeIds.typedUnion;
        }
        return resolvedType.isNamed ? TypeIds.namedUnion : TypeIds.union;
      case RegistrationKindInternal.struct:
        final compatible =
            config.compatible && resolvedType.structMetadata!.evolving;
        if (compatible) {
          return resolvedType.isNamed
              ? TypeIds.namedCompatibleStruct
              : TypeIds.compatibleStruct;
        }
        return resolvedType.isNamed ? TypeIds.namedStruct : TypeIds.struct;
    }
  }
}

/// Decodes type metadata from the xlang wire format.
final class TypeMetaDecoder {
  const TypeMetaDecoder();

  TypeMeta read(
    Buffer buffer, {
    required Config config,
    required TypeInfoInternal Function(int wireTypeId) resolveBuiltinWireType,
    required TypeInfoInternal Function(int id) resolveUserById,
    required TypeInfoInternal Function(
      int wireTypeId,
      EncodedMetaStringInternal namespace,
      EncodedMetaStringInternal typeName,
    ) resolveUserByEncodedNameCached,
    required TypeInfoInternal? Function(int wireTypeId) expectedNamedType,
    required TypeMeta Function() readSharedTypeDef,
    required EncodedMetaStringInternal Function([
      EncodedMetaStringInternal? expected,
    ]) readPackageMetaString,
    required EncodedMetaStringInternal Function([
      EncodedMetaStringInternal? expected,
    ]) readTypeNameMetaString,
  }) {
    final wireTypeId = buffer.readVarUint32Small7();
    if (_isBuiltinWireType(wireTypeId)) {
      return TypeMeta(
        resolvedType: resolveBuiltinWireType(wireTypeId),
        wireTypeId: wireTypeId,
        sharedTypeDef: false,
      );
    }
    switch (wireTypeId) {
      case TypeIds.enumById:
      case TypeIds.struct:
      case TypeIds.ext:
      case TypeIds.typedUnion:
        return TypeMeta(
          resolvedType: resolveUserById(buffer.readVarUint32()),
          wireTypeId: wireTypeId,
          sharedTypeDef: false,
        );
      case TypeIds.namedEnum:
      case TypeIds.namedStruct:
      case TypeIds.namedExt:
      case TypeIds.namedUnion:
        if (config.compatible) {
          return readSharedTypeDef();
        }
        final expected = expectedNamedType(wireTypeId);
        final namespace = readPackageMetaString(expected?.encodedNamespace);
        final typeName = readTypeNameMetaString(expected?.encodedTypeName);
        if (expected != null &&
            identical(namespace, expected.encodedNamespace) &&
            identical(typeName, expected.encodedTypeName)) {
          return TypeMeta(
            resolvedType: expected,
            wireTypeId: wireTypeId,
            sharedTypeDef: false,
          );
        }
        return TypeMeta(
          resolvedType: resolveUserByEncodedNameCached(
            wireTypeId,
            namespace,
            typeName,
          ),
          wireTypeId: wireTypeId,
          sharedTypeDef: false,
        );
      case TypeIds.compatibleStruct:
      case TypeIds.namedCompatibleStruct:
        return readSharedTypeDef();
      default:
        throw StateError('Unsupported wire type id $wireTypeId.');
    }
  }

  bool _isBuiltinWireType(int wireTypeId) =>
      wireTypeId == TypeIds.boolType ||
      wireTypeId == TypeIds.int8 ||
      wireTypeId == TypeIds.int16 ||
      wireTypeId == TypeIds.int32 ||
      wireTypeId == TypeIds.varInt32 ||
      wireTypeId == TypeIds.int64 ||
      wireTypeId == TypeIds.varInt64 ||
      wireTypeId == TypeIds.taggedInt64 ||
      wireTypeId == TypeIds.uint8 ||
      wireTypeId == TypeIds.uint16 ||
      wireTypeId == TypeIds.uint32 ||
      wireTypeId == TypeIds.varUint32 ||
      wireTypeId == TypeIds.uint64 ||
      wireTypeId == TypeIds.varUint64 ||
      wireTypeId == TypeIds.taggedUint64 ||
      wireTypeId == TypeIds.float16 ||
      wireTypeId == TypeIds.float32 ||
      wireTypeId == TypeIds.float64 ||
      wireTypeId == TypeIds.string ||
      wireTypeId == TypeIds.list ||
      wireTypeId == TypeIds.set ||
      wireTypeId == TypeIds.map ||
      wireTypeId == TypeIds.binary ||
      wireTypeId == TypeIds.date ||
      wireTypeId == TypeIds.timestamp ||
      wireTypeId == TypeIds.boolArray ||
      wireTypeId == TypeIds.int8Array ||
      wireTypeId == TypeIds.int16Array ||
      wireTypeId == TypeIds.int32Array ||
      wireTypeId == TypeIds.int64Array ||
      wireTypeId == TypeIds.uint8Array ||
      wireTypeId == TypeIds.uint16Array ||
      wireTypeId == TypeIds.uint32Array ||
      wireTypeId == TypeIds.uint64Array ||
      wireTypeId == TypeIds.float16Array ||
      wireTypeId == TypeIds.float32Array ||
      wireTypeId == TypeIds.float64Array;
}
