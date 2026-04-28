/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import 'dart:io';
import 'dart:typed_data';

import 'package:fory/fory.dart';
import 'package:fory/src/codegen/generated_registry.dart';
import 'package:fory/src/meta/field_info.dart';
import 'package:fory/src/serializer/serialization_field_info.dart';
import 'package:fory/src/serializer/struct_serializer.dart';
import 'package:test/test.dart';

import 'package:idl_dart_tests/generated/addressbook/addressbook.dart'
    as addressbook;
import 'package:idl_dart_tests/generated/auto_id/auto_id.dart' as auto_id;
import 'package:idl_dart_tests/generated/collection/collection.dart'
    as collection;
import 'package:idl_dart_tests/generated/example/example.dart' as example;
import 'package:idl_dart_tests/generated/example_common/example_common.dart'
    as example_common;
import 'package:idl_dart_tests/generated/graph/graph.dart' as graph;
import 'package:idl_dart_tests/generated/optional_types/optional_types.dart'
    as optional_types;
import 'package:idl_dart_tests/generated/root/root.dart' as root;
import 'package:idl_dart_tests/generated/tree/tree.dart' as tree;

Fory _newFory({bool compatible = false}) {
  return Fory(compatible: compatible, checkStructVersion: !compatible);
}

void _registerCommon(Fory fory) {
  addressbook.ForyRegistration.register(fory, addressbook.Person, id: 100);
  addressbook.ForyRegistration.register(
    fory,
    addressbook.Person_PhoneType,
    id: 101,
  );
  addressbook.ForyRegistration.register(
    fory,
    addressbook.Person_PhoneNumber,
    id: 102,
  );
  addressbook.ForyRegistration.register(fory, addressbook.AddressBook, id: 103);
  addressbook.ForyRegistration.register(fory, addressbook.Dog, id: 104);
  addressbook.ForyRegistration.register(fory, addressbook.Cat, id: 105);
  addressbook.ForyRegistration.register(fory, addressbook.Animal, id: 106);

  auto_id.ForyRegistration.register(fory, auto_id.Status);
  auto_id.ForyRegistration.register(fory, auto_id.Envelope_Payload);
  auto_id.ForyRegistration.register(fory, auto_id.Envelope_Detail);
  auto_id.ForyRegistration.register(fory, auto_id.Envelope);
  auto_id.ForyRegistration.register(fory, auto_id.Wrapper);

  collection.ForyRegistration.register(
    fory,
    collection.NumericCollections,
    id: 210,
  );
  collection.ForyRegistration.register(
    fory,
    collection.NumericCollectionUnion,
    id: 211,
  );
  collection.ForyRegistration.register(
    fory,
    collection.NumericCollectionsArray,
    id: 212,
  );
  collection.ForyRegistration.register(
    fory,
    collection.NumericCollectionArrayUnion,
    id: 213,
  );

  optional_types.ForyRegistration.register(
    fory,
    optional_types.AllOptionalTypes,
    id: 120,
  );
  optional_types.ForyRegistration.register(
    fory,
    optional_types.OptionalUnion,
    id: 121,
  );
  optional_types.ForyRegistration.register(
    fory,
    optional_types.OptionalHolder,
    id: 122,
  );
}

void _registerRefs(Fory fory) {
  tree.ForyRegistration.register(fory, tree.TreeNode);
  graph.ForyRegistration.register(fory, graph.Node);
  graph.ForyRegistration.register(fory, graph.Edge);
  graph.ForyRegistration.register(fory, graph.Graph);
  root.ForyRegistration.register(fory, root.MultiHolder, id: 300);
}

void _registerExample(Fory fory) {
  example_common.ForyRegistration.register(fory, example_common.ExampleState);
  example_common.ForyRegistration.register(
    fory,
    example_common.ExampleLeafUnion,
  );
  example_common.ForyRegistration.register(fory, example_common.ExampleLeaf);
  example.ForyRegistration.register(fory, example.ExampleMessageUnion);
  example.ForyRegistration.register(fory, example.ExampleMessage);
}

void _registerExampleCommon(Fory fory) {
  example_common.ForyRegistration.register(fory, example_common.ExampleState);
  example_common.ForyRegistration.register(
    fory,
    example_common.ExampleLeafUnion,
  );
  example_common.ForyRegistration.register(fory, example_common.ExampleLeaf);
}

List<bool> _requestedCompatibleModes() {
  final value = Platform.environment['IDL_COMPATIBLE'];
  if (value == null || value.isEmpty) {
    return <bool>[true, false];
  }
  return <bool>[value.toLowerCase() == 'true'];
}

final class _ExampleSchemaEmpty {}

final class _ExampleSchemaSingleField {
  Object? value;
}

final class _ExampleSchemaEmptySerializer
    extends Serializer<_ExampleSchemaEmpty> {
  const _ExampleSchemaEmptySerializer();

  @override
  void write(WriteContext context, _ExampleSchemaEmpty value) {}

  @override
  _ExampleSchemaEmpty read(ReadContext context) {
    final value = _ExampleSchemaEmpty();
    context.reference(value);
    return value;
  }
}

final class _ExampleSchemaSingleFieldSerializer
    extends Serializer<_ExampleSchemaSingleField> {
  final FieldInfo _field;

  _ExampleSchemaSingleFieldSerializer(this._field);

  SerializationFieldInfo _localField(WriteContext context) {
    return context.typeResolver.serializationFieldInfo(_field, slot: 0);
  }

  SerializationFieldInfo _readField(ReadContext context) {
    return context.typeResolver.serializationFieldInfo(_field, slot: 0);
  }

  @override
  void write(WriteContext context, _ExampleSchemaSingleField value) {
    writeGeneratedStructFieldInfoValue(
      context,
      _localField(context),
      value.value,
    );
  }

  @override
  _ExampleSchemaSingleField read(ReadContext context) {
    final value = _ExampleSchemaSingleField();
    context.reference(value);
    value.value = readGeneratedStructFieldInfoValue(
      context,
      _readField(context),
    );
    return value;
  }
}

List<FieldInfo> _exampleMessageFields() {
  var registration = GeneratedRegistrationCatalog.lookup(
    example.ExampleMessage,
  );
  if (registration == null) {
    final fory = _newFory(compatible: true);
    _registerExample(fory);
    registration = GeneratedRegistrationCatalog.lookup(example.ExampleMessage);
  }
  final fields = registration!.fields
      .where((field) => field.id != null)
      .toList(growable: false);
  fields.sort((left, right) => left.id!.compareTo(right.id!));
  return fields;
}

void _registerExampleSchemaEmpty(Fory fory) {
  GeneratedRegistrationCatalog.remember(
    _ExampleSchemaEmpty,
    GeneratedRegistration(
      kind: GeneratedRegistrationKind.struct,
      serializerFactory: _ExampleSchemaEmptySerializer.new,
      compatibleFactory: _ExampleSchemaEmpty.new,
      compatibleReadersBySlot: const <GeneratedStructCompatibleFieldReader<
          Object>>[],
    ),
  );
  fory.register(_ExampleSchemaEmpty, id: 1500);
}

void _registerExampleSchemaField(Fory fory, FieldInfo field) {
  GeneratedRegistrationCatalog.remember(
    _ExampleSchemaSingleField,
    GeneratedRegistration(
      kind: GeneratedRegistrationKind.struct,
      serializerFactory: () => _ExampleSchemaSingleFieldSerializer(field),
      fields: <FieldInfo>[field],
      compatibleFactory: _ExampleSchemaSingleField.new,
      compatibleReadersBySlot: <GeneratedStructCompatibleFieldReader<Object>>[
        (ReadContext context, Object value, Object? rawValue) {
          (value as _ExampleSchemaSingleField).value =
              resolveGeneratedSlotRawValue(context, rawValue);
        },
      ],
    ),
  );
  fory.register(_ExampleSchemaSingleField, id: 1500);
}

_ExampleSchemaEmpty _decodeExampleSchemaEmpty(Uint8List bytes) {
  final fory = _newFory(compatible: true);
  _registerExampleCommon(fory);
  _registerExampleSchemaEmpty(fory);
  return fory.deserialize<_ExampleSchemaEmpty>(bytes);
}

