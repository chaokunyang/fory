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

part 'signed_serializer_test.fory.dart';

const int _jsSafeIntMax = 9007199254740991;
const int _jsSafeIntMin = -9007199254740991;
const int _jsUnsafeInt = 9007199254740992;
final Int64 _int64Min = Int64.parseHex('8000000000000000');
final Int64 _int64Max = Int64.parseHex('7fffffffffffffff');

@ForyStruct()
class SignedFields {
  SignedFields();

  int plainInt = 0;

  @Int64Type(encoding: LongEncoding.varint)
  int i64VarInt = 0;

  @Int64Type(encoding: LongEncoding.fixed)
  int i64FixedInt = 0;

  @Int64Type(encoding: LongEncoding.tagged)
  int i64TaggedInt = 0;

  Int64 i64Default = Int64(0);

  @Int64Type(encoding: LongEncoding.varint)
  Int64 i64Var = Int64(0);

  @Int64Type(encoding: LongEncoding.fixed)
  Int64 i64Fixed = Int64(0);

  @Int64Type(encoding: LongEncoding.tagged)
  Int64 i64Tagged = Int64(0);

  @Int64Type(encoding: LongEncoding.varint)
  int? optionalI64VarInt;

  @Int64Type(encoding: LongEncoding.fixed)
  Int64? optionalI64Fixed;

  @Int64Type(encoding: LongEncoding.tagged)
  Int64? optionalI64Tagged;
}

@ForyStruct()
class SignedMetadataReader {
  SignedMetadataReader();

  int plainInt = 0;
}

void _registerSignedFields(Fory fory) {
  SignedSerializerTestFory.register(
    fory,
    SignedFields,
    namespace: 'test',
    typeName: 'SignedFields',
  );
}

void _registerSignedMetadataReader(Fory fory) {
  SignedSerializerTestFory.register(
    fory,
    SignedMetadataReader,
    namespace: 'test',
    typeName: 'SignedFields',
  );
}

SignedFields _smallSignedFields() {
  return SignedFields()
    ..plainInt = -1
    ..i64VarInt = -64
    ..i64FixedInt = 63
    ..i64TaggedInt = 0x3fffffff
    ..i64Default = Int64(-1)
    ..i64Var = Int64(1)
    ..i64Fixed = Int64(-0x40000000)
    ..i64Tagged = Int64(0x3fffffff)
    ..optionalI64VarInt = -128
    ..optionalI64Fixed = Int64(0x40000000)
    ..optionalI64Tagged = Int64(-0x40000001);
}

SignedFields _jsSafeBoundarySignedFields() {
  return SignedFields()
    ..plainInt = _jsSafeIntMin
    ..i64VarInt = _jsSafeIntMax
    ..i64FixedInt = _jsSafeIntMin
    ..i64TaggedInt = _jsSafeIntMax
    ..i64Default = Int64(_jsSafeIntMin)
    ..i64Var = Int64(_jsSafeIntMax)
    ..i64Fixed = Int64(_jsSafeIntMin)
    ..i64Tagged = Int64(_jsSafeIntMax)
    ..optionalI64VarInt = _jsSafeIntMin
    ..optionalI64Fixed = Int64(_jsSafeIntMax)
    ..optionalI64Tagged = Int64(_jsSafeIntMin);
}

SignedFields _fullRangeWrapperSignedFields() {
  return SignedFields()
    ..plainInt = 0
    ..i64VarInt = -0x40000001
    ..i64FixedInt = 0x40000000
    ..i64TaggedInt = -0x40000001
    ..i64Default = _int64Min
    ..i64Var = _int64Max
    ..i64Fixed = _int64Min
    ..i64Tagged = _int64Max
    ..optionalI64VarInt = 0x40000000
    ..optionalI64Fixed = _int64Max
    ..optionalI64Tagged = _int64Min;
}

SignedFields _nullSignedFields() {
  return SignedFields()
    ..plainInt = 1
    ..i64VarInt = 2
    ..i64FixedInt = 3
    ..i64TaggedInt = 4
    ..i64Default = Int64(5)
    ..i64Var = Int64(6)
    ..i64Fixed = Int64(7)
    ..i64Tagged = Int64(8)
    ..optionalI64VarInt = null
    ..optionalI64Fixed = null
    ..optionalI64Tagged = null;
}

void _expectSignedFieldsEqual(SignedFields actual, SignedFields expected) {
  expect(actual.plainInt, equals(expected.plainInt));
  expect(actual.i64VarInt, equals(expected.i64VarInt));
  expect(actual.i64FixedInt, equals(expected.i64FixedInt));
  expect(actual.i64TaggedInt, equals(expected.i64TaggedInt));
  expect(actual.i64Default, equals(expected.i64Default));
  expect(actual.i64Var, equals(expected.i64Var));
  expect(actual.i64Fixed, equals(expected.i64Fixed));
  expect(actual.i64Tagged, equals(expected.i64Tagged));
  expect(actual.optionalI64VarInt, equals(expected.optionalI64VarInt));
  expect(actual.optionalI64Fixed, equals(expected.optionalI64Fixed));
  expect(actual.optionalI64Tagged, equals(expected.optionalI64Tagged));
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
  group('signed generated fields', () {
    test('round trips int and Int64 encoding edge cases', () {
      final fory = Fory();
      _registerSignedFields(fory);

      for (final value in <SignedFields>[
        _smallSignedFields(),
        _jsSafeBoundarySignedFields(),
        _fullRangeWrapperSignedFields(),
        _nullSignedFields(),
      ]) {
        final roundTrip = fory.deserialize<SignedFields>(fory.serialize(value));
        _expectSignedFieldsEqual(roundTrip, value);
      }
    });

    test('compatible metadata records signed wire types and nullability', () {
      final writer = Fory(compatible: true);
      final reader = Fory(compatible: true);
      _registerSignedFields(writer);
      _registerSignedMetadataReader(reader);

      final roundTrip = reader.deserialize<SignedMetadataReader>(
        writer.serialize(_fullRangeWrapperSignedFields()),
      );
      expect(roundTrip.plainInt, equals(0));

      expect(
        _remoteFieldTypeId(roundTrip, 'plain_int'),
        equals(TypeIds.varInt64),
      );
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_var_int'),
        equals(TypeIds.varInt64),
      );
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_fixed_int'),
        equals(TypeIds.int64),
      );
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_tagged_int'),
        equals(TypeIds.taggedInt64),
      );
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_default'),
        equals(TypeIds.varInt64),
      );
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_var'),
        equals(TypeIds.varInt64),
      );
      expect(_remoteFieldTypeId(roundTrip, 'i64_fixed'), equals(TypeIds.int64));
      expect(
        _remoteFieldTypeId(roundTrip, 'i64_tagged'),
        equals(TypeIds.taggedInt64),
      );

      expect(_remoteFieldNullable(roundTrip, 'plain_int'), isFalse);
      expect(_remoteFieldNullable(roundTrip, 'optional_i64_var_int'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'optional_i64_fixed'), isTrue);
      expect(_remoteFieldNullable(roundTrip, 'optional_i64_tagged'), isTrue);
    });

    test('web rejects JS-unsafe Dart int fields instead of corrupting bytes',
        () {
      final fory = Fory();
      _registerSignedFields(fory);
      final value = _smallSignedFields()..i64FixedInt = _jsUnsafeInt;

      if (identical(1, 1.0)) {
        expect(() => fory.serialize(value), throwsA(isA<StateError>()));
      } else {
        final roundTrip = fory.deserialize<SignedFields>(fory.serialize(value));
        _expectSignedFieldsEqual(roundTrip, value);
      }
    });
  });
}
