// dart format width=80
// GENERATED CODE - DO NOT MODIFY BY HAND

// **************************************************************************
// ForyGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: implementation_imports, invalid_use_of_internal_member

import 'package:fory/fory.dart';
import 'package:fory/src/codegen/generated_support.dart';
import 'package:fory/src/serializer/serializer_support.dart';
import 'xlang_test_models.dart';

final class _ColorForySerializer extends EnumSerializer<Color> {
  const _ColorForySerializer();
  @override
  void write(WriteContext context, Color value) {
    context.writeVarUint32(value.index);
  }

  @override
  Color read(ReadContext context) {
    return Color.values[context.readVarUint32()];
  }
}

final class _TestEnumForySerializer extends EnumSerializer<TestEnum> {
  const _TestEnumForySerializer();
  @override
  void write(WriteContext context, TestEnum value) {
    context.writeVarUint32(value.index);
  }

  @override
  TestEnum read(ReadContext context) {
    return TestEnum.values[context.readVarUint32()];
  }
}

const List<GeneratedFieldMetadata>
    _twoEnumFieldStructEvolutionForyFieldMetadata = <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f2',
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _TwoEnumFieldStructEvolutionSessionWriter
    = GeneratedStructFieldWriter<TwoEnumFieldStructEvolution>;
typedef _TwoEnumFieldStructEvolutionSessionReader
    = GeneratedStructFieldReader<TwoEnumFieldStructEvolution>;

void _writeTwoEnumFieldStructEvolutionSessionField0(WriteContext context,
    GeneratedStructField field, TwoEnumFieldStructEvolution value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeTwoEnumFieldStructEvolutionSessionField1(WriteContext context,
    GeneratedStructField field, TwoEnumFieldStructEvolution value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _readTwoEnumFieldStructEvolutionSessionField0(
    ReadContext context, TwoEnumFieldStructEvolution value, Object? rawValue) {
  value.f1 = _readTwoEnumFieldStructEvolutionF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoEnumFieldStructEvolutionSessionField1(
    ReadContext context, TwoEnumFieldStructEvolution value, Object? rawValue) {
  value.f2 = _readTwoEnumFieldStructEvolutionF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoEnumFieldStructEvolution>
    _twoEnumFieldStructEvolutionForyRegistration =
    GeneratedStructRegistration<TwoEnumFieldStructEvolution>(
  sessionWritersBySlot: <_TwoEnumFieldStructEvolutionSessionWriter>[
    _writeTwoEnumFieldStructEvolutionSessionField0,
    _writeTwoEnumFieldStructEvolutionSessionField1,
  ],
  compatibleFactory: TwoEnumFieldStructEvolution.new,
  compatibleReadersBySlot: <_TwoEnumFieldStructEvolutionSessionReader>[
    _readTwoEnumFieldStructEvolutionSessionField0,
    _readTwoEnumFieldStructEvolutionSessionField1,
  ],
  type: TwoEnumFieldStructEvolution,
  serializerFactory: _TwoEnumFieldStructEvolutionForySerializer.new,
  evolving: true,
  fields: _twoEnumFieldStructEvolutionForyFieldMetadata,
);

final class _TwoEnumFieldStructEvolutionForySerializer
    extends Serializer<TwoEnumFieldStructEvolution> {
  List<GeneratedStructField>? _generatedFields;

  _TwoEnumFieldStructEvolutionForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoEnumFieldStructEvolutionForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoEnumFieldStructEvolutionForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoEnumFieldStructEvolution value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.writeVarUint32(value.f2.index);
      cursor0.finish();
      return;
    }
    final writers =
        _twoEnumFieldStructEvolutionForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoEnumFieldStructEvolution read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = TwoEnumFieldStructEvolution();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      value.f2 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawTwoEnumFieldStructEvolution0 = session.valueForSlot(0);
      value.f1 = _readTwoEnumFieldStructEvolutionF1(
          rawTwoEnumFieldStructEvolution0 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStructEvolution0.id)
              : rawTwoEnumFieldStructEvolution0,
          value.f1);
    }
    if (session.containsSlot(1)) {
      final rawTwoEnumFieldStructEvolution1 = session.valueForSlot(1);
      value.f2 = _readTwoEnumFieldStructEvolutionF2(
          rawTwoEnumFieldStructEvolution1 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStructEvolution1.id)
              : rawTwoEnumFieldStructEvolution1,
          value.f2);
    }
    return value;
  }
}

TestEnum _readTwoEnumFieldStructEvolutionF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as TestEnum;
}

TestEnum _readTwoEnumFieldStructEvolutionF2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f2.')))
      : value as TestEnum;
}

const List<GeneratedFieldMetadata> _itemForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _ItemSessionWriter = GeneratedStructFieldWriter<Item>;
typedef _ItemSessionReader = GeneratedStructFieldReader<Item>;

void _writeItemSessionField0(
    WriteContext context, GeneratedStructField field, Item value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _readItemSessionField0(ReadContext context, Item value, Object? rawValue) {
  value.name = _readItemName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<Item> _itemForyRegistration =
    GeneratedStructRegistration<Item>(
  sessionWritersBySlot: <_ItemSessionWriter>[
    _writeItemSessionField0,
  ],
  compatibleFactory: Item.new,
  compatibleReadersBySlot: <_ItemSessionReader>[
    _readItemSessionField0,
  ],
  type: Item,
  serializerFactory: _ItemForySerializer.new,
  evolving: true,
  fields: _itemForyFieldMetadata,
);

final class _ItemForySerializer extends Serializer<Item> {
  List<GeneratedStructField>? _generatedFields;

  _ItemForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _itemForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _itemForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Item value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.name);
      return;
    }
    final writers = _itemForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Item read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = Item();
    context.reference(value);
    if (session == null) {
      value.name = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawItem0 = session.valueForSlot(0);
      value.name = _readItemName(
          rawItem0 is DeferredReadRef
              ? context.getReadRef(rawItem0.id)
              : rawItem0,
          value.name);
    }
    return value;
  }
}

String _readItemName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _simpleStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f2',
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f7',
    identifier: 'f7',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f8',
    identifier: 'f8',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'last',
    identifier: 'last',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f4',
    identifier: 'f4',
    id: null,
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
    name: 'f6',
    identifier: 'f6',
    id: null,
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
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: Int32,
          typeId: 5,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        ),
        GeneratedTypeShape(
          type: double,
          typeId: 20,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f3',
    identifier: 'f3',
    id: null,
    shape: GeneratedTypeShape(
      type: Item,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f5',
    identifier: 'f5',
    id: null,
    shape: GeneratedTypeShape(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _SimpleStructSessionWriter = GeneratedStructFieldWriter<SimpleStruct>;
typedef _SimpleStructSessionReader = GeneratedStructFieldReader<SimpleStruct>;

void _writeSimpleStructSessionField0(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _writeSimpleStructSessionField1(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f7);
}

void _writeSimpleStructSessionField2(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f8);
}

void _writeSimpleStructSessionField3(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.last);
}

void _writeSimpleStructSessionField4(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f4);
}

void _writeSimpleStructSessionField5(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f6);
}

void _writeSimpleStructSessionField6(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeSimpleStructSessionField7(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f3);
}

void _writeSimpleStructSessionField8(
    WriteContext context, GeneratedStructField field, SimpleStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f5);
}

void _readSimpleStructSessionField0(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f2 = _readSimpleStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

void _readSimpleStructSessionField1(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f7 = _readSimpleStructF7(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f7);
}

void _readSimpleStructSessionField2(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f8 = _readSimpleStructF8(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f8);
}

void _readSimpleStructSessionField3(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.last = _readSimpleStructLast(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.last);
}

void _readSimpleStructSessionField4(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f4 = _readSimpleStructF4(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f4);
}

void _readSimpleStructSessionField5(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f6 = _readSimpleStructF6(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f6);
}

void _readSimpleStructSessionField6(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f1 = _readSimpleStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readSimpleStructSessionField7(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f3 = _readSimpleStructF3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readSimpleStructSessionField8(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f5 = _readSimpleStructF5(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f5);
}

final GeneratedStructRegistration<SimpleStruct> _simpleStructForyRegistration =
    GeneratedStructRegistration<SimpleStruct>(
  sessionWritersBySlot: <_SimpleStructSessionWriter>[
    _writeSimpleStructSessionField0,
    _writeSimpleStructSessionField1,
    _writeSimpleStructSessionField2,
    _writeSimpleStructSessionField3,
    _writeSimpleStructSessionField4,
    _writeSimpleStructSessionField5,
    _writeSimpleStructSessionField6,
    _writeSimpleStructSessionField7,
    _writeSimpleStructSessionField8,
  ],
  compatibleFactory: SimpleStruct.new,
  compatibleReadersBySlot: <_SimpleStructSessionReader>[
    _readSimpleStructSessionField0,
    _readSimpleStructSessionField1,
    _readSimpleStructSessionField2,
    _readSimpleStructSessionField3,
    _readSimpleStructSessionField4,
    _readSimpleStructSessionField5,
    _readSimpleStructSessionField6,
    _readSimpleStructSessionField7,
    _readSimpleStructSessionField8,
  ],
  type: SimpleStruct,
  serializerFactory: _SimpleStructForySerializer.new,
  evolving: true,
  fields: _simpleStructForyFieldMetadata,
);

final class _SimpleStructForySerializer extends Serializer<SimpleStruct> {
  List<GeneratedStructField>? _generatedFields;

  _SimpleStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _simpleStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _simpleStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, SimpleStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 20);
      cursor0.writeVarInt32(value.f2.value);
      cursor0.writeVarInt32(value.f7.value);
      cursor0.writeVarInt32(value.f8.value);
      cursor0.writeVarInt32(value.last.value);
      cursor0.finish();
      context.writeString(value.f4);
      writeGeneratedStructRuntimeValue(context, fields[5], value.f6);
      writeGeneratedStructRuntimeValue(context, fields[6], value.f1);
      writeGeneratedStructRuntimeValue(context, fields[7], value.f3);
      final cursor8 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor8.writeVarUint32(value.f5.index);
      cursor8.finish();
      return;
    }
    final writers = _simpleStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  SimpleStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = SimpleStruct();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f2 = Int32(cursor0.readVarInt32());
      value.f7 = Int32(cursor0.readVarInt32());
      value.f8 = Int32(cursor0.readVarInt32());
      value.last = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.f4 = context.readString();
      value.f6 = readGeneratedDirectListValue<String>(
          context, fields[5], _readSimpleStructF6Element);
      value.f1 = readGeneratedDirectMapValue<Int32?, double?>(
          context, fields[6], _readSimpleStructF1Key, _readSimpleStructF1Value);
      value.f3 = _readSimpleStructF3(
          readGeneratedStructRuntimeValue(context, fields[7], value.f3),
          value.f3);
      final cursor8 = GeneratedReadCursor.start(buffer);
      value.f5 = Color.values[cursor8.readVarUint32()];
      cursor8.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawSimpleStruct0 = session.valueForSlot(0);
      value.f2 = _readSimpleStructF2(
          rawSimpleStruct0 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct0.id)
              : rawSimpleStruct0,
          value.f2);
    }
    if (session.containsSlot(1)) {
      final rawSimpleStruct1 = session.valueForSlot(1);
      value.f7 = _readSimpleStructF7(
          rawSimpleStruct1 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct1.id)
              : rawSimpleStruct1,
          value.f7);
    }
    if (session.containsSlot(2)) {
      final rawSimpleStruct2 = session.valueForSlot(2);
      value.f8 = _readSimpleStructF8(
          rawSimpleStruct2 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct2.id)
              : rawSimpleStruct2,
          value.f8);
    }
    if (session.containsSlot(3)) {
      final rawSimpleStruct3 = session.valueForSlot(3);
      value.last = _readSimpleStructLast(
          rawSimpleStruct3 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct3.id)
              : rawSimpleStruct3,
          value.last);
    }
    if (session.containsSlot(4)) {
      final rawSimpleStruct4 = session.valueForSlot(4);
      value.f4 = _readSimpleStructF4(
          rawSimpleStruct4 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct4.id)
              : rawSimpleStruct4,
          value.f4);
    }
    if (session.containsSlot(5)) {
      final rawSimpleStruct5 = session.valueForSlot(5);
      value.f6 = _readSimpleStructF6(
          rawSimpleStruct5 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct5.id)
              : rawSimpleStruct5,
          value.f6);
    }
    if (session.containsSlot(6)) {
      final rawSimpleStruct6 = session.valueForSlot(6);
      value.f1 = _readSimpleStructF1(
          rawSimpleStruct6 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct6.id)
              : rawSimpleStruct6,
          value.f1);
    }
    if (session.containsSlot(7)) {
      final rawSimpleStruct7 = session.valueForSlot(7);
      value.f3 = _readSimpleStructF3(
          rawSimpleStruct7 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct7.id)
              : rawSimpleStruct7,
          value.f3);
    }
    if (session.containsSlot(8)) {
      final rawSimpleStruct8 = session.valueForSlot(8);
      value.f5 = _readSimpleStructF5(
          rawSimpleStruct8 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct8.id)
              : rawSimpleStruct8,
          value.f5);
    }
    return value;
  }
}

Int32 _readSimpleStructF2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f2.')))
      : value as Int32;
}

Int32 _readSimpleStructF7(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f7.')))
      : value as Int32;
}

Int32 _readSimpleStructF8(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f8.')))
      : value as Int32;
}

Int32 _readSimpleStructLast(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field last.')))
      : value as Int32;
}

String _readSimpleStructF4(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f4.')))
      : value as String;
}

String _readSimpleStructF6Element(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable f6 item.'))
      : value as String;
}

List<String> _readSimpleStructF6(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError('Received null for non-nullable field f6.')))
      : List.castFrom<dynamic, String>(value as List);
}

Int32? _readSimpleStructF1Key(Object? value) {
  return value == null
      ? null as Int32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as Int32;
}

double? _readSimpleStructF1Value(Object? value) {
  return value == null
      ? null as double?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as double;
}

Map<Int32?, double?> _readSimpleStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<Int32?, double?>
          : (throw StateError('Received null for non-nullable field f1.')))
      : Map.castFrom<dynamic, dynamic, Int32?, double?>(value as Map);
}

Item _readSimpleStructF3(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Item
          : (throw StateError('Received null for non-nullable field f3.')))
      : value as Item;
}

Color _readSimpleStructF5(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError('Received null for non-nullable field f5.')))
      : value as Color;
}

const List<GeneratedFieldMetadata> _evolvingOverrideStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _EvolvingOverrideStructSessionWriter
    = GeneratedStructFieldWriter<EvolvingOverrideStruct>;
typedef _EvolvingOverrideStructSessionReader
    = GeneratedStructFieldReader<EvolvingOverrideStruct>;

void _writeEvolvingOverrideStructSessionField0(WriteContext context,
    GeneratedStructField field, EvolvingOverrideStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _readEvolvingOverrideStructSessionField0(
    ReadContext context, EvolvingOverrideStruct value, Object? rawValue) {
  value.f1 = _readEvolvingOverrideStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<EvolvingOverrideStruct>
    _evolvingOverrideStructForyRegistration =
    GeneratedStructRegistration<EvolvingOverrideStruct>(
  sessionWritersBySlot: <_EvolvingOverrideStructSessionWriter>[
    _writeEvolvingOverrideStructSessionField0,
  ],
  compatibleFactory: EvolvingOverrideStruct.new,
  compatibleReadersBySlot: <_EvolvingOverrideStructSessionReader>[
    _readEvolvingOverrideStructSessionField0,
  ],
  type: EvolvingOverrideStruct,
  serializerFactory: _EvolvingOverrideStructForySerializer.new,
  evolving: true,
  fields: _evolvingOverrideStructForyFieldMetadata,
);

final class _EvolvingOverrideStructForySerializer
    extends Serializer<EvolvingOverrideStruct> {
  List<GeneratedStructField>? _generatedFields;

  _EvolvingOverrideStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _evolvingOverrideStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _evolvingOverrideStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EvolvingOverrideStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.f1);
      return;
    }
    final writers =
        _evolvingOverrideStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EvolvingOverrideStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = EvolvingOverrideStruct();
    context.reference(value);
    if (session == null) {
      value.f1 = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawEvolvingOverrideStruct0 = session.valueForSlot(0);
      value.f1 = _readEvolvingOverrideStructF1(
          rawEvolvingOverrideStruct0 is DeferredReadRef
              ? context.getReadRef(rawEvolvingOverrideStruct0.id)
              : rawEvolvingOverrideStruct0,
          value.f1);
    }
    return value;
  }
}

String _readEvolvingOverrideStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _fixedOverrideStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _FixedOverrideStructSessionWriter
    = GeneratedStructFieldWriter<FixedOverrideStruct>;
typedef _FixedOverrideStructSessionReader
    = GeneratedStructFieldReader<FixedOverrideStruct>;

void _writeFixedOverrideStructSessionField0(WriteContext context,
    GeneratedStructField field, FixedOverrideStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _readFixedOverrideStructSessionField0(
    ReadContext context, FixedOverrideStruct value, Object? rawValue) {
  value.f1 = _readFixedOverrideStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<FixedOverrideStruct>
    _fixedOverrideStructForyRegistration =
    GeneratedStructRegistration<FixedOverrideStruct>(
  sessionWritersBySlot: <_FixedOverrideStructSessionWriter>[
    _writeFixedOverrideStructSessionField0,
  ],
  compatibleFactory: FixedOverrideStruct.new,
  compatibleReadersBySlot: <_FixedOverrideStructSessionReader>[
    _readFixedOverrideStructSessionField0,
  ],
  type: FixedOverrideStruct,
  serializerFactory: _FixedOverrideStructForySerializer.new,
  evolving: false,
  fields: _fixedOverrideStructForyFieldMetadata,
);

final class _FixedOverrideStructForySerializer
    extends Serializer<FixedOverrideStruct> {
  List<GeneratedStructField>? _generatedFields;

  _FixedOverrideStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _fixedOverrideStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _fixedOverrideStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, FixedOverrideStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.f1);
      return;
    }
    final writers = _fixedOverrideStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  FixedOverrideStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = FixedOverrideStruct();
    context.reference(value);
    if (session == null) {
      value.f1 = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawFixedOverrideStruct0 = session.valueForSlot(0);
      value.f1 = _readFixedOverrideStructF1(
          rawFixedOverrideStruct0 is DeferredReadRef
              ? context.getReadRef(rawFixedOverrideStruct0.id)
              : rawFixedOverrideStruct0,
          value.f1);
    }
    return value;
  }
}

String _readFixedOverrideStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _item1ForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f2',
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f3',
    identifier: 'f3',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f4',
    identifier: 'f4',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f5',
    identifier: 'f5',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f6',
    identifier: 'f6',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _Item1SessionWriter = GeneratedStructFieldWriter<Item1>;
typedef _Item1SessionReader = GeneratedStructFieldReader<Item1>;

void _writeItem1SessionField0(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeItem1SessionField1(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _writeItem1SessionField2(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f3);
}

void _writeItem1SessionField3(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f4);
}

void _writeItem1SessionField4(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f5);
}

void _writeItem1SessionField5(
    WriteContext context, GeneratedStructField field, Item1 value) {
  writeGeneratedStructRuntimeValue(context, field, value.f6);
}

void _readItem1SessionField0(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f1 = _readItem1F1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readItem1SessionField1(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f2 = _readItem1F2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

void _readItem1SessionField2(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f3 = _readItem1F3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readItem1SessionField3(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f4 = _readItem1F4(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f4);
}

void _readItem1SessionField4(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f5 = _readItem1F5(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f5);
}

void _readItem1SessionField5(
    ReadContext context, Item1 value, Object? rawValue) {
  value.f6 = _readItem1F6(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f6);
}

final GeneratedStructRegistration<Item1> _item1ForyRegistration =
    GeneratedStructRegistration<Item1>(
  sessionWritersBySlot: <_Item1SessionWriter>[
    _writeItem1SessionField0,
    _writeItem1SessionField1,
    _writeItem1SessionField2,
    _writeItem1SessionField3,
    _writeItem1SessionField4,
    _writeItem1SessionField5,
  ],
  compatibleFactory: Item1.new,
  compatibleReadersBySlot: <_Item1SessionReader>[
    _readItem1SessionField0,
    _readItem1SessionField1,
    _readItem1SessionField2,
    _readItem1SessionField3,
    _readItem1SessionField4,
    _readItem1SessionField5,
  ],
  type: Item1,
  serializerFactory: _Item1ForySerializer.new,
  evolving: true,
  fields: _item1ForyFieldMetadata,
);

final class _Item1ForySerializer extends Serializer<Item1> {
  List<GeneratedStructField>? _generatedFields;

  _Item1ForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _item1ForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _item1ForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Item1 value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 30);
      cursor0.writeVarInt32(value.f1.value);
      cursor0.writeVarInt32(value.f2.value);
      cursor0.writeVarInt32(value.f3.value);
      cursor0.writeVarInt32(value.f4.value);
      cursor0.writeVarInt32(value.f5.value);
      cursor0.writeVarInt32(value.f6.value);
      cursor0.finish();
      return;
    }
    final writers = _item1ForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Item1 read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = Item1();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = Int32(cursor0.readVarInt32());
      value.f2 = Int32(cursor0.readVarInt32());
      value.f3 = Int32(cursor0.readVarInt32());
      value.f4 = Int32(cursor0.readVarInt32());
      value.f5 = Int32(cursor0.readVarInt32());
      value.f6 = Int32(cursor0.readVarInt32());
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawItem10 = session.valueForSlot(0);
      value.f1 = _readItem1F1(
          rawItem10 is DeferredReadRef
              ? context.getReadRef(rawItem10.id)
              : rawItem10,
          value.f1);
    }
    if (session.containsSlot(1)) {
      final rawItem11 = session.valueForSlot(1);
      value.f2 = _readItem1F2(
          rawItem11 is DeferredReadRef
              ? context.getReadRef(rawItem11.id)
              : rawItem11,
          value.f2);
    }
    if (session.containsSlot(2)) {
      final rawItem12 = session.valueForSlot(2);
      value.f3 = _readItem1F3(
          rawItem12 is DeferredReadRef
              ? context.getReadRef(rawItem12.id)
              : rawItem12,
          value.f3);
    }
    if (session.containsSlot(3)) {
      final rawItem13 = session.valueForSlot(3);
      value.f4 = _readItem1F4(
          rawItem13 is DeferredReadRef
              ? context.getReadRef(rawItem13.id)
              : rawItem13,
          value.f4);
    }
    if (session.containsSlot(4)) {
      final rawItem14 = session.valueForSlot(4);
      value.f5 = _readItem1F5(
          rawItem14 is DeferredReadRef
              ? context.getReadRef(rawItem14.id)
              : rawItem14,
          value.f5);
    }
    if (session.containsSlot(5)) {
      final rawItem15 = session.valueForSlot(5);
      value.f6 = _readItem1F6(
          rawItem15 is DeferredReadRef
              ? context.getReadRef(rawItem15.id)
              : rawItem15,
          value.f6);
    }
    return value;
  }
}

