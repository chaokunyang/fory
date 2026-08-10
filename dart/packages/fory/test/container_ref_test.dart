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

import 'dart:typed_data';

import 'package:fory/fory.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:test/test.dart';

part 'container_ref_test.fory.dart';

@ForyStruct()
class Node {
  Node();

  String name = '';
}

/// Struct with @ListField that enables ref tracking on list elements.
@ForyStruct()
class RefListContainer {
  RefListContainer();

  @ListField(element: DeclaredType(ref: true))
  List<Node> items = <Node>[];
}

/// Struct with no @ListField annotation — list elements are NOT ref-tracked.
@ForyStruct()
class NoRefListContainer {
  NoRefListContainer();

  List<Node> items = <Node>[];
}

/// Struct with @MapField that enables ref tracking on map values.
@ForyStruct()
class RefMapValueContainer {
  RefMapValueContainer();

  @MapField(value: DeclaredType(ref: true))
  Map<String, Node> entries = <String, Node>{};
}

/// Struct with no @MapField — map values are NOT ref-tracked.
@ForyStruct()
class NoRefMapContainer {
  NoRefMapContainer();

  Map<String, Node> entries = <String, Node>{};
}

/// Struct with @MapField that enables ref tracking on map keys.
@ForyStruct()
class RefMapKeyContainer {
  RefMapKeyContainer();

  @MapField(key: DeclaredType(ref: true))
  Map<Node, String> entries = <Node, String>{};
}

/// Struct with nested list of maps where map values are ref-tracked.
@ForyStruct()
class NestedListOfMapContainer {
  NestedListOfMapContainer();

  @ListField(element: MapType(value: DeclaredType(ref: true)))
  List<Map<String, Node>> groups = <Map<String, Node>>[];
}

/// Struct with a map whose values are ref-tracked lists.
@ForyStruct()
class NestedMapOfListContainer {
  NestedMapOfListContainer();

  @MapField(value: ListType(element: DeclaredType(ref: true)))
  Map<String, List<Node>> groups = <String, List<Node>>{};
}

@ForyStruct()
final class TrackedContainerHolder {
  TrackedContainerHolder(
    this.lists,
    this.sets,
    this.maps,
    this.flags,
    this.dynamicLists,
  );

  @ForyField(ref: true)
  final List<List<int>> lists;

  @ForyField(ref: true)
  final Set<List<int>> sets;

  @ForyField(ref: true)
  final Map<String, List<int>> maps;

  @ForyField(ref: true)
  final BoolList flags;

  @ForyField(ref: true, dynamic: true)
  final List<List<int>> dynamicLists;
}

@ForyStruct()
final class TrackedMutableHolder {
  TrackedMutableHolder();

  @ForyField(ref: true)
  List<List<int>> lists = <List<int>>[];
}

@ForyStruct()
final class NestedTrackedOwnerHolder {
  NestedTrackedOwnerHolder();

  @ListField(element: ListType(ref: true, element: Int32Type()))
  List<List<int>> lists = <List<int>>[];

  @ListField(element: SetType(ref: true, element: Int32Type()))
  List<Set<int>> sets = <Set<int>>[];

  @ListField(element: MapType(ref: true, key: StringType(), value: Int32Type()))
  List<Map<String, int>> maps = <Map<String, int>>[];

  @ListField(element: ListType(ref: true, element: BoolType()))
  List<BoolList> flags = <BoolList>[];
}

@ForyStruct()
final class ListOwnerChild {
  ListOwnerChild();

  @ForyField(ref: true, dynamic: true)
  Object? owner;
}

@ForyStruct()
final class ListOwnerHolder {
  ListOwnerHolder();

  @ListField(ref: true, element: DeclaredType(ref: true))
  List<ListOwnerChild> children = <ListOwnerChild>[];
}

@ForyStruct()
final class CompatibleTrackedRemote {
  CompatibleTrackedRemote();

  @ListField(element: ListType(ref: true, element: Int32Type()))
  List<List<int>> values = <List<int>>[];
}

@ForyStruct()
final class CompatibleTrackedLocal {
  CompatibleTrackedLocal();

  @ListField(element: ListType(ref: false, element: Int32Type()))
  List<List<int>> values = <List<int>>[];
}

@ForyStruct()
final class TrackedBoolValues {
  TrackedBoolValues();

  @ListField(element: BoolType(ref: true))
  List<bool> values = <bool>[];
}

