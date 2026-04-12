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
import 'example.dart';

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

const List<GeneratedFieldMetadata> _personForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'age',
    identifier: 'age',
    id: null,
    shape: GeneratedTypeShape(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'name',
    identifier: 'name',
    id: null,
    shape: GeneratedTypeShape(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'tags',
    identifier: 'tags',
    id: null,
    shape: GeneratedTypeShape(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[
        GeneratedTypeShape(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedTypeShape>[],
        )
      ],
    ),
  ),
  GeneratedFieldMetadata(
    name: 'favoriteColor',
    identifier: 'favorite_color',
    id: null,
    shape: GeneratedTypeShape(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _PersonFieldWriter = GeneratedStructFieldWriter<Person>;
typedef _PersonFieldReader = GeneratedStructFieldReader<Person>;

void _writePersonField0(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructFieldValue(context, field, value.age);
}

void _writePersonField1(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructFieldValue(context, field, value.name);
}

void _writePersonField2(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructFieldValue(context, field, value.tags);
}

void _writePersonField3(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructFieldValue(context, field, value.favoriteColor);
}

void _readPersonField0(ReadContext context, Person value, Object? rawValue) {
  value.age = _readPersonAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readPersonField1(ReadContext context, Person value, Object? rawValue) {
  value.name = _readPersonName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readPersonField2(ReadContext context, Person value, Object? rawValue) {
  value.tags = _readPersonTags(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.tags);
}

void _readPersonField3(ReadContext context, Person value, Object? rawValue) {
  value.favoriteColor = _readPersonFavoriteColor(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.favoriteColor);
}

final GeneratedStructRegistration<Person> _personForyRegistration =
    GeneratedStructRegistration<Person>(
  fieldWritersBySlot: <_PersonFieldWriter>[
    _writePersonField0,
    _writePersonField1,
    _writePersonField2,
    _writePersonField3,
  ],
  compatibleFactory: Person.new,
  compatibleReadersBySlot: <_PersonFieldReader>[
    _readPersonField0,
    _readPersonField1,
    _readPersonField2,
    _readPersonField3,
  ],
  type: Person,
  serializerFactory: _PersonForySerializer.new,
  evolving: true,
  fields: _personForyFieldMetadata,
);

final class _PersonForySerializer extends Serializer<Person> {
  List<GeneratedStructField>? _generatedFields;

  _PersonForySerializer();

  List<GeneratedStructField> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFields(
      context.typeResolver,
      _personForyRegistration,
    );
  }

  List<GeneratedStructField> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFields(
      context.typeResolver,
      _personForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Person value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _writeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.age.value);
      cursor0.finish();
      context.writeString(value.name);
      writeGeneratedStructFieldValue(context, fields[2], value.tags);
      final cursor3 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor3.writeVarUint32(value.favoriteColor.index);
      cursor3.finish();
      return;
    }
    final writers = _personForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Person read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = Person();
    context.reference(value);
    if (slots == null) {
      final buffer = context.buffer;
      final fields = _readFields(context);
      final cursor0 = GeneratedReadCursor.start(buffer);
      value.age = Int32(cursor0.readVarInt32());
      cursor0.finish();
      value.name = context.readString();
      value.tags = readGeneratedDirectListValue<String>(
          context, fields[2], _readPersonTagsElement);
      final cursor3 = GeneratedReadCursor.start(buffer);
      value.favoriteColor = Color.values[cursor3.readVarUint32()];
      cursor3.finish();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawPerson0 = slots.valueForSlot(0);
      value.age = _readPersonAge(
          rawPerson0 is DeferredReadRef
              ? context.getReadRef(rawPerson0.id)
              : rawPerson0,
          value.age);
    }
    if (slots.containsSlot(1)) {
      final rawPerson1 = slots.valueForSlot(1);
      value.name = _readPersonName(
          rawPerson1 is DeferredReadRef
              ? context.getReadRef(rawPerson1.id)
              : rawPerson1,
          value.name);
    }
    if (slots.containsSlot(2)) {
      final rawPerson2 = slots.valueForSlot(2);
      value.tags = _readPersonTags(
          rawPerson2 is DeferredReadRef
              ? context.getReadRef(rawPerson2.id)
              : rawPerson2,
          value.tags);
    }
    if (slots.containsSlot(3)) {
      final rawPerson3 = slots.valueForSlot(3);
      value.favoriteColor = _readPersonFavoriteColor(
          rawPerson3 is DeferredReadRef
              ? context.getReadRef(rawPerson3.id)
              : rawPerson3,
          value.favoriteColor);
    }
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

String _readPersonTagsElement(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable tags item.'))
      : value as String;
}

List<String> _readPersonTags(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String>
          : (throw StateError('Received null for non-nullable field tags.')))
      : List.castFrom<dynamic, String>(value as List);
}

Color _readPersonFavoriteColor(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError(
              'Received null for non-nullable field favoriteColor.')))
      : value as Color;
}

final GeneratedEnumRegistration _colorForyRegistration =
    GeneratedEnumRegistration(
  type: Color,
  serializerFactory: _ColorForySerializer.new,
);

void registerExampleForyType(Fory fory, Type type,
    {int? id, String? namespace, String? typeName}) {
  if (type == Color) {
    registerGeneratedEnum(fory, _colorForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == Person) {
    registerGeneratedStruct(fory, _personForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void registerExampleForyTypes(Fory fory) {
  registerGeneratedEnum(fory, _colorForyRegistration,
      namespace: 'fory/example/example', typeName: 'Color');
  registerGeneratedStruct(fory, _personForyRegistration,
      namespace: 'fory/example/example', typeName: 'Person');
}
