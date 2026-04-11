// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'person.dart';

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

const List<Map<String, Object?>> _personForyFields = <Map<String, Object?>>[
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
          'arguments': <Object?>[],
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
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': Int32,
          'typeId': 5,
          'nullable': true,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
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
      'arguments': <Object?>[],
    },
  },
];

final class _PersonForySerializer extends Serializer<Person> {
  const _PersonForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _personForyFields;
  @override
  void write(WriteContext context, Person value) {
    final compatibleFields = context.compatibleFieldOrder(_personForyFields);
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
    context.writeField(_personForyFields[0], value.age);
    context.writeField(_personForyFields[1], value.name);
    context.writeField(_personForyFields[2], value.tags);
    context.writeField(_personForyFields[3], value.scores);
    context.writeField(_personForyFields[4], value.favoriteColor);
  }

  @override
  Person read(ReadContext context) {
    final value = Person();
    context.reference(value);
    value.age = _readPersonAge(
        context.readField<Object?>(_personForyFields[0], value.age), value.age);
    value.name = _readPersonName(
        context.readField<Object?>(_personForyFields[1], value.name),
        value.name);
    value.tags = _readPersonTags(
        context.readField<Object?>(_personForyFields[2], value.tags),
        value.tags);
    value.scores = _readPersonScores(
        context.readField<Object?>(_personForyFields[3], value.scores),
        value.scores);
    value.favoriteColor = _readPersonFavoriteColor(
        context.readField<Object?>(_personForyFields[4], value.favoriteColor),
        value.favoriteColor);
    return value;
  }
}

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

const List<Map<String, Object?>> _refNodeForyFields = <Map<String, Object?>>[
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
    'name': 'self',
    'identifier': 'self',
    'id': null,
    'shape': <String, Object?>{
      'type': RefNode,
      'typeId': 28,
      'nullable': true,
      'ref': true,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final class _RefNodeForySerializer extends Serializer<RefNode> {
  const _RefNodeForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _refNodeForyFields;
  @override
  void write(WriteContext context, RefNode value) {
    final compatibleFields = context.compatibleFieldOrder(_refNodeForyFields);
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
    context.writeField(_refNodeForyFields[0], value.name);
    context.writeField(_refNodeForyFields[1], value.self);
  }

  @override
  RefNode read(ReadContext context) {
    final value = RefNode();
    context.reference(value);
    value.name = _readRefNodeName(
        context.readField<Object?>(_refNodeForyFields[0], value.name),
        value.name);
    value.self = _readRefNodeSelf(
        context.readField<Object?>(_refNodeForyFields[1], value.self),
        value.self);
    return value;
  }
}

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

const List<Map<String, Object?>> _evolvingPayloadForyFields =
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
      'arguments': <Object?>[],
    },
  },
];

final class _EvolvingPayloadForySerializer extends Serializer<EvolvingPayload> {
  const _EvolvingPayloadForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => true;
  @override
  List<Map<String, Object?>> get fields => _evolvingPayloadForyFields;
  @override
  void write(WriteContext context, EvolvingPayload value) {
    final compatibleFields =
        context.compatibleFieldOrder(_evolvingPayloadForyFields);
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
    context.writeField(_evolvingPayloadForyFields[0], value.value);
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final value = EvolvingPayload();
    context.reference(value);
    value.value = _readEvolvingPayloadValue(
        context.readField<Object?>(_evolvingPayloadForyFields[0], value.value),
        value.value);
    return value;
  }
}

String _readEvolvingPayloadValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field value.')))
      : value as String;
}

const List<Map<String, Object?>> _fixedPayloadForyFields =
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
      'arguments': <Object?>[],
    },
  },
];

final class _FixedPayloadForySerializer extends Serializer<FixedPayload> {
  const _FixedPayloadForySerializer();
  @override
  bool get isStruct => true;
  @override
  bool get evolving => false;
  @override
  List<Map<String, Object?>> get fields => _fixedPayloadForyFields;
  @override
  void write(WriteContext context, FixedPayload value) {
    final compatibleFields =
        context.compatibleFieldOrder(_fixedPayloadForyFields);
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
    context.writeField(_fixedPayloadForyFields[0], value.value);
  }

  @override
  FixedPayload read(ReadContext context) {
    final value = FixedPayload();
    context.reference(value);
    value.value = _readFixedPayloadValue(
        context.readField<Object?>(_fixedPayloadForyFields[0], value.value),
        value.value);
    return value;
  }
}

String _readFixedPayloadValue(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as String
          : (throw StateError('Received null for non-nullable field value.')))
      : value as String;
}

bool _generatedForyBindingsInstalled = false;

void _installGeneratedForyBindings() {
  if (_generatedForyBindingsInstalled) {
    return;
  }
  _generatedForyBindingsInstalled = true;
  Fory.bindGeneratedEnumFactory(Color, _ColorForySerializer.new);
  Fory.bindGeneratedStructFactory(Person, _PersonForySerializer.new);
  Fory.bindGeneratedStructFactory(RefNode, _RefNodeForySerializer.new);
  Fory.bindGeneratedStructFactory(
      EvolvingPayload, _EvolvingPayloadForySerializer.new);
  Fory.bindGeneratedStructFactory(
      FixedPayload, _FixedPayloadForySerializer.new);
}

void _registerPersonForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  _installGeneratedForyBindings();
  if (type == Color) {
    fory.registerEnum(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Person) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefNode) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingPayload) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedPayload) {
    fory.registerStruct(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void _registerPersonForyTypes(Fory fory) {
  _installGeneratedForyBindings();
  fory.registerEnum(Color,
      namespace: 'fory_test/model/person', typeName: 'Color');
  fory.registerStruct(Person,
      namespace: 'fory_test/model/person', typeName: 'Person');
  fory.registerStruct(RefNode,
      namespace: 'fory_test/model/person', typeName: 'RefNode');
  fory.registerStruct(EvolvingPayload,
      namespace: 'fory_test/model/person', typeName: 'EvolvingPayload');
  fory.registerStruct(FixedPayload,
      namespace: 'fory_test/model/person', typeName: 'FixedPayload');
}
