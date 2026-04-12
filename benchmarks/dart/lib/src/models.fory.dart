// dart format width=80
// GENERATED CODE - DO NOT MODIFY BY HAND

// **************************************************************************
// ForyGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: implementation_imports, invalid_use_of_internal_member

import 'dart:typed_data';
import 'package:fory/fory.dart';
import 'package:fory/src/codegen/generated_support.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'models.dart';

final class _PlayerForySerializer extends EnumSerializer<Player> {
  const _PlayerForySerializer();
  @override
  void write(WriteContext context, Player value) {
    context.writeVarUint32(value.index);
  }

  @override
  Player read(ReadContext context) {
    return Player.values[context.readVarUint32()];
  }
}

final class _MediaSizeForySerializer extends EnumSerializer<MediaSize> {
  const _MediaSizeForySerializer();
  @override
  void write(WriteContext context, MediaSize value) {
    context.writeVarUint32(value.index);
  }

  @override
  MediaSize read(ReadContext context) {
    return MediaSize.values[context.readVarUint32()];
  }
}

const List<GeneratedFieldMetadata> _numericStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f2',
    identifier: '2',
    id: 2,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f3',
    identifier: '3',
    id: 3,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f4',
    identifier: '4',
    id: 4,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f5',
    identifier: '5',
    id: 5,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f6',
    identifier: '6',
    id: 6,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f7',
    identifier: '7',
    id: 7,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f8',
    identifier: '8',
    id: 8,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _NumericStructSessionWriter = GeneratedStructFieldWriter<NumericStruct>;

void _writeNumericStructSessionField0(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeNumericStructSessionField1(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _writeNumericStructSessionField2(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f3);
}

void _writeNumericStructSessionField3(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f4);
}

void _writeNumericStructSessionField4(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f5);
}

void _writeNumericStructSessionField5(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f6);
}

void _writeNumericStructSessionField6(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f7);
}

void _writeNumericStructSessionField7(
    WriteContext context, GeneratedStructField field, NumericStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f8);
}

final GeneratedStructRegistration<NumericStruct>
    _numericStructForyRegistration = GeneratedStructRegistration<NumericStruct>(
  sessionWritersBySlot: <_NumericStructSessionWriter>[
    _writeNumericStructSessionField0,
    _writeNumericStructSessionField1,
    _writeNumericStructSessionField2,
    _writeNumericStructSessionField3,
    _writeNumericStructSessionField4,
    _writeNumericStructSessionField5,
    _writeNumericStructSessionField6,
    _writeNumericStructSessionField7,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: NumericStruct,
  serializerFactory: _NumericStructForySerializer.new,
  evolving: true,
  fields: _numericStructForyFieldMetadata,
);

final class _NumericStructForySerializer extends Serializer<NumericStruct> {
  List<GeneratedStructField>? _generatedFields;

  _NumericStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _numericStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _numericStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, NumericStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 40);
      cursor0.writeVarInt32(value.f1);
      cursor0.writeVarInt32(value.f2);
      cursor0.writeVarInt32(value.f3);
      cursor0.writeVarInt32(value.f4);
      cursor0.writeVarInt32(value.f5);
      cursor0.writeVarInt32(value.f6);
      cursor0.writeVarInt32(value.f7);
      cursor0.writeVarInt32(value.f8);
      cursor0.finish();
      return;
    }
    final writers = _numericStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  NumericStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final int _f1Value;
    late final int _f2Value;
    late final int _f3Value;
    late final int _f4Value;
    late final int _f5Value;
    late final int _f6Value;
    late final int _f7Value;
    late final int _f8Value;
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      _f1Value = cursor0.readVarInt32();
      _f2Value = cursor0.readVarInt32();
      _f3Value = cursor0.readVarInt32();
      _f4Value = cursor0.readVarInt32();
      _f5Value = cursor0.readVarInt32();
      _f6Value = cursor0.readVarInt32();
      _f7Value = cursor0.readVarInt32();
      _f8Value = cursor0.readVarInt32();
      cursor0.finish();
    } else {
      if (session.containsSlot(0)) {
        final rawNumericStruct0 = session.valueForSlot(0);
        _f1Value = _readNumericStructF1(rawNumericStruct0 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct0.id)
            : rawNumericStruct0);
      } else {
        _f1Value = _readNumericStructF1(null);
      }
      if (session.containsSlot(1)) {
        final rawNumericStruct1 = session.valueForSlot(1);
        _f2Value = _readNumericStructF2(rawNumericStruct1 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct1.id)
            : rawNumericStruct1);
      } else {
        _f2Value = _readNumericStructF2(null);
      }
      if (session.containsSlot(2)) {
        final rawNumericStruct2 = session.valueForSlot(2);
        _f3Value = _readNumericStructF3(rawNumericStruct2 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct2.id)
            : rawNumericStruct2);
      } else {
        _f3Value = _readNumericStructF3(null);
      }
      if (session.containsSlot(3)) {
        final rawNumericStruct3 = session.valueForSlot(3);
        _f4Value = _readNumericStructF4(rawNumericStruct3 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct3.id)
            : rawNumericStruct3);
      } else {
        _f4Value = _readNumericStructF4(null);
      }
      if (session.containsSlot(4)) {
        final rawNumericStruct4 = session.valueForSlot(4);
        _f5Value = _readNumericStructF5(rawNumericStruct4 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct4.id)
            : rawNumericStruct4);
      } else {
        _f5Value = _readNumericStructF5(null);
      }
      if (session.containsSlot(5)) {
        final rawNumericStruct5 = session.valueForSlot(5);
        _f6Value = _readNumericStructF6(rawNumericStruct5 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct5.id)
            : rawNumericStruct5);
      } else {
        _f6Value = _readNumericStructF6(null);
      }
      if (session.containsSlot(6)) {
        final rawNumericStruct6 = session.valueForSlot(6);
        _f7Value = _readNumericStructF7(rawNumericStruct6 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct6.id)
            : rawNumericStruct6);
      } else {
        _f7Value = _readNumericStructF7(null);
      }
      if (session.containsSlot(7)) {
        final rawNumericStruct7 = session.valueForSlot(7);
        _f8Value = _readNumericStructF8(rawNumericStruct7 is DeferredReadRef
            ? context.getReadRef(rawNumericStruct7.id)
            : rawNumericStruct7);
      } else {
        _f8Value = _readNumericStructF8(null);
      }
    }
    final value = NumericStruct(
        f1: _f1Value,
        f2: _f2Value,
        f3: _f3Value,
        f4: _f4Value,
        f5: _f5Value,
        f6: _f6Value,
        f7: _f7Value,
        f8: _f8Value);
    context.reference(value);
    return value;
  }
}