_ExampleSchemaSingleField _decodeExampleSchemaField(
  Uint8List bytes,
  FieldInfo field,
) {
  final fory = _newFory(compatible: true);
  _registerExampleCommon(fory);
  _registerExampleSchemaField(fory, field);
  return fory.deserialize<_ExampleSchemaSingleField>(bytes);
}

addressbook.AddressBook buildAddressBook() {
  final mobile = addressbook.Person_PhoneNumber()
    ..number = '555-0100'
    ..phoneType = addressbook.Person_PhoneType.phoneTypeMobile;
  final work = addressbook.Person_PhoneNumber()
    ..number = '555-0111'
    ..phoneType = addressbook.Person_PhoneType.phoneTypeWork;

  final pet = addressbook.Animal.cat(
    addressbook.Cat()
      ..name = 'Mimi'
      ..lives = Int32(9),
  );

  final person = addressbook.Person()
    ..name = 'Alice'
    ..id = Int32(123)
    ..email = 'alice@example.com'
    ..tags = <String>['friend', 'colleague']
    ..scores = <String, Int32>{'math': Int32(100), 'science': Int32(98)}
    ..salary = 120000.5
    ..phones = <addressbook.Person_PhoneNumber>[mobile, work]
    ..pet = pet;

  return addressbook.AddressBook()
    ..people = <addressbook.Person>[person]
    ..peopleByName = <String, addressbook.Person>{person.name: person};
}

auto_id.Envelope buildAutoIdEnvelope() {
  final payload = auto_id.Envelope_Payload()..value = Int32(42);
  return auto_id.Envelope()
    ..id = 'env-1'
    ..payload = payload
    ..detail = auto_id.Envelope_Detail.payload(payload)
    ..status = auto_id.Status.ok;
}

collection.NumericCollections buildNumericCollections() {
  return collection.NumericCollections()
    ..int8Values = Int8List.fromList(<int>[1, -2, 3])
    ..int16Values = Int16List.fromList(<int>[100, -200, 300])
    ..int32Values = Int32List.fromList(<int>[1000, -2000, 3000])
    ..int64Values = Int64List.fromList(<int>[10000, -20000, 30000])
    ..uint8Values = Uint8List.fromList(<int>[200, 250])
    ..uint16Values = Uint16List.fromList(<int>[50000, 60000])
    ..uint32Values = Uint32List.fromList(<int>[2000000000, 2100000000])
    ..uint64Values = Uint64List.fromList(<int>[9000000000, 12000000000])
    ..float32Values = Float32List.fromList(<double>[1.5, 2.5])
    ..float64Values = Float64List.fromList(<double>[3.5, 4.5]);
}

collection.NumericCollectionUnion buildNumericCollectionUnion() {
  return collection.NumericCollectionUnion.int32Values(
    Int32List.fromList(<int>[7, 8, 9]),
  );
}

collection.NumericCollectionsArray buildNumericCollectionsArray() {
  return collection.NumericCollectionsArray()
    ..int8Values = Int8List.fromList(<int>[1, -2, 3])
    ..int16Values = Int16List.fromList(<int>[100, -200, 300])
    ..int32Values = Int32List.fromList(<int>[1000, -2000, 3000])
    ..int64Values = Int64List.fromList(<int>[10000, -20000, 30000])
    ..uint8Values = Uint8List.fromList(<int>[200, 250])
    ..uint16Values = Uint16List.fromList(<int>[50000, 60000])
    ..uint32Values = Uint32List.fromList(<int>[2000000000, 2100000000])
    ..uint64Values = Uint64List.fromList(<int>[9000000000, 12000000000])
    ..float32Values = Float32List.fromList(<double>[1.5, 2.5])
    ..float64Values = Float64List.fromList(<double>[3.5, 4.5]);
}

collection.NumericCollectionArrayUnion buildNumericCollectionArrayUnion() {
  return collection.NumericCollectionArrayUnion.uint16Values(
    Uint16List.fromList(<int>[1000, 2000, 3000]),
  );
}

optional_types.OptionalHolder buildOptionalHolder() {
  final allTypes = optional_types.AllOptionalTypes()
    ..boolValue = true
    ..int8Value = Int8(12)
    ..int16Value = Int16(1234)
    ..int32Value = Int32(-123456)
    ..fixedInt32Value = Int32(-123456)
    ..varint32Value = Int32(-12345)
    ..int64Value = -123456789
    ..fixedInt64Value = -123456789
    ..varint64Value = -987654321
    ..taggedInt64Value = 123456789
    ..uint8Value = Uint8(200)
    ..uint16Value = Uint16(60000)
    ..uint32Value = Uint32(1234567890)
    ..fixedUint32Value = Uint32(1234567890)
    ..varUint32Value = Uint32(1234567890)
    ..uint64Value = 9876543210
    ..fixedUint64Value = 9876543210
    ..varUint64Value = 12345678901
    ..taggedUint64Value = 2222222222
    ..float32Value = Float32(2.5)
    ..float64Value = 3.5
    ..stringValue = 'optional'
    ..bytesValue = Uint8List.fromList(<int>[1, 2, 3])
    ..dateValue = const LocalDate(2024, 1, 2)
    ..timestampValue = Timestamp.fromDateTime(DateTime.utc(2024, 1, 2, 3, 4, 5))
    ..int32List = Int32List.fromList(<int>[1, 2, 3])
    ..stringList = <String>['alpha', 'beta']
    ..int64Map = <String, int>{'alpha': 10, 'beta': 20};

  return optional_types.OptionalHolder()
    ..allTypes = allTypes
    ..choice = optional_types.OptionalUnion.note('optional');
}

tree.TreeNode buildTree() {
  final childA = tree.TreeNode()
    ..id = 'child-a'
    ..name = 'child-a'
    ..children = <tree.TreeNode>[];
  final childB = tree.TreeNode()
    ..id = 'child-b'
    ..name = 'child-b'
    ..children = <tree.TreeNode>[];
  childA.parent = childB;
  childB.parent = childA;

  return tree.TreeNode()
    ..id = 'root'
    ..name = 'root'
    ..children = <tree.TreeNode>[childA, childA, childB];
}

graph.Graph buildGraph() {
  final nodeA = graph.Node()..id = 'node-a';
  final nodeB = graph.Node()..id = 'node-b';
  final edge = graph.Edge()
    ..id = 'edge-1'
    ..weight = Float32(1.5)
    ..from = nodeA
    ..to = nodeB;
  nodeA.outEdges = <graph.Edge>[edge];
  nodeA.inEdges = <graph.Edge>[edge];
  nodeB.inEdges = <graph.Edge>[edge];
  nodeB.outEdges = <graph.Edge>[];

  return graph.Graph()
    ..nodes = <graph.Node>[nodeA, nodeB]
    ..edges = <graph.Edge>[edge];
}

root.MultiHolder buildRootHolder() {
  final book = buildAddressBook();
  final rootNode = tree.TreeNode()
    ..id = 'root'
    ..name = 'root'
    ..children = <tree.TreeNode>[];
  return root.MultiHolder()
    ..book = book
    ..root = rootNode
    ..owner = book.people.first;
}

example_common.ExampleLeaf _buildExampleLeaf(String label, int count) {
  return example_common.ExampleLeaf()
    ..label = label
    ..count = Int32(count);
}

Timestamp _buildExampleTimestamp() {
  return Timestamp.fromDateTime(
      DateTime.utc(2024, 2, 29, 12, 34, 56, 789, 123));
}

Duration _buildExampleDuration() {
  return const Duration(seconds: 3723, microseconds: 456789);
}

Decimal _buildExampleDecimal() {
  return Decimal(BigInt.parse('1234567890123456789'), 4);
}

