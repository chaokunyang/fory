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
import 'package:test/test.dart';

void main() {
  group('xlang protocol regressions', () {
    test('deserializes NONE wire values as null', () {
      final fory = Fory();
      final bytes = Uint8List.fromList(<int>[0x02, 0xff, TypeIds.none]);

      expect(fory.deserialize<Object?>(bytes), isNull);
      expect(fory.deserialize<Null>(bytes), isNull);
    });

    test('deserializes FLOAT16_ARRAY wire values', () {
      final fory = Fory();
      final bytes = Uint8List.fromList(
        fory.serialize(
          Uint16List.fromList(<int>[0x3c00, 0xc000, 0x7e00]),
        ),
      );
      bytes[2] = TypeIds.float16Array;

      final values = fory.deserialize<Float16List>(bytes);

      expect(
        values.map((value) => value.toBits()).toList(),
        orderedEquals(<int>[0x3c00, 0xc000, 0x7e00]),
      );
    });

    test('deserializes BFLOAT16 and BFLOAT16_ARRAY wire values', () {
      final fory = Fory();
      final scalarBytes =
          Uint8List.fromList(fory.serialize(Bfloat16.fromBits(0xbf60)));
      final arrayBytes = Uint8List.fromList(
        fory.serialize(
          Bfloat16List.fromList(<Bfloat16>[
            Bfloat16.fromBits(0x3f80),
            Bfloat16.fromBits(0xbf80),
            Bfloat16.fromBits(0x7fc1),
          ]),
        ),
      );

      expect(fory.deserialize<Bfloat16>(scalarBytes).toBits(), equals(0xbf60));
      expect(
        fory
            .deserialize<Bfloat16List>(arrayBytes)
            .map((value) => value.toBits())
            .toList(),
        orderedEquals(<int>[0x3f80, 0xbf80, 0x7fc1]),
      );
    });

    test('rejects out-of-band xlang payload headers', () {
      final fory = Fory();
      final bytes = Uint8List.fromList(fory.serialize('value'));
      bytes[0] |= 0x04;

      expect(
        () => fory.deserialize<String>(bytes),
        throwsA(
          isA<StateError>().having(
            (error) => error.toString(),
            'message',
            contains('Out-of-band buffers'),
          ),
        ),
      );
    });
  });
}
