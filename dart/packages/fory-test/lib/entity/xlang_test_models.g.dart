// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'xlang_test_models.dart';

// **************************************************************************
// ForyGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: invalid_use_of_internal_member

abstract base class _GeneratedStructSerializer<T> extends Serializer<T> {
  const _GeneratedStructSerializer();
}

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

const List<Map<String, Object?>> _twoEnumFieldStructEvolutionForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': TestEnum,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': TestEnum,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _twoEnumFieldStructEvolutionForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _twoEnumFieldStructEvolutionForyFieldMetadata[0]),
    generatedField(1, _twoEnumFieldStructEvolutionForyFieldMetadata[1]),
  ],
);

final class _TwoEnumFieldStructEvolutionForySerializer
    extends _GeneratedStructSerializer<TwoEnumFieldStructEvolution> {
  const _TwoEnumFieldStructEvolutionForySerializer();
  @override
  void write(WriteContext context, TwoEnumFieldStructEvolution value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          case 1:
            writeGeneratedField(context, field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _twoEnumFieldStructEvolutionForyFields[0], value.f1);
    writeGeneratedField(
        context, _twoEnumFieldStructEvolutionForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStructEvolution read(ReadContext context) {
    final value = TwoEnumFieldStructEvolution();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructEvolutionF1(
        readGeneratedField<Object?>(
            context, _twoEnumFieldStructEvolutionForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructEvolutionF2(
        readGeneratedField<Object?>(
            context, _twoEnumFieldStructEvolutionForyFields[1], value.f2),
        value.f2);
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

const List<Map<String, Object?>> _itemForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _itemForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _itemForyFieldMetadata[0]),
  ],
);

final class _ItemForySerializer extends _GeneratedStructSerializer<Item> {
  const _ItemForySerializer();
  @override
  void write(WriteContext context, Item value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _itemForyFields[0], value.name);
  }

  @override
  Item read(ReadContext context) {
    final value = Item();
    context.reference(value);
    value.name = _readItemName(
        readGeneratedField<Object?>(context, _itemForyFields[0], value.name),
        value.name);
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

const List<Map<String, Object?>> _simpleStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f7',
    'identifier': 'f7',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f8',
    'identifier': 'f8',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'last',
    'identifier': 'last',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f4',
    'identifier': 'f4',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f6',
    'identifier': 'f6',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': Int32,
          'typeId': 5,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': double,
          'typeId': 20,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'f3',
    'identifier': 'f3',
    'id': null,
    'shape': <String, Object?>{
      'type': Item,
      'typeId': 28,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f5',
    'identifier': 'f5',
    'id': null,
    'shape': <String, Object?>{
      'type': Color,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _simpleStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _simpleStructForyFieldMetadata[0]),
    generatedField(1, _simpleStructForyFieldMetadata[1]),
    generatedField(2, _simpleStructForyFieldMetadata[2]),
    generatedField(3, _simpleStructForyFieldMetadata[3]),
    generatedField(4, _simpleStructForyFieldMetadata[4]),
    generatedField(5, _simpleStructForyFieldMetadata[5]),
    generatedField(6, _simpleStructForyFieldMetadata[6]),
    generatedField(7, _simpleStructForyFieldMetadata[7]),
    generatedField(8, _simpleStructForyFieldMetadata[8]),
  ],
);

final class _SimpleStructForySerializer
    extends _GeneratedStructSerializer<SimpleStruct> {
  const _SimpleStructForySerializer();
  @override
  void write(WriteContext context, SimpleStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f2);
            break;
          case 1:
            writeGeneratedField(context, field, value.f7);
            break;
          case 2:
            writeGeneratedField(context, field, value.f8);
            break;
          case 3:
            writeGeneratedField(context, field, value.last);
            break;
          case 4:
            writeGeneratedField(context, field, value.f4);
            break;
          case 5:
            writeGeneratedField(context, field, value.f6);
            break;
          case 6:
            writeGeneratedField(context, field, value.f1);
            break;
          case 7:
            writeGeneratedField(context, field, value.f3);
            break;
          case 8:
            writeGeneratedField(context, field, value.f5);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _simpleStructForyFields[0], value.f2);
    writeGeneratedField(context, _simpleStructForyFields[1], value.f7);
    writeGeneratedField(context, _simpleStructForyFields[2], value.f8);
    writeGeneratedField(context, _simpleStructForyFields[3], value.last);
    writeGeneratedField(context, _simpleStructForyFields[4], value.f4);
    writeGeneratedField(context, _simpleStructForyFields[5], value.f6);
    writeGeneratedField(context, _simpleStructForyFields[6], value.f1);
    writeGeneratedField(context, _simpleStructForyFields[7], value.f3);
    writeGeneratedField(context, _simpleStructForyFields[8], value.f5);
  }

  @override
  SimpleStruct read(ReadContext context) {
    final value = SimpleStruct();
    context.reference(value);
    value.f2 = _readSimpleStructF2(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[0], value.f2),
        value.f2);
    value.f7 = _readSimpleStructF7(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[1], value.f7),
        value.f7);
    value.f8 = _readSimpleStructF8(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[2], value.f8),
        value.f8);
    value.last = _readSimpleStructLast(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[3], value.last),
        value.last);
    value.f4 = _readSimpleStructF4(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[4], value.f4),
        value.f4);
    value.f6 = _readSimpleStructF6(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[5], value.f6),
        value.f6);
    value.f1 = _readSimpleStructF1(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[6], value.f1),
        value.f1);
    value.f3 = _readSimpleStructF3(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[7], value.f3),
        value.f3);
    value.f5 = _readSimpleStructF5(
        readGeneratedField<Object?>(
            context, _simpleStructForyFields[8], value.f5),
        value.f5);
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

List<String> _readSimpleStructF6(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError('Received null for non-nullable field f6.')))
      : List<String>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as String));
}

Map<Int32?, double?> _readSimpleStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<Int32?, double?>
          : (throw StateError('Received null for non-nullable field f1.')))
      : Map<Int32?, double?>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? null as Int32?
              : key == null
                  ? (throw StateError('Received null for non-nullable value.'))
                  : key as Int32,
          value == null
              ? null as double?
              : value == null
                  ? (throw StateError('Received null for non-nullable value.'))
                  : value as double)));
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

const List<Map<String, Object?>> _evolvingOverrideStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _evolvingOverrideStructForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _evolvingOverrideStructForyFieldMetadata[0]),
  ],
);

final class _EvolvingOverrideStructForySerializer
    extends _GeneratedStructSerializer<EvolvingOverrideStruct> {
  const _EvolvingOverrideStructForySerializer();
  @override
  void write(WriteContext context, EvolvingOverrideStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _evolvingOverrideStructForyFields[0], value.f1);
  }

  @override
  EvolvingOverrideStruct read(ReadContext context) {
    final value = EvolvingOverrideStruct();
    context.reference(value);
    value.f1 = _readEvolvingOverrideStructF1(
        readGeneratedField<Object?>(
            context, _evolvingOverrideStructForyFields[0], value.f1),
        value.f1);
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

const List<Map<String, Object?>> _fixedOverrideStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _fixedOverrideStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _fixedOverrideStructForyFieldMetadata[0]),
  ],
);

final class _FixedOverrideStructForySerializer
    extends _GeneratedStructSerializer<FixedOverrideStruct> {
  const _FixedOverrideStructForySerializer();
  @override
  void write(WriteContext context, FixedOverrideStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _fixedOverrideStructForyFields[0], value.f1);
  }

  @override
  FixedOverrideStruct read(ReadContext context) {
    final value = FixedOverrideStruct();
    context.reference(value);
    value.f1 = _readFixedOverrideStructF1(
        readGeneratedField<Object?>(
            context, _fixedOverrideStructForyFields[0], value.f1),
        value.f1);
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

const List<Map<String, Object?>> _item1ForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f3',
    'identifier': 'f3',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f4',
    'identifier': 'f4',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f5',
    'identifier': 'f5',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f6',
    'identifier': 'f6',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _item1ForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _item1ForyFieldMetadata[0]),
    generatedField(1, _item1ForyFieldMetadata[1]),
    generatedField(2, _item1ForyFieldMetadata[2]),
    generatedField(3, _item1ForyFieldMetadata[3]),
    generatedField(4, _item1ForyFieldMetadata[4]),
    generatedField(5, _item1ForyFieldMetadata[5]),
  ],
);

final class _Item1ForySerializer extends _GeneratedStructSerializer<Item1> {
  const _Item1ForySerializer();
  @override
  void write(WriteContext context, Item1 value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          case 1:
            writeGeneratedField(context, field, value.f2);
            break;
          case 2:
            writeGeneratedField(context, field, value.f3);
            break;
          case 3:
            writeGeneratedField(context, field, value.f4);
            break;
          case 4:
            writeGeneratedField(context, field, value.f5);
            break;
          case 5:
            writeGeneratedField(context, field, value.f6);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _item1ForyFields[0], value.f1);
    writeGeneratedField(context, _item1ForyFields[1], value.f2);
    writeGeneratedField(context, _item1ForyFields[2], value.f3);
    writeGeneratedField(context, _item1ForyFields[3], value.f4);
    writeGeneratedField(context, _item1ForyFields[4], value.f5);
    writeGeneratedField(context, _item1ForyFields[5], value.f6);
  }

  @override
  Item1 read(ReadContext context) {
    final value = Item1();
    context.reference(value);
    value.f1 = _readItem1F1(
        readGeneratedField<Object?>(context, _item1ForyFields[0], value.f1),
        value.f1);
    value.f2 = _readItem1F2(
        readGeneratedField<Object?>(context, _item1ForyFields[1], value.f2),
        value.f2);
    value.f3 = _readItem1F3(
        readGeneratedField<Object?>(context, _item1ForyFields[2], value.f3),
        value.f3);
    value.f4 = _readItem1F4(
        readGeneratedField<Object?>(context, _item1ForyFields[3], value.f4),
        value.f4);
    value.f5 = _readItem1F5(
        readGeneratedField<Object?>(context, _item1ForyFields[4], value.f5),
        value.f5);
    value.f6 = _readItem1F6(
        readGeneratedField<Object?>(context, _item1ForyFields[5], value.f6),
        value.f6);
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

const List<Map<String, Object?>> _structWithUnion2ForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'union',
    'identifier': 'union',
    'id': null,
    'shape': <String, Object?>{
      'type': Union2,
      'typeId': 28,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _structWithUnion2ForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _structWithUnion2ForyFieldMetadata[0]),
  ],
);

