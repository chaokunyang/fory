import 'package:fory/src/buffer.dart';
import 'package:fory/src/context/meta_string_codec.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/resolver/type_resolver.dart';

/// Read-side state for meta-string references in one deserialization stream.
final class MetaStringReader implements MetaStringReadSource {
  final TypeResolver _typeResolver;
  final List<EncodedMetaString> _dynamicReadMetaStrings = <EncodedMetaString>[];
  final Map<int, EncodedMetaString> _bigMetaStrings =
      <int, EncodedMetaString>{};
  final Map<int, List<EncodedMetaString>> _smallMetaStrings =
      <int, List<EncodedMetaString>>{};

  MetaStringReader(this._typeResolver);

  /// Clears dynamic ids so the reader can be reused for a new operation.
  void reset() {
    _dynamicReadMetaStrings.clear();
  }

  /// Reads one meta string, resolving dynamic references when present.
  ///
  /// Callers with a likely expected value may pass [expected] to avoid an
  /// additional map lookup in the common exact-match case.
  @override
  EncodedMetaString readMetaString(
    Buffer buffer, [
    EncodedMetaString? expected,
  ]) {
    final header = buffer.readVarUint32Small7();
    final length = header >>> 1;
    if ((header & 1) == 1) {
      return _dynamicReadMetaStrings[length - 1];
    }
    final encoded = length > metaStringSmallThreshold
        ? _readBigMetaString(buffer, length, expected)
        : _readSmallMetaString(buffer, length, expected);
    _dynamicReadMetaStrings.add(encoded);
    return encoded;
  }

  EncodedMetaString _readBigMetaString(
    Buffer buffer,
    int length,
    EncodedMetaString? expected,
  ) {
    final hash = buffer.readInt64();
    if (expected != null && expected.hash == hash) {
      buffer.skip(length);
      return expected;
    }
    final cached = _bigMetaStrings[hash];
    if (cached != null) {
      buffer.skip(length);
      return cached;
    }
    final encoded = _typeResolver.internEncodedMetaString(
      buffer.copyBytes(length),
      encoding: hash & 0xff,
    );
    _bigMetaStrings[hash] = encoded;
    return encoded;
  }

  EncodedMetaString _readSmallMetaString(
    Buffer buffer,
    int length,
    EncodedMetaString? expected,
  ) {
    if (length == 0) {
      return EncodedMetaString.empty;
    }
    final encoding = buffer.readByte() & 0xff;
    final packed = bufferReadPackedBytes(buffer, length);
    final word0 = packed.word0;
    final word1 = packed.word1;
    final word2 = packed.word2;
    final word3 = packed.word3;
    if (expected != null &&
        expected.matchesPacked(
          encoding,
          length,
          word0,
          word1,
          word2,
          word3,
        )) {
      return expected;
    }
    final hash = _smallMetaStringHash(
      encoding,
      length,
      word0,
      word1,
      word2,
      word3,
    );
    final bucket = _smallMetaStrings[hash];
    if (bucket != null) {
      for (final cached in bucket) {
        if (cached.matchesPacked(
            encoding, length, word0, word1, word2, word3)) {
          return cached;
        }
      }
    }
    final encoded = _typeResolver.internEncodedMetaString(
      bufferMaterializePackedBytes(packed),
      encoding: encoding,
    );
    (bucket ?? (_smallMetaStrings[hash] = <EncodedMetaString>[])).add(
      encoded,
    );
    return encoded;
  }
}

int _smallMetaStringHash(
  int encoding,
  int length,
  int word0,
  int word1,
  int word2,
  int word3,
) {
  var hash = 0x811c9dc5;
  hash = (hash ^ encoding) * 0x01000193;
  hash = (hash ^ length) * 0x01000193;
  hash = (hash ^ word0) * 0x01000193;
  hash = (hash ^ word1) * 0x01000193;
  hash = (hash ^ word2) * 0x01000193;
  hash = (hash ^ word3) * 0x01000193;
  return hash;
}
