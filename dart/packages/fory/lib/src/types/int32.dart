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

/// Signed 32-bit integer wrapper used by the xlang type system.
///
/// This wrapper preserves xlang `int32` and `varint32` semantics on the Dart
/// side. Arithmetic and bitwise operators normalize every result back to the
/// signed 32-bit range.
final class Int32 implements Comparable<Int32> {
  /// The normalized signed 32-bit value.
  final int value;

  /// Creates a signed 32-bit value by truncating [value] to 32 bits.
  Int32(int value) : value = value.toSigned(32);

  @override
  int compareTo(Int32 other) => value.compareTo(other.value);

  Int32 operator +(Object other) => switch (other) {
        int otherValue => Int32(value + otherValue),
        Int32 otherValue => Int32(value + otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator -(Object other) => switch (other) {
        int otherValue => Int32(value - otherValue),
        Int32 otherValue => Int32(value - otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator *(Object other) => switch (other) {
        int otherValue => Int32(value * otherValue),
        Int32 otherValue => Int32(value * otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator ~/(Object other) => switch (other) {
        int otherValue => Int32(value ~/ otherValue),
        Int32 otherValue => Int32(value ~/ otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator %(Object other) => switch (other) {
        int otherValue => Int32(value % otherValue),
        Int32 otherValue => Int32(value % otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  double operator /(Object other) => switch (other) {
        int otherValue => value / otherValue,
        Int32 otherValue => value / otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator -() => Int32(-value);

  Int32 operator ~() => Int32(~value);

  Int32 operator &(Object other) => switch (other) {
        int otherValue => Int32(value & otherValue),
        Int32 otherValue => Int32(value & otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator |(Object other) => switch (other) {
        int otherValue => Int32(value | otherValue),
        Int32 otherValue => Int32(value | otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator ^(Object other) => switch (other) {
        int otherValue => Int32(value ^ otherValue),
        Int32 otherValue => Int32(value ^ otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  Int32 operator <<(int shift) => Int32(value << shift);

  Int32 operator >>(int shift) => Int32(value >> shift);

  bool operator <(Object other) => switch (other) {
        int otherValue => value < otherValue,
        Int32 otherValue => value < otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  bool operator <=(Object other) => switch (other) {
        int otherValue => value <= otherValue,
        Int32 otherValue => value <= otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  bool operator >(Object other) => switch (other) {
        int otherValue => value > otherValue,
        Int32 otherValue => value > otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  bool operator >=(Object other) => switch (other) {
        int otherValue => value >= otherValue,
        Int32 otherValue => value >= otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int32.'),
      };

  int toInt() => value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is Int32 && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value.toString();
}
