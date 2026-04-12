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
import 'person.dart';

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

const List<GeneratedFieldInfo> _personForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'age',
    identifier: 'age',
    id: null,
    fieldType: GeneratedFieldType(
      type: Int32,
      typeId: 5,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'tags',
    identifier: 'tags',
    id: null,
    fieldType: GeneratedFieldType(
      type: List,
      typeId: 22,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'scores',
    identifier: 'scores',
    id: null,
    fieldType: GeneratedFieldType(
      type: Map,
      typeId: 24,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[
        GeneratedFieldType(
          type: String,
          typeId: 21,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        ),
        GeneratedFieldType(
          type: Int32,
          typeId: 5,
          nullable: true,
          ref: false,
          dynamic: null,
          arguments: <GeneratedFieldType>[],
        )
      ],
    ),
  ),
  GeneratedFieldInfo(
    name: 'favoriteColor',
    identifier: 'favorite_color',
    id: null,
    fieldType: GeneratedFieldType(
      type: Color,
      typeId: 25,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _PersonFieldWriter = GeneratedStructFieldInfoWriter<Person>;
typedef _PersonFieldReader = GeneratedStructFieldInfoReader<Person>;

void _writePersonField0(
    WriteContext context, GeneratedStructFieldInfo field, Person value) {
  writeGeneratedStructFieldInfoValue(context, field, value.age);
}

void _writePersonField1(
    WriteContext context, GeneratedStructFieldInfo field, Person value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _writePersonField2(
    WriteContext context, GeneratedStructFieldInfo field, Person value) {
  writeGeneratedStructFieldInfoValue(context, field, value.tags);
}

void _writePersonField3(
    WriteContext context, GeneratedStructFieldInfo field, Person value) {
  writeGeneratedStructFieldInfoValue(context, field, value.scores);
}

void _writePersonField4(
    WriteContext context, GeneratedStructFieldInfo field, Person value) {
  writeGeneratedStructFieldInfoValue(context, field, value.favoriteColor);
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
  value.scores = _readPersonScores(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.scores);
}

void _readPersonField4(ReadContext context, Person value, Object? rawValue) {
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
    _writePersonField4,
  ],
  compatibleFactory: Person.new,
  compatibleReadersBySlot: <_PersonFieldReader>[
    _readPersonField0,
    _readPersonField1,
    _readPersonField2,
    _readPersonField3,
    _readPersonField4,
  ],
  type: Person,
  serializerFactory: _PersonForySerializer.new,
  evolving: true,
  fields: _personForyFieldInfo,
);

final class _PersonForySerializer extends Serializer<Person> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _PersonForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _personForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
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
      writeGeneratedStructFieldInfoValue(context, fields[2], value.tags);
      writeGeneratedStructFieldInfoValue(context, fields[3], value.scores);
      final cursor4 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor4.writeVarUint32(value.favoriteColor.index);
      cursor4.finish();
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
      value.tags = readGeneratedDirectListValue<String?>(
          context, fields[2], _readPersonTagsElement);
      value.scores = readGeneratedDirectMapValue<String, Int32>(
          context, fields[3], _readPersonScoresKey, _readPersonScoresValue);
      final cursor4 = GeneratedReadCursor.start(buffer);
      value.favoriteColor = Color.values[cursor4.readVarUint32()];
      cursor4.finish();
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
      value.scores = _readPersonScores(
          rawPerson3 is DeferredReadRef
              ? context.getReadRef(rawPerson3.id)
              : rawPerson3,
          value.scores);
    }
    if (slots.containsSlot(4)) {
      final rawPerson4 = slots.valueForSlot(4);
      value.favoriteColor = _readPersonFavoriteColor(
          rawPerson4 is DeferredReadRef
              ? context.getReadRef(rawPerson4.id)
              : rawPerson4,
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

String? _readPersonTagsElement(Object? value) {
  return value == null
      ? null as String?
      : value == null
          ? (throw StateError('Received null for non-nullable value.'))
          : value as String;
}

List<String?> _readPersonTags(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as List<String?>
          : (throw StateError('Received null for non-nullable field tags.')))
      : List.castFrom<dynamic, String?>(value as List);
}

String _readPersonScoresKey(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable scores map key.'))
      : value as String;
}

Int32 _readPersonScoresValue(Object? value) {
  return value == null
      ? (throw StateError('Received null for non-nullable scores map value.'))
      : value as Int32;
}

Map<String, Int32> _readPersonScores(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Map<String, Int32>
          : (throw StateError('Received null for non-nullable field scores.')))
      : Map.castFrom<dynamic, dynamic, String, Int32>(value as Map);
}

Color _readPersonFavoriteColor(Object? value, [Object? fallback]) {
  return value == null
      ? (fallback != null
          ? fallback as Color
          : (throw StateError(
              'Received null for non-nullable field favoriteColor.')))
      : value as Color;
}

const List<GeneratedFieldInfo> _refNodeForyFieldInfo = <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'name',
    identifier: 'name',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
  GeneratedFieldInfo(
    name: 'self',
    identifier: 'self',
    id: null,
    fieldType: GeneratedFieldType(
      type: RefNode,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _RefNodeFieldWriter = GeneratedStructFieldInfoWriter<RefNode>;
typedef _RefNodeFieldReader = GeneratedStructFieldInfoReader<RefNode>;

void _writeRefNodeField0(
    WriteContext context, GeneratedStructFieldInfo field, RefNode value) {
  writeGeneratedStructFieldInfoValue(context, field, value.name);
}

void _writeRefNodeField1(
    WriteContext context, GeneratedStructFieldInfo field, RefNode value) {
  writeGeneratedStructFieldInfoValue(context, field, value.self);
}

void _readRefNodeField0(ReadContext context, RefNode value, Object? rawValue) {
  value.name = _readRefNodeName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readRefNodeField1(ReadContext context, RefNode value, Object? rawValue) {
  value.self = _readRefNodeSelf(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.self);
}

final GeneratedStructRegistration<RefNode> _refNodeForyRegistration =
    GeneratedStructRegistration<RefNode>(
  fieldWritersBySlot: <_RefNodeFieldWriter>[
    _writeRefNodeField0,
    _writeRefNodeField1,
  ],
  compatibleFactory: RefNode.new,
  compatibleReadersBySlot: <_RefNodeFieldReader>[
    _readRefNodeField0,
    _readRefNodeField1,
  ],
  type: RefNode,
  serializerFactory: _RefNodeForySerializer.new,
  evolving: true,
  fields: _refNodeForyFieldInfo,
);

final class _RefNodeForySerializer extends Serializer<RefNode> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _RefNodeForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refNodeForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _refNodeForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefNode value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      final fields = _writeFields(context);
      context.writeString(value.name);
      writeGeneratedStructFieldInfoValue(context, fields[1], value.self);
      return;
    }
    final writers = _refNodeForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefNode read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = RefNode();
    context.reference(value);
    if (slots == null) {
      final fields = _readFields(context);
      value.name = context.readString();
      value.self = _readRefNodeSelf(
          readGeneratedStructFieldInfoValue(context, fields[1], value.self),
          value.self);
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawRefNode0 = slots.valueForSlot(0);
      value.name = _readRefNodeName(
          rawRefNode0 is DeferredReadRef
              ? context.getReadRef(rawRefNode0.id)
              : rawRefNode0,
          value.name);
    }
    if (slots.containsSlot(1)) {
      final rawRefNode1 = slots.valueForSlot(1);
      value.self = _readRefNodeSelf(
          rawRefNode1 is DeferredReadRef
              ? context.getReadRef(rawRefNode1.id)
              : rawRefNode1,
          value.self);
    }
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

const List<GeneratedFieldInfo> _evolvingPayloadForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'value',
    identifier: 'value',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _EvolvingPayloadFieldWriter
    = GeneratedStructFieldInfoWriter<EvolvingPayload>;
typedef _EvolvingPayloadFieldReader
    = GeneratedStructFieldInfoReader<EvolvingPayload>;

void _writeEvolvingPayloadField0(WriteContext context,
    GeneratedStructFieldInfo field, EvolvingPayload value) {
  writeGeneratedStructFieldInfoValue(context, field, value.value);
}

void _readEvolvingPayloadField0(
    ReadContext context, EvolvingPayload value, Object? rawValue) {
  value.value = _readEvolvingPayloadValue(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.value);
}

final GeneratedStructRegistration<EvolvingPayload>
    _evolvingPayloadForyRegistration =
    GeneratedStructRegistration<EvolvingPayload>(
  fieldWritersBySlot: <_EvolvingPayloadFieldWriter>[
    _writeEvolvingPayloadField0,
  ],
  compatibleFactory: EvolvingPayload.new,
  compatibleReadersBySlot: <_EvolvingPayloadFieldReader>[
    _readEvolvingPayloadField0,
  ],
  type: EvolvingPayload,
  serializerFactory: _EvolvingPayloadForySerializer.new,
  evolving: true,
  fields: _evolvingPayloadForyFieldInfo,
);

final class _EvolvingPayloadForySerializer extends Serializer<EvolvingPayload> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _EvolvingPayloadForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _evolvingPayloadForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _evolvingPayloadForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EvolvingPayload value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.value);
      return;
    }
    final writers = _evolvingPayloadForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = EvolvingPayload();
    context.reference(value);
    if (slots == null) {
      value.value = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawEvolvingPayload0 = slots.valueForSlot(0);
      value.value = _readEvolvingPayloadValue(
          rawEvolvingPayload0 is DeferredReadRef
              ? context.getReadRef(rawEvolvingPayload0.id)
              : rawEvolvingPayload0,
          value.value);
    }
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

const List<GeneratedFieldInfo> _fixedPayloadForyFieldInfo =
    <GeneratedFieldInfo>[
  GeneratedFieldInfo(
    name: 'value',
    identifier: 'value',
    id: null,
    fieldType: GeneratedFieldType(
      type: String,
      typeId: 21,
      nullable: false,
      ref: false,
      dynamic: null,
      arguments: <GeneratedFieldType>[],
    ),
  ),
];

typedef _FixedPayloadFieldWriter = GeneratedStructFieldInfoWriter<FixedPayload>;
typedef _FixedPayloadFieldReader = GeneratedStructFieldInfoReader<FixedPayload>;

void _writeFixedPayloadField0(
    WriteContext context, GeneratedStructFieldInfo field, FixedPayload value) {
  writeGeneratedStructFieldInfoValue(context, field, value.value);
}

void _readFixedPayloadField0(
    ReadContext context, FixedPayload value, Object? rawValue) {
  value.value = _readFixedPayloadValue(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.value);
}

final GeneratedStructRegistration<FixedPayload> _fixedPayloadForyRegistration =
    GeneratedStructRegistration<FixedPayload>(
  fieldWritersBySlot: <_FixedPayloadFieldWriter>[
    _writeFixedPayloadField0,
  ],
  compatibleFactory: FixedPayload.new,
  compatibleReadersBySlot: <_FixedPayloadFieldReader>[
    _readFixedPayloadField0,
  ],
  type: FixedPayload,
  serializerFactory: _FixedPayloadForySerializer.new,
  evolving: false,
  fields: _fixedPayloadForyFieldInfo,
);

final class _FixedPayloadForySerializer extends Serializer<FixedPayload> {
  List<GeneratedStructFieldInfo>? _generatedFields;

  _FixedPayloadForySerializer();

  List<GeneratedStructFieldInfo> _writeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _fixedPayloadForyRegistration,
    );
  }

  List<GeneratedStructFieldInfo> _readFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructFieldInfos(
      context.typeResolver,
      _fixedPayloadForyRegistration,
    );
  }

  @override
  void write(WriteContext context, FixedPayload value) {
    final slots = generatedStructWriteSlots(context);
    if (slots == null) {
      context.writeString(value.value);
      return;
    }
    final writers = _fixedPayloadForyRegistration.fieldWritersBySlot;
    for (final field in slots.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  FixedPayload read(ReadContext context) {
    final slots = generatedStructReadSlots(context);
    final value = FixedPayload();
    context.reference(value);
    if (slots == null) {
      value.value = context.readString();
      return value;
    }
    if (slots.containsSlot(0)) {
      final rawFixedPayload0 = slots.valueForSlot(0);
      value.value = _readFixedPayloadValue(
          rawFixedPayload0 is DeferredReadRef
              ? context.getReadRef(rawFixedPayload0.id)
              : rawFixedPayload0,
          value.value);
    }
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

final GeneratedEnumRegistration _colorForyRegistration =
    GeneratedEnumRegistration(
  type: Color,
  serializerFactory: _ColorForySerializer.new,
);

void registerPersonForyType(Fory fory, Type type,
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
  if (type == RefNode) {
    registerGeneratedStruct(fory, _refNodeForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == EvolvingPayload) {
    registerGeneratedStruct(fory, _evolvingPayloadForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  if (type == FixedPayload) {
    registerGeneratedStruct(fory, _fixedPayloadForyRegistration,
        id: id, namespace: namespace, typeName: typeName);
    return;
  }
  throw ArgumentError.value(
      type, 'type', 'No generated registration for this library.');
}

void registerPersonForyTypes(Fory fory) {
  registerGeneratedEnum(fory, _colorForyRegistration,
      namespace: 'fory_test/model/person', typeName: 'Color');
  registerGeneratedStruct(fory, _personForyRegistration,
      namespace: 'fory_test/model/person', typeName: 'Person');
  registerGeneratedStruct(fory, _refNodeForyRegistration,
      namespace: 'fory_test/model/person', typeName: 'RefNode');
  registerGeneratedStruct(fory, _evolvingPayloadForyRegistration,
      namespace: 'fory_test/model/person', typeName: 'EvolvingPayload');
  registerGeneratedStruct(fory, _fixedPayloadForyRegistration,
      namespace: 'fory_test/model/person', typeName: 'FixedPayload');
}
