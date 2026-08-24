/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import 'package:fory/src/memory/buffer.dart';
import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/meta/field_type.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/collection_serializers.dart';
import 'package:fory/src/serializer/serializer.dart';

const int _referenceBytes = 4;
// Conservative lower bound for the retained Dart Map owner itself. Key/value slots are charged by
// entry count below; this is not a Fory wire header or a Dart VM layout probe.
const int _mapOwnerBytes = 8 * _referenceBytes;

abstract final class MapFlags {
  static const int trackingKeyRef = 0x01;
  static const int keyHasNull = 0x02;
  static const int keyDeclaredType = 0x04;
  static const int trackingValueRef = 0x08;
  static const int valueHasNull = 0x10;
  static const int valueDeclaredType = 0x20;
}

final class MapSerializer extends Serializer<Map> {
  const MapSerializer();

  @override
  void write(WriteContext context, Map value) {
    writePayload(context, value, null, null, trackRef: context.rootTrackRef);
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
    context.buffer.writeVarUint32(values.length);
    final declaredKeyTypeInfo =
        keyFieldType == null || keyFieldType.isDynamic
            ? null
            : context.typeResolver.resolveFieldType(keyFieldType);
    final declaredValueTypeInfo =
        valueFieldType == null || valueFieldType.isDynamic
            ? null
            : context.typeResolver.resolveFieldType(valueFieldType);
    final keyDeclared =
        declaredKeyTypeInfo != null &&
        usesDeclaredTypeInfo(
          context.config.compatible,
          keyFieldType!,
          declaredKeyTypeInfo,
        );
    final valueDeclared =
        declaredValueTypeInfo != null &&
        usesDeclaredTypeInfo(
          context.config.compatible,
          valueFieldType!,
          declaredValueTypeInfo,
        );
    final keyRequestedRef =
        (keyFieldType?.ref ?? false) || (keyFieldType == null && trackRef);
    final valueRequestedRef =
        (valueFieldType?.ref ?? false) || (valueFieldType == null && trackRef);
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
        final keyTrackRef =
            keyRequestedRef &&
            (keyDeclared
                ? declaredKeyTypeInfo.supportsRef
                : (key == null ||
                    context.typeResolver
                        .resolveValue(key as Object)
                        .supportsRef));
        final valueTrackRef =
            valueRequestedRef &&
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
          keyFieldType: keyFieldType,
          valueFieldType: valueFieldType,
          declaredKeyTypeInfo: declaredKeyTypeInfo,
          declaredValueTypeInfo: declaredValueTypeInfo,
          keyDeclared: keyDeclared,
          valueDeclared: valueDeclared,
        );
        continue;
      }
      final chunkKeyTypeInfo =
          keyDeclared
              ? declaredKeyTypeInfo
              : context.typeResolver.resolveValue(key as Object);
      final chunkValueTypeInfo =
          valueDeclared
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
        context.writeTypeMetaValue(chunkKeyTypeInfo);
      }
      if (!valueDeclared) {
        context.writeTypeMetaValue(chunkValueTypeInfo);
      }
      var chunkLength = 1;
      _writePair(
        context,
        key as Object,
        value as Object,
        keyTrackRef: chunkKeyTrackRef,
        valueTrackRef: chunkValueTrackRef,
        keyTypeInfo: chunkKeyTypeInfo,
        valueTypeInfo: chunkValueTypeInfo,
        keyFieldType: keyDeclared ? keyFieldType : null,
        valueFieldType: valueDeclared ? valueFieldType : null,
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
        final nextKeyTypeInfo =
            keyDeclared
                ? declaredKeyTypeInfo
                : context.typeResolver.resolveValue(nextKey as Object);
        final nextValueTypeInfo =
            valueDeclared
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
                !sameTypeInfo(chunkValueTypeInfo, nextValueTypeInfo))) {
          pendingEntry = nextEntry;
          break;
        }
        _writePair(
          context,
          nextKey,
          nextValue,
          keyTrackRef: chunkKeyTrackRef,
          valueTrackRef: chunkValueTrackRef,
          keyTypeInfo: chunkKeyTypeInfo,
          valueTypeInfo: chunkValueTypeInfo,
          keyFieldType: keyDeclared ? keyFieldType : null,
          valueFieldType: valueDeclared ? valueFieldType : null,
        );
        chunkLength += 1;
      }
      bufferWriteUint8At(context.buffer, chunkLengthOffset, chunkLength);
    }
  }

  static Map<Object?, Object?> readPayload(
    ReadContext context,
    FieldType? keyFieldType,
    FieldType? valueFieldType, {
    bool hasPreservedRef = false,
  }) {
    return readTypedMapPayload<Object?, Object?>(
      context,
      keyFieldType,
      valueFieldType,
      (value) => value,
      (value) => value,
      hasPreservedRef: hasPreservedRef,
    );
  }
}

