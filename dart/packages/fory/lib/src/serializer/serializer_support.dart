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

import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/meta/field_info.dart';
import 'package:fory/src/meta/field_type.dart';
import 'package:fory/src/meta/type_ids.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/serialization_field_info.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/int16.dart';
import 'package:fory/src/types/int32.dart';
import 'package:fory/src/types/int64.dart';
import 'package:fory/src/types/int8.dart';
import 'package:fory/src/types/uint16.dart';
import 'package:fory/src/types/uint32.dart';
import 'package:fory/src/types/uint64.dart';
import 'package:fory/src/types/uint8.dart';

final class DeferredReadRef {
  final int id;

  const DeferredReadRef(this.id);
}

TypeInfo? fieldDeclaredTypeInfo(
  TypeResolver resolver,
  SerializationFieldInfo field,
) {
  return field.declaredTypeInfo(resolver);
}

bool fieldUsesDeclaredType(
  TypeResolver resolver,
  SerializationFieldInfo field,
) {
  return field.usesDeclaredType(resolver);
}

Object convertPrimitiveFieldValue(Object value, FieldType fieldType) {
  if (fieldType.type == int) {
    switch (fieldType.typeId) {
      case TypeIds.int8:
        return (value as Int8).value;
      case TypeIds.int16:
        return (value as Int16).value;
      case TypeIds.int32:
      case TypeIds.varInt32:
        return (value as Int32).value;
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
        return _declares64BitWrapper(fieldType)
            ? value
            : (value as Int64).toInt();
      case TypeIds.uint8:
        return (value as Uint8).value;
      case TypeIds.uint16:
        return (value as Uint16).value;
      case TypeIds.uint32:
      case TypeIds.varUint32:
        return (value as Uint32).value;
      case TypeIds.uint64:
      case TypeIds.varUint64:
      case TypeIds.taggedUint64:
        return _declares64BitWrapper(fieldType)
            ? value
            : (value as Uint64).toInt();
      default:
        return value;
    }
  }
  if (fieldType.type == double && fieldType.typeId == TypeIds.float32) {
    return (value as Float32).value;
  }
  return value;
}

bool _declares64BitWrapper(FieldType fieldType) {
  final declaredTypeName = fieldType.declaredTypeName;
  if (declaredTypeName == null) {
    return false;
  }
  return declaredTypeName == 'Int64' ||
      declaredTypeName.endsWith('.Int64') ||
      declaredTypeName == 'Uint64' ||
      declaredTypeName.endsWith('.Uint64');
}

Object convertResolvedPrimitiveValue(
  Object value,
  TypeInfo resolved, [
  FieldType? fieldType,
]) {
  if (fieldType != null &&
      _declares64BitWrapper(fieldType) &&
      (resolved.typeId == TypeIds.int64 ||
          resolved.typeId == TypeIds.varInt64 ||
          resolved.typeId == TypeIds.taggedInt64 ||
          resolved.typeId == TypeIds.uint64 ||
          resolved.typeId == TypeIds.varUint64 ||
          resolved.typeId == TypeIds.taggedUint64)) {
    return value;
  }
  if (resolved.type == int) {
    switch (resolved.typeId) {
      case TypeIds.int8:
        return (value as Int8).value;
      case TypeIds.int16:
        return (value as Int16).value;
      case TypeIds.int32:
      case TypeIds.varInt32:
        return (value as Int32).value;
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
        return (value as Int64).toInt();
      case TypeIds.uint8:
        return (value as Uint8).value;
      case TypeIds.uint16:
        return (value as Uint16).value;
      case TypeIds.uint32:
      case TypeIds.varUint32:
        return (value as Uint32).value;
      case TypeIds.uint64:
      case TypeIds.varUint64:
      case TypeIds.taggedUint64:
        return (value as Uint64).toInt();
      default:
        break;
    }
  }
  if (resolved.type == double && resolved.typeId == TypeIds.float32) {
    return (value as Float32).value;
  }
  if (fieldType != null) {
    return convertPrimitiveFieldValue(value, fieldType);
  }
  return value;
}

