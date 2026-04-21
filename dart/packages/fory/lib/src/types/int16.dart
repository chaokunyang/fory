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

/// Signed 16-bit integer wrapper used by the xlang type system.
///
/// The constructor truncates [value] to 16 bits using two's-complement
/// semantics, and arithmetic operators normalize every result back to the
/// signed 16-bit range.
final class Int16 implements Comparable<Int16> {
  /// The normalized signed 16-bit value.
  final int value;

  /// Creates a signed 16-bit value by truncating [value] to 16 bits.
  Int16(int value) : value = value.toSigned(16);

  @override
  int compareTo(Int16 other) => value.compareTo(other.value);

  Int16 operator +(Object other) => switch (other) {
        int otherValue => Int16(value + otherValue),
        Int16 otherValue => Int16(value + otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator -(Object other) => switch (other) {
        int otherValue => Int16(value - otherValue),
        Int16 otherValue => Int16(value - otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator *(Object other) => switch (other) {
        int otherValue => Int16(value * otherValue),
        Int16 otherValue => Int16(value * otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator ~/(Object other) => switch (other) {
        int otherValue => Int16(value ~/ otherValue),
        Int16 otherValue => Int16(value ~/ otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator %(Object other) => switch (other) {
        int otherValue => Int16(value % otherValue),
        Int16 otherValue => Int16(value % otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  double operator /(Object other) => switch (other) {
        int otherValue => value / otherValue,
        Int16 otherValue => value / otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator -() => Int16(-value);

  Int16 operator ~() => Int16(~value);

  Int16 operator &(Object other) => switch (other) {
        int otherValue => Int16(value & otherValue),
        Int16 otherValue => Int16(value & otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator |(Object other) => switch (other) {
        int otherValue => Int16(value | otherValue),
        Int16 otherValue => Int16(value | otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator ^(Object other) => switch (other) {
        int otherValue => Int16(value ^ otherValue),
        Int16 otherValue => Int16(value ^ otherValue.value),
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  Int16 operator <<(int shift) => Int16(value << shift);

  Int16 operator >>(int shift) => Int16(value >> shift);

  bool operator <(Object other) => switch (other) {
        int otherValue => value < otherValue,
        Int16 otherValue => value < otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  bool operator <=(Object other) => switch (other) {
        int otherValue => value <= otherValue,
        Int16 otherValue => value <= otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  bool operator >(Object other) => switch (other) {
        int otherValue => value > otherValue,
        Int16 otherValue => value > otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  bool operator >=(Object other) => switch (other) {
        int otherValue => value >= otherValue,
        Int16 otherValue => value >= otherValue.value,
        _ => throw ArgumentError.value(
            other, 'other', 'Expected an int or Int16.'),
      };

  int toInt() => value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is Int16 && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value.toString();
}