void _registerAll(Fory fory) {
  ContainerRefTestForyModule.register(fory, Node, name: 'test.Node');
  ContainerRefTestForyModule.register(
    fory,
    RefListContainer,
    name: 'test.RefListContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    NoRefListContainer,
    name: 'test.NoRefListContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    RefMapValueContainer,
    name: 'test.RefMapValueContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    NoRefMapContainer,
    name: 'test.NoRefMapContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    RefMapKeyContainer,
    name: 'test.RefMapKeyContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    NestedListOfMapContainer,
    name: 'test.NestedListOfMapContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    NestedMapOfListContainer,
    name: 'test.NestedMapOfListContainer',
  );
  ContainerRefTestForyModule.register(
    fory,
    TrackedContainerHolder,
    name: 'test.TrackedContainerHolder',
  );
  ContainerRefTestForyModule.register(
    fory,
    TrackedMutableHolder,
    name: 'test.TrackedMutableHolder',
  );
  ContainerRefTestForyModule.register(
    fory,
    NestedTrackedOwnerHolder,
    name: 'test.NestedTrackedOwnerHolder',
  );
  ContainerRefTestForyModule.register(
    fory,
    ListOwnerChild,
    name: 'test.ListOwnerChild',
  );
  ContainerRefTestForyModule.register(
    fory,
    ListOwnerHolder,
    name: 'test.ListOwnerHolder',
  );
  ContainerRefTestForyModule.register(
    fory,
    TrackedBoolValues,
    name: 'test.TrackedBoolValues',
  );
}

int _findUniqueBytes(Uint8List bytes, List<int> expected) {
  final matches = <int>[];
  for (var start = 0; start <= bytes.length - expected.length; start += 1) {
    var matchesAtStart = true;
    for (var offset = 0; offset < expected.length; offset += 1) {
      if (bytes[start + offset] != expected[offset]) {
        matchesAtStart = false;
        break;
      }
    }
    if (matchesAtStart) {
      matches.add(start);
    }
  }
  expect(
    matches,
    hasLength(1),
    reason: 'Expected one $expected sequence in $bytes.',
  );
  return matches.single;
}