int _readNumericStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f1.')))
      : (value as Int32).value;
}

int _readNumericStructF2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f2.')))
      : (value as Int32).value;
}

int _readNumericStructF3(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f3.')))
      : (value as Int32).value;
}

int _readNumericStructF4(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f4.')))
      : (value as Int32).value;
}

int _readNumericStructF5(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f5.')))
      : (value as Int32).value;
}

int _readNumericStructF6(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f6.')))
      : (value as Int32).value;
}

int _readNumericStructF7(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f7.')))
      : (value as Int32).value;
}

int _readNumericStructF8(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f8.')))
      : (value as Int32).value;
}

const List<GeneratedFieldMetadata> _sampleForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'doubleValueBoxed',
    identifier: '11',
    id: 11,
    shape: GeneratedTypeShape(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'doubleValue',
    identifier: '4',
    id: 4,
    shape: GeneratedTypeShape(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'floatValueBoxed',
    identifier: '10',
    id: 10,
    shape: GeneratedTypeShape(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'floatValue',
    identifier: '3',
    id: 3,
    shape: GeneratedTypeShape(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'booleanValueBoxed',
    identifier: '14',
    id: 14,
    shape: GeneratedTypeShape(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'booleanValue',
    identifier: '7',
    id: 7,
    shape: GeneratedTypeShape(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'longValue',
    identifier: '2',
    id: 2,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'longValueBoxed',
    identifier: '9',
    id: 9,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'intValue',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'shortValueBoxed',
    identifier: '12',
    id: 12,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'charValueBoxed',
    identifier: '13',
    id: 13,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'shortValue',
    identifier: '5',
    id: 5,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'charValue',
    identifier: '6',
    id: 6,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'intValueBoxed',
    identifier: '8',
    id: 8,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'string',
    identifier: '22',
    id: 22,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'booleanArray',
    identifier: '21',
    id: 21,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 43,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: bool,
          typeId: 1,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'intArray',
    identifier: '15',
    id: 15,
    shape: GeneratedTypeShape(
      type: Int32List,
      typeId: 46,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'shortArray',
    identifier: '19',
    id: 19,
    shape: GeneratedTypeShape(
      type: Int32List,
      typeId: 46,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'charArray',
    identifier: '20',
    id: 20,
    shape: GeneratedTypeShape(
      type: Int32List,
      typeId: 46,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'longArray',
    identifier: '16',
    id: 16,
    shape: GeneratedTypeShape(
      type: Int64List,
      typeId: 47,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'floatArray',
    identifier: '17',
    id: 17,
    shape: GeneratedTypeShape(
      type: Float32List,
      typeId: 55,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'doubleArray',
    identifier: '18',
    id: 18,
    shape: GeneratedTypeShape(
      type: Float64List,
      typeId: 56,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _SampleSessionWriter = GeneratedStructFieldWriter<Sample>;

void _writeSampleSessionField0(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.doubleValueBoxed);
}

void _writeSampleSessionField1(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.doubleValue);
}

void _writeSampleSessionField2(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.floatValueBoxed);
}

void _writeSampleSessionField3(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.floatValue);
}

void _writeSampleSessionField4(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.booleanValueBoxed);
}

void _writeSampleSessionField5(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.booleanValue);
}

void _writeSampleSessionField6(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.longValue);
}

void _writeSampleSessionField7(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.longValueBoxed);
}

void _writeSampleSessionField8(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.intValue);
}

void _writeSampleSessionField9(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.shortValueBoxed);
}

void _writeSampleSessionField10(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.charValueBoxed);
}

void _writeSampleSessionField11(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.shortValue);
}

void _writeSampleSessionField12(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.charValue);
}

void _writeSampleSessionField13(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.intValueBoxed);
}

void _writeSampleSessionField14(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.string);
}

void _writeSampleSessionField15(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.booleanArray);
}

void _writeSampleSessionField16(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.intArray);
}

void _writeSampleSessionField17(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.shortArray);
}

void _writeSampleSessionField18(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.charArray);
}

void _writeSampleSessionField19(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.longArray);
}

void _writeSampleSessionField20(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.floatArray);
}

void _writeSampleSessionField21(
    WriteContext context, GeneratedStructField field, Sample value) {
  writeGeneratedStructRuntimeValue(context, field, value.doubleArray);
}