example.ExampleMessage buildExampleMessage() {
  final leafA = _buildExampleLeaf('leaf-a', 7);
  final leafB = _buildExampleLeaf('leaf-b', -3);
  final timestamp = _buildExampleTimestamp();
  final alternateTimestamp =
      Timestamp.fromDateTime(DateTime.utc(2024, 3, 1, 0, 0, 0, 123, 456));
  final duration = _buildExampleDuration();
  final alternateDuration = const Duration(seconds: 1, microseconds: 234567);
  final decimal = _buildExampleDecimal();

  return example.ExampleMessage()
    ..boolValue = true
    ..int8Value = Int8(-12)
    ..int16Value = Int16(1234)
    ..fixedInt32Value = Int32(123456789)
    ..varint32Value = Int32(-1234567)
    ..fixedInt64Value = 1234567890123456789
    ..varint64Value = -1234567890123456789
    ..taggedInt64Value = 1073741824
    ..uint8Value = Uint8(200)
    ..uint16Value = Uint16(60000)
    ..fixedUint32Value = Uint32(2000000000)
    ..varUint32Value = Uint32(2100000000)
    ..fixedUint64Value = 9000000000
    ..varUint64Value = 12000000000
    ..taggedUint64Value = 2222222222
    ..float16Value = Float16(1.5)
    ..bfloat16Value = Bfloat16(-2.75)
    ..float32Value = Float32(3.25)
    ..float64Value = -4.5
    ..stringValue = 'example-string'
    ..bytesValue = Uint8List.fromList(<int>[1, 2, 3, 4])
    ..dateValue = const LocalDate(2024, 2, 29)
    ..timestampValue = timestamp
    ..durationValue = duration
    ..decimalValue = decimal
    ..enumValue = example_common.ExampleState.ready
    ..messageValue = leafA
    ..unionValue = example_common.ExampleLeafUnion.leaf(
      _buildExampleLeaf('leaf-b', -3),
    )
    ..boolList = <bool>[true, false]
    ..int8List = Int8List.fromList(<int>[-12, 7])
    ..int16List = Int16List.fromList(<int>[1234, -2345])
    ..fixedInt32List = Int32List.fromList(<int>[123456789, -123456789])
    ..varint32List = Int32List.fromList(<int>[-1234567, 7654321])
    ..fixedInt64List = Int64List.fromList(<int>[
      1234567890123456789,
      -123456789012345678,
    ])
    ..varint64List = Int64List.fromList(
      <int>[-1234567890123456789, 123456789012345678],
    )
    ..taggedInt64List = Int64List.fromList(<int>[1073741824, -1073741824])
    ..uint8List = Uint8List.fromList(<int>[200, 42])
    ..uint16List = Uint16List.fromList(<int>[60000, 12345])
    ..fixedUint32List = Uint32List.fromList(<int>[2000000000, 1234567890])
    ..varUint32List = Uint32List.fromList(<int>[2100000000, 1234567890])
    ..fixedUint64List = Uint64List.fromList(<int>[9000000000, 4000000000])
    ..varUint64List = Uint64List.fromList(<int>[12000000000, 5000000000])
    ..taggedUint64List = Uint64List.fromList(<int>[2222222222, 3333333333])
    ..float16List = Float16List.fromList(<Float16>[Float16(1.5), Float16(-0.5)])
    ..bfloat16List = Bfloat16List.fromList(<Bfloat16>[
      Bfloat16(-2.75),
      Bfloat16(2.25),
    ])
    ..maybeFloat16List = <Float16?>[Float16(1.5), null, Float16(-0.5)]
    ..maybeBfloat16List = <Bfloat16?>[null, Bfloat16(2.25), Bfloat16(-1.0)]
    ..float32List = Float32List.fromList(<double>[3.25, -0.5])
    ..float64List = Float64List.fromList(<double>[-4.5, 6.75])
    ..stringList = <String>['example-string', 'secondary']
    ..bytesList = <Uint8List>[
      Uint8List.fromList(<int>[1, 2, 3, 4]),
      Uint8List.fromList(<int>[5, 6]),
    ]
    ..dateList = <LocalDate>[
      const LocalDate(2024, 2, 29),
      const LocalDate(2024, 3, 1),
    ]
    ..timestampList = <Timestamp>[timestamp, alternateTimestamp]
    ..durationList = <Duration>[duration, alternateDuration]
    ..decimalList = <Decimal>[decimal, Decimal(BigInt.from(-5), 1)]
    ..enumList = <example_common.ExampleState>[
      example_common.ExampleState.ready,
      example_common.ExampleState.failed,
    ]
    ..messageList = <example_common.ExampleLeaf>[leafA, leafB]
    ..unionList = <example_common.ExampleLeafUnion>[
      example_common.ExampleLeafUnion.leaf(_buildExampleLeaf('leaf-a', 7)),
      example_common.ExampleLeafUnion.leaf(_buildExampleLeaf('leaf-b', -3)),
    ]
    ..stringValuesByBool = <bool, String>{
      true: 'true-value',
      false: 'false-value',
    }
    ..stringValuesByInt8 = <Int8, String>{Int8(-12): 'minus-twelve'}
    ..stringValuesByInt16 = <Int16, String>{Int16(1234): 'twelve-thirty-four'}
    ..stringValuesByFixedInt32 = <Int32, String>{
      Int32(123456789): 'fixed-int32',
    }
    ..stringValuesByVarint32 = <Int32, String>{Int32(-1234567): 'varint32'}
    ..stringValuesByFixedInt64 = <int, String>{
      1234567890123456789: 'fixed-int64',
    }
    ..stringValuesByVarint64 = <int, String>{-1234567890123456789: 'varint64'}
    ..stringValuesByTaggedInt64 = <int, String>{1073741824: 'tagged-int64'}
    ..stringValuesByUint8 = <Uint8, String>{Uint8(200): 'uint8'}
    ..stringValuesByUint16 = <Uint16, String>{Uint16(60000): 'uint16'}
    ..stringValuesByFixedUint32 = <Uint32, String>{
      Uint32(2000000000): 'fixed-uint32',
    }
    ..stringValuesByVarUint32 = <Uint32, String>{
      Uint32(2100000000): 'var-uint32',
    }
    ..stringValuesByFixedUint64 = <int, String>{9000000000: 'fixed-uint64'}
    ..stringValuesByVarUint64 = <int, String>{12000000000: 'var-uint64'}
    ..stringValuesByTaggedUint64 = <int, String>{2222222222: 'tagged-uint64'}
    ..stringValuesByString = <String, String>{'example-string': 'string'}
    ..stringValuesByTimestamp = <Timestamp, String>{timestamp: 'timestamp'}
    ..stringValuesByDuration = <Duration, String>{duration: 'duration'}
    ..stringValuesByEnum = <example_common.ExampleState, String>{
      example_common.ExampleState.ready: 'ready',
    }
    ..float16ValuesByName = <String, Float16>{'primary': Float16(1.5)}
    ..maybeFloat16ValuesByName = <String, Float16?>{
      'primary': Float16(1.5),
      'missing': null,
    }
    ..bfloat16ValuesByName = <String, Bfloat16>{'primary': Bfloat16(-2.75)}
    ..maybeBfloat16ValuesByName = <String, Bfloat16?>{
      'missing': null,
      'secondary': Bfloat16(2.25),
    }
    ..bytesValuesByName = <String, Uint8List>{
      'payload': Uint8List.fromList(<int>[1, 2, 3, 4]),
    }
    ..dateValuesByName = <String, LocalDate>{
      'leap-day': const LocalDate(2024, 2, 29),
    }
    ..decimalValuesByName = <String, Decimal>{'amount': decimal}
    ..messageValuesByName = <String, example_common.ExampleLeaf>{
      'leaf-a': leafA,
      'leaf-b': leafB,
    }
    ..unionValuesByName = <String, example_common.ExampleLeafUnion>{
      'leaf-b': example_common.ExampleLeafUnion.leaf(
        _buildExampleLeaf('leaf-b', -3),
      ),
    };
}

example.ExampleMessageUnion buildExampleMessageUnion() {
  return example.ExampleMessageUnion.unionValue(
    example_common.ExampleLeafUnion.leaf(_buildExampleLeaf('leaf-b', -3)),
  );
}

T _roundTrip<T>(Fory fory, T value, {bool trackRef = false}) {
  final bytes = fory.serialize(value, trackRef: trackRef);
  return fory.deserialize<T>(bytes);
}

void _roundTripFile<T>(
  String? env,
  Fory fory,
  T value,
  void Function(T actual) check, {
  bool trackRef = false,
}) {
  if (env == null || env.isEmpty) {
    return;
  }
  final file = File(env);
  if (!file.existsSync() || file.lengthSync() == 0) {
    file.createSync(recursive: true);
    file.writeAsBytesSync(
      fory.serialize(value, trackRef: trackRef),
      flush: true,
    );
  }
  final bytes = file.readAsBytesSync();
  final actual = fory.deserialize<T>(Uint8List.fromList(bytes));
  check(actual);
  file.writeAsBytesSync(
    fory.serialize(actual, trackRef: trackRef),
    flush: true,
  );
}

