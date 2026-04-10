import 'dart:convert';
import 'dart:typed_data';

import 'package:meta/meta.dart';
import 'package:fory/src/buffer.dart';
import 'package:fory/src/config.dart';
import 'package:fory/src/context/ref_reader.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';
import 'package:fory/src/util/hash_util.dart';

final class ReadContext {
  final Config config;
  final TypeResolver _typeResolver;
  final RefReader _refReader;

  late Buffer _buffer;
  final List<String> _metaStrings = <String>[];
  final List<ResolvedTypeInternal> _sharedTypes = <ResolvedTypeInternal>[];
  final List<Map<String, Object?>> _compatibleFieldStack = <Map<String, Object?>>[];
  int _depth = 0;

  @internal
  ReadContext(this.config, this._typeResolver, this._refReader);

  void prepare(Buffer buffer) {
    _buffer = buffer;
    _metaStrings.clear();
    _sharedTypes.clear();
    _compatibleFieldStack.clear();
    _refReader.reset();
    _depth = 0;
  }

  Buffer get buffer => _buffer;

  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Deserialization depth exceeded ${config.maxDepth}.');
    }
  }

  void decreaseDepth() {
    _depth -= 1;
  }

  bool readBool() => _buffer.readBool();

  int readByte() => _buffer.readByte();

  int readUint8() => _buffer.readUint8();

  int readInt16() => _buffer.readInt16();

  int readInt32() => _buffer.readInt32();

  int readInt64() => _buffer.readInt64();

  Float16 readFloat16() => _buffer.readFloat16();

  double readFloat32() => _buffer.readFloat32();

  double readFloat64() => _buffer.readFloat64();

  int readVarInt32() => _buffer.readVarInt32();

  int readVarUint32() => _buffer.readVarUint32();

  int readVarInt64() => _buffer.readVarInt64();

  int readVarUint64() => _buffer.readVarUint64();

  void reference(Object? value) {
    _refReader.reference(value);
  }

  Object? readAny() {
    final flag = _refReader.readRefHeader(_buffer);
    final preservedRefId = flag == RefWriter.refValueFlag
        ? _refReader.lastPreservedRefId
        : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      return _refReader.readRef;
    }
    final resolved = _readTypeMeta();
    final value = _readResolvedValue(resolved, null);
    if (preservedRefId != null &&
        !resolved.isBasicValue &&
        _refReader.readRefAt(preservedRefId) == null) {
      _refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }

  Object readValue() {
    final resolved = _readTypeMeta();
    return _readResolvedValue(resolved, null) as Object;
  }

  Object? readNullable() {
    final flag = _buffer.readByte();
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag != RefWriter.notNullValueFlag) {
      throw StateError('Unexpected nullable flag $flag.');
    }
    return readValue();
  }

  T readField<T>(Map<String, Object?> metadata, [T? fallback]) {
    final compatibleFields =
        _compatibleFieldStack.isEmpty ? null : _compatibleFieldStack.last;
    final field = FieldMetadataInternal.fromMetadata(metadata);
    if (compatibleFields != null) {
      if (!compatibleFields.containsKey(field.identifier)) {
        return fallback as T;
      }
      return compatibleFields[field.identifier] as T;
    }
    final shape = field.shape;
    if (shape.isDynamic) {
      return readAny() as T;
    }
    if (shape.isPrimitive && !shape.nullable) {
      return _readPrimitive(shape.typeId) as T;
    }
    if (shape.nullable || shape.ref) {
      final flag = _refReader.readRefHeader(_buffer);
      final preservedRefId = flag == RefWriter.refValueFlag
          ? _refReader.lastPreservedRefId
          : null;
      if (flag == RefWriter.nullFlag) {
        return fallback as T;
      }
      if (flag == RefWriter.refFlag) {
        return _refReader.readRef as T;
      }
      final resolved = _typeResolver.resolveShape(shape);
      final value = _readResolvedValue(resolved, shape);
      if (preservedRefId != null &&
          !resolved.isBasicValue &&
          _refReader.readRefAt(preservedRefId) == null) {
        _refReader.setReadRef(preservedRefId, value);
      }
      return value as T;
    }
    final resolved = _typeResolver.resolveShape(shape);
    return _readResolvedValue(resolved, shape) as T;
  }

  String readMetaString() {
    final header = _buffer.readVarUint32Small7();
    final len = header >>> 1;
    if ((header & 1) == 1) {
      return _metaStrings[len - 1];
    }
    if (len > 16) {
      _buffer.readInt64();
    } else if (len > 0) {
      _buffer.readByte();
    }
    final value = utf8.decode(_buffer.readBytes(len));
    _metaStrings.add(value);
    return value;
  }

  ResolvedTypeInternal _readTypeMeta() {
    final wireTypeId = _buffer.readVarUint32Small7();
    switch (wireTypeId) {
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
      case TypeIds.float16Array:
      case TypeIds.float32Array:
      case TypeIds.float64Array:
        return _typeResolver.resolveShape(
          TypeShapeInternal(
            type: Object,
            typeId: wireTypeId,
            nullable: false,
            ref: false,
            dynamic: false,
            arguments: const <TypeShapeInternal>[],
          ),
        );
      case TypeIds.enumById:
      case TypeIds.struct:
      case TypeIds.ext:
      case TypeIds.typedUnion:
        return _typeResolver.resolveUserById(_buffer.readVarUint32());
      case TypeIds.namedEnum:
      case TypeIds.namedStruct:
      case TypeIds.namedExt:
      case TypeIds.namedUnion:
        return _typeResolver.resolveUserByName(readMetaString(), readMetaString());
      case TypeIds.compatibleStruct:
      case TypeIds.namedCompatibleStruct:
        return _readSharedTypeDef();
      default:
        throw StateError('Unsupported wire type id $wireTypeId.');
    }
  }

  ResolvedTypeInternal _readSharedTypeDef() {
    final marker = _buffer.readVarUint32();
    final isRef = (marker & 1) == 1;
    final index = marker >>> 1;
    if (isRef) {
      return _sharedTypes[index];
    }
    final typeDef = _readTypeDef();
    final resolved = typeDef.$1;
    _sharedTypes.add(typeDef.$2 ?? resolved);
    if (typeDef.$2 != null) {
      return typeDef.$2!;
    }
    return resolved;
  }

  (ResolvedTypeInternal, ResolvedTypeInternal?) _readTypeDef() {
    final header = _buffer.readUint64();
    final metaSizeLowBits = header & 0xff;
    final metaSize = metaSizeLowBits == 0xff
        ? 0xff + _buffer.readVarUint32()
        : metaSizeLowBits.toInt();
    final typeDefBytes = Buffer.wrap(_buffer.readBytes(metaSize));
    final metaHeader = typeDefBytes.readByte();
    final byName = (metaHeader & (1 << 5)) != 0;
    final namespace = byName ? _readMetaStringFrom(typeDefBytes) : null;
    final typeName = byName ? _readMetaStringFrom(typeDefBytes) : null;
    final userTypeId = byName ? null : typeDefBytes.readVarUint32();
    final fields = <FieldMetadataInternal>[];
    final fieldCount = metaHeader & 0x1f;
    for (var i = 0; i < fieldCount; i += 1) {
      fields.add(_readTypeDefField(typeDefBytes));
    }
    final resolved = userTypeId != null
        ? _typeResolver.resolveUserById(userTypeId)
        : _typeResolver.resolveUserByName(namespace!, typeName!);
    final remoteResolved = ResolvedTypeInternal(
      type: resolved.type,
      kind: resolved.kind,
      typeId: resolved.typeId,
      serializer: resolved.serializer,
      userTypeId: resolved.userTypeId,
      namespace: resolved.namespace,
      typeName: resolved.typeName,
      structMetadata: StructMetadataInternal(
        evolving: true,
        fields: fields,
      ),
    );
    return (remoteResolved, remoteResolved);
  }

  String _readMetaStringFrom(Buffer source) {
    final header = source.readVarUint32Small7();
    final len = header >>> 1;
    if ((header & 1) == 1) {
      return _metaStrings[len - 1];
    }
    if (len > 16) {
      source.readInt64();
    } else if (len > 0) {
      source.readByte();
    }
    final value = utf8.decode(source.readBytes(len));
    _metaStrings.add(value);
    return value;
  }

  FieldMetadataInternal _readTypeDefField(Buffer source) {
    final fieldHeader = source.readByte();
    final fieldRef = (fieldHeader & 1) == 1;
    final fieldNullable = (fieldHeader & (1 << 1)) != 0;
    var size = (fieldHeader >> 2) & 0x0f;
    if (size == 15) {
      size += source.readVarUint32();
    }
    final isTag = ((fieldHeader >> 6) & 0x03) == 3;
    final typeId = source.readVarUint32();
    final arguments = <TypeShapeInternal>[];
    if (typeId == TypeIds.list || typeId == TypeIds.set) {
      arguments.add(_readNestedTypeShape(source));
    } else if (typeId == TypeIds.map) {
      arguments.add(_readNestedTypeShape(source));
      arguments.add(_readNestedTypeShape(source));
    }
    final identifier = isTag ? size.toString() : _readMetaStringFrom(source);
    return FieldMetadataInternal(
      name: identifier,
      identifier: identifier,
      id: isTag ? size : null,
      shape: TypeShapeInternal(
        type: Object,
        typeId: typeId,
        nullable: fieldNullable,
        ref: fieldRef,
        dynamic: false,
        arguments: arguments,
      ),
    );
  }

  TypeShapeInternal _readNestedTypeShape(Buffer source) {
    final encoded = source.readVarUint32();
    return TypeShapeInternal(
      type: Object,
      typeId: encoded >> 2,
      nullable: ((encoded >> 1) & 1) == 1,
      ref: (encoded & 1) == 1,
      dynamic: false,
      arguments: const <TypeShapeInternal>[],
    );
  }

  Object? _readResolvedValue(
    ResolvedTypeInternal resolved,
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
        return _readPrimitive(resolved.typeId);
      case TypeIds.string:
        final header = _buffer.readVarUint36Small();
        final byteLength = header >> 2;
        return utf8.decode(_buffer.readBytes(byteLength));
      case TypeIds.binary:
        final size = _buffer.readVarUint32();
        if (size > config.maxBinarySize) {
          throw StateError('Binary payload exceeds ${config.maxBinarySize} bytes.');
        }
        return _buffer.copyBytes(size);
      case TypeIds.boolArray:
        final size = _buffer.readVarUint32();
        return List<bool>.generate(size, (_) => _buffer.readBool(), growable: false);
      case TypeIds.int8Array:
        final size = _buffer.readVarUint32();
        return Int8List.fromList(_buffer.readBytes(size));
      case TypeIds.int16Array:
        return _readTypedArray<Int16List>(2, (bytes) => bytes.buffer.asInt16List());
      case TypeIds.int32Array:
        return _readTypedArray<Int32List>(4, (bytes) => bytes.buffer.asInt32List());
      case TypeIds.int64Array:
        return _readTypedArray<Int64List>(8, (bytes) => bytes.buffer.asInt64List());
      case TypeIds.uint16Array:
        return _readTypedArray<Uint16List>(2, (bytes) => bytes.buffer.asUint16List());
      case TypeIds.uint32Array:
        return _readTypedArray<Uint32List>(4, (bytes) => bytes.buffer.asUint32List());
      case TypeIds.float32Array:
        return _readTypedArray<Float32List>(4, (bytes) => bytes.buffer.asFloat32List());
      case TypeIds.float64Array:
        return _readTypedArray<Float64List>(8, (bytes) => bytes.buffer.asFloat64List());
      case TypeIds.list:
        return _readList(
          _firstOrNull(declaredShape?.arguments),
        );
      case TypeIds.set:
        return Set<Object?>.of(
          _readList(_firstOrNull(declaredShape?.arguments)),
        );
      case TypeIds.map:
        return _readMap(
          _elementAtOrNull(declaredShape?.arguments, 0),
          _elementAtOrNull(declaredShape?.arguments, 1),
        );
      case TypeIds.date:
        return LocalDate.fromEpochDay(_buffer.readInt32());
      case TypeIds.timestamp:
        return Timestamp(_buffer.readInt64(), _buffer.readUint32());
      default:
        final serializer = resolved.serializer;
        if (serializer == null) {
          throw StateError('No serializer available for ${resolved.type}.');
        }
        if (resolved.kind == RegistrationKindInternal.struct &&
            !config.compatible &&
            config.checkStructVersion &&
            resolved.structMetadata != null) {
          final expected = schemaHashInternal(resolved.structMetadata!);
          final actual = _buffer.readUint32();
          if (actual != expected) {
            throw StateError(
              'Struct schema version mismatch for ${resolved.type}: $actual != $expected.',
            );
          }
        }
        if (resolved.kind == RegistrationKindInternal.struct &&
            config.compatible &&
            resolved.structMetadata != null &&
            resolved.isCompatibleStruct) {
          final compatibleFields = <String, Object?>{};
          for (final field in resolved.structMetadata!.fields) {
            compatibleFields[field.identifier] = _readCompatibleField(field);
          }
          _compatibleFieldStack.add(compatibleFields);
          try {
            return serializer.read(this);
          } finally {
            _compatibleFieldStack.removeLast();
          }
        }
        return serializer.read(this);
    }
  }

  T _readTypedArray<T>(
    int elementSize,
    T Function(Uint8List bytes) viewBuilder,
  ) {
    final size = _buffer.readVarUint32();
    final bytes = Uint8List.fromList(_buffer.readBytes(size * elementSize));
    return viewBuilder(bytes);
  }

  Object? _readCompatibleField(FieldMetadataInternal field) {
    final shape = field.shape;
    if (shape.isDynamic) {
      return readAny();
    }
    if (shape.isPrimitive && !shape.nullable) {
      return _readPrimitive(shape.typeId);
    }
    if (shape.nullable || shape.ref) {
      final flag = _refReader.readRefHeader(_buffer);
      final preservedRefId = flag == RefWriter.refValueFlag
          ? _refReader.lastPreservedRefId
          : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return _refReader.readRef;
      }
      final resolved = _typeResolver.resolveShape(shape);
      final value = _readResolvedValue(resolved, shape);
      if (preservedRefId != null &&
          !resolved.isBasicValue &&
          _refReader.readRefAt(preservedRefId) == null) {
        _refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    final resolved = _typeResolver.resolveShape(shape);
    return _readResolvedValue(resolved, shape);
  }

  Object _readPrimitive(int typeId) {
    switch (typeId) {
      case TypeIds.boolType:
        return _buffer.readBool();
      case TypeIds.int8:
        return Int8(_buffer.readByte());
      case TypeIds.int16:
        return Int16(_buffer.readInt16());
      case TypeIds.int32:
        return Int32(_buffer.readInt32());
      case TypeIds.int64:
        return _buffer.readInt64();
      case TypeIds.uint8:
        return UInt8(_buffer.readUint8());
      case TypeIds.uint16:
        return UInt16(_buffer.readUint16());
      case TypeIds.uint32:
        return UInt32(_buffer.readUint32());
      case TypeIds.float16:
        return _buffer.readFloat16();
      case TypeIds.float32:
        return Float32(_buffer.readFloat32());
      case TypeIds.float64:
        return _buffer.readFloat64();
      default:
        throw StateError('Unsupported primitive type id $typeId.');
    }
  }

  List<Object?> _readList(TypeShapeInternal? elementShape) {
    final size = _buffer.readVarUint32();
    if (size > config.maxCollectionSize) {
      throw StateError('Collection size $size exceeds ${config.maxCollectionSize}.');
    }
    final header = _buffer.readUint8();
    final trackRef = (header & 0x01) == 1;
    final hasNull = (header & 0x02) != 0;
    final declaredType = (header & 0x04) != 0;
    final sameType = (header & 0x08) != 0;
    final result = <Object?>[];
    final sameResolved =
        (!declaredType && sameType && size > 0) ? _readTypeMeta() : null;
    for (var index = 0; index < size; index += 1) {
      if (declaredType && elementShape != null) {
        result.add(
          readField<Object?>(
            <String, Object?>{
              'name': 'item',
              'identifier': 'item',
              'id': null,
              'shape': <String, Object?>{
                'type': elementShape.type,
                'typeId': elementShape.typeId,
                'nullable': elementShape.nullable || hasNull,
                'ref': trackRef,
                'dynamic': elementShape.dynamic,
                'arguments': const <Object?>[],
              },
            },
          ),
        );
        continue;
      }
      if (sameType && sameResolved != null) {
        if (hasNull || trackRef) {
          final flag = _refReader.readRefHeader(_buffer);
          final preservedRefId = flag == RefWriter.refValueFlag
              ? _refReader.lastPreservedRefId
              : null;
          if (flag == RefWriter.nullFlag) {
            result.add(null);
            continue;
          }
          if (flag == RefWriter.refFlag) {
            result.add(_refReader.readRef);
            continue;
          }
          final value = _readResolvedValue(sameResolved, null);
          if (preservedRefId != null &&
              !sameResolved.isBasicValue &&
              _refReader.readRefAt(preservedRefId) == null) {
            _refReader.setReadRef(preservedRefId, value);
          }
          result.add(value);
        } else {
          result.add(_readResolvedValue(sameResolved, null));
        }
        continue;
      }
      if (trackRef) {
        result.add(readAny());
      } else if (hasNull) {
        result.add(readNullable());
      } else {
        result.add(readValue());
      }
    }
    return result;
  }

  Map<Object?, Object?> _readMap(
    TypeShapeInternal? keyShape,
    TypeShapeInternal? valueShape,
  ) {
    final totalSize = _buffer.readVarUint32();
    if (totalSize > config.maxCollectionSize) {
      throw StateError('Map size $totalSize exceeds ${config.maxCollectionSize}.');
    }
    final result = <Object?, Object?>{};
    while (result.length < totalSize) {
      final header = _buffer.readUint8();
      final keyTrackRef = (header & 0x01) == 1;
      final keyHasNull = (header & 0x02) != 0;
      final keyDeclared = (header & 0x04) != 0;
      final valueTrackRef = (header & 0x08) != 0;
      final valueHasNull = (header & 0x10) != 0;
      final valueDeclared = (header & 0x20) != 0;
      final pairCount = keyHasNull || valueHasNull ? 1 : _buffer.readUint8();
      for (var i = 0; i < pairCount; i += 1) {
        final key = _readMapEntryValue(
          keyShape,
          keyTrackRef,
          keyHasNull,
          keyDeclared,
        );
        final value = _readMapEntryValue(
          valueShape,
          valueTrackRef,
          valueHasNull,
          valueDeclared,
        );
        result[key] = value;
      }
    }
    return result;
  }

  Object? _readMapEntryValue(
    TypeShapeInternal? shape,
    bool trackRef,
    bool hasNull,
    bool declared,
  ) {
    if (declared && shape != null) {
      return readField<Object?>(
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
            'arguments': const <Object?>[],
          },
        },
      );
    }
    if (trackRef) {
      return readAny();
    }
    if (hasNull) {
      return readNullable();
    }
    return readValue();
  }
}

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(List<TypeShapeInternal>? values, int index) =>
    values == null || values.length <= index ? null : values[index];
