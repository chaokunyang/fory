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

package org.apache.fory.json.reader;

import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldNameHash;
import org.apache.fory.json.meta.JsonFieldTable;
import org.apache.fory.serializer.StringSerializer;

public final class Latin1StringJsonReader extends JsonReader {
  private static final byte[] EMPTY_BYTES = new byte[0];

  private byte[] input;

  public Latin1StringJsonReader() {
    input = EMPTY_BYTES;
  }

  public Latin1StringJsonReader(String input) {
    reset(input);
  }

  public Latin1StringJsonReader reset(String input) {
    if (!StringSerializer.isBytesBackedString()) {
      throw new IllegalStateException("Latin1StringJsonReader requires byte-backed strings");
    }
    byte coder = StringSerializer.getStringCoder(input);
    if (!StringSerializer.isLatin1Coder(coder)) {
      throw new IllegalArgumentException("Latin1StringJsonReader requires a Latin1 string");
    }
    this.input = StringSerializer.getStringBytes(input);
    position = 0;
    return this;
  }

  public void clear() {
    input = EMPTY_BYTES;
    position = 0;
  }

  public boolean consumeToken(char expected) {
    skipWhitespaceFast();
    if (position < input.length && (input[position] & 0xFF) == expected) {
      position++;
      return true;
    }
    return false;
  }

  public boolean consumeNextToken(char expected) {
    if (position < input.length && (input[position] & 0xFF) == expected) {
      position++;
      return true;
    }
    return consumeToken(expected);
  }

  public void expectToken(char expected) {
    if (!consumeToken(expected)) {
      throw error("Expected '" + expected + "'");
    }
  }

  public void expectNextToken(char expected) {
    if (position < input.length && (input[position] & 0xFF) == expected) {
      position++;
      return;
    }
    expectToken(expected);
  }

  public boolean tryReadNullToken() {
    skipWhitespaceFast();
    if (startsWithAscii("null")) {
      position += 4;
      return true;
    }
    return false;
  }

  public boolean readBooleanValue() {
    skipWhitespaceFast();
    if (startsWithAscii("true")) {
      position += 4;
      return true;
    } else if (startsWithAscii("false")) {
      position += 5;
      return false;
    }
    throw error("Expected boolean");
  }

  public int readIntValue() {
    skipWhitespaceFast();
    int start = position;
    int result = 0;
    int limit = -Integer.MAX_VALUE;
    boolean negative = false;
    if (position < input.length && input[position] == '-') {
      negative = true;
      limit = Integer.MIN_VALUE;
      position++;
    }
    if (position >= input.length) {
      throw error("Expected digit");
    }
    int ch = input[position] & 0xFF;
    if (ch == '0') {
      position++;
      rejectLeadingDigitFast();
      rejectFractionOrExponentFast();
      return 0;
    }
    if (ch < '1' || ch > '9') {
      throw error("Expected digit");
    }
    int multmin = limit / 10;
    while (position < input.length) {
      ch = input[position] & 0xFF;
      if (ch < '0' || ch > '9') {
        break;
      }
      int digit = ch - '0';
      if (result < multmin) {
        throw error("Integer overflow");
      }
      result *= 10;
      if (result < limit + digit) {
        throw error("Integer overflow");
      }
      result -= digit;
      position++;
    }
    if (start == position || (negative && start + 1 == position)) {
      throw error("Expected digit");
    }
    rejectFractionOrExponentFast();
    return negative ? result : -result;
  }

  public long readLongValue() {
    skipWhitespaceFast();
    int start = position;
    long result = 0;
    long limit = -Long.MAX_VALUE;
    boolean negative = false;
    if (position < input.length && input[position] == '-') {
      negative = true;
      limit = Long.MIN_VALUE;
      position++;
    }
    if (position >= input.length) {
      throw error("Expected digit");
    }
    int ch = input[position] & 0xFF;
    if (ch == '0') {
      position++;
      rejectLeadingDigitFast();
      rejectFractionOrExponentFast();
      return 0;
    }
    if (ch < '1' || ch > '9') {
      throw error("Expected digit");
    }
    long multmin = limit / 10;
    while (position < input.length) {
      ch = input[position] & 0xFF;
      if (ch < '0' || ch > '9') {
        break;
      }
      int digit = ch - '0';
      if (result < multmin) {
        throw error("Long overflow");
      }
      result *= 10;
      if (result < limit + digit) {
        throw error("Long overflow");
      }
      result -= digit;
      position++;
    }
    if (start == position || (negative && start + 1 == position)) {
      throw error("Expected digit");
    }
    rejectFractionOrExponentFast();
    return negative ? result : -result;
  }