void _expectMapEquals<K, V>(Map<K, V> actual, Map<K, V> expected) {
  expect(actual.length, expected.length);
  for (final entry in expected.entries) {
    expect(actual[entry.key], equals(entry.value));
  }
}

void _expectBytesEquals(Uint8List actual, Uint8List expected) {
  expect(actual, orderedEquals(expected));
}

void _expectOrderedIterableEquals<T>(Iterable<T> actual, Iterable<T> expected) {
  expect(actual, orderedEquals(expected));
}

void _expectUntypedIterableEquals(Iterable actual, Iterable expected) {
  expect(actual.toList(growable: false),
      orderedEquals(expected.toList(growable: false)));
}

void _expectIterableWith<T>(
  Iterable<T> actual,
  Iterable<T> expected,
  void Function(T actual, T expected) check,
) {
  final actualValues = actual.toList(growable: false);
  final expectedValues = expected.toList(growable: false);
  expect(actualValues.length, equals(expectedValues.length));
  for (var index = 0; index < expectedValues.length; index += 1) {
    check(actualValues[index], expectedValues[index]);
  }
}

void _expectUntypedIterableWith(
  Iterable actual,
  Iterable expected,
  void Function(Object? actual, Object? expected) check,
) {
  final actualValues = actual.toList(growable: false);
  final expectedValues = expected.toList(growable: false);
  expect(actualValues.length, equals(expectedValues.length));
  for (var index = 0; index < expectedValues.length; index += 1) {
    check(actualValues[index], expectedValues[index]);
  }
}

void _expectMapValuesEqual<K, V>(
  Map<K, V> actual,
  Map<K, V> expected, {
  void Function(V actual, V expected)? checkValue,
}) {
  expect(actual.length, equals(expected.length));
  for (final entry in expected.entries) {
    expect(actual.containsKey(entry.key), isTrue);
    final actualValue = actual[entry.key];
    if (checkValue == null) {
      expect(actualValue, equals(entry.value));
    } else {
      checkValue(actualValue as V, entry.value);
    }
  }
}

void _expectUntypedMapEquals(Map actual, Map expected) {
  expect(actual.length, expected.length);
  for (final entry in expected.entries) {
    expect(actual.containsKey(entry.key), isTrue);
    expect(actual[entry.key], equals(entry.value));
  }
}

void _expectUntypedMapValuesEqual(
  Map actual,
  Map expected, {
  void Function(Object? actual, Object? expected)? checkValue,
}) {
  expect(actual.length, expected.length);
  for (final entry in expected.entries) {
    expect(actual.containsKey(entry.key), isTrue);
    final actualValue = actual[entry.key];
    if (checkValue == null) {
      expect(actualValue, equals(entry.value));
    } else {
      checkValue(actualValue, entry.value);
    }
  }
}

void _expectPhoneNumberEquals(
  addressbook.Person_PhoneNumber actual,
  addressbook.Person_PhoneNumber expected,
) {
  expect(actual.number, equals(expected.number));
  expect(actual.phoneType, equals(expected.phoneType));
}

void _expectAnimalEquals(
  addressbook.Animal actual,
  addressbook.Animal expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case addressbook.AnimalCase.dog:
      expect(actual.dogValue.name, equals(expected.dogValue.name));
      expect(actual.dogValue.barkVolume, equals(expected.dogValue.barkVolume));
    case addressbook.AnimalCase.cat:
      expect(actual.catValue.name, equals(expected.catValue.name));
      expect(actual.catValue.lives, equals(expected.catValue.lives));
  }
}

void _expectPersonEquals(
  addressbook.Person actual,
  addressbook.Person expected,
) {
  expect(actual.name, equals(expected.name));
  expect(actual.id, equals(expected.id));
  expect(actual.email, equals(expected.email));
  expect(actual.tags, orderedEquals(expected.tags));
  _expectMapEquals(actual.scores, expected.scores);
  expect(actual.salary, equals(expected.salary));
  expect(actual.phones.length, expected.phones.length);
  for (var index = 0; index < expected.phones.length; index += 1) {
    _expectPhoneNumberEquals(actual.phones[index], expected.phones[index]);
  }
  _expectAnimalEquals(actual.pet, expected.pet);
}

void _expectAddressBookEquals(
  addressbook.AddressBook actual,
  addressbook.AddressBook expected,
) {
  expect(actual.people.length, expected.people.length);
  for (var index = 0; index < expected.people.length; index += 1) {
    _expectPersonEquals(actual.people[index], expected.people[index]);
  }
  expect(actual.peopleByName.length, expected.peopleByName.length);
  for (final entry in expected.peopleByName.entries) {
    final actualPerson = actual.peopleByName[entry.key];
    expect(actualPerson, isNotNull);
    _expectPersonEquals(actualPerson!, entry.value);
  }
}

void _expectEnvelopeEquals(auto_id.Envelope actual, auto_id.Envelope expected) {
  expect(actual.id, equals(expected.id));
  expect(actual.status, equals(expected.status));
  expect(actual.payload, isNotNull);
  expect(expected.payload, isNotNull);
  expect(actual.payload!.value, equals(expected.payload!.value));
  expect(actual.detail.caseValue, equals(expected.detail.caseValue));
  switch (expected.detail.caseValue) {
    case auto_id.Envelope_DetailCase.payload:
      expect(
        actual.detail.payloadValue.value,
        equals(expected.detail.payloadValue.value),
      );
    case auto_id.Envelope_DetailCase.note:
      expect(actual.detail.noteValue, equals(expected.detail.noteValue));
  }
}

void _expectWrapperEquals(auto_id.Wrapper actual, auto_id.Wrapper expected) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case auto_id.WrapperCase.envelope:
      _expectEnvelopeEquals(actual.envelopeValue, expected.envelopeValue);
    case auto_id.WrapperCase.raw:
      expect(actual.rawValue, equals(expected.rawValue));
  }
}

void _expectNumericCollectionsEquals(
  collection.NumericCollections actual,
  collection.NumericCollections expected,
) {
  expect(actual.int8Values, orderedEquals(expected.int8Values));
  expect(actual.int16Values, orderedEquals(expected.int16Values));
  expect(actual.int32Values, orderedEquals(expected.int32Values));
  expect(actual.int64Values, orderedEquals(expected.int64Values));
  expect(actual.uint8Values, orderedEquals(expected.uint8Values));
  expect(actual.uint16Values, orderedEquals(expected.uint16Values));
  expect(actual.uint32Values, orderedEquals(expected.uint32Values));
  expect(actual.uint64Values, orderedEquals(expected.uint64Values));
  expect(actual.float32Values, orderedEquals(expected.float32Values));
  expect(actual.float64Values, orderedEquals(expected.float64Values));
}

void _expectNumericCollectionsArrayEquals(
  collection.NumericCollectionsArray actual,
  collection.NumericCollectionsArray expected,
) {
  expect(actual.int8Values, orderedEquals(expected.int8Values));
  expect(actual.int16Values, orderedEquals(expected.int16Values));
  expect(actual.int32Values, orderedEquals(expected.int32Values));
  expect(actual.int64Values, orderedEquals(expected.int64Values));
  expect(actual.uint8Values, orderedEquals(expected.uint8Values));
  expect(actual.uint16Values, orderedEquals(expected.uint16Values));
  expect(actual.uint32Values, orderedEquals(expected.uint32Values));
  expect(actual.uint64Values, orderedEquals(expected.uint64Values));
  expect(actual.float32Values, orderedEquals(expected.float32Values));
  expect(actual.float64Values, orderedEquals(expected.float64Values));
}

