// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'xlang_test_models.dart';

// **************************************************************************
// ForyGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND

final class _ColorForySerializer extends Serializer<Color> {
  const _ColorForySerializer();
  bool get isEnum => true;
  @override
  void write(WriteContext context, Color value) {
    context.writeVarUint32(value.index);
  }

  @override
  Color read(ReadContext context) {
    return Color.values[context.readVarUint32()];
  }
}

const _ColorForySerializer _colorForySerializer = _ColorForySerializer();

final class _TestEnumForySerializer extends Serializer<TestEnum> {
  const _TestEnumForySerializer();
  bool get isEnum => true;
  @override
  void write(WriteContext context, TestEnum value) {
    context.writeVarUint32(value.index);
  }

  @override
  TestEnum read(ReadContext context) {
    return TestEnum.values[context.readVarUint32()];
  }
}

const _TestEnumForySerializer _testEnumForySerializer =
    _TestEnumForySerializer();

const List<Map<String, Object?>> _TwoEnumFieldStructEvolutionForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _TwoEnumFieldStructEvolutionForySerializer
    extends Serializer<TwoEnumFieldStructEvolution> {
  const _TwoEnumFieldStructEvolutionForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _TwoEnumFieldStructEvolutionForyFields;
  @override
  void write(WriteContext context, TwoEnumFieldStructEvolution value) {
    final compatibleFields =
        context.compatibleFieldOrder(_TwoEnumFieldStructEvolutionForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f2':
            context.writeField(field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_TwoEnumFieldStructEvolutionForyFields[0], value.f1);
    context.writeField(_TwoEnumFieldStructEvolutionForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStructEvolution read(ReadContext context) {
    final value = TwoEnumFieldStructEvolution();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructEvolutionF1(
        context.readField<Object?>(
            _TwoEnumFieldStructEvolutionForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructEvolutionF2(
        context.readField<Object?>(
            _TwoEnumFieldStructEvolutionForyFields[1], value.f2),
        value.f2);
    return value;
  }
}

const _TwoEnumFieldStructEvolutionForySerializer
    _twoEnumFieldStructEvolutionForySerializer =
    _TwoEnumFieldStructEvolutionForySerializer();

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

const List<Map<String, Object?>> _ItemForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
    },
  },
];

final class _ItemForySerializer extends Serializer<Item> {
  const _ItemForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _ItemForyFields;
  @override
  void write(WriteContext context, Item value) {
    final compatibleFields = context.compatibleFieldOrder(_ItemForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'name':
            context.writeField(field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_ItemForyFields[0], value.name);
  }

  @override
  Item read(ReadContext context) {
    final value = Item();
    context.reference(value);
    value.name = _readItemName(
        context.readField<Object?>(_ItemForyFields[0], value.name), value.name);
    return value;
  }
}

const _ItemForySerializer _itemForySerializer = _ItemForySerializer();

String _readItemName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

const List<Map<String, Object?>> _SimpleStructForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': double,
          'typeId': 20,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _SimpleStructForySerializer extends Serializer<SimpleStruct> {
  const _SimpleStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _SimpleStructForyFields;
  @override
  void write(WriteContext context, SimpleStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_SimpleStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f2':
            context.writeField(field, value.f2);
            break;
          case 'f7':
            context.writeField(field, value.f7);
            break;
          case 'f8':
            context.writeField(field, value.f8);
            break;
          case 'last':
            context.writeField(field, value.last);
            break;
          case 'f4':
            context.writeField(field, value.f4);
            break;
          case 'f6':
            context.writeField(field, value.f6);
            break;
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f3':
            context.writeField(field, value.f3);
            break;
          case 'f5':
            context.writeField(field, value.f5);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_SimpleStructForyFields[0], value.f2);
    context.writeField(_SimpleStructForyFields[1], value.f7);
    context.writeField(_SimpleStructForyFields[2], value.f8);
    context.writeField(_SimpleStructForyFields[3], value.last);
    context.writeField(_SimpleStructForyFields[4], value.f4);
    context.writeField(_SimpleStructForyFields[5], value.f6);
    context.writeField(_SimpleStructForyFields[6], value.f1);
    context.writeField(_SimpleStructForyFields[7], value.f3);
    context.writeField(_SimpleStructForyFields[8], value.f5);
  }

  @override
  SimpleStruct read(ReadContext context) {
    final value = SimpleStruct();
    context.reference(value);
    value.f2 = _readSimpleStructF2(
        context.readField<Object?>(_SimpleStructForyFields[0], value.f2),
        value.f2);
    value.f7 = _readSimpleStructF7(
        context.readField<Object?>(_SimpleStructForyFields[1], value.f7),
        value.f7);
    value.f8 = _readSimpleStructF8(
        context.readField<Object?>(_SimpleStructForyFields[2], value.f8),
        value.f8);
    value.last = _readSimpleStructLast(
        context.readField<Object?>(_SimpleStructForyFields[3], value.last),
        value.last);
    value.f4 = _readSimpleStructF4(
        context.readField<Object?>(_SimpleStructForyFields[4], value.f4),
        value.f4);
    value.f6 = _readSimpleStructF6(
        context.readField<Object?>(_SimpleStructForyFields[5], value.f6),
        value.f6);
    value.f1 = _readSimpleStructF1(
        context.readField<Object?>(_SimpleStructForyFields[6], value.f1),
        value.f1);
    value.f3 = _readSimpleStructF3(
        context.readField<Object?>(_SimpleStructForyFields[7], value.f3),
        value.f3);
    value.f5 = _readSimpleStructF5(
        context.readField<Object?>(_SimpleStructForyFields[8], value.f5),
        value.f5);
    return value;
  }
}

const _SimpleStructForySerializer _simpleStructForySerializer =
    _SimpleStructForySerializer();

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

const List<Map<String, Object?>> _EvolvingOverrideStructForyFields =
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
      'arguments': const <Object?>[],
    },
  },
];

final class _EvolvingOverrideStructForySerializer
    extends Serializer<EvolvingOverrideStruct> {
  const _EvolvingOverrideStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _EvolvingOverrideStructForyFields;
  @override
  void write(WriteContext context, EvolvingOverrideStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_EvolvingOverrideStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_EvolvingOverrideStructForyFields[0], value.f1);
  }

  @override
  EvolvingOverrideStruct read(ReadContext context) {
    final value = EvolvingOverrideStruct();
    context.reference(value);
    value.f1 = _readEvolvingOverrideStructF1(
        context.readField<Object?>(
            _EvolvingOverrideStructForyFields[0], value.f1),
        value.f1);
    return value;
  }
}

const _EvolvingOverrideStructForySerializer
    _evolvingOverrideStructForySerializer =
    _EvolvingOverrideStructForySerializer();

String _readEvolvingOverrideStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as String;
}

const List<Map<String, Object?>> _FixedOverrideStructForyFields =
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
      'arguments': const <Object?>[],
    },
  },
];

final class _FixedOverrideStructForySerializer
    extends Serializer<FixedOverrideStruct> {
  const _FixedOverrideStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => false;
  @override
  List<Map<String, Object?>> get fields => _FixedOverrideStructForyFields;
  @override
  void write(WriteContext context, FixedOverrideStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_FixedOverrideStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_FixedOverrideStructForyFields[0], value.f1);
  }

  @override
  FixedOverrideStruct read(ReadContext context) {
    final value = FixedOverrideStruct();
    context.reference(value);
    value.f1 = _readFixedOverrideStructF1(
        context.readField<Object?>(_FixedOverrideStructForyFields[0], value.f1),
        value.f1);
    return value;
  }
}

