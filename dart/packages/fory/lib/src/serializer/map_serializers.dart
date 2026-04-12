import 'package:fory/src/buffer.dart';
import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/collection_serializers.dart';
import 'package:fory/src/serializer/serializer.dart';

final class MapSerializer extends Serializer<Map> {
  const MapSerializer();

  @override
  void write(WriteContext context, Map value) {
    writePayload(
      context,
      value,
      null,
      null,
      trackRef: context.rootTrackRef,
    );
  }

  @override
  Map read(ReadContext context) {
    return readPayload(context, null, null);
  }

  static void writePayload(
    WriteContext context,
    Map values,
    FieldType? keyFieldType,
    FieldType? valueFieldType, {
    required bool trackRef,
  }) {
    if (values.length > context.config.maxCollectionSize) {
      throw StateError(
        'Map size ${values.length} exceeds ${context.config.maxCollectionSize}.',
      );
    }
    context.buffer.writeVarUint32(values.length);
    final declaredKeyTypeInfo = keyFieldType == null || keyFieldType.isDynamic
        ? null
        : context.typeResolver.resolveFieldType(keyFieldType);
    final declaredValueTypeInfo =
        valueFieldType == null || valueFieldType.isDynamic
            ? null
            : context.typeResolver.resolveFieldType(valueFieldType);
    final keyDeclared = declaredKeyTypeInfo != null &&
        usesDeclaredTypeInfo(
          context.config.compatible,
          keyFieldType!,
          declaredKeyTypeInfo,
        );
    final valueDeclared = declaredValueTypeInfo != null &&
        usesDeclaredTypeInfo(
          context.config.compatible,
          valueFieldType!,
          declaredValueTypeInfo,
        );
    final keyRequestedRef =
        (keyFieldType?.ref ?? false) || (keyFieldType == null && trackRef);
    final valueRequestedRef =
        (valueFieldType?.ref ?? false) || (valueFieldType == null && trackRef);
    final declaredKeyPayload = keyDeclared
        ? FixedTypePayload(declaredKeyTypeInfo, keyFieldType)
        : null;
    final declaredValuePayload = valueDeclared
        ? FixedTypePayload(declaredValueTypeInfo, valueFieldType)
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
                ? declaredKeyTypeInfo.supportsRef
                : (key == null ||
                    context.typeResolver
                        .resolveValue(key as Object)
                        .supportsRef));
        final valueTrackRef = valueRequestedRef &&
            (valueDeclared
                ? declaredValueTypeInfo.supportsRef
                : (value == null ||
                    context.typeResolver
                        .resolveValue(value as Object)
                        .supportsRef));
        _writeNullChunk(
          context,
          key,
          value,
          keyTrackRef: keyTrackRef,
          valueTrackRef: valueTrackRef,
          keyPayload: declaredKeyPayload,
          valuePayload: declaredValuePayload,
          keyFieldType: keyFieldType,
          valueFieldType: valueFieldType,
          declaredKeyTypeInfo: declaredKeyTypeInfo,
          declaredValueTypeInfo: declaredValueTypeInfo,
          keyDeclared: keyDeclared,
          valueDeclared: valueDeclared,
        );
        continue;
      }
      final chunkKeyTypeInfo = keyDeclared
          ? declaredKeyTypeInfo
          : context.typeResolver.resolveValue(key as Object);
      final chunkValueTypeInfo = valueDeclared
          ? declaredValueTypeInfo
          : context.typeResolver.resolveValue(value as Object);
      final chunkKeyTrackRef = keyRequestedRef && chunkKeyTypeInfo.supportsRef;
      final chunkValueTrackRef =
          valueRequestedRef && chunkValueTypeInfo.supportsRef;
      context.buffer.writeUint8(
        _mapChunkHeader(
          keyDeclared: keyDeclared,
          valueDeclared: valueDeclared,
          keyTrackRef: chunkKeyTrackRef,
          valueTrackRef: chunkValueTrackRef,
        ),
      );
      context.buffer.writeUint8(0);
      final chunkLengthOffset = bufferWriterIndex(context.buffer) - 1;
      if (!keyDeclared) {
        context.writeTypeMetaValue(chunkKeyTypeInfo, key);
      }
      if (!valueDeclared) {
        context.writeTypeMetaValue(chunkValueTypeInfo, value);
      }
      final keyPayload =
          keyDeclared ? declaredKeyPayload : FixedTypePayload(chunkKeyTypeInfo);
      final valuePayload = valueDeclared
          ? declaredValuePayload
          : FixedTypePayload(chunkValueTypeInfo);
      final chunkKeyPayload = keyPayload!;
      final chunkValuePayload = valuePayload!;
      var chunkLength = 1;
      final tracksDepth = tracksNestedPayloadDepth(
            chunkKeyPayload.typeInfo,
          ) ||
          tracksNestedPayloadDepth(chunkValuePayload.typeInfo);
      if (tracksDepth) {
        context.increaseDepth();
      }
      _writePair(
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
        final nextKeyTypeInfo = keyDeclared
            ? declaredKeyTypeInfo
            : context.typeResolver.resolveValue(nextKey as Object);
        final nextValueTypeInfo = valueDeclared
            ? declaredValueTypeInfo
            : context.typeResolver.resolveValue(nextValue as Object);
        final nextKeyTrackRef = keyRequestedRef && nextKeyTypeInfo.supportsRef;
        final nextValueTrackRef =
            valueRequestedRef && nextValueTypeInfo.supportsRef;
        if (nextKeyTrackRef != chunkKeyTrackRef ||
            nextValueTrackRef != chunkValueTrackRef ||
            (!keyDeclared &&
                !sameTypeInfo(chunkKeyTypeInfo, nextKeyTypeInfo)) ||
            (!valueDeclared &&
                !sameTypeInfo(
                  chunkValueTypeInfo,
                  nextValueTypeInfo,
                ))) {
          pendingEntry = nextEntry;
          break;
        }
        _writePair(
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
      bufferWriteUint8At(context.buffer, chunkLengthOffset, chunkLength);
    }
  }

  static Map<Object?, Object?> readPayload(
    ReadContext context,
    FieldType? keyFieldType,
    FieldType? valueFieldType,
  ) {
    return readTypedMapPayload<Object?, Object?>(
      context,
      keyFieldType,
      valueFieldType,
      (value) => value,
      (value) => value,
    );
  }
}

