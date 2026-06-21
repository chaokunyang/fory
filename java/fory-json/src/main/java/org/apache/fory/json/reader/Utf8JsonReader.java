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

import java.nio.charset.StandardCharsets;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldNameHash;
import org.apache.fory.json.meta.JsonFieldTable;

public final class Utf8JsonReader extends JsonReader {
  private static final byte[] EMPTY_BYTES = new byte[0];

  private byte[] input;

  public Utf8JsonReader() {
    input = EMPTY_BYTES;
  }

  public Utf8JsonReader(byte[] input) {
    this.input = input;
  }

  public Utf8JsonReader reset(byte[] input) {
    this.input = input;
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

  public void expectToken(char expected) {
    if (!consumeToken(expected)) {
      throw error("Expected '" + expected + "'");
    }
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
  protected int length() {
    return input.length;
  }

  @Override
  protected char charAt(int index) {
    return (char) (input[index] & 0xFF);
  }

  @Override
  public String readString() {
    skipWhitespace();
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    while (position < input.length) {
      int b = input[position++] & 0xFF;
      if (b == '"') {
        return new String(input, start, position - 1 - start, StandardCharsets.ISO_8859_1);
      } else if (b == '\\') {
        StringBuilder builder = new StringBuilder(input.length - start);
        appendAscii(builder, start, position - 1);
        appendEscape(builder);
        return readStringTail(builder);
      } else if (b < 0x20) {
        throw error("Control character in string");
      } else if (b < 0x80) {
        continue;
      } else {
        StringBuilder builder = new StringBuilder(input.length - start);
        appendAscii(builder, start, position - 1);
        appendUtf8(builder, b);
        return readStringTail(builder);
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
      int b = bytes[position++] & 0xFF;
      if (b == '"') {
        return JsonFieldNameHash.finish(hash, value, nameLength, latin1);
      }
      if (b == '\\') {
        char escaped = readEscapedFieldNameChar();
        hash = JsonFieldNameHash.update(hash, escaped);
        latin1 = latin1 && escaped <= 0xFF && (nameLength != 0 || escaped != 0);
        if (latin1 && nameLength < Long.BYTES) {
          value = JsonFieldNameHash.value(value, nameLength, escaped);
        }
        nameLength++;
        if (Character.isHighSurrogate(escaped)) {
          if (position + 2 > length() || charAt(position) != '\\' || charAt(position + 1) != 'u') {
            throw error("Unpaired high surrogate escape");
          }
          position += 2;
          char low = readUnicodeEscape();
          if (!Character.isLowSurrogate(low)) {
            throw error("Unpaired high surrogate escape");
          }
          hash = JsonFieldNameHash.update(hash, low);
          latin1 = false;
          nameLength++;
        } else if (Character.isLowSurrogate(escaped)) {
          throw error("Unpaired low surrogate escape");
        }
        continue;
      }
      if (b < 0x20) {
        throw error("Control character in string");
      }
      if (b < 0x80) {
        hash = JsonFieldNameHash.update(hash, (char) b);
        latin1 = latin1 && (nameLength != 0 || b != 0);
        if (latin1 && nameLength < Long.BYTES) {
          value = JsonFieldNameHash.value(value, nameLength, (char) b);
        }
        nameLength++;
        continue;
      }
      int codePoint = readUtf8CodePoint(b);
      if (codePoint <= 0xFFFF) {
        char ch = (char) codePoint;
        hash = JsonFieldNameHash.update(hash, ch);
        latin1 = latin1 && ch <= 0xFF && (nameLength != 0 || ch != 0);
        if (latin1 && nameLength < Long.BYTES) {
          value = JsonFieldNameHash.value(value, nameLength, ch);
        }
        nameLength++;
      } else {
        hash = JsonFieldNameHash.update(hash, Character.highSurrogate(codePoint));
        hash = JsonFieldNameHash.update(hash, Character.lowSurrogate(codePoint));
        latin1 = false;
        nameLength += 2;
      }
    }
    throw error("Unterminated string");
  }

  @Override
  protected String slice(int start, int end) {
    char[] chars = new char[end - start];
    for (int i = start; i < end; i++) {
      chars[i - start] = (char) (input[i] & 0xFF);
    }
    return new String(chars);
  }

  private String readStringTail(StringBuilder builder) {
    while (position < input.length) {
      int b = input[position++] & 0xFF;
      if (b == '"') {
        return builder.toString();
      } else if (b == '\\') {
        appendEscape(builder);
      } else if (b < 0x20) {
        throw error("Control character in string");
      } else if (b < 0x80) {
        builder.append((char) b);
      } else {
        appendUtf8(builder, b);
      }
    }
    throw error("Unterminated string");
  }

  private void appendAscii(StringBuilder builder, int start, int end) {
    for (int i = start; i < end; i++) {
      builder.append((char) (input[i] & 0xFF));
    }
  }

  private void appendUtf8(StringBuilder builder, int first) {
    int codePoint = readUtf8CodePoint(first);
    if (codePoint <= 0xFFFF) {
      builder.append((char) codePoint);
    } else {
      builder.append(Character.highSurrogate(codePoint));
      builder.append(Character.lowSurrogate(codePoint));
    }
  }

  private int readUtf8CodePoint(int first) {
    if ((first & 0xE0) == 0xC0) {
      int second = continuation();
      int codePoint = ((first & 0x1F) << 6) | second;
      if (codePoint < 0x80) {
        throw error("Overlong UTF-8 sequence");
      }
      return codePoint;
    } else if ((first & 0xF0) == 0xE0) {
      int second = continuation();
      int third = continuation();
      int codePoint = ((first & 0x0F) << 12) | (second << 6) | third;
      if (codePoint < 0x800 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
        throw error("Invalid UTF-8 sequence");
      }
      return codePoint;
    } else if ((first & 0xF8) == 0xF0) {
      int second = continuation();
      int third = continuation();
      int fourth = continuation();
      int codePoint = ((first & 0x07) << 18) | (second << 12) | (third << 6) | fourth;
      if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
        throw error("Invalid UTF-8 sequence");
      }
      return codePoint;
    }
    throw error("Invalid UTF-8 sequence");
  }

  private int continuation() {
    if (position >= input.length) {
      throw error("Short UTF-8 sequence");
    }
    int value = input[position++] & 0xFF;
    if ((value & 0xC0) != 0x80) {
      throw error("Invalid UTF-8 continuation");
    }
    return value & 0x3F;
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
}
