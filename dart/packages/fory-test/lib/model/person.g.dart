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

const _ColorForySerializer _colorForySerializer = _ColorForySerializer();

const List<Map<String, Object?>> _PersonForyFields = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'age',
    'identifier': 'age',
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
          'dynamic': null,
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
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        },
        <String, Object?>{
          'type': Int32,
          'typeId': 5,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': const <Object?>[],
        }
      ],
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
    final compatibleFields = context.compatibleFieldOrder(_PersonForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'age':
            context.writeField(field, value.age);
            break;
          case 'name':
            context.writeField(field, value.name);
            break;
          case 'tags':
            context.writeField(field, value.tags);
            break;
          case 'scores':
            context.writeField(field, value.scores);
            break;
          case 'favorite_color':
            context.writeField(field, value.favoriteColor);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_PersonForyFields[0], value.age);
    context.writeField(_PersonForyFields[1], value.name);
    context.writeField(_PersonForyFields[2], value.tags);
    context.writeField(_PersonForyFields[3], value.scores);
    context.writeField(_PersonForyFields[4], value.favoriteColor);
  }

  @override
  Person read(ReadContext context) {
    final value = Person();
    context.reference(value);
    value.age = _readPersonAge(
        context.readField<Object?>(_PersonForyFields[0], value.age), value.age);
    value.name = _readPersonName(
        context.readField<Object?>(_PersonForyFields[1], value.name),
        value.name);
    value.tags = _readPersonTags(
        context.readField<Object?>(_PersonForyFields[2], value.tags),
        value.tags);
    value.scores = _readPersonScores(
        context.readField<Object?>(_PersonForyFields[3], value.scores),
        value.scores);
    value.favoriteColor = _readPersonFavoriteColor(
        context.readField<Object?>(_PersonForyFields[4], value.favoriteColor),
        value.favoriteColor);
    return value;
  }
}

const _PersonForySerializer _personForySerializer = _PersonForySerializer();

Int32 _readPersonAge(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Int32
          : (throw StateError('Received null for non-nullable field age.')))
      : value as Int32;
}

String _readPersonName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

List<String?> _readPersonTags(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String?>
          : (throw StateError('Received null for non-nullable field tags.')))
      : List<String?>.of(((value as List)).map((item) => item == null
          ? null as String?
          : item == null
              ? (throw StateError('Received null for non-nullable value.'))
              : item as String));
}

Map<String, Int32> _readPersonScores(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, Int32>
          : (throw StateError('Received null for non-nullable field scores.')))
      : Map<String, Int32>.of(((value as Map)).map((key, value) => MapEntry(
          key == null
              ? (throw StateError('Received null for non-nullable map key.'))
              : key as String,
          value == null
              ? (throw StateError('Received null for non-nullable map value.'))
              : value as Int32)));
}

Color _readPersonFavoriteColor(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError(
              'Received null for non-nullable field favoriteColor.')))
      : value as Color;
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
    final compatibleFields = context.compatibleFieldOrder(_RefNodeForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'name':
            context.writeField(field, value.name);
            break;
          case 'self':
            context.writeField(field, value.self);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_RefNodeForyFields[0], value.name);
    context.writeField(_RefNodeForyFields[1], value.self);
  }

  @override
  RefNode read(ReadContext context) {
    final value = RefNode();
    context.reference(value);
    value.name = _readRefNodeName(
        context.readField<Object?>(_RefNodeForyFields[0], value.name),
        value.name);
    value.self = _readRefNodeSelf(
        context.readField<Object?>(_RefNodeForyFields[1], value.self),
        value.self);
    return value;
  }
}

const _RefNodeForySerializer _refNodeForySerializer = _RefNodeForySerializer();

String _readRefNodeName(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field name.')))
      : value as String;
}

RefNode? _readRefNodeSelf(Object? value, [Object? fallback]) {
  return value == null
      ? null as RefNode?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as RefNode;
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
    final compatibleFields =
        context.compatibleFieldOrder(_EvolvingPayloadForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'value':
            context.writeField(field, value.value);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_EvolvingPayloadForyFields[0], value.value);
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final value = EvolvingPayload();
    context.reference(value);
    value.value = _readEvolvingPayloadValue(
        context.readField<Object?>(_EvolvingPayloadForyFields[0], value.value),
        value.value);
    return value;
  }
}

const _EvolvingPayloadForySerializer _evolvingPayloadForySerializer =
    _EvolvingPayloadForySerializer();

String _readEvolvingPayloadValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field value.')))
      : value as String;
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
    final compatibleFields =
        context.compatibleFieldOrder(_FixedPayloadForyFields);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (field['identifier'] as String) {
          case 'value':
            context.writeField(field, value.value);
            break;
          default:
            break;
        }
      }
      return;
    }
    context.writeField(_FixedPayloadForyFields[0], value.value);
  }

  @override
  FixedPayload read(ReadContext context) {
    final value = FixedPayload();
    context.reference(value);
    value.value = _readFixedPayloadValue(
        context.readField<Object?>(_FixedPayloadForyFields[0], value.value),
        value.value);
    return value;
  }
}

const _FixedPayloadForySerializer _fixedPayloadForySerializer =
    _FixedPayloadForySerializer();

String _readFixedPayloadValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field value.')))
      : value as String;
}

Serializer _serializerForGeneratedType(Type type) {
  if (type == Color) return _colorForySerializer;
  if (type == Person) return _personForySerializer;
  if (type == RefNode) return _refNodeForySerializer;
  if (type == EvolvingPayload) return _evolvingPayloadForySerializer;
  if (type == FixedPayload) return _fixedPayloadForySerializer;
  throw ArgumentError.value(
      type, 'type', 'No generated serializer for this library.');
}

void _registerPersonForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  fory.register(type, _serializerForGeneratedType(type),
      id: id, namespace: namespace, typeName: typeName);
}

void _registerPersonForyTypes(Fory fory) {
  fory.register(Color, _colorForySerializer,
      namespace: 'fory_test/model/person', typeName: 'Color');
  fory.register(Person, _personForySerializer,
      namespace: 'fory_test/model/person', typeName: 'Person');
  fory.register(RefNode, _refNodeForySerializer,
      namespace: 'fory_test/model/person', typeName: 'RefNode');
  fory.register(EvolvingPayload, _evolvingPayloadForySerializer,
      namespace: 'fory_test/model/person', typeName: 'EvolvingPayload');
  fory.register(FixedPayload, _fixedPayloadForySerializer,
      namespace: 'fory_test/model/person', typeName: 'FixedPayload');
}
