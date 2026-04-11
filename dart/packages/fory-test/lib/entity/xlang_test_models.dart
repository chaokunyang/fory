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

library;

import 'package:fory/fory.dart';

part 'xlang_test_models.g.dart';

abstract final class _WireTypeIds {
  static const int string = 21;
  static const int list = 22;
  static const int map = 24;
  static const int enumById = 25;
  static const int compatibleStruct = 28;
  static const int ext = 31;
  static const int union = 33;
}

void registerXlangType(
  Fory fory,
  Type type, {
  int? id,
  String? namespace,
  String? typeName,
}) {
  if (type == MyExt) {
    fory.register(
      MyExt,
      const _MyExtSerializer(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
    return;
  }
  if (type == Union2) {
    fory.registerUnion(
      Union2,
      const _Union2Serializer(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
    return;
  }
  if (type == StructWithUnion2) {
    fory.register(
      StructWithUnion2,
      const _StructWithUnion2Serializer(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
    return;
  }
  if (type == RefOverrideContainer) {
    fory.register(
      RefOverrideContainer,
      const _RefOverrideContainerSerializer(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
    return;
  }
  if (type == MyWrapper) {
    fory.register(
      MyWrapper,
      const _MyWrapperSerializer(),
      id: id,
      namespace: namespace,
      typeName: typeName,
    );
    return;
  }
  _registerXlangTestModelsForyType(
    fory,
    type,
    id: id,
    namespace: namespace,
    typeName: typeName,
  );
}

@ForyObject()
enum Color {
  green,
  red,
  blue,
  white,
}

@ForyObject()
enum TestEnum {
  valueA,
  valueB,
  valueC,
}

@ForyObject()
class TwoEnumFieldStructEvolution {
  TwoEnumFieldStructEvolution();

  TestEnum f1 = TestEnum.valueA;
  TestEnum f2 = TestEnum.valueA;
}

@ForyObject()
class Item {
  Item();

  String name = '';
}

@ForyObject()
class SimpleStruct {
  SimpleStruct();

  Map<Int32?, double?> f1 = <Int32?, double?>{};
  Int32 f2 = Int32(0);
  Item f3 = Item();
  String f4 = '';
  Color f5 = Color.green;
  List<String> f6 = <String>[];
  Int32 f7 = Int32(0);
  Int32 f8 = Int32(0);
  Int32 last = Int32(0);
}

@ForyObject()
class EvolvingOverrideStruct {
  EvolvingOverrideStruct();

  String f1 = '';
}

@ForyObject(evolving: false)
class FixedOverrideStruct {
  FixedOverrideStruct();

  String f1 = '';
}

@ForyObject()
class Item1 {
  Item1();

  Int32 f1 = Int32(0);
  Int32 f2 = Int32(0);
  Int32 f3 = Int32(0);
  Int32 f4 = Int32(0);
  Int32 f5 = Int32(0);
  Int32 f6 = Int32(0);
}

final class Union2 {
  const Union2._(this.index, this.value);

  final int index;
  final Object value;

  factory Union2.ofString(String value) => Union2._(0, value);

  factory Union2.ofInt64(int value) => Union2._(1, value);

  factory Union2.of(int index, Object value) => Union2._(index, value);

  bool get isString => index == 0;

  bool get isInt64 => index == 1;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Union2 && other.index == index && other.value == value;

  @override
  int get hashCode => Object.hash(index, value);
}

@ForyObject()
class StructWithUnion2 {
  StructWithUnion2();

  Union2 union = Union2.ofString('');
}

@ForyObject()
class StructWithList {
  StructWithList();

  List<String?> items = <String?>[];
}

@ForyObject()
class StructWithMap {
  StructWithMap();

  Map<String?, String?> data = <String?, String?>{};
}

@ForyObject()
class MyStruct {
  MyStruct();

  @Int32Type()
  int id = 0;
}

final class MyExt {
  MyExt([this.id = 0]);

  int id;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is MyExt && other.id == id;

  @override
  int get hashCode => id.hashCode;
}

@ForyObject()
class MyWrapper {
  MyWrapper();

  Color color = Color.white;
  MyExt myExt = MyExt();
  MyStruct myStruct = MyStruct();
}

@ForyObject()
class EmptyWrapper {
  EmptyWrapper();
}

@ForyObject()
class VersionCheckStruct {
  VersionCheckStruct();

  @Int32Type()
  int f1 = 0;

  @ForyField(nullable: true)
  String? f2;

  double f3 = 0;
}

abstract interface class Animal {
  int get age;
}

@ForyObject()
class Dog implements Animal {
  Dog();

  @override
  @Int32Type()
  int age = 0;

  @ForyField(nullable: true)
  String? name;
}

@ForyObject()
class Cat implements Animal {
  Cat();

  @Int32Type()
  @override
  int age = 0;

  @Int32Type()
  int lives = 0;
}

@ForyObject()
class AnimalListHolder {
  AnimalListHolder();

  List<Animal> animals = <Animal>[];
}

@ForyObject()
class AnimalMapHolder {
  AnimalMapHolder();

  Map<String, Animal> animalMap = <String, Animal>{};
}

@ForyObject()
class EmptyStruct {
  EmptyStruct();
}

@ForyObject()
class OneStringFieldStruct {
  OneStringFieldStruct();

  @ForyField(nullable: true)
  String? f1;
}

@ForyObject()
class TwoStringFieldStruct {
  TwoStringFieldStruct();

  String f1 = '';
  String f2 = '';
}

@ForyObject()
class OneEnumFieldStruct {
  OneEnumFieldStruct();

  TestEnum f1 = TestEnum.valueA;
}

@ForyObject()
class TwoEnumFieldStruct {
  TwoEnumFieldStruct();

  TestEnum f1 = TestEnum.valueA;
  TestEnum f2 = TestEnum.valueA;
}

@ForyObject()
class NullableComprehensiveSchemaConsistent {
  NullableComprehensiveSchemaConsistent();

  Int8 byteField = Int8(0);
  Int16 shortField = Int16(0);
  Int32 intField = Int32(0);
  int longField = 0;
  Float32 floatField = Float32(0);
  double doubleField = 0;
  bool boolField = false;
  String stringField = '';
  List<String> listField = <String>[];
  Set<String> setField = <String>{};
  Map<String, String> mapField = <String, String>{};

  @ForyField(nullable: true)
  Int32? nullableInt;

  @ForyField(nullable: true)
  int? nullableLong;

  @ForyField(nullable: true)
  Float32? nullableFloat;

  @ForyField(nullable: true)
  double? nullableDouble;

  @ForyField(nullable: true)
  bool? nullableBool;

  @ForyField(nullable: true)
  String? nullableString;

  @ForyField(nullable: true)
  List<String>? nullableList;

  @ForyField(nullable: true)
  Set<String>? nullableSet;

  @ForyField(nullable: true)
  Map<String, String>? nullableMap;
}

@ForyObject()
class NullableComprehensiveCompatible {
  NullableComprehensiveCompatible();

  Int8 byteField = Int8(0);
  Int16 shortField = Int16(0);
  Int32 intField = Int32(0);
  int longField = 0;
  Float32 floatField = Float32(0);
  double doubleField = 0;
  bool boolField = false;

  Int32 boxedInt = Int32(0);
  int boxedLong = 0;
  Float32 boxedFloat = Float32(0);
  double boxedDouble = 0;
  bool boxedBool = false;

  String stringField = '';
  List<String> listField = <String>[];
  Set<String> setField = <String>{};
  Map<String, String> mapField = <String, String>{};

  Int32 nullableInt1 = Int32(0);
  int nullableLong1 = 0;
  Float32 nullableFloat1 = Float32(0);
  double nullableDouble1 = 0;
  bool nullableBool1 = false;
  String nullableString2 = '';
  List<String> nullableList2 = <String>[];
  Set<String> nullableSet2 = <String>{};
  Map<String, String> nullableMap2 = <String, String>{};
}

@ForyObject()
class RefInnerSchemaConsistent {
  RefInnerSchemaConsistent();

  @Int32Type()
  int id = 0;
  String name = '';
}

@ForyObject()
class RefOuterSchemaConsistent {
  RefOuterSchemaConsistent();

  @ForyField(ref: true, nullable: true, dynamic: false)
  RefInnerSchemaConsistent? inner1;

  @ForyField(ref: true, nullable: true, dynamic: false)
  RefInnerSchemaConsistent? inner2;
}

@ForyObject()
class RefInnerCompatible {
  RefInnerCompatible();

  @Int32Type()
  int id = 0;
  String name = '';
}

@ForyObject()
class RefOuterCompatible {
  RefOuterCompatible();

  @ForyField(ref: true, nullable: true)
  RefInnerCompatible? inner1;

  @ForyField(ref: true, nullable: true)
  RefInnerCompatible? inner2;
}

@ForyObject()
class RefOverrideElement {
  RefOverrideElement();

  Int32 id = Int32(0);
  String name = '';
}

class RefOverrideContainer {
  RefOverrideContainer();

  List<RefOverrideElement> listField = <RefOverrideElement>[];
  Map<String, RefOverrideElement> mapField = <String, RefOverrideElement>{};
}

@ForyObject()
class CircularRefStruct {
  CircularRefStruct();

  String name = '';

  @ForyField(ref: true, nullable: true)
  CircularRefStruct? selfRef;
}

@ForyObject()
class UnsignedSchemaConsistent {
  UnsignedSchemaConsistent();

  UInt8 u8Field = UInt8(0);
  UInt16 u16Field = UInt16(0);

  @Uint32Type(compress: true)
  UInt32 u32VarField = UInt32(0);

  @Uint32Type(compress: false)
  UInt32 u32FixedField = UInt32(0);

  @Uint64Type(encoding: LongEncoding.varint)
  int u64VarField = 0;

  @Uint64Type(encoding: LongEncoding.fixed)
  int u64FixedField = 0;

  @Uint64Type(encoding: LongEncoding.tagged)
  int u64TaggedField = 0;

  @ForyField(nullable: true)
  UInt8? u8NullableField;

  @ForyField(nullable: true)
  UInt16? u16NullableField;

  @ForyField(nullable: true)
  @Uint32Type(compress: true)
  UInt32? u32VarNullableField;

  @ForyField(nullable: true)
  @Uint32Type(compress: false)
  UInt32? u32FixedNullableField;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.varint)
  int? u64VarNullableField;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.fixed)
  int? u64FixedNullableField;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.tagged)
  int? u64TaggedNullableField;
}

@ForyObject()
class UnsignedSchemaConsistentSimple {
  UnsignedSchemaConsistentSimple();

  @Uint64Type(encoding: LongEncoding.tagged)
  int u64Tagged = 0;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.tagged)
  int? u64TaggedNullable;
}

@ForyObject()
class UnsignedSchemaCompatible {
  UnsignedSchemaCompatible();

  @ForyField(nullable: true)
  UInt8? u8Field1;

  @ForyField(nullable: true)
  UInt16? u16Field1;

  @ForyField(nullable: true)
  @Uint32Type(compress: true)
  UInt32? u32VarField1;

  @ForyField(nullable: true)
  @Uint32Type(compress: false)
  UInt32? u32FixedField1;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.varint)
  int? u64VarField1;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.fixed)
  int? u64FixedField1;

  @ForyField(nullable: true)
  @Uint64Type(encoding: LongEncoding.tagged)
  int? u64TaggedField1;

  UInt8 u8Field2 = UInt8(0);
  UInt16 u16Field2 = UInt16(0);

  @Uint32Type(compress: true)
  UInt32 u32VarField2 = UInt32(0);

  @Uint32Type(compress: false)
  UInt32 u32FixedField2 = UInt32(0);

  @Uint64Type(encoding: LongEncoding.varint)
  int u64VarField2 = 0;

  @Uint64Type(encoding: LongEncoding.fixed)
  int u64FixedField2 = 0;

  @Uint64Type(encoding: LongEncoding.tagged)
  int u64TaggedField2 = 0;
}

final class _Union2Serializer extends Serializer<Union2> {
  const _Union2Serializer();

  @override
  bool get isUnion => true;

  @override
  void write(WriteContext context, Union2 value) {
    final buffer = context.buffer;
    buffer.writeVarUint32(value.index);
    context.writeAny(value.value);
  }

  @override
  Union2 read(ReadContext context) {
    final buffer = context.buffer;
    final index = buffer.readVarUint32();
    final value = context.readAny();
    if (index == 0 && value is String) {
      return Union2.ofString(value);
    }
    if (index == 1 && value is int) {
      return Union2.ofInt64(value);
    }
    throw StateError('Unsupported Union2 case $index with value $value.');
  }
}

final class _MyExtSerializer extends Serializer<MyExt> {
  const _MyExtSerializer();

  @override
  void write(WriteContext context, MyExt value) {
    context.writeVarInt32(value.id);
  }

  @override
  MyExt read(ReadContext context) {
    return MyExt(context.readVarInt32());
  }
}

const List<Map<String, Object?>> _structWithUnion2Fields =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'union',
    'identifier': 'union',
    'id': null,
    'shape': <String, Object?>{
      'type': Union2,
      'typeId': _WireTypeIds.union,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final class _StructWithUnion2Serializer extends Serializer<StructWithUnion2> {
  const _StructWithUnion2Serializer();

  @override
  bool get isStruct => true;

  @override
  List<Map<String, Object?>> get fields => _structWithUnion2Fields;

  @override
  void write(WriteContext context, StructWithUnion2 value) {
    context.writeField(_structWithUnion2Fields[0], value.union);
  }

  @override
  StructWithUnion2 read(ReadContext context) {
    final value = StructWithUnion2();
    context.reference(value);
    value.union = context.readField<Object?>(
      _structWithUnion2Fields[0],
      value.union,
    ) as Union2;
    return value;
  }
}

const List<Map<String, Object?>> _refOverrideContainerFields =
    <Map<String, Object?>>[
  <String, Object?>{
    'name': 'listField',
    'identifier': 'list_field',
    'id': null,
    'shape': <String, Object?>{
      'type': List,
      'typeId': _WireTypeIds.list,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': RefOverrideElement,
          'typeId': _WireTypeIds.compatibleStruct,
          'nullable': false,
          'ref': true,
          'dynamic': null,
          'arguments': <Object?>[],
        },
      ],
    },
  },
  <String, Object?>{
    'name': 'mapField',
    'identifier': 'map_field',
    'id': null,
    'shape': <String, Object?>{
      'type': Map,
      'typeId': _WireTypeIds.map,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[
        <String, Object?>{
          'type': String,
          'typeId': _WireTypeIds.string,
          'nullable': false,
          'ref': false,
          'dynamic': null,
          'arguments': <Object?>[],
        },
        <String, Object?>{
          'type': RefOverrideElement,
          'typeId': _WireTypeIds.compatibleStruct,
          'nullable': false,
          'ref': true,
          'dynamic': null,
          'arguments': <Object?>[],
        },
      ],
    },
  },
];

final class _RefOverrideContainerSerializer
    extends Serializer<RefOverrideContainer> {
  const _RefOverrideContainerSerializer();

  @override
  bool get isStruct => true;

  @override
  List<Map<String, Object?>> get fields => _refOverrideContainerFields;

  @override
  void write(WriteContext context, RefOverrideContainer value) {
    context.writeField(_refOverrideContainerFields[0], value.listField);
    context.writeField(_refOverrideContainerFields[1], value.mapField);
  }

  @override
  RefOverrideContainer read(ReadContext context) {
    final value = RefOverrideContainer();
    context.reference(value);
    final listValue = context.readField<Object?>(
      _refOverrideContainerFields[0],
      value.listField,
    ) as List;
    value.listField =
        listValue.cast<RefOverrideElement>().toList(growable: false);
    final mapValue = context.readField<Object?>(
      _refOverrideContainerFields[1],
      value.mapField,
    ) as Map;
    value.mapField = Map<String, RefOverrideElement>.from(
      mapValue.map(
        (key, item) => MapEntry(key as String, item as RefOverrideElement),
      ),
    );
    return value;
  }
}

const List<Map<String, Object?>> _myWrapperFields = <Map<String, Object?>>[
  <String, Object?>{
    'name': 'color',
    'identifier': 'color',
    'id': null,
    'shape': <String, Object?>{
      'type': Color,
      'typeId': _WireTypeIds.enumById,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'myExt',
    'identifier': 'my_ext',
    'id': null,
    'shape': <String, Object?>{
      'type': MyExt,
      'typeId': _WireTypeIds.ext,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
  <String, Object?>{
    'name': 'myStruct',
    'identifier': 'my_struct',
    'id': null,
    'shape': <String, Object?>{
      'type': MyStruct,
      'typeId': _WireTypeIds.compatibleStruct,
      'nullable': false,
      'ref': false,
      'dynamic': null,
      'arguments': <Object?>[],
    },
  },
];

final class _MyWrapperSerializer extends Serializer<MyWrapper> {
  const _MyWrapperSerializer();

  @override
  bool get isStruct => true;

  @override
  List<Map<String, Object?>> get fields => _myWrapperFields;

  @override
  void write(WriteContext context, MyWrapper value) {
    context.writeField(_myWrapperFields[0], value.color);
    context.writeField(_myWrapperFields[1], value.myExt);
    context.writeField(_myWrapperFields[2], value.myStruct);
  }

  @override
  MyWrapper read(ReadContext context) {
    final value = MyWrapper();
    context.reference(value);
    value.color = context.readField<Object?>(
      _myWrapperFields[0],
      value.color,
    ) as Color;
    value.myExt = context.readField<Object?>(
      _myWrapperFields[1],
      value.myExt,
    ) as MyExt;
    value.myStruct = context.readField<Object?>(
      _myWrapperFields[2],
      value.myStruct,
    ) as MyStruct;
    return value;
  }
}