final class _StructWithUnion2ForySerializer
    extends _GeneratedStructSerializer<StructWithUnion2> {
  const _StructWithUnion2ForySerializer();
  @override
  void write(WriteContext context, StructWithUnion2 value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.union);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _structWithUnion2ForyFields[0], value.union);
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final value = StructWithUnion2();
    context.reference(value);
    value.union = _readStructWithUnion2Union(
        readGeneratedField<Object?>(
            context, _structWithUnion2ForyFields[0], value.union),
        value.union);
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

const List<Map<String, Object?>> _structWithListForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'items',
    'identifier': 'items',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _structWithListForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _structWithListForyFieldMetadata[0]),
  ],
);

final class _StructWithListForySerializer
    extends _GeneratedStructSerializer<StructWithList> {
  const _StructWithListForySerializer();
  @override
  void write(WriteContext context, StructWithList value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.items);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _structWithListForyFields[0], value.items);
  }

  @override
  StructWithList read(ReadContext context) {
    final value = StructWithList();
    context.reference(value);
    value.items = _readStructWithListItems(
        readGeneratedField<Object?>(
            context, _structWithListForyFields[0], value.items),
        value.items);
    return value;
  }
}

List<String?> _readStructWithListItems(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String?>
          : (throw StateError('Received null for non-nullable field items.')))
      : List<String?>.of(((value as List)).map((item) => item == null
          ? null as String?
          : item == null
              ? (throw StateError('Received null for non-nullable value.'))
              : item as String));
}

const List<Map<String, Object?>> _structWithMapForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'data',
    'identifier': 'data',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _structWithMapForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _structWithMapForyFieldMetadata[0]),
  ],
);

final class _StructWithMapForySerializer
    extends _GeneratedStructSerializer<StructWithMap> {
  const _StructWithMapForySerializer();
  @override
  void write(WriteContext context, StructWithMap value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.data);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _structWithMapForyFields[0], value.data);
  }

  @override
  StructWithMap read(ReadContext context) {
    final value = StructWithMap();
    context.reference(value);
    value.data = _readStructWithMapData(
        readGeneratedField<Object?>(
            context, _structWithMapForyFields[0], value.data),
        value.data);
    return value;
  }
}

Map<String?, String?> _readStructWithMapData(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String?, String?>
          : (throw StateError('Received null for non-nullable field data.')))
      : Map<String?, String?>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? null as String?
              : key == null
                  ? (throw StateError('Received null for non-nullable value.'))
                  : key as String,
          value == null
              ? null as String?
              : value == null
                  ? (throw StateError('Received null for non-nullable value.'))
                  : value as String)));
}

const List<Map<String, Object?>> _myStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'id',
    'identifier': 'id',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _myStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _myStructForyFieldMetadata[0]),
  ],
);

final class _MyStructForySerializer
    extends _GeneratedStructSerializer<MyStruct> {
  const _MyStructForySerializer();
  @override
  void write(WriteContext context, MyStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.id);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _myStructForyFields[0], value.id);
  }

  @override
  MyStruct read(ReadContext context) {
    final value = MyStruct();
    context.reference(value);
    value.id = _readMyStructId(
        readGeneratedField<Object?>(context, _myStructForyFields[0], value.id),
        value.id);
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

const List<Map<String, Object?>> _myWrapperForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'color',
    'identifier': 'color',
    'id': null,
    'shape': <String, Object?>{
      'type': Color,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'myExt',
    'identifier': 'my_ext',
    'id': null,
    'shape': <String, Object?>{
      'type': MyExt,
      'typeId': 28,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'myStruct',
    'identifier': 'my_struct',
    'id': null,
    'shape': <String, Object?>{
      'type': MyStruct,
      'typeId': 28,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _myWrapperForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _myWrapperForyFieldMetadata[0]),
    generatedField(1, _myWrapperForyFieldMetadata[1]),
    generatedField(2, _myWrapperForyFieldMetadata[2]),
  ],
);

final class _MyWrapperForySerializer
    extends _GeneratedStructSerializer<MyWrapper> {
  const _MyWrapperForySerializer();
  @override
  void write(WriteContext context, MyWrapper value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.color);
            break;
          case 1:
            writeGeneratedField(context, field, value.myExt);
            break;
          case 2:
            writeGeneratedField(context, field, value.myStruct);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _myWrapperForyFields[0], value.color);
    writeGeneratedField(context, _myWrapperForyFields[1], value.myExt);
    writeGeneratedField(context, _myWrapperForyFields[2], value.myStruct);
  }

  @override
  MyWrapper read(ReadContext context) {
    final value = MyWrapper();
    context.reference(value);
    value.color = _readMyWrapperColor(
        readGeneratedField<Object?>(
            context, _myWrapperForyFields[0], value.color),
        value.color);
    value.myExt = _readMyWrapperMyExt(
        readGeneratedField<Object?>(
            context, _myWrapperForyFields[1], value.myExt),
        value.myExt);
    value.myStruct = _readMyWrapperMyStruct(
        readGeneratedField<Object?>(
            context, _myWrapperForyFields[2], value.myStruct),
        value.myStruct);
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

const List<Map<String, Object?>> _emptyWrapperForyFieldMetadata =
    <Map<String, Object?>>[];

final List<Object> _emptyWrapperForyFields = List<Object>.unmodifiable(
  <Object>[],
);

final class _EmptyWrapperForySerializer
    extends _GeneratedStructSerializer<EmptyWrapper> {
  const _EmptyWrapperForySerializer();
  @override
  void write(WriteContext context, EmptyWrapper value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          default:
            break;
        }
      }
      return;
    }
  }

  @override
  EmptyWrapper read(ReadContext context) {
    final value = EmptyWrapper();
    context.reference(value);
    return value;
  }
}

const List<Map<String, Object?>> _versionCheckStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f3',
    'identifier': 'f3',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _versionCheckStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _versionCheckStructForyFieldMetadata[0]),
    generatedField(1, _versionCheckStructForyFieldMetadata[1]),
    generatedField(2, _versionCheckStructForyFieldMetadata[2]),
  ],
);

final class _VersionCheckStructForySerializer
    extends _GeneratedStructSerializer<VersionCheckStruct> {
  const _VersionCheckStructForySerializer();
  @override
  void write(WriteContext context, VersionCheckStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f3);
            break;
          case 1:
            writeGeneratedField(context, field, value.f1);
            break;
          case 2:
            writeGeneratedField(context, field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _versionCheckStructForyFields[0], value.f3);
    writeGeneratedField(context, _versionCheckStructForyFields[1], value.f1);
    writeGeneratedField(context, _versionCheckStructForyFields[2], value.f2);
  }

  @override
  VersionCheckStruct read(ReadContext context) {
    final value = VersionCheckStruct();
    context.reference(value);
    value.f3 = _readVersionCheckStructF3(
        readGeneratedField<Object?>(
            context, _versionCheckStructForyFields[0], value.f3),
        value.f3);
    value.f1 = _readVersionCheckStructF1(
        readGeneratedField<Object?>(
            context, _versionCheckStructForyFields[1], value.f1),
        value.f1);
    value.f2 = _readVersionCheckStructF2(
        readGeneratedField<Object?>(
            context, _versionCheckStructForyFields[2], value.f2),
        value.f2);
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

const List<Map<String, Object?>> _dogForyFieldMetadata = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'age',
    'identifier': 'age',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _dogForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _dogForyFieldMetadata[0]),
    generatedField(1, _dogForyFieldMetadata[1]),
  ],
);

final class _DogForySerializer extends _GeneratedStructSerializer<Dog> {
  const _DogForySerializer();
  @override
  void write(WriteContext context, Dog value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.age);
            break;
          case 1:
            writeGeneratedField(context, field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _dogForyFields[0], value.age);
    writeGeneratedField(context, _dogForyFields[1], value.name);
  }

  @override
  Dog read(ReadContext context) {
    final value = Dog();
    context.reference(value);
    value.age = _readDogAge(
        readGeneratedField<Object?>(context, _dogForyFields[0], value.age),
        value.age);
    value.name = _readDogName(
        readGeneratedField<Object?>(context, _dogForyFields[1], value.name),
        value.name);
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

const List<Map<String, Object?>> _catForyFieldMetadata = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'age',
    'identifier': 'age',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'lives',
    'identifier': 'lives',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _catForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _catForyFieldMetadata[0]),
    generatedField(1, _catForyFieldMetadata[1]),
  ],
);

final class _CatForySerializer extends _GeneratedStructSerializer<Cat> {
  const _CatForySerializer();
  @override
  void write(WriteContext context, Cat value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.age);
            break;
          case 1:
            writeGeneratedField(context, field, value.lives);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _catForyFields[0], value.age);
    writeGeneratedField(context, _catForyFields[1], value.lives);
  }

  @override
  Cat read(ReadContext context) {
    final value = Cat();
    context.reference(value);
    value.age = _readCatAge(
        readGeneratedField<Object?>(context, _catForyFields[0], value.age),
        value.age);
    value.lives = _readCatLives(
        readGeneratedField<Object?>(context, _catForyFields[1], value.lives),
        value.lives);
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

const List<Map<String, Object?>> _animalListHolderForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'animals',
    'identifier': 'animals',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': Animal,
          'typeId': 28,
          'nullable': true,
          'ref': false,
          'dynamic': true,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _animalListHolderForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _animalListHolderForyFieldMetadata[0]),
  ],
);