final GeneratedStructRegistration<Sample> _sampleForyRegistration =
    GeneratedStructRegistration<Sample>(
  sessionWritersBySlot: <_SampleSessionWriter>[
    _writeSampleSessionField0,
    _writeSampleSessionField1,
    _writeSampleSessionField2,
    _writeSampleSessionField3,
    _writeSampleSessionField4,
    _writeSampleSessionField5,
    _writeSampleSessionField6,
    _writeSampleSessionField7,
    _writeSampleSessionField8,
    _writeSampleSessionField9,
    _writeSampleSessionField10,
    _writeSampleSessionField11,
    _writeSampleSessionField12,
    _writeSampleSessionField13,
    _writeSampleSessionField14,
    _writeSampleSessionField15,
    _writeSampleSessionField16,
    _writeSampleSessionField17,
    _writeSampleSessionField18,
    _writeSampleSessionField19,
    _writeSampleSessionField20,
    _writeSampleSessionField21,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: Sample,
  serializerFactory: _SampleForySerializer.new,
  evolving: true,
  fields: _sampleForyFieldMetadata,
);

final class _SampleForySerializer extends Serializer<Sample> {
  List<GeneratedStructField>? _generatedFields;

  _SampleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _sampleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _sampleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Sample value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 76);
      cursor0.writeFloat64(value.doubleValueBoxed);
      cursor0.writeFloat64(value.doubleValue);
      cursor0.writeFloat32(value.floatValueBoxed.value);
      cursor0.writeFloat32(value.floatValue.value);
      cursor0.writeBool(value.booleanValueBoxed);
      cursor0.writeBool(value.booleanValue);
      cursor0.writeVarInt64(value.longValue);
      cursor0.writeVarInt64(value.longValueBoxed);
      cursor0.writeVarInt32(value.intValue);
      cursor0.writeVarInt32(value.shortValueBoxed);
      cursor0.writeVarInt32(value.charValueBoxed);
      cursor0.writeVarInt32(value.shortValue);
      cursor0.writeVarInt32(value.charValue);
      cursor0.writeVarInt32(value.intValueBoxed);
      cursor0.finish();
      context.writeString(value.string);
      writeGeneratedBoolArrayValue(context, value.booleanArray);
      writeGeneratedFixedArrayValue(context, value.intArray);
      writeGeneratedFixedArrayValue(context, value.shortArray);
      writeGeneratedFixedArrayValue(context, value.charArray);
      writeGeneratedFixedArrayValue(context, value.longArray);
      writeGeneratedFixedArrayValue(context, value.floatArray);
      writeGeneratedFixedArrayValue(context, value.doubleArray);
      return;
    }
    final writers = _sampleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Sample read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final double _doubleValueBoxedValue;
    late final double _doubleValueValue;
    late final Float32 _floatValueBoxedValue;
    late final Float32 _floatValueValue;
    late final bool _booleanValueBoxedValue;
    late final bool _booleanValueValue;
    late final int _longValueValue;
    late final int _longValueBoxedValue;
    late final int _intValueValue;
    late final int _shortValueBoxedValue;
    late final int _charValueBoxedValue;
    late final int _shortValueValue;
    late final int _charValueValue;
    late final int _intValueBoxedValue;
    late final String _stringValue;
    late final List<bool> _booleanArrayValue;
    late final Int32List _intArrayValue;
    late final Int32List _shortArrayValue;
    late final Int32List _charArrayValue;
    late final Int64List _longArrayValue;
    late final Float32List _floatArrayValue;
    late final Float64List _doubleArrayValue;
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      _doubleValueBoxedValue = cursor0.readFloat64();
      _doubleValueValue = cursor0.readFloat64();
      _floatValueBoxedValue = Float32(cursor0.readFloat32());
      _floatValueValue = Float32(cursor0.readFloat32());
      _booleanValueBoxedValue = cursor0.readBool();
      _booleanValueValue = cursor0.readBool();
      _longValueValue = cursor0.readVarInt64();
      _longValueBoxedValue = cursor0.readVarInt64();
      _intValueValue = cursor0.readVarInt32();
      _shortValueBoxedValue = cursor0.readVarInt32();
      _charValueBoxedValue = cursor0.readVarInt32();
      _shortValueValue = cursor0.readVarInt32();
      _charValueValue = cursor0.readVarInt32();
      _intValueBoxedValue = cursor0.readVarInt32();
      cursor0.finish();
      _stringValue = context.readString();
      _booleanArrayValue = readGeneratedBoolArrayValue(context);
      _intArrayValue = readGeneratedTypedArrayValue<Int32List>(
          context,
          4,
          (bytes) => bytes.buffer
              .asInt32List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 4));
      _shortArrayValue = readGeneratedTypedArrayValue<Int32List>(
          context,
          4,
          (bytes) => bytes.buffer
              .asInt32List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 4));
      _charArrayValue = readGeneratedTypedArrayValue<Int32List>(
          context,
          4,
          (bytes) => bytes.buffer
              .asInt32List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 4));
      _longArrayValue = readGeneratedTypedArrayValue<Int64List>(
          context,
          8,
          (bytes) => bytes.buffer
              .asInt64List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 8));
      _floatArrayValue = readGeneratedTypedArrayValue<Float32List>(
          context,
          4,
          (bytes) => bytes.buffer
              .asFloat32List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 4));
      _doubleArrayValue = readGeneratedTypedArrayValue<Float64List>(
          context,
          8,
          (bytes) => bytes.buffer
              .asFloat64List(bytes.offsetInBytes, bytes.lengthInBytes ~/ 8));
    } else {
      if (session.containsSlot(0)) {
        final rawSample0 = session.valueForSlot(0);
        _doubleValueBoxedValue = _readSampleDoubleValueBoxed(
            rawSample0 is DeferredReadRef
                ? context.getReadRef(rawSample0.id)
                : rawSample0);
      } else {
        _doubleValueBoxedValue = _readSampleDoubleValueBoxed(null);
      }
      if (session.containsSlot(1)) {
        final rawSample1 = session.valueForSlot(1);
        _doubleValueValue = _readSampleDoubleValue(rawSample1 is DeferredReadRef
            ? context.getReadRef(rawSample1.id)
            : rawSample1);
      } else {
        _doubleValueValue = _readSampleDoubleValue(null);
      }
      if (session.containsSlot(2)) {
        final rawSample2 = session.valueForSlot(2);
        _floatValueBoxedValue = _readSampleFloatValueBoxed(
            rawSample2 is DeferredReadRef
                ? context.getReadRef(rawSample2.id)
                : rawSample2);
      } else {
        _floatValueBoxedValue = _readSampleFloatValueBoxed(null);
      }
      if (session.containsSlot(3)) {
        final rawSample3 = session.valueForSlot(3);
        _floatValueValue = _readSampleFloatValue(rawSample3 is DeferredReadRef
            ? context.getReadRef(rawSample3.id)
            : rawSample3);
      } else {
        _floatValueValue = _readSampleFloatValue(null);
      }
      if (session.containsSlot(4)) {
        final rawSample4 = session.valueForSlot(4);
        _booleanValueBoxedValue = _readSampleBooleanValueBoxed(
            rawSample4 is DeferredReadRef
                ? context.getReadRef(rawSample4.id)
                : rawSample4);
      } else {
        _booleanValueBoxedValue = _readSampleBooleanValueBoxed(null);
      }
      if (session.containsSlot(5)) {
        final rawSample5 = session.valueForSlot(5);
        _booleanValueValue = _readSampleBooleanValue(
            rawSample5 is DeferredReadRef
                ? context.getReadRef(rawSample5.id)
                : rawSample5);
      } else {
        _booleanValueValue = _readSampleBooleanValue(null);
      }
      if (session.containsSlot(6)) {
        final rawSample6 = session.valueForSlot(6);
        _longValueValue = _readSampleLongValue(rawSample6 is DeferredReadRef
            ? context.getReadRef(rawSample6.id)
            : rawSample6);
      } else {
        _longValueValue = _readSampleLongValue(null);
      }
      if (session.containsSlot(7)) {
        final rawSample7 = session.valueForSlot(7);
        _longValueBoxedValue = _readSampleLongValueBoxed(
            rawSample7 is DeferredReadRef
                ? context.getReadRef(rawSample7.id)
                : rawSample7);
      } else {
        _longValueBoxedValue = _readSampleLongValueBoxed(null);
      }
      if (session.containsSlot(8)) {
        final rawSample8 = session.valueForSlot(8);
        _intValueValue = _readSampleIntValue(rawSample8 is DeferredReadRef
            ? context.getReadRef(rawSample8.id)
            : rawSample8);
      } else {
        _intValueValue = _readSampleIntValue(null);
      }
      if (session.containsSlot(9)) {
        final rawSample9 = session.valueForSlot(9);
        _shortValueBoxedValue = _readSampleShortValueBoxed(
            rawSample9 is DeferredReadRef
                ? context.getReadRef(rawSample9.id)
                : rawSample9);
      } else {
        _shortValueBoxedValue = _readSampleShortValueBoxed(null);
      }
      if (session.containsSlot(10)) {
        final rawSample10 = session.valueForSlot(10);
        _charValueBoxedValue = _readSampleCharValueBoxed(
            rawSample10 is DeferredReadRef
                ? context.getReadRef(rawSample10.id)
                : rawSample10);
      } else {
        _charValueBoxedValue = _readSampleCharValueBoxed(null);
      }
      if (session.containsSlot(11)) {
        final rawSample11 = session.valueForSlot(11);
        _shortValueValue = _readSampleShortValue(rawSample11 is DeferredReadRef
            ? context.getReadRef(rawSample11.id)
            : rawSample11);
      } else {
        _shortValueValue = _readSampleShortValue(null);
      }
      if (session.containsSlot(12)) {
        final rawSample12 = session.valueForSlot(12);
        _charValueValue = _readSampleCharValue(rawSample12 is DeferredReadRef
            ? context.getReadRef(rawSample12.id)
            : rawSample12);
      } else {
        _charValueValue = _readSampleCharValue(null);
      }
      if (session.containsSlot(13)) {
        final rawSample13 = session.valueForSlot(13);
        _intValueBoxedValue = _readSampleIntValueBoxed(
            rawSample13 is DeferredReadRef
                ? context.getReadRef(rawSample13.id)
                : rawSample13);
      } else {
        _intValueBoxedValue = _readSampleIntValueBoxed(null);
      }
      if (session.containsSlot(14)) {
        final rawSample14 = session.valueForSlot(14);
        _stringValue = _readSampleString(rawSample14 is DeferredReadRef
            ? context.getReadRef(rawSample14.id)
            : rawSample14);
      } else {
        _stringValue = _readSampleString(null);
      }
      if (session.containsSlot(15)) {
        final rawSample15 = session.valueForSlot(15);
        _booleanArrayValue = _readSampleBooleanArray(
            rawSample15 is DeferredReadRef
                ? context.getReadRef(rawSample15.id)
                : rawSample15);
      } else {
        _booleanArrayValue = _readSampleBooleanArray(null);
      }
      if (session.containsSlot(16)) {
        final rawSample16 = session.valueForSlot(16);
        _intArrayValue = _readSampleIntArray(rawSample16 is DeferredReadRef
            ? context.getReadRef(rawSample16.id)
            : rawSample16);
      } else {
        _intArrayValue = _readSampleIntArray(null);
      }
      if (session.containsSlot(17)) {
        final rawSample17 = session.valueForSlot(17);
        _shortArrayValue = _readSampleShortArray(rawSample17 is DeferredReadRef
            ? context.getReadRef(rawSample17.id)
            : rawSample17);
      } else {
        _shortArrayValue = _readSampleShortArray(null);
      }
      if (session.containsSlot(18)) {
        final rawSample18 = session.valueForSlot(18);
        _charArrayValue = _readSampleCharArray(rawSample18 is DeferredReadRef
            ? context.getReadRef(rawSample18.id)
            : rawSample18);
      } else {
        _charArrayValue = _readSampleCharArray(null);
      }
      if (session.containsSlot(19)) {
        final rawSample19 = session.valueForSlot(19);
        _longArrayValue = _readSampleLongArray(rawSample19 is DeferredReadRef
            ? context.getReadRef(rawSample19.id)
            : rawSample19);
      } else {
        _longArrayValue = _readSampleLongArray(null);
      }
      if (session.containsSlot(20)) {
        final rawSample20 = session.valueForSlot(20);
        _floatArrayValue = _readSampleFloatArray(rawSample20 is DeferredReadRef
            ? context.getReadRef(rawSample20.id)
            : rawSample20);
      } else {
        _floatArrayValue = _readSampleFloatArray(null);
      }
      if (session.containsSlot(21)) {
        final rawSample21 = session.valueForSlot(21);
        _doubleArrayValue = _readSampleDoubleArray(
            rawSample21 is DeferredReadRef
                ? context.getReadRef(rawSample21.id)
                : rawSample21);
      } else {
        _doubleArrayValue = _readSampleDoubleArray(null);
      }
    }
    final value = Sample(
        intValue: _intValueValue,
        longValue: _longValueValue,
        floatValue: _floatValueValue,
        doubleValue: _doubleValueValue,
        shortValue: _shortValueValue,
        charValue: _charValueValue,
        booleanValue: _booleanValueValue,
        intValueBoxed: _intValueBoxedValue,
        longValueBoxed: _longValueBoxedValue,
        floatValueBoxed: _floatValueBoxedValue,
        doubleValueBoxed: _doubleValueBoxedValue,
        shortValueBoxed: _shortValueBoxedValue,
        charValueBoxed: _charValueBoxedValue,
        booleanValueBoxed: _booleanValueBoxedValue,
        intArray: _intArrayValue,
        longArray: _longArrayValue,
        floatArray: _floatArrayValue,
        doubleArray: _doubleArrayValue,
        shortArray: _shortArrayValue,
        charArray: _charArrayValue,
        booleanArray: _booleanArrayValue,
        string: _stringValue);
    context.reference(value);
    return value;
  }
}

