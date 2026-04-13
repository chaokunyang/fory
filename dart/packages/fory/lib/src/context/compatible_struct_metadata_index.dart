import 'package:fory/src/resolver/type_resolver.dart';

final class CompatibleStructMetadataIndex {
  Expando<StructMetadata> _metadata =
      Expando<StructMetadata>('fory_compatible_struct_metadata');

  void remember(Object value, StructMetadata metadata) {
    _metadata[value] = metadata;
  }

  StructMetadata? metadataFor(Object value) => _metadata[value];

  void reset() {
    _metadata = Expando<StructMetadata>('fory_compatible_struct_metadata');
  }
}
