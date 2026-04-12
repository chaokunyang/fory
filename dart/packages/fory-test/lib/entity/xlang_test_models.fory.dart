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

const List<GeneratedFieldInfo> _twoEnumFieldStructEvolutionForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _TwoEnumFieldStructEvolutionFieldWriter
    = GeneratedStructFieldInfoWriter<TwoEnumFieldStructEvolution>;
typedef _TwoEnumFieldStructEvolutionFieldReader
    = GeneratedStructFieldInfoReader<TwoEnumFieldStructEvolution>;

void _writeTwoEnumFieldStructEvolutionField0(WriteContext context,
    GeneratedStructFieldInfo field, TwoEnumFieldStructEvolution value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeTwoEnumFieldStructEvolutionField1(WriteContext context,
    GeneratedStructFieldInfo field, TwoEnumFieldStructEvolution value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _readTwoEnumFieldStructEvolutionField0(
    ReadContext context, TwoEnumFieldStructEvolution value, Object? rawValue) {
  value.f1 = _readTwoEnumFieldStructEvolutionF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoEnumFieldStructEvolutionField1(
    ReadContext context, TwoEnumFieldStructEvolution value, Object? rawValue) {
  value.f2 = _readTwoEnumFieldStructEvolutionF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoEnumFieldStructEvolution>
    _twoEnumFieldStructEvolutionForyRegistration =
    GeneratedStructRegistration<TwoEnumFieldStructEvolution>(
  fieldWritersBySlot: <_TwoEnumFieldStructEvolutionFieldWriter>[
    _writeTwoEnumFieldStructEvolutionField0,
    _writeTwoEnumFieldStructEvolutionField1,
  ],
  compatibleFactory: TwoEnumFieldStructEvolution.new,
  compatibleReadersBySlot: <_TwoEnumFieldStructEvolutionFieldReader>[
    _readTwoEnumFieldStructEvolutionField0,
    _readTwoEnumFieldStructEvolutionField1,
  ],
  type: TwoEnumFieldStructEvolution,
  serializerFactory: _TwoEnumFieldStructEvolutionForySerializer.new,
  evolving: true,
  fields: _twoEnumFieldStructEvolutionForyFieldInfo,
);

final class _TwoEnumFieldStructEvolutionForySerializer
    extends Serializer<TwoEnumFieldStructEvolution> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _TwoEnumFieldStructEvolutionForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoEnumFieldStructEvolutionForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoEnumFieldStructEvolutionForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoEnumFieldStructEvolution value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.writeVarUint32(value.f2.index);
      cursor0.finish();
      return;
    }
    final writers =
        _twoEnumFieldStructEvolutionForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoEnumFieldStructEvolution read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = TwoEnumFieldStructEvolution();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      value.f2 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawTwoEnumFieldStructEvolution0 = slots.valueForSlot(0);
      value.f1 = _readTwoEnumFieldStructEvolutionF1(
          rawTwoEnumFieldStructEvolution0 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStructEvolution0.id)
              : rawTwoEnumFieldStructEvolution0,
          value.f1);
    }
    if (slots.containsSlot(1)) {
      final rawTwoEnumFieldStructEvolution1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _itemForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _ItemFieldWriter = GeneratedStructFieldInfoWriter<Item>;
typedef _ItemFieldReader = GeneratedStructFieldInfoReader<Item>;

void _writeItemField0(
    WriteContext context, GeneratedStructFieldInfo field, Item value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _readItemField0(ReadContext context, Item value, Object? rawValue) {
  value.name = _readItemName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<Item> _itemForyRegistration =
    GeneratedStructRegistration<Item>(
  fieldWritersBySlot: <_ItemFieldWriter>[
    _writeItemField0,
  ],
  compatibleFactory: Item.new,
  compatibleReadersBySlot: <_ItemFieldReader>[
    _readItemField0,
  ],
  type: Item,
  serializerFactory: _ItemForySerializer.new,
  evolving: true,
  fields: _itemForyFieldInfo,
);

final class _ItemForySerializer extends Serializer<Item> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _ItemForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _itemForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _itemForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Item value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.name);
      return;
    }
    final writers = _itemForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Item read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = Item();
    context.reference(value);
    if (slots == null) {
      value.name = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawItem0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _simpleStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f7',
    identifier: 'f7',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f8',
    identifier: 'f8',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'last',
    identifier: 'last',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f4',
    identifier: 'f4',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f6',
    identifier: 'f6',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: Int32,
          typeId: 5,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: double,
          typeId: 20,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f3',
    identifier: 'f3',
    id: null,
    fieldType: GeneratedFieldType(
      type: Item,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f5',
    identifier: 'f5',
    id: null,
    fieldType: GeneratedFieldType(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _SimpleStructFieldWriter = GeneratedStructFieldInfoWriter<SimpleStruct>;
typedef _SimpleStructFieldReader = GeneratedStructFieldInfoReader<SimpleStruct>;

void _writeSimpleStructField0(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _writeSimpleStructField1(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f7);
}

void _writeSimpleStructField2(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f8);
}

void _writeSimpleStructField3(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.last);
}

void _writeSimpleStructField4(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f4);
}

void _writeSimpleStructField5(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f6);
}

void _writeSimpleStructField6(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeSimpleStructField7(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f3);
}

void _writeSimpleStructField8(
    WriteContext context, GeneratedStructFieldInfo field, SimpleStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f5);
}

void _readSimpleStructField0(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f2 = _readSimpleStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

void _readSimpleStructField1(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f7 = _readSimpleStructF7(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f7);
}

void _readSimpleStructField2(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f8 = _readSimpleStructF8(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f8);
}

void _readSimpleStructField3(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.last = _readSimpleStructLast(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.last);
}

void _readSimpleStructField4(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f4 = _readSimpleStructF4(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f4);
}

void _readSimpleStructField5(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f6 = _readSimpleStructF6(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f6);
}

void _readSimpleStructField6(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f1 = _readSimpleStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readSimpleStructField7(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f3 = _readSimpleStructF3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readSimpleStructField8(
    ReadContext context, SimpleStruct value, Object? rawValue) {
  value.f5 = _readSimpleStructF5(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f5);
}

final GeneratedStructRegistration<SimpleStruct> _simpleStructForyRegistration =
    GeneratedStructRegistration<SimpleStruct>(
  fieldWritersBySlot: <_SimpleStructFieldWriter>[
    _writeSimpleStructField0,
    _writeSimpleStructField1,
    _writeSimpleStructField2,
    _writeSimpleStructField3,
    _writeSimpleStructField4,
    _writeSimpleStructField5,
    _writeSimpleStructField6,
    _writeSimpleStructField7,
    _writeSimpleStructField8,
  ],
  compatibleFactory: SimpleStruct.new,
  compatibleReadersBySlot: <_SimpleStructFieldReader>[
    _readSimpleStructField0,
    _readSimpleStructField1,
    _readSimpleStructField2,
    _readSimpleStructField3,
    _readSimpleStructField4,
    _readSimpleStructField5,
    _readSimpleStructField6,
    _readSimpleStructField7,
    _readSimpleStructField8,
  ],
  type: SimpleStruct,
  serializerFactory: _SimpleStructForySerializer.new,
  evolving: true,
  fields: _simpleStructForyFieldInfo,
);

final class _SimpleStructForySerializer extends Serializer<SimpleStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _SimpleStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _simpleStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _simpleStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, SimpleStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 20);
      cursor0.writeVarInt32(value.f2.value);
      cursor0.writeVarInt32(value.f7.value);
      cursor0.writeVarInt32(value.f8.value);
      cursor0.writeVarInt32(value.last.value);
      cursor0.finish();
      context.writeString(value.f4);
      writeGeneratedStructFieldInfoValue(context, fields[5], value.f6);
      writeGeneratedStructFieldInfoValue(context, fields[6], value.f1);
      writeGeneratedStructFieldInfoValue(context, fields[7], value.f3);
      final cursor8 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor8.writeVarUint32(value.f5.index);
      cursor8.finish();
      return;
    }
    final writers = _simpleStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  SimpleStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = SimpleStruct();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
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
          readGeneratedStructFieldInfoValue(context, fields[7], value.f3),
          value.f3);
      final cursor8 = GeneratedReadCursor.start(buffer);
      value.f5 = Color.values[cursor8.readVarUint32()];
      cursor8.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawSimpleStruct0 = slots.valueForSlot(0);
      value.f2 = _readSimpleStructF2(
          rawSimpleStruct0 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct0.id)
              : rawSimpleStruct0,
          value.f2);
    }
    if (slots.containsSlot(1)) {
      final rawSimpleStruct1 = slots.valueForSlot(1);
      value.f7 = _readSimpleStructF7(
          rawSimpleStruct1 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct1.id)
              : rawSimpleStruct1,
          value.f7);
    }
    if (slots.containsSlot(2)) {
      final rawSimpleStruct2 = slots.valueForSlot(2);
      value.f8 = _readSimpleStructF8(
          rawSimpleStruct2 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct2.id)
              : rawSimpleStruct2,
          value.f8);
    }
    if (slots.containsSlot(3)) {
      final rawSimpleStruct3 = slots.valueForSlot(3);
      value.last = _readSimpleStructLast(
          rawSimpleStruct3 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct3.id)
              : rawSimpleStruct3,
          value.last);
    }
    if (slots.containsSlot(4)) {
      final rawSimpleStruct4 = slots.valueForSlot(4);
      value.f4 = _readSimpleStructF4(
          rawSimpleStruct4 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct4.id)
              : rawSimpleStruct4,
          value.f4);
    }
    if (slots.containsSlot(5)) {
      final rawSimpleStruct5 = slots.valueForSlot(5);
      value.f6 = _readSimpleStructF6(
          rawSimpleStruct5 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct5.id)
              : rawSimpleStruct5,
          value.f6);
    }
    if (slots.containsSlot(6)) {
      final rawSimpleStruct6 = slots.valueForSlot(6);
      value.f1 = _readSimpleStructF1(
          rawSimpleStruct6 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct6.id)
              : rawSimpleStruct6,
          value.f1);
    }
    if (slots.containsSlot(7)) {
      final rawSimpleStruct7 = slots.valueForSlot(7);
      value.f3 = _readSimpleStructF3(
          rawSimpleStruct7 is DeferredReadRef
              ? context.getReadRef(rawSimpleStruct7.id)
              : rawSimpleStruct7,
          value.f3);
    }
    if (slots.containsSlot(8)) {
      final rawSimpleStruct8 = slots.valueForSlot(8);
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

const List<GeneratedFieldInfo> _evolvingOverrideStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _EvolvingOverrideStructFieldWriter
    = GeneratedStructFieldInfoWriter<EvolvingOverrideStruct>;
typedef _EvolvingOverrideStructFieldReader
    = GeneratedStructFieldInfoReader<EvolvingOverrideStruct>;

void _writeEvolvingOverrideStructField0(WriteContext context,
    GeneratedStructFieldInfo field, EvolvingOverrideStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _readEvolvingOverrideStructField0(
    ReadContext context, EvolvingOverrideStruct value, Object? rawValue) {
  value.f1 = _readEvolvingOverrideStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<EvolvingOverrideStruct>
    _evolvingOverrideStructForyRegistration =
    GeneratedStructRegistration<EvolvingOverrideStruct>(
  fieldWritersBySlot: <_EvolvingOverrideStructFieldWriter>[
    _writeEvolvingOverrideStructField0,
  ],
  compatibleFactory: EvolvingOverrideStruct.new,
  compatibleReadersBySlot: <_EvolvingOverrideStructFieldReader>[
    _readEvolvingOverrideStructField0,
  ],
  type: EvolvingOverrideStruct,
  serializerFactory: _EvolvingOverrideStructForySerializer.new,
  evolving: true,
  fields: _evolvingOverrideStructForyFieldInfo,
);

final class _EvolvingOverrideStructForySerializer
    extends Serializer<EvolvingOverrideStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _EvolvingOverrideStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _evolvingOverrideStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _evolvingOverrideStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EvolvingOverrideStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.f1);
      return;
    }
    final writers = _evolvingOverrideStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EvolvingOverrideStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = EvolvingOverrideStruct();
    context.reference(value);
    if (slots == null) {
      value.f1 = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawEvolvingOverrideStruct0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _fixedOverrideStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _FixedOverrideStructFieldWriter
    = GeneratedStructFieldInfoWriter<FixedOverrideStruct>;
typedef _FixedOverrideStructFieldReader
    = GeneratedStructFieldInfoReader<FixedOverrideStruct>;

void _writeFixedOverrideStructField0(WriteContext context,
    GeneratedStructFieldInfo field, FixedOverrideStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _readFixedOverrideStructField0(
    ReadContext context, FixedOverrideStruct value, Object? rawValue) {
  value.f1 = _readFixedOverrideStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<FixedOverrideStruct>
    _fixedOverrideStructForyRegistration =
    GeneratedStructRegistration<FixedOverrideStruct>(
  fieldWritersBySlot: <_FixedOverrideStructFieldWriter>[
    _writeFixedOverrideStructField0,
  ],
  compatibleFactory: FixedOverrideStruct.new,
  compatibleReadersBySlot: <_FixedOverrideStructFieldReader>[
    _readFixedOverrideStructField0,
  ],
  type: FixedOverrideStruct,
  serializerFactory: _FixedOverrideStructForySerializer.new,
  evolving: false,
  fields: _fixedOverrideStructForyFieldInfo,
);

final class _FixedOverrideStructForySerializer
    extends Serializer<FixedOverrideStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _FixedOverrideStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _fixedOverrideStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _fixedOverrideStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, FixedOverrideStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.f1);
      return;
    }
    final writers = _fixedOverrideStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  FixedOverrideStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = FixedOverrideStruct();
    context.reference(value);
    if (slots == null) {
      value.f1 = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawFixedOverrideStruct0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _item1ForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f3',
    identifier: 'f3',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f4',
    identifier: 'f4',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f5',
    identifier: 'f5',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f6',
    identifier: 'f6',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _Item1FieldWriter = GeneratedStructFieldInfoWriter<Item1>;
typedef _Item1FieldReader = GeneratedStructFieldInfoReader<Item1>;

void _writeItem1Field0(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeItem1Field1(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _writeItem1Field2(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f3);
}

void _writeItem1Field3(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f4);
}

void _writeItem1Field4(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f5);
}

void _writeItem1Field5(
    WriteContext context, GeneratedStructFieldInfo field, Item1 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f6);
}

void _readItem1Field0(ReadContext context, Item1 value, Object? rawValue) {
  value.f1 = _readItem1F1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readItem1Field1(ReadContext context, Item1 value, Object? rawValue) {
  value.f2 = _readItem1F2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

void _readItem1Field2(ReadContext context, Item1 value, Object? rawValue) {
  value.f3 = _readItem1F3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readItem1Field3(ReadContext context, Item1 value, Object? rawValue) {
  value.f4 = _readItem1F4(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f4);
}

void _readItem1Field4(ReadContext context, Item1 value, Object? rawValue) {
  value.f5 = _readItem1F5(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f5);
}

void _readItem1Field5(ReadContext context, Item1 value, Object? rawValue) {
  value.f6 = _readItem1F6(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f6);
}

final GeneratedStructRegistration<Item1> _item1ForyRegistration =
    GeneratedStructRegistration<Item1>(
  fieldWritersBySlot: <_Item1FieldWriter>[
    _writeItem1Field0,
    _writeItem1Field1,
    _writeItem1Field2,
    _writeItem1Field3,
    _writeItem1Field4,
    _writeItem1Field5,
  ],
  compatibleFactory: Item1.new,
  compatibleReadersBySlot: <_Item1FieldReader>[
    _readItem1Field0,
    _readItem1Field1,
    _readItem1Field2,
    _readItem1Field3,
    _readItem1Field4,
    _readItem1Field5,
  ],
  type: Item1,
  serializerFactory: _Item1ForySerializer.new,
  evolving: true,
  fields: _item1ForyFieldInfo,
);

final class _Item1ForySerializer extends Serializer<Item1> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _Item1ForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _item1ForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _item1ForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Item1 value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
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
    final writers = _item1ForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Item1 read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = Item1();
    context.reference(value);
    if (slots == null) {
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
    if (slots.containsSlot(0)) {
      final rawItem10 = slots.valueForSlot(0);
      value.f1 = _readItem1F1(
          rawItem10 is DeferredReadRef
              ? context.getReadRef(rawItem10.id)
              : rawItem10,
          value.f1);
    }
    if (slots.containsSlot(1)) {
      final rawItem11 = slots.valueForSlot(1);
      value.f2 = _readItem1F2(
          rawItem11 is DeferredReadRef
              ? context.getReadRef(rawItem11.id)
              : rawItem11,
          value.f2);
    }
    if (slots.containsSlot(2)) {
      final rawItem12 = slots.valueForSlot(2);
      value.f3 = _readItem1F3(
          rawItem12 is DeferredReadRef
              ? context.getReadRef(rawItem12.id)
              : rawItem12,
          value.f3);
    }
    if (slots.containsSlot(3)) {
      final rawItem13 = slots.valueForSlot(3);
      value.f4 = _readItem1F4(
          rawItem13 is DeferredReadRef
              ? context.getReadRef(rawItem13.id)
              : rawItem13,
          value.f4);
    }
    if (slots.containsSlot(4)) {
      final rawItem14 = slots.valueForSlot(4);
      value.f5 = _readItem1F5(
          rawItem14 is DeferredReadRef
              ? context.getReadRef(rawItem14.id)
              : rawItem14,
          value.f5);
    }
    if (slots.containsSlot(5)) {
      final rawItem15 = slots.valueForSlot(5);
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

const List<GeneratedFieldInfo> _structWithUnion2ForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'union',
    identifier: 'union',
    id: null,
    fieldType: GeneratedFieldType(
      type: Union2,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _StructWithUnion2FieldWriter
    = GeneratedStructFieldInfoWriter<StructWithUnion2>;
typedef _StructWithUnion2FieldReader
    = GeneratedStructFieldInfoReader<StructWithUnion2>;

void _writeStructWithUnion2Field0(WriteContext context,
    GeneratedStructFieldInfo field, StructWithUnion2 value) {
  writeGeneratedStructFieldInfoValue(context, field, value.union);
}

void _readStructWithUnion2Field0(
    ReadContext context, StructWithUnion2 value, Object? rawValue) {
  value.union = _readStructWithUnion2Union(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.union);
}

final GeneratedStructRegistration<StructWithUnion2>
    _structWithUnion2ForyRegistration =
    GeneratedStructRegistration<StructWithUnion2>(
  fieldWritersBySlot: <_StructWithUnion2FieldWriter>[
    _writeStructWithUnion2Field0,
  ],
  compatibleFactory: StructWithUnion2.new,
  compatibleReadersBySlot: <_StructWithUnion2FieldReader>[
    _readStructWithUnion2Field0,
  ],
  type: StructWithUnion2,
  serializerFactory: _StructWithUnion2ForySerializer.new,
  evolving: true,
  fields: _structWithUnion2ForyFieldInfo,
);

final class _StructWithUnion2ForySerializer
    extends Serializer<StructWithUnion2> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _StructWithUnion2ForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithUnion2ForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithUnion2ForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithUnion2 value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.union);
      return;
    }
    final writers = _structWithUnion2ForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = StructWithUnion2();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.union = _readStructWithUnion2Union(
          readGeneratedStructFieldInfoValue(context, fields[0], value.union),
          value.union);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawStructWithUnion20 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _structWithListForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'items',
    identifier: 'items',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _StructWithListFieldWriter
    = GeneratedStructFieldInfoWriter<StructWithList>;
typedef _StructWithListFieldReader
    = GeneratedStructFieldInfoReader<StructWithList>;

void _writeStructWithListField0(WriteContext context,
    GeneratedStructFieldInfo field, StructWithList value) {
  writeGeneratedStructFieldInfoValue(context, field, value.items);
}

void _readStructWithListField0(
    ReadContext context, StructWithList value, Object? rawValue) {
  value.items = _readStructWithListItems(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.items);
}

final GeneratedStructRegistration<StructWithList>
    _structWithListForyRegistration =
    GeneratedStructRegistration<StructWithList>(
  fieldWritersBySlot: <_StructWithListFieldWriter>[
    _writeStructWithListField0,
  ],
  compatibleFactory: StructWithList.new,
  compatibleReadersBySlot: <_StructWithListFieldReader>[
    _readStructWithListField0,
  ],
  type: StructWithList,
  serializerFactory: _StructWithListForySerializer.new,
  evolving: true,
  fields: _structWithListForyFieldInfo,
);

final class _StructWithListForySerializer extends Serializer<StructWithList> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _StructWithListForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithListForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithListForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithList value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.items);
      return;
    }
    final writers = _structWithListForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithList read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = StructWithList();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.items = readGeneratedDirectListValue<String?>(
          context, fields[0], _readStructWithListItemsElement);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawStructWithList0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _structWithMapForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'data',
    identifier: 'data',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _StructWithMapFieldWriter
    = GeneratedStructFieldInfoWriter<StructWithMap>;
typedef _StructWithMapFieldReader
    = GeneratedStructFieldInfoReader<StructWithMap>;

void _writeStructWithMapField0(
    WriteContext context, GeneratedStructFieldInfo field, StructWithMap value) {
  writeGeneratedStructFieldInfoValue(context, field, value.data);
}

void _readStructWithMapField0(
    ReadContext context, StructWithMap value, Object? rawValue) {
  value.data = _readStructWithMapData(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.data);
}

final GeneratedStructRegistration<StructWithMap>
    _structWithMapForyRegistration = GeneratedStructRegistration<StructWithMap>(
  fieldWritersBySlot: <_StructWithMapFieldWriter>[
    _writeStructWithMapField0,
  ],
  compatibleFactory: StructWithMap.new,
  compatibleReadersBySlot: <_StructWithMapFieldReader>[
    _readStructWithMapField0,
  ],
  type: StructWithMap,
  serializerFactory: _StructWithMapForySerializer.new,
  evolving: true,
  fields: _structWithMapForyFieldInfo,
);

final class _StructWithMapForySerializer extends Serializer<StructWithMap> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _StructWithMapForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithMapForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _structWithMapForyRegistration,
    );
  }

  @override
  void write(WriteContext context, StructWithMap value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.data);
      return;
    }
    final writers = _structWithMapForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  StructWithMap read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = StructWithMap();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.data = readGeneratedDirectMapValue<String?, String?>(context,
          fields[0], _readStructWithMapDataKey, _readStructWithMapDataValue);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawStructWithMap0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _myStructForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'id',
    identifier: 'id',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _MyStructFieldWriter = GeneratedStructFieldInfoWriter<MyStruct>;
