// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'xlang_test_models.dart';

// **************************************************************************
// ForyGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND

final class _ColorForySerializer extends Serializer<Color> {
  const _ColorForySerializer();
  @override
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

final class _TestEnumForySerializer extends Serializer<TestEnum> {
  const _TestEnumForySerializer();
  @override
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

const List<Map<String, Object?>> _twoEnumFieldStructEvolutionForyFields =
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

final class _TwoEnumFieldStructEvolutionForySerializer
    extends Serializer<TwoEnumFieldStructEvolution> {
  const _TwoEnumFieldStructEvolutionForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _twoEnumFieldStructEvolutionForyFields;
  @override
  void write(WriteContext context, TwoEnumFieldStructEvolution value) {
    final compatibleFields =
        context.compatibleFieldOrder(_twoEnumFieldStructEvolutionForyFields);
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
    context.writeField(_twoEnumFieldStructEvolutionForyFields[0], value.f1);
    context.writeField(_twoEnumFieldStructEvolutionForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStructEvolution read(ReadContext context) {
    final value = TwoEnumFieldStructEvolution();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructEvolutionF1(
        context.readField<Object?>(
            _twoEnumFieldStructEvolutionForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructEvolutionF2(
        context.readField<Object?>(
            _twoEnumFieldStructEvolutionForyFields[1], value.f2),
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

const List<Map<String, Object?>> _itemForyFields = <Map<String, Object?>>[
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

final class _ItemForySerializer extends Serializer<Item> {
  const _ItemForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _itemForyFields;
  @override
  void write(WriteContext context, Item value) {
    final compatibleFields = context.compatibleFieldOrder(_itemForyFields);
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
    context.writeField(_itemForyFields[0], value.name);
  }

  @override
  Item read(ReadContext context) {
    final value = Item();
    context.reference(value);
    value.name = _readItemName(
        context.readField<Object?>(_itemForyFields[0], value.name), value.name);
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

const List<Map<String, Object?>> _simpleStructForyFields =
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

final class _SimpleStructForySerializer extends Serializer<SimpleStruct> {
  const _SimpleStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _simpleStructForyFields;
  @override
  void write(WriteContext context, SimpleStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_simpleStructForyFields);
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
    context.writeField(_simpleStructForyFields[0], value.f2);
    context.writeField(_simpleStructForyFields[1], value.f7);
    context.writeField(_simpleStructForyFields[2], value.f8);
    context.writeField(_simpleStructForyFields[3], value.last);
    context.writeField(_simpleStructForyFields[4], value.f4);
    context.writeField(_simpleStructForyFields[5], value.f6);
    context.writeField(_simpleStructForyFields[6], value.f1);
    context.writeField(_simpleStructForyFields[7], value.f3);
    context.writeField(_simpleStructForyFields[8], value.f5);
  }

  @override
  SimpleStruct read(ReadContext context) {
    final value = SimpleStruct();
    context.reference(value);
    value.f2 = _readSimpleStructF2(
        context.readField<Object?>(_simpleStructForyFields[0], value.f2),
        value.f2);
    value.f7 = _readSimpleStructF7(
        context.readField<Object?>(_simpleStructForyFields[1], value.f7),
        value.f7);
    value.f8 = _readSimpleStructF8(
        context.readField<Object?>(_simpleStructForyFields[2], value.f8),
        value.f8);
    value.last = _readSimpleStructLast(
        context.readField<Object?>(_simpleStructForyFields[3], value.last),
        value.last);
    value.f4 = _readSimpleStructF4(
        context.readField<Object?>(_simpleStructForyFields[4], value.f4),
        value.f4);
    value.f6 = _readSimpleStructF6(
        context.readField<Object?>(_simpleStructForyFields[5], value.f6),
        value.f6);
    value.f1 = _readSimpleStructF1(
        context.readField<Object?>(_simpleStructForyFields[6], value.f1),
        value.f1);
    value.f3 = _readSimpleStructF3(
        context.readField<Object?>(_simpleStructForyFields[7], value.f3),
        value.f3);
    value.f5 = _readSimpleStructF5(
        context.readField<Object?>(_simpleStructForyFields[8], value.f5),
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

const List<Map<String, Object?>> _evolvingOverrideStructForyFields =
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

final class _EvolvingOverrideStructForySerializer
    extends Serializer<EvolvingOverrideStruct> {
  const _EvolvingOverrideStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _evolvingOverrideStructForyFields;
  @override
  void write(WriteContext context, EvolvingOverrideStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_evolvingOverrideStructForyFields);
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
    context.writeField(_evolvingOverrideStructForyFields[0], value.f1);
  }

  @override
  EvolvingOverrideStruct read(ReadContext context) {
    final value = EvolvingOverrideStruct();
    context.reference(value);
    value.f1 = _readEvolvingOverrideStructF1(
        context.readField<Object?>(
            _evolvingOverrideStructForyFields[0], value.f1),
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

const List<Map<String, Object?>> _fixedOverrideStructForyFields =
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

final class _FixedOverrideStructForySerializer
    extends Serializer<FixedOverrideStruct> {
  const _FixedOverrideStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => false;
  @override
  List<Map<String, Object?>> get fields => _fixedOverrideStructForyFields;
  @override
  void write(WriteContext context, FixedOverrideStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_fixedOverrideStructForyFields);
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
    context.writeField(_fixedOverrideStructForyFields[0], value.f1);
  }

  @override
  FixedOverrideStruct read(ReadContext context) {
    final value = FixedOverrideStruct();
    context.reference(value);
    value.f1 = _readFixedOverrideStructF1(
        context.readField<Object?>(_fixedOverrideStructForyFields[0], value.f1),
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

const List<Map<String, Object?>> _item1ForyFields = <Map<String, Object?>>[
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

final class _Item1ForySerializer extends Serializer<Item1> {
  const _Item1ForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _item1ForyFields;
  @override
  void write(WriteContext context, Item1 value) {
    final compatibleFields = context.compatibleFieldOrder(_item1ForyFields);
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
    context.writeField(_item1ForyFields[0], value.f1);
    context.writeField(_item1ForyFields[1], value.f2);
    context.writeField(_item1ForyFields[2], value.f3);
    context.writeField(_item1ForyFields[3], value.f4);
    context.writeField(_item1ForyFields[4], value.f5);
    context.writeField(_item1ForyFields[5], value.f6);
  }

  @override
  Item1 read(ReadContext context) {
    final value = Item1();
    context.reference(value);
    value.f1 = _readItem1F1(
        context.readField<Object?>(_item1ForyFields[0], value.f1), value.f1);
    value.f2 = _readItem1F2(
        context.readField<Object?>(_item1ForyFields[1], value.f2), value.f2);
    value.f3 = _readItem1F3(
        context.readField<Object?>(_item1ForyFields[2], value.f3), value.f3);
    value.f4 = _readItem1F4(
        context.readField<Object?>(_item1ForyFields[3], value.f4), value.f4);
    value.f5 = _readItem1F5(
        context.readField<Object?>(_item1ForyFields[4], value.f5), value.f5);
    value.f6 = _readItem1F6(
        context.readField<Object?>(_item1ForyFields[5], value.f6), value.f6);
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

const List<Map<String, Object?>> _structWithUnion2ForyFields =
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

final class _StructWithUnion2ForySerializer
    extends Serializer<StructWithUnion2> {
  const _StructWithUnion2ForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _structWithUnion2ForyFields;
  @override
  void write(WriteContext context, StructWithUnion2 value) {
    final compatibleFields =
        context.compatibleFieldOrder(_structWithUnion2ForyFields);
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
    context.writeField(_structWithUnion2ForyFields[0], value.union);
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final value = StructWithUnion2();
    context.reference(value);
    value.union = _readStructWithUnion2Union(
        context.readField<Object?>(_structWithUnion2ForyFields[0], value.union),
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

const List<Map<String, Object?>> _structWithListForyFields =
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

final class _StructWithListForySerializer extends Serializer<StructWithList> {
  const _StructWithListForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _structWithListForyFields;
  @override
  void write(WriteContext context, StructWithList value) {
    final compatibleFields =
        context.compatibleFieldOrder(_structWithListForyFields);
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
    context.writeField(_structWithListForyFields[0], value.items);
  }

  @override
  StructWithList read(ReadContext context) {
    final value = StructWithList();
    context.reference(value);
    value.items = _readStructWithListItems(
        context.readField<Object?>(_structWithListForyFields[0], value.items),
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

const List<Map<String, Object?>> _structWithMapForyFields =
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

final class _StructWithMapForySerializer extends Serializer<StructWithMap> {
  const _StructWithMapForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _structWithMapForyFields;
  @override
  void write(WriteContext context, StructWithMap value) {
    final compatibleFields =
        context.compatibleFieldOrder(_structWithMapForyFields);
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
    context.writeField(_structWithMapForyFields[0], value.data);
  }

  @override
  StructWithMap read(ReadContext context) {
    final value = StructWithMap();
    context.reference(value);
    value.data = _readStructWithMapData(
        context.readField<Object?>(_structWithMapForyFields[0], value.data),
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

const List<Map<String, Object?>> _myStructForyFields = <Map<String, Object?>>[
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

final class _MyStructForySerializer extends Serializer<MyStruct> {
  const _MyStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _myStructForyFields;
  @override
  void write(WriteContext context, MyStruct value) {
    final compatibleFields = context.compatibleFieldOrder(_myStructForyFields);
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
    context.writeField(_myStructForyFields[0], value.id);
  }

  @override
  MyStruct read(ReadContext context) {
    final value = MyStruct();
    context.reference(value);
    value.id = _readMyStructId(
        context.readField<Object?>(_myStructForyFields[0], value.id), value.id);
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

const List<Map<String, Object?>> _myWrapperForyFields = <Map<String, Object?>>[
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

final class _MyWrapperForySerializer extends Serializer<MyWrapper> {
  const _MyWrapperForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _myWrapperForyFields;
  @override
  void write(WriteContext context, MyWrapper value) {
    final compatibleFields = context.compatibleFieldOrder(_myWrapperForyFields);
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
    context.writeField(_myWrapperForyFields[0], value.color);
    context.writeField(_myWrapperForyFields[1], value.myExt);
    context.writeField(_myWrapperForyFields[2], value.myStruct);
  }

  @override
  MyWrapper read(ReadContext context) {
    final value = MyWrapper();
    context.reference(value);
    value.color = _readMyWrapperColor(
        context.readField<Object?>(_myWrapperForyFields[0], value.color),
        value.color);
    value.myExt = _readMyWrapperMyExt(
        context.readField<Object?>(_myWrapperForyFields[1], value.myExt),
        value.myExt);
    value.myStruct = _readMyWrapperMyStruct(
        context.readField<Object?>(_myWrapperForyFields[2], value.myStruct),
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

const List<Map<String, Object?>> _emptyWrapperForyFields =
    <Map<String, Object?>>[];

final class _EmptyWrapperForySerializer extends Serializer<EmptyWrapper> {
  const _EmptyWrapperForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _emptyWrapperForyFields;
  @override
  void write(WriteContext context, EmptyWrapper value) {
    final compatibleFields =
        context.compatibleFieldOrder(_emptyWrapperForyFields);
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

const List<Map<String, Object?>> _versionCheckStructForyFields =
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

final class _VersionCheckStructForySerializer
    extends Serializer<VersionCheckStruct> {
  const _VersionCheckStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _versionCheckStructForyFields;
  @override
  void write(WriteContext context, VersionCheckStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_versionCheckStructForyFields);
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
    context.writeField(_versionCheckStructForyFields[0], value.f3);
    context.writeField(_versionCheckStructForyFields[1], value.f1);
    context.writeField(_versionCheckStructForyFields[2], value.f2);
  }

  @override
  VersionCheckStruct read(ReadContext context) {
    final value = VersionCheckStruct();
    context.reference(value);
    value.f3 = _readVersionCheckStructF3(
        context.readField<Object?>(_versionCheckStructForyFields[0], value.f3),
        value.f3);
    value.f1 = _readVersionCheckStructF1(
        context.readField<Object?>(_versionCheckStructForyFields[1], value.f1),
        value.f1);
    value.f2 = _readVersionCheckStructF2(
        context.readField<Object?>(_versionCheckStructForyFields[2], value.f2),
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

const List<Map<String, Object?>> _dogForyFields = <Map<String, Object?>>[
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

final class _DogForySerializer extends Serializer<Dog> {
  const _DogForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _dogForyFields;
  @override
  void write(WriteContext context, Dog value) {
    final compatibleFields = context.compatibleFieldOrder(_dogForyFields);
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
    context.writeField(_dogForyFields[0], value.age);
    context.writeField(_dogForyFields[1], value.name);
  }

  @override
  Dog read(ReadContext context) {
    final value = Dog();
    context.reference(value);
    value.age = _readDogAge(
        context.readField<Object?>(_dogForyFields[0], value.age), value.age);
    value.name = _readDogName(
        context.readField<Object?>(_dogForyFields[1], value.name), value.name);
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

const List<Map<String, Object?>> _catForyFields = <Map<String, Object?>>[
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

final class _CatForySerializer extends Serializer<Cat> {
  const _CatForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _catForyFields;
  @override
  void write(WriteContext context, Cat value) {
    final compatibleFields = context.compatibleFieldOrder(_catForyFields);
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
    context.writeField(_catForyFields[0], value.age);
    context.writeField(_catForyFields[1], value.lives);
  }

  @override
  Cat read(ReadContext context) {
    final value = Cat();
    context.reference(value);
    value.age = _readCatAge(
        context.readField<Object?>(_catForyFields[0], value.age), value.age);
    value.lives = _readCatLives(
        context.readField<Object?>(_catForyFields[1], value.lives),
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

const List<Map<String, Object?>> _animalListHolderForyFields =
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

final class _AnimalListHolderForySerializer
    extends Serializer<AnimalListHolder> {
  const _AnimalListHolderForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _animalListHolderForyFields;
  @override
  void write(WriteContext context, AnimalListHolder value) {
    final compatibleFields =
        context.compatibleFieldOrder(_animalListHolderForyFields);
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
    context.writeField(_animalListHolderForyFields[0], value.animals);
  }

  @override
  AnimalListHolder read(ReadContext context) {
    final value = AnimalListHolder();
    context.reference(value);
    value.animals = _readAnimalListHolderAnimals(
        context.readField<Object?>(
            _animalListHolderForyFields[0], value.animals),
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

const List<Map<String, Object?>> _animalMapHolderForyFields =
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

final class _AnimalMapHolderForySerializer extends Serializer<AnimalMapHolder> {
  const _AnimalMapHolderForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _animalMapHolderForyFields;
  @override
  void write(WriteContext context, AnimalMapHolder value) {
    final compatibleFields =
        context.compatibleFieldOrder(_animalMapHolderForyFields);
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
    context.writeField(_animalMapHolderForyFields[0], value.animalMap);
  }

  @override
  AnimalMapHolder read(ReadContext context) {
    final value = AnimalMapHolder();
    context.reference(value);
    value.animalMap = _readAnimalMapHolderAnimalMap(
        context.readField<Object?>(
            _animalMapHolderForyFields[0], value.animalMap),
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

const List<Map<String, Object?>> _emptyStructForyFields =
    <Map<String, Object?>>[];

final class _EmptyStructForySerializer extends Serializer<EmptyStruct> {
  const _EmptyStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _emptyStructForyFields;
  @override
  void write(WriteContext context, EmptyStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_emptyStructForyFields);
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

const List<Map<String, Object?>> _oneStringFieldStructForyFields =
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

final class _OneStringFieldStructForySerializer
    extends Serializer<OneStringFieldStruct> {
  const _OneStringFieldStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _oneStringFieldStructForyFields;
  @override
  void write(WriteContext context, OneStringFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_oneStringFieldStructForyFields);
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
    context.writeField(_oneStringFieldStructForyFields[0], value.f1);
  }

  @override
  OneStringFieldStruct read(ReadContext context) {
    final value = OneStringFieldStruct();
    context.reference(value);
    value.f1 = _readOneStringFieldStructF1(
        context.readField<Object?>(
            _oneStringFieldStructForyFields[0], value.f1),
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

const List<Map<String, Object?>> _twoStringFieldStructForyFields =
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

final class _TwoStringFieldStructForySerializer
    extends Serializer<TwoStringFieldStruct> {
  const _TwoStringFieldStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _twoStringFieldStructForyFields;
  @override
  void write(WriteContext context, TwoStringFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_twoStringFieldStructForyFields);
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
    context.writeField(_twoStringFieldStructForyFields[0], value.f1);
    context.writeField(_twoStringFieldStructForyFields[1], value.f2);
  }

  @override
  TwoStringFieldStruct read(ReadContext context) {
    final value = TwoStringFieldStruct();
    context.reference(value);
    value.f1 = _readTwoStringFieldStructF1(
        context.readField<Object?>(
            _twoStringFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoStringFieldStructF2(
        context.readField<Object?>(
            _twoStringFieldStructForyFields[1], value.f2),
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

const List<Map<String, Object?>> _oneEnumFieldStructForyFields =
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

final class _OneEnumFieldStructForySerializer
    extends Serializer<OneEnumFieldStruct> {
  const _OneEnumFieldStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _oneEnumFieldStructForyFields;
  @override
  void write(WriteContext context, OneEnumFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_oneEnumFieldStructForyFields);
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
    context.writeField(_oneEnumFieldStructForyFields[0], value.f1);
  }

  @override
  OneEnumFieldStruct read(ReadContext context) {
    final value = OneEnumFieldStruct();
    context.reference(value);
    value.f1 = _readOneEnumFieldStructF1(
        context.readField<Object?>(_oneEnumFieldStructForyFields[0], value.f1),
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

const List<Map<String, Object?>> _twoEnumFieldStructForyFields =
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

final class _TwoEnumFieldStructForySerializer
    extends Serializer<TwoEnumFieldStruct> {
  const _TwoEnumFieldStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _twoEnumFieldStructForyFields;
  @override
  void write(WriteContext context, TwoEnumFieldStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_twoEnumFieldStructForyFields);
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
    context.writeField(_twoEnumFieldStructForyFields[0], value.f1);
    context.writeField(_twoEnumFieldStructForyFields[1], value.f2);
  }

  @override
  TwoEnumFieldStruct read(ReadContext context) {
    final value = TwoEnumFieldStruct();
    context.reference(value);
    value.f1 = _readTwoEnumFieldStructF1(
        context.readField<Object?>(_twoEnumFieldStructForyFields[0], value.f1),
        value.f1);
    value.f2 = _readTwoEnumFieldStructF2(
        context.readField<Object?>(_twoEnumFieldStructForyFields[1], value.f2),
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
    _nullableComprehensiveSchemaConsistentForyFields = <Map<String, Object?>>[
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

final class _NullableComprehensiveSchemaConsistentForySerializer
    extends Serializer<NullableComprehensiveSchemaConsistent> {
  const _NullableComprehensiveSchemaConsistentForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _nullableComprehensiveSchemaConsistentForyFields;
  @override
  void write(
      WriteContext context, NullableComprehensiveSchemaConsistent value) {
    final compatibleFields = context
        .compatibleFieldOrder(_nullableComprehensiveSchemaConsistentForyFields);
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
        _nullableComprehensiveSchemaConsistentForyFields[0], value.doubleField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[1], value.floatField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[2], value.shortField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[3], value.byteField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[4], value.boolField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[5], value.longField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[6], value.intField);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[7],
        value.nullableDouble);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[8],
        value.nullableFloat);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[9],
        value.nullableBool);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[10],
        value.nullableLong);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[11],
        value.nullableInt);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[12],
        value.nullableString);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[13],
        value.stringField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[14], value.listField);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[15],
        value.nullableList);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[16],
        value.nullableSet);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[17], value.setField);
    context.writeField(
        _nullableComprehensiveSchemaConsistentForyFields[18], value.mapField);
    context.writeField(_nullableComprehensiveSchemaConsistentForyFields[19],
        value.nullableMap);
  }

  @override
  NullableComprehensiveSchemaConsistent read(ReadContext context) {
    final value = NullableComprehensiveSchemaConsistent();
    context.reference(value);
    value.doubleField = _readNullableComprehensiveSchemaConsistentDoubleField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[0],
            value.doubleField),
        value.doubleField);
    value.floatField = _readNullableComprehensiveSchemaConsistentFloatField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[1],
            value.floatField),
        value.floatField);
    value.shortField = _readNullableComprehensiveSchemaConsistentShortField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[2],
            value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveSchemaConsistentByteField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[3],
            value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveSchemaConsistentBoolField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[4],
            value.boolField),
        value.boolField);
    value.longField = _readNullableComprehensiveSchemaConsistentLongField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[5],
            value.longField),
        value.longField);
    value.intField = _readNullableComprehensiveSchemaConsistentIntField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[6],
            value.intField),
        value.intField);
    value.nullableDouble =
        _readNullableComprehensiveSchemaConsistentNullableDouble(
            context.readField<Object?>(
                _nullableComprehensiveSchemaConsistentForyFields[7],
                value.nullableDouble),
            value.nullableDouble);
    value.nullableFloat =
        _readNullableComprehensiveSchemaConsistentNullableFloat(
            context.readField<Object?>(
                _nullableComprehensiveSchemaConsistentForyFields[8],
                value.nullableFloat),
            value.nullableFloat);
    value.nullableBool = _readNullableComprehensiveSchemaConsistentNullableBool(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[9],
            value.nullableBool),
        value.nullableBool);
    value.nullableLong = _readNullableComprehensiveSchemaConsistentNullableLong(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[10],
            value.nullableLong),
        value.nullableLong);
    value.nullableInt = _readNullableComprehensiveSchemaConsistentNullableInt(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[11],
            value.nullableInt),
        value.nullableInt);
    value.nullableString =
        _readNullableComprehensiveSchemaConsistentNullableString(
            context.readField<Object?>(
                _nullableComprehensiveSchemaConsistentForyFields[12],
                value.nullableString),
            value.nullableString);
    value.stringField = _readNullableComprehensiveSchemaConsistentStringField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[13],
            value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveSchemaConsistentListField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[14],
            value.listField),
        value.listField);
    value.nullableList = _readNullableComprehensiveSchemaConsistentNullableList(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[15],
            value.nullableList),
        value.nullableList);
    value.nullableSet = _readNullableComprehensiveSchemaConsistentNullableSet(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[16],
            value.nullableSet),
        value.nullableSet);
    value.setField = _readNullableComprehensiveSchemaConsistentSetField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[17],
            value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveSchemaConsistentMapField(
        context.readField<Object?>(
            _nullableComprehensiveSchemaConsistentForyFields[18],
            value.mapField),
        value.mapField);
    value.nullableMap = _readNullableComprehensiveSchemaConsistentNullableMap(
        context.readField<Object?>(
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

const List<Map<String, Object?>> _nullableComprehensiveCompatibleForyFields =
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

final class _NullableComprehensiveCompatibleForySerializer
    extends Serializer<NullableComprehensiveCompatible> {
  const _NullableComprehensiveCompatibleForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _nullableComprehensiveCompatibleForyFields;
  @override
  void write(WriteContext context, NullableComprehensiveCompatible value) {
    final compatibleFields = context
        .compatibleFieldOrder(_nullableComprehensiveCompatibleForyFields);
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
        _nullableComprehensiveCompatibleForyFields[0], value.boxedDouble);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[1], value.doubleField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[2], value.nullableDouble1);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[3], value.boxedFloat);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[4], value.floatField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[5], value.nullableFloat1);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[6], value.shortField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[7], value.byteField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[8], value.boolField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[9], value.boxedBool);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[10], value.nullableBool1);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[11], value.boxedLong);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[12], value.longField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[13], value.nullableLong1);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[14], value.boxedInt);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[15], value.intField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[16], value.nullableInt1);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[17], value.nullableString2);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[18], value.stringField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[19], value.listField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[20], value.nullableList2);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[21], value.nullableSet2);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[22], value.setField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[23], value.mapField);
    context.writeField(
        _nullableComprehensiveCompatibleForyFields[24], value.nullableMap2);
  }

  @override
  NullableComprehensiveCompatible read(ReadContext context) {
    final value = NullableComprehensiveCompatible();
    context.reference(value);
    value.boxedDouble = _readNullableComprehensiveCompatibleBoxedDouble(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[0], value.boxedDouble),
        value.boxedDouble);
    value.doubleField = _readNullableComprehensiveCompatibleDoubleField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[1], value.doubleField),
        value.doubleField);
    value.nullableDouble1 = _readNullableComprehensiveCompatibleNullableDouble1(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[2],
            value.nullableDouble1),
        value.nullableDouble1);
    value.boxedFloat = _readNullableComprehensiveCompatibleBoxedFloat(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[3], value.boxedFloat),
        value.boxedFloat);
    value.floatField = _readNullableComprehensiveCompatibleFloatField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[4], value.floatField),
        value.floatField);
    value.nullableFloat1 = _readNullableComprehensiveCompatibleNullableFloat1(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[5],
            value.nullableFloat1),
        value.nullableFloat1);
    value.shortField = _readNullableComprehensiveCompatibleShortField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[6], value.shortField),
        value.shortField);
    value.byteField = _readNullableComprehensiveCompatibleByteField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[7], value.byteField),
        value.byteField);
    value.boolField = _readNullableComprehensiveCompatibleBoolField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[8], value.boolField),
        value.boolField);
    value.boxedBool = _readNullableComprehensiveCompatibleBoxedBool(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[9], value.boxedBool),
        value.boxedBool);
    value.nullableBool1 = _readNullableComprehensiveCompatibleNullableBool1(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[10],
            value.nullableBool1),
        value.nullableBool1);
    value.boxedLong = _readNullableComprehensiveCompatibleBoxedLong(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[11], value.boxedLong),
        value.boxedLong);
    value.longField = _readNullableComprehensiveCompatibleLongField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[12], value.longField),
        value.longField);
    value.nullableLong1 = _readNullableComprehensiveCompatibleNullableLong1(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[13],
            value.nullableLong1),
        value.nullableLong1);
    value.boxedInt = _readNullableComprehensiveCompatibleBoxedInt(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[14], value.boxedInt),
        value.boxedInt);
    value.intField = _readNullableComprehensiveCompatibleIntField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[15], value.intField),
        value.intField);
    value.nullableInt1 = _readNullableComprehensiveCompatibleNullableInt1(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[16], value.nullableInt1),
        value.nullableInt1);
    value.nullableString2 = _readNullableComprehensiveCompatibleNullableString2(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[17],
            value.nullableString2),
        value.nullableString2);
    value.stringField = _readNullableComprehensiveCompatibleStringField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[18], value.stringField),
        value.stringField);
    value.listField = _readNullableComprehensiveCompatibleListField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[19], value.listField),
        value.listField);
    value.nullableList2 = _readNullableComprehensiveCompatibleNullableList2(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[20],
            value.nullableList2),
        value.nullableList2);
    value.nullableSet2 = _readNullableComprehensiveCompatibleNullableSet2(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[21], value.nullableSet2),
        value.nullableSet2);
    value.setField = _readNullableComprehensiveCompatibleSetField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[22], value.setField),
        value.setField);
    value.mapField = _readNullableComprehensiveCompatibleMapField(
        context.readField<Object?>(
            _nullableComprehensiveCompatibleForyFields[23], value.mapField),
        value.mapField);
    value.nullableMap2 = _readNullableComprehensiveCompatibleNullableMap2(
        context.readField<Object?>(
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

const List<Map<String, Object?>> _refInnerSchemaConsistentForyFields =
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

final class _RefInnerSchemaConsistentForySerializer
    extends Serializer<RefInnerSchemaConsistent> {
  const _RefInnerSchemaConsistentForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refInnerSchemaConsistentForyFields;
  @override
  void write(WriteContext context, RefInnerSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_refInnerSchemaConsistentForyFields);
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
    context.writeField(_refInnerSchemaConsistentForyFields[0], value.id);
    context.writeField(_refInnerSchemaConsistentForyFields[1], value.name);
  }

  @override
  RefInnerSchemaConsistent read(ReadContext context) {
    final value = RefInnerSchemaConsistent();
    context.reference(value);
    value.id = _readRefInnerSchemaConsistentId(
        context.readField<Object?>(
            _refInnerSchemaConsistentForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerSchemaConsistentName(
        context.readField<Object?>(
            _refInnerSchemaConsistentForyFields[1], value.name),
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

const List<Map<String, Object?>> _refOuterSchemaConsistentForyFields =
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

final class _RefOuterSchemaConsistentForySerializer
    extends Serializer<RefOuterSchemaConsistent> {
  const _RefOuterSchemaConsistentForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refOuterSchemaConsistentForyFields;
  @override
  void write(WriteContext context, RefOuterSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_refOuterSchemaConsistentForyFields);
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
    context.writeField(_refOuterSchemaConsistentForyFields[0], value.inner1);
    context.writeField(_refOuterSchemaConsistentForyFields[1], value.inner2);
  }

  @override
  RefOuterSchemaConsistent read(ReadContext context) {
    final value = RefOuterSchemaConsistent();
    context.reference(value);
    value.inner1 = _readRefOuterSchemaConsistentInner1(
        context.readField<Object?>(
            _refOuterSchemaConsistentForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterSchemaConsistentInner2(
        context.readField<Object?>(
            _refOuterSchemaConsistentForyFields[1], value.inner2),
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

const List<Map<String, Object?>> _refInnerCompatibleForyFields =
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

final class _RefInnerCompatibleForySerializer
    extends Serializer<RefInnerCompatible> {
  const _RefInnerCompatibleForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refInnerCompatibleForyFields;
  @override
  void write(WriteContext context, RefInnerCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_refInnerCompatibleForyFields);
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
    context.writeField(_refInnerCompatibleForyFields[0], value.id);
    context.writeField(_refInnerCompatibleForyFields[1], value.name);
  }

  @override
  RefInnerCompatible read(ReadContext context) {
    final value = RefInnerCompatible();
    context.reference(value);
    value.id = _readRefInnerCompatibleId(
        context.readField<Object?>(_refInnerCompatibleForyFields[0], value.id),
        value.id);
    value.name = _readRefInnerCompatibleName(
        context.readField<Object?>(
            _refInnerCompatibleForyFields[1], value.name),
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

const List<Map<String, Object?>> _refOuterCompatibleForyFields =
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

final class _RefOuterCompatibleForySerializer
    extends Serializer<RefOuterCompatible> {
  const _RefOuterCompatibleForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refOuterCompatibleForyFields;
  @override
  void write(WriteContext context, RefOuterCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_refOuterCompatibleForyFields);
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
    context.writeField(_refOuterCompatibleForyFields[0], value.inner1);
    context.writeField(_refOuterCompatibleForyFields[1], value.inner2);
  }

  @override
  RefOuterCompatible read(ReadContext context) {
    final value = RefOuterCompatible();
    context.reference(value);
    value.inner1 = _readRefOuterCompatibleInner1(
        context.readField<Object?>(
            _refOuterCompatibleForyFields[0], value.inner1),
        value.inner1);
    value.inner2 = _readRefOuterCompatibleInner2(
        context.readField<Object?>(
            _refOuterCompatibleForyFields[1], value.inner2),
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

const List<Map<String, Object?>> _refOverrideElementForyFields =
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

final class _RefOverrideElementForySerializer
    extends Serializer<RefOverrideElement> {
  const _RefOverrideElementForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refOverrideElementForyFields;
  @override
  void write(WriteContext context, RefOverrideElement value) {
    final compatibleFields =
        context.compatibleFieldOrder(_refOverrideElementForyFields);
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
    context.writeField(_refOverrideElementForyFields[0], value.id);
    context.writeField(_refOverrideElementForyFields[1], value.name);
  }

  @override
  RefOverrideElement read(ReadContext context) {
    final value = RefOverrideElement();
    context.reference(value);
    value.id = _readRefOverrideElementId(
        context.readField<Object?>(_refOverrideElementForyFields[0], value.id),
        value.id);
    value.name = _readRefOverrideElementName(
        context.readField<Object?>(
            _refOverrideElementForyFields[1], value.name),
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

const List<Map<String, Object?>> _circularRefStructForyFields =
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

final class _CircularRefStructForySerializer
    extends Serializer<CircularRefStruct> {
  const _CircularRefStructForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _circularRefStructForyFields;
  @override
  void write(WriteContext context, CircularRefStruct value) {
    final compatibleFields =
        context.compatibleFieldOrder(_circularRefStructForyFields);
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
    context.writeField(_circularRefStructForyFields[0], value.name);
    context.writeField(_circularRefStructForyFields[1], value.selfRef);
  }

  @override
  CircularRefStruct read(ReadContext context) {
    final value = CircularRefStruct();
    context.reference(value);
    value.name = _readCircularRefStructName(
        context.readField<Object?>(_circularRefStructForyFields[0], value.name),
        value.name);
    value.selfRef = _readCircularRefStructSelfRef(
        context.readField<Object?>(
            _circularRefStructForyFields[1], value.selfRef),
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

const List<Map<String, Object?>> _unsignedSchemaConsistentForyFields =
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

final class _UnsignedSchemaConsistentForySerializer
    extends Serializer<UnsignedSchemaConsistent> {
  const _UnsignedSchemaConsistentForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _unsignedSchemaConsistentForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaConsistent value) {
    final compatibleFields =
        context.compatibleFieldOrder(_unsignedSchemaConsistentForyFields);
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
        _unsignedSchemaConsistentForyFields[0], value.u64FixedField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[1], value.u32FixedField);
    context.writeField(_unsignedSchemaConsistentForyFields[2], value.u16Field);
    context.writeField(_unsignedSchemaConsistentForyFields[3], value.u8Field);
    context.writeField(
        _unsignedSchemaConsistentForyFields[4], value.u64TaggedField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[5], value.u64VarField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[6], value.u32VarField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[7], value.u64FixedNullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[8], value.u32FixedNullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[9], value.u16NullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[10], value.u8NullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[11], value.u64TaggedNullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[12], value.u64VarNullableField);
    context.writeField(
        _unsignedSchemaConsistentForyFields[13], value.u32VarNullableField);
  }

  @override
  UnsignedSchemaConsistent read(ReadContext context) {
    final value = UnsignedSchemaConsistent();
    context.reference(value);
    value.u64FixedField = _readUnsignedSchemaConsistentU64FixedField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[0], value.u64FixedField),
        value.u64FixedField);
    value.u32FixedField = _readUnsignedSchemaConsistentU32FixedField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[1], value.u32FixedField),
        value.u32FixedField);
    value.u16Field = _readUnsignedSchemaConsistentU16Field(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[2], value.u16Field),
        value.u16Field);
    value.u8Field = _readUnsignedSchemaConsistentU8Field(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[3], value.u8Field),
        value.u8Field);
    value.u64TaggedField = _readUnsignedSchemaConsistentU64TaggedField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[4], value.u64TaggedField),
        value.u64TaggedField);
    value.u64VarField = _readUnsignedSchemaConsistentU64VarField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[5], value.u64VarField),
        value.u64VarField);
    value.u32VarField = _readUnsignedSchemaConsistentU32VarField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[6], value.u32VarField),
        value.u32VarField);
    value.u64FixedNullableField =
        _readUnsignedSchemaConsistentU64FixedNullableField(
            context.readField<Object?>(_unsignedSchemaConsistentForyFields[7],
                value.u64FixedNullableField),
            value.u64FixedNullableField);
    value.u32FixedNullableField =
        _readUnsignedSchemaConsistentU32FixedNullableField(
            context.readField<Object?>(_unsignedSchemaConsistentForyFields[8],
                value.u32FixedNullableField),
            value.u32FixedNullableField);
    value.u16NullableField = _readUnsignedSchemaConsistentU16NullableField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[9], value.u16NullableField),
        value.u16NullableField);
    value.u8NullableField = _readUnsignedSchemaConsistentU8NullableField(
        context.readField<Object?>(
            _unsignedSchemaConsistentForyFields[10], value.u8NullableField),
        value.u8NullableField);
    value.u64TaggedNullableField =
        _readUnsignedSchemaConsistentU64TaggedNullableField(
            context.readField<Object?>(_unsignedSchemaConsistentForyFields[11],
                value.u64TaggedNullableField),
            value.u64TaggedNullableField);
    value.u64VarNullableField =
        _readUnsignedSchemaConsistentU64VarNullableField(
            context.readField<Object?>(_unsignedSchemaConsistentForyFields[12],
                value.u64VarNullableField),
            value.u64VarNullableField);
    value.u32VarNullableField =
        _readUnsignedSchemaConsistentU32VarNullableField(
            context.readField<Object?>(_unsignedSchemaConsistentForyFields[13],
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

const List<Map<String, Object?>> _unsignedSchemaConsistentSimpleForyFields =
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

final class _UnsignedSchemaConsistentSimpleForySerializer
    extends Serializer<UnsignedSchemaConsistentSimple> {
  const _UnsignedSchemaConsistentSimpleForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields =>
      _unsignedSchemaConsistentSimpleForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaConsistentSimple value) {
    final compatibleFields =
        context.compatibleFieldOrder(_unsignedSchemaConsistentSimpleForyFields);
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
        _unsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged);
    context.writeField(
        _unsignedSchemaConsistentSimpleForyFields[1], value.u64TaggedNullable);
  }

  @override
  UnsignedSchemaConsistentSimple read(ReadContext context) {
    final value = UnsignedSchemaConsistentSimple();
    context.reference(value);
    value.u64Tagged = _readUnsignedSchemaConsistentSimpleU64Tagged(
        context.readField<Object?>(
            _unsignedSchemaConsistentSimpleForyFields[0], value.u64Tagged),
        value.u64Tagged);
    value.u64TaggedNullable =
        _readUnsignedSchemaConsistentSimpleU64TaggedNullable(
            context.readField<Object?>(
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

const List<Map<String, Object?>> _unsignedSchemaCompatibleForyFields =
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

final class _UnsignedSchemaCompatibleForySerializer
    extends Serializer<UnsignedSchemaCompatible> {
  const _UnsignedSchemaCompatibleForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _unsignedSchemaCompatibleForyFields;
  @override
  void write(WriteContext context, UnsignedSchemaCompatible value) {
    final compatibleFields =
        context.compatibleFieldOrder(_unsignedSchemaCompatibleForyFields);
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
        _unsignedSchemaCompatibleForyFields[0], value.u64FixedField2);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[1], value.u32FixedField2);
    context.writeField(_unsignedSchemaCompatibleForyFields[2], value.u16Field2);
    context.writeField(_unsignedSchemaCompatibleForyFields[3], value.u8Field2);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[4], value.u64TaggedField2);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[5], value.u64VarField2);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[6], value.u32VarField2);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[7], value.u64FixedField1);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[8], value.u32FixedField1);
    context.writeField(_unsignedSchemaCompatibleForyFields[9], value.u16Field1);
    context.writeField(_unsignedSchemaCompatibleForyFields[10], value.u8Field1);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[11], value.u64TaggedField1);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[12], value.u64VarField1);
    context.writeField(
        _unsignedSchemaCompatibleForyFields[13], value.u32VarField1);
  }

  @override
  UnsignedSchemaCompatible read(ReadContext context) {
    final value = UnsignedSchemaCompatible();
    context.reference(value);
    value.u64FixedField2 = _readUnsignedSchemaCompatibleU64FixedField2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[0], value.u64FixedField2),
        value.u64FixedField2);
    value.u32FixedField2 = _readUnsignedSchemaCompatibleU32FixedField2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[1], value.u32FixedField2),
        value.u32FixedField2);
    value.u16Field2 = _readUnsignedSchemaCompatibleU16Field2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[2], value.u16Field2),
        value.u16Field2);
    value.u8Field2 = _readUnsignedSchemaCompatibleU8Field2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[3], value.u8Field2),
        value.u8Field2);
    value.u64TaggedField2 = _readUnsignedSchemaCompatibleU64TaggedField2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[4], value.u64TaggedField2),
        value.u64TaggedField2);
    value.u64VarField2 = _readUnsignedSchemaCompatibleU64VarField2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[5], value.u64VarField2),
        value.u64VarField2);
    value.u32VarField2 = _readUnsignedSchemaCompatibleU32VarField2(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[6], value.u32VarField2),
        value.u32VarField2);
    value.u64FixedField1 = _readUnsignedSchemaCompatibleU64FixedField1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[7], value.u64FixedField1),
        value.u64FixedField1);
    value.u32FixedField1 = _readUnsignedSchemaCompatibleU32FixedField1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[8], value.u32FixedField1),
        value.u32FixedField1);
    value.u16Field1 = _readUnsignedSchemaCompatibleU16Field1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[9], value.u16Field1),
        value.u16Field1);
    value.u8Field1 = _readUnsignedSchemaCompatibleU8Field1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[10], value.u8Field1),
        value.u8Field1);
    value.u64TaggedField1 = _readUnsignedSchemaCompatibleU64TaggedField1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[11], value.u64TaggedField1),
        value.u64TaggedField1);
    value.u64VarField1 = _readUnsignedSchemaCompatibleU64VarField1(
        context.readField<Object?>(
            _unsignedSchemaCompatibleForyFields[12], value.u64VarField1),
        value.u64VarField1);
    value.u32VarField1 = _readUnsignedSchemaCompatibleU32VarField1(
        context.readField<Object?>(
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
      _TwoEnumFieldStructEvolutionForySerializer.new);
  Fory.bindGeneratedStructFactory(Item, _ItemForySerializer.new);
  Fory.bindGeneratedStructFactory(
      SimpleStruct, _SimpleStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      EvolvingOverrideStruct, _EvolvingOverrideStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      FixedOverrideStruct, _FixedOverrideStructForySerializer.new);
  Fory.bindGeneratedStructFactory(Item1, _Item1ForySerializer.new);
  Fory.bindGeneratedStructFactory(
      StructWithUnion2, _StructWithUnion2ForySerializer.new);
  Fory.bindGeneratedStructFactory(
      StructWithList, _StructWithListForySerializer.new);
  Fory.bindGeneratedStructFactory(
      StructWithMap, _StructWithMapForySerializer.new);
  Fory.bindGeneratedStructFactory(MyStruct, _MyStructForySerializer.new);
  Fory.bindGeneratedStructFactory(MyWrapper, _MyWrapperForySerializer.new);
  Fory.bindGeneratedStructFactory(
      EmptyWrapper, _EmptyWrapperForySerializer.new);
  Fory.bindGeneratedStructFactory(
      VersionCheckStruct, _VersionCheckStructForySerializer.new);
  Fory.bindGeneratedStructFactory(Dog, _DogForySerializer.new);
  Fory.bindGeneratedStructFactory(Cat, _CatForySerializer.new);
  Fory.bindGeneratedStructFactory(
      AnimalListHolder, _AnimalListHolderForySerializer.new);
  Fory.bindGeneratedStructFactory(
      AnimalMapHolder, _AnimalMapHolderForySerializer.new);
  Fory.bindGeneratedStructFactory(EmptyStruct, _EmptyStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      OneStringFieldStruct, _OneStringFieldStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      TwoStringFieldStruct, _TwoStringFieldStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      OneEnumFieldStruct, _OneEnumFieldStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      TwoEnumFieldStruct, _TwoEnumFieldStructForySerializer.new);
  Fory.bindGeneratedStructFactory(NullableComprehensiveSchemaConsistent,
      _NullableComprehensiveSchemaConsistentForySerializer.new);
  Fory.bindGeneratedStructFactory(NullableComprehensiveCompatible,
      _NullableComprehensiveCompatibleForySerializer.new);
  Fory.bindGeneratedStructFactory(
      RefInnerSchemaConsistent, _RefInnerSchemaConsistentForySerializer.new);
  Fory.bindGeneratedStructFactory(
      RefOuterSchemaConsistent, _RefOuterSchemaConsistentForySerializer.new);
  Fory.bindGeneratedStructFactory(
      RefInnerCompatible, _RefInnerCompatibleForySerializer.new);
  Fory.bindGeneratedStructFactory(
      RefOuterCompatible, _RefOuterCompatibleForySerializer.new);
  Fory.bindGeneratedStructFactory(
      RefOverrideElement, _RefOverrideElementForySerializer.new);
  Fory.bindGeneratedStructFactory(
      CircularRefStruct, _CircularRefStructForySerializer.new);
  Fory.bindGeneratedStructFactory(
      UnsignedSchemaConsistent, _UnsignedSchemaConsistentForySerializer.new);
  Fory.bindGeneratedStructFactory(UnsignedSchemaConsistentSimple,
      _UnsignedSchemaConsistentSimpleForySerializer.new);
  Fory.bindGeneratedStructFactory(
      UnsignedSchemaCompatible, _UnsignedSchemaCompatibleForySerializer.new);
}