Int32 _readItem1F1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as Int32;
}

Int32 _readItem1F2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f2.')))
      : value as Int32;
}

Int32 _readItem1F3(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f3.')))
      : value as Int32;
}

Int32 _readItem1F4(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f4.')))
      : value as Int32;
}

Int32 _readItem1F5(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f5.')))
      : value as Int32;
}

Int32 _readItem1F6(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field f6.')))
      : value as Int32;
}

const List<GeneratedFieldMetadata> _structWithUnion2ForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'union',
    identifier: 'union',
    id: null,
    shape: GeneratedTypeShape(
      type: Union2,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _StructWithUnion2SessionWriter
    = GeneratedStructFieldWriter<StructWithUnion2>;
typedef _StructWithUnion2SessionReader
    = GeneratedStructFieldReader<StructWithUnion2>;

void _writeStructWithUnion2SessionField0(
    WriteContext context, GeneratedStructField field, StructWithUnion2 value) {
  writeGeneratedStructRuntimeValue(context, field, value.union);
}

void _readStructWithUnion2SessionField0(
    ReadContext context, StructWithUnion2 value, Object? rawValue) {
  value.union = _readStructWithUnion2Union(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.union);
}

final GeneratedStructRegistration<StructWithUnion2>
    _structWithUnion2ForyRegistration =
    GeneratedStructRegistration<StructWithUnion2>(
  sessionWritersBySlot: <_StructWithUnion2SessionWriter>[
    _writeStructWithUnion2SessionField0,
  ],
  compatibleFactory: StructWithUnion2.new,
  compatibleReadersBySlot: <_StructWithUnion2SessionReader>[
    _readStructWithUnion2SessionField0,
  ],
  type: StructWithUnion2,
  serializerFactory: _StructWithUnion2ForySerializer.new,
  evolving: true,
  fields: _structWithUnion2ForyFieldMetadata,
);

final class _StructWithUnion2ForySerializer
    extends Serializer<StructWithUnion2> {
  List<GeneratedStructField>? _generatedFields;

  _StructWithUnion2ForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithUnion2ForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithUnion2ForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithUnion2 value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.union);
      return;
    }
    final writers = _structWithUnion2ForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = StructWithUnion2();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.union = _readStructWithUnion2Union(
          readGeneratedStructRuntimeValue(context, fields[0], value.union),
          value.union);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawStructWithUnion20 = session.valueForSlot(0);
      value.union = _readStructWithUnion2Union(
          rawStructWithUnion20 is DeferredReadRef
              ? context.getReadRef(rawStructWithUnion20.id)
              : rawStructWithUnion20,
          value.union);
    }
    return value;
  }
}

Union2 _readStructWithUnion2Union(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Union2
          : (throw StateError('Received null for non-nullable field union.')))
      : value as Union2;
}

const List<GeneratedFieldMetadata> _structWithListForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'items',
    identifier: 'items',
    id: null,
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
];

typedef _StructWithListSessionWriter
    = GeneratedStructFieldWriter<StructWithList>;
typedef _StructWithListSessionReader
    = GeneratedStructFieldReader<StructWithList>;

void _writeStructWithListSessionField0(
    WriteContext context, GeneratedStructField field, StructWithList value) {
  writeGeneratedStructRuntimeValue(context, field, value.items);
}

void _readStructWithListSessionField0(
    ReadContext context, StructWithList value, Object? rawValue) {
  value.items = _readStructWithListItems(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.items);
}

final GeneratedStructRegistration<StructWithList>
    _structWithListForyRegistration =
    GeneratedStructRegistration<StructWithList>(
  sessionWritersBySlot: <_StructWithListSessionWriter>[
    _writeStructWithListSessionField0,
  ],
  compatibleFactory: StructWithList.new,
  compatibleReadersBySlot: <_StructWithListSessionReader>[
    _readStructWithListSessionField0,
  ],
  type: StructWithList,
  serializerFactory: _StructWithListForySerializer.new,
  evolving: true,
  fields: _structWithListForyFieldMetadata,
);

final class _StructWithListForySerializer extends Serializer<StructWithList> {
  List<GeneratedStructField>? _generatedFields;

  _StructWithListForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithListForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithListForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithList value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.items);
      return;
    }
    final writers = _structWithListForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithList read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = StructWithList();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.items = readGeneratedDirectListValue<String?>(
          context, fields[0], _readStructWithListItemsElement);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawStructWithList0 = session.valueForSlot(0);
      value.items = _readStructWithListItems(
          rawStructWithList0 is DeferredReadRef
              ? context.getReadRef(rawStructWithList0.id)
              : rawStructWithList0,
          value.items);
    }
    return value;
  }
}

String? _readStructWithListItemsElement(Object? value) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

List<String?> _readStructWithListItems(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String?>
          : (throw StateError('Received null for non-nullable field items.')))
      : List.castFrom<dynamic, String?>(value as List);
}

const List<GeneratedFieldMetadata> _structWithMapForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'data',
    identifier: 'data',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
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
];

typedef _StructWithMapSessionWriter = GeneratedStructFieldWriter<StructWithMap>;
typedef _StructWithMapSessionReader = GeneratedStructFieldReader<StructWithMap>;

void _writeStructWithMapSessionField0(
    WriteContext context, GeneratedStructField field, StructWithMap value) {
  writeGeneratedStructRuntimeValue(context, field, value.data);
}

void _readStructWithMapSessionField0(
    ReadContext context, StructWithMap value, Object? rawValue) {
  value.data = _readStructWithMapData(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.data);
}

final GeneratedStructRegistration<StructWithMap>
    _structWithMapForyRegistration = GeneratedStructRegistration<StructWithMap>(
  sessionWritersBySlot: <_StructWithMapSessionWriter>[
    _writeStructWithMapSessionField0,
  ],
  compatibleFactory: StructWithMap.new,
  compatibleReadersBySlot: <_StructWithMapSessionReader>[
    _readStructWithMapSessionField0,
  ],
  type: StructWithMap,
  serializerFactory: _StructWithMapForySerializer.new,
  evolving: true,
  fields: _structWithMapForyFieldMetadata,
);

final class _StructWithMapForySerializer extends Serializer<StructWithMap> {
  List<GeneratedStructField>? _generatedFields;

  _StructWithMapForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithMapForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _structWithMapForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithMap value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.data);
      return;
    }
    final writers = _structWithMapForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithMap read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = StructWithMap();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.data = readGeneratedDirectMapValue<String?, String?>(context,
          fields[0], _readStructWithMapDataKey, _readStructWithMapDataValue);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawStructWithMap0 = session.valueForSlot(0);
      value.data = _readStructWithMapData(
          rawStructWithMap0 is DeferredReadRef
              ? context.getReadRef(rawStructWithMap0.id)
              : rawStructWithMap0,
          value.data);
    }
    return value;
  }
}

String? _readStructWithMapDataKey(Object? value) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

String? _readStructWithMapDataValue(Object? value) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

Map<String?, String?> _readStructWithMapData(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String?, String?>
          : (throw StateError('Received null for non-nullable field data.')))
      : Map.castFrom<dynamic, dynamic, String?, String?>(value as Map);
}

const List<GeneratedFieldMetadata> _myStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'id',
    identifier: 'id',
    id: null,
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

typedef _MyStructSessionWriter = GeneratedStructFieldWriter<MyStruct>;
typedef _MyStructSessionReader = GeneratedStructFieldReader<MyStruct>;

void _writeMyStructSessionField0(
    WriteContext context, GeneratedStructField field, MyStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.id);
}

void _readMyStructSessionField0(
    ReadContext context, MyStruct value, Object? rawValue) {
  value.id = _readMyStructId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

final GeneratedStructRegistration<MyStruct> _myStructForyRegistration =
    GeneratedStructRegistration<MyStruct>(
  sessionWritersBySlot: <_MyStructSessionWriter>[
    _writeMyStructSessionField0,
  ],
  compatibleFactory: MyStruct.new,
  compatibleReadersBySlot: <_MyStructSessionReader>[
    _readMyStructSessionField0,
  ],
  type: MyStruct,
  serializerFactory: _MyStructForySerializer.new,
  evolving: true,
  fields: _myStructForyFieldMetadata,
);

final class _MyStructForySerializer extends Serializer<MyStruct> {
  List<GeneratedStructField>? _generatedFields;

  _MyStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _myStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _myStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MyStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      return;
    }
    final writers = _myStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MyStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = MyStruct();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawMyStruct0 = session.valueForSlot(0);
      value.id = _readMyStructId(
          rawMyStruct0 is DeferredReadRef
              ? context.getReadRef(rawMyStruct0.id)
              : rawMyStruct0,
          value.id);
    }
    return value;
  }
}

int _readMyStructId(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field id.')))
      : (value as Int32).value;
}

const List<GeneratedFieldMetadata> _myWrapperForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'color',
    identifier: 'color',
    id: null,
    shape: GeneratedTypeShape(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'myExt',
    identifier: 'my_ext',
    id: null,
    shape: GeneratedTypeShape(
      type: MyExt,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'myStruct',
    identifier: 'my_struct',
    id: null,
    shape: GeneratedTypeShape(
      type: MyStruct,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _MyWrapperSessionWriter = GeneratedStructFieldWriter<MyWrapper>;
typedef _MyWrapperSessionReader = GeneratedStructFieldReader<MyWrapper>;

void _writeMyWrapperSessionField0(
    WriteContext context, GeneratedStructField field, MyWrapper value) {
  writeGeneratedStructRuntimeValue(context, field, value.color);
}

void _writeMyWrapperSessionField1(
    WriteContext context, GeneratedStructField field, MyWrapper value) {
  writeGeneratedStructRuntimeValue(context, field, value.myExt);
}

void _writeMyWrapperSessionField2(
    WriteContext context, GeneratedStructField field, MyWrapper value) {
  writeGeneratedStructRuntimeValue(context, field, value.myStruct);
}

void _readMyWrapperSessionField0(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.color = _readMyWrapperColor(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.color);
}

void _readMyWrapperSessionField1(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.myExt = _readMyWrapperMyExt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.myExt);
}

void _readMyWrapperSessionField2(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.myStruct = _readMyWrapperMyStruct(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.myStruct);
}

final GeneratedStructRegistration<MyWrapper> _myWrapperForyRegistration =
    GeneratedStructRegistration<MyWrapper>(
  sessionWritersBySlot: <_MyWrapperSessionWriter>[
    _writeMyWrapperSessionField0,
    _writeMyWrapperSessionField1,
    _writeMyWrapperSessionField2,
  ],
  compatibleFactory: MyWrapper.new,
  compatibleReadersBySlot: <_MyWrapperSessionReader>[
    _readMyWrapperSessionField0,
    _readMyWrapperSessionField1,
    _readMyWrapperSessionField2,
  ],
  type: MyWrapper,
  serializerFactory: _MyWrapperForySerializer.new,
  evolving: true,
  fields: _myWrapperForyFieldMetadata,
);

final class _MyWrapperForySerializer extends Serializer<MyWrapper> {
  List<GeneratedStructField>? _generatedFields;

  _MyWrapperForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _myWrapperForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _myWrapperForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MyWrapper value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarUint32(value.color.index);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(context, fields[1], value.myExt);
      writeGeneratedStructRuntimeValue(context, fields[2], value.myStruct);
      return;
    }
    final writers = _myWrapperForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MyWrapper read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = MyWrapper();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.color = Color.values[cursor0.readVarUint32()];
      cursor0.finish();
      value.myExt = _readMyWrapperMyExt(
          readGeneratedStructRuntimeValue(context, fields[1], value.myExt),
          value.myExt);
      value.myStruct = _readMyWrapperMyStruct(
          readGeneratedStructRuntimeValue(context, fields[2], value.myStruct),
          value.myStruct);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawMyWrapper0 = session.valueForSlot(0);
      value.color = _readMyWrapperColor(
          rawMyWrapper0 is DeferredReadRef
              ? context.getReadRef(rawMyWrapper0.id)
              : rawMyWrapper0,
          value.color);
    }
    if (session.containsSlot(1)) {
      final rawMyWrapper1 = session.valueForSlot(1);
      value.myExt = _readMyWrapperMyExt(
          rawMyWrapper1 is DeferredReadRef
              ? context.getReadRef(rawMyWrapper1.id)
              : rawMyWrapper1,
          value.myExt);
    }
    if (session.containsSlot(2)) {
      final rawMyWrapper2 = session.valueForSlot(2);
      value.myStruct = _readMyWrapperMyStruct(
          rawMyWrapper2 is DeferredReadRef
              ? context.getReadRef(rawMyWrapper2.id)
              : rawMyWrapper2,
          value.myStruct);
    }
    return value;
  }
}

Color _readMyWrapperColor(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError('Received null for non-nullable field color.')))
      : value as Color;
}

MyExt _readMyWrapperMyExt(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as MyExt
          : (throw StateError('Received null for non-nullable field myExt.')))
      : value as MyExt;
}

MyStruct _readMyWrapperMyStruct(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as MyStruct
          : (throw StateError(
              'Received null for non-nullable field myStruct.')))
      : value as MyStruct;
}

const List<GeneratedFieldMetadata> _emptyWrapperForyFieldMetadata =
    <GeneratedFieldMetadata>[];

typedef _EmptyWrapperSessionWriter = GeneratedStructFieldWriter<EmptyWrapper>;
typedef _EmptyWrapperSessionReader = GeneratedStructFieldReader<EmptyWrapper>;

final GeneratedStructRegistration<EmptyWrapper> _emptyWrapperForyRegistration =
    GeneratedStructRegistration<EmptyWrapper>(
  sessionWritersBySlot: <_EmptyWrapperSessionWriter>[],
  compatibleFactory: EmptyWrapper.new,
  compatibleReadersBySlot: <_EmptyWrapperSessionReader>[],
  type: EmptyWrapper,
  serializerFactory: _EmptyWrapperForySerializer.new,
  evolving: true,
  fields: _emptyWrapperForyFieldMetadata,
);

final class _EmptyWrapperForySerializer extends Serializer<EmptyWrapper> {
  List<GeneratedStructField>? _generatedFields;

  _EmptyWrapperForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _emptyWrapperForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _emptyWrapperForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EmptyWrapper value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      return;
    }
    final writers = _emptyWrapperForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EmptyWrapper read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = EmptyWrapper();
    context.reference(value);
    if (session == null) {
      return value;
    }
    return value;
  }
}

const List<GeneratedFieldMetadata> _versionCheckStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f3',
    identifier: 'f3',
    id: null,
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
    name: 'f1',
    identifier: 'f1',
    id: null,
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
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _VersionCheckStructSessionWriter
    = GeneratedStructFieldWriter<VersionCheckStruct>;
typedef _VersionCheckStructSessionReader
    = GeneratedStructFieldReader<VersionCheckStruct>;

void _writeVersionCheckStructSessionField0(WriteContext context,
    GeneratedStructField field, VersionCheckStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f3);
}

void _writeVersionCheckStructSessionField1(WriteContext context,
    GeneratedStructField field, VersionCheckStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeVersionCheckStructSessionField2(WriteContext context,
    GeneratedStructField field, VersionCheckStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _readVersionCheckStructSessionField0(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f3 = _readVersionCheckStructF3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readVersionCheckStructSessionField1(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f1 = _readVersionCheckStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readVersionCheckStructSessionField2(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f2 = _readVersionCheckStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<VersionCheckStruct>
    _versionCheckStructForyRegistration =
    GeneratedStructRegistration<VersionCheckStruct>(
  sessionWritersBySlot: <_VersionCheckStructSessionWriter>[
    _writeVersionCheckStructSessionField0,
    _writeVersionCheckStructSessionField1,
    _writeVersionCheckStructSessionField2,
  ],
  compatibleFactory: VersionCheckStruct.new,
  compatibleReadersBySlot: <_VersionCheckStructSessionReader>[
    _readVersionCheckStructSessionField0,
    _readVersionCheckStructSessionField1,
    _readVersionCheckStructSessionField2,
  ],
  type: VersionCheckStruct,
  serializerFactory: _VersionCheckStructForySerializer.new,
  evolving: true,
  fields: _versionCheckStructForyFieldMetadata,
);

final class _VersionCheckStructForySerializer
    extends Serializer<VersionCheckStruct> {
  List<GeneratedStructField>? _generatedFields;

  _VersionCheckStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _versionCheckStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _versionCheckStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, VersionCheckStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 13);
      cursor0.writeFloat64(value.f3);
      cursor0.writeVarInt32(value.f1);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(context, fields[2], value.f2);
      return;
    }
    final writers = _versionCheckStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  VersionCheckStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = VersionCheckStruct();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f3 = cursor0.readFloat64();
      value.f1 = cursor0.readVarInt32();
      cursor0.finish();
      value.f2 = _readVersionCheckStructF2(
          readGeneratedStructRuntimeValue(context, fields[2], value.f2),
          value.f2);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawVersionCheckStruct0 = session.valueForSlot(0);
      value.f3 = _readVersionCheckStructF3(
          rawVersionCheckStruct0 is DeferredReadRef
              ? context.getReadRef(rawVersionCheckStruct0.id)
              : rawVersionCheckStruct0,
          value.f3);
    }
    if (session.containsSlot(1)) {
      final rawVersionCheckStruct1 = session.valueForSlot(1);
      value.f1 = _readVersionCheckStructF1(
          rawVersionCheckStruct1 is DeferredReadRef
              ? context.getReadRef(rawVersionCheckStruct1.id)
              : rawVersionCheckStruct1,
          value.f1);
    }
    if (session.containsSlot(2)) {
      final rawVersionCheckStruct2 = session.valueForSlot(2);
      value.f2 = _readVersionCheckStructF2(
          rawVersionCheckStruct2 is DeferredReadRef
              ? context.getReadRef(rawVersionCheckStruct2.id)
              : rawVersionCheckStruct2,
          value.f2);
    }
    return value;
  }
}

double _readVersionCheckStructF3(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError('Received null for non-nullable field f3.')))
      : value as double;
}

int _readVersionCheckStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field f1.')))
      : (value as Int32).value;
}

String? _readVersionCheckStructF2(Object? value, [Object? fallback]) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

const List<GeneratedFieldMetadata> _dogForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'age',
    identifier: 'age',
    id: null,
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
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _DogSessionWriter = GeneratedStructFieldWriter<Dog>;
typedef _DogSessionReader = GeneratedStructFieldReader<Dog>;

void _writeDogSessionField0(
    WriteContext context, GeneratedStructField field, Dog value) {
  writeGeneratedStructRuntimeValue(context, field, value.age);
}

void _writeDogSessionField1(
    WriteContext context, GeneratedStructField field, Dog value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _readDogSessionField0(ReadContext context, Dog value, Object? rawValue) {
  value.age = _readDogAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readDogSessionField1(ReadContext context, Dog value, Object? rawValue) {
  value.name = _readDogName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<Dog> _dogForyRegistration =
    GeneratedStructRegistration<Dog>(
  sessionWritersBySlot: <_DogSessionWriter>[
    _writeDogSessionField0,
    _writeDogSessionField1,
  ],
  compatibleFactory: Dog.new,
  compatibleReadersBySlot: <_DogSessionReader>[
    _readDogSessionField0,
    _readDogSessionField1,
  ],
  type: Dog,
  serializerFactory: _DogForySerializer.new,
  evolving: true,
  fields: _dogForyFieldMetadata,
);

final class _DogForySerializer extends Serializer<Dog> {
  List<GeneratedStructField>? _generatedFields;

  _DogForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _dogForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _dogForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Dog value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.age);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(context, fields[1], value.name);
      return;
    }
    final writers = _dogForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Dog read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = Dog();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.age = cursor0.readVarInt32();
      cursor0.finish();
      value.name = _readDogName(
          readGeneratedStructRuntimeValue(context, fields[1], value.name),
          value.name);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawDog0 = session.valueForSlot(0);
      value.age = _readDogAge(
          rawDog0 is DeferredReadRef ? context.getReadRef(rawDog0.id) : rawDog0,
          value.age);
    }
    if (session.containsSlot(1)) {
      final rawDog1 = session.valueForSlot(1);
      value.name = _readDogName(
          rawDog1 is DeferredReadRef ? context.getReadRef(rawDog1.id) : rawDog1,
          value.name);
    }
    return value;
  }
}