void main() {
  late Fory fory;

  setUp(() {
    fory = Fory();
    _registerAll(fory);
  });

  group('list element ref via @ListField annotation', () {
    test('shared list elements preserve identity with element ref enabled', () {
      final shared = Node()..name = 'shared';
      final container =
          RefListContainer()..items = <Node>[shared, shared, shared];
      final bytes = fory.serialize(container);
      final result = fory.deserialize<RefListContainer>(bytes);

      expect(result.items, hasLength(3));
      expect(result.items[0].name, equals('shared'));
      expect(identical(result.items[0], result.items[1]), isTrue);
      expect(identical(result.items[1], result.items[2]), isTrue);
    });

    test('shared list elements are different instances without annotation', () {
      final shared = Node()..name = 'shared';
      final container =
          NoRefListContainer()..items = <Node>[shared, shared, shared];
      final bytes = fory.serialize(container);
      final result = fory.deserialize<NoRefListContainer>(bytes);

      expect(result.items, hasLength(3));
      expect(result.items[0].name, equals('shared'));
      expect(result.items[1].name, equals('shared'));
      expect(identical(result.items[0], result.items[1]), isFalse);
      expect(identical(result.items[1], result.items[2]), isFalse);
    });
  });

  group('map value ref via @MapField annotation', () {
    test('shared map values preserve identity with value ref enabled', () {
      final shared = Node()..name = 'val';
      final container =
          RefMapValueContainer()
            ..entries = <String, Node>{'a': shared, 'b': shared};
      final bytes = fory.serialize(container);
      final result = fory.deserialize<RefMapValueContainer>(bytes);

      expect(result.entries, hasLength(2));
      expect(result.entries['a']!.name, equals('val'));
      expect(identical(result.entries['a'], result.entries['b']), isTrue);
    });

    test('shared map values are different instances without annotation', () {
      final shared = Node()..name = 'val';
      final container =
          NoRefMapContainer()
            ..entries = <String, Node>{'a': shared, 'b': shared};
      final bytes = fory.serialize(container);
      final result = fory.deserialize<NoRefMapContainer>(bytes);

      expect(result.entries, hasLength(2));
      expect(result.entries['a']!.name, equals('val'));
      expect(result.entries['b']!.name, equals('val'));
      expect(identical(result.entries['a'], result.entries['b']), isFalse);
    });
  });

  group('map key ref via @MapField annotation', () {
    test('shared map keys preserve identity with key ref enabled', () {
      final shared = Node()..name = 'key';
      final container =
          RefMapKeyContainer()..entries = <Node, String>{shared: 'x'};
      final bytes = fory.serialize(container);
      final result = fory.deserialize<RefMapKeyContainer>(bytes);

      expect(result.entries, hasLength(1));
      final key = result.entries.keys.first;
      expect(key.name, equals('key'));
    });
  });

  group('nested container ref via @ListField/@MapField annotation', () {
    test(
      'list of maps with ref-tracked values preserves identity across maps',
      () {
        final shared = Node()..name = 'deep';
        final container =
            NestedListOfMapContainer()
              ..groups = <Map<String, Node>>[
                <String, Node>{'x': shared},
                <String, Node>{'y': shared},
              ];
        final bytes = fory.serialize(container);
        final result = fory.deserialize<NestedListOfMapContainer>(bytes);

        expect(result.groups, hasLength(2));
        expect(result.groups[0]['x']!.name, equals('deep'));
        expect(identical(result.groups[0]['x'], result.groups[1]['y']), isTrue);
      },
    );

    test(
      'map of lists with ref-tracked elements preserves identity across lists',
      () {
        final shared = Node()..name = 'maplist';
        final container =
            NestedMapOfListContainer()
              ..groups = <String, List<Node>>{
                'a': <Node>[shared, shared],
                'b': <Node>[shared],
              };
        final bytes = fory.serialize(container);
        final result = fory.deserialize<NestedMapOfListContainer>(bytes);

        expect(result.groups['a'], hasLength(2));
        expect(result.groups['b'], hasLength(1));
        expect(result.groups['a']![0].name, equals('maplist'));
        expect(
          identical(result.groups['a']![0], result.groups['a']![1]),
          isTrue,
        );
        expect(
          identical(result.groups['a']![0], result.groups['b']![0]),
          isTrue,
        );
      },
    );
  });

  test('tracked generated containers preserve final carrier identity', () {
    final lists = <List<int>>[
      <int>[1, 2],
      <int>[3],
    ];
    final sets = <List<int>>{
      <int>[4, 5],
    };
    final maps = <String, List<int>>{
      'items': <int>[6, 7],
    };
    final flags = BoolList.fromList(<bool>[true, false, true]);
    final values = <TrackedContainerHolder>[
      TrackedContainerHolder(lists, sets, maps, flags, lists),
      TrackedContainerHolder(lists, sets, maps, flags, lists),
    ];

    final result = fory.deserialize<List>(fory.serialize(values));
    final first = result[0] as TrackedContainerHolder;
    final second = result[1] as TrackedContainerHolder;

    expect(identical(first.lists, second.lists), isTrue);
    expect(identical(first.sets, second.sets), isTrue);
    expect(identical(first.maps, second.maps), isTrue);
    expect(identical(first.flags, second.flags), isTrue);
    expect(identical(first.dynamicLists, second.dynamicLists), isTrue);
    expect(identical(first.lists, first.dynamicLists), isTrue);
    expect(first.lists, equals(lists));
    expect(first.maps, equals(maps));
    expect(first.flags, orderedEquals(flags));
  });

  test('tracked mutable generated containers preserve identity', () {
    final lists = <List<int>>[
      <int>[8, 9],
    ];
    final values = <TrackedMutableHolder>[
      TrackedMutableHolder()..lists = lists,
      TrackedMutableHolder()..lists = lists,
    ];

    final result = fory.deserialize<List>(fory.serialize(values));
    final first = result[0] as TrackedMutableHolder;
    final second = result[1] as TrackedMutableHolder;

    expect(identical(first.lists, second.lists), isTrue);
    expect(first.lists, equals(lists));
  });

  test('compatible generated containers preserve final identity', () {
    final writer = Fory(compatible: true);
    final reader = Fory(compatible: true);
    _registerAll(writer);
    _registerAll(reader);
    final lists = <List<int>>[
      <int>[10, 11],
    ];
    final values = <TrackedMutableHolder>[
      TrackedMutableHolder()..lists = lists,
      TrackedMutableHolder()..lists = lists,
    ];

    final result = reader.deserialize<List>(writer.serialize(values));
    final first = result[0] as TrackedMutableHolder;
    final second = result[1] as TrackedMutableHolder;

    expect(identical(first.lists, second.lists), isTrue);
    expect(first.lists, equals(lists));
  });

  test('nested tracked generated owners preserve final identity', () {
    final list = <int>[1, 2];
    final set = <int>{3, 4};
    final map = <String, int>{'five': 5};
    final flags = BoolList.fromList(<bool>[true, false]);
    final value =
        NestedTrackedOwnerHolder()
          ..lists = <List<int>>[list, list]
          ..sets = <Set<int>>[set, set]
          ..maps = <Map<String, int>>[map, map]
          ..flags = <BoolList>[flags, flags];

    final result = fory.deserialize<NestedTrackedOwnerHolder>(
      fory.serialize(value),
    );

    expect(identical(result.lists[0], result.lists[1]), isTrue);
    expect(identical(result.sets[0], result.sets[1]), isTrue);
    expect(identical(result.maps[0], result.maps[1]), isTrue);
    expect(identical(result.flags[0], result.flags[1]), isTrue);
    expect(result.lists[0], equals(list));
    expect(result.sets[0], equals(set));
    expect(result.maps[0], equals(map));
    expect(result.flags[0], orderedEquals(flags));
  });

  test('first child resolves its final outer list owner', () {
    final value = ListOwnerHolder();
    final child = ListOwnerChild();
    value.children = <ListOwnerChild>[child];
    child.owner = value.children;

    final result = fory.deserialize<ListOwnerHolder>(fory.serialize(value));

    expect(result.children, hasLength(1));
    expect(identical(result.children, result.children.first.owner), isTrue);
  });

  test('non-ref empty generated containers stay empty', () {
    final result = fory.deserialize<NestedTrackedOwnerHolder>(
      fory.serialize(NestedTrackedOwnerHolder()),
    );

    expect(result.lists, isEmpty);
    expect(result.sets, isEmpty);
    expect(result.maps, isEmpty);
    expect(result.flags, isEmpty);
  });

  test('compatible remote nested refs publish the local carrier', () {
    final writer = Fory(compatible: true);
    final reader = Fory(compatible: true);
    ContainerRefTestForyModule.register(
      writer,
      CompatibleTrackedRemote,
      name: 'test.CompatibleTracked',
    );
    ContainerRefTestForyModule.register(
      reader,
      CompatibleTrackedLocal,
      name: 'test.CompatibleTracked',
    );
    final shared = <int>[12, 13];
    final bytes = writer.serialize(
      CompatibleTrackedRemote()..values = <List<int>>[shared, shared],
    );
    final result = reader.deserialize<CompatibleTrackedLocal>(bytes);

    expect(identical(result.values[0], result.values[1]), isTrue);
    expect(result.values[0], equals(shared));
  });

  test('value element fresh ref is rejected', () {
    final shared = Node()..name = 'shared';
    final bytes = fory.serialize(<Object?>[
      false,
      shared,
      shared,
    ], trackRef: true);
    final malformed = Uint8List.fromList(bytes);
    final boolEnvelope = _findUniqueBytes(malformed, <int>[
      0xff,
      TypeIds.boolType,
      0,
    ]);
    malformed[boolEnvelope] = RefWriter.refValueFlag;

    expect(
      () => fory.deserialize<Object?>(malformed),
      throwsA(isA<StateError>()),
    );

    final decoded = fory.deserialize<Object?>(bytes) as List<Object?>;
    expect(decoded.first, isFalse);
    expect(identical(decoded[1], decoded[2]), isTrue);
  });

  test('generated value element fresh ref is rejected', () {
    final bytes = fory.serialize(
      TrackedBoolValues()..values = <bool>[false, true],
    );
    final malformed = Uint8List.fromList(bytes);
    final boolElements = _findUniqueBytes(malformed, <int>[
      2,
      0x0d,
      0xff,
      0,
      0xff,
      1,
    ]);
    malformed[boolElements + 2] = RefWriter.refValueFlag;

    expect(
      () => fory.deserialize<TrackedBoolValues>(malformed),
      throwsA(isA<StateError>()),
    );
    expect(
      fory.deserialize<TrackedBoolValues>(bytes).values,
      orderedEquals(<bool>[false, true]),
    );
  });

  test('map value fresh ref is rejected', () {
    final bytes = fory.serialize(<bool, bool>{false: true}, trackRef: true);
    final entryHeader = _findUniqueBytes(bytes, <int>[0, 1, 1, 1, 0, 1]);
    final valueStart = entryHeader + 4;
    final malformed =
        Uint8List(bytes.length + 1)
          ..setRange(0, valueStart + 1, bytes)
          ..[entryHeader] = 0x08
          ..[valueStart + 1] = RefWriter.refValueFlag
          ..setRange(valueStart + 2, bytes.length + 1, bytes, valueStart + 1);

    expect(
      () => fory.deserialize<Object?>(malformed),
      throwsA(isA<StateError>()),
    );
    expect(fory.deserialize<Object?>(bytes), equals(<bool, bool>{false: true}));
  });
}
