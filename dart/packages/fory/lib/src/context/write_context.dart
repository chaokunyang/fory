import 'dart:convert';
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
import 'package:fory/src/util/hash_util.dart';

final class WriteContext {
  static const int _typeDefHasFieldsMeta = 1 << 8;

  final Config config;
  final TypeResolver _typeResolver;
  final RefWriter _refWriter;

  late Buffer _buffer;
  final Map<String, int> _metaStringIds = <String, int>{};
  final Map<String, int> _typeDefIds = <String, int>{};
  int _nextMetaStringId = 0;
  int _nextTypeDefId = 0;
  bool _rootTrackRef = false;
  int _depth = 0;

  @internal
  WriteContext(this.config, this._typeResolver, this._refWriter);

  void prepare(Buffer buffer, {required bool trackRef}) {
    _buffer = buffer;
    _rootTrackRef = trackRef;
    _nextMetaStringId = 0;
    _nextTypeDefId = 0;
    _metaStringIds.clear();
    _typeDefIds.clear();
    _refWriter.reset();
    _depth = 0;
  }

  Buffer get buffer => _buffer;

  bool get rootTrackRef => _rootTrackRef;

  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Serialization depth exceeded ${config.maxDepth}.');
    }
  }

  void decreaseDepth() {
    _depth -= 1;
  }

  void writeBool(bool value) => _buffer.writeBool(value);

  void writeByte(int value) => _buffer.writeByte(value);

  void writeUint8(int value) => _buffer.writeUint8(value);

  void writeInt16(int value) => _buffer.writeInt16(value);

  void writeInt32(int value) => _buffer.writeInt32(value);

  void writeInt64(int value) => _buffer.writeInt64(value);

  void writeFloat16(Float16 value) => _buffer.writeFloat16(value);

  void writeFloat32(double value) => _buffer.writeFloat32(value);

  void writeFloat64(double value) => _buffer.writeFloat64(value);

  void writeVarInt32(int value) => _buffer.writeVarInt32(value);

  void writeVarUint32(int value) => _buffer.writeVarUint32(value);

  void writeVarInt64(int value) => _buffer.writeVarInt64(value);

  void writeVarUint64(int value) => _buffer.writeVarUint64(value);

  void writeAny(Object? value, {bool trackRef = false}) {
    final resolved = value == null ? null : _typeResolver.resolveValue(value);
    final effectiveTrackRef =
        trackRef && value != null && !resolved!.isBasicValue;
    if (_refWriter.writeRefOrNull(_buffer, value, trackRef: effectiveTrackRef)) {
      return;
    }
    if (value == null) {
      return;
    }
    writeTypeMeta(resolved!);
    _writeResolvedValue(resolved, value, null);
  }

  void writeValue(Object value) {
    final resolved = _typeResolver.resolveValue(value);
    writeTypeMeta(resolved);
    _writeResolvedValue(resolved, value, null);
  }

  void writeNullable(Object? value) {
    if (value == null) {
      _buffer.writeByte(RefWriter.nullFlag);
      return;
    }
    _buffer.writeByte(RefWriter.notNullValueFlag);
    writeValue(value);
  }

  void writeField(Map<String, Object?> metadata, Object? value) {
    final field = FieldMetadataInternal.fromMetadata(metadata);
    final shape = field.shape;
    if (shape.isDynamic) {
      writeAny(value, trackRef: shape.ref || _rootTrackRef);
      return;
    }
    if (shape.isPrimitive && !shape.nullable) {
      if (value == null) {
        throw StateError('Field ${field.name} is not nullable.');
      }
      _writePrimitive(shape.typeId, value);
      return;
    }
    if (shape.nullable || shape.ref) {
      final handled = _refWriter.writeRefOrNull(
        _buffer,
        value,
        trackRef: shape.ref && !TypeIds.isBasicValue(shape.typeId),
      );
      if (handled) {
        return;
      }
    }
    if (value == null) {
      throw StateError('Field ${field.name} is not nullable.');
    }
    final resolved = _typeResolver.resolveShape(shape);
    _writeResolvedValue(resolved, value, shape);
  }

  void writeTypeMeta(ResolvedTypeInternal resolved) {
    final wireTypeId = resolved.wireTypeId(config);
    _buffer.writeVarUint32Small7(wireTypeId);
    switch (wireTypeId) {
      case TypeIds.enumById:
      case TypeIds.struct:
      case TypeIds.ext:
      case TypeIds.typedUnion:
        _buffer.writeVarUint32(resolved.userTypeId!);
        return;
      case TypeIds.namedEnum:
      case TypeIds.namedStruct:
      case TypeIds.namedExt:
      case TypeIds.namedUnion:
        writeMetaString(resolved.namespace!);
        writeMetaString(resolved.typeName!);
        return;
      case TypeIds.compatibleStruct:
      case TypeIds.namedCompatibleStruct:
        _writeSharedTypeDef(resolved);
        return;
      default:
        return;
    }
  }

  void writeMetaString(String value) {
    final existing = _metaStringIds[value];
    if (existing != null) {
      _buffer.writeVarUint32Small7(((existing + 1) << 1) | 1);
      return;
    }
    _metaStringIds[value] = _nextMetaStringId++;
    final bytes = utf8.encode(value);
    _buffer.writeVarUint32Small7(bytes.length << 1);
    if (bytes.isNotEmpty && bytes.length <= 16) {
      _buffer.writeByte(0);
    } else if (bytes.length > 16) {
      _buffer.writeInt64(stableHash64Internal(bytes));
    }
    _buffer.writeBytes(bytes);
  }

  void _writeSharedTypeDef(ResolvedTypeInternal resolved) {
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
    final fields = resolved.structMetadata!.fields;
    var metaHeader = fields.length & 0x1f;
    if (resolved.isNamed) {
      metaHeader |= 1 << 5;
    }
    metaBuffer.writeByte(metaHeader);
    if (resolved.isNamed) {
      writeMetaStringTo(metaBuffer, resolved.namespace!);
      writeMetaStringTo(metaBuffer, resolved.typeName!);
    } else {
      metaBuffer.writeVarUint32(resolved.userTypeId!);
    }
    for (final field in fields) {
      _writeTypeDefField(metaBuffer, field);
    }
    final body = metaBuffer.toBytes();
    final headerValue =
        (body.length & 0xff) |
        _typeDefHasFieldsMeta |
        (stableHash64Internal(body) << 14);
    final buffer = Buffer();
    buffer.writeUint64(headerValue);
    if (body.length >= 0xff) {
      buffer.writeVarUint32(body.length - 0xff);
    }
    buffer.writeBytes(body);
    return buffer.toBytes();
  }

  void writeMetaStringTo(Buffer target, String value) {
    final bytes = utf8.encode(value);
    target.writeVarUint32Small7(bytes.length << 1);
    if (bytes.isNotEmpty && bytes.length <= 16) {
      target.writeByte(0);
    } else if (bytes.length > 16) {
      target.writeInt64(stableHash64Internal(bytes));
    }
    target.writeBytes(bytes);
  }

  void _writeTypeDefField(Buffer target, FieldMetadataInternal field) {
    final shape = field.shape;
    final usesTag = field.id != null;
    var size = usesTag ? field.id! : utf8.encode(field.identifier).length - 1;
    var header = shape.ref ? 1 : 0;
    if (shape.nullable) {
      header |= 1 << 1;
    }
    header |= ((size > 15 ? 15 : size) << 2);
    header |= ((usesTag ? 3 : 0) << 6);
    target.writeByte(header);
    if (size > 15) {
      target.writeVarUint32(size - 15);
    }
    target.writeVarUint32(shape.typeId);
    if (shape.typeId == TypeIds.list || shape.typeId == TypeIds.set) {
      _writeNestedFieldType(target, shape.arguments.single);
    } else if (shape.typeId == TypeIds.map) {
      _writeNestedFieldType(target, shape.arguments[0]);
      _writeNestedFieldType(target, shape.arguments[1]);
    }
    if (!usesTag) {
      writeMetaStringTo(target, field.identifier);
    }
  }

  void _writeNestedFieldType(Buffer target, TypeShapeInternal shape) {
    var encoded = shape.typeId << 2;
    if (shape.nullable) {
      encoded |= 1 << 1;
    }
    if (shape.ref) {
      encoded |= 1;
    }
    target.writeVarUint32(encoded);
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
      case TypeIds.int64:
      case TypeIds.uint8:
      case TypeIds.uint16:
      case TypeIds.uint32:
      case TypeIds.float16:
      case TypeIds.float32:
      case TypeIds.float64:
        _writePrimitive(resolved.typeId, value);
        return;
      case TypeIds.string:
        final bytes = utf8.encode(value as String);
        _buffer.writeVarUint36Small((bytes.length << 2));
        _buffer.writeBytes(bytes);
        return;
      case TypeIds.binary:
        final bytes = value as Uint8List;
        if (bytes.length > config.maxBinarySize) {
          throw StateError('Binary payload exceeds ${config.maxBinarySize} bytes.');
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
        if (!resolved.isBasicValue) {
          _refWriter.reference(value);
        }
        if (resolved.kind == RegistrationKindInternal.struct &&
            !config.compatible &&
            config.checkStructVersion &&
            resolved.structMetadata != null) {
          _buffer.writeUint32(schemaHashInternal(resolved.structMetadata!));
        }
        serializer.write(this, value);
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
      case TypeIds.int64:
        _buffer.writeInt64(value as int);
        return;
      case TypeIds.uint8:
        _buffer.writeUint8((value as UInt8).value);
        return;
      case TypeIds.uint16:
        _buffer.writeUint16((value as UInt16).value);
        return;
      case TypeIds.uint32:
        _buffer.writeUint32((value as UInt32).value);
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

  void _writeFixedArray(TypedData values, int elementSize) {
    _buffer.writeVarUint32(values.lengthInBytes ~/ elementSize);
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
    final hasNull = values.any((value) => value == null);
    final declaredType = elementShape != null &&
        !elementShape.isDynamic &&
        elementShape.typeId != TypeIds.unknown;
    final nonNull = values.where((value) => value != null).toList(growable: false);
    final sameResolved = nonNull.isEmpty
        ? null
        : _typeResolver.resolveValue(nonNull.first as Object);
    final sameType = sameResolved == null
        ? true
        : nonNull.every(
            (value) =>
                _typeResolver.resolveValue(value as Object).wireTypeId(config) ==
                sameResolved.wireTypeId(config),
          );
    final elementTrackRef = (elementShape?.ref ?? false) || (elementShape == null && trackRef);
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
        if (elementTrackRef || hasNull || elementShape.nullable) {
          writeField(
            <String, Object?>{
              'name': 'item',
              'identifier': 'item',
              'id': null,
              'shape': <String, Object?>{
                'type': elementShape.type,
                'typeId': elementShape.typeId,
                'nullable': elementShape.nullable || hasNull,
                'ref': elementTrackRef,
                'dynamic': elementShape.dynamic,
                'arguments': elementShape.arguments
                    .map(
                      (argument) => <String, Object?>{
                        'type': argument.type,
                        'typeId': argument.typeId,
                        'nullable': argument.nullable,
                        'ref': argument.ref,
                        'dynamic': argument.dynamic,
                        'arguments': const <Object?>[],
                      },
                    )
                    .toList(growable: false),
              },
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
            trackRef: !sameResolved.isBasicValue,
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
      throw StateError('Map size ${values.length} exceeds ${config.maxCollectionSize}.');
    }
    _buffer.writeVarUint32(values.length);
    final keyTrackRef = (keyShape?.ref ?? false) || (keyShape == null && trackRef);
    final valueTrackRef = (valueShape?.ref ?? false) || (valueShape == null && trackRef);
    for (final entry in values.entries) {
      var header = 0;
      final keyHasNull = entry.key == null;
      final valueHasNull = entry.value == null;
      if (keyTrackRef) {
        header |= 0x01;
      }
      if (keyHasNull) {
        header |= 0x02;
      }
      if (keyShape != null && !keyShape.isDynamic) {
        header |= 0x04;
      }
      if (valueTrackRef) {
        header |= 0x08;
      }
      if (valueHasNull) {
        header |= 0x10;
      }
      if (valueShape != null && !valueShape.isDynamic) {
        header |= 0x20;
      }
      _buffer.writeUint8(header);
      if (!keyHasNull && !valueHasNull) {
        _buffer.writeUint8(1);
      }
      _writeMapEntryValue(entry.key, keyShape, keyTrackRef, keyHasNull);
      _writeMapEntryValue(entry.value, valueShape, valueTrackRef, valueHasNull);
    }
  }

  void _writeMapEntryValue(
    Object? value,
    TypeShapeInternal? shape,
    bool trackRef,
    bool hasNull,
  ) {
    if (shape != null && !shape.isDynamic) {
      writeField(
        <String, Object?>{
          'name': 'entry',
          'identifier': 'entry',
          'id': null,
          'shape': <String, Object?>{
            'type': shape.type,
            'typeId': shape.typeId,
            'nullable': hasNull || shape.nullable,
            'ref': trackRef,
            'dynamic': shape.dynamic,
            'arguments': shape.arguments
                .map(
                  (argument) => <String, Object?>{
                    'type': argument.type,
                    'typeId': argument.typeId,
                    'nullable': argument.nullable,
                    'ref': argument.ref,
                    'dynamic': argument.dynamic,
                    'arguments': const <Object?>[],
                  },
                )
                .toList(growable: false),
          },
        },
        value,
      );
      return;
    }
    if (trackRef) {
      writeAny(value, trackRef: true);
    } else if (hasNull) {
      writeNullable(value);
    } else {
      writeValue(value as Object);
    }
  }
}

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(List<TypeShapeInternal>? values, int index) =>
    values == null || values.length <= index ? null : values[index];