int _readDogAge(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field age.')))
      : (value as Int32).value;
}

String? _readDogName(Object? value, [Object? fallback]) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

const List<GeneratedFieldMetadata> _catForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'age',
    identifier: 'age',
    id: null,
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
    name: 'lives',
    identifier: 'lives',
    id: null,
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

typedef _CatSessionWriter = GeneratedStructFieldWriter<Cat>;
typedef _CatSessionReader = GeneratedStructFieldReader<Cat>;

void _writeCatSessionField0(
    WriteContext context, GeneratedStructField field, Cat value) {
  writeGeneratedStructRuntimeValue(context, field, value.age);
}

void _writeCatSessionField1(
    WriteContext context, GeneratedStructField field, Cat value) {
  writeGeneratedStructRuntimeValue(context, field, value.lives);
}

void _readCatSessionField0(ReadContext context, Cat value, Object? rawValue) {
  value.age = _readCatAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readCatSessionField1(ReadContext context, Cat value, Object? rawValue) {
  value.lives = _readCatLives(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.lives);
}

final GeneratedStructRegistration<Cat> _catForyRegistration =
    GeneratedStructRegistration<Cat>(
  sessionWritersBySlot: <_CatSessionWriter>[
    _writeCatSessionField0,
    _writeCatSessionField1,
  ],
  compatibleFactory: Cat.new,
  compatibleReadersBySlot: <_CatSessionReader>[
    _readCatSessionField0,
    _readCatSessionField1,
  ],
  type: Cat,
  serializerFactory: _CatForySerializer.new,
  evolving: true,
  fields: _catForyFieldMetadata,
);

final class _CatForySerializer extends Serializer<Cat> {
  List<GeneratedStructField>? _generatedFields;

  _CatForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _catForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _catForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Cat value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarInt32(value.age);
      cursor0.writeVarInt32(value.lives);
      cursor0.finish();
      return;
    }
    final writers = _catForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Cat read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = Cat();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.age = cursor0.readVarInt32();
      value.lives = cursor0.readVarInt32();
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawCat0 = session.valueForSlot(0);
      value.age = _readCatAge(
          rawCat0 is DeferredReadRef ? context.getReadRef(rawCat0.id) : rawCat0,
          value.age);
    }
    if (session.containsSlot(1)) {
      final rawCat1 = session.valueForSlot(1);
      value.lives = _readCatLives(
          rawCat1 is DeferredReadRef ? context.getReadRef(rawCat1.id) : rawCat1,
          value.lives);
    }
    return value;
  }
}

int _readCatAge(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field age.')))
      : (value as Int32).value;
}

int _readCatLives(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field lives.')))
      : (value as Int32).value;
}

const List<GeneratedFieldMetadata> _animalListHolderForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'animals',
    identifier: 'animals',
    id: null,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: Animal,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: true,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
];

typedef _AnimalListHolderSessionWriter
    = GeneratedStructFieldWriter<AnimalListHolder>;
typedef _AnimalListHolderSessionReader
    = GeneratedStructFieldReader<AnimalListHolder>;

void _writeAnimalListHolderSessionField0(
    WriteContext context, GeneratedStructField field, AnimalListHolder value) {
  writeGeneratedStructRuntimeValue(context, field, value.animals);
}

void _readAnimalListHolderSessionField0(
    ReadContext context, AnimalListHolder value, Object? rawValue) {
  value.animals = _readAnimalListHolderAnimals(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.animals);
}

final GeneratedStructRegistration<AnimalListHolder>
    _animalListHolderForyRegistration =
    GeneratedStructRegistration<AnimalListHolder>(
  sessionWritersBySlot: <_AnimalListHolderSessionWriter>[
    _writeAnimalListHolderSessionField0,
  ],
  compatibleFactory: AnimalListHolder.new,
  compatibleReadersBySlot: <_AnimalListHolderSessionReader>[
    _readAnimalListHolderSessionField0,
  ],
  type: AnimalListHolder,
  serializerFactory: _AnimalListHolderForySerializer.new,
  evolving: true,
  fields: _animalListHolderForyFieldMetadata,
);

final class _AnimalListHolderForySerializer
    extends Serializer<AnimalListHolder> {
  List<GeneratedStructField>? _generatedFields;

  _AnimalListHolderForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _animalListHolderForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _animalListHolderForyRegistration,
    );
  }

  @override
  void write(WriteContext context, AnimalListHolder value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.animals);
      return;
    }
    final writers = _animalListHolderForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  AnimalListHolder read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = AnimalListHolder();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.animals = readGeneratedDirectListValue<Animal>(
          context, fields[0], _readAnimalListHolderAnimalsElement);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawAnimalListHolder0 = session.valueForSlot(0);
      value.animals = _readAnimalListHolderAnimals(
          rawAnimalListHolder0 is DeferredReadRef
              ? context.getReadRef(rawAnimalListHolder0.id)
              : rawAnimalListHolder0,
          value.animals);
    }
    return value;
  }
}

Animal _readAnimalListHolderAnimalsElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable animals item.'))
      : value as Animal;
}

List<Animal> _readAnimalListHolderAnimals(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<Animal>
          : (throw StateError('Received null for non-nullable field animals.')))
      : List.castFrom<dynamic, Animal>(value as List);
}

const List<GeneratedFieldMetadata> _animalMapHolderForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'animalMap',
    identifier: 'animal_map',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
        GeneratedTypeShape(
          type: Animal,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: true,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
];

typedef _AnimalMapHolderSessionWriter
    = GeneratedStructFieldWriter<AnimalMapHolder>;
typedef _AnimalMapHolderSessionReader
    = GeneratedStructFieldReader<AnimalMapHolder>;

void _writeAnimalMapHolderSessionField0(
    WriteContext context, GeneratedStructField field, AnimalMapHolder value) {
  writeGeneratedStructRuntimeValue(context, field, value.animalMap);
}

void _readAnimalMapHolderSessionField0(
    ReadContext context, AnimalMapHolder value, Object? rawValue) {
  value.animalMap = _readAnimalMapHolderAnimalMap(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.animalMap);
}

final GeneratedStructRegistration<AnimalMapHolder>
    _animalMapHolderForyRegistration =
    GeneratedStructRegistration<AnimalMapHolder>(
  sessionWritersBySlot: <_AnimalMapHolderSessionWriter>[
    _writeAnimalMapHolderSessionField0,
  ],
  compatibleFactory: AnimalMapHolder.new,
  compatibleReadersBySlot: <_AnimalMapHolderSessionReader>[
    _readAnimalMapHolderSessionField0,
  ],
  type: AnimalMapHolder,
  serializerFactory: _AnimalMapHolderForySerializer.new,
  evolving: true,
  fields: _animalMapHolderForyFieldMetadata,
);

final class _AnimalMapHolderForySerializer extends Serializer<AnimalMapHolder> {
  List<GeneratedStructField>? _generatedFields;

  _AnimalMapHolderForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _animalMapHolderForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _animalMapHolderForyRegistration,
    );
  }

  @override
  void write(WriteContext context, AnimalMapHolder value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.animalMap);
      return;
    }
    final writers = _animalMapHolderForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  AnimalMapHolder read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = AnimalMapHolder();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.animalMap = readGeneratedDirectMapValue<String, Animal>(
          context,
          fields[0],
          _readAnimalMapHolderAnimalMapKey,
          _readAnimalMapHolderAnimalMapValue);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawAnimalMapHolder0 = session.valueForSlot(0);
      value.animalMap = _readAnimalMapHolderAnimalMap(
          rawAnimalMapHolder0 is DeferredReadRef
              ? context.getReadRef(rawAnimalMapHolder0.id)
              : rawAnimalMapHolder0,
          value.animalMap);
    }
    return value;
  }
}

String _readAnimalMapHolderAnimalMapKey(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable animalMap map key.'))
      : value as String;
}

Animal _readAnimalMapHolderAnimalMapValue(Object? value) {
  return value == null
      ? (throw StateError(
          'Received null for non-nullable animalMap map value.'))
      : value as Animal;
}

Map<String, Animal> _readAnimalMapHolderAnimalMap(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, Animal>
          : (throw StateError(
              'Received null for non-nullable field animalMap.')))
      : Map.castFrom<dynamic, dynamic, String, Animal>(value as Map);
}

const List<GeneratedFieldMetadata> _emptyStructForyFieldMetadata =
    <GeneratedFieldMetadata>[];

typedef _EmptyStructSessionWriter = GeneratedStructFieldWriter<EmptyStruct>;
typedef _EmptyStructSessionReader = GeneratedStructFieldReader<EmptyStruct>;

final GeneratedStructRegistration<EmptyStruct> _emptyStructForyRegistration =
    GeneratedStructRegistration<EmptyStruct>(
  sessionWritersBySlot: <_EmptyStructSessionWriter>[],
  compatibleFactory: EmptyStruct.new,
  compatibleReadersBySlot: <_EmptyStructSessionReader>[],
  type: EmptyStruct,
  serializerFactory: _EmptyStructForySerializer.new,
  evolving: true,
  fields: _emptyStructForyFieldMetadata,
);

final class _EmptyStructForySerializer extends Serializer<EmptyStruct> {
  List<GeneratedStructField>? _generatedFields;

  _EmptyStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _emptyStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _emptyStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EmptyStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      return;
    }
    final writers = _emptyStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EmptyStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = EmptyStruct();
    context.reference(value);
    if (session == null) {
      return value;
    }
    return value;
  }
}

const List<GeneratedFieldMetadata> _oneStringFieldStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _OneStringFieldStructSessionWriter
    = GeneratedStructFieldWriter<OneStringFieldStruct>;
typedef _OneStringFieldStructSessionReader
    = GeneratedStructFieldReader<OneStringFieldStruct>;

void _writeOneStringFieldStructSessionField0(WriteContext context,
    GeneratedStructField field, OneStringFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _readOneStringFieldStructSessionField0(
    ReadContext context, OneStringFieldStruct value, Object? rawValue) {
  value.f1 = _readOneStringFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<OneStringFieldStruct>
    _oneStringFieldStructForyRegistration =
    GeneratedStructRegistration<OneStringFieldStruct>(
  sessionWritersBySlot: <_OneStringFieldStructSessionWriter>[
    _writeOneStringFieldStructSessionField0,
  ],
  compatibleFactory: OneStringFieldStruct.new,
  compatibleReadersBySlot: <_OneStringFieldStructSessionReader>[
    _readOneStringFieldStructSessionField0,
  ],
  type: OneStringFieldStruct,
  serializerFactory: _OneStringFieldStructForySerializer.new,
  evolving: true,
  fields: _oneStringFieldStructForyFieldMetadata,
);

final class _OneStringFieldStructForySerializer
    extends Serializer<OneStringFieldStruct> {
  List<GeneratedStructField>? _generatedFields;

  _OneStringFieldStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _oneStringFieldStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _oneStringFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, OneStringFieldStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.f1);
      return;
    }
    final writers = _oneStringFieldStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  OneStringFieldStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = OneStringFieldStruct();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.f1 = _readOneStringFieldStructF1(
          readGeneratedStructRuntimeValue(context, fields[0], value.f1),
          value.f1);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawOneStringFieldStruct0 = session.valueForSlot(0);
      value.f1 = _readOneStringFieldStructF1(
          rawOneStringFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawOneStringFieldStruct0.id)
              : rawOneStringFieldStruct0,
          value.f1);
    }
    return value;
  }
}

String? _readOneStringFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

const List<GeneratedFieldMetadata> _twoStringFieldStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
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
    name: 'f2',
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _TwoStringFieldStructSessionWriter
    = GeneratedStructFieldWriter<TwoStringFieldStruct>;
typedef _TwoStringFieldStructSessionReader
    = GeneratedStructFieldReader<TwoStringFieldStruct>;

void _writeTwoStringFieldStructSessionField0(WriteContext context,
    GeneratedStructField field, TwoStringFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeTwoStringFieldStructSessionField1(WriteContext context,
    GeneratedStructField field, TwoStringFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _readTwoStringFieldStructSessionField0(
    ReadContext context, TwoStringFieldStruct value, Object? rawValue) {
  value.f1 = _readTwoStringFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoStringFieldStructSessionField1(
    ReadContext context, TwoStringFieldStruct value, Object? rawValue) {
  value.f2 = _readTwoStringFieldStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoStringFieldStruct>
    _twoStringFieldStructForyRegistration =
    GeneratedStructRegistration<TwoStringFieldStruct>(
  sessionWritersBySlot: <_TwoStringFieldStructSessionWriter>[
    _writeTwoStringFieldStructSessionField0,
    _writeTwoStringFieldStructSessionField1,
  ],
  compatibleFactory: TwoStringFieldStruct.new,
  compatibleReadersBySlot: <_TwoStringFieldStructSessionReader>[
    _readTwoStringFieldStructSessionField0,
    _readTwoStringFieldStructSessionField1,
  ],
  type: TwoStringFieldStruct,
  serializerFactory: _TwoStringFieldStructForySerializer.new,
  evolving: true,
  fields: _twoStringFieldStructForyFieldMetadata,
);

final class _TwoStringFieldStructForySerializer
    extends Serializer<TwoStringFieldStruct> {
  List<GeneratedStructField>? _generatedFields;

  _TwoStringFieldStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoStringFieldStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoStringFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoStringFieldStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.f1);
      context.writeString(value.f2);
      return;
    }
    final writers = _twoStringFieldStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoStringFieldStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = TwoStringFieldStruct();
    context.reference(value);
    if (session == null) {
      value.f1 = context.readString();
      value.f2 = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawTwoStringFieldStruct0 = session.valueForSlot(0);
      value.f1 = _readTwoStringFieldStructF1(
          rawTwoStringFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawTwoStringFieldStruct0.id)
              : rawTwoStringFieldStruct0,
          value.f1);
    }
    if (session.containsSlot(1)) {
      final rawTwoStringFieldStruct1 = session.valueForSlot(1);
      value.f2 = _readTwoStringFieldStructF2(
          rawTwoStringFieldStruct1 is DeferredReadRef
              ? context.getReadRef(rawTwoStringFieldStruct1.id)
              : rawTwoStringFieldStruct1,
          value.f2);
    }
    return value;
  }
}

String _readTwoStringFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as String;
}

String _readTwoStringFieldStructF2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f2.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _oneEnumFieldStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _OneEnumFieldStructSessionWriter
    = GeneratedStructFieldWriter<OneEnumFieldStruct>;
typedef _OneEnumFieldStructSessionReader
    = GeneratedStructFieldReader<OneEnumFieldStruct>;

void _writeOneEnumFieldStructSessionField0(WriteContext context,
    GeneratedStructField field, OneEnumFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _readOneEnumFieldStructSessionField0(
    ReadContext context, OneEnumFieldStruct value, Object? rawValue) {
  value.f1 = _readOneEnumFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<OneEnumFieldStruct>
    _oneEnumFieldStructForyRegistration =
    GeneratedStructRegistration<OneEnumFieldStruct>(
  sessionWritersBySlot: <_OneEnumFieldStructSessionWriter>[
    _writeOneEnumFieldStructSessionField0,
  ],
  compatibleFactory: OneEnumFieldStruct.new,
  compatibleReadersBySlot: <_OneEnumFieldStructSessionReader>[
    _readOneEnumFieldStructSessionField0,
  ],
  type: OneEnumFieldStruct,
  serializerFactory: _OneEnumFieldStructForySerializer.new,
  evolving: true,
  fields: _oneEnumFieldStructForyFieldMetadata,
);

final class _OneEnumFieldStructForySerializer
    extends Serializer<OneEnumFieldStruct> {
  List<GeneratedStructField>? _generatedFields;

  _OneEnumFieldStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _oneEnumFieldStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _oneEnumFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, OneEnumFieldStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.finish();
      return;
    }
    final writers = _oneEnumFieldStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  OneEnumFieldStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = OneEnumFieldStruct();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawOneEnumFieldStruct0 = session.valueForSlot(0);
      value.f1 = _readOneEnumFieldStructF1(
          rawOneEnumFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawOneEnumFieldStruct0.id)
              : rawOneEnumFieldStruct0,
          value.f1);
    }
    return value;
  }
}

TestEnum _readOneEnumFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as TestEnum;
}

const List<GeneratedFieldMetadata> _twoEnumFieldStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'f1',
    identifier: 'f1',
    id: null,
    shape: GeneratedTypeShape(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'f2',
    identifier: 'f2',
    id: null,
    shape: GeneratedTypeShape(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _TwoEnumFieldStructSessionWriter
    = GeneratedStructFieldWriter<TwoEnumFieldStruct>;
typedef _TwoEnumFieldStructSessionReader
    = GeneratedStructFieldReader<TwoEnumFieldStruct>;

void _writeTwoEnumFieldStructSessionField0(WriteContext context,
    GeneratedStructField field, TwoEnumFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f1);
}

void _writeTwoEnumFieldStructSessionField1(WriteContext context,
    GeneratedStructField field, TwoEnumFieldStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.f2);
}

void _readTwoEnumFieldStructSessionField0(
    ReadContext context, TwoEnumFieldStruct value, Object? rawValue) {
  value.f1 = _readTwoEnumFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoEnumFieldStructSessionField1(
    ReadContext context, TwoEnumFieldStruct value, Object? rawValue) {
  value.f2 = _readTwoEnumFieldStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoEnumFieldStruct>
    _twoEnumFieldStructForyRegistration =
    GeneratedStructRegistration<TwoEnumFieldStruct>(
  sessionWritersBySlot: <_TwoEnumFieldStructSessionWriter>[
    _writeTwoEnumFieldStructSessionField0,
    _writeTwoEnumFieldStructSessionField1,
  ],
  compatibleFactory: TwoEnumFieldStruct.new,
  compatibleReadersBySlot: <_TwoEnumFieldStructSessionReader>[
    _readTwoEnumFieldStructSessionField0,
    _readTwoEnumFieldStructSessionField1,
  ],
  type: TwoEnumFieldStruct,
  serializerFactory: _TwoEnumFieldStructForySerializer.new,
  evolving: true,
  fields: _twoEnumFieldStructForyFieldMetadata,
);

final class _TwoEnumFieldStructForySerializer
    extends Serializer<TwoEnumFieldStruct> {
  List<GeneratedStructField>? _generatedFields;

  _TwoEnumFieldStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoEnumFieldStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _twoEnumFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoEnumFieldStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.writeVarUint32(value.f2.index);
      cursor0.finish();
      return;
    }
    final writers = _twoEnumFieldStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoEnumFieldStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = TwoEnumFieldStruct();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      value.f2 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawTwoEnumFieldStruct0 = session.valueForSlot(0);
      value.f1 = _readTwoEnumFieldStructF1(
          rawTwoEnumFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStruct0.id)
              : rawTwoEnumFieldStruct0,
          value.f1);
    }
    if (session.containsSlot(1)) {
      final rawTwoEnumFieldStruct1 = session.valueForSlot(1);
      value.f2 = _readTwoEnumFieldStructF2(
          rawTwoEnumFieldStruct1 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStruct1.id)
              : rawTwoEnumFieldStruct1,
          value.f2);
    }
    return value;
  }
}

TestEnum _readTwoEnumFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as TestEnum;
}

TestEnum _readTwoEnumFieldStructF2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f2.')))
      : value as TestEnum;
}