typedef _MyStructFieldReader = GeneratedStructFieldInfoReader<MyStruct>;

void _writeMyStructField0(
    WriteContext context, GeneratedStructFieldInfo field, MyStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.id);
}

void _readMyStructField0(
    ReadContext context, MyStruct value, Object? rawValue) {
  value.id = _readMyStructId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

final GeneratedStructRegistration<MyStruct> _myStructForyRegistration =
    GeneratedStructRegistration<MyStruct>(
  fieldWritersBySlot: <_MyStructFieldWriter>[
    _writeMyStructField0,
  ],
  compatibleFactory: MyStruct.new,
  compatibleReadersBySlot: <_MyStructFieldReader>[
    _readMyStructField0,
  ],
  type: MyStruct,
  serializerFactory: _MyStructForySerializer.new,
  evolving: true,
  fields: _myStructForyFieldInfo,
);

final class _MyStructForySerializer extends Serializer<MyStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _MyStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _myStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _myStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MyStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      return;
    }
    final writers = _myStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MyStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = MyStruct();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawMyStruct0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _myWrapperForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'color',
    identifier: 'color',
    id: null,
    fieldType: GeneratedFieldType(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'myExt',
    identifier: 'my_ext',
    id: null,
    fieldType: GeneratedFieldType(
      type: MyExt,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'myStruct',
    identifier: 'my_struct',
    id: null,
    fieldType: GeneratedFieldType(
      type: MyStruct,
      typeId: 28,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _MyWrapperFieldWriter = GeneratedStructFieldInfoWriter<MyWrapper>;
typedef _MyWrapperFieldReader = GeneratedStructFieldInfoReader<MyWrapper>;

void _writeMyWrapperField0(
    WriteContext context, GeneratedStructFieldInfo field, MyWrapper value) {
  writeGeneratedStructFieldInfoValue(context, field, value.color);
}

void _writeMyWrapperField1(
    WriteContext context, GeneratedStructFieldInfo field, MyWrapper value) {
  writeGeneratedStructFieldInfoValue(context, field, value.myExt);
}

void _writeMyWrapperField2(
    WriteContext context, GeneratedStructFieldInfo field, MyWrapper value) {
  writeGeneratedStructFieldInfoValue(context, field, value.myStruct);
}

void _readMyWrapperField0(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.color = _readMyWrapperColor(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.color);
}

void _readMyWrapperField1(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.myExt = _readMyWrapperMyExt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.myExt);
}

void _readMyWrapperField2(
    ReadContext context, MyWrapper value, Object? rawValue) {
  value.myStruct = _readMyWrapperMyStruct(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.myStruct);
}

final GeneratedStructRegistration<MyWrapper> _myWrapperForyRegistration =
    GeneratedStructRegistration<MyWrapper>(
  fieldWritersBySlot: <_MyWrapperFieldWriter>[
    _writeMyWrapperField0,
    _writeMyWrapperField1,
    _writeMyWrapperField2,
  ],
  compatibleFactory: MyWrapper.new,
  compatibleReadersBySlot: <_MyWrapperFieldReader>[
    _readMyWrapperField0,
    _readMyWrapperField1,
    _readMyWrapperField2,
  ],
  type: MyWrapper,
  serializerFactory: _MyWrapperForySerializer.new,
  evolving: true,
  fields: _myWrapperForyFieldInfo,
);

final class _MyWrapperForySerializer extends Serializer<MyWrapper> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _MyWrapperForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _myWrapperForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _myWrapperForyRegistration,
    );
  }

  @override
  void write(WriteContext context, MyWrapper value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarUint32(value.color.index);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(context, fields[1], value.myExt);
      writeGeneratedStructFieldInfoValue(context, fields[2], value.myStruct);
      return;
    }
    final writers = _myWrapperForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  MyWrapper read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = MyWrapper();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.color = Color.values[cursor0.readVarUint32()];
      cursor0.finish();
      value.myExt = _readMyWrapperMyExt(
          readGeneratedStructFieldInfoValue(context, fields[1], value.myExt),
          value.myExt);
      value.myStruct = _readMyWrapperMyStruct(
          readGeneratedStructFieldInfoValue(context, fields[2], value.myStruct),
          value.myStruct);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawMyWrapper0 = slots.valueForSlot(0);
      value.color = _readMyWrapperColor(
          rawMyWrapper0 is DeferredReadRef
              ? context.getReadRef(rawMyWrapper0.id)
              : rawMyWrapper0,
          value.color);
    }
    if (slots.containsSlot(1)) {
      final rawMyWrapper1 = slots.valueForSlot(1);
      value.myExt = _readMyWrapperMyExt(
          rawMyWrapper1 is DeferredReadRef
              ? context.getReadRef(rawMyWrapper1.id)
              : rawMyWrapper1,
          value.myExt);
    }
    if (slots.containsSlot(2)) {
      final rawMyWrapper2 = slots.valueForSlot(2);
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

const List<GeneratedFieldInfo> _emptyWrapperForyFieldInfo =
    <GeneratedFieldInfo>[];

typedef _EmptyWrapperFieldWriter = GeneratedStructFieldInfoWriter<EmptyWrapper>;
typedef _EmptyWrapperFieldReader = GeneratedStructFieldInfoReader<EmptyWrapper>;

final GeneratedStructRegistration<EmptyWrapper> _emptyWrapperForyRegistration =
    GeneratedStructRegistration<EmptyWrapper>(
  fieldWritersBySlot: <_EmptyWrapperFieldWriter>[],
  compatibleFactory: EmptyWrapper.new,
  compatibleReadersBySlot: <_EmptyWrapperFieldReader>[],
  type: EmptyWrapper,
  serializerFactory: _EmptyWrapperForySerializer.new,
  evolving: true,
  fields: _emptyWrapperForyFieldInfo,
);

final class _EmptyWrapperForySerializer extends Serializer<EmptyWrapper> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _EmptyWrapperForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _emptyWrapperForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _emptyWrapperForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EmptyWrapper value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      return;
    }
    final writers = _emptyWrapperForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EmptyWrapper read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = EmptyWrapper();
    context.reference(value);
    if (slots == null) {
      return value;
    }
    return value;
  }
}

const List<GeneratedFieldInfo> _versionCheckStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f3',
    identifier: 'f3',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _VersionCheckStructFieldWriter
    = GeneratedStructFieldInfoWriter<VersionCheckStruct>;
typedef _VersionCheckStructFieldReader
    = GeneratedStructFieldInfoReader<VersionCheckStruct>;

void _writeVersionCheckStructField0(WriteContext context,
    GeneratedStructFieldInfo field, VersionCheckStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f3);
}