void writeFieldValue(
  WriteContext context,
  SerializationFieldInfo field,
  Object? value,
) {
  final fieldType = field.fieldType;
  if (fieldType.isDynamic) {
    if (fieldType.ref) {
      context.writeRef(value);
      return;
    }
    if (context.writeNullFlag(value)) {
      return;
    }
    context.buffer.writeByte(RefWriter.notNullValueFlag);
    context.writeNonRef(value as Object);
    return;
  }
  if (fieldType.isPrimitive && !fieldType.nullable) {
    if (value == null) {
      throw StateError('Field ${field.name} is not nullable.');
    }
    context.writePrimitiveValue(fieldType.typeId, value);
    return;
  }
  final declaredTypeInfo = fieldDeclaredTypeInfo(context.typeResolver, field);
  final usesDeclaredType = fieldUsesDeclaredType(context.typeResolver, field);
  if (!usesDeclaredType || declaredTypeInfo == null) {
    if (fieldType.ref) {
      context.writeRef(value);
      return;
    }
    if (fieldType.nullable) {
      if (context.writeNullFlag(value)) {
        return;
      }
      context.buffer.writeByte(RefWriter.notNullValueFlag);
    } else if (value == null) {
      throw StateError('Field ${field.name} is not nullable.');
    }
    context.writeNonRef(value as Object);
    return;
  }
  final resolved = declaredTypeInfo;
  if (fieldType.nullable || fieldType.ref) {
    final handled = context.refWriter.writeRefOrNull(
      context.buffer,
      value,
      trackRef: fieldType.ref && resolved.supportsRef,
    );
    if (handled) {
      return;
    }
  }
  if (value == null) {
    throw StateError('Field ${field.name} is not nullable.');
  }
  context.writeResolvedValue(resolved, value, fieldType);
}

T readFieldValue<T>(
  ReadContext context,
  SerializationFieldInfo field, [
  T? fallback,
]) {
  final fieldType = field.fieldType;
  if (fieldType.isDynamic) {
    return context.readRef() as T;
  }
  if (fieldType.isPrimitive && !fieldType.nullable) {
    return convertPrimitiveFieldValue(
      context.readPrimitiveValue(fieldType.typeId),
      fieldType,
    ) as T;
  }
  final declaredTypeInfo = fieldDeclaredTypeInfo(context.typeResolver, field);
  final usesDeclaredType = fieldUsesDeclaredType(context.typeResolver, field);
  if (!usesDeclaredType || declaredTypeInfo == null) {
    if (fieldType.ref) {
      return context.readRef() as T;
    }
    if (fieldType.nullable) {
      return context.readNullable() as T;
    }
    return context.readNonRef() as T;
  }
  final resolved = declaredTypeInfo;
  if (fieldType.nullable || fieldType.ref) {
    final flag = context.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return fallback as T;
    }
    if (flag == RefWriter.refFlag) {
      return context.refReader.getReadRef() as T;
    }
    final value = context.readResolvedValue(
      resolved,
      fieldType,
      hasPreservedRef: preservedRefId != null,
    );
    if (preservedRefId != null &&
        resolved.supportsRef &&
        context.refReader.readRefAt(preservedRefId) == null) {
      context.refReader.setReadRef(preservedRefId, value);
    }
    return value as T;
  }
  return context.readResolvedValue(resolved, fieldType) as T;
}

Object? readCompatibleField(
  ReadContext context,
  FieldInfo field,
) {
  final fieldType = field.fieldType;
  if (fieldType.isDynamic) {
    return context.readRef();
  }
  if (fieldType.isPrimitive && !fieldType.nullable) {
    return convertPrimitiveFieldValue(
      context.readPrimitiveValue(fieldType.typeId),
      fieldType,
    );
  }
  final declaredTypeInfo = _compatibleFieldDeclaredTypeInfo(
    context.typeResolver,
    field,
  );
  final usesDeclaredType = declaredTypeInfo != null &&
      usesDeclaredTypeInfo(
        context.typeResolver.config.compatible,
        fieldType,
        declaredTypeInfo,
      );
  if (!usesDeclaredType) {
    if (fieldType.ref) {
      return context.readRef();
    }
    if (fieldType.nullable) {
      return context.readNullable();
    }
    return context.readNonRef();
  }
  final resolved = declaredTypeInfo;
  if (fieldType.nullable || fieldType.ref) {
    final flag = context.refReader.tryPreserveRefId(context.buffer);
    final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
    if (flag == RefWriter.nullFlag) {
      return null;
    }
    if (flag == RefWriter.refFlag) {
      return context.refReader.getReadRef();
    }
    final value = context.readResolvedValue(
      resolved,
      fieldType,
      hasPreservedRef: preservedRefId != null,
    );
    if (preservedRefId != null &&
        resolved.supportsRef &&
        context.refReader.readRefAt(preservedRefId) == null) {
      context.refReader.setReadRef(preservedRefId, value);
    }
    return value;
  }
  return context.readResolvedValue(resolved, fieldType);
}