const _FixedOverrideStructForySerializer _fixedOverrideStructForySerializer =
    _FixedOverrideStructForySerializer();

String _readFixedOverrideStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as String;
}

const List<Map<String, Object?>> _Item1ForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _Item1ForySerializer extends Serializer<Item1> {
  const _Item1ForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _Item1ForyFields;
  @override
  void write(WriteContext context, Item1 value) {
    final compatibleFields = context.compatibleFieldOrder(_Item1ForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f2':
            context.writeField(field, value.f2);
            break;
          case 'f3':
            context.writeField(field, value.f3);
            break;
          case 'f4':
            context.writeField(field, value.f4);
            break;
          case 'f5':
            context.writeField(field, value.f5);
            break;
          case 'f6':
            context.writeField(field, value.f6);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_Item1ForyFields[0], value.f1);
    context.writeField(_Item1ForyFields[1], value.f2);
    context.writeField(_Item1ForyFields[2], value.f3);
    context.writeField(_Item1ForyFields[3], value.f4);
    context.writeField(_Item1ForyFields[4], value.f5);
    context.writeField(_Item1ForyFields[5], value.f6);
  }

  @override
  Item1 read(ReadContext context) {
    final value = Item1();
    context.reference(value);
    value.f1 = _readItem1F1(
        context.readField<Object?>(_Item1ForyFields[0], value.f1), value.f1);
    value.f2 = _readItem1F2(
        context.readField<Object?>(_Item1ForyFields[1], value.f2), value.f2);
    value.f3 = _readItem1F3(
        context.readField<Object?>(_Item1ForyFields[2], value.f3), value.f3);
    value.f4 = _readItem1F4(
        context.readField<Object?>(_Item1ForyFields[3], value.f4), value.f4);
    value.f5 = _readItem1F5(
        context.readField<Object?>(_Item1ForyFields[4], value.f5), value.f5);
    value.f6 = _readItem1F6(
        context.readField<Object?>(_Item1ForyFields[5], value.f6), value.f6);
    return value;
  }
}

const _Item1ForySerializer _item1ForySerializer = _Item1ForySerializer();

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

const List<Map<String, Object?>> _StructWithUnion2ForyFields =
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
      'arguments': const <Object?>[],
    },
  },
];

final class _StructWithUnion2ForySerializer
    extends Serializer<StructWithUnion2> {
  const _StructWithUnion2ForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _StructWithUnion2ForyFields;
  @override
  void write(WriteContext context, StructWithUnion2 value) {
    final compatibleFields =
        context.compatibleFieldOrder(_StructWithUnion2ForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'union':
            context.writeField(field, value.union);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_StructWithUnion2ForyFields[0], value.union);
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final value = StructWithUnion2();
    context.reference(value);
    value.union = _readStructWithUnion2Union(
        context.readField<Object?>(_StructWithUnion2ForyFields[0], value.union),
        value.union);
    return value;
  }
}

const _StructWithUnion2ForySerializer _structWithUnion2ForySerializer =
    _StructWithUnion2ForySerializer();

Union2 _readStructWithUnion2Union(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Union2
          : (throw StateError('Received null for non-nullable field union.')))
      : value as Union2;
}

const List<Map<String, Object?>> _StructWithListForyFields =
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
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _StructWithListForySerializer extends Serializer<StructWithList> {
  const _StructWithListForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _StructWithListForyFields;
  @override
  void write(WriteContext context, StructWithList value) {
    final compatibleFields =
        context.compatibleFieldOrder(_StructWithListForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'items':
            context.writeField(field, value.items);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_StructWithListForyFields[0], value.items);
  }

  @override
  StructWithList read(ReadContext context) {
    final value = StructWithList();
    context.reference(value);
    value.items = _readStructWithListItems(
        context.readField<Object?>(_StructWithListForyFields[0], value.items),
        value.items);
    return value;
  }
}

const _StructWithListForySerializer _structWithListForySerializer =
    _StructWithListForySerializer();

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

const List<Map<String, Object?>> _StructWithMapForyFields =
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _StructWithMapForySerializer extends Serializer<StructWithMap> {
  const _StructWithMapForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _StructWithMapForyFields;
  @override
  void write(WriteContext context, StructWithMap value) {
    final compatibleFields =
        context.compatibleFieldOrder(_StructWithMapForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'data':
            context.writeField(field, value.data);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_StructWithMapForyFields[0], value.data);
  }

  @override
  StructWithMap read(ReadContext context) {
    final value = StructWithMap();
    context.reference(value);
    value.data = _readStructWithMapData(
        context.readField<Object?>(_StructWithMapForyFields[0], value.data),
        value.data);
    return value;
  }
}

const _StructWithMapForySerializer _structWithMapForySerializer =
    _StructWithMapForySerializer();

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

const List<Map<String, Object?>> _MyStructForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
    },
  },
];

final class _MyStructForySerializer extends Serializer<MyStruct> {
  const _MyStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _MyStructForyFields;
  @override
  void write(WriteContext context, MyStruct value) {
    final compatibleFields = context.compatibleFieldOrder(_MyStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'id':
            context.writeField(field, value.id);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_MyStructForyFields[0], value.id);
  }

  @override
  MyStruct read(ReadContext context) {
    final value = MyStruct();
    context.reference(value);
    value.id = _readMyStructId(
        context.readField<Object?>(_MyStructForyFields[0], value.id), value.id);
    return value;
  }
}

const _MyStructForySerializer _myStructForySerializer =
    _MyStructForySerializer();

int _readMyStructId(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as int
          : (throw StateError('Received null for non-nullable field id.')))
      : (value as Int32).value;
}

const List<Map<String, Object?>> _MyWrapperForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _MyWrapperForySerializer extends Serializer<MyWrapper> {
  const _MyWrapperForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _MyWrapperForyFields;
  @override
  void write(WriteContext context, MyWrapper value) {
    final compatibleFields = context.compatibleFieldOrder(_MyWrapperForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'color':
            context.writeField(field, value.color);
            break;
          case 'my_ext':
            context.writeField(field, value.myExt);
            break;
          case 'my_struct':
            context.writeField(field, value.myStruct);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_MyWrapperForyFields[0], value.color);
    context.writeField(_MyWrapperForyFields[1], value.myExt);
    context.writeField(_MyWrapperForyFields[2], value.myStruct);
  }

  @override
  MyWrapper read(ReadContext context) {
    final value = MyWrapper();
    context.reference(value);
    value.color = _readMyWrapperColor(
        context.readField<Object?>(_MyWrapperForyFields[0], value.color),
        value.color);
    value.myExt = _readMyWrapperMyExt(
        context.readField<Object?>(_MyWrapperForyFields[1], value.myExt),
        value.myExt);
    value.myStruct = _readMyWrapperMyStruct(
        context.readField<Object?>(_MyWrapperForyFields[2], value.myStruct),
        value.myStruct);
    return value;
  }
}

const _MyWrapperForySerializer _myWrapperForySerializer =
    _MyWrapperForySerializer();

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

const List<Map<String, Object?>> _EmptyWrapperForyFields =
    <Map<String, Object?>>[];

