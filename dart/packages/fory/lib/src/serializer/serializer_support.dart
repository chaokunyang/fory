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

import 'dart:typed_data';

import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/meta/field_info.dart';
import 'package:fory/src/meta/field_type.dart';
import 'package:fory/src/meta/type_ids.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:fory/src/serializer/collection_flags.dart';
import 'package:fory/src/serializer/serialization_field_info.dart';
import 'package:fory/src/types/bfloat16.dart';
import 'package:fory/src/types/bool_list.dart';
import 'package:fory/src/types/float32.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/int64.dart';
import 'package:fory/src/types/uint64.dart';

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
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
        return _declares64BitWrapper(fieldType)
            ? value
            : (value as Int64).toInt();
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
      case TypeIds.int64:
      case TypeIds.varInt64:
      case TypeIds.taggedInt64:
        return (value as Int64).toInt();
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

Object? readCompatibleMatchedField(
  ReadContext context,
  SerializationFieldInfo localField,
  FieldInfo remoteField,
) {
  final localType = localField.fieldType;
  final remoteType = remoteField.fieldType;
  if (_isArrayType(localType.typeId) && remoteType.typeId == TypeIds.list) {
    final elementType =
        remoteType.arguments.isEmpty ? null : remoteType.arguments.single;
    if (elementType == null ||
        _arrayElementTypeId(localType.typeId) !=
            _compatibleArrayElementTypeId(elementType.typeId)) {
      throw StateError(
          'Compatible list-to-array field ${localField.name} is unsupported.');
    }
    return _readCompatibleListAsArrayField(
      context,
      elementType,
      localType.typeId,
      localField.name,
    );
  }
  if (localType.typeId == TypeIds.list && _isArrayType(remoteType.typeId)) {
    final localElementType =
        localType.arguments.isEmpty ? null : localType.arguments.single;
    if (localElementType == null ||
        _arrayElementTypeId(remoteType.typeId) !=
            _compatibleArrayElementTypeId(localElementType.typeId)) {
      throw StateError(
          'Compatible array-to-list field ${localField.name} is unsupported.');
    }
    final raw = readCompatibleField(context, remoteField);
    return _arrayToListValue(raw);
  }
  return readFieldValue<Object?>(context, localField);
}

Object _readCompatibleListAsArrayField(
  ReadContext context,
  FieldType elementType,
  int arrayTypeId,
  String fieldName,
) {
  final size = context.buffer.readVarUint32();
  if (size > context.config.maxCollectionSize) {
    throw StateError(
      'Collection size $size exceeds ${context.config.maxCollectionSize}.',
    );
  }
  if (size == 0) {
    return _listToArrayValue(arrayTypeId, const <Object?>[]);
  }
  final header = context.buffer.readUint8();
  final trackRef = (header & CollectionFlags.trackingRef) != 0;
  final hasNull = (header & CollectionFlags.hasNull) != 0;
  final usesDeclaredType =
      (header & CollectionFlags.isDeclaredElementType) != 0;
  final sameType = (header & CollectionFlags.isSameType) != 0;
  if (hasNull || trackRef) {
    throw StateError(
      'Compatible list-to-array field $fieldName cannot read nullable or ref-tracked elements.',
    );
  }
  if (!sameType || !usesDeclaredType) {
    throw StateError(
      'Compatible list-to-array field $fieldName requires declared same-type elements.',
    );
  }
  final elementResolved = context.typeResolver.resolveFieldType(elementType);
  final values = List<Object?>.filled(size, null, growable: false);
  for (var index = 0; index < size; index += 1) {
    values[index] = context.readResolvedValue(elementResolved, elementType);
  }
  return _listToArrayValue(arrayTypeId, values);
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

bool _isArrayType(int typeId) =>
    typeId >= TypeIds.boolArray &&
    typeId <= TypeIds.float64Array &&
    typeId != 52;

int? _arrayElementTypeId(int typeId) {
  return switch (typeId) {
    TypeIds.boolArray => TypeIds.boolType,
    TypeIds.int8Array => TypeIds.int8,
    TypeIds.int16Array => TypeIds.int16,
    TypeIds.int32Array => TypeIds.int32,
    TypeIds.int64Array => TypeIds.int64,
    TypeIds.uint8Array => TypeIds.uint8,
    TypeIds.uint16Array => TypeIds.uint16,
    TypeIds.uint32Array => TypeIds.uint32,
    TypeIds.uint64Array => TypeIds.uint64,
    TypeIds.float16Array => TypeIds.float16,
    TypeIds.bfloat16Array => TypeIds.bfloat16,
    TypeIds.float32Array => TypeIds.float32,
    TypeIds.float64Array => TypeIds.float64,
    _ => null,
  };
}

int _compatibleArrayElementTypeId(int typeId) {
  return switch (typeId) {
    TypeIds.varInt32 => TypeIds.int32,
    TypeIds.varInt64 || TypeIds.taggedInt64 => TypeIds.int64,
    TypeIds.varUint32 => TypeIds.uint32,
    TypeIds.varUint64 || TypeIds.taggedUint64 => TypeIds.uint64,
    _ => typeId,
  };
}

Object _listToArrayValue(int arrayTypeId, Object? raw) {
  if (raw is! Iterable) {
    throw StateError('Expected compatible list payload.');
  }
  return switch (arrayTypeId) {
    TypeIds.boolArray => BoolList.fromList(raw.cast<bool>()),
    TypeIds.int8Array => Int8List.fromList(raw.cast<int>().toList()),
    TypeIds.int16Array => Int16List.fromList(raw.cast<int>().toList()),
    TypeIds.int32Array => Int32List.fromList(raw.cast<int>().toList()),
    TypeIds.int64Array => Int64List.fromList(raw.cast<Object>()),
    TypeIds.uint8Array => Uint8List.fromList(raw.cast<int>().toList()),
    TypeIds.uint16Array => Uint16List.fromList(raw.cast<int>().toList()),
    TypeIds.uint32Array => Uint32List.fromList(raw.cast<int>().toList()),
    TypeIds.uint64Array => Uint64List.fromList(raw.cast<Object>()),
    TypeIds.float16Array => Float16List.fromList(raw.cast<Float16>()),
    TypeIds.bfloat16Array => Bfloat16List.fromList(raw.cast<Bfloat16>()),
    TypeIds.float32Array => Float32List.fromList(
        raw.map((value) => (value as num).toDouble()).toList()),
    TypeIds.float64Array => Float64List.fromList(
        raw.map((value) => (value as num).toDouble()).toList()),
    _ =>
      throw StateError('Unsupported compatible array field type $arrayTypeId.'),
  };
}

Object _arrayToListValue(Object? raw) {
  if (raw is BoolList) {
    return raw.toList();
  }
  if (raw is Iterable) {
    return raw.toList();
  }
  throw StateError('Expected compatible array payload.');
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
