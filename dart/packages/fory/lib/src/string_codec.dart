import 'dart:convert';
import 'dart:typed_data';

const int stringLatin1Encoding = 0;
const int stringUtf16Encoding = 1;
const int stringUtf8Encoding = 2;

final class EncodedStringInternal {
  final Uint8List bytes;
  final int encoding;

  const EncodedStringInternal(this.bytes, this.encoding);
}

EncodedStringInternal encodeStringInternal(String value) {
  final codeUnits = value.codeUnits;
  var isLatin1 = true;
  for (final codeUnit in codeUnits) {
    if (codeUnit > 0xff) {
      isLatin1 = false;
      break;
    }
  }
  if (isLatin1) {
    return EncodedStringInternal(
      Uint8List.fromList(codeUnits),
      stringLatin1Encoding,
    );
  }
  final bytes = Uint8List(codeUnits.length * 2);
  final view = ByteData.sublistView(bytes);
  for (var index = 0; index < codeUnits.length; index += 1) {
    view.setUint16(index * 2, codeUnits[index], Endian.little);
  }
  return EncodedStringInternal(bytes, stringUtf16Encoding);
}

String decodeStringInternal(Uint8List bytes, int encoding) {
  switch (encoding) {
    case stringLatin1Encoding:
      return latin1.decode(bytes);
    case stringUtf16Encoding:
      if (bytes.length.isOdd) {
        throw StateError(
          'Invalid UTF-16 string payload length ${bytes.length}.',
        );
      }
      final codeUnitCount = bytes.length ~/ 2;
      final codeUnits = Uint16List(codeUnitCount);
      for (var index = 0; index < codeUnitCount; index += 1) {
        final byteIndex = index * 2;
        codeUnits[index] = bytes[byteIndex] | (bytes[byteIndex + 1] << 8);
      }
      return String.fromCharCodes(codeUnits);
    case stringUtf8Encoding:
      return utf8.decode(bytes);
    default:
      throw StateError('Unsupported string encoding $encoding.');
  }
}