final class _EmptyWrapperForySerializer extends Serializer<EmptyWrapper> {
  const _EmptyWrapperForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _EmptyWrapperForyFields;
  @override
  void write(WriteContext context, EmptyWrapper value) {
    final compatibleFields =
        context.compatibleFieldOrder(_EmptyWrapperForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
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

const _EmptyWrapperForySerializer _emptyWrapperForySerializer =
    _EmptyWrapperForySerializer();

const List<Map<String, Object?>> _VersionCheckStructForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _VersionCheckStructForySerializer
    extends Serializer<VersionCheckStruct> {
  const _VersionCheckStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _VersionCheckStructForyFields;
  @override
  void write(WriteContext context, VersionCheckStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_VersionCheckStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f3':
            context.writeField(field, value.f3);
            break;
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f2':
            context.writeField(field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_VersionCheckStructForyFields[0], value.f3);
    context.writeField(_VersionCheckStructForyFields[1], value.f1);
    context.writeField(_VersionCheckStructForyFields[2], value.f2);
  }

  @override
  VersionCheckStruct read(ReadContext context) {
    final value = VersionCheckStruct();
    context.reference(value);
    value.f3 = _readVersionCheckStructF3(
        context.readField<Object?>(_VersionCheckStructForyFields[0], value.f3),
        value.f3);
    value.f1 = _readVersionCheckStructF1(
        context.readField<Object?>(_VersionCheckStructForyFields[1], value.f1),
        value.f1);
    value.f2 = _readVersionCheckStructF2(
        context.readField<Object?>(_VersionCheckStructForyFields[2], value.f2),
        value.f2);
    return value;
  }
}

const _VersionCheckStructForySerializer _versionCheckStructForySerializer =
    _VersionCheckStructForySerializer();

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

const List<Map<String, Object?>> _DogForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _DogForySerializer extends Serializer<Dog> {
  const _DogForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _DogForyFields;
  @override
  void write(WriteContext context, Dog value) {
    final compatibleFields = context.compatibleFieldOrder(_DogForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'age':
            context.writeField(field, value.age);
            break;
          case 'name':
            context.writeField(field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_DogForyFields[0], value.age);
    context.writeField(_DogForyFields[1], value.name);
  }

  @override
  Dog read(ReadContext context) {
    final value = Dog();
    context.reference(value);
    value.age = _readDogAge(
        context.readField<Object?>(_DogForyFields[0], value.age), value.age);
    value.name = _readDogName(
        context.readField<Object?>(_DogForyFields[1], value.name), value.name);
    return value;
  }
}

const _DogForySerializer _dogForySerializer = _DogForySerializer();

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

const List<Map<String, Object?>> _CatForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _CatForySerializer extends Serializer<Cat> {
  const _CatForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _CatForyFields;
  @override
  void write(WriteContext context, Cat value) {
    final compatibleFields = context.compatibleFieldOrder(_CatForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'age':
            context.writeField(field, value.age);
            break;
          case 'lives':
            context.writeField(field, value.lives);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_CatForyFields[0], value.age);
    context.writeField(_CatForyFields[1], value.lives);
  }

  @override
  Cat read(ReadContext context) {
    final value = Cat();
    context.reference(value);
    value.age = _readCatAge(
        context.readField<Object?>(_CatForyFields[0], value.age), value.age);
    value.lives = _readCatLives(
        context.readField<Object?>(_CatForyFields[1], value.lives),
        value.lives);
    return value;
  }
}

const _CatForySerializer _catForySerializer = _CatForySerializer();

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

const List<Map<String, Object?>> _AnimalListHolderForyFields =
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
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _AnimalListHolderForySerializer
    extends Serializer<AnimalListHolder> {
  const _AnimalListHolderForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _AnimalListHolderForyFields;
  @override
  void write(WriteContext context, AnimalListHolder value) {
    final compatibleFields =
        context.compatibleFieldOrder(_AnimalListHolderForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'animals':
            context.writeField(field, value.animals);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_AnimalListHolderForyFields[0], value.animals);
  }

  @override
  AnimalListHolder read(ReadContext context) {
    final value = AnimalListHolder();
    context.reference(value);
    value.animals = _readAnimalListHolderAnimals(
        context.readField<Object?>(
            _AnimalListHolderForyFields[0], value.animals),
        value.animals);
    return value;
  }
}

const _AnimalListHolderForySerializer _animalListHolderForySerializer =
    _AnimalListHolderForySerializer();

List<Animal> _readAnimalListHolderAnimals(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<Animal>
          : (throw StateError('Received null for non-nullable field animals.')))
      : List<Animal>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as Animal));
}

const List<Map<String, Object?>> _AnimalMapHolderForyFields =
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': Animal,
          'typeId': 28,
          'nullable': true,
          'ref': false,
          'dynamic': true,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _AnimalMapHolderForySerializer extends Serializer<AnimalMapHolder> {
  const _AnimalMapHolderForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _AnimalMapHolderForyFields;
  @override
  void write(WriteContext context, AnimalMapHolder value) {
    final compatibleFields =
        context.compatibleFieldOrder(_AnimalMapHolderForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'animal_map':
            context.writeField(field, value.animalMap);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_AnimalMapHolderForyFields[0], value.animalMap);
  }

  @override
  AnimalMapHolder read(ReadContext context) {
    final value = AnimalMapHolder();
    context.reference(value);
    value.animalMap = _readAnimalMapHolderAnimalMap(
        context.readField<Object?>(
            _AnimalMapHolderForyFields[0], value.animalMap),
        value.animalMap);
    return value;
  }
}

const _AnimalMapHolderForySerializer _animalMapHolderForySerializer =
    _AnimalMapHolderForySerializer();

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

const List<Map<String, Object?>> _EmptyStructForyFields =
    <Map<String, Object?>>[];

final class _EmptyStructForySerializer extends Serializer<EmptyStruct> {
  const _EmptyStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _EmptyStructForyFields;
  @override
  void write(WriteContext context, EmptyStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_EmptyStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
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

const _EmptyStructForySerializer _emptyStructForySerializer =
    _EmptyStructForySerializer();

const List<Map<String, Object?>> _OneStringFieldStructForyFields =
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
      'arguments': const <Object?>[],
    },
  },
];

final class _OneStringFieldStructForySerializer
    extends Serializer<OneStringFieldStruct> {
  const _OneStringFieldStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _OneStringFieldStructForyFields;
  @override
  void write(WriteContext context, OneStringFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_OneStringFieldStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_OneStringFieldStructForyFields[0], value.f1);
  }

  @override
  OneStringFieldStruct read(ReadContext context) {
    final value = OneStringFieldStruct();
    context.reference(value);
    value.f1 = _readOneStringFieldStructF1(
        context.readField<Object?>(
            _OneStringFieldStructForyFields[0], value.f1),
        value.f1);
    return value;
  }
}

const _OneStringFieldStructForySerializer _oneStringFieldStructForySerializer =
    _OneStringFieldStructForySerializer();

String? _readOneStringFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