final class _AnimalListHolderForySerializer
    extends _GeneratedStructSerializer<AnimalListHolder> {
  const _AnimalListHolderForySerializer();
  @override
  void write(WriteContext context, AnimalListHolder value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.animals);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _animalListHolderForyFields[0], value.animals);
  }

  @override
  AnimalListHolder read(ReadContext context) {
    final value = AnimalListHolder();
    context.reference(value);
    value.animals = _readAnimalListHolderAnimals(
        readGeneratedField<Object?>(
            context, _animalListHolderForyFields[0], value.animals),
        value.animals);
    return value;
  }
}

List<Animal> _readAnimalListHolderAnimals(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<Animal>
          : (throw StateError('Received null for non-nullable field animals.')))
      : List<Animal>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as Animal));
}

const List<Map<String, Object?>> _animalMapHolderForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'animalMap',
    'identifier': 'animal_map',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': Animal,
          'typeId': 28,
          'nullable': true,
          'ref': false,
          'dynamic': true,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _animalMapHolderForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _animalMapHolderForyFieldMetadata[0]),
  ],
);

final class _AnimalMapHolderForySerializer
    extends _GeneratedStructSerializer<AnimalMapHolder> {
  const _AnimalMapHolderForySerializer();
  @override
  void write(WriteContext context, AnimalMapHolder value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.animalMap);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _animalMapHolderForyFields[0], value.animalMap);
  }

  @override
  AnimalMapHolder read(ReadContext context) {
    final value = AnimalMapHolder();
    context.reference(value);
    value.animalMap = _readAnimalMapHolderAnimalMap(
        readGeneratedField<Object?>(
            context, _animalMapHolderForyFields[0], value.animalMap),
        value.animalMap);
    return value;
  }
}

Map<String, Animal> _readAnimalMapHolderAnimalMap(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, Animal>
          : (throw StateError(
              'Received null for non-nullable field animalMap.')))
      : Map<String, Animal>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? (throw StateError('Received null for non-nullable map key.'))
              : key as String,
          value == null
              ? (throw StateError('Received null for non-nullable map value.'))
              : value as Animal)));
}

const List<Map<String, Object?>> _emptyStructForyFieldMetadata =
    <Map<String, Object?>>[];

final List<Object> _emptyStructForyFields = List<Object>.unmodifiable(
  <Object>[],
);

final class _EmptyStructForySerializer
    extends _GeneratedStructSerializer<EmptyStruct> {
  const _EmptyStructForySerializer();
  @override
  void write(WriteContext context, EmptyStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          default:
            break;
        }
      }
      return;
    }
  }

  @override
  EmptyStruct read(ReadContext context) {
    final value = EmptyStruct();
    context.reference(value);
    return value;
  }
}

const List<Map<String, Object?>> _oneStringFieldStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _oneStringFieldStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _oneStringFieldStructForyFieldMetadata[0]),
  ],
);

final class _OneStringFieldStructForySerializer
    extends _GeneratedStructSerializer<OneStringFieldStruct> {
  const _OneStringFieldStructForySerializer();
  @override
  void write(WriteContext context, OneStringFieldStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _oneStringFieldStructForyFields[0], value.f1);
  }

  @override
  OneStringFieldStruct read(ReadContext context) {
    final value = OneStringFieldStruct();
    context.reference(value);
    value.f1 = _readOneStringFieldStructF1(
        readGeneratedField<Object?>(
            context, _oneStringFieldStructForyFields[0], value.f1),
        value.f1);
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

const List<Map<String, Object?>> _twoStringFieldStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _twoStringFieldStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _twoStringFieldStructForyFieldMetadata[0]),
    generatedField(1, _twoStringFieldStructForyFieldMetadata[1]),
  ],
);

