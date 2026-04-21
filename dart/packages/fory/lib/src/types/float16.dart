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

double _float16OperandValue(Object other) {
  if (other is Float16) {
    return other.toDouble();
  }
  if (other is num) {
    return other.toDouble();
  }
  throw ArgumentError.value(
    other,
    'other',
    'Expected a num or Float16.',
  );
}

/// Half-precision floating-point wrapper used by the xlang type system.
///
/// [Float16] stores an IEEE 754 binary16 payload exactly. Constructing from a
/// Dart number rounds to the closest representable binary16 value, and the
/// arithmetic operators round every result back to binary16 precision before
/// returning a new wrapper.
final class Float16 implements Comparable<Float16> {
  final int _bits;

  /// Creates a value directly from IEEE 754 binary16 bits.
  const Float16.fromBits(int bits) : _bits = bits & 0xffff;

  /// Converts [value] to the closest representable binary16 value.
  factory Float16(num value) => Float16.fromDouble(value.toDouble());

  /// Converts [value] to the closest representable binary16 value.
  factory Float16.fromDouble(double value) {
    if (value.isNaN) {
      return const Float16.fromBits(0x7e00);
    }
    final data = ByteData(8)..setFloat64(0, value, Endian.little);
    final bits = data.getUint64(0, Endian.little);
    final sign = (bits >> 63) & 0x1;
    final exponent = (bits >> 52) & 0x7ff;
    final mantissa = bits & 0x000fffffffffffff;

    if (exponent == 0x7ff) {
      return Float16.fromBits((sign << 15) | 0x7c00);
    }

    final adjustedExponent = exponent - 1023 + 15;
    if (adjustedExponent >= 0x1f) {
      return Float16.fromBits((sign << 15) | 0x7c00);
    }
    if (adjustedExponent <= 0) {
      if (adjustedExponent < -10) {
        return Float16.fromBits(sign << 15);
      }
      final shifted = (mantissa | (1 << 52)) >> (43 - adjustedExponent);
      final rounded = (shifted + 1) >> 1;
      return Float16.fromBits((sign << 15) | rounded);
    }

    var roundedMantissa = mantissa + 0x0000020000000000;
    var roundedExponent = adjustedExponent;
    if ((roundedMantissa & 0x0010000000000000) != 0) {
      roundedMantissa = 0;
      roundedExponent += 1;
      if (roundedExponent >= 0x1f) {
        return Float16.fromBits((sign << 15) | 0x7c00);
      }
    }
    return Float16.fromBits(
      (sign << 15) | (roundedExponent << 10) | (roundedMantissa >> 42),
    );
  }

  /// Returns the raw IEEE 754 binary16 bits for this value.
  int toBits() => _bits;

  /// Expands this binary16 value to a Dart [double].
  double toDouble() {
    final sign = (_bits >> 15) & 0x1;
    final exponent = (_bits >> 10) & 0x1f;
    final mantissa = _bits & 0x03ff;
    if (exponent == 0) {
      if (mantissa == 0) {
        return sign == 0 ? 0.0 : -0.0;
      }
      final value = mantissa / 1024.0 * 0.00006103515625;
      return sign == 0 ? value : -value;
    }
    if (exponent == 0x1f) {
      return mantissa == 0
          ? (sign == 0 ? double.infinity : double.negativeInfinity)
          : double.nan;
    }
    final exponentValue = exponent - 15;
    final scale = exponentValue >= 0
        ? (1 << exponentValue).toDouble()
        : 1.0 / (1 << -exponentValue).toDouble();
    final value = (1.0 + mantissa / 1024.0) * scale;
    return sign == 0 ? value : -value;
  }

  /// Returns the rounded binary16 value as a Dart [double].
  double get value => toDouble();

  /// Returns a binary16-rounded sum.
  Float16 operator +(Object other) =>
      Float16(value + _float16OperandValue(other));

  /// Returns a binary16-rounded difference.
  Float16 operator -(Object other) =>
      Float16(value - _float16OperandValue(other));

  /// Returns a binary16-rounded product.
  Float16 operator *(Object other) =>
      Float16(value * _float16OperandValue(other));

  /// Returns a binary16-rounded quotient.
  Float16 operator /(Object other) =>
      Float16(value / _float16OperandValue(other));

  /// Returns a binary16-rounded remainder.
  Float16 operator %(Object other) =>
      Float16(value % _float16OperandValue(other));

  /// Returns the truncated integer quotient, matching Dart `num` semantics.
  int operator ~/(Object other) => value ~/ _float16OperandValue(other);

  /// Returns the negated binary16-rounded value.
  Float16 operator -() => Float16(-value);

  bool operator <(Object other) => value < _float16OperandValue(other);

  bool operator <=(Object other) => value <= _float16OperandValue(other);

  bool operator >(Object other) => value > _float16OperandValue(other);

  bool operator >=(Object other) => value >= _float16OperandValue(other);

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is Float16 && other._bits == _bits;

  @override
  int get hashCode => _bits.hashCode;

  @override
  int compareTo(Float16 other) => toDouble().compareTo(other.toDouble());

  @override
  String toString() => toDouble().toString();
}