  @Override
  public int readFieldNameInt() {
    skipWhitespaceFast();
    int nameStart = position;
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int result = 0;
    int limit = -Integer.MAX_VALUE;
    boolean negative = false;
    if (position < input.length && input[position] == '-') {
      negative = true;
      limit = Integer.MIN_VALUE;
      position++;
    }
    if (position >= input.length) {
      throw error("Unterminated string");
    }
    int ch = input[position] & 0xFF;
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameInt();
    }
    if (ch == '0') {
      position++;
      return readZeroIntName(nameStart);
    }
    if (ch < '1' || ch > '9') {
      throw error("Expected integer field name");
    }
    int multmin = limit / 10;
    do {
      int digit = ch - '0';
      if (result < multmin) {
        throw error("Integer overflow");
      }
      result *= 10;
      if (result < limit + digit) {
        throw error("Integer overflow");
      }
      result -= digit;
      position++;
      if (position >= input.length) {
        throw error("Unterminated string");
      }
      ch = input[position] & 0xFF;
    } while (ch >= '0' && ch <= '9');
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameInt();
    }
    if (ch != '"') {
      throw error("Expected integer field name");
    }
    position++;
    return negative ? result : -result;
  }

  @Override
  public long readFieldNameLong() {
    skipWhitespaceFast();
    int nameStart = position;
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    long result = 0;
    long limit = -Long.MAX_VALUE;
    boolean negative = false;
    if (position < input.length && input[position] == '-') {
      negative = true;
      limit = Long.MIN_VALUE;
      position++;
    }
    if (position >= input.length) {
      throw error("Unterminated string");
    }
    int ch = input[position] & 0xFF;
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameLong();
    }
    if (ch == '0') {
      position++;
      return readZeroLongName(nameStart);
    }
    if (ch < '1' || ch > '9') {
      throw error("Expected long field name");
    }
    long multmin = limit / 10;
    do {
      int digit = ch - '0';
      if (result < multmin) {
        throw error("Long overflow");
      }
      result *= 10;
      if (result < limit + digit) {
        throw error("Long overflow");
      }
      result -= digit;
      position++;
      if (position >= input.length) {
        throw error("Unterminated string");
      }
      ch = input[position] & 0xFF;
    } while (ch >= '0' && ch <= '9');
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameLong();
    }
    if (ch != '"') {
      throw error("Expected long field name");
    }
    position++;
    return negative ? result : -result;
  }

  @Override
  protected int length() {
    return input.length;
  }

  @Override
  protected char charAt(int index) {
    return (char) (input[index] & 0xFF);
  }

  @Override
  public String readString() {
    skipWhitespaceFast();
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    while (position < input.length) {
      int ch = input[position++] & 0xFF;
      if (ch == '"') {
        return newLatin1String(start, position - 1);
      } else if (ch == '\\') {
        StringBuilder builder = new StringBuilder(input.length - start);
        appendLatin1(builder, start, position - 1);
        appendEscape(builder);
        return readStringTail(builder);
      } else if (ch < 0x20) {
        throw error("Control character in string");
      }
    }
    throw error("Unterminated string");
  }

  @Override
  public JsonFieldInfo readField(JsonFieldTable table) {
    return table.get(readFieldNameHash());
  }

  @Override
  public int readFieldIndex(JsonFieldTable table) {
    return table.index(readFieldNameHash());
  }

  @Override
  public int readFieldIndex(JsonFieldTable table, long expectedHash, int expectedIndex) {
    long hash = readFieldNameHash();
    return hash == expectedHash ? expectedIndex : table.index(hash);
  }

  @Override
  public long readFieldNameHash() {
    return readQuotedStringHash();
  }

  public boolean tryReadFieldNameColon(long expectedHash, int expectedLength) {
    int mark = position;
    skipWhitespaceFast();
    byte[] bytes = input;
    int offset = position;
    int end = offset + expectedLength + 1;
    if (end < bytes.length && bytes[offset++] == '"') {
      long value = 0;
      for (int i = 0; i < expectedLength; i++) {
        int ch = bytes[offset++] & 0xFF;
        if (ch == 0 || ch == '"' || ch == '\\' || ch < 0x20) {
          position = mark;
          return false;
        }
        value = JsonFieldNameHash.value(value, i, (char) ch);
      }
      if (value == expectedHash && bytes[offset] == '"') {
        position = offset + 1;
        expectNextToken(':');
        return true;
      }
    }
    position = mark;
    return false;
  }

  @Override
  public long readStringHash() {
    return readQuotedStringHash();
  }

  private long readQuotedStringHash() {
    skipWhitespaceFast();
    byte[] bytes = input;
    int length = bytes.length;
    if (position >= length || bytes[position++] != '"') {
      throw error("Expected string");
    }
    long hash = JsonFieldNameHash.MAGIC_HASH_CODE;
    long value = 0;
    int nameLength = 0;
    boolean latin1 = true;
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        return JsonFieldNameHash.finish(hash, value, nameLength, latin1);
      }
      if (ch == '\\') {
        char escaped = readEscapedFieldNameChar();
        if (Character.isHighSurrogate(escaped)) {
          if (latin1) {
            hash = JsonFieldNameHash.hashPacked(value, nameLength);
            latin1 = false;
          }
          hash = JsonFieldNameHash.update(hash, escaped);
          nameLength++;
          if (position + 2 > length() || charAt(position) != '\\' || charAt(position + 1) != 'u') {
            throw error("Unpaired high surrogate escape");
          }
          position += 2;
          char low = readUnicodeEscape();
          if (!Character.isLowSurrogate(low)) {
            throw error("Unpaired high surrogate escape");
          }
          hash = JsonFieldNameHash.update(hash, low);
          nameLength++;
        } else if (Character.isLowSurrogate(escaped)) {
          throw error("Unpaired low surrogate escape");
        } else {
          if (latin1) {
            if (escaped != 0 && nameLength < Long.BYTES) {
              value = JsonFieldNameHash.value(value, nameLength, escaped);
              nameLength++;
              continue;
            }
            hash = JsonFieldNameHash.hashPacked(value, nameLength);
            latin1 = false;
          }
          hash = JsonFieldNameHash.update(hash, escaped);
          nameLength++;
        }
        continue;
      }
      if (ch < 0x20) {
        throw error("Control character in string");
      }
      if (latin1) {
        if (ch != 0 && nameLength < Long.BYTES) {
          value = JsonFieldNameHash.value(value, nameLength, (char) ch);
          nameLength++;
          continue;
        }
        hash = JsonFieldNameHash.hashPacked(value, nameLength);
        latin1 = false;
      }
      hash = JsonFieldNameHash.update(hash, (char) ch);
      nameLength++;
    }
    throw error("Unterminated string");
  }

  @Override
  protected String slice(int start, int end) {
    return newLatin1String(start, end);
  }

  private String newLatin1String(int start, int end) {
    int length = end - start;
    byte[] bytes = new byte[length];
    System.arraycopy(input, start, bytes, 0, length);
    return StringSerializer.newLatin1StringZeroCopy(bytes);
  }

  private String readStringTail(StringBuilder builder) {
    while (position < input.length) {
      int ch = input[position++] & 0xFF;
      if (ch == '"') {
        return builder.toString();
      } else if (ch == '\\') {
        appendEscape(builder);
      } else if (ch < 0x20) {
        throw error("Control character in string");
      } else {
        builder.append((char) ch);
      }
    }
    throw error("Unterminated string");
  }

  private void appendLatin1(StringBuilder builder, int start, int end) {
    for (int i = start; i < end; i++) {
      builder.append((char) (input[i] & 0xFF));
    }
  }

  private void skipWhitespaceFast() {
    while (position < input.length) {
      int ch = input[position] & 0xFF;
      if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
        position++;
      } else {
        return;
      }
    }
  }

  private boolean startsWithAscii(String value) {
    int end = position + value.length();
    if (end > input.length) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if ((input[position + i] & 0xFF) != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private void rejectLeadingDigitFast() {
    if (position < input.length) {
      int ch = input[position] & 0xFF;
      if (ch >= '0' && ch <= '9') {
        throw error("Leading zero in number");
      }
    }
  }

  private void rejectFractionOrExponentFast() {
    if (position < input.length) {
      int ch = input[position] & 0xFF;
      if (ch == '.' || ch == 'e' || ch == 'E') {
        throw error("Expected integer");
      }
    }
  }

  private int readZeroIntName(int nameStart) {
    if (position >= input.length) {
      throw error("Unterminated string");
    }
    int ch = input[position] & 0xFF;
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameInt();
    }
    if (ch >= '0' && ch <= '9') {
      throw error("Leading zero in number");
    }
    if (ch != '"') {
      throw error("Expected integer field name");
    }
    position++;
    return 0;
  }

  private long readZeroLongName(int nameStart) {
    if (position >= input.length) {
      throw error("Unterminated string");
    }
    int ch = input[position] & 0xFF;
    if (ch == '\\') {
      position = nameStart;
      return super.readFieldNameLong();
    }
    if (ch >= '0' && ch <= '9') {
      throw error("Leading zero in number");
    }
    if (ch != '"') {
      throw error("Expected long field name");
    }
    position++;
    return 0L;
  }
}
