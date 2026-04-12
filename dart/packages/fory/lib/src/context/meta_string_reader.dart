import 'dart:typed_data';

import 'package:fory/src/buffer.dart';
import 'package:fory/src/meta/meta_string.dart';
import 'package:fory/src/resolver/type_resolver.dart';

/// Read-side state for meta-string references in one deserialization stream.
final class MetaStringReader {
  final TypeResolver _typeResolver;
  final List<EncodedMetaStringInternal> _dynamicReadMetaStrings =
      <EncodedMetaStringInternal>[];
  final Map<int, EncodedMetaStringInternal> _bigMetaStrings =
      <int, EncodedMetaStringInternal>{};
  final Map<_SmallMetaStringKey, EncodedMetaStringInternal> _smallMetaStrings =
      <_SmallMetaStringKey, EncodedMetaStringInternal>{};

  MetaStringReader(this._typeResolver);

  /// Clears dynamic ids so the reader can be reused for a new operation.
  void reset() {
    _dynamicReadMetaStrings.clear();
  }

  /// Reads one meta string, resolving dynamic references when present.
  EncodedMetaStringInternal readMetaString(Buffer buffer) {
    final header = buffer.readVarUint32Small7();
    final length = header >>> 1;
    if ((header & 1) == 1) {
      return _dynamicReadMetaStrings[length - 1];
    }
    final encoded = length > metaStringSmallThreshold
        ? _readBigMetaString(buffer, length)
        : _readSmallMetaString(buffer, length);
    _dynamicReadMetaStrings.add(encoded);
    return encoded;
  }

  EncodedMetaStringInternal _readBigMetaString(Buffer buffer, int length) {
    final hash = buffer.readInt64();
    final cached = _bigMetaStrings[hash];
    if (cached != null) {
      buffer.skip(length);
      return cached;
    }
    final encoded = _typeResolver.internEncodedMetaString(
      Uint8List.fromList(buffer.readBytes(length)),
      encoding: hash & 0xff,
    );
    _bigMetaStrings[hash] = encoded;
    return encoded;
  }

  EncodedMetaStringInternal _readSmallMetaString(Buffer buffer, int length) {
    if (length == 0) {
      return EncodedMetaStringInternal.empty;
    }
    final encoding = buffer.readByte() & 0xff;
    final bytes = Uint8List.fromList(buffer.readBytes(length));
    final key = _SmallMetaStringKey(encoding, bytes);
    final cached = _smallMetaStrings[key];
    if (cached != null) {
      return cached;
    }
    final encoded = _typeResolver.internEncodedMetaString(
      bytes,
      encoding: encoding,
    );
    _smallMetaStrings[key] = encoded;
    return encoded;
  }
}

final class _SmallMetaStringKey {
  final int encoding;
  final Uint8List bytes;

  const _SmallMetaStringKey(this.encoding, this.bytes);

  @override
  int get hashCode => Object.hash(encoding, Object.hashAll(bytes));

  @override
  bool operator ==(Object other) {
    if (other is! _SmallMetaStringKey || other.encoding != encoding) {
      return false;
    }
    if (other.bytes.length != bytes.length) {
      return false;
    }
    for (var index = 0; index < bytes.length; index += 1) {
      if (other.bytes[index] != bytes[index]) {
        return false;
      }
    }
    return true;
  }
}