void _expectNumericCollectionUnionEquals(
  collection.NumericCollectionUnion actual,
  collection.NumericCollectionUnion expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case collection.NumericCollectionUnionCase.int8Values:
      expect(actual.int8ValuesValue, orderedEquals(expected.int8ValuesValue));
    case collection.NumericCollectionUnionCase.int16Values:
      expect(actual.int16ValuesValue, orderedEquals(expected.int16ValuesValue));
    case collection.NumericCollectionUnionCase.int32Values:
      expect(actual.int32ValuesValue, orderedEquals(expected.int32ValuesValue));
    case collection.NumericCollectionUnionCase.int64Values:
      expect(actual.int64ValuesValue, orderedEquals(expected.int64ValuesValue));
    case collection.NumericCollectionUnionCase.uint8Values:
      expect(actual.uint8ValuesValue, orderedEquals(expected.uint8ValuesValue));
    case collection.NumericCollectionUnionCase.uint16Values:
      expect(
        actual.uint16ValuesValue,
        orderedEquals(expected.uint16ValuesValue),
      );
    case collection.NumericCollectionUnionCase.uint32Values:
      expect(
        actual.uint32ValuesValue,
        orderedEquals(expected.uint32ValuesValue),
      );
    case collection.NumericCollectionUnionCase.uint64Values:
      expect(
        actual.uint64ValuesValue,
        orderedEquals(expected.uint64ValuesValue),
      );
    case collection.NumericCollectionUnionCase.float32Values:
      expect(
        actual.float32ValuesValue,
        orderedEquals(expected.float32ValuesValue),
      );
    case collection.NumericCollectionUnionCase.float64Values:
      expect(
        actual.float64ValuesValue,
        orderedEquals(expected.float64ValuesValue),
      );
  }
}

void _expectNumericCollectionArrayUnionEquals(
  collection.NumericCollectionArrayUnion actual,
  collection.NumericCollectionArrayUnion expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case collection.NumericCollectionArrayUnionCase.int8Values:
      expect(actual.int8ValuesValue, orderedEquals(expected.int8ValuesValue));
    case collection.NumericCollectionArrayUnionCase.int16Values:
      expect(actual.int16ValuesValue, orderedEquals(expected.int16ValuesValue));
    case collection.NumericCollectionArrayUnionCase.int32Values:
      expect(actual.int32ValuesValue, orderedEquals(expected.int32ValuesValue));
    case collection.NumericCollectionArrayUnionCase.int64Values:
      expect(actual.int64ValuesValue, orderedEquals(expected.int64ValuesValue));
    case collection.NumericCollectionArrayUnionCase.uint8Values:
      expect(actual.uint8ValuesValue, orderedEquals(expected.uint8ValuesValue));
    case collection.NumericCollectionArrayUnionCase.uint16Values:
      expect(
        actual.uint16ValuesValue,
        orderedEquals(expected.uint16ValuesValue),
      );
    case collection.NumericCollectionArrayUnionCase.uint32Values:
      expect(
        actual.uint32ValuesValue,
        orderedEquals(expected.uint32ValuesValue),
      );
    case collection.NumericCollectionArrayUnionCase.uint64Values:
      expect(
        actual.uint64ValuesValue,
        orderedEquals(expected.uint64ValuesValue),
      );
    case collection.NumericCollectionArrayUnionCase.float32Values:
      expect(
        actual.float32ValuesValue,
        orderedEquals(expected.float32ValuesValue),
      );
    case collection.NumericCollectionArrayUnionCase.float64Values:
      expect(
        actual.float64ValuesValue,
        orderedEquals(expected.float64ValuesValue),
      );
  }
}

void _expectOptionalTypesEquals(
  optional_types.AllOptionalTypes actual,
  optional_types.AllOptionalTypes expected,
) {
  expect(actual.boolValue, equals(expected.boolValue));
  expect(actual.int8Value, equals(expected.int8Value));
  expect(actual.int16Value, equals(expected.int16Value));
  expect(actual.int32Value, equals(expected.int32Value));
  expect(actual.fixedInt32Value, equals(expected.fixedInt32Value));
  expect(actual.varint32Value, equals(expected.varint32Value));
  expect(actual.int64Value, equals(expected.int64Value));
  expect(actual.fixedInt64Value, equals(expected.fixedInt64Value));
  expect(actual.varint64Value, equals(expected.varint64Value));
  expect(actual.taggedInt64Value, equals(expected.taggedInt64Value));
  expect(actual.uint8Value, equals(expected.uint8Value));
  expect(actual.uint16Value, equals(expected.uint16Value));
  expect(actual.uint32Value, equals(expected.uint32Value));
  expect(actual.fixedUint32Value, equals(expected.fixedUint32Value));
  expect(actual.varUint32Value, equals(expected.varUint32Value));
  expect(actual.uint64Value, equals(expected.uint64Value));
  expect(actual.fixedUint64Value, equals(expected.fixedUint64Value));
  expect(actual.varUint64Value, equals(expected.varUint64Value));
  expect(actual.taggedUint64Value, equals(expected.taggedUint64Value));
  expect(actual.float32Value, equals(expected.float32Value));
  expect(actual.float64Value, equals(expected.float64Value));
  expect(actual.stringValue, equals(expected.stringValue));
  if (expected.bytesValue == null) {
    expect(actual.bytesValue, isNull);
  } else {
    expect(actual.bytesValue, isNotNull);
    expect(actual.bytesValue!, orderedEquals(expected.bytesValue!));
  }
  expect(actual.dateValue, equals(expected.dateValue));
  expect(actual.timestampValue, equals(expected.timestampValue));
  if (expected.int32List == null) {
    expect(actual.int32List, isNull);
  } else {
    expect(actual.int32List, isNotNull);
    expect(actual.int32List!, orderedEquals(expected.int32List!));
  }
  if (expected.stringList == null) {
    expect(actual.stringList, isNull);
  } else {
    expect(actual.stringList, isNotNull);
    expect(actual.stringList!, orderedEquals(expected.stringList!));
  }
  if (expected.int64Map == null) {
    expect(actual.int64Map, isNull);
  } else {
    expect(actual.int64Map, isNotNull);
    _expectMapEquals(actual.int64Map!, expected.int64Map!);
  }
}

void _expectOptionalUnionEquals(
  optional_types.OptionalUnion actual,
  optional_types.OptionalUnion expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case optional_types.OptionalUnionCase.note:
      expect(actual.noteValue, equals(expected.noteValue));
    case optional_types.OptionalUnionCase.code:
      expect(actual.codeValue, equals(expected.codeValue));
    case optional_types.OptionalUnionCase.payload:
      _expectOptionalTypesEquals(actual.payloadValue, expected.payloadValue);
  }
}

void _expectOptionalHolderEquals(
  optional_types.OptionalHolder actual,
  optional_types.OptionalHolder expected,
) {
  if (expected.allTypes == null) {
    expect(actual.allTypes, isNull);
  } else {
    expect(actual.allTypes, isNotNull);
    _expectOptionalTypesEquals(actual.allTypes!, expected.allTypes!);
  }
  if (expected.choice == null) {
    expect(actual.choice, isNull);
  } else {
    expect(actual.choice, isNotNull);
    _expectOptionalUnionEquals(actual.choice!, expected.choice!);
  }
}

void _expectTree(tree.TreeNode actual) {
  expect(actual.children.length, equals(3));
  expect(identical(actual.children[0], actual.children[1]), isTrue);
  expect(identical(actual.children[0], actual.children[2]), isFalse);
  expect(identical(actual.children[0].parent, actual.children[2]), isTrue);
  expect(identical(actual.children[2].parent, actual.children[0]), isTrue);
}

void _expectGraph(graph.Graph actual) {
  expect(actual.nodes.length, equals(2));
  expect(actual.edges.length, equals(1));
  final nodeA = actual.nodes[0];
  final nodeB = actual.nodes[1];
  final edge = actual.edges[0];
  expect(identical(nodeA.outEdges[0], nodeA.inEdges[0]), isTrue);
  expect(identical(edge, nodeA.outEdges[0]), isTrue);
  expect(identical(edge.from, nodeA), isTrue);
  expect(identical(edge.to, nodeB), isTrue);
}

void _expectRootHolderEquals(
  root.MultiHolder actual,
  root.MultiHolder expected,
) {
  expect(actual.book, isNotNull);
  expect(expected.book, isNotNull);
  _expectAddressBookEquals(actual.book!, expected.book!);
  expect(actual.root, isNotNull);
  expect(expected.root, isNotNull);
  expect(actual.root!.id, equals(expected.root!.id));
  expect(actual.root!.name, equals(expected.root!.name));
  expect(actual.root!.children, isEmpty);
  expect(actual.root!.parent, isNull);
  expect(actual.owner, isNotNull);
  expect(expected.owner, isNotNull);
  _expectPersonEquals(actual.owner!, expected.owner!);
}

