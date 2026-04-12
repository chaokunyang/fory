import 'package:fory/src/buffer.dart';
import 'package:fory/src/meta/meta_string.dart';

abstract interface class MetaStringWriteSink {
  void writeMetaString(Buffer buffer, EncodedMetaStringInternal encoded);
}

abstract interface class MetaStringReadSource {
  EncodedMetaStringInternal readMetaString(
    Buffer buffer, [
    EncodedMetaStringInternal? expected,
  ]);
}
