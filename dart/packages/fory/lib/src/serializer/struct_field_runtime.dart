import 'package:meta/meta.dart';

import 'package:fory/src/resolver/type_resolver.dart';

@internal
final class StructFieldRuntime {
  final int slot;
  final FieldMetadataInternal metadata;
  final DeclaredValueRuntimeInternal runtime;

  const StructFieldRuntime(this.slot, this.metadata, this.runtime);
}