final class _TwoStringFieldStructForySerializer
    extends _GeneratedStructSerializer<TwoStringFieldStruct> {
  const _TwoStringFieldStructForySerializer();
  @override
  void write(WriteContext context, TwoStringFieldStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          case 1:
            writeGeneratedField(context, field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _twoStringFieldStructForyFields[0], value.f1);
    writeGeneratedField(context, _twoStringFieldStructForyFields[1], value.f2);
  }

  @override
  TwoStringFieldStruct read(ReadContext context) {
    final value = TwoStringFieldStruct();
    context.reference(value);
    value.f1 = _readTwoStringFieldStructF1(
        readGeneratedField<Object?>(
            context, _twoStringFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoStringFieldStructF2(
        readGeneratedField<Object?>(
            context, _twoStringFieldStructForyFields[1], value.f2),
        value.f2);
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

const List<Map<String, Object?>> _oneEnumFieldStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': TestEnum,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _oneEnumFieldStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _oneEnumFieldStructForyFieldMetadata[0]),
  ],
);

final class _OneEnumFieldStructForySerializer
    extends _GeneratedStructSerializer<OneEnumFieldStruct> {
  const _OneEnumFieldStructForySerializer();
  @override
  void write(WriteContext context, OneEnumFieldStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _oneEnumFieldStructForyFields[0], value.f1);
  }

  @override
  OneEnumFieldStruct read(ReadContext context) {
    final value = OneEnumFieldStruct();
    context.reference(value);
    value.f1 = _readOneEnumFieldStructF1(
        readGeneratedField<Object?>(
            context, _oneEnumFieldStructForyFields[0], value.f1),
        value.f1);
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

const List<Map<String, Object?>> _twoEnumFieldStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'f1',
    'identifier': 'f1',
    'id': null,
    'shape': <String, Object?>{
      'type': TestEnum,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'f2',
    'identifier': 'f2',
    'id': null,
    'shape': <String, Object?>{
      'type': TestEnum,
      'typeId': 25,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _twoEnumFieldStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _twoEnumFieldStructForyFieldMetadata[0]),
    generatedField(1, _twoEnumFieldStructForyFieldMetadata[1]),
  ],
);

final class _TwoEnumFieldStructForySerializer
    extends _GeneratedStructSerializer<TwoEnumFieldStruct> {
  const _TwoEnumFieldStructForySerializer();
  @override
  void write(WriteContext context, TwoEnumFieldStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.f1);
            break;
          case 1:
            writeGeneratedField(context, field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _twoEnumFieldStructForyFields[0], value.f1);
    writeGeneratedField(context, _twoEnumFieldStructForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStruct read(ReadContext context) {
    final value = TwoEnumFieldStruct();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructF1(
        readGeneratedField<Object?>(
            context, _twoEnumFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructF2(
        readGeneratedField<Object?>(
            context, _twoEnumFieldStructForyFields[1], value.f2),
        value.f2);
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

const List<Map<String, Object?>>
    _nullableComprehensiveSchemaConsistentForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'doubleField',
    'identifier': 'double_field',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'floatField',
    'identifier': 'float_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Float32,
      'typeId': 19,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'shortField',
    'identifier': 'short_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int16,
      'typeId': 3,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'byteField',
    'identifier': 'byte_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int8,
      'typeId': 2,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boolField',
    'identifier': 'bool_field',
    'id': null,
    'shape': <String, Object?>{
      'type': bool,
      'typeId': 1,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'longField',
    'identifier': 'long_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 7,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'intField',
    'identifier': 'int_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableDouble',
    'identifier': 'nullable_double',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableFloat',
    'identifier': 'nullable_float',
    'id': null,
    'shape': <String, Object?>{
      'type': Float32,
      'typeId': 19,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableBool',
    'identifier': 'nullable_bool',
    'id': null,
    'shape': <String, Object?>{
      'type': bool,
      'typeId': 1,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableLong',
    'identifier': 'nullable_long',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 7,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableInt',
    'identifier': 'nullable_int',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableString',
    'identifier': 'nullable_string',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'stringField',
    'identifier': 'string_field',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'listField',
    'identifier': 'list_field',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableList',
    'identifier': 'nullable_list',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableSet',
    'identifier': 'nullable_set',
    'id': null,
    'shape': <String, Object?>{
      'type': Set,
      'typeId': 23,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'setField',
    'identifier': 'set_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Set,
      'typeId': 23,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'mapField',
    'identifier': 'map_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableMap',
    'identifier': 'nullable_map',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _nullableComprehensiveSchemaConsistentForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(
        0, _nullableComprehensiveSchemaConsistentForyFieldMetadata[0]),
    generatedField(
        1, _nullableComprehensiveSchemaConsistentForyFieldMetadata[1]),
    generatedField(
        2, _nullableComprehensiveSchemaConsistentForyFieldMetadata[2]),
    generatedField(
        3, _nullableComprehensiveSchemaConsistentForyFieldMetadata[3]),
    generatedField(
        4, _nullableComprehensiveSchemaConsistentForyFieldMetadata[4]),
    generatedField(
        5, _nullableComprehensiveSchemaConsistentForyFieldMetadata[5]),
    generatedField(
        6, _nullableComprehensiveSchemaConsistentForyFieldMetadata[6]),
    generatedField(
        7, _nullableComprehensiveSchemaConsistentForyFieldMetadata[7]),
    generatedField(
        8, _nullableComprehensiveSchemaConsistentForyFieldMetadata[8]),
    generatedField(
        9, _nullableComprehensiveSchemaConsistentForyFieldMetadata[9]),
    generatedField(
        10, _nullableComprehensiveSchemaConsistentForyFieldMetadata[10]),
    generatedField(
        11, _nullableComprehensiveSchemaConsistentForyFieldMetadata[11]),
    generatedField(
        12, _nullableComprehensiveSchemaConsistentForyFieldMetadata[12]),
    generatedField(
        13, _nullableComprehensiveSchemaConsistentForyFieldMetadata[13]),
    generatedField(
        14, _nullableComprehensiveSchemaConsistentForyFieldMetadata[14]),
    generatedField(
        15, _nullableComprehensiveSchemaConsistentForyFieldMetadata[15]),
    generatedField(
        16, _nullableComprehensiveSchemaConsistentForyFieldMetadata[16]),
    generatedField(
        17, _nullableComprehensiveSchemaConsistentForyFieldMetadata[17]),
    generatedField(
        18, _nullableComprehensiveSchemaConsistentForyFieldMetadata[18]),
    generatedField(
        19, _nullableComprehensiveSchemaConsistentForyFieldMetadata[19]),
  ],
);

final class _NullableComprehensiveSchemaConsistentForySerializer
    extends _GeneratedStructSerializer<NullableComprehensiveSchemaConsistent> {
  const _NullableComprehensiveSchemaConsistentForySerializer();
  @override
  void write(
      WriteContext context, NullableComprehensiveSchemaConsistent value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.doubleField);
            break;
          case 1:
            writeGeneratedField(context, field, value.floatField);
            break;
          case 2:
            writeGeneratedField(context, field, value.shortField);
            break;
          case 3:
            writeGeneratedField(context, field, value.byteField);
            break;
          case 4:
            writeGeneratedField(context, field, value.boolField);
            break;
          case 5:
            writeGeneratedField(context, field, value.longField);
            break;
          case 6:
            writeGeneratedField(context, field, value.intField);
            break;
          case 7:
            writeGeneratedField(context, field, value.nullableDouble);
            break;
          case 8:
            writeGeneratedField(context, field, value.nullableFloat);
            break;
          case 9:
            writeGeneratedField(context, field, value.nullableBool);
            break;
          case 10:
            writeGeneratedField(context, field, value.nullableLong);
            break;
          case 11:
            writeGeneratedField(context, field, value.nullableInt);
            break;
          case 12:
            writeGeneratedField(context, field, value.nullableString);
            break;
          case 13:
            writeGeneratedField(context, field, value.stringField);
            break;
          case 14:
            writeGeneratedField(context, field, value.listField);
            break;
          case 15:
            writeGeneratedField(context, field, value.nullableList);
            break;
          case 16:
            writeGeneratedField(context, field, value.nullableSet);
            break;
          case 17:
            writeGeneratedField(context, field, value.setField);
            break;
          case 18:
            writeGeneratedField(context, field, value.mapField);
            break;
          case 19:
            writeGeneratedField(context, field, value.nullableMap);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[0], value.doubleField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[1], value.floatField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[2], value.shortField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[3], value.byteField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[4], value.boolField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[5], value.longField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[6], value.intField);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[7],
        value.nullableDouble);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[8],
        value.nullableFloat);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[9],
        value.nullableBool);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[10],
        value.nullableLong);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[11],
        value.nullableInt);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[12],
        value.nullableString);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[13],
        value.stringField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[14], value.listField);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[15],
        value.nullableList);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[16],
        value.nullableSet);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[17], value.setField);
    writeGeneratedField(context,
        _nullableComprehensiveSchemaConsistentForyFields[18], value.mapField);
    writeGeneratedField(
        context,
        _nullableComprehensiveSchemaConsistentForyFields[19],
        value.nullableMap);
  }

  @override
  NullableComprehensiveSchemaConsistent read(ReadContext context) {
    final value = NullableComprehensiveSchemaConsistent();
    context.reference(value);
    value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[0],
            value.doubleField),
        value.doubleField);
    value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[1],
            value.floatField),
        value.floatField);
    value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[2],
            value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[3],
            value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[4],
            value.boolField),
        value.boolField);
    value.longField = _readNullableComprehensiveSchemaConsistentLongField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[5],
            value.longField),
        value.longField);
    value.intField = _readNullableComprehensiveSchemaConsistentIntField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[6],
            value.intField),
        value.intField);
    value.nullableDouble =
        _readNullableComprehensiveSchemaConsistentNullableDouble(
            readGeneratedField<Object?>(
                context,
                _nullableComprehensiveSchemaConsistentForyFields[7],
                value.nullableDouble),
            value.nullableDouble);
    value.nullableFloat =
        _readNullableComprehensiveSchemaConsistentNullableFloat(
            readGeneratedField<Object?>(
                context,
                _nullableComprehensiveSchemaConsistentForyFields[8],
                value.nullableFloat),
            value.nullableFloat);
    value.nullableBool = _readNullableComprehensiveSchemaConsistentNullableBool(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[9],
            value.nullableBool),
        value.nullableBool);
    value.nullableLong = _readNullableComprehensiveSchemaConsistentNullableLong(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[10],
            value.nullableLong),
        value.nullableLong);
    value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[11],
            value.nullableInt),
        value.nullableInt);
    value.nullableString =
        _readNullableComprehensiveSchemaConsistentNullableString(
            readGeneratedField<Object?>(
                context,
                _nullableComprehensiveSchemaConsistentForyFields[12],
                value.nullableString),
            value.nullableString);
    value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[13],
            value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveSchemaConsistentListField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[14],
            value.listField),
        value.listField);
    value.nullableList = _readNullableComprehensiveSchemaConsistentNullableList(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[15],
            value.nullableList),
        value.nullableList);
    value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[16],
            value.nullableSet),
        value.nullableSet);
    value.setField = _readNullableComprehensiveSchemaConsistentSetField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[17],
            value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[18],
            value.mapField),
        value.mapField);
    value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveSchemaConsistentForyFields[19],
            value.nullableMap),
        value.nullableMap);
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

List<String> _readNullableComprehensiveSchemaConsistentListField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field listField.')))
      : List<String>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as String));
}

List<String>? _readNullableComprehensiveSchemaConsistentNullableList(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as List<String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : List<String>.of(((value as List)).map((item) => item == null
              ? (throw StateError('Received null for non-nullable list item.'))
              : item as String));
}

Set<String>? _readNullableComprehensiveSchemaConsistentNullableSet(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Set<String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : Set<String>.of(((value as Set)).map((item) => item == null
              ? (throw StateError('Received null for non-nullable set item.'))
              : item as String));
}

Set<String> _readNullableComprehensiveSchemaConsistentSetField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field setField.')))
      : Set<String>.of(((value as Set)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable set item.'))
          : item as String));
}

Map<String, String> _readNullableComprehensiveSchemaConsistentMapField(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field mapField.')))
      : Map<String, String>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? (throw StateError('Received null for non-nullable map key.'))
              : key as String,
          value == null
              ? (throw StateError('Received null for non-nullable map value.'))
              : value as String)));
}

Map<String, String>? _readNullableComprehensiveSchemaConsistentNullableMap(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? null as Map<String, String>?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : Map<String, String>.of(((value as Map)).map((key, value) =>
              MapEntry(
                  key == null
                      ? (throw StateError(
                          'Received null for non-nullable map key.'))
                      : key as String,
                  value == null
                      ? (throw StateError(
                          'Received null for non-nullable map value.'))
                      : value as String)));
}

const List<Map<String, Object?>>
    _nullableComprehensiveCompatibleForyFieldMetadata = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'boxedDouble',
    'identifier': 'boxed_double',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'doubleField',
    'identifier': 'double_field',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableDouble1',
    'identifier': 'nullable_double1',
    'id': null,
    'shape': <String, Object?>{
      'type': double,
      'typeId': 20,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boxedFloat',
    'identifier': 'boxed_float',
    'id': null,
    'shape': <String, Object?>{
      'type': Float32,
      'typeId': 19,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'floatField',
    'identifier': 'float_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Float32,
      'typeId': 19,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableFloat1',
    'identifier': 'nullable_float1',
    'id': null,
    'shape': <String, Object?>{
      'type': Float32,
      'typeId': 19,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'shortField',
    'identifier': 'short_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int16,
      'typeId': 3,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'byteField',
    'identifier': 'byte_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int8,
      'typeId': 2,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boolField',
    'identifier': 'bool_field',
    'id': null,
    'shape': <String, Object?>{
      'type': bool,
      'typeId': 1,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boxedBool',
    'identifier': 'boxed_bool',
    'id': null,
    'shape': <String, Object?>{
      'type': bool,
      'typeId': 1,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableBool1',
    'identifier': 'nullable_bool1',
    'id': null,
    'shape': <String, Object?>{
      'type': bool,
      'typeId': 1,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boxedLong',
    'identifier': 'boxed_long',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 7,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'longField',
    'identifier': 'long_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 7,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableLong1',
    'identifier': 'nullable_long1',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 7,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'boxedInt',
    'identifier': 'boxed_int',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'intField',
    'identifier': 'int_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableInt1',
    'identifier': 'nullable_int1',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'nullableString2',
    'identifier': 'nullable_string2',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'stringField',
    'identifier': 'string_field',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'listField',
    'identifier': 'list_field',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableList2',
    'identifier': 'nullable_list2',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': 22,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableSet2',
    'identifier': 'nullable_set2',
    'id': null,
    'shape': <String, Object?>{
      'type': Set,
      'typeId': 23,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'setField',
    'identifier': 'set_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Set,
      'typeId': 23,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'mapField',
    'identifier': 'map_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'nullableMap2',
    'identifier': 'nullable_map2',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': 24,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        }
      ],
    },
  },
];

final List<Object> _nullableComprehensiveCompatibleForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _nullableComprehensiveCompatibleForyFieldMetadata[0]),
    generatedField(1, _nullableComprehensiveCompatibleForyFieldMetadata[1]),
    generatedField(2, _nullableComprehensiveCompatibleForyFieldMetadata[2]),
    generatedField(3, _nullableComprehensiveCompatibleForyFieldMetadata[3]),
    generatedField(4, _nullableComprehensiveCompatibleForyFieldMetadata[4]),
    generatedField(5, _nullableComprehensiveCompatibleForyFieldMetadata[5]),
    generatedField(6, _nullableComprehensiveCompatibleForyFieldMetadata[6]),
    generatedField(7, _nullableComprehensiveCompatibleForyFieldMetadata[7]),
    generatedField(8, _nullableComprehensiveCompatibleForyFieldMetadata[8]),
    generatedField(9, _nullableComprehensiveCompatibleForyFieldMetadata[9]),
    generatedField(10, _nullableComprehensiveCompatibleForyFieldMetadata[10]),
    generatedField(11, _nullableComprehensiveCompatibleForyFieldMetadata[11]),
    generatedField(12, _nullableComprehensiveCompatibleForyFieldMetadata[12]),
    generatedField(13, _nullableComprehensiveCompatibleForyFieldMetadata[13]),
    generatedField(14, _nullableComprehensiveCompatibleForyFieldMetadata[14]),
    generatedField(15, _nullableComprehensiveCompatibleForyFieldMetadata[15]),
    generatedField(16, _nullableComprehensiveCompatibleForyFieldMetadata[16]),
    generatedField(17, _nullableComprehensiveCompatibleForyFieldMetadata[17]),
    generatedField(18, _nullableComprehensiveCompatibleForyFieldMetadata[18]),
    generatedField(19, _nullableComprehensiveCompatibleForyFieldMetadata[19]),
    generatedField(20, _nullableComprehensiveCompatibleForyFieldMetadata[20]),
    generatedField(21, _nullableComprehensiveCompatibleForyFieldMetadata[21]),
    generatedField(22, _nullableComprehensiveCompatibleForyFieldMetadata[22]),
    generatedField(23, _nullableComprehensiveCompatibleForyFieldMetadata[23]),
    generatedField(24, _nullableComprehensiveCompatibleForyFieldMetadata[24]),
  ],
);

