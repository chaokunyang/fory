// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

@TestOn('vm')
library;

import 'dart:typed_data';

import 'package:fory/fory.dart';
import 'package:fory/src/meta/field_info.dart';
import 'package:fory/src/meta/parsed_type_meta_entries_native.dart';
import 'package:fory/src/meta/type_def.dart';
import 'package:fory/src/meta/type_meta.dart';
import 'package:fory/src/resolver/type_resolver.dart';
import 'package:test/test.dart';

final class _CacheTestSerializer extends Serializer<Object?> {
  const _CacheTestSerializer();

  @override
  bool get supportsRef => false;

  @override
  Object? read(ReadContext context) => null;

  @override
  void write(WriteContext context, Object? value) {}
}

TypeInfo _cachedTypeInfo(Int64 header) {
  return TypeInfo(
    type: Object,
    kind: RegistrationKind.builtin,
    typeId: TypeIds.struct,
    supportsRef: false,
    needsRootRef: false,
    usesNestedTypeDefinitions: false,
    readDataAlwaysAdvances: false,
    evolving: false,
    fields: const <FieldInfo>[],
    serializer: const _CacheTestSerializer(),
    structSerializer: null,
    userTypeId: null,
    namespace: null,
    typeName: null,
    encodedNamespace: null,
    encodedTypeName: null,
    typeDef: TypeDef(
      evolving: false,
      fields: const <FieldInfo>[],
      header: header,
      encoded: Uint8List(0),
    ),
    remoteTypeDef: null,
  );
}

void main() {
  test('mixes native integral header collisions', () {
    final headers = List<Int64>.generate(
      16,
      (index) => Int64(index * 0x100000001),
    );

    expect(headers.map((header) => header.hashCode).toSet(), hasLength(1));
    expect(headers.map(typeMetaHeaderHashCode).toSet().length, greaterThan(1));

    final cache = ParsedTypeMetaCache();
    for (var index = 0; index < headers.length; index += 1) {
      final header = TypeHeader(headers[index]);
      cache.remember(header, _cachedTypeInfo(header.value));
    }
    for (var index = 0; index < headers.length; index += 1) {
      final header = TypeHeader(headers[index]);
      expect(cache.lookup(header)?.cachedTypeDefHeader, header.value);
    }
  });
}