const MapSerializer mapSerializer = MapSerializer();

Map<K, V> readTypedMapPayload<K, V>(
  ReadContext context,
  FieldType? keyFieldType,
  FieldType? valueFieldType,
  K Function(Object? value) convertKey,
  V Function(Object? value) convertValue,
) {
  var remaining = context.buffer.readVarUint32();
  if (remaining > context.config.maxCollectionSize) {
    throw StateError(
      'Map size $remaining exceeds ${context.config.maxCollectionSize}.',
    );
  }
  final declaredKeyPayload = keyFieldType == null || keyFieldType.isDynamic
      ? null
      : FixedTypePayload(
          context.typeResolver.resolveFieldType(keyFieldType),
          keyFieldType,
        );
  final declaredValuePayload =
      valueFieldType == null || valueFieldType.isDynamic
          ? null
          : FixedTypePayload(
              context.typeResolver.resolveFieldType(valueFieldType),
              valueFieldType,
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
          keyFieldType,
          declaredKeyPayload,
        ),
      )] = convertValue(
        _readNullChunkValue(
          context,
          header,
          valueFieldType,
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
    final keyTypeInfo = keyDeclared ? null : context.readTypeMetaValue();
    final valueTypeInfo = valueDeclared ? null : context.readTypeMetaValue();
    final keyPayload = keyDeclared
        ? declaredKeyPayload
        : keyTypeInfo == null
            ? null
            : FixedTypePayload(keyTypeInfo);
    final valuePayload = valueDeclared
        ? declaredValuePayload
        : valueTypeInfo == null
            ? null
            : FixedTypePayload(valueTypeInfo);
    final tracksDepth =
        (keyPayload != null && tracksNestedPayloadDepth(keyPayload.typeInfo)) ||
            (valuePayload != null &&
                tracksNestedPayloadDepth(valuePayload.typeInfo));
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (var index = 0; index < chunkSize; index += 1) {
      final key = keyDeclared
          ? _readDeclaredMapValue(
              context,
              keyFieldType!,
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
              valueFieldType!,
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

void _writeNullChunk(
  WriteContext context,
  Object? key,
  Object? value, {
  required bool keyTrackRef,
  required bool valueTrackRef,
  required FixedTypePayload? keyPayload,
  required FixedTypePayload? valuePayload,
  required FieldType? keyFieldType,
  required FieldType? valueFieldType,
  required TypeInfo? declaredKeyTypeInfo,
  required TypeInfo? declaredValueTypeInfo,
  required bool keyDeclared,
  required bool valueDeclared,
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
    if (keyDeclared) {
      writeFixedTypeValue(
        context,
        keyPayload!,
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
    if (valueDeclared) {
      writeFixedTypeValue(
        context,
        valuePayload!,
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

void _writePair(
  WriteContext context,
  Object key,
  Object value, {
  required bool keyTrackRef,
  required bool valueTrackRef,
  required FixedTypePayload keyPayload,
  required FixedTypePayload valuePayload,
}) {
  writeFixedTypeValue(
    context,
    keyPayload,
    key,
    trackRef: keyTrackRef,
  );
  writeFixedTypeValue(
    context,
    valuePayload,
    value,
    trackRef: valueTrackRef,
  );
}

Object? _readNullChunkKey(
  ReadContext context,
  int header,
  FieldType? keyFieldType,
  FixedTypePayload? declaredKeyPayload,
) {
  final keyHasNull = (header & 0x02) != 0;
  if (keyHasNull) {
    return null;
  }
  final trackRef = (header & 0x01) != 0;
  final declared = (header & 0x04) != 0;
  if (declared && keyFieldType != null) {
    return _readDeclaredMapValue(
      context,
      keyFieldType,
      declaredKeyPayload!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readNullChunkValue(
  ReadContext context,
  int header,
  FieldType? valueFieldType,
  FixedTypePayload? declaredValuePayload,
) {
  final valueHasNull = (header & 0x10) != 0;
  if (valueHasNull) {
    return null;
  }
  final trackRef = (header & 0x08) != 0;
  final declared = (header & 0x20) != 0;
  if (declared && valueFieldType != null) {
    return _readDeclaredMapValue(
      context,
      valueFieldType,
      declaredValuePayload!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

FieldType _declaredMapFieldType(
  FieldType fieldType, {
  required bool trackRef,
}) {
  return fieldType.withRootOverrides(nullable: false, ref: trackRef);
}

Object? _readDeclaredMapValue(
  ReadContext context,
  FieldType fieldType,
  FixedTypePayload payload, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return payload.read(context);
  }
  return readFieldTypeValue<Object?>(
    context,
    _declaredMapFieldType(fieldType, trackRef: true),
    payload.typeInfo,
    true,
  );
}

Object? _readResolvedMapValue(
  ReadContext context,
  FixedTypePayload payload, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return payload.read(context);
  }
  final flag = context.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.refFlag) {
    return context.refReader.getReadRef();
  }
  final value = payload.read(
    context,
    hasPreservedRef: preservedRefId != null,
  );
  if (preservedRefId != null &&
      payload.typeInfo.supportsRef &&
      context.refReader.readRefAt(preservedRefId) == null) {
    context.refReader.setReadRef(preservedRefId, value);
  }
  return value;
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