double _readSampleDoubleValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field doubleValueBoxed.')))
      : value as double;
}

double _readSampleDoubleValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field doubleValue.')))
      : value as double;
}

Float32 _readSampleFloatValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field floatValueBoxed.')))
      : value as Float32;
}

Float32 _readSampleFloatValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field floatValue.')))
      : value as Float32;
}

bool _readSampleBooleanValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field booleanValueBoxed.')))
      : value as bool;
}

bool _readSampleBooleanValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field booleanValue.')))
      : value as bool;
}

int _readSampleLongValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field longValue.')))
      : value as int;
}

int _readSampleLongValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field longValueBoxed.')))
      : value as int;
}

int _readSampleIntValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field intValue.')))
      : (value as Int32).value;
}

int _readSampleShortValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field shortValueBoxed.')))
      : (value as Int32).value;
}

int _readSampleCharValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field charValueBoxed.')))
      : (value as Int32).value;
}

int _readSampleShortValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field shortValue.')))
      : (value as Int32).value;
}

int _readSampleCharValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field charValue.')))
      : (value as Int32).value;
}

int _readSampleIntValueBoxed(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field intValueBoxed.')))
      : (value as Int32).value;
}

String _readSampleString(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field string.')))
      : value as String;
}

List<bool> _readSampleBooleanArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<bool>
          : (throw StateError(
              'Received null for non-nullable field booleanArray.')))
      : List.castFrom<dynamic, bool>(value as List);
}

Int32List _readSampleIntArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32List
          : (throw StateError(
              'Received null for non-nullable field intArray.')))
      : value as Int32List;
}

Int32List _readSampleShortArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32List
          : (throw StateError(
              'Received null for non-nullable field shortArray.')))
      : value as Int32List;
}