final class _NullableComprehensiveCompatibleForySerializer
    extends _GeneratedStructSerializer<NullableComprehensiveCompatible> {
  const _NullableComprehensiveCompatibleForySerializer();
  @override
  void write(WriteContext context, NullableComprehensiveCompatible value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.boxedDouble);
            break;
          case 1:
            writeGeneratedField(context, field, value.doubleField);
            break;
          case 2:
            writeGeneratedField(context, field, value.nullableDouble1);
            break;
          case 3:
            writeGeneratedField(context, field, value.boxedFloat);
            break;
          case 4:
            writeGeneratedField(context, field, value.floatField);
            break;
          case 5:
            writeGeneratedField(context, field, value.nullableFloat1);
            break;
          case 6:
            writeGeneratedField(context, field, value.shortField);
            break;
          case 7:
            writeGeneratedField(context, field, value.byteField);
            break;
          case 8:
            writeGeneratedField(context, field, value.boolField);
            break;
          case 9:
            writeGeneratedField(context, field, value.boxedBool);
            break;
          case 10:
            writeGeneratedField(context, field, value.nullableBool1);
            break;
          case 11:
            writeGeneratedField(context, field, value.boxedLong);
            break;
          case 12:
            writeGeneratedField(context, field, value.longField);
            break;
          case 13:
            writeGeneratedField(context, field, value.nullableLong1);
            break;
          case 14:
            writeGeneratedField(context, field, value.boxedInt);
            break;
          case 15:
            writeGeneratedField(context, field, value.intField);
            break;
          case 16:
            writeGeneratedField(context, field, value.nullableInt1);
            break;
          case 17:
            writeGeneratedField(context, field, value.nullableString2);
            break;
          case 18:
            writeGeneratedField(context, field, value.stringField);
            break;
          case 19:
            writeGeneratedField(context, field, value.listField);
            break;
          case 20:
            writeGeneratedField(context, field, value.nullableList2);
            break;
          case 21:
            writeGeneratedField(context, field, value.nullableSet2);
            break;
          case 22:
            writeGeneratedField(context, field, value.setField);
            break;
          case 23:
            writeGeneratedField(context, field, value.mapField);
            break;
          case 24:
            writeGeneratedField(context, field, value.nullableMap2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[0],
        value.boxedDouble);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[1],
        value.doubleField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[2],
        value.nullableDouble1);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[3],
        value.boxedFloat);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[4],
        value.floatField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[5],
        value.nullableFloat1);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[6],
        value.shortField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[7],
        value.byteField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[8],
        value.boolField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[9],
        value.boxedBool);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[10],
        value.nullableBool1);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[11],
        value.boxedLong);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[12],
        value.longField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[13],
        value.nullableLong1);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[14],
        value.boxedInt);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[15],
        value.intField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[16],
        value.nullableInt1);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[17],
        value.nullableString2);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[18],
        value.stringField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[19],
        value.listField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[20],
        value.nullableList2);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[21],
        value.nullableSet2);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[22],
        value.setField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[23],
        value.mapField);
    writeGeneratedField(context, _nullableComprehensiveCompatibleForyFields[24],
        value.nullableMap2);
  }

  @override
  NullableComprehensiveCompatible read(ReadContext context) {
    final value = NullableComprehensiveCompatible();
    context.reference(value);
    value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[0], value.boxedDouble),
        value.boxedDouble);
    value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[1], value.doubleField),
        value.doubleField);
    value.nullableDouble1 = _readNullableComprehensiveCompatibleNullableDouble1(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[2],
            value.nullableDouble1),
        value.nullableDouble1);
    value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[3], value.boxedFloat),
        value.boxedFloat);
    value.floatField = _readNullableComprehensiveCompatibleFloatField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[4], value.floatField),
        value.floatField);
    value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[5],
            value.nullableFloat1),
        value.nullableFloat1);
    value.shortField = _readNullableComprehensiveCompatibleShortField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[6], value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveCompatibleByteField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[7], value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveCompatibleBoolField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[8], value.boolField),
        value.boolField);
    value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[9], value.boxedBool),
        value.boxedBool);
    value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[10],
            value.nullableBool1),
        value.nullableBool1);
    value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[11], value.boxedLong),
        value.boxedLong);
    value.longField = _readNullableComprehensiveCompatibleLongField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[12], value.longField),
        value.longField);
    value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[13],
            value.nullableLong1),
        value.nullableLong1);
    value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[14], value.boxedInt),
        value.boxedInt);
    value.intField = _readNullableComprehensiveCompatibleIntField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[15], value.intField),
        value.intField);
    value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[16], value.nullableInt1),
        value.nullableInt1);
    value.nullableString2 = _readNullableComprehensiveCompatibleNullableString2(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[17],
            value.nullableString2),
        value.nullableString2);
    value.stringField = _readNullableComprehensiveCompatibleStringField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[18], value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveCompatibleListField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[19], value.listField),
        value.listField);
    value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
        readGeneratedField<Object?>(
            context,
            _nullableComprehensiveCompatibleForyFields[20],
            value.nullableList2),
        value.nullableList2);
    value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[21], value.nullableSet2),
        value.nullableSet2);
    value.setField = _readNullableComprehensiveCompatibleSetField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[22], value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveCompatibleMapField(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[23], value.mapField),
        value.mapField);
    value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
        readGeneratedField<Object?>(context,
            _nullableComprehensiveCompatibleForyFields[24], value.nullableMap2),
        value.nullableMap2);
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

List<String> _readNullableComprehensiveCompatibleListField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field listField.')))
      : List<String>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as String));
}

List<String> _readNullableComprehensiveCompatibleNullableList2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError(
              'Received null for non-nullable field nullableList2.')))
      : List<String>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as String));
}

Set<String> _readNullableComprehensiveCompatibleNullableSet2(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field nullableSet2.')))
      : Set<String>.of(((value as Set)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable set item.'))
          : item as String));
}

Set<String> _readNullableComprehensiveCompatibleSetField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Set<String>
          : (throw StateError(
              'Received null for non-nullable field setField.')))
      : Set<String>.of(((value as Set)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable set item.'))
          : item as String));
}

Map<String, String> _readNullableComprehensiveCompatibleMapField(Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field mapField.')))
      : Map<String, String>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? (throw StateError('Received null for non-nullable map key.'))
              : key as String,
          value == null
              ? (throw StateError('Received null for non-nullable map value.'))
              : value as String)));
}

Map<String, String> _readNullableComprehensiveCompatibleNullableMap2(
    Object? value,
    [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, String>
          : (throw StateError(
              'Received null for non-nullable field nullableMap2.')))
      : Map<String, String>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? (throw StateError('Received null for non-nullable map key.'))
              : key as String,
          value == null
              ? (throw StateError('Received null for non-nullable map value.'))
              : value as String)));
}

const List<Map<String, Object?>> _refInnerSchemaConsistentForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'id',
    'identifier': 'id',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _refInnerSchemaConsistentForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refInnerSchemaConsistentForyFieldMetadata[0]),
    generatedField(1, _refInnerSchemaConsistentForyFieldMetadata[1]),
  ],
);