void _expectExampleLeafEquals(
  example_common.ExampleLeaf actual,
  example_common.ExampleLeaf expected,
) {
  expect(actual.label, equals(expected.label));
  expect(actual.count, equals(expected.count));
}

void _expectExampleLeafUnionEquals(
  example_common.ExampleLeafUnion actual,
  example_common.ExampleLeafUnion expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case example_common.ExampleLeafUnionCase.note:
      expect(actual.noteValue, equals(expected.noteValue));
    case example_common.ExampleLeafUnionCase.code:
      expect(actual.codeValue, equals(expected.codeValue));
    case example_common.ExampleLeafUnionCase.leaf:
      _expectExampleLeafEquals(actual.leafValue, expected.leafValue);
  }
}

void _expectExampleMessageUnionEquals(
  example.ExampleMessageUnion actual,
  example.ExampleMessageUnion expected,
) {
  expect(actual.caseValue, equals(expected.caseValue));
  switch (expected.caseValue) {
    case example.ExampleMessageUnionCase.unionValue:
      _expectExampleLeafUnionEquals(
        actual.unionValueValue,
        expected.unionValueValue,
      );
    default:
      fail('Unhandled ExampleMessageUnion case ${expected.caseValue}.');
  }
}

void _expectExampleMessageEquals(
  example.ExampleMessage actual,
  example.ExampleMessage expected,
) {
  expect(actual.boolValue, equals(expected.boolValue));
  expect(actual.int8Value, equals(expected.int8Value));
  expect(actual.int16Value, equals(expected.int16Value));
  expect(actual.fixedInt32Value, equals(expected.fixedInt32Value));
  expect(actual.varint32Value, equals(expected.varint32Value));
  expect(actual.fixedInt64Value, equals(expected.fixedInt64Value));
  expect(actual.varint64Value, equals(expected.varint64Value));
  expect(actual.taggedInt64Value, equals(expected.taggedInt64Value));
  expect(actual.uint8Value, equals(expected.uint8Value));
  expect(actual.uint16Value, equals(expected.uint16Value));
  expect(actual.fixedUint32Value, equals(expected.fixedUint32Value));
  expect(actual.varUint32Value, equals(expected.varUint32Value));
  expect(actual.fixedUint64Value, equals(expected.fixedUint64Value));
  expect(actual.varUint64Value, equals(expected.varUint64Value));
  expect(actual.taggedUint64Value, equals(expected.taggedUint64Value));
  expect(actual.float16Value, equals(expected.float16Value));
  expect(actual.bfloat16Value, equals(expected.bfloat16Value));
  expect(actual.float32Value, equals(expected.float32Value));
  expect(actual.float64Value, equals(expected.float64Value));
  expect(actual.stringValue, equals(expected.stringValue));
  _expectBytesEquals(actual.bytesValue, expected.bytesValue);
  expect(actual.dateValue, equals(expected.dateValue));
  expect(actual.timestampValue, equals(expected.timestampValue));
  expect(actual.durationValue, equals(expected.durationValue));
  expect(actual.decimalValue, equals(expected.decimalValue));
  expect(actual.enumValue, equals(expected.enumValue));
  expect(actual.messageValue, isNotNull);
  expect(expected.messageValue, isNotNull);
  _expectExampleLeafEquals(actual.messageValue!, expected.messageValue!);
  _expectExampleLeafUnionEquals(actual.unionValue, expected.unionValue);

  _expectOrderedIterableEquals(actual.boolList, expected.boolList);
  _expectOrderedIterableEquals(actual.int8List, expected.int8List);
  _expectOrderedIterableEquals(actual.int16List, expected.int16List);
  _expectOrderedIterableEquals(actual.fixedInt32List, expected.fixedInt32List);
  _expectOrderedIterableEquals(actual.varint32List, expected.varint32List);
  _expectOrderedIterableEquals(actual.fixedInt64List, expected.fixedInt64List);
  _expectOrderedIterableEquals(actual.varint64List, expected.varint64List);
  _expectOrderedIterableEquals(
    actual.taggedInt64List,
    expected.taggedInt64List,
  );
  _expectOrderedIterableEquals(actual.uint8List, expected.uint8List);
  _expectOrderedIterableEquals(actual.uint16List, expected.uint16List);
  _expectOrderedIterableEquals(
    actual.fixedUint32List,
    expected.fixedUint32List,
  );
  _expectOrderedIterableEquals(actual.varUint32List, expected.varUint32List);
  _expectOrderedIterableEquals(
    actual.fixedUint64List,
    expected.fixedUint64List,
  );
  _expectOrderedIterableEquals(actual.varUint64List, expected.varUint64List);
  _expectOrderedIterableEquals(
    actual.taggedUint64List,
    expected.taggedUint64List,
  );
  _expectOrderedIterableEquals(actual.float16List, expected.float16List);
  _expectOrderedIterableEquals(actual.bfloat16List, expected.bfloat16List);
  _expectOrderedIterableEquals(
    actual.maybeFloat16List,
    expected.maybeFloat16List,
  );
  _expectOrderedIterableEquals(
    actual.maybeBfloat16List,
    expected.maybeBfloat16List,
  );
  _expectOrderedIterableEquals(actual.float32List, expected.float32List);
  _expectOrderedIterableEquals(actual.float64List, expected.float64List);
  _expectOrderedIterableEquals(actual.stringList, expected.stringList);
  _expectIterableWith(actual.bytesList, expected.bytesList, _expectBytesEquals);
  _expectOrderedIterableEquals(actual.dateList, expected.dateList);
  _expectOrderedIterableEquals(actual.timestampList, expected.timestampList);
  _expectOrderedIterableEquals(actual.durationList, expected.durationList);
  _expectOrderedIterableEquals(actual.decimalList, expected.decimalList);
  _expectOrderedIterableEquals(actual.enumList, expected.enumList);
  _expectIterableWith(
    actual.messageList,
    expected.messageList,
    _expectExampleLeafEquals,
  );
  _expectIterableWith(
    actual.unionList,
    expected.unionList,
    _expectExampleLeafUnionEquals,
  );

  _expectMapEquals(actual.stringValuesByBool, expected.stringValuesByBool);
  _expectMapEquals(actual.stringValuesByInt8, expected.stringValuesByInt8);
  _expectMapEquals(actual.stringValuesByInt16, expected.stringValuesByInt16);
  _expectMapEquals(
    actual.stringValuesByFixedInt32,
    expected.stringValuesByFixedInt32,
  );
  _expectMapEquals(
    actual.stringValuesByVarint32,
    expected.stringValuesByVarint32,
  );
  _expectMapEquals(
    actual.stringValuesByFixedInt64,
    expected.stringValuesByFixedInt64,
  );
  _expectMapEquals(
    actual.stringValuesByVarint64,
    expected.stringValuesByVarint64,
  );
  _expectMapEquals(
    actual.stringValuesByTaggedInt64,
    expected.stringValuesByTaggedInt64,
  );
  _expectMapEquals(actual.stringValuesByUint8, expected.stringValuesByUint8);
  _expectMapEquals(actual.stringValuesByUint16, expected.stringValuesByUint16);
  _expectMapEquals(
    actual.stringValuesByFixedUint32,
    expected.stringValuesByFixedUint32,
  );
  _expectMapEquals(
    actual.stringValuesByVarUint32,
    expected.stringValuesByVarUint32,
  );
  _expectMapEquals(
    actual.stringValuesByFixedUint64,
    expected.stringValuesByFixedUint64,
  );
  _expectMapEquals(
    actual.stringValuesByVarUint64,
    expected.stringValuesByVarUint64,
  );
  _expectMapEquals(
    actual.stringValuesByTaggedUint64,
    expected.stringValuesByTaggedUint64,
  );
  _expectMapEquals(actual.stringValuesByString, expected.stringValuesByString);
  _expectMapEquals(
    actual.stringValuesByTimestamp,
    expected.stringValuesByTimestamp,
  );
  _expectMapEquals(
    actual.stringValuesByDuration,
    expected.stringValuesByDuration,
  );
  _expectMapEquals(actual.stringValuesByEnum, expected.stringValuesByEnum);
  _expectMapEquals(actual.float16ValuesByName, expected.float16ValuesByName);
  _expectMapEquals(
    actual.maybeFloat16ValuesByName,
    expected.maybeFloat16ValuesByName,
  );
  _expectMapEquals(actual.bfloat16ValuesByName, expected.bfloat16ValuesByName);
  _expectMapEquals(
    actual.maybeBfloat16ValuesByName,
    expected.maybeBfloat16ValuesByName,
  );
  _expectMapValuesEqual(
    actual.bytesValuesByName,
    expected.bytesValuesByName,
    checkValue: _expectBytesEquals,
  );
  _expectMapEquals(actual.dateValuesByName, expected.dateValuesByName);
  _expectMapEquals(actual.decimalValuesByName, expected.decimalValuesByName);
  _expectMapValuesEqual(
    actual.messageValuesByName,
    expected.messageValuesByName,
    checkValue: _expectExampleLeafEquals,
  );
  _expectMapValuesEqual(
    actual.unionValuesByName,
    expected.unionValuesByName,
    checkValue: _expectExampleLeafUnionEquals,
  );
}

