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

import 'package:meta/meta.dart';

import 'package:fory/fory.dart';
import 'package:fory/src/buffer.dart';
import 'package:fory/src/codegen/generated_registry.dart';
import 'package:fory/src/meta/field_info.dart' as meta;
import 'package:fory/src/meta/field_type.dart' as meta_types;
import 'package:fory/src/resolver/type_resolver.dart' as resolver;
import 'package:fory/src/serializer/collection_serializers.dart';
import 'package:fory/src/serializer/map_serializers.dart';
import 'package:fory/src/serializer/scalar_serializers.dart';
import 'package:fory/src/serializer/serialization_field_info.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'package:fory/src/serializer/struct_serializer.dart';
import 'package:fory/src/serializer/struct_slots.dart';
import 'package:fory/src/serializer/time_serializers.dart';
import 'package:fory/src/serializer/typed_array_serializers.dart';
import 'package:fory/src/util/int64_codec.dart';
import 'package:fory/src/util/int64_platform.dart';

@internal
final class GeneratedWriteCursor {
  final Buffer _buffer;
  final Uint8List _bytes;
  final ByteData _view;
  int _offset;

  GeneratedWriteCursor._(
    this._buffer,
    this._bytes,
    this._view,
    this._offset,
  );

  factory GeneratedWriteCursor.reserve(Buffer buffer, int maxBytes) {
    final start = bufferReserveBytes(buffer, maxBytes);
    return GeneratedWriteCursor._(
      buffer,
      bufferBytes(buffer),
      bufferByteData(buffer),
      start,
    );
  }

  void finish() {
    bufferSetWriterIndex(_buffer, _offset);
  }

  @pragma('vm:prefer-inline')
  void writeBool(bool value) {
    _bytes[_offset] = value ? 1 : 0;
    _offset += 1;
  }

  @pragma('vm:prefer-inline')
  void writeByte(int value) {
    _view.setInt8(_offset, value);
    _offset += 1;
  }

  @pragma('vm:prefer-inline')
  void writeUint8(int value) {
    _view.setUint8(_offset, value);
    _offset += 1;
  }

  @pragma('vm:prefer-inline')
  void writeInt16(int value) {
    _view.setInt16(_offset, value, Endian.little);
    _offset += 2;
  }

  @pragma('vm:prefer-inline')
  void writeUint16(int value) {
    _view.setUint16(_offset, value, Endian.little);
    _offset += 2;
  }

  @pragma('vm:prefer-inline')
  void writeInt32(int value) {
    _view.setInt32(_offset, value, Endian.little);
    _offset += 4;
  }

  @pragma('vm:prefer-inline')
  void writeUint32(int value) {
    _view.setUint32(_offset, value, Endian.little);
    _offset += 4;
  }

  @pragma('vm:prefer-inline')
  void writeInt64(Int64 value) {
    writeInt64LittleEndian(_view, _offset, value);
    _offset += 8;
  }

  @pragma('vm:prefer-inline')
  void writeInt64FromInt(int value) {
    if (useNativeInt64FastPath) {
      _view.setInt64(_offset, value, Endian.little);
      _offset += 8;
      return;
    }
    writeInt64(Int64(value));
  }

  @pragma('vm:prefer-inline')
  void writeUint64(Uint64 value) {
    writeUint64LittleEndian(_view, _offset, value);
    _offset += 8;
  }

  @pragma('vm:prefer-inline')
  void writeUint64FromInt(int value) {
    if (useNativeInt64FastPath) {
      _view.setUint64(_offset, value, Endian.little);
      _offset += 8;
      return;
    }
    writeUint64(Uint64(value));
  }

  @pragma('vm:prefer-inline')
  void writeFloat16(Float16 value) {
    writeUint16(value.toBits());
  }

  @pragma('vm:prefer-inline')
  void writeBfloat16(Bfloat16 value) {
    writeUint16(value.toBits());
  }

  @pragma('vm:prefer-inline')
  void writeFloat32(double value) {
    _view.setFloat32(_offset, value, Endian.little);
    _offset += 4;
  }

  @pragma('vm:prefer-inline')
  void writeFloat64(double value) {
    _view.setFloat64(_offset, value, Endian.little);
    _offset += 8;
  }

  @pragma('vm:prefer-inline')
  void writeVarUint32(int value) {
    var remaining = value;
    while (remaining >= 0x80) {
      _bytes[_offset] = (remaining & 0x7f) | 0x80;
      _offset += 1;
      remaining >>>= 7;
    }
    _bytes[_offset] = remaining;
    _offset += 1;
  }