void _writeVersionCheckStructField1(WriteContext context,
    GeneratedStructFieldInfo field, VersionCheckStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeVersionCheckStructField2(WriteContext context,
    GeneratedStructFieldInfo field, VersionCheckStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _readVersionCheckStructField0(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f3 = _readVersionCheckStructF3(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f3);
}

void _readVersionCheckStructField1(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f1 = _readVersionCheckStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readVersionCheckStructField2(
    ReadContext context, VersionCheckStruct value, Object? rawValue) {
  value.f2 = _readVersionCheckStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<VersionCheckStruct>
    _versionCheckStructForyRegistration =
    GeneratedStructRegistration<VersionCheckStruct>(
  fieldWritersBySlot: <_VersionCheckStructFieldWriter>[
    _writeVersionCheckStructField0,
    _writeVersionCheckStructField1,
    _writeVersionCheckStructField2,
  ],
  compatibleFactory: VersionCheckStruct.new,
  compatibleReadersBySlot: <_VersionCheckStructFieldReader>[
    _readVersionCheckStructField0,
    _readVersionCheckStructField1,
    _readVersionCheckStructField2,
  ],
  type: VersionCheckStruct,
  serializerFactory: _VersionCheckStructForySerializer.new,
  evolving: true,
  fields: _versionCheckStructForyFieldInfo,
);

final class _VersionCheckStructForySerializer
    extends Serializer<VersionCheckStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _VersionCheckStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _versionCheckStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _versionCheckStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, VersionCheckStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 13);
      cursor0.writeFloat64(value.f3);
      cursor0.writeVarInt32(value.f1);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(context, fields[2], value.f2);
      return;
    }
    final writers = _versionCheckStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  VersionCheckStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = VersionCheckStruct();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f3 = cursor0.readFloat64();
      value.f1 = cursor0.readVarInt32();
      cursor0.finish();
      value.f2 = _readVersionCheckStructF2(
          readGeneratedStructFieldInfoValue(context, fields[2], value.f2),
          value.f2);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawVersionCheckStruct0 = slots.valueForSlot(0);
      value.f3 = _readVersionCheckStructF3(
          rawVersionCheckStruct0 is DeferredReadRef
              ? context.getReadRef(rawVersionCheckStruct0.id)
              : rawVersionCheckStruct0,
          value.f3);
    }
    if (slots.containsSlot(1)) {
      final rawVersionCheckStruct1 = slots.valueForSlot(1);
      value.f1 = _readVersionCheckStructF1(
          rawVersionCheckStruct1 is DeferredReadRef
              ? context.getReadRef(rawVersionCheckStruct1.id)
              : rawVersionCheckStruct1,
          value.f1);
    }
    if (slots.containsSlot(2)) {
      final rawVersionCheckStruct2 = slots.valueForSlot(2);
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

const List<GeneratedFieldInfo> _dogForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'age',
    identifier: 'age',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _DogFieldWriter = GeneratedStructFieldInfoWriter<Dog>;
typedef _DogFieldReader = GeneratedStructFieldInfoReader<Dog>;

void _writeDogField0(
    WriteContext context, GeneratedStructFieldInfo field, Dog value) {
  writeGeneratedStructFieldInfoValue(context, field, value.age);
}

void _writeDogField1(
    WriteContext context, GeneratedStructFieldInfo field, Dog value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _readDogField0(ReadContext context, Dog value, Object? rawValue) {
  value.age = _readDogAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readDogField1(ReadContext context, Dog value, Object? rawValue) {
  value.name = _readDogName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<Dog> _dogForyRegistration =
    GeneratedStructRegistration<Dog>(
  fieldWritersBySlot: <_DogFieldWriter>[
    _writeDogField0,
    _writeDogField1,
  ],
  compatibleFactory: Dog.new,
  compatibleReadersBySlot: <_DogFieldReader>[
    _readDogField0,
    _readDogField1,
  ],
  type: Dog,
  serializerFactory: _DogForySerializer.new,
  evolving: true,
  fields: _dogForyFieldInfo,
);

final class _DogForySerializer extends Serializer<Dog> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _DogForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _dogForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _dogForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Dog value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.age);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(context, fields[1], value.name);
      return;
    }
    final writers = _dogForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Dog read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = Dog();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.age = cursor0.readVarInt32();
      cursor0.finish();
      value.name = _readDogName(
          readGeneratedStructFieldInfoValue(context, fields[1], value.name),
          value.name);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawDog0 = slots.valueForSlot(0);
      value.age = _readDogAge(
          rawDog0 is DeferredReadRef ? context.getReadRef(rawDog0.id) : rawDog0,
          value.age);
    }
    if (slots.containsSlot(1)) {
      final rawDog1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _catForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'age',
    identifier: 'age',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'lives',
    identifier: 'lives',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _CatFieldWriter = GeneratedStructFieldInfoWriter<Cat>;
typedef _CatFieldReader = GeneratedStructFieldInfoReader<Cat>;

void _writeCatField0(
    WriteContext context, GeneratedStructFieldInfo field, Cat value) {
  writeGeneratedStructFieldInfoValue(context, field, value.age);
}

void _writeCatField1(
    WriteContext context, GeneratedStructFieldInfo field, Cat value) {
  writeGeneratedStructFieldInfoValue(context, field, value.lives);
}

void _readCatField0(ReadContext context, Cat value, Object? rawValue) {
  value.age = _readCatAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readCatField1(ReadContext context, Cat value, Object? rawValue) {
  value.lives = _readCatLives(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.lives);
}

final GeneratedStructRegistration<Cat> _catForyRegistration =
    GeneratedStructRegistration<Cat>(
  fieldWritersBySlot: <_CatFieldWriter>[
    _writeCatField0,
    _writeCatField1,
  ],
  compatibleFactory: Cat.new,
  compatibleReadersBySlot: <_CatFieldReader>[
    _readCatField0,
    _readCatField1,
  ],
  type: Cat,
  serializerFactory: _CatForySerializer.new,
  evolving: true,
  fields: _catForyFieldInfo,
);

final class _CatForySerializer extends Serializer<Cat> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _CatForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _catForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _catForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Cat value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarInt32(value.age);
      cursor0.writeVarInt32(value.lives);
      cursor0.finish();
      return;
    }
    final writers = _catForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Cat read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = Cat();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.age = cursor0.readVarInt32();
      value.lives = cursor0.readVarInt32();
      cursor0.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawCat0 = slots.valueForSlot(0);
      value.age = _readCatAge(
          rawCat0 is DeferredReadRef ? context.getReadRef(rawCat0.id) : rawCat0,
          value.age);
    }
    if (slots.containsSlot(1)) {
      final rawCat1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _animalListHolderForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'animals',
    identifier: 'animals',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: Animal,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: true,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _AnimalListHolderFieldWriter
    = GeneratedStructFieldInfoWriter<AnimalListHolder>;
typedef _AnimalListHolderFieldReader
    = GeneratedStructFieldInfoReader<AnimalListHolder>;

void _writeAnimalListHolderField0(WriteContext context,
    GeneratedStructFieldInfo field, AnimalListHolder value) {
  writeGeneratedStructFieldInfoValue(context, field, value.animals);
}

void _readAnimalListHolderField0(
    ReadContext context, AnimalListHolder value, Object? rawValue) {
  value.animals = _readAnimalListHolderAnimals(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.animals);
}

final GeneratedStructRegistration<AnimalListHolder>
    _animalListHolderForyRegistration =
    GeneratedStructRegistration<AnimalListHolder>(
  fieldWritersBySlot: <_AnimalListHolderFieldWriter>[
    _writeAnimalListHolderField0,
  ],
  compatibleFactory: AnimalListHolder.new,
  compatibleReadersBySlot: <_AnimalListHolderFieldReader>[
    _readAnimalListHolderField0,
  ],
  type: AnimalListHolder,
  serializerFactory: _AnimalListHolderForySerializer.new,
  evolving: true,
  fields: _animalListHolderForyFieldInfo,
);

final class _AnimalListHolderForySerializer
    extends Serializer<AnimalListHolder> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _AnimalListHolderForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _animalListHolderForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _animalListHolderForyRegistration,
    );
  }

  @override
  void write(WriteContext context, AnimalListHolder value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.animals);
      return;
    }
    final writers = _animalListHolderForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  AnimalListHolder read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = AnimalListHolder();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.animals = readGeneratedDirectListValue<Animal>(
          context, fields[0], _readAnimalListHolderAnimalsElement);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawAnimalListHolder0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _animalMapHolderForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'animalMap',
    identifier: 'animal_map',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: Animal,
          typeId: 28,
          nullable: true,
          ref: false,
          dynamic: true,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _AnimalMapHolderFieldWriter
    = GeneratedStructFieldInfoWriter<AnimalMapHolder>;
typedef _AnimalMapHolderFieldReader
    = GeneratedStructFieldInfoReader<AnimalMapHolder>;

void _writeAnimalMapHolderField0(WriteContext context,
    GeneratedStructFieldInfo field, AnimalMapHolder value) {
  writeGeneratedStructFieldInfoValue(context, field, value.animalMap);
}

void _readAnimalMapHolderField0(
    ReadContext context, AnimalMapHolder value, Object? rawValue) {
  value.animalMap = _readAnimalMapHolderAnimalMap(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.animalMap);
}

final GeneratedStructRegistration<AnimalMapHolder>
    _animalMapHolderForyRegistration =
    GeneratedStructRegistration<AnimalMapHolder>(
  fieldWritersBySlot: <_AnimalMapHolderFieldWriter>[
    _writeAnimalMapHolderField0,
  ],
  compatibleFactory: AnimalMapHolder.new,
  compatibleReadersBySlot: <_AnimalMapHolderFieldReader>[
    _readAnimalMapHolderField0,
  ],
  type: AnimalMapHolder,
  serializerFactory: _AnimalMapHolderForySerializer.new,
  evolving: true,
  fields: _animalMapHolderForyFieldInfo,
);

final class _AnimalMapHolderForySerializer extends Serializer<AnimalMapHolder> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _AnimalMapHolderForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _animalMapHolderForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _animalMapHolderForyRegistration,
    );
  }

  @override
  void write(WriteContext context, AnimalMapHolder value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.animalMap);
      return;
    }
    final writers = _animalMapHolderForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  AnimalMapHolder read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = AnimalMapHolder();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.animalMap = readGeneratedDirectMapValue<String, Animal>(
          context,
          fields[0],
          _readAnimalMapHolderAnimalMapKey,
          _readAnimalMapHolderAnimalMapValue);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawAnimalMapHolder0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _emptyStructForyFieldInfo =
    <GeneratedFieldInfo>[];

typedef _EmptyStructFieldWriter = GeneratedStructFieldInfoWriter<EmptyStruct>;
typedef _EmptyStructFieldReader = GeneratedStructFieldInfoReader<EmptyStruct>;

final GeneratedStructRegistration<EmptyStruct> _emptyStructForyRegistration =
    GeneratedStructRegistration<EmptyStruct>(
  fieldWritersBySlot: <_EmptyStructFieldWriter>[],
  compatibleFactory: EmptyStruct.new,
  compatibleReadersBySlot: <_EmptyStructFieldReader>[],
  type: EmptyStruct,
  serializerFactory: _EmptyStructForySerializer.new,
  evolving: true,
  fields: _emptyStructForyFieldInfo,
);

final class _EmptyStructForySerializer extends Serializer<EmptyStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _EmptyStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _emptyStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _emptyStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EmptyStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      return;
    }
    final writers = _emptyStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EmptyStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = EmptyStruct();
    context.reference(value);
    if (slots == null) {
      return value;
    }
    return value;
  }
}

const List<GeneratedFieldInfo> _oneStringFieldStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _OneStringFieldStructFieldWriter
    = GeneratedStructFieldInfoWriter<OneStringFieldStruct>;
typedef _OneStringFieldStructFieldReader
    = GeneratedStructFieldInfoReader<OneStringFieldStruct>;

void _writeOneStringFieldStructField0(WriteContext context,
    GeneratedStructFieldInfo field, OneStringFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _readOneStringFieldStructField0(
    ReadContext context, OneStringFieldStruct value, Object? rawValue) {
  value.f1 = _readOneStringFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<OneStringFieldStruct>
    _oneStringFieldStructForyRegistration =
    GeneratedStructRegistration<OneStringFieldStruct>(
  fieldWritersBySlot: <_OneStringFieldStructFieldWriter>[
    _writeOneStringFieldStructField0,
  ],
  compatibleFactory: OneStringFieldStruct.new,
  compatibleReadersBySlot: <_OneStringFieldStructFieldReader>[
    _readOneStringFieldStructField0,
  ],
  type: OneStringFieldStruct,
  serializerFactory: _OneStringFieldStructForySerializer.new,
  evolving: true,
  fields: _oneStringFieldStructForyFieldInfo,
);

final class _OneStringFieldStructForySerializer
    extends Serializer<OneStringFieldStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _OneStringFieldStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _oneStringFieldStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _oneStringFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, OneStringFieldStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.f1);
      return;
    }
    final writers = _oneStringFieldStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  OneStringFieldStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = OneStringFieldStruct();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.f1 = _readOneStringFieldStructF1(
          readGeneratedStructFieldInfoValue(context, fields[0], value.f1),
          value.f1);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawOneStringFieldStruct0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _twoStringFieldStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _TwoStringFieldStructFieldWriter
    = GeneratedStructFieldInfoWriter<TwoStringFieldStruct>;
typedef _TwoStringFieldStructFieldReader
    = GeneratedStructFieldInfoReader<TwoStringFieldStruct>;

void _writeTwoStringFieldStructField0(WriteContext context,
    GeneratedStructFieldInfo field, TwoStringFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeTwoStringFieldStructField1(WriteContext context,
    GeneratedStructFieldInfo field, TwoStringFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _readTwoStringFieldStructField0(
    ReadContext context, TwoStringFieldStruct value, Object? rawValue) {
  value.f1 = _readTwoStringFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoStringFieldStructField1(
    ReadContext context, TwoStringFieldStruct value, Object? rawValue) {
  value.f2 = _readTwoStringFieldStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoStringFieldStruct>
    _twoStringFieldStructForyRegistration =
    GeneratedStructRegistration<TwoStringFieldStruct>(
  fieldWritersBySlot: <_TwoStringFieldStructFieldWriter>[
    _writeTwoStringFieldStructField0,
    _writeTwoStringFieldStructField1,
  ],
  compatibleFactory: TwoStringFieldStruct.new,
  compatibleReadersBySlot: <_TwoStringFieldStructFieldReader>[
    _readTwoStringFieldStructField0,
    _readTwoStringFieldStructField1,
  ],
  type: TwoStringFieldStruct,
  serializerFactory: _TwoStringFieldStructForySerializer.new,
  evolving: true,
  fields: _twoStringFieldStructForyFieldInfo,
);

final class _TwoStringFieldStructForySerializer
    extends Serializer<TwoStringFieldStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _TwoStringFieldStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoStringFieldStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoStringFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoStringFieldStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.f1);
      context.writeString(value.f2);
      return;
    }
    final writers = _twoStringFieldStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoStringFieldStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = TwoStringFieldStruct();
    context.reference(value);
    if (slots == null) {
      value.f1 = context.readString();
      value.f2 = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawTwoStringFieldStruct0 = slots.valueForSlot(0);
      value.f1 = _readTwoStringFieldStructF1(
          rawTwoStringFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawTwoStringFieldStruct0.id)
              : rawTwoStringFieldStruct0,
          value.f1);
    }
    if (slots.containsSlot(1)) {
      final rawTwoStringFieldStruct1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _oneEnumFieldStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _OneEnumFieldStructFieldWriter
    = GeneratedStructFieldInfoWriter<OneEnumFieldStruct>;
typedef _OneEnumFieldStructFieldReader
    = GeneratedStructFieldInfoReader<OneEnumFieldStruct>;

void _writeOneEnumFieldStructField0(WriteContext context,
    GeneratedStructFieldInfo field, OneEnumFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _readOneEnumFieldStructField0(
    ReadContext context, OneEnumFieldStruct value, Object? rawValue) {
  value.f1 = _readOneEnumFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

final GeneratedStructRegistration<OneEnumFieldStruct>
    _oneEnumFieldStructForyRegistration =
    GeneratedStructRegistration<OneEnumFieldStruct>(
  fieldWritersBySlot: <_OneEnumFieldStructFieldWriter>[
    _writeOneEnumFieldStructField0,
  ],
  compatibleFactory: OneEnumFieldStruct.new,
  compatibleReadersBySlot: <_OneEnumFieldStructFieldReader>[
    _readOneEnumFieldStructField0,
  ],
  type: OneEnumFieldStruct,
  serializerFactory: _OneEnumFieldStructForySerializer.new,
  evolving: true,
  fields: _oneEnumFieldStructForyFieldInfo,
);

final class _OneEnumFieldStructForySerializer
    extends Serializer<OneEnumFieldStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _OneEnumFieldStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _oneEnumFieldStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _oneEnumFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, OneEnumFieldStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.finish();
      return;
    }
    final writers = _oneEnumFieldStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  OneEnumFieldStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = OneEnumFieldStruct();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawOneEnumFieldStruct0 = slots.valueForSlot(0);
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

const List<GeneratedFieldInfo> _twoEnumFieldStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'f1',
    identifier: 'f1',
    id: null,
    fieldType: GeneratedFieldType(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'f2',
    identifier: 'f2',
    id: null,
    fieldType: GeneratedFieldType(
      type: TestEnum,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _TwoEnumFieldStructFieldWriter
    = GeneratedStructFieldInfoWriter<TwoEnumFieldStruct>;
typedef _TwoEnumFieldStructFieldReader
    = GeneratedStructFieldInfoReader<TwoEnumFieldStruct>;

void _writeTwoEnumFieldStructField0(WriteContext context,
    GeneratedStructFieldInfo field, TwoEnumFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f1);
}

void _writeTwoEnumFieldStructField1(WriteContext context,
    GeneratedStructFieldInfo field, TwoEnumFieldStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.f2);
}

void _readTwoEnumFieldStructField0(
    ReadContext context, TwoEnumFieldStruct value, Object? rawValue) {
  value.f1 = _readTwoEnumFieldStructF1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f1);
}

void _readTwoEnumFieldStructField1(
    ReadContext context, TwoEnumFieldStruct value, Object? rawValue) {
  value.f2 = _readTwoEnumFieldStructF2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.f2);
}

final GeneratedStructRegistration<TwoEnumFieldStruct>
    _twoEnumFieldStructForyRegistration =
    GeneratedStructRegistration<TwoEnumFieldStruct>(
  fieldWritersBySlot: <_TwoEnumFieldStructFieldWriter>[
    _writeTwoEnumFieldStructField0,
    _writeTwoEnumFieldStructField1,
  ],
  compatibleFactory: TwoEnumFieldStruct.new,
  compatibleReadersBySlot: <_TwoEnumFieldStructFieldReader>[
    _readTwoEnumFieldStructField0,
    _readTwoEnumFieldStructField1,
  ],
  type: TwoEnumFieldStruct,
  serializerFactory: _TwoEnumFieldStructForySerializer.new,
  evolving: true,
  fields: _twoEnumFieldStructForyFieldInfo,
);

final class _TwoEnumFieldStructForySerializer
    extends Serializer<TwoEnumFieldStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _TwoEnumFieldStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoEnumFieldStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _twoEnumFieldStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, TwoEnumFieldStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeVarUint32(value.f1.index);
      cursor0.writeVarUint32(value.f2.index);
      cursor0.finish();
      return;
    }
    final writers = _twoEnumFieldStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  TwoEnumFieldStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = TwoEnumFieldStruct();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.f1 = TestEnum.values[cursor0.readVarUint32()];
      value.f2 = TestEnum.values[cursor0.readVarUint32()];
      cursor0.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawTwoEnumFieldStruct0 = slots.valueForSlot(0);
      value.f1 = _readTwoEnumFieldStructF1(
          rawTwoEnumFieldStruct0 is DeferredReadRef
              ? context.getReadRef(rawTwoEnumFieldStruct0.id)
              : rawTwoEnumFieldStruct0,
          value.f1);
    }
    if (slots.containsSlot(1)) {
      final rawTwoEnumFieldStruct1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo>
    _nullableComprehensiveSchemaConsistentForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'doubleField',
    identifier: 'double_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'floatField',
    identifier: 'float_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'shortField',
    identifier: 'short_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int16,
      typeId: 3,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'byteField',
    identifier: 'byte_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int8,
      typeId: 2,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boolField',
    identifier: 'bool_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'longField',
    identifier: 'long_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'intField',
    identifier: 'int_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableDouble',
    identifier: 'nullable_double',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableFloat',
    identifier: 'nullable_float',
    id: null,
    fieldType: GeneratedFieldType(
      type: Float32,
      typeId: 19,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableBool',
    identifier: 'nullable_bool',
    id: null,
    fieldType: GeneratedFieldType(
      type: bool,
      typeId: 1,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableLong',
    identifier: 'nullable_long',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 7,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableInt',
    identifier: 'nullable_int',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableString',
    identifier: 'nullable_string',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'stringField',
    identifier: 'string_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'listField',
    identifier: 'list_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableList',
    identifier: 'nullable_list',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableSet',
    identifier: 'nullable_set',
    id: null,
    fieldType: GeneratedFieldType(
      type: Set,
      typeId: 23,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'setField',
    identifier: 'set_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Set,
      typeId: 23,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'mapField',
    identifier: 'map_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableMap',
    identifier: 'nullable_map',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _NullableComprehensiveSchemaConsistentFieldWriter
    = GeneratedStructFieldInfoWriter<NullableComprehensiveSchemaConsistent>;
typedef _NullableComprehensiveSchemaConsistentFieldReader
    = GeneratedStructFieldInfoReader<NullableComprehensiveSchemaConsistent>;

void _writeNullableComprehensiveSchemaConsistentField0(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.doubleField);
}

void _writeNullableComprehensiveSchemaConsistentField1(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.floatField);
}

void _writeNullableComprehensiveSchemaConsistentField2(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.shortField);
}

void _writeNullableComprehensiveSchemaConsistentField3(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.byteField);
}