const List<Map<String, Object?>> _TwoStringFieldStructForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _TwoStringFieldStructForySerializer
    extends Serializer<TwoStringFieldStruct> {
  const _TwoStringFieldStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _TwoStringFieldStructForyFields;
  @override
  void write(WriteContext context, TwoStringFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_TwoStringFieldStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f2':
            context.writeField(field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_TwoStringFieldStructForyFields[0], value.f1);
    context.writeField(_TwoStringFieldStructForyFields[1], value.f2);
  }

  @override
  TwoStringFieldStruct read(ReadContext context) {
    final value = TwoStringFieldStruct();
    context.reference(value);
    value.f1 = _readTwoStringFieldStructF1(
        context.readField<Object?>(
            _TwoStringFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoStringFieldStructF2(
        context.readField<Object?>(
            _TwoStringFieldStructForyFields[1], value.f2),
        value.f2);
    return value;
  }
}

const _TwoStringFieldStructForySerializer _twoStringFieldStructForySerializer =
    _TwoStringFieldStructForySerializer();

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

const List<Map<String, Object?>> _OneEnumFieldStructForyFields =
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
      'arguments': const <Object?>[],
    },
  },
];

final class _OneEnumFieldStructForySerializer
    extends Serializer<OneEnumFieldStruct> {
  const _OneEnumFieldStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _OneEnumFieldStructForyFields;
  @override
  void write(WriteContext context, OneEnumFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_OneEnumFieldStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_OneEnumFieldStructForyFields[0], value.f1);
  }

  @override
  OneEnumFieldStruct read(ReadContext context) {
    final value = OneEnumFieldStruct();
    context.reference(value);
    value.f1 = _readOneEnumFieldStructF1(
        context.readField<Object?>(_OneEnumFieldStructForyFields[0], value.f1),
        value.f1);
    return value;
  }
}

const _OneEnumFieldStructForySerializer _oneEnumFieldStructForySerializer =
    _OneEnumFieldStructForySerializer();

TestEnum _readOneEnumFieldStructF1(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as TestEnum
          : (throw StateError('Received null for non-nullable field f1.')))
      : value as TestEnum;
}

const List<Map<String, Object?>> _TwoEnumFieldStructForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _TwoEnumFieldStructForySerializer
    extends Serializer<TwoEnumFieldStruct> {
  const _TwoEnumFieldStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _TwoEnumFieldStructForyFields;
  @override
  void write(WriteContext context, TwoEnumFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_TwoEnumFieldStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'f1':
            context.writeField(field, value.f1);
            break;
          case 'f2':
            context.writeField(field, value.f2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_TwoEnumFieldStructForyFields[0], value.f1);
    context.writeField(_TwoEnumFieldStructForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStruct read(ReadContext context) {
    final value = TwoEnumFieldStruct();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructF1(
        context.readField<Object?>(_TwoEnumFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructF2(
        context.readField<Object?>(_TwoEnumFieldStructForyFields[1], value.f2),
        value.f2);
    return value;
  }
}

const _TwoEnumFieldStructForySerializer _twoEnumFieldStructForySerializer =
    _TwoEnumFieldStructForySerializer();

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
    _NullableComprehensiveSchemaConsistentForyFields = <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _NullableComprehensiveSchemaConsistentForySerializer
    extends Serializer<NullableComprehensiveSchemaConsistent> {
  const _NullableComprehensiveSchemaConsistentForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _NullableComprehensiveSchemaConsistentForyFields;
  @override
  void write(
      WriteContext context, NullableComprehensiveSchemaConsistent value) {
    final compatibleFields = context
        .compatibleFieldOrder(_NullableComprehensiveSchemaConsistentForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'double_field':
            context.writeField(field, value.doubleField);
            break;
          case 'float_field':
            context.writeField(field, value.floatField);
            break;
          case 'short_field':
            context.writeField(field, value.shortField);
            break;
          case 'byte_field':
            context.writeField(field, value.byteField);
            break;
          case 'bool_field':
            context.writeField(field, value.boolField);
            break;
          case 'long_field':
            context.writeField(field, value.longField);
            break;
          case 'int_field':
            context.writeField(field, value.intField);
            break;
          case 'nullable_double':
            context.writeField(field, value.nullableDouble);
            break;
          case 'nullable_float':
            context.writeField(field, value.nullableFloat);
            break;
          case 'nullable_bool':
            context.writeField(field, value.nullableBool);
            break;
          case 'nullable_long':
            context.writeField(field, value.nullableLong);
            break;
          case 'nullable_int':
            context.writeField(field, value.nullableInt);
            break;
          case 'nullable_string':
            context.writeField(field, value.nullableString);
            break;
          case 'string_field':
            context.writeField(field, value.stringField);
            break;
          case 'list_field':
            context.writeField(field, value.listField);
            break;
          case 'nullable_list':
            context.writeField(field, value.nullableList);
            break;
          case 'nullable_set':
            context.writeField(field, value.nullableSet);
            break;
          case 'set_field':
            context.writeField(field, value.setField);
            break;
          case 'map_field':
            context.writeField(field, value.mapField);
            break;
          case 'nullable_map':
            context.writeField(field, value.nullableMap);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[0], value.doubleField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[1], value.floatField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[2], value.shortField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[3], value.byteField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[4], value.boolField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[5], value.longField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[6], value.intField);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[7],
        value.nullableDouble);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[8],
        value.nullableFloat);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[9],
        value.nullableBool);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[10],
        value.nullableLong);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[11],
        value.nullableInt);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[12],
        value.nullableString);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[13],
        value.stringField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[14], value.listField);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[15],
        value.nullableList);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[16],
        value.nullableSet);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[17], value.setField);
    context.writeField(
        _NullableComprehensiveSchemaConsistentForyFields[18], value.mapField);
    context.writeField(_NullableComprehensiveSchemaConsistentForyFields[19],
        value.nullableMap);
  }

  @override
  NullableComprehensiveSchemaConsistent read(ReadContext context) {
    final value = NullableComprehensiveSchemaConsistent();
    context.reference(value);
    value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[0],
            value.doubleField),
        value.doubleField);
    value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[1],
            value.floatField),
        value.floatField);
    value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[2],
            value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[3],
            value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[4],
            value.boolField),
        value.boolField);
    value.longField = _readNullableComprehensiveSchemaConsistentLongField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[5],
            value.longField),
        value.longField);
    value.intField = _readNullableComprehensiveSchemaConsistentIntField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[6],
            value.intField),
        value.intField);
    value.nullableDouble =
        _readNullableComprehensiveSchemaConsistentNullableDouble(
            context.readField<Object?>(
                _NullableComprehensiveSchemaConsistentForyFields[7],
                value.nullableDouble),
            value.nullableDouble);
    value.nullableFloat =
        _readNullableComprehensiveSchemaConsistentNullableFloat(
            context.readField<Object?>(
                _NullableComprehensiveSchemaConsistentForyFields[8],
                value.nullableFloat),
            value.nullableFloat);
    value.nullableBool = _readNullableComprehensiveSchemaConsistentNullableBool(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[9],
            value.nullableBool),
        value.nullableBool);
    value.nullableLong = _readNullableComprehensiveSchemaConsistentNullableLong(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[10],
            value.nullableLong),
        value.nullableLong);
    value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[11],
            value.nullableInt),
        value.nullableInt);
    value.nullableString =
        _readNullableComprehensiveSchemaConsistentNullableString(
            context.readField<Object?>(
                _NullableComprehensiveSchemaConsistentForyFields[12],
                value.nullableString),
            value.nullableString);
    value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[13],
            value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveSchemaConsistentListField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[14],
            value.listField),
        value.listField);
    value.nullableList = _readNullableComprehensiveSchemaConsistentNullableList(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[15],
            value.nullableList),
        value.nullableList);
    value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[16],
            value.nullableSet),
        value.nullableSet);
    value.setField = _readNullableComprehensiveSchemaConsistentSetField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[17],
            value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[18],
            value.mapField),
        value.mapField);
    value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
        context.readField<Object?>(
            _NullableComprehensiveSchemaConsistentForyFields[19],
            value.nullableMap),
        value.nullableMap);
    return value;
  }
}

