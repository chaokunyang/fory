import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/resolver/type_resolver.dart';

/// Internal representation of the wire-level type metadata for one value.
final class TypeMeta {
  final ResolvedTypeInternal resolvedType;
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

/// Encodes type metadata into the xlang wire format.
final class TypeMetaEncoder {
  const TypeMetaEncoder();

  TypeMeta typeMetaFor(Config config, ResolvedTypeInternal resolvedType) {
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

  int _wireTypeIdFor(Config config, ResolvedTypeInternal resolvedType) {
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
    required ResolvedTypeInternal Function(int wireTypeId)
        resolveBuiltinWireType,
    required ResolvedTypeInternal Function(int id) resolveUserById,
    required ResolvedTypeInternal Function(
      EncodedMetaStringInternal namespace,
      EncodedMetaStringInternal typeName,
    ) resolveUserByEncodedName,
    required TypeMeta Function() readSharedTypeDef,
    required EncodedMetaStringInternal Function() readPackageMetaString,
    required EncodedMetaStringInternal Function() readTypeNameMetaString,
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
        return TypeMeta(
          resolvedType: resolveUserByEncodedName(
              readPackageMetaString(), readTypeNameMetaString()),
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
