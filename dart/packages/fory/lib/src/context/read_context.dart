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
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/meta/type_meta.dart';
import 'package:fory/src/string_codec.dart';
import 'package:fory/src/util/hash_util.dart';

/// Read-side runtime state for a single Fory operation.
///
/// Generated and manual serializers receive this object from [Serializer.read].
/// Application code normally interacts with [Fory] instead of preparing
/// contexts directly.
final class ReadContext {
  /// Effective runtime configuration for the active operation.
  final Config config;
  final TypeResolver _typeResolver;
  final RefReader _refReader;

  late Buffer _buffer;
  final List<_MetaStringEntry> _metaStrings = <_MetaStringEntry>[];
  final List<ResolvedTypeInternal> _sharedTypes = <ResolvedTypeInternal>[];
  final List<Map<String, Object?>> _compatibleFieldStack =
      <Map<String, Object?>>[];
  int _depth = 0;

  @internal
  ReadContext(this.config, this._typeResolver, this._refReader);

  /// Prepares the context to read from [buffer].
  ///
  /// This resets all per-operation caches, shared TypeDef state, and Ref state.
  void prepare(Buffer buffer) {
    _buffer = buffer;
    _metaStrings.clear();
    _sharedTypes.clear();
    _compatibleFieldStack.clear();
    _refReader.reset();
    _depth = 0;
  }

  /// The active input buffer for the current operation.
  Buffer get buffer => _buffer;

  /// Records entry into one more nested read frame.
  void increaseDepth() {
    _depth += 1;
    if (_depth > config.maxDepth) {
      throw StateError('Deserialization depth exceeded ${config.maxDepth}.');
    }
  }

  /// Records exit from a nested read frame.
  void decreaseDepth() {
    _depth -= 1;
  }

  /// Reads a boolean value.
  bool readBool() => _buffer.readBool();

  /// Reads a signed 8-bit integer.
  int readByte() => _buffer.readByte();

  /// Reads an unsigned 8-bit integer.
  int readUint8() => _buffer.readUint8();

  /// Reads a signed little-endian 16-bit integer.
  int readInt16() => _buffer.readInt16();

  /// Reads a signed little-endian 32-bit integer.
  int readInt32() => _buffer.readInt32();

  /// Reads a signed little-endian 64-bit integer.
  int readInt64() => _buffer.readInt64();

  /// Reads a half-precision floating-point value.
  Float16 readFloat16() => _buffer.readFloat16();

  /// Reads a single-precision floating-point value.
  double readFloat32() => _buffer.readFloat32();

  /// Reads a double-precision floating-point value.
  double readFloat64() => _buffer.readFloat64();

  /// Reads a zig-zag encoded signed 32-bit varint.
  int readVarInt32() => _buffer.readVarInt32();

  /// Reads an unsigned 32-bit varint.
  int readVarUint32() => _buffer.readVarUint32();

  /// Reads a zig-zag encoded signed 64-bit varint.
  int readVarInt64() => _buffer.readVarInt64();

  /// Reads a tagged signed 64-bit integer.
  int readTaggedInt64() => _buffer.readTaggedInt64();

  /// Reads an unsigned 64-bit varint.
  int readVarUint64() => _buffer.readVarUint64();

  /// Reads a tagged unsigned 64-bit integer.
  int readTaggedUint64() => _buffer.readTaggedUint64();

  /// Binds [value] to the most recently preserved Ref slot.
  ///
  /// Manual serializers call this when they need to publish a newly created
  /// object before reading its recursive fields.
  void reference(Object? value) {
    _refReader.reference(value);
  }

