import 'dart:typed_data';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/declared_value_codec.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';
import 'package:fory/src/string_codec.dart';

WriteContext _writeImpl(WriteContext context) => context;

ReadContext _readImpl(ReadContext context) => context;

void writePayloadPrimitive(
  WriteContext context,
  int typeId,
  Object value,
) {
  final buffer = context.buffer;
  switch (typeId) {
    case TypeIds.boolType:
      buffer.writeBool(value as bool);
      return;
    case TypeIds.int8:
      buffer.writeByte((value as Int8).value);
      return;
    case TypeIds.int16:
      buffer.writeInt16((value as Int16).value);
      return;
    case TypeIds.int32:
      buffer.writeInt32((value as Int32).value);
      return;
    case TypeIds.varInt32:
      buffer.writeVarInt32(value is Int32 ? value.value : value as int);
      return;
    case TypeIds.int64:
      buffer.writeInt64(value as int);
      return;
    case TypeIds.varInt64:
      buffer.writeVarInt64(value as int);
      return;
    case TypeIds.taggedInt64:
      buffer.writeTaggedInt64(value as int);
      return;
    case TypeIds.uint8:
      buffer.writeUint8(value is UInt8 ? value.value : value as int);
      return;
    case TypeIds.uint16:
      buffer.writeUint16(value is UInt16 ? value.value : value as int);
      return;
    case TypeIds.uint32:
      buffer.writeUint32(value is UInt32 ? value.value : value as int);
      return;
    case TypeIds.varUint32:
      buffer.writeVarUint32(value is UInt32 ? value.value : value as int);
      return;
    case TypeIds.uint64:
      buffer.writeUint64(value as int);
      return;
    case TypeIds.varUint64:
      buffer.writeVarUint64(value as int);
      return;
    case TypeIds.taggedUint64:
      buffer.writeTaggedUint64(value as int);
      return;
    case TypeIds.float16:
      buffer.writeFloat16(value as Float16);
      return;
    case TypeIds.float32:
      buffer.writeFloat32((value as Float32).value);
      return;
    case TypeIds.float64:
      buffer.writeFloat64(value as double);
      return;
    default:
      throw StateError('Unsupported primitive type id $typeId.');
  }
}

void writeStringPayload(
  WriteContext context,
  String value,
) {
  writeStringInternal(context.buffer, value);
}

void writePayloadValue(
  WriteContext context,
  ResolvedTypeInternal resolved,
  Object value,
  TypeShapeInternal? declaredShape,
) {
  final internal = _writeImpl(context);
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
      writePayloadPrimitive(context, resolved.typeId, value);
      return;
    case TypeIds.string:
      writeStringPayload(context, value as String);
      return;
    case TypeIds.binary:
      final bytes = value as Uint8List;
      if (bytes.length > context.config.maxBinarySize) {
        throw StateError(
          'Binary payload exceeds ${context.config.maxBinarySize} bytes.',
        );
      }
      context.buffer.writeVarUint32(bytes.length);
      context.buffer.writeBytes(bytes);
      return;
    case TypeIds.boolArray:
      final values = value as List<bool>;
      context.buffer.writeVarUint32(values.length);
      for (final element in values) {
        context.buffer.writeBool(element);
      }
      return;
    case TypeIds.int8Array:
      final values = value as Int8List;
      context.buffer.writeVarUint32(values.length);
      context.buffer.writeBytes(values);
      return;
    case TypeIds.int16Array:
      writeFixedArrayPayload(context, value as Int16List);
      return;
    case TypeIds.int32Array:
      writeFixedArrayPayload(context, value as Int32List);
      return;
    case TypeIds.int64Array:
      writeFixedArrayPayload(context, value as Int64List);
      return;
    case TypeIds.uint16Array:
      writeFixedArrayPayload(context, value as Uint16List);
      return;
    case TypeIds.uint32Array:
      writeFixedArrayPayload(context, value as Uint32List);
      return;
    case TypeIds.uint64Array:
      writeFixedArrayPayload(context, value as Uint64List);
      return;
    case TypeIds.float32Array:
      writeFixedArrayPayload(context, value as Float32List);
      return;
    case TypeIds.float64Array:
      writeFixedArrayPayload(context, value as Float64List);
      return;
    case TypeIds.list:
      _writeList(
        context,
        value as List,
        _firstOrNull(declaredShape?.arguments),
        trackRef:
            declaredShape == null ? internal.rootTrackRef : declaredShape.ref,
      );
      return;
    case TypeIds.set:
      _writeList(
        context,
        value as Set,
        _firstOrNull(declaredShape?.arguments),
        trackRef:
            declaredShape == null ? internal.rootTrackRef : declaredShape.ref,
      );
      return;
    case TypeIds.map:
      _writeMap(
        context,
        value as Map,
        _elementAtOrNull(declaredShape?.arguments, 0),
        _elementAtOrNull(declaredShape?.arguments, 1),
        trackRef:
            declaredShape == null ? internal.rootTrackRef : declaredShape.ref,
      );
      return;
    case TypeIds.date:
      context.buffer.writeInt32((value as LocalDate).toEpochDay());
      return;
    case TypeIds.timestamp:
      final timestamp = value as Timestamp;
      context.buffer.writeInt64(timestamp.seconds);
      context.buffer.writeUint32(timestamp.nanoseconds);
      return;
    default:
      if (resolved.kind == RegistrationKindInternal.struct) {
        resolved.structCodec!.write(context, resolved, value);
        return;
      }
      final serializer = resolved.serializer;
      if (serializer == null) {
        throw StateError('No serializer available for ${resolved.type}.');
      }
      serializer.write(context, value);
  }
}

