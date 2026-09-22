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

import 'package:fory/fory.dart';
import 'package:test/test.dart';

part 'object_and_compatible_serializer_test.fory.dart';

@ForyStruct()
class SchemaValue {
  int value = 0;
}

@ForyStruct()
class SchemaValueA {
  int value = 0;
  String extraA = '';
}

@ForyStruct()
class SchemaValueB {
  int value = 0;
  String extraB = '';
}

@ForyStruct()
class SharedLeaf {
  SharedLeaf();

  String label = '';
}

@ForyStruct()
class RefPair {
  RefPair();

  @ForyField(ref: true)
  SharedLeaf? first;

  @ForyField(ref: true)
  SharedLeaf? second;
}

@ForyStruct()
class NoRefPair {
  NoRefPair();

  SharedLeaf? first;
  SharedLeaf? second;
}

@ForyStruct()
class CircularNode {
  CircularNode();

  String name = '';

  @ForyField(ref: true)
  CircularNode? next;
}

@ForyStruct()
class ImmutableRefPair {
  const ImmutableRefPair(this.first, this.second);

  @ForyField(ref: true)
  final SharedLeaf? first;

  @ForyField(ref: true)
  final SharedLeaf? second;
}

@ForyStruct()
class CompatibleEnvelopeV1 {
  CompatibleEnvelopeV1();

  @ForyField(id: 1)
  String name = '';

  @ForyField(id: 2)
  int age = 0;

  @ForyField(id: 3, dynamic: true)
  Object? payload;

  @ForyField(id: 4, ref: true)
  SharedLeaf? first;

  @ForyField(id: 5, ref: true)
  SharedLeaf? second;
}

@ForyStruct()
class CompatibleEnvelopeV2 {
  CompatibleEnvelopeV2();

  @ForyField(id: 5, ref: true)
  SharedLeaf? duplicate;

  @ForyField(id: 1)
  String displayName = '';

  @ForyField(id: 6)
  String city = 'unknown';

  @ForyField(id: 4, ref: true)
  SharedLeaf? original;

  @ForyField(id: 3, dynamic: true)
  Object? payload;
}

@ForyStruct()
class DynamicPayloadEnvelopeV1 {
  DynamicPayloadEnvelopeV1();

  @ForyField(id: 1, dynamic: true)
  Object? payload;
}

@ForyStruct()
class StaticPayloadEnvelopeV2 {
  StaticPayloadEnvelopeV2();

  @ForyField(id: 1)
  SharedLeaf? payload;
}

void _registerCommonTypes(Fory fory) {
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    SharedLeaf,
    name: 'test.SharedLeaf',
  );
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    RefPair,
    name: 'test.RefPair',
  );
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    NoRefPair,
    name: 'test.NoRefPair',
  );
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    CircularNode,
    name: 'test.CircularNode',
  );
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    ImmutableRefPair,
    name: 'test.ImmutableRefPair',
  );
}

void _registerV1Types(Fory fory) {
  _registerCommonTypes(fory);
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    CompatibleEnvelopeV1,
    name: 'compat.Envelope',
  );
}

void _registerV2Types(Fory fory) {
  _registerCommonTypes(fory);
  ObjectAndCompatibleSerializerTestForyModule.register(
    fory,
    CompatibleEnvelopeV2,
    name: 'compat.Envelope',
  );
}

