import 'package:fory/src/buffer.dart';
import 'package:fory/src/meta/meta_string.dart';

abstract interface class MetaStringWriteSink {
  void writeMetaString(Buffer buffer, EncodedMetaString encoded);
}

abstract interface class MetaStringReadSource {
  EncodedMetaString readMetaString(
    Buffer buffer, [
    EncodedMetaString? expected,
  ]);
}
