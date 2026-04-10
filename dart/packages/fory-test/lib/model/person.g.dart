// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'person.dart';

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

const List<Map<String, Object?>> _PersonForyFields = <Map<String, Object?>>[
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
    'name': 'age',
    'identifier': 'age',
    'id': null,
    'shape': <String, Object?>{
      'type': Int32,
      'typeId': 4,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': const <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'favoriteColor',
    'identifier': 'favorite_color',
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
    'name': 'tags',
    'identifier': 'tags',
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
          'dynamic': true,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
  <String, Object?>{
    'name': 'scores',
    'identifier': 'scores',
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
          'nullable': false,
          'ref': false,
          'dynamic': true,
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': Int32,
          'typeId': 4,
          'nullable': false,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        }
      ],
    },
  },
];

final class _PersonForySerializer extends Serializer<Person> {
  const _PersonForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _PersonForyFields;
  @override
  void write(WriteContext context, Person value) {
    context.writeField(_PersonForyFields[0], value.name);
    context.writeField(_PersonForyFields[1], value.age);
    context.writeField(_PersonForyFields[2], value.favoriteColor);
    context.writeField(_PersonForyFields[3], value.tags);
    context.writeField(_PersonForyFields[4], value.scores);
  }

  @override
  Person read(ReadContext context) {
    final value = Person();
    context.reference(value);
    value.name = _readPersonName(
        context.readField<Object?>(_PersonForyFields[0], value.name));
    value.age = _readPersonAge(
        context.readField<Object?>(_PersonForyFields[1], value.age));
    value.favoriteColor = _readPersonFavoriteColor(
        context.readField<Object?>(_PersonForyFields[2], value.favoriteColor));
    value.tags = _readPersonTags(
        context.readField<Object?>(_PersonForyFields[3], value.tags));
    value.scores = _readPersonScores(
        context.readField<Object?>(_PersonForyFields[4], value.scores));
    return value;
  }
}

String _readPersonName(Object? value) {
  return value as String;
}

Int32 _readPersonAge(Object? value) {
  return value as Int32;
}

Color _readPersonFavoriteColor(Object? value) {
  return value as Color;
}

List<String?> _readPersonTags(Object? value) {
  return List<String?>.of(((value as List))
      .map((item) => item == null ? null as String? : item as String));
}

Map<String, Int32> _readPersonScores(Object? value) {
  return Map<String, Int32>.of(((value as Map))
      .map((key, value) => MapEntry(key as String, value as Int32)));
}

const List<Map<String, Object?>> _RefNodeForyFields = <Map<String, Object?>>[
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
    'name': 'self',
    'identifier': 'self',
    'id': null,
    'shape': <String, Object?>{
      'type': RefNode,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': null,
      'arguments': const <Object?>[],
    },
  },
];

final class _RefNodeForySerializer extends Serializer<RefNode> {
  const _RefNodeForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _RefNodeForyFields;
  @override
  void write(WriteContext context, RefNode value) {
    context.writeField(_RefNodeForyFields[0], value.name);
    context.writeField(_RefNodeForyFields[1], value.self);
  }

  @override
  RefNode read(ReadContext context) {
    final value = RefNode();
    context.reference(value);
    value.name = _readRefNodeName(
        context.readField<Object?>(_RefNodeForyFields[0], value.name));
    value.self = _readRefNodeSelf(
        context.readField<Object?>(_RefNodeForyFields[1], value.self));
    return value;
  }
}

String _readRefNodeName(Object? value) {
  return value as String;
}

RefNode? _readRefNodeSelf(Object? value) {
  return value == null ? null as RefNode? : value as RefNode;
}

const List<Map<String, Object?>> _EvolvingPayloadForyFields =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'value',
    'identifier': 'value',
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

final class _EvolvingPayloadForySerializer extends Serializer<EvolvingPayload> {
  const _EvolvingPayloadForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _EvolvingPayloadForyFields;
  @override
  void write(WriteContext context, EvolvingPayload value) {
    context.writeField(_EvolvingPayloadForyFields[0], value.value);
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final value = EvolvingPayload();
    context.reference(value);
    value.value = _readEvolvingPayloadValue(
        context.readField<Object?>(_EvolvingPayloadForyFields[0], value.value));
    return value;
  }
}

String _readEvolvingPayloadValue(Object? value) {
  return value as String;
}

const List<Map<String, Object?>> _FixedPayloadForyFields =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'value',
    'identifier': 'value',
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

final class _FixedPayloadForySerializer extends Serializer<FixedPayload> {
  const _FixedPayloadForySerializer();
  bool get isStruct => true;
  @override
  bool get evolving => false;
  @override
  List<Map<String, Object?>> get fields => _FixedPayloadForyFields;
  @override
  void write(WriteContext context, FixedPayload value) {
    context.writeField(_FixedPayloadForyFields[0], value.value);
  }

  @override
  FixedPayload read(ReadContext context) {
    final value = FixedPayload();
    context.reference(value);
    value.value = _readFixedPayloadValue(
        context.readField<Object?>(_FixedPayloadForyFields[0], value.value));
    return value;
  }
}

String _readFixedPayloadValue(Object? value) {
  return value as String;
}

void _registerPersonForyTypes(Fory fory) {
  fory.register(Color, const _ColorForySerializer(),
      namespace: 'fory_test/model/person', typeName: 'Color');
  fory.register(Person, const _PersonForySerializer(),
      namespace: 'fory_test/model/person', typeName: 'Person');
  fory.register(RefNode, const _RefNodeForySerializer(),
      namespace: 'fory_test/model/person', typeName: 'RefNode');
  fory.register(EvolvingPayload, const _EvolvingPayloadForySerializer(),
      namespace: 'fory_test/model/person', typeName: 'EvolvingPayload');
  fory.register(FixedPayload, const _FixedPayloadForySerializer(),
      namespace: 'fory_test/model/person', typeName: 'FixedPayload');
}