void main() {
  test('schema overflow root reuse', () {
    final reader = Fory(compatible: true, maxSchemaVersionsPerType: 1);
    final firstWriter = Fory(compatible: true);
    final secondWriter = Fory(compatible: true);
    ObjectAndCompatibleSerializerTestForyModule.register(
      reader,
      SchemaValue,
      name: 'test.SchemaValue',
    );
    ObjectAndCompatibleSerializerTestForyModule.register(
      firstWriter,
      SchemaValueA,
      name: 'test.SchemaValue',
    );
    ObjectAndCompatibleSerializerTestForyModule.register(
      secondWriter,
      SchemaValueB,
      name: 'test.SchemaValue',
    );
    final first = firstWriter.serialize(<Object>[
      SchemaValueA()
        ..value = 17
        ..extraA = 'a',
      SchemaValueA()
        ..value = 19
        ..extraA = 'b',
    ]);
    final overflow = secondWriter.serialize(<Object>[
      SchemaValueB()
        ..value = 29
        ..extraB = 'c',
      SchemaValueB()
        ..value = 31
        ..extraB = 'd',
    ]);
    expect(
      reader.deserialize<List>(first).cast<SchemaValue>().map((v) => v.value),
      [17, 19],
    );
    for (var i = 0; i < 2; i++) {
      expect(
        reader
            .deserialize<List>(overflow)
            .cast<SchemaValue>()
            .map((v) => v.value),
        [29, 31],
      );
      expect(
        () =>
            reader.deserialize<List>(overflow.sublist(0, overflow.length - 1)),
        throwsA(anything),
      );
    }
    expect(
      reader.deserialize<List>(first).cast<SchemaValue>().map((v) => v.value),
      [17, 19],
    );
  });
  group('generated struct serialization', () {
    test('direct ref fields preserve identity while plain fields do not', () {
      final fory = Fory();
      _registerCommonTypes(fory);

      final shared = SharedLeaf()..label = 'shared';
      final refRoundTrip = fory.deserialize<RefPair>(
        fory.serialize(
          RefPair()
            ..first = shared
            ..second = shared,
        ),
      );
      final noRefRoundTrip = fory.deserialize<NoRefPair>(
        fory.serialize(
          NoRefPair()
            ..first = shared
            ..second = shared,
        ),
      );

      expect(refRoundTrip.first!.label, equals('shared'));
      expect(identical(refRoundTrip.first, refRoundTrip.second), isTrue);
      expect(noRefRoundTrip.first!.label, equals('shared'));
      expect(identical(noRefRoundTrip.first, noRefRoundTrip.second), isFalse);
    });

    test('constructor-based generated structs preserve shared references', () {
      final fory = Fory();
      _registerCommonTypes(fory);

      final shared = SharedLeaf()..label = 'immutable';
      final roundTrip = fory.deserialize<ImmutableRefPair>(
        fory.serialize(ImmutableRefPair(shared, shared)),
      );

      expect(roundTrip.first!.label, equals('immutable'));
      expect(identical(roundTrip.first, roundTrip.second), isTrue);
    });

    test('mutable generated structs round trip self references', () {
      final fory = Fory();
      _registerCommonTypes(fory);

      final node = CircularNode()..name = 'loop';
      node.next = node;

      final roundTrip = fory.deserialize<CircularNode>(fory.serialize(node));

      expect(roundTrip.name, equals('loop'));
      expect(identical(roundTrip, roundTrip.next), isTrue);
    });
  });

  group('compatible generated structs', () {
    test(
      'match fields by stable ids across rename, reorder, and missing fields',
      () {
        final writer = Fory(compatible: true);
        final reader = Fory(compatible: true);
        _registerV1Types(writer);
        _registerV2Types(reader);

        final shared = SharedLeaf()..label = 'shared';
        final migrated = reader.deserialize<CompatibleEnvelopeV2>(
          writer.serialize(
            CompatibleEnvelopeV1()
              ..name = 'Ada'
              ..age = 36
              ..payload = shared
              ..first = shared
              ..second = shared,
          ),
        );

        expect(migrated.displayName, equals('Ada'));
        expect(migrated.city, equals('unknown'));
        expect(migrated.payload, isA<SharedLeaf>());
        expect((migrated.payload as SharedLeaf).label, equals('shared'));
        expect(identical(migrated.original, migrated.duplicate), isTrue);

        final roundTripBack = writer.deserialize<CompatibleEnvelopeV1>(
          reader.serialize(migrated),
        );

        expect(roundTripBack.name, equals('Ada'));
        expect(roundTripBack.age, equals(0));
        expect(roundTripBack.payload, isA<SharedLeaf>());
        expect((roundTripBack.payload as SharedLeaf).label, equals('shared'));
        expect(identical(roundTripBack.first, roundTripBack.second), isTrue);
      },
    );

    test('reserializes compatible structs with local TypeDef ordering', () {
      final writer = Fory(compatible: true);
      final reader = Fory(compatible: true);
      _registerV1Types(writer);
      _registerV2Types(reader);

      final migratedShared = SharedLeaf()..label = 'shared';
      final migrated = reader.deserialize<CompatibleEnvelopeV2>(
        writer.serialize(
          CompatibleEnvelopeV1()
            ..name = 'Ada'
            ..age = 36
            ..payload = migratedShared
            ..first = migratedShared
            ..second = migratedShared,
        ),
      );

      final localShared = SharedLeaf()..label = 'shared';
      final local =
          CompatibleEnvelopeV2()
            ..displayName = 'Ada'
            ..city = 'unknown'
            ..payload = localShared
            ..original = localShared
            ..duplicate = localShared;

      expect(
        reader.serialize(migrated),
        orderedEquals(reader.serialize(local)),
      );
    });

    test('rejects compatible dynamic field framing drift', () {
      final writer = Fory(compatible: true);
      final reader = Fory(compatible: true);
      _registerCommonTypes(writer);
      _registerCommonTypes(reader);
      ObjectAndCompatibleSerializerTestForyModule.register(
        writer,
        DynamicPayloadEnvelopeV1,
        name: 'compat.DynamicPayloadEnvelope',
      );
      ObjectAndCompatibleSerializerTestForyModule.register(
        reader,
        StaticPayloadEnvelopeV2,
        name: 'compat.DynamicPayloadEnvelope',
      );

      final leaf = SharedLeaf()..label = 'leaf';
      final bytes = writer.serialize(
        DynamicPayloadEnvelopeV1()..payload = leaf,
      );

      expect(
        () => reader.deserialize<StaticPayloadEnvelopeV2>(bytes),
        throwsA(
          isA<StateError>().having(
            (error) => error.toString(),
            'message',
            contains('incompatible local and remote schemas'),
          ),
        ),
      );
    });
  });
}