Int32List _readSampleCharArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32List
          : (throw StateError(
              'Received null for non-nullable field charArray.')))
      : value as Int32List;
}

Int64List _readSampleLongArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int64List
          : (throw StateError(
              'Received null for non-nullable field longArray.')))
      : value as Int64List;
}

Float32List _readSampleFloatArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32List
          : (throw StateError(
              'Received null for non-nullable field floatArray.')))
      : value as Float32List;
}

Float64List _readSampleDoubleArray(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float64List
          : (throw StateError(
              'Received null for non-nullable field doubleArray.')))
      : value as Float64List;
}

const List<GeneratedFieldMetadata> _mediaForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'hasBitrate',
    identifier: '9',
    id: 9,
    shape: GeneratedTypeShape(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'duration',
    identifier: '6',
    id: 6,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'size',
    identifier: '7',
    id: 7,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'width',
    identifier: '3',
    id: 3,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'height',
    identifier: '4',
    id: 4,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'bitrate',
    identifier: '8',
    id: 8,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'uri',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'copyright',
    identifier: '12',
    id: 12,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'title',
    identifier: '2',
    id: 2,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'format',
    identifier: '5',
    id: 5,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'persons',
    identifier: '10',
    id: 10,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'player',
    identifier: '11',
    id: 11,
    shape: GeneratedTypeShape(
      type: Player,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _MediaSessionWriter = GeneratedStructFieldWriter<Media>;

void _writeMediaSessionField0(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.hasBitrate);
}

void _writeMediaSessionField1(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.duration);
}

void _writeMediaSessionField2(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.size);
}

void _writeMediaSessionField3(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.width);
}

void _writeMediaSessionField4(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.height);
}

void _writeMediaSessionField5(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.bitrate);
}

void _writeMediaSessionField6(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.uri);
}

void _writeMediaSessionField7(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.copyright);
}

void _writeMediaSessionField8(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.title);
}

void _writeMediaSessionField9(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.format);
}

void _writeMediaSessionField10(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.persons);
}

void _writeMediaSessionField11(
    WriteContext context, GeneratedStructField field, Media value) {
  writeGeneratedStructRuntimeValue(context, field, value.player);
}

final GeneratedStructRegistration<Media> _mediaForyRegistration =
    GeneratedStructRegistration<Media>(
  sessionWritersBySlot: <_MediaSessionWriter>[
    _writeMediaSessionField0,
    _writeMediaSessionField1,
    _writeMediaSessionField2,
    _writeMediaSessionField3,
    _writeMediaSessionField4,
    _writeMediaSessionField5,
    _writeMediaSessionField6,
    _writeMediaSessionField7,
    _writeMediaSessionField8,
    _writeMediaSessionField9,
    _writeMediaSessionField10,
    _writeMediaSessionField11,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: Media,
  serializerFactory: _MediaForySerializer.new,
  evolving: true,
  fields: _mediaForyFieldMetadata,
);

final class _MediaForySerializer extends Serializer<Media> {
  List<GeneratedStructField>? _generatedFields;

  _MediaForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Media value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 36);
      cursor0.writeBool(value.hasBitrate);
      cursor0.writeVarInt64(value.duration);
      cursor0.writeVarInt64(value.size);
      cursor0.writeVarInt32(value.width);
      cursor0.writeVarInt32(value.height);
      cursor0.writeVarInt32(value.bitrate);
      cursor0.finish();
      context.writeString(value.uri);
      context.writeString(value.copyright);
      context.writeString(value.title);
      context.writeString(value.format);
      writeGeneratedStructRuntimeValue(context, fields[10], value.persons);
      final cursor11 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor11.writeVarUint32(value.player.index);
      cursor11.finish();
      return;
    }
    final writers = _mediaForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Media read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final bool _hasBitrateValue;
    late final int _durationValue;
    late final int _sizeValue;
    late final int _widthValue;
    late final int _heightValue;
    late final int _bitrateValue;
    late final String _uriValue;
    late final String _copyrightValue;
    late final String _titleValue;
    late final String _formatValue;
    late final List<String> _personsValue;
    late final Player _playerValue;
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      _hasBitrateValue = cursor0.readBool();
      _durationValue = cursor0.readVarInt64();
      _sizeValue = cursor0.readVarInt64();
      _widthValue = cursor0.readVarInt32();
      _heightValue = cursor0.readVarInt32();
      _bitrateValue = cursor0.readVarInt32();
      cursor0.finish();
      _uriValue = context.readString();
      _copyrightValue = context.readString();
      _titleValue = context.readString();
      _formatValue = context.readString();
      _personsValue = readGeneratedDirectListValue<String>(
          context, fields[10], _readMediaPersonsElement);
      final cursor11 = GeneratedReadCursor.start(buffer);
      _playerValue = Player.values[cursor11.readVarUint32()];
      cursor11.finish();
    } else {
      if (session.containsSlot(0)) {
        final rawMedia0 = session.valueForSlot(0);
        _hasBitrateValue = _readMediaHasBitrate(rawMedia0 is DeferredReadRef
            ? context.getReadRef(rawMedia0.id)
            : rawMedia0);
      } else {
        _hasBitrateValue = _readMediaHasBitrate(null);
      }
      if (session.containsSlot(1)) {
        final rawMedia1 = session.valueForSlot(1);
        _durationValue = _readMediaDuration(rawMedia1 is DeferredReadRef
            ? context.getReadRef(rawMedia1.id)
            : rawMedia1);
      } else {
        _durationValue = _readMediaDuration(null);
      }
      if (session.containsSlot(2)) {
        final rawMedia2 = session.valueForSlot(2);
        _sizeValue = _readMediaSize(rawMedia2 is DeferredReadRef
            ? context.getReadRef(rawMedia2.id)
            : rawMedia2);
      } else {
        _sizeValue = _readMediaSize(null);
      }
      if (session.containsSlot(3)) {
        final rawMedia3 = session.valueForSlot(3);
        _widthValue = _readMediaWidth(rawMedia3 is DeferredReadRef
            ? context.getReadRef(rawMedia3.id)
            : rawMedia3);
      } else {
        _widthValue = _readMediaWidth(null);
      }
      if (session.containsSlot(4)) {
        final rawMedia4 = session.valueForSlot(4);
        _heightValue = _readMediaHeight(rawMedia4 is DeferredReadRef
            ? context.getReadRef(rawMedia4.id)
            : rawMedia4);
      } else {
        _heightValue = _readMediaHeight(null);
      }
      if (session.containsSlot(5)) {
        final rawMedia5 = session.valueForSlot(5);
        _bitrateValue = _readMediaBitrate(rawMedia5 is DeferredReadRef
            ? context.getReadRef(rawMedia5.id)
            : rawMedia5);
      } else {
        _bitrateValue = _readMediaBitrate(null);
      }
      if (session.containsSlot(6)) {
        final rawMedia6 = session.valueForSlot(6);
        _uriValue = _readMediaUri(rawMedia6 is DeferredReadRef
            ? context.getReadRef(rawMedia6.id)
            : rawMedia6);
      } else {
        _uriValue = _readMediaUri(null);
      }
      if (session.containsSlot(7)) {
        final rawMedia7 = session.valueForSlot(7);
        _copyrightValue = _readMediaCopyright(rawMedia7 is DeferredReadRef
            ? context.getReadRef(rawMedia7.id)
            : rawMedia7);
      } else {
        _copyrightValue = _readMediaCopyright(null);
      }
      if (session.containsSlot(8)) {
        final rawMedia8 = session.valueForSlot(8);
        _titleValue = _readMediaTitle(rawMedia8 is DeferredReadRef
            ? context.getReadRef(rawMedia8.id)
            : rawMedia8);
      } else {
        _titleValue = _readMediaTitle(null);
      }
      if (session.containsSlot(9)) {
        final rawMedia9 = session.valueForSlot(9);
        _formatValue = _readMediaFormat(rawMedia9 is DeferredReadRef
            ? context.getReadRef(rawMedia9.id)
            : rawMedia9);
      } else {
        _formatValue = _readMediaFormat(null);
      }
      if (session.containsSlot(10)) {
        final rawMedia10 = session.valueForSlot(10);
        _personsValue = _readMediaPersons(rawMedia10 is DeferredReadRef
            ? context.getReadRef(rawMedia10.id)
            : rawMedia10);
      } else {
        _personsValue = _readMediaPersons(null);
      }
      if (session.containsSlot(11)) {
        final rawMedia11 = session.valueForSlot(11);
        _playerValue = _readMediaPlayer(rawMedia11 is DeferredReadRef
            ? context.getReadRef(rawMedia11.id)
            : rawMedia11);
      } else {
        _playerValue = _readMediaPlayer(null);
      }
    }
    final value = Media(
        uri: _uriValue,
        title: _titleValue,
        width: _widthValue,
        height: _heightValue,
        format: _formatValue,
        duration: _durationValue,
        size: _sizeValue,
        bitrate: _bitrateValue,
        hasBitrate: _hasBitrateValue,
        persons: _personsValue,
        player: _playerValue,
        copyright: _copyrightValue);
    context.reference(value);
    return value;
  }
}