  /// Reads a value using the type metadata stored in the payload.
  Object? readAny() {
    final flag = _refReader.readRefHeader(_buffer);
    final preservedRefId =
        flag == RefWriter.refValueFlag ? _refReader.lastPreservedRefId : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      return _refReader.readRef;
    }
    final resolved = _readTypeMeta();
    final value = _readResolvedValue(resolved, null);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        _refReader.readRefAt(preservedRefId) == null) {
      _refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }

  /// Reads a non-null value using the type metadata stored in the payload.
  Object readValue() {
    final resolved = _readTypeMeta();
    return _readResolvedValue(resolved, null) as Object;
  }

  /// Reads a nullable value using the standard Fory nullable framing.
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

  /// Reads one annotated struct field described by [metadata].
  ///
  /// [fallback] is returned when compatible mode omits the field from the
  /// incoming payload.
  T readField<T>(Map<String, Object?> metadata, [T? fallback]) {
    final compatibleFields =
        _compatibleFieldStack.isEmpty ? null : _compatibleFieldStack.last;
    final field = FieldMetadataInternal.fromMetadata(metadata);
    if (compatibleFields != null) {
      if (!compatibleFields.containsKey(field.identifier)) {
        return fallback as T;
      }
      final value = compatibleFields[field.identifier];
      if (value is _DeferredReadRef) {
        return _refReader.readRefAt(value.id) as T;
      }
      return value as T;
    }
    final shape = field.shape;
    if (shape.isDynamic) {
      return readAny() as T;
    }
    if (shape.isPrimitive && !shape.nullable) {
      return _readPrimitive(shape.typeId) as T;
    }
    final resolved = _typeResolver.resolveShape(shape);
    if (!_usesDeclaredTypeInfo(shape, resolved)) {
      if (shape.ref) {
        return readAny() as T;
      }
      if (shape.nullable) {
        return readNullable() as T;
      }
      return readValue() as T;
    }
    if (shape.nullable || shape.ref) {
      final flag = _refReader.readRefHeader(_buffer);
      final preservedRefId =
          flag == RefWriter.refValueFlag ? _refReader.lastPreservedRefId : null;
      if (flag == RefWriter.nullFlag) {
        return fallback as T;
      }
      if (flag == RefWriter.refFlag) {
        return _refReader.readRef as T;
      }
      final value = _readResolvedValue(resolved, shape);
      if (preservedRefId != null &&
          resolved.supportsRef &&
          _refReader.readRefAt(preservedRefId) == null) {
        _refReader.setReadRef(preservedRefId, value);
      }
      return value as T;
    }
    return _readResolvedValue(resolved, shape) as T;
  }

  /// Reads a meta string using package-style decoding rules.
  String readMetaString() =>
      _readMetaStringFrom(_buffer, decoder: decodePackageMetaStringInternal);

  ResolvedTypeInternal _readTypeMeta() {
    final typeMeta = _typeResolver.typeMetaDecoder.read(
      _buffer,
      config: config,
      resolveBuiltinWireType: _typeResolver.resolveBuiltinWireType,
      resolveUserById: _typeResolver.resolveUserById,
      resolveUserByName: _typeResolver.resolveUserByName,
      readSharedTypeDef: _readSharedTypeDef,
      readPackageMetaString: _readPackageMetaString,
      readTypeNameMetaString: _readTypeNameMetaString,
    );
    return typeMeta.resolvedType;
  }

  TypeMeta _readSharedTypeDef() {
    final marker = _buffer.readVarUint32Small14();
    final isRef = (marker & 1) == 1;
    final index = marker >>> 1;
    if (isRef) {
      return _typeResolver.typeMetaForResolved(_sharedTypes[index]);
    }
    final typeDef = _readTypeDef();
    final resolved = typeDef.$1;
    _sharedTypes.add(typeDef.$2 ?? resolved);
    return _typeResolver.typeMetaForResolved(typeDef.$2 ?? resolved);
  }

  (ResolvedTypeInternal, ResolvedTypeInternal?) _readTypeDef() {
    final header = _buffer.readInt64();
    final metaSizeLowBits = header & 0xff;
    final metaSize = metaSizeLowBits == 0xff
        ? 0xff + _buffer.readVarUint32Small14()
        : metaSizeLowBits.toInt();
    final typeDefBytes = Buffer.wrap(_buffer.readBytes(metaSize));
    final classHeader = typeDefBytes.readUint8();
    var fieldCount = classHeader & typeDefSmallFieldCountThreshold;
    if (fieldCount == typeDefSmallFieldCountThreshold) {
      fieldCount += typeDefBytes.readVarUint32Small7();
    }
    final byName = (classHeader & typeDefRegisterByNameFlag) != 0;
    final namespace = byName ? _readPackageNameFromTypeDef(typeDefBytes) : null;
    final typeName = byName ? _readTypeNameFromTypeDef(typeDefBytes) : null;
    int? userTypeId;
    if (byName) {
      userTypeId = null;
    } else {
      typeDefBytes.readUint8();
      userTypeId = typeDefBytes.readVarUint32();
    }
    final fields = <FieldMetadataInternal>[];
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
      structMetadata: resolved.structMetadata,
      remoteStructMetadata:
          StructMetadataInternal(evolving: true, fields: fields),
    );
    if (remoteResolved.remoteStructMetadata != null) {
      _typeResolver.rememberRemoteStructMetadata(
        resolved,
        remoteResolved.remoteStructMetadata!,
      );
    }
    return (remoteResolved, remoteResolved);
  }

  String _readPackageMetaString() =>
      _readMetaStringFrom(_buffer, decoder: decodePackageMetaStringInternal);

  String _readTypeNameMetaString() =>
      _readMetaStringFrom(_buffer, decoder: decodeTypeNameMetaStringInternal);

  String _readPackageNameFromTypeDef(Buffer source) =>
      _readTypeDefName(source, decoder: decodePackageNameInternal);

  String _readTypeNameFromTypeDef(Buffer source) =>
      _readTypeDefName(source, decoder: decodeTypeNameInternal);

  String _readMetaStringFrom(
    Buffer source, {
    required String Function(List<int> bytes, int encoding) decoder,
  }) {
    final header = source.readVarUint32Small7();
    final len = header >>> 1;
    if ((header & 1) == 1) {
      final entry = _metaStrings[len - 1];
      return decoder(entry.bytes, entry.encoding);
    }
    var encoding = metaStringUtf8Encoding;
    if (len > metaStringSmallThreshold) {
      encoding = source.readInt64() & 0xff;
    } else if (len > 0) {
      encoding = source.readByte() & 0xff;
    }
    final bytes = source.readBytes(len);
    _metaStrings.add(_MetaStringEntry(bytes, encoding));
    try {
      return decoder(bytes, encoding);
    } catch (error) {
      throw StateError(
        'Failed to decode meta string: header=$header len=$len encoding=$encoding bytes=$bytes error=$error',
      );
    }
  }

  String _readTypeDefName(
    Buffer source, {
    required String Function(List<int> bytes, int encoding) decoder,
  }) {
    final header = source.readUint8();
    final encoding = header & 0x03;
    var size = header >>> 2;
    if (size == typeDefBigNameThreshold) {
      size += source.readVarUint32Small7();
    }
    return decoder(source.readBytes(size), encoding);
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

  Object? _readResolvedValue(
    ResolvedTypeInternal resolved,
    TypeShapeInternal? declaredShape,
  ) {
    if (TypeIds.isPrimitive(resolved.typeId)) {
      return _readPrimitive(resolved.typeId);
    }
    switch (resolved.typeId) {
      case TypeIds.string:
        return _readStringPayload();
      case TypeIds.binary:
        final size = _buffer.readVarUint32();
        if (size > config.maxBinarySize) {
          throw StateError(
              'Binary payload exceeds ${config.maxBinarySize} bytes.');
        }
        return _buffer.copyBytes(size);
      case TypeIds.boolArray:
        final size = _buffer.readVarUint32();
        return List<bool>.generate(size, (_) => _buffer.readBool(),
            growable: false);
      case TypeIds.int8Array:
        final size = _buffer.readVarUint32();
        return Int8List.fromList(_buffer.readBytes(size));
      case TypeIds.int16Array:
        return _readTypedArray<Int16List>(
            2, (bytes) => bytes.buffer.asInt16List());
      case TypeIds.int32Array:
        return _readTypedArray<Int32List>(
            4, (bytes) => bytes.buffer.asInt32List());
      case TypeIds.int64Array:
        return _readTypedArray<Int64List>(
            8, (bytes) => bytes.buffer.asInt64List());
      case TypeIds.uint16Array:
        return _readTypedArray<Uint16List>(
            2, (bytes) => bytes.buffer.asUint16List());
      case TypeIds.uint32Array:
        return _readTypedArray<Uint32List>(
            4, (bytes) => bytes.buffer.asUint32List());
      case TypeIds.uint64Array:
        return _readTypedArray<Uint64List>(
            8, (bytes) => bytes.buffer.asUint64List());
      case TypeIds.float32Array:
        return _readTypedArray<Float32List>(
            4, (bytes) => bytes.buffer.asFloat32List());
      case TypeIds.float64Array:
        return _readTypedArray<Float64List>(
            8, (bytes) => bytes.buffer.asFloat64List());
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
          final remoteFields = resolved.remoteStructMetadata?.fields ??
              resolved.structMetadata!.fields;
          final localFields = <String, FieldMetadataInternal>{
            for (final field in resolved.structMetadata!.fields)
              field.identifier: field,
          };
          final compatibleFields = <String, Object?>{};
          for (final remoteField in remoteFields) {
            final localField = localFields[remoteField.identifier];
            if (localField == null) {
              _readCompatibleField(remoteField);
              continue;
            }
            compatibleFields[remoteField.identifier] = _readCompatibleField(
              _mergeCompatibleField(localField, remoteField),
            );
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
    final byteSize = _buffer.readVarUint32();
    if (byteSize % elementSize != 0) {
      throw StateError(
        'Typed array byte size $byteSize is not aligned to element size $elementSize.',
      );
    }
    final bytes = Uint8List.fromList(_buffer.readBytes(byteSize));
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
    final resolved = _typeResolver.resolveShape(shape);
    if (!_usesDeclaredTypeInfo(shape, resolved)) {
      if (shape.ref) {
        return _readRefValueWithTypeMeta(shape);
      }
      if (shape.nullable) {
        final flag = _buffer.readByte();
        if (flag == RefWriter.nullFlag) {
          return null;
        }
        if (flag != RefWriter.notNullValueFlag) {
          throw StateError('Unexpected nullable flag $flag.');
        }
      }
      return _readResolvedValue(_readTypeMeta(), shape);
    }
    if (shape.nullable || shape.ref) {
      final flag = _refReader.readRefHeader(_buffer);
      final preservedRefId =
          flag == RefWriter.refValueFlag ? _refReader.lastPreservedRefId : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        final value = _refReader.readRef;
        if (value != null) {
          return value;
        }
        final refId = _refReader.readRefId;
        if (refId != null) {
          return _DeferredReadRef(refId);
        }
        return null;
      }
      final value = _readResolvedValue(resolved, shape);
      if (preservedRefId != null &&
          resolved.supportsRef &&
          _refReader.readRefAt(preservedRefId) == null) {
        _refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
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
      case TypeIds.varInt32:
        return Int32(_buffer.readVarInt32());
      case TypeIds.int64:
        return _buffer.readInt64();
      case TypeIds.varInt64:
        return _buffer.readVarInt64();
      case TypeIds.taggedInt64:
        return _buffer.readTaggedInt64();
      case TypeIds.uint8:
        return UInt8(_buffer.readUint8());
      case TypeIds.uint16:
        return UInt16(_buffer.readUint16());
      case TypeIds.uint32:
        return UInt32(_buffer.readUint32());
      case TypeIds.varUint32:
        return UInt32(_buffer.readVarUint32());
      case TypeIds.uint64:
        return _buffer.readUint64();
      case TypeIds.varUint64:
        return _buffer.readVarUint64();
      case TypeIds.taggedUint64:
        return _buffer.readTaggedUint64();
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

  String _readStringPayload() {
    final header = _buffer.readVarUint36Small();
    final encoding = header & 0x03;
    final byteLength = header >>> 2;
    return decodeStringInternal(_buffer.readBytes(byteLength), encoding);
  }

  List<Object?> _readList(TypeShapeInternal? elementShape) {
    final size = _buffer.readVarUint32();
    if (size > config.maxCollectionSize) {
      throw StateError(
          'Collection size $size exceeds ${config.maxCollectionSize}.');
    }
    if (size == 0) {
      return <Object?>[];
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
        Map<String, Object?> shapeMetadata(
          TypeShapeInternal value, {
          required bool isRoot,
        }) {
          return <String, Object?>{
            'type': value.type,
            'typeId': value.typeId,
            'nullable': isRoot ? hasNull : value.nullable,
            'ref': isRoot ? trackRef : value.ref,
            'dynamic': value.dynamic,
            'arguments': value.arguments
                .map(
                  (argument) => shapeMetadata(argument, isRoot: false),
                )
                .toList(growable: false),
          };
        }

        result.add(
          readField<Object?>(
            <String, Object?>{
              'name': 'item',
              'identifier': 'item',
              'id': null,
              'shape': shapeMetadata(elementShape, isRoot: true),
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
              sameResolved.supportsRef &&
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
    var remaining = _buffer.readVarUint32();
    if (remaining > config.maxCollectionSize) {
      throw StateError(
          'Map size $remaining exceeds ${config.maxCollectionSize}.');
    }
    final result = <Object?, Object?>{};
    while (remaining > 0) {
      final header = _buffer.readUint8();
      final keyHasNull = (header & 0x02) != 0;
      final valueHasNull = (header & 0x10) != 0;
      if (keyHasNull || valueHasNull) {
        result[_readNullChunkKey(header, keyShape)] = _readNullChunkValue(
          header,
          valueShape,
        );
        remaining -= 1;
        continue;
      }
      final keyTrackRef = (header & 0x01) != 0;
      final valueTrackRef = (header & 0x08) != 0;
      final keyDeclared = (header & 0x04) != 0;
      final valueDeclared = (header & 0x20) != 0;
      final chunkSize = _buffer.readUint8();
      final keyResolved = keyDeclared ? null : _readTypeMeta();
      final valueResolved = valueDeclared ? null : _readTypeMeta();
      for (var index = 0; index < chunkSize; index += 1) {
        final key = keyDeclared
            ? _readDeclaredMapValue(keyShape!, trackRef: keyTrackRef)
            : _readResolvedMapValue(keyResolved!, trackRef: keyTrackRef);
        final value = valueDeclared
            ? _readDeclaredMapValue(valueShape!, trackRef: valueTrackRef)
            : _readResolvedMapValue(valueResolved!, trackRef: valueTrackRef);
        result[key] = value;
      }
      remaining -= chunkSize;
    }
    return result;
  }

  Object? _readNullChunkKey(int header, TypeShapeInternal? keyShape) {
    final keyHasNull = (header & 0x02) != 0;
    if (keyHasNull) {
      return null;
    }
    final trackRef = (header & 0x01) != 0;
    final declared = (header & 0x04) != 0;
    if (declared && keyShape != null) {
      return _readDeclaredMapValue(keyShape, trackRef: trackRef);
    }
    return trackRef ? readAny() : readValue();
  }

  Object? _readNullChunkValue(int header, TypeShapeInternal? valueShape) {
    final valueHasNull = (header & 0x10) != 0;
    if (valueHasNull) {
      return null;
    }
    final trackRef = (header & 0x08) != 0;
    final declared = (header & 0x20) != 0;
    if (declared && valueShape != null) {
      return _readDeclaredMapValue(valueShape, trackRef: trackRef);
    }
    return trackRef ? readAny() : readValue();
  }

  Object? _readDeclaredMapValue(
    TypeShapeInternal shape, {
    required bool trackRef,
  }) {
    return readField<Object?>(
      _mapFieldMetadata(
        shape,
        trackRef: trackRef,
      ),
    );
  }

  Object? _readResolvedMapValue(
    ResolvedTypeInternal resolved, {
    required bool trackRef,
  }) {
    if (!trackRef) {
      return _readResolvedValue(resolved, null);
    }
    final flag = _refReader.readRefHeader(_buffer);
    final preservedRefId =
        flag == RefWriter.refValueFlag ? _refReader.lastPreservedRefId : null;
    if (flag == RefWriter.refFlag) {
      return _refReader.readRef;
    }
    final value = _readResolvedValue(resolved, null);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        _refReader.readRefAt(preservedRefId) == null) {
      _refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }
}

FieldMetadataInternal _mergeCompatibleField(
  FieldMetadataInternal localField,
  FieldMetadataInternal remoteField,
) {
  TypeShapeInternal mergeShape(
      TypeShapeInternal local, TypeShapeInternal remote) {
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

extension on ReadContext {
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

  Object? _readRefValueWithTypeMeta(TypeShapeInternal declaredShape) {
    final flag = _refReader.readRefHeader(_buffer);
    final preservedRefId =
        flag == RefWriter.refValueFlag ? _refReader.lastPreservedRefId : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      final value = _refReader.readRef;
      if (value != null) {
        return value;
      }
      final refId = _refReader.readRefId;
      if (refId != null) {
        return _DeferredReadRef(refId);
      }
      return null;
    }
    final resolved = _readTypeMeta();
    final value = _readResolvedValue(resolved, declaredShape);
    if (preservedRefId != null &&
        resolved.supportsRef &&
        _refReader.readRefAt(preservedRefId) == null) {
      _refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }
}

Map<String, Object?> _mapFieldMetadata(
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
            _shapeMetadata,
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

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(
        List<TypeShapeInternal>? values, int index) =>
    values == null || values.length <= index ? null : values[index];

class _DeferredReadRef {
  final int id;

  const _DeferredReadRef(this.id);
}

final class _MetaStringEntry {
  final Uint8List bytes;
  final int encoding;

  const _MetaStringEntry(this.bytes, this.encoding);
}

Map<String, Object?> _shapeMetadata(TypeShapeInternal shape) =>
    <String, Object?>{
      'type': shape.type,
      'typeId': shape.typeId,
      'nullable': shape.nullable,
      'ref': shape.ref,
      'dynamic': shape.dynamic,
      'arguments': shape.arguments.map(_shapeMetadata).toList(growable: false),
    };