void _writeNullableComprehensiveSchemaConsistentField4(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boolField);
}

void _writeNullableComprehensiveSchemaConsistentField5(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.longField);
}

void _writeNullableComprehensiveSchemaConsistentField6(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.intField);
}

void _writeNullableComprehensiveSchemaConsistentField7(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableDouble);
}

void _writeNullableComprehensiveSchemaConsistentField8(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableFloat);
}

void _writeNullableComprehensiveSchemaConsistentField9(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableBool);
}

void _writeNullableComprehensiveSchemaConsistentField10(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableLong);
}

void _writeNullableComprehensiveSchemaConsistentField11(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableInt);
}

void _writeNullableComprehensiveSchemaConsistentField12(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableString);
}

void _writeNullableComprehensiveSchemaConsistentField13(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.stringField);
}

void _writeNullableComprehensiveSchemaConsistentField14(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.listField);
}

void _writeNullableComprehensiveSchemaConsistentField15(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableList);
}

void _writeNullableComprehensiveSchemaConsistentField16(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableSet);
}

void _writeNullableComprehensiveSchemaConsistentField17(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.setField);
}

void _writeNullableComprehensiveSchemaConsistentField18(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.mapField);
}

void _writeNullableComprehensiveSchemaConsistentField19(
    WriteContext context,
    GeneratedStructFieldInfo field,
    NullableComprehensiveSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableMap);
}

void _readNullableComprehensiveSchemaConsistentField0(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.doubleField);
}

void _readNullableComprehensiveSchemaConsistentField1(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.floatField);
}

void _readNullableComprehensiveSchemaConsistentField2(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.shortField);
}

void _readNullableComprehensiveSchemaConsistentField3(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.byteField);
}

void _readNullableComprehensiveSchemaConsistentField4(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boolField);
}

void _readNullableComprehensiveSchemaConsistentField5(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.longField = _readNullableComprehensiveSchemaConsistentLongField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.longField);
}

void _readNullableComprehensiveSchemaConsistentField6(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.intField = _readNullableComprehensiveSchemaConsistentIntField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.intField);
}

void _readNullableComprehensiveSchemaConsistentField7(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableDouble =
      _readNullableComprehensiveSchemaConsistentNullableDouble(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.nullableDouble);
}

void _readNullableComprehensiveSchemaConsistentField8(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableFloat = _readNullableComprehensiveSchemaConsistentNullableFloat(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableFloat);
}

void _readNullableComprehensiveSchemaConsistentField9(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableBool = _readNullableComprehensiveSchemaConsistentNullableBool(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableBool);
}

void _readNullableComprehensiveSchemaConsistentField10(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableLong = _readNullableComprehensiveSchemaConsistentNullableLong(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableLong);
}

void _readNullableComprehensiveSchemaConsistentField11(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableInt);
}

void _readNullableComprehensiveSchemaConsistentField12(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableString =
      _readNullableComprehensiveSchemaConsistentNullableString(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.nullableString);
}

void _readNullableComprehensiveSchemaConsistentField13(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.stringField);
}

void _readNullableComprehensiveSchemaConsistentField14(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.listField = _readNullableComprehensiveSchemaConsistentListField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.listField);
}

void _readNullableComprehensiveSchemaConsistentField15(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableList = _readNullableComprehensiveSchemaConsistentNullableList(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableList);
}

void _readNullableComprehensiveSchemaConsistentField16(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableSet);
}

void _readNullableComprehensiveSchemaConsistentField17(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.setField = _readNullableComprehensiveSchemaConsistentSetField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.setField);
}

void _readNullableComprehensiveSchemaConsistentField18(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.mapField);
}

void _readNullableComprehensiveSchemaConsistentField19(ReadContext context,
    NullableComprehensiveSchemaConsistent value, Object? rawValue) {
  value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableMap);
}

final GeneratedStructRegistration<NullableComprehensiveSchemaConsistent>
    _nullableComprehensiveSchemaConsistentForyRegistration =
    GeneratedStructRegistration<NullableComprehensiveSchemaConsistent>(
  fieldWritersBySlot: <_NullableComprehensiveSchemaConsistentFieldWriter>[
    _writeNullableComprehensiveSchemaConsistentField0,
    _writeNullableComprehensiveSchemaConsistentField1,
    _writeNullableComprehensiveSchemaConsistentField2,
    _writeNullableComprehensiveSchemaConsistentField3,
    _writeNullableComprehensiveSchemaConsistentField4,
    _writeNullableComprehensiveSchemaConsistentField5,
    _writeNullableComprehensiveSchemaConsistentField6,
    _writeNullableComprehensiveSchemaConsistentField7,
    _writeNullableComprehensiveSchemaConsistentField8,
    _writeNullableComprehensiveSchemaConsistentField9,
    _writeNullableComprehensiveSchemaConsistentField10,
    _writeNullableComprehensiveSchemaConsistentField11,
    _writeNullableComprehensiveSchemaConsistentField12,
    _writeNullableComprehensiveSchemaConsistentField13,
    _writeNullableComprehensiveSchemaConsistentField14,
    _writeNullableComprehensiveSchemaConsistentField15,
    _writeNullableComprehensiveSchemaConsistentField16,
    _writeNullableComprehensiveSchemaConsistentField17,
    _writeNullableComprehensiveSchemaConsistentField18,
    _writeNullableComprehensiveSchemaConsistentField19,
  ],
  compatibleFactory: NullableComprehensiveSchemaConsistent.new,
  compatibleReadersBySlot: <_NullableComprehensiveSchemaConsistentFieldReader>[
    _readNullableComprehensiveSchemaConsistentField0,
    _readNullableComprehensiveSchemaConsistentField1,
    _readNullableComprehensiveSchemaConsistentField2,
    _readNullableComprehensiveSchemaConsistentField3,
    _readNullableComprehensiveSchemaConsistentField4,
    _readNullableComprehensiveSchemaConsistentField5,
    _readNullableComprehensiveSchemaConsistentField6,
    _readNullableComprehensiveSchemaConsistentField7,
    _readNullableComprehensiveSchemaConsistentField8,
    _readNullableComprehensiveSchemaConsistentField9,
    _readNullableComprehensiveSchemaConsistentField10,
    _readNullableComprehensiveSchemaConsistentField11,
    _readNullableComprehensiveSchemaConsistentField12,
    _readNullableComprehensiveSchemaConsistentField13,
    _readNullableComprehensiveSchemaConsistentField14,
    _readNullableComprehensiveSchemaConsistentField15,
    _readNullableComprehensiveSchemaConsistentField16,
    _readNullableComprehensiveSchemaConsistentField17,
    _readNullableComprehensiveSchemaConsistentField18,
    _readNullableComprehensiveSchemaConsistentField19,
  ],
  type: NullableComprehensiveSchemaConsistent,
  serializerFactory: _NullableComprehensiveSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _nullableComprehensiveSchemaConsistentForyFieldInfo,
);

final class _NullableComprehensiveSchemaConsistentForySerializer
    extends Serializer<NullableComprehensiveSchemaConsistent> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _NullableComprehensiveSchemaConsistentForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _nullableComprehensiveSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _nullableComprehensiveSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(
      WriteContext context, NullableComprehensiveSchemaConsistent value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 31);
      cursor0.writeFloat64(value.doubleField);
      cursor0.writeFloat32(value.floatField.value);
      cursor0.writeInt16(value.shortField.value);
      cursor0.writeByte(value.byteField.value);
      cursor0.writeBool(value.boolField);
      cursor0.writeVarInt64(value.longField);
      cursor0.writeVarInt32(value.intField.value);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(
          context, fields[7], value.nullableDouble);
      writeGeneratedStructFieldInfoValue(
          context, fields[8], value.nullableFloat);
      writeGeneratedStructFieldInfoValue(
          context, fields[9], value.nullableBool);
      writeGeneratedStructFieldInfoValue(
          context, fields[10], value.nullableLong);
      writeGeneratedStructFieldInfoValue(
          context, fields[11], value.nullableInt);
      writeGeneratedStructFieldInfoValue(
          context, fields[12], value.nullableString);
      context.writeString(value.stringField);
      writeGeneratedStructFieldInfoValue(context, fields[14], value.listField);
      writeGeneratedStructFieldInfoValue(
          context, fields[15], value.nullableList);
      writeGeneratedStructFieldInfoValue(
          context, fields[16], value.nullableSet);
      writeGeneratedStructFieldInfoValue(context, fields[17], value.setField);
      writeGeneratedStructFieldInfoValue(context, fields[18], value.mapField);
      writeGeneratedStructFieldInfoValue(
          context, fields[19], value.nullableMap);
      return;
    }
    final writers = _nullableComprehensiveSchemaConsistentForyRegistration
        .fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  NullableComprehensiveSchemaConsistent read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = NullableComprehensiveSchemaConsistent();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
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
              readGeneratedStructFieldInfoValue(
                  context, fields[7], value.nullableDouble),
              value.nullableDouble);
      value.nullableFloat =
          _readNullableComprehensiveSchemaConsistentNullableFloat(
              readGeneratedStructFieldInfoValue(
                  context, fields[8], value.nullableFloat),
              value.nullableFloat);
      value.nullableBool =
          _readNullableComprehensiveSchemaConsistentNullableBool(
              readGeneratedStructFieldInfoValue(
                  context, fields[9], value.nullableBool),
              value.nullableBool);
      value.nullableLong =
          _readNullableComprehensiveSchemaConsistentNullableLong(
              readGeneratedStructFieldInfoValue(
                  context, fields[10], value.nullableLong),
              value.nullableLong);
      value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
          readGeneratedStructFieldInfoValue(
              context, fields[11], value.nullableInt),
          value.nullableInt);
      value.nullableString =
          _readNullableComprehensiveSchemaConsistentNullableString(
              readGeneratedStructFieldInfoValue(
                  context, fields[12], value.nullableString),
              value.nullableString);
      value.stringField = context.readString();
      value.listField = readGeneratedDirectListValue<String>(
          context,
          fields[14],
          _readNullableComprehensiveSchemaConsistentListFieldElement);
      value.nullableList =
          _readNullableComprehensiveSchemaConsistentNullableList(
              readGeneratedStructFieldInfoValue(
                  context, fields[15], value.nullableList),
              value.nullableList);
      value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
          readGeneratedStructFieldInfoValue(
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
          readGeneratedStructFieldInfoValue(
              context, fields[19], value.nullableMap),
          value.nullableMap);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawNullableComprehensiveSchemaConsistent0 = slots.valueForSlot(0);
      value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
          rawNullableComprehensiveSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent0.id)
              : rawNullableComprehensiveSchemaConsistent0,
          value.doubleField);
    }
    if (slots.containsSlot(1)) {
      final rawNullableComprehensiveSchemaConsistent1 = slots.valueForSlot(1);
      value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
          rawNullableComprehensiveSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent1.id)
              : rawNullableComprehensiveSchemaConsistent1,
          value.floatField);
    }
    if (slots.containsSlot(2)) {
      final rawNullableComprehensiveSchemaConsistent2 = slots.valueForSlot(2);
      value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
          rawNullableComprehensiveSchemaConsistent2 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent2.id)
              : rawNullableComprehensiveSchemaConsistent2,
          value.shortField);
    }
    if (slots.containsSlot(3)) {
      final rawNullableComprehensiveSchemaConsistent3 = slots.valueForSlot(3);
      value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
          rawNullableComprehensiveSchemaConsistent3 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent3.id)
              : rawNullableComprehensiveSchemaConsistent3,
          value.byteField);
    }
    if (slots.containsSlot(4)) {
      final rawNullableComprehensiveSchemaConsistent4 = slots.valueForSlot(4);
      value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
          rawNullableComprehensiveSchemaConsistent4 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent4.id)
              : rawNullableComprehensiveSchemaConsistent4,
          value.boolField);
    }
    if (slots.containsSlot(5)) {
      final rawNullableComprehensiveSchemaConsistent5 = slots.valueForSlot(5);
      value.longField = _readNullableComprehensiveSchemaConsistentLongField(
          rawNullableComprehensiveSchemaConsistent5 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent5.id)
              : rawNullableComprehensiveSchemaConsistent5,
          value.longField);
    }
    if (slots.containsSlot(6)) {
      final rawNullableComprehensiveSchemaConsistent6 = slots.valueForSlot(6);
      value.intField = _readNullableComprehensiveSchemaConsistentIntField(
          rawNullableComprehensiveSchemaConsistent6 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveSchemaConsistent6.id)
              : rawNullableComprehensiveSchemaConsistent6,
          value.intField);
    }
    if (slots.containsSlot(7)) {
      final rawNullableComprehensiveSchemaConsistent7 = slots.valueForSlot(7);
      value.nullableDouble =
          _readNullableComprehensiveSchemaConsistentNullableDouble(
              rawNullableComprehensiveSchemaConsistent7 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent7.id)
                  : rawNullableComprehensiveSchemaConsistent7,
              value.nullableDouble);
    }
    if (slots.containsSlot(8)) {
      final rawNullableComprehensiveSchemaConsistent8 = slots.valueForSlot(8);
      value.nullableFloat =
          _readNullableComprehensiveSchemaConsistentNullableFloat(
              rawNullableComprehensiveSchemaConsistent8 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent8.id)
                  : rawNullableComprehensiveSchemaConsistent8,
              value.nullableFloat);
    }
    if (slots.containsSlot(9)) {
      final rawNullableComprehensiveSchemaConsistent9 = slots.valueForSlot(9);
      value.nullableBool =
          _readNullableComprehensiveSchemaConsistentNullableBool(
              rawNullableComprehensiveSchemaConsistent9 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent9.id)
                  : rawNullableComprehensiveSchemaConsistent9,
              value.nullableBool);
    }
    if (slots.containsSlot(10)) {
      final rawNullableComprehensiveSchemaConsistent10 = slots.valueForSlot(10);
      value.nullableLong =
          _readNullableComprehensiveSchemaConsistentNullableLong(
              rawNullableComprehensiveSchemaConsistent10 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent10.id)
                  : rawNullableComprehensiveSchemaConsistent10,
              value.nullableLong);
    }
    if (slots.containsSlot(11)) {
      final rawNullableComprehensiveSchemaConsistent11 = slots.valueForSlot(11);
      value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
          rawNullableComprehensiveSchemaConsistent11 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent11.id)
              : rawNullableComprehensiveSchemaConsistent11,
          value.nullableInt);
    }
    if (slots.containsSlot(12)) {
      final rawNullableComprehensiveSchemaConsistent12 = slots.valueForSlot(12);
      value.nullableString =
          _readNullableComprehensiveSchemaConsistentNullableString(
              rawNullableComprehensiveSchemaConsistent12 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent12.id)
                  : rawNullableComprehensiveSchemaConsistent12,
              value.nullableString);
    }
    if (slots.containsSlot(13)) {
      final rawNullableComprehensiveSchemaConsistent13 = slots.valueForSlot(13);
      value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
          rawNullableComprehensiveSchemaConsistent13 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent13.id)
              : rawNullableComprehensiveSchemaConsistent13,
          value.stringField);
    }
    if (slots.containsSlot(14)) {
      final rawNullableComprehensiveSchemaConsistent14 = slots.valueForSlot(14);
      value.listField = _readNullableComprehensiveSchemaConsistentListField(
          rawNullableComprehensiveSchemaConsistent14 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent14.id)
              : rawNullableComprehensiveSchemaConsistent14,
          value.listField);
    }
    if (slots.containsSlot(15)) {
      final rawNullableComprehensiveSchemaConsistent15 = slots.valueForSlot(15);
      value.nullableList =
          _readNullableComprehensiveSchemaConsistentNullableList(
              rawNullableComprehensiveSchemaConsistent15 is DeferredReadRef
                  ? context
                      .getReadRef(rawNullableComprehensiveSchemaConsistent15.id)
                  : rawNullableComprehensiveSchemaConsistent15,
              value.nullableList);
    }
    if (slots.containsSlot(16)) {
      final rawNullableComprehensiveSchemaConsistent16 = slots.valueForSlot(16);
      value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
          rawNullableComprehensiveSchemaConsistent16 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent16.id)
              : rawNullableComprehensiveSchemaConsistent16,
          value.nullableSet);
    }
    if (slots.containsSlot(17)) {
      final rawNullableComprehensiveSchemaConsistent17 = slots.valueForSlot(17);
      value.setField = _readNullableComprehensiveSchemaConsistentSetField(
          rawNullableComprehensiveSchemaConsistent17 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent17.id)
              : rawNullableComprehensiveSchemaConsistent17,
          value.setField);
    }
    if (slots.containsSlot(18)) {
      final rawNullableComprehensiveSchemaConsistent18 = slots.valueForSlot(18);
      value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
          rawNullableComprehensiveSchemaConsistent18 is DeferredReadRef
              ? context
                  .getReadRef(rawNullableComprehensiveSchemaConsistent18.id)
              : rawNullableComprehensiveSchemaConsistent18,
          value.mapField);
    }
    if (slots.containsSlot(19)) {
      final rawNullableComprehensiveSchemaConsistent19 = slots.valueForSlot(19);
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

const List<GeneratedFieldInfo> _nullableComprehensiveCompatibleForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'boxedDouble',
    identifier: 'boxed_double',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'doubleField',
    identifier: 'double_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableDouble1',
    identifier: 'nullable_double1',
    id: null,
    fieldType: GeneratedFieldType(
      type: double,
      typeId: 20,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boxedFloat',
    identifier: 'boxed_float',
    id: null,
    fieldType: GeneratedFieldType(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'floatField',
    identifier: 'float_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableFloat1',
    identifier: 'nullable_float1',
    id: null,
    fieldType: GeneratedFieldType(
      type: Float32,
      typeId: 19,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'shortField',
    identifier: 'short_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int16,
      typeId: 3,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'byteField',
    identifier: 'byte_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int8,
      typeId: 2,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boolField',
    identifier: 'bool_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boxedBool',
    identifier: 'boxed_bool',
    id: null,
    fieldType: GeneratedFieldType(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableBool1',
    identifier: 'nullable_bool1',
    id: null,
    fieldType: GeneratedFieldType(
      type: bool,
      typeId: 1,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boxedLong',
    identifier: 'boxed_long',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'longField',
    identifier: 'long_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableLong1',
    identifier: 'nullable_long1',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 7,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'boxedInt',
    identifier: 'boxed_int',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'intField',
    identifier: 'int_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableInt1',
    identifier: 'nullable_int1',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableString2',
    identifier: 'nullable_string2',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'stringField',
    identifier: 'string_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'listField',
    identifier: 'list_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableList2',
    identifier: 'nullable_list2',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableSet2',
    identifier: 'nullable_set2',
    id: null,
    fieldType: GeneratedFieldType(
      type: Set,
      typeId: 23,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'setField',
    identifier: 'set_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Set,
      typeId: 23,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'mapField',
    identifier: 'map_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'nullableMap2',
    identifier: 'nullable_map2',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
];

typedef _NullableComprehensiveCompatibleFieldWriter
    = GeneratedStructFieldInfoWriter<NullableComprehensiveCompatible>;
typedef _NullableComprehensiveCompatibleFieldReader
    = GeneratedStructFieldInfoReader<NullableComprehensiveCompatible>;

void _writeNullableComprehensiveCompatibleField0(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boxedDouble);
}

void _writeNullableComprehensiveCompatibleField1(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.doubleField);
}

void _writeNullableComprehensiveCompatibleField2(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableDouble1);
}

void _writeNullableComprehensiveCompatibleField3(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boxedFloat);
}

void _writeNullableComprehensiveCompatibleField4(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.floatField);
}

void _writeNullableComprehensiveCompatibleField5(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableFloat1);
}