bool _readMediaHasBitrate(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field hasBitrate.')))
      : value as bool;
}

int _readMediaDuration(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field duration.')))
      : value as int;
}

int _readMediaSize(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field size.')))
      : value as int;
}

int _readMediaWidth(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field width.')))
      : (value as Int32).value;
}

int _readMediaHeight(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field height.')))
      : (value as Int32).value;
}

int _readMediaBitrate(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field bitrate.')))
      : (value as Int32).value;
}

String _readMediaUri(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field uri.')))
      : value as String;
}

String _readMediaCopyright(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError(
              'Received null for non-nullable field copyright.')))
      : value as String;
}

String _readMediaTitle(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field title.')))
      : value as String;
}

String _readMediaFormat(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field format.')))
      : value as String;
}

String _readMediaPersonsElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable persons item.'))
      : value as String;
}

List<String> _readMediaPersons(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError('Received null for non-nullable field persons.')))
      : List.castFrom<dynamic, String>(value as List);
}

Player _readMediaPlayer(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Player
          : (throw StateError('Received null for non-nullable field player.')))
      : value as Player;
}

const List<GeneratedFieldMetadata> _imageForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'width',
    identifier: '3',
    id: 3,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'height',
    identifier: '4',
    id: 4,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'uri',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'title',
    identifier: '2',
    id: 2,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'size',
    identifier: '5',
    id: 5,
    shape: GeneratedTypeShape(
      type: MediaSize,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _ImageSessionWriter = GeneratedStructFieldWriter<Image>;

void _writeImageSessionField0(
    WriteContext context, GeneratedStructField field, Image value) {
  writeGeneratedStructRuntimeValue(context, field, value.width);
}

void _writeImageSessionField1(
    WriteContext context, GeneratedStructField field, Image value) {
  writeGeneratedStructRuntimeValue(context, field, value.height);
}

void _writeImageSessionField2(
    WriteContext context, GeneratedStructField field, Image value) {
  writeGeneratedStructRuntimeValue(context, field, value.uri);
}

void _writeImageSessionField3(
    WriteContext context, GeneratedStructField field, Image value) {
  writeGeneratedStructRuntimeValue(context, field, value.title);
}

void _writeImageSessionField4(
    WriteContext context, GeneratedStructField field, Image value) {
  writeGeneratedStructRuntimeValue(context, field, value.size);
}

final GeneratedStructRegistration<Image> _imageForyRegistration =
    GeneratedStructRegistration<Image>(
  sessionWritersBySlot: <_ImageSessionWriter>[
    _writeImageSessionField0,
    _writeImageSessionField1,
    _writeImageSessionField2,
    _writeImageSessionField3,
    _writeImageSessionField4,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: Image,
  serializerFactory: _ImageForySerializer.new,
  evolving: true,
  fields: _imageForyFieldMetadata,
);

final class _ImageForySerializer extends Serializer<Image> {
  List<GeneratedStructField>? _generatedFields;

  _ImageForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _imageForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _imageForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Image value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarInt32(value.width);
      cursor0.writeVarInt32(value.height);
      cursor0.finish();
      context.writeString(value.uri);
      context.writeString(value.title);
      final cursor4 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor4.writeVarUint32(value.size.index);
      cursor4.finish();
      return;
    }
    final writers = _imageForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Image read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final int _widthValue;
    late final int _heightValue;
    late final String _uriValue;
    late final String _titleValue;
    late final MediaSize _sizeValue;
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      _widthValue = cursor0.readVarInt32();
      _heightValue = cursor0.readVarInt32();
      cursor0.finish();
      _uriValue = context.readString();
      _titleValue = context.readString();
      final cursor4 = GeneratedReadCursor.start(buffer);
      _sizeValue = MediaSize.values[cursor4.readVarUint32()];
      cursor4.finish();
    } else {
      if (session.containsSlot(0)) {
        final rawImage0 = session.valueForSlot(0);
        _widthValue = _readImageWidth(rawImage0 is DeferredReadRef
            ? context.getReadRef(rawImage0.id)
            : rawImage0);
      } else {
        _widthValue = _readImageWidth(null);
      }
      if (session.containsSlot(1)) {
        final rawImage1 = session.valueForSlot(1);
        _heightValue = _readImageHeight(rawImage1 is DeferredReadRef
            ? context.getReadRef(rawImage1.id)
            : rawImage1);
      } else {
        _heightValue = _readImageHeight(null);
      }
      if (session.containsSlot(2)) {
        final rawImage2 = session.valueForSlot(2);
        _uriValue = _readImageUri(rawImage2 is DeferredReadRef
            ? context.getReadRef(rawImage2.id)
            : rawImage2);
      } else {
        _uriValue = _readImageUri(null);
      }
      if (session.containsSlot(3)) {
        final rawImage3 = session.valueForSlot(3);
        _titleValue = _readImageTitle(rawImage3 is DeferredReadRef
            ? context.getReadRef(rawImage3.id)
            : rawImage3);
      } else {
        _titleValue = _readImageTitle(null);
      }
      if (session.containsSlot(4)) {
        final rawImage4 = session.valueForSlot(4);
        _sizeValue = _readImageSize(rawImage4 is DeferredReadRef
            ? context.getReadRef(rawImage4.id)
            : rawImage4);
      } else {
        _sizeValue = _readImageSize(null);
      }
    }
    final value = Image(
        uri: _uriValue,
        title: _titleValue,
        width: _widthValue,
        height: _heightValue,
        size: _sizeValue);
    context.reference(value);
    return value;
  }
}