const FieldType _skippedUnknownFieldType = FieldType(
  type: Object,
  declaredTypeName: null,
  typeId: TypeIds.unknown,
  nullable: true,
  ref: true,
  dynamic: true,
  arguments: <FieldType>[],
);

final class _SkippedUnionValue {
  final int caseId;
  final Object? value;

  const _SkippedUnionValue(this.caseId, this.value);
}

void skipCompatibleField(
  ReadContext context,
  FieldInfo field,
) {
  _readSkippedFieldValue(
    context,
    field.fieldType,
  );
}

Object? _readSkippedFieldValue(
  ReadContext context,
  FieldType fieldType, {
  TypeInfo? typeInfo,
  bool? nullableOverride,
  bool? trackRefOverride,
}) {
  final nullable = nullableOverride ?? fieldType.nullable;
  final trackRef = trackRefOverride ?? fieldType.ref;
  if (!nullable && !trackRef) {
    return _readSkippedFieldPayload(
      context,
      fieldType,
      typeInfo: typeInfo,
      hasPreservedRef: false,
    );
  }
  final flag = context.refReader.tryPreserveRefId(context.buffer);
  final preservedRefId = flag >= RefWriter.refValueFlag ? flag : null;
  if (flag == RefWriter.nullFlag) {
    return null;
  }
  if (flag == RefWriter.refFlag) {
    return context.refReader.getReadRef();
  }
  final value = _readSkippedFieldPayload(
    context,
    fieldType,
    typeInfo: typeInfo,
    hasPreservedRef: preservedRefId != null,
  );
  final supportsRef = typeInfo?.supportsRef ??
      TypeIds.supportsRef(
        typeInfo?.typeId ?? fieldType.typeId,
      );
  if (preservedRefId != null &&
      supportsRef &&
      context.refReader.readRefAt(preservedRefId) == null) {
    context.refReader.setReadRef(preservedRefId, value);
  }
  return value;
}