void _writeNullableComprehensiveCompatibleField6(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.shortField);
}

void _writeNullableComprehensiveCompatibleField7(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.byteField);
}

void _writeNullableComprehensiveCompatibleField8(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boolField);
}

void _writeNullableComprehensiveCompatibleField9(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boxedBool);
}

void _writeNullableComprehensiveCompatibleField10(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableBool1);
}

void _writeNullableComprehensiveCompatibleField11(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boxedLong);
}

void _writeNullableComprehensiveCompatibleField12(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.longField);
}

void _writeNullableComprehensiveCompatibleField13(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableLong1);
}

void _writeNullableComprehensiveCompatibleField14(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.boxedInt);
}

void _writeNullableComprehensiveCompatibleField15(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.intField);
}

void _writeNullableComprehensiveCompatibleField16(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableInt1);
}

void _writeNullableComprehensiveCompatibleField17(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableString2);
}

void _writeNullableComprehensiveCompatibleField18(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.stringField);
}

void _writeNullableComprehensiveCompatibleField19(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.listField);
}

void _writeNullableComprehensiveCompatibleField20(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableList2);
}

void _writeNullableComprehensiveCompatibleField21(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableSet2);
}

void _writeNullableComprehensiveCompatibleField22(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.setField);
}

void _writeNullableComprehensiveCompatibleField23(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.mapField);
}

void _writeNullableComprehensiveCompatibleField24(WriteContext context,
    GeneratedStructFieldInfo field, NullableComprehensiveCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.nullableMap2);
}

void _readNullableComprehensiveCompatibleField0(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedDouble);
}

void _readNullableComprehensiveCompatibleField1(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.doubleField);
}

void _readNullableComprehensiveCompatibleField2(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableDouble1 = _readNullableComprehensiveCompatibleNullableDouble1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableDouble1);
}

void _readNullableComprehensiveCompatibleField3(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedFloat);
}

void _readNullableComprehensiveCompatibleField4(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.floatField = _readNullableComprehensiveCompatibleFloatField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.floatField);
}

void _readNullableComprehensiveCompatibleField5(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableFloat1);
}

void _readNullableComprehensiveCompatibleField6(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.shortField = _readNullableComprehensiveCompatibleShortField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.shortField);
}

void _readNullableComprehensiveCompatibleField7(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.byteField = _readNullableComprehensiveCompatibleByteField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.byteField);
}

void _readNullableComprehensiveCompatibleField8(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boolField = _readNullableComprehensiveCompatibleBoolField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boolField);
}

void _readNullableComprehensiveCompatibleField9(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedBool);
}

void _readNullableComprehensiveCompatibleField10(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableBool1);
}

void _readNullableComprehensiveCompatibleField11(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedLong);
}

void _readNullableComprehensiveCompatibleField12(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.longField = _readNullableComprehensiveCompatibleLongField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.longField);
}

void _readNullableComprehensiveCompatibleField13(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableLong1);
}

void _readNullableComprehensiveCompatibleField14(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.boxedInt);
}

void _readNullableComprehensiveCompatibleField15(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.intField = _readNullableComprehensiveCompatibleIntField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.intField);
}

void _readNullableComprehensiveCompatibleField16(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableInt1);
}

void _readNullableComprehensiveCompatibleField17(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableString2 = _readNullableComprehensiveCompatibleNullableString2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableString2);
}

void _readNullableComprehensiveCompatibleField18(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.stringField = _readNullableComprehensiveCompatibleStringField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.stringField);
}

void _readNullableComprehensiveCompatibleField19(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.listField = _readNullableComprehensiveCompatibleListField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.listField);
}

void _readNullableComprehensiveCompatibleField20(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableList2);
}

void _readNullableComprehensiveCompatibleField21(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableSet2);
}

void _readNullableComprehensiveCompatibleField22(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.setField = _readNullableComprehensiveCompatibleSetField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.setField);
}

void _readNullableComprehensiveCompatibleField23(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.mapField = _readNullableComprehensiveCompatibleMapField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.mapField);
}

void _readNullableComprehensiveCompatibleField24(ReadContext context,
    NullableComprehensiveCompatible value, Object? rawValue) {
  value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.nullableMap2);
}

final GeneratedStructRegistration<NullableComprehensiveCompatible>
    _nullableComprehensiveCompatibleForyRegistration =
    GeneratedStructRegistration<NullableComprehensiveCompatible>(
  fieldWritersBySlot: <_NullableComprehensiveCompatibleFieldWriter>[
    _writeNullableComprehensiveCompatibleField0,
    _writeNullableComprehensiveCompatibleField1,
    _writeNullableComprehensiveCompatibleField2,
    _writeNullableComprehensiveCompatibleField3,
    _writeNullableComprehensiveCompatibleField4,
    _writeNullableComprehensiveCompatibleField5,
    _writeNullableComprehensiveCompatibleField6,
    _writeNullableComprehensiveCompatibleField7,
    _writeNullableComprehensiveCompatibleField8,
    _writeNullableComprehensiveCompatibleField9,
    _writeNullableComprehensiveCompatibleField10,
    _writeNullableComprehensiveCompatibleField11,
    _writeNullableComprehensiveCompatibleField12,
    _writeNullableComprehensiveCompatibleField13,
    _writeNullableComprehensiveCompatibleField14,
    _writeNullableComprehensiveCompatibleField15,
    _writeNullableComprehensiveCompatibleField16,
    _writeNullableComprehensiveCompatibleField17,
    _writeNullableComprehensiveCompatibleField18,
    _writeNullableComprehensiveCompatibleField19,
    _writeNullableComprehensiveCompatibleField20,
    _writeNullableComprehensiveCompatibleField21,
    _writeNullableComprehensiveCompatibleField22,
    _writeNullableComprehensiveCompatibleField23,
    _writeNullableComprehensiveCompatibleField24,
  ],
  compatibleFactory: NullableComprehensiveCompatible.new,
  compatibleReadersBySlot: <_NullableComprehensiveCompatibleFieldReader>[
    _readNullableComprehensiveCompatibleField0,
    _readNullableComprehensiveCompatibleField1,
    _readNullableComprehensiveCompatibleField2,
    _readNullableComprehensiveCompatibleField3,
    _readNullableComprehensiveCompatibleField4,
    _readNullableComprehensiveCompatibleField5,
    _readNullableComprehensiveCompatibleField6,
    _readNullableComprehensiveCompatibleField7,
    _readNullableComprehensiveCompatibleField8,
    _readNullableComprehensiveCompatibleField9,
    _readNullableComprehensiveCompatibleField10,
    _readNullableComprehensiveCompatibleField11,
    _readNullableComprehensiveCompatibleField12,
    _readNullableComprehensiveCompatibleField13,
    _readNullableComprehensiveCompatibleField14,
    _readNullableComprehensiveCompatibleField15,
    _readNullableComprehensiveCompatibleField16,
    _readNullableComprehensiveCompatibleField17,
    _readNullableComprehensiveCompatibleField18,
    _readNullableComprehensiveCompatibleField19,
    _readNullableComprehensiveCompatibleField20,
    _readNullableComprehensiveCompatibleField21,
    _readNullableComprehensiveCompatibleField22,
    _readNullableComprehensiveCompatibleField23,
    _readNullableComprehensiveCompatibleField24,
  ],
  type: NullableComprehensiveCompatible,
  serializerFactory: _NullableComprehensiveCompatibleForySerializer.new,
  evolving: true,
  fields: _nullableComprehensiveCompatibleForyFieldInfo,
);

