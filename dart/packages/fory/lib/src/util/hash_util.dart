import 'dart:convert';

import 'package:fory/src/resolver/type_resolver.dart';

int stableHash64Internal(List<int> bytes) {
  var hash = 0xcbf29ce484222325;
  for (final byte in bytes) {
    hash ^= byte;
    hash = (hash * 0x100000001b3) & 0x7fffffffffffffff;
  }
  return hash;
}

int schemaHashInternal(StructMetadataInternal metadata) {
  final parts = metadata.fields
      .map(
        (field) =>
            '${field.identifier},${field.shape.typeId},${field.shape.ref},${field.shape.nullable};',
      )
      .toList(growable: false)
    ..sort();
  return stableHash64Internal(utf8.encode(parts.join())) & 0xffffffff;
}
