import 'dart:convert';
import 'dart:typed_data';

import 'package:fory/src/types/float16.dart';

final BigInt _mask64Big = (BigInt.one << 64) - BigInt.one;
final BigInt _sevenBitMaskBig = BigInt.from(0x7f);
final BigInt _byteMaskBig = BigInt.from(0xff);

final class Buffer {
  Uint8List _bytes;
  late ByteData _view;
  int _readerIndex;
  int _writerIndex;

  Buffer([int initialCapacity = 256])
      : _bytes = Uint8List(initialCapacity),
        _readerIndex = 0,
        _writerIndex = 0 {
    _view = ByteData.sublistView(_bytes);
  }

  Buffer.wrap(Uint8List bytes)
      : _bytes = bytes,
        _view = ByteData.sublistView(bytes),
        _readerIndex = 0,
        _writerIndex = bytes.length;

  int get readableBytes => _writerIndex - _readerIndex;

  Uint8List toBytes() => Uint8List.sublistView(_bytes, 0, _writerIndex);

  void clear() {
    _readerIndex = 0;
    _writerIndex = 0;
  }

  void wrap(Uint8List bytes) {
    _bytes = bytes;
    _view = ByteData.sublistView(bytes);
    _readerIndex = 0;
    _writerIndex = bytes.length;
  }

  void ensureWritable(int additionalBytes) {
    final required = _writerIndex + additionalBytes;
    if (required <= _bytes.length) {
      return;
    }
    var newLength = _bytes.length;
    while (newLength < required) {
      newLength *= 2;
    }
    final newBytes = Uint8List(newLength);
    newBytes.setRange(0, _writerIndex, _bytes);
    _bytes = newBytes;
    _view = ByteData.sublistView(newBytes);
  }

  void skip(int length) {
    _readerIndex += length;
  }

  void writeBool(bool value) => writeUint8(value ? 1 : 0);

  bool readBool() => readUint8() != 0;

  void writeByte(int value) {
    ensureWritable(1);
    _view.setInt8(_writerIndex, value);
    _writerIndex += 1;
  }

  int readByte() {
    final value = _view.getInt8(_readerIndex);
    _readerIndex += 1;
    return value;
  }

  void writeUint8(int value) {
    ensureWritable(1);
    _view.setUint8(_writerIndex, value);
    _writerIndex += 1;
  }

  int readUint8() {
    final value = _view.getUint8(_readerIndex);
    _readerIndex += 1;
    return value;
  }

  void writeInt16(int value) {
    ensureWritable(2);
    _view.setInt16(_writerIndex, value, Endian.little);
    _writerIndex += 2;
  }

  int readInt16() {
    final value = _view.getInt16(_readerIndex, Endian.little);
    _readerIndex += 2;
    return value;
  }

  void writeUint16(int value) {
    ensureWritable(2);
    _view.setUint16(_writerIndex, value, Endian.little);
    _writerIndex += 2;
  }

  int readUint16() {
    final value = _view.getUint16(_readerIndex, Endian.little);
    _readerIndex += 2;
    return value;
  }

  void writeInt32(int value) {
    ensureWritable(4);
    _view.setInt32(_writerIndex, value, Endian.little);
    _writerIndex += 4;
  }

  int readInt32() {
    final value = _view.getInt32(_readerIndex, Endian.little);
    _readerIndex += 4;
    return value;
  }

  void writeUint32(int value) {
    ensureWritable(4);
    _view.setUint32(_writerIndex, value, Endian.little);
    _writerIndex += 4;
  }

  int readUint32() {
    final value = _view.getUint32(_readerIndex, Endian.little);
    _readerIndex += 4;
    return value;
  }

  void writeInt64(int value) {
    ensureWritable(8);
    _view.setInt64(_writerIndex, value, Endian.little);
    _writerIndex += 8;
  }

  int readInt64() {
    final value = _view.getInt64(_readerIndex, Endian.little);
    _readerIndex += 8;
    return value;
  }

  void writeUint64(int value) {
    ensureWritable(8);
    _view.setUint64(_writerIndex, value, Endian.little);
    _writerIndex += 8;
  }

  int readUint64() {
    final value = _view.getUint64(_readerIndex, Endian.little);
    _readerIndex += 8;
    return value;
  }

  void writeFloat32(double value) {
    ensureWritable(4);
    _view.setFloat32(_writerIndex, value, Endian.little);
    _writerIndex += 4;
  }

  double readFloat32() {
    final value = _view.getFloat32(_readerIndex, Endian.little);
    _readerIndex += 4;
    return value;
  }

  void writeFloat64(double value) {
    ensureWritable(8);
    _view.setFloat64(_writerIndex, value, Endian.little);
    _writerIndex += 8;
  }

  double readFloat64() {
    final value = _view.getFloat64(_readerIndex, Endian.little);
    _readerIndex += 8;
    return value;
  }