const List<GeneratedFieldMetadata>
    _nullableComprehensiveSchemaConsistentForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'doubleField',
    identifier: 'double_field',
    id: null,
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
    name: 'floatField',
    identifier: 'float_field',
    id: null,
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
    name: 'shortField',
    identifier: 'short_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int16,
      typeId: 3,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'byteField',
    identifier: 'byte_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int8,
      typeId: 2,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'boolField',
    identifier: 'bool_field',
    id: null,
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
    name: 'longField',
    identifier: 'long_field',
    id: null,
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
    name: 'intField',
    identifier: 'int_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableDouble',
    identifier: 'nullable_double',
    id: null,
    shape: GeneratedTypeShape(
      type: double,
      typeId: 20,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableFloat',
    identifier: 'nullable_float',
    id: null,
    shape: GeneratedTypeShape(
      type: Float32,
      typeId: 19,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableBool',
    identifier: 'nullable_bool',
    id: null,
    shape: GeneratedTypeShape(
      type: bool,
      typeId: 1,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableLong',
    identifier: 'nullable_long',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 7,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableInt',
    identifier: 'nullable_int',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableString',
    identifier: 'nullable_string',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'stringField',
    identifier: 'string_field',
    id: null,
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
    name: 'listField',
    identifier: 'list_field',
    id: null,
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
    name: 'nullableList',
    identifier: 'nullable_list',
    id: null,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: true,
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
    name: 'nullableSet',
    identifier: 'nullable_set',
    id: null,
    shape: GeneratedTypeShape(
      type: Set,
      typeId: 23,
      nullable: true,
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
    name: 'setField',
    identifier: 'set_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Set,
      typeId: 23,
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
    name: 'mapField',
    identifier: 'map_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
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
    name: 'nullableMap',
    identifier: 'nullable_map',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
      nullable: true,
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
        ),
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
];

typedef _NullableComprehensiveSchemaConsistentSessionWriter
    = GeneratedStructFieldWriter<NullableComprehensiveSchemaConsistent>;
typedef _NullableComprehensiveSchemaConsistentSessionReader
    = GeneratedStructFieldReader<NullableComprehensiveSchemaConsistent>;

void _writeNullableComprehensiveSchemaConsistentSessionField0(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.doubleField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField1(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.floatField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField2(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.shortField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField3(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.byteField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField4(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.boolField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField5(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.longField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField6(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.intField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField7(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableDouble);
}

void _writeNullableComprehensiveSchemaConsistentSessionField8(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableFloat);
}

void _writeNullableComprehensiveSchemaConsistentSessionField9(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableBool);
}

void _writeNullableComprehensiveSchemaConsistentSessionField10(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableLong);
}

void _writeNullableComprehensiveSchemaConsistentSessionField11(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableInt);
}

void _writeNullableComprehensiveSchemaConsistentSessionField12(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableString);
}

void _writeNullableComprehensiveSchemaConsistentSessionField13(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.stringField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField14(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.listField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField15(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableList);
}

void _writeNullableComprehensiveSchemaConsistentSessionField16(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableSet);
}

void _writeNullableComprehensiveSchemaConsistentSessionField17(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.setField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField18(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.mapField);
}

void _writeNullableComprehensiveSchemaConsistentSessionField19(
    WriteContext context,
    GeneratedStructField field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableMap);
}

void _readNullableComprehensiveSchemaConsistentSessionField0(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.doubleField);
}

void _readNullableComprehensiveSchemaConsistentSessionField1(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.floatField);
}

void _readNullableComprehensiveSchemaConsistentSessionField2(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.shortField);
}

void _readNullableComprehensiveSchemaConsistentSessionField3(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.byteField);
}

void _readNullableComprehensiveSchemaConsistentSessionField4(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boolField);
}

void _readNullableComprehensiveSchemaConsistentSessionField5(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.longField = _readNullableComprehensiveSchemaConsistentLongField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.longField);
}

void _readNullableComprehensiveSchemaConsistentSessionField6(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.intField = _readNullableComprehensiveSchemaConsistentIntField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.intField);
}

void _readNullableComprehensiveSchemaConsistentSessionField7(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableDouble =
      _readNullableComprehensiveSchemaConsistentNullableDouble(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.nullableDouble);
}

void _readNullableComprehensiveSchemaConsistentSessionField8(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableFloat = _readNullableComprehensiveSchemaConsistentNullableFloat(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableFloat);
}

void _readNullableComprehensiveSchemaConsistentSessionField9(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableBool = _readNullableComprehensiveSchemaConsistentNullableBool(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableBool);
}

void _readNullableComprehensiveSchemaConsistentSessionField10(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableLong = _readNullableComprehensiveSchemaConsistentNullableLong(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableLong);
}

void _readNullableComprehensiveSchemaConsistentSessionField11(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableInt);
}

void _readNullableComprehensiveSchemaConsistentSessionField12(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableString =
      _readNullableComprehensiveSchemaConsistentNullableString(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.nullableString);
}

void _readNullableComprehensiveSchemaConsistentSessionField13(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.stringField);
}

void _readNullableComprehensiveSchemaConsistentSessionField14(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.listField = _readNullableComprehensiveSchemaConsistentListField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.listField);
}

void _readNullableComprehensiveSchemaConsistentSessionField15(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableList = _readNullableComprehensiveSchemaConsistentNullableList(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableList);
}

void _readNullableComprehensiveSchemaConsistentSessionField16(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableSet);
}

void _readNullableComprehensiveSchemaConsistentSessionField17(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.setField = _readNullableComprehensiveSchemaConsistentSetField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.setField);
}

void _readNullableComprehensiveSchemaConsistentSessionField18(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.mapField);
}

void _readNullableComprehensiveSchemaConsistentSessionField19(
    ReadContext context,
    NullableComprehensiveSchemaConsistent value,
    Object? rawValue) {
  value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableMap);
}

final GeneratedStructRegistration<NullableComprehensiveSchemaConsistent>
    _nullableComprehensiveSchemaConsistentForyRegistration =
    GeneratedStructRegistration<NullableComprehensiveSchemaConsistent>(
  sessionWritersBySlot: <_NullableComprehensiveSchemaConsistentSessionWriter>[
    _writeNullableComprehensiveSchemaConsistentSessionField0,
    _writeNullableComprehensiveSchemaConsistentSessionField1,
    _writeNullableComprehensiveSchemaConsistentSessionField2,
    _writeNullableComprehensiveSchemaConsistentSessionField3,
    _writeNullableComprehensiveSchemaConsistentSessionField4,
    _writeNullableComprehensiveSchemaConsistentSessionField5,
    _writeNullableComprehensiveSchemaConsistentSessionField6,
    _writeNullableComprehensiveSchemaConsistentSessionField7,
    _writeNullableComprehensiveSchemaConsistentSessionField8,
    _writeNullableComprehensiveSchemaConsistentSessionField9,
    _writeNullableComprehensiveSchemaConsistentSessionField10,
    _writeNullableComprehensiveSchemaConsistentSessionField11,
    _writeNullableComprehensiveSchemaConsistentSessionField12,
    _writeNullableComprehensiveSchemaConsistentSessionField13,
    _writeNullableComprehensiveSchemaConsistentSessionField14,
    _writeNullableComprehensiveSchemaConsistentSessionField15,
    _writeNullableComprehensiveSchemaConsistentSessionField16,
    _writeNullableComprehensiveSchemaConsistentSessionField17,
    _writeNullableComprehensiveSchemaConsistentSessionField18,
    _writeNullableComprehensiveSchemaConsistentSessionField19,
  ],
  compatibleFactory: NullableComprehensiveSchemaConsistent.new,
  compatibleReadersBySlot: <_NullableComprehensiveSchemaConsistentSessionReader>[
    _readNullableComprehensiveSchemaConsistentSessionField0,
    _readNullableComprehensiveSchemaConsistentSessionField1,
    _readNullableComprehensiveSchemaConsistentSessionField2,
    _readNullableComprehensiveSchemaConsistentSessionField3,
    _readNullableComprehensiveSchemaConsistentSessionField4,
    _readNullableComprehensiveSchemaConsistentSessionField5,
    _readNullableComprehensiveSchemaConsistentSessionField6,
    _readNullableComprehensiveSchemaConsistentSessionField7,
    _readNullableComprehensiveSchemaConsistentSessionField8,
    _readNullableComprehensiveSchemaConsistentSessionField9,
    _readNullableComprehensiveSchemaConsistentSessionField10,
    _readNullableComprehensiveSchemaConsistentSessionField11,
    _readNullableComprehensiveSchemaConsistentSessionField12,
    _readNullableComprehensiveSchemaConsistentSessionField13,
    _readNullableComprehensiveSchemaConsistentSessionField14,
    _readNullableComprehensiveSchemaConsistentSessionField15,
    _readNullableComprehensiveSchemaConsistentSessionField16,
    _readNullableComprehensiveSchemaConsistentSessionField17,
    _readNullableComprehensiveSchemaConsistentSessionField18,
    _readNullableComprehensiveSchemaConsistentSessionField19,
  ],
  type: NullableComprehensiveSchemaConsistent,
  serializerFactory: _NullableComprehensiveSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _nullableComprehensiveSchemaConsistentForyFieldMetadata,
);

final class _NullableComprehensiveSchemaConsistentForySerializer
    extends Serializer<NullableComprehensiveSchemaConsistent> {
  List<GeneratedStructField>? _generatedFields;

  _NullableComprehensiveSchemaConsistentForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _nullableComprehensiveSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _nullableComprehensiveSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(
      WriteContext context, NullableComprehensiveSchemaConsistent value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 31);
      cursor0.writeFloat64(value.doubleField);
      cursor0.writeFloat32(value.floatField.value);
      cursor0.writeInt16(value.shortField.value);
      cursor0.writeByte(value.byteField.value);
      cursor0.writeBool(value.boolField);
      cursor0.writeVarInt64(value.longField);
      cursor0.writeVarInt32(value.intField.value);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(
          context, fields[7], value.nullableDouble);
      writeGeneratedStructRuntimeValue(context, fields[8], value.nullableFloat);
      writeGeneratedStructRuntimeValue(context, fields[9], value.nullableBool);
      writeGeneratedStructRuntimeValue(context, fields[10], value.nullableLong);
      writeGeneratedStructRuntimeValue(context, fields[11], value.nullableInt);
      writeGeneratedStructRuntimeValue(
          context, fields[12], value.nullableString);
      context.writeString(value.stringField);
      writeGeneratedStructRuntimeValue(context, fields[14], value.listField);
      writeGeneratedStructRuntimeValue(context, fields[15], value.nullableList);
      writeGeneratedStructRuntimeValue(context, fields[16], value.nullableSet);
      writeGeneratedStructRuntimeValue(context, fields[17], value.setField);
      writeGeneratedStructRuntimeValue(context, fields[18], value.mapField);
      writeGeneratedStructRuntimeValue(context, fields[19], value.nullableMap);
      return;
    }
    final writers = _nullableComprehensiveSchemaConsistentForyRegistration
        .sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  NullableComprehensiveSchemaConsistent read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = NullableComprehensiveSchemaConsistent();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.doubleField = cursor0.readFloat64();
      value.floatField = Float32(cursor0.readFloat32());
      value.shortField = Int16(cursor0.readInt16());
      value.byteField = Int8(cursor0.readByte());
      value.boolField = cursor0.readBool();
      value.longField = cursor0.readVarInt64();
      value.intField = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.nullableDouble =
          _readNullableComprehensiveSchemaConsistentNullableDouble(
              readGeneratedStructRuntimeValue(
                  context, fields[7], value.nullableDouble),
              value.nullableDouble);
      value.nullableFloat =
          _readNullableComprehensiveSchemaConsistentNullableFloat(
              readGeneratedStructRuntimeValue(
                  context, fields[8], value.nullableFloat),
              value.nullableFloat);
      value.nullableBool =
          _readNullableComprehensiveSchemaConsistentNullableBool(
              readGeneratedStructRuntimeValue(
                  context, fields[9], value.nullableBool),
              value.nullableBool);
      value.nullableLong =
          _readNullableComprehensiveSchemaConsistentNullableLong(
              readGeneratedStructRuntimeValue(
                  context, fields[10], value.nullableLong),
              value.nullableLong);
      value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
          readGeneratedStructRuntimeValue(
              context, fields[11], value.nullableInt),
          value.nullableInt);
      value.nullableString =
          _readNullableComprehensiveSchemaConsistentNullableString(
              readGeneratedStructRuntimeValue(
                  context, fields[12], value.nullableString),
              value.nullableString);
      value.stringField = context.readString();
      value.listField = readGeneratedDirectListValue<String>(
          context,
          fields[14],
          _readNullableComprehensiveSchemaConsistentListFieldElement);
      value.nullableList =
          _readNullableComprehensiveSchemaConsistentNullableList(
              readGeneratedStructRuntimeValue(
                  context, fields[15], value.nullableList),
              value.nullableList);
      value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
          readGeneratedStructRuntimeValue(
              context, fields[16], value.nullableSet),
          value.nullableSet);
      value.setField = readGeneratedDirectSetValue<String>(context, fields[17],
          _readNullableComprehensiveSchemaConsistentSetFieldElement);
      value.mapField = readGeneratedDirectMapValue<String, String>(
          context,
          fields[18],
          _readNullableComprehensiveSchemaConsistentMapFieldKey,
          _readNullableComprehensiveSchemaConsistentMapFieldValue);
      value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
          readGeneratedStructRuntimeValue(
              context, fields[19], value.nullableMap),
          value.nullableMap);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawNullableComprehensiveSchemaConsistent0 = session.valueForSlot(0);
      value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
          rawNullableComprehensiveSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent0.id)
              : rawNullableComprehensiveSchemaConsistent0,
          value.doubleField);
    }
    if (session.containsSlot(1)) {
      final rawNullableComprehensiveSchemaConsistent1 = session.valueForSlot(1);
      value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
          rawNullableComprehensiveSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent1.id)
              : rawNullableComprehensiveSchemaConsistent1,
          value.floatField);
    }
    if (session.containsSlot(2)) {
      final rawNullableComprehensiveSchemaConsistent2 = session.valueForSlot(2);
      value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
          rawNullableComprehensiveSchemaConsistent2 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent2.id)
              : rawNullableComprehensiveSchemaConsistent2,
          value.shortField);
    }
    if (session.containsSlot(3)) {
      final rawNullableComprehensiveSchemaConsistent3 = session.valueForSlot(3);
      value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
          rawNullableComprehensiveSchemaConsistent3 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent3.id)
              : rawNullableComprehensiveSchemaConsistent3,
          value.byteField);
    }
    if (session.containsSlot(4)) {
      final rawNullableComprehensiveSchemaConsistent4 = session.valueForSlot(4);
      value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
          rawNullableComprehensiveSchemaConsistent4 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent4.id)
              : rawNullableComprehensiveSchemaConsistent4,
          value.boolField);
    }
    if (session.containsSlot(5)) {
      final rawNullableComprehensiveSchemaConsistent5 = session.valueForSlot(5);
      value.longField = _readNullableComprehensiveSchemaConsistentLongField(
          rawNullableComprehensiveSchemaConsistent5 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent5.id)
              : rawNullableComprehensiveSchemaConsistent5,
          value.longField);
    }
    if (session.containsSlot(6)) {
      final rawNullableComprehensiveSchemaConsistent6 = session.valueForSlot(6);
      value.intField = _readNullableComprehensiveSchemaConsistentIntField(
          rawNullableComprehensiveSchemaConsistent6 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent6.id)
              : rawNullableComprehensiveSchemaConsistent6,
          value.intField);
    }
    if (session.containsSlot(7)) {
      final rawNullableComprehensiveSchemaConsistent7 = session.valueForSlot(7);
      value.nullableDouble =
          _readNullableComprehensiveSchemaConsistentNullableDouble(
              rawNullableComprehensiveSchemaConsistent7 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent7.id)
                  : rawNullableComprehensiveSchemaConsistent7,
              value.nullableDouble);
    }
    if (session.containsSlot(8)) {
      final rawNullableComprehensiveSchemaConsistent8 = session.valueForSlot(8);
      value.nullableFloat =
          _readNullableComprehensiveSchemaConsistentNullableFloat(
              rawNullableComprehensiveSchemaConsistent8 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent8.id)
                  : rawNullableComprehensiveSchemaConsistent8,
              value.nullableFloat);
    }
    if (session.containsSlot(9)) {
      final rawNullableComprehensiveSchemaConsistent9 = session.valueForSlot(9);
      value.nullableBool =
          _readNullableComprehensiveSchemaConsistentNullableBool(
              rawNullableComprehensiveSchemaConsistent9 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent9.id)
                  : rawNullableComprehensiveSchemaConsistent9,
              value.nullableBool);
    }
    if (session.containsSlot(10)) {
      final rawNullableComprehensiveSchemaConsistent10 =
          session.valueForSlot(10);
      value.nullableLong =
          _readNullableComprehensiveSchemaConsistentNullableLong(
              rawNullableComprehensiveSchemaConsistent10 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent10.id)
                  : rawNullableComprehensiveSchemaConsistent10,
              value.nullableLong);
    }
    if (session.containsSlot(11)) {
      final rawNullableComprehensiveSchemaConsistent11 =
          session.valueForSlot(11);
      value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
          rawNullableComprehensiveSchemaConsistent11 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent11.id)
              : rawNullableComprehensiveSchemaConsistent11,
          value.nullableInt);
    }
    if (session.containsSlot(12)) {
      final rawNullableComprehensiveSchemaConsistent12 =
          session.valueForSlot(12);
      value.nullableString =
          _readNullableComprehensiveSchemaConsistentNullableString(
              rawNullableComprehensiveSchemaConsistent12 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent12.id)
                  : rawNullableComprehensiveSchemaConsistent12,
              value.nullableString);
    }
    if (session.containsSlot(13)) {
      final rawNullableComprehensiveSchemaConsistent13 =
          session.valueForSlot(13);
      value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
          rawNullableComprehensiveSchemaConsistent13 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent13.id)
              : rawNullableComprehensiveSchemaConsistent13,
          value.stringField);
    }
    if (session.containsSlot(14)) {
      final rawNullableComprehensiveSchemaConsistent14 =
          session.valueForSlot(14);
      value.listField = _readNullableComprehensiveSchemaConsistentListField(
          rawNullableComprehensiveSchemaConsistent14 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent14.id)
              : rawNullableComprehensiveSchemaConsistent14,
          value.listField);
    }
    if (session.containsSlot(15)) {
      final rawNullableComprehensiveSchemaConsistent15 =
          session.valueForSlot(15);
      value.nullableList =
          _readNullableComprehensiveSchemaConsistentNullableList(
              rawNullableComprehensiveSchemaConsistent15 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent15.id)
                  : rawNullableComprehensiveSchemaConsistent15,
              value.nullableList);
    }
    if (session.containsSlot(16)) {
      final rawNullableComprehensiveSchemaConsistent16 =
          session.valueForSlot(16);
      value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
          rawNullableComprehensiveSchemaConsistent16 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent16.id)
              : rawNullableComprehensiveSchemaConsistent16,
          value.nullableSet);
    }
    if (session.containsSlot(17)) {
      final rawNullableComprehensiveSchemaConsistent17 =
          session.valueForSlot(17);
      value.setField = _readNullableComprehensiveSchemaConsistentSetField(
          rawNullableComprehensiveSchemaConsistent17 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent17.id)
              : rawNullableComprehensiveSchemaConsistent17,
          value.setField);
    }
    if (session.containsSlot(18)) {
      final rawNullableComprehensiveSchemaConsistent18 =
          session.valueForSlot(18);
      value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
          rawNullableComprehensiveSchemaConsistent18 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent18.id)
              : rawNullableComprehensiveSchemaConsistent18,
          value.mapField);
    }
    if (session.containsSlot(19)) {
      final rawNullableComprehensiveSchemaConsistent19 =
          session.valueForSlot(19);
      value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
          rawNullableComprehensiveSchemaConsistent19 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent19.id)
              : rawNullableComprehensiveSchemaConsistent19,
          value.nullableMap);
    }
    return value;
  }
}

double _readNullableComprehensiveSchemaConsistentDoubleField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field doubleField.')))
      : value as double;
}

Float32 _readNullableComprehensiveSchemaConsistentFloatField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field floatField.')))
      : value as Float32;
}

Int16 _readNullableComprehensiveSchemaConsistentShortField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int16
          : (throw StateError(
              'Received null for non-nullable field shortField.')))
      : value as Int16;
}

Int8 _readNullableComprehensiveSchemaConsistentByteField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int8
          : (throw StateError(
              'Received null for non-nullable field byteField.')))
      : value as Int8;
}

bool _readNullableComprehensiveSchemaConsistentBoolField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field boolField.')))
      : value as bool;
}

int _readNullableComprehensiveSchemaConsistentLongField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field longField.')))
      : value as int;
}

Int32 _readNullableComprehensiveSchemaConsistentIntField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError(
              'Received null for non-nullable field intField.')))
      : value as Int32;
}

double? _readNullableComprehensiveSchemaConsistentNullableDouble(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as double?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as double;
}

Float32? _readNullableComprehensiveSchemaConsistentNullableFloat(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Float32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as Float32;
}

bool? _readNullableComprehensiveSchemaConsistentNullableBool(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as bool?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as bool;
}

int? _readNullableComprehensiveSchemaConsistentNullableLong(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

Int32? _readNullableComprehensiveSchemaConsistentNullableInt(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Int32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as Int32;
}

String? _readNullableComprehensiveSchemaConsistentNullableString(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

String _readNullableComprehensiveSchemaConsistentStringField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError(
              'Received null for non-nullable field stringField.')))
      : value as String;
}

String _readNullableComprehensiveSchemaConsistentListFieldElement(
    Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable listField item.'))
      : value as String;
}

List<String> _readNullableComprehensiveSchemaConsistentListField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field listField.')))
      : List.castFrom<dynamic, String>(value as List);
}

List<String>? _readNullableComprehensiveSchemaConsistentNullableList(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as List<String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : List.castFrom<dynamic, String>(value as List);
}

Set<String>? _readNullableComprehensiveSchemaConsistentNullableSet(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Set<String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : Set.castFrom<dynamic, String>(value as Set);
}

String _readNullableComprehensiveSchemaConsistentSetFieldElement(
    Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable setField item.'))
      : value as String;
}

Set<String> _readNullableComprehensiveSchemaConsistentSetField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field setField.')))
      : Set.castFrom<dynamic, String>(value as Set);
}

String _readNullableComprehensiveSchemaConsistentMapFieldKey(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable mapField map key.'))
      : value as String;
}

String _readNullableComprehensiveSchemaConsistentMapFieldValue(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable mapField map value.'))
      : value as String;
}

Map<String, String> _readNullableComprehensiveSchemaConsistentMapField(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field mapField.')))
      : Map.castFrom<dynamic, dynamic, String, String>(value as Map);
}

Map<String, String>? _readNullableComprehensiveSchemaConsistentNullableMap(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Map<String, String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : Map.castFrom<dynamic, dynamic, String, String>(value as Map);
}