const _NullableComprehensiveSchemaConsistentForySerializer
    _nullableComprehensiveSchemaConsistentForySerializer =
    _NullableComprehensiveSchemaConsistentForySerializer();

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

const List<Map<String, Object?>> _NullableComprehensiveCompatibleForyFields =
    <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
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
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': String,
          'typeId': 21,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _NullableComprehensiveCompatibleForySerializer
    extends Serializer<NullableComprehensiveCompatible> {
  const _NullableComprehensiveCompatibleForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _NullableComprehensiveCompatibleForyFields;
  @override
  void write(WriteContext context, NullableComprehensiveCompatible value) {
    final compatibleFields = context
        .compatibleFieldOrder(_NullableComprehensiveCompatibleForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'boxed_double':
            context.writeField(field, value.boxedDouble);
            break;
          case 'double_field':
            context.writeField(field, value.doubleField);
            break;
          case 'nullable_double1':
            context.writeField(field, value.nullableDouble1);
            break;
          case 'boxed_float':
            context.writeField(field, value.boxedFloat);
            break;
          case 'float_field':
            context.writeField(field, value.floatField);
            break;
          case 'nullable_float1':
            context.writeField(field, value.nullableFloat1);
            break;
          case 'short_field':
            context.writeField(field, value.shortField);
            break;
          case 'byte_field':
            context.writeField(field, value.byteField);
            break;
          case 'bool_field':
            context.writeField(field, value.boolField);
            break;
          case 'boxed_bool':
            context.writeField(field, value.boxedBool);
            break;
          case 'nullable_bool1':
            context.writeField(field, value.nullableBool1);
            break;
          case 'boxed_long':
            context.writeField(field, value.boxedLong);
            break;
          case 'long_field':
            context.writeField(field, value.longField);
            break;
          case 'nullable_long1':
            context.writeField(field, value.nullableLong1);
            break;
          case 'boxed_int':
            context.writeField(field, value.boxedInt);
            break;
          case 'int_field':
            context.writeField(field, value.intField);
            break;
          case 'nullable_int1':
            context.writeField(field, value.nullableInt1);
            break;
          case 'nullable_string2':
            context.writeField(field, value.nullableString2);
            break;
          case 'string_field':
            context.writeField(field, value.stringField);
            break;
          case 'list_field':
            context.writeField(field, value.listField);
            break;
          case 'nullable_list2':
            context.writeField(field, value.nullableList2);
            break;
          case 'nullable_set2':
            context.writeField(field, value.nullableSet2);
            break;
          case 'set_field':
            context.writeField(field, value.setField);
            break;
          case 'map_field':
            context.writeField(field, value.mapField);
            break;
          case 'nullable_map2':
            context.writeField(field, value.nullableMap2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[0], value.boxedDouble);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[1], value.doubleField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[2], value.nullableDouble1);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[3], value.boxedFloat);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[4], value.floatField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[5], value.nullableFloat1);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[6], value.shortField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[7], value.byteField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[8], value.boolField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[9], value.boxedBool);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[10], value.nullableBool1);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[11], value.boxedLong);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[12], value.longField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[13], value.nullableLong1);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[14], value.boxedInt);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[15], value.intField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[16], value.nullableInt1);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[17], value.nullableString2);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[18], value.stringField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[19], value.listField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[20], value.nullableList2);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[21], value.nullableSet2);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[22], value.setField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[23], value.mapField);
    context.writeField(
        _NullableComprehensiveCompatibleForyFields[24], value.nullableMap2);
  }

  @override
  NullableComprehensiveCompatible read(ReadContext context) {
    final value = NullableComprehensiveCompatible();
    context.reference(value);
    value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[0], value.boxedDouble),
        value.boxedDouble);
    value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[1], value.doubleField),
        value.doubleField);
    value.nullableDouble1 = _readNullableComprehensiveCompatibleNullableDouble1(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[2],
            value.nullableDouble1),
        value.nullableDouble1);
    value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[3], value.boxedFloat),
        value.boxedFloat);
    value.floatField = _readNullableComprehensiveCompatibleFloatField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[4], value.floatField),
        value.floatField);
    value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[5],
            value.nullableFloat1),
        value.nullableFloat1);
    value.shortField = _readNullableComprehensiveCompatibleShortField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[6], value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveCompatibleByteField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[7], value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveCompatibleBoolField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[8], value.boolField),
        value.boolField);
    value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[9], value.boxedBool),
        value.boxedBool);
    value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[10],
            value.nullableBool1),
        value.nullableBool1);
    value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[11], value.boxedLong),
        value.boxedLong);
    value.longField = _readNullableComprehensiveCompatibleLongField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[12], value.longField),
        value.longField);
    value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[13],
            value.nullableLong1),
        value.nullableLong1);
    value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[14], value.boxedInt),
        value.boxedInt);
    value.intField = _readNullableComprehensiveCompatibleIntField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[15], value.intField),
        value.intField);
    value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[16], value.nullableInt1),
        value.nullableInt1);
    value.nullableString2 = _readNullableComprehensiveCompatibleNullableString2(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[17],
            value.nullableString2),
        value.nullableString2);
    value.stringField = _readNullableComprehensiveCompatibleStringField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[18], value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveCompatibleListField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[19], value.listField),
        value.listField);
    value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[20],
            value.nullableList2),
        value.nullableList2);
    value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[21], value.nullableSet2),
        value.nullableSet2);
    value.setField = _readNullableComprehensiveCompatibleSetField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[22], value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveCompatibleMapField(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[23], value.mapField),
        value.mapField);
    value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
        context.readField<Object?>(
            _NullableComprehensiveCompatibleForyFields[24], value.nullableMap2),
        value.nullableMap2);
    return value;
  }
}

const _NullableComprehensiveCompatibleForySerializer
    _nullableComprehensiveCompatibleForySerializer =
    _NullableComprehensiveCompatibleForySerializer();

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

