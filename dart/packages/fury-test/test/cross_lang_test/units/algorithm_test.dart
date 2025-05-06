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

// @Skip()
library;

import 'dart:io';
import 'dart:typed_data';
import 'package:checks/checks.dart';
import 'package:fury/fury.dart';
import 'package:fury/fury_test.dart';
import 'package:fury_test/entity/complex_obj_1.dart';
import 'package:fury_test/util/cross_lang_util.dart';
import 'package:fury_test/util/test_file_util.dart';
import 'package:test/test.dart';

void main() {
  group('Cross-language hash algorithms', () {
    test('MurmurHash3 (128x64) cross-language match', () {
      Uint8List bytes = Uint8List(16);
      bytes[0] = 0x01;
      bytes[1] = 0x02;
      bytes[2] = 0x08;
      var hashPair = Murmur3Hash.hash128x64(bytes, 3);
      ByteData byteData = ByteData.view(bytes.buffer);
      byteData.setUint64(0, hashPair.$1, Endian.little);
      byteData.setUint64(8, hashPair.$2, Endian.little);
      File file = TestFileUtil.getWriteFile('test_murmurhash3.data', bytes);
      bool exeRes = CrossLangUtil.executeWithPython('test_murmurhash3', file.path);
      check(exeRes).isTrue();
    });

    test('struct hash cross-language match', () {
      Fury fury = Fury(
        refTracking: true,
      );
      fury.register($ComplexObject1, "test.ComplexObject1");
      var hashPair = fury.getStructHashPair(ComplexObject1);
      ByteData byteData = ByteData(4);
      byteData.setUint32(0, hashPair.fromFuryHash, Endian.little);
      File file = TestFileUtil.getWriteFile('test_struct_hash', byteData.buffer.asUint8List());
      bool exeRes = CrossLangUtil.executeWithPython('test_struct_hash', file.path);
      check(exeRes).isTrue();
    });
  });
}
