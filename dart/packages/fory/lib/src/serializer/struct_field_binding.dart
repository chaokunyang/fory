import 'package:meta/meta.dart';

import 'package:fory/src/resolver/type_resolver.dart';

@internal
final class StructFieldBinding {
  final int slot;
  final FieldMetadataInternal metadata;
  final DeclaredValueBindingInternal valueBinding;

  const StructFieldBinding(this.slot, this.metadata, this.valueBinding);
}
