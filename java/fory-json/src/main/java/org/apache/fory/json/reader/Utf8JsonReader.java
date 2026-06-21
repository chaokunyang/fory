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
import org.apache.fory.memory.LittleEndian;
import org.apache.fory.serializer.StringSerializer;

public final class Utf8JsonReader extends JsonReader {
  private static final byte[] EMPTY_BYTES = new byte[0];
  private static final long BYTE_ONES = 0x0101010101010101L;
  private static final long BYTE_HIGH_BITS = 0x8080808080808080L;
  private static final long BACKSLASH_BYTES = 0x5c5c5c5c5c5c5c5cL;
  private static final long CONTROL_LIMIT_BYTES = 0x2020202020202020L;
  private static final long QUOTE_BYTES = 0x2222222222222222L;

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
    return readStringToken();
  }

  @Override
  public String readNullableString() {
    skipWhitespaceFast();
    if (startsWithAscii("null")) {
      position += 4;
      return null;
    }
    return readStringToken();
  }

  private String readStringToken() {
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    position = scanStringChars(position);
    while (position < input.length) {
      int b = input[position++] & 0xFF;
      if (b == '"') {
        return newLatin1String(start, position - 1);
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

  private int scanStringChars(int offset) {
    byte[] bytes = input;
    int end = bytes.length - Long.BYTES;
    while (offset <= end) {
      long word = LittleEndian.getInt64(bytes, offset);
      // The word scan only skips bytes proven safe; the byte loop still owns token termination,
      // escape handling, and UTF-8 validation once any special byte may be present.
      if (hasStringStop(word)) {
        break;
      }
      offset += Long.BYTES;
    }
    return offset;
  }

  private static boolean hasStringStop(long word) {
    return (word & BYTE_HIGH_BITS) != 0
        || hasByte(word, QUOTE_BYTES)
        || hasByte(word, BACKSLASH_BYTES)
        || ((word - CONTROL_LIMIT_BYTES) & ~word & BYTE_HIGH_BITS) != 0;
  }

  private static boolean hasByte(long word, long repeatedByte) {
    long match = word ^ repeatedByte;
    return ((match - BYTE_ONES) & ~match & BYTE_HIGH_BITS) != 0;
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

  public boolean tryReadFieldNameColon(long expectedHash, long expectedMask, int expectedLength) {
    int mark = position;
    skipWhitespaceFast();
    byte[] bytes = input;
    int offset = position;
    int nameOffset = offset + 1;
    int quoteOffset = nameOffset + expectedLength;
    if (quoteOffset < bytes.length && bytes[offset] == '"') {
      // A raw word match proves this exact generated field name. Misses fall through to the
      // byte parser below, which keeps validation and escaped-name/UTF8 fallback in one owner.
      if (nameOffset + Long.BYTES <= bytes.length
          && (LittleEndian.getInt64(bytes, nameOffset) & expectedMask) == expectedHash
          && bytes[quoteOffset] == '"') {
        position = quoteOffset + 1;
        expectNextToken(':');
        return true;
      }
      offset = nameOffset;
      long value = 0;
      for (int i = 0; i < expectedLength; i++) {
        int ch = bytes[offset++] & 0xFF;
        if (ch == 0 || ch == '"' || ch == '\\' || ch < 0x20 || ch >= 0x80) {
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
    return readQuotedStringHashToken();
  }

  private long readQuotedStringHashToken() {
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
            if (escaped <= 0xFF && escaped != 0 && nameLength < Long.BYTES) {
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
      if (b < 0x20) {
        throw error("Control character in string");
      }
      if (b < 0x80) {
        if (latin1) {
          if (b != 0 && nameLength < Long.BYTES) {
            value = JsonFieldNameHash.value(value, nameLength, (char) b);
            nameLength++;
            continue;
          }
          hash = JsonFieldNameHash.hashPacked(value, nameLength);
          latin1 = false;
        }
        hash = JsonFieldNameHash.update(hash, (char) b);
        nameLength++;
        continue;
      }
      int codePoint = readUtf8CodePoint(b);
      if (codePoint <= 0xFFFF) {
        char ch = (char) codePoint;
        if (latin1) {
          if (ch <= 0xFF && ch != 0 && nameLength < Long.BYTES) {
            value = JsonFieldNameHash.value(value, nameLength, ch);
            nameLength++;
            continue;
          }
          hash = JsonFieldNameHash.hashPacked(value, nameLength);
          latin1 = false;
        }
        hash = JsonFieldNameHash.update(hash, ch);
        nameLength++;
      } else {
        if (latin1) {
          hash = JsonFieldNameHash.hashPacked(value, nameLength);
          latin1 = false;
        }
        hash = JsonFieldNameHash.update(hash, Character.highSurrogate(codePoint));
        hash = JsonFieldNameHash.update(hash, Character.lowSurrogate(codePoint));
        nameLength += 2;
      }
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
