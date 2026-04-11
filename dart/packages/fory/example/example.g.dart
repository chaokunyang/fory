// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'example.dart';

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

const _ColorForySerializer _colorForySerializer = _ColorForySerializer();

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
    context.writeField(_personForyFields[3], value.favoriteColor);
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
    value.favoriteColor = _readPersonFavoriteColor(
        context.readField<Object?>(_personForyFields[3], value.favoriteColor),
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

List<String> _readPersonTags(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError('Received null for non-nullable field tags.')))
      : List<String>.of(((value as List)).map((item) => item == null
          ? (throw StateError('Received null for non-nullable list item.'))
          : item as String));
}

Color _readPersonFavoriteColor(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError(
              'Received null for non-nullable field favoriteColor.')))
      : value as Color;
}

Serializer _serializerForGeneratedType(Type type) {
  if (type == Color) return _colorForySerializer;
  if (type == Person) return _personForySerializer;
  throw ArgumentError.value(
      type, 'type', 'No generated serializer for this library.');
}

void _registerExampleForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  fory.register(type, _serializerForGeneratedType(type),
      id: id, namespace: namespace, typeName: typeName);
}

void _registerExampleForyTypes(Fory fory) {
  fory.register(Color, _colorForySerializer,
      namespace: 'fory/example/example', typeName: 'Color');
  fory.register(Person, _personForySerializer,
      namespace: 'fory/example/example', typeName: 'Person');
}
