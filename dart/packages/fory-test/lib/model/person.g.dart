// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'person.dart';

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

const List<Map<String, Object?>> _personForyFieldMetadata =
    <Map<String, Object?>>[
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

final List<Object> _personForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _personForyFieldMetadata[0]),
    generatedField(1, _personForyFieldMetadata[1]),
    generatedField(2, _personForyFieldMetadata[2]),
    generatedField(3, _personForyFieldMetadata[3]),
    generatedField(4, _personForyFieldMetadata[4]),
  ],
);

final class _PersonForySerializer extends _GeneratedStructSerializer<Person> {
  const _PersonForySerializer();
  @override
  void write(WriteContext context, Person value) {
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
          case 2:
            writeGeneratedField(context, field, value.tags);
            break;
          case 3:
            writeGeneratedField(context, field, value.scores);
            break;
          case 4:
            writeGeneratedField(context, field, value.favoriteColor);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _personForyFields[0], value.age);
    writeGeneratedField(context, _personForyFields[1], value.name);
    writeGeneratedField(context, _personForyFields[2], value.tags);
    writeGeneratedField(context, _personForyFields[3], value.scores);
    writeGeneratedField(context, _personForyFields[4], value.favoriteColor);
  }

  @override
  Person read(ReadContext context) {
    final value = Person();
    context.reference(value);
    value.age = _readPersonAge(
        readGeneratedField<Object?>(context, _personForyFields[0], value.age),
        value.age);
    value.name = _readPersonName(
        readGeneratedField<Object?>(context, _personForyFields[1], value.name),
        value.name);
    value.tags = _readPersonTags(
        readGeneratedField<Object?>(context, _personForyFields[2], value.tags),
        value.tags);
    value.scores = _readPersonScores(
        readGeneratedField<Object?>(
            context, _personForyFields[3], value.scores),
        value.scores);
    value.favoriteColor = _readPersonFavoriteColor(
        readGeneratedField<Object?>(
            context, _personForyFields[4], value.favoriteColor),
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

const List<Map<String, Object?>> _refNodeForyFieldMetadata =
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

final List<Object> _refNodeForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _refNodeForyFieldMetadata[0]),
    generatedField(1, _refNodeForyFieldMetadata[1]),
  ],
);

final class _RefNodeForySerializer extends _GeneratedStructSerializer<RefNode> {
  const _RefNodeForySerializer();
  @override
  void write(WriteContext context, RefNode value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.name);
            break;
          case 1:
            writeGeneratedField(context, field, value.self);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _refNodeForyFields[0], value.name);
    writeGeneratedField(context, _refNodeForyFields[1], value.self);
  }

  @override
  RefNode read(ReadContext context) {
    final value = RefNode();
    context.reference(value);
    value.name = _readRefNodeName(
        readGeneratedField<Object?>(context, _refNodeForyFields[0], value.name),
        value.name);
    value.self = _readRefNodeSelf(
        readGeneratedField<Object?>(context, _refNodeForyFields[1], value.self),
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

const List<Map<String, Object?>> _evolvingPayloadForyFieldMetadata =
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

final List<Object> _evolvingPayloadForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _evolvingPayloadForyFieldMetadata[0]),
  ],
);

final class _EvolvingPayloadForySerializer
    extends _GeneratedStructSerializer<EvolvingPayload> {
  const _EvolvingPayloadForySerializer();
  @override
  void write(WriteContext context, EvolvingPayload value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.value);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _evolvingPayloadForyFields[0], value.value);
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final value = EvolvingPayload();
    context.reference(value);
    value.value = _readEvolvingPayloadValue(
        readGeneratedField<Object?>(
            context, _evolvingPayloadForyFields[0], value.value),
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

const List<Map<String, Object?>> _fixedPayloadForyFieldMetadata =
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

final List<Object> _fixedPayloadForyFields = List<Object>.unmodifiable(
  <Object>[
    generatedField(0, _fixedPayloadForyFieldMetadata[0]),
  ],
);

final class _FixedPayloadForySerializer
    extends _GeneratedStructSerializer<FixedPayload> {
  const _FixedPayloadForySerializer();
  @override
  void write(WriteContext context, FixedPayload value) {
    final compatibleFields = generatedCompatibleWriteFields(context);
    if (compatibleFields != null) {
      for (final field in compatibleFields) {
        switch (generatedFieldSlot(field)) {
          case 0:
            writeGeneratedField(context, field, value.value);
            break;
          default:
            break;
        }
      }
      return;
    }
    writeGeneratedField(context, _fixedPayloadForyFields[0], value.value);
  }

  @override
  FixedPayload read(ReadContext context) {
    final value = FixedPayload();
    context.reference(value);
    value.value = _readFixedPayloadValue(
        readGeneratedField<Object?>(
            context, _fixedPayloadForyFields[0], value.value),
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
  Fory.bindGeneratedStructFactory(Person, _PersonForySerializer.new,
      evolving: true, fields: _personForyFieldMetadata);
  Fory.bindGeneratedStructFactory(RefNode, _RefNodeForySerializer.new,
      evolving: true, fields: _refNodeForyFieldMetadata);
  Fory.bindGeneratedStructFactory(
      EvolvingPayload, _EvolvingPayloadForySerializer.new,
      evolving: true, fields: _evolvingPayloadForyFieldMetadata);
  Fory.bindGeneratedStructFactory(FixedPayload, _FixedPayloadForySerializer.new,
      evolving: false, fields: _fixedPayloadForyFieldMetadata);
}

void _registerPersonForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  _installGeneratedForyBindings();
  if (type == Color) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Person) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == RefNode) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingPayload) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedPayload) {
    fory.register(type, id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void _registerPersonForyTypes(Fory fory) {
  _installGeneratedForyBindings();
  fory.register(Color, namespace: 'fory_test/model/person', typeName: 'Color');
  fory.register(Person,
      namespace: 'fory_test/model/person', typeName: 'Person');
  fory.register(RefNode,
      namespace: 'fory_test/model/person', typeName: 'RefNode');
  fory.register(EvolvingPayload,
      namespace: 'fory_test/model/person', typeName: 'EvolvingPayload');
  fory.register(FixedPayload,
      namespace: 'fory_test/model/person', typeName: 'FixedPayload');
}