Object? _readSkippedFieldPayload(
  ReadContext context,
  FieldType fieldType, {
  required bool hasPreservedRef,
  TypeInfo? typeInfo,
}) {
  if (typeInfo == null && _needsCompatibleTypeMeta(fieldType.typeId)) {
    final resolved = context.readTypeMetaValue();
    return context.readResolvedValue(
      resolved,
      null,
      hasPreservedRef: hasPreservedRef,
    );
  }
  final resolvedTypeId = typeInfo?.typeId ?? fieldType.typeId;
  switch (resolvedTypeId) {
    case TypeIds.none:
      return null;
    case TypeIds.boolType:
      context.buffer.skip(1);
      return null;
    case TypeIds.int8:
      context.buffer.skip(1);
      return null;
    case TypeIds.int16:
      context.buffer.skip(2);
      return null;
    case TypeIds.int32:
      context.buffer.skip(4);
      return null;
    case TypeIds.varInt32:
      context.buffer.readVarInt32();
      return null;
    case TypeIds.int64:
      context.buffer.skip(8);
      return null;
    case TypeIds.varInt64:
      context.buffer.readVarInt64();
      return null;
    case TypeIds.taggedInt64:
      context.buffer.readTaggedInt64();
      return null;
    case TypeIds.uint8:
      context.buffer.skip(1);
      return null;
    case TypeIds.uint16:
      context.buffer.skip(2);
      return null;
    case TypeIds.uint32:
      context.buffer.skip(4);
      return null;
    case TypeIds.varUint32:
      context.buffer.readVarUint32();
      return null;
    case TypeIds.uint64:
      context.buffer.skip(8);
      return null;
    case TypeIds.varUint64:
      context.buffer.readVarUint64();
      return null;
    case TypeIds.taggedUint64:
      context.buffer.readTaggedUint64();
      return null;
    case TypeIds.float16:
    case TypeIds.bfloat16:
      context.buffer.skip(2);
      return null;
    case TypeIds.float32:
      context.buffer.skip(4);
      return null;
    case TypeIds.float64:
      context.buffer.skip(8);
      return null;
    case TypeIds.string:
      return context.readString();
    case TypeIds.binary:
      _skipSizedPayload(context);
      return null;
    case TypeIds.boolArray:
      context.buffer.skip(context.buffer.readVarUint32());
      return null;
    case TypeIds.int8Array:
    case TypeIds.uint8Array:
      _skipSizedPayload(context);
      return null;
    case TypeIds.int16Array:
    case TypeIds.uint16Array:
    case TypeIds.float16Array:
    case TypeIds.bfloat16Array:
      _skipSizedPayload(context);
      return null;
    case TypeIds.int32Array:
    case TypeIds.uint32Array:
    case TypeIds.float32Array:
      _skipSizedPayload(context);
      return null;
    case TypeIds.int64Array:
    case TypeIds.uint64Array:
    case TypeIds.float64Array:
      _skipSizedPayload(context);
      return null;
    case TypeIds.date:
      context.buffer.readVarInt64();
      return null;
    case TypeIds.timestamp:
      context.buffer.skip(12);
      return null;
    case TypeIds.duration:
      context.buffer.readVarInt64();
      context.buffer.skip(4);
      return null;
    case TypeIds.decimal:
      _skipDecimalPayload(context);
      return null;
    case TypeIds.list:
      return _readSkippedListPayload(
        context,
        fieldType,
        hasPreservedRef: hasPreservedRef,
      );
    case TypeIds.set:
      return Set<Object?>.of(
        _readSkippedListPayload(
          context,
          fieldType,
          hasPreservedRef: hasPreservedRef,
        ),
      );
    case TypeIds.map:
      return _readSkippedMapPayload(
        context,
        fieldType,
        hasPreservedRef: hasPreservedRef,
      );
    case TypeIds.enumById:
    case TypeIds.namedEnum:
      if (typeInfo != null) {
        return context.readResolvedValue(
          typeInfo,
          null,
          hasPreservedRef: hasPreservedRef,
        );
      }
      return context.buffer.readVarUint32();
    case TypeIds.union:
    case TypeIds.typedUnion:
    case TypeIds.namedUnion:
      if (typeInfo != null) {
        return context.readResolvedValue(
          typeInfo,
          null,
          hasPreservedRef: hasPreservedRef,
        );
      }
      return _readSkippedUnionPayload(context);
    case TypeIds.struct:
    case TypeIds.compatibleStruct:
    case TypeIds.namedStruct:
    case TypeIds.namedCompatibleStruct:
    case TypeIds.ext:
    case TypeIds.namedExt:
      if (typeInfo != null) {
        return context.readResolvedValue(
          typeInfo,
          null,
          hasPreservedRef: hasPreservedRef,
        );
      }
      throw StateError(
        'Missing compatible type metadata for skipped field type $resolvedTypeId.',
      );
    default:
      if (typeInfo != null) {
        return context.readResolvedValue(
          typeInfo,
          null,
          hasPreservedRef: hasPreservedRef,
        );
      }
      throw StateError('Unsupported compatible field type id $resolvedTypeId.');
  }
}

bool _needsCompatibleTypeMeta(int typeId) {
  return typeId == TypeIds.unknown ||
      typeId == TypeIds.struct ||
      typeId == TypeIds.compatibleStruct ||
      typeId == TypeIds.namedStruct ||
      typeId == TypeIds.namedCompatibleStruct ||
      typeId == TypeIds.ext ||
      typeId == TypeIds.namedExt;
}

