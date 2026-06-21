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
import org.apache.fory.json.meta.JsonFieldTable;
import org.apache.fory.serializer.StringSerializer;

public final class Utf16StringJsonReader extends JsonReader {
  private String input;
  private byte[] bytes;
  private int length;

  public Utf16StringJsonReader() {
    input = "";
    bytes = null;
    length = 0;
  }

  public Utf16StringJsonReader(String input) {
    reset(input);
  }

  public Utf16StringJsonReader reset(String input) {
    this.input = input;
    if (StringSerializer.isBytesBackedString()) {
      byte coder = StringSerializer.getStringCoder(input);
      if (StringSerializer.isUtf16Coder(coder)) {
        bytes = StringSerializer.getStringBytes(input);
        length = bytes.length >>> 1;
        position = 0;
        return this;
      }
    }
    bytes = null;
    length = input.length();
    position = 0;
    return this;
  }

  public void clear() {
    input = "";
    bytes = null;
    length = 0;
    position = 0;
  }

  public boolean consumeToken(char expected) {
    skipWhitespaceFast();
    if (position < length && charAtFast(position) == expected) {
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
    int inputLength = length;
    if (position >= inputLength || charAtFast(position++) != '"') {
      position = savedPosition;
      return false;
    }
    int matchedLength = 0;
    int expectedLength = expectedName.length();
    while (position < inputLength) {
      char ch = charAtFast(position++);
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
    if (position < length && charAtFast(position) == '-') {
      negative = true;
      limit = Integer.MIN_VALUE;
      position++;
    }
    if (position >= length) {
      throw error("Expected digit");
    }
    char ch = charAtFast(position);
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
    while (position < length) {
      ch = charAtFast(position);
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
    if (position < length && charAtFast(position) == '-') {
      negative = true;
      limit = Long.MIN_VALUE;
      position++;
    }
    if (position >= length) {
      throw error("Expected digit");
    }
    char ch = charAtFast(position);
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
    while (position < length) {
      ch = charAtFast(position);
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
    return length;
  }

  @Override
  protected char charAt(int index) {
    return charAtFast(index);
  }

  @Override
  public String readString() {
    skipWhitespace();
    if (position >= length || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    StringBuilder builder = null;
    while (position < length) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        if (builder == null) {
          return input.substring(start, position - 1);
        }
        builder.append(input, start, position - 1);
        return builder.toString();
      } else if (ch == '\\') {
        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(input, start, position - 1);
        appendEscape(builder);
        start = position;
      } else if (ch < 0x20) {
        throw error("Control character in string");
      } else if (Character.isHighSurrogate(ch)) {
        if (position >= length || !Character.isLowSurrogate(charAtFast(position))) {
          throw error("Unpaired high surrogate in string");
        }
        position++;
      } else if (Character.isLowSurrogate(ch)) {
        throw error("Unpaired low surrogate in string");
      }
    }
    throw error("Unterminated string");
  }

  @Override
  public JsonFieldInfo readField(JsonFieldTable table) {
    skipWhitespace();
    int inputLength = length;
    if (position >= inputLength || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < inputLength) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        return table.getUtf16(this, start, position - 1, hash);
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
    skipWhitespace();
    int inputLength = length;
    if (position >= inputLength || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < inputLength) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        return table.indexUtf16(this, start, position - 1, hash);
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
    skipWhitespace();
    int inputLength = length;
    if (position >= inputLength || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int matchedLength = 0;
    int expectedLength = expectedName.length();
    while (position < inputLength) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        if (matchedLength == expectedLength) {
          return expectedIndex;
        }
        int end = position - 1;
        return table.indexUtf16(this, start, end, hashRange(start, end));
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
    for (int i = start; i < position; i++) {
      hash = 31 * hash + charAtFast(i);
    }
    int inputLength = length;
    while (position < inputLength) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        return table.indexUtf16(this, start, position - 1, hash);
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
    for (int i = start; i < end; i++) {
      hash = 31 * hash + charAtFast(i);
    }
    return hash;
  }

  @Override
  public boolean regionEquals(String value, int start, int end) {
    int length = end - start;
    if (value.length() != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      if (charAtFast(start + i) != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected String slice(int start, int end) {
    return input.substring(start, end);
  }

  private void skipWhitespaceFast() {
    int inputLength = length;
    while (position < inputLength) {
      char ch = charAtFast(position);
      if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
        position++;
      } else {
        return;
      }
    }
  }

  private boolean startsWithAscii(String value) {
    int end = position + value.length();
    if (end > length) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (charAtFast(position + i) != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private void rejectLeadingDigitFast() {
    if (position < length) {
      char ch = charAtFast(position);
      if (ch >= '0' && ch <= '9') {
        throw error("Leading zero in number");
      }
    }
  }

  private void rejectFractionOrExponentFast() {
    if (position < length) {
      char ch = charAtFast(position);
      if (ch == '.' || ch == 'e' || ch == 'E') {
        throw error("Expected integer");
      }
    }
  }

  private char charAtFast(int index) {
    byte[] localBytes = bytes;
    return localBytes == null
        ? input.charAt(index)
        : StringSerializer.getBytesChar(localBytes, index << 1);
  }
}
