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
import 'package:fory/src/serializer/compatible_struct_metadata.dart';
import 'package:test/test.dart';

part 'unsigned_serializer_test.fory.dart';

const int _uint32Midpoint = 0x80000000;
const int _uint64Midpoint = 0x8000000000000000;
const int _uint64Max = 0xffffffffffffffff;

@ForyStruct()
class UnsignedFields {
  UnsignedFields();

  @Uint8Type()
  int u8 = 0;

  @Uint16Type()
  int u16 = 0;

  @Uint32Type(compress: true)
  int u32Var = 0;

  @Uint32Type(compress: false)
  int u32Fixed = 0;

  @Uint64Type(encoding: LongEncoding.varint)
  int u64Var = 0;

  @Uint64Type(encoding: LongEncoding.fixed)
  int u64Fixed = 0;

  @Uint64Type(encoding: LongEncoding.tagged)
  int u64Tagged = 0;

  @Uint8Type()
  int? u8Nullable;

  @Uint16Type()
  int? u16Nullable;

  @Uint32Type(compress: true)
  int? u32VarNullable;

  @Uint32Type(compress: false)
  int? u32FixedNullable;

  @Uint64Type(encoding: LongEncoding.varint)
  int? u64VarNullable;

  @Uint64Type(encoding: LongEncoding.fixed)
  int? u64FixedNullable;

  @Uint64Type(encoding: LongEncoding.tagged)
  int? u64TaggedNullable;
}

@ForyStruct()
class UnsignedMetadataReader {
  UnsignedMetadataReader();

  @Uint8Type()
  int u8 = 0;

  int extra = 42;
}

void _registerUnsignedFields(Fory fory) {
  UnsignedSerializerTestFory.register(
    fory,
    UnsignedFields,
    namespace: 'test',
    typeName: 'UnsignedFields',
  );
}

void _registerUnsignedMetadataReader(Fory fory) {
  UnsignedSerializerTestFory.register(
    fory,
    UnsignedMetadataReader,
    namespace: 'test',
    typeName: 'UnsignedFields',
  );
}

UnsignedFields _midpointUnsignedFields() {
  return UnsignedFields()
    ..u8 = 0x80
    ..u16 = 0x8000
    ..u32Var = _uint32Midpoint
    ..u32Fixed = _uint32Midpoint
    ..u64Var = _uint64Midpoint
    ..u64Fixed = _uint64Midpoint
    ..u64Tagged = _uint64Midpoint
    ..u8Nullable = 0x80
    ..u16Nullable = 0x8000
    ..u32VarNullable = _uint32Midpoint
    ..u32FixedNullable = _uint32Midpoint
    ..u64VarNullable = _uint64Midpoint
    ..u64FixedNullable = _uint64Midpoint
    ..u64TaggedNullable = _uint64Midpoint;
}

UnsignedFields _maxUnsignedFields() {
  return UnsignedFields()
    ..u8 = 0xff
    ..u16 = 0xffff
    ..u32Var = 0xffffffff
    ..u32Fixed = 0xffffffff
    ..u64Var = _uint64Max
    ..u64Fixed = _uint64Max
    ..u64Tagged = _uint64Max
    ..u8Nullable = 0xff
    ..u16Nullable = 0xffff
    ..u32VarNullable = 0xffffffff
    ..u32FixedNullable = 0xffffffff
    ..u64VarNullable = _uint64Max
    ..u64FixedNullable = _uint64Max
    ..u64TaggedNullable = _uint64Max;
}

UnsignedFields _nullUnsignedFields() {
  return UnsignedFields()
    ..u8 = 1
    ..u16 = 2
    ..u32Var = 3
    ..u32Fixed = 4
    ..u64Var = 5
    ..u64Fixed = 6
    ..u64Tagged = 7
    ..u8Nullable = null
    ..u16Nullable = null
    ..u32VarNullable = null
    ..u32FixedNullable = null
    ..u64VarNullable = null
    ..u64FixedNullable = null
    ..u64TaggedNullable = null;
}

