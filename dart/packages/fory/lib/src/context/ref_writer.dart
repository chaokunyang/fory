import 'package:fory/src/buffer.dart';

final class RefWriter {
  static const int nullFlag = -3;
  static const int refFlag = -2;
  static const int notNullValueFlag = -1;
  static const int refValueFlag = 0;

  Expando<int> _ids = Expando<int>('fory_write_refs');
  int _nextId = 0;

  bool writeRefOrNull(
    Buffer buffer,
    Object? value, {
    required bool trackRef,
  }) {
    if (value == null) {
      buffer.writeByte(nullFlag);
      return true;
    }
    if (!trackRef) {
      buffer.writeByte(notNullValueFlag);
      return false;
    }
    final existingId = _ids[value];
    if (existingId != null) {
      buffer.writeByte(refFlag);
      buffer.writeVarUint32(existingId);
      return true;
    }
    _ids[value] = _nextId++;
    buffer.writeByte(refValueFlag);
    return false;
  }

  bool writeNullFlag(Buffer buffer, Object? value) {
    if (value == null) {
      buffer.writeByte(nullFlag);
      return true;
    }
    return false;
  }

  void reference(Object value) {
    if (_ids[value] != null) {
      return;
    }
    _ids[value] = _nextId++;
  }

  void reset() {
    _ids = Expando<int>('fory_write_refs');
    _nextId = 0;
  }
}