const List<GeneratedFieldMetadata>
    _nullableComprehensiveCompatibleForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'boxedDouble',
    identifier: 'boxed_double',
    id: null,
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
    name: 'doubleField',
    identifier: 'double_field',
    id: null,
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
    name: 'nullableDouble1',
    identifier: 'nullable_double1',
    id: null,
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
    name: 'boxedFloat',
    identifier: 'boxed_float',
    id: null,
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
    name: 'floatField',
    identifier: 'float_field',
    id: null,
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
    name: 'nullableFloat1',
    identifier: 'nullable_float1',
    id: null,
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
    name: 'shortField',
    identifier: 'short_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int16,
      typeId: 3,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'byteField',
    identifier: 'byte_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int8,
      typeId: 2,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'boolField',
    identifier: 'bool_field',
    id: null,
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
    name: 'boxedBool',
    identifier: 'boxed_bool',
    id: null,
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
    name: 'nullableBool1',
    identifier: 'nullable_bool1',
    id: null,
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
    name: 'boxedLong',
    identifier: 'boxed_long',
    id: null,
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
    name: 'longField',
    identifier: 'long_field',
    id: null,
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
    name: 'nullableLong1',
    identifier: 'nullable_long1',
    id: null,
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
    name: 'boxedInt',
    identifier: 'boxed_int',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'intField',
    identifier: 'int_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableInt1',
    identifier: 'nullable_int1',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'nullableString2',
    identifier: 'nullable_string2',
    id: null,
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
    name: 'stringField',
    identifier: 'string_field',
    id: null,
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
    name: 'listField',
    identifier: 'list_field',
    id: null,
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
    name: 'nullableList2',
    identifier: 'nullable_list2',
    id: null,
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
    name: 'nullableSet2',
    identifier: 'nullable_set2',
    id: null,
    shape: GeneratedTypeShape(
      type: Set,
      typeId: 23,
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
    name: 'setField',
    identifier: 'set_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Set,
      typeId: 23,
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
    name: 'mapField',
    identifier: 'map_field',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
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
    name: 'nullableMap2',
    identifier: 'nullable_map2',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
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
];

typedef _NullableComprehensiveCompatibleSessionWriter
    = GeneratedStructFieldWriter<NullableComprehensiveCompatible>;
typedef _NullableComprehensiveCompatibleSessionReader
    = GeneratedStructFieldReader<NullableComprehensiveCompatible>;

void _writeNullableComprehensiveCompatibleSessionField0(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boxedDouble);
}

void _writeNullableComprehensiveCompatibleSessionField1(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.doubleField);
}

void _writeNullableComprehensiveCompatibleSessionField2(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableDouble1);
}

void _writeNullableComprehensiveCompatibleSessionField3(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boxedFloat);
}

void _writeNullableComprehensiveCompatibleSessionField4(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.floatField);
}

void _writeNullableComprehensiveCompatibleSessionField5(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableFloat1);
}

void _writeNullableComprehensiveCompatibleSessionField6(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.shortField);
}

void _writeNullableComprehensiveCompatibleSessionField7(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.byteField);
}

void _writeNullableComprehensiveCompatibleSessionField8(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boolField);
}

void _writeNullableComprehensiveCompatibleSessionField9(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boxedBool);
}

void _writeNullableComprehensiveCompatibleSessionField10(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableBool1);
}

void _writeNullableComprehensiveCompatibleSessionField11(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boxedLong);
}

void _writeNullableComprehensiveCompatibleSessionField12(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.longField);
}

void _writeNullableComprehensiveCompatibleSessionField13(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableLong1);
}

void _writeNullableComprehensiveCompatibleSessionField14(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.boxedInt);
}

void _writeNullableComprehensiveCompatibleSessionField15(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.intField);
}

void _writeNullableComprehensiveCompatibleSessionField16(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableInt1);
}

void _writeNullableComprehensiveCompatibleSessionField17(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableString2);
}

void _writeNullableComprehensiveCompatibleSessionField18(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.stringField);
}

void _writeNullableComprehensiveCompatibleSessionField19(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.listField);
}

void _writeNullableComprehensiveCompatibleSessionField20(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableList2);
}

void _writeNullableComprehensiveCompatibleSessionField21(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableSet2);
}

void _writeNullableComprehensiveCompatibleSessionField22(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.setField);
}

void _writeNullableComprehensiveCompatibleSessionField23(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.mapField);
}

void _writeNullableComprehensiveCompatibleSessionField24(WriteContext context,
    GeneratedStructField field, NullableComprehensiveCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.nullableMap2);
}

void _readNullableComprehensiveCompatibleSessionField0(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedDouble);
}

void _readNullableComprehensiveCompatibleSessionField1(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.doubleField);
}

void _readNullableComprehensiveCompatibleSessionField2(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableDouble1 = _readNullableComprehensiveCompatibleNullableDouble1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableDouble1);
}

void _readNullableComprehensiveCompatibleSessionField3(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedFloat);
}

void _readNullableComprehensiveCompatibleSessionField4(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.floatField = _readNullableComprehensiveCompatibleFloatField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.floatField);
}

void _readNullableComprehensiveCompatibleSessionField5(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableFloat1);
}

void _readNullableComprehensiveCompatibleSessionField6(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.shortField = _readNullableComprehensiveCompatibleShortField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.shortField);
}

void _readNullableComprehensiveCompatibleSessionField7(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.byteField = _readNullableComprehensiveCompatibleByteField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.byteField);
}

void _readNullableComprehensiveCompatibleSessionField8(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boolField = _readNullableComprehensiveCompatibleBoolField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boolField);
}

void _readNullableComprehensiveCompatibleSessionField9(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedBool);
}

void _readNullableComprehensiveCompatibleSessionField10(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableBool1);
}

void _readNullableComprehensiveCompatibleSessionField11(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedLong);
}

void _readNullableComprehensiveCompatibleSessionField12(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.longField = _readNullableComprehensiveCompatibleLongField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.longField);
}

void _readNullableComprehensiveCompatibleSessionField13(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableLong1);
}

void _readNullableComprehensiveCompatibleSessionField14(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedInt);
}

void _readNullableComprehensiveCompatibleSessionField15(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.intField = _readNullableComprehensiveCompatibleIntField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.intField);
}

void _readNullableComprehensiveCompatibleSessionField16(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableInt1);
}

void _readNullableComprehensiveCompatibleSessionField17(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableString2 = _readNullableComprehensiveCompatibleNullableString2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableString2);
}

void _readNullableComprehensiveCompatibleSessionField18(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.stringField = _readNullableComprehensiveCompatibleStringField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.stringField);
}

void _readNullableComprehensiveCompatibleSessionField19(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.listField = _readNullableComprehensiveCompatibleListField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.listField);
}

void _readNullableComprehensiveCompatibleSessionField20(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableList2);
}

void _readNullableComprehensiveCompatibleSessionField21(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableSet2);
}

void _readNullableComprehensiveCompatibleSessionField22(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.setField = _readNullableComprehensiveCompatibleSetField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.setField);
}

void _readNullableComprehensiveCompatibleSessionField23(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.mapField = _readNullableComprehensiveCompatibleMapField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.mapField);
}

void _readNullableComprehensiveCompatibleSessionField24(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableMap2);
}

final GeneratedStructRegistration<NullableComprehensiveCompatible>
    _nullableComprehensiveCompatibleForyRegistration =
    GeneratedStructRegistration<NullableComprehensiveCompatible>(
  sessionWritersBySlot: <_NullableComprehensiveCompatibleSessionWriter>[
    _writeNullableComprehensiveCompatibleSessionField0,
    _writeNullableComprehensiveCompatibleSessionField1,
    _writeNullableComprehensiveCompatibleSessionField2,
    _writeNullableComprehensiveCompatibleSessionField3,
    _writeNullableComprehensiveCompatibleSessionField4,
    _writeNullableComprehensiveCompatibleSessionField5,
    _writeNullableComprehensiveCompatibleSessionField6,
    _writeNullableComprehensiveCompatibleSessionField7,
    _writeNullableComprehensiveCompatibleSessionField8,
    _writeNullableComprehensiveCompatibleSessionField9,
    _writeNullableComprehensiveCompatibleSessionField10,
    _writeNullableComprehensiveCompatibleSessionField11,
    _writeNullableComprehensiveCompatibleSessionField12,
    _writeNullableComprehensiveCompatibleSessionField13,
    _writeNullableComprehensiveCompatibleSessionField14,
    _writeNullableComprehensiveCompatibleSessionField15,
    _writeNullableComprehensiveCompatibleSessionField16,
    _writeNullableComprehensiveCompatibleSessionField17,
    _writeNullableComprehensiveCompatibleSessionField18,
    _writeNullableComprehensiveCompatibleSessionField19,
    _writeNullableComprehensiveCompatibleSessionField20,
    _writeNullableComprehensiveCompatibleSessionField21,
    _writeNullableComprehensiveCompatibleSessionField22,
    _writeNullableComprehensiveCompatibleSessionField23,
    _writeNullableComprehensiveCompatibleSessionField24,
  ],
  compatibleFactory: NullableComprehensiveCompatible.new,
  compatibleReadersBySlot: <_NullableComprehensiveCompatibleSessionReader>[
    _readNullableComprehensiveCompatibleSessionField0,
    _readNullableComprehensiveCompatibleSessionField1,
    _readNullableComprehensiveCompatibleSessionField2,
    _readNullableComprehensiveCompatibleSessionField3,
    _readNullableComprehensiveCompatibleSessionField4,
    _readNullableComprehensiveCompatibleSessionField5,
    _readNullableComprehensiveCompatibleSessionField6,
    _readNullableComprehensiveCompatibleSessionField7,
    _readNullableComprehensiveCompatibleSessionField8,
    _readNullableComprehensiveCompatibleSessionField9,
    _readNullableComprehensiveCompatibleSessionField10,
    _readNullableComprehensiveCompatibleSessionField11,
    _readNullableComprehensiveCompatibleSessionField12,
    _readNullableComprehensiveCompatibleSessionField13,
    _readNullableComprehensiveCompatibleSessionField14,
    _readNullableComprehensiveCompatibleSessionField15,
    _readNullableComprehensiveCompatibleSessionField16,
    _readNullableComprehensiveCompatibleSessionField17,
    _readNullableComprehensiveCompatibleSessionField18,
    _readNullableComprehensiveCompatibleSessionField19,
    _readNullableComprehensiveCompatibleSessionField20,
    _readNullableComprehensiveCompatibleSessionField21,
    _readNullableComprehensiveCompatibleSessionField22,
    _readNullableComprehensiveCompatibleSessionField23,
    _readNullableComprehensiveCompatibleSessionField24,
  ],
  type: NullableComprehensiveCompatible,
  serializerFactory: _NullableComprehensiveCompatibleForySerializer.new,
  evolving: true,
  fields: _nullableComprehensiveCompatibleForyFieldMetadata,
);

final class _NullableComprehensiveCompatibleForySerializer
    extends Serializer<NullableComprehensiveCompatible> {
  List<GeneratedStructField>? _generatedFields;

  _NullableComprehensiveCompatibleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _nullableComprehensiveCompatibleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _nullableComprehensiveCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, NullableComprehensiveCompatible value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 87);
      cursor0.writeFloat64(value.boxedDouble);
      cursor0.writeFloat64(value.doubleField);
      cursor0.writeFloat64(value.nullableDouble1);
      cursor0.writeFloat32(value.boxedFloat.value);
      cursor0.writeFloat32(value.floatField.value);
      cursor0.writeFloat32(value.nullableFloat1.value);
      cursor0.writeInt16(value.shortField.value);
      cursor0.writeByte(value.byteField.value);
      cursor0.writeBool(value.boolField);
      cursor0.writeBool(value.boxedBool);
      cursor0.writeBool(value.nullableBool1);
      cursor0.writeVarInt64(value.boxedLong);
      cursor0.writeVarInt64(value.longField);
      cursor0.writeVarInt64(value.nullableLong1);
      cursor0.writeVarInt32(value.boxedInt.value);
      cursor0.writeVarInt32(value.intField.value);
      cursor0.writeVarInt32(value.nullableInt1.value);
      cursor0.finish();
      context.writeString(value.nullableString2);
      context.writeString(value.stringField);
      writeGeneratedStructRuntimeValue(context, fields[19], value.listField);
      writeGeneratedStructRuntimeValue(
          context, fields[20], value.nullableList2);
      writeGeneratedStructRuntimeValue(context, fields[21], value.nullableSet2);
      writeGeneratedStructRuntimeValue(context, fields[22], value.setField);
      writeGeneratedStructRuntimeValue(context, fields[23], value.mapField);
      writeGeneratedStructRuntimeValue(context, fields[24], value.nullableMap2);
      return;
    }
    final writers =
        _nullableComprehensiveCompatibleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  NullableComprehensiveCompatible read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = NullableComprehensiveCompatible();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.boxedDouble = cursor0.readFloat64();
      value.doubleField = cursor0.readFloat64();
      value.nullableDouble1 = cursor0.readFloat64();
      value.boxedFloat = Float32(cursor0.readFloat32());
      value.floatField = Float32(cursor0.readFloat32());
      value.nullableFloat1 = Float32(cursor0.readFloat32());
      value.shortField = Int16(cursor0.readInt16());
      value.byteField = Int8(cursor0.readByte());
      value.boolField = cursor0.readBool();
      value.boxedBool = cursor0.readBool();
      value.nullableBool1 = cursor0.readBool();
      value.boxedLong = cursor0.readVarInt64();
      value.longField = cursor0.readVarInt64();
      value.nullableLong1 = cursor0.readVarInt64();
      value.boxedInt = Int32(cursor0.readVarInt32());
      value.intField = Int32(cursor0.readVarInt32());
      value.nullableInt1 = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.nullableString2 = context.readString();
      value.stringField = context.readString();
      value.listField = readGeneratedDirectListValue<String>(context,
          fields[19], _readNullableComprehensiveCompatibleListFieldElement);
      value.nullableList2 = readGeneratedDirectListValue<String>(context,
          fields[20], _readNullableComprehensiveCompatibleNullableList2Element);
      value.nullableSet2 = readGeneratedDirectSetValue<String>(context,
          fields[21], _readNullableComprehensiveCompatibleNullableSet2Element);
      value.setField = readGeneratedDirectSetValue<String>(context, fields[22],
          _readNullableComprehensiveCompatibleSetFieldElement);
      value.mapField = readGeneratedDirectMapValue<String, String>(
          context,
          fields[23],
          _readNullableComprehensiveCompatibleMapFieldKey,
          _readNullableComprehensiveCompatibleMapFieldValue);
      value.nullableMap2 = readGeneratedDirectMapValue<String, String>(
          context,
          fields[24],
          _readNullableComprehensiveCompatibleNullableMap2Key,
          _readNullableComprehensiveCompatibleNullableMap2Value);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawNullableComprehensiveCompatible0 = session.valueForSlot(0);
      value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
          rawNullableComprehensiveCompatible0 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible0.id)
              : rawNullableComprehensiveCompatible0,
          value.boxedDouble);
    }
    if (session.containsSlot(1)) {
      final rawNullableComprehensiveCompatible1 = session.valueForSlot(1);
      value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
          rawNullableComprehensiveCompatible1 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible1.id)
              : rawNullableComprehensiveCompatible1,
          value.doubleField);
    }
    if (session.containsSlot(2)) {
      final rawNullableComprehensiveCompatible2 = session.valueForSlot(2);
      value.nullableDouble1 =
          _readNullableComprehensiveCompatibleNullableDouble1(
              rawNullableComprehensiveCompatible2 is DeferredReadRef
                  ? context.getReadRef(rawNullableComprehensiveCompatible2.id)
                  : rawNullableComprehensiveCompatible2,
              value.nullableDouble1);
    }
    if (session.containsSlot(3)) {
      final rawNullableComprehensiveCompatible3 = session.valueForSlot(3);
      value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
          rawNullableComprehensiveCompatible3 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible3.id)
              : rawNullableComprehensiveCompatible3,
          value.boxedFloat);
    }
    if (session.containsSlot(4)) {
      final rawNullableComprehensiveCompatible4 = session.valueForSlot(4);
      value.floatField = _readNullableComprehensiveCompatibleFloatField(
          rawNullableComprehensiveCompatible4 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible4.id)
              : rawNullableComprehensiveCompatible4,
          value.floatField);
    }
    if (session.containsSlot(5)) {
      final rawNullableComprehensiveCompatible5 = session.valueForSlot(5);
      value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
          rawNullableComprehensiveCompatible5 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible5.id)
              : rawNullableComprehensiveCompatible5,
          value.nullableFloat1);
    }
    if (session.containsSlot(6)) {
      final rawNullableComprehensiveCompatible6 = session.valueForSlot(6);
      value.shortField = _readNullableComprehensiveCompatibleShortField(
          rawNullableComprehensiveCompatible6 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible6.id)
              : rawNullableComprehensiveCompatible6,
          value.shortField);
    }
    if (session.containsSlot(7)) {
      final rawNullableComprehensiveCompatible7 = session.valueForSlot(7);
      value.byteField = _readNullableComprehensiveCompatibleByteField(
          rawNullableComprehensiveCompatible7 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible7.id)
              : rawNullableComprehensiveCompatible7,
          value.byteField);
    }
    if (session.containsSlot(8)) {
      final rawNullableComprehensiveCompatible8 = session.valueForSlot(8);
      value.boolField = _readNullableComprehensiveCompatibleBoolField(
          rawNullableComprehensiveCompatible8 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible8.id)
              : rawNullableComprehensiveCompatible8,
          value.boolField);
    }
    if (session.containsSlot(9)) {
      final rawNullableComprehensiveCompatible9 = session.valueForSlot(9);
      value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
          rawNullableComprehensiveCompatible9 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible9.id)
              : rawNullableComprehensiveCompatible9,
          value.boxedBool);
    }
    if (session.containsSlot(10)) {
      final rawNullableComprehensiveCompatible10 = session.valueForSlot(10);
      value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
          rawNullableComprehensiveCompatible10 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible10.id)
              : rawNullableComprehensiveCompatible10,
          value.nullableBool1);
    }
    if (session.containsSlot(11)) {
      final rawNullableComprehensiveCompatible11 = session.valueForSlot(11);
      value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
          rawNullableComprehensiveCompatible11 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible11.id)
              : rawNullableComprehensiveCompatible11,
          value.boxedLong);
    }
    if (session.containsSlot(12)) {
      final rawNullableComprehensiveCompatible12 = session.valueForSlot(12);
      value.longField = _readNullableComprehensiveCompatibleLongField(
          rawNullableComprehensiveCompatible12 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible12.id)
              : rawNullableComprehensiveCompatible12,
          value.longField);
    }
    if (session.containsSlot(13)) {
      final rawNullableComprehensiveCompatible13 = session.valueForSlot(13);
      value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
          rawNullableComprehensiveCompatible13 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible13.id)
              : rawNullableComprehensiveCompatible13,
          value.nullableLong1);
    }
    if (session.containsSlot(14)) {
      final rawNullableComprehensiveCompatible14 = session.valueForSlot(14);
      value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
          rawNullableComprehensiveCompatible14 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible14.id)
              : rawNullableComprehensiveCompatible14,
          value.boxedInt);
    }
    if (session.containsSlot(15)) {
      final rawNullableComprehensiveCompatible15 = session.valueForSlot(15);
      value.intField = _readNullableComprehensiveCompatibleIntField(
          rawNullableComprehensiveCompatible15 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible15.id)
              : rawNullableComprehensiveCompatible15,
          value.intField);
    }
    if (session.containsSlot(16)) {
      final rawNullableComprehensiveCompatible16 = session.valueForSlot(16);
      value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
          rawNullableComprehensiveCompatible16 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible16.id)
              : rawNullableComprehensiveCompatible16,
          value.nullableInt1);
    }
    if (session.containsSlot(17)) {
      final rawNullableComprehensiveCompatible17 = session.valueForSlot(17);
      value.nullableString2 =
          _readNullableComprehensiveCompatibleNullableString2(
              rawNullableComprehensiveCompatible17 is DeferredReadRef
                  ? context.getReadRef(rawNullableComprehensiveCompatible17.id)
                  : rawNullableComprehensiveCompatible17,
              value.nullableString2);
    }
    if (session.containsSlot(18)) {
      final rawNullableComprehensiveCompatible18 = session.valueForSlot(18);
      value.stringField = _readNullableComprehensiveCompatibleStringField(
          rawNullableComprehensiveCompatible18 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible18.id)
              : rawNullableComprehensiveCompatible18,
          value.stringField);
    }
    if (session.containsSlot(19)) {
      final rawNullableComprehensiveCompatible19 = session.valueForSlot(19);
      value.listField = _readNullableComprehensiveCompatibleListField(
          rawNullableComprehensiveCompatible19 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible19.id)
              : rawNullableComprehensiveCompatible19,
          value.listField);
    }
    if (session.containsSlot(20)) {
      final rawNullableComprehensiveCompatible20 = session.valueForSlot(20);
      value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
          rawNullableComprehensiveCompatible20 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible20.id)
              : rawNullableComprehensiveCompatible20,
          value.nullableList2);
    }
    if (session.containsSlot(21)) {
      final rawNullableComprehensiveCompatible21 = session.valueForSlot(21);
      value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
          rawNullableComprehensiveCompatible21 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible21.id)
              : rawNullableComprehensiveCompatible21,
          value.nullableSet2);
    }
    if (session.containsSlot(22)) {
      final rawNullableComprehensiveCompatible22 = session.valueForSlot(22);
      value.setField = _readNullableComprehensiveCompatibleSetField(
          rawNullableComprehensiveCompatible22 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible22.id)
              : rawNullableComprehensiveCompatible22,
          value.setField);
    }
    if (session.containsSlot(23)) {
      final rawNullableComprehensiveCompatible23 = session.valueForSlot(23);
      value.mapField = _readNullableComprehensiveCompatibleMapField(
          rawNullableComprehensiveCompatible23 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible23.id)
              : rawNullableComprehensiveCompatible23,
          value.mapField);
    }
    if (session.containsSlot(24)) {
      final rawNullableComprehensiveCompatible24 = session.valueForSlot(24);
      value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
          rawNullableComprehensiveCompatible24 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible24.id)
              : rawNullableComprehensiveCompatible24,
          value.nullableMap2);
    }
    return value;
  }
}