  @pragma('vm:prefer-inline')
  void writeVarInt32(int value) {
    final encoded = (value << 1) ^ (value >> 31);
    writeVarUint32(
      useNativeInt64FastPath ? encoded : encoded.toUnsigned(32),
    );
  }

  @pragma('vm:prefer-inline')
  void writeVarUint64(Uint64 value) {
    writeVarUint64Bytes(value, (byte) {
      _bytes[_offset] = byte;
      _offset += 1;
    });
  }

  @pragma('vm:prefer-inline')
  void writeVarUint64FromInt(int value) {
    if (useNativeInt64FastPath) {
      _writeVarUint64Int(value);
      return;
    }
    writeVarUint64(Uint64(value));
  }

  @pragma('vm:prefer-inline')
  void writeVarInt64(Int64 value) {
    writeVarUint64(zigZagEncodeInt64(value));
  }

  @pragma('vm:prefer-inline')
  void writeVarInt64FromInt(int value) {
    if (useNativeInt64FastPath) {
      _writeVarUint64Int((value << 1) ^ (value >> 63));
      return;
    }
    writeVarInt64(Int64(value));
  }

  @pragma('vm:prefer-inline')
  void writeTaggedInt64(Int64 value) {
    if (value >= -0x40000000 && value <= 0x3fffffff) {
      writeInt32((value.toInt() << 1).toSigned(32));
      return;
    }
    writeUint8(0x01);
    writeInt64(value);
  }

  @pragma('vm:prefer-inline')
  void writeTaggedInt64FromInt(int value) {
    if (value >= -0x40000000 && value <= 0x3fffffff) {
      writeInt32((value << 1).toSigned(32));
      return;
    }
    writeUint8(0x01);
    writeInt64FromInt(value);
  }

  @pragma('vm:prefer-inline')
  void writeTaggedUint64(Uint64 value) {
    if (value >= 0 && value <= 0x7fffffff) {
      writeInt32(value.toInt() << 1);
      return;
    }
    writeUint8(0x01);
    writeUint64(value);
  }

  @pragma('vm:prefer-inline')
  void writeTaggedUint64FromInt(int value) {
    if (value >= 0 && value <= 0x7fffffff) {
      writeInt32(value << 1);
      return;
    }
    writeUint8(0x01);
    writeUint64FromInt(value);
  }

  @pragma('vm:prefer-inline')
  void _writeVarUint64Int(int value) {
    var remaining = value;
    for (var index = 0; index < 8; index += 1) {
      final chunk = remaining & 0x7f;
      remaining = remaining >>> 7;
      if (remaining == 0) {
        _bytes[_offset] = chunk;
        _offset += 1;
        return;
      }
      _bytes[_offset] = chunk | 0x80;
      _offset += 1;
    }
    _bytes[_offset] = remaining & 0xff;
    _offset += 1;
  }
}

@internal
final class GeneratedReadCursor {
  final Buffer _buffer;
  final ByteData _view;
  int _offset;

  GeneratedReadCursor._(
    this._buffer,
    this._view,
    this._offset,
  );

  factory GeneratedReadCursor.start(Buffer buffer) {
    return GeneratedReadCursor._(
      buffer,
      bufferByteData(buffer),
      bufferReaderIndex(buffer),
    );
  }

  void finish() {
    bufferSetReaderIndex(_buffer, _offset);
  }

  @pragma('vm:prefer-inline')
  bool readBool() => readUint8() != 0;

