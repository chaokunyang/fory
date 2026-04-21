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

/// Signed 8-bit integer wrapper used by the xlang type system.
///
/// The constructor truncates [value] to 8 bits using two's-complement
/// semantics, so arithmetic, shifts, and bitwise operators behave like native
/// 8-bit machine operations. Operators that produce wrapped integers return a
/// new normalized [Int8]; `/` returns a Dart [double].
final class Int8 implements Comparable<Int8> {
  /// The normalized signed 8-bit value.
  final int value;

  /// Creates a signed 8-bit value by truncating [value] to 8 bits.
  Int8(int value) : value = value.toSigned(8);

  @override
  int compareTo(Int8 other) => value.compareTo(other.value);

  /// Returns a normalized sum.
  Int8 operator +(Object other) => switch (other) {
        int otherValue => Int8(value + otherValue),
        Int8 otherValue => Int8(value + otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized difference.
  Int8 operator -(Object other) => switch (other) {
        int otherValue => Int8(value - otherValue),
        Int8 otherValue => Int8(value - otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized product.
  Int8 operator *(Object other) => switch (other) {
        int otherValue => Int8(value * otherValue),
        Int8 otherValue => Int8(value * otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized integer quotient.
  Int8 operator ~/(Object other) => switch (other) {
        int otherValue => Int8(value ~/ otherValue),
        Int8 otherValue => Int8(value ~/ otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized remainder.
  Int8 operator %(Object other) => switch (other) {
        int otherValue => Int8(value % otherValue),
        Int8 otherValue => Int8(value % otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a Dart [double] quotient.
  double operator /(Object other) => switch (other) {
        int otherValue => value / otherValue,
        Int8 otherValue => value / otherValue.value,
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns the normalized negated value.
  Int8 operator -() => Int8(-value);

  /// Returns the normalized bitwise inverse.
  Int8 operator ~() => Int8(~value);

  /// Returns a normalized bitwise AND.
  Int8 operator &(Object other) => switch (other) {
        int otherValue => Int8(value & otherValue),
        Int8 otherValue => Int8(value & otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized bitwise OR.
  Int8 operator |(Object other) => switch (other) {
        int otherValue => Int8(value | otherValue),
        Int8 otherValue => Int8(value | otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized bitwise XOR.
  Int8 operator ^(Object other) => switch (other) {
        int otherValue => Int8(value ^ otherValue),
        Int8 otherValue => Int8(value ^ otherValue.value),
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns a normalized left shift.
  Int8 operator <<(int shift) => Int8(value << shift);

  /// Returns a normalized arithmetic right shift.
  Int8 operator >>(int shift) => Int8(value >> shift);

  bool operator <(Object other) => switch (other) {
        int otherValue => value < otherValue,
        Int8 otherValue => value < otherValue.value,
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  bool operator <=(Object other) => switch (other) {
        int otherValue => value <= otherValue,
        Int8 otherValue => value <= otherValue.value,
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  bool operator >(Object other) => switch (other) {
        int otherValue => value > otherValue,
        Int8 otherValue => value > otherValue.value,
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  bool operator >=(Object other) => switch (other) {
        int otherValue => value >= otherValue,
        Int8 otherValue => value >= otherValue.value,
        _ =>
          throw ArgumentError.value(other, 'other', 'Expected an int or Int8.'),
      };

  /// Returns the normalized integer value as a Dart [int].
  int toInt() => value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is Int8 && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value.toString();
}
