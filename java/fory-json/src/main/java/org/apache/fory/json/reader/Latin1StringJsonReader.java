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

  public boolean readExpectedField(String expectedName) {
    skipWhitespaceFast();
    int savedPosition = position;
    byte[] bytes = input;
    int length = bytes.length;
    if (position >= length || bytes[position++] != '"') {
      position = savedPosition;
      return false;
    }
    int matchedLength = 0;
    int expectedLength = expectedName.length();
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        if (matchedLength == expectedLength) {
          return true;
        }
        position = savedPosition;
        return false;
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = savedPosition;
        return false;
      }
      if (matchedLength >= expectedLength || expectedName.charAt(matchedLength) != ch) {
        position = savedPosition;
        return false;
      }
      matchedLength++;
    }
    position = savedPosition;
    return false;
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
    skipWhitespaceFast();
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    while (position < input.length) {
      int ch = input[position++] & 0xFF;
      if (ch == '"') {
        return new String(input, start, position - 1 - start, StandardCharsets.ISO_8859_1);
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
    skipWhitespaceFast();
    byte[] bytes = input;
    int length = bytes.length;
    if (position >= length || bytes[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        return table.getLatin1(this, start, position - 1, hash);
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = start - 1;
        return table.get(readString());
      }
      hash = 31 * hash + ch;
    }
    throw error("Unterminated string");
  }

  @Override
  public int readFieldIndex(JsonFieldTable table) {
    skipWhitespaceFast();
    byte[] bytes = input;
    int length = bytes.length;
    if (position >= length || bytes[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        return table.indexLatin1(this, start, position - 1, hash);
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = start - 1;
        return table.index(readString());
      }
      hash = 31 * hash + ch;
    }
    throw error("Unterminated string");
  }

  @Override
  public int readFieldIndex(JsonFieldTable table, String expectedName, int expectedIndex) {
    skipWhitespaceFast();
    byte[] bytes = input;
    int length = bytes.length;
    if (position >= length || bytes[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    int matchedLength = 0;
    int expectedLength = expectedName.length();
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        if (matchedLength == expectedLength) {
          return expectedIndex;
        }
        int end = position - 1;
        return table.indexLatin1(this, start, end, hashRange(start, end));
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = start - 1;
        String name = readString();
        return expectedName.equals(name) ? expectedIndex : table.index(name);
      }
      if (matchedLength >= expectedLength || expectedName.charAt(matchedLength) != ch) {
        return fieldIndexFallback(table, start);
      }
      matchedLength++;
    }
    throw error("Unterminated string");
  }

  private int fieldIndexFallback(JsonFieldTable table, int start) {
    int hash = 0;
    byte[] bytes = input;
    for (int i = start; i < position; i++) {
      hash = 31 * hash + (bytes[i] & 0xFF);
    }
    int length = bytes.length;
    while (position < length) {
      int ch = bytes[position++] & 0xFF;
      if (ch == '"') {
        return table.indexLatin1(this, start, position - 1, hash);
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = start - 1;
        return table.index(readString());
      }
      hash = 31 * hash + ch;
    }
    throw error("Unterminated string");
  }

  private int hashRange(int start, int end) {
    int hash = 0;
    byte[] bytes = input;
    for (int i = start; i < end; i++) {
      hash = 31 * hash + (bytes[i] & 0xFF);
    }
    return hash;
  }

  @Override
  public boolean regionEquals(String value, int start, int end) {
    int length = end - start;
    if (value.length() != length) {
      return false;
    }
    byte[] bytes = input;
    for (int i = 0; i < length; i++) {
      char expected = value.charAt(i);
      if (expected > 0xFF || (bytes[start + i] & 0xFF) != expected) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected String slice(int start, int end) {
    return new String(input, start, end - start, StandardCharsets.ISO_8859_1);
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
}
