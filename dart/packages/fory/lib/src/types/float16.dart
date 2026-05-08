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

import 'dart:collection';
import 'dart:typed_data';

const int _float16ImplicitMantissaBit = 0x0010000000000000;
const int _float16RoundIncrement = 0x0000020000000000;
const int _float16MantissaDivisor = 0x0000040000000000;

final ByteData _float16Float64Data = ByteData(8);

/// Converts [value] to IEEE 754 binary16 bits.
int toFloat16Bits(double value) {
  if (value.isNaN) {
    return 0x7e00;
  }
  _float16Float64Data.setFloat64(0, value, Endian.little);
  final low32 = _float16Float64Data.getUint32(0, Endian.little);
  final high32 = _float16Float64Data.getUint32(4, Endian.little);
  final sign = (high32 >>> 31) & 0x1;
  final exponent = (high32 >>> 20) & 0x7ff;
  final mantissa = ((high32 & 0x000fffff) * 0x100000000) + low32;

  if (exponent == 0x7ff) {
    return (sign << 15) | 0x7c00;
  }

  final adjustedExponent = exponent - 1023 + 15;
  if (adjustedExponent >= 0x1f) {
    return (sign << 15) | 0x7c00;
  }
  if (adjustedExponent <= 0) {
    if (adjustedExponent < -10) {
      return sign << 15;
    }
    final shifted = (mantissa + _float16ImplicitMantissaBit) ~/
        _pow2Int(43 - adjustedExponent);
    final rounded = (shifted + 1) ~/ 2;
    return (sign << 15) | rounded;
  }

  var roundedMantissa = mantissa + _float16RoundIncrement;
  var roundedExponent = adjustedExponent;
  if (roundedMantissa >= _float16ImplicitMantissaBit) {
    roundedMantissa = 0;
    roundedExponent += 1;
    if (roundedExponent >= 0x1f) {
      return (sign << 15) | 0x7c00;
    }
  }
  return (sign << 15) |
      (roundedExponent << 10) |
      (roundedMantissa ~/ _float16MantissaDivisor);
}

/// Expands IEEE 754 binary16 [bits] to a Dart [double].
double fromFloat16Bits(int bits) {
  final sign = (bits >> 15) & 0x1;
  final exponent = (bits >> 10) & 0x1f;
  final mantissa = bits & 0x03ff;
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

int _pow2Int(int exponent) {
  var result = 1;
  for (var index = 0; index < exponent; index += 1) {
    result *= 2;
  }
  return result;
}

/// Fixed-length contiguous storage for binary16 values.
///
/// [Float16List] stores raw binary16 bits but exposes elements as Dart
/// [double] values. Each element occupies exactly two bytes, backed by a
/// contiguous [ByteBuffer], so serializers can write or read the list without
/// repacking each element.
final class Float16List extends ListBase<double> {
  /// The number of bytes used by one binary16 element.
  static const int bytesPerElement = Uint16List.bytesPerElement;

  final Uint16List _storage;

  /// Creates a zero-initialized list with [length] binary16 elements.
  Float16List(int length) : _storage = Uint16List(length);

  Float16List._(this._storage);

  /// Copies [values] into a new contiguous binary16 list.
  factory Float16List.fromList(Iterable<double> values) {
    final copied = values.toList(growable: false);
    final result = Float16List(copied.length);
    for (var index = 0; index < copied.length; index += 1) {
      result[index] = copied[index];
    }
    return result;
  }

  /// Creates a zero-copy view over [buffer].
  ///
  /// [offsetInBytes] must be aligned to [bytesPerElement]. When [length] is
  /// omitted, the view spans the remaining aligned binary16 values.
  factory Float16List.view(
    ByteBuffer buffer, [
    int offsetInBytes = 0,
    int? length,
  ]) {
    return Float16List._(Uint16List.view(buffer, offsetInBytes, length));
  }

  /// Creates a zero-copy element-range view of [data].
  ///
  /// [start] and [end] are measured in binary16 elements, not bytes.
  factory Float16List.sublistView(TypedData data, [int start = 0, int? end]) {
    if (data.lengthInBytes % bytesPerElement != 0) {
      throw ArgumentError.value(
        data,
        'data',
        'Expected typed data aligned to $bytesPerElement-byte elements.',
      );
    }
    final elementLength = data.lengthInBytes ~/ bytesPerElement;
    final actualEnd = RangeError.checkValidRange(start, end, elementLength);
    return Float16List.view(
      data.buffer,
      data.offsetInBytes + start * bytesPerElement,
      actualEnd - start,
    );
  }

  /// Returns the shared backing buffer for this list.
  ByteBuffer get buffer => _storage.buffer;

  /// Returns the size in bytes of each binary16 element.
  int get elementSizeInBytes => bytesPerElement;

  /// Returns the byte length of this list view.
  int get lengthInBytes => _storage.lengthInBytes;

  /// Returns the byte offset of this view in [buffer].
  int get offsetInBytes => _storage.offsetInBytes;

  /// Returns the number of binary16 elements in this fixed-length list.
  @override
  int get length => _storage.length;

  /// Throws because [Float16List] has a fixed length.
  @override
  set length(int newLength) {
    throw UnsupportedError('Float16List has a fixed length.');
  }

  /// Reads the element at [index].
  @override
  double operator [](int index) => fromFloat16Bits(_storage[index]);

  /// Stores [value] at [index].
  @override
  void operator []=(int index, double value) {
    _storage[index] = toFloat16Bits(value);
  }
}