final class _RefInnerSchemaConsistentForySerializer
    extends _GeneratedStructSerializer<RefInnerSchemaConsistent> {
  const _RefInnerSchemaConsistentForySerializer();
  @override
  void write(WriteContext context, RefInnerSchemaConsistent value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.id);
            break;
          case 1:
            writeGeneratedField(context, field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _refInnerSchemaConsistentForyFields[0], value.id);
    writeGeneratedField(
        context, _refInnerSchemaConsistentForyFields[1], value.name);
  }

  @override
  RefInnerSchemaConsistent read(ReadContext context) {
    final value = RefInnerSchemaConsistent();
    context.reference(value);
    value.id = _readRefInnerSchemaConsistentId(
        readGeneratedField<Object?>(
            context, _refInnerSchemaConsistentForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerSchemaConsistentName(
        readGeneratedField<Object?>(
            context, _refInnerSchemaConsistentForyFields[1], value.name),
        value.name);
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

const List<Map<String, Object?>> _refOuterSchemaConsistentForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'inner1',
    'identifier': 'inner1',
    'id': null,
    'shape': <String, Object?>{
      'type': RefInnerSchemaConsistent,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': false,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'inner2',
    'identifier': 'inner2',
    'id': null,
    'shape': <String, Object?>{
      'type': RefInnerSchemaConsistent,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': false,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _refOuterSchemaConsistentForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refOuterSchemaConsistentForyFieldMetadata[0]),
    generatedField(1, _refOuterSchemaConsistentForyFieldMetadata[1]),
  ],
);

final class _RefOuterSchemaConsistentForySerializer
    extends _GeneratedStructSerializer<RefOuterSchemaConsistent> {
  const _RefOuterSchemaConsistentForySerializer();
  @override
  void write(WriteContext context, RefOuterSchemaConsistent value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.inner1);
            break;
          case 1:
            writeGeneratedField(context, field, value.inner2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _refOuterSchemaConsistentForyFields[0], value.inner1);
    writeGeneratedField(
        context, _refOuterSchemaConsistentForyFields[1], value.inner2);
  }

  @override
  RefOuterSchemaConsistent read(ReadContext context) {
    final value = RefOuterSchemaConsistent();
    context.reference(value);
    value.inner1 = _readRefOuterSchemaConsistentInner1(
        readGeneratedField<Object?>(
            context, _refOuterSchemaConsistentForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterSchemaConsistentInner2(
        readGeneratedField<Object?>(
            context, _refOuterSchemaConsistentForyFields[1], value.inner2),
        value.inner2);
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

const List<Map<String, Object?>> _refInnerCompatibleForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'id',
    'identifier': 'id',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _refInnerCompatibleForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refInnerCompatibleForyFieldMetadata[0]),
    generatedField(1, _refInnerCompatibleForyFieldMetadata[1]),
  ],
);

final class _RefInnerCompatibleForySerializer
    extends _GeneratedStructSerializer<RefInnerCompatible> {
  const _RefInnerCompatibleForySerializer();
  @override
  void write(WriteContext context, RefInnerCompatible value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.id);
            break;
          case 1:
            writeGeneratedField(context, field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _refInnerCompatibleForyFields[0], value.id);
    writeGeneratedField(context, _refInnerCompatibleForyFields[1], value.name);
  }

  @override
  RefInnerCompatible read(ReadContext context) {
    final value = RefInnerCompatible();
    context.reference(value);
    value.id = _readRefInnerCompatibleId(
        readGeneratedField<Object?>(
            context, _refInnerCompatibleForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerCompatibleName(
        readGeneratedField<Object?>(
            context, _refInnerCompatibleForyFields[1], value.name),
        value.name);
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

const List<Map<String, Object?>> _refOuterCompatibleForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'inner1',
    'identifier': 'inner1',
    'id': null,
    'shape': <String, Object?>{
      'type': RefInnerCompatible,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'inner2',
    'identifier': 'inner2',
    'id': null,
    'shape': <String, Object?>{
      'type': RefInnerCompatible,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _refOuterCompatibleForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refOuterCompatibleForyFieldMetadata[0]),
    generatedField(1, _refOuterCompatibleForyFieldMetadata[1]),
  ],
);

final class _RefOuterCompatibleForySerializer
    extends _GeneratedStructSerializer<RefOuterCompatible> {
  const _RefOuterCompatibleForySerializer();
  @override
  void write(WriteContext context, RefOuterCompatible value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.inner1);
            break;
          case 1:
            writeGeneratedField(context, field, value.inner2);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _refOuterCompatibleForyFields[0], value.inner1);
    writeGeneratedField(
        context, _refOuterCompatibleForyFields[1], value.inner2);
  }

  @override
  RefOuterCompatible read(ReadContext context) {
    final value = RefOuterCompatible();
    context.reference(value);
    value.inner1 = _readRefOuterCompatibleInner1(
        readGeneratedField<Object?>(
            context, _refOuterCompatibleForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterCompatibleInner2(
        readGeneratedField<Object?>(
            context, _refOuterCompatibleForyFields[1], value.inner2),
        value.inner2);
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

const List<Map<String, Object?>> _refOverrideElementForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'id',
    'identifier': 'id',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 5,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _refOverrideElementForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refOverrideElementForyFieldMetadata[0]),
    generatedField(1, _refOverrideElementForyFieldMetadata[1]),
  ],
);

final class _RefOverrideElementForySerializer
    extends _GeneratedStructSerializer<RefOverrideElement> {
  const _RefOverrideElementForySerializer();
  @override
  void write(WriteContext context, RefOverrideElement value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.id);
            break;
          case 1:
            writeGeneratedField(context, field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _refOverrideElementForyFields[0], value.id);
    writeGeneratedField(context, _refOverrideElementForyFields[1], value.name);
  }

  @override
  RefOverrideElement read(ReadContext context) {
    final value = RefOverrideElement();
    context.reference(value);
    value.id = _readRefOverrideElementId(
        readGeneratedField<Object?>(
            context, _refOverrideElementForyFields[0], value.id),
        value.id);
    value.name = _readRefOverrideElementName(
        readGeneratedField<Object?>(
            context, _refOverrideElementForyFields[1], value.name),
        value.name);
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

const List<Map<String, Object?>> _circularRefStructForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'name',
    'identifier': 'name',
    'id': null,
    'shape': <String, Object?>{
      'type': String,
      'typeId': 21,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'selfRef',
    'identifier': 'self_ref',
    'id': null,
    'shape': <String, Object?>{
      'type': CircularRefStruct,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _circularRefStructForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _circularRefStructForyFieldMetadata[0]),
    generatedField(1, _circularRefStructForyFieldMetadata[1]),
  ],
);

final class _CircularRefStructForySerializer
    extends _GeneratedStructSerializer<CircularRefStruct> {
  const _CircularRefStructForySerializer();
  @override
  void write(WriteContext context, CircularRefStruct value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.name);
            break;
          case 1:
            writeGeneratedField(context, field, value.selfRef);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _circularRefStructForyFields[0], value.name);
    writeGeneratedField(
        context, _circularRefStructForyFields[1], value.selfRef);
  }

  @override
  CircularRefStruct read(ReadContext context) {
    final value = CircularRefStruct();
    context.reference(value);
    value.name = _readCircularRefStructName(
        readGeneratedField<Object?>(
            context, _circularRefStructForyFields[0], value.name),
        value.name);
    value.selfRef = _readCircularRefStructSelfRef(
        readGeneratedField<Object?>(
            context, _circularRefStructForyFields[1], value.selfRef),
        value.selfRef);
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

const List<Map<String, Object?>> _unsignedSchemaConsistentForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'u64FixedField',
    'identifier': 'u64_fixed_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 13,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32FixedField',
    'identifier': 'u32_fixed_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 11,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u16Field',
    'identifier': 'u16_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt16,
      'typeId': 10,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u8Field',
    'identifier': 'u8_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt8,
      'typeId': 9,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64TaggedField',
    'identifier': 'u64_tagged_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64VarField',
    'identifier': 'u64_var_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 14,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32VarField',
    'identifier': 'u32_var_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 12,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64FixedNullableField',
    'identifier': 'u64_fixed_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 13,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32FixedNullableField',
    'identifier': 'u32_fixed_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 11,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u16NullableField',
    'identifier': 'u16_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt16,
      'typeId': 10,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u8NullableField',
    'identifier': 'u8_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt8,
      'typeId': 9,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64TaggedNullableField',
    'identifier': 'u64_tagged_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64VarNullableField',
    'identifier': 'u64_var_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 14,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32VarNullableField',
    'identifier': 'u32_var_nullable_field',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 12,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _unsignedSchemaConsistentForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _unsignedSchemaConsistentForyFieldMetadata[0]),
    generatedField(1, _unsignedSchemaConsistentForyFieldMetadata[1]),
    generatedField(2, _unsignedSchemaConsistentForyFieldMetadata[2]),
    generatedField(3, _unsignedSchemaConsistentForyFieldMetadata[3]),
    generatedField(4, _unsignedSchemaConsistentForyFieldMetadata[4]),
    generatedField(5, _unsignedSchemaConsistentForyFieldMetadata[5]),
    generatedField(6, _unsignedSchemaConsistentForyFieldMetadata[6]),
    generatedField(7, _unsignedSchemaConsistentForyFieldMetadata[7]),
    generatedField(8, _unsignedSchemaConsistentForyFieldMetadata[8]),
    generatedField(9, _unsignedSchemaConsistentForyFieldMetadata[9]),
    generatedField(10, _unsignedSchemaConsistentForyFieldMetadata[10]),
    generatedField(11, _unsignedSchemaConsistentForyFieldMetadata[11]),
    generatedField(12, _unsignedSchemaConsistentForyFieldMetadata[12]),
    generatedField(13, _unsignedSchemaConsistentForyFieldMetadata[13]),
  ],
);

final class _UnsignedSchemaConsistentForySerializer
    extends _GeneratedStructSerializer<UnsignedSchemaConsistent> {
  const _UnsignedSchemaConsistentForySerializer();
  @override
  void write(WriteContext context, UnsignedSchemaConsistent value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.u64FixedField);
            break;
          case 1:
            writeGeneratedField(context, field, value.u32FixedField);
            break;
          case 2:
            writeGeneratedField(context, field, value.u16Field);
            break;
          case 3:
            writeGeneratedField(context, field, value.u8Field);
            break;
          case 4:
            writeGeneratedField(context, field, value.u64TaggedField);
            break;
          case 5:
            writeGeneratedField(context, field, value.u64VarField);
            break;
          case 6:
            writeGeneratedField(context, field, value.u32VarField);
            break;
          case 7:
            writeGeneratedField(context, field, value.u64FixedNullableField);
            break;
          case 8:
            writeGeneratedField(context, field, value.u32FixedNullableField);
            break;
          case 9:
            writeGeneratedField(context, field, value.u16NullableField);
            break;
          case 10:
            writeGeneratedField(context, field, value.u8NullableField);
            break;
          case 11:
            writeGeneratedField(context, field, value.u64TaggedNullableField);
            break;
          case 12:
            writeGeneratedField(context, field, value.u64VarNullableField);
            break;
          case 13:
            writeGeneratedField(context, field, value.u32VarNullableField);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[0], value.u64FixedField);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[1], value.u32FixedField);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[2], value.u16Field);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[3], value.u8Field);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[4], value.u64TaggedField);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[5], value.u64VarField);
    writeGeneratedField(
        context, _unsignedSchemaConsistentForyFields[6], value.u32VarField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[7],
        value.u64FixedNullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[8],
        value.u32FixedNullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[9],
        value.u16NullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[10],
        value.u8NullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[11],
        value.u64TaggedNullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[12],
        value.u64VarNullableField);
    writeGeneratedField(context, _unsignedSchemaConsistentForyFields[13],
        value.u32VarNullableField);
  }

  @override
  UnsignedSchemaConsistent read(ReadContext context) {
    final value = UnsignedSchemaConsistent();
    context.reference(value);
    value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentForyFields[0], value.u64FixedField),
        value.u64FixedField);
    value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentForyFields[1], value.u32FixedField),
        value.u32FixedField);
    value.u16Field = _readUnsignedSchemaConsistentU16Field(
        readGeneratedField<Object?>(
            context, _unsignedSchemaConsistentForyFields[2], value.u16Field),
        value.u16Field);
    value.u8Field = _readUnsignedSchemaConsistentU8Field(
        readGeneratedField<Object?>(
            context, _unsignedSchemaConsistentForyFields[3], value.u8Field),
        value.u8Field);
    value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentForyFields[4], value.u64TaggedField),
        value.u64TaggedField);
    value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
        readGeneratedField<Object?>(
            context, _unsignedSchemaConsistentForyFields[5], value.u64VarField),
        value.u64VarField);
    value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
        readGeneratedField<Object?>(
            context, _unsignedSchemaConsistentForyFields[6], value.u32VarField),
        value.u32VarField);
    value.u64FixedNullableField =
        _readUnsignedSchemaConsistentU64FixedNullableField(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentForyFields[7],
                value.u64FixedNullableField),
            value.u64FixedNullableField);
    value.u32FixedNullableField =
        _readUnsignedSchemaConsistentU32FixedNullableField(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentForyFields[8],
                value.u32FixedNullableField),
            value.u32FixedNullableField);
    value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentForyFields[9], value.u16NullableField),
        value.u16NullableField);
    value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentForyFields[10], value.u8NullableField),
        value.u8NullableField);
    value.u64TaggedNullableField =
        _readUnsignedSchemaConsistentU64TaggedNullableField(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentForyFields[11],
                value.u64TaggedNullableField),
            value.u64TaggedNullableField);
    value.u64VarNullableField =
        _readUnsignedSchemaConsistentU64VarNullableField(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentForyFields[12],
                value.u64VarNullableField),
            value.u64VarNullableField);
    value.u32VarNullableField =
        _readUnsignedSchemaConsistentU32VarNullableField(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentForyFields[13],
                value.u32VarNullableField),
            value.u32VarNullableField);
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

