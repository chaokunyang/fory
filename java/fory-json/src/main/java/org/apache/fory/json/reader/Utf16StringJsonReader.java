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
  public int readFieldNameInt() {
    skipWhitespaceFast();
    int nameStart = position;
    if (position >= length || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    int result = 0;
    int limit = -Integer.MAX_VALUE;
    boolean negative = false;
    if (position < length && charAtFast(position) == '-') {
      negative = true;
      limit = Integer.MIN_VALUE;
      position++;
    }
    if (position >= length) {
      throw error("Unterminated string");
    }
    char ch = charAtFast(position);
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
      if (position >= length) {
        throw error("Unterminated string");
      }
      ch = charAtFast(position);
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
    if (position >= length || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    long result = 0;
    long limit = -Long.MAX_VALUE;
    boolean negative = false;
    if (position < length && charAtFast(position) == '-') {
      negative = true;
      limit = Long.MIN_VALUE;
      position++;
    }
    if (position >= length) {
      throw error("Unterminated string");
    }
    char ch = charAtFast(position);
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
      if (position >= length) {
        throw error("Unterminated string");
      }
      ch = charAtFast(position);
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

  @Override
  public long readStringHash() {
    return readQuotedStringHash();
  }

  private long readQuotedStringHash() {
    skipWhitespaceFast();
    int inputLength = length;
    if (position >= inputLength || charAtFast(position++) != '"') {
      throw error("Expected string");
    }
    long hash = JsonFieldNameHash.MAGIC_HASH_CODE;
    long value = 0;
    int nameLength = 0;
    boolean latin1 = true;
    while (position < inputLength) {
      char ch = charAtFast(position++);
      if (ch == '"') {
        return JsonFieldNameHash.finish(hash, value, nameLength, latin1);
      }
      if (ch == '\\') {
        ch = readEscapedFieldNameChar();
        if (Character.isHighSurrogate(ch)) {
          if (latin1) {
            hash = JsonFieldNameHash.hashPacked(value, nameLength);
            latin1 = false;
          }
          hash = JsonFieldNameHash.update(hash, ch);
          nameLength++;
          if (position + 2 > inputLength
              || charAtFast(position) != '\\'
              || charAtFast(position + 1) != 'u') {
            throw error("Unpaired high surrogate escape");
          }
          position += 2;
          char low = readUnicodeEscape();
          if (!Character.isLowSurrogate(low)) {
            throw error("Unpaired high surrogate escape");
          }
          hash = JsonFieldNameHash.update(hash, low);
          nameLength++;
        } else if (Character.isLowSurrogate(ch)) {
          throw error("Unpaired low surrogate escape");
        } else {
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
        }
        continue;
      }
      if (ch < 0x20) {
        throw error("Control character in string");
      }
      if (Character.isHighSurrogate(ch)) {
        if (position >= inputLength || !Character.isLowSurrogate(charAtFast(position))) {
          throw error("Unpaired high surrogate in string");
        }
        if (latin1) {
          hash = JsonFieldNameHash.hashPacked(value, nameLength);
          latin1 = false;
        }
        hash = JsonFieldNameHash.update(hash, ch);
        hash = JsonFieldNameHash.update(hash, charAtFast(position++));
        nameLength += 2;
        continue;
      }
      if (Character.isLowSurrogate(ch)) {
        throw error("Unpaired low surrogate in string");
      }
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
    }
    throw error("Unterminated string");
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

  private int readZeroIntName(int nameStart) {
    if (position >= length) {
      throw error("Unterminated string");
    }
    char ch = charAtFast(position);
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
    if (position >= length) {
      throw error("Unterminated string");
    }
    char ch = charAtFast(position);
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

  private char charAtFast(int index) {
    byte[] localBytes = bytes;
    return localBytes == null
        ? input.charAt(index)
        : StringSerializer.getBytesChar(localBytes, index << 1);
  }
}