Object readPayloadPrimitive(
  ReadContext context,
  int typeId,
) {
  final buffer = context.buffer;
  switch (typeId) {
    case TypeIds.boolType:
      return buffer.readBool();
    case TypeIds.int8:
      return Int8(buffer.readByte());
    case TypeIds.int16:
      return Int16(buffer.readInt16());
    case TypeIds.int32:
      return Int32(buffer.readInt32());
    case TypeIds.varInt32:
      return Int32(buffer.readVarInt32());
    case TypeIds.int64:
      return buffer.readInt64();
    case TypeIds.varInt64:
      return buffer.readVarInt64();
    case TypeIds.taggedInt64:
      return buffer.readTaggedInt64();
    case TypeIds.uint8:
      return UInt8(buffer.readUint8());
    case TypeIds.uint16:
      return UInt16(buffer.readUint16());
    case TypeIds.uint32:
      return UInt32(buffer.readUint32());
    case TypeIds.varUint32:
      return UInt32(buffer.readVarUint32());
    case TypeIds.uint64:
      return buffer.readUint64();
    case TypeIds.varUint64:
      return buffer.readVarUint64();
    case TypeIds.taggedUint64:
      return buffer.readTaggedUint64();
    case TypeIds.float16:
      return buffer.readFloat16();
    case TypeIds.float32:
      return Float32(buffer.readFloat32());
    case TypeIds.float64:
      return buffer.readFloat64();
    default:
      throw StateError('Unsupported primitive type id $typeId.');
  }
}

String readStringPayload(ReadContext context) {
  final header = context.buffer.readVarUint36Small();
  final encoding = header & 0x03;
  final byteLength = header >>> 2;
  return readStringFromBufferInternal(context.buffer, byteLength, encoding);
}

Object? readPayloadValue(
  ReadContext context,
  ResolvedTypeInternal resolved,
  TypeShapeInternal? declaredShape,
  {bool hasPreservedRef = false}
) {
  if (TypeIds.isPrimitive(resolved.typeId)) {
    return readPayloadPrimitive(context, resolved.typeId);
  }
  switch (resolved.typeId) {
    case TypeIds.string:
      return readStringPayload(context);
    case TypeIds.binary:
      final size = context.buffer.readVarUint32();
      if (size > context.config.maxBinarySize) {
        throw StateError(
          'Binary payload exceeds ${context.config.maxBinarySize} bytes.',
        );
      }
      return context.buffer.copyBytes(size);
    case TypeIds.boolArray:
      final size = context.buffer.readVarUint32();
      return List<bool>.generate(
        size,
        (_) => context.buffer.readBool(),
        growable: false,
      );
    case TypeIds.int8Array:
      final size = context.buffer.readVarUint32();
      return Int8List.fromList(context.buffer.readBytes(size));
    case TypeIds.int16Array:
      return readTypedArrayPayload<Int16List>(
        context,
        2,
        (bytes) =>
            bytes.buffer.asInt16List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 2,
            ),
      );
    case TypeIds.int32Array:
      return readTypedArrayPayload<Int32List>(
        context,
        4,
        (bytes) =>
            bytes.buffer.asInt32List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 4,
            ),
      );
    case TypeIds.int64Array:
      return readTypedArrayPayload<Int64List>(
        context,
        8,
        (bytes) =>
            bytes.buffer.asInt64List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 8,
            ),
      );
    case TypeIds.uint16Array:
      return readTypedArrayPayload<Uint16List>(
        context,
        2,
        (bytes) =>
            bytes.buffer.asUint16List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 2,
            ),
      );
    case TypeIds.uint32Array:
      return readTypedArrayPayload<Uint32List>(
        context,
        4,
        (bytes) =>
            bytes.buffer.asUint32List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 4,
            ),
      );
    case TypeIds.uint64Array:
      return readTypedArrayPayload<Uint64List>(
        context,
        8,
        (bytes) =>
            bytes.buffer.asUint64List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 8,
            ),
      );
    case TypeIds.float32Array:
      return readTypedArrayPayload<Float32List>(
        context,
        4,
        (bytes) =>
            bytes.buffer.asFloat32List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 4,
            ),
      );
    case TypeIds.float64Array:
      return readTypedArrayPayload<Float64List>(
        context,
        8,
        (bytes) =>
            bytes.buffer.asFloat64List(
              bytes.offsetInBytes,
              bytes.lengthInBytes ~/ 8,
            ),
      );
    case TypeIds.list:
      return _readList(
        context,
        _firstOrNull(declaredShape?.arguments),
      );
    case TypeIds.set:
      return _readSet(
        context,
        _firstOrNull(declaredShape?.arguments),
      );
    case TypeIds.map:
      return _readMap(
        context,
        _elementAtOrNull(declaredShape?.arguments, 0),
        _elementAtOrNull(declaredShape?.arguments, 1),
      );
    case TypeIds.date:
      return LocalDate.fromEpochDay(context.buffer.readInt32());
    case TypeIds.timestamp:
      return Timestamp(
        context.buffer.readInt64(),
        context.buffer.readUint32(),
      );
    default:
      if (resolved.kind == RegistrationKindInternal.struct) {
        return resolved.structCodec!.read(
          context,
          resolved,
          hasCurrentPreservedRef: hasPreservedRef,
        );
      }
      final serializer = resolved.serializer;
      if (serializer == null) {
        throw StateError('No serializer available for ${resolved.type}.');
      }
      return _readImpl(context).readSerializerPayload(
        serializer,
        resolved,
        hasCurrentPreservedRef: hasPreservedRef,
      );
  }
}