List<Object?> _readSkippedListPayload(
  ReadContext context,
  FieldType fieldType, {
  required bool hasPreservedRef,
}) {
  final size = context.buffer.readVarUint32();
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  final result = List<Object?>.filled(size, null, growable: false);
  if (hasPreservedRef) {
    context.reference(result);
  }
  if (size == 0) {
    return result;
  }
  final header = context.buffer.readUint8();
  final trackRef = (header & 0x01) != 0;
  final hasNull = (header & 0x02) != 0;
  final declared = (header & 0x04) != 0;
  final sameType = (header & 0x08) != 0;
  final elementFieldType = fieldType.arguments.isEmpty
      ? _skippedUnknownFieldType
      : fieldType.arguments.single;
  final declaredTypeInfo = declared
      ? context.typeResolver.tryResolveFieldType(elementFieldType)
      : null;
  final sameTypeInfo =
      (!declared && sameType) ? context.readTypeMetaValue() : null;
  context.increaseDepth();
  try {
    for (var index = 0; index < size; index += 1) {
      if (sameTypeInfo != null) {
        result[index] = _readSkippedFieldValue(
          context,
          elementFieldType,
          typeInfo: sameTypeInfo,
          nullableOverride: hasNull,
          trackRefOverride: trackRef,
        );
        continue;
      }
      if (declared) {
        result[index] = _readSkippedFieldValue(
          context,
          elementFieldType,
          typeInfo: declaredTypeInfo,
          nullableOverride: hasNull,
          trackRefOverride: trackRef,
        );
        continue;
      }
      result[index] = _readSkippedFieldValue(
        context,
        _skippedUnknownFieldType,
        nullableOverride: hasNull,
        trackRefOverride: trackRef,
      );
    }
  } finally {
    context.decreaseDepth();
  }
  return result;
}

Map<Object?, Object?> _readSkippedMapPayload(
  ReadContext context,
  FieldType fieldType, {
  required bool hasPreservedRef,
}) {
  final totalEntries = context.buffer.readVarUint32();
  if (totalEntries > context.config.maxCollectionSize) {
    throw StateError(
      'Map size $totalEntries exceeds ${context.config.maxCollectionSize}.',
    );
  }
  final result = <Object?, Object?>{};
  if (hasPreservedRef) {
    context.reference(result);
  }
  if (totalEntries == 0) {
    return result;
  }
  final keyFieldType = fieldType.arguments.isEmpty
      ? _skippedUnknownFieldType
      : fieldType.arguments[0];
  final valueFieldType = fieldType.arguments.length < 2
      ? _skippedUnknownFieldType
      : fieldType.arguments[1];
  var remaining = totalEntries;
  context.increaseDepth();
  try {
    while (remaining > 0) {
      final header = context.buffer.readUint8();
      final keyHasNull = (header & 0x02) != 0;
      final valueHasNull = (header & 0x10) != 0;
      final keyTrackRef = (header & 0x01) != 0;
      final valueTrackRef = (header & 0x08) != 0;
      final keyDeclared = (header & 0x04) != 0;
      final valueDeclared = (header & 0x20) != 0;
      if (keyHasNull || valueHasNull) {
        final key = keyHasNull
            ? null
            : _readSkippedFieldValue(
                context,
                keyFieldType.withRootOverrides(
                  nullable: false,
                  ref: keyTrackRef,
                ),
                typeInfo: keyDeclared
                    ? context.typeResolver.tryResolveFieldType(keyFieldType)
                    : null,
              );
        final value = valueHasNull
            ? null
            : _readSkippedFieldValue(
                context,
                valueFieldType.withRootOverrides(
                  nullable: false,
                  ref: valueTrackRef,
                ),
                typeInfo: valueDeclared
                    ? context.typeResolver.tryResolveFieldType(valueFieldType)
                    : null,
              );
        result[key] = value;
        remaining -= 1;
        continue;
      }
      final chunkSize = context.buffer.readUint8();
      if (chunkSize == 0) {
        throw StateError('Invalid compatible map chunk size 0.');
      }
      final sharedKeyTypeInfo =
          keyDeclared ? null : context.readTypeMetaValue();
      final sharedValueTypeInfo =
          valueDeclared ? null : context.readTypeMetaValue();
      final declaredKeyTypeInfo = keyDeclared
          ? context.typeResolver.tryResolveFieldType(keyFieldType)
          : null;
      final declaredValueTypeInfo = valueDeclared
          ? context.typeResolver.tryResolveFieldType(valueFieldType)
          : null;
      for (var index = 0; index < chunkSize; index += 1) {
        final key = keyDeclared
            ? _readSkippedFieldValue(
                context,
                keyFieldType.withRootOverrides(
                  nullable: false,
                  ref: keyTrackRef,
                ),
                typeInfo: declaredKeyTypeInfo,
              )
            : _readSkippedFieldValue(
                context,
                _skippedUnknownFieldType,
                typeInfo: sharedKeyTypeInfo,
                nullableOverride: false,
                trackRefOverride: keyTrackRef,
              );
        final value = valueDeclared
            ? _readSkippedFieldValue(
                context,
                valueFieldType.withRootOverrides(
                  nullable: false,
                  ref: valueTrackRef,
                ),
                typeInfo: declaredValueTypeInfo,
              )
            : _readSkippedFieldValue(
                context,
                _skippedUnknownFieldType,
                typeInfo: sharedValueTypeInfo,
                nullableOverride: false,
                trackRefOverride: valueTrackRef,
              );
        result[key] = value;
      }
      remaining -= chunkSize;
    }
  } finally {
    context.decreaseDepth();
  }
  return result;
}

