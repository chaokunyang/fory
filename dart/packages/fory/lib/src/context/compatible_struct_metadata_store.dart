import 'package:fory/src/resolver/type_resolver.dart';

final class CompatibleStructMetadataStore {
  Expando<StructMetadataInternal> _metadata =
      Expando<StructMetadataInternal>('fory_compatible_struct_metadata');

  void remember(Object value, StructMetadataInternal metadata) {
    _metadata[value] = metadata;
  }

  StructMetadataInternal? metadataFor(Object value) => _metadata[value];

  void reset() {
    _metadata =
        Expando<StructMetadataInternal>('fory_compatible_struct_metadata');
  }
}
