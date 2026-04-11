import 'dart:typed_data';

import 'package:meta/meta.dart';
import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/meta/type_meta.dart';
import 'package:fory/src/string_codec.dart';
import 'package:fory/src/util/hash_util.dart';

/// Write-side runtime state for a single Fory operation.
///
/// Generated and manual serializers receive this object from [Serializer.write].
/// Application code normally interacts with [Fory] instead of constructing or
/// preparing contexts directly.
final class WriteContext {
  /// Effective runtime configuration for the active operation.
  final Config config;
  final TypeResolver _typeResolver;
  final RefWriter _refWriter;

  late Buffer _buffer;
  final Map<_MetaStringKey, int> _metaStringIds = <_MetaStringKey, int>{};
  final Map<String, int> _typeDefIds = <String, int>{};
  final List<_CompatibleWriteState?> _compatibleWriteStack =
      <_CompatibleWriteState?>[];
  int _nextMetaStringId = 0;
  int _nextTypeDefId = 0;
  bool _rootTrackRef = false;
  int _depth = 0;

  @internal
  WriteContext(this.config, this._typeResolver, this._refWriter);

  /// Prepares the context to write into [buffer].
  ///
  /// This resets all per-operation caches, meta-string tables, and Ref state.
  void prepare(Buffer buffer, {required bool trackRef}) {
    _buffer = buffer;
    _rootTrackRef = trackRef;
    _nextMetaStringId = 0;
    _nextTypeDefId = 0;
    _metaStringIds.clear();
    _typeDefIds.clear();
    _compatibleWriteStack.clear();
    _refWriter.reset();
    _depth = 0;
  }

  /// The active output buffer for the current operation.
  Buffer get buffer => _buffer;

  /// Whether the current root operation requested root-level Ref tracking.
  bool get rootTrackRef => _rootTrackRef;

