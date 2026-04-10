import 'dart:convert';
import 'dart:typed_data';

import 'package:fory/src/types/float16.dart';

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

  void writeVarInt32(int value) =>
      writeVarUint32((value << 1) ^ (value >> 31));

  int readVarInt32() {
    final value = readVarUint32();
    return (value >>> 1) ^ -(value & 1);
  }

  void writeVarUint64(int value) {
    var remaining = value;
    while (remaining >= 0x80) {
      writeUint8((remaining & 0x7f) | 0x80);
      remaining >>>= 7;
    }
    writeUint8(remaining);
  }

  int readVarUint64() {
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

  void writeVarInt64(int value) =>
      writeVarUint64((value << 1) ^ (value >> 63));

  int readVarInt64() {
    final value = readVarUint64();
    return (value >>> 1) ^ -(value & 1);
  }

  void writeVarUint32Small7(int value) => writeVarUint32(value);

  int readVarUint32Small7() => readVarUint32();

  void writeVarUint32Small14(int value) => writeVarUint32(value);

  int readVarUint32Small14() => readVarUint32();

  void writeVarUint36Small(int value) => writeVarUint64(value);

  int readVarUint36Small() => readVarUint64();
}
