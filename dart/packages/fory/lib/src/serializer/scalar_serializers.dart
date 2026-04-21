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

import 'package:fory/src/context/read_context.dart';
import 'package:fory/src/context/write_context.dart';
import 'package:fory/src/serializer/serializer.dart';
import 'package:fory/src/string_encoding.dart';

final class NoneSerializer extends Serializer<Null> {
  const NoneSerializer();

  @override
  bool get supportsRef => false;

  @override
  void write(WriteContext context, Null value) {}

  @override
  Null read(ReadContext context) {
    return null;
  }
}

final class StringSerializer extends Serializer<String> {
  const StringSerializer();

  @override
  bool get supportsRef => false;

  @override
  void write(WriteContext context, String value) {
    writePayload(context, value);
  }

  @override
  String read(ReadContext context) {
    return readPayload(context);
  }

  static void writePayload(WriteContext context, String value) {
    writeString(context.buffer, value);
  }

  static String readPayload(ReadContext context) {
    final header = context.buffer.readVarUint36Small();
    final encoding = header & 0x03;
    final byteLength = header >>> 2;
    return readStringFromBuffer(context.buffer, byteLength, encoding);
  }
}

final class BinarySerializer extends Serializer<Uint8List> {
  const BinarySerializer();

  @override
  bool get supportsRef => false;

  @override
  void write(WriteContext context, Uint8List value) {
    writePayload(context, value);
  }

  @override
  Uint8List read(ReadContext context) {
    return readPayload(context);
  }

  static void writePayload(WriteContext context, Uint8List value) {
    if (value.length > context.config.maxBinarySize) {
      throw StateError(
        'Binary payload exceeds ${context.config.maxBinarySize} bytes.',
      );
    }
    context.buffer.writeVarUint32(value.length);
    context.buffer.writeBytes(value);
  }

  static Uint8List readPayload(ReadContext context) {
    final size = context.buffer.readVarUint32();
    if (size > context.config.maxBinarySize) {
      throw StateError(
        'Binary payload exceeds ${context.config.maxBinarySize} bytes.',
      );
    }
    return context.buffer.copyBytes(size);
  }
}

const NoneSerializer noneSerializer = NoneSerializer();
const StringSerializer stringSerializer = StringSerializer();
const BinarySerializer binarySerializer = BinarySerializer();
