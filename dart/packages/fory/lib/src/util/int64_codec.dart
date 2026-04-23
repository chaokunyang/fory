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

import 'package:fory/src/types/int64.dart';
import 'package:fory/src/types/uint64.dart';
import 'package:fory/src/util/int64_platform.dart';

void writeInt64LittleEndian(ByteData view, int offset, Int64 value) {
  if (useNativeInt64FastPath) {
    view.setInt64(offset, value.toInt(), Endian.little);
    return;
  }
  view.setUint32(offset, value.low32, Endian.little);
  view.setUint32(offset + 4, value.high32Unsigned, Endian.little);
}

Int64 readInt64LittleEndian(ByteData view, int offset) {
  if (useNativeInt64FastPath) {
    return Int64(view.getInt64(offset, Endian.little));
  }
  return Int64.fromWords(
    view.getUint32(offset, Endian.little),
    view.getInt32(offset + 4, Endian.little),
  );
}

void writeUint64LittleEndian(ByteData view, int offset, Uint64 value) {
  view.setUint32(offset, value.low32, Endian.little);
  view.setUint32(offset + 4, value.high32Unsigned, Endian.little);
}

Uint64 readUint64LittleEndian(ByteData view, int offset) {
  return Uint64.fromWords(
    view.getUint32(offset, Endian.little),
    view.getUint32(offset + 4, Endian.little),
  );
}

Uint64 zigZagEncodeInt64(Int64 value) {
  final encoded = (value << 1) ^ (value >> 63);
  return Uint64.fromWords(encoded.low32, encoded.high32Unsigned);
}

Int64 zigZagDecodeInt64(Uint64 encoded) {
  final magnitude = encoded >> 1;
  final decoded = Int64.fromWords(magnitude.low32, magnitude.high32Unsigned);
  if ((encoded.low32 & 1) == 0) {
    return decoded;
  }
  return -(decoded + 1);
}

void writeVarUint64Bytes(Uint64 value, void Function(int byte) writeByte) {
  var remaining = value;
  for (var shift = 0; shift < 56 && remaining > 0x7f; shift += 7) {
    writeByte((remaining.low32 & 0x7f) | 0x80);
    remaining = remaining >> 7;
  }
  writeByte(remaining.toInt());
}

Uint64 readVarUint64Bytes(int Function() readByte) {
  var shift = 0;
  var result = Uint64(0);
  while (shift < 56) {
    final byte = readByte();
    result = result | (Uint64(byte & 0x7f) << shift);
    if ((byte & 0x80) == 0) {
      return result;
    }
    shift += 7;
  }
  return result | (Uint64(readByte()) << 56);
}