enum _PinnedResolvedPayloadKind {
  primitive,
  string,
  struct,
  serializer,
  generic,
}

final class _PinnedResolvedPayload {
  final ResolvedTypeInternal resolved;
  final TypeShapeInternal? declaredShape;
  final Serializer<Object?>? serializer;
  final _PinnedResolvedPayloadKind kind;

  const _PinnedResolvedPayload._(
    this.resolved,
    this.declaredShape,
    this.serializer,
    this.kind,
  );

  factory _PinnedResolvedPayload(
    ResolvedTypeInternal resolved, [
    TypeShapeInternal? declaredShape,
  ]) {
    if (TypeIds.isPrimitive(resolved.typeId)) {
      return _PinnedResolvedPayload._(
        resolved,
        declaredShape,
        null,
        _PinnedResolvedPayloadKind.primitive,
      );
    }
    if (resolved.typeId == TypeIds.string) {
      return _PinnedResolvedPayload._(
        resolved,
        declaredShape,
        null,
        _PinnedResolvedPayloadKind.string,
      );
    }
    if (resolved.kind == RegistrationKindInternal.struct) {
      return _PinnedResolvedPayload._(
        resolved,
        declaredShape,
        null,
        _PinnedResolvedPayloadKind.struct,
      );
    }
    final serializer = resolved.serializer;
    if (serializer != null) {
      return _PinnedResolvedPayload._(
        resolved,
        declaredShape,
        serializer,
        _PinnedResolvedPayloadKind.serializer,
      );
    }
    return _PinnedResolvedPayload._(
      resolved,
      declaredShape,
      null,
      _PinnedResolvedPayloadKind.generic,
    );
  }

  @pragma('vm:prefer-inline')
  void write(WriteContext context, Object value) {
    switch (kind) {
      case _PinnedResolvedPayloadKind.primitive:
        writePayloadPrimitive(context, resolved.typeId, value);
        return;
      case _PinnedResolvedPayloadKind.string:
        writeStringPayload(context, value as String);
        return;
      case _PinnedResolvedPayloadKind.struct:
        resolved.structCodec!.write(context, resolved, value);
        return;
      case _PinnedResolvedPayloadKind.serializer:
        serializer!.write(context, value);
        return;
      case _PinnedResolvedPayloadKind.generic:
        writePayloadValue(context, resolved, value, declaredShape);
        return;
    }
  }

  @pragma('vm:prefer-inline')
  Object? read(ReadContext context, {bool hasPreservedRef = false}) {
    switch (kind) {
      case _PinnedResolvedPayloadKind.primitive:
        return readPayloadPrimitive(context, resolved.typeId);
      case _PinnedResolvedPayloadKind.string:
        return readStringPayload(context);
      case _PinnedResolvedPayloadKind.struct:
        return resolved.structCodec!.read(
          context,
          resolved,
          hasCurrentPreservedRef: hasPreservedRef,
        );
      case _PinnedResolvedPayloadKind.serializer:
        return _readImpl(context).readSerializerPayload(
          serializer!,
          resolved,
          hasCurrentPreservedRef: hasPreservedRef,
        );
      case _PinnedResolvedPayloadKind.generic:
        return readPayloadValue(
          context,
          resolved,
          declaredShape,
          hasPreservedRef: hasPreservedRef,
        );
    }
  }
}

bool _tracksNestedPayloadDepth(ResolvedTypeInternal resolved) {
  if (TypeIds.isContainer(resolved.typeId)) {
    return true;
  }
  switch (resolved.kind) {
    case RegistrationKindInternal.builtin:
    case RegistrationKindInternal.enumType:
      return false;
    case RegistrationKindInternal.struct:
    case RegistrationKindInternal.ext:
    case RegistrationKindInternal.union:
      return true;
  }
}

@pragma('vm:prefer-inline')
void _writePinnedResolvedValue(
  WriteContext context,
  _PinnedResolvedPayload payload,
  Object value, {
  required bool trackRef,
}) {
  if (!trackRef) {
    payload.write(context, value);
    return;
  }
  final handled = _writeImpl(context).refWriter.writeRefOrNull(
        context.buffer,
        value,
        trackRef: payload.resolved.supportsRef,
      );
  if (!handled) {
    payload.write(context, value);
  }
}

@pragma('vm:prefer-inline')
Object? _readPinnedResolvedValue(
  ReadContext context,
  _PinnedResolvedPayload payload, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return payload.read(context);
  }
  final internal = _readImpl(context);
  final flag = internal.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.refFlag) {
    return internal.refReader.getReadRef();
  }
  final value = payload.read(
    context,
    hasPreservedRef: preservedRefId != null,
  );
  if (preservedRefId != null &&
      payload.resolved.supportsRef &&
      internal.refReader.readRefAt(preservedRefId) == null) {
    internal.refReader.setReadRef(preservedRefId, value);
  }
  return value;
}

void writeFixedArrayPayload(
  WriteContext context,
  TypedData values,
) {
  context.buffer.writeVarUint32(values.lengthInBytes);
  context.buffer.writeBytes(
    values.buffer.asUint8List(values.offsetInBytes, values.lengthInBytes),
  );
}

