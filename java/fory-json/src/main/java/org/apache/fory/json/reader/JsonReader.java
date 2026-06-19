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

import org.apache.fory.json.ForyJsonException;

public abstract class JsonReader {
  protected int position;

  protected abstract int length();

  protected abstract char charAt(int index);

  public abstract String readString();

  public final void skipWhitespace() {
    while (position < length()) {
      char ch = charAt(position);
      if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
        position++;
      } else {
        return;
      }
    }
  }

  public final boolean consume(char expected) {
    skipWhitespace();
    if (position < length() && charAt(position) == expected) {
      position++;
      return true;
    }
    return false;
  }

  public final void expect(char expected) {
    if (!consume(expected)) {
      throw error("Expected '" + expected + "'");
    }
  }

  public final boolean peekNull() {
    skipWhitespace();
    return startsWith("null");
  }

  public final char peekToken() {
    skipWhitespace();
    if (position >= length()) {
      throw error("Expected token");
    }
    return charAt(position);
  }

  public final void readNull() {
    skipWhitespace();
    if (!startsWith("null")) {
      throw error("Expected null");
    }
    position += 4;
  }

  public final boolean readBoolean() {
    skipWhitespace();
    if (startsWith("true")) {
      position += 4;
      return true;
    } else if (startsWith("false")) {
      position += 5;
      return false;
    }
    throw error("Expected boolean");
  }

  public final String readNumber() {
    skipWhitespace();
    int start = position;
    if (position < length() && charAt(position) == '-') {
      position++;
    }
    readIntegerDigits();
    if (position < length() && charAt(position) == '.') {
      position++;
      readDigits();
    }
    if (position < length() && (charAt(position) == 'e' || charAt(position) == 'E')) {
      position++;
      if (position < length() && (charAt(position) == '+' || charAt(position) == '-')) {
        position++;
      }
      readDigits();
    }
    if (start == position) {
      throw error("Expected number");
    }
    return slice(start, position);
  }

  public final void skipValue() {
    skipWhitespace();
    if (position >= length()) {
      throw error("Expected value");
    }
    char ch = charAt(position);
    if (ch == '"') {
      readString();
    } else if (ch == '{') {
      skipObject();
    } else if (ch == '[') {
      skipArray();
    } else if (startsWith("true")) {
      position += 4;
    } else if (startsWith("false")) {
      position += 5;
    } else if (startsWith("null")) {
      position += 4;
    } else {
      readNumber();
    }
  }

  public final void finish() {
    skipWhitespace();
    if (position != length()) {
      throw error("Trailing content");
    }
  }

  protected final ForyJsonException error(String message) {
    return new ForyJsonException(message + " at JSON position " + position);
  }

  protected final void appendEscape(StringBuilder builder) {
    if (position >= length()) {
      throw error("Unterminated escape");
    }
    char escaped = charAt(position++);
    switch (escaped) {
      case '"':
      case '\\':
      case '/':
        builder.append(escaped);
        return;
      case 'b':
        builder.append('\b');
        return;
      case 'f':
        builder.append('\f');
        return;
      case 'n':
        builder.append('\n');
        return;
      case 'r':
        builder.append('\r');
        return;
      case 't':
        builder.append('\t');
        return;
      case 'u':
        appendUnicodeEscape(builder);
        return;
      default:
        throw error("Invalid escape");
    }
  }

  private void skipObject() {
    expect('{');
    if (consume('}')) {
      return;
    }
    do {
      skipWhitespace();
      readString();
      expect(':');
      skipValue();
    } while (consume(','));
    expect('}');
  }

  private void skipArray() {
    expect('[');
    if (consume(']')) {
      return;
    }
    do {
      skipValue();
    } while (consume(','));
    expect(']');
  }

  private boolean startsWith(String value) {
    int end = position + value.length();
    if (end > length()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (charAt(position + i) != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private void readIntegerDigits() {
    if (position >= length()) {
      throw error("Expected digit");
    }
    char ch = charAt(position);
    if (ch == '0') {
      position++;
      if (position < length()) {
        ch = charAt(position);
        if (ch >= '0' && ch <= '9') {
          throw error("Leading zero in number");
        }
      }
      return;
    }
    if (ch < '1' || ch > '9') {
      throw error("Expected digit");
    }
    while (position < length()) {
      ch = charAt(position);
      if (ch >= '0' && ch <= '9') {
        position++;
      } else {
        break;
      }
    }
  }

  private void readDigits() {
    int start = position;
    while (position < length()) {
      char ch = charAt(position);
      if (ch >= '0' && ch <= '9') {
        position++;
      } else {
        break;
      }
    }
    if (start == position) {
      throw error("Expected digit");
    }
  }

  private void appendUnicodeEscape(StringBuilder builder) {
    char ch = readUnicodeEscape();
    if (Character.isHighSurrogate(ch)) {
      if (position + 2 > length() || charAt(position) != '\\' || charAt(position + 1) != 'u') {
        throw error("Unpaired high surrogate escape");
      }
      position += 2;
      char low = readUnicodeEscape();
      if (!Character.isLowSurrogate(low)) {
        throw error("Unpaired high surrogate escape");
      }
      builder.append(ch);
      builder.append(low);
    } else if (Character.isLowSurrogate(ch)) {
      throw error("Unpaired low surrogate escape");
    } else {
      builder.append(ch);
    }
  }

  private char readUnicodeEscape() {
    if (position + 4 > length()) {
      throw error("Short unicode escape");
    }
    int value = 0;
    for (int i = 0; i < 4; i++) {
      value = (value << 4) | hexValue(charAt(position++));
    }
    return (char) value;
  }

  private int hexValue(char ch) {
    if (ch >= '0' && ch <= '9') {
      return ch - '0';
    } else if (ch >= 'a' && ch <= 'f') {
      return ch - 'a' + 10;
    } else if (ch >= 'A' && ch <= 'F') {
      return ch - 'A' + 10;
    }
    throw error("Invalid hex digit");
  }

  protected abstract String slice(int start, int end);
}