final class _NullableComprehensiveCompatibleForySerializer
    extends Serializer<NullableComprehensiveCompatible> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _NullableComprehensiveCompatibleForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _nullableComprehensiveCompatibleForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _nullableComprehensiveCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, NullableComprehensiveCompatible value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
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
      writeGeneratedStructFieldInfoValue(context, fields[19], value.listField);
      writeGeneratedStructFieldInfoValue(
          context, fields[20], value.nullableList2);
      writeGeneratedStructFieldInfoValue(
          context, fields[21], value.nullableSet2);
      writeGeneratedStructFieldInfoValue(context, fields[22], value.setField);
      writeGeneratedStructFieldInfoValue(context, fields[23], value.mapField);
      writeGeneratedStructFieldInfoValue(
          context, fields[24], value.nullableMap2);
      return;
    }
    final writers =
        _nullableComprehensiveCompatibleForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  NullableComprehensiveCompatible read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = NullableComprehensiveCompatible();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
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
    if (slots.containsSlot(0)) {
      final rawNullableComprehensiveCompatible0 = slots.valueForSlot(0);
      value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
          rawNullableComprehensiveCompatible0 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible0.id)
              : rawNullableComprehensiveCompatible0,
          value.boxedDouble);
    }
    if (slots.containsSlot(1)) {
      final rawNullableComprehensiveCompatible1 = slots.valueForSlot(1);
      value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
          rawNullableComprehensiveCompatible1 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible1.id)
              : rawNullableComprehensiveCompatible1,
          value.doubleField);
    }
    if (slots.containsSlot(2)) {
      final rawNullableComprehensiveCompatible2 = slots.valueForSlot(2);
      value.nullableDouble1 =
          _readNullableComprehensiveCompatibleNullableDouble1(
              rawNullableComprehensiveCompatible2 is DeferredReadRef
                  ? context.getReadRef(rawNullableComprehensiveCompatible2.id)
                  : rawNullableComprehensiveCompatible2,
              value.nullableDouble1);
    }
    if (slots.containsSlot(3)) {
      final rawNullableComprehensiveCompatible3 = slots.valueForSlot(3);
      value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
          rawNullableComprehensiveCompatible3 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible3.id)
              : rawNullableComprehensiveCompatible3,
          value.boxedFloat);
    }
    if (slots.containsSlot(4)) {
      final rawNullableComprehensiveCompatible4 = slots.valueForSlot(4);
      value.floatField = _readNullableComprehensiveCompatibleFloatField(
          rawNullableComprehensiveCompatible4 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible4.id)
              : rawNullableComprehensiveCompatible4,
          value.floatField);
    }
    if (slots.containsSlot(5)) {
      final rawNullableComprehensiveCompatible5 = slots.valueForSlot(5);
      value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
          rawNullableComprehensiveCompatible5 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible5.id)
              : rawNullableComprehensiveCompatible5,
          value.nullableFloat1);
    }
    if (slots.containsSlot(6)) {
      final rawNullableComprehensiveCompatible6 = slots.valueForSlot(6);
      value.shortField = _readNullableComprehensiveCompatibleShortField(
          rawNullableComprehensiveCompatible6 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible6.id)
              : rawNullableComprehensiveCompatible6,
          value.shortField);
    }
    if (slots.containsSlot(7)) {
      final rawNullableComprehensiveCompatible7 = slots.valueForSlot(7);
      value.byteField = _readNullableComprehensiveCompatibleByteField(
          rawNullableComprehensiveCompatible7 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible7.id)
              : rawNullableComprehensiveCompatible7,
          value.byteField);
    }
    if (slots.containsSlot(8)) {
      final rawNullableComprehensiveCompatible8 = slots.valueForSlot(8);
      value.boolField = _readNullableComprehensiveCompatibleBoolField(
          rawNullableComprehensiveCompatible8 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible8.id)
              : rawNullableComprehensiveCompatible8,
          value.boolField);
    }
    if (slots.containsSlot(9)) {
      final rawNullableComprehensiveCompatible9 = slots.valueForSlot(9);
      value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
          rawNullableComprehensiveCompatible9 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible9.id)
              : rawNullableComprehensiveCompatible9,
          value.boxedBool);
    }
    if (slots.containsSlot(10)) {
      final rawNullableComprehensiveCompatible10 = slots.valueForSlot(10);
      value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
          rawNullableComprehensiveCompatible10 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible10.id)
              : rawNullableComprehensiveCompatible10,
          value.nullableBool1);
    }
    if (slots.containsSlot(11)) {
      final rawNullableComprehensiveCompatible11 = slots.valueForSlot(11);
      value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
          rawNullableComprehensiveCompatible11 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible11.id)
              : rawNullableComprehensiveCompatible11,
          value.boxedLong);
    }
    if (slots.containsSlot(12)) {
      final rawNullableComprehensiveCompatible12 = slots.valueForSlot(12);
      value.longField = _readNullableComprehensiveCompatibleLongField(
          rawNullableComprehensiveCompatible12 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible12.id)
              : rawNullableComprehensiveCompatible12,
          value.longField);
    }
    if (slots.containsSlot(13)) {
      final rawNullableComprehensiveCompatible13 = slots.valueForSlot(13);
      value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
          rawNullableComprehensiveCompatible13 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible13.id)
              : rawNullableComprehensiveCompatible13,
          value.nullableLong1);
    }
    if (slots.containsSlot(14)) {
      final rawNullableComprehensiveCompatible14 = slots.valueForSlot(14);
      value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
          rawNullableComprehensiveCompatible14 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible14.id)
              : rawNullableComprehensiveCompatible14,
          value.boxedInt);
    }
    if (slots.containsSlot(15)) {
      final rawNullableComprehensiveCompatible15 = slots.valueForSlot(15);
      value.intField = _readNullableComprehensiveCompatibleIntField(
          rawNullableComprehensiveCompatible15 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible15.id)
              : rawNullableComprehensiveCompatible15,
          value.intField);
    }
    if (slots.containsSlot(16)) {
      final rawNullableComprehensiveCompatible16 = slots.valueForSlot(16);
      value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
          rawNullableComprehensiveCompatible16 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible16.id)
              : rawNullableComprehensiveCompatible16,
          value.nullableInt1);
    }
    if (slots.containsSlot(17)) {
      final rawNullableComprehensiveCompatible17 = slots.valueForSlot(17);
      value.nullableString2 =
          _readNullableComprehensiveCompatibleNullableString2(
              rawNullableComprehensiveCompatible17 is DeferredReadRef
                  ? context.getReadRef(rawNullableComprehensiveCompatible17.id)
                  : rawNullableComprehensiveCompatible17,
              value.nullableString2);
    }
    if (slots.containsSlot(18)) {
      final rawNullableComprehensiveCompatible18 = slots.valueForSlot(18);
      value.stringField = _readNullableComprehensiveCompatibleStringField(
          rawNullableComprehensiveCompatible18 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible18.id)
              : rawNullableComprehensiveCompatible18,
          value.stringField);
    }
    if (slots.containsSlot(19)) {
      final rawNullableComprehensiveCompatible19 = slots.valueForSlot(19);
      value.listField = _readNullableComprehensiveCompatibleListField(
          rawNullableComprehensiveCompatible19 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible19.id)
              : rawNullableComprehensiveCompatible19,
          value.listField);
    }
    if (slots.containsSlot(20)) {
      final rawNullableComprehensiveCompatible20 = slots.valueForSlot(20);
      value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
          rawNullableComprehensiveCompatible20 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible20.id)
              : rawNullableComprehensiveCompatible20,
          value.nullableList2);
    }
    if (slots.containsSlot(21)) {
      final rawNullableComprehensiveCompatible21 = slots.valueForSlot(21);
      value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
          rawNullableComprehensiveCompatible21 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible21.id)
              : rawNullableComprehensiveCompatible21,
          value.nullableSet2);
    }
    if (slots.containsSlot(22)) {
      final rawNullableComprehensiveCompatible22 = slots.valueForSlot(22);
      value.setField = _readNullableComprehensiveCompatibleSetField(
          rawNullableComprehensiveCompatible22 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible22.id)
              : rawNullableComprehensiveCompatible22,
          value.setField);
    }
    if (slots.containsSlot(23)) {
      final rawNullableComprehensiveCompatible23 = slots.valueForSlot(23);
      value.mapField = _readNullableComprehensiveCompatibleMapField(
          rawNullableComprehensiveCompatible23 is DeferredReadRef
              ? context.getReadRef(rawNullableComprehensiveCompatible23.id)
              : rawNullableComprehensiveCompatible23,
          value.mapField);
    }
    if (slots.containsSlot(24)) {
      final rawNullableComprehensiveCompatible24 = slots.valueForSlot(24);
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

const List<GeneratedFieldInfo> _refInnerSchemaConsistentForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'id',
    identifier: 'id',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefInnerSchemaConsistentFieldWriter
    = GeneratedStructFieldInfoWriter<RefInnerSchemaConsistent>;
typedef _RefInnerSchemaConsistentFieldReader
    = GeneratedStructFieldInfoReader<RefInnerSchemaConsistent>;

void _writeRefInnerSchemaConsistentField0(WriteContext context,
    GeneratedStructFieldInfo field, RefInnerSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.id);
}

void _writeRefInnerSchemaConsistentField1(WriteContext context,
    GeneratedStructFieldInfo field, RefInnerSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _readRefInnerSchemaConsistentField0(
    ReadContext context, RefInnerSchemaConsistent value, Object? rawValue) {
  value.id = _readRefInnerSchemaConsistentId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefInnerSchemaConsistentField1(
    ReadContext context, RefInnerSchemaConsistent value, Object? rawValue) {
  value.name = _readRefInnerSchemaConsistentName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefInnerSchemaConsistent>
    _refInnerSchemaConsistentForyRegistration =
    GeneratedStructRegistration<RefInnerSchemaConsistent>(
  fieldWritersBySlot: <_RefInnerSchemaConsistentFieldWriter>[
    _writeRefInnerSchemaConsistentField0,
    _writeRefInnerSchemaConsistentField1,
  ],
  compatibleFactory: RefInnerSchemaConsistent.new,
  compatibleReadersBySlot: <_RefInnerSchemaConsistentFieldReader>[
    _readRefInnerSchemaConsistentField0,
    _readRefInnerSchemaConsistentField1,
  ],
  type: RefInnerSchemaConsistent,
  serializerFactory: _RefInnerSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _refInnerSchemaConsistentForyFieldInfo,
);

final class _RefInnerSchemaConsistentForySerializer
    extends Serializer<RefInnerSchemaConsistent> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefInnerSchemaConsistentForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refInnerSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refInnerSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefInnerSchemaConsistent value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers =
        _refInnerSchemaConsistentForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefInnerSchemaConsistent read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefInnerSchemaConsistent();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefInnerSchemaConsistent0 = slots.valueForSlot(0);
      value.id = _readRefInnerSchemaConsistentId(
          rawRefInnerSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawRefInnerSchemaConsistent0.id)
              : rawRefInnerSchemaConsistent0,
          value.id);
    }
    if (slots.containsSlot(1)) {
      final rawRefInnerSchemaConsistent1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _refOuterSchemaConsistentForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'inner1',
    identifier: 'inner1',
    id: null,
    fieldType: GeneratedFieldType(
      type: RefInnerSchemaConsistent,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: false,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'inner2',
    identifier: 'inner2',
    id: null,
    fieldType: GeneratedFieldType(
      type: RefInnerSchemaConsistent,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: false,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefOuterSchemaConsistentFieldWriter
    = GeneratedStructFieldInfoWriter<RefOuterSchemaConsistent>;
typedef _RefOuterSchemaConsistentFieldReader
    = GeneratedStructFieldInfoReader<RefOuterSchemaConsistent>;

void _writeRefOuterSchemaConsistentField0(WriteContext context,
    GeneratedStructFieldInfo field, RefOuterSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.inner1);
}

void _writeRefOuterSchemaConsistentField1(WriteContext context,
    GeneratedStructFieldInfo field, RefOuterSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.inner2);
}

void _readRefOuterSchemaConsistentField0(
    ReadContext context, RefOuterSchemaConsistent value, Object? rawValue) {
  value.inner1 = _readRefOuterSchemaConsistentInner1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner1);
}

void _readRefOuterSchemaConsistentField1(
    ReadContext context, RefOuterSchemaConsistent value, Object? rawValue) {
  value.inner2 = _readRefOuterSchemaConsistentInner2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner2);
}

final GeneratedStructRegistration<RefOuterSchemaConsistent>
    _refOuterSchemaConsistentForyRegistration =
    GeneratedStructRegistration<RefOuterSchemaConsistent>(
  fieldWritersBySlot: <_RefOuterSchemaConsistentFieldWriter>[
    _writeRefOuterSchemaConsistentField0,
    _writeRefOuterSchemaConsistentField1,
  ],
  compatibleFactory: RefOuterSchemaConsistent.new,
  compatibleReadersBySlot: <_RefOuterSchemaConsistentFieldReader>[
    _readRefOuterSchemaConsistentField0,
    _readRefOuterSchemaConsistentField1,
  ],
  type: RefOuterSchemaConsistent,
  serializerFactory: _RefOuterSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _refOuterSchemaConsistentForyFieldInfo,
);

final class _RefOuterSchemaConsistentForySerializer
    extends Serializer<RefOuterSchemaConsistent> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefOuterSchemaConsistentForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOuterSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOuterSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOuterSchemaConsistent value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.inner1);
      writeGeneratedStructFieldInfoValue(context, fields[1], value.inner2);
      return;
    }
    final writers =
        _refOuterSchemaConsistentForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOuterSchemaConsistent read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefOuterSchemaConsistent();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.inner1 = _readRefOuterSchemaConsistentInner1(
          readGeneratedStructFieldInfoValue(context, fields[0], value.inner1),
          value.inner1);
      value.inner2 = _readRefOuterSchemaConsistentInner2(
          readGeneratedStructFieldInfoValue(context, fields[1], value.inner2),
          value.inner2);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefOuterSchemaConsistent0 = slots.valueForSlot(0);
      value.inner1 = _readRefOuterSchemaConsistentInner1(
          rawRefOuterSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawRefOuterSchemaConsistent0.id)
              : rawRefOuterSchemaConsistent0,
          value.inner1);
    }
    if (slots.containsSlot(1)) {
      final rawRefOuterSchemaConsistent1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _refInnerCompatibleForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'id',
    identifier: 'id',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefInnerCompatibleFieldWriter
    = GeneratedStructFieldInfoWriter<RefInnerCompatible>;
typedef _RefInnerCompatibleFieldReader
    = GeneratedStructFieldInfoReader<RefInnerCompatible>;

void _writeRefInnerCompatibleField0(WriteContext context,
    GeneratedStructFieldInfo field, RefInnerCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.id);
}