double _readNullableComprehensiveCompatibleBoxedDouble(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field boxedDouble.')))
      : value as double;
}

double _readNullableComprehensiveCompatibleDoubleField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field doubleField.')))
      : value as double;
}

double _readNullableComprehensiveCompatibleNullableDouble1(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as double
          : (throw StateError(
              'Received null for non-nullable field nullableDouble1.')))
      : value as double;
}

Float32 _readNullableComprehensiveCompatibleBoxedFloat(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field boxedFloat.')))
      : value as Float32;
}

Float32 _readNullableComprehensiveCompatibleFloatField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field floatField.')))
      : value as Float32;
}

Float32 _readNullableComprehensiveCompatibleNullableFloat1(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Float32
          : (throw StateError(
              'Received null for non-nullable field nullableFloat1.')))
      : value as Float32;
}

Int16 _readNullableComprehensiveCompatibleShortField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int16
          : (throw StateError(
              'Received null for non-nullable field shortField.')))
      : value as Int16;
}

Int8 _readNullableComprehensiveCompatibleByteField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int8
          : (throw StateError(
              'Received null for non-nullable field byteField.')))
      : value as Int8;
}

bool _readNullableComprehensiveCompatibleBoolField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field boolField.')))
      : value as bool;
}

bool _readNullableComprehensiveCompatibleBoxedBool(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field boxedBool.')))
      : value as bool;
}

bool _readNullableComprehensiveCompatibleNullableBool1(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as bool
          : (throw StateError(
              'Received null for non-nullable field nullableBool1.')))
      : value as bool;
}

int _readNullableComprehensiveCompatibleBoxedLong(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field boxedLong.')))
      : value as int;
}

int _readNullableComprehensiveCompatibleLongField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field longField.')))
      : value as int;
}

int _readNullableComprehensiveCompatibleNullableLong1(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field nullableLong1.')))
      : value as int;
}

Int32 _readNullableComprehensiveCompatibleBoxedInt(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError(
              'Received null for non-nullable field boxedInt.')))
      : value as Int32;
}

Int32 _readNullableComprehensiveCompatibleIntField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError(
              'Received null for non-nullable field intField.')))
      : value as Int32;
}

Int32 _readNullableComprehensiveCompatibleNullableInt1(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError(
              'Received null for non-nullable field nullableInt1.')))
      : value as Int32;
}

String _readNullableComprehensiveCompatibleNullableString2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError(
              'Received null for non-nullable field nullableString2.')))
      : value as String;
}

String _readNullableComprehensiveCompatibleStringField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError(
              'Received null for non-nullable field stringField.')))
      : value as String;
}

String _readNullableComprehensiveCompatibleListFieldElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable listField item.'))
      : value as String;
}

List<String> _readNullableComprehensiveCompatibleListField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field listField.')))
      : List.castFrom<dynamic, String>(value as List);
}

String _readNullableComprehensiveCompatibleNullableList2Element(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable nullableList2 item.'))
      : value as String;
}

List<String> _readNullableComprehensiveCompatibleNullableList2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field nullableList2.')))
      : List.castFrom<dynamic, String>(value as List);
}

String _readNullableComprehensiveCompatibleNullableSet2Element(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable nullableSet2 item.'))
      : value as String;
}

Set<String> _readNullableComprehensiveCompatibleNullableSet2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field nullableSet2.')))
      : Set.castFrom<dynamic, String>(value as Set);
}

String _readNullableComprehensiveCompatibleSetFieldElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable setField item.'))
      : value as String;
}

Set<String> _readNullableComprehensiveCompatibleSetField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field setField.')))
      : Set.castFrom<dynamic, String>(value as Set);
}

String _readNullableComprehensiveCompatibleMapFieldKey(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable mapField map key.'))
      : value as String;
}

String _readNullableComprehensiveCompatibleMapFieldValue(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable mapField map value.'))
      : value as String;
}

Map<String, String> _readNullableComprehensiveCompatibleMapField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field mapField.')))
      : Map.castFrom<dynamic, dynamic, String, String>(value as Map);
}

String _readNullableComprehensiveCompatibleNullableMap2Key(Object? value) {
  return value == null
      ? (throw StateError(
          'Received null for non-nullable nullableMap2 map key.'))
      : value as String;
}

String _readNullableComprehensiveCompatibleNullableMap2Value(Object? value) {
  return value == null
      ? (throw StateError(
          'Received null for non-nullable nullableMap2 map value.'))
      : value as String;
}

Map<String, String> _readNullableComprehensiveCompatibleNullableMap2(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field nullableMap2.')))
      : Map.castFrom<dynamic, dynamic, String, String>(value as Map);
}

const List<GeneratedFieldMetadata> _refInnerSchemaConsistentForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'id',
    identifier: 'id',
    id: null,
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
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefInnerSchemaConsistentSessionWriter
    = GeneratedStructFieldWriter<RefInnerSchemaConsistent>;
typedef _RefInnerSchemaConsistentSessionReader
    = GeneratedStructFieldReader<RefInnerSchemaConsistent>;

void _writeRefInnerSchemaConsistentSessionField0(WriteContext context,
    GeneratedStructField field, RefInnerSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.id);
}

void _writeRefInnerSchemaConsistentSessionField1(WriteContext context,
    GeneratedStructField field, RefInnerSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _readRefInnerSchemaConsistentSessionField0(
    ReadContext context, RefInnerSchemaConsistent value, Object? rawValue) {
  value.id = _readRefInnerSchemaConsistentId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefInnerSchemaConsistentSessionField1(
    ReadContext context, RefInnerSchemaConsistent value, Object? rawValue) {
  value.name = _readRefInnerSchemaConsistentName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefInnerSchemaConsistent>
    _refInnerSchemaConsistentForyRegistration =
    GeneratedStructRegistration<RefInnerSchemaConsistent>(
  sessionWritersBySlot: <_RefInnerSchemaConsistentSessionWriter>[
    _writeRefInnerSchemaConsistentSessionField0,
    _writeRefInnerSchemaConsistentSessionField1,
  ],
  compatibleFactory: RefInnerSchemaConsistent.new,
  compatibleReadersBySlot: <_RefInnerSchemaConsistentSessionReader>[
    _readRefInnerSchemaConsistentSessionField0,
    _readRefInnerSchemaConsistentSessionField1,
  ],
  type: RefInnerSchemaConsistent,
  serializerFactory: _RefInnerSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _refInnerSchemaConsistentForyFieldMetadata,
);

final class _RefInnerSchemaConsistentForySerializer
    extends Serializer<RefInnerSchemaConsistent> {
  List<GeneratedStructField>? _generatedFields;

  _RefInnerSchemaConsistentForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refInnerSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refInnerSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefInnerSchemaConsistent value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers =
        _refInnerSchemaConsistentForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefInnerSchemaConsistent read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefInnerSchemaConsistent();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefInnerSchemaConsistent0 = session.valueForSlot(0);
      value.id = _readRefInnerSchemaConsistentId(
          rawRefInnerSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawRefInnerSchemaConsistent0.id)
              : rawRefInnerSchemaConsistent0,
          value.id);
    }
    if (session.containsSlot(1)) {
      final rawRefInnerSchemaConsistent1 = session.valueForSlot(1);
      value.name = _readRefInnerSchemaConsistentName(
          rawRefInnerSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawRefInnerSchemaConsistent1.id)
              : rawRefInnerSchemaConsistent1,
          value.name);
    }
    return value;
  }
}

int _readRefInnerSchemaConsistentId(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field id.')))
      : (value as Int32).value;
}

String _readRefInnerSchemaConsistentName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _refOuterSchemaConsistentForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'inner1',
    identifier: 'inner1',
    id: null,
    shape: GeneratedTypeShape(
      type: RefInnerSchemaConsistent,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: false,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'inner2',
    identifier: 'inner2',
    id: null,
    shape: GeneratedTypeShape(
      type: RefInnerSchemaConsistent,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: false,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefOuterSchemaConsistentSessionWriter
    = GeneratedStructFieldWriter<RefOuterSchemaConsistent>;
typedef _RefOuterSchemaConsistentSessionReader
    = GeneratedStructFieldReader<RefOuterSchemaConsistent>;

void _writeRefOuterSchemaConsistentSessionField0(WriteContext context,
    GeneratedStructField field, RefOuterSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.inner1);
}

void _writeRefOuterSchemaConsistentSessionField1(WriteContext context,
    GeneratedStructField field, RefOuterSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.inner2);
}

void _readRefOuterSchemaConsistentSessionField0(
    ReadContext context, RefOuterSchemaConsistent value, Object? rawValue) {
  value.inner1 = _readRefOuterSchemaConsistentInner1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner1);
}

void _readRefOuterSchemaConsistentSessionField1(
    ReadContext context, RefOuterSchemaConsistent value, Object? rawValue) {
  value.inner2 = _readRefOuterSchemaConsistentInner2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner2);
}

final GeneratedStructRegistration<RefOuterSchemaConsistent>
    _refOuterSchemaConsistentForyRegistration =
    GeneratedStructRegistration<RefOuterSchemaConsistent>(
  sessionWritersBySlot: <_RefOuterSchemaConsistentSessionWriter>[
    _writeRefOuterSchemaConsistentSessionField0,
    _writeRefOuterSchemaConsistentSessionField1,
  ],
  compatibleFactory: RefOuterSchemaConsistent.new,
  compatibleReadersBySlot: <_RefOuterSchemaConsistentSessionReader>[
    _readRefOuterSchemaConsistentSessionField0,
    _readRefOuterSchemaConsistentSessionField1,
  ],
  type: RefOuterSchemaConsistent,
  serializerFactory: _RefOuterSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _refOuterSchemaConsistentForyFieldMetadata,
);

final class _RefOuterSchemaConsistentForySerializer
    extends Serializer<RefOuterSchemaConsistent> {
  List<GeneratedStructField>? _generatedFields;

  _RefOuterSchemaConsistentForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOuterSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOuterSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOuterSchemaConsistent value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.inner1);
      writeGeneratedStructRuntimeValue(context, fields[1], value.inner2);
      return;
    }
    final writers =
        _refOuterSchemaConsistentForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOuterSchemaConsistent read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefOuterSchemaConsistent();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.inner1 = _readRefOuterSchemaConsistentInner1(
          readGeneratedStructRuntimeValue(context, fields[0], value.inner1),
          value.inner1);
      value.inner2 = _readRefOuterSchemaConsistentInner2(
          readGeneratedStructRuntimeValue(context, fields[1], value.inner2),
          value.inner2);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefOuterSchemaConsistent0 = session.valueForSlot(0);
      value.inner1 = _readRefOuterSchemaConsistentInner1(
          rawRefOuterSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawRefOuterSchemaConsistent0.id)
              : rawRefOuterSchemaConsistent0,
          value.inner1);
    }
    if (session.containsSlot(1)) {
      final rawRefOuterSchemaConsistent1 = session.valueForSlot(1);
      value.inner2 = _readRefOuterSchemaConsistentInner2(
          rawRefOuterSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawRefOuterSchemaConsistent1.id)
              : rawRefOuterSchemaConsistent1,
          value.inner2);
    }
    return value;
  }
}

RefInnerSchemaConsistent? _readRefOuterSchemaConsistentInner1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as RefInnerSchemaConsistent?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as RefInnerSchemaConsistent;
}

RefInnerSchemaConsistent? _readRefOuterSchemaConsistentInner2(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as RefInnerSchemaConsistent?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as RefInnerSchemaConsistent;
}

const List<GeneratedFieldMetadata> _refInnerCompatibleForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'id',
    identifier: 'id',
    id: null,
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
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefInnerCompatibleSessionWriter
    = GeneratedStructFieldWriter<RefInnerCompatible>;
typedef _RefInnerCompatibleSessionReader
    = GeneratedStructFieldReader<RefInnerCompatible>;

void _writeRefInnerCompatibleSessionField0(WriteContext context,
    GeneratedStructField field, RefInnerCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.id);
}

void _writeRefInnerCompatibleSessionField1(WriteContext context,
    GeneratedStructField field, RefInnerCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _readRefInnerCompatibleSessionField0(
    ReadContext context, RefInnerCompatible value, Object? rawValue) {
  value.id = _readRefInnerCompatibleId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefInnerCompatibleSessionField1(
    ReadContext context, RefInnerCompatible value, Object? rawValue) {
  value.name = _readRefInnerCompatibleName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefInnerCompatible>
    _refInnerCompatibleForyRegistration =
    GeneratedStructRegistration<RefInnerCompatible>(
  sessionWritersBySlot: <_RefInnerCompatibleSessionWriter>[
    _writeRefInnerCompatibleSessionField0,
    _writeRefInnerCompatibleSessionField1,
  ],
  compatibleFactory: RefInnerCompatible.new,
  compatibleReadersBySlot: <_RefInnerCompatibleSessionReader>[
    _readRefInnerCompatibleSessionField0,
    _readRefInnerCompatibleSessionField1,
  ],
  type: RefInnerCompatible,
  serializerFactory: _RefInnerCompatibleForySerializer.new,
  evolving: true,
  fields: _refInnerCompatibleForyFieldMetadata,
);

final class _RefInnerCompatibleForySerializer
    extends Serializer<RefInnerCompatible> {
  List<GeneratedStructField>? _generatedFields;

  _RefInnerCompatibleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refInnerCompatibleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refInnerCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefInnerCompatible value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers = _refInnerCompatibleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefInnerCompatible read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefInnerCompatible();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefInnerCompatible0 = session.valueForSlot(0);
      value.id = _readRefInnerCompatibleId(
          rawRefInnerCompatible0 is DeferredReadRef
              ? context.getReadRef(rawRefInnerCompatible0.id)
              : rawRefInnerCompatible0,
          value.id);
    }
    if (session.containsSlot(1)) {
      final rawRefInnerCompatible1 = session.valueForSlot(1);
      value.name = _readRefInnerCompatibleName(
          rawRefInnerCompatible1 is DeferredReadRef
              ? context.getReadRef(rawRefInnerCompatible1.id)
              : rawRefInnerCompatible1,
          value.name);
    }
    return value;
  }
}

int _readRefInnerCompatibleId(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field id.')))
      : (value as Int32).value;
}

String _readRefInnerCompatibleName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _refOuterCompatibleForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'inner1',
    identifier: 'inner1',
    id: null,
    shape: GeneratedTypeShape(
      type: RefInnerCompatible,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'inner2',
    identifier: 'inner2',
    id: null,
    shape: GeneratedTypeShape(
      type: RefInnerCompatible,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefOuterCompatibleSessionWriter
    = GeneratedStructFieldWriter<RefOuterCompatible>;
typedef _RefOuterCompatibleSessionReader
    = GeneratedStructFieldReader<RefOuterCompatible>;

void _writeRefOuterCompatibleSessionField0(WriteContext context,
    GeneratedStructField field, RefOuterCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.inner1);
}

void _writeRefOuterCompatibleSessionField1(WriteContext context,
    GeneratedStructField field, RefOuterCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.inner2);
}

void _readRefOuterCompatibleSessionField0(
    ReadContext context, RefOuterCompatible value, Object? rawValue) {
  value.inner1 = _readRefOuterCompatibleInner1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner1);
}

void _readRefOuterCompatibleSessionField1(
    ReadContext context, RefOuterCompatible value, Object? rawValue) {
  value.inner2 = _readRefOuterCompatibleInner2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner2);
}

final GeneratedStructRegistration<RefOuterCompatible>
    _refOuterCompatibleForyRegistration =
    GeneratedStructRegistration<RefOuterCompatible>(
  sessionWritersBySlot: <_RefOuterCompatibleSessionWriter>[
    _writeRefOuterCompatibleSessionField0,
    _writeRefOuterCompatibleSessionField1,
  ],
  compatibleFactory: RefOuterCompatible.new,
  compatibleReadersBySlot: <_RefOuterCompatibleSessionReader>[
    _readRefOuterCompatibleSessionField0,
    _readRefOuterCompatibleSessionField1,
  ],
  type: RefOuterCompatible,
  serializerFactory: _RefOuterCompatibleForySerializer.new,
  evolving: true,
  fields: _refOuterCompatibleForyFieldMetadata,
);

final class _RefOuterCompatibleForySerializer
    extends Serializer<RefOuterCompatible> {
  List<GeneratedStructField>? _generatedFields;

  _RefOuterCompatibleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOuterCompatibleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOuterCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOuterCompatible value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      writeGeneratedStructRuntimeValue(context, fields[0], value.inner1);
      writeGeneratedStructRuntimeValue(context, fields[1], value.inner2);
      return;
    }
    final writers = _refOuterCompatibleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOuterCompatible read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefOuterCompatible();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.inner1 = _readRefOuterCompatibleInner1(
          readGeneratedStructRuntimeValue(context, fields[0], value.inner1),
          value.inner1);
      value.inner2 = _readRefOuterCompatibleInner2(
          readGeneratedStructRuntimeValue(context, fields[1], value.inner2),
          value.inner2);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefOuterCompatible0 = session.valueForSlot(0);
      value.inner1 = _readRefOuterCompatibleInner1(
          rawRefOuterCompatible0 is DeferredReadRef
              ? context.getReadRef(rawRefOuterCompatible0.id)
              : rawRefOuterCompatible0,
          value.inner1);
    }
    if (session.containsSlot(1)) {
      final rawRefOuterCompatible1 = session.valueForSlot(1);
      value.inner2 = _readRefOuterCompatibleInner2(
          rawRefOuterCompatible1 is DeferredReadRef
              ? context.getReadRef(rawRefOuterCompatible1.id)
              : rawRefOuterCompatible1,
          value.inner2);
    }
    return value;
  }
}

RefInnerCompatible? _readRefOuterCompatibleInner1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as RefInnerCompatible?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as RefInnerCompatible;
}

RefInnerCompatible? _readRefOuterCompatibleInner2(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as RefInnerCompatible?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as RefInnerCompatible;
}

const List<GeneratedFieldMetadata> _refOverrideElementForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'id',
    identifier: 'id',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefOverrideElementSessionWriter
    = GeneratedStructFieldWriter<RefOverrideElement>;
typedef _RefOverrideElementSessionReader
    = GeneratedStructFieldReader<RefOverrideElement>;

void _writeRefOverrideElementSessionField0(WriteContext context,
    GeneratedStructField field, RefOverrideElement value) {
  writeGeneratedStructRuntimeValue(context, field, value.id);
}

void _writeRefOverrideElementSessionField1(WriteContext context,
    GeneratedStructField field, RefOverrideElement value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _readRefOverrideElementSessionField0(
    ReadContext context, RefOverrideElement value, Object? rawValue) {
  value.id = _readRefOverrideElementId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefOverrideElementSessionField1(
    ReadContext context, RefOverrideElement value, Object? rawValue) {
  value.name = _readRefOverrideElementName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefOverrideElement>
    _refOverrideElementForyRegistration =
    GeneratedStructRegistration<RefOverrideElement>(
  sessionWritersBySlot: <_RefOverrideElementSessionWriter>[
    _writeRefOverrideElementSessionField0,
    _writeRefOverrideElementSessionField1,
  ],
  compatibleFactory: RefOverrideElement.new,
  compatibleReadersBySlot: <_RefOverrideElementSessionReader>[
    _readRefOverrideElementSessionField0,
    _readRefOverrideElementSessionField1,
  ],
  type: RefOverrideElement,
  serializerFactory: _RefOverrideElementForySerializer.new,
  evolving: true,
  fields: _refOverrideElementForyFieldMetadata,
);

final class _RefOverrideElementForySerializer
    extends Serializer<RefOverrideElement> {
  List<GeneratedStructField>? _generatedFields;

  _RefOverrideElementForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOverrideElementForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refOverrideElementForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOverrideElement value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id.value);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers = _refOverrideElementForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOverrideElement read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefOverrideElement();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefOverrideElement0 = session.valueForSlot(0);
      value.id = _readRefOverrideElementId(
          rawRefOverrideElement0 is DeferredReadRef
              ? context.getReadRef(rawRefOverrideElement0.id)
              : rawRefOverrideElement0,
          value.id);
    }
    if (session.containsSlot(1)) {
      final rawRefOverrideElement1 = session.valueForSlot(1);
      value.name = _readRefOverrideElementName(
          rawRefOverrideElement1 is DeferredReadRef
              ? context.getReadRef(rawRefOverrideElement1.id)
              : rawRefOverrideElement1,
          value.name);
    }
    return value;
  }
}

Int32 _readRefOverrideElementId(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field id.')))
      : value as Int32;
}