void _expectExampleSchemaFieldValue(
  FieldInfo field,
  Object? actual,
  example.ExampleMessage expected,
) {
  switch (field.id!) {
    case 1:
      expect(actual, equals(expected.boolValue));
      return;
    case 2:
      expect(actual, equals(expected.int8Value));
      return;
    case 3:
      expect(actual, equals(expected.int16Value));
      return;
    case 4:
      expect(actual, equals(expected.fixedInt32Value));
      return;
    case 5:
      expect(actual, equals(expected.varint32Value));
      return;
    case 6:
      expect(actual, equals(expected.fixedInt64Value));
      return;
    case 7:
      expect(actual, equals(expected.varint64Value));
      return;
    case 8:
      expect(actual, equals(expected.taggedInt64Value));
      return;
    case 9:
      expect(actual, equals(expected.uint8Value));
      return;
    case 10:
      expect(actual, equals(expected.uint16Value));
      return;
    case 11:
      expect(actual, equals(expected.fixedUint32Value));
      return;
    case 12:
      expect(actual, equals(expected.varUint32Value));
      return;
    case 13:
      expect(actual, equals(expected.fixedUint64Value));
      return;
    case 14:
      expect(actual, equals(expected.varUint64Value));
      return;
    case 15:
      expect(actual, equals(expected.taggedUint64Value));
      return;
    case 16:
      expect(actual, equals(expected.float16Value));
      return;
    case 17:
      expect(actual, equals(expected.bfloat16Value));
      return;
    case 18:
      expect(actual, equals(expected.float32Value));
      return;
    case 19:
      expect(actual, equals(expected.float64Value));
      return;
    case 20:
      expect(actual, equals(expected.stringValue));
      return;
    case 21:
      _expectBytesEquals(actual as Uint8List, expected.bytesValue);
      return;
    case 22:
      expect(actual, equals(expected.dateValue));
      return;
    case 23:
      expect(actual, equals(expected.timestampValue));
      return;
    case 24:
      expect(actual, equals(expected.durationValue));
      return;
    case 25:
      expect(actual, equals(expected.decimalValue));
      return;
    case 26:
      expect(actual, equals(expected.enumValue));
      return;
    case 27:
      _expectExampleLeafEquals(
        actual as example_common.ExampleLeaf,
        expected.messageValue!,
      );
      return;
    case 28:
      _expectExampleLeafUnionEquals(
        actual as example_common.ExampleLeafUnion,
        expected.unionValue,
      );
      return;
    case 101:
      _expectOrderedIterableEquals(actual as List<bool>, expected.boolList);
      return;
    case 102:
      _expectOrderedIterableEquals(actual as Int8List, expected.int8List);
      return;
    case 103:
      _expectOrderedIterableEquals(actual as Int16List, expected.int16List);
      return;
    case 104:
      _expectOrderedIterableEquals(
        actual as Int32List,
        expected.fixedInt32List,
      );
      return;
    case 105:
      _expectOrderedIterableEquals(actual as Int32List, expected.varint32List);
      return;
    case 106:
      _expectOrderedIterableEquals(
        actual as Int64List,
        expected.fixedInt64List,
      );
      return;
    case 107:
      _expectOrderedIterableEquals(actual as Int64List, expected.varint64List);
      return;
    case 108:
      _expectOrderedIterableEquals(
        actual as Int64List,
        expected.taggedInt64List,
      );
      return;
    case 109:
      _expectOrderedIterableEquals(actual as Uint8List, expected.uint8List);
      return;
    case 110:
      _expectOrderedIterableEquals(actual as Uint16List, expected.uint16List);
      return;
    case 111:
      _expectOrderedIterableEquals(
        actual as Uint32List,
        expected.fixedUint32List,
      );
      return;
    case 112:
      _expectOrderedIterableEquals(
        actual as Uint32List,
        expected.varUint32List,
      );
      return;
    case 113:
      _expectOrderedIterableEquals(
        actual as Uint64List,
        expected.fixedUint64List,
      );
      return;
    case 114:
      _expectOrderedIterableEquals(
        actual as Uint64List,
        expected.varUint64List,
      );
      return;
    case 115:
      _expectOrderedIterableEquals(
        actual as Uint64List,
        expected.taggedUint64List,
      );
      return;
    case 116:
      _expectOrderedIterableEquals(actual as Float16List, expected.float16List);
      return;
    case 117:
      _expectOrderedIterableEquals(
        actual as Bfloat16List,
        expected.bfloat16List,
      );
      return;
    case 118:
      _expectUntypedIterableEquals(
          actual as Iterable, expected.maybeFloat16List);
      return;
    case 119:
      _expectUntypedIterableEquals(
        actual as Iterable,
        expected.maybeBfloat16List,
      );
      return;
    case 120:
      _expectOrderedIterableEquals(actual as Float32List, expected.float32List);
      return;
    case 121:
      _expectOrderedIterableEquals(actual as Float64List, expected.float64List);
      return;
    case 122:
      _expectUntypedIterableEquals(actual as Iterable, expected.stringList);
      return;
    case 123:
      _expectUntypedIterableWith(
        actual as Iterable,
        expected.bytesList,
        (actualValue, expectedValue) => _expectBytesEquals(
          actualValue as Uint8List,
          expectedValue as Uint8List,
        ),
      );
      return;
    case 124:
      _expectUntypedIterableEquals(actual as Iterable, expected.dateList);
      return;
    case 125:
      _expectUntypedIterableEquals(actual as Iterable, expected.timestampList);
      return;
    case 126:
      _expectUntypedIterableEquals(actual as Iterable, expected.durationList);
      return;
    case 127:
      _expectUntypedIterableEquals(actual as Iterable, expected.decimalList);
      return;
    case 128:
      _expectUntypedIterableEquals(actual as Iterable, expected.enumList);
      return;
    case 129:
      _expectUntypedIterableWith(
        actual as Iterable,
        expected.messageList,
        (actualValue, expectedValue) => _expectExampleLeafEquals(
          actualValue as example_common.ExampleLeaf,
          expectedValue as example_common.ExampleLeaf,
        ),
      );
      return;
    case 130:
      _expectUntypedIterableWith(
        actual as Iterable,
        expected.unionList,
        (actualValue, expectedValue) => _expectExampleLeafUnionEquals(
          actualValue as example_common.ExampleLeafUnion,
          expectedValue as example_common.ExampleLeafUnion,
        ),
      );
      return;
    case 201:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByBool);
      return;
    case 202:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByInt8);
      return;
    case 203:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByInt16);
      return;
    case 204:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByFixedInt32);
      return;
    case 205:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByVarint32);
      return;
    case 206:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByFixedInt64);
      return;
    case 207:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByVarint64);
      return;
    case 208:
      _expectUntypedMapEquals(
          actual as Map, expected.stringValuesByTaggedInt64);
      return;
    case 209:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByUint8);
      return;
    case 210:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByUint16);
      return;
    case 211:
      _expectUntypedMapEquals(
          actual as Map, expected.stringValuesByFixedUint32);
      return;
    case 212:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByVarUint32);
      return;
    case 213:
      _expectUntypedMapEquals(
          actual as Map, expected.stringValuesByFixedUint64);
      return;
    case 214:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByVarUint64);
      return;
    case 215:
      _expectUntypedMapEquals(
          actual as Map, expected.stringValuesByTaggedUint64);
      return;
    case 218:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByString);
      return;
    case 219:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByTimestamp);
      return;
    case 220:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByDuration);
      return;
    case 221:
      _expectUntypedMapEquals(actual as Map, expected.stringValuesByEnum);
      return;
    case 222:
      _expectUntypedMapEquals(actual as Map, expected.float16ValuesByName);
      return;
    case 223:
      _expectUntypedMapEquals(actual as Map, expected.maybeFloat16ValuesByName);
      return;
    case 224:
      _expectUntypedMapEquals(actual as Map, expected.bfloat16ValuesByName);
      return;
    case 225:
      _expectUntypedMapEquals(
        actual as Map,
        expected.maybeBfloat16ValuesByName,
      );
      return;
    case 226:
      _expectUntypedMapValuesEqual(
        actual as Map,
        expected.bytesValuesByName,
        checkValue: (actualValue, expectedValue) => _expectBytesEquals(
          actualValue as Uint8List,
          expectedValue as Uint8List,
        ),
      );
      return;
    case 227:
      _expectUntypedMapEquals(actual as Map, expected.dateValuesByName);
      return;
    case 228:
      _expectUntypedMapEquals(actual as Map, expected.decimalValuesByName);
      return;
    case 229:
      _expectUntypedMapValuesEqual(
        actual as Map,
        expected.messageValuesByName,
        checkValue: (actualValue, expectedValue) => _expectExampleLeafEquals(
          actualValue as example_common.ExampleLeaf,
          expectedValue as example_common.ExampleLeaf,
        ),
      );
      return;
    case 230:
      _expectUntypedMapValuesEqual(
        actual as Map,
        expected.unionValuesByName,
        checkValue: (actualValue, expectedValue) =>
            _expectExampleLeafUnionEquals(
          actualValue as example_common.ExampleLeafUnion,
          expectedValue as example_common.ExampleLeafUnion,
        ),
      );
      return;
    default:
      fail('Unhandled example schema field ${field.id} (${field.name}).');
  }
}