void _writeRefInnerCompatibleField1(WriteContext context,
    GeneratedStructFieldInfo field, RefInnerCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _readRefInnerCompatibleField0(
    ReadContext context, RefInnerCompatible value, Object? rawValue) {
  value.id = _readRefInnerCompatibleId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefInnerCompatibleField1(
    ReadContext context, RefInnerCompatible value, Object? rawValue) {
  value.name = _readRefInnerCompatibleName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefInnerCompatible>
    _refInnerCompatibleForyRegistration =
    GeneratedStructRegistration<RefInnerCompatible>(
  fieldWritersBySlot: <_RefInnerCompatibleFieldWriter>[
    _writeRefInnerCompatibleField0,
    _writeRefInnerCompatibleField1,
  ],
  compatibleFactory: RefInnerCompatible.new,
  compatibleReadersBySlot: <_RefInnerCompatibleFieldReader>[
    _readRefInnerCompatibleField0,
    _readRefInnerCompatibleField1,
  ],
  type: RefInnerCompatible,
  serializerFactory: _RefInnerCompatibleForySerializer.new,
  evolving: true,
  fields: _refInnerCompatibleForyFieldInfo,
);

final class _RefInnerCompatibleForySerializer
    extends Serializer<RefInnerCompatible> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefInnerCompatibleForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refInnerCompatibleForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refInnerCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefInnerCompatible value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers = _refInnerCompatibleForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefInnerCompatible read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefInnerCompatible();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = cursor0.readVarInt32();
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefInnerCompatible0 = slots.valueForSlot(0);
      value.id = _readRefInnerCompatibleId(
          rawRefInnerCompatible0 is DeferredReadRef
              ? context.getReadRef(rawRefInnerCompatible0.id)
              : rawRefInnerCompatible0,
          value.id);
    }
    if (slots.containsSlot(1)) {
      final rawRefInnerCompatible1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _refOuterCompatibleForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'inner1',
    identifier: 'inner1',
    id: null,
    fieldType: GeneratedFieldType(
      type: RefInnerCompatible,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'inner2',
    identifier: 'inner2',
    id: null,
    fieldType: GeneratedFieldType(
      type: RefInnerCompatible,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefOuterCompatibleFieldWriter
    = GeneratedStructFieldInfoWriter<RefOuterCompatible>;
typedef _RefOuterCompatibleFieldReader
    = GeneratedStructFieldInfoReader<RefOuterCompatible>;

void _writeRefOuterCompatibleField0(WriteContext context,
    GeneratedStructFieldInfo field, RefOuterCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.inner1);
}

void _writeRefOuterCompatibleField1(WriteContext context,
    GeneratedStructFieldInfo field, RefOuterCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.inner2);
}

void _readRefOuterCompatibleField0(
    ReadContext context, RefOuterCompatible value, Object? rawValue) {
  value.inner1 = _readRefOuterCompatibleInner1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner1);
}

void _readRefOuterCompatibleField1(
    ReadContext context, RefOuterCompatible value, Object? rawValue) {
  value.inner2 = _readRefOuterCompatibleInner2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.inner2);
}

final GeneratedStructRegistration<RefOuterCompatible>
    _refOuterCompatibleForyRegistration =
    GeneratedStructRegistration<RefOuterCompatible>(
  fieldWritersBySlot: <_RefOuterCompatibleFieldWriter>[
    _writeRefOuterCompatibleField0,
    _writeRefOuterCompatibleField1,
  ],
  compatibleFactory: RefOuterCompatible.new,
  compatibleReadersBySlot: <_RefOuterCompatibleFieldReader>[
    _readRefOuterCompatibleField0,
    _readRefOuterCompatibleField1,
  ],
  type: RefOuterCompatible,
  serializerFactory: _RefOuterCompatibleForySerializer.new,
  evolving: true,
  fields: _refOuterCompatibleForyFieldInfo,
);

final class _RefOuterCompatibleForySerializer
    extends Serializer<RefOuterCompatible> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefOuterCompatibleForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOuterCompatibleForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOuterCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOuterCompatible value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      writeGeneratedStructFieldInfoValue(context, fields[0], value.inner1);
      writeGeneratedStructFieldInfoValue(context, fields[1], value.inner2);
      return;
    }
    final writers = _refOuterCompatibleForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOuterCompatible read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefOuterCompatible();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.inner1 = _readRefOuterCompatibleInner1(
          readGeneratedStructFieldInfoValue(context, fields[0], value.inner1),
          value.inner1);
      value.inner2 = _readRefOuterCompatibleInner2(
          readGeneratedStructFieldInfoValue(context, fields[1], value.inner2),
          value.inner2);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefOuterCompatible0 = slots.valueForSlot(0);
      value.inner1 = _readRefOuterCompatibleInner1(
          rawRefOuterCompatible0 is DeferredReadRef
              ? context.getReadRef(rawRefOuterCompatible0.id)
              : rawRefOuterCompatible0,
          value.inner1);
    }
    if (slots.containsSlot(1)) {
      final rawRefOuterCompatible1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _refOverrideElementForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'id',
    identifier: 'id',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefOverrideElementFieldWriter
    = GeneratedStructFieldInfoWriter<RefOverrideElement>;
typedef _RefOverrideElementFieldReader
    = GeneratedStructFieldInfoReader<RefOverrideElement>;

void _writeRefOverrideElementField0(WriteContext context,
    GeneratedStructFieldInfo field, RefOverrideElement value) {
  writeGeneratedStructFieldInfoValue(context, field, value.id);
}

void _writeRefOverrideElementField1(WriteContext context,
    GeneratedStructFieldInfo field, RefOverrideElement value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _readRefOverrideElementField0(
    ReadContext context, RefOverrideElement value, Object? rawValue) {
  value.id = _readRefOverrideElementId(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.id);
}

void _readRefOverrideElementField1(
    ReadContext context, RefOverrideElement value, Object? rawValue) {
  value.name = _readRefOverrideElementName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

final GeneratedStructRegistration<RefOverrideElement>
    _refOverrideElementForyRegistration =
    GeneratedStructRegistration<RefOverrideElement>(
  fieldWritersBySlot: <_RefOverrideElementFieldWriter>[
    _writeRefOverrideElementField0,
    _writeRefOverrideElementField1,
  ],
  compatibleFactory: RefOverrideElement.new,
  compatibleReadersBySlot: <_RefOverrideElementFieldReader>[
    _readRefOverrideElementField0,
    _readRefOverrideElementField1,
  ],
  type: RefOverrideElement,
  serializerFactory: _RefOverrideElementForySerializer.new,
  evolving: true,
  fields: _refOverrideElementForyFieldInfo,
);

final class _RefOverrideElementForySerializer
    extends Serializer<RefOverrideElement> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefOverrideElementForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOverrideElementForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refOverrideElementForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefOverrideElement value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.id.value);
      cursor0.finish();
      context.writeString(value.name);
      return;
    }
    final writers = _refOverrideElementForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefOverrideElement read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefOverrideElement();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.id = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.name = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefOverrideElement0 = slots.valueForSlot(0);
      value.id = _readRefOverrideElementId(
          rawRefOverrideElement0 is DeferredReadRef
              ? context.getReadRef(rawRefOverrideElement0.id)
              : rawRefOverrideElement0,
          value.id);
    }
    if (slots.containsSlot(1)) {
      final rawRefOverrideElement1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _circularRefStructForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'selfRef',
    identifier: 'self_ref',
    id: null,
    fieldType: GeneratedFieldType(
      type: CircularRefStruct,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _CircularRefStructFieldWriter
    = GeneratedStructFieldInfoWriter<CircularRefStruct>;
typedef _CircularRefStructFieldReader
    = GeneratedStructFieldInfoReader<CircularRefStruct>;

void _writeCircularRefStructField0(WriteContext context,
    GeneratedStructFieldInfo field, CircularRefStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _writeCircularRefStructField1(WriteContext context,
    GeneratedStructFieldInfo field, CircularRefStruct value) {
  writeGeneratedStructFieldInfoValue(context, field, value.selfRef);
}

void _readCircularRefStructField0(
    ReadContext context, CircularRefStruct value, Object? rawValue) {
  value.name = _readCircularRefStructName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readCircularRefStructField1(
    ReadContext context, CircularRefStruct value, Object? rawValue) {
  value.selfRef = _readCircularRefStructSelfRef(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.selfRef);
}

final GeneratedStructRegistration<CircularRefStruct>
    _circularRefStructForyRegistration =
    GeneratedStructRegistration<CircularRefStruct>(
  fieldWritersBySlot: <_CircularRefStructFieldWriter>[
    _writeCircularRefStructField0,
    _writeCircularRefStructField1,
  ],
  compatibleFactory: CircularRefStruct.new,
  compatibleReadersBySlot: <_CircularRefStructFieldReader>[
    _readCircularRefStructField0,
    _readCircularRefStructField1,
  ],
  type: CircularRefStruct,
  serializerFactory: _CircularRefStructForySerializer.new,
  evolving: true,
  fields: _circularRefStructForyFieldInfo,
);

final class _CircularRefStructForySerializer
    extends Serializer<CircularRefStruct> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _CircularRefStructForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _circularRefStructForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _circularRefStructForyRegistration,
    );
  }

  @override
  void write(WriteContext context, CircularRefStruct value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      context.writeString(value.name);
      writeGeneratedStructFieldInfoValue(context, fields[1], value.selfRef);
      return;
    }
    final writers = _circularRefStructForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  CircularRefStruct read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = CircularRefStruct();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.name = context.readString();
      value.selfRef = _readCircularRefStructSelfRef(
          readGeneratedStructFieldInfoValue(context, fields[1], value.selfRef),
          value.selfRef);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawCircularRefStruct0 = slots.valueForSlot(0);
      value.name = _readCircularRefStructName(
          rawCircularRefStruct0 is DeferredReadRef
              ? context.getReadRef(rawCircularRefStruct0.id)
              : rawCircularRefStruct0,
          value.name);
    }
    if (slots.containsSlot(1)) {
      final rawCircularRefStruct1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _unsignedSchemaConsistentForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'u64FixedField',
    identifier: 'u64_fixed_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 13,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32FixedField',
    identifier: 'u32_fixed_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 11,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u16Field',
    identifier: 'u16_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt16,
      typeId: 10,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u8Field',
    identifier: 'u8_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt8,
      typeId: 9,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64TaggedField',
    identifier: 'u64_tagged_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64VarField',
    identifier: 'u64_var_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 14,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32VarField',
    identifier: 'u32_var_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 12,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64FixedNullableField',
    identifier: 'u64_fixed_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 13,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32FixedNullableField',
    identifier: 'u32_fixed_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 11,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u16NullableField',
    identifier: 'u16_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt16,
      typeId: 10,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u8NullableField',
    identifier: 'u8_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt8,
      typeId: 9,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64TaggedNullableField',
    identifier: 'u64_tagged_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64VarNullableField',
    identifier: 'u64_var_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 14,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32VarNullableField',
    identifier: 'u32_var_nullable_field',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 12,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _UnsignedSchemaConsistentFieldWriter
    = GeneratedStructFieldInfoWriter<UnsignedSchemaConsistent>;
typedef _UnsignedSchemaConsistentFieldReader
    = GeneratedStructFieldInfoReader<UnsignedSchemaConsistent>;

void _writeUnsignedSchemaConsistentField0(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64FixedField);
}

void _writeUnsignedSchemaConsistentField1(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32FixedField);
}

void _writeUnsignedSchemaConsistentField2(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u16Field);
}

void _writeUnsignedSchemaConsistentField3(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u8Field);
}

void _writeUnsignedSchemaConsistentField4(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64TaggedField);
}

void _writeUnsignedSchemaConsistentField5(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64VarField);
}

void _writeUnsignedSchemaConsistentField6(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32VarField);
}

void _writeUnsignedSchemaConsistentField7(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(
      context, field, value.u64FixedNullableField);
}

void _writeUnsignedSchemaConsistentField8(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(
      context, field, value.u32FixedNullableField);
}

void _writeUnsignedSchemaConsistentField9(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u16NullableField);
}

void _writeUnsignedSchemaConsistentField10(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u8NullableField);
}

void _writeUnsignedSchemaConsistentField11(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(
      context, field, value.u64TaggedNullableField);
}

void _writeUnsignedSchemaConsistentField12(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64VarNullableField);
}

void _writeUnsignedSchemaConsistentField13(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistent value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32VarNullableField);
}

void _readUnsignedSchemaConsistentField0(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField);
}

void _readUnsignedSchemaConsistentField1(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField);
}

void _readUnsignedSchemaConsistentField2(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u16Field = _readUnsignedSchemaConsistentU16Field(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field);
}

void _readUnsignedSchemaConsistentField3(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u8Field = _readUnsignedSchemaConsistentU8Field(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field);
}

void _readUnsignedSchemaConsistentField4(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField);
}

void _readUnsignedSchemaConsistentField5(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField);
}

void _readUnsignedSchemaConsistentField6(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField);
}

void _readUnsignedSchemaConsistentField7(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64FixedNullableField =
      _readUnsignedSchemaConsistentU64FixedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u64FixedNullableField);
}

void _readUnsignedSchemaConsistentField8(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32FixedNullableField =
      _readUnsignedSchemaConsistentU32FixedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u32FixedNullableField);
}

void _readUnsignedSchemaConsistentField9(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16NullableField);
}

void _readUnsignedSchemaConsistentField10(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8NullableField);
}

void _readUnsignedSchemaConsistentField11(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64TaggedNullableField =
      _readUnsignedSchemaConsistentU64TaggedNullableField(
          rawValue is DeferredReadRef
              ? context.getReadRef(rawValue.id)
              : rawValue,
          value.u64TaggedNullableField);
}

void _readUnsignedSchemaConsistentField12(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u64VarNullableField = _readUnsignedSchemaConsistentU64VarNullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarNullableField);
}

void _readUnsignedSchemaConsistentField13(
    ReadContext context, UnsignedSchemaConsistent value, Object? rawValue) {
  value.u32VarNullableField = _readUnsignedSchemaConsistentU32VarNullableField(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarNullableField);
}

final GeneratedStructRegistration<UnsignedSchemaConsistent>
    _unsignedSchemaConsistentForyRegistration =
    GeneratedStructRegistration<UnsignedSchemaConsistent>(
  fieldWritersBySlot: <_UnsignedSchemaConsistentFieldWriter>[
    _writeUnsignedSchemaConsistentField0,
    _writeUnsignedSchemaConsistentField1,
    _writeUnsignedSchemaConsistentField2,
    _writeUnsignedSchemaConsistentField3,
    _writeUnsignedSchemaConsistentField4,
    _writeUnsignedSchemaConsistentField5,
    _writeUnsignedSchemaConsistentField6,
    _writeUnsignedSchemaConsistentField7,
    _writeUnsignedSchemaConsistentField8,
    _writeUnsignedSchemaConsistentField9,
    _writeUnsignedSchemaConsistentField10,
    _writeUnsignedSchemaConsistentField11,
    _writeUnsignedSchemaConsistentField12,
    _writeUnsignedSchemaConsistentField13,
  ],
  compatibleFactory: UnsignedSchemaConsistent.new,
  compatibleReadersBySlot: <_UnsignedSchemaConsistentFieldReader>[
    _readUnsignedSchemaConsistentField0,
    _readUnsignedSchemaConsistentField1,
    _readUnsignedSchemaConsistentField2,
    _readUnsignedSchemaConsistentField3,
    _readUnsignedSchemaConsistentField4,
    _readUnsignedSchemaConsistentField5,
    _readUnsignedSchemaConsistentField6,
    _readUnsignedSchemaConsistentField7,
    _readUnsignedSchemaConsistentField8,
    _readUnsignedSchemaConsistentField9,
    _readUnsignedSchemaConsistentField10,
    _readUnsignedSchemaConsistentField11,
    _readUnsignedSchemaConsistentField12,
    _readUnsignedSchemaConsistentField13,
  ],
  type: UnsignedSchemaConsistent,
  serializerFactory: _UnsignedSchemaConsistentForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaConsistentForyFieldInfo,
);

final class _UnsignedSchemaConsistentForySerializer
    extends Serializer<UnsignedSchemaConsistent> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _UnsignedSchemaConsistentForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaConsistentForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaConsistentForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaConsistent value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 40);
      cursor0.writeUint64(value.u64FixedField);
      cursor0.writeUint32(value.u32FixedField.value);
      cursor0.writeUint16(value.u16Field.value);
      cursor0.writeUint8(value.u8Field.value);
      cursor0.writeTaggedUint64(value.u64TaggedField);
      cursor0.writeVarUint64(value.u64VarField);
      cursor0.writeVarUint32(value.u32VarField.value);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(
          context, fields[7], value.u64FixedNullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[8], value.u32FixedNullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[9], value.u16NullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[10], value.u8NullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[11], value.u64TaggedNullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[12], value.u64VarNullableField);
      writeGeneratedStructFieldInfoValue(
          context, fields[13], value.u32VarNullableField);
      return;
    }
    final writers =
        _unsignedSchemaConsistentForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaConsistent read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = UnsignedSchemaConsistent();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
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
              readGeneratedStructFieldInfoValue(
                  context, fields[7], value.u64FixedNullableField),
              value.u64FixedNullableField);
      value.u32FixedNullableField =
          _readUnsignedSchemaConsistentU32FixedNullableField(
              readGeneratedStructFieldInfoValue(
                  context, fields[8], value.u32FixedNullableField),
              value.u32FixedNullableField);
      value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
          readGeneratedStructFieldInfoValue(
              context, fields[9], value.u16NullableField),
          value.u16NullableField);
      value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
          readGeneratedStructFieldInfoValue(
              context, fields[10], value.u8NullableField),
          value.u8NullableField);
      value.u64TaggedNullableField =
          _readUnsignedSchemaConsistentU64TaggedNullableField(
              readGeneratedStructFieldInfoValue(
                  context, fields[11], value.u64TaggedNullableField),
              value.u64TaggedNullableField);
      value.u64VarNullableField =
          _readUnsignedSchemaConsistentU64VarNullableField(
              readGeneratedStructFieldInfoValue(
                  context, fields[12], value.u64VarNullableField),
              value.u64VarNullableField);
      value.u32VarNullableField =
          _readUnsignedSchemaConsistentU32VarNullableField(
              readGeneratedStructFieldInfoValue(
                  context, fields[13], value.u32VarNullableField),
              value.u32VarNullableField);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawUnsignedSchemaConsistent0 = slots.valueForSlot(0);
      value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
          rawUnsignedSchemaConsistent0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent0.id)
              : rawUnsignedSchemaConsistent0,
          value.u64FixedField);
    }
    if (slots.containsSlot(1)) {
      final rawUnsignedSchemaConsistent1 = slots.valueForSlot(1);
      value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
          rawUnsignedSchemaConsistent1 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent1.id)
              : rawUnsignedSchemaConsistent1,
          value.u32FixedField);
    }
    if (slots.containsSlot(2)) {
      final rawUnsignedSchemaConsistent2 = slots.valueForSlot(2);
      value.u16Field = _readUnsignedSchemaConsistentU16Field(
          rawUnsignedSchemaConsistent2 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent2.id)
              : rawUnsignedSchemaConsistent2,
          value.u16Field);
    }
    if (slots.containsSlot(3)) {
      final rawUnsignedSchemaConsistent3 = slots.valueForSlot(3);
      value.u8Field = _readUnsignedSchemaConsistentU8Field(
          rawUnsignedSchemaConsistent3 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent3.id)
              : rawUnsignedSchemaConsistent3,
          value.u8Field);
    }
    if (slots.containsSlot(4)) {
      final rawUnsignedSchemaConsistent4 = slots.valueForSlot(4);
      value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
          rawUnsignedSchemaConsistent4 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent4.id)
              : rawUnsignedSchemaConsistent4,
          value.u64TaggedField);
    }
    if (slots.containsSlot(5)) {
      final rawUnsignedSchemaConsistent5 = slots.valueForSlot(5);
      value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
          rawUnsignedSchemaConsistent5 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent5.id)
              : rawUnsignedSchemaConsistent5,
          value.u64VarField);
    }
    if (slots.containsSlot(6)) {
      final rawUnsignedSchemaConsistent6 = slots.valueForSlot(6);
      value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
          rawUnsignedSchemaConsistent6 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent6.id)
              : rawUnsignedSchemaConsistent6,
          value.u32VarField);
    }
    if (slots.containsSlot(7)) {
      final rawUnsignedSchemaConsistent7 = slots.valueForSlot(7);
      value.u64FixedNullableField =
          _readUnsignedSchemaConsistentU64FixedNullableField(
              rawUnsignedSchemaConsistent7 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent7.id)
                  : rawUnsignedSchemaConsistent7,
              value.u64FixedNullableField);
    }
    if (slots.containsSlot(8)) {
      final rawUnsignedSchemaConsistent8 = slots.valueForSlot(8);
      value.u32FixedNullableField =
          _readUnsignedSchemaConsistentU32FixedNullableField(
              rawUnsignedSchemaConsistent8 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent8.id)
                  : rawUnsignedSchemaConsistent8,
              value.u32FixedNullableField);
    }
    if (slots.containsSlot(9)) {
      final rawUnsignedSchemaConsistent9 = slots.valueForSlot(9);
      value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
          rawUnsignedSchemaConsistent9 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent9.id)
              : rawUnsignedSchemaConsistent9,
          value.u16NullableField);
    }
    if (slots.containsSlot(10)) {
      final rawUnsignedSchemaConsistent10 = slots.valueForSlot(10);
      value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
          rawUnsignedSchemaConsistent10 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistent10.id)
              : rawUnsignedSchemaConsistent10,
          value.u8NullableField);
    }
    if (slots.containsSlot(11)) {
      final rawUnsignedSchemaConsistent11 = slots.valueForSlot(11);
      value.u64TaggedNullableField =
          _readUnsignedSchemaConsistentU64TaggedNullableField(
              rawUnsignedSchemaConsistent11 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent11.id)
                  : rawUnsignedSchemaConsistent11,
              value.u64TaggedNullableField);
    }
    if (slots.containsSlot(12)) {
      final rawUnsignedSchemaConsistent12 = slots.valueForSlot(12);
      value.u64VarNullableField =
          _readUnsignedSchemaConsistentU64VarNullableField(
              rawUnsignedSchemaConsistent12 is DeferredReadRef
                  ? context.getReadRef(rawUnsignedSchemaConsistent12.id)
                  : rawUnsignedSchemaConsistent12,
              value.u64VarNullableField);
    }
    if (slots.containsSlot(13)) {
      final rawUnsignedSchemaConsistent13 = slots.valueForSlot(13);
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

const List<GeneratedFieldInfo> _unsignedSchemaConsistentSimpleForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'u64Tagged',
    identifier: 'u64_tagged',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64TaggedNullable',
    identifier: 'u64_tagged_nullable',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _UnsignedSchemaConsistentSimpleFieldWriter
    = GeneratedStructFieldInfoWriter<UnsignedSchemaConsistentSimple>;
typedef _UnsignedSchemaConsistentSimpleFieldReader
    = GeneratedStructFieldInfoReader<UnsignedSchemaConsistentSimple>;

void _writeUnsignedSchemaConsistentSimpleField0(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistentSimple value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64Tagged);
}

