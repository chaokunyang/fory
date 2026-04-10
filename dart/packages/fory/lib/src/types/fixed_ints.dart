import 'dart:typed_data';

abstract class _FixedInt implements Comparable<_FixedInt> {
  final int value;

  const _FixedInt(this.value);

  @override
  int compareTo(_FixedInt other) => value.compareTo(other.value);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other.runtimeType == runtimeType &&
          other is _FixedInt &&
          other.value == value;

  @override
  int get hashCode => Object.hash(runtimeType, value);

  @override
  String toString() => value.toString();
}

final class Int8 extends _FixedInt {
  Int8(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int8List(1)..[0] = value;
    return list[0];
  }
}

final class Int16 extends _FixedInt {
  Int16(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int16List(1)..[0] = value;
    return list[0];
  }
}

final class Int32 extends _FixedInt {
  Int32(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int32List(1)..[0] = value;
    return list[0];
  }
}

final class UInt8 extends _FixedInt {
  UInt8(int value) : super(value & 0xff);
}

final class UInt16 extends _FixedInt {
  UInt16(int value) : super(value & 0xffff);
}

final class UInt32 extends _FixedInt {
  UInt32(int value) : super(value & 0xffffffff);
}