void _writeList(
  WriteContext context,
  Iterable values,
  TypeShapeInternal? elementShape, {
  required bool trackRef,
}) {
  final internal = _writeImpl(context);
  final size = values.length;
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  context.buffer.writeVarUint32(size);
  if (size == 0) {
    return;
  }
  final declaredResolved = elementShape == null ||
          elementShape.isDynamic ||
          elementShape.typeId == TypeIds.unknown
      ? null
      : internal.typeResolver.resolveShape(elementShape);
  final declaredType = declaredResolved != null &&
      usesDeclaredTypeInfo(
        context.config.compatible,
        elementShape!,
        declaredResolved,
      );
  final analysis = _analyzeListHeader(
    internal,
    values,
    declaredType: declaredType,
  );
  final elementTrackRef =
      (elementShape?.ref ?? false) || (elementShape == null && trackRef);
  var header = 0;
  if (elementTrackRef) {
    header |= 0x01;
  }
  if (analysis.hasNull) {
    header |= 0x02;
  }
  if (declaredType) {
    header |= 0x04;
  }
  if (analysis.sameType) {
    header |= 0x08;
  }
  context.buffer.writeUint8(header);
  final declaredBinding = declaredType
      ? internal.typeResolver.declaredValueBindingForShape(
          elementShape,
          identifier: 'item',
          nullable: analysis.hasNull,
          ref: elementTrackRef,
        )
      : null;
  final declaredPayload = declaredType
      ? _PinnedResolvedPayload(declaredResolved, elementShape)
      : null;
  final sameResolved =
      !declaredType && analysis.sameType ? analysis.sameResolved : null;
  final samePayload =
      sameResolved == null ? null : _PinnedResolvedPayload(sameResolved);
  if (!declaredType &&
      sameResolved != null &&
      analysis.firstNonNull != null) {
    final firstNonNull = analysis.firstNonNull!;
    internal.writeTypeMetaValue(
      sameResolved,
      firstNonNull,
    );
  }
  if (declaredPayload != null) {
    final tracksDepth = _tracksNestedPayloadDepth(declaredPayload.resolved);
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (final value in values) {
      if (value == null) {
        context.buffer.writeByte(RefWriter.nullFlag);
        continue;
      }
      if (elementTrackRef) {
        _writePinnedResolvedValue(
          context,
          declaredPayload,
          value as Object,
          trackRef: true,
        );
      } else if (analysis.hasNull) {
        context.buffer.writeByte(RefWriter.notNullValueFlag);
        declaredPayload.write(context, value as Object);
      } else {
        declaredPayload.write(context, value as Object);
      }
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    return;
  }
  if (samePayload != null) {
    final tracksDepth = _tracksNestedPayloadDepth(sameResolved!);
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (final value in values) {
      if (value == null) {
        context.buffer.writeByte(RefWriter.nullFlag);
      } else if (elementTrackRef) {
        _writePinnedResolvedValue(
          context,
          samePayload,
          value as Object,
          trackRef: true,
        );
      } else if (analysis.hasNull) {
        context.buffer.writeByte(RefWriter.notNullValueFlag);
        samePayload.write(context, value as Object);
      } else {
        samePayload.write(context, value as Object);
      }
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    return;
  }
  for (final value in values) {
    if (declaredType) {
      if (elementTrackRef || analysis.hasNull) {
        writeDeclaredValueBinding(context, declaredBinding!, value);
      } else {
        writePayloadValue(
          context,
          declaredResolved,
          value as Object,
          elementShape,
        );
      }
      continue;
    }
    if (analysis.sameType && analysis.sameResolved != null) {
      if (value == null) {
        context.buffer.writeByte(RefWriter.nullFlag);
      } else if (elementTrackRef) {
        final handled = internal.refWriter.writeRefOrNull(
          context.buffer,
          value,
          trackRef: analysis.sameResolved!.supportsRef,
        );
        if (!handled) {
          writePayloadValue(
            context,
            analysis.sameResolved!,
            value as Object,
            null,
          );
        }
      } else if (analysis.hasNull) {
        context.buffer.writeByte(RefWriter.notNullValueFlag);
        writePayloadValue(
          context,
          analysis.sameResolved!,
          value as Object,
          null,
        );
      } else {
        writePayloadValue(
          context,
          analysis.sameResolved!,
          value as Object,
          null,
        );
      }
      continue;
    }
    if (elementTrackRef) {
      context.writeRef(value);
    } else if (analysis.hasNull) {
      if (value == null) {
        context.buffer.writeByte(RefWriter.nullFlag);
      } else {
        context.buffer.writeByte(RefWriter.notNullValueFlag);
        context.writeNonRef(value as Object);
      }
    } else {
      context.writeNonRef(value as Object);
    }
  }
}

void _writeMap(
  WriteContext context,
  Map values,
  TypeShapeInternal? keyShape,
  TypeShapeInternal? valueShape, {
  required bool trackRef,
}) {
  final internal = _writeImpl(context);
  if (values.length > context.config.maxCollectionSize) {
    throw StateError(
      'Map size ${values.length} exceeds ${context.config.maxCollectionSize}.',
    );
  }
  context.buffer.writeVarUint32(values.length);
  final declaredKeyResolved = keyShape == null || keyShape.isDynamic
      ? null
      : internal.typeResolver.resolveShape(keyShape);
  final declaredValueResolved = valueShape == null || valueShape.isDynamic
      ? null
      : internal.typeResolver.resolveShape(valueShape);
  final keyDeclared = declaredKeyResolved != null &&
      usesDeclaredTypeInfo(
          context.config.compatible, keyShape!, declaredKeyResolved);
  final valueDeclared = declaredValueResolved != null &&
      usesDeclaredTypeInfo(
        context.config.compatible,
        valueShape!,
        declaredValueResolved,
      );
  final keyRequestedRef =
      (keyShape?.ref ?? false) || (keyShape == null && trackRef);
  final valueRequestedRef =
      (valueShape?.ref ?? false) || (valueShape == null && trackRef);
  final declaredKeyPayload =
      keyDeclared ? _PinnedResolvedPayload(declaredKeyResolved, keyShape) : null;
  final declaredValuePayload = valueDeclared
      ? _PinnedResolvedPayload(declaredValueResolved, valueShape)
      : null;
  final iterator = values.entries.iterator;
  MapEntry<dynamic, dynamic>? pendingEntry;
  var exhausted = false;

  while (!exhausted) {
    MapEntry<dynamic, dynamic>? entry;
    if (pendingEntry != null) {
      entry = pendingEntry;
      pendingEntry = null;
    } else {
      if (!iterator.moveNext()) {
        exhausted = true;
        break;
      }
      entry = iterator.current;
    }
    final key = entry.key;
    final value = entry.value;
    if (key == null || value == null) {
      final keyTrackRef = keyRequestedRef &&
          (keyDeclared
              ? declaredKeyResolved.supportsRef
              : (key == null ||
                  internal.typeResolver
                      .resolveValue(key as Object)
                      .supportsRef));
      final valueTrackRef = valueRequestedRef &&
          (valueDeclared
              ? declaredValueResolved.supportsRef
              : (value == null ||
                  internal.typeResolver
                      .resolveValue(
                        value as Object,
                      )
                      .supportsRef));
      _writeNullMapChunk(
        context,
        key,
        value,
        keyTrackRef: keyTrackRef,
        valueTrackRef: valueTrackRef,
        keyPayload: declaredKeyPayload,
        valuePayload: declaredValuePayload,
      );
      continue;
    }
    final chunkKeyResolved = keyDeclared
        ? declaredKeyResolved
        : internal.typeResolver.resolveValue(key as Object);
    final chunkValueResolved = valueDeclared
        ? declaredValueResolved
        : internal.typeResolver.resolveValue(value as Object);
    final chunkKeyTrackRef = keyRequestedRef &&
        (keyDeclared
            ? declaredKeyResolved.supportsRef
            : chunkKeyResolved.supportsRef);
    final chunkValueTrackRef = valueRequestedRef &&
        (valueDeclared
            ? declaredValueResolved.supportsRef
            : chunkValueResolved.supportsRef);
    context.buffer.writeUint8(
      _mapChunkHeader(
        keyDeclared: keyDeclared,
        valueDeclared: valueDeclared,
        keyTrackRef: chunkKeyTrackRef,
        valueTrackRef: chunkValueTrackRef,
      ),
    );
    context.buffer.writeUint8(0);
    final chunkLengthOffset = bufferWriterIndexInternal(context.buffer) - 1;
    if (!keyDeclared) {
      internal.writeTypeMetaValue(chunkKeyResolved, key);
    }
    if (!valueDeclared) {
      internal.writeTypeMetaValue(chunkValueResolved, value);
    }
    final keyPayload = keyDeclared
        ? declaredKeyPayload
        : _PinnedResolvedPayload(chunkKeyResolved);
    final valuePayload = valueDeclared
        ? declaredValuePayload
        : _PinnedResolvedPayload(chunkValueResolved);
    final chunkKeyPayload = keyPayload!;
    final chunkValuePayload = valuePayload!;
    var chunkLength = 1;
    final tracksDepth = _tracksNestedPayloadDepth(chunkKeyPayload.resolved) ||
        _tracksNestedPayloadDepth(chunkValuePayload.resolved);
    if (tracksDepth) {
      context.increaseDepth();
    }
    _writeMapValuePair(
      context,
      key as Object,
      value as Object,
      keyTrackRef: chunkKeyTrackRef,
      valueTrackRef: chunkValueTrackRef,
      keyPayload: chunkKeyPayload,
      valuePayload: chunkValuePayload,
    );
    while (chunkLength < 255) {
      if (!iterator.moveNext()) {
        exhausted = true;
        break;
      }
      final nextEntry = iterator.current;
      final nextKey = nextEntry.key;
      final nextValue = nextEntry.value;
      if (nextKey == null || nextValue == null) {
        pendingEntry = nextEntry;
        break;
      }
      final nextKeyResolved = keyDeclared
          ? declaredKeyResolved
          : internal.typeResolver.resolveValue(nextKey as Object);
      final nextValueResolved = valueDeclared
          ? declaredValueResolved
          : internal.typeResolver.resolveValue(nextValue as Object);
      final nextKeyTrackRef = keyRequestedRef &&
          (keyDeclared
              ? declaredKeyResolved.supportsRef
              : nextKeyResolved.supportsRef);
      final nextValueTrackRef = valueRequestedRef &&
          (valueDeclared
              ? declaredValueResolved.supportsRef
              : nextValueResolved.supportsRef);
      if (nextKeyTrackRef != chunkKeyTrackRef ||
          nextValueTrackRef != chunkValueTrackRef ||
          (!keyDeclared &&
              !_sameResolvedType(chunkKeyResolved, nextKeyResolved)) ||
          (!valueDeclared &&
              !_sameResolvedType(chunkValueResolved, nextValueResolved))) {
        pendingEntry = nextEntry;
        break;
      }
      _writeMapValuePair(
        context,
        nextKey,
        nextValue,
        keyTrackRef: chunkKeyTrackRef,
        valueTrackRef: chunkValueTrackRef,
        keyPayload: chunkKeyPayload,
        valuePayload: chunkValuePayload,
      );
      chunkLength += 1;
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    bufferWriteUint8AtInternal(context.buffer, chunkLengthOffset, chunkLength);
  }
}

void _writeNullMapChunk(
  WriteContext context,
  Object? key,
  Object? value, {
  required bool keyTrackRef,
  required bool valueTrackRef,
  required _PinnedResolvedPayload? keyPayload,
  required _PinnedResolvedPayload? valuePayload,
}) {
  var header = 0;
  if (key == null) {
    header |= 0x02;
  } else if (keyPayload != null) {
    header |= 0x04;
  }
  if (keyTrackRef) {
    header |= 0x01;
  }
  if (value == null) {
    header |= 0x10;
  } else if (valuePayload != null) {
    header |= 0x20;
  }
  if (valueTrackRef) {
    header |= 0x08;
  }
  context.buffer.writeUint8(header);
  if (key != null) {
    if (keyPayload != null) {
      _writePinnedResolvedValue(
        context,
        keyPayload,
        key,
        trackRef: keyTrackRef,
      );
    } else if (keyTrackRef) {
      context.writeRef(key);
    } else {
      context.writeNonRef(key);
    }
  }
  if (value != null) {
    if (valuePayload != null) {
      _writePinnedResolvedValue(
        context,
        valuePayload,
        value,
        trackRef: valueTrackRef,
      );
    } else if (valueTrackRef) {
      context.writeRef(value);
    } else {
      context.writeNonRef(value);
    }
  }
}

void _writeMapValuePair(
  WriteContext context,
  Object key,
  Object value, {
  required bool keyTrackRef,
  required bool valueTrackRef,
  required _PinnedResolvedPayload keyPayload,
  required _PinnedResolvedPayload valuePayload,
}) {
  _writePinnedResolvedValue(
    context,
    keyPayload,
    key,
    trackRef: keyTrackRef,
  );
  _writePinnedResolvedValue(
    context,
    valuePayload,
    value,
    trackRef: valueTrackRef,
  );
}

T readTypedArrayPayload<T>(
  ReadContext context,
  int elementSize,
  T Function(Uint8List bytes) viewBuilder,
) {
  final byteSize = context.buffer.readVarUint32();
  if (byteSize % elementSize != 0) {
    throw StateError(
      'Typed array byte size $byteSize is not aligned to element size $elementSize.',
    );
  }
  var bytes = context.buffer.readBytes(byteSize);
  if (bytes.offsetInBytes % elementSize != 0) {
    bytes = Uint8List.fromList(bytes);
  }
  return viewBuilder(bytes);
}

List<T> readTypedListPayload<T>(
  ReadContext context,
  TypeShapeInternal? elementShape,
  T Function(Object? value) convert,
) {
  final state = _prepareListRead(context, elementShape);
  if (state.size == 0) {
    return List<T>.empty(growable: false);
  }
  if (state.tracksDepth) {
    context.increaseDepth();
  }
  final result = List<T>.generate(
    state.size,
    (_) => convert(_readPreparedListItem(context, state)),
    growable: false,
  );
  if (state.tracksDepth) {
    context.decreaseDepth();
  }
  return result;
}

Set<T> readTypedSetPayload<T>(
  ReadContext context,
  TypeShapeInternal? elementShape,
  T Function(Object? value) convert,
) {
  return Set<T>.of(readTypedListPayload(context, elementShape, convert));
}

Map<K, V> readTypedMapPayload<K, V>(
  ReadContext context,
  TypeShapeInternal? keyShape,
  TypeShapeInternal? valueShape,
  K Function(Object? value) convertKey,
  V Function(Object? value) convertValue,
) {
  final internal = _readImpl(context);
  var remaining = context.buffer.readVarUint32();
  if (remaining > context.config.maxCollectionSize) {
    throw StateError(
      'Map size $remaining exceeds ${context.config.maxCollectionSize}.',
    );
  }
  final declaredKeyPayload = keyShape == null || keyShape.isDynamic
      ? null
      : _PinnedResolvedPayload(
          internal.typeResolver.resolveShape(keyShape),
          keyShape,
        );
  final declaredValuePayload = valueShape == null || valueShape.isDynamic
      ? null
      : _PinnedResolvedPayload(
          internal.typeResolver.resolveShape(valueShape),
          valueShape,
        );
  final result = <K, V>{};
  while (remaining > 0) {
    final header = context.buffer.readUint8();
    final keyHasNull = (header & 0x02) != 0;
    final valueHasNull = (header & 0x10) != 0;
    if (keyHasNull || valueHasNull) {
      result[convertKey(
        _readNullChunkKey(
          context,
          header,
          keyShape,
          declaredKeyPayload,
        ),
      )] = convertValue(
        _readNullChunkValue(
          context,
          header,
          valueShape,
          declaredValuePayload,
        ),
      );
      remaining -= 1;
      continue;
    }
    final keyTrackRef = (header & 0x01) != 0;
    final valueTrackRef = (header & 0x08) != 0;
    final keyDeclared = (header & 0x04) != 0;
    final valueDeclared = (header & 0x20) != 0;
    final chunkSize = context.buffer.readUint8();
    final keyResolved = keyDeclared ? null : internal.readTypeMetaValue();
    final valueResolved = valueDeclared ? null : internal.readTypeMetaValue();
    final keyPayload = keyDeclared
        ? declaredKeyPayload
        : keyResolved == null
            ? null
            : _PinnedResolvedPayload(keyResolved);
    final valuePayload = valueDeclared
        ? declaredValuePayload
        : valueResolved == null
            ? null
            : _PinnedResolvedPayload(valueResolved);
    final tracksDepth = (keyPayload != null &&
            _tracksNestedPayloadDepth(keyPayload.resolved)) ||
        (valuePayload != null && _tracksNestedPayloadDepth(valuePayload.resolved));
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (var index = 0; index < chunkSize; index += 1) {
      final key = keyDeclared
          ? _readDeclaredMapValue(
              context,
              keyShape!,
              keyPayload!,
              trackRef: keyTrackRef,
            )
          : _readResolvedMapValue(
              context,
              keyPayload!,
              trackRef: keyTrackRef,
            );
      final value = valueDeclared
          ? _readDeclaredMapValue(
              context,
              valueShape!,
              valuePayload!,
              trackRef: valueTrackRef,
            )
          : _readResolvedMapValue(
              context,
              valuePayload!,
              trackRef: valueTrackRef,
            );
      result[convertKey(key)] = convertValue(value);
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    remaining -= chunkSize;
  }
  return result;
}

List<Object?> _readList(
  ReadContext context,
  TypeShapeInternal? elementShape,
) {
  final state = _prepareListRead(context, elementShape);
  final result = List<Object?>.filled(state.size, null, growable: false);
  if (state.size == 0) {
    return result;
  }
  if (state.tracksDepth) {
    context.increaseDepth();
  }
  for (var index = 0; index < state.size; index += 1) {
    result[index] = _readPreparedListItem(context, state);
  }
  if (state.tracksDepth) {
    context.decreaseDepth();
  }
  return result;
}

Set<Object?> _readSet(
  ReadContext context,
  TypeShapeInternal? elementShape,
) {
  return Set<Object?>.of(_readList(context, elementShape));
}

Map<Object?, Object?> _readMap(
  ReadContext context,
  TypeShapeInternal? keyShape,
  TypeShapeInternal? valueShape,
) {
  return readTypedMapPayload<Object?, Object?>(
    context,
    keyShape,
    valueShape,
    (value) => value,
    (value) => value,
  );
}

final class _PreparedListRead {
  final int size;
  final bool trackRef;
  final bool hasNull;
  final bool declaredType;
  final TypeShapeInternal? elementShape;
  final DeclaredValueBindingInternal? declaredBinding;
  final ResolvedTypeInternal? declaredDirectResolved;
  final _PinnedResolvedPayload? declaredPayload;
  final ResolvedTypeInternal? sameResolved;
  final _PinnedResolvedPayload? samePayload;
  final bool tracksDepth;

  const _PreparedListRead({
    required this.size,
    required this.trackRef,
    required this.hasNull,
    required this.declaredType,
    required this.elementShape,
    required this.declaredBinding,
    required this.declaredDirectResolved,
    required this.declaredPayload,
    required this.sameResolved,
    required this.samePayload,
    required this.tracksDepth,
  });
}

_PreparedListRead _prepareListRead(
  ReadContext context,
  TypeShapeInternal? elementShape,
) {
  final internal = _readImpl(context);
  final size = context.buffer.readVarUint32();
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  if (size == 0) {
    return _PreparedListRead(
      size: 0,
      trackRef: false,
      hasNull: false,
      declaredType: false,
      elementShape: elementShape,
      declaredBinding: null,
      declaredDirectResolved: null,
      declaredPayload: null,
      sameResolved: null,
      samePayload: null,
      tracksDepth: false,
    );
  }
  final header = context.buffer.readUint8();
  final trackRef = (header & 0x01) == 1;
  final hasNull = (header & 0x02) != 0;
  final declaredType = (header & 0x04) != 0;
  final sameType = (header & 0x08) != 0;
  final declaredBinding = declaredType && elementShape != null
      ? internal.typeResolver.declaredValueBindingForShape(
          elementShape,
          identifier: 'item',
          nullable: hasNull,
          ref: trackRef,
        )
      : null;
  final declaredDirectResolved = declaredBinding?.resolved;
  final sameResolved = (!declaredType && sameType)
      ? internal.readTypeMetaValue()
      : null;
  final declaredPayload = declaredDirectResolved == null
      ? null
      : _PinnedResolvedPayload(declaredDirectResolved, elementShape);
  final samePayload =
      sameResolved == null ? null : _PinnedResolvedPayload(sameResolved);
  final tracksDepth =
      (declaredPayload != null &&
              _tracksNestedPayloadDepth(declaredDirectResolved!)) ||
          (samePayload != null && _tracksNestedPayloadDepth(sameResolved!));
  return _PreparedListRead(
    size: size,
    trackRef: trackRef,
    hasNull: hasNull,
    declaredType: declaredType,
    elementShape: elementShape,
    declaredBinding: declaredBinding,
    declaredDirectResolved: declaredDirectResolved,
    declaredPayload: declaredPayload,
    sameResolved: sameResolved,
    samePayload: samePayload,
    tracksDepth: tracksDepth,
  );
}

@pragma('vm:prefer-inline')
Object? _readPreparedListItem(
  ReadContext context,
  _PreparedListRead state,
) {
  final internal = _readImpl(context);
  final declaredPayload = state.declaredPayload;
  if (declaredPayload != null) {
    if (state.hasNull || state.trackRef) {
      final flag = internal.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return internal.refReader.getReadRef();
      }
      final value = declaredPayload.read(
        context,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.declaredDirectResolved!.supportsRef &&
          internal.refReader.readRefAt(preservedRefId) == null) {
        internal.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return declaredPayload.read(context);
  }
  final samePayload = state.samePayload;
  if (samePayload != null) {
    if (state.hasNull || state.trackRef) {
      final flag = internal.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return internal.refReader.getReadRef();
      }
      final value = samePayload.read(
        context,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.sameResolved!.supportsRef &&
          internal.refReader.readRefAt(preservedRefId) == null) {
        internal.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return samePayload.read(context);
  }
  if (state.declaredType && state.elementShape != null) {
    if (!state.trackRef && !state.hasNull) {
      return state.declaredBinding!.shape.isPrimitive
          ? internal.readPrimitiveValue(state.declaredBinding!.shape.typeId)
          : internal.readResolvedValue(
              state.declaredDirectResolved!,
              state.elementShape,
            );
    }
    return readDeclaredValueBinding<Object?>(
      context,
      state.declaredBinding!,
    );
  }
  if (state.sameResolved != null) {
    if (state.hasNull || state.trackRef) {
      final flag = internal.refReader.tryPreserveRefId(context.buffer);
      final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
      if (flag == RefWriter.nullFlag) {
        return null;
      }
      if (flag == RefWriter.refFlag) {
        return internal.refReader.getReadRef();
      }
      final value = readPayloadValue(
        context,
        state.sameResolved!,
        null,
        hasPreservedRef: preservedRefId != null,
      );
      if (preservedRefId != null &&
          state.sameResolved!.supportsRef &&
          internal.refReader.readRefAt(preservedRefId) == null) {
        internal.refReader.setReadRef(preservedRefId, value);
      }
      return value;
    }
    return readPayloadValue(context, state.sameResolved!, null);
  }
  if (state.trackRef) {
    return context.readRef();
  }
  if (state.hasNull) {
    return context.readNullable();
  }
  return context.readNonRef();
}

Object? _readNullChunkKey(
  ReadContext context,
  int header,
  TypeShapeInternal? keyShape,
  _PinnedResolvedPayload? declaredKeyPayload,
) {
  final keyHasNull = (header & 0x02) != 0;
  if (keyHasNull) {
    return null;
  }
  final trackRef = (header & 0x01) != 0;
  final declared = (header & 0x04) != 0;
  if (declared && keyShape != null) {
    return _readDeclaredMapValue(
      context,
      keyShape,
      declaredKeyPayload!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readNullChunkValue(
  ReadContext context,
  int header,
  TypeShapeInternal? valueShape,
  _PinnedResolvedPayload? declaredValuePayload,
) {
  final valueHasNull = (header & 0x10) != 0;
  if (valueHasNull) {
    return null;
  }
  final trackRef = (header & 0x08) != 0;
  final declared = (header & 0x20) != 0;
  if (declared && valueShape != null) {
    return _readDeclaredMapValue(
      context,
      valueShape,
      declaredValuePayload!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readDeclaredMapValue(
  ReadContext context,
  TypeShapeInternal shape,
  _PinnedResolvedPayload payload, {
  required bool trackRef,
}) {
  if (!trackRef && !shape.nullable) {
    return payload.read(context);
  }
  return readDeclaredValue<Object?>(
    context,
    fieldMetadata(
      shape,
      name: 'entry',
      identifier: 'entry',
      ref: trackRef,
    ),
  );
}

Object? _readResolvedMapValue(
  ReadContext context,
  _PinnedResolvedPayload payload, {
  required bool trackRef,
}) {
  return _readPinnedResolvedValue(
    context,
    payload,
    trackRef: trackRef,
  );
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
    return left.namespace == right.namespace && left.typeName == right.typeName;
  }
  return true;
}

_ListHeaderAnalysis _analyzeListHeader(
  WriteContext context,
  Iterable values, {
  required bool declaredType,
}) {
  var hasNull = false;
  if (declaredType) {
    for (final value in values) {
      if (value == null) {
        hasNull = true;
        break;
      }
    }
    return _ListHeaderAnalysis(
      hasNull: hasNull,
      sameType: true,
      sameResolved: null,
      firstNonNull: null,
    );
  }
  Object? firstNonNull;
  ResolvedTypeInternal? sameResolved;
  Type? firstRuntimeType;
  var sameType = true;
  for (final value in values) {
    if (value == null) {
      hasNull = true;
      continue;
    }
    if (firstNonNull == null) {
      firstNonNull = value;
      firstRuntimeType = value.runtimeType;
      sameResolved = context.typeResolver.resolveValue(value as Object);
      continue;
    }
    if (!sameType) {
      continue;
    }
    if (value.runtimeType != firstRuntimeType) {
      sameType = false;
    }
  }
  return _ListHeaderAnalysis(
    hasNull: hasNull,
    sameType: sameType,
    sameResolved: sameResolved,
    firstNonNull: firstNonNull,
  );
}

final class _ListHeaderAnalysis {
  final bool hasNull;
  final bool sameType;
  final ResolvedTypeInternal? sameResolved;
  final Object? firstNonNull;

  const _ListHeaderAnalysis({
    required this.hasNull,
    required this.sameType,
    required this.sameResolved,
    required this.firstNonNull,
  });
}

int _mapChunkHeader({
  required bool keyDeclared,
  required bool valueDeclared,
  required bool keyTrackRef,
  required bool valueTrackRef,
}) {
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
  return header;
}

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(
  List<TypeShapeInternal>? values,
  int index,
) =>
    values == null || values.length <= index ? null : values[index];
