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

import 'dart:convert';
import 'dart:typed_data';

import 'package:fory/fory.dart';
import 'package:fory/src/string_encoding.dart';
import 'package:test/test.dart';

void main() {
  group('string payload encoding', () {
    test('writes latin1 payloads for ascii and latin1 strings', () {
      final cases = <String>[
        '',
        'a',
        'hello world',
        'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',
        'café',
        'résumé',
        'Ñoño',
        '\u00A0\u00FF',
        'hello \u00E9 world',
        'line1\nline2\rline3\tline4',
        _repeat('ÿ', 40),
      ];

      for (final value in cases) {
        final payload = _writeAndReadPayload(value);
        expect(payload.encoding, equals(stringLatin1Encoding), reason: value);
        expect(payload.byteLength, equals(value.length), reason: value);
        expect(payload.decoded, equals(value), reason: value);
      }
    });

    test('writes utf8 payloads for unicode, mixed, and large strings', () {
      final cases = <String>[
        '你好',
        'Hello 世界',
        'こんにちは',
        '안녕하세요',
        'Привет',
        '😀😁😂',
        'hello café 你好',
        'test\u00E9test你test',
        '🎉hello世界café🎊',
        _repeat('你', 30),
        '${_repeat('a', 32)}你好${_repeat('b', 32)}',
        '${_repeat('x', 500)}${_repeat('你', 250)}',
      ];

      for (final value in cases) {
        final payload = _writeAndReadPayload(value);
        expect(payload.encoding, equals(stringUtf8Encoding), reason: value);
        expect(
          payload.byteLength,
          equals(utf8.encode(value).length),
          reason: value,
        );
        expect(payload.decoded, equals(value), reason: value);
      }
    });

    test('normalizes unmatched surrogates to replacement characters', () {
      final cases = <({String expected, String input})>[
        (input: '\uD800', expected: '\uFFFD'),
        (input: 'x\uD800y', expected: 'x\uFFFDy'),
        (input: '\uDC00', expected: '\uFFFD'),
        (input: '\uD83Dabc', expected: '\uFFFDabc'),
        (input: 'abc\uDE00', expected: 'abc\uFFFD'),
      ];

      for (final testCase in cases) {
        final payload = _writeAndReadPayload(testCase.input);
        expect(
          payload.encoding,
          equals(stringUtf8Encoding),
          reason: testCase.input.runes.toList().toString(),
        );
        expect(payload.decoded, equals(testCase.expected));
      }
    });

    test('reads utf16 payloads from cross-language wire format', () {
      final cases = <String>[
        '',
        '你好',
        'Hello 世界',
        'こんにちは',
        '🎉🎊',
        'alpha\u0000omega',
      ];

      for (final value in cases) {
        final utf16Bytes = _utf16LeBytes(value);
        final buffer = Buffer();
        buffer.writeVarUint36Small(
          (utf16Bytes.length << 2) | stringUtf16Encoding,
        );
        buffer.writeBytes(utf16Bytes);

        final payload = _readPayload(buffer.toBytes());
        expect(payload.encoding, equals(stringUtf16Encoding), reason: value);
        expect(payload.byteLength, equals(utf16Bytes.length), reason: value);
        expect(payload.decoded, equals(value), reason: value);
      }
    });

    test('handles payload header boundaries and utf8 header compaction', () {
      final cases =
          <({int byteLength, int encoding, int headerLength, String value})>[
        (
          value: _repeat('a', 31),
          encoding: stringLatin1Encoding,
          byteLength: 31,
          headerLength: 1
        ),
        (
          value: _repeat('a', 32),
          encoding: stringLatin1Encoding,
          byteLength: 32,
          headerLength: 2
        ),
        (
          value: _repeat('😀', 7),
          encoding: stringUtf8Encoding,
          byteLength: 28,
          headerLength: 1
        ),
        (
          value: _repeat('😀', 8),
          encoding: stringUtf8Encoding,
          byteLength: 32,
          headerLength: 2
        ),
        (
          value: _repeat('你', 31),
          encoding: stringUtf8Encoding,
          byteLength: 93,
          headerLength: 2
        ),
      ];

      for (final testCase in cases) {
        final payload = _writeAndReadPayload(testCase.value);
        expect(payload.encoding, equals(testCase.encoding),
            reason: testCase.value);
        expect(payload.byteLength, equals(testCase.byteLength),
            reason: testCase.value);
        expect(
          payload.bytes.length - payload.byteLength,
          equals(testCase.headerLength),
          reason: testCase.value,
        );
        expect(payload.decoded, equals(testCase.value), reason: testCase.value);
      }
    });

    test('reuses the same buffer across a varied string sequence', () {
      final buffer = Buffer();
      final cases = <({String expected, String input})>[
        (input: 'short', expected: 'short'),
        (input: _repeat('a', 1000), expected: _repeat('a', 1000)),
        (input: 'short again', expected: 'short again'),
        (input: 'café', expected: 'café'),
        (input: '你好', expected: '你好'),
        (input: _repeat('é', 500), expected: _repeat('é', 500)),
        (input: 'abc\uDE00', expected: 'abc\uFFFD'),
        (input: 'final 😀 test', expected: 'final 😀 test'),
      ];

      for (final testCase in cases) {
        buffer.clear();
        writeString(buffer, testCase.input);
        final payload = _readPayload(buffer.toBytes());
        expect(payload.decoded, equals(testCase.expected));
      }
    });
  });

  group('string serializer', () {
    test('round-trips root strings across unicode categories', () {
      final fory = Fory();
      final cases = <({String expected, String input})>[
        (input: '', expected: ''),
        (input: 'Apache Fory', expected: 'Apache Fory'),
        (input: 'café', expected: 'café'),
        (input: '你好，Fory🙂', expected: '你好，Fory🙂'),
        (input: 'hello café 你好', expected: 'hello café 你好'),
        (input: '\n\r\t', expected: '\n\r\t'),
        (input: '\u0000', expected: '\u0000'),
        (input: _repeat('😀', 64), expected: _repeat('😀', 64)),
        (
          input: '${_repeat('x', 500)}${_repeat('你', 500)}',
          expected: '${_repeat('x', 500)}${_repeat('你', 500)}'
        ),
        (input: 'x\uD800y', expected: 'x\uFFFDy'),
      ];

      for (final testCase in cases) {
        final roundTrip =
            fory.deserialize<String>(fory.serialize(testCase.input));
        expect(roundTrip, equals(testCase.expected));
      }
    });

    test('round-trips string collections with mixed content', () {
      final fory = Fory();
      final values = <String>[
        '',
        'alpha',
        'café',
        '你好',
        '😀',
        'line1\nline2',
        'x\uD800y',
        _repeat('你', 64),
      ];
      final expected = <String>[
        '',
        'alpha',
        'café',
        '你好',
        '😀',
        'line1\nline2',
        'x\uFFFDy',
        _repeat('你', 64),
      ];

      final roundTrip = fory.deserialize<Object?>(
        fory.serialize(values, trackRef: true),
      ) as List<Object?>;

      expect(roundTrip, orderedEquals(expected));
    });

    test('reuses the same fory instance and target buffer across strings', () {
      final fory = Fory();
      final buffer = Buffer();
      final cases = <({String expected, String input})>[
        (input: 'short', expected: 'short'),
        (input: _repeat('a', 1000), expected: _repeat('a', 1000)),
        (input: 'こんにちは', expected: 'こんにちは'),
        (input: 'abc\uDE00', expected: 'abc\uFFFD'),
        (input: 'final test', expected: 'final test'),
      ];

      for (final testCase in cases) {
        fory.serializeTo(testCase.input, buffer);
        final roundTrip = fory.deserializeFrom<String>(buffer);
        expect(roundTrip, equals(testCase.expected));
        expect(buffer.readableBytes, equals(0));
      }
    });
  });
}

({Uint8List bytes, int encoding, int byteLength, String decoded})
    _writeAndReadPayload(
  String value,
) {
  final buffer = Buffer();
  writeString(buffer, value);
  return _readPayload(buffer.toBytes());
}

({Uint8List bytes, int encoding, int byteLength, String decoded}) _readPayload(
  Uint8List bytes,
) {
  final buffer = Buffer.wrap(Uint8List.fromList(bytes));
  final header = buffer.readVarUint36Small();
  final encoding = header & 0x03;
  final byteLength = header >>> 2;
  final decoded = readStringFromBuffer(buffer, byteLength, encoding);
  expect(buffer.readableBytes, equals(0));
  return (
    bytes: bytes,
    encoding: encoding,
    byteLength: byteLength,
    decoded: decoded,
  );
}

Uint8List _utf16LeBytes(String value) {
  final codeUnits = value.codeUnits;
  final bytes = Uint8List(codeUnits.length * 2);
  final view = ByteData.sublistView(bytes);
  for (var index = 0; index < codeUnits.length; index += 1) {
    view.setUint16(index * 2, codeUnits[index], Endian.little);
  }
  return bytes;
}

String _repeat(String value, int count) =>
    List<String>.filled(count, value).join();