int _readImageWidth(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field width.')))
      : (value as Int32).value;
}

int _readImageHeight(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field height.')))
      : (value as Int32).value;
}

String _readImageUri(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field uri.')))
      : value as String;
}

String _readImageTitle(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field title.')))
      : value as String;
}

MediaSize _readImageSize(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as MediaSize
          : (throw StateError('Received null for non-nullable field size.')))
      : value as MediaSize;
}

const List<GeneratedFieldMetadata> _mediaContentForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'images',
    identifier: '2',
    id: 2,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: Image,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'media',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: Media,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _MediaContentSessionWriter = GeneratedStructFieldWriter<MediaContent>;

void _writeMediaContentSessionField0(
    WriteContext context, GeneratedStructField field, MediaContent value) {
  writeGeneratedStructRuntimeValue(context, field, value.images);
}

void _writeMediaContentSessionField1(
    WriteContext context, GeneratedStructField field, MediaContent value) {
  writeGeneratedStructRuntimeValue(context, field, value.media);
}

final GeneratedStructRegistration<MediaContent> _mediaContentForyRegistration =
    GeneratedStructRegistration<MediaContent>(
  sessionWritersBySlot: <_MediaContentSessionWriter>[
    _writeMediaContentSessionField0,
    _writeMediaContentSessionField1,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: MediaContent,
  serializerFactory: _MediaContentForySerializer.new,
  evolving: true,
  fields: _mediaContentForyFieldMetadata,
);

final class _MediaContentForySerializer extends Serializer<MediaContent> {
  List<GeneratedStructField>? _generatedFields;

  _MediaContentForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaContentForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaContentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MediaContent value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.images);
      writeGeneratedStructRuntimeValue(context, fields[1], value.media);
      return;
    }
    final writers = _mediaContentForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MediaContent read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final List<Image> _imagesValue;
    late final Media _mediaValue;
    if (session == null) {
      final fields = _readRuntimeFields(context);
      _imagesValue = readGeneratedDirectListValue<Image>(
          context, fields[0], _readMediaContentImagesElement);
      _mediaValue = _readMediaContentMedia(
          readGeneratedStructRuntimeValue(context, fields[1]));
    } else {
      if (session.containsSlot(0)) {
        final rawMediaContent0 = session.valueForSlot(0);
        _imagesValue = _readMediaContentImages(
            rawMediaContent0 is DeferredReadRef
                ? context.getReadRef(rawMediaContent0.id)
                : rawMediaContent0);
      } else {
        _imagesValue = _readMediaContentImages(null);
      }
      if (session.containsSlot(1)) {
        final rawMediaContent1 = session.valueForSlot(1);
        _mediaValue = _readMediaContentMedia(rawMediaContent1 is DeferredReadRef
            ? context.getReadRef(rawMediaContent1.id)
            : rawMediaContent1);
      } else {
        _mediaValue = _readMediaContentMedia(null);
      }
    }
    final value = MediaContent(media: _mediaValue, images: _imagesValue);
    context.reference(value);
    return value;
  }
}

Image _readMediaContentImagesElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable images item.'))
      : value as Image;
}

List<Image> _readMediaContentImages(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<Image>
          : (throw StateError('Received null for non-nullable field images.')))
      : List.castFrom<dynamic, Image>(value as List);
}

Media _readMediaContentMedia(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Media
          : (throw StateError('Received null for non-nullable field media.')))
      : value as Media;
}

const List<GeneratedFieldMetadata> _structListForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'structList',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: NumericStruct,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
];

typedef _StructListSessionWriter = GeneratedStructFieldWriter<StructList>;

void _writeStructListSessionField0(
    WriteContext context, GeneratedStructField field, StructList value) {
  writeGeneratedStructRuntimeValue(context, field, value.structList);
}

final GeneratedStructRegistration<StructList> _structListForyRegistration =
    GeneratedStructRegistration<StructList>(
  sessionWritersBySlot: <_StructListSessionWriter>[
    _writeStructListSessionField0,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: StructList,
  serializerFactory: _StructListForySerializer.new,
  evolving: true,
  fields: _structListForyFieldMetadata,
);

final class _StructListForySerializer extends Serializer<StructList> {
  List<GeneratedStructField>? _generatedFields;

  _StructListForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structListForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structListForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructList value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.structList);
      return;
    }
    final writers = _structListForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructList read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final List<NumericStruct> _structListValue;
    if (session == null) {
      final fields = _readRuntimeFields(context);
      _structListValue = readGeneratedDirectListValue<NumericStruct>(
          context, fields[0], _readStructListStructListElement);
    } else {
      if (session.containsSlot(0)) {
        final rawStructList0 = session.valueForSlot(0);
        _structListValue = _readStructListStructList(
            rawStructList0 is DeferredReadRef
                ? context.getReadRef(rawStructList0.id)
                : rawStructList0);
      } else {
        _structListValue = _readStructListStructList(null);
      }
    }
    final value = StructList(structList: _structListValue);
    context.reference(value);
    return value;
  }
}

NumericStruct _readStructListStructListElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable structList item.'))
      : value as NumericStruct;
}

List<NumericStruct> _readStructListStructList(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<NumericStruct>
          : (throw StateError(
              'Received null for non-nullable field structList.')))
      : List.castFrom<dynamic, NumericStruct>(value as List);
}

const List<GeneratedFieldMetadata> _sampleListForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'sampleList',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: Sample,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
];

