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

void main() {
  group('TypeSpec leaves', () {
    test('DeclaredType stores root overrides', () {
      const spec = DeclaredType(nullable: false, ref: true, dynamic: true);
      expect(spec.nullable, isFalse);
      expect(spec.ref, isTrue);
      expect(spec.dynamic, isTrue);
    });

    test('numeric specs store encodings', () {
      const i32 = Int32Type(encoding: Encoding.fixed);
      const i64 = Int64Type(encoding: Encoding.tagged);
      const u32 = Uint32Type(encoding: Encoding.fixed);
      const u64 = Uint64Type(encoding: Encoding.tagged);

      expect(i32.encoding, equals(Encoding.fixed));
      expect(i64.encoding, equals(Encoding.tagged));
      expect(u32.encoding, equals(Encoding.fixed));
      expect(u64.encoding, equals(Encoding.tagged));
    });

    test('builtin leaves expose override flags', () {
      const stringSpec = StringType(nullable: false);
      const binarySpec = BinaryType(ref: false);
      const timestampSpec = TimestampType(dynamic: false);

      expect(stringSpec.nullable, isFalse);
      expect(binarySpec.ref, isFalse);
      expect(timestampSpec.dynamic, isFalse);
    });
  });

  group('container TypeSpec nodes', () {
    test('ListType defaults to DeclaredType elements', () {
      const spec = ListType();
      expect(spec.element, isA<DeclaredType>());
      expect(spec.nullable, isNull);
      expect(spec.ref, isNull);
      expect(spec.dynamic, isNull);
    });

    test('SetType stores nested element specs', () {
      const spec = SetType(
        element: Int32Type(encoding: Encoding.fixed, nullable: false),
        nullable: false,
      );

      expect(spec.element, isA<Int32Type>());
      expect((spec.element as Int32Type).encoding, equals(Encoding.fixed));
      expect(spec.nullable, isFalse);
    });

    test('MapType stores nested key/value specs', () {
      const spec = MapType(
        key: StringType(),
        value: ListType(
          element: Int32Type(
            nullable: true,
            encoding: Encoding.fixed,
          ),
        ),
        ref: true,
      );

      expect(spec.key, isA<StringType>());
      final value = spec.value as ListType;
      expect(value.element, isA<Int32Type>());
      expect((value.element as Int32Type).encoding, equals(Encoding.fixed));
      expect(spec.ref, isTrue);
    });
  });

  group('field sugar annotations', () {
    test('ForyField stores canonical TypeSpec', () {
      const field = ForyField(
        id: 7,
        type: MapType(
          key: StringType(),
          value: ListType(
            element: Int32Type(encoding: Encoding.fixed),
          ),
        ),
      );

      expect(field.id, equals(7));
      expect(field.type, isA<MapType>());
      expect(field.encoding, isNull);
    });

    test('ListField, SetField, and MapField store child specs', () {
      const listField = ListField(element: DeclaredType(ref: true));
      const setField = SetField(element: Uint16Type());
      const mapField = MapField(
        key: StringType(),
        value: DeclaredType(ref: true),
      );

      expect(listField.element, isA<DeclaredType>());
      expect((listField.element as DeclaredType).ref, isTrue);
      expect(setField.element, isA<Uint16Type>());
      expect(mapField.key, isA<StringType>());
      expect(mapField.value, isA<DeclaredType>());
    });
  });
}