_SkippedUnionValue _readSkippedUnionPayload(ReadContext context) {
  context.increaseDepth();
  try {
    final caseId = context.buffer.readVarUint32();
    return _SkippedUnionValue(caseId, context.readRef());
  } finally {
    context.decreaseDepth();
  }
}

void _skipSizedPayload(ReadContext context) {
  context.buffer.skip(context.buffer.readVarUint32());
}

void _skipDecimalPayload(ReadContext context) {
  context.buffer.readVarInt32();
  final header = context.buffer.readVarUint64();
  if ((header.low32 & 1) == 0) {
    return;
  }
  final meta = header >>> 1;
  final length = (meta >>> 1).toInt();
  if (length <= 0) {
    throw StateError('Invalid decimal magnitude length $length.');
  }
  context.buffer.skip(length);
}

TypeInfo? _compatibleFieldDeclaredTypeInfo(
  TypeResolver resolver,
  FieldInfo field,
) {
  final fieldType = field.fieldType;
  if (fieldType.isDynamic || (fieldType.isPrimitive && !fieldType.nullable)) {
    return null;
  }
  return resolver.resolveFieldType(fieldType);
}

FieldInfo mergeCompatibleWriteField(
  FieldInfo localField,
  FieldInfo remoteField,
) {
  FieldType mergeFieldType(
    FieldType local,
    FieldType remote,
  ) {
    final mergedArguments = <FieldType>[];
    final argumentCount = remote.arguments.length;
    for (var index = 0; index < argumentCount; index += 1) {
      final remoteArgument = remote.arguments[index];
      final localArgument = index < local.arguments.length
          ? local.arguments[index]
          : remoteArgument;
      mergedArguments.add(mergeFieldType(localArgument, remoteArgument));
    }
    return FieldType(
      type: local.type,
      declaredTypeName: local.declaredTypeName,
      typeId: remote.typeId,
      nullable: remote.nullable,
      ref: remote.ref,
      dynamic: local.dynamic ?? remote.dynamic,
      arguments: mergedArguments,
    );
  }

  return FieldInfo(
    name: localField.name,
    identifier: localField.identifier,
    id: localField.id,
    fieldType: mergeFieldType(localField.fieldType, remoteField.fieldType),
  );
}

FieldInfo mergeCompatibleReadField(
  FieldInfo localField,
  FieldInfo remoteField,
) {
  FieldType mergeFieldType(
    FieldType local,
    FieldType remote,
  ) {
    final mergedArguments = <FieldType>[];
    final argumentCount = remote.arguments.length;
    for (var index = 0; index < argumentCount; index += 1) {
      final remoteArgument = remote.arguments[index];
      final localArgument = index < local.arguments.length
          ? local.arguments[index]
          : remoteArgument;
      mergedArguments.add(mergeFieldType(localArgument, remoteArgument));
    }
    return FieldType(
      type: local.type,
      declaredTypeName: local.declaredTypeName,
      typeId: remote.typeId,
      nullable: remote.nullable,
      ref: remote.ref,
      dynamic: local.dynamic ?? remote.dynamic,
      arguments: mergedArguments,
    );
  }

  return FieldInfo(
    name: localField.name,
    identifier: localField.identifier,
    id: localField.id,
    fieldType: mergeFieldType(localField.fieldType, remoteField.fieldType),
  );
}
