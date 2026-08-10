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

import 'package:fory/src/context/ref_reader.dart';
import 'package:fory/src/context/ref_writer.dart';
import 'package:fory/src/memory/buffer.dart';
import 'package:test/test.dart';

void main() {
  test('unsupported fresh value does not reserve a reference id', () {
    final reader = RefReader();
    final flag = reader.readRefOrNull(
      Buffer.wrap(Uint8List.fromList(<int>[RefWriter.refValueFlag])),
    );

    expect(
      () => reader.preserveRefValue(flag, false),
      throwsA(isA<StateError>()),
    );
    expect(reader.hasPreservedRefId, isFalse);
    expect(reader.preserveRefId(), 0);
  });

  test('back reference reuses the published final owner', () {
    final reader = RefReader();
    final owner = <int>[1];
    final id = reader.preserveRefId();
    reader.setReadRef(id, owner);
    final flag = reader.readRefOrNull(
      Buffer.wrap(Uint8List.fromList(<int>[RefWriter.refFlag & 0xff, 0])),
    );

    expect(flag, RefWriter.refFlag);
    expect(identical(reader.getReadRef(), owner), isTrue);
  });
}
