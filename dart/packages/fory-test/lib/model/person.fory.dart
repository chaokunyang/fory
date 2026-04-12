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
    name: 'scores',
    identifier: 'scores',
    id: null,
    shape: GeneratedTypeShape(
      type: Map,
      typeId: 24,
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
        ),
        GeneratedTypeShape(
          type: Int32,
          typeId: 5,
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

typedef _PersonSessionWriter = GeneratedStructFieldWriter<Person>;
typedef _PersonSessionReader = GeneratedStructFieldReader<Person>;

void _writePersonSessionField0(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructRuntimeValue(context, field, value.age);
}

void _writePersonSessionField1(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _writePersonSessionField2(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructRuntimeValue(context, field, value.tags);
}

void _writePersonSessionField3(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructRuntimeValue(context, field, value.scores);
}

void _writePersonSessionField4(
    WriteContext context, GeneratedStructField field, Person value) {
  writeGeneratedStructRuntimeValue(context, field, value.favoriteColor);
}

void _readPersonSessionField0(
    ReadContext context, Person value, Object? rawValue) {
  value.age = _readPersonAge(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.age);
}

void _readPersonSessionField1(
    ReadContext context, Person value, Object? rawValue) {
  value.name = _readPersonName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readPersonSessionField2(
    ReadContext context, Person value, Object? rawValue) {
  value.tags = _readPersonTags(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.tags);
}

void _readPersonSessionField3(
    ReadContext context, Person value, Object? rawValue) {
  value.scores = _readPersonScores(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.scores);
}

void _readPersonSessionField4(
    ReadContext context, Person value, Object? rawValue) {
  value.favoriteColor = _readPersonFavoriteColor(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.favoriteColor);
}

final GeneratedStructRegistration<Person> _personForyRegistration =
    GeneratedStructRegistration<Person>(
  sessionWritersBySlot: <_PersonSessionWriter>[
    _writePersonSessionField0,
    _writePersonSessionField1,
    _writePersonSessionField2,
    _writePersonSessionField3,
    _writePersonSessionField4,
  ],
  compatibleFactory: Person.new,
  compatibleReadersBySlot: <_PersonSessionReader>[
    _readPersonSessionField0,
    _readPersonSessionField1,
    _readPersonSessionField2,
    _readPersonSessionField3,
    _readPersonSessionField4,
  ],
  type: Person,
  serializerFactory: _PersonForySerializer.new,
  evolving: true,
  fields: _personForyFieldMetadata,
);

final class _PersonForySerializer extends Serializer<Person> {
  List<GeneratedStructField>? _generatedFields;

  _PersonForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _personForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _personForyRegistration,
    );
  }

  @override
  void write(WriteContext context, Person value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _writeRuntimeFields(context);
      final cursor0 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor0.writeVarInt32(value.age.value);
      cursor0.finish();
      context.writeString(value.name);
      writeGeneratedStructRuntimeValue(context, fields[2], value.tags);
      writeGeneratedStructRuntimeValue(context, fields[3], value.scores);
      final cursor4 = GeneratedWriteCursor.reserve(buffer, 5);
      cursor4.writeVarUint32(value.favoriteColor.index);
      cursor4.finish();
      return;
    }
    final writers = _personForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  Person read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = Person();
    context.reference(value);
    if (session == null) {
      final buffer = context.buffer;
      final fields = _readRuntimeFields(context);
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
    if (session.containsSlot(0)) {
      final rawPerson0 = session.valueForSlot(0);
      value.age = _readPersonAge(
          rawPerson0 is DeferredReadRef
              ? context.getReadRef(rawPerson0.id)
              : rawPerson0,
          value.age);
    }
    if (session.containsSlot(1)) {
      final rawPerson1 = session.valueForSlot(1);
      value.name = _readPersonName(
          rawPerson1 is DeferredReadRef
              ? context.getReadRef(rawPerson1.id)
              : rawPerson1,
          value.name);
    }
    if (session.containsSlot(2)) {
      final rawPerson2 = session.valueForSlot(2);
      value.tags = _readPersonTags(
          rawPerson2 is DeferredReadRef
              ? context.getReadRef(rawPerson2.id)
              : rawPerson2,
          value.tags);
    }
    if (session.containsSlot(3)) {
      final rawPerson3 = session.valueForSlot(3);
      value.scores = _readPersonScores(
          rawPerson3 is DeferredReadRef
              ? context.getReadRef(rawPerson3.id)
              : rawPerson3,
          value.scores);
    }
    if (session.containsSlot(4)) {
      final rawPerson4 = session.valueForSlot(4);
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

const List<GeneratedFieldMetadata> _refNodeForyFieldMetadata =
    <GeneratedFieldMetadata>[
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
    name: 'self',
    identifier: 'self',
    id: null,
    shape: GeneratedTypeShape(
      type: RefNode,
      typeId: 28,
      nullable: true,
      ref: true,
      dynamic: null,
      arguments: <GeneratedTypeShape>[],
    ),
  ),
];

typedef _RefNodeSessionWriter = GeneratedStructFieldWriter<RefNode>;
typedef _RefNodeSessionReader = GeneratedStructFieldReader<RefNode>;

void _writeRefNodeSessionField0(
    WriteContext context, GeneratedStructField field, RefNode value) {
  writeGeneratedStructRuntimeValue(context, field, value.name);
}

void _writeRefNodeSessionField1(
    WriteContext context, GeneratedStructField field, RefNode value) {
  writeGeneratedStructRuntimeValue(context, field, value.self);
}

void _readRefNodeSessionField0(
    ReadContext context, RefNode value, Object? rawValue) {
  value.name = _readRefNodeName(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.name);
}

void _readRefNodeSessionField1(
    ReadContext context, RefNode value, Object? rawValue) {
  value.self = _readRefNodeSelf(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.self);
}

final GeneratedStructRegistration<RefNode> _refNodeForyRegistration =
    GeneratedStructRegistration<RefNode>(
  sessionWritersBySlot: <_RefNodeSessionWriter>[
    _writeRefNodeSessionField0,
    _writeRefNodeSessionField1,
  ],
  compatibleFactory: RefNode.new,
  compatibleReadersBySlot: <_RefNodeSessionReader>[
    _readRefNodeSessionField0,
    _readRefNodeSessionField1,
  ],
  type: RefNode,
  serializerFactory: _RefNodeForySerializer.new,
  evolving: true,
  fields: _refNodeForyFieldMetadata,
);

final class _RefNodeForySerializer extends Serializer<RefNode> {
  List<GeneratedStructField>? _generatedFields;

  _RefNodeForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refNodeForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _refNodeForyRegistration,
    );
  }

  @override
  void write(WriteContext context, RefNode value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      final fields = _writeRuntimeFields(context);
      context.writeString(value.name);
      writeGeneratedStructRuntimeValue(context, fields[1], value.self);
      return;
    }
    final writers = _refNodeForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  RefNode read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = RefNode();
    context.reference(value);
    if (session == null) {
      final fields = _readRuntimeFields(context);
      value.name = context.readString();
      value.self = _readRefNodeSelf(
          readGeneratedStructRuntimeValue(context, fields[1], value.self),
          value.self);
      return value;
    }
    if (session.containsSlot(0)) {
      final rawRefNode0 = session.valueForSlot(0);
      value.name = _readRefNodeName(
          rawRefNode0 is DeferredReadRef
              ? context.getReadRef(rawRefNode0.id)
              : rawRefNode0,
          value.name);
    }
    if (session.containsSlot(1)) {
      final rawRefNode1 = session.valueForSlot(1);
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

const List<GeneratedFieldMetadata> _evolvingPayloadForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'value',
    identifier: 'value',
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
];

typedef _EvolvingPayloadSessionWriter
    = GeneratedStructFieldWriter<EvolvingPayload>;
typedef _EvolvingPayloadSessionReader
    = GeneratedStructFieldReader<EvolvingPayload>;

void _writeEvolvingPayloadSessionField0(
    WriteContext context, GeneratedStructField field, EvolvingPayload value) {
  writeGeneratedStructRuntimeValue(context, field, value.value);
}

void _readEvolvingPayloadSessionField0(
    ReadContext context, EvolvingPayload value, Object? rawValue) {
  value.value = _readEvolvingPayloadValue(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.value);
}

final GeneratedStructRegistration<EvolvingPayload>
    _evolvingPayloadForyRegistration =
    GeneratedStructRegistration<EvolvingPayload>(
  sessionWritersBySlot: <_EvolvingPayloadSessionWriter>[
    _writeEvolvingPayloadSessionField0,
  ],
  compatibleFactory: EvolvingPayload.new,
  compatibleReadersBySlot: <_EvolvingPayloadSessionReader>[
    _readEvolvingPayloadSessionField0,
  ],
  type: EvolvingPayload,
  serializerFactory: _EvolvingPayloadForySerializer.new,
  evolving: true,
  fields: _evolvingPayloadForyFieldMetadata,
);

final class _EvolvingPayloadForySerializer extends Serializer<EvolvingPayload> {
  List<GeneratedStructField>? _generatedFields;

  _EvolvingPayloadForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _evolvingPayloadForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _evolvingPayloadForyRegistration,
    );
  }

  @override
  void write(WriteContext context, EvolvingPayload value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.value);
      return;
    }
    final writers = _evolvingPayloadForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  EvolvingPayload read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = EvolvingPayload();
    context.reference(value);
    if (session == null) {
      value.value = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawEvolvingPayload0 = session.valueForSlot(0);
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

const List<GeneratedFieldMetadata> _fixedPayloadForyFieldMetadata =
    <GeneratedFieldMetadata>[
  GeneratedFieldMetadata(
    name: 'value',
    identifier: 'value',
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
];

typedef _FixedPayloadSessionWriter = GeneratedStructFieldWriter<FixedPayload>;
typedef _FixedPayloadSessionReader = GeneratedStructFieldReader<FixedPayload>;

void _writeFixedPayloadSessionField0(
    WriteContext context, GeneratedStructField field, FixedPayload value) {
  writeGeneratedStructRuntimeValue(context, field, value.value);
}

void _readFixedPayloadSessionField0(
    ReadContext context, FixedPayload value, Object? rawValue) {
  value.value = _readFixedPayloadValue(
      rawValue is DeferredReadRef ? context.getReadRef(rawValue.id) : rawValue,
      value.value);
}

final GeneratedStructRegistration<FixedPayload> _fixedPayloadForyRegistration =
    GeneratedStructRegistration<FixedPayload>(
  sessionWritersBySlot: <_FixedPayloadSessionWriter>[
    _writeFixedPayloadSessionField0,
  ],
  compatibleFactory: FixedPayload.new,
  compatibleReadersBySlot: <_FixedPayloadSessionReader>[
    _readFixedPayloadSessionField0,
  ],
  type: FixedPayload,
  serializerFactory: _FixedPayloadForySerializer.new,
  evolving: false,
  fields: _fixedPayloadForyFieldMetadata,
);

final class _FixedPayloadForySerializer extends Serializer<FixedPayload> {
  List<GeneratedStructField>? _generatedFields;

  _FixedPayloadForySerializer();

  List<GeneratedStructField> _writeRuntimeFields(WriteContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _fixedPayloadForyRegistration,
    );
  }

  List<GeneratedStructField> _readRuntimeFields(ReadContext context) {
    return _generatedFields ??= buildGeneratedStructRuntimeFields(
      context.typeResolver,
      _fixedPayloadForyRegistration,
    );
  }

  @override
  void write(WriteContext context, FixedPayload value) {
    final session = generatedStructWriteSession(context);
    if (session == null) {
      context.writeString(value.value);
      return;
    }
    final writers = _fixedPayloadForyRegistration.sessionWritersBySlot;
    for (final field in session.orderedFields) {
      writers[field.slot](context, field, value);
    }
  }

  @override
  FixedPayload read(ReadContext context) {
    final session = generatedStructReadSession(context);
    final value = FixedPayload();
    context.reference(value);
    if (session == null) {
      value.value = context.readString();
      return value;
    }
    if (session.containsSlot(0)) {
      final rawFixedPayload0 = session.valueForSlot(0);
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