  /// Records entry into one more nested write frame.
  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Serialization depth exceeded ${config.maxDepth}.');
    }
  }

  /// Records exit from a nested write frame.
  void decreaseDepth() {
    _depth -= 1;
  }

  /// Writes a boolean value.
  void writeBool(bool value) => _buffer.writeBool(value);

  /// Writes a signed 8-bit integer.
  void writeByte(int value) => _buffer.writeByte(value);

  /// Writes an unsigned 8-bit integer.
  void writeUint8(int value) => _buffer.writeUint8(value);

  /// Writes a signed little-endian 16-bit integer.
  void writeInt16(int value) => _buffer.writeInt16(value);

  /// Writes a signed little-endian 32-bit integer.
  void writeInt32(int value) => _buffer.writeInt32(value);

  /// Writes a signed little-endian 64-bit integer.
  void writeInt64(int value) => _buffer.writeInt64(value);

  /// Writes a half-precision floating-point value.
  void writeFloat16(Float16 value) => _buffer.writeFloat16(value);

  /// Writes a single-precision floating-point value.
  void writeFloat32(double value) => _buffer.writeFloat32(value);

  /// Writes a double-precision floating-point value.
  void writeFloat64(double value) => _buffer.writeFloat64(value);

  /// Writes a zig-zag encoded signed 32-bit varint.
  void writeVarInt32(int value) => _buffer.writeVarInt32(value);

  /// Writes an unsigned 32-bit varint.
  void writeVarUint32(int value) => _buffer.writeVarUint32(value);

  /// Writes a zig-zag encoded signed 64-bit varint.
  void writeVarInt64(int value) => _buffer.writeVarInt64(value);

  /// Writes a tagged signed 64-bit integer.
  void writeTaggedInt64(int value) => _buffer.writeTaggedInt64(value);

  /// Writes an unsigned 64-bit varint.
  void writeVarUint64(int value) => _buffer.writeVarUint64(value);

  /// Writes a tagged unsigned 64-bit integer.
  void writeTaggedUint64(int value) => _buffer.writeTaggedUint64(value);

  /// Writes [value] together with the type metadata needed to read it back.
  ///
  /// Use [trackRef] only when the value is a root graph or other shape that
  /// does not already carry field-level Ref metadata.
  void writeAny(Object? value, {bool trackRef = false}) {
    final resolved = value == null ? null : _typeResolver.resolveValue(value);
    final effectiveTrackRef =
        trackRef && value != null && resolved!.supportsRef;
    if (_refWriter.writeRefOrNull(_buffer, value,
        trackRef: effectiveTrackRef)) {
      return;
    }
    if (value == null) {
      return;
    }
    writeTypeMeta(resolved!);
    _writeResolvedValue(resolved, value, null);
  }

  /// Writes a non-null [value] together with its type metadata.
  void writeValue(Object value) {
    final resolved = _typeResolver.resolveValue(value);
    writeTypeMeta(resolved);
    _writeResolvedValue(resolved, value, null);
  }

  /// Writes a nullable value using the standard Fory nullable framing.
  void writeNullable(Object? value) {
    if (value == null) {
      _buffer.writeByte(RefWriter.nullFlag);
      return;
    }
    _buffer.writeByte(RefWriter.notNullValueFlag);
    writeValue(value);
  }

  /// Writes one annotated struct field described by [metadata].
  ///
  /// This is primarily for generated and manual struct serializers.
  void writeField(Map<String, Object?> metadata, Object? value) {
    final field = FieldMetadataInternal.fromMetadata(metadata);
    final effectiveField = _effectiveFieldMetadata(field);
    final shape = effectiveField.shape;
    if (shape.isDynamic) {
      writeAny(value, trackRef: shape.ref || _rootTrackRef);
      return;
    }
    if (shape.isPrimitive && !shape.nullable) {
      if (value == null) {
        throw StateError('Field ${effectiveField.name} is not nullable.');
      }
      _writePrimitive(shape.typeId, value);
      return;
    }
    final resolved = _typeResolver.resolveShape(shape);
    if (!_usesDeclaredTypeInfo(shape, resolved)) {
      if (shape.ref) {
        writeAny(value, trackRef: true);
        return;
      }
      if (shape.nullable) {
        writeNullable(value);
        return;
      }
      if (value == null) {
        throw StateError('Field ${effectiveField.name} is not nullable.');
      }
      writeValue(value);
      return;
    }
    if (shape.nullable || shape.ref) {
      final handled = _refWriter.writeRefOrNull(
        _buffer,
        value,
        trackRef: shape.ref && TypeIds.supportsRef(shape.typeId),
      );
      if (handled) {
        return;
      }
    }
    if (value == null) {
      throw StateError('Field ${effectiveField.name} is not nullable.');
    }
    _writeResolvedValue(resolved, value, shape);
  }

  @internal

  /// Returns the field order to use when writing a compatible struct.
  ///
  /// Generated struct serializers use this to honor remote field order first
  /// and then append local-only fields.
  List<Map<String, Object?>>? compatibleFieldOrder(
    List<Map<String, Object?>> localFields,
  ) {
    final state =
        _compatibleWriteStack.isEmpty ? null : _compatibleWriteStack.last;
    if (state == null) {
      return null;
    }
    final localByIdentifier = <String, FieldMetadataInternal>{
      for (final metadata in localFields)
        metadata['identifier'] as String:
            FieldMetadataInternal.fromMetadata(metadata),
    };
    final orderedFields = <FieldMetadataInternal>[];
    final usedIdentifiers = <String>{};
    for (final remoteField in state.orderedFields) {
      final localField = localByIdentifier[remoteField.identifier];
      if (localField == null) {
        continue;
      }
      orderedFields.add(_mergeCompatibleWriteField(localField, remoteField));
      usedIdentifiers.add(remoteField.identifier);
    }
    for (final metadata in localFields) {
      final localField = FieldMetadataInternal.fromMetadata(metadata);
      if (usedIdentifiers.add(localField.identifier)) {
        orderedFields.add(localField);
      }
    }
    return orderedFields.map(_fieldMetadataMap).toList(growable: false);
  }

  /// Writes the wire-level type metadata for [resolved].
  ///
  /// This is exposed for generated and low-level integrations that need exact
  /// control over the emitted wire shape.
  void writeTypeMeta(ResolvedTypeInternal resolved) {
    final typeMeta = _typeResolver.typeMetaForResolved(resolved);
    _typeResolver.typeMetaEncoder.write(
      _buffer,
      typeMeta,
      writeSharedTypeDef: _writeSharedTypeDef,
      writePackageMetaString: writePackageMetaString,
      writeTypeNameMetaString: writeTypeNameMetaString,
    );
  }

  /// Writes a package-name meta string using the session-local meta-string
  /// table.
  void writePackageMetaString(String value) {
    _writeMetaStringEncoded(encodePackageMetaStringInternal(value));
  }

  /// Writes a type-name meta string using the session-local meta-string table.
  void writeTypeNameMetaString(String value) {
    _writeMetaStringEncoded(encodeTypeNameMetaStringInternal(value));
  }

  void _writeMetaStringEncoded(EncodedMetaStringInternal encoded) {
    final key = _MetaStringKey(encoded.encoding, encoded.bytes);
    final existing = _metaStringIds[key];
    if (existing != null) {
      _buffer.writeVarUint32Small7(((existing + 1) << 1) | 1);
      return;
    }
    _metaStringIds[key] = _nextMetaStringId++;
    final bytes = encoded.bytes;
    _buffer.writeVarUint32Small7(bytes.length << 1);
    if (bytes.isNotEmpty && bytes.length <= metaStringSmallThreshold) {
      _buffer.writeByte(encoded.encoding);
    } else if (bytes.length > metaStringSmallThreshold) {
      _buffer.writeInt64(
        metaStringHashInternal(bytes, encoding: encoded.encoding),
      );
    }
    _buffer.writeBytes(bytes);
  }

  /// Writes a generic meta string using package-style encoding rules.
  void writeMetaString(String value) {
    _writeMetaStringEncoded(encodePackageMetaStringInternal(value));
  }

  void _writeSharedTypeDef(TypeMeta typeMeta) {
    final resolved = typeMeta.resolvedType;
    final identity = resolved.userTypeId != null
        ? 'id:${resolved.userTypeId}'
        : 'name:${resolved.namespace}:${resolved.typeName}';
    final index = _typeDefIds[identity];
    if (index != null) {
      _buffer.writeVarUint32((index << 1) | 1);
      return;
    }
    final newIndex = _nextTypeDefId++;
    _typeDefIds[identity] = newIndex;
    _buffer.writeVarUint32(newIndex << 1);
    final typeDefBytes = _encodeTypeDef(resolved);
    _buffer.writeBytes(typeDefBytes);
  }

  Uint8List _encodeTypeDef(ResolvedTypeInternal resolved) {
    final metaBuffer = Buffer();
    final fields = (_compatibleTypeDefFields(resolved) ??
            resolved.structMetadata?.fields) ??
        const <FieldMetadataInternal>[];
    var classHeader = fields.length;
    metaBuffer.writeByte(0xff);
    if (fields.length >= typeDefSmallFieldCountThreshold) {
      classHeader = typeDefSmallFieldCountThreshold;
      metaBuffer.writeVarUint32Small7(
          fields.length - typeDefSmallFieldCountThreshold);
    }
    if (resolved.isNamed) {
      classHeader |= typeDefRegisterByNameFlag;
      _writePackageNameTo(metaBuffer, resolved.namespace!);
      _writeTypeNameTo(metaBuffer, resolved.typeName!);
    }
    if (!resolved.isNamed) {
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
      typeDefHeaderInternal(
        body,
        hasFieldsMeta: fields.isNotEmpty,
      ),
    );
    if (body.length >= 0xff) {
      buffer.writeVarUint32(body.length - 0xff);
    }
    buffer.writeBytes(body);
    return buffer.toBytes();
  }

  /// Writes [value] as a stand-alone meta string to [target].
  ///
  /// Unlike [writeMetaString], this does not use the session-local dedup table.
  void writeMetaStringTo(Buffer target, String value) {
    final encoded = encodePackageMetaStringInternal(value);
    final bytes = encoded.bytes;
    target.writeVarUint32Small7(bytes.length << 1);
    if (bytes.isNotEmpty && bytes.length <= metaStringSmallThreshold) {
      target.writeByte(encoded.encoding);
    } else if (bytes.length > metaStringSmallThreshold) {
      target.writeInt64(
        metaStringHashInternal(bytes, encoding: encoded.encoding),
      );
    }
    target.writeBytes(bytes);
  }

  void _writeTypeDefField(Buffer target, FieldMetadataInternal field) {
    final shape = field.shape;
    final usesTag = field.id != null;
    final encodedName =
        usesTag ? null : encodeFieldNameMetaStringInternal(field.identifier);
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

  void _writePackageNameTo(Buffer target, String value) {
    final encoded = encodePackageMetaStringInternal(value);
    _writeTypeDefName(
      target,
      encoded.bytes,
      encoding: packageNameCompactEncodingInternal(encoded.encoding),
    );
  }

  void _writeTypeNameTo(Buffer target, String value) {
    final encoded = encodeTypeNameMetaStringInternal(value);
    _writeTypeDefName(
      target,
      encoded.bytes,
      encoding: typeNameCompactEncodingInternal(encoded.encoding),
    );
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

  bool _usesDeclaredTypeInfo(
    TypeShapeInternal shape,
    ResolvedTypeInternal resolved,
  ) {
    if (shape.isDynamic) {
      return false;
    }
    if (!config.compatible) {
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

  int _typeDefFieldTypeId(TypeShapeInternal shape) {
    if (TypeIds.isPrimitive(shape.typeId) ||
        TypeIds.isContainer(shape.typeId) ||
        shape.typeId == TypeIds.unknown ||
        shape.typeId == TypeIds.string ||
        shape.typeId == TypeIds.binary ||
        shape.typeId == TypeIds.date ||
        shape.typeId == TypeIds.timestamp ||
        shape.typeId == TypeIds.boolArray ||
        shape.typeId == TypeIds.int8Array ||
        shape.typeId == TypeIds.int16Array ||
        shape.typeId == TypeIds.int32Array ||
        shape.typeId == TypeIds.int64Array ||
        shape.typeId == TypeIds.uint8Array ||
        shape.typeId == TypeIds.uint16Array ||
        shape.typeId == TypeIds.uint32Array ||
        shape.typeId == TypeIds.uint64Array ||
        shape.typeId == TypeIds.float16Array ||
        shape.typeId == TypeIds.float32Array ||
        shape.typeId == TypeIds.float64Array ||
        shape.typeId == TypeIds.enumById ||
        shape.typeId == TypeIds.union) {
      return shape.typeId;
    }
    final resolved = _typeResolver.resolveShape(shape);
    switch (resolved.kind) {
      case RegistrationKindInternal.builtin:
        return resolved.typeId;
      case RegistrationKindInternal.enumType:
        return TypeIds.enumById;
      case RegistrationKindInternal.union:
        return TypeIds.union;
      case RegistrationKindInternal.ext:
        return resolved.isNamed ? TypeIds.namedExt : TypeIds.ext;
      case RegistrationKindInternal.struct:
        if (config.compatible && resolved.structMetadata!.evolving) {
          return resolved.isNamed
              ? TypeIds.namedCompatibleStruct
              : TypeIds.compatibleStruct;
        }
        return resolved.isNamed ? TypeIds.namedStruct : TypeIds.struct;
    }
  }

  void _writeResolvedValue(
    ResolvedTypeInternal resolved,
    Object value,
    TypeShapeInternal? declaredShape,
  ) {
    switch (resolved.typeId) {
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
        _writePrimitive(resolved.typeId, value);
        return;
      case TypeIds.string:
        _writeStringPayload(value as String);
        return;
      case TypeIds.binary:
        final bytes = value as Uint8List;
        if (bytes.length > config.maxBinarySize) {
          throw StateError(
              'Binary payload exceeds ${config.maxBinarySize} bytes.');
        }
        _buffer.writeVarUint32(bytes.length);
        _buffer.writeBytes(bytes);
        return;
      case TypeIds.boolArray:
        final values = value as List<bool>;
        _buffer.writeVarUint32(values.length);
        for (final element in values) {
          _buffer.writeBool(element);
        }
        return;
      case TypeIds.int8Array:
        final values = (value as Int8List);
        _buffer.writeVarUint32(values.length);
        _buffer.writeBytes(values);
        return;
      case TypeIds.int16Array:
        _writeFixedArray(value as Int16List, 2);
        return;
      case TypeIds.int32Array:
        _writeFixedArray(value as Int32List, 4);
        return;
      case TypeIds.int64Array:
        _writeFixedArray(value as Int64List, 8);
        return;
      case TypeIds.uint16Array:
        _writeFixedArray(value as Uint16List, 2);
        return;
      case TypeIds.uint32Array:
        _writeFixedArray(value as Uint32List, 4);
        return;
      case TypeIds.uint64Array:
        _writeFixedArray(value as Uint64List, 8);
        return;
      case TypeIds.float32Array:
        _writeFixedArray(value as Float32List, 4);
        return;
      case TypeIds.float64Array:
        _writeFixedArray(value as Float64List, 8);
        return;
      case TypeIds.list:
        _writeList(
          value as List,
          _firstOrNull(declaredShape?.arguments),
          trackRef: declaredShape == null ? _rootTrackRef : declaredShape.ref,
        );
        return;
      case TypeIds.set:
        _writeList(
          (value as Set).toList(growable: false),
          _firstOrNull(declaredShape?.arguments),
          trackRef: declaredShape == null ? _rootTrackRef : declaredShape.ref,
        );
        return;
      case TypeIds.map:
        _writeMap(
          value as Map,
          _elementAtOrNull(declaredShape?.arguments, 0),
          _elementAtOrNull(declaredShape?.arguments, 1),
          trackRef: declaredShape == null ? _rootTrackRef : declaredShape.ref,
        );
        return;
      case TypeIds.date:
        _buffer.writeInt32((value as LocalDate).toEpochDay());
        return;
      case TypeIds.timestamp:
        final timestamp = value as Timestamp;
        _buffer.writeInt64(timestamp.seconds);
        _buffer.writeUint32(timestamp.nanoseconds);
        return;
      default:
        final serializer = resolved.serializer;
        if (serializer == null) {
          throw StateError('No serializer available for ${resolved.type}.');
        }
        if (resolved.supportsRef) {
          _refWriter.reference(value);
        }
        if (resolved.kind == RegistrationKindInternal.struct &&
            !config.compatible &&
            config.checkStructVersion &&
            resolved.structMetadata != null) {
          _buffer.writeUint32(schemaHashInternal(resolved.structMetadata!));
        }
        final compatibleFields = _compatibleWriteMetadata(resolved);
        _compatibleWriteStack.add(
          compatibleFields == null
              ? null
              : _CompatibleWriteState.fromFields(compatibleFields.fields),
        );
        try {
          serializer.write(this, value);
        } finally {
          _compatibleWriteStack.removeLast();
        }
    }
  }

  void _writePrimitive(int typeId, Object value) {
    switch (typeId) {
      case TypeIds.boolType:
        _buffer.writeBool(value as bool);
        return;
      case TypeIds.int8:
        _buffer.writeByte((value as Int8).value);
        return;
      case TypeIds.int16:
        _buffer.writeInt16((value as Int16).value);
        return;
      case TypeIds.int32:
        _buffer.writeInt32((value as Int32).value);
        return;
      case TypeIds.varInt32:
        _buffer.writeVarInt32(value is Int32 ? value.value : value as int);
        return;
      case TypeIds.int64:
        _buffer.writeInt64(value as int);
        return;
      case TypeIds.varInt64:
        _buffer.writeVarInt64(value as int);
        return;
      case TypeIds.taggedInt64:
        _buffer.writeTaggedInt64(value as int);
        return;
      case TypeIds.uint8:
        _buffer.writeUint8(value is UInt8 ? value.value : value as int);
        return;
      case TypeIds.uint16:
        _buffer.writeUint16(value is UInt16 ? value.value : value as int);
        return;
      case TypeIds.uint32:
        _buffer.writeUint32(value is UInt32 ? value.value : value as int);
        return;
      case TypeIds.varUint32:
        _buffer.writeVarUint32(value is UInt32 ? value.value : value as int);
        return;
      case TypeIds.uint64:
        _buffer.writeUint64(value as int);
        return;
      case TypeIds.varUint64:
        _buffer.writeVarUint64(value as int);
        return;
      case TypeIds.taggedUint64:
        _buffer.writeTaggedUint64(value as int);
        return;
      case TypeIds.float16:
        _buffer.writeFloat16(value as Float16);
        return;
      case TypeIds.float32:
        _buffer.writeFloat32((value as Float32).value);
        return;
      case TypeIds.float64:
        _buffer.writeFloat64(value as double);
        return;
      default:
        throw StateError('Unsupported primitive type id $typeId.');
    }
  }

  void _writeStringPayload(String value) {
    final encoded = encodeStringInternal(value);
    _buffer.writeVarUint36Small((encoded.bytes.length << 2) | encoded.encoding);
    _buffer.writeBytes(encoded.bytes);
  }

  void _writeFixedArray(TypedData values, int elementSize) {
    _buffer.writeVarUint32(values.lengthInBytes);
    _buffer.writeBytes(
      values.buffer.asUint8List(values.offsetInBytes, values.lengthInBytes),
    );
  }

  void _writeList(
    List values,
    TypeShapeInternal? elementShape, {
    required bool trackRef,
  }) {
    if (values.length > config.maxCollectionSize) {
      throw StateError(
        'Collection size ${values.length} exceeds ${config.maxCollectionSize}.',
      );
    }
    _buffer.writeVarUint32(values.length);
    if (values.isEmpty) {
      return;
    }
    final hasNull = values.any((value) => value == null);
    final declaredType = elementShape != null &&
        !elementShape.isDynamic &&
        elementShape.typeId != TypeIds.unknown;
    final nonNull =
        values.where((value) => value != null).toList(growable: false);
    final sameResolved = nonNull.isEmpty
        ? null
        : _typeResolver.resolveValue(nonNull.first as Object);
    final sameType = sameResolved == null
        ? true
        : nonNull.every(
            (value) => _sameResolvedType(
              _typeResolver.resolveValue(value as Object),
              sameResolved,
            ),
          );
    final elementTrackRef =
        (elementShape?.ref ?? false) || (elementShape == null && trackRef);
    var header = 0;
    if (elementTrackRef) {
      header |= 0x01;
    }
    if (hasNull) {
      header |= 0x02;
    }
    if (declaredType) {
      header |= 0x04;
    }
    if (sameType) {
      header |= 0x08;
    }
    _buffer.writeUint8(header);
    if (!declaredType && sameType && sameResolved != null) {
      writeTypeMeta(sameResolved);
    }
    for (final value in values) {
      if (declaredType) {
        Map<String, Object?> shapeMetadata(
          TypeShapeInternal value, {
          required bool isRoot,
        }) {
          return <String, Object?>{
            'type': value.type,
            'typeId': value.typeId,
            'nullable': isRoot ? hasNull : value.nullable,
            'ref': isRoot ? elementTrackRef : value.ref,
            'dynamic': value.dynamic,
            'arguments': value.arguments
                .map(
                  (argument) => shapeMetadata(argument, isRoot: false),
                )
                .toList(growable: false),
          };
        }

        if (elementTrackRef || hasNull) {
          writeField(
            <String, Object?>{
              'name': 'item',
              'identifier': 'item',
              'id': null,
              'shape': shapeMetadata(elementShape, isRoot: true),
            },
            value,
          );
        } else {
          final resolved = _typeResolver.resolveShape(elementShape);
          _writeResolvedValue(resolved, value as Object, elementShape);
        }
        continue;
      }
      if (sameType && sameResolved != null) {
        if (value == null) {
          _buffer.writeByte(RefWriter.nullFlag);
        } else if (elementTrackRef) {
          final handled = _refWriter.writeRefOrNull(
            _buffer,
            value,
            trackRef: sameResolved.supportsRef,
          );
          if (!handled) {
            _writeResolvedValue(sameResolved, value as Object, null);
          }
        } else if (hasNull) {
          _buffer.writeByte(RefWriter.notNullValueFlag);
          _writeResolvedValue(sameResolved, value as Object, null);
        } else {
          _writeResolvedValue(sameResolved, value as Object, null);
        }
        continue;
      }
      if (elementTrackRef) {
        writeAny(value, trackRef: true);
      } else if (hasNull) {
        writeNullable(value);
      } else {
        writeValue(value as Object);
      }
    }
  }

  void _writeMap(
    Map values,
    TypeShapeInternal? keyShape,
    TypeShapeInternal? valueShape, {
    required bool trackRef,
  }) {
    if (values.length > config.maxCollectionSize) {
      throw StateError(
          'Map size ${values.length} exceeds ${config.maxCollectionSize}.');
    }
    _buffer.writeVarUint32(values.length);
    for (final entry in values.entries) {
      final key = entry.key;
      final value = entry.value;
      final keyDeclared = keyShape != null && !keyShape.isDynamic;
      final valueDeclared = valueShape != null && !valueShape.isDynamic;
      final keyRequestedRef =
          (keyShape?.ref ?? false) || (keyShape == null && trackRef);
      final valueRequestedRef =
          (valueShape?.ref ?? false) || (valueShape == null && trackRef);
      final keyTrackRef = keyRequestedRef &&
          (keyDeclared
              ? TypeIds.supportsRef(keyShape.typeId)
              : (key == null ||
                  _typeResolver.resolveValue(key as Object).supportsRef));
      final valueTrackRef = valueRequestedRef &&
          (valueDeclared
              ? TypeIds.supportsRef(valueShape.typeId)
              : (value == null ||
                  _typeResolver.resolveValue(value as Object).supportsRef));
      if (key == null || value == null) {
        _writeNullMapChunk(
          key,
          value,
          keyShape,
          valueShape,
          keyDeclared: keyDeclared,
          valueDeclared: valueDeclared,
          keyTrackRef: keyTrackRef,
          valueTrackRef: valueTrackRef,
        );
        continue;
      }
      var header = 0;
      if (keyTrackRef) {
        header |= 0x01;
      }
      if (keyDeclared) {
        header |= 0x04;
      }
      if (valueTrackRef) {
        header |= 0x08;
      }
      if (valueDeclared) {
        header |= 0x20;
      }
      _buffer.writeUint8(header);
      _buffer.writeUint8(1);
      ResolvedTypeInternal? keyResolved;
      if (!keyDeclared) {
        keyResolved = _typeResolver.resolveValue(key as Object);
        writeTypeMeta(keyResolved);
      }
      ResolvedTypeInternal? valueResolved;
      if (!valueDeclared) {
        valueResolved = _typeResolver.resolveValue(value as Object);
        writeTypeMeta(valueResolved);
      }
      if (keyDeclared) {
        writeField(
            _mapFieldMetadataForWrite(keyShape, trackRef: keyTrackRef), key);
      } else {
        _writeResolvedMapValue(key, keyResolved!, trackRef: keyTrackRef);
      }
      if (valueDeclared) {
        writeField(
          _mapFieldMetadataForWrite(valueShape, trackRef: valueTrackRef),
          value,
        );
      } else {
        _writeResolvedMapValue(value, valueResolved!, trackRef: valueTrackRef);
      }
    }
  }

  void _writeNullMapChunk(
    Object? key,
    Object? value,
    TypeShapeInternal? keyShape,
    TypeShapeInternal? valueShape, {
    required bool keyDeclared,
    required bool valueDeclared,
    required bool keyTrackRef,
    required bool valueTrackRef,
  }) {
    var header = 0;
    if (key == null) {
      header |= 0x02;
    } else if (keyDeclared) {
      header |= 0x04;
    }
    if (keyTrackRef) {
      header |= 0x01;
    }
    if (value == null) {
      header |= 0x10;
    } else if (valueDeclared) {
      header |= 0x20;
    }
    if (valueTrackRef) {
      header |= 0x08;
    }
    _buffer.writeUint8(header);
    if (key != null) {
      if (keyDeclared) {
        final declaredKeyShape = keyShape!;
        writeField(
          _mapFieldMetadataForWrite(declaredKeyShape, trackRef: keyTrackRef),
          key,
        );
      } else if (keyTrackRef) {
        writeAny(key, trackRef: true);
      } else {
        writeValue(key);
      }
    }
    if (value != null) {
      if (valueDeclared) {
        final declaredValueShape = valueShape!;
        writeField(
          _mapFieldMetadataForWrite(
            declaredValueShape,
            trackRef: valueTrackRef,
          ),
          value,
        );
      } else if (valueTrackRef) {
        writeAny(value, trackRef: true);
      } else {
        writeValue(value);
      }
    }
  }

  void _writeResolvedMapValue(
    Object value,
    ResolvedTypeInternal resolved, {
    required bool trackRef,
  }) {
    if (trackRef) {
      final handled = _refWriter.writeRefOrNull(
        _buffer,
        value,
        trackRef: resolved.supportsRef,
      );
      if (handled) {
        return;
      }
    }
    _writeResolvedValue(resolved, value, null);
  }

  bool _sameResolvedType(
    ResolvedTypeInternal left,
    ResolvedTypeInternal right,
  ) {
    if (left.kind != right.kind || left.typeId != right.typeId) {
      return false;
    }
    if (left.userTypeId != null || right.userTypeId != null) {
      return left.userTypeId == right.userTypeId;
    }
    if (left.namespace != null || right.namespace != null) {
      return left.namespace == right.namespace &&
          left.typeName == right.typeName;
    }
    return true;
  }

  StructMetadataInternal? _compatibleWriteMetadata(
      ResolvedTypeInternal resolved) {
    if (!config.compatible ||
        resolved.kind != RegistrationKindInternal.struct ||
        resolved.structMetadata == null ||
        !resolved.structMetadata!.evolving) {
      return null;
    }
    return _typeResolver.remoteStructMetadataFor(resolved);
  }

  List<FieldMetadataInternal>? _compatibleTypeDefFields(
    ResolvedTypeInternal resolved,
  ) {
    final remoteMetadata = _compatibleWriteMetadata(resolved);
    final localFields = resolved.structMetadata?.fields;
    if (remoteMetadata == null || localFields == null) {
      return null;
    }
    final localByIdentifier = <String, FieldMetadataInternal>{
      for (final field in localFields) field.identifier: field,
    };
    final orderedFields = <FieldMetadataInternal>[];
    final usedIdentifiers = <String>{};
    for (final remoteField in remoteMetadata.fields) {
      final localField = localByIdentifier[remoteField.identifier];
      if (localField == null) {
        continue;
      }
      orderedFields.add(_mergeCompatibleWriteField(localField, remoteField));
      usedIdentifiers.add(remoteField.identifier);
    }
    for (final localField in localFields) {
      if (usedIdentifiers.add(localField.identifier)) {
        orderedFields.add(localField);
      }
    }
    return orderedFields;
  }

  FieldMetadataInternal _effectiveFieldMetadata(FieldMetadataInternal field) {
    final state =
        _compatibleWriteStack.isEmpty ? null : _compatibleWriteStack.last;
    final remoteField = state?.byIdentifier[field.identifier];
    if (remoteField == null) {
      return field;
    }
    return _mergeCompatibleWriteField(field, remoteField);
  }

  Map<String, Object?> _mapFieldMetadataForWrite(
    TypeShapeInternal shape, {
    required bool trackRef,
  }) {
    Map<String, Object?> shapeMetadata(
      TypeShapeInternal value, {
      required bool isRoot,
    }) {
      return <String, Object?>{
        'type': value.type,
        'typeId': value.typeId,
        'nullable': isRoot ? false : value.nullable,
        'ref': isRoot ? trackRef : value.ref,
        'dynamic': value.dynamic,
        'arguments': value.arguments
            .map(
              (argument) => shapeMetadata(argument, isRoot: false),
            )
            .toList(growable: false),
      };
    }

    return <String, Object?>{
      'name': 'entry',
      'identifier': 'entry',
      'id': null,
      'shape': shapeMetadata(shape, isRoot: true),
    };
  }
}

FieldMetadataInternal _mergeCompatibleWriteField(
  FieldMetadataInternal localField,
  FieldMetadataInternal remoteField,
) {
  TypeShapeInternal mergeShape(
    TypeShapeInternal local,
    TypeShapeInternal remote,
  ) {
    final mergedArguments = <TypeShapeInternal>[];
    final argumentCount = remote.arguments.length;
    for (var index = 0; index < argumentCount; index += 1) {
      final remoteArgument = remote.arguments[index];
      final localArgument = index < local.arguments.length
          ? local.arguments[index]
          : remoteArgument;
      mergedArguments.add(mergeShape(localArgument, remoteArgument));
    }
    return TypeShapeInternal(
      type: local.type,
      typeId: remote.typeId,
      nullable: remote.nullable,
      ref: remote.ref,
      dynamic: local.dynamic ?? remote.dynamic,
      arguments: mergedArguments,
    );
  }

  return FieldMetadataInternal(
    name: localField.name,
    identifier: localField.identifier,
    id: localField.id,
    shape: mergeShape(localField.shape, remoteField.shape),
  );
}

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(
        List<TypeShapeInternal>? values, int index) =>
    values == null || values.length <= index ? null : values[index];

final class _CompatibleWriteState {
  final List<FieldMetadataInternal> orderedFields;
  final Map<String, FieldMetadataInternal> byIdentifier;

  _CompatibleWriteState._(this.orderedFields, this.byIdentifier);

  factory _CompatibleWriteState.fromFields(List<FieldMetadataInternal> fields) {
    return _CompatibleWriteState._(
      fields,
      <String, FieldMetadataInternal>{
        for (final field in fields) field.identifier: field,
      },
    );
  }
}

Map<String, Object?> _fieldMetadataMap(FieldMetadataInternal field) =>
    <String, Object?>{
      'name': field.name,
      'identifier': field.identifier,
      'id': field.id,
      'shape': _shapeMetadata(field.shape),
    };

Map<String, Object?> _shapeMetadata(TypeShapeInternal shape) =>
    <String, Object?>{
      'type': shape.type,
      'typeId': shape.typeId,
      'nullable': shape.nullable,
      'ref': shape.ref,
      'dynamic': shape.dynamic,
      'arguments': shape.arguments.map(_shapeMetadata).toList(growable: false),
    };

final class _MetaStringKey {
  final int encoding;
  final Uint8List bytes;

  const _MetaStringKey(this.encoding, this.bytes);

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) {
      return true;
    }
    return other is _MetaStringKey &&
        encoding == other.encoding &&
        _bytesEqual(bytes, other.bytes);
  }

  @override
  int get hashCode => Object.hash(encoding, Object.hashAll(bytes));
}

bool _bytesEqual(Uint8List left, Uint8List right) {
  if (identical(left, right)) {
    return true;
  }
  if (left.length != right.length) {
    return false;
  }
  for (var index = 0; index < left.length; index += 1) {
    if (left[index] != right[index]) {
      return false;
    }
  }
  return true;
}