void _expectUnsignedFieldsEqual(UnsignedFields actual, UnsignedFields expected) {
  expect(actual.u8, equals(expected.u8));
  expect(actual.u16, equals(expected.u16));
  expect(actual.u32Var, equals(expected.u32Var));
  expect(actual.u32Fixed, equals(expected.u32Fixed));
  expect(actual.u64Var, equals(expected.u64Var));
  expect(actual.u64Fixed, equals(expected.u64Fixed));
  expect(actual.u64Tagged, equals(expected.u64Tagged));
  expect(actual.u8Nullable, equals(expected.u8Nullable));
  expect(actual.u16Nullable, equals(expected.u16Nullable));
  expect(actual.u32VarNullable, equals(expected.u32VarNullable));
  expect(actual.u32FixedNullable, equals(expected.u32FixedNullable));
  expect(actual.u64VarNullable, equals(expected.u64VarNullable));
  expect(actual.u64FixedNullable, equals(expected.u64FixedNullable));
  expect(actual.u64TaggedNullable, equals(expected.u64TaggedNullable));
}

int _remoteFieldTypeId(Object value, String identifier) {
  final remoteTypeDef = CompatibleStructMetadata.remoteTypeDefFor(value);
  expect(remoteTypeDef, isNotNull);
  final field = remoteTypeDef!.fields.firstWhere(
    (field) => field.identifier == identifier,
  );
  return field.fieldType.typeId;
}

bool _remoteFieldNullable(Object value, String identifier) {
  final remoteTypeDef = CompatibleStructMetadata.remoteTypeDefFor(value);
  expect(remoteTypeDef, isNotNull);
  final field = remoteTypeDef!.fields.firstWhere(
    (field) => field.identifier == identifier,
  );
  return field.fieldType.nullable;
}

void main() {
  group('unsigned generated fields', () {
    test('round trips midpoint, max, and null boundary cases', () {
      final fory = Fory();
      _registerUnsignedFields(fory);

      for (final value in <UnsignedFields>[
        _midpointUnsignedFields(),
        _maxUnsignedFields(),
        _nullUnsignedFields(),
      ]) {
        final roundTrip = fory.deserialize<UnsignedFields>(fory.serialize(value));
        _expectUnsignedFieldsEqual(roundTrip, value);
      }
    });

    test('compatible metadata records unsigned wire types and nullability', () {
      final writer = Fory(compatible: true);
      final reader = Fory(compatible: true);
      _registerUnsignedFields(writer);
      _registerUnsignedMetadataReader(reader);

      final roundTrip = reader.deserialize<UnsignedMetadataReader>(
        writer.serialize(_maxUnsignedFields()),
      );
      expect(roundTrip.u8, equals(0xff));
      expect(roundTrip.extra, equals(42));

      expect(_remoteFieldTypeId(roundTrip, 'u8'), equals(TypeIds.uint8));
      expect(_remoteFieldTypeId(roundTrip, 'u16'), equals(TypeIds.uint16));
      expect(_remoteFieldTypeId(roundTrip, 'u32_var'), equals(TypeIds.varUint32));
      expect(_remoteFieldTypeId(roundTrip, 'u32_fixed'), equals(TypeIds.uint32));
      expect(_remoteFieldTypeId(roundTrip, 'u64_var'), equals(TypeIds.varUint64));
      expect(_remoteFieldTypeId(roundTrip, 'u64_fixed'), equals(TypeIds.uint64));
      expect(
        _remoteFieldTypeId(roundTrip, 'u64_tagged'),
        equals(TypeIds.taggedUint64),
      );

      expect(_remoteFieldNullable(roundTrip, 'u8'), isFalse);
      expect(_remoteFieldNullable(roundTrip, 'u16'), isFalse);
      expect(_remoteFieldNullable(roundTrip, 'u8_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u16_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u32_var_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u32_fixed_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u64_var_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u64_fixed_nullable'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'u64_tagged_nullable'), isTrue);
    });
  });
}