void _registerXlangTestModelsForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  _installGeneratedForyBindings();
  if (type == Color) {
    fory.registerEnum(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TestEnum) {
    fory.registerEnum(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStructEvolution) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == SimpleStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingOverrideStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedOverrideStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Item1) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithUnion2) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithList) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == StructWithMap) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == MyWrapper) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyWrapper) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == VersionCheckStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Dog) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Cat) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalListHolder) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == AnimalMapHolder) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EmptyStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneStringFieldStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoStringFieldStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == OneEnumFieldStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == TwoEnumFieldStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveSchemaConsistent) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == NullableComprehensiveCompatible) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerSchemaConsistent) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterSchemaConsistent) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefInnerCompatible) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOuterCompatible) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefOverrideElement) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == CircularRefStruct) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistent) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaConsistentSimple) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == UnsignedSchemaCompatible) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void _registerXlangTestModelsForyTypes(Fory fory) {
  _installGeneratedForyBindings();
  fory.registerEnum(Color,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Color');
  fory.registerEnum(TestEnum,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'TestEnum');
  fory.registerStruct(TwoEnumFieldStructEvolution,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStructEvolution');
  fory.registerStruct(Item,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item');
  fory.registerStruct(SimpleStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'SimpleStruct');
  fory.registerStruct(EvolvingOverrideStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EvolvingOverrideStruct');
  fory.registerStruct(FixedOverrideStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'FixedOverrideStruct');
  fory.registerStruct(Item1,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Item1');
  fory.registerStruct(StructWithUnion2,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithUnion2');
  fory.registerStruct(StructWithList,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithList');
  fory.registerStruct(StructWithMap,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'StructWithMap');
  fory.registerStruct(MyStruct,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyStruct');
  fory.registerStruct(MyWrapper,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'MyWrapper');
  fory.registerStruct(EmptyWrapper,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'EmptyWrapper');
  fory.registerStruct(VersionCheckStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'VersionCheckStruct');
  fory.registerStruct(Dog,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Dog');
  fory.registerStruct(Cat,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'Cat');
  fory.registerStruct(AnimalListHolder,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalListHolder');
  fory.registerStruct(AnimalMapHolder,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'AnimalMapHolder');
  fory.registerStruct(EmptyStruct,
      namespace: 'fory_test/entity/xlang_test_models', typeName: 'EmptyStruct');
  fory.registerStruct(OneStringFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneStringFieldStruct');
  fory.registerStruct(TwoStringFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoStringFieldStruct');
  fory.registerStruct(OneEnumFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'OneEnumFieldStruct');
  fory.registerStruct(TwoEnumFieldStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'TwoEnumFieldStruct');
  fory.registerStruct(NullableComprehensiveSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveSchemaConsistent');
  fory.registerStruct(NullableComprehensiveCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'NullableComprehensiveCompatible');
  fory.registerStruct(RefInnerSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerSchemaConsistent');
  fory.registerStruct(RefOuterSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterSchemaConsistent');
  fory.registerStruct(RefInnerCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefInnerCompatible');
  fory.registerStruct(RefOuterCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOuterCompatible');
  fory.registerStruct(RefOverrideElement,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'RefOverrideElement');
  fory.registerStruct(CircularRefStruct,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'CircularRefStruct');
  fory.registerStruct(UnsignedSchemaConsistent,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistent');
  fory.registerStruct(UnsignedSchemaConsistentSimple,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaConsistentSimple');
  fory.registerStruct(UnsignedSchemaCompatible,
      namespace: 'fory_test/entity/xlang_test_models',
      typeName: 'UnsignedSchemaCompatible');
}