const List<Map<String, Object?>>
    _unsignedSchemaConsistentSimpleForyFieldMetadata = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'u64Tagged',
    'identifier': 'u64_tagged',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64TaggedNullable',
    'identifier': 'u64_tagged_nullable',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _unsignedSchemaConsistentSimpleForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _unsignedSchemaConsistentSimpleForyFieldMetadata[0]),
    generatedField(1, _unsignedSchemaConsistentSimpleForyFieldMetadata[1]),
  ],
);

final class _UnsignedSchemaConsistentSimpleForySerializer
    extends _GeneratedStructSerializer<UnsignedSchemaConsistentSimple> {
  const _UnsignedSchemaConsistentSimpleForySerializer();
  @override
  void write(WriteContext context, UnsignedSchemaConsistentSimple value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.u64Tagged);
            break;
          case 1:
            writeGeneratedField(context, field, value.u64TaggedNullable);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _unsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged);
    writeGeneratedField(context, _unsignedSchemaConsistentSimpleForyFields[1],
        value.u64TaggedNullable);
  }

  @override
  UnsignedSchemaConsistentSimple read(ReadContext context) {
    final value = UnsignedSchemaConsistentSimple();
    context.reference(value);
    value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
        readGeneratedField<Object?>(context,
            _unsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged),
        value.u64Tagged);
    value.u64TaggedNullable =
        _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
            readGeneratedField<Object?>(
                context,
                _unsignedSchemaConsistentSimpleForyFields[1],
                value.u64TaggedNullable),
            value.u64TaggedNullable);
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

const List<Map<String, Object?>> _unsignedSchemaCompatibleForyFieldMetadata =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'u64FixedField2',
    'identifier': 'u64_fixed_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 13,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32FixedField2',
    'identifier': 'u32_fixed_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 11,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u16Field2',
    'identifier': 'u16_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt16,
      'typeId': 10,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u8Field2',
    'identifier': 'u8_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt8,
      'typeId': 9,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64TaggedField2',
    'identifier': 'u64_tagged_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64VarField2',
    'identifier': 'u64_var_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 14,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32VarField2',
    'identifier': 'u32_var_field2',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 12,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64FixedField1',
    'identifier': 'u64_fixed_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 13,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32FixedField1',
    'identifier': 'u32_fixed_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 11,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u16Field1',
    'identifier': 'u16_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt16,
      'typeId': 10,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u8Field1',
    'identifier': 'u8_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt8,
      'typeId': 9,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64TaggedField1',
    'identifier': 'u64_tagged_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 15,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u64VarField1',
    'identifier': 'u64_var_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': int,
      'typeId': 14,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'u32VarField1',
    'identifier': 'u32_var_field1',
    'id': null,
    'shape': <String, Object?>{
      'type': UInt32,
      'typeId': 12,
      'nullable': true,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final List<Object> _unsignedSchemaCompatibleForyFields =
    List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _unsignedSchemaCompatibleForyFieldMetadata[0]),
    generatedField(1, _unsignedSchemaCompatibleForyFieldMetadata[1]),
    generatedField(2, _unsignedSchemaCompatibleForyFieldMetadata[2]),
    generatedField(3, _unsignedSchemaCompatibleForyFieldMetadata[3]),
    generatedField(4, _unsignedSchemaCompatibleForyFieldMetadata[4]),
    generatedField(5, _unsignedSchemaCompatibleForyFieldMetadata[5]),
    generatedField(6, _unsignedSchemaCompatibleForyFieldMetadata[6]),
    generatedField(7, _unsignedSchemaCompatibleForyFieldMetadata[7]),
    generatedField(8, _unsignedSchemaCompatibleForyFieldMetadata[8]),
    generatedField(9, _unsignedSchemaCompatibleForyFieldMetadata[9]),
    generatedField(10, _unsignedSchemaCompatibleForyFieldMetadata[10]),
    generatedField(11, _unsignedSchemaCompatibleForyFieldMetadata[11]),
    generatedField(12, _unsignedSchemaCompatibleForyFieldMetadata[12]),
    generatedField(13, _unsignedSchemaCompatibleForyFieldMetadata[13]),
  ],
);