const MapSerializer mapSerializer = MapSerializer();

Map<K, V> readTypedMapPayload<K, V>(
  ReadContext context,
  FieldType? keyFieldType,
  FieldType? valueFieldType,
  K Function(Object? value) convertKey,
  V Function(Object? value) convertValue, {
  bool hasPreservedRef = false,
}) {
  var remaining = context.buffer.readVarUint32();
  context.reserveGraphMemory(_mapOwnerBytes + remaining * 2 * _referenceBytes);
  final declaredKeyTypeInfo =
      keyFieldType == null || keyFieldType.isDynamic
          ? null
          : context.typeResolver.resolveFieldType(keyFieldType);
  final declaredValueTypeInfo =
      valueFieldType == null || valueFieldType.isDynamic
          ? null
          : context.typeResolver.resolveFieldType(valueFieldType);
  final result = <K, V>{};
  if (hasPreservedRef) {
    context.reference(result);
  }
  while (remaining > 0) {
    final header = context.buffer.readUint8();
    final keyHasNull = (header & MapFlags.keyHasNull) != 0;
    final valueHasNull = (header & MapFlags.valueHasNull) != 0;
    if (keyHasNull || valueHasNull) {
      result[convertKey(
        _readNullChunkKey(context, header, keyFieldType, declaredKeyTypeInfo),
      )] = convertValue(
        _readNullChunkValue(
          context,
          header,
          valueFieldType,
          declaredValueTypeInfo,
        ),
      );
      remaining -= 1;
      continue;
    }
    // IMPORTANT: map readers must obey the sender-written key/value ref bits
    // in the wire header. Local Dart field metadata must not override that
    // decision while reading. Shared xlang tests intentionally deserialize one
    // ref policy and then serialize another local payload. DO NOT REMOVE this
    // comment.
    final keyTrackRef = (header & MapFlags.trackingKeyRef) != 0;
    final valueTrackRef = (header & MapFlags.trackingValueRef) != 0;
    final keyDeclared = (header & MapFlags.keyDeclaredType) != 0;
    final valueDeclared = (header & MapFlags.valueDeclaredType) != 0;
    final chunkSize = context.buffer.readUint8();
    if (chunkSize == 0 || chunkSize > remaining) {
      _throwInvalidMapChunk(chunkSize, remaining);
    }
    final keyTypeInfo = keyDeclared ? null : context.readTypeMetaValue();
    final valueTypeInfo = valueDeclared ? null : context.readTypeMetaValue();
    final resolvedKeyTypeInfo = keyDeclared ? declaredKeyTypeInfo : keyTypeInfo;
    final resolvedValueTypeInfo =
        valueDeclared ? declaredValueTypeInfo : valueTypeInfo;
    final tracksDepth =
        (resolvedKeyTypeInfo != null &&
            tracksNestedPayloadDepth(resolvedKeyTypeInfo)) ||
        (resolvedValueTypeInfo != null &&
            tracksNestedPayloadDepth(resolvedValueTypeInfo));
    final guardUnbackedItems =
        !keyTrackRef &&
        !valueTrackRef &&
        resolvedKeyTypeInfo != null &&
        resolvedValueTypeInfo != null &&
        !resolvedKeyTypeInfo.readDataAlwaysAdvances &&
        !resolvedValueTypeInfo.readDataAlwaysAdvances;
    final checkpoint =
        guardUnbackedItems ? bufferReaderIndex(context.buffer) : 0;
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (var index = 0; index < chunkSize; index += 1) {
      final key =
          keyDeclared
              ? _readDeclaredMapValue(
                context,
                keyFieldType!,
                declaredKeyTypeInfo!,
                trackRef: keyTrackRef,
              )
              : _readResolvedMapValue(
                context,
                keyTypeInfo!,
                null,
                trackRef: keyTrackRef,
              );
      final value =
          valueDeclared
              ? _readDeclaredMapValue(
                context,
                valueFieldType!,
                declaredValueTypeInfo!,
                trackRef: valueTrackRef,
              )
              : _readResolvedMapValue(
                context,
                valueTypeInfo!,
                null,
                trackRef: valueTrackRef,
              );
      result[convertKey(key)] = convertValue(value);
    }
    if (guardUnbackedItems) {
      context.settleUnbackedContainerItems(
        chunkSize,
        bufferReaderIndex(context.buffer) - checkpoint,
      );
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    remaining -= chunkSize;
  }
  return result;
}

Map<K, V> readGeneratedMapPayload<K, V>(
  ReadContext context,
  FieldType? keyFieldType,
  FieldType? valueFieldType,
  GeneratedValueReader<K> readKey,
  GeneratedValueReader<V> readValue, {
  bool hasPreservedRef = false,
}) {
  var remaining = context.buffer.readVarUint32();
  context.reserveGraphMemory(_mapOwnerBytes + remaining * 2 * _referenceBytes);
  final declaredKeyTypeInfo =
      keyFieldType == null || keyFieldType.isDynamic
          ? null
          : context.typeResolver.resolveFieldType(keyFieldType);
  final declaredValueTypeInfo =
      valueFieldType == null || valueFieldType.isDynamic
          ? null
          : context.typeResolver.resolveFieldType(valueFieldType);
  final result = <K, V>{};
  if (hasPreservedRef) {
    // The typed map is the final generated owner. Bind it before keys or
    // values are read so nested back-references resolve to this exact map.
    context.reference(result);
  }
  while (remaining > 0) {
    final header = context.buffer.readUint8();
    final keyHasNull = (header & MapFlags.keyHasNull) != 0;
    final valueHasNull = (header & MapFlags.valueHasNull) != 0;
    if (keyHasNull || valueHasNull) {
      final key =
          keyHasNull
              ? null as K
              : _readGeneratedDynamicMapValue(
                context,
                header,
                MapFlags.trackingKeyRef,
                MapFlags.keyDeclaredType,
                keyFieldType,
                declaredKeyTypeInfo,
                readKey,
              );
      final value =
          valueHasNull
              ? null as V
              : _readGeneratedDynamicMapValue(
                context,
                header,
                MapFlags.trackingValueRef,
                MapFlags.valueDeclaredType,
                valueFieldType,
                declaredValueTypeInfo,
                readValue,
              );
      result[key] = value;
      remaining -= 1;
      continue;
    }
    final keyTrackRef = (header & MapFlags.trackingKeyRef) != 0;
    final valueTrackRef = (header & MapFlags.trackingValueRef) != 0;
    final keyDeclared = (header & MapFlags.keyDeclaredType) != 0;
    final valueDeclared = (header & MapFlags.valueDeclaredType) != 0;
    final chunkSize = context.buffer.readUint8();
    if (chunkSize == 0 || chunkSize > remaining) {
      _throwInvalidMapChunk(chunkSize, remaining);
    }
    final keyTypeInfo = keyDeclared ? null : context.readTypeMetaValue();
    final valueTypeInfo = valueDeclared ? null : context.readTypeMetaValue();
    final resolvedKeyTypeInfo = keyDeclared ? declaredKeyTypeInfo : keyTypeInfo;
    final resolvedValueTypeInfo =
        valueDeclared ? declaredValueTypeInfo : valueTypeInfo;
    final tracksDepth =
        (resolvedKeyTypeInfo != null &&
            tracksNestedPayloadDepth(resolvedKeyTypeInfo)) ||
        (resolvedValueTypeInfo != null &&
            tracksNestedPayloadDepth(resolvedValueTypeInfo));
    final guardUnbackedItems =
        !keyTrackRef &&
        !valueTrackRef &&
        resolvedKeyTypeInfo != null &&
        resolvedValueTypeInfo != null &&
        !resolvedKeyTypeInfo.readDataAlwaysAdvances &&
        !resolvedValueTypeInfo.readDataAlwaysAdvances;
    final checkpoint =
        guardUnbackedItems ? bufferReaderIndex(context.buffer) : 0;
    if (tracksDepth) {
      context.increaseDepth();
    }
    for (var index = 0; index < chunkSize; index += 1) {
      final key = _readGeneratedKnownMapValue(
        context,
        keyDeclared ? declaredKeyTypeInfo! : keyTypeInfo!,
        keyDeclared ? keyFieldType : null,
        keyTrackRef,
        readKey,
      );
      final value = _readGeneratedKnownMapValue(
        context,
        valueDeclared ? declaredValueTypeInfo! : valueTypeInfo!,
        valueDeclared ? valueFieldType : null,
        valueTrackRef,
        readValue,
      );
      result[key] = value;
    }
    if (guardUnbackedItems) {
      context.settleUnbackedContainerItems(
        chunkSize,
        bufferReaderIndex(context.buffer) - checkpoint,
      );
    }
    if (tracksDepth) {
      context.decreaseDepth();
    }
    remaining -= chunkSize;
  }
  return result;
}

T _readGeneratedDynamicMapValue<T>(
  ReadContext context,
  int header,
  int trackingFlag,
  int declaredFlag,
  FieldType? fieldType,
  TypeInfo? declaredTypeInfo,
  GeneratedValueReader<T> readValue,
) {
  final trackRef = (header & trackingFlag) != 0;
  if ((header & declaredFlag) != 0 && fieldType != null) {
    return _readGeneratedKnownMapValue(
      context,
      declaredTypeInfo!,
      fieldType,
      trackRef,
      readValue,
    );
  }
  if (trackRef) {
    final flag = context.refReader.readRefOrNull(context.buffer);
    if (flag == RefWriter.refFlag) {
      return context.refReader.getReadRef() as T;
    }
    final resolved = context.readTypeMetaValue();
    final preservedRefId = context.refReader.preserveRefValue(
      flag,
      resolved.supportsRef,
    );
    return _readGeneratedMapPayloadValue(
      context,
      resolved,
      null,
      preservedRefId,
      readValue,
      trackDepth: tracksNestedPayloadDepth(resolved),
    );
  }
  final resolved = context.readTypeMetaValue();
  if (tracksNestedPayloadDepth(resolved)) {
    context.increaseDepth();
    final value = readValue(context, resolved, null, false);
    context.decreaseDepth();
    return value;
  }
  return readValue(context, resolved, null, false);
}

T _readGeneratedKnownMapValue<T>(
  ReadContext context,
  TypeInfo resolved,
  FieldType? fieldType,
  bool trackRef,
  GeneratedValueReader<T> readValue,
) {
  if (!trackRef) {
    return readValue(context, resolved, fieldType, false);
  }
  final flag = context.refReader.readRefOrNull(context.buffer);
  if (flag == RefWriter.refFlag) {
    return context.refReader.getReadRef() as T;
  }
  final preservedRefId = context.refReader.preserveRefValue(
    flag,
    resolved.supportsRef,
  );
  return _readGeneratedMapPayloadValue(
    context,
    resolved,
    fieldType,
    preservedRefId,
    readValue,
  );
}

T _readGeneratedMapPayloadValue<T>(
  ReadContext context,
  TypeInfo resolved,
  FieldType? fieldType,
  int? preservedRefId,
  GeneratedValueReader<T> readValue, {
  bool trackDepth = false,
}) {
  if (trackDepth) {
    context.increaseDepth();
  }
  final value = readValue(context, resolved, fieldType, preservedRefId != null);
  if (preservedRefId != null &&
      resolved.supportsRef &&
      context.refReader.readRefAt(preservedRefId) == null) {
    context.setReadRef(preservedRefId, value);
  }
  if (trackDepth) {
    context.decreaseDepth();
  }
  return value;
}

@pragma('vm:never-inline')
Never _throwInvalidMapChunk(int chunkSize, int remaining) {
  throw StateError(
    'Invalid map chunk size $chunkSize with $remaining entries remaining.',
  );
}

void _writeNullChunk(
  WriteContext context,
  Object? key,
  Object? value, {
  required bool keyTrackRef,
  required bool valueTrackRef,
  required FieldType? keyFieldType,
  required FieldType? valueFieldType,
  required TypeInfo? declaredKeyTypeInfo,
  required TypeInfo? declaredValueTypeInfo,
  required bool keyDeclared,
  required bool valueDeclared,
}) {
  var header = 0;
  if (key == null) {
    header |= MapFlags.keyHasNull;
  } else if (keyDeclared) {
    header |= MapFlags.keyDeclaredType;
  }
  if (keyTrackRef) {
    header |= MapFlags.trackingKeyRef;
  }
  if (value == null) {
    header |= MapFlags.valueHasNull;
  } else if (valueDeclared) {
    header |= MapFlags.valueDeclaredType;
  }
  if (valueTrackRef) {
    header |= MapFlags.trackingValueRef;
  }
  context.buffer.writeUint8(header);
  if (key != null) {
    if (keyDeclared) {
      writeFieldTypeValue(
        context,
        _declaredMapFieldType(keyFieldType!, trackRef: keyTrackRef),
        declaredKeyTypeInfo,
        true,
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
      writeFieldTypeValue(
        context,
        _declaredMapFieldType(valueFieldType!, trackRef: valueTrackRef),
        declaredValueTypeInfo,
        true,
        value,
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
  required TypeInfo keyTypeInfo,
  required TypeInfo valueTypeInfo,
  required FieldType? keyFieldType,
  required FieldType? valueFieldType,
}) {
  writeTypeInfoValue(
    context,
    keyTypeInfo,
    keyFieldType,
    key,
    trackRef: keyTrackRef,
  );
  writeTypeInfoValue(
    context,
    valueTypeInfo,
    valueFieldType,
    value,
    trackRef: valueTrackRef,
  );
}

Object? _readNullChunkKey(
  ReadContext context,
  int header,
  FieldType? keyFieldType,
  TypeInfo? declaredKeyTypeInfo,
) {
  final keyHasNull = (header & MapFlags.keyHasNull) != 0;
  if (keyHasNull) {
    return null;
  }
  final trackRef = (header & MapFlags.trackingKeyRef) != 0;
  final declared = (header & MapFlags.keyDeclaredType) != 0;
  if (declared && keyFieldType != null) {
    return _readDeclaredMapValue(
      context,
      keyFieldType,
      declaredKeyTypeInfo!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

Object? _readNullChunkValue(
  ReadContext context,
  int header,
  FieldType? valueFieldType,
  TypeInfo? declaredValueTypeInfo,
) {
  final valueHasNull = (header & MapFlags.valueHasNull) != 0;
  if (valueHasNull) {
    return null;
  }
  final trackRef = (header & MapFlags.trackingValueRef) != 0;
  final declared = (header & MapFlags.valueDeclaredType) != 0;
  if (declared && valueFieldType != null) {
    return _readDeclaredMapValue(
      context,
      valueFieldType,
      declaredValueTypeInfo!,
      trackRef: trackRef,
    );
  }
  return trackRef ? context.readRef() : context.readNonRef();
}

FieldType _declaredMapFieldType(FieldType fieldType, {required bool trackRef}) {
  return fieldType.withRootOverrides(nullable: false, ref: trackRef);
}

Object? _readDeclaredMapValue(
  ReadContext context,
  FieldType fieldType,
  TypeInfo typeInfo, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return readTypeInfoValue(context, typeInfo, fieldType);
  }
  return readFieldTypeValue<Object?>(
    context,
    _declaredMapFieldType(fieldType, trackRef: true),
    typeInfo,
    true,
  );
}

Object? _readResolvedMapValue(
  ReadContext context,
  TypeInfo typeInfo,
  FieldType? fieldType, {
  required bool trackRef,
}) {
  if (!trackRef) {
    return readTypeInfoValue(context, typeInfo, fieldType);
  }
  final flag = context.refReader.readRefOrNull(context.buffer);
  if (flag == RefWriter.refFlag) {
    return context.refReader.getReadRef();
  }
  final preservedRefId = context.refReader.preserveRefValue(
    flag,
    typeInfo.supportsRef,
  );
  final value = readTypeInfoValue(
    context,
    typeInfo,
    fieldType,
    hasPreservedRef: preservedRefId != null,
  );
  if (preservedRefId != null &&
      typeInfo.supportsRef &&
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
    header |= MapFlags.trackingKeyRef;
  }
  if (keyDeclared) {
    header |= MapFlags.keyDeclaredType;
  }
  if (valueTrackRef) {
    header |= MapFlags.trackingValueRef;
  }
  if (valueDeclared) {
    header |= MapFlags.valueDeclaredType;
  }
  return header;
}