  @pragma('vm:prefer-inline')
  int readByte() {
    final value = _view.getInt8(_offset);
    _offset += 1;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readUint8() {
    final value = _view.getUint8(_offset);
    _offset += 1;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readInt16() {
    final value = _view.getInt16(_offset, Endian.little);
    _offset += 2;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readUint16() {
    final value = _view.getUint16(_offset, Endian.little);
    _offset += 2;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readInt32() {
    final value = _view.getInt32(_offset, Endian.little);
    _offset += 4;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readUint32() {
    final value = _view.getUint32(_offset, Endian.little);
    _offset += 4;
    return value;
  }

  @pragma('vm:prefer-inline')
  Int64 readInt64() {
    final value = readInt64LittleEndian(_view, _offset);
    _offset += 8;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readInt64AsInt() {
    if (useNativeInt64FastPath) {
      final value = _view.getInt64(_offset, Endian.little);
      _offset += 8;
      return value;
    }
    return readInt64().toInt();
  }

  @pragma('vm:prefer-inline')
  Uint64 readUint64() {
    final value = readUint64LittleEndian(_view, _offset);
    _offset += 8;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readUint64AsInt() {
    if (useNativeInt64FastPath) {
      final value = _view.getUint64(_offset, Endian.little);
      _offset += 8;
      return value;
    }
    return readUint64().toInt();
  }

  @pragma('vm:prefer-inline')
  Float16 readFloat16() => Float16.fromBits(readUint16());

  @pragma('vm:prefer-inline')
  Bfloat16 readBfloat16() => Bfloat16.fromBits(readUint16());

  @pragma('vm:prefer-inline')
  double readFloat32() {
    final value = _view.getFloat32(_offset, Endian.little);
    _offset += 4;
    return value;
  }

  @pragma('vm:prefer-inline')
  double readFloat64() {
    final value = _view.getFloat64(_offset, Endian.little);
    _offset += 8;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readVarUint32() {
    var shift = 0;
    var result = 0;
    while (true) {
      final byte = readUint8();
      result |= (byte & 0x7f) << shift;
      if ((byte & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
  }

  @pragma('vm:prefer-inline')
  int readVarInt32() {
    final value = readVarUint32();
    final decoded = (value >>> 1) ^ -(value & 1);
    return useNativeInt64FastPath ? decoded : decoded.toSigned(32);
  }

  @pragma('vm:prefer-inline')
  Uint64 readVarUint64() {
    return readVarUint64Bytes(readUint8);
  }

  @pragma('vm:prefer-inline')
  int readVarUint64AsInt() {
    if (useNativeInt64FastPath) {
      return _readVarUint64Int();
    }
    return readVarUint64().toInt();
  }

  @pragma('vm:prefer-inline')
  Int64 readVarInt64() {
    return zigZagDecodeInt64(readVarUint64());
  }

  @pragma('vm:prefer-inline')
  int readVarInt64AsInt() {
    if (useNativeInt64FastPath) {
      final encoded = _readVarUint64Int();
      return (encoded >>> 1) ^ -(encoded & 1);
    }
    return readVarInt64().toInt();
  }

  @pragma('vm:prefer-inline')
  Int64 readTaggedInt64() {
    final readIndex = _offset;
    final first = _view.getInt32(readIndex, Endian.little);
    if ((first & 1) == 0) {
      _offset = readIndex + 4;
      return Int64(first.toSigned(32) ~/ 2);
    }
    final value = readInt64LittleEndian(_view, readIndex + 1);
    _offset = readIndex + 9;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readTaggedInt64AsInt() {
    if (useNativeInt64FastPath) {
      final readIndex = _offset;
      final first = _view.getInt32(readIndex, Endian.little);
      if ((first & 1) == 0) {
        _offset = readIndex + 4;
        return first >> 1;
      }
      final value = _view.getInt64(readIndex + 1, Endian.little);
      _offset = readIndex + 9;
      return value;
    }
    return readTaggedInt64().toInt();
  }

  @pragma('vm:prefer-inline')
  Uint64 readTaggedUint64() {
    final readIndex = _offset;
    final first = _view.getUint32(readIndex, Endian.little);
    if ((first & 1) == 0) {
      _offset = readIndex + 4;
      return Uint64(first >>> 1);
    }
    final value = readUint64LittleEndian(_view, readIndex + 1);
    _offset = readIndex + 9;
    return value;
  }

  @pragma('vm:prefer-inline')
  int readTaggedUint64AsInt() {
    if (useNativeInt64FastPath) {
      final readIndex = _offset;
      final first = _view.getUint32(readIndex, Endian.little);
      if ((first & 1) == 0) {
        _offset = readIndex + 4;
        return first >>> 1;
      }
      final value = _view.getUint64(readIndex + 1, Endian.little);
      _offset = readIndex + 9;
      return value;
    }
    return readTaggedUint64().toInt();
  }

  @pragma('vm:prefer-inline')
  int _readVarUint64Int() {
    var shift = 0;
    var result = 0;
    while (shift < 56) {
      final byte = readUint8();
      result |= (byte & 0x7f) << shift;
      if ((byte & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    return result | (readUint8() << 56);
  }
}

@internal
final class GeneratedFieldType {
  final Type type;
  final String? declaredTypeName;
  final int typeId;
  final bool nullable;
  final bool ref;
  final bool? dynamic;
  final List<GeneratedFieldType> arguments;

  const GeneratedFieldType({
    required this.type,
    this.declaredTypeName,
    required this.typeId,
    required this.nullable,
    required this.ref,
    required this.dynamic,
    required this.arguments,
  });

  meta_types.FieldType toFieldType() {
    return meta_types.FieldType(
      type: type,
      declaredTypeName: declaredTypeName,
      typeId: typeId,
      nullable: nullable,
      ref: ref,
      dynamic: dynamic,
      arguments: arguments
          .map((argument) => argument.toFieldType())
          .toList(growable: false),
    );
  }
}

@internal
Object? resolveGeneratedSlotRawValue(
  ReadContext context,
  Object? rawValue,
) {
  if (rawValue is DeferredReadRef) {
    return context.getReadRef(rawValue.id);
  }
  return rawValue;
}

@internal
final class GeneratedFieldInfo {
  final String name;
  final String identifier;
  final int? id;
  final GeneratedFieldType fieldType;

  const GeneratedFieldInfo({
    required this.name,
    required this.identifier,
    required this.id,
    required this.fieldType,
  });

  meta.FieldInfo toFieldInfo() {
    return meta.FieldInfo(
      name: name,
      identifier: identifier,
      id: id,
      fieldType: fieldType.toFieldType(),
    );
  }
}

@internal
final class GeneratedEnumRegistration {
  final Type type;
  final Serializer<Object?> Function() serializerFactory;

  const GeneratedEnumRegistration({
    required this.type,
    required this.serializerFactory,
  });
}

@internal
typedef GeneratedStructFieldInfo = SerializationFieldInfo;

@internal
typedef GeneratedStructFieldInfoWriter<T> = void Function(
    WriteContext context, GeneratedStructFieldInfo field, T value);

@internal
typedef GeneratedStructFieldInfoReader<T> = void Function(
    ReadContext context, T value, Object? rawValue);

@internal
final class GeneratedStructRegistration<T> {
  final List<GeneratedStructFieldInfoWriter<T>> fieldWritersBySlot;
  final GeneratedStructCompatibleFactory<T>? compatibleFactory;
  final List<GeneratedStructFieldInfoReader<T>>? compatibleReadersBySlot;
  final Type type;
  final Serializer<Object?> Function() serializerFactory;
  final bool evolving;
  final List<GeneratedFieldInfo> fields;

  GeneratedStructRegistration({
    required this.fieldWritersBySlot,
    this.compatibleFactory,
    this.compatibleReadersBySlot,
    required this.type,
    required this.serializerFactory,
    required this.evolving,
    required this.fields,
  });

  late final List<meta.FieldInfo> fieldInfos =
      List<meta.FieldInfo>.unmodifiable(
    List<meta.FieldInfo>.generate(
      fields.length,
      (index) => fields[index].toFieldInfo(),
    ),
  );

  late final List<int> defaultSlots = List<int>.unmodifiable(
    List<int>.generate(fieldInfos.length, (index) => index),
  );
}

@internal
void registerGeneratedEnum(
  Fory fory,
  GeneratedEnumRegistration registration, {
  int? id,
  String? namespace,
  String? typeName,
}) {
  GeneratedRegistrationCatalog.remember(
    registration.type,
    GeneratedRegistration(
      kind: GeneratedRegistrationKind.enumType,
      serializerFactory: registration.serializerFactory,
    ),
  );
  fory.register(
    registration.type,
    id: id,
    namespace: namespace,
    typeName: typeName,
  );
}

@internal
void registerGeneratedStruct<T>(
  Fory fory,
  GeneratedStructRegistration<T> registration, {
  int? id,
  String? namespace,
  String? typeName,
}) {
  final compatibleReadersBySlot = registration.compatibleReadersBySlot == null
      ? null
      : List<GeneratedStructCompatibleFieldReader<Object>>.unmodifiable(
          registration.compatibleReadersBySlot!.map(
            (reader) => (
              ReadContext context,
              Object value,
              Object? rawValue,
            ) =>
                reader(context, value as T, rawValue),
          ),
        );
  GeneratedRegistrationCatalog.remember(
    registration.type,
    GeneratedRegistration(
      kind: GeneratedRegistrationKind.struct,
      serializerFactory: registration.serializerFactory,
      evolving: registration.evolving,
      fields: registration.fieldInfos,
      compatibleFactory: registration.compatibleFactory == null
          ? null
          : () => registration.compatibleFactory!() as Object,
      compatibleReadersBySlot: compatibleReadersBySlot,
    ),
  );
  fory.register(
    registration.type,
    id: id,
    namespace: namespace,
    typeName: typeName,
  );
}

@internal
StructWriteSlots? generatedStructWriteSlots(WriteContext context) {
  return context.structWriteSlots;
}

@internal
StructReadSlots? generatedStructReadSlots(ReadContext context) {
  return context.structReadSlots;
}

@internal
void writeGeneratedBinaryValue(WriteContext context, Uint8List value) {
  BinarySerializer.writePayload(context, value);
}

@internal
Uint8List readGeneratedBinaryValue(ReadContext context) {
  return BinarySerializer.readPayload(context);
}

@internal
void writeGeneratedBoolArrayValue(WriteContext context, List<bool> value) {
  const BoolArraySerializer().write(context, value);
}

@internal
List<bool> readGeneratedBoolArrayValue(ReadContext context) {
  return const BoolArraySerializer().read(context);
}

@internal
void writeGeneratedLocalDateValue(WriteContext context, LocalDate value) {
  const LocalDateSerializer().write(context, value);
}

@internal
LocalDate readGeneratedLocalDateValue(ReadContext context) {
  return const LocalDateSerializer().read(context);
}

@internal
void writeGeneratedDecimalValue(WriteContext context, Decimal value) {
  const DecimalSerializer().write(context, value);
}

@internal
Decimal readGeneratedDecimalValue(ReadContext context) {
  return const DecimalSerializer().read(context);
}

@internal
Int64 generatedDurationWireSeconds(Duration value) {
  return durationWireSeconds(value);
}

@internal
int generatedDurationWireNanoseconds(Duration value) {
  return durationWireNanoseconds(value);
}

@internal
Duration readGeneratedDurationFromWire(Int64 seconds, int nanoseconds) {
  return durationFromWire(seconds, nanoseconds);
}

@internal
void writeGeneratedDurationValue(WriteContext context, Duration value) {
  const DurationSerializer().write(context, value);
}

@internal
Duration readGeneratedDurationValue(ReadContext context) {
  return const DurationSerializer().read(context);
}

@internal
int generatedTimestampWireNanoseconds(Timestamp value) {
  return timestampWireNanoseconds(value);
}

@internal
Int64 generatedDateTimeWireSeconds(DateTime value) {
  return dateTimeWireSeconds(value);
}

@internal
int generatedDateTimeWireNanoseconds(DateTime value) {
  return dateTimeWireNanoseconds(value);
}

@internal
Timestamp readGeneratedTimestampFromWire(Int64 seconds, int nanoseconds) {
  return timestampFromWire(seconds, nanoseconds);
}

@internal
DateTime readGeneratedDateTimeFromWire(Int64 seconds, int nanoseconds) {
  return dateTimeFromWire(seconds, nanoseconds);
}

@internal
void writeGeneratedTimestampValue(WriteContext context, Timestamp value) {
  const TimestampSerializer().write(context, value);
}

@internal
void writeGeneratedDateTimeValue(WriteContext context, DateTime value) {
  const DateTimeSerializer().write(context, value);
}

@internal
Timestamp readGeneratedTimestampValue(ReadContext context) {
  return const TimestampSerializer().read(context);
}

@internal
DateTime readGeneratedDateTimeValue(ReadContext context) {
  return const DateTimeSerializer().read(context);
}

@internal
void writeGeneratedFixedArrayValue(WriteContext context, Object value) {
  writeTypedArrayBytes(context, value);
}

@internal
T readGeneratedTypedArrayValue<T>(
  ReadContext context,
  int elementSize,
  T Function(Uint8List bytes) viewBuilder,
) {
  return readTypedArrayBytes(context, elementSize, viewBuilder);
}

@internal
List<GeneratedStructFieldInfo> buildGeneratedStructFieldInfos(
  resolver.TypeResolver typeResolver,
  GeneratedStructRegistration registration,
) {
  return typeResolver
      .resolvedRegisteredType(registration.type)
      .structSerializer!
      .localFields;
}

@internal
void writeGeneratedStructFieldInfoValue(
  WriteContext context,
  GeneratedStructFieldInfo field,
  Object? value,
) {
  final fieldType = field.fieldType;
  if (!fieldType.isDynamic && !fieldType.ref && !fieldType.nullable) {
    if (fieldType.isPrimitive) {
      context.writePrimitiveValue(fieldType.typeId, value as Object);
      return;
    }
    final resolved = fieldDeclaredTypeInfo(context.typeResolver, field)!;
    if (fieldUsesDeclaredType(context.typeResolver, field)) {
      context.writeResolvedValue(resolved, value as Object, fieldType);
      return;
    }
    final actualResolved = context.typeResolver.resolveValue(value as Object);
    context.writeTypeMetaValue(actualResolved, value);
    context.writeResolvedValue(actualResolved, value, fieldType);
    return;
  }
  writeFieldValue(context, field, value);
}

@internal
@pragma('vm:prefer-inline')
Object? readGeneratedStructFieldInfoValue(
  ReadContext context,
  GeneratedStructFieldInfo field, [
  Object? fallback,
]) {
  final fieldType = field.fieldType;
  if (fallback == null &&
      !fieldType.isDynamic &&
      !fieldType.ref &&
      !fieldType.nullable) {
    if (fieldType.isPrimitive) {
      return convertPrimitiveFieldValue(
        context.readPrimitiveValue(fieldType.typeId),
        fieldType,
      );
    }
    final resolved = fieldDeclaredTypeInfo(context.typeResolver, field)!;
    if (fieldUsesDeclaredType(context.typeResolver, field)) {
      return context.readResolvedValue(resolved, fieldType);
    }
    final actualResolved = context.readTypeMetaValue(resolved);
    return context.readResolvedValue(actualResolved, fieldType);
  }
  return readFieldValue(context, field, fallback);
}

@internal
@pragma('vm:prefer-inline')
Object? readGeneratedStructDeclaredValue(
  ReadContext context,
  GeneratedStructFieldInfo field,
) {
  final resolved = fieldDeclaredTypeInfo(context.typeResolver, field)!;
  if (fieldUsesDeclaredType(context.typeResolver, field)) {
    return context.readResolvedValue(resolved, field.fieldType);
  }
  final actualResolved = context.readTypeMetaValue(resolved);
  return context.readResolvedValue(actualResolved, field.fieldType);
}

@internal
@pragma('vm:prefer-inline')
Object readGeneratedStructDirectValue(
  ReadContext context,
  GeneratedStructFieldInfo field,
) {
  final declared = fieldDeclaredTypeInfo(context.typeResolver, field)!;
  final resolver.TypeInfo resolved;
  if (fieldUsesDeclaredType(context.typeResolver, field)) {
    resolved = declared;
  } else {
    resolved = context.readTypeMetaValue(declared);
  }
  context.increaseDepth();
  final value = resolved.structSerializer!.readValue(context, resolved);
  context.decreaseDepth();
  return value;
}

@internal
@pragma('vm:prefer-inline')
List<T> readGeneratedDirectListValue<T>(
  ReadContext context,
  GeneratedStructFieldInfo field,
  T Function(Object? value) convert,
) {
  final fieldType = field.fieldType;
  if (fieldType.typeId != TypeIds.list ||
      fieldType.nullable ||
      fieldType.ref ||
      fieldType.isDynamic) {
    throw StateError('Field ${field.name} is not a direct list path.');
  }
  return readTypedListPayload(
    context,
    fieldType.arguments.single,
    convert,
  );
}

@internal
@pragma('vm:prefer-inline')
Set<T> readGeneratedDirectSetValue<T>(
  ReadContext context,
  GeneratedStructFieldInfo field,
  T Function(Object? value) convert,
) {
  final fieldType = field.fieldType;
  if (fieldType.typeId != TypeIds.set ||
      fieldType.nullable ||
      fieldType.ref ||
      fieldType.isDynamic) {
    throw StateError('Field ${field.name} is not a direct set path.');
  }
  return readTypedSetPayload(
    context,
    fieldType.arguments.single,
    convert,
  );
}

@internal
@pragma('vm:prefer-inline')
Map<K, V> readGeneratedDirectMapValue<K, V>(
  ReadContext context,
  GeneratedStructFieldInfo field,
  K Function(Object? value) convertKey,
  V Function(Object? value) convertValue,
) {
  final fieldType = field.fieldType;
  if (fieldType.typeId != TypeIds.map ||
      fieldType.nullable ||
      fieldType.ref ||
      fieldType.isDynamic) {
    throw StateError('Field ${field.name} is not a direct map path.');
  }
  return readTypedMapPayload(
    context,
    fieldType.arguments[0],
    fieldType.arguments[1],
    convertKey,
    convertValue,
  );
}
