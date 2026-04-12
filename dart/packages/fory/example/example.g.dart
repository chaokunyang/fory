// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'example.dart';

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
    writeGeneratedField(context, _personForyFields[3], value.favoriteColor);
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
    value.favoriteColor = _readPersonFavoriteColor(
        readGeneratedField<Object?>(
            context, _personForyFields[3], value.favoriteColor),
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

bool _generatedForyBindingsInstalled = false;

void _installGeneratedForyBindings() {
  if (_generatedForyBindingsInstalled) {
    return;
  }
  _generatedForyBindingsInstalled = true;
  Fory.bindGeneratedEnumFactory(Color, _ColorForySerializer.new);
  Fory.bindGeneratedStructFactory(Person, _PersonForySerializer.new,
      evolving: true, fields: _personForyFieldMetadata);
}

void _registerExampleForyType(Fory fory, Type type,
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
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void _registerExampleForyTypes(Fory fory) {
  _installGeneratedForyBindings();
  fory.register(Color, namespace: 'fory/example/example', typeName: 'Color');
  fory.register(Person, namespace: 'fory/example/example', typeName: 'Person');
}