String _readRefOverrideElementName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

const List<GeneratedFieldMetadata> _circularRefStructForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'name',
    identifier: 'name',
    id: null,
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
    name: 'selfRef',
    identifier: 'self_ref',
    id: null,
    shape: GeneratedTypeShape(
      type: CircularRefStruct,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _CircularRefStructSessionWriter
    = GeneratedStructFieldWriter<CircularRefStruct>;
typedef _CircularRefStructSessionReader
    = GeneratedStructFieldReader<CircularRefStruct>;

void _writeCircularRefStructSessionField0(
    WriteContext context, GeneratedStructField field, CircularRefStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _writeCircularRefStructSessionField1(
    WriteContext context, GeneratedStructField field, CircularRefStruct value) {
  writeGeneratedStructRuntimeValue(context, field, value.selfRef);
}

void _readCircularRefStructSessionField0(
    ReadContext context, CircularRefStruct value, Object? rawValue) {
  value.name = _readCircularRefStructName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readCircularRefStructSessionField1(
    ReadContext context, CircularRefStruct value, Object? rawValue) {
  value.selfRef = _readCircularRefStructSelfRef(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.selfRef);
}

final GeneratedStructRegistration<CircularRefStruct>
    _circularRefStructForyRegistration =
    GeneratedStructRegistration<CircularRefStruct>(
  sessionWritersBySlot: <_CircularRefStructSessionWriter>[
    _writeCircularRefStructSessionField0,
    _writeCircularRefStructSessionField1,
  ],
  compatibleFactory: CircularRefStruct.new,
  compatibleReadersBySlot: <_CircularRefStructSessionReader>[
    _readCircularRefStructSessionField0,
    _readCircularRefStructSessionField1,
  ],
  type: CircularRefStruct,
  serializerFactory: _CircularRefStructForySerializer.new,
  evolving: true,
  fields: _circularRefStructForyFieldMetadata,
);

final class _CircularRefStructForySerializer
    extends Serializer<CircularRefStruct> {
  List<GeneratedStructField>? _generatedFields;

  _CircularRefStructForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _circularRefStructForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _circularRefStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, CircularRefStruct value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      context.writeString(value.name);
      writeGeneratedStructRuntimeValue(context, fields[1], value.selfRef);
      return;
    }
    final writers = _circularRefStructForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  CircularRefStruct read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = CircularRefStruct();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.name = context.readString();
      value.selfRef = _readCircularRefStructSelfRef(
          readGeneratedStructRuntimeValue(context, fields[1], value.selfRef),
          value.selfRef);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawCircularRefStruct0 = session.valueForSlot(0);
      value.name = _readCircularRefStructName(
          rawCircularRefStruct0 is DeferredReadRef
              ? context.getReadRef(rawCircularRefStruct0.id)
              : rawCircularRefStruct0,
          value.name);
    }
    if (session.containsSlot(1)) {
      final rawCircularRefStruct1 = session.valueForSlot(1);
      value.selfRef = _readCircularRefStructSelfRef(
          rawCircularRefStruct1 is DeferredReadRef
              ? context.getReadRef(rawCircularRefStruct1.id)
              : rawCircularRefStruct1,
          value.selfRef);
    }
    return value;
  }
}

String _readCircularRefStructName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

CircularRefStruct? _readCircularRefStructSelfRef(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as CircularRefStruct?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as CircularRefStruct;
}

const List<GeneratedFieldMetadata> _unsignedSchemaConsistentForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'u64FixedField',
    identifier: 'u64_fixed_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 13,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32FixedField',
    identifier: 'u32_fixed_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 11,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u16Field',
    identifier: 'u16_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt16,
      typeId: 10,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u8Field',
    identifier: 'u8_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt8,
      typeId: 9,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64TaggedField',
    identifier: 'u64_tagged_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64VarField',
    identifier: 'u64_var_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 14,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32VarField',
    identifier: 'u32_var_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 12,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64FixedNullableField',
    identifier: 'u64_fixed_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 13,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32FixedNullableField',
    identifier: 'u32_fixed_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 11,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u16NullableField',
    identifier: 'u16_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt16,
      typeId: 10,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u8NullableField',
    identifier: 'u8_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt8,
      typeId: 9,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64TaggedNullableField',
    identifier: 'u64_tagged_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64VarNullableField',
    identifier: 'u64_var_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 14,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32VarNullableField',
    identifier: 'u32_var_nullable_field',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 12,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _UnsignedSchemaConsistentSessionWriter
    = GeneratedStructFieldWriter<UnsignedSchemaConsistent>;
typedef _UnsignedSchemaConsistentSessionReader
    = GeneratedStructFieldReader<UnsignedSchemaConsistent>;

void _writeUnsignedSchemaConsistentSessionField0(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64FixedField);
}

void _writeUnsignedSchemaConsistentSessionField1(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32FixedField);
}

void _writeUnsignedSchemaConsistentSessionField2(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u16Field);
}

void _writeUnsignedSchemaConsistentSessionField3(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u8Field);
}

void _writeUnsignedSchemaConsistentSessionField4(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64TaggedField);
}

void _writeUnsignedSchemaConsistentSessionField5(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64VarField);
}

void _writeUnsignedSchemaConsistentSessionField6(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32VarField);
}

void _writeUnsignedSchemaConsistentSessionField7(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64FixedNullableField);
}

void _writeUnsignedSchemaConsistentSessionField8(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32FixedNullableField);
}

void _writeUnsignedSchemaConsistentSessionField9(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u16NullableField);
}

void _writeUnsignedSchemaConsistentSessionField10(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u8NullableField);
}

void _writeUnsignedSchemaConsistentSessionField11(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(
      context, field, value.u64TaggedNullableField);
}

void _writeUnsignedSchemaConsistentSessionField12(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64VarNullableField);
}

void _writeUnsignedSchemaConsistentSessionField13(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistent value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32VarNullableField);
}

void _readUnsignedSchemaConsistentSessionField0(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField);
}

void _readUnsignedSchemaConsistentSessionField1(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField);
}

void _readUnsignedSchemaConsistentSessionField2(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u16Field = _readUnsignedSchemaConsistentU16Field(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field);
}

void _readUnsignedSchemaConsistentSessionField3(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u8Field = _readUnsignedSchemaConsistentU8Field(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field);
}

void _readUnsignedSchemaConsistentSessionField4(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField);
}

void _readUnsignedSchemaConsistentSessionField5(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField);
}

void _readUnsignedSchemaConsistentSessionField6(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField);
}

void _readUnsignedSchemaConsistentSessionField7(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64FixedNullableField =
      _readUnsignedSchemaConsistentU64FixedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u64FixedNullableField);
}

void _readUnsignedSchemaConsistentSessionField8(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32FixedNullableField =
      _readUnsignedSchemaConsistentU32FixedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u32FixedNullableField);
}

void _readUnsignedSchemaConsistentSessionField9(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16NullableField);
}

void _readUnsignedSchemaConsistentSessionField10(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8NullableField);
}

void _readUnsignedSchemaConsistentSessionField11(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64TaggedNullableField =
      _readUnsignedSchemaConsistentU64TaggedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u64TaggedNullableField);
}

void _readUnsignedSchemaConsistentSessionField12(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64VarNullableField = _readUnsignedSchemaConsistentU64VarNullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarNullableField);
}

void _readUnsignedSchemaConsistentSessionField13(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32VarNullableField = _readUnsignedSchemaConsistentU32VarNullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarNullableField);
}

final GeneratedStructRegistration<UnsignedSchemaConsistent>
    _unsignedSchemaConsistentForyRegistration =
    GeneratedStructRegistration<UnsignedSchemaConsistent>(
  sessionWritersBySlot: <_UnsignedSchemaConsistentSessionWriter>[
    _writeUnsignedSchemaConsistentSessionField0,
    _writeUnsignedSchemaConsistentSessionField1,
    _writeUnsignedSchemaConsistentSessionField2,
    _writeUnsignedSchemaConsistentSessionField3,
    _writeUnsignedSchemaConsistentSessionField4,
    _writeUnsignedSchemaConsistentSessionField5,
    _writeUnsignedSchemaConsistentSessionField6,
    _writeUnsignedSchemaConsistentSessionField7,
    _writeUnsignedSchemaConsistentSessionField8,
    _writeUnsignedSchemaConsistentSessionField9,
    _writeUnsignedSchemaConsistentSessionField10,
    _writeUnsignedSchemaConsistentSessionField11,
    _writeUnsignedSchemaConsistentSessionField12,
    _writeUnsignedSchemaConsistentSessionField13,
  ],
  compatibleFactory: UnsignedSchemaConsistent.new,
  compatibleReadersBySlot: <_UnsignedSchemaConsistentSessionReader>[
    _readUnsignedSchemaConsistentSessionField0,
    _readUnsignedSchemaConsistentSessionField1,
    _readUnsignedSchemaConsistentSessionField2,
    _readUnsignedSchemaConsistentSessionField3,
    _readUnsignedSchemaConsistentSessionField4,
    _readUnsignedSchemaConsistentSessionField5,
    _readUnsignedSchemaConsistentSessionField6,
    _readUnsignedSchemaConsistentSessionField7,
    _readUnsignedSchemaConsistentSessionField8,
    _readUnsignedSchemaConsistentSessionField9,
    _readUnsignedSchemaConsistentSessionField10,
    _readUnsignedSchemaConsistentSessionField11,
    _readUnsignedSchemaConsistentSessionField12,
    _readUnsignedSchemaConsistentSessionField13,
  ],
  type: UnsignedSchemaConsistent,
  serializerFactory: _UnsignedSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaConsistentForyFieldMetadata,
);

final class _UnsignedSchemaConsistentForySerializer
    extends Serializer<UnsignedSchemaConsistent> {
  List<GeneratedStructField>? _generatedFields;

  _UnsignedSchemaConsistentForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaConsistent value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 40);
      cursor0.writeUint64(value.u64FixedField);
      cursor0.writeUint32(value.u32FixedField.value);
      cursor0.writeUint16(value.u16Field.value);
      cursor0.writeUint8(value.u8Field.value);
      cursor0.writeTaggedUint64(value.u64TaggedField);
      cursor0.writeVarUint64(value.u64VarField);
      cursor0.writeVarUint32(value.u32VarField.value);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(
          context, fields[7], value.u64FixedNullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[8], value.u32FixedNullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[9], value.u16NullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[10], value.u8NullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[11], value.u64TaggedNullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[12], value.u64VarNullableField);
      writeGeneratedStructRuntimeValue(
          context, fields[13], value.u32VarNullableField);
      return;
    }
    final writers =
        _unsignedSchemaConsistentForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaConsistent read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = UnsignedSchemaConsistent();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.u64FixedField = cursor0.readUint64();
      value.u32FixedField = UInt32(cursor0.readUint32());
      value.u16Field = UInt16(cursor0.readUint16());
      value.u8Field = UInt8(cursor0.readUint8());
      value.u64TaggedField = cursor0.readTaggedUint64();
      value.u64VarField = cursor0.readVarUint64();
      value.u32VarField = UInt32(cursor0.readVarUint32());
      cursor0.finish();
      value.u64FixedNullableField =
          _readUnsignedSchemaConsistentU64FixedNullableField(
              readGeneratedStructRuntimeValue(
                  context, fields[7], value.u64FixedNullableField),
              value.u64FixedNullableField);
      value.u32FixedNullableField =
          _readUnsignedSchemaConsistentU32FixedNullableField(
              readGeneratedStructRuntimeValue(
                  context, fields[8], value.u32FixedNullableField),
              value.u32FixedNullableField);
      value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
          readGeneratedStructRuntimeValue(
              context, fields[9], value.u16NullableField),
          value.u16NullableField);
      value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
          readGeneratedStructRuntimeValue(
              context, fields[10], value.u8NullableField),
          value.u8NullableField);
      value.u64TaggedNullableField =
          _readUnsignedSchemaConsistentU64TaggedNullableField(
              readGeneratedStructRuntimeValue(
                  context, fields[11], value.u64TaggedNullableField),
              value.u64TaggedNullableField);
      value.u64VarNullableField =
          _readUnsignedSchemaConsistentU64VarNullableField(
              readGeneratedStructRuntimeValue(
                  context, fields[12], value.u64VarNullableField),
              value.u64VarNullableField);
      value.u32VarNullableField =
          _readUnsignedSchemaConsistentU32VarNullableField(
              readGeneratedStructRuntimeValue(
                  context, fields[13], value.u32VarNullableField),
              value.u32VarNullableField);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawUnsignedSchemaConsistent0 = session.valueForSlot(0);
      value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
          rawUnsignedSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent0.id)
              : rawUnsignedSchemaConsistent0,
          value.u64FixedField);
    }
    if (session.containsSlot(1)) {
      final rawUnsignedSchemaConsistent1 = session.valueForSlot(1);
      value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
          rawUnsignedSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent1.id)
              : rawUnsignedSchemaConsistent1,
          value.u32FixedField);
    }
    if (session.containsSlot(2)) {
      final rawUnsignedSchemaConsistent2 = session.valueForSlot(2);
      value.u16Field = _readUnsignedSchemaConsistentU16Field(
          rawUnsignedSchemaConsistent2 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent2.id)
              : rawUnsignedSchemaConsistent2,
          value.u16Field);
    }
    if (session.containsSlot(3)) {
      final rawUnsignedSchemaConsistent3 = session.valueForSlot(3);
      value.u8Field = _readUnsignedSchemaConsistentU8Field(
          rawUnsignedSchemaConsistent3 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent3.id)
              : rawUnsignedSchemaConsistent3,
          value.u8Field);
    }
    if (session.containsSlot(4)) {
      final rawUnsignedSchemaConsistent4 = session.valueForSlot(4);
      value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
          rawUnsignedSchemaConsistent4 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent4.id)
              : rawUnsignedSchemaConsistent4,
          value.u64TaggedField);
    }
    if (session.containsSlot(5)) {
      final rawUnsignedSchemaConsistent5 = session.valueForSlot(5);
      value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
          rawUnsignedSchemaConsistent5 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent5.id)
              : rawUnsignedSchemaConsistent5,
          value.u64VarField);
    }
    if (session.containsSlot(6)) {
      final rawUnsignedSchemaConsistent6 = session.valueForSlot(6);
      value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
          rawUnsignedSchemaConsistent6 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent6.id)
              : rawUnsignedSchemaConsistent6,
          value.u32VarField);
    }
    if (session.containsSlot(7)) {
      final rawUnsignedSchemaConsistent7 = session.valueForSlot(7);
      value.u64FixedNullableField =
          _readUnsignedSchemaConsistentU64FixedNullableField(
              rawUnsignedSchemaConsistent7 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent7.id)
                  : rawUnsignedSchemaConsistent7,
              value.u64FixedNullableField);
    }
    if (session.containsSlot(8)) {
      final rawUnsignedSchemaConsistent8 = session.valueForSlot(8);
      value.u32FixedNullableField =
          _readUnsignedSchemaConsistentU32FixedNullableField(
              rawUnsignedSchemaConsistent8 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent8.id)
                  : rawUnsignedSchemaConsistent8,
              value.u32FixedNullableField);
    }
    if (session.containsSlot(9)) {
      final rawUnsignedSchemaConsistent9 = session.valueForSlot(9);
      value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
          rawUnsignedSchemaConsistent9 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent9.id)
              : rawUnsignedSchemaConsistent9,
          value.u16NullableField);
    }
    if (session.containsSlot(10)) {
      final rawUnsignedSchemaConsistent10 = session.valueForSlot(10);
      value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
          rawUnsignedSchemaConsistent10 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent10.id)
              : rawUnsignedSchemaConsistent10,
          value.u8NullableField);
    }
    if (session.containsSlot(11)) {
      final rawUnsignedSchemaConsistent11 = session.valueForSlot(11);
      value.u64TaggedNullableField =
          _readUnsignedSchemaConsistentU64TaggedNullableField(
              rawUnsignedSchemaConsistent11 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent11.id)
                  : rawUnsignedSchemaConsistent11,
              value.u64TaggedNullableField);
    }
    if (session.containsSlot(12)) {
      final rawUnsignedSchemaConsistent12 = session.valueForSlot(12);
      value.u64VarNullableField =
          _readUnsignedSchemaConsistentU64VarNullableField(
              rawUnsignedSchemaConsistent12 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent12.id)
                  : rawUnsignedSchemaConsistent12,
              value.u64VarNullableField);
    }
    if (session.containsSlot(13)) {
      final rawUnsignedSchemaConsistent13 = session.valueForSlot(13);
      value.u32VarNullableField =
          _readUnsignedSchemaConsistentU32VarNullableField(
              rawUnsignedSchemaConsistent13 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent13.id)
                  : rawUnsignedSchemaConsistent13,
              value.u32VarNullableField);
    }
    return value;
  }
}

int _readUnsignedSchemaConsistentU64FixedField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64FixedField.')))
      : value as int;
}

UInt32 _readUnsignedSchemaConsistentU32FixedField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt32
          : (throw StateError(
              'Received null for non-nullable field u32FixedField.')))
      : value as UInt32;
}

UInt16 _readUnsignedSchemaConsistentU16Field(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt16
          : (throw StateError(
              'Received null for non-nullable field u16Field.')))
      : value as UInt16;
}

UInt8 _readUnsignedSchemaConsistentU8Field(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt8
          : (throw StateError('Received null for non-nullable field u8Field.')))
      : value as UInt8;
}

int _readUnsignedSchemaConsistentU64TaggedField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64TaggedField.')))
      : value as int;
}

int _readUnsignedSchemaConsistentU64VarField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64VarField.')))
      : value as int;
}

UInt32 _readUnsignedSchemaConsistentU32VarField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt32
          : (throw StateError(
              'Received null for non-nullable field u32VarField.')))
      : value as UInt32;
}

int? _readUnsignedSchemaConsistentU64FixedNullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

UInt32? _readUnsignedSchemaConsistentU32FixedNullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt32;
}

UInt16? _readUnsignedSchemaConsistentU16NullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt16?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt16;
}

UInt8? _readUnsignedSchemaConsistentU8NullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt8?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt8;
}

int? _readUnsignedSchemaConsistentU64TaggedNullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

int? _readUnsignedSchemaConsistentU64VarNullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

UInt32? _readUnsignedSchemaConsistentU32VarNullableField(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt32;
}

const List<GeneratedFieldMetadata>
    _unsignedSchemaConsistentSimpleForyFieldMetadata = <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'u64Tagged',
    identifier: 'u64_tagged',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64TaggedNullable',
    identifier: 'u64_tagged_nullable',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _UnsignedSchemaConsistentSimpleSessionWriter
    = GeneratedStructFieldWriter<UnsignedSchemaConsistentSimple>;
typedef _UnsignedSchemaConsistentSimpleSessionReader
    = GeneratedStructFieldReader<UnsignedSchemaConsistentSimple>;

void _writeUnsignedSchemaConsistentSimpleSessionField0(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistentSimple value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64Tagged);
}

void _writeUnsignedSchemaConsistentSimpleSessionField1(WriteContext context,
    GeneratedStructField field, UnsignedSchemaConsistentSimple value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64TaggedNullable);
}

void _readUnsignedSchemaConsistentSimpleSessionField0(ReadContext context,
    UnsignedSchemaConsistentSimple value, Object? rawValue) {
  value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64Tagged);
}

void _readUnsignedSchemaConsistentSimpleSessionField1(ReadContext context,
    UnsignedSchemaConsistentSimple value, Object? rawValue) {
  value.u64TaggedNullable =
      _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u64TaggedNullable);
}

final GeneratedStructRegistration<UnsignedSchemaConsistentSimple>
    _unsignedSchemaConsistentSimpleForyRegistration =
    GeneratedStructRegistration<UnsignedSchemaConsistentSimple>(
  sessionWritersBySlot: <_UnsignedSchemaConsistentSimpleSessionWriter>[
    _writeUnsignedSchemaConsistentSimpleSessionField0,
    _writeUnsignedSchemaConsistentSimpleSessionField1,
  ],
  compatibleFactory: UnsignedSchemaConsistentSimple.new,
  compatibleReadersBySlot: <_UnsignedSchemaConsistentSimpleSessionReader>[
    _readUnsignedSchemaConsistentSimpleSessionField0,
    _readUnsignedSchemaConsistentSimpleSessionField1,
  ],
  type: UnsignedSchemaConsistentSimple,
  serializerFactory: _UnsignedSchemaConsistentSimpleForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaConsistentSimpleForyFieldMetadata,
);

final class _UnsignedSchemaConsistentSimpleForySerializer
    extends Serializer<UnsignedSchemaConsistentSimple> {
  List<GeneratedStructField>? _generatedFields;

  _UnsignedSchemaConsistentSimpleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaConsistentSimpleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaConsistentSimpleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaConsistentSimple value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeTaggedUint64(value.u64Tagged);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(
          context, fields[1], value.u64TaggedNullable);
      return;
    }
    final writers =
        _unsignedSchemaConsistentSimpleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaConsistentSimple read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = UnsignedSchemaConsistentSimple();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.u64Tagged = cursor0.readTaggedUint64();
      cursor0.finish();
      value.u64TaggedNullable =
          _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
              readGeneratedStructRuntimeValue(
                  context, fields[1], value.u64TaggedNullable),
              value.u64TaggedNullable);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawUnsignedSchemaConsistentSimple0 = session.valueForSlot(0);
      value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
          rawUnsignedSchemaConsistentSimple0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistentSimple0.id)
              : rawUnsignedSchemaConsistentSimple0,
          value.u64Tagged);
    }
    if (session.containsSlot(1)) {
      final rawUnsignedSchemaConsistentSimple1 = session.valueForSlot(1);
      value.u64TaggedNullable =
          _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
              rawUnsignedSchemaConsistentSimple1 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistentSimple1.id)
                  : rawUnsignedSchemaConsistentSimple1,
              value.u64TaggedNullable);
    }
    return value;
  }
}