const List<Map<String, Object?>> _RefInnerSchemaConsistentForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _RefInnerSchemaConsistentForySerializer
    extends Serializer<RefInnerSchemaConsistent> {
  const _RefInnerSchemaConsistentForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefInnerSchemaConsistentForyFields;
  @override
  void write(WriteContext context, RefInnerSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_RefInnerSchemaConsistentForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'id':
            context.writeField(field, value.id);
            break;
          case 'name':
            context.writeField(field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefInnerSchemaConsistentForyFields[0], value.id);
    context.writeField(_RefInnerSchemaConsistentForyFields[1], value.name);
  }

  @override
  RefInnerSchemaConsistent read(ReadContext context) {
    final value = RefInnerSchemaConsistent();
    context.reference(value);
    value.id = _readRefInnerSchemaConsistentId(
        context.readField<Object?>(
            _RefInnerSchemaConsistentForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerSchemaConsistentName(
        context.readField<Object?>(
            _RefInnerSchemaConsistentForyFields[1], value.name),
        value.name);
    return value;
  }
}

const _RefInnerSchemaConsistentForySerializer
    _refInnerSchemaConsistentForySerializer =
    _RefInnerSchemaConsistentForySerializer();

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

const List<Map<String, Object?>> _RefOuterSchemaConsistentForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _RefOuterSchemaConsistentForySerializer
    extends Serializer<RefOuterSchemaConsistent> {
  const _RefOuterSchemaConsistentForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefOuterSchemaConsistentForyFields;
  @override
  void write(WriteContext context, RefOuterSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_RefOuterSchemaConsistentForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'inner1':
            context.writeField(field, value.inner1);
            break;
          case 'inner2':
            context.writeField(field, value.inner2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefOuterSchemaConsistentForyFields[0], value.inner1);
    context.writeField(_RefOuterSchemaConsistentForyFields[1], value.inner2);
  }

  @override
  RefOuterSchemaConsistent read(ReadContext context) {
    final value = RefOuterSchemaConsistent();
    context.reference(value);
    value.inner1 = _readRefOuterSchemaConsistentInner1(
        context.readField<Object?>(
            _RefOuterSchemaConsistentForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterSchemaConsistentInner2(
        context.readField<Object?>(
            _RefOuterSchemaConsistentForyFields[1], value.inner2),
        value.inner2);
    return value;
  }
}

const _RefOuterSchemaConsistentForySerializer
    _refOuterSchemaConsistentForySerializer =
    _RefOuterSchemaConsistentForySerializer();

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

const List<Map<String, Object?>> _RefInnerCompatibleForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _RefInnerCompatibleForySerializer
    extends Serializer<RefInnerCompatible> {
  const _RefInnerCompatibleForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefInnerCompatibleForyFields;
  @override
  void write(WriteContext context, RefInnerCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_RefInnerCompatibleForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'id':
            context.writeField(field, value.id);
            break;
          case 'name':
            context.writeField(field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefInnerCompatibleForyFields[0], value.id);
    context.writeField(_RefInnerCompatibleForyFields[1], value.name);
  }

  @override
  RefInnerCompatible read(ReadContext context) {
    final value = RefInnerCompatible();
    context.reference(value);
    value.id = _readRefInnerCompatibleId(
        context.readField<Object?>(_RefInnerCompatibleForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerCompatibleName(
        context.readField<Object?>(
            _RefInnerCompatibleForyFields[1], value.name),
        value.name);
    return value;
  }
}

const _RefInnerCompatibleForySerializer _refInnerCompatibleForySerializer =
    _RefInnerCompatibleForySerializer();

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

const List<Map<String, Object?>> _RefOuterCompatibleForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _RefOuterCompatibleForySerializer
    extends Serializer<RefOuterCompatible> {
  const _RefOuterCompatibleForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefOuterCompatibleForyFields;
  @override
  void write(WriteContext context, RefOuterCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_RefOuterCompatibleForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'inner1':
            context.writeField(field, value.inner1);
            break;
          case 'inner2':
            context.writeField(field, value.inner2);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefOuterCompatibleForyFields[0], value.inner1);
    context.writeField(_RefOuterCompatibleForyFields[1], value.inner2);
  }

  @override
  RefOuterCompatible read(ReadContext context) {
    final value = RefOuterCompatible();
    context.reference(value);
    value.inner1 = _readRefOuterCompatibleInner1(
        context.readField<Object?>(
            _RefOuterCompatibleForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterCompatibleInner2(
        context.readField<Object?>(
            _RefOuterCompatibleForyFields[1], value.inner2),
        value.inner2);
    return value;
  }
}

const _RefOuterCompatibleForySerializer _refOuterCompatibleForySerializer =
    _RefOuterCompatibleForySerializer();

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

const List<Map<String, Object?>> _RefOverrideElementForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _RefOverrideElementForySerializer
    extends Serializer<RefOverrideElement> {
  const _RefOverrideElementForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefOverrideElementForyFields;
  @override
  void write(WriteContext context, RefOverrideElement value) {
    final compatibleFields =
        context.compatibleFieldOrder(_RefOverrideElementForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'id':
            context.writeField(field, value.id);
            break;
          case 'name':
            context.writeField(field, value.name);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefOverrideElementForyFields[0], value.id);
    context.writeField(_RefOverrideElementForyFields[1], value.name);
  }

  @override
  RefOverrideElement read(ReadContext context) {
    final value = RefOverrideElement();
    context.reference(value);
    value.id = _readRefOverrideElementId(
        context.readField<Object?>(_RefOverrideElementForyFields[0], value.id),
        value.id);
    value.name = _readRefOverrideElementName(
        context.readField<Object?>(
            _RefOverrideElementForyFields[1], value.name),
        value.name);
    return value;
  }
}

const _RefOverrideElementForySerializer _refOverrideElementForySerializer =
    _RefOverrideElementForySerializer();

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

const List<Map<String, Object?>> _CircularRefStructForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _CircularRefStructForySerializer
    extends Serializer<CircularRefStruct> {
  const _CircularRefStructForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _CircularRefStructForyFields;
  @override
  void write(WriteContext context, CircularRefStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_CircularRefStructForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'name':
            context.writeField(field, value.name);
            break;
          case 'self_ref':
            context.writeField(field, value.selfRef);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_CircularRefStructForyFields[0], value.name);
    context.writeField(_CircularRefStructForyFields[1], value.selfRef);
  }

  @override
  CircularRefStruct read(ReadContext context) {
    final value = CircularRefStruct();
    context.reference(value);
    value.name = _readCircularRefStructName(
        context.readField<Object?>(_CircularRefStructForyFields[0], value.name),
        value.name);
    value.selfRef = _readCircularRefStructSelfRef(
        context.readField<Object?>(
            _CircularRefStructForyFields[1], value.selfRef),
        value.selfRef);
    return value;
  }
}

const _CircularRefStructForySerializer _circularRefStructForySerializer =
    _CircularRefStructForySerializer();

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

const List<Map<String, Object?>> _UnsignedSchemaConsistentForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _UnsignedSchemaConsistentForySerializer
    extends Serializer<UnsignedSchemaConsistent> {
  const _UnsignedSchemaConsistentForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _UnsignedSchemaConsistentForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_UnsignedSchemaConsistentForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'u64_fixed_field':
            context.writeField(field, value.u64FixedField);
            break;
          case 'u32_fixed_field':
            context.writeField(field, value.u32FixedField);
            break;
          case 'u16_field':
            context.writeField(field, value.u16Field);
            break;
          case 'u8_field':
            context.writeField(field, value.u8Field);
            break;
          case 'u64_tagged_field':
            context.writeField(field, value.u64TaggedField);
            break;
          case 'u64_var_field':
            context.writeField(field, value.u64VarField);
            break;
          case 'u32_var_field':
            context.writeField(field, value.u32VarField);
            break;
          case 'u64_fixed_nullable_field':
            context.writeField(field, value.u64FixedNullableField);
            break;
          case 'u32_fixed_nullable_field':
            context.writeField(field, value.u32FixedNullableField);
            break;
          case 'u16_nullable_field':
            context.writeField(field, value.u16NullableField);
            break;
          case 'u8_nullable_field':
            context.writeField(field, value.u8NullableField);
            break;
          case 'u64_tagged_nullable_field':
            context.writeField(field, value.u64TaggedNullableField);
            break;
          case 'u64_var_nullable_field':
            context.writeField(field, value.u64VarNullableField);
            break;
          case 'u32_var_nullable_field':
            context.writeField(field, value.u32VarNullableField);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(
        _UnsignedSchemaConsistentForyFields[0], value.u64FixedField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[1], value.u32FixedField);
    context.writeField(_UnsignedSchemaConsistentForyFields[2], value.u16Field);
    context.writeField(_UnsignedSchemaConsistentForyFields[3], value.u8Field);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[4], value.u64TaggedField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[5], value.u64VarField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[6], value.u32VarField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[7], value.u64FixedNullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[8], value.u32FixedNullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[9], value.u16NullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[10], value.u8NullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[11], value.u64TaggedNullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[12], value.u64VarNullableField);
    context.writeField(
        _UnsignedSchemaConsistentForyFields[13], value.u32VarNullableField);
  }

  @override
  UnsignedSchemaConsistent read(ReadContext context) {
    final value = UnsignedSchemaConsistent();
    context.reference(value);
    value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[0], value.u64FixedField),
        value.u64FixedField);
    value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[1], value.u32FixedField),
        value.u32FixedField);
    value.u16Field = _readUnsignedSchemaConsistentU16Field(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[2], value.u16Field),
        value.u16Field);
    value.u8Field = _readUnsignedSchemaConsistentU8Field(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[3], value.u8Field),
        value.u8Field);
    value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[4], value.u64TaggedField),
        value.u64TaggedField);
    value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[5], value.u64VarField),
        value.u64VarField);
    value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[6], value.u32VarField),
        value.u32VarField);
    value.u64FixedNullableField =
        _readUnsignedSchemaConsistentU64FixedNullableField(
            context.readField<Object?>(_UnsignedSchemaConsistentForyFields[7],
                value.u64FixedNullableField),
            value.u64FixedNullableField);
    value.u32FixedNullableField =
        _readUnsignedSchemaConsistentU32FixedNullableField(
            context.readField<Object?>(_UnsignedSchemaConsistentForyFields[8],
                value.u32FixedNullableField),
            value.u32FixedNullableField);
    value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[9], value.u16NullableField),
        value.u16NullableField);
    value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
        context.readField<Object?>(
            _UnsignedSchemaConsistentForyFields[10], value.u8NullableField),
        value.u8NullableField);
    value.u64TaggedNullableField =
        _readUnsignedSchemaConsistentU64TaggedNullableField(
            context.readField<Object?>(_UnsignedSchemaConsistentForyFields[11],
                value.u64TaggedNullableField),
            value.u64TaggedNullableField);
    value.u64VarNullableField =
        _readUnsignedSchemaConsistentU64VarNullableField(
            context.readField<Object?>(_UnsignedSchemaConsistentForyFields[12],
                value.u64VarNullableField),
            value.u64VarNullableField);
    value.u32VarNullableField =
        _readUnsignedSchemaConsistentU32VarNullableField(
            context.readField<Object?>(_UnsignedSchemaConsistentForyFields[13],
                value.u32VarNullableField),
            value.u32VarNullableField);
    return value;
  }
}

