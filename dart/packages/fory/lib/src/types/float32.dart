import 'dart:typed_data';

final class Float32 implements Comparable<Float32> {
  final double value;

  Float32(num value) : value = (Float32List(1)..[0] = value.toDouble())[0];

  @override
  int compareTo(Float32 other) => value.compareTo(other.value);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Float32 && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value.toString();
}