void _expectExampleSchemaEvolution(
  Uint8List payload,
  example.ExampleMessage expected,
) {
  _decodeExampleSchemaEmpty(payload);
  for (final field in _exampleMessageFields()) {
    final decoded = _decodeExampleSchemaField(payload, field);
    _expectExampleSchemaFieldValue(field, decoded.value, expected);
  }
}

void main() {
  group('dart schema idl integration', () {
    test(
      'addressbook and auto-id roundtrip in compatible and schema-consistent modes',
      () {
        for (final compatible in <bool>[true, false]) {
          final fory = _newFory(compatible: compatible);
          _registerCommon(fory);

          final book = buildAddressBook();
          final bookRoundTrip = _roundTrip<addressbook.AddressBook>(fory, book);
          _expectAddressBookEquals(bookRoundTrip, book);

          final envelope = buildAutoIdEnvelope();
          final envelopeRoundTrip = _roundTrip<auto_id.Envelope>(
            fory,
            envelope,
          );
          _expectEnvelopeEquals(envelopeRoundTrip, envelope);

          final wrapper = auto_id.Wrapper.envelope(envelope);
          final wrapperRoundTrip = _roundTrip<auto_id.Wrapper>(fory, wrapper);
          _expectWrapperEquals(wrapperRoundTrip, wrapper);
        }
      },
    );

    test('collection and optional payloads roundtrip', () {
      final fory = _newFory(compatible: true);
      _registerCommon(fory);

      final collections = buildNumericCollections();
      _expectNumericCollectionsEquals(
        _roundTrip<collection.NumericCollections>(fory, collections),
        collections,
      );

      final collectionUnion = buildNumericCollectionUnion();
      _expectNumericCollectionUnionEquals(
        _roundTrip<collection.NumericCollectionUnion>(fory, collectionUnion),
        collectionUnion,
      );

      final collectionsArray = buildNumericCollectionsArray();
      _expectNumericCollectionsArrayEquals(
        _roundTrip<collection.NumericCollectionsArray>(fory, collectionsArray),
        collectionsArray,
      );

      final collectionArrayUnion = buildNumericCollectionArrayUnion();
      _expectNumericCollectionArrayUnionEquals(
        _roundTrip<collection.NumericCollectionArrayUnion>(
          fory,
          collectionArrayUnion,
        ),
        collectionArrayUnion,
      );

      final holder = buildOptionalHolder();
      _expectOptionalHolderEquals(
        _roundTrip<optional_types.OptionalHolder>(fory, holder),
        holder,
      );
    });

    test(
      'example schema family roundtrip in compatible and schema-consistent modes',
      () {
        for (final compatible in <bool>[true, false]) {
          final fory = _newFory(compatible: compatible);
          _registerExample(fory);

          final message = buildExampleMessage();
          final messageRoundTrip = _roundTrip<example.ExampleMessage>(
            fory,
            message,
          );
          _expectExampleMessageEquals(messageRoundTrip, message);
          if (compatible) {
            _expectExampleSchemaEvolution(fory.serialize(message), message);
          }

          final union = buildExampleMessageUnion();
          final unionRoundTrip = _roundTrip<example.ExampleMessageUnion>(
            fory,
            union,
          );
          _expectExampleMessageUnionEquals(unionRoundTrip, union);
        }
      },
    );

    test('reference graphs and root helpers roundtrip', () {
      final fory = _newFory(compatible: true);
      _registerCommon(fory);
      _registerRefs(fory);

      final treeValue = buildTree();
      final treeRoundTrip = _roundTrip<tree.TreeNode>(
        fory,
        treeValue,
        trackRef: true,
      );
      _expectTree(treeRoundTrip);

      final graphValue = buildGraph();
      final graphRoundTrip = _roundTrip<graph.Graph>(
        fory,
        graphValue,
        trackRef: true,
      );
      _expectGraph(graphRoundTrip);

      final holder = buildRootHolder();
      final holderBytes = holder.toBytes();
      final holderDecoded = root.MultiHolder.fromBytes(holderBytes);
      _expectRootHolderEquals(holderDecoded, holder);
    });

    test('interop file roundtrip hooks when env vars are set', () {
      for (final compatible in _requestedCompatibleModes()) {
        final fory = _newFory(compatible: compatible);
        _registerCommon(fory);
        _registerRefs(fory);

        _roundTripFile(
          Platform.environment['DATA_FILE'],
          fory,
          buildAddressBook(),
          (actual) => _expectAddressBookEquals(actual, buildAddressBook()),
        );
        _roundTripFile(
          Platform.environment['DATA_FILE_AUTO_ID'],
          fory,
          buildAutoIdEnvelope(),
          (actual) => _expectEnvelopeEquals(actual, buildAutoIdEnvelope()),
        );
        _roundTripFile(
          Platform.environment['DATA_FILE_TREE'],
          fory,
          buildTree(),
          (actual) => _expectTree(actual),
          trackRef: true,
        );
        _roundTripFile(
          Platform.environment['DATA_FILE_GRAPH'],
          fory,
          buildGraph(),
          (actual) => _expectGraph(actual),
          trackRef: true,
        );

        final exampleFory = _newFory(compatible: compatible);
        _registerExample(exampleFory);

        final expectedMessage = buildExampleMessage();
        final exampleMessageFile =
            Platform.environment['DATA_FILE_EXAMPLE_MESSAGE'];
        _roundTripFile(
          exampleMessageFile,
          exampleFory,
          expectedMessage,
          (actual) => _expectExampleMessageEquals(actual, expectedMessage),
        );
        if (compatible &&
            exampleMessageFile != null &&
            exampleMessageFile.isNotEmpty) {
          _expectExampleSchemaEvolution(
            Uint8List.fromList(File(exampleMessageFile).readAsBytesSync()),
            expectedMessage,
          );
        }

        final expectedUnion = buildExampleMessageUnion();
        _roundTripFile(
          Platform.environment['DATA_FILE_EXAMPLE_UNION'],
          exampleFory,
          expectedUnion,
          (actual) => _expectExampleMessageUnionEquals(actual, expectedUnion),
        );
      }
    });
  });
}
