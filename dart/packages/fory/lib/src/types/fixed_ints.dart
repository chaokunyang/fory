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

/// Signed 8-bit integer wrapper used by the xlang type system.
///
/// Values are normalized to the `[-128, 127]` range at construction time.
final class Int8 extends _FixedInt {
  /// Creates a normalized signed 8-bit value.
  Int8(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int8List(1)..[0] = value;
    return list[0];
  }
}

/// Signed 16-bit integer wrapper used by the xlang type system.
///
/// Values are normalized to the `[-32768, 32767]` range at construction time.
final class Int16 extends _FixedInt {
  /// Creates a normalized signed 16-bit value.
  Int16(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int16List(1)..[0] = value;
    return list[0];
  }
}

/// Signed 32-bit integer wrapper used by the xlang type system.
///
/// Values are normalized to the signed 32-bit range at construction time.
final class Int32 extends _FixedInt {
  /// Creates a normalized signed 32-bit value.
  Int32(int value) : super(_normalize(value));

  static int _normalize(int value) {
    final list = Int32List(1)..[0] = value;
    return list[0];
  }
}

/// Unsigned 8-bit integer wrapper used by the xlang type system.
final class UInt8 extends _FixedInt {
  /// Creates a normalized unsigned 8-bit value.
  UInt8(int value) : super(value & 0xff);
}

/// Unsigned 16-bit integer wrapper used by the xlang type system.
final class UInt16 extends _FixedInt {
  /// Creates a normalized unsigned 16-bit value.
  UInt16(int value) : super(value & 0xffff);
}

/// Unsigned 32-bit integer wrapper used by the xlang type system.
final class UInt32 extends _FixedInt {
  /// Creates a normalized unsigned 32-bit value.
  UInt32(int value) : super(value & 0xffffffff);
}