const _UnsignedSchemaConsistentForySerializer
    _unsignedSchemaConsistentForySerializer =
    _UnsignedSchemaConsistentForySerializer();

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

const List<Map<String, Object?>> _UnsignedSchemaConsistentSimpleForyFields =
    <Map<String, Object?>>[
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _UnsignedSchemaConsistentSimpleForySerializer
    extends Serializer<UnsignedSchemaConsistentSimple> {
  const _UnsignedSchemaConsistentSimpleForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _UnsignedSchemaConsistentSimpleForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaConsistentSimple value) {
    final compatibleFields =
        context.compatibleFieldOrder(_UnsignedSchemaConsistentSimpleForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'u64_tagged':
            context.writeField(field, value.u64Tagged);
            break;
          case 'u64_tagged_nullable':
            context.writeField(field, value.u64TaggedNullable);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(
        _UnsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged);
    context.writeField(
        _UnsignedSchemaConsistentSimpleForyFields[1], value.u64TaggedNullable);
  }

  @override
  UnsignedSchemaConsistentSimple read(ReadContext context) {
    final value = UnsignedSchemaConsistentSimple();
    context.reference(value);
    value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
        context.readField<Object?>(
            _UnsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged),
        value.u64Tagged);
    value.u64TaggedNullable =
        _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
            context.readField<Object?>(
                _UnsignedSchemaConsistentSimpleForyFields[1],
                value.u64TaggedNullable),
            value.u64TaggedNullable);
    return value;
  }
}

const _UnsignedSchemaConsistentSimpleForySerializer
    _unsignedSchemaConsistentSimpleForySerializer =
    _UnsignedSchemaConsistentSimpleForySerializer();

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

const List<Map<String, Object?>> _UnsignedSchemaCompatibleForyFields =
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
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
      'arguments': const <Object?>[],
    },
  },
];

final class _UnsignedSchemaCompatibleForySerializer
    extends Serializer<UnsignedSchemaCompatible> {
  const _UnsignedSchemaCompatibleForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _UnsignedSchemaCompatibleForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_UnsignedSchemaCompatibleForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'u64_fixed_field2':
            context.writeField(field, value.u64FixedField2);
            break;
          case 'u32_fixed_field2':
            context.writeField(field, value.u32FixedField2);
            break;
          case 'u16_field2':
            context.writeField(field, value.u16Field2);
            break;
          case 'u8_field2':
            context.writeField(field, value.u8Field2);
            break;
          case 'u64_tagged_field2':
            context.writeField(field, value.u64TaggedField2);
            break;
          case 'u64_var_field2':
            context.writeField(field, value.u64VarField2);
            break;
          case 'u32_var_field2':
            context.writeField(field, value.u32VarField2);
            break;
          case 'u64_fixed_field1':
            context.writeField(field, value.u64FixedField1);
            break;
          case 'u32_fixed_field1':
            context.writeField(field, value.u32FixedField1);
            break;
          case 'u16_field1':
            context.writeField(field, value.u16Field1);
            break;
          case 'u8_field1':
            context.writeField(field, value.u8Field1);
            break;
          case 'u64_tagged_field1':
            context.writeField(field, value.u64TaggedField1);
            break;
          case 'u64_var_field1':
            context.writeField(field, value.u64VarField1);
            break;
          case 'u32_var_field1':
            context.writeField(field, value.u32VarField1);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[0], value.u64FixedField2);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[1], value.u32FixedField2);
    context.writeField(_UnsignedSchemaCompatibleForyFields[2], value.u16Field2);
    context.writeField(_UnsignedSchemaCompatibleForyFields[3], value.u8Field2);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[4], value.u64TaggedField2);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[5], value.u64VarField2);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[6], value.u32VarField2);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[7], value.u64FixedField1);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[8], value.u32FixedField1);
    context.writeField(_UnsignedSchemaCompatibleForyFields[9], value.u16Field1);
    context.writeField(_UnsignedSchemaCompatibleForyFields[10], value.u8Field1);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[11], value.u64TaggedField1);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[12], value.u64VarField1);
    context.writeField(
        _UnsignedSchemaCompatibleForyFields[13], value.u32VarField1);
  }

  @override
  UnsignedSchemaCompatible read(ReadContext context) {
    final value = UnsignedSchemaCompatible();
    context.reference(value);
    value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[0], value.u64FixedField2),
        value.u64FixedField2);
    value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[1], value.u32FixedField2),
        value.u32FixedField2);
    value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[2], value.u16Field2),
        value.u16Field2);
    value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[3], value.u8Field2),
        value.u8Field2);
    value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[4], value.u64TaggedField2),
        value.u64TaggedField2);
    value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[5], value.u64VarField2),
        value.u64VarField2);
    value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[6], value.u32VarField2),
        value.u32VarField2);
    value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[7], value.u64FixedField1),
        value.u64FixedField1);
    value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[8], value.u32FixedField1),
        value.u32FixedField1);
    value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[9], value.u16Field1),
        value.u16Field1);
    value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[10], value.u8Field1),
        value.u8Field1);
    value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[11], value.u64TaggedField1),
        value.u64TaggedField1);
    value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[12], value.u64VarField1),
        value.u64VarField1);
    value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
        context.readField<Object?>(
            _UnsignedSchemaCompatibleForyFields[13], value.u32VarField1),
        value.u32VarField1);
    return value;
  }
}

