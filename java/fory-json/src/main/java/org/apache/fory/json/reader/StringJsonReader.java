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

public final class StringJsonReader extends JsonReader {
  private String input;

  public StringJsonReader() {
    input = "";
  }

  public StringJsonReader(String input) {
    this.input = input;
  }

  public StringJsonReader reset(String input) {
    this.input = input;
    position = 0;
    return this;
  }

  public void clear() {
    input = "";
    position = 0;
  }

  @Override
  protected int length() {
    return input.length();
  }

  @Override
  protected char charAt(int index) {
    return input.charAt(index);
  }

  @Override
  public String readString() {
    skipWhitespace();
    if (position >= input.length() || input.charAt(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    StringBuilder builder = null;
    while (position < input.length()) {
      char ch = input.charAt(position++);
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
        if (position >= input.length() || !Character.isLowSurrogate(input.charAt(position))) {
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
    int length = input.length();
    if (position >= length || input.charAt(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < length) {
      char ch = input.charAt(position++);
      if (ch == '"') {
        return table.get(this, start, position - 1, hash);
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
    int length = input.length();
    if (position >= length || input.charAt(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    while (position < length) {
      char ch = input.charAt(position++);
      if (ch == '"') {
        return table.index(this, start, position - 1, hash);
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
    int length = input.length();
    if (position >= length || input.charAt(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    int hash = 0;
    int matchedLength = 0;
    int expectedLength = expectedName.length();
    boolean matched = true;
    while (position < length) {
      char ch = input.charAt(position++);
      if (ch == '"') {
        if (matched && matchedLength == expectedLength) {
          return expectedIndex;
        }
        return table.index(this, start, position - 1, hash);
      }
      if (ch == '\\' || ch < 0x20 || ch >= 0x80) {
        position = start - 1;
        String name = readString();
        return expectedName.equals(name) ? expectedIndex : table.index(name);
      }
      if (matched) {
        if (matchedLength >= expectedLength || expectedName.charAt(matchedLength) != ch) {
          matched = false;
        } else {
          matchedLength++;
        }
      }
      hash = 31 * hash + ch;
    }
    throw error("Unterminated string");
  }

  @Override
  public boolean regionEquals(String value, int start, int end) {
    int length = end - start;
    if (value.length() != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      if (input.charAt(start + i) != value.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected String slice(int start, int end) {
    return input.substring(start, end);
  }
}
