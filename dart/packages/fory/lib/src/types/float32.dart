/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import 'dart:typed_data';

double _float32OperandValue(Object other) {
  if (other is Float32) {
    return other.toDouble();
  }
  if (other is num) {
    return other.toDouble();
  }
  throw ArgumentError.value(
    other,
    'other',
    'Expected a num or Float32.',
  );
}

/// Single-precision floating-point wrapper used by the xlang type system.
///
/// [Float32] stores an IEEE 754 binary32 payload exactly. Constructing from a
/// Dart number rounds to the closest representable binary32 value, and the
/// arithmetic operators round every result back to binary32 precision before
/// returning a new wrapper.
final class Float32 implements Comparable<Float32> {
  final int _bits;

  /// Creates a value directly from IEEE 754 binary32 bits.
  const Float32.fromBits(int bits) : _bits = bits & 0xffffffff;

  /// Creates a value rounded to IEEE 754 binary32 precision.
  factory Float32(num value) => Float32.fromDouble(value.toDouble());

  /// Converts [value] to the closest representable binary32 value.
  factory Float32.fromDouble(double value) {
    final data = ByteData(4)..setFloat32(0, value, Endian.little);
    return Float32.fromBits(data.getUint32(0, Endian.little));
  }

  /// Returns the raw IEEE 754 binary32 bits for this value.
  int toBits() => _bits;

  /// Expands this binary32 value to a Dart [double].
  double toDouble() {
    final data = ByteData(4)..setUint32(0, _bits, Endian.little);
    return data.getFloat32(0, Endian.little);
  }

  /// Returns the rounded binary32 value as a Dart [double].
  double get value => toDouble();

  /// Returns a binary32-rounded sum.
  Float32 operator +(Object other) =>
      Float32(value + _float32OperandValue(other));

  /// Returns a binary32-rounded difference.
  Float32 operator -(Object other) =>
      Float32(value - _float32OperandValue(other));

  /// Returns a binary32-rounded product.
  Float32 operator *(Object other) =>
      Float32(value * _float32OperandValue(other));

  /// Returns a binary32-rounded quotient.
  Float32 operator /(Object other) =>
      Float32(value / _float32OperandValue(other));

  /// Returns a binary32-rounded remainder.
  Float32 operator %(Object other) =>
      Float32(value % _float32OperandValue(other));

  /// Returns the truncated integer quotient, matching Dart `num` semantics.
  int operator ~/(Object other) => value ~/ _float32OperandValue(other);

  /// Returns the negated binary32-rounded value.
  Float32 operator -() => Float32(-value);

  bool operator <(Object other) => value < _float32OperandValue(other);

  bool operator <=(Object other) => value <= _float32OperandValue(other);

  bool operator >(Object other) => value > _float32OperandValue(other);

  bool operator >=(Object other) => value >= _float32OperandValue(other);

  @override
  int compareTo(Float32 other) => value.compareTo(other.value);

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is Float32 && other._bits == _bits;

  @override
  int get hashCode => _bits.hashCode;

  @override
  String toString() => value.toString();
}