  void writeFloat16(Float16 value) => writeUint16(value.toBits());

  Float16 readFloat16() => Float16.fromBits(readUint16());

  void writeBytes(List<int> value) {
    ensureWritable(value.length);
    _bytes.setRange(_writerIndex, _writerIndex + value.length, value);
    _writerIndex += value.length;
  }

  Uint8List readBytes(int length) {
    final result = Uint8List.sublistView(
      _bytes,
      _readerIndex,
      _readerIndex + length,
    );
    _readerIndex += length;
    return result;
  }

  Uint8List copyBytes(int length) => Uint8List.fromList(readBytes(length));

  void writeUtf8(String value) {
    final bytes = utf8.encode(value);
    writeVarUint32(bytes.length);
    writeBytes(bytes);
  }

  String readUtf8() => utf8.decode(readBytes(readVarUint32()));

  void writeVarUint32(int value) {
    var remaining = value;
    while (remaining >= 0x80) {
      writeUint8((remaining & 0x7f) | 0x80);
      remaining >>>= 7;
    }
    writeUint8(remaining);
  }

  int readVarUint32() {
    var shift = 0;
    var result = 0;
    while (true) {
      final byte = readUint8();
      result |= (byte & 0x7f) << shift;
      if ((byte & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
  }

  void writeVarInt32(int value) => writeVarUint32((value << 1) ^ (value >> 31));

  int readVarInt32() {
    final value = readVarUint32();
    return (value >>> 1) ^ -(value & 1);
  }

  void writeVarUint64(int value) {
    _writeVarUint64BigInt(BigInt.from(value) & _mask64Big);
  }

  int readVarUint64() => _readVarUint64BigInt().toInt();

  void writeVarInt64(int value) {
    final signed = BigInt.from(value);
    final zigZag = ((signed << 1) ^ BigInt.from(value >> 63)) & _mask64Big;
    _writeVarUint64BigInt(zigZag);
  }

  int readVarInt64() {
    final encoded = _readVarUint64BigInt();
    final magnitude = (encoded >> 1).toInt();
    if ((encoded & BigInt.one) == BigInt.zero) {
      return magnitude;
    }
    return -magnitude - 1;
  }

  void writeTaggedInt64(int value) {
    if (value >= -0x40000000 && value <= 0x3fffffff) {
      writeInt32(value << 1);
      return;
    }
    writeUint8(0x01);
    writeInt64(value);
  }

  int readTaggedInt64() {
    final readIndex = _readerIndex;
    final first = _view.getInt32(readIndex, Endian.little);
    if ((first & 1) == 0) {
      _readerIndex = readIndex + 4;
      return first >> 1;
    }
    final value = _view.getInt64(readIndex + 1, Endian.little);
    _readerIndex = readIndex + 9;
    return value;
  }

  void writeTaggedUint64(int value) {
    final unsigned = _toUnsigned64(value);
    if (unsigned <= 0x7fffffff) {
      writeInt32((unsigned << 1) & 0xffffffff);
      return;
    }
    writeUint8(0x01);
    writeUint64(unsigned);
  }

  int readTaggedUint64() {
    final readIndex = _readerIndex;
    final first = _view.getInt32(readIndex, Endian.little);
    if ((first & 1) == 0) {
      _readerIndex = readIndex + 4;
      return first >>> 1;
    }
    final value = _view.getUint64(readIndex + 1, Endian.little);
    _readerIndex = readIndex + 9;
    return value;
  }

  void writeVarUint32Small7(int value) => writeVarUint32(value);

  int readVarUint32Small7() => readVarUint32();

  void writeVarUint32Small14(int value) => writeVarUint32(value);

  int readVarUint32Small14() => readVarUint32();

  void writeVarUint36Small(int value) => writeVarUint64(value);

  int readVarUint36Small() => readVarUint64();
}

int _toUnsigned64(int value) => (BigInt.from(value) & _mask64Big).toInt();

extension on Buffer {
  void _writeVarUint64BigInt(BigInt value) {
    var remaining = value & _mask64Big;
    for (var index = 0; index < 8; index += 1) {
      final chunk = (remaining & _sevenBitMaskBig).toInt();
      remaining >>= 7;
      if (remaining == BigInt.zero) {
        writeUint8(chunk);
        return;
      }
      writeUint8(chunk | 0x80);
    }
    writeUint8((remaining & _byteMaskBig).toInt());
  }

  BigInt _readVarUint64BigInt() {
    var shift = 0;
    var result = BigInt.zero;
    while (shift < 56) {
      final byte = readUint8();
      result |= BigInt.from(byte & 0x7f) << shift;
      if ((byte & 0x80) == 0) {
        return result;
      }
      shift += 7;
    }
    return result | ((BigInt.from(readUint8()) & _byteMaskBig) << 56);
  }
}
