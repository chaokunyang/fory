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

import 'package:fory/fory.dart';
import 'package:test/test.dart';

part 'generated_field_order_test.fory.dart';

@ForyStruct()
class GeneratedFieldOrderStruct {
  GeneratedFieldOrderStruct();

  Float16 float16Value = const Float16.fromBits(0x3c00);
  Bfloat16 bfloat16Value = const Bfloat16.fromBits(0x3f80);
  bool boolValue = true;
  Int8 int8Value = Int8(-12);
  Uint8 uint8Value = Uint8(200);
}

String _generatedPart() {
  return File('test/generated_field_order_test.fory.dart').readAsStringSync();
}

void main() {
  group('generated field order', () {
    test('keeps bfloat16 in the 2-byte fixed-width bucket', () {
      final content = _generatedPart();

      final writeFloat16 = content.indexOf(
        'cursor0.writeFloat16(value.float16Value);',
      );
      final writeBfloat16 = content.indexOf(
        'cursor0.writeBfloat16(value.bfloat16Value);',
      );
      final writeBool = content.indexOf('cursor0.writeBool(value.boolValue);');
      final writeInt8 = content.indexOf(
        'cursor0.writeByte(value.int8Value.value);',
      );
      final writeUint8 = content.indexOf(
        'cursor0.writeUint8(value.uint8Value.value);',
      );

      final readFloat16 = content.indexOf(
        'value.float16Value = cursor0.readFloat16();',
      );
      final readBfloat16 = content.indexOf(
        'value.bfloat16Value = cursor0.readBfloat16();',
      );
      final readBool = content.indexOf('value.boolValue = cursor0.readBool();');
      final readInt8 = content.indexOf(
        'value.int8Value = Int8(cursor0.readByte());',
      );
      final readUint8 = content.indexOf(
        'value.uint8Value = Uint8(cursor0.readUint8());',
      );

      for (final marker in <int>[
        writeFloat16,
        writeBfloat16,
        writeBool,
        writeInt8,
        writeUint8,
        readFloat16,
        readBfloat16,
        readBool,
        readInt8,
        readUint8,
      ]) {
        expect(marker, greaterThanOrEqualTo(0));
      }

      expect(writeFloat16, lessThan(writeBfloat16));
      expect(writeBfloat16, lessThan(writeBool));
      expect(writeBool, lessThan(writeInt8));
      expect(writeInt8, lessThan(writeUint8));

      expect(readFloat16, lessThan(readBfloat16));
      expect(readBfloat16, lessThan(readBool));
      expect(readBool, lessThan(readInt8));
      expect(readInt8, lessThan(readUint8));
    });
  });
}
