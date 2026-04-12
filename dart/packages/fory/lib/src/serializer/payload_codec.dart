import 'dart:typed_data';

import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/declared_value_codec.dart';
import 'package:fory/src/types/fixed_ints.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/local_date.dart';
import 'package:fory/src/types/timestamp.dart';
import 'package:fory/src/string_codec.dart';

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
  final encoded = encodeStringInternal(value);
  context.buffer
      .writeVarUint36Small((encoded.bytes.length << 2) | encoded.encoding);
  context.buffer.writeBytes(encoded.bytes);
}

void writePayloadValue(
  WriteContext context,
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
      _writeFixedArray(context, value as Int16List);
      return;
    case TypeIds.int32Array:
      _writeFixedArray(context, value as Int32List);
      return;
    case TypeIds.int64Array:
      _writeFixedArray(context, value as Int64List);
      return;
    case TypeIds.uint16Array:
      _writeFixedArray(context, value as Uint16List);
      return;
    case TypeIds.uint32Array:
      _writeFixedArray(context, value as Uint32List);
      return;
    case TypeIds.uint64Array:
      _writeFixedArray(context, value as Uint64List);
      return;
    case TypeIds.float32Array:
      _writeFixedArray(context, value as Float32List);
      return;
    case TypeIds.float64Array:
      _writeFixedArray(context, value as Float64List);
      return;
    case TypeIds.list:
      _writeList(
        context,
        value as List,
        _firstOrNull(declaredShape?.arguments),
        trackRef:
            declaredShape == null ? context.rootTrackRef : declaredShape.ref,
      );
      return;
    case TypeIds.set:
      _writeList(
        context,
        (value as Set).toList(growable: false),
        _firstOrNull(declaredShape?.arguments),
        trackRef:
            declaredShape == null ? context.rootTrackRef : declaredShape.ref,
      );
      return;
    case TypeIds.map:
      _writeMap(
        context,
        value as Map,
        _elementAtOrNull(declaredShape?.arguments, 0),
        _elementAtOrNull(declaredShape?.arguments, 1),
        trackRef:
            declaredShape == null ? context.rootTrackRef : declaredShape.ref,
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
        resolved.structRuntime!.write(context, resolved, value);
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
  return decodeStringInternal(context.buffer.readBytes(byteLength), encoding);
}

Object? readPayloadValue(
  ReadContext context,
  ResolvedTypeInternal resolved,
  TypeShapeInternal? declaredShape,
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
      return _readTypedArray<Int16List>(
        context,
        2,
        (bytes) => bytes.buffer.asInt16List(),
      );
    case TypeIds.int32Array:
      return _readTypedArray<Int32List>(
        context,
        4,
        (bytes) => bytes.buffer.asInt32List(),
      );
    case TypeIds.int64Array:
      return _readTypedArray<Int64List>(
        context,
        8,
        (bytes) => bytes.buffer.asInt64List(),
      );
    case TypeIds.uint16Array:
      return _readTypedArray<Uint16List>(
        context,
        2,
        (bytes) => bytes.buffer.asUint16List(),
      );
    case TypeIds.uint32Array:
      return _readTypedArray<Uint32List>(
        context,
        4,
        (bytes) => bytes.buffer.asUint32List(),
      );
    case TypeIds.uint64Array:
      return _readTypedArray<Uint64List>(
        context,
        8,
        (bytes) => bytes.buffer.asUint64List(),
      );
    case TypeIds.float32Array:
      return _readTypedArray<Float32List>(
        context,
        4,
        (bytes) => bytes.buffer.asFloat32List(),
      );
    case TypeIds.float64Array:
      return _readTypedArray<Float64List>(
        context,
        8,
        (bytes) => bytes.buffer.asFloat64List(),
      );
    case TypeIds.list:
      return _readList(
        context,
        _firstOrNull(declaredShape?.arguments),
      );
    case TypeIds.set:
      return Set<Object?>.of(
        _readList(
          context,
          _firstOrNull(declaredShape?.arguments),
        ),
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
        return resolved.structRuntime!.read(context, resolved);
      }
      final serializer = resolved.serializer;
      if (serializer == null) {
        throw StateError('No serializer available for ${resolved.type}.');
      }
      return context.readSerializerPayload(serializer, resolved);
  }
}