final class _UnsignedSchemaCompatibleForySerializer
    extends _GeneratedStructSerializer<UnsignedSchemaCompatible> {
  const _UnsignedSchemaCompatibleForySerializer();
  @override
  void write(WriteContext context, UnsignedSchemaCompatible value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.u64FixedField2);
            break;
          case 1:
            writeGeneratedField(context, field, value.u32FixedField2);
            break;
          case 2:
            writeGeneratedField(context, field, value.u16Field2);
            break;
          case 3:
            writeGeneratedField(context, field, value.u8Field2);
            break;
          case 4:
            writeGeneratedField(context, field, value.u64TaggedField2);
            break;
          case 5:
            writeGeneratedField(context, field, value.u64VarField2);
            break;
          case 6:
            writeGeneratedField(context, field, value.u32VarField2);
            break;
          case 7:
            writeGeneratedField(context, field, value.u64FixedField1);
            break;
          case 8:
            writeGeneratedField(context, field, value.u32FixedField1);
            break;
          case 9:
            writeGeneratedField(context, field, value.u16Field1);
            break;
          case 10:
            writeGeneratedField(context, field, value.u8Field1);
            break;
          case 11:
            writeGeneratedField(context, field, value.u64TaggedField1);
            break;
          case 12:
            writeGeneratedField(context, field, value.u64VarField1);
            break;
          case 13:
            writeGeneratedField(context, field, value.u32VarField1);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[0], value.u64FixedField2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[1], value.u32FixedField2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[2], value.u16Field2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[3], value.u8Field2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[4], value.u64TaggedField2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[5], value.u64VarField2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[6], value.u32VarField2);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[7], value.u64FixedField1);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[8], value.u32FixedField1);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[9], value.u16Field1);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[10], value.u8Field1);
    writeGeneratedField(context, _unsignedSchemaCompatibleForyFields[11],
        value.u64TaggedField1);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[12], value.u64VarField1);
    writeGeneratedField(
        context, _unsignedSchemaCompatibleForyFields[13], value.u32VarField1);
  }

  @override
  UnsignedSchemaCompatible read(ReadContext context) {
    final value = UnsignedSchemaCompatible();
    context.reference(value);
    value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[0], value.u64FixedField2),
        value.u64FixedField2);
    value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[1], value.u32FixedField2),
        value.u32FixedField2);
    value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
        readGeneratedField<Object?>(
            context, _unsignedSchemaCompatibleForyFields[2], value.u16Field2),
        value.u16Field2);
    value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
        readGeneratedField<Object?>(
            context, _unsignedSchemaCompatibleForyFields[3], value.u8Field2),
        value.u8Field2);
    value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[4], value.u64TaggedField2),
        value.u64TaggedField2);
    value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[5], value.u64VarField2),
        value.u64VarField2);
    value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[6], value.u32VarField2),
        value.u32VarField2);
    value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[7], value.u64FixedField1),
        value.u64FixedField1);
    value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[8], value.u32FixedField1),
        value.u32FixedField1);
    value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
        readGeneratedField<Object?>(
            context, _unsignedSchemaCompatibleForyFields[9], value.u16Field1),
        value.u16Field1);
    value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
        readGeneratedField<Object?>(
            context, _unsignedSchemaCompatibleForyFields[10], value.u8Field1),
        value.u8Field1);
    value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[11], value.u64TaggedField1),
        value.u64TaggedField1);
    value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[12], value.u64VarField1),
        value.u64VarField1);
    value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
        readGeneratedField<Object?>(context,
            _unsignedSchemaCompatibleForyFields[13], value.u32VarField1),
        value.u32VarField1);
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

bool _generatedForyBindingsInstalled = false;

void _installGeneratedForyBindings() {
  if (_generatedForyBindingsInstalled) {
    return;
  }
  _generatedForyBindingsInstalled = true;
  Fory.bindGeneratedEnumFactory(Color, _ColorForySerializer.new);
  Fory.bindGeneratedEnumFactory(TestEnum, _TestEnumForySerializer.new);
  Fory.bindGeneratedStructFactory(TwoEnumFieldStructEvolution,
      _TwoEnumFieldStructEvolutionForySerializer.new,
      evolving: true, fields: _twoEnumFieldStructEvolutionForyFieldMetadata);
  Fory.bindGeneratedStructFactory(Item, _ItemForySerializer.new,
      evolving: true, fields: _itemForyFieldMetadata);
  Fory.bindGeneratedStructFactory(SimpleStruct, _SimpleStructForySerializer.new,
      evolving: true, fields: _simpleStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      EvolvingOverrideStruct, _EvolvingOverrideStructForySerializer.new,
      evolving: true, fields: _evolvingOverrideStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      FixedOverrideStruct, _FixedOverrideStructForySerializer.new,
      evolving: false, fields: _fixedOverrideStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(Item1, _Item1ForySerializer.new,
      evolving: true, fields: _item1ForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      StructWithUnion2, _StructWithUnion2ForySerializer.new,
      evolving: true, fields: _structWithUnion2ForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      StructWithList, _StructWithListForySerializer.new,
      evolving: true, fields: _structWithListForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      StructWithMap, _StructWithMapForySerializer.new,
      evolving: true, fields: _structWithMapForyFieldMetadata);
  Fory.bindGeneratedStructFactory(MyStruct, _MyStructForySerializer.new,
      evolving: true, fields: _myStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(MyWrapper, _MyWrapperForySerializer.new,
      evolving: true, fields: _myWrapperForyFieldMetadata);
  Fory.bindGeneratedStructFactory(EmptyWrapper, _EmptyWrapperForySerializer.new,
      evolving: true, fields: _emptyWrapperForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      VersionCheckStruct, _VersionCheckStructForySerializer.new,
      evolving: true, fields: _versionCheckStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(Dog, _DogForySerializer.new,
      evolving: true, fields: _dogForyFieldMetadata);
  Fory.bindGeneratedStructFactory(Cat, _CatForySerializer.new,
      evolving: true, fields: _catForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      AnimalListHolder, _AnimalListHolderForySerializer.new,
      evolving: true, fields: _animalListHolderForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      AnimalMapHolder, _AnimalMapHolderForySerializer.new,
      evolving: true, fields: _animalMapHolderForyFieldMetadata);
  Fory.bindGeneratedStructFactory(EmptyStruct, _EmptyStructForySerializer.new,
      evolving: true, fields: _emptyStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      OneStringFieldStruct, _OneStringFieldStructForySerializer.new,
      evolving: true, fields: _oneStringFieldStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      TwoStringFieldStruct, _TwoStringFieldStructForySerializer.new,
      evolving: true, fields: _twoStringFieldStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      OneEnumFieldStruct, _OneEnumFieldStructForySerializer.new,
      evolving: true, fields: _oneEnumFieldStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      TwoEnumFieldStruct, _TwoEnumFieldStructForySerializer.new,
      evolving: true, fields: _twoEnumFieldStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(NullableComprehensiveSchemaConsistent,
      _NullableComprehensiveSchemaConsistentForySerializer.new,
      evolving: true,
      fields: _nullableComprehensiveSchemaConsistentForyFieldMetadata);
  Fory.bindGeneratedStructFactory(NullableComprehensiveCompatible,
      _NullableComprehensiveCompatibleForySerializer.new,
      evolving: true,
      fields: _nullableComprehensiveCompatibleForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      RefInnerSchemaConsistent, _RefInnerSchemaConsistentForySerializer.new,
      evolving: true, fields: _refInnerSchemaConsistentForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      RefOuterSchemaConsistent, _RefOuterSchemaConsistentForySerializer.new,
      evolving: true, fields: _refOuterSchemaConsistentForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      RefInnerCompatible, _RefInnerCompatibleForySerializer.new,
      evolving: true, fields: _refInnerCompatibleForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      RefOuterCompatible, _RefOuterCompatibleForySerializer.new,
      evolving: true, fields: _refOuterCompatibleForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      RefOverrideElement, _RefOverrideElementForySerializer.new,
      evolving: true, fields: _refOverrideElementForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      CircularRefStruct, _CircularRefStructForySerializer.new,
      evolving: true, fields: _circularRefStructForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      UnsignedSchemaConsistent, _UnsignedSchemaConsistentForySerializer.new,
      evolving: true, fields: _unsignedSchemaConsistentForyFieldMetadata);
  Fory.bindGeneratedStructFactory(UnsignedSchemaConsistentSimple,
      _UnsignedSchemaConsistentSimpleForySerializer.new,
      evolving: true, fields: _unsignedSchemaConsistentSimpleForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      UnsignedSchemaCompatible, _UnsignedSchemaCompatibleForySerializer.new,
      evolving: true, fields: _unsignedSchemaCompatibleForyFieldMetadata);
}

void _registerXlangTestModelsForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  _installGeneratedForyBindings();
  if (type == Color) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TestEnum) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStructEvolution) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == SimpleStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingOverrideStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedOverrideStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item1) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithUnion2) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithList) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithMap) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyWrapper) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyWrapper) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == VersionCheckStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Dog) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Cat) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalListHolder) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalMapHolder) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneStringFieldStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoStringFieldStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneEnumFieldStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveSchemaConsistent) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveCompatible) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerSchemaConsistent) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterSchemaConsistent) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerCompatible) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterCompatible) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOverrideElement) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == CircularRefStruct) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistent) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistentSimple) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaCompatible) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void _registerXlangTestModelsForyTypes(Fory fory) {
  _installGeneratedForyBindings();
  fory.register(Color,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Color');
  fory.register(TestEnum,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'TestEnum');
  fory.register(TwoEnumFieldStructEvolution,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStructEvolution');
  fory.register(Item,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item');
  fory.register(SimpleStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'SimpleStruct');
  fory.register(EvolvingOverrideStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EvolvingOverrideStruct');
  fory.register(FixedOverrideStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'FixedOverrideStruct');
  fory.register(Item1,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item1');
  fory.register(StructWithUnion2,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithUnion2');
  fory.register(StructWithList,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithList');
  fory.register(StructWithMap,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithMap');
  fory.register(MyStruct,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyStruct');
  fory.register(MyWrapper,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyWrapper');
  fory.register(EmptyWrapper,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EmptyWrapper');
  fory.register(VersionCheckStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'VersionCheckStruct');
  fory.register(Dog,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Dog');
  fory.register(Cat,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Cat');
  fory.register(AnimalListHolder,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalListHolder');
  fory.register(AnimalMapHolder,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalMapHolder');
  fory.register(EmptyStruct,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'EmptyStruct');
  fory.register(OneStringFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneStringFieldStruct');
  fory.register(TwoStringFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoStringFieldStruct');
  fory.register(OneEnumFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneEnumFieldStruct');
  fory.register(TwoEnumFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStruct');
  fory.register(NullableComprehensiveSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveSchemaConsistent');
  fory.register(NullableComprehensiveCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveCompatible');
  fory.register(RefInnerSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerSchemaConsistent');
  fory.register(RefOuterSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterSchemaConsistent');
  fory.register(RefInnerCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerCompatible');
  fory.register(RefOuterCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterCompatible');
  fory.register(RefOverrideElement,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOverrideElement');
  fory.register(CircularRefStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'CircularRefStruct');
  fory.register(UnsignedSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistent');
  fory.register(UnsignedSchemaConsistentSimple,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistentSimple');
  fory.register(UnsignedSchemaCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaCompatible');
}