void _writeUnsignedSchemaConsistentSimpleField1(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaConsistentSimple value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64TaggedNullable);
}

void _readUnsignedSchemaConsistentSimpleField0(ReadContext context,
    UnsignedSchemaConsistentSimple value, Object? rawValue) {
  value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64Tagged);
}

void _readUnsignedSchemaConsistentSimpleField1(ReadContext context,
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
  fieldWritersBySlot: <_UnsignedSchemaConsistentSimpleFieldWriter>[
    _writeUnsignedSchemaConsistentSimpleField0,
    _writeUnsignedSchemaConsistentSimpleField1,
  ],
  compatibleFactory: UnsignedSchemaConsistentSimple.new,
  compatibleReadersBySlot: <_UnsignedSchemaConsistentSimpleFieldReader>[
    _readUnsignedSchemaConsistentSimpleField0,
    _readUnsignedSchemaConsistentSimpleField1,
  ],
  type: UnsignedSchemaConsistentSimple,
  serializerFactory: _UnsignedSchemaConsistentSimpleForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaConsistentSimpleForyFieldInfo,
);

final class _UnsignedSchemaConsistentSimpleForySerializer
    extends Serializer<UnsignedSchemaConsistentSimple> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _UnsignedSchemaConsistentSimpleForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaConsistentSimpleForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaConsistentSimpleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaConsistentSimple value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 10);
      cursor0.writeTaggedUint64(value.u64Tagged);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(
          context, fields[1], value.u64TaggedNullable);
      return;
    }
    final writers =
        _unsignedSchemaConsistentSimpleForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaConsistentSimple read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = UnsignedSchemaConsistentSimple();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.u64Tagged = cursor0.readTaggedUint64();
      cursor0.finish();
      value.u64TaggedNullable =
          _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
              readGeneratedStructFieldInfoValue(
                  context, fields[1], value.u64TaggedNullable),
              value.u64TaggedNullable);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawUnsignedSchemaConsistentSimple0 = slots.valueForSlot(0);
      value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
          rawUnsignedSchemaConsistentSimple0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaConsistentSimple0.id)
              : rawUnsignedSchemaConsistentSimple0,
          value.u64Tagged);
    }
    if (slots.containsSlot(1)) {
      final rawUnsignedSchemaConsistentSimple1 = slots.valueForSlot(1);
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

const List<GeneratedFieldInfo> _unsignedSchemaCompatibleForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'u64FixedField2',
    identifier: 'u64_fixed_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 13,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32FixedField2',
    identifier: 'u32_fixed_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 11,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u16Field2',
    identifier: 'u16_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt16,
      typeId: 10,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u8Field2',
    identifier: 'u8_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt8,
      typeId: 9,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64TaggedField2',
    identifier: 'u64_tagged_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64VarField2',
    identifier: 'u64_var_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 14,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32VarField2',
    identifier: 'u32_var_field2',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 12,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64FixedField1',
    identifier: 'u64_fixed_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 13,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32FixedField1',
    identifier: 'u32_fixed_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 11,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u16Field1',
    identifier: 'u16_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt16,
      typeId: 10,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u8Field1',
    identifier: 'u8_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt8,
      typeId: 9,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64TaggedField1',
    identifier: 'u64_tagged_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 15,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u64VarField1',
    identifier: 'u64_var_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: int,
      typeId: 14,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'u32VarField1',
    identifier: 'u32_var_field1',
    id: null,
    fieldType: GeneratedFieldType(
      type: UInt32,
      typeId: 12,
      nullable: true,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _UnsignedSchemaCompatibleFieldWriter
    = GeneratedStructFieldInfoWriter<UnsignedSchemaCompatible>;
typedef _UnsignedSchemaCompatibleFieldReader
    = GeneratedStructFieldInfoReader<UnsignedSchemaCompatible>;

void _writeUnsignedSchemaCompatibleField0(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64FixedField2);
}

void _writeUnsignedSchemaCompatibleField1(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32FixedField2);
}

void _writeUnsignedSchemaCompatibleField2(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u16Field2);
}

void _writeUnsignedSchemaCompatibleField3(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u8Field2);
}

void _writeUnsignedSchemaCompatibleField4(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64TaggedField2);
}

void _writeUnsignedSchemaCompatibleField5(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64VarField2);
}

void _writeUnsignedSchemaCompatibleField6(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32VarField2);
}

void _writeUnsignedSchemaCompatibleField7(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64FixedField1);
}

void _writeUnsignedSchemaCompatibleField8(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32FixedField1);
}

void _writeUnsignedSchemaCompatibleField9(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u16Field1);
}

void _writeUnsignedSchemaCompatibleField10(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u8Field1);
}

void _writeUnsignedSchemaCompatibleField11(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64TaggedField1);
}

void _writeUnsignedSchemaCompatibleField12(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u64VarField1);
}

void _writeUnsignedSchemaCompatibleField13(WriteContext context,
    GeneratedStructFieldInfo field, UnsignedSchemaCompatible value) {
  writeGeneratedStructFieldInfoValue(context, field, value.u32VarField1);
}

void _readUnsignedSchemaCompatibleField0(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField2);
}

void _readUnsignedSchemaCompatibleField1(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField2);
}

void _readUnsignedSchemaCompatibleField2(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field2);
}

void _readUnsignedSchemaCompatibleField3(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field2);
}

void _readUnsignedSchemaCompatibleField4(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField2);
}

void _readUnsignedSchemaCompatibleField5(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField2);
}

void _readUnsignedSchemaCompatibleField6(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField2);
}

void _readUnsignedSchemaCompatibleField7(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64FixedField1);
}

void _readUnsignedSchemaCompatibleField8(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32FixedField1);
}

void _readUnsignedSchemaCompatibleField9(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u16Field1);
}

void _readUnsignedSchemaCompatibleField10(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u8Field1);
}

void _readUnsignedSchemaCompatibleField11(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64TaggedField1);
}

void _readUnsignedSchemaCompatibleField12(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u64VarField1);
}

void _readUnsignedSchemaCompatibleField13(
    ReadContext context, UnsignedSchemaCompatible value, Object? rawValue) {
  value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.u32VarField1);
}

final GeneratedStructRegistration<UnsignedSchemaCompatible>
    _unsignedSchemaCompatibleForyRegistration =
    GeneratedStructRegistration<UnsignedSchemaCompatible>(
  fieldWritersBySlot: <_UnsignedSchemaCompatibleFieldWriter>[
    _writeUnsignedSchemaCompatibleField0,
    _writeUnsignedSchemaCompatibleField1,
    _writeUnsignedSchemaCompatibleField2,
    _writeUnsignedSchemaCompatibleField3,
    _writeUnsignedSchemaCompatibleField4,
    _writeUnsignedSchemaCompatibleField5,
    _writeUnsignedSchemaCompatibleField6,
    _writeUnsignedSchemaCompatibleField7,
    _writeUnsignedSchemaCompatibleField8,
    _writeUnsignedSchemaCompatibleField9,
    _writeUnsignedSchemaCompatibleField10,
    _writeUnsignedSchemaCompatibleField11,
    _writeUnsignedSchemaCompatibleField12,
    _writeUnsignedSchemaCompatibleField13,
  ],
  compatibleFactory: UnsignedSchemaCompatible.new,
  compatibleReadersBySlot: <_UnsignedSchemaCompatibleFieldReader>[
    _readUnsignedSchemaCompatibleField0,
    _readUnsignedSchemaCompatibleField1,
    _readUnsignedSchemaCompatibleField2,
    _readUnsignedSchemaCompatibleField3,
    _readUnsignedSchemaCompatibleField4,
    _readUnsignedSchemaCompatibleField5,
    _readUnsignedSchemaCompatibleField6,
    _readUnsignedSchemaCompatibleField7,
    _readUnsignedSchemaCompatibleField8,
    _readUnsignedSchemaCompatibleField9,
    _readUnsignedSchemaCompatibleField10,
    _readUnsignedSchemaCompatibleField11,
    _readUnsignedSchemaCompatibleField12,
    _readUnsignedSchemaCompatibleField13,
  ],
  type: UnsignedSchemaCompatible,
  serializerFactory: _UnsignedSchemaCompatibleForySerializer.new,
  evolving: true,
  fields: _unsignedSchemaCompatibleForyFieldInfo,
);

final class _UnsignedSchemaCompatibleForySerializer
    extends Serializer<UnsignedSchemaCompatible> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _UnsignedSchemaCompatibleForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaCompatibleForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _unsignedSchemaCompatibleForyRegistration,
    );
  }

  @override
  void write(WriteContext context, UnsignedSchemaCompatible value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 40);
      cursor0.writeUint64(value.u64FixedField2);
      cursor0.writeUint32(value.u32FixedField2.value);
      cursor0.writeUint16(value.u16Field2.value);
      cursor0.writeUint8(value.u8Field2.value);
      cursor0.writeTaggedUint64(value.u64TaggedField2);
      cursor0.writeVarUint64(value.u64VarField2);
      cursor0.writeVarUint32(value.u32VarField2.value);
      cursor0.finish();
      writeGeneratedStructFieldInfoValue(
          context, fields[7], value.u64FixedField1);
      writeGeneratedStructFieldInfoValue(
          context, fields[8], value.u32FixedField1);
      writeGeneratedStructFieldInfoValue(context, fields[9], value.u16Field1);
      writeGeneratedStructFieldInfoValue(context, fields[10], value.u8Field1);
      writeGeneratedStructFieldInfoValue(
          context, fields[11], value.u64TaggedField1);
      writeGeneratedStructFieldInfoValue(
          context, fields[12], value.u64VarField1);
      writeGeneratedStructFieldInfoValue(
          context, fields[13], value.u32VarField1);
      return;
    }
    final writers =
        _unsignedSchemaCompatibleForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  UnsignedSchemaCompatible read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = UnsignedSchemaCompatible();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
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
          readGeneratedStructFieldInfoValue(
              context, fields[7], value.u64FixedField1),
          value.u64FixedField1);
      value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
          readGeneratedStructFieldInfoValue(
              context, fields[8], value.u32FixedField1),
          value.u32FixedField1);
      value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
          readGeneratedStructFieldInfoValue(
              context, fields[9], value.u16Field1),
          value.u16Field1);
      value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
          readGeneratedStructFieldInfoValue(
              context, fields[10], value.u8Field1),
          value.u8Field1);
      value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
          readGeneratedStructFieldInfoValue(
              context, fields[11], value.u64TaggedField1),
          value.u64TaggedField1);
      value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
          readGeneratedStructFieldInfoValue(
              context, fields[12], value.u64VarField1),
          value.u64VarField1);
      value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
          readGeneratedStructFieldInfoValue(
              context, fields[13], value.u32VarField1),
          value.u32VarField1);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawUnsignedSchemaCompatible0 = slots.valueForSlot(0);
      value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
          rawUnsignedSchemaCompatible0 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible0.id)
              : rawUnsignedSchemaCompatible0,
          value.u64FixedField2);
    }
    if (slots.containsSlot(1)) {
      final rawUnsignedSchemaCompatible1 = slots.valueForSlot(1);
      value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
          rawUnsignedSchemaCompatible1 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible1.id)
              : rawUnsignedSchemaCompatible1,
          value.u32FixedField2);
    }
    if (slots.containsSlot(2)) {
      final rawUnsignedSchemaCompatible2 = slots.valueForSlot(2);
      value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
          rawUnsignedSchemaCompatible2 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible2.id)
              : rawUnsignedSchemaCompatible2,
          value.u16Field2);
    }
    if (slots.containsSlot(3)) {
      final rawUnsignedSchemaCompatible3 = slots.valueForSlot(3);
      value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
          rawUnsignedSchemaCompatible3 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible3.id)
              : rawUnsignedSchemaCompatible3,
          value.u8Field2);
    }
    if (slots.containsSlot(4)) {
      final rawUnsignedSchemaCompatible4 = slots.valueForSlot(4);
      value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
          rawUnsignedSchemaCompatible4 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible4.id)
              : rawUnsignedSchemaCompatible4,
          value.u64TaggedField2);
    }
    if (slots.containsSlot(5)) {
      final rawUnsignedSchemaCompatible5 = slots.valueForSlot(5);
      value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
          rawUnsignedSchemaCompatible5 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible5.id)
              : rawUnsignedSchemaCompatible5,
          value.u64VarField2);
    }
    if (slots.containsSlot(6)) {
      final rawUnsignedSchemaCompatible6 = slots.valueForSlot(6);
      value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
          rawUnsignedSchemaCompatible6 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible6.id)
              : rawUnsignedSchemaCompatible6,
          value.u32VarField2);
    }
    if (slots.containsSlot(7)) {
      final rawUnsignedSchemaCompatible7 = slots.valueForSlot(7);
      value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
          rawUnsignedSchemaCompatible7 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible7.id)
              : rawUnsignedSchemaCompatible7,
          value.u64FixedField1);
    }
    if (slots.containsSlot(8)) {
      final rawUnsignedSchemaCompatible8 = slots.valueForSlot(8);
      value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
          rawUnsignedSchemaCompatible8 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible8.id)
              : rawUnsignedSchemaCompatible8,
          value.u32FixedField1);
    }
    if (slots.containsSlot(9)) {
      final rawUnsignedSchemaCompatible9 = slots.valueForSlot(9);
      value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
          rawUnsignedSchemaCompatible9 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible9.id)
              : rawUnsignedSchemaCompatible9,
          value.u16Field1);
    }
    if (slots.containsSlot(10)) {
      final rawUnsignedSchemaCompatible10 = slots.valueForSlot(10);
      value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
          rawUnsignedSchemaCompatible10 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible10.id)
              : rawUnsignedSchemaCompatible10,
          value.u8Field1);
    }
    if (slots.containsSlot(11)) {
      final rawUnsignedSchemaCompatible11 = slots.valueForSlot(11);
      value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
          rawUnsignedSchemaCompatible11 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible11.id)
              : rawUnsignedSchemaCompatible11,
          value.u64TaggedField1);
    }
    if (slots.containsSlot(12)) {
      final rawUnsignedSchemaCompatible12 = slots.valueForSlot(12);
      value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
          rawUnsignedSchemaCompatible12 is DeferredReadRef
              ? context.getReadRef(rawUnsignedSchemaCompatible12.id)
              : rawUnsignedSchemaCompatible12,
          value.u64VarField1);
    }
    if (slots.containsSlot(13)) {
      final rawUnsignedSchemaCompatible13 = slots.valueForSlot(13);
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
