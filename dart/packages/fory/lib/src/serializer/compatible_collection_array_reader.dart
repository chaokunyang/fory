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
import 'package:fory/src/meta/field_info.dart';
import 'package:fory/src/meta/field_type.dart';
import 'package:fory/src/meta/type_ids.dart';
import 'package:fory/src/serializer/collection_flags.dart';
import 'package:fory/src/serializer/serialization_field_info.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/types/bfloat16.dart';
import 'package:fory/src/types/bool_list.dart';
import 'package:fory/src/types/float16.dart';
import 'package:fory/src/types/int64.dart';
import 'package:fory/src/types/uint64.dart';

@pragma('vm:never-inline')
Object? readCompatibleMatchedCollectionArrayField(
  ReadContext context,
  SerializationFieldInfo localField,
  FieldInfo remoteField,
) {
  final localType = localField.fieldType;
  final remoteType = remoteField.fieldType;
  if (isCompatibleArrayType(localType.typeId) &&
      remoteType.typeId == TypeIds.list) {
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
  if (localType.typeId == TypeIds.list &&
      isCompatibleArrayType(remoteType.typeId)) {
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

bool isCompatibleArrayType(int typeId) =>
    typeId >= TypeIds.boolArray &&
    typeId <= TypeIds.float64Array &&
    typeId != 52;

bool isCompatibleCollectionArrayFieldPair(
  FieldInfo localField,
  FieldInfo remoteField,
) {
  final localType = localField.fieldType;
  final remoteType = remoteField.fieldType;
  if (isCompatibleArrayType(localType.typeId) &&
      remoteType.typeId == TypeIds.list) {
    return _listElementMatchesArray(remoteType, localType.typeId);
  }
  if (localType.typeId == TypeIds.list &&
      isCompatibleArrayType(remoteType.typeId)) {
    return _listElementMatchesArray(localType, remoteType.typeId);
  }
  return false;
}

bool _listElementMatchesArray(FieldType listType, int arrayTypeId) {
  final elementType =
      listType.arguments.isEmpty ? null : listType.arguments.single;
  return elementType != null &&
      _arrayElementTypeId(arrayTypeId) ==
          _compatibleArrayElementTypeId(elementType.typeId);
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