void _writeFixedArray(
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
  List values,
  TypeShapeInternal? elementShape, {
  required bool trackRef,
}) {
  if (values.length > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size ${values.length} exceeds ${context.config.maxCollectionSize}.',
    );
  }
  context.buffer.writeVarUint32(values.length);
  if (values.isEmpty) {
    return;
  }
  final hasNull = values.any((value) => value == null);
  final declaredType = elementShape != null &&
      !elementShape.isDynamic &&
      elementShape.typeId != TypeIds.unknown;
  final nonNull = values.where((value) => value != null).toList(growable: false);
  final sameResolved = nonNull.isEmpty
      ? null
      : context.typeResolver.resolveValue(nonNull.first as Object);
  final sameType = sameResolved == null
      ? true
      : nonNull.every(
          (value) => _sameResolvedType(
            context.typeResolver.resolveValue(value as Object),
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
  context.buffer.writeUint8(header);
  if (!declaredType && sameType && sameResolved != null) {
    context.writeTypeMetaValue(sameResolved, nonNull.first as Object);
  }
  for (final value in values) {
    if (declaredType) {
      if (elementTrackRef || hasNull) {
        writeDeclaredValue(
          context,
          fieldMetadata(
            elementShape,
            name: 'item',
            identifier: 'item',
            nullable: hasNull,
            ref: elementTrackRef,
          ),
          value,
        );
      } else {
        final resolved = context.typeResolver.resolveShape(elementShape);
        writePayloadValue(context, resolved, value as Object, elementShape);
      }
      continue;
    }
    if (sameType && sameResolved != null) {
      if (value == null) {
        context.buffer.writeByte(RefWriter.nullFlag);
      } else if (elementTrackRef) {
        final handled = context.refWriter.writeRefOrNull(
          context.buffer,
          value,
          trackRef: sameResolved.supportsRef,
        );
        if (!handled) {
          writePayloadValue(context, sameResolved, value as Object, null);
        }
      } else if (hasNull) {
        context.buffer.writeByte(RefWriter.notNullValueFlag);
        writePayloadValue(context, sameResolved, value as Object, null);
      } else {
        writePayloadValue(context, sameResolved, value as Object, null);
      }
      continue;
    }
    if (elementTrackRef) {
      context.writeRef(value);
    } else if (hasNull) {
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
  if (values.length > context.config.maxCollectionSize) {
    throw StateError(
      'Map size ${values.length} exceeds ${context.config.maxCollectionSize}.',
    );
  }
  context.buffer.writeVarUint32(values.length);
  final declaredKeySupportsRef = keyShape == null
      ? null
      : context.typeResolver.resolveShape(keyShape).supportsRef;
  final declaredValueSupportsRef = valueShape == null
      ? null
      : context.typeResolver.resolveShape(valueShape).supportsRef;
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
            ? declaredKeySupportsRef!
            : (key == null ||
                context.typeResolver.resolveValue(key as Object).supportsRef));
    final valueTrackRef = valueRequestedRef &&
        (valueDeclared
            ? declaredValueSupportsRef!
            : (value == null ||
                context.typeResolver.resolveValue(value as Object).supportsRef));
    if (key == null || value == null) {
      _writeNullMapChunk(
        context,
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
    context.buffer.writeUint8(header);
    context.buffer.writeUint8(1);
    ResolvedTypeInternal? keyResolved;
    if (!keyDeclared) {
      keyResolved = context.typeResolver.resolveValue(key as Object);
      context.writeTypeMetaValue(keyResolved, key);
    }
    ResolvedTypeInternal? valueResolved;
    if (!valueDeclared) {
      valueResolved = context.typeResolver.resolveValue(value as Object);
      context.writeTypeMetaValue(valueResolved, value);
    }
    if (keyDeclared) {
      writeDeclaredValue(
        context,
        fieldMetadata(
          keyShape,
          name: 'entry',
          identifier: 'entry',
          ref: keyTrackRef,
        ),
        key,
      );
    } else {
      _writeResolvedMapValue(
        context,
        key,
        keyResolved!,
        trackRef: keyTrackRef,
      );
    }
    if (valueDeclared) {
      writeDeclaredValue(
        context,
        fieldMetadata(
          valueShape,
          name: 'entry',
          identifier: 'entry',
          ref: valueTrackRef,
        ),
        value,
      );
    } else {
      _writeResolvedMapValue(
        context,
        value,
        valueResolved!,
        trackRef: valueTrackRef,
      );
    }
  }
}

void _writeNullMapChunk(
  WriteContext context,
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
  context.buffer.writeUint8(header);
  if (key != null) {
    if (keyDeclared) {
      writeDeclaredValue(
        context,
        fieldMetadata(
          keyShape!,
          name: 'entry',
          identifier: 'entry',
          ref: keyTrackRef,
        ),
        key,
      );
    } else if (keyTrackRef) {
      context.writeRef(key);
    } else {
      context.writeNonRef(key);
    }
  }
  if (value != null) {
    if (valueDeclared) {
      writeDeclaredValue(
        context,
        fieldMetadata(
          valueShape!,
          name: 'entry',
          identifier: 'entry',
          ref: valueTrackRef,
        ),
        value,
      );
    } else if (valueTrackRef) {
      context.writeRef(value);
    } else {
      context.writeNonRef(value);
    }
  }
}

void _writeResolvedMapValue(
  WriteContext context,
  Object value,
  ResolvedTypeInternal resolved, {
  required bool trackRef,
}) {
  if (trackRef) {
    final handled = context.refWriter.writeRefOrNull(
      context.buffer,
      value,
      trackRef: resolved.supportsRef,
    );
    if (handled) {
      return;
    }
  }
  writePayloadValue(context, resolved, value, null);
}

T _readTypedArray<T>(
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
  final bytes = Uint8List.fromList(context.buffer.readBytes(byteSize));
  return viewBuilder(bytes);
}

List<Object?> _readList(
  ReadContext context,
  TypeShapeInternal? elementShape,
) {
  final size = context.buffer.readVarUint32();
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  if (size == 0) {
    return <Object?>[];
  }
  final header = context.buffer.readUint8();
  final trackRef = (header & 0x01) == 1;
  final hasNull = (header & 0x02) != 0;
  final declaredType = (header & 0x04) != 0;
  final sameType = (header & 0x08) != 0;
  final result = <Object?>[];
  final sameResolved =
      (!declaredType && sameType && size > 0) ? context.readTypeMetaValue() : null;
  for (var index = 0; index < size; index += 1) {
    if (declaredType && elementShape != null) {
      result.add(
        readDeclaredValue<Object?>(
          context,
          fieldMetadata(
            elementShape,
            name: 'item',
            identifier: 'item',
            nullable: hasNull,
            ref: trackRef,
          ),
        ),
      );
      continue;
    }
    if (sameType && sameResolved != null) {
      if (hasNull || trackRef) {
        final flag = context.refReader.tryPreserveRefId(context.buffer);
        final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
        if (flag == RefWriter.nullFlag) {
          result.add(null);
          continue;
        }
        if (flag == RefWriter.refFlag) {
          result.add(context.refReader.getReadRef());
          continue;
        }
        final value = readPayloadValue(context, sameResolved, null);
        if (preservedRefId != null &&
            sameResolved.supportsRef &&
            context.refReader.readRefAt(preservedRefId) == null) {
          context.refReader.setReadRef(preservedRefId, value);
        }
        result.add(value);
      } else {
        result.add(readPayloadValue(context, sameResolved, null));
      }
      continue;
    }
    if (trackRef) {
      result.add(context.readRef());
    } else if (hasNull) {
      result.add(context.readNullable());
    } else {
      result.add(context.readNonRef());
    }
  }
  return result;
}

Map<Object?, Object?> _readMap(
  ReadContext context,
  TypeShapeInternal? keyShape,
  TypeShapeInternal? valueShape,
) {
  var remaining = context.buffer.readVarUint32();
  if (remaining > context.config.maxCollectionSize) {
    throw StateError(
      'Map size $remaining exceeds ${context.config.maxCollectionSize}.',
    );
  }
  final result = <Object?, Object?>{};
  while (remaining > 0) {
    final header = context.buffer.readUint8();
    final keyHasNull = (header & 0x02) != 0;
    final valueHasNull = (header & 0x10) != 0;
    if (keyHasNull || valueHasNull) {
      result[_readNullChunkKey(context, header, keyShape)] = _readNullChunkValue(
        context,
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
    final chunkSize = context.buffer.readUint8();
    final keyResolved = keyDeclared ? null : context.readTypeMetaValue();
    final valueResolved = valueDeclared ? null : context.readTypeMetaValue();
    for (var index = 0; index < chunkSize; index += 1) {
      final key = keyDeclared
          ? _readDeclaredMapValue(context, keyShape!, trackRef: keyTrackRef)
          : _readResolvedMapValue(
              context,
              keyResolved!,
              trackRef: keyTrackRef,
            );
      final value = valueDeclared
          ? _readDeclaredMapValue(context, valueShape!, trackRef: valueTrackRef)
          : _readResolvedMapValue(
              context,
              valueResolved!,
              trackRef: valueTrackRef,
            );
      result[key] = value;
    }
    remaining -= chunkSize;
  }
  return result;
}

Object? _readNullChunkKey(
  ReadContext context,
  int header,
  TypeShapeInternal? keyShape,
) {
  final keyHasNull = (header & 0x02) != 0;
  if (keyHasNull) {
    return null;
  }
  final trackRef = (header & 0x01) != 0;
  final declared = (header & 0x04) != 0;
  if (declared && keyShape != null) {
    return _readDeclaredMapValue(context, keyShape, trackRef: trackRef);
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readNullChunkValue(
  ReadContext context,
  int header,
  TypeShapeInternal? valueShape,
) {
  final valueHasNull = (header & 0x10) != 0;
  if (valueHasNull) {
    return null;
  }
  final trackRef = (header & 0x08) != 0;
  final declared = (header & 0x20) != 0;
  if (declared && valueShape != null) {
    return _readDeclaredMapValue(context, valueShape, trackRef: trackRef);
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readDeclaredMapValue(
  ReadContext context,
  TypeShapeInternal shape, {
  required bool trackRef,
}) {
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
  ResolvedTypeInternal resolved, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return readPayloadValue(context, resolved, null);
  }
  final flag = context.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.refFlag) {
    return context.refReader.getReadRef();
  }
  final value = readPayloadValue(context, resolved, null);
  if (preservedRefId != null &&
      resolved.supportsRef &&
      context.refReader.readRefAt(preservedRefId) == null) {
    context.refReader.setReadRef(preservedRefId, value);
  }
  return value;
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

TypeShapeInternal? _firstOrNull(List<TypeShapeInternal>? values) =>
    values == null || values.isEmpty ? null : values.first;

TypeShapeInternal? _elementAtOrNull(
  List<TypeShapeInternal>? values,
  int index,
) =>
    values == null || values.length <= index ? null : values[index];
