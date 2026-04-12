import 'dart:collection';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/context/meta_string_codec.dart';
import 'package:fory/src/meta/meta_string.dart';

/// Write-side state for meta-string references in one serialization stream.
final class MetaStringWriter implements MetaStringWriteSink {
  final Map<EncodedMetaStringInternal, int> _writtenMetaStrings =
      LinkedHashMap<EncodedMetaStringInternal, int>.identity();

  /// Clears dynamic ids so the writer can be reused for a new operation.
  void reset() {
    _writtenMetaStrings.clear();
  }

  /// Writes [encoded] using the stream-local meta-string table.
  @override
  void writeMetaString(Buffer buffer, EncodedMetaStringInternal encoded) {
    final existing = _writtenMetaStrings[encoded];
    if (existing != null) {
      buffer.writeVarUint32Small7(((existing + 1) << 1) | 1);
      return;
    }
    _writtenMetaStrings[encoded] = _writtenMetaStrings.length;
    _writeNewMetaString(buffer, encoded);
  }

  /// Writes [encoded] without using the stream-local meta-string table.
  void writeStandaloneMetaString(
    Buffer buffer,
    EncodedMetaStringInternal encoded,
  ) {
    _writeNewMetaString(buffer, encoded);
  }

  void _writeNewMetaString(Buffer buffer, EncodedMetaStringInternal encoded) {
    final bytes = encoded.bytes;
    buffer.writeVarUint32Small7(bytes.length << 1);
    if (bytes.isNotEmpty && bytes.length <= metaStringSmallThreshold) {
      buffer.writeByte(encoded.encoding);
    } else if (bytes.length > metaStringSmallThreshold) {
      buffer.writeInt64(encoded.hash);
    }
    buffer.writeBytes(bytes);
  }
}