int _readUnsignedSchemaConsistentSimpleU64Tagged(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64Tagged.')))
      : value as int;
}

int? _readUnsignedSchemaConsistentSimpleU64TaggedNullable(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

const List<GeneratedFieldMetadata> _unsignedSchemaCompatibleForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'u64FixedField2',
    identifier: 'u64_fixed_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 13,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32FixedField2',
    identifier: 'u32_fixed_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 11,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u16Field2',
    identifier: 'u16_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt16,
      typeId: 10,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u8Field2',
    identifier: 'u8_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt8,
      typeId: 9,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64TaggedField2',
    identifier: 'u64_tagged_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64VarField2',
    identifier: 'u64_var_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 14,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32VarField2',
    identifier: 'u32_var_field2',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 12,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64FixedField1',
    identifier: 'u64_fixed_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 13,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32FixedField1',
    identifier: 'u32_fixed_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 11,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u16Field1',
    identifier: 'u16_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt16,
      typeId: 10,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u8Field1',
    identifier: 'u8_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt8,
      typeId: 9,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64TaggedField1',
    identifier: 'u64_tagged_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u64VarField1',
    identifier: 'u64_var_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: int,
      typeId: 14,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'u32VarField1',
    identifier: 'u32_var_field1',
    id: null,
    shape: GeneratedTypeShape(
      type: UInt32,
      typeId: 12,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _UnsignedSchemaCompatibleSessionWriter
    = GeneratedStructFieldWriter<UnsignedSchemaCompatible>;
typedef _UnsignedSchemaCompatibleSessionReader
    = GeneratedStructFieldReader<UnsignedSchemaCompatible>;

void _writeUnsignedSchemaCompatibleSessionField0(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64FixedField2);
}

void _writeUnsignedSchemaCompatibleSessionField1(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32FixedField2);
}

void _writeUnsignedSchemaCompatibleSessionField2(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u16Field2);
}

void _writeUnsignedSchemaCompatibleSessionField3(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u8Field2);
}

void _writeUnsignedSchemaCompatibleSessionField4(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64TaggedField2);
}

void _writeUnsignedSchemaCompatibleSessionField5(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64VarField2);
}

void _writeUnsignedSchemaCompatibleSessionField6(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32VarField2);
}

void _writeUnsignedSchemaCompatibleSessionField7(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64FixedField1);
}

void _writeUnsignedSchemaCompatibleSessionField8(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32FixedField1);
}

void _writeUnsignedSchemaCompatibleSessionField9(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u16Field1);
}

void _writeUnsignedSchemaCompatibleSessionField10(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u8Field1);
}

void _writeUnsignedSchemaCompatibleSessionField11(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64TaggedField1);
}

void _writeUnsignedSchemaCompatibleSessionField12(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u64VarField1);
}

void _writeUnsignedSchemaCompatibleSessionField13(WriteContext context,
    GeneratedStructField field, UnsignedSchemaCompatible value) {
  writeGeneratedStructRuntimeValue(context, field, value.u32VarField1);
}

void _readUnsignedSchemaCompatibleSessionField0(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField2);
}

void _readUnsignedSchemaCompatibleSessionField1(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField2);
}

void _readUnsignedSchemaCompatibleSessionField2(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field2);
}

void _readUnsignedSchemaCompatibleSessionField3(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field2);
}

void _readUnsignedSchemaCompatibleSessionField4(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField2);
}

void _readUnsignedSchemaCompatibleSessionField5(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField2);
}

void _readUnsignedSchemaCompatibleSessionField6(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField2);
}

void _readUnsignedSchemaCompatibleSessionField7(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField1);
}

void _readUnsignedSchemaCompatibleSessionField8(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField1);
}

void _readUnsignedSchemaCompatibleSessionField9(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field1);
}

void _readUnsignedSchemaCompatibleSessionField10(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field1);
}

void _readUnsignedSchemaCompatibleSessionField11(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField1);
}

void _readUnsignedSchemaCompatibleSessionField12(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField1);
}

void _readUnsignedSchemaCompatibleSessionField13(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField1);
}

final GeneratedStructRegistration<UnsignedSchemaCompatible>
    _unsignedSchemaCompatibleForyRegistration =
    GeneratedStructRegistration<UnsignedSchemaCompatible>(
  sessionWritersBySlot: <_UnsignedSchemaCompatibleSessionWriter>[
    _writeUnsignedSchemaCompatibleSessionField0,
    _writeUnsignedSchemaCompatibleSessionField1,
    _writeUnsignedSchemaCompatibleSessionField2,
    _writeUnsignedSchemaCompatibleSessionField3,
    _writeUnsignedSchemaCompatibleSessionField4,
    _writeUnsignedSchemaCompatibleSessionField5,
    _writeUnsignedSchemaCompatibleSessionField6,
    _writeUnsignedSchemaCompatibleSessionField7,
    _writeUnsignedSchemaCompatibleSessionField8,
    _writeUnsignedSchemaCompatibleSessionField9,
    _writeUnsignedSchemaCompatibleSessionField10,
    _writeUnsignedSchemaCompatibleSessionField11,
    _writeUnsignedSchemaCompatibleSessionField12,
    _writeUnsignedSchemaCompatibleSessionField13,
  ],
  compatibleFactory: UnsignedSchemaCompatible.new,
  compatibleReadersBySlot: <_UnsignedSchemaCompatibleSessionReader>[
    _readUnsignedSchemaCompatibleSessionField0,
    _readUnsignedSchemaCompatibleSessionField1,
    _readUnsignedSchemaCompatibleSessionField2,
    _readUnsignedSchemaCompatibleSessionField3,
    _readUnsignedSchemaCompatibleSessionField4,
    _readUnsignedSchemaCompatibleSessionField5,
    _readUnsignedSchemaCompatibleSessionField6,
    _readUnsignedSchemaCompatibleSessionField7,
    _readUnsignedSchemaCompatibleSessionField8,
    _readUnsignedSchemaCompatibleSessionField9,
    _readUnsignedSchemaCompatibleSessionField10,
    _readUnsignedSchemaCompatibleSessionField11,
    _readUnsignedSchemaCompatibleSessionField12,
    _readUnsignedSchemaCompatibleSessionField13,
  ],
  type: UnsignedSchemaCompatible,
  serializerFactory: _UnsignedSchemaCompatibleForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaCompatibleForyFieldMetadata,
);

final class _UnsignedSchemaCompatibleForySerializer
    extends Serializer<UnsignedSchemaCompatible> {
  List<GeneratedStructField>? _generatedFields;

  _UnsignedSchemaCompatibleForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaCompatibleForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _unsignedSchemaCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaCompatible value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 40);
      cursor0.writeUint64(value.u64FixedField2);
      cursor0.writeUint32(value.u32FixedField2.value);
      cursor0.writeUint16(value.u16Field2.value);
      cursor0.writeUint8(value.u8Field2.value);
      cursor0.writeTaggedUint64(value.u64TaggedField2);
      cursor0.writeVarUint64(value.u64VarField2);
      cursor0.writeVarUint32(value.u32VarField2.value);
      cursor0.finish();
      writeGeneratedStructRuntimeValue(
          context, fields[7], value.u64FixedField1);
      writeGeneratedStructRuntimeValue(
          context, fields[8], value.u32FixedField1);
      writeGeneratedStructRuntimeValue(context, fields[9], value.u16Field1);
      writeGeneratedStructRuntimeValue(context, fields[10], value.u8Field1);
      writeGeneratedStructRuntimeValue(
          context, fields[11], value.u64TaggedField1);
      writeGeneratedStructRuntimeValue(context, fields[12], value.u64VarField1);
      writeGeneratedStructRuntimeValue(context, fields[13], value.u32VarField1);
      return;
    }
    final writers =
        _unsignedSchemaCompatibleForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaCompatible read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = UnsignedSchemaCompatible();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.u64FixedField2 = cursor0.readUint64();
      value.u32FixedField2 = UInt32(cursor0.readUint32());
      value.u16Field2 = UInt16(cursor0.readUint16());
      value.u8Field2 = UInt8(cursor0.readUint8());
      value.u64TaggedField2 = cursor0.readTaggedUint64();
      value.u64VarField2 = cursor0.readVarUint64();
      value.u32VarField2 = UInt32(cursor0.readVarUint32());
      cursor0.finish();
      value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
          readGeneratedStructRuntimeValue(
              context, fields[7], value.u64FixedField1),
          value.u64FixedField1);
      value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
          readGeneratedStructRuntimeValue(
              context, fields[8], value.u32FixedField1),
          value.u32FixedField1);
      value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
          readGeneratedStructRuntimeValue(context, fields[9], value.u16Field1),
          value.u16Field1);
      value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
          readGeneratedStructRuntimeValue(context, fields[10], value.u8Field1),
          value.u8Field1);
      value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
          readGeneratedStructRuntimeValue(
              context, fields[11], value.u64TaggedField1),
          value.u64TaggedField1);
      value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
          readGeneratedStructRuntimeValue(
              context, fields[12], value.u64VarField1),
          value.u64VarField1);
      value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
          readGeneratedStructRuntimeValue(
              context, fields[13], value.u32VarField1),
          value.u32VarField1);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawUnsignedSchemaCompatible0 = session.valueForSlot(0);
      value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
          rawUnsignedSchemaCompatible0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible0.id)
              : rawUnsignedSchemaCompatible0,
          value.u64FixedField2);
    }
    if (session.containsSlot(1)) {
      final rawUnsignedSchemaCompatible1 = session.valueForSlot(1);
      value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
          rawUnsignedSchemaCompatible1 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible1.id)
              : rawUnsignedSchemaCompatible1,
          value.u32FixedField2);
    }
    if (session.containsSlot(2)) {
      final rawUnsignedSchemaCompatible2 = session.valueForSlot(2);
      value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
          rawUnsignedSchemaCompatible2 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible2.id)
              : rawUnsignedSchemaCompatible2,
          value.u16Field2);
    }
    if (session.containsSlot(3)) {
      final rawUnsignedSchemaCompatible3 = session.valueForSlot(3);
      value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
          rawUnsignedSchemaCompatible3 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible3.id)
              : rawUnsignedSchemaCompatible3,
          value.u8Field2);
    }
    if (session.containsSlot(4)) {
      final rawUnsignedSchemaCompatible4 = session.valueForSlot(4);
      value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
          rawUnsignedSchemaCompatible4 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible4.id)
              : rawUnsignedSchemaCompatible4,
          value.u64TaggedField2);
    }
    if (session.containsSlot(5)) {
      final rawUnsignedSchemaCompatible5 = session.valueForSlot(5);
      value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
          rawUnsignedSchemaCompatible5 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible5.id)
              : rawUnsignedSchemaCompatible5,
          value.u64VarField2);
    }
    if (session.containsSlot(6)) {
      final rawUnsignedSchemaCompatible6 = session.valueForSlot(6);
      value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
          rawUnsignedSchemaCompatible6 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible6.id)
              : rawUnsignedSchemaCompatible6,
          value.u32VarField2);
    }
    if (session.containsSlot(7)) {
      final rawUnsignedSchemaCompatible7 = session.valueForSlot(7);
      value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
          rawUnsignedSchemaCompatible7 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible7.id)
              : rawUnsignedSchemaCompatible7,
          value.u64FixedField1);
    }
    if (session.containsSlot(8)) {
      final rawUnsignedSchemaCompatible8 = session.valueForSlot(8);
      value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
          rawUnsignedSchemaCompatible8 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible8.id)
              : rawUnsignedSchemaCompatible8,
          value.u32FixedField1);
    }
    if (session.containsSlot(9)) {
      final rawUnsignedSchemaCompatible9 = session.valueForSlot(9);
      value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
          rawUnsignedSchemaCompatible9 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible9.id)
              : rawUnsignedSchemaCompatible9,
          value.u16Field1);
    }
    if (session.containsSlot(10)) {
      final rawUnsignedSchemaCompatible10 = session.valueForSlot(10);
      value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
          rawUnsignedSchemaCompatible10 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible10.id)
              : rawUnsignedSchemaCompatible10,
          value.u8Field1);
    }
    if (session.containsSlot(11)) {
      final rawUnsignedSchemaCompatible11 = session.valueForSlot(11);
      value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
          rawUnsignedSchemaCompatible11 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible11.id)
              : rawUnsignedSchemaCompatible11,
          value.u64TaggedField1);
    }
    if (session.containsSlot(12)) {
      final rawUnsignedSchemaCompatible12 = session.valueForSlot(12);
      value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
          rawUnsignedSchemaCompatible12 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible12.id)
              : rawUnsignedSchemaCompatible12,
          value.u64VarField1);
    }
    if (session.containsSlot(13)) {
      final rawUnsignedSchemaCompatible13 = session.valueForSlot(13);
      value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
          rawUnsignedSchemaCompatible13 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible13.id)
              : rawUnsignedSchemaCompatible13,
          value.u32VarField1);
    }
    return value;
  }
}

int _readUnsignedSchemaCompatibleU64FixedField2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64FixedField2.')))
      : value as int;
}

UInt32 _readUnsignedSchemaCompatibleU32FixedField2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt32
          : (throw StateError(
              'Received null for non-nullable field u32FixedField2.')))
      : value as UInt32;
}

UInt16 _readUnsignedSchemaCompatibleU16Field2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt16
          : (throw StateError(
              'Received null for non-nullable field u16Field2.')))
      : value as UInt16;
}

UInt8 _readUnsignedSchemaCompatibleU8Field2(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt8
          : (throw StateError(
              'Received null for non-nullable field u8Field2.')))
      : value as UInt8;
}

int _readUnsignedSchemaCompatibleU64TaggedField2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64TaggedField2.')))
      : value as int;
}

int _readUnsignedSchemaCompatibleU64VarField2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError(
              'Received null for non-nullable field u64VarField2.')))
      : value as int;
}

UInt32 _readUnsignedSchemaCompatibleU32VarField2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as UInt32
          : (throw StateError(
              'Received null for non-nullable field u32VarField2.')))
      : value as UInt32;
}

int? _readUnsignedSchemaCompatibleU64FixedField1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

UInt32? _readUnsignedSchemaCompatibleU32FixedField1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt32;
}

UInt16? _readUnsignedSchemaCompatibleU16Field1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt16?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt16;
}

UInt8? _readUnsignedSchemaCompatibleU8Field1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt8?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt8;
}

int? _readUnsignedSchemaCompatibleU64TaggedField1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

int? _readUnsignedSchemaCompatibleU64VarField1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as int?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as int;
}

UInt32? _readUnsignedSchemaCompatibleU32VarField1(Object? value,
    [Object? fallback]) {
  return value == null
      ? null as UInt32?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as UInt32;
}

final GeneratedEnumRegistration _colorForyRegistration =
    GeneratedEnumRegistration(
  type: Color,
  serializerFactory: _ColorForySerializer.new,
);

final GeneratedEnumRegistration _testEnumForyRegistration =
    GeneratedEnumRegistration(
  type: TestEnum,
  serializerFactory: _TestEnumForySerializer.new,
);

void registerXlangTestModelsForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  if (type == Color) {
    registerGeneratedEnum(fory, _colorForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TestEnum) {
    registerGeneratedEnum(fory, _testEnumForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStructEvolution) {
    registerGeneratedStruct(fory, _twoEnumFieldStructEvolutionForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item) {
    registerGeneratedStruct(fory, _itemForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == SimpleStruct) {
    registerGeneratedStruct(fory, _simpleStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingOverrideStruct) {
    registerGeneratedStruct(fory, _evolvingOverrideStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedOverrideStruct) {
    registerGeneratedStruct(fory, _fixedOverrideStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item1) {
    registerGeneratedStruct(fory, _item1ForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithUnion2) {
    registerGeneratedStruct(fory, _structWithUnion2ForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithList) {
    registerGeneratedStruct(fory, _structWithListForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithMap) {
    registerGeneratedStruct(fory, _structWithMapForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyStruct) {
    registerGeneratedStruct(fory, _myStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyWrapper) {
    registerGeneratedStruct(fory, _myWrapperForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyWrapper) {
    registerGeneratedStruct(fory, _emptyWrapperForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == VersionCheckStruct) {
    registerGeneratedStruct(fory, _versionCheckStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Dog) {
    registerGeneratedStruct(fory, _dogForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Cat) {
    registerGeneratedStruct(fory, _catForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalListHolder) {
    registerGeneratedStruct(fory, _animalListHolderForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalMapHolder) {
    registerGeneratedStruct(fory, _animalMapHolderForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyStruct) {
    registerGeneratedStruct(fory, _emptyStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneStringFieldStruct) {
    registerGeneratedStruct(fory, _oneStringFieldStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoStringFieldStruct) {
    registerGeneratedStruct(fory, _twoStringFieldStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneEnumFieldStruct) {
    registerGeneratedStruct(fory, _oneEnumFieldStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStruct) {
    registerGeneratedStruct(fory, _twoEnumFieldStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveSchemaConsistent) {
    registerGeneratedStruct(
        fory, _nullableComprehensiveSchemaConsistentForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveCompatible) {
    registerGeneratedStruct(
        fory, _nullableComprehensiveCompatibleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerSchemaConsistent) {
    registerGeneratedStruct(fory, _refInnerSchemaConsistentForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterSchemaConsistent) {
    registerGeneratedStruct(fory, _refOuterSchemaConsistentForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerCompatible) {
    registerGeneratedStruct(fory, _refInnerCompatibleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterCompatible) {
    registerGeneratedStruct(fory, _refOuterCompatibleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOverrideElement) {
    registerGeneratedStruct(fory, _refOverrideElementForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == CircularRefStruct) {
    registerGeneratedStruct(fory, _circularRefStructForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistent) {
    registerGeneratedStruct(fory, _unsignedSchemaConsistentForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistentSimple) {
    registerGeneratedStruct(
        fory, _unsignedSchemaConsistentSimpleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaCompatible) {
    registerGeneratedStruct(fory, _unsignedSchemaCompatibleForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void registerXlangTestModelsForyTypes(Fory fory) {
  registerGeneratedEnum(fory, _colorForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Color');
  registerGeneratedEnum(fory, _testEnumForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'TestEnum');
  registerGeneratedStruct(fory, _twoEnumFieldStructEvolutionForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStructEvolution');
  registerGeneratedStruct(fory, _itemForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item');
  registerGeneratedStruct(fory, _simpleStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'SimpleStruct');
  registerGeneratedStruct(fory, _evolvingOverrideStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EvolvingOverrideStruct');
  registerGeneratedStruct(fory, _fixedOverrideStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'FixedOverrideStruct');
  registerGeneratedStruct(fory, _item1ForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item1');
  registerGeneratedStruct(fory, _structWithUnion2ForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithUnion2');
  registerGeneratedStruct(fory, _structWithListForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithList');
  registerGeneratedStruct(fory, _structWithMapForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithMap');
  registerGeneratedStruct(fory, _myStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyStruct');
  registerGeneratedStruct(fory, _myWrapperForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyWrapper');
  registerGeneratedStruct(fory, _emptyWrapperForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EmptyWrapper');
  registerGeneratedStruct(fory, _versionCheckStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'VersionCheckStruct');
  registerGeneratedStruct(fory, _dogForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Dog');
  registerGeneratedStruct(fory, _catForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Cat');
  registerGeneratedStruct(fory, _animalListHolderForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalListHolder');
  registerGeneratedStruct(fory, _animalMapHolderForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalMapHolder');
  registerGeneratedStruct(fory, _emptyStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'EmptyStruct');
  registerGeneratedStruct(fory, _oneStringFieldStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneStringFieldStruct');
  registerGeneratedStruct(fory, _twoStringFieldStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoStringFieldStruct');
  registerGeneratedStruct(fory, _oneEnumFieldStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneEnumFieldStruct');
  registerGeneratedStruct(fory, _twoEnumFieldStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStruct');
  registerGeneratedStruct(
      fory, _nullableComprehensiveSchemaConsistentForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveSchemaConsistent');
  registerGeneratedStruct(
      fory, _nullableComprehensiveCompatibleForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveCompatible');
  registerGeneratedStruct(fory, _refInnerSchemaConsistentForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerSchemaConsistent');
  registerGeneratedStruct(fory, _refOuterSchemaConsistentForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterSchemaConsistent');
  registerGeneratedStruct(fory, _refInnerCompatibleForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerCompatible');
  registerGeneratedStruct(fory, _refOuterCompatibleForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterCompatible');
  registerGeneratedStruct(fory, _refOverrideElementForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOverrideElement');
  registerGeneratedStruct(fory, _circularRefStructForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'CircularRefStruct');
  registerGeneratedStruct(fory, _unsignedSchemaConsistentForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistent');
  registerGeneratedStruct(fory, _unsignedSchemaConsistentSimpleForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistentSimple');
  registerGeneratedStruct(fory, _unsignedSchemaCompatibleForyRegistration,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaCompatible');
}