const _UnsignedSchemaCompatibleForySerializer
    _unsignedSchemaCompatibleForySerializer =
    _UnsignedSchemaCompatibleForySerializer();

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

Serializer _serializerForGeneratedType(Type type) {
  if (type == Color) return _colorForySerializer;
  if (type == TestEnum) return _testEnumForySerializer;
  if (type == TwoEnumFieldStructEvolution)
    return _twoEnumFieldStructEvolutionForySerializer;
  if (type == Item) return _itemForySerializer;
  if (type == SimpleStruct) return _simpleStructForySerializer;
  if (type == EvolvingOverrideStruct)
    return _evolvingOverrideStructForySerializer;
  if (type == FixedOverrideStruct) return _fixedOverrideStructForySerializer;
  if (type == Item1) return _item1ForySerializer;
  if (type == StructWithUnion2) return _structWithUnion2ForySerializer;
  if (type == StructWithList) return _structWithListForySerializer;
  if (type == StructWithMap) return _structWithMapForySerializer;
  if (type == MyStruct) return _myStructForySerializer;
  if (type == MyWrapper) return _myWrapperForySerializer;
  if (type == EmptyWrapper) return _emptyWrapperForySerializer;
  if (type == VersionCheckStruct) return _versionCheckStructForySerializer;
  if (type == Dog) return _dogForySerializer;
  if (type == Cat) return _catForySerializer;
  if (type == AnimalListHolder) return _animalListHolderForySerializer;
  if (type == AnimalMapHolder) return _animalMapHolderForySerializer;
  if (type == EmptyStruct) return _emptyStructForySerializer;
  if (type == OneStringFieldStruct) return _oneStringFieldStructForySerializer;
  if (type == TwoStringFieldStruct) return _twoStringFieldStructForySerializer;
  if (type == OneEnumFieldStruct) return _oneEnumFieldStructForySerializer;
  if (type == TwoEnumFieldStruct) return _twoEnumFieldStructForySerializer;
  if (type == NullableComprehensiveSchemaConsistent)
    return _nullableComprehensiveSchemaConsistentForySerializer;
  if (type == NullableComprehensiveCompatible)
    return _nullableComprehensiveCompatibleForySerializer;
  if (type == RefInnerSchemaConsistent)
    return _refInnerSchemaConsistentForySerializer;
  if (type == RefOuterSchemaConsistent)
    return _refOuterSchemaConsistentForySerializer;
  if (type == RefInnerCompatible) return _refInnerCompatibleForySerializer;
  if (type == RefOuterCompatible) return _refOuterCompatibleForySerializer;
  if (type == RefOverrideElement) return _refOverrideElementForySerializer;
  if (type == CircularRefStruct) return _circularRefStructForySerializer;
  if (type == UnsignedSchemaConsistent)
    return _unsignedSchemaConsistentForySerializer;
  if (type == UnsignedSchemaConsistentSimple)
    return _unsignedSchemaConsistentSimpleForySerializer;
  if (type == UnsignedSchemaCompatible)
    return _unsignedSchemaCompatibleForySerializer;
  throw ArgumentError.value(
      type, 'type', 'No generated serializer for this library.');
}

void _registerXlangTestModelsForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  fory.register(type, _serializerForGeneratedType(type),
      id: id, namespace: namespace, typeName: typeName);
}

void _registerXlangTestModelsForyTypes(Fory fory) {
  fory.register(Color, _colorForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Color');
  fory.register(TestEnum, _testEnumForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'TestEnum');
  fory.register(
      TwoEnumFieldStructEvolution, _twoEnumFieldStructEvolutionForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStructEvolution');
  fory.register(Item, _itemForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item');
  fory.register(SimpleStruct, _simpleStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'SimpleStruct');
  fory.register(EvolvingOverrideStruct, _evolvingOverrideStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EvolvingOverrideStruct');
  fory.register(FixedOverrideStruct, _fixedOverrideStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'FixedOverrideStruct');
  fory.register(Item1, _item1ForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item1');
  fory.register(StructWithUnion2, _structWithUnion2ForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithUnion2');
  fory.register(StructWithList, _structWithListForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithList');
  fory.register(StructWithMap, _structWithMapForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithMap');
  fory.register(MyStruct, _myStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyStruct');
  fory.register(MyWrapper, _myWrapperForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyWrapper');
  fory.register(EmptyWrapper, _emptyWrapperForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EmptyWrapper');
  fory.register(VersionCheckStruct, _versionCheckStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'VersionCheckStruct');
  fory.register(Dog, _dogForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Dog');
  fory.register(Cat, _catForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Cat');
  fory.register(AnimalListHolder, _animalListHolderForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalListHolder');
  fory.register(AnimalMapHolder, _animalMapHolderForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalMapHolder');
  fory.register(EmptyStruct, _emptyStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'EmptyStruct');
  fory.register(OneStringFieldStruct, _oneStringFieldStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneStringFieldStruct');
  fory.register(TwoStringFieldStruct, _twoStringFieldStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoStringFieldStruct');
  fory.register(OneEnumFieldStruct, _oneEnumFieldStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneEnumFieldStruct');
  fory.register(TwoEnumFieldStruct, _twoEnumFieldStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStruct');
  fory.register(NullableComprehensiveSchemaConsistent,
      _nullableComprehensiveSchemaConsistentForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveSchemaConsistent');
  fory.register(NullableComprehensiveCompatible,
      _nullableComprehensiveCompatibleForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveCompatible');
  fory.register(
      RefInnerSchemaConsistent, _refInnerSchemaConsistentForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerSchemaConsistent');
  fory.register(
      RefOuterSchemaConsistent, _refOuterSchemaConsistentForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterSchemaConsistent');
  fory.register(RefInnerCompatible, _refInnerCompatibleForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerCompatible');
  fory.register(RefOuterCompatible, _refOuterCompatibleForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterCompatible');
  fory.register(RefOverrideElement, _refOverrideElementForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOverrideElement');
  fory.register(CircularRefStruct, _circularRefStructForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'CircularRefStruct');
  fory.register(
      UnsignedSchemaConsistent, _unsignedSchemaConsistentForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistent');
  fory.register(UnsignedSchemaConsistentSimple,
      _unsignedSchemaConsistentSimpleForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistentSimple');
  fory.register(
      UnsignedSchemaCompatible, _unsignedSchemaCompatibleForySerializer,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaCompatible');
}