typedef _SampleListSessionWriter = GeneratedStructFieldWriter<SampleList>;

void _writeSampleListSessionField0(
    WriteContext context, GeneratedStructField field, SampleList value) {
  writeGeneratedStructRuntimeValue(context, field, value.sampleList);
}

final GeneratedStructRegistration<SampleList> _sampleListForyRegistration =
    GeneratedStructRegistration<SampleList>(
  sessionWritersBySlot: <_SampleListSessionWriter>[
    _writeSampleListSessionField0,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: SampleList,
  serializerFactory: _SampleListForySerializer.new,
  evolving: true,
  fields: _sampleListForyFieldMetadata,
);

final class _SampleListForySerializer extends Serializer<SampleList> {
  List<GeneratedStructField>? _generatedFields;

  _SampleListForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _sampleListForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _sampleListForyRegistration,
    );
  }

  @override
  void write(WriteContext context, SampleList value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.sampleList);
      return;
    }
    final writers = _sampleListForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  SampleList read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final List<Sample> _sampleListValue;
    if (session == null) {
      final fields = _readRuntimeFields(context);
      _sampleListValue = readGeneratedDirectListValue<Sample>(
          context, fields[0], _readSampleListSampleListElement);
    } else {
      if (session.containsSlot(0)) {
        final rawSampleList0 = session.valueForSlot(0);
        _sampleListValue = _readSampleListSampleList(
            rawSampleList0 is DeferredReadRef
                ? context.getReadRef(rawSampleList0.id)
                : rawSampleList0);
      } else {
        _sampleListValue = _readSampleListSampleList(null);
      }
    }
    final value = SampleList(sampleList: _sampleListValue);
    context.reference(value);
    return value;
  }
}

Sample _readSampleListSampleListElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable sampleList item.'))
      : value as Sample;
}

List<Sample> _readSampleListSampleList(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<Sample>
          : (throw StateError(
              'Received null for non-nullable field sampleList.')))
      : List.castFrom<dynamic, Sample>(value as List);
}

const List<GeneratedFieldMetadata> _mediaContentListForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'mediaContentList',
    identifier: '1',
    id: 1,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: MediaContent,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
];

typedef _MediaContentListSessionWriter
    = GeneratedStructFieldWriter<MediaContentList>;

void _writeMediaContentListSessionField0(
    WriteContext context, GeneratedStructField field, MediaContentList value) {
  writeGeneratedStructRuntimeValue(context, field, value.mediaContentList);
}

final GeneratedStructRegistration<MediaContentList>
    _mediaContentListForyRegistration =
    GeneratedStructRegistration<MediaContentList>(
  sessionWritersBySlot: <_MediaContentListSessionWriter>[
    _writeMediaContentListSessionField0,
  ],
  compatibleFactory: null,
  compatibleReadersBySlot: null,
  type: MediaContentList,
  serializerFactory: _MediaContentListForySerializer.new,
  evolving: true,
  fields: _mediaContentListForyFieldMetadata,
);

final class _MediaContentListForySerializer
    extends Serializer<MediaContentList> {
  List<GeneratedStructField>? _generatedFields;

  _MediaContentListForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaContentListForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _mediaContentListForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MediaContentList value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(
          context, fields[0], value.mediaContentList);
      return;
    }
    final writers = _mediaContentListForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MediaContentList read(ReadContext context) {
    final session = generatedStructReadSession(context);
    late final List<MediaContent> _mediaContentListValue;
    if (session == null) {
      final fields = _readRuntimeFields(context);
      _mediaContentListValue = readGeneratedDirectListValue<MediaContent>(
          context, fields[0], _readMediaContentListMediaContentListElement);
    } else {
      if (session.containsSlot(0)) {
        final rawMediaContentList0 = session.valueForSlot(0);
        _mediaContentListValue = _readMediaContentListMediaContentList(
            rawMediaContentList0 is DeferredReadRef
                ? context.getReadRef(rawMediaContentList0.id)
                : rawMediaContentList0);
      } else {
        _mediaContentListValue = _readMediaContentListMediaContentList(null);
      }
    }
    final value = MediaContentList(mediaContentList: _mediaContentListValue);
    context.reference(value);
    return value;
  }
}

MediaContent _readMediaContentListMediaContentListElement(Object? value) {
  return value == null
      ? (throw StateError(
          'Received null for non-nullable mediaContentList item.'))
      : value as MediaContent;
}

List<MediaContent> _readMediaContentListMediaContentList(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<MediaContent>
          : (throw StateError(
              'Received null for non-nullable field mediaContentList.')))
      : List.castFrom<dynamic, MediaContent>(value as List);
}

final GeneratedEnumRegistration _playerForyRegistration =
    GeneratedEnumRegistration(
  type: Player,
  serializerFactory: _PlayerForySerializer.new,
);

final GeneratedEnumRegistration _mediaSizeForyRegistration =
    GeneratedEnumRegistration(
  type: MediaSize,
  serializerFactory: _MediaSizeForySerializer.new,
);

void registerModelsForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  if (type == Player) {
    registerGeneratedEnum(fory, _playerForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MediaSize) {
    registerGeneratedEnum(fory, _mediaSizeForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NumericStruct) {
    registerGeneratedStruct(fory, _numericStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Sample) {
    registerGeneratedStruct(fory, _sampleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Media) {
    registerGeneratedStruct(fory, _mediaForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Image) {
    registerGeneratedStruct(fory, _imageForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MediaContent) {
    registerGeneratedStruct(fory, _mediaContentForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructList) {
    registerGeneratedStruct(fory, _structListForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == SampleList) {
    registerGeneratedStruct(fory, _sampleListForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MediaContentList) {
    registerGeneratedStruct(fory, _mediaContentListForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void registerModelsForyTypes(Fory fory) {
  registerGeneratedEnum(fory, _playerForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'Player');
  registerGeneratedEnum(fory, _mediaSizeForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'MediaSize');
  registerGeneratedStruct(fory, _numericStructForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'NumericStruct');
  registerGeneratedStruct(fory, _sampleForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'Sample');
  registerGeneratedStruct(fory, _mediaForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'Media');
  registerGeneratedStruct(fory, _imageForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'Image');
  registerGeneratedStruct(fory, _mediaContentForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'MediaContent');
  registerGeneratedStruct(fory, _structListForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'StructList');
  registerGeneratedStruct(fory, _sampleListForyRegistration,
      namespace: 'fory_dart_benchmark/src/models', typeName: 'SampleList');
  registerGeneratedStruct(fory, _mediaContentListForyRegistration,
      namespace: 'fory_dart_benchmark/src/models',
      typeName: 'MediaContentList');
}
