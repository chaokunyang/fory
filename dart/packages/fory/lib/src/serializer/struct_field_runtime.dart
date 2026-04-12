import 'package:meta/meta.dart';

import 'package:fory/src/resolver/type_resolver.dart';

@internal
final class StructFieldRuntime {
  final int slot;
  final FieldMetadataInternal metadata;

  const StructFieldRuntime(this.slot, this.metadata);

  factory StructFieldRuntime.fromMetadata(
    int slot,
    Map<String, Object?> metadata,
  ) {
    return StructFieldRuntime(
      slot,
      FieldMetadataInternal.fromMetadata(metadata),
    );
  }
}
