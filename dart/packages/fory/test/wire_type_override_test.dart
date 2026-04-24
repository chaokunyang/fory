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
import 'package:fory/src/meta/field_type.dart';
import 'package:fory/src/serializer/compatible_struct_metadata.dart';
import 'package:test/test.dart';

part 'wire_type_override_test.fory.dart';

@ForyStruct()
class WireTypeOverrideFields {
  WireTypeOverrideFields();

  @ForyField(wireTypeId: TypeIds.uint32)
  Uint32 fixedUint32 = Uint32(0);

  @ForyField(wireTypeId: TypeIds.uint8Array)
  Uint8List uint8Array = Uint8List(0);

  @MapType(
    key: ValueType(wireTypeId: TypeIds.int32),
    value: ValueType(wireTypeId: TypeIds.uint32),
  )
  Map<Int32, Uint32> fixedMap = <Int32, Uint32>{};

  Uint8List binary = Uint8List(0);
}

@ForyStruct()
class WireTypeOverrideMetadataReader {
  WireTypeOverrideMetadataReader();

  int extra = 42;
}

void _registerWireTypeOverrideFields(Fory fory) {
  WireTypeOverrideTestFory.register(
    fory,
    WireTypeOverrideFields,
    namespace: 'test',
    typeName: 'WireTypeOverrideFields',
  );
}

void _registerWireTypeOverrideMetadataReader(Fory fory) {
  WireTypeOverrideTestFory.register(
    fory,
    WireTypeOverrideMetadataReader,
    namespace: 'test',
    typeName: 'WireTypeOverrideFields',
  );
}

int _remoteFieldTypeId(Object value, String identifier) {
  final typeDef = CompatibleStructMetadata.remoteTypeDefFor(value);
  if (typeDef == null) {
    throw StateError('No remote type definition recorded.');
  }
  final field = typeDef.fields.firstWhere(
    (candidate) => candidate.identifier == identifier,
  );
  return field.fieldType.typeId;
}

FieldType _remoteFieldType(Object value, String identifier) {
  final typeDef = CompatibleStructMetadata.remoteTypeDefFor(value);
  if (typeDef == null) {
    throw StateError('No remote type definition recorded.');
  }
  return typeDef.fields
      .firstWhere((candidate) => candidate.identifier == identifier)
      .fieldType;
}

void main() {
  group('wire type override', () {
    test('pins compatible metadata to the requested wire types', () {
      final writer = Fory(compatible: true);
      final reader = Fory(compatible: true);
      _registerWireTypeOverrideFields(writer);
      _registerWireTypeOverrideMetadataReader(reader);

      final roundTrip = reader.deserialize<WireTypeOverrideMetadataReader>(
        writer.serialize(
          WireTypeOverrideFields()
            ..fixedUint32 = Uint32(0x80000000)
            ..uint8Array = Uint8List.fromList(<int>[1, 2, 3])
            ..fixedMap = <Int32, Uint32>{
              Int32(7): Uint32(0x80000000),
            }
            ..binary = Uint8List.fromList(<int>[4, 5, 6]),
        ),
      );

      expect(roundTrip.extra, equals(42));
      expect(_remoteFieldTypeId(roundTrip, 'fixed_uint32'),
          equals(TypeIds.uint32));
      expect(
        _remoteFieldTypeId(roundTrip, 'uint8_array'),
        equals(TypeIds.uint8Array),
      );
      final fixedMapType = _remoteFieldType(roundTrip, 'fixed_map');
      expect(fixedMapType.typeId, equals(TypeIds.map));
      expect(fixedMapType.arguments[0].typeId, equals(TypeIds.int32));
      expect(fixedMapType.arguments[1].typeId, equals(TypeIds.uint32));
      expect(_remoteFieldTypeId(roundTrip, 'binary'), equals(TypeIds.binary));
    });

    test('drives fixed uint32 and uint8 array read/write paths', () {
      final fory = Fory();
      _registerWireTypeOverrideFields(fory);

      final value = WireTypeOverrideFields()
        ..fixedUint32 = Uint32(0x80000000)
        ..uint8Array = Uint8List.fromList(<int>[1, 2, 3])
        ..fixedMap = <Int32, Uint32>{
          Int32(7): Uint32(0x80000000),
        }
        ..binary = Uint8List.fromList(<int>[4, 5, 6]);

      final roundTrip = fory.deserialize<WireTypeOverrideFields>(
        fory.serialize(value),
      );

      expect(roundTrip.fixedUint32, equals(value.fixedUint32));
      expect(roundTrip.uint8Array, orderedEquals(value.uint8Array));
      expect(roundTrip.fixedMap, equals(value.fixedMap));
      expect(roundTrip.binary, orderedEquals(value.binary));
    });
  });
}
